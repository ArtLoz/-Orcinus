#include "project_3mf.hpp"

#include <algorithm>
#include <fstream>
#include <mutex>
#include <optional>
#include <set>

#include <boost/algorithm/string/predicate.hpp>
#include <boost/filesystem.hpp>
#include <nlohmann/json.hpp>

#include "engine_context.hpp"
#include "settings_tab.hpp"
#include "slice_info.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/Exception.hpp"
#include "libslic3r/LocalesUtils.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Utils.hpp"
#include "libslic3r/libslic3r.h"
#include "libslic3r_version.h"

namespace orcinus::orca {

// Plater::get_next_color_for_filament()'s palette (presets.cpp).
const std::vector<std::string>& filament_palette();

}  // namespace orcinus::orca

namespace orcinus::orca::detail {

namespace {

// LoadType of Plater.cpp, as the app configuration's import_project_action keeps it.
constexpr int load_type_geometry = 2;

// PresetBundle::validate_presets()
constexpr int validate_presets_printer_not_found = 1;
constexpr int validate_presets_filaments_not_found = 2;
constexpr int validate_presets_modified_gcodes = 3;

UiText literal(const std::string& text)
{
    return ui_text("%s", {text});
}

// The Migration fix for OrcaSlicer 2.3.1-alpha's sparse infill rotation
// template of load_files(), which asks before it clears the template.
void offer_rotation_template_fix(Slic3r::DynamicPrintConfig& config_loaded, SettingsDialogs& dialogs)
{
    if (config_loaded.opt_string("sparse_infill_rotate_template").empty()) {
        return;
    }
    const auto _sparse_infill_pattern = config_loaded.option<Slic3r::ConfigOptionEnum<Slic3r::InfillPattern>>("sparse_infill_pattern")->value;
    bool is_safe_to_rotate = _sparse_infill_pattern == Slic3r::ipRectilinear || _sparse_infill_pattern == Slic3r::ipLine ||
                             _sparse_infill_pattern == Slic3r::ipZigZag || _sparse_infill_pattern == Slic3r::ipCrossZag ||
                             _sparse_infill_pattern == Slic3r::ipLockedZag;
    if (!is_safe_to_rotate &&
        dialogs.ask("rotation_template",
                    {ui_text("This project was created with an OrcaSlicer 2.3.1-alpha and uses "
                             "infill rotation template settings that may not work properly with your current infill pattern. "
                             "This could result in weak support or print quality issues."),
                     literal("\n\n"),
                     ui_text("Would you like OrcaSlicer to automatically fix this by clearing the rotation template settings?")},
                    {}, ui_text("Yes"), ui_text("No"))) {
        config_loaded.opt_string("sparse_infill_rotate_template") = "";
    }
}

// add_config_substitutions() of GUI.cpp, a line for each value.
std::vector<UiText> substitution_lines(const Slic3r::ConfigSubstitutions& conf_substitutions)
{
    std::vector<UiText> changes;
    for (const Slic3r::ConfigSubstitution& conf_substitution : conf_substitutions) {
        std::string new_val;
        const Slic3r::ConfigOptionDef* def = conf_substitution.opt_def;
        if (!def) {
            continue;
        }
        switch (def->type) {
        case Slic3r::coEnum: {
            const std::vector<std::string>& labels = def->enum_labels;
            const std::vector<std::string>& values = def->enum_values;
            int val = conf_substitution.new_value->getInt();

            bool is_infill = def->opt_key == "top_surface_pattern" || def->opt_key == "bottom_surface_pattern" ||
                             def->opt_key == "internal_solid_infill_pattern" || def->opt_key == "support_base_pattern" ||
                             def->opt_key == "support_interface_pattern" || def->opt_key == "ironing_pattern" ||
                             def->opt_key == "support_ironing_pattern" || def->opt_key == "sparse_infill_pattern";

            // Each infill doesn't use all list of infill declared in PrintConfig.hpp.
            // So we should "convert" val to the correct one
            std::string label;
            if (is_infill) {
                for (const auto& key_val : *def->enum_keys_map) {
                    if ((int) key_val.second == val) {
                        auto it = std::find(values.begin(), values.end(), key_val.first);
                        if (it == values.end()) {
                            break;
                        }
                        auto idx = it - values.begin();
                        new_val = "\"" + values[idx] + "\"";
                        label = labels[idx];
                        break;
                    }
                }
            } else if (val >= 0 && std::size_t(val) < values.size()) {
                new_val = "\"" + values[val] + "\"";
                label = labels[val];
            }
            changes.push_back(literal("\n\"" + def->opt_key + "\" ("));
            changes.push_back(ui_text(def->label));
            changes.push_back(literal("): "));
            changes.push_back(ui_text("%1% was replaced with %2%",
                                      {conf_substitution.old_value, new_val.empty() ? std::string("Undefined") : new_val + " (" + label + ")"}));
            continue;
        }
        case Slic3r::coEnums:
            new_val = "\"" + conf_substitution.new_value->serialize() + "\"";
            break;
        case Slic3r::coBool:
            new_val = conf_substitution.new_value->getBool() ? "true" : "false";
            break;
        case Slic3r::coBools:
            if (conf_substitution.new_value->nullable()) {
                for (const char v : static_cast<const Slic3r::ConfigOptionBoolsNullable*>(conf_substitution.new_value.get())->values) {
                    new_val += std::string(v == Slic3r::ConfigOptionBoolsNullable::nil_value() ? "nil" : v ? "true" : "false") + ", ";
                }
            } else {
                for (const char v : static_cast<const Slic3r::ConfigOptionBools*>(conf_substitution.new_value.get())->values) {
                    new_val += std::string(v ? "true" : "false") + ", ";
                }
            }
            if (!new_val.empty()) {
                new_val.erase(new_val.begin() + new_val.size() - 2, new_val.end());
            }
            break;
        default:
            break;
        }
        changes.push_back(literal("\n\"" + def->opt_key + "\" ("));
        changes.push_back(ui_text(def->label));
        changes.push_back(literal("): "));
        changes.push_back(ui_text("%1% was replaced with %2%", {conf_substitution.old_value, new_val}));
    }
    return changes;
}

// substitution_message() of GUI.cpp
std::vector<UiText> substitution_message(std::vector<UiText> changes)
{
    std::vector<UiText> text = {ui_text("The configuration may be generated by a newer version of OrcaSlicer."), literal(" "),
                                ui_text("Some values have been replaced. Please check them:"), literal("\n")};
    text.insert(text.end(), changes.begin(), changes.end());
    text.push_back(literal("\n"));
    return text;
}

// show_substitutions_info() for the settings of a file.
void show_substitutions_info(const Slic3r::ConfigSubstitutions& config_substitutions, const std::string& filename, SettingsDialogs& dialogs)
{
    std::vector<UiText> changes = {literal("\n")};
    const std::vector<UiText> lines = substitution_lines(config_substitutions);
    changes.insert(changes.end(), lines.begin(), lines.end());
    dialogs.inform("config_substitutions", substitution_message(changes),
                   {ui_text("Configuration file \"%1%\" was loaded, but some values were not recognized.", {filename})}, DialogIcon::info);
}

// show_substitutions_info() for presets.
void show_substitutions_info(const Slic3r::PresetsConfigSubstitutions& presets_config_substitutions, SettingsDialogs& dialogs)
{
    std::vector<UiText> changes;
    for (const Slic3r::PresetConfigSubstitutions& substitution : presets_config_substitutions) {
        changes.push_back(literal("\n\n"));
        switch (substitution.preset_type) {
        case Slic3r::Preset::TYPE_PRINT: changes.push_back(ui_text("Process")); break;
        case Slic3r::Preset::TYPE_FILAMENT: changes.push_back(ui_text("Filament")); break;
        case Slic3r::Preset::TYPE_PRINTER: changes.push_back(ui_text("Machine")); break;
        default: break;
        }
        changes.push_back(literal(" : " + substitution.preset_name));
        if (!substitution.preset_file.empty()) {
            changes.push_back(literal(" (" + substitution.preset_file + ")"));
        }
        const std::vector<UiText> lines = substitution_lines(substitution.substitutions);
        changes.insert(changes.end(), lines.begin(), lines.end());
    }
    dialogs.inform("preset_substitutions", substitution_message(changes),
                   {ui_text("Configuration package was loaded, but some values were not recognized.")}, DialogIcon::info);
}

// BBS: modify the prime tower params for old version file
void migrate_prime_tower(Slic3r::DynamicPrintConfig& config)
{
    double old_filament_prime_volume = 0.;
    int    filament_count            = 0;
    {
        Slic3r::ConfigOptionFloats*  filament_prime_volume_option = config.option<Slic3r::ConfigOptionFloats>("filament_prime_volume");
        Slic3r::ConfigOptionStrings* filament_colors_option       = config.option<Slic3r::ConfigOptionStrings>("filament_colour", true);
        filament_count                                            = filament_colors_option->values.size();
        if (filament_prime_volume_option) {
            std::vector<double>& filament_prime_volume_values = filament_prime_volume_option->values;
            if (!filament_prime_volume_values.empty()) {
                old_filament_prime_volume = filament_prime_volume_values[0];
                if (filament_count > 1) filament_prime_volume_values.resize(filament_count, old_filament_prime_volume);
            }
        }
    }
    auto* prime_tower_rib_wall_option = config.option<Slic3r::ConfigOptionEnum<Slic3r::WipeTowerWallType>>("wipe_tower_wall_type", true);
    prime_tower_rib_wall_option->value = Slic3r::WipeTowerWallType::wtwRectangle;

    auto* prime_tower_infill_gap_option = config.option<Slic3r::ConfigOptionPercent>("prime_tower_infill_gap", true);
    prime_tower_infill_gap_option->value = 100;

    auto* filament_adhesiveness_category_option = config.option<Slic3r::ConfigOptionInts>("filament_adhesiveness_category", true);
    std::vector<int>& filament_adhesiveness_category_values = filament_adhesiveness_category_option->values;
    filament_adhesiveness_category_values.resize(filament_count);
    for (int index = 0; index < filament_count; index++)
        filament_adhesiveness_category_values[index] = 100;

    std::vector<std::string>& diff_settings = config.option<Slic3r::ConfigOptionStrings>("different_settings_to_system", true)->values;
    diff_settings.resize(filament_count + 2);

    std::vector<std::string> diff_process_keys;
    std::string              diff_process_settings = diff_settings[0];
    Slic3r::unescape_strings_cstyle(diff_process_settings, diff_process_keys);
    diff_process_keys.emplace_back("wipe_tower_wall_type");
    diff_process_keys.emplace_back("prime_tower_infill_gap");
    diff_process_settings = Slic3r::escape_strings_cstyle(diff_process_keys);
    diff_settings[0]      = diff_process_settings;

    for (int index = 0; index < filament_count; index++) {
        std::vector<std::string> diff_filament_keys;
        std::string              diff_filament_settings = diff_settings[index + 1];
        Slic3r::unescape_strings_cstyle(diff_filament_settings, diff_filament_keys);
        diff_filament_keys.emplace_back("filament_adhesiveness_category");
        diff_filament_settings   = Slic3r::escape_strings_cstyle(diff_filament_keys);
        diff_settings[index + 1] = diff_filament_settings;
    }
}

// The warnings of load_files() about presets of the project that are not
// the system's, or whose G-code differs from theirs; the desktop app's "Don't
// show again" is kept in no_warn_when_modified_gcodes.
void warn_about_modified_gcodes(const std::string& file_name, Slic3r::DynamicPrintConfig& config, SettingsDialogs& dialogs)
{
    auto choise = engine().config->get("no_warn_when_modified_gcodes");
    if (!choise.empty() && choise == "true") {
        return;
    }
    // BBS: first validate the printer
    // validate the system profiles
    std::set<std::string> modified_gcodes;
    int validated = engine().bundle->validate_presets(file_name, config, modified_gcodes);
    std::string warning_message;
    warning_message += "\n";
    for (std::set<std::string>::iterator it = modified_gcodes.begin(); it != modified_gcodes.end(); ++it)
        warning_message += "-" + *it + "\n";
    warning_message += "\n";
    if (validated == validate_presets_modified_gcodes) {
        dialogs.inform("modified_gcodes",
                       {ui_text("The 3MF has the following modified G-code in filament or printer presets:"), literal(warning_message),
                        ui_text("Please confirm that all modified G-code is safe to prevent any damage to the machine!")},
                       {ui_text("Modified G-code")});
    } else if (validated == validate_presets_printer_not_found || validated == validate_presets_filaments_not_found) {
        dialogs.inform("customized_presets",
                       {ui_text("The 3MF has the following customized filament or printer presets:"), literal(warning_message),
                        ui_text("Please confirm that the G-code within these presets is safe to prevent any damage to the machine!")},
                       {ui_text("Customized Preset")});
    }
}

}  // namespace

namespace {

// Copies every file under from into to, keeping the folders between.
void copy_tree(const boost::filesystem::path& from, const boost::filesystem::path& to)
{
    boost::system::error_code ec;
    if (!boost::filesystem::is_directory(from, ec)) {
        return;
    }
    for (boost::filesystem::recursive_directory_iterator it(from, ec), end; !ec && it != end; it.increment(ec)) {
        const boost::filesystem::path target = to / boost::filesystem::relative(it->path(), from, ec);
        if (boost::filesystem::is_directory(it->path(), ec)) {
            boost::filesystem::create_directories(target, ec);
        } else if (boost::filesystem::is_regular_file(it->path(), ec)) {
            boost::filesystem::create_directories(target.parent_path(), ec);
            boost::filesystem::copy_file(it->path(), target, boost::filesystem::copy_options::overwrite_existing, ec);
        }
    }
}

constexpr const char* project_info_file = "info.json";
constexpr const char* project_auxiliaries = "Auxiliaries";

}  // namespace

void keep_project_info(Slic3r::Model& model, const std::string& directory)
{
    nlohmann::json info;
    info["stl_design_id"] = model.stl_design_id;
    info["stl_design_country"] = model.stl_design_country;
    if (model.design_info) {
        info["design_info"] = {{"DesignId", model.design_info->DesignId},
                               {"Designer", model.design_info->Designer},
                               {"DesignerUserId", model.design_info->DesignerUserId}};
    }
    if (model.model_info) {
        info["model_info"] = {{"cover_file", model.model_info->cover_file},   {"license", model.model_info->license},
                              {"description", model.model_info->description}, {"copyright", model.model_info->copyright},
                              {"model_name", model.model_info->model_name},   {"origin", model.model_info->origin},
                              {"metadata_items", model.model_info->metadata_items}};
    }
    if (model.profile_info) {
        info["profile_info"] = {{"ProfileTile", model.profile_info->ProfileTile},
                                {"ProfileCover", model.profile_info->ProfileCover},
                                {"ProfileDescription", model.profile_info->ProfileDescription},
                                {"ProfileUserId", model.profile_info->ProfileUserId},
                                {"ProfileUserName", model.profile_info->ProfileUserName}};
    }
    info["mk_name"] = model.mk_name;
    info["mk_version"] = model.mk_version;
    info["md_name"] = model.md_name;
    info["md_value"] = model.md_value;

    boost::filesystem::create_directories(directory);
    std::ofstream((boost::filesystem::path(directory) / project_info_file).string()) << info.dump();
    copy_tree(model.get_auxiliary_file_temp_path(), boost::filesystem::path(directory) / project_auxiliaries);
}

void restore_project_info(Slic3r::Model& model, const std::string& directory)
{
    std::ifstream file((boost::filesystem::path(directory) / project_info_file).string());
    if (!file) {
        return;
    }
    const nlohmann::json info = nlohmann::json::parse(file, nullptr, false);
    if (info.is_discarded()) {
        return;
    }
    const auto text = [](const nlohmann::json& object, const char* key) {
        const auto found = object.find(key);
        return found != object.end() && found->is_string() ? found->get<std::string>() : std::string();
    };
    model.stl_design_id = text(info, "stl_design_id");
    model.stl_design_country = text(info, "stl_design_country");
    if (const auto design = info.find("design_info"); design != info.end()) {
        model.design_info = std::make_shared<Slic3r::ModelDesignInfo>();
        model.design_info->DesignId = text(*design, "DesignId");
        model.design_info->Designer = text(*design, "Designer");
        model.design_info->DesignerUserId = text(*design, "DesignerUserId");
    }
    if (const auto described = info.find("model_info"); described != info.end()) {
        model.model_info = std::make_shared<Slic3r::ModelInfo>();
        model.model_info->cover_file = text(*described, "cover_file");
        model.model_info->license = text(*described, "license");
        model.model_info->description = text(*described, "description");
        model.model_info->copyright = text(*described, "copyright");
        model.model_info->model_name = text(*described, "model_name");
        model.model_info->origin = text(*described, "origin");
        if (const auto items = described->find("metadata_items"); items != described->end() && items->is_object()) {
            model.model_info->metadata_items = items->get<std::map<std::string, std::string>>();
        }
    }
    if (const auto profile = info.find("profile_info"); profile != info.end()) {
        model.profile_info = std::make_shared<Slic3r::ModelProfileInfo>();
        model.profile_info->ProfileTile = text(*profile, "ProfileTile");
        model.profile_info->ProfileCover = text(*profile, "ProfileCover");
        model.profile_info->ProfileDescription = text(*profile, "ProfileDescription");
        model.profile_info->ProfileUserId = text(*profile, "ProfileUserId");
        model.profile_info->ProfileUserName = text(*profile, "ProfileUserName");
    }
    model.mk_name = text(info, "mk_name");
    model.mk_version = text(info, "mk_version");
    if (const auto names = info.find("md_name"); names != info.end() && names->is_array()) {
        model.md_name = names->get<std::vector<std::string>>();
    }
    if (const auto values = info.find("md_value"); values != info.end() && values->is_array()) {
        model.md_value = values->get<std::vector<std::string>>();
    }
    copy_tree(boost::filesystem::path(directory) / project_auxiliaries, model.get_auxiliary_file_temp_path());
}

Archive3mf::~Archive3mf()
{
    Slic3r::release_PlateData_list(plate_data);
    for (Slic3r::Preset* preset : project_presets) {
        delete preset;
    }
}

Slic3r::Model read_3mf(
    const std::string& path,
    const bool project,
    const Slic3r::DynamicPrintConfig& current,
    SettingsDialogs& dialogs,
    Archive3mf& archive
)
{
    using Slic3r::LoadStrategy;
    // Plater::load_project() loads the model with its settings, and "Import
    // geometry only" the model alone.
    bool load_model = true;
    bool load_config = project;
    LoadStrategy strategy = LoadStrategy::LoadModel;
    if (load_config) {
        strategy = strategy | LoadStrategy::LoadConfig | LoadStrategy::LoadAuxiliary | LoadStrategy::CheckVersion;
    }

    Slic3r::DynamicPrintConfig config_loaded;
    Slic3r::ConfigSubstitutionContext config_substitutions{Slic3r::ForwardCompatibilitySubstitutionRule::Enable};
    Slic3r::En3mfType en_3mf_file_type = Slic3r::En3mfType::From_BBS;
    Slic3r::Model model = Slic3r::Model::read_from_archive(path, &config_loaded, &config_substitutions, en_3mf_file_type, strategy,
                                                           &archive.plate_data, &archive.project_presets, &archive.file_version);
    const Slic3r::Semver& file_version = archive.file_version;

    // 1. add extruder for prusa model if the number of existing extruders is not enough
    // 2. add extruder for BBS or Other model if only import geometry
    if (en_3mf_file_type == Slic3r::En3mfType::From_Prusa || (load_model && !load_config)) {
        std::set<int> extruderIds;
        for (Slic3r::ModelObject* o : model.objects) {
            if (o->config.option("extruder")) extruderIds.insert(o->config.extruder());
            for (auto volume : o->volumes) {
                if (volume->config.option("extruder")) extruderIds.insert(volume->config.extruder());
                for (int extruder : volume->get_extruders()) {
                    extruderIds.insert(extruder);
                }
            }
        }
        const std::size_t size = extruderIds.empty() ? 0 : std::size_t(std::max(*extruderIds.rbegin(), 0));
        const std::size_t filament_size = current.option<Slic3r::ConfigOptionStrings>("filament_colour")->values.size();
        if (filament_size < std::min(size, ::MAXIMUM_EXTRUDER_NUMBER)) {
            archive.filament_count = std::min(size, ::MAXIMUM_EXTRUDER_NUMBER);
        }
    }

    // The choice ProjectDropDialog last remembered (import_project_action).
    const std::string import_project_action = engine().config->get("import_project_action");
    const bool load_geometry_chosen = !import_project_action.empty() && std::atoi(import_project_action.c_str()) == load_type_geometry;

    // BBS: version check
    Slic3r::Semver app_version = *(Slic3r::Semver::parse(SoftFever_VERSION));
    const UiText load_3mf_title = ui_text("Load 3MF");
    const UiText newer_3mf_title = ui_text("Newer 3MF version");
    const UiText bambu_project_title = ui_text("BambuStudio Project");
    const UiText msg_unsupported_geometry = ui_text("The 3MF is not supported by OrcaSlicer, loading geometry data only.");
    const UiText msg_old_orca_geometry = ui_text("The 3MF file was generated by an old OrcaSlicer version, loading geometry data only.");
    const UiText msg_older_geometry = ui_text("The 3MF file was generated by an older version, loading geometry data only.");
    const UiText msg_bambu_geometry = ui_text("The 3MF file was generated by BambuStudio, loading geometry data only.");
    auto show_3mf_info = [&dialogs](const std::string& id, std::vector<UiText> text, const UiText& title) {
        dialogs.inform(id, std::move(text), {title}, DialogIcon::info);
    };
    if (en_3mf_file_type == Slic3r::En3mfType::From_Prusa) {
        // do not reset the model config
        load_config = false;
        if (!load_geometry_chosen)
            show_3mf_info("3mf_unsupported", {msg_unsupported_geometry}, load_3mf_title);
    } else if (en_3mf_file_type == Slic3r::En3mfType::From_Orca) {
        // OrcaSlicer file (has OrcaSlicer tag) - compare file_version with SoftFever_VERSION
        // Migration fix for OrcaSlicer 2.3.1-alpha sparse infill rotation template
        if (load_config && (file_version < app_version) && file_version == Slic3r::Semver("2.3.1-alpha")) {
            offer_rotation_template_fix(config_loaded, dialogs);
        } else if (load_config && (file_version > app_version)) {
            if (config_substitutions.unrecogized_keys.size() > 0) {
                show_3mf_info("3mf_newer",
                              {ui_text("The 3MF file version %s is newer than %s's version %s, found the following unrecognized keys:",
                                       {file_version.to_string_sf(), SLIC3R_APP_FULL_NAME, app_version.to_string_sf()}),
                               literal("\n"), literal("\n\n"), ui_text("You'd better upgrade your software.\n")},
                              newer_3mf_title);
            } else {
                //if the minor version is not matched
                if (file_version.min() != app_version.min()) {
                    show_3mf_info("3mf_newer",
                                  {ui_text("The 3MF file version %s is newer than %s's version %s, we suggest to upgrade your software.",
                                           {file_version.to_string_sf(), SLIC3R_APP_FULL_NAME, app_version.to_string_sf()}),
                                   literal("\n")},
                                  newer_3mf_title);
                }
            }
        } else if (load_config && config_loaded.empty()) {
            load_config = false;
            show_3mf_info("3mf_old_orca", {msg_old_orca_geometry}, load_3mf_title);
        }
    } else if (en_3mf_file_type == Slic3r::En3mfType::From_BBS) {
        // No OrcaSlicer tag - check Bambu/Application version
        Slic3r::Semver orca_tag_start_version(2, 3, 2);
        if (file_version <= orca_tag_start_version) {
            // Compatible old version (before OrcaSlicer tagging was introduced after 2.3.2).
            // Any version prior or equal to 2.3.2 is older than the current one, no version warnings needed.
            // Still apply migration fixes for known old versions.
            if (load_config && (file_version == Slic3r::Semver("2.3.1-alpha"))) {
                offer_rotation_template_fix(config_loaded, dialogs);
            } else if (load_config && config_loaded.empty()) {
                load_config = false;
                show_3mf_info("3mf_older", {msg_older_geometry}, load_3mf_title);
            }
        } else {
            // BambuStudio project (version > 2.3.2 without OrcaSlicer tag)
            // Report that a BambuStudio project is being imported and compare with SLIC3R_VERSION
            Slic3r::Semver slic3r_version = *(Slic3r::Semver::parse(SLIC3R_VERSION));
            if (load_config && config_loaded.empty()) {
                load_config = false;
                show_3mf_info("3mf_bambu", {msg_bambu_geometry}, load_3mf_title);
            } else if (load_config && (file_version > slic3r_version)) {
                // BambuStudio file version is newer than our compatible SLIC3R_VERSION
                if (config_substitutions.unrecogized_keys.size() > 0) {
                    show_3mf_info("3mf_bambu",
                                  {ui_text("The 3MF was created by BambuStudio (version %s), which is newer than the compatible version %s. "
                                           "Found unrecognized settings:",
                                           {file_version.to_string(), slic3r_version.to_string()}),
                                   literal("\n"), literal("\n\n"), ui_text("You'd better upgrade your software.\n")},
                                  bambu_project_title);
                } else {
                    show_3mf_info("3mf_bambu",
                                  {ui_text("The 3MF was created by BambuStudio (version %s), which is newer than the compatible version %s. "
                                           "Some settings may not be fully compatible.",
                                           {file_version.to_string(), slic3r_version.to_string()}),
                                   literal("\n")},
                                  bambu_project_title);
                }
            } else if (load_config) {
                // BambuStudio version is older or same as our SLIC3R_VERSION
                show_3mf_info("3mf_bambu", {ui_text("The 3MF was created by BambuStudio. Some settings may differ from OrcaSlicer.")},
                              bambu_project_title);
            }
        }
    } else if (en_3mf_file_type == Slic3r::En3mfType::From_Other) {
        // Generic CAD/other 3MF without slicer metadata: import geometry silently.
        if (load_config && config_loaded.empty()) {
            load_config = false;
        }
    }

    // plate data: the plates of a project keep their settings, and its objects
    // join the plates they stand on (PartPlateList::reload_all_objects).

    Slic3r::DynamicPrintConfig config;
    if (load_config && !config_loaded.empty()) {
        config.apply(static_cast<const Slic3r::ConfigBase&>(Slic3r::FullPrintConfig::defaults()));
        // and place the loaded config over the base.
        config += std::move(config_loaded);
        std::map<std::string, std::string> validity = config.validate();
        if (!validity.empty()) {
            // NotificationManager::bbl_show_3mf_warn_notification()
            std::string error_message = "\n";
            for (std::map<std::string, std::string>::iterator it = validity.begin(); it != validity.end(); ++it)
                error_message += "-" + it->first + ": " + it->second + "\n";
            error_message += "\n";
            dialogs.inform("3mf_invalid_values",
                           {ui_text("Invalid values found in the 3MF:"), literal(error_message), ui_text("Please correct them in the param tabs")});
        }
    }
    if (!config_substitutions.empty()) {
        show_substitutions_info(config_substitutions.substitutions, boost::filesystem::path(path).filename().string(), dialogs);
    }
    if (load_config) {
        archive.custom_gcodes = model.plates_custom_gcodes;
    }

    if (load_config && !config.empty()) {
        Slic3r::Preset::normalize(config);
        // BBS: modify the prime tower params for old version file
        Slic3r::Semver old_version3(2, 0, 0);
        if ((en_3mf_file_type == Slic3r::En3mfType::From_BBS || en_3mf_file_type == Slic3r::En3mfType::From_Orca) && file_version < old_version3) {
            migrate_prime_tower(config);
        }
        warn_about_modified_gcodes(boost::filesystem::path(path).filename().string(), config, dialogs);
        archive.load_config = true;
        archive.config = std::move(config);
    }
    return model;
}

const Slic3r::DynamicPrintConfig& placing_config(const Archive3mf& archive, const Slic3r::DynamicPrintConfig& current)
{
    return archive.load_config ? archive.config : current;
}

void apply_3mf(Archive3mf& archive, const std::string& file_name, SettingsDialogs& dialogs, ImportedModels& result)
{
    Slic3r::PresetBundle& bundle = *engine().bundle;
    if (!archive.load_config) {
        if (archive.filament_count == 0) {
            return;
        }
        // Sidebar::add_filament()'s colours, one filament at a time.
        std::size_t filament_size = bundle.filament_presets.size();
        while (filament_size < ::MAXIMUM_EXTRUDER_NUMBER && filament_size < archive.filament_count) {
            const std::size_t filament_count = filament_size + 1;
            const std::string new_color = filament_palette()[(filament_count - 1) % filament_palette().size()];
            bundle.set_num_filaments(unsigned(filament_count), new_color);
            ++filament_size;
        }
        reload_tab_after_selection(PresetKind::print);
        bundle.export_selections(*engine().config);
        save_config(engine());
        result.presets_changed = true;
        return;
    }

    // Plater::priv::reset(): the presets the project before brought go.
    bundle.reset_project_embedded_presets();

    // BBS:: project embedded presets
    if (!archive.project_presets.empty()) {
        Slic3r::PresetsConfigSubstitutions preset_substitutions =
            bundle.load_project_embedded_presets(archive.project_presets, Slic3r::ForwardCompatibilitySubstitutionRule::Enable);
        if (!preset_substitutions.empty()) show_substitutions_info(preset_substitutions, dialogs);
    }

    // BBS: save the wipe tower pos in file here, will be used later
    Slic3r::DynamicPrintConfig& config = archive.config;
    std::optional<Slic3r::ConfigOptionFloats> file_wipe_tower_x;
    std::optional<Slic3r::ConfigOptionFloats> file_wipe_tower_y;
    if (auto* wipe_tower_x_opt = config.opt<Slic3r::ConfigOptionFloats>("wipe_tower_x")) file_wipe_tower_x = *wipe_tower_x_opt;
    if (auto* wipe_tower_y_opt = config.opt<Slic3r::ConfigOptionFloats>("wipe_tower_y")) file_wipe_tower_y = *wipe_tower_y_opt;

    bundle.load_config_model(file_name, std::move(config), archive.file_version);

    if (const Slic3r::ConfigOption* bed_type_opt = bundle.project_config.option("curr_bed_type")) {
        // update app config for bed type
        if (bundle.is_bbl_vendor()) engine().config->set("curr_bed_type", std::to_string(bed_type_opt->getInt()));
    }

    // GUI_App::load_current_presets(): every tab shows the selected presets.
    for (const PresetKind kind : {PresetKind::print, PresetKind::filament, PresetKind::printer}) {
        reload_tab(kind);
    }

    Slic3r::DynamicConfig& proj_cfg = bundle.project_config;
    // do some post process after loading config
    {
        //BBS: rewrite wipe tower pos stored in 3mf file , the code above should be seriously reconsidered
        Slic3r::ConfigOptionFloats* wipe_tower_x = proj_cfg.opt<Slic3r::ConfigOptionFloats>("wipe_tower_x");
        Slic3r::ConfigOptionFloats* wipe_tower_y = proj_cfg.opt<Slic3r::ConfigOptionFloats>("wipe_tower_y");
        if (file_wipe_tower_x && wipe_tower_x) *wipe_tower_x = *file_wipe_tower_x;
        if (file_wipe_tower_y && wipe_tower_y) *wipe_tower_y = *file_wipe_tower_y;

        Slic3r::ConfigOptionStrings* filament_color = proj_cfg.opt<Slic3r::ConfigOptionStrings>("filament_colour");
        if (filament_color) {
            size_t filament_count = filament_color->size();

            // Sync filament map
            Slic3r::ConfigOptionInts* filament_map = proj_cfg.opt<Slic3r::ConfigOptionInts>("filament_map", true);
            if (filament_map->size() != filament_count) {
                filament_map->values.resize(filament_count, 1);
            }

            // Sync filament multi colour
            Slic3r::ConfigOptionStrings* filament_multi_color = proj_cfg.opt<Slic3r::ConfigOptionStrings>("filament_multi_colour", true);
            if (filament_multi_color->size() != filament_count) {
                filament_multi_color->values.resize(filament_count);
            }
            // If there is no multi-color data or color is not match, use single color as default value
            for (size_t i = 0; i < filament_count; i++) {
                std::vector<std::string> colors = Slic3r::split_string(filament_multi_color->values[i], ' ');
                if (i >= filament_multi_color->values.size() || colors.empty() || colors[0] != filament_color->values[i]) {
                    filament_multi_color->values[i] = filament_color->values[i];
                }
            }
            // Sync filament colour type
            Slic3r::ConfigOptionStrings* filament_color_type = proj_cfg.opt<Slic3r::ConfigOptionStrings>("filament_colour_type", true);
            if (filament_color_type && filament_color_type->size() != filament_count) {
                filament_color_type->values.resize(filament_count);

                for (size_t i = 0; i < filament_count; i++) {
                    if (i >= filament_color_type->values.size() || filament_color_type->values[i].empty()) {
                        filament_color_type->values[i] = "1";
                    }
                }
            }
        }
    }

    // Plater::load_project(): the app configuration remembers the project's presets.
    bundle.export_selections(*engine().config);
    save_config(engine());
    engine().bundle_follows_config = true;

    // PartPlateList::load_from_3mf_structure(): a plate for each of the
    // file's, with its lock, name and own settings, and the project's values
    // the app keeps with it; the codes on its layers.
    const auto* tower_x = proj_cfg.opt<Slic3r::ConfigOptionFloats>("wipe_tower_x");
    const auto* tower_y = proj_cfg.opt<Slic3r::ConfigOptionFloats>("wipe_tower_y");
    const std::size_t count = std::max<std::size_t>(archive.plate_data.size(), 1);
    for (std::size_t index = 0; index < count; ++index) {
        ProjectPlate& plate = result.plates.emplace_back();
        Slic3r::DynamicPrintConfig own;
        if (index < archive.plate_data.size()) {
            const Slic3r::PlateData& data = *archive.plate_data[index];
            plate.locked = data.locked;
            plate.name = data.plate_name;
            own.apply(data.config);
        }
        const auto keep = [&plate](const std::string& key, const std::string& value) {
            plate.settings.keys.push_back(key);
            plate.settings.values.push_back(value);
        };
        for (const std::string& key : own.keys()) {
            keep(key, own.opt_serialize(key));
        }
        // The project keeps a wipe tower position for every plate, by its index.
        if (tower_x != nullptr && tower_y != nullptr && !tower_x->values.empty() && !tower_y->values.empty()) {
            keep("wipe_tower_x", Slic3r::float_to_string_decimal_point(tower_x->get_at(index), 3));
            keep("wipe_tower_y", Slic3r::float_to_string_decimal_point(tower_y->get_at(index), 3));
        }
        for (const char* key : {"flush_volumes_matrix", "flush_multiplier"}) {
            if (proj_cfg.has(key)) keep(key, proj_cfg.opt_serialize(key));
        }
        const auto codes = archive.custom_gcodes.find(int(index));
        if (codes != archive.custom_gcodes.end()) {
            for (const Slic3r::CustomGCode::Item& item : codes->second.gcodes) {
                plate.layer_gcodes.push_back({item.print_z, static_cast<LayerGcodeType>(item.type), item.extruder, item.color, item.extra});
            }
        }
    }
    result.project = true;
    result.presets_changed = true;
}

}  // namespace orcinus::orca::detail

