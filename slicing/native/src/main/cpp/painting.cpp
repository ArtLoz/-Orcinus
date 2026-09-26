// Painting a model, ported from OrcaSlicer's painting gizmos over
// GLGizmoPainterBase (colour, supports, seam and fuzzy skin): the desktop app
// keeps a TriangleSelector per volume while a gizmo is open and paints into it
// with a cursor that follows the mouse. The app does the same through a
// painting session: it opens one of a kind for the volume it paints, sends the
// strokes of a finger, and closes it with the painted facets in hand.

#include <algorithm>
#include <array>
#include <cstdint>
#include <fstream>
#include <iterator>
#include <memory>
#include <string>
#include <vector>

#include "libslic3r/AABBMesh.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "libslic3r/TriangleSelector.hpp"

#include "engine_context.hpp"
#include "orca_engine_adapter.hpp"

namespace orcinus::orca {
namespace {

const char* const HEX = "0123456789abcdef";

void write_byte(std::string& out, const unsigned char value)
{
    out.push_back(HEX[value >> 4]);
    out.push_back(HEX[value & 0x0F]);
}

void write_int(std::string& out, const std::int32_t value)
{
    const auto bits = static_cast<std::uint32_t>(value);
    for (int shift = 24; shift >= 0; shift -= 8) {
        write_byte(out, static_cast<unsigned char>((bits >> shift) & 0xFF));
    }
}

void write_bits(std::string& out, const std::vector<bool>& bits)
{
    write_int(out, static_cast<std::int32_t>(bits.size()));
    unsigned char byte = 0;
    for (std::size_t index = 0; index < bits.size(); ++index) {
        byte = static_cast<unsigned char>(byte | (bits[index] ? 1 << (index % 8) : 0));
        if (index % 8 == 7) {
            write_byte(out, byte);
            byte = 0;
        }
    }
    if (bits.size() % 8 != 0) {
        write_byte(out, byte);
    }
}

struct Reader {
    const std::string& text;
    std::size_t at{0};

    bool byte(unsigned char& value)
    {
        if (at + 2 > text.size()) {
            return false;
        }
        const auto digit = [](const char character) -> int {
            if (character >= '0' && character <= '9') return character - '0';
            if (character >= 'a' && character <= 'f') return character - 'a' + 10;
            return -1;
        };
        const int high = digit(text[at]);
        const int low = digit(text[at + 1]);
        if (high < 0 || low < 0) {
            return false;
        }
        value = static_cast<unsigned char>(high << 4 | low);
        at += 2;
        return true;
    }

    bool integer(std::int32_t& value)
    {
        std::uint32_t bits = 0;
        for (int index = 0; index < 4; ++index) {
            unsigned char part = 0;
            if (!byte(part)) {
                return false;
            }
            bits = bits << 8 | part;
        }
        value = static_cast<std::int32_t>(bits);
        return true;
    }

