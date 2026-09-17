#pragma once

#include <map>
#include <set>
#include <string>
#include <vector>

namespace Slic3r {
class AppConfig;
}

namespace orcinus::orca::setup {

// The printers and filaments OrcaSlicer's Setup Wizard offers, read from the
// vendor bundles as GuideFrame::LoadProfileData() reads them for its pages
// (src/slic3r/GUI/WebGuideDialog.cpp): the bundles installed in the data
// directory first, then the other bundles the app ships in its resources.

// A printer model of a vendor bundle (machine_model_list).
struct Model {
    // The bundle, for example "Creality".
    std::string vendor;
    // The model id, for example "Creality K2 Plus".
    std::string model;
    // The name in the model's own file.
    std::string name;
    // Its variants: nozzle diameters, for example {"0.4", "0.6"}.
    std::vector<std::string> nozzle_diameters;
    // default_materials: filament presets the wizard selects with the model.
    std::vector<std::string> materials;
    // The cover image, <resources>/profiles/<vendor>/<model>_cover.png.
    std::string cover;
};

// An instantiable filament preset of a bundle (filament_list).
struct Filament {
    std::string name;
    // filament_vendor and filament_type, from the preset or the presets it inherits.
    std::string vendor;
    std::string type;
    // The printer models and nozzle diameters of its compatible_printers;
    // empty when the preset names none the bundles define, which the wizard
    // offers for every printer.
    std::set<std::pair<std::string, std::string>> models;
};

struct Catalog {
    std::vector<Model> models;
    // By preset name, the first bundle defining a name wins.
    std::map<std::string, Filament> filaments;
};

// Reads the catalogue from <data_dir>/system and <resources_dir>/profiles.
// Throws on files that are not JSON.
Catalog load(const std::string& data_dir, const std::string& resources_dir);

// GuideFrame::SaveProfileData(): the nozzle diameters of a model the app
// configuration has installed.
std::vector<std::string> installed_nozzles(const Model& model, const Slic3r::AppConfig& config);

}  // namespace orcinus::orca::setup
