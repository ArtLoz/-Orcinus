#pragma once

#include <array>
#include <cstdint>
#include <functional>
#include <map>
#include <string>
#include <utility>
#include <vector>

namespace Slic3r {
class ModelVolume;
}

namespace orcinus::orca {

// Stable boundary between the JNI bridge and OrcaSlicer. Orca types must not
// cross it.

std::string engine_version();

struct EngineDirectories {
    // Orca data directory, as the desktop app keeps it: OrcaSlicer.conf with the
    // installed printers and filaments and the selected presets, and system/
    // with the vendor bundles installed from the resources.
    std::string data_dir;
    // Orca resources directory with info/, flush/, and profiles/, every vendor
    // bundle the app ships.
    std::string resources_dir;
    std::string temporary_dir;
};

struct EngineInitialization {
    bool ready{false};
    std::string message;
};

// Loads the app configuration and the installed presets once per process,
// installing vendor bundles from the resources as the desktop app's updater
// does at start-up. Later calls return the first result.
EngineInitialization initialize(const EngineDirectories& directories);

struct ProfileSelection {
    std::string printer;
    // The first filament, which a plate with one filament prints with.
    std::string filament;
    std::string process;
    // PresetBundle::filament_presets: every filament of the plate, in the order
    // the sidebar lists them. Empty means the one of `filament` alone.
    std::vector<std::string> filaments;
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
    // The mesh of the wipe tower the slice built was written for the 3D view.
    bool wipe_tower_written{false};
    // What the layer slider's menu offers for this print (IMSlider::SetDrawMode,
    // SetModeAndOnlyExtruder): nothing but "Jump to Layer" for a print by
    // object, a filament change while the objects print with one filament and
    // the print is no spiral vase, a template when the printer has one.
    bool sequential{false};
    bool can_change_filament{true};
    bool has_template{false};
};

// CustomGCode::Type of CustomGCode.hpp: what a code on a layer does.
enum class LayerGcodeType : std::int64_t {
    color_change = 0,
    pause_print = 1,
    tool_change = 2,
    template_gcode = 3,
    custom = 4,
};

// CustomGCode::Item: a code the print runs where the layer at print_z starts,
// as the layer slider of the preview puts it there. extruder is the filament
// a tool change switches to, color its colour, extra the G-code of a custom one.
struct LayerGcode {
    double print_z{0.0};
    LayerGcodeType type{LayerGcodeType::custom};
    int extruder{1};
    std::string color;
    std::string extra;
};

// A thumbnail of the plate the app rendered for the G-code, as the desktop app
// renders it when the G-code is exported (GLCanvas3D::render_thumbnail): width x
// height RGBA pixels in a file, the bottom row first, as glReadPixels() reads them.
struct ThumbnailImage {
    std::int32_t width{0};
    std::int32_t height{0};
    std::string path;
};

// Receives Orca's slicing status: percent in [0, 100] and its status text.
using ProgressCallback = std::function<void(int percent, const std::string& message)>;

// Where the user put one copy of an object (ModelInstance): its transformation,
// column-major 4 x 4, ModelInstance::auto_drop, which lets it stay above the
// plate when off, and ModelInstance::printable, which the object list's check
// box switches.
struct ObjectPlacement {
    std::vector<double> matrix;
    bool auto_drop{true};
    bool printable{true};
};

// The settings an object or the plate overrides the process preset with, as the
// desktop app keeps them in a ModelConfig: the keys the user set, with their
// values as OrcaSlicer writes them into a project.
struct ModelSettings {
    std::vector<std::string> keys;
    std::vector<std::string> values;
};

// ModelVolumeType of Model.hpp: what a part of an object is for.
enum class VolumeType : std::int64_t {
    // MODEL_PART: printed with the object.
    part = 0,
    // NEGATIVE_VOLUME: taken out of the object.
    negative = 1,
    // PARAMETER_MODIFIER: the settings of the object apply to it alone.
    modifier = 2,
    // SUPPORT_BLOCKER and SUPPORT_ENFORCER.
    support_blocker = 3,
    support_enforcer = 4,
};

// A part of an object (ModelVolume): one of the shapes the desktop app
// generates (ObjectList::load_generic_subobject), or a volume a model file
// brought (Plater::priv::load_files), with its transformation in the object's
// coordinates and the settings it overrides.
struct ObjectPart {
    // "Cube", "Cylinder", "Sphere", "Slab", "Cone", "Disc" or "Torus", as
    // create_mesh() of GUI_ObjectList.cpp names them; empty for a part read
    // from model_path.
    std::string shape;
    // The mesh of a part a model file brought, in its own coordinates, as
    // import_model() wrote it; empty for a generated shape.
    std::string model_path;
    // ModelVolume::name, which the object list shows.
    std::string name;
    VolumeType type{VolumeType::part};
    // Column-major 4 x 4, in the object's coordinates.
    std::vector<double> matrix;
    // The settings of the part (ModelVolume::config).
    ModelSettings settings;
    // The facets painted with the filaments of the plate, as begin_painting()
    // and end_painting() hand them over; empty for a part painted with nothing.
    std::string painted;
    // ModelVolume::source: the units the mesh was converted from, which the
    // object menu can restore.
    bool from_inches{false};
    bool from_meters{false};
    // ModelVolume::source.input_file: the file the volume was read from, which
    // "Replace all with 3D files" looks for by name; empty for a generated shape.
    std::string input_file;
};

// A height range of an object (one entry of ModelObject::layer_config_ranges):
// the slab between two heights in the object's coordinates, with the settings
// the desktop app slices it with — at least its own layer height.
struct LayerRange {
    double bottom{0.0};
    double top{0.0};
    ModelSettings settings;
};

// An object on the plate: the file its own mesh is loaded from, empty for the
// built-in 20 mm calibration cube, the parts it has, and where its copies stand
// (ModelObject::instances). Without an instance the object is placed as the
// desktop app places an object added to the plate that holds the objects
// before it.
struct PlateObject {
    std::string model_path;
    // ModelObject::name and the name of its own mesh (its first ModelVolume);
    // empty keeps the names the model file gives them.
    std::string name;
    std::string volume_name;
    // The transformation of the object's own mesh in the object (the matrix of
    // its first ModelVolume), column-major 4 x 4, for an object a model file
    // brought: its parts stand in the same frame, which import_model() gave it.
    // Empty centres the mesh around the origin, as for an STL file or the cube.
    std::vector<double> matrix;
    std::vector<ObjectPart> parts;
    std::vector<ObjectPlacement> instances;
    // The settings of the object (ModelObject::config), which the process
    // preset is sliced with for it and which its copies share.
    ModelSettings settings;
    // The height ranges of the object, in their order from the bed up.
    std::vector<LayerRange> layer_ranges;
    // The facets of the object's own mesh painted with the filaments of the
    // plate (ModelVolume::mmu_segmentation_facets).
    std::string painted;
    // The settings of the object's own mesh (the config of its first
    // ModelVolume), which the object list edits once the object has parts.
    ModelSettings volume_settings;
    // ModelVolume::source of its own mesh, as ObjectPart::from_inches and input_file.
    bool volume_from_inches{false};
    bool volume_from_meters{false};
    std::string volume_input_file;
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
    // The settings of the plate, which the print is sliced with
    // (BackgroundSlicingProcess::apply of the desktop app).
    const ModelSettings& plate_settings,
    const ProgressCallback& on_progress,
    // Where the wipe tower the slice built is written, as the desktop app
    // draws it on the plate once the plate is sliced
    // (GLVolumeCollection::load_real_wipe_tower_preview); empty writes none.
    const std::string& wipe_tower_mesh_path = {},
    // The thumbnails the G-code is exported with (the ThumbnailsGeneratorCallback
    // of BackgroundSlicingProcess); a size the app rendered none of is left out.
    const std::vector<ThumbnailImage>& thumbnails = {},
    // The codes on the layers (Model::plates_custom_gcodes); none leaves the plate without.
    const std::vector<LayerGcode>& layer_gcodes = {}
);

// Returns true only when job_id is the active job and cancellation was requested.
bool cancel(const std::string& job_id);

// PartPlateList::select_plate(): the plate the next requests are for, at
// index among count plates. It stands where PartPlateList::compute_origin()
// puts it for the printer of a request, and its build volume is the printable
// area moved there: objects are judged by it
// (Plater::priv::update_print_volume_state), new ones placed on it, and
// slice() prints it with the plate's origin, from which the G-code counts
// (Print::set_plate_origin).
void select_plate(int index, int count);

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

// The thumbnails the G-code of the selected printer holds: the sizes of its
// "thumbnails" setting, in order (GCodeThumbnails::make_and_check_thumbnail_list).
// None for a Bambu Lab printer, whose G-code holds its configuration instead.
// The app renders them before it slices with profiles.
struct ThumbnailSizes {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    // Width and height of each thumbnail, one after another.
    std::vector<std::int32_t> sizes;
};

ThumbnailSizes thumbnail_sizes(const ProfileSelection& profiles);

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
    // ModelObject::get_object_stl_stats().open_edges: the edges of the
    // object's meshes that bound one triangle only.
    std::int64_t open_edges{0};
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
    // GLCanvas3D::mirror_selection(): the copy mirrored along an axis about the
    // centre of its bounding box, then do_mirror(), which rests it on the plate as do_rotate().
    mirror_x = 6,
    mirror_y = 7,
    mirror_z = 8,
    // Selection::center(): the copy moved over the centre of the plate, then do_move().
    center = 9,
    // Selection::drop(): the copy moved down or up until it touches the plate,
    // whether it drops by itself or not.
    drop = 10,
};

