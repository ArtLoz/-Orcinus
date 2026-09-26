#pragma once

#include <map>
#include <string>
#include <vector>

#include "libslic3r/CustomGCode.hpp"
#include "libslic3r/Format/bbs_3mf.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/Semver.hpp"
#include "orca_engine_adapter.hpp"
#include "settings_dialogs.hpp"

namespace orcinus::orca::detail {

// A 3MF file as Plater::priv::load_files() reads it (Model::read_from_archive),
// with what it brought besides its objects.
struct Archive3mf {
    Archive3mf() = default;
    Archive3mf(const Archive3mf&) = delete;
    Archive3mf& operator=(const Archive3mf&) = delete;
    ~Archive3mf();

    // is_project_file of load_files(): the file opened as a project and holds
    // settings, which the presets take.
    bool load_config{false};
    // Those settings over FullPrintConfig's defaults, as
    // PresetBundle::load_config_model() takes them.
    Slic3r::DynamicPrintConfig config;
    Slic3r::Semver file_version;
    Slic3r::PlateDataPtrs plate_data;
    std::vector<Slic3r::Preset*> project_presets;
    // The filaments the plate needs for the extruders of the file's objects
    // (set_num_filaments); 0 needs none.
    std::size_t filament_count{0};
    // Model::plates_custom_gcodes of the file.
    std::map<int, Slic3r::CustomGCode::Info> custom_gcodes;
};

// Reads the 3MF file at path, as a project when project is set, and shows what
// the desktop app shows about it: the kind of file and its version, the
// settings it could not read and the presets whose G-code it changed. current
// is the configuration of the selected presets.
Slic3r::Model read_3mf(
    const std::string& path,
    bool project,
    const Slic3r::DynamicPrintConfig& current,
    SettingsDialogs& dialogs,
    Archive3mf& archive
);

// The build volume the objects of the file are placed in: the project's
// printer's for a project, the selected one's otherwise.
const Slic3r::DynamicPrintConfig& placing_config(const Archive3mf& archive, const Slic3r::DynamicPrintConfig& current);

// What the file brings into the presets once its objects are loaded: a
// project's presets and settings are selected, or the plate gets the
// filaments its objects need. result gets the first plate's settings and codes.
void apply_3mf(Archive3mf& archive, const std::string& file_name, SettingsDialogs& dialogs, ImportedModels& result);

}  // namespace orcinus::orca::detail
