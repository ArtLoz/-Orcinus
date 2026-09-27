// The cut gizmo's view of its plane, ported from OrcaSlicer's GLGizmoCut3D and
// the object clipper it draws the section with (ObjectClipper and MeshClipper
// of the desktop GUI): while the gizmo is open the desktop app keeps the
// object's meshes and works out, for every position of the plane, the size of
// what it cuts, whether it cuts the object at all, and the outline of the
// section. The app does the same through a session the engine keeps from
// begin_cut() to end_cut(); the cut itself is ObjectEdit::cut of edit_object().

#include <algorithm>
#include <array>
#include <cfloat>
#include <cmath>
#include <limits>
#include <map>
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
    // GLGizmoCut3D::m_bounding_box: the copy's solid parts in the world.
    Slic3r::BoundingBoxf3 bounding_box;
    // How many contours were written, which names each one anew so the 3D
    // view reads it again.
    std::size_t writes{0};
    // The connector shapes written for the 3D view, by name.
    std::map<std::string, std::string> shapes;
};

// What one clipper of the object clipper cut (MeshClipper::ClipResult): the
// islands of the section in the plane's frame, which trafo places.
struct ClipResult {
    Slic3r::Transform3d trafo{Slic3r::Transform3d::Identity()};
    Slic3r::ExPolygons islands;
    std::vector<Slic3r::BoundingBox> boxes;
};

// ObjectClipper::is_projection_inside_cut(): the island the projection of point
// on the plane lies in, counted over every clipper; -1 for none.
int is_projection_inside_cut(const std::vector<ClipResult>& clippers, const Slic3r::Vec3d& point_in)
{
    int idx_offset = 0;
    for (const ClipResult& result : clippers) {
        // MeshClipper::is_projection_inside_cut()
        const Slic3r::Vec3d point = result.trafo.inverse() * point_in;
        const Slic3r::Point pt_2d = Slic3r::Point::new_scale(Slic3r::Vec2d(point.x(), point.y()));
        for (int i = 0; i < int(result.islands.size()); ++i) {
            if (result.boxes[i].contains(pt_2d) && result.islands[i].contains(pt_2d))
                return idx_offset + i;
        }
        idx_offset += int(result.islands.size());
    }
    return -1;
}

// GLGizmoCut3D::get_connector_mesh(): the shape of a connector at unit size.
indexed_triangle_set connector_mesh(const int type, const int style, const int shape, const double snap_space, const double snap_bulge)
{
    using namespace Slic3r;
    indexed_triangle_set connector_mesh;

    int   sectorCount{ 1 };
    switch (CutConnectorShape(shape)) {
    case CutConnectorShape::Triangle:
        sectorCount = 3;
        break;
    case CutConnectorShape::Square:
        sectorCount = 4;
        break;
    case CutConnectorShape::Circle:
        sectorCount = 360;
        break;
    case CutConnectorShape::Hexagon:
        sectorCount = 6;
        break;
    default:
        break;
    }

    if (CutConnectorType(type) == CutConnectorType::Snap)
        connector_mesh = its_make_snap(1.0, 1.0, float(snap_space), float(snap_bulge));
    else if (CutConnectorStyle(style) == CutConnectorStyle::Prism)
        connector_mesh = its_make_cylinder(1.0, 1.0, (2 * PI / sectorCount));
    else if (CutConnectorType(type) == CutConnectorType::Plug)
        connector_mesh = its_make_frustum(1.0, 1.0, (2 * PI / sectorCount));
    else
        connector_mesh = its_make_frustum_dowel(1.0, 1.0, sectorCount);

    return connector_mesh;
}

// The name the shape of a connector is written under.
std::string shape_name(const CutConnectorData& connector, const double snap_space, const double snap_bulge)
{
    std::string name = std::to_string(connector.type) + "-" + std::to_string(connector.style) + "-" + std::to_string(connector.shape);
    if (Slic3r::CutConnectorType(connector.type) == Slic3r::CutConnectorType::Snap) {
        name += "-" + std::to_string(int(std::lround(snap_space * 1000.0))) + "-" + std::to_string(int(std::lround(snap_bulge * 1000.0)));
    }
    return name;
}

