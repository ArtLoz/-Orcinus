#include "orca_engine_adapter.hpp"

// OrcaSlicer's CreatePrinterPresetDialog (CreatePresetsDialog.cpp): a printer
// of the user's own, or a nozzle for a printer that is installed. Its first
// page names the printer, its nozzle and its printable area, its second page
// the presets it is made from; create_printer_options() answers with what the
// pages offer, check_printer_page() is the first page's OK, and
// create_printer() the second page's Create button.

#include <algorithm>
#include <cmath>
#include <map>
#include <set>
#include <string>
#include <vector>

#include <boost/algorithm/string/trim.hpp>
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
const std::map<std::string, float> nozzle_diameter_map = {{"0.15", 0.15f}, {"0.2", 0.2f}, {"0.25", 0.25f}, {"0.3", 0.3f},
                                                          {"0.35", 0.35f}, {"0.4", 0.4f}, {"0.5", 0.5f},   {"0.6", 0.6f},
                                                          {"0.75", 0.75f}, {"0.8", 0.8f}, {"1.0", 1.0f},   {"1.2", 1.2f},
                                                          {"1.75", 1.75f}};

// remove_special_key(): what a typed vendor or model keeps.
std::string remove_special_key(const std::string& str)
{
    static const std::set<char> special_key = {'\n', '\t', '\r', '\v', '@', ';'};
    std::string res_str;
    for (const char c : str) {
        if (special_key.find(c) == special_key.end()) {
            res_str.push_back(c);
        }
    }
    return res_str;
}

std::string trimmed(std::string text)
{
    boost::algorithm::trim(text);
    return text;
}

