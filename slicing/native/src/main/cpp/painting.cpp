// Painting a model with the filaments of the plate, ported from OrcaSlicer's
// colour painting gizmo (GLGizmoMmuSegmentation over GLGizmoPainterBase): the
// desktop app keeps a TriangleSelector per volume while the gizmo is open and
// paints into it with a cursor that follows the mouse. The app does the same
// through a painting session: it opens one for the volume it paints, sends the
// strokes of a finger, and closes it with the painted facets in hand.

#include <algorithm>
#include <cstdint>
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

/** Extruder1 is 1, as EnforcerBlockerType numbers the filaments. */
Slic3r::EnforcerBlockerType state_of(const int filament)
{
    if (filament <= 0) {
        return Slic3r::EnforcerBlockerType::NONE;
    }
    const int highest = static_cast<int>(Slic3r::EnforcerBlockerType::ExtruderMax);
    return static_cast<Slic3r::EnforcerBlockerType>(std::min(filament, highest));
}

/**
 * The painting session, which lives while the tool is open, as the desktop
 * gizmo keeps its selectors while it is shown.
 */
struct Session {
    bool open{false};
    // The mesh being painted, in its own coordinates, with the tree that finds
    // the triangle under the finger.
    Slic3r::TriangleMesh mesh;
    std::unique_ptr<Slic3r::AABBMesh> tree;
    std::unique_ptr<Slic3r::TriangleSelector> selector;
    // Where the volume stands in the world, which the cursor needs.
    Slic3r::Transform3d world{Slic3r::Transform3d::Identity()};
};

Session& session()
{
    static Session current;
    return current;
}

/** The triangles painted with each filament, written for the 3D view. */
void write_painted_meshes(const std::string& mesh_prefix, PaintingState& result)
{
    std::vector<indexed_triangle_set> per_state;
    session().selector->get_facets(per_state);
    for (std::size_t state = 1; state < per_state.size(); ++state) {
        if (per_state[state].indices.empty()) {
            continue;
        }
        const std::string path = mesh_prefix + "-" + std::to_string(state) + ".mesh";
        if (!detail::write_mesh(per_state[state], path)) {
            continue;
        }
        result.filaments.push_back(static_cast<int>(state));
        result.meshes.push_back(path);
    }
}

}  // namespace

// Called by the adapter while it loads a plate, so painted objects are sliced
// with their colours (Model's mmu_segmentation_facets).
bool apply_painted_facets(Slic3r::ModelVolume& volume, const std::string& facets)
{
    if (facets.empty()) {
        return true;
    }
    Slic3r::TriangleSelector::TriangleSplittingData data;
    if (!deserialize_facets(facets, data)) {
        return false;
    }
    volume.mmu_segmentation_facets.set_data(std::move(data));
    return true;
}

PaintingState begin_painting(
    const PlateObject& object,
    const int part,
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
        if (!facets.empty()) {
            Slic3r::TriangleSelector::TriangleSplittingData data;
            if (!deserialize_facets(facets, data)) {
                result.status = SceneStatus::model_read_failed;
                result.message = "The painted facets could not be read";
                return result;
            }
            current.selector->deserialize(data, true);
        }
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
        if (hit.face() < 0) {
            // The finger missed the model, which leaves it as it was.
            result.hit = false;
            write_painted_meshes(mesh_prefix, result);
            return result;
        }
        result.hit = true;

        const Slic3r::Vec3f position = hit.position().cast<float>();
        const Slic3r::Transform3d no_translation = Slic3r::Transform3d(current.world.linear());
        const Slic3r::TriangleSelector::ClippingPlane clipping_plane;
        const Slic3r::EnforcerBlockerType state = state_of(stroke.filament);

        switch (stroke.tool) {
        case PaintTool::brush: {
            std::unique_ptr<Slic3r::TriangleSelector::Cursor> cursor =
                Slic3r::TriangleSelector::SinglePointCursor::cursor_factory(
                    position,
                    source.cast<float>(),
                    static_cast<float>(stroke.radius),
                    Slic3r::TriangleSelector::CursorType::SPHERE,
                    current.world,
                    clipping_plane
                );
            current.selector->select_patch(hit.face(), std::move(cursor), state, no_translation, true, 0.0f);
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
                0.0f,
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
    result.facets = serialize(current.selector->serialize());
    result.status = SceneStatus::success;
    current.open = false;
    current.selector.reset();
    current.tree.reset();
    current.mesh = Slic3r::TriangleMesh();
    return result;
}

}  // namespace orcinus::orca
