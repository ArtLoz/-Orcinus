// Text and SVG embossed on a model, as OrcaSlicer's text and SVG tools make
// them (GLGizmoEmboss and GLGizmoSVG with their jobs in EmbossJob.cpp): the
// shape of the text's glyphs or of the SVG's paths, extruded as deep as the
// projection says, onto the object's surface when it uses the surface, and
// glyph by glyph along the surface when the text is placed per glyph. The
// volume keeps what it was embossed from (ModelVolume::text_configuration
// and emboss_shape), which travels between the app and the engine in a file
// of its own and goes into a project as the desktop app stores it.

#include <algorithm>
#include <cmath>
#include <cstring>
#include <fstream>
#include <limits>
#include <list>
#include <mutex>
#include <optional>
#include <string>
#include <vector>

#include <boost/algorithm/string/predicate.hpp>
#include <boost/filesystem.hpp>
#include <boost/nowide/convert.hpp>
#include <cereal/archives/binary.hpp>

#include "libslic3r/AABBMesh.hpp"
#include "libslic3r/AABBTreeLines.hpp"
#include "libslic3r/ClipperUtils.hpp"
#include "libslic3r/CutSurface.hpp"
#include "libslic3r/Emboss.hpp"
#include "libslic3r/EmbossShape.hpp"
#include "libslic3r/ExPolygonsIndex.hpp"
#include "libslic3r/Format/OBJ.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/NSVGUtils.hpp"
#include "libslic3r/TextConfiguration.hpp"
#include "libslic3r/TriangleMeshSlicer.hpp"
#include "libslic3r/Utils.hpp"

// stb_truetype's declarations; Emboss.cpp holds its implementation.
#include "imgui/imstb_truetype.h"

#include "engine_context.hpp"
#include "orca_engine_adapter.hpp"

namespace orcinus::orca {
namespace {

using Slic3r::Transform3d;
using Slic3r::Vec2d;
using Slic3r::Vec3d;

// EmbossJob.cpp: the offset of the closed side of a volume from the model.
constexpr float SAFE_SURFACE_OFFSET = 0.015f; // [in mm]
// EmbossJob.cpp: how far the cut of the surface reaches past the model.
constexpr float SAFE_EXTENSION = 1.0f;
// SurfaceDrag.hpp
constexpr double UP_LIMIT = 0.9;
// SurfaceDrag.cpp: the distances of an embossed volume from the surface that
// count as a distance, squared; the largest grows with the volume's depth.
constexpr double SURFACE_DISTANCE_SQ_MIN = 1e-4;
constexpr double SURFACE_DISTANCE_SQ_MAX = 10.;

bool never_canceled() { return false; }

// ---------------------------------------------------------------- fonts --

std::uint16_t read_u16(const unsigned char* data) { return std::uint16_t((data[0] << 8) | data[1]); }

std::uint32_t read_u32(const unsigned char* data)
{
    return (std::uint32_t(data[0]) << 24) | (std::uint32_t(data[1]) << 16) | (std::uint32_t(data[2]) << 8) | std::uint32_t(data[3]);
}

// The offset of the table with tag in the face starting at fontstart; 0 for none.
std::uint32_t find_table(const std::vector<unsigned char>& data, std::uint32_t fontstart, const char* tag)
{
    if (std::size_t(fontstart) + 12 > data.size()) {
        return 0;
    }
    const std::uint16_t count = read_u16(data.data() + fontstart + 4);
    for (std::uint16_t table = 0; table < count; ++table) {
        const std::size_t record = std::size_t(fontstart) + 12 + 16 * std::size_t(table);
        if (record + 16 > data.size()) {
            return 0;
        }
        if (std::memcmp(data.data() + record, tag, 4) == 0) {
            return read_u32(data.data() + record + 8);
        }
    }
    return 0;
}

// A name of the naming table as UTF-8: the Windows Unicode entries in
// English first, then any Windows Unicode entry, then the Macintosh Roman one.
std::string font_name(const stbtt_fontinfo& info, int name_id)
{
    const auto from_utf16 = [](const char* name, int length) {
        std::u16string utf16;
        for (int at = 0; at + 1 < length; at += 2) {
            utf16.push_back(char16_t((static_cast<unsigned char>(name[at]) << 8) | static_cast<unsigned char>(name[at + 1])));
        }
        return boost::nowide::narrow(std::wstring(utf16.begin(), utf16.end()));
    };
    int length = 0;
    for (const int encoding : {STBTT_MS_EID_UNICODE_BMP, STBTT_MS_EID_UNICODE_FULL}) {
        if (const char* name = stbtt_GetFontNameString(&info, &length, STBTT_PLATFORM_ID_MICROSOFT, encoding, STBTT_MS_LANG_ENGLISH, name_id)) {
            return from_utf16(name, length);
        }
    }
    if (const char* name = stbtt_GetFontNameString(&info, &length, STBTT_PLATFORM_ID_MAC, STBTT_MAC_EID_ROMAN, STBTT_MAC_LANG_ENGLISH, name_id)) {
        return std::string(name, std::size_t(length));
    }
    return {};
}

// ------------------------------------------------- the emboss data file --

// What a volume was embossed from, as the app and the engine hand it over:
// the undo/redo serialization of OrcaSlicer, with the description of the font
// (FontProp::family, face_name, style and weight), which that leaves out and
// a project stores.
struct StoredEmboss {
    std::optional<Slic3r::TextConfiguration> text;
    std::optional<Slic3r::EmbossShape> shape;

    template<class Archive> void save(Archive& archive) const
    {
        archive(text, shape);
        const Slic3r::FontProp* prop = text.has_value() ? &text->style.prop : nullptr;
        const std::optional<std::string> none;
        archive(prop ? prop->family : none, prop ? prop->face_name : none, prop ? prop->style : none, prop ? prop->weight : none);
    }

