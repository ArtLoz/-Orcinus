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

void append_items(std::vector<PresetItem>& items, const std::vector<ComboEntries::const_iterator>& list, const PresetGroup group,
                  const std::string& selected, const std::function<std::string(const ComboEntry&)>& subgroup)
{
    for (const auto entry : list) {
        PresetItem& item = items.emplace_back();
        item.name = entry->first;
        item.label = entry->second.label;
        item.group = group;
        item.subgroup = subgroup(entry->second);
        item.selected = entry->first == selected;
    }
}

// PlaterPresetComboBox::update() for the printer or the first filament, with the
// desktop app's default preferences: unsupported presets hidden
// (show_unsupported_presets) and user filaments not grouped (group_filament_presets).
// Android opens no projects, so no project presets are listed.
std::vector<PresetItem> plater_combo_items(Slic3r::PresetBundle& bundle, const Slic3r::Preset::Type type)
{
    const bool is_filament = type == Slic3r::Preset::TYPE_FILAMENT;
    Slic3r::PresetCollection& collection = is_filament ? bundle.filaments : static_cast<Slic3r::PresetCollection&>(bundle.printers);
    if (is_filament && bundle.filament_presets.empty()) {
        return {};
    }

    ComboEntries user_presets;
    ComboEntries bundle_presets;
    ComboEntries system_presets;
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
            // Unsupported presets.
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
            continue;
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
    const auto no_subgroup = [](const ComboEntry&) { return std::string(); };
    append_items(items, sorted_by(user_presets, [](const ComboEntry& entry) { return entry.label; }), PresetGroup::user, selected_user_preset, no_subgroup);
    append_items(items, sorted_by(bundle_presets, [](const ComboEntry& entry) { return entry.bundle; }), PresetGroup::bundle, selected_bundle_preset,
                 [](const ComboEntry& entry) { return entry.bundle; });

    std::vector<ComboEntries::const_iterator> system_list;
    for (auto entry = system_presets.begin(); entry != system_presets.end(); ++entry) {
        system_list.push_back(entry);
    }
    if (is_filament) {
        static const std::vector<std::string> filament_orders = {"Bambu PLA Basic", "Bambu PLA Matte", "Bambu PETG HF", "Bambu ABS", "Bambu PLA Silk", "Bambu PLA-CF",
                                                                 "Bambu PLA Galaxy", "Bambu PLA Metal", "Bambu PLA Marble", "Bambu PETG-CF", "Bambu PETG Translucent", "Bambu ABS-GF"};
        static const std::vector<std::string> first_vendors = {"", "Bambu", "Generic"};
        static const std::vector<std::string> first_types = {"PLA", "PETG", "ABS", "TPU"};
        const auto position = [](const std::vector<std::string>& order, const std::string& value) {
            return std::find(order.begin(), order.end(), value) - order.begin();
        };
        std::stable_sort(system_list.begin(), system_list.end(), [&position](const auto l, const auto r) {
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
    append_items(items, system_list, PresetGroup::system, selected_system_preset, [](const ComboEntry& entry) { return entry.vendor; });
    return items;
}

// TabPresetComboBox::update() of the process tab: the visible presets
// compatible with the printer, and the selected one.
std::vector<PresetItem> tab_combo_items(Slic3r::PresetBundle& bundle, Slic3r::PresetCollection& collection)
{
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
        if (preset.is_project_embedded) {
            continue;
        }
        if (i == idx_selected) {
            selected = preset.name;
        }
        // TabPresetComboBox::get_preset_name() with the label update_dirty() gives the selected preset.
        const Slic3r::Preset& shown = i == idx_selected ? collection.get_edited_preset() : preset;
        if (preset.is_default || preset.is_system) {
            system_presets.emplace(preset.name, ComboEntry{shown.label(true)});
        } else if (preset.is_from_bundle()) {
            bundle_presets.emplace(preset.name, ComboEntry{shown.label(false), {}, {}, bundle_name(bundle, preset)});
        } else {
            user_presets.emplace(preset.name, ComboEntry{shown.label(true)});
        }
    }

    std::vector<PresetItem> items;
    const auto in_order = [](const ComboEntries& entries) {
        std::vector<ComboEntries::const_iterator> list;
        for (auto entry = entries.begin(); entry != entries.end(); ++entry) {
            list.push_back(entry);
        }
        return list;
    };
    const auto no_subgroup = [](const ComboEntry&) { return std::string(); };
    append_items(items, in_order(user_presets), PresetGroup::user, selected, no_subgroup);
    append_items(items, in_order(bundle_presets), PresetGroup::bundle, selected, [](const ComboEntry& entry) { return entry.bundle; });
    append_items(items, in_order(system_presets), PresetGroup::system, selected, no_subgroup);
    return items;
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

// Tab::select_preset(): the changes of a printer are never moved, and neither
// are the ones of a filament of another type.
bool may_transfer(Slic3r::PresetBundle& bundle, const PresetChoice choice, const std::string& value)
{
    // printer_tab: no_transfer = true.
    if (choice == PresetChoice::printer || choice == PresetChoice::printer_model || choice == PresetChoice::nozzle_diameter) {
        return false;
    }
    if (choice != PresetChoice::filament) {
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
        // Tab::select_preset(): a preset with unsaved changes is not left
        // behind before the user says what happens to them.
        const PresetKind kind = choice_kind(choice);
        if (preset_collection(bundle, kind).current_is_dirty()) {
            if (action == PresetChangeAction::ask) {
                PresetState result = preset_state(bundle);
                result.asks_unsaved_changes = true;
                result.changed_kind = kind;
                result.unsaved_changes = detail::preset_changes(kind);
                result.can_transfer = may_transfer(bundle, choice, value);
                result.save_name = detail::save_preset_name(preset_collection(bundle, kind).get_selected_preset(), result.save_name_copy_suffix);
                return result;
            }
            if (action == PresetChangeAction::transfer) {
                detail::cache_preset_changes(kind);
            }
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
            if (bundle.filaments.find_preset(value, false) == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Unknown filament profile: " + value);
            }
            // Plater::priv::on_select_preset(), then Tab::select_preset() of the filament tab.
            bundle.set_filament_preset(0, value);
            bundle.filaments.select_preset_by_name(value, false);
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

PresetState add_filament()
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *engine().bundle;
        follow_config(engine());
        // Sidebar::add_filament(): the desktop app prints with at most 16.
        constexpr std::size_t maximum = 16;
        if (bundle.filament_presets.size() >= maximum) {
            return preset_failure(SceneStatus::profile_not_found, "A plate prints with at most 16 filaments");
        }
        const std::size_t count = bundle.filament_presets.size() + 1;
        const std::string color = filament_palette()[(count - 1) % filament_palette().size()];
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
    if (index == 0) {
        // The first filament is the one the tab edits, which asks about
        // unsaved changes as any other preset choice does.
        return select_preset(PresetChoice::filament, name, action);
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
        // PlaterPresetComboBox of that slot (Plater::priv::on_select_preset).
        bundle.set_filament_preset(std::size_t(index), name);
        bundle.update_multi_material_filament_presets();
        bundle.export_selections(*engine().config);
        save_config(engine());
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
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

PresetState apply_setup(const std::vector<std::string>& models, const std::vector<std::string>& filaments)
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
        const setup::Catalog& data = setup_catalog();
        VendorMap vendors;
        for (const std::string& model_id : models) {
            const setup::Model* model = find_setup_model(data, model_id);
            if (model == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Unknown printer model: " + model_id);
            }
            vendors[model->vendor][model->model].insert(model->nozzle_diameters.begin(), model->nozzle_diameters.end());
        }
        std::map<std::string, std::string> enabled_filaments;
        for (const std::string& filament : filaments) {
            enabled_filaments[filament] = "true";
        }
        engine().config->set("firstguide", "finish", "1");

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
        save_config(engine());
        engine().config_existed = true;
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
