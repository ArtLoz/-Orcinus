#include "orca_engine_adapter.hpp"

#include <algorithm>
#include <cmath>
#include <memory>
#include <mutex>
#include <set>
#include <utility>
#include <vector>

#include <boost/filesystem.hpp>

#include "android_log_sink.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/BuildVolume.hpp"
#include "libslic3r/Format/STL.hpp"
#include "libslic3r/GCode/GCodeProcessor.hpp"
#include "libslic3r/Layer.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "libslic3r/TriangleMeshSlicer.hpp"
#include "libslic3r/Utils.hpp"
#include "libslic3r_version.h"

namespace orcinus::orca {
namespace {

namespace fs = boost::filesystem;

// Warning, the level Orca's desktop app uses by default.
constexpr unsigned int orca_log_level = 2;
constexpr double geometry_preview_max_step_mm = 0.2;
constexpr std::size_t geometry_preview_max_planes = 10'000;

std::mutex engine_mutex;
std::unique_ptr<Slic3r::PresetBundle> preset_bundle;
std::unique_ptr<EngineInitialization> initialization;

std::mutex job_mutex;
std::string active_job_id;
Slic3r::Print* active_print = nullptr;
bool cancel_requested = false;

// Releases the job slot taken by acquire_job().
class ActiveJob final {
public:
    ActiveJob() = default;
    ActiveJob(const ActiveJob&) = delete;
    ActiveJob& operator=(const ActiveJob&) = delete;

