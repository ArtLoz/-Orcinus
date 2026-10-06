#include "tab_print.hpp"

#include "settings_fields.hpp"

// TabPrint of the desktop app (Tab.cpp): the pages of the process tab, the
// fields it enables, and the corrections a change brings. The layout is the
// upstream build() with its statements in order, so an OrcaSlicer update can be
// followed line by line.

namespace orcinus::orca::detail {

using namespace Slic3r;

//BBS: BBS new parameter list
void TabPrint::build()
{
    if (m_presets == nullptr)
        m_presets = &m_preset_bundle->prints;
    load_initial_data();

    auto page = add_options_page("Quality", "custom-gcode_quality"); // ORCA: icon only visible on placeholders
        auto optgroup = page->new_optgroup("Layer height", "param_layer_height");
        optgroup->append_single_option_line("layer_height","quality_settings_layer_height");
        optgroup->append_single_option_line("initial_layer_print_height","quality_settings_layer_height");

        optgroup = page->new_optgroup("Line width", "param_line_width");
        optgroup->append_single_option_line("line_width","quality_settings_line_width");
        optgroup->append_single_option_line("initial_layer_line_width","quality_settings_line_width#first-layer");
        optgroup->append_single_option_line("outer_wall_line_width","quality_settings_line_width#outer-wall");
        optgroup->append_single_option_line("inner_wall_line_width","quality_settings_line_width#inner-wall");
        optgroup->append_single_option_line("top_surface_line_width","quality_settings_line_width#top-surface");
        optgroup->append_single_option_line("sparse_infill_line_width","quality_settings_line_width#sparse-infill");
        optgroup->append_single_option_line("internal_solid_infill_line_width","quality_settings_line_width#internal-solid-infill");
        optgroup->append_single_option_line("support_line_width","quality_settings_line_width#support");
        optgroup->append_single_option_line("bridge_line_width","quality_settings_line_width#bridge");

        optgroup = page->new_optgroup("Seam", "param_seam");
        optgroup->append_single_option_line("seam_position", "quality_settings_seam#seam-position");
        optgroup->append_single_option_line("staggered_inner_seams", "quality_settings_seam#staggered-inner-seams");
        optgroup->append_single_option_line("seam_gap","quality_settings_seam#seam-gap");
        optgroup->append_single_option_line("seam_slope_type", "quality_settings_seam#scarf-joint-seam");
        optgroup->append_single_option_line("seam_slope_conditional", "quality_settings_seam#scarf-joint-seam");
        optgroup->append_single_option_line("scarf_angle_threshold", "quality_settings_seam#scarf-joint-seam");
        optgroup->append_single_option_line("scarf_overhang_threshold", "quality_settings_seam#scarf-joint-seam");
        optgroup->append_single_option_line("scarf_joint_speed", "quality_settings_seam#scarf-joint-seam");
        optgroup->append_single_option_line("seam_slope_start_height", "quality_settings_seam#scarf-joint-seam");
        optgroup->append_single_option_line("seam_slope_entire_loop", "quality_settings_seam#scarf-joint-seam");
        optgroup->append_single_option_line("seam_slope_min_length", "quality_settings_seam#scarf-joint-seam");
        optgroup->append_single_option_line("seam_slope_steps", "quality_settings_seam#scarf-joint-seam");
        optgroup->append_single_option_line("scarf_joint_flow_ratio", "quality_settings_seam#scarf-joint-seam");
        optgroup->append_single_option_line("seam_slope_inner_walls", "quality_settings_seam#scarf-joint-seam");
        optgroup->append_single_option_line("role_based_wipe_speed","quality_settings_seam#role-based-wipe-speed");
        optgroup->append_single_option_line("wipe_speed", "quality_settings_seam#wipe-speed");
        optgroup->append_single_option_line("wipe_on_loops","quality_settings_seam#wipe-on-loop-inward-movement");
        optgroup->append_single_option_line("wipe_before_external_loop","quality_settings_seam#wipe-before-external");

        optgroup = page->new_optgroup("Precision", "param_precision");
        optgroup->append_single_option_line("slice_closing_radius", "quality_settings_precision#slice-gap-closing-radius");
        optgroup->append_single_option_line("resolution", "quality_settings_precision#resolution");
        optgroup->append_single_option_line("enable_arc_fitting", "quality_settings_precision#arc-fitting");
        optgroup->append_single_option_line("xy_hole_compensation", "quality_settings_precision#x-y-compensation");
        optgroup->append_single_option_line("xy_contour_compensation", "quality_settings_precision#x-y-compensation");
        optgroup->append_single_option_line("elefant_foot_compensation", "quality_settings_precision#elephant-foot-compensation");
        optgroup->append_single_option_line("elefant_foot_layers_density", "quality_settings_precision#elephant-foot-compensation-density");
        optgroup->append_single_option_line("elefant_foot_compensation_layers", "quality_settings_precision#elephant-foot-compensation");
        optgroup->append_single_option_line("precise_outer_wall", "quality_settings_precision#precise-wall");
        optgroup->append_single_option_line("precise_z_height", "quality_settings_precision#precise-z-height");
        optgroup->append_single_option_line("hole_to_polyhole", "quality_settings_precision#polyholes");
        optgroup->append_single_option_line("hole_to_polyhole_threshold", "quality_settings_precision#polyholes");
        optgroup->append_single_option_line("hole_to_polyhole_twisted", "quality_settings_precision#polyholes");

        optgroup = page->new_optgroup("Ironing", "param_ironing");
        optgroup->append_single_option_line("ironing_type", "quality_settings_ironing#type");
        optgroup->append_single_option_line("ironing_pattern", "quality_settings_ironing#pattern");
        optgroup->append_single_option_line("ironing_flow", "quality_settings_ironing#flow");
        optgroup->append_single_option_line("ironing_spacing", "quality_settings_ironing#line-spacing");
        optgroup->append_single_option_line("ironing_inset", "quality_settings_ironing#inset");
        optgroup->append_single_option_line("ironing_angle", "quality_settings_ironing#angle-offset");
        optgroup->append_single_option_line("ironing_angle_fixed", "quality_settings_ironing#fixed-angle");

        optgroup = page->new_optgroup("Z contouring", "param_advanced");
        optgroup->append_single_option_line("zaa_enabled", "quality_settings_z_contouring");
        optgroup->append_single_option_line("zaa_minimize_perimeter_height", "quality_settings_z_contouring#minimize-wall-height-angle");
        optgroup->append_single_option_line("zaa_min_z", "quality_settings_z_contouring#minimum-z-height");
        optgroup->append_single_option_line("zaa_dont_alternate_fill_direction", "quality_settings_z_contouring#dont-alternate-fill-direction");
        // Orca: it's not used yet, so hide it in UI for now
        // optgroup->append_single_option_line("ironing_expansion");

        optgroup = page->new_optgroup("Wall generator", "param_wall_generator");
        optgroup->append_single_option_line("wall_generator", "quality_settings_wall_generator");
        optgroup->append_single_option_line("wall_transition_angle", "quality_settings_wall_generator#wall-transitioning-threshhold-angle");
        optgroup->append_single_option_line("wall_transition_filter_deviation", "quality_settings_wall_generator#wall-transitioning-filter-margin");
        optgroup->append_single_option_line("wall_transition_length", "quality_settings_wall_generator#wall-transitioning-length");
        optgroup->append_single_option_line("wall_distribution_count", "quality_settings_wall_generator#wall-distribution-count");
        optgroup->append_single_option_line("initial_layer_min_bead_width", "quality_settings_wall_generator#first-layer-minimum-wall-width");
        optgroup->append_single_option_line("min_bead_width", "quality_settings_wall_generator#minimum-wall-width");
        optgroup->append_single_option_line("min_feature_size", "quality_settings_wall_generator#minimum-feature-size");
        optgroup->append_single_option_line("min_length_factor", "quality_settings_wall_generator#minimum-wall-length");
        optgroup->append_single_option_line("wall_maximum_resolution", "quality_settings_wall_generator#maximum-wall-resolution");
        optgroup->append_single_option_line("wall_maximum_deviation", "quality_settings_wall_generator#maximum-wall-deviation");

        optgroup = page->new_optgroup("Walls and surfaces", "param_wall_surface");
        optgroup->append_single_option_line("wall_sequence", "quality_settings_wall_and_surfaces#walls-printing-order");
        optgroup->append_single_option_line("is_infill_first", "quality_settings_wall_and_surfaces#print-infill-first");
        optgroup->append_single_option_line("wall_direction", "quality_settings_wall_and_surfaces#wall-loop-direction");
        optgroup->append_single_option_line("print_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("top_solid_infill_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("bottom_solid_infill_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("set_other_flow_ratios", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("first_layer_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("outer_wall_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("inner_wall_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("overhang_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("sparse_infill_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("internal_solid_infill_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("gap_fill_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("support_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("support_interface_flow_ratio", "quality_settings_wall_and_surfaces#surface-flow-ratio");
        optgroup->append_single_option_line("only_one_wall_first_layer", "quality_settings_wall_and_surfaces#only-one-wall");
        optgroup->append_single_option_line("only_one_wall_top", "quality_settings_wall_and_surfaces#only-one-wall");
        optgroup->append_single_option_line("min_width_top_surface", "quality_settings_wall_and_surfaces#threshold");
        optgroup->append_single_option_line("reduce_crossing_wall", "quality_settings_wall_and_surfaces#avoid-crossing-walls");
        optgroup->append_single_option_line("max_travel_detour_distance", "quality_settings_wall_and_surfaces#max-detour-length");

        optgroup->append_single_option_line("small_area_infill_flow_compensation", "quality_settings_wall_and_surfaces#small-area-flow-compensation");
        Option option = optgroup->get_option("small_area_infill_flow_compensation_model");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = 15;
        optgroup->append_single_option_line(option, "quality_settings_wall_and_surfaces#small-area-flow-compensation");

        optgroup = page->new_optgroup("Bridging", "param_bridge");
        optgroup->append_single_option_line("bridge_flow", "quality_settings_bridging#flow-ratio");
        optgroup->append_single_option_line("internal_bridge_flow", "quality_settings_bridging#flow-ratio");
        optgroup->append_single_option_line("bridge_density", "quality_settings_bridging#bridge-density");
        optgroup->append_single_option_line("internal_bridge_density", "quality_settings_bridging#bridge-density");
        optgroup->append_single_option_line("thick_bridges", "quality_settings_bridging#thick-bridges");
        optgroup->append_single_option_line("thick_internal_bridges", "quality_settings_bridging#thick-bridges");
        optgroup->append_single_option_line("enable_extra_bridge_layer", "quality_settings_bridging#extra-bridge-layers");
        optgroup->append_single_option_line("dont_filter_internal_bridges", "quality_settings_bridging#filter-out-small-internal-bridges");
        optgroup->append_single_option_line("counterbore_hole_bridging", "quality_settings_bridging#bridge-counterbore-hole");

        optgroup = page->new_optgroup("Overhangs", "param_overhang");
        optgroup->append_single_option_line("detect_overhang_wall", "quality_settings_overhangs#detect-overhang-wall");
        optgroup->append_single_option_line("make_overhang_printable", "quality_settings_overhangs#make-overhang-printable");
        optgroup->append_single_option_line("make_overhang_printable_angle", "quality_settings_overhangs#maximum-angle");
        optgroup->append_single_option_line("make_overhang_printable_hole_size", "quality_settings_overhangs#hole-area");
        optgroup->append_single_option_line("extra_perimeters_on_overhangs", "quality_settings_overhangs#extra-perimeters-on-overhangs");
        optgroup->append_single_option_line("overhang_reverse", "quality_settings_overhangs#reverse-on-even");
        optgroup->append_single_option_line("overhang_reverse_internal_only", "quality_settings_overhangs#reverse-internal-only");
        optgroup->append_single_option_line("overhang_reverse_threshold", "quality_settings_overhangs#reverse-threshold");

    page = add_options_page("Strength", "custom-gcode_strength"); // ORCA: icon only visible on placeholders
        optgroup = page->new_optgroup("Walls", "param_wall");
        optgroup->append_single_option_line("wall_loops", "strength_settings_walls#wall-loops");
        optgroup->append_single_option_line("alternate_extra_wall", "strength_settings_walls#alternate-extra-wall");
        optgroup->append_single_option_line("detect_thin_wall", "strength_settings_walls#detect-thin-wall");

        optgroup = page->new_optgroup("Top/bottom shells", "param_shell");

        optgroup->append_single_option_line("top_shell_layers", "strength_settings_top_bottom_shells#shell-layers");
        optgroup->append_single_option_line("top_shell_thickness", "strength_settings_top_bottom_shells#shell-thickness");
        optgroup->append_single_option_line("top_surface_density", "strength_settings_top_bottom_shells#surface-density");
        optgroup->append_single_option_line("top_surface_pattern", "strength_settings_top_bottom_shells#surface-pattern");
        optgroup->append_single_option_line("bottom_shell_layers", "strength_settings_top_bottom_shells#shell-layers");
        optgroup->append_single_option_line("bottom_shell_thickness", "strength_settings_top_bottom_shells#shell-thickness");
        optgroup->append_single_option_line("bottom_surface_density", "strength_settings_top_bottom_shells#surface-density");
        optgroup->append_single_option_line("bottom_surface_pattern", "strength_settings_top_bottom_shells#surface-pattern");
        optgroup->append_single_option_line("top_bottom_infill_wall_overlap", "strength_settings_top_bottom_shells#infillwall-overlap");

        optgroup = page->new_optgroup("Infill", "param_infill");
        optgroup->append_single_option_line("sparse_infill_density", "strength_settings_infill#sparse-infill-density");
        optgroup->append_single_option_line("fill_multiline", "strength_settings_infill#fill-multiline");
        optgroup->append_single_option_line("sparse_infill_pattern", "strength_settings_infill#sparse-infill-pattern");
        optgroup->append_single_option_line("gyroid_optimized", "strength_settings_patterns#gyroid-optimized");
        optgroup->append_single_option_line("infill_direction", "strength_settings_infill#direction");
        optgroup->append_single_option_line("sparse_infill_rotate_template", "strength_settings_infill_rotation_template_metalanguage");
        optgroup->append_single_option_line("skin_infill_density", "strength_settings_patterns#locked-zag");
        optgroup->append_single_option_line("skeleton_infill_density", "strength_settings_patterns#locked-zag");
        optgroup->append_single_option_line("infill_lock_depth", "strength_settings_patterns#locked-zag");
        optgroup->append_single_option_line("skin_infill_depth", "strength_settings_patterns#locked-zag");
        optgroup->append_single_option_line("skin_infill_line_width", "strength_settings_patterns#locked-zag");
        optgroup->append_single_option_line("skeleton_infill_line_width", "strength_settings_patterns#locked-zag");
        optgroup->append_single_option_line("symmetric_infill_y_axis", "strength_settings_infill#symmetric-infill-y-axis");
        optgroup->append_single_option_line("infill_shift_step", "strength_settings_patterns#cross-hatch");
        optgroup->append_single_option_line("lateral_lattice_angle_1", "strength_settings_patterns#lateral-lattice");
        optgroup->append_single_option_line("lateral_lattice_angle_2", "strength_settings_patterns#lateral-lattice");
        optgroup->append_single_option_line("infill_overhang_angle", "strength_settings_patterns#lateral-honeycomb");
        optgroup->append_single_option_line("lightning_overhang_angle", "strength_settings_patterns#lightning");
        optgroup->append_single_option_line("lightning_prune_angle", "strength_settings_patterns#lightning");
        optgroup->append_single_option_line("lightning_straightening_angle", "strength_settings_patterns#lightning");
        optgroup->append_single_option_line("infill_anchor_max", "strength_settings_infill#anchor");
        optgroup->append_single_option_line("infill_anchor", "strength_settings_infill#anchor");
        optgroup->append_single_option_line("internal_solid_infill_pattern", "strength_settings_infill#internal-solid-infill");
        optgroup->append_single_option_line("solid_infill_direction", "strength_settings_infill#direction");
        optgroup->append_single_option_line("solid_infill_rotate_template", "strength_settings_infill_rotation_template_metalanguage");
        optgroup->append_single_option_line("gap_fill_target", "strength_settings_infill#apply-gap-fill");
        optgroup->append_single_option_line("filter_out_gap_fill", "strength_settings_infill#filter-out-tiny-gaps");
        optgroup->append_single_option_line("infill_wall_overlap", "strength_settings_infill#infill-wall-overlap");

        optgroup = page->new_optgroup("Advanced", "param_advanced");
        optgroup->append_single_option_line("align_infill_direction_to_model", "strength_settings_advanced#align-infill-direction-to-model");
        optgroup->append_single_option_line("extra_solid_infills", "strength_settings_infill#extra-solid-infill");
        optgroup->append_single_option_line("bridge_angle", "strength_settings_advanced#bridge-infill-direction");
        optgroup->append_single_option_line("internal_bridge_angle", "strength_settings_advanced#bridge-infill-direction"); // ORCA: Internal bridge angle override
        optgroup->append_single_option_line("relative_bridge_angle", "strength_settings_advanced#relative-bridge-angle");
        optgroup->append_single_option_line("minimum_sparse_infill_area", "strength_settings_advanced#minimum-sparse-infill-threshold");
        optgroup->append_single_option_line("infill_combination", "strength_settings_advanced#infill-combination");
        optgroup->append_single_option_line("infill_combination_max_layer_height", "strength_settings_advanced#max-layer-height");
        optgroup->append_single_option_line("detect_narrow_internal_solid_infill", "strength_settings_advanced#detect-narrow-internal-solid-infill");
        optgroup->append_single_option_line("ensure_vertical_shell_thickness", "strength_settings_advanced#ensure-vertical-shell-thickness");

    page = add_options_page("Speed", "custom-gcode_speed"); // ORCA: icon only visible on placeholders
        optgroup = page->new_optgroup("First layer speed", "param_speed_first", 15);
        optgroup->append_single_option_line("initial_layer_speed", "speed_settings_initial_layer_speed#initial-layer");
        optgroup->append_single_option_line("initial_layer_infill_speed", "speed_settings_initial_layer_speed#initial-layer-infill");
        optgroup->append_single_option_line("initial_layer_travel_speed", "speed_settings_initial_layer_speed#initial-layer-travel-speed");
        optgroup->append_single_option_line("slow_down_layers", "speed_settings_initial_layer_speed#number-of-slow-layers");
        optgroup = page->new_optgroup("Other layers speed", "param_speed", 15);
        optgroup->append_single_option_line("outer_wall_speed", "speed_settings_other_layers_speed#outer-wall");
        optgroup->append_single_option_line("inner_wall_speed", "speed_settings_other_layers_speed#inner-wall");
        optgroup->append_single_option_line("small_perimeter_speed", "speed_settings_other_layers_speed#small-perimeters");
        optgroup->append_single_option_line("small_perimeter_threshold", "speed_settings_other_layers_speed#small-perimeters-threshold");
        optgroup->append_single_option_line("sparse_infill_speed", "speed_settings_other_layers_speed#sparse-infill");
        optgroup->append_single_option_line("internal_solid_infill_speed", "speed_settings_other_layers_speed#internal-solid-infill");
        optgroup->append_single_option_line("top_surface_speed", "speed_settings_other_layers_speed#top-surface");
        optgroup->append_single_option_line("gap_infill_speed", "speed_settings_other_layers_speed#gap-infill");
        optgroup->append_single_option_line("ironing_speed", "speed_settings_other_layers_speed#ironing-speed");
        optgroup->append_single_option_line("support_speed", "speed_settings_other_layers_speed#support");
        optgroup->append_single_option_line("support_interface_speed", "speed_settings_other_layers_speed#support-interface");
        optgroup = page->new_optgroup("Overhang speed", "param_overhang_speed", 15);
        optgroup->append_single_option_line("enable_overhang_speed", "speed_settings_overhang_speed#slow-down-for-overhang");

        optgroup->append_single_option_line("slowdown_for_curled_perimeters", "speed_settings_overhang_speed#slow-down-for-curled-perimeters");
        Line line = { "Overhang speed", "This is the speed for various overhang degrees. Overhang degrees are expressed as a percentage of line width. 0 speed means no slowing down for the overhang degree range and wall speed is used" };
        line.label_path = "speed_settings_overhang_speed#speed";
        line.append_option(optgroup->get_option("overhang_1_4_speed"));
        line.append_option(optgroup->get_option("overhang_2_4_speed"));
        line.append_option(optgroup->get_option("overhang_3_4_speed"));
        line.append_option(optgroup->get_option("overhang_4_4_speed"));
        optgroup->append_line(line);
        optgroup->append_separator();
        line = { "Bridge", "Set speed for external and internal bridges" };
        line.append_option(optgroup->get_option("bridge_speed"));
        line.append_option(optgroup->get_option("internal_bridge_speed"));
        optgroup->append_line(line);

        optgroup = page->new_optgroup("Travel speed", "param_travel_speed", 15);
        optgroup->append_single_option_line("travel_speed", "speed_settings_travel");

        optgroup = page->new_optgroup("Acceleration", "param_acceleration", 15);
        optgroup->append_single_option_line("default_acceleration", "speed_settings_acceleration#normal-printing");
        optgroup->append_single_option_line("outer_wall_acceleration", "speed_settings_acceleration#outer-wall");
        optgroup->append_single_option_line("inner_wall_acceleration", "speed_settings_acceleration#inner-wall");
        optgroup->append_single_option_line("bridge_acceleration", "speed_settings_acceleration#bridge");
        optgroup->append_single_option_line("sparse_infill_acceleration", "speed_settings_acceleration#sparse-infill");
        optgroup->append_single_option_line("internal_solid_infill_acceleration", "speed_settings_acceleration#internal-solid-infill");
        optgroup->append_single_option_line("initial_layer_acceleration", "speed_settings_acceleration#initial-layer");
        optgroup->append_single_option_line("initial_layer_travel_acceleration", "speed_settings_acceleration#initial-layer-travel");
        optgroup->append_single_option_line("top_surface_acceleration", "speed_settings_acceleration#top-surface");
        optgroup->append_single_option_line("travel_acceleration", "speed_settings_acceleration#travel");
        optgroup->append_single_option_line("accel_to_decel_enable", "speed_settings_acceleration");
        optgroup->append_single_option_line("accel_to_decel_factor", "speed_settings_acceleration");

        optgroup = page->new_optgroup("Junction Deviation", "param_junction_deviation", 15);
        optgroup->append_single_option_line("default_junction_deviation", "speed_settings_jerk_xy#junction-deviation");

        optgroup = page->new_optgroup("Jerk(XY)", "param_jerk", 15);
        optgroup->append_single_option_line("default_jerk", "speed_settings_jerk_xy#default");
        optgroup->append_single_option_line("outer_wall_jerk", "speed_settings_jerk_xy#outer-wall");
        optgroup->append_single_option_line("inner_wall_jerk", "speed_settings_jerk_xy#inner-wall");
        optgroup->append_single_option_line("infill_jerk", "speed_settings_jerk_xy#infill");
        optgroup->append_single_option_line("top_surface_jerk", "speed_settings_jerk_xy#top-surface");
        optgroup->append_single_option_line("initial_layer_jerk", "speed_settings_jerk_xy#initial-layer");
        optgroup->append_single_option_line("initial_layer_travel_jerk", "speed_settings_jerk_xy#initial-layer-travel");
        optgroup->append_single_option_line("travel_jerk", "speed_settings_jerk_xy#travel");

        optgroup = page->new_optgroup("Advanced", "param_advanced", 15);
        optgroup->append_single_option_line("max_volumetric_extrusion_rate_slope", "speed_settings_advanced");
        optgroup->append_single_option_line("max_volumetric_extrusion_rate_slope_segment_length", "speed_settings_advanced");
        optgroup->append_single_option_line("extrusion_rate_smoothing_external_perimeter_only", "speed_settings_advanced");

    page = add_options_page("Support", "custom-gcode_support"); // ORCA: icon only visible on placeholders
        optgroup = page->new_optgroup("Support", "param_support");
        optgroup->append_single_option_line("enable_support", "support_settings_support");
        optgroup->append_single_option_line("support_type", "support_settings_support#type");
        optgroup->append_single_option_line("support_style", "support_settings_support#style");
        optgroup->append_single_option_line("support_threshold_angle", "support_settings_support#threshold-angle");
        optgroup->append_single_option_line("support_threshold_overlap", "support_settings_support#threshold-overlap");
        optgroup->append_single_option_line("raft_first_layer_density", "support_settings_support#initial-layer-density");
        optgroup->append_single_option_line("raft_first_layer_expansion", "support_settings_support#initial-layer-expansion");
        optgroup->append_single_option_line("support_on_build_plate_only", "support_settings_support#on-build-plate-only");
        optgroup->append_single_option_line("support_critical_regions_only", "support_settings_support#support-critical-regions-only");
        optgroup->append_single_option_line("support_remove_small_overhang", "support_settings_support#ignore-small-overhangs");
        //optgroup->append_single_option_line("enforce_support_layers", "support_settings_support");

        optgroup = page->new_optgroup("Raft", "param_raft");
        optgroup->append_single_option_line("raft_layers", "support_settings_raft");
        optgroup->append_single_option_line("raft_contact_distance", "support_settings_raft");

        optgroup = page->new_optgroup("Support filament", "param_support_filament");
        optgroup->append_single_option_line("support_filament", "support_settings_filament#base");
        optgroup->append_single_option_line("support_interface_filament", "support_settings_filament#interface");
        optgroup->append_single_option_line("support_interface_not_for_body", "support_settings_filament#avoid-interface-filament-for-base");

        optgroup = page->new_optgroup("Support ironing", "param_ironing");
        optgroup->append_single_option_line("support_ironing", "support_settings_ironing");
        optgroup->append_single_option_line("support_ironing_pattern", "support_settings_ironing#pattern");
        optgroup->append_single_option_line("support_ironing_flow", "support_settings_ironing#flow");
        optgroup->append_single_option_line("support_ironing_spacing", "support_settings_ironing#line-spacing");

        //optgroup = page->new_optgroup(L("Options for support material and raft"));

        // Support
        optgroup = page->new_optgroup("Advanced", "param_advanced");
        optgroup->append_single_option_line("support_top_z_distance", "support_settings_advanced#z-distance");
        optgroup->append_single_option_line("support_bottom_z_distance", "support_settings_advanced#z-distance");
        optgroup->append_single_option_line("tree_support_wall_count", "support_settings_advanced#support-wall-loops");
        optgroup->append_single_option_line("support_base_pattern", "support_settings_advanced#base-pattern");
        optgroup->append_single_option_line("support_base_pattern_spacing", "support_settings_advanced#base-pattern-spacing");
        optgroup->append_single_option_line("support_angle", "support_settings_advanced#pattern-angle");
        optgroup->append_single_option_line("support_interface_top_layers", "support_settings_advanced#interface-layers");
        optgroup->append_single_option_line("support_interface_bottom_layers", "support_settings_advanced#interface-layers");
        optgroup->append_single_option_line("support_interface_pattern", "support_settings_advanced#interface-pattern");
        optgroup->append_single_option_line("support_interface_spacing", "support_settings_advanced#interface-spacing");
        optgroup->append_single_option_line("support_bottom_interface_spacing", "support_settings_advanced#interface-spacing");
        optgroup->append_single_option_line("support_expansion", "support_settings_advanced#normal-support-expansion");
        //optgroup->append_single_option_line("support_interface_loop_pattern", "support_settings_advanced");

        optgroup->append_single_option_line("support_object_xy_distance", "support_settings_advanced#supportobject-xy-distance");
        optgroup->append_single_option_line("support_object_first_layer_gap", "support_settings_advanced#supportobject-first-layer-gap");
        optgroup->append_single_option_line("bridge_no_support", "support_settings_advanced#dont-support-bridges");
        optgroup->append_single_option_line("max_bridge_length", "support_settings_advanced");
        optgroup->append_single_option_line("independent_support_layer_height", "support_settings_advanced#independent-support-layer-height");

        optgroup = page->new_optgroup("Tree supports", "param_support_tree");
        optgroup->append_single_option_line("tree_support_tip_diameter", "support_settings_tree#tip-diameter");
        optgroup->append_single_option_line("tree_support_branch_distance", "support_settings_tree#branch-distance");
        optgroup->append_single_option_line("tree_support_branch_distance_organic", "support_settings_tree#branch-distance");
        optgroup->append_single_option_line("tree_support_top_rate", "support_settings_tree#branch-density");
        optgroup->append_single_option_line("tree_support_branch_diameter", "support_settings_tree#branch-diameter");
        optgroup->append_single_option_line("tree_support_branch_diameter_organic", "support_settings_tree#branch-diameter");
        optgroup->append_single_option_line("tree_support_branch_diameter_angle", "support_settings_tree#branch-diameter-angle");
        optgroup->append_single_option_line("tree_support_branch_angle", "support_settings_tree#branch-angle");
        optgroup->append_single_option_line("tree_support_branch_angle_organic", "support_settings_tree#branch-angle");
        optgroup->append_single_option_line("tree_support_angle_slow", "support_settings_tree#preferred-branch-angle");
        optgroup->append_single_option_line("tree_support_auto_brim", "support_settings_tree");
        optgroup->append_single_option_line("tree_support_brim_width", "support_settings_tree");

    page = add_options_page("Multimaterial", "custom-gcode_multi_material"); // ORCA: icon only visible on placeholders
        optgroup = page->new_optgroup("Prime tower", "param_tower");
        optgroup->append_single_option_line("enable_prime_tower", "multimaterial_settings_prime_tower");
        optgroup->append_single_option_line("prime_tower_skip_points", "multimaterial_settings_prime_tower");
        optgroup->append_single_option_line("enable_tower_interface_features", "multimaterial_settings_prime_tower");
        optgroup->append_single_option_line("enable_tower_interface_cooldown_during_tower", "multimaterial_settings_prime_tower");
        optgroup->append_single_option_line("prime_tower_enable_framework", "multimaterial_settings_prime_tower");
        optgroup->append_single_option_line("prime_tower_width", "multimaterial_settings_prime_tower#width");
        optgroup->append_single_option_line("prime_volume", "multimaterial_settings_prime_tower");
        optgroup->append_single_option_line("prime_tower_brim_width", "multimaterial_settings_prime_tower#brim-width");
        optgroup->append_single_option_line("prime_tower_infill_gap", "multimaterial_settings_prime_tower");
        optgroup->append_single_option_line("wipe_tower_rotation_angle", "multimaterial_settings_prime_tower#wipe-tower-rotation-angle");
        optgroup->append_single_option_line("wipe_tower_bridging", "multimaterial_settings_prime_tower#maximal-bridging-distance");
        optgroup->append_single_option_line("wipe_tower_extra_spacing", "multimaterial_settings_prime_tower#wipe-tower-purge-lines-spacing");
        optgroup->append_single_option_line("wipe_tower_extra_flow", "multimaterial_settings_prime_tower#extra-flow-for-purge");
        optgroup->append_single_option_line("wipe_tower_max_purge_speed", "multimaterial_settings_prime_tower#maximum-wipe-tower-print-speed");
        optgroup->append_single_option_line("wipe_tower_wall_type", "multimaterial_settings_prime_tower#wall-type");
        optgroup->append_single_option_line("wipe_tower_cone_angle", "multimaterial_settings_prime_tower#stabilization-cone-apex-angle");
        optgroup->append_single_option_line("wipe_tower_extra_rib_length", "multimaterial_settings_prime_tower#extra-rib-length");
        optgroup->append_single_option_line("wipe_tower_rib_width", "multimaterial_settings_prime_tower#rib-width");
        optgroup->append_single_option_line("wipe_tower_fillet_wall", "multimaterial_settings_prime_tower#fillet-wall");
        optgroup->append_single_option_line("wipe_tower_no_sparse_layers", "multimaterial_settings_prime_tower#no-sparse-layers");
        optgroup->append_single_option_line("single_extruder_multi_material_priming", "multimaterial_settings_prime_tower");

        optgroup = page->new_optgroup("Filament for Features", "param_filament_for_features");
        optgroup->append_single_option_line("outer_wall_filament_id", "multimaterial_settings_filament_for_features#outer-walls");
        optgroup->append_single_option_line("inner_wall_filament_id", "multimaterial_settings_filament_for_features#inner-walls");
        optgroup->append_single_option_line("sparse_infill_filament_id", "multimaterial_settings_filament_for_features#sparse-infill");
        optgroup->append_single_option_line("internal_solid_filament_id", "multimaterial_settings_filament_for_features#internal-solid-infill");
        optgroup->append_single_option_line("top_surface_filament_id", "multimaterial_settings_filament_for_features#top-surface");
        optgroup->append_single_option_line("bottom_surface_filament_id", "multimaterial_settings_filament_for_features#bottom-surface");
        optgroup->append_single_option_line("wipe_tower_filament", "multimaterial_settings_filament_for_features#wipe-tower");

        optgroup = page->new_optgroup("Ooze prevention", "param_ooze_prevention");
        optgroup->append_single_option_line("ooze_prevention", "multimaterial_settings_ooze_prevention");
        optgroup->append_single_option_line("standby_temperature_delta", "multimaterial_settings_ooze_prevention#temperature-variation");
        optgroup->append_single_option_line("preheat_time", "multimaterial_settings_ooze_prevention#preheat-time");
        optgroup->append_single_option_line("preheat_steps", "multimaterial_settings_ooze_prevention#preheat-steps");

        optgroup = page->new_optgroup("Flush options", "param_flush");
        optgroup->append_single_option_line("flush_into_infill", "multimaterial_settings_flush_options#flush-into-objects-infill");
        optgroup->append_single_option_line("flush_into_objects", "multimaterial_settings_flush_options");
        optgroup->append_single_option_line("flush_into_support", "multimaterial_settings_flush_options#flush-into-objects-support");
        optgroup = page->new_optgroup("Advanced", "advanced");
        optgroup->append_single_option_line("interlocking_beam", "multimaterial_settings_advanced#interlocking-beam");
        optgroup->append_single_option_line("interface_shells", "multimaterial_settings_advanced#interface-shells");
        optgroup->append_single_option_line("mmu_segmented_region_max_width", "multimaterial_settings_advanced#maximum-width-of-segmented-region");
        optgroup->append_single_option_line("mmu_segmented_region_interlocking_depth", "multimaterial_settings_advanced#interlocking-depth-of-segmented-region");
        optgroup->append_single_option_line("interlocking_beam_width", "multimaterial_settings_advanced#interlocking-beam-width");
        optgroup->append_single_option_line("interlocking_orientation", "multimaterial_settings_advanced#interlocking-direction");
        optgroup->append_single_option_line("interlocking_beam_layer_count", "multimaterial_settings_advanced#interlocking-beam-layers");
        optgroup->append_single_option_line("interlocking_depth", "multimaterial_settings_advanced#interlocking-depth");
        optgroup->append_single_option_line("interlocking_boundary_avoidance", "multimaterial_settings_advanced#interlocking-boundary-avoidance");

    page = add_options_page("Others", "custom-gcode_other"); // ORCA: icon only visible on placeholders
        optgroup = page->new_optgroup("Skirt", "param_skirt");
        optgroup->append_single_option_line("skirt_loops", "others_settings_skirt#loops");
        optgroup->append_single_option_line("skirt_type", "others_settings_skirt#type");
        optgroup->append_single_option_line("min_skirt_length", "others_settings_skirt#minimum-extrusion-length");
        optgroup->append_single_option_line("skirt_distance", "others_settings_skirt#distance");
        optgroup->append_single_option_line("skirt_start_angle", "others_settings_skirt#start-point");
        optgroup->append_single_option_line("skirt_speed", "others_settings_skirt#speed");
        optgroup->append_single_option_line("skirt_height", "others_settings_skirt#height");
        optgroup->append_single_option_line("draft_shield", "others_settings_skirt#shield");
        optgroup->append_single_option_line("single_loop_draft_shield", "others_settings_skirt#single-loop-after-first-layer");

        optgroup = page->new_optgroup("Brim", "param_adhension");
        optgroup->append_single_option_line("brim_type", "others_settings_brim#type");
        optgroup->append_single_option_line("brim_width", "others_settings_brim#width");
        optgroup->append_single_option_line("brim_object_gap", "others_settings_brim#brim-object-gap");
        optgroup->append_single_option_line("brim_flow_ratio", "others_settings_brim#brim-flow-ratio");
        optgroup->append_single_option_line("brim_use_efc_outline", "others_settings_brim#brim-use-efc-outline");
        optgroup->append_single_option_line("combine_brims", "others_settings_brim#combine-brims");
        optgroup->append_single_option_line("brim_ears_max_angle", "others_settings_brim#ear-max-angle");
        optgroup->append_single_option_line("brim_ears_detection_length", "others_settings_brim#ear-detection-radius");

        optgroup = page->new_optgroup("Special mode", "param_special");
        optgroup->append_single_option_line("slicing_mode", "others_settings_special_mode#slicing-mode");
        optgroup->append_single_option_line("print_sequence", "others_settings_special_mode#print-sequence");
        optgroup->append_single_option_line("print_order", "others_settings_special_mode#intra-layer-order");
        optgroup->append_single_option_line("spiral_mode", "others_settings_special_mode#spiral-vase");
        optgroup->append_single_option_line("spiral_mode_smooth", "others_settings_special_mode#smooth-spiral");
        optgroup->append_single_option_line("spiral_mode_max_xy_smoothing", "others_settings_special_mode#max-xy-smoothing");
        optgroup->append_single_option_line("spiral_starting_flow_ratio", "others_settings_special_mode#spiral-starting-flow-ratio");
        optgroup->append_single_option_line("spiral_finishing_flow_ratio", "others_settings_special_mode#spiral-finishing-flow-ratio");

        optgroup->append_single_option_line("timelapse_type", "others_settings_special_mode#timelapse");
        optgroup->append_single_option_line("enable_wrapping_detection");

        optgroup = page->new_optgroup("Fuzzy Skin", "fuzzy_skin");
        optgroup->append_single_option_line("fuzzy_skin", "others_settings_fuzzy_skin");
        optgroup->append_single_option_line("fuzzy_skin_mode", "others_settings_fuzzy_skin#fuzzy-skin-mode");
        optgroup->append_single_option_line("fuzzy_skin_noise_type", "others_settings_fuzzy_skin#noise-type");
        optgroup->append_single_option_line("fuzzy_skin_point_distance", "others_settings_fuzzy_skin#point-distance");
        optgroup->append_single_option_line("fuzzy_skin_thickness", "others_settings_fuzzy_skin#skin-thickness");
        optgroup->append_single_option_line("fuzzy_skin_scale", "others_settings_fuzzy_skin#skin-feature-size");
        optgroup->append_single_option_line("fuzzy_skin_octaves", "others_settings_fuzzy_skin#skin-noise-octaves");
        optgroup->append_single_option_line("fuzzy_skin_persistence", "others_settings_fuzzy_skin#skin-noise-persistence");
        optgroup->append_single_option_line("fuzzy_skin_ripples_per_layer", "others_settings_fuzzy_skin#ripples-per-layer");
        optgroup->append_single_option_line("fuzzy_skin_ripple_offset", "others_settings_fuzzy_skin#ripple-offset");
        optgroup->append_single_option_line("fuzzy_skin_layers_between_ripple_offset", "others_settings_fuzzy_skin#layers-between-ripple-offset");
        optgroup->append_single_option_line("fuzzy_skin_first_layer", "others_settings_fuzzy_skin#apply-fuzzy-skin-to-first-layer");

        optgroup = page->new_optgroup("G-code output", "param_gcode");
        optgroup->append_single_option_line("reduce_infill_retraction", "others_settings_g_code_output#reduce-infill-retraction");
        optgroup->append_single_option_line("gcode_add_line_number", "others_settings_g_code_output#add-line-number");
        optgroup->append_single_option_line("gcode_comments", "others_settings_g_code_output#verbose-g-code");
        optgroup->append_single_option_line("gcode_label_objects", "others_settings_g_code_output#label-objects");
        optgroup->append_single_option_line("exclude_object", "others_settings_g_code_output#exclude-objects");
        option = optgroup->get_option("filename_format");
        // option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.multiline = true;
        // option.opt.height = 5;
        optgroup->append_single_option_line(option, "others_settings_g_code_output#filename-format");

        optgroup = page->new_optgroup("Change extrusion role G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("process_change_extrusion_role_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = 15;
        optgroup->append_single_option_line(option);

        optgroup = page->new_optgroup("Post-processing Scripts", "param_gcode", 0);
        option = optgroup->get_option("post_process");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = 15;
        optgroup->append_single_option_line(option, "others_settings_post_processing_scripts");

        optgroup = page->new_optgroup("Notes", "note", 0);
        option = optgroup->get_option("notes");
        option.opt.full_width = true;
        option.opt.height = 25;//250;
        optgroup->append_single_option_line(option, "others_settings_notes");

    // Orca: hide the dependencies tab for process for now. The UI is not ready yet.
}

// Reload current config (aka presets->edited_preset->config) into the UI fields.
void TabPrint::reload_config()
{
    this->compatible_widget_reload("compatible_printers_condition", "compatible_printers");
    Tab::reload_config();
}

void TabPrint::toggle_options()
{
    if (m_active_page == nullptr) return;
    // BBS: whether the preset is Bambu Lab printer
    if (m_preset_bundle != nullptr) {
        const bool is_BBL_printer = m_preset_bundle->is_bbl_vendor();
        m_config_manipulation.set_is_BBL_Printer(is_BBL_printer);
    }

    m_config_manipulation.toggle_print_fff_options(m_config, m_type < Preset::TYPE_COUNT);

    const auto support_type = m_config->opt_enum<SupportType>("support_type");
    if (const std::string field = m_active_page->get_field("support_style"); !field.empty()) {
        const std::vector<int> enum_set_normal = {smsDefault, smsGrid, smsSnug};
        const std::vector<int> enum_set_tree = {smsDefault, smsTreeSlim, smsTreeStrong, smsTreeHybrid, smsTreeOrganic};
        set_choices(field, "support_style", is_tree(support_type) ? enum_set_tree : enum_set_normal);
    }

    // BBL printers do not support cone wipe tower
    if (const std::string field = m_active_page->get_field("wipe_tower_wall_type"); !field.empty()) {
        const std::vector<int> enum_set_bbl = {wtwRectangle, wtwRib};
        const std::vector<int> enum_set_none_bbl = {wtwRectangle, wtwCone, wtwRib};
        set_choices(field, "wipe_tower_wall_type", m_config_manipulation.get_is_BBL_Printer() ? enum_set_bbl : enum_set_none_bbl);
    }

    // ParamsPanel::set_active_tab(): the process tab hides the surface flow
    // ratio, which only the settings of an object show.
    if (m_type == Preset::TYPE_PRINT) {
        toggle_line("print_flow_ratio", false);
    }
}

void TabPrint::update()
{
    if (m_preset_bundle->printers.get_selected_preset().printer_technology() == ptSLA)
        return; // ys_FIXME

    m_update_cnt++;

    // ysFIXME: It's temporary workaround and should be clewer reworked:
    // Note: This workaround works till "enable_support" and "overhangs" is exclusive sets of mutually no-exclusive parameters.
    // But it should be corrected when we will have more such sets.
    // Disable check of the compatibility of the "enable_support" and "overhangs" options for saved user profile
    // NOTE: Initialization of the support_material_overhangs_queried value have to be processed just ones
    if (!m_config_manipulation.is_initialized_support_material_overhangs_queried())
    {
        const Preset& selected_preset = m_preset_bundle->prints.get_selected_preset();
        const bool is_user_and_saved_preset = !selected_preset.is_system && !selected_preset.is_dirty;
        const bool support_material_overhangs_queried = m_config->opt_bool("enable_support") && !m_config->opt_bool("detect_overhang_wall");
        m_config_manipulation.initialize_support_material_overhangs_queried(is_user_and_saved_preset && support_material_overhangs_queried);
    }

    m_config_manipulation.update_print_fff_config(m_config, m_type < Preset::TYPE_COUNT, m_type == Preset::TYPE_PLATE);

    m_update_cnt--;

    if (m_update_cnt == 0) {
        if (m_active_page != nullptr && m_active_page->title() != "Dependencies")
            toggle_options();
        // MainFrame::on_config_changed(): the plater takes the settings.
        m_plater_config.apply(*m_config);
    }
}

}  // namespace orcinus::orca::detail
