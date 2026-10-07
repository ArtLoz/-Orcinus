#pragma once

// The settings tabs of the desktop app (Tab.cpp, OptionsGroup.cpp): the pages
// a tab lays out, the values of the edited preset, and what a tab does when one
// of them changes. The classes keep the upstream names and the order of their
// members, so an OrcaSlicer update can be followed.
//
// The app draws the controls, so a field here is not a control but what the tab
// knows about it: which option a line shows, whether the tab enabled it, and the
// text it would hold.

#include <functional>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include <boost/any.hpp>

#include "config_manipulation.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "orca_engine_adapter.hpp"
#include "settings_dialogs.hpp"

namespace orcinus::orca::detail {

// GUI_App::get_mode()
Slic3r::ConfigOptionMode app_mode(const Slic3r::AppConfig& app_config);

// Option of OptionsGroup.hpp: a setting as a line shows it, with the changes
// the tab made to its definition.
struct Option {
    Slic3r::ConfigOptionDef opt;
    std::string opt_id;

    Option(const Slic3r::ConfigOptionDef& _opt, std::string id) : opt(_opt), opt_id(std::move(id)) {}
};

// Line of OptionsGroup.hpp.
class Line {
public:
    std::string label;
    std::string label_tooltip;
    std::string label_path;
    // BBS: object config
    bool undo_to_sys{false};
    // BBS: hide some line
    bool toggle_visible{true};

    std::size_t full_width{0};
    // The widget the line shows in place of its fields.
    SettingWidget widget{SettingWidget::none};
    // The check box of a filament override before the label.
    bool near_label_widget{false};

    void append_option(const Option& option) { m_options.push_back(option); }

    Line(std::string label, std::string tooltip) : label(std::move(label)), label_tooltip(std::move(tooltip)) {}
    Line() : m_is_separator(true) {}

    bool is_separator() const { return m_is_separator; }

    const std::vector<Option>& get_options() const { return m_options; }
    std::vector<Option>& get_options() { return m_options; }

private:
    bool m_is_separator{false};
    std::vector<Option> m_options;
};

class Tab;

// The option ids of an option group by the key and index they edit.
using t_opt_map = std::map<std::string, std::pair<std::string, int>>;
using t_change = std::function<void(const std::string&, const boost::any&)>;

// ConfigOptionsGroup of OptionsGroup.hpp.
class ConfigOptionsGroup {
public:
    ConfigOptionsGroup(std::string title, std::string icon, const Slic3r::DynamicPrintConfig* config)
        : icon(std::move(icon)), title(std::move(title)), m_config(config)
    {
    }
    virtual ~ConfigOptionsGroup() = default;

    const std::string icon;
    std::string title;
    std::size_t label_width = 20;
    t_change m_on_change{nullptr};
    // Field::set_value() then Field::get_value(): the value the field of an
    // option gives for the text the config shows for it.
    std::function<boost::any(const std::string& opt_id, const std::string& opt_key, int opt_index, const std::string& text)> m_value_from_text{nullptr};
    std::function<Slic3r::DynamicPrintConfig()> m_get_initial_config{nullptr};
    std::function<Slic3r::DynamicPrintConfig()> m_get_sys_config{nullptr};
    std::function<bool()> have_sys_config{nullptr};
    // OptionsGroup::edit_custom_gcode: the custom G-codes of the group offer
    // EditGCodeDialog. Upstream holds the function that opens it; the app
    // opens the dialog, so the group only says whether there is one.
    bool edit_custom_gcode{false};

    void append_line(const Line& line);
    Line* get_line(const std::string& opt_key);
    void append_separator();

    Line create_single_option_line(const Option& option, const std::string& path = std::string()) const;
    void append_single_option_line(const Option& option, const std::string& path = std::string()) { append_line(create_single_option_line(option, path)); }

    Option get_option(const std::string& opt_key, int opt_index = -1);
    Line create_single_option_line(const std::string& title, const std::string& path = std::string(), int idx = -1)
    {
        Option option = get_option(title, idx);
        return create_single_option_line(option, path);
    }
    void append_single_option_line(const std::string& title, const std::string& path = std::string(), int idx = -1)
    {
        append_single_option_line(get_option(title, idx), path);
    }

    // ExtruderOptionsGroup changes every value of a vector setting.
    virtual void on_change_OG(const std::string& opt_id, const boost::any& value);
    void back_to_initial_value(const std::string& opt_key);
    void back_to_sys_value(const std::string& opt_key);
    void back_to_config_value(const Slic3r::DynamicPrintConfig& config, const std::string& opt_key);
    // What the field of an option gives for the value the config shows.
    boost::any field_of_config_value(const std::string& opt_id, const std::string& opt_key, int opt_index, const boost::any& value);

