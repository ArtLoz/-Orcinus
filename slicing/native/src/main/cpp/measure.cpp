// OrcaSlicer's measuring tool (GLGizmoMeasure): while it is open the desktop
// app keeps a Measure::Measuring of every volume of the selected copies, the
// feature under the mouse, and the two features the user selected, which
// Measure::get_measurement() measures. The app does the same through a
// session the engine keeps from begin_measure() to end_measure(); the
// features live in the engine because the measurement of a plane needs the
// features of the whole plane (SurfaceFeature::world_plane_features).

#include <algorithm>
#include <cmath>
#include <limits>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <vector>

#include "libslic3r/AABBMesh.hpp"
#include "libslic3r/Geometry.hpp"
#include "libslic3r/Measure.hpp"
#include "libslic3r/MeasureUtils.hpp"
#include "libslic3r/Model.hpp"

#include "engine_context.hpp"
#include "orca_engine_adapter.hpp"

namespace orcinus::orca {
namespace {

using Slic3r::Transform3d;
using Slic3r::Vec3d;
using Slic3r::Measure::SurfaceFeature;
using Slic3r::Measure::SurfaceFeatureType;

// A volume of a measured copy, as GLGizmoMeasure::register_single_mesh_pick()
// keeps it: its transformation to the world, the raycaster of its mesh, and
// its Measuring, made when it is first hit.
struct MeasuredVolume {
    int object_index{-1};
    int instance_index{-1};
    int volume_index{-1};
    Transform3d world{Transform3d::Identity()};
    std::shared_ptr<const Slic3r::TriangleMesh> mesh;
    std::unique_ptr<Slic3r::AABBMesh> raycaster;
    std::shared_ptr<Slic3r::Measure::Measuring> measuring;
};

// SelectedFeatures::Item, with the volume its feature is of.
struct Item {
    bool is_center{false};
    std::optional<SurfaceFeature> source;
    std::optional<SurfaceFeature> feature;
    int volume{-1};

