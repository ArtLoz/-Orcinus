#include "orca_engine_adapter.hpp"

// OrcaSlicer's ExportConfigsDialog (CreatePresetsDialog.cpp): the user presets
// written as the bundles and archives the desktop app writes. The dialog's
// check boxes are the entries config_export_options() answers with, and its OK
// button is export_configs().

#include <chrono>
#include <ctime>
#include <filesystem>
#include <iomanip>
#include <map>
#include <set>
#include <sstream>

#include <miniz/miniz.h>
#include <nlohmann/json.hpp>

#include "engine_context.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Utils.hpp"

namespace orcinus::orca {

using detail::engine;
using detail::follow_config;

namespace {

// get_curr_time() of CreatePresetsDialog.cpp
std::string current_time(const char* format)
{
    const std::chrono::system_clock::time_point now = std::chrono::system_clock::now();
    const std::time_t time = std::chrono::system_clock::to_time_t(now);
    std::tm local_time{};
#ifdef _WIN32
    localtime_s(&local_time, &time);
#else
    localtime_r(&time, &local_time);
#endif
    std::ostringstream time_stream;
    time_stream << std::put_time(&local_time, format);
    return time_stream.str();
}

// get_curr_timestmp()
std::string current_timestamp()
{
    return current_time("%Y%m%d%H%M%S");
}

// get_machine_name(): what follows the last "@" of a preset's name.
std::string machine_name(const std::string& preset_name)
{
    const std::size_t index_at = preset_name.find_last_of('@');
    return index_at == std::string::npos ? std::string() : preset_name.substr(index_at + 1);
}

// get_filament_name(): the name without the printer it is made for.
std::string filament_name(const std::string& preset_name)
{
    const std::size_t index_at = preset_name.find_last_of('@');
    return index_at == std::string::npos ? preset_name : preset_name.substr(0, index_at - 1);
}

// get_vendor_name(): the first word of the printer's name.
std::string vendor_name(const std::string& preset_name)
{
    if (preset_name.empty()) {
        return {};
    }
    const std::size_t first = preset_name.find_first_not_of(' ');
    if (first == std::string::npos) {
        return {};
    }
    std::string vendor = preset_name.substr(first);
    const std::size_t index_at = vendor.find(' ');
    return index_at == std::string::npos ? vendor : vendor.substr(0, index_at);
}

// The presets ExportConfigsDialog::data_init() gathers: every visible printer,
// the user filament and process presets compatible with it, and the user
// filament presets by the name they share.
struct ExportData {
    std::map<std::string, Slic3r::Preset> printer_presets;
    std::map<std::string, std::vector<Slic3r::Preset>> filament_presets;
    std::map<std::string, std::vector<Slic3r::Preset>> process_presets;
    std::map<std::string, std::vector<std::pair<std::string, Slic3r::Preset>>> filament_name_to_presets;
    // The printer presets the app has, to tell a system printer from a user's.
    std::set<std::string> system_printers;
    std::set<std::string> base_system_printers;
};

// earse_preset_fields_for_safe(): the printer preset is written into the Temp
// folder without the address and the credentials of its physical printer, and
// the export takes that copy.
void write_safe_printer_preset(Slic3r::Preset& preset, const std::filesystem::path& temp_folder)
{
    if (preset.type != Slic3r::Preset::Type::TYPE_PRINTER) {
        return;
    }
    preset.file = (temp_folder / (preset.name + ".json")).make_preferred().string();
    Slic3r::DynamicPrintConfig& config = preset.config;
    config.erase("print_host");
    config.erase("print_host_webui");
    config.erase("printhost_apikey");
    config.erase("printhost_cafile");
    config.erase("printhost_user");
    config.erase("printhost_password");
    config.erase("printhost_port");
    preset.save(nullptr);
}

// ExportConfigsDialog::data_init()
ExportData gather(Slic3r::PresetBundle& app)
{
    // Delete the Temp folder
    const std::filesystem::path user_folder = std::filesystem::path(Slic3r::data_dir()) / PRESET_USER_DIR;
    const std::filesystem::path temp_folder = user_folder / "Temp";
    std::error_code error;
    std::filesystem::remove_all(temp_folder, error);
    std::filesystem::create_directories(temp_folder, error);

    ExportData data;
    Slic3r::PresetBundle preset_bundle(app);

    const std::deque<Slic3r::Preset>& printer_presets = preset_bundle.printers.get_presets();
    for (const Slic3r::Preset& printer_preset : printer_presets) {
        const std::string preset_name = printer_preset.name;
        if (!printer_preset.is_visible || printer_preset.is_default || printer_preset.is_project_embedded) {
            continue;
        }
        if (preset_bundle.printers.select_preset_by_name(preset_name, true)) {
            preset_bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always);

            for (const Slic3r::Preset& filament_preset : preset_bundle.filaments.get_presets()) {
                if (!filament_preset.is_user()) {
                    continue;
                }
                if (filament_preset.is_compatible) {
                    data.filament_presets[preset_name].push_back(filament_preset);
                }
            }

            for (const Slic3r::Preset& process_preset : preset_bundle.prints.get_presets()) {
                if (!process_preset.is_user()) {
                    continue;
                }
                if (process_preset.is_compatible) {
                    data.process_presets[preset_name].push_back(process_preset);
                }
            }

            Slic3r::Preset new_printer_preset = printer_preset;
            write_safe_printer_preset(new_printer_preset, temp_folder);
            data.printer_presets.emplace(preset_name, new_printer_preset);
            if (printer_preset.is_system) {
                data.system_printers.insert(preset_name);
                const Slic3r::Preset* base = preset_bundle.printers.get_preset_base(printer_preset);
                if (base != nullptr && base->name == preset_name) {
                    data.base_system_printers.insert(preset_name);
                }
            }
        }
    }

