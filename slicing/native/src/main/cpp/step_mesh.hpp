#pragma once

// StepMeshDialog of the desktop app: the options a STEP file is meshed with,
// which it asks before a load or a replacement when the Preferences' "Show
// options when importing STEP file" is on.

#include <string>

#include "orca_engine_adapter.hpp"

namespace orcinus::orca::detail {

// The load stops for the dialog, which opens with these values; the file is
// loaded and kept for the dialog's count of triangles.
struct StepMeshPending {
    double linear_deflection;
    double angle_deflection;
    bool split_compound;
};

struct StepMeshParameters {
    double linear_deflection;
    double angle_deflection;
    bool split_compound;
};

// Plater::priv::load_files() (replacing false) and replace_volume_with_stl()
// (replacing true): the app configuration's deflections and split, or with
// the dialog on, the user's choice. Throws StepMeshPending while the choice is
// not made yet, and a RuntimeError for a file that cannot be read.
StepMeshParameters step_mesh_parameters(const std::string& path, const StepMeshChoice& choice, bool replacing);

}  // namespace orcinus::orca::detail
