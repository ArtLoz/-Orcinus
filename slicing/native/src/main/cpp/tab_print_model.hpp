#pragma once

#include <string>
#include <vector>

#include "settings_tab.hpp"
#include "tab_print.hpp"

namespace orcinus::orca::detail {

// TabPrintModel of Tab.cpp: the process settings an object or the plate
// overrides. The tab shows the process preset with the overrides applied, and
// every change goes into the overrides, which the app keeps with the object.
//
// The tab edits every object the object list has selected: their configurations
// are merged, and a value they disagree on is a null key the tab shows no value
// for. A change writes the new value into every one of them.
class TabPrintModel : public TabPrint {
public:
    // The selected objects disagree on the setting, whose field shows no value.
    bool shows_no_value(const std::string& opt_id) const override;
    TabPrintModel(Slic3r::Preset::Type type, std::vector<std::string> keys, Slic3r::PresetBundle& bundle, Slic3r::AppConfig& app_config,
                  TabState& state, SettingsDialogs& dialogs);

    void build() override;

    // TabPrintModel::set_model_config(): the values the selected objects
    // override, and the ones the plate overrides, which an object's settings
    // follow.
    virtual void set_model_config(std::vector<Slic3r::DynamicPrintConfig> model_configs, const Slic3r::DynamicPrintConfig& plate_config);
    // The overrides after the change; the app keeps them with the objects.
    const std::vector<Slic3r::DynamicPrintConfig>& model_configs() const { return m_object_configs; }

    // The configuration the overrides sit on, which the desktop app reaches
    // through m_parent_tab: the process preset for an object and for the plate,
    // and the settings of the object for one of its parts.
    virtual void set_parent_config(const Slic3r::DynamicPrintConfig&) {}

    void update_model_config();
    // The Reset Options button of the desktop app's parameter panel.
    void reset_model_config();
    bool has_key(const std::string& key) const;

    void on_value_change(const std::string& opt_key, const boost::any& value) override;
    void reload_config() override;
    void update_custom_dirty(std::vector<std::string>& dirty_options, std::vector<std::string>& nonsys_options) override;
    // The undo of a line, which removes the override, and the Reset Options
    // button, which removes every one of them.
    void back_to_initial_value(const std::string& opt_id) override;
    void on_roll_back_value(bool to_sys = false) override;
    PresetSettings describe() override;

protected:
    // The configuration the overrides sit on: the edited process preset, which
    // upstream reaches through m_parent_tab.
    virtual Slic3r::DynamicPrintConfig& parent_config() { return m_preset_bundle->prints.get_edited_preset().config; }

    // TabPrintModel::m_keys: the settings the tab may override.
    std::vector<std::string> m_keys;
    // TabPrintModel::m_prints: the process preset the overrides are compared
    // against, which makes the changed values of the tab the overridden ones.
    Slic3r::PresetCollection m_prints;
    // TabPrintModel::m_object_configs: the ModelConfig of every object the list
    // has selected, or the one of the plate.
    std::vector<Slic3r::DynamicPrintConfig> m_object_configs;
    // The overrides of the plate, which the settings of an object follow.
    Slic3r::DynamicPrintConfig m_plate_config;
    // TabPrintModel::m_all_keys: the keys the objects override.
    std::vector<std::string> m_all_keys;
    // TabPrintModel::m_null_keys: the keys the selected objects disagree on.
    std::vector<std::string> m_null_keys;
    // TabPrintModel::m_back_to_sys: the change removes the override.
    bool m_back_to_sys{false};
};

// TabPrintObject of Tab.cpp: the settings of one object on the plate.
class TabPrintObject : public TabPrintModel {
public:
    TabPrintObject(Slic3r::PresetBundle& bundle, Slic3r::AppConfig& app_config, TabState& state, SettingsDialogs& dialogs);
};

// TabPrintPart of Tab.cpp: the settings of one part of an object. A part
// overrides the settings of the region it prints, and the value an override
// goes back to is the one of the object it belongs to, not of the process
// preset: upstream reaches it through the model tab.
class TabPrintPart : public TabPrintModel {
public:
    TabPrintPart(Slic3r::PresetBundle& bundle, Slic3r::AppConfig& app_config, TabState& state, SettingsDialogs& dialogs);

    void set_parent_config(const Slic3r::DynamicPrintConfig& object_config) override;
    PresetSettings describe() override;

protected:
    Slic3r::DynamicPrintConfig& parent_config() override { return m_parent_config; }

private:
    // The process preset with the settings of the object applied.
    Slic3r::DynamicPrintConfig m_parent_config;
};

// TabPrintLayer of Tab.cpp: the settings of one height range of an object. Like
// a part it sits on the settings of the object, and it always has a layer
// height of its own, which is what a range is for.
class TabPrintLayer : public TabPrintModel {
public:
    TabPrintLayer(Slic3r::PresetBundle& bundle, Slic3r::AppConfig& app_config, TabState& state, SettingsDialogs& dialogs);

    void set_parent_config(const Slic3r::DynamicPrintConfig& object_config) override;
    void set_model_config(std::vector<Slic3r::DynamicPrintConfig> model_configs, const Slic3r::DynamicPrintConfig& plate_config) override;
    void update_custom_dirty(std::vector<std::string>& dirty_options, std::vector<std::string>& nonsys_options) override;
    PresetSettings describe() override;

protected:
    Slic3r::DynamicPrintConfig& parent_config() override { return m_parent_config; }

private:
    // The process preset with the settings of the object applied.
    Slic3r::DynamicPrintConfig m_parent_config;
};

// TabPrintPlate of Tab.cpp: the settings of the plate itself.
class TabPrintPlate : public TabPrintModel {
public:
    TabPrintPlate(Slic3r::PresetBundle& bundle, Slic3r::AppConfig& app_config, TabState& state, SettingsDialogs& dialogs);

    void build() override;
    void on_value_change(const std::string& opt_key, const boost::any& value) override;
    void update_custom_dirty(std::vector<std::string>& dirty_options, std::vector<std::string>& nonsys_options) override;

private:
    // The filaments in their order, which a customized layer sequence starts from.
    std::vector<int> default_layer_sequence() const;
};

// The settings of the plate (plate_keys of Tab.cpp), which are not settings of
// the process preset.
const std::vector<std::string>& plate_setting_keys();

}  // namespace orcinus::orca::detail
