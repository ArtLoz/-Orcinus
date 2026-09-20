// Slices one STL with the app's engine facade, the same code path as the APK.
// scripts/golden.ps1 runs it on a device and compares the G-code with desktop
// OrcaSlicer.
//
// Usage: orca_engine_slice <data_dir> <resources_dir> <temporary_dir> <model.stl>
//                          <output.gcode> <printer_model> <printer> <filament> <process>
//
// The printer model and the filament are installed as the Setup Wizard installs
// them unless the data directory has the model installed.

#include "orca_engine_adapter.hpp"

#include <algorithm>
#include <cstdio>

namespace orca = orcinus::orca;

int main(int argc, char** argv)
{
    if (argc != 10) {
        std::fprintf(stderr,
            "usage: %s <data_dir> <resources_dir> <temporary_dir> <model.stl> <output.gcode> <printer_model> <printer> <filament> <process>\n",
            argv[0]);
        return 2;
    }

    orca::EngineDirectories directories;
    directories.data_dir = argv[1];
    directories.resources_dir = argv[2];
    directories.temporary_dir = argv[3];
    const orca::EngineInitialization initialization = orca::initialize(directories);
    if (!initialization.ready) {
        std::fprintf(stderr, "engine not ready: %s\n", initialization.message.c_str());
        return 3;
    }

    const std::string printer_model = argv[6];
    const orca::PresetState presets = orca::describe_presets();
    const bool installed = std::any_of(presets.printers.begin(), presets.printers.end(), [&printer_model](const orca::PresetItem& item) {
        return item.name == printer_model;
    });
    if (!installed) {
        const orca::PresetState setup = orca::apply_setup({printer_model}, {argv[8]});
        if (setup.status != orca::SceneStatus::success) {
            std::fprintf(stderr, "setup failed: %s\n", setup.message.c_str());
            return 3;
        }
    }

    orca::ProfileSelection profiles;
    profiles.printer = argv[7];
    profiles.filament = argv[8];
    profiles.process = argv[9];
    orca::PlateObject model;
    model.model_path = argv[4];
    // The golden run prints with the presets alone: the plate overrides nothing.
    const orca::SliceResult result = orca::slice("golden", {model}, argv[5], {}, profiles, {}, {});

    std::printf("status=%lld layers=%lld time_s=%lld filament_um=%lld message=%s\n",
        static_cast<long long>(result.status),
        static_cast<long long>(result.layer_count),
        static_cast<long long>(result.estimated_print_time_seconds),
        static_cast<long long>(result.filament_micrometers),
        result.message.c_str());
    return result.status == orca::SliceStatus::success ? 0 : 1;
}
