// The flushing volumes of the plate, ported from OrcaSlicer's GUI: how much
// filament is pushed out when the print changes from one filament to another.
// Ports WipingDialog::CalcFlushingVolumes() and CalcFlushingVolume(),
// get_min_flush_volumes() of Plater.cpp, is_support_filament() and
// has_filaments() of GUI_App.cpp, and is_flush_config_modified().

#include <algorithm>
#include <cmath>
#include <numeric>
#include <string>
#include <vector>

#include "libslic3r/FlushVolCalc.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/libslic3r.h"

#include "engine_context.hpp"
#include "orca_engine_adapter.hpp"

namespace orcinus::orca {
namespace {

// A colour of the project as OrcaSlicer writes it, "#RRGGBB" or "#RRGGBBAA".
struct Rgba {
    unsigned char red{0};
    unsigned char green{0};
    unsigned char blue{0};
    unsigned char alpha{255};
};

Rgba parse_color(const std::string& value)
{
    Rgba color;
    const std::string hex = value.empty() || value.front() != '#' ? value : value.substr(1);
    const auto component = [&hex](const std::size_t at) -> unsigned char {
        if (hex.size() < at + 2) {
            return 0;
        }
        return static_cast<unsigned char>(std::stoul(hex.substr(at, 2), nullptr, 16));
    };
    if (hex.size() >= 6) {
        color.red = component(0);
        color.green = component(2);
        color.blue = component(4);
    }
    if (hex.size() >= 8) {
        color.alpha = component(6);
    }
    return color;
}

/**
 * get_min_flush_volumes(): the volume left in the nozzle when the filament is
 * changed, less what a long retraction takes back out.
 */
std::vector<int> min_flush_volumes(const Slic3r::DynamicPrintConfig& config, const std::size_t nozzle)
{
    const auto* const nozzle_volume = config.option<Slic3r::ConfigOptionFloatsNullable>("nozzle_volume");
    const int nozzle_volume_value = nozzle_volume == nullptr ? 0 : static_cast<int>(nozzle_volume->get_at(nozzle));

    const auto* const enabled_level = config.option<Slic3r::ConfigOptionInt>("enable_long_retraction_when_cut");
    const int machine_enabled_level = enabled_level == nullptr ? 0 : enabled_level->value;
    const auto* const machine_long_retractions = config.option<Slic3r::ConfigOptionBools>("long_retractions_when_cut");
    const bool machine_activated = machine_long_retractions != nullptr
        && nozzle < machine_long_retractions->values.size()
        && machine_long_retractions->values[nozzle] == 1;

    const std::size_t filaments = config.option<Slic3r::ConfigOptionFloats>("filament_diameter")->values.size();
    std::vector<double> filament_distances(filaments, 18.0);
    std::vector<double> printer_distances(filaments, 18.0);
    std::vector<unsigned char> filament_long_retractions(filaments, 0);
    if (const auto* const option = config.option<Slic3r::ConfigOptionFloats>("filament_retraction_distances_when_cut")) {
        filament_distances = option->values;
    }
    if (const auto* const option = config.option<Slic3r::ConfigOptionFloats>("retraction_distances_when_cut")) {
        printer_distances = option->values;
    }
    if (const auto* const option = config.option<Slic3r::ConfigOptionBools>("filament_long_retractions_when_cut")) {
        filament_long_retractions = option->values;
    }

    std::vector<int> volumes;
    volumes.reserve(filaments);
    for (std::size_t index = 0; index < filaments; ++index) {
        int retract_length = machine_enabled_level != 0 && machine_activated && nozzle < printer_distances.size()
            ? static_cast<int>(printer_distances[nozzle])
            : 0;
        const unsigned char filament_activated = index < filament_long_retractions.size() ? filament_long_retractions[index] : 0;
        if (filament_activated == 0) {
            retract_length = 0;
        } else if (filament_activated == 1 && machine_enabled_level == Slic3r::LongRectrationLevel::EnableFilament) {
            const double distance = index < filament_distances.size() ? filament_distances[index] : 18.0;
            retract_length = std::isnan(distance)
                ? (nozzle < printer_distances.size() ? static_cast<int>(printer_distances[nozzle]) : 0)
                : static_cast<int>(distance);
        }
        volumes.push_back(nozzle_volume_value - static_cast<int>(PI * 1.75 * 1.75 / 4 * retract_length));
    }
    return volumes;
}

/** has_filaments(): whether the plate prints with one of those materials. */
bool has_filaments(const Slic3r::Model& model, const Slic3r::DynamicPrintConfig& config, const std::vector<std::string>& wanted)
{
    Slic3r::Model::setExtruderParams(config, config.option<Slic3r::ConfigOptionStrings>("filament_settings_id")->values.size());
    for (const Slic3r::ModelObject* const object : model.objects) {
        for (const Slic3r::ModelVolume* const volume : object->volumes) {
            for (const int id : volume->get_extruders()) {
                const auto found = Slic3r::Model::extruderParamsMap.find(id);
                const std::string name = found == Slic3r::Model::extruderParamsMap.end() ? "PLA" : found->second.materialName;
                if (std::find(wanted.begin(), wanted.end(), name) != wanted.end()) {
                    return true;
                }
            }
        }
    }
    return false;
}

/** is_support_filament(): a filament printed as support is wiped differently. */
bool is_support_filament(
    const Slic3r::PresetBundle& bundle,
    const Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    const std::size_t index
)
{
    if (index >= bundle.filament_presets.size()) {
        return false;
    }
    const Slic3r::Preset* const filament = bundle.filaments.find_preset(bundle.filament_presets[index]);
    if (filament == nullptr) {
        return false;
    }
    const std::string type = filament->config.option<Slic3r::ConfigOptionStrings>("filament_type")->values.front();
    if (type == "PETG" || type == "PLA") {
        const std::vector<std::string> pairs = type == "PETG"
            ? std::vector<std::string>{"PLA"}
            : std::vector<std::string>{"PETG", "TPU", "TPU-AMS"};
        if (has_filaments(model, config, pairs)) {
            return true;
        }
    }
    const auto* const support = dynamic_cast<const Slic3r::ConfigOptionBools*>(filament->config.option("filament_is_support"));
    return support != nullptr && support->get_at(0) != 0;
}

/** WipingDialog::CalcFlushingVolumes(): the volumes OrcaSlicer works out from the filament colours. */
std::vector<double> calculated_matrix(
    const Slic3r::PresetBundle& bundle,
    const Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    const std::size_t nozzle
)
{
    const std::vector<std::string>& colors = config.option<Slic3r::ConfigOptionStrings>("filament_colour")->values;
    const std::size_t filaments = colors.size();
    const std::vector<int> minimums = min_flush_volumes(config, nozzle);
    int dataset = 0;
    if (const auto* const option = config.option<Slic3r::ConfigOptionIntsNullable>("nozzle_flush_dataset");
        option != nullptr && nozzle < option->values.size()) {
        dataset = option->values[nozzle];
    }

    std::vector<double> matrix(filaments * filaments, 0.0);
    for (std::size_t from = 0; from < filaments; ++from) {
        const bool from_support = is_support_filament(bundle, model, config, from);
        for (std::size_t to = 0; to < filaments; ++to) {
            if (from == to) {
                continue;
            }
            int volume = 0;
            if (is_support_filament(bundle, model, config, to)) {
                volume = Slic3r::g_flush_volume_to_support;
            } else {
                const Rgba source = parse_color(colors[from]);
                const Rgba target = parse_color(colors[to]);
                Slic3r::FlushVolCalculator calculator(
                    from < minimums.size() ? minimums[from] : 0,
                    Slic3r::g_max_flush_volume,
                    dataset
                );
                volume = calculator.calc_flush_vol(
                    source.alpha, source.red, source.green, source.blue,
                    target.alpha, target.red, target.green, target.blue
                );
                if (from_support) {
                    volume = std::max(Slic3r::g_min_flush_volume_from_support, volume);
                }
            }
            matrix[from * filaments + to] = volume;
        }
    }
    return matrix;
}

/**
 * PresetBundle::update_multi_material_filament_presets() for the project's
 * matrix and multipliers: brought to num_filaments and nozzle_nums, keeping the
 * volumes between the filaments the matrix had (without to_delete_filament_id)
 * and giving a new pair the sum of the volumes flush_volumes_vector holds.
 */
void update_flush_matrix(
    std::vector<double>& old_matrix_values,
    std::vector<double>& f_multiplier,
    std::vector<double> filaments,
    const std::size_t num_filaments,
    const std::size_t nozzle_nums,
    std::size_t to_delete_filament_id
)
{
    if (to_delete_filament_id == std::size_t(-1))
        to_delete_filament_id = num_filaments;

    const std::vector<double> old_matrix = old_matrix_values;
    const std::size_t old_nozzle_nums = std::max<std::size_t>(1, f_multiplier.size());
    const std::size_t old_number_of_filaments = std::size_t(std::sqrt(old_matrix.size() / old_nozzle_nums) + EPSILON);
    if (old_nozzle_nums != nozzle_nums) {
        f_multiplier.resize(nozzle_nums, 1.f);
    }

    if ((num_filaments * num_filaments) != std::size_t(old_matrix.size() / old_nozzle_nums)) {
        // First verify if purging volumes presets for each extruder matches number of extruders
        while (filaments.size() < 2 * num_filaments) {
            filaments.push_back(filaments.size() > 1 ? filaments[0] : 140.); // copy the values from the first extruder
            filaments.push_back(filaments.size() > 1 ? filaments[1] : 140.);
        }
        while (filaments.size() > 2 * num_filaments) {
            filaments.pop_back();
            filaments.pop_back();
        }

        const std::size_t old_matrix_size = old_number_of_filaments * old_number_of_filaments;
        const std::size_t new_matrix_size = num_filaments * num_filaments;
        std::vector<double> new_matrix(new_matrix_size * nozzle_nums, 0);
        for (unsigned int i = 0; i < num_filaments; ++i)
            for (unsigned int j = 0; j < num_filaments; ++j) {
                if (i < old_number_of_filaments && j < old_number_of_filaments) {
                    const unsigned int old_i = i >= to_delete_filament_id ? i + 1 : i;
                    const unsigned int old_j = j >= to_delete_filament_id ? j + 1 : j;
                    for (std::size_t nozzle_id = 0; nozzle_id < nozzle_nums; ++nozzle_id) {
                        // Orca: only copy from old_matrix when the old layout actually has data
                        // for this nozzle slot; otherwise initialize from the per-filament
                        // flush volumes the same way the (i,j) out-of-range branch does.
                        const std::size_t old_at = old_i * old_number_of_filaments + old_j + old_matrix_size * nozzle_id;
                        if (nozzle_id < old_nozzle_nums && old_at < old_matrix.size()) {
                            new_matrix[i * num_filaments + j + new_matrix_size * nozzle_id] = old_matrix[old_at];
                        } else {
                            new_matrix[i * num_filaments + j + new_matrix_size * nozzle_id] = (i == j ? 0. : filaments[2 * i] + filaments[2 * j + 1]);
                        }
                    }
                } else {
                    for (std::size_t nozzle_id = 0; nozzle_id < nozzle_nums; ++nozzle_id) {
                        new_matrix[i * num_filaments + j + new_matrix_size * nozzle_id] = (i == j ? 0. : filaments[2 * i] + filaments[2 * j + 1]);
                    }
                }
            }
        old_matrix_values = new_matrix;
    }
}

/**
 * Sidebar::auto_calc_flushing_volumes_internal(): the volumes from every
 * filament to modify_id and from modify_id to every filament, for the nozzle
 * extruder_id, worked out from the colours. The other volumes stay.
 */
void auto_calc_flushing_volumes_internal(
    std::vector<double>& project_matrix,
    const Slic3r::PresetBundle& bundle,
    const Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& full_config,
    const int modify_id,
    const int extruder_id,
    const std::size_t extruder_nums
)
{
    int nozzle_flush_dataset = 0;
    if (const auto* const option = full_config.option<Slic3r::ConfigOptionIntsNullable>("nozzle_flush_dataset");
        option != nullptr && std::size_t(extruder_id) < option->values.size()) {
        nozzle_flush_dataset = option->values[extruder_id];
    }
    const std::vector<double> init_matrix = Slic3r::get_flush_volumes_matrix(project_matrix, extruder_id, extruder_nums);

    const std::vector<int> min_flush_volumes = orcinus::orca::min_flush_volumes(full_config, std::size_t(extruder_id));

    std::vector<double> matrix = init_matrix;
    const int m_max_flush_volume = Slic3r::g_max_flush_volume;
    const unsigned int m_number_of_extruders = (int)(std::sqrt(init_matrix.size()) + 0.001);

    // get_extruder_colors_from_plater_config(); the app has no multi-colour filaments of an AMS.
    const std::vector<std::string>& extruder_colours = full_config.option<Slic3r::ConfigOptionStrings>("filament_colour")->values;
    const auto is_support_filament = [&](const int index) { return orcinus::orca::is_support_filament(bundle, model, full_config, std::size_t(index)); };

    if (modify_id >= 0 && std::size_t(modify_id) < extruder_colours.size() && std::size_t(modify_id) < m_number_of_extruders) {
        const int filaments = int(std::min<std::size_t>(extruder_colours.size(), m_number_of_extruders));
        for (int i = 0; i < filaments; ++i) {
            // from to modify
            int from_idx = i;
            if (from_idx != modify_id) {
                Slic3r::FlushVolCalculator calculator(std::size_t(from_idx) < min_flush_volumes.size() ? min_flush_volumes[from_idx] : 0,
                                                      m_max_flush_volume, nozzle_flush_dataset);
                int flushing_volume = 0;
                bool is_from_support = is_support_filament(from_idx);
                bool is_to_support = is_support_filament(modify_id);
                if (is_to_support) {
                    flushing_volume = Slic3r::g_flush_volume_to_support;
                } else {
                    const Rgba from = parse_color(extruder_colours[from_idx]);
                    const Rgba to = parse_color(extruder_colours[modify_id]);
                    int volume = calculator.calc_flush_vol(from.alpha, from.red, from.green, from.blue, to.alpha, to.red, to.green, to.blue);
                    flushing_volume = std::max(flushing_volume, volume);
                    if (is_from_support)
                        flushing_volume = std::max(flushing_volume, Slic3r::g_min_flush_volume_from_support);
                }
                matrix[m_number_of_extruders * from_idx + modify_id] = flushing_volume;
            }

            // modify to to
            int to_idx = i;
            if (to_idx != modify_id) {
                Slic3r::FlushVolCalculator calculator(std::size_t(modify_id) < min_flush_volumes.size() ? min_flush_volumes[modify_id] : 0,
                                                      m_max_flush_volume, nozzle_flush_dataset);
                bool is_from_support = is_support_filament(modify_id);
                bool is_to_support = is_support_filament(to_idx);
                int flushing_volume = 0;
                if (is_to_support) {
                    flushing_volume = Slic3r::g_flush_volume_to_support;
                } else {
                    const Rgba from = parse_color(extruder_colours[modify_id]);
                    const Rgba to = parse_color(extruder_colours[to_idx]);
                    int volume = calculator.calc_flush_vol(from.alpha, from.red, from.green, from.blue, to.alpha, to.red, to.green, to.blue);
                    flushing_volume = std::max(flushing_volume, volume);
                    if (is_from_support)
                        flushing_volume = std::max(flushing_volume, Slic3r::g_min_flush_volume_from_support);

                    matrix[m_number_of_extruders * modify_id + to_idx] = flushing_volume;
                }
            }
        }
    }
    Slic3r::set_flush_volumes_matrix(project_matrix, matrix, extruder_id, extruder_nums);
}

/** The flushing volumes of a project with matrix and multipliers, as the sheet shows them. */
void describe_project(
    FlushVolumes& result,
    const Slic3r::PresetBundle& bundle,
    const Slic3r::Model& model,
    const Slic3r::DynamicPrintConfig& config,
    const std::vector<double>* stored,
    const std::vector<double>* stored_multipliers
)
{
    const std::size_t filaments = config.option<Slic3r::ConfigOptionStrings>("filament_colour")->values.size();
    const std::size_t nozzles = std::max<std::size_t>(1, config.option<Slic3r::ConfigOptionFloats>("nozzle_diameter")->values.size());
    result.status = SceneStatus::success;
    result.filaments = static_cast<int>(filaments);
    result.nozzles = static_cast<int>(nozzles);
    for (std::size_t nozzle = 0; nozzle < nozzles; ++nozzle) {
        const std::vector<double> automatic = calculated_matrix(bundle, model, config, nozzle);
        result.automatic.insert(result.automatic.end(), automatic.begin(), automatic.end());

        const double multiplier = stored_multipliers != nullptr && nozzle < stored_multipliers->size() ? (*stored_multipliers)[nozzle] : 1.0;
        result.multipliers.push_back(multiplier);

        // The project keeps a matrix per nozzle, one row after another; a
        // project that carries none is shown the calculated volumes. The
        // matrix holds the volumes themselves: the multiplier is applied
        // when the print changes filament (GCode::process_layer).
        for (std::size_t cell = 0; cell < filaments * filaments; ++cell) {
            const std::size_t at = nozzle * filaments * filaments + cell;
            const bool kept = stored != nullptr && at < stored->size();
            result.matrix.push_back(kept ? (*stored)[at] : automatic[cell]);
            // is_flush_config_modified(): the project differs from what
            // OrcaSlicer would work out on its own.
            if (multiplier != 1.0 || (kept && (*stored)[at] != automatic[cell] * multiplier)) {
                result.modified = true;
            }
        }
    }
}

}  // namespace

FlushVolumes describe_flush_volumes(
    const std::vector<PlateObject>& plate,
    const ProfileSelection& profiles,
    const ModelSettings& plate_settings
)
{
    FlushVolumes result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().bundle == nullptr) {
        result.message = "OrcaSlicer profiles are not loaded";
        return result;
    }

