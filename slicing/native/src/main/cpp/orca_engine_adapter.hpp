#pragma once

#include <array>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace orcinus::orca {

// Stable boundary between the JNI bridge and OrcaSlicer. Orca types must not
// cross it.

std::string engine_version();

struct EngineDirectories {
    // Orca data directory. Its system/ folder holds the bundled vendor profiles,
    // as the desktop app's data directory does after its first start.
    std::string data_dir;
    // Orca resources directory with info/ and flush/.
    std::string resources_dir;
    std::string temporary_dir;
};

struct EngineInitialization {
    bool ready{false};
    std::string message;
};

// Loads the system profiles once per process. Later calls return the first result.
EngineInitialization initialize(const EngineDirectories& directories);

struct ProfileSelection {
    std::string printer;
    std::string filament;
    std::string process;
};

enum class SliceStatus : std::int64_t {
    success = 0,
    cancelled = 1,
    busy = 2,
    output_write_failed = 3,
    slicing_failed = 4,
    profile_not_found = 5,
    model_read_failed = 6,
    engine_not_ready = 7,
    invalid_print = 8,
};

struct SliceResult {
    SliceStatus status{SliceStatus::slicing_failed};
    std::string message;
    std::int64_t layer_count{0};
    std::int64_t estimated_print_time_seconds{0};
    std::int64_t filament_micrometers{0};
    // The toolpaths file was written for the G-code viewer.
    bool toolpaths_written{false};
};

// Receives Orca's slicing status: percent in [0, 100] and its status text.
using ProgressCallback = std::function<void(int percent, const std::string& message)>;

// Where the user put an object: its instance transformation, column-major
// 4 x 4, and ModelInstance::auto_drop, which lets an object stay above the plate
// when off.
struct ObjectPlacement {
    std::vector<double> matrix;
    bool auto_drop{true};
};

// An object on the plate: the STL file it is loaded from, empty for the
// built-in 20 mm calibration cube, and where it stands. With an empty matrix
// the object is placed as the desktop app places an object added to the plate
// that holds the objects before it.
struct PlateObject {
    std::string model_path;
    ObjectPlacement placement;
};

// Slices the objects of the plate and writes G-code to output_path only after
// a complete export. Placements are the instances as inspect_model() and
// place_model() report them and the user may have changed them; objects
// entirely off the plate are not printed. Unless toolpaths_path is empty, the
// G-code's toolpaths are written there for libvgcode as well
// (toolpaths_file.hpp in :render:gcode).
SliceResult slice(
    const std::string& job_id,
    const std::vector<PlateObject>& objects,
    const std::string& output_path,
    const std::string& toolpaths_path,
    const ProfileSelection& profiles,
    const ProgressCallback& on_progress
);

// Returns true only when job_id is the active job and cancellation was requested.
bool cancel(const std::string& job_id);

// Mesh files written by describe_plate() and inspect_model(), little-endian:
//   char[4]   "OMSH"
//   uint32    format version, 1
//   uint32    vertex count V
//   uint32    triangle count T
//   float32   V x 3 vertex positions, millimetres
//   uint32    T x 3 vertex indices, counter-clockwise seen from outside
inline constexpr char mesh_file_magic[4] = {'O', 'M', 'S', 'H'};
inline constexpr std::uint32_t mesh_file_version = 1;

enum class SceneStatus : std::int64_t {
    success = 0,
    engine_not_ready = 1,
    profile_not_found = 2,
    model_read_failed = 3,
    write_failed = 4,
};

// The plate of the selected printer as desktop OrcaSlicer draws it (Bed3D and
// PartPlate), in millimetres on the plate plane. Files are written into the
// output directory given to describe_plate().
struct PlateDescription {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    // printable_area, counter-clockwise: x0, y0, x1, y1, ...
    std::vector<double> printable_area;
    double printable_height{0.0};
    // Triangulated printable area and excluded area: x, y per vertex.
    std::vector<float> plate_triangles;
    std::vector<float> exclude_triangles;
    // Grid lines clipped to the plate, x1, y1, x2, y2 per line. The thin set
    // includes the plate contour; the bold set is every fifth line.
    std::vector<float> thin_grid_lines;
    std::vector<float> bold_grid_lines;
    // Mesh file of the printer's bed model; empty when the printer has none.
    std::string bed_model_mesh;
    // RGBA PNG of the bed texture; empty when the printer has none.
    std::string bed_texture;
    // Colour of the first filament, "#RRGGBB" as in its profile.
    std::string filament_colour;
};

PlateDescription describe_plate(const ProfileSelection& profiles, const std::string& output_dir);

// Where an object stands relative to the build volume
// (ModelInstanceEPrintVolumeState).
enum class VolumeState : std::int64_t {
    // Inside the build volume, thus printable.
    inside = 0,
    // Across the plate boundary or above the build height: slicing is blocked.
    partly_outside = 1,
    // Entirely off the plate: not printed.
    outside = 2,
};

