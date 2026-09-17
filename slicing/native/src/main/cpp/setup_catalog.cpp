#include "setup_catalog.hpp"

#include <algorithm>
#include <fstream>
#include <sstream>
#include <unordered_map>

#include <boost/algorithm/string/predicate.hpp>
#include <boost/filesystem.hpp>
#include <boost/log/trivial.hpp>
#include <nlohmann/json.hpp>

#include "libslic3r/AppConfig.hpp"
#include "libslic3r/Config.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"

namespace orcinus::orca::setup {
namespace {

namespace fs = boost::filesystem;
using nlohmann::json;

json read_json(const fs::path& path)
{
    std::ifstream input(path.string());
    std::stringstream buffer;
    buffer << input.rdbuf();
    return json::parse(buffer.str());
}

// The nozzle diameters of a model, "0.4;0.6", without spaces.
std::vector<std::string> split_nozzles(std::string nozzles)
{
    nozzles.erase(std::remove(nozzles.begin(), nozzles.end(), ' '), nozzles.end());
    std::vector<std::string> result;
    std::stringstream stream(nozzles);
    for (std::string nozzle; std::getline(stream, nozzle, ';');) {
        if (!nozzle.empty()) {
            result.push_back(nozzle);
        }
    }
    return result;
}

// The JSON files of a directory in name order, which is how NTFS lists them to
// the desktop app; the order decides which bundle defines a filament first.
std::vector<fs::path> bundle_files(const fs::path& directory)
{
    std::vector<fs::path> files;
    if (!fs::is_directory(directory)) {
        return files;
    }
    for (const fs::directory_entry& entry : fs::directory_iterator(directory)) {
        if (!fs::is_directory(entry.path()) && boost::iequals(entry.path().extension().string(), ".json")) {
            files.push_back(entry.path());
        }
    }
    std::sort(files.begin(), files.end());
    return files;
}

class Loader {
public:
    explicit Loader(fs::path resources_dir) : m_resources_dir(std::move(resources_dir)) {}

    Catalog catalog;
    // m_OrcaFilaLibPath
    fs::path filament_library;

    // GuideFrame::LoadProfileFamily(); a bundle that fails to parse stops at the failure, as there.
    void load_family(const std::string& vendor, const fs::path& file)
    {
        const fs::path vendor_dir = fs::absolute(file.parent_path() / vendor);
        try {
            const json bundle = read_json(file);

            for (const json& entry : bundle.value("machine_model_list", json::array())) {
                const std::string model_id = entry.at("name").get<std::string>();
                const std::string sub_path = entry.at("sub_path").get<std::string>();
                const fs::path model_file = vendor_dir / sub_path;
                if (!fs::exists(model_file)) {
                    continue;
                }
                const json definition = read_json(model_file);
                Model model;
                model.vendor = vendor;
                model.model = model_id;
                model.name = definition.at("name").get<std::string>();
                model.nozzle_diameters = split_nozzles(definition.at("nozzle_diameter").get<std::string>());
                // A string of C-style escaped names or an array, as save_userguide_models reads it.
                const json materials = definition.value("default_materials", json());
                if (materials.is_array()) {
                    for (const json& material : materials) {
                        model.materials.push_back(material.get<std::string>());
                    }
                } else if (materials.is_string()) {
                    const std::string text = materials.get<std::string>();
                    Slic3r::unescape_strings_cstyle(text, model.materials);
                }
                fs::path cover = m_resources_dir / "profiles" / vendor / (model_id + "_cover.png");
                if (!fs::exists(cover)) {
                    cover = m_resources_dir / "web/image/printer" / (model_id + "_cover.png");
                }
                model.cover = cover.string();
                catalog.models.push_back(std::move(model));
            }

            for (const json& entry : bundle.value("machine_list", json::array())) {
                const std::string name = entry.at("name").get<std::string>();
                const std::string sub_path = entry.at("sub_path").get<std::string>();
                const fs::path machine_file = vendor_dir / sub_path;
                if (!fs::exists(machine_file)) {
                    continue;
                }
                const json definition = read_json(machine_file);
                if (definition.at("instantiation").get<std::string>() == "true") {
                    m_machines[name] = {definition.at("printer_model").get<std::string>(), definition.at("nozzle_diameter").at(0).get<std::string>()};
                }
            }

            json filament_list = m_library_filaments;
            const json filaments = bundle.value("filament_list", json::array());
            for (const json& entry : filaments) {
                filament_list[entry.at("name").get<std::string>()] = entry;
            }
            for (const json& entry : filaments) {
                const std::string name = entry.at("name").get<std::string>();
                const std::string sub_path = entry.at("sub_path").get<std::string>();
                if (catalog.filaments.count(name) > 0) {
                    continue;
                }
                const fs::path filament_file = vendor_dir / sub_path;
                if (!fs::exists(filament_file)) {
                    continue;
                }
                const json definition = read_json(filament_file);
                if (definition.at("instantiation").get<std::string>() != "true") {
                    continue;
                }
                std::string filament_vendor;
                std::string filament_type;
                if (filament_info(vendor_dir, filament_list, filament_file, filament_vendor, filament_type) != 0) {
                    continue;
                }
                Filament filament;
                filament.name = name;
                filament.vendor = filament_vendor;
                filament.type = filament_type;
                for (const json& printer : definition.value("compatible_printers", json::array())) {
                    const std::string printer_name = printer.get<std::string>();
                    if (const auto machine = m_machines.find(printer_name); machine != m_machines.end()) {
                        filament.models.insert(machine->second);
                    }
                }
                catalog.filaments.emplace(name, std::move(filament));
            }
            if (vendor == Slic3r::PresetBundle::ORCA_FILAMENT_LIBRARY) {
                m_library_filaments = filament_list;
            }
        } catch (const std::exception& error) {
            BOOST_LOG_TRIVIAL(error) << __FUNCTION__ << ": parse " << file.string() << " got exception: " << error.what();
        }
    }

private:
    struct CachedFilamentInfo {
        int status{-1};
        std::string vendor;
        std::string type;
    };

