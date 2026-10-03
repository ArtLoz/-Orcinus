// OrcaSlicer's brim ears tool (GLGizmoBrimEars): while it is open the desktop
// app keeps the first layer of the selected copy, which the tool generates
// ears along and checks the ears against, and the copy's model parts, which a
// press places an ear under. The engine keeps the same in a session from
// begin_brim_ears() to end_brim_ears(); the ears themselves are the object's
// (ModelObject::brim_points), which the app edits.

#include <algorithm>
#include <cmath>
#include <limits>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "libslic3r/AABBMesh.hpp"
#include "libslic3r/BrimEarsPoint.hpp"
#include "libslic3r/ClipperUtils.hpp"
#include "libslic3r/ExPolygon.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/MultiPoint.hpp"
#include "libslic3r/TriangleMeshSlicer.hpp"

#include "engine_context.hpp"
#include "orca_engine_adapter.hpp"

namespace orcinus::orca {
namespace {

using Slic3r::Transform3d;
using Slic3r::Vec3d;

// A model part of the copy, as register_single_mesh_pick() keeps it: its
// transformation in the object and its raycaster.
struct EarPart {
    Transform3d volume{Transform3d::Identity()};
    std::shared_ptr<const Slic3r::TriangleMesh> mesh;
    std::unique_ptr<Slic3r::AABBMesh> raycaster;
};

struct BrimEarsState {
    bool open{false};
    Transform3d instance{Transform3d::Identity()};
    std::vector<EarPart> parts;
    // m_first_layer, in the world.
    Slic3r::ExPolygons first_layer;
};

BrimEarsState& state()
{
    static BrimEarsState current;
    return current;
}

// GLGizmoBrimEars::first_layer_slicer(): the copy's model parts less its
// negative volumes, sliced at 0.1 mm in the world.
Slic3r::ExPolygons first_layer_of(const Slic3r::ModelObject& object, const Slic3r::ModelInstance& instance)
{
    using namespace Slic3r;
    std::vector<float> slice_height(1, 0.1f);
    MeshSlicingParamsEx params;
    params.mode = MeshSlicingParams::SlicingMode::Regular;
    params.closing_radius = 0.1f;
    params.extra_offset = 0.05f;
    params.resolution = 0.01;
    ExPolygons part_ex;
    ExPolygons negative_ex;
    for (const ModelVolume* model_volume : object.volumes) {
        if (model_volume->type() != ModelVolumeType::MODEL_PART && model_volume->type() != ModelVolumeType::NEGATIVE_VOLUME)
            continue;
        indexed_triangle_set volume_its = model_volume->mesh().its;
        if (volume_its.indices.empty())
            continue;
        const Transform3d trsf = instance.get_matrix() * model_volume->get_matrix();
        MeshSlicingParamsEx params_ex(params);
        params_ex.trafo = params_ex.trafo * trsf;
        if (params_ex.trafo.rotation().determinant() < 0.)
            its_flip_triangles(volume_its);
        ExPolygons sliced_layer = slice_mesh_ex(volume_its, slice_height, params_ex).front();
        if (model_volume->type() == ModelVolumeType::MODEL_PART)
            part_ex = union_ex(part_ex, sliced_layer);
        else
            negative_ex = union_ex(negative_ex, sliced_layer);
    }
    return diff_ex(part_ex, negative_ex);
}

// GLGizmoBrimEars::get_detection_radius_max()
double detection_radius_max_of(const Slic3r::ExPolygons& first_layer)
{
    using namespace Slic3r;
    double max_dist = 0.0;
    int min_points_num = 0;
    for (const ExPolygon& ex_poly : first_layer) {
        Polygon out_poly = ex_poly.contour;
        Points out_points = out_poly.points;
        out_points.push_back(out_points.front());
        double tolerance = 0.0;
        min_points_num = int(MultiPoint::_douglas_peucker(out_points, 0).size());
        int repeat = 0;
        int loop_protect = 0;
        for (;;) {
            loop_protect++;
            tolerance += 10;
            const int num = int(MultiPoint::_douglas_peucker(out_points, tolerance / SCALING_FACTOR).size());
            if (num == min_points_num) {
                repeat++;
                if (repeat > 1)
                    break;
            }
            min_points_num = num;
            if (loop_protect > 100)
                break;
        }
        loop_protect = 0;
        for (;;) {
            loop_protect++;
            tolerance -= 1;
            const int num = int(MultiPoint::_douglas_peucker(out_points, tolerance / SCALING_FACTOR).size());
            if (num <= min_points_num) {
                min_points_num = num;
            } else {
                break;
            }
            if (loop_protect > 100)
                break;
        }
        tolerance += 1;
        if (tolerance > max_dist)
            max_dist = tolerance;
    }
    if (max_dist > 100 || max_dist <= 0)
        return 100;
    return max_dist;
}

// GLGizmoBrimEars::generate_points()
Slic3r::Points generate_points(Slic3r::Polygon& obj_polygon, float ear_detection_length, float brim_ears_max_angle, bool is_outer)
{
    using namespace Slic3r;
    const coordf_t angle_threshold = (180 - brim_ears_max_angle) * PI / 180.0;
    Points pt_ears;
    if (ear_detection_length > 0) {
        double detect_length = ear_detection_length / SCALING_FACTOR;
        Points points = obj_polygon.points;
        points.push_back(points.front());
        points = MultiPoint::_douglas_peucker(points, detect_length);
        if (points.size() > 4) {
            points.erase(points.end() - 1);
        }
        obj_polygon.points = points;
    }
    append(pt_ears, is_outer ? obj_polygon.convex_points(angle_threshold) : obj_polygon.concave_points(angle_threshold));
    return pt_ears;
}

// GLGizmoBrimEars::make_polygon(): an ear's circle on the plate, in the world.
Slic3r::ExPolygon make_polygon(const Slic3r::BrimPoint& point, const Transform3d& instance)
{
    using namespace Slic3r;
    ExPolygon point_round;
    const coord_t size_ear = scale_(point.head_front_radius);
    for (size_t i = 0; i < POLY_SIDE_COUNT; i++) {
        const double angle = (2.0 * PI * i) / POLY_SIDE_COUNT;
        point_round.contour.points.emplace_back(size_ear * cos(angle), size_ear * sin(angle));
    }
    const Vec3d pos = instance * point.pos.cast<double>();
    const int32_t pt_x = scale_(pos.x());
    const int32_t pt_y = scale_(pos.y());
    point_round.translate(Point(pt_x, pt_y));
    return point_round;
}

std::vector<Slic3r::BrimPoint> to_brim_points(const std::vector<double>& values)
{
    std::vector<Slic3r::BrimPoint> points;
    for (std::size_t index = 0; index + 3 < values.size(); index += 4) {
        points.emplace_back(float(values[index]), float(values[index + 1]), float(values[index + 2]), float(values[index + 3]));
    }
    return points;
}

} // namespace

BrimEars begin_brim_ears(const std::vector<PlateObject>& plate, int index, int instance_index, const ProfileSelection& profiles)
{
    using namespace Slic3r;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    BrimEars result;
    BrimEarsState& current = state();
    current = BrimEarsState();
    if (detail::engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        DynamicPrintConfig config;
        if (detail::select_profiles(*detail::engine().bundle, profiles, config, result.message) != SliceStatus::success) {
            result.status = SceneStatus::profile_not_found;
            return result;
        }
        Model model;
        if (!detail::load_plate(plate, config, model, result.message)) {
            result.status = SceneStatus::model_read_failed;
            return result;
        }
        if (index < 0 || std::size_t(index) >= model.objects.size()) {
            result.message = "The object is not on the plate";
            return result;
        }
        const ModelObject& object = *model.objects[std::size_t(index)];
        if (instance_index < 0 || std::size_t(instance_index) >= object.instances.size()) {
            result.message = "The copy is not on the plate";
            return result;
        }
        const ModelInstance& instance = *object.instances[std::size_t(instance_index)];
        current.instance = instance.get_matrix();
        // register_single_mesh_pick(): the model parts.
        for (const ModelVolume* volume : object.volumes) {
            if (!volume->is_model_part())
                continue;
            EarPart part;
            part.volume = volume->get_matrix();
            part.mesh = volume->mesh_ptr();
            part.raycaster = std::make_unique<AABBMesh>(part.mesh->its);
            current.parts.push_back(std::move(part));
        }
        current.first_layer = first_layer_of(object, instance);
        current.open = true;
        // on_render_input_window(): the object's brim type, else the process preset's.
        const ConfigOption* object_brim = object.config.option("brim_type");
        const BrimType brim_type = object_brim != nullptr ? static_cast<const ConfigOptionEnum<BrimType>*>(object_brim)->value
                                                          : config.opt_enum<BrimType>("brim_type");
        result.painted = brim_type == btPainted;
        result.detection_radius_max = detection_radius_max_of(current.first_layer);
        // get_brim_default_radius(): sixteen first layer lines wide.
        const double nozzle_diameter = config.option<ConfigOptionFloats>("nozzle_diameter")->get_at(0);
        result.default_head_diameter = config.get_abs_value("initial_layer_line_width", nozzle_diameter) * 16.0;
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        current = BrimEarsState();
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

BrimEarHit hit_brim_ears(const std::vector<double>& origin, const std::vector<double>& direction)
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    BrimEarHit result;
    const BrimEarsState& current = state();
    if (!current.open || origin.size() < 3 || direction.size() < 3)
        return result;
    // unproject_on_mesh2(): the hit nearest the eye among the model parts, in the object.
    const Vec3d ray_origin(origin[0], origin[1], origin[2]);
    const Vec3d ray_direction(direction[0], direction[1], direction[2]);
    double closest = std::numeric_limits<double>::max();
    Vec3d position_on_model = Vec3d::Zero();
    for (const EarPart& part : current.parts) {
        const Transform3d world = current.instance * part.volume;
        const Transform3d inverse = world.inverse();
        const Slic3r::AABBMesh::hit_result hit = part.raycaster->query_ray_hit(inverse * ray_origin, (inverse.linear() * ray_direction).normalized());
        if (!hit.is_hit())
            continue;
        const double distance = (world * hit.position() - ray_origin).norm();
        if (distance < closest) {
            closest = distance;
            position_on_model = part.volume * hit.position();
            result.hit = true;
        }
    }
    if (!result.hit)
        return result;
    result.position = {position_on_model.x(), position_on_model.y(), position_on_model.z()};
    // gizmo_event(LeftDown): the ear stands on the plate under the point.
    Vec3d world_pos = current.instance * position_on_model;
    world_pos[2] = -0.0001;
    const Vec3d object_pos = current.instance.inverse() * world_pos;
    result.ear = {object_pos.x(), object_pos.y(), object_pos.z()};
    return result;
}

std::vector<double> generate_brim_ears(const std::vector<double>& points, double max_angle, double detection_radius, double head_diameter)
{
    using namespace Slic3r;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    const BrimEarsState& current = state();
    std::vector<BrimPoint> ears = to_brim_points(points);
    if (!current.open)
        return points;
    // auto_generate() and add_point_to_cache(), which leaves out a point there already is.
    const Transform3d& trsf = current.instance;
    const auto add_point = [&](const Point& p) {
        const Vec3d world_pos = {float(p.x() * SCALING_FACTOR), float(p.y() * SCALING_FACTOR), -0.0001};
        const Vec3d object_pos = trsf.inverse() * world_pos;
        const BrimPoint point(object_pos.cast<float>(), float(head_diameter) / 2.f);
        if (std::find(ears.begin(), ears.end(), point) == ears.end())
            ears.push_back(point);
    };
    for (const ExPolygon& ex_poly : current.first_layer) {
        Polygon out_poly = ex_poly.contour;
        Polygons inner_poly = ex_poly.holes;
        polygons_reverse(inner_poly);
        Points out_points = generate_points(out_poly, float(detection_radius), float(max_angle), true);
        for (const Point& p : out_points)
            add_point(p);
        for (Polygon& pl : inner_poly) {
            Points inner_points = generate_points(pl, float(detection_radius), float(max_angle), false);
            for (const Point& p : inner_points)
                add_point(p);
        }
    }
    std::vector<double> result;
    for (const BrimPoint& ear : ears)
        result.insert(result.end(), {ear.pos.x(), ear.pos.y(), ear.pos.z(), ear.head_front_radius});
    return result;
}

std::vector<int> check_brim_ears(const std::vector<double>& points)
{
    using namespace Slic3r;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    const BrimEarsState& current = state();
    std::vector<int> invalid;
    const std::vector<BrimPoint> ears = to_brim_points(points);
    if (!current.open || ears.empty())
        return invalid;
    // find_single(): an ear that overlaps the first layer joins it, and so
    // does every ear that overlaps what joined; the others are left alone.
    ExPolygons model_pl = current.first_layer;
    std::map<int, BrimPoint> single_brim;
    for (int i = 0; i < int(ears.size()); i++)
        single_brim.emplace(i, ears[std::size_t(i)]);
    unsigned int index = 0;
    bool cyc = true;
    while (cyc) {
        index++;
        if (index > 99999999)
            break; // cycle protection
        if (single_brim.empty())
            break;
        auto end = --single_brim.end();
        for (auto it = single_brim.begin(); it != single_brim.end(); ++it) {
            ExPolygon point_pl = make_polygon(it->second, current.instance);
            if (overlaps(model_pl, point_pl)) {
                model_pl.emplace_back(point_pl);
                model_pl = union_ex(model_pl);
                it = single_brim.erase(it);
                break;
            } else {
                if (it == end)
                    cyc = false;
            }
        }
    }
    for (const auto& entry : single_brim)
        invalid.push_back(entry.first);
    return invalid;
}

void end_brim_ears()
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    state() = BrimEarsState();
}

} // namespace orcinus::orca