struct ModelInspection {
    SceneStatus status{SceneStatus::model_read_failed};
    std::string message;
    std::int64_t facet_count{0};
    // Size of the placed object's bounding box, in millimetres.
    double size_x{0.0};
    double size_y{0.0};
    double size_z{0.0};
    // Centre of that bounding box, which scaling keeps in place (Selection's dragging centre).
    std::array<double, 3> box_center{};
    // World transformation of the object's instance, column-major 4 x 4.
    std::array<double, 16> instance_matrix{};
    VolumeState volume_state{VolumeState::inside};
    // The smallest sphere around the placed object (Selection::get_bounding_sphere),
    // which the rotation gizmo turns about.
    std::array<double, 3> sphere_center{};
    double sphere_radius{0.0};
    // The instance rotation in degrees, as the rotation window shows it
    // (Transformation::get_rotation_by_quaternion).
    std::array<double, 3> rotation_degrees{};
    // Size of the object's bounding box without the instance scaling
    // (Selection::get_full_unscaled_instance_bounding_box), which scale ratios refer to.
    std::array<double, 3> unscaled_size{};
};

// How the desktop app commits a manipulation of an object.
enum class Manipulation : std::int64_t {
    // GLCanvas3D::do_move(): an object above the plate drops onto it.
    move = 0,
    // GLCanvas3D::do_rotate(): an object that was not sunk into the plate rests on it.
    rotate = 1,
    // GLCanvas3D::do_scale(): as do_rotate().
    scale = 2,
    // GizmoObjectManipulation::reset_rotation_value(false): no rotation, same
    // offset and scale; then do_rotate().
    reset_rotation = 3,
    // GLGizmoFlatten: the face with face_normal turns down (Selection::flattening_rotate)
    // and the object rests on the plate.
    lay_on_face = 4,
    // ObjectList::toggle_auto_drop() turning auto drop on: ModelObject::ensure_on_bed().
    ensure_on_bed = 5,
};

// How the desktop app's jobs place several objects of the plate at once.
enum class PlateManipulation : std::int64_t {
    // OrientJob from the toolbar: the selected objects, or every object when
    // none is selected, turn for the least support area and rest on the plate.
    auto_orient = 0,
    // ArrangeJob from the arrange options (prepare_all): every object arranged
    // on the plate with the arrange settings.
    arrange = 1,
};

// GLCanvas3D::ArrangeSettings for FFF printers, as the arrange options window sets them.
struct ArrangeSettings {
    // Spacing between objects in millimetres; 0 means auto spacing.
    double distance{0.0};
    bool enable_rotation{false};
    bool allow_multi_materials_on_same_plate{true};
    bool align_to_y_axis{false};
};

// A face the object can lie on, as GLGizmoFlatten offers it: a flat region of
// the convex hull, shrunk and rounded for display.
struct FlatteningPlane {
    // Outward normal in object coordinates.
    std::array<double, 3> normal{};
    // A convex polygon in object coordinates, x, y, z per point, counter-clockwise seen from outside.
    std::vector<float> vertices;
};

struct FlatteningPlanes {
    SceneStatus status{SceneStatus::model_read_failed};
    std::string message;
    // Largest first, at most 254 (GLGizmoFlatten::update_planes).
    std::vector<FlatteningPlane> planes;
};

// The faces the object of model_path can lie on with the instance transformation placement.
FlatteningPlanes describe_flattening_planes(const std::string& model_path, const ProfileSelection& profiles, const std::vector<double>& placement);

// Loads an STL file, or the built-in 20 mm calibration cube when model_path is
// empty, places it as the desktop app places an object added to the plate that
// holds plate (Plater::priv::load_model_objects), and writes its mesh in object
// coordinates to mesh_path. Models read stay loaded while their objects are
// on the plate, so placing and slicing an object does not read its file again.
ModelInspection inspect_model(
    const std::string& model_path,
    const ProfileSelection& profiles,
    const std::string& mesh_path,
    const std::vector<PlateObject>& plate
);

// Commits a manipulation of the object of model_path, which stood at
// previous_placement, to the instance transformation placement (both
// column-major 4 x 4), as the desktop app does. With auto_drop off
// (ModelInstance::auto_drop) the object is never moved onto the plate. Reports
// the placed object without writing its mesh.
ModelInspection place_model(
    const std::string& model_path,
    const ProfileSelection& profiles,
    const std::vector<double>& previous_placement,
    const std::vector<double>& placement,
    bool auto_drop,
    Manipulation manipulation,
    const std::array<double, 3>& face_normal
);

struct PlateInspection {
    SceneStatus status{SceneStatus::model_read_failed};
    std::string message;
    // Every object of the plate as placed, in the plate's order.
    std::vector<ModelInspection> objects;
};

// Commits a manipulation of the objects of plate as the desktop app's job does.
// selected marks the objects auto orient turns, one flag per object.
PlateInspection place_objects(
    const std::vector<PlateObject>& plate,
    const std::vector<bool>& selected,
    const ProfileSelection& profiles,
    PlateManipulation manipulation,
    const ArrangeSettings& arrange_settings
);

}  // namespace orcinus::orca
