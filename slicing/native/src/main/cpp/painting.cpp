// Painting a model, ported from OrcaSlicer's painting gizmos over
// GLGizmoPainterBase (colour, supports, seam and fuzzy skin): the desktop app
// keeps a TriangleSelector per model part while a gizmo is open and paints into
// them with a cursor that follows the mouse. The app does the same through a
// painting session: it opens one of a kind for the object it paints, sends the
// strokes of a finger, and closes it with the painted facets of every volume
// in hand.

#include <algorithm>
#include <array>
#include <cstdint>
#include <fstream>
#include <iterator>
#include <limits>
#include <queue>
#include <set>
#include <memory>
#include <optional>
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
 * TriangleSelectorPatch of GLGizmoPainterBase.cpp without its drawing: the
 * painting split into patches of one state that touch each other, each with
 * its area and the states around it, which the gap fill merges into the
 * state around them.
 */
class PatchSelector : public Slic3r::TriangleSelector {
public:
    using TriangleSelector::TriangleSelector;

    // TrianglePatch
    struct Patch {
        std::vector<int> facet_indices;
        std::set<Slic3r::EnforcerBlockerType> neighbor_types;
        Slic3r::EnforcerBlockerType type{Slic3r::EnforcerBlockerType::NONE};
        double area{0.0};
    };

    // TriangleSelectorPatch::GapAreaMax: patches are measured up to it.
    static constexpr double GapAreaMax = 5.0;

    // TrianglePatch::is_fragment()
    static bool is_fragment(const Patch& patch, const double gap_area) { return patch.area < gap_area; }

    // TriangleSelectorPatch::update_triangles_per_patch()
    std::vector<Patch> patches() const
    {
        std::vector<Patch> result;
        auto [neighbors, neighbors_propagated] = this->precompute_all_neighbors();
        std::vector<bool> visited(m_triangles.size(), false);

        auto get_all_touching_triangles = [this](int facet_idx, const Slic3r::Vec3i32& neighbors, const Slic3r::Vec3i32& neighbors_propagated) -> std::vector<int> {
            assert(facet_idx != -1 && facet_idx < int(m_triangles.size()));
            assert(this->verify_triangle_neighbors(m_triangles[facet_idx], neighbors));
            std::vector<int> touching_triangles;
            Slic3r::Vec3i32 vertices = { m_triangles[facet_idx].verts_idxs[0], m_triangles[facet_idx].verts_idxs[1], m_triangles[facet_idx].verts_idxs[2] };
            append_touching_subtriangles(neighbors(0), vertices(1), vertices(0), touching_triangles);
            append_touching_subtriangles(neighbors(1), vertices(2), vertices(1), touching_triangles);
            append_touching_subtriangles(neighbors(2), vertices(0), vertices(2), touching_triangles);

            for (int neighbor_idx : neighbors_propagated)
                if (neighbor_idx != -1 && !m_triangles[neighbor_idx].is_split())
                    touching_triangles.emplace_back(neighbor_idx);

            return touching_triangles;
        };

        // calc_fragment_area(): summed up to max_limit_area.
        auto calc_fragment_area = [this](const Patch& patch, double max_limit_area) {
            double total_area = 0.0;
            for (const int facet : patch.facet_indices) {
                const std::array<int, 3>& v = m_triangles[facet].verts_idxs;
                const Slic3r::Vec3f v0 = m_vertices[v[0]].v;
                const Slic3r::Vec3f v1 = m_vertices[v[1]].v;
                const Slic3r::Vec3f v2 = m_vertices[v[2]].v;
                total_area += std::abs((v0 - v1).cross(v0 - v2).norm()) / 2;
                if (total_area >= max_limit_area)
                    break;
            }
            return total_area;
        };

        std::size_t start_facet_idx = 0;
        while (true) {
            for (; start_facet_idx < visited.size(); start_facet_idx++) {
                if (!visited[start_facet_idx] && m_triangles[start_facet_idx].valid() && !m_triangles[start_facet_idx].is_split())
                    break;
            }

            if (start_facet_idx >= m_triangles.size())
                break;

            Slic3r::EnforcerBlockerType start_facet_state = m_triangles[start_facet_idx].get_state();
            Patch patch;
            std::queue<int> facet_queue;
            facet_queue.push(int(start_facet_idx));
            while (!facet_queue.empty()) {
                int current_facet = facet_queue.front();
                facet_queue.pop();
                assert(!m_triangles[current_facet].is_split());

                if (!visited[current_facet]) {
                    patch.facet_indices.push_back(current_facet);

                    std::vector<int> touching_triangles = get_all_touching_triangles(current_facet, neighbors[current_facet], neighbors_propagated[current_facet]);
                    for (const int tr_idx : touching_triangles) {
                        if (tr_idx < 0)
                            continue;

                        if (m_triangles[tr_idx].get_state() != start_facet_state) {
                            patch.neighbor_types.insert(m_triangles[tr_idx].get_state());
                            continue;
                        }

                        // should check visited state after color for neight types
                        if (visited[tr_idx])
                            continue;

                        assert(!m_triangles[tr_idx].is_split());
                        facet_queue.push(tr_idx);
                    }
                }

                visited[current_facet] = true;
            }

            patch.area = calc_fragment_area(patch, GapAreaMax);
            patch.type = start_facet_state;
            result.emplace_back(std::move(patch));
        }
        return result;
    }

