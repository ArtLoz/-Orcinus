// The variable layer height of OrcaSlicer's 3D view (GLCanvas3D::LayersEditing):
// while it is open the desktop app keeps the selected object, the slicing
// parameters of its print and the layer height profile being edited, and
// every press on the bar adjusts the profile. The app does the same through a
// session the engine keeps from begin_layer_editing() to end_layer_editing();
// the profile the object takes is the app's to keep (ModelObject::layer_height_profile).

#include <mutex>
#include <string>
#include <vector>

#include "libslic3r/Model.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/Slicing.hpp"

#include "engine_context.hpp"
#include "orca_engine_adapter.hpp"

namespace orcinus::orca {
namespace {

// LayersEditing's object, alone in a model of its own, with its
// m_slicing_parameters, m_object_max_z and m_layer_height_profile.
struct LayerEditingSession {
    bool open{false};
    Slic3r::Model model;
    Slic3r::SlicingParameters slicing_parameters;
    float object_max_z{0.0f};
    std::vector<double> profile;
};

LayerEditingSession& session()
{
    static LayerEditingSession current;
    return current;
}

LayerEditing failure(SceneStatus status, std::string message)
{
    LayerEditing result;
    result.status = status;
    result.message = std::move(message);
    return result;
}

// LayersEditing::generate_layer_height_texture(): the profile is brought up
// to date with the object (PrintObject::update_layer_height_profile()), and
// the layers it makes (generate_object_layers()) are what the bar and the
// object show.
LayerEditing describe(LayerEditingSession& current)
{
    const Slic3r::ModelObject& object = *current.model.objects.front();
    Slic3r::PrintObject::update_layer_height_profile(object, current.slicing_parameters, current.profile);
    LayerEditing result;
    result.status = SceneStatus::success;
    result.profile = current.profile;
    result.layers = Slic3r::generate_object_layers(current.slicing_parameters, current.profile, false);
    result.object_max_z = current.object_max_z;
    result.layer_height = current.slicing_parameters.layer_height;
    result.min_layer_height = current.slicing_parameters.min_layer_height;
    result.max_layer_height = current.slicing_parameters.max_layer_height;
    result.object_print_z_height = current.slicing_parameters.object_print_z_height();
    result.fixed = Slic3r::check_object_layers_fixed(current.slicing_parameters, current.profile);
    return result;
}

}  // namespace

LayerEditing begin_layer_editing(
    const std::vector<PlateObject>& plate,
    const int object_index,
    const ProfileSelection& profiles,
    const ModelSettings& plate_settings
)
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().bundle == nullptr) {
        return failure(SceneStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }
    LayerEditingSession& current = session();
    current.open = false;
    try {
        // LayersEditing::set_config(): the configuration of the presets.
        Slic3r::DynamicPrintConfig config;
        std::string message;
        if (detail::select_profiles(*detail::engine().bundle, profiles, config, message) != SliceStatus::success) {
            return failure(SceneStatus::profile_not_found, message);
        }
        Slic3r::Model model;
        if (!detail::load_plate(plate, config, model, message)) {
            return failure(SceneStatus::model_read_failed, message);
        }
        if (object_index < 0 || object_index >= int(model.objects.size())) {
            return failure(SceneStatus::model_read_failed, "The plate has no such object");
        }
        // GLCanvas3D::set_config(): the shrinkage compensation of the plate's
        // print (Print::shrinkage_compensation()), applied as the background
        // process applies the plate.
        Slic3r::DynamicPrintConfig print_config = config;
        print_config.apply(detail::model_config(plate_settings), true);
        model.update_print_volume_state(detail::build_volume_of(print_config));
        Slic3r::Print print;
        print.set_plate_origin(Slic3r::to_3d(detail::plate_origin(print_config, detail::engine().plate_index, detail::engine().plate_count), 0.));
        print.set_plate_index(detail::engine().plate_index);
        print.apply(model, print_config);
        const Slic3r::Vec3d shrinkage = print.empty() ? Slic3r::Vec3d::Ones() : print.shrinkage_compensation();

        // LayersEditing::select_object() and update_slicing_parameters().
        current.model.clear_objects();
        current.model.add_object(*model.objects[std::size_t(object_index)]);
        const Slic3r::ModelObject& object = *current.model.objects.front();
        current.object_max_z = float(object.max_z());
        current.slicing_parameters = Slic3r::PrintObject::slicing_parameters(config, object, current.object_max_z, shrinkage);
        current.profile.clear();
        current.open = true;
        return describe(current);
    } catch (const std::exception& error) {
        return failure(SceneStatus::model_read_failed, error.what());
    }
}

LayerEditing edit_layer_heights(const LayerHeightEdit action, const double z, const double strength, const double band_width)
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    LayerEditingSession& current = session();
    if (!current.open) {
        return failure(SceneStatus::model_read_failed, "No object's layer heights are edited");
    }
    // LayersEditing::adjust_layer_height_profile()
    const Slic3r::ModelObject& object = *current.model.objects.front();
    Slic3r::PrintObject::update_layer_height_profile(object, current.slicing_parameters, current.profile);
    Slic3r::adjust_layer_height_profile(object, current.slicing_parameters, current.profile, z, strength, band_width,
                                        static_cast<Slic3r::LayerHeightEditActionType>(action));
    return describe(current);
}

LayerEditing adaptive_layer_heights(const double quality)
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    LayerEditingSession& current = session();
    if (!current.open) {
        return failure(SceneStatus::model_read_failed, "No object's layer heights are edited");
    }
    // LayersEditing::adaptive_layer_height_profile()
    Slic3r::ModelObject& object = *current.model.objects.front();
    current.profile = Slic3r::layer_height_profile_adaptive(current.slicing_parameters, object, float(quality));
    object.layer_height_profile.set(current.profile);
    return describe(current);
}

LayerEditing smooth_layer_heights(const int radius, const bool keep_min)
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    LayerEditingSession& current = session();
    if (!current.open) {
        return failure(SceneStatus::model_read_failed, "No object's layer heights are edited");
    }
    // LayersEditing::smooth_layer_height_profile()
    Slic3r::ModelObject& object = *current.model.objects.front();
    Slic3r::PrintObject::update_layer_height_profile(object, current.slicing_parameters, current.profile);
    current.profile = Slic3r::smooth_height_profile(current.profile, current.slicing_parameters,
                                                     Slic3r::HeightProfileSmoothingParams(unsigned(radius), keep_min));
    object.layer_height_profile.set(current.profile);
    return describe(current);
}

LayerEditing reset_layer_heights()
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    LayerEditingSession& current = session();
    if (!current.open) {
        return failure(SceneStatus::model_read_failed, "No object's layer heights are edited");
    }
    // LayersEditing::reset_layer_height_profile()
    current.model.objects.front()->layer_height_profile.clear();
    current.profile.clear();
    return describe(current);
}

LayerEditing accept_layer_heights()
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    LayerEditingSession& current = session();
    if (!current.open) {
        return failure(SceneStatus::model_read_failed, "No object's layer heights are edited");
    }
    // LayersEditing::accept_changes()
    current.model.objects.front()->layer_height_profile.set(current.profile);
    return describe(current);
}

void end_layer_editing()
{
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    LayerEditingSession& current = session();
    current.open = false;
    current.model.clear_objects();
    current.profile.clear();
}

}  // namespace orcinus::orca
