#pragma once

// What a plate keeps of its slice for the 3MF files of the project and of its
// sliced plates. The desktop app's plate keeps its print and the G-code
// processor's result, and PartPlateList::store_to_3mf_structure() and
// Plater::priv::generate_first_layer_bbox() read them when a 3MF file is
// written. The app's engine keeps no print between requests, so the slice
// writes what those read into a file the plate keeps beside its G-code.

#include <string>

namespace Slic3r {
class Print;
struct GCodeProcessorResult;
struct PlateData;
struct PlateBBoxData;
}  // namespace Slic3r

namespace orcinus::orca::detail {

// Writes the slice info of print, whose G-code result is result, into path;
// false when the file cannot be written.
bool write_slice_info(Slic3r::Print& print, Slic3r::GCodeProcessorResult& result, const std::string& path);

// The slice info write_slice_info() wrote into path: the fields
// store_to_3mf_structure() takes from the slice into plate (the filaments
// without their types and colours, which the export adds), and the plate's
// first layer (generate_first_layer_bbox()); false when it cannot be read.
bool read_slice_info(const std::string& path, Slic3r::PlateData& plate, Slic3r::PlateBBoxData& first_layer);

}  // namespace orcinus::orca::detail
