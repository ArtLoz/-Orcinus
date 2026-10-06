#include "tab_print_model.hpp"

// TabPrintModel, TabPrintObject and TabPrintPlate of the desktop app (Tab.cpp):
// the process settings an object or the plate overrides. The tab shows the
// process preset with the overrides applied; a change is written into the
// overrides, which travel with the object and reach the slicer with it.
//
// The desktop app keeps the overrides in the ModelConfig of the object and in
// the one of the PartPlate, and edits every object the object list has
// selected; the app keeps them with the plate it prepares and sends them with
// every request, since the engine holds no plate of its own. A value the
// selected objects disagree on is a null key: the tab shows none for it, and a
// change writes the new value into every selected object.

#include <algorithm>
#include <iterator>
#include <memory>

#include "libslic3r/PrintConfig.hpp"

namespace orcinus::orca::detail {

using namespace Slic3r;

namespace {

// The null_vecs of TabPrintModel::on_value_change(): one nil value of a vector
// setting, which a removed override leaves behind.
const ConfigOptionVectorBase* null_vector_of(const ConfigOptionType type)
{
    static const ConfigOptionBoolsNullable bools{std::initializer_list<unsigned char>{ConfigOptionBoolsNullable::nil_value()}};
    static const ConfigOptionIntsNullable ints(1, ConfigOptionIntsNullable::nil_value());
    static const ConfigOptionFloatsNullable floats(1, ConfigOptionFloatsNullable::nil_value());
    static const ConfigOptionPercentsNullable percents(1, ConfigOptionPercentsNullable::nil_value());
    static const ConfigOptionFloatsOrPercentsNullable floats_or_percents(1, ConfigOptionFloatsOrPercentsNullable::nil_value());
    switch (type) {
    case coBools:
        return &bools;
    case coInts:
        return &ints;
    case coPercents:
        return &percents;
    case coFloatsOrPercents:
        return &floats_or_percents;
    case coFloats:
    default:
        return &floats;
    }
}

// The helpers of Tab.cpp, which keep the vectors of keys sorted.
std::vector<std::string> intersect(const std::vector<std::string>& l, const std::vector<std::string>& r)
{
    std::vector<std::string> t;
    std::copy_if(r.begin(), r.end(), std::back_inserter(t), [&l](const std::string& e) { return std::find(l.begin(), l.end(), e) != l.end(); });
    std::sort(t.begin(), t.end());
    return t;
}

std::vector<std::string> concat(const std::vector<std::string>& l, const std::vector<std::string>& r)
{
    std::vector<std::string> sorted_l = l;
    std::vector<std::string> sorted_r = r;
    std::sort(sorted_l.begin(), sorted_l.end());
    std::sort(sorted_r.begin(), sorted_r.end());
    std::vector<std::string> t;
    std::set_union(sorted_l.begin(), sorted_l.end(), sorted_r.begin(), sorted_r.end(), std::back_inserter(t));
    return t;
}

std::vector<std::string> substruct(const std::vector<std::string>& l, const std::vector<std::string>& r)
{
    std::vector<std::string> t;
    std::copy_if(l.begin(), l.end(), std::back_inserter(t), [&r](const std::string& e) { return std::find(r.begin(), r.end(), e) == r.end(); });
    return t;
}

// variant_keys() of Tab.cpp: the keys of a configuration, with one key per
// value a vector setting holds.
std::vector<std::string> variant_keys(const DynamicPrintConfig& config)
{
    std::vector<std::string> t;
    for (const std::string& opt_key : config.keys()) {
        const ConfigOption* opt = config.option(opt_key);
        if (opt->type() & coVectorType) {
            const auto* vec = dynamic_cast<const ConfigOptionVectorBase*>(opt);
            for (std::size_t i = 0; i < vec->size(); i++) {
                if (!vec->is_nil(i)) {
                    t.push_back(opt_key + "#" + std::to_string(i));
                }
            }
        } else {
            t.push_back(opt_key);
        }
    }
    return t;
}

// add_correct_opts_to_diff() of Preset.cpp, which deep_diff() needs; both are
// file-local upstream.
template<class T>
void add_correct_opts_to_diff(const std::string& opt_key, t_config_option_keys& vec, const ConfigBase& other, const ConfigBase& this_c, bool strict)
{
    const T* opt_init = static_cast<const T*>(other.option(opt_key));
    const T* opt_cur = static_cast<const T*>(this_c.option(opt_key));
    int opt_init_max_id = int(opt_init->values.size()) - 1;
    if (opt_init_max_id < 0) {
        for (int i = 0; i < int(opt_cur->values.size()); i++)
            vec.emplace_back(opt_key + "#" + std::to_string(i));
        return;
    }

    for (int i = 0; i < int(opt_cur->values.size()); i++)
    {
        int init_id = i <= opt_init_max_id ? i : 0;
        if (opt_cur->values[i] != opt_init->values[init_id]) {
            if (opt_cur->nullable()) {
                if (opt_cur->is_nil(i)) {
                    if (strict && !opt_init->is_nil(init_id))
                        vec.emplace_back(opt_key + "#" + std::to_string(i));
                } else {
                    if (strict || !opt_init->is_nil(init_id))
                        vec.emplace_back(opt_key + "#" + std::to_string(i));
                }
            } else {
                vec.emplace_back(opt_key + "#" + std::to_string(i));
            }
        }
    }
}

// deep_diff() of Preset.cpp: the keys two configurations differ on, with one
// key per value of a vector setting. The configurations of objects hold no
// thumbnails or compatible presets, so the keys the desktop app treats apart
// there are left out.
t_config_option_keys deep_diff(const ConfigBase& config_this, const ConfigBase& config_other, bool strict = true)
{
    t_config_option_keys diff;
    t_config_option_keys keys;
    if (strict) {
        t_config_option_keys keys_this = config_this.keys();
        t_config_option_keys keys_other = config_other.keys();
        std::set_union(keys_this.begin(), keys_this.end(), keys_other.begin(), keys_other.end(), std::back_inserter(keys));
    } else {
        keys = config_this.keys();
    }
    for (const t_config_option_key& opt_key : keys) {
        const ConfigOption* this_opt = config_this.option(opt_key);
        const ConfigOption* other_opt = config_other.option(opt_key);
        if (this_opt != nullptr && other_opt != nullptr && *this_opt != *other_opt) {
            switch (other_opt->type()) {
            case coInts:    add_correct_opts_to_diff<ConfigOptionInts       >(opt_key, diff, config_other, config_this, strict);  break;
            case coBools:   add_correct_opts_to_diff<ConfigOptionBools      >(opt_key, diff, config_other, config_this, strict);  break;
            case coFloats:  add_correct_opts_to_diff<ConfigOptionFloats     >(opt_key, diff, config_other, config_this, strict);  break;
            case coStrings: add_correct_opts_to_diff<ConfigOptionStrings    >(opt_key, diff, config_other, config_this, strict);  break;
            case coPercents:add_correct_opts_to_diff<ConfigOptionPercents   >(opt_key, diff, config_other, config_this, strict);  break;
            case coFloatsOrPercents: add_correct_opts_to_diff<ConfigOptionFloatsOrPercents>(opt_key, diff, config_other, config_this, strict); break;
            case coPoints:  add_correct_opts_to_diff<ConfigOptionPoints     >(opt_key, diff, config_other, config_this, strict);  break;
            case coEnums:   add_correct_opts_to_diff<ConfigOptionInts       >(opt_key, diff, config_other, config_this, strict);  break;
            default:        diff.emplace_back(opt_key);     break;
            }
        } else if (strict) {
            const ConfigOption* opt = nullptr;
            if (this_opt != nullptr && other_opt == nullptr)
                opt = this_opt;
            else if (this_opt == nullptr && other_opt != nullptr)
                opt = other_opt;
            if (opt) {
                if (opt->type() & coVectorType) {
                    const auto* vec = dynamic_cast<const ConfigOptionVectorBase*>(opt);
                    for (std::size_t i = 0; i < vec->size(); i++)
                        diff.push_back(opt_key + "#" + std::to_string(i));
                } else {
                    diff.push_back(opt_key);
                }
            }
        }
    }
    return diff;
}

}  // namespace

const std::vector<std::string>& plate_setting_keys()
{
    // plate_keys of Tab.cpp
    static const std::vector<std::string> keys = {"curr_bed_type",
                                                  "skirt_start_angle",
                                                  "first_layer_print_sequence",
                                                  "first_layer_sequence_choice",
                                                  "other_layers_print_sequence",
                                                  "other_layers_sequence_choice",
                                                  "print_sequence",
                                                  "spiral_mode"};
    return keys;
}

// -----------------------------------------------------------------------------
// TabPrintModel
// -----------------------------------------------------------------------------

TabPrintModel::TabPrintModel(const Preset::Type type, std::vector<std::string> keys, PresetBundle& bundle, AppConfig& app_config, TabState& state,
                             SettingsDialogs& dialogs)
    : TabPrint(type, bundle, app_config, state, dialogs),
      m_keys(intersect(Preset::print_options(), keys)),
      m_prints(Preset::TYPE_MODEL, Preset::print_options(), static_cast<const PrintRegionConfig&>(FullPrintConfig::defaults()))
{
    m_opt_status_value = osInitValue | osSystemValue;
    m_is_default_preset = true;
}

void TabPrintModel::build()
{
    m_presets = &m_prints;
    TabPrint::build();
    init_options_list();

    PageShp page = add_options_page("Frequent", "empty");
        ConfigOptionsGroupShp optgroup = page->new_optgroup("");
            optgroup->append_single_option_line("layer_height", "quality_settings_layer_height");
            optgroup->append_single_option_line("sparse_infill_density", "strength_settings_infill#sparse-infill-density");
            optgroup->append_single_option_line("wall_loops", "strength_settings_walls");
            optgroup->append_single_option_line("enable_support", "support_settings_support");
    m_pages.pop_back();
    m_pages.insert(m_pages.begin(), page);

    for (const PageShp& p : m_pages) {
        for (const ConfigOptionsGroupShp& g : p->m_optgroups) {
            g->remove_option_if([this](const std::string& key) { return !has_key(key); });
            g->have_sys_config = [this] { m_back_to_sys = true; return true; };
            // The value an override goes back to is the one of the process
            // preset, which the desktop app reaches through m_parent_tab.
            g->m_get_sys_config = [this] { return parent_config(); };
        }
        p->m_optgroups.erase(std::remove_if(p->m_optgroups.begin(), p->m_optgroups.end(),
                                            [](const ConfigOptionsGroupShp& g) { return g->get_lines().empty(); }),
                             p->m_optgroups.end());
    }
    m_pages.erase(std::remove_if(m_pages.begin(), m_pages.end(), [](const PageShp& p) { return p->m_optgroups.empty(); }), m_pages.end());
}

void TabPrintModel::set_model_config(std::vector<DynamicPrintConfig> model_configs, const DynamicPrintConfig& plate_config)
{
    m_object_configs = std::move(model_configs);
    if (m_type == Preset::TYPE_PLATE && m_object_configs.empty()) {
        // The desktop app always has the one PartPlate of the tab; a request
        // that carries nothing edits the plate the app keeps.
        m_object_configs.push_back(plate_config);
    }
    m_plate_config = plate_config;
    m_prints.get_selected_preset().config.apply(parent_config());
    update_model_config();
}

void TabPrintModel::update_model_config()
{
    if (m_config_manipulation.is_applying()) {
        return;
    }
    m_config->apply(parent_config());
    if (m_type != Preset::TYPE_PLATE) {
        m_config->apply_only(m_plate_config, plate_setting_keys(), true);
    }
    m_null_keys.clear();
    if (!m_object_configs.empty()) {
        const DynamicPrintConfig& global_config = *m_config;
        const DynamicPrintConfig& local_config = m_object_configs.front();
        DynamicPrintConfig diff_config;
        std::vector<std::string> all_keys = variant_keys(local_config);  // at least one has these keys
        std::vector<std::string> local_diffs;                            // all diff keys to first config
        if (m_object_configs.size() > 1) {
            std::vector<std::string> global_diffs;                       // all diff keys to global config
            for (const DynamicPrintConfig& config : m_object_configs) {
                all_keys = concat(all_keys, variant_keys(config));
                const std::vector<std::string> diffs = deep_diff(config, global_config, false);
                global_diffs = concat(global_diffs, diffs);
                diff_config.apply_only(config, diffs, true);
                if (&config == &local_config) continue;
                local_diffs = concat(local_diffs, deep_diff(local_config, config));
            }
            m_null_keys = intersect(global_diffs, local_diffs);
            m_config->apply(diff_config);
        }
        m_all_keys.clear();
        std::copy_if(all_keys.begin(), all_keys.end(), std::back_inserter(m_all_keys), [this](const std::string& e) {
            auto iter = std::lower_bound(m_keys.begin(), m_keys.end(), e);
            if (const auto n = e.find('#'); n == std::string::npos)
                return iter != m_keys.end() && e == *iter;
            else
                return iter != m_keys.begin() && e.compare(0, n, *--iter) == 0;
        });
        // except those than all equal on
        std::vector<std::string> local_keys = substruct(m_all_keys, local_diffs);
        auto iter = std::partition(local_keys.begin(), local_keys.end(), [](const std::string& e) { return e.find('#') == std::string::npos; });
        m_config->apply_only(local_config, std::vector<std::string>(local_keys.begin(), iter), true);
        for (; iter != local_keys.end(); ++iter) {
            int n = int(iter->find('#'));
            const std::string opt_key = iter->substr(0, std::size_t(n));
            n = std::atoi(iter->c_str() + n + 1);
            auto* vec = dynamic_cast<ConfigOptionVectorBase*>(m_config->option(opt_key));
            if (vec != nullptr && local_config.option(opt_key) != nullptr) {
                vec->set_at(local_config.option(opt_key), std::size_t(n), std::size_t(n));
            }
        }
        m_config_manipulation.apply_null_fff_config(m_config, m_null_keys, m_object_configs);
    }

    if (m_type == Preset::TYPE_PLATE) {
        // The plate settings the process preset knows nothing about: the bed
        // type of the project, and whether the layer sequences are customized.
        const DynamicPrintConfig& plate = m_object_configs.empty() ? m_plate_config : m_object_configs.front();
        if (!plate.has("curr_bed_type")) {
            const DynamicConfig& global_config = m_preset_bundle->project_config;
            if (global_config.has("curr_bed_type")) {
                m_config->set_key_value("curr_bed_type", new ConfigOptionEnum<BedType>(global_config.opt_enum<BedType>("curr_bed_type")));
            }
        }
        const bool first_layer_customized = plate.has("first_layer_print_sequence");
        m_config->set_key_value("first_layer_sequence_choice", new ConfigOptionEnum<LayerSeq>(first_layer_customized ? flsCustomize : flsAuto));
        if (first_layer_customized) {
            std::replace(m_all_keys.begin(), m_all_keys.end(), std::string("first_layer_print_sequence"), std::string("first_layer_sequence_choice"));
        }
        const bool other_layers_customized = plate.has("other_layers_print_sequence");
        m_config->set_key_value("other_layers_sequence_choice", new ConfigOptionEnum<LayerSeq>(other_layers_customized ? flsCustomize : flsAuto));
        if (other_layers_customized) {
            std::replace(m_all_keys.begin(), m_all_keys.end(), std::string("other_layers_print_sequence"), std::string("other_layers_sequence_choice"));
        }
    }

    toggle_options();
    m_toggled = true;
    update_dirty();
    TabPrint::reload_config();
}

void TabPrintModel::reset_model_config()
{
    for (DynamicPrintConfig& config : m_object_configs) {
        for (const std::string& key : intersect(m_keys, config.keys())) {
            config.erase(key);
        }
    }
    update_model_config();
}

bool TabPrintModel::has_key(const std::string& key) const
{
    return std::find(m_keys.begin(), m_keys.end(), key) != m_keys.end();
}

void TabPrintModel::back_to_initial_value(const std::string& opt_id)
{
    // The desktop app shows two arrows on a line of an object: one back to the
    // value of the process preset, which removes the override, and one back to
    // the value the object had. The app has one undo, which removes it.
    for (const PageShp& page : m_pages) {
        for (const ConfigOptionsGroupShp& group : page->m_optgroups) {
            if (group->has_option(opt_id) || group->opt_map().count(opt_id) > 0) {
                group->back_to_sys_value(opt_id);
                return;
            }
        }
    }
}

void TabPrintModel::on_roll_back_value(const bool to_sys)
{
    // The Reset Options button of the parameter panel (TabPrintModel::reset_model_config).
    reset_model_config();
}

void TabPrintModel::on_value_change(const std::string& opt_id, const boost::any& value)
{
    if (m_config_manipulation.is_applying()) {
        TabPrint::on_value_change(opt_id, value);
        return;
    }
    std::string opt_key = opt_id;
    std::string opt_id2 = opt_id;
    int opt_index = -1;
    if (const auto n = opt_key.find('#'); n != std::string::npos) {
        opt_key = opt_key.substr(0, n);
        opt_index = std::atoi(opt_id.c_str() + n + 1);
    }
    if (!has_key(opt_key)) {
        return;
    }
    const auto inull = std::find(m_null_keys.begin(), m_null_keys.end(), opt_id2);
    // always add object config
    auto* tab_opt = dynamic_cast<ConfigOptionVectorBase*>(m_config->option(opt_key));
    if (m_back_to_sys) {
        for (DynamicPrintConfig& config : m_object_configs) {
            if (opt_key == opt_id || tab_opt == nullptr) {
                config.erase(opt_key);
                continue;
            }
            // One value of a vector setting: the override keeps the others.
            const ConfigOption* opt = config.option(opt_key);
            if (opt == nullptr) {
                continue;
            }
            std::unique_ptr<ConfigOption> opt2(opt->clone());
            auto* vec = dynamic_cast<ConfigOptionVectorBase*>(opt2.get());
            if (vec == nullptr) {
                config.erase(opt_key);
                continue;
            }
            vec->resize(tab_opt->size());
            vec->set_at(null_vector_of(tab_opt->type()), std::size_t(opt_index), 0);
            if (opt2->is_nil()) {
                config.erase(opt_key);
            } else {
                config.set_key_value(opt_key, opt2.release());
            }
        }
        m_all_keys.erase(std::remove(m_all_keys.begin(), m_all_keys.end(), opt_id2), m_all_keys.end());
    } else {
        for (DynamicPrintConfig& config : m_object_configs) {
            if (opt_key == opt_id || tab_opt == nullptr) {
                config.apply_only(*m_config, {opt_key}, true);
                continue;
            }
            const ConfigOption* opt = config.option(opt_key);
            std::unique_ptr<ConfigOption> opt2(opt != nullptr ? opt->clone() : null_vector_of(tab_opt->type())->clone());
            auto* vec = dynamic_cast<ConfigOptionVectorBase*>(opt2.get());
            if (vec == nullptr) {
                config.apply_only(*m_config, {opt_key}, true);
                continue;
            }
            vec->resize(tab_opt->size());
            vec->set_at(tab_opt, std::size_t(opt_index), std::size_t(opt_index));
            config.set_key_value(opt_key, opt2.release());
        }
        m_all_keys = concat(m_all_keys, {opt_id2});
    }
    if (inull != m_null_keys.end()) {
        m_null_keys.erase(inull);
    }
    update_changed_ui();
    m_back_to_sys = false;
    TabPrint::on_value_change(opt_id, value);
}

bool TabPrintModel::shows_no_value(const std::string& opt_id) const
{
    return std::find(m_null_keys.begin(), m_null_keys.end(), opt_id) != m_null_keys.end();
}

void TabPrintModel::reload_config()
{
    TabPrint::reload_config();
    // A correction of ConfigManipulation changed values of the tab: the ones
    // the object may override become overrides, the others go to the process
    // preset, as the desktop app hands them to the parent tab.
    bool parent_changed = false;
    for (const std::string& key : m_config_manipulation.applying_keys()) {
        if (has_key(key)) {
            const auto inull = std::find(m_null_keys.begin(), m_null_keys.end(), key);
            const bool set = *m_config->option(key) != *m_prints.get_selected_preset().config.option(key) || inull != m_null_keys.end();
            if (set) {
                for (DynamicPrintConfig& config : m_object_configs) {
                    config.apply_only(*m_config, {key}, true);
                }
                m_all_keys = concat(m_all_keys, {key});
            }
            if (inull != m_null_keys.end()) {
                m_null_keys.erase(inull);
            }
        } else if (m_config->has(key)) {
            parent_config().apply_only(*m_config, {key}, true);
            parent_changed = true;
        }
    }
    if (parent_changed) {
        m_preset_bundle->prints.update_dirty();
    }
}

void TabPrintModel::update_custom_dirty(std::vector<std::string>& dirty_options, std::vector<std::string>& nonsys_options)
{
    dirty_options = concat(dirty_options, m_null_keys);
    nonsys_options = concat(nonsys_options, m_null_keys);
    nonsys_options = concat(nonsys_options, m_all_keys);
}

PresetSettings TabPrintModel::describe()
{
    PresetSettings result = TabPrint::describe();
    result.has_model_settings = true;
    for (const DynamicPrintConfig& config : m_object_configs) {
        ModelSettings& settings = result.model_settings.emplace_back();
        for (const std::string& key : config.keys()) {
            settings.keys.push_back(key);
            settings.values.push_back(config.opt_serialize(key));
        }
    }
    // The settings the selected objects disagree on, which the tab shows no
    // value for (TabPrintModel::activate_selected_page).
    std::vector<std::string> null_keys = m_null_keys;
    filter_diff_option(null_keys);
    for (SettingState& state : result.settings) {
        if (std::find(null_keys.begin(), null_keys.end(), state.id) != null_keys.end()) {
            state.mixed = true;
            state.value.clear();
        }
    }
    return result;
}

// -----------------------------------------------------------------------------
// TabPrintObject
// -----------------------------------------------------------------------------

TabPrintObject::TabPrintObject(PresetBundle& bundle, AppConfig& app_config, TabState& state, SettingsDialogs& dialogs)
    : TabPrintModel(Preset::TYPE_MODEL, concat(PrintObjectConfig().keys(), PrintRegionConfig().keys()), bundle, app_config, state, dialogs)
{
}

// -----------------------------------------------------------------------------
// TabPrintPart
// -----------------------------------------------------------------------------

TabPrintPart::TabPrintPart(PresetBundle& bundle, AppConfig& app_config, TabState& state, SettingsDialogs& dialogs)
    : TabPrintModel(Preset::TYPE_MODEL, PrintRegionConfig().keys(), bundle, app_config, state, dialogs)
{
    m_parent_config = m_preset_bundle->prints.get_edited_preset().config;
}

void TabPrintPart::set_parent_config(const DynamicPrintConfig& object_config)
{
    m_parent_config = m_preset_bundle->prints.get_edited_preset().config;
    m_parent_config.apply(object_config);
}

PresetSettings TabPrintPart::describe()
{
    PresetSettings result = TabPrintModel::describe();
    // The desktop app has the one model tab for an object and for its parts
    // (Preset::TYPE_MODEL); the app asks for the settings of a part by itself.
    result.kind = PresetKind::part;
    return result;
}

// -----------------------------------------------------------------------------
// TabPrintLayer
// -----------------------------------------------------------------------------

namespace {

// TabPrintLayer of Tab.cpp keeps the key in a file-local variable of its own.
const std::string layer_height_key = "layer_height";

}  // namespace

TabPrintLayer::TabPrintLayer(PresetBundle& bundle, AppConfig& app_config, TabState& state, SettingsDialogs& dialogs)
    : TabPrintModel(Preset::TYPE_MODEL, concat({layer_height_key}, PrintRegionConfig().keys()), bundle, app_config, state, dialogs)
{
    m_parent_config = m_preset_bundle->prints.get_edited_preset().config;
}

void TabPrintLayer::set_parent_config(const DynamicPrintConfig& object_config)
{
    m_parent_config = m_preset_bundle->prints.get_edited_preset().config;
    m_parent_config.apply(object_config);
}

void TabPrintLayer::set_model_config(std::vector<DynamicPrintConfig> model_configs, const DynamicPrintConfig& plate_config)
{
    TabPrintModel::set_model_config(std::move(model_configs), plate_config);
    // TabPrintLayer::notify_changed(): a range always has a layer height of its
    // own, which is what a range is for; a range that carries none takes the
    // one of the object.
    bool added = false;
    for (DynamicPrintConfig& config : m_object_configs) {
        if (!config.has(layer_height_key)) {
            config.set_key_value(layer_height_key, parent_config().option(layer_height_key)->clone());
            added = true;
        }
    }
    if (added) {
        m_all_keys = concat(m_all_keys, {layer_height_key});
        update_model_config();
    }
}

void TabPrintLayer::update_custom_dirty(std::vector<std::string>& dirty_options, std::vector<std::string>& nonsys_options)
{
    TabPrintModel::update_custom_dirty(dirty_options, nonsys_options);
    // A range always prints with a layer height of its own: one that lost it to
    // a reset takes the object's back. A range whose layer height is the one of
    // the object changes nothing, so the desktop app does not mark it.
    const ConfigOption* option = parent_config().option(layer_height_key);
    const auto erase = [](std::vector<std::string>& keys) {
        keys.erase(std::remove(keys.begin(), keys.end(), layer_height_key), keys.end());
    };
    for (DynamicPrintConfig& config : m_object_configs) {
        if (!config.has(layer_height_key)) {
            config.set_key_value(layer_height_key, option->clone());
            erase(dirty_options);
            erase(nonsys_options);
        } else if (config.opt_float(layer_height_key) == option->getFloat()) {
            erase(dirty_options);
            erase(nonsys_options);
        }
    }
}

PresetSettings TabPrintLayer::describe()
{
    PresetSettings result = TabPrintModel::describe();
    // The desktop app has the one model tab for an object, its parts and its
    // height ranges (Preset::TYPE_MODEL); the app asks for a range by itself.
    result.kind = PresetKind::layer;
    return result;
}

// -----------------------------------------------------------------------------
// TabPrintPlate
// -----------------------------------------------------------------------------

TabPrintPlate::TabPrintPlate(PresetBundle& bundle, AppConfig& app_config, TabState& state, SettingsDialogs& dialogs)
    : TabPrintModel(Preset::TYPE_PLATE, plate_setting_keys(), bundle, app_config, state, dialogs)
{
    m_keys = concat(m_keys, plate_setting_keys());
}

void TabPrintPlate::build()
{
    m_presets = &m_prints;
    load_initial_data();

    // The settings of the plate the process preset does not hold. The desktop
    // app shifts the bed type by one, since its combo box lists the types
    // without the default one and picks them by their place in that list; the
    // app names a type by its key, which needs no shift.
    m_config->option("curr_bed_type", true);
    if (m_preset_bundle->project_config.has("curr_bed_type")) {
        m_config->set_key_value("curr_bed_type", new ConfigOptionEnum<BedType>(m_preset_bundle->project_config.opt_enum<BedType>("curr_bed_type")));
    }
    m_config->option("first_layer_sequence_choice", true);
    m_config->option("first_layer_print_sequence", true);
    m_config->option("other_layers_print_sequence", true);
    m_config->option("other_layers_sequence_choice", true);

    PageShp page = add_options_page("Plate Settings", "empty");
    ConfigOptionsGroupShp optgroup = page->new_optgroup("");
    optgroup->append_single_option_line("curr_bed_type");
    optgroup->append_single_option_line("skirt_start_angle");
    optgroup->append_single_option_line("print_sequence");
    optgroup->append_single_option_line("spiral_mode");
    optgroup->append_single_option_line("first_layer_sequence_choice");
    optgroup->append_single_option_line("other_layers_sequence_choice");

    for (Line& line : optgroup->get_lines()) {
        line.undo_to_sys = true;
    }
    optgroup->have_sys_config = [this] { m_back_to_sys = true; return true; };
    optgroup->m_get_sys_config = [this] { return parent_config(); };
}

void TabPrintPlate::on_value_change(const std::string& opt_key, const boost::any& value)
{
    if (m_config_manipulation.is_applying()) {
        return;
    }
    if (!has_key(opt_key)) {
        return;
    }
    if (m_object_configs.empty()) {
        m_object_configs.emplace_back();
    }
    DynamicPrintConfig& plate = m_object_configs.front();
    if (m_back_to_sys) {
        plate.erase(opt_key);
        if (opt_key == "first_layer_sequence_choice") {
            plate.erase("first_layer_print_sequence");
        }
        if (opt_key == "other_layers_sequence_choice") {
            plate.erase("other_layers_print_sequence");
            plate.erase("other_layers_print_sequence_nums");
        }
        m_all_keys.erase(std::remove(m_all_keys.begin(), m_all_keys.end(), opt_key), m_all_keys.end());
    } else {
        plate.apply_only(*m_config, {opt_key}, true);
        // The order the layers print in is a sequence per filament: a
        // customized sequence starts as the filaments' order, and the app then
        // opens the plate settings dialog with the sequences alone
        // (EVT_OPEN_PLATESETTINGSDIALOG with "only_layer_sequence").
        if (opt_key == "first_layer_sequence_choice") {
            if (m_config->opt_enum<LayerSeq>("first_layer_sequence_choice") == flsAuto) {
                plate.erase("first_layer_print_sequence");
            } else if (!plate.has("first_layer_print_sequence")) {
                plate.set_key_value("first_layer_print_sequence", new ConfigOptionInts(default_layer_sequence()));
            }
        }
        if (opt_key == "other_layers_sequence_choice") {
            if (m_config->opt_enum<LayerSeq>("other_layers_sequence_choice") == flsAuto) {
                plate.erase("other_layers_print_sequence");
                plate.erase("other_layers_print_sequence_nums");
            } else if (!plate.has("other_layers_print_sequence")) {
                // The layers from the second on, as get_other_layers_print_sequence()
                // writes a sequence: the first and the last layer, then the filaments.
                std::vector<int> sequence = {2, INT_MAX};
                const std::vector<int> filaments = default_layer_sequence();
                sequence.insert(sequence.end(), filaments.begin(), filaments.end());
                plate.set_key_value("other_layers_print_sequence", new ConfigOptionInts(sequence));
                plate.set_key_value("other_layers_print_sequence_nums", new ConfigOptionInt(1));
            }
        }
        m_all_keys = concat(m_all_keys, {opt_key});
    }
    update_changed_ui();
    m_back_to_sys = false;
    update();
}

std::vector<int> TabPrintPlate::default_layer_sequence() const
{
    // The filaments in their order (TabPrintPlate::on_value_change).
    std::vector<int> sequence;
    for (std::size_t i = 0; i < m_preset_bundle->filament_presets.size(); ++i) {
        sequence.push_back(int(i) + 1);
    }
    if (sequence.empty()) {
        sequence.push_back(1);
    }
    return sequence;
}

void TabPrintPlate::update_custom_dirty(std::vector<std::string>& dirty_options, std::vector<std::string>& nonsys_options)
{
    TabPrintModel::update_custom_dirty(dirty_options, nonsys_options);
    std::vector<std::string> dirty;
    for (const std::string& key : m_all_keys) {
        if (key == "first_layer_sequence_choice" || key == "other_layers_sequence_choice") {
            if (m_config->opt_enum<LayerSeq>("first_layer_sequence_choice") != flsAuto) {
                dirty.push_back(key);
            }
            if (m_config->opt_enum<LayerSeq>("other_layers_sequence_choice") != flsAuto) {
                dirty.push_back(key);
            }
        }
        if (key == "curr_bed_type") {
            const DynamicConfig& global_config = m_preset_bundle->project_config;
            if (global_config.has("curr_bed_type") && m_config->opt_enum<BedType>("curr_bed_type") != global_config.opt_enum<BedType>("curr_bed_type")) {
                dirty.push_back(key);
            }
        }
    }
    dirty_options = concat(dirty_options, dirty);
}

}  // namespace orcinus::orca::detail