    template<class Archive> void load(Archive& archive)
    {
        archive(text, shape);
        std::optional<std::string> family, face_name, style, weight;
        archive(family, face_name, style, weight);
        if (text.has_value()) {
            text->style.prop.family = std::move(family);
            text->style.prop.face_name = std::move(face_name);
            text->style.prop.style = std::move(style);
            text->style.prop.weight = std::move(weight);
        }
    }
};

// ------------------------------------------------------ the text's style --

Slic3r::EmbossStyle emboss_style_of(const TextStyle& style)
{
    Slic3r::EmbossStyle result;
    result.name = style.name;
    result.path = style.font_path;
    result.type = Slic3r::EmbossStyle::Type::file_path;
    Slic3r::FontProp& prop = result.prop;
    prop.size_in_mm = float(style.size_in_mm);
    prop.per_glyph = style.per_glyph;
    prop.align = {
        static_cast<Slic3r::FontProp::HorizontalAlign>(std::clamp(style.horizontal_align, 0, 2)),
        static_cast<Slic3r::FontProp::VerticalAlign>(std::clamp(style.vertical_align, 0, 2)),
    };
    if (style.char_gap.has_value()) prop.char_gap = *style.char_gap;
    if (style.line_gap.has_value()) prop.line_gap = *style.line_gap;
    if (style.boldness.has_value()) prop.boldness = float(*style.boldness);
    if (style.skew.has_value()) prop.skew = float(*style.skew);
    // The first face of a collection is no collection number (GLGizmoEmboss::draw_advanced()).
    if (style.collection_number.has_value() && *style.collection_number > 0) prop.collection_number = unsigned(*style.collection_number);
    if (!style.family.empty()) prop.family = style.family;
    if (!style.face_name.empty()) prop.face_name = style.face_name;
    if (!style.style.empty()) prop.style = style.style;
    if (!style.weight.empty()) prop.weight = style.weight;
    return result;
}

TextStyle text_style_of(const Slic3r::EmbossStyle& emboss_style, const Slic3r::EmbossProjection& projection)
{
    TextStyle result;
    result.name = emboss_style.name;
    result.font_path = emboss_style.path;
    const Slic3r::FontProp& prop = emboss_style.prop;
    result.size_in_mm = prop.size_in_mm;
    result.per_glyph = prop.per_glyph;
    result.horizontal_align = int(prop.align.first);
    result.vertical_align = int(prop.align.second);
    if (prop.char_gap.has_value()) result.char_gap = *prop.char_gap;
    if (prop.line_gap.has_value()) result.line_gap = *prop.line_gap;
    if (prop.boldness.has_value()) result.boldness = *prop.boldness;
    if (prop.skew.has_value()) result.skew = *prop.skew;
    if (prop.collection_number.has_value()) result.collection_number = int(*prop.collection_number);
    result.family = prop.family.value_or("");
    result.face_name = prop.face_name.value_or("");
    result.style = prop.style.value_or("");
    result.weight = prop.weight.value_or("");
    result.depth = projection.depth;
    result.use_surface = projection.use_surface;
    return result;
}

Slic3r::EmbossProjection projection_of(const TextStyle& style)
{
    Slic3r::EmbossProjection projection;
    projection.depth = style.depth;
    projection.use_surface = style.use_surface;
    return projection;
}

std::optional<float> optional_float(const std::optional<double>& value)
{
    return value.has_value() ? std::optional<float>(float(*value)) : std::nullopt;
}

// The fonts the text tool read last, by their files, with the shapes of the
// glyphs they gave (StyleManager keeps the active one and its cache).
Slic3r::Emboss::FontFileWithCache font_file(const std::string& path)
{
    struct LoadedFont {
        std::string path;
        Slic3r::Emboss::FontFileWithCache font;
    };
    static std::list<LoadedFont> loaded;
    const auto found = std::find_if(loaded.begin(), loaded.end(), [&path](const LoadedFont& font) { return font.path == path; });
    if (found != loaded.end()) {
        loaded.splice(loaded.begin(), loaded, found);
        return loaded.front().font;
    }
    std::unique_ptr<Slic3r::Emboss::FontFile> file = Slic3r::Emboss::create_font_file(path.c_str());
    if (file == nullptr) {
        return {};
    }
    loaded.push_front({path, Slic3r::Emboss::FontFileWithCache(std::move(file))});
    constexpr std::size_t kept = 4;
    while (loaded.size() > kept) {
        loaded.pop_back();
    }
    return loaded.front().font;
}

// ---------------------------------------------- the shape and its mesh --

// DataBase and TextDataBase of EmbossJob.hpp and GLGizmoEmboss.cpp: what a
// volume is embossed from, the text with its font making its shape.
struct EmbossInput {
    std::string volume_name;
    Slic3r::EmbossShape shape;
    // True (raised) moves outside from the surface (MODEL_PART), false
    // (engraved) into the object (NEGATIVE_VOLUME).
    bool is_outside{true};
    // The lines of the text along the surface, per glyph; empty otherwise.
    Slic3r::Emboss::TextLines text_lines;
    // The distance from a flat surface.
    std::optional<float> from_surface;
    std::optional<Slic3r::TextConfiguration> text;
    Slic3r::Emboss::FontFileWithCache font;

    // TextDataBase::create_shape()
    Slic3r::EmbossShape& create_shape()
    {
        if (!text.has_value() || !shape.shapes_with_ids.empty()) {
            return shape;
        }
        const std::wstring text_w = boost::nowide::widen(text->text.c_str());
        shape.shapes_with_ids = Slic3r::Emboss::text2vshapes(font, text_w, text->style.prop, never_canceled);
        return shape;
    }

