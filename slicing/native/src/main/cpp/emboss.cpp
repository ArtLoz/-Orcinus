// Text and SVG embossed on a model, as OrcaSlicer's text and SVG tools make
// them (GLGizmoEmboss and GLGizmoSVG with their jobs in EmbossJob.cpp): the
// shape of the text's glyphs or of the SVG's paths, extruded as deep as the
// projection says, onto the object's surface when it uses the surface, and
// glyph by glyph along the surface when the text is placed per glyph. The
// volume keeps what it was embossed from (ModelVolume::text_configuration
// and emboss_shape), which travels between the app and the engine in a file
// of its own and goes into a project as the desktop app stores it.

#include <algorithm>
#include <functional>
#include <array>
#include <cmath>
#include <cstring>
#include <fstream>
#include <limits>
#include <list>
#include <map>
#include <mutex>
#include <optional>
#include <sstream>
#include <string>
#include <vector>

#include <boost/algorithm/string/predicate.hpp>
#include <boost/filesystem.hpp>
#include <boost/nowide/convert.hpp>
#include <cereal/archives/binary.hpp>
#include <fast_float/fast_float.h>

#include "libslic3r/AABBMesh.hpp"
#include "libslic3r/AppConfig.hpp"
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

// GLGizmoEmboss::process() of a text volume: embossed anew from text and
// style, matrix placing it first when given.
void reemboss_text(
    Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    Slic3r::ModelVolume& volume,
    const std::string& text,
    const TextStyle& style,
    const std::vector<double>& matrix,
    const std::string& output_prefix,
    ImportedModels& result
)
{
    using namespace Slic3r;
    // is_text_empty() of GLGizmoEmboss.cpp: without text there is nothing to emboss.
    if (text.empty() || text.find_first_not_of(" \n\t\r") == std::string::npos) {
        result.message = "Embossed text cannot contain only white spaces.";
        return;
    }
    std::optional<EmbossInput> input = text_input(text, style, volume.type(), result.message);
    if (!input.has_value()) {
        return;
    }
    // init_text_lines(): the lines along the object's surface, per glyph.
    if (style.per_glyph && !volume.is_the_only_one_part()) {
        Transform3d mv_trafo = volume.get_matrix();
        if (matrix.size() == 16) {
            std::copy(matrix.begin(), matrix.end(), mv_trafo.data());
        } else if (volume.emboss_shape->fix_3mf_tr.has_value()) {
            mv_trafo = mv_trafo * volume.emboss_shape->fix_3mf_tr->inverse();
        }
        const unsigned count_lines = Slic3r::Emboss::get_count_lines(text);
        input->text_lines = create_text_lines(mv_trafo, prepare_volumes_to_slice(volume), *input->font.font_file, input->text->style.prop, count_lines);
    }
    update_emboss(model, config, volume, *input, matrix, output_prefix, result);
}

// get_volume_transformation() of SurfaceDrag.cpp: the volume's transformation
// in its instance, world turned so that its Z axis looks along world_dir and
// moved to world_position, its up kept within up_limit, current_angle turning
// it about Z again.
Slic3r::Transform3d get_volume_transformation(
    Slic3r::Transform3d world, // from volume
    const Slic3r::Vec3d& world_dir, // wanted new direction
    const Slic3r::Vec3d& world_position, // wanted new position
    const std::optional<Slic3r::Transform3d>& fix, // [optional] fix matrix
    // Invers transformation of text volume instance
    // Help convert world transformation to instance space
    const Slic3r::Transform3d& instance_inv,
    // initial rotation in Z axis
    std::optional<float> current_angle,
    const std::optional<double>& up_limit)
{
    using namespace Slic3r;
    auto world_linear = world.linear().eval();
    // Calculate offset: transformation to wanted position
    {
        // Reset skew of the text Z axis:
        // Project the old Z axis into a new Z axis, which is perpendicular to the old XY plane.
        Vec3d old_z         = world_linear.col(2);
        Vec3d new_z         = world_linear.col(0).cross(world_linear.col(1));
        world_linear.col(2) = new_z * (old_z.dot(new_z) / new_z.squaredNorm());
    }

    Vec3d       text_z_world     = world_linear.col(2); // world_linear * Vec3d::UnitZ()
    auto        z_rotation       = Eigen::Quaternion<double, Eigen::DontAlign>::FromTwoVectors(text_z_world, world_dir);
    Transform3d world_new        = z_rotation * world;
    auto        world_new_linear = world_new.linear().eval();

    // Fix direction of up vector to zero initial rotation
    if(up_limit.has_value()){
        Vec3d z_world = world_new_linear.col(2);
        z_world.normalize();
        Vec3d wanted_up = Emboss::suggest_up(z_world, *up_limit);

        Vec3d y_world    = world_new_linear.col(1);
        auto  y_rotation = Eigen::Quaternion<double, Eigen::DontAlign>::FromTwoVectors(y_world, wanted_up);

        world_new        = y_rotation * world_new;
        world_new_linear = world_new.linear();
    }

    // Edit position from right
    Transform3d volume_new{Eigen::Translation<double, 3>(instance_inv * world_position)};
    volume_new.linear() = instance_inv.linear() * world_new_linear;

    // Check that transformation matrix is valid transformation
    if (volume_new.matrix()(0, 0) != volume_new.matrix()(0, 0))
        return Transform3d::Identity();

    // fix baked transformation from .3mf store process
    if (fix.has_value())
        volume_new = volume_new * (*fix);

    // apply move in Z direction and rotation by up vector
    Emboss::apply_transformation(current_angle, {}, volume_new);

    return volume_new;
}

// Selection::synchronize_unselected_instances(GENERAL) after the copy
// instance of object turned from old_instance: the other copies turn alike,
// and one that does not drop keeps its height with it.
void synchronize_instances(Slic3r::ModelObject& object, const Slic3r::ModelInstance& instance, const Slic3r::Transform3d& old_instance)
{
    using namespace Slic3r;
    const Transform3d& curr_inst_trafo_i = instance.get_matrix();
    for (ModelInstance* other : object.instances) {
        if (other == &instance) {
            continue;
        }
        Transform3d new_inst_trafo_j = other->get_matrix();
        const Transform3d old_inst_trafo_j = new_inst_trafo_j;
        new_inst_trafo_j.linear() = (old_inst_trafo_j.linear() * old_instance.linear().inverse()) * curr_inst_trafo_i.linear();
        if (!other->auto_drop) {
            new_inst_trafo_j.translation().z() = curr_inst_trafo_i.translation().z();
        }
        other->set_transformation(Geometry::Transformation(new_inst_trafo_j));
    }
}

