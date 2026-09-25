// Device tests for the Android facade over OrcaSlicer. OrcaSlicer's own suites
// cover the algorithms; these cover what the app adds: installing the bundled
// vendor profiles through the Setup Wizard and selecting presets, the full
// slice/export/cancel path, and the plate and model geometry exported for the
// 3D view.
//
// scripts/engine-test.ps1 pushes the vendor profiles and upstream test data to
// ENGINE_DEVICE_TEST_DIR before running this executable. It runs the hidden
// [FirstRun] cases first in an empty data directory, then the others in another.

#include <catch2/catch_approx.hpp>
#include <catch2/catch_test_macros.hpp>

#include <algorithm>
#include <array>
#include <cmath>
#include <utility>
#include <functional>
#include <chrono>
#include <cstdint>
#include <cctype>
#include <cstring>
#include <limits>
#include <map>
#include <set>
#include <sstream>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>

#include <boost/filesystem.hpp>

#include <miniz/miniz.h>

#include <BRepPrimAPI_MakeBox.hxx>
#include <STEPControl_Writer.hxx>
#include <gp_Pnt.hxx>

#include "orca_engine_adapter.hpp"
#include "toolpaths_file.hpp"

namespace orca = orcinus::orca;
namespace fs = boost::filesystem;

namespace {

const std::string device_dir = ENGINE_DEVICE_TEST_DIR;

orca::ProfileSelection k2_plus_profiles()
{
    orca::ProfileSelection profiles;
    profiles.printer = "Creality K2 Plus 0.4 nozzle";
    profiles.filament = "Generic PLA @K2 Plus-all";
    profiles.process = "0.20mm Standard @Creality K2 Plus 0.4 nozzle";
    return profiles;
}

const std::string data_dir = device_dir + "/orca/data";

void initialize_engine()
{
    orca::EngineDirectories directories;
    directories.data_dir = data_dir;
    directories.resources_dir = device_dir + "/orca/resources";
    directories.temporary_dir = device_dir + "/tmp/orca";
    const orca::EngineInitialization initialization = orca::initialize(directories);
    INFO(initialization.message);
    REQUIRE(initialization.ready);
}

const orca::PresetItem* find_item(const std::vector<orca::PresetItem>& items, const std::string& name)
{
    const auto item = std::find_if(items.begin(), items.end(), [&name](const orca::PresetItem& candidate) { return candidate.name == name; });
    return item == items.end() ? nullptr : &*item;
}

// The engine with the K2 Plus installed as the Setup Wizard installs it.
void require_engine()
{
    initialize_engine();
    const orca::PresetState presets = orca::describe_presets();
    INFO(presets.message);
    REQUIRE(presets.status == orca::SceneStatus::success);
    if (find_item(presets.printers, "Creality K2 Plus") == nullptr) {
        const orca::PresetState installed = orca::apply_setup({"Creality K2 Plus"}, {"Generic PLA @K2 Plus-all"});
        INFO(installed.message);
        REQUIRE(installed.status == orca::SceneStatus::success);
    }
}

std::string output_path(const std::string& name)
{
    const fs::path path = fs::path(device_dir) / "tmp" / name;
    fs::remove(path);
    return path.string();
}

std::string read_file(const std::string& path)
{
    std::ifstream input(path, std::ios::binary);
    return {std::istreambuf_iterator<char>(input), std::istreambuf_iterator<char>()};
}

std::uint32_t read_u32(const std::string& data, const std::size_t offset)
{
    std::uint32_t value = 0;
    std::memcpy(&value, data.data() + offset, sizeof(value));
    return value;
}

// A plate with the object of model_path, empty for the calibration cube; an
// empty placement places it as a new object.
std::vector<orca::PlateObject> plate_of(const std::string& model_path, const std::vector<double>& placement = {})
{
    orca::PlateObject object;
    object.model_path = model_path;
    if (!placement.empty()) {
        orca::ObjectPlacement& instance = object.instances.emplace_back();
        instance.matrix = placement;
    }
    return {object};
}

std::vector<double> matrix_of(const orca::ModelInspection& inspection)
{
    return {inspection.instance_matrix.begin(), inspection.instance_matrix.end()};
}

struct Bounds {
    double min_x{std::numeric_limits<double>::max()};
    double min_y{std::numeric_limits<double>::max()};
    double max_x{std::numeric_limits<double>::lowest()};
    double max_y{std::numeric_limits<double>::lowest()};
};

// The extent of the extrusions from the second layer on, which outline the objects.
Bounds extrusion_bounds(const std::string& gcode_path)
{
    std::istringstream gcode(read_file(gcode_path));
    std::string line;
    Bounds bounds;
    int layer = 0;
    while (std::getline(gcode, line)) {
        if (line.rfind(";LAYER_CHANGE", 0) == 0) {
            ++layer;
        }
        const std::size_t x = line.find(" X");
        const std::size_t y = line.find(" Y");
        if (layer < 2 || line.rfind("G1 ", 0) != 0 || x == std::string::npos || y == std::string::npos || line.find(" E") == std::string::npos) {
            continue;
        }
        const double px = std::stod(line.substr(x + 2));
        const double py = std::stod(line.substr(y + 2));
        bounds.min_x = std::min(bounds.min_x, px);
        bounds.max_x = std::max(bounds.max_x, px);
        bounds.min_y = std::min(bounds.min_y, py);
        bounds.max_y = std::max(bounds.max_y, py);
    }
    return bounds;
}

}  // namespace

TEST_CASE("On first run the Setup Wizard is required, and closing it installs Orca's default printer", "[.][FirstRun]")
{
    initialize_engine();
    const std::string config_path = data_dir + "/OrcaSlicer.conf";
    REQUIRE_FALSE(fs::exists(config_path));

    const orca::PresetState first = orca::describe_presets();

    INFO(first.message);
    REQUIRE(first.status == orca::SceneStatus::success);
    CHECK(first.setup_required);

    const orca::PresetState closed = orca::apply_default_setup();

    INFO(closed.message);
    REQUIRE(closed.status == orca::SceneStatus::success);
    CHECK_FALSE(closed.setup_required);
    CHECK(closed.selection.printer == "MyKlipper 0.4 nozzle");
    CHECK(closed.selection.filament == "Generic PLA @System");
    CHECK_FALSE(closed.selection.process.empty());
    const orca::PresetItem* model = find_item(closed.printers, "Generic Klipper Printer");
    REQUIRE(model != nullptr);
    CHECK(model->group == orca::PresetGroup::system);
    CHECK(model->selected);
    CHECK(fs::exists(config_path));
    CHECK(read_file(config_path).find("MyKlipper 0.4 nozzle") != std::string::npos);
}

TEST_CASE("The Setup Wizard offers every bundled printer model and the filaments for the chosen ones", "[Adapter][Setup]")
{
    require_engine();

    const orca::SetupPrinters printers = orca::describe_setup_printers();

    INFO(printers.message);
    REQUIRE(printers.status == orca::SceneStatus::success);
    CHECK(printers.models.size() > 300);
    const auto k2_plus = std::find_if(printers.models.begin(), printers.models.end(), [](const orca::SetupPrinterModel& model) {
        return model.model == "Creality K2 Plus";
    });
    REQUIRE(k2_plus != printers.models.end());
    CHECK(k2_plus->vendor == "Creality");
    CHECK(k2_plus->nozzle_diameters == std::vector<std::string>{"0.2", "0.4", "0.6", "0.8"});
    // The wizard installs a model with all its nozzle diameters.
    CHECK(k2_plus->installed_nozzles == k2_plus->nozzle_diameters);
    CHECK(std::count(k2_plus->default_materials.begin(), k2_plus->default_materials.end(), "Creality Generic PLA @K2-all") == 1);
    CHECK(fs::exists(k2_plus->cover));
    const auto klipper = std::find_if(printers.models.begin(), printers.models.end(), [](const orca::SetupPrinterModel& model) {
        return model.model == "Generic Klipper Printer";
    });
    REQUIRE(klipper != printers.models.end());
    CHECK(klipper->vendor == "Custom");

    const orca::SetupFilaments filaments = orca::describe_setup_filaments({"Creality K2 Plus", "Generic Klipper Printer"});

    INFO(filaments.message);
    REQUIRE(filaments.status == orca::SceneStatus::success);
    const auto find_filament = [&filaments](const std::string& name) -> const orca::SetupFilament* {
        const auto filament = std::find_if(filaments.filaments.begin(), filaments.filaments.end(), [&name](const orca::SetupFilament& candidate) {
            return candidate.name == name;
        });
        return filament == filaments.filaments.end() ? nullptr : &*filament;
    };
    // Installed, for the K2 Plus.
    const orca::SetupFilament* installed = find_filament("Generic PLA @K2 Plus-all");
    REQUIRE(installed != nullptr);
    CHECK(installed->models == std::vector<std::int32_t>{0});
    CHECK(installed->selected);
    CHECK(installed->type == "PLA");
    // A default material of the K2 Plus.
    const orca::SetupFilament* default_material = find_filament("Creality Generic PLA @K2-all");
    REQUIRE(default_material != nullptr);
    CHECK(default_material->selected);
    CHECK(default_material->models == std::vector<std::int32_t>{0});
    CHECK(default_material->type == "PLA");
    // The filament library's presets are offered for every printer.
    const orca::SetupFilament* library = find_filament("Generic PLA @System");
    REQUIRE(library != nullptr);
    CHECK(library->models.empty());
    CHECK(library->selected);
    CHECK(library->vendor == "Generic");
    // Not for the chosen printers.
    CHECK(find_filament("Generic PLA @K1C-all") == nullptr);

    CHECK(orca::describe_setup_filaments({"No Such Printer"}).status == orca::SceneStatus::profile_not_found);
}

TEST_CASE("Finishing the Setup Wizard installs the chosen printers and selects the one it adds", "[Adapter][Setup]")
{
    require_engine();

    const orca::PresetState added = orca::apply_setup({"Creality K2 Plus", "Creality K1C"}, {"Generic PLA @K2 Plus-all", "Generic PLA @K1C-all"});

    INFO(added.message);
    REQUIRE(added.status == orca::SceneStatus::success);
    CHECK_FALSE(added.setup_required);
    // The K2 Plus was installed before, so the K1C is selected, with its 0.4 mm nozzle.
    CHECK(added.selection.printer == "Creality K1C 0.4 nozzle");
    CHECK(added.selection.filament == "Generic PLA @K1C-all");
    REQUIRE(find_item(added.printers, "Creality K2 Plus") != nullptr);
    REQUIRE(find_item(added.printers, "Creality K1C") != nullptr);
    CHECK(find_item(added.printers, "Creality K1C")->selected);
    CHECK(added.nozzle_diameters == std::vector<std::string>{"0.4", "0.6", "0.8"});

    // A cube near the far corner of the K2 Plus's 350 mm plate is off the K1C's 220 mm plate.
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("k1c.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    std::vector<double> corner = matrix_of(cube);
    corner[12] = 300.0;
    corner[13] = 300.0;
    const orca::PlateInspection on_k1c = orca::place_objects(plate_of({}, corner), {}, added.selection, orca::PlateManipulation::update_print_volume_state, {});
    INFO(on_k1c.message);
    REQUIRE(on_k1c.status == orca::SceneStatus::success);
    REQUIRE(on_k1c.objects.size() == 1);
    CHECK(on_k1c.objects[0].instances.front().volume_state == orca::VolumeState::outside);
    CHECK(on_k1c.objects[0].instances.front().instance_matrix[12] == Catch::Approx(300.0));
    CHECK(orca::place_objects(plate_of({}, corner), {}, k2_plus_profiles(), orca::PlateManipulation::update_print_volume_state, {}).objects[0].instances.front().volume_state
          == orca::VolumeState::inside);

    const orca::PresetState removed = orca::apply_setup({"Creality K2 Plus"}, {"Generic PLA @K2 Plus-all"});

    INFO(removed.message);
    REQUIRE(removed.status == orca::SceneStatus::success);
    CHECK(find_item(removed.printers, "Creality K1C") == nullptr);
    CHECK(removed.selection.printer.rfind("Creality K2 Plus ", 0) == 0);
    CHECK(orca::apply_setup({"No Such Printer"}, {"Generic PLA @K2 Plus-all"}).status == orca::SceneStatus::profile_not_found);
}

TEST_CASE("The sidebar selects presets as the desktop app does", "[Adapter][Presets]")
{
    require_engine();

    const orca::PresetState printer = orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle");

    INFO(printer.message);
    REQUIRE(printer.status == orca::SceneStatus::success);
    CHECK(printer.selection.printer == "Creality K2 Plus 0.4 nozzle");
    // System printers are listed once per model.
    const orca::PresetItem* model = find_item(printer.printers, "Creality K2 Plus");
    REQUIRE(model != nullptr);
    CHECK(model->label == "Creality K2 Plus");
    CHECK(model->group == orca::PresetGroup::system);
    CHECK(model->selected);
    CHECK(find_item(printer.printers, "Creality K2 Plus 0.4 nozzle") == nullptr);
    CHECK(printer.nozzle_diameters == std::vector<std::string>{"0.2", "0.4", "0.6", "0.8"});
    CHECK(printer.nozzle_diameter == "0.4");
    // Only processes for the selected printer.
    CHECK(find_item(printer.processes, "0.20mm Strength @Creality K2 Plus 0.4 nozzle") != nullptr);
    CHECK(find_item(printer.processes, "0.30mm Standard @Creality K2 Plus 0.6 nozzle") == nullptr);
    const orca::PresetItem* filament = find_item(printer.filaments, printer.selection.filament);
    REQUIRE(filament != nullptr);
    CHECK(filament->selected);

    const orca::PresetState process = orca::select_preset(orca::PresetChoice::process, "0.20mm Strength @Creality K2 Plus 0.4 nozzle");

    REQUIRE(process.status == orca::SceneStatus::success);
    CHECK(process.selection.process == "0.20mm Strength @Creality K2 Plus 0.4 nozzle");
    CHECK(find_item(process.processes, "0.20mm Strength @Creality K2 Plus 0.4 nozzle")->selected);

    // Another nozzle selects the model's printer for it, and a process for that printer.
    const orca::PresetState larger = orca::select_preset(orca::PresetChoice::nozzle_diameter, "0.6");

    REQUIRE(larger.status == orca::SceneStatus::success);
    CHECK(larger.selection.printer == "Creality K2 Plus 0.6 nozzle");
    CHECK(larger.nozzle_diameter == "0.6");
    CHECK(larger.selection.process.find("@Creality K2 Plus 0.6 nozzle") != std::string::npos);

    // Back on the 0.4 mm nozzle, the printer's process is remembered.
    const orca::PresetState back = orca::select_preset(orca::PresetChoice::nozzle_diameter, "0.4");

    REQUIRE(back.status == orca::SceneStatus::success);
    CHECK(back.selection.printer == "Creality K2 Plus 0.4 nozzle");
    CHECK(back.selection.process == "0.20mm Strength @Creality K2 Plus 0.4 nozzle");

    // The model keeps the selected nozzle.
    CHECK(orca::select_preset(orca::PresetChoice::printer_model, "Creality K2 Plus").selection.printer == "Creality K2 Plus 0.4 nozzle");

    const orca::PresetState pla = orca::select_preset(orca::PresetChoice::filament, "Generic PLA @K2 Plus-all");

    REQUIRE(pla.status == orca::SceneStatus::success);
    CHECK(pla.selection.filament == "Generic PLA @K2 Plus-all");
    // Remembered in the app configuration, as describe_presets() reports it.
    CHECK(read_file(data_dir + "/OrcaSlicer.conf").find("0.20mm Strength @Creality K2 Plus 0.4 nozzle") != std::string::npos);
    CHECK(orca::describe_presets().selection.process == "0.20mm Strength @Creality K2 Plus 0.4 nozzle");

    CHECK(orca::select_preset(orca::PresetChoice::printer, "No Such Printer").status == orca::SceneStatus::profile_not_found);
    CHECK(orca::select_preset(orca::PresetChoice::filament, "No Such Filament").status == orca::SceneStatus::profile_not_found);
}

TEST_CASE("The process tab edits the print preset as the desktop app does", "[Adapter][Settings]")
{
    require_engine();
    const std::string standard = "0.20mm Standard @Creality K2 Plus 0.4 nozzle";
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::process, standard).status == orca::SceneStatus::success);
    const auto print = orca::PresetKind::print;
    // The requests carry the page the app shows, whose fields the tab toggles.
    const std::string quality = "Quality";
    const std::string strength = "Strength";
    const std::string speed = "Speed";
    const std::string others = "Others";

    const auto setting = [](const orca::PresetSettings& settings, const std::string& id) -> const orca::SettingState& {
        const auto state = std::find_if(settings.settings.begin(), settings.settings.end(), [&id](const orca::SettingState& candidate) { return candidate.id == id; });
        INFO(id);
        REQUIRE(state != settings.settings.end());
        return *state;
    };
    const auto has_notice = [](const orca::PresetSettings& settings, const std::string& id) {
        return std::any_of(settings.notices.begin(), settings.notices.end(), [&id](const orca::SettingsDialog& dialog) { return dialog.id == id; });
    };

    SECTION("definitions come from PrintConfigDef")
    {
        const orca::SettingDefinitions definitions = orca::describe_setting_definitions(print);
        REQUIRE(definitions.status == orca::SceneStatus::success);
        const auto definition = [&definitions](const std::string& key) -> const orca::SettingDefinition& {
            const auto found = std::find_if(definitions.settings.begin(), definitions.settings.end(), [&key](const auto& candidate) { return candidate.key == key; });
            INFO(key);
            REQUIRE(found != definitions.settings.end());
            return *found;
        };
        CHECK(definition("layer_height").label == "Layer height");
        CHECK(definition("layer_height").sidetext == "mm");
        CHECK(definition("layer_height").type == 1);  // coFloat
        const orca::SettingDefinition& pattern = definition("sparse_infill_pattern");
        CHECK(pattern.type == 9);  // coEnum
        CHECK(std::find(pattern.enum_values.begin(), pattern.enum_values.end(), "gyroid") != pattern.enum_values.end());
        CHECK(definition("infill_anchor").gui_type == 2);  // f_enum_open
        CHECK(definition("support_filament").gui_type == 1);  // i_enum_open
        // The filament and printer tabs define their own settings.
        CHECK(orca::describe_setting_definitions(orca::PresetKind::filament).status == orca::SceneStatus::success);
        CHECK(orca::describe_setting_definitions(orca::PresetKind::printer).status == orca::SceneStatus::success);
    }

    SECTION("a system preset, its fields, and what the tab toggles")
    {
        const orca::PresetSettings settings = orca::describe_settings(print, others, {});
        INFO(settings.message);
        REQUIRE(settings.status == orca::SceneStatus::success);
        CHECK(settings.preset == standard);
        CHECK(settings.label == standard);
        CHECK_FALSE(settings.dirty);
        CHECK(settings.is_system);
        CHECK(settings.has_parent);
        CHECK_FALSE(settings.can_delete);
        CHECK(settings.save_name == standard);
        CHECK(settings.save_name_copy_suffix);
        const orca::PresetSettings quality_page = orca::describe_settings(print, quality, {});
        CHECK(setting(quality_page, "layer_height").value == "0.2");
        CHECK(setting(quality_page, "layer_height").system);
        CHECK_FALSE(setting(quality_page, "layer_height").modified);
        CHECK(setting(orca::describe_settings(print, strength, {}), "sparse_infill_density").value == "15");
        CHECK(setting(settings, "spiral_mode").value == "0");
        // Only Bambu Lab printers offer the timelapse type and a cone prime tower.
        CHECK_FALSE(setting(settings, "timelapse_type").visible);
        const orca::PresetSettings multimaterial = orca::describe_settings(print, "Multimaterial", {});
        CHECK(setting(multimaterial, "wipe_tower_wall_type").has_choices);
        CHECK(setting(multimaterial, "wipe_tower_wall_type").choice_values == std::vector<std::string>{"rectangle", "cone", "rib"});
        // Normal supports offer the normal styles.
        const orca::PresetSettings support = orca::describe_settings(print, "Support", {});
        CHECK(setting(support, "support_style").choice_values == std::vector<std::string>{"default", "grid", "snug"});
        // One filament: "Default" and the filament's type.
        CHECK(setting(support, "support_filament").choice_labels == std::vector<std::string>{"Default", "PLA"});
        // The pages are the ones TabPrint::build() lays out.
        REQUIRE_FALSE(settings.pages.empty());
        CHECK(settings.pages.front().title == "Quality");
        CHECK(settings.active_page == others);
    }

    SECTION("a changed field modifies the preset, and the undo button restores it")
    {
        const orca::PresetSettings changed = orca::change_setting(print, quality, "layer_height", "0.16", {});
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        CHECK(changed.dirty);
        CHECK(changed.label == "* " + standard);
        CHECK(setting(changed, "layer_height").value == "0.16");
        CHECK(setting(changed, "layer_height").modified);
        CHECK_FALSE(setting(changed, "layer_height").system);
        // The sidebar's process list marks the preset too.
        const orca::PresetState presets = orca::describe_presets();
        REQUIRE(find_item(presets.processes, standard) != nullptr);
        CHECK(find_item(presets.processes, standard)->label == "* " + standard);

        // No infill hides the infill pattern.
        const orca::PresetSettings no_infill = orca::change_setting(print, strength, "sparse_infill_density", "0%", {});
        CHECK(setting(no_infill, "sparse_infill_density").value == "0");
        CHECK_FALSE(setting(no_infill, "sparse_infill_pattern").visible);

        const orca::PresetSettings undone = orca::reset_settings(print, quality, {"layer_height"}, {});
        CHECK(setting(undone, "layer_height").value == "0.2");
        CHECK_FALSE(setting(undone, "layer_height").modified);
        CHECK(undone.dirty);

        const orca::PresetSettings reverted = orca::reset_settings(print, strength, {}, {});
        CHECK_FALSE(reverted.dirty);
        CHECK(reverted.label == standard);
        CHECK(setting(reverted, "sparse_infill_density").value == "15");
        CHECK(setting(reverted, "sparse_infill_pattern").visible);
    }

    SECTION("the fields correct what they are given and tell why")
    {
        // A layer height of 0 is below the printer's limit: set to it.
        const orca::PresetSettings zero = orca::change_setting(print, quality, "layer_height", "0", {});
        CHECK(has_notice(zero, "layer_height_too_small"));
        CHECK(setting(zero, "layer_height").value == "0.08");

        // Not a number.
        const orca::PresetSettings letters = orca::change_setting(print, speed, "outer_wall_speed", "fast", {});
        CHECK(has_notice(letters, "invalid_numeric"));

        // The same value changes nothing.
        const orca::PresetSettings same = orca::change_setting(print, strength, "wall_loops", setting(letters, "wall_loops").value, {});
        CHECK(same.notices.empty());

        CHECK_FALSE(orca::reset_settings(print, quality, {}, {}).dirty);
    }

    SECTION("a question abandons the change until it is answered")
    {
        const orca::PresetSettings asked = orca::change_setting(print, quality, "layer_height", "0.32", {});
        REQUIRE(asked.has_question);
        CHECK(asked.question.id == "layer_height_limits");
        CHECK(asked.question.yes.msgid == "Adjust");
        CHECK(asked.question.no.msgid == "Ignore");
        CHECK(setting(orca::describe_settings(print, quality, {}), "layer_height").value == "0.2");

        // Adjust: the printer's maximum.
        const orca::PresetSettings adjusted = orca::change_setting(print, quality, "layer_height", "0.32", {{"layer_height_limits", true}});
        CHECK_FALSE(adjusted.has_question);
        CHECK(setting(adjusted, "layer_height").value == "0.3");

        // Spiral vase mode asks to change what it needs.
        const orca::PresetSettings spiral = orca::change_setting(print, others, "spiral_mode", "1", {});
        REQUIRE(spiral.has_question);
        CHECK(spiral.question.id == "spiral_mode");
        const orca::PresetSettings vase = orca::change_setting(print, others, "spiral_mode", "1", {{"spiral_mode", true}});
        CHECK(setting(vase, "spiral_mode").value == "1");
        CHECK(setting(vase, "spiral_mode_smooth").visible);
        const orca::PresetSettings walls = orca::describe_settings(print, strength, {});
        CHECK(setting(walls, "wall_loops").value == "1");
        CHECK(setting(walls, "top_shell_layers").value == "0");
        CHECK(setting(walls, "sparse_infill_density").value == "0");

        const orca::PresetSettings reverted = orca::reset_settings(print, quality, {}, {});
        CHECK_FALSE(reverted.dirty);
        CHECK(setting(reverted, "spiral_mode").value == "0");

        // No: spiral mode stays off.
        const orca::PresetSettings declined = orca::change_setting(print, others, "spiral_mode", "1", {{"spiral_mode", false}});
        CHECK(setting(declined, "spiral_mode").value == "0");
        CHECK_FALSE(declined.dirty);
    }

    SECTION("the view mode is remembered")
    {
        CHECK(orca::set_settings_mode(print, orca::SettingsMode::expert).mode == orca::SettingsMode::expert);
        CHECK(orca::describe_settings(print, quality, {}).mode == orca::SettingsMode::expert);
        CHECK(read_file(data_dir + "/OrcaSlicer.conf").find("\"expert\"") != std::string::npos);
        CHECK(orca::set_settings_mode(print, orca::SettingsMode::simple).mode == orca::SettingsMode::simple);
    }

    SECTION("a tooltip shows the parent's value and the range")
    {
        std::vector<std::string> parts;
        for (const orca::UiText& text : orca::setting_tooltip(print, "wall_loops")) {
            parts.push_back(text.msgid);
        }
        CHECK(std::find(parts.begin(), parts.end(), "parameter name") != parts.end());
        CHECK(std::find(parts.begin(), parts.end(), ": wall_loops") != parts.end());
        CHECK(std::find(parts.begin(), parts.end(), "Default") != parts.end());
        CHECK(std::find(parts.begin(), parts.end(), "Range") != parts.end());
    }

    SECTION("an edited preset is saved as a user preset and deleted again")
    {
        CHECK(orca::check_preset_name(print, standard).check == orca::PresetNameCheck::invalid);
        CHECK(orca::check_preset_name(print, "bad/name").check == orca::PresetNameCheck::invalid);
        CHECK(orca::check_preset_name(print, " leading space").check == orca::PresetNameCheck::invalid);
        CHECK(orca::check_preset_name(print, "* marked").check == orca::PresetNameCheck::invalid);
        const orca::PresetNameValidation fresh = orca::check_preset_name(print, "Orcinus test process");
        CHECK(fresh.check == orca::PresetNameCheck::valid);
        CHECK(fresh.info.empty());

        REQUIRE(orca::change_setting(print, quality, "layer_height", "0.16", {}).dirty);
        const orca::PresetSettings saved = orca::save_preset(print, "Orcinus test process");
        INFO(saved.message);
        REQUIRE(saved.status == orca::SceneStatus::success);
        CHECK(saved.preset == "Orcinus test process");
        CHECK_FALSE(saved.dirty);
        CHECK_FALSE(saved.is_system);
        CHECK(saved.has_parent);
        CHECK(saved.can_delete);
        CHECK(saved.save_name == "Orcinus test process");
        CHECK_FALSE(saved.save_name_copy_suffix);
        CHECK(setting(saved, "layer_height").value == "0.16");
        CHECK_FALSE(setting(saved, "layer_height").modified);
        CHECK_FALSE(setting(saved, "layer_height").system);
        CHECK(setting(saved, "wall_loops").system);

        bool file_written = false;
        for (fs::recursive_directory_iterator it(data_dir), end; it != end; ++it) {
            file_written = file_written || it->path().filename() == "Orcinus test process.json";
        }
        CHECK(file_written);
        const orca::PresetState presets = orca::describe_presets();
        const orca::PresetItem* item = find_item(presets.processes, "Orcinus test process");
        REQUIRE(item != nullptr);
        CHECK(item->group == orca::PresetGroup::user);
        CHECK(item->selected);
        CHECK(presets.selection.process == "Orcinus test process");

        // Saving under the selected name overwrites it silently; another preset's name warns.
        CHECK(orca::check_preset_name(print, "Orcinus test process").check == orca::PresetNameCheck::valid);
        REQUIRE(orca::select_preset(orca::PresetChoice::process, standard).status == orca::SceneStatus::success);
        const orca::PresetNameValidation existing = orca::check_preset_name(print, "Orcinus test process");
        CHECK(existing.check == orca::PresetNameCheck::warning);
        REQUIRE_FALSE(existing.info.empty());
        CHECK(existing.info.front().msgid == "Preset \"%1%\" already exists.");
        REQUIRE(orca::select_preset(orca::PresetChoice::process, "Orcinus test process").status == orca::SceneStatus::success);

        const orca::PresetSettings asked = orca::delete_preset(print, {});
        REQUIRE(asked.has_question);
        CHECK(asked.question.id == "delete_preset");
        CHECK(orca::describe_settings(print, quality, {}).preset == "Orcinus test process");

        const orca::PresetSettings deleted = orca::delete_preset(print, {{"delete_preset", true}});
        INFO(deleted.message);
        REQUIRE(deleted.status == orca::SceneStatus::success);
        // The parent preset is selected.
        CHECK(deleted.preset == standard);
        CHECK(find_item(orca::describe_presets().processes, "Orcinus test process") == nullptr);
        bool file_left = false;
        for (fs::recursive_directory_iterator it(data_dir), end; it != end; ++it) {
            file_left = file_left || it->path().filename() == "Orcinus test process.json";
        }
        CHECK_FALSE(file_left);
    }

    // The slicing tests use the saved preset.
    CHECK_FALSE(orca::reset_settings(print, quality, {}, {}).dirty);
}

