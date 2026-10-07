#include "orca_engine_adapter.hpp"

// OrcaSlicer's CreateFilamentPresetDialog (CreatePresetsDialog.cpp): a filament
// of the user's own, made from a filament that is already installed. The lists
// the dialog offers are what create_filament_options() answers with, and its
// Create button is create_filament().

#include <algorithm>
#include <cctype>
#include <chrono>
#include <ctime>
#include <iomanip>
#include <map>
#include <set>
#include <sstream>
#include <unordered_map>

#include <boost/algorithm/hex.hpp>
#include <boost/algorithm/string/trim.hpp>
#include <boost/uuid/detail/md5.hpp>

#include "engine_context.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Utils.hpp"
#include "settings_dialogs.hpp"

namespace orcinus::orca {

using detail::engine;
using detail::follow_config;
using detail::save_config;

namespace {

// The vendors CreateFilamentPresetDialog offers (filament_vendors).
const std::vector<std::string>& filament_vendors()
{
    static const std::vector<std::string> vendors = {
        "3Dgenius",     "3DJake",        "3DXTECH",     "3D BEST-Q",     "3D Hero",
        "3D-Fuel",      "Aceaddity",     "AddNorth",    "Amazon Basics", "AMOLEN",
        "Ankermake",    "Anycubic",      "Atomic",      "AzureFilm",     "BASF",
        "Bblife",       "BCN3D",         "Beyond Plastic", "California Filament", "Capricorn",
        "CC3D",         "colorFabb",     "Comgrow",     "Cookiecad",     "Creality",
        "CERPRiSE",     "Das Filament",  "DO3D",        "DOW",           "DREMC",
        "DSM",          "Duramic",       "ELEGOO",      "Eryone",        "Essentium",
        "eSUN",         "Extrudr",       "Fiberforce",  "Fiberlogy",     "FilaCube",
        "Filamentive",  "Fillamentum",   "FLASHFORGE",  "Formfutura",    "Francofil",
        "FusRock",      "FilamentOne",   "Fil X",       "GEEETECH",      "Giantarm",
        "Gizmo Dorks",  "GreenGate3D",   "HATCHBOX",    "Hello3D",       "IC3D",
        "IEMAI",        "IIID Max",      "INLAND",      "iProspect",     "iSANMATE",
        "Justmaker",    "Keene Village Plastics", "Kexcelled", "LDO",    "MakerBot",
        "MatterHackers", "MIKA3D",       "NinjaTek",    "Nobufil",       "Novamaker",
        "OVERTURE",     "OVVNYXE",       "Polymaker",   "Priline",       "Printed Solid",
        "Protopasta",   "Prusament",     "Push Plastic", "R3D",          "re3D",
        "Re-pet3D",     "Recreus",       "Regen",       "RatRig",        "Sain SMART",
        "SliceWorx",    "Snapmaker",     "SnoLabs",     "Spectrum",      "SUNLU",
        "TTYT3D",       "Tianse",        "UltiMaker",   "Valment",       "Verbatim",
        "VO3D",         "Voxelab",       "VOXELPLA",    "YOOPAI",        "Yousu",
        "Ziro",         "Zyltech"};
    return vendors;
}

// nozzle_diameter_map: the nozzles a printer's name may carry.
const std::map<std::string, float>& nozzle_diameters()
{
    static const std::map<std::string, float> diameters = {
        {"0.15", 0.15f}, {"0.2", 0.2f}, {"0.25", 0.25f}, {"0.3", 0.3f},   {"0.35", 0.35f}, {"0.4", 0.4f}, {"0.5", 0.5f},
        {"0.6", 0.6f},   {"0.75", 0.75f}, {"0.8", 0.8f}, {"1.0", 1.0f},   {"1.2", 1.2f},   {"1.75", 1.75f}};
    return diameters;
}

float nozzle_of(const std::string& name)
{
    const auto found = nozzle_diameters().find(name);
    return found == nozzle_diameters().end() ? 0.0f : found->second;
}

std::string lowercase(std::string text)
{
    std::transform(text.begin(), text.end(), text.begin(), [](const unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return text;
}

// caseInsensitiveCompare(): the order the vendor list is shown in.
bool case_insensitive_less(const std::string& left, const std::string& right)
{
    return lowercase(left) < lowercase(right);
}

// get_printer_nozzle_diameter(): the nozzle a printer's name carries.
std::string printer_nozzle_diameter(const std::string& printer_name)
{
    const std::string printer_name_lower = lowercase(printer_name);
    const std::size_t index = printer_name_lower.find(" nozzle)");
    if (index == std::string::npos) {
        const std::size_t plain = printer_name_lower.find(" nozzle");
        if (plain == std::string::npos) {
            return {};
        }
        const std::string nozzle = printer_name_lower.substr(0, plain);
        return nozzle.substr(nozzle.find_last_of(' ') + 1);
    }
    const std::string nozzle = printer_name_lower.substr(0, index);
    return nozzle.substr(nozzle.find_last_of('(') + 1);
}

// sort_printer_by_nozzle(): the printers by the nozzle their name carries. The
// presets of one printer keep their order, the order of the names the dialog
// lists them in (a std::map in the desktop app).
template<typename T>
void sort_printers_by_nozzle(std::vector<std::pair<std::string, T>>& printers)
{
    std::stable_sort(printers.begin(), printers.end(), [](const std::pair<std::string, T>& a, const std::pair<std::string, T>& b) {
        const std::size_t nozzle_index_a = a.first.find(" nozzle");
        const std::size_t nozzle_index_b = b.first.find(" nozzle");
        if (nozzle_index_a == std::string::npos || nozzle_index_b == std::string::npos) {
            return a.first < b.first;
        }
        std::string nozzle_str_a = a.first.substr(0, nozzle_index_a);
        std::string nozzle_str_b = b.first.substr(0, nozzle_index_b);
        nozzle_str_a = nozzle_str_a.substr(nozzle_str_a.find_last_of(' ') + 1);
        nozzle_str_b = nozzle_str_b.substr(nozzle_str_b.find_last_of(' ') + 1);
        const float nozzle_a = nozzle_of(nozzle_str_a);
        const float nozzle_b = nozzle_of(nozzle_str_b);
        if (nozzle_a == nozzle_b) {
            return a.first < b.first;
        }
        return nozzle_a < nozzle_b;
    });
}

// remove_special_key(): what the vendor and the serial may not carry.
std::string remove_special_key(const std::string& text)
{
    static const std::set<char> special_key = {'\n', '\t', '\r', '\v', '@', ';'};
    std::string result;
    for (const char c : text) {
        if (special_key.count(c) == 0) {
            result += c;
        }
    }
    return result;
}

bool all_digits(const std::string& text)
{
    return !text.empty() && std::all_of(text.begin(), text.end(), [](const unsigned char c) { return std::isdigit(c) != 0; });
}

// calculate_md5() of CreatePresetsDialog.cpp, as AppConfig hashes a line.
std::string calculate_md5(const std::string& input)
{
    using boost::uuids::detail::md5;
    md5 md5_hash;
    md5::digest_type md5_digest{};
    std::string md5_digest_str;
    md5_hash.process_bytes(input.data(), input.size());
    md5_hash.get_digest(md5_digest);
    const auto* bytes = reinterpret_cast<const unsigned char*>(&md5_digest[0]);
    boost::algorithm::hex(bytes, bytes + sizeof(md5_digest), std::back_inserter(md5_digest_str));
    return lowercase(md5_digest_str);
}

std::string current_time_text()
{
    const std::time_t time = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
    std::tm local_time{};
    localtime_r(&time, &local_time);
    std::ostringstream stream;
    stream << std::put_time(&local_time, "%Y_%m_%d_%H_%M_%S");
    return stream.str();
}

// The name of a filament without the printer it is made for ("PLA @K2 Plus").
std::string filament_public_name(const std::string& preset_name)
{
    const std::size_t index_at = preset_name.find(" @");
    return index_at == std::string::npos ? preset_name : preset_name.substr(0, index_at);
}

}  // namespace

namespace detail {

// get_filament_id(): the id a filament of this name already has, or a new one.
std::string filament_id_for(Slic3r::PresetBundle& bundle, const std::string& vendor_type_serial)
{
    std::map<std::string, std::set<std::string>> filament_id_to_filament_name;
    for (const auto& [id, presets] : bundle.filaments.get_filament_presets()) {
        if (id.empty()) {
            continue;
        }
        for (const Slic3r::Preset* preset : presets) {
            const std::size_t index_at = preset->name.find_first_of('@');
            if (index_at == std::string::npos) {
                continue;
            }
            const std::string filament_name = preset->name.substr(0, index_at - 1);
            if (filament_name == vendor_type_serial && preset->filament_id != "null") {
                return preset->filament_id;
            }
            filament_id_to_filament_name[preset->filament_id].insert(filament_name);
        }
    }

    std::string user_filament_id = "P" + calculate_md5(vendor_type_serial).substr(0, 7);
    while (filament_id_to_filament_name.find(user_filament_id) != filament_id_to_filament_name.end()) {
        const std::set<std::string>& names = filament_id_to_filament_name.find(user_filament_id)->second;
        if (names.count(vendor_type_serial) != 0) {
            break;
        }
        // Different names correspond to the same filament id
        user_filament_id = "P" + calculate_md5(vendor_type_serial + current_time_text()).substr(0, 7);
    }
    return user_filament_id;
}

}  // namespace detail

namespace {

using detail::filament_id_for;

// The printers the dialog offers presets for (get_all_visible_printer_name).
std::set<std::string> visible_printers(Slic3r::PresetBundle& bundle)
{
    std::set<std::string> printers;
    for (const Slic3r::Preset& printer_preset : bundle.printers.get_presets()) {
        if (printer_preset.is_visible) {
            printers.insert(printer_preset.name);
        }
    }
    return printers;
}

// get_all_filament_presets(): every filament preset that carries a filament id,
// and the types the installed filaments are of.
struct FilamentPresets {
    std::map<std::string, const Slic3r::Preset*> presets;
    std::set<std::string> types;
};

FilamentPresets all_filament_presets(Slic3r::PresetBundle& bundle)
{
    FilamentPresets result;
    for (const Slic3r::Preset& preset : bundle.filaments.get_presets()) {
        if (preset.filament_id.empty() || preset.filament_id == "null") {
            continue;
        }
        const auto* filament_type = preset.config.option<Slic3r::ConfigOptionStrings>("filament_type");
        if (filament_type != nullptr && !filament_type->values.empty()) {
            result.types.insert(filament_type->values[0]);
        }
        if (!preset.is_visible) {
            continue;
        }
        result.presets.emplace(preset.name, &preset);
    }
    return result;
}

// The type a preset prints, which it may take from the preset it inherits.
std::string type_of(Slic3r::PresetBundle& bundle, const Slic3r::Preset& preset)
{
    const Slic3r::Preset* source = &preset;
    const auto* inherits = preset.config.opt<Slic3r::ConfigOptionString>("inherits");
    if (inherits != nullptr && !inherits->value.empty()) {
        if (const Slic3r::Preset* parent = bundle.filaments.find_preset(inherits->value, false, true)) {
            source = parent;
        }
    }
    const auto* filament_type = source->config.option<Slic3r::ConfigOptionStrings>("filament_type");
    return filament_type == nullptr || filament_type->values.empty() ? std::string() : filament_type->values[0];
}

// The presets of a filament by the printer each of them is for, as the check
// boxes of the dialog list them.
std::vector<FilamentPresetChoice> presets_by_printer(
    Slic3r::PresetBundle& bundle,
    const std::set<std::string>& printers,
    const std::vector<const Slic3r::Preset*>& presets
)
{
    std::vector<std::pair<std::string, const Slic3r::Preset*>> printer_to_preset;
    for (const Slic3r::Preset* preset : presets) {
        const auto* compatible_printers = preset->config.opt<Slic3r::ConfigOptionStrings>("compatible_printers");
        if (compatible_printers == nullptr || compatible_printers->values.empty()) {
            // If no compatible printers are defined, add all visible printers
            for (const std::string& visible_printer : printers) {
                if (nozzle_of(printer_nozzle_diameter(visible_printer)) == 0) {
                    continue;
                }
                printer_to_preset.emplace_back(visible_printer, preset);
            }
            continue;
        }
        for (const std::string& compatible_printer_name : compatible_printers->values) {
            if (printers.count(compatible_printer_name) == 0) {
                continue;
            }
            if (nozzle_of(printer_nozzle_diameter(compatible_printer_name)) == 0) {
                continue;
            }
            printer_to_preset.emplace_back(compatible_printer_name, preset);
        }
    }
    sort_printers_by_nozzle(printer_to_preset);
    std::vector<FilamentPresetChoice> choices;
    for (const auto& [printer, preset] : printer_to_preset) {
        choices.push_back(FilamentPresetChoice{printer, preset->name});
    }
    return choices;
}

}  // namespace

CreateFilamentOptions create_filament_options(const std::string& type, const std::string& base_filament)
{
    CreateFilamentOptions result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        Slic3r::PresetBundle& bundle = *engine().bundle;
        result.vendors = filament_vendors();
        std::sort(result.vendors.begin(), result.vendors.end(), case_insensitive_less);

        const FilamentPresets all = all_filament_presets(bundle);
        result.types.assign(all.types.begin(), all.types.end());
        const std::set<std::string> printers = visible_printers(bundle);

        if (type.empty()) {
            result.status = SceneStatus::success;
            return result;
        }

        // get_filament_preset_choices(): the filaments of that type, by the name
        // their presets share; a preset that inherits another is left out.
        std::map<std::string, std::vector<const Slic3r::Preset*>> filaments_by_id;
        for (const auto& [name, preset] : all.presets) {
            const auto* inherits = preset->config.opt<Slic3r::ConfigOptionString>("inherits");
            if (inherits != nullptr && !inherits->value.empty()) {
                continue;
            }
            const auto* filament_type = preset->config.option<Slic3r::ConfigOptionStrings>("filament_type");
            if (filament_type == nullptr || filament_type->values.empty() || filament_type->values[0] != type) {
                continue;
            }
            filaments_by_id[preset->filament_id].push_back(preset);
        }
        int suffix = 0;
        std::map<std::string, std::string> public_name_to_id;
        for (const auto& [id, presets] : filaments_by_id) {
            if (presets.empty()) {
                continue;
            }
            std::set<std::string> names;
            for (const Slic3r::Preset* preset : presets) {
                names.insert(filament_public_name(preset->name));
            }
            for (const std::string& public_name : names) {
                std::string shown = public_name;
                if (public_name_to_id.count(public_name) != 0) {
                    ++suffix;
                    shown = public_name + "_" + std::to_string(suffix);
                }
                public_name_to_id.emplace(shown, id);
                result.base_filaments.push_back(shown);
            }
        }

        // "Create Based on Current Filament": the presets of the chosen filament.
        const auto chosen = public_name_to_id.find(base_filament);
        if (chosen != public_name_to_id.end()) {
            result.presets = presets_by_printer(bundle, printers, filaments_by_id[chosen->second]);
        }

        // "Copy Current Filament Preset": every preset of that type
        // (get_filament_presets_by_machine).
        std::vector<const Slic3r::Preset*> of_type;
        for (const auto& [name, preset] : all.presets) {
            if (type_of(bundle, *preset) == type) {
                of_type.push_back(preset);
            }
        }
        result.copy_presets = presets_by_printer(bundle, printers, of_type);
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

namespace {

PresetCreation creation_failure(const SceneStatus status, std::string message)
{
    PresetCreation result;
    result.status = status;
    result.message = std::move(message);
    return result;
}

}  // namespace

PresetCreation create_filament(const CreateFilamentRequest& request, const DialogAnswers& answers)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return creation_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        detail::SettingsDialogs dialogs(answers);
        const auto ui_text = [](std::string msgid, std::vector<std::string> args = {}) { return detail::ui_text(std::move(msgid), std::move(args)); };

        // The checks of the Create button, in the order the dialog makes them.
        std::string vendor_name = request.vendor;
        const std::string type_name = request.type;
        std::string serial_name = request.serial;
        if (vendor_name.empty()) {
            return creation_failure(SceneStatus::profile_not_found, "Vendor is not selected, please reselect vendor.");
        }
        if (request.custom_vendor && (vendor_name == "Bambu" || vendor_name == "Generic")) {
            return creation_failure(SceneStatus::profile_not_found, "\"Bambu\" or \"Generic\" cannot be used as a Vendor for custom filaments.");
        }
        if (type_name.empty()) {
            return creation_failure(SceneStatus::profile_not_found, "Filament type is not selected, please reselect type.");
        }
        if (serial_name.empty()) {
            return creation_failure(SceneStatus::profile_not_found, "Filament serial is not entered, please enter serial.");
        }
        vendor_name = remove_special_key(vendor_name);
        serial_name = remove_special_key(serial_name);
        if (vendor_name.empty() || serial_name.empty()) {
            return creation_failure(SceneStatus::profile_not_found,
                                  "There may be escape characters in the vendor or serial input of filament. Please delete and re-enter.");
        }
        boost::algorithm::trim(vendor_name);
        boost::algorithm::trim(serial_name);
        if (vendor_name.empty() || serial_name.empty()) {
            return creation_failure(SceneStatus::profile_not_found, "All inputs in the custom vendor or serial are spaces. Please re-enter.");
        }
        if (request.custom_vendor && all_digits(vendor_name)) {
            return creation_failure(SceneStatus::profile_not_found, "The vendor cannot be a number. Please re-enter.");
        }
        if (request.presets.empty()) {
            return creation_failure(SceneStatus::profile_not_found, "You have not selected a printer or preset yet. Please select at least one.");
        }

        const std::string filament_preset_name = vendor_name + " " + (type_name == "PLA-AERO" ? "PLA Aero" : type_name) + " " + serial_name;
        if (bundle.filaments.is_alias_exist(filament_preset_name)) {
            // The dialog asks before it creates a preset whose name is taken.
            dialogs.ask(
                "filament_name_exists",
                {ui_text("The Filament name %1% you created already exists.\nIf you continue, the preset created will be displayed with its full name. "
                         "Do you want to continue?",
                         {filament_preset_name})}
            );
        }
        const std::string user_filament_id = filament_id_for(bundle, filament_preset_name);

        std::vector<std::string> failures;
        for (const FilamentPresetChoice& chosen : request.presets) {
            const Slic3r::Preset* checked_preset = bundle.filaments.find_preset(chosen.preset, false, true);
            if (checked_preset == nullptr) {
                continue;
            }
            Slic3r::DynamicConfig dynamic_config;
            dynamic_config.set_key_value("filament_vendor", new Slic3r::ConfigOptionStrings({vendor_name}));
            dynamic_config.set_key_value("compatible_printers", new Slic3r::ConfigOptionStrings({chosen.printer}));
            dynamic_config.set_key_value("filament_type", new Slic3r::ConfigOptionStrings({type_name}));
            std::vector<std::string> failed;
            const bool cloned = bundle.filaments.clone_presets_for_filament(checked_preset, failed, filament_preset_name, user_filament_id,
                                                                           dynamic_config, chosen.printer);
            if (!cloned) {
                failures.insert(failures.end(), failed.begin(), failed.end());
            }
        }

        if (!failures.empty()) {
            // The presets that are already there are written again once the
            // user says so.
            std::string failure_names;
            for (const std::string& failure : failures) {
                failure_names += failure + "\n";
            }
            dialogs.ask(
                "rewrite_presets",
                {ui_text("Some existing presets have failed to be created, as follows:\n"), ui_text("%1%", {failure_names}),
                 ui_text("\nDo you want to rewrite it?")}
            );
            for (const FilamentPresetChoice& chosen : request.presets) {
                const Slic3r::Preset* checked_preset = bundle.filaments.find_preset(chosen.preset, false, true);
                if (checked_preset == nullptr) {
                    continue;
                }
                Slic3r::DynamicConfig dynamic_config;
                dynamic_config.set_key_value("filament_vendor", new Slic3r::ConfigOptionStrings({vendor_name}));
                dynamic_config.set_key_value("compatible_printers", new Slic3r::ConfigOptionStrings({chosen.printer}));
                dynamic_config.set_key_value("filament_type", new Slic3r::ConfigOptionStrings({type_name}));
                std::vector<std::string> failed;
                bundle.filaments.clone_presets_for_filament(checked_preset, failed, filament_preset_name, user_filament_id, dynamic_config,
                                                            chosen.printer, true);
            }
        }

        bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always);
        bundle.export_selections(*engine().config);
        save_config(engine());
        PresetCreation result;
        result.status = SceneStatus::success;
        result.name = filament_preset_name;
        return result;
    } catch (const detail::QuestionPending& pending) {
        // The app asks the user and creates again with the answer.
        PresetCreation result;
        result.status = SceneStatus::success;
        result.has_question = true;
        result.question = pending.dialog;
        return result;
    } catch (const std::exception& error) {
        return creation_failure(SceneStatus::profile_not_found, error.what());
    }
}

CustomFilaments custom_filaments()
{
    CustomFilaments result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        Slic3r::PresetBundle& bundle = *engine().bundle;
        // GuideFrame::update_custom_filaments(): the filaments of the user's
        // own, by the name their presets share.
        std::vector<std::pair<std::string, std::string>> need_sort;
        for (const auto& [filament_id, presets] : bundle.filaments.get_filament_presets()) {
            if (filament_id.empty() || filament_id == "null") {
                continue;
            }
            bool not_need_show = false;
            std::string filament_name;
            for (const Slic3r::Preset* preset : presets) {
                if (preset->is_system || preset->is_project_embedded) {
                    not_need_show = true;
                    break;
                }
                if (!preset->inherits().empty()) {
                    continue;
                }
                const auto* filament_vendor = preset->config.opt<Slic3r::ConfigOptionStrings>("filament_vendor");
                if (filament_vendor != nullptr && !filament_vendor->values.empty() && filament_vendor->values[0] == "Generic") {
                    not_need_show = true;
                }
                if (filament_name.empty()) {
                    filament_name = filament_public_name(preset->name);
                }
            }
            if (not_need_show || filament_name.empty()) {
                continue;
            }
            need_sort.emplace_back(filament_name, filament_id);
        }
        std::sort(need_sort.begin(), need_sort.end(), [](const auto& a, const auto& b) { return a.first < b.first; });
        for (const auto& [name, id] : need_sort) {
            result.filaments.push_back(CustomFilament{id, name});
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

namespace {

// EditFilamentPresetDialog's constructor: the filament's name, vendor, type and
// serial, from a preset that inherits from no other one when there is one.
void describe_filament(Slic3r::PresetBundle& bundle, const std::string& filament_id, FilamentPresetList& result)
{
    const Slic3r::Preset* basic = nullptr;
    for (const Slic3r::Preset& preset : bundle.filaments.get_presets()) {
        if (preset.is_system || preset.filament_id != filament_id) {
            continue;
        }
        if (basic == nullptr || (preset.inherits().empty() && !basic->inherits().empty())) {
            basic = &preset;
        }
    }
    if (basic == nullptr) {
        return;
    }
    result.name = filament_public_name(basic->name);
    const auto* vendor_names = basic->config.opt<Slic3r::ConfigOptionStrings>("filament_vendor");
    if (vendor_names != nullptr && !vendor_names->values.empty()) {
        result.vendor = vendor_names->values[0];
    }
    const auto* filament_types = basic->config.opt<Slic3r::ConfigOptionStrings>("filament_type");
    if (filament_types != nullptr && !filament_types->values.empty()) {
        result.type = filament_types->values[0];
    }
    const std::string filament_type = result.type == "PLA-AERO" ? "PLA Aero" : result.type;
    const std::size_t index = result.name.find(filament_type);
    if (index != std::string::npos && index + filament_type.size() < result.name.size()) {
        result.serial = result.name.substr(index + filament_type.size());
        if (result.serial.size() > 2 && result.serial[0] == ' ') {
            result.serial = result.serial.substr(1);
        }
    }
}

}  // namespace

FilamentPresetList filament_presets(const std::string& filament_id)
{
    FilamentPresetList result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        Slic3r::PresetBundle& bundle = *engine().bundle;
        // get_same_filament_id_presets(): the user presets of that filament, by
        // the printer each of them is compatible with.
        std::vector<std::pair<std::string, const Slic3r::Preset*>> printer_to_preset;
        for (const Slic3r::Preset& preset : bundle.filaments.get_presets()) {
            if (preset.is_system || preset.filament_id != filament_id) {
                continue;
            }
            // get_filament_compatible_printer()
            const auto* compatible_printers = preset.config.opt<Slic3r::ConfigOptionStrings>("compatible_printers");
            if (compatible_printers == nullptr) {
                continue;
            }
            for (const std::string& printer_name : compatible_printers->values) {
                printer_to_preset.emplace_back(printer_name, &preset);
            }
        }
        sort_printers_by_nozzle(printer_to_preset);
        for (const auto& [printer, preset] : printer_to_preset) {
            result.presets.push_back(FilamentPresetChoice{printer, preset->name});
        }
        // The basic information the dialog shows over the presets.
        describe_filament(bundle, filament_id, result);
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

namespace {

// delete_filament_preset_by_name() of CreatePresetsDialog.cpp: the preset goes,
// and the one to select is another when it was that one.
bool delete_filament_preset_by_name(Slic3r::PresetBundle& bundle, const std::string& delete_preset_name, std::string& selected_preset_name)
{
    if (delete_preset_name.empty()) return false;

    // Find an alternate preset to be selected after the current preset is deleted.
    Slic3r::PresetCollection& m_presets = bundle.filaments;
    if (delete_preset_name == selected_preset_name) {
        const std::deque<Slic3r::Preset>& presets = m_presets.get_presets();
        std::size_t idx_current = m_presets.get_idx_selected();

        // Find the visible preset.
        std::size_t idx_new = idx_current;
        if (idx_current > presets.size()) idx_current = presets.size();
        if (idx_new < presets.size())
            for (; idx_new < presets.size() && (presets[idx_new].name == delete_preset_name || !presets[idx_new].is_visible); ++idx_new)
                ;
        if (idx_new == presets.size())
            for (idx_new = idx_current - 1; idx_new > 0 && (presets[idx_new].name == delete_preset_name || !presets[idx_new].is_visible); --idx_new)
                ;
        selected_preset_name = presets[idx_new].name;
    }

    Slic3r::Preset* need_delete_preset = m_presets.find_preset(delete_preset_name);
    if (need_delete_preset == nullptr) {
        return false;
    }
    if (m_presets.get_edited_preset().name == delete_preset_name) {
        m_presets.discard_current_changes();
    }
    m_presets.delete_preset(need_delete_preset->name);
    return true;
}

// What the filaments select once presets are deleted: next_selected_preset_name,
// which the slots of a deleted preset take too.
void select_after_deletion(Slic3r::PresetBundle& bundle, const std::string& next_selected_preset_name)
{
    bundle.filaments.select_preset_by_name(next_selected_preset_name, true);
    for (std::size_t i = 0; i < bundle.filament_presets.size(); ++i) {
        if (bundle.filaments.find_preset(bundle.filament_presets[i]) == nullptr) {
            bundle.filament_presets[i] = bundle.filaments.get_selected_preset_name();
        }
    }
    bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always);
    bundle.export_selections(*engine().config);
    save_config(engine());
}

// A question the user said No to: the dialog stays as it is.
PresetCreation refused()
{
    PresetCreation result;
    result.status = SceneStatus::success;
    return result;
}

PresetCreation question_of(const detail::QuestionPending& pending)
{
    PresetCreation result;
    result.status = SceneStatus::success;
    result.has_question = true;
    result.question = pending.dialog;
    return result;
}

}  // namespace

PresetCreation delete_filament_preset(const std::string& preset_name, const DialogAnswers& answers)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return creation_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        detail::SettingsDialogs dialogs(answers);
        const auto ui_text = [](std::string msgid, std::vector<std::string> args = {}) { return detail::ui_text(std::move(msgid), std::move(args)); };

        Slic3r::Preset* filament_preset = bundle.filaments.find_preset(preset_name);
        if (filament_preset == nullptr) {
            return creation_failure(SceneStatus::profile_not_found, "Unknown filament profile: " + preset_name);
        }
        // is root preset ?
        bool is_base_preset = false;
        if (bundle.filaments.get_preset_base(*filament_preset) == filament_preset) {
            is_base_preset = true;
            std::string presets;
            int count = 0;
            for (const Slic3r::Preset& other : bundle.filaments) {
                if (other.inherits() == filament_preset->name) {
                    ++count;
                    presets += "\n - " + other.name;
                }
            }
            if (count > 0) {
                // "Presets inherited by other presets cannot be deleted"
                return creation_failure(SceneStatus::profile_not_found, "Presets inherited by other presets cannot be deleted" + presets);
            }
        }
        // wxYES_NO | wxNO_DEFAULT, whose No deletes nothing.
        if (!dialogs.ask(
                "delete_filament_preset",
                {ui_text(is_base_preset ? "Are you sure to delete the selected preset?\n"
                                          "If the preset corresponds to a filament currently in use on your printer, please reset the filament "
                                          "information for that slot." :
                                          "Are you sure to delete the selected preset?")},
                {ui_text("Delete preset")})) {
            return refused();
        }

        // delete preset
        std::string next_selected_preset_name = bundle.filaments.get_selected_preset().name;
        delete_filament_preset_by_name(bundle, preset_name, next_selected_preset_name);
        select_after_deletion(bundle, next_selected_preset_name);
        PresetCreation result;
        result.status = SceneStatus::success;
        result.name = preset_name;
        return result;
    } catch (const detail::QuestionPending& pending) {
        return question_of(pending);
    } catch (const std::exception& error) {
        return creation_failure(SceneStatus::profile_not_found, error.what());
    }
}

PresetCreation delete_filament(const std::string& filament_id, const DialogAnswers& answers)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return creation_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        detail::SettingsDialogs dialogs(answers);
        // WarningDialog with wxYES | wxCANCEL.
        if (!dialogs.ask("delete_filament",
                         {detail::ui_text("All the filament presets belong to this filament would be deleted.\n"
                                          "If you are using this filament on your printer, please reset the filament information for that slot.")},
                         {detail::ui_text("Delete filament")}, {}, detail::ui_text("Cancel"))) {
            return refused();
        }
        // The presets of m_printer_compatible_presets (get_same_filament_id_presets()).
        std::set<std::string> inherit_preset_names;
        std::set<std::string> root_preset_names;
        for (const Slic3r::Preset& preset : bundle.filaments.get_presets()) {
            if (preset.is_system || preset.filament_id != filament_id) {
                continue;
            }
            const auto* compatible_printers = preset.config.opt<Slic3r::ConfigOptionStrings>("compatible_printers");
            if (compatible_printers == nullptr || compatible_printers->values.empty()) {
                continue;
            }
            if (preset.inherits().empty()) {
                root_preset_names.insert(preset.name);
            } else {
                inherit_preset_names.insert(preset.name);
            }
        }
        // delete inherit preset first
        std::string next_selected_preset_name = bundle.filaments.get_selected_preset().name;
        for (const std::string& name : inherit_preset_names) {
            delete_filament_preset_by_name(bundle, name, next_selected_preset_name);
        }
        for (const std::string& name : root_preset_names) {
            delete_filament_preset_by_name(bundle, name, next_selected_preset_name);
        }
        select_after_deletion(bundle, next_selected_preset_name);
        PresetCreation result;
        result.status = SceneStatus::success;
        result.name = filament_id;
        return result;
    } catch (const detail::QuestionPending& pending) {
        return question_of(pending);
    } catch (const std::exception& error) {
        return creation_failure(SceneStatus::profile_not_found, error.what());
    }
}

