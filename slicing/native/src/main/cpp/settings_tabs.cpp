#include "orca_engine_adapter.hpp"

// The settings tabs of the desktop app as requests of the app: the pages and
// values of a tab, a changed field, the undo buttons, the mode, and saving and
// deleting user presets (SavePresetDialog, Tab::save_preset, delete_preset).
//
// The app builds no tab of its own: every request builds the tab the desktop
// app would show, runs the action on it, and describes it again. A question
// abandons the action; the tab and the presets are restored, and the app asks
// the user and requests the action again with the answer.

#include <algorithm>
#include <array>
#include <cfloat>
#include <cstring>
#include <filesystem>
#include <deque>
#include <functional>
#include <memory>
#include <set>
#include <stdexcept>

#include <boost/algorithm/string/predicate.hpp>

#include "engine_context.hpp"
#include "libslic3r/BuildVolume.hpp"
#include "libslic3r/ClipperUtils.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r_version.h"
#include "settings_dialogs.hpp"
#include "settings_fields.hpp"
#include "settings_tab.hpp"
#include "tab_filament.hpp"
#include "tab_print.hpp"
#include "tab_print_model.hpp"
#include "tab_printer.hpp"

namespace orcinus::orca {

using detail::engine;
using detail::follow_config;
using detail::model_config;
using detail::save_config;
using detail::ui_text;

namespace {

// What the tabs keep between requests (Tab's members that outlive one).
struct TabStates {
    detail::TabState print;
    detail::TabState filament;
    detail::TabState printer;
    detail::TabState object;
    detail::TabState part;
    detail::TabState layer;
    detail::TabState plate;
};

TabStates tab_states;

detail::TabState& tab_state(const PresetKind kind)
{
    switch (kind) {
    case PresetKind::filament:
        return tab_states.filament;
    case PresetKind::printer:
        return tab_states.printer;
    case PresetKind::object:
        return tab_states.object;
    case PresetKind::part:
        return tab_states.part;
    case PresetKind::layer:
        return tab_states.layer;
    case PresetKind::plate:
        return tab_states.plate;
    case PresetKind::print:
    default:
        return tab_states.print;
    }
}

// The settings of an object and of the plate are the ones of the process preset.
Slic3r::PresetCollection& preset_collection(Slic3r::PresetBundle& bundle, const PresetKind kind)
{
    switch (kind) {
    case PresetKind::filament:
        return bundle.filaments;
    case PresetKind::printer:
        return bundle.printers;
    case PresetKind::print:
    default:
        return bundle.prints;
    }
}

std::unique_ptr<detail::Tab> make_tab(const PresetKind kind, Slic3r::PresetBundle& bundle, Slic3r::AppConfig& config, detail::SettingsDialogs& dialogs)
{
    detail::TabState& state = tab_state(kind);
    switch (kind) {
    case PresetKind::filament:
        return std::make_unique<detail::TabFilament>(bundle, config, state, dialogs);
    case PresetKind::printer:
        return std::make_unique<detail::TabPrinter>(bundle, config, state, dialogs);
    case PresetKind::object:
        return std::make_unique<detail::TabPrintObject>(bundle, config, state, dialogs);
    case PresetKind::part:
        return std::make_unique<detail::TabPrintPart>(bundle, config, state, dialogs);
    case PresetKind::layer:
        return std::make_unique<detail::TabPrintLayer>(bundle, config, state, dialogs);
    case PresetKind::plate:
        return std::make_unique<detail::TabPrintPlate>(bundle, config, state, dialogs);
    case PresetKind::print:
    default:
        return std::make_unique<detail::TabPrint>(bundle, config, state, dialogs);
    }
}

// The state a question restores: what a tab and its action may change before it
// asks, so the app can ask the user and request the action again.
struct BundleSnapshot {
    explicit BundleSnapshot(const Slic3r::PresetBundle& bundle)
        : prints(bundle.prints.get_edited_preset()),
          filaments(bundle.filaments.get_edited_preset()),
          printers(bundle.printers.get_edited_preset()),
          prints_dirty(bundle.prints.get_selected_preset().is_dirty),
          filaments_dirty(bundle.filaments.get_selected_preset().is_dirty),
          printers_dirty(bundle.printers.get_selected_preset().is_dirty),
          filament_presets(bundle.filament_presets),
          project_config(bundle.project_config),
          states(tab_states)
    {
    }

    void restore(Slic3r::PresetBundle& bundle) const
    {
        bundle.prints.get_edited_preset() = prints;
        bundle.filaments.get_edited_preset() = filaments;
        bundle.printers.get_edited_preset() = printers;
        bundle.prints.get_selected_preset().is_dirty = prints_dirty;
        bundle.filaments.get_selected_preset().is_dirty = filaments_dirty;
        bundle.printers.get_selected_preset().is_dirty = printers_dirty;
        bundle.filament_presets = filament_presets;
        bundle.project_config = project_config;
        tab_states = states;
    }

    Slic3r::Preset prints;
    Slic3r::Preset filaments;
    Slic3r::Preset printers;
    bool prints_dirty;
    bool filaments_dirty;
    bool printers_dirty;
    std::vector<std::string> filament_presets;
    Slic3r::DynamicPrintConfig project_config;
    TabStates states;
};

PresetSettings settings_failure(const SceneStatus status, std::string message)
{
    PresetSettings result;
    result.status = status;
    result.message = std::move(message);
    return result;
}

// Runs action on the tab of kind, showing page, and describes the tab
// afterwards. A question without an answer abandons the action: the presets and
// the tabs are restored, and the result carries only the question.
PresetSettings with_tab(const PresetKind kind, const std::string& page, const DialogAnswers& answers, const ModelSettingsRequest& model,
                        const std::function<void(detail::Tab&)>& action)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return settings_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    Slic3r::PresetBundle& bundle = *engine().bundle;
    try {
        follow_config(engine());
        const BundleSnapshot snapshot(bundle);
        detail::SettingsDialogs dialogs(answers);
        bool loading = true;
        try {
            const std::unique_ptr<detail::Tab> tab = make_tab(kind, bundle, *engine().config, dialogs);
            tab->build();
            if (auto* const model_tab = dynamic_cast<detail::TabPrintModel*>(tab.get())) {
                // TabPrintModel::set_model_config(): the settings of the
                // objects sit on the selected process preset, which is loaded.
                std::vector<Slic3r::DynamicPrintConfig> configs;
                configs.reserve(model.settings.size());
                for (const ModelSettings& settings : model.settings) {
                    configs.push_back(model_config(settings));
                }
                model_tab->set_parent_config(model_config(model.parent));
                model_tab->set_model_config(std::move(configs), model_config(model.plate));
            } else {
                tab->load_selection();
            }
            loading = false;
            tab->activate_page(page);
            if (action) {
                action(*tab);
            }
            return tab->describe();
        } catch (const detail::QuestionPending& pending) {
            snapshot.restore(bundle);
            PresetSettings result;
            result.status = SceneStatus::success;
            result.kind = kind;
            result.has_question = true;
            result.question = pending.dialog;
            result.question_loads_selection = loading;
            return result;
        }
    } catch (const std::exception& error) {
        return settings_failure(SceneStatus::profile_not_found, error.what());
    }
}

}  // namespace

namespace detail {

std::vector<PresetChange> preset_changes(const PresetKind kind)
{
    Slic3r::PresetBundle& bundle = *engine().bundle;
    const DialogAnswers no_answers;
    detail::SettingsDialogs dialogs(no_answers);
    const std::unique_ptr<detail::Tab> tab = make_tab(kind, bundle, *engine().config, dialogs);
    tab->build();
    return tab->describe_changes();
}

void cache_preset_changes(const PresetKind kind)
{
    Slic3r::PresetBundle& bundle = *engine().bundle;
    const DialogAnswers no_answers;
    detail::SettingsDialogs dialogs(no_answers);
    const std::unique_ptr<detail::Tab> tab = make_tab(kind, bundle, *engine().config, dialogs);
    tab->build();
    // The dialog of the desktop app moves every change it lists.
    std::vector<std::string> selected_options;
    for (const PresetChange& change : tab->describe_changes()) {
        if (change.id == "extruders_count") {
            // cache the extruders count
            static_cast<detail::TabPrinter*>(tab.get())->cache_extruder_cnt();
            continue;
        }
        selected_options.push_back(change.id);
    }
    tab->cache_config_diff(selected_options);
}

void cache_transfer(const PresetKind kind, const Slic3r::DynamicPrintConfig& from, const std::vector<std::string>& selected)
{
    Slic3r::PresetBundle& bundle = *engine().bundle;
    const DialogAnswers no_answers;
    detail::SettingsDialogs dialogs(no_answers);
    const std::unique_ptr<detail::Tab> tab = make_tab(kind, bundle, *engine().config, dialogs);
    tab->build();
    // DiffViewCtrl::options(): the keys without the extruder index.
    std::vector<std::string> options;
    for (const std::string& id : selected) {
        options.push_back(id.substr(0, id.find('#')));
    }
    if (kind == PresetKind::printer) {
        auto it = std::find(options.begin(), options.end(), "extruders_count");
        if (it != options.end()) {
            // erase "extruders_count" option from the list
            options.erase(it);
            // cache the extruders count
            static_cast<detail::TabPrinter*>(tab.get())->cache_extruder_cnt(&from);
        }
    }
    tab->cache_config_diff(options, &from);
}

void reload_tab(const PresetKind kind)
{
    // load_current_preset() runs whole, as for a preset the tab has not loaded.
    tab_state(kind).loaded_preset.clear();
    reload_tab_after_selection(kind);
}

void update_extruder_variants_of_tabs(const Slic3r::Preset::Type except, const int extruder_idx)
{
    Slic3r::PresetBundle& bundle = *engine().bundle;
    const DialogAnswers no_answers;
    detail::SettingsDialogs dialogs(no_answers);
    for (const auto& [kind, type] : {std::pair{PresetKind::print, Slic3r::Preset::TYPE_PRINT}, std::pair{PresetKind::filament, Slic3r::Preset::TYPE_FILAMENT},
                                     std::pair{PresetKind::printer, Slic3r::Preset::TYPE_PRINTER}}) {
        if (type == except) {
            continue;
        }
        const std::unique_ptr<detail::Tab> tab = make_tab(kind, bundle, *engine().config, dialogs);
        try {
            tab->build();
            tab->update_extruder_variants(extruder_idx);
            tab->reload_config();
        } catch (const detail::QuestionPending&) {
            // Nothing the variants change asks the user anything.
        }
    }
}

void reload_tab_after_selection(const PresetKind kind)
{
    Slic3r::PresetBundle& bundle = *engine().bundle;
    const DialogAnswers no_answers;
    detail::SettingsDialogs dialogs(no_answers);
    const std::unique_ptr<detail::Tab> tab = make_tab(kind, bundle, *engine().config, dialogs);
    try {
        tab->build();
        tab->load_selection();
    } catch (const detail::QuestionPending&) {
        // Loading the preset asked the user something the selection cannot
        // answer; the values it moved stay as they are.
    }
}

}  // namespace detail