    ~ActiveJob()
    {
        const std::lock_guard<std::mutex> lock(job_mutex);
        active_job_id.clear();
        active_print = nullptr;
        cancel_requested = false;
    }
};

bool acquire_job(const std::string& job_id)
{
    const std::lock_guard<std::mutex> lock(job_mutex);
    if (!active_job_id.empty()) {
        return false;
    }
    active_job_id = job_id;
    active_print = nullptr;
    cancel_requested = false;
    return true;
}

// Registers the print for cancellation; returns false if cancel() already ran.
bool attach_print(Slic3r::Print* print)
{
    const std::lock_guard<std::mutex> lock(job_mutex);
    active_print = print;
    return !cancel_requested;
}

bool cancellation_requested()
{
    const std::lock_guard<std::mutex> lock(job_mutex);
    return cancel_requested;
}

SliceResult failure(const SliceStatus status, std::string message)
{
    SliceResult result;
    result.status = status;
    result.message = std::move(message);
    return result;
}

// Applies the selection the way the desktop app restores it at start-up: the
// printer model and filament are marked as installed in AppConfig, as the
// desktop setup wizard does, and the process and filament remembered for that
// printer are selected by load_selections().
SliceStatus select_profiles(
    Slic3r::PresetBundle& bundle,
    const ProfileSelection& profiles,
    Slic3r::DynamicPrintConfig& config,
    std::string& message
)
{
    const Slic3r::Preset* printer = bundle.printers.find_preset(profiles.printer, false);
    if (printer == nullptr || printer->vendor == nullptr) {
        message = "Unknown printer profile: " + profiles.printer;
        return SliceStatus::profile_not_found;
    }
    if (bundle.prints.find_preset(profiles.process, false) == nullptr) {
        message = "Unknown process profile: " + profiles.process;
        return SliceStatus::profile_not_found;
    }
    if (bundle.filaments.find_preset(profiles.filament, false) == nullptr) {
        message = "Unknown filament profile: " + profiles.filament;
        return SliceStatus::profile_not_found;
    }

    Slic3r::AppConfig app_config;
    app_config.set_variant(
        printer->vendor->id,
        printer->config.opt_string("printer_model"),
        printer->config.opt_string("printer_variant"),
        true
    );
    app_config.set(Slic3r::AppConfig::SECTION_FILAMENTS, profiles.filament, "true");
    app_config.set("presets", PRESET_PRINTER_NAME, profiles.printer);
    app_config.set_printer_setting(profiles.printer, PRESET_PRINT_NAME, profiles.process);
    app_config.set_printer_setting(profiles.printer, PRESET_FILAMENT_NAME, profiles.filament);
    bundle.load_selections(app_config);

    if (bundle.printers.get_selected_preset_name() != profiles.printer
        || bundle.prints.get_selected_preset_name() != profiles.process
        || bundle.filament_presets.empty()
        || bundle.filament_presets.front() != profiles.filament) {
        message = "Orca selected printer '" + bundle.printers.get_selected_preset_name() + "', process '"
            + bundle.prints.get_selected_preset_name() + "', filament '"
            + (bundle.filament_presets.empty() ? std::string() : bundle.filament_presets.front())
            + "': the requested profiles are not compatible";
        return SliceStatus::profile_not_found;
    }

    config = bundle.full_config();
    return SliceStatus::success;
}

bool load_model(const std::string& model_path, Slic3r::Model& model)
{
    if (model_path.empty()) {
        Slic3r::ModelObject* object = model.add_object();
        object->name = "calibration-cube-20mm";
        object->add_volume(Slic3r::TriangleMesh(Slic3r::its_make_cube(20.0, 20.0, 20.0)));
        return true;
    }
    return Slic3r::load_stl(model_path.c_str(), &model) && !model.objects.empty();
}

// Places a loaded object as the desktop app does when it is added to an empty
// plate (Plater::priv::load_model_objects): the mesh is centred around the
// origin, the instance stands on the bed centre, and the object rests on the
// plate. Doing the same steps keeps the coordinates bit-identical to desktop.
void place_on_bed(Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config)
{
    const Slic3r::BuildVolume build_volume(
        config.option<Slic3r::ConfigOptionPoints>("printable_area")->values,
        config.opt_float("printable_height"),
        {},
        {});
    for (Slic3r::ModelObject* object : model.objects) {
        object->center_around_origin();
        Slic3r::ModelInstance* instance = object->add_instance();
        instance->set_offset(Slic3r::to_3d(build_volume.bed_center(), -object->origin_translation(2)));
        object->ensure_on_bed();
    }
}

std::int64_t printed_layer_count(const Slic3r::Print& print)
{
    std::set<coordf_t> heights;
    for (const Slic3r::PrintObject* object : print.objects()) {
        for (const Slic3r::Layer* layer : object->layers()) {
            heights.insert(layer->print_z);
        }
        for (const Slic3r::SupportLayer* layer : object->support_layers()) {
            heights.insert(layer->print_z);
        }
    }
    return static_cast<std::int64_t>(heights.size());
}

void remove_file(const std::string& path)
{
    boost::system::error_code ignored;
    fs::remove(path, ignored);
}

}  // namespace

std::string engine_version()
{
    return "orca-upstream/" SoftFever_VERSION " bridge/1.0";
}

EngineInitialization initialize(const EngineDirectories& directories)
{
    const std::lock_guard<std::mutex> lock(engine_mutex);
    if (initialization != nullptr) {
        return *initialization;
    }

    install_android_log_sink();
    Slic3r::set_logging_level(orca_log_level);
    Slic3r::set_resources_dir(directories.resources_dir);
    Slic3r::set_data_dir(directories.data_dir);
    Slic3r::set_temporary_dir(directories.temporary_dir);

    auto result = std::make_unique<EngineInitialization>();
    try {
        fs::create_directories(fs::path(directories.data_dir) / PRESET_USER_DIR);
        fs::create_directories(directories.temporary_dir);

        auto bundle = std::make_unique<Slic3r::PresetBundle>();
        Slic3r::AppConfig app_config;
        // Same substitution rule as the desktop app's start-up.
        bundle->load_presets(app_config, Slic3r::ForwardCompatibilitySubstitutionRule::EnableSystemSilent);
        if (bundle->printers.size() <= bundle->printers.num_default_presets()) {
            result->message = "No system printer profiles in " + directories.data_dir;
        } else {
            preset_bundle = std::move(bundle);
            result->ready = true;
        }
    } catch (const std::exception& error) {
        result->message = error.what();
    }

    initialization = std::move(result);
    return *initialization;
}

SliceResult slice(
    const std::string& job_id,
    const std::string& model_path,
    const std::string& output_path,
    const ProfileSelection& profiles,
    const ProgressCallback& on_progress
)
{
    if (!acquire_job(job_id)) {
        return failure(SliceStatus::busy, "Another slicing job is running");
    }
    const ActiveJob active_job;

    const std::lock_guard<std::mutex> engine_lock(engine_mutex);
    if (preset_bundle == nullptr) {
        return failure(SliceStatus::engine_not_ready, "OrcaSlicer profiles are not loaded");
    }

    const std::string temporary_path = output_path + ".part";
    try {
        Slic3r::DynamicPrintConfig config;
        std::string message;
        if (const SliceStatus status = select_profiles(*preset_bundle, profiles, config, message);
            status != SliceStatus::success) {
            return failure(status, message);
        }

        Slic3r::Model model;
        if (!load_model(model_path, model)) {
            return failure(SliceStatus::model_read_failed, "Unable to read model " + model_path);
        }
        place_on_bed(model, config);

        Slic3r::Print print;
        print.set_status_callback([&on_progress](const Slic3r::PrintBase::SlicingStatus& status) {
            if (on_progress && status.percent >= 0) {
                on_progress(status.percent, status.text);
            }
        });
        if (!attach_print(&print)) {
            return failure(SliceStatus::cancelled, {});
        }

        print.apply(model, config);
        Slic3r::StringObjectException warning;
        const Slic3r::StringObjectException error = print.validate(&warning);
        if (!error.string.empty()) {
            return failure(SliceStatus::invalid_print, error.string);
        }
        if (cancellation_requested()) {
            return failure(SliceStatus::cancelled, {});
        }

        print.is_BBL_printer() = preset_bundle->is_bbl_vendor();
        print.process();

        Slic3r::GCodeProcessorResult gcode_result;
        print.export_gcode(temporary_path, &gcode_result, nullptr);
        boost::system::error_code rename_error;
        fs::rename(temporary_path, output_path, rename_error);
        if (rename_error) {
            remove_file(temporary_path);
            return failure(SliceStatus::output_write_failed, rename_error.message());
        }

        using TimeMode = Slic3r::PrintEstimatedStatistics::ETimeMode;
        const float print_time =
            gcode_result.print_statistics.modes[static_cast<std::size_t>(TimeMode::Normal)].time;

        SliceResult result;
        result.status = SliceStatus::success;
        result.layer_count = printed_layer_count(print);
        result.estimated_print_time_seconds = std::llround(print_time);
        result.filament_micrometers = std::llround(print.print_statistics().total_used_filament * 1'000.0);
        return result;
    } catch (const Slic3r::CanceledException&) {
        remove_file(temporary_path);
        return failure(SliceStatus::cancelled, {});
    } catch (const std::exception& error) {
        remove_file(temporary_path);
        return failure(SliceStatus::slicing_failed, error.what());
    }
}

bool cancel(const std::string& job_id)
{
    const std::lock_guard<std::mutex> lock(job_mutex);
    if (active_job_id.empty() || active_job_id != job_id) {
        return false;
    }
    cancel_requested = true;
    if (active_print != nullptr) {
        active_print->cancel();
    }
    return true;
}

StlInspection inspect_stl(const std::string& input_path)
{
    Slic3r::TriangleMesh model;
    if (!model.ReadSTLFile(input_path.c_str(), true)) {
        return StlInspection{};
    }
    if (model.empty()) {
        StlInspection empty;
        empty.status = StlInspectionStatus::empty;
        return empty;
    }
    const Slic3r::TriangleMeshStats& stats = model.stats();

    const auto make_result = [&](const StlInspectionStatus status) {
        StlInspection inspection;
        inspection.status = status;
        inspection.facet_count = stats.number_of_facets;
        inspection.width_micrometers = std::llround(stats.size.x() * 1'000.0);
        inspection.depth_micrometers = std::llround(stats.size.y() * 1'000.0);
        inspection.height_micrometers = std::llround(stats.size.z() * 1'000.0);
        return inspection;
    };

    const double height_mm = stats.size.z();
    if (!std::isfinite(height_mm) || height_mm < 0.0) {
        return make_result(StlInspectionStatus::layer_analysis_failed);
    }
    if (height_mm == 0.0) {
        return make_result(StlInspectionStatus::success);
    }

    const double requested_plane_count = std::ceil(height_mm / geometry_preview_max_step_mm);
    if (!std::isfinite(requested_plane_count) || requested_plane_count < 1.0
        || requested_plane_count > static_cast<double>(geometry_preview_max_planes)) {
        return make_result(StlInspectionStatus::layer_analysis_failed);
    }
    const auto plane_count = static_cast<std::size_t>(requested_plane_count);
    const double sampling_step_mm = height_mm / static_cast<double>(plane_count);

    std::vector<float> plane_heights;
    plane_heights.reserve(plane_count);
    for (std::size_t index = 0; index < plane_count; ++index) {
        plane_heights.emplace_back(static_cast<float>(
            stats.min.z() + (static_cast<double>(index) + 0.5) * sampling_step_mm
        ));
    }

    std::vector<Slic3r::Polygons> slices;
    try {
        slices = Slic3r::slice_mesh(model.its, plane_heights, Slic3r::MeshSlicingParams{}, [] {});
    } catch (...) {
        return make_result(StlInspectionStatus::layer_analysis_failed);
    }

    StlInspection result = make_result(StlInspectionStatus::success);
    result.sampling_step_micrometers = std::max<std::int64_t>(1, std::llround(sampling_step_mm * 1'000.0));
    result.sampled_plane_count = static_cast<std::int64_t>(slices.size());
    for (const Slic3r::Polygons& slice : slices) {
        if (!slice.empty()) {
            ++result.non_empty_plane_count;
            result.contour_count += static_cast<std::int64_t>(slice.size());
        }
    }
    return result;
}

}  // namespace orcinus::orca
