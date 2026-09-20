#pragma once

#include "settings_tab.hpp"

namespace orcinus::orca::detail {

// TabFilament of Tab.hpp: the filament tab.
class TabFilament : public Tab {
public:
    TabFilament(Slic3r::PresetBundle& bundle, Slic3r::AppConfig& app_config, TabState& state, SettingsDialogs& dialogs)
        : Tab(Slic3r::Preset::TYPE_FILAMENT, bundle, app_config, state, dialogs)
    {
    }

    void build() override;
    void reload_config() override;
    void toggle_options() override;
    void update() override;
    void init_options_list() override;
    bool supports_printer_technology(const Slic3r::PrinterTechnology tech) const override { return tech == Slic3r::ptFFF; }

    const std::string& get_custom_gcode(const std::string& opt_key) override;
    void set_custom_gcode(const std::string& opt_key, const std::string& value) override;

private:
    void add_filament_overrides_page();
    void update_filament_overrides_page(const Slic3r::DynamicPrintConfig* printers_config);
};

}  // namespace orcinus::orca::detail
