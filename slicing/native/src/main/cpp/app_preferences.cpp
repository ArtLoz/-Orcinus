#include "orca_engine_adapter.hpp"

// OrcaSlicer's Preferences (PreferencesDialog): the "app" section of the app
// configuration, which the desktop app's dialog reads and writes key by key.

#include "engine_context.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/Utils.hpp"

namespace orcinus::orca {

using detail::engine;

AppConfigValues app_config_values(const std::vector<std::string>& keys)
{
    AppConfigValues result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().config == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    for (const std::string& key : keys) {
        result.values.push_back(engine().config->get(key));
    }
    result.status = SceneStatus::success;
    return result;
}

AppConfigValues set_app_config_value(const std::string& key, const std::string& value)
{
    AppConfigValues result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().config == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    engine().config->set(key, value);
    // create_item_loglevel_combobox(): the level the dialog picks applies at once.
    if (key == "log_severity_level") {
        Slic3r::set_logging_level(Slic3r::level_string_to_boost(value));
    }
    detail::save_config(engine());
    result.values.push_back(engine().config->get(key));
    result.status = SceneStatus::success;
    return result;
}

AppConfigValues recent_projects()
{
    AppConfigValues result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().config == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    result.values = engine().config->get_recent_projects();
    result.status = SceneStatus::success;
    return result;
}

AppConfigValues set_recent_projects(const std::vector<std::string>& projects)
{
    AppConfigValues result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().config == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    engine().config->set_recent_projects(projects);
    detail::save_config(engine());
    result.values = engine().config->get_recent_projects();
    result.status = SceneStatus::success;
    return result;
}

}  // namespace orcinus::orca