namespace orcinus::orca {

namespace {

// The wipe tower's position the app keeps among the settings of each plate,
// which the desktop app keeps in PresetBundle::project_config by plate index.
bool is_tower_value(const std::string& key)
{
    return key == "wipe_tower_x" || key == "wipe_tower_y";
}

// The project's flushing volumes the app keeps among the settings of every plate.
bool is_flush_value(const std::string& key)
{
    return key == "flush_volumes_matrix" || key == "flush_multiplier";
}

// The picture of a plate the app rendered: RGBA rows from the bottom up, as
// the desktop app reads its framebuffer.
bool read_thumbnail(const ThumbnailImage& image, Slic3r::ThumbnailData& data)
{
    if (image.path.empty() || image.width <= 0 || image.height <= 0) {
        return false;
    }
    data.set(unsigned(image.width), unsigned(image.height));
    std::ifstream file(image.path, std::ios::binary);
    file.read(reinterpret_cast<char*>(data.pixels.data()), std::streamsize(data.pixels.size()));
    if (!file || !data.is_valid()) {
        data.reset();
        return false;
    }
    return true;
}

}  // namespace

ProjectSave save_project(
    const std::string& path,
    const std::vector<PlateObject>& plate,
    const ProfileSelection& profiles,
    const std::vector<ProjectPlate>& plates,
    const std::string& project_info,
    const int current_plate,
    const SlicedPlates sliced
)
{
    ProjectSave result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    Slic3r::PlateDataPtrs plate_data_list;
    std::vector<Slic3r::Preset*> project_presets;
    // Every plate's pictures, which store_bbs_3mf() takes by plate index, and
    // the plates' first layers (PartPlate::cali_bboxes_data), of which the
    // current plate's is made once it is sliced.
    std::vector<Slic3r::ThumbnailData> thumbnail_data(plates.size());
    std::vector<Slic3r::ThumbnailData> no_light_data(plates.size());
    std::vector<Slic3r::ThumbnailData> top_data(plates.size());
    std::vector<Slic3r::ThumbnailData> pick_data(plates.size());
    std::vector<Slic3r::PlateBBoxData> bbox_data(plates.size());
    try {
        Slic3r::PresetBundle& preset_bundle = *detail::engine().bundle;
        Slic3r::DynamicPrintConfig selected;
        if (const SliceStatus status = detail::select_profiles(preset_bundle, profiles, selected, result.message);
            status != SliceStatus::success) {
            result.status = SceneStatus::profile_not_found;
            return result;
        }
        Slic3r::Model model;
        if (!detail::load_plate(plate, selected, model, result.message)) {
            result.status = SceneStatus::model_read_failed;
            return result;
        }
        if (!project_info.empty()) {
            detail::restore_project_info(model, project_info);
        }
        // The mode the layer slider keeps its codes in (Preview::update_layers_slider_mode).
        const Slic3r::CustomGCode::Mode mode = profiles.filaments.size() > 1 ? Slic3r::CustomGCode::MultiAsSingle : Slic3r::CustomGCode::SingleExtruder;
        for (std::size_t index = 0; index < plates.size(); ++index) {
            if (plates[index].layer_gcodes.empty()) {
                continue;
            }
            Slic3r::CustomGCode::Info info;
            info.mode = mode;
            for (const LayerGcode& code : plates[index].layer_gcodes) {
                info.gcodes.push_back({code.print_z, static_cast<Slic3r::CustomGCode::Type>(code.type), code.extruder, code.color, code.extra});
            }
            std::sort(info.gcodes.begin(), info.gcodes.end());
            model.plates_custom_gcodes[int(index)] = info;
        }

        // The project's values the plates keep: the flushing volumes, alike on
        // every plate, and a wipe tower position for each plate by its index,
        // which a plate without one takes from the first
        // (PartPlateList::create_plate).
        Slic3r::DynamicPrintConfig cfg = preset_bundle.full_config_secure();
        ModelSettings flush;
        std::vector<Slic3r::DynamicPrintConfig> own_configs;
        std::vector<Slic3r::DynamicPrintConfig> tower_configs;
        for (std::size_t index = 0; index < plates.size(); ++index) {
            const ModelSettings& settings = plates[index].settings;
            ModelSettings own;
            ModelSettings tower;
            for (std::size_t i = 0; i < settings.keys.size() && i < settings.values.size(); ++i) {
                const std::string& key = settings.keys[i];
                ModelSettings* target = is_tower_value(key) ? &tower : is_flush_value(key) ? (index == 0 ? &flush : nullptr) : &own;
                if (target != nullptr) {
                    target->keys.push_back(key);
                    target->values.push_back(settings.values[i]);
                }
            }
            own_configs.push_back(detail::model_config(own));
            tower_configs.push_back(detail::model_config(tower));
        }
        cfg.apply(detail::model_config(flush), true);
        for (const char* key : {"wipe_tower_x", "wipe_tower_y"}) {
            auto* positions = cfg.opt<Slic3r::ConfigOptionFloats>(key, true);
            const double first = positions->values.empty() ? 0.0 : positions->get_at(0);
            std::vector<double> values(std::max<std::size_t>(plates.size(), 1), first);
            for (std::size_t index = 0; index < plates.size(); ++index) {
                if (const auto* own = tower_configs[index].opt<Slic3r::ConfigOptionFloats>(key); own != nullptr && !own->values.empty()) {
                    values[index] = own->get_at(0);
                } else if (index > 0) {
                    values[index] = values.front();
                }
            }
            positions->values = values;
        }

        //BBS: add plate logic for thumbnail generate
        std::vector<Slic3r::ThumbnailData*> thumbnails;
        std::vector<Slic3r::ThumbnailData*> no_light_thumbnails;
        std::vector<Slic3r::ThumbnailData*> top_thumbnails;
        std::vector<Slic3r::ThumbnailData*> picking_thumbnails;
        std::vector<Slic3r::PlateBBoxData*> plate_bboxes;
        const int count = int(plates.size());
        for (std::size_t index = 0; index < plates.size(); ++index) {
            read_thumbnail(plates[index].thumbnail, thumbnail_data[index]);
            thumbnails.push_back(&thumbnail_data[index]);
            read_thumbnail(plates[index].no_light_thumbnail, no_light_data[index]);
            no_light_thumbnails.push_back(&no_light_data[index]);
            read_thumbnail(plates[index].top_thumbnail, top_data[index]);
            top_thumbnails.push_back(&top_data[index]);
            read_thumbnail(plates[index].pick_thumbnail, pick_data[index]);
            picking_thumbnails.push_back(&pick_data[index]);
            plate_bboxes.push_back(&bbox_data[index]);
        }
        // The current plate's first layer, while its slice result is valid
        // (generate_first_layer_bbox()).
        if (current_plate >= 0 && current_plate < count && !plates[std::size_t(current_plate)].slice_info_path.empty()) {
            Slic3r::PlateData slice;
            detail::read_slice_info(plates[std::size_t(current_plate)].slice_info_path, slice, bbox_data[std::size_t(current_plate)]);
        }

        // PartPlateList::store_to_3mf_structure() for every plate, with the slice
        // info of the plates sliced whose G-code the file carries.
        for (std::size_t index = 0; index < plates.size(); ++index) {
            Slic3r::PlateData* plate_data_item = new Slic3r::PlateData();
            plate_data_list.push_back(plate_data_item);
            if (const auto* maps = own_configs[index].option<Slic3r::ConfigOptionInts>("filament_map")) {
                plate_data_item->filament_maps = maps->values;
            }
            plate_data_item->locked = plates[index].locked;
            plate_data_item->plate_index = int(index);
            plate_data_item->plate_name = plates[index].name;
            plate_data_item->plate_thumbnail.load_from(thumbnail_data[index]);
            plate_data_item->config.apply(own_configs[index]);
            if (no_light_data[index].is_valid()) {
                plate_data_item->no_light_thumbnail_file = "valid_no_light";
            }
            if (top_data[index].is_valid()) {
                plate_data_item->top_file = "valid_top";
            }
            if (pick_data[index].is_valid()) {
                plate_data_item->pick_file = "valid_pick";
            }
            const ProjectPlate& own = plates[index];
            const bool exported = sliced == SlicedPlates::all || (sliced == SlicedPlates::current && int(index) == current_plate);
            if (exported && !own.slice_info_path.empty() && !own.gcode_path.empty()) {
                Slic3r::PlateBBoxData first_layer;
                if (detail::read_slice_info(own.slice_info_path, *plate_data_item, first_layer)) {
                    if (bbox_data[index].is_valid()) {
                        plate_data_item->pattern_bbox_file = "valid_pattern_bbox";
                    }
                    plate_data_item->gcode_file = own.gcode_path;
                    plate_data_item->is_sliced_valid = true;
                    plate_data_item->first_layer_time = std::to_string(bbox_data[index].first_layer_time);
                }
            }
        }
        // PartPlateList::reload_all_objects(): every copy joins the first plate it meets.
        for (std::size_t obj_id = 0; obj_id < model.objects.size(); ++obj_id) {
            for (std::size_t instance_id = 0; instance_id < model.objects[obj_id]->instances.size(); ++instance_id) {
                const int index = detail::plate_of(*model.objects[obj_id], instance_id, selected, count);
                if (index >= 0) {
                    plate_data_list[std::size_t(index)]->objects_and_instances.emplace_back(int(obj_id), int(instance_id));
                }
            }
        }

        // BBS: backup
        project_presets = preset_bundle.get_current_project_embedded_presets();

        // Plater::save_project(), and Plater::export_gcode_3mf() for a sliced plate's file.
        auto save_strategy = Slic3r::SaveStrategy::SplitModel | Slic3r::SaveStrategy::ShareMesh;
        if (sliced == SlicedPlates::generic) {
            // Plater::export_core_3mf()
            save_strategy = Slic3r::SaveStrategy::Silence;
        } else if (sliced != SlicedPlates::none) {
            save_strategy = Slic3r::SaveStrategy::Silence | Slic3r::SaveStrategy::SplitModel | Slic3r::SaveStrategy::WithGcode |
                            Slic3r::SaveStrategy::SkipModel;
        } else if (detail::engine().config->get_bool("export_sources_full_pathnames")) {
            save_strategy = save_strategy | Slic3r::SaveStrategy::FullPathSources;
        }
        save_strategy = save_strategy | Slic3r::SaveStrategy::UseLoadedId;

        Slic3r::StoreParams store_params;
        store_params.path = path.c_str();
        store_params.model = &model;
        store_params.plate_data_list = plate_data_list;
        // PLATE_CURRENT_IDX, the plate exported, or PLATE_ALL_IDX.
        store_params.export_plate_idx = sliced == SlicedPlates::current ? current_plate : sliced == SlicedPlates::all ? -2 : -1;
        store_params.project_presets = project_presets;
        store_params.config = &cfg;
        store_params.thumbnail_data = thumbnails;
        store_params.no_light_thumbnail_data = no_light_thumbnails;
        store_params.top_thumbnail_data = top_thumbnails;
        store_params.pick_thumbnail_data = picking_thumbnails;
        store_params.id_bboxes = plate_bboxes;
        store_params.strategy = save_strategy | Slic3r::SaveStrategy::Zip64;

        // get type and color for platedata
        auto* nozzle_diameter_option = dynamic_cast<const Slic3r::ConfigOptionFloats*>(cfg.option("nozzle_diameter"));
        std::string nozzle_diameter_str;
        if (nozzle_diameter_option)
            nozzle_diameter_str = nozzle_diameter_option->serialize();
        std::string printer_model_id = preset_bundle.printers.get_edited_preset().get_printer_type(&preset_bundle);
        auto* filament_color = dynamic_cast<const Slic3r::ConfigOptionStrings*>(cfg.option("filament_colour"));
        auto* filament_id_opt = dynamic_cast<const Slic3r::ConfigOptionStrings*>(cfg.option("filament_ids"));
        for (Slic3r::PlateData* plate_data : plate_data_list) {
            plate_data->printer_model_id = printer_model_id;
            plate_data->nozzle_diameters = nozzle_diameter_str;
            for (auto it = plate_data->slice_filaments_info.begin(); it != plate_data->slice_filaments_info.end(); it++) {
                std::string display_filament_type;
                it->type = cfg.get_filament_type(display_filament_type, it->id);
                it->filament_id = filament_id_opt ? filament_id_opt->get_at(it->id) : "";
                it->color = filament_color ? filament_color->get_at(it->id) : "#FFFFFF";
                // save filament info used in curr plate
                if (current_plate >= 0 && std::size_t(current_plate) < plate_bboxes.size()) {
                    plate_bboxes[std::size_t(current_plate)]->filament_ids.push_back(it->id);
                    plate_bboxes[std::size_t(current_plate)]->filament_colors.push_back(it->color);
                }
            }
        }

        if (!Slic3r::store_bbs_3mf(store_params)) {
            // Plater::save_project()'s message box.
            result.status = SceneStatus::write_failed;
            result.message = "Failed to save the project.\nPlease check whether the folder exists online or if other programs open the project file.";
        } else {
            result.status = SceneStatus::success;
        }
    } catch (const std::exception& error) {
        result.status = SceneStatus::write_failed;
        result.message = error.what();
    }
    for (Slic3r::Preset* preset : project_presets) {
        delete preset;
    }
    Slic3r::release_PlateData_list(plate_data_list);
    return result;
}

}  // namespace orcinus::orca
