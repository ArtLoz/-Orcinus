#include "orca_engine_adapter.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <fstream>
#include <iomanip>
#include <map>
#include <memory>
#include <mutex>
#include <numeric>
#include <set>
#include <sstream>
#include <unordered_set>
#include <utility>
#include <vector>

#include <CGAL/Min_sphere_of_points_d_traits_3.h>
#include <CGAL/Min_sphere_of_spheres_d.h>
#include <CGAL/Simple_cartesian.h>
#include <boost/algorithm/string/predicate.hpp>
#include <boost/filesystem.hpp>
#include <png.h>

#include "android_log_sink.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/BuildVolume.hpp"
#include "libslic3r/ClipperUtils.hpp"
#include "libslic3r/ExPolygon.hpp"
#include "libslic3r/Exception.hpp"
#include "libslic3r/Format/STL.hpp"
#include "libslic3r/Geometry/ConvexHull.hpp"
#include "libslic3r/GCode/GCodeProcessor.hpp"
#include "libslic3r/Layer.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/ModelArrange.hpp"
#include "libnest2d/common.hpp"
#include "libslic3r/Orient.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/Tesselate.hpp"
#include "libslic3r/Thread.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "libslic3r/Utils.hpp"
#include "libslic3r_version.h"

#include "nanosvg/nanosvg.h"
#include "nanosvg/nanosvgrast.h"
#include "setup_catalog.hpp"
#include "slic3r/GUI/LibVGCode/LibVGCodeWrapper.hpp"
#include "toolpaths_file.hpp"

#if !defined(__BYTE_ORDER__) || __BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__
#error "Mesh files are written in the native byte order, which must be little-endian"
#endif