TEST_CASE("The filament tab edits the filament preset and its overrides", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    const auto filament = orca::PresetKind::filament;
    const std::string selected = orca::describe_presets().selection.filament;

    const auto setting = [](const orca::PresetSettings& settings, const std::string& id) -> const orca::SettingState& {
        const auto state = std::find_if(settings.settings.begin(), settings.settings.end(), [&id](const orca::SettingState& candidate) { return candidate.id == id; });
        INFO(id);
        REQUIRE(state != settings.settings.end());
        return *state;
    };
    const auto find_line = [](const orca::PresetSettings& settings, const std::string& id) -> const orca::SettingsLine* {
        for (const orca::SettingsPage& page : settings.pages) {
            for (const orca::SettingsGroup& group : page.groups) {
                for (const orca::SettingsLine& line : group.lines) {
                    for (const orca::SettingsLineOption& option : line.options) {
                        if (option.id == id) {
                            return &line;
                        }
                    }
                }
            }
        }
        return nullptr;
    };

    SECTION("the ramming dialog's OK writes the parameters it built into the filament")
    {
        const std::string page = "Multimaterial";
        const orca::PresetSettings before = orca::describe_settings(filament, page, {});
        REQUIRE(before.status == orca::SceneStatus::success);
        // The line shows the "Set ..." button of RammingDialog in place of its field.
        const orca::SettingsLine* line = find_line(before, "filament_ramming_parameters");
        REQUIRE(line != nullptr);
        CHECK(line->widget == orca::SettingWidget::ramming);
        CHECK(setting(before, "filament_ramming_parameters").value.find('|') != std::string::npos);

        const std::string written = "150 80 6.3871 6.77419 9.96774 14.0323| 0.05 6.6 0.45 6.8 0.95 15.5419 1.45 8.3";
        const orca::PresetSettings changed = orca::set_ramming_parameters(filament, page, written, {});
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        CHECK(setting(changed, "filament_ramming_parameters").value == written);
        CHECK(setting(changed, "filament_ramming_parameters").modified);
        CHECK(changed.dirty);
        CHECK_FALSE(orca::reset_settings(filament, page, {}, {}).dirty);
    }

    SECTION("the pages are the ones TabFilament::build() lays out")
    {
        const orca::PresetSettings settings = orca::describe_settings(filament, "Filament", {});
        INFO(settings.message);
        REQUIRE(settings.status == orca::SceneStatus::success);
        CHECK(settings.kind == filament);
        CHECK(settings.preset == selected);
        CHECK(settings.active_page == "Filament");
        std::vector<std::string> titles;
        for (const orca::SettingsPage& page : settings.pages) {
            titles.push_back(page.title);
        }
        CHECK(titles.front() == "Filament");
        CHECK(std::find(titles.begin(), titles.end(), "Setting Overrides") != titles.end());
        CHECK(std::find(titles.begin(), titles.end(), "Advanced") != titles.end());
        CHECK(std::find(titles.begin(), titles.end(), "Dependencies") != titles.end());
        CHECK(setting(settings, "filament_type").value == "PLA");
        const std::string colour = setting(settings, "default_filament_colour").value;
        CHECK((colour.empty() || colour.front() == '#'));
        // One value of a vector setting is an option of its own.
        CHECK(setting(settings, "nozzle_temperature#0").value == std::to_string(std::atoi(setting(settings, "nozzle_temperature#0").value.c_str())));
    }

    SECTION("an override is switched on, changed, and switched off again")
    {
        const std::string overrides = "Setting Overrides";
        const orca::PresetSettings described = orca::describe_settings(filament, overrides, {});
        REQUIRE(described.status == orca::SceneStatus::success);
        const std::string id = "filament_wipe_distance#0";
        const orca::SettingsLine* line = find_line(described, id);
        REQUIRE(line != nullptr);
        // Tab::create_near_label_widget(): the check box before the label.
        CHECK(line->has_override);
        CHECK(setting(described, id).nullable);

        // The check box leaves the value to the printer, or takes it over.
        const bool was_nil = setting(described, id).is_nil;
        const orca::PresetSettings toggled = orca::set_setting_override(filament, overrides, id, was_nil, {});
        INFO(toggled.message);
        REQUIRE(toggled.status == orca::SceneStatus::success);
        CHECK(setting(toggled, id).is_nil != was_nil);
        CHECK(toggled.dirty);

        if (was_nil) {
            const orca::PresetSettings changed = orca::change_setting(filament, overrides, id, "1.5", {});
            CHECK(setting(changed, id).value == "1.5");
        }

        const orca::PresetSettings back = orca::set_setting_override(filament, overrides, id, !was_nil, {});
        CHECK(setting(back, id).is_nil == was_nil);
        CHECK_FALSE(orca::reset_settings(filament, overrides, {}, {}).dirty);
    }

    SECTION("the presets the filament is compatible with")
    {
        const std::string dependencies = "Dependencies";
        const orca::PresetSettings described = orca::describe_settings(filament, dependencies, {});
        const orca::SettingsLine* line = find_line(described, "compatible_printers");
        REQUIRE(line != nullptr);
        CHECK(line->widget == orca::SettingWidget::compatible_printers);

        const orca::PresetNames choices = orca::compatible_preset_choices(filament, "compatible_printers");
        REQUIRE(choices.status == orca::SceneStatus::success);
        REQUIRE_FALSE(choices.names.empty());
        CHECK(std::find(choices.names.begin(), choices.names.end(), "Creality K2 Plus 0.4 nozzle") != choices.names.end());

        const orca::PresetSettings set = orca::set_compatible_presets(filament, dependencies, "compatible_printers", {"Creality K2 Plus 0.4 nozzle"}, {});
        INFO(set.message);
        REQUIRE(set.status == orca::SceneStatus::success);
        CHECK(setting(set, "compatible_printers").list_values == std::vector<std::string>{"Creality K2 Plus 0.4 nozzle"});
        CHECK(set.dirty);
        // An empty list is every printer, as the dialog's "All" leaves it.
        const orca::PresetSettings all = orca::set_compatible_presets(filament, dependencies, "compatible_printers", {}, {});
        CHECK(setting(all, "compatible_printers").list_values.empty());
        CHECK_FALSE(orca::reset_settings(filament, dependencies, {}, {}).dirty);
    }
}

TEST_CASE("The printer tab edits the machine preset and its extruder pages", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    const auto printer = orca::PresetKind::printer;
    const std::string basic = "Basic information";

    const auto setting = [](const orca::PresetSettings& settings, const std::string& id) -> const orca::SettingState& {
        const auto state = std::find_if(settings.settings.begin(), settings.settings.end(), [&id](const orca::SettingState& candidate) { return candidate.id == id; });
        INFO(id);
        REQUIRE(state != settings.settings.end());
        return *state;
    };

    SECTION("the pages are the ones TabPrinter::build_fff() lays out, with the extruder pages")
    {
        const orca::PresetSettings settings = orca::describe_settings(printer, basic, {});
        INFO(settings.message);
        REQUIRE(settings.status == orca::SceneStatus::success);
        CHECK(settings.preset == "Creality K2 Plus 0.4 nozzle");
        std::vector<std::string> titles;
        for (const orca::SettingsPage& page : settings.pages) {
            titles.push_back(page.title);
        }
        CHECK(titles.front() == basic);
        CHECK(std::find(titles.begin(), titles.end(), "Machine G-code") != titles.end());
        // One extruder: the page of the desktop app is named after it alone.
        CHECK(std::find(titles.begin(), titles.end(), "Extruder") != titles.end());
        // The bed is a line with a widget of its own.
        CHECK(setting(settings, "printable_area").value.find('x') != std::string::npos);
        CHECK(setting(settings, "printable_height").value == "350");
    }

    SECTION("the thumbnails are written back as the desktop app lists them, and name their format")
    {
        const auto value = [&setting](const orca::PresetSettings& settings, const std::string& id) { return setting(settings, id).value; };
        // validate_thumbnails_string(): each size with its format.
        const orca::PresetSettings listed = orca::change_setting(printer, basic, "thumbnails", "300x300, 96x96/JPG", {});
        INFO(listed.message);
        REQUIRE(listed.status == orca::SceneStatus::success);
        CHECK(value(listed, "thumbnails") == "300x300/PNG, 96x96/JPG");

        // The format of the first thumbnail becomes thumbnails_format, which
        // the G-code the edited preset slices lists in its configuration.
        const orca::PresetSettings jpeg = orca::change_setting(printer, basic, "thumbnails", "300x300/JPG, 96x96/PNG", {});
        REQUIRE(jpeg.status == orca::SceneStatus::success);
        CHECK(jpeg.dirty);
        const std::string output = output_path("thumbnails-format.gcode");
        // The selection the tab edits, which slicing takes with its changes.
        const orca::ProfileSelection selection = orca::describe_presets().selection;
        REQUIRE(orca::slice("thumbnails-format", plate_of({}), output, {}, selection, {}, {}).status == orca::SliceStatus::success);
        CHECK(read_file(output).find("; thumbnails_format = JPG") != std::string::npos);

        // A size out of range is reported, and the valid rest is kept.
        const orca::PresetSettings invalid = orca::change_setting(printer, basic, "thumbnails", "300x300/PNG, 5000x5000/PNG", {});
        CHECK(std::any_of(invalid.notices.begin(), invalid.notices.end(), [](const orca::SettingsDialog& dialog) { return dialog.id == "thumbnails"; }));
        CHECK_FALSE(orca::reset_settings(printer, basic, {}, {}).dirty);
    }

    SECTION("a custom G-code is checked as the desktop app checks it")
    {
        const std::string gcode_page = "Machine G-code";
        const orca::PresetSettings changed = orca::change_setting(printer, gcode_page, "machine_start_gcode", "G28 ; home\n", {});
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        CHECK(setting(changed, "machine_start_gcode").value == "G28 ; home\n");
        CHECK(changed.dirty);

        // Tab::validate_custom_gcode(): a reserved keyword is reported.
        // A keyword both the Bambu Lab and the compatible tag lists reserve.
        const orca::PresetSettings reserved = orca::change_setting(printer, gcode_page, "machine_start_gcode", ";_GP_LAST_LINE_M73_PLACEHOLDER\nG28\n", {});
        CHECK(std::any_of(reserved.notices.begin(), reserved.notices.end(), [](const orca::SettingsDialog& dialog) {
            return dialog.id == "reserved_keywords";
        }));
        CHECK_FALSE(orca::reset_settings(printer, gcode_page, {}, {}).dirty);
    }
}

TEST_CASE("Selecting another preset asks what happens to the unsaved changes", "[Adapter][Settings]")
{
    require_engine();
    const std::string standard = "0.20mm Standard @Creality K2 Plus 0.4 nozzle";
    const std::string strength = "0.20mm Strength @Creality K2 Plus 0.4 nozzle";
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::process, standard).status == orca::SceneStatus::success);
    const auto print = orca::PresetKind::print;
    const std::string quality = "Quality";

    const auto value_of = [](const orca::PresetSettings& settings, const std::string& id) {
        const auto state = std::find_if(settings.settings.begin(), settings.settings.end(), [&id](const orca::SettingState& candidate) { return candidate.id == id; });
        REQUIRE(state != settings.settings.end());
        return state->value;
    };

    REQUIRE(orca::change_setting(print, quality, "layer_height", "0.16", {}).dirty);

    // Tab::may_discard_current_dirty_preset(): nothing is selected yet.
    const orca::PresetState asked = orca::select_preset(orca::PresetChoice::process, strength);
    REQUIRE(asked.status == orca::SceneStatus::success);
    CHECK(asked.asks_unsaved_changes);
    CHECK(asked.changed_kind == print);
    CHECK(asked.can_transfer);
    CHECK(asked.save_name == standard);
    CHECK(asked.save_name_copy_suffix);
    CHECK(asked.selection.process == standard);
    REQUIRE_FALSE(asked.unsaved_changes.empty());
    const orca::PresetChange& change = asked.unsaved_changes.front();
    CHECK(change.id == "layer_height");
    CHECK(change.category.front().msgid == "Quality");
    CHECK(change.label.front().msgid == "Layer height");
    CHECK(change.old_value.front().args.front() == "0.2");
    CHECK(change.new_value.front().args.front() == "0.16");

    SECTION("discarding leaves the changes behind")
    {
        const orca::PresetState selected = orca::select_preset(orca::PresetChoice::process, strength, orca::PresetChangeAction::discard);
        REQUIRE(selected.status == orca::SceneStatus::success);
        CHECK_FALSE(selected.asks_unsaved_changes);
        CHECK(selected.selection.process == strength);
        CHECK(value_of(orca::describe_settings(print, quality, {}), "layer_height") == "0.2");
    }

    SECTION("transferring moves them to the preset that is selected")
    {
        const orca::PresetState selected = orca::select_preset(orca::PresetChoice::process, strength, orca::PresetChangeAction::transfer);
        REQUIRE(selected.status == orca::SceneStatus::success);
        CHECK(selected.selection.process == strength);
        const orca::PresetSettings settings = orca::describe_settings(print, quality, {});
        CHECK(settings.dirty);
        CHECK(value_of(settings, "layer_height") == "0.16");
    }

    // The tests that follow slice with the unchanged preset.
    REQUIRE(orca::select_preset(orca::PresetChoice::process, standard, orca::PresetChangeAction::discard).status == orca::SceneStatus::success);
    CHECK_FALSE(orca::describe_settings(print, quality, {}).dirty);
}

TEST_CASE("The settings of an object and of the plate override the process preset", "[Adapter][Settings]")
{
    require_engine();
    const std::string standard = "0.20mm Standard @Creality K2 Plus 0.4 nozzle";
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::process, standard).status == orca::SceneStatus::success);
    const auto object = orca::PresetKind::object;
    const auto plate = orca::PresetKind::plate;
    const std::string frequent = "Frequent";
    const std::string plate_page = "Plate Settings";

    const auto setting = [](const orca::PresetSettings& settings, const std::string& id) -> const orca::SettingState& {
        const auto state = std::find_if(settings.settings.begin(), settings.settings.end(), [&id](const orca::SettingState& candidate) { return candidate.id == id; });
        INFO(id);
        REQUIRE(state != settings.settings.end());
        return *state;
    };
    // What an object or the plate overrides, as the app keeps it.
    const auto overridden = [](const orca::ModelSettings& settings, const std::string& key) {
        const auto found = std::find(settings.keys.begin(), settings.keys.end(), key);
        return found == settings.keys.end() ? std::string("<unset>") : settings.values[std::size_t(found - settings.keys.begin())];
    };
    // The overrides of one selected object, as the app sends them.
    const auto request_of = [](const std::vector<std::pair<std::string, std::string>>& overrides) {
        orca::ModelSettingsRequest request;
        orca::ModelSettings settings;
        for (const auto& [key, value] : overrides) {
            settings.keys.push_back(key);
            settings.values.push_back(value);
        }
        request.settings.push_back(settings);
        return request;
    };
    const auto first_of = [](const orca::PresetSettings& settings) {
        REQUIRE_FALSE(settings.model_settings.empty());
        return settings.model_settings.front();
    };

    SECTION("the object tab shows the process preset with the overrides of the object")
    {
        // The app asks for the tab of the object the plate has selected, which
        // overrides nothing yet.
        const orca::PresetSettings settings = orca::describe_settings(object, frequent, {}, request_of({}));
        INFO(settings.message);
        REQUIRE(settings.status == orca::SceneStatus::success);
        CHECK(settings.kind == object);
        // TabPrintModel::build(): the Frequent page comes first, and only the
        // settings an object may override are left on the pages.
        REQUIRE_FALSE(settings.pages.empty());
        CHECK(settings.pages.front().title == frequent);
        REQUIRE_FALSE(settings.pages.front().groups.empty());
        CHECK(settings.pages.front().groups.front().lines.size() == 4);
        CHECK(setting(settings, "layer_height").value == "0.2");
        CHECK_FALSE(setting(settings, "layer_height").modified);
        CHECK(settings.has_model_settings);
        CHECK(first_of(settings).keys.empty());
        // A setting of the printer is on no page of an object.
        const auto machine = std::find_if(settings.settings.begin(), settings.settings.end(),
                                          [](const orca::SettingState& state) { return state.key == "spiral_mode"; });
        CHECK(machine == settings.settings.end());
    }

    SECTION("a change becomes an override of the object, and the undo removes it")
    {
        const orca::PresetSettings changed = orca::change_setting(object, frequent, "layer_height", "0.28", {}, request_of({}));
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        CHECK(setting(changed, "layer_height").value == "0.28");
        CHECK(setting(changed, "layer_height").modified);
        CHECK(overridden(first_of(changed), "layer_height") == "0.28");
        // The process preset itself is untouched.
        CHECK(orca::describe_settings(orca::PresetKind::print, "Quality", {}).dirty == false);

        // The tab is described again with what the app keeps.
        const orca::ModelSettingsRequest kept = request_of({{"layer_height", "0.28"}});
        const orca::PresetSettings shown = orca::describe_settings(object, frequent, {}, kept);
        CHECK(setting(shown, "layer_height").value == "0.28");
        CHECK(setting(shown, "layer_height").modified);

        // The undo of a line removes the override, and the preset's value applies again.
        const orca::PresetSettings undone = orca::reset_settings(object, frequent, {"layer_height"}, {}, kept);
        INFO(undone.message);
        REQUIRE(undone.status == orca::SceneStatus::success);
        CHECK(setting(undone, "layer_height").value == "0.2");
        CHECK(first_of(undone).keys.empty());

        // Reset Options removes every override of the object.
        const orca::ModelSettingsRequest several = request_of({{"layer_height", "0.28"}, {"wall_loops", "5"}});
        const orca::PresetSettings reset = orca::reset_settings(object, frequent, {}, {}, several);
        REQUIRE(reset.status == orca::SceneStatus::success);
        CHECK(first_of(reset).keys.empty());
        CHECK(setting(reset, "wall_loops").value == "2");
    }

    SECTION("several selected objects show what they agree on, and a change reaches all of them")
    {
        // TabPrintModel::update_model_config(): a value they disagree on is a
        // null key, which the tab shows no value for.
        orca::ModelSettingsRequest both;
        orca::ModelSettings first;
        first.keys = {"layer_height", "wall_loops"};
        first.values = {"0.28", "5"};
        orca::ModelSettings second;
        // The second object overrides the walls with the same value, and the
        // layer height with another one.
        second.keys = {"layer_height", "wall_loops"};
        second.values = {"0.24", "5"};
        both.settings = {first, second};

        const orca::PresetSettings shown = orca::describe_settings(object, frequent, {}, both);
        INFO(shown.message);
        REQUIRE(shown.status == orca::SceneStatus::success);
        CHECK(setting(shown, "layer_height").mixed);
        CHECK(setting(shown, "layer_height").value.empty());
        // Both hold the same value for the walls, which the tab shows.
        CHECK_FALSE(setting(shown, "wall_loops").mixed);
        CHECK(setting(shown, "wall_loops").value == "5");

        const orca::PresetSettings changed = orca::change_setting(object, frequent, "layer_height", "0.3", {}, both);
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        REQUIRE(changed.model_settings.size() == 2);
        CHECK(overridden(changed.model_settings[0], "layer_height") == "0.3");
        CHECK(overridden(changed.model_settings[1], "layer_height") == "0.3");
        // The walls they agreed on stay as they were.
        CHECK(overridden(changed.model_settings[0], "wall_loops") == "5");
        CHECK(overridden(changed.model_settings[1], "wall_loops") == "5");
    }

    SECTION("the plate tab holds the settings of the plate, starting from the project's bed type")
    {
        const orca::PresetSettings settings = orca::describe_settings(plate, plate_page, {});
        INFO(settings.message);
        REQUIRE(settings.status == orca::SceneStatus::success);
        CHECK(settings.kind == plate);
        REQUIRE(settings.pages.size() == 1);
        CHECK(settings.pages.front().title == plate_page);
        CHECK(setting(settings, "print_sequence").value == "by layer");
        CHECK(setting(settings, "spiral_mode").value == "0");
        CHECK(setting(settings, "first_layer_sequence_choice").value == "Auto");
        // The bed type of the project, which the plate follows until it is changed.
        CHECK_FALSE(setting(settings, "curr_bed_type").value.empty());
        CHECK(first_of(settings).keys.empty());

        const orca::PresetSettings changed = orca::change_setting(plate, plate_page, "print_sequence", "by object", {}, {});
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        CHECK(setting(changed, "print_sequence").value == "by object");
        CHECK(overridden(first_of(changed), "print_sequence") == "by object");
        // The settings of an object follow the plate's.
        orca::ModelSettingsRequest with_plate;
        with_plate.plate = first_of(changed);
        CHECK(orca::describe_settings(object, frequent, {}, with_plate).status == orca::SceneStatus::success);
    }

    SECTION("the plate is sliced with the settings of its objects")
    {
        std::vector<orca::PlateObject> objects = plate_of({});
        objects.front().settings.keys = {"layer_height"};
        objects.front().settings.values = {"0.3"};
        const orca::SliceResult result = orca::slice("object-settings", objects, output_path("object.gcode"), {}, k2_plus_profiles(), {},
                                                    [](int, const std::string&) {});
        INFO(result.message);
        REQUIRE(result.status == orca::SliceStatus::success);
        // The 20 mm cube at 0.3 mm layers over a 0.2 mm first layer, where the
        // preset alone gives 100 layers of 0.2 mm.
        CHECK(result.layer_count == 67);

        // The settings of the plate reach the print as well
        // (BackgroundSlicingProcess::apply).
        orca::ModelSettings plate_settings;
        plate_settings.keys = {"curr_bed_type"};
        plate_settings.values = {"Textured PEI Plate"};
        const std::string bed_output = output_path("plate-settings.gcode");
        const orca::SliceResult on_pei = orca::slice("plate-settings", plate_of({}), bed_output, {}, k2_plus_profiles(), plate_settings,
                                                    [](int, const std::string&) {});
        INFO(on_pei.message);
        REQUIRE(on_pei.status == orca::SliceStatus::success);
        CHECK(read_file(bed_output).find("; curr_bed_type = Textured PEI Plate") != std::string::npos);
    }
}

TEST_CASE("Bundled K2 Plus profiles slice the calibration cube into Orca G-code", "[Adapter]")
{
    require_engine();
    const std::string output = output_path("cube.gcode");
    const std::string toolpaths = output_path("cube.toolpaths");
    int last_percent = -1;

    const orca::SliceResult result = orca::slice(
        "cube",
        plate_of({}),
        output,
        toolpaths,
        k2_plus_profiles(),
        {},
        [&last_percent](const int percent, const std::string&) { last_percent = percent; }
    );

    INFO(result.message);
    REQUIRE(result.status == orca::SliceStatus::success);
    CHECK(result.layer_count == 100);
    CHECK(result.estimated_print_time_seconds > 0);
    CHECK(result.filament_micrometers > 0);
    CHECK(last_percent >= 80);
    CHECK_FALSE(fs::exists(output + ".part"));

    const std::string gcode = read_file(output);
    CHECK(gcode.find("; generated by OrcaSlicer ") != std::string::npos);
    CHECK(gcode.find("; printer_settings_id = Creality K2 Plus 0.4 nozzle") != std::string::npos);
    CHECK(gcode.find("; filament_settings_id = \"Generic PLA @K2 Plus-all\"") != std::string::npos);
    // z_hop_types comes only from the root fdm_machine_common profile.
    CHECK(gcode.find("; z_hop_types = Normal Lift") != std::string::npos);

    // The toolpaths for libvgcode: every layer extrudes, in the filament's colour.
    REQUIRE(result.toolpaths_written);
    CHECK_FALSE(fs::exists(toolpaths + ".part"));
    libvgcode::GCodeInputData data;
    orcinus::toolpaths::Statistics statistics;
    REQUIRE(orcinus::toolpaths::read_file(toolpaths, data, statistics));
    // Layer i of the 0.2 mm process extrudes at (i + 1) * 0.2 mm, within float rounding.
    std::set<std::uint32_t> extrusion_layers;
    float worst_height_error = 0.0f;
    for (const libvgcode::PathVertex& vertex : data.vertices) {
        if (vertex.type == libvgcode::EMoveType::Extrude) {
            extrusion_layers.insert(vertex.layer_id);
            const float expected = 0.2f * static_cast<float>(vertex.layer_id + 1);
            worst_height_error = std::max(worst_height_error, std::abs(vertex.position[2] - expected));
        }
    }
    CHECK(extrusion_layers.size() == 100);
    CHECK(*extrusion_layers.rbegin() == 99);
    CHECK(worst_height_error < 1e-3f);
    REQUIRE(data.tools_colors.size() == 1);
    CHECK(data.color_print_colors == data.tools_colors);
    CHECK_FALSE(data.spiral_vase_mode);

    // The legend's figures agree with the slice result: time, filament, and travel.
    CHECK(statistics.time[0] == Catch::Approx(static_cast<float>(result.estimated_print_time_seconds)).margin(1.0));
    CHECK(statistics.total_used_filament == Catch::Approx(result.filament_micrometers / 1000.0).margin(0.01));
    CHECK(statistics.model_filament[0] == Catch::Approx(statistics.total_used_filament / 1000.0).epsilon(0.05));
    CHECK(statistics.total_weight > 0.0);
    CHECK(statistics.total_travel_moves > 0);
    CHECK_FALSE(statistics.used_filament_per_role.empty());
    CHECK(statistics.move_counts[static_cast<std::size_t>(libvgcode::EMoveType::Extrude)] > 0);
}

