#include <cmath>
#include <functional>
#include <mutex>

#include <boost/filesystem.hpp>

#include "engine_context.hpp"
#include "project_3mf.hpp"
#include "settings_dialogs.hpp"
#include "libslic3r/Arrange.hpp"
#include "libslic3r/CutUtils.hpp"
#include "libslic3r/Flow.hpp"
#include "libslic3r/Geometry.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Utils.hpp"
#include "libslic3r/calib.hpp"

namespace orcinus::orca {

namespace detail {

Slic3r::Calib_Params calib_params(const CalibrationParams& params)
{
    Slic3r::Calib_Params calib;
    calib.mode = static_cast<Slic3r::CalibMode>(params.mode);
    calib.extruder_id = params.extruder_id;
    calib.start = params.start;
    calib.end = params.end;
    calib.step = params.step;
    calib.print_numbers = params.print_numbers;
    calib.freqStartX = params.freq_start_x;
    calib.freqEndX = params.freq_end_x;
    calib.freqStartY = params.freq_start_y;
    calib.freqEndY = params.freq_end_y;
    calib.test_model = params.test_model;
    calib.shaper_type = params.shaper_type;
    calib.accelerations = params.accelerations;
    calib.speeds = params.speeds;
    return calib;
}

}  // namespace detail

namespace {

// Plater::add_model() of a calibration's model on the plate the new project
// left empty (Plater::priv::load_files): read from OrcaSlicer's resources,
// turned by preferred_orientation, centred around its origin, and standing on
// the plate's centre.
Slic3r::ModelObject* add_calibration_model(Slic3r::Model& model, const std::string& file, const Slic3r::DynamicPrintConfig& config)
{
    const std::string path = Slic3r::resources_dir() + "/calib/" + file;
    Slic3r::Model read = Slic3r::Model::read_from_file(path, nullptr, nullptr, Slic3r::LoadStrategy::LoadModel);
    if (read.objects.empty()) {
        throw Slic3r::RuntimeError("The calibration model " + path + " is empty");
    }
    Slic3r::ModelObject* object = model.add_object(*read.objects.front());
    if (object->name.empty()) {
        object->name = boost::filesystem::path(path).filename().string();
    }
    object->rotate(Slic3r::Geometry::deg2rad(config.opt_float("preferred_orientation")), Slic3r::Axis::Z);
    object->center_around_origin(false);
    object->clear_instances();
    Slic3r::ModelInstance* instance = object->add_instance();
    const Slic3r::Vec2d center = detail::bed_center(config);
    instance->set_offset({center.x(), center.y(), 0.0});
    object->ensure_on_bed(false);
    return object;
}

// Plater::add_model() of a calibration's 3MF file on the plate the new
// project left empty (Plater::priv::load_files() of a file loaded as
// geometry): its objects as the file placed them, standing on the plate and
// gathered around its centre as they stood to each other.
std::vector<Slic3r::ModelObject*> add_calibration_3mf(
    Slic3r::Model& model,
    const std::string& file,
    const Slic3r::DynamicPrintConfig& config,
    detail::SettingsDialogs& dialogs,
    detail::Archive3mf& archive
)
{
    const std::string path = Slic3r::resources_dir() + "/calib/" + file;
    Slic3r::Model read = detail::read_3mf(path, false, config, dialogs, archive);
    if (read.objects.empty()) {
        throw Slic3r::RuntimeError("The calibration model " + path + " is empty");
    }
    for (Slic3r::ModelObject* object : read.objects) {
        if (!object->instances.empty()) {
            object->ensure_on_bed(false);
        }
    }
    read.center_instances_around_point(detail::bed_center(config));
    std::vector<Slic3r::ModelObject*> objects;
    for (const Slic3r::ModelObject* object : read.objects) {
        Slic3r::ModelObject* added = model.add_object(*object);
        added->sort_volumes(true);
        if (added->instances.empty()) {
            added->center_around_origin();
            const Slic3r::Vec2d center = detail::bed_center(config);
            added->add_instance()->set_offset({center.x(), center.y(), 0.0});
        }
        added->ensure_on_bed(false);
        objects.push_back(added);
    }
    return objects;
}

// Selection::scale() of the objects load_files() selected, relative to the
// world: around the centre of their bounding box (Selection::setup_cache()),
// their places spreading with them; then GLCanvas3D::do_scale(), which drops
// every copy onto the bed.
void scale_selection(const std::vector<Slic3r::ModelObject*>& objects, const Slic3r::Vec3d& scale)
{
    // Selection::get_bounding_box(): the convex hulls of the selected volumes.
    Slic3r::BoundingBoxf3 box;
    for (const Slic3r::ModelObject* object : objects) {
        for (std::size_t instance = 0; instance < object->instances.size(); ++instance) {
            box.merge(object->instance_convex_hull_bounding_box(instance));
        }
    }
    // Selection::transform_instance_relative() about m_cache.dragging_center.
    const Slic3r::Vec3d pivot = box.center();
    const Slic3r::Transform3d transform = Slic3r::Geometry::translation_transform(pivot) * Slic3r::Geometry::scale_transform(scale) *
                                          Slic3r::Geometry::translation_transform(-pivot);
    for (Slic3r::ModelObject* object : objects) {
        for (Slic3r::ModelInstance* instance : object->instances) {
            instance->set_transformation(Slic3r::Geometry::Transformation(transform * instance->get_matrix()));
        }
        object->invalidate_bounding_box();
    }
    // Fixes sinking/flying instances (snaps object to buildplate)
    for (Slic3r::ModelObject* object : objects) {
        for (std::size_t instance = 0; instance < object->instances.size(); ++instance) {
            const double shift_z = object->get_instance_min_z(instance);
            if (shift_z != 0.0) {
                object->translate_instance(instance, Slic3r::Vec3d(0.0, 0.0, -shift_z));
            }
        }
    }
}

// adjust_settings_for_flowrate_calib() of Plater.cpp: the objects scaled to
// ten layers (and to the nozzle when it is much larger than 0.6 mm), their
// infill speeds kept within the filament's volumetric speed, each object's
// flow ratio from its name (flowrate_xxx), and the process at the layer the
// test prints with. For the linear (YOLO) test, pass 1 is the normal version
// and pass 2 the one for perfectionists.
void adjust_settings_for_flowrate_calib(
    std::vector<Slic3r::ModelObject*>& objects,
    bool linear,
    int pass,
    Slic3r::InfillPattern pattern,
    Slic3r::PresetBundle& bundle
)
{
    using namespace Slic3r;
    auto print_config = &bundle.prints.get_edited_preset().config;
    auto printerConfig = &bundle.printers.get_edited_preset().config;
    auto filament_config = &bundle.filaments.get_edited_preset().config;

    /// --- scale ---
    // model is created for a 0.4 nozzle, scale z with nozzle size.
    const ConfigOptionFloats* nozzle_diameter_config = printerConfig->option<ConfigOptionFloats>("nozzle_diameter");
    assert(nozzle_diameter_config->values.size() > 0);
    float nozzle_diameter = nozzle_diameter_config->values[0];
    float xyScale = nozzle_diameter / 0.6;
    //scale z to have 10 layers
    // 2 bottom, 5 top, 3 sparse infill
    double first_layer_height = print_config->option<ConfigOptionFloat>("initial_layer_print_height")->value;
    double layer_height = nozzle_diameter / 2.0; // prefer 0.2 layer height for 0.4 nozzle
    first_layer_height = std::max(first_layer_height, layer_height);

    float zscale = (first_layer_height + 9 * layer_height) / 2;
    // only enlarge
    if (xyScale > 1.2) {
        scale_selection(objects, {xyScale, xyScale, zscale});
    } else {
        scale_selection(objects, {1, 1, zscale});
    }

    auto cur_flowrate = filament_config->option<ConfigOptionFloats>("filament_flow_ratio")->get_at(0);
    Flow infill_flow = Flow(nozzle_diameter * 1.2f, layer_height, nozzle_diameter);
    double filament_max_volumetric_speed = filament_config->option<ConfigOptionFloats>("filament_max_volumetric_speed")->get_at(0);
    double max_infill_speed;
    if (linear)
        max_infill_speed = filament_max_volumetric_speed /
                           (infill_flow.mm3_per_mm() * (cur_flowrate + (pass == 2 ? 0.035 : 0.05)) / cur_flowrate);
    else
        max_infill_speed = filament_max_volumetric_speed / (infill_flow.mm3_per_mm() * (pass == 1 ? 1.2 : 1));
    double internal_solid_speed = std::floor(std::min(print_config->opt_float("internal_solid_infill_speed"), max_infill_speed));
    double top_surface_speed = std::floor(std::min(print_config->opt_float("top_surface_speed"), max_infill_speed));

    // adjust parameters
    for (auto _obj : objects) {
        _obj->ensure_on_bed();
        _obj->config.set_key_value("wall_loops", new ConfigOptionInt(1));
        _obj->config.set_key_value("only_one_wall_top", new ConfigOptionBool(true));
        _obj->config.set_key_value("thick_internal_bridges", new ConfigOptionBool(false));
        _obj->config.set_key_value("enable_extra_bridge_layer", new ConfigOptionEnum<EnableExtraBridgeLayer>(eblDisabled));
        _obj->config.set_key_value("internal_bridge_density", new ConfigOptionPercent(100));
        _obj->config.set_key_value("sparse_infill_density", new ConfigOptionPercent(35));
        _obj->config.set_key_value("min_width_top_surface", new ConfigOptionFloatOrPercent(100,true));
        _obj->config.set_key_value("bottom_shell_layers", new ConfigOptionInt(2));
        _obj->config.set_key_value("top_shell_layers", new ConfigOptionInt(5));
        _obj->config.set_key_value("top_shell_thickness", new ConfigOptionFloat(0));
        _obj->config.set_key_value("bottom_shell_thickness", new ConfigOptionFloat(0));
        _obj->config.set_key_value("detect_thin_wall", new ConfigOptionBool(true));
        _obj->config.set_key_value("filter_out_gap_fill", new ConfigOptionFloat(0));
        _obj->config.set_key_value("sparse_infill_pattern", new ConfigOptionEnum<InfillPattern>(ipRectilinear));
        _obj->config.set_key_value("top_surface_line_width", new ConfigOptionFloatOrPercent(nozzle_diameter * 1.2f, false));
        _obj->config.set_key_value("internal_solid_infill_line_width", new ConfigOptionFloatOrPercent(nozzle_diameter * 1.2f, false));
        // ORCA: use the pattern parameter
        _obj->config.set_key_value("top_surface_pattern", new ConfigOptionEnum<InfillPattern>(pattern));
        _obj->config.set_key_value("top_solid_infill_flow_ratio", new ConfigOptionFloat(1.0f));
        _obj->config.set_key_value("infill_direction", new ConfigOptionFloat(45));
        _obj->config.set_key_value("solid_infill_direction", new ConfigOptionFloat(135));
        _obj->config.set_key_value("align_infill_direction_to_model", new ConfigOptionBool(true));
        _obj->config.set_key_value("ironing_type", new ConfigOptionEnum<IroningType>(IroningType::NoIroning));
        _obj->config.set_key_value("internal_solid_infill_speed", new ConfigOptionFloat(internal_solid_speed));
        _obj->config.set_key_value("top_surface_speed", new ConfigOptionFloat(top_surface_speed));
        _obj->config.set_key_value("seam_slope_type", new ConfigOptionEnum<SeamScarfType>(SeamScarfType::None));
        _obj->config.set_key_value("gap_fill_target", new ConfigOptionEnum<GapFillTarget>(GapFillTarget::gftNowhere));
        print_config->set_key_value("max_volumetric_extrusion_rate_slope", new ConfigOptionFloat(0));
        _obj->config.set_key_value("calib_flowrate_topinfill_special_order", new ConfigOptionBool(true));

        // extract flowrate from name, filename format: flowrate_xxx
        std::string obj_name = _obj->name;
        assert(obj_name.length() > 9);
        obj_name = obj_name.substr(9);
        if (obj_name[0] == 'm')
            obj_name[0] = '-';
        // Orca: force set locale to C to avoid parsing error; the engine keeps
        // the C locale throughout.
        auto              modifier  = 1.0f;
        try {
            modifier = stof(obj_name);
        } catch (...) {
        }

        if(linear)
            _obj->config.set_key_value("print_flow_ratio", new ConfigOptionFloat((cur_flowrate + modifier)/cur_flowrate));
        else
            _obj->config.set_key_value("print_flow_ratio", new ConfigOptionFloat(1.0f + modifier/100.f));

    }

    print_config->set_key_value("layer_height", new ConfigOptionFloat(layer_height));
    print_config->set_key_value("alternate_extra_wall", new ConfigOptionBool(false));
    print_config->set_key_value("initial_layer_print_height", new ConfigOptionFloat(first_layer_height));
    print_config->set_key_value("reduce_crossing_wall", new ConfigOptionBool(true));
    print_config->set_key_value("enable_wrapping_detection", new ConfigOptionBool(false));
}

// Plater::cut_horizontal() at height z of the object's copy: the part the
// attributes keep takes the object's place (apply_cut_object_to_model).
Slic3r::ModelObject* cut_horizontal(Slic3r::Model& model, Slic3r::ModelObject* object, double z, Slic3r::ModelObjectCutAttributes attributes)
{
    const Slic3r::Vec3d instance_offset = object->instances.front()->get_offset();
    Slic3r::Cut cut(object, 0, Slic3r::Geometry::translation_transform(z * Slic3r::Vec3d::UnitZ() - instance_offset), attributes);
    const Slic3r::ModelObjectPtrs& new_objects = cut.perform_with_plane();
    if (new_objects.empty()) {
        return object;
    }
    // The cut's objects are its own until it ends.
    Slic3r::ModelObject* kept = model.add_object(*new_objects.front());
    model.delete_object(object);
    kept->ensure_on_bed(false);
    return kept;
}

// Plater::calib_temp(): the temperature tower, a block for every 5 °C from
// 500 °C down, cut to the blocks from the start to the end temperature,
// scaled to the nozzle, and printed with a layer of half the nozzle and an
// outer brim, from the start temperature.
Slic3r::ModelObject* calib_temp(Slic3r::Model& model, const CalibrationParams& params, const Slic3r::DynamicPrintConfig& config, Slic3r::PresetBundle& bundle)
{
    constexpr double base_temp_tower_nozzle_diameter = 0.4;
    constexpr double base_temp_tower_block_height = 10.0;
    constexpr int base_temp_tower_temp_step = 5;

    Slic3r::ModelObject* object = add_calibration_model(model, "temperature_tower/temperature_tower.drc", config);
    auto printer_config = &bundle.printers.get_edited_preset().config;
    auto filament_config = &bundle.filaments.get_edited_preset().config;
    auto start_temp = std::lround(params.start);
    const Slic3r::ConfigOptionFloats* nozzle_diameter_config = printer_config->option<Slic3r::ConfigOptionFloats>("nozzle_diameter");
    std::size_t nozzle_id = static_cast<std::size_t>(std::max(params.extruder_id, 0));
    double nozzle_diameter = base_temp_tower_nozzle_diameter;
    if (nozzle_diameter_config && !nozzle_diameter_config->values.empty()) {
        nozzle_id = std::min(nozzle_id, nozzle_diameter_config->values.size() - 1);
        nozzle_diameter = nozzle_diameter_config->values[nozzle_id];
    }
    if (nozzle_diameter <= 0.0)
        nozzle_diameter = base_temp_tower_nozzle_diameter;

    const double nozzle_scale = nozzle_diameter / base_temp_tower_nozzle_diameter;
    const double block_height = base_temp_tower_block_height;

    // cut upper
    auto obj_bb = object->bounding_box_exact();
    auto block_count = std::lround((500 - params.end) / base_temp_tower_temp_step + 1);
    if (block_count > 0) {
        // subtract EPSILON offset to avoid cutting at the exact location where the flat surface is
        auto new_height = block_count * block_height - EPSILON;
        if (new_height < obj_bb.size().z()) {
            object = cut_horizontal(model, object, new_height, Slic3r::ModelObjectCutAttribute::KeepLower);
        }
    }

    // cut bottom
    obj_bb = object->bounding_box_exact();
    block_count = std::lround((500 - params.start) / base_temp_tower_temp_step);
    if (block_count > 0) {
        auto new_height = block_count * block_height + EPSILON;
        if (new_height < obj_bb.size().z()) {
            object = cut_horizontal(model, object, new_height, Slic3r::ModelObjectCutAttribute::KeepUpper);
        }
    }

    if (std::abs(nozzle_scale - 1.0) > EPSILON)
        object->scale(nozzle_scale, nozzle_scale, nozzle_scale);

    object->ensure_on_bed();

    printer_config->set_key_value("resonance_avoidance", new Slic3r::ConfigOptionBool{false});
    filament_config->set_key_value("nozzle_temperature_initial_layer", new Slic3r::ConfigOptionInts(1, (int) start_temp));
    filament_config->set_key_value("nozzle_temperature", new Slic3r::ConfigOptionInts(1, (int) start_temp));
    object->config.set_key_value("layer_height", new Slic3r::ConfigOptionFloat(nozzle_diameter / 2));
    object->config.set_key_value("brim_type", new Slic3r::ConfigOptionEnum<Slic3r::BrimType>(Slic3r::btOuterOnly));
    object->config.set_key_value("brim_width", new Slic3r::ConfigOptionFloat(5.0));
    object->config.set_key_value("brim_object_gap", new Slic3r::ConfigOptionFloat(0.0));
    object->config.set_key_value("alternate_extra_wall", new Slic3r::ConfigOptionBool(false));
    object->config.set_key_value("seam_slope_type", new Slic3r::ConfigOptionEnum<Slic3r::SeamScarfType>(Slic3r::SeamScarfType::None));
    object->config.set_key_value("overhang_reverse", new Slic3r::ConfigOptionBool(false));
    object->config.set_key_value("precise_z_height", new Slic3r::ConfigOptionBool(false));

    auto print_config = &bundle.prints.get_edited_preset().config;
    print_config->set_key_value("enable_wrapping_detection", new Slic3r::ConfigOptionBool(false));
    print_config->set_key_value("initial_layer_print_height", new Slic3r::ConfigOptionFloat(nozzle_diameter / 2));
    return object;
}

// The printer's max_layer_height of the first extruder raised to a test's layer height.
void allow_layer_height(Slic3r::DynamicPrintConfig& printer_config, double layer_height)
{
    auto max_lh = printer_config.option<Slic3r::ConfigOptionFloats>("max_layer_height");
    if (max_lh->values[0] < layer_height)
        max_lh->values[0] = {layer_height};
}

// Plater::calib_max_vol_speed(): the speed test structure, narrowed to the
// bed, printed in spiral vase mode with one wall a line of 1.75 nozzles wide
// and layers of 0.8 nozzles, cut to the heights of the volumetric speeds; the
// print is told the speeds these volumes take.
Slic3r::ModelObject* calib_max_vol_speed(Slic3r::Model& model, CalibrationParams& params, const Slic3r::DynamicPrintConfig& config, Slic3r::PresetBundle& bundle)
{
    Slic3r::ModelObject* obj = add_calibration_model(model, "volumetric_speed/SpeedTestStructure.drc", config);

    auto print_config = &bundle.prints.get_edited_preset().config;
    auto filament_config = &bundle.filaments.get_edited_preset().config;
    auto printer_config = &bundle.printers.get_edited_preset().config;
    auto& obj_cfg = obj->config;

    auto bed_shape = printer_config->option<Slic3r::ConfigOptionPoints>("printable_area")->values;
    Slic3r::BoundingBoxf bed_ext = Slic3r::get_extents(bed_shape);
    auto scale_obj = (bed_ext.size().x() - 10) / obj->bounding_box_exact().size().x();
    if (scale_obj < 1.0)
        obj->scale(scale_obj, 1, 1);

    const Slic3r::ConfigOptionFloats* nozzle_diameter_config = printer_config->option<Slic3r::ConfigOptionFloats>("nozzle_diameter");
    double nozzle_diameter = nozzle_diameter_config->values[0];
    double line_width = nozzle_diameter * 1.75;
    double layer_height = nozzle_diameter * 0.8;

    allow_layer_height(*printer_config, layer_height);

    filament_config->set_key_value("filament_max_volumetric_speed", new Slic3r::ConfigOptionFloats{200});
    filament_config->set_key_value("slow_down_layer_time", new Slic3r::ConfigOptionFloats{0.0});
    printer_config->set_key_value("resonance_avoidance", new Slic3r::ConfigOptionBool{false});
    obj_cfg.set_key_value("enable_overhang_speed", new Slic3r::ConfigOptionBool{false});
    obj_cfg.set_key_value("wall_loops", new Slic3r::ConfigOptionInt(1));
    obj_cfg.set_key_value("alternate_extra_wall", new Slic3r::ConfigOptionBool(false));
    obj_cfg.set_key_value("top_shell_layers", new Slic3r::ConfigOptionInt(0));
    obj_cfg.set_key_value("bottom_shell_layers", new Slic3r::ConfigOptionInt(0));
    obj_cfg.set_key_value("sparse_infill_density", new Slic3r::ConfigOptionPercent(0));
    obj_cfg.set_key_value("outer_wall_line_width", new Slic3r::ConfigOptionFloatOrPercent(line_width, false));
    obj_cfg.set_key_value("layer_height", new Slic3r::ConfigOptionFloat(layer_height));
    obj_cfg.set_key_value("brim_type", new Slic3r::ConfigOptionEnum<Slic3r::BrimType>(Slic3r::btOuterAndInner));
    obj_cfg.set_key_value("brim_width", new Slic3r::ConfigOptionFloat(5.0));
    obj_cfg.set_key_value("brim_object_gap", new Slic3r::ConfigOptionFloat(0.0));
    obj_cfg.set_key_value("precise_z_height", new Slic3r::ConfigOptionBool(false));
    print_config->set_key_value("timelapse_type", new Slic3r::ConfigOptionEnum<Slic3r::TimelapseType>(Slic3r::tlTraditional));
    print_config->set_key_value("spiral_mode", new Slic3r::ConfigOptionBool(true));
    print_config->set_key_value("max_volumetric_extrusion_rate_slope", new Slic3r::ConfigOptionFloat(0));
    print_config->set_key_value("enable_wrapping_detection", new Slic3r::ConfigOptionBool(false));

    //  cut upper
    auto obj_bb = obj->bounding_box_exact();
    auto height = (params.end - params.start + 1) / params.step;
    if (height < obj_bb.size().z()) {
        obj = cut_horizontal(model, obj, height, Slic3r::ModelObjectCutAttribute::KeepLower);
    }

    // filament_flow_ratio is nullable, as the desktop app reads it.
    double flow_ratio = 1.0;
    if (const auto* ratio = filament_config->option<Slic3r::ConfigOptionFloatsNullable>("filament_flow_ratio"); ratio != nullptr) {
        flow_ratio = ratio->get_at(0);
    } else if (const auto* plain = filament_config->option<Slic3r::ConfigOptionFloats>("filament_flow_ratio"); plain != nullptr) {
        flow_ratio = plain->get_at(0);
    }
    auto mm3_per_mm = Slic3r::Flow(float(line_width), float(layer_height), float(nozzle_diameter)).mm3_per_mm() * flow_ratio;
    params.end = params.end / mm3_per_mm;
    params.start = params.start / mm3_per_mm;
    params.step = params.step / mm3_per_mm;
    return obj;
}

// Plater::calib_retraction(): the retraction tower, with layers of 0.2 mm
// (less for a fine nozzle), two walls and three bottom layers, aligned seams
// and the firmware's retraction off, cut to the heights of the lengths.
Slic3r::ModelObject* calib_retraction(Slic3r::Model& model, const CalibrationParams& params, const Slic3r::DynamicPrintConfig& config, Slic3r::PresetBundle& bundle)
{
    Slic3r::ModelObject* obj = add_calibration_model(model, "retraction/retraction_tower.drc", config);

    auto print_config = &bundle.prints.get_edited_preset().config;
    auto printer_config = &bundle.printers.get_edited_preset().config;

    print_config->set_key_value("enable_wrapping_detection", new Slic3r::ConfigOptionBool(false));

    float nozzle_diameter = printer_config->option<Slic3r::ConfigOptionFloats>("nozzle_diameter")->get_at(0);
    float layer_height;
    if (nozzle_diameter <= 0.1f) {
        layer_height = 0.05f;
    } else if (nozzle_diameter <= 0.2f) {
        layer_height = 0.1f;
    } else {
        layer_height = 0.2f;
    }

    allow_layer_height(*printer_config, layer_height);

    printer_config->set_key_value("resonance_avoidance", new Slic3r::ConfigOptionBool{false});
    printer_config->set_key_value("use_firmware_retraction", new Slic3r::ConfigOptionBool(false));
    obj->config.set_key_value("wall_loops", new Slic3r::ConfigOptionInt(2));
    obj->config.set_key_value("top_shell_layers", new Slic3r::ConfigOptionInt(0));
    obj->config.set_key_value("bottom_shell_layers", new Slic3r::ConfigOptionInt(3));
    obj->config.set_key_value("sparse_infill_density", new Slic3r::ConfigOptionPercent(0));
    print_config->set_key_value("initial_layer_print_height", new Slic3r::ConfigOptionFloat(layer_height));
    obj->config.set_key_value("layer_height", new Slic3r::ConfigOptionFloat(layer_height));
    obj->config.set_key_value("alternate_extra_wall", new Slic3r::ConfigOptionBool(false));
    obj->config.set_key_value("seam_position", new Slic3r::ConfigOptionEnum<Slic3r::SeamPosition>(Slic3r::spAligned));
    obj->config.set_key_value("wall_sequence", new Slic3r::ConfigOptionEnum<Slic3r::WallSequence>(Slic3r::WallSequence::InnerOuter));
    obj->config.set_key_value("overhang_reverse", new Slic3r::ConfigOptionBool(false));
    obj->config.set_key_value("precise_z_height", new Slic3r::ConfigOptionBool(false));

    //  cut upper
    auto obj_bb = obj->bounding_box_exact();
    auto height = 1.0 + 0.4 + ((params.end - params.start)) / params.step - EPSILON;
    if (height < obj_bb.size().z()) {
        obj = cut_horizontal(model, obj, height, Slic3r::ModelObjectCutAttribute::KeepLower);
    }
    return obj;
}

// Plater::calib_VFA(): the VFA tower in spiral vase mode with one wall and an
// outer brim, cut to 5 mm for every speed.
Slic3r::ModelObject* calib_vfa(Slic3r::Model& model, const CalibrationParams& params, const Slic3r::DynamicPrintConfig& config, Slic3r::PresetBundle& bundle)
{
    Slic3r::ModelObject* obj = add_calibration_model(model, "vfa/vfa.drc", config);
    auto print_config = &bundle.prints.get_edited_preset().config;
    auto filament_config = &bundle.filaments.get_edited_preset().config;
    auto printer_config = &bundle.printers.get_edited_preset().config;
    printer_config->set_key_value("resonance_avoidance", new Slic3r::ConfigOptionBool{false});
    filament_config->set_key_value("slow_down_layer_time", new Slic3r::ConfigOptionFloats{0.0});
    print_config->set_key_value("enable_overhang_speed", new Slic3r::ConfigOptionBool{false});
    print_config->set_key_value("timelapse_type", new Slic3r::ConfigOptionEnum<Slic3r::TimelapseType>(Slic3r::tlTraditional));
    print_config->set_key_value("wall_loops", new Slic3r::ConfigOptionInt(1));
    print_config->set_key_value("alternate_extra_wall", new Slic3r::ConfigOptionBool(false));
    print_config->set_key_value("top_shell_layers", new Slic3r::ConfigOptionInt(0));
    print_config->set_key_value("bottom_shell_layers", new Slic3r::ConfigOptionInt(1));
    print_config->set_key_value("sparse_infill_density", new Slic3r::ConfigOptionPercent(0));
    print_config->set_key_value("detect_thin_wall", new Slic3r::ConfigOptionBool(false));
    print_config->set_key_value("spiral_mode", new Slic3r::ConfigOptionBool(true));
    print_config->set_key_value("enable_wrapping_detection", new Slic3r::ConfigOptionBool(false));
    print_config->set_key_value("precise_z_height", new Slic3r::ConfigOptionBool(false));
    obj->config.set_key_value("brim_type", new Slic3r::ConfigOptionEnum<Slic3r::BrimType>(Slic3r::btOuterOnly));
    obj->config.set_key_value("brim_width", new Slic3r::ConfigOptionFloat(3.0));
    obj->config.set_key_value("brim_object_gap", new Slic3r::ConfigOptionFloat(0.0));

    // cut upper
    auto obj_bb = obj->bounding_box_exact();
    auto height = 5 * ((params.end - params.start) / params.step + 1);
    if (height < obj_bb.size().z()) {
        obj = cut_horizontal(model, obj, height, Slic3r::ModelObjectCutAttribute::KeepLower);
    }
    return obj;
}

// Plater::calib_pa() before its method: the process without reversed
// overhangs and precise Z height, and the printer without resonance avoidance.
void calib_pa_common(Slic3r::PresetBundle& bundle)
{
    auto print_config = &bundle.prints.get_edited_preset().config;
    auto printer_config = &bundle.printers.get_edited_preset().config;
    print_config->set_key_value("overhang_reverse", new Slic3r::ConfigOptionBool(false));
    print_config->set_key_value("precise_z_height", new Slic3r::ConfigOptionBool(false));
    printer_config->set_key_value("resonance_avoidance", new Slic3r::ConfigOptionBool{false});
}

// Plater::_calib_pa_tower(): the tower with its seam, its walls at the
// speed the test finds best (CalibPressureAdvance::find_optimal_PA_speed),
// two walls without shells or infill and an ear brim, cut to a millimetre
// for every step.
Slic3r::ModelObject* calib_pa_tower(Slic3r::Model& model, const CalibrationParams& params, const Slic3r::DynamicPrintConfig& config, Slic3r::PresetBundle& bundle)
{
    Slic3r::ModelObject* obj = add_calibration_model(model, "pressure_advance/tower_with_seam.drc", config);

    auto& print_config = bundle.prints.get_edited_preset().config;
    auto printer_config = &bundle.printers.get_edited_preset().config;
    auto filament_config = &bundle.filaments.get_edited_preset().config;

    const double nozzle_diameter = printer_config->option<Slic3r::ConfigOptionFloats>("nozzle_diameter")->get_at(0);

    print_config.set_key_value("enable_wrapping_detection", new Slic3r::ConfigOptionBool(false));
    filament_config->set_key_value("slow_down_layer_time", new Slic3r::ConfigOptionFloats{1.0f});

    auto& obj_cfg = obj->config;

    obj_cfg.set_key_value("alternate_extra_wall", new Slic3r::ConfigOptionBool(false));
    auto full_config = bundle.full_config();
    auto wall_speed = Slic3r::CalibPressureAdvance::find_optimal_PA_speed(
        full_config, full_config.get_abs_value("line_width", nozzle_diameter),
        full_config.get_abs_value("layer_height"), 0, 0);
    obj_cfg.set_key_value("outer_wall_speed", new Slic3r::ConfigOptionFloat(wall_speed));
    obj_cfg.set_key_value("inner_wall_speed", new Slic3r::ConfigOptionFloat(wall_speed));
    obj_cfg.set_key_value("seam_position", new Slic3r::ConfigOptionEnum<Slic3r::SeamPosition>(Slic3r::spRear));
    obj_cfg.set_key_value("wall_loops", new Slic3r::ConfigOptionInt(2));
    obj_cfg.set_key_value("top_shell_layers", new Slic3r::ConfigOptionInt(0));
    obj_cfg.set_key_value("bottom_shell_layers", new Slic3r::ConfigOptionInt(0));
    obj_cfg.set_key_value("sparse_infill_density", new Slic3r::ConfigOptionPercent(0));
    obj_cfg.set_key_value("brim_type", new Slic3r::ConfigOptionEnum<Slic3r::BrimType>(Slic3r::btEar));
    obj_cfg.set_key_value("brim_object_gap", new Slic3r::ConfigOptionFloat(.0f));
    obj_cfg.set_key_value("brim_ears_max_angle", new Slic3r::ConfigOptionFloat(135.f));
    obj_cfg.set_key_value("brim_width", new Slic3r::ConfigOptionFloat(6.f));
    obj_cfg.set_key_value("seam_slope_type", new Slic3r::ConfigOptionEnum<Slic3r::SeamScarfType>(Slic3r::SeamScarfType::None));
    print_config.set_key_value("max_volumetric_extrusion_rate_slope", new Slic3r::ConfigOptionFloat(0));

    auto new_height = std::ceil((params.end - params.start) / params.step) + 1;
    auto obj_bb = obj->bounding_box_exact();
    if (new_height < obj_bb.size().z()) {
        obj = cut_horizontal(model, obj, new_height, Slic3r::ModelObjectCutAttribute::KeepLower);
    }
    return obj;
}

// Plater::has_junction_deviation(): Marlin 2 with a junction deviation.
bool has_junction_deviation(const Slic3r::DynamicPrintConfig* printer_config)
{
    if (!printer_config) {
        return false;
    }
    const auto gcode_flavor = printer_config->option<Slic3r::ConfigOptionEnum<Slic3r::GCodeFlavor>>("gcode_flavor");
    const auto junction_dev = printer_config->option<Slic3r::ConfigOptionFloats>("machine_max_junction_deviation");
    return gcode_flavor &&
           gcode_flavor->value == Slic3r::GCodeFlavor::gcfMarlinFirmware &&
           junction_dev &&
           !junction_dev->values.empty() &&
           junction_dev->values.front() > 0.0;
}

// Plater::_calib_pa_pattern(): the presets set up for the pattern, and a
// handle cube for every speed and acceleration, arranged as the patterns
// they stand for would be (the plates after the first take what the first
// does not hold), named after its speed and acceleration, with its own when
// the test has several. The G-code of the patterns is generated as each
// plate is sliced (pa_pattern_gcodes of the adapter). The notifications
// OrcaSlicer pushes for the figures it chose show as notices.
std::vector<Slic3r::ModelObject*> calib_pa_pattern(
    Slic3r::Model& model,
    const CalibrationParams& calibration,
    const Slic3r::DynamicPrintConfig& config,
    Slic3r::PresetBundle& bundle,
    detail::SettingsDialogs& dialogs,
    int& plate_count
)
{
    using namespace Slic3r;
    // The pattern keeps a reference to its figures (CalibPressureAdvancePattern::m_params).
    const Calib_Params params = detail::calib_params(calibration);
    std::vector<double> speeds{params.speeds};
    std::vector<double> accels{params.accelerations};
    /* Set common parameters */
    auto printer_config = &bundle.printers.get_edited_preset().config;
    DynamicPrintConfig& print_config = bundle.prints.get_edited_preset().config;
    auto filament_config = &bundle.filaments.get_edited_preset().config;
    double nozzle_diameter = printer_config->option<ConfigOptionFloats>("nozzle_diameter")->get_at(0);
    filament_config->set_key_value("filament_retract_when_changing_layer", new ConfigOptionBoolsNullable{false});
    filament_config->set_key_value("filament_wipe", new ConfigOptionBoolsNullable{false});
    printer_config->set_key_value("wipe", new ConfigOptionBools{false});
    printer_config->set_key_value("retract_when_changing_layer", new ConfigOptionBools{false});
    printer_config->set_key_value("resonance_avoidance", new ConfigOptionBool{false});

    //Orca: find acceleration to use in the test
    auto accel = print_config.option<ConfigOptionFloat>("outer_wall_acceleration")->value; // get the outer wall acceleration
    if (accel == 0) // if outer wall accel isnt defined, fall back to inner wall accel
        accel = print_config.option<ConfigOptionFloat>("inner_wall_acceleration")->value;
    if (accel == 0) // if inner wall accel is not defined fall back to default accel
        accel = print_config.option<ConfigOptionFloat>("default_acceleration")->value;
    // Orca: Set all accelerations except first layer, as the first layer accel doesnt affect the PA test since accel
    // is set to the travel accel before printing the pattern.
    if (accels.empty()) {
        accels.assign({accel});
        dialogs.inform("pa_pattern_accelerations",
                       {detail::ui_text("INFO:"), detail::ui_text("%s", {"\n"}),
                        detail::ui_text("No accelerations provided for calibration. Use default acceleration value "),
                        detail::ui_text("%s", {std::to_string(long(accel))}), detail::ui_text("mm/s²")},
                       {}, DialogIcon::info);
    } else {
        // set max acceleration in case of batch mode to get correct test pattern size
        accel = *std::max_element(accels.begin(), accels.end());
    }
    print_config.set_key_value("outer_wall_acceleration", new ConfigOptionFloat(accel));
    print_config.set_key_value("print_sequence", new ConfigOptionEnum(PrintSequence::ByLayer));

    //Orca: find jerk value to use in the test
    if (!has_junction_deviation(printer_config) && print_config.option<ConfigOptionFloat>("default_jerk")->value > 0) { // we have set a jerk value
        auto jerk = print_config.option<ConfigOptionFloat>("outer_wall_jerk")->value; // get outer wall jerk
        if (jerk == 0) // if outer wall jerk is not defined, get inner wall jerk
            jerk = print_config.option<ConfigOptionFloat>("inner_wall_jerk")->value;
        if (jerk == 0) // if inner wall jerk is not defined, get the default jerk
            jerk = print_config.option<ConfigOptionFloat>("default_jerk")->value;

        //Orca: Set jerk values. Again first layer jerk should not matter as it is reset to the travel jerk before the
        // first PA pattern is printed.
        print_config.set_key_value("default_jerk", new ConfigOptionFloat(jerk));
        print_config.set_key_value("outer_wall_jerk", new ConfigOptionFloat(jerk));
        print_config.set_key_value("inner_wall_jerk", new ConfigOptionFloat(jerk));
        print_config.set_key_value("top_surface_jerk", new ConfigOptionFloat(jerk));
        print_config.set_key_value("infill_jerk", new ConfigOptionFloat(jerk));
        print_config.set_key_value("travel_jerk", new ConfigOptionFloat(jerk));
    }

    if (has_junction_deviation(printer_config)) {
        print_config.set_key_value("default_junction_deviation", new ConfigOptionFloat(0));
    }

    for (const auto& opt : SuggestedConfigCalibPAPattern().float_pairs) {
        print_config.set_key_value(opt.first, new ConfigOptionFloat(opt.second));
    }

    for (const auto& opt : SuggestedConfigCalibPAPattern().nozzle_ratio_pairs) {
        print_config.set_key_value(opt.first, new ConfigOptionFloatOrPercent(nozzle_diameter * opt.second / 100, false));
    }

    for (const auto& opt : SuggestedConfigCalibPAPattern().int_pairs) {
        print_config.set_key_value(opt.first, new ConfigOptionInt(opt.second));
    }

    print_config.set_key_value(SuggestedConfigCalibPAPattern().brim_pair.first,
                               new ConfigOptionEnum<BrimType>(SuggestedConfigCalibPAPattern().brim_pair.second));

    print_config.set_key_value("enable_wrapping_detection", new ConfigOptionBool(false));

    // Orca: Set the outer wall speed to the optimal speed for the test, cap it with max volumetric speed
    if (speeds.empty()) {
        double speed = CalibPressureAdvance::find_optimal_PA_speed(bundle.full_config(), print_config.get_abs_value("line_width", nozzle_diameter),
                                                                   print_config.get_abs_value("layer_height"), 0, 0);
        print_config.set_key_value("outer_wall_speed", new ConfigOptionFloat(speed));

        speeds.assign({speed});
        dialogs.inform("pa_pattern_speeds",
                       {detail::ui_text("INFO:"), detail::ui_text("%s", {"\n"}),
                        detail::ui_text("No speeds provided for calibration. Use default optimal speed "),
                        detail::ui_text("%s", {std::to_string(long(speed))}), detail::ui_text("mm/s")},
                       {}, DialogIcon::info);
    } else if (speeds.size() == 1) {
        // If we have single value provided, set speed using global configuration.
        // per-object config is not set in this case
        print_config.set_key_value("outer_wall_speed", new ConfigOptionFloat(speeds.front()));
    }

    const DynamicPrintConfig full_config = bundle.full_config();
    const bool is_bbl_machine = bundle.is_bbl_vendor();

    // add "handle" cube
    ModelObject* cube = detail::add_shape_object(model, "Cube", "Cube", config);

    CalibPressureAdvancePattern pa_pattern(params, full_config, is_bbl_machine, *cube, to_3d(detail::plate_origin(config, 0, 1), 0.));

    /* Having PA pattern configured, we could make a set of polygons resembling N test patterns.
     * We'll arrange this set of polygons, so we would know position of each test pattern and
     * could position test cubes later on
     *
     * We'll take advantage of already existing cube: scale it up to test pattern size to use
     * as a reference for objects arrangement. Polygon is slightly oversized to add spaces between patterns.
     * That arrangement will be used to place 'handle cubes' for each test. */
    auto cube_bb = cube->raw_bounding_box();
    cube->scale((pa_pattern.print_size_x() + 4) / cube_bb.size().x(),
                (pa_pattern.print_size_y() + 4) / cube_bb.size().y(),
                pa_pattern.max_layer_z() / cube_bb.size().z());

    arrangement::ArrangePolygons arranged_items;
    {
        arrangement::ArrangeParams ap;
        Points bedpts = arrangement::get_shrink_bedpts(&full_config, ap);

        for (size_t i = 0; i < speeds.size() * accels.size(); i++) {
            arrangement::ArrangePolygon p;
            cube->instances[0]->get_arrange_polygon(&p);
            p.bed_idx = 0;
            arranged_items.emplace_back(p);
        }

        arrangement::arrange(arranged_items, bedpts, ap);
    }

    /* scale cube back to the size of test pattern 'handle' */
    cube_bb = cube->raw_bounding_box();
    cube->scale(pa_pattern.handle_xy_size() / cube_bb.size().x(),
                pa_pattern.handle_xy_size() / cube_bb.size().y(),
                pa_pattern.max_layer_z() / cube_bb.size().z());

    // PartPlateList::create_plate() for a test the plates so far do not hold.
    plate_count = 1;
    for (const auto& ai : arranged_items) {
        plate_count = std::max(plate_count, ai.bed_idx + 1);
    }

    /* Set speed and acceleration on per-object basis and arrange anchor object on the plates.
     * Test gcode will be genecated during plate slicing */
    std::vector<ModelObject*> objects;
    for (size_t test_idx = 0; test_idx < arranged_items.size(); test_idx++) {
        const auto& ai = arranged_items[test_idx];
        int plate_idx = std::max(ai.bed_idx, 0);
        auto tspd = speeds[test_idx % speeds.size()];
        auto tacc = accels[test_idx / speeds.size()];

        /* make an own copy of anchor cube for each test */
        auto obj = test_idx == 0 ? cube : model.add_object(*cube);
        obj->name.assign(std::string("pa_pattern_") + std::to_string(int(tspd)) + std::string("_") + std::to_string(int(tacc)));

        auto& obj_config = obj->config;
        if (speeds.size() > 1)
            obj_config.set_key_value("outer_wall_speed", new ConfigOptionFloat(tspd));
        if (accels.size() > 1)
            obj_config.set_key_value("outer_wall_acceleration", new ConfigOptionFloat(tacc));

        const Vec3d obj_offset{unscale<double>(ai.translation(X)), unscale<double>(ai.translation(Y)), 0};
        obj->instances[0]->set_offset(to_3d(detail::plate_origin(config, plate_idx, plate_count), 0.) + obj_offset + pa_pattern.handle_pos_offset());
        obj->ensure_on_bed();
        objects.push_back(obj);
    }
    return objects;
}

// The objects a calibration set up on the empty plate, with the plates they
// stand on.
struct CalibrationSetup {
    std::vector<Slic3r::ModelObject*> objects;
    int plate_count{1};
};

// What every calibration of the menu does once its new project stands: the
// selected presets, the objects setup() adds to the empty plate with the
// changes to the edited presets, written as import_model() writes them.
// Tab::reload_config() only shows the values the calibration changed, without
// Tab::update() and its questions (a spiral vase test leaves the walls of the
// process preset to the object), as the tabs show the edited presets whenever
// they are described.
ImportedModels prepare(
    const ProfileSelection& profiles,
    const std::string& output_prefix,
    const std::function<CalibrationSetup(Slic3r::Model&, const Slic3r::DynamicPrintConfig&, Slic3r::PresetBundle&, detail::SettingsDialogs&, ImportedModels&)>& setup
)
{
    ImportedModels result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().bundle == nullptr) {
        result.status = SceneStatus::engine_not_ready;
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }
    try {
        Slic3r::PresetBundle& bundle = *detail::engine().bundle;
        Slic3r::DynamicPrintConfig config;
        if (const SliceStatus status = detail::select_profiles(bundle, profiles, config, result.message);
            status != SliceStatus::success) {
            result.status = SceneStatus::profile_not_found;
            return result;
        }
        Slic3r::Model model;
        const DialogAnswers no_answers;
        detail::SettingsDialogs dialogs(no_answers);
        const CalibrationSetup set_up = setup(model, config, bundle, dialogs, result);
        if (set_up.objects.empty()) {
            return result;
        }
        result.plate_count = set_up.plate_count;
        result.notices = dialogs.take_notices();
        result.presets_changed = true;
        if (!detail::write_objects(set_up.objects, output_prefix, result)) {
            return result;
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
    }
    return result;
}

}  // namespace

ImportedModels prepare_calibration(const CalibrationParams& params, const ProfileSelection& profiles, const std::string& output_prefix)
{
    return prepare(profiles, output_prefix, [&params](Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config, Slic3r::PresetBundle& bundle,
                                                      detail::SettingsDialogs& dialogs, ImportedModels& result) {
        CalibrationSetup set_up;
        Slic3r::ModelObject* object = nullptr;
        // What the plate's print is told, which a test may turn into other figures.
        CalibrationParams print_params = params;
        switch (params.mode) {
        case CalibrationMode::temp_tower: object = calib_temp(model, params, config, bundle); break;
        case CalibrationMode::vol_speed_tower: object = calib_max_vol_speed(model, print_params, config, bundle); break;
        case CalibrationMode::retraction_tower: object = calib_retraction(model, params, config, bundle); break;
        case CalibrationMode::vfa_tower: object = calib_vfa(model, params, config, bundle); break;
        case CalibrationMode::pa_tower:
            calib_pa_common(bundle);
            object = calib_pa_tower(model, params, config, bundle);
            break;
        case CalibrationMode::pa_line:
            // The G-code draws the lines itself (CalibPressureAdvanceLine); the model stands in for them.
            calib_pa_common(bundle);
            object = add_calibration_model(model, "pressure_advance/pressure_advance_test.drc", config);
            break;
        case CalibrationMode::pa_pattern:
            calib_pa_common(bundle);
            set_up.objects = calib_pa_pattern(model, params, config, bundle, dialogs, set_up.plate_count);
            break;
        default: result.message = "This calibration is not ported yet"; return set_up;
        }
        result.calibration = print_params;
        if (object != nullptr) {
            set_up.objects.push_back(object);
        }
        return set_up;
    });
}

ImportedModels prepare_flow_rate_calibration(
    const bool linear,
    const int pass,
    const std::string& top_surface_pattern,
    const ProfileSelection& profiles,
    const std::string& output_prefix
)
{
    return prepare(profiles, output_prefix, [&](Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config, Slic3r::PresetBundle& bundle,
                                                detail::SettingsDialogs& dialogs, ImportedModels& result) {
        CalibrationSetup set_up;
        Slic3r::ConfigOptionEnum<Slic3r::InfillPattern> pattern;
        if ((pass != 1 && pass != 2) || !pattern.deserialize(top_surface_pattern)) {
            result.message = "Unknown flow rate calibration";
            return set_up;
        }
        std::string file;
        if (linear) {
            file = pass == 1 ? "filament_flow/Orca-LinearFlow.3mf" : "filament_flow/Orca-LinearFlow_fine.3mf";
        } else {
            file = pass == 1 ? "filament_flow/flowrate-test-pass1.3mf" : "filament_flow/flowrate-test-pass2.3mf";
        }
        detail::Archive3mf archive;
        set_up.objects = add_calibration_3mf(model, file, config, dialogs, archive);
        // What the file brings into the presets, as load_files() applies it.
        detail::apply_3mf(archive, boost::filesystem::path(file).filename().string(), dialogs, result);

        // ORCA: pass the pattern
        adjust_settings_for_flowrate_calib(set_up.objects, linear, pass, pattern.value, bundle);
        auto printer_config = &bundle.printers.get_edited_preset().config;
        printer_config->set_key_value("resonance_avoidance", new Slic3r::ConfigOptionBool{false});
        return set_up;
    });
}

}  // namespace orcinus::orca
