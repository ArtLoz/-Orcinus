// JNI bridge of :render:gcode: OrcaSlicer's libvgcode viewer, drawing the
// toolpaths the engine process wrote, in the plate view's OpenGL ES context.
//
// Data read from a toolpaths file and the viewer are separate handles: the
// data is read off the GL thread, and the viewer lives in the GL context it
// was initialised in, taking the data when it loads it. Changing what the
// viewer shows uploads textures, so those calls come from the GL thread too.

#include "toolpaths_file.hpp"
#include "toolpaths_obj.hpp"

#include "libvgcode/include/ColorRange.hpp"
#include "libvgcode/include/Viewer.hpp"

#include <android/log.h>
#include <jni.h>

#include <GLES3/gl3.h>

#include <algorithm>
#include <array>
#include <cfloat>
#include <exception>
#include <memory>
#include <string>
#include <vector>

namespace {

constexpr const char* LOG_TAG = "OrcinusToolpaths";

// A file's content until the viewer takes the moves.
struct ToolpathsData {
    libvgcode::GCodeInputData input;
    orcinus::toolpaths::Statistics statistics;
};

libvgcode::Viewer& viewer_of(jlong viewer)
{
    return *reinterpret_cast<libvgcode::Viewer*>(viewer);
}

std::string to_string(JNIEnv* env, jstring value)
{
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

libvgcode::Mat4x4 to_matrix(JNIEnv* env, jfloatArray values)
{
    libvgcode::Mat4x4 matrix{};
    env->GetFloatArrayRegion(values, 0, static_cast<jsize>(matrix.size()), matrix.data());
    return matrix;
}

jint to_rgb(const libvgcode::Color& color)
{
    return (static_cast<jint>(color[0]) << 16) | (static_cast<jint>(color[1]) << 8) | static_cast<jint>(color[2]);
}

jintArray int_array(JNIEnv* env, const std::vector<jint>& values)
{
    jintArray array = env->NewIntArray(static_cast<jsize>(values.size()));
    env->SetIntArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    return array;
}

jfloatArray float_array(JNIEnv* env, const std::vector<jfloat>& values)
{
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(values.size()));
    env->SetFloatArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    return array;
}

jdoubleArray double_array(JNIEnv* env, const std::vector<jdouble>& values)
{
    jdoubleArray array = env->NewDoubleArray(static_cast<jsize>(values.size()));
    env->SetDoubleArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    return array;
}

jbooleanArray boolean_array(JNIEnv* env, const std::vector<jboolean>& values)
{
    jbooleanArray array = env->NewBooleanArray(static_cast<jsize>(values.size()));
    env->SetBooleanArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    return array;
}

// The view types whose legend is a colour range (GCodeViewer::render_legend()'s append_range()).
bool has_color_range(libvgcode::EViewType type)
{
    switch (type) {
    case libvgcode::EViewType::Speed:
    case libvgcode::EViewType::ActualSpeed:
    case libvgcode::EViewType::Height:
    case libvgcode::EViewType::Width:
    case libvgcode::EViewType::VolumetricFlowRate:
    case libvgcode::EViewType::ActualVolumetricFlowRate:
    case libvgcode::EViewType::LayerTimeLinear:
    case libvgcode::EViewType::LayerTimeLogarithmic:
    case libvgcode::EViewType::FanSpeed:
    case libvgcode::EViewType::Temperature:
    case libvgcode::EViewType::PressureAdvance:
    case libvgcode::EViewType::Acceleration:
    case libvgcode::EViewType::Jerk:
        return true;
    default:
        return false;
    }
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_read(JNIEnv* env, jobject, jstring path, jfloatArray bounds)
{
    auto data = std::make_unique<ToolpathsData>();
    try {
        if (!orcinus::toolpaths::read_file(to_string(env, path), data->input, data->statistics)) {
            return 0;
        }
    } catch (const std::exception& error) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Unable to read toolpaths: %s", error.what());
        return 0;
    }
    // Every move but no-ops, as Viewer::get_bounding_box() takes them.
    std::array<float, 6> box{FLT_MAX, FLT_MAX, FLT_MAX, -FLT_MAX, -FLT_MAX, -FLT_MAX};
    for (const libvgcode::PathVertex& vertex : data->input.vertices) {
        if (vertex.type == libvgcode::EMoveType::Noop) {
            continue;
        }
        for (std::size_t axis = 0; axis < 3; ++axis) {
            box[axis] = std::min(box[axis], vertex.position[axis]);
            box[axis + 3] = std::max(box[axis + 3], vertex.position[axis]);
        }
    }
    if (box[0] > box[3]) {
        box.fill(0.0f);
    }
    env->SetFloatArrayRegion(bounds, 0, static_cast<jsize>(box.size()), box.data());
    return reinterpret_cast<jlong>(data.release());
}

JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_statistics(JNIEnv* env, jobject, jlong data)
{
    const orcinus::toolpaths::Statistics& statistics = reinterpret_cast<ToolpathsData*>(data)->statistics;
    std::vector<jint> roles;
    std::vector<jdouble> role_filament;
    for (const auto& [role, used] : statistics.used_filament_per_role) {
        roles.push_back(role);
        role_filament.push_back(used[0]);
        role_filament.push_back(used[1]);
    }
    std::vector<jint> move_counts(statistics.move_counts.begin(), statistics.move_counts.end());
    std::vector<jfloat> move_times;
    for (const auto& times : statistics.move_times) {
        move_times.insert(move_times.end(), times.begin(), times.end());
    }
    std::vector<jfloat> move_distances(statistics.move_distances.begin(), statistics.move_distances.end());
    const std::vector<jdouble> filament{
        statistics.model_filament[0], statistics.model_filament[1],
        statistics.support_filament[0], statistics.support_filament[1],
        statistics.flushed_filament[0], statistics.flushed_filament[1],
        statistics.wipe_tower_filament[0], statistics.wipe_tower_filament[1],
    };

    std::vector<jint> extruders;
    std::vector<jint> extruder_listed;
    std::vector<jdouble> extruder_filament;
    for (std::size_t index = 0; index < statistics.filament_per_extruder.size(); ++index) {
        extruders.push_back(statistics.filament_per_extruder[index].first);
        extruder_listed.push_back(statistics.filament_listed[index]);
        const auto& values = statistics.filament_per_extruder[index].second;
        extruder_filament.insert(extruder_filament.end(), values.begin(), values.end());
    }

    const jclass type = env->FindClass("app/orcinus/shadow/render/gcode/NativeToolpathsStatistics");
    const jmethodID constructor = env->GetMethodID(type, "<init>", "([F[F[I[D[DDDDFIFIIF[I[F[F[I[I[D)V");
    return env->NewObject(
        type,
        constructor,
        float_array(env, {statistics.time[0], statistics.time[1]}),
        float_array(env, {statistics.prepare_time[0], statistics.prepare_time[1]}),
        int_array(env, roles),
        double_array(env, role_filament),
        double_array(env, filament),
        statistics.total_used_filament,
        statistics.total_weight,
        statistics.total_cost,
        statistics.total_travel_distance,
        static_cast<jint>(statistics.total_travel_moves),
        statistics.total_seam_distance,
        static_cast<jint>(statistics.total_filament_changes),
        static_cast<jint>(statistics.total_extruder_changes),
        statistics.total_tool_change_time,
        int_array(env, move_counts),
        float_array(env, move_times),
        float_array(env, move_distances),
        int_array(env, extruders),
        int_array(env, extruder_listed),
        double_array(env, extruder_filament)
    );
}

JNIEXPORT void JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_destroyData(JNIEnv*, jobject, jlong data)
{
    delete reinterpret_cast<ToolpathsData*>(data);
}

JNIEXPORT jlong JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_createViewer(JNIEnv*, jobject)
{
    try {
        // GCodeViewer::init(): the viewer for the current context, showing feature types.
        auto viewer = std::make_unique<libvgcode::Viewer>();
        viewer->init(reinterpret_cast<const char*>(glGetString(GL_VERSION)));
        viewer->set_view_type(libvgcode::EViewType::FeatureType);
        return reinterpret_cast<jlong>(viewer.release());
    } catch (const std::exception& error) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Unable to initialise the G-code viewer: %s", error.what());
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_load(JNIEnv*, jobject, jlong viewer, jlong data)
{
    // GCodeViewer::load_as_gcode(): the moves slider steps through the top layer
    // only, as OrcaSlicer's seq_top_layer_only defaults to, and the viewer takes the moves.
    const std::unique_ptr<ToolpathsData> input(reinterpret_cast<ToolpathsData*>(data));
    try {
        if (!viewer_of(viewer).is_top_layer_only_view_range()) {
            viewer_of(viewer).toggle_top_layer_only_view_range();
        }
        viewer_of(viewer).reset_default_extrusion_roles_colors();
        viewer_of(viewer).load(std::move(input->input));
    } catch (const std::exception& error) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Unable to load toolpaths: %s", error.what());
    }
}

JNIEXPORT void JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_render(JNIEnv* env, jobject, jlong viewer, jfloatArray view, jfloatArray projection)
{
    // GCodeViewer::render(): depth tested, over the bed.
    glEnable(GL_DEPTH_TEST);
    try {
        viewer_of(viewer).render(to_matrix(env, view), to_matrix(env, projection));
    } catch (const std::exception& error) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Unable to draw toolpaths: %s", error.what());
    }
    glDisable(GL_DEPTH_TEST);
}