TEST_CASE("The G-code holds the thumbnails the app rendered, in the printer's sizes", "[Adapter][Scene]")
{
    require_engine();
    const orca::ThumbnailSizes sizes = orca::thumbnail_sizes(k2_plus_profiles());
    INFO(sizes.message);
    REQUIRE(sizes.status == orca::SceneStatus::success);
    // The K2 Plus profile: "thumbnails": ["300x300", "96x96"], thumbnails_format PNG.
    CHECK(sizes.sizes == std::vector<std::int32_t>{300, 300, 96, 96});

    // A picture of every size, as the app reads it back from its framebuffer.
    std::vector<orca::ThumbnailImage> images;
    for (std::size_t i = 0; i + 1 < sizes.sizes.size(); i += 2) {
        orca::ThumbnailImage& image = images.emplace_back();
        image.width = sizes.sizes[i];
        image.height = sizes.sizes[i + 1];
        image.path = output_path("thumbnail-" + std::to_string(image.width) + ".rgba");
        std::vector<unsigned char> pixels(std::size_t(image.width) * std::size_t(image.height) * 4, 0);
        for (std::size_t pixel = 0; pixel < pixels.size(); pixel += 4) {
            pixels[pixel + 1] = 150;
            pixels[pixel + 2] = 136;
            pixels[pixel + 3] = 255;
        }
        std::ofstream(image.path, std::ios::binary).write(reinterpret_cast<const char*>(pixels.data()), std::streamsize(pixels.size()));
    }

    const std::string output = output_path("thumbnails.gcode");
    const orca::SliceResult result = orca::slice("thumbnails", plate_of({}), output, {}, k2_plus_profiles(), {}, {}, {}, images);
    INFO(result.message);
    REQUIRE(result.status == orca::SliceStatus::success);
    const std::string gcode = read_file(output);
    // GCodeThumbnails::export_thumbnails_to_file(): PNG, base64, in the header.
    CHECK(gcode.find("; THUMBNAIL_BLOCK_START") != std::string::npos);
    const std::size_t large = gcode.find("; thumbnail begin 300x300 ");
    const std::size_t small = gcode.find("; thumbnail begin 96x96 ");
    CHECK(large != std::string::npos);
    CHECK(small != std::string::npos);
    CHECK(large < small);
    CHECK(small < gcode.find("; EXECUTABLE_BLOCK_START"));

    // Without pictures the G-code holds none, as it would if the desktop app's
    // renderer made no valid thumbnail.
    const std::string bare = output_path("no-thumbnails.gcode");
    REQUIRE(orca::slice("no-thumbnails", plate_of({}), bare, {}, k2_plus_profiles(), {}, {}).status == orca::SliceStatus::success);
    CHECK(read_file(bare).find("; THUMBNAIL_BLOCK_START") == std::string::npos);
}

TEST_CASE("An imported STL is sliced through the same pipeline", "[Adapter]")
{
    require_engine();
    const std::string output = output_path("20mmbox.gcode");

    const orca::SliceResult result = orca::slice(
        "stl",
        plate_of(device_dir + "/data/test_stl/ASCII/20mmbox-LF.stl"),
        output,
        {},
        k2_plus_profiles(),
        {},
        {}
    );

    INFO(result.message);
    REQUIRE(result.status == orca::SliceStatus::success);
    CHECK(result.layer_count > 0);
    CHECK(fs::file_size(output) > 0);
}

TEST_CASE("Unknown profiles and unreadable models are reported", "[Adapter]")
{
    require_engine();

    orca::ProfileSelection unknown_printer = k2_plus_profiles();
    unknown_printer.printer = "No Such Printer";
    CHECK(orca::slice("unknown", plate_of({}), output_path("unknown.gcode"), {}, unknown_printer, {}, {}).status
          == orca::SliceStatus::profile_not_found);

    CHECK(orca::slice("missing", plate_of(device_dir + "/data/missing.stl"), output_path("missing.gcode"), {}, k2_plus_profiles(), {}, {}).status
          == orca::SliceStatus::model_read_failed);
}

TEST_CASE("Cancelling the active job stops slicing without output", "[Adapter]")
{
    require_engine();
    const std::string output = output_path("cancelled.gcode");
    bool other_job_rejected = false;
    bool cancel_accepted = false;

    // Catch2 assertions are not thread-safe; the callback only records results.
    const orca::SliceResult result = orca::slice(
        "cancel-me",
        plate_of({}),
        output,
        {},
        k2_plus_profiles(),
        {},
        [&](const int percent, const std::string&) {
            if (percent >= 10 && !cancel_accepted) {
                other_job_rejected = !orca::cancel("other-job");
                cancel_accepted = orca::cancel("cancel-me");
            }
        }
    );

    CHECK(other_job_rejected);
    CHECK(cancel_accepted);
    CHECK(result.status == orca::SliceStatus::cancelled);
    CHECK_FALSE(fs::exists(output));
    CHECK_FALSE(fs::exists(output + ".part"));
}

TEST_CASE("The K2 Plus plate is described as OrcaSlicer draws it", "[Adapter][Scene]")
{
    require_engine();
    const std::string directory = (fs::path(device_dir) / "tmp" / "plate").string();

    const orca::PlateDescription plate = orca::describe_plate(k2_plus_profiles(), directory);

    INFO(plate.message);
    REQUIRE(plate.status == orca::SceneStatus::success);
    CHECK(plate.printable_area == std::vector<double>{0, 0, 350, 0, 350, 350, 0, 350});
    CHECK(plate.printable_height == 350.0);
    // A rectangle is two triangles; the "0x0" exclude area is none.
    CHECK(plate.plate_triangles.size() == 12);
    CHECK(plate.exclude_triangles.empty());
    // Bed_2D::generate_grid(): a 10 mm grid over 350 mm walks each axis from the
    // origin both ways, so the line at 0 is bold twice; 36 lines per axis, every
    // fifth bold. The thin set also carries the 4 contour edges.
    CHECK(plate.bold_grid_lines.size() == 4 * (9 + 9));
    CHECK(plate.thin_grid_lines.size() == 4 * (28 + 28 + 4));
    CHECK(plate.filament_colour.size() == 7);
    CHECK(plate.filament_colour.front() == '#');

    REQUIRE_FALSE(plate.bed_model_mesh.empty());
    const std::string mesh = read_file(plate.bed_model_mesh);
    REQUIRE(mesh.size() >= 16);
    CHECK(mesh.compare(0, 4, "OMSH") == 0);
    CHECK(read_u32(mesh, 4) == orca::mesh_file_version);
    const std::uint32_t vertex_count = read_u32(mesh, 8);
    const std::uint32_t triangle_count = read_u32(mesh, 12);
    // creality_k2plus_buildplate_model.stl
    CHECK(triangle_count == 432);
    CHECK(mesh.size() == 16 + 12 * static_cast<std::size_t>(vertex_count) + 12 * static_cast<std::size_t>(triangle_count));

    REQUIRE_FALSE(plate.bed_texture.empty());
    CHECK(read_file(plate.bed_texture).compare(1, 3, "PNG") == 0);
}

TEST_CASE("A model is placed and meshed the way slicing places it", "[Adapter][Scene]")
{
    require_engine();
    const std::string mesh_path = output_path("cube.mesh");

    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), mesh_path, {});

    INFO(cube.message);
    REQUIRE(cube.status == orca::SceneStatus::success);
    CHECK(cube.facet_count == 12);
    CHECK(cube.size_x == Catch::Approx(20.0));
    CHECK(cube.size_y == Catch::Approx(20.0));
    CHECK(cube.size_z == Catch::Approx(20.0));
    // Centred on the 350 mm plate and resting on it.
    CHECK(cube.instance_matrix[12] == Catch::Approx(175.0));
    CHECK(cube.instance_matrix[13] == Catch::Approx(175.0));
    CHECK(cube.instance_matrix[14] == Catch::Approx(10.0));
    CHECK(cube.instance_matrix[15] == 1.0);

    const std::string mesh = read_file(mesh_path);
    CHECK(mesh.size() == 16 + 12 * 8 + 12 * 12);
    CHECK_FALSE(fs::exists(mesh_path + ".part"));

    orca::ProfileSelection unknown_printer = k2_plus_profiles();
    unknown_printer.printer = "No Such Printer";
    CHECK(orca::inspect_model({}, unknown_printer, output_path("unknown.mesh"), {}).status == orca::SceneStatus::profile_not_found);
    CHECK(orca::inspect_model(device_dir + "/data/missing.stl", k2_plus_profiles(), output_path("missing.mesh"), {}).status
          == orca::SceneStatus::model_read_failed);
}

