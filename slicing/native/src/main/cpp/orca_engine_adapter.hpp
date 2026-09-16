#pragma once

#include <cstdint>
#include <functional>
#include <string>

namespace orcinus::orca {

// Stable boundary between the JNI bridge and OrcaSlicer. Orca types must not
// cross it.

std::string engine_version();

struct EngineDirectories {
    // Orca data directory. Its system/ folder holds the bundled vendor profiles,
    // as the desktop app's data directory does after its first start.
    std::string data_dir;
    // Orca resources directory with info/ and flush/.
    std::string resources_dir;
    std::string temporary_dir;
};

struct EngineInitialization {
    bool ready{false};
    std::string message;
};

// Loads the system profiles once per process. Later calls return the first result.
EngineInitialization initialize(const EngineDirectories& directories);

struct ProfileSelection {
    std::string printer;
    std::string filament;
    std::string process;
};

enum class SliceStatus : std::int64_t {
    success = 0,
    cancelled = 1,
    busy = 2,
    output_write_failed = 3,
    slicing_failed = 4,
    profile_not_found = 5,
    model_read_failed = 6,
    engine_not_ready = 7,
    invalid_print = 8,
};

struct SliceResult {
    SliceStatus status{SliceStatus::slicing_failed};
    std::string message;
    std::int64_t layer_count{0};
    std::int64_t estimated_print_time_seconds{0};
    std::int64_t filament_micrometers{0};
};

// Receives Orca's slicing status: percent in [0, 100] and its status text.
using ProgressCallback = std::function<void(int percent, const std::string& message)>;

// Slices an STL file, or the built-in 20 mm calibration cube when model_path is
// empty, and writes G-code to output_path only after a complete export.
SliceResult slice(
    const std::string& job_id,
    const std::string& model_path,
    const std::string& output_path,
    const ProfileSelection& profiles,
    const ProgressCallback& on_progress
);

// Returns true only when job_id is the active job and cancellation was requested.
bool cancel(const std::string& job_id);

enum class StlInspectionStatus : std::int64_t {
    success = 0,
    read_failed = 1,
    empty = 2,
    layer_analysis_failed = 3,
};

struct StlInspection {
    StlInspectionStatus status{StlInspectionStatus::read_failed};
    std::int64_t facet_count{0};
    std::int64_t width_micrometers{0};
    std::int64_t depth_micrometers{0};
    std::int64_t height_micrometers{0};
    std::int64_t sampling_step_micrometers{0};
    std::int64_t sampled_plane_count{0};
    std::int64_t non_empty_plane_count{0};
    std::int64_t contour_count{0};
};

StlInspection inspect_stl(const std::string& input_path);

}  // namespace orcinus::orca
