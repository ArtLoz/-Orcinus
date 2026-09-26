#include "tab_printer.hpp"

#include <algorithm>

#include <boost/algorithm/string/predicate.hpp>

#include "config_manipulation.hpp"
#include "libslic3r/GCode/Thumbnails.hpp"
#include "settings_fields.hpp"

// TabPrinter of the desktop app (Tab.cpp): the pages of the printer tab, the
// pages it builds for the extruders and the motion ability, and the fields it
// enables. The layout is the upstream build_fff() with its statements in order.

namespace orcinus::orca::detail {

using namespace Slic3r;

std::vector<InputShaperType> input_shaper_types_for_flavor(const GCodeFlavor flavor)
{
    switch (flavor) {
    case GCodeFlavor::gcfKlipper:
        return {
            InputShaperType::Default,
            InputShaperType::ZV,
            InputShaperType::MZV,
            InputShaperType::ZVD,
            InputShaperType::EI,
            InputShaperType::TwoHumpEI,
            InputShaperType::ThreeHumpEI,
            InputShaperType::Disable
        };
    case GCodeFlavor::gcfRepRapFirmware:
        return {
            InputShaperType::Default,
            InputShaperType::MZV,
            InputShaperType::ZVD,
            InputShaperType::ZVDD,
            InputShaperType::ZVDDD,
            InputShaperType::EI2,
            InputShaperType::EI3,
            InputShaperType::DAA,
            InputShaperType::Disable
        };
    case GCodeFlavor::gcfMarlinFirmware:
        return {
            InputShaperType::ZV,
            InputShaperType::Disable
        };
    default:
        return {
            InputShaperType::Default,
            InputShaperType::Disable
        };
    }
}

void TabPrinter::build()
{
    m_presets = &m_preset_bundle->printers;

    // The engine prints with FFF printers only; the desktop app would build the
    // pages of the other technology here as well.
    load_initial_data();
    build_fff();
}

void TabPrinter::build_fff()
{
    if (!m_pages.empty())
        m_pages.resize(0);

    const auto* nozzle_diameter = dynamic_cast<const ConfigOptionFloats*>(m_config->option("nozzle_diameter"));
    m_extruders_count = nozzle_diameter->values.size();
    m_extruder_variant_list = m_config->option<ConfigOptionStrings>("printer_extruder_variant")->values;

    const Preset* parent_preset = m_presets->get_selected_preset_parent();
    m_state.sys_extruders_count = parent_preset == nullptr
        ? 0
        : static_cast<const ConfigOptionFloats*>(parent_preset->config.option("nozzle_diameter"))->values.size();

    auto page = add_options_page("Basic information", "custom-gcode_object-info"); // ORCA: icon only visible on placeholders
    auto optgroup = page->new_optgroup("Printable space", "param_printable_space");

        create_line_with_widget(optgroup.get(), "printable_area", "custom-svg-and-png-bed-textures_124612", SettingWidget::bed_shape);
        optgroup->append_single_option_line("parallel_printheads_count");
        Option option = optgroup->get_option("bed_exclude_area");
        option.opt.full_width = true;
        optgroup->append_single_option_line(option, "printer_basic_information_printable_space#excluded-bed-area");
        // optgroup->append_single_option_line("printable_area");
        optgroup->append_single_option_line("printable_height", "printer_basic_information_printable_space#printable-height");
        optgroup->append_single_option_line("support_multi_bed_types","printer_basic_information_printable_space#support-multi-bed-types");
        optgroup->append_single_option_line("best_object_pos", "printer_basic_information_printable_space#best-object-position");
        // todo: for multi_extruder test
        optgroup->append_single_option_line("z_offset", "printer_basic_information_printable_space#z-offset");
        optgroup->append_single_option_line("preferred_orientation", "printer_basic_information_printable_space#preferred-orientation");

        optgroup = page->new_optgroup("Advanced", "param_advanced");

        optgroup->append_single_option_line("printer_structure", "printer_basic_information_advanced#printer-structure");
        optgroup->append_single_option_line("gcode_flavor", "printer_basic_information_advanced#g-code-flavor");
        optgroup->append_single_option_line("pellet_modded_printer", "printer_basic_information_advanced#pellet-modded-printer");
        optgroup->append_single_option_line("bbl_use_printhost", "printer_basic_information_advanced#use-3rd-party-print-host");
        optgroup->append_single_option_line("use_3mf");
        optgroup->append_single_option_line("scan_first_layer" , "printer_basic_information_advanced#scan-first-layer");
        optgroup->append_single_option_line("enable_power_loss_recovery", "printer_basic_information_advanced#power-loss-recovery");
        optgroup->append_single_option_line("disable_m73", "printer_basic_information_advanced#disable-set-remaining-print-time");
        option = optgroup->get_option("thumbnails");
        option.opt.full_width = true;
        optgroup->append_single_option_line(option, "printer_basic_information_advanced#g-code-thumbnails");
        // optgroup->append_single_option_line("thumbnails_format");
        optgroup->m_on_change = [this](const std::string& opt_key, const boost::any& value) {
            // wxTheApp->CallAfter(): the request runs the change to its end anyway.
            if (opt_key == "thumbnails" && m_config->has("thumbnails_format")) {
                // to backward compatibility we need to update "thumbnails_format" from new "thumbnails"
                const std::string val = boost::any_cast<std::string>(value);
                if (!value.empty()) {
                    auto [thumbnails_list, errors] = GCodeThumbnails::make_and_check_thumbnail_list(val);

                    if (errors != enum_bitmask<ThumbnailError>()) {
                        // TRN: The first argument is the parameter's name; the second argument is its value.
                        std::vector<UiText> error_str = {ui_text("Invalid value provided for parameter %1%: %2%", {"thumbnails", val})};
                        error_str.push_back(ui_text(GCodeThumbnails::get_error_string(errors)));
                        m_dialogs.inform("thumbnails_invalid", error_str, {ui_text("G-code flavor is switched")}, DialogIcon::info);
                    }

                    if (!thumbnails_list.empty()) {
                        GCodeThumbnailsFormat old_format = GCodeThumbnailsFormat(m_config->option("thumbnails_format")->getInt());
                        GCodeThumbnailsFormat new_format = thumbnails_list.begin()->first;
                        if (old_format != new_format) {
                            DynamicPrintConfig new_conf = *m_config;

                            auto* opt = m_config->option("thumbnails_format")->clone();
                            opt->setInt(int(new_format));
                            new_conf.set_key_value("thumbnails_format", opt);

                            load_config(new_conf);
                        }
                    }
                }
            }

            update_dirty();
            on_value_change(opt_key, value);
        };

        optgroup->append_single_option_line("use_relative_e_distances", "printer_basic_information_advanced#use-relative-e-distances");
        optgroup->append_single_option_line("use_firmware_retraction", "printer_basic_information_advanced#use-firmware-retraction");
        // optgroup->append_single_option_line("spaghetti_detector");
        optgroup->append_single_option_line("time_cost", "printer_basic_information_advanced#time-cost");

        optgroup  = page->new_optgroup("Cooling Fan", "param_cooling_fan");
        Line line = Line{ "Fan speed-up time", optgroup->get_option("fan_speedup_time").opt.tooltip };
        line.label_path = "printer_basic_information_cooling_fan#fan-speed-up-time";
        line.append_option(optgroup->get_option("fan_speedup_time"));
        line.append_option(optgroup->get_option("fan_speedup_overhangs"));
        optgroup->append_line(line);
        optgroup->append_single_option_line("fan_kickstart", "printer_basic_information_cooling_fan#fan-kick-start-time");
        // ORCA: PWM floor for fans that won't spool at low duty cycles.
        optgroup->append_single_option_line("part_cooling_fan_min_pwm", "printer_basic_information_cooling_fan#minimum-non-zero-part-cooling-fan-speed");

        optgroup = page->new_optgroup("Extruder Clearance", "param_extruder_clearance");
        optgroup->append_single_option_line("extruder_clearance_radius", "printer_basic_information_extruder_clearance#radius");
        optgroup->append_single_option_line("extruder_clearance_height_to_rod", "printer_basic_information_extruder_clearance#height-to-rod");
        optgroup->append_single_option_line("extruder_clearance_height_to_lid", "printer_basic_information_extruder_clearance#height-to-lid");

        optgroup = page->new_optgroup("Adaptive bed mesh", "param_adaptive_mesh");
        optgroup->append_single_option_line("bed_mesh_min", "printer_basic_information_adaptive_bed_mesh#bed-mesh");
        optgroup->append_single_option_line("bed_mesh_max", "printer_basic_information_adaptive_bed_mesh#bed-mesh");
        optgroup->append_single_option_line("bed_mesh_probe_distance", "printer_basic_information_adaptive_bed_mesh#probe-point-distance");
        optgroup->append_single_option_line("adaptive_bed_mesh_margin", "printer_basic_information_adaptive_bed_mesh#mesh-margin");

        optgroup = page->new_optgroup("Accessory", "param_accessory");
        optgroup->append_single_option_line("nozzle_type", "printer_basic_information_accessory#nozzle-type");
        optgroup->append_single_option_line("nozzle_hrc", "printer_basic_information_accessory#nozzle-hrc");
        optgroup->append_single_option_line("auxiliary_fan", "printer_basic_information_accessory#auxiliary-part-cooling-fan");
        optgroup->append_single_option_line("support_chamber_temp_control", "printer_basic_information_accessory#support-controlling-chamber-temperature");
        optgroup->append_single_option_line("support_air_filtration", "printer_basic_information_accessory#support-air-filtration");

    const int gcode_field_height = 15; // 150
    const int notes_field_height = 25; // 250
    page = add_options_page("Machine G-code", "custom-gcode_gcode"); // ORCA: icon only visible on placeholders
        optgroup = page->new_optgroup("File header G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("file_start_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = 8;
        optgroup->append_single_option_line(option);

        optgroup = page->new_optgroup("Machine start G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("machine_start_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;//150;
        optgroup->append_single_option_line(option, "printer_machine_gcode#machine-start-g-code");

        optgroup = page->new_optgroup("Machine end G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("machine_end_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;//150;
        optgroup->append_single_option_line(option, "printer_machine_gcode#machine-end-g-code");

        optgroup              = page->new_optgroup("Printing by object G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option                = optgroup->get_option("printing_by_object_gcode");
        option.opt.full_width = true;
        option.opt.is_code    = true;
        option.opt.height     = gcode_field_height; // 150;
        optgroup->append_single_option_line(option, "printer_machine_gcode#printing-by-object-g-code");

        optgroup = page->new_optgroup("Before layer change G-code","param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("before_layer_change_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;//150;
        optgroup->append_single_option_line(option, "printer_machine_gcode#before-layer-change-g-code");

        optgroup = page->new_optgroup("Layer change G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("layer_change_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;//150;
        optgroup->append_single_option_line(option, "printer_machine_gcode#layer-change-g-code");

        optgroup = page->new_optgroup("Timelapse G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("time_lapse_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;//150;
        optgroup->append_single_option_line(option, "printer_machine_gcode#timelapse-g-code");

        optgroup              = page->new_optgroup("Clumping Detection G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option                = optgroup->get_option("wrapping_detection_gcode");
        option.opt.full_width = true;
        option.opt.is_code    = true;
        option.opt.height     = gcode_field_height; // 150;
        optgroup->append_single_option_line(option, "printer_machine_gcode#clumping-detection-g-code");

        optgroup = page->new_optgroup("Change filament G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("change_filament_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;//150;
        optgroup->append_single_option_line(option, "printer_machine_gcode#change-filament-g-code");

        optgroup = page->new_optgroup("Change extrusion role G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("change_extrusion_role_gcode");
        option.opt.full_width = true;
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;//150;
        optgroup->append_single_option_line(option, "printer_machine_gcode#change-extrusion-role-g-code");

        optgroup = page->new_optgroup("Pause G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("machine_pause_gcode");
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;//150;
        optgroup->append_single_option_line(option, "printer_machine_gcode#pause-g-code");

        optgroup = page->new_optgroup("Template Custom G-code", "param_gcode", 0);
        optgroup->m_on_change = [this, optgroup_title = optgroup->title](const std::string& opt_key, const boost::any& value) {
            validate_custom_gcode_cb(this, optgroup_title, opt_key, value);
        };
        optgroup->edit_custom_gcode = true;
        option = optgroup->get_option("template_custom_gcode");
        option.opt.is_code = true;
        option.opt.height = gcode_field_height;//150;
        optgroup->append_single_option_line(option, "printer_machine_gcode#template-custom-g-code");

    page = add_options_page("Notes", "custom-gcode_note"); // ORCA: icon only visible on placeholders
        optgroup = page->new_optgroup("Notes", "note", 0);
        option = optgroup->get_option("printer_notes");
        option.opt.full_width = true;
        option.opt.height = notes_field_height;//250;
        optgroup->append_single_option_line(option);

    build_unregular_pages(true);
}

void TabPrinter::extruders_count_changed(const std::size_t extruders_count)
{
    bool is_count_changed = false;
    if (m_extruders_count != extruders_count) {
        m_extruders_count = extruders_count;
        m_preset_bundle->on_extruders_count_changed(int(extruders_count));
        is_count_changed = true;
    }
    // Orca: support multi tool
    else if (m_extruders_count == 1 && m_preset_bundle->project_config.option<ConfigOptionFloats>("flush_volumes_matrix")->values.size() > 1)
        m_preset_bundle->update_multi_material_filament_presets();

    /* This function should be call in any case because of correct updating/rebuilding
     * of unregular pages of a Printer Settings
     */
    build_unregular_pages();

    if (is_count_changed) {
        on_value_change("extruders_count", extruders_count);
    }
}

void TabPrinter::append_option_line(const ConfigOptionsGroupShp& optgroup, const std::string& opt_key, const std::string& label_path)
{
    auto option = optgroup->get_option(opt_key, 0);
    auto line = Line{option.opt.full_label, ""};
    line.label_path = label_path;
    line.append_option(option);
    if (m_state.use_silent_mode)
        line.append_option(optgroup->get_option(opt_key, 1));
    optgroup->append_line(line);
}

PageShp TabPrinter::build_kinematics_page()
{
    auto page = add_options_page("Motion ability", "custom-gcode_motion", true); // ORCA: icon only visible on placeholders

    if (m_state.use_silent_mode) {
        // Legend for OptionsGroups
        auto optgroup = page->new_optgroup("");
        auto line = Line{ "", "" };

        ConfigOptionDef def;
        def.type = coString;
        def.gui_type = ConfigOptionDef::GUIType::legend;
        def.mode = comDevelop;
        //def.tooltip = L("Values in this column are for Normal mode");
        def.set_default_value(new ConfigOptionString{"Normal"});

        auto option = Option(def, "full_power_legend");
        line.append_option(option);

        //def.tooltip = L("Values in this column are for Stealth mode");
        def.set_default_value(new ConfigOptionString{"Silent"});
        option = Option(def, "silent_legend");
        line.append_option(option);

        optgroup->append_line(line);
    }
    auto optgroup = page->new_optgroup("Advanced", "param_advanced");
    optgroup->append_single_option_line("emit_machine_limits_to_gcode", "printer_motion_ability#emit-limits-to-g-code");

    // resonance avoidance ported over from qidi slicer
    optgroup = page->new_optgroup("Resonance Compensation", "param_resonance_avoidance");
    optgroup->append_single_option_line("resonance_avoidance", "printer_motion_ability#resonance-avoidance");
    // Resonance-avoidance speed inputs
    {
        Line resonance_line = {"Resonance Avoidance Speed", ""};
        resonance_line.label_path = "printer_motion_ability#resonance-avoidance";
        resonance_line.append_option(optgroup->get_option("min_resonance_avoidance_speed"));
        resonance_line.append_option(optgroup->get_option("max_resonance_avoidance_speed"));
        optgroup->append_line(resonance_line);
    }
    optgroup->append_single_option_line("input_shaping_emit", "printer_motion_ability#input-shaping");
    optgroup->append_single_option_line("input_shaping_type", "printer_motion_ability#input-shaping-type");
    {
        Line freq_line = {"Frequency", "The frequency of the anti-vibration signal will correspond to the natural frequency of the frame."};
        freq_line.label_path = "printer_motion_ability#input-shaping";
        freq_line.append_option(optgroup->get_option("input_shaping_freq_x"));
        freq_line.append_option(optgroup->get_option("input_shaping_freq_y"));
        optgroup->append_line(freq_line);
    }
    {
        Line damping_line = {"Damping", "Damping ratio for the input shaping filter."};
        damping_line.label_path = "printer_motion_ability#input-shaping";
        damping_line.append_option(optgroup->get_option("input_shaping_damp_x"));
        damping_line.append_option(optgroup->get_option("input_shaping_damp_y"));
        optgroup->append_line(damping_line);
    }

    const std::vector<std::string> speed_axes{
        "machine_max_speed_x",
        "machine_max_speed_y",
        "machine_max_speed_z",
        "machine_max_speed_e"
    };
    optgroup = page->new_optgroup("Speed limitation", "param_speed");
        for (const std::string& speed_axis : speed_axes) {
            append_option_line(optgroup, speed_axis, "printer_motion_ability#speed-limitation");
        }

    const std::vector<std::string> axes{ "x", "y", "z", "e" };
    optgroup = page->new_optgroup("Acceleration limitation", "param_acceleration");
        for (const std::string& axis : axes) {
            append_option_line(optgroup, "machine_max_acceleration_" + axis, "printer_motion_ability#acceleration-limitation");
        }
        append_option_line(optgroup, "machine_max_acceleration_extruding", "printer_motion_ability#acceleration-limitation");
        append_option_line(optgroup, "machine_max_acceleration_retracting", "printer_motion_ability#acceleration-limitation");
        append_option_line(optgroup, "machine_max_acceleration_travel", "printer_motion_ability#acceleration-limitation");

        optgroup = page->new_optgroup("Jerk limitation", "param_jerk");
        // machine max junction deviation
        append_option_line(optgroup, "machine_max_junction_deviation", "printer_motion_ability#maximum-junction-deviation");
        for (const std::string& axis : axes) {
            append_option_line(optgroup, "machine_max_jerk_" + axis, "printer_motion_ability#maximum-jerk");
        }

    return page;
}

/* Previous name build_extruder_pages().
 *
 * This function was renamed because of now it implements not just an extruder pages building,
 * but "Motion ability" and "Single extruder MM setup" too
 * (These pages can changes according to the another values of a current preset)
 * */
void TabPrinter::build_unregular_pages(const bool from_initial_build)
{
    std::size_t n_before_extruders = 2;         //	Count of pages before Extruder pages
    const auto flavor = m_config->option<ConfigOptionEnum<GCodeFlavor>>("gcode_flavor")->value;
    const bool is_marlin_flavor = (flavor == gcfMarlinLegacy || flavor == gcfMarlinFirmware || flavor == gcfKlipper || flavor == gcfRepRapFirmware
                                   || flavor == gcfRepetier);

    // Add/delete Kinematics page according to is_marlin_flavor
    std::size_t existed_page = 0;
    for (std::size_t i = n_before_extruders; i < m_pages.size(); ++i) // first make sure it's not there already
        if (m_pages[i]->title().find("Motion ability") != std::string::npos) {
            if (m_rebuild_kinematics_page)
                m_pages.erase(m_pages.begin() + i);
            else
                existed_page = i;
            break;
        }

    if (existed_page < n_before_extruders && (is_marlin_flavor || from_initial_build)) {
        auto page = build_kinematics_page();
        if (!(from_initial_build && !is_marlin_flavor))
            m_pages.insert(m_pages.begin() + n_before_extruders, page);
    }

if (is_marlin_flavor)
    n_before_extruders++;
    const std::size_t n_after_single_extruder_MM = 2; //	Count of pages after single_extruder_multi_material page

    if (from_initial_build) {
        // create a page, but pretend it's an extruder page, so we can add it to m_pages ourselves
        auto page     = add_options_page("Multimaterial", "custom-gcode_multi_material", true); // ORCA: icon only visible on placeholders
        auto optgroup = page->new_optgroup("Single extruder multi-material setup", "param_multi_material");
        optgroup->append_single_option_line("single_extruder_multi_material", "printer_multimaterial_setup#single-extruder-multi-material");
        ConfigOptionDef def;
        def.type    = coInt, def.set_default_value(new ConfigOptionInt((int) m_extruders_count));
        def.label   = "Extruders";
        def.tooltip = "Number of extruders of the printer.";
        def.min     = 1;
        def.max     = MAXIMUM_EXTRUDER_NUMBER;
        def.mode    = comAdvanced;
        def.opt_key = "extruders_count";
        Option option(def, "extruders_count");
        optgroup->append_single_option_line(option, "printer_multimaterial_setup#extruders");

        // Orca: rebuild missed extruder pages
        optgroup->m_on_change = [this](const std::string& opt_key, const boost::any& value) {
            if (opt_key == "extruders_count" || opt_key == "single_extruder_multi_material") {
                const std::size_t extruders_count = opt_key == "extruders_count" ? std::size_t(boost::any_cast<int>(value)) : m_extruders_count;
                extruders_count_changed(extruders_count);
                init_options_list(); // m_options_list should be updated before UI updating
                update_dirty();
                if (opt_key == "single_extruder_multi_material") { // the single_extruder_multimaterial was added to force pages
                    on_value_change(opt_key, value);               // rebuild - let's make sure the on_value_change is not skipped

                    if (boost::any_cast<bool>(value) && m_extruders_count > 1) {
                        // Orca: we use a different logic here. If SEMM is enabled, we set extruder count to 1.
                        extruders_count_changed(1);
                    }

                    m_preset_bundle->update_compatible(PresetSelectCompatibleType::Never);
                }
            }
            else {
                update_dirty();
                on_value_change(opt_key, value);
            }
        };
        optgroup->append_single_option_line("manual_filament_change", "printer_multimaterial_setup#manual-filament-change");
        optgroup->append_single_option_line("bed_temperature_formula", "printer_basic_information_advanced#bed-temperature-type");

        optgroup = page->new_optgroup("Wipe tower", "param_tower");
        optgroup->append_single_option_line("wipe_tower_type", "printer_multimaterial_wipe_tower");
        optgroup->append_single_option_line("purge_in_prime_tower", "printer_multimaterial_wipe_tower#purge-in-prime-tower");
        optgroup->append_single_option_line("enable_filament_ramming", "printer_multimaterial_wipe_tower#enable-filament-ramming");
        optgroup->append_single_option_line("tool_change_on_wipe_tower", "printer_multimaterial_wipe_tower#tool-change-on-wipe-tower");


        optgroup = page->new_optgroup("Single extruder multi-material parameters", "param_settings");
        optgroup->append_single_option_line("cooling_tube_retraction", "printer_multimaterial_semm_parameters#cooling-tube-position");
        optgroup->append_single_option_line("cooling_tube_length", "printer_multimaterial_semm_parameters#cooling-tube-length");
        optgroup->append_single_option_line("parking_pos_retraction", "printer_multimaterial_semm_parameters#filament-parking-position");
        optgroup->append_single_option_line("extra_loading_move", "printer_multimaterial_semm_parameters#extra-loading-distance");
        optgroup->append_single_option_line("high_current_on_filament_swap", "printer_multimaterial_semm_parameters#high-extruder-current-on-filament-swap");

        optgroup = page->new_optgroup("Advanced", "param_advanced");
        optgroup->append_single_option_line("machine_load_filament_time", "printer_multimaterial_advanced#filament-load-time");
        optgroup->append_single_option_line("machine_unload_filament_time", "printer_multimaterial_advanced#filament-unload-time");
        optgroup->append_single_option_line("machine_tool_change_time", "printer_multimaterial_advanced#tool-change-time");
        m_pages.insert(m_pages.end() - n_after_single_extruder_MM, page);
    }

    // Orca: build missed extruder pages
    for (std::size_t extruder_idx = m_extruders_count_old; extruder_idx < m_extruders_count; ++extruder_idx) {
        const std::string page_name = (m_extruders_count > 1) ? "Extruder " + std::to_string(extruder_idx + 1) : std::string("Extruder");

        //# build page
        auto page = add_options_page(page_name, "custom-gcode_extruder", true); // ORCA: icon only visible on placeholders
        m_pages.insert(m_pages.begin() + n_before_extruders + extruder_idx, page);

        auto optgroup = page->new_optgroup("Basic information", "param_information", -1, true);
            optgroup->append_single_option_line("nozzle_diameter", "printer_extruder_basic_information#nozzle-diameter", int(extruder_idx));

            optgroup->append_single_option_line("nozzle_volume", "printer_extruder_basic_information#nozzle-volume", int(extruder_idx));
            optgroup->append_single_option_line("extruder_printable_height", "printer_extruder_basic_information#extruder-layer-height-limits", int(extruder_idx));
            Option option         = optgroup->get_option("extruder_printable_area", int(extruder_idx));
            option.opt.full_width = true;
            optgroup->append_single_option_line(option, "printer_extruder_basic_information#extruder-offset-position");

            optgroup->m_on_change = [this, extruder_idx](const std::string& opt_key, const boost::any& value)
            {
                const bool is_SEMM = m_config->opt_bool("single_extruder_multi_material");
                if (is_SEMM && m_extruders_count > 1 && opt_key.find_first_of("nozzle_diameter") != std::string::npos)
                {
                    const double new_nd = boost::any_cast<double>(value);
                    std::vector<double> nozzle_diameters = static_cast<const ConfigOptionFloats*>(m_config->option("nozzle_diameter"))->values;

                    // if value was changed
                    if (fabs(nozzle_diameters[extruder_idx == 0 ? 1 : 0] - new_nd) > EPSILON)
                    {
                        DynamicPrintConfig new_conf = *m_config;
                        if (m_dialogs.ask("nozzle_diameter_semm",
                                          {ui_text("This is a single extruder multi-material printer, diameters of all extruders will be set to the "
                                                   "new value. Do you want to proceed?")},
                                          {ui_text("Nozzle diameter")})) {
                            for (std::size_t i = 0; i < nozzle_diameters.size(); i++) {
                                if (i == extruder_idx)
                                    continue;
                                nozzle_diameters[i] = new_nd;
                            }
                        }
                        else
                            nozzle_diameters[extruder_idx] = nozzle_diameters[extruder_idx == 0 ? 1 : 0];

                        new_conf.set_key_value("nozzle_diameter", new ConfigOptionFloats(nozzle_diameters));
                        load_config(new_conf);
                    }
                }

                update_dirty();
                on_value_change(opt_key, value);
                update();
            };

            optgroup = page->new_optgroup("Layer height limits", "param_layer_height");
            optgroup->append_single_option_line("min_layer_height", "printer_extruder_basic_information#extruder-layer-height-limits", int(extruder_idx));
            optgroup->append_single_option_line("max_layer_height", "printer_extruder_basic_information#extruder-layer-height-limits", int(extruder_idx));

            optgroup = page->new_optgroup("Position", "param_position");
            optgroup->append_single_option_line("extruder_offset", "printer_extruder_basic_information#extruder-offset-position", int(extruder_idx));

            //BBS: don't show retract related config menu in machine page
            optgroup = page->new_optgroup("Retraction", "param_retraction");
            optgroup->append_single_option_line("retraction_length", "printer_extruder_retraction#length", int(extruder_idx));
            optgroup->append_single_option_line("retract_restart_extra", "printer_extruder_retraction#extra-length-on-restart", int(extruder_idx));
            optgroup->append_single_option_line("retraction_speed", "printer_extruder_retraction#retraction-speed", int(extruder_idx));
            optgroup->append_single_option_line("deretraction_speed", "printer_extruder_retraction#deretraction-speed", int(extruder_idx));
            optgroup->append_single_option_line("retraction_minimum_travel", "printer_extruder_retraction#travel-distance-threshold", int(extruder_idx));
            optgroup->append_single_option_line("retract_when_changing_layer", "printer_extruder_retraction#retract-on-layer-change", int(extruder_idx));
            optgroup->append_single_option_line("wipe", "printer_extruder_retraction#wipe-while-retracting", int(extruder_idx));
            optgroup->append_single_option_line("wipe_distance", "printer_extruder_retraction#wipe-distance", int(extruder_idx));
            optgroup->append_single_option_line("retract_before_wipe", "printer_extruder_retraction#retract-amount-before-wipe", int(extruder_idx));

            optgroup = page->new_optgroup("Z-Hop", "param_extruder_lift_enforcement");
            optgroup->append_single_option_line("retract_lift_enforce", "printer_extruder_z_hop#on-surfaces", int(extruder_idx));
            optgroup->append_single_option_line("z_hop_types", "printer_extruder_z_hop#z-hop-type", int(extruder_idx));
            optgroup->append_single_option_line("z_hop", "printer_extruder_z_hop#z-hop-height", int(extruder_idx));
            optgroup->append_single_option_line("travel_slope", "printer_extruder_z_hop#traveling-angle", int(extruder_idx));
            optgroup->append_single_option_line("retract_lift_above", "printer_extruder_z_hop#only-lift-z-above", int(extruder_idx));
            optgroup->append_single_option_line("retract_lift_below", "printer_extruder_z_hop#only-lift-z-below", int(extruder_idx));

            optgroup = page->new_optgroup("Retraction when switching material", "param_retraction_material_change");
            optgroup->append_single_option_line("retract_length_toolchange", "printer_extruder_retraction#retraction-when-switching-materials", int(extruder_idx));
            optgroup->append_single_option_line("retract_restart_extra_toolchange", "printer_extruder_retraction#retraction-when-switching-materials", int(extruder_idx));
            // do not display this params now
            optgroup->append_single_option_line("long_retractions_when_cut", "printer_extruder_retraction#long-retraction-when-cut-beta", int(extruder_idx));
            optgroup->append_single_option_line("retraction_distances_when_cut", "printer_extruder_retraction#long-retraction-when-cut-beta", int(extruder_idx));
    }
    // BBS. No extra extruder page for single physical extruder machine
    // # remove extra pages
    if (m_extruders_count < m_extruders_count_old) {
        m_pages.erase(m_pages.begin() + n_before_extruders + m_extruders_count, m_pages.begin() + n_before_extruders + m_extruders_count_old);
        if (m_extruders_count == 1)
            m_pages[n_before_extruders]->set_title("Extruder");
    } else if (m_extruders_count_old == 1 && m_extruders_count > 1) {
        m_pages[n_before_extruders]->set_title("Extruder 1");
    }

    m_extruders_count_old = m_extruders_count;

    // Reload preset pages with current configuration values
    reload_config();
}

// this gets executed after preset is loaded and before GUI fields are updated
void TabPrinter::on_preset_loaded()
{
    // Orca
    //update nozzle_volume_type
    const Preset& current_printer = m_preset_bundle->printers.get_selected_preset();
    const Preset* base_printer = m_preset_bundle->printers.get_preset_base(current_printer);
    if (base_printer == nullptr)
        base_printer = &current_printer;
    const std::string base_name = base_printer->name;
    // update the extruders count field
    const auto* nozzle_diameter = dynamic_cast<const ConfigOptionFloats*>(m_config->option("nozzle_diameter"));
    const std::size_t extruders_count = nozzle_diameter->values.size();
    // update the GUI field according to the number of nozzle diameters supplied
    if (m_extruders_count != extruders_count)
        extruders_count_changed(extruders_count);

    m_extruder_variant_list = m_config->option<ConfigOptionStrings>("printer_extruder_variant")->values;

    if (base_name != m_state.base_preset_name) {
        bool use_default_nozzle_volume_type = true;
        m_state.base_preset_name = base_name;
        const std::string prev_nozzle_volume_type = m_app_config.get_nozzle_volume_types_from_config(base_name);
        if (!prev_nozzle_volume_type.empty()) {
            auto* nozzle_volume_type_option = m_preset_bundle->project_config.option<ConfigOptionEnumsGeneric>("nozzle_volume_type");
            if (nozzle_volume_type_option->deserialize(prev_nozzle_volume_type)) {
                use_default_nozzle_volume_type = false;
            }
        }
        if (use_default_nozzle_volume_type) {
            m_preset_bundle->project_config.option<ConfigOptionEnumsGeneric>("nozzle_volume_type")->values =
                current_printer.config.option<ConfigOptionEnumsGeneric>("default_nozzle_volume_type")->values;
        }
    }
}

void TabPrinter::reload_config()
{
    Tab::reload_config();
}

PresetSettings TabPrinter::describe()
{
    PresetSettings result = Tab::describe();
    // "extruders_count" is no setting of the preset, so it has no value of its
    // own to show; the desktop app pushes the count into the field itself
    // (TabPrinter::reload_config and activate_selected_page).
    for (SettingState& state : result.settings) {
        if (state.key == "extruders_count") {
            state.value = std::to_string(m_extruders_count);
        }
    }
    return result;
}

void TabPrinter::update_input_shaper_menu(const GCodeFlavor flavor)
{
    if (m_presets->get_edited_preset().printer_technology() != ptFFF)
        return;

    const std::vector<InputShaperType> allowed = input_shaper_types_for_flavor(flavor);
    if (allowed.empty())
        return;

    const InputShaperType current = m_config->opt_enum<InputShaperType>("input_shaping_type");
    const bool needs_reset = std::find(allowed.begin(), allowed.end(), current) == allowed.end();
    const InputShaperType desired = needs_reset ? allowed.front() : current;

    if (needs_reset && current != desired) {
        DynamicPrintConfig new_conf = *m_config;
        new_conf.set_key_value("input_shaping_type", new ConfigOptionEnum<InputShaperType>(desired));
        m_config_manipulation.apply(m_config, &new_conf);
    }

    if (m_active_page == nullptr)
        return;
    const std::string field = m_active_page->get_field("input_shaping_type");
    if (field.empty())
        return;

    std::vector<int> set;
    set.reserve(allowed.size());
    for (const InputShaperType type : allowed)
        set.push_back(int(type));
    set_choices(field, "input_shaping_type", set);
}

void TabPrinter::on_gcode_flavor_changed()
{
    const auto* flavor_option = m_config->option<ConfigOptionEnum<GCodeFlavor>>("gcode_flavor");
    if (flavor_option == nullptr)
        return;
    update_input_shaper_menu(flavor_option->value);
}

void TabPrinter::toggle_options()
{
    if (m_active_page == nullptr || m_presets->get_edited_preset().printer_technology() == ptSLA)
        return;

    auto nozzle_volumes = m_preset_bundle->project_config.option<ConfigOptionEnumsGeneric>("nozzle_volume_type");
    auto extruders      = m_config->option<ConfigOptionEnumsGeneric>("extruder_type");
    auto get_index_for_extruder = [this, &extruders, &nozzle_volumes](const int extruder_id, const int stride = 1) {
        return m_config->get_index_for_extruder(extruder_id + 1, "printer_extruder_id", ExtruderType(extruders->values[extruder_id]),
                                                NozzleVolumeType(nozzle_volumes->values[extruder_id]), "printer_extruder_variant", stride);
    };

    //BBS: whether the preset is Bambu Lab printer
    bool is_BBL_printer = false;
    if (m_preset_bundle != nullptr) {
       is_BBL_printer = m_preset_bundle->is_bbl_vendor();
    }

    const bool have_multiple_extruders = true;
    if (m_active_page->title() == "Basic information") {
        const auto& printer_cfg = m_preset_bundle->printers.get_edited_preset().config;

        // SoftFever: hide BBL specific settings
        for (auto el : {"scan_first_layer", "bbl_calib_mark_logo", "bbl_use_printhost"})
            toggle_line(el, is_BBL_printer);

        // SoftFever: hide non-BBL settings
        for (auto el : {"use_firmware_retraction", "use_relative_e_distances", "support_multi_bed_types", "pellet_modded_printer", "bed_mesh_max", "bed_mesh_min", "bed_mesh_probe_distance", "adaptive_bed_mesh_margin", "thumbnails"})
          toggle_line(el, !is_BBL_printer);

        const bool gcf_is_marlin_firmware = m_config->option<ConfigOptionEnum<GCodeFlavor>>("gcode_flavor")->value == GCodeFlavor::gcfMarlinFirmware;
        toggle_line("enable_power_loss_recovery", is_BBL_printer || gcf_is_marlin_firmware);

        const bool support_parallel_printheads = printer_cfg.opt_bool("support_parallel_printheads");
        toggle_line("parallel_printheads_count", support_parallel_printheads);
    }

    if (m_active_page->title() == "Machine G-code") {
        const std::string printer_type = m_preset_bundle->printers.get_edited_preset().get_printer_type(m_preset_bundle);
        toggle_line("wrapping_detection_gcode", support_wrapping_detection(printer_type));
    }

    if (m_active_page->title() == "Multimaterial") {
        const bool supports_wipe_tower_2 = !is_BBL_printer && m_config->opt_enum<WipeTowerType>("wipe_tower_type") == WipeTowerType::Type2;
        toggle_line("wipe_tower_type", !is_BBL_printer);
        // SoftFever: hide specific settings for BBL printer
        for (auto el : {
                 "enable_filament_ramming",
                 "cooling_tube_retraction",
                 "cooling_tube_length",
                 "parking_pos_retraction",
                 "extra_loading_move",
                 "high_current_on_filament_swap",
             })
            toggle_option(el, supports_wipe_tower_2);

        const auto bSEMM = m_config->opt_bool("single_extruder_multi_material");
        if (!bSEMM && m_config->opt_bool("manual_filament_change")) {
            DynamicPrintConfig new_conf = *m_config;
            new_conf.set_key_value("manual_filament_change", new ConfigOptionBool(false));
            load_config(new_conf);
        }
        toggle_option("extruders_count", !bSEMM);
        toggle_option("manual_filament_change", bSEMM);
        toggle_option("purge_in_prime_tower", bSEMM && supports_wipe_tower_2);

        // Orca: "Tool change on wipe tower" only makes sense for multi-extruder (multi-toolhead) printers
        // using a Type 2 wipe tower. SEMM already always travels to the tower as part of the purge,
        // so the option is irrelevant there.
        const std::size_t extruders_count = m_config->option<ConfigOptionFloats>("nozzle_diameter")->size();
        toggle_option("tool_change_on_wipe_tower", !bSEMM && supports_wipe_tower_2 && extruders_count > 1);
    }
    long val = 1;
    if (m_active_page->title() == "Extruder"
        || (boost::starts_with(m_active_page->title(), "Extruder ") && (val = std::atol(m_active_page->title().substr(9).c_str())) > 0
            && std::size_t(val) <= m_extruders_count))
    {
        const std::size_t i = std::size_t(val - 1);
        const int variant_index = get_index_for_extruder(int(i));
        const bool have_retract_length = m_config->opt_float("retraction_length", variant_index) > 0;

        toggle_option("extruder_printable_area", false, int(i));          // disable
        toggle_line("extruder_printable_area", m_preset_bundle->get_printer_extruder_count() == 2, int(i));  //hide
        toggle_option("extruder_printable_height", false, int(i));
        toggle_line("extruder_printable_height", m_preset_bundle->get_printer_extruder_count() == 2, int(i));

        // when using firmware retraction, firmware decides retraction length
        const bool use_firmware_retraction = m_config->opt_bool("use_firmware_retraction");
        toggle_option("retract_length", !use_firmware_retraction, int(i));

        // user can customize travel length if we have retraction length or we"re using
        // firmware retraction
        toggle_option("retraction_minimum_travel", have_retract_length || use_firmware_retraction, int(i));

        // user can customize other retraction options if retraction is enabled
        //BBS
        const bool retraction = have_retract_length || use_firmware_retraction;
        std::vector<std::string> vec = {"z_hop", "retract_when_changing_layer"};
        for (auto el : vec)
            toggle_option(el, retraction, int(i));

        // retract lift above / below + enforce only applies if using retract lift
        vec.resize(0);
        vec = {"retract_lift_above", "retract_lift_below", "retract_lift_enforce"};
        for (auto el : vec)
          toggle_option(el, retraction && (m_config->opt_float("z_hop", i) > 0), int(i));

        // some options only apply when not using firmware retraction
        vec.resize(0);
        vec = {"retraction_speed", "deretraction_speed",    "retract_before_wipe",
               "retract_length",   "retract_restart_extra",
               "wipe_distance"};
        for (auto el : vec)
            //BBS
            toggle_option(el, retraction && !use_firmware_retraction, int(i));

        const bool wipe = retraction && m_config->opt_bool("wipe", variant_index);
        toggle_option("retract_before_wipe", wipe, int(i));
        const float retract_before_wipe = static_cast<const ConfigOptionPercents*>(m_config->option("retract_before_wipe"))->values[variant_index];

        if (use_firmware_retraction && wipe && retract_before_wipe < 100.0) {
            DynamicPrintConfig new_conf = *m_config;
            if (m_dialogs.ask("firmware_retraction_wipe",
                              {ui_text("The Retract before wipe option could be only 100% when using the Firmware Retraction mode.\n"
                                       "\nShall I set it to 100% in order to enable Firmware Retraction?")},
                              {ui_text("Firmware Retraction")})) {
                auto* wipe_option = static_cast<ConfigOptionBools*>(m_config->option("wipe")->clone());
                auto* retract_before_wipe_option = static_cast<ConfigOptionPercents*>(m_config->option("retract_before_wipe")->clone());
                for (std::size_t w = 0; w < wipe_option->values.size(); w++) {
                    wipe_option->values[w] = false;
                    retract_before_wipe_option->values[w] = 100.0;
                }
                new_conf.set_key_value("wipe", wipe_option);
                new_conf.set_key_value("retract_before_wipe", retract_before_wipe_option);
            }
            else {
                new_conf.set_key_value("use_firmware_retraction", new ConfigOptionBool(false));
            }
            load_config(new_conf);
        }
        // BBS
        toggle_option("wipe_distance", wipe, int(i));

        toggle_option("retract_length_toolchange", have_multiple_extruders, int(i));

        const bool toolchange_retraction = m_config->opt_float("retract_length_toolchange", variant_index) > 0;
        toggle_option("retract_restart_extra_toolchange", have_multiple_extruders && toolchange_retraction, int(i));

        toggle_option("long_retractions_when_cut", !use_firmware_retraction && m_config->opt_int("enable_long_retraction_when_cut"), int(i));
        toggle_line("retraction_distances_when_cut", m_config->opt_bool("long_retractions_when_cut", variant_index), int(i));

        toggle_option("travel_slope", ZHopType(m_config->opt_enum("z_hop_types", (unsigned int) i)) != ZHopType::zhtNormal, int(i));
    }

    if (m_active_page->title() == "Motion ability") {
        const auto gcf = m_config->option<ConfigOptionEnum<GCodeFlavor>>("gcode_flavor")->value;
        update_input_shaper_menu(gcf);

        // Orca: use booleans to avoid repeated comparisons with enum values
        const bool gcf_is_marlin_legacy = gcf == GCodeFlavor::gcfMarlinLegacy;
        const bool gcf_is_marlin_firmware = gcf == GCodeFlavor::gcfMarlinFirmware;
        const bool gcf_is_klipper = gcf == GCodeFlavor::gcfKlipper;
        const bool gcf_is_reprap_firmware = gcf == GCodeFlavor::gcfRepRapFirmware;

        const bool silent_mode = m_config->opt_bool("silent_mode");
        const int max_field = silent_mode ? 2 : 1;
        for (int i = 0; i < max_field; ++i)
            toggle_option("machine_max_acceleration_travel", !gcf_is_marlin_legacy && !gcf_is_klipper, i);
        toggle_line("machine_max_acceleration_travel", !gcf_is_marlin_legacy && !gcf_is_klipper);
        for (int i = 0; i < max_field; ++i)
            toggle_option("machine_max_junction_deviation", gcf_is_marlin_firmware, i);
        toggle_line("machine_max_junction_deviation", gcf_is_marlin_firmware);

        // Check if junction deviation value is non-zero and firmware is Marlin
        bool enable_jerk = !gcf_is_marlin_firmware;
        if (gcf_is_marlin_firmware) {
            const auto* junction_deviation = m_config->option<ConfigOptionFloats>("machine_max_junction_deviation");
            if (junction_deviation != nullptr) {
                const auto& values = junction_deviation->values;
                enable_jerk = std::all_of(values.begin(), values.end(), [](const double val) { return val == 0.0; });
            } else {
                enable_jerk = true;
            }
        }
        for (int i = 0; i < max_field; ++i) {
            toggle_option("machine_max_jerk_x", enable_jerk, i);
            toggle_option("machine_max_jerk_y", enable_jerk, i);
            toggle_option("machine_max_jerk_z", enable_jerk, i);
            toggle_option("machine_max_jerk_e", enable_jerk, i);
        }

        const bool emittable_limits = gcf_is_marlin_legacy || gcf_is_marlin_firmware || gcf_is_reprap_firmware;
        toggle_option("emit_machine_limits_to_gcode", emittable_limits);

        const bool resonance_avoidance = m_config->opt_bool("resonance_avoidance");
        toggle_option("min_resonance_avoidance_speed", resonance_avoidance);
        toggle_option("max_resonance_avoidance_speed", resonance_avoidance);

        const bool input_shaping_compatible = gcf_is_marlin_firmware || gcf_is_reprap_firmware;

        for (auto is : {"input_shaping_emit", "input_shaping_type", "input_shaping_freq_x", "input_shaping_freq_y",
                            "input_shaping_damp_x", "input_shaping_damp_y"})
                toggle_line(is, input_shaping_compatible);

        if (input_shaping_compatible) {
            const bool emit_machine_limits_to_gcode = m_config->opt_bool("emit_machine_limits_to_gcode");
            toggle_option("input_shaping_emit", emit_machine_limits_to_gcode);
            const bool input_shaping_emit = emit_machine_limits_to_gcode && m_config->opt_bool("input_shaping_emit");
            toggle_option("input_shaping_type", input_shaping_emit);
            toggle_option("input_shaping_freq_x", input_shaping_emit);
            toggle_option("input_shaping_freq_y", input_shaping_emit && !gcf_is_reprap_firmware);
            toggle_option("input_shaping_damp_x", input_shaping_emit);
            toggle_option("input_shaping_damp_y", input_shaping_emit && !gcf_is_reprap_firmware);
        }
    }
}

void TabPrinter::update()
{
    m_update_cnt++;
    if (m_presets->get_edited_preset().printer_technology() == ptFFF)
        update_fff();
    m_update_cnt--;

    if (m_update_cnt == 0) {
        // MainFrame::on_config_changed(): the plater takes the settings.
        m_plater_config.apply(*m_config);
    }
}

void TabPrinter::update_fff()
{
    if (m_state.use_silent_mode != m_config->opt_bool("silent_mode")) {
        m_rebuild_kinematics_page = true;
        m_state.use_silent_mode = m_config->opt_bool("silent_mode");
        // The pages the app shows are built again with the second column.
        build_unregular_pages();
        m_rebuild_kinematics_page = false;
    }

    toggle_options();
}

void TabPrinter::init_options_list()
{
    Tab::init_options_list();
    if (m_presets->get_edited_preset().printer_technology() == ptFFF)
        m_options_list.emplace("extruders_count", m_opt_status_value);
    for (std::size_t i = 1; i < m_extruders_count; ++i) {
        const std::string target_title = "Extruder " + std::to_string(i + 1);
        for (const PageShp& page : m_pages) {
            if (page->title() == target_title) {
                for (const ConfigOptionsGroupShp& group : page->m_optgroups) {
                    for (const auto& opt : group->opt_map())
                        m_options_list.emplace(opt.first, m_opt_status_value);
                }
                break;
            }
        }
    }
}

void TabPrinter::cache_extruder_cnt(const DynamicPrintConfig* config)
{
    const DynamicPrintConfig& cached_config = config != nullptr ? *config : m_presets->get_edited_preset().config;
    if (Preset::printer_technology(cached_config) == ptSLA)
        return;

    // get extruders count
    const auto* nozzle_diameter = dynamic_cast<const ConfigOptionFloats*>(cached_config.option("nozzle_diameter"));
    m_state.cache_extruder_count = nozzle_diameter->values.size(); //m_extruders_count;
}

bool TabPrinter::apply_extruder_cnt_from_cache()
{
    if (m_presets->get_edited_preset().printer_technology() == ptSLA)
        return false;

    if (m_state.cache_extruder_count > 0) {
        m_presets->get_edited_preset().set_num_extruders(m_state.cache_extruder_count);
        m_state.cache_extruder_count = 0;
        return true;
    }
    return false;
}

}  // namespace orcinus::orca::detail