    bool bits(std::vector<bool>& value)
    {
        std::int32_t count = 0;
        if (!integer(count) || count < 0) {
            return false;
        }
        value.assign(static_cast<std::size_t>(count), false);
        unsigned char byte_value = 0;
        for (std::int32_t index = 0; index < count; ++index) {
            if (index % 8 == 0 && !byte(byte_value)) {
                return false;
            }
            value[static_cast<std::size_t>(index)] = (byte_value >> (index % 8) & 1) != 0;
        }
        return true;
    }
};

/**
 * The painted facets as the app keeps them: the three pieces the desktop app
 * stores in a project (TriangleSelector::TriangleSplittingData), written as
 * hexadecimal so they travel as text.
 */
std::string serialize(const Slic3r::TriangleSelector::TriangleSplittingData& data)
{
    std::string out;
    write_int(out, static_cast<std::int32_t>(data.triangles_to_split.size()));
    for (const auto& mapping : data.triangles_to_split) {
        write_int(out, static_cast<std::int32_t>(mapping.triangle_idx));
        write_int(out, static_cast<std::int32_t>(mapping.bitstream_start_idx));
    }
    write_bits(out, data.bitstream);
    write_bits(out, data.used_states);
    return out;
}

bool deserialize_facets(const std::string& text, Slic3r::TriangleSelector::TriangleSplittingData& data)
{
    Reader reader{text};
    std::int32_t count = 0;
    if (!reader.integer(count) || count < 0) {
        return false;
    }
    data.triangles_to_split.clear();
    data.triangles_to_split.reserve(static_cast<std::size_t>(count));
    for (std::int32_t index = 0; index < count; ++index) {
        std::int32_t triangle = 0;
        std::int32_t start = 0;
        if (!reader.integer(triangle) || !reader.integer(start)) {
            return false;
        }
        data.triangles_to_split.emplace_back(triangle, start);
    }
    return reader.bits(data.bitstream) && reader.bits(data.used_states);
}

/** The facets a volume is painted with, of each kind, in PaintKind's order. */
using KindFacets = std::array<std::string, 4>;

/** The names the facets of each kind go by, in PaintKind's order. */
const char* const KIND_NAMES[] = {"supports", "seam", "color", "fuzzy_skin"};

/**
 * The painting of a volume as the app keeps it: the facets of each kind the
 * volume is painted with, each as serialize() writes them behind the name of
 * its kind, "<kind>=<facets>", separated by ';'. Facets without a name are
 * colour, as the app kept them before it painted anything else.
 */
KindFacets split_painting(const std::string& painting)
{
    KindFacets kinds;
    if (painting.find('=') == std::string::npos) {
        kinds[static_cast<std::size_t>(PaintKind::color)] = painting;
        return kinds;
    }
    std::size_t at = 0;
    while (at < painting.size()) {
        const std::size_t separator = painting.find(';', at);
        const std::size_t end = separator == std::string::npos ? painting.size() : separator;
        const std::size_t equals = painting.find('=', at);
        if (equals != std::string::npos && equals < end) {
            for (std::size_t kind = 0; kind < kinds.size(); ++kind) {
                if (painting.compare(at, equals - at, KIND_NAMES[kind]) == 0) {
                    kinds[kind] = painting.substr(equals + 1, end - equals - 1);
                }
            }
        }
        at = end + 1;
    }
    return kinds;
}

std::string join_painting(const KindFacets& kinds)
{
    std::string painting;
    for (std::size_t kind = 0; kind < kinds.size(); ++kind) {
        if (kinds[kind].empty()) {
            continue;
        }
        if (!painting.empty()) {
            painting += ';';
        }
        painting += KIND_NAMES[kind];
        painting += '=';
        painting += kinds[kind];
    }
    return painting;
}

/**
 * The painting the app keeps for a volume: the file at [facets], or the
 * painting itself, as the app kept it inline before its paintings went to
 * files (a path is absolute, a painting starts with a name or a digit).
 */
bool read_painting(const std::string& facets, std::string& painting)
{
    if (facets.empty() || facets.front() != '/') {
        painting = facets;
        return true;
    }
    std::ifstream in(facets, std::ios::binary);
    if (!in) {
        return false;
    }
    painting.assign(std::istreambuf_iterator<char>(in), std::istreambuf_iterator<char>());
    return true;
}

/** Writes a painting to path and returns the path; empty for a painting of nothing. */
std::string write_painting(const std::string& painting, const std::string& path)
{
    if (painting.empty()) {
        return {};
    }
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    out << painting;
    if (!out) {
        throw std::runtime_error("The painted facets could not be written to " + path);
    }
    return path;
}

/** The facets of a kind of the volume (GLGizmoPainterBase's subclasses each keep their own). */
Slic3r::FacetsAnnotation& facets_of(Slic3r::ModelVolume& volume, const std::size_t kind)
{
    switch (static_cast<PaintKind>(kind)) {
    case PaintKind::supports: return volume.supported_facets;
    case PaintKind::seam: return volume.seam_facets;
    case PaintKind::fuzzy_skin: return volume.fuzzy_skin_facets;
    case PaintKind::color: break;
    }
    return volume.mmu_segmentation_facets;
}

const Slic3r::FacetsAnnotation& facets_of(const Slic3r::ModelVolume& volume, const std::size_t kind)
{
    return facets_of(const_cast<Slic3r::ModelVolume&>(volume), kind);
}

/** The painted facets, or none when nothing is painted (FacetsAnnotation::empty()). */
std::string serialized(const Slic3r::TriangleSelector::TriangleSplittingData& data)
{
    return data.triangles_to_split.empty() ? std::string() : serialize(data);
}

/** The state of a stroke; Extruder1 is 1, as EnforcerBlockerType numbers the filaments. */
Slic3r::EnforcerBlockerType state_of(const int state)
{
    if (state <= 0) {
        return Slic3r::EnforcerBlockerType::NONE;
    }
    const int highest = static_cast<int>(Slic3r::EnforcerBlockerType::ExtruderMax);
    return static_cast<Slic3r::EnforcerBlockerType>(std::min(state, highest));
}

/**
 * The painting session, which lives while the tool is open, as the desktop
 * gizmo keeps its selectors while it is shown.
 */
struct Session {
    bool open{false};
    // What the session paints, and the painting of the volume of every kind,
    // which end_painting() hands back with the session's kind painted anew.
    PaintKind kind{PaintKind::color};
    KindFacets painting;
    // The file the session opened with, handed back while nothing changed,
    // and where the painting is written once it did.
    std::string opened_with;
    std::string painting_path;
    // The mesh being painted, in its own coordinates, with the tree that finds
    // the triangle under the finger.
    Slic3r::TriangleMesh mesh;
    std::unique_ptr<Slic3r::AABBMesh> tree;
    std::unique_ptr<Slic3r::TriangleSelector> selector;
    // Where the volume stands in the world, which the cursor needs.
    Slic3r::Transform3d world{Slic3r::Transform3d::Identity()};
    // The gizmo's own undo/redo stack: the painting before each stroke, and
    // the paintings Undo left, the next one last.
    std::vector<Slic3r::TriangleSelector::TriangleSplittingData> undo;
    std::vector<Slic3r::TriangleSelector::TriangleSplittingData> redo;
    // A stroke began and has not met the model yet.
    bool stroke_pending{false};
    // Where the stroke last met the model, in the mesh's coordinates, and on
    // which facet: the brush paints from there to where the finger is now
    // (the gizmo's DoublePointCursor between its mouse positions).
    bool has_last{false};
    Slic3r::Vec3f last_position{Slic3r::Vec3f::Zero()};
    int last_face{-1};
    // How many times the painted meshes were written, which names them anew
    // each time so the 3D view reads them again.
    std::size_t writes{0};
};

Session& session()
{
    static Session current;
    return current;
}

/** The triangles painted in each state, written for the 3D view, and what the tool can undo. */
void write_painted_meshes(const std::string& mesh_prefix, PaintingState& result)
{
    result.can_undo = !session().undo.empty();
    result.can_redo = !session().redo.empty();
    std::vector<indexed_triangle_set> per_state;
    session().selector->get_facets(per_state);
    const std::size_t write = session().writes++;
    for (std::size_t state = 1; state < per_state.size(); ++state) {
        if (per_state[state].indices.empty()) {
            continue;
        }
        const std::string path = mesh_prefix + "-" + std::to_string(state) + "-" + std::to_string(write) + ".mesh";
        if (!detail::write_mesh(per_state[state], path)) {
            continue;
        }
        result.states.push_back(static_cast<int>(state));
        result.meshes.push_back(path);
    }
}

}  // namespace

// Called by the adapter while it loads a plate, so painted objects are sliced
// with their paint: colours, supports, seams and fuzzy skin.
bool apply_painted_facets(Slic3r::ModelVolume& volume, const std::string& path)
{
    std::string painting;
    if (!read_painting(path, painting)) {
        return false;
    }
    const KindFacets kinds = split_painting(painting);
    for (std::size_t kind = 0; kind < kinds.size(); ++kind) {
        if (kinds[kind].empty()) {
            continue;
        }
        Slic3r::TriangleSelector::TriangleSplittingData data;
        if (!deserialize_facets(kinds[kind], data)) {
            return false;
        }
        facets_of(volume, kind).set_data(std::move(data));
    }
    return true;
}

std::string painted_facets_of(const Slic3r::ModelVolume& volume, const std::string& path)
{
    KindFacets kinds;
    for (std::size_t kind = 0; kind < kinds.size(); ++kind) {
        const Slic3r::FacetsAnnotation& facets = facets_of(volume, kind);
        if (!facets.empty()) {
            kinds[kind] = serialize(facets.get_data());
        }
    }
    return write_painting(join_painting(kinds), path);
}

PaintingState begin_painting(
    const PlateObject& object,
    const int part,
    const PaintKind kind,
    const ProfileSelection& profiles,
    const std::string& facets,
    const std::string& mesh_prefix
)
{
    PaintingState result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }

