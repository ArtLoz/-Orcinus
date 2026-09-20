#pragma once

#include "settings_tab.hpp"

namespace orcinus::orca::detail {

// TabPrinter of Tab.hpp: the printer tab. Its pages depend on the printer:
// one per extruder, and the motion ability of a Marlin-like firmware.
class TabPrinter : public Tab {
public:
    TabPrinter(Slic3r::PresetBundle& bundle, Slic3r::AppConfig& app_config, TabState& state, SettingsDialogs& dialogs)
        : Tab(Slic3r::Preset::TYPE_PRINTER, bundle, app_config, state, dialogs)
    {
    }

    void build() override;
    void build_fff();
    void reload_config() override;
    PresetSettings describe() override;
    void toggle_options() override;
    void update() override;
    void update_fff();
    void on_gcode_flavor_changed() override;
    void extruders_count_changed(std::size_t extruders_count);
    PageShp build_kinematics_page();
    void build_unregular_pages(bool from_initial_build = false);
    void on_preset_loaded() override;
    void init_options_list() override;
    bool supports_printer_technology(Slic3r::PrinterTechnology /* tech */) const override { return true; }

    void cache_extruder_cnt(const Slic3r::DynamicPrintConfig* config = nullptr);
    bool apply_extruder_cnt_from_cache() override;

    std::size_t m_extruders_count{0};
    std::size_t m_extruders_count_old{0};

private:
    void append_option_line(const ConfigOptionsGroupShp& optgroup, const std::string& opt_key, const std::string& label_path = "");
    void update_input_shaper_menu(Slic3r::GCodeFlavor flavor);

    bool m_rebuild_kinematics_page{false};
    std::vector<std::string> m_extruder_variant_list;
};

}  // namespace orcinus::orca::detail
