#include "slice_info.hpp"

#include <cstdio>
#include <fstream>
#include <utility>
#include <vector>

#include <nlohmann/json.hpp>

#include "libslic3r/BoundingBox.hpp"
#include "libslic3r/Format/bbs_3mf.hpp"
#include "libslic3r/GCode/GCodeProcessor.hpp"
#include "libslic3r/GCode/ThumbnailData.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/PrintConfig.hpp"

namespace orcinus::orca::detail {
namespace {

// Plater::priv::generate_first_layer_bbox() for the plate print is the print
// of: the first layer's box of every object and of the wipe tower, relative
// to the plate's origin.
Slic3r::PlateBBoxData first_layer_of(Slic3r::Print& print, const Slic3r::GCodeProcessorResult& result)
{
    Slic3r::PlateBBoxData bboxdata;
    std::vector<Slic3r::BBoxData>& id_bboxes = bboxdata.bbox_objs;
    Slic3r::BoundingBoxf bbox_all;
    // PartPlate::get_real_print_seq(): the plate's own sequence, which the print was applied with.
    bboxdata.is_seq_print = print.config().print_sequence == Slic3r::PrintSequence::ByObject;
    bboxdata.first_extruder = print.get_tool_ordering().first_extruder();
    bboxdata.bed_type = Slic3r::bed_type_to_gcode_string(print.config().curr_bed_type.value);
    bboxdata.first_layer_time = result.initial_layer_time;
    // get nozzle diameter
    bboxdata.nozzle_diameter = float(print.config().nozzle_diameter.get_at(bboxdata.first_extruder));
    const Slic3r::Vec3d orig = print.get_plate_origin();
    const Slic3r::Vec2d orig2d = {orig[0], orig[1]};

    Slic3r::BBoxData data;
    for (Slic3r::PrintObject* obj : print.objects_mutable()) {
        auto bb_scaled = obj->get_first_layer_bbox(data.area, data.layer_height, data.name);
        auto bb = Slic3r::unscaled(bb_scaled);
        bb.min -= orig2d;
        bb.max -= orig2d;
        bbox_all.merge(bb);
        data.area *= (SCALING_FACTOR * SCALING_FACTOR); // unscale area
        data.id = obj->id().id;
        data.bbox = {bb.min.x(), bb.min.y(), bb.max.x(), bb.max.y()};
        id_bboxes.emplace_back(data);
    }

    // add wipe tower bounding box
    if (print.has_wipe_tower()) {
        auto wt_corners = print.first_layer_wipe_tower_corners();
        // when loading gcode.3mf, wipe tower info may not be correct
        if (!wt_corners.empty()) {
            Slic3r::BoundingBox bb_scaled = {wt_corners[0], wt_corners[2]};
            auto bb = Slic3r::unscaled(bb_scaled);
            bb.min -= orig2d;
            bb.max -= orig2d;
            bbox_all.merge(bb);
            data.name = "wipe_tower";
            data.id = print.get_plate_index() + 1000;
            data.bbox = {bb.min.x(), bb.min.y(), bb.max.x(), bb.max.y()};
            id_bboxes.emplace_back(data);
        }
    }

    bboxdata.bbox_all = {bbox_all.min.x(), bbox_all.min.y(), bbox_all.max.x(), bbox_all.max.y()};
    return bboxdata;
}

}  // namespace

bool write_slice_info(Slic3r::Print& print, Slic3r::GCodeProcessorResult& result, const std::string& path)
{
    // PartPlateList::store_to_3mf_structure() with the slice info.
    using TimeMode = Slic3r::PrintEstimatedStatistics::ETimeMode;
    nlohmann::json j;
    j["gcode_prediction"] = std::to_string(int(result.print_statistics.modes[static_cast<std::size_t>(TimeMode::Normal)].time));
    std::string weight;
    if (const Slic3r::PrintStatistics& ps = print.print_statistics(); ps.total_weight != 0.0) {
        char buffer[64];
        std::snprintf(buffer, sizeof buffer, "%.2f", ps.total_weight);
        weight = buffer;
    }
    j["gcode_weight"] = weight;
    j["is_support_used"] = print.is_support_used();
    j["toolpath_outside"] = result.toolpath_outside;
    j["timelapse_warning_code"] = result.timelapse_warning_code;
    j["is_label_object_enabled"] = result.label_object_enabled;
    j["limit_filament_maps"] = result.limit_filament_maps;
    nlohmann::json layer_filaments = nlohmann::json::array();
    for (const auto& [filaments, layers] : result.layer_filaments) {
        layer_filaments.push_back({{"filaments", filaments}, {"layers", layers}});
    }
    j["layer_filaments"] = layer_filaments;
    j["filament_change_sequence"] = result.filament_change_sequence;
    j["nozzle_change_sequence"] = result.nozzle_change_sequence;
    j["optimal_assignment"] = result.optimal_assignment;

    // PlateData::parse_filament_info()
    Slic3r::PlateData parsed;
    parsed.parse_filament_info(&result);
    nlohmann::json filaments = nlohmann::json::array();
    for (const Slic3r::FilamentInfo& info : parsed.slice_filaments_info) {
        filaments.push_back({{"id", info.id},
                             {"used_m", info.used_m},
                             {"used_g", info.used_g},
                             {"used_for_object", info.used_for_object},
                             {"used_for_support", info.used_for_support}});
    }
    j["filaments"] = filaments;
    nlohmann::json warnings = nlohmann::json::array();
    for (const auto& warning : parsed.warnings) {
        warnings.push_back({{"level", warning.level}, {"msg", warning.msg}, {"error_code", warning.error_code}, {"params", warning.params}});
    }
    j["warnings"] = warnings;

    nlohmann::json first_layer;
    first_layer_of(print, result).to_json(first_layer);
    j["first_layer"] = first_layer;

    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    out << j.dump();
    return bool(out);
}

bool read_slice_info(const std::string& path, Slic3r::PlateData& plate, Slic3r::PlateBBoxData& first_layer)
{
    std::ifstream in(path, std::ios::binary);
    if (!in) {
        return false;
    }
    try {
        const nlohmann::json j = nlohmann::json::parse(in);
        plate.gcode_prediction = j.at("gcode_prediction").get<std::string>();
        plate.gcode_weight = j.at("gcode_weight").get<std::string>();
        plate.is_support_used = j.at("is_support_used").get<bool>();
        plate.toolpath_outside = j.at("toolpath_outside").get<bool>();
        plate.timelapse_warning_code = j.at("timelapse_warning_code").get<int>();
        plate.is_label_object_enabled = j.at("is_label_object_enabled").get<bool>();
        plate.limit_filament_maps = j.at("limit_filament_maps").get<std::vector<int>>();
        for (const nlohmann::json& entry : j.at("layer_filaments")) {
            plate.layer_filaments[entry.at("filaments").get<std::vector<unsigned int>>()] = entry.at("layers").get<std::vector<std::pair<int, int>>>();
        }
        plate.filament_change_sequence = j.at("filament_change_sequence").get<std::vector<unsigned int>>();
        plate.nozzle_change_sequence = j.at("nozzle_change_sequence").get<std::vector<unsigned int>>();
        plate.optimal_assignment = j.at("optimal_assignment").get<std::vector<int>>();
        for (const nlohmann::json& entry : j.at("filaments")) {
            Slic3r::FilamentInfo info;
            info.id = entry.at("id").get<int>();
            info.used_m = entry.at("used_m").get<float>();
            info.used_g = entry.at("used_g").get<float>();
            info.used_for_object = entry.at("used_for_object").get<bool>();
            info.used_for_support = entry.at("used_for_support").get<bool>();
            plate.slice_filaments_info.push_back(info);
        }
        for (const nlohmann::json& entry : j.at("warnings")) {
            Slic3r::GCodeProcessorResult::SliceWarning warning;
            warning.level = entry.at("level").get<int>();
            warning.msg = entry.at("msg").get<std::string>();
            warning.error_code = entry.at("error_code").get<std::string>();
            warning.params = entry.at("params").get<std::vector<std::string>>();
            plate.warnings.push_back(warning);
        }
        const nlohmann::json& layer = j.at("first_layer");
        first_layer.from_json(layer);
        // PlateBBoxData::from_json() leaves out the first layer's time, which to_json() writes.
        first_layer.first_layer_time = layer.at("first_layer_time").get<float>();
        return true;
    } catch (const std::exception&) {
        return false;
    }
}

}  // namespace orcinus::orca::detail