JNIEXPORT void JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_destroyViewer(JNIEnv*, jobject, jlong viewer)
{
    delete reinterpret_cast<libvgcode::Viewer*>(viewer);
}

JNIEXPORT void JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_setViewType(JNIEnv*, jobject, jlong viewer, jint type)
{
    if (type >= 0 && type < static_cast<jint>(libvgcode::EViewType::COUNT)) {
        viewer_of(viewer).set_view_type(static_cast<libvgcode::EViewType>(type));
    }
}

JNIEXPORT void JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_setLayersViewRange(JNIEnv*, jobject, jlong viewer, jint min, jint max)
{
    viewer_of(viewer).set_layers_view_range(static_cast<libvgcode::Interval::value_type>(min), static_cast<libvgcode::Interval::value_type>(max));
}

JNIEXPORT void JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_setViewVisibleRange(JNIEnv*, jobject, jlong viewer, jint first, jint last)
{
    viewer_of(viewer).set_view_visible_range(static_cast<libvgcode::Interval::value_type>(first), static_cast<libvgcode::Interval::value_type>(last));
}

JNIEXPORT void JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_toggleExtrusionRoleVisibility(JNIEnv*, jobject, jlong viewer, jint role)
{
    if (role >= 0 && role < static_cast<jint>(libvgcode::EGCodeExtrusionRole::COUNT)) {
        viewer_of(viewer).toggle_extrusion_role_visibility(static_cast<libvgcode::EGCodeExtrusionRole>(role));
    }
}