    const t_opt_map& opt_map() const { return m_opt_map; }
    t_opt_map& opt_map() { return m_opt_map; }
    const std::vector<Line>& get_lines() const { return m_lines; }
    std::vector<Line>& get_lines() { return m_lines; }
    const std::map<std::string, Option>& get_option_map() const { return m_options; }
    bool has_option(const std::string& opt_id) const { return m_options.count(opt_id) > 0; }
    void remove_option_if(const std::function<bool(const std::string&)>& comp);
    // OptionsGroup::is_visible(): whether any line is shown in the mode.
    bool is_visible(Slic3r::ConfigOptionMode mode) const;

protected:
    void change_opt_value(const std::string& opt_key, const boost::any& value, int opt_index = 0);

    const Slic3r::DynamicPrintConfig* m_config{nullptr};
    std::map<std::string, Option> m_options;
    std::vector<Slic3r::ConfigOptionMode> m_options_mode;
    std::vector<Line> m_lines;
    t_opt_map m_opt_map;
};

using ConfigOptionsGroupShp = std::shared_ptr<ConfigOptionsGroup>;

// BBS. ExtruderOptionsGroup changes all members in vector option.
class ExtruderOptionsGroup : public ConfigOptionsGroup {
public:
    using ConfigOptionsGroup::ConfigOptionsGroup;

    void on_change_OG(const std::string& opt_id, const boost::any& value) override;
};

// Page of Tab.hpp.
class Page {
public:
    Page(std::string title, std::string icon, Tab* tab, const Slic3r::DynamicPrintConfig* config)
        : m_title(std::move(title)), m_icon(std::move(icon)), m_tab(tab), m_config(config)
    {
    }

    const std::string& title() const { return m_title; }
    const std::string& icon() const { return m_icon; }
    void set_title(std::string title) { m_title = std::move(title); }

    ConfigOptionsGroupShp new_optgroup(const std::string& title, const std::string& icon = std::string(), int noncommon_label_width = -1,
                                       bool is_extruder_og = false);
    ConfigOptionsGroupShp get_optgroup(const std::string& title) const;

    // Page::get_field() and get_line(): the option the tab means by a key and
    // an index, which a variant may show under another id.
    std::string get_field(const std::string& opt_key, int opt_index = -1) const;
    Line* get_line(const std::string& opt_key, int opt_index = -1);

    std::vector<ConfigOptionsGroupShp> m_optgroups;
    std::map<std::string, std::string> m_opt_id_map;

private:
    std::string m_title;
    std::string m_icon;
    Tab* m_tab{nullptr};
    const Slic3r::DynamicPrintConfig* m_config{nullptr};
};

using PageShp = std::shared_ptr<Page>;

// validate_custom_gcode_cb() of Tab.cpp: the group of a custom G-code checks it
// for reserved keywords before the value goes to the tab.
void validate_custom_gcode_cb(Tab* tab, const std::string& title, const std::string& opt_key, const boost::any& value);

// The members of a tab that outlive one request, since the app builds the tab
// again for every one of them.
struct TabState {
    ConfigManipulationState manipulation;
    // Tab::m_last_sparse_infill_rotate_template_value
    std::string last_sparse_infill_rotate_template_value;
    // The presets whose selection the tab loaded (Tab::load_current_preset()).
    std::string loaded_preset;
    std::string loaded_printer;
    // The page the tab shows (Tab::m_active_page).
    std::string active_page;
    // TabPrinter
    std::size_t initial_extruders_count{0};
    std::size_t sys_extruders_count{0};
    std::size_t cache_extruder_count{0};
    bool use_silent_mode{false};
    std::string base_preset_name;
    // TabFilament: the extruder variant the tab shows (m_variant_combo).
    int variant{0};
    // Tab::m_cache_config and m_cache_options: the values a preset change moves
    // to the preset that is selected next (Tab::cache_config_diff).
    Slic3r::DynamicPrintConfig cache_config;
    std::vector<std::string> cache_options;
};

// What the tab's toggle_option() and toggle_line() were last told for a
// setting: whether its field is enabled and its line shown.
struct SettingToggles {
    std::map<std::string, bool> fields;
    std::map<std::string, bool> lines;
};

// The entries the tab gives a combo box in place of the definition's.
struct SettingChoices {
    std::vector<std::string> values;
    std::vector<std::string> labels;
};

// SavePresetDialog::Item::Item(): the name the save dialog suggests for a
// preset, and whether " - Copy" follows it.
std::string save_preset_name(const Slic3r::Preset& selected, bool& copy_suffix);

// What the preset selection asks of the settings tabs (settings_tabs.cpp).
// The engine mutex is held by the selection.
//
// Tab::may_discard_current_dirty_preset(): the unsaved changes of the edited
// preset, which the app shows before it selects another one.
std::vector<PresetChange> preset_changes(PresetKind kind);
// The dialog's Transfer: the changes move to the preset selected next. With
// no_transfer_variant, a dependent preset of a printer that is left for one of
// other extruder variants, the changes of the variants stay behind.
void cache_preset_changes(PresetKind kind, bool no_transfer_variant = false);
// Whether that Transfer leaves changes of the variants behind, which the
// desktop app warns about ("Use Modified Value").
bool transfer_drops_variants(PresetKind kind);
// Tab::apply_config_from_cache() alone: the moved values go into the preset
// that was selected, before anything else follows the selection.
void apply_tab_cache(PresetKind kind);
// Tab::apply_config_from_cache() and load_current_preset() once a preset was
// selected: the moved values go into it.
void reload_tab_after_selection(PresetKind kind);
// Tab::transfer_options(): the values of options in from are kept for the
// preset the tab selects next (DiffPresetDialog's Transfer).
void cache_transfer(PresetKind kind, const Slic3r::DynamicPrintConfig& from, const std::vector<std::string>& options);
// Tab::apply_config_from_cache() and load_current_preset() of the preset the
// tab already edits, which a transfer into it loads again.
void reload_tab(PresetKind kind);
// Tab::on_value_change() of nozzle_volume_type: the other preset tabs show the
// variant of that extruder too (update_extruder_variants() and reload_config()
// of every tab of wxGetApp().tabs_list).
void update_extruder_variants_of_tabs(Slic3r::Preset::Type except, int extruder_idx);

// Tab of Tab.hpp, without the parts that only draw.
class Tab {
public:
    Tab(Slic3r::Preset::Type type, Slic3r::PresetBundle& bundle, Slic3r::AppConfig& app_config, TabState& state, SettingsDialogs& dialogs);
    virtual ~Tab() = default;