// my_stof(): the number a text holds, with either decimal separator, or zero.
float text_to_float(std::string text)
{
    const std::size_t alt_pos = text.find(',');
    if (alt_pos != std::string::npos) {
        text.replace(alt_pos, 1, 1, '.');
    }
    if (text == ".") {
        return 0.0f;
    }
    try {
        return static_cast<float>(std::stod(text));
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

// get_printer_vendor() and get_printer_model(): as chosen, or as typed.
std::string printer_vendor(const CreatePrinterRequest& request)
{
    return request.custom_printer ? trimmed(remove_special_key(request.vendor)) : request.vendor;
}

std::string printer_model(const CreatePrinterRequest& request)
{
    return request.custom_printer ? trimmed(remove_special_key(request.model)) : request.model;
}

// get_nozzle_diameter(): the nozzle as chosen or typed, "0.4" for no number.
std::string nozzle_diameter(const CreatePrinterRequest& request)
{
    std::string diameter = request.custom_nozzle ? request.custom_nozzle_diameter : request.nozzle;
    if (text_to_float(diameter) == 0.0f) {
        diameter = "0.4";
    }
    return diameter;
}

// set_current_visible_printer(): the installed printers a nozzle is made for —
// the visible ones that are their own base — once for every printer_model.
std::vector<std::pair<std::string, const Slic3r::Preset*>> visible_printers(const Slic3r::PresetBundle& bundle)
{
    std::vector<std::pair<std::string, const Slic3r::Preset*>> printers;
    for (const Slic3r::Preset& printer_preset : bundle.printers.get_presets()) {
        if (!printer_preset.is_visible) {
            continue;
        }
        if (bundle.printers.get_preset_base(printer_preset)->name != printer_preset.name) {
            continue;
        }
        if (const auto* printer_model = printer_preset.config.opt<Slic3r::ConfigOptionString>("printer_model")) {
            const bool listed = std::any_of(printers.begin(), printers.end(), [&](const auto& printer) { return printer.first == printer_model->value; });
            if (!listed) {
                printers.emplace_back(printer_model->value, &printer_preset);
            }
        }
    }
    return printers;
}

const Slic3r::Preset* visible_printer(const std::vector<std::pair<std::string, const Slic3r::Preset*>>& printers, const std::string& model)
{
    const auto found = std::find_if(printers.begin(), printers.end(), [&](const auto& printer) { return printer.first == model; });
    return found == printers.end() ? nullptr : found->second;
}

// get_nozzle_size_for_printer_model(): how many nozzles the installed printer
// of a printer_model has, one for a model that is not installed. Upstream
// knows the installed printers once "Create Nozzle for Existing Printer" has
// been chosen; before, every model has one nozzle.
std::size_t nozzle_count(
    const std::vector<std::pair<std::string, const Slic3r::Preset*>>& printers,
    const CreatePrinterRequest& request,
    const std::string& model
)
{
    if (!request.create_nozzle && request.existing_printer.empty()) {
        return 1;
    }
    if (const Slic3r::Preset* printer_preset = visible_printer(printers, model)) {
        if (const auto* nozzles = printer_preset->config.opt<Slic3r::ConfigOptionFloats>("nozzle_diameter")) {
            return nozzles->values.size();
        }
    }
    return 1;
}

// get_custom_printer_model(): the model of the printer to create.
std::string custom_printer_model(const Slic3r::PresetBundle& bundle, const CreatePrinterRequest& request)
{
    if (!request.create_nozzle) {
        return printer_vendor(request) + " " + printer_model(request);
    }
    const Slic3r::Preset* printer_preset = visible_printer(visible_printers(bundle), request.existing_printer);
    if (printer_preset == nullptr) {
        return {};
    }
    const auto* printer_model = printer_preset->config.opt<Slic3r::ConfigOptionString>("printer_model");
    return printer_model == nullptr ? std::string() : printer_model->value;
}

// get_exist_vendor_choices(): the vendors whose profiles the app can read, with
// "Custom" for the printers of the user's own.
std::map<std::string, Slic3r::VendorProfile> installed_vendors(const Slic3r::PresetBundle& bundle)
{
    Slic3r::PresetBundle temp_preset_bundle;
    temp_preset_bundle.load_system_models_from_json(Slic3r::ForwardCompatibilitySubstitutionRule::EnableSystemSilent);
    std::map<std::string, Slic3r::VendorProfile> vendors(temp_preset_bundle.vendors.begin(), temp_preset_bundle.vendors.end());
    Slic3r::VendorProfile users_models = bundle.get_custom_vendor_models();
    if (!users_models.models.empty()) {
        vendors[users_models.name] = users_models;
    }
    for (auto vendor = vendors.begin(); vendor != vendors.end();) {
        if (vendor->second.models.empty() || vendor->second.id.empty()) {
            vendor = vendors.erase(vendor);
        } else {
            ++vendor;
        }
    }
    return vendors;
}

// printer_preset_sort_with_nozzle_diameter(): the printer presets of a vendor,
// as "<model> @ <nozzle> nozzle", for models of as many nozzles as the chosen
// installed printer, the nearest [nozzle_diameter] first.
std::vector<std::string> vendor_printer_presets(
    const Slic3r::VendorProfile& vendor_profile,
    const float nozzle_diameter,
    const std::vector<std::pair<std::string, const Slic3r::Preset*>>& printers,
    const CreatePrinterRequest& request
)
{
    std::vector<std::pair<float, std::string>> preset_sort;
    const std::size_t selected_nozzle_size = nozzle_count(printers, request, request.existing_printer);
    for (const Slic3r::VendorProfile::PrinterModel& model : vendor_profile.models) {
        if (nozzle_count(printers, request, model.name) != selected_nozzle_size) {
            continue;
        }
        for (const Slic3r::VendorProfile::PrinterVariant& variant : model.variants) {
            preset_sort.emplace_back(text_to_float(variant.name), model.name + " @ " + variant.name + " nozzle");
        }
    }
    std::stable_sort(preset_sort.begin(), preset_sort.end(), [](const auto& a, const auto& b) { return a.first < b.first; });

    int index_nearest_nozzle = -1;
    float nozzle_diameter_diff = 1;
    for (int i = 0; i < static_cast<int>(preset_sort.size()); ++i) {
        const float curr_nozzle_diameter_diff = std::abs(nozzle_diameter - preset_sort[i].first);
        if (curr_nozzle_diameter_diff < nozzle_diameter_diff) {
            index_nearest_nozzle = i;
            nozzle_diameter_diff = curr_nozzle_diameter_diff;
            if (curr_nozzle_diameter_diff == 0) {
                break;
            }
        }
    }
    std::vector<std::string> printer_preset_model_selection;
    int right_index = index_nearest_nozzle + 1;
    const int size = static_cast<int>(preset_sort.size());
    while (index_nearest_nozzle >= 0 || right_index < size) {
        if (index_nearest_nozzle >= 0 && right_index < size) {
            const float left_nozzle_diff = std::abs(nozzle_diameter - preset_sort[index_nearest_nozzle].first);
            const float right_nozzle_diff = std::abs(nozzle_diameter - preset_sort[right_index].first);
            if (left_nozzle_diff < right_nozzle_diff) {
                printer_preset_model_selection.push_back(preset_sort[index_nearest_nozzle--].second);
            } else {
                printer_preset_model_selection.push_back(preset_sort[right_index++].second);
            }
        } else if (index_nearest_nozzle >= 0) {
            printer_preset_model_selection.push_back(preset_sort[index_nearest_nozzle--].second);
        } else {
            printer_preset_model_selection.push_back(preset_sort[right_index++].second);
        }
    }
    return printer_preset_model_selection;
}

// load_system_and_user_presets_with_curr_model(): the printer preset chosen on
// the second page, and a bundle of the presets it can be made with — the
// vendor's and the templates for "Create from Template", the vendor's and the
// user's that suit it for "Create Based on Current Printer". The dialog's
// message when they cannot be read.
std::string load_presets_with_model(
    Slic3r::PresetBundle& temp_preset_bundle,
    const Slic3r::VendorProfile& vendor_selected,
    const std::string& curr_selected_model,
    const bool just_template,
    Slic3r::Preset& printer_preset
)
{
    const std::size_t nozzle_index = curr_selected_model.find_first_of('@');
    const std::string select_model = nozzle_index == std::string::npos ? curr_selected_model : curr_selected_model.substr(0, nozzle_index - 1);
    const Slic3r::VendorProfile::PrinterModel* model_selected = nullptr;
    for (const Slic3r::VendorProfile::PrinterModel& model : vendor_selected.models) {
        if (model.name == select_model) {
            model_selected = &model;
            break;
        }
    }
    if (vendor_selected.id.empty() || model_selected == nullptr || model_selected->id.empty()) {
        return "Preset path was not found, please reselect vendor.";
    }

    const bool is_custom_vendor = PRESET_CUSTOM_VENDOR == vendor_selected.name || PRESET_CUSTOM_VENDOR == vendor_selected.id;
    if (is_custom_vendor) {
        temp_preset_bundle = *engine().bundle;
    } else {
        std::string preset_path;
        if (boost::filesystem::exists(boost::filesystem::path(Slic3r::data_dir()) / PRESET_SYSTEM_DIR / vendor_selected.id)) {
            preset_path = (boost::filesystem::path(Slic3r::data_dir()) / PRESET_SYSTEM_DIR).string();
        } else if (boost::filesystem::exists(boost::filesystem::path(Slic3r::resources_dir()) / "profiles" / vendor_selected.id)) {
            preset_path = (boost::filesystem::path(Slic3r::resources_dir()) / "profiles").string();
        }
        if (preset_path.empty()) {
            return "Preset path was not found, please reselect vendor.";
        }
        try {
            temp_preset_bundle.load_vendor_configs_from_json(
                preset_path,
                vendor_selected.id,
                Slic3r::PresetBundle::LoadConfigBundleAttribute::LoadSystem,
                Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilent,
                engine().bundle.get()
            );
        } catch (...) {
            return "The printer model was not found, please reselect.";
        }
        if (!just_template) {
            const std::string dir_user_presets = engine().config->get("preset_folder");
            temp_preset_bundle.load_user_presets(dir_user_presets.empty() ? std::string(DEFAULT_USER_FOLDER_NAME) : dir_user_presets,
                                                 Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilent);
        }
    }

    const std::size_t index_at = curr_selected_model.find(" @ ");
    const std::size_t index_nozzle = curr_selected_model.find("nozzle");
    if (index_at == std::string::npos || index_nozzle == std::string::npos) {
        return "The nozzle diameter was not found, please reselect.";
    }
    const std::string varient = curr_selected_model.substr(index_at + 3, index_nozzle - index_at - 4);
    const Slic3r::Preset* temp_printer_preset =
        is_custom_vendor ? temp_preset_bundle.printers.find_custom_preset_by_model_and_variant(model_selected->id, varient)
                         : temp_preset_bundle.printers.find_system_preset_by_model_and_variant(model_selected->id, varient);
    if (temp_printer_preset == nullptr) {
        return "The printer preset was not found, please reselect.";
    }
    printer_preset = *temp_printer_preset;

    if (!just_template) {
        temp_preset_bundle.printers.select_preset_by_name(printer_preset.name, true);
        temp_preset_bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always);
    } else {
        const boost::filesystem::path templates = boost::filesystem::path(Slic3r::resources_dir()) / PRESET_PROFILES_TEMOLATE_DIR;
        if (!boost::filesystem::exists(templates / PRESET_TEMPLATE_DIR)) {
            return "Preset path was not found, please reselect vendor.";
        }
        try {
            temp_preset_bundle.load_vendor_configs_from_json(
                templates.string(),
                PRESET_TEMPLATE_DIR,
                Slic3r::PresetBundle::LoadConfigBundleAttribute::LoadSystem,
                Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilent
            );
        } catch (...) {
            return "The printer model was not found, please reselect.";
        }
    }
    return {};
}

// update_presets_list(): the presets the second page lists, which suit the
// chosen printer preset.
std::vector<const Slic3r::Preset*> listed_presets(const Slic3r::PresetCollection& collection)
{
    std::vector<const Slic3r::Preset*> presets;
    for (const Slic3r::Preset& preset : collection.get_presets()) {
        if (preset.is_compatible && !preset.is_default) {
            presets.push_back(&preset);
        }
    }
    return presets;
}

// generate_process_presets_data(): a process template takes the nozzle it prints with.
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
    if (auto* wall_loops = preset.config.option<Slic3r::ConfigOptionInt>("wall_loops", true)) {
        wall_loops->value = std::max(2, static_cast<int>(std::ceil(2 * 0.4 / nozzle_dia)));
    }
    if (auto* top_shell_layers = preset.config.option<Slic3r::ConfigOptionInt>("top_shell_layers", true)) {
        top_shell_layers->value = std::max(5, static_cast<int>(std::ceil(5 * 0.4 / nozzle_dia)));
    }
    if (auto* bottom_shell_layers = preset.config.option<Slic3r::ConfigOptionInt>("bottom_shell_layers", true)) {
        bottom_shell_layers->value = std::max(3, static_cast<int>(std::ceil(3 * 0.4 / nozzle_dia)));
    }
}

