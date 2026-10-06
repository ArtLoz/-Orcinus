#include "orca_engine_adapter.hpp"

// OrcaSlicer's PresetUpdater for the system profiles of a vendor
// (sync_vendor_config(), get_config_updates() and perform_updates()). The app
// asks the server and downloads the bundle the answer names; the engine tells
// what to ask, keeps the bundle in its cache (data_dir/ota/profiles) and
// installs from there the bundles newer than the installed ones.

#include <functional>
#include <sstream>

#include <boost/filesystem.hpp>
#include <boost/log/trivial.hpp>
#include <boost/nowide/fstream.hpp>
#include <nlohmann/json.hpp>

#include "engine_context.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/Exception.hpp"
#include "libslic3r/I18N.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Semver.hpp"
#include "libslic3r/Utils.hpp"
#include "libslic3r/format.hpp"
#include "libslic3r/miniz_extension.hpp"
#include "libslic3r_version.h"

namespace orcinus::orca {

using detail::engine;
using detail::follow_config;

namespace {

namespace fs = boost::filesystem;

// TMP_EXTENSION: the download of a bundle, until it is unpacked.
constexpr const char* tmp_extension = ".data";

// PresetUpdater::priv's cache_path and vendor_path.
fs::path cache_path()
{
    return fs::path(Slic3r::data_dir()) / "ota";
}

fs::path vendor_path()
{
    return fs::path(Slic3r::data_dir()) / PRESET_SYSTEM_DIR;
}

// Http::url_encode(): curl_easy_escape(), which keeps the unreserved
// characters of RFC 3986 and writes every other byte as %XX.
std::string url_encode(const std::string& text)
{
    static constexpr char hex[] = "0123456789ABCDEF";
    std::string encoded;
    for (const char c : text) {
        const auto byte = static_cast<unsigned char>(c);
        if ((byte >= 'A' && byte <= 'Z') || (byte >= 'a' && byte <= 'z') || (byte >= '0' && byte <= '9') || byte == '-' || byte == '.' || byte == '_' ||
            byte == '~') {
            encoded += c;
        } else {
            encoded += '%';
            encoded += hex[byte >> 4];
            encoded += hex[byte & 0x0F];
        }
    }
    return encoded;
}

// copy_file_fix()
void copy_file_fix(const fs::path& source, const fs::path& target)
{
    BOOST_LOG_TRIVIAL(debug) << Slic3r::format("PresetUpdater: Copying %1% -> %2%", source, target);
    std::string error_message;
    const Slic3r::CopyFileResult cfr = Slic3r::copy_file(source.string(), target.string(), error_message, false);
    if (cfr != Slic3r::CopyFileResult::SUCCESS) {
        BOOST_LOG_TRIVIAL(error) << "Copying failed(" << cfr << "): " << error_message;
        throw Slic3r::CriticalException(Slic3r::format(_u8L("Copying of file %1% to %2% failed: %3%"), source, target, error_message));
    }
    // Permissions should be copied from the source file by copy_file(). We are not sure about the source
    // permissions, let's rewrite them with 644.
    static constexpr const auto perms = fs::owner_read | fs::owner_write | fs::group_read | fs::others_read;
    fs::permissions(target, perms);
}

// PresetUpdater's Update: a vendor's json file, or its folder, of the cache
// that replaces the installed one.
struct Update {
    fs::path source;
    fs::path target;
    Slic3r::Semver version;
    std::string vendor;
    std::string change_log;
    bool forced_update{false};
    bool is_directory{false};