TEST_CASE("A moved object is sliced where the user placed it", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("moved.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);

    // Drag the cube from the plate centre to (100, 120).
    std::vector<double> placement = matrix_of(cube);
    placement[12] = 100.0;
    placement[13] = 120.0;
    const std::string output = output_path("moved.gcode");

    const orca::SliceResult result = orca::slice("moved", plate_of({}, placement), output, {}, k2_plus_profiles(), {}, {});

    INFO(result.message);
    REQUIRE(result.status == orca::SliceStatus::success);
    CHECK(result.layer_count == 100);

    // The extrusions outline the cube at its new position.
    const Bounds bounds = extrusion_bounds(output);
    REQUIRE(bounds.min_x < bounds.max_x);
    CHECK((bounds.min_x + bounds.max_x) / 2.0 == Catch::Approx(100.0).margin(1.0));
    CHECK((bounds.min_y + bounds.max_y) / 2.0 == Catch::Approx(120.0).margin(1.0));
    CHECK(bounds.max_x - bounds.min_x == Catch::Approx(20.0).margin(1.0));
}

TEST_CASE("Every object on the plate is sliced where it stands", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("pair.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    std::vector<double> left = matrix_of(cube);
    std::vector<double> right = left;
    left[12] = 100.0;
    right[12] = 250.0;
    std::vector<orca::PlateObject> plate = plate_of({}, left);
    plate.push_back(plate_of({}, right).front());
    const std::string output = output_path("pair.gcode");

    SECTION("two cubes on the plate")
    {
        const orca::SliceResult result = orca::slice("pair", plate, output, {}, k2_plus_profiles(), {}, {});

        INFO(result.message);
        REQUIRE(result.status == orca::SliceStatus::success);
        CHECK(result.layer_count == 100);
        const Bounds bounds = extrusion_bounds(output);
        CHECK(bounds.min_x == Catch::Approx(90.0).margin(1.0));
        CHECK(bounds.max_x == Catch::Approx(260.0).margin(1.0));
    }
    SECTION("a cube entirely off the plate is not printed")
    {
        plate[1].instances.front().matrix[12] = -100.0;

        const orca::SliceResult result = orca::slice("pair-off", plate, output, {}, k2_plus_profiles(), {}, {});

        INFO(result.message);
        REQUIRE(result.status == orca::SliceStatus::success);
        const Bounds bounds = extrusion_bounds(output);
        CHECK(bounds.min_x == Catch::Approx(90.0).margin(1.0));
        CHECK(bounds.max_x == Catch::Approx(110.0).margin(1.0));
    }
    SECTION("a cube the object list marked as not printable is left out")
    {
        // ObjectList::toggle_printable_state(): ModelInstance::is_printable().
        plate[1].instances.front().printable = false;

        const orca::SliceResult result = orca::slice("pair-unprintable", plate, output, {}, k2_plus_profiles(), {}, {});

        INFO(result.message);
        REQUIRE(result.status == orca::SliceStatus::success);
        const Bounds bounds = extrusion_bounds(output);
        CHECK(bounds.min_x == Catch::Approx(90.0).margin(1.0));
        CHECK(bounds.max_x == Catch::Approx(110.0).margin(1.0));
    }
    SECTION("a plate whose objects are all unprintable has nothing to slice")
    {
        plate[0].instances.front().printable = false;
        plate[1].instances.front().printable = false;

        const orca::SliceResult result = orca::slice("pair-none", plate, output, {}, k2_plus_profiles(), {}, {});

        CHECK(result.status == orca::SliceStatus::invalid_print);
    }
}

TEST_CASE("Every copy of an object is placed and printed", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("copies.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    // Plater::increase_instances(): another copy, offset from the first.
    std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));
    orca::ObjectPlacement copy = plate.front().instances.front();
    copy.matrix[12] += 60.0;
    plate.front().instances.push_back(copy);
    const std::string output = output_path("copies.gcode");

    const orca::PlateInspection placed = orca::place_objects(plate, {}, k2_plus_profiles(), orca::PlateManipulation::update_print_volume_state, {});
    INFO(placed.message);
    REQUIRE(placed.status == orca::SceneStatus::success);
    REQUIRE(placed.objects.size() == 1);
    REQUIRE(placed.objects.front().instances.size() == 2);
    CHECK(placed.objects.front().instances[0].instance_matrix[12] == Catch::Approx(175.0).margin(1.0));
    CHECK(placed.objects.front().instances[1].instance_matrix[12] == Catch::Approx(235.0).margin(1.0));
    CHECK(placed.objects.front().instances[1].volume_state == orca::VolumeState::inside);

    const orca::SliceResult result = orca::slice("copies", plate, output, {}, k2_plus_profiles(), {}, {});
    INFO(result.message);
    REQUIRE(result.status == orca::SliceStatus::success);
    CHECK(result.layer_count == 100);
    // Both copies are printed, so the extrusions span them.
    const Bounds bounds = extrusion_bounds(output);
    CHECK(bounds.min_x == Catch::Approx(165.0).margin(1.0));
    CHECK(bounds.max_x == Catch::Approx(245.0).margin(1.0));

    SECTION("a copy the object list marked as not printable is left out")
    {
        plate.front().instances[1].printable = false;

        const orca::SliceResult one = orca::slice("copies-one", plate, output, {}, k2_plus_profiles(), {}, {});

        INFO(one.message);
        REQUIRE(one.status == orca::SliceStatus::success);
        const Bounds printed = extrusion_bounds(output);
        CHECK(printed.max_x == Catch::Approx(185.0).margin(1.0));
    }
}

TEST_CASE("The settings of a part sit on the ones of the object it belongs to", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::process, "0.20mm Standard @Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    const auto part = orca::PresetKind::part;
    const std::string frequent = "Frequent";

    const auto setting = [](const orca::PresetSettings& settings, const std::string& id) -> const orca::SettingState& {
        const auto state = std::find_if(settings.settings.begin(), settings.settings.end(),
                                        [&id](const orca::SettingState& candidate) { return candidate.id == id; });
        INFO(id);
        REQUIRE(state != settings.settings.end());
        return *state;
    };
    const auto model_settings = [](const std::vector<std::pair<std::string, std::string>>& overrides) {
        orca::ModelSettings settings;
        for (const auto& [key, value] : overrides) {
            settings.keys.push_back(key);
            settings.values.push_back(value);
        }
        return settings;
    };
    const auto overridden = [](const orca::ModelSettings& settings, const std::string& key) {
        const auto found = std::find(settings.keys.begin(), settings.keys.end(), key);
        return found == settings.keys.end() ? std::string("<unset>") : settings.values[std::size_t(found - settings.keys.begin())];
    };

    SECTION("the part shows the settings of its object, which its own override")
    {
        // TabPrintPart::m_parent_tab is the model tab: the object overrides the
        // walls, and the part alone overrides them again.
        orca::ModelSettingsRequest request;
        request.settings = {model_settings({{"wall_loops", "7"}})};
        request.parent = model_settings({{"wall_loops", "5"}, {"sparse_infill_density", "35%"}});

        const orca::PresetSettings settings = orca::describe_settings(part, frequent, {}, request);
        INFO(settings.message);
        REQUIRE(settings.status == orca::SceneStatus::success);
        CHECK(settings.kind == part);
        CHECK(setting(settings, "wall_loops").value == "7");
        // The object's own value applies where the part overrides nothing; a
        // percent field shows the number alone, as the desktop app's does.
        CHECK(setting(settings, "sparse_infill_density").value == "35");
    }

    SECTION("a part overrides the settings of its region alone")
    {
        // TabPrintPart: PrintRegionConfig().keys(), so the layer height of the
        // object is on no page of a part.
        orca::ModelSettingsRequest request;
        request.settings = {orca::ModelSettings{}};
        const orca::PresetSettings settings = orca::describe_settings(part, frequent, {}, request);
        REQUIRE(settings.status == orca::SceneStatus::success);
        const auto height = std::find_if(settings.settings.begin(), settings.settings.end(),
                                         [](const orca::SettingState& state) { return state.key == "layer_height"; });
        CHECK(height == settings.settings.end());
        CHECK(std::any_of(settings.settings.begin(), settings.settings.end(),
                          [](const orca::SettingState& state) { return state.key == "wall_loops"; }));
    }

    SECTION("a change becomes an override of the part, and the undo goes back to the object")
    {
        orca::ModelSettingsRequest request;
        request.settings = {orca::ModelSettings{}};
        request.parent = model_settings({{"wall_loops", "5"}});

        const orca::PresetSettings changed = orca::change_setting(part, frequent, "wall_loops", "9", {}, request);
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        REQUIRE_FALSE(changed.model_settings.empty());
        CHECK(overridden(changed.model_settings.front(), "wall_loops") == "9");
        CHECK(setting(changed, "wall_loops").modified);

        orca::ModelSettingsRequest kept = request;
        kept.settings = {model_settings({{"wall_loops", "9"}})};
        const orca::PresetSettings undone = orca::reset_settings(part, frequent, {"wall_loops"}, {}, kept);
        REQUIRE(undone.status == orca::SceneStatus::success);
        REQUIRE_FALSE(undone.model_settings.empty());
        CHECK(undone.model_settings.front().keys.empty());
        // Back to the value of the object, not of the process preset.
        CHECK(setting(undone, "wall_loops").value == "5");
    }
}

TEST_CASE("A part added to an object prints with it, and a negative volume is taken out", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("parts.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));
    const std::string output = output_path("parts.gcode");

    const orca::SliceResult alone = orca::slice("parts-alone", plate, output, {}, k2_plus_profiles(), {}, {});
    INFO(alone.message);
    REQUIRE(alone.status == orca::SliceStatus::success);

    // ObjectList::load_generic_subobject(): the shape joins the object beside it.
    const orca::ModelInspection added = orca::add_object_part(plate.front(), "Cube", orca::VolumeType::part, k2_plus_profiles(), output_path("part.mesh"));
    INFO(added.message);
    REQUIRE(added.status == orca::SceneStatus::success);
    CHECK(added.facet_count == 12);
    // 5% of the largest side of the bed is 17.5 mm, and the shape is twice that.
    CHECK(added.size_x == Catch::Approx(35.0).margin(0.01));
    REQUIRE(added.instance_matrix.size() == 16);

    orca::ObjectPart part;
    part.shape = "Cube";
    part.type = orca::VolumeType::part;
    part.matrix.assign(added.instance_matrix.begin(), added.instance_matrix.end());
    plate.front().parts.push_back(part);

    const orca::SliceResult with_part = orca::slice("parts-with", plate, output, {}, k2_plus_profiles(), {}, {});
    INFO(with_part.message);
    REQUIRE(with_part.status == orca::SliceStatus::success);
    CHECK(with_part.filament_micrometers > alone.filament_micrometers);
    // The part stands beside the object, so the print reaches further.
    const Bounds bounds = extrusion_bounds(output);
    CHECK(bounds.max_x > 195.0);

    SECTION("a negative volume is taken out of the object")
    {
        // The shape overlaps the right half of the cube, which is centred
        // around the origin, so the print keeps only what is left of it.
        plate.front().parts.front().type = orca::VolumeType::negative;
        std::vector<double>& matrix = plate.front().parts.front().matrix;
        matrix[12] = 20.0;
        matrix[13] = 0.0;
        matrix[14] = 0.0;

        const orca::SliceResult cut = orca::slice("parts-negative", plate, output, {}, k2_plus_profiles(), {}, {});

        INFO(cut.message);
        REQUIRE(cut.status == orca::SliceStatus::success);
        const Bounds cut_bounds = extrusion_bounds(output);
        // The cube reached 185 mm; the shape starts at 2.5 mm of its own.
        CHECK(cut_bounds.max_x == Catch::Approx(177.5).margin(1.0));
        CHECK(cut.filament_micrometers < with_part.filament_micrometers);
    }
}

TEST_CASE("A height range prints with its own layer height", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("ranges.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));
    const std::string output = output_path("ranges.gcode");

    const orca::SliceResult plain = orca::slice("ranges-plain", plate, output, {}, k2_plus_profiles(), {}, {});
    INFO(plain.message);
    REQUIRE(plain.status == orca::SliceStatus::success);
    // A 20 mm cube at 0.2 mm is 100 layers.
    CHECK(plain.layer_count == 100);

    SECTION("the range slices its own slab thinner")
    {
        // ObjectList::layers_editing(): the first range the desktop app adds is
        // 0 to 2 mm. At 0.1 mm its 2 mm hold the 0.2 mm first layer and 18
        // layers of its own (19 instead of 10), and the 18 mm above it stay at
        // 0.2 mm: 19 + 90 = 109 layers.
        orca::LayerRange range;
        range.bottom = 0.0;
        range.top = 2.0;
        range.settings.keys = {"layer_height"};
        range.settings.values = {"0.1"};
        plate.front().layer_ranges.push_back(range);

        const orca::SliceResult sliced = orca::slice("ranges-thin", plate, output, {}, k2_plus_profiles(), {}, {});
        INFO(sliced.message);
        REQUIRE(sliced.status == orca::SliceStatus::success);
        CHECK(sliced.layer_count == 109);
    }

    SECTION("a range above the object changes nothing")
    {
        orca::LayerRange range;
        range.bottom = 30.0;
        range.top = 32.0;
        range.settings.keys = {"layer_height"};
        range.settings.values = {"0.1"};
        plate.front().layer_ranges.push_back(range);

        const orca::SliceResult sliced = orca::slice("ranges-above", plate, output, {}, k2_plus_profiles(), {}, {});
        INFO(sliced.message);
        REQUIRE(sliced.status == orca::SliceStatus::success);
        CHECK(sliced.layer_count == plain.layer_count);
    }
}

TEST_CASE("A plate with two filaments prints each object with its own", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection first = orca::inspect_model({}, k2_plus_profiles(), output_path("mm-first.mesh"), {});
    REQUIRE(first.status == orca::SceneStatus::success);
    std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(first));
    // A second cube beside the first one, printed with the other filament.
    orca::PlateObject second;
    second.instances.push_back(orca::ObjectPlacement{matrix_of(first), true, true});
    second.instances.front().matrix[12] = 60.0;
    second.settings.keys = {"extruder"};
    second.settings.values = {"2"};
    plate.push_back(second);

    orca::ProfileSelection profiles = k2_plus_profiles();
    // Two slots of the same filament: OrcaSlicer refuses materials whose nozzle
    // temperatures do not overlap, which is a check of its own.
    profiles.filaments = {k2_plus_profiles().filament, k2_plus_profiles().filament};
    const std::string output = output_path("multi.gcode");

    const std::string tower = output_path("multi.tower.mesh");
    const orca::SliceResult sliced = orca::slice("multi", plate, output, {}, profiles, {}, {}, tower);
    INFO(sliced.message);
    REQUIRE(sliced.status == orca::SliceStatus::success);
    // load_real_wipe_tower_preview(): the tower the slice built is written for the plate.
    CHECK(sliced.wipe_tower_written);
    CHECK(std::ifstream(tower).good());

    // The print changes tools, which only a plate with two filaments does.
    std::ifstream gcode(output);
    REQUIRE(gcode.is_open());
    bool changes_tool = false;
    std::string line;
    while (std::getline(gcode, line)) {
        if (line.rfind("T1", 0) == 0) {
            changes_tool = true;
            break;
        }
    }
    CHECK(changes_tool);
}

TEST_CASE("A part and a height range print with the filament they are given", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("mm-part.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));

    // A part of the model standing in the cube, printed with the second
    // filament while the object itself keeps the first one
    // (ObjectList::set_extruder_for_selected_items).
    const orca::ModelInspection added = orca::add_object_part(plate.front(), "Cube", orca::VolumeType::part, k2_plus_profiles(), output_path("mm-part-shape.mesh"));
    INFO(added.message);
    REQUIRE(added.status == orca::SceneStatus::success);
    REQUIRE(added.instance_matrix.size() == 16);

    orca::ObjectPart part;
    part.shape = "Cube";
    part.type = orca::VolumeType::part;
    part.matrix.assign(added.instance_matrix.begin(), added.instance_matrix.end());
    part.settings.keys = {"extruder"};
    part.settings.values = {"2"};
    plate.front().parts.push_back(part);

    // And a range of the object printed with the second one as well.
    orca::LayerRange range;
    range.bottom = 0.0;
    range.top = 2.0;
    range.settings.keys = {"layer_height", "extruder"};
    range.settings.values = {"0.1", "2"};
    plate.front().layer_ranges.push_back(range);

    orca::ProfileSelection profiles = k2_plus_profiles();
    profiles.filaments = {k2_plus_profiles().filament, k2_plus_profiles().filament};
    const std::string output = output_path("multi-part.gcode");

    const orca::SliceResult sliced = orca::slice("multi-part", plate, output, {}, profiles, {}, {});
    INFO(sliced.message);
    REQUIRE(sliced.status == orca::SliceStatus::success);

    std::ifstream gcode(output);
    REQUIRE(gcode.is_open());
    bool changes_tool = false;
    std::string line;
    while (std::getline(gcode, line)) {
        if (line.rfind("T1", 0) == 0) {
            changes_tool = true;
            break;
        }
    }
    CHECK(changes_tool);
}

TEST_CASE("The settings of an object's own mesh reach the print", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("volume.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));
    // The first ModelVolume prints with the second filament, as the object
    // list sets it on the row of the object's own mesh.
    plate.front().volume_settings.keys = {"extruder"};
    plate.front().volume_settings.values = {"2"};

    orca::ProfileSelection profiles = k2_plus_profiles();
    profiles.filaments = {k2_plus_profiles().filament, k2_plus_profiles().filament};
    const std::string output = output_path("volume-extruder.gcode");
    const orca::SliceResult sliced = orca::slice("volume-extruder", plate, output, {}, profiles, {}, {});
    INFO(sliced.message);
    REQUIRE(sliced.status == orca::SliceStatus::success);

    std::ifstream gcode(output);
    REQUIRE(gcode.is_open());
    // The first tool the print selects is the second filament's.
    std::string line;
    std::string first_tool;
    while (first_tool.empty() && std::getline(gcode, line)) {
        if (line.size() >= 2 && line[0] == 'T' && std::isdigit(static_cast<unsigned char>(line[1]))) {
            first_tool = line;
        }
    }
    CHECK(first_tool == "T1");
}

TEST_CASE("The wipe tower stands on a plate that prints with two filaments", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("wt.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    const std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));

    // One filament: the plate has nothing to wipe.
    const orca::WipeTowerState alone = orca::describe_wipe_tower(plate, k2_plus_profiles(), {});
    INFO(alone.message);
    REQUIRE(alone.status == orca::SceneStatus::success);
    CHECK_FALSE(alone.shown);
    // What the object menu's Flush Options go by is described all the same:
    // the filaments printed, and the process preset's prime tower.
    CHECK(alone.filaments == std::vector<int>{1});
    CHECK(alone.prime_tower);

    orca::ProfileSelection profiles = k2_plus_profiles();
    profiles.filaments = {k2_plus_profiles().filament, k2_plus_profiles().filament};
    std::vector<orca::PlateObject> two = plate;
    two.front().settings.keys = {"extruder"};
    two.front().settings.values = {"2"};
    // A second cube beside the first one, printed with the first filament.
    orca::PlateObject second;
    second.instances.push_back(orca::ObjectPlacement{matrix_of(cube), true, true});
    second.instances.front().matrix[12] = 60.0;
    two.push_back(second);

    const orca::WipeTowerState tower = orca::describe_wipe_tower(two, profiles, {});
    INFO(tower.message);
    REQUIRE(tower.status == orca::SceneStatus::success);
    CHECK(tower.shown);
    // PartPlate::get_extruders(): both filaments are printed on the plate.
    CHECK(tower.filaments == std::vector<int>{1, 2});
    // estimate_wipe_tower_size(): this printer's profile asks for Orca's rib
    // wall, whose tower is square and as deep as the wiped volume needs; it is
    // printed up to the tallest object of the plate.
    CHECK(tower.depth > 0.0);
    CHECK(tower.width == Catch::Approx(tower.depth).margin(0.01));
    CHECK(tower.height == Catch::Approx(20.0).margin(0.01));
    // set_default_wipe_tower_pos_for_plate(): the desktop position, clamped
    // into the 350 x 350 plate with room for the brim.
    CHECK(tower.x == Catch::Approx(165.0).margin(0.01));
    CHECK(tower.y < 350.0);
    CHECK(tower.y + tower.depth < 350.0);

    // The project places it: the app keeps the position among the plate's settings.
    orca::ModelSettings placed;
    placed.keys = {"wipe_tower_x", "wipe_tower_y"};
    placed.values = {"40", "30"};
    const orca::WipeTowerState moved = orca::describe_wipe_tower(two, profiles, placed);
    INFO(moved.message);
    REQUIRE(moved.status == orca::SceneStatus::success);
    CHECK(moved.shown);
    CHECK(moved.x == Catch::Approx(40.0).margin(0.01));
    CHECK(moved.y == Catch::Approx(30.0).margin(0.01));
}

TEST_CASE("The flushing volumes of the plate reach the G-code", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("flush-slice.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));
    plate.front().settings.keys = {"extruder"};
    plate.front().settings.values = {"2"};
    orca::PlateObject second;
    second.instances.push_back(orca::ObjectPlacement{matrix_of(cube), true, true});
    second.instances.front().matrix[12] = 60.0;
    plate.push_back(second);

    orca::ProfileSelection profiles = k2_plus_profiles();
    profiles.filaments = {k2_plus_profiles().filament, k2_plus_profiles().filament};

    orca::ModelSettings plate_settings;
    plate_settings.keys = {"flush_volumes_matrix", "flush_multiplier"};
    plate_settings.values = {"0,600,400,0", "0.5"};
    const std::string output = output_path("flush-slice.gcode");
    const orca::SliceResult sliced = orca::slice("flush", plate, output, {}, profiles, plate_settings, {});
    INFO(sliced.message);
    REQUIRE(sliced.status == orca::SliceStatus::success);

    // GCode::append_full_config() writes the matrix multiplied by the
    // multiplier, which is what the print flushes.
    std::ifstream gcode(output);
    REQUIRE(gcode.is_open());
    std::string line;
    std::string matrix;
    while (std::getline(gcode, line)) {
        if (line.rfind("; flush_volumes_matrix", 0) == 0) {
            matrix = line;
        }
    }
    CHECK(matrix == "; flush_volumes_matrix = 0,300,200,0");
}

TEST_CASE("A model painted with a filament prints with it", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("paint.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));

    orca::ProfileSelection profiles = k2_plus_profiles();
    profiles.filaments = {k2_plus_profiles().filament, k2_plus_profiles().filament};

    // GLGizmoMmuSegmentation: the tool opens on the object, paints, and closes.
    const orca::PaintingState opened =
        orca::begin_painting(plate.front(), -1, profiles, {}, output_path("paint-face"));
    INFO(opened.message);
    REQUIRE(opened.status == orca::SceneStatus::success);
    CHECK(opened.filaments.empty());

    // A brush stroke onto the top of the cube, from straight above.
    orca::PaintStroke stroke;
    const std::vector<double> center = matrix_of(cube);
    stroke.origin[0] = center[12];
    stroke.origin[1] = center[13];
    stroke.origin[2] = 100.0;
    stroke.direction[2] = -1.0;
    stroke.filament = 2;
    stroke.radius = 6.0;
    const orca::PaintingState painted = orca::paint(stroke, output_path("paint-face"));
    INFO(painted.message);
    REQUIRE(painted.status == orca::SceneStatus::success);
    CHECK(painted.hit);
    // The painted triangles are reported as a mesh per filament, for the 3D view.
    REQUIRE(painted.filaments.size() == 1);
    CHECK(painted.filaments.front() == 2);
    REQUIRE(painted.meshes.size() == 1);
    CHECK(std::ifstream(painted.meshes.front()).good());

    const orca::PaintingState closed = orca::end_painting();
    INFO(closed.message);
    REQUIRE(closed.status == orca::SceneStatus::success);
    REQUIRE_FALSE(closed.facets.empty());

    // The tool's own Undo and Redo: a stroke is undone as a whole, and a
    // stroke that begins off the model and moves onto it is one step too.
    {
        const orca::PaintingState again = orca::begin_painting(plate.front(), -1, profiles, {}, output_path("paint-undo"));
        REQUIRE(again.status == orca::SceneStatus::success);
        CHECK_FALSE(again.can_undo);
        orca::PaintStroke first = stroke;
        first.starts = true;
        orca::PaintStroke off = stroke;
        off.origin[0] = center[12] + 500.0;
        off.starts = true;
        CHECK_FALSE(orca::paint(off, output_path("paint-undo")).can_undo);
        const orca::PaintingState onto = orca::paint(stroke, output_path("paint-undo"));
        CHECK(onto.can_undo);
        CHECK(onto.filaments.size() == 1);
        const orca::PaintingState undone = orca::undo_painting(output_path("paint-undo"));
        REQUIRE(undone.status == orca::SceneStatus::success);
        CHECK(undone.filaments.empty());
        CHECK_FALSE(undone.can_undo);
        CHECK(undone.can_redo);
        const orca::PaintingState redone = orca::redo_painting(output_path("paint-undo"));
        CHECK(redone.filaments.size() == 1);
        CHECK(redone.can_undo);
        CHECK_FALSE(redone.can_redo);
        // A new stroke drops what Undo left.
        CHECK(orca::undo_painting(output_path("paint-undo")).can_redo);
        const orca::PaintingState repainted = orca::paint(first, output_path("paint-undo"));
        CHECK(repainted.can_undo);
        CHECK_FALSE(repainted.can_redo);
        const orca::PaintingState kept = orca::end_painting();
        REQUIRE(kept.status == orca::SceneStatus::success);
        CHECK(kept.facets == closed.facets);
    }

    // A stroke that misses the model paints nothing.
    const orca::PaintingState reopened =
        orca::begin_painting(plate.front(), -1, profiles, closed.facets, output_path("paint-again"));
    REQUIRE(reopened.status == orca::SceneStatus::success);
    // The painted facets come back with the session.
    REQUIRE(reopened.filaments.size() == 1);
    orca::PaintStroke away = stroke;
    away.origin[0] = center[12] + 500.0;
    const orca::PaintingState missed = orca::paint(away, output_path("paint-again"));
    REQUIRE(missed.status == orca::SceneStatus::success);
    CHECK_FALSE(missed.hit);
    REQUIRE(orca::end_painting().status == orca::SceneStatus::success);

    // The painted object prints with both filaments.
    plate.front().painted = closed.facets;
    const std::string output = output_path("painted.gcode");
    const orca::SliceResult sliced = orca::slice("painted", plate, output, {}, profiles, {}, {});
    INFO(sliced.message);
    REQUIRE(sliced.status == orca::SliceStatus::success);

    std::ifstream gcode(output);
    REQUIRE(gcode.is_open());
    bool changes_tool = false;
    std::string line;
    while (std::getline(gcode, line)) {
        if (line.rfind("T1", 0) == 0) {
            changes_tool = true;
            break;
        }
    }
    CHECK(changes_tool);
}

TEST_CASE("The flushing volumes follow the colours of the filaments", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("flush.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    const std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));

    orca::ProfileSelection profiles = k2_plus_profiles();
    profiles.filaments = {k2_plus_profiles().filament, k2_plus_profiles().filament};

    orca::ModelSettings colors;
    colors.keys = {"filament_colour"};
    colors.values = {"#FFFFFF;#000000"};
    const orca::FlushVolumes apart = orca::describe_flush_volumes(plate, profiles, colors);
    INFO(apart.message);
    REQUIRE(apart.status == orca::SceneStatus::success);
    REQUIRE(apart.filaments == 2);
    REQUIRE(apart.nozzles == 1);
    REQUIRE(apart.matrix.size() == 4);
    REQUIRE(apart.automatic.size() == 4);
    REQUIRE(apart.multipliers.size() == 1);
    // flush_multiplier comes from the profile; this printer asks for 0.3.
    CHECK(apart.multipliers.front() > 0.0);
    // CalcFlushingVolumes(): a filament is never flushed into itself, and white
    // and black, as far apart as two colours get, ask for filament both ways.
    CHECK(apart.automatic[0] == 0.0);
    CHECK(apart.automatic[3] == 0.0);
    CHECK(apart.automatic[1] > 0.0);
    CHECK(apart.automatic[2] > 0.0);

    // Two filaments of the same colour need less than white into black.
    orca::ModelSettings same;
    same.keys = {"filament_colour"};
    same.values = {"#FFFFFF;#FFFFFF"};
    const orca::FlushVolumes alike = orca::describe_flush_volumes(plate, profiles, same);
    INFO(alike.message);
    REQUIRE(alike.status == orca::SceneStatus::success);
    CHECK(alike.automatic[1] < apart.automatic[1]);

    // The project's own matrix is reported as it stands, beside the calculated
    // volumes the app can go back to (is_flush_config_modified).
    orca::ModelSettings stored;
    stored.keys = {"filament_colour", "flush_volumes_matrix", "flush_multiplier"};
    stored.values = {"#FFFFFF;#000000", "0,123,456,0", "1"};
    const orca::FlushVolumes kept = orca::describe_flush_volumes(plate, profiles, stored);
    INFO(kept.message);
    REQUIRE(kept.status == orca::SceneStatus::success);
    REQUIRE(kept.matrix.size() == 4);
    CHECK(kept.matrix[1] == Catch::Approx(123.0));
    CHECK(kept.matrix[2] == Catch::Approx(456.0));
    CHECK(kept.multipliers.front() == Catch::Approx(1.0));
    CHECK(kept.modified);
    CHECK(kept.automatic == apart.automatic);
}

TEST_CASE("The flushing volumes are worked out again where the desktop app does it", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("flush-update.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    const std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));
    const auto project = [](const std::string& colors, const std::string& matrix) {
        orca::ModelSettings settings;
        settings.keys = {"filament_colour", "flush_volumes_matrix", "flush_multiplier"};
        settings.values = {colors, matrix, "1"};
        return settings;
    };
    orca::ProfileSelection two = k2_plus_profiles();
    two.filaments = {two.filament, two.filament};
    orca::ProfileSelection three = two;
    three.filaments.push_back(two.filament);

    SECTION("a filament added to a project of two gets its own row and column, and the others stay")
    {
        const orca::FlushVolumesUpdate added =
            orca::update_flush_volumes(plate, three, project("#FFFFFF;#000000;#FF0000", "0,123,456,0"), orca::FlushVolumesChange::filament_added, 2);
        INFO(added.volumes.message);
        REQUIRE(added.volumes.status == orca::SceneStatus::success);
        CHECK(added.updated);
        const std::vector<double>& matrix = added.volumes.matrix;
        const std::vector<double>& automatic = added.volumes.automatic;
        REQUIRE(matrix.size() == 9);
        REQUIRE(automatic.size() == 9);
        // update_multi_material_filament_presets() keeps the pairs it had.
        CHECK(matrix[1] == Catch::Approx(123.0));
        CHECK(matrix[3] == Catch::Approx(456.0));
        // auto_calc_flushing_volumes() works the new filament out from the colours.
        for (const std::size_t cell : {2u, 5u, 6u, 7u}) {
            INFO(cell);
            CHECK(matrix[cell] == Catch::Approx(automatic[cell]));
            CHECK(matrix[cell] > 0.0);
        }
        CHECK(matrix[8] == 0.0);
    }

    SECTION("a colour change works the row and column of that filament out again")
    {
        const orca::FlushVolumesUpdate recoloured = orca::update_flush_volumes(
            plate, three, project("#FFFFFF;#00FF00;#FF0000", "0,11,12,21,0,23,31,32,0"), orca::FlushVolumesChange::color_changed, 1);
        INFO(recoloured.volumes.message);
        REQUIRE(recoloured.volumes.status == orca::SceneStatus::success);
        CHECK(recoloured.updated);
        const std::vector<double>& matrix = recoloured.volumes.matrix;
        REQUIRE(matrix.size() == 9);
        CHECK(matrix[2] == Catch::Approx(12.0));
        CHECK(matrix[6] == Catch::Approx(31.0));
        for (const std::size_t cell : {1u, 3u, 5u, 7u}) {
            INFO(cell);
            CHECK(matrix[cell] == Catch::Approx(recoloured.volumes.automatic[cell]));
        }
    }

    SECTION("a filament taken out takes its row and column with it, and nothing is worked out")
    {
        const orca::FlushVolumesUpdate removed = orca::update_flush_volumes(
            plate, two, project("#FFFFFF;#FF0000", "0,11,12,21,0,23,31,32,0"), orca::FlushVolumesChange::filament_removed, 1);
        INFO(removed.volumes.message);
        REQUIRE(removed.volumes.status == orca::SceneStatus::success);
        CHECK(removed.updated);
        CHECK(removed.volumes.matrix == std::vector<double>{0.0, 12.0, 31.0, 0.0});
    }

    SECTION("a project that already fits and has nothing to work out stays as it is")
    {
        const orca::FlushVolumesUpdate same = orca::update_flush_volumes(
            plate, two, project("#FFFFFF;#000000", "0,123,456,0"), orca::FlushVolumesChange::filament_removed, 5);
        REQUIRE(same.volumes.status == orca::SceneStatus::success);
        CHECK_FALSE(same.updated);
        CHECK(same.volumes.matrix == std::vector<double>{0.0, 123.0, 456.0, 0.0});
    }
}

TEST_CASE("A plate prints with several filaments the sidebar lists", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::filament, k2_plus_profiles().filament).status == orca::SceneStatus::success);

    const orca::PresetState one = orca::describe_presets();
    REQUIRE(one.status == orca::SceneStatus::success);
    REQUIRE(one.selection.filaments.size() == 1);
    CHECK(one.selection.filaments.front() == k2_plus_profiles().filament);

    // Sidebar::add_custom_filament(): the new filament takes the next colour
    // of OrcaSlicer's palette, and starts with the preset of the first one.
    const orca::PresetState added = orca::add_filament();
    INFO(added.message);
    REQUIRE(added.status == orca::SceneStatus::success);
    REQUIRE(added.selection.filaments.size() == 2);
    REQUIRE(added.filament_colors.size() == 2);
    CHECK(added.filament_colors[1] == "#F4E2C1");

    // A slot of its own takes another preset, and the first one is untouched.
    const std::string other = "Generic PETG @K2 Plus-all";
    const orca::PresetState changed = orca::select_filament(1, other, orca::PresetChangeAction::discard);
    INFO(changed.message);
    REQUIRE(changed.status == orca::SceneStatus::success);
    REQUIRE(changed.selection.filaments.size() == 2);
    CHECK(changed.selection.filaments[0] == k2_plus_profiles().filament);
    CHECK(changed.selection.filaments[1] == other);
    // The tab still edits the first filament.
    CHECK(changed.selection.filament == k2_plus_profiles().filament);

    const orca::PresetState colored = orca::set_filament_color(1, "#123456");
    REQUIRE(colored.status == orca::SceneStatus::success);
    REQUIRE(colored.filament_colors.size() == 2);
    CHECK(colored.filament_colors[1] == "#123456");

    // Sidebar::delete_filament(): the plate keeps at least one filament.
    const orca::PresetState removed = orca::remove_filament(1);
    INFO(removed.message);
    REQUIRE(removed.status == orca::SceneStatus::success);
    CHECK(removed.selection.filaments.size() == 1);
    CHECK(orca::remove_filament(0).status != orca::SceneStatus::success);
}

TEST_CASE("The printer tab shows how many extruders the printer has", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);

    // "extruders_count" is no setting of the preset: the tab fills it from the
    // number of nozzle diameters (TabPrinter::reload_config).
    const orca::PresetSettings settings = orca::describe_settings(orca::PresetKind::printer, "Multimaterial", {});
    INFO(settings.message);
    REQUIRE(settings.status == orca::SceneStatus::success);
    const auto count = std::find_if(settings.settings.begin(), settings.settings.end(),
                                    [](const orca::SettingState& state) { return state.key == "extruders_count"; });
    REQUIRE(count != settings.settings.end());
    CHECK(count->value == "1");
}

TEST_CASE("The condition of compatible presets is edited only while none is listed", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    const auto filament = orca::PresetKind::filament;
    const std::string page = "Dependencies";

    const auto condition = [](const orca::PresetSettings& settings) {
        const auto found = std::find_if(settings.settings.begin(), settings.settings.end(),
                                        [](const orca::SettingState& state) { return state.key == "compatible_printers_condition"; });
        REQUIRE(found != settings.settings.end());
        return *found;
    };

    // Tab::compatible_widget_reload(): with no printer listed the expression is
    // what says which printers the filament is for, so its field is editable.
    const orca::PresetSettings none = orca::describe_settings(filament, page, {});
    INFO(none.message);
    REQUIRE(none.status == orca::SceneStatus::success);
    CHECK(condition(none).enabled);

    // Listing printers takes the expression out of use.
    const orca::PresetSettings listed =
        orca::set_compatible_presets(filament, page, "compatible_printers", {"Creality K2 Plus 0.4 nozzle"}, {});
    INFO(listed.message);
    REQUIRE(listed.status == orca::SceneStatus::success);
    CHECK_FALSE(condition(listed).enabled);

    // The preset is left as it was found.
    REQUIRE(orca::set_compatible_presets(filament, page, "compatible_printers", {}, {}).status == orca::SceneStatus::success);
    REQUIRE(orca::reset_settings(filament, page, {}, {}, {}).status == orca::SceneStatus::success);
}

TEST_CASE("A printer to send G-code to is kept as the physical printer dialog keeps it", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    const std::string name = "Orcinus test host";

    const auto setting = [](const orca::PhysicalPrinterState& printer, const std::string& key) {
        const auto found = std::find(printer.settings.keys.begin(), printer.settings.keys.end(), key);
        return found == printer.settings.keys.end() ? std::string("<unset>")
                                                    : printer.settings.values[std::size_t(found - printer.settings.keys.begin())];
    };
    const auto find = [&name](const orca::PhysicalPrinters& printers) {
        return std::find_if(printers.printers.begin(), printers.printers.end(),
                            [&name](const orca::PhysicalPrinterState& printer) { return printer.name == name; });
    };

    orca::PhysicalPrinterState printer;
    printer.name = name;
    printer.preset_names = {"Creality K2 Plus 0.4 nozzle"};
    printer.settings.keys = {"host_type", "print_host", "printhost_apikey"};
    printer.settings.values = {"octoprint", "http://192.168.1.50", "abcdef"};

    const orca::PhysicalPrinters saved = orca::save_physical_printer(printer, {});
    INFO(saved.message);
    REQUIRE(saved.status == orca::SceneStatus::success);
    auto stored = find(saved);
    REQUIRE(stored != saved.printers.end());
    CHECK(setting(*stored, "host_type") == "octoprint");
    CHECK(setting(*stored, "print_host") == "http://192.168.1.50");
    CHECK(setting(*stored, "printhost_apikey") == "abcdef");
    CHECK(stored->preset_names == std::vector<std::string>{"Creality K2 Plus 0.4 nozzle"});

    // The printer is there for the next request, as the collection keeps it.
    const orca::PhysicalPrinters listed = orca::physical_printers();
    REQUIRE(listed.status == orca::SceneStatus::success);
    REQUIRE(find(listed) != listed.printers.end());

    // Saving under the same name changes the printer instead of adding one.
    printer.settings.values = {"moonraker", "http://192.168.1.60", "abcdef"};
    const orca::PhysicalPrinters changed = orca::save_physical_printer(printer, {});
    REQUIRE(changed.status == orca::SceneStatus::success);
    stored = find(changed);
    REQUIRE(stored != changed.printers.end());
    CHECK(setting(*stored, "host_type") == "moonraker");
    CHECK(std::count_if(changed.printers.begin(), changed.printers.end(),
                        [&name](const orca::PhysicalPrinterState& one) { return one.name == name; }) == 1);

    const orca::PhysicalPrinters deleted = orca::delete_physical_printer(name);
    REQUIRE(deleted.status == orca::SceneStatus::success);
    CHECK(find(deleted) == deleted.printers.end());
}

TEST_CASE("A user preset is exported to files and imported back", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::process, "0.20mm Standard @Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    const auto print = orca::PresetKind::print;
    const std::string name = "Orcinus exported process";
    const std::string directory = (fs::path(device_dir) / "tmp" / "configs").string();
    fs::remove_all(directory);

    // A preset of the user's own, which is what an export writes.
    REQUIRE(orca::change_setting(print, "Quality", "layer_height", "0.14", {}).dirty);
    REQUIRE(orca::save_preset(print, name).status == orca::SceneStatus::success);

    // The dialog lists the printer the user's process preset belongs to.
    const orca::ConfigExportOptions options = orca::config_export_options(orca::ConfigExportKind::process_presets);
    INFO(options.message);
    REQUIRE(options.status == orca::SceneStatus::success);
    const std::string printer = "Creality K2 Plus 0.4 nozzle";
    const auto exported_printer = std::find_if(options.entries.begin(), options.entries.end(),
                                               [&printer](const orca::ConfigExportEntry& entry) { return entry.name == printer; });
    REQUIRE(exported_printer != options.entries.end());
    CHECK(exported_printer->count >= 1);
    // A printer preset of the user's there is none of.
    const orca::ConfigExportOptions printers = orca::config_export_options(orca::ConfigExportKind::printer_presets);
    REQUIRE(printers.status == orca::SceneStatus::success);
    CHECK(std::none_of(printers.entries.begin(), printers.entries.end(),
                       [&printer](const orca::ConfigExportEntry& entry) { return entry.name == printer; }));

    const orca::ConfigTransfer exported = orca::export_configs(orca::ConfigExportKind::process_presets, {printer}, directory);
    INFO(exported.message);
    REQUIRE(exported.status == orca::SceneStatus::success);
    // Every user process preset of the printer goes into one archive.
    REQUIRE(exported.names.size() == 1);
    CHECK(fs::path(exported.names.front()).filename().string() == "Process presets.zip");
    REQUIRE(fs::exists(exported.names.front()));
    CHECK(fs::file_size(exported.names.front()) > 0);

    // The preset is deleted, and the file brings it back.
    const auto listed = [&name] {
        const orca::PresetState state = orca::describe_presets();
        return std::any_of(state.processes.begin(), state.processes.end(),
                           [&name](const orca::PresetItem& item) { return item.name == name; });
    };
    REQUIRE(listed());
    // Deleting a preset asks first, as the desktop app asks.
    REQUIRE(orca::delete_preset(print, {{"delete_preset", true}}).status == orca::SceneStatus::success);
    CHECK_FALSE(listed());

    const orca::ConfigTransfer imported = orca::import_presets({exported.names.front()});
    INFO(imported.message);
    REQUIRE(imported.status == orca::SceneStatus::success);
    CHECK(imported.names.size() == 1);

    CHECK(listed());
    const orca::PresetState selected = orca::select_preset(orca::PresetChoice::process, name);
    INFO(selected.message);
    REQUIRE(selected.status == orca::SceneStatus::success);
    const orca::PresetSettings settings = orca::describe_settings(print, "Quality", {});
    REQUIRE(settings.status == orca::SceneStatus::success);
    CHECK(settings.preset == name);
    const auto height = std::find_if(settings.settings.begin(), settings.settings.end(),
                                     [](const orca::SettingState& state) { return state.id == "layer_height"; });
    REQUIRE(height != settings.settings.end());
    CHECK(height->value == "0.14");

    // The test leaves the profiles as it found them.
    REQUIRE(orca::delete_preset(print, {{"delete_preset", true}}).status == orca::SceneStatus::success);
    fs::remove_all(directory);
}

TEST_CASE("A printer of the user's own is created from a vendor's preset", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);

    // The first page offers the vendors and models the dialog knows.
    const orca::CreatePrinterOptions opened = orca::create_printer_options({}, {}, {}, {});
    INFO(opened.message);
    REQUIRE(opened.status == orca::SceneStatus::success);
    CHECK(std::find(opened.vendors.begin(), opened.vendors.end(), "Creality") != opened.vendors.end());
    CHECK(std::find(opened.nozzle_diameters.begin(), opened.nozzle_diameters.end(), "0.4") != opened.nozzle_diameters.end());
    CHECK(opened.models.empty());

    const orca::CreatePrinterOptions of_vendor = orca::create_printer_options("Creality", "0.4", "Creality", {});
    INFO(of_vendor.message);
    REQUIRE(of_vendor.status == orca::SceneStatus::success);
    CHECK_FALSE(of_vendor.models.empty());
    CHECK(std::find(of_vendor.preset_vendors.begin(), of_vendor.preset_vendors.end(), "Creality") != of_vendor.preset_vendors.end());
    REQUIRE_FALSE(of_vendor.printer_presets.empty());

    // The second page offers the presets that come with the chosen printer.
    const auto listed = std::find_if(of_vendor.printer_presets.begin(), of_vendor.printer_presets.end(),
                                     [](const std::string& name) { return name.find("K2 Plus") != std::string::npos && name.find("0.4") != std::string::npos; });
    REQUIRE(listed != of_vendor.printer_presets.end());
    const std::string printer_preset = *listed;
    const orca::CreatePrinterOptions chosen = orca::create_printer_options("Creality", "0.4", "Creality", printer_preset);
    INFO(chosen.message);
    REQUIRE(chosen.status == orca::SceneStatus::success);
    REQUIRE_FALSE(chosen.filament_presets.empty());
    REQUIRE_FALSE(chosen.process_presets.empty());
    CHECK(chosen.max_print_height > 0);
    CHECK(chosen.printable_area.size() >= 8);

    orca::CreatePrinterRequest request;
    request.model = "Orcinus Test Printer";
    request.nozzle = "0.4";
    request.printable_area = {0, 0, 200, 0, 200, 200, 0, 200};
    request.max_print_height = 200;
    request.preset_vendor = "Creality";
    request.printer_preset = printer_preset;
    request.filament_presets = {chosen.filament_presets.front()};
    request.process_presets = {chosen.process_presets.front()};

    // A printer of that name from an earlier run is overwritten, as answering
    // the dialog's question does.
    const orca::DialogAnswers answers = {
        {"printer_name_exists", true}, {"rewrite_filament_presets", true}, {"rewrite_process_presets", true}};
    const orca::PresetCreation created = orca::create_printer(request, answers);
    INFO(created.message);
    REQUIRE(created.status == orca::SceneStatus::success);
    CHECK_FALSE(created.has_question);
    const std::string printer_name = "Orcinus Test Printer 0.4 nozzle";
    CHECK(created.name == printer_name);

    // The printer is there, and it prints on the plate the dialog was given.
    const orca::PresetState selected = orca::select_preset(orca::PresetChoice::printer, printer_name);
    INFO(selected.message);
    REQUIRE(selected.status == orca::SceneStatus::success);
    CHECK(selected.selection.printer == printer_name);
    const orca::SettingState* area = nullptr;
    const orca::PresetSettings printer_settings = orca::describe_settings(orca::PresetKind::printer, "Basic information", {});
    REQUIRE(printer_settings.status == orca::SceneStatus::success);
    for (const orca::SettingState& state : printer_settings.settings) {
        if (state.id == "printable_height") {
            area = &state;
        }
    }
    REQUIRE(area != nullptr);
    CHECK(area->value == "200");

    // The test leaves the profiles as it found them: the presets it made go
    // first, then the printer they were made for.
    const orca::PresetState state = orca::describe_presets();
    for (const orca::PresetItem& item : state.filaments) {
        if (item.name.find(printer_name) != std::string::npos) {
            orca::delete_filament_preset(item.name, {{"delete_filament_preset", true}});
        }
    }
    for (const orca::PresetItem& item : orca::describe_presets().processes) {
        if (item.name.find(printer_name) != std::string::npos) {
            REQUIRE(orca::select_preset(orca::PresetChoice::process, item.name).status == orca::SceneStatus::success);
            REQUIRE(orca::delete_preset(orca::PresetKind::print, {{"delete_preset", true}}).status == orca::SceneStatus::success);
        }
    }
    REQUIRE(orca::delete_preset(orca::PresetKind::printer, {{"delete_preset", true}}).status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
}

TEST_CASE("A filament of the user's own is created from an installed one", "[Adapter][Settings]")
{
    require_engine();
    const std::string printer = "Creality K2 Plus 0.4 nozzle";
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, printer).status == orca::SceneStatus::success);

    // The dialog opens with the vendors and the types it offers.
    const orca::CreateFilamentOptions opened = orca::create_filament_options({}, {});
    INFO(opened.message);
    REQUIRE(opened.status == orca::SceneStatus::success);
    CHECK(std::find(opened.vendors.begin(), opened.vendors.end(), "Creality") != opened.vendors.end());
    CHECK(std::find(opened.types.begin(), opened.types.end(), "PLA") != opened.types.end());
    CHECK(opened.base_filaments.empty());

    // With a type chosen it offers the filaments of that type and their presets.
    const orca::CreateFilamentOptions of_type = orca::create_filament_options("PLA", {});
    REQUIRE(of_type.status == orca::SceneStatus::success);
    REQUIRE_FALSE(of_type.base_filaments.empty());
    REQUIRE_FALSE(of_type.copy_presets.empty());
    const std::string base = of_type.base_filaments.front();

    const orca::CreateFilamentOptions chosen = orca::create_filament_options("PLA", base);
    REQUIRE(chosen.status == orca::SceneStatus::success);
    REQUIRE_FALSE(chosen.presets.empty());
    const auto for_printer = std::find_if(chosen.presets.begin(), chosen.presets.end(),
                                          [&printer](const orca::FilamentPresetChoice& choice) { return choice.printer == printer; });
    REQUIRE(for_printer != chosen.presets.end());

    orca::CreateFilamentRequest request;
    request.vendor = "Creality";
    request.type = "PLA";
    request.serial = "Orcinus test";
    request.presets = {*for_printer};

    const orca::PresetCreation created = orca::create_filament(request, {});
    INFO(created.message);
    REQUIRE(created.status == orca::SceneStatus::success);
    CHECK_FALSE(created.has_question);
    CHECK(created.name == "Creality PLA Orcinus test");

    // The filament is there, made for the printer that was chosen.
    const std::string preset_name = created.name + " @" + printer;
    const orca::PresetState state = orca::describe_presets();
    CHECK(std::any_of(state.filaments.begin(), state.filaments.end(),
                      [&preset_name](const orca::PresetItem& item) { return item.name == preset_name; }));

    // A serial that is missing is refused, as the dialog refuses it.
    orca::CreateFilamentRequest empty_serial = request;
    empty_serial.serial.clear();
    const orca::PresetCreation refused = orca::create_filament(empty_serial, {});
    CHECK(refused.status != orca::SceneStatus::success);
    CHECK(refused.message == "Filament serial is not entered, please enter serial.");

    // A filament of the same name asks before it is created again.
    const orca::PresetCreation asked = orca::create_filament(request, {});
    REQUIRE(asked.status == orca::SceneStatus::success);
    CHECK(asked.has_question);
    CHECK(asked.question.id == "filament_name_exists");

    // The test leaves the profiles as it found them.
    REQUIRE(orca::select_preset(orca::PresetChoice::filament, preset_name).status == orca::SceneStatus::success);
    REQUIRE(orca::delete_preset(orca::PresetKind::filament, {{"delete_preset", true}}).status == orca::SceneStatus::success);
}

TEST_CASE("An import asks before it replaces a preset that is already there", "[Adapter][Settings]")
{
    require_engine();
    const std::string printer = "Creality K2 Plus 0.4 nozzle";
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, printer).status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::process, "0.20mm Standard @Creality K2 Plus 0.4 nozzle",
                                orca::PresetChangeAction::discard)
                .status == orca::SceneStatus::success);
    const auto print = orca::PresetKind::print;
    const std::string name = "Orcinus import ask";
    const std::string directory = (fs::path(device_dir) / "tmp" / "imports").string();
    fs::remove_all(directory);

    REQUIRE(orca::change_setting(print, "Quality", "layer_height", "0.14", {}).dirty);
    REQUIRE(orca::save_preset(print, name).status == orca::SceneStatus::success);
    const orca::ConfigTransfer exported = orca::export_configs(orca::ConfigExportKind::process_presets, {printer}, directory);
    REQUIRE(exported.status == orca::SceneStatus::success);
    REQUIRE(exported.names.size() == 1);

    // The preset is still there, so the import stops and asks about it
    // (ConfigsOverwriteConfirmDialog).
    const orca::ConfigTransfer asked = orca::import_presets({exported.names.front()});
    INFO(asked.message);
    REQUIRE(asked.status == orca::SceneStatus::success);
    CHECK(asked.overwrite_preset == name);

    // With the answer the import goes through and asks nothing more.
    const orca::ConfigTransfer imported = orca::import_presets({exported.names.front()}, {{name, orca::ConfigOverwriteAnswer::yes}});
    INFO(imported.message);
    REQUIRE(imported.status == orca::SceneStatus::success);
    CHECK(imported.overwrite_preset.empty());

    // The test leaves the profiles as it found them.
    REQUIRE(orca::select_preset(orca::PresetChoice::process, name, orca::PresetChangeAction::discard).status == orca::SceneStatus::success);
    REQUIRE(orca::delete_preset(print, {{"delete_preset", true}}).status == orca::SceneStatus::success);
    fs::remove_all(directory);
}

TEST_CASE("A printer is exported with the presets it prints with", "[Adapter][Settings]")
{
    require_engine();
    const std::string printer = "Creality K2 Plus 0.4 nozzle";
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, printer).status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::process, "0.20mm Standard @Creality K2 Plus 0.4 nozzle",
                                orca::PresetChangeAction::discard)
                .status == orca::SceneStatus::success);
    const auto print = orca::PresetKind::print;
    const std::string name = "Orcinus exported bundle";
    const std::string directory = (fs::path(device_dir) / "tmp" / "bundles").string();
    fs::remove_all(directory);

    REQUIRE(orca::change_setting(print, "Quality", "layer_height", "0.14", {}).dirty);
    REQUIRE(orca::save_preset(print, name).status == orca::SceneStatus::success);

    // The printer carries the user's process preset, so the dialog lists it.
    const orca::ConfigExportOptions options = orca::config_export_options(orca::ConfigExportKind::printer_bundle);
    INFO(options.message);
    REQUIRE(options.status == orca::SceneStatus::success);
    const auto listed = std::find_if(options.entries.begin(), options.entries.end(),
                                     [&printer](const orca::ConfigExportEntry& entry) { return entry.name == printer; });
    REQUIRE(listed != options.entries.end());
    // The printer preset itself and the presets that came with it.
    CHECK(listed->count >= 2);

    const orca::ConfigTransfer exported = orca::export_configs(orca::ConfigExportKind::printer_bundle, {printer}, directory);
    INFO(exported.message);
    REQUIRE(exported.status == orca::SceneStatus::success);
    REQUIRE(exported.names.size() == 1);
    CHECK(fs::path(exported.names.front()).filename().string() == printer + ".orca_printer");
    REQUIRE(fs::exists(exported.names.front()));

    // The bundle holds the printer, the process preset and the file that
    // describes it (archive_preset_bundle_to_file).
    std::set<std::string> entries;
    mz_zip_archive zip_archive;
    mz_zip_zero_struct(&zip_archive);
    REQUIRE(mz_zip_reader_init_file(&zip_archive, exported.names.front().c_str(), 0) == MZ_TRUE);
    for (mz_uint index = 0; index < mz_zip_reader_get_num_files(&zip_archive); ++index) {
        mz_zip_archive_file_stat file_stat;
        if (mz_zip_reader_file_stat(&zip_archive, index, &file_stat) == MZ_TRUE) {
            entries.insert(file_stat.m_filename);
        }
    }
    mz_zip_reader_end(&zip_archive);
    CHECK(entries.count("bundle_structure.json") == 1);
    CHECK(entries.count("process/" + name + ".json") == 1);
    CHECK(std::any_of(entries.begin(), entries.end(),
                      [](const std::string& entry) { return entry.rfind("printer/", 0) == 0; }));

    // The test leaves the profiles as it found them.
    REQUIRE(orca::delete_preset(print, {{"delete_preset", true}}).status == orca::SceneStatus::success);
    fs::remove_all(directory);
}