// check_and_update_connectors_state() of the planar cut, with
// is_conflict_for_connector() and is_outside_of_cut_contour(): which
// connectors cannot be cut with, and why.
void check_connectors(
    const std::vector<CutConnectorData>& connectors,
    const Slic3r::Transform3d& m_rotation_m,
    const Slic3r::BoundingBoxf3& m_bounding_box,
    const std::vector<ClipResult>& clippers,
    const double snap_space,
    const double snap_bulge,
    CutPlane& result
)
{
    using namespace Slic3r;
    const auto is_outside_of_cut_contour = [&](const CutConnectorData& cur_connector, const Vec3d& cur_pos) {
        // check if connector pos is out of clipping plane
        if (is_projection_inside_cut(clippers, cur_pos) == -1) {
            result.outside_cut_contour++;
            return true;
        }

        // check if connector bottom contour is out of clipping plane
        const CutConnectorShape shape = CutConnectorShape(cur_connector.shape);
        const int   sectorCount = shape == CutConnectorShape::Triangle  ? 3 :
                                  shape == CutConnectorShape::Square    ? 4 :
                                  shape == CutConnectorShape::Circle    ? 60: // supposably, 60 points are enough for conflict detection
                                  shape == CutConnectorShape::Hexagon   ? 6 : 1 ;

        indexed_triangle_set mesh;
        auto& vertices = mesh.vertices;
        vertices.reserve(sectorCount + 1);

        float fa = 2 * PI / sectorCount;
        auto vec = Eigen::Vector2f(0, float(cur_connector.radius));
        for (float angle = 0; angle < 2.f * PI; angle += fa) {
            Vec2f p = Eigen::Rotation2Df(angle) * vec;
            vertices.emplace_back(Vec3f(p(0), p(1), 0.f));
        }
        its_transform(mesh, Geometry::translation_transform(cur_pos) * m_rotation_m);

        for (const Vec3f& vertex : vertices) {
            if (is_projection_inside_cut(clippers, vertex.cast<double>()) == -1) {
                result.outside_cut_contour++;
                return true;
            }
        }

        return false;
    };

    for (std::size_t idx = 0; idx < connectors.size(); ++idx) {
        const CutConnectorData& cur_connector = connectors[idx];
        const Vec3d cur_pos(cur_connector.position[0], cur_connector.position[1], cur_connector.position[2]);
        bool conflict = is_outside_of_cut_contour(cur_connector, cur_pos);
        if (!conflict) {
            const Transform3d matrix = Geometry::translation_transform(cur_pos) * m_rotation_m *
                                       Geometry::scale_transform(Vec3d(cur_connector.radius, cur_connector.radius, cur_connector.height));
            const BoundingBoxf3 cur_tbb = bounding_box(
                connector_mesh(cur_connector.type, cur_connector.style, cur_connector.shape, snap_space, snap_bulge)).transformed(matrix);

            // check if connector's bounding box is inside the object's bounding box
            if (!m_bounding_box.contains(cur_tbb)) {
                result.outside_bounding_box++;
                conflict = true;
            }
        }
        if (!conflict) {
            // check if connectors are overlapping
            for (std::size_t i = 0; i < connectors.size(); ++i) {
                if (i == idx)
                    continue;
                const CutConnectorData& connector = connectors[i];
                const Vec3d pos(connector.position[0], connector.position[1], connector.position[2]);
                if ((pos - cur_pos).norm() < connector.radius + cur_connector.radius) {
                    result.overlap = true;
                    conflict = true;
                    break;
                }
            }
        }
        if (conflict) {
            result.invalid_connectors.push_back(int(idx));
        }
    }
}

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
    Slic3r::Transform3d& trafo,
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
    trafo = tr;

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
        current.shapes.clear();
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
        current.bounding_box = box;
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

