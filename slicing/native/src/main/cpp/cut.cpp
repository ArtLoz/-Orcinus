// The cut gizmo's view of its plane, ported from OrcaSlicer's GLGizmoCut3D and
// the object clipper it draws the section with (ObjectClipper and MeshClipper
// of the desktop GUI): while the gizmo is open the desktop app keeps the
// object's meshes and works out, for every position of the plane, the size of
// what it cuts, whether it cuts the object at all, and the outline of the
// section. The app does the same through a session the engine keeps from
// begin_cut() to end_cut(); the cut itself is ObjectEdit::cut of edit_object().

#include <algorithm>
#include <cfloat>
#include <cmath>
#include <limits>
#include <string>
#include <vector>

#include "libslic3r/BoundingBox.hpp"
#include "libslic3r/ClipperUtils.hpp"
#include "libslic3r/ExPolygon.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Tesselate.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "libslic3r/TriangleMeshSlicer.hpp"

#include "engine_context.hpp"
#include "orca_engine_adapter.hpp"

namespace orcinus::orca {
namespace {

// ClippingPlane of slic3r/GUI/MeshUtils.hpp: the points p with
// normal . p = offset; distance() is positive on the side against the normal.
class ClippingPlane {
    std::array<double, 4> m_data;

public:
    ClippingPlane(const Slic3r::Vec3d& direction, double offset)
    {
        const Slic3r::Vec3d norm_dir = direction.normalized();
        m_data[0] = norm_dir.x();
        m_data[1] = norm_dir.y();
        m_data[2] = norm_dir.z();
        m_data[3] = offset;
    }