// The rotation part of a left-handed transformation as Selection::rotate()
// takes it (Geometry::TransformationSVD's u * v^T); the rotation itself
// turns the other way about any axis but X, which a Z rotation inverts.
Slic3r::Transform3d rigid_rotation(const Slic3r::Geometry::Transformation& trafo, Slic3r::Transform3d& rotation_matrix)
{
    using namespace Slic3r;
    Transform3d rotation = trafo.get_rotation_matrix();
    if (trafo.is_left_handed()) {
        Geometry::TransformationSVD svd(trafo);
        rotation = svd.u * svd.v.transpose();
        // ensure the rotation has the proper direction
        rotation_matrix = rotation_matrix.inverse();
    }
    return rotation;
}

// GLGizmoSVG::calculate_scale() and get_scale_for_tolerance(): the larger of
// the world's scales of the volume's width and height, without the fix of a
// 3MF file.
double svg_scale_for_tolerance(const Slic3r::ModelVolume& volume, const Slic3r::ModelInstance& instance)
{
    using namespace Slic3r;
    Transform3d to_world = instance.get_matrix() * volume.get_matrix();
    if (volume.emboss_shape.has_value() && volume.emboss_shape->fix_3mf_tr.has_value())
        to_world = to_world * volume.emboss_shape->fix_3mf_tr->inverse();
    const auto to_world_linear = to_world.linear();
    auto calc = [&to_world_linear](const Vec3d& axe) -> float {
        const Vec3d axe_world = to_world_linear * axe;
        const double norm_sq = axe_world.squaredNorm();
        return is_approx(norm_sq, 1.) ? 1.f : static_cast<float>(std::sqrt(norm_sq));
    };
    return std::max(calc(Vec3d::UnitX()), calc(Vec3d::UnitY()));
}

