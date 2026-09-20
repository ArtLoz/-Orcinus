#pragma once

#include <memory>
#include <mutex>

#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "orca_engine_adapter.hpp"

namespace Slic3r {
class AppConfig;
class Model;
class PresetBundle;
}

namespace orcinus::orca::detail {

// The engine's state for the life of the process, shared by the parts of the
// adapter. Every access holds mutex; the engine is ready once bundle is set.
struct EngineContext {
    std::mutex mutex;
    std::unique_ptr<Slic3r::PresetBundle> bundle;
    // OrcaSlicer.conf of the data directory, and whether it existed when the
    // engine started (GUI_App::m_app_conf_exists).
    std::unique_ptr<Slic3r::AppConfig> config;
    bool config_existed = false;
    // Whether bundle shows the presets config installs and the selection it
    // remembers; a request for other presets may select others (select_profiles).
    bool bundle_follows_config = false;
};

EngineContext& engine();

// Shows the selection the app configuration remembers again after a request
// selected other presets.
void follow_config(EngineContext& context);

// The ModelConfig of an object or of the plate, as the app keeps it: the keys
// it overrides, with the values OrcaSlicer writes into a project. A key the
// engine no longer knows is left behind, as loading an older project leaves it.
Slic3r::DynamicPrintConfig model_config(const ModelSettings& settings);

// The configuration of the presets a request names, selected on a copy of the
// app configuration when they are not the current selection (select_profiles of
// the adapter).
SliceStatus select_profiles(
    Slic3r::PresetBundle& bundle,
    const ProfileSelection& profiles,
    Slic3r::DynamicPrintConfig& config,
    std::string& message
);

// The objects of the plate loaded into model, with their parts, settings and
// height ranges, as the adapter loads them for slicing.
bool load_plate(
    const std::vector<PlateObject>& plate,
    const Slic3r::DynamicPrintConfig& config,
    Slic3r::Model& model,
    std::string& message
);

// Writes a mesh for the 3D view, in the format :render:scene reads
// (mesh_file_magic of orca_engine_adapter.hpp).
bool write_mesh(const indexed_triangle_set& its, const std::string& path);

// AppConfig::save() refuses to run on any thread but the one the app considers
// its main thread; the engine's calls run on the service's binder threads, one
// at a time under the engine's mutex, so the calling thread is recorded first.
void save_config(EngineContext& context);

}  // namespace orcinus::orca::detail