    for (const Slic3r::Preset& filament_preset : preset_bundle.filaments.get_presets()) {
        if (!filament_preset.can_overwrite()) {
            continue;
        }
        const Slic3r::Preset* base_filament_preset = preset_bundle.filaments.get_preset_base(filament_preset);
        if (base_filament_preset == nullptr) {
            continue;
        }
        const std::string preset_name = base_filament_preset->name;
        data.filament_name_to_presets[filament_name(preset_name)].emplace_back(vendor_name(machine_name(preset_name)), filament_preset);
    }
    return data;
}

// initial_zip_archive() and save_zip_archive_to_file(): the files of an archive.
class ZipArchive {
public:
    explicit ZipArchive(const std::string& path)
    {
        mz_zip_zero_struct(&archive_);
        opened_ = mz_zip_writer_init_file(&archive_, path.c_str(), 0) == MZ_TRUE;
    }

    ZipArchive(const ZipArchive&) = delete;
    ZipArchive& operator=(const ZipArchive&) = delete;

    ~ZipArchive()
    {
        if (opened_) {
            mz_zip_writer_end(&archive_);
        }
    }

    bool opened() const { return opened_; }

    bool add_file(const std::string& name, const std::string& path)
    {
        return opened_ && mz_zip_writer_add_file(&archive_, name.c_str(), path.c_str(), nullptr, 0, MZ_DEFAULT_COMPRESSION) == MZ_TRUE;
    }

    bool add_text(const std::string& name, const std::string& text)
    {
        return opened_ && mz_zip_writer_add_mem(&archive_, name.c_str(), text.data(), text.size(), MZ_DEFAULT_COMPRESSION) == MZ_TRUE;
    }