    // TriangleSelectorPatch::update_selector_triangles()
    void merge_fragments(const double gap_area)
    {
        for (const Patch& patch : patches()) {
            if (!is_fragment(patch, gap_area) || patch.neighbor_types.empty())
                continue;

            Slic3r::EnforcerBlockerType type = *patch.neighbor_types.begin();
            for (int facet_idx : patch.facet_indices) {
                m_triangles[facet_idx].set_state(type);
            }
        }
    }

    // TriangleSelectorPatch::render() in the gap fill's filter state: every
    // patch in its own state, a fragment in the state of its first neighbour.
    void get_gap_filled_facets(const double gap_area, std::vector<indexed_triangle_set>& per_state) const
    {
        per_state.assign(std::size_t(Slic3r::EnforcerBlockerType::ExtruderMax) + 1, indexed_triangle_set());
        for (const Patch& patch : patches()) {
            const Slic3r::EnforcerBlockerType type =
                is_fragment(patch, gap_area) && !patch.neighbor_types.empty() ? *patch.neighbor_types.begin() : patch.type;
            indexed_triangle_set& out = per_state[std::size_t(type)];
            for (const int facet : patch.facet_indices) {
                const std::array<int, 3>& v = m_triangles[facet].verts_idxs;
                const int first = int(out.vertices.size());
                for (int corner = 0; corner < 3; ++corner) {
                    out.vertices.emplace_back(m_vertices[v[corner]].v);
                }
                out.indices.emplace_back(first, first + 1, first + 2);
            }
        }
    }
};

/**
 * A model part of the painted object while the tool is open: the desktop
 * gizmo keeps a TriangleSelectorGUI and a MeshRaycaster for every model part
 * of the object (GLGizmoPainterBase::m_triangle_selectors).
 */
struct PaintedVolume {
    // Its place in ModelObject::volumes.
    std::size_t index{0};
    // Its mesh in its own coordinates, with the tree that finds the triangle
    // under the finger; both hold on to the mesh, which keeps its place while
    // the session's volumes grow.
    std::unique_ptr<Slic3r::TriangleMesh> mesh;
    std::unique_ptr<Slic3r::AABBMesh> tree;
    std::unique_ptr<PatchSelector> selector;
    // Where it stands in the world (the gizmo's trafo matrices), which the
    // cursor needs, and in the object.
    Slic3r::Transform3d world{Slic3r::Transform3d::Identity()};
    Slic3r::Transform3d in_object{Slic3r::Transform3d::Identity()};
};

/** The painting of every model part, in the order of Session::volumes. */
using Snapshot = std::vector<Slic3r::TriangleSelector::TriangleSplittingData>;

