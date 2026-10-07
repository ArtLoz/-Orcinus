// G-code files the app opens: Plater::load_gcode() of a .gcode file, and
// Print::export_gcode_from_previous_file() of a plate's G-code a .gcode.3mf
// file carries, both read by GCodeProcessor::process_file() for the preview.

#include <cmath>
#include <mutex>
#include <string>
#include <vector>

#include "libslic3r/GCode/GCodeProcessor.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/libslic3r.h"

#include "engine_context.hpp"
#include "orca_engine_adapter.hpp"
#include "slice_info.hpp"

namespace orcinus::orca {

GcodeLoad load_gcode(
    const std::string& gcode_path,
    const std::string& toolpaths_path,
    const std::string& slice_info_path,
    const int plate_index,
    const int plate_count,
    const bool apply_bed_type
)
{
    GcodeLoad result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().bundle == nullptr) {
        result.status = SliceStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    Slic3r::PresetBundle& bundle = *detail::engine().bundle;
    detail::follow_config(detail::engine());
    Slic3r::DynamicPrintConfig config = bundle.full_config();

    // process gcode
    Slic3r::GCodeProcessor processor;
    processor.init_filament_maps_and_nozzle_type_when_import_only_gcode();
    // export_gcode_from_previous_file(): the moves stand where the plate does.
    const Slic3r::Vec2d origin = detail::plate_origin(config, plate_index, plate_count);
    processor.set_xy_offset(origin.x(), origin.y());
    try {
        Slic3r::GCodeProcessor::s_IsBBLPrinter = bundle.is_bbl_vendor();
        processor.process_file(gcode_path);
    } catch (const std::exception& ex) {
        result.message = ex.what();
        return result;
    }
    // The processor's result itself, which the slice info reads as a slice's.
    Slic3r::GCodeProcessorResult&& gcode_result = processor.extract_result();

    // Plater::load_gcode(): the G-code's plate type becomes the project's.
    const Slic3r::BedType bed_type = gcode_result.bed_type;
    if (apply_bed_type && bed_type != Slic3r::BedType::btCount) {
        Slic3r::DynamicPrintConfig& proj_config = bundle.project_config;
        result.bed_type_changed = proj_config.opt_enum<Slic3r::BedType>("curr_bed_type") != bed_type;
        proj_config.set_key_value("curr_bed_type", new Slic3r::ConfigOptionEnum<Slic3r::BedType>(bed_type));
        config.set_key_value("curr_bed_type", new Slic3r::ConfigOptionEnum<Slic3r::BedType>(bed_type));
    }

    // current_print.apply(): the print of the plate the G-code stands on,
    // without objects, which its statistics and slice info are of.
    Slic3r::Print print;
    try {
        print.apply(Slic3r::Model(), config);
    } catch (const std::exception& ex) {
        result.message = ex.what();
        return result;
    }
    print.set_plate_origin(Slic3r::to_3d(origin, 0.));
    print.set_plate_index(plate_index);

    //BBS: add cost info when drag in gcode
    // The densities and costs of the G-code's own configuration. Orca leaves
    // the print's filament length and weight empty, which the legend's totals
    // show; they are worked out from the same volumes.
    const auto& ps = gcode_result.print_statistics;
    Slic3r::PrintStatistics& print_statistics = print.print_statistics();
    double total_cost = 0.0;
    for (const auto& [extruder_id, volume] : ps.total_volumes_per_extruder) {
        if (extruder_id >= gcode_result.filament_densities.size()) {
            continue;
        }
        const double density = gcode_result.filament_densities.at(extruder_id);
        const double weight = volume * density * 0.001;
        if (extruder_id < gcode_result.filament_costs.size()) {
            const double cost = gcode_result.filament_costs.at(extruder_id);
            total_cost += weight * cost * 0.001;
        }
        print_statistics.total_weight += weight;
        if (extruder_id < gcode_result.filament_diameters.size() && gcode_result.filament_diameters[extruder_id] > 0.0f) {
            const double radius = 0.5 * gcode_result.filament_diameters[extruder_id];
            print_statistics.total_used_filament += volume / (PI * radius * radius);
        }
    }
    print_statistics.total_cost = total_cost;

    // Preview::load_print_as_fff(): the layers of the moves, as libvgcode
    // numbers them (Layers::update()); none means no valid G-code.
    const auto& moves = gcode_result.moves;
    result.layer_count = moves.size() > 1 ? std::int64_t(moves.back().layer_id) + 1 : 0;
    result.valid = result.layer_count > 0;
    using TimeMode = Slic3r::PrintEstimatedStatistics::ETimeMode;
    result.estimated_print_time_seconds = std::llround(ps.modes[static_cast<std::size_t>(TimeMode::Normal)].time);
    result.filament_micrometers = std::llround(print_statistics.total_used_filament * 1'000.0);
    result.total_cost = total_cost;
    std::vector<int> filaments;
    for (const auto& [extruder_id, volume] : ps.total_volumes_per_extruder) {
        filaments.push_back(int(extruder_id) + 1);
    }
    result.filaments = detail::filament_usage(gcode_result, filaments, gcode_result.filament_diameters.size());
    result.printer_settings = gcode_result.settings_ids.printer;
    result.print_settings = gcode_result.settings_ids.print;
    result.filament_settings = gcode_result.settings_ids.filament;

    // GLCanvas3D::load_gcode_preview() with the plate's filament colours; a
    // G-code of more filaments than the plate has takes its own for the others.
    Slic3r::DynamicPrintConfig tool_colors;
    std::vector<std::string> colors = config.option<Slic3r::ConfigOptionStrings>("filament_colour")->values;
    for (std::size_t index = colors.size(); index < gcode_result.extruder_colors.size(); ++index) {
        colors.push_back(gcode_result.extruder_colors[index]);
    }
    tool_colors.set_key_value("filament_colour", new Slic3r::ConfigOptionStrings(colors));
    result.toolpaths_written = result.valid && !toolpaths_path.empty() && detail::write_toolpaths(gcode_result, print, tool_colors, toolpaths_path);
    result.slice_info_written = result.valid && !slice_info_path.empty() && detail::write_slice_info(print, gcode_result, slice_info_path);
    result.status = SliceStatus::success;
    return result;
}

}  // namespace orcinus::orca