    Slic3r::Preset::Type type() const { return m_type; }
    Slic3r::PresetCollection* get_presets() { return m_presets; }
    Slic3r::DynamicPrintConfig* get_config() { return m_config; }
    // Tab::m_options_list, which init_options_list() fills.
    const std::map<std::string, int>& options_list() const { return m_options_list; }

    virtual bool supports_printer_technology(Slic3r::PrinterTechnology tech) const = 0;
    virtual void build() = 0;
    virtual void update() = 0;
    virtual void toggle_options() = 0;
    virtual void reload_config();
    virtual void on_value_change(const std::string& opt_key, const boost::any& value);
    // A field the tab shows no value for (TabPrintModel's m_null_keys): any
    // text is a change, as Field::value_was_changed() compares with nothing.
    virtual bool shows_no_value(const std::string& /*opt_id*/) const { return false; }
    virtual void on_preset_loaded() {}
    // TabPrinter::apply_extruder_cnt_from_cache(): the extruders a preset
    // change moved to the selected printer.
    virtual bool apply_extruder_cnt_from_cache() { return false; }
    // TabPrinter::on_gcode_flavor_changed()
    virtual void on_gcode_flavor_changed() {}
    virtual void init_options_list();
    virtual void update_custom_dirty(std::vector<std::string>& dirty_options, std::vector<std::string>& nonsys_options) {}
    virtual const std::string& get_custom_gcode(const std::string& opt_key);
    virtual void set_custom_gcode(const std::string& opt_key, const std::string& value);
    // Tab::edit_custom_gcode() after EditGCodeDialog, which the app shows,
    // was closed with OK and the G-code it edited.
    void edit_custom_gcode(const std::string& opt_key, const std::string& edited_gcode);

    void load_initial_data();
    PageShp add_options_page(const std::string& title, const std::string& icon, bool is_extruder_pages = false);

    // Tab::load_current_preset() when another preset was selected since the tab
    // last loaded one.
    void load_selection();
    // Tab::activate_selected_page(): the page the app shows.
    void activate_page(const std::string& page);