    // DataBase::write() and TextDataBase::write()
    void write(Slic3r::ModelVolume& volume) const
    {
        volume.name = volume_name;
        volume.emboss_shape = shape;
        volume.emboss_shape->fix_3mf_tr.reset();
        if (text.has_value()) {
            volume.text_configuration = *text;
        }
    }
};

// create_shape() of EmbossJob.cpp
Slic3r::ExPolygons create_shape(EmbossInput& input)
{
    Slic3r::EmbossShape& shape = input.create_shape();
    return Slic3r::union_with_delta(shape, Slic3r::Emboss::UNION_DELTA, Slic3r::Emboss::UNION_MAX_ITERATIN);
}

// create_line_bounds() of EmbossJob.cpp
std::vector<Slic3r::BoundingBoxes> create_line_bounds(const Slic3r::ExPolygonsWithIds& shapes, std::size_t count_lines)
{
    std::vector<Slic3r::BoundingBoxes> result(count_lines);
    std::size_t text_line_index = 0;
    for (const Slic3r::ExPolygonsWithId& shape_id : shapes) {
        const Slic3r::ExPolygons& shape = shape_id.expoly;
        Slic3r::BoundingBox bb;
        if (!shape.empty()) {
            bb = Slic3r::get_extents(shape);
        }
        if (text_line_index < result.size()) {
            result[text_line_index].push_back(bb);
        }
        if (shape_id.id == Slic3r::Emboss::ENTER_UNICODE) {
            ++text_line_index;
        }
    }
    return result;
}

// create_mesh_per_glyph() of EmbossJob.cpp
Slic3r::TriangleMesh create_mesh_per_glyph(EmbossInput& input)
{
    using namespace Slic3r;
    using namespace Slic3r::Emboss;
    const EmbossShape& shape = input.create_shape();
    if (shape.shapes_with_ids.empty()) {
        return {};
    }
    const std::size_t count_lines = input.text_lines.size();
    std::vector<BoundingBoxes> bbs = create_line_bounds(shape.shapes_with_ids, count_lines);

    const double depth = shape.projection.depth / shape.scale;
    const auto scale_tr = Eigen::Scaling(shape.scale);

    // half of font em size for direction of letter emboss
    const double em_2_mm = 5.;
    const coord_t em_2_polygon = static_cast<coord_t>(std::round(scale_(em_2_mm)));

    std::size_t s_i_offset = 0; // shape index offset(for next lines)
    indexed_triangle_set result;
    for (std::size_t text_line_index = 0; text_line_index < input.text_lines.size(); ++text_line_index) {
        const BoundingBoxes& line_bbs = bbs[text_line_index];
        const TextLine& line = input.text_lines[text_line_index];
        PolygonPoints samples = sample_slice(line, line_bbs, shape.scale);
        std::vector<double> angles = calculate_angles(em_2_polygon, samples, line.polygon);

        for (std::size_t i = 0; i < line_bbs.size(); ++i) {
            const BoundingBox& letter_bb = line_bbs[i];
            if (!letter_bb.defined) {
                continue;
            }
            Vec2d to_zero_vec = letter_bb.center().cast<double>() * shape.scale; // [in mm]
            float surface_offset = input.is_outside ? -SAFE_SURFACE_OFFSET : float(-shape.projection.depth + SAFE_SURFACE_OFFSET);
            if (input.from_surface.has_value()) {
                surface_offset += *input.from_surface;
            }
            Eigen::Translation<double, 3> to_zero(-to_zero_vec.x(), 0., static_cast<double>(surface_offset));

            const double& angle = angles[i];
            Eigen::AngleAxisd rotate(angle + M_PI_2, Vec3d::UnitY());

            const PolygonPoint& sample = samples[i];
            Vec2d offset_vec = unscale(sample.point); // [in mm]
            Eigen::Translation<double, 3> offset_tr(offset_vec.x(), 0., -offset_vec.y());
            Transform3d tr = offset_tr * rotate * to_zero * scale_tr;

            const ExPolygons& letter_shape = shape.shapes_with_ids[s_i_offset + i].expoly;
            auto projectZ = std::make_unique<ProjectZ>(depth);
            ProjectTransform project(std::move(projectZ), tr);
            indexed_triangle_set glyph_its = polygons2model(letter_shape, project);
            its_merge(result, std::move(glyph_its));
        }
        s_i_offset += line_bbs.size();
    }
    return TriangleMesh(std::move(result));
}

// try_create_mesh() of EmbossJob.cpp
Slic3r::TriangleMesh try_create_mesh(EmbossInput& input)
{
    using namespace Slic3r;
    using namespace Slic3r::Emboss;
    if (!input.text_lines.empty()) {
        TriangleMesh tm = create_mesh_per_glyph(input);
        if (!tm.empty()) {
            return tm;
        }
    }
    ExPolygons shapes = create_shape(input);
    if (shapes.empty()) {
        return {};
    }
    // NOTE: SHAPE_SCALE is applied in ProjectZ
    const double scale = input.shape.scale;
    const double depth = input.shape.projection.depth / scale;
    auto projectZ = std::make_unique<ProjectZ>(depth);
    float offset = input.is_outside ? -SAFE_SURFACE_OFFSET : float(SAFE_SURFACE_OFFSET - input.shape.projection.depth);
    if (input.from_surface.has_value()) {
        offset += *input.from_surface;
    }
    Transform3d tr = Eigen::Translation<double, 3>(0., 0., static_cast<double>(offset)) * Eigen::Scaling(scale);
    ProjectTransform project(std::move(projectZ), tr);
    return TriangleMesh(polygons2model(shapes, project));
}

// create_default_mesh() of EmbossJob.cpp
Slic3r::TriangleMesh create_default_mesh()
{
    // When cant load any font use default object loaded from file
    const std::string path = Slic3r::resources_dir() + "/data/embossed_text.obj";
    Slic3r::TriangleMesh triangle_mesh;
    std::string message;
    Slic3r::ObjInfo obj_info;
    if (!Slic3r::load_obj(path.c_str(), &triangle_mesh, obj_info, message)) {
        // when can't load mesh use cube
        return Slic3r::TriangleMesh(Slic3r::its_make_cube(36., 4., 2.5));
    }
    return triangle_mesh;
}

// create_mesh() of EmbossJob.cpp: some shape is always made, as the window
// opens on the new volume.
Slic3r::TriangleMesh create_mesh(EmbossInput& input)
{
    Slic3r::TriangleMesh result = try_create_mesh(input);
    if (result.its.empty()) {
        result = create_default_mesh();
    }
    return result;
}

// SurfaceVolumeData::ModelSource of EmbossJob.hpp: a volume the surface is cut from.
struct ModelSource {
    std::shared_ptr<const Slic3r::TriangleMesh> mesh;
    Transform3d tr;
};
using ModelSources = std::vector<ModelSource>;

// create_sources() of EmbossJob.cpp: the model parts of the object, but the embossed volume.
ModelSources create_sources(const Slic3r::ModelVolumePtrs& volumes, std::optional<std::size_t> text_volume_id = {})
{
    ModelSources result;
    result.reserve(volumes.size());
    for (const Slic3r::ModelVolume* v : volumes) {
        if (text_volume_id.has_value() && v->id().id == *text_volume_id) {
            continue;
        }
        // skip modifiers and negative volumes, ...
        if (!v->is_model_part()) {
            continue;
        }
        const Slic3r::TriangleMesh& tm = v->mesh();
        if (tm.empty() || tm.its.empty()) {
            continue;
        }
        result.push_back({v->get_mesh_shared_ptr(), v->get_matrix()});
    }
    return result;
}

// create_projection_for_cut() of EmbossJob.cpp
Slic3r::Emboss::OrthoProject create_projection_for_cut(Transform3d tr, double shape_scale, const std::pair<float, float>& z_range)
{
    const double min_z = z_range.first - SAFE_EXTENSION;
    const double max_z = z_range.second + SAFE_EXTENSION;
    // range between min and max value
    const double projection_size = max_z - min_z;
    const Slic3r::Matrix3d transformation_for_vector = tr.linear();
    // Projection must be negative value.
    // System of text coordinate
    // X .. from left to right
    // Y .. from bottom to top
    // Z .. from text to eye
    const Vec3d untransformed_direction(0., 0., projection_size);
    const Vec3d project_direction = transformation_for_vector * untransformed_direction;

    // Projection is in direction from far plane
    tr.translate(Vec3d(0., 0., min_z));
    tr.scale(shape_scale);
    return Slic3r::Emboss::OrthoProject(tr, project_direction);
}

// create_emboss_projection() of EmbossJob.cpp
Slic3r::Emboss::OrthoProject3d create_emboss_projection(bool is_outside, float emboss, Transform3d tr, Slic3r::SurfaceCut& cut)
{
    const float front_move = is_outside ? emboss : SAFE_SURFACE_OFFSET;
    const float back_move = -(is_outside ? SAFE_SURFACE_OFFSET : emboss);
    its_transform(cut, tr.pretranslate(Vec3d(0., 0., front_move)));
    const Vec3d from_front_to_back(0., 0., back_move - front_move);
    return Slic3r::Emboss::OrthoProject3d(from_front_to_back);
}

// cut_surface_to_its() of EmbossJob.cpp
indexed_triangle_set cut_surface_to_its(const Slic3r::ExPolygons& shapes, const Transform3d& tr, const ModelSources& sources, EmbossInput& input)
{
    using namespace Slic3r;
    BoundingBox bb = get_extents(shapes);
    const double shape_scale = input.shape.scale;

    const ModelSource* biggest = &sources.front();

    std::size_t biggest_count = 0;
    // convert index from (s)ources to (i)ndexed (t)riangle (s)ets
    std::vector<std::size_t> s_to_itss(sources.size(), std::numeric_limits<std::size_t>::max());
    std::vector<indexed_triangle_set> itss;
    itss.reserve(sources.size());
    for (const ModelSource& s : sources) {
        Transform3d mesh_tr_inv = s.tr.inverse();
        Transform3d cut_projection_tr = mesh_tr_inv * tr;
        std::pair<float, float> z_range{0., 1.};
        Emboss::OrthoProject cut_projection = create_projection_for_cut(cut_projection_tr, shape_scale, z_range);
        // copy only part of source model
        indexed_triangle_set its = its_cut_AoI(s.mesh->its, bb, cut_projection);
        if (its.indices.empty()) {
            continue;
        }
        if (biggest_count < its.vertices.size()) {
            biggest_count = its.vertices.size();
            biggest = &s;
        }
        const std::size_t source_index = &s - &sources.front();
        const std::size_t its_index = itss.size();
        s_to_itss[source_index] = its_index;
        itss.emplace_back(std::move(its));
    }
    if (itss.empty()) {
        return {};
    }

    Transform3d tr_inv = biggest->tr.inverse();
    Transform3d cut_projection_tr = tr_inv * tr;

    std::size_t itss_index = s_to_itss[biggest - &sources.front()];
    BoundingBoxf3 mesh_bb = bounding_box(itss[itss_index]);
    for (const ModelSource& s : sources) {
        itss_index = s_to_itss[&s - &sources.front()];
        if (itss_index == std::numeric_limits<std::size_t>::max()) {
            continue;
        }
        if (&s == biggest) {
            continue;
        }
        Transform3d its_tr = s.tr * tr_inv;
        const bool fix_reflected = true;
        indexed_triangle_set& its = itss[itss_index];
        its_transform(its, its_tr, fix_reflected);
        BoundingBoxf3 its_bb = bounding_box(its);
        mesh_bb.merge(its_bb);
    }

    // tr_inv = transformation of mesh inverted
    Transform3d emboss_tr = cut_projection_tr.inverse();
    BoundingBoxf3 mesh_bb_tr = mesh_bb.transformed(emboss_tr);
    std::pair<float, float> z_range{mesh_bb_tr.min.z(), mesh_bb_tr.max.z()};
    Emboss::OrthoProject cut_projection = create_projection_for_cut(cut_projection_tr, shape_scale, z_range);
    const float projection_ratio = (-z_range.first + SAFE_EXTENSION) / (z_range.second - z_range.first + 2 * SAFE_EXTENSION);

    ExPolygons shapes_data; // is used only when text is reflected to reverse polygon points order
    const ExPolygons* shapes_ptr = &shapes;
    const bool is_text_reflected = has_reflection(tr);
    if (is_text_reflected) {
        // revert order of points in expolygons
        // CW --> CCW
        shapes_data = shapes; // copy
        for (ExPolygon& shape : shapes_data) {
            shape.contour.reverse();
            for (Slic3r::Polygon& hole : shape.holes) {
                hole.reverse();
            }
        }
        shapes_ptr = &shapes_data;
    }

    // Use CGAL to cut surface from triangle mesh
    SurfaceCut cut = cut_surface(*shapes_ptr, itss, cut_projection, projection_ratio);

    if (is_text_reflected) {
        for (SurfaceCut::Contour& c : cut.contours) {
            std::reverse(c.begin(), c.end());
        }
        for (Vec3i32& t : cut.indices) {
            std::swap(t[0], t[1]);
        }
    }

    if (cut.empty()) {
        return {}; // There is no valid surface for text projection.
    }

    // !! Projection needs to transform cut
    Emboss::OrthoProject3d projection = create_emboss_projection(input.is_outside, float(input.shape.projection.depth), emboss_tr, cut);
    return cut2model(cut, projection);
}

// cut_per_glyph_surface() of EmbossJob.cpp
Slic3r::TriangleMesh cut_per_glyph_surface(EmbossInput& input, const Transform3d& transform, const ModelSources& sources)
{
    using namespace Slic3r;
    using namespace Slic3r::Emboss;
    // Precalculate bounding boxes of glyphs
    // Separate lines of text to vector of Bounds
    const EmbossShape& es = input.create_shape();
    if (es.shapes_with_ids.empty()) {
        throw std::runtime_error("Font doesn't have any shape for given text.");
    }
    const std::size_t count_lines = input.text_lines.size();
    std::vector<BoundingBoxes> bbs = create_line_bounds(es.shapes_with_ids, count_lines);

    // half of font em size for direction of letter emboss
    const double em_2_mm = 5.;
    const int32_t em_2_polygon = static_cast<int32_t>(std::round(scale_(em_2_mm)));

    std::size_t s_i_offset = 0; // shape index offset(for next lines)
    indexed_triangle_set result;
    for (std::size_t text_line_index = 0; text_line_index < input.text_lines.size(); ++text_line_index) {
        const BoundingBoxes& line_bbs = bbs[text_line_index];
        const TextLine& line = input.text_lines[text_line_index];
        PolygonPoints samples = sample_slice(line, line_bbs, es.scale);
        std::vector<double> angles = calculate_angles(em_2_polygon, samples, line.polygon);

        for (std::size_t i = 0; i < line_bbs.size(); ++i) {
            const BoundingBox& glyph_bb = line_bbs[i];
            if (!glyph_bb.defined) {
                continue;
            }
            const double& angle = angles[i];
            auto rotate = Eigen::AngleAxisd(angle + M_PI_2, Vec3d::UnitY());

            const PolygonPoint& sample = samples[i];
            Vec2d offset_vec = unscale(sample.point); // [in mm]
            auto offset_tr = Eigen::Translation<double, 3>(offset_vec.x(), 0., -offset_vec.y());

            ExPolygons glyph_shape = es.shapes_with_ids[s_i_offset + i].expoly;
            Point offset(-glyph_bb.center().x(), 0);
            for (ExPolygon& s : glyph_shape) {
                s.translate(offset);
            }

            Transform3d modify = offset_tr * rotate;
            Transform3d tr = transform * modify;
            indexed_triangle_set glyph_its = cut_surface_to_its(glyph_shape, tr, sources, input);
            // move letter in volume on the right position
            its_transform(glyph_its, modify);

            // Improve: union instead of merge
            its_merge(result, std::move(glyph_its));
        }
        s_i_offset += line_bbs.size();
    }

    if (result.empty()) {
        throw std::runtime_error("There is no valid surface for text projection.");
    }
    return TriangleMesh(std::move(result));
}

// cut_surface() of EmbossJob.cpp
Slic3r::TriangleMesh cut_surface(EmbossInput& input, const Transform3d& transform, const ModelSources& sources)
{
    if (!input.text_lines.empty()) {
        return cut_per_glyph_surface(input, transform, sources);
    }
    Slic3r::ExPolygons shapes = create_shape(input);
    if (shapes.empty()) {
        throw std::runtime_error("Font doesn't have any shape for given text.");
    }
    indexed_triangle_set its = cut_surface_to_its(shapes, transform, sources, input);
    if (its.empty()) {
        throw std::runtime_error("There is no valid surface for text projection.");
    }
    return Slic3r::TriangleMesh(std::move(its));
}

// ------------------------------------------------------ the text lines --

// select_closest_contour() of TextLines.cpp: the contour of each line
// nearest to the text's origin.
Slic3r::Emboss::TextLines select_closest_contour(const std::vector<Slic3r::Polygons>& line_contours)
{
    using namespace Slic3r;
    Emboss::TextLines result;
    result.reserve(line_contours.size());
    Vec2d zero(0., 0.);
    for (const Polygons& polygons : line_contours) {
        if (polygons.empty()) {
            result.emplace_back();
            continue;
        }
        ExPolygons expolygons = union_ex(polygons);
        std::vector<Linef> linesf = to_linesf(expolygons);
        AABBTreeIndirect::Tree2d tree = AABBTreeLines::build_aabb_tree_over_indexed_lines(linesf);

        std::size_t line_idx = 0;
        Vec2d hit_point;
        AABBTreeLines::squared_distance_to_indexed_lines(linesf, tree, zero, line_idx, hit_point);

        // conversion between index of point and expolygon
        ExPolygonsIndices cvt(expolygons);
        ExPolygonsIndex index = cvt.cvt(static_cast<uint32_t>(line_idx));

        const Slic3r::Polygon& polygon = index.is_contour() ? expolygons[index.expolygons_index].contour :
                                                              expolygons[index.expolygons_index].holes[index.hole_index()];

        Point hit_point_int = hit_point.cast<Point::coord_type>();
        Emboss::TextLine tl{polygon, PolygonPoint{index.point_index, hit_point_int}};
        result.emplace_back(tl);
    }
    return result;
}

// TextLinesModel::init() of TextLines.cpp: the object's model parts sliced
// through the middle of each line of the text standing at text_tr.
Slic3r::Emboss::TextLines create_text_lines(
    const Transform3d& text_tr,
    const Slic3r::ModelVolumePtrs& volumes_to_slice,
    const Slic3r::Emboss::FontFile& ff,
    const Slic3r::FontProp& fp,
    unsigned count_lines
)
{
    using namespace Slic3r;
    const FontProp::VerticalAlign align = fp.align.second;

    // TextLinesModel::calc_line_height_in_mm()
    const double line_height_mm = Emboss::get_line_height(ff, fp) * Emboss::get_text_shape_scale(fp, ff);
    if (line_height_mm <= 0) {
        return {};
    }

    // size_in_mm .. contain volume scale and should be ascent value in mm
    const double ascent_ratio_offset = 1 / 3.;
    const double line_offset = fp.size_in_mm * ascent_ratio_offset;
    const double first_line_center = line_offset + Emboss::get_align_y_offset_in_mm(align, count_lines, ff, fp);
    std::vector<float> line_centers(count_lines);
    for (std::size_t i = 0; i < count_lines; ++i) {
        line_centers[i] = static_cast<float>(first_line_center - i * line_height_mm);
    }

    // contour transformation
    const auto rotation = Eigen::AngleAxis(-M_PI_2, Vec3d::UnitX());
    Transform3d c_trafo = text_tr * rotation;
    Transform3d c_trafo_inv = c_trafo.inverse();

    std::vector<Polygons> line_contours(count_lines);
    for (const ModelVolume* volume : volumes_to_slice) {
        MeshSlicingParams slicing_params;
        slicing_params.trafo = c_trafo_inv * volume->get_matrix();
        for (std::size_t i = 0; i < count_lines; ++i) {
            const Polygons polys = Slic3r::slice_mesh(volume->mesh().its, line_centers[i], slicing_params);
            if (polys.empty()) {
                continue;
            }
            Polygons& contours = line_contours[i];
            contours.insert(contours.end(), polys.begin(), polys.end());
        }
    }

    // fix for text line out of object
    // When move text close to edge - line center could be out of object
    for (Polygons& contours : line_contours) {
        if (!contours.empty()) {
            continue;
        }
        // use line center at zero, there should be some contour.
        const float line_center = 0.f;
        for (const ModelVolume* volume : volumes_to_slice) {
            MeshSlicingParams slicing_params;
            slicing_params.trafo = c_trafo_inv * volume->get_matrix();
            const Polygons polys = Slic3r::slice_mesh(volume->mesh().its, line_center, slicing_params);
            if (polys.empty()) {
                continue;
            }
            contours.insert(contours.end(), polys.begin(), polys.end());
        }
    }

    Emboss::TextLines lines = select_closest_contour(line_contours);
    for (std::size_t i = 0; i < count_lines && i < lines.size(); ++i) {
        lines[i].y = line_centers[i];
    }
    return lines;
}

// prepare_volumes_to_slice() of GLGizmoEmboss.cpp: the model parts of the
// object the text is embossed onto, but the text.
Slic3r::ModelVolumePtrs prepare_volumes_to_slice(const Slic3r::ModelVolume& mv)
{
    const Slic3r::ModelVolumePtrs& volumes = mv.get_object()->volumes;
    Slic3r::ModelVolumePtrs result;
    result.reserve(volumes.size());
    for (Slic3r::ModelVolume* volume : volumes) {
        // only part could be surface for volumes
        if (!volume->is_model_part()) {
            continue;
        }
        // is selected volume
        if (mv.id() == volume->id()) {
            continue;
        }
        result.push_back(volume);
    }
    return result;
}

// --------------------------------------------------- creating volumes --

// create_emboss_data_base() of GLGizmoEmboss.cpp: what text in style makes,
// for a volume of type; the text lines are given when the text stands per
// glyph on an object.
std::optional<EmbossInput> text_input(const std::string& text, const TextStyle& style, Slic3r::ModelVolumeType type, std::string& message)
{
    EmbossInput input;
    input.font = font_file(style.font_path);
    if (!input.font.has_value()) {
        message = "The font " + style.font_path + " can't be read";
        return std::nullopt;
    }
    // create volume_name
    input.volume_name = text;
    // change enters to space
    std::replace(input.volume_name.begin(), input.volume_name.end(), '\n', ' ');
    input.is_outside = type == Slic3r::ModelVolumeType::MODEL_PART;
    input.from_surface = optional_float(style.distance);
    Slic3r::TextConfiguration configuration{emboss_style_of(style), text};
    input.shape.projection = projection_of(style);
    input.shape.scale = Slic3r::Emboss::get_text_shape_scale(configuration.style.prop, *input.font.font_file);
    input.text = std::move(configuration);
    return input;
}

// get_tesselation_tolerance() of GLGizmoSVG.cpp
double tesselation_tolerance(double scale)
{
    const double tesselation_tolerance_in_mm = .1;
    const double tesselation_tolerance_scaled = (tesselation_tolerance_in_mm * tesselation_tolerance_in_mm) / SCALING_FACTOR / SCALING_FACTOR;
    return tesselation_tolerance_scaled / scale / scale;
}

// get_file_name() of GLGizmoSVG.cpp: the name without directory and extension.
std::string file_name_of(const std::string& file_path)
{
    if (file_path.empty()) {
        return file_path;
    }
    std::size_t pos_last_delimiter = file_path.find_last_of("/\\");
    if (pos_last_delimiter == std::string::npos) {
        pos_last_delimiter = 0;
    }
    std::size_t pos_point = file_path.find_last_of('.');
    if (pos_point == std::string::npos || pos_point < pos_last_delimiter) {
        pos_point = file_path.size();
    }
    const std::size_t offset = pos_last_delimiter + 1;
    const std::size_t count = pos_point - pos_last_delimiter - 1;
    return file_path.substr(offset, count);
}

// select_shape() of GLGizmoSVG.cpp for the file at path: its paths as shapes,
// 10 mm deep; none for a file nanosvg can't read or without a path.
std::optional<Slic3r::EmbossShape> select_shape(const std::string& path, double tolerance, std::string& message)
{
    Slic3r::EmbossShape shape;
    shape.projection.depth = 10.;
    shape.projection.use_surface = false;
    Slic3r::EmbossShape::SvgFile svg;
    svg.path = path;
    if (!boost::filesystem::exists(boost::filesystem::path(svg.path))) {
        message = "File does NOT exist (" + svg.path + ").";
        return std::nullopt;
    }
    if (!boost::algorithm::iends_with(svg.path, ".svg")) {
        message = "Filename has to end with \".svg\" but you selected " + svg.path;
        return std::nullopt;
    }
    if (Slic3r::init_image(svg) == nullptr) {
        message = "Nano SVG parser can't load from file (" + svg.path + ").";
        return std::nullopt;
    }
    // Set default and unchanging scale
    Slic3r::NSVGLineParams params{tolerance};
    shape.shapes_with_ids = Slic3r::create_shape_with_ids(*svg.image, params);
    // Must contain some shapes !!!
    if (shape.shapes_with_ids.empty()) {
        message = "SVG file does NOT contain a single path to be embossed (" + svg.path + ").";
        return std::nullopt;
    }
    shape.svg_file = std::move(svg);
    return shape;
}

// volume_name() of GLGizmoSVG.cpp
std::string svg_volume_name(const Slic3r::EmbossShape& shape)
{
    std::string file_name = file_name_of(shape.svg_file->path);
    if (!file_name.empty()) {
        return file_name;
    }
    return "SVG shape";
}

Slic3r::ModelVolumeType volume_type_of(VolumeType type)
{
    switch (type) {
    case VolumeType::negative: return Slic3r::ModelVolumeType::NEGATIVE_VOLUME;
    case VolumeType::modifier: return Slic3r::ModelVolumeType::PARAMETER_MODIFIER;
    case VolumeType::support_blocker: return Slic3r::ModelVolumeType::SUPPORT_BLOCKER;
    case VolumeType::support_enforcer: return Slic3r::ModelVolumeType::SUPPORT_ENFORCER;
    case VolumeType::part:
    default: return Slic3r::ModelVolumeType::MODEL_PART;
    }
}

VolumeType volume_type_from(Slic3r::ModelVolumeType type)
{
    switch (type) {
    case Slic3r::ModelVolumeType::NEGATIVE_VOLUME: return VolumeType::negative;
    case Slic3r::ModelVolumeType::PARAMETER_MODIFIER: return VolumeType::modifier;
    case Slic3r::ModelVolumeType::SUPPORT_BLOCKER: return VolumeType::support_blocker;
    case Slic3r::ModelVolumeType::SUPPORT_ENFORCER: return VolumeType::support_enforcer;
    default: return VolumeType::part;
    }
}

// create_volume() of EmbossJob.cpp: the volume of type with mesh joins
// object, placed by trmat, or beside the object's first copy without it.
Slic3r::ModelVolume* add_volume(
    Slic3r::ModelObject& object,
    Slic3r::TriangleMesh&& mesh,
    Slic3r::ModelVolumeType type,
    const std::optional<Transform3d>& trmat,
    const EmbossInput& input
)
{
    using namespace Slic3r;
    BoundingBoxf3 instance_bb;
    if (!trmat.has_value()) {
        // used for align to instance
        const std::size_t instance_index = 0; // must exist
        instance_bb = object.instance_bounding_box(instance_index);
    }

    // NOTE: be carefull add volume also center mesh !!!
    // So first add simple shape(convex hull is also calculated)
    ModelVolume* volume = object.add_volume(make_cube(1., 1., 1.), type);

    // Revert mesh centering by set mesh after add cube
    volume->set_mesh(std::move(mesh));
    volume->calculate_convex_hull();

    // set a default extruder value, since user can't add it manually
    volume->config.set_key_value("extruder", new ConfigOptionInt(0));

    // do not allow model reload from disk
    volume->source.is_from_builtin_objects = true;

    volume->name = input.volume_name; // copy

    if (trmat.has_value()) {
        volume->set_transformation(*trmat);
    } else {
        // Create transformation for volume near from object(defined by glVolume)
        // Transformation is inspired add generic volumes in ObjectList::load_generic_subobject
        Vec3d volume_size = volume->mesh().bounding_box().size();
        // Translate the new modifier to be pickable: move to the left front corner of the instance's bounding box, lift to print bed.
        Vec3d offset_tr(0, // center of instance - Can't suggest width of text before it will be created
                        -instance_bb.size().y() / 2 - volume_size.y() / 2, // under
                        volume_size.z() / 2 - instance_bb.size().z() / 2); // lay on bed
        // use same instance as for calculation of instance_bounding_box
        Transform3d tr = object.instances.front()->get_transformation().get_matrix_no_offset().inverse();
        Transform3d volume_trmat = tr * Eigen::Translation3d(offset_tr);
        volume->set_transformation(volume_trmat);
    }

    input.write(*volume);

    // update printable state on canvas
    if (type == ModelVolumeType::MODEL_PART) {
        volume->get_object()->ensure_on_bed();
    }
    return volume;
}

// The engine's state and the plate, read for a request; false with result's
// status and message when the presets or the plate can't be.
bool prepare(
    const std::vector<PlateObject>& plate,
    const ProfileSelection& profiles,
    Slic3r::DynamicPrintConfig& config,
    Slic3r::Model& model,
    ImportedModels& result
)
{
    if (detail::engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return false;
    }
    if (detail::select_profiles(*detail::engine().bundle, profiles, config, result.message) != SliceStatus::success) {
        result.status = SceneStatus::profile_not_found;
        return false;
    }
    return detail::load_plate(plate, config, model, result.message);
}

// start_create_volume() and the jobs it starts (EmbossJob.cpp): the shape of
// input as a volume of type at placement, or as an object of its own.
void create_emboss(
    Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    const EmbossPlacement& placement,
    Slic3r::ModelVolumeType type,
    EmbossInput& input,
    const std::optional<float>& angle,
    const std::optional<float>& distance,
    const std::string& output_prefix,
    ImportedModels& result
)
{
    using namespace Slic3r;
    if (placement.object_index < 0) {
        // CreateObjectJob: can't create new object with using surface
        if (input.shape.projection.use_surface) {
            input.shape.projection.use_surface = false;
        }
        TriangleMesh mesh = create_mesh(input);

        // calculate X,Y offset position for lay on platter in place of mouse click
        const BuildVolume build_volume = detail::build_volume_of(config);
        Points bed_shape;
        for (const Vec2d& p : build_volume.printable_area()) {
            bed_shape.emplace_back(p.cast<coord_t>());
        }
        // The current plate's printable area, where it stands among the plates.
        Slic3r::Polygon bed(bed_shape);
        Vec2d bed_coor = placement.bed_point.size() == 2 ? Vec2d(placement.bed_point[0], placement.bed_point[1]) : bed.centroid().cast<double>();
        // check point is on build plate:
        if (!bed.contains(bed_coor.cast<coord_t>())) {
            // mouse pose is out of build plate so create object in center of plate
            bed_coor = bed.centroid().cast<double>();
        }

        const double z = input.shape.projection.depth / 2;
        Vec3d offset(bed_coor.x(), bed_coor.y(), z);
        offset -= mesh.center();
        Transform3d::TranslationType tt(offset.x(), offset.y(), offset.z());
        Transform3d transformation = Transform3d(tt);

        // rotate around Z by style settings
        if (angle.has_value()) {
            std::optional<float> no_distance; // new object ignore surface distance from style settings
            Emboss::apply_transformation(angle, no_distance, transformation);
        }

        // CreateObjectJob::finalize(): inspiration for create object is from ObjectList::load_mesh_object()
        ModelObject* new_object = model.add_object();
        new_object->name = input.volume_name;
        new_object->add_instance(); // each object should have at list one instance
        new_object->config.set_key_value("extruder", new ConfigOptionInt(1));
        ModelVolume* new_volume = new_object->add_volume(std::move(mesh));
        // set a default extruder value, since user can't add it manually
        new_volume->config.set_key_value("extruder", new ConfigOptionInt(1));
        // write emboss data into volume
        input.write(*new_volume);

        // set transformation
        Slic3r::Geometry::Transformation tr(transformation);
        new_object->instances.front()->set_transformation(tr);
        new_object->ensure_on_bed();

        model.update_print_volume_state(build_volume);
        result.appended = true;
        result.selected_volume = 0;
        if (!detail::write_objects({new_object}, output_prefix, result)) {
            return;
        }
        result.status = SceneStatus::success;
        return;
    }

    if (std::size_t(placement.object_index) >= model.objects.size()) {
        result.message = "The object is not on the plate";
        return;
    }
    ModelObject& object = *model.objects[std::size_t(placement.object_index)];
    if (placement.instance_index < 0 || std::size_t(placement.instance_index) >= object.instances.size()) {
        result.message = "The copy is not on the plate";
        return;
    }
    const ModelInstance& instance = *object.instances[std::size_t(placement.instance_index)];

    // start_create_volume_on_surface_job(): the volume on the surface where
    // the ray hit it; without a hit, beside the object without the surface.
    std::optional<Transform3d> transform;
    if (placement.position.size() == 3 && placement.normal.size() == 3) {
        const Vec3d position(placement.position[0], placement.position[1], placement.position[2]);
        const Vec3d normal(placement.normal[0], placement.normal[1], placement.normal[2]);
        Transform3d surface_trmat = Emboss::create_transformation_onto_surface(position, normal.normalized(), UP_LIMIT);
        Emboss::apply_transformation(angle, distance, surface_trmat);
        transform = instance.get_matrix().inverse() * surface_trmat;
    } else if (input.shape.projection.use_surface) {
        // there is no point on surface so no use of surface will be applied
        input.shape.projection.use_surface = false;
    }

    // start_create_volume_job(): the surface is cut from the object's model parts.
    TriangleMesh mesh;
    if (input.shape.projection.use_surface) {
        ModelSources sources = create_sources(object.volumes);
        if (sources.empty() || !transform.has_value()) {
            input.shape.projection.use_surface = false;
        } else {
            mesh = cut_surface(input, *transform, sources);
        }
    }
    if (!input.shape.projection.use_surface) {
        mesh = create_mesh(input);
    }
    if (mesh.its.empty()) {
        result.message = "Can't create empty volume.";
        return;
    }

    ModelVolume* volume = add_volume(object, std::move(mesh), type, transform, input);
    // reorder_volumes_and_get_selection(): the object list sorts the volumes by type.
    object.sort_volumes(true);
    object.invalidate_bounding_box();
    const auto found = std::find(object.volumes.begin(), object.volumes.end(), volume);
    result.selected_volume = found == object.volumes.end() ? -1 : int(found - object.volumes.begin());

    model.update_print_volume_state(detail::build_volume_of(config));
    if (!detail::write_objects({&object}, output_prefix, result)) {
        return;
    }
    result.status = SceneStatus::success;
}

// start_update_volume() with UpdateJob and UpdateSurfaceVolumeJob: the volume
// embossed anew from input, the surface cut from the object when it uses the
// surface, with matrix placing it first when given.
void update_emboss(
    Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    Slic3r::ModelVolume& volume,
    EmbossInput& input,
    const std::vector<double>& matrix,
    const std::string& output_prefix,
    ImportedModels& result
)
{
    using namespace Slic3r;
    std::optional<Transform3d> given;
    if (matrix.size() == 16) {
        Transform3d placed = Transform3d::Identity();
        std::copy(matrix.begin(), matrix.end(), placed.data());
        given = placed;
    }

    // check cutting from source mesh
    bool& use_surface = input.shape.projection.use_surface;
    if (use_surface && volume.is_the_only_one_part()) {
        use_surface = false;
    }

    TriangleMesh mesh;
    std::optional<Transform3d> transform = given;
    if (use_surface) {
        // Model to cut surface from.
        ModelSources sources = create_sources(volume.get_object()->volumes, volume.id().id);
        if (sources.empty()) {
            result.message = "There is no surface to cut the volume from";
            return;
        }
        Transform3d volume_tr = given.value_or(volume.get_matrix());
        const std::optional<Transform3d>& fix_3mf = volume.emboss_shape->fix_3mf_tr;
        if (!given.has_value() && fix_3mf.has_value()) {
            volume_tr = volume_tr * fix_3mf->inverse();
        }
        // when it is new applying of use surface than move origin onto surfaca
        if (!volume.emboss_shape->projection.use_surface) {
            // calc_surface_offset(): along the volume's projection, or to the closest point without a hit.
            const ModelObject& object = *volume.get_object();
            const ModelInstance& instance = *object.instances.front();
            const Transform3d to_world = instance.get_matrix() * volume_tr;
            const Vec3d point = to_world.translation();
            const Vec3d dir = -to_world.linear().col(2).normalized();
            std::optional<Vec3d> hit_world;
            double closest = std::numeric_limits<double>::max();
            for (const ModelVolume* source : object.volumes) {
                if (source->id() == volume.id() || source->mesh().empty()) {
                    continue;
                }
                const Transform3d tr = instance.get_matrix() * source->get_matrix();
                const Transform3d tr_inv = tr.inverse();
                const Vec3d mesh_point = tr_inv * point;
                const Vec3d mesh_direction = tr_inv.linear() * dir;
                const AABBMesh aabb(source->mesh());
                std::vector<AABBMesh::hit_result> hits = aabb.query_ray_hits(mesh_point - mesh_direction, mesh_direction);
                std::vector<AABBMesh::hit_result> hits_neg = aabb.query_ray_hits(mesh_point + mesh_direction, -mesh_direction);
                hits.insert(hits.end(), hits_neg.begin(), hits_neg.end());
                for (const AABBMesh::hit_result& hit : hits) {
                    if (!hit.is_hit()) {
                        continue;
                    }
                    const double squared_distance = (mesh_point - hit.position()).squaredNorm();
                    if (squared_distance < closest) {
                        closest = squared_distance;
                        hit_world = tr * hit.position();
                    }
                }
            }
            if (hit_world.has_value() && closest >= EPSILON) {
                const Vec3d offset_world = *hit_world - point;
                const Vec3d offset_volume = to_world.inverse().linear() * offset_world;
                volume_tr *= Eigen::Translation<double, 3>(offset_volume);
            }
        }
        mesh = cut_surface(input, volume_tr, sources);
        transform = volume_tr;
    } else {
        mesh = try_create_mesh(input);
        if (mesh.its.empty()) {
            result.message = "Created text volume is empty. Change text or font.";
            return;
        }
    }
    if (mesh.its.empty()) {
        result.message = "Empty mesh can't be created.";
        return;
    }

    // update_volume() of EmbossJob.cpp
    if (transform.has_value()) {
        volume.set_transformation(*transform);
    } else {
        // apply fix matrix made by store to .3mf
        const std::optional<EmbossShape>& emboss_shape = volume.emboss_shape;
        if (emboss_shape.has_value() && emboss_shape->fix_3mf_tr.has_value()) {
            volume.set_transformation(volume.get_matrix() * emboss_shape->fix_3mf_tr->inverse());
        }
    }
    // UpdateJob::update_volume()
    volume.set_mesh(std::move(mesh));
    volume.set_new_unique_id();
    volume.calculate_convex_hull();
    input.write(volume);

    // Plater::changed_object(): the object rests on the plate, as it may sink.
    ModelObject& object = *volume.get_object();
    object.invalidate_bounding_box();
    object.ensure_on_bed(true);

    model.update_print_volume_state(detail::build_volume_of(config));
    if (!detail::write_objects({&object}, output_prefix, result)) {
        return;
    }
    result.status = SceneStatus::success;
}

// calc_distance() of SurfaceDrag.cpp: how far the volume's origin stands from
// the object's other volumes along its projection; none when it touches them,
// is too far, or the object has no other part.
std::optional<double> surface_distance(const Slic3r::ModelVolume& volume, const Slic3r::ModelInstance& instance)
{
    using namespace Slic3r;
    if (volume.is_the_only_one_part() || !volume.emboss_shape.has_value()) {
        return std::nullopt;
    }
    Transform3d w = instance.get_matrix() * volume.get_matrix();
    if (volume.emboss_shape->fix_3mf_tr.has_value()) {
        w = w * volume.emboss_shape->fix_3mf_tr->inverse();
    }
    const Vec3d p = w.translation();
    const Vec3d dir = -w.linear().col(2).normalized();

    // RaycastManager::closest_hit() over the object's volumes but this one.
    std::optional<Vec3d> hit_world;
    double closest = std::numeric_limits<double>::max();
    for (const ModelVolume* source : volume.get_object()->volumes) {
        if (source->id() == volume.id() || source->mesh().empty()) {
            continue;
        }
        const Transform3d tr = instance.get_matrix() * source->get_matrix();
        const Transform3d tr_inv = tr.inverse();
        const Vec3d mesh_point = tr_inv * p;
        const Vec3d mesh_direction = tr_inv.linear() * dir;
        const AABBMesh aabb(source->mesh());
        std::vector<AABBMesh::hit_result> hits = aabb.query_ray_hits(mesh_point - mesh_direction, mesh_direction);
        std::vector<AABBMesh::hit_result> hits_neg = aabb.query_ray_hits(mesh_point + mesh_direction, -mesh_direction);
        hits.insert(hits.end(), hits_neg.begin(), hits_neg.end());
        for (const AABBMesh::hit_result& hit : hits) {
            if (!hit.is_hit()) {
                continue;
            }
            const double squared_distance = (mesh_point - hit.position()).squaredNorm();
            if (squared_distance < closest) {
                closest = squared_distance;
                hit_world = tr * hit.position();
            }
        }
    }
    if (!hit_world.has_value()) {
        return std::nullopt;
    }
    const Vec3d p_to_hit = *hit_world - p;
    const double distance_sq = p_to_hit.squaredNorm();
    // too small distance is calculated as zero distance
    if (distance_sq < SURFACE_DISTANCE_SQ_MIN) {
        return std::nullopt;
    }
    // check maximal distance
    const BoundingBoxf3 bb = volume.mesh().bounding_box().transformed(w);
    const double max_squared_distance = std::max(std::pow(2 * bb.size().z(), 2), SURFACE_DISTANCE_SQ_MAX);
    if (distance_sq > max_squared_distance) {
        return std::nullopt;
    }
    // calculate sign
    const double sign = (p_to_hit.dot(dir) > 0) ? 1. : -1.;
    return sign * std::sqrt(distance_sq);
}

// The volume at volume_index of the object at object_index, or null with result's message.
Slic3r::ModelVolume* volume_at(Slic3r::Model& model, std::size_t object_index, std::size_t volume_index, std::string& message)
{
    if (object_index >= model.objects.size()) {
        message = "The object is not on the plate";
        return nullptr;
    }
    Slic3r::ModelObject& object = *model.objects[object_index];
    if (volume_index >= object.volumes.size()) {
        message = "The object has no such volume";
        return nullptr;
    }
    return object.volumes[volume_index];
}

} // namespace

namespace detail {

std::string write_emboss(const Slic3r::ModelVolume& volume, const std::string& path)
{
    if (!volume.text_configuration.has_value() && !volume.emboss_shape.has_value()) {
        return {};
    }
    std::ofstream file(path, std::ios::binary);
    if (!file) {
        return {};
    }
    {
        cereal::BinaryOutputArchive archive(file);
        StoredEmboss stored{volume.text_configuration, volume.emboss_shape};
        archive(stored);
    }
    return file ? path : std::string();
}

bool read_emboss(const std::string& path, Slic3r::ModelVolume& volume)
{
    if (path.empty()) {
        return true;
    }
    std::ifstream file(path, std::ios::binary);
    if (!file) {
        return false;
    }
    try {
        StoredEmboss stored;
        cereal::BinaryInputArchive archive(file);
        archive(stored);
        volume.text_configuration = std::move(stored.text);
        volume.emboss_shape = std::move(stored.shape);
        return true;
    } catch (const std::exception&) {
        return false;
    }
}

EmbossKind emboss_kind_of(const Slic3r::ModelVolume& volume)
{
    if (volume.is_text()) {
        return EmbossKind::text;
    }
    if (volume.is_svg()) {
        return EmbossKind::svg;
    }
    return EmbossKind::none;
}

} // namespace detail

std::vector<FontFace> describe_fonts(const std::vector<std::string>& paths)
{
    std::vector<FontFace> result;
    for (const std::string& path : paths) {
        std::ifstream file(path, std::ios::binary);
        if (!file) {
            continue;
        }
        const std::vector<unsigned char> data((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());
        if (data.empty()) {
            continue;
        }
        const int count = std::max(0, stbtt_GetNumberOfFonts(data.data()));
        for (int index = 0; index < count; ++index) {
            const int fontstart = stbtt_GetFontOffsetForIndex(data.data(), index);
            if (fontstart < 0) {
                continue;
            }
            stbtt_fontinfo info;
            if (stbtt_InitFont(&info, data.data(), fontstart) == 0) {
                continue;
            }
            FontFace face;
            face.path = path;
            face.index = index;
            int descent = 0;
            int linegap = 0;
            stbtt_GetFontVMetrics(&info, &face.ascent, &descent, &linegap);
            // The typographic family and subfamily, or the legacy ones.
            face.family = font_name(info, 16);
            if (face.family.empty()) {
                face.family = font_name(info, 1);
            }
            face.subfamily = font_name(info, 17);
            if (face.subfamily.empty()) {
                face.subfamily = font_name(info, 2);
            }
            if (face.family.empty()) {
                continue;
            }
            // OS/2: usWeightClass, and fsSelection's italic and oblique bits.
            if (const std::uint32_t os2 = find_table(data, std::uint32_t(fontstart), "OS/2"); os2 != 0 && std::size_t(os2) + 64 <= data.size()) {
                face.weight = read_u16(data.data() + os2 + 4);
                const std::uint16_t selection = read_u16(data.data() + os2 + 62);
                face.italic = (selection & 0x0001) != 0 || (selection & 0x0200) != 0;
            }
            result.push_back(std::move(face));
        }
    }
    return result;
}

ImportedModels create_text(
    const std::vector<PlateObject>& plate,
    const EmbossPlacement& placement,
    VolumeType type,
    const std::string& text,
    const TextStyle& style,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    try {
        Slic3r::DynamicPrintConfig config;
        Slic3r::Model model;
        if (!prepare(plate, profiles, config, model, result)) {
            return result;
        }
        // init_create(): a part, a negative volume or a modifier; a new object is a part.
        const Slic3r::ModelVolumeType volume_type = placement.object_index < 0 ? Slic3r::ModelVolumeType::MODEL_PART : volume_type_of(type);
        if (volume_type != Slic3r::ModelVolumeType::MODEL_PART && volume_type != Slic3r::ModelVolumeType::NEGATIVE_VOLUME &&
            volume_type != Slic3r::ModelVolumeType::PARAMETER_MODIFIER) {
            result.message = "Can't create embossed volume with this type";
            return result;
        }
        std::optional<EmbossInput> input = text_input(text, style, volume_type, result.message);
        if (!input.has_value()) {
            return result;
        }
        create_emboss(model, config, placement, volume_type, *input, optional_float(style.angle), optional_float(style.distance), output_prefix, result);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

ImportedModels update_text(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    const std::string& text,
    const TextStyle& style,
    const std::vector<double>& matrix,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    try {
        Slic3r::DynamicPrintConfig config;
        Slic3r::Model model;
        if (!prepare(plate, profiles, config, model, result)) {
            return result;
        }
        Slic3r::ModelVolume* volume = volume_at(model, object_index, volume_index, result.message);
        if (volume == nullptr) {
            return result;
        }
        if (!volume->text_configuration.has_value() || !volume->emboss_shape.has_value()) {
            result.message = "The volume is no text";
            return result;
        }
        // is_text_empty() of GLGizmoEmboss.cpp: without text there is nothing to emboss.
        if (text.empty() || text.find_first_not_of(" \n\t\r") == std::string::npos) {
            result.message = "Embossed text cannot contain only white spaces.";
            return result;
        }
        std::optional<EmbossInput> input = text_input(text, style, volume->type(), result.message);
        if (!input.has_value()) {
            return result;
        }
        // init_text_lines(): the lines along the object's surface, per glyph.
        if (style.per_glyph && !volume->is_the_only_one_part()) {
            Transform3d mv_trafo = volume->get_matrix();
            if (matrix.size() == 16) {
                std::copy(matrix.begin(), matrix.end(), mv_trafo.data());
            } else if (volume->emboss_shape->fix_3mf_tr.has_value()) {
                mv_trafo = mv_trafo * volume->emboss_shape->fix_3mf_tr->inverse();
            }
            const unsigned count_lines = Slic3r::Emboss::get_count_lines(text);
            input->text_lines = create_text_lines(mv_trafo, prepare_volumes_to_slice(*volume), *input->font.font_file, input->text->style.prop, count_lines);
        }
        update_emboss(model, config, *volume, *input, matrix, output_prefix, result);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

ImportedModels create_svg(
    const std::vector<PlateObject>& plate,
    const EmbossPlacement& placement,
    VolumeType type,
    const std::string& svg_path,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    try {
        Slic3r::DynamicPrintConfig config;
        Slic3r::Model model;
        if (!prepare(plate, profiles, config, model, result)) {
            return result;
        }
        const Slic3r::ModelVolumeType volume_type = placement.object_index < 0 ? Slic3r::ModelVolumeType::MODEL_PART : volume_type_of(type);
        if (volume_type != Slic3r::ModelVolumeType::MODEL_PART && volume_type != Slic3r::ModelVolumeType::NEGATIVE_VOLUME &&
            volume_type != Slic3r::ModelVolumeType::PARAMETER_MODIFIER) {
            result.message = "Can't create embossed volume with this type";
            return result;
        }
        std::optional<Slic3r::EmbossShape> shape = select_shape(svg_path, tesselation_tolerance(1.), result.message);
        if (!shape.has_value()) {
            return result;
        }
        // create_emboss_data_base() of GLGizmoSVG.cpp
        EmbossInput input;
        input.volume_name = svg_volume_name(*shape);
        input.shape = std::move(*shape);
        input.is_outside = volume_type == Slic3r::ModelVolumeType::MODEL_PART;
        create_emboss(model, config, placement, volume_type, input, std::nullopt, std::nullopt, output_prefix, result);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

ImportedModels update_svg(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    double depth,
    bool use_surface,
    const std::string& svg_path,
    const std::vector<double>& matrix,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    try {
        Slic3r::DynamicPrintConfig config;
        Slic3r::Model model;
        if (!prepare(plate, profiles, config, model, result)) {
            return result;
        }
        Slic3r::ModelVolume* volume = volume_at(model, object_index, volume_index, result.message);
        if (volume == nullptr) {
            return result;
        }
        if (!volume->is_svg() || !volume->emboss_shape->svg_file.has_value()) {
            result.message = "The volume is no SVG";
            return result;
        }
        // GLGizmoSVG::process(): the volume's shape, from another file when given.
        EmbossInput input;
        input.shape = *volume->emboss_shape;
        if (!svg_path.empty()) {
            std::optional<Slic3r::EmbossShape> shape = select_shape(svg_path, tesselation_tolerance(1.), result.message);
            if (!shape.has_value()) {
                return result;
            }
            input.shape.svg_file = std::move(shape->svg_file);
            input.shape.shapes_with_ids = std::move(shape->shapes_with_ids);
            input.shape.final_shape = {}; // clear cache
        }
        input.shape.projection.depth = depth;
        input.shape.projection.use_surface = use_surface;
        input.volume_name = svg_volume_name(input.shape);
        input.is_outside = volume->type() == Slic3r::ModelVolumeType::MODEL_PART;
        update_emboss(model, config, *volume, input, matrix, output_prefix, result);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

EmbossVolume describe_emboss(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    const ProfileSelection& profiles
)
{
    EmbossVolume result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    try {
        Slic3r::DynamicPrintConfig config;
        Slic3r::Model model;
        ImportedModels loaded;
        if (!prepare(plate, profiles, config, model, loaded)) {
            result.status = loaded.status;
            result.message = loaded.message;
            return result;
        }
        Slic3r::ModelVolume* volume = volume_at(model, object_index, volume_index, result.message);
        if (volume == nullptr) {
            return result;
        }
        result.kind = detail::emboss_kind_of(*volume);
        if (result.kind == EmbossKind::none) {
            result.message = "The volume is neither text nor SVG";
            return result;
        }
        const Slic3r::EmbossShape& shape = *volume->emboss_shape;
        if (volume->text_configuration.has_value()) {
            result.text = volume->text_configuration->text;
            result.style = text_style_of(volume->text_configuration->style, shape.projection);
        } else {
            result.style.depth = shape.projection.depth;
            result.style.use_surface = shape.projection.use_surface;
        }
        const Slic3r::ModelInstance& instance = *volume->get_object()->instances.front();
        Transform3d world = instance.get_matrix() * volume->get_matrix();
        if (shape.fix_3mf_tr.has_value()) {
            world = world * shape.fix_3mf_tr->inverse();
        }
        // calc_angle() and calc_distance() of SurfaceDrag.cpp
        if (const std::optional<float> angle = Slic3r::Emboss::calc_up(world, UP_LIMIT); angle.has_value()) {
            result.style.angle = *angle;
        }
        result.style.distance = surface_distance(*volume, instance);
        // GLGizmoEmboss::calculate_scale()
        const auto linear = world.linear();
        const double height = (linear * Vec3d::UnitY()).norm();
        const double depth = (linear * Vec3d::UnitZ()).norm();
        result.scale_height = std::abs(height - 1.) < EPSILON ? 1. : height;
        result.scale_depth = std::abs(depth - 1.) < EPSILON ? 1. : depth;
        if (shape.svg_file.has_value()) {
            const Slic3r::EmbossShape::SvgFile& svg = *shape.svg_file;
            result.svg_name = file_name_of(!svg.path.empty() ? svg.path : svg.path_in_3mf);
            result.svg_reloadable = !svg.path.empty() && boost::filesystem::exists(boost::filesystem::path(svg.path));
            // The size of the shape: its bounds in millimetres, as the volume scales them.
            const Slic3r::BoundingBox bb = Slic3r::get_extents(shape.shapes_with_ids);
            if (bb.defined) {
                const Vec2d size = bb.size().cast<double>() * shape.scale;
                result.width = size.x() * (linear * Vec3d::UnitX()).norm();
                result.height = size.y() * height;
            }
        }
        result.type = volume_type_from(volume->type());
        result.only_part = volume->is_the_only_one_part();
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

} // namespace orcinus::orca