TEST_CASE("Two presets are compared as the diff dialog compares them", "[Adapter][Settings]")
{
    require_engine();
    const std::string printer = "Creality K2 Plus 0.4 nozzle";
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, printer).status == orca::SceneStatus::success);
    const std::string standard = "0.20mm Standard @Creality K2 Plus 0.4 nozzle";
    const std::string fine = "0.12mm Detail @Creality K2 Plus 0.4 nozzle";
    REQUIRE(orca::select_preset(orca::PresetChoice::process, standard, orca::PresetChangeAction::discard).status == orca::SceneStatus::success);

    const auto row = [](const orca::PresetComparison& diff, const orca::PresetKind kind) {
        const auto found = std::find_if(diff.kinds.begin(), diff.kinds.end(),
                                        [kind](const orca::PresetKindComparison& one) { return one.kind == kind; });
        REQUIRE(found != diff.kinds.end());
        return *found;
    };

    SECTION("the dialog opens with the presets the app has selected, a row for every kind")
    {
        const orca::PresetComparison diff = orca::compare_presets({}, {}, false);
        INFO(diff.message);
        REQUIRE(diff.status == orca::SceneStatus::success);
        REQUIRE(diff.kinds.size() == 3);
        // create_presets_sizer(): the printer, filament and process rows.
        CHECK(diff.kinds[0].kind == orca::PresetKind::printer);
        CHECK(diff.kinds[1].kind == orca::PresetKind::filament);
        CHECK(diff.kinds[2].kind == orca::PresetKind::print);
        for (const orca::PresetKindComparison& kind : diff.kinds) {
            CHECK(kind.left == kind.right);
            CHECK(kind.changes.empty());
            CHECK(kind.problem.empty());
        }
        CHECK(diff.kinds[0].left == printer);
        CHECK(diff.kinds[2].left == standard);
        // The combo box marks the preset it selects.
        const orca::PresetKindComparison prints = diff.kinds[2];
        const auto selected = std::find_if(prints.left_presets.begin(), prints.left_presets.end(),
                                           [](const orca::PresetItem& item) { return item.selected; });
        REQUIRE(selected != prints.left_presets.end());
        CHECK(selected->name == standard);
    }

    SECTION("the settings two process presets differ in carry both values")
    {
        const orca::PresetComparison compared = orca::compare_presets({printer, standard, {}}, {printer, fine, {}}, false);
        INFO(compared.message);
        REQUIRE(compared.status == orca::SceneStatus::success);
        const orca::PresetKindComparison diff = row(compared, orca::PresetKind::print);
        CHECK(diff.left == standard);
        CHECK(diff.right == fine);
        REQUIRE_FALSE(diff.changes.empty());
        CHECK(row(compared, orca::PresetKind::printer).changes.empty());

        const auto height = std::find_if(diff.changes.begin(), diff.changes.end(),
                                         [](const orca::PresetChange& change) { return change.id == "layer_height"; });
        REQUIRE(height != diff.changes.end());
        // The values of the two presets, in the order they were asked for.
        REQUIRE_FALSE(height->old_value.empty());
        REQUIRE_FALSE(height->new_value.empty());
        // A value crosses the boundary as a text with the value as its argument.
        REQUIRE_FALSE(height->old_value.front().args.empty());
        CHECK(height->old_value.front().args.front() == "0.2");
        REQUIRE_FALSE(height->new_value.front().args.empty());
        CHECK(height->new_value.front().args.front() == "0.12");
        // Where the setting sits, as the dialog groups its rows.
        REQUIRE_FALSE(height->category.empty());
        CHECK(height->category.front().msgid == "Quality");
    }

    SECTION("a combo box that does not list a preset selects its first one")
    {
        const orca::PresetComparison compared = orca::compare_presets({}, {printer, "no such preset", {}}, false);
        REQUIRE(compared.status == orca::SceneStatus::success);
        const orca::PresetKindComparison diff = row(compared, orca::PresetKind::print);
        REQUIRE_FALSE(diff.right_presets.empty());
        CHECK(diff.right == diff.right_presets.front().name);
        CHECK(diff.right_presets.front().selected);
    }

    SECTION("the incompatible presets are listed on request")
    {
        const orca::PresetComparison compatible = orca::compare_presets({}, {}, false);
        const orca::PresetComparison all = orca::compare_presets({}, {}, true);
        REQUIRE(all.status == orca::SceneStatus::success);
        CHECK(row(all, orca::PresetKind::print).left_presets.size() > row(compatible, orca::PresetKind::print).left_presets.size());
        CHECK(row(all, orca::PresetKind::filament).left_presets.size() > row(compatible, orca::PresetKind::filament).left_presets.size());
        // Not the printers, which the dialog lists the same either way.
        CHECK(row(all, orca::PresetKind::printer).left_presets.size() == row(compatible, orca::PresetKind::printer).left_presets.size());
    }

    SECTION("the dialog tells which preset the app edits, and whether it was modified")
    {
        REQUIRE(orca::change_setting(orca::PresetKind::print, "Quality", "layer_height", "0.16", {}).dirty);
        const orca::PresetComparison compared = orca::compare_presets({}, {}, false);
        const orca::PresetKindComparison diff = row(compared, orca::PresetKind::print);
        CHECK(diff.edited == standard);
        CHECK(diff.edited_dirty);
        // The saved values are compared, not the unsaved ones.
        CHECK(diff.changes.empty());
        REQUIRE(orca::select_preset(orca::PresetChoice::process, standard, orca::PresetChangeAction::discard).status == orca::SceneStatus::success);
    }
}

TEST_CASE("The diff dialog transfers the chosen values from the left preset to the right one", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    const std::string standard = "0.20mm Standard @Creality K2 Plus 0.4 nozzle";
    const std::string fine = "0.12mm Detail @Creality K2 Plus 0.4 nozzle";
    const auto print = orca::PresetKind::print;
    REQUIRE(orca::select_preset(orca::PresetChoice::process, standard, orca::PresetChangeAction::discard).status == orca::SceneStatus::success);

    const auto value = [print](const std::string& id) {
        const orca::PresetSettings settings = orca::describe_settings(print, "Quality", {});
        REQUIRE(settings.status == orca::SceneStatus::success);
        const auto found = std::find_if(settings.settings.begin(), settings.settings.end(),
                                        [&id](const orca::SettingState& state) { return state.id == id; });
        REQUIRE(found != settings.settings.end());
        return found->value;
    };

    SECTION("into the preset the app edits")
    {
        const orca::PresetState state = orca::transfer_preset_options(print, fine, standard, {"layer_height"});
        INFO(state.message);
        REQUIRE(state.status == orca::SceneStatus::success);
        CHECK(state.selection.process == standard);
        CHECK(value("layer_height") == "0.12");
        const orca::PresetSettings settings = orca::describe_settings(print, "Quality", {});
        CHECK(settings.preset == standard);
        CHECK(settings.dirty);
        // Only the chosen setting moved: it is the one unsaved change.
        const orca::PresetState leaving = orca::select_preset(orca::PresetChoice::process, fine);
        REQUIRE(leaving.asks_unsaved_changes);
        REQUIRE(leaving.unsaved_changes.size() == 1);
        CHECK(leaving.unsaved_changes.front().id == "layer_height");
    }

    SECTION("into another preset, which the app selects with the values as unsaved changes")
    {
        const orca::PresetState state = orca::transfer_preset_options(print, standard, fine, {"layer_height"});
        INFO(state.message);
        REQUIRE(state.status == orca::SceneStatus::success);
        CHECK(state.selection.process == fine);
        const orca::PresetSettings settings = orca::describe_settings(print, "Quality", {});
        CHECK(settings.preset == fine);
        CHECK(settings.dirty);
        CHECK(value("layer_height") == "0.2");
    }

    SECTION("not away from a preset with unsaved changes")
    {
        REQUIRE(orca::change_setting(print, "Quality", "layer_height", "0.16", {}).dirty);
        const orca::PresetState state = orca::transfer_preset_options(print, standard, fine, {"layer_height"});
        CHECK(state.status != orca::SceneStatus::success);
        CHECK(state.message == "You can only transfer to current active profile because it has been modified.");
        CHECK(value("layer_height") == "0.16");
    }

    // The test leaves the profiles as it found them.
    REQUIRE(orca::select_preset(orca::PresetChoice::process, standard, orca::PresetChangeAction::discard).status == orca::SceneStatus::success);
}

TEST_CASE("The search catalogue holds every setting of the preset tabs", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);

    const orca::SearchCatalog catalog = orca::search_catalog();
    INFO(catalog.message);
    REQUIRE(catalog.status == orca::SceneStatus::success);
    // The process tab alone shows a few hundred settings.
    CHECK(catalog.options.size() > 300);

    const auto find = [&catalog](const std::string& key) {
        return std::find_if(catalog.options.begin(), catalog.options.end(),
                            [&key](const orca::SearchOption& option) { return option.key == key; });
    };

    const auto walls = find("wall_loops");
    REQUIRE(walls != catalog.options.end());
    CHECK(walls->kind == orca::PresetKind::print);
    REQUIRE_FALSE(walls->page.empty());
    CHECK(walls->page.front().msgid == "Strength");
    REQUIRE_FALSE(walls->group.empty());
    CHECK(walls->group.front().msgid == "Walls");
    REQUIRE_FALSE(walls->label.empty());
    CHECK(walls->label.front().msgid == "Wall loops");
    CHECK(walls->mode == orca::SettingsMode::simple);

    // Every tab is in the catalogue, and a setting of the printer keeps the
    // index its field is shown under.
    CHECK(find("filament_type") != catalog.options.end());
    CHECK(find("filament_type")->kind == orca::PresetKind::filament);
    const auto nozzle = find("nozzle_diameter");
    REQUIRE(nozzle != catalog.options.end());
    CHECK(nozzle->kind == orca::PresetKind::printer);
    CHECK(nozzle->id == "nozzle_diameter#0");
}

TEST_CASE("The G-code editor lists the placeholders the desktop dialog lists", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    const auto printer = orca::PresetKind::printer;
    const std::string gcode_page = "Machine G-code";

    SECTION("the printer's custom G-codes offer the dialog, the other fields do not")
    {
        const orca::PresetSettings settings = orca::describe_settings(printer, gcode_page, {});
        INFO(settings.message);
        REQUIRE(settings.status == orca::SceneStatus::success);
        std::map<std::string, bool> editable;
        for (const orca::SettingsPage& page : settings.pages)
            for (const orca::SettingsGroup& group : page.groups)
                for (const orca::SettingsLine& line : group.lines)
                    for (const orca::SettingsLineOption& option : line.options)
                        editable[option.id] = option.edit_custom_gcode;
        CHECK(editable.at("machine_start_gcode"));
        CHECK(editable.at("change_filament_gcode"));
        CHECK(editable.at("template_custom_gcode"));
        CHECK_FALSE(editable.at("printer_notes"));
        CHECK_FALSE(editable.at("printable_height"));
    }

    SECTION("the groups come in the dialog's order, with the placeholders of the G-code expanded")
    {
        const orca::GcodePlaceholders placeholders = orca::describe_gcode_placeholders(printer, "change_filament_gcode");
        INFO(placeholders.message);
        REQUIRE(placeholders.status == orca::SceneStatus::success);
        const std::vector<orca::GcodePlaceholder>& nodes = placeholders.placeholders;

        std::vector<std::string> groups;
        for (const orca::GcodePlaceholder& node : nodes)
            if (node.parent < 0) {
                REQUIRE_FALSE(node.label.empty());
                groups.push_back(node.label.front().msgid);
            }
        CHECK(groups == std::vector<std::string>{"[Global] Slicing State", "Slicing State", "Print Statistics", "Objects Info", "Dimensions",
                                                 "Temperatures", "Timestamps", "Specific for %1%", "Presets"});

        const auto index_of = [&nodes](const std::function<bool(const orca::GcodePlaceholder&)>& match) {
            const auto found = std::find_if(nodes.begin(), nodes.end(), match);
            REQUIRE(found != nodes.end());
            return std::int32_t(found - nodes.begin());
        };
        const auto named = [](const std::string& msgid) {
            return [msgid](const orca::GcodePlaceholder& node) { return !node.label.empty() && node.label.front().msgid == msgid; };
        };
        const auto param = [](const std::string& key, const std::int32_t parent) {
            return [key, parent](const orca::GcodePlaceholder& node) { return node.key == key && node.parent == parent; };
        };

        // Specific for change_filament_gcode, without the placeholder the
        // parser only zeroes (fan_speed is coNone).
        const std::int32_t specific = index_of(named("Specific for %1%"));
        CHECK(nodes[specific].expanded);
        CHECK(nodes[specific].label.front().args == std::vector<std::string>{"change_filament_gcode"});
        const orca::GcodePlaceholder& next_extruder = nodes[index_of(param("next_extruder", specific))];
        CHECK(next_extruder.type == orca::GcodePlaceholderType::scalar);
        CHECK(next_extruder.text == "next_extruder");
        CHECK(next_extruder.icon == "custom-gcode_single");
        CHECK(std::none_of(nodes.begin(), nodes.end(), [](const orca::GcodePlaceholder& node) { return node.key == "fan_speed"; }));

        // [Global] Slicing State > Read Only > zhop
        const std::int32_t global = index_of(named("[Global] Slicing State"));
        CHECK_FALSE(nodes[global].expanded);
        const std::int32_t read_only = index_of(named("Read Only"));
        CHECK(nodes[read_only].parent == global);
        CHECK(nodes[read_only].icon == "lock_closed");
        index_of(param("zhop", read_only));

        // A vector placeholder is written with its brackets.
        const std::int32_t temperatures = index_of(named("Temperatures"));
        const orca::GcodePlaceholder& bed = nodes[index_of(param("bed_temperature", temperatures))];
        CHECK(bed.type == orca::GcodePlaceholderType::vector);
        CHECK(bed.text == "bed_temperature[]");

        // Presets: a subgroup per preset tab, and in it a subgroup per page.
        const std::int32_t presets = index_of(named("Presets"));
        const std::int32_t print_settings = index_of(named("Print settings"));
        const std::int32_t printer_settings = index_of(named("Printer settings"));
        CHECK(nodes[print_settings].parent == presets);
        CHECK(nodes[index_of(named("Filament settings"))].parent == presets);
        CHECK(nodes[printer_settings].parent == presets);
        const std::int32_t strength = index_of([print_settings](const orca::GcodePlaceholder& node) {
            return node.parent == print_settings && !node.label.empty() && node.label.front().msgid == "Strength";
        });
        CHECK(nodes[index_of(param("wall_loops", strength))].type == orca::GcodePlaceholderType::scalar);
        const std::int32_t machine_gcode = index_of([printer_settings](const orca::GcodePlaceholder& node) {
            return node.parent == printer_settings && !node.label.empty() && node.label.front().msgid == "Machine G-code";
        });
        CHECK(nodes[machine_gcode].icon == "custom-gcode_gcode");
        index_of(param("machine_start_gcode", machine_gcode));
        // The extruder page shows nozzle_diameter#0, which is no key of the
        // config, so the setting is listed under the tab itself.
        const orca::GcodePlaceholder& nozzle = nodes[index_of(param("nozzle_diameter", printer_settings))];
        CHECK(nozzle.text == "nozzle_diameter[]");
        // The other placeholders of the presets follow the three tabs.
        CHECK(index_of(param("print_preset", presets)) > printer_settings);
    }

    SECTION("a placeholder is described by its definition")
    {
        const orca::GcodePlaceholderInfo layer_z = orca::describe_gcode_placeholder("layer_z", false);
        REQUIRE(layer_z.label.size() == 1);
        CHECK(layer_z.label.front().msgid == "Layer Z");
        CHECK(layer_z.type == "float");
        REQUIRE(layer_z.description.size() == 1);
        CHECK(layer_z.description.front().msgid == "Height of the current layer above the print bed, measured to the top of the layer.");

        const orca::GcodePlaceholderInfo nozzle = orca::describe_gcode_placeholder("nozzle_diameter", true);
        CHECK(nozzle.type == "float[]");
        CHECK_FALSE(nozzle.undefined);

        CHECK(orca::describe_gcode_placeholder("no_such_placeholder", false).undefined);
    }

    SECTION("the dialog opens with the tab's G-code, and OK writes it without the field's checks")
    {
        const orca::GcodePlaceholders placeholders = orca::describe_gcode_placeholders(printer, "machine_start_gcode");
        REQUIRE(placeholders.status == orca::SceneStatus::success);
        const orca::PresetSettings saved = orca::describe_settings(printer, gcode_page, {});
        const auto value = [](const orca::PresetSettings& settings, const std::string& id) {
            const auto state = std::find_if(settings.settings.begin(), settings.settings.end(),
                                            [&id](const orca::SettingState& candidate) { return candidate.id == id; });
            REQUIRE(state != settings.settings.end());
            return state->value;
        };
        CHECK(placeholders.value == value(saved, "machine_start_gcode"));

        const std::string edited = ";_GP_LAST_LINE_M73_PLACEHOLDER\nG28\nM190 S[bed_temperature_initial_layer_single]\n";
        const orca::PresetSettings changed = orca::edit_custom_gcode(printer, gcode_page, "machine_start_gcode", edited, {});
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        CHECK(value(changed, "machine_start_gcode") == edited);
        CHECK(changed.dirty);
        // Tab::edit_custom_gcode() does not validate the G-code as the field does.
        CHECK(changed.notices.empty());
        CHECK_FALSE(orca::reset_settings(printer, gcode_page, {}, {}).dirty);
    }

    SECTION("a filament's G-code is its first value")
    {
        const orca::GcodePlaceholders placeholders = orca::describe_gcode_placeholders(orca::PresetKind::filament, "filament_start_gcode");
        REQUIRE(placeholders.status == orca::SceneStatus::success);
        const orca::PresetSettings changed = orca::edit_custom_gcode(orca::PresetKind::filament, "Advanced", "filament_start_gcode", "; start\n", {});
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        CHECK(changed.dirty);
        CHECK_FALSE(orca::reset_settings(orca::PresetKind::filament, "Advanced", {}, {}).dirty);
    }
}

