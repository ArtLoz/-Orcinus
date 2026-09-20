#pragma once

#include "settings_tab.hpp"

namespace orcinus::orca::detail {

// TabPrint of Tab.hpp: the process tab.
class TabPrint : public Tab {
public:
    TabPrint(Slic3r::PresetBundle& bundle, Slic3r::AppConfig& app_config, TabState& state, SettingsDialogs& dialogs)
        : Tab(Slic3r::Preset::TYPE_PRINT, bundle, app_config, state, dialogs)
    {
    }

    void build() override;
    void reload_config() override;
    void toggle_options() override;
    void update() override;
    bool supports_printer_technology(const Slic3r::PrinterTechnology tech) const override { return tech == Slic3r::ptFFF; }

protected:
    // TabPrint(parent, type): the settings of an object or of the plate are the
    // ones of the process tab, for another kind of preset.
    TabPrint(Slic3r::Preset::Type type, Slic3r::PresetBundle& bundle, Slic3r::AppConfig& app_config, TabState& state, SettingsDialogs& dialogs)
        : Tab(type, bundle, app_config, state, dialogs)
    {
    }
};

}  // namespace orcinus::orca::detail
