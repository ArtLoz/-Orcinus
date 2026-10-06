#include "orca_engine_adapter.hpp"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <fstream>
#include <limits>
#include <map>
#include <optional>
#include <memory>
#include <mutex>
#include <numeric>
#include <set>
#include <utility>
#include <vector>

#include <CGAL/Min_sphere_of_points_d_traits_3.h>
#include <CGAL/Min_sphere_of_spheres_d.h>
#include <CGAL/Simple_cartesian.h>
#include <boost/algorithm/string.hpp>
#include <boost/algorithm/string/predicate.hpp>
#include <boost/format.hpp>
#include <boost/filesystem.hpp>
#include <png.h>

#include "android_log_sink.hpp"
#include "engine_context.hpp"
#include "project_3mf.hpp"
#include "obj_colors.hpp"
#include "step_mesh.hpp"
#include "settings_dialogs.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/BuildVolume.hpp"
#include "libslic3r/ClipperUtils.hpp"
#include "libslic3r/CutUtils.hpp"
#include "libslic3r/ExPolygon.hpp"
#include "libslic3r/Exception.hpp"
#include "libslic3r/Format/STL.hpp"
#include "libslic3r/Format/STEP.hpp"
#include "libslic3r/Format/DRC.hpp"
#include "libslic3r/CSGMesh/ModelToCSGMesh.hpp"
#include "libslic3r/CSGMesh/PerformCSGMeshBooleans.hpp"
#include "libslic3r/MeshBoolean.hpp"
#include "libslic3r/TriangleMeshDeal.hpp"
#include "libslic3r/QuadricEdgeCollapse.hpp"
#include "libslic3r/MeshBoolean.hpp"
#include "libslic3r/Geometry/ConvexHull.hpp"
#include "libslic3r/GCode/GCodeProcessor.hpp"
#include "libslic3r/GCode/Thumbnails.hpp"
#include "libslic3r/I18N.hpp"
#include "libslic3r/Layer.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/GCode/WipeTower.hpp"
#include "libslic3r/ModelArrange.hpp"
#include "libslic3r/calib.hpp"
#include "libnest2d/common.hpp"
#include "libslic3r/Orient.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/Tesselate.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "libslic3r/Utils.hpp"
#include "libslic3r_version.h"
#include "slic3r/Utils/ASCIIFolding.hpp"

#include "nanosvg/nanosvg.h"
#include "nanosvg/nanosvgrast.h"
#include "slic3r/GUI/LibVGCode/LibVGCodeWrapper.hpp"
#include "toolpaths_file.hpp"
#include "slice_info.hpp"

#if !defined(__BYTE_ORDER__) || __BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__
#error "Mesh files are written in the native byte order, which must be little-endian"
#endif

namespace orcinus::orca {
namespace {

namespace fs = boost::filesystem;
using detail::engine;

// Warning, until the app configuration names its own level (log_severity_level).
constexpr unsigned int orca_log_level = 2;

std::unique_ptr<EngineInitialization> initialization;

std::mutex job_mutex;
std::string active_job_id;
Slic3r::Print* active_print = nullptr;
bool cancel_requested = false;

// Releases the job slot taken by acquire_job().
class ActiveJob final {
public:
    ActiveJob() = default;
    ActiveJob(const ActiveJob&) = delete;
    ActiveJob& operator=(const ActiveJob&) = delete;