TEST_CASE("The plate the 3D view draws follows the shape the dialog sets", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::filament, k2_plus_profiles().filament).status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::process, k2_plus_profiles().process).status == orca::SceneStatus::success);

    REQUIRE(orca::set_bed_shape(orca::BedShapeKind::rectangle, 200.0, 180.0, 0.0, 0.0, 0.0, {}, {}, {}, {}).status == orca::SceneStatus::success);
    CHECK(orca::describe_bed_shape().size_x == Catch::Approx(200.0));

    // The app asks for the presets before it describes the plate again
    // (SetBedShapeUseCase), which must not drop the unsaved shape.
    CHECK(orca::describe_presets().status == orca::SceneStatus::success);
    CHECK(orca::describe_bed_shape().size_x == Catch::Approx(200.0));

    const orca::PlateDescription plate = orca::describe_plate(k2_plus_profiles(), (fs::path(device_dir) / "tmp" / "shape").string());
    INFO(plate.message);
    REQUIRE(plate.status == orca::SceneStatus::success);
    double max_x = 0.0;
    double max_y = 0.0;
    for (std::size_t i = 0; i + 1 < plate.printable_area.size(); i += 2) {
        max_x = std::max(max_x, plate.printable_area[i]);
        max_y = std::max(max_y, plate.printable_area[i + 1]);
    }
    CHECK(max_x == Catch::Approx(200.0));
    CHECK(max_y == Catch::Approx(180.0));
    CHECK(orca::describe_bed_shape().size_x == Catch::Approx(200.0));

    // The Reset Options button of the tab, so the next test starts with the
    // printer as it is installed.
    REQUIRE(orca::reset_settings(orca::PresetKind::printer, {}, {}, {}, {}).status == orca::SceneStatus::success);
    CHECK(orca::describe_bed_shape().size_x == Catch::Approx(350.0));
}

TEST_CASE("The shape of the plate is described and set as its dialog does", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);

    SECTION("a rectangular plate is described by its size and origin")
    {
        const orca::BedShapeState shape = orca::describe_bed_shape();
        INFO(shape.message);
        REQUIRE(shape.status == orca::SceneStatus::success);
        CHECK(shape.kind == orca::BedShapeKind::rectangle);
        CHECK(shape.size_x == Catch::Approx(350.0));
        CHECK(shape.size_y == Catch::Approx(350.0));
        CHECK(shape.origin_x == Catch::Approx(0.0));
        CHECK(shape.origin_y == Catch::Approx(0.0));
        CHECK(shape.points.size() == 8);
    }

    SECTION("a size of its own reaches the printer preset")
    {
        const orca::PresetSettings changed = orca::set_bed_shape(orca::BedShapeKind::rectangle, 200.0, 180.0, 10.0, 0.0, 0.0, {}, {}, {}, {});
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        CHECK(changed.kind == orca::PresetKind::printer);
        // The preset now differs from the installed one.
        CHECK(changed.dirty);

        const orca::BedShapeState shape = orca::describe_bed_shape();
        REQUIRE(shape.status == orca::SceneStatus::success);
        CHECK(shape.kind == orca::BedShapeKind::rectangle);
        CHECK(shape.size_x == Catch::Approx(200.0));
        CHECK(shape.size_y == Catch::Approx(180.0));
        // BedShapePanel::update_shape() moves the rectangle by the origin.
        CHECK(shape.origin_x == Catch::Approx(10.0));
        CHECK(shape.origin_y == Catch::Approx(0.0));
    }

    SECTION("a round plate is described by its diameter")
    {
        const orca::PresetSettings changed = orca::set_bed_shape(orca::BedShapeKind::circle, 0.0, 0.0, 0.0, 0.0, 250.0, {}, {}, {}, {});
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);

        const orca::BedShapeState shape = orca::describe_bed_shape();
        REQUIRE(shape.status == orca::SceneStatus::success);
        CHECK(shape.kind == orca::BedShapeKind::circle);
        CHECK(shape.diameter == Catch::Approx(250.0).margin(0.5));
        // BedShapePanel::update_shape() draws a circle as 72 edges.
        CHECK(shape.points.size() == 144);
    }

    SECTION("a shape without a size is refused")
    {
        const orca::PresetSettings changed = orca::set_bed_shape(orca::BedShapeKind::rectangle, 0.0, 0.0, 0.0, 0.0, 0.0, {}, {}, {}, {});
        CHECK(changed.status != orca::SceneStatus::success);
    }

    // Every section leaves the printer as it is installed.
    REQUIRE(orca::reset_settings(orca::PresetKind::printer, {}, {}, {}, {}).status == orca::SceneStatus::success);
}

TEST_CASE("The settings of a height range sit on the ones of its object and always hold a layer height", "[Adapter][Settings]")
{
    require_engine();
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::process, "0.20mm Standard @Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
    const auto layer = orca::PresetKind::layer;
    const std::string frequent = "Frequent";

    const auto setting = [](const orca::PresetSettings& settings, const std::string& id) -> const orca::SettingState& {
        const auto state = std::find_if(settings.settings.begin(), settings.settings.end(),
                                        [&id](const orca::SettingState& candidate) { return candidate.id == id; });
        INFO(id);
        REQUIRE(state != settings.settings.end());
        return *state;
    };
    const auto model_settings = [](const std::vector<std::pair<std::string, std::string>>& overrides) {
        orca::ModelSettings settings;
        for (const auto& [key, value] : overrides) {
            settings.keys.push_back(key);
            settings.values.push_back(value);
        }
        return settings;
    };
    const auto overridden = [](const orca::ModelSettings& settings, const std::string& key) {
        const auto found = std::find(settings.keys.begin(), settings.keys.end(), key);
        return found == settings.keys.end() ? std::string("<unset>") : settings.values[std::size_t(found - settings.keys.begin())];
    };

    SECTION("a range that carries no layer height takes the one of its object")
    {
        // TabPrintLayer::notify_changed(): the range is given the layer height
        // the object prints with, which is what the app keeps with it.
        orca::ModelSettingsRequest request;
        request.settings = {orca::ModelSettings{}};
        request.parent = model_settings({{"layer_height", "0.28"}});

        const orca::PresetSettings settings = orca::describe_settings(layer, frequent, {}, request);
        INFO(settings.message);
        REQUIRE(settings.status == orca::SceneStatus::success);
        CHECK(settings.kind == layer);
        REQUIRE_FALSE(settings.model_settings.empty());
        CHECK(overridden(settings.model_settings.front(), "layer_height") == "0.28");
        CHECK(setting(settings, "layer_height").value == "0.28");
        // A range whose height is the one of the object changes nothing, so the
        // desktop app does not mark it (TabPrintLayer::update_custom_dirty).
        CHECK_FALSE(setting(settings, "layer_height").modified);
    }

    SECTION("a layer height of its own is an override of the range")
    {
        orca::ModelSettingsRequest request;
        request.settings = {model_settings({{"layer_height", "0.2"}})};

        const orca::PresetSettings changed = orca::change_setting(layer, frequent, "layer_height", "0.1", {}, request);
        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        REQUIRE_FALSE(changed.model_settings.empty());
        CHECK(overridden(changed.model_settings.front(), "layer_height") == "0.1");
        CHECK(setting(changed, "layer_height").modified);
    }
}

TEST_CASE("The filament tab offers the extruder variants of a printer that has several", "[Adapter][Settings]")
{
    require_engine();
    // A dual-nozzle printer: its filaments carry more than one extruder
    // variant, which is when the desktop shows Tab::m_variant_combo.
    const orca::PresetState added = orca::apply_setup({"Creality K2 Plus", "Bambu Lab H2D"}, {"Generic PLA @K2 Plus-all"});
    INFO(added.message);
    REQUIRE(added.status == orca::SceneStatus::success);

    const orca::PresetSettings filament = orca::describe_settings(orca::PresetKind::filament, {}, {});
    INFO(filament.message + " | filament " + filament.preset);
    REQUIRE(filament.status == orca::SceneStatus::success);
    INFO("variants: " + std::to_string(filament.variants.size()));
    REQUIRE(filament.variants.size() > 1);
    CHECK(filament.variant == 0);
    // generate_extruder_options(): "<drive>: <nozzle>".
    CHECK(filament.variants.front().find(": ") != std::string::npos);

    const orca::PresetSettings switched =
        orca::set_settings_variant(orca::PresetKind::filament, filament.active_page, 1, {});
    INFO(switched.message);
    REQUIRE(switched.status == orca::SceneStatus::success);
    CHECK(switched.variant == 1);
    CHECK(switched.variants == filament.variants);

    // The tab keeps the variant until it is switched back.
    const orca::PresetSettings again = orca::describe_settings(orca::PresetKind::filament, {}, {});
    CHECK(again.variant == 1);
    CHECK(orca::set_settings_variant(orca::PresetKind::filament, filament.active_page, 0, {}).variant == 0);

    // Back to the printer the other tests work with.
    REQUIRE(orca::apply_setup({"Creality K2 Plus"}, {"Generic PLA @K2 Plus-all"}).status == orca::SceneStatus::success);
    REQUIRE(orca::select_preset(orca::PresetChoice::printer, "Creality K2 Plus 0.4 nozzle").status == orca::SceneStatus::success);
}

namespace {

void write_text(const std::string& path, const std::string& text)
{
    fs::create_directories(fs::path(path).parent_path());
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    out << text;
}

// A cube as an OBJ file, from its_make_cube(), with its corner at the origin.
std::string cube_obj(const double size)
{
    std::ostringstream obj;
    const double s = size;
    const double vertices[8][3] = {{s, s, 0}, {s, 0, 0}, {0, 0, 0}, {0, s, 0}, {s, s, s}, {0, s, s}, {0, 0, s}, {s, 0, s}};
    const int faces[12][3] = {{0, 1, 2}, {0, 2, 3}, {4, 5, 6}, {4, 6, 7}, {0, 4, 7}, {0, 7, 1},
                              {1, 7, 6}, {1, 6, 2}, {2, 6, 5}, {2, 5, 3}, {4, 0, 3}, {4, 3, 5}};
    for (const auto& vertex : vertices) {
        obj << "v " << vertex[0] << ' ' << vertex[1] << ' ' << vertex[2] << '\n';
    }
    for (const auto& face : faces) {
        obj << "f " << face[0] + 1 << ' ' << face[1] + 1 << ' ' << face[2] + 1 << '\n';
    }
    return obj.str();
}

// Two 10 mm cubes as AMF objects, the second one lifted 20 mm by the
// constellation, as a file that stacks objects at several heights holds them.
std::string stacked_cubes_amf()
{
    const double s = 10;
    const double vertices[8][3] = {{s, s, 0}, {s, 0, 0}, {0, 0, 0}, {0, s, 0}, {s, s, s}, {0, s, s}, {0, 0, s}, {s, 0, s}};
    const int faces[12][3] = {{0, 1, 2}, {0, 2, 3}, {4, 5, 6}, {4, 6, 7}, {0, 4, 7}, {0, 7, 1},
                              {1, 7, 6}, {1, 6, 2}, {2, 6, 5}, {2, 5, 3}, {4, 0, 3}, {4, 3, 5}};
    std::ostringstream amf;
    amf << "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<amf unit=\"millimeter\">\n";
    for (int object = 0; object < 2; ++object) {
        amf << "<object id=\"" << object << "\"><mesh><vertices>\n";
        for (const auto& vertex : vertices) {
            amf << "<vertex><coordinates><x>" << vertex[0] << "</x><y>" << vertex[1] << "</y><z>" << vertex[2]
                << "</z></coordinates></vertex>\n";
        }
        amf << "</vertices><volume>\n";
        for (const auto& face : faces) {
            amf << "<triangle><v1>" << face[0] << "</v1><v2>" << face[1] << "</v2><v3>" << face[2] << "</v3></triangle>\n";
        }
        amf << "</volume></mesh></object>\n";
    }
    amf << "<constellation id=\"1\">\n"
        << "<instance objectid=\"0\"><deltax>0</deltax><deltay>0</deltay><deltaz>0</deltaz></instance>\n"
        << "<instance objectid=\"1\"><deltax>0</deltax><deltay>0</deltay><deltaz>20</deltaz></instance>\n"
        << "</constellation>\n</amf>\n";
    return amf.str();
}

// A STEP file with two bodies, each a shape of its own: a 20 mm box, and a
// 10 mm box beside it that reaches 5 mm lower.
void write_two_box_step(const std::string& path)
{
    fs::create_directories(fs::path(path).parent_path());
    STEPControl_Writer writer;
    writer.Transfer(BRepPrimAPI_MakeBox(gp_Pnt(0, 0, 0), 20, 20, 20).Shape(), STEPControl_AsIs);
    writer.Transfer(BRepPrimAPI_MakeBox(gp_Pnt(30, 0, -5), 10, 10, 10).Shape(), STEPControl_AsIs);
    REQUIRE(writer.Write(path.c_str()) == IFSelect_RetDone);
}

// An imported object as the app keeps it on the plate.
orca::PlateObject plate_object_of(const orca::ImportedObject& imported)
{
    orca::PlateObject object;
    object.model_path = imported.model_path;
    object.matrix = imported.matrix;
    object.settings = imported.settings;
    object.volume_settings = imported.volume_settings;
    object.name = imported.name;
    object.volume_name = imported.volume_name;
    object.painted = imported.painted;
    object.volume_from_inches = imported.volume_from_inches;
    object.volume_from_meters = imported.volume_from_meters;
    object.volume_input_file = imported.volume_input_file;
    object.layer_ranges = imported.layer_ranges;
    for (const orca::ImportedPart& part : imported.parts) {
        orca::ObjectPart& added = object.parts.emplace_back();
        added.model_path = part.model_path;
        added.name = part.name;
        added.type = part.type;
        added.matrix = part.matrix;
        added.settings = part.settings;
        added.painted = part.painted;
        added.from_inches = part.from_inches;
        added.from_meters = part.from_meters;
        added.input_file = part.input_file;
    }
    for (std::size_t index = 0; index < imported.instances.size(); ++index) {
        orca::ObjectPlacement& instance = object.instances.emplace_back();
        instance.matrix.assign(imported.instances[index].instance_matrix.begin(), imported.instances[index].instance_matrix.end());
        instance.auto_drop = index < imported.auto_drops.size() ? bool(imported.auto_drops[index]) : true;
        instance.printable = index < imported.printables.size() ? bool(imported.printables[index]) : true;
    }
    return object;
}

// An OBJ of two separate 10 mm cubes side by side: one mesh of two shells.
std::string two_cube_obj()
{
    std::ostringstream obj;
    const double s = 10;
    const double vertices[8][3] = {{s, s, 0}, {s, 0, 0}, {0, 0, 0}, {0, s, 0}, {s, s, s}, {0, s, s}, {0, 0, s}, {s, 0, s}};
    const int faces[12][3] = {{0, 1, 2}, {0, 2, 3}, {4, 5, 6}, {4, 6, 7}, {0, 4, 7}, {0, 7, 1},
                              {1, 7, 6}, {1, 6, 2}, {2, 6, 5}, {2, 5, 3}, {4, 0, 3}, {4, 3, 5}};
    for (int cube = 0; cube < 2; ++cube) {
        for (const auto& vertex : vertices) {
            obj << "v " << vertex[0] + cube * 20 << ' ' << vertex[1] << ' ' << vertex[2] << '\n';
        }
    }
    for (int cube = 0; cube < 2; ++cube) {
        for (const auto& face : faces) {
            obj << "f " << face[0] + 1 + cube * 8 << ' ' << face[1] + 1 + cube * 8 << ' ' << face[2] + 1 + cube * 8 << '\n';
        }
    }
    return obj.str();
}

std::string import_prefix(const std::string& name)
{
    const fs::path directory = fs::path(device_dir) / "tmp" / "import";
    fs::create_directories(directory);
    return (directory / name).string();
}

}  // namespace

TEST_CASE("A model file of another type than STL is imported as the desktop app loads it", "[Adapter][Import]")
{
    require_engine();

    SECTION("an OBJ file")
    {
        const orca::ImportedModels imported =
            orca::import_model(device_dir + "/data/20mm_cube.obj", k2_plus_profiles(), {}, import_prefix("obj"), {});
        INFO(imported.message);
        REQUIRE(imported.status == orca::SceneStatus::success);
        REQUIRE_FALSE(imported.has_question);
        REQUIRE(imported.objects.size() == 1);
        const orca::ImportedObject& object = imported.objects.front();
        // An object without a name of its own takes the file's.
        CHECK(object.name == "20mm_cube.obj");
        CHECK(object.parts.empty());
        CHECK(object.instances.front().size_x == Catch::Approx(20.0));
        CHECK(object.instances.front().size_z == Catch::Approx(20.0));
        // load_model_objects(): an object alone on the plate stands at its centre.
        CHECK(object.instances.front().instance_matrix[12] == Catch::Approx(175.0));
        CHECK(object.instances.front().instance_matrix[13] == Catch::Approx(175.0));
        CHECK(fs::exists(object.model_path));
        CHECK(fs::exists(object.mesh_path));

        // The object slices from the mesh the import wrote.
        const orca::SliceResult sliced = orca::slice("import-obj", {plate_object_of(object)}, output_path("import-obj.gcode"), {},
                                                     k2_plus_profiles(), {}, {});
        INFO(sliced.message);
        REQUIRE(sliced.status == orca::SliceStatus::success);
        CHECK(sliced.layer_count == 100);

        // The same file again goes to the empty cell nearest to the centre.
        const orca::ImportedModels again = orca::import_model(device_dir + "/data/20mm_cube.obj", k2_plus_profiles(),
                                                              {plate_object_of(object)}, import_prefix("obj-again"), {});
        INFO(again.message);
        REQUIRE(again.objects.size() == 1);
        const auto& beside = again.objects.front().instances.front().instance_matrix;
        CHECK(std::hypot(beside[12] - 175.0, beside[13] - 175.0) >= 10.0);
    }

    SECTION("a Draco file, which the desktop app's handy models are")
    {
        const orca::ImportedModels imported = orca::import_model(device_dir + "/orca/resources/handy_models/OrcaCube_v2.drc",
                                                                 k2_plus_profiles(), {}, import_prefix("drc"), {});
        INFO(imported.message);
        REQUIRE(imported.status == orca::SceneStatus::success);
        REQUIRE(imported.objects.size() == 1);
        CHECK(imported.objects.front().instances.front().size_z > 0.0);
    }

    SECTION("an SVG file, extruded as the desktop app loads one")
    {
        const std::string svg = device_dir + "/tmp/import/square.svg";
        write_text(svg, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"20mm\" height=\"20mm\" viewBox=\"0 0 20 20\">"
                        "<path d=\"M0 0 L20 0 L20 20 L0 20 Z\" fill=\"#000000\"/></svg>");
        const orca::ImportedModels imported = orca::import_model(svg, k2_plus_profiles(), {}, import_prefix("svg"), {});
        INFO(imported.message);
        REQUIRE(imported.status == orca::SceneStatus::success);
        REQUIRE(imported.objects.size() == 1);
        CHECK(imported.objects.front().instances.front().size_x > 0.0);
    }

    SECTION("a file that does not exist")
    {
        const orca::ImportedModels imported =
            orca::import_model(device_dir + "/data/missing.obj", k2_plus_profiles(), {}, import_prefix("missing"), {});
        CHECK(imported.status != orca::SceneStatus::success);
        CHECK(imported.objects.empty());
    }
}

TEST_CASE("A STEP file with several bodies becomes one object with a part for each further body", "[Adapter][Import]")
{
    require_engine();
    const std::string step = device_dir + "/tmp/import/two-boxes.step";
    write_two_box_step(step);

    const orca::ImportedModels imported = orca::import_model(step, k2_plus_profiles(), {}, import_prefix("step"), {});
    INFO(imported.message);
    REQUIRE(imported.status == orca::SceneStatus::success);
    REQUIRE(imported.objects.size() == 1);
    const orca::ImportedObject& object = imported.objects.front();
    // Step::mesh(): one object, a volume per body.
    REQUIRE(object.parts.size() == 1);
    CHECK(object.parts.front().type == orca::VolumeType::part);
    // ModelObject::facets_count(), which the object's info shows: both boxes.
    CHECK(object.instances.front().facet_count == 24);
    CHECK(fs::exists(object.parts.front().model_path));
    // Both bodies count: 40 mm wide, 25 mm high with the lower box.
    CHECK(object.instances.front().size_x == Catch::Approx(40.0));
    CHECK(object.instances.front().size_z == Catch::Approx(25.0));

    const orca::PlateObject on_plate = plate_object_of(object);
    const orca::SliceResult sliced =
        orca::slice("import-step", {on_plate}, output_path("import-step.gcode"), {}, k2_plus_profiles(), {}, {});
    INFO(sliced.message);
    REQUIRE(sliced.status == orca::SliceStatus::success);
    CHECK(sliced.layer_count == 125);

    // A lifted object drops until its lowest body, the part, touches the plate.
    const std::vector<double> placed(object.instances.front().instance_matrix.begin(), object.instances.front().instance_matrix.end());
    std::vector<double> lifted = placed;
    lifted[14] += 30.0;
    const orca::ModelInspection dropped =
        orca::place_model(on_plate, k2_plus_profiles(), placed, lifted, true, orca::Manipulation::move, {});
    INFO(dropped.message);
    REQUIRE(dropped.status == orca::SceneStatus::success);
    CHECK(dropped.instance_matrix[14] == Catch::Approx(placed[14]));

    // The faces it can lie on come from both bodies.
    const orca::FlatteningPlanes faces = orca::describe_flattening_planes(on_plate, k2_plus_profiles(), placed);
    INFO(faces.message);
    REQUIRE(faces.status == orca::SceneStatus::success);
    CHECK_FALSE(faces.planes.empty());
}

