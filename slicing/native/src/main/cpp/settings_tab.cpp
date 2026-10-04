#include "settings_tab.hpp"

#include <algorithm>
#include <array>
#include <cmath>
#include <stdexcept>

#include <boost/algorithm/string/predicate.hpp>
#include <boost/format.hpp>
#include <boost/log/trivial.hpp>

#include "engine_context.hpp"
#include "libslic3r/GCode/GCodeProcessor.hpp"
#include "settings_fields.hpp"

namespace orcinus::orca::detail {

using namespace Slic3r;

// GUI_App::get_mode()
ConfigOptionMode app_mode(const AppConfig& app_config)
{
    if (app_config.get_bool("developer_mode")) {
        return comDevelop;
    }
    if (!app_config.has("user_mode")) {
        return comSimple;
    }
    // saved_mode_from_string()
    const std::string mode = app_config.get("user_mode");
    return mode == "expert" ? comExpert : mode == "advanced" || mode == "develop" ? comAdvanced : comSimple;
}

namespace {

// Tab::translate_category(): the name the tab shows for a page.
std::vector<UiText> translate_category(const std::string& title, const Preset::Type type, PresetBundle& bundle)
{
    if (type == Preset::TYPE_PRINTER && title.find("Extruder ") != std::string::npos) {
        if (bundle.is_bbl_vendor()) {
            if (title == "Extruder 1") {
                return {ui_text("Left Extruder")};
            }
            if (title == "Extruder 2") {
                return {ui_text("Right Extruder")};
            }
        }
        return {ui_text("Extruder"), ui_text(title.substr(8))};
    }
    return {ui_text(title)};
}

// The settings whose values the widget of their line edits.
bool is_list_option(const std::string& opt_key)
{
    return opt_key == "compatible_printers" || opt_key == "compatible_prints";
}

}  // namespace

// The first filament's type is what the objects print with: Android plates
// print every object with the first filament (has_filaments() of GUI_App.cpp).
bool has_filaments(const PresetBundle& bundle, const std::vector<std::string>& model_filaments)
{
    if (bundle.filament_presets.empty()) {
        return false;
    }
    const DynamicPrintConfig& config = bundle.full_config();
    const std::string type = config.has("filament_type") ? config.opt_string("filament_type", 0u) : std::string("PLA");
    return std::find(model_filaments.begin(), model_filaments.end(), type) != model_filaments.end();
}

// is_support_filament() of GUI_App.cpp
bool is_support_filament(const PresetBundle& bundle, const int extruder_id, const bool strict_check)
{
    auto& filament_presets = bundle.filament_presets;
    auto& filaments = bundle.filaments;

    if (extruder_id < 0 || extruder_id >= int(filament_presets.size())) return false;

    const Preset* filament = filaments.find_preset(filament_presets[extruder_id]);
    if (filament == nullptr) return false;

    std::string filament_type = filament->config.option<ConfigOptionStrings>("filament_type")->values[0];

    const ConfigOptionBools* support_option = dynamic_cast<const ConfigOptionBools*>(filament->config.option("filament_is_support"));

    if (!strict_check && (filament_type == "PETG" || filament_type == "PLA")) {
        std::vector<std::string> model_filaments;
        if (filament_type == "PETG")
            model_filaments.emplace_back("PLA");
        else {
            model_filaments = {"PETG", "TPU", "TPU-AMS"};
        }
        if (has_filaments(bundle, model_filaments)) return true;
    }
    if (support_option == nullptr) return false;
    return support_option->get_at(0);
}

// is_soluble_filament() of GUI_App.cpp
bool is_soluble_filament(const PresetBundle& bundle, const int extruder_id)
{
    auto& filament_presets = bundle.filament_presets;
    auto& filaments = bundle.filaments;

    if (extruder_id < 0 || extruder_id >= int(filament_presets.size())) return false;

    const Preset* filament = filaments.find_preset(filament_presets[extruder_id]);
    if (filament == nullptr) return false;

    const ConfigOptionBools* support_option = dynamic_cast<const ConfigOptionBools*>(filament->config.option("filament_soluble"));
    if (support_option == nullptr) return false;

    return support_option->get_at(0);
}

// -----------------------------------------------------------------------------
// ConfigOptionsGroup
// -----------------------------------------------------------------------------

void ConfigOptionsGroup::append_line(const Line& line)
{
    m_lines.emplace_back(line);

    if (line.full_width && line.widget != SettingWidget::none) {
        return;
    }

    const std::vector<Option>& option_set = line.get_options();
    for (const Option& opt : option_set) {
        m_options.emplace(opt.opt_id, opt);
    }

    // add mode value for current line to m_options_mode
    if (!option_set.empty()) {
        m_options_mode.push_back(option_set[0].opt.mode);
    }
}

Line* ConfigOptionsGroup::get_line(const std::string& opt_key)
{
    for (Line& line : m_lines) {
        for (const Option& opt : line.get_options()) {
            if (opt.opt_id == opt_key) {
                return &line;
            }
        }
    }
    return nullptr;
}

void ConfigOptionsGroup::append_separator()
{
    m_lines.emplace_back(Line());
}

Line ConfigOptionsGroup::create_single_option_line(const Option& option, const std::string& path) const
{
    // The tooltip of a field is the option's, which the app asks for when it
    // shows one (get_formatted_tooltip_text).
    Line retval{option.opt.label, ""};
    retval.label_path = path;
    retval.append_option(option);
    return retval;
}

Option ConfigOptionsGroup::get_option(const std::string& opt_key, const int opt_index)
{
    if (!m_config->has(opt_key)) {
        BOOST_LOG_TRIVIAL(error) << "No " << opt_key << " in ConfigOptionsGroup config.";
    }

    const std::string opt_id = opt_index == -1 ? opt_key : opt_key + "#" + std::to_string(opt_index);
    m_opt_map.emplace(opt_id, std::make_pair(opt_key, opt_index));

    return Option(*m_config->def()->get(opt_key), opt_id);
}

void ConfigOptionsGroup::on_change_OG(const std::string& opt_id, const boost::any& value)
{
    if (!m_opt_map.empty()) {
        const auto it = m_opt_map.find(opt_id);
        if (it == m_opt_map.end()) {
            if (m_on_change != nullptr) {
                m_on_change(opt_id, value);
            }
            return;
        }

        const std::string& opt_key = it->second.first;
        const int opt_index = it->second.second;
        this->change_opt_value(opt_key, value, opt_index == -1 ? 0 : opt_index);
    }

    if (m_on_change != nullptr) {
        m_on_change(opt_id, value);
    }
}

void ConfigOptionsGroup::back_to_initial_value(const std::string& opt_key)
{
    if (m_get_initial_config == nullptr) {
        return;
    }
    back_to_config_value(m_get_initial_config(), opt_key);
}

void ConfigOptionsGroup::back_to_sys_value(const std::string& opt_key)
{
    if (m_get_sys_config == nullptr) {
        return;
    }
    if (!have_sys_config()) {
        return;
    }
    back_to_config_value(m_get_sys_config(), opt_key);
}

void ConfigOptionsGroup::back_to_config_value(const DynamicPrintConfig& config, const std::string& opt_key)
{
    boost::any value;
    if (opt_key == "extruders_count") {
        const auto* nozzle_diameter = dynamic_cast<const ConfigOptionFloats*>(config.option("nozzle_diameter"));
        value = int(nozzle_diameter->values.size());
    } else if (m_opt_map.find(opt_key) == m_opt_map.end() ||
               // This option don't have corresponded field
               opt_key == "printable_area" || opt_key == "compatible_printers" || opt_key == "compatible_prints" || opt_key == "thumbnails" ||
               opt_key == "bed_custom_texture" || opt_key == "bed_custom_model") {
        // The option has no field to give its value: the preset's own is copied.
        const_cast<DynamicPrintConfig&>(*m_config).apply_only(config, {opt_key});
        if (m_on_change != nullptr) {
            m_on_change(opt_key, config_value(config, opt_key));
        }
        return;
    } else {
        const auto& entry = *m_opt_map.find(opt_key);
        value = field_of_config_value(opt_key, entry.second.first, entry.second.second, config_value(config, entry.second.first, entry.second.second));
    }

    on_change_OG(opt_key, value);
}

// Field::set_value() shows the text of the value, and Field::get_value() gives
// back what the field holds: a number for a number, the enum's index for a
// combo box. The config takes that, not the text.
boost::any ConfigOptionsGroup::field_of_config_value(const std::string& opt_id, const std::string& opt_key, const int opt_index, const boost::any& value)
{
    const std::string* text = boost::any_cast<std::string>(&value);
    if (text == nullptr || m_value_from_text == nullptr) {
        return value;
    }
    const boost::any held = m_value_from_text(opt_id, opt_key, opt_index, *text);
    return held.empty() ? value : held;
}

void ConfigOptionsGroup::remove_option_if(const std::function<bool(const std::string&)>& comp)
{
    for (Line& line : m_lines) {
        std::vector<Option>& options = line.get_options();
        options.erase(std::remove_if(options.begin(), options.end(), [&comp](const Option& o) { return comp(o.opt.opt_key); }), options.end());
        line.undo_to_sys = true;
    }
    for (int i = int(m_lines.size()) - 1; i >= 0; --i) {
        if (m_lines[i].get_options().empty() && size_t(i) < m_options_mode.size()) {
            m_options_mode.erase(m_options_mode.begin() + i);
        }
    }
    m_lines.erase(std::remove_if(m_lines.begin(), m_lines.end(), [](const Line& l) { return !l.is_separator() && l.get_options().empty(); }), m_lines.end());
}

bool ConfigOptionsGroup::is_visible(const ConfigOptionMode mode) const
{
    if (m_options_mode.empty()) {
        return true;
    }
    if (m_options_mode.size() == 1) {
        return m_options_mode[0] <= mode;
    }

    size_t hidden_row_cnt = 0;
    for (const ConfigOptionMode opt_mode : m_options_mode) {
        if (opt_mode > mode) {
            hidden_row_cnt++;
        }
    }
    return hidden_row_cnt != m_options_mode.size();
}

void ConfigOptionsGroup::change_opt_value(const std::string& opt_key, const boost::any& value, const int opt_index)
{
    detail::change_opt_value(const_cast<DynamicPrintConfig&>(*m_config), opt_key, value, opt_index);
}

// BBS
void ExtruderOptionsGroup::on_change_OG(const std::string& opt_id, const boost::any& value)
{
    if (!m_opt_map.empty()) {
        const auto it = m_opt_map.find(opt_id);
        if (it == m_opt_map.end()) {
            if (m_on_change != nullptr) {
                m_on_change(opt_id, value);
            }
            return;
        }

        const std::string& opt_key = it->second.first;
        const int opt_index = it->second.second;
        this->change_opt_value(opt_key, value, opt_index == -1 ? 0 : opt_index);
    }

    if (m_on_change != nullptr) {
        m_on_change(opt_id, value);
    }
}

// -----------------------------------------------------------------------------
// Page
// -----------------------------------------------------------------------------

ConfigOptionsGroupShp Page::new_optgroup(const std::string& title, const std::string& icon, const int noncommon_label_width, const bool is_extruder_og)
{
    ConfigOptionsGroupShp optgroup = is_extruder_og ? std::make_shared<ExtruderOptionsGroup>(title, icon, m_config)
                                                    : std::make_shared<ConfigOptionsGroup>(title, icon, m_config);
    if (noncommon_label_width >= 0) {
        optgroup->label_width = size_t(noncommon_label_width);
    }

    Tab* tab = m_tab;
    optgroup->m_on_change = [tab](const std::string& opt_key, const boost::any& value) {
        tab->update_dirty();
        tab->on_value_change(opt_key, value);
    };

    optgroup->m_value_from_text = [tab](const std::string& opt_id, const std::string& opt_key, const int opt_index, const std::string& text) {
        return tab->value_from_text(opt_id, opt_key, opt_index, text);
    };

    optgroup->m_get_initial_config = [tab]() { return tab->m_presets->get_selected_preset().config; };

    optgroup->m_get_sys_config = [tab]() { return tab->m_presets->get_selected_preset_parent()->config; };

    optgroup->have_sys_config = [tab]() { return tab->m_presets->get_selected_preset_parent() != nullptr; };

    m_optgroups.push_back(optgroup);

    return optgroup;
}

ConfigOptionsGroupShp Page::get_optgroup(const std::string& title) const
{
    for (const ConfigOptionsGroupShp& optgroup : m_optgroups) {
        if (optgroup->title == title) {
            return optgroup;
        }
    }
    return nullptr;
}

std::string Page::get_field(const std::string& opt_key, const int opt_index) const
{
    std::string opt_key2 = opt_key;
    if (opt_index >= 256) {
        const auto iter = m_opt_id_map.find(opt_key + '#' + std::to_string(opt_index - 256));
        if (iter != m_opt_id_map.end()) {
            opt_key2 = iter->second;
        }
    }
    for (const ConfigOptionsGroupShp& group : m_optgroups) {
        // ConfigOptionsGroup::get_fieldc()
        if (group->has_option(opt_key2)) {
            return opt_key2;
        }
        const std::string indexed = opt_key2 + '#' + std::to_string(opt_index);
        if (group->has_option(indexed)) {
            return indexed;
        }
    }
    return {};
}

Line* Page::get_line(const std::string& opt_key, const int opt_index)
{
    std::string opt_key2 = opt_key;
    if (opt_index >= 256) {
        const auto iter = m_opt_id_map.find(opt_key + '#' + std::to_string(opt_index - 256));
        if (iter != m_opt_id_map.end()) {
            opt_key2 = iter->second;
        }
    } else if (opt_index >= 0) {
        opt_key2 = opt_key + '#' + std::to_string(opt_index);
    }
    for (const ConfigOptionsGroupShp& group : m_optgroups) {
        if (Line* line = group->get_line(opt_key2)) {
            return line;
        }
    }
    return nullptr;
}

// -----------------------------------------------------------------------------
// Tab
// -----------------------------------------------------------------------------

Tab::Tab(const Preset::Type type, PresetBundle& bundle, AppConfig& app_config, TabState& state, SettingsDialogs& dialogs)
    : m_preset_bundle(&bundle),
      m_type(type),
      m_app_config(app_config),
      m_state(state),
      m_dialogs(dialogs),
      m_mode(app_mode(app_config)),
      m_plater_config(bundle.full_config()),
      m_config_manipulation(
          state.manipulation, bundle, m_plater_config, dialogs,
          [this]() {
              update_dirty();
              // Initialize UI components with the config values.
              reload_config();
              update();
          },
          [this](const std::string& opt_key, const bool toggle, const int opt_index) { toggle_option(opt_key, toggle, opt_index); },
          [this](const std::string& opt_key, const bool toggle, const int opt_index) { toggle_line(opt_key, toggle, opt_index); })
{
}

void Tab::load_initial_data()
{
    m_config = &m_presets->get_edited_preset().config;
    m_is_default_preset = false;
}

PageShp Tab::add_options_page(const std::string& title, const std::string& icon, const bool is_extruder_pages)
{
    PageShp page = std::make_shared<Page>(title, icon, this, m_config);
    if (!is_extruder_pages) {
        m_pages.push_back(page);
    }
    return page;
}

void Tab::load_selection()
{
    const std::string preset = m_presets->get_selected_preset_name();
    const std::string printer = m_preset_bundle->printers.get_selected_preset_name();
    const bool loaded = m_state.loaded_preset == preset && m_state.loaded_printer == printer;

    // Tab::select_preset(): what a preset change moved to this preset.
    apply_config_from_cache();

    // Tab::load_current_preset()
    if (!loaded) {
        update();
        if (m_type == Preset::TYPE_PRINTER) {
            on_preset_loaded();
        }
        update_extruder_variants();
        reload_config();
    } else {
        update_extruder_variants();
    }

    // update_ui_items_related_on_parent_preset()
    const Preset* parent = m_presets->get_selected_preset_parent();
    m_is_default_preset = parent != nullptr && parent->is_default;

    m_opt_status_value = (parent != nullptr ? osSystemValue : 0) | osInitValue;
    init_options_list();
    update_changed_ui();

    m_state.loaded_preset = preset;
    m_state.loaded_printer = printer;
}

void Tab::activate_page(const std::string& page)
{
    const std::string wanted = page.empty() ? m_state.active_page : page;
    m_active_page = nullptr;
    for (const PageShp& candidate : m_pages) {
        if (candidate->title() == wanted) {
            m_active_page = candidate.get();
            break;
        }
    }
    if (m_active_page == nullptr && !m_pages.empty()) {
        m_active_page = m_pages.front().get();
    }
    if (m_active_page == nullptr) {
        return;
    }
    m_state.active_page = m_active_page->title();

    // Tab::activate_selected_page()
    update_changed_ui();
    if (m_active_page->title() != "Dependencies") {
        toggle_options();
        m_toggled = true;
    }
}

void Tab::change_field(const std::string& opt_id, const std::string& text)
{
    for (const PageShp& page : m_pages) {
        for (const ConfigOptionsGroupShp& group : page->m_optgroups) {
            const auto option = group->get_option_map().find(opt_id);
            if (option == group->get_option_map().end()) {
                continue;
            }
            const auto entry = group->opt_map().find(opt_id);
            const int index = entry == group->opt_map().end() ? -1 : entry->second.second;
            const boost::any value = field_value(option->second.opt, opt_id, *m_config, index, text, m_dialogs, !shows_no_value(opt_id));
            if (!value.empty()) {
                group->on_change_OG(opt_id, value);
            }
            return;
        }
    }
    throw std::runtime_error("Unknown setting: " + opt_id);
}

void Tab::set_override(const std::string& opt_id, const bool enabled)
{
    // The check box of TabFilament::add_filament_overrides_page().
    for (const PageShp& page : m_pages) {
        for (const ConfigOptionsGroupShp& group : page->m_optgroups) {
            Line* line = group->get_line(opt_id);
            if (line == nullptr || !line->near_label_widget) {
                continue;
            }
            const auto entry = group->opt_map().find(opt_id);
            if (entry == group->opt_map().end()) {
                return;
            }
            const std::string& opt_key = entry->second.first;
            const int opt_index = entry->second.second < 0 ? 0 : entry->second.second;
            const bool is_retraction = boost::starts_with(opt_key, "filament_retract") || boost::starts_with(opt_key, "filament_z_hop")
                || boost::starts_with(opt_key, "filament_wipe") || boost::starts_with(opt_key, "filament_deretraction")
                || boost::starts_with(opt_key, "filament_long_retractions");
            const std::string source_key = opt_key.substr(strlen("filament_"));
            const DynamicPrintConfig& source = is_retraction ? m_preset_bundle->printers.get_edited_preset().config
                                                             : m_preset_bundle->prints.get_edited_preset().config;
            if (enabled) {
                // The override starts at the value it overrides, as its field would hold it.
                boost::any value = config_value(source, source_key, is_retraction ? opt_index : 0);
                if (value.empty()) {
                    return;
                }
                if (const std::string* text = boost::any_cast<std::string>(&value)) {
                    value = value_from_text(opt_id, opt_key, opt_index, *text);
                }
                group->on_change_OG(opt_id, value);
            } else {
                // The filament leaves the setting to the printer or the process.
                if (ConfigOption* filament_option = m_config->option(opt_key)) {
                    if (auto* filament_vector = dynamic_cast<ConfigOptionVectorBase*>(filament_option)) {
                        filament_vector->set_at_to_nil(size_t(opt_index));
                    }
                }
                update_dirty();
                on_value_change(opt_id, boost::any());
            }
            return;
        }
    }
}

void Tab::load_key_value(const std::string& opt_key, const boost::any& value, const bool saved_value)
{
    if (!saved_value) {
        detail::change_opt_value(*m_config, opt_key, value);
    }
    // Mark the print & filament enabled if they are compatible with the currently selected preset.
    if (opt_key == "compatible_printers" || opt_key == "compatible_prints") {
        // Don't select another profile if this profile happens to become incompatible.
        m_preset_bundle->update_compatible(PresetSelectCompatibleType::Never);
        // The widget of the list is reloaded with it, which is what decides
        // whether the condition is edited (Tab::compatible_widget_reload).
        compatible_widget_reload(opt_key + "_condition", opt_key);
    }
    update_dirty();
    update();
}

void Tab::load_config(const DynamicPrintConfig& config)
{
    bool modified = false;
    for (const std::string& opt_key : m_config->diff(config)) {
        m_config->set_key_value(opt_key, config.option(opt_key)->clone());
        modified = true;
    }
    if (modified) {
        update_dirty();
        // Initialize UI components with the config values.
        reload_config();
        update();
    }
}

void Tab::update_dirty()
{
    if (m_postpone_update_ui) {
        return;
    }
    m_presets->update_dirty();
    update_changed_ui();
}

void Tab::init_options_list()
{
    if (!m_options_list.empty()) {
        m_options_list.clear();
    }

    for (const std::string& opt_key : m_config->keys()) {
        if (opt_key == "printable_area" || opt_key == "bed_exclude_area" || opt_key == "compatible_prints" || opt_key == "compatible_printers"
            || opt_key == "thumbnails" || opt_key == "wrapping_exclude_area") {
            m_options_list.emplace(opt_key, m_opt_status_value);
            continue;
        }
        const ConfigOptionDef* opt_def = m_config->def()->get(opt_key);
        if (m_config->option(opt_key)->is_vector() && !(opt_def != nullptr && opt_def->gui_flags == "serialized")) {
            m_options_list.emplace(opt_key + "#0", m_opt_status_value);
        } else {
            m_options_list.emplace(opt_key, m_opt_status_value);
        }
    }
}

void Tab::filter_diff_option(std::vector<std::string>& options)
{
    for (std::string& opt : options) {
        const auto hash_pos = opt.find_last_of('#');
        if (hash_pos == std::string::npos) continue;
        bool found = false;
        for (const PageShp& page : m_pages) {
            if (const auto iter = page->m_opt_id_map.find(opt); iter != page->m_opt_id_map.end()) {
                opt = iter->second;
                found = true;
                break;
            }
        }
        if (found) continue;

        // Keep key#index if that exact option is tracked.
        if (m_options_list.find(opt) != m_options_list.end()) continue;

        const std::string base = opt.substr(0, hash_pos);
        const std::string idx0 = base + "#0";
        if (m_options_list.find(idx0) != m_options_list.end()) {
            opt = idx0;
            continue;
        }
        if (m_options_list.find(base) != m_options_list.end()) {
            opt = base;
        }
    }
}

void Tab::update_changed_ui()
{
    if (m_postpone_update_ui) {
        return;
    }

    const bool deep_compare = (m_type == Preset::TYPE_PRINTER || m_type == Preset::TYPE_PRINT || m_type == Preset::TYPE_FILAMENT
                               || m_type == Preset::TYPE_SLA_MATERIAL || m_type == Preset::TYPE_MODEL);
    auto dirty_options = m_presets->current_dirty_options(deep_compare);
    auto nonsys_options = m_presets->current_different_from_parent_options(deep_compare);
    update_custom_dirty(dirty_options, nonsys_options);

    filter_diff_option(dirty_options);
    filter_diff_option(nonsys_options);

    for (auto& it : m_options_list) {
        it.second = m_opt_status_value;
    }

    for (const std::string& opt_key : dirty_options) {
        const auto iter = m_options_list.find(opt_key);
        if (iter != m_options_list.end()) {
            iter->second &= ~osInitValue;
        }
    }
    for (const std::string& opt_key : nonsys_options) {
        const auto iter = m_options_list.find(opt_key);
        if (iter != m_options_list.end()) {
            iter->second &= ~osSystemValue;
        }
    }
}

void Tab::get_sys_and_mod_flags(const std::string& opt_key, bool& sys_page, bool& modified_page)
{
    const auto opt = m_options_list.find(opt_key);
    if (opt == m_options_list.end()) {
        return;
    }
    // If the value is empty, clear the system flag
    if (opt_key == "compatible_printers" || opt_key == "compatible_prints") {
        const auto* compatible_values = m_config->option<ConfigOptionStrings>(opt_key);
        if (compatible_values != nullptr && compatible_values->values.empty()) {
            sys_page = false; // Empty value should NOT be treated as a system value
        }
    } else if (sys_page) {
        sys_page = (opt->second & osSystemValue) != 0;
    }

    modified_page |= (opt->second & osInitValue) == 0;
}

void Tab::back_to_initial_value(const std::string& opt_id)
{
    for (const PageShp& page : m_pages) {
        for (const ConfigOptionsGroupShp& group : page->m_optgroups) {
            if (group->has_option(opt_id) || group->opt_map().count(opt_id) > 0) {
                group->back_to_initial_value(opt_id);
                return;
            }
        }
    }
}

void Tab::on_roll_back_value(const bool to_sys)
{
    int os;
    if (to_sys) {
        os = osSystemValue;
    } else {
        // BBS: restore all pages in preset
        if (!m_presets->get_edited_preset().is_dirty) return;
        os = osInitValue;
    }

    m_postpone_update_ui = true;

    // BBS: restore all preset
    for (const PageShp& page : m_pages)
        for (const ConfigOptionsGroupShp& group : page->m_optgroups) {
            if (group->title == "Capabilities") {
                if ((m_options_list["extruders_count"] & os) == 0)
                    to_sys ? group->back_to_sys_value("extruders_count") : group->back_to_initial_value("extruders_count");
            }
            if (group->title == "Size and coordinates") {
                if ((m_options_list["printable_area"] & os) == 0) {
                    to_sys ? group->back_to_sys_value("printable_area") : group->back_to_initial_value("printable_area");
                    load_key_value("printable_area", true /*some value*/, true);
                }
            }
            if (group->title == "Profile dependencies") {
                if (m_type != Preset::TYPE_PRINTER && (m_options_list["compatible_printers"] & os) == 0) {
                    to_sys ? group->back_to_sys_value("compatible_printers") : group->back_to_initial_value("compatible_printers");
                    load_key_value("compatible_printers", true /*some value*/, true);
                }
                if ((m_type == Preset::TYPE_FILAMENT || m_type == Preset::TYPE_SLA_MATERIAL) && (m_options_list["compatible_prints"] & os) == 0) {
                    to_sys ? group->back_to_sys_value("compatible_prints") : group->back_to_initial_value("compatible_prints");
                    load_key_value("compatible_prints", true /*some value*/, true);
                }
            }
            for (const auto& kvp : group->opt_map()) {
                const std::string& opt_key = kvp.first;
                const auto iter = m_options_list.find(opt_key);
                if (iter != m_options_list.end() && (iter->second & os) == 0)
                    to_sys ? group->back_to_sys_value(opt_key) : group->back_to_initial_value(opt_key);
            }
        }

    // BBS: restore all pages in preset
    m_presets->discard_current_changes();

    m_postpone_update_ui = false;

    // When all values are rolled, then we have to update whole tab in respect to the reverted values
    update();

    // BBS: restore all pages in preset, update_dirty also update combobox
    update_dirty();
}

void Tab::toggle_option(const std::string& opt_key, const bool toggle, const int opt_index)
{
    if (m_active_page == nullptr) {
        return;
    }
    const std::string field = m_active_page->get_field(opt_key, opt_index);
    if (!field.empty()) {
        // Field::toggle()
        m_toggles.fields[field] = toggle;
    }
}

void Tab::toggle_line(const std::string& opt_key, const bool toggle, const int opt_index)
{
    if (m_active_page == nullptr) {
        return;
    }
    if (Line* line = m_active_page->get_line(opt_key, opt_index)) {
        line->toggle_visible = toggle;
        for (const Option& option : line->get_options()) {
            m_toggles.lines[option.opt_id] = toggle;
        }
    }
}

void Tab::reload_config()
{
    if (m_type == Preset::TYPE_PRINT && m_config != nullptr) {
        m_state.last_sparse_infill_rotate_template_value = m_config->opt_string("sparse_infill_rotate_template");
    }
}

const std::string& Tab::get_custom_gcode(const std::string& opt_key)
{
    return m_config->opt_string(opt_key);
}

void Tab::set_custom_gcode(const std::string& opt_key, const std::string& value)
{
    DynamicPrintConfig new_conf = *m_config;
    new_conf.set_key_value(opt_key, new ConfigOptionString(value));
    load_config(new_conf);
}

void Tab::edit_custom_gcode(const std::string& opt_key, const std::string& edited_gcode)
{
    // if (dlg.ShowModal() == wxID_OK)
    set_custom_gcode(opt_key, edited_gcode);
    update_dirty();
    update();
}

bool Tab::validate_custom_gcode(const std::string& title, const std::string& gcode)
{
    std::vector<std::string> tags;
    const bool invalid = GCodeProcessor::contains_reserved_tags(gcode, 5, tags);
    if (invalid) {
        std::string lines = ":\n";
        for (const std::string& keyword : tags) {
            lines += ";" + keyword + "\n";
        }
        UiText reports = ui_text("Following line %s contains reserved keywords.\nPlease remove it, or will beat G-code visualization and printing time estimation.",
                                 {lines});
        reports.msgid_plural = "Following lines %s contain reserved keywords.\nPlease remove them, or will beat G-code visualization and printing time estimation.";
        reports.count = std::int64_t(tags.size());
        m_dialogs.inform("reserved_keywords", {reports}, {ui_text("Reserved keywords found"), ui_text(" " + title)});
    }
    return !invalid;
}

void Tab::set_choices(const std::string& opt_id, const std::string& opt_key, const std::vector<int>& set)
{
    const ConfigOptionDef* def = print_config_def.get(opt_key);
    if (def == nullptr) {
        return;
    }
    SettingChoices choices;
    for (const int i : set) {
        choices.values.push_back(def->enum_values[i]);
        choices.labels.push_back(def->enum_labels[i]);
    }
    m_choices[opt_id] = std::move(choices);
}

std::vector<std::string> Tab::generate_extruder_options()
{
    std::vector<std::string> options;
    if (m_type != Preset::TYPE_FILAMENT) {
        return options;
    }

    const auto* variants = m_config->option<ConfigOptionStrings>("filament_extruder_variant");
    if (variants == nullptr) {
        return options;
    }

    const std::vector<std::string> known_nozzle_types = {
        get_nozzle_volume_type_string(NozzleVolumeType::nvtHighFlow),
        get_nozzle_volume_type_string(NozzleVolumeType::nvtStandard),
    };

    for (const std::string& variant : variants->values) {
        std::string drive;
        std::string nozzle;

        for (const std::string& nozzle_type : known_nozzle_types) {
            if (variant.size() > nozzle_type.size() && variant.substr(variant.size() - nozzle_type.size()) == nozzle_type
                && variant[variant.size() - nozzle_type.size() - 1] == ' ') {
                drive = variant.substr(0, variant.size() - nozzle_type.size() - 1);
                nozzle = nozzle_type;
                break;
            }
        }

        options.push_back(nozzle.empty() ? variant : drive + ": " + nozzle);
    }

    return options;
}

void Tab::update_extruder_variants(const int extruder_id)
{
    if (m_type == Preset::TYPE_FILAMENT) {
        if (extruder_id >= 0) {
            return;
        }
        const std::vector<std::string> options = generate_extruder_options();
        if (!options.empty() && (m_state.variant < 0 || m_state.variant >= int(options.size()))) {
            m_state.variant = 0;
        }
    }
    switch_excluder(extruder_id);
}

void Tab::switch_excluder(int extruder_id)
{
    Preset& printer_preset = m_preset_bundle->printers.get_edited_preset();
    auto nozzle_volumes = m_preset_bundle->project_config.option<ConfigOptionEnumsGeneric>("nozzle_volume_type");
    auto extruders = printer_preset.config.option<ConfigOptionEnumsGeneric>("extruder_type");
    if (nozzle_volumes == nullptr || extruders == nullptr || nozzle_volumes->values.empty() || extruders->values.empty()) {
        return;
    }
    const std::pair<std::string, std::string> variant_keys[]{
        {}, {"print_extruder_id", "print_extruder_variant"},     // Preset::TYPE_PRINT
        {}, {"", "filament_extruder_variant"},                   // Preset::TYPE_FILAMENT filament don't use id anymore
        {}, {"printer_extruder_id", "printer_extruder_variant"}, // Preset::TYPE_PRINTER
    };
    const bool has_variant_combo = m_type == Preset::TYPE_FILAMENT;
    if (!has_variant_combo && (extruder_id >= int(nozzle_volumes->size()) || extruder_id >= int(extruders->size()))) {
        extruder_id = 0;
    }
    if (has_variant_combo) {
        const int current_variant = std::max(m_state.variant, 0);
        if (extruder_id == -1) {
            extruder_id = current_variant;
        } else if (extruder_id != current_variant) {
            return;
        }
    }
    const auto keys = variant_keys[m_type >= Preset::TYPE_COUNT ? Preset::TYPE_PRINT : m_type];
    auto get_index_for_extruder = [&](const int extruder, const int stride = 1) {
        return m_config->get_index_for_extruder(extruder + 1, keys.first, ExtruderType(extruders->values[extruder]),
                                                NozzleVolumeType(nozzle_volumes->values[extruder]), keys.second, stride);
    };
    int index = has_variant_combo ? extruder_id : get_index_for_extruder(extruder_id == -1 ? 0 : extruder_id);
    if (index < 0) {
        return;
    }
    m_variant_index = index;
    for (const PageShp& page : m_pages) {
        if (m_type == Preset::TYPE_PRINTER) {
            if (boost::starts_with(page->title(), "Extruder")) {
                const std::string& title = page->title();
                const int extruder_id2 = std::atoi(title.size() > 9 ? title.c_str() + 9 : "") - 1;
                if (extruder_id >= 0 && extruder_id2 != extruder_id) {
                    continue;
                }
                if (extruder_id2 > 0) {
                    index = get_index_for_extruder(extruder_id2);
                }
            } else if (boost::starts_with(page->title(), "Motion ability")) {
                index = get_index_for_extruder(extruder_id == -1 ? 0 : extruder_id, 2);
            }
        }
        page->m_opt_id_map.clear();
        for (const ConfigOptionsGroupShp& group : page->m_optgroups) {
            for (auto& opt : group->opt_map()) {
                const auto iter = std::find(printer_extruder_options.begin(), printer_extruder_options.end(), opt.second.first);
                if (iter != printer_extruder_options.end()) {
                    page->m_opt_id_map.insert({opt.first, opt.first});
                    continue;
                }

                if (opt.second.second >= 0) {
                    opt.second.second = index;
                    page->m_opt_id_map.insert({opt.second.first + "#" + std::to_string(index), opt.first});
                }
            }
        }
    }
}

void validate_custom_gcode_cb(Tab* tab, const std::string& title, const std::string& opt_key, const boost::any& value)
{
    tab->validate_custom_gcode(title, boost::any_cast<std::string>(value));
    tab->update_dirty();
    tab->on_value_change(opt_key, value);
}

namespace {

// Plater::get_next_color_for_filament()
std::string next_color_for_filament()
{
    static int curr_color_filamenet = 0;
    // refs to https://www.ebaomonthly.com/window/photo/lesson/colorList.htm
    // ORCA updated all color palette
    static const char* const colors[] = {
        "#00C1AE", "#F4E2C1", "#ED1C24", "#00FF7F", "#F26722", "#FFEB31", "#7841CE", "#115877",
        "#ED1E79", "#2EBDEF", "#345B2F", "#800080", "#FA8173", "#800000", "#F7B763", "#A4C41E",
    };
    constexpr int filament_system_colors_num = 16;
    return colors[curr_color_filamenet++ % filament_system_colors_num];
}

}  // namespace

void Tab::on_value_change(const std::string& opt_key, const boost::any& value)
{
    if (opt_key == "gcode_flavor" && m_type == Preset::TYPE_PRINTER) {
        on_gcode_flavor_changed();
    }

    if (opt_key == "compatible_prints") {
        this->compatible_widget_reload("compatible_prints_condition", "compatible_prints");
    }
    if (opt_key == "compatible_printers") {
        this->compatible_widget_reload("compatible_printers_condition", "compatible_printers");
    }

    if (opt_key == "pellet_flow_coefficient") {
        const double double_value = Preset::convert_pellet_flow_to_filament_diameter(boost::any_cast<double>(value));
        m_config->set_key_value("filament_diameter", new ConfigOptionFloats{double_value});
    }

    if (opt_key == "filament_diameter") {
        const double double_value = Preset::convert_filament_diameter_to_pellet_flow(boost::any_cast<double>(value));
        m_config->set_key_value("pellet_flow_coefficient", new ConfigOptionFloats{double_value});
    }

    // The other tabs update themselves the next time the app asks them for
    // their settings (the desktop app updates the process tab here).

    if (opt_key == "enable_prime_tower") {
        auto timelapse_type = m_config->option<ConfigOptionEnum<TimelapseType>>("timelapse_type");
        const bool timelapse_enabled = timelapse_type->value == TimelapseType::tlSmooth;
        if (!boost::any_cast<bool>(value) && timelapse_enabled) {
            bool set_enable_prime_tower = false;
            if (!m_dialogs.ask("prime_tower_smooth_timelapse",
                               {ui_text("A prime tower is required for smooth timelapse. There may be flaws on the model without prime tower. Are you "
                                        "sure you want to disable prime tower?")},
                               {ui_text("Warning")})) {
                DynamicPrintConfig new_conf = *m_config;
                new_conf.set_key_value("enable_prime_tower", new ConfigOptionBool(true));
                m_config_manipulation.apply(m_config, &new_conf);
                set_enable_prime_tower = true;
            }
            const bool enable_wrapping = m_config->option<ConfigOptionBool>("enable_wrapping_detection")->value;
            if (enable_wrapping && !set_enable_prime_tower) {
                if (!m_dialogs.ask("prime_tower_clumping_detection",
                                   {ui_text("A prime tower is required for clumping detection. There may be flaws on the model without prime tower. "
                                            "Are you sure you want to disable prime tower?")},
                                   {ui_text("Warning")})) {
                    DynamicPrintConfig new_conf = *m_config;
                    new_conf.set_key_value("enable_prime_tower", new ConfigOptionBool(true));
                    m_config_manipulation.apply(m_config, &new_conf);
                }
            }
        }
        const bool is_precise_z_height = m_config->option<ConfigOptionBool>("precise_z_height")->value;
        if (boost::any_cast<bool>(value) && is_precise_z_height) {
            if (!m_dialogs.ask("prime_tower_precise_z_height",
                               {ui_text("Enabling both precise Z height and the prime tower may cause slicing errors. Do you still want to enable?")},
                               {ui_text("Warning")})) {
                DynamicPrintConfig new_conf = *m_config;
                new_conf.set_key_value("enable_prime_tower", new ConfigOptionBool(false));
                m_config_manipulation.apply(m_config, &new_conf);
            }
        }
    }

    if (opt_key == "enable_wrapping_detection") {
        const bool wipe_tower_enabled = m_config->option<ConfigOptionBool>("enable_prime_tower")->value;
        if (boost::any_cast<bool>(value) && !wipe_tower_enabled) {
            if (!m_dialogs.ask("clumping_detection_prime_tower",
                               {ui_text("A prime tower is required for clumping detection. There may be flaws on the model without prime tower. Do you "
                                        "still want to enable clumping detection?")},
                               {ui_text("Warning")})) {
                DynamicPrintConfig new_conf = *m_config;
                new_conf.set_key_value("enable_wrapping_detection", new ConfigOptionBool(false));
                m_config_manipulation.apply(m_config, &new_conf);
            }
        }
    }

    if (opt_key == "precise_z_height") {
        const bool wipe_tower_enabled = m_config->option<ConfigOptionBool>("enable_prime_tower")->value;
        if (boost::any_cast<bool>(value) && wipe_tower_enabled) {
            if (!m_dialogs.ask("precise_z_height_prime_tower",
                               {ui_text("Enabling both precise Z height and the prime tower may cause slicing errors. Do you still want to enable "
                                        "precise Z height?")},
                               {ui_text("Warning")})) {
                DynamicPrintConfig new_conf = *m_config;
                new_conf.set_key_value("precise_z_height", new ConfigOptionBool(false));
                m_config_manipulation.apply(m_config, &new_conf);
            }
        }
    }

    // reload scene to update timelapse wipe tower
    if (opt_key == "timelapse_type") {
        const bool wipe_tower_enabled = m_config->option<ConfigOptionBool>("enable_prime_tower")->value;
        if (!wipe_tower_enabled && boost::any_cast<int>(value) == int(TimelapseType::tlSmooth)) {
            if (m_dialogs.ask("smooth_timelapse_prime_tower",
                              {ui_text("A prime tower is required for smooth timelapse. There may be flaws on the model without prime tower. Do you "
                                       "want to enable prime tower?")},
                              {ui_text("Warning")})) {
                DynamicPrintConfig new_conf = *m_config;
                new_conf.set_key_value("enable_prime_tower", new ConfigOptionBool(true));
                m_config_manipulation.apply(m_config, &new_conf);
            }
        }
    }

    if (opt_key == "print_sequence" && m_config->opt_enum<PrintSequence>("print_sequence") == PrintSequence::ByObject) {
        auto printer_structure_opt = m_preset_bundle->printers.get_edited_preset().config.option<ConfigOptionEnum<PrinterStructure>>("printer_structure");
        if (printer_structure_opt && printer_structure_opt->value == PrinterStructure::psI3) {
            if (!m_dialogs.ask("print_by_object_timelapse",
                               {ui_text("The current printer does not support timelapse in Traditional Mode when printing By-Object."), ui_text("\n\n"),
                                ui_text("Still print by object?")})) {
                DynamicPrintConfig new_conf = *m_config;
                new_conf.set_key_value("print_sequence", new ConfigOptionEnum<PrintSequence>(PrintSequence::ByLayer));
                m_config_manipulation.apply(m_config, &new_conf);
            }
        }
    }

    // BBS set support style to default when support type changes
    // Orca: do this only in simple mode
    if (opt_key == "support_type" && m_mode == comSimple) {
        DynamicPrintConfig new_conf = *m_config;
        new_conf.set_key_value("support_style", new ConfigOptionEnum<SupportMaterialStyle>(smsDefault));
        m_config_manipulation.apply(m_config, &new_conf);
    }

    if (opt_key == "support_filament") {
        const int filament_id = m_config->opt_int("support_filament") - 1; // the displayed id is based from 1, while internal id is based from 0
        if (is_support_filament(*m_preset_bundle, filament_id, false) && !is_soluble_filament(*m_preset_bundle, filament_id)
            && !has_filaments(*m_preset_bundle, {"TPU", "TPU-AMS"})) {
            DynamicPrintConfig new_conf = *m_config;
            if (!m_dialogs.ask("support_filament_non_soluble",
                               {ui_text("Non-soluble support materials are not recommended for support base.\n"
                                        "Are you sure to use them for support base?\n")})) {
                new_conf.set_key_value("support_filament", new ConfigOptionInt(0));
                m_config_manipulation.apply(m_config, &new_conf);
                on_value_change(opt_key, 0);
            }
        }
    }

    // BBS popup a message to ask the user to set optimum parameters for support interface if support materials are used
    if (opt_key == "support_interface_filament") {
        const int filament_id = m_config->opt_int("support_filament") - 1;
        const int interface_filament_id = m_config->opt_int("support_interface_filament") - 1; // the displayed id is based from 1, while internal id is based from 0
        if ((is_support_filament(*m_preset_bundle, interface_filament_id, false)
             && !(m_config->opt_float("support_top_z_distance") == 0 && m_config->opt_float("support_interface_spacing") == 0
                  && m_config->opt_enum<SupportMaterialInterfacePattern>("support_interface_pattern")
                      == SupportMaterialInterfacePattern::smipRectilinearInterlaced))
            || (is_soluble_filament(*m_preset_bundle, interface_filament_id) && !is_soluble_filament(*m_preset_bundle, filament_id))) {
            std::vector<UiText> msg_text;
            if (!is_soluble_filament(*m_preset_bundle, interface_filament_id)) {
                msg_text.push_back(ui_text("When using support material for the support interface, we recommend the following settings:\n"
                                           "0 top Z distance, 0 interface spacing, interlaced rectilinear pattern and disable independent support layer height."));
            } else {
                msg_text.push_back(ui_text("When using soluble material for the support interface, we recommend the following settings:\n"
                                           "0 top Z distance, 0 interface spacing, interlaced rectilinear pattern, disable independent support layer height\n"
                                           "and use soluble materials for both support interface and support base."));
            }
            msg_text.push_back(ui_text("\n\n"));
            msg_text.push_back(ui_text("Change these settings automatically?\n"
                                       "Yes - Change these settings automatically\n"
                                       "No  - Do not change these settings for me"));
            DynamicPrintConfig new_conf = *m_config;
            if (m_dialogs.ask("support_interface_filament", std::move(msg_text), {ui_text("Suggestion")})) {
                auto& filament_presets = m_preset_bundle->filament_presets;
                auto& filaments = m_preset_bundle->filaments;
                Preset* filament = filaments.find_preset(filament_presets[interface_filament_id]);
                const std::string filament_type = filament->config.option<ConfigOptionStrings>("filament_type")->values[0];

                new_conf.set_key_value("support_top_z_distance", new ConfigOptionFloat(0));
                new_conf.set_key_value("support_interface_spacing", new ConfigOptionFloat(0));
                new_conf.set_key_value("support_interface_pattern",
                                       new ConfigOptionEnum<SupportMaterialInterfacePattern>(SupportMaterialInterfacePattern::smipRectilinearInterlaced));
                new_conf.set_key_value("independent_support_layer_height", new ConfigOptionBool(false));
                if ((filament_type == "PLA" && has_filaments(*m_preset_bundle, {"TPU", "TPU-AMS"}))
                    || (is_soluble_filament(*m_preset_bundle, interface_filament_id) && !is_soluble_filament(*m_preset_bundle, filament_id)))
                    new_conf.set_key_value("support_filament", new ConfigOptionInt(interface_filament_id + 1));
                m_config_manipulation.apply(m_config, &new_conf);
            }
        }
    }

    if (opt_key == "make_overhang_printable") {
        if (m_config->opt_bool("make_overhang_printable")) {
            if (!m_dialogs.ask("make_overhang_printable",
                               {ui_text("Enabling this option will modify the model's shape. If your print requires precise dimensions or is part of an "
                                        "assembly, it's important to double-check whether this change in geometry impacts the functionality of your print."),
                                ui_text("\n\n"), ui_text("Are you sure you want to enable this option?")},
                               {}, ui_text("Enable"), ui_text("Cancel"))) {
                DynamicPrintConfig new_conf = *m_config;
                new_conf.set_key_value("make_overhang_printable", new ConfigOptionBool(false));
                m_config_manipulation.apply(m_config, &new_conf);
            }
        }
    }

    if (opt_key == "sparse_infill_rotate_template") {
        // Orca: show warning dialog if rotate template for solid infill if not support
        const auto _sparse_infill_pattern = m_config->option<ConfigOptionEnum<InfillPattern>>("sparse_infill_pattern")->value;
        bool is_safe_to_rotate = _sparse_infill_pattern == ipRectilinear || _sparse_infill_pattern == ipLine || _sparse_infill_pattern == ipZigZag
            || _sparse_infill_pattern == ipCrossZag || _sparse_infill_pattern == ipLockedZag;

        const auto new_value = boost::any_cast<std::string>(value);
        is_safe_to_rotate = is_safe_to_rotate || new_value.empty();
        const bool had_previous_value = !m_state.last_sparse_infill_rotate_template_value.empty();

        if (!is_safe_to_rotate && !had_previous_value) {
            if (!m_dialogs.ask("sparse_infill_rotate_template",
                               {ui_text("Infill patterns are typically designed to handle rotation automatically to ensure proper printing and achieve "
                                        "their intended effects (e.g., Gyroid, Cubic). Rotating the current sparse infill pattern may lead to "
                                        "insufficient support. Please proceed with caution and thoroughly check for any potential printing issues. "
                                        "Are you sure you want to enable this option?"),
                                ui_text("\n\n"), ui_text("Are you sure you want to enable this option?")},
                               {}, ui_text("Enable"), ui_text("Cancel"))) {
                DynamicPrintConfig new_conf = *m_config;
                new_conf.set_key_value("sparse_infill_rotate_template", new ConfigOptionString(""));
                m_config_manipulation.apply(m_config, &new_conf);
            }
        }

        m_state.last_sparse_infill_rotate_template_value = m_config->opt_string("sparse_infill_rotate_template");
    }

    if (opt_key == "layer_height") {
        auto min_layer_height_from_nozzle = m_preset_bundle->full_config().option<ConfigOptionFloats>("min_layer_height")->values;
        auto max_layer_height_from_nozzle = m_preset_bundle->full_config().option<ConfigOptionFloats>("max_layer_height")->values;
        const auto layer_height_floor = *std::min_element(min_layer_height_from_nozzle.begin(), min_layer_height_from_nozzle.end());
        const auto layer_height_ceil = *std::max_element(max_layer_height_from_nozzle.begin(), max_layer_height_from_nozzle.end());
        const auto lh = m_config->opt_float("layer_height");
        const bool exceed_minimum_flag = lh < layer_height_floor;
        const bool exceed_maximum_flag = lh > layer_height_ceil;

        if (exceed_maximum_flag || exceed_minimum_flag) {
            if (lh < EPSILON) {
                m_dialogs.inform("layer_height_too_small", {ui_text("Layer height is too small.\nIt will set to min_layer_height\n")});
                auto new_conf = *m_config;
                new_conf.set_key_value("layer_height", new ConfigOptionFloat(layer_height_floor));
                m_config_manipulation.apply(m_config, &new_conf);
            } else {
                const bool answer_yes = m_dialogs.ask(
                    "layer_height_limits",
                    {ui_text("Layer height exceeds the limit in Printer Settings -> Extruder -> Layer height limits, this may cause printing quality "
                             "issues."),
                     ui_text("\n\n"), ui_text("Adjust to the set range automatically?\n")},
                    {}, ui_text("Adjust"), ui_text("Ignore"));
                auto new_conf = *m_config;
                if (answer_yes) {
                    if (exceed_maximum_flag) new_conf.set_key_value("layer_height", new ConfigOptionFloat(layer_height_ceil));
                    if (exceed_minimum_flag) new_conf.set_key_value("layer_height", new ConfigOptionFloat(layer_height_floor));
                    m_config_manipulation.apply(m_config, &new_conf);
                }
            }
        }
    }

    const std::string opt_key_without_idx = opt_key.substr(0, opt_key.find('#'));

    if (opt_key_without_idx == "long_retractions_when_cut") {
        const unsigned char activate = boost::any_cast<unsigned char>(value);
        if (activate == 1) {
            m_dialogs.inform("long_retractions_when_cut",
                             {ui_text("Experimental feature: Retracting and cutting off the filament at a greater distance during filament changes to "
                                      "minimize flush. Although it can notably reduce flush, it may also elevate the risk of nozzle clogs or other "
                                      "printing complications.")});
        }
    }

    if (opt_key == "filament_long_retractions_when_cut") {
        const unsigned char activate = boost::any_cast<unsigned char>(value);
        if (activate == 1) {
            m_dialogs.inform("filament_long_retractions_when_cut",
                             {ui_text("Experimental feature: Retracting and cutting off the filament at a greater distance during filament changes to "
                                      "minimize flush. Although it can notably reduce flush, it may also elevate the risk of nozzle clogs or other "
                                      "printing complications. Please use with the latest printer firmware.")});
        }
    }

    // Orca: sync filament num if it's a multi tool printer
    if (opt_key == "extruders_count" && !m_config->opt_bool("single_extruder_multi_material")) {
        const auto num_extruder = boost::any_cast<size_t>(value);
        const int old_filament_size = int(m_preset_bundle->filament_presets.size());
        std::vector<std::string> new_colors;
        for (int i = old_filament_size; i < int(num_extruder); ++i) {
            new_colors.push_back(next_color_for_filament());
        }
        m_preset_bundle->set_num_filaments(unsigned(num_extruder), new_colors);
        m_preset_bundle->export_selections(m_app_config);
    }

    // Orca: disable purge_in_prime_tower if single_extruder_multi_material is disabled
    if (opt_key == "single_extruder_multi_material" && m_config->opt_bool("single_extruder_multi_material") == false) {
        DynamicPrintConfig new_conf = *m_config;
        new_conf.set_key_value("purge_in_prime_tower", new ConfigOptionBool(false));
        m_config_manipulation.apply(m_config, &new_conf);
    }

    if (opt_key.find("nozzle_volume_type") != std::string::npos) {
        const int extruder_idx = std::atoi(opt_key.substr(opt_key.find_last_of('#') + 1).c_str());
        update_extruder_variants(extruder_idx);
        reload_config();
    }

    // Orca: allow different layer height for non-bbl printers
    // TODO: allow this for BBL printers too?
    if (m_preset_bundle->get_printer_extruder_count() > 1 && m_preset_bundle->is_bbl_vendor()) {
        const int extruder_idx = std::atoi(opt_key.substr(opt_key.find_last_of('#') + 1).c_str());
        if (opt_key.find("min_layer_height") != std::string::npos) {
            auto min_layer_height_from_nozzle = m_preset_bundle->full_config().option<ConfigOptionFloats>("min_layer_height")->values;
            if (size_t(extruder_idx) < min_layer_height_from_nozzle.size()) {
                const double val = min_layer_height_from_nozzle[extruder_idx];
                std::fill(min_layer_height_from_nozzle.begin(), min_layer_height_from_nozzle.end(), val);
            }
            auto new_conf = *m_config;
            new_conf.set_key_value("min_layer_height", new ConfigOptionFloats(min_layer_height_from_nozzle));
            m_config_manipulation.apply(m_config, &new_conf);
        } else if (opt_key.find("max_layer_height") != std::string::npos) {
            auto max_layer_height_from_nozzle = m_preset_bundle->full_config().option<ConfigOptionFloats>("max_layer_height")->values;
            if (size_t(extruder_idx) < max_layer_height_from_nozzle.size()) {
                const double val = max_layer_height_from_nozzle[extruder_idx];
                std::fill(max_layer_height_from_nozzle.begin(), max_layer_height_from_nozzle.end(), val);
            }
            auto new_conf = *m_config;
            new_conf.set_key_value("max_layer_height", new ConfigOptionFloats(max_layer_height_from_nozzle));
            m_config_manipulation.apply(m_config, &new_conf);
        }
    }

    if (opt_key == "parallel_printheads_count" || opt_key == "parallel_printheads_bed_exclude_areas") {
        if (m_config->opt_bool("support_parallel_printheads")) {
            const int count = opt_key == "parallel_printheads_count" ? boost::any_cast<int>(value) : m_config->opt_int("parallel_printheads_count");
            std::string exclude_area;
            if (count > 0) {
                if (const auto* areas = m_config->option<ConfigOptionStrings>("parallel_printheads_bed_exclude_areas"); areas != nullptr) {
                    const size_t index = size_t(count - 1);
                    if (index < areas->values.size()) {
                        exclude_area = areas->values[index];
                    }
                }
            }
            const DialogAnswers no_answers;
            SettingsDialogs no_dialogs{no_answers};
            const ConfigOptionDef* def = m_config->def()->get("bed_exclude_area");
            if (def != nullptr) {
                const boost::any area = field_value(*def, "bed_exclude_area", *m_config, -1, exclude_area, no_dialogs, false);
                if (!area.empty()) {
                    detail::change_opt_value(*m_config, "bed_exclude_area", area);
                }
            }
        }
    }

    if (m_postpone_update_ui) {
        // It means that not all values are rolled to the system/last saved values jet.
        // And call of the update() can causes a redundant check of the config values,
        return;
    }

    update();
}

void Tab::create_line_with_widget(ConfigOptionsGroup* optgroup, const std::string& opt_key, const std::string& path, const SettingWidget widget)
{
    Line line = optgroup->create_single_option_line(opt_key);
    line.widget = widget;
    line.label_path = path;
    optgroup->append_line(line);
}

void Tab::compatible_widget_reload(const std::string& key_condition, const std::string& key_list)
{
    const auto* list = m_config->option<ConfigOptionStrings>(key_list);
    if (list == nullptr) {
        return;
    }
    // Tab::compatible_widget_reload(): the condition is edited only while no
    // preset is listed, since a list and an expression are two ways to say the
    // same thing.
    const bool has_any = !list->values.empty();
    toggle_option(key_condition, !has_any);
}

boost::any Tab::value_from_text(const std::string& opt_id, const std::string& opt_key, const int opt_index, const std::string& text)
{
    const ConfigOptionDef* opt = m_config->def()->get(opt_key);
    if (opt == nullptr) {
        return {};
    }
    // The value the field would hold for the text, without the checks a change
    // of the field runs: the text comes from a preset, not from the user.
    const DialogAnswers no_answers;
    SettingsDialogs dialogs(no_answers);
    return field_value(*opt, opt_id, *m_config, opt_index, text, dialogs, false);
}

void Tab::cache_config_diff(const std::vector<std::string>& selected_options, const DynamicPrintConfig* config)
{
    m_state.cache_options = selected_options;
    m_state.cache_config.apply_only(config != nullptr ? *config : m_presets->get_edited_preset().config, selected_options);
}

void Tab::apply_config_from_cache()
{
    // check and apply extruders count for printer preset
    bool was_applied = m_type == Preset::TYPE_PRINTER && apply_extruder_cnt_from_cache();

    if (!m_state.cache_config.empty()) {
        m_presets->get_edited_preset().config.apply_only(m_state.cache_config, m_state.cache_options);
        m_state.cache_config.clear();
        m_state.cache_options.clear();
        was_applied = true;
    }

    if (was_applied) {
        update_dirty();
    }
}

namespace {

// get_pure_opt_key() of UnsavedChangesDialog.cpp: the key without the index of
// the extruder, "retraction_length" of "retraction_length#1".
std::string pure_opt_key(std::string opt_key)
{
    const std::size_t pos = opt_key.find('#');
    if (pos != std::string::npos && pos > 0) {
        opt_key.erase(pos);
    }
    return opt_key;
}

// The extruder count of a printer, as both dialogs list it first.
PresetChange extruders_count_change(const std::size_t old_count, const std::size_t new_count)
{
    PresetChange change;
    change.id = "extruders_count";
    change.category = {ui_text("General")};
    change.group = {ui_text("Capabilities")};
    change.label = {ui_text("Extruder count")};
    change.old_value = {ui_text("%1%", {std::to_string(old_count)})};
    change.new_value = {ui_text("%1%", {std::to_string(new_count)})};
    return change;
}

}  // namespace

// UnsavedChangesDialog::update_tree(): the edited preset against the selected
// one.
std::vector<PresetChange> Tab::describe_changes()
{
    std::vector<PresetChange> changes;
    const Preset& selected = m_presets->get_selected_preset();
    const DynamicPrintConfig& old_config = selected.config;
    const DynamicPrintConfig& new_config = m_presets->get_edited_preset().config;

    // Collect dirty options.
    const bool deep_compare = m_type == Preset::TYPE_PRINTER || m_type == Preset::TYPE_FILAMENT;
    const std::vector<std::string> dirty_options = m_presets->current_dirty_options(deep_compare);

    // process changes of extruders count
    if (m_type == Preset::TYPE_PRINTER && selected.printer_technology() == ptFFF) {
        const std::size_t old_count = old_config.opt<ConfigOptionFloats>("nozzle_diameter")->values.size();
        const std::size_t new_count = new_config.opt<ConfigOptionFloats>("nozzle_diameter")->values.size();
        if (old_count != new_count) {
            changes.push_back(extruders_count_change(old_count, new_count));
        }
    }

    append_changes(changes, dirty_options, old_config, new_config);
    return changes;
}

// DiffPresetDialog::update_tree(): the left preset is the one the values come
// from, the right one the preset they would move to.
std::vector<PresetChange> Tab::compare(const Preset& left, const Preset& right)
{
    std::vector<PresetChange> changes;
    const DynamicPrintConfig& left_config = left.config;
    const DynamicPrintConfig& right_config = right.config;
    const bool fff_printer = m_type == Preset::TYPE_PRINTER && left.printer_technology() == ptFFF;
    const auto extruders = [](const DynamicPrintConfig& config) { return config.opt<ConfigOptionStrings>("extruder_colour")->values.size(); };

    // Collect dirty options.
    const bool deep_compare = m_type == Preset::TYPE_PRINTER || m_type == Preset::TYPE_FILAMENT;
    const std::vector<std::string> dirty_options = fff_printer && extruders(left_config) < extruders(right_config) ?
                                                       PresetCollection::dirty_options(&right, &left, deep_compare) :
                                                       PresetCollection::dirty_options(&left, &right, deep_compare);

    // process changes of extruders count
    if (fff_printer && extruders(left_config) != extruders(right_config)) {
        changes.push_back(extruders_count_change(extruders(left_config), extruders(right_config)));
    }

    append_changes(changes, dirty_options, left_config, right_config);
    return changes;
}

void Tab::append_changes(std::vector<PresetChange>& changes, const std::vector<std::string>& dirty_options,
                         const DynamicPrintConfig& old_config, const DynamicPrintConfig& new_config)
{
    // Where a setting sits in the tab, as the searcher of the desktop app tells
    // the dialog: the page, the option group, and the option's label.
    std::map<std::string, std::array<std::vector<UiText>, 3>> places;
    for (const PageShp& page : m_pages) {
        const std::vector<UiText> category = translate_category(page->title(), m_type, *m_preset_bundle);
        for (const ConfigOptionsGroupShp& group : page->m_optgroups) {
            for (const Line& line : group->get_lines()) {
                for (const Option& option : line.get_options()) {
                    places.emplace(option.opt_id, std::array<std::vector<UiText>, 3>{category, std::vector<UiText>{ui_text(group->title)},
                                                                                     std::vector<UiText>{ui_text(option.opt.label)}});
                }
            }
        }
    }

    for (const std::string& opt_key : dirty_options) {
        // OptionsSearcher::get_option(): the first setting whose key is the
        // one without the extruder index, which the entries of every extruder
        // share.
        const std::string lookup_key = pure_opt_key(opt_key);
        const auto place = places.lower_bound(lookup_key);
        if (place == places.end() || pure_opt_key(place->first) != lookup_key) {
            // When the found option is not the requested one.
            // This can happen for dirty_options such as:
            // "default_print_profile", "printer_model", "printer_settings_id",
            // because they do not exist in the searcher.
            continue;
        }
        PresetChange& change = changes.emplace_back();
        change.id = opt_key;
        change.category = place->second[0];
        change.group = place->second[1];
        change.label = place->second[2];
        change.old_value = changed_value_text(opt_key, old_config);
        change.new_value = changed_value_text(opt_key, new_config);
    }
}

// SavePresetDialog::Item::Item()
std::string save_preset_name(const Preset& selected, bool& copy_suffix)
{
    std::string name;
    copy_suffix = false;
    if (selected.is_default) {
        name = "Untitled";
    } else if (selected.is_system) {
        name = selected.name;
        copy_suffix = true;
    } else {
        name = selected.is_from_bundle() && !selected.alias.empty() ? selected.alias : selected.name;
    }
    // if name contains extension
    if (boost::iends_with(name, ".ini")) {
        name.resize(name.length() - 4);
    }
    return name;
}

PresetSettings Tab::describe()
{
    // The fields keep what the tab last toggled.
    if (!m_toggled) {
        toggle_options();
        m_toggled = true;
    }

    PresetSettings result;
    result.status = SceneStatus::success;
    result.kind = m_type == Preset::TYPE_FILAMENT   ? PresetKind::filament
                  : m_type == Preset::TYPE_PRINTER   ? PresetKind::printer
                  : m_type == Preset::TYPE_MODEL     ? PresetKind::object
                  : m_type == Preset::TYPE_PLATE     ? PresetKind::plate
                                                     : PresetKind::print;
    const Preset& selected = m_presets->get_selected_preset();
    const Preset& edited = m_presets->get_edited_preset();
    result.preset = selected.name;
    // TabPresetComboBox::get_preset_name()
    result.label = edited.is_from_bundle() ? edited.label(false) : edited.label(true);
    result.dirty = edited.is_dirty;
    // GUI_App::has_unsaved_preset_changes() asks the tabs of tabs_list.
    result.saved_dirty = (m_type == Preset::TYPE_PRINT || m_type == Preset::TYPE_FILAMENT || m_type == Preset::TYPE_PRINTER) &&
                         m_presets->saved_is_dirty();
    result.is_default = selected.is_default;
    result.is_system = selected.is_system;
    const Preset* parent = m_presets->get_selected_preset_parent();
    result.has_parent = parent != nullptr;
    // Tab::update_btns_enabling()
    result.can_delete = edited.can_overwrite();
    result.mode = static_cast<SettingsMode>(m_mode);
    result.active_page = m_active_page != nullptr ? m_active_page->title() : std::string();
    // Tab::update_extruder_variants(): what m_variant_combo offers and shows.
    result.variants = generate_extruder_options();
    result.variant = result.variants.empty() ? 0 : std::max(m_state.variant, 0);

    for (const PageShp& page : m_pages) {
        SettingsPage& described = result.pages.emplace_back();
        described.title = page->title();
        described.label = translate_category(page->title(), m_type, *m_preset_bundle);
        described.icon = page->icon();
        for (const ConfigOptionsGroupShp& group : page->m_optgroups) {
            SettingsGroup& described_group = described.groups.emplace_back();
            described_group.title = group->title;
            described_group.icon = group->icon;
            for (const Line& line : group->get_lines()) {
                SettingsLine& described_line = described_group.lines.emplace_back();
                described_line.separator = line.is_separator();
                described_line.label = line.label;
                described_line.tooltip = line.label_tooltip;
                described_line.widget = line.widget;
                described_line.has_override = line.near_label_widget;
                for (const Option& option : line.get_options()) {
                    SettingsLineOption& described_option = described_line.options.emplace_back();
                    described_option.id = option.opt_id;
                    described_option.key = option.opt.opt_key;
                    const auto entry = group->opt_map().find(option.opt_id);
                    described_option.index = entry == group->opt_map().end() ? -1 : entry->second.second;
                    described_option.label = option.opt.label;
                    described_option.full_width = option.opt.full_width;
                    described_option.is_code = option.opt.is_code;
                    described_option.multiline = option.opt.multiline;
                    described_option.height = option.opt.height;
                    // OptionsGroup::build_field()
                    described_option.edit_custom_gcode = group->edit_custom_gcode && option.opt.is_code;

                    // The value and the state of the option's field.
                    SettingState& state = result.settings.emplace_back();
                    state.id = option.opt_id;
                    state.key = option.opt.opt_key;
                    const int index = described_option.index;
                    state.nullable = option.opt.nullable;
                    const ConfigOption* config_option = m_config->option(option.opt.opt_key);
                    if (state.nullable && config_option != nullptr) {
                        const auto* vector = dynamic_cast<const ConfigOptionVectorBase*>(config_option);
                        state.is_nil = vector != nullptr ? vector->is_nil(size_t(index < 0 ? 0 : index)) : config_option->is_nil();
                    }
                    const auto override_state = m_overrides.find(option.opt_id);
                    state.override_enabled = override_state == m_overrides.end() || override_state->second;
                    // update_filament_overrides_page(): SetValue(is_checked),
                    // where is_checked is off while the check box cannot be
                    // used, and the field then shows the value it would override.
                    if (!state.override_enabled) {
                        state.is_nil = true;
                    }
                    state.value = describe_value(option, index, state.is_nil);
                    if (is_list_option(option.opt.opt_key) && config_option != nullptr) {
                        state.list_values = m_config->option<ConfigOptionStrings>(option.opt.opt_key)->values;
                    }
                    const auto status = m_options_list.find(option.opt_id);
                    const int flags = status != m_options_list.end() ? status->second : m_opt_status_value;
                    state.modified = (flags & osInitValue) == 0;
                    state.system = parent != nullptr && (flags & osSystemValue) != 0;
                    const auto field = m_toggles.fields.find(option.opt_id);
                    // Field::toggle()
                    state.enabled = (field == m_toggles.fields.end() || field->second) && !option.opt.readonly;
                    const auto toggled_line = m_toggles.lines.find(option.opt_id);
                    state.visible = toggled_line == m_toggles.lines.end() || toggled_line->second;
                    const auto choices = m_choices.find(option.opt_id);
                    if (choices != m_choices.end()) {
                        state.has_choices = true;
                        state.choice_values = choices->second.values;
                        state.choice_labels = choices->second.labels;
                    } else if (is_filament_list_option(option.opt.opt_key)) {
                        // DynamicFilamentList: "Default", then the type of each filament.
                        state.has_choices = true;
                        state.choice_values.push_back("0");
                        state.choice_labels.push_back("Default");
                        for (size_t i = 0; i < m_preset_bundle->filament_presets.size(); ++i) {
                            std::string type;
                            if (Preset* filament = m_preset_bundle->filaments.find_preset(m_preset_bundle->filament_presets[i])) {
                                filament->get_filament_type(type);
                            }
                            state.choice_values.push_back(std::to_string(i + 1));
                            state.choice_labels.push_back(type);
                        }
                    }
                }
            }
        }
    }

    result.save_name = save_preset_name(selected, result.save_name_copy_suffix);

    result.notices = m_dialogs.take_notices();
    return result;
}

std::string Tab::describe_value(const Option& option, const int index, const bool is_nil)
{
    if (!is_nil) {
        return field_text(option.opt, config_value(*m_config, option.opt.opt_key, index));
    }
    // A filament override that is not set shows the value it would override
    // (TabFilament::update_filament_overrides_page).
    const std::string& opt_key = option.opt.opt_key;
    if (!boost::starts_with(opt_key, "filament_")) {
        return {};
    }
    const std::string source_key = opt_key.substr(strlen("filament_"));
    const bool is_ironing = boost::starts_with(opt_key, "filament_ironing");
    const DynamicPrintConfig& source = is_ironing ? m_preset_bundle->prints.get_edited_preset().config
                                                  : m_preset_bundle->printers.get_edited_preset().config;
    if (!source.has(source_key)) {
        return {};
    }
    return field_text(*source.def()->get(source_key), config_value(source, source_key, is_ironing ? 0 : index));
}

}  // namespace orcinus::orca::detail