/**
 * The painting session, which lives while the tool is open, as the desktop
 * gizmo keeps its selectors while it is shown.
 */
struct Session {
    bool open{false};
    // What the session paints.
    PaintKind kind{PaintKind::color};
    // The painting of every volume of the object (ModelObject::volumes), of
    // every kind, and the file it came in, which end_painting() hands back
    // while the volume's painting did not change; once it did, it is written
    // to "<prefix>-<volume>.painted" with the session's kind painted anew.
    std::vector<KindFacets> painting;
    std::vector<std::string> opened_with;
    std::string painting_prefix;
    // The model parts, which the finger paints.
    std::vector<PaintedVolume> volumes;
    // The gap fill tool's area while it is chosen (TriangleSelectorPatch's
    // filter state), negative otherwise.
    double gap_area{-1.0};
    // The gizmo's own undo/redo stack: the painting before each stroke, and
    // the paintings Undo left, the next one last.
    std::vector<Snapshot> undo;
    std::vector<Snapshot> redo;
    // A stroke began and has not met the model yet.
    bool stroke_pending{false};
    // Where the stroke last met the model: on which model part, in its
    // coordinates, and on which facet. The brush paints from there to where
    // the finger is now (the gizmo's DoublePointCursor between its mouse
    // positions) while it stays on the same part.
    bool has_last{false};
    std::size_t last_volume{0};
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

Snapshot snapshot_of(const Session& current)
{
    Snapshot snapshot;
    for (const PaintedVolume& volume : current.volumes) {
        snapshot.push_back(volume.selector->serialize());
    }
    return snapshot;
}

void restore(Session& current, const Snapshot& snapshot)
{
    for (std::size_t index = 0; index < current.volumes.size() && index < snapshot.size(); ++index) {
        current.volumes[index].selector->deserialize(snapshot[index], true);
    }
}

/**
 * The triangles painted in each state, written for the 3D view, and what the
 * tool can undo: per model part, those of the object's own mesh in the
 * object's coordinates, as the view draws that mesh, and those of a part in
 * the part's own, as the view places the part.
 */
void write_painted_meshes(const std::string& mesh_prefix, PaintingState& result)
{
    Session& current = session();
    result.can_undo = !current.undo.empty();
    result.can_redo = !current.redo.empty();
    const std::size_t write = current.writes++;
    for (const PaintedVolume& volume : current.volumes) {
        std::vector<indexed_triangle_set> per_state;
        if (current.gap_area >= 0.0) {
            volume.selector->get_gap_filled_facets(current.gap_area, per_state);
        } else {
            volume.selector->get_facets(per_state);
        }
        for (std::size_t state = 1; state < per_state.size(); ++state) {
            if (per_state[state].indices.empty()) {
                continue;
            }
            if (volume.index == 0) {
                its_transform(per_state[state], volume.in_object, true);
            }
            const std::string path =
                mesh_prefix + "-" + std::to_string(volume.index) + "-" + std::to_string(state) + "-" + std::to_string(write) + ".mesh";
            if (!detail::write_mesh(per_state[state], path)) {
                continue;
            }
            result.states.push_back(static_cast<int>(state));
            result.meshes.push_back(path);
            result.volumes.push_back(static_cast<int>(volume.index));
        }
    }
}

}  // namespace