TEST_CASE("Objects a file stacks at several heights can be loaded as one object with parts", "[Adapter][Import]")
{
    require_engine();
    const std::string amf = device_dir + "/tmp/import/stacked.amf";
    write_text(amf, stacked_cubes_amf());

    // Plater::priv::load_files() asks before it loads anything.
    const orca::ImportedModels asked = orca::import_model(amf, k2_plus_profiles(), {}, import_prefix("amf"), {});
    INFO(asked.message);
    REQUIRE(asked.status == orca::SceneStatus::success);
    REQUIRE(asked.has_question);
    CHECK(asked.question.id == "multipart_object");
    CHECK(asked.objects.empty());

    SECTION("yes: one object with a part")
    {
        const orca::ImportedModels merged =
            orca::import_model(amf, k2_plus_profiles(), {}, import_prefix("amf-yes"), {{"multipart_object", true}});
        INFO(merged.message);
        REQUIRE(merged.status == orca::SceneStatus::success);
        REQUIRE_FALSE(merged.has_question);
        REQUIRE(merged.objects.size() == 1);
        const orca::ImportedObject& object = merged.objects.front();
        // Model::convert_multipart_object() names the object after the file.
        CHECK(object.name == "stacked");
        // convert_multipart_object() names every volume after the object it was.
        CHECK(object.volume_name == "stacked.amf");
        CHECK(object.parts.front().name == "stacked.amf");
        CHECK(object.parts.size() == 1);
        // The two cubes keep their heights: 30 mm from the lower to the upper one.
        CHECK(object.instances.front().size_z == Catch::Approx(30.0));
        // The object has no place from the file, so it stands in the centre.
        CHECK(object.instances.size() == 1);
        CHECK(object.instances.front().instance_matrix[12] == Catch::Approx(175.0));
        CHECK(object.instances.front().instance_matrix[13] == Catch::Approx(175.0));
    }

    SECTION("no: two objects where the file puts them")
    {
        const orca::ImportedModels apart =
            orca::import_model(amf, k2_plus_profiles(), {}, import_prefix("amf-no"), {{"multipart_object", false}});
        INFO(apart.message);
        REQUIRE(apart.status == orca::SceneStatus::success);
        REQUIRE(apart.objects.size() == 2);
        // load_model_objects() keeps the instances of the file's constellation,
        // and the upper cube drops onto the plate. ModelObject::rotate() by the
        // preferred orientation centred each mesh around its instance, which
        // then stands half a cube high.
        for (const orca::ImportedObject& object : apart.objects) {
            CHECK(object.parts.empty());
            REQUIRE(object.instances.size() == 1);
            CHECK(object.instances.front().instance_matrix[12] == Catch::Approx(0.0));
            CHECK(object.instances.front().instance_matrix[13] == Catch::Approx(0.0));
            CHECK(object.instances.front().instance_matrix[14] == Catch::Approx(5.0));
            CHECK(object.instances.front().size_z == Catch::Approx(10.0));
        }
    }
}

TEST_CASE("Split to objects makes every body an object where it stood", "[Adapter][Edit]")
{
    require_engine();
    const std::string step = device_dir + "/tmp/import/split-boxes.step";
    write_two_box_step(step);
    const orca::ImportedModels imported = orca::import_model(step, k2_plus_profiles(), {}, import_prefix("split-step"), {});
    REQUIRE(imported.objects.size() == 1);
    const std::vector<orca::PlateObject> plate = {plate_object_of(imported.objects.front())};

    // The lower box rests on the plate, so the bigger one floats once it is
    // an object of its own: Plater::priv::split_object() asks first.
    const orca::ImportedModels asked =
        orca::edit_object(plate, 0, orca::ObjectEdit::split_to_objects, -1, k2_plus_profiles(), import_prefix("split-asked"), {});
    INFO(asked.message);
    REQUIRE(asked.status == orca::SceneStatus::success);
    REQUIRE(asked.has_question);
    CHECK(asked.question.id == "split_auto_drop");

    SECTION("keeping auto drop: both rest on the plate")
    {
        const orca::ImportedModels split = orca::edit_object(plate, 0, orca::ObjectEdit::split_to_objects, -1, k2_plus_profiles(),
                                                             import_prefix("split-drop"), {{"split_auto_drop", false}});
        INFO(split.message);
        REQUIRE(split.status == orca::SceneStatus::success);
        CHECK(split.appended);
        REQUIRE(split.objects.size() == 2);
        // ModelObject::split(): an object of one body is named after the body.
        CHECK(split.objects[0].name == imported.objects.front().volume_name);
        CHECK(split.objects[1].name == imported.objects.front().parts.front().name);
        for (const orca::ImportedObject& object : split.objects) {
            CHECK(object.parts.empty());
            const orca::ModelInspection& copy = object.instances.front();
            CHECK(copy.box_center[2] - copy.size_z / 2 == Catch::Approx(0.0).margin(1e-6));
            CHECK(object.auto_drops.front());
        }
    }

    SECTION("without auto drop: the bigger box stays where it was")
    {
        const orca::ImportedModels split = orca::edit_object(plate, 0, orca::ObjectEdit::split_to_objects, -1, k2_plus_profiles(),
                                                             import_prefix("split-keep"), {{"split_auto_drop", true}});
        REQUIRE(split.objects.size() == 2);
        double highest_bottom = 0.0;
        for (const orca::ImportedObject& object : split.objects) {
            const orca::ModelInspection& copy = object.instances.front();
            highest_bottom = std::max(highest_bottom, copy.box_center[2] - copy.size_z / 2);
            CHECK_FALSE(object.auto_drops.front());
        }
        CHECK(highest_bottom == Catch::Approx(5.0));
    }
}

TEST_CASE("An object of one shell cannot be split", "[Adapter][Edit]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("unsplit.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    const orca::ImportedModels split = orca::edit_object(plate_of({}, matrix_of(cube)), 0, orca::ObjectEdit::split_to_objects, -1,
                                                         k2_plus_profiles(), import_prefix("unsplit"), {});
    REQUIRE(split.status == orca::SceneStatus::success);
    CHECK(split.objects.empty());
    REQUIRE(split.notices.size() == 1);
    CHECK(split.notices.front().id == "split_failed");
}

TEST_CASE("Split to parts makes every shell of a mesh a part of the object", "[Adapter][Edit]")
{
    require_engine();
    const std::string obj = device_dir + "/tmp/import/two-cubes.obj";
    write_text(obj, two_cube_obj());
    const orca::ImportedModels imported = orca::import_model(obj, k2_plus_profiles(), {}, import_prefix("two-cubes"), {});
    INFO(imported.message);
    REQUIRE(imported.objects.size() == 1);
    // ModelVolume::is_splittable(): one mesh of two shells.
    CHECK(imported.objects.front().volume_splittable);
    CHECK(imported.objects.front().parts.empty());

    const orca::ImportedModels split = orca::edit_object({plate_object_of(imported.objects.front())}, 0, orca::ObjectEdit::split_to_parts,
                                                         -1, k2_plus_profiles(), import_prefix("two-cubes-parts"), {});
    INFO(split.message);
    REQUIRE(split.status == orca::SceneStatus::success);
    CHECK_FALSE(split.appended);
    REQUIRE(split.objects.size() == 1);
    const orca::ImportedObject& object = split.objects.front();
    REQUIRE(object.parts.size() == 1);
    CHECK_FALSE(object.volume_splittable);
    // The volume was read from the file, which "Replace all with 3D files"
    // looks for; ModelVolume::split() forgets it, so reload cannot undo the split.
    CHECK(fs::path(imported.objects.front().volume_input_file).filename() == "two-cubes.obj");
    CHECK(object.volume_input_file.empty());
    CHECK(object.parts.front().input_file.empty());
    // The object stays where it was, 30 mm wide.
    CHECK(object.instances.front().size_x == Catch::Approx(30.0));
    CHECK(object.instances.front().instance_matrix[12] == Catch::Approx(imported.objects.front().instances.front().instance_matrix[12]));
}

TEST_CASE("Fix model closes an open mesh with CGAL", "[Adapter][Edit]")
{
    require_engine();
    // A 20 mm cube without its top: 10 triangles and a square hole.
    std::string open = cube_obj(20.0);
    const std::string top = "f 5 6 7\nf 5 7 8\n";
    REQUIRE(open.find(top) != std::string::npos);
    open.replace(open.find(top), top.size(), "");
    const std::string obj = device_dir + "/tmp/import/open-cube.obj";
    write_text(obj, open);
    const orca::ImportedModels imported = orca::import_model(obj, k2_plus_profiles(), {}, import_prefix("open-cube"), {});
    INFO(imported.message);
    REQUIRE(imported.objects.size() == 1);
    CHECK(imported.objects.front().instances.front().facet_count == 10);
    // The four edges around the hole, which keep Subdivision mesh off.
    CHECK(imported.objects.front().instances.front().open_edges == 4);

    const orca::ImportedModels fixed = orca::edit_object({plate_object_of(imported.objects.front())}, 0, orca::ObjectEdit::fix, -1,
                                                         k2_plus_profiles(), import_prefix("open-cube-fixed"), {});
    INFO(fixed.message);
    REQUIRE(fixed.status == orca::SceneStatus::success);
    REQUIRE(fixed.objects.size() == 1);
    CHECK(fixed.objects.front().instances.front().facet_count >= 12);
    CHECK(fixed.objects.front().instances.front().open_edges == 0);
    // The CgalFinished notification names what it repaired.
    REQUIRE(fixed.notices.size() == 1);
    CHECK(fixed.notices.front().id == "fix_finished");
    CHECK(fixed.notices.front().text.front().msgid == "Following model object has been repaired");
}

// A generated cylinder, a tenth of the bed wide, made 7 mm wide and moved into
// the middle of a 20 mm cube, through its top.
orca::ObjectPart through_hole(orca::ObjectPart part)
{
    part.matrix = {0.2, 0.0, 0.0, 0.0, 0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0};
    return part;
}

TEST_CASE("Subdivision mesh gives every triangle four, and the object rests on the plate", "[Adapter][Edit]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("smooth.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    CHECK(cube.open_edges == 0);

    const orca::ImportedModels smoothed = orca::edit_object(plate_of({}, matrix_of(cube)), 0, orca::ObjectEdit::smooth_mesh, -1,
                                                            k2_plus_profiles(), import_prefix("smoothed"), {});
    INFO(smoothed.message);
    REQUIRE(smoothed.status == orca::SceneStatus::success);
    CHECK_FALSE(smoothed.appended);
    REQUIRE(smoothed.objects.size() == 1);
    const orca::ModelInspection& copy = smoothed.objects.front().instances.front();
    CHECK(copy.facet_count == 48);
    CHECK(copy.box_center[2] == Catch::Approx(copy.size_z / 2).margin(1e-4));
}

TEST_CASE("Mesh boolean makes one mesh of the object and its copies, its negative volumes taken out", "[Adapter][Edit]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("boolean.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));
    const orca::ModelInspection added =
        orca::add_object_part(plate.front(), "Cylinder", orca::VolumeType::negative, k2_plus_profiles(), output_path("boolean-hole.mesh"));
    REQUIRE(added.status == orca::SceneStatus::success);
    orca::ObjectPart hole;
    hole.shape = "Cylinder";
    hole.type = orca::VolumeType::negative;
    hole.matrix.assign(added.instance_matrix.begin(), added.instance_matrix.end());
    plate.front().parts.push_back(through_hole(hole));
    // A second copy 40 mm to the right.
    plate.front().instances.push_back(plate.front().instances.front());
    plate.front().instances[1].matrix[12] += 40.0;

    const orca::ImportedModels merged = orca::edit_object(plate, 0, orca::ObjectEdit::mesh_boolean, -1, k2_plus_profiles(),
                                                          import_prefix("merged"), {});
    INFO(merged.message);
    REQUIRE(merged.status == orca::SceneStatus::success);
    CHECK(merged.appended);
    CHECK(merged.notices.empty());
    REQUIRE(merged.objects.size() == 1);
    const orca::ImportedObject& object = merged.objects.front();
    CHECK(object.parts.empty());
    REQUIRE(object.instances.size() == 1);
    const orca::ModelInspection& copy = object.instances.front();
    // Both cubes, holed.
    CHECK(copy.size_x == Catch::Approx(60.0).margin(1e-3));
    CHECK(copy.facet_count > 24);
    CHECK(copy.box_center[0] == Catch::Approx(cube.instance_matrix[12] + 20.0).margin(1e-3));
    CHECK(copy.box_center[2] == Catch::Approx(10.0).margin(1e-3));
}

TEST_CASE("Change type makes a part a modifier, sorted after the solid parts; the last solid part keeps its type", "[Adapter][Edit]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("type.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));
    const orca::ModelInspection added =
        orca::add_object_part(plate.front(), "Cube", orca::VolumeType::negative, k2_plus_profiles(), output_path("type-negative.mesh"));
    REQUIRE(added.status == orca::SceneStatus::success);
    orca::ObjectPart negative;
    negative.shape = "Cube";
    negative.type = orca::VolumeType::negative;
    negative.matrix.assign(added.instance_matrix.begin(), added.instance_matrix.end());
    plate.front().parts.push_back(negative);

    SECTION("the negative volume becomes a part: the solid parts come first")
    {
        const orca::ImportedModels changed = orca::set_volume_type(plate, 0, 1, orca::VolumeType::part, k2_plus_profiles(), import_prefix("type-part"));

        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        REQUIRE(changed.objects.size() == 1);
        REQUIRE(changed.objects.front().parts.size() == 1);
        CHECK(changed.objects.front().parts.front().type == orca::VolumeType::part);
        CHECK(changed.selected_volume == 1);
    }
    SECTION("the cube itself becomes a modifier: the other volume is the object's own mesh now")
    {
        std::vector<orca::PlateObject> two = plate;
        two.front().parts.front().type = orca::VolumeType::part;
        const orca::ImportedModels changed = orca::set_volume_type(two, 0, 0, orca::VolumeType::modifier, k2_plus_profiles(), import_prefix("type-modifier"));

        INFO(changed.message);
        REQUIRE(changed.status == orca::SceneStatus::success);
        REQUIRE(changed.objects.size() == 1);
        REQUIRE(changed.objects.front().parts.size() == 1);
        CHECK(changed.objects.front().parts.front().type == orca::VolumeType::modifier);
        CHECK(changed.selected_volume == 1);
    }
    SECTION("the last solid part stays one, and the message box says so")
    {
        const orca::ImportedModels refused = orca::set_volume_type(plate, 0, 0, orca::VolumeType::modifier, k2_plus_profiles(), import_prefix("type-refused"));

        REQUIRE(refused.status == orca::SceneStatus::success);
        CHECK(refused.objects.empty());
        REQUIRE(refused.notices.size() == 1);
        CHECK(refused.notices.front().id == "last_solid_part");
    }
}

TEST_CASE("Simplify decimates a volume for its preview, and Apply gives the volume that mesh", "[Adapter][Edit]")
{
    require_engine();
    const orca::ImportedModels sphere = orca::add_primitive({}, "Sphere", "Sphere", k2_plus_profiles(), import_prefix("simplify-sphere"));
    INFO(sphere.message);
    REQUIRE(sphere.status == orca::SceneStatus::success);
    const std::vector<orca::PlateObject> plate = {plate_object_of(sphere.objects.front())};
    const std::int64_t original = sphere.objects.front().instances.front().facet_count;
    REQUIRE(original > 100);

    SECTION("with the decimate ratio: half of the triangles taken away, the count worked out from the ratio")
    {
        orca::SimplifyConfig config;
        config.use_count = true;
        config.decimate_ratio = 50.f;
        const std::string path = output_path("simplify-preview.mesh");
        const orca::SimplifiedVolume preview = orca::simplify_volume(plate, 0, 0, config, k2_plus_profiles(), path);

        INFO(preview.message);
        REQUIRE(preview.status == orca::SceneStatus::success);
        CHECK(preview.original_count == original);
        CHECK(preview.triangle_count <= (original + 1) / 2);
        CHECK(preview.triangle_count > original / 4);
        CHECK(fs::file_size(path) > 16);
    }
    SECTION("with a detail level: only edges within the error collapse")
    {
        orca::SimplifyConfig config;
        config.max_error = 1.f;
        const orca::SimplifiedVolume preview = orca::simplify_volume(plate, 0, 0, config, k2_plus_profiles(), output_path("simplify-error.mesh"));

        INFO(preview.message);
        REQUIRE(preview.status == orca::SceneStatus::success);
        CHECK(preview.triangle_count < original);
    }
    SECTION("apply: the object's mesh is the decimated one, on the plate")
    {
        orca::SimplifyConfig config;
        config.use_count = true;
        config.wanted_count = 100;
        const orca::ImportedModels applied = orca::apply_simplify(plate, 0, 0, config, k2_plus_profiles(), import_prefix("simplified"));

        INFO(applied.message);
        REQUIRE(applied.status == orca::SceneStatus::success);
        REQUIRE(applied.objects.size() == 1);
        const orca::ModelInspection& copy = applied.objects.front().instances.front();
        CHECK(copy.facet_count <= 100);
        CHECK(copy.box_center[2] == Catch::Approx(copy.size_z / 2).margin(1e-3));
    }
    SECTION("a volume the object does not have is refused")
    {
        CHECK(orca::simplify_volume(plate, 0, 3, {}, k2_plus_profiles(), output_path("simplify-none.mesh")).status != orca::SceneStatus::success);
    }
}

TEST_CASE("Convert from inches scales an object, and Restore to inches scales it back", "[Adapter][Edit]")
{
    require_engine();
    const std::string obj = device_dir + "/tmp/import/inch-cube.obj";
    write_text(obj, cube_obj(1.0));
    // A 1 mm cube looks like inches; the load leaves it as it is.
    const orca::ImportedModels imported =
        orca::import_model(obj, k2_plus_profiles(), {}, import_prefix("inch-cube"), {{"model_in_inches", false}, {"model_in_meters", false}});
    INFO(imported.message);
    REQUIRE(imported.objects.size() == 1);
    CHECK(imported.objects.front().instances.front().size_x == Catch::Approx(1.0));
    CHECK_FALSE(imported.objects.front().volume_from_inches);

    const orca::ImportedModels converted = orca::edit_object({plate_object_of(imported.objects.front())}, 0,
                                                             orca::ObjectEdit::convert_from_inches, -1, k2_plus_profiles(),
                                                             import_prefix("inch-cube-mm"), {});
    INFO(converted.message);
    REQUIRE(converted.objects.size() == 1);
    CHECK(converted.appended);
    CHECK(converted.objects.front().instances.front().size_x == Catch::Approx(25.4));
    CHECK(converted.objects.front().volume_from_inches);
    // The converted volume keeps the file it was read from, through the plate and back.
    CHECK(fs::path(converted.objects.front().volume_input_file).filename() == "inch-cube.obj");

    const orca::ImportedModels restored = orca::edit_object({plate_object_of(converted.objects.front())}, 0,
                                                            orca::ObjectEdit::restore_to_inches, -1, k2_plus_profiles(),
                                                            import_prefix("inch-cube-back"), {});
    REQUIRE(restored.objects.size() == 1);
    CHECK(restored.objects.front().instances.front().size_x == Catch::Approx(1.0));
    CHECK_FALSE(restored.objects.front().volume_from_inches);
}

TEST_CASE("A model that looks like metres offers to be scaled to millimetres", "[Adapter][Import]")
{
    require_engine();
    const std::string obj = device_dir + "/tmp/import/tiny.obj";
    write_text(obj, cube_obj(0.02));

    const orca::ImportedModels asked = orca::import_model(obj, k2_plus_profiles(), {}, import_prefix("tiny"), {});
    INFO(asked.message);
    REQUIRE(asked.has_question);
    CHECK(asked.question.id == "model_in_meters");

    const orca::ImportedModels scaled =
        orca::import_model(obj, k2_plus_profiles(), {}, import_prefix("tiny-yes"), {{"model_in_meters", true}});
    INFO(scaled.message);
    REQUIRE(scaled.status == orca::SceneStatus::success);
    REQUIRE(scaled.objects.size() == 1);
    CHECK(scaled.objects.front().instances.front().size_x == Catch::Approx(20.0));

    const orca::ImportedModels kept =
        orca::import_model(obj, k2_plus_profiles(), {}, import_prefix("tiny-no"), {{"model_in_meters", false}});
    REQUIRE(kept.objects.size() == 1);
    CHECK(kept.objects.front().instances.front().size_x == Catch::Approx(0.02));
}

TEST_CASE("The object menu mirrors a copy, centres it and drops it as the desktop app does", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("menu.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    const std::vector<double> placed = matrix_of(cube);

    SECTION("mirror along X: about the centre of its bounding box, still on the plate")
    {
        const orca::ModelInspection mirrored =
            orca::place_model({}, k2_plus_profiles(), placed, placed, true, orca::Manipulation::mirror_x, {});
        INFO(mirrored.message);
        REQUIRE(mirrored.status == orca::SceneStatus::success);
        CHECK(mirrored.instance_matrix[0] == Catch::Approx(-1.0));
        CHECK(mirrored.instance_matrix[5] == Catch::Approx(1.0));
        CHECK(mirrored.instance_matrix[12] == Catch::Approx(placed[12]));
        CHECK(mirrored.instance_matrix[14] == Catch::Approx(placed[14]));
    }

    SECTION("mirror along Z: the upside-down copy rests on the plate again")
    {
        const orca::ModelInspection mirrored =
            orca::place_model({}, k2_plus_profiles(), placed, placed, true, orca::Manipulation::mirror_z, {});
        REQUIRE(mirrored.status == orca::SceneStatus::success);
        CHECK(mirrored.instance_matrix[10] == Catch::Approx(-1.0));
        CHECK(mirrored.box_center[2] == Catch::Approx(10.0));
    }

    SECTION("center: over the centre of the plate, at the same height")
    {
        std::vector<double> aside = placed;
        aside[12] = 60.0;
        aside[13] = 80.0;
        const orca::ModelInspection centred =
            orca::place_model({}, k2_plus_profiles(), aside, aside, true, orca::Manipulation::center, {});
        REQUIRE(centred.status == orca::SceneStatus::success);
        CHECK(centred.box_center[0] == Catch::Approx(175.0));
        CHECK(centred.box_center[1] == Catch::Approx(175.0));
        CHECK(centred.instance_matrix[14] == Catch::Approx(placed[14]));
    }

    SECTION("drop: a copy that does not drop by itself comes down to the plate")
    {
        std::vector<double> lifted = placed;
        lifted[14] += 30.0;
        const orca::ModelInspection dropped =
            orca::place_model({}, k2_plus_profiles(), lifted, lifted, false, orca::Manipulation::drop, {});
        REQUIRE(dropped.status == orca::SceneStatus::success);
        CHECK(dropped.instance_matrix[14] == Catch::Approx(placed[14]));
    }
}

TEST_CASE("Objects are copied, pasted and cloned as the desktop app does", "[Adapter][Copy]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("copy.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    const std::vector<double> centred = matrix_of(cube);
    const std::vector<orca::PlateObject> plate = plate_of({}, centred);

    SECTION("pasted twice: each copy goes into the empty cell nearest to the cube")
    {
        const orca::ImportedModels pasted = orca::copy_objects(plate, plate, 2, orca::CopyPlacement::paste, k2_plus_profiles(), output_path("paste"));

        INFO(pasted.message);
        REQUIRE(pasted.status == orca::SceneStatus::success);
        REQUIRE(pasted.objects.size() == 2);
        REQUIRE(pasted.objects[0].instances.size() == 1);
        // The cells are the cube's size and 1 mm apart; the one under the cube is covered.
        const auto& first = pasted.objects[0].instances.front();
        const auto& second = pasted.objects[1].instances.front();
        CHECK(std::hypot(first.instance_matrix[12] - 175.0, first.instance_matrix[13] - 175.0) == Catch::Approx(21.0));
        CHECK(std::hypot(second.instance_matrix[12] - 175.0, second.instance_matrix[13] - 175.0) == Catch::Approx(21.0));
        CHECK(std::hypot(first.instance_matrix[12] - second.instance_matrix[12], first.instance_matrix[13] - second.instance_matrix[13]) > 20.0);
        CHECK(first.instance_matrix[14] == Catch::Approx(10.0));
        CHECK(first.volume_state == orca::VolumeState::inside);
        CHECK(!pasted.objects[0].mesh_path.empty());
        CHECK(pasted.objects[0].model_path != pasted.objects[1].model_path);
    }
    SECTION("kept: the copy of the second copy stands where that copy does")
    {
        std::vector<orca::PlateObject> two = plate;
        two[0].instances.push_back(two[0].instances.front());
        two[0].instances[1].matrix[12] = 100.0;
        std::vector<orca::PlateObject> sources = two;
        sources[0].instances.erase(sources[0].instances.begin());

        const orca::ImportedModels kept = orca::copy_objects(two, sources, 1, orca::CopyPlacement::keep, k2_plus_profiles(), output_path("keep"));

        INFO(kept.message);
        REQUIRE(kept.status == orca::SceneStatus::success);
        REQUIRE(kept.objects.size() == 1);
        REQUIRE(kept.objects[0].instances.size() == 1);
        CHECK(kept.objects[0].instances[0].instance_matrix[12] == Catch::Approx(100.0));
        CHECK(kept.objects[0].instances[0].instance_matrix[13] == Catch::Approx(175.0));
    }
    SECTION("several objects pasted keep their layout")
    {
        std::vector<orca::PlateObject> two = plate;
        two.push_back(two.front());
        two[0].instances[0].matrix[12] = 100.0;
        two[1].instances[0].matrix[12] = 150.0;

        const orca::ImportedModels pasted = orca::copy_objects(two, two, 1, orca::CopyPlacement::paste, k2_plus_profiles(), output_path("layout"));

        INFO(pasted.message);
        REQUIRE(pasted.status == orca::SceneStatus::success);
        REQUIRE(pasted.objects.size() == 2);
        const auto& a = pasted.objects[0].instances.front().instance_matrix;
        const auto& b = pasted.objects[1].instances.front().instance_matrix;
        CHECK(b[12] - a[12] == Catch::Approx(50.0));
        CHECK(b[13] == Catch::Approx(a[13]));
        // Wider than deep: the copies move on by the depth of the objects.
        CHECK(a[13] != Catch::Approx(175.0));
    }
    SECTION("nothing to copy is refused")
    {
        CHECK(orca::copy_objects(plate, {}, 1, orca::CopyPlacement::paste, k2_plus_profiles(), output_path("none")).status
              != orca::SceneStatus::success);
    }
    SECTION("a copied modifier joins another cube beside it, or keeps its place in a cube of the same file")
    {
        std::vector<orca::PlateObject> source = plate;
        const orca::ModelInspection added =
            orca::add_object_part(source.front(), "Cube", orca::VolumeType::modifier, k2_plus_profiles(), output_path("clip-part.mesh"));
        REQUIRE(added.status == orca::SceneStatus::success);
        orca::ObjectPart part;
        part.shape = "Cube";
        part.type = orca::VolumeType::modifier;
        part.matrix.assign(added.instance_matrix.begin(), added.instance_matrix.end());
        source.front().parts.push_back(part);
        std::vector<orca::PlateObject> target = plate;
        target[0].instances[0].matrix[12] = 100.0;

        const orca::ImportedModels beside =
            orca::paste_volumes(target, 0, 0, source.front(), {1}, false, k2_plus_profiles(), output_path("pasted-beside"));

        INFO(beside.message);
        REQUIRE(beside.status == orca::SceneStatus::success);
        REQUIRE(beside.objects.size() == 1);
        REQUIRE(beside.objects[0].parts.size() == 1);
        CHECK(beside.objects[0].parts[0].type == orca::VolumeType::modifier);
        CHECK(beside.selected_volume == 1);
        // Beside the right front corner of the cube, whose right side is 10 mm from its centre.
        CHECK(beside.objects[0].parts[0].matrix[12] >= 10.0 - 1e-6);
        CHECK(beside.objects[0].instances.front().instance_matrix[12] == Catch::Approx(100.0));

        const orca::ImportedModels kept =
            orca::paste_volumes(target, 0, 0, source.front(), {1}, true, k2_plus_profiles(), output_path("pasted-kept"));

        REQUIRE(kept.status == orca::SceneStatus::success);
        REQUIRE(kept.objects[0].parts.size() == 1);
        for (std::size_t index = 0; index < 16; ++index) {
            CHECK(kept.objects[0].parts[0].matrix[index] == Catch::Approx(part.matrix[index]).margin(1e-6));
        }
        CHECK(orca::paste_volumes(target, 0, 0, source.front(), {5}, false, k2_plus_profiles(), output_path("pasted-none")).status
              != orca::SceneStatus::success);
    }
}

TEST_CASE("A primitive joins the plate as an object of its own", "[Adapter][Copy]")
{
    require_engine();
    SECTION("a cylinder a tenth of the bed wide, on the empty plate's centre, printing with filament 1")
    {
        const orca::ImportedModels added = orca::add_primitive({}, "Cylinder", "Cylinder", k2_plus_profiles(), output_path("primitive"));

        INFO(added.message);
        REQUIRE(added.status == orca::SceneStatus::success);
        REQUIRE(added.objects.size() == 1);
        const orca::ImportedObject& cylinder = added.objects.front();
        CHECK(cylinder.name == "Cylinder");
        CHECK(cylinder.volume_name == "Cylinder");
        REQUIRE(cylinder.instances.size() == 1);
        // 10% of the K2 Plus's 350 mm bed.
        CHECK(cylinder.instances.front().size_x == Catch::Approx(35.0).margin(0.01));
        CHECK(cylinder.instances.front().size_z == Catch::Approx(35.0).margin(0.01));
        CHECK(cylinder.instances.front().instance_matrix[12] == Catch::Approx(175.0));
        CHECK(cylinder.instances.front().instance_matrix[13] == Catch::Approx(175.0));
        CHECK(cylinder.instances.front().box_center[2] == Catch::Approx(17.5).margin(0.01));
        const auto extruder = std::find(cylinder.settings.keys.begin(), cylinder.settings.keys.end(), "extruder");
        REQUIRE(extruder != cylinder.settings.keys.end());
        CHECK(cylinder.settings.values[extruder - cylinder.settings.keys.begin()] == "1");
    }
    SECTION("an unknown shape is refused")
    {
        CHECK(orca::add_primitive({}, "Pyramid", "Pyramid", k2_plus_profiles(), output_path("pyramid")).status != orca::SceneStatus::success);
    }
}

// The triangles of a binary STL file, as their corners.
std::vector<std::array<float, 9>> stl_triangles(const std::string& path)
{
    const std::string data = read_file(path);
    std::vector<std::array<float, 9>> triangles;
    if (data.size() < 84) {
        return triangles;
    }
    const std::uint32_t count = read_u32(data, 80);
    for (std::uint32_t index = 0; index < count && 84 + (index + 1) * 50 <= data.size(); ++index) {
        std::array<float, 9> corners{};
        std::memcpy(corners.data(), data.data() + 84 + index * 50 + 12, sizeof(float) * 9);
        triangles.push_back(corners);
    }
    return triangles;
}

TEST_CASE("Export as one STL or DRC writes the whole object", "[Adapter][Edit]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("export.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    const std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));

    SECTION("a cube as binary STL: its twelve triangles, 20 mm across")
    {
        const std::string path = output_path("export-cube.stl");
        const orca::MeshExport exported = orca::export_object_mesh(plate, 0, orca::MeshFormat::stl, k2_plus_profiles(), path);

        INFO(exported.message);
        REQUIRE(exported.status == orca::SceneStatus::success);
        CHECK(exported.warning.empty());
        const auto triangles = stl_triangles(path);
        REQUIRE(triangles.size() == 12);
        float min_x = std::numeric_limits<float>::max();
        float max_x = std::numeric_limits<float>::lowest();
        float min_z = std::numeric_limits<float>::max();
        float max_z = std::numeric_limits<float>::lowest();
        for (const auto& corners : triangles) {
            for (int corner = 0; corner < 3; ++corner) {
                min_x = std::min(min_x, corners[corner * 3]);
                max_x = std::max(max_x, corners[corner * 3]);
                min_z = std::min(min_z, corners[corner * 3 + 2]);
                max_z = std::max(max_z, corners[corner * 3 + 2]);
            }
        }
        CHECK(max_x - min_x == Catch::Approx(20.0).margin(1e-4));
        CHECK(max_z - min_z == Catch::Approx(20.0).margin(1e-4));
    }
    SECTION("a negative volume is taken out of the mesh")
    {
        std::vector<orca::PlateObject> holed = plate;
        const orca::ModelInspection added =
            orca::add_object_part(holed.front(), "Cylinder", orca::VolumeType::negative, k2_plus_profiles(), output_path("export-hole.mesh"));
        REQUIRE(added.status == orca::SceneStatus::success);
        orca::ObjectPart part;
        part.shape = "Cylinder";
        part.type = orca::VolumeType::negative;
        part.matrix.assign(added.instance_matrix.begin(), added.instance_matrix.end());
        holed.front().parts.push_back(through_hole(part));

        const std::string path = output_path("export-holed.stl");
        const orca::MeshExport exported = orca::export_object_mesh(holed, 0, orca::MeshFormat::stl, k2_plus_profiles(), path);

        INFO(exported.message);
        REQUIRE(exported.status == orca::SceneStatus::success);
        CHECK(exported.warning.empty());
        CHECK(stl_triangles(path).size() > 12);
    }
    SECTION("Draco: a file of the Draco format")
    {
        const std::string path = output_path("export-cube.drc");
        const orca::MeshExport exported = orca::export_object_mesh(plate, 0, orca::MeshFormat::drc, k2_plus_profiles(), path);

        INFO(exported.message);
        REQUIRE(exported.status == orca::SceneStatus::success);
        CHECK(read_file(path).rfind("DRACO", 0) == 0);
    }
    SECTION("an object the plate does not have is refused")
    {
        CHECK(orca::export_object_mesh(plate, 3, orca::MeshFormat::stl, k2_plus_profiles(), output_path("export-none.stl")).status
              != orca::SceneStatus::success);
    }
}