    void install() const
    {
        if (is_directory) {
            Slic3r::copy_directory_recursively(source, target);
        } else {
            copy_file_fix(source, target);
        }
    }
};

// PresetUpdater::priv::prune_tmps(): leftover partially downloaded files are removed.
void prune_tmps()
{
    boost::system::error_code ec;
    if (!fs::exists(cache_path(), ec)) {
        return;
    }
    for (const fs::directory_entry& dir_entry : fs::directory_iterator(cache_path())) {
        if (Slic3r::is_plain_file(dir_entry) && dir_entry.path().extension() == tmp_extension) {
            BOOST_LOG_TRIVIAL(debug) << "[Orca Updater]remove old cached files: " << dir_entry.path().string();
            fs::remove(dir_entry.path(), ec);
        }
    }
}

// An entry of the bundle that would land outside the folder it is unpacked
// into: an absolute name, or one that climbs out with "..". The desktop app
// unpacks every name as it is.
bool escapes(const std::string& name)
{
    const fs::path path(name);
    if (path.has_root_path()) {
        return true;
    }
    for (const fs::path& part : path) {
        if (part == "..") {
            return true;
        }
    }
    return false;
}

// PresetUpdater::priv::extract_file()
bool extract_file(const fs::path& source_path, const fs::path& dest_path)
{
    bool res = true;
    const std::string file_path = source_path.string();
    const std::string parent_path = dest_path.string();
    mz_zip_archive archive;
    mz_zip_zero_struct(&archive);

    if (!Slic3r::open_zip_reader(&archive, file_path)) {
        BOOST_LOG_TRIVIAL(error) << "Unable to open zip reader for " << file_path;
        return false;
    }

    const mz_uint num_entries = mz_zip_reader_get_num_files(&archive);

    mz_zip_archive_file_stat stat;
    for (mz_uint i = 0; i < num_entries; ++i) {
        if (mz_zip_reader_file_stat(&archive, i, &stat)) {
            if (escapes(stat.m_filename)) {
                BOOST_LOG_TRIVIAL(warning) << "[Orca Updater]Unzip: skipped the entry outside the cache " << stat.m_filename;
                continue;
            }
            const std::string dest_file = parent_path + "/" + stat.m_filename;
            if (stat.m_is_directory) {
                const fs::path dest_dir(dest_file);
                if (!fs::exists(dest_dir)) {
                    fs::create_directories(dest_dir);
                }
                continue;
            } else if (stat.m_uncomp_size == 0) {
                BOOST_LOG_TRIVIAL(warning) << "[Orca Updater]Unzip: invalid size for file " << stat.m_filename;
                continue;
            }
            try {
                res = mz_zip_reader_extract_to_file(&archive, stat.m_file_index, dest_file.c_str(), 0);
                if (!res) {
                    BOOST_LOG_TRIVIAL(error) << "[Orca Updater]extract file " << stat.m_filename << " to dest " << dest_file << " failed";
                    Slic3r::close_zip_reader(&archive);
                    return res;
                }
                BOOST_LOG_TRIVIAL(info) << "[Orca Updater]successfully extract file " << stat.m_file_index << " to " << dest_file;
            } catch (const std::exception& e) {
                // ensure the zip archive is closed and rethrow the exception
                Slic3r::close_zip_reader(&archive);
                BOOST_LOG_TRIVIAL(error) << "[Orca Updater]Archive read exception:" << e.what();
                return false;
            }
        } else {
            BOOST_LOG_TRIVIAL(warning) << "[Orca Updater]Unzip: read file stat failed";
        }
    }
    Slic3r::close_zip_reader(&archive);

    return true;
}

// PresetUpdater::priv::get_config_updates(): OTA profile updates are located
// in the ota/profiles folder.
std::vector<Update> get_config_updates()
{
    std::vector<Update> updates;

    BOOST_LOG_TRIVIAL(info) << "[Orca Updater]:Checking for cached configuration updates...";
    const fs::path cache_profile_path = cache_path() / "profiles";
    if (!fs::exists(cache_profile_path)) {
        return updates;
    }

    for (const fs::directory_entry& dir_entry : fs::directory_iterator(cache_profile_path)) {
        const fs::path& path = dir_entry.path();
        const std::string file_path = path.string();
        if (!Slic3r::is_json_file(file_path)) {
            continue;
        }
        const fs::path path_in_vendor = vendor_path() / path.filename();
        std::string vendor_name = path.filename().string();
        // Remove the .json suffix.
        vendor_name.erase(vendor_name.size() - 5);
        const fs::path print_in_cache = cache_profile_path / vendor_name / PRESET_PRINT_NAME;
        const fs::path filament_in_cache = cache_profile_path / vendor_name / PRESET_FILAMENT_NAME;
        const fs::path machine_in_cache = cache_profile_path / vendor_name / PRESET_PRINTER_NAME;

        if (fs::exists(path_in_vendor) || fs::exists(print_in_cache) || fs::exists(filament_in_cache) || fs::exists(machine_in_cache)) {
            const Slic3r::Semver vendor_ver = Slic3r::get_version_from_json(path_in_vendor.string());

            std::map<std::string, std::string> key_values;
            std::vector<std::string> keys(3);
            Slic3r::Semver cache_ver;
            keys[0] = BBL_JSON_KEY_VERSION;
            keys[1] = BBL_JSON_KEY_DESCRIPTION;
            keys[2] = BBL_JSON_KEY_FORCE_UPDATE;
            Slic3r::get_values_from_json(file_path, keys, key_values);
            bool force_update = false;
            if (key_values.find(BBL_JSON_KEY_FORCE_UPDATE) != key_values.end()) {
                force_update = key_values[BBL_JSON_KEY_FORCE_UPDATE] == "1";
            }
            if (const auto config_version = Slic3r::Semver::parse(key_values[BBL_JSON_KEY_VERSION])) {
                cache_ver = *config_version;
            }

            std::string changelog;
            const std::string changelog_file = (cache_profile_path / (vendor_name + ".changelog")).string();
            boost::nowide::ifstream ifs(changelog_file);
            if (ifs) {
                std::ostringstream oss;
                oss << ifs.rdbuf();
                changelog = oss.str();
                ifs.close();
            }

            if (vendor_ver < cache_ver) {
                BOOST_LOG_TRIVIAL(info) << "[Orca Updater]:need to update settings from " << vendor_ver.to_string() << " to newer version "
                                        << cache_ver.to_string() << ", app version " << SLIC3R_VERSION;
                // Orca: update vendor.json
                Update json;
                json.source = path;
                json.target = path_in_vendor;
                json.version = cache_ver;
                json.vendor = vendor_name;
                json.change_log = changelog;
                json.forced_update = force_update;
                updates.push_back(std::move(json));
                // Orca: update vendor folder
                Update folder;
                folder.source = cache_profile_path / vendor_name;
                folder.target = vendor_path() / vendor_name;
                folder.vendor = vendor_name;
                folder.forced_update = force_update;
                folder.is_directory = true;
                updates.push_back(std::move(folder));
            } else {
                BOOST_LOG_TRIVIAL(info) << "[Orca Updater]:cached settings for " << vendor_name
                                        << " are not newer than installed version, installed " << vendor_ver.to_string() << ", cached "
                                        << cache_ver.to_string();
            }
        }
    }

    return updates;
}

}  // namespace

ProfileUpdateRequest profile_update_request(const bool startup)
{
    ProfileUpdateRequest result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr || engine().config == nullptr) {
        return result;
    }
    try {
        follow_config(engine());
        Slic3r::AppConfig& config = *engine().config;
        if (startup) {
            prune_tmps();
        }
        // GUI_App::on_init_inner(): the updater syncs unless in stealth mode,
        // and asks about the active vendor with sync_system_preset.
        result.enabled = !config.get_stealth_mode() && config.get("sync_system_preset") == "true";
        const Slic3r::Preset& printer = engine().bundle->printers.get_edited_preset();
        if (printer.vendor != nullptr) {
            result.vendor = printer.vendor->id;
        }
        // checked_vendors: a vendor is asked about once while the app runs.
        if (!result.enabled || result.vendor.empty() || !engine().checked_profile_vendors.insert(result.vendor).second) {
            return result;
        }
        BOOST_LOG_TRIVIAL(info) << "[Orca Updater] checking vendor update for " << result.vendor;
        result.url = config.profile_update_url() + "?vendor=" + url_encode(result.vendor) + "&orca_version=" + url_encode(SoftFever_VERSION);
    } catch (const std::exception& error) {
        BOOST_LOG_TRIVIAL(error) << "[Orca Updater] vendor update failed: " << error.what();
        result.url.clear();
    }
    return result;
}