namespace {

// SavePresetDialog::Item::update()
PresetNameValidation validate_preset_name(Slic3r::PresetCollection& presets, const std::string& m_preset_name)
{
    PresetNameCheck m_valid_type = PresetNameCheck::valid;
    std::vector<UiText> info_line;

    const char *unusable_symbols = "<>[]:/\\|?*\"";

    const std::string unusable_suffix = Slic3r::PresetCollection::get_suffix_modified(); //"(modified)";
    for (size_t i = 0; i < std::strlen(unusable_symbols); i++) {
        if (m_preset_name.find_first_of(unusable_symbols[i]) != std::string::npos) {
            info_line    = {ui_text("Name is invalid;"), ui_text("\n"), ui_text("illegal characters:"), ui_text(std::string(" ") + unusable_symbols)};
            m_valid_type = PresetNameCheck::invalid;
            break;
        }
    }

    if (m_valid_type == PresetNameCheck::valid && m_preset_name.find(unusable_suffix) != std::string::npos) {
        info_line    = {ui_text("Name is invalid;"), ui_text("\n"), ui_text("illegal suffix:"), ui_text("\n\t" + Slic3r::PresetCollection::get_suffix_modified())};
        m_valid_type = PresetNameCheck::invalid;
    }

    if (m_valid_type == PresetNameCheck::valid &&
        (m_preset_name == "Default Setting" || m_preset_name == Slic3r::PresetBundle::ORCA_DEFAULT_FILAMENT_PLACEHOLDER || m_preset_name == "Default Printer")) {
        info_line    = {ui_text("Name is unavailable.")};
        m_valid_type = PresetNameCheck::invalid;
    }

    const Slic3r::Preset *existing = presets.find_preset(m_preset_name, false);
    if (m_valid_type == PresetNameCheck::valid && existing && !existing->can_overwrite()) {
        info_line = {ui_text("Overwriting a system profile is not allowed.")};
        m_valid_type = PresetNameCheck::invalid;
    }

    if (m_valid_type == PresetNameCheck::valid && existing && m_preset_name != presets.get_selected_preset_name()) {
        if (existing->is_compatible)
            info_line = {ui_text("Preset \"%1%\" already exists.", {m_preset_name})};
        else
            info_line = {ui_text("Preset \"%1%\" already exists and is incompatible with the current printer.", {m_preset_name})};
        info_line.push_back(ui_text("\n"));
        info_line.push_back(ui_text("Please note that saving will overwrite this preset."));
        m_valid_type = PresetNameCheck::warning;
    }

    if (m_valid_type == PresetNameCheck::valid && m_preset_name.empty()) {
        info_line    = {ui_text("The name is not allowed to be empty.")};
        m_valid_type = PresetNameCheck::invalid;
    }

    if (m_valid_type == PresetNameCheck::valid && m_preset_name.find_first_of(' ') == 0) {
        info_line    = {ui_text("The name is not allowed to start with space character.")};
        m_valid_type = PresetNameCheck::invalid;
    }

    if (m_valid_type == PresetNameCheck::valid && m_preset_name.find_last_of(' ') == m_preset_name.length() - 1) {
        info_line    = {ui_text("The name is not allowed to end with space character.")};
        m_valid_type = PresetNameCheck::invalid;
    }

    if (m_valid_type == PresetNameCheck::valid && presets.get_preset_name_by_alias(m_preset_name) != m_preset_name) {
        info_line    = {ui_text("The name cannot be the same as a preset alias name.")};
        m_valid_type = PresetNameCheck::invalid;
    }

    PresetNameValidation result;
    result.status = SceneStatus::success;
    result.check = m_valid_type;
    result.info = std::move(info_line);
    // BBS: add project embedded presets logic
    result.existing = existing != nullptr;
    result.existing_in_project = existing != nullptr && existing->is_project_embedded;
    result.edited_in_project = presets.get_edited_preset().is_project_embedded;
    return result;
}

// The settings a tab defines itself, which PrintConfigDef does not know.
void append_tab_definitions(const PresetKind kind, std::vector<SettingDefinition>& settings)
{
    if (kind != PresetKind::printer) {
        return;
    }
    // TabPrinter::build_unregular_pages(): the number of extruders.
    SettingDefinition& extruders = settings.emplace_back();
    extruders.key = "extruders_count";
    extruders.type = Slic3r::coInt;
    extruders.label = "Extruders";
    extruders.tooltip = "Number of extruders of the printer.";
    extruders.min = 1;
    extruders.max = MAXIMUM_EXTRUDER_NUMBER;
    extruders.mode = Slic3r::comAdvanced;

    // TabPrinter::build_kinematics_page(): the columns of the normal and the
    // silent mode.
    for (const char* const key : {"full_power_legend", "silent_legend"}) {
        SettingDefinition& legend = settings.emplace_back();
        legend.key = key;
        legend.type = Slic3r::coString;
        legend.gui_type = std::int64_t(Slic3r::ConfigOptionDef::GUIType::legend);
        legend.mode = Slic3r::comDevelop;
        legend.label = std::strcmp(key, "silent_legend") == 0 ? "Silent" : "Normal";
    }
}

const std::vector<std::string>& preset_option_keys(const PresetKind kind)
{
    switch (kind) {
    case PresetKind::filament:
        return Slic3r::Preset::filament_options();
    case PresetKind::printer:
        return Slic3r::Preset::printer_options();
    case PresetKind::plate:
        // The settings of the plate, which are none of the process preset's.
        return detail::plate_setting_keys();
    // The settings of an object or of one of its parts are the ones of the
    // process preset they override.
    case PresetKind::part:
    case PresetKind::layer:
    case PresetKind::object:
    case PresetKind::print:
    default:
        return Slic3r::Preset::print_options();
    }
}

}  // namespace

SettingDefinitions describe_setting_definitions(const PresetKind kind)
{
    SettingDefinitions result;
    for (const std::string& key : preset_option_keys(kind)) {
        const Slic3r::ConfigOptionDef* def = Slic3r::print_config_def.get(key);
        if (def == nullptr) {
            continue;
        }
        SettingDefinition& setting = result.settings.emplace_back();
        setting.key = key;
        setting.type = def->type;
        setting.label = def->label;
        setting.full_label = def->full_label;
        setting.category = def->category;
        setting.tooltip = def->tooltip;
        setting.sidetext = def->sidetext;
        setting.mode = def->mode;
        setting.gui_type = static_cast<std::int64_t>(def->gui_type);
        setting.gui_flags = def->gui_flags;
        setting.enum_values = def->enum_values;
        setting.enum_labels = def->enum_labels;
        setting.min = def->min;
        setting.max = def->max;
        setting.nullable = def->nullable;
        setting.readonly = def->readonly;
        setting.multiline = def->multiline;
        setting.full_width = def->full_width;
        setting.is_code = def->is_code;
        setting.height = def->height;
    }
    append_tab_definitions(kind, result.settings);
    result.status = SceneStatus::success;
    return result;
}

PresetSettings describe_settings(const PresetKind kind, const std::string& page, const DialogAnswers& answers, const ModelSettingsRequest& model)
{
    return with_tab(kind, page, answers, model, nullptr);
}

PastedSettings paste_model_settings(const ModelSettings& clipboard, const ModelSettings& target, const bool part, const ModelSettings& object)
{
    PastedSettings result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        // wxGetApp().get_tab(Preset::TYPE_PRINT)->get_config()
        const Slic3r::DynamicPrintConfig& print = engine().bundle->prints.get_edited_preset().config;
        const Slic3r::DynamicPrintConfig config_cache = model_config(clipboard);
        Slic3r::DynamicPrintConfig config = model_config(target);

        auto keys = config_cache.keys();
        // SettingsFactory::get_options(true)
        const std::vector<std::string> part_options = Slic3r::PrintRegionConfig().keys();
        std::unique_ptr<Slic3r::ConfigOption> extruder(config.option("extruder") ? config.option("extruder")->clone() : nullptr);
        config.clear();

        if (part) {
            const Slic3r::DynamicPrintConfig object_config = model_config(object);
            Slic3r::DynamicPrintConfig compared;
            compared.apply_only(print, keys);
            compared.apply_only(object_config, keys);
            const auto equals = compared.equal(config_cache);
            Slic3r::t_config_option_keys global_keys;
            auto keys2 = object_config.keys();
            std::copy_if(keys2.begin(), keys2.end(), std::back_inserter(global_keys),
                         [&equals](auto& e) { return std::find(equals.begin(), equals.end(), e) == equals.end(); });
            keys.erase(std::remove_if(keys.begin(), keys.end(),
                         [&equals](auto& e) { return std::find(equals.begin(), equals.end(), e) != equals.end(); }), keys.end());
            config.apply_only(print, global_keys);
        }

        for (const std::string& opt_key : keys) {
            if (part && std::find(part_options.begin(), part_options.end(), opt_key) == part_options.end())
                continue; // we can't to add object specific options for the part's(itVolume | itLayer) config

            const Slic3r::ConfigOption* option = config_cache.option(opt_key);
            if (option)
                config.set_key_value(opt_key, option->clone());
        }
        if (extruder)
            config.set_key_value("extruder", extruder.release());
        else
            config.erase("extruder");

        for (const std::string& key : config.keys()) {
            result.settings.keys.push_back(key);
            result.settings.values.push_back(config.opt_serialize(key));
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
        return result;
    }
}