TEST_CASE("Replace 3D file gives a volume the mesh of another file", "[Adapter][Edit]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("replace.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    const std::vector<orca::PlateObject> plate = plate_of({}, matrix_of(cube));

    SECTION("the cube becomes the 2 x 20 x 10 mm block, the origins of both files together, on the plate")
    {
        const orca::ImportedModels replaced =
            orca::replace_volume(plate, 0, 0, device_dir + "/data/2x20x10.obj", k2_plus_profiles(), import_prefix("replaced"));

        INFO(replaced.message);
        REQUIRE(replaced.status == orca::SceneStatus::success);
        CHECK(replaced.notices.empty());
        REQUIRE(replaced.objects.size() == 1);
        const orca::ImportedObject& object = replaced.objects.front();
        CHECK(object.parts.empty());
        REQUIRE(object.instances.size() == 1);
        CHECK(object.instances.front().size_x == Catch::Approx(2.0).margin(1e-4));
        CHECK(object.instances.front().size_y == Catch::Approx(20.0).margin(1e-4));
        CHECK(object.instances.front().size_z == Catch::Approx(10.0).margin(1e-4));
        CHECK(object.instances.front().box_center[2] == Catch::Approx(5.0).margin(1e-4));
        // The volume moves by the difference of the meshes' offsets
        // (ModelVolume::source.mesh_offset): the block's corner is where the cube's was.
        CHECK(object.instances.front().box_center[0] == Catch::Approx(cube.instance_matrix[12] - 10.0 + 1.0).margin(1e-4));
        CHECK(object.instances.front().box_center[1] == Catch::Approx(cube.instance_matrix[13]).margin(1e-4));
        // The object of one volume takes the name of the new volume.
        CHECK(object.name == object.volume_name);
    }
    SECTION("a file that cannot be read changes nothing")
    {
        const orca::ImportedModels missing =
            orca::replace_volume(plate, 0, 0, device_dir + "/data/missing.obj", k2_plus_profiles(), import_prefix("replace-missing"));
        CHECK(missing.status != orca::SceneStatus::success);
        CHECK(missing.objects.empty());
    }
    SECTION("a volume the object does not have is refused")
    {
        CHECK(orca::replace_volume(plate, 0, 2, device_dir + "/data/2x20x10.obj", k2_plus_profiles(), import_prefix("replace-none")).status
              != orca::SceneStatus::success);
    }
}

// The value of key among settings; empty when they do not set it.
std::string setting_of(const orca::ModelSettings& settings, const std::string& key)
{
    const auto found = std::find(settings.keys.begin(), settings.keys.end(), key);
    return found == settings.keys.end() ? std::string() : settings.values[found - settings.keys.begin()];
}

orca::ModelSettings settings_of(const std::vector<std::pair<std::string, std::string>>& entries)
{
    orca::ModelSettings settings;
    for (const auto& [key, value] : entries) {
        settings.keys.push_back(key);
        settings.values.push_back(value);
    }
    return settings;
}

TEST_CASE("Paste Process Settings gives an item the settings copied from another", "[Adapter][Settings]")
{
    require_engine();
    SECTION("an object takes every copied setting and keeps its filament")
    {
        const orca::PastedSettings pasted = orca::paste_model_settings(
            settings_of({{"layer_height", "0.12"}, {"wall_loops", "4"}, {"extruder", "2"}}),
            settings_of({{"extruder", "3"}, {"sparse_infill_density", "10%"}}),
            false,
            {});

        INFO(pasted.message);
        REQUIRE(pasted.status == orca::SceneStatus::success);
        CHECK(pasted.settings.keys.size() == 3);
        CHECK(setting_of(pasted.settings, "layer_height") == "0.12");
        CHECK(setting_of(pasted.settings, "wall_loops") == "4");
        CHECK(setting_of(pasted.settings, "extruder") == "3");
        CHECK(setting_of(pasted.settings, "sparse_infill_density").empty());
    }
    SECTION("a part takes what differs from its object, none of the object's own options, and keeps its filament")
    {
        const orca::PastedSettings pasted = orca::paste_model_settings(
            settings_of({{"layer_height", "0.11"}, {"wall_loops", "5"}, {"sparse_infill_density", "37%"}}),
            settings_of({{"extruder", "2"}, {"bottom_shell_layers", "9"}}),
            true,
            settings_of({{"wall_loops", "5"}, {"top_shell_layers", "17"}}));

        INFO(pasted.message);
        REQUIRE(pasted.status == orca::SceneStatus::success);
        CHECK(setting_of(pasted.settings, "sparse_infill_density") == "37%");
        // The object has it already.
        CHECK(setting_of(pasted.settings, "wall_loops").empty());
        // An option of the object alone.
        CHECK(setting_of(pasted.settings, "layer_height").empty());
        // What the object sets and the clipboard does not goes back to the process preset.
        CHECK_FALSE(setting_of(pasted.settings, "top_shell_layers").empty());
        CHECK(setting_of(pasted.settings, "top_shell_layers") != "17");
        CHECK(setting_of(pasted.settings, "bottom_shell_layers").empty());
        CHECK(setting_of(pasted.settings, "extruder") == "2");
    }
    SECTION("an item without a filament of its own gets none")
    {
        const orca::PastedSettings pasted =
            orca::paste_model_settings(settings_of({{"wall_loops", "3"}, {"extruder", "2"}}), {}, false, {});
        REQUIRE(pasted.status == orca::SceneStatus::success);
        CHECK(setting_of(pasted.settings, "extruder").empty());
        CHECK(setting_of(pasted.settings, "wall_loops") == "3");
    }
}

TEST_CASE("The menu's arrangement leaves the objects off the plate, and the bed fills with instances", "[Adapter][Copy]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("fill.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    const std::vector<double> centred = matrix_of(cube);

    SECTION("arranging the plate from a menu keeps the cube off it where it is")
    {
        std::vector<orca::PlateObject> plate = plate_of({}, centred);
        plate.push_back(plate.front());
        plate[0].instances[0].matrix[12] = 20.0;
        plate[1].instances[0].matrix[12] = 500.0;

        const orca::PlateInspection menu = orca::place_objects(plate, {}, k2_plus_profiles(), orca::PlateManipulation::arrange_plate, {});
        const orca::PlateInspection all = orca::place_objects(plate, {}, k2_plus_profiles(), orca::PlateManipulation::arrange, {});

        INFO(menu.message);
        REQUIRE(menu.status == orca::SceneStatus::success);
        REQUIRE(all.status == orca::SceneStatus::success);
        CHECK(menu.objects[0].instances[0].instance_matrix[12] == Catch::Approx(175.0).margin(1.0));
        CHECK(menu.objects[1].instances[0].instance_matrix[12] == Catch::Approx(500.0));
        CHECK(menu.objects[1].instances[0].volume_state == orca::VolumeState::outside);
        CHECK(all.objects[1].instances[0].volume_state == orca::VolumeState::inside);
    }
    SECTION("fill bed adds copies of the cube, and none lies across the plate edge")
    {
        const orca::PlateInspection filled =
            orca::place_objects(plate_of({}, centred), {true}, k2_plus_profiles(), orca::PlateManipulation::fill_bed, {}, -1);

        INFO(filled.message);
        REQUIRE(filled.status == orca::SceneStatus::success);
        REQUIRE(filled.objects.size() == 1);
        const auto& copies = filled.objects[0].instances;
        INFO(copies.size() << " copies");
        const auto inside = std::count_if(copies.begin(), copies.end(), [](const orca::ModelInspection& copy) {
            return copy.volume_state == orca::VolumeState::inside;
        });
        CHECK(inside > 50);
        CHECK(std::none_of(copies.begin(), copies.end(), [](const orca::ModelInspection& copy) {
            return copy.volume_state == orca::VolumeState::partly_outside;
        }));
    }
    SECTION("fill bed needs a selected object")
    {
        CHECK(orca::place_objects(plate_of({}, centred), {false}, k2_plus_profiles(), orca::PlateManipulation::fill_bed, {}, -1).status
              != orca::SceneStatus::success);
    }
}

TEST_CASE("An object added to the plate goes to its centre, or to the empty cell nearest to it", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection first = orca::inspect_model({}, k2_plus_profiles(), output_path("first.mesh"), {});
    REQUIRE(first.status == orca::SceneStatus::success);
    std::vector<double> placement = matrix_of(first);

    SECTION("beside a cube in the centre")
    {
        const orca::ModelInspection second = orca::inspect_model({}, k2_plus_profiles(), output_path("second.mesh"), plate_of({}, placement));

        INFO(second.message);
        REQUIRE(second.status == orca::SceneStatus::success);
        // Cells lie 10 mm apart, and one on the first cube's contour is covered:
        // the second cube stands 20 mm from the centre, next to the first.
        CHECK(std::hypot(second.instance_matrix[12] - 175.0, second.instance_matrix[13] - 175.0) == Catch::Approx(20.0));
        CHECK(second.instance_matrix[14] == Catch::Approx(10.0));
        CHECK(second.volume_state == orca::VolumeState::inside);
    }
    SECTION("in the centre, when the cube there was moved away")
    {
        placement[12] = 60.0;
        placement[13] = 60.0;

        const orca::ModelInspection second = orca::inspect_model({}, k2_plus_profiles(), output_path("second.mesh"), plate_of({}, placement));

        REQUIRE(second.status == orca::SceneStatus::success);
        CHECK(second.instance_matrix[12] == Catch::Approx(175.0));
        CHECK(second.instance_matrix[13] == Catch::Approx(175.0));
    }
    SECTION("in the centre, when the other cube is entirely off the plate")
    {
        placement[12] = -100.0;

        const orca::ModelInspection second = orca::inspect_model({}, k2_plus_profiles(), output_path("second.mesh"), plate_of({}, placement));

        REQUIRE(second.status == orca::SceneStatus::success);
        CHECK(second.instance_matrix[12] == Catch::Approx(175.0));
        CHECK(second.instance_matrix[13] == Catch::Approx(175.0));
    }
    SECTION("a plate object that cannot be read fails the inspection")
    {
        CHECK(orca::inspect_model({}, k2_plus_profiles(), output_path("second.mesh"), plate_of(device_dir + "/data/missing.stl", placement)).status
              == orca::SceneStatus::model_read_failed);
    }
}

TEST_CASE("An object over the plate boundary is not sliced", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("outside.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    std::vector<double> placement(cube.instance_matrix.begin(), cube.instance_matrix.end());

    SECTION("across the edge")
    {
        placement[12] = 0.0;
    }
    SECTION("entirely off the plate")
    {
        placement[12] = -100.0;
    }
    const std::string output = output_path("outside.gcode");
    boost::filesystem::remove(output);

    const orca::SliceResult result = orca::slice("outside", plate_of({}, placement), output, {}, k2_plus_profiles(), {}, {});

    CHECK(result.status == orca::SliceStatus::invalid_print);
    CHECK_FALSE(result.message.empty());
    CHECK_FALSE(boost::filesystem::exists(output));
}

TEST_CASE("A manipulated object is placed as the desktop app commits it", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("placed.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    CHECK(cube.volume_state == orca::VolumeState::inside);
    const std::vector<double> centred(cube.instance_matrix.begin(), cube.instance_matrix.end());
    std::vector<double> placement = centred;

    SECTION("lifted above the plate, it drops onto it")
    {
        placement[12] = 100.0;
        placement[13] = 120.0;
        placement[14] = 40.0;

        const orca::ModelInspection placed = orca::place_model({}, k2_plus_profiles(), centred, placement, true, orca::Manipulation::move, {});

        REQUIRE(placed.status == orca::SceneStatus::success);
        CHECK(placed.instance_matrix[12] == Catch::Approx(100.0));
        CHECK(placed.instance_matrix[13] == Catch::Approx(120.0));
        CHECK(placed.instance_matrix[14] == Catch::Approx(10.0));
        CHECK(placed.size_z == Catch::Approx(20.0));
        CHECK(placed.facet_count == 12);
        CHECK(placed.volume_state == orca::VolumeState::inside);
        // The sphere through the cube's corners, and no rotation or scale.
        CHECK(placed.sphere_center[0] == Catch::Approx(100.0).margin(0.01));
        CHECK(placed.sphere_center[2] == Catch::Approx(10.0).margin(0.01));
        CHECK(placed.sphere_radius == Catch::Approx(10.0 * std::sqrt(3.0)).margin(0.01));
        CHECK(placed.rotation_degrees[2] == 0.0);
        CHECK(placed.unscaled_size[0] == Catch::Approx(20.0));
        CHECK(placed.box_center[1] == Catch::Approx(120.0));
    }
    SECTION("turned by 45 degrees, its bounding box grows")
    {
        const double c = std::sqrt(0.5);
        placement[0] = c;
        placement[1] = c;
        placement[4] = -c;
        placement[5] = c;

        const orca::ModelInspection placed = orca::place_model({}, k2_plus_profiles(), centred, placement, true, orca::Manipulation::move, {});

        REQUIRE(placed.status == orca::SceneStatus::success);
        CHECK(placed.size_x == Catch::Approx(20.0 * std::sqrt(2.0)));
        CHECK(placed.size_y == Catch::Approx(20.0 * std::sqrt(2.0)));
        CHECK(placed.size_z == Catch::Approx(20.0));
        CHECK(placed.rotation_degrees[2] == Catch::Approx(45.0));

        const orca::ModelInspection reset = orca::place_model({}, k2_plus_profiles(), placement, placement, true, orca::Manipulation::reset_rotation, {});

        REQUIRE(reset.status == orca::SceneStatus::success);
        CHECK(reset.size_x == Catch::Approx(20.0));
        CHECK(reset.rotation_degrees[2] == 0.0);
        CHECK(reset.instance_matrix[12] == Catch::Approx(175.0));
    }
    SECTION("scaled to twice its size about its centre, it rests on the plate")
    {
        placement[0] = 2.0;
        placement[5] = 2.0;
        placement[10] = 2.0;

        const orca::ModelInspection placed = orca::place_model({}, k2_plus_profiles(), centred, placement, true, orca::Manipulation::scale, {});

        REQUIRE(placed.status == orca::SceneStatus::success);
        CHECK(placed.size_z == Catch::Approx(40.0));
        CHECK(placed.unscaled_size[2] == Catch::Approx(20.0));
        CHECK(placed.instance_matrix[14] == Catch::Approx(20.0));
        // A move leaves the same sunk object sunk.
        CHECK(orca::place_model({}, k2_plus_profiles(), centred, placement, true, orca::Manipulation::move, {}).instance_matrix[14] == Catch::Approx(10.0));
    }
    SECTION("across the edge of the plate, it does not fit")
    {
        placement[12] = 0.0;
        CHECK(orca::place_model({}, k2_plus_profiles(), centred, placement, true, orca::Manipulation::move, {}).volume_state == orca::VolumeState::partly_outside);
    }
    SECTION("off the plate, it is outside")
    {
        placement[12] = -100.0;
        CHECK(orca::place_model({}, k2_plus_profiles(), centred, placement, true, orca::Manipulation::move, {}).volume_state == orca::VolumeState::outside);
    }
    SECTION("a cube offers its six faces, and lying on a side turns it")
    {
        const orca::FlatteningPlanes faces = orca::describe_flattening_planes({}, k2_plus_profiles(), placement);

        REQUIRE(faces.status == orca::SceneStatus::success);
        REQUIRE(faces.planes.size() == 6);
        // Rounded corners: 2 * k * N points for a square, k = 10.
        CHECK(faces.planes.front().vertices.size() == 3 * 80);

        const orca::ModelInspection laid = orca::place_model(
            {}, k2_plus_profiles(), placement, placement, true, orca::Manipulation::lay_on_face, {1.0, 0.0, 0.0});

        REQUIRE(laid.status == orca::SceneStatus::success);
        // The +X face now points down: the object's X axis maps to -Z.
        CHECK(laid.instance_matrix[2] == Catch::Approx(-1.0));
        CHECK(laid.instance_matrix[14] == Catch::Approx(10.0));
    }
    SECTION("with auto drop off, a lifted object stays up until auto drop is on again")
    {
        placement[14] = 40.0;

        const orca::ModelInspection lifted = orca::place_model({}, k2_plus_profiles(), centred, placement, false, orca::Manipulation::move, {});

        REQUIRE(lifted.status == orca::SceneStatus::success);
        CHECK(lifted.instance_matrix[14] == Catch::Approx(40.0));
        const std::vector<double> up(lifted.instance_matrix.begin(), lifted.instance_matrix.end());
        CHECK(orca::place_model({}, k2_plus_profiles(), up, up, false, orca::Manipulation::rotate, {}).instance_matrix[14] == Catch::Approx(40.0));

        const orca::ModelInspection dropped = orca::place_model({}, k2_plus_profiles(), up, up, true, orca::Manipulation::ensure_on_bed, {});

        CHECK(dropped.instance_matrix[14] == Catch::Approx(10.0));
    }
    SECTION("a placement that is not a matrix is rejected")
    {
        CHECK(orca::place_model({}, k2_plus_profiles(), centred, {1.0, 2.0}, true, orca::Manipulation::move, {}).status != orca::SceneStatus::success);
    }
}

TEST_CASE("The desktop app's jobs place the objects of the plate", "[Adapter][Scene]")
{
    require_engine();
    const orca::ModelInspection cube = orca::inspect_model({}, k2_plus_profiles(), output_path("jobs.mesh"), {});
    REQUIRE(cube.status == orca::SceneStatus::success);
    const std::vector<double> centred = matrix_of(cube);

    SECTION("arranged from a corner, a cube goes to the plate's best position")
    {
        std::vector<double> corner = centred;
        corner[12] = 20.0;
        corner[13] = 20.0;

        const orca::PlateInspection arranged = orca::place_objects(plate_of({}, corner), {}, k2_plus_profiles(), orca::PlateManipulation::arrange, {});

        INFO(arranged.message);
        REQUIRE(arranged.status == orca::SceneStatus::success);
        REQUIRE(arranged.objects.size() == 1);
        // best_object_pos is the plate centre for the K2 Plus.
        CHECK(arranged.objects[0].instances.front().instance_matrix[12] == Catch::Approx(175.0).margin(1.0));
        CHECK(arranged.objects[0].instances.front().instance_matrix[13] == Catch::Approx(175.0).margin(1.0));
        CHECK(arranged.objects[0].instances.front().volume_state == orca::VolumeState::inside);
    }
    SECTION("two cubes on top of each other are arranged apart")
    {
        std::vector<orca::PlateObject> plate = plate_of({}, centred);
        plate.push_back(plate.front());

        const orca::PlateInspection arranged = orca::place_objects(plate, {}, k2_plus_profiles(), orca::PlateManipulation::arrange, {});

        INFO(arranged.message);
        REQUIRE(arranged.status == orca::SceneStatus::success);
        REQUIRE(arranged.objects.size() == 2);
        const auto& a = arranged.objects[0].instances.front().instance_matrix;
        const auto& b = arranged.objects[1].instances.front().instance_matrix;
        CHECK(std::max(std::abs(a[12] - b[12]), std::abs(a[13] - b[13])) >= 20.0);
        CHECK(arranged.objects[0].instances.front().volume_state == orca::VolumeState::inside);
        CHECK(arranged.objects[1].instances.front().volume_state == orca::VolumeState::inside);
    }
    SECTION("auto orient lays the selected cubes flat, or every cube when none is selected")
    {
        // Both cubes tipped onto an edge.
        const double c = std::sqrt(0.5);
        std::vector<double> tipped = centred;
        tipped[5] = c;
        tipped[6] = c;
        tipped[9] = -c;
        tipped[10] = c;
        std::vector<orca::PlateObject> plate = plate_of({}, tipped);
        plate.push_back(plate.front());
        plate[0].instances.front().matrix[12] = 100.0;
        plate[1].instances.front().matrix[12] = 250.0;

        const orca::PlateInspection selected = orca::place_objects(plate, {false, true}, k2_plus_profiles(), orca::PlateManipulation::auto_orient, {});

        INFO(selected.message);
        REQUIRE(selected.status == orca::SceneStatus::success);
        REQUIRE(selected.objects.size() == 2);
        CHECK(selected.objects[0].instances.front().size_z == Catch::Approx(20.0 * std::sqrt(2.0)).margin(0.01));
        CHECK(selected.objects[1].instances.front().size_z == Catch::Approx(20.0).margin(0.01));

        const orca::PlateInspection all = orca::place_objects(plate, {false, false}, k2_plus_profiles(), orca::PlateManipulation::auto_orient, {});

        REQUIRE(all.status == orca::SceneStatus::success);
        CHECK(all.objects[0].instances.front().size_z == Catch::Approx(20.0).margin(0.01));
        CHECK(all.objects[1].instances.front().size_z == Catch::Approx(20.0).margin(0.01));
    }
    SECTION("a placement that is not a matrix is rejected")
    {
        CHECK(orca::place_objects(plate_of({}, {1.0, 2.0}), {}, k2_plus_profiles(), orca::PlateManipulation::arrange, {}).status
              != orca::SceneStatus::success);
    }
}