ProfileDownload profile_update_answer(const std::string& vendor_id, const int http_status, const std::string& body, const std::string& error)
{
    ProfileDownload result;
    {
        const std::lock_guard<std::mutex> engine_lock(engine().mutex);
        if (engine().bundle == nullptr) {
            return result;
        }
    }
    std::string online_version_str;  // this represents the PROFILE VERSION, not ORCA VERSION
    std::string download_url_str;
    if (http_status == 0 || http_status >= 400) {
        BOOST_LOG_TRIVIAL(warning) << "[Orca Updater] vendor check HTTP error for " << vendor_id << ": " << error;
    } else if (http_status == 200) {
        try {
            const nlohmann::json j = nlohmann::json::parse(body);
            if (j.contains("vendor_version") && j.contains("download_url")) {
                online_version_str = j.at("vendor_version").get<std::string>();
                download_url_str = j.at("download_url").get<std::string>();
            }
        } catch (const std::exception& e) {
            BOOST_LOG_TRIVIAL(warning) << "[Orca Updater] vendor check JSON parse failed: " << e.what();
        }
    }
    if (online_version_str.empty() || download_url_str.empty()) {
        BOOST_LOG_TRIVIAL(info) << "[Orca Updater] no update available for vendor " << vendor_id;
        return result;
    }
    try {
        // Clear only this vendor's cached data
        const fs::path cache_profile_path = cache_path() / "profiles";
        fs::create_directories(cache_profile_path);
        boost::system::error_code ec;
        fs::remove_all(cache_profile_path / vendor_id, ec);
        fs::remove(cache_profile_path / (vendor_id + ".json"), ec);
        fs::remove(cache_profile_path / (vendor_id + ".changelog"), ec);
    } catch (const std::exception& e) {
        BOOST_LOG_TRIVIAL(error) << "[Orca Updater] vendor update failed for " << vendor_id << ": " << e.what();
        return result;
    }
    BOOST_LOG_TRIVIAL(info) << "[Orca Updater] downloading update for " << vendor_id << " version " << online_version_str;
    result.url = download_url_str;
    result.path = (cache_path() / (vendor_id + tmp_extension)).string();
    return result;
}