    bool operator==(const Item& other) const
    {
        return is_center == other.is_center && source == other.source && feature == other.feature;
    }
    bool operator!=(const Item& other) const { return !operator==(other); }
    void reset()
    {
        is_center = false;
        source.reset();
        feature.reset();
        volume = -1;
    }
};

struct MeasureSession {
    bool open{false};
    Slic3r::Model model;
    std::vector<MeasuredVolume> volumes;
    // m_curr_feature and the volume it is of (m_last_hit_volume).
    std::optional<SurfaceFeature> current;
    int current_volume{-1};
    Item first;
    Item second;
    // The feature hover_measure() told last, whose triangles the app keeps.
    std::optional<SurfaceFeature> told;
    // m_hit_different_volumes: the volumes of the selections, the same one once.
    std::vector<int> hit_volumes;
    bool show_reset_first_tip{false};
};

MeasureSession& session()
{
    static MeasureSession current;
    return current;
}

MeasureState failure(SceneStatus status, std::string message)
{
    MeasureState result;
    result.status = status;
    result.message = std::move(message);
    return result;
}

std::vector<double> to_vector(const Vec3d& v) { return {v.x(), v.y(), v.z()}; }

// GLGizmoMeasure::is_feature_with_center()
bool is_feature_with_center(const SurfaceFeature& feature)
{
    const SurfaceFeatureType type = feature.get_type();
    return type == SurfaceFeatureType::Circle || (type == SurfaceFeatureType::Edge && feature.get_extra_point().has_value());
}

// GLGizmoMeasure::get_feature_offset(): a feature's centre, or a point's position.
Vec3d get_feature_offset(const SurfaceFeature& feature)
{
    switch (feature.get_type()) {
    case SurfaceFeatureType::Circle: return std::get<0>(feature.get_circle());
    case SurfaceFeatureType::Edge: {
        // Only polygon edges store an extra point (the polygon centre); plain edges have none.
        const std::optional<Vec3d> extra = feature.get_extra_point();
        if (extra.has_value())
            return *extra;
        const auto [pt1, pt2] = feature.get_edge();
        return 0.5 * (pt1 + pt2);
    }
    case SurfaceFeatureType::Point: return feature.get_point();
    default: return Vec3d::Zero();
    }
}

MeasuredVolume& measuring_of(MeasuredVolume& volume)
{
    if (volume.measuring == nullptr)
        volume.measuring = std::make_shared<Slic3r::Measure::Measuring>(volume.mesh->its);
    return volume;
}

// GLGizmoMeasure::update_world_plane_features(): a plane takes the features
// of the whole plane in world coordinates.
void update_world_plane_features(MeasuredVolume& volume, SurfaceFeature& feature)
{
    if (feature.get_type() != SurfaceFeatureType::Plane)
        return;
    const auto [idx, normal, pt] = feature.get_plane();
    const std::vector<SurfaceFeature>& plane_features = measuring_of(volume).measuring->get_plane_features(unsigned(idx));
    feature.world_plane_features = std::make_shared<std::vector<SurfaceFeature>>();
    for (const SurfaceFeature& plane_feature : plane_features) {
        SurfaceFeature temp(plane_feature);
        temp.translate(feature.world_tran);
        feature.world_plane_features->push_back(std::move(temp));
    }
}

// The ray's first hit on the measured volumes (unproject_on_mesh() of each
// raycaster, the nearest kept): the volume, the facet and the point in the
// volume's coordinates; and its distance along the ray.
struct VolumeHit {
    int volume{-1};
    int facet{-1};
    Vec3d position{Vec3d::Zero()};
    double distance{std::numeric_limits<double>::max()};
};

VolumeHit hit_volumes(MeasureSession& current, const Vec3d& origin, const Vec3d& direction)
{
    VolumeHit result;
    for (std::size_t index = 0; index < current.volumes.size(); ++index) {
        MeasuredVolume& volume = current.volumes[index];
        const Transform3d inverse = volume.world.inverse();
        const Vec3d local_origin = inverse * origin;
        const Vec3d local_direction = (inverse.linear() * direction).normalized();
        const Slic3r::AABBMesh::hit_result hit = volume.raycaster->query_ray_hit(local_origin, local_direction);
        if (!hit.is_hit())
            continue;
        const double distance = (volume.world * hit.position() - origin).norm();
        if (distance < result.distance) {
            result.volume = int(index);
            result.facet = hit.face();
            result.position = hit.position();
            result.distance = distance;
        }
    }
    return result;
}

// The feature under the ray (on_render()'s m_curr_feature), with its volume
// and where the ray hit it.
struct Hovered {
    std::optional<SurfaceFeature> feature;
    int volume{-1};
    Vec3d hit{Vec3d::Zero()};
    double distance{std::numeric_limits<double>::max()};
};

Hovered hovered_feature(MeasureSession& current, const Vec3d& origin, const Vec3d& direction, bool only_select_plane)
{
    Hovered result;
    const VolumeHit hit = hit_volumes(current, origin, direction);
    if (hit.volume < 0)
        return result;
    MeasuredVolume& volume = measuring_of(current.volumes[std::size_t(hit.volume)]);
    result.feature = volume.measuring->get_feature(std::size_t(hit.facet), hit.position, volume.world, only_select_plane);
    if (!result.feature.has_value())
        return result;
    result.feature->world_tran = volume.world;
    update_world_plane_features(volume, *result.feature);
    result.volume = hit.volume;
    result.hit = volume.world * hit.position;
    result.distance = hit.distance;
    return result;
}

// The point of point selection on the feature under the ray (on_render()'s
// m_curr_point_on_feature_position): a point itself; the point of an edge
// where the ray passes it (the desktop app takes the height along the edge
// where the ray hits a thin cylinder about it); where the ray meets a plane;
// or the point of a circle towards where the ray meets the circle's plane
// (the desktop app takes where it hits a thin torus about the circle). The
// desktop app also checks for the centre's sphere of a polygon's edge or a
// circle (m_hover_id == GripperType::POINT, POINT_ID), which never matches,
// as no raycaster of that id is registered for them.
std::optional<Vec3d> point_on_feature(const SurfaceFeature& feature, const Vec3d& hit, const Vec3d& origin, const Vec3d& direction)
{
    const Vec3d dir = direction.normalized();
    switch (feature.get_type()) {
    case SurfaceFeatureType::Point: return feature.get_point();
    case SurfaceFeatureType::Edge: {
        // The closest points of the edge's line and the ray, clamped to the edge.
        const auto [from, to] = feature.get_edge();
        const Vec3d u = to - from;
        const Vec3d w = from - origin;
        const double a = u.dot(u);
        const double b = u.dot(dir);
        const double d = u.dot(w);
        const double e = dir.dot(w);
        const double denominator = a - b * b;
        double s = denominator > 1e-12 ? (b * e - d) / denominator : 0.0;
        s = std::clamp(s, 0.0, 1.0);
        return Vec3d(from + s * u);
    }
    case SurfaceFeatureType::Plane: return hit;
    case SurfaceFeatureType::Circle: {
        const auto [center, radius, normal] = feature.get_circle();
        const double along = dir.dot(normal);
        const Vec3d on_plane = std::abs(along) > 1e-12 ? Vec3d(origin + dir * ((center - origin).dot(normal) / along)) : hit;
        const Eigen::Hyperplane<double, 3> plane(normal, center);
        const Transform3d local_to_model_matrix = Slic3r::Geometry::translation_transform(center) * Eigen::Quaternion<double>::FromTwoVectors(Vec3d::UnitZ(), normal);
        const Vec3d local_proj = local_to_model_matrix.inverse() * plane.projection(on_plane);
        double angle = std::atan2(local_proj.y(), local_proj.x());
        if (angle < 0.0)
            angle += 2.0 * double(M_PI);
        const Vec3d local_pos = radius * Vec3d(std::cos(angle), std::sin(angle), 0.0);
        return Vec3d(local_to_model_matrix * local_pos);
    }
    default: return std::nullopt;
    }
}

// The centre or point a selection's sphere stands at (on_render()'s
// SEL_SPHERE_1 and SEL_SPHERE_2), when it has one: a point of point
// selection, a point feature, a centre, or the centre of a feature with one.
std::optional<Vec3d> sphere_of(const Item& item)
{
    if (!item.feature.has_value())
        return std::nullopt;
    if (item.is_center || item.feature->get_type() == SurfaceFeatureType::Point)
        return get_feature_offset(*item.feature);
    if (is_feature_with_center(*item.feature))
        return get_feature_offset(*item.feature);
    return std::nullopt;
}

// Which selection's sphere the ray meets first, 0 for none. The desktop app
// draws the spheres over the scene and picks them before the volumes, so a
// sphere behind a volume is picked as well.
int hovered_sphere(const MeasureSession& current, const Vec3d& origin, const Vec3d& direction, double sphere_radius)
{
    const Vec3d dir = direction.normalized();
    int result = 0;
    double nearest = std::numeric_limits<double>::max();
    for (int id : {1, 2}) {
        const std::optional<Vec3d> center = sphere_of(id == 1 ? current.first : current.second);
        if (!center.has_value())
            continue;
        const Vec3d to_center = *center - origin;
        const double along = to_center.dot(dir);
        if ((to_center - dir * along).norm() > sphere_radius || along < 0.0)
            continue;
        if (along < nearest) {
            nearest = along;
            result = id;
        }
    }
    return result;
}

// GLGizmoMeasure.cpp's MEASURE_PLNE_NORMAL_OFFSET
constexpr float MEASURE_PLANE_NORMAL_OFFSET = 0.05f;

MeasureFeature to_feature(const SurfaceFeature& feature, const MeasureSession& current, int volume)
{
    MeasureFeature result;
    result.type = int(feature.get_type());
    result.pt1 = to_vector(feature.get_pt1());
    result.pt2 = to_vector(feature.get_pt2());
    if (feature.get_pt3().has_value())
        result.pt3 = to_vector(*feature.get_pt3());
    result.value = feature.get_value();
    if (feature.get_type() == SurfaceFeatureType::Plane && volume >= 0 && std::size_t(volume) < current.volumes.size()) {
        // init_plane_glmodel(): init_plane_data() lifts the plane's triangles
        // off the mesh by MEASURE_PLNE_NORMAL_OFFSET; the canvas draws them in
        // the world.
        const MeasuredVolume& measured = current.volumes[std::size_t(volume)];
        if (measured.measuring != nullptr) {
            const auto [idx, normal, pt] = feature.get_plane();
            const indexed_triangle_set& its = measured.measuring->get_its();
            for (int triangle : measured.measuring->get_plane_triangle_indices(idx)) {
                const auto& corners = its.indices[std::size_t(triangle)];
                const auto& v0 = its.vertices[std::size_t(corners[0])];
                const auto& v1 = its.vertices[std::size_t(corners[1])];
                const auto& v2 = its.vertices[std::size_t(corners[2])];
                const Slic3r::Vec3f n = (v1 - v0).cross(v2 - v0).normalized();
                for (const auto* corner : {&v0, &v1, &v2}) {
                    const Vec3d vertex = measured.world * (*corner + n * MEASURE_PLANE_NORMAL_OFFSET).cast<double>();
                    result.plane_triangles.push_back(float(vertex.x()));
                    result.plane_triangles.push_back(float(vertex.y()));
                    result.plane_triangles.push_back(float(vertex.z()));
                }
            }
        }
    }
    return result;
}

MeasureItem to_item(const Item& item, const MeasureSession& current)
{
    MeasureItem result;
    if (!item.feature.has_value())
        return result;
    result.selected = true;
    result.is_center = item.is_center;
    result.feature = to_feature(*item.feature, current, item.volume);
    if (item.source.has_value() && *item.source != *item.feature)
        result.source = to_feature(*item.source, current, item.volume);
    else
        result.source = result.feature;
    const MeasuredVolume* volume = item.volume >= 0 && std::size_t(item.volume) < current.volumes.size() ? &current.volumes[std::size_t(item.volume)] : nullptr;
    if (volume != nullptr) {
        result.object_index = volume->object_index;
        result.volume_index = volume->volume_index;
    }
    return result;
}

// update_measurement_result() and the window's state.
MeasureState describe(const MeasureSession& current)
{
    MeasureState result;
    result.status = SceneStatus::success;
    result.first = to_item(current.first, current);
    result.second = to_item(current.second, current);
    result.show_reset_first_tip = current.show_reset_first_tip;
    result.hit_volumes = int(current.hit_volumes.size());
    // is_two_volume_in_same_model_object()
    result.same_object = current.hit_volumes.size() == 2 &&
        current.volumes[std::size_t(current.hit_volumes[0])].object_index == current.volumes[std::size_t(current.hit_volumes[1])].object_index;
    std::optional<Slic3r::Measure::MeasurementResult> measurement;
    if (current.first.feature.has_value() && current.second.feature.has_value()) {
        measurement = Slic3r::Measure::get_measurement(*current.first.feature, *current.second.feature, true);
        const Slic3r::Measure::AssemblyAction action = Slic3r::Measure::get_assembly_action(*current.first.feature, *current.second.feature);
        result.can_set_to_parallel = action.can_set_to_parallel;
        result.can_set_to_center_coincidence = action.can_set_to_center_coincidence;
        result.can_set_feature_1_reverse_rotation = action.can_set_feature_1_reverse_rotation;
        result.can_set_feature_2_reverse_rotation = action.can_set_feature_2_reverse_rotation;
        result.can_around_center_of_faces = action.can_around_center_of_faces;
        result.has_parallel_distance = action.has_parallel_distance;
        result.parallel_distance = action.parallel_distance;
        result.can_set_xyz_distance = Slic3r::Measure::can_set_xyz_distance(*current.first.feature, *current.second.feature);
    } else if (current.first.feature.has_value() && current.first.feature->get_type() == SurfaceFeatureType::Circle) {
        measurement = Slic3r::Measure::get_measurement(*current.first.feature, SurfaceFeature(std::get<0>(current.first.feature->get_circle())));
    }
    if (measurement.has_value()) {
        if (measurement->angle.has_value()) {
            const Slic3r::Measure::AngleAndEdges& angle = *measurement->angle;
            result.has_angle = true;
            result.angle = angle.angle;
            result.angle_center = to_vector(angle.center);
            result.angle_edge_1 = {angle.e1.first.x(), angle.e1.first.y(), angle.e1.first.z(), angle.e1.second.x(), angle.e1.second.y(), angle.e1.second.z()};
            result.angle_edge_2 = {angle.e2.first.x(), angle.e2.first.y(), angle.e2.first.z(), angle.e2.second.x(), angle.e2.second.y(), angle.e2.second.z()};
            result.angle_radius = angle.radius;
            result.angle_coplanar = angle.coplanar;
        }
        if (measurement->distance_infinite.has_value()) {
            result.has_infinite = true;
            result.infinite = measurement->distance_infinite->dist;
            result.infinite_from = to_vector(measurement->distance_infinite->from);
            result.infinite_to = to_vector(measurement->distance_infinite->to);
        }
        if (measurement->distance_strict.has_value()) {
            result.has_strict = true;
            result.strict = measurement->distance_strict->dist;
            result.strict_from = to_vector(measurement->distance_strict->from);
            result.strict_to = to_vector(measurement->distance_strict->to);
        }
        if (measurement->distance_xyz.has_value())
            result.distance_xyz = to_vector(*measurement->distance_xyz);
    }
    if (current.current.has_value())
        result.hovered = to_feature(*current.current, current, current.current_volume);
    return result;
}

// requires_sphere_raycaster_for_picking() is the app's: the spheres are
// where sphere_of() puts them.

// reset_feature1(): the second selection becomes the first, or the first goes.
void reset_feature1(MeasureSession& current)
{
    if (current.second.feature.has_value()) {
        if (current.hit_volumes.size() == 2)
            current.hit_volumes[0] = current.hit_volumes[1];
        current.first = current.second;
        current.second.reset();
        if (current.hit_volumes.size() == 2)
            current.hit_volumes.erase(current.hit_volumes.begin() + 1);
        current.show_reset_first_tip = true;
    } else {
        current.first.reset();
        current.show_reset_first_tip = false;
        if (current.hit_volumes.size() == 1)
            current.hit_volumes.clear();
    }
}

// reset_feature2()
void reset_feature2(MeasureSession& current)
{
    if (current.hit_volumes.size() == 2)
        current.hit_volumes.erase(current.hit_volumes.begin() + 1);
    current.second.reset();
    current.show_reset_first_tip = false;
}

// on_render()'s m_hit_different_volumes once a selection is made on [volume].
void note_hit_volume(MeasureSession& current, int volume)
{
    if (current.second.feature.has_value()) {
        if (current.hit_volumes.size() >= 1) {
            if (volume == current.hit_volumes[0]) {
                if (current.hit_volumes.size() == 2) // hit same volume
                    current.hit_volumes.erase(current.hit_volumes.begin() + 1);
            } else {
                if (current.hit_volumes.size() == 2)
                    current.hit_volumes[1] = volume;
                else
                    current.hit_volumes.push_back(volume);
            }
        }
    } else if (current.first.feature.has_value()) {
        if (current.hit_volumes.empty())
            current.hit_volumes.push_back(volume);
    }
}

} // namespace

MeasureState begin_measure(const std::vector<PlateObject>& plate, const std::vector<int>& selection, const ProfileSelection& profiles)
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().bundle == nullptr)
        return failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    MeasureSession& current = session();
    current = MeasureSession();
    try {
        Slic3r::DynamicPrintConfig config;
        std::string message;
        if (detail::select_profiles(*detail::engine().bundle, profiles, config, message) != SliceStatus::success)
            return failure(SceneStatus::profile_not_found, message);
        if (!detail::load_plate(plate, config, current.model, message))
            return failure(SceneStatus::model_read_failed, message);
        // register_single_mesh_pick(): every selected volume.
        for (std::size_t triple = 0; triple + 2 < selection.size(); triple += 3) {
            const int object_index = selection[triple];
            const int instance_index = selection[triple + 1];
            const int selected_volume = selection[triple + 2];
            if (object_index < 0 || std::size_t(object_index) >= current.model.objects.size())
                continue;
            const Slic3r::ModelObject& object = *current.model.objects[std::size_t(object_index)];
            if (instance_index < 0 || std::size_t(instance_index) >= object.instances.size())
                continue;
            const Slic3r::ModelInstance& instance = *object.instances[std::size_t(instance_index)];
            for (std::size_t volume_index = 0; volume_index < object.volumes.size(); ++volume_index) {
                if (selected_volume >= 0 && std::size_t(selected_volume) != volume_index)
                    continue;
                const Slic3r::ModelVolume& volume = *object.volumes[volume_index];
                MeasuredVolume measured;
                measured.object_index = object_index;
                measured.instance_index = instance_index;
                measured.volume_index = int(volume_index);
                measured.world = instance.get_matrix() * volume.get_matrix();
                measured.mesh = volume.mesh_ptr();
                measured.raycaster = std::make_unique<Slic3r::AABBMesh>(measured.mesh->its);
                current.volumes.push_back(std::move(measured));
            }
        }
        current.open = true;
        return describe(current);
    } catch (const std::exception& error) {
        current = MeasureSession();
        return failure(SceneStatus::model_read_failed, error.what());
    }
}

