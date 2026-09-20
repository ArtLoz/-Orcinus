#include "config_manipulation.hpp"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <set>

#include <boost/filesystem.hpp>
#include <boost/log/trivial.hpp>
#include <boost/nowide/fstream.hpp>
#include <nlohmann/json.hpp>

#include "libslic3r/MaterialType.hpp"
#include "libslic3r/Utils.hpp"

namespace orcinus::orca::detail {

namespace {

using namespace Slic3r;

// wxString::Format() of a text the desktop app does not translate.
UiText untranslated(const char* format, const double value)
{
    const int length = std::snprintf(nullptr, 0, format, value);
    std::string text(size_t(std::max(length, 0)), '\0');
    std::snprintf(text.data(), text.size() + 1, format, value);
    return ui_text(std::move(text));
}

const UiText paragraph = ui_text("\n\n");

}  // namespace

ConfigManipulation::ConfigManipulation(ConfigManipulationState& state, PresetBundle& bundle, const DynamicPrintConfig& plater_config,
                                       SettingsDialogs& dialogs, std::function<void()> load_config,
                                       std::function<void(const std::string&, bool, int)> cb_toggle_field,
                                       std::function<void(const std::string&, bool, int)> cb_toggle_line)
    : m_state(state),
      m_bundle(bundle),
      m_plater_config(plater_config),
      m_dialogs(dialogs),
      load_config(std::move(load_config)),
      cb_toggle_field(std::move(cb_toggle_field)),
      cb_toggle_line(std::move(cb_toggle_line))
{
}

void ConfigManipulation::apply(DynamicPrintConfig* config, DynamicPrintConfig* new_config)
{
    bool modified = false;
    m_applying_keys = config->diff(*new_config);
    for (const auto& opt_key : m_applying_keys) {
        config->set_key_value(opt_key, new_config->option(opt_key)->clone());
        modified = true;
    }
    if (modified && load_config != nullptr)
        load_config();
    m_applying_keys.clear();
}

bool ConfigManipulation::is_applying() const { return is_msg_dlg_already_exist; }

const Slic3r::t_config_option_keys& ConfigManipulation::applying_keys() const
{
    return m_applying_keys;
}

void ConfigManipulation::apply_null_fff_config(DynamicPrintConfig* config, const std::vector<std::string>& keys,
                                               const std::vector<DynamicPrintConfig>& configs)
{
    for (const std::string& k : keys) {
        if (k == "independent_support_layer_height" || k == "enable_support" || k == "detect_thin_wall")
            config->set_key_value(k, new ConfigOptionBool(true));
        else if (k == "wall_loops")
            config->set_key_value(k, new ConfigOptionInt(0));
        else if (k == "top_shell_layers" || k == "enforce_support_layers")
            config->set_key_value(k, new ConfigOptionInt(1));
        else if (k == "sparse_infill_density") {
            double v = config->option<ConfigOptionPercent>(k)->value;
            for (const DynamicPrintConfig& c : configs) {
                const auto* o = c.option<ConfigOptionPercent>(k);
                if (o && o->value > v) v = o->value;
            }
            config->set_key_value(k, new ConfigOptionPercent(v)); // sparse_infill_pattern
        }
        else if (k == "detect_overhang_wall")
            config->set_key_value(k, new ConfigOptionBool(false));
        else if (k == "sparse_infill_pattern")
            config->set_key_value(k, new ConfigOptionEnum<InfillPattern>(ipGrid));
    }
}

void ConfigManipulation::toggle_field(const std::string& opt_key, const bool toggle, const int opt_index)
{
    cb_toggle_field(opt_key, toggle, opt_index);
}

void ConfigManipulation::toggle_line(const std::string& opt_key, const bool toggle, const int opt_index)
{
    if (cb_toggle_line) {
        cb_toggle_line(opt_key, toggle, opt_index);
    }
}

void ConfigManipulation::initialize_support_material_overhangs_queried(const bool queried)
{
    m_state.is_initialized_support_material_overhangs_queried = true;
    m_state.support_material_overhangs_queried = queried;
}

void ConfigManipulation::update_print_fff_config(DynamicPrintConfig* config, const bool is_global_config, const bool is_plate_config)
{
    // #ys_FIXME_to_delete
    //! Temporary workaround for the correct updates of the TextCtrl (like "layer_height"):
    // KillFocus() for the wxSpinCtrl use CallAfter function. So,
    // to except the duplicate call of the update() after dialog->ShowModal(),
    // let check if this process is already started.
    if (is_msg_dlg_already_exist)
        return;

    bool is_object_config = (!is_global_config && !is_plate_config);

    // layer_height shouldn't be equal to zero
    auto layer_height = config->opt_float("layer_height");
    auto gpreset = m_bundle.printers.get_edited_preset();
    if (layer_height < EPSILON)
    {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist = true;
        m_dialogs.inform("too_small_layer_height", {ui_text("Too small layer height.\nReset to 0.2.")});
        new_conf.set_key_value("layer_height", new ConfigOptionFloat(0.2));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }

    //BBS: limite the max layer_herght
    auto max_lh = gpreset.config.opt_float("max_layer_height",0);
    if (max_lh > 0.2 && layer_height > max_lh+ EPSILON)
    {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist = true;
        m_dialogs.inform("too_large_layer_height", {untranslated("Too large layer height.\nReset to %0.3f.", max_lh)});
        new_conf.set_key_value("layer_height", new ConfigOptionFloat(max_lh));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }

    //BBS: ironing_spacing shouldn't be too small or equal to zero
    if (config->opt_float("ironing_spacing") < 0.05)
    {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist = true;
        m_dialogs.inform("too_small_ironing_spacing", {ui_text("Too small ironing spacing.\nReset to 0.1.")});
        new_conf.set_key_value("ironing_spacing", new ConfigOptionFloat(0.1));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }
    if (config->opt_float("support_ironing_spacing") < 0.05)
    {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist = true;
        m_dialogs.inform("too_small_support_ironing_spacing", {ui_text("Too small ironing spacing.\nReset to 0.1.")});
        new_conf.set_key_value("support_ironing_spacing", new ConfigOptionFloat(0.1));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }

    if (config->option<ConfigOptionFloat>("initial_layer_print_height")->value < EPSILON)
    {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist = true;
        m_dialogs.inform("zero_initial_layer_height", {ui_text("Zero initial layer height is invalid.\n\nThe first layer height will be reset to 0.2.")});
        new_conf.set_key_value("initial_layer_print_height", new ConfigOptionFloat(0.2));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }

    if (std::abs(config->option<ConfigOptionFloat>("xy_hole_compensation")->value) > 2)
    {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist = true;
        m_dialogs.inform("xy_hole_compensation", {ui_text("This setting is only used for model size tunning with small value in some cases.\n"
                                                          "For example, when model size has small error and hard to be assembled.\n"
                                                          "For large size tuning, please use model scale function.\n\n"
                                                          "The value will be reset to 0.")});
        new_conf.set_key_value("xy_hole_compensation", new ConfigOptionFloat(0));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }

    if (std::abs(config->option<ConfigOptionFloat>("xy_contour_compensation")->value) > 2)
    {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist = true;
        m_dialogs.inform("xy_contour_compensation", {ui_text("This setting is only used for model size tunning with small value in some cases.\n"
                                                             "For example, when model size has small error and hard to be assembled.\n"
                                                             "For large size tuning, please use model scale function.\n\n"
                                                             "The value will be reset to 0.")});
        new_conf.set_key_value("xy_contour_compensation", new ConfigOptionFloat(0));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }

    if (config->option<ConfigOptionFloat>("elefant_foot_compensation")->value > 1)
    {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist = true;
        m_dialogs.inform("elefant_foot_compensation", {ui_text("Too large elephant foot compensation is unreasonable.\n"
                                                               "If really have serious elephant foot effect, please check other settings.\n"
                                                               "For example, whether bed temperature is too high.\n\n"
                                                               "The value will be reset to 0.")});
        new_conf.set_key_value("elefant_foot_compensation", new ConfigOptionFloat(0));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }

    if (config->option<ConfigOptionBool>("enable_wrapping_detection")->value) {
        std::string printer_type = m_bundle.printers.get_edited_preset().get_printer_type(&m_bundle);
        if (!support_wrapping_detection(printer_type)) {
            DynamicPrintConfig new_conf = *config;
            new_conf.set_key_value("enable_wrapping_detection", new ConfigOptionBool(false));
            apply(config, &new_conf);
        }
    }

    double sparse_infill_density = config->option<ConfigOptionPercent>("sparse_infill_density")->value;
    auto timelapse_type = config->opt_enum<TimelapseType>("timelapse_type");

    if (!is_plate_config &&
        config->opt_bool("spiral_mode") &&
        ! (config->opt_int("wall_loops") == 1 &&
           config->opt_int("top_shell_layers") == 0 &&
           sparse_infill_density == 0 &&
           ! config->opt_bool("enable_support") &&
           config->opt_int("enforce_support_layers") == 0 &&
           ! config->opt_bool("detect_thin_wall") &&
           ! config->opt_bool("overhang_reverse") &&
            config->opt_enum<TimelapseType>("timelapse_type") == TimelapseType::tlTraditional &&
            !config->opt_bool("enable_wrapping_detection")))
    {
        DynamicPrintConfig new_conf = *config;
        if (show_spiral_mode_settings_dialog(is_object_config)) {
            new_conf.set_key_value("wall_loops", new ConfigOptionInt(1));
            new_conf.set_key_value("top_shell_layers", new ConfigOptionInt(0));
            new_conf.set_key_value("sparse_infill_density", new ConfigOptionPercent(0));
            new_conf.set_key_value("enable_support", new ConfigOptionBool(false));
            new_conf.set_key_value("enforce_support_layers", new ConfigOptionInt(0));
            new_conf.set_key_value("detect_thin_wall", new ConfigOptionBool(false));
            new_conf.set_key_value("overhang_reverse", new ConfigOptionBool(false));
            new_conf.set_key_value("timelapse_type", new ConfigOptionEnum<TimelapseType>(tlTraditional));
            new_conf.set_key_value("enable_wrapping_detection", new ConfigOptionBool(false));
            sparse_infill_density = 0;
            timelapse_type = TimelapseType::tlTraditional;
        }
        else {
            new_conf.set_key_value("spiral_mode", new ConfigOptionBool(false));
        }
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }

    if (config->opt_bool("alternate_extra_wall") &&
        (config->opt_enum<EnsureVerticalShellThickness>("ensure_vertical_shell_thickness") == evstAll)) {
        std::vector<UiText> msg_text = {ui_text("Alternate extra wall does't work well when ensure vertical shell thickness is set to All.")};

        bool answer_yes = false;
        if (is_global_config) {
            msg_text.push_back(paragraph);
            msg_text.push_back(ui_text("Change these settings automatically?\n"
                                       "Yes - Change ensure vertical shell thickness to Moderate and enable alternate extra wall\n"
                                       "No  - Don't use alternate extra wall"));
            answer_yes = m_dialogs.ask("alternate_extra_wall", std::move(msg_text));
        } else {
            m_dialogs.inform("alternate_extra_wall", std::move(msg_text));
        }
        DynamicPrintConfig new_conf = *config;
        if (!is_global_config || answer_yes) {
            new_conf.set_key_value("ensure_vertical_shell_thickness", new ConfigOptionEnum<EnsureVerticalShellThickness>(evstModerate));
            new_conf.set_key_value("alternate_extra_wall", new ConfigOptionBool(true));
        }
        else {
            new_conf.set_key_value("ensure_vertical_shell_thickness", new ConfigOptionEnum<EnsureVerticalShellThickness>(evstAll));
            new_conf.set_key_value("alternate_extra_wall", new ConfigOptionBool(false));
        }
        apply(config, &new_conf);
    }

    // BBS
    int filament_cnt = m_bundle.filament_presets.size();

    // BBL printers do not support cone wipe tower
    if (config->opt_bool("enable_prime_tower") && m_state.is_BBL_Printer) {
        auto wipe_tower_wall_type = config->opt_enum<WipeTowerWallType>("wipe_tower_wall_type");
        if (wipe_tower_wall_type == WipeTowerWallType::wtwCone) {
            DynamicPrintConfig new_conf = *config;
            new_conf.set_key_value("wipe_tower_wall_type", new ConfigOptionEnum<WipeTowerWallType>(WipeTowerWallType::wtwRectangle));
            apply(config, &new_conf);
        }
    }

    // Check "enable_support" and "overhangs" relations only on global settings level
    if (is_global_config && config->opt_bool("enable_support")) {
        // Ask only once.
        if (!m_state.support_material_overhangs_queried) {
            m_state.support_material_overhangs_queried = true;
            if (!config->opt_bool("detect_overhang_wall")/* != 1*/) {
                //BBS: detect_overhang_wall is setting in develop mode. Enable it directly.
                DynamicPrintConfig new_conf = *config;
                new_conf.set_key_value("detect_overhang_wall", new ConfigOptionBool(true));
                apply(config, &new_conf);
            }
        }
    }
    else {
        m_state.support_material_overhangs_queried = false;
    }

    if (config->opt_bool("enable_support")) {
        auto   support_type = config->opt_enum<SupportType>("support_type");
        auto   support_style = config->opt_enum<SupportMaterialStyle>("support_style");
        std::set<int> enum_set_normal = { smsDefault, smsGrid, smsSnug };
        std::set<int> enum_set_tree   = { smsDefault, smsTreeSlim, smsTreeStrong, smsTreeHybrid, smsTreeOrganic };
        auto &           set             = is_tree(support_type) ? enum_set_tree : enum_set_normal;
        if (set.find(support_style) == set.end()) {
            DynamicPrintConfig new_conf = *config;
            new_conf.set_key_value("support_style", new ConfigOptionEnum<SupportMaterialStyle>(smsDefault));
            apply(config, &new_conf);
        }
    }

    // BBS
    static const char* keys[] = { "support_filament", "support_interface_filament"};
    for (int i = 0; i < sizeof(keys) / sizeof(keys[0]); i++) {
        std::string key = std::string(keys[i]);
        auto* opt = dynamic_cast<ConfigOptionInt*>(config->option(key, false));
        if (opt != nullptr) {
            if (opt->getInt() > filament_cnt) {
                DynamicPrintConfig new_conf = *config;
                const DynamicPrintConfig *conf_temp = &m_plater_config;
                int new_value = 0;
                if (conf_temp != nullptr && conf_temp->has(key)) {
                    new_value = conf_temp->opt_int(key);
                }
                new_conf.set_key_value(key, new ConfigOptionInt(new_value));
                apply(config, &new_conf);
            }
        }
    }

    if (config->opt_enum<SeamScarfType>("seam_slope_type") != SeamScarfType::None &&
        config->get_abs_value("seam_slope_start_height") >= layer_height) {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist    = true;
        m_dialogs.inform("seam_slope_start_height", {ui_text("seam_slope_start_height need to be smaller than layer_height.\nReset to 0.")});
        new_conf.set_key_value("seam_slope_start_height", new ConfigOptionFloatOrPercent(0, false));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }

    // layer_height shouldn't be equal to zero
    float skin_depth = config->opt_float("skin_infill_depth");
    if (config->opt_float("infill_lock_depth") > skin_depth) {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist    = true;
        // xgettext:no-c-format, no-boost-format
        m_dialogs.inform("infill_lock_depth", {ui_text("Lock depth should smaller than skin depth.\nReset to 50% of skin depth.")});
        new_conf.set_key_value("infill_lock_depth", new ConfigOptionFloat(skin_depth / 2));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }

    bool have_arachne = config->opt_enum<PerimeterGeneratorType>("wall_generator") == PerimeterGeneratorType::Arachne;
    if (config->opt_enum<FuzzySkinMode>("fuzzy_skin_mode") != FuzzySkinMode::Displacement && !have_arachne) {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist = true;
        const bool answer_yes = m_dialogs.ask(
            "fuzzy_skin_mode",
            {ui_text("Both [Extrusion] and [Combined] modes of Fuzzy Skin require the Arachne Wall Generator to be enabled."), paragraph,
             ui_text("Change these settings automatically?\n"
                     "Yes - Enable Arachne Wall Generator\n"
                     "No  - Disable Arachne Wall Generator and set [Displacement] mode of the Fuzzy Skin")});
        if (answer_yes)
            new_conf.set_key_value("wall_generator", new ConfigOptionEnum<PerimeterGeneratorType>(PerimeterGeneratorType::Arachne));
        else
            new_conf.set_key_value("fuzzy_skin_mode", new ConfigOptionEnum<FuzzySkinMode>(FuzzySkinMode::Displacement));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }
}

void ConfigManipulation::toggle_print_fff_options(DynamicPrintConfig *config, const bool is_global_config)
{
    PresetBundle *preset_bundle  = &m_bundle;

    const GCodeFlavor gcflavor = preset_bundle->printers.get_edited_preset().config.option<ConfigOptionEnum<GCodeFlavor>>("gcode_flavor")->value;

    // Orca: use booleans to avoid repeated comparisons with enum values
    const bool gcf_is_marlin_firmware = gcflavor == GCodeFlavor::gcfMarlinFirmware;
    const bool gcf_is_klipper = gcflavor == GCodeFlavor::gcfKlipper;

    bool have_volumetric_extrusion_rate_slope = config->option<ConfigOptionFloat>("max_volumetric_extrusion_rate_slope")->value > 0;
    float have_volumetric_extrusion_rate_slope_segment_length = config->option<ConfigOptionFloat>("max_volumetric_extrusion_rate_slope_segment_length")->value;
    toggle_field("enable_arc_fitting", !have_volumetric_extrusion_rate_slope);
    toggle_line("max_volumetric_extrusion_rate_slope_segment_length", have_volumetric_extrusion_rate_slope);
    toggle_line("extrusion_rate_smoothing_external_perimeter_only", have_volumetric_extrusion_rate_slope);
    if(have_volumetric_extrusion_rate_slope) config->set_key_value("enable_arc_fitting", new ConfigOptionBool(false));
    if(have_volumetric_extrusion_rate_slope_segment_length < 0.5) {
        DynamicPrintConfig new_conf = *config;
        new_conf.set_key_value("max_volumetric_extrusion_rate_slope_segment_length", new ConfigOptionFloat(1));
        apply(config, &new_conf);
    }

    bool have_perimeters = config->opt_int("wall_loops") > 0;
    for (auto el : { "extra_perimeters_on_overhangs", "ensure_vertical_shell_thickness", "detect_thin_wall", "detect_overhang_wall",
        "seam_position", "staggered_inner_seams", "wall_sequence", "outer_wall_line_width",
        "inner_wall_speed", "outer_wall_speed", "small_perimeter_speed", "small_perimeter_threshold" })
        toggle_field(el, have_perimeters);

    bool have_infill = config->option<ConfigOptionPercent>("sparse_infill_density")->value > 0;
    // sparse_infill_filament_id uses the same logic as in Print::extruders()
    for (auto el : { "sparse_infill_pattern", "infill_combination", "fill_multiline","infill_direction",
        "minimum_sparse_infill_area", "sparse_infill_filament_id", "infill_anchor", "infill_anchor_max","infill_shift_step","sparse_infill_rotate_template","symmetric_infill_y_axis"})
        toggle_line(el, have_infill);

    bool have_combined_infill = config->opt_bool("infill_combination") && have_infill;
    toggle_line("infill_combination_max_layer_height", have_combined_infill);

    // Infill patterns that support multiline infill.
    InfillPattern pattern = config->opt_enum<InfillPattern>("sparse_infill_pattern");
    bool          have_multiline_infill_pattern = pattern == ipGyroid || pattern == ipGrid || pattern == ipRectilinear || pattern == ipTpmsD || pattern == ipTpmsFK || pattern == ipCrossHatch || pattern == ipHoneycomb || pattern == ipLateralLattice || pattern == ipLateralHoneycomb || pattern == ipConcentric ||
                                                  pattern == ipCubic || pattern == ipStars || pattern == ipAlignedRectilinear || pattern == ipLightning || pattern == ip3DHoneycomb || pattern == ipAdaptiveCubic || pattern == ipSupportCubic|| pattern == ipTriangles || pattern == ipQuarterCubic|| pattern == ipArchimedeanChords || pattern == ipHilbertCurve || pattern == ipOctagramSpiral;

    // gyroid_optimized only applies when the sparse infill pattern is gyroid;
    // hide the whole line otherwise.
    toggle_line("gyroid_optimized", have_infill && pattern == ipGyroid);

    // If there is infill, enable/disable fill_multiline according to whether the pattern supports multiline infill.
    if (have_infill) {
        toggle_field("fill_multiline", have_multiline_infill_pattern);
        // If the infill pattern does not support multiline fill_multiline is changed to 1.
        // Necessary when the pattern contains params.multiline (for example, triangles because they belong to the rectilinear class)
        if (!have_multiline_infill_pattern) {
            DynamicPrintConfig new_conf = *config;
            new_conf.set_key_value("fill_multiline", new ConfigOptionInt(1));
            apply(config, &new_conf);
        }
        // Hide infill anchor max if sparse_infill_pattern is not line or if sparse_infill_pattern is line but infill_anchor_max is 0.
        bool infill_anchor = config->opt_enum<InfillPattern>("sparse_infill_pattern") != ipLine;
        toggle_field("infill_anchor_max", infill_anchor);

        // Only allow configuration of open anchors if the anchoring is enabled.
        bool has_infill_anchors = infill_anchor && config->option<ConfigOptionFloatOrPercent>("infill_anchor_max")->value > 0;
        toggle_field("infill_anchor", has_infill_anchors);
    }

    //cross zag
    bool is_cross_zag = config->option<ConfigOptionEnum<InfillPattern>>("sparse_infill_pattern")->value == InfillPattern::ipCrossZag;
    bool is_locked_zig = config->option<ConfigOptionEnum<InfillPattern>>("sparse_infill_pattern")->value == InfillPattern::ipLockedZag;

    toggle_line("infill_shift_step", is_cross_zag || is_locked_zig);

    for (auto el : { "skeleton_infill_density", "skin_infill_density", "infill_lock_depth", "skin_infill_depth","skin_infill_line_width", "skeleton_infill_line_width" })
        toggle_line(el, is_locked_zig);

    bool is_zig_zag = config->option<ConfigOptionEnum<InfillPattern>>("sparse_infill_pattern")->value == InfillPattern::ipZigZag;

    toggle_line("symmetric_infill_y_axis", is_zig_zag || is_cross_zag || is_locked_zig);

    bool has_spiral_vase         = config->opt_bool("spiral_mode");
    toggle_line("spiral_mode_smooth", has_spiral_vase);
    toggle_line("spiral_mode_max_xy_smoothing", has_spiral_vase && config->opt_bool("spiral_mode_smooth"));
    toggle_line("spiral_starting_flow_ratio", has_spiral_vase);
    toggle_line("spiral_finishing_flow_ratio", has_spiral_vase);
    bool has_top_shell    = config->opt_int("top_shell_layers") > 0 || (has_spiral_vase && config->opt_int("bottom_shell_layers") > 1);
    bool has_bottom_shell = config->opt_int("bottom_shell_layers") > 0;
    bool has_solid_infill = has_top_shell || has_bottom_shell;
    toggle_field("top_surface_pattern", has_top_shell);
    toggle_field("bottom_surface_pattern", has_bottom_shell);
    toggle_field("top_surface_density", has_top_shell);
    toggle_field("bottom_surface_density", has_bottom_shell);

    for (auto el : { "infill_direction", "sparse_infill_line_width", "gap_fill_target","filter_out_gap_fill","infill_wall_overlap",
        "sparse_infill_speed", "bridge_speed", "internal_bridge_speed", "bridge_angle", "internal_bridge_angle", "relative_bridge_angle",
        "solid_infill_direction", "solid_infill_rotate_template", "internal_solid_infill_pattern", "internal_solid_filament_id", "top_surface_filament_id", "bottom_surface_filament_id",
        })
        toggle_field(el, have_infill || has_solid_infill);

    toggle_field("top_shell_thickness", ! has_spiral_vase && has_top_shell);
    toggle_field("bottom_shell_thickness", ! has_spiral_vase && has_bottom_shell);

    // Gap fill is newly allowed in between perimeter lines even for empty infill (see GH #1476).
    toggle_field("gap_infill_speed", have_perimeters);

    for (auto el : { "top_surface_line_width", "top_surface_speed" })
        toggle_field(el, has_top_shell);

    bool have_default_acceleration = config->opt_float("default_acceleration") > 0;

    for (auto el : {"outer_wall_acceleration", "inner_wall_acceleration", "initial_layer_acceleration", "initial_layer_travel_acceleration",
        "top_surface_acceleration", "travel_acceleration", "bridge_acceleration", "sparse_infill_acceleration", "internal_solid_infill_acceleration"})
        toggle_field(el, have_default_acceleration);

    const bool junction_deviation_enabled =
        gcf_is_marlin_firmware &&
        [&]() {
            const auto *machine_jd = preset_bundle->printers.get_edited_preset().config.option<ConfigOptionFloats>("machine_max_junction_deviation");
            return machine_jd && !machine_jd->values.empty() && machine_jd->values.front() > 0.0;
        }();

    toggle_line("default_junction_deviation", gcf_is_marlin_firmware);
    toggle_field("default_junction_deviation", junction_deviation_enabled);
    toggle_field("default_jerk", !junction_deviation_enabled);

    const std::initializer_list<const char*> jerk_options = {
        "outer_wall_jerk", "inner_wall_jerk", "initial_layer_jerk",
        "initial_layer_travel_jerk", "top_surface_jerk", "travel_jerk", "infill_jerk"
    };

    if (junction_deviation_enabled) {
        for (auto el : jerk_options)
            toggle_line(el, false);
    } else {
        const bool have_default_jerk = config->has("default_jerk") && config->opt_float("default_jerk") > 0;
        for (auto el : jerk_options) {
            toggle_line(el, true);
            toggle_field(el, have_default_jerk);
        }
    }

    bool have_skirt = config->opt_int("skirt_loops") > 0;
    toggle_field("skirt_height", have_skirt && config->opt_enum<DraftShield>("draft_shield") != dsEnabled);
    toggle_line("single_loop_draft_shield", have_skirt); // ORCA: Display one wall if skirt enabled
    for (auto el : {"skirt_type", "min_skirt_length", "skirt_distance", "skirt_start_angle", "skirt_speed", "draft_shield"})
        toggle_field(el, have_skirt);

    bool have_brim = (config->opt_enum<BrimType>("brim_type") != btNoBrim);
    toggle_field("brim_object_gap", have_brim);
    toggle_field("brim_use_efc_outline", have_brim);
    toggle_field("combine_brims", have_brim);
    bool have_brim_width = (config->opt_enum<BrimType>("brim_type") != btNoBrim) && config->opt_enum<BrimType>("brim_type") != btAutoBrim &&
                           config->opt_enum<BrimType>("brim_type") != btPainted;
    toggle_field("brim_width", have_brim_width);
    toggle_field("brim_flow_ratio", have_brim);
    // Wall filament selectors use the same logic as in Print::extruders().
    toggle_field("outer_wall_filament_id", have_perimeters || have_brim);
    toggle_field("inner_wall_filament_id", have_perimeters || have_brim);

    bool have_brim_ear = (config->opt_enum<BrimType>("brim_type") == btEar);
    const auto brim_width = config->opt_float("brim_width");
    // disable brim_ears_max_angle and brim_ears_detection_length if brim_width is 0
    toggle_field("brim_ears_max_angle", brim_width > 0.0f);
    toggle_field("brim_ears_detection_length", brim_width > 0.0f);
    // hide brim_ears_max_angle and brim_ears_detection_length if brim_ear is not selected
    toggle_line("brim_ears_max_angle", have_brim_ear);
    toggle_line("brim_ears_detection_length", have_brim_ear);

    // Hide Elephant foot compensation layers if elefant_foot_compensation is not enabled
    toggle_line("elefant_foot_compensation_layers", config->opt_float("elefant_foot_compensation") > 0 || config->option<ConfigOptionPercent>("elefant_foot_layers_density")->get_abs_value(1.0f) < 1.0f);

    bool have_raft = config->opt_int("raft_layers") > 0;
    bool have_support_material = config->opt_bool("enable_support") || have_raft;

    SupportType support_type = config->opt_enum<SupportType>("support_type");
    bool have_support_interface = config->opt_int("support_interface_top_layers") > 0 || config->opt_int("support_interface_bottom_layers") > 0;
    bool have_support_soluble = have_support_material && config->opt_float("support_top_z_distance") == 0;
    auto support_style = config->opt_enum<SupportMaterialStyle>("support_style");
    for (auto el : { "support_style", "support_base_pattern",
        "support_base_pattern_spacing", "support_expansion", "support_angle",
        "support_interface_pattern", "support_interface_top_layers", "support_interface_bottom_layers",
        "bridge_no_support", "max_bridge_length", "support_top_z_distance", "support_bottom_z_distance",
        "support_type", "support_on_build_plate_only", "support_critical_regions_only", "support_interface_not_for_body",
        "support_object_xy_distance", "support_object_first_layer_gap", "independent_support_layer_height"})
        toggle_field(el, have_support_material);
    toggle_field("support_threshold_angle", have_support_material && is_auto(support_type));
    toggle_field("support_threshold_overlap", config->opt_int("support_threshold_angle") == 0 && have_support_material && is_auto(support_type));
    //toggle_field("support_closing_radius", have_support_material && support_style == smsSnug);

    bool support_is_tree = config->opt_bool("enable_support") && is_tree(support_type);
    bool support_is_organic = support_is_tree && (support_style == smsTreeOrganic || support_style == smsDefault);
    bool support_is_normal_tree = support_is_tree && !support_is_organic;

    // hide settings that are not used by tree supports
    toggle_line("support_threshold_overlap", !support_is_tree); // ORCA: tree supports do not use Threshold Overlap
    // settings specific to normal trees
    for (auto el : {"tree_support_branch_angle", "tree_support_branch_distance", "tree_support_branch_diameter", "tree_support_auto_brim", "tree_support_brim_width"})
        toggle_line(el, support_is_normal_tree);
    // settings specific to organic trees
    for (auto el : {"tree_support_branch_angle_organic", "tree_support_branch_distance_organic", "tree_support_branch_diameter_organic", "tree_support_angle_slow", "tree_support_tip_diameter", "tree_support_top_rate", "tree_support_branch_diameter_angle"})
        toggle_line(el, support_is_organic);
    // ORCA: Independent support layer height is not compatible with organic tree supports,
    // as they rely on the support layers being the same as the object layers to determine where to place branches.
    toggle_line("independent_support_layer_height", have_support_material && !support_is_organic);

    toggle_field("tree_support_brim_width", support_is_tree && !config->opt_bool("tree_support_auto_brim"));
    // tree support use max_bridge_length instead of bridge_no_support
    toggle_line("max_bridge_length", support_is_tree);
    toggle_line("bridge_no_support", !support_is_tree);
    toggle_line("support_critical_regions_only", is_auto(support_type) && support_is_tree);

    for (auto el : { "support_interface_filament",
        "support_interface_loop_pattern", "support_bottom_interface_spacing" })
        toggle_field(el, have_support_material && have_support_interface);

    bool can_ironing_support = have_raft || (have_support_material && config->opt_int("support_interface_top_layers") > 0);
    toggle_field("support_ironing", can_ironing_support);
    bool has_support_ironing = can_ironing_support && config->opt_bool("support_ironing");
    for (auto el : {"support_ironing_pattern", "support_ironing_flow", "support_ironing_spacing" })
        toggle_line(el, has_support_ironing);
    // Orca: Force solid support interface when using support ironing
    toggle_field("support_interface_spacing", have_support_material && have_support_interface && !has_support_ironing);

//    see issue #10915
//    bool have_skirt_height = have_skirt &&
//    (config->opt_int("skirt_height") > 1 || config->opt_enum<DraftShield>("draft_shield") != dsEnabled);
//    toggle_line("support_speed", have_support_material || have_skirt_height);
//    toggle_line("support_interface_speed", have_support_material && have_support_interface);

    // BBS
    //toggle_field("support_material_synchronize_layers", have_support_soluble);

    toggle_field("inner_wall_line_width", have_perimeters || have_skirt || have_brim);
    toggle_field("support_filament", have_support_material || have_skirt);

    toggle_line("raft_contact_distance", have_raft && !have_support_soluble);

    // Orca: First-layer density is available for supports broadly.
    toggle_field("raft_first_layer_density", have_support_material);
    // Orca: For regular tree (Slim/Strong) without raft, hide first-layer expansion.
    // Keep it enabled for non-tree supports, organic tree, hybrid tree, and any raft case.
    toggle_field("raft_first_layer_expansion",
                 have_support_material && ((!support_is_normal_tree || support_style == smsTreeHybrid) || have_raft));

    bool has_ironing = (config->opt_enum<IroningType>("ironing_type") != IroningType::NoIroning);
    for (auto el : { "ironing_pattern", "ironing_flow", "ironing_spacing", "ironing_angle", "ironing_inset", "ironing_angle_fixed" })
        toggle_line(el, has_ironing);
    bool has_rectilinear_ironing = (config->opt_enum<InfillPattern>("ironing_pattern") == InfillPattern::ipRectilinear);
    for (auto el : {"ironing_angle", "ironing_angle_fixed"})
        toggle_field(el, has_ironing && has_rectilinear_ironing);

    toggle_line("ironing_speed", has_ironing || has_support_ironing);

    bool has_zaa = config->opt_bool("zaa_enabled");
    for (auto el : {"zaa_minimize_perimeter_height", "zaa_min_z", "zaa_dont_alternate_fill_direction", "ironing_expansion"})
        toggle_line(el, has_zaa);

    bool have_sequential_printing = (config->opt_enum<PrintSequence>("print_sequence") == PrintSequence::ByObject);
    // for (auto el : { "extruder_clearance_radius", "extruder_clearance_height_to_rod", "extruder_clearance_height_to_lid" })
    //     toggle_field(el, have_sequential_printing);
    toggle_field("print_order", !have_sequential_printing);

    toggle_field("single_extruder_multi_material", !m_state.is_BBL_Printer);

    auto bSEMM = preset_bundle->printers.get_edited_preset().config.opt_bool("single_extruder_multi_material");
    const bool supports_wipe_tower_2 = !m_state.is_BBL_Printer && preset_bundle->printers.get_edited_preset().config.opt_enum<WipeTowerType>("wipe_tower_type") == WipeTowerType::Type2;

    toggle_field("ooze_prevention", !bSEMM);
    bool have_ooze_prevention = config->opt_bool("ooze_prevention");
    toggle_line("standby_temperature_delta", have_ooze_prevention);
    toggle_line("preheat_time", have_ooze_prevention);
    int preheat_steps = config->opt_int("preheat_steps");
    toggle_line("preheat_steps", have_ooze_prevention && (preheat_steps > 0));

    bool have_prime_tower = config->opt_bool("enable_prime_tower");
    for (auto el : {"prime_tower_width", "prime_tower_brim_width", "prime_tower_skip_points", "wipe_tower_wall_type", "prime_tower_infill_gap","prime_tower_enable_framework", "enable_tower_interface_features"})
        toggle_line(el, have_prime_tower);

    toggle_line("enable_tower_interface_cooldown_during_tower",
                have_prime_tower && config->opt_bool("enable_tower_interface_features"));

    bool purge_in_primetower = preset_bundle->printers.get_edited_preset().config.opt_bool("purge_in_prime_tower");

    for (auto el : {"wipe_tower_rotation_angle", "wipe_tower_cone_angle",
                    "wipe_tower_extra_spacing", "wipe_tower_max_purge_speed",
                    "wipe_tower_bridging", "wipe_tower_extra_flow",
                    "wipe_tower_no_sparse_layers"})
            toggle_line(el, have_prime_tower && supports_wipe_tower_2);

    WipeTowerWallType wipe_tower_wall_type = config->opt_enum<WipeTowerWallType>("wipe_tower_wall_type");
    bool have_rib_wall = (wipe_tower_wall_type == WipeTowerWallType::wtwRib)&&have_prime_tower;
    toggle_line("wipe_tower_cone_angle", have_prime_tower && supports_wipe_tower_2 && wipe_tower_wall_type == WipeTowerWallType::wtwCone);
    toggle_line("wipe_tower_extra_rib_length", have_rib_wall);
    toggle_line("wipe_tower_rib_width", have_rib_wall);
    toggle_line("wipe_tower_fillet_wall", have_rib_wall);
    toggle_field("prime_tower_width", have_prime_tower && (supports_wipe_tower_2 || have_rib_wall));

    toggle_line("single_extruder_multi_material_priming", !bSEMM && have_prime_tower && supports_wipe_tower_2);

    toggle_line("prime_volume",have_prime_tower && (!purge_in_primetower || !bSEMM));

    for (auto el : {"flush_into_infill", "flush_into_support", "flush_into_objects"})
        toggle_field(el, have_prime_tower);

    bool have_avoid_crossing_perimeters = config->opt_bool("reduce_crossing_wall");
    toggle_line("max_travel_detour_distance", have_avoid_crossing_perimeters);

    bool has_set_other_flow_ratios = config->opt_bool("set_other_flow_ratios");
    for (auto el : {"first_layer_flow_ratio", "outer_wall_flow_ratio", "inner_wall_flow_ratio", "overhang_flow_ratio", "sparse_infill_flow_ratio", "internal_solid_infill_flow_ratio", "gap_fill_flow_ratio", "support_flow_ratio", "support_interface_flow_ratio"})
        toggle_line(el, has_set_other_flow_ratios);

    bool has_overhang_speed = config->opt_bool("enable_overhang_speed");
    for (auto el : {"overhang_1_4_speed", "overhang_2_4_speed", "overhang_3_4_speed", "overhang_4_4_speed"})
        toggle_line(el, has_overhang_speed);

    toggle_line("slowdown_for_curled_perimeters", has_overhang_speed);

    toggle_line("flush_into_objects", !is_global_config);

    toggle_line("support_interface_not_for_body",config->opt_int("support_interface_filament")&&!config->opt_int("support_filament"));

    // Get the current fuzzy skin state
    bool has_fuzzy_skin = config->opt_enum<FuzzySkinType>("fuzzy_skin") != FuzzySkinType::Disabled_fuzzy;

    // Show fuzzy skin options when fuzzy skin is not disabled
    for (auto el : {"fuzzy_skin_mode", "fuzzy_skin_noise_type", "fuzzy_skin_point_distance", "fuzzy_skin_thickness", "fuzzy_skin_first_layer"})
        toggle_line(el, has_fuzzy_skin);

    // Show noise type specific options with the same logic
    NoiseType fuzzy_skin_noise_type = config->opt_enum<NoiseType>("fuzzy_skin_noise_type");
    const bool is_ripple = fuzzy_skin_noise_type == NoiseType::Ripple;
    toggle_line("fuzzy_skin_scale", fuzzy_skin_noise_type != NoiseType::Classic && has_fuzzy_skin && !is_ripple);
    toggle_line("fuzzy_skin_octaves", fuzzy_skin_noise_type != NoiseType::Classic && fuzzy_skin_noise_type != NoiseType::Voronoi && has_fuzzy_skin && !is_ripple);
    toggle_line("fuzzy_skin_persistence", (fuzzy_skin_noise_type == NoiseType::Perlin || fuzzy_skin_noise_type == NoiseType::Billow) && has_fuzzy_skin && !is_ripple);
    toggle_line("fuzzy_skin_ripples_per_layer", is_ripple && has_fuzzy_skin);
    toggle_line("fuzzy_skin_ripple_offset", is_ripple && has_fuzzy_skin);
    toggle_line("fuzzy_skin_layers_between_ripple_offset", is_ripple && has_fuzzy_skin);

    bool have_arachne = config->opt_enum<PerimeterGeneratorType>("wall_generator") == PerimeterGeneratorType::Arachne;
    for (auto el : {"wall_transition_length", "wall_transition_filter_deviation", "wall_transition_angle", "min_feature_size", "min_length_factor",
        "min_bead_width", "wall_distribution_count", "initial_layer_min_bead_width", "wall_maximum_resolution", "wall_maximum_deviation"})
        toggle_line(el, have_arachne);
    toggle_field("detect_thin_wall", !have_arachne);

    // Orca
    auto is_role_based_wipe_speed = config->opt_bool("role_based_wipe_speed");
    toggle_field("wipe_speed",!is_role_based_wipe_speed);

    for (auto el : {"accel_to_decel_enable", "accel_to_decel_factor"})
        toggle_line(el, gcf_is_klipper);
    if(gcf_is_klipper)
        toggle_field("accel_to_decel_factor", config->opt_bool("accel_to_decel_enable"));

    bool have_make_overhang_printable = config->opt_bool("make_overhang_printable");
    toggle_line("make_overhang_printable_angle", have_make_overhang_printable);
    toggle_line("make_overhang_printable_hole_size", have_make_overhang_printable);

    toggle_line("min_width_top_surface", config->opt_bool("only_one_wall_top") || ((config->opt_float("min_length_factor") > 0.5f) && have_arachne)); // 0.5 is default value

    for (auto el : { "hole_to_polyhole_threshold", "hole_to_polyhole_twisted" })
        toggle_line(el, config->opt_bool("hole_to_polyhole"));

    bool has_detect_overhang_wall = config->opt_bool("detect_overhang_wall");
    bool has_overhang_reverse     = config->opt_bool("overhang_reverse");
    bool allow_overhang_reverse   = !has_spiral_vase;
    toggle_line("overhang_reverse", allow_overhang_reverse);
    toggle_line("overhang_reverse_internal_only", allow_overhang_reverse && has_overhang_reverse);
    bool has_overhang_reverse_internal_only = config->opt_bool("overhang_reverse_internal_only");
    if (has_overhang_reverse_internal_only){
        DynamicPrintConfig new_conf = *config;
        new_conf.set_key_value("overhang_reverse_threshold", new ConfigOptionFloatOrPercent(0,true));
        apply(config, &new_conf);
    }
    toggle_line("overhang_reverse_threshold", has_detect_overhang_wall && allow_overhang_reverse && has_overhang_reverse && !has_overhang_reverse_internal_only);
    toggle_line("timelapse_type", m_state.is_BBL_Printer);


    bool have_small_area_infill_flow_compensation = config->opt_bool("small_area_infill_flow_compensation");
    toggle_line("small_area_infill_flow_compensation_model", have_small_area_infill_flow_compensation);


    toggle_field("seam_slope_type", !has_spiral_vase);
    bool has_seam_slope = !has_spiral_vase && config->opt_enum<SeamScarfType>("seam_slope_type") != SeamScarfType::None;
    toggle_line("seam_slope_conditional", has_seam_slope);
    toggle_line("seam_slope_start_height", has_seam_slope);
    toggle_line("seam_slope_entire_loop", has_seam_slope);
    toggle_line("seam_slope_min_length", has_seam_slope);
    toggle_line("seam_slope_steps", has_seam_slope);
    toggle_line("seam_slope_inner_walls", has_seam_slope);
    toggle_line("scarf_joint_speed", has_seam_slope);
    toggle_line("scarf_joint_flow_ratio", has_seam_slope);
    toggle_field("seam_slope_min_length", !config->opt_bool("seam_slope_entire_loop"));
    toggle_line("scarf_angle_threshold", has_seam_slope && config->opt_bool("seam_slope_conditional"));
    toggle_line("scarf_overhang_threshold", has_seam_slope && config->opt_bool("seam_slope_conditional"));

    bool use_beam_interlocking = config->opt_bool("interlocking_beam");
    toggle_line("mmu_segmented_region_interlocking_depth", !use_beam_interlocking);
    toggle_line("interlocking_beam_width", use_beam_interlocking);
    toggle_line("interlocking_orientation", use_beam_interlocking);
    toggle_line("interlocking_beam_layer_count", use_beam_interlocking);
    toggle_line("interlocking_depth", use_beam_interlocking);
    toggle_line("interlocking_boundary_avoidance", use_beam_interlocking);

    bool lattice_options = config->opt_enum<InfillPattern>("sparse_infill_pattern") == InfillPattern::ipLateralLattice;
    for (auto el : { "lateral_lattice_angle_1", "lateral_lattice_angle_2"})
        toggle_line(el, lattice_options);

    bool lightning_options = config->opt_enum<InfillPattern>("sparse_infill_pattern") == InfillPattern::ipLightning;
    for (auto el : { "lightning_overhang_angle", "lightning_prune_angle", "lightning_straightening_angle" })
        toggle_line(el, lightning_options);

    // Adaptative Cubic and support cubic infill patterns do not support infill rotation.
    bool FillAdaptive = (pattern == InfillPattern::ipAdaptiveCubic || pattern == InfillPattern::ipSupportCubic);

    //Orca: disable infill_direction/solid_infill_direction if sparse_infill_rotate_template/solid_infill_rotate_template is not empty value and adaptive cubic/support cubic infill pattern is not selected
    toggle_field("sparse_infill_rotate_template", !FillAdaptive);
    toggle_field("infill_direction", config->opt_string("sparse_infill_rotate_template") == "" && !FillAdaptive);
    toggle_field("solid_infill_direction", config->opt_string("solid_infill_rotate_template") == "");

    toggle_line("infill_overhang_angle", config->opt_enum<InfillPattern>("sparse_infill_pattern") == InfillPattern::ipLateralHoneycomb);

    std::string printer_type = m_bundle.printers.get_edited_preset().get_printer_type(&m_bundle);
    toggle_line("enable_wrapping_detection", support_wrapping_detection(printer_type));
}

bool ConfigManipulation::show_spiral_mode_settings_dialog(bool is_object_config)
{
    std::vector<UiText> msg_text = {ui_text("Spiral mode only works when wall loops is 1, support is disabled, clumping detection by probing is disabled, top shell layers is 0, sparse infill density is 0 and timelapse type is traditional.")};
    auto printer_structure_opt = m_bundle.printers.get_edited_preset().config.option<ConfigOptionEnum<PrinterStructure>>("printer_structure");
    if (printer_structure_opt && printer_structure_opt->value == PrinterStructure::psI3) {
        msg_text.push_back(ui_text(" But machines with I3 structure will not generate timelapse videos."));
    }
    if (!is_object_config) {
        msg_text.push_back(paragraph);
        msg_text.push_back(ui_text("Change these settings automatically?\n"
            "Yes - Change these settings and enable spiral mode automatically\n"
            "No  - Give up using spiral mode this time"));
    }

    is_msg_dlg_already_exist = true;
    bool answer_yes = true;
    if (!is_object_config)
        answer_yes = m_dialogs.ask("spiral_mode", std::move(msg_text));
    else
        m_dialogs.inform("spiral_mode", std::move(msg_text));
    is_msg_dlg_already_exist = false;
    return answer_yes;
}

bool ConfigManipulation::get_temperature_range(DynamicPrintConfig* config, int& range_low, int& range_high)
{
    bool range_low_exist = false, range_high_exist = false;
    if (config->has("nozzle_temperature_range_low")) {
        range_low = config->opt_int("nozzle_temperature_range_low", (unsigned int) 0);
        range_low_exist = true;
    }
    if (config->has("nozzle_temperature_range_high")) {
        range_high = config->opt_int("nozzle_temperature_range_high", (unsigned int) 0);
        range_high_exist = true;
    }
    return range_low_exist && range_high_exist;
}

void ConfigManipulation::check_nozzle_recommended_temperature_range(DynamicPrintConfig* config)
{
    if (is_msg_dlg_already_exist)
        return;

    int temperature_range_low, temperature_range_high;
    if (!get_temperature_range(config, temperature_range_low, temperature_range_high)) return;

    // Get the selected filament type
    std::string filament_type = "";
    if (config->has("filament_type") && config->option<ConfigOptionStrings>("filament_type")->values.size() > 0) {
        filament_type = config->option<ConfigOptionStrings>("filament_type")->values[0];
    }

    int min_recommended_temp = 190;
    int max_recommended_temp = 300;

    if (!MaterialType::get_temperature_range(filament_type, min_recommended_temp, max_recommended_temp)) {
        filament_type = "Unknown";
    }

    std::vector<UiText> msg_text;
    bool need_check = false;
    if (temperature_range_low < min_recommended_temp) {
        msg_text.push_back(ui_text(u8"A minimum temperature above %d\u2103 is recommended for %s.\n",
                                   {std::to_string(min_recommended_temp), filament_type}));
        need_check = true;
    }
    if (temperature_range_high > max_recommended_temp) {
        msg_text.push_back(ui_text(u8"A maximum temperature below %d\u2103 is recommended for %s.\n",
                                   {std::to_string(max_recommended_temp), filament_type}));
        need_check = true;
    }
    if (temperature_range_low > temperature_range_high) {
        msg_text.push_back(ui_text("The recommended minimum temperature cannot be higher than the recommended maximum temperature.\n"));
        need_check = true;
    }
    if (need_check) {
        msg_text.push_back(ui_text("Please check.\n"));
        is_msg_dlg_already_exist = true;
        m_dialogs.inform("nozzle_recommended_temperature_range", std::move(msg_text));
        is_msg_dlg_already_exist = false;
    }
}

void ConfigManipulation::check_nozzle_temperature_range(DynamicPrintConfig* config)
{
    if (is_msg_dlg_already_exist)
        return;

    int temperature_range_low, temperature_range_high;
    if (!get_temperature_range(config, temperature_range_low, temperature_range_high)) return;

    if (config->has("nozzle_temperature")) {
        if (config->opt_int("nozzle_temperature", 0) < temperature_range_low || config->opt_int("nozzle_temperature", 0) > temperature_range_high) {
            std::vector<UiText> msg_text{ui_text("Nozzle may be blocked when the temperature is out of recommended range.\n"
                                                 "Please make sure whether to use the temperature to print.\n\n")};
            msg_text.push_back(ui_text("The recommended nozzle temperature for this filament type is [%d, %d] degrees Celsius.",
                                       {std::to_string(temperature_range_low), std::to_string(temperature_range_high)}));
            is_msg_dlg_already_exist = true;
            m_dialogs.inform("nozzle_temperature_range", std::move(msg_text));
            is_msg_dlg_already_exist = false;
        }
    }
}

void ConfigManipulation::check_nozzle_temperature_initial_layer_range(DynamicPrintConfig* config)
{
    if (is_msg_dlg_already_exist)
        return;

    int temperature_range_low, temperature_range_high;
    if (!get_temperature_range(config, temperature_range_low, temperature_range_high)) return;

    if (config->has("nozzle_temperature_initial_layer")) {
        if (config->opt_int("nozzle_temperature_initial_layer", 0) < temperature_range_low ||
            config->opt_int("nozzle_temperature_initial_layer", 0) > temperature_range_high) {
            std::vector<UiText> msg_text{ui_text("Nozzle may be blocked when the temperature is out of recommended range.\n"
                                                 "Please make sure whether to use the temperature to print.\n\n")};
            msg_text.push_back(ui_text("The recommended nozzle temperature for this filament type is [%d, %d] degrees Celsius.",
                                       {std::to_string(temperature_range_low), std::to_string(temperature_range_high)}));
            is_msg_dlg_already_exist = true;
            m_dialogs.inform("nozzle_temperature_initial_layer_range", std::move(msg_text));
            is_msg_dlg_already_exist = false;
        }
    }
}

void ConfigManipulation::check_filament_max_volumetric_speed(DynamicPrintConfig* config)
{
    const float max_volumetric_speed = config->has("filament_max_volumetric_speed") ? config->opt_float("filament_max_volumetric_speed", (float) 0.5)
                                                                                    : 0.5;
    // BBS: limite the min max_volumetric_speed
    if (max_volumetric_speed < 0.5) {
        DynamicPrintConfig new_conf = *config;
        is_msg_dlg_already_exist = true;
        m_dialogs.inform("filament_max_volumetric_speed", {ui_text("Too small max volumetric speed.\nReset to 0.5.")});
        new_conf.set_key_value("filament_max_volumetric_speed", new ConfigOptionFloats({0.5}));
        apply(config, &new_conf);
        is_msg_dlg_already_exist = false;
    }
}

void ConfigManipulation::check_chamber_temperature(DynamicPrintConfig* config)
{
    const bool support_chamber_temp_control = m_bundle.printers.get_selected_preset().config.opt_bool("support_chamber_temp_control");
    if (support_chamber_temp_control && config->has("chamber_temperature")) {
        const std::string filament_type = config->option<ConfigOptionStrings>("filament_type")->get_at(0);
        int chamber_min_temp, chamber_max_temp;
        if (MaterialType::get_chamber_temperature_range(filament_type, chamber_min_temp, chamber_max_temp)) {
            if (chamber_max_temp < config->option<ConfigOptionInts>("chamber_temperature")->get_at(0)) {
                is_msg_dlg_already_exist = true;
                m_dialogs.inform("chamber_temperature",
                                 {ui_text("Current chamber temperature is higher than the material's safe temperature, this may result in material "
                                          "softening and clogging. The maximum safe temperature for the material is %d",
                                          {std::to_string(chamber_max_temp)})});
                is_msg_dlg_already_exist = false;
            }
        }
    }
}

void ConfigManipulation::check_chamber_minimal_temperature(DynamicPrintConfig* config)
{
    // Orca: the minimal chamber temperature is a "start printing" threshold that is passed to the
    // print start macro. It must not exceed the target chamber temperature, otherwise the macro
    // could wait forever for a temperature the heater is never asked to reach.
    if (config->has("chamber_minimal_temperature") && config->has("chamber_temperature")) {
        const int chamber_min_temp = config->option<ConfigOptionInts>("chamber_minimal_temperature")->get_at(0);
        const int chamber_target_temp = config->option<ConfigOptionInts>("chamber_temperature")->get_at(0);
        if (chamber_min_temp > chamber_target_temp) {
            DynamicPrintConfig new_conf = *config;
            is_msg_dlg_already_exist = true;
            m_dialogs.inform("chamber_minimal_temperature",
                             {ui_text(u8"The minimal chamber temperature (%d\u2103) is higher than the target chamber temperature (%d\u2103). The "
                                      u8"minimal value is the threshold at which printing starts while the chamber keeps heating toward the target, so "
                                      u8"it should not exceed it. It will be clamped to the target.",
                                      {std::to_string(chamber_min_temp), std::to_string(chamber_target_temp)})});
            new_conf.set_key_value("chamber_minimal_temperature", new ConfigOptionInts({chamber_target_temp}));
            apply(config, &new_conf);
            is_msg_dlg_already_exist = false;
        }
    }
}

bool support_wrapping_detection(const std::string& printer_type)
{
    const std::string config_file = Slic3r::resources_dir() + "/printers/" + printer_type + ".json";
    boost::nowide::ifstream json_file(config_file.c_str());
    try {
        nlohmann::json jj;
        if (json_file.is_open()) {
            json_file >> jj;
            if (jj.contains("00.00.00.00")) {
                const nlohmann::json& printer = jj["00.00.00.00"];
                if (printer.contains("support_wrapping_detection")) {
                    return printer["support_wrapping_detection"].get<bool>();
                }
            }
        }
    } catch (...) {
        BOOST_LOG_TRIVIAL(error) << __FUNCTION__ << " failed";
    }
    return false;
}

}  // namespace orcinus::orca::detail
