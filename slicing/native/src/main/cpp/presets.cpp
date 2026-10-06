#include "orca_engine_adapter.hpp"

// The presets of OrcaSlicer's sidebar and its Setup Wizard.

#include <algorithm>
#include <cctype>
#include <functional>
#include <iomanip>
#include <map>
#include <set>
#include <sstream>
#include <unordered_set>
#include <boost/algorithm/string/predicate.hpp>

#include "engine_context.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Utils.hpp"
#include "settings_tab.hpp"
#include "setup_catalog.hpp"

namespace orcinus::orca {

using detail::engine;
using detail::follow_config;
using detail::save_config;

namespace {

using VendorMap = Slic3r::AppConfig::VendorMap;

// The Setup Wizard's printers and filaments, read once per process.
std::unique_ptr<setup::Catalog> catalog;

const setup::Catalog& setup_catalog()
{
    if (catalog == nullptr) {
        catalog = std::make_unique<setup::Catalog>(setup::load(Slic3r::data_dir(), Slic3r::resources_dir()));
    }
    return *catalog;
}

const setup::Model* find_setup_model(const setup::Catalog& data, const std::string& model_id)
{
    const auto model = std::find_if(data.models.begin(), data.models.end(), [&model_id](const setup::Model& candidate) {
        return candidate.model == model_id;
    });
    return model == data.models.end() ? nullptr : &*model;
}

// get_diameter_string() of the sidebar (Plater.cpp): "0.4", "0.25".
std::string diameter_string(const float diameter)
{
    std::ostringstream stream;
    stream << std::fixed << std::setprecision(2) << diameter;
    std::string text = stream.str();
    if (text.find('.') != std::string::npos) {
        text.erase(text.find_last_not_of('0') + 1);
        if (text.back() == '.') {
            text += '0';
        }
    }
    return text;
}

std::string bundle_name(Slic3r::PresetBundle& bundle, const Slic3r::Preset& preset)
{
    bundle.bundles.ReadLock();
    const auto found = bundle.bundles.m_bundles.find(preset.bundle_id);
    std::string name = found == bundle.bundles.m_bundles.end() ? std::string() : found->second.name;
    bundle.bundles.ReadUnlock();
    return name;
}

std::string lower(std::string text)
{
    std::transform(text.begin(), text.end(), text.begin(), [](const unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return text;
}

struct ComboEntry {
    std::string label;
    std::string vendor;
    std::string type;
    std::string bundle;
};

using ComboEntries = std::map<std::string, ComboEntry>;

// The order of "add_presets" in PlaterPresetComboBox::update(): sorted by the
// key, a non-empty key before an empty one, then by the name.
std::vector<ComboEntries::const_iterator> sorted_by(const ComboEntries& entries, const std::function<std::string(const ComboEntry&)>& key)
{
    std::vector<ComboEntries::const_iterator> list;
    for (auto entry = entries.begin(); entry != entries.end(); ++entry) {
        list.push_back(entry);
    }
    std::stable_sort(list.begin(), list.end(), [&key](const auto l, const auto r) {
        const std::string l_key = lower(key(l->second));
        const std::string r_key = lower(key(r->second));
        if (l_key.empty() != r_key.empty()) {
            return !l_key.empty();
        }
        return l_key != r_key ? l_key < r_key : l->first < r->first;
    });
    return list;
}

// The submenu of an entry: its text, and whether that is a msgid.
struct Subgroup {
    std::string text;
    bool msgid{false};
};

void append_items(std::vector<PresetItem>& items, const std::vector<ComboEntries::const_iterator>& list, const PresetGroup group,
                  const std::string& selected, const std::function<Subgroup(const ComboEntry&)>& subgroup)
{
    for (const auto entry : list) {
        PresetItem& item = items.emplace_back();
        item.name = entry->first;
        item.label = entry->second.label;
        item.group = group;
        const Subgroup of = subgroup(entry->second);
        item.subgroup = of.text;
        item.subgroup_msgid = of.msgid;
        item.selected = entry->first == selected;
    }
}

std::vector<ComboEntries::const_iterator> in_order(const ComboEntries& entries)
{
    std::vector<ComboEntries::const_iterator> list;
    for (auto entry = entries.begin(); entry != entries.end(); ++entry) {
        list.push_back(entry);
    }
    return list;
}

// add_presets() of PlaterPresetComboBox::update(): the filaments of "System
// presets" and "Unsupported presets" in the order of Bambu's own first, then
// by vendor and type.
void sort_filaments(std::vector<ComboEntries::const_iterator>& list)
{
    static const std::vector<std::string> filament_orders = {"Bambu PLA Basic", "Bambu PLA Matte", "Bambu PETG HF", "Bambu ABS", "Bambu PLA Silk", "Bambu PLA-CF",
                                                             "Bambu PLA Galaxy", "Bambu PLA Metal", "Bambu PLA Marble", "Bambu PETG-CF", "Bambu PETG Translucent", "Bambu ABS-GF"};
    static const std::vector<std::string> first_vendors = {"", "Bambu", "Generic"};
    static const std::vector<std::string> first_types = {"PLA", "PETG", "ABS", "TPU"};
    const auto position = [](const std::vector<std::string>& order, const std::string& value) {
        return std::find(order.begin(), order.end(), value) - order.begin();
    };
    std::stable_sort(list.begin(), list.end(), [&position](const auto l, const auto r) {
        if (const auto l_order = position(filament_orders, l->first), r_order = position(filament_orders, r->first); l_order != r_order) {
            return l_order < r_order;
        }
        if (const auto l_vendor = position(first_vendors, l->second.vendor), r_vendor = position(first_vendors, r->second.vendor); l_vendor != r_vendor) {
            return l_vendor < r_vendor;
        }
        if (const auto l_type = position(first_types, l->second.type), r_type = position(first_types, r->second.type); l_type != r_type) {
            return l_type < r_type;
        }
        return l->first < r->first;
    });
}

// PlaterPresetComboBox::update() for the printer or the first filament, with
// the Preferences' "Group user filament presets" (group_filament_presets) and
// "Show unsupported presets" (show_unsupported_presets).
std::vector<PresetItem> plater_combo_items(Slic3r::PresetBundle& bundle, const Slic3r::Preset::Type type)
{
    const bool is_filament = type == Slic3r::Preset::TYPE_FILAMENT;
    Slic3r::PresetCollection& collection = is_filament ? bundle.filaments : static_cast<Slic3r::PresetCollection&>(bundle.printers);
    if (is_filament && bundle.filament_presets.empty()) {
        return {};
    }

    ComboEntries project_presets;
    ComboEntries user_presets;
    ComboEntries bundle_presets;
    ComboEntries system_presets;
    ComboEntries unsupported_presets;
    std::unordered_set<std::string> system_printer_models;
    std::string selected_user_preset;
    std::string selected_bundle_preset;
    std::string selected_system_preset;

    const std::deque<Slic3r::Preset>& presets = collection.get_presets();
    for (size_t i = presets.front().is_visible ? 0 : collection.num_default_presets(); i < presets.size(); ++i) {
        Slic3r::Preset& preset = collection.preset(i, true);
        const bool is_selected = is_filament ? bundle.filament_presets.front() == preset.name : i == collection.get_selected_idx();
        if (!is_selected && !preset.is_visible) {
            continue;
        }
        if (is_selected && !preset.is_visible) {
            preset.is_visible = true;
        }

        std::string name = preset.name;
        ComboEntry entry{preset.label(false)};
        if (preset.is_from_bundle()) {
            entry.bundle = bundle_name(bundle, preset);
        }
        if (is_filament) {
            if (const auto* vendor = preset.config.option<Slic3r::ConfigOptionStrings>("filament_vendor"); vendor != nullptr && !vendor->values.empty()) {
                entry.vendor = vendor->values.front() == "Bambu Lab" ? "Bambu" : vendor->values.front();
            }
            if (const auto* filament_type = preset.config.option<Slic3r::ConfigOptionStrings>("filament_type"); filament_type != nullptr && !filament_type->values.empty()) {
                entry.type = filament_type->values.front();
            }
        }

        if (!preset.is_compatible) {
            // Unsupported presets, but not the templates.
            if (!boost::algorithm::ends_with(name, " template")) {
                unsupported_presets.emplace(name, entry);
            }
            continue;
        }
        if (preset.is_default || preset.is_system) {
            if (!is_filament) {
                // A system printer is listed once per printer model.
                name = preset.config.opt_string("printer_model");
                entry.label = name;
                if (system_printer_models.insert(name).second) {
                    system_presets.emplace(name, entry);
                }
            } else {
                system_presets.emplace(name, entry);
            }
            if (is_selected) {
                selected_system_preset = name;
            }
        } else if (preset.is_project_embedded) {
            project_presets.emplace(name, entry);
            if (is_selected) {
                selected_user_preset = name;
            }
        } else if (preset.is_from_bundle()) {
            bundle_presets.emplace(name, entry);
            if (is_selected) {
                selected_bundle_preset = name;
            }
        } else {
            user_presets.emplace(name, entry);
            if (is_selected) {
                selected_user_preset = name;
            }
        }
    }

    std::vector<PresetItem> items;
    const auto msgid = [](const std::string& text) { return [text](const ComboEntry&) { return Subgroup{text, true}; }; };
    // "Project-inside presets" in a "Project" submenu, as the map orders them.
    append_items(items, in_order(project_presets), PresetGroup::project, selected_user_preset, msgid("Project"));

    // "User presets": the user filaments grouped as the Preferences choose
    // ("0" all under "Custom", "2" by type, "3" by vendor, "Unspecified" for a
    // filament without one), the printers and ungrouped filaments by their label.
    const std::string grouping = is_filament ? engine().config->get("group_filament_presets") : std::string();
    if (grouping == "0") {
        append_items(items, in_order(user_presets), PresetGroup::user, selected_user_preset, msgid("Custom"));
    } else if (grouping == "2" || grouping == "3") {
        const auto key = [grouping](const ComboEntry& entry) { return grouping == "2" ? entry.type : entry.vendor; };
        append_items(items, sorted_by(user_presets, key), PresetGroup::user, selected_user_preset, [key](const ComboEntry& entry) {
            const std::string text = key(entry);
            return text.empty() ? Subgroup{"Unspecified", true} : Subgroup{text};
        });
    } else {
        append_items(items, sorted_by(user_presets, [](const ComboEntry& entry) { return entry.label; }), PresetGroup::user, selected_user_preset,
                     [](const ComboEntry&) { return Subgroup{}; });
    }
    append_items(items, sorted_by(bundle_presets, [](const ComboEntry& entry) { return entry.bundle; }), PresetGroup::bundle, selected_bundle_preset,
                 [](const ComboEntry& entry) { return Subgroup{entry.bundle}; });

    std::vector<ComboEntries::const_iterator> system_list = in_order(system_presets);
    if (is_filament) {
        sort_filaments(system_list);
    }
    append_items(items, system_list, PresetGroup::system, selected_system_preset, [](const ComboEntry& entry) { return Subgroup{entry.vendor}; });

    // "Unsupported presets" in an "Unsupported" submenu, which nothing is selected from.
    if (engine().config->get_bool("show_unsupported_presets")) {
        std::vector<ComboEntries::const_iterator> unsupported_list = in_order(unsupported_presets);
        if (is_filament) {
            sort_filaments(unsupported_list);
        }
        append_items(items, unsupported_list, PresetGroup::unsupported, {}, msgid("Unsupported"));
    }
    return items;
}

// TabPresetComboBox::update() of the process tab: the visible presets
// compatible with the printer, and the selected one; the presets inside the
// project come first ("Project-inside presets").
std::vector<PresetItem> tab_combo_items(Slic3r::PresetBundle& bundle, Slic3r::PresetCollection& collection)
{
    ComboEntries project_presets;
    ComboEntries user_presets;
    ComboEntries bundle_presets;
    ComboEntries system_presets;
    std::string selected;

    const std::deque<Slic3r::Preset>& presets = collection.get_presets();
    const size_t idx_selected = collection.get_selected_idx();
    for (size_t i = presets.front().is_visible ? 0 : collection.num_default_presets(); i < presets.size(); ++i) {
        const Slic3r::Preset& preset = presets[i];
        if (!preset.is_visible || (!preset.is_compatible && i != idx_selected)) {
            continue;
        }
        if (i == idx_selected) {
            selected = preset.name;
        }
        // TabPresetComboBox::get_preset_name() with the label update_dirty() gives the selected preset.
        const Slic3r::Preset& shown = i == idx_selected ? collection.get_edited_preset() : preset;
        if (preset.is_default || preset.is_system) {
            system_presets.emplace(preset.name, ComboEntry{shown.label(true)});
        } else if (preset.is_project_embedded) {
            project_presets.emplace(preset.name, ComboEntry{shown.label(true)});
        } else if (preset.is_from_bundle()) {
            bundle_presets.emplace(preset.name, ComboEntry{shown.label(false), {}, {}, bundle_name(bundle, preset)});
        } else {
            user_presets.emplace(preset.name, ComboEntry{shown.label(true)});
        }
    }

    std::vector<PresetItem> items;
    const auto no_subgroup = [](const ComboEntry&) { return Subgroup{}; };
    append_items(items, in_order(project_presets), PresetGroup::project, selected, no_subgroup);
    append_items(items, in_order(user_presets), PresetGroup::user, selected, no_subgroup);
    append_items(items, in_order(bundle_presets), PresetGroup::bundle, selected, [](const ComboEntry& entry) { return Subgroup{entry.bundle}; });
    append_items(items, in_order(system_presets), PresetGroup::system, selected, no_subgroup);
    return items;
}

// Plater::get_curr_printer_model(): the system model of the selected printer,
// or of the preset it inherits from.
const Slic3r::VendorProfile::PrinterModel* current_printer_model(Slic3r::PresetBundle& bundle)
{
    const Slic3r::VendorProfile::PrinterModel* model = Slic3r::PresetUtils::system_printer_model(bundle.printers.get_selected_preset());
    if (model == nullptr) {
        if (const Slic3r::Preset* parent = bundle.printers.get_selected_preset_parent(); parent != nullptr) {
            model = Slic3r::PresetUtils::system_printer_model(*parent);
        }
    }
    return model;
}

// Sidebar::reset_bed_type_combox_choices(): the plate types of curr_bed_type
// the printer model supports, every one for a printer without a system model.
std::vector<Slic3r::BedType> printer_bed_types(Slic3r::PresetBundle& bundle)
{
    std::vector<Slic3r::BedType> types;
    const Slic3r::VendorProfile::PrinterModel* pm = current_printer_model(bundle);
    const Slic3r::ConfigOptionDef* bed_type_def = Slic3r::print_config_def.get("curr_bed_type");
    int index = 0;
    for (const std::string& item : bed_type_def->enum_labels) {
        index++;
        if (pm != nullptr && std::find(pm->not_support_bed_types.begin(), pm->not_support_bed_types.end(), item) != pm->not_support_bed_types.end()) {
            continue;
        }
        types.emplace_back(Slic3r::BedType(index));  // BedType //btPC =1
    }
    return types;
}

// Plater::priv::on_select_bed_type(): the project prints on new_bed_type, which
// the app configuration remembers, also for the selected printer.
bool set_bed_type(Slic3r::PresetBundle& bundle, const Slic3r::BedType new_bed_type)
{
    Slic3r::DynamicPrintConfig& proj_config = bundle.project_config;
    const Slic3r::BedType old_bed_type = proj_config.opt_enum<Slic3r::BedType>("curr_bed_type");
    if (old_bed_type == new_bed_type) {
        return false;
    }
    proj_config.set_key_value("curr_bed_type", new Slic3r::ConfigOptionEnum<Slic3r::BedType>(new_bed_type));
    engine().config->set("curr_bed_type", std::to_string(int(new_bed_type)));
    engine().config->set_printer_setting(bundle.printers.get_selected_preset_name(), "curr_bed_type", std::to_string(int(new_bed_type)));
    return true;
}

// Sidebar::update_all_preset_comboboxes() for another printer: a Bambu Lab
// printer, or one that supports several plate types, takes the plate type the
// app configuration remembers for it, or its default one; any other printer
// takes its default. set_bed_type_accord_combox() selects the first type of
// the combo box when the printer lacks that one.
void update_bed_type(Slic3r::PresetBundle& bundle)
{
    Slic3r::Preset& printer = bundle.printers.get_edited_preset();
    const Slic3r::DynamicPrintConfig& cfg = printer.config;
    Slic3r::BedType bed_type = printer.get_default_bed_type(&bundle);
    if (bundle.is_bbl_vendor() || cfg.opt_bool("support_multi_bed_types")) {
        const std::string str_bed_type = engine().config->get_printer_setting(bundle.printers.get_selected_preset_name(), "curr_bed_type");
        if (!str_bed_type.empty()) {
            int bed_type_value = atoi(str_bed_type.c_str());
            if (bed_type_value <= 0 || bed_type_value >= Slic3r::btCount) {
                bed_type_value = printer.get_default_bed_type(&bundle);
            }
            bed_type = Slic3r::BedType(bed_type_value);
        }
    }
    const std::vector<Slic3r::BedType> types = printer_bed_types(bundle);
    if (!types.empty() && std::find(types.begin(), types.end(), bed_type) == types.end()) {
        bed_type = types.front();
    }
    if (set_bed_type(bundle, bed_type)) {
        save_config(engine());
    }
}

PresetState preset_state(Slic3r::PresetBundle& bundle)
{
    PresetState state;
    state.status = SceneStatus::success;
    state.setup_required = !engine().config_existed || bundle.printers.only_default_printers();
    state.selection.printer = bundle.printers.get_selected_preset_name();
    state.selection.process = bundle.prints.get_selected_preset_name();
    state.selection.filament = bundle.filament_presets.empty() ? std::string() : bundle.filament_presets.front();
    // PresetBundle::filament_presets: every filament of the plate, as the
    // sidebar lists them, with the colour project_config keeps for each.
    state.selection.filaments = bundle.filament_presets;
    if (const auto* colors = bundle.project_config.option<Slic3r::ConfigOptionStrings>("filament_colour"); colors != nullptr) {
        state.filament_colors = colors->values;
    }
    state.filament_colors.resize(bundle.filament_presets.size());
    for (const std::string& name : bundle.filament_presets) {
        const Slic3r::Preset* const filament = bundle.filaments.find_preset(name, false);
        const auto* const types = filament == nullptr ? nullptr : filament->config.option<Slic3r::ConfigOptionStrings>("filament_type");
        state.filament_types.push_back(types == nullptr || types->values.empty() ? std::string() : types->values.front());
        std::string displayed;
        if (filament != nullptr) {
            Slic3r::DynamicPrintConfig config = filament->config;
            config.get_filament_type(displayed);
        }
        state.filament_display_types.push_back(displayed);
    }
    state.printers = plater_combo_items(bundle, Slic3r::Preset::TYPE_PRINTER);
    state.filaments = plater_combo_items(bundle, Slic3r::Preset::TYPE_FILAMENT);
    state.processes = tab_combo_items(bundle, bundle.prints);

    // Sidebar::update_presets() for a printer with one extruder.
    const auto* nozzle_diameter = bundle.printers.get_edited_preset().config.option<Slic3r::ConfigOptionFloats>("nozzle_diameter");
    std::vector<std::string> diameters = bundle.printers.diameters_of_selected_printer();
    const std::string nozzle = nozzle_diameter == nullptr || nozzle_diameter->values.empty() ? std::string() : diameter_string(float(nozzle_diameter->values.front()));
    if (!diameters.empty() && diameters.front().empty() && !nozzle.empty()) {
        diameters.front() = nozzle;
    }
    if (!nozzle.empty() && std::find(diameters.begin(), diameters.end(), nozzle) == diameters.end()) {
        diameters.push_back(nozzle);
    }
    state.nozzle_diameters = std::move(diameters);
    state.nozzle_diameter = nozzle;

    // Sidebar::update_all_preset_comboboxes(): the project takes the plate type
    // of a printer the sidebar shows anew; a loaded project keeps its own.
    if (engine().bed_type_printer != state.selection.printer) {
        engine().bed_type_printer = state.selection.printer;
        update_bed_type(bundle);
    }
    // The plate types of the sidebar's combo box and PlateSettingsDialog, which
    // the sidebar shows for a Bambu Lab printer or one that supports several
    // (panel_printer_bed), and the dialog lets a Bambu Lab printer choose.
    const Slic3r::ConfigOptionDef* bed_type_def = Slic3r::print_config_def.get("curr_bed_type");
    const auto value_of = [bed_type_def](const Slic3r::BedType type) {
        const std::size_t index = std::size_t(type) - 1;
        return index < bed_type_def->enum_values.size() ? bed_type_def->enum_values[index] : std::string();
    };
    for (const Slic3r::BedType type : printer_bed_types(bundle)) {
        state.bed_type_values.push_back(value_of(type));
        state.bed_type_labels.push_back(bed_type_def->enum_labels[std::size_t(type) - 1]);
    }
    state.bed_type = value_of(bundle.project_config.opt_enum<Slic3r::BedType>("curr_bed_type"));
    state.bed_type_selectable = bundle.is_bbl_vendor() || bundle.printers.get_edited_preset().config.opt_bool("support_multi_bed_types");
    state.plate_bed_type_selectable = bundle.is_bbl_vendor();
    state.multi_material_buttons = bundle.printers.get_edited_preset().config.opt_bool("single_extruder_multi_material") || bundle.is_bbl_vendor();
    return state;
}

// Tab::select_preset() of the printer tab with the default preferences, whose
// "Remember printer configuration" selects the process and filament the
// printer last used (PresetBundle::update_selections).
void select_printer(Slic3r::PresetBundle& bundle, const std::string& name)
{
    bundle.printers.select_preset_by_name(name, false);
    bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always, Slic3r::PresetSelectCompatibleType::Always);
    if (engine().config->get_bool("remember_printer_config")) {
        bundle.update_selections(*engine().config);
    }
}

// get_preferred_printer_model in GuideFrame::apply_config(): the first model of
// the vendor the wizard installs, or installs another nozzle diameter of, with
// that nozzle diameter. Reads the vendor profile without inserting an empty one.
std::string preferred_printer_model(const Slic3r::PresetBundle& bundle, const VendorMap& enabled_vendors, const VendorMap& old_enabled_vendors,
                                    const std::string& bundle_name, std::string& variant)
{
    const auto config = enabled_vendors.find(bundle_name);
    if (config == enabled_vendors.end()) {
        return {};
    }
    const auto printer_profile = bundle.vendors.find(bundle_name);
    for (const auto& [model_id, variants] : config->second) {
        if (variants.empty()) {
            continue;
        }
        variant = *variants.begin();
        if (variants.size() > 1) {
            if (printer_profile != bundle.vendors.end() && !printer_profile->second.models.empty()) {
                const auto& models = printer_profile->second.models;
                const auto printer_model = std::find_if(models.begin(), models.end(), [&id = model_id](const auto& model) { return model.id == id; });
                if (printer_model != models.end()) {
                    for (const auto& printer_variant : printer_model->variants) {
                        if (variants.count(printer_variant.name) > 0) {
                            variant = printer_variant.name;
                            break;
                        }
                    }
                }
            } else if (variant != Slic3r::PresetBundle::ORCA_DEFAULT_PRINTER_VARIANT
                       && variants.count(Slic3r::PresetBundle::ORCA_DEFAULT_PRINTER_VARIANT) > 0) {
                variant = Slic3r::PresetBundle::ORCA_DEFAULT_PRINTER_VARIANT;
            }
        }

        const auto config_old = old_enabled_vendors.find(bundle_name);
        if (config_old == old_enabled_vendors.end()) {
            return model_id;
        }
        const auto model_old = config_old->second.find(model_id);
        if (model_old == config_old->second.end()) {
            return model_id;
        }
        if (model_old->second != variants) {
            for (const std::string& added : variants) {
                if (model_old->second.count(added) == 0) {
                    variant = added;
                    return model_id;
                }
            }
        }
    }
    variant.clear();
    return {};
}

PresetState preset_failure(const SceneStatus status, std::string message)
{
    PresetState result;
    result.status = status;
    result.message = std::move(message);
    return result;
}

}  // namespace

PresetState describe_presets()
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        follow_config(engine());
        return preset_state(*engine().bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

namespace {

// The presets a tab edits.
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

// Tab::select_preset(): which tab's preset the choice changes.
PresetKind choice_kind(const PresetChoice choice)
{
    switch (choice) {
    case PresetChoice::filament:
    case PresetChoice::slot_filament:
    case PresetChoice::edit_filament:
        return PresetKind::filament;
    case PresetChoice::process:
        return PresetKind::print;
    case PresetChoice::printer:
    case PresetChoice::printer_model:
    case PresetChoice::nozzle_diameter:
    default:
        return PresetKind::printer;
    }
}

// PresetComboBox::update_ams_color() of a slot that takes a preset: the
// slot's colour is the preset's default colour, when it has one.
void take_default_colour(Slic3r::PresetBundle& bundle, const std::size_t index, const std::string& name)
{
    const Slic3r::Preset* preset = bundle.filaments.find_preset(name, false);
    const auto* defaults = preset == nullptr ? nullptr : preset->config.option<Slic3r::ConfigOptionStrings>("default_filament_colour");
    const std::string color = defaults == nullptr || defaults->values.empty() ? std::string() : defaults->values.front();
    if (color.empty()) {
        return;
    }
    auto* color_head = bundle.project_config.option<Slic3r::ConfigOptionStrings>("filament_colour", true);
    auto* color_type = bundle.project_config.option<Slic3r::ConfigOptionStrings>("filament_colour_type", true);
    auto* color_pack = bundle.project_config.option<Slic3r::ConfigOptionStrings>("filament_multi_colour", true);
    for (Slic3r::ConfigOptionStrings* option : {color_head, color_type, color_pack}) {
        if (option->values.size() <= index) {
            option->values.resize(index + 1);
        }
    }
    color_head->values[index] = color;
    color_type->values[index] = std::string();
    color_pack->values[index] = color;
}

// Sidebar::update_presets() of the filament tab's preset: the slot being
// edited takes it, or else the only slot, when the preset suits the printer.
void update_filament_slots(Slic3r::PresetBundle& bundle)
{
    const std::string& name = bundle.filaments.get_selected_preset_name();
    const int editing = engine().editing_filament;
    if (editing >= 0 && std::size_t(editing) < bundle.filament_presets.size()) {
        bundle.set_filament_preset(std::size_t(editing), name);
    } else if (bundle.filament_presets.size() == 1) {
        const Slic3r::Preset* preset = bundle.filaments.find_preset(name, false);
        if (preset != nullptr && preset->is_compatible) {
            bundle.set_filament_preset(0, name);
        }
    }
}

// Tab::select_preset(): the changes of a printer are never moved, and neither
// are the ones of a filament of another type.
bool may_transfer(Slic3r::PresetBundle& bundle, const PresetChoice choice, const std::string& value)
{
    // printer_tab: no_transfer = true.
    if (choice == PresetChoice::printer || choice == PresetChoice::printer_model || choice == PresetChoice::nozzle_diameter) {
        return false;
    }
    if (choice_kind(choice) != PresetKind::filament) {
        return true;
    }
    const Slic3r::Preset* to_be_selected = bundle.filaments.find_preset(value, false, true);
    if (to_be_selected == nullptr) {
        return true;
    }
    const auto* current = dynamic_cast<const Slic3r::ConfigOptionStrings*>(bundle.filaments.get_edited_preset().config.option("filament_type"));
    const auto* to_select = dynamic_cast<const Slic3r::ConfigOptionStrings*>(to_be_selected->config.option("filament_type"));
    const std::string current_type = current != nullptr && !current->values.empty() ? current->values[0] : std::string();
    const std::string to_select_type = to_select != nullptr && !to_select->values.empty() ? to_select->values[0] : std::string();
    return current_type == to_select_type;
}

// Tab::select_preset(): the presets the selection would change with it, whose
// unsaved changes the user is asked about too: the process and the filament
// of a printer they do not suit, the filament of a process it does not suit.
std::vector<PresetKind> dirty_dependents(Slic3r::PresetBundle& bundle, const PresetChoice choice, const std::string& value)
{
    std::vector<PresetKind> dependents;
    if (choice == PresetChoice::process) {
        const Slic3r::Preset* print = bundle.prints.find_preset(value, true);
        if (print == nullptr) {
            return dependents;
        }
        const Slic3r::PresetWithVendorProfile printer_profile = bundle.printers.get_edited_preset_with_vendor_profile();
        Slic3r::PresetCollection& dependent = bundle.filaments;
        const bool old_preset_dirty = dependent.current_is_dirty();
        const bool new_preset_compatible = Slic3r::is_compatible_with_print(dependent.get_edited_preset_with_vendor_profile(),
                                                                            bundle.prints.get_preset_with_vendor_profile(*print), printer_profile);
        if (old_preset_dirty && !new_preset_compatible) {
            dependents.push_back(PresetKind::filament);
        }
        return dependents;
    }
    const Slic3r::Preset* new_printer_preset = nullptr;
    switch (choice) {
    case PresetChoice::printer:
        new_printer_preset = bundle.printers.find_preset(value, true);
        break;
    case PresetChoice::printer_model:
        new_printer_preset = bundle.get_similar_printer_preset(value, {});
        break;
    case PresetChoice::nozzle_diameter: {
        const auto* nozzle_diameter = bundle.printers.get_edited_preset().config.option<Slic3r::ConfigOptionFloats>("nozzle_diameter");
        if (nozzle_diameter == nullptr || nozzle_diameter->values.empty() || diameter_string(float(nozzle_diameter->values.front())) != value) {
            new_printer_preset = bundle.get_similar_printer_preset({}, value);
        }
        break;
    }
    default:
        break;
    }
    if (new_printer_preset == nullptr) {
        return dependents;
    }
    const Slic3r::PresetWithVendorProfile new_printer = bundle.printers.get_preset_with_vendor_profile(*new_printer_preset);
    const std::pair<PresetKind, Slic3r::PresetCollection*> updates[] = {{PresetKind::print, &bundle.prints}, {PresetKind::filament, &bundle.filaments}};
    for (const auto& [kind, presets] : updates) {
        const bool old_preset_dirty = presets->current_is_dirty();
        const bool new_preset_compatible = Slic3r::is_compatible_with_printer(presets->get_edited_preset_with_vendor_profile(), new_printer);
        if (old_preset_dirty && !new_preset_compatible) {
            dependents.push_back(kind);
        }
    }
    return dependents;
}

}  // namespace

PresetState select_preset(const PresetChoice choice, const std::string& value, const PresetChangeAction action)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        // The preset to select: an edited filament slot's own.
        std::string target = value;
        if (choice == PresetChoice::edit_filament) {
            const int slot = std::stoi(value);
            if (slot < 0 || std::size_t(slot) >= bundle.filament_presets.size()) {
                return preset_failure(SceneStatus::profile_not_found, "No such filament slot");
            }
            target = bundle.filament_presets[std::size_t(slot)];
            engine().editing_filament = -1;
            // PlaterPresetComboBox::switch_to_tab(): "Call select_preset() only if
            // there is new preset and not just modified".
            if (bundle.filaments.get_edited_preset().name == target) {
                engine().editing_filament = slot;
                return preset_state(bundle);
            }
        }
        // Tab::select_preset(): a preset with unsaved changes is not left
        // behind before the user says what happens to them, and neither is
        // one that depends on it and would change with it
        // (may_discard_current_dirty_preset() of the dependent collection,
        // whose dialog moves nothing). Every answer goes to the first question
        // still open; the dependent presets are asked about first, so the
        // changes the selected preset moves into the new one are taken last.
        const PresetKind kind = choice_kind(choice);
        std::vector<PresetKind> questions = dirty_dependents(bundle, choice, target);
        if (preset_collection(bundle, kind).current_is_dirty()) {
            questions.push_back(kind);
        }
        PresetChangeAction answer = action;
        for (const PresetKind asked : questions) {
            Slic3r::PresetCollection& asked_presets = preset_collection(bundle, asked);
            if (answer == PresetChangeAction::ask) {
                PresetState result = preset_state(bundle);
                result.asks_unsaved_changes = true;
                result.changed_kind = asked;
                result.unsaved_changes = detail::preset_changes(asked);
                result.can_transfer = asked == kind && may_transfer(bundle, choice, target);
                result.save_name = detail::save_preset_name(asked_presets.get_selected_preset(), result.save_name_copy_suffix);
                result.save_can_overwrite = asked_presets.get_edited_preset().can_overwrite();
                if (result.save_can_overwrite) {
                    result.save_name = asked_presets.get_edited_preset().name;
                    result.save_name_copy_suffix = false;
                }
                return result;
            }
            if (asked == kind && answer == PresetChangeAction::transfer) {
                detail::cache_preset_changes(kind);
            }
            // The changes leave with the preset, even when it is selected again.
            asked_presets.discard_current_changes();
            answer = PresetChangeAction::ask;
        }
        switch (choice) {
        case PresetChoice::printer:
            if (bundle.printers.find_preset(value, false) == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Unknown printer profile: " + value);
            }
            select_printer(bundle, value);
            break;
        case PresetChoice::printer_model: {
            // Plater::priv::on_select_preset() for a printer model.
            Slic3r::Preset* preset = bundle.get_similar_printer_preset(value, {});
            if (preset == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Unknown printer model: " + value);
            }
            preset->is_visible = true;
            select_printer(bundle, preset->name);
            break;
        }
        case PresetChoice::nozzle_diameter: {
            // Sidebar::priv::switch_diameter()
            const auto* nozzle_diameter = bundle.printers.get_edited_preset().config.option<Slic3r::ConfigOptionFloats>("nozzle_diameter");
            if (nozzle_diameter != nullptr && !nozzle_diameter->values.empty() && diameter_string(float(nozzle_diameter->values.front())) == value) {
                break;
            }
            Slic3r::Preset* preset = bundle.get_similar_printer_preset({}, value);
            if (preset == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Configuration incompatible");
            }
            preset->is_visible = true;
            select_printer(bundle, preset->name);
            break;
        }
        case PresetChoice::filament:
        case PresetChoice::slot_filament:
        case PresetChoice::edit_filament:
            if (bundle.filaments.find_preset(target, false) == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Unknown filament profile: " + target);
            }
            if (choice == PresetChoice::slot_filament) {
                take_default_colour(bundle, 0, target);
            }
            // Tab::select_preset() of the filament tab, and Sidebar::update_presets().
            bundle.filaments.select_preset_by_name(target, false);
            update_filament_slots(bundle);
            if (choice == PresetChoice::edit_filament) {
                engine().editing_filament = std::stoi(value);
            }
            break;
        case PresetChoice::process:
            if (bundle.prints.find_preset(value, false) == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Unknown process profile: " + value);
            }
            // Tab::select_preset() of the process tab.
            bundle.prints.select_preset_by_name(value, false);
            bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Never, Slic3r::PresetSelectCompatibleType::Always);
            break;
        default:
            return preset_failure(SceneStatus::profile_not_found, "Unknown preset choice");
        }
        // Tab::select_preset(): the values the change moved go into the preset
        // that was selected.
        detail::reload_tab_after_selection(kind);
        bundle.export_selections(*engine().config);
        save_config(engine());
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

PresetState select_bed_type(const std::string& value)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        const Slic3r::t_config_enum_values* keys_map = Slic3r::print_config_def.get("curr_bed_type")->enum_keys_map;
        const auto found = keys_map->find(value);
        if (found == keys_map->end()) {
            return preset_failure(SceneStatus::profile_not_found, "Unknown plate type: " + value);
        }
        if (set_bed_type(bundle, Slic3r::BedType(found->second))) {
            save_config(engine());
        }
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

DirtyPresets dirty_presets()
{
    DirtyPresets result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        for (const PresetKind kind : {PresetKind::print, PresetKind::filament, PresetKind::printer}) {
            Slic3r::PresetCollection& presets = preset_collection(bundle, kind);
            if (!presets.current_is_dirty()) {
                continue;
            }
            DirtyPreset& dirty = result.presets.emplace_back();
            dirty.kind = kind;
            dirty.name = presets.get_edited_preset().name;
            dirty.can_overwrite = presets.get_edited_preset().can_overwrite();
            dirty.save_name = detail::save_preset_name(presets.get_selected_preset(), dirty.save_name_copy_suffix);
            dirty.changes = detail::preset_changes(kind);
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

PresetState discard_preset_changes()
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        for (const PresetKind kind : {PresetKind::print, PresetKind::filament, PresetKind::printer}) {
            Slic3r::PresetCollection& presets = preset_collection(bundle, kind);
            if (presets.current_is_dirty()) {
                presets.discard_current_changes();
                // load_current_presets()
                detail::reload_tab(kind);
            }
        }
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

PresetState reset_project_presets()
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        //BBS: reset all project embedded presets
        bundle.reset_project_embedded_presets();
        for (const PresetKind kind : {PresetKind::print, PresetKind::filament, PresetKind::printer}) {
            detail::reload_tab(kind);
        }
        bundle.export_selections(*engine().config);
        save_config(engine());
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

void update_saved_presets()
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return;
    }
    Slic3r::PresetBundle& bundle = *engine().bundle;
    for (const PresetKind kind : {PresetKind::print, PresetKind::filament, PresetKind::printer}) {
        preset_collection(bundle, kind).update_saved_preset_from_current_preset();
    }
}

PresetState transfer_preset_options(const PresetKind kind, const std::string& from, const std::string& to, const std::vector<std::string>& options)
{
    PresetChoice choice = PresetChoice::process;
    switch (kind) {
    case PresetKind::printer:
        choice = PresetChoice::printer;
        break;
    case PresetKind::filament:
        choice = PresetChoice::filament;
        break;
    case PresetKind::print:
        break;
    default:
        return preset_failure(SceneStatus::profile_not_found, "Only presets transfer their values");
    }
    {
        const std::lock_guard<std::mutex> engine_lock(engine().mutex);
        if (engine().bundle == nullptr) {
            return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
        }
        try {
            Slic3r::PresetBundle& bundle = *engine().bundle;
            follow_config(engine());
            Slic3r::PresetCollection& presets = preset_collection(bundle, kind);
            const Slic3r::Preset* preset_from = presets.find_preset(from, false);
            const Slic3r::Preset* preset_to = presets.find_preset(to, false);
            if (preset_from == nullptr || preset_to == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "One of the presets does not exist");
            }
            if (options.empty()) {
                return preset_state(bundle);
            }
            // enable_transfer() of the dialog's Transfer button: a preset with
            // unsaved changes is not left behind.
            const Slic3r::Preset& main_edited_preset = presets.get_edited_preset();
            if (main_edited_preset.is_dirty && main_edited_preset.name != to) {
                return preset_failure(SceneStatus::profile_not_found, "You can only transfer to current active profile because it has been modified.");
            }
            detail::cache_transfer(kind, preset_from->config, options);
            if (to == main_edited_preset.name) {
                // apply_config_from_cache(); load_current_preset();
                detail::reload_tab(kind);
                return preset_state(bundle);
            }
        } catch (const std::exception& error) {
            return preset_failure(SceneStatus::profile_not_found, error.what());
        }
    }
    // select_preset(preset_to->name): the values go into it once it is selected.
    return select_preset(choice, to, PresetChangeAction::ask);
}

// Plater::get_next_color_for_filament(): OrcaSlicer's palette, in its order.
const std::vector<std::string>& filament_palette()
{
    static const std::vector<std::string> colors = {"#00C1AE", "#F4E2C1", "#ED1C24", "#00FF7F", "#F26722", "#FFEB31", "#7841CE", "#115877",
                                                    "#ED1E79", "#2EBDEF", "#345B2F", "#800080", "#FA8173", "#800000", "#F7B763", "#A4C41E"};
    return colors;
}

PresetState add_filament(const std::string& custom_color)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        // Sidebar::add_filament()
        if (bundle.filament_presets.size() >= MAXIMUM_EXTRUDER_NUMBER) {
            return preset_failure(SceneStatus::profile_not_found, "A plate prints with at most 64 filaments");
        }
        const std::size_t count = bundle.filament_presets.size() + 1;
        const std::string color =
            custom_color.empty() ? filament_palette()[engine().next_filament_color++ % filament_palette().size()] : custom_color;
        bundle.set_num_filaments(unsigned(count), color);
        detail::reload_tab_after_selection(PresetKind::print);
        bundle.export_selections(*engine().config);
        save_config(engine());
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

PresetState remove_filament(const std::int64_t index)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        // Sidebar::delete_filament(): the plate keeps at least one filament.
        if (index < 0 || std::size_t(index) >= bundle.filament_presets.size() || bundle.filament_presets.size() <= 1) {
            return preset_failure(SceneStatus::profile_not_found, "A plate prints with at least one filament");
        }
        // The slot being edited leaves with it. Orca keeps the number of a later
        // one, which then names the slot after it; here it comes one lower.
        int& editing = engine().editing_filament;
        if (editing == int(index)) {
            editing = -1;
        } else if (editing > int(index)) {
            --editing;
        }
        bundle.update_num_filaments(unsigned(index));
        detail::reload_tab_after_selection(PresetKind::print);
        bundle.export_selections(*engine().config);
        save_config(engine());
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

PresetState select_filament(const std::int64_t index, const std::string& name, const PresetChangeAction action)
{
    bool only = false;
    {
        const std::lock_guard<std::mutex> engine_lock(engine().mutex);
        only = engine().bundle != nullptr && engine().bundle->filament_presets.size() <= 1;
    }
    if (only && index == 0) {
        // The only filament goes through the filament tab, which asks about
        // unsaved changes as any other preset choice does.
        return select_preset(PresetChoice::slot_filament, name, action);
    }
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        if (index < 0 || std::size_t(index) >= bundle.filament_presets.size()) {
            return preset_failure(SceneStatus::profile_not_found, "No such filament slot");
        }
        if (bundle.filaments.find_preset(name, false) == nullptr) {
            return preset_failure(SceneStatus::profile_not_found, "Unknown filament profile: " + name);
        }
        // PlaterPresetComboBox of that slot: update_ams_color(), then
        // Plater::priv::on_select_preset() of a plate with several filaments,
        // which leaves the filament tab as it is.
        take_default_colour(bundle, std::size_t(index), name);
        bundle.set_filament_preset(std::size_t(index), name);
        bundle.update_multi_material_filament_presets();
        bundle.export_selections(*engine().config);
        save_config(engine());
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

void finish_filament_edit()
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    engine().editing_filament = -1;
}

PresetState set_filament_color(const std::int64_t index, const std::string& color)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        auto* colors = bundle.project_config.option<Slic3r::ConfigOptionStrings>("filament_colour");
        if (colors == nullptr || index < 0 || std::size_t(index) >= bundle.filament_presets.size()) {
            return preset_failure(SceneStatus::profile_not_found, "No such filament slot");
        }
        if (colors->values.size() < bundle.filament_presets.size()) {
            colors->values.resize(bundle.filament_presets.size(), color);
        }
        colors->values[std::size_t(index)] = color;
        // PlaterPresetComboBox::sync_colour_config() of the colour picker's one
        // colour: the slot's colours are that colour alone, and not a gradient.
        auto* multi_colour = bundle.project_config.option<Slic3r::ConfigOptionStrings>("filament_multi_colour", true);
        auto* colour_type = bundle.project_config.option<Slic3r::ConfigOptionStrings>("filament_colour_type", true);
        if (std::size_t(index) >= multi_colour->values.size()) {
            multi_colour->values.resize(std::size_t(index) + 1);
        }
        if (std::size_t(index) >= colour_type->values.size()) {
            colour_type->values.resize(std::size_t(index) + 1);
        }
        multi_colour->values[std::size_t(index)] = color;
        colour_type->values[std::size_t(index)] = "1";
        bundle.export_selections(*engine().config);
        save_config(engine());
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

SetupPrinters describe_setup_printers()
{
    SetupPrinters result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        for (const setup::Model& model : setup_catalog().models) {
            SetupPrinterModel& item = result.models.emplace_back();
            item.vendor = model.vendor;
            item.model = model.model;
            item.name = model.name;
            item.nozzle_diameters = model.nozzle_diameters;
            item.default_materials = model.materials;
            item.cover = model.cover;
            item.installed_nozzles = setup::installed_nozzles(model, *engine().config);
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
        result.models.clear();
    }
    return result;
}

SetupFilaments describe_setup_filaments(const std::vector<std::string>& models)
{
    SetupFilaments result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        const setup::Catalog& data = setup_catalog();
        std::vector<const setup::Model*> chosen;
        std::set<std::string> default_materials;
        for (const std::string& model_id : models) {
            const setup::Model* model = find_setup_model(data, model_id);
            if (model == nullptr) {
                result.status = SceneStatus::profile_not_found;
                result.message = "Unknown printer model: " + model_id;
                return result;
            }
            chosen.push_back(model);
            // GuideFrame::OnScriptMessage("save_userguide_models"): the default materials of a chosen model are selected.
            default_materials.insert(model->materials.begin(), model->materials.end());
        }

        // GuideFrame::LoadProfile(): the installed filaments are selected.
        const std::map<std::string, std::string> installed = engine().config->has_section(Slic3r::AppConfig::SECTION_FILAMENTS)
            ? engine().config->get_section(Slic3r::AppConfig::SECTION_FILAMENTS)
            : std::map<std::string, std::string>();
        for (const auto& [name, filament] : data.filaments) {
            SetupFilament item;
            for (std::size_t index = 0; index < chosen.size(); ++index) {
                const bool compatible = std::any_of(chosen[index]->nozzle_diameters.begin(), chosen[index]->nozzle_diameters.end(),
                    [&filament = filament, model = chosen[index]](const std::string& nozzle) {
                        return filament.models.count({model->model, nozzle}) > 0;
                    });
                if (compatible) {
                    item.models.push_back(static_cast<std::int32_t>(index));
                }
            }
            // The filament page lists a filament for the chosen printers, or for every printer.
            if (!filament.models.empty() && item.models.empty()) {
                continue;
            }
            item.name = name;
            item.vendor = filament.vendor;
            item.type = filament.type;
            item.selected = installed.count(name) > 0 || default_materials.count(name) > 0;
            result.filaments.push_back(std::move(item));
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
        result.filaments.clear();
    }
    return result;
}

namespace {

// GuideFrame::SaveProfile(): the vendors, models and nozzles of the printer
// models the wizard installs, and the filaments it enables.
bool setup_selection(const std::vector<std::string>& models, const std::vector<std::string>& filaments, VendorMap& vendors,
                     std::map<std::string, std::string>& enabled_filaments, std::string& unknown)
{
    const setup::Catalog& data = setup_catalog();
    for (const std::string& model_id : models) {
        const setup::Model* model = find_setup_model(data, model_id);
        if (model == nullptr) {
            unknown = model_id;
            return false;
        }
        vendors[model->vendor][model->model].insert(model->nozzle_diameters.begin(), model->nozzle_diameters.end());
    }
    for (const std::string& filament : filaments) {
        enabled_filaments[filament] = "true";
    }
    return true;
}

}  // namespace

bool setup_changes_installation(const std::vector<std::string>& models, const std::vector<std::string>& filaments)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return false;
    }
    follow_config(engine());
    VendorMap vendors;
    std::map<std::string, std::string> enabled_filaments;
    std::string unknown;
    if (!setup_selection(models, filaments, vendors, enabled_filaments, unknown)) {
        return false;
    }
    // check_unsaved_preset_changes = (enabled_vendors != old_enabled_vendors) || (enabled_filaments != old_enabled_filaments)
    const VendorMap old_vendors = engine().config->vendors();
    const std::map<std::string, std::string> old_filaments = engine().config->has_section(Slic3r::AppConfig::SECTION_FILAMENTS) ?
                                                                 engine().config->get_section(Slic3r::AppConfig::SECTION_FILAMENTS) :
                                                                 std::map<std::string, std::string>();
    return vendors != old_vendors || enabled_filaments != old_filaments;
}