    bool finish()
    {
        if (!opened_) {
            return false;
        }
        const bool finalized = mz_zip_writer_finalize_archive(&archive_) == MZ_TRUE;
        mz_zip_writer_end(&archive_);
        opened_ = false;
        return finalized;
    }

private:
    mz_zip_archive archive_{};
    bool opened_{false};
};

// The name of the file a preset was read from; empty when it has none, which
// the dialog skips.
std::string preset_path(const Slic3r::Preset& preset)
{
    return std::filesystem::path(preset.file).make_preferred().string();
}

std::string file_name_of(const Slic3r::Preset& preset)
{
    return std::filesystem::path(preset.file).filename().string();
}

// save_presets_to_zip(): every preset as <name>.json of one archive.
bool save_presets_to_zip(const std::string& export_file, const std::vector<const Slic3r::Preset*>& presets)
{
    ZipArchive archive(export_file);
    if (!archive.opened()) {
        return false;
    }
    for (const Slic3r::Preset* preset : presets) {
        const std::string path = preset_path(*preset);
        if (path.empty()) {
            continue;
        }
        if (!archive.add_file(preset->name + ".json", path)) {
            return false;
        }
    }
    return archive.finish();
}

// The head of a bundle's bundle_structure.json. The desktop app puts the id of
// the signed-in user in the bundle id; the app signs in to nothing, which is
// the "offline" the desktop app writes then.
nlohmann::json bundle_head(const std::string& type, const std::string& name)
{
    nlohmann::json bundle_structure;
    bundle_structure["version"] = "";
    bundle_structure["bundle_id"] = "offline_" + name + "_" + current_timestamp();
    bundle_structure["bundle_type"] = type;
    return bundle_structure;
}

}  // namespace

ConfigExportOptions config_export_options(const ConfigExportKind kind)
{
    ConfigExportOptions result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        const ExportData data = gather(*engine().bundle);
        // select_curr_radiobox(): what the chosen kind lists.
        switch (kind) {
        case ConfigExportKind::printer_bundle:
            for (const auto& [name, preset] : data.printer_presets) {
                // printer preset mast have user's filament or process preset or printer preset is user preset
                if (data.filament_presets.count(name) == 0 && data.process_presets.count(name) == 0 && preset.is_system) {
                    continue;
                }
                const auto filaments = data.filament_presets.find(name);
                const auto processes = data.process_presets.find(name);
                ConfigExportEntry& entry = result.entries.emplace_back();
                entry.name = name;
                entry.count = std::int64_t(1 + (filaments == data.filament_presets.end() ? 0 : filaments->second.size()) +
                                           (processes == data.process_presets.end() ? 0 : processes->second.size()));
            }
            result.note = "Only display printer names with changes to printer, filament, and process presets.";
            break;
        case ConfigExportKind::filament_bundle:
        case ConfigExportKind::filament_presets:
            for (const auto& [name, presets] : data.filament_name_to_presets) {
                if (presets.empty()) {
                    continue;
                }
                ConfigExportEntry& entry = result.entries.emplace_back();
                entry.name = name;
                entry.count = std::int64_t(presets.size());
            }
            result.note = kind == ConfigExportKind::filament_bundle ?
                              "Only display the filament names with changes to filament presets." :
                              "Only the filament names with user filament presets will be displayed, \n"
                              "and all user filament presets in each filament name you select will be exported as a zip.";
            break;
        case ConfigExportKind::printer_presets:
            for (const auto& [name, preset] : data.printer_presets) {
                if (preset.is_system) {
                    continue;
                }
                ConfigExportEntry& entry = result.entries.emplace_back();
                entry.name = name;
                entry.count = 1;
            }
            result.note = "Only printer names with user printer presets will be displayed, and each preset you choose will be exported as a zip.";
            break;
        case ConfigExportKind::process_presets:
            for (const auto& [name, presets] : data.process_presets) {
                // The processes of a system printer that is a base preset.
                if (data.base_system_printers.count(name) == 0) {
                    continue;
                }
                if (presets.empty()) {
                    continue;
                }
                ConfigExportEntry& entry = result.entries.emplace_back();
                entry.name = name;
                entry.count = std::int64_t(presets.size());
            }
            result.note = "Only printer names with changed process presets will be displayed, \n"
                          "and all user process presets in each printer name you select will be exported as a zip.";
            break;
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::write_failed;
        result.message = error.what();
    }
    return result;
}

