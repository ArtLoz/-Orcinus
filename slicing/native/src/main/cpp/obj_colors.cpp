#include "obj_colors.hpp"

#include <algorithm>
#include <cstdio>
#include <map>
#include <mutex>

#include "libslic3r/Exception.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/ObjColorUtils.hpp"
#include "libslic3r/TriangleSelector.hpp"

namespace orcinus::orca {

namespace {

// g_max_color of ObjColorDialog.cpp.
constexpr int max_color = int(Slic3r::EnforcerBlockerType::ExtruderMax);

// What ObjColorPanel keeps of a file between the requests of the dialog: its
// colours (m_input_colors) and the last clustering (deal_algo()), which the
// load reads again with every answer until it ends.
struct KeptColors {
    std::vector<Slic3r::RGBA> input_colors;
    bool error{false};
    std::vector<Slic3r::RGBA> cluster_colors;
    std::vector<int> cluster_labels;
    char last_cluster_number{-2};
};

std::mutex colors_mutex;
std::map<std::string, KeptColors> kept;

// convert_to_wxColour() written as wxColour::GetAsString(wxC2S_HTML_SYNTAX).
std::string html_color(const Slic3r::RGBA& color)
{
    char text[8];
    std::snprintf(text, sizeof(text), "#%02X%02X%02X", std::clamp(int(color[0] * 255.f), 0, 255), std::clamp(int(color[1] * 255.f), 0, 255),
                  std::clamp(int(color[2] * 255.f), 0, 255));
    return text;
}

std::vector<std::string> html_colors(const std::vector<Slic3r::RGBA>& colors)
{
    std::vector<std::string> texts;
    texts.reserve(colors.size());
    for (const Slic3r::RGBA& color : colors) {
        texts.push_back(html_color(color));
    }
    return texts;
}

// ObjColorPanel::deal_algo() on the kept colours.
void deal_algo(KeptColors& colors, char cluster_number)
{
    if (colors.last_cluster_number == cluster_number) {
        return;
    }
    colors.last_cluster_number = cluster_number;
    obj_color_deal_algo(colors.input_colors, colors.cluster_colors, colors.cluster_labels, cluster_number, max_color);
}

}  // namespace

std::vector<std::string> obj_color_clusters(const std::string& path, const int cluster_number)
{
    const std::lock_guard<std::mutex> lock(colors_mutex);
    const auto colors = kept.find(path);
    if (colors == kept.end() || colors->second.error) {
        return {};
    }
    deal_algo(colors->second, char(cluster_number));
    return html_colors(colors->second.cluster_colors);
}

void release_obj_colors()
{
    const std::lock_guard<std::mutex> lock(colors_mutex);
    kept.clear();
}

namespace detail {

Slic3r::ObjImportColorFn obj_color_function(const std::string& path, const std::string& filament_color, const ObjColorChoice& choice)
{
    return [path, filament_color, choice](Slic3r::ObjDialogInOut& in_out) {
        // ObjColorDialog: a file with faces of no colour, or of a material its
        // MTL file lacks, shows an error and loads without colours.
        bool some_face_no_color = false;
        if (!in_out.deal_vertex_color) {
            some_face_no_color = in_out.input_colors.size() < in_out.model->objects[0]->volumes[0]->mesh_ptr()->facets_count();
        }
        const bool ok = in_out.lost_material_name.empty() && !some_face_no_color;
        if (!choice.chosen) {
            KeptColors colors;
            colors.error = !ok;
            ObjColorQuestion question;
            question.lost_material_name = in_out.lost_material_name;
            question.some_face_no_color = some_face_no_color;
            if (ok) {
                // ObjColorPanel::ObjColorPanel(): the colours left undefined take the first filament's.
                Slic3r::RGBA first = Slic3r::UNDEFINE_COLOR;
                Slic3r::ColorRGBA parsed;
                if (Slic3r::decode_color(filament_color, parsed)) {
                    first = {parsed.r(), parsed.g(), parsed.b(), parsed.a()};
                }
                colors.input_colors = in_out.input_colors;
                for (Slic3r::RGBA& color : colors.input_colors) {
                    if (Slic3r::color_is_equal(color, Slic3r::UNDEFINE_COLOR)) {
                        color = first;
                    }
                }
                if (in_out.is_single_color && !colors.input_colors.empty()) {
                    colors.cluster_colors = {colors.input_colors[0]};
                    colors.cluster_labels.assign(colors.input_colors.size(), 0);
                } else {
                    deal_algo(colors, -1);
                }
                question.cluster_colors = html_colors(colors.cluster_colors);
                // m_color_num_recommend
                question.recommended = int(colors.cluster_colors.size());
            }
            {
                const std::lock_guard<std::mutex> lock(colors_mutex);
                kept[path] = std::move(colors);
            }
            throw ObjColorPending{question};
        }

        KeptColors colors;
        {
            const std::lock_guard<std::mutex> lock(colors_mutex);
            const auto found = kept.find(path);
            if (found == kept.end()) {
                throw Slic3r::RuntimeError("The colours of the OBJ file are no longer kept");
            }
            colors = found->second;
        }
        if (!ok) {
            // Its OK ends the dialog as a Cancel, before any painting.
            in_out.filament_ids.clear();
            return;
        }
        Slic3r::ModelObject* object = in_out.model->objects[0];
        if (choice.cluster_filaments.empty()) {
            // ObjColorPanel::cancel_paint_color()
            in_out.filament_ids.clear();
            object->config.set("extruder", 1);
            Slic3r::ModelVolume* volume = object->volumes[0];
            volume->mmu_segmentation_facets.reset();
            volume->config.set("extruder", 1);
            in_out.first_extruder_id = 1;
            return;
        }
        if (colors.cluster_labels.size() != in_out.input_colors.size() ||
            choice.cluster_filaments.size() != colors.cluster_colors.size()) {
            throw Slic3r::RuntimeError("The colours of the OBJ file changed while the dialog was open");
        }
        // ObjColorPanel::update_filament_ids()
        in_out.filament_ids.clear();
        in_out.filament_ids.reserve(colors.cluster_labels.size());
        for (const int label : colors.cluster_labels) {
            const int filament = choice.cluster_filaments[label];
            in_out.filament_ids.emplace_back(filament > 0 ? (unsigned char) filament : 1);  // min filament_id is 1
        }
        in_out.first_extruder_id = (unsigned char) choice.cluster_filaments[0];
        // ObjColorPanel::deal_thumbnail(): the model as the thumbnail showed it.
        if (in_out.deal_vertex_color) {
            Slic3r::Model::obj_import_vertex_color_deal(in_out.filament_ids, in_out.first_extruder_id, in_out.model);
        } else {
            Slic3r::Model::obj_import_face_color_deal(in_out.filament_ids, in_out.first_extruder_id, in_out.model);
        }
    };
}

}  // namespace detail

}  // namespace orcinus::orca
