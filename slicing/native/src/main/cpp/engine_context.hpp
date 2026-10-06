#pragma once

#include <memory>
#include <mutex>
#include <set>

#include "libslic3r/BoundingBox.hpp"
#include "libslic3r/BuildVolume.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "orca_engine_adapter.hpp"

#include "libslic3r/CutUtils.hpp"

namespace Slic3r {
class AppConfig;
class Model;
class ModelInstance;
class ModelObject;
class ModelVolume;
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
    // The printer the project took its plate type from (curr_bed_type of the
    // project configuration), as the sidebar's plate type combo box shows it:
    // another printer makes it take that printer's (update_all_preset_comboboxes),
    // a loaded project brings its own.
    std::string bed_type_printer;
    // Sidebar's editing_filament: the slot the filament tab edits, which takes
    // the presets the tab selects; -1 for none.
    int editing_filament = -1;
    // Plater::get_next_color_for_filament()'s curr_color_filamenet.
    std::size_t next_filament_color = 0;
    // GUI_App's m_config_corrupted, until the app says so.
    bool config_corrupted = false;
    // PresetUpdater's checked_vendors: the vendors whose profiles were asked
    // about while the app runs.
    std::set<std::string> checked_profile_vendors;
    // Tab's s_filament_temp_pair_warning_suppressed_for_session.
    std::set<std::string> filament_temperature_warnings_suppressed;
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
// the adapter); apply_extruder false keeps the extruder variants of every
// filament for Print::apply(), as the background process takes them.
SliceStatus select_profiles(
    Slic3r::PresetBundle& bundle,
    const ProfileSelection& profiles,
    Slic3r::DynamicPrintConfig& config,
    std::string& message,
    bool apply_extruder = true
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

// PartPlateList::compute_origin() of the plate at index among count plates.
Slic3r::Vec2d plate_origin(const Slic3r::DynamicPrintConfig& config, int index, int count);

// PartPlate::get_build_volume() of the current plate, which
// Model::update_print_volume_state() judges the copies by.
Slic3r::BuildVolume build_volume_of(const Slic3r::DynamicPrintConfig& config);

// ObjectList::load_shape_object(): a shape of create_mesh() as an object of
// its own named name, in the empty cell nearest to the plate's centre; null
// for an unknown shape.
Slic3r::ModelObject* add_shape_object(Slic3r::Model& model, const std::string& shape, const std::string& name, const Slic3r::DynamicPrintConfig& config);

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

// GLGizmoCut3D::apply_connectors_in_model() and apply_cut_connectors(): the
// connectors of cut become negative volumes of object, turned by the plane's
// rotation_m, before it is cut; dowels_count counts the dowels among them.
void apply_cut_connectors(Slic3r::ModelObject& object, const ObjectCut& cut, const Slic3r::Transform3d& rotation_m, int& dowels_count);

// Cut::Groove of the dovetail cut.
Slic3r::Cut::Groove cut_groove(const CutGroove& groove);

// ModelVolume::CutInfo as the app keeps it, and back.
Slic3r::ModelVolume::CutInfo cut_info_of(const VolumeCutInfo& info);
VolumeCutInfo cut_info_from(const Slic3r::ModelVolume::CutInfo& info);

// The object of GLGizmoCut3D::PartSelection: object cut at its copy instance
// by cut_matrix into the parts of one object, each solid part split into its
// pieces, alone in model.
Slic3r::ModelObject* split_cut_parts(Slic3r::Model& model, const Slic3r::ModelObject& object, int instance, const Slic3r::Transform3d& cut_matrix);

// The text or the SVG volume was embossed from (ModelVolume::text_configuration
// and emboss_shape), written to path; empty when it was embossed from neither
// or the file can't be written.
std::string write_emboss(const Slic3r::ModelVolume& volume, const std::string& path);

// ... read back from path into volume; an empty path reads nothing. False
// when the file can't be read.
bool read_emboss(const std::string& path, Slic3r::ModelVolume& volume);

// ModelVolume::is_text() and is_svg().
EmbossKind emboss_kind_of(const Slic3r::ModelVolume& volume);

// Selection::get_bounding_sphere() of the copy instance of object: the centre
// and radius of the smallest sphere around its volumes' convex hulls in world
// coordinates.
std::pair<Slic3r::Vec3d, double> bounding_sphere(const Slic3r::ModelObject& object, const Slic3r::ModelInstance& instance);

// ... and of volume alone, standing at the world transformation matrix.
std::pair<Slic3r::Vec3d, double> bounding_sphere(const Slic3r::ModelVolume& volume, const Slic3r::Transform3d& matrix);

// The wipe tower of the plate model holds, with config the plate's (its
// wipe_tower_x and wipe_tower_y among it): whether GLCanvas3D::reload_scene()
// would draw it, the filaments the plate prints with
// (PartPlate::get_extruders_under_cli()), the tallest object, its size
// (PartPlate::estimate_wipe_tower_size()) and brim, and where it stands: the
// project's position when placed, else where the desktop app puts a new one.
struct PlateTower {
    bool shown{false};
    std::vector<int> filaments;
    double height{0.0};
    double x{0.0};
    double y{0.0};
    double width{0.0};
    double depth{0.0};
    double brim_width{0.0};
};
PlateTower plate_tower(const Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config, bool placed);

// PartPlate::estimate_wipe_tower_size() for plate_extruder_size filaments, up
// to max_height; the tallest object of model; and
// PartPlateList::set_default_wipe_tower_pos_for_plate()'s position.
Slic3r::Vec3d estimate_wipe_tower_size(const Slic3r::DynamicPrintConfig& config, int plate_extruder_size, double max_height);
double plate_objects_height(const Slic3r::Model& model);
Slic3r::Vec2d default_wipe_tower_position(const Slic3r::DynamicPrintConfig& config, const Slic3r::Vec3d& size, double brim_width);

// An RGBA picture of width x height, rows from the top, written as a PNG to path.
bool write_png_rgba(const std::string& path, int width, int height, const std::vector<unsigned char>& rgba);

// Writes a mesh for the 3D view, in the format :render:scene reads
// (mesh_file_magic of orca_engine_adapter.hpp).
bool write_mesh(const indexed_triangle_set& its, const std::string& path);

// AppConfig::save() refuses to run on any thread but the one the app considers
// its main thread; the engine's calls run on the service's binder threads, one
// at a time under the engine's mutex, so the calling thread is recorded first.
void save_config(EngineContext& context);

}  // namespace orcinus::orca::detail