    double distance(const Slic3r::Vec3d& pt) const { return (-get_normal().dot(pt) + m_data[3]); }
    Slic3r::Vec3d get_normal() const { return Slic3r::Vec3d(m_data[0], m_data[1], m_data[2]); }
    const std::array<double, 4>& get_data() const { return m_data; }
};

// The object the gizmo is open on, alone in a model of its own.
struct CutSession {
    bool open{false};
    Slic3r::Model model;
    int instance{0};
    // How many contours were written, which names each one anew so the 3D
    // view reads it again.
    std::size_t writes{0};
};

CutSession& session()
{
    static CutSession current;
    return current;
}

Slic3r::Transform3d matrix_of(const std::vector<double>& elements)
{
    Slic3r::Transform3d matrix = Slic3r::Transform3d::Identity();
    if (elements.size() == 16) {
        std::copy(elements.begin(), elements.end(), matrix.data());
    }
    return matrix;
}

// MeshClipper::recalculate_triangles() of a clipper with the object
// clipper's limiting plane: the islands of the section of its, placed by
// trafo, the filled section added to section and the outline of each island,
// contour_width wide, to contour, in world coordinates.
void clip(
    const indexed_triangle_set& its,
    const Slic3r::Geometry::Transformation& m_trafo,
    const ClippingPlane& m_plane,
    const ClippingPlane& m_limiting_plane,
    const double m_contour_width,
    Slic3r::ExPolygons& islands,
    indexed_triangle_set& section,
    indexed_triangle_set& contour
)
{
    using namespace Slic3r;

    auto plane_mesh = Eigen::Hyperplane<double, 3>(m_plane.get_normal(), -m_plane.distance(Vec3d::Zero())).transform(m_trafo.get_matrix().inverse());
    const Vec3d up = plane_mesh.normal();
    const float height_mesh = -plane_mesh.offset();

    // Now do the cutting
    MeshSlicingParams slicing_params;
    slicing_params.trafo.rotate(Eigen::Quaternion<double, Eigen::DontAlign>::FromTwoVectors(up, Vec3d::UnitZ()));

    ExPolygons expolys = union_ex(slice_mesh(its, height_mesh, slicing_params));

    // Triangulate and rotate the cut into world coords:
    Eigen::Quaterniond q;
    q.setFromTwoVectors(Vec3d::UnitZ(), up);
    Transform3d tr = Transform3d::Identity();
    tr.rotate(q);
    tr = m_trafo.get_matrix() * tr;

    {
        // Now remove whatever ended up below the limiting plane (e.g. sinking objects).
        // First transform the limiting plane from world to mesh coords.
        // Note that inverse of tr transforms the plane from world to horizontal.
        const Vec3d normal_old = m_limiting_plane.get_normal().normalized();
        const Vec3d normal_new = (tr.matrix().block<3,3>(0,0).transpose() * normal_old).normalized();

        // normal_new should now be the plane normal in mesh coords. To find the offset,
        // transform a point and set offset so it belongs to the transformed plane.
        Vec3d pt = Vec3d::Zero();
        const double plane_offset = m_limiting_plane.get_data()[3];
        if (std::abs(normal_old.z()) > 0.5) // normal is normalized, at least one of the coords if larger than sqrt(3)/3 = 0.57
            pt.z() = - plane_offset / normal_old.z();
        else if (std::abs(normal_old.y()) > 0.5)
            pt.y() = - plane_offset / normal_old.y();
        else
            pt.x() = - plane_offset / normal_old.x();
        pt = tr.inverse() * pt;
        const double offset = -(normal_new.dot(pt));

        if (std::abs(normal_old.dot(m_plane.get_normal().normalized())) > 0.99) {
            // The cuts are parallel, show all or nothing.
            if (normal_old.dot(m_plane.get_normal().normalized()) < 0.0 && offset < height_mesh)
                expolys.clear();
        } else {
            // The cut is a horizontal plane defined by z=height_mesh.
            // ax+by+e=0 is the line of intersection with the limiting plane.
            // Normalized so a^2 + b^2 = 1.
            const double len = std::hypot(normal_new.x(), normal_new.y());
            if (len == 0.)
                return;
            const double a = normal_new.x() / len;
            const double b = normal_new.y() / len;
            const double e = (normal_new.z() * height_mesh + offset) / len;

            // We need a half-plane to limit the cut. Get angle of the intersecting line.
            double angle = (b != 0.0) ? std::atan(-a / b) : ((a < 0.0) ? -0.5 * M_PI : 0.5 * M_PI);
            if (b > 0) // select correct half-plane
                angle += M_PI;

            // We'll take a big rectangle above x-axis and rotate and translate
            // it so it lies on our line. This will be the figure to subtract
            // from the cut. The coordinates must not overflow after the transform,
            // make the rectangle a bit smaller.
            const coord_t size = (std::numeric_limits<coord_t>::max()/2 - scale_(std::max(std::abs(e * a), std::abs(e * b)))) / 4;
            Polygons ep {Polygon({Point(-size, 0), Point(size, 0), Point(size, 2*size), Point(-size, 2*size)})};
            ep.front().rotate(angle);
            ep.front().translate(scale_(-e * a), scale_(-e * b));
            expolys = diff_ex(expolys, ep);
        }
    }

    tr.pretranslate(0.001 * m_plane.get_normal().normalized()); // to avoid z-fighting
    Transform3d tr2 = tr;
    tr2.pretranslate(0.002 * m_plane.get_normal().normalized());

    std::vector<Vec2f> triangles2d;

    for (const ExPolygon& exp : expolys) {
        triangles2d.clear();

        // m_fill_cut
        triangles2d = triangulate_expolygon_2f(exp, m_trafo.get_matrix().matrix().determinant() < 0.);
        for (auto it = triangles2d.cbegin(); it != triangles2d.cend(); it = it + 3) {
            const int first = int(section.vertices.size());
            for (int corner = 0; corner < 3; ++corner) {
                section.vertices.emplace_back((tr * Vec3d((*(it + corner)).x(), (*(it + corner)).y(), height_mesh)).cast<float>());
            }
            section.indices.emplace_back(first, first + 1, first + 2);
        }
        triangles2d.clear();

        if (m_contour_width != 0. && ! exp.contour.empty()) {
            // The contours must not scale with the object. Check the scale factor
            // in the respective directions, create a scaled copy of the ExPolygon
            // offset it and then unscale the result again.

            Transform3d t = tr;
            t.translation() = Vec3d::Zero();
            double scale_x = (t * Vec3d::UnitX()).norm();
            double scale_y = (t * Vec3d::UnitY()).norm();

            // To prevent overflow after scaling, downscale the input if needed:
            double extra_scale = 1.;
            coord_t limit = coord_t(std::min(std::numeric_limits<coord_t>::max() / (2. * std::max(1., scale_x)), std::numeric_limits<coord_t>::max() / (2. * std::max(1., scale_y))));
            coord_t max_coord = 0;
            for (const Point& pt : exp.contour)
                max_coord = std::max(max_coord, std::max(std::abs(pt.x()), std::abs(pt.y())));
            if (max_coord + m_contour_width >= limit)
                extra_scale = 0.9 * double(limit) / max_coord;

            ExPolygon exp_copy = exp;
            if (extra_scale != 1.)
                exp_copy.scale(extra_scale);
            exp_copy.scale(scale_x, scale_y);

            ExPolygons expolys_exp = offset_ex(exp_copy, scale_(m_contour_width));
            expolys_exp = diff_ex(expolys_exp, ExPolygons({exp_copy}));

            for (ExPolygon& e : expolys_exp) {
                e.scale(1./scale_x, 1./scale_y);
                if (extra_scale != 1.)
                    e.scale(1./extra_scale);
            }

            triangles2d = triangulate_expolygons_2f(expolys_exp, m_trafo.get_matrix().matrix().determinant() < 0.);
            for (auto it = triangles2d.cbegin(); it != triangles2d.cend(); it = it + 3) {
                const int first = int(contour.vertices.size());
                for (int corner = 0; corner < 3; ++corner) {
                    contour.vertices.emplace_back((tr2 * Vec3d((*(it + corner)).x(), (*(it + corner)).y(), height_mesh)).cast<float>());
                }
                contour.indices.emplace_back(first, first + 1, first + 2);
            }
        }

        islands.push_back(exp);
    }
}

}  // namespace

CutObject begin_cut(const PlateObject& object, const int instance, const ProfileSelection& profiles)
{
    CutObject result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        Slic3r::DynamicPrintConfig config;
        if (detail::select_profiles(*detail::engine().bundle, profiles, config, result.message) != SliceStatus::success) {
            result.status = SceneStatus::profile_not_found;
            return result;
        }
        CutSession& current = session();
        current.open = false;
        current.model.clear_objects();
        if (!detail::load_plate({object}, config, current.model, result.message) || current.model.objects.empty()) {
            return result;
        }
        const Slic3r::ModelObject& loaded = *current.model.objects.front();
        if (instance < 0 || std::size_t(instance) >= loaded.instances.size()) {
            result.message = "The object has no such copy";
            return result;
        }
        current.instance = instance;
        current.writes = 0;
        current.open = true;

        // GLGizmoCut3D::bounding_box(): the convex hulls of the copy's solid
        // parts (GLVolume::transformed_convex_hull_bounding_box).
        Slic3r::BoundingBoxf3 box;
        const Slic3r::Transform3d instance_matrix = loaded.instances[std::size_t(instance)]->get_matrix();
        for (const Slic3r::ModelVolume* volume : loaded.volumes) {
            if (volume->is_model_part()) {
                box.merge(volume->get_convex_hull().transformed_bounding_box(instance_matrix * volume->get_matrix()));
            }
        }
        for (int axis = 0; axis < 3; ++axis) {
            result.min[axis] = box.min[axis];
            result.max[axis] = box.max[axis];
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

CutPlane describe_cut_plane(const std::vector<double>& plane, const std::string& mesh_prefix)
{
    CutPlane result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    CutSession& current = session();
    if (!current.open || current.model.objects.empty()) {
        result.message = "The cut gizmo is not open";
        return result;
    }
    try {
        const Slic3r::ModelObject& object = *current.model.objects.front();
        const Slic3r::ModelInstance& instance = *object.instances[std::size_t(current.instance)];
        const Slic3r::Transform3d plane_matrix = matrix_of(plane);
        const Slic3r::Vec3d plane_center = plane_matrix.translation();
        const Slic3r::Transform3d rotation_m(plane_matrix.linear());

        // transformed_bounding_box(m_plane_center, m_rotation_m)
        const Slic3r::Transform3d cut_matrix =
            Slic3r::Transform3d::Identity() * rotation_m.inverse() * Slic3r::Geometry::translation_transform(instance.get_offset() - plane_center);
        Slic3r::BoundingBoxf3 tbb;
        for (const Slic3r::ModelVolume* volume : object.volumes) {
            if (volume->is_model_part()) {
                const Slic3r::Transform3d volume_trafo = instance.get_transformation().get_matrix_no_offset() * volume->get_matrix();
                tbb.merge(volume->get_convex_hull().transformed_bounding_box(cut_matrix * volume_trafo));
            }
        }
        for (int axis = 0; axis < 3; ++axis) {
            result.min[axis] = tbb.min[axis];
            result.max[axis] = tbb.max[axis];
        }

        // update_clipper(): the plane through its centre, its normal the rotated Z
        // axis, and ObjectClipper::render_cut() over every volume of the copy,
        // the part below the plate left out.
        const Slic3r::Vec3d normal = (rotation_m * Slic3r::Vec3d::UnitZ()).normalized();
        const ClippingPlane clipping_plane(normal, normal.dot(plane_center));
        const ClippingPlane limiting_plane(Slic3r::Vec3d::UnitZ(), -Slic3r::SINKING_Z_THRESHOLD);
        // GLGizmoCut3D::m_contour_width of the planar cut.
        const double contour_width = 0.4;
        indexed_triangle_set section;
        indexed_triangle_set contour;
        for (const Slic3r::ModelVolume* volume : object.volumes) {
            Slic3r::ExPolygons islands;
            const Slic3r::Geometry::Transformation trafo = instance.get_transformation() * volume->get_transformation();
            clip(volume->mesh().its, trafo, clipping_plane, limiting_plane, contour_width, islands, section, contour);
            // MeshClipper::has_valid_contour()
            if (std::any_of(islands.begin(), islands.end(), [](const Slic3r::ExPolygon& island) { return !island.empty(); })) {
                result.valid_contour = true;
            }
        }
        const std::string name = mesh_prefix + "-" + std::to_string(current.writes++);
        if (!contour.indices.empty() && detail::write_mesh(contour, name + ".mesh")) {
            result.contour = name + ".mesh";
        }
        if (!section.indices.empty() && detail::write_mesh(section, name + "-section.mesh")) {
            result.section = name + "-section.mesh";
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

void end_cut()
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    CutSession& current = session();
    current.open = false;
    current.model.clear_objects();
}

}  // namespace orcinus::orca