PastedSettings default_layer_config(const ModelSettings& object)
{
    PastedSettings result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        const Slic3r::DynamicPrintConfig object_config = model_config(object);
        const Slic3r::DynamicPrintConfig& print = engine().bundle->prints.get_edited_preset().config;

        Slic3r::DynamicPrintConfig config;
        coordf_t layer_height = object_config.has("layer_height") ?
                                object_config.opt_float("layer_height") :
                                print.opt_float("layer_height");
        config.set_key_value("layer_height",new Slic3r::ConfigOptionFloat(layer_height));
        // BBS
        config.set_key_value("extruder",    new Slic3r::ConfigOptionInt(0));

        for (const std::string& key : config.keys()) {
            result.settings.keys.push_back(key);
            result.settings.values.push_back(config.opt_serialize(key));
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
        return result;
    }
}

PresetSettings change_setting(const PresetKind kind, const std::string& page, const std::string& id, const std::string& text,
                              const DialogAnswers& answers, const ModelSettingsRequest& model)
{
    return with_tab(kind, page, answers, model, [&](detail::Tab& tab) { tab.change_field(id, text); });
}

PresetSettings reset_settings(const PresetKind kind, const std::string& page, const std::vector<std::string>& ids, const DialogAnswers& answers,
                              const ModelSettingsRequest& model)
{
    return with_tab(kind, page, answers, model, [&](detail::Tab& tab) {
        if (ids.empty()) {
            tab.on_roll_back_value();
            return;
        }
        for (const std::string& id : ids) {
            tab.back_to_initial_value(id);
        }
    });
}

PresetSettings set_setting_override(const PresetKind kind, const std::string& page, const std::string& id, const bool enabled,
                                    const DialogAnswers& answers)
{
    return with_tab(kind, page, answers, {}, [&](detail::Tab& tab) { tab.set_override(id, enabled); });
}

namespace {

// Tab::compatible_widget_create(): the non-default presets of the other kind.
std::vector<std::string> compatible_choices(const Slic3r::PresetBundle& bundle, const std::string& key)
{
    std::vector<std::string> names;
    const Slic3r::PrinterTechnology printer_technology = bundle.printers.get_edited_preset().printer_technology();
    const Slic3r::PresetCollection& depending_presets = key == "compatible_printers" ? bundle.printers : bundle.prints;
    for (size_t idx = 0; idx < depending_presets.size(); ++idx) {
        const Slic3r::Preset& preset = depending_presets.preset(idx);
        //BBS: add project embedded preset logic and refine is_external
        bool add = !preset.is_default;
        if (add && key == "compatible_printers") {
            // Only add printers with the same technology as the active printer.
            add &= preset.printer_technology() == printer_technology;
        }
        if (add) {
            names.push_back(preset.name);
        }
    }
    return names;
}

}  // namespace

PresetSettings set_compatible_presets(const PresetKind kind, const std::string& page, const std::string& key,
                                      const std::vector<std::string>& presets, const DialogAnswers& answers)
{
    return with_tab(kind, page, answers, {}, [&](detail::Tab& tab) {
        if (key != "compatible_printers" && key != "compatible_prints") {
            throw std::runtime_error("Unknown list of compatible presets: " + key);
        }
        // Tab::compatible_widget_create(): "leave list empty if all items checked".
        std::vector<std::string> value;
        if (presets.size() != compatible_choices(*engine().bundle, key).size()) {
            value = presets;
        }
        tab.load_key_value(key, value);
    });
}

PresetSettings set_ramming_parameters(const PresetKind kind, const std::string& page, const std::string& parameters, const DialogAnswers& answers)
{
    return with_tab(kind, page, answers, {}, [&parameters](detail::Tab& tab) {
        tab.load_key_value("filament_ramming_parameters", parameters);
        tab.update_changed_ui();
    });
}

namespace {

// BedShapePanel::update_shape(): the points the dialog builds for a shape.
std::vector<Slic3r::Vec2d> bed_shape_points(const BedShapeKind kind, const double size_x, const double size_y, const double origin_x,
                                            const double origin_y, const double diameter, const std::vector<double>& custom_points)
{
    switch (kind) {
    case BedShapeKind::rectangle: {
        if (size_x == 0.0 || size_y == 0.0) {
            return {};
        }
        return {Slic3r::Vec2d(-origin_x, -origin_y),
                Slic3r::Vec2d(size_x - origin_x, -origin_y),
                Slic3r::Vec2d(size_x - origin_x, size_y - origin_y),
                Slic3r::Vec2d(-origin_x, size_y - origin_y)};
    }
    case BedShapeKind::circle: {
        if (diameter == 0.0) {
            return {};
        }
        const double radius = diameter / 2;
        // Don't change this value without adjusting BuildVolume constructor
        // detecting circle diameter (BedShapePanel::update_shape).
        constexpr int edges = 72;
        std::vector<Slic3r::Vec2d> points;
        points.reserve(edges);
        for (int i = 1; i <= edges; ++i) {
            const double angle = i * 2 * PI / edges;
            points.emplace_back(radius * std::cos(angle), radius * std::sin(angle));
        }
        return points;
    }
    case BedShapeKind::custom:
    default: {
        // m_loaded_shape
        std::vector<Slic3r::Vec2d> points;
        for (std::size_t index = 0; index + 1 < custom_points.size(); index += 2) {
            points.emplace_back(custom_points[index], custom_points[index + 1]);
        }
        return points;
    }
    }
}

// Bed_2D::calculate_grid_step() and generate_grid() of slic3r/GUI/2DBed.cpp,
// which belongs to the desktop GUI, carried over unchanged.
int bed_2d_grid_step(const Slic3r::BoundingBox& bb, const double& scale)
{
    // Orca: use 500 x 500 bed size as baseline.
    int min_edge = (bb.size() * (1 / scale)).minCoeff(); // Get short edge
                                           // if the grid is too dense, we increase the step
    return   min_edge >= 6000 ? 100        // Short edge >= 6000mm  Main Grid: 5 x 100 = 500mm
           : min_edge >= 1200 ? 50         // Short edge >= 1200mm  Main Grid: 5 x 50  = 250mm
           : min_edge >= 600  ? 20         // Short edge >= 600mm   Main Grid: 5 x 20  = 100mm
           : 10;                           // Short edge <  600mm   Main Grid: 5 x 10  =  50mm
}

std::vector<Slic3r::Polylines> bed_2d_grid(const Slic3r::ExPolygon& poly, const Slic3r::BoundingBox& bb, const Slic3r::Vec2d& origin, const double& step, const double& scale)
{
    using Slic3r::Point;
    using Slic3r::Polyline;

    Slic3r::Polylines lines_thin, lines_bold;
    int   count = 0;

    // ORCA draw grid lines relative to origin
    for (coord_t x = origin.x(); x >= bb.min(0); x -= step) { // Negative X axis
        (count % 5 ? lines_thin : lines_bold).push_back(Polyline(
            Point(x, bb.min(1)),
            Point(x, bb.max(1))
        ));
        count ++;
    }
    count = 0;
    for (coord_t x = origin.x(); x <= bb.max(0); x += step) { // Positive X axis
        (count % 5 ? lines_thin : lines_bold).push_back(Polyline(
            Point(x, bb.min(1)),
            Point(x, bb.max(1))
        ));
        count ++;
    }
    count = 0;
    for (coord_t y = origin.y(); y >= bb.min(1); y -= step) { // Negative Y axis
        (count % 5 ? lines_thin : lines_bold).push_back(Polyline(
            Point(bb.min(0), y),
            Point(bb.max(0), y)
        ));
        count ++;
    }
    count = 0;
    for (coord_t y = origin.y(); y <= bb.max(1); y += step) { // Positive Y axis
        (count % 5 ? lines_thin : lines_bold).push_back(Polyline(
            Point(bb.min(0), y),
            Point(bb.max(0), y)
        ));
        count ++;
    }

    std::vector<Slic3r::Polylines> grid;
    // clip with a slightly grown expolygon because our lines lay on the contours and may get erroneously clipped
    auto scaled_poly = Slic3r::offset(poly, scale);
    grid.push_back(Slic3r::intersection_pl(lines_thin, scaled_poly));
    grid.push_back(Slic3r::intersection_pl(lines_bold, scaled_poly));
    return grid;
}

}  // namespace

namespace {

// The settings of a printer's host, which are PhysicalPrinter::printer_options()
// without the three the collection keeps for itself. This build of OrcaSlicer
// declares print_host_options() without defining it, so the keys are listed here.
const std::vector<std::string>& print_host_keys()
{
    static const std::vector<std::string> keys = {"bbl_use_printhost",
                                                  "host_type",
                                                  "printer_agent",
                                                  "print_host",
                                                  "print_host_webui",
                                                  "printhost_apikey",
                                                  "flashforge_serial_number",
                                                  "printhost_cafile",
                                                  "printhost_port",
                                                  "printhost_authorization_type",
                                                  "printhost_user",
                                                  "printhost_password",
                                                  "printhost_ssl_ignore_revoke"};
    return keys;
}

// ElegooLink's classify_printer_model(): a Centauri whose name ends in 2.
bool elegoo_is_cc2(const std::string& printer_model)
{
    if (!boost::algorithm::starts_with(printer_model, "Elegoo Centauri"))
        return false;
    const auto last_char = printer_model.find_last_not_of(" \t\r\n");
    return last_char != std::string::npos && printer_model[last_char] == '2';
}

// Http::get_host_header_value(): the host of an address, with its port when it
// names one, as curl's URL parser reads it (http:// in front when there is no scheme).
std::string host_header_value(const std::string& address)
{
    std::string url = address;
    if (url.find("//") == std::string::npos)
        url = "http://" + url;
    std::string authority = url.substr(url.find("//") + 2);
    authority = authority.substr(0, authority.find_first_of("/?#"));
    if (const auto at = authority.rfind('@'); at != std::string::npos)
        authority = authority.substr(at + 1);
    return authority;
}

}  // namespace

