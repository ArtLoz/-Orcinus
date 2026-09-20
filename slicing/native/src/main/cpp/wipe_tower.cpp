// The wipe tower of the plate, ported from OrcaSlicer's GUI: the desktop app
// draws it as a volume of its own and keeps its position in the project
// configuration. Ports PartPlate::get_extruders_under_cli(),
// PartPlate::estimate_wipe_tower_size(),
// PartPlateList::set_default_wipe_tower_pos_for_plate() and the condition
// GLCanvas3D::reload_scene() shows the tower under.

#include <algorithm>
#include <cmath>
#include <set>
#include <string>
#include <vector>

#include "libslic3r/BoundingBox.hpp"
#include "libslic3r/GCode/WipeTower.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/libslic3r.h"

#include "engine_context.hpp"
#include "orca_engine_adapter.hpp"

namespace orcinus::orca {
namespace {

// PartPlate.cpp: where the desktop app puts a new tower, before it is clamped
// into the plate. The I3 values are used by printers whose bed moves in Y.
constexpr float WIPE_TOWER_DEFAULT_X_POS = 165.0f;
constexpr float WIPE_TOWER_DEFAULT_Y_POS = 250.0f;
constexpr float I3_WIPE_TOWER_DEFAULT_X_POS = 0.0f;
constexpr float I3_WIPE_TOWER_DEFAULT_Y_POS = 250.0f;

int config_int(const Slic3r::DynamicPrintConfig& config, const std::string& key)
{
    const Slic3r::ConfigOption* const option = config.option(key);
    return option == nullptr ? 0 : option->getInt();
}

double config_float(const Slic3r::DynamicPrintConfig& config, const std::string& key, const double fallback = 0.0)
{
    const Slic3r::ConfigOption* const option = config.option(key);
    return option == nullptr ? fallback : option->getFloat();
}

bool config_bool(const Slic3r::DynamicPrintConfig& config, const std::string& key)
{
    const Slic3r::ConfigOption* const option = config.option(key);
    return option != nullptr && option->getBool();
}

// The value an object overrides, or 0 when it leaves the setting to the preset.
int object_int(const Slic3r::ModelObject& object, const std::string& key)
{
    const Slic3r::ConfigOption* const option = object.config.option(key);
    return option == nullptr ? 0 : option->getInt();
}

// PartPlate::get_extruders_under_cli(): the filaments printed on the plate, by
// what every object and its volumes, height ranges, supports, walls and infills
// are printed with. Sorted, without repeats, 1-based.
std::vector<int> plate_extruders(const Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config)
{
    std::vector<int> extruders;

    const int glb_support_intf_extr = config_int(config, "support_interface_filament");
    const int glb_support_extr = config_int(config, "support_filament");
    int glb_outer_wall_extr = config_int(config, "outer_wall_filament_id");
    int glb_inner_wall_extr = config_int(config, "inner_wall_filament_id");
    if (glb_outer_wall_extr == 0) glb_outer_wall_extr = glb_inner_wall_extr;
    if (glb_inner_wall_extr == 0) glb_inner_wall_extr = glb_outer_wall_extr;
    const int glb_sparse_infill_extr = config_int(config, "sparse_infill_filament_id");
    const int glb_internal_solid_extr = config_int(config, "internal_solid_filament_id");
    int glb_top_surface_extr = config_int(config, "top_surface_filament_id");
    int glb_bottom_surface_extr = config_int(config, "bottom_surface_filament_id");
    if (glb_top_surface_extr == 0) glb_top_surface_extr = glb_internal_solid_extr;
    if (glb_bottom_surface_extr == 0) glb_bottom_surface_extr = glb_internal_solid_extr;

    bool glb_support = config_bool(config, "enable_support");
    glb_support |= config_int(config, "raft_layers") > 0;

    for (const Slic3r::ModelObject* const object : model.objects) {
        // The desktop app walks the plate's printable copies; ours is the one
        // plate, so an object counts once it has a printable copy.
        const bool printable = std::any_of(
            object->instances.begin(),
            object->instances.end(),
            [](const Slic3r::ModelInstance* const instance) { return instance->printable; }
        );
        if (!printable) {
            continue;
        }

        for (Slic3r::ModelVolume* const volume : object->volumes) {
            const std::vector<int> volume_extruders = volume->get_extruders();
            extruders.insert(extruders.end(), volume_extruders.begin(), volume_extruders.end());
        }

        for (const auto& range : object->layer_config_ranges) {
            if (range.second.has("extruder")) {
                if (const int id = range.second.option("extruder")->getInt(); id > 0) {
                    extruders.push_back(id);
                }
            }
        }

        bool obj_support = glb_support;
        const Slic3r::ConfigOption* const obj_support_opt = object->config.option("enable_support");
        const Slic3r::ConfigOption* const obj_raft_opt = object->config.option("raft_layers");
        if (obj_support_opt != nullptr || obj_raft_opt != nullptr) {
            obj_support = obj_support_opt != nullptr && obj_support_opt->getBool();
            if (obj_raft_opt != nullptr) {
                obj_support |= obj_raft_opt->getInt() > 0;
            }
        }

        if (obj_support) {
            if (const int own = object_int(*object, "support_interface_filament"); own != 0) {
                extruders.push_back(own);
            } else if (glb_support_intf_extr != 0) {
                extruders.push_back(glb_support_intf_extr);
            }
            if (const int own = object_int(*object, "support_filament"); own != 0) {
                extruders.push_back(own);
            } else if (glb_support_extr != 0) {
                extruders.push_back(glb_support_extr);
            }
        }

        int obj_outer_wall_extr = object_int(*object, "outer_wall_filament_id");
        if (obj_outer_wall_extr == 0) {
            obj_outer_wall_extr = object_int(*object, "inner_wall_filament_id");
        }
        if (obj_outer_wall_extr != 0) {
            extruders.push_back(obj_outer_wall_extr);
        } else if (glb_outer_wall_extr != 0) {
            extruders.push_back(glb_outer_wall_extr);
        }

        int obj_inner_wall_extr = object_int(*object, "inner_wall_filament_id");
        if (obj_inner_wall_extr == 0) {
            obj_inner_wall_extr = object_int(*object, "outer_wall_filament_id");
        }
        if (obj_inner_wall_extr != 0) {
            extruders.push_back(obj_inner_wall_extr);
        } else if (glb_inner_wall_extr != 0) {
            extruders.push_back(glb_inner_wall_extr);
        }

        if (const int own = object_int(*object, "sparse_infill_filament_id"); own != 0) {
            extruders.push_back(own);
        } else if (glb_sparse_infill_extr != 0) {
            extruders.push_back(glb_sparse_infill_extr);
        }

        const int obj_internal_solid_extr = object_int(*object, "internal_solid_filament_id");
        if (obj_internal_solid_extr != 0) {
            extruders.push_back(obj_internal_solid_extr);
        } else if (glb_internal_solid_extr != 0) {
            extruders.push_back(glb_internal_solid_extr);
        }

        int obj_top_surface_extr = object_int(*object, "top_surface_filament_id");
        if (obj_top_surface_extr == 0) {
            obj_top_surface_extr = obj_internal_solid_extr;
        }
        if (obj_top_surface_extr != 0) {
            extruders.push_back(obj_top_surface_extr);
        } else if (glb_top_surface_extr != 0) {
            extruders.push_back(glb_top_surface_extr);
        }

        int obj_bottom_surface_extr = object_int(*object, "bottom_surface_filament_id");
        if (obj_bottom_surface_extr == 0) {
            obj_bottom_surface_extr = obj_internal_solid_extr;
        }
        if (obj_bottom_surface_extr != 0) {
            extruders.push_back(obj_bottom_surface_extr);
        } else if (glb_bottom_surface_extr != 0) {
            extruders.push_back(glb_bottom_surface_extr);
        }
    }

    std::sort(extruders.begin(), extruders.end());
    extruders.erase(std::unique(extruders.begin(), extruders.end()), extruders.end());
    return extruders;
}

// The tallest object of the plate, which the tower is printed up to.
double plate_height(const Slic3r::Model& model)
{
    double height = 0.0;
    for (const Slic3r::ModelObject* const object : model.objects) {
        height = std::max(object->bounding_box_exact().size().z(), height);
    }
    return height;
}

// PartPlate::estimate_wipe_tower_size(): the tower is as wide as the process
// preset says and deep enough for the filament wiped into it.
Slic3r::Vec3d estimate_size(
    const Slic3r::DynamicPrintConfig& config,
    const double width,
    const double wipe_volume,
    const int extruder_count,
    const int plate_extruder_size,
    const double max_height
)
{
    Slic3r::Vec3d size = Slic3r::Vec3d::Zero();
    if (plate_extruder_size == 0) {
        return size;
    }
    // A hard-coded layer height, as upstream uses for the estimate.
    double layer_height = 0.08;
    if (const Slic3r::ConfigOption* const option = config.option("layer_height"); option != nullptr) {
        layer_height = option->getFloat();
    }
    size(2) = max_height;

    const auto* const timelapse = config.option<Slic3r::ConfigOptionEnum<Slic3r::TimelapseType>>("timelapse_type");
    const bool enable_wrapping = config_bool(config, "enable_wrapping_detection");
    const bool need_wipe_tower = (timelapse != nullptr && timelapse->value == Slic3r::TimelapseType::tlSmooth) || enable_wrapping;
    const double extra_spacing = config_float(config, "prime_tower_infill_gap") / 100.0;
    const auto* const wall_type = config.option<Slic3r::ConfigOptionEnum<Slic3r::WipeTowerWallType>>("wipe_tower_wall_type");
    const bool use_rib_wall = wall_type != nullptr && wall_type->value == Slic3r::WipeTowerWallType::wtwRib;
    double rib_width = config_float(config, "wipe_tower_rib_width");

    // The filament a tool change pushes out, for a printer that changes with one nozzle.
    double filament_change_volume = 0.0;
    {
        double length = 0.0;
        if (const auto* const lengths = config.option<Slic3r::ConfigOptionFloats>("filament_change_length");
            lengths != nullptr && !lengths->values.empty()) {
            length = *std::max_element(lengths->values.begin(), lengths->values.end());
        }
        double diameter = 1.75;
        if (const auto* const diameters = config.option<Slic3r::ConfigOptionFloats>("filament_diameter");
            diameters != nullptr && !diameters->values.empty()) {
            diameter = *std::max_element(diameters->values.begin(), diameters->values.end());
        }
        filament_change_volume = length * PI * diameter * diameter / 4.0;
    }

    double volume = wipe_volume * (extruder_count == 2 ? plate_extruder_size : (plate_extruder_size - 1));
    if (extruder_count == 2) {
        volume += filament_change_volume * (plate_extruder_size / 2);
    }

    if (use_rib_wall) {
        double depth = std::sqrt(volume / layer_height * extra_spacing);
        if (need_wipe_tower || plate_extruder_size > 1) {
            const float min_depth = Slic3r::WipeTower::get_limit_depth_by_height(static_cast<float>(max_height));
            const double volume_depth = depth;
            depth = std::max(static_cast<double>(min_depth), depth);
            rib_width = std::min(rib_width, depth / 2);
            depth = rib_width / std::sqrt(2.0)
                + std::max(depth + config_float(config, "wipe_tower_extra_rib_length"), volume_depth);
            size(0) = size(1) = depth;
        }
    } else {
        double depth = volume / (layer_height * width) * extra_spacing;
        if (need_wipe_tower || depth > EPSILON) {
            const float min_depth = Slic3r::WipeTower::get_limit_depth_by_height(static_cast<float>(max_height));
            depth = std::max(static_cast<double>(min_depth), depth);
        }
        size(0) = width;
        size(1) = depth;
    }
    return size;
}

// PartPlateList::set_default_wipe_tower_pos_for_plate(): a tower the project
// does not place yet stands at the desktop app's own position, clamped into the
// plate with room for its brim.
Slic3r::Vec2d default_position(
    const Slic3r::DynamicPrintConfig& config,
    const Slic3r::BoundingBoxf& plate,
    const Slic3r::Vec3d& size,
    const double brim_width
)
{
    const auto* const structure = config.option<Slic3r::ConfigOptionEnum<Slic3r::PrinterStructure>>("printer_structure");
    const bool i3 = structure != nullptr && structure->value == Slic3r::PrinterStructure::psI3;
    double x = i3 ? I3_WIPE_TOWER_DEFAULT_X_POS : WIPE_TOWER_DEFAULT_X_POS;
    double y = i3 ? I3_WIPE_TOWER_DEFAULT_Y_POS : WIPE_TOWER_DEFAULT_Y_POS;

    const double margin = WIPE_TOWER_MARGIN + brim_width;
    if (x + margin + size(0) > plate.max(0)) {
        x = plate.max(0) - size(0) - margin;
    } else if (x < margin + plate.min(0)) {
        x = margin + plate.min(0);
    }
    if (y + margin + size(1) > plate.max(1)) {
        y = plate.max(1) - size(1) - margin;
    } else if (y < margin) {
        y = margin;
    }
    return {x, y};
}

}  // namespace

WipeTowerState describe_wipe_tower(
    const std::vector<PlateObject>& plate,
    const ProfileSelection& profiles,
    const ModelSettings& plate_settings
)
{
    WipeTowerState result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }

