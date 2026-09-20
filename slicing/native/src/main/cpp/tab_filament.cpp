#include "tab_filament.hpp"

#include <algorithm>

#include "settings_fields.hpp"

// TabFilament of the desktop app (Tab.cpp): the pages of the filament tab, its
// overrides of the printer and process settings, and the checks a change
// brings. The layout is the upstream build() with its statements in order.

namespace orcinus::orca::detail {

using namespace Slic3r;

const std::string& TabFilament::get_custom_gcode(const std::string& opt_key)
{
    return m_config->opt_string(opt_key, unsigned(0));
}

void TabFilament::set_custom_gcode(const std::string& opt_key, const std::string& value)
{
    std::vector<std::string> gcodes = static_cast<const ConfigOptionStrings*>(m_config->option(opt_key))->values;
    gcodes[0] = value;

    DynamicPrintConfig new_conf = *m_config;
    new_conf.set_key_value(opt_key, new ConfigOptionStrings(gcodes));
    load_config(new_conf);
}

void TabFilament::add_filament_overrides_page()
{
    //BBS
    PageShp page = add_options_page("Setting Overrides", "custom-gcode_setting_override"); // ORCA: icon only visible on placeholders

    const int extruder_idx = 0; // #ys_FIXME

    // The check box before the label switches the override on (near_label_widget).
    auto append_override_option = [](const ConfigOptionsGroupShp& optgroup, const std::string& opt_key, const int opt_index) {
        Line line = optgroup->create_single_option_line(optgroup->get_option(opt_key, opt_index));
        line.near_label_widget = true;
        optgroup->append_line(line);
    };

    ConfigOptionsGroupShp retraction_optgroup = page->new_optgroup("Retraction", "param_retraction");

    for (const std::string opt_key : {  "filament_retraction_length",
                                        "filament_z_hop",
                                        "filament_z_hop_types",
                                        "filament_retract_lift_above",
                                        "filament_retract_lift_below",
                                        "filament_retract_lift_enforce",
                                        "filament_retraction_speed",
                                        "filament_deretraction_speed",
                                        "filament_retract_restart_extra",
                                        "filament_retraction_minimum_travel",
                                        "filament_retract_when_changing_layer",
                                        "filament_wipe",
                                        //BBS
                                        "filament_wipe_distance",
                                        "filament_retract_before_wipe",
                                        "filament_long_retractions_when_cut",
                                        "filament_retraction_distances_when_cut"
                                        //SoftFever
                                        // "filament_seam_gap"
                                     })
        append_override_option(retraction_optgroup, opt_key, extruder_idx);

    ConfigOptionsGroupShp ironing_optgroup = page->new_optgroup("Ironing", "param_ironing");

    for (const std::string opt_key : {  "filament_ironing_flow",
                                        "filament_ironing_spacing",
                                        "filament_ironing_inset",
                                        "filament_ironing_speed"
                                     })
        append_override_option(ironing_optgroup, opt_key, extruder_idx);
}

void TabFilament::update_filament_overrides_page(const DynamicPrintConfig* printers_config)
{
    if (m_active_page == nullptr || m_active_page->title() != "Setting Overrides")
        return;

    Page* page = m_active_page;

    ConfigOptionsGroupShp optgroup = page->get_optgroup("Retraction");
    if (optgroup == nullptr)
        return;

    std::vector<std::string> opt_keys = {   "filament_retraction_length",
                                            "filament_z_hop",
                                            "filament_z_hop_types",
                                            "filament_retract_lift_above",
                                            "filament_retract_lift_below",
                                            "filament_retract_lift_enforce",
                                            "filament_retraction_speed",
                                            "filament_deretraction_speed",
                                            "filament_retract_restart_extra",
                                            "filament_retraction_minimum_travel",
                                            "filament_retract_when_changing_layer",
                                            "filament_wipe",
                                            //BBS
                                            "filament_wipe_distance",
                                            "filament_retract_before_wipe",
                                            "filament_long_retractions_when_cut",
                                            "filament_retraction_distances_when_cut"
                                            //SoftFever
                                            // "filament_seam_gap"
                                        };

    const int selection = m_state.variant;
    const auto* opt = dynamic_cast<const ConfigOptionVectorBase*>(m_config->option("filament_retraction_length"));
    const int extruder_idx = selection < 0 || selection >= int(opt->size()) ? 0 : selection;

    const bool have_retract_length = dynamic_cast<const ConfigOptionVectorBase*>(m_config->option("filament_retraction_length"))->is_nil(extruder_idx)
        || m_config->opt_float("filament_retraction_length", extruder_idx) > 0;

    for (const std::string& opt_key : opt_keys) {
        bool is_checked = opt_key == "filament_retraction_length" ? true : have_retract_length;
        // The check box can be used while the override may be set at all.
        m_overrides[opt_key + "#0"] = is_checked;

        is_checked &= !dynamic_cast<const ConfigOptionVectorBase*>(m_config->option(opt_key))->is_nil(extruder_idx);

        if (opt_key == "filament_long_retractions_when_cut") {
            const int machine_enabled_level = printers_config->option<ConfigOptionInt>("enable_long_retraction_when_cut")->value;
            const bool machine_enabled = machine_enabled_level == LongRectrationLevel::EnableFilament;
            toggle_line(opt_key, machine_enabled, extruder_idx + 256);
            toggle_option(opt_key, is_checked && machine_enabled, extruder_idx + 256);
        } else if (opt_key == "filament_retraction_distances_when_cut") {
            const int machine_enabled_level = printers_config->option<ConfigOptionInt>("enable_long_retraction_when_cut")->value;
            const bool machine_enabled = machine_enabled_level == LongRectrationLevel::EnableFilament;
            const bool filament_enabled = m_config->option<ConfigOptionBools>("filament_long_retractions_when_cut")->values[extruder_idx] == 1;
            toggle_line(opt_key, filament_enabled && machine_enabled, extruder_idx + 256);
            toggle_option(opt_key, is_checked && filament_enabled && machine_enabled, extruder_idx + 256);
        } else {
            toggle_option(opt_key, is_checked, extruder_idx + 256);
        }
    }

    // Handle ironing overrides
    if (ConfigOptionsGroupShp ironing_optgroup = page->get_optgroup("Ironing")) {
        std::vector<std::string> ironing_opt_keys = {
            "filament_ironing_flow",
            "filament_ironing_spacing",
            "filament_ironing_inset",
            "filament_ironing_speed"
        };

        for (const std::string& opt_key : ironing_opt_keys) {
            const bool is_checked = !dynamic_cast<const ConfigOptionVectorBase*>(m_config->option(opt_key))->is_nil(extruder_idx);
            m_overrides[opt_key + "#0"] = true;
            toggle_option(opt_key, is_checked, extruder_idx + 256);
        }
    }
}

void TabFilament::build()
{
    m_presets = &m_preset_bundle->filaments;
    load_initial_data();

    auto page = add_options_page("Filament", "custom-gcode_filament"); // ORCA: icon only visible on placeholders
        //BBS
        auto optgroup = page->new_optgroup("Basic information", "param_information");
        optgroup->append_single_option_line("filament_type", "material_basic_information#type"); // ORCA use same width with other elements
        optgroup->append_single_option_line("filament_vendor", "material_basic_information#vendor");
        optgroup->append_single_option_line("filament_soluble", "material_basic_information#soluble-material");
        // BBS
        optgroup->append_single_option_line("filament_is_support", "material_basic_information#support-material");
        optgroup->append_single_option_line("filament_change_length", "material_basic_information#filament-ramming-length");

        //optgroup->append_single_option_line("filament_colour");
        optgroup->append_single_option_line("required_nozzle_HRC", "material_basic_information#required-nozzle-hrc");
        optgroup->append_single_option_line("default_filament_colour", "material_basic_information#default-color");
        optgroup->append_single_option_line("filament_diameter", "material_basic_information#diameter");
        optgroup->append_single_option_line("filament_adhesiveness_category", "material_basic_information#adhesiveness-category");

        optgroup->append_single_option_line("filament_density", "material_basic_information#density");
        optgroup->append_single_option_line("filament_shrink", "material_basic_information#shrinkage-xy");
        optgroup->append_single_option_line("filament_shrinkage_compensation_z", "material_basic_information#shrinkage-z");
        optgroup->append_single_option_line("filament_cost", "material_basic_information#price");
        //BBS
        optgroup->append_single_option_line("temperature_vitrification", "material_basic_information#softening-temperature");
        optgroup->append_single_option_line("idle_temperature", "material_basic_information#idle-temperature");
        Line line = { "Recommended nozzle temperature", "Recommended nozzle temperature range of this filament. 0 means no set" };
        line.append_option(optgroup->get_option("nozzle_temperature_range_low"));
        line.append_option(optgroup->get_option("nozzle_temperature_range_high"));
        optgroup->append_line(line);

        optgroup->m_on_change = [this](const std::string& opt_key, const boost::any& value) {
            DynamicPrintConfig& filament_config = m_preset_bundle->filaments.get_edited_preset().config;

            update_dirty();
            if (!m_postpone_update_ui && (opt_key == "nozzle_temperature_range_low" || opt_key == "nozzle_temperature_range_high")) {
                m_config_manipulation.check_nozzle_recommended_temperature_range(&filament_config);
            }
            on_value_change(opt_key, value);
        };

        // Orca: New section to focus on flow rate and PA to declutter general section
        optgroup = page->new_optgroup("Flow ratio and Pressure Advance", "param_flow_ratio_and_pressure_advance");
        optgroup->append_single_option_line("pellet_flow_coefficient", "printer_basic_information_advanced#pellet-modded-printer");
        optgroup->append_single_option_line("filament_flow_ratio", "material_flow_ratio_and_pressure_advance#flow-ratio", 0);

        optgroup->append_single_option_line("enable_pressure_advance", "material_flow_ratio_and_pressure_advance#pressure-advance");
        optgroup->append_single_option_line("pressure_advance", "material_flow_ratio_and_pressure_advance#pressure-advance");

        // Orca: adaptive pressure advance and calibration model
        optgroup->append_single_option_line("adaptive_pressure_advance", "material_flow_ratio_and_pressure_advance#enable-adaptive-pressure-advance-beta");
        optgroup->append_single_option_line("adaptive_pressure_advance_overhangs", "material_flow_ratio_and_pressure_advance#enable-adaptive-pressure-advance-for-overhangs-beta");
        optgroup->append_single_option_line("adaptive_pressure_advance_bridges", "material_flow_ratio_and_pressure_advance#pressure-advance-for-bridges");

        Option option = optgroup->get_option("adaptive_pressure_advance_model");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = 15;
        optgroup->append_single_option_line(option);
        //

        optgroup = page->new_optgroup("Print chamber temperature", "param_chamber_temp");
        optgroup->append_single_option_line("activate_chamber_temp_control", "material_temperatures#print-chamber-temperature");
        line = { "Chamber temperature", "Target chamber temperature, and the minimal chamber temperature at which printing should start" };
        line.label_path = "material_temperatures#print-chamber-temperature";
        Option chamber_temp_target_opt = optgroup->get_option("chamber_temperature");
        chamber_temp_target_opt.opt.label = "Target";
        line.append_option(chamber_temp_target_opt);
        Option chamber_min_temp_opt = optgroup->get_option("chamber_minimal_temperature");
        chamber_min_temp_opt.opt.label = "Minimal";
        line.append_option(chamber_min_temp_opt);
        optgroup->append_line(line);
        optgroup->m_on_change = [this](const std::string& opt_key, const boost::any& value) {
            DynamicPrintConfig& filament_config = m_preset_bundle->filaments.get_edited_preset().config;

            update_dirty();
            if (opt_key == "chamber_temperature") {
                m_config_manipulation.check_chamber_temperature(&filament_config);
                m_config_manipulation.check_chamber_minimal_temperature(&filament_config);
            }
            else if (opt_key == "chamber_minimal_temperature") {
                m_config_manipulation.check_chamber_minimal_temperature(&filament_config);
            }

            on_value_change(opt_key, value);
        };

        optgroup = page->new_optgroup("Print temperature", "param_extruder_temp");
        line = { "Nozzle", "Nozzle temperature when printing" };
        line.label_path = "material_temperatures#nozzle";
        line.append_option(optgroup->get_option("nozzle_temperature_initial_layer", 0));
        line.append_option(optgroup->get_option("nozzle_temperature", 0));
        optgroup->append_line(line);

        optgroup = page->new_optgroup("Bed temperature", "param_bed_temp");
        line = { "Cool Plate (SuperTack)",
                 "Bed temperature when the Cool Plate SuperTack is installed. A value of 0 means the filament does not support printing on the Cool Plate SuperTack." };
        line.label_path = "material_temperatures#bed";
        line.append_option(optgroup->get_option("supertack_plate_temp_initial_layer"));
        line.append_option(optgroup->get_option("supertack_plate_temp"));
        optgroup->append_line(line);

        line = { "Cool Plate",
                 "Bed temperature when the Cool Plate is installed. A value of 0 means the filament does not support printing on the Cool Plate." };
        line.label_path = "material_temperatures#bed";
        line.append_option(optgroup->get_option("cool_plate_temp_initial_layer"));
        line.append_option(optgroup->get_option("cool_plate_temp"));
        optgroup->append_line(line);

        line = { "Textured Cool Plate",
                 "Bed temperature when the Textured Cool Plate is installed. A value of 0 means the filament does not support printing on the Textured Cool Plate." };
        line.label_path = "material_temperatures#bed";
        line.append_option(optgroup->get_option("textured_cool_plate_temp_initial_layer"));
        line.append_option(optgroup->get_option("textured_cool_plate_temp"));
        optgroup->append_line(line);

        line = { "Engineering Plate",
                 "Bed temperature when the Engineering Plate is installed. A value of 0 means the filament does not support printing on the Engineering Plate." };
        line.label_path = "material_temperatures#bed";
        line.append_option(optgroup->get_option("eng_plate_temp_initial_layer"));
        line.append_option(optgroup->get_option("eng_plate_temp"));
        optgroup->append_line(line);

        line = { "Smooth PEI Plate / High Temp Plate",
                 "Bed temperature when the Smooth PEI Plate/High Temperature Plate is installed. A value of 0 means the filament does not support printing on the Smooth PEI Plate/High Temp Plate." };
        line.label_path = "material_temperatures#bed";
        line.append_option(optgroup->get_option("hot_plate_temp_initial_layer"));
        line.append_option(optgroup->get_option("hot_plate_temp"));
        optgroup->append_line(line);

        line = { "Textured PEI Plate",
                 "Bed temperature when the Textured PEI Plate is installed. A value of 0 means the filament does not support printing on the Textured PEI Plate." };
        line.label_path = "material_temperatures#bed";
        line.append_option(optgroup->get_option("textured_plate_temp_initial_layer"));
        line.append_option(optgroup->get_option("textured_plate_temp"));
        optgroup->append_line(line);

        optgroup->m_on_change = [this](const std::string& opt_key, const boost::any& value)
        {
            DynamicPrintConfig& filament_config = m_preset_bundle->filaments.get_edited_preset().config;

            update_dirty();
            if (opt_key == "nozzle_temperature") {
                m_config_manipulation.check_nozzle_temperature_range(&filament_config);
            }
            else if (opt_key == "nozzle_temperature_initial_layer") {
                m_config_manipulation.check_nozzle_temperature_initial_layer_range(&filament_config);
            }

            on_value_change(opt_key, value);
        };

        //BBS
        optgroup = page->new_optgroup("Volumetric speed limitation", "param_volumetric_speed");
        optgroup->append_single_option_line("filament_adaptive_volumetric_speed", "material_volumetric_speed_limitation#adaptive-volumetric-speed", 0);
        optgroup->append_single_option_line("filament_max_volumetric_speed", "material_volumetric_speed_limitation#max-volumetric-speed", 0);

    page = add_options_page("Cooling", "custom-gcode_cooling_fan"); // ORCA: icon only visible on placeholders

        optgroup = page->new_optgroup("Cooling for specific layer", "param_cooling_specific_layer");
        optgroup->append_single_option_line("close_fan_the_first_x_layers", "material_cooling#no-cooling-for-the-first");
        optgroup->append_single_option_line("full_fan_speed_layer", "material_cooling#full-fan-speed-at-layer");

        optgroup = page->new_optgroup("Part cooling fan", "param_cooling_part_fan");
        line = { "Min fan speed threshold", "Part cooling fan speed will start to run at min speed when the estimated layer time is no longer than the layer time in setting. When layer time is shorter than threshold, fan speed is interpolated between the minimum and maximum fan speed according to layer printing time" };
        line.label_path = "material_cooling#material-part-cooling-fan";
        line.append_option(optgroup->get_option("fan_min_speed"));
        line.append_option(optgroup->get_option("fan_cooling_layer_time"));
        optgroup->append_line(line);
        line = { "Max fan speed threshold", "Part cooling fan speed will be max when the estimated layer time is shorter than the setting value" };
        line.label_path = "material_cooling#material-part-cooling-fan";
        line.append_option(optgroup->get_option("fan_max_speed"));
        line.append_option(optgroup->get_option("slow_down_layer_time"));
        optgroup->append_line(line);
        optgroup->append_single_option_line("reduce_fan_stop_start_freq", "material_cooling#keep-fan-always-on");
        optgroup->append_single_option_line("slow_down_for_layer_cooling", "material_cooling#slow-printing-down-for-better-layer-cooling");
        optgroup->append_single_option_line("dont_slow_down_outer_wall", "material_cooling#dont-slow-down-outer-walls");
        optgroup->append_single_option_line("slow_down_min_speed", "material_cooling#min-print-speed");

        optgroup->append_single_option_line("enable_overhang_bridge_fan", "material_cooling#force-cooling-for-overhangs-and-bridges");
        optgroup->append_single_option_line("overhang_fan_threshold", "material_cooling#overhang-cooling-activation-threshold");
        optgroup->append_single_option_line("overhang_fan_speed", "material_cooling#overhangs-and-external-bridges-fan-speed");
        optgroup->append_single_option_line("internal_bridge_fan_speed", "material_cooling#internal-bridges-fan-speed"); // ORCA: Add support for separate internal bridge fan speed control
        optgroup->append_single_option_line("support_material_interface_fan_speed", "material_cooling#support-interface-fan-speed");
        optgroup->append_single_option_line("ironing_fan_speed", "material_cooling#ironing-fan-speed"); // ORCA: Add support for ironing fan speed control

        optgroup = page->new_optgroup("Auxiliary part cooling fan", "param_cooling_aux_fan");
        optgroup->append_single_option_line("additional_cooling_fan_speed", "material_cooling#auxiliary-part-cooling-fan");

        optgroup = page->new_optgroup("Exhaust fan", "param_cooling_exhaust");

        optgroup->append_single_option_line("activate_air_filtration", "material_cooling#activate-air-filtration");

        line = {"During print", ""};
        line.append_option(optgroup->get_option("activate_air_filtration_during_print"));
        line.append_option(optgroup->get_option("during_print_exhaust_fan_speed"));
        line.label_path = "material_cooling#during-print";
        optgroup->append_line(line);


        line = {"Complete print", ""};
        line.append_option(optgroup->get_option("activate_air_filtration_on_completion"));
        line.append_option(optgroup->get_option("complete_print_exhaust_fan_speed"));
        line.label_path = "material_cooling#complete-print";
        optgroup->append_line(line);
        //BBS
        add_filament_overrides_page();
        const int gcode_field_height = 15; // 150
        const int notes_field_height = 25; // 250

    page = add_options_page("Advanced", "custom-gcode_advanced"); // ORCA: icon only visible on placeholders
        optgroup = page->new_optgroup("Filament start G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("filament_start_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;// 150;
        optgroup->append_single_option_line(option);

        optgroup = page->new_optgroup("Change extrusion role G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("filament_change_extrusion_role_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;// 150;
        optgroup->append_single_option_line(option);

        optgroup = page->new_optgroup("Filament end G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("filament_end_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;// 150;
        optgroup->append_single_option_line(option);

    page = add_options_page("Multimaterial", "custom-gcode_multi_material"); // ORCA: icon only visible on placeholders
        optgroup = page->new_optgroup("Wipe tower parameters", "param_tower");
        optgroup->append_single_option_line("filament_minimal_purge_on_wipe_tower", "material_multimaterial#multimaterial-wipe-tower-parameters");
        optgroup->append_single_option_line("filament_tower_interface_pre_extrusion_dist", "material_multimaterial#multimaterial-wipe-tower-parameters");
        optgroup->append_single_option_line("filament_tower_interface_pre_extrusion_length", "material_multimaterial#multimaterial-wipe-tower-parameters");
        optgroup->append_single_option_line("filament_tower_ironing_area", "material_multimaterial#multimaterial-wipe-tower-parameters");
        optgroup->append_single_option_line("filament_tower_interface_purge_volume", "material_multimaterial#multimaterial-wipe-tower-parameters");
        optgroup->append_single_option_line("filament_tower_interface_print_temp", "material_multimaterial#multimaterial-wipe-tower-parameters");

        optgroup = page->new_optgroup("Multi Filament");
        optgroup->append_single_option_line("long_retractions_when_ec", "material_multimaterial#multi-filament" , 0);
        optgroup->append_single_option_line("retraction_distances_when_ec", "material_multimaterial#multi-filament" , 0);

        optgroup = page->new_optgroup("Tool change parameters with single extruder MM printers", "param_toolchange");
        optgroup->append_single_option_line("filament_loading_speed_start", "material_multimaterial#loading-speed-at-the-start");
        optgroup->append_single_option_line("filament_loading_speed", "material_multimaterial#loading-speed");
        optgroup->append_single_option_line("filament_unloading_speed_start", "material_multimaterial#unloading-speed-at-the-start");
        optgroup->append_single_option_line("filament_unloading_speed", "material_multimaterial#unloading-speed");
        optgroup->append_single_option_line("filament_toolchange_delay", "material_multimaterial#delay-after-unloading");
        optgroup->append_single_option_line("filament_cooling_moves", "material_multimaterial#number-of-cooling-moves");
        optgroup->append_single_option_line("filament_cooling_initial_speed", "material_multimaterial#speed-of-the-first-cooling-move");
        optgroup->append_single_option_line("filament_cooling_final_speed", "material_multimaterial#speed-of-the-last-cooling-move");
        optgroup->append_single_option_line("filament_stamping_loading_speed", "material_multimaterial#stamping-loading-speed");
        optgroup->append_single_option_line("filament_stamping_distance", "material_multimaterial#stamping-distance");
        create_line_with_widget(optgroup.get(), "filament_ramming_parameters", "material_multimaterial#ramming-parameters", SettingWidget::ramming);

        optgroup = page->new_optgroup("Tool change parameters with multi extruder MM printers", "param_toolchange_multi_extruder");
        optgroup->append_single_option_line("filament_multitool_ramming", "material_multimaterial#tool-change-parameters-with-multi-extruder");
        optgroup->append_single_option_line("filament_multitool_ramming_volume", "material_multimaterial#multi-tool-ramming-volume");
        optgroup->append_single_option_line("filament_multitool_ramming_flow", "material_multimaterial#multi-tool-ramming-flow");

    page = add_options_page("Dependencies", "advanced");
        optgroup = page->new_optgroup("Compatible printers", "param_dependencies_printers");
        create_line_with_widget(optgroup.get(), "compatible_printers", "", SettingWidget::compatible_printers);

        option = optgroup->get_option("compatible_printers_condition");
        option.opt.full_width = true;
        optgroup->append_single_option_line(option, "material_dependencies#compatible-printers");

        optgroup = page->new_optgroup("Compatible process profiles", "param_dependencies_presets");
        create_line_with_widget(optgroup.get(), "compatible_prints", "", SettingWidget::compatible_prints);

        option = optgroup->get_option("compatible_prints_condition");
        option.opt.full_width = true;
        optgroup->append_single_option_line(option, "material_dependencies#compatible-process-profiles");

    page = add_options_page("Notes", "custom-gcode_note"); // ORCA: icon only visible on placeholders
        optgroup = page->new_optgroup("Notes", "note", 0);
        optgroup->label_width = 0;
        option = optgroup->get_option("filament_notes");
        option.opt.full_width = true;
        option.opt.height = notes_field_height;// 250;
        optgroup->append_single_option_line(option);
}

// Reload current config (aka presets->edited_preset->config) into the UI fields.
void TabFilament::reload_config()
{
    this->compatible_widget_reload("compatible_printers_condition", "compatible_printers");
    this->compatible_widget_reload("compatible_prints_condition", "compatible_prints");
    Tab::reload_config();

    // Recompute derived override UI from the newly loaded config
    update_filament_overrides_page(&m_preset_bundle->printers.get_edited_preset().config);
}

void TabFilament::toggle_options()
{
    if (m_active_page == nullptr)
        return;
    bool is_BBL_printer = false;
    if (m_preset_bundle != nullptr) {
        is_BBL_printer = m_preset_bundle->is_bbl_vendor();
    }

    const DynamicPrintConfig& printer_cfg = m_preset_bundle->printers.get_edited_preset().config;

    if (m_active_page->title() == "Cooling") {
        const bool has_enable_overhang_bridge_fan = m_config->opt_bool("enable_overhang_bridge_fan", 0);
        for (auto el : {"overhang_fan_speed", "overhang_fan_threshold", "internal_bridge_fan_speed"}) // ORCA: Add support for separate internal bridge fan speed control
            toggle_option(el, has_enable_overhang_bridge_fan);

        // Orca: toggle dont slow down for external perimeters if
        const bool has_slow_down_for_layer_cooling = m_config->opt_bool("slow_down_for_layer_cooling", 0);
        toggle_option("dont_slow_down_outer_wall", has_slow_down_for_layer_cooling);

        toggle_line("additional_cooling_fan_speed", printer_cfg.opt_bool("auxiliary_fan"));

        const bool support_air_filtration = printer_cfg.opt_bool("support_air_filtration");
        for (auto el : {"activate_air_filtration", "during_print_exhaust_fan_speed", "complete_print_exhaust_fan_speed"})
            toggle_line(el, support_air_filtration);

        if (support_air_filtration) {
            const bool activate_air_filtration = m_config->opt_bool("activate_air_filtration", 0);
            toggle_option("activate_air_filtration_during_print", activate_air_filtration);
            toggle_option("during_print_exhaust_fan_speed", activate_air_filtration && m_config->opt_bool("activate_air_filtration_during_print", 0));
            toggle_option("activate_air_filtration_on_completion", activate_air_filtration);
            toggle_option("complete_print_exhaust_fan_speed", activate_air_filtration && m_config->opt_bool("activate_air_filtration_on_completion", 0));
        }
    }
    if (m_active_page->title() == "Filament")
    {
        const bool pa = m_config->opt_bool("enable_pressure_advance", 0);
        toggle_option("pressure_advance", pa);

        //Orca: Enable the plates that should be visible when multi bed support is enabled or a BBL printer is selected; otherwise, enable only the plate visible for the selected bed type.
        DynamicConfig& proj_cfg = m_preset_bundle->project_config;
        std::string bed_temp_1st_layer_key = "";
        if (proj_cfg.has("curr_bed_type"))
        {
            bed_temp_1st_layer_key = get_bed_temp_1st_layer_key(proj_cfg.opt_enum<BedType>("curr_bed_type"));
        }

        const std::vector<std::string> bed_temp_keys = {"supertack_plate_temp_initial_layer", "cool_plate_temp_initial_layer",
                                                        "textured_cool_plate_temp_initial_layer", "eng_plate_temp_initial_layer",
                                                        "textured_plate_temp_initial_layer", "hot_plate_temp_initial_layer"};

        const bool support_multi_bed_types = std::find(bed_temp_keys.begin(), bed_temp_keys.end(), bed_temp_1st_layer_key) == bed_temp_keys.end()
            || is_BBL_printer || printer_cfg.opt_bool("support_multi_bed_types");

        for (const auto& key : bed_temp_keys)
        {
            toggle_line(key, support_multi_bed_types || bed_temp_1st_layer_key == key);
        }

        // Orca: adaptive pressure advance and calibration model
        // If PA is not enabled, disable adaptive pressure advance and hide the model section
        // If adaptive PA is not enabled, hide the adaptive PA model section
        toggle_option("adaptive_pressure_advance", pa);
        toggle_option("adaptive_pressure_advance_overhangs", pa);
        const bool has_adaptive_pa = m_config->opt_bool("adaptive_pressure_advance", 0);
        toggle_line("adaptive_pressure_advance_overhangs", has_adaptive_pa && pa);
        toggle_line("adaptive_pressure_advance_model", has_adaptive_pa && pa);
        toggle_line("adaptive_pressure_advance_bridges", has_adaptive_pa && pa);

        const bool is_pellet_printer = printer_cfg.opt_bool("pellet_modded_printer");
        toggle_line("pellet_flow_coefficient", is_pellet_printer);
        toggle_line("filament_diameter", !is_pellet_printer);

        toggle_line("activate_chamber_temp_control", printer_cfg.opt_bool("support_chamber_temp_control"));

        const unsigned int variant_idx = (unsigned int) std::max(m_state.variant, 0);
        const std::string volumetric_speed_cos = m_config->opt_string("volumetric_speed_coefficients", variant_idx);
        const bool enable_fit = volumetric_speed_cos != "0 0 0 0 0 0";
        toggle_option("filament_adaptive_volumetric_speed", enable_fit, int(256 + variant_idx));
    }

    if (m_active_page->title() == "Setting Overrides")
        update_filament_overrides_page(&printer_cfg);

    if (m_active_page->title() == "Multimaterial") {
        // Orca: hide specific settings for BBL printers
        for (auto el : {"filament_minimal_purge_on_wipe_tower", "filament_loading_speed_start", "filament_loading_speed",
                        "filament_unloading_speed_start", "filament_unloading_speed", "filament_toolchange_delay", "filament_cooling_moves",
                        "filament_cooling_initial_speed", "filament_cooling_final_speed"})
            toggle_option(el, !is_BBL_printer);

        const bool multitool_ramming = m_config->opt_bool("filament_multitool_ramming", 0);
        toggle_option("filament_multitool_ramming_volume", multitool_ramming);
        toggle_option("filament_multitool_ramming_flow", multitool_ramming);

        const bool is_BBL_multi_extruder = is_BBL_printer && printer_cfg.option<ConfigOptionFloats>("nozzle_diameter")->size() > 1;
        const int extruder_idx = std::max(m_state.variant, 0);
        toggle_line("long_retractions_when_ec", is_BBL_multi_extruder, 256 + extruder_idx);
        toggle_line("retraction_distances_when_ec", is_BBL_multi_extruder && m_config->opt_bool("long_retractions_when_ec", extruder_idx),
                    256 + extruder_idx);
    }
}

void TabFilament::update()
{
    if (m_preset_bundle->printers.get_selected_preset().printer_technology() == ptSLA)
        return; // ys_FIXME

    m_config_manipulation.check_filament_max_volumetric_speed(m_config);

    m_update_cnt++;

    toggle_options();

    m_update_cnt--;

    if (m_update_cnt == 0) {
        // MainFrame::on_config_changed(): the plater takes the settings.
        m_plater_config.apply(*m_config);
    }
}

void TabFilament::init_options_list()
{
    if (!m_options_list.empty())
        m_options_list.clear();

    for (const std::string& opt_key : m_config->keys()) {
        if (filament_options_with_variant.find(opt_key) == filament_options_with_variant.end())
            m_options_list.emplace(opt_key, m_opt_status_value);
        else
            m_options_list.emplace(opt_key + "#0", m_opt_status_value);
    }
}

}  // namespace orcinus::orca::detail