    ~ActiveJob()
    {
        const std::lock_guard<std::mutex> lock(job_mutex);
        active_job_id.clear();
        active_print = nullptr;
        cancel_requested = false;
    }
};

bool acquire_job(const std::string& job_id)
{
    const std::lock_guard<std::mutex> lock(job_mutex);
    if (!active_job_id.empty()) {
        return false;
    }
    active_job_id = job_id;
    active_print = nullptr;
    cancel_requested = false;
    return true;
}

// Registers the print for cancellation; returns false if cancel() already ran.
bool attach_print(Slic3r::Print* print)
{
    const std::lock_guard<std::mutex> lock(job_mutex);
    active_print = print;
    return !cancel_requested;
}

bool cancellation_requested()
{
    const std::lock_guard<std::mutex> lock(job_mutex);
    return cancel_requested;
}

// Plater::priv::generate_thumbnails() with the thumbnails the app rendered:
// one for every size the export asks for, read from the file of that size. A
// size the app rendered none of is left out, as an invalid thumbnail is.
Slic3r::ThumbnailsList load_thumbnails(const std::vector<ThumbnailImage>& images, const Slic3r::ThumbnailsParams& params)
{
    Slic3r::ThumbnailsList thumbnails;
    for (const Slic3r::Vec2d& size : params.sizes) {
        // round to ints
        const long width = std::lround(size.x());
        const long height = std::lround(size.y());
        const auto image = std::find_if(images.begin(), images.end(), [width, height](const ThumbnailImage& candidate) {
            return candidate.width == width && candidate.height == height;
        });
        if (image == images.end()) {
            continue;
        }
        Slic3r::ThumbnailData data;
        data.set(unsigned(width), unsigned(height));
        std::ifstream file(image->path, std::ios::binary);
        file.read(reinterpret_cast<char*>(data.pixels.data()), std::streamsize(data.pixels.size()));
        if (!file || !data.is_valid()) {
            continue;
        }
        thumbnails.push_back(std::move(data));
    }
    return thumbnails;
}

SliceResult failure(const SliceStatus status, std::string message)
{
    SliceResult result;
    result.status = status;
    result.message = std::move(message);
    return result;
}

// The filaments of the plate, in the order the sidebar lists them; a request
// that carries none prints with the one filament it names.
std::vector<std::string> filaments_of(const ProfileSelection& profiles)
{
    return profiles.filaments.empty() ? std::vector<std::string>{profiles.filament} : profiles.filaments;
}

bool is_selected(const Slic3r::PresetBundle& bundle, const ProfileSelection& profiles)
{
    return bundle.printers.get_selected_preset_name() == profiles.printer
        && bundle.prints.get_selected_preset_name() == profiles.process
        && bundle.filaments.get_selected_preset_name() == profiles.filament
        && bundle.filament_presets == filaments_of(profiles);
}

// The configuration of the selected presets. Other presets are selected the way
// the desktop app restores a selection at start-up, on a copy of the app
// configuration: the printer model and filament are marked as installed, as the
// Setup Wizard does, and the process and filament remembered for that printer
// are selected by load_selections(). The configuration a print is applied with
// keeps the extruder variants of every filament (apply_extruder false, as
// Plater::priv::update_background_process()), which Print::apply() resolves.
SliceStatus select_profiles(
    Slic3r::PresetBundle& bundle,
    const ProfileSelection& profiles,
    Slic3r::DynamicPrintConfig& config,
    std::string& message,
    const bool apply_extruder = true
)
{
    if (is_selected(bundle, profiles)) {
        config = bundle.full_config(apply_extruder);
        return SliceStatus::success;
    }

    const Slic3r::Preset* printer = bundle.printers.find_preset(profiles.printer, false);
    if (printer == nullptr) {
        message = "Unknown printer profile: " + profiles.printer;
        return SliceStatus::profile_not_found;
    }
    // A user's printer, or one a project brought, has the vendor of the
    // system printer it inherits from; one that inherits nothing has none.
    const Slic3r::Preset* system_printer = printer->vendor != nullptr ? printer : bundle.printers.get_preset_parent(*printer);
    if (bundle.prints.find_preset(profiles.process, false) == nullptr) {
        message = "Unknown process profile: " + profiles.process;
        return SliceStatus::profile_not_found;
    }
    for (const std::string& filament : filaments_of(profiles)) {
        if (bundle.filaments.find_preset(filament, false) == nullptr) {
            message = "Unknown filament profile: " + filament;
            return SliceStatus::profile_not_found;
        }
    }

    Slic3r::AppConfig app_config = *engine().config;
    if (system_printer != nullptr && system_printer->vendor != nullptr) {
        app_config.set_variant(
            system_printer->vendor->id,
            system_printer->config.opt_string("printer_model"),
            system_printer->config.opt_string("printer_variant"),
            true
        );
    }
    for (const std::string& filament : filaments_of(profiles)) {
        app_config.set(Slic3r::AppConfig::SECTION_FILAMENTS, filament, "true");
    }
    app_config.set("presets", PRESET_PRINTER_NAME, profiles.printer);
    app_config.set_printer_setting(profiles.printer, PRESET_PRINT_NAME, profiles.process);
    app_config.set_printer_setting(profiles.printer, PRESET_FILAMENT_NAME, profiles.filament);
    engine().bundle_follows_config = false;
    bundle.load_selections(app_config);
    // The presets a project brought: load_selections() keeps to the presets it
    // finds compatible with the printer, and the request names the ones the
    // engine reported selected.
    if (bundle.printers.get_selected_preset_name() != profiles.printer) {
        bundle.printers.select_preset_by_name(profiles.printer, true);
    }
    if (bundle.prints.get_selected_preset_name() != profiles.process) {
        bundle.prints.select_preset_by_name(profiles.process, true);
    }

    // The plate prints with every filament the app listed, so the bundle is
    // given as many slots (PresetBundle::set_num_filaments / set_filament_preset).
    const std::vector<std::string> filaments = filaments_of(profiles);
    if (bundle.filament_presets.size() != filaments.size()) {
        bundle.set_num_filaments(unsigned(filaments.size()), std::string());
    }
    for (std::size_t index = 0; index < filaments.size(); ++index) {
        bundle.set_filament_preset(index, filaments[index]);
    }
    bundle.update_multi_material_filament_presets();

    if (bundle.printers.get_selected_preset_name() != profiles.printer
        || bundle.prints.get_selected_preset_name() != profiles.process
        || bundle.filament_presets != filaments) {
        message = "Orca selected printer '" + bundle.printers.get_selected_preset_name() + "', process '"
            + bundle.prints.get_selected_preset_name() + "', filament '"
            + (bundle.filament_presets.empty() ? std::string() : bundle.filament_presets.front())
            + "': the requested profiles are not compatible";
        return SliceStatus::profile_not_found;
    }

    config = bundle.full_config(apply_extruder);
    return SliceStatus::success;
}

bool write_mesh(const indexed_triangle_set& its, const std::string& path);

// Reads a mesh file write_mesh() wrote; false for a file of any other kind.
bool read_mesh(const std::string& path, indexed_triangle_set& its)
{
    std::ifstream in(path, std::ios::binary);
    char magic[sizeof(mesh_file_magic)];
    if (!in.read(magic, sizeof(magic)) || std::memcmp(magic, mesh_file_magic, sizeof(magic)) != 0) {
        return false;
    }
    std::uint32_t header[3] = {0, 0, 0};
    if (!in.read(reinterpret_cast<char*>(header), sizeof(header)) || header[0] != mesh_file_version) {
        return false;
    }
    std::vector<float> positions(std::size_t(header[1]) * 3);
    std::vector<std::uint32_t> corners(std::size_t(header[2]) * 3);
    if (!in.read(reinterpret_cast<char*>(positions.data()), std::streamsize(positions.size() * sizeof(float)))
        || !in.read(reinterpret_cast<char*>(corners.data()), std::streamsize(corners.size() * sizeof(std::uint32_t)))) {
        return false;
    }
    its.vertices.clear();
    its.indices.clear();
    its.vertices.reserve(header[1]);
    its.indices.reserve(header[2]);
    for (std::size_t vertex = 0; vertex < header[1]; ++vertex) {
        its.vertices.emplace_back(positions[3 * vertex], positions[3 * vertex + 1], positions[3 * vertex + 2]);
    }
    for (std::size_t triangle = 0; triangle < header[2]; ++triangle) {
        const std::uint32_t a = corners[3 * triangle], b = corners[3 * triangle + 1], c = corners[3 * triangle + 2];
        if (a >= header[1] || b >= header[1] || c >= header[1]) {
            return false;
        }
        its.indices.emplace_back(int(a), int(b), int(c));
    }
    return true;
}

bool load_model(const std::string& model_path, Slic3r::Model& model)
{
    if (model_path.empty()) {
        Slic3r::ModelObject* object = model.add_object();
        object->name = "calibration-cube-20mm";
        object->add_volume(Slic3r::TriangleMesh(Slic3r::its_make_cube(20.0, 20.0, 20.0)));
        return true;
    }
    // A mesh import_model() wrote, in the coordinates it had in the file's
    // object; the object's frame, or the part's matrix, places it again.
    indexed_triangle_set its;
    if (read_mesh(model_path, its)) {
        if (its.indices.empty()) {
            return false;
        }
        Slic3r::ModelObject* object = model.add_object();
        object->add_volume(Slic3r::TriangleMesh(std::move(its)), Slic3r::ModelVolumeType::MODEL_PART, false);
        return true;
    }
    return Slic3r::load_stl(model_path.c_str(), &model) && !model.objects.empty();
}

// The models read for the objects of the plate, by file, each with the file's
// modification time. Guarded by engine().mutex.
struct LoadedModel {
    std::time_t modified{0};
    Slic3r::Model model;
};
std::map<std::string, std::unique_ptr<LoadedModel>> loaded_models;

// The object of model_path as read. The file is read only when its model is
// not loaded yet or the file changed since. Returns nullptr when the file
// cannot be read.
const Slic3r::ModelObject* cached_object(const std::string& model_path)
{
    boost::system::error_code error;
    const std::time_t modified = model_path.empty() ? 0 : fs::last_write_time(model_path, error);
    if (error) {
        return nullptr;
    }
    auto loaded = loaded_models.find(model_path);
    if (loaded == loaded_models.end() || loaded->second->modified != modified) {
        auto read = std::make_unique<LoadedModel>();
        read->modified = modified;
        if (!load_model(model_path, read->model)) {
            return nullptr;
        }
        loaded = loaded_models.insert_or_assign(model_path, std::move(read)).first;
    }
    return loaded->second->model.objects.front();
}

// Adds a copy of the object of model_path to model; nullptr when the file cannot be read.
Slic3r::ModelObject* add_loaded_object(const std::string& model_path, Slic3r::Model& model)
{
    const Slic3r::ModelObject* read = cached_object(model_path);
    return read == nullptr ? nullptr : model.add_object(*read);
}

// Forgets the models no object of the plate, nor any of their parts, is loaded from.
void keep_models_of(const std::vector<PlateObject>& plate)
{
    for (auto loaded = loaded_models.begin(); loaded != loaded_models.end();) {
        const bool used = std::any_of(plate.begin(), plate.end(), [&loaded](const PlateObject& object) {
            return object.model_path == loaded->first
                || std::any_of(object.parts.begin(), object.parts.end(), [&loaded](const ObjectPart& part) {
                       return !part.model_path.empty() && part.model_path == loaded->first;
                   });
        });
        loaded = used ? std::next(loaded) : loaded_models.erase(loaded);
    }
}

// ModelVolumeType of Model.hpp for the type the app names.
Slic3r::ModelVolumeType volume_type_of(const VolumeType type)
{
    switch (type) {
    case VolumeType::negative:
        return Slic3r::ModelVolumeType::NEGATIVE_VOLUME;
    case VolumeType::modifier:
        return Slic3r::ModelVolumeType::PARAMETER_MODIFIER;
    case VolumeType::support_blocker:
        return Slic3r::ModelVolumeType::SUPPORT_BLOCKER;
    case VolumeType::support_enforcer:
        return Slic3r::ModelVolumeType::SUPPORT_ENFORCER;
    case VolumeType::part:
    default:
        return Slic3r::ModelVolumeType::MODEL_PART;
    }
}

// create_mesh() of GUI_ObjectList.cpp: the shapes the desktop app adds to an
// object, sized from the bed and from the object they join.
Slic3r::TriangleMesh create_mesh(const std::string& type_name, const Slic3r::BoundingBoxf3& bb, const double side)
{
    Slic3r::TriangleMesh mesh;
    if (type_name == "Cube")
        // Sitting on the print bed, left front front corner at (0, 0).
        mesh = Slic3r::TriangleMesh(Slic3r::its_make_cube(side, side, side));
    else if (type_name == "Cylinder")
        // Centered around 0, sitting on the print bed.
        // The cylinder has the same volume as the box above.
        mesh = Slic3r::TriangleMesh(Slic3r::its_make_cylinder(0.5 * side, side));
    else if (type_name == "Sphere")
        // Centered around 0, half the sphere below the print bed, half above.
        // The sphere has the same volume as the box above.
        mesh = Slic3r::TriangleMesh(Slic3r::its_make_sphere(0.5 * side, PI / 90));
    else if (type_name == "Slab")
        // Sitting on the print bed, left front front corner at (0, 0).
        mesh = Slic3r::TriangleMesh(Slic3r::its_make_cube(bb.size().x() * 1.5, bb.size().y() * 1.5, bb.size().z() * 0.5));
    else if (type_name == "Cone")
        mesh = Slic3r::TriangleMesh(Slic3r::its_make_cone(0.5 * side, side));
    else if (type_name == "Disc")
        mesh = Slic3r::TriangleMesh(Slic3r::its_make_cylinder(0.5 * side, 0.2f));
    else if (type_name == "Torus")
        mesh = Slic3r::TriangleMesh(Slic3r::its_make_torus(0.5 * side, 0.125 * side, (PI / 60)));
    return mesh;
}

// compute_colum_count() of PartPlate.hpp: the plates stand in rows of this
// many, as close to a square as they fill.
int plate_columns(const int count)
{
    const float value = std::sqrt(static_cast<float>(count));
    const float round_value = std::round(value);
    return value > round_value ? static_cast<int>(round_value) + 1 : static_cast<int>(round_value);
}

// PartPlateList::MAX_PLATES_COUNT
constexpr int max_plates_count = 36;

// PartPlateList's layout for the printer of config: every plate is as wide
// and deep as the printable area in whole millimetres (reset_size() takes the
// Bed3D's printable box, which the axes' tip widens by what it subtracts),
// with a fifth of that between plates (LOGICAL_PART_PLATE_GAP).
struct PlateLayout {
    int width{0};
    int depth{0};
    // plate_stride_x() and plate_stride_y()
    double stride_x{0.0};
    double stride_y{0.0};
};

PlateLayout plate_layout_of(const Slic3r::DynamicPrintConfig& config)
{
    constexpr double logical_part_plate_gap = 1. / 5.;
    const Slic3r::BoundingBoxf area(config.option<Slic3r::ConfigOptionPoints>("printable_area")->values);
    PlateLayout layout;
    layout.width = static_cast<int>(area.size().x());
    layout.depth = static_cast<int>(area.size().y());
    layout.stride_x = layout.width * (1. + logical_part_plate_gap);
    layout.stride_y = layout.depth * (1. + logical_part_plate_gap);
    return layout;
}

// PartPlateList::compute_origin() of the plate at index among count plates:
// the first stands at the origin, the next ones to its right, row after row
// towards the front.
Slic3r::Vec2d plate_origin_at(const Slic3r::DynamicPrintConfig& config, const int index, const int count)
{
    const PlateLayout layout = plate_layout_of(config);
    const int columns = plate_columns(count);
    return {(index % columns) * layout.stride_x, -(index / columns) * layout.stride_y};
}

// The origin of the current plate.
Slic3r::Vec2d plate_origin_of(const Slic3r::DynamicPrintConfig& config)
{
    return plate_origin_at(config, detail::engine().plate_index, detail::engine().plate_count);
}

// PartPlate::get_shape() of the plate at index among count plates: the
// printable area moved to its origin.
Slic3r::Pointfs plate_shape_at(const Slic3r::DynamicPrintConfig& config, const int index, const int count)
{
    Slic3r::Pointfs shape = config.option<Slic3r::ConfigOptionPoints>("printable_area")->values;
    const Slic3r::Vec2d origin = plate_origin_at(config, index, count);
    for (Slic3r::Vec2d& point : shape) {
        point += origin;
    }
    return shape;
}

// PartPlate::get_shape() of the current plate.
Slic3r::Pointfs plate_shape_of(const Slic3r::DynamicPrintConfig& config)
{
    return plate_shape_at(config, detail::engine().plate_index, detail::engine().plate_count);
}

// Plater::priv::update_print_volume_state(): the build volume of the current plate.
Slic3r::BuildVolume build_volume_of(const Slic3r::DynamicPrintConfig& config)
{
    return Slic3r::BuildVolume(
        plate_shape_of(config),
        config.opt_float("printable_height"),
        {},
        {});
}

// PartPlate::get_build_volume() of the plate at index among count plates: the
// printable area up to the printable height, grown by BuildVolume::SceneEpsilon.
Slic3r::BoundingBoxf3 plate_box_at(const Slic3r::DynamicPrintConfig& config, const int index, const int count)
{
    const Slic3r::BoundingBoxf area(plate_shape_at(config, index, count));
    const double eps = Slic3r::BuildVolume::SceneEpsilon;
    return Slic3r::BoundingBoxf3(
        Slic3r::Vec3d(area.min.x() - eps, area.min.y() - eps, -eps),
        Slic3r::Vec3d(area.max.x() + eps, area.max.y() + eps, config.opt_float("printable_height") + eps));
}

// PartPlateList::find_instance(): the first of count plates the copy crosses
// (PartPlate::intersect_instance, which puts it among the plate's instances);
// -1 for a copy on none.
int plate_of(const Slic3r::ModelObject& object, const std::size_t instance, const Slic3r::DynamicPrintConfig& config, const int count)
{
    const Slic3r::BoundingBoxf3 box = object.instance_convex_hull_bounding_box(instance);
    for (int plate = 0; plate < count; ++plate) {
        if (plate_box_at(config, plate, count).intersects(box)) {
            return plate;
        }
    }
    return -1;
}

// load_files()'s translate_old: a project older than 1.5.9 laid count plates
// out as wide and deep as the plates were then, each a whole millimetre more
// (reset_size(current_width + Bed3D::Axes::DefaultTipRadius, ...)), so the
// copies of every plate but the first move to where their plate stands now
// (PartPlate::translate_all_instance() by compute_origin_using_new_size() less
// the old origin). A copy belongs to the first plate of the old layout it
// crosses (reload_all_objects()). The sizes are those of config, the printer
// selected before the load (get_plate_size()).
void translate_old_plates(Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config, const int count)
{
    constexpr double logical_part_plate_gap = 1. / 5.;
    // Bed3D::Axes::DefaultTipRadius
    constexpr float default_tip_radius = 2.5f * 0.5f;
    const PlateLayout layout = plate_layout_of(config);
    const int old_width = static_cast<int>(layout.width + default_tip_radius);
    const int old_depth = static_cast<int>(layout.depth + default_tip_radius);
    const int columns = plate_columns(count);
    const auto origin = [columns](const int index, const double stride_x, const double stride_y) {
        return Slic3r::Vec2d((index % columns) * stride_x, -(index / columns) * stride_y);
    };
    const Slic3r::BoundingBoxf area(config.option<Slic3r::ConfigOptionPoints>("printable_area")->values);
    const double eps = Slic3r::BuildVolume::SceneEpsilon;
    const double height = config.opt_float("printable_height");
    for (Slic3r::ModelObject* object : model.objects) {
        for (std::size_t instance = 0; instance < object->instances.size(); ++instance) {
            const Slic3r::BoundingBoxf3 box = object->instance_convex_hull_bounding_box(instance);
            for (int plate = 0; plate < count; ++plate) {
                const Slic3r::Vec2d old_origin =
                    origin(plate, old_width * (1. + logical_part_plate_gap), old_depth * (1. + logical_part_plate_gap));
                const Slic3r::BoundingBoxf3 old_box(
                    Slic3r::Vec3d(area.min.x() + old_origin.x() - eps, area.min.y() + old_origin.y() - eps, -eps),
                    Slic3r::Vec3d(area.max.x() + old_origin.x() + eps, area.max.y() + old_origin.y() + eps, height + eps));
                if (!old_box.intersects(box)) {
                    continue;
                }
                if (plate > 0) {
                    const Slic3r::Vec2d shift = origin(plate, layout.stride_x, layout.stride_y) - old_origin;
                    Slic3r::ModelInstance* copy = object->instances[instance];
                    copy->set_offset(copy->get_offset() + Slic3r::Vec3d(shift.x(), shift.y(), 0.));
                }
                break;
            }
        }
    }
}

// PartPlate::get_build_volume() of the current plate.
Slic3r::BoundingBoxf3 plate_box_of(const Slic3r::DynamicPrintConfig& config)
{
    const Slic3r::BoundingBoxf area(plate_shape_of(config));
    const double eps = Slic3r::BuildVolume::SceneEpsilon;
    return Slic3r::BoundingBoxf3(
        Slic3r::Vec3d(area.min.x() - eps, area.min.y() - eps, -eps),
        Slic3r::Vec3d(area.max.x() + eps, area.max.y() + eps, config.opt_float("printable_height") + eps));
}

// PartPlate::empty() for the plate an object joins: no other object's
// instance meets the plate (PartPlate::intersect_instance). joining is null
// for the plate as it is.
bool plate_empty(const Slic3r::Model& model, const Slic3r::ModelObject* joining, const Slic3r::BoundingBoxf3& plate_box)
{
    for (const Slic3r::ModelObject* object : model.objects) {
        for (std::size_t instance = 0; object != joining && instance < object->instances.size(); ++instance) {
            if (plate_box.intersects(object->instance_convex_hull_bounding_box(instance))) {
                return false;
            }
        }
    }
    return true;
}

// GLCanvas3D::get_nearest_empty_cell(), by default with its 10 mm step: the
// cell of the plate nearest to start_point that no instance's convex hull
// covers, or a point beside start_point when every cell is covered. The cells
// are those of GLCanvas3D::get_empty_cells() as Bambu Studio has it, and
// OrcaSlicer had it until commit 8f777555: that commit keeps every cell some
// instance leaves free, which puts an object added to a plate onto the one in
// its centre.
Slic3r::Vec2f nearest_empty_cell(
    const Slic3r::Model& model,
    const Slic3r::BoundingBoxf3& plate_box,
    const Slic3r::BoundingBoxf& bed,
    const Slic3r::Vec2f& start_point,
    const Slic3r::Vec2f& step = Slic3r::Vec2f(10.0f, 10.0f)
)
{
    using namespace Slic3r;
    std::vector<Vec2f> cells;
    const float min_x = start_point.x() - step(0) * int((start_point.x() - plate_box.min.x()) / step(0));
    const float min_y = start_point.y() - step(1) * int((start_point.y() - plate_box.min.y()) / step(1));
    for (float x = min_x; x < plate_box.max.x() - step(0) / 2; x += step(0)) {
        for (float y = min_y; y < plate_box.max.y() - step(1) / 2; y += step(1)) {
            cells.emplace_back(x, y);
        }
    }
    for (const ModelObject* object : model.objects) {
        const ModelInstance* first = object->instances.front();
        const Polygon hull = object->convex_hull_2d(Geometry::assemble_transform(
            {0.0, 0.0, first->get_offset().z()}, first->get_rotation(), first->get_scaling_factor(), first->get_mirror()));
        if (hull.empty()) {
            continue;
        }
        for (const ModelInstance* instance : object->instances) {
            Geometry::Transformation transformation;
            transformation.set_offset({scale_(instance->get_offset().x()), scale_(instance->get_offset().y()), 0.0});
            transformation.set_rotation(Z, instance->get_rotation().z() - first->get_rotation().z());
            const Polygon instance_hull = hull.transform(transformation.get_matrix());
            cells.erase(std::remove_if(cells.begin(), cells.end(), [&instance_hull](const Vec2f& cell) {
                return instance_hull.contains(Point(scale_(cell.x()), scale_(cell.y())));
            }), cells.end());
        }
    }
    if (cells.empty()) {
        // GLCanvas3D::get_size_proportional_to_max_bed_size(0.05)
        const double offset = 0.05 * std::max(bed.size().x(), bed.size().y());
        return {float(start_point.x() + offset), float(start_point.y() + offset)};
    }
    // Nearest to start_point first; of equally near cells, the first found.
    return *std::min_element(cells.begin(), cells.end(), [&start_point](const Vec2f& a, const Vec2f& b) {
        return (a - start_point).norm() < (b - start_point).norm();
    });
}

// Plater::priv::load_model_objects() for an object added to the plate that
// model holds: the mesh is centred around the origin and the object rests on
// the plate, on the plate's centre when no other object is on the plate, and
// otherwise in the empty cell nearest to the centre.
void place_new_object(Slic3r::Model& model, Slic3r::ModelObject& object, const Slic3r::DynamicPrintConfig& config)
{
    object.center_around_origin();
    Slic3r::ModelInstance* instance = object.add_instance();
    object.ensure_on_bed();
    const Slic3r::BoundingBoxf bed = build_volume_of(config).bounding_volume2d();
    const Slic3r::BoundingBoxf3 plate_box = plate_box_of(config);
    const Slic3r::Vec2d start_point = bed.center();
    const double z = instance->get_offset().z();
    if (plate_empty(model, &object, plate_box)) {
        instance->set_offset({start_point.x(), start_point.y(), z});
    } else {
        const Slic3r::Vec2f cell = nearest_empty_cell(model, plate_box, bed, start_point.cast<float>());
        instance->set_offset({cell.x(), cell.y(), z});
    }
}

// The frame of a loaded object: the transformation import_model() gave its own
// mesh, or, for an STL file or the cube, the mesh centred around the origin as
// for inspect_model().
void set_frame(Slic3r::ModelObject& object, const std::vector<double>& matrix)
{
    if (matrix.size() != 16) {
        object.center_around_origin();
        return;
    }
    Slic3r::Transform3d transformation = Slic3r::Transform3d::Identity();
    std::copy(matrix.begin(), matrix.end(), transformation.data());
    object.volumes.front()->set_transformation(Slic3r::Geometry::Transformation(transformation));
    object.invalidate_bounding_box();
}

// Gives a loaded object the transformations from the app, as the desktop app
// commits a moved instance (GLCanvas3D::do_move): every copy takes its
// transformation. drop_instances() lets them fall onto the plate once the
// object has all of its parts.
void place_at(Slic3r::ModelObject& object, const std::vector<ObjectPlacement>& instances)
{
    for (const ObjectPlacement& placement : instances) {
        Slic3r::Transform3d transformation = Slic3r::Transform3d::Identity();
        std::copy(placement.matrix.begin(), placement.matrix.end(), transformation.data());
        Slic3r::ModelInstance* instance = object.add_instance();
        instance->set_transformation(Slic3r::Geometry::Transformation(transformation));
        instance->auto_drop = placement.auto_drop;
        // ObjectList::toggle_printable_state()
        instance->printable = placement.printable;
        // The copy's place in the assembly view.
        if (placement.assemble_matrix.size() == 16) {
            Slic3r::Transform3d assemble = Slic3r::Transform3d::Identity();
            std::copy(placement.assemble_matrix.begin(), placement.assemble_matrix.end(), assemble.data());
            instance->set_assemble_transformation(Slic3r::Geometry::Transformation(assemble));
        }
        if (placement.offset_to_assembly.size() == 3) {
            instance->set_offset_to_assembly(Slic3r::Vec3d(placement.offset_to_assembly[0], placement.offset_to_assembly[1], placement.offset_to_assembly[2]));
        }
    }
}

// A copy above the plate drops onto it unless its auto drop is off; every part
// printed with the object counts, as ModelObject::get_instance_min_z() takes
// them all.
void drop_instances(Slic3r::ModelObject& object)
{
    for (std::size_t index = 0; index < object.instances.size(); ++index) {
        const double shift_z = object.get_instance_min_z(index);
        if (object.instances[index]->auto_drop && shift_z > Slic3r::SINKING_Z_THRESHOLD && shift_z != 0.0) {
            object.translate_instance(index, Slic3r::Vec3d(0.0, 0.0, -shift_z));
        }
    }
}

// GLCanvas3D::get_size_proportional_to_max_bed_size(): a share of the largest
// side of the bed, which sizes the shapes the desktop app adds to an object.
double size_proportional_to_max_bed_size(const Slic3r::DynamicPrintConfig& config, const double factor)
{
    const Slic3r::BoundingBoxf bed = build_volume_of(config).bounding_volume2d();
    return factor * std::max(bed.size().x(), bed.size().y());
}

// ModelVolume::source's object, volume and mesh offset, as the app keeps them.
VolumeOrigin origin_of(const Slic3r::ModelVolume::Source& source)
{
    return {source.object_idx, source.volume_idx, {source.mesh_offset.x(), source.mesh_offset.y(), source.mesh_offset.z()}};
}

// A volume the app knows no origin of (ModelVolume::Source's defaults) keeps
// the one it got as it was made, as the calibration cube's centred mesh.
void apply_origin(Slic3r::ModelVolume& volume, const VolumeOrigin& origin)
{
    if (origin.object_idx < 0 && origin.volume_idx < 0 && origin.mesh_offset == std::array<double, 3>{0.0, 0.0, 0.0}) {
        return;
    }
    volume.source.object_idx = origin.object_idx;
    volume.source.volume_idx = origin.volume_idx;
    volume.source.mesh_offset = Slic3r::Vec3d(origin.mesh_offset[0], origin.mesh_offset[1], origin.mesh_offset[2]);
}

// The mesh stats the object list reports of volume (TriangleMeshStats).
MeshErrors mesh_errors_of(const Slic3r::ModelVolume& volume)
{
    const Slic3r::TriangleMeshStats& stats = volume.mesh().stats();
    const Slic3r::RepairedMeshErrors& repaired = stats.repaired_errors;
    return {std::int64_t(stats.open_edges), repaired.edges_fixed, repaired.degenerate_facets, repaired.facets_removed,
            repaired.facets_reversed, repaired.backwards_edges};
}

// The volume's mesh, read again from the app's file, knows again what the
// repair of its source fixed (TriangleMesh(its, repaired_errors), as a
// project's mesh_stat is read).
void apply_repaired_errors(Slic3r::ModelVolume& volume, const MeshErrors& errors)
{
    const Slic3r::RepairedMeshErrors repaired{errors.edges_fixed, errors.degenerate_facets, errors.facets_removed, errors.facets_reversed,
                                              errors.backwards_edges};
    if (!repaired.repaired()) {
        return;
    }
    indexed_triangle_set its = volume.mesh().its;
    volume.set_mesh(Slic3r::TriangleMesh(std::move(its), repaired));
}

// Loads one object of the plate into model: its own mesh in its frame, its
// copies, its settings, painting and parts, its height ranges; then its copies
// drop onto the plate. An object without a placement is placed as an object
// added to the plate that holds the objects before it.
Slic3r::ModelObject* load_object(const PlateObject& object, const Slic3r::DynamicPrintConfig& config, Slic3r::Model& model, std::string& message)
{
    Slic3r::ModelObject* loaded = add_loaded_object(object.model_path, model);
    if (loaded == nullptr) {
        message = "Unable to read model " + object.model_path;
        return nullptr;
    }
    loaded->origin_translation = Slic3r::Vec3d(object.origin_translation[0], object.origin_translation[1], object.origin_translation[2]);
    set_frame(*loaded, object.matrix);
    if (!object.instances.empty()) {
        place_at(*loaded, object.instances);
    } else {
        place_new_object(model, *loaded, config);
    }
    // The settings of the object (ModelObject::config), which Print::apply
    // lays over the process preset for it.
    loaded->config.assign_config(detail::model_config(object.settings));
    // The settings of its own mesh (the first ModelVolume's config), and the
    // units the mesh was converted from.
    if (!object.name.empty()) {
        loaded->name = object.name;
    }
    if (!loaded->volumes.empty()) {
        Slic3r::ModelVolume& own = *loaded->volumes.front();
        if (!object.volume_name.empty()) {
            own.name = object.volume_name;
        }
        own.config.assign_config(detail::model_config(object.volume_settings));
        own.source.is_converted_from_inches = object.volume_from_inches;
        own.source.is_converted_from_meters = object.volume_from_meters;
        if (!object.volume_input_file.empty()) {
            own.source.input_file = object.volume_input_file;
        }
        apply_origin(own, object.volume_origin);
        own.cut_info = detail::cut_info_of(object.volume_cut_info);
        apply_repaired_errors(own, object.volume_mesh_errors);
    }
    // The cut the object is a part of (as _BBS_3MF_Importer applies it).
    if (object.cut_id.id != 0) {
        loaded->cut_id = Slic3r::CutObjectBase(Slic3r::ObjectID(std::size_t(object.cut_id.id)), std::size_t(object.cut_id.check_sum),
                                               std::size_t(object.cut_id.connectors_cnt));
    }
    // The colours the object is painted with (GLGizmoMmuSegmentation),
    // which MultiMaterialSegmentation prints.
    if (!loaded->volumes.empty() && !apply_painted_facets(*loaded->volumes.front(), object.painted)) {
        message = "The painted facets of the object could not be read";
        return nullptr;
    }
    // The text or the SVG its own mesh was embossed from (GLGizmoEmboss, GLGizmoSVG).
    if (!loaded->volumes.empty() && !detail::read_emboss(object.volume_emboss, *loaded->volumes.front())) {
        message = "The embossed text or SVG of the object could not be read";
        return nullptr;
    }
    // The parts of the object (ModelObject::volumes), which print with it,
    // are taken out of it, or change its settings: a shape the desktop app
    // generates, or a volume a model file brought.
    for (const ObjectPart& part : object.parts) {
        Slic3r::ModelVolume* volume = nullptr;
        if (!part.model_path.empty()) {
            const Slic3r::ModelObject* read = cached_object(part.model_path);
            if (read == nullptr || read->volumes.empty()) {
                message = "Unable to read part " + part.model_path;
                return nullptr;
            }
            Slic3r::TriangleMesh mesh = read->volumes.front()->mesh();
            volume = loaded->add_volume(std::move(mesh), volume_type_of(part.type), false);
            volume->name = part.name;
        } else {
            const Slic3r::BoundingBoxf3 instance_bb = loaded->instance_bounding_box(0);
            Slic3r::TriangleMesh mesh = create_mesh(part.shape, instance_bb, size_proportional_to_max_bed_size(config, 0.1));
            if (mesh.empty()) {
                message = "Unknown part shape " + part.shape;
                return nullptr;
            }
            volume = loaded->add_volume(std::move(mesh), volume_type_of(part.type));
            volume->name = part.name.empty() ? part.shape : part.name;
        }
        if (part.matrix.size() == 16) {
            Slic3r::Transform3d transformation = Slic3r::Transform3d::Identity();
            std::copy(part.matrix.begin(), part.matrix.end(), transformation.data());
            volume->set_transformation(Slic3r::Geometry::Transformation(transformation));
        }
        volume->config.assign_config(detail::model_config(part.settings));
        volume->source.is_converted_from_inches = part.from_inches;
        volume->source.is_converted_from_meters = part.from_meters;
        volume->source.input_file = part.input_file;
        apply_origin(*volume, part.origin);
        volume->cut_info = detail::cut_info_of(part.cut_info);
        apply_repaired_errors(*volume, part.mesh_errors);
        if (!apply_painted_facets(*volume, part.painted)) {
            message = "The painted facets of a part could not be read";
            return nullptr;
        }
        if (!detail::read_emboss(part.emboss, *volume)) {
            message = "The embossed text or SVG of a part could not be read";
            return nullptr;
        }
    }
    loaded->invalidate_bounding_box();
    // The height ranges of the object (ModelObject::layer_config_ranges),
    // which PrintObject::slice() prints with their own layer height.
    for (const LayerRange& range : object.layer_ranges) {
        loaded->layer_config_ranges[{range.bottom, range.top}].assign_config(detail::model_config(range.settings));
    }
    // The variable layer height of the object (GLCanvas3D::LayersEditing).
    if (!object.layer_height_profile.empty()) {
        loaded->layer_height_profile.set(object.layer_height_profile);
    }
    // The brim ears of the object (GLGizmoBrimEars).
    for (std::size_t index = 0; index + 3 < object.brim_points.size(); index += 4) {
        loaded->brim_points.emplace_back(float(object.brim_points[index]), float(object.brim_points[index + 1]), float(object.brim_points[index + 2]),
            float(object.brim_points[index + 3]));
    }
    drop_instances(*loaded);
    return loaded;
}

// Loads the objects of the plate into model, in the plate's order.
bool load_plate(const std::vector<PlateObject>& plate, const Slic3r::DynamicPrintConfig& config, Slic3r::Model& model, std::string& message)
{
    for (const PlateObject& object : plate) {
        if (load_object(object, config, model, message) == nullptr) {
            return false;
        }
    }
    // The plate is loaded anew for every request, so its copies go by their
    // place among the plate's copies, which stays from the slice to the 3MF
    // file of the sliced plate and its pick picture, as OrcaSlicer's command
    // line labels the copies of a 3MF file by their loaded_id; the desktop
    // app's copies go by ids that live as long as the app.
    std::size_t label = 0;
    for (Slic3r::ModelObject* object : model.objects) {
        for (Slic3r::ModelInstance* instance : object->instances) {
            instance->loaded_id = ++label;
            instance->use_loaded_id_for_label = true;
        }
    }
    return true;
}

// Plater::priv::update_print_volume_state() and the slice button of
// GLCanvas3D::reload_scene(): an object over the plate boundary or above the
// build height blocks slicing, and an object entirely off the plate is not
// printed, so a plate with no object on it has nothing to slice.
bool check_print_volume(Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config, std::string& message)
{
    model.update_print_volume_state(build_volume_of(config));
    // ModelInstance::is_printable(): inside the build volume and printed.
    unsigned int printable = 0;
    for (const Slic3r::ModelObject* object : model.objects) {
        for (const Slic3r::ModelInstance* instance : object->instances) {
            if (instance->is_printable()) {
                ++printable;
            }
        }
    }
    for (const Slic3r::ModelObject* object : model.objects) {
        for (const Slic3r::ModelInstance* instance : object->instances) {
            if (instance->print_volume_state == Slic3r::ModelInstancePVS_Partly_Outside) {
                message = "An object is laid over the boundary of plate or exceeds the height limit.\n"
                          "Please solve the problem by moving it totally on or off the plate, and confirming that the height is within the build volume.";
                return false;
            }
        }
    }
    if (printable == 0) {
        message = "No object is on the plate.";
        return false;
    }
    return true;
}

std::int64_t printed_layer_count(const Slic3r::Print& print)
{
    std::set<coordf_t> heights;
    for (const Slic3r::PrintObject* object : print.objects()) {
        for (const Slic3r::Layer* layer : object->layers()) {
            heights.insert(layer->print_z);
        }
        for (const Slic3r::SupportLayer* layer : object->support_layers()) {
            heights.insert(layer->print_z);
        }
    }
    return static_cast<std::int64_t>(heights.size());
}

void remove_file(const std::string& path)
{
    boost::system::error_code ignored;
    fs::remove(path, ignored);
}

// Scene files are written next to their final path and renamed when complete,
// so the app never reads a partial file.
bool commit_file(const std::string& temporary_path, const std::string& path)
{
    boost::system::error_code error;
    fs::rename(temporary_path, path, error);
    if (error) {
        remove_file(temporary_path);
        return false;
    }
    return true;
}

// PartPlate::get_extruders(true) of the current plate: the filaments, from 1,
// of the objects whose first copy stands on the plate whole
// (contain_instance_totally) — their parts and height ranges, their support
// and its interface when they have support or a raft, and their walls,
// sparse and internal solid infill and top and bottom surfaces, each the
// object's own or the process preset's — and of the tool changes on the
// plate's layers.
std::vector<int> plate_filaments(const Slic3r::Model& model, const Slic3r::DynamicPrintConfig& glb_config, const std::vector<LayerGcode>& layer_gcodes)
{
    std::vector<int> plate_extruders;
    int glb_support_intf_extr = glb_config.opt_int("support_interface_filament");
    int glb_support_extr = glb_config.opt_int("support_filament");
    int glb_outer_wall_extr = glb_config.opt_int("outer_wall_filament_id");
    int glb_inner_wall_extr = glb_config.opt_int("inner_wall_filament_id");
    if (glb_outer_wall_extr == 0) glb_outer_wall_extr = glb_inner_wall_extr;
    if (glb_inner_wall_extr == 0) glb_inner_wall_extr = glb_outer_wall_extr;
    int glb_sparse_infill_extr = glb_config.opt_int("sparse_infill_filament_id");
    int glb_internal_solid_extr = glb_config.opt_int("internal_solid_filament_id");
    int glb_top_surface_extr = glb_config.opt_int("top_surface_filament_id");
    int glb_bottom_surface_extr = glb_config.opt_int("bottom_surface_filament_id");
    if (glb_top_surface_extr == 0) glb_top_surface_extr = glb_internal_solid_extr;
    if (glb_bottom_surface_extr == 0) glb_bottom_surface_extr = glb_internal_solid_extr;
    bool glb_support = glb_config.opt_bool("enable_support");
    glb_support |= glb_config.opt_int("raft_layers") > 0;

    // The option of the object, or 0 for none.
    const auto object_int = [](const Slic3r::ModelObject& mo, const char* key) {
        const Slic3r::ConfigOption* option = mo.config.option(key);
        return option != nullptr ? option->getInt() : 0;
    };
    for (const Slic3r::ModelObject* mo : model.objects) {
        if (mo->instances.empty() || mo->instances.front()->print_volume_state != Slic3r::ModelInstancePVS_Inside) {
            continue;
        }
        for (const Slic3r::ModelVolume* mv : mo->volumes) {
            std::vector<int> volume_extruders = mv->get_extruders();
            plate_extruders.insert(plate_extruders.end(), volume_extruders.begin(), volume_extruders.end());
        }

        // layer range
        for (const auto& layer_range : mo->layer_config_ranges) {
            if (layer_range.second.has("extruder")) {
                if (auto id = layer_range.second.option("extruder")->getInt(); id > 0)
                    plate_extruders.push_back(id);
            }
        }

        bool obj_support = false;
        const Slic3r::ConfigOption* obj_support_opt = mo->config.option("enable_support");
        const Slic3r::ConfigOption* obj_raft_opt = mo->config.option("raft_layers");
        if (obj_support_opt != nullptr || obj_raft_opt != nullptr) {
            if (obj_support_opt != nullptr)
                obj_support = obj_support_opt->getBool();
            if (obj_raft_opt != nullptr)
                obj_support |= obj_raft_opt->getInt() > 0;
        } else
            obj_support = glb_support;

        if (obj_support) {
            const int obj_support_intf_extr = object_int(*mo, "support_interface_filament");
            if (obj_support_intf_extr != 0)
                plate_extruders.push_back(obj_support_intf_extr);
            else if (glb_support_intf_extr != 0)
                plate_extruders.push_back(glb_support_intf_extr);

            const int obj_support_extr = object_int(*mo, "support_filament");
            if (obj_support_extr != 0)
                plate_extruders.push_back(obj_support_extr);
            else if (glb_support_extr != 0)
                plate_extruders.push_back(glb_support_extr);
        }

        int obj_outer_wall_extr = object_int(*mo, "outer_wall_filament_id");
        if (obj_outer_wall_extr == 0)
            obj_outer_wall_extr = object_int(*mo, "inner_wall_filament_id");
        if (obj_outer_wall_extr != 0)
            plate_extruders.push_back(obj_outer_wall_extr);
        else if (glb_outer_wall_extr != 0)
            plate_extruders.push_back(glb_outer_wall_extr);

        int obj_inner_wall_extr = object_int(*mo, "inner_wall_filament_id");
        if (obj_inner_wall_extr == 0)
            obj_inner_wall_extr = object_int(*mo, "outer_wall_filament_id");
        if (obj_inner_wall_extr != 0)
            plate_extruders.push_back(obj_inner_wall_extr);
        else if (glb_inner_wall_extr != 0)
            plate_extruders.push_back(glb_inner_wall_extr);

        const int obj_sparse_infill_extr = object_int(*mo, "sparse_infill_filament_id");
        if (obj_sparse_infill_extr != 0)
            plate_extruders.push_back(obj_sparse_infill_extr);
        else if (glb_sparse_infill_extr != 0)
            plate_extruders.push_back(glb_sparse_infill_extr);

        const int obj_internal_solid_extr = object_int(*mo, "internal_solid_filament_id");
        if (obj_internal_solid_extr != 0)
            plate_extruders.push_back(obj_internal_solid_extr);
        else if (glb_internal_solid_extr != 0)
            plate_extruders.push_back(glb_internal_solid_extr);

        int obj_top_surface_extr = object_int(*mo, "top_surface_filament_id");
        if (obj_top_surface_extr == 0)
            obj_top_surface_extr = obj_internal_solid_extr;
        if (obj_top_surface_extr != 0)
            plate_extruders.push_back(obj_top_surface_extr);
        else if (glb_top_surface_extr != 0)
            plate_extruders.push_back(glb_top_surface_extr);

        int obj_bottom_surface_extr = object_int(*mo, "bottom_surface_filament_id");
        if (obj_bottom_surface_extr == 0)
            obj_bottom_surface_extr = obj_internal_solid_extr;
        if (obj_bottom_surface_extr != 0)
            plate_extruders.push_back(obj_bottom_surface_extr);
        else if (glb_bottom_surface_extr != 0)
            plate_extruders.push_back(glb_bottom_surface_extr);
    }

    // conside_custom_gcode
    const int nums_extruders = int(glb_config.option<Slic3r::ConfigOptionStrings>("filament_colour")->values.size());
    for (const LayerGcode& item : layer_gcodes) {
        if (item.type == LayerGcodeType::tool_change && item.extruder <= nums_extruders)
            plate_extruders.push_back(item.extruder);
    }

    std::sort(plate_extruders.begin(), plate_extruders.end());
    plate_extruders.erase(std::unique(plate_extruders.begin(), plate_extruders.end()), plate_extruders.end());
    return plate_extruders;
}

// render_all_plates_stats(): what the print used of each of the plate's
// filaments, get_used_filament_from_volume() of its volumes per extruder, and
// nothing of a filament the plate has no colour for.
std::vector<FilamentUsage> filament_usage(const Slic3r::GCodeProcessorResult& gcode_result, const std::vector<int>& filaments, const std::size_t colors)
{
    const Slic3r::PrintEstimatedStatistics& estimated = gcode_result.print_statistics;
    std::vector<FilamentUsage> usage;
    for (const int filament : filaments) {
        const std::size_t extruder_id = std::size_t(filament - 1);
        if (filament < 1 || extruder_id >= colors) {
            continue;
        }
        const auto used = [&gcode_result, extruder_id](const std::map<std::size_t, double>& volumes) -> std::array<double, 2> {
            const auto found = volumes.find(extruder_id);
            if (found == volumes.end() || extruder_id >= gcode_result.filament_diameters.size() ||
                extruder_id >= gcode_result.filament_densities.size()) {
                return {0.0, 0.0};
            }
            const double volume = found->second;
            const double radius = 0.5 * gcode_result.filament_diameters[extruder_id];
            return {0.001 * volume / (PI * radius * radius), volume * gcode_result.filament_densities[extruder_id] * 0.001};
        };
        FilamentUsage& item = usage.emplace_back();
        item.filament = filament;
        item.model = used(estimated.model_volumes_per_extruder);
        item.support = used(estimated.support_volumes_per_extruder);
        item.flushed = used(estimated.flush_per_filament);
        item.wipe_tower = used(estimated.wipe_tower_volumes_per_extruder);
    }
    return usage;
}

// Plater::_calib_pa_pattern_gen_gcode() for the current plate: the test
// pattern of every handle on it (PartPlate::get_objects_on_this_plate),
// generated by CalibPressureAdvancePattern from the presets (the plate's
// settings aside, as PresetBundle::full_config()), the first test's codes
// taking the plate's and the others' G-code joining them layer by layer.
void pa_pattern_gcodes(Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config, const CalibrationParams& calibration)
{
    const Slic3r::BoundingBoxf3 box = plate_box_of(config);
    std::vector<const Slic3r::ModelObject*> handles;
    for (const Slic3r::ModelObject* object : model.objects) {
        for (std::size_t instance = 0; instance < object->instances.size(); ++instance) {
            if (box.intersects(object->instance_convex_hull_bounding_box(instance))) {
                handles.push_back(object);
                break;
            }
        }
    }
    if (handles.empty()) {
        return;
    }
    const bool is_bbl_machine = detail::engine().bundle->is_bbl_vendor();
    const Slic3r::Vec3d origin = Slic3r::to_3d(plate_origin_of(config), 0.);
    // The pattern keeps a reference to its figures (CalibPressureAdvancePattern::m_params).
    const Slic3r::Calib_Params params = detail::calib_params(calibration);
    Slic3r::CalibPressureAdvancePattern pattern(params, config, is_bbl_machine, *handles.front(), origin);
    std::vector<Slic3r::CustomGCode::Info> mgc;
    for (const Slic3r::ModelObject* handle : handles) {
        mgc.emplace_back(pattern.generate_custom_gcodes(config, is_bbl_machine, *handle, origin));
    }

    // move first item into model custom gcode
    model.curr_plate_index = detail::engine().plate_index;
    auto& pcgc = model.plates_custom_gcodes[model.curr_plate_index];
    pcgc = std::move(mgc[0]);
    mgc.erase(mgc.begin());

    // concat layer gcodes for each test
    for (std::size_t i = 0; i < pcgc.gcodes.size(); i++) {
        for (auto& gc : mgc) {
            pcgc.gcodes[i].extra += gc.gcodes[i].extra;
        }
    }
}

// The legend's figures besides the moves: GCodeViewer::load_as_gcode() and
// render_legend() read them from the processor's result and the print.
orcinus::toolpaths::Statistics toolpaths_statistics(const Slic3r::GCodeProcessorResult& gcode_result, const Slic3r::PrintStatistics& print_statistics)
{
    using TimeMode = Slic3r::PrintEstimatedStatistics::ETimeMode;
    const Slic3r::PrintEstimatedStatistics& estimated = gcode_result.print_statistics;
    orcinus::toolpaths::Statistics statistics;
    for (const TimeMode mode : {TimeMode::Normal, TimeMode::Stealth}) {
        const std::size_t index = static_cast<std::size_t>(mode);
        statistics.time[index] = estimated.modes[index].time;
        statistics.prepare_time[index] = estimated.modes[index].prepare_time;
    }
    for (const auto& [role, used] : estimated.used_filaments_per_role) {
        statistics.used_filament_per_role.push_back({static_cast<std::uint8_t>(libvgcode::convert(role)), {used.first, used.second}});
    }
    // render_legend()'s get_used_filament_from_volume(): metres and grams of a volume of an extruder's filament.
    const auto used = [&gcode_result](const std::size_t extruder, const double volume) {
        if (extruder >= gcode_result.filament_diameters.size() || extruder >= gcode_result.filament_densities.size()) {
            return std::array<double, 2>{0.0, 0.0};
        }
        const double radius = 0.5 * gcode_result.filament_diameters[extruder];
        return std::array<double, 2>{0.001 * volume / (PI * radius * radius), volume * gcode_result.filament_densities[extruder] * 0.001};
    };
    const auto filament = [&used](const std::map<std::size_t, double>& volumes) {
        std::array<double, 2> sum{0.0, 0.0};
        for (const auto& [extruder, volume] : volumes) {
            const std::array<double, 2> usage = used(extruder, volume);
            sum[0] += usage[0];
            sum[1] += usage[1];
        }
        return sum;
    };
    statistics.model_filament = filament(estimated.model_volumes_per_extruder);
    statistics.support_filament = filament(estimated.support_volumes_per_extruder);
    statistics.flushed_filament = filament(estimated.flush_per_filament);
    statistics.wipe_tower_filament = filament(estimated.wipe_tower_volumes_per_extruder);
    // The ColorPrint legend's columns, per extruder and listed or not.
    std::map<std::size_t, std::pair<std::array<double, 8>, std::uint8_t>> per_extruder;
    const std::array<const std::map<std::size_t, double>*, 4> maps{
        &estimated.model_volumes_per_extruder, &estimated.support_volumes_per_extruder, &estimated.flush_per_filament,
        &estimated.wipe_tower_volumes_per_extruder,
    };
    for (std::size_t column = 0; column < maps.size(); ++column) {
        for (const auto& [extruder, volume] : *maps[column]) {
            auto& [values, listed] = per_extruder[extruder];
            const std::array<double, 2> usage = used(extruder, volume);
            values[column * 2] = usage[0];
            values[column * 2 + 1] = usage[1];
            listed = static_cast<std::uint8_t>(listed | (1u << column));
        }
    }
    for (const auto& [extruder, entry] : per_extruder) {
        statistics.filament_per_extruder.push_back({static_cast<std::uint8_t>(extruder), entry.first});
        statistics.filament_listed.push_back(entry.second);
    }
    statistics.total_used_filament = print_statistics.total_used_filament;
    statistics.total_weight = print_statistics.total_weight;
    statistics.total_cost = print_statistics.total_cost;
    statistics.total_travel_distance = estimated.total_travel_distance;
    statistics.total_travel_moves = estimated.total_travel_moves;
    statistics.total_seam_distance = estimated.total_seam_gap_distance + estimated.total_seam_scarf_distance;
    statistics.total_filament_changes = estimated.total_filament_changes;
    statistics.total_extruder_changes = estimated.total_extruder_changes;
    statistics.total_tool_change_time =
        estimated.total_filament_load_time + estimated.total_filament_unload_time + estimated.total_tool_change_time;
    for (const Slic3r::GCodeProcessorResult::MoveVertex& move : gcode_result.moves) {
        if (move.internal_only) {
            continue;
        }
        const std::size_t type = static_cast<std::size_t>(move.type);
        if (type >= orcinus::toolpaths::MOVE_TYPES_COUNT) {
            continue;
        }
        ++statistics.move_counts[type];
        for (std::size_t mode = 0; mode < move.time.size(); ++mode) {
            statistics.move_times[type][mode] += move.time[mode];
        }
        if (move.type == Slic3r::EMoveType::Retract || move.type == Slic3r::EMoveType::Unretract) {
            statistics.move_distances[type] += std::fabs(move.delta_extruder);
        } else {
            statistics.move_distances[type] += move.travel_dist;
        }
    }
    return statistics;
}

// GLCanvas3D::load_gcode_preview() for the plate: libvgcode's input converted
// from the G-code processor's moves, with the filament colours as the tool
// colours and as the colour print colours, which Plater::get_colors_for_color_print()
// extends only with colour changes the app does not have, and the legend's figures.
bool write_toolpaths(
    const Slic3r::GCodeProcessorResult& gcode_result,
    const Slic3r::Print& print,
    const Slic3r::DynamicPrintConfig& config,
    const std::string& path
)
{
    std::vector<std::string> tool_colors;
    if (const auto* colours = config.option<Slic3r::ConfigOptionStrings>("filament_colour")) {
        tool_colors = colours->values;
    }
    // convert() takes the viewer it converts for, but reads nothing from it.
    const libvgcode::Viewer viewer;
    const libvgcode::GCodeInputData data = libvgcode::convert(gcode_result, tool_colors, tool_colors, viewer);
    const std::string temporary_path = path + ".part";
    if (!orcinus::toolpaths::write_file(temporary_path, data, toolpaths_statistics(gcode_result, print.print_statistics()))) {
        remove_file(temporary_path);
        return false;
    }
    return commit_file(temporary_path, path);
}

bool write_mesh(const indexed_triangle_set& its, const std::string& path)
{
    const std::string temporary_path = path + ".part";
    {
        std::ofstream out(temporary_path, std::ios::binary | std::ios::trunc);
        const auto write_u32 = [&out](const std::uint32_t value) {
            out.write(reinterpret_cast<const char*>(&value), sizeof(value));
        };
        out.write(mesh_file_magic, sizeof(mesh_file_magic));
        write_u32(mesh_file_version);
        write_u32(static_cast<std::uint32_t>(its.vertices.size()));
        write_u32(static_cast<std::uint32_t>(its.indices.size()));
        for (const Slic3r::Vec3f& vertex : its.vertices) {
            out.write(reinterpret_cast<const char*>(vertex.data()), 3 * sizeof(float));
        }
        for (const Slic3r::Vec3i32& triangle : its.indices) {
            for (int corner = 0; corner < 3; ++corner) {
                write_u32(static_cast<std::uint32_t>(triangle(corner)));
            }
        }
        if (!out) {
            out.close();
            remove_file(temporary_path);
            return false;
        }
    }
    return commit_file(temporary_path, path);
}

bool write_rgba_png(const std::string& path, const int width, const int height, const std::vector<unsigned char>& rgba)
{
    const std::string temporary_path = path + ".part";
    std::FILE* file = std::fopen(temporary_path.c_str(), "wb");
    if (file == nullptr) {
        return false;
    }
    png_structp png = png_create_write_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    png_infop info = png == nullptr ? nullptr : png_create_info_struct(png);
    if (info == nullptr || setjmp(png_jmpbuf(png))) {
        png_destroy_write_struct(&png, &info);
        std::fclose(file);
        remove_file(temporary_path);
        return false;
    }
    png_init_io(png, file);
    png_set_IHDR(png, info, width, height, 8, PNG_COLOR_TYPE_RGBA, PNG_INTERLACE_NONE, PNG_COMPRESSION_TYPE_DEFAULT, PNG_FILTER_TYPE_DEFAULT);
    png_write_info(png, info);
    for (int row = 0; row < height; ++row) {
        png_write_row(png, rgba.data() + static_cast<std::size_t>(row) * static_cast<std::size_t>(width) * 4);
    }
    png_write_end(png, nullptr);
    png_destroy_write_struct(&png, &info);
    if (std::fclose(file) != 0) {
        remove_file(temporary_path);
        return false;
    }
    return commit_file(temporary_path, path);
}

// GLTexture::load_from_svg(): the SVG is parsed at 96 dpi and rasterized with
// its longest side at max_size_px on a transparent background. Orca draws bed
// textures at up to 2048 px (PartPlate::render_logo).
bool rasterize_svg(const std::string& svg_path, const std::string& png_path)
{
    constexpr int max_size_px = 2048;
    NSVGimage* image = nsvgParseFromFile(svg_path.c_str(), "px", 96.0f);
    if (image == nullptr) {
        return false;
    }
    const float scale = static_cast<float>(max_size_px) / std::max(image->width, image->height);
    const int width = static_cast<int>(scale * image->width);
    const int height = static_cast<int>(scale * image->height);
    if (width <= 0 || height <= 0) {
        nsvgDelete(image);
        return false;
    }
    std::vector<unsigned char> rgba(static_cast<std::size_t>(width) * static_cast<std::size_t>(height) * 4, 0);
    NSVGrasterizer* rasterizer = nsvgCreateRasterizer();
    if (rasterizer == nullptr) {
        nsvgDelete(image);
        return false;
    }
    nsvgRasterizeXY(rasterizer, image, 0, 0, static_cast<float>(width) / image->width, static_cast<float>(height) / image->height,
        rgba.data(), width, height, width * 4);
    nsvgDeleteRasterizer(rasterizer);
    nsvgDelete(image);
    return write_rgba_png(png_path, width, height, rgba);
}

// Bed_2D::calculate_grid_step(): wider beds get a wider grid, based on a
// 500 x 500 mm bed.
int calculate_grid_step(const Slic3r::BoundingBox& box, const double scale)
{
    const int min_edge = static_cast<int>(std::min(box.size().x() / scale, box.size().y() / scale));
    return min_edge >= 6000 ? 100 : min_edge >= 1200 ? 50 : min_edge >= 600 ? 20 : 10;
}

// Bed_2D::generate_grid(): lines every step from the plate origin, every fifth
// one bold, clipped to the slightly grown plate polygon.
std::pair<Slic3r::Polylines, Slic3r::Polylines> generate_grid(
    const Slic3r::ExPolygon& polygon,
    const Slic3r::BoundingBox& box,
    const Slic3r::Vec2d& origin,
    const double step,
    const double scale
)
{
    Slic3r::Polylines thin;
    Slic3r::Polylines bold;
    int count = 0;
    for (coord_t x = origin.x(); x >= box.min(0); x -= step) {
        (count % 5 ? thin : bold).push_back(Slic3r::Polyline(Slic3r::Point(x, box.min(1)), Slic3r::Point(x, box.max(1))));
        ++count;
    }
    count = 0;
    for (coord_t x = origin.x(); x <= box.max(0); x += step) {
        (count % 5 ? thin : bold).push_back(Slic3r::Polyline(Slic3r::Point(x, box.min(1)), Slic3r::Point(x, box.max(1))));
        ++count;
    }
    count = 0;
    for (coord_t y = origin.y(); y >= box.min(1); y -= step) {
        (count % 5 ? thin : bold).push_back(Slic3r::Polyline(Slic3r::Point(box.min(0), y), Slic3r::Point(box.max(0), y)));
        ++count;
    }
    count = 0;
    for (coord_t y = origin.y(); y <= box.max(1); y += step) {
        (count % 5 ? thin : bold).push_back(Slic3r::Polyline(Slic3r::Point(box.min(0), y), Slic3r::Point(box.max(0), y)));
        ++count;
    }
    const Slic3r::Polygons grown = Slic3r::offset(polygon, static_cast<float>(scale));
    return {Slic3r::intersection_pl(thin, grown), Slic3r::intersection_pl(bold, grown)};
}

// PartPlate::generate_exclude_polygon(): a four-point area gets corners rounded
// with a 1 mm radius, any other shape is used as is.
Slic3r::ExPolygon exclude_polygon(const Slic3r::Pointfs& area)
{
    Slic3r::ExPolygon polygon;
    const auto append_arc = [&polygon](const Slic3r::Vec2d& center, const double radius, const double start_angle, const double stop_angle, const int count) {
        const double angle_step = (stop_angle - start_angle) / (count - 1);
        for (int index = 0; index < count; ++index) {
            const double angle = start_angle + index * angle_step;
            polygon.contour.append({scale_(center(0) + std::cos(angle) * radius), scale_(center(1) + std::sin(angle) * radius)});
        }
    };
    if (area.size() == 4) {
        constexpr int points_count = 8;
        constexpr double radius = 1.0;
        append_arc({area[0](0) + radius, area[0](1) + radius}, radius, 1.0 * PI, 1.5 * PI, points_count);
        append_arc({area[1](0) - radius, area[1](1) + radius}, radius, 1.5 * PI, 2.0 * PI, points_count);
        append_arc({area[2](0) - radius, area[2](1) - radius}, radius, 0.0 * PI, 0.5 * PI, points_count);
        append_arc({area[3](0) + radius, area[3](1) - radius}, radius, 0.5 * PI, 1.0 * PI, points_count);
    } else {
        for (const Slic3r::Vec2d& point : area) {
            polygon.contour.append({scale_(point(0)), scale_(point(1))});
        }
    }
    polygon.contour.make_counter_clockwise();
    return polygon;
}

// init_model_from_poly() in 3DBed.cpp: the triangles Orca draws for a plate polygon.
std::vector<float> triangulate(const Slic3r::ExPolygon& polygon)
{
    std::vector<float> xy;
    if (polygon.contour.size() < 3) {
        return xy;
    }
    for (const Slic3r::Vec2f& vertex : Slic3r::triangulate_expolygon_2f(polygon, Slic3r::NORMALS_UP)) {
        xy.push_back(vertex.x());
        xy.push_back(vertex.y());
    }
    return xy;
}

std::vector<float> line_coordinates(const Slic3r::Lines& lines)
{
    std::vector<float> xy;
    xy.reserve(lines.size() * 4);
    for (const Slic3r::Line& line : lines) {
        xy.push_back(Slic3r::unscale<float>(line.a.x()));
        xy.push_back(Slic3r::unscale<float>(line.a.y()));
        xy.push_back(Slic3r::unscale<float>(line.b.x()));
        xy.push_back(Slic3r::unscale<float>(line.b.y()));
    }
    return xy;
}

// Bed3D::detect_type(): the bed model of the selected printer, or of its
// closest ancestor with the same printable area.
std::string system_bed_model(Slic3r::PresetBundle& bundle, const Slic3r::Pointfs& shape)
{
    const Slic3r::Preset* current = &bundle.printers.get_selected_preset();
    while (current != nullptr) {
        if (current->config.has("printable_area")
            && shape == Slic3r::make_counter_clockwise(current->config.option<Slic3r::ConfigOptionPoints>("printable_area")->values)) {
            std::string model;
            if (current->is_system) {
                model = Slic3r::PresetUtils::system_printer_bed_model(*current);
            } else if (const auto* printer_model = current->config.opt<Slic3r::ConfigOptionString>("printer_model");
                       printer_model != nullptr && !printer_model->value.empty()) {
                model = bundle.get_stl_model_for_printer_model(printer_model->value);
            }
            if (!model.empty()) {
                return model;
            }
        }
        current = bundle.printers.get_preset_parent(*current);
    }
    return {};
}

// Plater::set_bed_shape(): the texture of the selected printer model.
std::string system_bed_texture(Slic3r::PresetBundle& bundle)
{
    const Slic3r::Preset& printer = bundle.printers.get_selected_preset();
    if (printer.is_system) {
        return Slic3r::PresetUtils::system_printer_bed_texture(printer);
    }
    const auto* printer_model = printer.config.opt<Slic3r::ConfigOptionString>("printer_model");
    return printer_model == nullptr || printer_model->value.empty() ? std::string() : bundle.get_texture_for_printer_model(printer_model->value);
}

// GCodeViewer::init(): the tool marker is the hotend model of the selected
// printer's model, or OrcaSlicer's own hotend.
std::string hotend_model(Slic3r::PresetBundle& bundle)
{
    const Slic3r::Preset& printer = bundle.printers.get_selected_preset();
    if (printer.is_system) {
        return Slic3r::PresetUtils::system_printer_hotend_model(printer);
    }
    std::string filename;
    const auto* printer_model = printer.config.opt<Slic3r::ConfigOptionString>("printer_model");
    if (printer_model != nullptr && !printer_model->value.empty()) {
        filename = bundle.get_hotend_model_for_printer_model(printer_model->value);
    }
    if (filename.empty()) {
        filename = bundle.get_hotend_model_for_printer_model(Slic3r::PresetBundle::ORCA_DEFAULT_PRINTER_MODEL);
    }
    return filename;
}

bool file_exists(const std::string& path)
{
    boost::system::error_code error;
    return !path.empty() && fs::exists(path, error);
}

SceneStatus scene_status(const SliceStatus status)
{
    return status == SliceStatus::profile_not_found ? SceneStatus::profile_not_found : SceneStatus::engine_not_ready;
}

// Whether the bundle installed in the data directory is the one the resources
// hold (PresetUpdater::priv::check_installed_vendor_profiles()'s version rule).
bool bundle_is_current(const fs::path& path_in_resources, const fs::path& path_in_vendor)
{
    if (!fs::exists(path_in_vendor)) {
        return false;
    }
    const Slic3r::Semver resource_version = Slic3r::get_version_from_json(path_in_resources.string());
    const Slic3r::Semver vendor_version = Slic3r::get_version_from_json(path_in_vendor.string());
    const bool version_match = resource_version.maj() == vendor_version.maj() && resource_version.min() == vendor_version.min();
    return version_match && !(vendor_version < resource_version);
}

// PresetUpdater::priv::check_installed_vendor_profiles() with profile updates
// enabled: the filament library and the default bundle are always installed,
// the bundle of an enabled vendor when it is missing or older than the one in
// the resources, and the bundle of a vendor no longer enabled is removed.
// Returns the number of printer vendor bundles in the resources.
//
// The desktop app installs the filament library on every start, from a
// background thread of its updater. Here the app waits for this before it can
// show anything, and the library is 482 files, so it is installed by the same
// rule as a vendor bundle: when it is missing or older than the resources. A
// new app version brings a new library version with it.
std::size_t install_vendor_bundles(const Slic3r::AppConfig& config)
{
    std::size_t vendor_bundles = 0;
    const fs::path rsrc_path = fs::path(Slic3r::resources_dir()) / "profiles";
    const fs::path vendor_path = fs::path(Slic3r::data_dir()) / PRESET_SYSTEM_DIR;
    const Slic3r::AppConfig::VendorMap& enabled_vendors = config.vendors();

    const std::string library = Slic3r::PresetBundle::ORCA_FILAMENT_LIBRARY;
    std::set<std::string> bundles;
    if (!bundle_is_current(rsrc_path / (library + ".json"), vendor_path / (library + ".json"))) {
        bundles.insert(library);
    }
    for (const fs::directory_entry& entry : fs::directory_iterator(rsrc_path)) {
        const fs::path& path = entry.path();
        if (!Slic3r::is_json_file(path.string())) {
            continue;
        }
        const fs::path path_in_vendor = vendor_path / path.filename();
        std::string vendor_name = path.filename().string();
        vendor_name.erase(vendor_name.size() - 5);
        if (vendor_name == library) {
            continue;
        }
        ++vendor_bundles;
        const bool is_vendor_enabled = vendor_name == Slic3r::PresetBundle::ORCA_DEFAULT_BUNDLE || enabled_vendors.count(vendor_name) > 0;
        if (fs::exists(path_in_vendor)) {
            if (is_vendor_enabled) {
                if (!bundle_is_current(path, path_in_vendor)) {
                    bundles.insert(vendor_name);
                }
            } else {
                fs::remove(path_in_vendor);
                if (fs::exists(vendor_path / vendor_name)) {
                    fs::remove_all(vendor_path / vendor_name);
                }
            }
        } else if (is_vendor_enabled) {
            bundles.insert(vendor_name);
        }
    }
    if (!bundles.empty()) {
        Slic3r::install_vendor_bundles_from_resources(std::vector<std::string>(bundles.begin(), bundles.end()));
    }
    return vendor_bundles;
}

}  // namespace

std::string engine_version()
{
    return "orca-upstream/" SoftFever_VERSION " bridge/1.0";
}

EngineInitialization initialize(const EngineDirectories& directories)
{
    const std::lock_guard<std::mutex> lock(engine().mutex);
    if (initialization != nullptr) {
        return *initialization;
    }

    install_android_log_sink();
    Slic3r::set_logging_level(orca_log_level);
    Slic3r::set_resources_dir(directories.resources_dir);
    Slic3r::set_data_dir(directories.data_dir);
    Slic3r::set_temporary_dir(directories.temporary_dir);

    auto result = std::make_unique<EngineInitialization>();
    try {
        fs::create_directories(fs::path(directories.data_dir) / PRESET_USER_DIR);
        fs::create_directories(directories.temporary_dir);

        // GUI_App::init_app_config(): the configuration of the data directory, when there is one.
        auto config = std::make_unique<Slic3r::AppConfig>();
        const bool config_existed = config->exists();
        if (config_existed) {
            if (const std::string error = config->load(); !error.empty()) {
                BOOST_LOG_TRIVIAL(error) << "Unable to load the app configuration: " << error;
            }
        }
        // GUI_App::init_app_config(): the log level of the Preferences.
        Slic3r::set_logging_level(Slic3r::level_string_to_boost(config->get("log_severity_level")));
        const std::size_t vendor_bundles = install_vendor_bundles(*config);

        // GUI_App::load_language(): a modified preset's label starts with the
        // translation of "*" and a space, which is "*" in every catalogue.
        Slic3r::Preset::update_suffix_modified("* ");
        auto bundle = std::make_unique<Slic3r::PresetBundle>();
        // Same substitution rule as the desktop app's start-up.
        bundle->load_presets(*config, Slic3r::ForwardCompatibilitySubstitutionRule::EnableSystemSilent);
        // The printers the user can send G-code to. This build of OrcaSlicer
        // never loads them (its own printers are found over the network), so
        // the app reads them itself from the data directory, where
        // PhysicalPrinterCollection::save_printer() writes them.
        Slic3r::PresetsConfigSubstitutions printer_substitutions;
        bundle->physical_printers.load_printers(
            Slic3r::data_dir(),
            "physical_printer",
            printer_substitutions,
            Slic3r::ForwardCompatibilitySubstitutionRule::EnableSystemSilent
        );
        if (vendor_bundles == 0) {
            // The Setup Wizard would offer no printer.
            result->message = "No vendor profiles in " + (fs::path(directories.resources_dir) / "profiles").string();
        } else {
            engine().bundle = std::move(bundle);
            engine().config = std::move(config);
            engine().config_existed = config_existed;
            engine().bundle_follows_config = true;
            result->ready = true;
        }
    } catch (const std::exception& error) {
        result->message = error.what();
    }

    initialization = std::move(result);
    return *initialization;
}


namespace {

// The index of the plate's object, and of its copy, an object or copy of the
// print's model stands for (the print keeps the ids of the model it was applied with).
std::pair<std::int32_t, std::int32_t> plate_copy_of(const Slic3r::Model& model, const Slic3r::ObjectID object, const Slic3r::ObjectID instance)
{
    for (std::size_t index = 0; index < model.objects.size(); ++index) {
        const Slic3r::ModelObject& candidate = *model.objects[index];
        if (candidate.id() != object) {
            continue;
        }
        for (std::size_t copy = 0; copy < candidate.instances.size(); ++copy) {
            if (candidate.instances[copy]->id() == instance) {
                return {std::int32_t(index), std::int32_t(copy)};
            }
        }
        return {std::int32_t(index), -1};
    }
    return {-1, -1};
}

// Plater::priv::on_slicing_update(): the current warnings of a step, which
// the notification center shows with a "Jump to" the object; the empty
// layers' are serious (SlicingReplaceInitEmptyLayers, SlicingEmptyGcodeLayers).
void add_step_warnings(const Slic3r::PrintStateBase::StateWithWarnings& state, const std::int32_t object_index, std::vector<SliceNotice>& notices)
{
    for (const Slic3r::PrintStateBase::Warning& warning : state.warnings) {
        if (!warning.current) {
            continue;
        }
        SliceNotice& notice = notices.emplace_back();
        notice.level = warning.message_id == Slic3r::PrintStateBase::SlicingReplaceInitEmptyLayers ||
                               warning.message_id == Slic3r::PrintStateBase::SlicingEmptyGcodeLayers ?
                           SliceNoticeLevel::serious_warning :
                           SliceNoticeLevel::warning;
        // The message is the print's own, translated as the engine translates.
        UiText text;
        text.msgid = warning.message;
        notice.text.push_back(std::move(text));
        notice.object_index = object_index;
        notice.step_warning = true;
    }
}

UiText ui_text(const std::string& msgid, std::vector<std::string> args = {})
{
    UiText text;
    text.msgid = msgid;
    text.args = std::move(args);
    return text;
}

// The warnings the plate's print holds, then GLCanvas3D::_update_slice_error_status()
// of its G-code (GCodeViewer::load_as_gcode()'s judgement of the paths).
void add_slice_notices(const Slic3r::Print& print, const Slic3r::Model& model, const Slic3r::GCodeProcessorResult& result,
                       const Slic3r::BuildVolume& build_volume, std::vector<SliceNotice>& notices)
{
    for (int step = 0; step < int(Slic3r::psCount); ++step) {
        add_step_warnings(print.step_state_with_warnings(Slic3r::PrintStep(step)), -1, notices);
    }
    for (const Slic3r::PrintObject* print_object : print.objects()) {
        const std::int32_t object_index = plate_copy_of(model, print_object->model_object()->id(), {}).first;
        for (int step = 0; step < int(Slic3r::posCount); ++step) {
            add_step_warnings(print_object->step_state_with_warnings(Slic3r::PrintObjectStep(step)), object_index, notices);
        }
    }

    // GCodeViewer::load_as_gcode(): the box of the extrusions, whether every
    // path is on the plate (BuildVolume::all_paths_inside()), and the heights of
    // the layers the paths make.
    const auto extrusion = [](const Slic3r::GCodeProcessorResult::MoveVertex& move) {
        return move.type == Slic3r::EMoveType::Extrude && move.extrusion_role != Slic3r::erCustom && move.extrusion_role != Slic3r::erNone &&
               move.width != 0.f && move.height != 0.f;
    };
    Slic3r::BoundingBoxf3 paths_bbox;
    std::vector<float> layer_zs;
    for (const Slic3r::GCodeProcessorResult::MoveVertex& move : result.moves) {
        if (!extrusion(move)) {
            continue;
        }
        paths_bbox.merge(move.position.cast<double>());
        layer_zs.push_back(move.position.z());
    }
    if (layer_zs.empty()) {
        return;
    }
    std::sort(layer_zs.begin(), layer_zs.end());
    layer_zs.erase(std::unique(layer_zs.begin(), layer_zs.end(), [](const float a, const float b) { return std::abs(a - b) < 1e-4f; }), layer_zs.end());
    const bool contained_in_bed = build_volume.all_paths_inside(result, paths_bbox);
    const double top = layer_zs.back();
    const double max_print_height = result.printable_height;

    // _set_warning_notification(): EWarning::ToolHeightOutside and ToolpathOutside.
    if (top - max_print_height >= 1e-6) {
        SliceNotice& notice = notices.emplace_back();
        notice.level = SliceNoticeLevel::error;
        notice.text.push_back(ui_text("A G-code path goes beyond the max print height."));
    }
    if (!contained_in_bed && max_print_height - top >= 1e-6) {
        SliceNotice& notice = notices.emplace_back();
        notice.level = SliceNoticeLevel::error;
        notice.text.push_back(ui_text("A G-code path goes beyond the plate boundaries."));
    }
    // EWarning::GCodeConflict: the layer at the height of the conflict, which
    // the viewer finds in its layers (Layers::get_layer_id_at()), and the copy
    // of the second object, whose object "Jump to" selects.
    if (contained_in_bed && result.conflict_result.has_value()) {
        const Slic3r::ConflictResult& conflict = *result.conflict_result;
        const std::size_t layer = std::size_t(std::lower_bound(layer_zs.begin(), layer_zs.end(), float(conflict._height) - 1e-4f) - layer_zs.begin());
        char height[32];
        std::snprintf(height, sizeof(height), "%.2f", conflict._height);
        SliceNotice& notice = notices.emplace_back();
        notice.level = SliceNoticeLevel::serious_warning;
        notice.text.push_back(ui_text(
            "Conflicts of G-code paths have been found at layer %d, Z = %.2lfmm. Please separate the conflicted objects farther (%s <-> %s).",
            {std::to_string(layer), height, conflict._objName1, conflict._objName2}));
        if (const auto* instance = reinterpret_cast<const Slic3r::PrintInstance*>(conflict._obj2); instance != nullptr) {
            if (instance->model_instance != nullptr && instance->model_instance->get_object() != nullptr) {
                const auto copy = plate_copy_of(model, instance->model_instance->get_object()->id(), instance->model_instance->id());
                notice.object_index = copy.first;
                notice.instance_index = copy.second;
            } else if (instance->print_object != nullptr) {
                notice.object_index = plate_copy_of(model, instance->print_object->model_object()->id(), {}).first;
            }
        }
    }
    // EWarning::FilamentUnPrintableOnFirstLayer: the filaments, from 1, each followed by a space.
    if (!result.filament_printable_reuslt.conflict_filament.empty()) {
        std::string filaments;
        for (const int filament : result.filament_printable_reuslt.conflict_filament) {
            filaments += std::to_string(filament + 1) + " ";
        }
        SliceNotice& notice = notices.emplace_back();
        notice.level = SliceNoticeLevel::error;
        notice.text.push_back(ui_text("Filaments %s cannot be printed directly on the surface of this plate.", {filaments}));
    }
}

// run_post_process_scripts(): the process names a script on a line of post_process.
bool has_post_process_scripts(const Slic3r::DynamicPrintConfig& config)
{
    const auto* post_process = config.option<Slic3r::ConfigOptionStrings>("post_process");
    if (post_process == nullptr) {
        return false;
    }
    for (const std::string& scripts : post_process->values) {
        std::vector<std::string> lines;
        boost::split(lines, scripts, boost::is_any_of("\r\n"));
        for (std::string script : lines) {
            boost::trim(script);
            if (!script.empty()) {
                return true;
            }
        }
    }
    return false;
}

}  // namespace

SliceResult slice(
    const std::string& job_id,
    const std::vector<PlateObject>& objects,
    const std::string& output_path,
    const std::string& toolpaths_path,
    const ProfileSelection& profiles,
    const ModelSettings& plate_settings,
    const ProgressCallback& on_progress,
    const std::string& wipe_tower_mesh_path,
    const std::vector<ThumbnailImage>& thumbnails,
    const std::vector<LayerGcode>& layer_gcodes,
    const CalibrationParams& calibration,
    const CalibrationParams& pa_pattern,
    const std::string& slice_info_path,
    const OutputNaming& naming
)
{
    if (!acquire_job(job_id)) {
        return failure(SliceStatus::busy, "Another slicing job is running");
    }
    const ActiveJob active_job;

    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return failure(SliceStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }

    const std::string temporary_path = output_path + ".part";
    try {
        Slic3r::DynamicPrintConfig config;
        std::string message;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, message, false);
            status != SliceStatus::success) {
            return failure(status, message);
        }
        // BackgroundSlicingProcess::apply(): the settings of the plate are laid
        // over the ones of the presets before the print is applied.
        // Plater::_calib_pa_pattern_gen_gcode() generates the pattern from the presets alone.
        const Slic3r::DynamicPrintConfig presets_config =
            pa_pattern.mode == CalibrationMode::pa_pattern ? config : Slic3r::DynamicPrintConfig();
        config.apply(detail::model_config(plate_settings), true);