CutPlane describe_cut_plane(
    const std::vector<double>& plane,
    const std::vector<CutConnectorData>& connectors,
    const double snap_space,
    const double snap_bulge,
    const std::string& mesh_prefix
)
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
        std::vector<ClipResult> clippers;
        for (const Slic3r::ModelVolume* volume : object.volumes) {
            ClipResult clipped;
            const Slic3r::Geometry::Transformation trafo = instance.get_transformation() * volume->get_transformation();
            clip(volume->mesh().its, trafo, clipping_plane, limiting_plane, contour_width, clipped.islands, clipped.trafo, section, contour);
            // MeshClipper::has_valid_contour()
            if (std::any_of(clipped.islands.begin(), clipped.islands.end(), [](const Slic3r::ExPolygon& island) { return !island.empty(); })) {
                result.valid_contour = true;
            }
            for (const Slic3r::ExPolygon& island : clipped.islands) {
                clipped.boxes.push_back(Slic3r::get_extents(island));
            }
            clippers.push_back(std::move(clipped));
        }

        check_connectors(connectors, rotation_m, current.bounding_box, clippers, snap_space, snap_bulge, result);
        for (const CutConnectorData& connector : connectors) {
            const std::string name = shape_name(connector, snap_space, snap_bulge);
            auto written = current.shapes.find(name);
            if (written == current.shapes.end()) {
                const std::string path = mesh_prefix + "-connector-" + name + ".mesh";
                const indexed_triangle_set shape = connector_mesh(connector.type, connector.style, connector.shape, snap_space, snap_bulge);
                written = current.shapes.emplace(name, detail::write_mesh(shape, path) ? path : std::string()).first;
            }
            result.connector_meshes.push_back(written->second);
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

namespace detail {

void apply_cut_connectors(Slic3r::ModelObject& object, const ObjectCut& cut, const Slic3r::Transform3d& m_rotation_m, int& dowels_count)
{
    using namespace Slic3r;
    using namespace Slic3r::Geometry;
    if (cut.connectors.empty() || cut.instance < 0 || std::size_t(cut.instance) >= object.instances.size())
        return;
    const Vec3d instance_offset = object.instances[std::size_t(cut.instance)]->get_offset();
    const Vec3d m_cut_normal = (m_rotation_m * Vec3d::UnitZ()).normalized();

    // The gizmo keeps the connectors on the object (ModelObject::cut_connectors),
    // each where it stands on the plane, from the copy's offset.
    object.cut_connectors.clear();
    for (const CutConnectorData& data : cut.connectors) {
        const Vec3d pos = Vec3d(data.position[0], data.position[1], data.position[2]) - instance_offset;
        object.cut_connectors.emplace_back(
            pos, Transform3d::Identity(), float(data.radius), float(data.height), float(data.radius_tolerance), float(data.height_tolerance),
            float(data.z_angle),
            CutConnectorAttributes(CutConnectorType(data.type), CutConnectorStyle(data.style), CutConnectorShape(data.shape)));
    }

    // apply_connectors_in_model()
    for (CutConnector& connector : object.cut_connectors) {
        connector.rotation_m = m_rotation_m;

        if (connector.attribs.type == CutConnectorType::Dowel) {
            if (connector.attribs.style == CutConnectorStyle::Prism)
                connector.height *= 2;
            dowels_count ++;
        }
        else {
            // calculate shift of the connector center regarding to the position on the cut plane
            connector.pos += m_cut_normal * 0.5 * double(connector.height);
        }
    }

    // apply_cut_connectors(mo, _u8L("Connector"))
    size_t connector_id = object.cut_id.connectors_cnt();
    for (const CutConnector& connector : object.cut_connectors) {
        TriangleMesh mesh = TriangleMesh(connector_mesh(int(connector.attribs.type), int(connector.attribs.style), int(connector.attribs.shape),
                                                        cut.snap_space, cut.snap_bulge));
        // Mesh will be centered when loading.
        ModelVolume* new_volume = object.add_volume(std::move(mesh), ModelVolumeType::NEGATIVE_VOLUME);

        // Transform the new modifier to be aligned inside the instance
        new_volume->set_transformation(translation_transform(connector.pos) * connector.rotation_m *
            rotation_transform(-connector.z_angle * Vec3d::UnitZ()) *
            scale_transform(Vec3f(connector.radius, connector.radius, connector.height).cast<double>()));

        new_volume->cut_info = { connector.attribs.type, connector.radius_tolerance, connector.height_tolerance };
        new_volume->name = cut.connector_name + "-" + std::to_string(++connector_id);
    }
    object.cut_id.increase_connectors_cnt(object.cut_connectors.size());

    // delete all connectors
    object.cut_connectors.clear();
}

}  // namespace detail

void end_cut()
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    CutSession& current = session();
    current.open = false;
    current.model.clear_objects();
}

}  // namespace orcinus::orca
