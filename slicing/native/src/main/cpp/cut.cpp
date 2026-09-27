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
#include <memory>
#include <string>
#include <vector>

#include "libslic3r/AABBMesh.hpp"
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

// GLGizmoCut3D::PartSelection: the object cut by the plane it was built at
// and split into its pieces (m_model), each with its raycaster, where it goes
// and whether it is a modifier, and the pieces' meshes for the 3D view.
struct PartSelection {
    bool valid{false};
    std::vector<double> plane;
    Slic3r::Model model;
    struct Part {
        std::unique_ptr<Slic3r::AABBMesh> raycaster;
        bool selected{true};
        bool is_modifier{false};
    };
    std::vector<Part> parts;
    std::vector<std::string> meshes;
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
    // The raycasters of the copy's solid parts, which has_valid_groove() casts
    // the groove's edges at, with the parts' matrices in the world.
    std::vector<std::unique_ptr<Slic3r::AABBMesh>> raycasters;
    std::vector<Slic3r::Transform3d> raycaster_matrices;
    // The pieces of the last right click.
    PartSelection part_selection;
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

// GLGizmoCut3D::its_make_groove_plane(): the plane of a dovetail cut, with a
// slot per groove, in the plane's frame; groove_vertices are the ends of the
// slot's edges, which has_valid_groove() checks against the object.
indexed_triangle_set its_make_groove_plane(
    const Slic3r::Cut::Groove& m_groove,
    const float m_radius,
    const int m_groove_count,
    const float m_groove_gap,
    std::vector<Slic3r::Vec3d>& m_groove_vertices
)
{
    using namespace Slic3r;
    const auto offset_indices = [](const std::vector<Vec3i32>& base_indices, size_t vo) {
        std::vector<Vec3i32> offset;
        int                  vo_int = static_cast<int>(vo);
        for (const auto& tri : base_indices) {
            offset.push_back({tri[0] + vo_int, tri[1] + vo_int, tri[2] + vo_int});
        }
        return offset;
    };

    // This function generates a dovetail slot in a wall, viewed from above (top-down).
    // The slot has a wide "mouth" (closest to the wall surface) and a narrow "neck" (deepest into the wall).
    // The flaps are the tapered sides connecting the mouth to the neck.

    const float flap_taper_width  = is_approx(m_groove.flaps_angle, 0.f) ? m_groove.depth : (m_groove.depth / sin(m_groove.flaps_angle));
    const float total_flap_taper_width = 2.f * flap_taper_width * cos(m_groove.flaps_angle);

    const float slot_neck_half_width = 0.5f * (m_groove.width);
    const float slot_mouth_half_width = 0.5f * (m_groove.width + total_flap_taper_width);

    const float cut_plane_radius = 1.5f * float(m_radius);
    const float cut_plane_length = 1.5f * cut_plane_radius;

    const float plane_half_width    = 0.5f * cut_plane_radius;  // x
    const float plane_half_height   = 0.5f * cut_plane_length;  // y

    const float slot_half_depth   = 0.5f * m_groove.depth;    // z
    float slot_front_z          = slot_half_depth;
    float slot_back_z           = -slot_half_depth;

    const float flap_taper_offset = plane_half_height * tan(m_groove.angle);

    float slot_mouth_outer_x = slot_neck_half_width + flap_taper_offset; // upper_x extension
    float slot_neck_outer_x = slot_mouth_half_width + flap_taper_offset; // lower_x extension
    float slot_outer_x_max   = std::max(slot_neck_outer_x, slot_mouth_outer_x);  // max x extension

    float slot_neck_inner_x = slot_neck_half_width - flap_taper_offset; // upper_x narrowing
    float slot_mouth_inner_x = slot_mouth_half_width - flap_taper_offset; // lower_x narrowing

    const float wall_thickness = 0.02f; // 0.02f * (float)get_grabber_mean_size(m_bounding_box);   // cut_plane_thiknes

    int   groove_count    = m_groove_count;
    float groove_gap = m_groove_gap;

    indexed_triangle_set mesh;

    // handle multiple dovetails/grooves
    m_groove_vertices.clear();
    m_groove_vertices.reserve(8 * groove_count);
    for (int i = 0; i < groove_count; ++i) {
        bool is_first_groove = i == 0; // when a groove is not the last groove, then limit the extent of the right plane so that it doesnt overlap the next groove
        bool is_last_groove  = i == groove_count - 1; // do the same in reverse if a groove is not the first groove
        size_t vertex_index_offset      = mesh.vertices.size();

        // Calculate the x-axis offset for this dovetail
        float groove_offset_factor_start = -.5 * ((groove_count - 1));
        float  groove_offset_factor   = groove_offset_factor_start + i;
        // Recalculate x with offset (only X-axis offset)
        float offset_x = groove_offset_factor * (groove_gap + (2 * slot_outer_x_max));

        // Vertices of the groove used to detection if groove is valid (not used in mesh)
        {
            m_groove_vertices.emplace_back(Vec3f(-slot_neck_outer_x + offset_x, -plane_half_height, slot_front_z).cast<double>());
            m_groove_vertices.emplace_back(Vec3f(-slot_mouth_inner_x + offset_x, plane_half_height, slot_front_z).cast<double>());
            m_groove_vertices.emplace_back(Vec3f(-slot_mouth_outer_x + offset_x, -plane_half_height, slot_back_z).cast<double>());
            m_groove_vertices.emplace_back(Vec3f(-slot_neck_outer_x + offset_x, plane_half_height, slot_back_z).cast<double>());
            m_groove_vertices.emplace_back(Vec3f(slot_neck_outer_x + offset_x, -plane_half_height, slot_front_z).cast<double>());
            m_groove_vertices.emplace_back(Vec3f(slot_mouth_inner_x + offset_x, plane_half_height, slot_front_z).cast<double>());
            m_groove_vertices.emplace_back(Vec3f(slot_mouth_outer_x + offset_x, -plane_half_height, slot_back_z).cast<double>());
            m_groove_vertices.emplace_back(Vec3f(slot_neck_outer_x + offset_x, plane_half_height, slot_back_z).cast<double>());
        }

        //                                     ___
        // Case: Groove is open (Top&bottom: __\ /__ )
        if (slot_neck_half_width > flap_taper_offset && slot_mouth_half_width > flap_taper_offset) {
            auto get_vertices = [plane_half_width, plane_half_height]
                (float slot_front_z, float slot_back_z, float slot_neck_inner_x, float slot_mouth_inner_x, float slot_mouth_outer_x, float slot_neck_outer_x,
                float slot_outer_x_max, bool is_first_groove, bool is_last_groove, float groove_gap, float offset_x)
                {

                return std::vector<stl_vertex>({
                    // front left part vertices
                    {is_first_groove ? -plane_half_width + offset_x : -(groove_gap / 2.f) - slot_outer_x_max + offset_x,    -plane_half_height, slot_front_z},    // *__/ \__
                    {is_first_groove ? -plane_half_width + offset_x : -(groove_gap / 2.f) - slot_outer_x_max + offset_x,    plane_half_height,  slot_front_z},    // *__/ \__
                    {-slot_neck_inner_x + offset_x,     plane_half_height,      slot_front_z},   // __*/ \__
                    {-slot_mouth_outer_x + offset_x,    -plane_half_height,     slot_front_z},   // __/* \__
                    // back part vertices
                    {-slot_neck_outer_x + offset_x,     -plane_half_height,     slot_back_z},   // __*/ \__
                    {-slot_mouth_inner_x + offset_x,    plane_half_height,      slot_back_z},   // __/* \__
                    {slot_mouth_inner_x + offset_x,     plane_half_height,      slot_back_z},   // __/ *\__
                    {slot_neck_outer_x + offset_x,      -plane_half_height,     slot_back_z},   // __/ \*__
                    // front right part vertices
                    {slot_mouth_outer_x + offset_x,     -plane_half_height,     slot_front_z},   // __/ \*__
                    {slot_neck_inner_x + offset_x,      plane_half_height,      slot_front_z},   // __/ *\__
                    {is_last_groove ? plane_half_width + offset_x : (groove_gap / 2.f) + slot_outer_x_max + offset_x,       plane_half_height,  slot_front_z},      // __/ \__*
                    {is_last_groove ? plane_half_width + offset_x : (groove_gap / 2.f) + slot_outer_x_max + offset_x,       -plane_half_height, slot_front_z}});    // __/ \__*
            };

            std::vector<stl_vertex> vertices = get_vertices(slot_front_z, slot_back_z, slot_neck_inner_x, slot_mouth_inner_x, slot_mouth_outer_x, slot_neck_outer_x, slot_outer_x_max,
                                                            is_first_groove, is_last_groove, groove_gap, offset_x);
            mesh.vertices.insert(mesh.vertices.end(), vertices.begin(), vertices.end());

            // Back face
            slot_front_z        -= wall_thickness;
            slot_back_z         -= wall_thickness;

            const float slot_back_face_x_offset = wall_thickness / tan(0.5f * m_groove.flaps_angle);
            slot_neck_inner_x   += slot_back_face_x_offset;
            slot_mouth_inner_x  += slot_back_face_x_offset;
            slot_mouth_outer_x  += slot_back_face_x_offset;
            slot_neck_outer_x   += slot_back_face_x_offset;

            vertices = get_vertices(slot_front_z, slot_back_z, slot_neck_inner_x, slot_mouth_inner_x, slot_mouth_outer_x, slot_neck_outer_x,
                                    slot_outer_x_max, is_first_groove,
                                    is_last_groove, groove_gap, offset_x);
            mesh.vertices.insert(mesh.vertices.end(), vertices.begin(), vertices.end());

            std::vector<Vec3i32> base_indices;

            base_indices  = {
                // above view
                {5,4,7}, {5,7,6},       // lower part
                {3,4,5}, {3,5,2},       // left side
                {9,6,8}, {8,6,7},       // right side
                {1,0,2}, {2,0,3},       // upper left part
                {9,8,10}, {10,8,11},    // upper right part
                // under view
                {20,21,22}, {20,22,23}, // upper right part
                {12,13,14}, {12,14,15}, // upper left part
                {18,21,20}, {18,20,19}, // right side
                {16,15,14}, {16,14,17}, // left side
                {16,17,18}, {16,18,19}, // lower part
                // left edge
                {1,13,0}, {0,13,12},
                // front edge
                {0,12,3}, {3,12,15}, {3,15,4}, {4,15,16}, {4,16,7}, {7,16,19}, {7,19,20}, {7,20,8}, {8,20,11}, {11,20,23},
                // right edge
                {11,23,10}, {10,23,22},
                // back edge
                {1,13,2}, {2,13,14}, {2,14,17}, {2,17,5}, {5,17,6}, {6,17,18}, {6,18,9}, {9,18,21}, {9,21,10}, {10,21,22}
            };

            std::vector<Vec3i32> indices = offset_indices(base_indices, vertex_index_offset);
            mesh.indices.insert(mesh.indices.end(), indices.begin(), indices.end());
        }

        //                                         __
        // CASE: Groove is closed (Top&Bottom: ___/  \___)
        else if (slot_neck_half_width < flap_taper_offset && slot_mouth_half_width < flap_taper_offset) {
            float slot_neck_apex_y = slot_neck_half_width / tan(m_groove.angle);
            float slot_mouth_apex_y = slot_mouth_half_width / tan(m_groove.angle);

            auto get_vertices = [plane_half_width, plane_half_height]
            (float slot_front_z, float slot_back_z, float slot_neck_apex_y, float slot_mouth_apex_y, float slot_mouth_outer_x, float slot_neck_outer_x,
             float slot_outer_x_max, bool is_first_groove, bool is_last_groove, float groove_gap, float offset_x)
            {
                return std::vector<stl_vertex>({
                    // front part vertices
                    {is_first_groove ? -plane_half_width + offset_x : -(groove_gap / 2.f) - slot_outer_x_max + offset_x,  -plane_half_height,     slot_front_z},   // *__/\__
                    {is_first_groove ? -plane_half_width + offset_x : -(groove_gap / 2.f) - slot_outer_x_max + offset_x,  plane_half_height,      slot_front_z},   // *__/\__
                    {is_last_groove ? plane_half_width + offset_x : (groove_gap / 2.f) + slot_outer_x_max + offset_x,     plane_half_height,      slot_front_z},   // __/\__*
                    {is_last_groove ? plane_half_width + offset_x : (groove_gap / 2.f) + slot_outer_x_max + offset_x,     -plane_half_height,     slot_front_z},   // __/\__*
                    {slot_mouth_outer_x + offset_x,     -plane_half_height,     slot_front_z},    // __*/\__
                    {offset_x,                          slot_neck_apex_y,       slot_front_z},    // __/*\__
                    {-slot_mouth_outer_x + offset_x,    -plane_half_height,     slot_front_z},    // __/\*__
                    // back part vertices
                    {-slot_neck_outer_x + offset_x,     -plane_half_height,     slot_back_z},    // __*/\__
                    {offset_x,                          slot_mouth_apex_y,      slot_back_z},    // __/*\__
                    {slot_neck_outer_x + offset_x,      -plane_half_height,     slot_back_z}});  // __/\*__
            };

            std::vector<stl_vertex> vertices = get_vertices(slot_front_z, slot_back_z, slot_neck_apex_y, slot_mouth_apex_y,
                                                            slot_mouth_outer_x, slot_neck_outer_x, slot_outer_x_max,
                                                            is_first_groove, is_last_groove, groove_gap, offset_x);
            mesh.vertices.insert(mesh.vertices.end(), vertices.begin(), vertices.end());

            // Back face
            slot_front_z        -= wall_thickness;
            slot_back_z         -= wall_thickness;
            slot_neck_apex_y    += wall_thickness;
            slot_mouth_apex_y   += wall_thickness;

            const float slot_back_face_x_offset = wall_thickness / tan(0.5f * m_groove.flaps_angle);
            slot_mouth_outer_x  += slot_back_face_x_offset;
            slot_neck_outer_x   += slot_back_face_x_offset;

            vertices = get_vertices(slot_front_z, slot_back_z, slot_neck_apex_y, slot_mouth_apex_y, slot_mouth_outer_x, slot_neck_outer_x,
                                    slot_outer_x_max, is_first_groove,
                                    is_last_groove, groove_gap, offset_x);
            mesh.vertices.insert(mesh.vertices.end(), vertices.begin(), vertices.end());

            std::vector<Vec3i32> base_indices = {
                // above view
                {8,7,9},                // lower part
                {5,8,6}, {6,8,7},       // left side
                {4,9,8}, {4,8,5},       // right side
                {1,0,6}, {1,6,5},{1,5,2},
                {2,5,4}, {2,4,3},       // upper part
                // under view
                {10,11,16}, {16,11,15},
                {15,11,12}, {15,12,14}, {14,12,13},   // upper part
                {18,15,14}, {14,18,19}, // right side
                {17,16,15}, {17,15,18}, // left side
                {17,18,19},             // lower part
                // left edge
                {1,11,0}, {0,11,10},
                // front edge
                {0,10,6}, {6,10,16}, {6,17,16}, {6,7,17}, {7,17,19}, {7,19,9}, {4,14,19}, {4,19,9}, {4,14,13}, {4,13,3},
                // right edge
                {3,13,12}, {3,12,2},
                // back edge
                {2,12,11}, {2,11,1}
            };
            std::vector<Vec3i32> indices      = offset_indices(base_indices, vertex_index_offset);

            mesh.indices.insert(mesh.indices.end(), indices.begin(), indices.end());
        }

        // Case: Groove is closed from the roof (TOP&Bottom: __/\__ )
        else {
            float slot_neck_apex_y = slot_neck_half_width / tan(m_groove.angle);

            std::vector<stl_vertex> vertices = {
                // front part vertices
                {is_first_groove ? -plane_half_width + offset_x : -(groove_gap / 2.f) - slot_outer_x_max + offset_x,  -plane_half_height,     slot_front_z},
                {is_first_groove ? -plane_half_width + offset_x : -(groove_gap / 2.f) - slot_outer_x_max + offset_x,  plane_half_height,      slot_front_z},
                {is_last_groove ? plane_half_width + offset_x : (groove_gap / 2.f) + slot_outer_x_max + offset_x,     plane_half_height,      slot_front_z},
                {is_last_groove ? plane_half_width + offset_x : (groove_gap / 2.f) + slot_outer_x_max + offset_x,     -plane_half_height,     slot_front_z},
                {slot_mouth_outer_x + offset_x,     -plane_half_height,     slot_front_z},
                {offset_x,                          slot_neck_apex_y,       slot_front_z},
                {-slot_mouth_outer_x + offset_x,    -plane_half_height,     slot_front_z},
                // back part vertices
                {-slot_neck_outer_x + offset_x,     -plane_half_height,     slot_back_z},
                {-slot_mouth_inner_x + offset_x,    plane_half_height,      slot_back_z},
                {slot_mouth_inner_x + offset_x,     plane_half_height,      slot_back_z},
                {slot_neck_outer_x + offset_x,      -plane_half_height,     slot_back_z}};
            mesh.vertices.insert(mesh.vertices.end(), vertices.begin(), vertices.end());

            // Back face
            slot_front_z        -= wall_thickness;
            slot_back_z         -= wall_thickness;

            const float slot_back_face_x_offset = wall_thickness / tan(0.5f * m_groove.flaps_angle);
            slot_mouth_inner_x  += slot_back_face_x_offset;
            slot_mouth_outer_x  += slot_back_face_x_offset;
            slot_neck_outer_x   += slot_back_face_x_offset;

            vertices = {
                // upper part vertices
                {is_first_groove ? -plane_half_width + offset_x : -(groove_gap / 2.f) - slot_outer_x_max + offset_x,  -plane_half_height, slot_front_z},
                {is_first_groove ? -plane_half_width + offset_x : -(groove_gap / 2.f) - slot_outer_x_max + offset_x,  plane_half_height,  slot_front_z},
                {is_last_groove ? plane_half_width + offset_x : (groove_gap / 2.f) + slot_outer_x_max + offset_x,     plane_half_height,  slot_front_z},
                {is_last_groove ? plane_half_width + offset_x : (groove_gap / 2.f) + slot_outer_x_max + offset_x,     -plane_half_height, slot_front_z},
                {slot_mouth_outer_x + offset_x,         -plane_half_height,     slot_front_z},
                {slot_back_face_x_offset + offset_x,    slot_neck_apex_y,       slot_front_z},
                {-slot_back_face_x_offset + offset_x,   slot_neck_apex_y,       slot_front_z},
                {-slot_mouth_outer_x + offset_x,        -plane_half_height,     slot_front_z},
                // lower part vertices
                {-slot_neck_outer_x + offset_x,         -plane_half_height,     slot_back_z},
                {-slot_mouth_inner_x + offset_x,        plane_half_height,      slot_back_z},
                {slot_mouth_inner_x + offset_x,         plane_half_height,      slot_back_z},
                {slot_neck_outer_x + offset_x,          -plane_half_height,     slot_back_z}};
            mesh.vertices.insert(mesh.vertices.end(), vertices.begin(), vertices.end());

            // Indices for this dovetail
            std::vector<Vec3i32> base_indices = {
                // above view
                {8,7,10}, {8,10,9},     // lower part
                {5,8,7}, {5,7,6},       // left side
                {4,10,9}, {4,9,5},      // right side
                {1,0,6}, {1,6,5},{1,5,2}, {2,5,4}, {2,4,3},   // upper part
                // under view
                {11,12,18}, {18,12,17}, {17,12,16}, {16,12,13}, {16,13,15}, {15,13,14},   // upper part
                {21,16,15}, {21,15,22}, // right side
                {19,18,17}, {19,17,20}, // left side
                {19,20,21}, {19,21,22}, // lower part
                // left edge
                {1,12,11}, {1,11,0},
                // front edge
                {0,11,18}, {0,18,6}, {7,19,18}, {7,18,6}, {7,19,22}, {7,22,10}, {10,22,15}, {10,15,4}, {4,15,14}, {4,14,3},
                // right edge
                {3,14,13}, {3,14,2},
                // back edge
                {2,13,12}, {2,12,1}, {5,16,21}, {5,21,9}, {9,21,20}, {9,20,8}, {5,17,20}, {5,20,8}
            };
            std::vector<Vec3i32> indices      = offset_indices(base_indices, vertex_index_offset);

            mesh.indices.insert(mesh.indices.end(), indices.begin(), indices.end());
        }
    }

    return mesh;
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
    const std::vector<std::size_t>& ignored,
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
            const int contour_idx = is_projection_inside_cut(clippers, vertex.cast<double>());
            bool is_invalid = (contour_idx == -1);
            if (!is_invalid) {
                is_invalid = (std::find(ignored.begin(), ignored.end(), std::size_t(contour_idx)) != ignored.end());
            }
            if (is_invalid) {
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
// contour_width wide, to contour, in world coordinates; render_cut() and
// render_contour() leave out the islands ignore_idxs names.
void clip(
    const indexed_triangle_set& its,
    const Slic3r::Geometry::Transformation& m_trafo,
    const ClippingPlane& m_plane,
    const ClippingPlane& m_limiting_plane,
    const double m_contour_width,
    Slic3r::ExPolygons& islands,
    Slic3r::Transform3d& trafo,
    indexed_triangle_set& section,
    indexed_triangle_set& contour,
    const std::vector<std::size_t>& ignore_idxs = {}
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
        if (std::binary_search(ignore_idxs.begin(), ignore_idxs.end(), islands.size())) {
            islands.push_back(exp);
            continue;
        }

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

// ObjectClipper::render_cut() over every volume of the copy, the part below
// the plate left out: the clippers of the plane through plane_center along
// normal, with their sections and outlines. ignore_idxs counts the islands
// over every clipper.
std::vector<ClipResult> clip_object(
    const Slic3r::ModelObject& object,
    const Slic3r::ModelInstance& instance,
    const Slic3r::Vec3d& plane_center,
    const Slic3r::Vec3d& normal,
    const double contour_width,
    const std::vector<std::size_t>& ignore_idxs,
    indexed_triangle_set& section,
    indexed_triangle_set& contour
)
{
    const ClippingPlane clipping_plane(normal, normal.dot(plane_center));
    const ClippingPlane limiting_plane(Slic3r::Vec3d::UnitZ(), -Slic3r::SINKING_Z_THRESHOLD);
    std::vector<std::size_t> ignore_idxs_local = ignore_idxs;
    std::vector<ClipResult> clippers;
    for (const Slic3r::ModelVolume* volume : object.volumes) {
        ClipResult clipped;
        const Slic3r::Geometry::Transformation trafo = instance.get_transformation() * volume->get_transformation();
        clip(volume->mesh().its, trafo, clipping_plane, limiting_plane, contour_width, clipped.islands, clipped.trafo, section, contour, ignore_idxs_local);
        for (const Slic3r::ExPolygon& island : clipped.islands) {
            clipped.boxes.push_back(Slic3r::get_extents(island));
        }
        // Now update the ignore idxs. Find the first element belonging to the next clipper,
        // and remove everything before it and decrement everything by current number of contours.
        const std::size_t num_of_contours = clipped.islands.size();
        ignore_idxs_local.erase(ignore_idxs_local.begin(), std::find_if(ignore_idxs_local.begin(), ignore_idxs_local.end(), [num_of_contours](std::size_t idx) { return idx >= num_of_contours; }));
        for (std::size_t& idx : ignore_idxs_local)
            idx -= num_of_contours;
        clippers.push_back(std::move(clipped));
    }
    return clippers;
}

// ObjectClipper::point_per_contour(): a point inside every island, not in a
// hole, in the order the islands are counted.
std::vector<Slic3r::Vec3d> point_per_contour(const std::vector<ClipResult>& clippers)
{
    using namespace Slic3r;
    std::vector<Vec3d> out;
    for (const ClipResult& result : clippers) {
        for (const ExPolygon& expoly : result.islands) {
            // Now return a point lying inside the contour but not in a hole.
            // We do this by taking a point lying close to the edge, repeating
            // this several times for different edges and distances from them.
            // (We prefer point not extremely close to the border.
            bool done = false;
            Vec2d p;
            size_t i = 1;
            while (i < expoly.contour.size()) {
                const Vec2d& a = unscale(expoly.contour.points[i-1]);
                const Vec2d& b = unscale(expoly.contour.points[i]);
                Vec2d n = (b-a).normalized();
                std::swap(n.x(), n.y());
                n.x() = -1 * n.x();
                double f = 10.;
                while (f > 0.05) {
                    p = (0.5*(b+a)) + f * n;
                    if (expoly.contains(Point::new_scale(p))) {
                        done = true;
                        break;
                    }
                    f = f/10.;
                }
                if (done)
                    break;
                i += std::max(size_t(2), expoly.contour.size() / 5);
            }
            // If the above failed, just return the centroid, regardless of whether
            // it is inside the contour or in a hole (we must return something).
            Vec2d c = done ? p : unscale(expoly.contour.centroid());
            out.emplace_back(result.trafo * Vec3d(c.x(), c.y(), 0.));
        }
    }
    return out;
}

// The PartSelection constructor for a planar cut: the copy cut at plane and
// split into its pieces, each above the plane or below it, with the pieces'
// meshes written for the 3D view. Kept while the plane stays.
void build_part_selection(CutSession& current, const std::vector<double>& plane, const std::string& mesh_prefix)
{
    using namespace Slic3r;
    using namespace Slic3r::Geometry;
    PartSelection& selection = current.part_selection;
    if (selection.valid && selection.plane == plane)
        return;
    selection.valid = false;
    selection.parts.clear();
    selection.meshes.clear();

    const ModelObject& object = *current.model.objects.front();
    const Transform3d plane_matrix = matrix_of(plane);
    const Vec3d center = plane_matrix.translation();
    const Transform3d m_rotation_m(plane_matrix.linear());
    const Vec3d normal = (m_rotation_m * Vec3d::UnitZ()).normalized();
    // get_cut_matrix()
    const Vec3d instance_offset = object.instances[std::size_t(current.instance)]->get_offset();
    const Transform3d cut_matrix = translation_transform(center - instance_offset) * m_rotation_m;
    const ModelObject* model_object = detail::split_cut_parts(selection.model, object, current.instance, cut_matrix);

    const ModelInstance& instance = *model_object->instances[std::size_t(current.instance)];
    for (std::size_t id = 0; id < model_object->volumes.size(); ++id) {
        const ModelVolume* volume = model_object->volumes[id];
        PartSelection::Part part;
        part.raycaster = std::make_unique<AABBMesh>(volume->mesh());
        part.is_modifier = !volume->is_model_part();

        // Now check whether this part is below or above the plane.
        Transform3d tr = (instance.get_matrix() * volume->get_matrix()).inverse();
        Vec3f pos = (tr * center).cast<float>();
        Vec3f norm = (tr.linear().inverse().transpose() * normal).cast<float>();
        for (const Vec3f& v : volume->mesh().its.vertices) {
            double p = (v - pos).dot(norm);
            if (std::abs(p) > EPSILON) {
                part.selected = p > 0.;
                break;
            }
        }
        selection.parts.push_back(std::move(part));

        // PartSelection::render() draws the piece at the copy's offset.
        indexed_triangle_set its = volume->mesh().its;
        its_transform(its, translation_transform(instance.get_offset()) * volume->get_matrix());
        const std::string path = mesh_prefix + "-" + std::to_string(current.writes) + "-piece-" + std::to_string(id) + ".mesh";
        selection.meshes.push_back(detail::write_mesh(its, path) ? path : std::string());
    }
    ++current.writes;
    selection.plane = plane;
    selection.valid = true;
}

// The contours PartSelection::toggle_selection() leaves out, for the islands
// the clippers of the plane through center along normal cut now: from a
// point of every contour on the plane (m_contour_points), a ray each way
// finds the pieces it lies in above and below the plane
// (m_contour_to_parts); a contour between pieces going to the same part is
// ignored.
std::vector<std::size_t> ignored_contours(
    const CutSession& current,
    const std::vector<int>& selected,
    const std::vector<ClipResult>& clippers,
    const Slic3r::Vec3d& center,
    const Slic3r::Vec3d& normal
)
{
    using namespace Slic3r;
    using namespace Slic3r::Geometry;
    const PartSelection& selection = current.part_selection;
    std::vector<std::size_t> ignored;
    if (!selection.valid || selected.size() != selection.parts.size())
        return ignored;
    const ModelObject* model_object = selection.model.objects.front();
    const std::vector<Vec3d> pts = point_per_contour(clippers);
    for (std::size_t pt_idx = 0; pt_idx < pts.size(); ++pt_idx) {
        const Vec3d& pt = pts[pt_idx];
        const Vec3d dir = (center-pt).dot(normal) * normal;
        const Vec3d contour_point = dir + pt; // the result is in world coordinates.

        // Now, cast a ray from every contour point and see which volumes of the ones above
        // the plane are hit from the inside.
        std::vector<std::size_t> parts_above;
        std::vector<std::size_t> parts_below;
        for (std::size_t part_id = 0; part_id < selection.parts.size(); ++part_id) {
            const AABBMesh& aabb = *selection.parts[part_id].raycaster;
            const Transform3d& tr = (translation_transform(model_object->instances[std::size_t(current.instance)]->get_offset()) * translation_transform(model_object->volumes[part_id]->get_offset())).inverse();
            for (double d : {-1., 1.}) {
                const Vec3d dir_mesh = d * tr.linear().inverse().transpose() * normal;
                const Vec3d src = tr * (contour_point + d*0.01 * normal);
                AABBMesh::hit_result hit = aabb.query_ray_hit(src, dir_mesh);

                if (hit.is_inside()) {
                    // This part belongs to this point.
                    if (d == 1.)
                        parts_above.emplace_back(part_id);
                    else
                        parts_below.emplace_back(part_id);
                }
            }
        }

        // toggle_selection(): recalculate the contours which should be ignored.
        for (std::size_t upper : parts_above) {
            bool upper_sel = selected[upper] != 0;
            if (std::find_if(parts_below.begin(), parts_below.end(), [&selected, &upper_sel](const std::size_t& i) { return (selected[i] != 0) == upper_sel; }) != parts_below.end()) {
                ignored.emplace_back(pt_idx);
                break;
            }
        }
    }
    return ignored;
}

// MeshRaycaster::unproject_on_mesh() without a clipping plane and with the
// sinking limit: where the ray from point along direction (world
// coordinates) first meets the mesh of emesh, placed by trafo, from outside.
bool unproject_on_mesh(const Slic3r::AABBMesh& emesh, const Slic3r::Transform3d& trafo, const Slic3r::Vec3d& point_world, const Slic3r::Vec3d& direction_world, Slic3r::Vec3d& position)
{
    using namespace Slic3r;
    const Transform3d inv = trafo.inverse();
    const Vec3d point = inv * point_world;
    const Vec3d direction = inv.linear() * direction_world;

    std::vector<AABBMesh::hit_result> hits = emesh.query_ray_hits(point, direction);

    if (hits.empty())
        return false; // no intersection found

    unsigned i = 0;

    // Remove points that are obscured or cut by the clipping plane.
    // Also, remove anything below the bed (sinking objects).
    for (i=0; i<hits.size(); ++i) {
        Vec3d transformed_hit = trafo * hits[i].position();
        if (transformed_hit.z() >= SINKING_Z_THRESHOLD)
            break;
    }

    if (i==hits.size() || (hits.size()-i) % 2 != 0) {
        // All hits are either clipped, or there is an odd number of unclipped
        // hits - meaning the nearest must be from inside the mesh.
        return false;
    }

    position = hits[i].position();
    return true;
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
        current.raycasters.clear();
        current.raycaster_matrices.clear();
        current.part_selection = PartSelection();
        current.open = true;

        // GLGizmoCut3D::bounding_box(): the convex hulls of the copy's solid
        // parts (GLVolume::transformed_convex_hull_bounding_box).
        Slic3r::BoundingBoxf3 box;
        const Slic3r::Transform3d instance_matrix = loaded.instances[std::size_t(instance)]->get_matrix();
        for (const Slic3r::ModelVolume* volume : loaded.volumes) {
            if (volume->is_model_part()) {
                box.merge(volume->get_convex_hull().transformed_bounding_box(instance_matrix * volume->get_matrix()));
                current.raycasters.push_back(std::make_unique<Slic3r::AABBMesh>(volume->mesh()));
                current.raycaster_matrices.push_back(instance_matrix * volume->get_matrix());
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
    const bool dovetail,
    const CutGroove& groove,
    const bool preview,
    const std::string& mesh_prefix,
    const std::vector<double>& parts_plane,
    const std::vector<int>& parts
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
        // GLGizmoCut3D::m_contour_width: none for the dovetail cut.
        const double contour_width = dovetail ? 0.0 : 0.4;
        indexed_triangle_set section;
        indexed_triangle_set contour;
        std::vector<ClipResult> clippers = clip_object(object, instance, plane_center, normal, contour_width, {}, section, contour);

        // The pieces of a right click leave out the contours they join over
        // (m_part_selection.get_ignored_contours_ptr()).
        std::vector<std::size_t> ignored;
        if (!dovetail && !parts.empty() && parts_plane.size() == 16) {
            build_part_selection(current, parts_plane, mesh_prefix);
            ignored = ignored_contours(current, parts, clippers, plane_center, normal);
            if (!ignored.empty()) {
                section.clear();
                contour.clear();
                clippers = clip_object(object, instance, plane_center, normal, contour_width, ignored, section, contour);
            }
        }

        // MeshClipper::has_valid_contour()
        for (const ClipResult& clipped : clippers) {
            if (std::any_of(clipped.islands.begin(), clipped.islands.end(), [](const Slic3r::ExPolygon& island) { return !island.empty(); })) {
                result.valid_contour = true;
            }
        }

        // check_and_update_connectors_state() checks the planar cut only.
        if (!dovetail) {
            check_connectors(connectors, rotation_m, current.bounding_box, clippers, ignored, snap_space, snap_bulge, result);
        }
        if (dovetail) {
            const Slic3r::Cut::Groove m_groove = detail::cut_groove(groove);
            const float m_radius = float(current.bounding_box.radius());
            std::vector<Slic3r::Vec3d> m_groove_vertices;
            const indexed_triangle_set groove_mesh = its_make_groove_plane(m_groove, m_radius, groove.count, float(groove.gap), m_groove_vertices);
            const std::string groove_path = mesh_prefix + "-" + std::to_string(current.writes) + "-groove.mesh";
            if (detail::write_mesh(groove_mesh, groove_path)) {
                result.groove_plane = groove_path;
            }

            // has_valid_groove()
            const auto has_valid_groove = [&]() {
                const float flaps_width = -2.f * m_groove.depth / tan(m_groove.flaps_angle);
                if (flaps_width > m_groove.width)
                    return false;
                if (current.raycasters.empty())
                    return false;

                const Slic3r::Transform3d cp_matrix = Slic3r::Geometry::translation_transform(plane_center) * rotation_m;

                for (size_t id = 0; id < m_groove_vertices.size(); id += 2) {
                    const Slic3r::Vec3d beg = cp_matrix * m_groove_vertices[id];
                    const Slic3r::Vec3d end = cp_matrix * m_groove_vertices[id + 1];

                    bool intersection = false;
                    for (std::size_t volume = 0; volume < current.raycasters.size(); ++volume) {
                        // MeshRaycaster::intersects_line()
                        const Slic3r::Transform3d trafo_inv = current.raycaster_matrices[volume].inverse();
                        const Slic3r::Vec3d to = trafo_inv * end;
                        const Slic3r::Vec3d point = trafo_inv * beg;
                        const Slic3r::Vec3d direction = (to - point).normalized();
                        if (!current.raycasters[volume]->query_ray_hits(point, direction).empty() ||
                            !current.raycasters[volume]->query_ray_hits(point, -direction).empty()) {
                            intersection = true;
                            break;
                        }
                    }
                    if (!intersection)
                        return false;
                }

                return true;
            };
            result.valid_groove = has_valid_groove();

            // process_contours(): the parts of the cut with the groove, which the
            // gizmo shows in the object's place.
            if (preview && result.valid_groove) {
                const Slic3r::Vec3d instance_offset = instance.get_offset();
                const Slic3r::Transform3d cut_matrix = Slic3r::Geometry::translation_transform(plane_center - instance_offset) * rotation_m;
                Slic3r::Cut cut(&object, current.instance, cut_matrix);
                const Slic3r::ModelObjectPtrs& new_objects = cut.perform_with_groove(m_groove, rotation_m, groove.count, float(groove.gap), m_radius, true);
                if (!new_objects.empty()) {
                    const Slic3r::ModelObject& parts = *new_objects.front();
                    for (std::size_t id = 0; id < parts.volumes.size(); ++id) {
                        const Slic3r::ModelVolume& volume = *parts.volumes[id];
                        indexed_triangle_set its = volume.mesh().its;
                        its_transform(its, Slic3r::Geometry::translation_transform(instance_offset) * volume.get_matrix());
                        const std::string path = mesh_prefix + "-" + std::to_string(current.writes) + "-part-" + std::to_string(id) + ".mesh";
                        if (detail::write_mesh(its, path)) {
                            result.preview_parts.push_back({path, volume.is_from_upper(), !volume.is_model_part()});
                        }
                    }
                }
            }
        }
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

CutParts select_cut_part(
    const std::vector<double>& parts_plane,
    const std::vector<int>& selected,
    const double origin[3],
    const double direction[3],
    const std::string& mesh_prefix
)
{
    using namespace Slic3r;
    using namespace Slic3r::Geometry;
    CutParts result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    CutSession& current = session();
    if (!current.open || current.model.objects.empty() || parts_plane.size() != 16) {
        result.message = "The cut gizmo is not open";
        return result;
    }
    try {
        // if (! m_part_selection.valid()) process_contours();
        build_part_selection(current, parts_plane, mesh_prefix);
        PartSelection& selection = current.part_selection;
        if (selected.size() == selection.parts.size()) {
            for (std::size_t id = 0; id < selected.size(); ++id)
                selection.parts[id].selected = selected[id] != 0;
        }

        // PartSelection::toggle_selection()
        const ModelObject* model_object = selection.model.objects.front();
        const Vec3d camera_pos(origin[0], origin[1], origin[2]);
        const Vec3d ray(direction[0], direction[1], direction[2]);
        std::vector<std::pair<size_t, double>> hits_id_and_sqdist;
        for (size_t id=0; id<selection.parts.size(); ++id) {
            Transform3d tr = translation_transform(model_object->instances[std::size_t(current.instance)]->get_offset()) * translation_transform(model_object->volumes[id]->get_offset());
            Vec3d pos;
            if (unproject_on_mesh(*selection.parts[id].raycaster, tr, camera_pos, ray, pos)) {
                hits_id_and_sqdist.emplace_back(id, (camera_pos - tr*pos).squaredNorm());
            }
        }
        if (! hits_id_and_sqdist.empty()) {
            size_t id = std::min_element(hits_id_and_sqdist.begin(), hits_id_and_sqdist.end(),
                [](const std::pair<size_t, double>& a, const std::pair<size_t, double>& b) { return a.second < b.second; })->first;
            selection.parts[id].selected = ! selection.parts[id].selected;
        }

        for (std::size_t id = 0; id < selection.parts.size(); ++id) {
            result.parts.push_back({selection.meshes[id], selection.parts[id].selected, selection.parts[id].is_modifier});
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.message = error.what();
        return result;
    }
}

namespace detail {

Slic3r::ModelObject* split_cut_parts(Slic3r::Model& model, const Slic3r::ModelObject& object, const int instance, const Slic3r::Transform3d& cut_matrix)
{
    // PartSelection::add_object() of the object cut as parts.
    Slic3r::Cut cut(&object, instance, cut_matrix);
    model = Slic3r::Model();
    model.add_object(*cut.perform_with_plane().front());
    Slic3r::ModelObject* model_object = model.objects.front();

    const Slic3r::ModelVolumePtrs& volumes = model_object->volumes;

    // split to parts
    for (int id = int(volumes.size())-1; id >= 0; id--)
        if (volumes[id]->is_splittable())
            volumes[id]->split(1, false); // No need to remap paint here, we do it later in perform_by_contour

    return model_object;
}

Slic3r::ModelVolume::CutInfo cut_info_of(const VolumeCutInfo& info)
{
    Slic3r::ModelVolume::CutInfo result;
    result.is_from_upper = info.from_upper;
    result.is_connector = info.connector;
    result.is_processed = info.processed;
    result.connector_type = Slic3r::CutConnectorType(info.connector_type);
    result.radius_tolerance = float(info.radius_tolerance);
    result.height_tolerance = float(info.height_tolerance);
    return result;
}

VolumeCutInfo cut_info_from(const Slic3r::ModelVolume::CutInfo& info)
{
    VolumeCutInfo result;
    result.from_upper = info.is_from_upper;
    result.connector = info.is_connector;
    result.processed = info.is_processed;
    result.connector_type = int(info.connector_type);
    result.radius_tolerance = info.radius_tolerance;
    result.height_tolerance = info.height_tolerance;
    return result;
}

Slic3r::Cut::Groove cut_groove(const CutGroove& groove)
{
    Slic3r::Cut::Groove result;
    result.depth = float(groove.depth);
    result.width = float(groove.width);
    result.flaps_angle = float(groove.flaps_angle);
    result.angle = float(groove.angle);
    result.depth_tolerance = float(groove.depth_tolerance);
    result.width_tolerance = float(groove.width_tolerance);
    return result;
}

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
    current.part_selection = PartSelection();
}

}  // namespace orcinus::orca
