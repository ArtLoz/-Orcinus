#pragma once

// The toolpaths file: libvgcode::GCodeInputData as the engine process converts
// it from OrcaSlicer's GCodeProcessorResult (libvgcode::convert), for the
// G-code viewer in the app process. Both processes run on the same device, so
// values are written in the host's byte order, which is little-endian on
// every Android ABI.
//
// Layout: "OTPF", format version (u32), spiral vase mode (u8), vertex count
// (u64), every PathVertex field by field in declaration order (weight is
// always present, zero unless libvgcode keeps it), the tool colours and the
// colour print colours, each as a count (u32) and RGB bytes, then Statistics
// field by field in declaration order, vectors as a count (u32) and items.

#include "libvgcode/include/GCodeInputData.hpp"

#include <array>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

namespace orcinus::toolpaths {

inline constexpr std::array<char, 4> FILE_MAGIC{'O', 'T', 'P', 'F'};
inline constexpr std::uint32_t FILE_VERSION = 3;

inline constexpr std::size_t MOVE_TYPES_COUNT = static_cast<std::size_t>(libvgcode::EMoveType::COUNT);

// What the desktop legend shows besides the moves: GCodeProcessorResult's
// print_statistics, the Print's statistics, and the move statistics
// GCodeViewer::load_as_gcode() gathers. Times are in seconds per
// libvgcode::ETimeMode, lengths in millimetres, filament as metres and grams.
struct Statistics {
    std::array<float, 2> time{0.0f, 0.0f};
    std::array<float, 2> prepare_time{0.0f, 0.0f};
    // used_filaments_per_role, by libvgcode::EGCodeExtrusionRole.
    std::vector<std::pair<std::uint8_t, std::array<double, 2>>> used_filament_per_role;
    // The filament of the volumes per extruder, over every extruder.
    std::array<double, 2> model_filament{0.0, 0.0};
    std::array<double, 2> support_filament{0.0, 0.0};
    std::array<double, 2> flushed_filament{0.0, 0.0};
    std::array<double, 2> wipe_tower_filament{0.0, 0.0};
    // The same per extruder, for the extruders each volume map lists: the
    // extruder, then metres and grams of the model, support, flushed, and
    // wipe tower filament, and which of the four the maps list (bits 1, 2, 4
    // and 8).
    std::vector<std::pair<std::uint8_t, std::array<double, 8>>> filament_per_extruder;
    std::vector<std::uint8_t> filament_listed;
    // PrintStatistics.
    double total_used_filament{0.0};
    double total_weight{0.0};
    double total_cost{0.0};
    float total_travel_distance{0.0f};
    std::uint32_t total_travel_moves{0};
    // Seam gap and scarf distances.
    float total_seam_distance{0.0f};
    std::uint32_t total_filament_changes{0};
    std::uint32_t total_extruder_changes{0};
    // Filament load, unload, and tool change times.
    float total_tool_change_time{0.0f};
    // By libvgcode::EMoveType.
    std::array<std::uint32_t, MOVE_TYPES_COUNT> move_counts{};
    std::array<std::array<float, 2>, MOVE_TYPES_COUNT> move_times{};
    std::array<float, MOVE_TYPES_COUNT> move_distances{};
};

namespace detail {

struct FileCloser {
    void operator()(std::FILE* file) const { std::fclose(file); }
};
using File = std::unique_ptr<std::FILE, FileCloser>;

// Large buffers: a print has millions of vertices.
inline constexpr std::size_t BUFFER_SIZE = 1 << 20;

template <typename T>
bool put(std::FILE* file, const T& value)
{
    return std::fwrite(&value, sizeof(T), 1, file) == 1;
}

template <typename T>
bool get(std::FILE* file, T& value)
{
    return std::fread(&value, sizeof(T), 1, file) == 1;
}

template <typename Enum>
bool get_enum(std::FILE* file, Enum& value)
{
    std::underlying_type_t<Enum> raw{};
    if (!get(file, raw) || raw >= static_cast<std::underlying_type_t<Enum>>(Enum::COUNT)) {
        return false;
    }
    value = static_cast<Enum>(raw);
    return true;
}

inline bool put_palette(std::FILE* file, const libvgcode::Palette& palette)
{
    if (!put(file, static_cast<std::uint32_t>(palette.size()))) {
        return false;
    }
    for (const libvgcode::Color& color : palette) {
        if (!put(file, color)) {
            return false;
        }
    }
    return true;
}

inline bool get_palette(std::FILE* file, libvgcode::Palette& palette)
{
    std::uint32_t count = 0;
    if (!get(file, count) || count > 4096) {
        return false;
    }
    palette.resize(count);
    for (libvgcode::Color& color : palette) {
        if (!get(file, color)) {
            return false;
        }
    }
    return true;
}

inline bool put_statistics(std::FILE* file, const Statistics& statistics)
{
    bool ok = put(file, statistics.time) && put(file, statistics.prepare_time) &&
        put(file, static_cast<std::uint32_t>(statistics.used_filament_per_role.size()));
    for (const auto& [role, used] : statistics.used_filament_per_role) {
        ok = ok && put(file, role) && put(file, used);
    }
    ok = ok && put(file, static_cast<std::uint32_t>(statistics.filament_per_extruder.size()));
    for (std::size_t index = 0; index < statistics.filament_per_extruder.size(); ++index) {
        ok = ok && put(file, statistics.filament_per_extruder[index].first) && put(file, statistics.filament_per_extruder[index].second) &&
            put(file, statistics.filament_listed[index]);
    }
    return ok && put(file, statistics.model_filament) && put(file, statistics.support_filament) &&
        put(file, statistics.flushed_filament) && put(file, statistics.wipe_tower_filament) &&
        put(file, statistics.total_used_filament) && put(file, statistics.total_weight) && put(file, statistics.total_cost) &&
        put(file, statistics.total_travel_distance) && put(file, statistics.total_travel_moves) &&
        put(file, statistics.total_seam_distance) && put(file, statistics.total_filament_changes) &&
        put(file, statistics.total_extruder_changes) && put(file, statistics.total_tool_change_time) &&
        put(file, statistics.move_counts) && put(file, statistics.move_times) && put(file, statistics.move_distances);
}

inline bool get_statistics(std::FILE* file, Statistics& statistics)
{
    std::uint32_t roles = 0;
    if (!get(file, statistics.time) || !get(file, statistics.prepare_time) || !get(file, roles) ||
        roles > static_cast<std::uint32_t>(libvgcode::EGCodeExtrusionRole::COUNT)) {
        return false;
    }
    statistics.used_filament_per_role.resize(roles);
    for (auto& [role, used] : statistics.used_filament_per_role) {
        if (!get(file, role) || !get(file, used)) {
            return false;
        }
    }
    std::uint32_t extruders = 0;
    if (!get(file, extruders) || extruders > 256) {
        return false;
    }
    statistics.filament_per_extruder.resize(extruders);
    statistics.filament_listed.resize(extruders);
    for (std::size_t index = 0; index < extruders; ++index) {
        if (!get(file, statistics.filament_per_extruder[index].first) || !get(file, statistics.filament_per_extruder[index].second) ||
            !get(file, statistics.filament_listed[index])) {
            return false;
        }
    }
    return get(file, statistics.model_filament) && get(file, statistics.support_filament) &&
        get(file, statistics.flushed_filament) && get(file, statistics.wipe_tower_filament) &&
        get(file, statistics.total_used_filament) && get(file, statistics.total_weight) && get(file, statistics.total_cost) &&
        get(file, statistics.total_travel_distance) && get(file, statistics.total_travel_moves) &&
        get(file, statistics.total_seam_distance) && get(file, statistics.total_filament_changes) &&
        get(file, statistics.total_extruder_changes) && get(file, statistics.total_tool_change_time) &&
        get(file, statistics.move_counts) && get(file, statistics.move_times) && get(file, statistics.move_distances);
}

} // namespace detail

// Writes data and statistics to path; false when the file cannot be written completely.
inline bool write_file(const std::string& path, const libvgcode::GCodeInputData& data, const Statistics& statistics)
{
    using detail::put;
    const detail::File file{std::fopen(path.c_str(), "wb")};
    if (!file) {
        return false;
    }
    std::FILE* out = file.get();
    std::setvbuf(out, nullptr, _IOFBF, detail::BUFFER_SIZE);
    bool ok = std::fwrite(FILE_MAGIC.data(), 1, FILE_MAGIC.size(), out) == FILE_MAGIC.size() &&
        put(out, FILE_VERSION) &&
        put(out, static_cast<std::uint8_t>(data.spiral_vase_mode ? 1 : 0)) &&
        put(out, static_cast<std::uint64_t>(data.vertices.size()));
    for (const libvgcode::PathVertex& vertex : data.vertices) {
        if (!ok) {
            break;
        }
#if VGCODE_ENABLE_COG_AND_TOOL_MARKERS
        const float weight = vertex.weight;
#else
        const float weight = 0.0f;
#endif
        ok = put(out, vertex.position) && put(out, vertex.height) && put(out, vertex.width) &&
            put(out, vertex.feedrate) && put(out, vertex.actual_feedrate) && put(out, vertex.mm3_per_mm) &&
            put(out, vertex.fan_speed) && put(out, vertex.temperature) && put(out, weight) &&
            put(out, static_cast<std::uint8_t>(vertex.role)) && put(out, static_cast<std::uint8_t>(vertex.type)) &&
            put(out, vertex.gcode_id) && put(out, vertex.layer_id) && put(out, vertex.extruder_id) &&
            put(out, vertex.color_id) && put(out, vertex.times) && put(out, vertex.layer_duration) &&
            put(out, vertex.pressure_advance) && put(out, vertex.acceleration) && put(out, vertex.jerk);
    }
    ok = ok && detail::put_palette(out, data.tools_colors) && detail::put_palette(out, data.color_print_colors) &&
        detail::put_statistics(out, statistics);
    return ok && std::fflush(out) == 0;
}

// Reads a file written by write_file(); false when it is missing, of another
// format version, or cut short.
inline bool read_file(const std::string& path, libvgcode::GCodeInputData& data, Statistics& statistics)
{
    using detail::get;
    const detail::File file{std::fopen(path.c_str(), "rb")};
    if (!file) {
        return false;
    }
    std::FILE* in = file.get();
    std::setvbuf(in, nullptr, _IOFBF, detail::BUFFER_SIZE);
    std::array<char, 4> magic{};
    std::uint32_t version = 0;
    std::uint8_t spiral_vase_mode = 0;
    std::uint64_t count = 0;
    if (std::fread(magic.data(), 1, magic.size(), in) != magic.size() || magic != FILE_MAGIC ||
        !get(in, version) || version != FILE_VERSION || !get(in, spiral_vase_mode) || !get(in, count)) {
        return false;
    }
    libvgcode::GCodeInputData result;
    result.spiral_vase_mode = spiral_vase_mode != 0;
    result.vertices.resize(static_cast<std::size_t>(count));
    for (libvgcode::PathVertex& vertex : result.vertices) {
        float weight = 0.0f;
        if (!(get(in, vertex.position) && get(in, vertex.height) && get(in, vertex.width) &&
                get(in, vertex.feedrate) && get(in, vertex.actual_feedrate) && get(in, vertex.mm3_per_mm) &&
                get(in, vertex.fan_speed) && get(in, vertex.temperature) && get(in, weight) &&
                detail::get_enum(in, vertex.role) && detail::get_enum(in, vertex.type) &&
                get(in, vertex.gcode_id) && get(in, vertex.layer_id) && get(in, vertex.extruder_id) &&
                get(in, vertex.color_id) && get(in, vertex.times) && get(in, vertex.layer_duration) &&
                get(in, vertex.pressure_advance) && get(in, vertex.acceleration) && get(in, vertex.jerk))) {
            return false;
        }
#if VGCODE_ENABLE_COG_AND_TOOL_MARKERS
        vertex.weight = weight;
#endif
    }
    Statistics result_statistics;
    if (!detail::get_palette(in, result.tools_colors) || !detail::get_palette(in, result.color_print_colors) ||
        !detail::get_statistics(in, result_statistics)) {
        return false;
    }
    data = std::move(result);
    statistics = std::move(result_statistics);
    return true;
}

} // namespace orcinus::toolpaths