MeasureState hover_measure(const MeasureRay& ray)
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    MeasureSession& current = session();
    if (!current.open)
        return failure(SceneStatus::model_read_failed, "The measuring tool is not open");
    try {
        const Vec3d origin(ray.origin[0], ray.origin[1], ray.origin[2]);
        const Vec3d direction(ray.direction[0], ray.direction[1], ray.direction[2]);
        Hovered hovered = hovered_feature(current, origin, direction, ray.only_select_plane);
        MeasureState result;
        const int sphere = hovered_sphere(current, origin, direction, ray.sphere_radius);
        if (sphere != 0) {
            // Skip feature detection if hovering on a selected point/center
            current.current.reset();
            current.current_volume = -1;
            current.told.reset();
            result.status = SceneStatus::success;
            result.hover_only = true;
            result.hovered_sphere = sphere;
            return result;
        }
        current.current = hovered.feature;
        current.current_volume = hovered.volume;
        result = MeasureState();
        result.status = SceneStatus::success;
        result.hover_only = true;
        if (hovered.feature.has_value()) {
            const bool unchanged = current.told.has_value() && *current.told == *hovered.feature;
            if (unchanged) {
                result.hovered = to_feature(SurfaceFeature(*hovered.feature), current, -1);
                result.hovered_unchanged = true;
            } else {
                result.hovered = to_feature(*hovered.feature, current, hovered.volume);
            }
        }
        current.told = hovered.feature;
        if (ray.point_selection && hovered.feature.has_value()) {
            if (const std::optional<Vec3d> point = point_on_feature(*hovered.feature, hovered.hit, origin, direction); point.has_value())
                result.hovered_point = to_vector(*point);
        }
        return result;
    } catch (const std::exception& error) {
        return failure(SceneStatus::model_read_failed, error.what());
    }
}