namespace orcinus::orca {
namespace {

namespace fs = boost::filesystem;

// Warning, the level Orca's desktop app uses by default.
constexpr unsigned int orca_log_level = 2;

std::mutex engine_mutex;
std::unique_ptr<Slic3r::PresetBundle> preset_bundle;
// OrcaSlicer.conf of the data directory, and whether it existed when the engine
// started (GUI_App::m_app_conf_exists).
std::unique_ptr<Slic3r::AppConfig> engine_config;
bool engine_config_existed = false;
// Whether preset_bundle shows the presets engine_config installs and the
// selection it remembers; select_profiles() may select others.
bool bundle_follows_config = false;
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

SliceResult failure(const SliceStatus status, std::string message)
{
    SliceResult result;
    result.status = status;
    result.message = std::move(message);
    return result;
}

bool is_selected(const Slic3r::PresetBundle& bundle, const ProfileSelection& profiles)
{
    return bundle.printers.get_selected_preset_name() == profiles.printer
        && bundle.prints.get_selected_preset_name() == profiles.process
        && bundle.filaments.get_selected_preset_name() == profiles.filament
        && !bundle.filament_presets.empty()
        && bundle.filament_presets.front() == profiles.filament;
}

// The configuration of the selected presets. Other presets are selected the way
// the desktop app restores a selection at start-up, on a copy of the app
// configuration: the printer model and filament are marked as installed, as the
// Setup Wizard does, and the process and filament remembered for that printer
// are selected by load_selections().
SliceStatus select_profiles(
    Slic3r::PresetBundle& bundle,
    const ProfileSelection& profiles,
    Slic3r::DynamicPrintConfig& config,
    std::string& message
)
{
    if (is_selected(bundle, profiles)) {
        config = bundle.full_config();
        return SliceStatus::success;
    }

    const Slic3r::Preset* printer = bundle.printers.find_preset(profiles.printer, false);
    if (printer == nullptr || printer->vendor == nullptr) {
        message = "Unknown printer profile: " + profiles.printer;
        return SliceStatus::profile_not_found;
    }
    if (bundle.prints.find_preset(profiles.process, false) == nullptr) {
        message = "Unknown process profile: " + profiles.process;
        return SliceStatus::profile_not_found;
    }
    if (bundle.filaments.find_preset(profiles.filament, false) == nullptr) {
        message = "Unknown filament profile: " + profiles.filament;
        return SliceStatus::profile_not_found;
    }

    Slic3r::AppConfig app_config = *engine_config;
    app_config.set_variant(
        printer->vendor->id,
        printer->config.opt_string("printer_model"),
        printer->config.opt_string("printer_variant"),
        true
    );
    app_config.set(Slic3r::AppConfig::SECTION_FILAMENTS, profiles.filament, "true");
    app_config.set("presets", PRESET_PRINTER_NAME, profiles.printer);
    app_config.set_printer_setting(profiles.printer, PRESET_PRINT_NAME, profiles.process);
    app_config.set_printer_setting(profiles.printer, PRESET_FILAMENT_NAME, profiles.filament);
    bundle_follows_config = false;
    bundle.load_selections(app_config);

    if (bundle.printers.get_selected_preset_name() != profiles.printer
        || bundle.prints.get_selected_preset_name() != profiles.process
        || bundle.filament_presets.empty()
        || bundle.filament_presets.front() != profiles.filament) {
        message = "Orca selected printer '" + bundle.printers.get_selected_preset_name() + "', process '"
            + bundle.prints.get_selected_preset_name() + "', filament '"
            + (bundle.filament_presets.empty() ? std::string() : bundle.filament_presets.front())
            + "': the requested profiles are not compatible";
        return SliceStatus::profile_not_found;
    }

    config = bundle.full_config();
    return SliceStatus::success;
}

bool load_model(const std::string& model_path, Slic3r::Model& model)
{
    if (model_path.empty()) {
        Slic3r::ModelObject* object = model.add_object();
        object->name = "calibration-cube-20mm";
        object->add_volume(Slic3r::TriangleMesh(Slic3r::its_make_cube(20.0, 20.0, 20.0)));
        return true;
    }
    return Slic3r::load_stl(model_path.c_str(), &model) && !model.objects.empty();
}

// The models read for the objects of the plate, by file, each with the file's
// modification time. Guarded by engine_mutex.
struct LoadedModel {
    std::time_t modified{0};
    Slic3r::Model model;
};
std::map<std::string, std::unique_ptr<LoadedModel>> loaded_models;

// Adds a copy of the object of model_path to model. The file is read only when
// its model is not loaded yet or the file changed since. Returns nullptr when
// the file cannot be read.
Slic3r::ModelObject* add_loaded_object(const std::string& model_path, Slic3r::Model& model)
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
    return model.add_object(*loaded->second->model.objects.front());
}

// Forgets the models no object of the plate is loaded from.
void keep_models_of(const std::vector<PlateObject>& plate)
{
    for (auto loaded = loaded_models.begin(); loaded != loaded_models.end();) {
        const bool used = std::any_of(plate.begin(), plate.end(), [&loaded](const PlateObject& object) {
            return object.model_path == loaded->first;
        });
        loaded = used ? std::next(loaded) : loaded_models.erase(loaded);
    }
}

Slic3r::BuildVolume build_volume_of(const Slic3r::DynamicPrintConfig& config)
{
    return Slic3r::BuildVolume(
        config.option<Slic3r::ConfigOptionPoints>("printable_area")->values,
        config.opt_float("printable_height"),
        {},
        {});
}

// PartPlate::get_build_volume() of the only plate: the printable area up to the
// printable height, grown by BuildVolume::SceneEpsilon.
Slic3r::BoundingBoxf3 plate_box_of(const Slic3r::DynamicPrintConfig& config)
{
    const Slic3r::BoundingBoxf area(config.option<Slic3r::ConfigOptionPoints>("printable_area")->values);
    const double eps = Slic3r::BuildVolume::SceneEpsilon;
    return Slic3r::BoundingBoxf3(
        Slic3r::Vec3d(area.min.x() - eps, area.min.y() - eps, -eps),
        Slic3r::Vec3d(area.max.x() + eps, area.max.y() + eps, config.opt_float("printable_height") + eps));
}

// PartPlate::empty() for the plate an object joins: no other object's
// instance meets the plate (PartPlate::intersect_instance).
bool plate_empty(const Slic3r::Model& model, const Slic3r::ModelObject& joining, const Slic3r::BoundingBoxf3& plate_box)
{
    for (const Slic3r::ModelObject* object : model.objects) {
        for (std::size_t instance = 0; object != &joining && instance < object->instances.size(); ++instance) {
            if (plate_box.intersects(object->instance_convex_hull_bounding_box(instance))) {
                return false;
            }
        }
    }
    return true;
}

// GLCanvas3D::get_nearest_empty_cell() with its default 10 mm step: the cell of
// the plate nearest to start_point that no instance's convex hull covers, or a
// point beside start_point when every cell is covered. The cells are those of
// GLCanvas3D::get_empty_cells() as Bambu Studio has it, and OrcaSlicer had it
// until commit 8f777555: that commit keeps every cell some instance leaves
// free, which puts an object added to a plate onto the one in its centre.
Slic3r::Vec2f nearest_empty_cell(
    const Slic3r::Model& model,
    const Slic3r::BoundingBoxf3& plate_box,
    const Slic3r::BoundingBoxf& bed,
    const Slic3r::Vec2f& start_point
)
{
    using namespace Slic3r;
    const Vec2f step(10.0f, 10.0f);
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
    if (plate_empty(model, object, plate_box)) {
        instance->set_offset({start_point.x(), start_point.y(), z});
    } else {
        const Slic3r::Vec2f cell = nearest_empty_cell(model, plate_box, bed, start_point.cast<float>());
        instance->set_offset({cell.x(), cell.y(), z});
    }
}

// Gives a loaded object the transformation from the app, as the desktop app
// commits a moved instance (GLCanvas3D::do_move): the mesh is centred around
// the origin as for inspect_model(), the instance takes the transformation,
// and an instance above the plate drops onto it unless its auto drop is off.
void place_at(Slic3r::ModelObject& object, const ObjectPlacement& placement)
{
    Slic3r::Transform3d transformation = Slic3r::Transform3d::Identity();
    std::copy(placement.matrix.begin(), placement.matrix.end(), transformation.data());
    object.center_around_origin();
    Slic3r::ModelInstance* instance = object.add_instance();
    instance->set_transformation(Slic3r::Geometry::Transformation(transformation));
    instance->auto_drop = placement.auto_drop;
    const double shift_z = object.get_instance_min_z(0);
    if (instance->auto_drop && shift_z > Slic3r::SINKING_Z_THRESHOLD && shift_z != 0.0) {
        object.translate_instance(0, Slic3r::Vec3d(0.0, 0.0, -shift_z));
    }
}

// Loads the objects of the plate into model, in the plate's order; an object
// without a placement is placed as an object added to the plate that holds the
// objects before it.
bool load_plate(const std::vector<PlateObject>& plate, const Slic3r::DynamicPrintConfig& config, Slic3r::Model& model, std::string& message)
{
    for (const PlateObject& object : plate) {
        Slic3r::ModelObject* loaded = add_loaded_object(object.model_path, model);
        if (loaded == nullptr) {
            message = "Unable to read model " + object.model_path;
            return false;
        }
        if (object.placement.matrix.size() == 16) {
            place_at(*loaded, object.placement);
        } else {
            place_new_object(model, *loaded, config);
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
    const unsigned int printable = model.update_print_volume_state(build_volume_of(config));
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
    // render_legend()'s get_used_filament_from_volume(), summed over the extruders.
    const auto filament = [&gcode_result](const std::map<std::size_t, double>& volumes) {
        std::array<double, 2> sum{0.0, 0.0};
        for (const auto& [extruder, volume] : volumes) {
            if (extruder < gcode_result.filament_diameters.size() && extruder < gcode_result.filament_densities.size()) {
                const double radius = 0.5 * gcode_result.filament_diameters[extruder];
                sum[0] += 0.001 * volume / (PI * radius * radius);
                sum[1] += volume * gcode_result.filament_densities[extruder] * 0.001;
            }
        }
        return sum;
    };
    statistics.model_filament = filament(estimated.model_volumes_per_extruder);
    statistics.support_filament = filament(estimated.support_volumes_per_extruder);
    statistics.flushed_filament = filament(estimated.flush_per_filament);
    statistics.wipe_tower_filament = filament(estimated.wipe_tower_volumes_per_extruder);
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

bool file_exists(const std::string& path)
{
    boost::system::error_code error;
    return !path.empty() && fs::exists(path, error);
}

SceneStatus scene_status(const SliceStatus status)
{
    return status == SliceStatus::profile_not_found ? SceneStatus::profile_not_found : SceneStatus::engine_not_ready;
}

// PresetUpdater::priv::check_installed_vendor_profiles() with profile updates
// enabled: the filament library and the default bundle are always installed,
// the bundle of an enabled vendor when it is missing or older than the one in
// the resources, and the bundle of a vendor no longer enabled is removed.
// Returns the number of printer vendor bundles in the resources.
std::size_t install_vendor_bundles(const Slic3r::AppConfig& config)
{
    std::size_t vendor_bundles = 0;
    const fs::path rsrc_path = fs::path(Slic3r::resources_dir()) / "profiles";
    const fs::path vendor_path = fs::path(Slic3r::data_dir()) / PRESET_SYSTEM_DIR;
    const Slic3r::AppConfig::VendorMap& enabled_vendors = config.vendors();

    std::set<std::string> bundles{Slic3r::PresetBundle::ORCA_FILAMENT_LIBRARY};
    for (const fs::directory_entry& entry : fs::directory_iterator(rsrc_path)) {
        const fs::path& path = entry.path();
        if (!Slic3r::is_json_file(path.string())) {
            continue;
        }
        const fs::path path_in_vendor = vendor_path / path.filename();
        std::string vendor_name = path.filename().string();
        vendor_name.erase(vendor_name.size() - 5);
        if (bundles.count(vendor_name) > 0) {
            continue;
        }
        ++vendor_bundles;
        const bool is_vendor_enabled = vendor_name == Slic3r::PresetBundle::ORCA_DEFAULT_BUNDLE || enabled_vendors.count(vendor_name) > 0;
        if (fs::exists(path_in_vendor)) {
            if (is_vendor_enabled) {
                const Slic3r::Semver resource_version = Slic3r::get_version_from_json(path.string());
                const Slic3r::Semver vendor_version = Slic3r::get_version_from_json(path_in_vendor.string());
                const bool version_match = resource_version.maj() == vendor_version.maj() && resource_version.min() == vendor_version.min();
                if (!version_match || vendor_version < resource_version) {
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
    Slic3r::install_vendor_bundles_from_resources(std::vector<std::string>(bundles.begin(), bundles.end()));
    return vendor_bundles;
}

}  // namespace

std::string engine_version()
{
    return "orca-upstream/" SoftFever_VERSION " bridge/1.0";
}

EngineInitialization initialize(const EngineDirectories& directories)
{
    const std::lock_guard<std::mutex> lock(engine_mutex);
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
        const std::size_t vendor_bundles = install_vendor_bundles(*config);

        auto bundle = std::make_unique<Slic3r::PresetBundle>();
        // Same substitution rule as the desktop app's start-up.
        bundle->load_presets(*config, Slic3r::ForwardCompatibilitySubstitutionRule::EnableSystemSilent);
        if (vendor_bundles == 0) {
            // The Setup Wizard would offer no printer.
            result->message = "No vendor profiles in " + (fs::path(directories.resources_dir) / "profiles").string();
        } else {
            preset_bundle = std::move(bundle);
            engine_config = std::move(config);
            engine_config_existed = config_existed;
            bundle_follows_config = true;
            result->ready = true;
        }
    } catch (const std::exception& error) {
        result->message = error.what();
    }

    initialization = std::move(result);
    return *initialization;
}

SliceResult slice(
    const std::string& job_id,
    const std::vector<PlateObject>& objects,
    const std::string& output_path,
    const std::string& toolpaths_path,
    const ProfileSelection& profiles,
    const ProgressCallback& on_progress
)
{
    if (!acquire_job(job_id)) {
        return failure(SliceStatus::busy, "Another slicing job is running");
    }
    const ActiveJob active_job;

    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        return failure(SliceStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }

    const std::string temporary_path = output_path + ".part";
    try {
        Slic3r::DynamicPrintConfig config;
        std::string message;
        if (const SliceStatus status = select_profiles(*preset_bundle, profiles, config, message);
            status != SliceStatus::success) {
            return failure(status, message);
        }

        Slic3r::Model model;
        if (!load_plate(objects, config, model, message)) {
            return failure(SliceStatus::model_read_failed, message);
        }
        keep_models_of(objects);
        if (std::string outside; !check_print_volume(model, config, outside)) {
            return failure(SliceStatus::invalid_print, outside);
        }

        Slic3r::Print print;
        // PartPlate::set_print(): the print of the first plate starts at the origin.
        // Print leaves its plate origin uninitialized, and G-code coordinates are relative to it.
        print.set_plate_origin(Slic3r::Vec3d::Zero());
        print.set_status_callback([&on_progress](const Slic3r::PrintBase::SlicingStatus& status) {
            if (on_progress && status.percent >= 0) {
                on_progress(status.percent, status.text);
            }
        });
        if (!attach_print(&print)) {
            return failure(SliceStatus::cancelled, {});
        }

        print.apply(model, config);
        Slic3r::StringObjectException warning;
        const Slic3r::StringObjectException error = print.validate(&warning);
        if (!error.string.empty()) {
            return failure(SliceStatus::invalid_print, error.string);
        }
        if (cancellation_requested()) {
            return failure(SliceStatus::cancelled, {});
        }

        print.is_BBL_printer() = preset_bundle->is_bbl_vendor();
        print.process();

        Slic3r::GCodeProcessorResult gcode_result;
        print.export_gcode(temporary_path, &gcode_result, nullptr);
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
        result.layer_count = printed_layer_count(print);
        result.estimated_print_time_seconds = std::llround(print_time);
        result.filament_micrometers = std::llround(print.print_statistics().total_used_filament * 1'000.0);
        result.toolpaths_written = !toolpaths_path.empty() && write_toolpaths(gcode_result, print, config, toolpaths_path);
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
    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*preset_bundle, profiles, config, result.message);
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
            model = system_bed_model(*preset_bundle, shape);
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
            texture = system_bed_texture(*preset_bundle);
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

        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

namespace {

// Selection::get_bounding_sphere(): the smallest sphere around the convex
// hulls of the instance's volumes in world coordinates.
void bounding_sphere(const Slic3r::ModelObject& object, const Slic3r::ModelInstance& instance, ModelInspection& result)
{
    using Kernel = CGAL::Simple_cartesian<float>;
    using Traits = CGAL::Min_sphere_of_points_d_traits_3<Kernel, float>;
    using MinSphere = CGAL::Min_sphere_of_spheres_d<Traits>;
    std::vector<Kernel::Point_3> points;
    for (const Slic3r::ModelVolume* volume : object.volumes) {
        const Slic3r::Transform3d matrix = instance.get_matrix() * volume->get_matrix();
        for (const Slic3r::Vec3f& vertex : volume->get_convex_hull().its.vertices) {
            const Slic3r::Vec3d point = matrix * vertex.cast<double>();
            points.emplace_back(float(point.x()), float(point.y()), float(point.z()));
        }
    }
    MinSphere sphere(points.begin(), points.end());
    const float* center = sphere.center_cartesian_begin();
    result.sphere_center = {center[0], center[1], center[2]};
    result.sphere_radius = sphere.radius();
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

// A placed object as the app shows it: its size, instance transformation, and
// whether it fits the build volume, as the model's update_print_volume_state()
// found (Plater::priv::update_print_volume_state), with what the gizmo windows
// show (GizmoObjectManipulation::update_settings_value).
void describe_placed(const Slic3r::ModelObject& object, ModelInspection& result)
{
    const Slic3r::ModelInstance& instance = *object.instances.front();
    const Slic3r::BoundingBoxf3 box = object.instance_bounding_box(0);
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
    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*preset_bundle, profiles, config, result.message);
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
        objects.push_back({model_path, {}});
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
        describe_placed(*object, result);
        result.status = SceneStatus::success;
        result.facet_count = static_cast<std::int64_t>(mesh.indices.size());
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
void auto_orient(Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config, const std::vector<bool>& selected)
{
    Slic3r::orientation::OrientParams params;
    Slic3r::orientation::OrientParamsArea params_area;
    // OrientJob::process() copies the area parameters over the defaults the same way.
    std::memcpy(&params, &params_area, sizeof(params));
    params.min_volume = false;
    // orientation::orient() reports progress and checks for cancellation without testing for callbacks.
    params.progressind = [](unsigned, std::string) {};
    params.stopcondition = [] { return false; };

    const bool all = std::find(selected.begin(), selected.end(), true) == selected.end();
    Slic3r::orientation::OrientMeshs meshes;
    for (std::size_t index = 0; index < model.objects.size(); ++index) {
        if (!all && (index >= selected.size() || !selected[index])) {
            continue;
        }
        Slic3r::ModelObject* object = model.objects[index];
        for (Slic3r::ModelInstance* instance : object->instances) {
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
            meshes.push_back(std::move(mesh));
        }
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

// ArrangeJob with the given settings, on the only plate: init_arrange_params(),
// prepare_all(), check_unprintable(), process(), and finalize(). Wipe towers
// do not apply to single-filament plates.
void arrange_on_plate(Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config, const ArrangeSettings& settings)
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

    arrangement::ArrangePolygons selected;
    arrangement::ArrangePolygons unselected;
    for (ModelObject* object : model.objects) {
        for (ModelInstance* instance : object->instances) {
            if (!instance->printable)
                continue;
            arrangement::ArrangePolygon polygon = get_instance_arrange_poly(instance, config);
            polygon.itemid = int(selected.size());
            selected.emplace_back(std::move(polygon));
        }
    }
    add_exclude_areas(unselected, config, MAX_NUM_PLATES, 0.0f);
    // check_unprintable(): nothing without area or above the build height is arranged.
    selected.erase(
        std::remove_if(selected.begin(), selected.end(), [&](const arrangement::ArrangePolygon& polygon) {
            return polygon.poly.area() < 0.001 || polygon.height > params.printable_height;
        }),
        selected.end());

    update_arrange_params(params, &config, selected);
    update_selected_items_inflation(selected, &config, params);
    update_unselected_items_inflation(unselected, &config, params);
    update_selected_items_axis_align(selected, &config, params);
    const Points bed = get_shrink_bedpts(&config, params);
    add_exclude_areas(params.excluded_regions, config, 1, scale_(1));
    arrangement::arrange(selected, unselected, bed, params);

    // PartPlateList::postprocess_arrange_polygon() for a list of one plate:
    // items that do not fit go beside it, as the desktop app adds plates for them.
    const BoundingBoxf plate = BoundingBoxf(config.option<ConfigOptionPoints>("printable_area")->values);
    const double plate_width = plate.size().x();
    const double plate_depth = plate.size().y();
    const int plate_count = 1;
    std::sort(selected.begin(), selected.end(), [](const auto& a, const auto& b) { return a.itemid < b.itemid; });
    for (arrangement::ArrangePolygon& polygon : selected) {
        if (polygon.bed_idx == -1) {
            polygon.bed_idx = plate_count;
            const BoundingBox box = get_extents(polygon.transformed_poly());
            polygon.translation(X) = 0.5 * box.size()[0];
            polygon.translation(Y) = scaled<double>(plate_depth) - 0.5 * box.size()[1];
        }
        // compute_colum_count() of the plates the arrangement needs.
        const int plates = std::max(plate_count, polygon.bed_idx + 1);
        const float root = std::sqrt(float(plates));
        const int columns = root > std::round(root) ? int(std::round(root)) + 1 : int(std::round(root));
        polygon.row = polygon.bed_idx / columns;
        polygon.col = polygon.bed_idx % columns;
        polygon.translation(X) += scaled<double>(plate_width * (1.0 + 1.0 / 5.0) * polygon.col);
        polygon.translation(Y) -= scaled<double>(plate_depth * (1.0 + 1.0 / 5.0) * polygon.row);
        polygon.apply();
    }
    for (ModelObject* object : model.objects) {
        object->invalidate_bounding_box();
    }
}

// The instance's lowest point with the transformation, as instance_bounding_box().min.z().
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

// Loads the object of model_path with the instance transformation placement.
bool load_placed(const std::string& model_path, const std::vector<double>& placement, Slic3r::Model& model)
{
    Slic3r::ModelObject* object = add_loaded_object(model_path, model);
    if (object == nullptr) {
        return false;
    }
    object->center_around_origin();
    Slic3r::Transform3d transformation = Slic3r::Transform3d::Identity();
    std::copy(placement.begin(), placement.end(), transformation.data());
    object->add_instance()->set_transformation(Slic3r::Geometry::Transformation(transformation));
    return true;
}

FlatteningPlanes describe_flattening_planes(const std::string& model_path, const ProfileSelection& profiles, const std::vector<double>& placement)
{
    FlatteningPlanes result;
    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    if (placement.size() != 16) {
        result.message = "The placement is not a 4 x 4 matrix";
        return result;
    }
    try {
        Slic3r::Model model;
        if (!load_placed(model_path, placement, model)) {
            result.message = "Unable to read model " + model_path;
            return result;
        }
        result.planes = flattening_planes(*model.objects.front());
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

ModelInspection place_model(
    const std::string& model_path,
    const ProfileSelection& profiles,
    const std::vector<double>& previous_placement,
    const std::vector<double>& placement,
    bool auto_drop,
    Manipulation manipulation,
    const std::array<double, 3>& face_normal
)
{
    ModelInspection result;
    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
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
        if (const SliceStatus status = select_profiles(*preset_bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }

        Slic3r::Model model;
        if (!load_placed(model_path, placement, model)) {
            result.message = "Unable to read model " + model_path;
            return result;
        }
        Slic3r::ModelObject& object = *model.objects.front();
        object.instances.front()->auto_drop = auto_drop;
        const double min_z_before = instance_min_z(object, previous_placement);
        switch (manipulation) {
        case Manipulation::move: {
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
        describe_placed(object, result);
        result.status = SceneStatus::success;
        result.facet_count = static_cast<std::int64_t>(object.facets_count());
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
    const ArrangeSettings& arrange_settings
)
{
    PlateInspection result;
    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    if (std::any_of(plate.begin(), plate.end(), [](const PlateObject& object) { return object.placement.matrix.size() != 16; })) {
        result.message = "A placement is not a 4 x 4 matrix";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = select_profiles(*preset_bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = scene_status(status);
            return result;
        }

        Slic3r::Model model;
        if (!load_plate(plate, config, model, result.message)) {
            return result;
        }
        keep_models_of(plate);
        switch (manipulation) {
        case PlateManipulation::auto_orient:
            auto_orient(model, config, selected);
            break;
        case PlateManipulation::arrange:
            arrange_on_plate(model, config, arrange_settings);
            break;
        case PlateManipulation::update_print_volume_state:
            break;
        default:
            result.message = "Unknown manipulation";
            return result;
        }

        model.update_print_volume_state(build_volume_of(config));
        for (const Slic3r::ModelObject* object : model.objects) {
            ModelInspection& placed = result.objects.emplace_back();
            describe_placed(*object, placed);
            placed.status = SceneStatus::success;
            placed.facet_count = static_cast<std::int64_t>(object->facets_count());
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

using VendorMap = Slic3r::AppConfig::VendorMap;

// The Setup Wizard's printers and filaments, read once per process.
std::unique_ptr<setup::Catalog> catalog;

const setup::Catalog& setup_catalog()
{
    if (catalog == nullptr) {
        catalog = std::make_unique<setup::Catalog>(setup::load(Slic3r::data_dir(), Slic3r::resources_dir()));
    }
    return *catalog;
}

const setup::Model* find_setup_model(const setup::Catalog& data, const std::string& model_id)
{
    const auto model = std::find_if(data.models.begin(), data.models.end(), [&model_id](const setup::Model& candidate) {
        return candidate.model == model_id;
    });
    return model == data.models.end() ? nullptr : &*model;
}

// Shows the selection engine_config remembers again after select_profiles() selected other presets.
void follow_config(Slic3r::PresetBundle& bundle)
{
    if (!bundle_follows_config) {
        bundle.load_selections(*engine_config);
        bundle_follows_config = true;
    }
}

// AppConfig::save() refuses to run on any thread but the one the app considers
// its main thread; the engine's calls run on the service's binder threads, one at a time.
void save_config()
{
    Slic3r::save_main_thread_id();
    engine_config->save();
}

// get_diameter_string() of the sidebar (Plater.cpp): "0.4", "0.25".
std::string diameter_string(const float diameter)
{
    std::ostringstream stream;
    stream << std::fixed << std::setprecision(2) << diameter;
    std::string text = stream.str();
    if (text.find('.') != std::string::npos) {
        text.erase(text.find_last_not_of('0') + 1);
        if (text.back() == '.') {
            text += '0';
        }
    }
    return text;
}

std::string bundle_name(Slic3r::PresetBundle& bundle, const Slic3r::Preset& preset)
{
    bundle.bundles.ReadLock();
    const auto found = bundle.bundles.m_bundles.find(preset.bundle_id);
    std::string name = found == bundle.bundles.m_bundles.end() ? std::string() : found->second.name;
    bundle.bundles.ReadUnlock();
    return name;
}

std::string lower(std::string text)
{
    std::transform(text.begin(), text.end(), text.begin(), [](const unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return text;
}

struct ComboEntry {
    std::string label;
    std::string vendor;
    std::string type;
    std::string bundle;
};

using ComboEntries = std::map<std::string, ComboEntry>;

// The order of "add_presets" in PlaterPresetComboBox::update(): sorted by the
// key, a non-empty key before an empty one, then by the name.
std::vector<ComboEntries::const_iterator> sorted_by(const ComboEntries& entries, const std::function<std::string(const ComboEntry&)>& key)
{
    std::vector<ComboEntries::const_iterator> list;
    for (auto entry = entries.begin(); entry != entries.end(); ++entry) {
        list.push_back(entry);
    }
    std::stable_sort(list.begin(), list.end(), [&key](const auto l, const auto r) {
        const std::string l_key = lower(key(l->second));
        const std::string r_key = lower(key(r->second));
        if (l_key.empty() != r_key.empty()) {
            return !l_key.empty();
        }
        return l_key != r_key ? l_key < r_key : l->first < r->first;
    });
    return list;
}

void append_items(std::vector<PresetItem>& items, const std::vector<ComboEntries::const_iterator>& list, const PresetGroup group,
                  const std::string& selected, const std::function<std::string(const ComboEntry&)>& subgroup)
{
    for (const auto entry : list) {
        PresetItem& item = items.emplace_back();
        item.name = entry->first;
        item.label = entry->second.label;
        item.group = group;
        item.subgroup = subgroup(entry->second);
        item.selected = entry->first == selected;
    }
}

// PlaterPresetComboBox::update() for the printer or the first filament, with the
// desktop app's default preferences: unsupported presets hidden
// (show_unsupported_presets) and user filaments not grouped (group_filament_presets).
// Android opens no projects, so no project presets are listed.
std::vector<PresetItem> plater_combo_items(Slic3r::PresetBundle& bundle, const Slic3r::Preset::Type type)
{
    const bool is_filament = type == Slic3r::Preset::TYPE_FILAMENT;
    Slic3r::PresetCollection& collection = is_filament ? bundle.filaments : static_cast<Slic3r::PresetCollection&>(bundle.printers);
    if (is_filament && bundle.filament_presets.empty()) {
        return {};
    }

    ComboEntries user_presets;
    ComboEntries bundle_presets;
    ComboEntries system_presets;
    std::unordered_set<std::string> system_printer_models;
    std::string selected_user_preset;
    std::string selected_bundle_preset;
    std::string selected_system_preset;

    const std::deque<Slic3r::Preset>& presets = collection.get_presets();
    for (size_t i = presets.front().is_visible ? 0 : collection.num_default_presets(); i < presets.size(); ++i) {
        Slic3r::Preset& preset = collection.preset(i, true);
        const bool is_selected = is_filament ? bundle.filament_presets.front() == preset.name : i == collection.get_selected_idx();
        if (!is_selected && !preset.is_visible) {
            continue;
        }
        if (is_selected && !preset.is_visible) {
            preset.is_visible = true;
        }

        std::string name = preset.name;
        ComboEntry entry{preset.label(false)};
        if (preset.is_from_bundle()) {
            entry.bundle = bundle_name(bundle, preset);
        }
        if (is_filament) {
            if (const auto* vendor = preset.config.option<Slic3r::ConfigOptionStrings>("filament_vendor"); vendor != nullptr && !vendor->values.empty()) {
                entry.vendor = vendor->values.front() == "Bambu Lab" ? "Bambu" : vendor->values.front();
            }
            if (const auto* filament_type = preset.config.option<Slic3r::ConfigOptionStrings>("filament_type"); filament_type != nullptr && !filament_type->values.empty()) {
                entry.type = filament_type->values.front();
            }
        }

        if (!preset.is_compatible) {
            // Unsupported presets.
            continue;
        }
        if (preset.is_default || preset.is_system) {
            if (!is_filament) {
                // A system printer is listed once per printer model.
                name = preset.config.opt_string("printer_model");
                entry.label = name;
                if (system_printer_models.insert(name).second) {
                    system_presets.emplace(name, entry);
                }
            } else {
                system_presets.emplace(name, entry);
            }
            if (is_selected) {
                selected_system_preset = name;
            }
        } else if (preset.is_project_embedded) {
            continue;
        } else if (preset.is_from_bundle()) {
            bundle_presets.emplace(name, entry);
            if (is_selected) {
                selected_bundle_preset = name;
            }
        } else {
            user_presets.emplace(name, entry);
            if (is_selected) {
                selected_user_preset = name;
            }
        }
    }

    std::vector<PresetItem> items;
    const auto no_subgroup = [](const ComboEntry&) { return std::string(); };
    append_items(items, sorted_by(user_presets, [](const ComboEntry& entry) { return entry.label; }), PresetGroup::user, selected_user_preset, no_subgroup);
    append_items(items, sorted_by(bundle_presets, [](const ComboEntry& entry) { return entry.bundle; }), PresetGroup::bundle, selected_bundle_preset,
                 [](const ComboEntry& entry) { return entry.bundle; });

    std::vector<ComboEntries::const_iterator> system_list;
    for (auto entry = system_presets.begin(); entry != system_presets.end(); ++entry) {
        system_list.push_back(entry);
    }
    if (is_filament) {
        static const std::vector<std::string> filament_orders = {"Bambu PLA Basic", "Bambu PLA Matte", "Bambu PETG HF", "Bambu ABS", "Bambu PLA Silk", "Bambu PLA-CF",
                                                                 "Bambu PLA Galaxy", "Bambu PLA Metal", "Bambu PLA Marble", "Bambu PETG-CF", "Bambu PETG Translucent", "Bambu ABS-GF"};
        static const std::vector<std::string> first_vendors = {"", "Bambu", "Generic"};
        static const std::vector<std::string> first_types = {"PLA", "PETG", "ABS", "TPU"};
        const auto position = [](const std::vector<std::string>& order, const std::string& value) {
            return std::find(order.begin(), order.end(), value) - order.begin();
        };
        std::stable_sort(system_list.begin(), system_list.end(), [&position](const auto l, const auto r) {
            if (const auto l_order = position(filament_orders, l->first), r_order = position(filament_orders, r->first); l_order != r_order) {
                return l_order < r_order;
            }
            if (const auto l_vendor = position(first_vendors, l->second.vendor), r_vendor = position(first_vendors, r->second.vendor); l_vendor != r_vendor) {
                return l_vendor < r_vendor;
            }
            if (const auto l_type = position(first_types, l->second.type), r_type = position(first_types, r->second.type); l_type != r_type) {
                return l_type < r_type;
            }
            return l->first < r->first;
        });
    }
    append_items(items, system_list, PresetGroup::system, selected_system_preset, [](const ComboEntry& entry) { return entry.vendor; });
    return items;
}

// TabPresetComboBox::update() of the process tab: the visible presets
// compatible with the printer, and the selected one.
std::vector<PresetItem> tab_combo_items(Slic3r::PresetBundle& bundle, Slic3r::PresetCollection& collection)
{
    ComboEntries user_presets;
    ComboEntries bundle_presets;
    ComboEntries system_presets;
    std::string selected;

    const std::deque<Slic3r::Preset>& presets = collection.get_presets();
    const size_t idx_selected = collection.get_selected_idx();
    for (size_t i = presets.front().is_visible ? 0 : collection.num_default_presets(); i < presets.size(); ++i) {
        const Slic3r::Preset& preset = presets[i];
        if (!preset.is_visible || (!preset.is_compatible && i != idx_selected)) {
            continue;
        }
        if (preset.is_project_embedded) {
            continue;
        }
        if (i == idx_selected) {
            selected = preset.name;
        }
        if (preset.is_default || preset.is_system) {
            system_presets.emplace(preset.name, ComboEntry{preset.name});
        } else if (preset.is_from_bundle()) {
            bundle_presets.emplace(preset.name, ComboEntry{preset.label(false), {}, {}, bundle_name(bundle, preset)});
        } else {
            user_presets.emplace(preset.name, ComboEntry{preset.name});
        }
    }

    std::vector<PresetItem> items;
    const auto in_order = [](const ComboEntries& entries) {
        std::vector<ComboEntries::const_iterator> list;
        for (auto entry = entries.begin(); entry != entries.end(); ++entry) {
            list.push_back(entry);
        }
        return list;
    };
    const auto no_subgroup = [](const ComboEntry&) { return std::string(); };
    append_items(items, in_order(user_presets), PresetGroup::user, selected, no_subgroup);
    append_items(items, in_order(bundle_presets), PresetGroup::bundle, selected, [](const ComboEntry& entry) { return entry.bundle; });
    append_items(items, in_order(system_presets), PresetGroup::system, selected, no_subgroup);
    return items;
}

PresetState preset_state(Slic3r::PresetBundle& bundle)
{
    PresetState state;
    state.status = SceneStatus::success;
    state.setup_required = !engine_config_existed || bundle.printers.only_default_printers();
    state.selection.printer = bundle.printers.get_selected_preset_name();
    state.selection.process = bundle.prints.get_selected_preset_name();
    state.selection.filament = bundle.filament_presets.empty() ? std::string() : bundle.filament_presets.front();
    state.printers = plater_combo_items(bundle, Slic3r::Preset::TYPE_PRINTER);
    state.filaments = plater_combo_items(bundle, Slic3r::Preset::TYPE_FILAMENT);
    state.processes = tab_combo_items(bundle, bundle.prints);

    // Sidebar::update_presets() for a printer with one extruder.
    const auto* nozzle_diameter = bundle.printers.get_edited_preset().config.option<Slic3r::ConfigOptionFloats>("nozzle_diameter");
    std::vector<std::string> diameters = bundle.printers.diameters_of_selected_printer();
    const std::string nozzle = nozzle_diameter == nullptr || nozzle_diameter->values.empty() ? std::string() : diameter_string(float(nozzle_diameter->values.front()));
    if (!diameters.empty() && diameters.front().empty() && !nozzle.empty()) {
        diameters.front() = nozzle;
    }
    if (!nozzle.empty() && std::find(diameters.begin(), diameters.end(), nozzle) == diameters.end()) {
        diameters.push_back(nozzle);
    }
    state.nozzle_diameters = std::move(diameters);
    state.nozzle_diameter = nozzle;
    return state;
}

// Tab::select_preset() of the printer tab with the default preferences, whose
// "Remember printer configuration" selects the process and filament the
// printer last used (PresetBundle::update_selections).
void select_printer(Slic3r::PresetBundle& bundle, const std::string& name)
{
    bundle.printers.select_preset_by_name(name, false);
    bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Always, Slic3r::PresetSelectCompatibleType::Always);
    if (engine_config->get_bool("remember_printer_config")) {
        bundle.update_selections(*engine_config);
    }
}

// get_preferred_printer_model in GuideFrame::apply_config(): the first model of
// the vendor the wizard installs, or installs another nozzle diameter of, with
// that nozzle diameter. Reads the vendor profile without inserting an empty one.
std::string preferred_printer_model(const Slic3r::PresetBundle& bundle, const VendorMap& enabled_vendors, const VendorMap& old_enabled_vendors,
                                    const std::string& bundle_name, std::string& variant)
{
    const auto config = enabled_vendors.find(bundle_name);
    if (config == enabled_vendors.end()) {
        return {};
    }
    const auto printer_profile = bundle.vendors.find(bundle_name);
    for (const auto& [model_id, variants] : config->second) {
        if (variants.empty()) {
            continue;
        }
        variant = *variants.begin();
        if (variants.size() > 1) {
            if (printer_profile != bundle.vendors.end() && !printer_profile->second.models.empty()) {
                const auto& models = printer_profile->second.models;
                const auto printer_model = std::find_if(models.begin(), models.end(), [&id = model_id](const auto& model) { return model.id == id; });
                if (printer_model != models.end()) {
                    for (const auto& printer_variant : printer_model->variants) {
                        if (variants.count(printer_variant.name) > 0) {
                            variant = printer_variant.name;
                            break;
                        }
                    }
                }
            } else if (variant != Slic3r::PresetBundle::ORCA_DEFAULT_PRINTER_VARIANT
                       && variants.count(Slic3r::PresetBundle::ORCA_DEFAULT_PRINTER_VARIANT) > 0) {
                variant = Slic3r::PresetBundle::ORCA_DEFAULT_PRINTER_VARIANT;
            }
        }

        const auto config_old = old_enabled_vendors.find(bundle_name);
        if (config_old == old_enabled_vendors.end()) {
            return model_id;
        }
        const auto model_old = config_old->second.find(model_id);
        if (model_old == config_old->second.end()) {
            return model_id;
        }
        if (model_old->second != variants) {
            for (const std::string& added : variants) {
                if (model_old->second.count(added) == 0) {
                    variant = added;
                    return model_id;
                }
            }
        }
    }
    variant.clear();
    return {};
}

PresetState preset_failure(const SceneStatus status, std::string message)
{
    PresetState result;
    result.status = status;
    result.message = std::move(message);
    return result;
}

}  // namespace

PresetState describe_presets()
{
    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        follow_config(*preset_bundle);
        return preset_state(*preset_bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

PresetState select_preset(const PresetChoice choice, const std::string& value)
{
    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *preset_bundle;
        follow_config(bundle);
        switch (choice) {
        case PresetChoice::printer:
            if (bundle.printers.find_preset(value, false) == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Unknown printer profile: " + value);
            }
            select_printer(bundle, value);
            break;
        case PresetChoice::printer_model: {
            // Plater::priv::on_select_preset() for a printer model.
            Slic3r::Preset* preset = bundle.get_similar_printer_preset(value, {});
            if (preset == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Unknown printer model: " + value);
            }
            preset->is_visible = true;
            select_printer(bundle, preset->name);
            break;
        }
        case PresetChoice::nozzle_diameter: {
            // Sidebar::priv::switch_diameter()
            const auto* nozzle_diameter = bundle.printers.get_edited_preset().config.option<Slic3r::ConfigOptionFloats>("nozzle_diameter");
            if (nozzle_diameter != nullptr && !nozzle_diameter->values.empty() && diameter_string(float(nozzle_diameter->values.front())) == value) {
                break;
            }
            Slic3r::Preset* preset = bundle.get_similar_printer_preset({}, value);
            if (preset == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Configuration incompatible");
            }
            preset->is_visible = true;
            select_printer(bundle, preset->name);
            break;
        }
        case PresetChoice::filament:
            if (bundle.filaments.find_preset(value, false) == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Unknown filament profile: " + value);
            }
            // Plater::priv::on_select_preset(), then Tab::select_preset() of the filament tab.
            bundle.set_filament_preset(0, value);
            bundle.filaments.select_preset_by_name(value, false);
            break;
        case PresetChoice::process:
            if (bundle.prints.find_preset(value, false) == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Unknown process profile: " + value);
            }
            // Tab::select_preset() of the process tab.
            bundle.prints.select_preset_by_name(value, false);
            bundle.update_compatible(Slic3r::PresetSelectCompatibleType::Never, Slic3r::PresetSelectCompatibleType::Always);
            break;
        default:
            return preset_failure(SceneStatus::profile_not_found, "Unknown preset choice");
        }
        bundle.export_selections(*engine_config);
        save_config();
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

SetupPrinters describe_setup_printers()
{
    SetupPrinters result;
    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        for (const setup::Model& model : setup_catalog().models) {
            SetupPrinterModel& item = result.models.emplace_back();
            item.vendor = model.vendor;
            item.model = model.model;
            item.name = model.name;
            item.nozzle_diameters = model.nozzle_diameters;
            item.default_materials = model.materials;
            item.cover = model.cover;
            item.installed_nozzles = setup::installed_nozzles(model, *engine_config);
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
        result.models.clear();
    }
    return result;
}

SetupFilaments describe_setup_filaments(const std::vector<std::string>& models)
{
    SetupFilaments result;
    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        const setup::Catalog& data = setup_catalog();
        std::vector<const setup::Model*> chosen;
        std::set<std::string> default_materials;
        for (const std::string& model_id : models) {
            const setup::Model* model = find_setup_model(data, model_id);
            if (model == nullptr) {
                result.status = SceneStatus::profile_not_found;
                result.message = "Unknown printer model: " + model_id;
                return result;
            }
            chosen.push_back(model);
            // GuideFrame::OnScriptMessage("save_userguide_models"): the default materials of a chosen model are selected.
            default_materials.insert(model->materials.begin(), model->materials.end());
        }

        // GuideFrame::LoadProfile(): the installed filaments are selected.
        const std::map<std::string, std::string> installed = engine_config->has_section(Slic3r::AppConfig::SECTION_FILAMENTS)
            ? engine_config->get_section(Slic3r::AppConfig::SECTION_FILAMENTS)
            : std::map<std::string, std::string>();
        for (const auto& [name, filament] : data.filaments) {
            SetupFilament item;
            for (std::size_t index = 0; index < chosen.size(); ++index) {
                const bool compatible = std::any_of(chosen[index]->nozzle_diameters.begin(), chosen[index]->nozzle_diameters.end(),
                    [&filament = filament, model = chosen[index]](const std::string& nozzle) {
                        return filament.models.count({model->model, nozzle}) > 0;
                    });
                if (compatible) {
                    item.models.push_back(static_cast<std::int32_t>(index));
                }
            }
            // The filament page lists a filament for the chosen printers, or for every printer.
            if (!filament.models.empty() && item.models.empty()) {
                continue;
            }
            item.name = name;
            item.vendor = filament.vendor;
            item.type = filament.type;
            item.selected = installed.count(name) > 0 || default_materials.count(name) > 0;
            result.filaments.push_back(std::move(item));
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::profile_not_found;
        result.message = error.what();
        result.filaments.clear();
    }
    return result;
}

PresetState apply_setup(const std::vector<std::string>& models, const std::vector<std::string>& filaments)
{
    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    if (models.empty() || filaments.empty()) {
        return preset_failure(SceneStatus::profile_not_found, "The Setup Wizard installs at least one printer and one filament");
    }
    try {
        Slic3r::PresetBundle& bundle = *preset_bundle;
        follow_config(bundle);

        // GuideFrame::SaveProfile()
        const setup::Catalog& data = setup_catalog();
        VendorMap vendors;
        for (const std::string& model_id : models) {
            const setup::Model* model = find_setup_model(data, model_id);
            if (model == nullptr) {
                return preset_failure(SceneStatus::profile_not_found, "Unknown printer model: " + model_id);
            }
            vendors[model->vendor][model->model].insert(model->nozzle_diameters.begin(), model->nozzle_diameters.end());
        }
        std::map<std::string, std::string> enabled_filaments;
        for (const std::string& filament : filaments) {
            enabled_filaments[filament] = "true";
        }
        engine_config->set("firstguide", "finish", "1");

        // GuideFrame::apply_config(): Orca's "custom" printers are considered first, then 3rd party.
        const VendorMap old_vendors = engine_config->vendors();
        std::string preferred_variant;
        std::string preferred_model = preferred_printer_model(bundle, vendors, old_vendors, Slic3r::PresetBundle::ORCA_DEFAULT_BUNDLE, preferred_variant);
        if (preferred_model.empty()) {
            for (const auto& vendor : vendors) {
                if (vendor.first == Slic3r::PresetBundle::ORCA_DEFAULT_BUNDLE) {
                    continue;
                }
                preferred_model = preferred_printer_model(bundle, vendors, old_vendors, vendor.first, preferred_variant);
                if (!preferred_model.empty()) {
                    break;
                }
            }
        }
        if (!bundle.apply_vendor_config(vendors, enabled_filaments, engine_config.get(), true, preferred_model, preferred_variant)) {
            return preset_failure(SceneStatus::write_failed, "Unable to install the vendor bundles");
        }
        save_config();
        engine_config_existed = true;
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

PresetState apply_default_setup()
{
    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        return preset_failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    try {
        Slic3r::PresetBundle& bundle = *preset_bundle;
        follow_config(bundle);
        if (bundle.printers.only_default_printers()) {
            // GuideFrame::run() for a cancelled wizard: install the default printer, clear the
            // filament section and use the default materials. ORCA_DEFAULT_PRINTER_MODEL names
            // the preset "MyKlipper 0.4 nozzle", not its printer model "Generic Klipper Printer",
            // so the desktop app installs no printer here; the model of that preset is installed.
            std::string model = Slic3r::PresetBundle::ORCA_DEFAULT_PRINTER_MODEL;
            std::string variant = Slic3r::PresetBundle::ORCA_DEFAULT_PRINTER_VARIANT;
            if (const Slic3r::Preset* printer = bundle.printers.find_preset(model, false); printer != nullptr && printer->is_system) {
                model = printer->config.opt_string("printer_model");
                variant = printer->config.opt_string("printer_variant");
            }
            engine_config->set_variant(Slic3r::PresetBundle::ORCA_DEFAULT_BUNDLE, model, variant, true);
            engine_config->clear_section(Slic3r::AppConfig::SECTION_FILAMENTS);
            bundle.load_selections(*engine_config, {model, variant, Slic3r::PresetBundle::ORCA_DEFAULT_FILAMENT, std::string()});
        }
        bundle.export_selections(*engine_config);
        save_config();
        engine_config_existed = true;
        return preset_state(bundle);
    } catch (const std::exception& error) {
        return preset_failure(SceneStatus::profile_not_found, error.what());
    }
}

}  // namespace orcinus::orca