    try {
        Slic3r::DynamicPrintConfig config;
        std::string message;
        if (detail::select_profiles(*detail::engine().bundle, profiles, config, message) != SliceStatus::success) {
            result.status = SceneStatus::profile_not_found;
            result.message = message;
            return result;
        }
        config.apply(detail::model_config(plate_settings), true);

        Slic3r::Model model;
        if (!detail::load_plate(plate, config, model, message)) {
            result.status = SceneStatus::model_read_failed;
            result.message = message;
            return result;
        }

        result.status = SceneStatus::success;
        result.rotation = config_float(config, "wipe_tower_rotation_angle");
        result.filaments = plate_extruders(model, config);
        result.height = plate_height(model);

        // GLCanvas3D::reload_scene(): the plate draws a tower once the process
        // preset asks for one and more than one filament is printed on it.
        const bool enabled = config_bool(config, "enable_prime_tower");
        const bool timelapse = [&config] {
            const auto* const option = config.option<Slic3r::ConfigOptionEnum<Slic3r::TimelapseType>>("timelapse_type");
            return option != nullptr && option->value == Slic3r::TimelapseType::tlSmooth;
        }();
        const bool need_tower = timelapse || config_bool(config, "enable_wrapping_detection");
        const std::size_t filament_count = detail::engine().bundle->filament_presets.size();
        result.shown = enabled
            && (need_tower || filament_count > 1)
            && !model.objects.empty()
            && (need_tower || result.filaments.size() > 1);
        if (!result.shown) {
            return result;
        }

        const double width = config_float(config, "prime_tower_width");
        const double volume = config_float(config, "prime_volume");
        const int extruder_count = static_cast<int>(detail::engine().bundle->get_printer_extruder_count());
        const Slic3r::Vec3d size = estimate_size(
            config,
            width,
            volume,
            extruder_count,
            static_cast<int>(result.filaments.size()),
            result.height
        );
        result.width = size(0);
        result.depth = size(1);

        double brim_width = config_float(config, "prime_tower_brim_width");
        if (brim_width < 0) {
            brim_width = Slic3r::WipeTower::get_auto_brim_by_height(static_cast<float>(result.height));
        }
        result.brim_width = brim_width;

        // The project places the tower; without a position it stands where the
        // desktop app puts a new one.
        const auto* const x = config.option<Slic3r::ConfigOptionFloats>("wipe_tower_x");
        const auto* const y = config.option<Slic3r::ConfigOptionFloats>("wipe_tower_y");
        const bool placed = plate_settings.keys.end()
            != std::find(plate_settings.keys.begin(), plate_settings.keys.end(), std::string("wipe_tower_x"));
        if (placed && x != nullptr && !x->values.empty() && y != nullptr && !y->values.empty()) {
            result.x = x->values.front();
            result.y = y->values.front();
        } else {
            const Slic3r::BoundingBoxf area(config.option<Slic3r::ConfigOptionPoints>("printable_area")->values);
            const Slic3r::Vec2d position = default_position(config, area, size, brim_width);
            result.x = position(0);
            result.y = position(1);
        }
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        result.shown = false;
        return result;
    }
}

}  // namespace orcinus::orca