        Slic3r::Model model;
        if (!load_plate(objects, config, model, message)) {
            return failure(SliceStatus::model_read_failed, message);
        }
        keep_models_of(objects);
        if (std::string outside; !check_print_volume(model, config, outside)) {
            return failure(SliceStatus::invalid_print, outside);
        }
        const std::vector<int> filaments = plate_filaments(model, config, layer_gcodes);
        // The codes the layer slider put on the plate (Plater's
        // EVT_CUSTOMEVT_TICKSCHANGED), with the mode of the slider
        // (IMSlider::GetTicksValues(), Preview::update_layers_slider_mode()).
        const std::size_t filament_count = std::max<std::size_t>(profiles.filaments.size(), 1);
        if (!layer_gcodes.empty()) {
            Slic3r::CustomGCode::Info info;
            info.mode = filament_count > 1 ? Slic3r::CustomGCode::MultiAsSingle : Slic3r::CustomGCode::SingleExtruder;
            for (const LayerGcode& code : layer_gcodes) {
                info.gcodes.push_back({code.print_z, static_cast<Slic3r::CustomGCode::Type>(code.type), code.extruder, code.color, code.extra});
            }
            std::sort(info.gcodes.begin(), info.gcodes.end());
            // PartPlateList::select_plate() sets the model's current plate.
            model.curr_plate_index = engine().plate_index;
            model.plates_custom_gcodes[model.curr_plate_index] = info;
        }

        // Plater::_calib_pa_pattern_gen_gcode(): the PA pattern of every handle
        // on the plate, merged layer by layer into the plate's codes.
        if (pa_pattern.mode == CalibrationMode::pa_pattern) {
            pa_pattern_gcodes(model, presets_config, pa_pattern);
        }

        // The project's model name, which filename_format may name the G-code after.
        if (!naming.model_name.empty()) {
            model.model_info = std::make_shared<Slic3r::ModelInfo>();
            model.model_info->model_name = naming.model_name;
        }

        Slic3r::Print print;
        // PartPlate::set_print() and set_index(): the print of the current
        // plate starts at its origin, which G-code coordinates are relative
        // to (Print leaves it uninitialized otherwise), and takes the plate's
        // wipe tower position.
        print.set_plate_origin(Slic3r::to_3d(plate_origin_of(config), 0.));
        print.set_plate_index(engine().plate_index);
        print.set_plate_name(naming.plate_name);
        // The calibration the Calibration menu set on the plate's print.
        print.set_calib_params(detail::calib_params(calibration));
        print.set_status_callback([&on_progress](const Slic3r::PrintBase::SlicingStatus& status) {
            if (on_progress && status.percent >= 0) {
                on_progress(status.percent, status.text);
            }
        });
        if (!attach_print(&print)) {
            return failure(SliceStatus::cancelled, {});
        }

        // Plater::priv::update(): the Preferences' "Remove mixed temperature
        // restriction" lets filaments of very different temperatures print together.
        print.set_check_multi_filaments_compatibility(engine().config->get("enable_high_low_temp_mixed_printing") == "false");
        print.apply(model, config);
        // Plater::priv::on_action_slice_plate(): the extruder parameters and the
        // speed table of the presets, which the automatic brim width reads
        // (configBrimWidthByVolumeGroups()), before the plate is sliced.
        {
            const Slic3r::DynamicPrintConfig presets = engine().bundle->full_config();
            Slic3r::Model::setExtruderParams(presets, int(engine().bundle->filament_presets.size()));
            Slic3r::Model::setPrintSpeedTable(presets, print.config());
        }
        // BackgroundSlicingProcess::validate(): the checks of a Bambu printer
        // differ, so the print knows it before it validates.
        print.is_BBL_printer() = engine().bundle->is_bbl_vendor();
        Slic3r::StringObjectException warning;
        const Slic3r::StringObjectException error = print.validate(&warning);
        if (!error.string.empty()) {
            return failure(SliceStatus::invalid_print, error.string);
        }
        if (cancellation_requested()) {
            return failure(SliceStatus::cancelled, {});
        }

        print.process();

        Slic3r::GCodeProcessorResult gcode_result;
        // The desktop app renders the thumbnails while the G-code is exported;
        // the app rendered them before, and they are read as they are asked for.
        print.export_gcode(temporary_path, &gcode_result,
                           [&thumbnails](const Slic3r::ThumbnailsParams& params) { return load_thumbnails(thumbnails, params); });
        boost::system::error_code rename_error;
        fs::rename(temporary_path, output_path, rename_error);
        if (rename_error) {
            remove_file(temporary_path);
            return failure(SliceStatus::output_write_failed, rename_error.message());
        }

        using TimeMode = Slic3r::PrintEstimatedStatistics::ETimeMode;
        const float print_time =
            gcode_result.print_statistics.modes[static_cast<std::size_t>(TimeMode::Normal)].time;

        SliceResult result;
        result.status = SliceStatus::success;
        result.sequential = print.config().print_sequence == Slic3r::PrintSequence::ByObject;
        result.can_change_filament = print.object_extruders().size() <= 1 && !print.config().spiral_mode;
        result.has_template = !print.config().template_custom_gcode.value.empty();
        result.layer_count = printed_layer_count(print);
        result.estimated_print_time_seconds = std::llround(print_time);
        result.filament_micrometers = std::llround(print.print_statistics().total_used_filament * 1'000.0);
        result.total_cost = print.print_statistics().total_cost;
        // Plater::export_gcode(): BackgroundSlicingProcess::output_filepath_for_project()
        // of the finished print, whose statistics fill the template, folded to
        // ASCII; the file dialog offers its name alone.
        try {
            const std::string name = Slic3r::fold_utf8_to_ascii(print.output_filename(naming.filename_base));
            result.output_name = fs::path(name).filename().string();
        } catch (const Slic3r::PlaceholderParserError& error) {
            result.output_name_error = error.what();
        }
        result.filaments = filament_usage(gcode_result, filaments, config.option<Slic3r::ConfigOptionStrings>("filament_colour")->values.size());
        add_slice_notices(print, model, gcode_result, build_volume_of(config), result.notices);
        result.print_ready = gcode_result.filament_printable_reuslt.conflict_filament.empty() && gcode_result.gcode_check_result.error_code == 0;
        result.post_process_skipped = has_post_process_scripts(config);
        result.toolpaths_written = !toolpaths_path.empty() && write_toolpaths(gcode_result, print, config, toolpaths_path);
        result.slice_info_written = !slice_info_path.empty() && detail::write_slice_info(print, gcode_result, slice_info_path);
        // GLCanvas3D::reload_scene(): once the wipe tower is built, the plate
        // shows it as the slice made it — its ribs and its brim, in the tower's
        // own coordinates — instead of the box it estimated.
        if (!wipe_tower_mesh_path.empty() && print.has_wipe_tower() && print.wipe_tower_data().wipe_tower_mesh_data) {
            Slic3r::TriangleMesh tower = print.wipe_tower_data().wipe_tower_mesh_data->real_wipe_tower_mesh;
            tower.merge(print.wipe_tower_data().wipe_tower_mesh_data->real_brim_mesh);
            // its_make_rib_tower() fills the triangles without the mesh's
            // statistics, so TriangleMesh::empty() calls a rib tower empty;
            // the desktop app draws from its triangles, and so is this.
            result.wipe_tower_written = !tower.its.indices.empty() && write_mesh(tower.its, wipe_tower_mesh_path);
        }
        return result;
    } catch (const Slic3r::CanceledException&) {
        remove_file(temporary_path);
        return failure(SliceStatus::cancelled, {});
    } catch (const Slic3r::SlicingErrors& errors) {
        // BackgroundSlicingProcess: the messages are in the individual errors, not in what().
        remove_file(temporary_path);
        std::string message;
        for (const Slic3r::SlicingError& error : errors.errors_) {
            if (!message.empty()) {
                message.push_back(char(10));
            }
            message += error.what();
        }
        return failure(SliceStatus::slicing_failed, message.empty() ? errors.what() : message);
    } catch (const std::exception& error) {
        remove_file(temporary_path);
        return failure(SliceStatus::slicing_failed, error.what());
    }
}

namespace {

// Plater::post_process_string_object_exception(): a filament the bed type is
// not meant for is named in the message, by its alias or its name without the
// printer.
void post_process_string_object_exception(Slic3r::StringObjectException& err, const Slic3r::PresetBundle& bundle)
{
    if (err.type != Slic3r::StringExceptionType::STRING_EXCEPT_FILAMENT_NOT_MATCH_BED_TYPE || err.params.size() < 3) {
        return;
    }
    try {
        const int extruder_id = std::atoi(err.params[2].c_str()) - 1;
        if (extruder_id < 0 || std::size_t(extruder_id) >= bundle.filament_presets.size()) {
            return;
        }
        std::string filament_name = bundle.filament_presets[std::size_t(extruder_id)];
        for (const Slic3r::Preset& filament : bundle.filaments) {
            if (filament.name == filament_name) {
                if (!filament.alias.empty()) {
                    filament_name = filament.alias;
                } else if (const std::size_t at = filament_name.find('@'); at != std::string::npos && at > 0) {
                    filament_name = filament_name.substr(0, at - 1);
                }
                break;
            }
        }
        err.string = (boost::format(Slic3r::I18N::translate(
                          "Plate %d: %s is not suggested to be used to print filament %s (%s). "
                          "If you still want to do this print job, please set this filament's bed temperature to non-zero."))
                      % err.params[0] % err.params[1] % err.params[2] % filament_name).str();
        err.string += "\n";
    } catch (...) {
    }
}

// The message of [err], with the copy it is about found among the plate's
// objects by its id: a print object or model object names the object, a model
// instance the copy.
ValidationMessage validation_message(
    const Slic3r::StringObjectException& err,
    const std::map<std::size_t, std::int32_t>& objects,
    const std::map<std::size_t, std::pair<std::int32_t, std::int32_t>>& copies
)
{
    ValidationMessage message;
    message.text = err.string;
    message.option = err.opt_key;
    const auto* print_object = dynamic_cast<const Slic3r::PrintObjectBase*>(err.object);
    const auto* model_object = print_object != nullptr ? print_object->model_object() : dynamic_cast<const Slic3r::ModelObject*>(err.object);
    const auto* model_instance = dynamic_cast<const Slic3r::ModelInstance*>(err.object);
    if (model_instance != nullptr) {
        if (const auto copy = copies.find(model_instance->id().id); copy != copies.end()) {
            message.object = copy->second.first;
            message.instance = copy->second.second;
        }
    } else if (model_object != nullptr) {
        if (const auto object = objects.find(model_object->id().id); object != objects.end()) {
            message.object = object->second;
        }
    }
    return message;
}

void add_outline(const Slic3r::Polygon& polygon, std::vector<std::int32_t>& counts, std::vector<double>& points)
{
    counts.push_back(std::int32_t(polygon.points.size()));
    for (const Slic3r::Point& point : polygon.points) {
        points.push_back(Slic3r::unscale<double>(point.x()));
        points.push_back(Slic3r::unscale<double>(point.y()));
    }
}

}  // namespace

PlateValidation validate_plate(
    const std::vector<PlateObject>& plate,
    const ProfileSelection& profiles,
    const ModelSettings& plate_settings
)
{
    PlateValidation result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        std::string message;
        if (select_profiles(*engine().bundle, profiles, config, message, false) != SliceStatus::success) {
            return result;
        }
        config.apply(detail::model_config(plate_settings), true);

        Slic3r::Model model;
        if (!load_plate(plate, config, model, message)) {
            return result;
        }
        // The objects and copies of the plate by their ids, which the print's own model keeps.
        std::map<std::size_t, std::int32_t> objects;
        std::map<std::size_t, std::pair<std::int32_t, std::int32_t>> copies;
        std::vector<std::size_t> copy_ids;
        for (std::size_t object = 0; object < model.objects.size(); ++object) {
            objects.emplace(model.objects[object]->id().id, std::int32_t(object));
            for (std::size_t instance = 0; instance < model.objects[object]->instances.size(); ++instance) {
                const std::size_t id = model.objects[object]->instances[instance]->id().id;
                copies.emplace(id, std::make_pair(std::int32_t(object), std::int32_t(instance)));
                copy_ids.push_back(id);
            }
        }
        // update_print_volume_state(), then BackgroundSlicingProcess::apply() of the current plate.
        model.update_print_volume_state(build_volume_of(config));
        Slic3r::Print print;
        print.set_plate_origin(Slic3r::to_3d(plate_origin_of(config), 0.));
        print.set_plate_index(engine().plate_index);
        print.set_check_multi_filaments_compatibility(engine().config->get("enable_high_low_temp_mixed_printing") == "false");
        print.is_BBL_printer() = engine().bundle->is_bbl_vendor();
        print.apply(model, config);
        result.read = true;
        for (const Slic3r::PrintObject* print_object : print.objects()) {
            const auto found = objects.find(print_object->model_object()->id().id);
            if (found != objects.end()) {
                result.print_objects.push_back(found->second);
                result.print_z_min.push_back(print_object->slicing_parameters().object_print_z_min);
            }
        }
        // background_process.empty(): a plate with nothing to print is not validated.
        if (print.empty()) {
            return result;
        }

        Slic3r::StringObjectException warning;
        Slic3r::Polygons polygons;
        std::vector<std::pair<Slic3r::Polygon, float>> height_polygons;
        Slic3r::StringObjectException err = print.validate(&warning, &polygons, &height_polygons);
        post_process_string_object_exception(err, *engine().bundle);
        result.error = validation_message(err, objects, copies);
        result.warning = validation_message(warning, objects, copies);
        // set_sequential_print_clearance_polygons() while the print is not valid.
        if (!err.string.empty()) {
            for (const Slic3r::Polygon& polygon : polygons) {
                add_outline(polygon, result.clearance_counts, result.clearance);
            }
            for (const Slic3r::ExPolygon& polygon : Slic3r::union_ex(polygons)) {
                for (const Slic3r::Vec3d& corner : Slic3r::triangulate_expolygon_3d(polygon)) {
                    result.clearance_fill.push_back(corner.x());
                    result.clearance_fill.push_back(corner.y());
                }
            }
            for (const auto& [polygon, height] : height_polygons) {
                for (const Slic3r::Vec3d& corner : Slic3r::triangulate_expolygon_3d(Slic3r::ExPolygon(polygon))) {
                    result.height_fill.push_back(corner.x());
                    result.height_fill.push_back(corner.y());
                    result.height_fill.push_back(height);
                }
            }
        }

        // GLCanvas3D::_render(): sequential_print, from PartPlate::get_real_print_seq() and print_order.
        const auto* order = config.option<Slic3r::ConfigOptionEnum<Slic3r::PrintOrder>>("print_order");
        const bool sequential = config.opt_enum<Slic3r::PrintSequence>("print_sequence") == Slic3r::PrintSequence::ByObject
            || (order != nullptr && order->value == Slic3r::PrintOrder::AsObjectList);
        if (sequential) {
            result.sequence.assign(copy_ids.size(), -1);
            for (const Slic3r::PrintObject* print_object : print.objects()) {
                for (const Slic3r::PrintInstance& instance : print_object->instances()) {
                    const auto found = std::find(copy_ids.begin(), copy_ids.end(), instance.model_instance->id().id);
                    if (found != copy_ids.end()) {
                        result.sequence[std::size_t(found - copy_ids.begin())] = instance.model_instance->arrange_order;
                    }
                }
            }
        }
        return result;
    } catch (const std::exception&) {
        return PlateValidation{};
    }
}

ThumbnailSizes thumbnail_sizes(const ProfileSelection& profiles)
{
    ThumbnailSizes result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (select_profiles(*engine().bundle, profiles, config, result.message) != SliceStatus::success) {
            result.status = SceneStatus::profile_not_found;
            return result;
        }
        result.status = SceneStatus::success;
        // GCode::_do_export(): a Bambu Lab printer's G-code holds no thumbnails.
        if (engine().bundle->is_bbl_vendor()) {
            return result;
        }
        // An invalid list fails the export, which reports it.
        const auto [thumbnails, errors] = Slic3r::GCodeThumbnails::make_and_check_thumbnail_list(config);
        for (const auto& [format, size] : thumbnails) {
            result.sizes.push_back(std::int32_t(std::lround(size.x())));
            result.sizes.push_back(std::int32_t(std::lround(size.y())));
        }
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
    }
    return result;
}

double overhang_normal_z(const ProfileSelection& profiles)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        return std::numeric_limits<double>::quiet_NaN();
    }
    try {
        using namespace Slic3r;
        DynamicPrintConfig selected;
        std::string message;
        if (select_profiles(*engine().bundle, profiles, selected, message) != SliceStatus::success) {
            return std::numeric_limits<double>::quiet_NaN();
        }
        // ORCA: Compute slope.normal_z for 3D overhang highlight directly from support settings.
        // If support_threshold_angle is 0, use tree fallback angle (30 deg) for tree supports,
        // and derive an equivalent angle from threshold overlap for normal supports.
        const DynamicPrintConfig& glb_cfg  = engine().bundle->prints.get_edited_preset().config;
        const auto& full_cfg               = selected;
        const auto support_type            = glb_cfg.opt_enum<SupportType>("support_type");
        const int  support_threshold_angle = glb_cfg.opt_int("support_threshold_angle");
        double angle_rad;

        if (support_threshold_angle > 0) {
            // Match support generation: explicit threshold angles are treated as inclusive.
            const int effective_support_threshold_angle = std::min(support_threshold_angle + 1, 89);
            angle_rad = Geometry::deg2rad(static_cast<double>(effective_support_threshold_angle));
        } else if (is_tree(support_type)) {
            angle_rad = Geometry::deg2rad(30.0); // fallback value for tree supports
        } else { // For normal supports, if the angle is set to 0, calculate normal_z from overlap.
            const double layer_height        = full_cfg.opt_float("layer_height");
            const auto*  nozzle_diameter_opt = full_cfg.option<ConfigOptionFloats>("nozzle_diameter");
            const int    wall_filament_id       = full_cfg.opt_int("outer_wall_filament_id");
            const size_t nozzle_count        = nozzle_diameter_opt->values.size();
            const size_t wall_extruder_idx   = (wall_filament_id > 0 && wall_filament_id <= static_cast<int>(nozzle_count))
                ? static_cast<size_t>(wall_filament_id - 1)
                : 0; // Invalid extruder index falls back to extruder 1.

            // Use wall extruder's nozzle diameter for better estimation of external perimeter width,
            // which is more relevant to overhang printing than the default nozzle diameter.
            const double nozzle_diameter = nozzle_diameter_opt->values[wall_extruder_idx];

            double external_perimeter_width = full_cfg.get_abs_value("outer_wall_line_width", nozzle_diameter);
            if (external_perimeter_width <= 0.0) {
                external_perimeter_width = full_cfg.get_abs_value("line_width", nozzle_diameter);

                if (external_perimeter_width <= 0.0)
                    external_perimeter_width = nozzle_diameter;
            }

            const double overlap_width      = full_cfg.get_abs_value("support_threshold_overlap", external_perimeter_width);
            const double lower_layer_offset = std::max(0.0, external_perimeter_width - overlap_width);

            angle_rad = lower_layer_offset <= EPSILON ? Geometry::deg2rad(89.0) : std::atan(layer_height / lower_layer_offset);
        }

        return static_cast<float>(-std::cos(std::clamp(angle_rad, 0.0, Geometry::deg2rad(89.0))));
    } catch (const std::exception&) {
        return std::numeric_limits<double>::quiet_NaN();
    }
}

void select_plate(const int index, const int count)
{
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    engine().plate_count = std::max(count, 1);
    engine().plate_index = std::clamp(index, 0, engine().plate_count - 1);
}

bool cancel(const std::string& job_id)
{
    const std::lock_guard<std::mutex> lock(job_mutex);
    if (active_job_id.empty() || active_job_id != job_id) {
        return false;
    }
    cancel_requested = true;
    if (active_print != nullptr) {
        active_print->cancel();
    }
    return true;
}

PlateDescription describe_plate(const ProfileSelection& profiles, const std::string& output_dir)
{
    PlateDescription result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }

        // Plater::set_bed_shape() and PartPlate::set_shape() for the first plate,
        // whose origin is (0, 0).
        const Slic3r::Pointfs shape = Slic3r::make_counter_clockwise(config.option<Slic3r::ConfigOptionPoints>("printable_area")->values);
        result.printable_height = config.opt_float("printable_height");
        for (const Slic3r::Vec2d& point : shape) {
            result.printable_area.push_back(point.x());
            result.printable_area.push_back(point.y());
        }

        // Bed3D::build_volume(): the shape the 3D view darkens objects outside and casts shadows inside.
        const Slic3r::BuildVolume build_volume(shape, result.printable_height, {}, {});
        result.build_volume_type = static_cast<int>(build_volume.type());
        if (build_volume.type() == Slic3r::BuildVolume_Type::Circle) {
            result.circle_center_x = Slic3r::unscaled<double>(build_volume.circle().center.x());
            result.circle_center_y = Slic3r::unscaled<double>(build_volume.circle().center.y());
            result.circle_radius = Slic3r::unscaled<double>(build_volume.circle().radius);
        }

        Slic3r::ExPolygon print_polygon;
        for (const Slic3r::Vec2d& point : shape) {
            print_polygon.contour.append({scale_(point(0)), scale_(point(1))});
        }
        result.plate_triangles = triangulate(print_polygon);
        result.exclude_triangles = triangulate(exclude_polygon(config.option<Slic3r::ConfigOptionPoints>("bed_exclude_area")->values));

        // PartPlate::calc_gridlines()
        const Slic3r::BoundingBox plate_box = print_polygon.contour.bounding_box();
        const int step = calculate_grid_step(plate_box, scale_(1.00));
        const auto [thin, bold] = generate_grid(print_polygon, plate_box, Slic3r::Vec2d(scale_(0.0), scale_(0.0)), scale_(step), SCALED_EPSILON);
        Slic3r::Lines thin_lines = Slic3r::to_lines(thin);
        const Slic3r::Lines contour_lines = Slic3r::to_lines(print_polygon);
        thin_lines.insert(thin_lines.end(), contour_lines.begin(), contour_lines.end());
        result.thin_grid_lines = line_coordinates(thin_lines);
        result.bold_grid_lines = line_coordinates(Slic3r::to_lines(bold));

        result.filament_colour = config.opt_string("filament_colour", 0u);

        fs::create_directories(output_dir);

        // Bed3D::set_shape(): a custom model replaces the system one; only an
        // existing STL file is used.
        std::string model = config.opt_string("bed_custom_model");
        if (model.empty()) {
            model = system_bed_model(*engine().bundle, shape);
        }
        const std::string model_mesh = (fs::path(output_dir) / "bed_model.mesh").string();
        remove_file(model_mesh);
        if (boost::algorithm::iends_with(model, ".stl") && file_exists(model)) {
            // GLModel::init_from_file()
            const Slic3r::TriangleMesh mesh = Slic3r::Model::read_from_file(model).mesh();
            if (!write_mesh(mesh.its, model_mesh)) {
                result.status = SceneStatus::write_failed;
                result.message = "Unable to write " + model_mesh;
                return result;
            }
            result.bed_model_mesh = model_mesh;
        }

        std::string texture = config.opt_string("bed_custom_texture");
        if (texture.empty()) {
            texture = system_bed_texture(*engine().bundle);
        }
        const std::string texture_png = (fs::path(output_dir) / "bed_texture.png").string();
        remove_file(texture_png);
        if (file_exists(texture)) {
            bool written = false;
            if (boost::algorithm::iends_with(texture, ".svg")) {
                written = rasterize_svg(texture, texture_png);
            } else if (boost::algorithm::iends_with(texture, ".png")) {
                boost::system::error_code error;
                fs::copy_file(texture, texture_png, fs::copy_options::overwrite_existing, error);
                written = !error;
            }
            if (written) {
                result.bed_texture = texture_png;
            }
        }

        // GCodeViewer::SequentialView::Marker::init(): GLModel::init_from_file() of the hotend.
        const std::string hotend = hotend_model(*engine().bundle);
        const std::string hotend_mesh = (fs::path(output_dir) / "hotend_model.mesh").string();
        remove_file(hotend_mesh);
        if (file_exists(hotend)) {
            const Slic3r::TriangleMesh mesh = Slic3r::Model::read_from_file(hotend).mesh();
            if (write_mesh(mesh.its, hotend_mesh)) {
                result.hotend_model_mesh = hotend_mesh;
            }
        }

        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