FilamentPresetList filament_preset_sources(const std::string& filament_id)
{
    FilamentPresetList result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        describe_filament(*engine().bundle, filament_id, result);
        // get_visible_printer_and_compatible_filament_presets() on a copy of the
        // presets (m_preset_bundle), which selects every printer in turn.
        Slic3r::PresetBundle preset_bundle(*engine().bundle);
        const std::deque<Slic3r::Preset>& printer_presets = preset_bundle.printers.get_presets();
        for (const Slic3r::Preset& printer_preset : printer_presets) {
            if (!printer_preset.is_visible) {
                continue;
            }
            if (preset_bundle.printers.get_preset_base(printer_preset) != &printer_preset) continue;
            if (preset_bundle.printers.select_preset_by_name(printer_preset.name, true)) {
                preset_bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always);
                const std::deque<Slic3r::Preset>& filament_presets = preset_bundle.filaments.get_presets();
                for (const Slic3r::Preset& filament_preset : filament_presets) {
                    if (filament_preset.is_default || !filament_preset.is_compatible || filament_preset.is_project_embedded) continue;
                    const Slic3r::Preset* filament_preset_base = preset_bundle.filaments.get_preset_base(filament_preset);
                    const Slic3r::Preset& typed = filament_preset_base == nullptr ? filament_preset : *filament_preset_base;
                    const auto* filament_types = typed.config.option<Slic3r::ConfigOptionStrings>("filament_type");
                    if (filament_types == nullptr || filament_types->values.empty()) continue;
                    const std::string filament_type = filament_types->values[0];
                    std::string filament_type_ = result.type == "PLA Aero" ? "PLA-AERO" : result.type;
                    if (filament_type == filament_type_) {
                        result.presets.push_back(FilamentPresetChoice{printer_preset.name, filament_preset.name});
                    }
                }
            }
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
        result.presets.clear();
    }
    return result;
}