PresetState apply_setup(const std::vector<std::string>& models, const std::vector<std::string>& filaments, const bool keep_changes)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    if (models.empty() || filaments.empty()) {
        return preset_failure(SceneStatus::profile_not_found, "The Setup Wizard installs at least one printer and one filament");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());

        // GuideFrame::SaveProfile()
        VendorMap vendors;
        std::map<std::string, std::string> enabled_filaments;
        std::string unknown;
        if (!setup_selection(models, filaments, vendors, enabled_filaments, unknown)) {
            return preset_failure(SceneStatus::profile_not_found, "Unknown printer model: " + unknown);
        }
        engine().config->set("firstguide", "finish", "1");

        // GUI_App::check_and_keep_current_preset_changes() of the wizard with
        // "Keep": every tab caches its changes (Tab::cache_config_diff()).
        std::vector<PresetKind> kept;
        if (keep_changes) {
            for (const PresetKind kind : {PresetKind::print, PresetKind::filament, PresetKind::printer}) {
                if (preset_collection(bundle, kind).current_is_dirty()) {
                    detail::cache_preset_changes(kind);
                    kept.push_back(kind);
                }
            }
        }

        // GuideFrame::apply_config(): Orca's "custom" printers are considered first, then 3rd party.
        const VendorMap old_vendors = engine().config->vendors();
        std::string preferred_variant;
        std::string preferred_model = preferred_printer_model(bundle, vendors, old_vendors, Slic3r::PresetBundle::ORCA_DEFAULT_BUNDLE, preferred_variant);
        if (preferred_model.empty()) {
            for (const auto& vendor : vendors) {
                if (vendor.first == Slic3r::PresetBundle::ORCA_DEFAULT_BUNDLE) {
                    continue;
                }
                preferred_model = preferred_printer_model(bundle, vendors, old_vendors, vendor.first, preferred_variant);
                if (!preferred_model.empty()) {
                    break;
                }
            }
        }
        if (!bundle.apply_vendor_config(vendors, enabled_filaments, engine().config.get(), true, preferred_model, preferred_variant)) {
            return preset_failure(SceneStatus::write_failed, "Unable to install the vendor bundles");
        }
        // GUI_App::apply_keeped_preset_modifications(): the presets the wizard
        // left selected take the changes (Tab::apply_config_from_cache()).
        for (const PresetKind kind : kept) {
            detail::reload_tab(kind);
        }
        save_config(engine());
        engine().config_existed = true;
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