// check_printable_area()
bool printable_area_valid(const CreatePrinterRequest& request)
{
    if (request.size_x == 0 || request.size_y == 0) {
        return false;
    }
    return !(request.origin_x >= request.size_x || request.origin_y >= request.size_y);
}

// save_printable_area_config(): the printable area the printer gets, from the
// first page, or from the installed printer a nozzle is made for.
bool save_printable_area_config(const Slic3r::PresetBundle& bundle, const CreatePrinterRequest& request, Slic3r::DynamicPrintConfig& config)
{
    if (!request.create_nozzle) {
        if (!printable_area_valid(request)) {
            return false;
        }
        const double x0 = -request.origin_x;
        const double y0 = -request.origin_y;
        const double x1 = request.size_x - request.origin_x;
        const double y1 = request.size_y - request.origin_y;
        const std::vector<Slic3r::Vec2d> points = {Slic3r::Vec2d(x0, y0), Slic3r::Vec2d(x1, y0), Slic3r::Vec2d(x1, y1), Slic3r::Vec2d(x0, y1)};
        config.set_key_value("printable_area", new Slic3r::ConfigOptionPoints(points));
        config.set("printable_height", request.max_print_height);
        // Utils::slash_to_back_slash()
        std::string custom_texture = request.custom_texture;
        std::string custom_model = request.custom_model;
        std::replace(custom_texture.begin(), custom_texture.end(), '\\', '/');
        std::replace(custom_model.begin(), custom_model.end(), '\\', '/');
        config.set("bed_custom_model", custom_model);
        config.set("bed_custom_texture", custom_texture);
    } else if (const Slic3r::Preset* printer_preset = visible_printer(visible_printers(bundle), request.existing_printer)) {
        config.apply_only(printer_preset->config, {"printable_area", "printable_height", "bed_custom_model", "bed_custom_texture"}, true);
    }
    return true;
}

}  // namespace

