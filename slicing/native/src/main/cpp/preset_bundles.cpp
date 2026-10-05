#include "orca_engine_adapter.hpp"

// OrcaSlicer's PresetBundleDialog: the preset bundles the user has (the
// imported ones of the _local folder and those OrcaCloud keeps in
// _subscribed), as ListBundles() tells the page, and the context menu's
// "Delete bundle" (DeleteBundleById()).

#include <filesystem>
#include <unordered_map>

#include <boost/filesystem.hpp>

#include "engine_context.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"

namespace orcinus::orca {

using detail::engine;
using detail::follow_config;

namespace {

// ListBundles()' strip_prefix: a name's last path part.
std::vector<std::string> strip_prefix(const std::vector<std::string>& names)
{
    std::vector<std::string> stripped;
    stripped.reserve(names.size());
    for (const std::string& name : names) {
        stripped.push_back(boost::filesystem::path(name).filename().string());
    }
    return stripped;
}

// RefreshBundleMap() and ListBundles(): every bundle of the map, in its order.
std::vector<PresetBundleEntry> list_bundles(Slic3r::PresetBundle& bundle)
{
    bundle.bundles.ReadLock();
    const std::unordered_map<std::string, Slic3r::BundleMetadata> bundle_copy = bundle.bundles.m_bundles;
    bundle.bundles.ReadUnlock();
    std::vector<PresetBundleEntry> entries;
    for (const auto& [id, metadata] : bundle_copy) {
        PresetBundleEntry entry;
        entry.id = metadata.id;
        entry.name = metadata.name;
        entry.type = metadata.bundle_type == Slic3r::Subscribed ? PresetBundleType::subscribed
                     : metadata.bundle_type == Slic3r::Local    ? PresetBundleType::local
                                                                : PresetBundleType::default_type;
        entry.version = metadata.version;
        entry.printers = strip_prefix(metadata.printer_presets);
        entry.filaments = strip_prefix(metadata.filament_presets);
        entry.processes = strip_prefix(metadata.print_presets);
        entry.update_available = metadata.update_available;
        entry.unauthorized = metadata.unauthorized;
        entries.push_back(std::move(entry));
    }
    return entries;
}

// PresetBundleDialog::DeleteBundleById()
bool delete_bundle_by_id(Slic3r::PresetBundle& b, const std::string& bundle_id)
{
    if (bundle_id.empty()) {
        return false;
    }
    b.bundles.ReadLock();
    auto it = b.bundles.m_bundles.find(bundle_id);
    if (it == b.bundles.m_bundles.end()) {
        b.bundles.ReadUnlock();
        return false;
    }
    const std::string metadata_path = it->second.path;
    const boost::filesystem::path bundle_dir = boost::filesystem::path(metadata_path).parent_path();
    b.bundles.ReadUnlock();

    b.bundles.WriteLock();
    b.bundles.m_bundles.erase(bundle_id);
    b.bundles.WriteUnlock();

    // A bundle's presets cannot be overwritten, so delete_preset() leaves
    // them; the presets loaded anew once the folder is gone take them away.
    auto remove_from_collection = [&](Slic3r::PresetCollection& c) {
        std::vector<std::string> to_delete;
        for (const auto& p : c.get_presets()) {
            if (p.bundle_id == bundle_id)
                to_delete.push_back(p.name);
        }
        for (const auto& name : to_delete)
            c.delete_preset(name);
    };
    remove_from_collection(b.prints);
    remove_from_collection(b.filaments);
    remove_from_collection(b.printers);

    boost::system::error_code ec;
    if (!bundle_dir.empty() && boost::filesystem::exists(bundle_dir))
        boost::filesystem::remove_all(bundle_dir, ec);

    b.update_compatible(Slic3r::PresetSelectCompatibleType::Always);
    return true;
}

}  // namespace

PresetBundles preset_bundles()
{
    PresetBundles result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    follow_config(engine());
    result.bundles = list_bundles(*engine().bundle);
    result.status = SceneStatus::success;
    return result;
}

PresetBundles delete_preset_bundle(const std::string& id)
{
    PresetBundles result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        follow_config(engine());
        Slic3r::PresetBundle& bundle = *engine().bundle;
        if (!delete_bundle_by_id(bundle, id)) {
            result.status = SceneStatus::write_failed;
            result.message = "Failed to remove bundle.";
            return result;
        }
        // OnFSWatch(): the dialog watches the user's bundle folders, and their
        // change loads the presets anew.
        bundle.load_presets(*engine().config, Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilentDisableSystem);
        bundle.export_selections(*engine().config);
        detail::save_config(engine());
        result.bundles = list_bundles(bundle);
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::write_failed;
        result.message = error.what();
    }
    return result;
}

}  // namespace orcinus::orca
