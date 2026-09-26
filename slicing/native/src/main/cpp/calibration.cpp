#include <cmath>
#include <mutex>

#include <boost/filesystem.hpp>

#include "engine_context.hpp"
#include "settings_tab.hpp"
#include "libslic3r/CutUtils.hpp"
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

}  // namespace

ImportedModels prepare_calibration(const CalibrationParams& params, const ProfileSelection& profiles, const std::string& output_prefix)
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
        Slic3r::ModelObject* object = nullptr;
        switch (params.mode) {
        case CalibrationMode::temp_tower: object = calib_temp(model, params, config, bundle); break;
        default: result.message = "This calibration is not ported yet"; return result;
        }
        // Tab::reload_config() of the tabs the calibration changed.
        for (const PresetKind kind : {PresetKind::print, PresetKind::filament, PresetKind::printer}) {
            detail::reload_tab(kind);
        }
        result.presets_changed = true;
        if (!detail::write_objects({object}, output_prefix, result)) {
            return result;
        }
        result.status = SceneStatus::success;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
    }
    return result;
}

}  // namespace orcinus::orca