CreatePrinterOptions create_printer_options(const CreatePrinterRequest& request)
{
    CreatePrinterOptions result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        const Slic3r::PresetBundle& bundle = *engine().bundle;
        result.vendors = printer_vendors;
        result.nozzle_diameters = nozzle_diameter_vec;
        const auto models = printer_model_map.find(request.vendor);
        if (models != printer_model_map.end()) {
            result.models = models->second;
        }
        const std::vector<std::pair<std::string, const Slic3r::Preset*>> printers = visible_printers(bundle);
        for (const auto& [model, printer_preset] : printers) {
            result.existing_printers.push_back(model);
        }
        result.template_allowed = nozzle_count(printers, request, request.existing_printer) <= 1;

        const std::map<std::string, Slic3r::VendorProfile> vendors = installed_vendors(bundle);
        for (const auto& [name, profile] : vendors) {
            result.preset_vendors.push_back(name);
        }
        result.status = SceneStatus::success;
        const auto chosen_vendor = vendors.find(request.preset_vendor);
        if (chosen_vendor == vendors.end()) {
            return result;
        }
        // on_select_printer_model(): the vendor's printer presets, the nearest
        // the nozzle chosen from the list first.
        const auto nozzle = nozzle_diameter_map.find(request.nozzle);
        result.printer_presets =
            vendor_printer_presets(chosen_vendor->second, nozzle == nozzle_diameter_map.end() ? 0.0f : nozzle->second, printers, request);
        if (result.printer_presets.empty()) {
            result.message = "Current vendor has no models, please reselect.";
            return result;
        }
        if (request.printer_preset.empty()) {
            return result;
        }

        Slic3r::PresetBundle temp_preset_bundle;
        Slic3r::Preset printer_preset(Slic3r::Preset::TYPE_PRINTER, {});
        const bool just_template = request.from_template && result.template_allowed;
        result.message = load_presets_with_model(temp_preset_bundle, chosen_vendor->second, request.printer_preset, just_template, printer_preset);
        if (!result.message.empty()) {
            return result;
        }
        for (const Slic3r::Preset* preset : listed_presets(temp_preset_bundle.filaments)) {
            result.filament_presets.push_back(preset->name);
        }
        for (const Slic3r::Preset* preset : listed_presets(temp_preset_bundle.prints)) {
            result.process_presets.push_back(preset->name);
        }
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

PresetCreation check_printer_page(const CreatePrinterRequest& request, const DialogAnswers& answers)
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

        if (!request.create_nozzle) {
            std::string vendor_name = printer_vendor(request);
            std::string model_name = printer_model(request);
            if (vendor_name.empty() || model_name.empty()) {
                return printer_failure(SceneStatus::profile_not_found,
                                       "You have not selected the vendor and model or entered the custom vendor and model.");
            }
            vendor_name = remove_special_key(vendor_name);
            model_name = remove_special_key(model_name);
            if (vendor_name.empty() || model_name.empty()) {
                return printer_failure(SceneStatus::profile_not_found,
                                       "There may be escape characters in the custom printer vendor or model. Please delete and re-enter.");
            }
            if (trimmed(vendor_name).empty() || trimmed(model_name).empty()) {
                return printer_failure(SceneStatus::profile_not_found, "All inputs in the custom printer vendor or model are spaces. Please re-enter.");
            }
            if (!printable_area_valid(request)) {
                return printer_failure(SceneStatus::profile_not_found, "Please check bed printable shape and origin input.");
            }
        } else if (request.existing_printer.empty()) {
            return printer_failure(SceneStatus::profile_not_found, "You have not yet selected the printer to replace the nozzle, please choose.");
        }

        if (text_to_float(request.custom_nozzle ? request.custom_nozzle_diameter : request.nozzle) == 0.0f) {
            return printer_failure(SceneStatus::profile_not_found, "The entered nozzle diameter is invalid, please re-enter:\n");
        }

        // get_custom_printer_name(): a system preset of that name is offered
        // to switch to instead.
        const std::string custom_printer_name = custom_printer_model(bundle, request) + " " + nozzle_diameter(request) + " nozzle";
        const Slic3r::Preset* preset = bundle.printers.find_preset(custom_printer_name);
        if (preset != nullptr && preset->is_system) {
            std::string diameters;
            const std::string printer_model = preset->config.opt_string("printer_model");
            for (const Slic3r::Preset& printer : bundle.printers) {
                if (printer.config.opt_string("printer_model") == printer_model) {
                    diameters += printer.config.opt_string("printer_variant") + "  ";
                }
            }
            const std::string name = preset->name;
            const bool switch_preset = dialogs.ask(
                "printer_system_preset",
                {ui_text("The system preset does not allow creation. \nPlease re-enter the printer model or nozzle diameter."),
                 ui_text("\n\nAvailable nozzle profiles for this printer:"), ui_text("%1%", {"\n" + diameters}),
                 ui_text("\n\nChoose YES to switch existing preset:"), ui_text("%1%", {"\n" + name})}
            );
            if (!switch_preset) {
                return printer_failure(SceneStatus::profile_not_found, {});
            }
            bundle.printers.select_preset_by_name(name, true);
            bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always);
            bundle.export_selections(*engine().config);
            save_config(engine());
            PresetCreation result;
            result.status = SceneStatus::success;
            result.name = name;
            return result;
        }

        PresetCreation result;
        result.status = SceneStatus::success;
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

        // Confirm if the printer preset exists: the one the second page chose
        // (update_presets_list()).
        const std::map<std::string, Slic3r::VendorProfile> vendors = installed_vendors(bundle);
        const auto chosen_vendor = vendors.find(request.preset_vendor);
        Slic3r::PresetBundle temp_preset_bundle;
        Slic3r::Preset printer_preset(Slic3r::Preset::TYPE_PRINTER, {});
        const bool just_template = request.from_template && nozzle_count(visible_printers(bundle), request, request.existing_printer) <= 1;
        if (chosen_vendor == vendors.end() || request.printer_preset.empty() ||
            !load_presets_with_model(temp_preset_bundle, chosen_vendor->second, request.printer_preset, just_template, printer_preset).empty()) {
            return printer_failure(SceneStatus::profile_not_found,
                                   "You have not yet chosen which printer preset to create based on. Please choose the vendor and model of the printer");
        }

        if (!save_printable_area_config(bundle, request, printer_preset.config)) {
            return printer_failure(SceneStatus::profile_not_found,
                                   "You have entered an illegal input in the printable area section on the first page. Please check before creating it.");
        }

        // create preset name
        const std::string printer_model_name = custom_printer_model(bundle, request);
        std::string printer_nozzle_name = nozzle_diameter(request);
        const std::size_t comma_pos = printer_nozzle_name.find(',');
        if (comma_pos != std::string::npos) {
            printer_nozzle_name.replace(comma_pos, 1, ".");
        }
        const std::string printer_preset_name = printer_model_name + " " + printer_nozzle_name + " nozzle";

        // Confirm if the printer preset has a duplicate name
        bool rewritten = false;
        if (bundle.printers.find_preset(printer_preset_name) != nullptr) {
            // wxYES | wxCANCEL: the other answer returns to the dialog.
            rewritten = dialogs.ask(
                "printer_name_exists",
                {ui_text("The printer preset you created already has a preset with the same name. Do you want to overwrite it?\n\tYes: Overwrite the "
                         "printer preset with the same name, and filament and process presets with the same preset name will be recreated \nand filament "
                         "and process presets without the same preset name will be reserve.\n\tCancel: Do not create a preset, return to the creation "
                         "interface.")},
                {}, {}, ui_text("Cancel")
            );
        }

        // Confirm if the filament preset is exist: presets of the printer's
        // name are there already when none is checked.
        const auto checked = [](const std::vector<std::string>& names, const Slic3r::Preset* preset) {
            return std::find(names.begin(), names.end(), preset->name) != names.end();
        };
        bool filament_preset_is_exist = false;
        std::vector<Slic3r::Preset> filament_copies;
        for (const Slic3r::Preset* preset : listed_presets(temp_preset_bundle.filaments)) {
            if (checked(request.filament_presets, preset)) {
                filament_copies.push_back(*preset);
            }
            if (!filament_preset_is_exist && bundle.filaments.find_preset(preset->alias + " @ " + printer_preset_name) != nullptr) {
                filament_preset_is_exist = true;
            }
        }
        if (filament_copies.empty() && !filament_preset_is_exist) {
            return printer_failure(SceneStatus::profile_not_found, "You need to select at least one filament preset.");
        }
        // Upstream looks for the processes without the space after "@".
        bool process_preset_is_exist = false;
        std::vector<Slic3r::Preset> process_copies;
        for (const Slic3r::Preset* preset : listed_presets(temp_preset_bundle.prints)) {
            if (checked(request.process_presets, preset)) {
                process_copies.push_back(*preset);
            }
            if (!process_preset_is_exist && bundle.prints.find_preset(preset->alias + " @" + printer_preset_name) != nullptr) {
                process_preset_is_exist = true;
            }
        }
        if (process_copies.empty() && !process_preset_is_exist) {
            return printer_failure(SceneStatus::profile_not_found, "You need to select at least one process preset.");
        }

        // Presets made from the templates print with the printer's nozzle.
        if (just_template) {
            const float nozzle_dia = text_to_float(nozzle_diameter(request));
            for (Slic3r::Preset& preset : process_copies) {
                apply_nozzle_to_process(preset, nozzle_dia);
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
        if (!selected_filament_presets.empty() &&
            !bundle.filaments.clone_presets_for_printer(selected_filament_presets, failures, printer_preset_name, filament_id, rewritten)) {
            std::string message;
            for (const std::string& failure : failures) {
                message += "\t" + failure + "\n";
            }
            dialogs.ask("rewrite_filament_presets",
                        {ui_text("Create filament presets failed. As follows:\n"), ui_text("%1%", {message}), ui_text("\nDo you want to rewrite it?")});
            bundle.filaments.clone_presets_for_printer(selected_filament_presets, failures, printer_preset_name, filament_id, true);
        }
        failures.clear();
        if (!selected_process_presets.empty() &&
            !bundle.prints.clone_presets_for_printer(selected_process_presets, failures, printer_preset_name, filament_id, rewritten)) {
            std::string message;
            for (const std::string& failure : failures) {
                message += "\t" + failure + "\n";
            }
            dialogs.ask("rewrite_process_presets",
                        {ui_text("Create process presets failed. As follows:\n"), ui_text("%1%", {message}), ui_text("\nDo you want to rewrite it?")});
            bundle.prints.clone_presets_for_printer(selected_process_presets, failures, printer_preset_name, filament_id, true);
        }

        // clone printer preset
        if (auto* printer_model = printer_preset.config.option<Slic3r::ConfigOptionString>("printer_model", true)) {
            printer_model->value = printer_model_name;
        }
        if (auto* printer_variant = printer_preset.config.option<Slic3r::ConfigOptionString>("printer_variant", true)) {
            printer_variant->value = printer_nozzle_name;
        }
        if (auto* nozzle_diameters = printer_preset.config.option<Slic3r::ConfigOptionFloats>("nozzle_diameter", true)) {
            const auto known = nozzle_diameter_map.find(printer_nozzle_name);
            const float diameter = known != nozzle_diameter_map.end() ? known->second : text_to_float(nozzle_diameter(request));
            std::fill(nozzle_diameters->values.begin(), nozzle_diameters->values.end(), double(diameter));
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