    // A field's text changed (OptionsGroup::on_change_OG through Field).
    void change_field(const std::string& opt_id, const std::string& text);
    // The check box of a filament override.
    void set_override(const std::string& opt_id, bool enabled);
    void load_key_value(const std::string& opt_key, const boost::any& value, bool saved_value = false);
    void load_config(const Slic3r::DynamicPrintConfig& config);
    void update_dirty();
    void update_changed_ui();
    void get_sys_and_mod_flags(const std::string& opt_key, bool& sys_page, bool& modified_page);
    // Tab::update_changed_tree_ui(): the name of the page takes the colour of
    // a modified value.
    bool is_page_modified(const Page& page);
    virtual void on_roll_back_value(bool to_sys = false);
    virtual void back_to_initial_value(const std::string& opt_id);
    void toggle_option(const std::string& opt_key, bool toggle, int opt_index = -1);
    void toggle_line(const std::string& opt_key, bool toggle, int opt_index = -1);
    // Tab::create_line_with_widget(): a line the app shows with a widget of its
    // own in place of the field.
    void create_line_with_widget(ConfigOptionsGroup* optgroup, const std::string& opt_key, const std::string& path, SettingWidget widget);
    // Tab::compatible_widget_reload(): the condition is edited only while the
    // preset is not compatible with every printer or process.
    void compatible_widget_reload(const std::string& key_condition, const std::string& key_list);
    void filter_diff_option(std::vector<std::string>& options);
    void update_extruder_variants(int extruder_id = -1);
    void switch_excluder(int extruder_id = -1);
    std::vector<std::string> generate_extruder_options();
    // Tab::validate_custom_gcode(): reserved keywords in a custom G-code.
    bool validate_custom_gcode(const std::string& title, const std::string& gcode);

    // Field::set_value() then Field::get_value(): what the field of an option
    // gives for the text the config shows for it.
    boost::any value_from_text(const std::string& opt_id, const std::string& opt_key, int opt_index, const std::string& text);

    // Tab::cache_config_diff(): the values the preset changed are kept for the
    // preset that is selected next. They come from config, or from the edited
    // preset when it is null; DiffPresetDialog's Transfer gives the left preset.
    void cache_config_diff(const std::vector<std::string>& selected_options, const Slic3r::DynamicPrintConfig* config = nullptr);
    // Tab::apply_config_from_cache(): the kept values go into the preset that
    // was selected.
    void apply_config_from_cache();
    // UnsavedChangesDialog::update_tree(): the values the edited preset changed.
    std::vector<PresetChange> describe_changes();
    // DiffPresetDialog::update_tree(): what the presets left and right of this
    // tab's kind differ in, described the same way the unsaved changes are.
    std::vector<PresetChange> compare(const Slic3r::Preset& left, const Slic3r::Preset& right);

    // What the app draws: the pages, the values, and the state of every field.
    virtual PresetSettings describe();

    Slic3r::PresetBundle* m_preset_bundle{nullptr};
    Slic3r::PresetCollection* m_presets{nullptr};
    Slic3r::DynamicPrintConfig* m_config{nullptr};

protected:
    // The settings both dialogs list for dirty_options, each where the searcher
    // of the desktop app finds it, with its value in either config.
    void append_changes(std::vector<PresetChange>& changes, const std::vector<std::string>& dirty_options,
                        const Slic3r::DynamicPrintConfig& old_config, const Slic3r::DynamicPrintConfig& new_config);

    // The entries a combo box shows in place of the definition's.
    void set_choices(const std::string& opt_id, const std::string& opt_key, const std::vector<int>& set);
    // The text the field of an option shows; for a filament override that is
    // not set, the value of the preset it would override.
    std::string describe_value(const Option& option, int index, bool is_nil);

    Slic3r::Preset::Type m_type;
    Slic3r::AppConfig& m_app_config;
    TabState& m_state;
    SettingsDialogs& m_dialogs;
    Slic3r::ConfigOptionMode m_mode;
    std::vector<PageShp> m_pages;
    Page* m_active_page{nullptr};
    enum OptStatus { osSystemValue = 1, osInitValue = 2 };
    std::map<std::string, int> m_options_list;
    int m_opt_status_value{0};
    bool m_is_default_preset{false};
    bool m_postpone_update_ui{false};
    int m_update_cnt{0};
    // What toggle_option() and toggle_line() told about the active page.
    SettingToggles m_toggles;
    std::map<std::string, SettingChoices> m_choices;
    // Whether the check box of a filament override can be used
    // (TabFilament::update_filament_overrides_page).
    std::map<std::string, bool> m_overrides;
    // The configuration of the plater, which the tabs apply their changes to
    // (MainFrame::on_config_changed).
    Slic3r::DynamicPrintConfig m_plater_config;
    ConfigManipulation m_config_manipulation;
    bool m_toggled{false};
    // The variant of the extruder the tab shows (Tab::switch_excluder).
    int m_variant_index{0};

    friend class Page;
    friend class ConfigOptionsGroup;
};

}  // namespace orcinus::orca::detail
