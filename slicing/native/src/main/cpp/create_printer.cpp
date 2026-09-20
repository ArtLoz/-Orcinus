#include "orca_engine_adapter.hpp"

// OrcaSlicer's CreatePrinterPresetDialog (CreatePresetsDialog.cpp): a printer
// of the user's own. Its first page names the printer and its printable area,
// its second page the presets it is made from; create_printer_options()
// answers with what the pages offer, and create_printer() is its Create button.

#include <algorithm>
#include <cmath>
#include <map>
#include <set>
#include <string>
#include <vector>

#include <boost/filesystem.hpp>

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

namespace detail {

// get_filament_id() of the filament dialog (create_presets.cpp), which the
// cloned filament presets take their ids from.
std::string filament_id_for(Slic3r::PresetBundle& bundle, const std::string& vendor_type_serial);

}  // namespace detail

namespace {

// printer_vendors of CreatePresetsDialog.cpp
const std::vector<std::string> printer_vendors = 
    {"Anker",              "Anycubic",           "Artillery",          "Bambulab",           "BIQU",
     "Blocks",             "Chuanying",          "Co Print",           "Comgrow",            "CONSTRUCT3D",
     "Creality",           "DeltaMaker",         "Dremel",             "Elegoo",             "Flashforge",
     "FLSun",              "FlyingBear",         "Folgertech",         "Geeetech",           "Ginger Additive",
     "InfiMech",           "Kingroon",           "Lulzbot",            "MagicMaker",         "Mellow",
     "Orca Arena Printer", "Peopoly",            "Positron 3D",        "Prusa",              "Qidi",
     "Raise3D",            "RatRig",             "re3D",               "RolohaunDesign",     "SecKit",             
     "Snapmaker",          "Sovol",              "Thinker X400",       "Tronxy",             "TwoTrees",           
     "UltiMaker",          "Vivedino",           "Volumic",            "Voron",              "Voxelab",            
     "Vzbot",              "Wanhao",             "Z-Bolt"};

// printer_model_map of CreatePresetsDialog.cpp
const std::map<std::string, std::vector<std::string>> printer_model_map =
    {{"Anker",             {"Anker M5",                   "Anker M5 All-Metal Hot End", "Anker M5C"}},
     {"Anycubic",          {"Anycubic i3 Mega S",    "Anycubic Chiron",       "Anycubic Vyper",        "Anycubic Kobra",        "Anycubic Kobra Max",
                            "Anycubic Kobra Plus",   "Anycubic 4Max Pro",     "Anycubic 4Max Pro 2",   "Anycubic Kobra 2",      "Anycubic Kobra 2 Plus",
                            "Anycubic Kobra 2 Max",  "Anycubic Kobra 2 Pro",  "Anycubic Kobra 2 Neo",  "Anycubic Kobra 3",      "Anycubic Kobra 3 Max", "Anycubic Kobra S1", "Anycubic Predator", }},
     {"Artillery",         {"Artillery Sidewinder X1",      "Artillery Genius",             "Artillery Genius Pro",         "Artillery Sidewinder X2",      "Artillery Hornet",
                            "Artillery Sidewinder X3 Pro",  "Artillery Sidewinder X3 Plus", "Artillery Sidewinder X4 Pro",  "Artillery Sidewinder X4 Plus"}},
     {"Bambulab",          {"Bambu Lab X1 Carbon", "Bambu Lab X1",        "Bambu Lab X1E",       "Bambu Lab P1P",       "Bambu Lab P1S",
                            "Bambu Lab A1 mini",   "Bambu Lab A1"}},
     {"BIQU",              {"BIQU B1",      "BIQU BX",      "BIQU Hurakan"}},
     {"Blocks",            {"BLOCKS Pro S100", "BLOCKS RD50 V2",  "BLOCKS RF50"}},
     {"Chuanying",         {"Chuanying X1"}},
     {"Co Print",          {"Co Print ChromaSet"}},
     {"Comgrow",           {"Comgrow T300", "Comgrow T500"}},
     {"CONSTRUCT3D",       {"Construct 1 XL", "Construct 1"}},
     {"Creality",          {"Creality CR-10 V2",           "Creality CR-10 Max",          "Creality CR-10 SE",           "Creality CR-6 SE",            "Creality CR-6 Max",
                            "Creality CR-M4",              "Creality Ender-3 V2",         "Creality Ender-3 V2 Neo",     "Creality Ender-3 S1",         "Creality Ender-3",
                            "Creality Ender-3 Pro",        "Creality Ender-3 S1 Pro",     "Creality Ender-3 S1 Plus",    "Creality Ender-3 V3 SE",      "Creality Ender-3 V3 KE",
                            "Creality Ender-3 V3",         "Creality Ender-3 V3 Plus",    "Creality Ender-5",            "Creality Ender-5 Max",        "Creality Ender-5 Plus",
                            "Creality Ender-5 Pro (2019)", "Creality Ender-5S",           "Creality Ender-5 S1",         "Creality Ender-6",            "Creality Sermoon V1",
                            "Creality K1",                 "Creality K1C",                "Creality K1 Max",             "Creality K1 SE",              "Creality K2 Plus",
                            "Creality Hi"}},
     {"DeltaMaker",        {"DeltaMaker 2",   "DeltaMaker 2T",  "DeltaMaker 2XT"}},
     {"Dremel",            {"Dremel 3D20", "Dremel 3D40", "Dremel 3D45"}},
     {"Elegoo",            {"Elegoo Centauri Carbon 2", "Elegoo Centauri Carbon",  "Elegoo Centauri",         "Elegoo Neptune",          "Elegoo Neptune X",        "Elegoo Neptune 2",
                            "Elegoo Neptune 2S",       "Elegoo Neptune 2D",       "Elegoo Neptune 3",        "Elegoo Neptune 3 Pro",    "Elegoo Neptune 3 Plus",
                            "Elegoo Neptune 3 Max",    "Elegoo Neptune 4 Pro",    "Elegoo Neptune 4",        "Elegoo Neptune 4 Max",    "Elegoo Neptune 4 Plus",
                            "Elegoo OrangeStorm Giga"}},
     {"Flashforge",        {"Flashforge Adventurer 5M",       "Flashforge Adventurer 5M Pro",   "Flashforge AD5X",                "Flashforge Adventurer 3 Series", "Flashforge Adventurer 4 Series",
                            "Flashforge Guider 3 Ultra",      "Flashforge Guider 2s", "Flashforge Artemis"}},
     {"FLSun",             {"FLSun Q5",               "FLSun QQ-S Pro",         "FLSun Super Racer (SR)", "FLSun V400",             "FLSun T1",
                            "FLSun S1"}},
     {"FlyingBear",        {"FlyingBear Reborn3", "FlyingBear S1",      "FlyingBear Ghost 6"}},
     {"Folgertech",        {"Folgertech i3",   "Folgertech FT-5", "Folgertech FT-6"}},
     {"Geeetech",          {"Geeetech Thunder",   "Geeetech Mizar M",   "Geeetech Mizar S",   "Geeetech Mizar Pro", "Geeetech Mizar Max",
                            "Geeetech Mizar",     "Geeetech A10 Pro",   "Geeetech A10 M",     "Geeetech A10 T",     "Geeetech A20",
                            "Geeetech A20 M",     "Geeetech A20 T",     "Geeetech A30 Pro",   "Geeetech A30 M",     "Geeetech A30 T",
                            "Geeetech M1"}},
     {"Ginger Additive",   {"ginger G1"}},
     {"InfiMech",          {"InfiMech TX",                       "InfiMech TX Hardened Steel Nozzle"}},
     {"Kingroon",          {"Kingroon KP3S PRO S1", "Kingroon KP3S PRO V2", "Kingroon KP3S 3.0",    "Kingroon KP3S V1",     "Kingroon KLP1"}},
     {"Lulzbot",           {"Lulzbot Taz 6",        "Lulzbot Taz 4 or 5",   "Lulzbot Taz Pro Dual", "Lulzbot Taz Pro S"}},
     {"MagicMaker",        {"MM hqs hj",   "MM hqs SF",   "MM hj SK",    "MM BoneKing", "MM slb"}},
     {"Mellow",            {"M1"}},
     {"Orca Arena Printer",{"Orca Arena X1 Carbon"}},
     {"Peopoly",           {"Peopoly Magneto X"}},
     {"Positron 3D",       {"The Positron"}},
     {"Prusa",             {"Prusa CORE One", "Prusa CORE One HF", "Prusa CORE One L", "Prusa CORE One L HF", "MK4IS", "MK4S", "MK4S HF",
                            "Prusa XL", "Prusa XL 5T", "MK3.5", "MK3S", "MINI", "MINIIS"}},
     {"Qidi",              {"Qidi X-Plus 4",  "Qidi Q1 Pro",    "Qidi X-Max 3",   "Qidi X-Plus 3",  "Qidi X-Smart 3",
                            "Qidi X-Plus",    "Qidi X-Max",     "Qidi X-CF Pro"}},
     {"Raise3D",           {"Raise3D Pro3",      "Raise3D Pro3 Plus"}},
     {"RatRig",            {"RatRig V-Core 3 200",                  "RatRig V-Core 3 300",                  "RatRig V-Core 3 400",                  "RatRig V-Core 3 500",                  "RatRig V-Minion",
                            "RatRig V-Cast",                        "RatRig V-Core 4 300",                  "RatRig V-Core 4 400",                  "RatRig V-Core 4 500",                  "RatRig V-Core 4 HYBRID 300",
                            "RatRig V-Core 4 HYBRID 400",           "RatRig V-Core 4 HYBRID 500",           "RatRig V-Core 4 IDEX 300",             "RatRig V-Core 4 IDEX 300 COPY MODE",   "RatRig V-Core 4 IDEX 300 MIRROR MODE",
                            "RatRig V-Core 4 IDEX 400",             "RatRig V-Core 4 IDEX 400 COPY MODE",   "RatRig V-Core 4 IDEX 400 MIRROR MODE", "RatRig V-Core 4 IDEX 500",             "RatRig V-Core 4 IDEX 500 COPY MODE",
                            "RatRig V-Core 4 IDEX 500 MIRROR MODE"}},
     {"re3D",              {"re3D Gigabot 4",                       "re3D Gigabot 4 XLT",                   "re3D Terabot 4",
                            "re3D GigabotX 2",                      "re3D GigabotX 2 XLT",                  "re3D TerabotX 2"}},
     {"RolohaunDesign",    {"Rook MK1 LDO"}},
     {"SecKit",            {"SecKit SK-Tank", "Seckit Go3"}},
     {"Snapmaker",         {"Snapmaker J1",                 "Snapmaker A250",               "Snapmaker A350",               "Snapmaker A250 Dual",          "Snapmaker A350 Dual",
                            "Snapmaker A250 QSKit",         "Snapmaker A350 QSKit",         "Snapmaker A250 BKit",          "Snapmaker A350 BKit",          "Snapmaker A250 QS+B Kit",
                            "Snapmaker A350 QS+B Kit",      "Snapmaker A250 Dual QSKit",    "Snapmaker A350 Dual QSKit",    "Snapmaker A250 Dual BKit",     "Snapmaker A350 Dual BKit",
                            "Snapmaker A250 Dual QS+B Kit", "Snapmaker A350 Dual QS+B Kit", "Snapmaker Artisan"}},
     {"Sovol",             {"Sovol SV01 Pro",      "Sovol SV02",          "Sovol SV05",          "Sovol SV06",          "Sovol SV06 Plus",
                            "Sovol SV06 ACE",      "Sovol SV06 Plus ACE", "Sovol SV07",          "Sovol SV07 Plus",     "Sovol SV08"}},
     {"Thinker X400",      {"Thinker X400"}},
     {"Tronxy",            {"Tronxy X5SA 400 Marlin Firmware"}},
     {"TwoTrees",          {"TwoTrees SP-5 Klipper", "TwoTrees SK1"}},
     {"UltiMaker",         {"UltiMaker 2"}},
     {"Vivedino",          {"Troodon 2.0 - RRF",     "Troodon 2.0 - Klipper"}},
     {"Volumic",           {"EXO42 Performance", "EXO65 Performance", "SH65 Performance",  "EXO42",             "EXO65",           
                            "SH65",              "VS30SC2",           "VS30SC",            "VS30ULTRA",         "VS30MK3",         
                            "VS30MK2",           "VS20MK2"}},
     {"Voron",             {"Voron 2.4 250",        "Voron 2.4 300",        "Voron 2.4 350",        "Voron Trident 250",    "Voron Trident 300",
                            "Voron Trident 350",    "Voron 0.1",            "Voron Switchwire 250"}},
     {"Voxelab",           {"Voxelab Aquila X2"}},
     {"Vzbot",             {"Vzbot 235 AWD", "Vzbot 330 AWD"}},
     {"Wanhao",            {"Wanhao D12-300"}},
     {"Z-Bolt",            {"Z-Bolt S300",      "Z-Bolt S300 Dual", "Z-Bolt S400",      "Z-Bolt S400 Dual", "Z-Bolt S600",
                            "Z-Bolt S600 Dual"}}};

// nozzle_diameter_vec: the nozzles the dialog offers, in its order.
const std::vector<std::string> nozzle_diameter_vec = {"0.4", "0.15", "0.2", "0.25", "0.3", "0.35", "0.5", "0.6", "0.75", "0.8", "1.0", "1.2", "1.75"};

// my_stof(): the number a text holds, or zero.
float text_to_float(const std::string& text)
{
    try {
        return std::stof(text);
    } catch (...) {
        return 0.0f;
    }
}

PresetCreation printer_failure(const SceneStatus status, std::string message)
{
    PresetCreation result;
    result.status = status;
    result.message = std::move(message);
    return result;
}

// get_exist_vendor_choices(): the vendors whose profiles the app can read.
std::map<std::string, Slic3r::VendorProfile> installed_vendors()
{
    Slic3r::PresetBundle temp_preset_bundle;
    temp_preset_bundle.load_system_models_from_json(Slic3r::ForwardCompatibilitySubstitutionRule::EnableSystemSilent);
    std::map<std::string, Slic3r::VendorProfile> vendors;
    for (const auto& [name, vendor] : temp_preset_bundle.vendors) {
        if (vendor.models.empty() || vendor.id.empty()) {
            continue;
        }
        vendors.emplace(name, vendor);
    }
    return vendors;
}

// printer_preset_sort_with_nozzle_diameter(): the presets of a vendor, by the
// nozzle of each, as "<model> @ <nozzle> nozzle".
std::vector<std::string> vendor_printer_presets(const Slic3r::VendorProfile& vendor_profile)
{
    std::vector<std::pair<float, std::string>> preset_sort;
    for (const Slic3r::VendorProfile::PrinterModel& model : vendor_profile.models) {
        for (const Slic3r::VendorProfile::PrinterVariant& variant : model.variants) {
            const float variant_diameter = text_to_float(variant.name);
            if (variant_diameter == 0.0f) {
                continue;
            }
            preset_sort.emplace_back(variant_diameter, model.name + " @ " + variant.name + " nozzle");
        }
    }
    std::stable_sort(preset_sort.begin(), preset_sort.end(), [](const auto& a, const auto& b) { return a.first < b.first; });
    std::vector<std::string> presets;
    for (const auto& [diameter, name] : preset_sort) {
        presets.push_back(name);
    }
    return presets;
}

// The model and the nozzle "<model> @ <nozzle> nozzle" carries.
std::pair<std::string, std::string> model_and_variant(const std::string& printer_preset)
{
    const std::size_t index_at = printer_preset.find(" @ ");
    const std::size_t index_nozzle = printer_preset.find("nozzle");
    if (index_at == std::string::npos || index_nozzle == std::string::npos) {
        return {};
    }
    return {printer_preset.substr(0, index_at), printer_preset.substr(index_at + 3, index_nozzle - index_at - 4)};
}

// load_system_and_user_presets_with_curr_model(): the presets of the vendor the
// printer is made from, read into a bundle of their own.
bool load_vendor_presets(Slic3r::PresetBundle& temp_preset_bundle, const Slic3r::VendorProfile& vendor)
{
    std::string preset_path;
    if (boost::filesystem::exists(boost::filesystem::path(Slic3r::data_dir()) / PRESET_SYSTEM_DIR / vendor.id)) {
        preset_path = (boost::filesystem::path(Slic3r::data_dir()) / PRESET_SYSTEM_DIR).string();
    } else if (boost::filesystem::exists(boost::filesystem::path(Slic3r::resources_dir()) / "profiles" / vendor.id)) {
        preset_path = (boost::filesystem::path(Slic3r::resources_dir()) / "profiles").string();
    }
    if (preset_path.empty()) {
        return false;
    }
    temp_preset_bundle.load_vendor_configs_from_json(
        preset_path,
        vendor.id,
        Slic3r::PresetBundle::LoadConfigBundleAttribute::LoadSystem,
        Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilent,
        engine().bundle.get()
    );
    return true;
}

// generate_process_presets_data(): a process preset takes the nozzle it prints with.
void apply_nozzle_to_process(Slic3r::Preset& preset, const float nozzle_dia)
{
    if (auto* layer_height = preset.config.option<Slic3r::ConfigOptionFloat>("layer_height", true)) {
        layer_height->value = nozzle_dia / 2;
    }
    if (auto* initial_layer_print_height = preset.config.option<Slic3r::ConfigOptionFloat>("initial_layer_print_height", true)) {
        initial_layer_print_height->value = nozzle_dia / 2;
    }
    for (const char* key : {"line_width", "initial_layer_line_width", "outer_wall_line_width", "inner_wall_line_width",
                            "top_surface_line_width", "sparse_infill_line_width", "internal_solid_infill_line_width",
                            "support_line_width"}) {
        if (auto* width = preset.config.option<Slic3r::ConfigOptionFloat>(key, true)) {
            width->value = nozzle_dia;
        }
    }
}

}  // namespace

