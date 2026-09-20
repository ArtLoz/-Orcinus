#pragma once

// ConfigManipulation of the desktop app (ConfigManipulation.cpp) for the
// process tab: the corrections a change of the print settings brings, and
// which fields the tab enables and which lines it shows. The functions keep
// the upstream names and order, so upstream changes can be followed.

#include <functional>
#include <map>
#include <string>
#include <vector>

#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "settings_dialogs.hpp"

namespace orcinus::orca::detail {

// The members of ConfigManipulation that outlive a change.
struct ConfigManipulationState {
    bool is_initialized_support_material_overhangs_queried{false};
    bool support_material_overhangs_queried{false};
    bool is_BBL_Printer{false};
};

class ConfigManipulation {
public:
    // The callbacks are what Tab::get_config_manipulation() gives: load_config
    // is called when apply() changed the config, cb_toggle_field and
    // cb_toggle_line are the tab's toggle_option() and toggle_line().
    // plater_config: the configuration the plater had before the change
    // (Plater::config()).
    ConfigManipulation(ConfigManipulationState& state, Slic3r::PresetBundle& bundle, const Slic3r::DynamicPrintConfig& plater_config,
                       SettingsDialogs& dialogs, std::function<void()> load_config,
                       std::function<void(const std::string&, bool, int)> cb_toggle_field,
                       std::function<void(const std::string&, bool, int)> cb_toggle_line);

    void apply(Slic3r::DynamicPrintConfig* config, Slic3r::DynamicPrintConfig* new_config);
    // Whether a correction is being applied, which the settings of an object
    // leave to the process tab.
    bool is_applying() const;
    // The keys the last correction changed (ConfigManipulation::apply), which
    // the settings of an object take over.
    const Slic3r::t_config_option_keys& applying_keys() const;
    void toggle_field(const std::string& field_key, bool toggle, int opt_index = -1);
    void toggle_line(const std::string& field_key, bool toggle, int opt_index = -1);

    // The checks of the filament tab.
    void check_nozzle_recommended_temperature_range(Slic3r::DynamicPrintConfig* config);
    void check_nozzle_temperature_range(Slic3r::DynamicPrintConfig* config);
    void check_nozzle_temperature_initial_layer_range(Slic3r::DynamicPrintConfig* config);
    void check_filament_max_volumetric_speed(Slic3r::DynamicPrintConfig* config);
    void check_chamber_temperature(Slic3r::DynamicPrintConfig* config);
    void check_chamber_minimal_temperature(Slic3r::DynamicPrintConfig* config);

    // The values several selected objects disagree on, which the tab shows a
    // setting of its own for (ConfigManipulation::apply_null_fff_config).
    void apply_null_fff_config(Slic3r::DynamicPrintConfig* config, const std::vector<std::string>& keys,
                               const std::vector<Slic3r::DynamicPrintConfig>& configs);

    // FFF print
    void update_print_fff_config(Slic3r::DynamicPrintConfig* config, bool is_global_config = false, bool is_plate_config = false);
    void toggle_print_fff_options(Slic3r::DynamicPrintConfig* config, bool is_global_config = false);

    void set_is_BBL_Printer(bool is_bbl_printer) { m_state.is_BBL_Printer = is_bbl_printer; }
    bool get_is_BBL_Printer() const { return m_state.is_BBL_Printer; }

    bool is_initialized_support_material_overhangs_queried() const { return m_state.is_initialized_support_material_overhangs_queried; }
    void initialize_support_material_overhangs_queried(bool queried);

    // Yes, or No; an object's settings always change.
    bool show_spiral_mode_settings_dialog(bool is_object_config = false);

private:
    // get_temperature_range() of ConfigManipulation.
    bool get_temperature_range(Slic3r::DynamicPrintConfig* config, int& range_low, int& range_high);

    Slic3r::t_config_option_keys m_applying_keys;
    ConfigManipulationState& m_state;
    Slic3r::PresetBundle& m_bundle;
    const Slic3r::DynamicPrintConfig& m_plater_config;
    SettingsDialogs& m_dialogs;
    std::function<void()> load_config;
    std::function<void(const std::string&, bool, int)> cb_toggle_field;
    std::function<void(const std::string&, bool, int)> cb_toggle_line;
    bool is_msg_dlg_already_exist{false};
};

// DevPrinterConfigUtil::support_wrapping_detection(): the flag of the printer
// type in resources/printers/<type>.json.
bool support_wrapping_detection(const std::string& printer_type);

}  // namespace orcinus::orca::detail