PresetState reload_system_presets()
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        // Reload global configuration
        // System profiles should not trigger any substitutions, user profiles may trigger substitutions, but these substitutions
        // were already presented to the user on application start up. Just do substitutions now and keep quiet about it.
        // However throw on substitutions in system profiles, those shall never happen with system profiles installed over the air.
        bundle.load_presets(*engine().config, Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilentDisableSystem);
        // GUI_App::load_current_presets()
        for (const PresetKind kind : {PresetKind::print, PresetKind::filament, PresetKind::printer}) {
            detail::reload_tab(kind);
        }
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

PresetState apply_default_setup()
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        if (bundle.printers.only_default_printers()) {
            // GuideFrame::run() for a cancelled wizard: install the default printer, clear the
            // filament section and use the default materials. ORCA_DEFAULT_PRINTER_MODEL names
            // the preset "MyKlipper 0.4 nozzle", not its printer model "Generic Klipper Printer",
            // so the desktop app installs no printer here; the model of that preset is installed.
            std::string model = Slic3r::PresetBundle::ORCA_DEFAULT_PRINTER_MODEL;
            std::string variant = Slic3r::PresetBundle::ORCA_DEFAULT_PRINTER_VARIANT;
            if (const Slic3r::Preset* printer = bundle.printers.find_preset(model, false); printer != nullptr && printer->is_system) {
                model = printer->config.opt_string("printer_model");
                variant = printer->config.opt_string("printer_variant");
            }
            engine().config->set_variant(Slic3r::PresetBundle::ORCA_DEFAULT_BUNDLE, model, variant, true);
            engine().config->clear_section(Slic3r::AppConfig::SECTION_FILAMENTS);
            bundle.load_selections(*engine().config, {model, variant, Slic3r::PresetBundle::ORCA_DEFAULT_FILAMENT, std::string()});
        }
        bundle.export_selections(*engine().config);
        save_config(engine());
        engine().config_existed = true;
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

}  // namespace orcinus::orca