ConfigTransfer export_configs(const ConfigExportKind kind, const std::vector<std::string>& names, const std::string& directory)
{
    ConfigTransfer result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    if (names.empty()) {
        result.message = "Please select at least one printer or filament.";
        return result;
    }
    try {
        follow_config(engine());
        const ExportData data = gather(*engine().bundle);
        const std::set<std::string> chosen(names.begin(), names.end());
        std::error_code error;
        std::filesystem::create_directories(directory, error);
        const std::filesystem::path folder(directory);

        switch (kind) {
        case ConfigExportKind::printer_bundle: {
            // archive_preset_bundle_to_file()
            for (const auto& [name, printer_preset] : data.printer_presets) {
                if (chosen.count(name) == 0) {
                    continue;
                }
                const std::string path = preset_path(printer_preset);
                if (path.empty()) {
                    continue;
                }
                nlohmann::json bundle_structure = bundle_head("printer config bundle", name);
                bundle_structure["printer_preset_name"] = name;
                nlohmann::json printer_config = nlohmann::json::array();
                nlohmann::json filament_configs = nlohmann::json::array();
                nlohmann::json process_configs = nlohmann::json::array();

                const std::string file = (folder / (name + ".orca_printer")).string();
                ZipArchive archive(file);
                if (!archive.opened()) {
                    result.status = SceneStatus::write_failed;
                    result.message = "Failed to initialize ZIP archive";
                    return result;
                }
                const std::string printer_config_file_name = "printer/" + file_name_of(printer_preset);
                if (!archive.add_file(printer_config_file_name, path)) {
                    result.status = SceneStatus::write_failed;
                    result.message = name + ": failed to add file to ZIP archive";
                    return result;
                }
                printer_config.push_back(printer_config_file_name);

                const auto filaments = data.filament_presets.find(name);
                if (filaments != data.filament_presets.end()) {
                    for (const Slic3r::Preset& preset : filaments->second) {
                        const std::string filament_path = preset_path(preset);
                        if (filament_path.empty()) {
                            continue;
                        }
                        const std::string filament_config_file_name = "filament/" + file_name_of(preset);
                        if (!archive.add_file(filament_config_file_name, filament_path)) {
                            result.status = SceneStatus::write_failed;
                            result.message = preset.name + ": failed to add file to ZIP archive";
                            return result;
                        }
                        filament_configs.push_back(filament_config_file_name);
                    }
                }

                const auto processes = data.process_presets.find(name);
                if (processes != data.process_presets.end()) {
                    for (const Slic3r::Preset& preset : processes->second) {
                        const std::string process_path = preset_path(preset);
                        if (process_path.empty()) {
                            continue;
                        }
                        const std::string process_config_file_name = "process/" + file_name_of(preset);
                        if (!archive.add_file(process_config_file_name, process_path)) {
                            result.status = SceneStatus::write_failed;
                            result.message = preset.name + ": failed to add file to ZIP archive";
                            return result;
                        }
                        process_configs.push_back(process_config_file_name);
                    }
                }

                bundle_structure["printer_config"] = printer_config;
                bundle_structure["filament_config"] = filament_configs;
                bundle_structure["process_config"] = process_configs;
                if (!archive.add_text(BUNDLE_STRUCTURE_JSON_NAME, bundle_structure.dump()) || !archive.finish()) {
                    result.status = SceneStatus::write_failed;
                    result.message = "Failed to finalize ZIP archive";
                    return result;
                }
                result.names.push_back(file);
            }
            break;
        }
        case ConfigExportKind::filament_bundle: {
            // archive_filament_bundle_to_file()
            for (const auto& [name, presets] : data.filament_name_to_presets) {
                if (chosen.count(name) == 0) {
                    continue;
                }
                nlohmann::json bundle_structure = bundle_head("filament config bundle", name);
                bundle_structure["filament_name"] = name;
                std::map<std::string, nlohmann::json> vendor_structure;

                const std::string file = (folder / (name + ".orca_filament")).string();
                ZipArchive archive(file);
                if (!archive.opened()) {
                    result.status = SceneStatus::write_failed;
                    result.message = "Failed to initialize ZIP archive";
                    return result;
                }
                std::set<std::pair<std::string, std::string>> vendor_to_filament_name;
                for (const auto& [printer_vendor, filament_preset] : presets) {
                    if (printer_vendor.empty()) {
                        continue;
                    }
                    if (vendor_to_filament_name.count({printer_vendor, filament_preset.name}) != 0) {
                        continue;
                    }
                    vendor_to_filament_name.insert({printer_vendor, filament_preset.name});
                    const std::string path = preset_path(filament_preset);
                    if (path.empty()) {
                        continue;
                    }
                    const std::string file_name = printer_vendor + "/" + filament_preset.name + ".json";
                    if (!archive.add_file(file_name, path)) {
                        result.status = SceneStatus::write_failed;
                        result.message = filament_preset.name + ": failed to add file to ZIP archive";
                        return result;
                    }
                    vendor_structure[printer_vendor].push_back(file_name);
                }
                for (const auto& [vendor, files] : vendor_structure) {
                    bundle_structure[vendor] = files;
                }
                if (!archive.add_text(BUNDLE_STRUCTURE_JSON_NAME, bundle_structure.dump()) || !archive.finish()) {
                    result.status = SceneStatus::write_failed;
                    result.message = "Failed to finalize ZIP archive";
                    return result;
                }
                result.names.push_back(file);
            }
            break;
        }
        case ConfigExportKind::printer_presets: {
            // archive_printer_preset_to_file(): one archive of the chosen presets.
            std::vector<const Slic3r::Preset*> presets;
            for (const auto& [name, preset] : data.printer_presets) {
                if (chosen.count(name) != 0) {
                    presets.push_back(&preset);
                }
            }
            const std::string file = (folder / "Printer presets.zip").string();
            if (!save_presets_to_zip(file, presets)) {
                result.status = SceneStatus::write_failed;
                result.message = "Failed to write ZIP archive";
                return result;
            }
            result.names.push_back(file);
            break;
        }
        case ConfigExportKind::filament_presets: {
            // archive_filament_preset_to_file()
            std::vector<const Slic3r::Preset*> presets;
            std::set<std::string> written;
            for (const auto& [name, filaments] : data.filament_name_to_presets) {
                if (chosen.count(name) == 0) {
                    continue;
                }
                for (const auto& [printer_vendor, preset] : filaments) {
                    if (written.count(preset.name) != 0) {
                        continue;
                    }
                    written.insert(preset.name);
                    presets.push_back(&preset);
                }
            }
            const std::string file = (folder / "Filament presets.zip").string();
            if (!save_presets_to_zip(file, presets)) {
                result.status = SceneStatus::write_failed;
                result.message = "Failed to write ZIP archive";
                return result;
            }
            result.names.push_back(file);
            break;
        }
        case ConfigExportKind::process_presets: {
            // archive_process_preset_to_file()
            std::vector<const Slic3r::Preset*> presets;
            std::set<std::string> written;
            for (const auto& [name, processes] : data.process_presets) {
                if (chosen.count(name) == 0) {
                    continue;
                }
                for (const Slic3r::Preset& preset : processes) {
                    if (written.count(preset.name) != 0) {
                        continue;
                    }
                    written.insert(preset.name);
                    presets.push_back(&preset);
                }
            }
            const std::string file = (folder / "Process presets.zip").string();
            if (!save_presets_to_zip(file, presets)) {
                result.status = SceneStatus::write_failed;
                result.message = "Failed to write ZIP archive";
                return result;
            }
            result.names.push_back(file);
            break;
        }
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::write_failed;
        result.message = error.what();
    }
    return result;
}

}  // namespace orcinus::orca
