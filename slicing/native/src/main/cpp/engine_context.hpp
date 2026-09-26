#pragma once

#include <memory>
#include <mutex>

#include "libslic3r/BoundingBox.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "orca_engine_adapter.hpp"

namespace Slic3r {
class AppConfig;
class Model;
class ModelObject;
class PresetBundle;
struct Calib_Params;
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
    // PartPlateList's current plate and the number of plates, which place it
    // among them (compute_origin): the requests judge objects by it and slice it.
    int plate_index = 0;
    int plate_count = 1;
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

// The centre of the current plate's bed, where a new object stands on an empty plate.
Slic3r::Vec2d bed_center(const Slic3r::DynamicPrintConfig& config);

// The objects written as import_model() writes them into result.
bool write_objects(const std::vector<Slic3r::ModelObject*>& objects, const std::string& output_prefix, ImportedModels& result);

// Calib_Params of the calibration.
Slic3r::Calib_Params calib_params(const CalibrationParams& params);

// PartPlateList::find_instance(): the first of count plates the copy at
// instance of object crosses; -1 for a copy on none.
int plate_of(const Slic3r::ModelObject& object, std::size_t instance, const Slic3r::DynamicPrintConfig& config, int count);

// PartPlate's obj_to_instance_set: the copies of model that stand on the
// current plate (PartPlate::intersect_instance()) are kept, the others and the
// objects left without copies removed.
void keep_current_plate(Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config);

// Writes a mesh for the 3D view, in the format :render:scene reads
// (mesh_file_magic of orca_engine_adapter.hpp).
bool write_mesh(const indexed_triangle_set& its, const std::string& path);

// AppConfig::save() refuses to run on any thread but the one the app considers
// its main thread; the engine's calls run on the service's binder threads, one
// at a time under the engine's mutex, so the calling thread is recorded first.
void save_config(EngineContext& context);

}  // namespace orcinus::orca::detail