    try {
        Slic3r::DynamicPrintConfig config;
        std::string message;
        if (detail::select_profiles(*detail::engine().bundle, profiles, config, message) != SliceStatus::success) {
            result.status = SceneStatus::profile_not_found;
            result.message = message;
            return result;
        }
        Slic3r::Model model;
        if (!detail::load_plate({object}, config, model, message) || model.objects.empty()) {
            result.status = SceneStatus::model_read_failed;
            result.message = message;
            return result;
        }
        const Slic3r::ModelObject& loaded = *model.objects.front();
        // The object's own mesh is its first volume; a part is the one after it.
        const std::size_t volume_index = part < 0 ? 0 : static_cast<std::size_t>(part) + 1;
        if (volume_index >= loaded.volumes.size()) {
            result.status = SceneStatus::model_read_failed;
            result.message = "The plate has no such part";
            return result;
        }
        const Slic3r::ModelVolume& volume = *loaded.volumes[volume_index];

        Session& current = session();
        current.mesh = volume.mesh();
        current.tree = std::make_unique<Slic3r::AABBMesh>(current.mesh);
        current.selector = std::make_unique<Slic3r::TriangleSelector>(current.mesh);
        current.world = loaded.instances.empty()
            ? volume.get_matrix()
            : loaded.instances.front()->get_transformation().get_matrix() * volume.get_matrix();
        current.kind = kind;
        std::string opened;
        if (!read_painting(facets, opened)) {
            result.status = SceneStatus::model_read_failed;
            result.message = "The painted facets could not be read";
            return result;
        }
        current.painting = split_painting(opened);
        current.opened_with = facets;
        current.painting_path = mesh_prefix + ".painted";
        const std::string& painted = current.painting[static_cast<std::size_t>(kind)];
        if (!painted.empty()) {
            Slic3r::TriangleSelector::TriangleSplittingData data;
            if (!deserialize_facets(painted, data)) {
                result.status = SceneStatus::model_read_failed;
                result.message = "The painted facets could not be read";
                return result;
            }
            current.selector->deserialize(data, true);
        }
        current.undo.clear();
        current.redo.clear();
        current.stroke_pending = false;
        current.has_last = false;
        current.writes = 0;
        current.open = true;

        result.status = SceneStatus::success;
        write_painted_meshes(mesh_prefix, result);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

PaintingState paint(const PaintStroke& stroke, const std::string& mesh_prefix)
{
    PaintingState result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    Session& current = session();
    if (!current.open || current.selector == nullptr) {
        result.message = "No painting session is open";
        return result;
    }

    try {
        // The finger's ray in the volume's own coordinates, as the gizmo casts
        // the mouse ray into the mesh (GLGizmoPainterBase::gizmo_event).
        const Slic3r::Transform3d to_mesh = current.world.inverse();
        const Slic3r::Vec3d source = to_mesh * Slic3r::Vec3d(stroke.origin[0], stroke.origin[1], stroke.origin[2]);
        const Slic3r::Vec3d target = to_mesh
            * Slic3r::Vec3d(
                stroke.origin[0] + stroke.direction[0],
                stroke.origin[1] + stroke.direction[1],
                stroke.origin[2] + stroke.direction[2]
            );
        const Slic3r::Vec3d direction = (target - source).normalized();
        const Slic3r::AABBMesh::hit_result hit = current.tree->query_ray_hit(source, direction);
        result.status = SceneStatus::success;
        if (stroke.starts) {
            current.stroke_pending = true;
            current.has_last = false;
        }
        if (hit.face() < 0) {
            // The finger missed the model, which leaves it as it was, and the
            // brush starts anew where it meets it again.
            current.has_last = false;
            result.hit = false;
            write_painted_meshes(mesh_prefix, result);
            return result;
        }
        result.hit = true;
        // GLGizmoPainterBase::gizmo_event(): the snapshot of the stroke, and
        // what was undone before is gone.
        if (current.stroke_pending) {
            current.undo.push_back(current.selector->serialize());
            current.redo.clear();
            current.stroke_pending = false;
        }

        const Slic3r::Vec3f position = hit.position().cast<float>();
        const Slic3r::Transform3d no_translation = Slic3r::Transform3d(current.world.linear());
        const Slic3r::TriangleSelector::ClippingPlane clipping_plane;
        const Slic3r::EnforcerBlockerType state = state_of(stroke.state);
        // m_paint_on_overhangs_only ? m_highlight_by_angle_threshold_deg : 0.f
        const auto overhang_angle = static_cast<float>(stroke.overhang_angle);

        switch (stroke.tool) {
        case PaintTool::brush:
        case PaintTool::circle: {
            // The camera looks along the finger's ray, from its origin. Along
            // a stroke the brush paints the capsule from where it last met the
            // model, as the gizmo joins its mouse positions.
            const Slic3r::TriangleSelector::CursorType type = stroke.tool == PaintTool::circle
                ? Slic3r::TriangleSelector::CursorType::CIRCLE
                : Slic3r::TriangleSelector::CursorType::SPHERE;
            const auto radius = static_cast<float>(stroke.radius);
            if (current.has_last) {
                std::unique_ptr<Slic3r::TriangleSelector::Cursor> cursor =
                    Slic3r::TriangleSelector::DoublePointCursor::cursor_factory(
                        current.last_position, position, source.cast<float>(), radius, type, current.world, clipping_plane);
                current.selector->select_patch(current.last_face, std::move(cursor), state, no_translation, true, overhang_angle);
            } else {
                std::unique_ptr<Slic3r::TriangleSelector::Cursor> cursor =
                    Slic3r::TriangleSelector::SinglePointCursor::cursor_factory(position, source.cast<float>(), radius, type, current.world, clipping_plane);
                current.selector->select_patch(hit.face(), std::move(cursor), state, no_translation, true, overhang_angle);
            }
            current.has_last = true;
            current.last_position = position;
            current.last_face = hit.face();
            break;
        }
        case PaintTool::fill:
            // The smart fill of the desktop gizmo: the facets that lie flat
            // enough against the one touched take the colour with it.
            current.selector->seed_fill_select_triangles(
                position,
                hit.face(),
                no_translation,
                clipping_plane,
                static_cast<float>(stroke.angle),
                overhang_angle,
                true
            );
            current.selector->seed_fill_apply_on_triangles(state);
            break;
        case PaintTool::bucket:
            current.selector->bucket_fill_select_triangles(
                position,
                hit.face(),
                clipping_plane,
                static_cast<float>(stroke.angle),
                true,
                true
            );
            current.selector->seed_fill_apply_on_triangles(state);
            break;
        }

        write_painted_meshes(mesh_prefix, result);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

namespace {

/** Undo or Redo inside the tool: the painting on top of [from] comes back, and the one shown joins [to]. */
PaintingState step_painting(
    std::vector<Slic3r::TriangleSelector::TriangleSplittingData>& from,
    std::vector<Slic3r::TriangleSelector::TriangleSplittingData>& to,
    const std::string& mesh_prefix
)
{
    PaintingState result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    Session& current = session();
    if (!current.open || current.selector == nullptr) {
        result.message = "No painting session is open";
        return result;
    }
    try {
        result.status = SceneStatus::success;
        if (!from.empty()) {
            to.push_back(current.selector->serialize());
            current.selector->deserialize(from.back(), true);
            from.pop_back();
            current.stroke_pending = false;
            current.has_last = false;
        }
        write_painted_meshes(mesh_prefix, result);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

}  // namespace

PaintingState undo_painting(const std::string& mesh_prefix)
{
    return step_painting(session().undo, session().redo, mesh_prefix);
}

PaintingState redo_painting(const std::string& mesh_prefix)
{
    return step_painting(session().redo, session().undo, mesh_prefix);
}

PaintingState clear_painting(const std::string& mesh_prefix)
{
    PaintingState result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    Session& current = session();
    if (!current.open || current.selector == nullptr) {
        result.message = "No painting session is open";
        return result;
    }
    try {
        // Plater::TakeSnapshot(... "Reset selection", GizmoAction), which the
        // gizmo's stack keeps here.
        current.undo.push_back(current.selector->serialize());
        current.redo.clear();
        current.stroke_pending = false;
        current.has_last = false;
        current.selector->reset();
        result.status = SceneStatus::success;
        write_painted_meshes(mesh_prefix, result);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

PaintingState end_painting()
{
    PaintingState result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    Session& current = session();
    if (!current.open || current.selector == nullptr) {
        result.message = "No painting session is open";
        return result;
    }
    current.selector->garbage_collect();
    // FacetsAnnotation::set(): the kind painted anew, the other kinds as they
    // were; the file the session opened with while nothing changed.
    std::string& painted = current.painting[static_cast<std::size_t>(current.kind)];
    const std::string before = painted;
    painted = serialized(current.selector->serialize());
    try {
        result.facets = painted == before ? current.opened_with : write_painting(join_painting(current.painting), current.painting_path);
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        // The session closes all the same; the painting it had stays as it was.
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
    }
    current.open = false;
    current.undo.clear();
    current.redo.clear();
    current.selector.reset();
    current.tree.reset();
    current.mesh = Slic3r::TriangleMesh();
    current.painting = KindFacets();
    return result;
}

}  // namespace orcinus::orca