// GLGizmoSVG::process() of an SVG volume: embossed anew from its shape, which
// is sampled anew from its picture for the volume's size when resampled.
void reemboss_svg(
    Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    Slic3r::ModelVolume& volume,
    bool resampled,
    const std::string& output_prefix,
    ImportedModels& result
)
{
    using namespace Slic3r;
    EmbossInput input;
    input.shape = *volume.emboss_shape;
    if (resampled && input.shape.svg_file.has_value()) {
        EmbossShape::SvgFile& svg = *input.shape.svg_file;
        if (svg.image == nullptr && init_image(svg) == nullptr) {
            result.message = "Nano SVG parser can't load the SVG.";
            return;
        }
        const double scale = svg_scale_for_tolerance(volume, *volume.get_object()->instances.front());
        NSVGLineParams params{tesselation_tolerance(scale)};
        input.shape.shapes_with_ids = create_shape_with_ids(*svg.image, params);
        input.shape.final_shape = {}; // reset cache for final shape
    }
    input.volume_name = volume.name;
    input.is_outside = volume.type() == ModelVolumeType::MODEL_PART;
    update_emboss(model, config, volume, input, {}, output_prefix, result);
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
        reemboss_text(model, config, *volume, text, style, matrix, output_prefix, result);
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
        // GLGizmoSVG::process(): the volume's shape, from another file when given,
        // its curves sampled for the size the volume has (get_scale_for_tolerance()).
        EmbossInput input;
        input.shape = *volume->emboss_shape;
        if (!svg_path.empty()) {
            const double scale = svg_scale_for_tolerance(*volume, *volume->get_object()->instances.front());
            std::optional<Slic3r::EmbossShape> shape = select_shape(svg_path, tesselation_tolerance(scale), result.message);
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
        const double width = (linear * Vec3d::UnitX()).norm();
        result.scale_width = std::abs(width - 1.) < EPSILON ? 1. : width;
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
        if (shape.fix_3mf_tr.has_value()) {
            result.fix.assign(shape.fix_3mf_tr->data(), shape.fix_3mf_tr->data() + 16);
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

// EmbossStyleManager.cpp's StylesSerializable: a style's section of the app
// configuration. The desktop app names a style's font by its wxFont
// descriptor, of the system's own type (WxFontUtils::get_current_type());
// the phone's fonts are files, so a style names its font by the file's path.
namespace {

using Section = std::map<std::string, std::string>;

const std::string APP_CONFIG_FONT_NAME        = "name";
const std::string APP_CONFIG_FONT_DESCRIPTOR  = "descriptor";
const std::string APP_CONFIG_FONT_LINE_HEIGHT = "line_height";
const std::string APP_CONFIG_FONT_DEPTH       = "depth";
const std::string APP_CONFIG_FONT_USE_SURFACE = "use_surface";
const std::string APP_CONFIG_PER_GLYPH        = "per_glyph";
const std::string APP_CONFIG_VERTICAL_ALIGN   = "vertical_align";
const std::string APP_CONFIG_HORIZONTAL_ALIGN = "horizontal_align";
const std::string APP_CONFIG_FONT_BOLDNESS    = "boldness";
const std::string APP_CONFIG_FONT_SKEW        = "skew";
const std::string APP_CONFIG_FONT_DISTANCE    = "distance";
const std::string APP_CONFIG_FONT_ANGLE       = "angle";
const std::string APP_CONFIG_FONT_COLLECTION  = "collection";
const std::string APP_CONFIG_FONT_CHAR_GAP    = "char_gap";
const std::string APP_CONFIG_FONT_LINE_GAP    = "line_gap";

const std::string APP_CONFIG_ACTIVE_FONT = "active_font";

// FontProp::HorizontalAlign (left, center, right) and VerticalAlign (top,
// center, bottom) by their names, as the bimaps horizontal_align_to_name and
// vertical_align_to_name have them.
const std::array<const char*, 3> horizontal_align_names{"left", "center", "right"};
const std::array<const char*, 3> vertical_align_names{"top", "middle", "bottom"};

std::string create_section_name(unsigned index)
{
    return Slic3r::AppConfig::SECTION_EMBOSS_STYLE + ':' + std::to_string(index);
}

// check only existence of flag
bool read_flag(const Section& section, const std::string& key, bool& value)
{
    auto item = section.find(key);
    if (item == section.end())
        return false;

    value = true;
    return true;
}

bool read_align(const Section& section, const std::string& key, const std::array<const char*, 3>& names, int& value)
{
    auto item = section.find(key);
    if (item == section.end())
        return false;

    const std::string& data = item->second;
    if (data.empty())
        return false;

    const auto it = std::find_if(names.begin(), names.end(), [&data](const char* name) { return data == name; });
    // An unknown name is the centre.
    value = (it != names.end()) ? int(it - names.begin()) : 1;
    return true;
}

bool read_float(const Section& section, const std::string& key, float& value)
{
    auto item = section.find(key);
    if (item == section.end())
        return false;
    const std::string& data = item->second;
    if (data.empty())
        return false;
    float value_;
    fast_float::from_chars(data.c_str(), data.c_str() + data.length(), value_);
    // read only non zero value
    if (std::fabs(value_) <= std::numeric_limits<float>::epsilon())
        return false;

    value = value_;
    return true;
}

bool read_float(const Section& section, const std::string& key, std::optional<double>& value)
{
    float value_ = 0.f;
    if (!read_float(section, key, value_))
        return false;
    value = value_;
    return true;
}

bool read_int(const Section& section, const std::string& key, std::optional<int>& value)
{
    auto item = section.find(key);
    if (item == section.end())
        return false;
    const std::string& data = item->second;
    if (data.empty())
        return false;
    int value_ = std::atoi(data.c_str());
    if (value_ == 0)
        return false;

    value = value_;
    return true;
}

// The collection number: read only a positive one.
bool read_unsigned(const Section& section, const std::string& key, std::optional<int>& value)
{
    auto item = section.find(key);
    if (item == section.end())
        return false;
    const std::string& data = item->second;
    if (data.empty())
        return false;
    int value_ = std::atoi(data.c_str());
    if (value_ <= 0)
        return false;

    value = value_;
    return true;
}

std::optional<TextStyle> load_style(const Section& app_cfg_section)
{
    auto path_it = app_cfg_section.find(APP_CONFIG_FONT_DESCRIPTOR);
    if (path_it == app_cfg_section.end())
        return {};

    TextStyle s;
    s.font_path = path_it->second;
    auto name_it = app_cfg_section.find(APP_CONFIG_FONT_NAME);
    const std::string default_name = "font_name";
    s.name = (name_it == app_cfg_section.end()) ? default_name : name_it->second;

    // FontProp's and EmbossProjection's defaults.
    float size_in_mm = 10.f;
    read_float(app_cfg_section, APP_CONFIG_FONT_LINE_HEIGHT, size_in_mm);
    s.size_in_mm = size_in_mm;
    float depth = 1.;
    read_float(app_cfg_section, APP_CONFIG_FONT_DEPTH, depth);
    s.depth = depth;
    read_flag(app_cfg_section, APP_CONFIG_FONT_USE_SURFACE, s.use_surface);
    read_flag(app_cfg_section, APP_CONFIG_PER_GLYPH, s.per_glyph);
    read_align(app_cfg_section, APP_CONFIG_HORIZONTAL_ALIGN, horizontal_align_names, s.horizontal_align);
    read_align(app_cfg_section, APP_CONFIG_VERTICAL_ALIGN, vertical_align_names, s.vertical_align);
    read_float(app_cfg_section, APP_CONFIG_FONT_BOLDNESS, s.boldness);
    read_float(app_cfg_section, APP_CONFIG_FONT_SKEW, s.skew);
    read_float(app_cfg_section, APP_CONFIG_FONT_DISTANCE, s.distance);
    read_float(app_cfg_section, APP_CONFIG_FONT_ANGLE, s.angle);
    read_unsigned(app_cfg_section, APP_CONFIG_FONT_COLLECTION, s.collection_number);
    read_int(app_cfg_section, APP_CONFIG_FONT_CHAR_GAP, s.char_gap);
    read_int(app_cfg_section, APP_CONFIG_FONT_LINE_GAP, s.line_gap);
    return s;
}

void store_style(Slic3r::AppConfig& cfg, const TextStyle& s, unsigned index)
{
    // The style's values are floats, written as std::to_string() writes them.
    auto to_string = [](double value) { return std::to_string(static_cast<float>(value)); };
    Section data;
    data[APP_CONFIG_FONT_NAME]        = s.name;
    data[APP_CONFIG_FONT_DESCRIPTOR]  = s.font_path;
    data[APP_CONFIG_FONT_LINE_HEIGHT] = to_string(s.size_in_mm);
    data[APP_CONFIG_FONT_DEPTH]       = to_string(s.depth);
    if (s.use_surface)
        data[APP_CONFIG_FONT_USE_SURFACE] = "true";
    if (s.per_glyph)
        data[APP_CONFIG_PER_GLYPH] = "true";
    if (s.horizontal_align != 1 && s.horizontal_align >= 0 && s.horizontal_align < 3)
        data[APP_CONFIG_HORIZONTAL_ALIGN] = horizontal_align_names[std::size_t(s.horizontal_align)];
    if (s.vertical_align != 1 && s.vertical_align >= 0 && s.vertical_align < 3)
        data[APP_CONFIG_VERTICAL_ALIGN] = vertical_align_names[std::size_t(s.vertical_align)];
    if (s.boldness.has_value())
        data[APP_CONFIG_FONT_BOLDNESS] = to_string(*s.boldness);
    if (s.skew.has_value())
        data[APP_CONFIG_FONT_SKEW] = to_string(*s.skew);
    if (s.distance.has_value())
        data[APP_CONFIG_FONT_DISTANCE] = to_string(*s.distance);
    if (s.angle.has_value())
        data[APP_CONFIG_FONT_ANGLE] = to_string(*s.angle);
    if (s.collection_number.has_value())
        data[APP_CONFIG_FONT_COLLECTION] = std::to_string(static_cast<unsigned>(*s.collection_number));
    if (s.char_gap.has_value())
        data[APP_CONFIG_FONT_CHAR_GAP] = std::to_string(*s.char_gap);
    if (s.line_gap.has_value())
        data[APP_CONFIG_FONT_LINE_GAP] = std::to_string(*s.line_gap);
    cfg.set_section(create_section_name(index), std::move(data));
}

void store_style_index(Slic3r::AppConfig& cfg, std::size_t index)
{
    // store actual font index
    // active font first index is +1 to correspond with section name
    Section data;
    // OrcaSlicer writes the index from 0, which load_style_index() reads as from 1.
    data[APP_CONFIG_ACTIVE_FONT] = std::to_string(index);
    cfg.set_section(Slic3r::AppConfig::SECTION_EMBOSS_STYLE, std::move(data));
}

std::optional<std::size_t> load_style_index(const Slic3r::AppConfig& cfg)
{
    if (!cfg.has_section(Slic3r::AppConfig::SECTION_EMBOSS_STYLE))
        return {};

    auto section = cfg.get_section(Slic3r::AppConfig::SECTION_EMBOSS_STYLE);
    auto it      = section.find(APP_CONFIG_ACTIVE_FONT);
    if (it == section.end())
        return {};

    std::size_t active_font = static_cast<std::size_t>(std::atoi(it->second.c_str()));
    // order in config starts with number 1
    return active_font - 1;
}

void make_unique_name(const std::vector<TextStyle>& styles, std::string& name)
{
    auto is_unique = [&styles](const std::string& name) {
        for (const TextStyle& it : styles)
            if (it.name == name) return false;
        return true;
    };

    // Style name can't be empty so default name is set
    if (name.empty()) name = "Text style";

    // When name is already unique, nothing need to be changed
    if (is_unique(name)) return;

    // when there is previous version of style name only find number
    const char* prefix = " (";
    const char  suffix = ')';
    auto pos = name.find_last_of(prefix);
    if (name.c_str()[name.size() - 1] == suffix &&
        pos != std::string::npos) {
        // short name by ord number
        name = name.substr(0, pos);
    }

    int order = 1; // start with value 2 to represents same font name
    std::string new_name;
    do {
        new_name = name + prefix + std::to_string(++order) + suffix;
    } while (!is_unique(new_name));
    name = new_name;
}

std::vector<TextStyle> load_styles(const Slic3r::AppConfig& cfg)
{
    std::vector<TextStyle> result;
    // human readable index inside of config starts from 1 !!
    unsigned    index        = 1;
    std::string section_name = create_section_name(index);
    while (cfg.has_section(section_name)) {
        std::optional<TextStyle> style_opt = load_style(cfg.get_section(section_name));
        if (style_opt.has_value()) {
            make_unique_name(result, style_opt->name);
            result.emplace_back(*style_opt);
        }

        section_name = create_section_name(++index);
    }
    return result;
}

void store_styles(Slic3r::AppConfig& cfg, const std::vector<TextStyle>& styles)
{
    // store styles
    unsigned index = 1;
    for (const TextStyle& style : styles) {
        store_style(cfg, style, index);
        ++index;
    }

    // remove rest of font sections (after deletation)
    std::string section_name = create_section_name(index);
    while (cfg.has_section(section_name)) {
        cfg.clear_section(section_name);
        section_name = create_section_name(index);
        ++index;
    }
}

} // namespace

ImportedModels rename_text_style(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    const std::string& old_name,
    const std::string& new_name,
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
        if (object_index >= model.objects.size()) {
            result.message = "The object is not on the plate";
            return result;
        }
        Slic3r::ModelObject& object = *model.objects[object_index];
        // rename style in all objects and volumes
        bool renamed = false;
        for (Slic3r::ModelVolume* mv : object.volumes) {
            if (!mv->text_configuration.has_value()) continue;
            std::string& name = mv->text_configuration->style.name;
            if (name != old_name) continue;
            name = new_name;
            renamed = true;
        }
        if (renamed && !detail::write_objects({&object}, output_prefix, result)) {
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

TextStyles load_text_styles()
{
    TextStyles result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().config == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    const Slic3r::AppConfig& config = *detail::engine().config;
    result.styles = load_styles(config);
    if (std::optional<std::size_t> active = load_style_index(config); active.has_value() && *active < result.styles.size()) {
        result.active = static_cast<std::int64_t>(*active);
    }
    result.status = SceneStatus::success;
    return result;
}

TextStyles store_text_styles(const std::vector<TextStyle>& styles, std::int64_t active)
{
    TextStyles result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().config == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    Slic3r::AppConfig& config = *detail::engine().config;
    if (active >= 0) {
        store_style_index(config, static_cast<std::size_t>(active));
    }
    store_styles(config, styles);
    detail::save_config(detail::engine());
    result.styles = styles;
    result.active = active;
    result.status = SceneStatus::success;
    return result;
}

ImportedModels transform_emboss(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t instance_index,
    std::size_t volume_index,
    const EmbossTransform& transform,
    const std::string& text,
    const TextStyle& style,
    bool re_emboss,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    using namespace Slic3r;
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    try {
        DynamicPrintConfig config;
        Model model;
        if (!prepare(plate, profiles, config, model, result)) {
            return result;
        }
        ModelVolume* volume = volume_at(model, object_index, volume_index, result.message);
        if (volume == nullptr) {
            return result;
        }
        if (!volume->emboss_shape.has_value()) {
            result.message = "The volume is neither text nor SVG";
            return result;
        }
        const bool is_text = volume->text_configuration.has_value();
        ModelObject& object = *volume->get_object();
        if (instance_index >= object.instances.size()) {
            result.message = "The object has no such copy";
            return result;
        }
        ModelInstance& instance = *object.instances[instance_index];
        // is_embossed_object(): the text is the whole object, whose copy turns instead.
        const bool embossed_object = object.volumes.size() == 1;
        const std::optional<Transform3d>& fix = volume->emboss_shape->fix_3mf_tr;
        // selection_transform(): the fix of a text from a 3MF file is left out while it turns.
        auto unfixed = [&]() {
            if (fix.has_value()) volume->set_transformation(Geometry::Transformation(volume->get_matrix() * fix->inverse()));
        };
        auto refixed = [&]() {
            if (fix.has_value()) volume->set_transformation(Geometry::Transformation(volume->get_matrix() * (*fix)));
        };

        if (transform.rotate != 0.0) {
            // do_local_z_rotate()
            double relative_angle = transform.rotate;
            // Fix angle for mirrored volume
            const bool is_instance_mirrored = has_reflection(instance.get_matrix());
            const bool is_mirrored = embossed_object ? is_instance_mirrored : (is_instance_mirrored != has_reflection(volume->get_matrix()));
            if (is_mirrored)
                relative_angle *= -1;
            unfixed();
            Transform3d rotation_matrix = Geometry::rotation_transform(Vec3d(0., 0., relative_angle));
            const Geometry::Transformation inst_trafo = instance.get_transformation();
            if (embossed_object) {
                // Selection::rotate() of a full instance, Instance_Relative_Joint:
                // the instance rotates as a rigid body about the selection's centre.
                const Transform3d old_instance = instance.get_matrix();
                const Transform3d inst_rotation_matrix = rigid_rotation(inst_trafo, rotation_matrix);
                const Transform3d inst_matrix_no_offset = inst_trafo.get_matrix_no_offset();
                rotation_matrix = inst_matrix_no_offset.inverse() * inst_rotation_matrix * rotation_matrix * inst_rotation_matrix.inverse() * inst_matrix_no_offset;
                // rotate around selection center
                const Vec3d pivot = detail::bounding_sphere(object, instance).first;
                const Vec3d inst_pivot = inst_matrix_no_offset.inverse() * (pivot - inst_trafo.get_offset());
                rotation_matrix = Geometry::translation_transform(inst_pivot) * rotation_matrix * Geometry::translation_transform(-inst_pivot);
                // transform_instance_relative()
                instance.set_transformation(Geometry::Transformation(inst_trafo.get_matrix() * rotation_matrix));
                synchronize_instances(object, instance, old_instance);
            } else {
                // Selection::rotate() of a single volume, Local_Relative_Joint:
                // the volume rotates as a rigid body.
                const Geometry::Transformation vol_trafo = volume->get_transformation();
                const Transform3d vol_matrix_no_offset = vol_trafo.get_matrix_no_offset();
                const Transform3d inst_scale_matrix = inst_trafo.get_scaling_factor_matrix();
                const Transform3d vol_rotation_matrix = rigid_rotation(vol_trafo, rotation_matrix);
                rotation_matrix = vol_matrix_no_offset.inverse() * inst_scale_matrix.inverse() * vol_rotation_matrix * rotation_matrix *
                    vol_rotation_matrix.inverse() * inst_scale_matrix * vol_matrix_no_offset;
                // transform_volume_relative()
                volume->set_transformation(Geometry::Transformation(vol_trafo.get_matrix() * rotation_matrix));
            }
            refixed();
        }

        // do_local_z_move(): Selection::translate() with Local, which moves a
        // single volume and leaves a whole instance as it is.
        if (transform.move != 0.0 && !embossed_object) {
            unfixed();
            const Geometry::Transformation vol_trafo = volume->get_transformation();
            const Geometry::Transformation inst_trafo = instance.get_transformation();
            const Vec3d displacement = Vec3d::UnitZ() * transform.move;
            volume->set_offset(vol_trafo.get_offset() + inst_trafo.get_scaling_factor_matrix().inverse() * vol_trafo.get_rotation_matrix() * displacement);
            refixed();
        }

        if (transform.camera_position.size() == 3 && transform.camera_forward.size() == 3) {
            // face_selected_volume_to_camera()
            const std::optional<double> wanted_up_limit = transform.keep_up ? std::optional<double>(UP_LIMIT) : std::optional<double>{};
            Transform3d volume_tr = volume->get_matrix();
            if (fix.has_value())
                volume_tr = volume_tr * fix->inverse();
            const Transform3d instance_tr     = instance.get_matrix();
            const Transform3d instance_tr_inv = instance_tr.inverse();
            const Transform3d world_tr        = instance_tr * volume_tr; // without sla !!!
            std::optional<float> current_angle;
            if (wanted_up_limit.has_value())
                current_angle = Emboss::calc_up(world_tr, *wanted_up_limit);
            const Vec3d world_position = (instance_tr * volume->get_matrix()) * Vec3d::Zero();
            const Vec3d camera_position(transform.camera_position[0], transform.camera_position[1], transform.camera_position[2]);
            const Vec3d camera_forward(transform.camera_forward[0], transform.camera_forward[1], transform.camera_forward[2]);
            const Vec3d wanted_direction = transform.perspective ? Vec3d(camera_position - world_position) : Vec3d(-camera_forward);
            const Transform3d new_volume_tr = get_volume_transformation(world_tr, wanted_direction, world_position,
                fix, instance_tr_inv, current_angle, wanted_up_limit);
            if (embossed_object) {
                // transform instance instead of volume
                const Transform3d old_instance = instance.get_matrix();
                const Transform3d new_instance_tr = instance_tr * new_volume_tr * volume->get_matrix().inverse();
                instance.set_transformation(Geometry::Transformation(new_instance_tr));
                // set same transformation to other instances when instance is embossed object
                synchronize_instances(object, instance, old_instance);
            } else {
                // write result transformation
                volume->set_transformation(Geometry::Transformation(new_volume_tr));
            }
        }

        // Selection::scale_and_translate() of the SVG's width and height
        // (draw_size()) and of its mirror (Selection::mirror()).
        Vec3d relative_scale = Vec3d::Ones();
        if (transform.scale.size() == 3)
            relative_scale = Vec3d(transform.scale[0], transform.scale[1], transform.scale[2]);
        if (transform.mirror == 0 || transform.mirror == 1)
            relative_scale[transform.mirror] *= -1.;
        const bool scaled = transform.scale.size() == 3;
        if (!relative_scale.isApprox(Vec3d::Ones())) {
            unfixed();
            const Geometry::Transformation inst_trafo = instance.get_transformation();
            if (embossed_object) {
                // Instance_Relative_Joint: the copy scales about the selection's centre.
                const Transform3d old_instance = instance.get_matrix();
                const Vec3d dragging_center = object.instance_bounding_box(instance_index).center();
                const Vec3d world_inst_pivot = dragging_center - inst_trafo.get_offset();
                const Vec3d local_inst_pivot = inst_trafo.get_matrix_no_offset().inverse() * world_inst_pivot;
                Matrix3d inst_rotation, inst_scale;
                inst_trafo.get_matrix().computeRotationScaling(&inst_rotation, &inst_scale);
                const Transform3d offset_trafo = Geometry::translation_transform(inst_trafo.get_offset());
                const Transform3d scale_trafo = Transform3d(inst_scale) * Geometry::scale_transform(relative_scale);
                instance.set_transformation(Geometry::Transformation(Geometry::translation_transform(world_inst_pivot) * offset_trafo *
                    Transform3d(inst_rotation) * scale_trafo * Geometry::translation_transform(-local_inst_pivot)));
                synchronize_instances(object, instance, old_instance);
            } else {
                // Local_Relative_Independent of a single volume.
                volume->set_transformation(Geometry::Transformation(volume->get_matrix() * Geometry::scale_transform(relative_scale)));
            }
            refixed();
        }

        // volume_transformation_changed(): a text on the surface or per glyph, or
        // an SVG on the surface or of another size, is embossed anew.
        if (is_text && (re_emboss || volume->emboss_shape->projection.use_surface || style.per_glyph)) {
            reemboss_text(model, config, *volume, text, style, {}, output_prefix, result);
            return result;
        }
        if (!is_text && (re_emboss || volume->emboss_shape->projection.use_surface || scaled)) {
            reemboss_svg(model, config, *volume, scaled, output_prefix, result);
            return result;
        }
        // Plater::changed_object(): the object rests on the plate, as it may sink.
        object.invalidate_bounding_box();
        object.ensure_on_bed(true);
        model.update_print_volume_state(detail::build_volume_of(config));
        if (!detail::write_objects({&object}, output_prefix, result)) {
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

// GLGizmoSVG.cpp's drawing of the SVG window's preview and its warnings.
namespace {

// inspired by Xiaolin Wu's line algorithm - https://en.wikipedia.org/wiki/Xiaolin_Wu's_line_algorithm
// Draw inner part of polygon CCW line as full brightness(edge of expolygon)
void wu_draw_line_side(Slic3r::Linef line, const std::function<void(int x, int y, float brightess)>& plot)
{
    using namespace Slic3r;
    auto ipart = [](float x) -> int { return static_cast<int>(std::floor(x)); };
    auto round = [](float x) -> float { return std::round(x); };
    auto fpart = [](float x) -> float { return x - std::floor(x); };
    auto rfpart = [=](float x) -> float { return 1 - fpart(x); };

    Vec2d d = line.b - line.a;
    const bool steep = std::abs(d.y()) > std::abs(d.x());
    bool is_full; // identify full brightness pixel
    if (steep) {
        is_full = d.y() >= 0;
        std::swap(line.a.x(), line.a.y());
        std::swap(line.b.x(), line.b.y());
        std::swap(d.x(), d.y());
    } else
        is_full = d.x() < 0; // opposit direction of y

    if (line.a.x() > line.b.x()) {
        std::swap(line.a.x(), line.b.x());
        std::swap(line.a.y(), line.b.y());
        d *= -1;
    }
    const float gradient = (d.x() == 0) ? 1. : d.y() / d.x();

    int xpx11;
    float intery;
    {
        const float xend = round(line.a.x());
        const float yend = line.a.y() + gradient * (xend - line.a.x());
        const float xgap = rfpart(line.a.x() + 0.5f);
        xpx11 = int(xend);
        const int ypx11 = ipart(yend);
        if (steep) {
            plot(ypx11, xpx11, is_full ? 1.f : (rfpart(yend) * xgap));
            plot(ypx11 + 1, xpx11, !is_full ? 1.f : (fpart(yend) * xgap));
        } else {
            plot(xpx11, ypx11, is_full ? 1.f : (rfpart(yend) * xgap));
            plot(xpx11, ypx11 + 1, !is_full ? 1.f : (fpart(yend) * xgap));
        }
        intery = yend + gradient;
    }

    int xpx12;
    {
        const float xend = round(line.b.x());
        const float yend = line.b.y() + gradient * (xend - line.b.x());
        const float xgap = rfpart(line.b.x() + 0.5f);
        xpx12 = int(xend);
        const int ypx12 = ipart(yend);
        if (steep) {
            plot(ypx12, xpx12, is_full ? 1.f : (rfpart(yend) * xgap));
            plot(ypx12 + 1, xpx12, !is_full ? 1.f : (fpart(yend) * xgap));
        } else {
            plot(xpx12, ypx12, is_full ? 1.f : (rfpart(yend) * xgap));
            plot(xpx12, ypx12 + 1, !is_full ? 1.f : (fpart(yend) * xgap));
        }
    }

    if (steep) {
        if (is_full) {
            for (int x = xpx11 + 1; x < xpx12; x++) {
                plot(ipart(intery), x, 1.f);
                plot(ipart(intery) + 1, x, fpart(intery));
                intery += gradient;
            }
        } else {
            for (int x = xpx11 + 1; x < xpx12; x++) {
                plot(ipart(intery), x, rfpart(intery));
                plot(ipart(intery) + 1, x, 1.f);
                intery += gradient;
            }
        }
    } else {
        if (is_full) {
            for (int x = xpx11 + 1; x < xpx12; x++) {
                plot(x, ipart(intery), 1.f);
                plot(x, ipart(intery) + 1, fpart(intery));
                intery += gradient;
            }
        } else {
            for (int x = xpx11 + 1; x < xpx12; x++) {
                plot(x, ipart(intery), rfpart(intery));
                plot(x, ipart(intery) + 1, 1.f);
                intery += gradient;
            }
        }
    }
}

constexpr unsigned PREVIEW_CHANNELS = 4;
using PreviewColor = std::array<unsigned char, PREVIEW_CHANNELS>;

void draw_side_outline(const Slic3r::ExPolygons& shape, const PreviewColor& color, std::vector<unsigned char>& data, size_t data_width, double scale)
{
    using namespace Slic3r;
    constexpr unsigned N = PREVIEW_CHANNELS;
    int count_lines = data.size() / (N * data_width);
    size_t data_line = N * data_width;
    auto get_offset = [count_lines, data_line](int x, int y) {
        // NOTE: y has opposit direction in texture
        return (count_lines - y - 1) * data_line + x * N;
    };

    // overlap color
    auto draw = [&data, data_width, count_lines, get_offset, &color](int x, int y, float brightess) {
        if (x < 0 || y < 0 || static_cast<size_t>(x) >= data_width || y >= count_lines)
            return; // out of image
        size_t offset = get_offset(x, y);
        bool change_color = false;
        for (size_t i = 0; i < N - 1; ++i) {
            if (data[offset + i] != color[i]) {
                data[offset + i] = color[i];
                change_color = true;
            }
        }

        unsigned char& alpha = data[offset + N - 1];
        if (alpha == 0 || change_color) {
            alpha = static_cast<unsigned char>(std::round(brightess * 255));
        } else if (alpha != 255) {
            alpha = static_cast<unsigned char>(std::min(255, int(alpha) + static_cast<int>(std::round(brightess * 255))));
        }
    };

    Linesf lines = to_linesf(shape);
    // scale lines to pixels
    if (!is_approx(scale, 1.)) {
        for (Linef& line : lines) {
            line.a *= scale;
            line.b *= scale;
        }
    }

    for (const Linef& line : lines)
        wu_draw_line_side(line, draw);
}

/// Draw filled ExPolygon into data
/// line by line inspired by: http://alienryderflex.com/polygon_fill/
void draw_filled(const Slic3r::ExPolygons& shape, const PreviewColor& color, std::vector<unsigned char>& data, size_t data_width, double scale)
{
    using namespace Slic3r;
    constexpr unsigned N = PREVIEW_CHANNELS;
    BoundingBox bb_unscaled = get_extents(shape);

    Linesf lines = to_linesf(shape);
    BoundingBoxf bb(bb_unscaled.min.cast<double>(), bb_unscaled.max.cast<double>());

    // scale lines to pixels
    if (!is_approx(scale, 1.)) {
        for (Linef& line : lines) {
            line.a *= scale;
            line.b *= scale;
        }
        bb.min *= scale;
        bb.max *= scale;
    }

    int count_lines = data.size() / (N * data_width);
    size_t data_line = N * data_width;
    auto get_offset = [count_lines, data_line](int x, int y) {
        // NOTE: y has opposit direction in texture
        return (count_lines - y - 1) * data_line + x * N;
    };
    auto set_color = [&data, &color, get_offset](int x, int y) {
        size_t offset = get_offset(x, y);
        if (data[offset + N - 1] != 0)
            return; // already setted by line
        for (unsigned i = 0; i < N; ++i)
            data[offset + i] = color[i];
    };

    // anti aliased drawing of lines
    auto draw = [&data, width = static_cast<int>(data_width), count_lines, get_offset, &color](int x, int y, float brightess) {
        if (x < 0 || y < 0 || x >= width || y >= count_lines)
            return; // out of image
        size_t offset = get_offset(x, y);
        unsigned char& alpha = data[offset + N - 1];
        if (alpha == 0) {
            alpha = static_cast<unsigned char>(std::round(brightess * 255));
            for (size_t i = 0; i < N - 1; ++i)
                data[offset + i] = color[i];
        } else if (alpha != 255) {
            alpha = static_cast<unsigned char>(std::min(255, int(alpha) + static_cast<int>(std::round(brightess * 255))));
        }
    };

    for (const Linef& line : lines)
        wu_draw_line_side(line, draw);

    auto tree = Slic3r::AABBTreeLines::build_aabb_tree_over_indexed_lines(lines);

    // range for intersection line
    double x1 = bb.min.x() - 1.f;
    double x2 = bb.max.x() + 1.f;

    int max_y = std::min(count_lines, static_cast<int>(std::round(bb.max.y())));
    for (int y = std::max(0, static_cast<int>(std::round(bb.min.y()))); y < max_y; ++y) {
        double y_f = y + .5; // 0.5 ... intersection in center of pixel of pixel
        Linef line(Vec2d(x1, y_f), Vec2d(x2, y_f));
        using Intersection = std::pair<Vec2d, size_t>;
        using Intersections = std::vector<Intersection>;
        Intersections intersections = Slic3r::AABBTreeLines::get_intersections_with_line<false, Vec2d, Linef>(lines, tree, line);
        if (intersections.empty())
            continue;

        // sort intersections by x
        std::sort(intersections.begin(), intersections.end(),
            [](const Intersection& i1, const Intersection& i2) { return i1.first.x() < i2.first.x(); });

        // draw lines
        for (size_t i = 0; i + 1 < intersections.size(); i += 2) {
            const Vec2d& p2 = intersections[i + 1].first;
            if (p2.x() < 0)
                continue; // out of data

            const Vec2d& p1 = intersections[i].first;
            if (p1.x() > data_width)
                break; // out of data

            // clamp to data
            int max_x = std::min(static_cast<int>(data_width - 1), static_cast<int>(std::round(p2.x())));
            for (int x = std::max(0, static_cast<int>(std::round(p1.x()))); x <= max_x; ++x)
                set_color(x, y);
        }
    }
}

/// Union shape defined by glyphs
Slic3r::ExPolygons union_shapes(const Slic3r::ExPolygonsWithIds& shapes)
{
    // unify to one expolygon
    Slic3r::ExPolygons result;
    for (const Slic3r::ExPolygonsWithId& shape : shapes) {
        if (shape.expoly.empty())
            continue;
        Slic3r::expolygons_append(result, shape.expoly);
    }
    return Slic3r::union_ex(result);
}

// init_texture(): the shapes filled, those that could not be healed outlined
// in red and those with warnings in orange; false for no picture.
bool draw_preview(const Slic3r::ExPolygonsWithIds& shapes_with_ids, unsigned max_size_px, const std::vector<bool>& shape_warnings,
    std::vector<unsigned char>& data, int& width, int& height)
{
    using namespace Slic3r;
    BoundingBox bb = get_extents(shapes_with_ids);
    Point bb_size = bb.size();
    double bb_width = bb_size.x(); // [in mm]
    double bb_height = bb_size.y(); // [in mm]

    bool is_widder = bb_size.x() > bb_size.y();
    double scale = 0.f;
    if (is_widder) {
        scale = max_size_px / bb_width;
        width = max_size_px;
        height = static_cast<unsigned>(std::ceil(bb_height * scale));
    } else {
        scale = max_size_px / bb_height;
        width = static_cast<unsigned>(std::ceil(bb_width * scale));
        height = max_size_px;
    }
    const int n_pixels = width * height;
    if (n_pixels <= 0)
        return false;

    data.assign(static_cast<size_t>(n_pixels) * PREVIEW_CHANNELS, 0);

    // Union All shapes
    ExPolygons shape = union_shapes(shapes_with_ids);

    // align to texture
    translate(shape, -bb.min);
    size_t texture_width = static_cast<size_t>(width);
    unsigned char alpha = 255; // without transparency
    PreviewColor color_shape{201, 201, 201, alpha}; // from degin by @JosefZachar
    PreviewColor color_error{237, 28, 36, alpha}; // from icon: resources/icons/flag_red.svg
    PreviewColor color_warning{237, 107, 33, alpha}; // icons orange
    // draw unhealedable shape
    for (const ExPolygonsWithId& shapes_with_id : shapes_with_ids)
        if (!shapes_with_id.is_healed) {
            ExPolygons bad_shape = shapes_with_id.expoly; // copy
            translate(bad_shape, -bb.min); // align to texture
            draw_side_outline(bad_shape, color_error, data, texture_width, scale);
        }
    // Draw shape with warning
    if (!shape_warnings.empty()) {
        for (const ExPolygonsWithId& shapes_with_id : shapes_with_ids) {
            if (shapes_with_id.id >= shape_warnings.size())
                continue;
            if (!shape_warnings[shapes_with_id.id])
                continue; // no warnings for shape
            ExPolygons warn_shape = shapes_with_id.expoly; // copy
            translate(warn_shape, -bb.min); // align to texture
            draw_side_outline(warn_shape, color_warning, data, texture_width, scale);
        }
    }

    // Draw rest of shape
    draw_filled(shape, color_shape, data, texture_width, scale);
    return true;
}

bool is_closed(NSVGpath* path)
{
    for (; path != NULL; path = path->next)
        if (path->next == NULL && path->closed)
            return true;
    return false;
}

UiText ui_text(const char* msgid, std::vector<std::string> args = {})
{
    UiText text;
    text.msgid = msgid;
    text.args = std::move(args);
    return text;
}

// The float as GUI::format() writes it.
std::string format_float(float value)
{
    std::ostringstream stream;
    stream << value;
    return stream.str();
}

const float warning_preccission = 1e-4f;

// create_fill_warning(): what of the shape's fill is unsupported; none for all supported.
std::vector<UiText> create_fill_warning(const NSVGshape& shape)
{
    if (!(shape.flags & NSVG_FLAGS_VISIBLE) || shape.fill.type == NSVG_PAINT_NONE)
        return {}; // not visible

    std::vector<UiText> warning;
    if ((shape.opacity - 1.f + warning_preccission) <= 0.f)
        warning.push_back(ui_text("Opacity (%1%)", {format_float(shape.opacity)}));

    bool is_fill_gradient = shape.fillGradient[0] != '\0';
    if (is_fill_gradient)
        warning.push_back(ui_text("Color gradient (%1%)", {shape.fillGradient}));

    switch (shape.fill.type) {
    case NSVG_PAINT_UNDEF: warning.push_back(ui_text("Undefined fill type")); break;
    case NSVG_PAINT_LINEAR_GRADIENT:
        if (!is_fill_gradient)
            warning.push_back(ui_text("Linear gradient"));
        break;
    case NSVG_PAINT_RADIAL_GRADIENT:
        if (!is_fill_gradient)
            warning.push_back(ui_text("Radial gradient"));
        break;
    }

    // Unfilled is only line which could be opened
    if (shape.fill.type != NSVG_PAINT_NONE && !is_closed(shape.paths))
        warning.push_back(ui_text("Open filled path"));
    return warning;
}

// create_stroke_warning()
std::vector<UiText> create_stroke_warning(const NSVGshape& shape)
{
    std::vector<UiText> warning;
    if (!(shape.flags & NSVG_FLAGS_VISIBLE) || shape.stroke.type == NSVG_PAINT_NONE || shape.strokeWidth <= 1e-5f)
        return {}; // not visible

    if ((shape.opacity - 1.f + warning_preccission) <= 0.f)
        warning.push_back(ui_text("Opacity (%1%)", {format_float(shape.opacity)}));

    bool is_stroke_gradient = shape.strokeGradient[0] != '\0';
    if (is_stroke_gradient)
        warning.push_back(ui_text("Color gradient (%1%)", {shape.strokeGradient}));

    switch (shape.stroke.type) {
    case NSVG_PAINT_UNDEF: warning.push_back(ui_text("Undefined stroke type")); break;
    case NSVG_PAINT_LINEAR_GRADIENT:
        if (!is_stroke_gradient)
            warning.push_back(ui_text("Linear gradient"));
        break;
    case NSVG_PAINT_RADIAL_GRADIENT:
        if (!is_stroke_gradient)
            warning.push_back(ui_text("Radial gradient"));
        break;
    }
    return warning;
}

// create_shape_warnings(): the warnings in the order the tooltip lists them,
// and for every shape id (two per NSVGshape: its fill, then its stroke)
// whether it has any.
std::vector<SvgWarning> create_shape_warnings(const Slic3r::EmbossShape& shape, float scale, std::vector<bool>& shape_flags)
{
    const std::shared_ptr<NSVGimage>& image_ptr = shape.svg_file->image;
    if (image_ptr == nullptr)
        return {SvgWarning{ui_text("Uninitialized SVG image"), {}}};

    const NSVGimage& image = *image_ptr;
    std::vector<std::vector<SvgWarning>> result;
    auto add_warning = [&result, &image](size_t index, SvgWarning message) {
        if (result.empty())
            result = std::vector<std::vector<SvgWarning>>(Slic3r::get_shapes_count(image) * 2);
        if (index < result.size())
            result[index].push_back(std::move(message));
    };

    if (!shape.final_shape.is_healed) {
        for (const Slic3r::ExPolygonsWithId& i : shape.shapes_with_ids)
            if (!i.is_healed)
                add_warning(i.id, SvgWarning{ui_text("Path can't be healed from self-intersection and multiple points."), {}});

        // This waning is not connected to NSVGshape. It is about union of paths, but Zero index is shown first
        size_t index = 0;
        add_warning(index, SvgWarning{ui_text("Final shape contains self-intersection or multiple points with same coordinate."), {}});
    }

    size_t shape_index = 0;
    for (NSVGshape* svg_shape = image.shapes; svg_shape != NULL; svg_shape = svg_shape->next, ++shape_index) {
        if (!(svg_shape->flags & NSVG_FLAGS_VISIBLE)) {
            add_warning(shape_index * 2, SvgWarning{ui_text("Shape is marked as invisible (%1%).", {svg_shape->id}), {}});
            continue;
        }

        std::vector<UiText> fill_warning = create_fill_warning(*svg_shape);
        if (!fill_warning.empty()) {
            // TRN: The first placeholder is shape identifier, the second is text describing the problem.
            add_warning(shape_index * 2, SvgWarning{ui_text("Fill of shape (%1%) contains unsupported: %2%.", {svg_shape->id}), std::move(fill_warning)});
        }

        float minimal_width_in_mm = 1e-3f;
        if (svg_shape->strokeWidth <= minimal_width_in_mm * scale) {
            add_warning(shape_index * 2, SvgWarning{ui_text("Stroke of shape (%1%) is too thin (minimal width is %2% mm).",
                {svg_shape->id, format_float(minimal_width_in_mm)}), {}});
            continue;
        }
        std::vector<UiText> stroke_warning = create_stroke_warning(*svg_shape);
        if (!stroke_warning.empty())
            add_warning(shape_index * 2 + 1, SvgWarning{ui_text("Stroke of shape (%1%) contains unsupported: %2%.", {svg_shape->id}), std::move(stroke_warning)});
    }

    std::vector<SvgWarning> flat;
    shape_flags.assign(result.size(), false);
    for (size_t index = 0; index < result.size(); ++index) {
        shape_flags[index] = !result[index].empty();
        for (SvgWarning& warning : result[index])
            flat.push_back(std::move(warning));
    }
    if (flat.empty())
        shape_flags.clear();
    return flat;
}

} // namespace

SvgPreview preview_svg(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    const std::string& png_path,
    int max_size_px,
    const ProfileSelection& profiles
)
{
    SvgPreview result;
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
            result.status = SceneStatus::model_read_failed;
            return result;
        }
        if (!volume->is_svg() || !volume->emboss_shape->svg_file.has_value()) {
            result.status = SceneStatus::model_read_failed;
            result.message = "The volume is no SVG";
            return result;
        }
        Slic3r::EmbossShape& es = *volume->emboss_shape;
        Slic3r::EmbossShape::SvgFile& svg_file = *es.svg_file;
        if (svg_file.image == nullptr && Slic3r::init_image(svg_file) == nullptr) {
            result.status = SceneStatus::model_read_failed;
            result.message = "Nano SVG parser can't load the SVG.";
            return result;
        }
        const float scale = static_cast<float>(svg_scale_for_tolerance(*volume, *volume->get_object()->instances.front()));
        if (es.shapes_with_ids.empty()) {
            Slic3r::NSVGLineParams params{tesselation_tolerance(scale)};
            es.shapes_with_ids = Slic3r::create_shape_with_ids(*svg_file.image, params);
        }
        std::vector<bool> shape_flags;
        result.warnings = create_shape_warnings(es, scale, shape_flags);
        result.svg_path = svg_file.path;
        for (const Slic3r::ExPolygonsWithId& shape : es.shapes_with_ids)
            result.points += static_cast<std::int64_t>(Slic3r::count_points(shape.expoly));
        std::vector<unsigned char> data;
        if (!draw_preview(es.shapes_with_ids, static_cast<unsigned>(std::max(1, max_size_px)), shape_flags, data, result.width, result.height) ||
            !detail::write_png_rgba(png_path, result.width, result.height, data)) {
            result.status = SceneStatus::write_failed;
            result.message = "The SVG's picture can't be written";
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

ImportedModels edit_svg_file(
    const std::vector<PlateObject>& plate,
    std::size_t object_index,
    std::size_t volume_index,
    SvgFileEdit edit,
    const std::string& path,
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
        Slic3r::EmbossShape::SvgFile& svg = *volume->emboss_shape->svg_file;
        switch (edit) {
        case SvgFileEdit::forget_path:
            // set .svg_file.path_in_3mf to remember file name
            svg.path.clear();
            break;
        case SvgFileEdit::bake:
            volume->emboss_shape.reset();
            break;
        case SvgFileEdit::save_as: {
            if (svg.file_data == nullptr) {
                result.message = "Missing data of svg file";
                return result;
            }
            std::ofstream stream(path, std::ios::binary);
            if (!stream.is_open()) {
                result.message = "Opening file: \"" + path + "\" Failed";
                return result;
            }
            stream << *svg.file_data;
            if (!stream) {
                result.message = "Opening file: \"" + path + "\" Failed";
                return result;
            }
            // change source file
            svg.path = path;
            svg.path_in_3mf.clear(); // possible change name
            break;
        }
        }
        Slic3r::ModelObject& object = *volume->get_object();
        if (!detail::write_objects({&object}, output_prefix, result)) {
            return result;
        }
        result.status = SceneStatus::success;
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        result.objects.clear();
        return result;
    }
}

} // namespace orcinus::orca