    // GuideFrame::GetFilamentInfo(): the filament_vendor and filament_type of a
    // preset, following its inherits chain; fills only the empty out-parameters.
    int filament_info(const fs::path& vendor_dir, const json& filament_list, const fs::path& file, std::string& out_vendor, std::string& out_type)
    {
        std::string vendor;
        std::string type;
        int status = 0;
        if (const auto cached = m_filament_info.find(file.string()); cached != m_filament_info.end()) {
            vendor = cached->second.vendor;
            type = cached->second.type;
            status = cached->second.status;
        } else {
            try {
                const json definition = read_json(file);
                if (definition.contains("filament_vendor")) {
                    vendor = definition.at("filament_vendor").at(0).get<std::string>();
                }
                if (definition.contains("filament_type")) {
                    type = definition.at("filament_type").at(0).get<std::string>();
                }
                if (vendor.empty() || type.empty()) {
                    if (definition.contains("inherits")) {
                        const std::string parent = definition.at("inherits").get<std::string>();
                        if (!filament_list.contains(parent)) {
                            status = -1;
                        } else {
                            const std::string sub_path = filament_list.at(parent).at("sub_path").get<std::string>();
                            fs::path parent_file = vendor_dir / sub_path;
                            if (!fs::exists(parent_file)) {
                                parent_file = filament_library / sub_path;
                            }
                            status = fs::exists(parent_file) ? filament_info(vendor_dir, filament_list, parent_file, vendor, type) : -1;
                        }
                    } else if (type.empty()) {
                        status = -1;
                    } else {
                        if (vendor.empty()) {
                            vendor = "Generic";
                        }
                        status = 0;
                    }
                }
            } catch (const std::exception& error) {
                BOOST_LOG_TRIVIAL(error) << __FUNCTION__ << ": parse " << file.string() << " got exception: " << error.what();
                status = -1;
            }
            m_filament_info[file.string()] = CachedFilamentInfo{status, vendor, type};
        }
        if (out_vendor.empty()) {
            out_vendor = vendor;
        }
        if (out_type.empty()) {
            out_type = type;
        }
        return status;
    }

    fs::path m_resources_dir;
    // m_ProfileJson["machine"]: instantiable printer presets, by name, with their model and first nozzle.
    std::map<std::string, std::pair<std::string, std::string>> m_machines;
    // m_OrcaFilaList
    json m_library_filaments = json::object();
    std::unordered_map<std::string, CachedFilamentInfo> m_filament_info;
};

}  // namespace

Catalog load(const std::string& data_dir, const std::string& resources_dir)
{
    const fs::path vendor_dir = fs::path(data_dir) / PRESET_SYSTEM_DIR;
    const fs::path rsrc_vendor_dir = fs::path(resources_dir) / "profiles";
    Loader loader(resources_dir);

    // The filament library first, installed or from the resources.
    const std::string library = Slic3r::PresetBundle::ORCA_FILAMENT_LIBRARY;
    std::set<std::string> loaded_vendors;
    if (fs::exists(vendor_dir / (library + ".json"))) {
        loader.filament_library = vendor_dir / library;
        loader.load_family(library, vendor_dir / (library + ".json"));
    } else {
        loader.filament_library = rsrc_vendor_dir / library;
        loader.load_family(library, rsrc_vendor_dir / (library + ".json"));
    }
    loaded_vendors.insert(library);

    // Then the installed bundles, then the others.
    for (const fs::path& directory : {vendor_dir, rsrc_vendor_dir}) {
        for (const fs::path& file : bundle_files(directory)) {
            const std::string vendor = file.stem().string();
            if (loaded_vendors.insert(vendor).second) {
                loader.load_family(vendor, file);
            }
        }
    }
    return std::move(loader.catalog);
}

std::vector<std::string> installed_nozzles(const Model& model, const Slic3r::AppConfig& config)
{
    std::vector<std::string> nozzles;
    for (const std::string& nozzle : model.nozzle_diameters) {
        if (config.get_variant(model.vendor, model.model, nozzle)) {
            nozzles.push_back(nozzle);
        }
    }
    return nozzles;
}

}  // namespace orcinus::orca::setup