JNIEXPORT void JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_toggleOptionVisibility(JNIEnv*, jobject, jlong viewer, jint option)
{
    if (option >= 0 && option < static_cast<jint>(libvgcode::EOptionType::COUNT)) {
        viewer_of(viewer).toggle_option_visibility(static_cast<libvgcode::EOptionType>(option));
    }
}

JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_snapshot(JNIEnv* env, jobject, jlong viewer)
{
    const libvgcode::Viewer& source = viewer_of(viewer);

    const std::vector<float> zs = source.get_layers_zs();
    const libvgcode::Interval& layers_range = source.get_layers_view_range();

    std::vector<jint> roles;
    std::vector<jint> role_colors;
    std::vector<jboolean> role_visible;
    std::vector<jfloat> role_times;
    for (const libvgcode::EGCodeExtrusionRole role : source.get_extrusion_roles()) {
        roles.push_back(static_cast<jint>(role));
        role_colors.push_back(to_rgb(source.get_extrusion_role_color(role)));
        role_visible.push_back(source.is_extrusion_role_visible(role) ? JNI_TRUE : JNI_FALSE);
        role_times.push_back(source.get_extrusion_role_estimated_time(role));
    }

    std::vector<jint> options;
    std::vector<jint> option_colors;
    std::vector<jboolean> option_visible;
    for (const libvgcode::EOptionType option : source.get_options()) {
        options.push_back(static_cast<jint>(option));
        option_colors.push_back(to_rgb(source.get_option_color(option)));
        option_visible.push_back(source.is_option_visible(option) ? JNI_TRUE : JNI_FALSE);
    }

    std::vector<jfloat> range_values;
    std::vector<jint> range_palette;
    if (has_color_range(source.get_view_type())) {
        const libvgcode::ColorRange& range = source.get_color_range(source.get_view_type());
        range_values = range.get_values();
        for (const libvgcode::Color& color : range.get_palette()) {
            range_palette.push_back(to_rgb(color));
        }
    }

    // GCodeViewer::update_moves_slider(): the moves of the enabled range, one per
    // G-code line, as vertex ids; G2 and G3 lines keep their last vertex.
    const libvgcode::Interval& enabled = source.get_view_enabled_range();
    const libvgcode::Interval& visible = source.get_view_visible_range();
    std::vector<jint> moves;
    std::vector<jint> move_lines;
    if (source.get_vertices_count() > 0 && enabled[1] >= enabled[0]) {
        std::uint32_t last_gcode_id = source.get_vertex_at(enabled[0]).gcode_id;
        for (std::size_t i = enabled[0]; i <= enabled[1]; ++i) {
            const std::uint32_t gcode_id = source.get_vertex_at(i).gcode_id;
            if (i > enabled[0] && last_gcode_id == gcode_id) {
                moves.back() = static_cast<jint>(i);
                continue;
            }
            last_gcode_id = gcode_id;
            moves.push_back(static_cast<jint>(i));
            move_lines.push_back(static_cast<jint>(gcode_id));
        }
    }

    std::vector<jint> tool_colors;
    for (const libvgcode::Color& color : source.get_tool_colors()) {
        tool_colors.push_back(to_rgb(color));
    }
    std::vector<jint> used_extruders;
    for (const std::uint8_t extruder : source.get_used_extruders_ids()) {
        used_extruders.push_back(static_cast<jint>(extruder));
    }

    const jclass type = env->FindClass("app/orcinus/shadow/render/gcode/NativeToolpathsSnapshot");
    // GCodeViewer::render(): the G-code line of the current move, which the G-code window shows.
    const bool has_vertices = source.get_vertices_count() > 0;
    const jint current_line = has_vertices ? static_cast<jint>(source.get_current_vertex().gcode_id) : 0;
    // The tool marker stands at the current vertex once the visible range ends
    // before the full one (m_show_marker).
    std::vector<jfloat> marker;
    jboolean at_end = JNI_TRUE;
    std::vector<jfloat> values;
    std::vector<jint> kinds;
    if (has_vertices) {
        const libvgcode::PathVertex& current = source.get_current_vertex();
        marker = {current.position[0], current.position[1], current.position[2]};
        at_end = source.get_view_visible_range()[1] == source.get_view_full_range()[1] ? JNI_TRUE : JNI_FALSE;
        // Marker::render_position_window(): a seam's data may be arbitrary, so
        // outside the feature type view the last visible vertex before it speaks.
        libvgcode::PathVertex vertex = current;
        std::size_t vertex_id = source.get_current_vertex_id();
        if (source.get_view_type() != libvgcode::EViewType::FeatureType && vertex.type == libvgcode::EMoveType::Seam) {
            const libvgcode::Interval& visible_range = source.get_view_visible_range();
            if (visible_range[1] > 0) {
                vertex_id = static_cast<std::size_t>(visible_range[1]) - 1;
                vertex = source.get_vertex_at(vertex_id);
            }
        }
        values = {
            vertex.position[0], vertex.position[1], vertex.position[2], vertex.width, vertex.height, vertex.feedrate,
            vertex.acceleration, vertex.jerk, vertex.volumetric_rate(), vertex.fan_speed, vertex.temperature,
            vertex.pressure_advance, vertex.layer_duration, source.get_estimated_time_at(vertex_id),
            vertex.times[static_cast<std::size_t>(source.get_time_mode())],
        };
        kinds = {
            static_cast<jint>(vertex.type), static_cast<jint>(vertex.role), vertex.is_extrusion() ? 1 : 0,
            static_cast<jint>(vertex.layer_id), static_cast<jint>(vertex.extruder_id), static_cast<jint>(vertex.color_id),
        };
    }
    const jmethodID constructor = env->GetMethodID(type, "<init>", "(I[F[I[I[I[Z[F[I[I[ZFF[F[I[I[I[I[I[II[FZ[F[I)V");
    return env->NewObject(
        type,
        constructor,
        static_cast<jint>(source.get_view_type()),
        float_array(env, zs),
        int_array(env, {static_cast<jint>(layers_range[0]), static_cast<jint>(layers_range[1])}),
        int_array(env, roles),
        int_array(env, role_colors),
        boolean_array(env, role_visible),
        float_array(env, role_times),
        int_array(env, options),
        int_array(env, option_colors),
        boolean_array(env, option_visible),
        source.get_travels_estimated_time(),
        source.get_estimated_time(),
        float_array(env, range_values),
        int_array(env, range_palette),
        int_array(env, {static_cast<jint>(visible[0]), static_cast<jint>(visible[1])}),
        int_array(env, moves),
        int_array(env, move_lines),
        int_array(env, tool_colors),
        int_array(env, used_extruders),
        current_line,
        float_array(env, marker),
        at_end,
        float_array(env, values),
        int_array(env, kinds)
    );
}

// GCodeViewer::export_toolpaths_to_obj(): what the viewer shows, written to
// path, and its colours to the materials file beside it.
JNIEXPORT jboolean JNICALL
Java_app_orcinus_shadow_render_gcode_NativeToolpaths_exportToObj(JNIEnv* env, jobject, jlong viewer, jstring path)
{
    orcinus::toolpaths::ToolpathsObjExporter exporter(viewer_of(viewer));
    return exporter.export_to(to_string(env, path)) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