bool cache_profile_update(const std::string& vendor_id)
{
    {
        const std::lock_guard<std::mutex> engine_lock(engine().mutex);
        if (engine().bundle == nullptr) {
            return false;
        }
    }
    // Extract vendor profile bundles under ota/profiles. The downloaded zip contains
    // the vendor json/folder at its root.
    BOOST_LOG_TRIVIAL(info) << "[Orca Updater] extracting update for " << vendor_id;
    const fs::path download_file = cache_path() / (vendor_id + tmp_extension);
    try {
        if (!extract_file(download_file, cache_path() / "profiles")) {
            BOOST_LOG_TRIVIAL(warning) << "[Orca Updater] extraction failed for " << vendor_id;
            return false;
        }
    } catch (const std::exception& e) {
        BOOST_LOG_TRIVIAL(warning) << "[Orca Updater] extraction failed for " << vendor_id << ": " << e.what();
        return false;
    }
    boost::system::error_code ec;
    fs::remove(download_file, ec);
    BOOST_LOG_TRIVIAL(info) << "[Orca Updater] vendor " << vendor_id << " update cached, notifying UI";
    return true;
}

std::vector<ProfileUpdate> profile_updates()
{
    std::vector<ProfileUpdate> result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return result;
    }
    try {
        for (const Update& update : get_config_updates()) {
            // MsgUpdateConfig lists the json files, not the folders.
            if (update.is_directory) {
                continue;
            }
            result.push_back(ProfileUpdate{update.vendor, update.version.to_string(), update.change_log, update.forced_update});
        }
        if (result.empty()) {
            BOOST_LOG_TRIVIAL(info) << "[Orca Updater]:No configuration updates available.";
        }
    } catch (const std::exception& error) {
        BOOST_LOG_TRIVIAL(error) << "[Orca Updater] checking the cached updates failed: " << error.what();
        result.clear();
    }
    return result;
}

bool perform_profile_updates()
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return false;
    }
    try {
        const std::vector<Update> updates = get_config_updates();
        BOOST_LOG_TRIVIAL(info) << Slic3r::format("[Orca Updater]:Performing %1% updates", updates.size());
        for (const Update& update : updates) {
            BOOST_LOG_TRIVIAL(info) << "\tUpdate(" << update.source.string() << " -> " << update.target.string() << ')';
            update.install();
        }
        return true;
    } catch (const std::exception& error) {
        BOOST_LOG_TRIVIAL(error) << "[Orca Updater]:perform_updates failed: " << error.what();
        return false;
    }
}

}  // namespace orcinus::orca
