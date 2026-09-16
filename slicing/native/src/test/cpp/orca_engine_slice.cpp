// Slices one STL with the app's engine facade, the same code path as the APK.
// scripts/golden.ps1 runs it on a device and compares the G-code with desktop
// OrcaSlicer.
//
// Usage: orca_engine_slice <data_dir> <resources_dir> <temporary_dir> <model.stl>
//                          <output.gcode> <printer> <filament> <process>

#include "orca_engine_adapter.hpp"

#include <cstdio>

namespace orca = orcinus::orca;

int main(int argc, char** argv)
{
    if (argc != 9) {
        std::fprintf(stderr,
            "usage: %s <data_dir> <resources_dir> <temporary_dir> <model.stl> <output.gcode> <printer> <filament> <process>\n",
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

    orca::ProfileSelection profiles;
    profiles.printer = argv[6];
    profiles.filament = argv[7];
    profiles.process = argv[8];
    const orca::SliceResult result = orca::slice("golden", argv[4], argv[5], profiles, {});

    std::printf("status=%lld layers=%lld time_s=%lld filament_um=%lld message=%s\n",
        static_cast<long long>(result.status),
        static_cast<long long>(result.layer_count),
        static_cast<long long>(result.estimated_print_time_seconds),
        static_cast<long long>(result.filament_micrometers),
        result.message.c_str());
    return result.status == orca::SliceStatus::success ? 0 : 1;
}