CreatePrinterOptions create_printer_options(
    const std::string& vendor,
    const std::string& nozzle,
    const std::string& preset_vendor,
    const std::string& printer_preset
)
{
    CreatePrinterOptions result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        result.vendors = printer_vendors;
        result.nozzle_diameters = nozzle_diameter_vec;
        const auto models = printer_model_map.find(vendor);
        if (models != printer_model_map.end()) {
            result.models = models->second;
        }

        const std::map<std::string, Slic3r::VendorProfile> vendors = installed_vendors();
        for (const auto& [name, profile] : vendors) {
            result.preset_vendors.push_back(name);
        }
        const auto chosen_vendor = vendors.find(preset_vendor);
        if (chosen_vendor == vendors.end()) {
            result.status = SceneStatus::success;
            return result;
        }
        result.printer_presets = vendor_printer_presets(chosen_vendor->second);

        // The presets that come with the chosen printer preset (update_presets_list).
        const auto [model_name, variant] = model_and_variant(printer_preset);
        if (model_name.empty()) {
            result.status = SceneStatus::success;
            return result;
        }
        Slic3r::PresetBundle temp_preset_bundle;
        if (!load_vendor_presets(temp_preset_bundle, chosen_vendor->second)) {
            result.status = SceneStatus::profile_not_found;
            result.message = "Preset path was not found, please reselect vendor.";
            return result;
        }
        std::string model_id;
        for (const Slic3r::VendorProfile::PrinterModel& model : chosen_vendor->second.models) {
            if (model.name == model_name) {
                model_id = model.id;
                break;
            }
        }
        const Slic3r::Preset* temp_printer_preset = temp_preset_bundle.printers.find_system_preset_by_model_and_variant(model_id, variant);
        if (temp_printer_preset == nullptr) {
            result.status = SceneStatus::profile_not_found;
            result.message = "The printer preset was not found, please reselect.";
            return result;
        }
        temp_preset_bundle.printers.select_preset_by_name(temp_printer_preset->name, true);
        temp_preset_bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always);
        for (const Slic3r::Preset& filament_preset : temp_preset_bundle.filaments.get_presets()) {
            if (filament_preset.is_compatible && !filament_preset.is_default) {
                result.filament_presets.push_back(filament_preset.name);
            }
        }
        for (const Slic3r::Preset& process_preset : temp_preset_bundle.prints.get_presets()) {
            if (process_preset.is_compatible && !process_preset.is_default) {
                result.process_presets.push_back(process_preset.name);
            }
        }
        // The printable area the printer preset brings, which page 1 opens with.
        if (const auto* printable_area = temp_printer_preset->config.opt<Slic3r::ConfigOptionPoints>("printable_area")) {
            for (const Slic3r::Vec2d& point : printable_area->values) {
                result.printable_area.push_back(point.x());
                result.printable_area.push_back(point.y());
            }
        }
        if (const auto* printable_height = temp_printer_preset->config.opt<Slic3r::ConfigOptionFloat>("printable_height")) {
            result.max_print_height = printable_height->value;
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

PresetCreation create_printer(const CreatePrinterRequest& request, const DialogAnswers& answers)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return printer_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        detail::SettingsDialogs dialogs(answers);
        const auto ui_text = [](std::string msgid, std::vector<std::string> args = {}) { return detail::ui_text(std::move(msgid), std::move(args)); };

        if (request.printer_preset.empty()) {
            return printer_failure(SceneStatus::profile_not_found,
                                   "You have not yet chosen which printer preset to create based on. Please choose the vendor and model of the printer");
        }
        if (request.model.empty()) {
            return printer_failure(SceneStatus::profile_not_found, "The printer model was not found, please reselect.");
        }
        // save_printable_area_config(): the area page 1 was filled in with.
        if (request.printable_area.size() < 6 || request.printable_area.size() % 2 != 0 || request.max_print_height <= 0) {
            return printer_failure(SceneStatus::profile_not_found,
                                   "You have entered an illegal input in the printable area section on the first page. Please check before creating it.");
        }

        // create preset name
        std::string printer_nozzle_name = request.nozzle;
        const std::size_t comma_pos = printer_nozzle_name.find(',');
        if (comma_pos != std::string::npos) {
            printer_nozzle_name.replace(comma_pos, 1, ".");
        }
        const std::string printer_preset_name = request.model + " " + printer_nozzle_name + " nozzle";

        // Confirm if the printer preset has a duplicate name
        if (bundle.printers.find_preset(printer_preset_name) != nullptr) {
            dialogs.ask(
                "printer_name_exists",
                {ui_text("The printer preset you created already has a preset with the same name. Do you want to overwrite it?\n\tYes: Overwrite the "
                         "printer preset with the same name, and filament and process presets with the same preset name will be recreated \nand filament "
                         "and process presets without the same preset name will be reserve.\n\tCancel: Do not create a preset, return to the creation "
                         "interface.")},
                // wxYES | wxCANCEL: the other answer returns to the dialog.
                {}, {}, ui_text("Cancel")
            );
        }
        if (request.filament_presets.empty()) {
            return printer_failure(SceneStatus::profile_not_found, "You need to select at least one filament preset.");
        }
        if (request.process_presets.empty()) {
            return printer_failure(SceneStatus::profile_not_found, "You need to select at least one process preset.");
        }

        // The vendor's presets, which the chosen ones are cloned from.
        const std::map<std::string, Slic3r::VendorProfile> vendors = installed_vendors();
        const auto chosen_vendor = vendors.find(request.preset_vendor);
        if (chosen_vendor == vendors.end()) {
            return printer_failure(SceneStatus::profile_not_found, "Vendor was not found, please reselect.");
        }
        Slic3r::PresetBundle temp_preset_bundle;
        if (!load_vendor_presets(temp_preset_bundle, chosen_vendor->second)) {
            return printer_failure(SceneStatus::profile_not_found, "Preset path was not found, please reselect vendor.");
        }
        const auto [model_name, variant] = model_and_variant(request.printer_preset);
        std::string model_id;
        for (const Slic3r::VendorProfile::PrinterModel& model : chosen_vendor->second.models) {
            if (model.name == model_name) {
                model_id = model.id;
                break;
            }
        }
        const Slic3r::Preset* source_preset = temp_preset_bundle.printers.find_system_preset_by_model_and_variant(model_id, variant);
        if (source_preset == nullptr) {
            return printer_failure(SceneStatus::profile_not_found, "The printer preset was not found, please reselect.");
        }
        Slic3r::Preset printer_preset = *source_preset;
        temp_preset_bundle.printers.select_preset_by_name(source_preset->name, true);
        temp_preset_bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always);

        // The presets the check boxes of page 2 chose.
        std::vector<Slic3r::Preset> filament_copies;
        std::vector<Slic3r::Preset> process_copies;
        for (const Slic3r::Preset& preset : temp_preset_bundle.filaments.get_presets()) {
            if (std::find(request.filament_presets.begin(), request.filament_presets.end(), preset.name) != request.filament_presets.end()) {
                filament_copies.push_back(preset);
            }
        }
        const float nozzle_dia = text_to_float(printer_nozzle_name);
        for (const Slic3r::Preset& preset : temp_preset_bundle.prints.get_presets()) {
            if (std::find(request.process_presets.begin(), request.process_presets.end(), preset.name) != request.process_presets.end()) {
                process_copies.push_back(preset);
                apply_nozzle_to_process(process_copies.back(), nozzle_dia);
            }
        }

        std::vector<const Slic3r::Preset*> selected_filament_presets;
        for (const Slic3r::Preset& preset : filament_copies) {
            selected_filament_presets.push_back(&preset);
        }
        std::vector<const Slic3r::Preset*> selected_process_presets;
        for (const Slic3r::Preset& preset : process_copies) {
            selected_process_presets.push_back(&preset);
        }

        // clone filament preset, then process preset, as the dialog does.
        std::vector<std::string> failures;
        const auto filament_id = [&bundle](const std::string& name) { return detail::filament_id_for(bundle, name); };
        if (!bundle.filaments.clone_presets_for_printer(selected_filament_presets, failures, printer_preset_name, filament_id, false)) {
            std::string message;
            for (const std::string& failure : failures) {
                message += "\t" + failure + "\n";
            }
            dialogs.ask("rewrite_filament_presets",
                        {ui_text("Create filament presets failed. As follows:\n"), ui_text("%1%", {message}), ui_text("\nDo you want to rewrite it?")});
            bundle.filaments.clone_presets_for_printer(selected_filament_presets, failures, printer_preset_name, filament_id, true);
        }
        failures.clear();
        if (!bundle.prints.clone_presets_for_printer(selected_process_presets, failures, printer_preset_name, filament_id, false)) {
            std::string message;
            for (const std::string& failure : failures) {
                message += "\t" + failure + "\n";
            }
            dialogs.ask("rewrite_process_presets",
                        {ui_text("Create process presets failed. As follows:\n"), ui_text("%1%", {message}), ui_text("\nDo you want to rewrite it?")});
            bundle.prints.clone_presets_for_printer(selected_process_presets, failures, printer_preset_name, filament_id, true);
        }

        // The printable area and the height of page 1 go into the printer preset.
        std::vector<Slic3r::Vec2d> points;
        for (std::size_t index = 0; index + 1 < request.printable_area.size(); index += 2) {
            points.emplace_back(request.printable_area[index], request.printable_area[index + 1]);
        }
        printer_preset.config.set_key_value("printable_area", new Slic3r::ConfigOptionPoints(points));
        printer_preset.config.set("printable_height", request.max_print_height);
        printer_preset.config.set("bed_custom_model", request.custom_model);
        printer_preset.config.set("bed_custom_texture", request.custom_texture);

        // clone printer preset
        if (auto* printer_model = printer_preset.config.option<Slic3r::ConfigOptionString>("printer_model", true)) {
            printer_model->value = request.model;
        }
        if (auto* printer_variant = printer_preset.config.option<Slic3r::ConfigOptionString>("printer_variant", true)) {
            printer_variant->value = printer_nozzle_name;
        }
        if (auto* nozzle_diameter = printer_preset.config.option<Slic3r::ConfigOptionFloats>("nozzle_diameter", true)) {
            std::fill(nozzle_diameter->values.begin(), nozzle_diameter->values.end(), double(nozzle_dia));
        }
        bundle.printers.save_current_preset(printer_preset_name, true, false, &printer_preset);
        bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always);
        bundle.export_selections(*engine().config);
        save_config(engine());

        PresetCreation result;
        result.status = SceneStatus::success;
        result.name = printer_preset_name;
        return result;
    } catch (const detail::QuestionPending& pending) {
        PresetCreation result;
        result.status = SceneStatus::success;
        result.has_question = true;
        result.question = pending.dialog;
        return result;
    } catch (const std::exception& error) {
        return printer_failure(SceneStatus::profile_not_found, error.what());
    }
}

}  // namespace orcinus::orca