PrinterConnection printer_connection()
{
    PrinterConnection result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        Slic3r::PresetBundle& preset_bundle = *engine().bundle;
        Slic3r::DynamicPrintConfig& cfg = preset_bundle.printers.get_edited_preset().config;
        for (const std::string& key : print_host_keys()) {
            if (const Slic3r::ConfigOption* option = cfg.option(key); option != nullptr) {
                result.settings.keys.push_back(key);
                result.settings.values.push_back(option->serialize());
            }
        }
        // A host reads more of the printer's configuration than its own keys:
        // Flashforge's serial console says which firmware it talks to
        // (Flashforge::Flashforge()), ElegooLink tells its printers apart by
        // their model (ElegooLink::ElegooLink()). They are only read; saving
        // keeps the host's keys.
        for (const char* key : {"gcode_flavor", "printer_model"}) {
            if (const Slic3r::ConfigOption* option = cfg.option(key); option != nullptr) {
                result.settings.keys.push_back(key);
                result.settings.values.push_back(option->serialize());
            }
        }

        // PhysicalPrinterDialog::PhysicalPrinterDialog()
        const Slic3r::Preset& sel_preset = preset_bundle.printers.get_selected_preset();
        result.save_name = sel_preset.is_default ? "Untitled" : sel_preset.name;
        result.save_name_copy_suffix = !sel_preset.is_default && sel_preset.is_system;

        // PrintHost::get_print_host_webui()
        std::string webui_url = cfg.opt_string("print_host_webui");
        if (webui_url.empty())
            webui_url = cfg.opt_string("print_host");
        if (!webui_url.empty()) {
            const bool has_http_scheme = boost::algorithm::istarts_with(webui_url, "http");
            const bool has_file_scheme = boost::algorithm::istarts_with(webui_url, "file:");
            if (!has_http_scheme && !has_file_scheme)
                webui_url = "http://" + webui_url;
        }

        // ElegooLink::get_print_host_webui(): a Centauri Carbon 2 shows Elegoo's own
        // LAN page with the printer's access code and address. The app adds the
        // serial number, which takes a request to the printer, and the rest.
        const auto host_type = cfg.option<Slic3r::ConfigOptionEnum<Slic3r::PrintHostType>>("host_type")->value;
        const std::string print_host = cfg.opt_string("print_host");
        if (host_type == Slic3r::htElegooLink && !print_host.empty() && elegoo_is_cc2(cfg.opt_string("printer_model"))) {
            std::string web_path = Slic3r::resources_dir() + "/web/elegoolink/lan_service_web/index.html";
            std::replace(web_path.begin(), web_path.end(), '\\', '/');
            const std::string apikey = cfg.opt_string("printhost_apikey");
            webui_url = "file://" + web_path + "?access_code=" + (apikey.empty() ? std::string("123456") : apikey) +
                        "&ip=" + host_header_value(print_host);
        }

        // Sidebar::update_all_preset_comboboxes()
        if (webui_url.empty()) {
            webui_url = "file://" + Slic3r::resources_dir() + "/web/orca/missing_connection.html";
        } else {
            if (cfg.has("printhost_apikey") && (host_type != Slic3r::htSimplyPrint))
                result.api_key = cfg.opt_string("printhost_apikey");
        }
        result.webui = webui_url;
        result.bbl_device_tab = preset_bundle.use_bbl_device_tab();
        result.printer_type = preset_bundle.printers.get_edited_preset().get_printer_type(&preset_bundle);
        if (const auto* use_3mf = preset_bundle.printers.get_edited_preset().config.option<Slic3r::ConfigOptionBool>("use_3mf")) {
            result.use_3mf = use_3mf->value;
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

PresetSettings save_printer_connection(const ModelSettings& settings, const std::string& name)
{
    {
        const std::lock_guard<std::mutex> engine_lock(engine().mutex);
        if (engine().bundle == nullptr) {
            return settings_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
        }
        try {
            follow_config(engine());
            // The dialog edits the edited printer preset's own configuration.
            std::vector<std::string> keys;
            for (const std::string& key : settings.keys) {
                if (std::find(print_host_keys().begin(), print_host_keys().end(), key) != print_host_keys().end())
                    keys.push_back(key);
            }
            engine().bundle->printers.get_edited_preset().config.apply_only(model_config(settings), keys, true);
        } catch (const std::exception& error) {
            return settings_failure(SceneStatus::write_failed, error.what());
        }
    }
    // PhysicalPrinterDialog::OnOK(): get_tab(TYPE_PRINTER)->save_preset("", false, false, true, m_preset_name)
    return save_preset(PresetKind::printer, name);
}

namespace {

// The presets that were there when the import first ran, which are the ones
// ConfigsOverwriteConfirmDialog asks about; a preset the import brought in
// itself is not asked about when it runs again with an answer.
std::set<std::string> import_asks_about;

std::set<std::string> user_preset_names(Slic3r::PresetBundle& bundle)
{
    std::set<std::string> names;
    const std::array<const Slic3r::PresetCollection*, 3> collections{&bundle.prints, &bundle.filaments, &bundle.printers};
    for (const Slic3r::PresetCollection* presets : collections) {
        for (const Slic3r::Preset& preset : presets->get_presets()) {
            if (preset.can_overwrite()) {
                names.insert(preset.name);
            }
        }
    }
    return names;
}

}  // namespace

ConfigTransfer import_presets(const std::vector<std::string>& paths, const std::map<std::string, ConfigOverwriteAnswer>& answers)
{
    ConfigTransfer result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        Slic3r::PresetBundle& bundle = *engine().bundle;
        if (answers.empty()) {
            import_asks_about = user_preset_names(bundle);
        }
        std::vector<std::string> files = paths;
        std::string asked;
        // MainFrame::load_config_file(): a preset of the same name is replaced
        // once the user has answered ConfigsOverwriteConfirmDialog for it.
        bundle.import_presets(
            files,
            [&answers, &asked](const std::string& name) {
                const auto answer = answers.find(name);
                if (answer != answers.end()) {
                    return static_cast<int>(answer->second);
                }
                if (import_asks_about.count(name) == 0) {
                    // A preset this import brought in itself, which it may write again.
                    return static_cast<int>(ConfigOverwriteAnswer::yes);
                }
                if (asked.empty()) {
                    asked = name;
                }
                // The import goes no further until the app has asked the user.
                return static_cast<int>(ConfigOverwriteAnswer::no_to_all);
            },
            Slic3r::ForwardCompatibilitySubstitutionRule::Enable,
            *engine().config
        );
        // The imported presets are the files that survived the import.
        result.names = files;
        result.overwrite_preset = asked;
        bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always);
        // The app's binder threads call in turn, and AppConfig::save() takes
        // the thread it was last told of for the main one.
        save_config(engine());
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

namespace {

// PresetComboBox::update() of DiffPresetDialog: the visible presets compatible
// with the side's printer and process, every one with show_all; the system
// presets in the collection's order under "System presets", then the others by
// name under "User presets". The box keeps the preset it selects while it lists
// it (PresetComboBox::update()); one it no longer lists falls to SetSelection(1)
// of update_selection(), the first preset under the "System presets" title.
std::vector<PresetItem> diff_combo_items(Slic3r::PresetCollection& collection, const bool show_all, std::string& select_preset_name)
{
    std::vector<PresetItem> items;
    std::set<std::string> nonsys_presets;
    std::string first;
    bool listed = false;
    const std::deque<Slic3r::Preset>& presets = collection.get_presets();
    for (std::size_t i = presets.front().is_visible ? 0 : collection.num_default_presets(); i < presets.size(); ++i) {
        const Slic3r::Preset& preset = presets[i];
        if (!show_all && (!preset.is_visible || !preset.is_compatible)) {
            continue;
        }
        if (select_preset_name.empty()) {
            select_preset_name = preset.name;
        }
        listed = listed || preset.name == select_preset_name;
        if (preset.is_default || preset.is_system) {
            if (first.empty()) {
                first = preset.name;
            }
            items.push_back(PresetItem{preset.name, preset.name, PresetGroup::system, {}, false});
        } else {
            nonsys_presets.insert(preset.name);
        }
    }
    for (const std::string& name : nonsys_presets) {
        if (first.empty()) {
            first = name;
        }
        items.push_back(PresetItem{name, name, PresetGroup::user, {}, false});
    }
    if (!listed) {
        select_preset_name = first;
    }
    for (PresetItem& item : items) {
        item.selected = item.name == select_preset_name;
    }
    return items;
}

// DiffPresetDialog::update_compatibility(): the preset is selected in the side's
// copy of the presets, and a printer or a process makes the presets compatible
// with it.
void update_compatibility(const std::string& preset_name, const Slic3r::Preset::Type type, Slic3r::PresetBundle& preset_bundle)
{
    Slic3r::PresetCollection* presets = type == Slic3r::Preset::TYPE_PRINTER ? &preset_bundle.printers :
                                        type == Slic3r::Preset::TYPE_PRINT   ? &preset_bundle.prints :
                                                                               &preset_bundle.filaments;

    const bool print_tab = type == Slic3r::Preset::TYPE_PRINT;
    const bool printer_tab = type == Slic3r::Preset::TYPE_PRINTER;
    bool technology_changed = false;

    if (printer_tab) {
        const Slic3r::Preset& new_printer_preset = *presets->find_preset(preset_name, true);
        const Slic3r::PrinterTechnology old_printer_technology = presets->get_selected_preset().printer_technology();
        const Slic3r::PrinterTechnology new_printer_technology = new_printer_preset.printer_technology();

        technology_changed = old_printer_technology != new_printer_technology;
    }

    // select preset
    presets->select_preset_by_name(preset_name, false);

    // Mark the print & filament enabled if they are compatible with the currently selected preset.
    // The following method should not discard changes of current print or filament presets on change of a printer profile,
    // if they are compatible with the current printer.
    const auto update_compatible_type = [](const bool technology_changed, const bool on_page, const bool show_incompatible_presets) {
        return technology_changed        ? Slic3r::PresetSelectCompatibleType::Always :
               on_page                   ? Slic3r::PresetSelectCompatibleType::Never :
               show_incompatible_presets ? Slic3r::PresetSelectCompatibleType::OnlyIfWasCompatible :
                                           Slic3r::PresetSelectCompatibleType::Always;
    };
    if (print_tab || printer_tab) {
        preset_bundle.update_compatible(update_compatible_type(technology_changed, print_tab, true), update_compatible_type(technology_changed, false, true));
    }
}

// The combo boxes of one side, printer first: selecting a printer or a process
// in one updates the lists of the boxes below it (the selection_changed
// function of each box).
struct DiffSide {
    std::vector<PresetItem> printers;
    std::vector<PresetItem> prints;
    std::vector<PresetItem> filaments;
    ComparedPresets names;
};

DiffSide select_side(const Slic3r::PresetBundle& app, const ComparedPresets& names, const bool show_all)
{
    DiffSide side;
    side.names = names;
    // update_bundles_from_app()
    const auto bundle = std::make_unique<Slic3r::PresetBundle>(app);
    // The boxes open with the presets the app has selected (update_from_bundle()).
    if (side.names.printer.empty()) {
        side.names.printer = bundle->printers.get_selected_preset_name();
    }
    if (side.names.print.empty()) {
        side.names.print = bundle->prints.get_selected_preset_name();
    }
    if (side.names.filament.empty()) {
        side.names.filament = bundle->filaments.get_selected_preset_name();
    }
    // "Show all presets" does not apply to printers.
    side.printers = diff_combo_items(bundle->printers, false, side.names.printer);
    if (bundle->printers.find_preset(side.names.printer, false) != nullptr) {
        update_compatibility(side.names.printer, Slic3r::Preset::TYPE_PRINTER, *bundle);
    }
    side.prints = diff_combo_items(bundle->prints, show_all, side.names.print);
    if (bundle->prints.find_preset(side.names.print, false) != nullptr) {
        update_compatibility(side.names.print, Slic3r::Preset::TYPE_PRINT, *bundle);
    }
    side.filaments = diff_combo_items(bundle->filaments, show_all, side.names.filament);
    return side;
}

}  // namespace

PresetComparison compare_presets(const ComparedPresets& left, const ComparedPresets& right, const bool show_all)
{
    PresetComparison result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        Slic3r::PresetBundle& bundle = *engine().bundle;
        const DiffSide left_side = select_side(bundle, left, show_all);
        const DiffSide right_side = select_side(bundle, right, show_all);

        // create_presets_sizer(): the printer, filament and process rows.
        struct Row {
            PresetKind kind;
            const std::vector<PresetItem>& left_items;
            const std::vector<PresetItem>& right_items;
            const std::string& left_name;
            const std::string& right_name;
        };
        const Row rows[] = {
            {PresetKind::printer, left_side.printers, right_side.printers, left_side.names.printer, right_side.names.printer},
            {PresetKind::filament, left_side.filaments, right_side.filaments, left_side.names.filament, right_side.names.filament},
            {PresetKind::print, left_side.prints, right_side.prints, left_side.names.print, right_side.names.print},
        };
        detail::SettingsDialogs dialogs{DialogAnswers{}};
        for (const Row& row : rows) {
            PresetKindComparison& compared = result.kinds.emplace_back();
            compared.kind = row.kind;
            compared.left_presets = row.left_items;
            compared.right_presets = row.right_items;
            compared.left = row.left_name;
            compared.right = row.right_name;
            const Slic3r::PresetCollection& presets = preset_collection(bundle, row.kind);
            compared.edited = presets.get_edited_preset().name;
            compared.edited_dirty = presets.get_edited_preset().is_dirty;

            // DiffPresetDialog::update_tree()
            const Slic3r::Preset* left_preset = presets.find_preset(row.left_name);
            const Slic3r::Preset* right_preset = presets.find_preset(row.right_name);
            if (left_preset == nullptr || right_preset == nullptr) {
                compared.problem = "One of the presets does not exist";
                continue;
            }
            if (left_preset->printer_technology() != right_preset->printer_technology()) {
                compared.problem = "Compared presets has different printer technology";
                continue;
            }
            const std::unique_ptr<detail::Tab> tab = make_tab(row.kind, bundle, *engine().config, dialogs);
            tab->build();
            compared.changes = tab->compare(*left_preset, *right_preset);
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

SearchCatalog search_catalog()
{
    SearchCatalog result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        Slic3r::PresetBundle& bundle = *engine().bundle;
        detail::SettingsDialogs dialogs{DialogAnswers{}};
        // Search::OptionsSearcher holds the settings of the three preset tabs,
        // each with the group and the category it was built under.
        for (const PresetKind kind : {PresetKind::print, PresetKind::filament, PresetKind::printer}) {
            const std::unique_ptr<detail::Tab> tab = make_tab(kind, bundle, *engine().config, dialogs);
            tab->build();
            tab->load_selection();
            const PresetSettings described = tab->describe();
            if (described.status != SceneStatus::success) {
                continue;
            }
            for (const SettingsPage& page : described.pages) {
                for (const SettingsGroup& group : page.groups) {
                    for (const SettingsLine& line : group.lines) {
                        for (const SettingsLineOption& option : line.options) {
                            const Slic3r::ConfigOptionDef* definition = Slic3r::print_config_def.get(option.key);
                            if (definition == nullptr) {
                                continue;
                            }
                            SearchOption& found = result.options.emplace_back();
                            found.kind = kind;
                            found.key = option.key;
                            found.id = option.id;
                            found.page = page.label;
                            found.group = {UiText{{}, group.title, {}, 0, {}, false}};
                            // The label of the line when it shows one option,
                            // as OptionsSearcher takes the option's own label.
                            found.label = {UiText{{}, option.label.empty() ? line.label : option.label, {}, 0, {}, false}};
                            found.mode = static_cast<SettingsMode>(definition->mode);
                        }
                    }
                }
            }
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

namespace {

// The ConfigDefs of the placeholders EditGCodeDialog holds as its members.
// ConfigDef::empty() is not const, so they are too; they are only read.
struct PlaceholderDefs {
    Slic3r::ReadOnlySlicingStatesConfigDef cgp_ro_slicing_states_config_def;
    Slic3r::ReadWriteSlicingStatesConfigDef cgp_rw_slicing_states_config_def;
    Slic3r::OtherSlicingStatesConfigDef cgp_other_slicing_states_config_def;
    Slic3r::PrintStatisticsConfigDef cgp_print_statistics_config_def;
    Slic3r::ObjectsInfoConfigDef cgp_objects_info_config_def;
    Slic3r::DimensionsConfigDef cgp_dimensions_config_def;
    Slic3r::TemperaturesConfigDef cgp_temperatures_config_def;
    Slic3r::TimestampsConfigDef cgp_timestamps_config_def;
    Slic3r::OtherPresetsConfigDef cgp_other_presets_config_def;
};

PlaceholderDefs& placeholder_defs()
{
    static PlaceholderDefs defs;
    return defs;
}

// ParamsViewCtrl as EditGCodeDialog fills it: the nodes in the order they are
// appended, each naming the group it is in.
class ParamsList {
public:
    std::int32_t AppendGroup(std::vector<UiText> group_name, const std::string& icon_name)
    {
        return append(-1, std::move(group_name), icon_name);
    }

    std::int32_t AppendSubGroup(const std::int32_t parent, std::vector<UiText> sub_group_name, const std::string& icon_name)
    {
        return append(parent, std::move(sub_group_name), icon_name);
    }

    // ParamsNode::ParamsNode() for a placeholder.
    std::int32_t AppendParam(const std::int32_t parent, const GcodePlaceholderType param_type, const std::string& param_key)
    {
        GcodePlaceholder& node = nodes.emplace_back();
        node.parent = parent;
        node.type = param_type;
        node.key = param_key;
        node.text = param_key;
        if (param_type == GcodePlaceholderType::vector)
            node.text += "[]";
        else if (param_type == GcodePlaceholderType::filament_vector)
            node.text += "[current_extruder]";
        // ParamsInfo
        node.icon = param_type == GcodePlaceholderType::scalar ? "custom-gcode_single"
                  : param_type == GcodePlaceholderType::vector ? "custom-gcode_vector"
                                                               : "custom-gcode_vector-index";
        return std::int32_t(nodes.size() - 1);
    }

    void Expand(const std::int32_t item) { nodes[std::size_t(item)].expanded = true; }

    std::vector<GcodePlaceholder> nodes;

private:
    std::int32_t append(const std::int32_t parent, std::vector<UiText> name, const std::string& icon_name)
    {
        GcodePlaceholder& node = nodes.emplace_back();
        node.parent = parent;
        node.label = std::move(name);
        node.icon = icon_name;
        return std::int32_t(nodes.size() - 1);
    }
};

// get_type() of EditGCodeDialog.cpp
GcodePlaceholderType get_type(const std::string& /*opt_key*/, const Slic3r::ConfigOptionDef& opt_def)
{
    return opt_def.is_scalar() ? GcodePlaceholderType::scalar : GcodePlaceholderType::vector;
}

void append_params(ParamsList& m_params_list, const std::int32_t parent, const Slic3r::ConfigDef& config_def)
{
    for (const auto& [opt_key, def] : config_def.options)
        m_params_list.AppendParam(parent, get_type(opt_key, def), opt_key);
}

std::vector<UiText> label_of(const std::string& msgid, std::vector<std::string> args = {})
{
    return {ui_text(msgid, std::move(args))};
}

// EditGCodeDialog::add_presets_placeholders(): the settings of the three preset
// tabs, a subgroup per page of each tab. The tabs are built as the desktop app
// holds them, for the selected presets.
std::int32_t add_presets_placeholders(ParamsList& m_params_list, Slic3r::PresetBundle& bundle, Slic3r::AppConfig& config)
{
    auto get_set_from_vec = [](const std::vector<std::string>& vec) { return std::set<std::string>(vec.begin(), vec.end()); };

    // Only FFF printers are ported.
    const std::set<std::string> print_options = get_set_from_vec(Slic3r::Preset::print_options());
    const std::set<std::string> material_options = get_set_from_vec(Slic3r::Preset::filament_options());
    const std::set<std::string> printer_options = get_set_from_vec(Slic3r::Preset::printer_options());
    const Slic3r::DynamicPrintConfig full_config = bundle.full_config();

    // Orca: create subgroups from the pages of the tabs
    auto init_from_tab = [&m_params_list, &full_config, &bundle, &config](const std::int32_t parent, const PresetKind kind,
                                                                         const std::set<std::string>& preset_keys) {
        detail::SettingsDialogs dialogs{DialogAnswers{}};
        const std::unique_ptr<detail::Tab> tab = make_tab(kind, bundle, config, dialogs);
        tab->build();
        tab->load_selection();
        const PresetSettings described = tab->describe();

        std::set<std::string> extra_keys(preset_keys);
        for (const SettingsPage& page : described.pages) {
            // The desktop app names the subgroup by Page::title(), the msgid
            // the tab was built with, which it shows untranslated; the app
            // names it as the tab's own list of pages does.
            const std::int32_t subgroup = m_params_list.AppendSubGroup(parent, page.label, page.icon.empty() ? "empty" : page.icon);

            // The ids of the page's options, as optgroup->opt_map() holds them:
            // a value of a vector setting ("retraction_length#0") is no key of
            // the config, so it is not listed on the page.
            std::set<std::string> opt_keys;
            for (const SettingsGroup& group : page.groups)
                for (const SettingsLine& line : group.lines)
                    for (const SettingsLineOption& option : line.options)
                        opt_keys.emplace(option.id);

            for (const std::string& opt_key : opt_keys)
                if (const Slic3r::ConfigOption* optptr = full_config.optptr(opt_key)) {
                    extra_keys.erase(opt_key);
                    m_params_list.AppendParam(subgroup, optptr->is_scalar() ? GcodePlaceholderType::scalar : GcodePlaceholderType::vector, opt_key);
                }
        }
        for (const std::string& opt_key : extra_keys)
            if (const Slic3r::ConfigOption* optptr = full_config.optptr(opt_key))
                m_params_list.AppendParam(parent, optptr->is_scalar() ? GcodePlaceholderType::scalar : GcodePlaceholderType::vector, opt_key);
    };

    const std::int32_t group = m_params_list.AppendGroup(label_of("Presets"), "cog");

    const std::int32_t print = m_params_list.AppendSubGroup(group, label_of("Print settings"), "process");
    init_from_tab(print, PresetKind::print, print_options);

    const std::int32_t material = m_params_list.AppendSubGroup(group, label_of("Filament settings"), "filament");
    init_from_tab(material, PresetKind::filament, material_options);

    const std::int32_t printer = m_params_list.AppendSubGroup(group, label_of("Printer settings"), "printer");
    init_from_tab(printer, PresetKind::printer, printer_options);

    return group;
}

// EditGCodeDialog::init_params_list()
void init_params_list(ParamsList& m_params_list, const std::string& custom_gcode_name, Slic3r::PresetBundle& bundle, Slic3r::AppConfig& config)
{
    PlaceholderDefs& defs = placeholder_defs();
    const auto& custom_gcode_placeholders = Slic3r::custom_gcode_specific_placeholders();
    const auto& specific_params = custom_gcode_placeholders.count(custom_gcode_name) > 0 ? custom_gcode_placeholders.at(custom_gcode_name)
                                                                                         : Slic3r::t_config_option_keys({});

    // Add slicing states placeholders

    std::int32_t slicing_state = m_params_list.AppendGroup(label_of("[Global] Slicing State"), "custom-gcode_slicing-state_global");
    if (!defs.cgp_ro_slicing_states_config_def.empty()) {
        const std::int32_t read_only = m_params_list.AppendSubGroup(slicing_state, label_of("Read Only"), "lock_closed");
        append_params(m_params_list, read_only, defs.cgp_ro_slicing_states_config_def);
    }

    if (!defs.cgp_rw_slicing_states_config_def.empty()) {
        const std::int32_t read_write = m_params_list.AppendSubGroup(slicing_state, label_of("Read Write"), "lock_open");
        append_params(m_params_list, read_write, defs.cgp_rw_slicing_states_config_def);
    }

    // add other universal params, which are related to slicing state
    if (!defs.cgp_other_slicing_states_config_def.empty()) {
        slicing_state = m_params_list.AppendGroup(label_of("Slicing State"), "custom-gcode_slicing-state");
        append_params(m_params_list, slicing_state, defs.cgp_other_slicing_states_config_def);
    }

    // Add universal placeholders

    {
        // Add print statistics subgroup

        if (!defs.cgp_print_statistics_config_def.empty()) {
            const std::int32_t statistics = m_params_list.AppendGroup(label_of("Print Statistics"), "custom-gcode_stats");
            append_params(m_params_list, statistics, defs.cgp_print_statistics_config_def);
        }

        // Add objects info subgroup

        if (!defs.cgp_objects_info_config_def.empty()) {
            const std::int32_t objects_info = m_params_list.AppendGroup(label_of("Objects Info"), "custom-gcode_object-info");
            append_params(m_params_list, objects_info, defs.cgp_objects_info_config_def);
        }

        // Add  dimensions subgroup

        if (!defs.cgp_dimensions_config_def.empty()) {
            const std::int32_t dimensions = m_params_list.AppendGroup(label_of("Dimensions"), "custom-gcode_measure");
            append_params(m_params_list, dimensions, defs.cgp_dimensions_config_def);
        }

        // Add temperature subgroup

        if (!defs.cgp_temperatures_config_def.empty()) {
            const std::int32_t temperatures = m_params_list.AppendGroup(label_of("Temperatures"), "custom-gcode_temperature");
            append_params(m_params_list, temperatures, defs.cgp_temperatures_config_def);
        }

        // Add timestamp subgroup

        if (!defs.cgp_timestamps_config_def.empty()) {
            const std::int32_t dimensions = m_params_list.AppendGroup(label_of("Timestamps"), "custom-gcode_time");
            append_params(m_params_list, dimensions, defs.cgp_timestamps_config_def);
        }
    }

    // Add specific placeholders

    if (!specific_params.empty()) {
        const std::int32_t group = m_params_list.AppendGroup(label_of("Specific for %1%", {custom_gcode_name}), "custom-gcode_gcode");
        for (const auto& opt_key : specific_params)
            if (auto def = Slic3r::custom_gcode_specific_config_def.get(opt_key); def && def->type != Slic3r::coNone) {
                m_params_list.AppendParam(group, get_type(opt_key, *def), opt_key);
            }
        m_params_list.Expand(group);
    }

    // Add placeholders from presets

    const std::int32_t presets = add_presets_placeholders(m_params_list, bundle, config);
    // add other params which are related to presets
    if (!defs.cgp_other_presets_config_def.empty())
        append_params(m_params_list, presets, defs.cgp_other_presets_config_def);
}

}  // namespace

GcodePlaceholders describe_gcode_placeholders(const PresetKind kind, const std::string& key)
{
    GcodePlaceholders result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    if (kind != PresetKind::print && kind != PresetKind::filament && kind != PresetKind::printer) {
        result.status = SceneStatus::profile_not_found;
        result.message = "Custom G-code is edited on the tabs of the presets";
        return result;
    }
    try {
        follow_config(engine());
        Slic3r::PresetBundle& bundle = *engine().bundle;
        {
            // Tab::edit_custom_gcode(): the dialog opens with the tab's G-code.
            detail::SettingsDialogs dialogs{DialogAnswers{}};
            const std::unique_ptr<detail::Tab> tab = make_tab(kind, bundle, *engine().config, dialogs);
            tab->build();
            tab->load_selection();
            result.value = tab->get_custom_gcode(key);
        }
        ParamsList params;
        init_params_list(params, key, bundle, *engine().config);
        result.placeholders = std::move(params.nodes);
        result.status = SceneStatus::success;
    } catch (const detail::QuestionPending&) {
        result.status = SceneStatus::profile_not_found;
        result.message = "The selected presets ask a question before their tab opens";
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

GcodePlaceholderInfo describe_gcode_placeholder(const std::string& key, const bool presets)
{
    // EditGCodeDialog::selection_changed()
    GcodePlaceholderInfo result;
    const std::string& opt_key = key;
    PlaceholderDefs& defs = placeholder_defs();
    const Slic3r::ConfigOptionDef* def{nullptr};

    for (const Slic3r::ConfigDef* config : std::initializer_list<const Slic3r::ConfigDef*>{
             &Slic3r::custom_gcode_specific_config_def,
             &defs.cgp_ro_slicing_states_config_def,
             &defs.cgp_rw_slicing_states_config_def,
             &defs.cgp_other_slicing_states_config_def,
             &defs.cgp_print_statistics_config_def,
             &defs.cgp_objects_info_config_def,
             &defs.cgp_dimensions_config_def,
             &defs.cgp_temperatures_config_def,
             &defs.cgp_timestamps_config_def,
             &defs.cgp_other_presets_config_def,
         }) {
        if (config->has(opt_key)) {
            def = config->get(opt_key);
            break;
        }
    }
    // Orca: move below checking for def in custom defined G-code placeholders
    // This allows custom placeholders to override the default ones for this dialog
    // Override custom def if selection is within the preset category
    // (full_config.def() is print_config_def.) Upstream compares the group's
    // translated name with "Presets", which only holds in English; the app
    // tells whether the placeholder is in that group.
    if (!def || presets) {
        if (const Slic3r::ConfigDef* config_def = &Slic3r::print_config_def; config_def->has(opt_key)) {
            def = config_def->get(opt_key);
        }
    }

    if (def) {
        using namespace Slic3r;
        const ConfigOptionType scalar_type = def->is_scalar() ? def->type : static_cast<ConfigOptionType>(def->type - coVectorType);
        std::string type_str = scalar_type == coNone           ? "none" :
                               scalar_type == coFloat          ? "float" :
                               scalar_type == coInt            ? "integer" :
                               scalar_type == coString         ? "string" :
                               scalar_type == coPercent        ? "percent" :
                               scalar_type == coFloatOrPercent ? "float or percent" :
                               scalar_type == coPoint          ? "point" :
                               scalar_type == coBool           ? "bool" :
                               scalar_type == coEnum           ? "enum" : "undef";
        if (!def->is_scalar())
            type_str += "[]";
        result.type = type_str;

        if (def->full_label.empty() && def->label.empty()) {
            // format_wxstr("%1%\n(%2%)", opt_key, type_str)
        } else if (!def->full_label.empty() && !def->label.empty()) {
            result.label = {ui_text(def->full_label), ui_text(" > "), ui_text(def->label)};
        } else {
            result.label = {ui_text(def->label.empty() ? def->full_label : def->label)};
        }

        if (!def->tooltip.empty())
            result.description = {ui_text(def->tooltip)};
    } else {
        // label = "Undef optptr";
        result.undefined = true;
    }
    return result;
}

PresetSettings edit_custom_gcode(const PresetKind kind, const std::string& page, const std::string& key, const std::string& value,
                                 const DialogAnswers& answers)
{
    return with_tab(kind, page, answers, {}, [&key, &value](detail::Tab& tab) { tab.edit_custom_gcode(key, value); });
}

BedShapeState describe_bed_shape()
{
    BedShapeState result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        const Slic3r::DynamicPrintConfig& config = engine().bundle->printers.get_edited_preset().config;
        const auto* area = config.option<Slic3r::ConfigOptionPoints>("printable_area");
        const std::vector<Slic3r::Vec2d> points = area != nullptr ? area->values : std::vector<Slic3r::Vec2d>();
        // BedShape::BedShape(): the type the dialog opens on.
        const Slic3r::BuildVolume volume(points, 0.0, {}, {});
        switch (volume.type()) {
        case Slic3r::BuildVolume_Type::Circle:
            result.kind = BedShapeKind::circle;
            result.diameter = 2.0 * Slic3r::unscaled<double>(volume.circle().radius);
            break;
        case Slic3r::BuildVolume_Type::Convex:
        case Slic3r::BuildVolume_Type::Custom:
            result.kind = BedShapeKind::custom;
            break;
        case Slic3r::BuildVolume_Type::Rectangle:
        case Slic3r::BuildVolume_Type::Invalid:
        default:
            result.kind = BedShapeKind::rectangle;
            break;
        }
        // BedShape::apply_optgroup_values(): every shape but a circle is
        // described by its bounding box and the place of its origin.
        if (result.kind != BedShapeKind::circle) {
            const Slic3r::BoundingBoxf3& bounds = volume.bounding_volume();
            result.size_x = bounds.size().x();
            result.size_y = bounds.size().y();
            result.origin_x = -bounds.min.x();
            result.origin_y = -bounds.min.y();
        }
        for (const Slic3r::Vec2d& point : points) {
            result.points.push_back(point.x());
            result.points.push_back(point.y());
        }
        if (const auto* texture = config.option<Slic3r::ConfigOptionString>("bed_custom_texture"); texture != nullptr) {
            result.texture = texture->value;
        }
        if (const auto* model = config.option<Slic3r::ConfigOptionString>("bed_custom_model"); model != nullptr) {
            result.model = model->value;
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

PresetSettings set_bed_shape(const BedShapeKind kind, const double size_x, const double size_y, const double origin_x, const double origin_y,
                             const double diameter, const std::vector<double>& custom_points, const std::string& texture, const std::string& model,
                             const DialogAnswers& answers)
{
    std::vector<Slic3r::Vec2d> points;
    try {
        points = bed_shape_points(kind, size_x, size_y, origin_x, origin_y, diameter, custom_points);
    } catch (const std::exception& error) {
        return settings_failure(SceneStatus::model_read_failed, error.what());
    }
    if (points.empty()) {
        return settings_failure(SceneStatus::profile_not_found, "The shape of the plate needs a size");
    }
    // TabPrinter::create_bed_shape_widget(): the dialog writes the three keys
    // into the edited preset, as the desktop app does when it closes with OK.
    return with_tab(PresetKind::printer, {}, answers, {}, [&](detail::Tab& tab) {
        tab.load_key_value("printable_area", points);
        tab.load_key_value("bed_custom_texture", texture);
        tab.load_key_value("bed_custom_model", model);
    });
}

BedShapeState load_bed_shape(const std::string& path)
{
    BedShapeState result;
    result.kind = BedShapeKind::custom;
    if (!boost::algorithm::iends_with(path, ".stl")) {
        result.message = "Invalid file format.";
        return result;
    }
    Slic3r::Model model;
    try {
        model = Slic3r::Model::read_from_file(path);
    } catch (const std::exception&) {
        result.message = "Error! Invalid model";
        return result;
    }
    const Slic3r::ExPolygons expolygons = model.mesh().horizontal_projection();
    if (expolygons.empty()) {
        result.message = "The selected file contains no geometry.";
        return result;
    }
    if (expolygons.size() > 1) {
        result.message = "The selected file contains several disjoint areas. This is not supported.";
        return result;
    }
    Slic3r::Polygon polygon = expolygons[0].contour;
    polygon.make_counter_clockwise();
    for (const Slic3r::Point& point : polygon.points) {
        const Slic3r::Vec2d unscaled = Slic3r::unscale(point);
        result.points.push_back(unscaled.x());
        result.points.push_back(unscaled.y());
    }
    result.status = SceneStatus::success;
    return result;
}

std::vector<double> bed_preview_grid(const std::vector<double>& points)
{
    // Bed_2D::repaint(): the grid of the shape in millimetres, from the origin (m_pos).
    Slic3r::ExPolygon bed_poly;
    for (std::size_t index = 0; index + 1 < points.size(); index += 2) {
        bed_poly.contour.append({points[index], points[index + 1]});
    }
    if (bed_poly.contour.points.size() < 3) {
        return {};
    }
    const Slic3r::BoundingBox bed_bb = bed_poly.contour.bounding_box();
    const int step = bed_2d_grid_step(bed_bb, 1.00);
    const std::vector<Slic3r::Polylines> grid_lines = bed_2d_grid(bed_poly, bed_bb, Slic3r::Vec2d(0, 0), step, 1.00);
    std::vector<double> result{double(step)};
    for (const Slic3r::Polylines& lines : grid_lines) {
        result.push_back(double(lines.size()));
        for (const Slic3r::Polyline& line : lines) {
            result.push_back(double(line.points.size()));
            for (const Slic3r::Point& point : line.points) {
                result.push_back(double(point.x()));
                result.push_back(double(point.y()));
            }
        }
    }
    return result;
}

PresetNames compatible_preset_choices(const PresetKind kind, const std::string& key)
{
    PresetNames result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    follow_config(engine());
    result.names = compatible_choices(*engine().bundle, key);
    result.status = SceneStatus::success;
    return result;
}

std::vector<UiText> setting_tooltip(const PresetKind kind, const std::string& id)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    const Slic3r::ConfigOptionDef* opt = Slic3r::print_config_def.get(id.substr(0, id.find('#')));
    if (engine().bundle == nullptr || opt == nullptr) {
        return {};
    }
    follow_config(engine());

    // get_formatted_tooltip_text()
    std::vector<UiText> tooltip;
    if (!opt->tooltip.empty()) {
        tooltip.push_back(ui_text(opt->tooltip));
    }

    std::string opt_id = id;
    auto hash_pos = opt_id.find("#");
    if (hash_pos != std::string::npos) {
        opt_id.replace(hash_pos, 1,"[");
        opt_id += "]";
    }

    if (!tooltip.empty())
        tooltip.push_back(ui_text("\n\n"));
    tooltip.push_back(ui_text("parameter name"));
    tooltip.push_back(ui_text(": " + opt_id));

    // Orca:
    // We can't use Orca's default values as-is because they sometimes depend on other values.
    // Parent preset configuration values will be used instead.
    const std::string key = id.substr(0, id.find('#'));
    if (const Slic3r::Preset* parent_preset = preset_collection(*engine().bundle, kind).get_selected_preset_parent()) {
        const Slic3r::DynamicPrintConfig& parent_config = parent_preset->config;

        if (!parent_config.has(key))
            return tooltip;

        std::vector<UiText> side_text = {ui_text(opt->sidetext)};

        // Orca: a small hack for `layers` side text: adding a space before it for better looking text
        if (opt->sidetext == "layers")
            side_text = {ui_text(" "), ui_text(opt->sidetext)};

        const auto append = [&tooltip](const std::vector<UiText>& parts) { tooltip.insert(tooltip.end(), parts.begin(), parts.end()); };
        if (opt->type == Slic3r::coFloat || opt->type == Slic3r::coInt || opt->type == Slic3r::coPercent || opt->type == Slic3r::coFloatOrPercent) {
            double default_value = 0.;

            if (opt->type == Slic3r::coFloat)
                default_value = parent_config.option<Slic3r::ConfigOptionFloat>(key)->value;
            else if (opt->type == Slic3r::coInt)
                default_value = parent_config.option<Slic3r::ConfigOptionInt>(key)->value;
            else if (opt->type == Slic3r::coPercent)
                default_value = parent_config.option<Slic3r::ConfigOptionPercent>(key)->value;
            else if (opt->type == Slic3r::coFloatOrPercent) {
                default_value = parent_config.option<Slic3r::ConfigOptionFloatOrPercent>(key)->value;
                if (parent_config.option<Slic3r::ConfigOptionFloatOrPercent>(key)->percent)
                    side_text = {ui_text("%")};
                else if (!opt->sidetext.empty()) {
                    static const std::string postfix = " or %";
                    std::string side = opt->sidetext;
                    auto postfix_pos = side.find(postfix);
                    if (postfix_pos != std::string::npos)
                        side.erase(postfix_pos, postfix.length());
                    side_text = {ui_text(side)};
                }
            }

            append({ui_text("\n\n"), ui_text("Default"), ui_text(": "), ui_text(detail::double_to_string(default_value))});
            append(side_text);

            if (opt->min > -FLT_MAX && opt->max < FLT_MAX) {
                append({ui_text("\n"), ui_text("Range"), ui_text(": ["), ui_text(detail::double_to_string(opt->min))});
                append(side_text);
                append({ui_text(", "), ui_text(detail::double_to_string(opt->max))});
                append(side_text);
                append({ui_text("]")});
            }
        } else if (opt->type == Slic3r::coBool || opt->type == Slic3r::coString) {
            std::string default_value = "";

            if (opt->type == Slic3r::coString)
                default_value = parent_config.option<Slic3r::ConfigOptionString>(key)->value;
            else if (opt->type == Slic3r::coBool)
                default_value = parent_config.option<Slic3r::ConfigOptionBool>(key)->value ? "true" : "false";

            append({ui_text("\n\n"), ui_text("Default"), ui_text(": ")});
            if (default_value.empty()) {
                append({ui_text("Empty string")});
            } else {
                append({ui_text(default_value)});
                append(side_text);
            }
        }
    }
    return tooltip;
}

PresetNameValidation check_preset_name(const PresetKind kind, const std::string& name)
{
    PresetNameValidation result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        return validate_preset_name(preset_collection(*engine().bundle, kind), name);
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
        return result;
    }
}

PresetSettings save_preset(const PresetKind kind, const std::string& name, const bool detach, const bool save_to_project)
{
    {
        const std::lock_guard<std::mutex> engine_lock(engine().mutex);
        if (engine().bundle == nullptr) {
            return settings_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
        }
        try {
            follow_config(engine());
            Slic3r::PresetBundle& bundle = *engine().bundle;
            Slic3r::PresetCollection* m_presets = &preset_collection(bundle, kind);

            // SavePresetDialog::Item::accept()
            const PresetNameValidation validation = validate_preset_name(*m_presets, name);
            if (validation.check == PresetNameCheck::invalid) {
                return settings_failure(SceneStatus::profile_not_found, "The preset name is not valid: " + name);
            }
            if (validation.check == PresetNameCheck::warning) {
                m_presets->delete_preset(name);
            }

            // Tab::save_preset()
            //BBS record current preset name
            const std::string curr_preset_name = m_presets->get_edited_preset().name;

            bool exist_preset = false;
            Slic3r::Preset* new_preset = m_presets->find_preset(name, false);
            if (new_preset) {
                exist_preset = true;
            }

            // Orca: check if compatible_printers exists and is not empty, set it to the current printer if it is empty
            // Ensures that custom filaments based on system are not accidentally allowed for all printers
            // Can still be set for all after creation
            Slic3r::Preset& edited_preset = m_presets->get_edited_preset();
            if (m_presets->type() == Slic3r::Preset::TYPE_FILAMENT && !exist_preset && edited_preset.is_system) {
                const Slic3r::Preset& current_printer = bundle.printers.get_selected_preset_base();
                auto* compatible_printers = edited_preset.config.option<Slic3r::ConfigOptionStrings>("compatible_printers");
                if (compatible_printers != nullptr && compatible_printers->values.empty())
                    compatible_printers->values.push_back(current_printer.name);
            }

            // Save the preset into Slic3r::data_dir / presets / section_name / preset_name.json
            m_presets->save_current_preset(name, detach, save_to_project, nullptr);

            //BBS create new settings
            new_preset = m_presets->find_preset(name, false, true);
            if (!new_preset) {
                return settings_failure(SceneStatus::write_failed, "create new preset failed");
            }

            // set sync_info for sync service
            new_preset->sync_info = exist_preset ? "update" : "create";
            new_preset->save_info();

            // Mark the print & filament enabled if they are compatible with the currently selected preset.
            // If saving the preset changes compatibility with other presets, keep the now incompatible dependent presets selected, however with a "red flag" icon showing that they are no more compatible.
            bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Never);

            //BBS if create a new prset name, preset changed from preset name to new preset name
            // (Sidebar::update_presets_from_to()): every filament slot of the old preset takes the new one.
            if (!exist_preset && kind == PresetKind::filament) {
                for (std::string& filament : bundle.filament_presets) {
                    if (filament == curr_preset_name) {
                        filament = new_preset->name;
                    }
                }
            }

            // Sidebar::update_presets()
            bundle.export_selections(*engine().config);
            save_config(engine());

            // The tab shows the saved preset without loading it again.
            tab_state(kind).loaded_preset = m_presets->get_selected_preset_name();
        } catch (const std::exception& error) {
            return settings_failure(SceneStatus::write_failed, error.what());
        }
    }
    return describe_settings(kind, {}, {});
}

PresetSettings delete_preset(const PresetKind kind, const DialogAnswers& answers)
{
    std::vector<SettingsDialog> notices;
    {
        const std::lock_guard<std::mutex> engine_lock(engine().mutex);
        if (engine().bundle == nullptr) {
            return settings_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
        }
        try {
            follow_config(engine());
            Slic3r::PresetBundle& bundle = *engine().bundle;
            Slic3r::PresetCollection* m_presets = &preset_collection(bundle, kind);
            detail::SettingsDialogs dialogs(answers);
            // Tab::delete_preset(), then Tab::select_preset("", true).
            const auto delete_selected = [&]() {
                auto current_preset = m_presets->get_selected_preset();
                if (current_preset.is_default || !current_preset.can_overwrite()) {
                    throw std::runtime_error("The preset cannot be deleted: " + current_preset.name);
                }
                // TRN  remove/delete
                const std::string action = "Delete";
                bool confirm_delete_third_party_printer = false;
                bool is_base_preset = false;
                if (m_presets->get_preset_base(current_preset) == &current_preset) { //root preset
                    is_base_preset = true;
                    if (current_preset.type == Slic3r::Preset::Type::TYPE_PRINTER && !current_preset.is_system) { //Customize third-party printers
                        int filament_preset_num = 0;
                        int process_preset_num = 0;
                        for (const Slic3r::Preset& preset : bundle.filaments.get_presets()) {
                            if (preset.is_compatible && !preset.is_default) { filament_preset_num++; }
                        }
                        for (const Slic3r::Preset& preset : bundle.prints.get_presets()) {
                            if (preset.is_compatible && !preset.is_default) { process_preset_num++; }
                        }
                        // DeleteConfirmDialog: Cancel, and Delete.
                        if (!dialogs.ask("delete_printer_presets",
                                         {ui_text("%d Filament Preset and %d Process Preset is attached to this printer. Those presets would be deleted if the printer is deleted.",
                                                  {std::to_string(filament_preset_num), std::to_string(process_preset_num)})},
                                         {ui_text(SLIC3R_APP_FULL_NAME " - "), ui_text("Delete")}, ui_text("Delete"), ui_text("Cancel")))
                            return;
                        confirm_delete_third_party_printer = true;
                    }
                    int count = 0;
                    std::string presets;
                    for (auto &preset2 : *m_presets)
                        if (preset2.inherits() == current_preset.name) {
                            ++count;
                            presets += "\n - " + preset2.name;
                        }
                    if (count > 0) {
                        UiText inherit = ui_text("The following presets inherit this preset.");
                        inherit.msgid_plural = "The following preset inherits this preset.";
                        inherit.count = count;
                        dialogs.inform("delete_preset_inherited",
                                       {ui_text("Presets inherited by other presets cannot be deleted!"), ui_text("\n"), inherit, ui_text(presets)},
                                       {ui_text("%1% Preset", {action}, true)}, DialogIcon::error);
                        return;
                    }
                }

                UiText msg = is_base_preset && current_preset.type == Slic3r::Preset::Type::TYPE_FILAMENT
                    ? ui_text("Are you sure to delete the selected preset?\nIf the preset corresponds to a filament currently in use on your printer, please reset the filament information for that slot.")
                    : ui_text("Are you sure to %1% the selected preset?", {action}, true);
                if (!confirm_delete_third_party_printer && !dialogs.ask("delete_preset", {msg}, {ui_text("%1% Preset", {action}, true)}))
                    return;

                // Tab::select_preset(): the filament and process presets of a
                // printer of the user's own go with it.
                bool delete_third_printer = false;
                std::deque<Slic3r::Preset> filament_presets;
                std::deque<Slic3r::Preset> process_presets;
                if (m_presets->get_preset_base(current_preset) == &current_preset && kind == PresetKind::printer && !current_preset.is_system &&
                    !current_preset.is_from_bundle()) {
                    delete_third_printer = true;
                    for (const Slic3r::Preset& preset : bundle.filaments.get_presets()) {
                        if (preset.is_compatible && !preset.is_default) {
                            if (preset.inherits() != "")
                                filament_presets.push_front(preset);
                            else
                                filament_presets.push_back(preset);
                        }
                    }
                    for (const Slic3r::Preset& preset : bundle.prints.get_presets()) {
                        if (preset.is_compatible && !preset.is_default) {
                            if (preset.inherits() != "")
                                process_presets.push_front(preset);
                            else
                                process_presets.push_back(preset);
                        }
                    }
                }

                // Find an alternate preset to be selected after the current preset is deleted.
                const std::deque<Slic3r::Preset> &presets = m_presets->get_presets();
                size_t idx_current = m_presets->get_idx_selected();
                // Find the next visible preset.
                std::string preset_name = presets[idx_current].inherits();
                if (preset_name.empty()) {
                    size_t idx_new = idx_current + 1;
                    if (idx_new < presets.size())
                        for (; idx_new < presets.size() && ! presets[idx_new].is_visible; ++ idx_new) ;
                    if (idx_new == presets.size())
                        for (idx_new = idx_current - 1; idx_new > 0 && ! presets[idx_new].is_visible; -- idx_new);
                    preset_name = presets[idx_new].name;
                }
                m_presets->delete_current_preset();
                m_presets->select_preset_by_name(preset_name, false);
                bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always, Slic3r::PresetSelectCompatibleType::Always);
                // Tab::select_preset() of the next printer: "Orca: update presets for the selected printer".
                if (kind == PresetKind::printer && engine().config->get_bool("remember_printer_config")) {
                    bundle.update_selections(*engine().config);
                }

                if (delete_third_printer) {
                    std::string old_filament_name = bundle.filaments.get_edited_preset().name;
                    std::string old_process_name = bundle.prints.get_edited_preset().name;
                    for (const Slic3r::Preset& preset : filament_presets) {
                        bundle.filaments.delete_preset(preset.name);
                    }
                    for (const Slic3r::Preset& preset : process_presets) {
                        bundle.prints.delete_preset(preset.name);
                    }
                    bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always);
                    bundle.filaments.select_preset_by_name(old_filament_name, true);
                    bundle.prints.select_preset_by_name(old_process_name, true);
                }

                // Sidebar::update_presets()
                bundle.export_selections(*engine().config);
                save_config(engine());
            };
            try {
                delete_selected();
            } catch (const detail::QuestionPending& pending) {
                PresetSettings result;
                result.status = SceneStatus::success;
                result.kind = kind;
                result.has_question = true;
                result.question = pending.dialog;
                result.question.icon = DialogIcon::question;
                return result;
            }
            notices = dialogs.take_notices();
        } catch (const std::exception& error) {
            return settings_failure(SceneStatus::write_failed, error.what());
        }
    }
    // The tab loads the selection that replaced the deleted preset.
    PresetSettings result = describe_settings(kind, {}, {});
    result.notices.insert(result.notices.begin(), notices.begin(), notices.end());
    return result;
}

PresetSettings set_settings_mode(const PresetKind kind, const SettingsMode mode, const ModelSettingsRequest& model)
{
    {
        const std::lock_guard<std::mutex> engine_lock(engine().mutex);
        if (engine().config == nullptr) {
            return settings_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
        }
        // GUI_App::save_mode(): develop mode is not saved.
        const std::string saved = mode == SettingsMode::expert ? "expert" : mode == SettingsMode::advanced ? "advanced" : mode == SettingsMode::simple ? "simple" : "";
        if (!saved.empty()) {
            engine().config->set("user_mode", saved);
            save_config(engine());
        }
    }
    return describe_settings(kind, {}, {}, model);
}

PresetSettings set_settings_variant(const PresetKind kind, const std::string& page, const int variant, const DialogAnswers& answers,
                                    const ModelSettingsRequest& model)
{
    {
        const std::lock_guard<std::mutex> engine_lock(engine().mutex);
        if (engine().config == nullptr) {
            return settings_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
        }
        // wxCUSTOMEVT_MULTISWITCH_SELECTION: the tab reloads with the variant,
        // which it picks up in update_extruder_variants() as it loads.
        tab_state(kind).variant = std::max(variant, 0);
    }
    return describe_settings(kind, page, answers, model);
}

}  // namespace orcinus::orca