PaintingState painted_colors(const PlateObject& object, const ProfileSelection& profiles, const std::string& mesh_prefix)
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
        for (std::size_t index = 0; index < loaded.volumes.size(); ++index) {
            const Slic3r::ModelVolume& volume = *loaded.volumes[index];
            if (!volume.is_model_part() || volume.mmu_segmentation_facets.empty()) {
                continue;
            }
            // GLVolume::update_mmuseg_ts(): the facets of every state of the
            // volume, the object's own mesh in the object's coordinates.
            Slic3r::TriangleSelector selector(volume.mesh());
            selector.deserialize(volume.mmu_segmentation_facets.get_data(), true);
            std::vector<indexed_triangle_set> per_state;
            selector.get_facets(per_state);
            for (std::size_t state = 1; state < per_state.size(); ++state) {
                if (per_state[state].indices.empty()) {
                    continue;
                }
                if (index == 0) {
                    its_transform(per_state[state], volume.get_matrix(), true);
                }
                const std::string path = mesh_prefix + "-" + std::to_string(index) + "-" + std::to_string(state) + ".mesh";
                if (!detail::write_mesh(per_state[state], path)) {
                    continue;
                }
                result.states.push_back(static_cast<int>(state));
                result.meshes.push_back(path);
                result.volumes.push_back(static_cast<int>(index));
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

PaintingState renumber_painted_filaments(
    const PlateObject& object,
    const int filament_count,
    const int deleted_filament,
    const int replace_filament,
    const ProfileSelection& profiles,
    const std::string& output_prefix
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
        std::vector<std::string> facets{object.painted};
        for (const ObjectPart& part : object.parts) {
            facets.push_back(part.painted);
        }
        Slic3r::ModelObject& loaded = *model.objects.front();
        for (std::size_t index = 0; index < loaded.volumes.size() && index < facets.size(); ++index) {
            Slic3r::ModelVolume& volume = *loaded.volumes[index];
            if (volume.mmu_segmentation_facets.empty()) {
                continue;
            }
            // Orca renumbers a volume once one of its filaments is the deleted one
            // or after it, or beyond the count; the facets of the filaments before
            // stay as they are, so every painted volume goes through it here.
            volume.mmu_segmentation_facets.set_enforcer_block_type_limit(volume, static_cast<Slic3r::EnforcerBlockerType>(filament_count),
                static_cast<Slic3r::EnforcerBlockerType>(deleted_filament), static_cast<Slic3r::EnforcerBlockerType>(replace_filament));
            facets[index] = painted_facets_of(volume, output_prefix + "-" + std::to_string(index) + ".painted");
        }
        result.facets = facets.front();
        result.part_facets.assign(facets.begin() + 1, facets.end());
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

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
    const PaintKind kind,
    const ProfileSelection& profiles,
    const std::string& mesh_prefix,
    const PaintPlacement& placement
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
        if (loaded.volumes.size() != object.parts.size() + 1) {
            result.status = SceneStatus::model_read_failed;
            result.message = "The object's volumes do not match its parts";
            return result;
        }

        Session& current = session();
        current = Session();
        current.kind = kind;
        current.painting_prefix = mesh_prefix;
        // GLGizmoPainterBase's trafo matrices: the selected copy, or in the
        // assembly view its assemble transformation with the explosion's offsets.
        const std::size_t copy = placement.instance > 0 && std::size_t(placement.instance) < loaded.instances.size() ? std::size_t(placement.instance) : 0;
        for (std::size_t index = 0; index < loaded.volumes.size(); ++index) {
            const Slic3r::ModelVolume& volume = *loaded.volumes[index];
            // The painting the app keeps for the object's own mesh and each part.
            const std::string& facets = index == 0 ? object.painted : object.parts[index - 1].painted;
            std::string opened;
            if (!read_painting(facets, opened)) {
                result.status = SceneStatus::model_read_failed;
                result.message = "The painted facets could not be read";
                return result;
            }
            current.painting.push_back(split_painting(opened));
            current.opened_with.push_back(facets);
            // The gizmo paints the model parts alone.
            if (!volume.is_model_part()) {
                continue;
            }
            PaintedVolume& painted = current.volumes.emplace_back();
            painted.index = index;
            painted.mesh = std::make_unique<Slic3r::TriangleMesh>(volume.mesh());
            painted.tree = std::make_unique<Slic3r::AABBMesh>(*painted.mesh);
            // GLGizmoMmuSegmentation::init_model_triangle_selectors() splits the
            // triangles down to 0.2 mm; the other painting gizmos keep
            // TriangleSelector's own limit.
            painted.selector = kind == PaintKind::color ? std::make_unique<PatchSelector>(*painted.mesh, 0.2f)
                                                        : std::make_unique<PatchSelector>(*painted.mesh);
            painted.in_object = volume.get_matrix();
            if (loaded.instances.empty()) {
                painted.world = volume.get_matrix();
            } else if (placement.assembly_view) {
                const Slic3r::ModelInstance& instance = *loaded.instances[copy];
                painted.world = instance.get_assemble_transformation().get_matrix() * volume.get_matrix();
                painted.world.translate(
                    volume.get_transformation().get_offset() * (placement.explosion_ratio - 1.0) +
                    instance.get_offset_to_assembly() * (placement.explosion_ratio - 1.0));
            } else {
                painted.world = loaded.instances[copy]->get_transformation().get_matrix() * volume.get_matrix();
            }
            const std::string& kind_facets = current.painting.back()[static_cast<std::size_t>(kind)];
            if (!kind_facets.empty()) {
                Slic3r::TriangleSelector::TriangleSplittingData data;
                if (!deserialize_facets(kind_facets, data)) {
                    result.status = SceneStatus::model_read_failed;
                    result.message = "The painted facets could not be read";
                    return result;
                }
                painted.selector->deserialize(data, true);
            }
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
    if (!current.open || current.volumes.empty()) {
        result.message = "No painting session is open";
        return result;
    }

    try {
        const Slic3r::Vec3d origin(stroke.origin[0], stroke.origin[1], stroke.origin[2]);
        const Slic3r::Vec3d along(stroke.direction[0], stroke.direction[1], stroke.direction[2]);
        const Slic3r::Vec3d clip_normal = Slic3r::Vec3d(stroke.clipping_plane[0], stroke.clipping_plane[1], stroke.clipping_plane[2]).normalized();
        const double clip_offset = stroke.clipping_plane[3];
        const bool clipping = clip_offset != std::numeric_limits<double>::max();

        // GLGizmoPainterBase::update_raycast_cache(): the finger's ray cast on
        // every model part in its own coordinates, and the hit nearest the
        // camera kept, which stands at the ray's origin.
        struct Met {
            std::size_t volume{0};
            Slic3r::Vec3d source;
            Slic3r::Vec3f position;
            int face{-1};
        };
        std::optional<Met> nearest;
        double nearest_distance = std::numeric_limits<double>::max();
        for (std::size_t index = 0; index < current.volumes.size(); ++index) {
            const PaintedVolume& volume = current.volumes[index];
            const Slic3r::Transform3d to_mesh = volume.world.inverse();
            const Slic3r::Vec3d source = to_mesh * origin;
            const Slic3r::Vec3d direction = (to_mesh * (origin + along) - source).normalized();
            // MeshRaycaster::unproject_on_mesh(): the nearest hit above the bed
            // (sinking objects) and not cut by the clipping plane; with an odd
            // number of such hits the nearest is from inside the mesh.
            const std::vector<Slic3r::AABBMesh::hit_result> hits = volume.tree->query_ray_hits(source, direction);
            std::size_t first = 0;
            for (; first < hits.size(); ++first) {
                const Slic3r::Vec3d transformed_hit = volume.world * hits[first].position();
                if (transformed_hit.z() >= (stroke.sinking_limit ? Slic3r::SINKING_Z_THRESHOLD : -std::numeric_limits<double>::max()) &&
                    (!clipping || -clip_normal.dot(transformed_hit) + clip_offset >= 0.))
                    break;
            }
            if (first == hits.size() || (hits.size() - first) % 2 != 0) {
                continue;
            }
            const double distance = (origin - volume.world * hits[first].position()).squaredNorm();
            if (distance < nearest_distance) {
                nearest_distance = distance;
                nearest = Met{index, source, hits[first].position().cast<float>(), hits[first].face()};
            }
        }
        result.status = SceneStatus::success;
        if (stroke.starts) {
            current.stroke_pending = true;
            current.has_last = false;
        }
        if (!nearest) {
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
            current.undo.push_back(snapshot_of(current));
            current.redo.clear();
            current.stroke_pending = false;
        }

        // get_clipping_plane_in_volume_coordinates()
        const auto clipping_plane_of = [&](const PaintedVolume& painted) {
            if (!clipping) {
                return Slic3r::TriangleSelector::ClippingPlane();
            }
            const Slic3r::Transform3d trafo_normal = Slic3r::Transform3d(painted.world.linear().transpose());
            const Slic3r::Transform3d trafo_inv = painted.world.inverse();
            const Slic3r::Vec3d point_on_plane = clip_normal * clip_offset;
            const Slic3r::Vec3d point_on_plane_transformed = trafo_inv * point_on_plane;
            const Slic3r::Vec3d normal_transformed = trafo_normal * clip_normal;
            const auto offset_transformed = float(point_on_plane_transformed.dot(normal_transformed));
            return Slic3r::TriangleSelector::ClippingPlane(
                std::array<float, 4>{float(normal_transformed.x()), float(normal_transformed.y()), float(normal_transformed.z()), offset_transformed}
            );
        };
        PaintedVolume& volume = current.volumes[nearest->volume];
        const Slic3r::Vec3f& position = nearest->position;
        const int face = nearest->face;
        const Slic3r::Vec3d& source = nearest->source;
        const Slic3r::Transform3d no_translation = Slic3r::Transform3d(volume.world.linear());
        const Slic3r::TriangleSelector::ClippingPlane clipping_plane = clipping_plane_of(volume);
        const Slic3r::EnforcerBlockerType state = state_of(stroke.state);
        // m_paint_on_overhangs_only ? m_highlight_by_angle_threshold_deg : 0.f
        const auto overhang_angle = static_cast<float>(stroke.overhang_angle);

        switch (stroke.tool) {
        case PaintTool::brush:
        case PaintTool::circle: {
            // The camera looks along the finger's ray, from its origin. Along
            // a stroke the brush paints the capsule from where it last met the
            // same part, as the gizmo joins its mouse positions on a mesh.
            const Slic3r::TriangleSelector::CursorType type = stroke.tool == PaintTool::circle
                ? Slic3r::TriangleSelector::CursorType::CIRCLE
                : Slic3r::TriangleSelector::CursorType::SPHERE;
            const auto radius = static_cast<float>(stroke.radius);
            if (current.has_last && current.last_volume == nearest->volume) {
                std::unique_ptr<Slic3r::TriangleSelector::Cursor> cursor =
                    Slic3r::TriangleSelector::DoublePointCursor::cursor_factory(
                        current.last_position, position, source.cast<float>(), radius, type, volume.world, clipping_plane);
                volume.selector->select_patch(current.last_face, std::move(cursor), state, no_translation, true, overhang_angle);
            } else {
                std::unique_ptr<Slic3r::TriangleSelector::Cursor> cursor =
                    Slic3r::TriangleSelector::SinglePointCursor::cursor_factory(position, source.cast<float>(), radius, type, volume.world, clipping_plane);
                volume.selector->select_patch(face, std::move(cursor), state, no_translation, true, overhang_angle);
            }
            current.has_last = true;
            current.last_volume = nearest->volume;
            current.last_position = position;
            current.last_face = face;
            break;
        }
        case PaintTool::fill:
            // The smart fill of the desktop gizmo: the facets that lie flat
            // enough against the one touched take the colour with it.
            volume.selector->seed_fill_select_triangles(
                position,
                face,
                no_translation,
                clipping_plane,
                static_cast<float>(stroke.angle),
                overhang_angle,
                true
            );
            volume.selector->seed_fill_apply_on_triangles(state);
            break;
        case PaintTool::gap_fill:
            // The gap fill paints no strokes (GLGizmoPainterBase::gizmo_event()).
            break;
        case PaintTool::height_range: {
            // get_projected_height_range(): from the height the finger meets the
            // model at up the cursor's height, on the part it meets and on every
            // other part with a facet within that band, from its first such facet.
            const float z_bot_world = float((volume.world * position.cast<double>()).z());
            const float z_top_world = z_bot_world + float(stroke.cursor_height);
            for (std::size_t index = 0; index < current.volumes.size(); ++index) {
                PaintedVolume& painted = current.volumes[index];
                int first_hit_facet_idx = index == nearest->volume ? face : -1;
                if (index != nearest->volume) {
                    const indexed_triangle_set& its = painted.mesh->its;
                    for (int facet_idx = 0; facet_idx < int(its.indices.size()); ++facet_idx) {
                        float z[3];
                        for (int corner = 0; corner < 3; ++corner) {
                            const Slic3r::Vec3f& v = its.vertices[its.indices[facet_idx](corner)];
                            z[corner] = float((painted.world * Slic3r::Vec3d(v(0), v(1), v(2))).z());
                        }
                        const bool outside_range = (z[0] < z_bot_world && z[1] < z_bot_world && z[2] < z_bot_world) ||
                                                   (z[0] > z_top_world && z[1] > z_top_world && z[2] > z_top_world);
                        if (!outside_range) {
                            first_hit_facet_idx = facet_idx;
                            break;
                        }
                    }
                }
                if (first_hit_facet_idx < 0) {
                    continue;
                }
                // The camera at the ray's origin, in the part's coordinates.
                const Slic3r::Vec3f camera_pos = (painted.world.inverse() * origin).cast<float>();
                std::unique_ptr<Slic3r::TriangleSelector::Cursor> cursor = Slic3r::TriangleSelector::SinglePointCursor::cursor_factory(
                    z_bot_world, camera_pos, float(stroke.cursor_height), painted.world, clipping_plane_of(painted));
                painted.selector->select_patch(
                    first_hit_facet_idx, std::move(cursor), state, Slic3r::Transform3d(painted.world.linear()), true, overhang_angle);
            }
            break;
        }
        case PaintTool::triangle:
            // The Triangles tool: the triangle under the finger alone, which the
            // stroke paints as it passes over one after another.
            volume.selector->bucket_fill_select_triangles(position, face, clipping_plane, -1.f, false, true);
            volume.selector->seed_fill_apply_on_triangles(state);
            break;
        case PaintTool::bucket:
            volume.selector->bucket_fill_select_triangles(
                position,
                face,
                clipping_plane,
                static_cast<float>(stroke.angle),
                true,
                true
            );
            volume.selector->seed_fill_apply_on_triangles(state);
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
PaintingState step_painting(std::vector<Snapshot>& from, std::vector<Snapshot>& to, const std::string& mesh_prefix)
{
    PaintingState result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    Session& current = session();
    if (!current.open || current.volumes.empty()) {
        result.message = "No painting session is open";
        return result;
    }
    try {
        result.status = SceneStatus::success;
        if (!from.empty()) {
            to.push_back(snapshot_of(current));
            restore(current, from.back());
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
    if (!current.open || current.volumes.empty()) {
        result.message = "No painting session is open";
        return result;
    }
    try {
        // Plater::TakeSnapshot(... "Reset selection", GizmoAction), which the
        // gizmo's stack keeps here; every model part is reset.
        current.undo.push_back(snapshot_of(current));
        current.redo.clear();
        current.stroke_pending = false;
        current.has_last = false;
        for (PaintedVolume& volume : current.volumes) {
            volume.selector->reset();
        }
        result.status = SceneStatus::success;
        write_painted_meshes(mesh_prefix, result);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

PaintingState remap_painting(const std::vector<int>& remap, const std::string& mesh_prefix)
{
    PaintingState result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    Session& current = session();
    if (!current.open || current.volumes.empty()) {
        result.message = "No painting session is open";
        return result;
    }
    try {
        // Identity mapping by default; filament i + 1 (Extruder1 + i) goes to remap[i] + 1.
        constexpr std::size_t max_state = std::size_t(Slic3r::EnforcerBlockerType::ExtruderMax);
        Slic3r::EnforcerBlockerStateMap state_map;
        for (std::size_t state = 0; state <= max_state; ++state) {
            state_map[state] = static_cast<Slic3r::EnforcerBlockerType>(state);
        }
        const int start_extruder = int(Slic3r::EnforcerBlockerType::Extruder1);
        bool any_change = false;
        for (std::size_t src = 0; src < std::min(remap.size(), max_state); ++src) {
            const int dst = remap[src];
            if (dst != int(src) && dst >= 0 && std::size_t(dst) < max_state) {
                state_map[src + start_extruder] = static_cast<Slic3r::EnforcerBlockerType>(dst + start_extruder);
                any_change = true;
            }
        }
        if (any_change) {
            // Plater::TakeSnapshot(... "Remap filament assignments", GizmoAction),
            // which the gizmo's stack keeps here.
            current.undo.push_back(snapshot_of(current));
            current.redo.clear();
            current.stroke_pending = false;
            current.has_last = false;
            for (PaintedVolume& volume : current.volumes) {
                volume.selector->remap_triangle_state(state_map);
            }
        }
        result.status = SceneStatus::success;
        write_painted_meshes(mesh_prefix, result);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

PaintingState set_gap_fill(const double gap_area, const std::string& mesh_prefix)
{
    PaintingState result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    Session& current = session();
    if (!current.open || current.volumes.empty()) {
        result.message = "No painting session is open";
        return result;
    }
    try {
        current.gap_area = gap_area;
        result.status = SceneStatus::success;
        write_painted_meshes(mesh_prefix, result);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

PaintingState fill_gaps(const std::string& mesh_prefix)
{
    PaintingState result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    Session& current = session();
    if (!current.open || current.volumes.empty() || current.gap_area < 0.0) {
        result.message = "The gap fill is not chosen";
        return result;
    }
    try {
        // Plater::TakeSnapshot(... "Reset selection", GizmoAction), which the
        // gizmo's stack keeps here; every model part's fragments are merged
        // (TriangleSelectorPatch::update_selector_triangles() of each).
        current.undo.push_back(snapshot_of(current));
        current.redo.clear();
        current.stroke_pending = false;
        current.has_last = false;
        for (PaintedVolume& volume : current.volumes) {
            volume.selector->merge_fragments(current.gap_area);
        }
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
    if (!current.open || current.volumes.empty()) {
        result.message = "No painting session is open";
        return result;
    }
    // FacetsAnnotation::set() of every model part: the kind painted anew, the
    // other kinds as they were; the file a volume came in while its painting
    // did not change, and every volume that is no model part as it was.
    std::vector<std::string> facets = current.opened_with;
    try {
        for (PaintedVolume& volume : current.volumes) {
            volume.selector->garbage_collect();
            std::string& painted = current.painting[volume.index][static_cast<std::size_t>(current.kind)];
            const std::string before = painted;
            painted = serialized(volume.selector->serialize());
            if (painted != before) {
                facets[volume.index] = write_painting(
                    join_painting(current.painting[volume.index]), current.painting_prefix + "-" + std::to_string(volume.index) + ".painted");
            }
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        // The session closes all the same; the painting it had stays as it was.
        facets = current.opened_with;
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
    }
    result.facets = facets.front();
    result.part_facets.assign(facets.begin() + 1, facets.end());
    current = Session();
    return result;
}

bool fuzzy_skin_disabled(const PlateObject& object, const ProfileSelection& profiles)
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().bundle == nullptr) {
        return false;
    }
    Slic3r::DynamicPrintConfig config;
    std::string message;
    if (detail::select_profiles(*detail::engine().bundle, profiles, config, message) != SliceStatus::success) {
        return false;
    }
    // GLGizmoFuzzySkin::on_render_input_window(): the object's own value, or
    // else the process preset's.
    const Slic3r::DynamicPrintConfig object_config = detail::model_config(object.settings);
    const Slic3r::DynamicPrintConfig& effective = object_config.option("fuzzy_skin") != nullptr ? object_config : config;
    const auto* fuzzy_skin = effective.option<Slic3r::ConfigOptionEnum<Slic3r::FuzzySkinType>>("fuzzy_skin");
    return fuzzy_skin != nullptr && fuzzy_skin->value == Slic3r::FuzzySkinType::Disabled_fuzzy;
}

}  // namespace orcinus::orca