    try {
        Slic3r::DynamicPrintConfig config;
        std::string message;
        if (detail::select_profiles(*detail::engine().bundle, profiles, config, message) != SliceStatus::success) {
            result.status = SceneStatus::profile_not_found;
            result.message = message;
            return result;
        }
        config.apply(detail::model_config(plate_settings), true);

        Slic3r::Model model;
        if (!detail::load_plate(plate, config, model, message)) {
            result.status = SceneStatus::model_read_failed;
            result.message = message;
            return result;
        }

        const auto* const stored = config.option<Slic3r::ConfigOptionFloats>("flush_volumes_matrix");
        const auto* const stored_multipliers = config.option<Slic3r::ConfigOptionFloats>("flush_multiplier");
        describe_project(result, *detail::engine().bundle, model, config, stored != nullptr ? &stored->values : nullptr,
                         stored_multipliers != nullptr ? &stored_multipliers->values : nullptr);
        return result;
    } catch (const std::exception& error) {
        result.status = SceneStatus::model_read_failed;
        result.message = error.what();
        return result;
    }
}

FlushVolumesUpdate update_flush_volumes(
    const std::vector<PlateObject>& plate,
    const ProfileSelection& profiles,
    const ModelSettings& plate_settings,
    const FlushVolumesChange change,
    const std::int64_t index
)
{
    FlushVolumesUpdate result;
    const std::lock_guard<std::mutex> engine_lock(detail::engine().mutex);
    if (detail::engine().bundle == nullptr) {
        result.volumes.message = "OrcaSlicer profiles are not loaded";
        return result;
    }

    try {
        Slic3r::PresetBundle& bundle = *detail::engine().bundle;
        Slic3r::DynamicPrintConfig config;
        std::string message;
        if (detail::select_profiles(bundle, profiles, config, message) != SliceStatus::success) {
            result.volumes.status = SceneStatus::profile_not_found;
            result.volumes.message = message;
            return result;
        }
        config.apply(detail::model_config(plate_settings), true);

        Slic3r::Model model;
        if (!detail::load_plate(plate, config, model, message)) {
            result.volumes.status = SceneStatus::model_read_failed;
            result.volumes.message = message;
            return result;
        }

        // The project: the plate's own volumes, or those of the project config.
        const std::vector<double> matrix_before = config.option<Slic3r::ConfigOptionFloats>("flush_volumes_matrix")->values;
        const std::vector<double> multipliers_before = config.option<Slic3r::ConfigOptionFloats>("flush_multiplier")->values;
        std::vector<double> matrix = matrix_before;
        std::vector<double> multipliers = multipliers_before;
        const std::size_t num_filaments = config.option<Slic3r::ConfigOptionStrings>("filament_colour")->values.size();
        const std::size_t nozzle_nums = std::size_t(std::max(1, bundle.get_printer_extruder_count()));
        std::vector<double> flush_volumes_vector;
        if (const auto* const option = config.option<Slic3r::ConfigOptionFloats>("flush_volumes_vector")) {
            flush_volumes_vector = option->values;
        }
        update_flush_matrix(matrix, multipliers, flush_volumes_vector, num_filaments, nozzle_nums,
                            change == FlushVolumesChange::filament_removed ? std::size_t(index) : std::size_t(-1));

        // Where the desktop app calls Sidebar::auto_calc_flushing_volumes(),
        // and what its preference lets it do: -2 is none, -1 every filament.
        const std::string auto_calculate_flush = detail::engine().config->get("auto_calculate_flush");
        int filament_idx = -2;
        switch (change) {
        case FlushVolumesChange::filament_added:
            filament_idx = int(index);
            break;
        case FlushVolumesChange::filament_removed:
            break;
        case FlushVolumesChange::color_changed:
            if (auto_calculate_flush != "disabled")
                filament_idx = int(index);
            break;
        case FlushVolumesChange::filament_changed:
            if (auto_calculate_flush == "all")
                filament_idx = int(index);
            break;
        case FlushVolumesChange::printer_changed:
        case FlushVolumesChange::long_retraction_changed:
            if (auto_calculate_flush == "all")
                filament_idx = -1;
            break;
        }
        if (filament_idx != -2) {
            // Sidebar::auto_calc_flushing_volumes()
            std::vector<int> filament_indices;
            if (filament_idx < 0) {
                filament_indices.resize(num_filaments);
                std::iota(filament_indices.begin(), filament_indices.end(), 0);
            } else {
                filament_indices.emplace_back(filament_idx);
            }
            for (std::size_t eidx = 0; eidx < nozzle_nums; ++eidx) {
                for (const int fidx : filament_indices) {
                    auto_calc_flushing_volumes_internal(matrix, bundle, model, config, fidx, int(eidx), nozzle_nums);
                }
            }
        }

        result.updated = matrix != matrix_before || multipliers != multipliers_before;
        describe_project(result.volumes, bundle, model, config, &matrix, &multipliers);
        return result;
    } catch (const std::exception& error) {
        result.volumes.status = SceneStatus::model_read_failed;
        result.volumes.message = error.what();
        return result;
    }
}

}  // namespace orcinus::orca