// How the desktop app's jobs place several objects of the plate at once.
enum class PlateManipulation : std::int64_t {
    // OrientJob from the toolbar: the selected objects, or every object when
    // none is selected, turn for the least support area and rest on the plate.
    auto_orient = 0,
    // ArrangeJob from the arrange options (prepare_all): every object arranged
    // on the plate with the arrange settings.
    arrange = 1,
    // Plater::on_config_change() for another printer: the objects stay where
    // they are, and whether they fit is judged against its build volume.
    update_print_volume_state = 2,
    // ArrangeJob from a menu (prepare_partplate): the copies on the plate or
    // over its edge arranged with the arrange settings; the ones off it stay.
    arrange_plate = 3,
    // FillBedJob with instances ("Fill bed with instances"): copies of the
    // selected object added while the free area of the plate holds more,
    // then the plate arranged as arrange_plate arranges it.
    fill_bed = 4,
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

// The faces the object can lie on with the instance transformation placement;
// its instances are not used. Every part printed with it counts, as
// GLGizmoFlatten::update_planes() takes the convex hull of the object's parts.
FlatteningPlanes describe_flattening_planes(const PlateObject& object, const ProfileSelection& profiles, const std::vector<double>& placement);

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

// Commits a manipulation of the object, which stood at previous_placement, to
// the instance transformation placement (both column-major 4 x 4), as the
// desktop app does; the object's instances are not used. With auto_drop off
// (ModelInstance::auto_drop) the object is never moved onto the plate. Its
// parts count, as the desktop app drops and rests a ModelObject with all of
// its volumes. Reports the placed object without writing its mesh.
ModelInspection place_model(
    const PlateObject& object,
    const ProfileSelection& profiles,
    const std::vector<double>& previous_placement,
    const std::vector<double>& placement,
    bool auto_drop,
    Manipulation manipulation,
    const std::array<double, 3>& face_normal
);

// The copies of one object as placed, in the object's order.
struct PlateObjectInspection {
    std::vector<ModelInspection> instances;
};

struct PlateInspection {
    SceneStatus status{SceneStatus::model_read_failed};
    std::string message;
    // Every object of the plate as placed, in the plate's order.
    std::vector<PlateObjectInspection> objects;
    // The number of plates afterwards: arranging every plate adds plates for
    // what the others do not hold.
    int plate_count{1};
};

// ObjectList::load_generic_subobject(): a shape added to the object as a part,
// a negative volume, a modifier, or a support blocker or enforcer. The shape is
// the size the desktop app gives it (5% of the largest side of the bed, a slab
// from the object's bounding box), it is placed beside the object as the
// desktop app places it, and its mesh is written to mesh_path for the 3D view.
ModelInspection add_object_part(
    const PlateObject& object,
    const std::string& shape,
    VolumeType type,
    const ProfileSelection& profiles,
    const std::string& mesh_path
);

// Commits a manipulation of the objects of plate as the desktop app's job does.
// selected marks the objects auto orient turns, one flag per object, and the
// object fill_bed adds copies of; selected_instance is its selected copy, or -1
// when the whole object is selected. locked_plates marks the plates locked
// (PartPlate::is_locked), whose copies arranging and orienting leave alone.
// An object has as many copies afterwards as the result describes.
PlateInspection place_objects(
    const std::vector<PlateObject>& plate,
    const std::vector<bool>& selected,
    const ProfileSelection& profiles,
    PlateManipulation manipulation,
    const ArrangeSettings& arrange_settings,
    int selected_instance = -1,
    const std::vector<bool>& locked_plates = {}
);

// The wipe tower of the plate, which the desktop app draws as a volume of its
// own (GLVolumeCollection::load_wipe_tower_preview): a box striped with the
// colours of the filaments printed on the plate. It stands where wipe_tower_x
// and wipe_tower_y of the project put it — the app keeps them among the
// settings of the plate — and its size follows the process preset and the
// objects on the plate (PartPlate::estimate_wipe_tower_size).
struct WipeTowerState {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    // Whether the plate prints one at all: the process preset enables the prime
    // tower and the plate prints with more than one filament.
    bool shown{false};
    // Its front left corner on the plate (wipe_tower_x, wipe_tower_y).
    double x{0.0};
    double y{0.0};
    double width{0.0};
    double depth{0.0};
    // The tallest object of the plate, which the tower is printed up to.
    double height{0.0};
    // wipe_tower_rotation_angle, in degrees.
    double rotation{0.0};
    // prime_tower_brim_width, with a negative (automatic) width resolved.
    double brim_width{0.0};
    // The filaments printed on the plate (PartPlate::get_extruders), 1-based
    // and in order: the desktop app stripes the tower with their colours.
    std::vector<int> filaments;
    // What the object menu's Flush Options take from the edited process preset
    // (MenuFactory::append_menu_items_flush_options): enable_prime_tower, and
    // flush_into_infill, flush_into_objects and flush_into_support, which an
    // object follows unless it sets them itself. Described whether or not the
    // tower is shown.
    bool prime_tower{false};
    bool flush_into_infill{false};
    bool flush_into_objects{false};
    bool flush_into_support{false};
};

// Describes the wipe tower of the plate. Without wipe_tower_x and wipe_tower_y
// among the plate's settings it stands where the desktop app puts it first
// (PartPlateList::set_default_wipe_tower_pos_for_plate), so the answer also
// tells the app the position to keep.
WipeTowerState describe_wipe_tower(
    const std::vector<PlateObject>& plate,
    const ProfileSelection& profiles,
    const ModelSettings& plate_settings
);

// The flushing volumes of the plate (WipingDialog): how much filament is
// pushed into the wipe tower when the print changes from one filament to
// another. OrcaSlicer keeps a matrix per nozzle in the project, one row per
// filament printed from, and works the volumes out from the filament colours
// when the project carries none.
struct FlushVolumes {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    // How many filaments the plate has, which is the matrix's side.
    int filaments{0};
    // How many nozzles the printer has: the project keeps a matrix per nozzle.
    int nozzles{1};
    // nozzles * filaments * filaments, row by row: from the filament of the row
    // to the one of the column.
    std::vector<double> matrix;
    // The volumes OrcaSlicer works out from the colours, in the same shape.
    std::vector<double> automatic;
    // flush_multiplier, one per nozzle.
    std::vector<double> multipliers;
    // is_flush_config_modified(): the project's volumes are not the ones
    // OrcaSlicer would work out on its own, which its sidebar marks.
    bool modified{false};
};

// Describes the flushing volumes of the plate. flush_volumes_matrix and
// flush_multiplier among the plate's settings are the project's own; without
// them the answer holds the calculated volumes, which the app then keeps.
FlushVolumes describe_flush_volumes(
    const std::vector<PlateObject>& plate,
    const ProfileSelection& profiles,
    const ModelSettings& plate_settings
);

// What changed about the filaments of the plate, after which the desktop app
// works its flushing volumes out again (Sidebar::auto_calc_flushing_volumes).
enum class FlushVolumesChange : std::int64_t {
    // Sidebar::add_custom_filament(): the filament at index was added.
    filament_added = 0,
    // Sidebar::delete_filament(): the filament at index was taken out.
    filament_removed = 1,
    // on_filament_color_changed(): the colour of the filament at index changed.
    color_changed = 2,
    // on_select_preset() of a filament combo box: the filament at index prints with another preset.
    filament_changed = 3,
    // on_select_preset() of the printer combo box: another printer was selected.
    printer_changed = 4,
    // Tab::on_value_change() of long_retractions_when_cut or filament_long_retractions_when_cut.
    long_retraction_changed = 5,
};

// The project's flushing volumes after change, as the desktop app keeps them:
// the matrix brought to the filaments of the selection
// (PresetBundle::update_multi_material_filament_presets), then the volumes from
// and to the changed filament — or every filament — worked out from the
// colours again where the desktop app does it, following its "Auto flush
// after changing..." preference (auto_calculate_flush). updated tells whether
// the project's matrix or multipliers differ from the ones the plate held.
struct FlushVolumesUpdate {
    FlushVolumes volumes;
    bool updated{false};
};

FlushVolumesUpdate update_flush_volumes(
    const std::vector<PlateObject>& plate,
    const ProfileSelection& profiles,
    const ModelSettings& plate_settings,
    FlushVolumesChange change,
    std::int64_t index
);

// Applies painted facets to a volume of a loaded model, as a project does.
bool apply_painted_facets(Slic3r::ModelVolume& volume, const std::string& facets);

// The painted facets of a volume as the app keeps them; empty for none.
std::string painted_facets_of(const Slic3r::ModelVolume& volume);

// Painting a model with the filaments of the plate (GLGizmoMmuSegmentation):
// which tool the finger paints with.
enum class PaintTool : std::int64_t {
    // A round brush that follows the finger (CursorType::SPHERE).
    brush = 0,
    // Smart fill: the facets that lie flat enough against the touched one.
    fill = 1,
    // Bucket fill: the whole surface up to its sharp edges.
    bucket = 2,
};

// One touch of the finger on a model being painted.
struct PaintStroke {
    // The finger's ray in world coordinates, as the 3D view casts it.
    double origin[3]{0.0, 0.0, 0.0};
    double direction[3]{0.0, 0.0, 0.0};
    // The filament to paint with, 1-based; 0 takes the paint off again.
    int filament{0};
    // The brush's radius in millimetres (GLGizmoPainterBase::m_cursor_radius).
    double radius{2.0};
    PaintTool tool{PaintTool::brush};
    // The angle the fills keep to (m_smart_fill_angle), in degrees.
    double angle{30.0};
    // The first touch of a stroke: the tool keeps the painting as it was, which
    // Undo returns to, once the stroke meets the model (the gizmo takes its
    // snapshot as the mouse button goes down on the object).
    bool starts{false};
};

// What a painting session answers with: the triangles painted with each
// filament, written as meshes for the 3D view, and, when the session is closed,
// the painted facets for the app to keep with the object.
struct PaintingState {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    // Whether the stroke met the model at all.
    bool hit{false};
    // The filaments the model is painted with, 1-based, and the mesh of each.
    std::vector<int> filaments;
    std::vector<std::string> meshes;
    // The painted facets of the volume, as the app keeps them.
    std::string facets;
    // Whether a stroke can be undone or redone inside the tool, which keeps a
    // stack of its own while it is open (the gizmo's UndoRedo::Stack).
    bool can_undo{false};
    bool can_redo{false};
};

// Opens a painting session for the object, or for one of its parts, with the
// facets it is already painted with. The engine keeps the session until
// end_painting(), as the desktop gizmo keeps its selectors while it is open.
PaintingState begin_painting(
    const PlateObject& object,
    // The part of the object to paint; -1 paints the object's own mesh.
    int part,
    const ProfileSelection& profiles,
    const std::string& facets,
    // Where the meshes of the painted triangles are written, "<prefix>-<filament>.mesh".
    const std::string& mesh_prefix
);

PaintingState paint(const PaintStroke& stroke, const std::string& mesh_prefix);

// The painting before the last stroke, or after the stroke undone last, as the
// gizmo's Undo and Redo bring it back.
PaintingState undo_painting(const std::string& mesh_prefix);
PaintingState redo_painting(const std::string& mesh_prefix);

// Closes the session and reports the painted facets.
PaintingState end_painting();

// A text of the desktop app. msgid, with its gettext context when it has one,
// is translated as _() translates it (with msgid_plural, as _L_PLURAL() does
// for count), then its placeholders (%s, %d, %.3f, %1%) are filled with args,
// translated first when translate_args is set. Texts the desktop app shows
// untranslated are not in the catalogue.
struct UiText {
    std::string context;
    std::string msgid;
    std::string msgid_plural;
    std::int64_t count{0};
    std::vector<std::string> args;
    bool translate_args{false};
};

// What the desktop app's settings tabs edit (Preset::Type): the presets, and
// the settings an object or the plate overrides them with.
enum class PresetKind : std::int64_t {
    print = 0,
    filament = 1,
    printer = 2,
    // TabPrintObject: the process settings of one object (Preset::TYPE_MODEL).
    object = 3,
    // TabPrintPlate: the settings of the plate (Preset::TYPE_PLATE).
    plate = 4,
    // TabPrintPart: the settings of one part of an object.
    part = 5,
    // TabPrintLayer: the settings of one height range of an object.
    layer = 6,
};

// The sections of a preset combo box of the desktop app.
enum class PresetGroup : std::int64_t {
    // "User presets"
    user = 0,
    // "Bundle presets": presets of a subscribed preset bundle.
    bundle = 1,
    // "System presets"
    system = 2,
};

// An entry of a preset combo box of the desktop app's sidebar, in the combo
// box's order.
struct PresetItem {
    // What choosing the entry selects: the preset name, or for a system printer
    // its printer model, which the printer combo box lists once.
    std::string name;
    // The text the combo box shows.
    std::string label;
    PresetGroup group{PresetGroup::system};
    // The submenu: the filament vendor of a system filament, the bundle of a bundle preset.
    std::string subgroup;
    bool selected{false};
};

// A value the edited preset changed, as UnsavedChangesDialog lists it: where
// its setting sits in the tab, and the value before and after the change.
struct PresetChange {
    // The option's id, as the tab's lines name it ("retraction_length#0").
    std::string id;
    // The page of the tab (Tab::translate_category), and the option group.
    std::vector<UiText> category;
    std::vector<UiText> group;
    std::vector<UiText> label;
    std::vector<UiText> old_value;
    std::vector<UiText> new_value;
};

struct PresetState {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    // GUI_App::config_wizard_startup() would run the Setup Wizard: the app had
    // no configuration yet, or only default printers are installed.
    bool setup_required{false};
    // The selected printer, first filament, and process presets.
    ProfileSelection selection;
    // project_config's filament_colour: the colour of every filament, "#RRGGBB".
    std::vector<std::string> filament_colors;
    // filament_type of every filament's preset ("PLA", "PETG" ...), which the
    // send dialog of a printer with material boxes matches its slots by.
    std::vector<std::string> filament_types;
    // PlaterPresetComboBox::update() for printers and the first filament.
    std::vector<PresetItem> printers;
    std::vector<PresetItem> filaments;
    // TabPresetComboBox::update() of the process tab.
    std::vector<PresetItem> processes;
    // Sidebar::update_presets(): the nozzle diameters of the selected printer
    // model and the one of the selected printer, "0.4".
    std::vector<std::string> nozzle_diameters;
    std::string nozzle_diameter;
    // Tab::may_discard_current_dirty_preset(): nothing was selected, because
    // the edited preset of changed_kind has the unsaved changes below. The app
    // asks the user and selects again with a PresetChangeAction.
    bool asks_unsaved_changes{false};
    PresetKind changed_kind{PresetKind::print};
    std::vector<PresetChange> unsaved_changes;
    // The changes can be moved to the preset that is selected (the dialog's
    // Transfer button, which a printer and a filament of another type lack).
    bool can_transfer{false};
    // The name its Save button suggests (SavePresetDialog::Item::Item()).
    std::string save_name;
    bool save_name_copy_suffix{false};
    // The plate types the selected printer model supports, curr_bed_type's
    // values with their labels, as the sidebar's plate type combo box and
    // PlateSettingsDialog list them; none for a printer of another vendor
    // than Bambu Lab, whose dialog does not choose one.
    std::vector<std::string> bed_type_values;
    std::vector<std::string> bed_type_labels;
};

// The preset combo boxes for the selection the app configuration remembers.
PresetState describe_presets();


// What a preset choice selects.
enum class PresetChoice : std::int64_t {
    // A printer preset by name (Tab::select_preset): the process and filament
    // the printer last used, or compatible ones, are selected with it.
    printer = 0,
    // A system printer model of the printer combo box: its preset with the
    // selected printer's nozzle, or another one (PresetBundle::get_similar_printer_preset).
    printer_model = 1,
    // A nozzle diameter of the selected printer model (Sidebar::priv::switch_diameter).
    nozzle_diameter = 2,
    // The first filament by preset name (Plater::priv::on_select_preset).
    filament = 3,
    // A process preset by name; the filament changes when it is not compatible with it.
    process = 4,
};

// What happens to the unsaved changes of the edited preset when another one is
// selected (UnsavedChangesDialog's buttons).
enum class PresetChangeAction : std::int64_t {
    // Ask: a dirty preset selects nothing and reports its changes.
    ask = 0,
    // The changed values move to the preset that is selected (Tab::cache_config_diff).
    transfer = 1,
    // The changes are lost with the preset they were made in.
    discard = 2,
};

// Selects a preset as the desktop app's sidebar does and remembers the
// selection in the app configuration (PresetBundle::export_selections). The
// unsaved changes of the edited preset are kept, moved, or lost, as action
// says; saving them is a request of the settings tab.
PresetState select_preset(PresetChoice choice, const std::string& value, PresetChangeAction action = PresetChangeAction::ask);

// A preset a settings tab edits with unsaved changes
// (Tab::current_preset_is_dirty), as UnsavedChangesDialog lists it when a
// project is created or another one is loaded.
struct DirtyPreset {
    PresetKind kind{PresetKind::print};
    // The edited preset's name.
    std::string name;
    // Preset::can_overwrite(): its changes are saved into it; a system,
    // default or external preset is saved under a name SavePresetDialog asks.
    bool can_overwrite{false};
    std::string save_name;
    bool save_name_copy_suffix{false};
    std::vector<PresetChange> changes;
};

struct DirtyPresets {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    std::vector<DirtyPreset> presets;
};

// GUI_App::has_current_preset_changes(): the presets of the process, filament
// and printer tabs that have unsaved changes, in that order.
DirtyPresets dirty_presets();

// reset_modifications() of GUI_App::check_and_keep_current_preset_changes():
// every tab's preset loses its unsaved changes.
PresetState discard_preset_changes();

// Plater::priv::reset() for a new project: the presets the project before
// brought go (PresetBundle::reset_project_embedded_presets).
PresetState reset_project_presets();

// Sidebar::add_custom_filament(): another filament joins the plate, with the
// next colour of OrcaSlicer's palette (Plater::get_next_color_for_filament).
PresetState add_filament();

// Sidebar::delete_filament(): the filament at index leaves the plate; the first
// one cannot, as the desktop app keeps at least one.
PresetState remove_filament(std::int64_t index);

// The preset of the filament at index (PlaterPresetComboBox of that slot).
PresetState select_filament(std::int64_t index, const std::string& name, PresetChangeAction action = PresetChangeAction::ask);

// The colour the sidebar shows for a filament (project_config's filament_colour).
PresetState set_filament_color(std::int64_t index, const std::string& color);

// A printer model of the Setup Wizard's printer page.
struct SetupPrinterModel {
    std::string vendor;
    std::string model;
    std::string name;
    std::vector<std::string> nozzle_diameters;
    // Filament presets the wizard selects with the model (default_materials).
    std::vector<std::string> default_materials;
    // The cover image file.
    std::string cover;
    // The nozzle diameters the app configuration has installed.
    std::vector<std::string> installed_nozzles;
};

struct SetupPrinters {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    std::vector<SetupPrinterModel> models;
};

// Every printer model of the vendor bundles, as GuideFrame::LoadProfileData() lists them.
SetupPrinters describe_setup_printers();

// A filament preset of the Setup Wizard's filament page.
struct SetupFilament {
    std::string name;
    std::string vendor;
    std::string type;
    // Indices of the requested printer models the filament is compatible with,
    // with any of their nozzle diameters; empty for a filament the wizard
    // offers for every printer.
    std::vector<std::int32_t> models;
    // Installed, or a default material of a requested model (save_userguide_models).
    bool selected{false};
};

struct SetupFilaments {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    std::vector<SetupFilament> filaments;
};

// The filaments of the Setup Wizard's filament page for the printer models
// (model ids) chosen on its printer page.
SetupFilaments describe_setup_filaments(const std::vector<std::string>& models);

// The Setup Wizard's Finish (GuideFrame::SaveProfile() and apply_config()):
// installs the printer models (model ids), each with all its nozzle diameters,
// and the filaments in place of the installed ones, selects the first printer
// the wizard added (PresetBundle::apply_vendor_config), and saves the configuration.
PresetState apply_setup(const std::vector<std::string>& models, const std::vector<std::string>& filaments);

// GuideFrame::run() when the wizard closes while only default printers are
// installed: OrcaSlicer's default printer and filament. Saves the configuration,
// so the wizard is not required again.
PresetState apply_default_setup();


// Which settings the tabs show (ConfigOptionMode). The app configuration keeps
// "user_mode"; with "developer_mode" the tabs show develop settings too.
enum class SettingsMode : std::int64_t {
    simple = 0,
    advanced = 1,
    expert = 2,
    develop = 3,
};

// A setting as PrintConfigDef defines it (ConfigOptionDef). Texts are the
// untranslated msgids of the desktop app.
struct SettingDefinition {
    std::string key;
    // ConfigOptionType
    std::int64_t type{0};
    std::string label;
    std::string full_label;
    std::string category;
    std::string tooltip;
    // A unit, usually.
    std::string sidetext;
    // ConfigOptionMode
    std::int64_t mode{0};
    // ConfigOptionDef::GUIType
    std::int64_t gui_type{0};
    std::string gui_flags;
    std::vector<std::string> enum_values;
    std::vector<std::string> enum_labels;
    // -FLT_MAX and FLT_MAX when unbounded.
    double min{0.0};
    double max{0.0};
    bool nullable{false};
    bool readonly{false};
    bool multiline{false};
    bool full_width{false};
    bool is_code{false};
    std::int32_t height{-1};
};

struct SettingDefinitions {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    std::vector<SettingDefinition> settings;
};

// Every setting of a kind of preset (Preset::print_options(),
// filament_options(), printer_options()), and the settings a tab defines
// itself, such as the extruder count of the printer tab.
SettingDefinitions describe_setting_definitions(PresetKind kind);

// What a line shows in place of the fields of its options
// (Tab::create_line_with_widget).
enum class SettingWidget : std::int64_t {
    none = 0,
    // TabPrinter::create_bed_shape_widget(): the shape of the printable area.
    bed_shape = 1,
    // Tab::compatible_widget_create(): the presets the edited preset is for.
    compatible_printers = 2,
    compatible_prints = 3,
    // RammingDialog for filament_ramming_parameters.
    ramming = 4,
};

// An option a line of a settings tab shows (Option of OptionsGroup.hpp).
struct SettingsLineOption {
    // The option's id: its key, and for one value of a vector setting the
    // index the line shows ("retraction_length#0").
    std::string id;
    std::string key;
    // -1 for a setting the line shows whole.
    std::int32_t index{-1};
    // The definition's label, or the one the tab gave the option.
    std::string label;
    bool full_width{false};
    bool is_code{false};
    bool multiline{false};
    // In lines of text; -1 for the default.
    std::int32_t height{-1};
    // The field offers EditGCodeDialog (OptionsGroup::build_field(): a custom
    // G-code of a group the tab gave edit_custom_gcode).
    bool edit_custom_gcode{false};
};

// A line of an option group (Line of OptionsGroup.hpp).
struct SettingsLine {
    // The label of the line; for a line of one option its label
    // (create_single_option_line).
    std::string label;
    std::string tooltip;
    // append_separator(): a line without options.
    bool separator{false};
    SettingWidget widget{SettingWidget::none};
    // A filament override (TabFilament::add_filament_overrides_page): the check
    // box before the label switches the override on.
    bool has_override{false};
    std::vector<SettingsLineOption> options;
};

struct SettingsGroup {
    std::string title;
    // An icon of resources/images.
    std::string icon;
    std::vector<SettingsLine> lines;
};

struct SettingsPage {
    // The page's name, which requests name it by.
    std::string title;
    // What the tab shows for it (Tab::translate_category).
    std::vector<UiText> label;
    std::string icon;
    std::vector<SettingsGroup> groups;
};

// How a message box of the desktop app looks.
enum class DialogIcon : std::int64_t {
    info = 0,
    warning = 1,
    error = 2,
    question = 3,
};

// A message box the desktop app shows while it applies a change.
struct SettingsDialog {
    // The check that shows it; an answer to a question names it.
    std::string id;
    DialogIcon icon{DialogIcon::warning};
    // The caption's parts; none for the app's own caption.
    std::vector<UiText> title;
    // The parts of the message, in order.
    std::vector<UiText> text;
    // Yes and No; otherwise the box only informs.
    bool question{false};
    // Labels of the Yes and No buttons when the box has its own.
    UiText yes;
    UiText no;
};

// A setting of the edited preset as its field shows it (Tab::update_changed_ui,
// ConfigManipulation's toggles).
struct SettingState {
    // The option's id, as the lines of the pages name it.
    std::string id;
    std::string key;
    // The field's text (ConfigOptionsGroup::get_config_value): "0.2", "15%",
    // "1" or "0" for a check box, the enum key ("grid") for a combo box.
    std::string value;
    // Differs from the saved preset.
    bool modified{false};
    // Equals the preset the edited one inherits from.
    bool system{false};
    // toggle_field
    bool enabled{true};
    // toggle_line
    bool visible{true};
    // The tab sets the entries of the combo box (TabPrint::toggle_options, the
    // filament lists): their values and labels, which replace the definition's.
    bool has_choices{false};
    std::vector<std::string> choice_values;
    std::vector<std::string> choice_labels;
    // A filament override that is not set: the field shows the value of the
    // printer or process preset, which the line's check box switches to the
    // filament's (TabFilament::update_filament_overrides_page).
    bool nullable{false};
    bool is_nil{false};
    // The objects the list has selected disagree on the value, so the field
    // shows none (TabPrintModel::m_null_keys); a change applies to all of them.
    bool mixed{false};
    // Whether that check box can be used at all.
    bool override_enabled{true};
    // The names a setting of several values holds: the presets of
    // compatible_printers and compatible_prints, which their widget edits.
    std::vector<std::string> list_values;
};

// The edited preset of a kind as its settings tab shows it.
struct PresetSettings {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    PresetKind kind{PresetKind::print};
    // The selected preset.
    std::string preset;
    // Preset::label(): the name or the alias, after the "* " of a modified preset.
    std::string label;
    bool dirty{false};
    bool is_default{false};
    bool is_system{false};
    // The edited preset inherits from another one (get_selected_preset_parent()).
    bool has_parent{false};
    // Tab::delete_preset() is offered: a user preset.
    bool can_delete{false};
    SettingsMode mode{SettingsMode::simple};
    // The pages the tab lays out for the edited preset; the printer tab builds
    // them from its configuration (TabPrinter::build_unregular_pages).
    std::vector<SettingsPage> pages;
    // The page whose fields the tab toggled (Tab::activate_selected_page).
    std::string active_page;
    // Tab::m_variant_combo: the extruder variants the filament tab can show
    // ("drive: nozzle"), and the one it shows. The desktop switch appears only
    // with more than one variant (m_variant_combo->Enable(options.size() > 1)).
    std::vector<std::string> variants;
    int variant{0};
    std::vector<SettingState> settings;
    // The name SavePresetDialog suggests: the preset's name, followed by " - "
    // and the translation of "Copy" (context "PresetName") when copy_suffix is set.
    std::string save_name;
    bool save_name_copy_suffix{false};
    // Message boxes the change showed, in order; they informed only.
    std::vector<SettingsDialog> notices;
    // A question the change asks before it applies anything: the change is
    // requested again with the answer. When loading the selection into the tab
    // asks it, describe_settings() is requested with the answer instead.
    bool has_question{false};
    SettingsDialog question;
    bool question_loads_selection{false};
    // The settings of the objects or of the plate the tab edits, after the
    // change, in the order the request carried them: the app keeps them with
    // the objects and sends them back with every request, since the engine
    // holds no plate of its own.
    bool has_model_settings{false};
    std::vector<ModelSettings> model_settings;
};

// Answers to the questions a change asked, by dialog id: true for Yes.
using DialogAnswers = std::vector<std::pair<std::string, bool>>;

// A volume of an object a model file brought, other than its own mesh.
struct ImportedPart {
    // ModelVolume::name.
    std::string name;
    VolumeType type{VolumeType::part};
    // Its mesh in its own coordinates, written by import_model(); the 3D view
    // draws it with matrix, and slicing reads it as ObjectPart::model_path.
    std::string model_path;
    // Its transformation in the object, column-major 4 x 4.
    std::vector<double> matrix;
    // The settings the file gave the volume (ModelVolume::config), such as its
    // extruder.
    ModelSettings settings;
    // Its painted facets, as ObjectPart::painted.
    std::string painted;
    // ModelVolume::is_splittable(): the mesh has more than one shell.
    bool splittable{false};
    bool from_inches{false};
    bool from_meters{false};
    // ModelVolume::source.input_file
    std::string input_file;
};

// An object a model file brought, placed on the plate.
struct ImportedObject {
    // ModelObject::name: the name the file gave it, or the file's name.
    std::string name;
    // Its own mesh (its first volume) in its own coordinates, which the object
    // is loaded from (PlateObject::model_path).
    std::string model_path;
    // PlateObject::matrix: that mesh's transformation in the object.
    std::vector<double> matrix;
    // Its other volumes, which print with it or change it.
    std::vector<ImportedPart> parts;
    // The settings the file gave the object (ModelObject::config).
    ModelSettings settings;
    // ModelVolume::name and ModelVolume::config of its own mesh.
    std::string volume_name;
    ModelSettings volume_settings;
    // The painted facets of its own mesh, whether it can be split, and the
    // units it was converted from.
    std::string painted;
    bool volume_splittable{false};
    bool volume_from_inches{false};
    bool volume_from_meters{false};
    std::string volume_input_file;
    // ModelObject::layer_config_ranges
    std::vector<LayerRange> layer_ranges;
    // The object's own mesh in object coordinates for the 3D view, as
    // inspect_model() writes it.
    std::string mesh_path;
    // Every instance as it stands, as inspect_model() describes one: a single
    // one for an object the file did not place, and the file's for one it did.
    std::vector<ModelInspection> instances;
    // ModelInstance::auto_drop and printable of every instance.
    std::vector<bool> auto_drops;
    std::vector<bool> printables;
};

struct ImportedModels {
    SceneStatus status{SceneStatus::model_read_failed};
    std::string message;
    // Message boxes the load showed; they informed only.
    std::vector<SettingsDialog> notices;
    // A question the load asks before anything is added: the import is
    // requested again with the answer.
    bool has_question{false};
    SettingsDialog question;
    std::vector<ImportedObject> objects;
    // edit_object(): the edited object left the plate and the objects join the
    // end of its list (load_model_objects), instead of taking its place.
    bool appended{false};
    // paste_volumes(): the first pasted volume of the object, which the object
    // list selects; -1 for none.
    int selected_volume{-1};
    // import_model() of a 3MF file opened as a project with its settings
    // (Plater::load_project): the settings of its first plate
    // (PartPlate::config) with the project's values the app keeps with the
    // plate (that plate's wipe_tower_x and wipe_tower_y, flush_volumes_matrix
    // and flush_multiplier), and the codes on that plate's layers
    // (Model::plates_custom_gcodes). The presets of the project are selected.
    bool project{false};
    ModelSettings plate_settings;
    std::vector<LayerGcode> layer_gcodes;
    // The folder where the load kept the project's information and auxiliary
    // files besides its objects (keep_project_info), which save_project()
    // writes into the project again.
    std::string project_info;
    // The load changed the presets: a project's were selected, or filaments
    // joined the plate for the extruders of a 3MF file's objects.
    bool presets_changed{false};
};

// What saving a project came to.
struct ProjectSave {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
};

// Plater::export_3mf() for "Save project" (SplitModel | ShareMesh): the
// objects of plate with their parts, settings, paint and copies, the codes on
// its layers (Model::plates_custom_gcodes), the configuration of the presets
// profiles names with the project's values of plate_settings (the wipe
// tower's position and the flushing volumes), the presets the project
// brought, and the plate (PartPlateList::store_to_3mf_structure): its own
// settings of plate_settings, the copies standing on it and its picture
// thumbnail, which the app rendered at 512 x 512 as the desktop app renders
// it (THUMBNAIL_SIZE_3MF); no picture is written for an empty path.
ProjectSave save_project(
    const std::string& path,
    const std::vector<PlateObject>& plate,
    const ProfileSelection& profiles,
    const ModelSettings& plate_settings,
    const std::vector<LayerGcode>& layer_gcodes,
    const ThumbnailImage& thumbnail,
    // What import_model() kept of the project the plate was opened from
    // (ImportedModels::project_info); empty for a plate that was not.
    const std::string& project_info = {}
);

// How a 3MF file loads (LoadType of Plater.cpp): its objects alone ("Import
// geometry only"), or as a project with its settings and presets ("Open as
// project"). Files of other types load their objects alone.
enum class ModelLoad : std::int64_t {
    geometry = 0,
    project = 1,
};

// What the object menu changes the mesh of an object with.
enum class ObjectEdit : std::int64_t {
    // Plater::priv::split_object(): every shell, or every part, an object of its own.
    split_to_objects = 0,
    // ObjectList::split(): every shell of a volume a part of its own.
    split_to_parts = 1,
    // ObjectList::fix_through_cgal(): the meshes repaired by CGAL.
    fix = 2,
    // Plater::convert_unit()
    convert_from_inches = 3,
    restore_to_inches = 4,
    convert_from_meters = 5,
    restore_to_meters = 6,
    // ObjectList::smooth_mesh() ("Subdivision mesh"): four triangles for every
    // one of the meshes.
    smooth_mesh = 7,
    // ObjectList::boolean() ("Mesh boolean"): the meshes of the object and of
    // its copies as one, its negative volumes taken out, a new object in its place.
    mesh_boolean = 8,
};

// The object menu's commands that change the meshes of the object at index
// object of plate, or of its volume at index volume (-1 for the whole object):
// its objects afterwards, written as import_model() writes them. With no
// objects the command changed nothing, and its notices say why.
ImportedModels edit_object(
    const std::vector<PlateObject>& plate,
    std::size_t object,
    ObjectEdit edit,
    int volume,
    const ProfileSelection& profiles,
    const std::string& output_prefix,
    const DialogAnswers& answers
);

// ObjectList::set_volume_type() of one volume: the volume at volume_index of
// the object at object_index of plate takes type, and the object's volumes are
// sorted by type (ModelObject::sort_volumes); selected_volume is where the
// volume went. The last solid part of an object keeps its type, and the
// notices say so; a volume of that type already changes nothing.
ImportedModels set_volume_type(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    VolumeType type,
    const ProfileSelection& profiles,
    const std::string& output_prefix
);

// GLGizmoSimplify::Configuration: how far a mesh is decimated, to a number of
// triangles (use_count) or as far as a collapsed edge keeps within max_error.
// A negative wanted_count takes decimate_ratio percent of the triangles away
// (Configuration::fix_count_by_ratio()), as the gizmo does when it opens.
struct SimplifyConfig {
    bool use_count{false};
    std::int64_t wanted_count{-1};
    float decimate_ratio{50.f};
    float max_error{1.0f};
};

struct SimplifiedVolume {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    // The triangles of the decimated mesh, and of the volume's own.
    std::int64_t triangle_count{0};
    std::int64_t original_count{0};
};

// GLGizmoSimplify::process(): the mesh of the volume at volume_index of the
// object at object_index of plate decimated by its_quadric_edge_collapse(),
// written to path for the 3D view as the volume is drawn: the object's own
// mesh in the object's coordinates, a part's in its own.
SimplifiedVolume simplify_volume(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    const SimplifyConfig& config,
    const ProfileSelection& profiles,
    const std::string& path
);

// GLGizmoSimplify::apply_simplify(): the volume takes its decimated mesh, its
// painting kept or cleared as the preference "Keep painted feature after mesh
// change" says, and the object rests on the plate; it is written as
// import_model() writes objects.
ImportedModels apply_simplify(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    const SimplifyConfig& config,
    const ProfileSelection& profiles,
    const std::string& output_prefix
);

// Where copy_objects() puts the copies it makes.
enum class CopyPlacement : std::int64_t {
    // Selection::copy_to_clipboard() and ObjectList::instances_to_separated_object():
    // every copy stands where its source stands.
    keep = 0,
    // Selection::paste_objects_from_clipboard(), count times as the clone
    // dialog pastes: each copy goes into the empty cell of the plate nearest to
    // its source, several sources keep their layout, and a copy above the
    // plate drops onto it (Plater::changed_objects).
    paste = 1,
};

// New objects copied from sources, which are objects as the plate describes
// them with only the copies taken, onto the plate that holds plate; written as
// import_model() writes objects, count rounds of them in order.
ImportedModels copy_objects(
    const std::vector<PlateObject>& plate,
    const std::vector<PlateObject>& sources,
    int count,
    CopyPlacement placement,
    const ProfileSelection& profiles,
    const std::string& output_prefix
);

// The file formats "Export as one STL" and "Export as one DRC" write.
enum class MeshFormat : std::int64_t {
    stl = 0,
    drc = 1,
};

struct MeshExport {
    SceneStatus status{SceneStatus::model_read_failed};
    std::string message;
    // The notification OrcaSlicer shows when the negative volumes could not
    // be taken out of the mesh, which then holds its positive parts alone.
    std::string warning;
};

// Plater::export_stl(false, true) for the object at object_index of plate,
// which the selection holds whole: its positive volumes less its negative
// ones, every copy of it where it stands (a lone copy at its origin), written
// to path as binary STL or as Draco with the app configuration's drc_bits.
MeshExport export_object_mesh(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    MeshFormat format,
    const ProfileSelection& profiles,
    const std::string& path
);

// Plater::priv::replace_volume_with_stl(): the volume at volume_index of the
// object at object_index of plate takes the mesh of the file at source_path,
// with the old volume's transformation, settings, type, units and painting;
// the object rests on the plate unless it was sunk, and takes the volume's
// name when it has no other. The object is written as import_model() writes
// objects; a file of more than one volume changes nothing, and the notices say so.
ImportedModels replace_volume(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    const std::string& source_path,
    const ProfileSelection& profiles,
    const std::string& output_prefix
);

// ObjectList::load_shape_object() and load_mesh_object(): a shape of
// create_mesh() ("Cube", "Cylinder", "Sphere", "Cone", "Disc" or "Torus"), a
// tenth of the largest side of the bed, joins the plate as an object named
// name, printing with filament 1, in the empty cell nearest to the centre of
// the plate. It is written as import_model() writes objects.
ImportedModels add_primitive(
    const std::vector<PlateObject>& plate,
    const std::string& shape,
    const std::string& name,
    const ProfileSelection& profiles,
    const std::string& output_prefix
);

// Selection::paste_volumes_from_clipboard(): the volumes at the indices
// volumes of source, the object the clipboard copied them from, join the
// object at object_index of plate. Over its copy instance they keep their place
// when both came from the same file and turn alike (same_input_file tells the
// first), and otherwise stand together beside its right front corner. The
// object's volumes are sorted by type as the object list sorts them, and it
// rests on the plate (Plater::changed_object). The object is written as
// import_model() writes objects.
ImportedModels paste_volumes(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t instance,
    const PlateObject& source,
    const std::vector<int>& volumes,
    bool same_input_file,
    const ProfileSelection& profiles,
    const std::string& output_prefix
);

// Plater::priv::load_files() for a model file: STL, OBJ, AMF, 3MF, STEP, SVG,
// DRC or OLTP, read with libslic3r's reader of its type; a 3MF file loads as
// load asks, a project onto an empty plate. As the desktop app does, the
// load renames nameless objects after the file, turns them by the printer's
// preferred orientation, drops objects without volume, offers to scale a model
// that looks like metres or inches, and to load objects stacked at several
// heights as one object with parts; then every object is placed as an object
// added to the plate that holds plate and the objects before it
// (load_model_objects). Every object's own mesh, the mesh of each of its other
// volumes and its mesh for the 3D view are written as mesh files whose names
// start with output_prefix.
ImportedModels import_model(
    const std::string& source_path,
    const ProfileSelection& profiles,
    const std::vector<PlateObject>& plate,
    const std::string& output_prefix,
    const DialogAnswers& answers,
    ModelLoad load = ModelLoad::geometry,
    // load is the user's answer to ProjectDropDialog, which the app
    // configuration remembers (import_project_action).
    bool chosen = false
);

// What a request of the settings of an object or of the plate carries, since
// the engine keeps no plate of its own: the overrides of the object or plate
// the tab edits, and the ones of the plate, which the settings of an object
// follow (curr_bed_type, print_sequence, spiral_mode). The requests of a preset
// tab carry nothing.
struct ModelSettingsRequest {
    // One per object the list has selected, in the plate's order; the settings
    // of the plate are a single entry.
    std::vector<ModelSettings> settings;
    ModelSettings plate;
    // The settings a part sits on: the ones of the object it belongs to
    // (TabPrintPart::m_parent_tab).
    ModelSettings parent;
};

// The edited preset of a kind, with page shown (Tab::activate_selected_page;
// the page the tab shows already when page is empty). Loading another selection
// into the tab may ask questions, as Tab::load_current_preset() does. For
// PresetKind::object and plate the settings of the object are described
// instead (TabPrintObject, TabPrintPlate), and the result carries them back.
PresetSettings describe_settings(PresetKind kind, const std::string& page, const DialogAnswers& answers, const ModelSettingsRequest& model = {});

// A field of the tab changed to text (Field::get_value(), change_opt_value,
// Tab::on_value_change, Tab::update): the edited preset gets the value and the
// corrections the desktop app makes. id is the option's, as the pages name it.
// On the settings of an object the value becomes an override of it.
PresetSettings change_setting(PresetKind kind, const std::string& page, const std::string& id, const std::string& text, const DialogAnswers& answers,
                              const ModelSettingsRequest& model = {});

// ObjectList::paste_settings_into_list() for one item of the object list: the
// settings of an object, a part or a height range (target) once the settings
// copied from an item of the same kind are pasted into them. The clipboard
// holds what ObjectList::copy_settings_to_clipboard() takes: the settings of
// the item's object with the item's own over them. A part or a range (part)
// sits on its object (object): it keeps only what differs from the object and
// the edited process preset, and takes none of the object's own options. Every
// item keeps the filament it prints with.
struct PastedSettings {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    ModelSettings settings;
};

PastedSettings paste_model_settings(const ModelSettings& clipboard, const ModelSettings& target, bool part, const ModelSettings& object);

// The undo buttons: the settings, or every modified setting when ids is
// empty, back to the saved preset (OptionsGroup::back_to_initial_value,
// Tab::on_roll_back_value). On the settings of an object the overrides are
// removed, so the values of the process preset apply again.
PresetSettings reset_settings(PresetKind kind, const std::string& page, const std::vector<std::string>& ids, const DialogAnswers& answers,
                              const ModelSettingsRequest& model = {});

// The check box of a filament override (TabFilament::add_filament_overrides_page):
// switched on, the setting takes the value of the printer or process preset it
// overrides; switched off, the filament leaves it to them.
PresetSettings set_setting_override(PresetKind kind, const std::string& page, const std::string& id, bool enabled, const DialogAnswers& answers);

// The presets a preset is compatible with (Tab::compatible_widget_create):
// their names, or none for every preset. key is "compatible_printers" or
// "compatible_prints".
PresetSettings set_compatible_presets(PresetKind kind, const std::string& page, const std::string& key, const std::vector<std::string>& presets,
                                      const DialogAnswers& answers);

// RammingDialog closed with OK (the "Set ..." button of the filament tab):
// load_key_value("filament_ramming_parameters", dlg.get_parameters()) and
// update_changed_ui(). parameters is what the dialog wrote,
// "<width %> <spacing %> <speeds...>| <time> <speed> ...".
PresetSettings set_ramming_parameters(PresetKind kind, const std::string& page, const std::string& parameters, const DialogAnswers& answers);

// The names the list of compatible presets offers, in the order of the collection.
struct PresetNames {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    std::vector<std::string> names;
};

PresetNames compatible_preset_choices(PresetKind kind, const std::string& key);

// A printer the app can send G-code to (PhysicalPrinter): its name, the printer
// presets it prints with, and the settings of its host as the desktop app's
// PhysicalPrinterDialog holds them (host_type, print_host, printhost_apikey
// and the rest of PhysicalPrinter::print_host_options()).
struct PhysicalPrinterState {
    std::string name;
    std::vector<std::string> preset_names;
    ModelSettings settings;
};

struct PhysicalPrinters {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    std::vector<PhysicalPrinterState> printers;
};

// PhysicalPrinterCollection: the printers the user set up, in its order.
PhysicalPrinters physical_printers();

// PhysicalPrinterCollection::save_printer(): a printer of that name is
// replaced, and one that is not there yet is added. renamed_from is the name it
// had before, as the dialog renames a printer.
PhysicalPrinters save_physical_printer(const PhysicalPrinterState& printer, const std::string& renamed_from);

// PhysicalPrinterCollection::delete_printer()
PhysicalPrinters delete_physical_printer(const std::string& name);

// The presets a configuration file brought in, or the files an export wrote
// (MainFrame::load_config_file and export_config).
struct ConfigTransfer {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    // The files that were written, or the presets that were imported.
    std::vector<std::string> names;
    // ConfigsOverwriteConfirmDialog: a preset of this name is already there and
    // the import has not been told what to do with it. The app asks the user
    // and imports again with the answer.
    std::string overwrite_preset;
};

// What the user answers about a preset the import would replace, in the order
// of the dialog's buttons (No, Yes, No to All, Yes to All).
enum class ConfigOverwriteAnswer : std::int64_t {
    no = 0,
    yes = 1,
    no_to_all = 2,
    yes_to_all = 3,
};

// PresetBundle::import_presets(): the user presets of the files, which are
// OrcaSlicer's .json, .zip, .orca_printer, .orca_bundle and .orca_filament.
// Only non-system presets compatible with the installed printers arrive.
//
// A preset that is already there is replaced only once the user says so:
// answers holds what was said about each preset by name, and an import that
// meets a preset it has no answer for stops and asks (overwrite_preset). The
// answers of the presets that were already there when the first import ran are
// the ones it asks for, so an import that runs again asks nothing new.
ConfigTransfer import_presets(const std::vector<std::string>& paths, const std::map<std::string, ConfigOverwriteAnswer>& answers = {});

// ExportConfigsDialog: what the dialog writes (the radio buttons it opens with).
enum class ConfigExportKind : std::int64_t {
    // "Printer config bundle(.orca_printer)": a printer with its filament and
    // process presets, one file per printer.
    printer_bundle = 0,
    // "Filament bundle(.orca_filament)": every preset of a filament name.
    filament_bundle = 1,
    // "Printer presets(.zip)"
    printer_presets = 2,
    // "Filament presets(.zip)"
    filament_presets = 3,
    // "Process presets(.zip)"
    process_presets = 4,
};

// A check box of the dialog: a printer preset for the printer exports, or the
// name a filament's presets share for the filament ones.
struct ConfigExportEntry {
    std::string name;
    // How many presets the entry carries, which the app shows beside its name.
    std::int64_t count{0};
};

// ExportConfigsDialog::data_init() and select_curr_radiobox(): what the dialog
// offers for an export kind.
struct ConfigExportOptions {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    std::vector<ConfigExportEntry> entries;
    // The line the dialog shows under the list (m_serial_text).
    std::string note;
};

ConfigExportOptions config_export_options(ConfigExportKind kind);

// The dialog's OK: the entries chosen by name are written into directory, and
// the files it wrote come back. A printer preset is written without the address
// and the credentials of its physical printer (earse_preset_fields_for_safe).
ConfigTransfer export_configs(ConfigExportKind kind, const std::vector<std::string>& names, const std::string& directory);

// CreateFilamentPresetDialog: a preset the dialog offers to make the filament
// from, with the printer it would be made for (its check box).
struct FilamentPresetChoice {
    std::string printer;
    std::string preset;
};

// What the dialog offers: the vendors and the types it lists, the filaments of
// the chosen type, and the presets of the chosen filament.
struct CreateFilamentOptions {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    // The vendor combo box (filament_vendors), and the type one
    // (the types the installed filaments are of).
    std::vector<std::string> vendors;
    std::vector<std::string> types;
    // "Create Based on Current Filament": the filaments of the chosen type by
    // the name their presets share, and the presets of the chosen one.
    std::vector<std::string> base_filaments;
    std::vector<FilamentPresetChoice> presets;
    // "Copy Current Filament Preset": every preset of the chosen type.
    std::vector<FilamentPresetChoice> copy_presets;
};

// The lists the dialog shows for the chosen type and filament; both may be
// empty, as the dialog opens with nothing chosen.
CreateFilamentOptions create_filament_options(const std::string& type, const std::string& base_filament);

// What the dialog's Create button was filled in with.
struct CreateFilamentRequest {
    // The vendor from the list, or the one the user wrote ("Can not find vendor").
    std::string vendor;
    bool custom_vendor{false};
    std::string type;
    std::string serial;
    // The presets the check boxes have chosen, of either radio button.
    std::vector<FilamentPresetChoice> presets;
};

// The Create button: the filament is cloned for every chosen preset
// (PresetCollection::clone_presets_for_filament). A question stops it until
// the app answers, as a settings request does.
struct PresetCreation {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    bool has_question{false};
    SettingsDialog question;
    // The name of the filament that was created ("<vendor> <type> <serial>").
    std::string name;
};

PresetCreation create_filament(const CreateFilamentRequest& request, const DialogAnswers& answers);

// CreatePrinterPresetDialog: what its two pages offer — the printer it names
// (the vendors and models the dialog knows, and the nozzles), and the presets
// it is made from (the vendors whose profiles the app has, their printer
// presets, and the filament and process presets that come with the chosen one).
struct CreatePrinterOptions {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    std::vector<std::string> vendors;
    std::vector<std::string> models;
    std::vector<std::string> nozzle_diameters;
    std::vector<std::string> preset_vendors;
    // "<model> @ <nozzle> nozzle", by the nozzle of each.
    std::vector<std::string> printer_presets;
    std::vector<std::string> filament_presets;
    std::vector<std::string> process_presets;
    // The printable area of the chosen printer preset, x and y of every point,
    // and how high it prints; the first page opens with them.
    std::vector<double> printable_area;
    double max_print_height{0.0};
};

CreatePrinterOptions create_printer_options(
    const std::string& vendor,
    const std::string& nozzle,
    const std::string& preset_vendor,
    const std::string& printer_preset
);

// What the dialog's Create button was filled in with.
struct CreatePrinterRequest {
    // The printer the first page names: its model, its nozzle ("0.4"), and its
    // printable area.
    std::string model;
    std::string nozzle;
    std::vector<double> printable_area;
    double max_print_height{0.0};
    std::string custom_texture;
    std::string custom_model;
    // The presets of the second page: the vendor and the printer preset it is
    // made from, and the filament and process presets that come with it.
    std::string preset_vendor;
    std::string printer_preset;
    std::vector<std::string> filament_presets;
    std::vector<std::string> process_presets;
};

PresetCreation create_printer(const CreatePrinterRequest& request, const DialogAnswers& answers);

// GuideFrame::update_custom_filaments(): a filament of the user's own, which
// the app lists to edit (EditFilamentPresetDialog).
struct CustomFilament {
    // The filament id its presets share, which the edit dialog is opened with.
    std::string id;
    // The name the presets share ("Creality PLA Orcinus").
    std::string name;
};

struct CustomFilaments {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    std::vector<CustomFilament> filaments;
};

CustomFilaments custom_filaments();

// EditFilamentPresetDialog: what it shows for a filament of the user's own —
// the vendor, the type and the serial its name carries, and its presets by the
// printer each of them is for (get_same_filament_id_presets).
struct FilamentPresetList {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    std::string name;
    std::string vendor;
    std::string type;
    std::string serial;
    std::vector<FilamentPresetChoice> presets;
};

FilamentPresetList filament_presets(const std::string& filament_id);

// The Delete button of one of its rows (EditFilamentPresetDialog::delete_preset):
// the preset is deleted once the user has answered, and the filaments select
// another one. A preset other presets inherit from cannot be deleted.
PresetCreation delete_filament_preset(const std::string& preset_name, const DialogAnswers& answers);

// The presets one side of DiffPresetDialog selects in its combo boxes. An empty
// name is the preset the app has selected, which the dialog opens with.
struct ComparedPresets {
    std::string printer;
    std::string print;
    std::string filament;
};

// A row of DiffPresetDialog: the combo boxes of one preset kind, and what the
// presets they select differ in, described the way the unsaved changes of a
// tab are.
struct PresetKindComparison {
    PresetKind kind{PresetKind::printer};
    // PresetComboBox::update() of either side, and the preset it selects.
    std::vector<PresetItem> left_presets;
    std::vector<PresetItem> right_presets;
    std::string left;
    std::string right;
    // Why the presets were not compared, which the dialog's bottom line says
    // ("One of the presets does not exist"); empty when they were.
    std::string problem;
    // The settings the presets differ in; none shows the "equal" icon.
    std::vector<PresetChange> changes;
    // enable_transfer(): the preset the app edits of this kind, and whether it
    // has unsaved changes, which only a transfer into it keeps.
    std::string edited;
    bool edited_dirty{false};
};

struct PresetComparison {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    // The printer, filament and process rows, in the dialog's order.
    std::vector<PresetKindComparison> kinds;
};

// DiffPresetDialog::show() and update_tree() for every preset kind at once, as
// ParamsPanel's compare button opens it. Each side is a copy of the app's
// presets (update_bundles_from_app), in which the printer and the process it
// selects make the lists of the presets compatible with them
// (update_compatibility). show_all lists the incompatible process and filament
// presets too ("Show all presets (including incompatible)").
PresetComparison compare_presets(const ComparedPresets& left, const ComparedPresets& right, bool show_all);

// Tab::transfer_options() for DiffPresetDialog's Transfer: the values options
// hold in the preset from move into the preset to, which the app selects and
// edits with them as unsaved changes. options are the ids the dialog lists
// ("retraction_length#0"); "extruders_count" moves the extruder count.
PresetState transfer_preset_options(PresetKind kind, const std::string& from, const std::string& to, const std::vector<std::string>& options);

// One setting the search can find (Search::Option): where it sits, which is
// what OptionsSearcher matches a query against. The texts are untranslated, as
// every text that crosses the boundary is; the app matches the query against
// the translated ones, since it holds the catalogue.
struct SearchOption {
    // The tab the setting is on, and the setting itself.
    PresetKind kind{PresetKind::print};
    std::string key;
    // The id of its field, which the tab shows the setting under.
    std::string id;
    // The page and the group of the tab it sits in.
    std::vector<UiText> page;
    std::vector<UiText> group;
    std::vector<UiText> label;
    // The settings mode the setting belongs to (ConfigOptionDef::mode).
    SettingsMode mode{SettingsMode::simple};
};

struct SearchCatalog {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    std::vector<SearchOption> options;
};

// Search::OptionsSearcher::append_options(): every setting the process,
// filament and printer tabs show, with the page and group it sits in.
SearchCatalog search_catalog();

// ParamType of EditGCodeDialog.hpp: how a placeholder is written into G-code.
enum class GcodePlaceholderType : std::int64_t {
    // A group of placeholders.
    undef = 0,
    // "key"
    scalar = 1,
    // "key[]", with the index to fill in.
    vector = 2,
    // "key[current_extruder]"
    filament_vector = 3,
};

// A node of EditGCodeDialog's list of placeholders (ParamsNode): a group, a
// subgroup, or a placeholder, in the order the dialog appends them.
struct GcodePlaceholder {
    // The group the node is in, as an index of the list; -1 for a group.
    std::int32_t parent{-1};
    GcodePlaceholderType type{GcodePlaceholderType::undef};
    // The name of a group or subgroup.
    std::vector<UiText> label;
    // The setting a placeholder stands for, and the text the list shows for it,
    // which the dialog writes into the G-code.
    std::string key;
    std::string text;
    // An icon of resources/images.
    std::string icon;
    // The dialog opens with the group expanded ("Specific for %1%").
    bool expanded{false};
};

struct GcodePlaceholders {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    // Tab::get_custom_gcode(): the G-code the dialog opens with.
    std::string value;
    std::vector<GcodePlaceholder> placeholders;
};

// EditGCodeDialog::init_params_list() for the custom G-code key of the tab of
// kind: the slicing state, the universal placeholders, the ones specific to
// the G-code, and the settings of the presets by the pages of their tabs.
GcodePlaceholders describe_gcode_placeholders(PresetKind kind, const std::string& key);

// EditGCodeDialog::selection_changed(): what the dialog says about a placeholder.
struct GcodePlaceholderInfo {
    // The label of its definition, full label first when it has both; empty
    // when it has neither, and the dialog shows the key itself.
    std::vector<UiText> label;
    // The type of its value ("float", "integer[]"), which the dialog does not translate.
    std::string type;
    // The tooltip of its definition.
    std::vector<UiText> description;
    // No definition was found ("Undef optptr").
    bool undefined{false};
};

// presets: the placeholder is one of the "Presets" group, whose definitions
// win over the placeholders of the same name.
GcodePlaceholderInfo describe_gcode_placeholder(const std::string& key, bool presets);

// Tab::edit_custom_gcode() once EditGCodeDialog is closed with OK: the edited
// G-code goes into the preset (Tab::set_custom_gcode), without the checks a
// change of the field makes.
PresetSettings edit_custom_gcode(PresetKind kind, const std::string& page, const std::string& key, const std::string& value,
                                 const DialogAnswers& answers);

// BedShape::PageType of BedShapeDialog.cpp: the shape of the printable area.
enum class BedShapeKind : std::int64_t {
    rectangle = 0,
    circle = 1,
    custom = 2,
};

// The printable area of the edited printer as its dialog shows it
// (BedShape::get_page_type and apply_optgroup_values). The rectangle values are
// the bounding box of the area and the distance of its origin from the front
// left corner, as the desktop app computes them for every shape but a circle.
struct BedShapeState {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    BedShapeKind kind{BedShapeKind::rectangle};
    double size_x{0.0};
    double size_y{0.0};
    double origin_x{0.0};
    double origin_y{0.0};
    double diameter{0.0};
    // bed_custom_texture and bed_custom_model of the printer preset.
    std::string texture;
    std::string model;
    // The points of the area, x and y per point, which a custom shape is drawn from.
    std::vector<double> points;
};

BedShapeState describe_bed_shape();

// BedShapePanel::update_shape() and TabPrinter::create_bed_shape_widget(): the
// points the dialog builds are written into the edited printer preset, with the
// texture and the model beside them. A custom shape is the horizontal
// projection of the model at custom_path (BedShapePanel::load_stl); the other
// shapes ignore it.
PresetSettings set_bed_shape(
    BedShapeKind kind,
    double size_x,
    double size_y,
    double origin_x,
    double origin_y,
    double diameter,
    const std::string& custom_path,
    const std::string& texture,
    const std::string& model,
    const DialogAnswers& answers
);

// GUI_App::save_mode(), then the tab of kind with the saved mode.
PresetSettings set_settings_mode(PresetKind kind, SettingsMode mode, const ModelSettingsRequest& model = {});

// Tab::m_variant_combo: the tab shows the values of another extruder variant
// (Tab::update_extruder_variants, switch_excluder).
PresetSettings set_settings_variant(PresetKind kind, const std::string& page, int variant, const DialogAnswers& answers,
                                    const ModelSettingsRequest& model = {});

// get_formatted_tooltip_text(): the tooltip of a setting's field, with the
// value of the parent preset and the range.
std::vector<UiText> setting_tooltip(PresetKind kind, const std::string& id);

// SavePresetDialog::Item::update(): whether a name can be saved as.
enum class PresetNameCheck : std::int64_t {
    valid = 0,
    // The preset exists and would be overwritten.
    warning = 1,
    invalid = 2,
};

struct PresetNameValidation {
    SceneStatus status{SceneStatus::engine_not_ready};
    std::string message;
    PresetNameCheck check{PresetNameCheck::invalid};
    std::vector<UiText> info;
};

PresetNameValidation check_preset_name(PresetKind kind, const std::string& name);

// SavePresetDialog's OK and Tab::save_preset(): the edited preset is saved as a
// user preset with name, replacing a user preset of that name, and becomes the
// selection.
PresetSettings save_preset(PresetKind kind, const std::string& name);

// Tab::delete_preset() for the selected user preset, which asks first; another
// preset is selected.
PresetSettings delete_preset(PresetKind kind, const DialogAnswers& answers);

}  // namespace orcinus::orca
