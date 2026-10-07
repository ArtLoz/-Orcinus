#include "orca_engine_adapter.hpp"

// OrcaSlicer's Troubleshoot Center (TroubleshootDialog): the overview of the
// loaded profiles, and the cleaning of the system profiles' cache, which
// waits for the engine's next start.

#include <algorithm>
#include <exception>

#include <boost/filesystem.hpp>
#include <boost/log/trivial.hpp>
#include <boost/nowide/fstream.hpp>
#include <nlohmann/json.hpp>

#include "engine_context.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Utils.hpp"

namespace orcinus::orca {

using detail::engine;
using detail::follow_config;

namespace {

namespace fs = boost::filesystem;
using ojson = nlohmann::ordered_json;

// What "Clean system profiles cache" leaves in the data directory for the
// next start.
fs::path clean_mark(const fs::path& data_dir)
{
    return data_dir / "system.clean";
}

// GetProfilesOverview(): the values of a list option of the preset, under key
// when it has any.
void add_values(ojson& entry, const char* key, const Slic3r::Preset& preset, const char* option)
{
    const auto* values = dynamic_cast<const Slic3r::ConfigOptionStrings*>(preset.config.option(option));
    if (values != nullptr && !values->values.empty()) {
        ojson array = ojson::array();
        for (const std::string& value : values->values) {
            array.push_back(value);
        }
        entry[key] = array;
    }
}

// create_item_loaded_profiles(): the system presets of a collection.
std::int32_t system_presets(const Slic3r::PresetCollection& presets)
{
    return static_cast<std::int32_t>(std::count_if(presets.begin(), presets.end(), [](const Slic3r::Preset& preset) { return preset.is_system; }));
}

}  // namespace

ProfilesOverview profiles_overview()
{
    const std::lock_guard<std::mutex> lock(engine().mutex);
    ProfilesOverview overview;
    if (engine().bundle == nullptr) {
        return overview;
    }
    try {
        follow_config(engine());
        const Slic3r::PresetBundle& bundle = *engine().bundle;
        const Slic3r::AppConfig& config = *engine().config;

        // TroubleshootDialog::GetProfilesOverview()
        ojson root = ojson::object();
        root["Overview"] = "";
        {  // PRINTERS - enabled
            ojson array = ojson::array();
            for (const auto& [vendor_name, models] : config.vendors()) {
                for (const auto& [model_name, variants] : models) {
                    array.push_back(model_name);
                    ++overview.printers_active;
                }
            }
            root["printers_enabled"] = array;
        }
        {  // PRINTERS - user defined
            ojson array = ojson::array();
            for (const Slic3r::Preset& preset : bundle.printers) {
                if (!preset.is_user()) {
                    continue;
                }
                ojson entry;
                entry["name"] = preset.name;
                entry["inherits"] = preset.inherits();
                array.push_back(entry);
                ++overview.printers_user;
            }
            root["printers_user"] = array;
        }
        // FILAMENTS - enabled
        if (config.has_section(Slic3r::AppConfig::SECTION_FILAMENTS)) {
            const auto& filaments = config.get_section(Slic3r::AppConfig::SECTION_FILAMENTS);
            if (!filaments.empty()) {
                ojson array = ojson::array();
                for (const auto& filament : filaments) {
                    array.push_back(filament.first);
                    ++overview.filaments_active;
                }
                root["filaments_enabled"] = array;
            }
        }
        {  // FILAMENTS - user defined
            ojson array = ojson::array();
            for (const Slic3r::Preset& preset : bundle.filaments) {
                if (!preset.is_user()) {
                    continue;
                }
                ojson entry;
                entry["name"] = preset.name;
                entry["inherits"] = preset.inherits();
                add_values(entry, "compatible_printers", preset, "compatible_printers");
                add_values(entry, "compatible_processes", preset, "compatible_prints");
                array.push_back(entry);
                ++overview.filaments_user;
            }
            root["filaments_user"] = array;
        }
        {  // PROCESSES - enabled, grouped by compatible printer
            ojson object = ojson::object();
            for (const Slic3r::Preset& printer : bundle.printers) {
                if (!printer.is_visible || !printer.is_compatible) {
                    continue;
                }
                ojson array = ojson::array();
                for (const Slic3r::Preset& process : bundle.prints) {
                    if (!process.is_visible || process.is_user() || !process.loaded) {
                        continue;
                    }
                    const auto* compatible_printers = dynamic_cast<const Slic3r::ConfigOptionStrings*>(process.config.option("compatible_printers"));
                    if (compatible_printers == nullptr ||
                        std::find(compatible_printers->values.begin(), compatible_printers->values.end(), printer.name) ==
                            compatible_printers->values.end()) {
                        continue;
                    }
                    array.push_back(process.name);
                    ++overview.processes_active;
                }
                if (!array.empty()) {
                    object[printer.name] = array;
                }
            }
            root["processes_enabled"] = object;
        }
        {  // PROCESSES - user defined
            ojson array = ojson::array();
            for (const Slic3r::Preset& preset : bundle.prints) {
                if (!preset.is_user()) {
                    continue;
                }
                ojson entry;
                entry["name"] = preset.name;
                entry["inherits"] = preset.inherits();
                add_values(entry, "compatible_printers", preset, "compatible_printers");
                array.push_back(entry);
                ++overview.processes_user;
            }
            root["processes_user"] = array;
        }
        {  // OVERVIEW
            ojson entry;
            entry["printers__act"] = overview.printers_active;
            entry["printers__usr"] = overview.printers_user;
            entry["filaments_act"] = overview.filaments_active;
            entry["filaments_usr"] = overview.filaments_user;
            entry["processes_act"] = overview.processes_active;
            entry["processes_usr"] = overview.processes_user;
            root["Overview"] = entry;
        }
        // A name that is not UTF-8 is written with U+FFFD instead of failing the export.
        overview.json = root.dump(4, ' ', false, ojson::error_handler_t::replace);

        // TroubleshootDialog::create_item_loaded_profiles()
        overview.printers_system = system_presets(bundle.printers);
        overview.filaments_system = system_presets(bundle.filaments);
        overview.processes_system = system_presets(bundle.prints);

        boost::system::error_code error;
        overview.system_cleaned = fs::exists(clean_mark(Slic3r::data_dir()), error);
    } catch (const std::exception& error) {
        BOOST_LOG_TRIVIAL(error) << "Unable to describe the loaded profiles: " << error.what();
        return {};
    }
    return overview;
}

bool clean_system_profiles()
{
    const fs::path mark = clean_mark(Slic3r::data_dir());
    boost::nowide::ofstream file(mark.string(), std::ios::out | std::ios::binary | std::ios::trunc);
    if (!file.is_open()) {
        BOOST_LOG_TRIVIAL(warning) << "Unable to mark the system profiles for cleaning: " << mark.string();
        return false;
    }
    return true;
}

void remove_cleaned_system_profiles(const std::string& data_dir)
{
    const fs::path mark = clean_mark(data_dir);
    boost::system::error_code error;
    if (!fs::exists(mark, error)) {
        return;
    }
    // TroubleshootDialog::RebuildSystemProfiles()
    fs::remove_all(fs::path(data_dir) / PRESET_SYSTEM_DIR, error);
    if (error) {
        BOOST_LOG_TRIVIAL(warning) << "Failed to delete system folder..." << error.message();
    }
    fs::remove(mark, error);
}

}  // namespace orcinus::orca