namespace {

// Selection::get_bounding_sphere() of the instance, as the app shows it.
void bounding_sphere(const Slic3r::ModelObject& object, const Slic3r::ModelInstance& instance, ModelInspection& result)
{
    const auto [center, radius] = detail::bounding_sphere(object, instance);
    result.sphere_center = {float(center.x()), float(center.y()), float(center.z())};
    result.sphere_radius = radius;
}

// Selection::get_full_unscaled_instance_bounding_box()
Slic3r::Vec3d unscaled_instance_size(const Slic3r::ModelObject& object, const Slic3r::ModelInstance& instance)
{
    Slic3r::BoundingBoxf3 box;
    for (const Slic3r::ModelVolume* volume : object.volumes) {
        const Slic3r::Transform3d matrix = instance.get_transformation().get_matrix_no_scaling_factor() * volume->get_matrix();
        box.merge(volume->get_convex_hull().transformed_bounding_box(matrix));
    }
    return box.size();
}

// Selection::get_full_unscaled_instance_local_bounding_box(): the object's
// volumes in its own coordinates.
Slic3r::Vec3d local_instance_size(const Slic3r::ModelObject& object)
{
    Slic3r::BoundingBoxf3 box;
    for (const Slic3r::ModelVolume* volume : object.volumes) {
        box.merge(volume->get_convex_hull().transformed_bounding_box(volume->get_matrix()));
    }
    return box.size();
}

// A placed object as the app shows it: its size, instance transformation, and
// whether it fits the build volume, as the model's update_print_volume_state()
// found (Plater::priv::update_print_volume_state), with what the gizmo windows
// show (GizmoObjectManipulation::update_settings_value).
void describe_placed(const Slic3r::ModelObject& object, const std::size_t index, ModelInspection& result)
{
    const Slic3r::ModelInstance& instance = *object.instances[index];
    const Slic3r::BoundingBoxf3 box = object.instance_bounding_box(index);
    const Slic3r::Vec3d size = box.size();
    const Slic3r::Vec3d center = box.center();
    const Slic3r::Transform3d& matrix = instance.get_matrix();
    result.size_x = size.x();
    result.size_y = size.y();
    result.size_z = size.z();
    result.box_center = {center.x(), center.y(), center.z()};
    std::copy(matrix.data(), matrix.data() + 16, result.instance_matrix.begin());
    bounding_sphere(object, instance, result);
    Slic3r::Vec3d rotation = instance.get_transformation().get_rotation_by_quaternion() * (180.0 / PI);
    for (int axis = 0; axis < 3; ++axis) {
        // delete_negative_sign()
        result.rotation_degrees[axis] = std::abs(rotation[axis]) < 0.001 ? 0.0 : rotation[axis];
    }
    const Slic3r::Vec3d unscaled = unscaled_instance_size(object, instance);
    result.unscaled_size = {unscaled.x(), unscaled.y(), unscaled.z()};
    const Slic3r::Vec3d local = local_instance_size(object);
    result.local_size = {local.x(), local.y(), local.z()};
    result.facet_count = static_cast<std::int64_t>(object.facets_count());
    const Slic3r::TriangleMeshStats stats = object.get_object_stl_stats();
    result.open_edges = static_cast<std::int64_t>(stats.open_edges);
    result.volume = stats.volume;
    switch (instance.print_volume_state) {
    case Slic3r::ModelInstancePVS_Inside:
    case Slic3r::ModelInstancePVS_Limited:
        result.volume_state = VolumeState::inside;
        break;
    case Slic3r::ModelInstancePVS_Partly_Outside:
        result.volume_state = VolumeState::partly_outside;
        break;
    default:
        result.volume_state = VolumeState::outside;
        break;
    }
}

}  // namespace

ModelInspection inspect_model(
    const std::string& model_path,
    const ProfileSelection& profiles,
    const std::string& mesh_path,
    const std::vector<PlateObject>& plate
)
{
    ModelInspection result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }

        Slic3r::Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        Slic3r::ModelObject* object = add_loaded_object(model_path, model);
        if (object == nullptr) {
            result.message = "Unable to read model " + model_path;
            return result;
        }
        std::vector<PlateObject> objects = plate;
        PlateObject added;
        added.model_path = model_path;
        objects.push_back(added);
        keep_models_of(objects);
        place_new_object(model, *object, config);

        const indexed_triangle_set mesh = object->raw_indexed_triangle_set();
        if (mesh.indices.empty()) {
            result.message = "The model has no facets";
            return result;
        }
        fs::create_directories(fs::path(mesh_path).parent_path());
        if (!write_mesh(mesh, mesh_path)) {
            result.status = SceneStatus::write_failed;
            result.message = "Unable to write " + mesh_path;
            return result;
        }

        model.update_print_volume_state(build_volume_of(config));
        describe_placed(*object, 0, result);
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

// OrientJob with the canvas's default OrientSettings (least support area): the
// instances of the selected objects, or of every object when none is selected
// (OrientJob::prepare_selection), turn as orientation::orient() finds and rest
// on the plate.
void auto_orient(
    Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    const std::vector<bool>& selected,
    const std::vector<bool>& locked_plates
)
{
    Slic3r::orientation::OrientParams params;
    Slic3r::orientation::OrientParamsArea params_area;
    // OrientJob::process() copies the area parameters over the defaults the same way.
    std::memcpy(&params, &params_area, sizeof(params));
    params.min_volume = false;
    // orientation::orient() reports progress and checks for cancellation without testing for callbacks.
    params.progressind = [](unsigned, std::string) {};
    params.stopcondition = [] { return false; };

    // OrientJob::prepare_selection(): the copies of the selected objects turn,
    // or every copy when none is selected; a copy on a locked plate stays, and
    // when all the selected ones are on locked plates, nothing turns.
    const int plates = detail::engine().plate_count;
    Slic3r::orientation::OrientMeshs meshes;
    Slic3r::orientation::OrientMeshs unselected;
    bool selected_is_locked = false;
    for (std::size_t index = 0; index < model.objects.size(); ++index) {
        const bool chosen = index < selected.size() && selected[index];
        Slic3r::ModelObject* object = model.objects[index];
        for (std::size_t copy = 0; copy < object->instances.size(); ++copy) {
            Slic3r::ModelInstance* instance = object->instances[copy];
            const int plate = plate_of(*object, copy, config, plates);
            if (plate >= 0 && plate < int(locked_plates.size()) && locked_plates[plate]) {
                selected_is_locked |= chosen;
                continue;
            }
            // OrientJob::get_orient_mesh()
            Slic3r::orientation::OrientMesh mesh;
            mesh.name = object->name;
            mesh.mesh = object->mesh();
            mesh.overhang_angle = config.opt_int("support_threshold_angle");
            mesh.setter = [instance](const Slic3r::orientation::OrientMesh& oriented) {
                instance->rotate(oriented.rotation_matrix);
                instance->get_object()->invalidate_bounding_box();
                instance->get_object()->ensure_on_bed();
            };
            (chosen ? meshes : unselected).push_back(std::move(mesh));
        }
    }
    if (meshes.empty() && !selected_is_locked) {
        meshes.swap(unselected);
    }
    Slic3r::orientation::orient(meshes, {}, params);
    for (const Slic3r::orientation::OrientMesh& mesh : meshes) {
        mesh.apply();
    }
}

// PartPlateList::preprocess_exclude_areas(): the wrapping detection area when
// enabled and the bounding box of every four points of the bed's excluded
// area, as virtual objects on each of num_plates beds.
void add_exclude_areas(Slic3r::arrangement::ArrangePolygons& unselected, const Slic3r::DynamicPrintConfig& config, int num_plates, float inflation)
{
    using namespace Slic3r;
    auto add = [&](const Polygon& contour, const std::string& name) {
        for (int bed = 0; bed < num_plates; ++bed) {
            arrangement::ArrangePolygon region;
            region.poly.contour = contour;
            region.translation = Vec2crd(0, 0);
            region.rotation = 0.0f;
            region.is_virt_object = true;
            region.bed_idx = bed;
            region.height = 1;
            region.name = name;
            region.inflation = inflation;
            unselected.emplace_back(std::move(region));
        }
    };
    if (config.opt_bool("enable_wrapping_detection")) {
        const Pointfs& wrapping = config.option<ConfigOptionPoints>("wrapping_exclude_area")->values;
        if (!wrapping.empty()) {
            Polygon contour;
            for (const Vec2d& point : wrapping) {
                contour.append({scale_(point(0)), scale_(point(1))});
            }
            add(contour, "WrappingRegion");
        }
    }
    // PartPlate::calculate_bounding_box(): a box per four points.
    const Pointfs& excluded = config.option<ConfigOptionPoints>("bed_exclude_area")->values;
    int index = 0;
    for (size_t start = 0; start + 4 <= excluded.size(); start += 4, ++index) {
        BoundingBoxf box;
        for (size_t point = start; point < start + 4; ++point) {
            box.merge(excluded[point]);
        }
        add(Polygon({
                {scaled(box.min.x()), scaled(box.min.y())},
                {scaled(box.max.x()), scaled(box.min.y())},
                {scaled(box.max.x()), scaled(box.max.y())},
                {scaled(box.min.x()), scaled(box.max.y())},
            }),
            "ExcludedRegion" + std::to_string(index));
    }
}

// init_arrange_params() with the arrange settings, and the extruder parameters
// and speed table ArrangeJob::prepare() sets for the arrangement.
Slic3r::arrangement::ArrangeParams init_arrange_params(Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config, const ArrangeSettings& settings)
{
    using namespace Slic3r;
    Print print;
    print.set_plate_origin(Vec3d::Zero());
    print.apply(model, config);
    const PrintConfig& print_config = print.config();
    const auto [object_skirt_offset, object_skirt_width] = print.object_skirt_offset();
    const bool sequential = config.opt_enum<PrintSequence>("print_sequence") == PrintSequence::ByObject;

    arrangement::ArrangeParams params;
    params.clearance_height_to_rod = print_config.extruder_clearance_height_to_rod.value;
    params.clearance_height_to_lid = print_config.extruder_clearance_height_to_lid.value;
    params.clearance_radius = print_config.extruder_clearance_radius.value + object_skirt_offset * 2;
    params.object_skirt_offset = object_skirt_offset;
    params.printable_height = print_config.printable_height.value;
    params.allow_rotations = settings.enable_rotation;
    params.nozzle_height = print_config.nozzle_height.value;
    params.align_center = print_config.best_object_pos.value;
    params.allow_multi_materials_on_same_plate = settings.allow_multi_materials_on_same_plate;
    params.avoid_extrusion_cali_region = true;
    params.is_seq_print = sequential;
    // GLCanvas3D::get_arrange_settings(): 0 means auto spacing; by-object printing keeps its minimum.
    params.min_obj_distance = scaled(sequential ? std::max(settings.distance, min_object_distance(config)) : settings.distance);
    // _render_arrange_menu(): no alignment to the Y axis while rotation is allowed.
    params.align_to_y_axis = settings.align_to_y_axis && !settings.enable_rotation;
    if (params.is_seq_print) {
        params.bed_shrink_x = BED_SHRINK_SEQ_PRINT;
        params.bed_shrink_y = BED_SHRINK_SEQ_PRINT;
    }

    Model::setExtruderParams(config, int(config.option<ConfigOptionStrings>("filament_colour")->values.size()));
    Model::setPrintSpeedTable(config, print_config);
    return params;
}

// The objects of model with a copy on plate (of plates): those copies alone,
// or with first_copy only, the objects whose first copy stands there
// (PartPlate::contain_instance_totally(object, 0)).
Slic3r::Model plate_model_of(const Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config, const int plate, const int plates, const bool first_copy)
{
    Slic3r::Model copy = model;
    for (std::size_t index = copy.objects.size(); index-- > 0;) {
        Slic3r::ModelObject* const object = copy.objects[index];
        const bool first_there = !object->instances.empty() && plate_of(*object, 0, config, plates) == plate;
        for (std::size_t instance = object->instances.size(); instance-- > 0;) {
            const bool keep = first_copy ? first_there && instance == 0 : plate_of(*object, instance, config, plates) == plate;
            if (!keep) {
                object->delete_instance(instance);
            }
        }
        if (object->instances.empty()) {
            copy.delete_object(index);
        }
    }
    return copy;
}

// The config of plate, with its own settings over it (its wipe tower among them).
Slic3r::DynamicPrintConfig plate_config_of(const Slic3r::DynamicPrintConfig& config, const std::vector<ModelSettings>& plate_settings, const int plate)
{
    Slic3r::DynamicPrintConfig plate_config = config;
    if (plate >= 0 && std::size_t(plate) < plate_settings.size()) {
        plate_config.apply(detail::model_config(plate_settings[std::size_t(plate)]), true);
    }
    return plate_config;
}

bool has_wipe_tower_position(const std::vector<ModelSettings>& plate_settings, const int plate)
{
    if (plate < 0 || std::size_t(plate) >= plate_settings.size()) {
        return false;
    }
    const std::vector<std::string>& keys = plate_settings[std::size_t(plate)].keys;
    return std::find(keys.begin(), keys.end(), std::string("wipe_tower_x")) != keys.end();
}

// GLCanvas3D::get_wipe_tower_info() and ArrangeJob's
// get_wipetower_arrange_poly(): the tower drawn on plate as a fixed item of
// the arrangement, its box grown by its brim (twice, as the desktop app grows
// it), at its position kept within the plate; none while the plate draws no
// tower.
std::optional<Slic3r::arrangement::ArrangePolygon> standing_wipe_tower(const detail::PlateTower& tower, const Slic3r::DynamicPrintConfig& config)
{
    using namespace Slic3r;
    if (!tower.shown) {
        return std::nullopt;
    }
    BoundingBoxf bb(Vec2d(0.0, 0.0), Vec2d(tower.width, tower.depth));
    double wt_brim_width = config.opt_float("prime_tower_brim_width");
    if (wt_brim_width < 0) {
        wt_brim_width = WipeTower::get_auto_brim_by_height(float(tower.height));
    }
    bb.offset(wt_brim_width);
    double brim_width = config.opt_float("prime_tower_brim_width");
    if (brim_width < 0) {
        brim_width = WipeTower::get_auto_brim_by_height(float(tower.height));
    }
    bb.offset(brim_width);
    // The wipe tower pos might be outside bed.
    const BoundingBoxf area(config.option<ConfigOptionPoints>("printable_area")->values);
    const Vec2d plate_size = area.size();
    const Vec2d position(std::clamp(tower.x, 0.0, plate_size(0) - bb.size().x()), std::clamp(tower.y, 0.0, plate_size(1) - bb.size().y()));

    arrangement::ArrangePolygon ap;
    ap.poly.contour = Polygon({
        {scaled(bb.min)},
        {scaled(bb.max.x()), scaled(bb.min.y())},
        {scaled(bb.max)},
        {scaled(bb.min.x()), scaled(bb.max.y())},
    });
    ap.translation = scaled(position);
    ap.rotation = 0.0;
    ap.name = "WipeTower";
    ap.is_virt_object = true;
    ap.is_wipe_tower = true;
    ++ap.priority;
    ap.bed_idx = 0;
    // do not move wipe tower
    ap.setter = nullptr;
    return ap;
}

// estimate_wipe_tower_info() and PartPlate::estimate_wipe_tower_polygon():
// the tower a plate would need for extruder_size filaments, at the project's
// position for the plate (the first plate's beyond the last), kept off the
// plate's edges, from the objects that stand on the plate (the last one beyond
// it).
Slic3r::arrangement::ArrangePolygon estimated_wipe_tower(
    const Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    const std::vector<ModelSettings>& plate_settings,
    const int plate_index,
    const int plates,
    const int extruder_size
)
{
    using namespace Slic3r;
    const int plate_index_valid = std::min(plate_index, plates - 1);
    const float w = float(config.opt_float("prime_tower_width"));
    const float tower_brim_width = float(config.opt_float("prime_tower_brim_width"));
    const double max_height = detail::plate_objects_height(plate_model_of(model, config, plate_index_valid, plates, true));
    const Vec3d wt_size = detail::estimate_wipe_tower_size(config, extruder_size, max_height);

    // wipe_tower_x.get_at(plate_index): the plate's own, or the first plate's.
    const int placed_plate = std::size_t(plate_index) < plate_settings.size() ? plate_index : 0;
    float x;
    float y;
    if (has_wipe_tower_position(plate_settings, placed_plate)) {
        const DynamicPrintConfig placed = plate_config_of(config, plate_settings, placed_plate);
        x = float(placed.option<ConfigOptionFloats>("wipe_tower_x")->get_at(0));
        y = float(placed.option<ConfigOptionFloats>("wipe_tower_y")->get_at(0));
    } else {
        const Vec2d position = detail::default_wipe_tower_position(config, wt_size, tower_brim_width < 0
            ? WipeTower::get_auto_brim_by_height(float(max_height)) : tower_brim_width);
        x = float(position(0));
        y = float(position(1));
    }
    const BoundingBoxf area(config.option<ConfigOptionPoints>("printable_area")->values);
    const float plate_width = float(area.size()(0));
    const float plate_depth = float(area.size()(1));
    const float depth = float(wt_size(1));
    const float margin = WIPE_TOWER_MARGIN + tower_brim_width;
    float wp_brim_width = tower_brim_width;
    if (wp_brim_width < 0) {
        wp_brim_width = WipeTower::get_auto_brim_by_height(float(wt_size.z()));
    }
    x = std::clamp(x, margin, plate_width - w - margin - wp_brim_width);
    y = std::clamp(y, margin, plate_depth - depth - margin - wp_brim_width);

    arrangement::ArrangePolygon wipe_tower_ap;
    wipe_tower_ap.poly.contour = Polygon({
        {scaled(x - wp_brim_width), scaled(y - wp_brim_width)},
        {scaled(x + w + wp_brim_width), scaled(y - wp_brim_width)},
        {scaled(x + w + wp_brim_width), scaled(y + depth + wp_brim_width)},
        {scaled(x - wp_brim_width), scaled(y + depth + wp_brim_width)},
    });
    wipe_tower_ap.bed_idx = plate_index;
    // do not move wipe tower
    wipe_tower_ap.setter = nullptr;
    wipe_tower_ap.translation = {scaled(0.f), scaled(0.f)};
    wipe_tower_ap.name = "WipeTower";
    wipe_tower_ap.is_virt_object = true;
    wipe_tower_ap.is_wipe_tower = true;
    return wipe_tower_ap;
}

// ArrangeJob::prepare_wipe_tower(): no tower without the prime tower or in
// sequential printing; a plate's standing tower is kept clear of; and where
// none stands, a tower is estimated for every bed once an object to arrange
// prints with several filaments, the timelapse is smooth, or filaments of the
// same bed temperature may share a plate. The beds count the unlocked plates.
void add_wipe_towers(
    Slic3r::arrangement::ArrangePolygons& unselected,
    const Slic3r::arrangement::ArrangePolygons& selected,
    const Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    const Slic3r::arrangement::ArrangeParams& params,
    const std::vector<ModelSettings>& plate_settings,
    const std::vector<bool>& locked,
    const int plates
)
{
    using namespace Slic3r;
    if (plate_settings.empty() || !config.opt_bool("enable_prime_tower") || params.is_seq_print) {
        return;
    }
    bool need_wipe_tower = false;
    if (const auto* const timelapse = config.option<ConfigOptionEnum<TimelapseType>>("timelapse_type");
        timelapse != nullptr && timelapse->value == TimelapseType::tlSmooth) {
        need_wipe_tower = true;
    }
    for (const arrangement::ArrangePolygon& item : selected) {
        if (std::set<int>(item.extrude_ids.begin(), item.extrude_ids.end()).size() > 1) {
            need_wipe_tower = true;
            break;
        }
    }
    if (params.allow_multi_materials_on_same_plate) {
        std::map<int, std::set<int>> bed_temp_to_extruder_ids;
        for (const arrangement::ArrangePolygon& item : selected) {
            for (const int id : item.extrude_ids) {
                bed_temp_to_extruder_ids[item.bed_temp].insert(id);
            }
        }
        for (const auto& [temp, ids] : bed_temp_to_extruder_ids) {
            if (ids.size() > 1) {
                need_wipe_tower = true;
                break;
            }
        }
    }

    // The tower of every plate, and PartPlateList::get_extruders(true): every plate's filaments.
    std::vector<detail::PlateTower> towers;
    std::set<int> extruder_ids;
    for (int plate = 0; plate < plates; ++plate) {
        towers.push_back(detail::plate_tower(
            plate_model_of(model, config, plate, plates, false), plate_config_of(config, plate_settings, plate), has_wipe_tower_position(plate_settings, plate)));
        extruder_ids.insert(towers.back().filaments.begin(), towers.back().filaments.end());
    }

    int bedid_unlocked = 0;
    for (int bedid = 0; bedid < MAX_NUM_PLATES; ++bedid) {
        if (bedid < plates && locked[std::size_t(bedid)]) {
            continue;
        }
        if (auto standing = bedid < plates ? standing_wipe_tower(towers[std::size_t(bedid)], config) : std::nullopt) {
            // wipe tower is already there
            standing->bed_idx = bedid_unlocked;
            unselected.emplace_back(std::move(*standing));
        } else if (need_wipe_tower) {
            arrangement::ArrangePolygon wipe_tower_ap = estimated_wipe_tower(model, config, plate_settings, bedid, plates, int(extruder_ids.size()));
            wipe_tower_ap.bed_idx = bedid_unlocked;
            unselected.emplace_back(std::move(wipe_tower_ap));
        }
        bedid_unlocked++;
    }
}

// ArrangeJob with the given settings: init_arrange_params(), prepare_all() over
// every plate, or prepare_partplate() for the current one when only_on_plate,
// as the menus start it, check_unprintable(), process(), and finalize() with
// PartPlateList's pre- and postprocessing of the arrange polygons. Copies on
// locked plates stay where they are; plates are added for what the others do
// not hold. Returns the number of plates afterwards; the app recycles the
// empty ones at the end (rebuild_plates_after_arrangement). The wipe towers are
// kept clear of (prepare_wipe_tower()).
int arrange_plates(
    Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    const ArrangeSettings& settings,
    const bool only_on_plate,
    std::vector<bool> locked,
    const std::vector<ModelSettings>& plate_settings
)
{
    using namespace Slic3r;
    using arrangement::ArrangePolygon;
    int plates = engine().plate_count;
    const int current = engine().plate_index;
    locked.resize(std::size_t(plates), false);
    // prepare_partplate(): "This plate is locked. Cannot auto-arrange on this plate."
    if (only_on_plate && locked[std::size_t(current)]) {
        return plates;
    }
    arrangement::ArrangeParams params = init_arrange_params(model, config, settings);
    const PlateLayout layout = plate_layout_of(config);
    int columns = plate_columns(plates);
    // The copy moved from its plate to the first one, where the arrangement happens.
    const auto on_first_plate = [&](ArrangePolygon& polygon, const int plate) {
        polygon.bed_idx = plate;
        polygon.row = plate / columns;
        polygon.col = plate % columns;
        polygon.translation(X) -= scaled<double>(layout.stride_x * polygon.col);
        polygon.translation(Y) += scaled<double>(layout.stride_y * polygon.row);
    };

    arrangement::ArrangePolygons selected;
    arrangement::ArrangePolygons unselected;
    arrangement::ArrangePolygons locked_items;
    arrangement::ArrangePolygons unprintable;
    const BoundingBoxf3 current_box = plate_box_at(config, current, plates);
    for (ModelObject* object : model.objects) {
        for (std::size_t index = 0; index < object->instances.size(); ++index) {
            ModelInstance* instance = object->instances[index];
            ArrangePolygon polygon = get_instance_arrange_poly(instance, config);
            const int plate = plate_of(*object, index, config, plates);
            bool fixed = false;
            if (!only_on_plate) {
                // preprocess_arrange_polygon(): the copies of a locked plate
                // are neither arranged nor in the way.
                if (plate >= 0 && locked[std::size_t(plate)]) {
                    on_first_plate(polygon, plate);
                    fixed = true;
                }
            } else if (plate != current && !current_box.intersects(object->instance_convex_hull_bounding_box(index))) {
                // preprocess_arrange_polygon_other_locked(): a copy off the
                // current plate is locked where it is.
                if (plate >= 0) {
                    on_first_plate(polygon, plate);
                } else {
                    polygon.bed_idx = max_plates_count;
                }
                fixed = true;
            }
            arrangement::ArrangePolygons& list = fixed ? locked_items : instance->printable ? selected : unprintable;
            polygon.itemid = int(list.size());
            list.emplace_back(std::move(polygon));
        }
    }
    if (only_on_plate) {
        // prepare_partplate(): the current plate's tower stays where it stands.
        if (!plate_settings.empty()) {
            const detail::PlateTower tower = detail::plate_tower(plate_model_of(model, config, current, plates, false),
                plate_config_of(config, plate_settings, current), has_wipe_tower_position(plate_settings, current));
            if (auto standing = standing_wipe_tower(tower, config)) {
                unselected.emplace_back(std::move(*standing));
            }
        }
    } else {
        add_wipe_towers(unselected, selected, model, config, params, plate_settings, locked, plates);
    }
    add_exclude_areas(unselected, config, only_on_plate ? current + 1 : MAX_NUM_PLATES, 0.0f);
    // check_unprintable(): nothing without area or above the build height is arranged.
    for (auto it = selected.begin(); it != selected.end();) {
        if (it->poly.area() < 0.001 || it->height > params.printable_height) {
            unprintable.push_back(*it);
            it = selected.erase(it);
        } else {
            ++it;
        }
    }

    update_arrange_params(params, &config, selected);
    update_selected_items_inflation(selected, &config, params);
    update_unselected_items_inflation(unselected, &config, params);
    update_selected_items_axis_align(selected, &config, params);
    const Points bed = get_shrink_bedpts(&config, params);
    add_exclude_areas(params.excluded_regions, config, 1, scale_(1));
    arrangement::arrange(selected, unselected, bed, params);
    std::sort(selected.begin(), selected.end(), [](const ArrangePolygon& a, const ArrangePolygon& b) { return a.itemid < b.itemid; });

    // finalize(): the plates each copy goes to, the plates added for them.
    const auto create_plate = [&]() -> int {
        if (plates >= max_plates_count) {
            return -1;
        }
        locked.push_back(false);
        ++plates;
        columns = plate_columns(plates);
        return plates - 1;
    };
    int beds = 0;
    for (ArrangePolygon& polygon : selected) {
        if (only_on_plate) {
            // postprocess_bed_index_for_current_plate()
            if (polygon.bed_idx == 0) {
                polygon.bed_idx = current;
            } else if (polygon.bed_idx != -1) {
                polygon.bed_idx = plates;
            }
        } else if (polygon.bed_idx != -1) {
            // postprocess_bed_index_for_selected(): locked plates are skipped,
            // and plates are created for the beds beyond the last one.
            bool found = false;
            for (int plate = 0; plate < plates; ++plate) {
                if (locked[std::size_t(plate)]) {
                    ++polygon.bed_idx;
                } else if (polygon.bed_idx <= plate) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                for (int plate = create_plate(); plate != -1 && polygon.bed_idx > plate; plate = create_plate()) {
                }
            }
        }
        beds = std::max(polygon.bed_idx, beds);
    }
    // postprocess_arrange_polygon(): back from the first plate to the plate's place.
    const auto on_plate = [&](ArrangePolygon& polygon, const bool arranged) {
        if (!arranged && polygon.bed_idx == max_plates_count) {
            return;
        }
        if (polygon.bed_idx == -1) {
            // Too large for a plate: beside the last one.
            polygon.bed_idx = plates;
            const BoundingBox box = get_extents(polygon.transformed_poly());
            polygon.translation(X) = 0.5 * box.size()[0];
            polygon.translation(Y) = scaled<double>(static_cast<double>(layout.depth)) - 0.5 * box.size()[1];
        }
        polygon.row = polygon.bed_idx / columns;
        polygon.col = polygon.bed_idx % columns;
        polygon.translation(X) += scaled<double>(layout.stride_x * polygon.col);
        polygon.translation(Y) -= scaled<double>(layout.stride_y * polygon.row);
    };
    for (ArrangePolygon& polygon : locked_items) {
        beds = std::max(polygon.bed_idx, beds);
        on_plate(polygon, false);
        polygon.apply();
    }
    for (ArrangePolygon& polygon : selected) {
        on_plate(polygon, true);
        polygon.apply();
    }
    // The unprintable copies go to the bed after the last one.
    for (ArrangePolygon& polygon : unprintable) {
        polygon.bed_idx = beds + 1;
        on_plate(polygon, true);
        polygon.apply();
    }
    for (ModelObject* object : model.objects) {
        object->invalidate_bounding_box();
    }
    return plates;
}

// Plater::get_empty_cells(): the centres of the cells of step the plate is
// divided into, from its front left corner, without the cells that meet an
// excluded area of the bed. Objects on the plate do not count.
std::vector<Slic3r::Vec2f> plate_cells(const Slic3r::DynamicPrintConfig& config, const Slic3r::Vec2f& step)
{
    using namespace Slic3r;
    const BoundingBoxf3 build_volume = plate_box_of(config);
    const BoundingBoxf bbox(Vec2d(build_volume.min.x(), build_volume.min.y()), Vec2d(build_volume.max.x(), build_volume.max.y()));
    // PartPlate::get_exclude_areas(): a box per four points of the excluded area.
    std::vector<BoundingBoxf> exclude_boxs;
    const Pointfs& excluded = config.option<ConfigOptionPoints>("bed_exclude_area")->values;
    for (size_t start = 0; start + 4 <= excluded.size(); start += 4) {
        BoundingBoxf box;
        for (size_t point = start; point < start + 4; ++point) {
            box.merge(excluded[point]);
        }
        exclude_boxs.push_back(box);
    }
    std::vector<Vec2f> cells;
    const float min_x = step(0) / 2;
    const float min_y = step(1) / 2;
    for (float x = min_x + bbox.min.x(); x < bbox.max.x() - step(0) / 2; x += step(0)) {
        for (float y = min_y + bbox.min.y(); y < bbox.max.y() - step(1) / 2; y += step(1)) {
            const BoundingBoxf cell(Vec2d(x - step(0) / 2, y - step(1) / 2), Vec2d(x + step(0) / 2, y + step(1) / 2));
            if (std::any_of(exclude_boxs.begin(), exclude_boxs.end(), [&cell](const BoundingBoxf& box) { return box.overlap(cell); })) {
                continue;
            }
            cells.emplace_back(x, y);
        }
    }
    return cells;
}