MeasureState select_measure(const MeasureRay& ray)
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    MeasureSession& current = session();
    if (!current.open)
        return failure(SceneStatus::model_read_failed, "The measuring tool is not open");
    try {
        const Vec3d origin(ray.origin[0], ray.origin[1], ray.origin[2]);
        const Vec3d direction(ray.direction[0], ray.direction[1], ray.direction[2]);
        Hovered hovered = hovered_feature(current, origin, direction, ray.only_select_plane);
        const int sphere = hovered_sphere(current, origin, direction, ray.sphere_radius);
        if (sphere == 0 && !hovered.feature.has_value()) {
            // A tap off the volumes changes nothing.
            current.current.reset();
            current.current_volume = -1;
            return describe(current);
        }

        // on_mouse()'s detect_current_item()
        Item item;
        if (sphere == 1 || sphere == 2) {
            const Item& selected = sphere == 1 ? current.first : current.second;
            if (selected.is_center)
                // mouse is hovering over a selected center
                item = Item{true, selected.source, SurfaceFeature(get_feature_offset(*selected.source)), selected.volume};
            else if (is_feature_with_center(*selected.feature))
                // mouse is hovering over a unselected center
                item = Item{true, selected.feature, SurfaceFeature(get_feature_offset(*selected.feature)), selected.volume};
            else
                // mouse is hovering over a point
                item = selected;
        } else if (!ray.point_selection) {
            item = Item{false, hovered.feature, hovered.feature, hovered.volume};
        } else {
            const std::optional<Vec3d> point = point_on_feature(*hovered.feature, hovered.hit, origin, direction);
            if (!point.has_value())
                return describe(current);
            item = Item{false, hovered.feature, SurfaceFeature(*point), hovered.volume};
            const MeasuredVolume& volume = current.volumes[std::size_t(hovered.volume)];
            item.feature->origin_surface_feature = std::make_shared<SurfaceFeature>(Vec3d(volume.world.inverse() * (*point)));
            item.feature->world_tran = volume.world;
        }
        const int hit_volume = item.volume;

        if (current.first.feature.has_value()) {
            if (current.first != item) {
                bool processed = false;
                if (item.is_center) {
                    if (item.source == current.first.feature) {
                        // switch 1st selection from feature to its center
                        current.first = item;
                        processed = true;
                    } else if (item.source == current.second.feature) {
                        // switch 2nd selection from feature to its center
                        current.second = item;
                        processed = true;
                    }
                } else if (is_feature_with_center(*item.feature)) {
                    if (current.first.is_center && current.first.source == item.feature) {
                        // switch 1st selection from center to its feature
                        current.first = item;
                        processed = true;
                    } else if (current.second.is_center && current.second.source == item.feature) {
                        // switch 2nd selection from center to its feature
                        current.second = item;
                        processed = true;
                    }
                }
                if (!processed) {
                    if (current.second == item) {
                        // 2nd feature deselection
                        reset_feature2(current);
                    } else {
                        // 2nd feature selection
                        current.second = item;
                        note_hit_volume(current, hit_volume);
                    }
                }
            } else {
                // promote 2nd feature to 1st feature, or the 1st feature deselection
                reset_feature1(current);
            }
        } else {
            // 1st feature selection
            current.first = item;
            current.show_reset_first_tip = false;
            note_hit_volume(current, hit_volume);
        }
        if (sphere != 0) {
            // on_render() detects no feature while a sphere is hovered.
            current.current.reset();
            current.current_volume = -1;
        } else {
            current.current = hovered.feature;
            current.current_volume = hovered.volume;
        }
        // The full state tells the hovered feature with its triangles.
        current.told = current.current;
        return describe(current);
    } catch (const std::exception& error) {
        return failure(SceneStatus::model_read_failed, error.what());
    }
}

MeasureState reset_measure(int selection)
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    MeasureSession& current = session();
    if (!current.open)
        return failure(SceneStatus::model_read_failed, "The measuring tool is not open");
    switch (selection) {
    case 1: reset_feature1(current); break;
    case 2: reset_feature2(current); break;
    default:
        // reset_all_feature()
        reset_feature2(current);
        reset_feature1(current);
        current.show_reset_first_tip = false;
        current.hit_volumes.clear();
        break;
    }
    current.told = current.current;
    return describe(current);
}

void end_measure()
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    session() = MeasureSession();
}

} // namespace orcinus::orca
