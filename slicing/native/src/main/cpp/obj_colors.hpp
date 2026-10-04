#pragma once

// ObjColorDialog of the desktop app: an OBJ file with colours, of its vertices
// or of the materials of its MTL file, asks which filaments print them before
// Plater::priv::load_files() loads it.

#include <cassert>
#include <string>

// Format/OBJ.hpp counts on what Model.hpp includes before it.
#include "libslic3r/Model.hpp"
#include "orca_engine_adapter.hpp"

namespace orcinus::orca::detail {

// The load stops for the dialog, which opens on this; the file's colours are
// kept for the dialog's numbers of colours and for its answer.
struct ObjColorPending {
    ObjColorQuestion question;
};

// The obj_color_fun of Plater::priv::load_files() for the file at path, whose
// colours missing from it print with the first filament (filament_color, "#RRGGBB"):
// throws ObjColorPending while the dialog is not answered, and otherwise paints
// the model as the dialog's OK left it (ObjColorPanel::deal_thumbnail()), or as
// its Cancel does (cancel_paint_color()).
Slic3r::ObjImportColorFn obj_color_function(const std::string& path, const std::string& filament_color, const ObjColorChoice& choice);

}  // namespace orcinus::orca::detail