// FillBedJob(true) on the current plate: prepare(), process() and
// finalize(), then ArrangeJob from the menu as finalize() starts it. Copies of
// the object at object_index are added while the free area of the plate holds
// more, as many as the arrangement packs onto the plate.
void fill_bed_with_instances(
    Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    const ArrangeSettings& settings,
    std::size_t object_index,
    int selected_instance,
    const std::vector<bool>& locked_plates,
    const std::vector<ModelSettings>& plate_settings
)
{
    using namespace Slic3r;
    using arrangement::ArrangePolygon;
    if (object_index >= model.objects.size()) {
        return;
    }
    arrangement::ArrangeParams params = init_arrange_params(model, config, settings);
    // The selected copy, or the first when the whole object is selected.
    const int sel_id = std::max(selected_instance, 0);
    ModelObject* model_object = model.objects[object_index];
    if (model_object->instances.empty() || sel_id >= int(model_object->instances.size())) {
        return;
    }
    // PartPlate::get_bounding_box_crd() of the current plate, and bed_stride_x()
    // and bed_stride_y() of the build volume, in scaled coordinates.
    const BoundingBox plate_bb = Polygon::new_scale(plate_shape_of(config)).bounding_box();
    const int plate_cols = plate_columns(engine().plate_count);
    const int cur_plate_index = engine().plate_index;
    const Vec2d bed_size = unscaled(plate_bb.size());
    const double bed_stride_x = scaled<double>(bed_size.x()) * (1. + 1. / 5.);
    const double bed_stride_y = scaled<double>(bed_size.y()) * (1. + 1. / 5.);
    // The copies of the plate, moved to the first plate where the arrangement happens.
    const auto on_first_plate = [&](ArrangePolygon& ap) {
        ap.bed_idx = 0;
        ap.row = cur_plate_index / plate_cols;
        ap.col = cur_plate_index % plate_cols;
        ap.translation(X) -= bed_stride_x * ap.col;
        ap.translation(Y) += bed_stride_y * ap.row;
    };

    // prepare(): the printable copies of the object are the items to pack;
    // the other copies within the plate stay fixed, and the ones off it are locked.
    arrangement::ArrangePolygons selected;
    arrangement::ArrangePolygons unselected;
    for (size_t oidx = 0; oidx < model.objects.size(); ++oidx) {
        ModelObject* mo = model.objects[oidx];
        for (size_t inst_idx = 0; inst_idx < mo->instances.size(); ++inst_idx) {
            ArrangePolygon ap = get_instance_arrange_poly(mo->instances[inst_idx], config);
            const BoundingBox ap_bb = ap.transformed_poly().contour.bounding_box();
            ap.name = mo->name;
            if (oidx == object_index && mo->instances[inst_idx]->printable) {
                ++ap.priority;
                ap.itemid = int(selected.size());
                selected.emplace_back(ap);
            } else if (plate_bb.contains(ap_bb)) {
                on_first_plate(ap);
                ap.itemid = int(unselected.size());
                unselected.emplace_back(ap);
            }
        }
    }
    // get_wipe_tower_arrangepoly(): the current plate's tower, on the first bed.
    if (!plate_settings.empty()) {
        const int plates = engine().plate_count;
        const int current = engine().plate_index;
        const detail::PlateTower tower = detail::plate_tower(plate_model_of(model, config, current, plates, false),
            plate_config_of(config, plate_settings, current), has_wipe_tower_position(plate_settings, current));
        if (auto standing = standing_wipe_tower(tower, config)) {
            unselected.emplace_back(std::move(*standing));
        }
    }
    if (selected.empty()) {
        return;
    }
    add_exclude_areas(params.excluded_regions, config, 1, scale_(1));
    // PartPlateList::preprocess_exclude_areas() with its default 16 plates.
    add_exclude_areas(unselected, config, 16, 0.0f);
    const Points bedpts = get_bed_shape(config);

    const double sc = scaled<double>(1.) * scaled(1.);
    const ExPolygons polys = offset_ex(selected.front().poly, params.min_obj_distance / 2);
    const ExPolygon poly = polys.empty() ? selected.front().poly : polys.front();
    const double poly_area = poly.area() / sc;
    const double unsel_area = std::accumulate(unselected.begin(), unselected.end(), 0., [](double s, const ArrangePolygon& ap) {
        return s + (ap.bed_idx == 0) * ap.poly.area();
    }) / sc;
    const double fixed_area = unsel_area + selected.size() * poly_area;
    const double bed_area = Polygon{bedpts}.area() / sc;
    // This is the maximum number of items, the real number will always be close but less.
    const int needed_items = (bed_area - fixed_area) / poly_area;

    ModelInstance* mi = model_object->instances[sel_id];
    const ArrangePolygon template_ap = get_instance_arrange_poly(mi, config);
    // GLCanvas3D::get_size_proportional_to_max_bed_size(0.05)
    const BoundingBoxf bed = build_volume_of(config).bounding_volume2d();
    const double offset_base = 0.05 * std::max(bed.size().x(), bed.size().y());
    double offset = offset_base;
    for (int i = 0; i < needed_items; ++i, offset += offset_base) {
        ArrangePolygon ap = template_ap;
        ap.poly = selected.front().poly;
        // PartPlateList::MAX_PLATES_COUNT
        ap.bed_idx = 36;
        ap.itemid = -1;
        ap.setter = [&model, object_index, offset](const ArrangePolygon& p) {
            ModelObject* mo = model.objects[object_index];
            ModelInstance* model_instance = mo->instances.back();
            const Vec3d offset_vec = model_instance->get_offset() + Vec3d(offset, offset, 0.0);
            mo->add_instance(offset_vec, model_instance->get_scaling_factor(), model_instance->get_rotation(), model_instance->get_mirror());
            for (ModelInstance* new_instance : mo->instances) {
                new_instance->apply_arrange_result(p.translation.cast<double>(), p.rotation);
            }
        };
        selected.emplace_back(ap);
    }

    // process()
    update_arrange_params(params, &config, selected);
    const Points shrunk_bed = get_shrink_bedpts(&config, params);
    update_selected_items_inflation(selected, &config, params);
    update_unselected_items_inflation(unselected, &config, params);
    bool do_stop = false;
    params.stopcondition = [&do_stop] { return do_stop; };
    params.on_packed = [&do_stop](const ArrangePolygon& ap) { do_stop = ap.bed_idx > 0 && ap.priority == 0; };
    params.do_final_align = !engine().bundle->is_bbl_vendor();
    if (selected.size() > 100) {
        // Too many items: the grid's cells take them.
        const Vec2f step = unscaled<float>(get_extents(selected.front().poly).size()) +
                           Vec2f(selected.front().brim_width, selected.front().brim_width);
        const std::vector<Vec2f> empty_cells = plate_cells(config, step);
        const size_t n = std::min(selected.size(), empty_cells.size());
        for (size_t i = 0; i < n; i++) {
            selected[i].translation = scaled<coord_t>(empty_cells[i]);
            selected[i].bed_idx = 0;
        }
        for (size_t i = n; i < selected.size(); i++) {
            selected[i].bed_idx = -1;
        }
    } else {
        arrangement::arrange(selected, unselected, shrunk_bed, params);
    }

    // finalize(): the items packed onto the plate apply on the current plate,
    // which adds the new copies.
    const int added_cnt = std::accumulate(selected.begin(), selected.end(), 0, [](int s, const ArrangePolygon& ap) {
        return s + int(ap.priority == 0 && ap.bed_idx == 0);
    });
    if (added_cnt <= 0) {
        return;
    }
    for (ArrangePolygon& ap : selected) {
        if (ap.bed_idx != 0) {
            continue;
        }
        ap.bed_idx = cur_plate_index;
        if (selected.size() <= 100) {
            ap.row = ap.bed_idx / plate_cols;
            ap.col = ap.bed_idx % plate_cols;
            ap.translation(X) += bed_stride_x * ap.col;
            ap.translation(Y) -= bed_stride_y * ap.row;
        }
        ap.apply();
    }
    for (ModelObject* object : model.objects) {
        object->invalidate_bounding_box();
    }
    arrange_plates(model, config, settings, true, locked_plates, plate_settings);
}

// The instance's lowest point with the transformation, as instance_bounding_box().min.z().
// Selection::ensure_not_below_bed(): the top of a copy's model parts
// (GLVolume::transformed_convex_hull_bounding_box(), the modifiers and the
// other volumes that are no model part aside).
double instance_top_z(const Slic3r::ModelObject& object, const std::size_t instance)
{
    double top = -DBL_MAX;
    const Slic3r::Transform3d& placement = object.instances[instance]->get_matrix();
    for (const Slic3r::ModelVolume* volume : object.volumes) {
        if (volume->is_model_part()) {
            top = std::max(top, volume->get_convex_hull().transformed_bounding_box(placement * volume->get_matrix()).max.z());
        }
    }
    return top;
}

// Selection::translate()'s ensure_not_below_bed() in instance mode: a copy
// moved wholly below the plate rises until its top stands
// SINKING_MIN_Z_THRESHOLD above it.
void ensure_not_below_bed(Slic3r::ModelObject& object, const std::size_t instance)
{
    const double top = instance_top_z(object, instance);
    if (top < Slic3r::SINKING_MIN_Z_THRESHOLD) {
        object.translate_instance(instance, Slic3r::Vec3d(0.0, 0.0, Slic3r::SINKING_MIN_Z_THRESHOLD - top));
    }
}

double instance_min_z(Slic3r::ModelObject& object, const std::vector<double>& placement)
{
    Slic3r::Transform3d transformation = Slic3r::Transform3d::Identity();
    std::copy(placement.begin(), placement.end(), transformation.data());
    const Slic3r::Geometry::Transformation saved = object.instances.front()->get_transformation();
    object.instances.front()->set_transformation(Slic3r::Geometry::Transformation(transformation));
    object.invalidate_bounding_box();
    const double min_z = object.instance_bounding_box(0).min.z();
    object.instances.front()->set_transformation(saved);
    object.invalidate_bounding_box();
    return min_z;
}

// The end of GLCanvas3D::do_rotate() and do_scale(): an instance moves onto the
// plate unless it was sunk into it before and still is.
void rest_on_plate(Slic3r::ModelObject& object, double min_z_before)
{
    if (!object.instances.front()->auto_drop) {
        return;
    }
    const double shift_z = object.get_instance_min_z(0);
    if ((min_z_before >= Slic3r::SINKING_Z_THRESHOLD || shift_z > Slic3r::SINKING_Z_THRESHOLD) && shift_z != 0.0) {
        object.translate_instance(0, Slic3r::Vec3d(0.0, 0.0, -shift_z));
    }
}

// Selection::get_bounding_box() of a selected copy: every volume of it, its
// parts and modifiers included, as the canvas draws them.
Slic3r::BoundingBoxf3 selection_box(const Slic3r::ModelObject& object, std::size_t instance)
{
    Slic3r::BoundingBoxf3 box;
    const Slic3r::Transform3d instance_matrix = object.instances[instance]->get_matrix();
    for (const Slic3r::ModelVolume* volume : object.volumes) {
        box.merge(volume->mesh().transformed_bounding_box(instance_matrix * volume->get_matrix()));
    }
    return box;
}

// GLGizmoFlatten::update_planes()
std::vector<FlatteningPlane> flattening_planes(const Slic3r::ModelObject& mo)
{
    using namespace Slic3r;
    struct PlaneData {
        std::vector<Vec3d> vertices;
        Vec3d normal;
        float area{0.f};
    };
    std::vector<PlaneData> m_planes;

    TriangleMesh ch;
    for (const ModelVolume* vol : mo.volumes) {
        if (vol->type() != ModelVolumeType::MODEL_PART)
            continue;
        TriangleMesh vol_ch = vol->get_convex_hull();
        vol_ch.transform(vol->get_matrix());
        ch.merge(vol_ch);
    }
    ch = ch.convex_hull_3d();
    const Transform3d inst_matrix = mo.instances.front()->get_matrix_no_offset();

    // Following constants are used for discarding too small polygons.
    const float minimal_area = 5.f; // in square mm (world coordinates)
    const float minimal_side = 1.f; // mm
    const float minimal_angle = 1.f; // degree, initial value was 10, but cause bugs

    // Now we'll go through all the facets and append Points of facets sharing the same normal.
    // This part is still performed in mesh coordinate system.
    const int                num_of_facets  = ch.facets_count();
    const std::vector<Vec3f> face_normals   = its_face_normals(ch.its);
    const std::vector<Vec3i32> face_neighbors = its_face_neighbors(ch.its);
    std::vector<int>         facet_queue(num_of_facets, 0);
    std::vector<bool>        facet_visited(num_of_facets, false);
    int                      facet_queue_cnt = 0;
    const stl_normal*        normal_ptr      = nullptr;
    int                      facet_idx       = 0;
    while (1) {
        // Find next unvisited triangle:
        for (; facet_idx < num_of_facets; ++ facet_idx)
            if (!facet_visited[facet_idx]) {
                facet_queue[facet_queue_cnt ++] = facet_idx;
                facet_visited[facet_idx] = true;
                normal_ptr = &face_normals[facet_idx];
                m_planes.emplace_back();
                break;
            }
        if (facet_idx == num_of_facets)
            break; // Everything was visited already

        while (facet_queue_cnt > 0) {
            int facet_idx = facet_queue[-- facet_queue_cnt];
            const stl_normal& this_normal = face_normals[facet_idx];
            if (std::abs(this_normal(0) - (*normal_ptr)(0)) < 0.001 && std::abs(this_normal(1) - (*normal_ptr)(1)) < 0.001 && std::abs(this_normal(2) - (*normal_ptr)(2)) < 0.001) {
                const Vec3i32 face = ch.its.indices[facet_idx];
                for (int j=0; j<3; ++j)
                    m_planes.back().vertices.emplace_back(ch.its.vertices[face[j]].cast<double>());

                facet_visited[facet_idx] = true;
                for (int j = 0; j < 3; ++ j)
                    if (int neighbor_idx = face_neighbors[facet_idx][j]; neighbor_idx >= 0 && ! facet_visited[neighbor_idx])
                        facet_queue[facet_queue_cnt ++] = neighbor_idx;
            }
        }
        m_planes.back().normal = normal_ptr->cast<double>();

        Pointf3s& verts = m_planes.back().vertices;
        // Now we'll transform all the points into world coordinates, so that the areas, angles and distances
        // make real sense.
        verts = transform(verts, inst_matrix);

        // if this is a just a very small triangle, remove it to speed up further calculations (it would be rejected later anyway):
        if (verts.size() == 3 &&
            ((verts[0] - verts[1]).norm() < minimal_side
            || (verts[0] - verts[2]).norm() < minimal_side
            || (verts[1] - verts[2]).norm() < minimal_side))
            m_planes.pop_back();
    }

    // Let's prepare transformation of the normal vector from mesh to instance coordinates.
    const Matrix3d normal_matrix = inst_matrix.matrix().block(0, 0, 3, 3).inverse().transpose();

    // Now we'll go through all the polygons, transform the points into xy plane to process them:
    for (unsigned int polygon_id=0; polygon_id < m_planes.size(); ++polygon_id) {
        Pointf3s& polygon = m_planes[polygon_id].vertices;
        const Vec3d& normal = m_planes[polygon_id].normal;

        // transform the normal according to the instance matrix:
        const Vec3d normal_transformed = normal_matrix * normal;

        // We are going to rotate about z and y to flatten the plane
        Eigen::Quaterniond q;
        Transform3d m = Transform3d::Identity();
        m.matrix().block(0, 0, 3, 3) = q.setFromTwoVectors(normal_transformed, Vec3d::UnitZ()).toRotationMatrix();
        polygon = transform(polygon, m);

        // Now to remove the inner points. We'll misuse Geometry::convex_hull for that, but since
        // it works in fixed point representation, we will rescale the polygon to avoid overflows.
        // And yes, it is a nasty thing to do. Whoever has time is free to refactor.
        Vec3d bb_size = BoundingBoxf3(polygon).size();
        float sf = std::min(1./bb_size(0), 1./bb_size(1));
        Transform3d tr = Geometry::scale_transform({ sf, sf, 1.f });
        polygon = transform(polygon, tr);
        polygon = Slic3r::Geometry::convex_hull(polygon);
        polygon = transform(polygon, tr.inverse());

        // Calculate area of the polygons and discard ones that are too small
        float& area = m_planes[polygon_id].area;
        area = 0.f;
        for (unsigned int i = 0; i < polygon.size(); i++) // Shoelace formula
            area += polygon[i](0)*polygon[i + 1 < polygon.size() ? i + 1 : 0](1) - polygon[i + 1 < polygon.size() ? i + 1 : 0](0)*polygon[i](1);
        area = 0.5f * std::abs(area);

        bool discard = false;
        if (area < minimal_area)
            discard = true;
        else {
            // We also check the inner angles and discard polygons with angles smaller than the following threshold
            const double angle_threshold = ::cos(minimal_angle * (double)PI / 180.0);

            for (unsigned int i = 0; i < polygon.size(); ++i) {
                const Vec3d& prec = polygon[(i == 0) ? polygon.size() - 1 : i - 1];
                const Vec3d& curr = polygon[i];
                const Vec3d& next = polygon[(i == polygon.size() - 1) ? 0 : i + 1];

                if ((prec - curr).normalized().dot((next - curr).normalized()) > angle_threshold) {
                    discard = true;
                    break;
                }
            }
        }

        if (discard) {
            m_planes[polygon_id--] = std::move(m_planes.back());
            m_planes.pop_back();
            continue;
        }

        // We will shrink the polygon a little bit so it does not touch the object edges:
        Vec3d centroid = std::accumulate(polygon.begin(), polygon.end(), Vec3d(0.0, 0.0, 0.0));
        centroid /= (double)polygon.size();
        for (auto& vertex : polygon)
            vertex = 0.9f*vertex + 0.1f*centroid;

        // Polygon is now simple and convex, we'll round the corners to make them look nicer.
        // The algorithm takes a vertex, calculates middles of respective sides and moves the vertex
        // towards their average (controlled by 'aggressivity'). This is repeated k times.
        // In next iterations, the neighbours are not always taken at the middle (to increase the
        // rounding effect at the corners, where we need it most).
        const unsigned int k = 10; // number of iterations
        const float aggressivity = 0.2f;  // agressivity
        const unsigned int N = polygon.size();
        std::vector<std::pair<unsigned int, unsigned int>> neighbours;
        if (k != 0) {
            Pointf3s points_out(2*k*N); // vector long enough to store the future vertices
            for (unsigned int j=0; j<N; ++j) {
                points_out[j*2*k] = polygon[j];
                neighbours.push_back(std::make_pair((int)(j*2*k-k) < 0 ? (N-1)*2*k+k : j*2*k-k, j*2*k+k));
            }

            for (unsigned int i=0; i<k; ++i) {
                // Calculate middle of each edge so that neighbours points to something useful:
                for (unsigned int j=0; j<N; ++j)
                    if (i==0)
                        points_out[j*2*k+k] = 0.5f * (points_out[j*2*k] + points_out[j==N-1 ? 0 : (j+1)*2*k]);
                    else {
                        float r = 0.2+0.3/(k-1)*i; // the neighbours are not always taken in the middle
                        points_out[neighbours[j].first] = r*points_out[j*2*k] + (1-r) * points_out[neighbours[j].first-1];
                        points_out[neighbours[j].second] = r*points_out[j*2*k] + (1-r) * points_out[neighbours[j].second+1];
                    }
                // Now we have a triangle and valid neighbours, we can do an iteration:
                for (unsigned int j=0; j<N; ++j)
                    points_out[2*k*j] = (1-aggressivity) * points_out[2*k*j] +
                                        aggressivity*0.5f*(points_out[neighbours[j].first] + points_out[neighbours[j].second]);

                for (auto& n : neighbours) {
                    ++n.first;
                    --n.second;
                }
            }
            polygon = points_out; // replace the coarse polygon with the smooth one that we just created
        }


        // Raise a bit above the object surface to avoid flickering:
        for (auto& b : polygon)
            b(2) += 0.1f;

        // Transform back to 3D (and also back to mesh coordinates)
        polygon = transform(polygon, inst_matrix.inverse() * m.inverse());
    }

    // We'll sort the planes by area and only keep the 254 largest ones (because of the picking pass limitations):
    std::sort(m_planes.rbegin(), m_planes.rend(), [](const PlaneData& a, const PlaneData& b) { return a.area < b.area; });
    m_planes.resize(std::min((int)m_planes.size(), 254));

    std::vector<FlatteningPlane> result;
    result.reserve(m_planes.size());
    for (const PlaneData& plane : m_planes) {
        FlatteningPlane& out = result.emplace_back();
        out.normal = {plane.normal.x(), plane.normal.y(), plane.normal.z()};
        out.vertices.reserve(plane.vertices.size() * 3);
        for (const Vec3d& vertex : plane.vertices) {
            out.vertices.push_back(float(vertex.x()));
            out.vertices.push_back(float(vertex.y()));
            out.vertices.push_back(float(vertex.z()));
        }
    }
    return result;
}

// Loads the object, with its parts, standing at the instance transformation
// placement alone; the copy does not drop.
Slic3r::ModelObject* load_placed(
    const PlateObject& object,
    const std::vector<double>& placement,
    const Slic3r::DynamicPrintConfig& config,
    Slic3r::Model& model,
    std::string& message
)
{
    PlateObject placed = object;
    placed.instances.clear();
    ObjectPlacement& instance = placed.instances.emplace_back();
    instance.matrix = placement;
    instance.auto_drop = false;
    return load_object(placed, config, model, message);
}