PresetCreation add_filament_preset(const std::string& filament_id, const std::string& printer, const std::string& preset,
                                   const DialogAnswers& answers)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return creation_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        detail::SettingsDialogs dialogs(answers);
        FilamentPresetList filament;
        describe_filament(bundle, filament_id, filament);
        const Slic3r::Preset* filament_preset = bundle.filaments.find_preset(preset, false);
        if (filament_preset == nullptr || printer.empty()) {
            return creation_failure(SceneStatus::profile_not_found, "The filament choice not find filament preset, please reselect it");
        }
        std::vector<std::string> failures;
        Slic3r::DynamicConfig dynamic_config;
        dynamic_config.set_key_value("filament_vendor", new Slic3r::ConfigOptionStrings({filament.vendor}));
        dynamic_config.set_key_value("compatible_printers", new Slic3r::ConfigOptionStrings({printer}));
        dynamic_config.set_key_value("filament_type", new Slic3r::ConfigOptionStrings({filament.type}));
        const bool res = bundle.filaments.clone_presets_for_filament(filament_preset, failures, filament.name, filament_id, dynamic_config, printer);
        if (!res) {
            std::string failure_names;
            for (std::string& failure : failures) {
                failure_names += failure + "\n";
            }
            // wxYES_NO, whose No returns to the dialog.
            if (!dialogs.ask("rewrite_presets",
                             {detail::ui_text("Some existing presets have failed to be created, as follows:\n"), detail::ui_text("%1%", {failure_names}),
                              detail::ui_text("\nDo you want to rewrite it?")})) {
                return refused();
            }
            bundle.filaments.clone_presets_for_filament(filament_preset, failures, filament.name, filament_id, dynamic_config, printer, true);
        }
        // The combo boxes list the new preset as the selected printer suits it.
        bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Never);
        PresetCreation result;
        result.status = SceneStatus::success;
        result.name = filament.name + " @" + printer;
        return result;
    } catch (const detail::QuestionPending& pending) {
        return question_of(pending);
    } catch (const std::exception& error) {
        return creation_failure(SceneStatus::profile_not_found, error.what());
    }
}

}  // namespace orcinus::orca