FlatteningPlanes describe_flattening_planes(const PlateObject& object, const ProfileSelection& profiles, const std::vector<double>& placement)
{
    FlatteningPlanes result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    if (placement.size() != 16) {
        result.message = "The placement is not a 4 x 4 matrix";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Slic3r::Model model;
        const Slic3r::ModelObject* loaded = load_placed(object, placement, config, model, result.message);
        if (loaded == nullptr) {
            return result;
        }
        result.planes = flattening_planes(*loaded);
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

VolumeDescription describe_volume(
    const PlateObject& object,
    const ProfileSelection& profiles,
    const std::vector<double>& placement,
    std::size_t volume_index
)
{
    using namespace Slic3r;
    VolumeDescription result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    if (placement.size() != 16) {
        result.message = "The placement is not a 4 x 4 matrix";
        return result;
    }
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        const ModelObject* loaded = load_placed(object, placement, config, model, result.message);
        if (loaded == nullptr) {
            return result;
        }
        if (volume_index >= loaded->volumes.size()) {
            result.message = "The object has no such volume";
            return result;
        }
        const ModelVolume& volume = *loaded->volumes[volume_index];
        const Transform3d instance_matrix = loaded->instances.front()->get_matrix();
        // GLVolume::world_matrix()
        const Transform3d world = instance_matrix * volume.get_matrix();
        const auto [center, radius] = detail::bounding_sphere(volume, world);
        result.sphere_center = {center.x(), center.y(), center.z()};
        result.sphere_radius = radius;

        // get_bounding_box_in_reference_system() for World, Instance and Local.
        const std::vector<Vec3f>& vertices = volume.get_convex_hull().its.vertices;
        const Transform3d trafos[3] = {Transform3d::Identity(), instance_matrix, world};
        for (int type = 0; type < 3; ++type) {
            Geometry::Transformation t(trafos[type]);
            t.reset_scaling_factor();
            const Transform3d basis_trafo = t.get_matrix_no_offset();
            const Vec3d axes[3] = {basis_trafo * Vec3d::UnitX(), basis_trafo * Vec3d::UnitY(), basis_trafo * Vec3d::UnitZ()};
            Vec3d min = Vec3d::Constant(std::numeric_limits<double>::max());
            Vec3d max = Vec3d::Constant(-std::numeric_limits<double>::max());
            for (const Vec3f& v : vertices) {
                const Vec3d world_v = world * v.cast<double>();
                for (int i = 0; i < 3; ++i) {
                    const double i_comp = world_v.dot(axes[i]);
                    min(i) = std::min(min(i), i_comp);
                    max(i) = std::max(max(i), i_comp);
                }
            }
            Vec3d half_box_size = 0.5 * (max - min);
            Vec3d box_center = 0.5 * (min + max);
            // Fix for non centered volume
            // by move with calculated center(to volume center) and extend half box size
            if (type == 2) {
                const Vec3d world_zero = world * Vec3d::Zero();
                for (int i = 0; i < 3; ++i) {
                    box_center[i] = world_zero.dot(axes[i]);
                    half_box_size[i] = std::max(std::abs(box_center[i] - min[i]), std::abs(box_center[i] - max[i]));
                }
            }
            const Vec3d world_center = basis_trafo * box_center;
            for (int i = 0; i < 3; ++i) {
                result.box_sizes[type][i] = 2.0 * half_box_size[i];
                result.box_centers[type][i] = world_center[i];
            }
        }
        // Plater::show_object_info() of a volume.
        const TriangleMeshStats& stats = volume.mesh().stats();
        result.mesh_volume = stats.volume * std::abs(world.matrix().block(0, 0, 3, 3).determinant());
        result.facet_count = static_cast<std::int64_t>(volume.mesh().facets_count());
        result.open_edges = static_cast<std::int64_t>(stats.open_edges);
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

ModelInspection place_model(
    const PlateObject& plate_object,
    const ProfileSelection& profiles,
    const std::vector<double>& previous_placement,
    const std::vector<double>& placement,
    bool auto_drop,
    Manipulation manipulation,
    const std::array<double, 3>& face_normal
)
{
    ModelInspection result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    if (placement.size() != 16 || previous_placement.size() != 16) {
        result.message = "The placement is not a 4 x 4 matrix";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }

        Slic3r::Model model;
        Slic3r::ModelObject* loaded = load_placed(plate_object, placement, config, model, result.message);
        if (loaded == nullptr) {
            return result;
        }
        Slic3r::ModelObject& object = *loaded;
        object.instances.front()->auto_drop = auto_drop;
        const double min_z_before = instance_min_z(object, previous_placement);
        switch (manipulation) {
        case Manipulation::move: {
            ensure_not_below_bed(object, 0);
            const double shift_z = object.get_instance_min_z(0);
            if (auto_drop && shift_z > Slic3r::SINKING_Z_THRESHOLD && shift_z != 0.0) {
                object.translate_instance(0, Slic3r::Vec3d(0.0, 0.0, -shift_z));
            }
            break;
        }
        case Manipulation::rotate:
        case Manipulation::scale:
            rest_on_plate(object, min_z_before);
            break;
        case Manipulation::reset_rotation: {
            Slic3r::Geometry::Transformation reset = object.instances.front()->get_transformation();
            reset.reset_rotation();
            object.instances.front()->set_transformation(reset);
            object.invalidate_bounding_box();
            rest_on_plate(object, min_z_before);
            break;
        }
        case Manipulation::ensure_on_bed:
            object.ensure_on_bed();
            break;
        case Manipulation::mirror_x:
        case Manipulation::mirror_y:
        case Manipulation::mirror_z: {
            // Selection::mirror() with a relative world transformation
            // (transform_instance_relative) about Selection's dragging centre.
            const Slic3r::Vec3d mirror(manipulation == Manipulation::mirror_x ? -1.0 : 1.0,
                                       manipulation == Manipulation::mirror_y ? -1.0 : 1.0,
                                       manipulation == Manipulation::mirror_z ? -1.0 : 1.0);
            const Slic3r::Vec3d pivot = selection_box(object, 0).center();
            const Slic3r::Transform3d transform = Slic3r::Geometry::translation_transform(pivot) *
                                                  Slic3r::Geometry::scale_transform(mirror) *
                                                  Slic3r::Geometry::translation_transform(-pivot);
            Slic3r::ModelInstance& instance = *object.instances.front();
            instance.set_transformation(Slic3r::Geometry::Transformation(transform * instance.get_matrix()));
            object.invalidate_bounding_box();
            // do_mirror() snaps the copy to the plate by the rule of do_rotate().
            rest_on_plate(object, min_z_before);
            break;
        }
        case Manipulation::center: {
            // PartPlate::get_center_origin() of the plate.
            const Slic3r::Vec2d plate_center = build_volume_of(config).bounding_volume2d().center();
            const Slic3r::Vec3d box_center = selection_box(object, 0).center();
            object.translate_instance(0, Slic3r::Vec3d(plate_center.x() - box_center.x(), plate_center.y() - box_center.y(), 0.0));
            ensure_not_below_bed(object, 0);
            // do_move("Move Object")
            const double shift_z = object.get_instance_min_z(0);
            if (auto_drop && shift_z > Slic3r::SINKING_Z_THRESHOLD && shift_z != 0.0) {
                object.translate_instance(0, Slic3r::Vec3d(0.0, 0.0, -shift_z));
            }
            break;
        }
        case Manipulation::drop: {
            const double min_z = selection_box(object, 0).min.z();
            if (std::abs(min_z) >= -Slic3r::SINKING_Z_THRESHOLD) {
                object.translate_instance(0, Slic3r::Vec3d(0.0, 0.0, -min_z));
            }
            break;
        }
        case Manipulation::lay_on_face: {
            // Selection::flattening_rotate(): the face normal, taken to world coordinates, turns to point down.
            const Slic3r::Geometry::Transformation old_transformation = object.instances.front()->get_transformation();
            const Slic3r::Vec3d normal(face_normal[0], face_normal[1], face_normal[2]);
            const Slic3r::Vec3d world_normal = old_transformation.get_matrix().matrix().block(0, 0, 3, 3).inverse().transpose() * normal;
            const Slic3r::Transform3d rotation(Eigen::Quaterniond().setFromTwoVectors(world_normal, -Slic3r::Vec3d::UnitZ()));
            object.instances.front()->set_transformation(Slic3r::Geometry::Transformation(
                old_transformation.get_offset_matrix() * rotation * old_transformation.get_matrix_no_offset()));
            object.invalidate_bounding_box();
            // do_rotate("Gizmo-Place on Face") treats the object as not sunk, so it rests on the plate.
            rest_on_plate(object, Slic3r::SINKING_Z_THRESHOLD);
            break;
        }
        default:
            result.message = "Unknown manipulation";
            return result;
        }

        model.update_print_volume_state(build_volume_of(config));
        describe_placed(object, 0, result);
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

ModelInspection add_object_part(
    const PlateObject& object,
    const std::string& shape,
    const VolumeType type,
    const ProfileSelection& profiles,
    const std::string& mesh_path
)
{
    ModelInspection result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }

        Slic3r::Model model;
        if (!load_plate({object}, config, model, result.message)) {
            return result;
        }
        Slic3r::ModelObject& loaded = *model.objects.front();

        // ObjectList::load_generic_subobject(): the shape is sized from the bed
        // and placed beside the first copy of the object, on the plate.
        const Slic3r::BoundingBoxf3 instance_bb = loaded.instance_bounding_box(0);
        Slic3r::TriangleMesh mesh = create_mesh(shape, instance_bb, size_proportional_to_max_bed_size(config, 0.1));
        if (mesh.empty()) {
            result.message = "Unknown part shape " + shape;
            return result;
        }
        Slic3r::ModelVolume* volume = loaded.add_volume(std::move(mesh), volume_type_of(type));
        const Slic3r::ModelInstance& instance = *loaded.instances.front();
        const Slic3r::Transform3d no_offset = instance.get_transformation().get_matrix_no_offset();
        volume->set_transformation(Slic3r::Geometry::Transformation(no_offset.inverse()));
        const Slic3r::BoundingBoxf3 mesh_bb = volume->mesh().bounding_box();
        const Slic3r::Vec3d offset = shape == "Slab"
            // Slab: Lift to print bed
            ? Slic3r::Vec3d(0., 0., 0.5 * mesh_bb.size().z() + instance_bb.min.z() - instance.get_offset().z())
            // Translate the new modifier to be pickable: move to the left front
            // corner of the instance's bounding box, lift to print bed.
            : Slic3r::Vec3d(instance_bb.max.x(), instance_bb.min.y(), instance_bb.min.z()) + 0.5 * mesh_bb.size() - instance.get_offset();
        volume->set_offset(no_offset.inverse() * offset);

        const indexed_triangle_set& part_mesh = volume->mesh().its;
        fs::create_directories(fs::path(mesh_path).parent_path());
        if (!write_mesh(part_mesh, mesh_path)) {
            result.status = SceneStatus::write_failed;
            result.message = "Unable to write " + mesh_path;
            return result;
        }

        // The part as the app keeps it: its transformation in the object and its size.
        const Slic3r::Transform3d matrix = volume->get_matrix();
        std::copy(matrix.data(), matrix.data() + 16, result.instance_matrix.begin());
        const Slic3r::Vec3d size = volume->mesh().bounding_box().size();
        result.size_x = size.x();
        result.size_y = size.y();
        result.size_z = size.z();
        const Slic3r::Vec3d center = volume->mesh().bounding_box().center();
        result.box_center = {center.x(), center.y(), center.z()};
        result.unscaled_size = {size.x(), size.y(), size.z()};
        result.local_size = {size.x(), size.y(), size.z()};
        result.facet_count = static_cast<std::int64_t>(part_mesh.indices.size());
        result.volume_state = VolumeState::inside;
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

PlateInspection place_objects(
    const std::vector<PlateObject>& plate,
    const std::vector<bool>& selected,
    const ProfileSelection& profiles,
    PlateManipulation manipulation,
    const ArrangeSettings& arrange_settings,
    int selected_instance,
    const std::vector<bool>& locked_plates,
    const std::vector<ModelSettings>& plate_settings
)
{
    PlateInspection result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    const auto placed = [](const PlateObject& object) {
        return !object.instances.empty()
               && std::all_of(object.instances.begin(), object.instances.end(),
                              [](const ObjectPlacement& instance) { return instance.matrix.size() == 16; });
    };
    if (!std::all_of(plate.begin(), plate.end(), placed)) {
        result.message = "A placement is not a 4 x 4 matrix";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }

        Slic3r::Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        keep_models_of(plate);
        result.plate_count = engine().plate_count;
        switch (manipulation) {
        case PlateManipulation::auto_orient:
            auto_orient(model, config, selected, locked_plates);
            break;
        case PlateManipulation::arrange:
            result.plate_count = arrange_plates(model, config, arrange_settings, false, locked_plates, plate_settings);
            break;
        case PlateManipulation::update_print_volume_state:
            break;
        case PlateManipulation::arrange_plate:
            arrange_plates(model, config, arrange_settings, true, locked_plates, plate_settings);
            break;
        case PlateManipulation::fill_bed: {
            const auto object = std::find(selected.begin(), selected.end(), true);
            if (object == selected.end()) {
                result.message = "No object is selected";
                return result;
            }
            fill_bed_with_instances(model, config, arrange_settings, std::size_t(object - selected.begin()), selected_instance, locked_plates, plate_settings);
            break;
        }
        default:
            result.message = "Unknown manipulation";
            return result;
        }

        if (manipulation == PlateManipulation::arrange || manipulation == PlateManipulation::arrange_plate) {
            // ArrangeJob::finalize(), rebuild_plates_after_arrangement(): sort
            // by arrange_order (FillBedJob does not).
            std::vector<int> order(model.objects.size());
            std::iota(order.begin(), order.end(), 0);
            std::stable_sort(order.begin(), order.end(), [&model](const int a, const int b) {
                return model.objects[std::size_t(a)]->instances[0]->arrange_order < model.objects[std::size_t(b)]->instances[0]->arrange_order;
            });
            if (!std::is_sorted(order.begin(), order.end())) {
                result.object_order = std::move(order);
            }
        }

        model.update_print_volume_state(build_volume_of(config));
        for (const Slic3r::ModelObject* object : model.objects) {
            PlateObjectInspection& described = result.objects.emplace_back();
            for (std::size_t index = 0; index < object->instances.size(); ++index) {
                ModelInspection& instance = described.instances.emplace_back();
                describe_placed(*object, index, instance);
                instance.status = SceneStatus::success;
            }
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

namespace {

// The type the app names for a ModelVolumeType of Model.hpp.
VolumeType volume_type_from(const Slic3r::ModelVolumeType type)
{
    switch (type) {
    case Slic3r::ModelVolumeType::NEGATIVE_VOLUME:
        return VolumeType::negative;
    case Slic3r::ModelVolumeType::PARAMETER_MODIFIER:
        return VolumeType::modifier;
    case Slic3r::ModelVolumeType::SUPPORT_BLOCKER:
        return VolumeType::support_blocker;
    case Slic3r::ModelVolumeType::SUPPORT_ENFORCER:
        return VolumeType::support_enforcer;
    case Slic3r::ModelVolumeType::MODEL_PART:
    default:
        return VolumeType::part;
    }
}

// The keys a ModelConfig overrides, with the values a project stores.
ModelSettings settings_of(const Slic3r::ModelConfig& config)
{
    ModelSettings settings;
    for (const std::string& key : config.keys()) {
        settings.keys.push_back(key);
        settings.values.push_back(config.opt_serialize(key));
    }
    return settings;
}

std::vector<double> matrix_of(const Slic3r::Transform3d& transformation)
{
    return std::vector<double>(transformation.data(), transformation.data() + 16);
}

// The pattern_any_amf of Plater::priv::load_files().
bool is_any_amf(const std::string& path)
{
    return boost::algorithm::iends_with(path, ".amf") || boost::algorithm::iends_with(path, ".amf.xml");
}

// Plater::priv::load_files() and replace_volume_with_stl() (replacing): the
// reader of the file's type. imperial is set for an AMF file in inches, whose
// objects are then scaled whatever their size.
Slic3r::Model read_model_file(const std::string& path, detail::SettingsDialogs& dialogs, bool& imperial, const StepMeshChoice& step_mesh,
                              const bool replacing, const Slic3r::ObjImportColorFn& obj_color_fun = nullptr)
{
    imperial = false;
    if (boost::algorithm::iends_with(path, ".stp") || boost::algorithm::iends_with(path, ".step")) {
        bool utf8 = true;
        // The deflections and split of the app configuration or of
        // StepMeshDialog, which stops the load until it is answered.
        const detail::StepMeshParameters mesh = detail::step_mesh_parameters(path, step_mesh, replacing);
        Slic3r::Model model = Slic3r::Model::read_from_step(path, Slic3r::LoadStrategy::LoadModel, nullptr, [&utf8](int is_utf8) { utf8 = is_utf8 != 0; },
                                                            nullptr, mesh.linear_deflection, mesh.angle_deflection, mesh.split_compound);
        if (!utf8) {
            // The desktop app warns, and Step::load() then fails the file.
            dialogs.inform("step_not_utf8",
                           {detail::ui_text("Name of components inside STEP file is not UTF8 format!"), detail::ui_text("\n\n"),
                            detail::ui_text("The name may show garbage characters!")},
                           {detail::ui_text("Attention!")}, DialogIcon::info);
        }
        return model;
    }
    if (boost::algorithm::iends_with(path, ".3mf")) {
        // The desktop app reads a 3MF file with its plates and project
        // settings (read_from_archive), which the app does not load yet.
        throw Slic3r::RuntimeError("Loading 3MF files is not ported yet");
    }
    bool is_xxx = false;
    Slic3r::Model model = Slic3r::Model::read_from_file(path, nullptr, nullptr, Slic3r::LoadStrategy::LoadModel, nullptr, nullptr, &is_xxx,
                                                        nullptr, nullptr, nullptr, nullptr, 0, obj_color_fun);
    // is_xxx means "in inches" for an AMF file.
    imperial = is_any_amf(path) && is_xxx;
    return model;
}

// load_model_objects(): an object more than ten times the plate is scaled
// down to it when the user agrees, and one more than 10000 times whatever the
// answer. index tells the objects of one load apart in the questions.
void offer_to_scale_down(Slic3r::ModelObject& object, const Slic3r::Vec3d& bed_size, detail::SettingsDialogs& dialogs, std::size_t index)
{
    const std::vector<UiText> too_large = {detail::ui_text(
        "Your object appears to be too large, do you want to scale it down to fit the print bed automatically?")};
    for (std::size_t instance = 0; instance < object.instances.size(); ++instance) {
        const Slic3r::Vec3d ratio = object.instance_bounding_box(instance).size().cwiseQuotient(bed_size);
        const double max_ratio = std::max(ratio.x(), ratio.y());
        if (max_ratio > 10000) {
            dialogs.inform("object_too_large", too_large, {detail::ui_text("Object too large")}, DialogIcon::question);
            object.scale_mesh_after_creation(1. / max_ratio);
            object.origin_translation = Slic3r::Vec3d::Zero();
            object.center_around_origin();
            break;
        }
        if (max_ratio > 10 &&
            dialogs.ask("object_too_large:" + std::to_string(index) + ":" + std::to_string(instance), too_large,
                        {detail::ui_text("Object too large")})) {
            Slic3r::ModelInstance& placed = *object.instances[instance];
            placed.set_scaling_factor(placed.get_scaling_factor() / max_ratio);
        }
    }
}

// The objects as the app keeps them: every volume's mesh written into files
// named after output_prefix, with the transformations, settings, paint and
// placement the engine loads them with again (load_object).
bool write_objects(const std::vector<Slic3r::ModelObject*>& objects, const std::string& output_prefix, ImportedModels& result)
{
    for (std::size_t index = 0; index < objects.size(); ++index) {
        Slic3r::ModelObject& object = *objects[index];
        const std::string base = output_prefix + "-" + std::to_string(index);
        ImportedObject& out = result.objects.emplace_back();
        out.name = object.name;
        out.settings = settings_of(object.config);

        // The object's own mesh, and its transformation in the object.
        const Slic3r::ModelVolume& own = *object.volumes.front();
        out.volume_name = own.name;
        out.volume_settings = settings_of(own.config);
        out.painted = painted_facets_of(own, base + ".painted");
        out.volume_splittable = own.is_splittable();
        out.volume_from_inches = own.source.is_converted_from_inches;
        out.volume_from_meters = own.source.is_converted_from_meters;
        out.volume_input_file = own.source.input_file;
        out.volume_origin = origin_of(own.source);
        out.volume_mesh_errors = mesh_errors_of(own);
        out.origin_translation = {object.origin_translation.x(), object.origin_translation.y(), object.origin_translation.z()};
        out.volume_cut_info = detail::cut_info_from(own.cut_info);
        out.volume_emboss = detail::write_emboss(own, base + ".emboss");
        out.volume_emboss_kind = detail::emboss_kind_of(own);
        if (object.is_cut()) {
            out.cut_id.id = object.cut_id.id().id;
            out.cut_id.check_sum = object.cut_id.check_sum();
            out.cut_id.connectors_cnt = object.cut_id.connectors_cnt();
        }
        out.model_path = base + "-source.mesh";
        out.matrix = matrix_of(own.get_matrix());
        fs::create_directories(fs::path(out.model_path).parent_path());
        if (!write_mesh(own.mesh().its, out.model_path)) {
            result.status = SceneStatus::write_failed;
            result.message = "Unable to write " + out.model_path;
            return false;
        }
        // Its other volumes, which the 3D view draws on their own.
        for (std::size_t volume = 1; volume < object.volumes.size(); ++volume) {
            const Slic3r::ModelVolume& source = *object.volumes[volume];
            ImportedPart& part = out.parts.emplace_back();
            part.name = source.name;
            part.type = volume_type_from(source.type());
            part.model_path = base + "-part-" + std::to_string(volume) + ".mesh";
            part.matrix = matrix_of(source.get_matrix());
            part.settings = settings_of(source.config);
            part.painted = painted_facets_of(source, base + "-part-" + std::to_string(volume) + ".painted");
            part.splittable = source.is_splittable();
            part.from_inches = source.source.is_converted_from_inches;
            part.from_meters = source.source.is_converted_from_meters;
            part.input_file = source.source.input_file;
            part.origin = origin_of(source.source);
            part.mesh_errors = mesh_errors_of(source);
            part.cut_info = detail::cut_info_from(source.cut_info);
            part.emboss = detail::write_emboss(source, base + "-part-" + std::to_string(volume) + ".emboss");
            part.emboss_kind = detail::emboss_kind_of(source);
            if (!write_mesh(source.mesh().its, part.model_path)) {
                result.status = SceneStatus::write_failed;
                result.message = "Unable to write " + part.model_path;
                return false;
            }
        }
        for (const auto& [range, config] : object.layer_config_ranges) {
            LayerRange& layers = out.layer_ranges.emplace_back();
            layers.bottom = range.first;
            layers.top = range.second;
            layers.settings = settings_of(config);
        }
        out.layer_height_profile = object.layer_height_profile.get();
        for (const Slic3r::BrimPoint& point : object.brim_points) {
            out.brim_points.insert(out.brim_points.end(), {point.pos.x(), point.pos.y(), point.pos.z(), point.head_front_radius});
        }
        // The own mesh in object coordinates for the 3D view.
        Slic3r::TriangleMesh shown = own.mesh();
        shown.transform(own.get_matrix(), true);
        out.mesh_path = base + ".mesh";
        if (!write_mesh(shown.its, out.mesh_path)) {
            result.status = SceneStatus::write_failed;
            result.message = "Unable to write " + out.mesh_path;
            return false;
        }
        for (std::size_t instance = 0; instance < object.instances.size(); ++instance) {
            ModelInspection& described = out.instances.emplace_back();
            describe_placed(object, instance, described);
            described.status = SceneStatus::success;
            out.auto_drops.push_back(object.instances[instance]->auto_drop);
            out.printables.push_back(object.instances[instance]->printable);
            Slic3r::ModelInstance& copy = *object.instances[instance];
            out.assemble_matrices.push_back(copy.is_assemble_initialized() ? matrix_of(copy.get_assemble_transformation().get_matrix()) : std::vector<double>{});
            const Slic3r::Vec3d offset_to_assembly = copy.get_offset_to_assembly();
            out.offsets_to_assembly.push_back(
                offset_to_assembly.isZero() ? std::vector<double>{} : std::vector<double>{offset_to_assembly.x(), offset_to_assembly.y(), offset_to_assembly.z()});
        }
    }
    return true;
}

// The mesh of a volume that is not a solid (FixModelByCgal.cpp).
bool is_not_3dimensional_part(const Slic3r::TriangleMesh& mesh)
{
    using namespace Slic3r;
    if (mesh.its.indices.empty())
        return true;

    indexed_triangle_set tmp = mesh.its;
    its_remove_degenerate_faces(tmp, true);
    if (tmp.indices.empty())
        return true;

    const BoundingBoxf3 bbox = mesh.bounding_box();
    const Vec3d size = bbox.size();
    const double min_dim = std::min(size.x(), std::min(size.y(), size.z()));
    const double max_dim = std::max(size.x(), std::max(size.y(), size.z()));
    if (min_dim <= EPSILON)
        return true;

    const double volume = std::abs(its_volume(mesh.its));
    const double bbox_volume = size.x() * size.y() * size.z();
    if (volume <= EPSILON)
        return true;

    const double min_relative_thickness = 1e-6;
    const double min_volume_ratio = 1e-6;
    if (min_dim / max_dim <= min_relative_thickness)
        return true;
    if (bbox_volume > 0.0 && volume / bbox_volume <= min_volume_ratio)
        return true;

    return false;
}

// fix_model_with_cgal_gui() of FixModelByCgal.cpp without its progress
// dialog: the volume at volume_idx, or every volume for -1, split into its
// shells, the shells that are not solids dropped, and every open mesh
// repaired by CGAL. Throws with the reason when a repair fails.
void fix_model_with_cgal(Slic3r::ModelObject& model_object, int volume_idx, bool keep_painting)
{
    using namespace Slic3r;
    size_t ivolume = 0;
    size_t start_volume = volume_idx == -1 ? 0 : size_t(volume_idx);
    size_t end_volume = volume_idx == -1 ? std::numeric_limits<size_t>::max() : size_t(volume_idx);

    for (ivolume = start_volume; ivolume < model_object.volumes.size(); ++ivolume) {
        if (volume_idx != -1 && ivolume > end_volume)
            break;

        ModelVolume* volume = model_object.volumes[ivolume];

        size_t parts_count = 1;
        if (volume->is_splittable())
            parts_count = volume->split(1, keep_painting);

        size_t part_end = std::min(ivolume + parts_count - 1, model_object.volumes.size() - 1);
        if (volume_idx != -1)
            end_volume = part_end;

        size_t removed_parts = 0;
        for (size_t idx = part_end + 1; idx > ivolume; --idx) {
            const size_t part_idx = idx - 1;
            const ModelVolume* part_volume = model_object.volumes[part_idx];
            if (!is_not_3dimensional_part(part_volume->mesh()))
                continue;

            model_object.delete_volume(part_idx);
            ++removed_parts;
            if (part_end > 0)
                --part_end;
            else
                part_end = 0;
            if (volume_idx != -1)
                end_volume = part_end;
        }

        if (removed_parts >= parts_count) {
            ivolume = part_end;
            continue;
        }

        for (size_t part_idx = ivolume; part_idx <= part_end && part_idx < model_object.volumes.size(); ++part_idx) {
            ModelVolume* part_volume = model_object.volumes[part_idx];
            TriangleMesh mesh = part_volume->mesh();
            if (its_num_open_edges(mesh.its) != 0) {
                // Save painting for later remap
                const std::optional<TriangleSelector::SavedPainting> saved_painting =
                    keep_painting ? part_volume->save_painting() : std::optional<TriangleSelector::SavedPainting>{};

                std::string error;
                if (!MeshBoolean::cgal::repair(mesh, nullptr, &error))
                    throw Slic3r::RuntimeError(error.empty() ? "Repair failed" : error);

                part_volume->set_mesh(std::move(mesh));
                part_volume->calculate_convex_hull();
                part_volume->invalidate_convex_hull_2d();
                part_volume->set_new_unique_id();

                // Remap paint back
                part_volume->restore_painting(saved_painting);
            }
        }

        ivolume = part_end;
    }

    model_object.invalidate_bounding_box();
}

}  // namespace

ImportedModels import_model(
    const std::string& source_path,
    const ProfileSelection& profiles,
    const std::vector<PlateObject>& plate,
    const std::string& output_prefix,
    const DialogAnswers& answers,
    const ModelLoad load,
    const bool chosen,
    const StepMeshChoice& step_mesh,
    const ObjColorChoice& obj_color
)
{
    return import_models({source_path}, profiles, plate, output_prefix, answers, load, chosen, {step_mesh}, false, {obj_color});
}

ImportedModels obj_color_preview(const std::string& path, const ObjColorChoice& choice, const std::string& output_prefix)
{
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        const auto* colours = engine().bundle->project_config.option<Slic3r::ConfigOptionStrings>("filament_colour");
        const std::string first = colours != nullptr && !colours->values.empty() ? colours->values.front() : std::string();
        Slic3r::Model model = Slic3r::Model::read_from_file(path, nullptr, nullptr, Slic3r::LoadStrategy::LoadModel, nullptr, nullptr, nullptr,
                                                            nullptr, nullptr, nullptr, nullptr, 0, detail::obj_color_function(path, first, choice));
        // generate_thumbnail() draws a model of one object.
        if (model.objects.size() != 1) {
            result.message = "The OBJ file is not one object";
            return result;
        }
        Slic3r::ModelObject* object = model.objects.front();
        // ObjColorPanel::ObjColorPanel(): the thumbnail's copy of the object.
        object->add_instance();
        if (!write_objects({object}, output_prefix, result)) {
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const detail::ObjColorPending&) {
        result.message = "The colours of the OBJ file are no longer kept";
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

ImportedModels import_models(
    const std::vector<std::string>& source_paths,
    const ProfileSelection& profiles,
    const std::vector<PlateObject>& plate,
    const std::string& output_prefix,
    const DialogAnswers& answers,
    const ModelLoad load,
    const bool chosen,
    const std::vector<StepMeshChoice>& step_meshes,
    const bool ask_multi,
    const std::vector<ObjColorChoice>& obj_colors
)
{
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    detail::SettingsDialogs dialogs(answers);
    // The file being read, whose STEP mesh StepMeshDialog may be asked for.
    std::size_t reading = 0;
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        if (source_paths.empty()) {
            result.message = "No file to load";
            return result;
        }
        // load_files(): a single file loads one by one, a 3MF file with what
        // it brings besides its objects; several files load together into
        // one model (new_model), whose objects join the plate at once.
        // Plater::add_file() opens a 3MF file of several on its own.
        const bool one_by_one = source_paths.size() == 1;
        if (!one_by_one && std::any_of(source_paths.begin(), source_paths.end(),
                                       [](const std::string& path) { return boost::algorithm::iends_with(path, ".3mf"); })) {
            result.message = "3MF files load one at a time";
            return result;
        }
        if (chosen) {
            // determine_load_type(): the answer to ProjectDropDialog.
            engine().config->set("import_project_action", load == ModelLoad::project ? "1" : "2");
            detail::save_config(engine());
        }
        detail::Archive3mf archive;
        Slic3r::Model imported;
        // A project's objects stand where it placed them, with the settings it
        // gave them; the questions about a model file are not asked.
        bool is_project_file = false;
        for (; reading < source_paths.size(); ++reading) {
            const std::string& source_path = source_paths[reading];
            // The questions about every file but the first are told apart by its place.
            dialogs.set_scope(reading == 0 ? std::string() : "@" + std::to_string(reading));
            const std::string file_name = fs::path(source_path).filename().string();
            const bool type_3mf = boost::algorithm::iends_with(source_path, ".3mf");
            bool imperial = false;
            Slic3r::Model model;
            try {
                if (type_3mf) {
                    model = detail::read_3mf(source_path, load == ModelLoad::project, config, dialogs, archive);
                    if (archive.translate_old) {
                        translate_old_plates(model, config, static_cast<int>(archive.plate_data.size()));
                    }
                } else {
                    const StepMeshChoice step_mesh = reading < step_meshes.size() ? step_meshes[reading] : StepMeshChoice{};
                    // load_files()'s obj_color_fun: ObjColorDialog for an OBJ file with colours,
                    // which takes the colours of the plate's filaments.
                    Slic3r::ObjImportColorFn obj_color_fun;
                    if (boost::algorithm::iends_with(source_path, ".obj")) {
                        const auto* colours = engine().bundle->project_config.option<Slic3r::ConfigOptionStrings>("filament_colour");
                        const std::string first = colours != nullptr && !colours->values.empty() ? colours->values.front() : std::string();
                        obj_color_fun = detail::obj_color_function(source_path, first,
                                                                   reading < obj_colors.size() ? obj_colors[reading] : ObjColorChoice{});
                    }
                    model = read_model_file(source_path, dialogs, imperial, step_mesh, false, obj_color_fun);
                    for (Slic3r::ModelObject* object : model.objects) {
                        if (object->name.empty()) {
                            object->name = file_name;
                        }
                        object->rotate(Slic3r::Geometry::deg2rad(config.opt_float("preferred_orientation")), Slic3r::Axis::Z);
                    }
                }
            } catch (const std::exception& error) {
                // A file of several that cannot be read shows its error, and the others load.
                if (one_by_one) {
                    throw;
                }
                dialogs.error("load_failed", {detail::ui_text("%1%", {error.what()})});
                continue;
            }
            const bool project = archive.load_config;

            if (!project && model.removed_objects_with_zero_volume() > 0) {
                dialogs.inform("zero_volume", {detail::ui_text("Objects with zero volume removed")},
                               {detail::ui_text("The volume of the object is zero")}, DialogIcon::info);
            }
            if (model.objects.empty() && !project) {
                if (!one_by_one) {
                    continue;
                }
                result.message = "The supplied file couldn't be read because it's empty";
                result.notices = dialogs.take_notices();
                return result;
            }
            // A model that looks like metres or inches is scaled to millimetres
            // when the user agrees; an AMF file in inches is scaled whatever its size.
            const std::vector<UiText> too_small = {detail::ui_text(
                "The object from file %s is too small, and maybe in meters or inches.\n Do you want to scale to millimeters?", {file_name})};
            if (project) {
            } else if (imperial) {
                model.convert_from_imperial_units(false);
            } else if (model.looks_like_saved_in_meters()) {
                if (dialogs.ask("model_in_meters", too_small, {detail::ui_text("Object too small")})) {
                    model.convert_from_meters(true);
                }
            } else if (model.looks_like_imperial_units()) {
                if (dialogs.ask("model_in_inches", too_small, {detail::ui_text("Object too small")})) {
                    model.convert_from_imperial_units(true);
                }
            }
            if (!project && model.looks_like_multipart_object()) {
                if (dialogs.ask("multipart_object",
                                {detail::ui_text("This file contains several objects positioned at multiple heights.\n"
                                                 "Instead of considering them as multiple objects, should \n"
                                                 "the file be loaded as a single object having multiple parts?")},
                                {detail::ui_text("Multi-part object detected")})) {
                    model.convert_multipart_object(unsigned(std::max<std::size_t>(profiles.filaments.size(), 1)));
                }
            }

            // An object of a file other than 3MF or AMF is centred around the
            // origin without its modifiers, and an object the file placed rests on
            // the plate, or keeps a project's height below it.
            for (Slic3r::ModelObject* object : model.objects) {
                if (!type_3mf && !is_any_amf(source_path)) {
                    object->center_around_origin(false);
                }
                if (!object->instances.empty()) {
                    object->ensure_on_bed(project);
                }
            }
            if (one_by_one) {
                // The objects of a 3MF file loaded without its settings gather around
                // the plate's centre, as they stood to each other.
                if (type_3mf && !project) {
                    model.center_instances_around_point(build_volume_of(config).bed_center());
                }
                imported = std::move(model);
                is_project_file = project;
            } else {
                for (const Slic3r::ModelObject* object : model.objects) {
                    imported.add_object(*object);
                }
            }
        }
        dialogs.set_scope({});

        // load_files() of several files the user picked at once: asked whether
        // their objects make one object of several parts, and whether they drop
        // onto the plate. Objects that keep their places without Auto-Drop load
        // as one object too, which split_object() then splits.
        bool auto_drop = true;
        if (!one_by_one && ask_multi && imported.objects.size() > 1) {
            const auto [single, drop] = dialogs.ask_checked(
                "multiple_files_parts", {detail::ui_text("Load these files as a single object with multiple parts?\n")},
                {detail::ui_text("Object with multiple parts was detected")}, detail::ui_text("Auto-Drop"), true, DialogIcon::question);
            auto_drop = drop;
            if (single || !auto_drop) {
                imported.convert_multipart_object(unsigned(std::max<std::size_t>(profiles.filaments.size(), 1)));
            }
            result.split_to_objects = !single && !auto_drop;
        }

        // load_model_objects(): an object the file placed keeps its instances,
        // and any other gets one.
        Slic3r::Model model;
        if (!load_plate(plate, config, model, result.message)) {
            result.notices = dialogs.take_notices();
            return result;
        }
        const Slic3r::DynamicPrintConfig& placing = detail::placing_config(archive, config);
        const Slic3r::BoundingBoxf bed = build_volume_of(placing).bounding_volume2d();
        const Slic3r::BoundingBoxf3 plate_box = plate_box_of(placing);
        // PartPlate::empty() does not see the file's objects until they are
        // all placed.
        const bool plate_was_empty = plate_empty(model, nullptr, plate_box);
        const Slic3r::Vec3d bed_size = Slic3r::to_3d(bed.size(), 1.0) - 2.0 * Slic3r::Vec3d::Ones();
        std::vector<Slic3r::ModelObject*> placed;
        std::vector<Slic3r::ModelInstance*> new_instances;
        for (const Slic3r::ModelObject* read : imported.objects) {
            Slic3r::ModelObject* object = model.add_object(*read);
            object->sort_volumes(true);
            if (object->instances.empty()) {
                object->center_around_origin();
                new_instances.push_back(object->add_instance());
            }
            offer_to_scale_down(*object, bed_size, dialogs, placed.size());
            if (auto_drop) {
                object->ensure_on_bed(is_project_file);
            } else {
                // Without Auto-Drop the copies keep their heights, lifted above the plate.
                for (Slic3r::ModelInstance* instance : object->instances) {
                    instance->auto_drop = false;
                }
                object->translate_instances(Slic3r::Vec3d(0.0, 0.0, -std::min(object->min_z(), 0.0)));
            }
            placed.push_back(object);
        }
        // load_model_objects(): every copy takes where it stands as its place in
        // the assembly view unless it has one, before the copies the file did
        // not place find theirs on the plate.
        if (!result.split_to_objects) {
            for (Slic3r::ModelObject* object : model.objects) {
                for (Slic3r::ModelInstance* instance : object->instances) {
                    if (!instance->is_assemble_initialized()) {
                        instance->set_assemble_transformation(instance->get_transformation());
                    }
                }
            }
        }
        // The objects the file did not place: on the plate's centre when the
        // plate was empty, and otherwise in the empty cell nearest to it.
        const Slic3r::Vec2d start_point = bed.center();
        for (Slic3r::ModelInstance* instance : new_instances) {
            const double z = instance->get_offset().z();
            if (plate_was_empty) {
                instance->set_offset({start_point.x(), start_point.y(), z});
            } else {
                const Slic3r::Vec2f cell = nearest_empty_cell(model, plate_box, bed, start_point.cast<float>());
                instance->set_offset({cell.x(), cell.y(), z});
            }
        }
        model.update_print_volume_state(build_volume_of(placing));

        if (!write_objects(placed, output_prefix, result)) {
            return result;
        }
        // The objects of several files tell which one each came from.
        if (!one_by_one) {
            for (std::size_t index = 0; index < placed.size() && index < result.objects.size(); ++index) {
                result.objects[index].input_file = fs::path(placed[index]->input_file).filename().string();
            }
        }
        // Every question is answered: what the file brings into the presets.
        if (one_by_one && boost::algorithm::iends_with(source_paths.front(), ".3mf")) {
            detail::apply_3mf(archive, fs::path(source_paths.front()).filename().string(), dialogs, result);
        }
        if (result.project) {
            result.project_info = output_prefix + "-project";
            detail::keep_project_info(imported, result.project_info);
        }
        // The colours kept for ObjColorDialog are not needed any more.
        release_obj_colors();
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const detail::QuestionPending& pending) {
        result.has_question = true;
        result.question = pending.dialog;
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const detail::StepMeshPending& pending) {
        result.step_mesh = true;
        result.step_linear_deflection = pending.linear_deflection;
        result.step_angle_deflection = pending.angle_deflection;
        result.step_split_compound = pending.split_compound;
        result.step_file = int(reading);
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const detail::ObjColorPending& pending) {
        result.obj_colors = true;
        result.obj_color = pending.question;
        result.obj_color_file = int(reading);
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        release_obj_colors();
        result.message = error.what();
        result.notices = dialogs.take_notices();
        return result;
    }
}

namespace {

// check_objects_after_cut() of GLGizmoCut.cpp: objects whose connectors
// came out with the same name are named in a warning.
void check_objects_after_cut(const Slic3r::ModelObjectPtrs& objects, detail::SettingsDialogs& dialogs)
{
    std::vector<std::string> err_objects_names;
    for (const Slic3r::ModelObject* object : objects) {
        std::vector<std::string> connectors_names;
        connectors_names.reserve(object->volumes.size());
        for (const Slic3r::ModelVolume* vol : object->volumes)
            if (vol->cut_info.is_connector)
                connectors_names.push_back(vol->name);
        const size_t connectors_count = connectors_names.size();
        Slic3r::sort_remove_duplicates(connectors_names);
        if (connectors_count != connectors_names.size())
            err_objects_names.push_back(object->name);
    }
    if (err_objects_names.empty())
        return;

    std::string names = err_objects_names[0];
    for (size_t i = 1; i < err_objects_names.size(); i++)
        names += ", " + err_objects_names[i];
    // The desktop app formats this message without translating it.
    UiText warning;
    warning.msgid = "Objects(%s) have duplicated connectors. Some connectors may be missing in slicing result.\n"
                    "Please report to PrusaSlicer team in which scenario this issue happened.\nThank you.";
    warning.args = {names};
    dialogs.inform("cut_duplicated_connectors", {warning});
}

// synchronize_model_after_cut() of GLGizmoCut.cpp
void synchronize_model_after_cut(Slic3r::Model& model, const Slic3r::CutObjectBase& cut_id)
{
    for (Slic3r::ModelObject* obj : model.objects)
        if (obj->is_cut() && obj->cut_id.has_same_id(cut_id) && !obj->cut_id.is_equal(cut_id))
            obj->cut_id.copy(cut_id);
}

// update_object_cut_id() of GLGizmoCut.cpp: an object whose cut keeps all of
// it remembers the cut, with how many objects came of it.
void update_object_cut_id(Slic3r::CutObjectBase& cut_id, Slic3r::ModelObjectCutAttributes attributes, const int dowels_count)
{
    // we don't save cut information, if result will not contains all parts of initial object
    if (!attributes.has(Slic3r::ModelObjectCutAttribute::KeepUpper) ||
        !attributes.has(Slic3r::ModelObjectCutAttribute::KeepLower) ||
        attributes.has(Slic3r::ModelObjectCutAttribute::InvalidateCutInfo))
        return;

    if (cut_id.id().invalid())
        cut_id.init();
    // increase check sum, if it's needed
    {
        int cut_obj_cnt = -1;
        if (attributes.has(Slic3r::ModelObjectCutAttribute::KeepUpper))    cut_obj_cnt++;
        if (attributes.has(Slic3r::ModelObjectCutAttribute::KeepLower))    cut_obj_cnt++;
        if (attributes.has(Slic3r::ModelObjectCutAttribute::CreateDowels)) cut_obj_cnt+= dowels_count;
        if (cut_obj_cnt > 0)
            cut_id.increase_check_sum(size_t(cut_obj_cnt));
    }
}

// Plater::clear_before_change_mesh(): a mesh about to change loses its custom
// supports, seams and painting, which would make no sense on it, and the
// notification says so.
void clear_before_change_mesh(Slic3r::ModelObject& mo, detail::SettingsDialogs& dialogs)
{
    // If there are custom supports/seams/mmu/fuzzy skin segmentation, remove them. Fixed mesh
    // may be different and they would make no sense.
    bool paint_removed = false;
    for (Slic3r::ModelVolume* mv : mo.volumes) {
        paint_removed |= ! mv->supported_facets.empty() || ! mv->seam_facets.empty() || ! mv->mmu_segmentation_facets.empty() || !mv->fuzzy_skin_facets.empty();
        mv->supported_facets.reset();
        mv->seam_facets.reset();
        mv->mmu_segmentation_facets.reset();
        mv->fuzzy_skin_facets.reset();
    }
    if (paint_removed) {
        // NotificationType::CustomSupportsAndSeamRemovedAfterRepair
        dialogs.inform("paint_removed", {detail::ui_text("Custom supports and color painting were removed before repairing.")}, {},
                       DialogIcon::info);
    }
}

// The decimation of GLGizmoSimplify::process() for config.
indexed_triangle_set simplified(const indexed_triangle_set& mesh, const SimplifyConfig& config)
{
    indexed_triangle_set its = mesh;
    uint32_t triangle_count = 0;
    float    max_error = std::numeric_limits<float>::max();
    if (config.use_count && config.wanted_count >= 0)
        triangle_count = static_cast<uint32_t>(config.wanted_count);
    else if (config.use_count) {
        // Configuration::fix_count_by_ratio()
        const std::size_t count = mesh.indices.size();
        if (config.decimate_ratio <= 0.f)
            triangle_count = static_cast<uint32_t>(count);
        else if (config.decimate_ratio >= 100.f)
            triangle_count = 0;
        else
            triangle_count = static_cast<uint32_t>(std::round(count * (100.f - config.decimate_ratio) / 100.f));
    }
    if (! config.use_count)
        max_error = config.max_error;
    Slic3r::its_quadric_edge_collapse(its, triangle_count, &max_error, nullptr, nullptr);
    return its;
}

// Plater::combine_mesh_fff(): the positive volumes of the object less its
// negative ones, as mcut works them out, in the coordinates of the copy at
// instance_id, or of every copy for -1. When the boolean fails, the positive
// volumes alone, and the plater's error notification says why.
Slic3r::TriangleMesh combine_mesh_fff(const Slic3r::ModelObject& mo, int instance_id, detail::SettingsDialogs& dialogs)
{
    using namespace Slic3r;
    TriangleMesh mesh;

    std::vector<csg::CSGPart> csgmesh;
    csgmesh.reserve(2 * mo.volumes.size());
    csg::model_to_csgmesh(mo, Transform3d::Identity(), std::back_inserter(csgmesh),
        csg::mpartsPositive | csg::mpartsNegative);

    std::vector<UiText> fail_msg = {detail::ui_text("Unable to perform boolean operation on model meshes. "
        "Only positive parts will be kept. You may fix the meshes and try again.")};
    if (auto fail_reason_name = csg::check_csgmesh_booleans(Range{ std::begin(csgmesh), std::end(csgmesh) }); std::get<0>(fail_reason_name) != csg::BooleanFailReason::OK) {
        std::string name = std::get<1>(fail_reason_name);
        std::map<csg::BooleanFailReason, UiText> fail_reasons = {
            {csg::BooleanFailReason::OK, detail::ui_text("%s", {"OK"})},
            {csg::BooleanFailReason::MeshEmpty, detail::ui_text("Reason: part \"%1%\" is empty.", {name})},
            {csg::BooleanFailReason::NotBoundAVolume, detail::ui_text("Reason: part \"%1%\" does not bound a volume.", {name})},
            {csg::BooleanFailReason::SelfIntersect, detail::ui_text("Reason: part \"%1%\" has self intersection.", {name})},
            {csg::BooleanFailReason::NoIntersection, detail::ui_text("Reason: \"%1%\" and another part have no intersection.", {name})} };
        fail_msg.push_back(detail::ui_text("%s", {" "}));
        fail_msg.push_back(fail_reasons[std::get<0>(fail_reason_name)]);
    }
    else {
        try {
            MeshBoolean::mcut::McutMeshPtr meshPtr = csg::perform_csgmesh_booleans_mcut(Range{ std::begin(csgmesh), std::end(csgmesh) });
            mesh = MeshBoolean::mcut::mcut_to_triangle_mesh(*meshPtr);
        }
        catch (...) {}
    }

    if (mesh.empty()) {
        // push_plater_error_notification()
        dialogs.inform("mesh_boolean_failed", fail_msg);

        for (const ModelVolume* v : mo.volumes)
            if (v->is_model_part()) {
                TriangleMesh vol_mesh(v->mesh());
                vol_mesh.transform(v->get_matrix(), true);
                mesh.merge(vol_mesh);
            }
    }

    if (instance_id == -1) {
        TriangleMesh vols_mesh(std::move(mesh));
        mesh = TriangleMesh();
        for (const ModelInstance* i : mo.instances) {
            TriangleMesh m = vols_mesh;
            m.transform(i->get_matrix(), true);
            mesh.merge(m);
        }
    }
    else if (0 <= instance_id && instance_id < int(mo.instances.size()))
        mesh.transform(mo.instances[instance_id]->get_matrix(), true);

    return mesh;
}

}  // namespace

ImportedModels edit_object(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    ObjectEdit edit,
    int volume_index,
    const ProfileSelection& profiles,
    const std::string& output_prefix,
    const DialogAnswers& answers,
    const ObjectCut& cut
)
{
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    detail::SettingsDialogs dialogs(answers);
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Slic3r::Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        if (object_index >= model.objects.size()) {
            result.message = "The object is not on the plate";
            return result;
        }
        Slic3r::ModelObject* object = model.objects[object_index];
        if (volume_index >= int(object->volumes.size())) {
            result.message = "The object has no such volume";
            return result;
        }
        // The preference "Keep painted feature after mesh change", off by default.
        const bool keep_painting = engine().config->get_bool("keep_painting");
        const Slic3r::BoundingBoxf bed = build_volume_of(config).bounding_volume2d();
        const Slic3r::Vec3d bed_size = Slic3r::to_3d(bed.size(), 1.0) - 2.0 * Slic3r::Vec3d::Ones();
        std::vector<Slic3r::ModelObject*> edited;

        switch (edit) {
        case ObjectEdit::split_to_objects: {
            Slic3r::ModelObjectPtrs new_objects;
            object->split(&new_objects, keep_painting);
            if (new_objects.size() <= 1) {
                // warning_catcher()
                dialogs.inform("split_failed", {detail::ui_text("The selected object couldn't be split.")});
                break;
            }
            const bool floating = std::any_of(new_objects.begin(), new_objects.end(), [](const Slic3r::ModelObject* split) {
                return split->get_instance_min_z(0) >= Slic3r::SINKING_MIN_Z_THRESHOLD;
            });
            bool split_auto_drop = true;
            if (object->instances[0]->auto_drop && floating &&
                dialogs.ask("split_auto_drop", {detail::ui_text("Disable Auto-Drop to preserve Z positioning?\n")},
                            {detail::ui_text("Object with floating parts was detected")})) {
                split_auto_drop = false;
            }
            // remove(obj_idx), then load_model_objects(new_objects, false, true,
            // split_auto_drop): the new objects keep the copies of the object.
            model.delete_object(object);
            for (Slic3r::ModelObject* split : new_objects) {
                offer_to_scale_down(*split, bed_size, dialogs, edited.size());
                if (!split_auto_drop) {
                    for (Slic3r::ModelInstance* instance : split->instances) {
                        instance->auto_drop = false;
                    }
                    split->translate_instances(Slic3r::Vec3d(0.0, 0.0, -std::min(split->min_z(), 0.0)));
                } else {
                    split->ensure_on_bed(false);
                }
                edited.push_back(split);
            }
            result.appended = true;
            break;
        }
        case ObjectEdit::split_to_parts: {
            // ObjectList::get_volume_by_item(): the object's only volume when the object is picked.
            if (volume_index < 0 && object->volumes.size() > 1) {
                result.message = "Pick the part to split";
                return result;
            }
            Slic3r::ModelVolume* volume = object->volumes[std::max(volume_index, 0)];
            if (!volume->is_splittable()) {
                dialogs.inform("split_single_part", {detail::ui_text("The target object contains only one part and can not be split.")},
                               {}, DialogIcon::info);
                break;
            }
            volume->split(unsigned(std::max<std::size_t>(profiles.filaments.size(), 1)), keep_painting);
            object->input_file.clear();
            edited.push_back(object);
            break;
        }
        case ObjectEdit::fix: {
            // The message of fix_through_cgal(): the one model it repaired, or why it could not.
            const std::string name = volume_index < 0 ? object->name : object->volumes[volume_index]->name;
            std::vector<UiText> summary;
            if (!keep_painting) {
                clear_before_change_mesh(*object, dialogs);
            }
            try {
                fix_model_with_cgal(*object, volume_index, keep_painting);
                object->ensure_on_bed();
                UiText repaired = detail::ui_text("Following model object has been repaired");
                repaired.msgid_plural = "Following model objects have been repaired";
                repaired.count = 1;
                summary = {repaired, detail::ui_text(":\n   - %s", {name})};
                edited.push_back(object);
            } catch (const std::exception& error) {
                UiText failed = detail::ui_text("Failed to repair following model object");
                failed.msgid_plural = "Failed to repair following model objects";
                failed.count = 1;
                summary = {failed, detail::ui_text(":\n\n   - %s: %s", {name, error.what()})};
            }
            // The CgalFinished notification.
            dialogs.inform("fix_finished", summary, {}, DialogIcon::info);
            break;
        }
        case ObjectEdit::convert_from_inches:
        case ObjectEdit::restore_to_inches:
        case ObjectEdit::convert_from_meters:
        case ObjectEdit::restore_to_meters: {
            const Slic3r::ConversionType type = edit == ObjectEdit::convert_from_inches ? Slic3r::ConversionType::CONV_FROM_INCH :
                                                edit == ObjectEdit::restore_to_inches   ? Slic3r::ConversionType::CONV_TO_INCH :
                                                edit == ObjectEdit::convert_from_meters ? Slic3r::ConversionType::CONV_FROM_METER :
                                                                                          Slic3r::ConversionType::CONV_TO_METER;
            Slic3r::ModelObjectPtrs converted;
            object->convert_units(converted, type, volume_index < 0 ? std::vector<int>() : std::vector<int>{volume_index});
            model.delete_object(object);
            // load_model_objects(objects): the converted object keeps its copies.
            // The desktop app copies the converted objects into its model and
            // leaves them behind; here a model of their own frees them.
            Slic3r::Model discarded;
            discarded.objects = converted;
            for (const Slic3r::ModelObject* added : converted) {
                Slic3r::ModelObject* joined = model.add_object(*added);
                offer_to_scale_down(*joined, bed_size, dialogs, edited.size());
                joined->ensure_on_bed(false);
                edited.push_back(joined);
            }
            result.appended = true;
            break;
        }
        case ObjectEdit::smooth_mesh: {
            // The WarningDialog of a subdivision past a million faces: No leaves the meshes.
            const auto show_warning_dlg = [&dialogs](int cur_face_count, const std::string& name, bool is_part) {
                const int limit_face_count = 1000000;
                if (cur_face_count > limit_face_count) {
                    return !dialogs.ask(
                        "smooth_mesh_faces",
                        {detail::ui_text(is_part ? "Part" : "Object"), detail::ui_text("%s", {" "}),
                         detail::ui_text("\"%s\" will exceed 1 million faces after this subdivision, which may increase slicing time. Do you want to continue?",
                                         {name})});
                }
                return false;
            };
            bool has_show_smooth_mesh_error_dlg = false;
            const auto smooth = [&](Slic3r::ModelVolume* mv) {
                bool ok;
                auto result_mesh = Slic3r::TriangleMeshDeal::smooth_triangle_mesh(mv->mesh(), ok);
                if (ok) {
                    const std::optional<Slic3r::TriangleSelector::SavedPainting> saved_painting =
                        keep_painting ? mv->save_painting() : std::optional<Slic3r::TriangleSelector::SavedPainting>{};
                    mv->set_mesh(result_mesh);
                    mv->restore_painting(saved_painting);
                    mv->calculate_convex_hull();
                    mv->invalidate_convex_hull_2d();
                    mv->set_new_unique_id();
                } else if (!has_show_smooth_mesh_error_dlg) {
                    dialogs.inform("smooth_mesh_error", {detail::ui_text("\"%s\" part's mesh contains errors. Please repair it first.", {mv->name})});
                    has_show_smooth_mesh_error_dlg = true;
                }
            };
            if (volume_index < 0) {
                auto future_face_count = static_cast<int>(object->facets_count()) * 4;
                if (show_warning_dlg(future_face_count, object->name, false)) {
                    break;
                }
                for (Slic3r::ModelVolume* mv : object->volumes) {
                    smooth(mv);
                }
            } else {
                Slic3r::ModelVolume* mv = object->volumes[volume_index];
                auto future_face_count = static_cast<int>(mv->mesh().facets_count()) * 4;
                if (show_warning_dlg(future_face_count, mv->name, true)) {
                    break;
                }
                smooth(mv);
            }
            object->invalidate_bounding_box();
            object->ensure_on_bed();
            edited.push_back(object);
            break;
        }
        case ObjectEdit::mesh_boolean: {
            std::vector<std::optional<Slic3r::TriangleSelector::SavedPainting>> saved_paintings;
            if (keep_painting) {
                // Save painting of all the positive parts
                saved_paintings.reserve(object->volumes.size());
                for (const Slic3r::ModelVolume* vol : object->volumes) {
                    if (vol && vol->mesh_ptr() && vol->is_model_part() && vol->is_any_painted()) {
                        saved_paintings.emplace_back(vol->save_painting());
                        if (saved_paintings.back()) {
                            saved_paintings.back()->mesh.transform(vol->get_matrix(), true);
                        }
                    }
                }
            }

            Slic3r::TriangleMesh mesh = combine_mesh_fff(*object, -1, dialogs);

            // add mesh to model as a new object, keep the original object's name and config
            Slic3r::ModelObject* new_object = model.add_object();
            new_object->name = object->name;
            new_object->input_file = object->input_file;
            new_object->config.assign_config(object->config);
            if (new_object->instances.empty())
                new_object->add_instance();
            Slic3r::ModelVolume* new_volume = new_object->add_volume(mesh);

            // Remap paint
            if (keep_painting) {
                for (auto& saved_painting : saved_paintings) {
                    if (saved_painting) {
                        // For each original painted volume, we need to apply to each instance
                        // because we merged all instances into one in `combine_mesh_fff`

                        // First we save the non-instance-translated mesh
                        Slic3r::TriangleMesh vols_mesh(std::move(saved_painting->mesh));

                        for (const Slic3r::ModelInstance* i : object->instances) {
                            // Then for each instance, we apply the paint at the given instance place
                            saved_painting->mesh = vols_mesh;
                            saved_painting->mesh.transform(i->get_matrix());

                            // Then paint it
                            new_volume->restore_painting(saved_painting, true);
                        }
                    }
                }
            }

            // BBS: ensure on bed but no need to ensure locate in the center around origin
            new_object->ensure_on_bed();
            new_object->center_around_origin();
            new_object->translate_instances(-new_object->origin_translation);
            new_object->origin_translation = Slic3r::Vec3d::Zero();

            // remove selected objects
            model.delete_object(object);
            edited.push_back(new_object);
            result.appended = true;
            break;
        }
        case ObjectEdit::delete_volume: {
            // ObjectList::del_subobject_from_object() of a volume; the app asks
            // about a part of a cut itself (del_from_cut_object()).
            if (volume_index < 0) {
                result.message = "The object has no such volume";
                return result;
            }
            const Slic3r::ModelVolume* volume = object->volumes[std::size_t(volume_index)];
            int solid_cnt = 0;
            for (const Slic3r::ModelVolume* vol : object->volumes) {
                if (vol->is_model_part()) {
                    ++solid_cnt;
                }
            }
            if (volume->is_model_part() && solid_cnt == 1) {
                dialogs.error("delete_last_solid_part", {detail::ui_text("Deleting the last solid part is not allowed.")});
                break;
            }
            object->delete_volume(std::size_t(volume_index));
            if (object->volumes.size() == 1) {
                Slic3r::ModelVolume* last_volume = object->volumes[0];
                if (!last_volume->config.empty()) {
                    object->config.apply(last_volume->config);
                    last_volume->config.reset();
                }
            }
            // Plater::changed_object(): an FFF printer lets the object sink.
            object->invalidate_bounding_box();
            object->ensure_on_bed(true);
            edited.push_back(object);
            break;
        }
        case ObjectEdit::cut: {
            // GLGizmoCut3D::perform_cut() with a plane; the app keeps to can_perform_cut().
            if (cut.instance < 0 || std::size_t(cut.instance) >= object->instances.size() || cut.plane.size() != 16) {
                result.message = "The object has no such copy to cut";
                return result;
            }
            Slic3r::Transform3d plane = Slic3r::Transform3d::Identity();
            std::copy(cut.plane.begin(), cut.plane.end(), plane.data());
            // get_cut_matrix(): the plane's centre from the copy's offset.
            const Slic3r::Vec3d cut_center_offset = plane.translation() - object->instances[cut.instance]->get_offset();
            const Slic3r::Transform3d cut_matrix =
                Slic3r::Geometry::translation_transform(cut_center_offset) * Slic3r::Transform3d(plane.linear());

            using Attribute = Slic3r::ModelObjectCutAttribute;
            const bool cut_with_groove = cut.dovetail;

            // A cut by the pieces a right click turned over takes the object
            // split at the plane they were made at (m_part_selection.model_object()).
            Slic3r::Model part_model;
            std::vector<Slic3r::Cut::Part> cut_parts;
            Slic3r::ModelObject* cut_mo = object;
            if (!cut_with_groove && !cut.parts.empty() && cut.parts_plane.size() == 16) {
                Slic3r::Transform3d parts_plane = Slic3r::Transform3d::Identity();
                std::copy(cut.parts_plane.begin(), cut.parts_plane.end(), parts_plane.data());
                const Slic3r::Transform3d parts_cut_matrix =
                    Slic3r::Geometry::translation_transform(parts_plane.translation() - object->instances[cut.instance]->get_offset()) *
                    Slic3r::Transform3d(parts_plane.linear());
                Slic3r::ModelObject* split = detail::split_cut_parts(part_model, *object, cut.instance, parts_cut_matrix);
                if (split->volumes.size() == cut.parts.size()) {
                    cut_mo = split;
                    for (std::size_t id = 0; id < split->volumes.size(); ++id) {
                        cut_parts.push_back({cut.parts[id] != 0, !split->volumes[id]->is_model_part()});
                    }
                }
            }
            const bool cut_by_contour = cut_mo != object;

            // perform_cut(): the connectors join the object as its volumes first.
            const bool has_connectors = !cut.connectors.empty() && !cut_with_groove;
            int dowels_count = 0;
            if (has_connectors) {
                detail::apply_cut_connectors(*cut_mo, cut, Slic3r::Transform3d(plane.linear()), dowels_count);
            }
            const Slic3r::ModelObjectCutAttributes attributes =
                Slic3r::only_if(has_connectors ? true : cut.keep_upper, Attribute::KeepUpper) |
                Slic3r::only_if(has_connectors ? true : cut.keep_lower, Attribute::KeepLower) |
                Slic3r::only_if(has_connectors ? false : cut.keep_as_parts, Attribute::KeepAsParts) |
                Slic3r::only_if(cut.place_on_cut_upper, Attribute::PlaceOnCutUpper) |
                Slic3r::only_if(cut.place_on_cut_lower, Attribute::PlaceOnCutLower) |
                Slic3r::only_if(cut.flip_upper, Attribute::FlipUpper) |
                Slic3r::only_if(cut.flip_lower, Attribute::FlipLower) |
                Slic3r::only_if(dowels_count > 0, Attribute::CreateDowels) |
                Slic3r::only_if(!has_connectors && !cut_with_groove && cut_mo->cut_id.id().invalid(), Attribute::InvalidateCutInfo) |
                Slic3r::only_if(keep_painting, Attribute::KeepPaint);
            update_object_cut_id(cut_mo->cut_id, attributes, dowels_count);

            Slic3r::Cut cutter(cut_mo, cut.instance, cut_matrix, attributes);
            const Slic3r::ModelObjectPtrs& new_objects = cut_by_contour
                ? cutter.perform_by_contour(object, cut_parts, dowels_count)
                : cut_with_groove
                ? cutter.perform_with_groove(detail::cut_groove(cut.groove), Slic3r::Transform3d(plane.linear()), cut.groove.count,
                                             float(cut.groove.gap), float(cut.radius))
                : cutter.perform_with_plane();

            // fix_non_manifold_edges: asked once, and every volume the cut left
            // open repaired when the user agrees.
            bool asked = false;
            bool repair = false;
            for (Slic3r::ModelObject* part : new_objects) {
                for (std::size_t volume = 0; volume < part->volumes.size(); ++volume) {
                    if (Slic3r::its_num_open_edges(part->volumes[volume]->mesh().its) == 0) {
                        continue;
                    }
                    if (!asked) {
                        asked = true;
                        repair = dialogs.ask(
                            "cut_repair",
                            {detail::ui_text("Non-manifold edges be caused by cut tool, do you want to fix it now?")},
                            {},
                            detail::ui_text("Yes"),
                            detail::ui_text("Cancel")
                        );
                    }
                    if (!repair) {
                        break;
                    }
                    try {
                        fix_model_with_cgal(*part, int(volume), keep_painting);
                    } catch (const std::exception&) {
                        // fix_and_update_progress() only logs what failed.
                    }
                }
            }

            check_objects_after_cut(new_objects, dialogs);

            // save cut_id to post update synchronization
            const Slic3r::CutObjectBase cut_id = cut_mo->cut_id;

            // Plater::apply_cut_object_to_model(): the object leaves the plate,
            // and the parts join the end of it (load_model_objects(new_objects, false, false)).
            model.delete_object(object);
            for (const Slic3r::ModelObject* part : new_objects) {
                Slic3r::ModelObject* joined = model.add_object(*part);
                joined->sort_volumes(true);
                offer_to_scale_down(*joined, bed_size, dialogs, edited.size());
                joined->ensure_on_bed(false);
                edited.push_back(joined);
            }
            synchronize_model_after_cut(model, cut_id);
            result.appended = true;
            break;
        }
        default:
            result.message = "Unknown edit";
            return result;
        }

        model.update_print_volume_state(build_volume_of(config));
        if (!write_objects(edited, output_prefix, result)) {
            return result;
        }
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const detail::QuestionPending& pending) {
        result.has_question = true;
        result.question = pending.dialog;
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const detail::StepMeshPending& pending) {
        result.step_mesh = true;
        result.step_linear_deflection = pending.linear_deflection;
        result.step_angle_deflection = pending.angle_deflection;
        result.step_split_compound = pending.split_compound;
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.notices = dialogs.take_notices();
        result.objects.clear();
        return result;
    }
}

namespace {

// PartPlate::intersects(): the box meets the plate, whatever its height.
bool plate_intersects(const Slic3r::DynamicPrintConfig& config, const Slic3r::BoundingBoxf3& box)
{
    using namespace Slic3r;
    const BoundingBoxf area(config.option<ConfigOptionPoints>("printable_area")->values);
    BoundingBoxf3 print_volume(Vec3d(area.min.x(), area.min.y(), 0.0), Vec3d(area.max.x(), area.max.y(), 1e3));
    print_volume.min(2) = -1e10;
    print_volume.min(0) -= BuildVolume::BedEpsilon;
    print_volume.min(1) -= BuildVolume::BedEpsilon;
    print_volume.max(0) += BuildVolume::BedEpsilon;
    print_volume.max(1) += BuildVolume::BedEpsilon;
    return print_volume.intersects(box);
}

// Selection::paste_objects_from_clipboard() once: a copy of every object of
// the clipboard joins model, in the empty cell nearest to its source, and the
// copies of several objects keep their layout; then Plater::changed_objects()
// drops a copy that is above the plate onto it.
void paste_objects(
    Slic3r::Model& model,
    const Slic3r::ModelObjectPtrs& src_objects,
    const Slic3r::DynamicPrintConfig& config,
    std::vector<Slic3r::ModelObject*>& pasted
)
{
    using namespace Slic3r;
    const BoundingBoxf bed = build_volume_of(config).bounding_volume2d();
    const BoundingBoxf3 plate_box = plate_box_of(config);
    // If multiple objects are selected, move them as a whole after copy.
    Vec2d shift_all = {0, 0};
    Vec2f empty_cell_all = {0, 0};
    if (src_objects.size() > 1) {
        BoundingBoxf3 bbox_all;
        for (const ModelObject* src_object : src_objects) {
            bbox_all.merge(src_object->instance_convex_hull_bounding_box(size_t(0)));
        }
        const Vec3d bsize = bbox_all.size();
        if (bsize.x() < bsize.y())
            shift_all = {bbox_all.size().x(), 0};
        else
            shift_all = {0, bbox_all.size().y()};
    }
    const std::size_t first = pasted.size();
    for (size_t i = 0; i < src_objects.size(); i++) {
        const ModelObject* src_object = src_objects[i];
        ModelObject* dst_object = model.add_object(*src_object);

        // Find an empty cell to put the copied object.
        const BoundingBoxf3 bbox = src_object->instance_convex_hull_bounding_box(size_t(0));
        Vec3d displacement;
        const bool in_current = plate_intersects(config, bbox);
        const Vec3d start_point = in_current ? bbox.center() : plate_box.center();
        const Vec3d start_offset = in_current ? src_object->instances.front()->get_offset() : plate_box.center();
        const Vec2f step(float(bbox.size()(0) + 1), float(bbox.size()(1) + 1));
        if (shift_all(0) != 0 || shift_all(1) != 0) {
            if (i == 0)
                empty_cell_all = nearest_empty_cell(model, plate_box, bed, {float(start_point(0)), float(start_point(1))}, step);
            const Vec3d instance_shift = src_object->instances.front()->get_offset() - src_objects[0]->instances.front()->get_offset();
            displacement = {shift_all.x() + empty_cell_all.x() + instance_shift.x(), shift_all.y() + empty_cell_all.y() + instance_shift.y(), start_offset(2)};
        } else {
            const Vec3d point_offset = start_offset - start_point;
            const Vec2f empty_cell = nearest_empty_cell(model, plate_box, bed, {float(start_point(0)), float(start_point(1))}, step);
            displacement = {empty_cell.x() + point_offset.x(), empty_cell.y() + point_offset.y(), start_offset(2)};
        }
        for (ModelInstance* inst : dst_object->instances) {
            inst->set_offset(displacement);
        }
        pasted.push_back(dst_object);
    }
    for (std::size_t index = first; index < pasted.size(); ++index) {
        if (pasted[index]->min_z() >= SINKING_Z_THRESHOLD) {
            pasted[index]->ensure_on_bed();
        }
    }
}

}  // namespace

ImportedModels copy_objects(
    const std::vector<PlateObject>& plate,
    const std::vector<PlateObject>& sources,
    int count,
    CopyPlacement placement,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    const auto placed = [](const PlateObject& object) {
        return !object.instances.empty()
               && std::all_of(object.instances.begin(), object.instances.end(),
                              [](const ObjectPlacement& instance) { return instance.matrix.size() == 16; });
    };
    if (sources.empty() || !std::all_of(sources.begin(), sources.end(), placed)) {
        result.message = "Every copied object needs a placed copy";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        // The clipboard holds the sources as the objects of a model of its own.
        Slic3r::Model clipboard;
        if (!load_plate(sources, config, clipboard, result.message)) {
            return result;
        }
        Slic3r::Model model;
        std::vector<Slic3r::ModelObject*> copies;
        switch (placement) {
        case CopyPlacement::keep:
            copies.assign(clipboard.objects.begin(), clipboard.objects.end());
            break;
        case CopyPlacement::paste:
            if (!load_plate(plate, config, model, result.message)) {
                return result;
            }
            // CloneDialog: the clipboard pasted count times, each paste beside the ones before.
            for (int round = 0; round < count; ++round) {
                paste_objects(model, clipboard.objects, config, copies);
            }
            break;
        default:
            result.message = "Unknown placement";
            return result;
        }
        model.update_print_volume_state(build_volume_of(config));
        clipboard.update_print_volume_state(build_volume_of(config));
        if (!write_objects(copies, output_prefix, result)) {
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

namespace {

// Plater::export_stl()'s mesh_to_export_fff_no_boolean(): a combined mesh with
// normals pointing outwards, of the copy instance_id of object or of all its
// copies for -1; warning takes the notification when only the positive parts
// could be merged.
Slic3r::TriangleMesh mesh_to_export_fff_no_boolean(const Slic3r::ModelObject& object, int instance_id, std::string& warning)
{
    using namespace Slic3r;
    TriangleMesh mesh;
    // Prusa export negative parts
    std::vector<csg::CSGPart> csgmesh;
    csgmesh.reserve(2 * object.volumes.size());
    csg::model_to_csgmesh(object, Transform3d::Identity(), std::back_inserter(csgmesh),
                          csg::mpartsPositive | csg::mpartsNegative | csg::mpartsDoSplits);
    auto csgrange = range(csgmesh);
    if (csg::is_all_positive(csgrange)) {
        mesh = TriangleMesh{csg::csgmesh_merge_positive_parts(csgrange)};
    } else if (std::get<2>(csg::check_csgmesh_booleans(csgrange)) == csgrange.end()) {
        try {
            auto cgalm = csg::perform_csgmesh_booleans(csgrange);
            mesh = MeshBoolean::cgal::cgal_to_triangle_mesh(*cgalm);
        } catch (...) {}
    }
    if (mesh.empty()) {
        warning = "Unable to perform boolean operation on model meshes. Only positive parts will be exported.";
        for (const ModelVolume* v : object.volumes)
            if (v->is_model_part()) {
                TriangleMesh vol_mesh(v->mesh());
                vol_mesh.transform(v->get_matrix(), true);
                mesh.merge(vol_mesh);
            }
    }
    if (instance_id == -1) {
        TriangleMesh vols_mesh(mesh);
        mesh = TriangleMesh();
        for (const ModelInstance* i : object.instances) {
            TriangleMesh m = vols_mesh;
            m.transform(i->get_matrix(), true);
            mesh.merge(m);
        }
    } else if (0 <= instance_id && instance_id < int(object.instances.size()))
        mesh.transform(object.instances[instance_id]->get_matrix(), true);
    return mesh;
}

// store_stl() or store_drc() with the app configuration's drc_bits.
bool store_mesh(const std::string& path, Slic3r::TriangleMesh& mesh, MeshFormat format)
{
    switch (format) {
    case MeshFormat::stl: return Slic3r::store_stl(path.c_str(), &mesh, true);
    case MeshFormat::drc: {
        const std::string bits = engine().config->get("drc_bits");
        const int quality = bits.empty() ? DRC_BITS_DEFAULT : std::stoi(bits);
        return Slic3r::store_drc(path.c_str(), &mesh, quality);
    }
    default: return false;
    }
}

}  // namespace

MeshExport export_object_mesh(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    MeshFormat format,
    const ProfileSelection& profiles,
    const std::string& path
)
{
    using namespace Slic3r;
    MeshExport result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        if (object_index >= model.objects.size()) {
            result.message = "The object is not on the plate";
            return result;
        }
        const ModelObject& mo = *model.objects[object_index];

        const auto mesh_to_export = [&result](const ModelObject& object, int instance_id) {
            return mesh_to_export_fff_no_boolean(object, instance_id, result.warning);
        };

        // selection.is_single_full_object() in Selection::Instance mode.
        TriangleMesh mesh = mesh_to_export(mo, mo.instances.size() > 1 ? -1 : 0);
        if (mo.instances.size() == 1) mesh.translate(-mo.origin_translation.cast<float>());

        fs::create_directories(fs::path(path).parent_path());
        if (format != MeshFormat::stl && format != MeshFormat::drc) {
            result.message = "Unknown format";
            return result;
        }
        if (!store_mesh(path, mesh, format)) {
            result.status = SceneStatus::write_failed;
            result.message = "Unable to write " + path;
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

MeshExport export_meshes(
    const std::vector<PlateObject>& plate,
    const std::vector<std::pair<std::int32_t, std::int32_t>>& copies,
    bool multi,
    MeshFormat format,
    const ProfileSelection& profiles,
    const std::string& path
)
{
    using namespace Slic3r;
    MeshExport result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    if (format != MeshFormat::stl && format != MeshFormat::drc) {
        result.message = "Unknown format";
        return result;
    }
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        if (model.objects.empty()) {
            result.message = "The plate has no objects";
            return result;
        }
        // The selection's copies, or every object of the plate whole.
        std::vector<std::pair<std::size_t, int>> exported;
        if (copies.empty()) {
            for (std::size_t object = 0; object < model.objects.size(); ++object) {
                exported.emplace_back(object, -1);
            }
        } else {
            for (const auto& [object, instance] : copies) {
                if (object < 0 || std::size_t(object) >= model.objects.size() || instance < -1
                    || instance >= int(model.objects[std::size_t(object)]->instances.size())) {
                    result.message = "The copy is not on the plate";
                    return result;
                }
                exported.emplace_back(std::size_t(object), instance);
            }
        }
        const std::string extension = format == MeshFormat::stl ? ".stl" : ".drc";
        fs::create_directories(fs::path(path).parent_path());
        TriangleMesh merged;
        for (const auto& [object, instance] : exported) {
            const ModelObject& mo = *model.objects[object];
            TriangleMesh mesh = mesh_to_export_fff_no_boolean(mo, instance, result.warning);
            if (!multi) {
                merged.merge(mesh);
                continue;
            }
            mesh.translate(-mo.origin_translation.cast<float>());
            const std::string file = path + "-" + std::to_string(result.paths.size() + 1) + extension;
            if (!store_mesh(file, mesh, format)) {
                result.status = SceneStatus::write_failed;
                result.message = "Unable to write " + file;
                return result;
            }
            result.paths.push_back(file);
            result.names.push_back(mo.name);
        }
        if (!multi && !store_mesh(path, merged, format)) {
            result.status = SceneStatus::write_failed;
            result.message = "Unable to write " + path;
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

ImportedModels edit_objects(
    const std::vector<PlateObject>& plate,
    const std::vector<std::size_t>& object_indexes,
    ObjectEdit edit,
    const ProfileSelection& profiles,
    const std::string& output_prefix,
    const DialogAnswers& answers
)
{
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    detail::SettingsDialogs dialogs(answers);
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Slic3r::Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        std::vector<Slic3r::ModelObject*> objects;
        for (const std::size_t index : object_indexes) {
            if (index >= model.objects.size()) {
                result.message = "The object is not on the plate";
                return result;
            }
            objects.push_back(model.objects[index]);
        }
        if (objects.empty()) {
            result.message = "No object is selected";
            return result;
        }
        const bool keep_painting = engine().config->get_bool("keep_painting");
        const Slic3r::BoundingBoxf bed = build_volume_of(config).bounding_volume2d();
        const Slic3r::Vec3d bed_size = Slic3r::to_3d(bed.size(), 1.0) - 2.0 * Slic3r::Vec3d::Ones();
        std::vector<Slic3r::ModelObject*> edited;

        switch (edit) {
        case ObjectEdit::fix: {
            // ObjectList::fix_through_cgal(): FIX_THROUGH_CGAL_ALWAYS repairs every object.
            std::vector<std::string> succes_models;
            std::vector<std::pair<std::string, std::string>> failed_models;
            for (Slic3r::ModelObject* object : objects) {
                if (!keep_painting) {
                    clear_before_change_mesh(*object, dialogs);
                }
                try {
                    fix_model_with_cgal(*object, -1, keep_painting);
                    object->ensure_on_bed();
                    succes_models.push_back(object->name);
                } catch (const std::exception& error) {
                    failed_models.push_back({object->name, error.what()});
                }
                edited.push_back(object);
            }
            // The CgalFinished notification.
            std::vector<UiText> summary;
            if (!succes_models.empty()) {
                UiText repaired = detail::ui_text("Following model object has been repaired");
                repaired.msgid_plural = "Following model objects have been repaired";
                repaired.count = int(succes_models.size());
                summary.push_back(repaired);
                summary.push_back(detail::ui_text("%s", {":"}));
                for (const std::string& model_name : succes_models) {
                    summary.push_back(detail::ui_text("\n   - %s", {model_name}));
                }
                summary.push_back(detail::ui_text("%s", {"\n\n"}));
            }
            if (!failed_models.empty()) {
                UiText failed = detail::ui_text("Failed to repair following model object");
                failed.msgid_plural = "Failed to repair following model objects";
                failed.count = int(failed_models.size());
                summary.push_back(failed);
                summary.push_back(detail::ui_text("%s", {":\n"}));
                for (const auto& [model_name, reason] : failed_models) {
                    summary.push_back(detail::ui_text("\n   - %s: %s", {model_name, reason}));
                }
            }
            if (summary.empty()) {
                summary.push_back(detail::ui_text("Repairing was canceled"));
            }
            dialogs.inform("fix_finished", summary, {}, DialogIcon::info);
            break;
        }
        case ObjectEdit::convert_from_inches:
        case ObjectEdit::restore_to_inches:
        case ObjectEdit::convert_from_meters:
        case ObjectEdit::restore_to_meters: {
            const Slic3r::ConversionType type = edit == ObjectEdit::convert_from_inches ? Slic3r::ConversionType::CONV_FROM_INCH :
                                                edit == ObjectEdit::restore_to_inches   ? Slic3r::ConversionType::CONV_TO_INCH :
                                                edit == ObjectEdit::convert_from_meters ? Slic3r::ConversionType::CONV_FROM_METER :
                                                                                          Slic3r::ConversionType::CONV_TO_METER;
            // Plater::convert_unit(): the objects converted from the last, then loaded in their order.
            Slic3r::ModelObjectPtrs converted;
            for (auto object = objects.rbegin(); object != objects.rend(); ++object) {
                (*object)->convert_units(converted, type, {});
                model.delete_object(*object);
            }
            std::reverse(converted.begin(), converted.end());
            Slic3r::Model discarded;
            discarded.objects = converted;
            for (const Slic3r::ModelObject* added : converted) {
                Slic3r::ModelObject* joined = model.add_object(*added);
                offer_to_scale_down(*joined, bed_size, dialogs, edited.size());
                joined->ensure_on_bed(false);
                edited.push_back(joined);
            }
            result.appended = true;
            break;
        }
        case ObjectEdit::assemble: {
            // ObjectList::merge(true) of objects selected whole: get_object_idxs()
            // first separates the copies of an object of several, which keeps its
            // first copy in its place and appends the others to the list.
            std::vector<std::pair<Slic3r::ModelObject*, Slic3r::ModelInstance*>> copies;
            for (Slic3r::ModelObject* object : objects) {
                copies.emplace_back(object, object->instances.front());
            }
            for (Slic3r::ModelObject* object : objects) {
                for (std::size_t instance = 1; instance < object->instances.size(); ++instance) {
                    copies.emplace_back(object, object->instances[instance]);
                }
            }
            Slic3r::ModelObject* new_object = model.add_object();
            new_object->name = Slic3r::I18N::translate("Assembly");
            Slic3r::ModelConfig& config_of_new = new_object->config;
            for (const auto& [object, copy] : copies) {
                const Slic3r::Geometry::Transformation& transformation = copy->get_transformation();
                if (copy == copies.front().second) {
                    new_object->add_instance();
                }
                const Slic3r::Transform3d& transformation_matrix = transformation.get_matrix();
                for (const Slic3r::ModelVolume* volume : object->volumes) {
                    Slic3r::ModelVolume* new_volume = new_object->add_volume(*volume);
                    const Slic3r::Transform3d& volume_matrix = new_volume->get_matrix();
                    new_volume->set_transformation(Slic3r::Transform3d(transformation_matrix * volume_matrix));
                    if (object->volumes.size() > 1) {
                        new_volume->config.assign_config(volume->config);
                    }
                    if (new_volume->config.option("extruder") == nullptr) {
                        if (const Slic3r::ConfigOption* opt = object->config.option("extruder")) {
                            new_volume->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(opt->getInt()));
                        }
                    }
                }
                new_object->sort_volumes(true);

                const auto new_opt_keys = config_of_new.keys();
                const Slic3r::ModelConfig& from_config = object->config;
                const auto opt_keys = from_config.keys();
                for (const auto& opt_key : opt_keys) {
                    if (std::find(new_opt_keys.begin(), new_opt_keys.end(), opt_key) == new_opt_keys.end()) {
                        const Slic3r::ConfigOption* option = from_config.option(opt_key);
                        std::unique_ptr<Slic3r::DynamicPrintConfig> defaults;
                        if (option == nullptr) {
                            defaults.reset(Slic3r::DynamicPrintConfig::new_from_defaults_keys({opt_key}));
                            option = defaults->option(opt_key);
                        }
                        config_of_new.set_key_value(opt_key, option->clone());
                    }
                }
                if (object->volumes.size() == 1 && std::find(opt_keys.begin(), opt_keys.end(), "extruder") != opt_keys.end()) {
                    if (const Slic3r::ConfigOption* option = from_config.option("extruder")) {
                        new_object->volumes.back()->config.set_key_value("extruder", option->clone());
                    }
                }
                if (!copy->printable) {
                    new_object->printable = false;
                    new_object->instances[0]->printable = false;
                }
                if (!copy->auto_drop) {
                    new_object->instances[0]->auto_drop = false;
                }
                for (const auto& range : object->layer_config_ranges) {
                    new_object->layer_config_ranges.emplace(range);
                }
                Slic3r::BrimPoints brim_points = object->brim_points;
                for (auto& point : brim_points) {
                    point.set_transform(transformation_matrix);
                    new_object->brim_points.push_back(point);
                }
            }
            new_object->ensure_on_bed();
            new_object->center_around_origin();
            new_object->translate_instances(-new_object->origin_translation);
            new_object->origin_translation = Slic3r::Vec3d::Zero();
            const Slic3r::Geometry::Transformation new_object_trsf = new_object->instances[0]->get_transformation();
            new_object->instances[0]->set_assemble_transformation(new_object_trsf);
            const Slic3r::Transform3d new_object_inverse_matrix = new_object_trsf.get_matrix().inverse();
            for (auto& point : new_object->brim_points) {
                point.set_transform(new_object_inverse_matrix);
            }
            // remove(): the objects it was made of leave.
            for (Slic3r::ModelObject* object : objects) {
                model.delete_object(object);
            }
            edited.push_back(new_object);
            result.appended = true;
            break;
        }
        default:
            result.message = "Unknown edit";
            return result;
        }

        model.update_print_volume_state(build_volume_of(config));
        if (!write_objects(edited, output_prefix, result)) {
            return result;
        }
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const detail::QuestionPending& pending) {
        result.has_question = true;
        result.question = pending.dialog;
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.notices = dialogs.take_notices();
        result.objects.clear();
        return result;
    }
}

ImportedModels replace_volume(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    const std::string& source_path,
    const ProfileSelection& profiles,
    const std::string& output_prefix,
    const StepMeshChoice& step_mesh
)
{
    using namespace Slic3r;
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    detail::SettingsDialogs dialogs({});
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        if (object_index >= model.objects.size() || volume_index >= model.objects[object_index]->volumes.size()) {
            result.message = "The plate has no such volume";
            return result;
        }

        Model new_model;
        try {
            bool imperial = false;
            new_model = read_model_file(source_path, dialogs, imperial, step_mesh, true);
            for (ModelObject* model_object : new_model.objects) {
                if (model_object->instances.empty()) model_object->add_instance();
                model_object->center_around_origin();
                model_object->ensure_on_bed();
            }
        } catch (const detail::StepMeshPending&) {
            throw;
        } catch (const std::exception&) {
            // error while loading
            result.message = "The file could not be read";
            result.notices = dialogs.take_notices();
            return result;
        }
        if (new_model.objects.empty()) {
            result.message = "The file has no model";
            return result;
        }
        if (new_model.objects.size() > 1 || new_model.objects.front()->volumes.size() > 1) {
            dialogs.inform("replace_more_than_one", {detail::ui_text("Unable to replace with more than one volume")},
                           {detail::ui_text("Error during replace")}, DialogIcon::warning);
            result.notices = dialogs.take_notices();
            result.status = SceneStatus::success;
            return result;
        }

        ModelObject* old_model_object = model.objects[object_index];
        ModelVolume* old_volume = old_model_object->volumes[volume_index];
        bool sinking = old_model_object->min_z() < SINKING_Z_THRESHOLD;

        ModelObject* new_model_object = new_model.objects.front();
        old_model_object->add_volume(*new_model_object->volumes.front());
        ModelVolume* new_volume = old_model_object->volumes.back();
        new_volume->set_new_unique_id();
        new_volume->config.apply(old_volume->config);
        new_volume->set_type(old_volume->type());
        new_volume->set_material_id(old_volume->material_id());
        new_volume->set_transformation(old_volume->get_transformation());
        new_volume->translate(new_volume->get_transformation().get_matrix_no_offset() * (new_volume->source.mesh_offset - old_volume->source.mesh_offset));
        if (old_volume->source.is_converted_from_inches)
            new_volume->convert_from_imperial_units();
        else if (old_volume->source.is_converted_from_meters)
            new_volume->convert_from_meters();
        if (engine().config->get_bool("keep_painting")) {
            // Proper paint remapping
            auto saved_painting = old_volume->save_painting();
            if (saved_painting) {
                saved_painting->mesh.transform(Geometry::translation_transform(new_volume->mesh().get_init_shift()));
                new_volume->restore_painting(saved_painting);
            }
        } else {
            // Won't work well if mesh changed, but kept for old behavior
            new_volume->supported_facets.assign(old_volume->supported_facets);
            new_volume->seam_facets.assign(old_volume->seam_facets);
            new_volume->mmu_segmentation_facets.assign(old_volume->mmu_segmentation_facets);
            new_volume->fuzzy_skin_facets.assign(old_volume->fuzzy_skin_facets);
        }
        std::swap(old_model_object->volumes[volume_index], old_model_object->volumes.back());
        old_model_object->delete_volume(old_model_object->volumes.size() - 1);
        if (!sinking)
            old_model_object->ensure_on_bed();
        old_model_object->sort_volumes(true);

        // if object has just one volume, rename object too
        if (old_model_object->volumes.size() == 1)
            old_model_object->name = old_model_object->volumes.front()->name;

        model.update_print_volume_state(build_volume_of(config));
        if (!write_objects({old_model_object}, output_prefix, result)) {
            return result;
        }
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.notices = dialogs.take_notices();
        result.objects.clear();
        return result;
    }
}

ImportedModels load_volume(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    const std::string& source_path,
    const std::string& name,
    VolumeType type,
    const ProfileSelection& profiles,
    const std::string& output_prefix,
    const StepMeshChoice& step_mesh
)
{
    using namespace Slic3r;
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    detail::SettingsDialogs dialogs({});
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        if (object_index >= model.objects.size()) {
            result.message = "The object is not on the plate";
            return result;
        }
        ModelObject& model_object = *model.objects[object_index];

        Model loaded;
        try {
            if (boost::algorithm::iends_with(source_path, ".stp") || boost::algorithm::iends_with(source_path, ".step")) {
                // The deflections and split of the app configuration or of StepMeshDialog.
                const detail::StepMeshParameters mesh = detail::step_mesh_parameters(source_path, step_mesh, false);
                loaded = Model::read_from_step(source_path, LoadStrategy::LoadModel, nullptr, nullptr, nullptr, mesh.linear_deflection,
                                               mesh.angle_deflection, mesh.split_compound);
            } else {
                loaded = Model::read_from_file(source_path, nullptr, nullptr, LoadStrategy::LoadModel);
            }
        } catch (const detail::StepMeshPending&) {
            throw;
        } catch (const std::exception&) {
            dialogs.error("load_volume_failed",
                          {detail::ui_text("Error!"), detail::ui_text(" "), detail::ui_text("Failed to get the model data in the current file.")});
            result.notices = dialogs.take_notices();
            result.status = SceneStatus::success;
            return result;
        }

        for (ModelObject* object : loaded.objects) {
            if (model_object.origin_translation != Vec3d::Zero()) {
                object->center_around_origin();
                const Vec3d delta = model_object.origin_translation - object->origin_translation;
                for (ModelVolume* volume : object->volumes) {
                    volume->translate(delta);
                }
            }
        }

        loaded.add_default_instances();
        TriangleMesh mesh = loaded.mesh();
        // Mesh will be centered when loading.
        ModelVolume* new_volume = model_object.add_volume(std::move(mesh), volume_type_of(type));
        new_volume->name = name;

        // set a default extruder value, since user can't add it manually
        int extruder_id = 0;
        if (new_volume->type() == ModelVolumeType::MODEL_PART && model_object.config.has("extruder"))
            extruder_id = model_object.config.opt_int("extruder");
        new_volume->config.set_key_value("extruder", new ConfigOptionInt(extruder_id));
        // update source data
        new_volume->source.input_file = source_path;
        new_volume->source.object_idx = int(object_index);
        new_volume->source.volume_idx = int(model_object.volumes.size()) - 1;
        if (loaded.objects.size() == 1 && loaded.objects.front()->volumes.size() == 1)
            new_volume->source.mesh_offset = loaded.objects.front()->volumes.front()->source.mesh_offset;
        new_volume->set_offset(new_volume->source.mesh_offset - model_object.volumes.front()->source.mesh_offset);

        // ObjectList::load_subobject(): reorder_volumes_and_get_selection(), and
        // Plater::changed_object() rests the object on the plate.
        model_object.sort_volumes(true);
        model_object.invalidate_bounding_box();
        model_object.ensure_on_bed(true);
        const auto added = std::find(model_object.volumes.begin(), model_object.volumes.end(), new_volume);
        result.selected_volume = added == model_object.volumes.end() ? -1 : int(added - model_object.volumes.begin());

        model.update_print_volume_state(build_volume_of(config));
        if (!write_objects({&model_object}, output_prefix, result)) {
            return result;
        }
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const detail::StepMeshPending& pending) {
        result.step_mesh = true;
        result.step_linear_deflection = pending.linear_deflection;
        result.step_angle_deflection = pending.angle_deflection;
        result.step_split_compound = pending.split_compound;
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.notices = dialogs.take_notices();
        result.objects.clear();
        return result;
    }
}

ImportedModels place_volume(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    const std::vector<double>& matrix,
    Manipulation manipulation,
    const ProfileSelection& profiles,
    const std::string& output_prefix,
    bool in_assembly
)
{
    using namespace Slic3r;
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    if (matrix.size() != 16) {
        result.message = "The transformation is not a 4 x 4 matrix";
        return result;
    }
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        if (object_index >= model.objects.size() || volume_index >= model.objects[object_index]->volumes.size()) {
            result.message = "The plate has no such volume";
            return result;
        }
        ModelObject* mo = model.objects[object_index];
        // do_rotate() and do_scale(): where each copy stood before.
        std::vector<double> min_zs;
        for (std::size_t instance = 0; instance < mo->instances.size(); ++instance) {
            min_zs.push_back(mo->instance_bounding_box(instance).min.z());
        }
        Transform3d transformation = Transform3d::Identity();
        std::copy(matrix.begin(), matrix.end(), transformation.data());
        ModelVolume* cur_mv = mo->volumes[volume_index];
        if (cur_mv->get_transformation() != Geometry::Transformation(transformation)) {
            cur_mv->set_transformation(Geometry::Transformation(transformation));
        }
        mo->invalidate_bounding_box();
        // Selection::translate()'s ensure_not_below_bed() in volume mode: the
        // moved volume rises by what the copy needs whose model parts all went
        // below the plate. Orca takes the selected copy; the engine is not told
        // which one is, and takes the copy that needs most.
        if (manipulation == Manipulation::move && !in_assembly) {
            double z_shift = 0.0;
            for (std::size_t instance = 0; instance < mo->instances.size(); ++instance) {
                z_shift = std::max(z_shift, SINKING_MIN_Z_THRESHOLD - instance_top_z(*mo, instance));
            }
            if (z_shift > 0.0) {
                Vec3d offset = cur_mv->get_offset();
                offset.z() += z_shift;
                cur_mv->set_offset(offset);
                mo->invalidate_bounding_box();
            }
        }

        // Fixes sinking/flying instances (snaps object to buildplate), but in the assembly view.
        for (std::size_t instance = 0; instance < mo->instances.size() && !in_assembly; ++instance) {
            ModelInstance* mi = mo->instances[instance];
            if (!mi->auto_drop) {
                continue;
            }
            const double shift_z = mo->get_instance_min_z(instance);
            const bool drops = manipulation == Manipulation::move ? shift_z > SINKING_Z_THRESHOLD :
                                                                     min_zs[instance] >= SINKING_Z_THRESHOLD || shift_z > SINKING_Z_THRESHOLD;
            if (drops && shift_z != 0.0) {
                mo->translate_instance(instance, Vec3d(0.0, 0.0, -shift_z));
            }
        }
        result.selected_volume = int(volume_index);

        model.update_print_volume_state(build_volume_of(config));
        if (!write_objects({mo}, output_prefix, result)) {
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

ImportedModels mesh_boolean(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    int source,
    int tool,
    MeshBooleanOperation operation,
    bool delete_input,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    using namespace Slic3r;
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    detail::SettingsDialogs dialogs({});
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        if (object_index >= model.objects.size()) {
            result.message = "The object is not on the plate";
            return result;
        }
        ModelObject* curr_model_object = model.objects[object_index];
        const int volumes = int(curr_model_object->volumes.size());
        if (source < 0 || tool < 0 || source >= volumes || tool >= volumes || source == tool) {
            result.message = "The object has no such volumes";
            return result;
        }
        ModelVolume* src = curr_model_object->volumes[source];
        ModelVolume* tool_volume = curr_model_object->volumes[tool];
        const Transform3d src_trafo = src->get_matrix();
        const Transform3d tool_trafo = tool_volume->get_matrix();

        // VolumeInfo::save_painting()
        const auto save_painting = [](const ModelVolume* mv, const Transform3d& trafo) -> std::optional<TriangleSelector::SavedPainting> {
            if (engine().config->get_bool("keep_painting")) {
                std::optional<TriangleSelector::SavedPainting> saved_painting = mv->save_painting();
                if (saved_painting) {
                    saved_painting->mesh.transform(trafo);
                }
                return saved_painting;
            }
            return {};
        };

        TriangleMesh temp_src_mesh = src->mesh();
        temp_src_mesh.transform(src_trafo);
        TriangleMesh temp_tool_mesh = tool_volume->mesh();
        temp_tool_mesh.transform(tool_trafo);
        std::vector<TriangleMesh> temp_mesh_resuls;
        std::vector<std::optional<TriangleSelector::SavedPainting>> saved_paintings;
        std::string suffix;
        switch (operation) {
        case MeshBooleanOperation::union_:
            MeshBoolean::mcut::make_boolean(temp_src_mesh, temp_tool_mesh, temp_mesh_resuls, "UNION");
            // For union, we want to keep paint from both meshes
            saved_paintings = {save_painting(src, src_trafo), save_painting(tool_volume, tool_trafo)};
            suffix = "union";
            delete_input = true;
            break;
        case MeshBooleanOperation::difference:
            MeshBoolean::mcut::make_boolean(temp_src_mesh, temp_tool_mesh, temp_mesh_resuls, "A_NOT_B");
            // For diff, we only need paint from src
            saved_paintings = {save_painting(src, src_trafo)};
            suffix = "difference";
            break;
        case MeshBooleanOperation::intersection:
            MeshBoolean::mcut::make_boolean(temp_src_mesh, temp_tool_mesh, temp_mesh_resuls, "INTERSECTION");
            // For intersection, we want to keep paint from both meshes
            saved_paintings = {save_painting(src, src_trafo), save_painting(tool_volume, tool_trafo)};
            suffix = "intersection";
            break;
        }
        if (temp_mesh_resuls.empty()) {
            // "Unable to perform boolean operation on selected parts"
            result.status = SceneStatus::success;
            return result;
        }

        // generate new volume
        ModelVolume* new_volume = curr_model_object->add_volume(std::move(temp_mesh_resuls.front()));

        // Remap paintings
        for (const auto& saved_painting : saved_paintings) {
            new_volume->restore_painting(saved_painting, true);
        }

        // assign to new_volume from old_volume
        ModelVolume* old_volume = src;
        new_volume->name = old_volume->name + " - " + suffix;
        new_volume->set_new_unique_id();
        new_volume->config.apply(old_volume->config);
        new_volume->set_type(old_volume->type());
        new_volume->set_material_id(old_volume->material_id());
        new_volume->set_offset(old_volume->get_transformation().get_offset());

        // delete old_volume
        std::swap(curr_model_object->volumes[source], curr_model_object->volumes.back());
        curr_model_object->delete_volume(curr_model_object->volumes.size() - 1);

        if (delete_input) {
            // ObjectList::del_subobject_from_object()
            ModelVolume* volume = curr_model_object->volumes[tool];
            int solid_cnt = 0;
            for (const ModelVolume* vol : curr_model_object->volumes)
                if (vol->is_model_part())
                    ++solid_cnt;
            if (volume->is_model_part() && solid_cnt == 1) {
                dialogs.error("delete_last_solid_part", {detail::ui_text("Deleting the last solid part is not allowed.")});
            } else if (curr_model_object->is_cut() && (volume->is_model_part() || volume->is_negative_volume())) {
                // del_from_cut_object() asks, and the volume stays whatever the answer.
            } else {
                // The object as the "Delete part" snapshot keeps it.
                curr_model_object->invalidate_bounding_box();
                if (!write_objects({curr_model_object}, output_prefix + "-boolean", result)) {
                    return result;
                }
                curr_model_object->delete_volume(tool);
                if (curr_model_object->volumes.size() == 1) {
                    ModelVolume* last_volume = curr_model_object->volumes[0];
                    if (!last_volume->config.empty()) {
                        curr_model_object->config.apply(last_volume->config);
                        last_volume->config.reset();
                    }
                }
            }
        }

        // ObjectList::reorder_volumes_and_get_selection()
        curr_model_object->sort_volumes(true);
        curr_model_object->invalidate_bounding_box();
        const auto added = std::find(curr_model_object->volumes.begin(), curr_model_object->volumes.end(), new_volume);
        result.selected_volume = added == curr_model_object->volumes.end() ? -1 : int(added - curr_model_object->volumes.begin());

        model.update_print_volume_state(build_volume_of(config));
        if (!write_objects({curr_model_object}, output_prefix, result)) {
            return result;
        }
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.notices = dialogs.take_notices();
        result.objects.clear();
        return result;
    }
}

ImportedModels move_volume(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    int from,
    int to,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    using namespace Slic3r;
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        if (object_index >= model.objects.size()) {
            result.message = "The object is not on the plate";
            return result;
        }
        ModelObject* object = model.objects[object_index];
        const int volumes = int(object->volumes.size());
        if (from < 0 || to < 0 || from >= volumes || to >= volumes || from == to) {
            result.message = "The object has no such volumes";
            return result;
        }
        // ObjectList::OnDrop(): the volumes swap one by one from the dragged one to the target.
        const int delta = to < from ? -1 : 1;
        for (int id = from, cnt = 0; cnt < std::abs(from - to); id += delta, ++cnt) {
            std::swap(object->volumes[std::size_t(id)], object->volumes[std::size_t(id + delta)]);
        }
        // Plater::changed_object(): an FFF printer lets the object sink.
        object->invalidate_bounding_box();
        object->ensure_on_bed(true);
        result.selected_volume = to;
        model.update_print_volume_state(build_volume_of(config));
        if (!write_objects({object}, output_prefix, result)) {
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

ImportedModels reload_volumes(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    const std::vector<int>& volume_indices,
    const std::string& source_path,
    const ProfileSelection& profiles,
    const std::string& output_prefix,
    const ObjColorChoice& obj_color
)
{
    using namespace Slic3r;
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        if (object_index >= model.objects.size()) {
            result.message = "The object is not on the plate";
            return result;
        }

        const std::string& path = source_path;
        Model new_model;
        try {
            PlateDataPtrs plate_data;
            std::vector<Preset*> project_presets;
            if (boost::iends_with(path, ".stp") || boost::iends_with(path, ".step")) {
                double linear = string_to_double_decimal_point(engine().config->get("linear_defletion"));
                double angle = string_to_double_decimal_point(engine().config->get("angle_defletion"));
                bool is_split = engine().config->get_bool("is_split_compound");
                new_model = Model::read_from_step(path, LoadStrategy::AddDefaultInstances | LoadStrategy::LoadModel, nullptr, nullptr, nullptr, linear,
                                                  angle, is_split);
            } else {
                // reload_from_disk()'s obj_color_fun: ObjColorDialog for an OBJ file with colours.
                ObjImportColorFn obj_color_fun;
                if (boost::iends_with(path, ".obj")) {
                    const auto* colours = engine().bundle->project_config.option<ConfigOptionStrings>("filament_colour");
                    const std::string first = colours != nullptr && !colours->values.empty() ? colours->values.front() : std::string();
                    obj_color_fun = detail::obj_color_function(path, first, obj_color);
                }
                new_model = Model::read_from_file(path, nullptr, nullptr, LoadStrategy::AddDefaultInstances | LoadStrategy::LoadModel, &plate_data,
                                                  &project_presets, nullptr, nullptr, nullptr, nullptr, nullptr, 0, obj_color_fun);
            }

            for (ModelObject* model_object : new_model.objects) {
                model_object->center_around_origin();
                model_object->ensure_on_bed();
            }

            release_PlateData_list(plate_data);
            for (Preset* preset : project_presets) {
                delete preset;
            }
        } catch (const std::exception&) {
            // error while loading
            result.message = "The file could not be read";
            return result;
        }

        ModelObject* old_model_object = model.objects[object_index];
        bool changed = false;
        for (const int vol_idx : volume_indices) {
            if (vol_idx < 0 || vol_idx >= int(old_model_object->volumes.size())) {
                continue;
            }
            ModelVolume* old_volume = old_model_object->volumes[vol_idx];

            bool sinking = old_model_object->min_z() < SINKING_Z_THRESHOLD;

            bool has_source = !old_volume->source.input_file.empty() &&
                              boost::algorithm::iequals(fs::path(old_volume->source.input_file).filename().string(), fs::path(path).filename().string());
            bool has_name = !old_volume->name.empty() && boost::algorithm::iequals(old_volume->name, fs::path(path).filename().string());
            if (has_source || has_name) {
                int new_volume_idx = -1;
                int new_object_idx = -1;
                bool match_found = false;
                // take idxs from the matching volume
                if (has_source && old_volume->source.object_idx < int(new_model.objects.size())) {
                    const ModelObject* obj = new_model.objects[old_volume->source.object_idx];
                    if (old_volume->source.volume_idx < int(obj->volumes.size())) {
                        const std::string& new_input_file = obj->volumes[old_volume->source.volume_idx]->source.input_file;
                        const std::string& old_input_file = old_volume->source.input_file;
                        // Orca: match on the exact source path first, then fall back to filename-only.
                        if (new_input_file == old_input_file ||
                            boost::algorithm::iequals(fs::path(new_input_file).filename().string(), fs::path(old_input_file).filename().string())) {
                            new_volume_idx = old_volume->source.volume_idx;
                            new_object_idx = old_volume->source.object_idx;
                            match_found = true;
                        }
                    }
                }

                if (!match_found && has_name) {
                    // take idxs from the 1st matching volume
                    for (size_t o = 0; o < new_model.objects.size(); ++o) {
                        ModelObject* obj = new_model.objects[o];
                        bool found = false;
                        for (size_t v = 0; v < obj->volumes.size(); ++v) {
                            if (obj->volumes[v]->name == old_volume->name) {
                                new_volume_idx = (int) v;
                                new_object_idx = (int) o;
                                found = true;
                                break;
                            }
                        }
                        if (found) break;
                        // BBS: step model,object loaded as a volume. GUI_ObfectList.cpp load_modifier()
                        if (obj->name == old_volume->name) {
                            new_object_idx = (int) o;
                            break;
                        }
                    }
                }

                if (new_object_idx < 0 || int(new_model.objects.size()) <= new_object_idx) {
                    result.failed.push_back(has_source ? old_volume->source.input_file : old_volume->name);
                    continue;
                }
                ModelObject* new_model_object = new_model.objects[new_object_idx];
                if (int(new_model_object->volumes.size()) <= new_volume_idx) {
                    result.failed.push_back(has_source ? old_volume->source.input_file : old_volume->name);
                    continue;
                }

                ModelVolume* new_volume = nullptr;
                // BBS: step model
                if (new_volume_idx < 0 && new_object_idx >= 0) {
                    TriangleMesh mesh = new_model_object->mesh();
                    new_volume = old_model_object->add_volume(std::move(mesh));
                    new_volume->name = new_model_object->name;
                    new_volume->source.input_file = new_model_object->input_file;
                } else {
                    new_volume = old_model_object->add_volume(*new_model_object->volumes[new_volume_idx]);
                }

                new_volume->set_new_unique_id();
                new_volume->config.apply(old_volume->config);
                new_volume->set_type(old_volume->type());
                new_volume->set_material_id(old_volume->material_id());

                new_volume->source.mesh_offset = old_volume->source.mesh_offset;
                new_volume->set_transformation(old_volume->get_transformation());

                new_volume->source.object_idx = old_volume->source.object_idx;
                new_volume->source.volume_idx = old_volume->source.volume_idx;
                if (old_volume->source.is_converted_from_inches)
                    new_volume->convert_from_imperial_units();
                else if (old_volume->source.is_converted_from_meters)
                    new_volume->convert_from_meters();

                // Remap paint
                if (engine().config->get_bool("keep_painting")) {
                    auto saved_painting = old_volume->save_painting();
                    if (saved_painting) {
                        saved_painting->mesh.transform(Geometry::translation_transform(new_volume->mesh().get_init_shift()));
                        new_volume->restore_painting(saved_painting);
                    }
                }

                std::swap(old_model_object->volumes[vol_idx], old_model_object->volumes.back());
                old_model_object->delete_volume(old_model_object->volumes.size() - 1);
                if (!sinking) old_model_object->ensure_on_bed();
                old_model_object->sort_volumes(engine().config->get("order_volumes") == "1");
                changed = true;
            }
        }

        if (changed) {
            model.update_print_volume_state(build_volume_of(config));
            if (!write_objects({old_model_object}, output_prefix, result)) {
                return result;
            }
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const detail::ObjColorPending& pending) {
        result.obj_colors = true;
        result.obj_color = pending.question;
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

ImportedModels set_volume_type(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    VolumeType type,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    using namespace Slic3r;
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    detail::SettingsDialogs dialogs({});
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        if (object_index >= model.objects.size() || volume_index >= model.objects[object_index]->volumes.size()) {
            result.message = "The plate has no such volume";
            return result;
        }
        ModelObject* object = model.objects[object_index];
        ModelVolume* volume = object->volumes[volume_index];
        const ModelVolumeType new_type = volume_type_of(type);
        if (volume->type() == new_type) {
            result.status = SceneStatus::success;
            return result;
        }
        if (new_type != ModelVolumeType::MODEL_PART && volume->type() == ModelVolumeType::MODEL_PART) {
            const auto parts = std::count_if(object->volumes.begin(), object->volumes.end(),
                                             [](const ModelVolume* vol) { return vol->type() == ModelVolumeType::MODEL_PART; });
            if (parts == 1) {
                // show_error()
                dialogs.inform("last_solid_part", {detail::ui_text("The type of the last solid object part is not to be changed.")}, {},
                               DialogIcon::error);
                result.notices = dialogs.take_notices();
                result.status = SceneStatus::success;
                return result;
            }
        }

        volume->set_type(new_type);
        // reorder_volumes_and_get_selection()
        object->sort_volumes(true);
        const auto placed = std::find(object->volumes.begin(), object->volumes.end(), volume);
        result.selected_volume = placed == object->volumes.end() ? -1 : int(placed - object->volumes.begin());

        model.update_print_volume_state(build_volume_of(config));
        if (!write_objects({object}, output_prefix, result)) {
            return result;
        }
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.notices = dialogs.take_notices();
        result.objects.clear();
        return result;
    }
}

SimplifiedVolume simplify_volume(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    const SimplifyConfig& config,
    const ProfileSelection& profiles,
    const std::string& path
)
{
    SimplifiedVolume result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig print_config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, print_config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Slic3r::Model model;
        if (!load_plate(plate, print_config, model, result.message)) {
            result.status = SceneStatus::model_read_failed;
            return result;
        }
        if (object_index >= model.objects.size() || volume_index >= model.objects[object_index]->volumes.size()) {
            result.status = SceneStatus::model_read_failed;
            result.message = "The plate has no such volume";
            return result;
        }
        const Slic3r::ModelVolume& volume = *model.objects[object_index]->volumes[volume_index];
        if (volume.mesh().its.indices.empty()) {
            result.status = SceneStatus::model_read_failed;
            result.message = "The volume has no triangles";
            return result;
        }
        Slic3r::TriangleMesh shown(simplified(volume.mesh().its, config));
        result.triangle_count = static_cast<std::int64_t>(shown.its.indices.size());
        result.original_count = static_cast<std::int64_t>(volume.mesh().its.indices.size());
        // The 3D view draws the object's own mesh in the object's coordinates
        // (write_objects()), a part in its own with its transformation.
        if (volume_index == 0) {
            shown.transform(volume.get_matrix(), true);
        }
        fs::create_directories(fs::path(path).parent_path());
        if (!write_mesh(shown.its, path)) {
            result.status = SceneStatus::write_failed;
            result.message = "Unable to write " + path;
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

ImportedModels apply_simplify(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    const SimplifyConfig& config,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    using namespace Slic3r;
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    detail::SettingsDialogs dialogs({});
    try {
        DynamicPrintConfig print_config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, print_config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, print_config, model, result.message)) {
            return result;
        }
        if (object_index >= model.objects.size() || volume_index >= model.objects[object_index]->volumes.size()) {
            result.message = "The plate has no such volume";
            return result;
        }
        ModelObject* object = model.objects[object_index];
        const bool keep_painting = engine().config->get_bool("keep_painting");
        if (!keep_painting) {
            clear_before_change_mesh(*object, dialogs);
        }

        ModelVolume* mv = object->volumes[volume_index];
        // Save paint
        std::optional<TriangleSelector::SavedPainting> saved_painting = keep_painting ? mv->save_painting() :
                                                                                        std::optional<TriangleSelector::SavedPainting>{};
        mv->set_mesh(TriangleMesh(simplified(mv->mesh().its, config)));
        // Remap paint
        mv->restore_painting(saved_painting);
        mv->calculate_convex_hull();
        mv->invalidate_convex_hull_2d();
        mv->set_new_unique_id();
        mv->get_object()->invalidate_bounding_box();
        mv->get_object()->ensure_on_bed();

        model.update_print_volume_state(build_volume_of(print_config));
        if (!write_objects({object}, output_prefix, result)) {
            return result;
        }
        result.notices = dialogs.take_notices();
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.notices = dialogs.take_notices();
        result.objects.clear();
        return result;
    }
}

ImportedModels add_primitive(
    const std::vector<PlateObject>& plate,
    const std::string& shape,
    const std::string& name,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    using namespace Slic3r;
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        ModelObject* new_object = detail::add_shape_object(model, shape, name, config);
        if (new_object == nullptr) {
            result.message = "Unknown shape " + shape;
            return result;
        }
        model.update_print_volume_state(build_volume_of(config));
        if (!write_objects({new_object}, output_prefix, result)) {
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

ImportedModels paste_volumes(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t instance,
    const PlateObject& source,
    const std::vector<int>& volumes,
    bool same_input_file,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    using namespace Slic3r;
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(engine().mutex);
    if (engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*engine().bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }
        Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        Model clipboard;
        if (!load_plate({source}, config, clipboard, result.message)) {
            return result;
        }
        if (object_index >= model.objects.size() || instance >= model.objects[object_index]->instances.size()) {
            result.message = "The object is not on the plate";
            return result;
        }
        ModelObject* src_object = clipboard.objects.front();
        // copy_to_clipboard() keeps the volumes in their order in the object.
        std::vector<int> taken = volumes;
        std::sort(taken.begin(), taken.end());
        taken.erase(std::unique(taken.begin(), taken.end()), taken.end());
        if (taken.empty() || taken.front() < 0 || taken.back() >= int(src_object->volumes.size()) || src_object->instances.empty()) {
            result.message = "The clipboard has no such volume";
            return result;
        }

        ModelObject* dst_object = model.objects[object_index];
        ModelInstance* dst_instance = dst_object->instances[instance];
        const BoundingBoxf3 dst_instance_bb = dst_object->instance_bounding_box(instance);
        const Transform3d src_matrix = src_object->instances[0]->get_transformation().get_matrix_no_offset();
        const Transform3d dst_matrix = dst_instance->get_transformation().get_matrix_no_offset();
        const bool from_same_object = same_input_file && src_matrix.isApprox(dst_matrix);

        // Used to keep relative position of multivolume selections when pasting from another object.
        BoundingBoxf3 total_bb;
        ModelVolumePtrs pasted;
        for (const int index : taken) {
            const ModelVolume* src_volume = src_object->volumes[index];
            ModelVolume* dst_volume = dst_object->add_volume(*src_volume);
            dst_volume->set_new_unique_id();
            if (!from_same_object) {
                // As done when adding modifiers (ObjectList::load_generic_subobject).
                total_bb.merge(dst_volume->mesh().bounding_box().transformed(src_volume->get_matrix()));
            }
            pasted.push_back(dst_volume);
        }
        // Keeps relative position of multivolume selections.
        if (!from_same_object) {
            for (ModelVolume* v : pasted) {
                v->set_offset((v->get_offset() - total_bb.center()) +
                              dst_matrix.inverse() * (Vec3d(dst_instance_bb.max(0), dst_instance_bb.min(1), dst_instance_bb.min(2)) +
                                                      0.5 * total_bb.size() - dst_instance->get_transformation().get_offset()));
            }
        }
        // ObjectList::paste_volumes_into_list(): reorder_volumes_and_get_selection().
        dst_object->sort_volumes(true);
        dst_object->invalidate_bounding_box();
        dst_object->ensure_on_bed(true);
        const auto first = std::find_first_of(dst_object->volumes.begin(), dst_object->volumes.end(), pasted.begin(), pasted.end());
        result.selected_volume = first == dst_object->volumes.end() ? -1 : int(first - dst_object->volumes.begin());

        model.update_print_volume_state(build_volume_of(config));
        if (!write_objects({dst_object}, output_prefix, result)) {
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

namespace detail {

bool write_png_rgba(const std::string& path, int width, int height, const std::vector<unsigned char>& rgba)
{
    return write_rgba_png(path, width, height, rgba);
}

// The smallest sphere around the convex hulls of the volumes, each standing at
// its matrix in the world, as Selection::get_bounding_sphere() finds it.
static std::pair<Slic3r::Vec3d, double> bounding_sphere_of(
    const std::vector<std::pair<const Slic3r::ModelVolume*, Slic3r::Transform3d>>& volumes
)
{
    using Kernel = CGAL::Simple_cartesian<float>;
    using Traits = CGAL::Min_sphere_of_points_d_traits_3<Kernel, float>;
    using MinSphere = CGAL::Min_sphere_of_spheres_d<Traits>;
    std::vector<Kernel::Point_3> points;
    for (const auto& [volume, matrix] : volumes) {
        for (const Slic3r::Vec3f& vertex : volume->get_convex_hull().its.vertices) {
            const Slic3r::Vec3d point = matrix * vertex.cast<double>();
            points.emplace_back(float(point.x()), float(point.y()), float(point.z()));
        }
    }
    MinSphere sphere(points.begin(), points.end());
    const float* center = sphere.center_cartesian_begin();
    return {Slic3r::Vec3d(center[0], center[1], center[2]), double(sphere.radius())};
}

std::pair<Slic3r::Vec3d, double> bounding_sphere(const Slic3r::ModelObject& object, const Slic3r::ModelInstance& instance)
{
    std::vector<std::pair<const Slic3r::ModelVolume*, Slic3r::Transform3d>> volumes;
    for (const Slic3r::ModelVolume* volume : object.volumes) {
        volumes.emplace_back(volume, instance.get_matrix() * volume->get_matrix());
    }
    return bounding_sphere_of(volumes);
}

std::pair<Slic3r::Vec3d, double> bounding_sphere(const Slic3r::ModelVolume& volume, const Slic3r::Transform3d& matrix)
{
    return bounding_sphere_of({{&volume, matrix}});
}

SliceStatus select_profiles(
    Slic3r::PresetBundle& bundle,
    const ProfileSelection& profiles,
    Slic3r::DynamicPrintConfig& config,
    std::string& message,
    const bool apply_extruder
)
{
    return orcinus::orca::select_profiles(bundle, profiles, config, message, apply_extruder);
}

bool load_plate(
    const std::vector<PlateObject>& plate,
    const Slic3r::DynamicPrintConfig& config,
    Slic3r::Model& model,
    std::string& message
)
{
    return orcinus::orca::load_plate(plate, config, model, message);
}

bool write_mesh(const indexed_triangle_set& its, const std::string& path)
{
    return orcinus::orca::write_mesh(its, path);
}

int plate_of(const Slic3r::ModelObject& object, const std::size_t instance, const Slic3r::DynamicPrintConfig& config, const int count)
{
    return orcinus::orca::plate_of(object, instance, config, count);
}

Slic3r::Vec2d bed_center(const Slic3r::DynamicPrintConfig& config)
{
    return build_volume_of(config).bed_center();
}

bool write_objects(const std::vector<Slic3r::ModelObject*>& objects, const std::string& output_prefix, ImportedModels& result)
{
    return orcinus::orca::write_objects(objects, output_prefix, result);
}

Slic3r::Vec2d plate_origin(const Slic3r::DynamicPrintConfig& config, const int index, const int count)
{
    return plate_origin_at(config, index, count);
}

Slic3r::BuildVolume build_volume_of(const Slic3r::DynamicPrintConfig& config)
{
    return orcinus::orca::build_volume_of(config);
}

Slic3r::ModelObject* add_shape_object(Slic3r::Model& model, const std::string& shape, const std::string& name, const Slic3r::DynamicPrintConfig& config)
{
    using namespace Slic3r;
    const BoundingBoxf bed = build_volume_of(config).bounding_volume2d();
    // GLCanvas3D::get_size_proportional_to_max_bed_size(0.1)
    const double side = 0.1 * std::max(bed.size().x(), bed.size().y());
    const TriangleMesh mesh = create_mesh(shape, BoundingBoxf3(), side);
    if (mesh.empty()) {
        return nullptr;
    }

    // load_mesh_object(mesh, _(type_name))
    const BoundingBoxf3 bb = mesh.bounding_box();
    ModelObject* new_object = model.add_object();
    new_object->name = name;
    new_object->add_instance(); // each object should have at least one instance
    ModelVolume* new_volume = new_object->add_volume(mesh);
    new_object->sort_volumes(true);
    new_volume->name = name;
    // set a default extruder value, since user can't add it manually
    new_object->config.set_key_value("extruder", new ConfigOptionInt(1));
    new_object->invalidate_bounding_box();
    new_object->translate(-bb.center());
    // Find an empty cell to put the object.
    const Vec2d start_point = bed.center();
    const Vec2f empty_cell = nearest_empty_cell(model, plate_box_of(config), bed, start_point.cast<float>());
    new_object->instances[0]->set_offset(to_3d(Vec2d(empty_cell(0), empty_cell(1)), -new_object->origin_translation.z()));
    new_object->ensure_on_bed();
    return new_object;
}

void keep_current_plate(Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config)
{
    const Slic3r::BoundingBoxf3 box = plate_box_of(config);
    for (std::size_t index = model.objects.size(); index-- > 0;) {
        Slic3r::ModelObject* const object = model.objects[index];
        for (std::size_t copy = object->instances.size(); copy-- > 0;) {
            if (!box.intersects(object->instance_convex_hull_bounding_box(copy))) {
                object->delete_instance(copy);
            }
        }
        if (object->instances.empty()) {
            model.delete_object(index);
        }
    }
}

}  // namespace detail

}  // namespace orcinus::orca
