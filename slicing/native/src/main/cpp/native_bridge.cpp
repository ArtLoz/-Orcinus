#include <jni.h>

#include <algorithm>
#include <cmath>
#include <limits>
#include <optional>
#include <string>
#include <vector>

#include "orca_engine_adapter.hpp"

// JNI only converts values; all engine behaviour lives behind orca_engine_adapter.hpp.

namespace {

// Java strings cross as UTF-8 bytes: JNI's "modified UTF-8" differs for
// characters outside the BMP, which can appear in document names.
std::string to_utf8(JNIEnv* env, jstring value)
{
    if (value == nullptr) {
        return {};
    }
    const jclass string_class = env->FindClass("java/lang/String");
    const jmethodID get_bytes = env->GetMethodID(string_class, "getBytes", "(Ljava/lang/String;)[B");
    const jstring charset = env->NewStringUTF("UTF-8");
    const auto bytes = static_cast<jbyteArray>(env->CallObjectMethod(value, get_bytes, charset));
    std::string result;
    if (bytes != nullptr) {
        const jsize length = env->GetArrayLength(bytes);
        result.resize(static_cast<std::size_t>(length));
        env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(result.data()));
        env->DeleteLocalRef(bytes);
    }
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(string_class);
    return result;
}

jstring to_java(JNIEnv* env, const std::string& value)
{
    const jclass string_class = env->FindClass("java/lang/String");
    const jmethodID constructor = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
    const auto length = static_cast<jsize>(value.size());
    const jbyteArray bytes = env->NewByteArray(length);
    env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte*>(value.data()));
    const jstring charset = env->NewStringUTF("UTF-8");
    const auto result = static_cast<jstring>(env->NewObject(string_class, constructor, bytes, charset));
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(string_class);
    return result;
}

jdoubleArray to_java(JNIEnv* env, const double* values, const std::size_t size)
{
    const auto length = static_cast<jsize>(size);
    const jdoubleArray array = env->NewDoubleArray(length);
    if (array != nullptr) {
        env->SetDoubleArrayRegion(array, 0, length, values);
    }
    return array;
}

jlongArray to_java(JNIEnv* env, const std::vector<std::int64_t>& values)
{
    const auto length = static_cast<jsize>(values.size());
    const jlongArray array = env->NewLongArray(length);
    if (array != nullptr) {
        env->SetLongArrayRegion(array, 0, length, reinterpret_cast<const jlong*>(values.data()));
    }
    return array;
}

jfloatArray to_java(JNIEnv* env, const std::vector<float>& values)
{
    const auto length = static_cast<jsize>(values.size());
    const jfloatArray array = env->NewFloatArray(length);
    if (array != nullptr) {
        env->SetFloatArrayRegion(array, 0, length, values.data());
    }
    return array;
}

jobjectArray to_java(JNIEnv* env, const std::vector<std::string>& values)
{
    const jclass string_class = env->FindClass("java/lang/String");
    const jobjectArray array = env->NewObjectArray(static_cast<jsize>(values.size()), string_class, nullptr);
    env->DeleteLocalRef(string_class);
    for (std::size_t index = 0; array != nullptr && index < values.size(); ++index) {
        const jstring value = to_java(env, values[index]);
        env->SetObjectArrayElement(array, static_cast<jsize>(index), value);
        env->DeleteLocalRef(value);
    }
    return array;
}

std::vector<std::string> to_strings(JNIEnv* env, jobjectArray values)
{
    std::vector<std::string> result(static_cast<std::size_t>(env->GetArrayLength(values)));
    for (std::size_t index = 0; index < result.size(); ++index) {
        const auto value = static_cast<jstring>(env->GetObjectArrayElement(values, static_cast<jsize>(index)));
        result[index] = to_utf8(env, value);
        env->DeleteLocalRef(value);
    }
    return result;
}

// An array of objects of class_name, each converted by convert() within its own local frame.
template<typename T, typename Convert>
jobjectArray to_java_objects(JNIEnv* env, const char* class_name, const std::vector<T>& values, Convert convert)
{
    const jclass element_class = env->FindClass(class_name);
    const jobjectArray array = env->NewObjectArray(static_cast<jsize>(values.size()), element_class, nullptr);
    env->DeleteLocalRef(element_class);
    for (std::size_t index = 0; array != nullptr && index < values.size(); ++index) {
        if (env->PushLocalFrame(16) != JNI_OK) {
            return nullptr;
        }
        const jobject element = env->PopLocalFrame(convert(env, values[index]));
        env->SetObjectArrayElement(array, static_cast<jsize>(index), element);
        env->DeleteLocalRef(element);
    }
    return array;
}

// NativePresetItem
jobject to_java(JNIEnv* env, const orcinus::orca::PresetItem& item)
{
    const jclass item_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePresetItem");
    const jmethodID constructor = env->GetMethodID(item_class, "<init>", "(Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;ZZ)V");
    return env->NewObject(
        item_class,
        constructor,
        to_java(env, item.name),
        to_java(env, item.label),
        static_cast<jlong>(item.group),
        to_java(env, item.subgroup),
        item.subgroup_msgid ? JNI_TRUE : JNI_FALSE,
        item.selected ? JNI_TRUE : JNI_FALSE
    );
}

// NativeUiText
jobject to_java(JNIEnv* env, const orcinus::orca::UiText& text)
{
    const jclass text_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeUiText");
    const jmethodID constructor = env->GetMethodID(text_class, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;J[Ljava/lang/String;Z)V");
    return env->NewObject(
        text_class,
        constructor,
        to_java(env, text.context),
        to_java(env, text.msgid),
        to_java(env, text.msgid_plural),
        static_cast<jlong>(text.count),
        to_java(env, text.args),
        text.translate_args ? JNI_TRUE : JNI_FALSE
    );
}

jobjectArray to_java(JNIEnv* env, const std::vector<orcinus::orca::UiText>& texts)
{
    return to_java_objects(env, "app/orcinus/shadow/slicing/nativebridge/NativeUiText", texts, [](JNIEnv* text_env, const orcinus::orca::UiText& text) {
        return to_java(text_env, text);
    });
}

// NativePresetChange
jobject to_java(JNIEnv* env, const orcinus::orca::PresetChange& change)
{
    const jclass change_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePresetChange");
    const jmethodID constructor = env->GetMethodID(
        change_class,
        "<init>",
        "(Ljava/lang/String;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;)V"
    );
    return env->NewObject(
        change_class,
        constructor,
        to_java(env, change.id),
        to_java(env, change.category),
        to_java(env, change.group),
        to_java(env, change.label),
        to_java(env, change.old_value),
        to_java(env, change.new_value)
    );
}

// NativePresetState
jobject to_java(JNIEnv* env, const orcinus::orca::PresetState& state)
{
    const char* item_class = "app/orcinus/shadow/slicing/nativebridge/NativePresetItem";
    const auto item = [](JNIEnv* item_env, const orcinus::orca::PresetItem& value) { return to_java(item_env, value); };
    const jclass state_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePresetState");
    const jmethodID constructor = env->GetMethodID(
        state_class,
        "<init>",
        "(JLjava/lang/String;ZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;"
        "[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativePresetItem;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativePresetItem;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativePresetItem;"
        "[Ljava/lang/String;Ljava/lang/String;ZJ"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativePresetChange;ZLjava/lang/String;Z)V"
    );
    return env->NewObject(
        state_class,
        constructor,
        static_cast<jlong>(state.status),
        to_java(env, state.message),
        state.setup_required ? JNI_TRUE : JNI_FALSE,
        to_java(env, state.selection.printer),
        to_java(env, state.selection.filament),
        to_java(env, state.selection.process),
        to_java(env, state.selection.filaments),
        to_java(env, state.filament_colors),
        to_java(env, state.filament_types),
        to_java(env, state.filament_display_types),
        to_java(env, state.bed_type_values),
        to_java(env, state.bed_type_labels),
        to_java_objects(env, item_class, state.printers, item),
        to_java_objects(env, item_class, state.filaments, item),
        to_java_objects(env, item_class, state.processes, item),
        to_java(env, state.nozzle_diameters),
        to_java(env, state.nozzle_diameter),
        state.asks_unsaved_changes ? JNI_TRUE : JNI_FALSE,
        static_cast<jlong>(state.changed_kind),
        to_java_objects(
            env,
            "app/orcinus/shadow/slicing/nativebridge/NativePresetChange",
            state.unsaved_changes,
            [](JNIEnv* change_env, const orcinus::orca::PresetChange& change) { return to_java(change_env, change); }
        ),
        state.can_transfer ? JNI_TRUE : JNI_FALSE,
        to_java(env, state.save_name),
        state.save_name_copy_suffix ? JNI_TRUE : JNI_FALSE
    );
}

orcinus::orca::ProfileSelection to_profiles(
    JNIEnv* env,
    jstring printer,
    jstring filament,
    jstring process,
    jobjectArray filaments = nullptr
)
{
    orcinus::orca::ProfileSelection profiles;
    profiles.printer = to_utf8(env, printer);
    profiles.filament = to_utf8(env, filament);
    profiles.process = to_utf8(env, process);
    // Every filament of the plate, in the order the sidebar lists them.
    if (filaments != nullptr) {
        profiles.filaments = to_strings(env, filaments);
    }
    return profiles;
}

// Forwards Orca's status updates to a Kotlin NativeProgressListener. Orca may
// report from worker threads, which are attached to the VM as daemons.
class ProgressForwarder final {
public:
    ProgressForwarder(JNIEnv* env, jobject listener)
    {
        env->GetJavaVM(&vm_);
        listener_ = env->NewGlobalRef(listener);
        const jclass listener_class = env->GetObjectClass(listener);
        on_progress_ = env->GetMethodID(listener_class, "onProgress", "(ILjava/lang/String;)V");
        env->DeleteLocalRef(listener_class);
    }

    ProgressForwarder(const ProgressForwarder&) = delete;
    ProgressForwarder& operator=(const ProgressForwarder&) = delete;

    ~ProgressForwarder()
    {
        if (JNIEnv* env = attached_env()) {
            env->DeleteGlobalRef(listener_);
        }
    }

    void operator()(const int percent, const std::string& message) const
    {
        JNIEnv* env = attached_env();
        if (env == nullptr || on_progress_ == nullptr || env->PushLocalFrame(4) != JNI_OK) {
            return;
        }
        env->CallVoidMethod(listener_, on_progress_, static_cast<jint>(percent), to_java(env, message));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        env->PopLocalFrame(nullptr);
    }

private:
    JNIEnv* attached_env() const
    {
        JNIEnv* env = nullptr;
        if (vm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED
            && vm_->AttachCurrentThreadAsDaemon(&env, nullptr) != JNI_OK) {
            return nullptr;
        }
        return env;
    }

    JavaVM* vm_{nullptr};
    jobject listener_{nullptr};
    jmethodID on_progress_{nullptr};
};

std::vector<double> to_doubles(JNIEnv* env, jdoubleArray values)
{
    std::vector<double> result(static_cast<std::size_t>(env->GetArrayLength(values)));
    env->GetDoubleArrayRegion(values, 0, static_cast<jsize>(result.size()), result.data());
    return result;
}

std::vector<std::int32_t> to_ints(JNIEnv* env, jintArray values)
{
    std::vector<std::int32_t> result(static_cast<std::size_t>(env->GetArrayLength(values)));
    env->GetIntArrayRegion(values, 0, static_cast<jsize>(result.size()), reinterpret_cast<jint*>(result.data()));
    return result;
}

// CutConnectorData flattened: position, radius, height, radius and height
// tolerances and the turn, eight values each; type, style and shape, three each.
std::vector<orcinus::orca::CutConnectorData> to_connectors(JNIEnv* env, jdoubleArray values, jintArray kinds)
{
    const std::vector<double> numbers = to_doubles(env, values);
    const std::vector<std::int32_t> types = to_ints(env, kinds);
    std::vector<orcinus::orca::CutConnectorData> connectors;
    for (std::size_t index = 0; index * 8 + 7 < numbers.size() && index * 3 + 2 < types.size(); ++index) {
        orcinus::orca::CutConnectorData connector;
        const double* value = &numbers[index * 8];
        std::copy(value, value + 3, connector.position);
        connector.radius = value[3];
        connector.height = value[4];
        connector.radius_tolerance = value[5];
        connector.height_tolerance = value[6];
        connector.z_angle = value[7];
        connector.type = types[index * 3];
        connector.style = types[index * 3 + 1];
        connector.shape = types[index * 3 + 2];
        connectors.push_back(connector);
    }
    return connectors;
}

// CutGroove flattened: depth, width, flaps angle, angle, the two tolerances, count and gap.
orcinus::orca::CutGroove to_groove(JNIEnv* env, jdoubleArray values)
{
    const std::vector<double> numbers = to_doubles(env, values);
    orcinus::orca::CutGroove groove;
    if (numbers.size() == 8) {
        groove.depth = numbers[0];
        groove.width = numbers[1];
        groove.flaps_angle = numbers[2];
        groove.angle = numbers[3];
        groove.depth_tolerance = numbers[4];
        groove.width_tolerance = numbers[5];
        groove.count = static_cast<int>(numbers[6]);
        groove.gap = numbers[7];
    }
    return groove;
}

std::vector<std::int64_t> to_longs(JNIEnv* env, jlongArray values)
{
    std::vector<std::int64_t> result(static_cast<std::size_t>(env->GetArrayLength(values)));
    env->GetLongArrayRegion(values, 0, static_cast<jsize>(result.size()), reinterpret_cast<jlong*>(result.data()));
    return result;
}

std::vector<bool> to_bools(JNIEnv* env, jbooleanArray values)
{
    std::vector<jboolean> flags(static_cast<std::size_t>(env->GetArrayLength(values)));
    env->GetBooleanArrayRegion(values, 0, static_cast<jsize>(flags.size()), flags.data());
    std::vector<bool> result;
    result.reserve(flags.size());
    for (const jboolean flag : flags) {
        result.push_back(flag == JNI_TRUE);
    }
    return result;
}

// The settings an object or the plate overrides, as parallel arrays of keys and
// values.
orcinus::orca::ModelSettings to_model_settings(JNIEnv* env, jobjectArray keys, jobjectArray values)
{
    orcinus::orca::ModelSettings settings;
    settings.keys = to_strings(env, keys);
    settings.values = to_strings(env, values);
    return settings;
}

// The settings a request of an object's or the plate's settings carries: the
// ones of every selected object, keys and values per object, and the ones of
// the plate.
orcinus::orca::ModelSettingsRequest to_model_request(
    JNIEnv* env,
    jobjectArray model_keys,
    jobjectArray model_values,
    jobjectArray plate_keys,
    jobjectArray plate_values,
    jobjectArray parent_keys,
    jobjectArray parent_values
)
{
    orcinus::orca::ModelSettingsRequest request;
    const std::size_t count = model_keys == nullptr || model_values == nullptr
                                  ? 0
                                  : std::min(static_cast<std::size_t>(env->GetArrayLength(model_keys)),
                                             static_cast<std::size_t>(env->GetArrayLength(model_values)));
    for (std::size_t index = 0; index < count; ++index) {
        const auto keys = static_cast<jobjectArray>(env->GetObjectArrayElement(model_keys, static_cast<jsize>(index)));
        const auto values = static_cast<jobjectArray>(env->GetObjectArrayElement(model_values, static_cast<jsize>(index)));
        request.settings.push_back(to_model_settings(env, keys, values));
        env->DeleteLocalRef(keys);
        env->DeleteLocalRef(values);
    }
    request.plate = to_model_settings(env, plate_keys, plate_values);
    request.parent = to_model_settings(env, parent_keys, parent_values);
    return request;
}

// The settings of every object the request answered with, as String[][].
jobjectArray to_java(JNIEnv* env, const std::vector<orcinus::orca::ModelSettings>& settings, const bool keys)
{
    const jclass array_class = env->FindClass("[Ljava/lang/String;");
    const jobjectArray result = env->NewObjectArray(static_cast<jsize>(settings.size()), array_class, nullptr);
    for (std::size_t index = 0; index < settings.size(); ++index) {
        const jobjectArray entry = to_java(env, keys ? settings[index].keys : settings[index].values);
        env->SetObjectArrayElement(result, static_cast<jsize>(index), entry);
        env->DeleteLocalRef(entry);
    }
    return result;
}

// The objects of the plate: a model path and the number of copies per object,
// and the transformation, auto drop and printable flag of every copy in turn.
std::vector<orcinus::orca::PlateObject> to_plate(
    JNIEnv* env,
    jobjectArray model_paths,
    jintArray instance_counts,
    jdoubleArray placements,
    jbooleanArray auto_drops,
    jbooleanArray printables,
    // The settings of every object, keys and values per object; null for a
    // request that does not slice.
    jobjectArray object_keys = nullptr,
    jobjectArray object_values = nullptr,
    // The parts of every object: the shape and type of each, its
    // transformation, and how many parts each object has.
    jintArray part_counts = nullptr,
    jobjectArray part_shapes = nullptr,
    jlongArray part_types = nullptr,
    jdoubleArray part_matrices = nullptr,
    // The settings of every part, keys and values per part, in the plate's order.
    jobjectArray part_keys = nullptr,
    jobjectArray part_values = nullptr,
    // The facets painted with the filaments of the plate, one per object and
    // one per part; null for a request that does not slice.
    jobjectArray painted = nullptr,
    jobjectArray part_painted = nullptr,
    // The height ranges of every object: how many each has, the heights they
    // span, and the settings of each.
    jintArray range_counts = nullptr,
    jdoubleArray range_heights = nullptr,
    jobjectArray range_keys = nullptr,
    jobjectArray range_values = nullptr
)
{
    const std::vector<double> matrices = to_doubles(env, placements);
    const std::vector<bool> drops = to_bools(env, auto_drops);
    const std::vector<bool> printed = printables != nullptr ? to_bools(env, printables) : std::vector<bool>();
    const std::vector<std::int32_t> counts = to_ints(env, instance_counts);
    const std::size_t count = std::min(static_cast<std::size_t>(env->GetArrayLength(model_paths)), counts.size());
    std::vector<orcinus::orca::PlateObject> plate(count);
    std::size_t instance = 0;
    for (std::size_t index = 0; index < count; ++index) {
        const auto path = static_cast<jstring>(env->GetObjectArrayElement(model_paths, static_cast<jsize>(index)));
        plate[index].model_path = to_utf8(env, path);
        env->DeleteLocalRef(path);
        for (std::int32_t copy = 0; copy < counts[index] && 16 * (instance + 1) <= matrices.size(); ++copy, ++instance) {
            orcinus::orca::ObjectPlacement& placement = plate[index].instances.emplace_back();
            const auto first = matrices.begin() + static_cast<std::ptrdiff_t>(16 * instance);
            placement.matrix.assign(first, first + 16);
            placement.auto_drop = instance < drops.size() ? drops[instance] : true;
            placement.printable = instance < printed.size() ? printed[instance] : true;
        }
        if (object_keys == nullptr || object_values == nullptr || static_cast<std::size_t>(env->GetArrayLength(object_keys)) <= index
            || static_cast<std::size_t>(env->GetArrayLength(object_values)) <= index) {
            continue;
        }
        const auto keys = static_cast<jobjectArray>(env->GetObjectArrayElement(object_keys, static_cast<jsize>(index)));
        const auto values = static_cast<jobjectArray>(env->GetObjectArrayElement(object_values, static_cast<jsize>(index)));
        plate[index].settings = to_model_settings(env, keys, values);
        env->DeleteLocalRef(keys);
        env->DeleteLocalRef(values);
    }
    // The colours every object is painted with (GLGizmoMmuSegmentation).
    if (painted != nullptr) {
        const std::vector<std::string> facets = to_strings(env, painted);
        for (std::size_t index = 0; index < plate.size() && index < facets.size(); ++index) {
            plate[index].painted = facets[index];
        }
    }
    // The parts of every object, in the plate's order.
    if (part_counts != nullptr && part_shapes != nullptr && part_types != nullptr && part_matrices != nullptr) {
        const std::vector<std::int32_t> counts_of_parts = to_ints(env, part_counts);
        const std::vector<std::string> shapes = to_strings(env, part_shapes);
        const std::vector<double> matrices_of_parts = to_doubles(env, part_matrices);
        std::vector<jlong> types(static_cast<std::size_t>(env->GetArrayLength(part_types)));
        env->GetLongArrayRegion(part_types, 0, static_cast<jsize>(types.size()), types.data());
        std::size_t part = 0;
        for (std::size_t index = 0; index < plate.size() && index < counts_of_parts.size(); ++index) {
            for (std::int32_t at = 0; at < counts_of_parts[index] && part < shapes.size() && 16 * (part + 1) <= matrices_of_parts.size(); ++at, ++part) {
                orcinus::orca::ObjectPart& added = plate[index].parts.emplace_back();
                added.shape = shapes[part];
                added.type = static_cast<orcinus::orca::VolumeType>(part < types.size() ? types[part] : 0);
                const auto first = matrices_of_parts.begin() + static_cast<std::ptrdiff_t>(16 * part);
                added.matrix.assign(first, first + 16);
                if (part_keys != nullptr && part_values != nullptr && static_cast<std::size_t>(env->GetArrayLength(part_keys)) > part
                    && static_cast<std::size_t>(env->GetArrayLength(part_values)) > part) {
                    const auto keys = static_cast<jobjectArray>(env->GetObjectArrayElement(part_keys, static_cast<jsize>(part)));
                    const auto values = static_cast<jobjectArray>(env->GetObjectArrayElement(part_values, static_cast<jsize>(part)));
                    added.settings = to_model_settings(env, keys, values);
                    env->DeleteLocalRef(keys);
                    env->DeleteLocalRef(values);
                }
                if (part_painted != nullptr && static_cast<std::size_t>(env->GetArrayLength(part_painted)) > part) {
                    const auto facets = static_cast<jstring>(env->GetObjectArrayElement(part_painted, static_cast<jsize>(part)));
                    added.painted = to_utf8(env, facets);
                    env->DeleteLocalRef(facets);
                }
            }
        }
    }
    // The height ranges of every object, in the plate's order; the heights come
    // as a bottom and a top per range.
    if (range_counts != nullptr && range_heights != nullptr) {
        const std::vector<std::int32_t> counts_of_ranges = to_ints(env, range_counts);
        const std::vector<double> heights = to_doubles(env, range_heights);
        std::size_t range = 0;
        for (std::size_t index = 0; index < plate.size() && index < counts_of_ranges.size(); ++index) {
            for (std::int32_t at = 0; at < counts_of_ranges[index] && 2 * (range + 1) <= heights.size(); ++at, ++range) {
                orcinus::orca::LayerRange& added = plate[index].layer_ranges.emplace_back();
                added.bottom = heights[2 * range];
                added.top = heights[2 * range + 1];
                if (range_keys != nullptr && range_values != nullptr && static_cast<std::size_t>(env->GetArrayLength(range_keys)) > range
                    && static_cast<std::size_t>(env->GetArrayLength(range_values)) > range) {
                    const auto keys = static_cast<jobjectArray>(env->GetObjectArrayElement(range_keys, static_cast<jsize>(range)));
                    const auto values = static_cast<jobjectArray>(env->GetObjectArrayElement(range_values, static_cast<jsize>(range)));
                    added.settings = to_model_settings(env, keys, values);
                    env->DeleteLocalRef(keys);
                    env->DeleteLocalRef(values);
                }
            }
        }
    }
    return plate;
}

// The objects of a plate as NativePlate carries them (NativeBindings.kt): the
// parallel arrays above, then the frame of every object and the mesh file and
// name of every part.
std::vector<orcinus::orca::PlateObject> to_plate(JNIEnv* env, jobject native_plate)
{
    const jclass plate_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePlate");
    const auto field = [env, native_plate, plate_class](const char* name, const char* signature) {
        return env->GetObjectField(native_plate, env->GetFieldID(plate_class, name, signature));
    };
    constexpr const char* strings = "[Ljava/lang/String;";
    constexpr const char* string_tables = "[[Ljava/lang/String;";
    std::vector<orcinus::orca::PlateObject> plate = to_plate(
        env,
        static_cast<jobjectArray>(field("modelPaths", strings)),
        static_cast<jintArray>(field("instanceCounts", "[I")),
        static_cast<jdoubleArray>(field("placements", "[D")),
        static_cast<jbooleanArray>(field("autoDrops", "[Z")),
        static_cast<jbooleanArray>(field("printable", "[Z")),
        static_cast<jobjectArray>(field("settingKeys", string_tables)),
        static_cast<jobjectArray>(field("settingValues", string_tables)),
        static_cast<jintArray>(field("partCounts", "[I")),
        static_cast<jobjectArray>(field("partShapes", strings)),
        static_cast<jlongArray>(field("partTypes", "[J")),
        static_cast<jdoubleArray>(field("partMatrices", "[D")),
        static_cast<jobjectArray>(field("partSettingKeys", string_tables)),
        static_cast<jobjectArray>(field("partSettingValues", string_tables)),
        static_cast<jobjectArray>(field("painted", strings)),
        static_cast<jobjectArray>(field("partPainted", strings)),
        static_cast<jintArray>(field("rangeCounts", "[I")),
        static_cast<jdoubleArray>(field("rangeHeights", "[D")),
        static_cast<jobjectArray>(field("rangeSettingKeys", string_tables)),
        static_cast<jobjectArray>(field("rangeSettingValues", string_tables))
    );
    // The frame of every object: 16 elements each, all zero for an object
    // centred around the origin (no transformation has a zero last row).
    const std::vector<double> frames = to_doubles(env, static_cast<jdoubleArray>(field("objectMatrices", "[D")));
    for (std::size_t index = 0; index < plate.size() && 16 * (index + 1) <= frames.size(); ++index) {
        const auto first = frames.begin() + static_cast<std::ptrdiff_t>(16 * index);
        if (std::any_of(first, first + 16, [](const double value) { return value != 0.0; })) {
            plate[index].matrix.assign(first, first + 16);
        }
    }
    // The settings of every object's own mesh (its first ModelVolume).
    const auto volume_keys = static_cast<jobjectArray>(field("volumeSettingKeys", string_tables));
    const auto volume_values = static_cast<jobjectArray>(field("volumeSettingValues", string_tables));
    for (std::size_t index = 0; index < plate.size() && static_cast<std::size_t>(env->GetArrayLength(volume_keys)) > index
                                && static_cast<std::size_t>(env->GetArrayLength(volume_values)) > index;
         ++index) {
        const auto keys = static_cast<jobjectArray>(env->GetObjectArrayElement(volume_keys, static_cast<jsize>(index)));
        const auto values = static_cast<jobjectArray>(env->GetObjectArrayElement(volume_values, static_cast<jsize>(index)));
        plate[index].volume_settings = to_model_settings(env, keys, values);
        env->DeleteLocalRef(keys);
        env->DeleteLocalRef(values);
    }
    // The names of every object and of its own mesh.
    const std::vector<std::string> object_names = to_strings(env, static_cast<jobjectArray>(field("names", strings)));
    const std::vector<std::string> volume_names = to_strings(env, static_cast<jobjectArray>(field("volumeNames", strings)));
    for (std::size_t index = 0; index < plate.size(); ++index) {
        if (index < object_names.size()) {
            plate[index].name = object_names[index];
        }
        if (index < volume_names.size()) {
            plate[index].volume_name = volume_names[index];
        }
    }
    // The units every object's own mesh and every part were converted from.
    const std::vector<bool> volume_inches = to_bools(env, static_cast<jbooleanArray>(field("volumeFromInches", "[Z")));
    const std::vector<bool> volume_meters = to_bools(env, static_cast<jbooleanArray>(field("volumeFromMeters", "[Z")));
    for (std::size_t index = 0; index < plate.size(); ++index) {
        plate[index].volume_from_inches = index < volume_inches.size() && volume_inches[index];
        plate[index].volume_from_meters = index < volume_meters.size() && volume_meters[index];
    }
    const std::vector<bool> part_inches = to_bools(env, static_cast<jbooleanArray>(field("partFromInches", "[Z")));
    const std::vector<bool> part_meters = to_bools(env, static_cast<jbooleanArray>(field("partFromMeters", "[Z")));
    // The files every object's own mesh and every part were read from.
    const std::vector<std::string> volume_inputs = to_strings(env, static_cast<jobjectArray>(field("volumeInputFiles", strings)));
    for (std::size_t index = 0; index < plate.size() && index < volume_inputs.size(); ++index) {
        plate[index].volume_input_file = volume_inputs[index];
    }
    const std::vector<std::string> part_inputs = to_strings(env, static_cast<jobjectArray>(field("partInputFiles", strings)));
    // The cut every object is a part of (three each), and the cut info of its
    // own mesh and of every part (six each, as VolumeCutInfo lists them).
    const std::vector<std::int64_t> cut_ids = to_longs(env, static_cast<jlongArray>(field("cutIds", "[J")));
    const std::vector<double> volume_cut_info = to_doubles(env, static_cast<jdoubleArray>(field("volumeCutInfo", "[D")));
    const std::vector<double> part_cut_info = to_doubles(env, static_cast<jdoubleArray>(field("partCutInfo", "[D")));
    const auto cut_info_at = [](const std::vector<double>& values, const std::size_t index) {
        orcinus::orca::VolumeCutInfo info;
        if (6 * (index + 1) <= values.size()) {
            const double* value = values.data() + 6 * index;
            info.from_upper = value[0] != 0.0;
            info.connector = value[1] != 0.0;
            info.processed = value[2] != 0.0;
            info.connector_type = int(value[3]);
            info.radius_tolerance = value[4];
            info.height_tolerance = value[5];
        }
        return info;
    };
    for (std::size_t index = 0; index < plate.size(); ++index) {
        if (3 * (index + 1) <= cut_ids.size()) {
            plate[index].cut_id.id = std::uint64_t(cut_ids[3 * index]);
            plate[index].cut_id.check_sum = std::uint64_t(cut_ids[3 * index + 1]);
            plate[index].cut_id.connectors_cnt = std::uint64_t(cut_ids[3 * index + 2]);
        }
        plate[index].volume_cut_info = cut_info_at(volume_cut_info, index);
    }
    // Where every own mesh and every part was in its file, five each (VolumeOrigin).
    const std::vector<double> volume_origins = to_doubles(env, static_cast<jdoubleArray>(field("volumeOrigins", "[D")));
    const std::vector<double> part_origins = to_doubles(env, static_cast<jdoubleArray>(field("partOrigins", "[D")));
    const auto origin_at = [](const std::vector<double>& values, const std::size_t index) {
        orcinus::orca::VolumeOrigin origin;
        if (5 * (index + 1) <= values.size()) {
            const double* value = values.data() + 5 * index;
            origin.object_idx = int(value[0]);
            origin.volume_idx = int(value[1]);
            origin.mesh_offset = {value[2], value[3], value[4]};
        }
        return origin;
    };
    for (std::size_t index = 0; index < plate.size(); ++index) {
        plate[index].volume_origin = origin_at(volume_origins, index);
    }
    // The mesh file and the name of every part, in the plate's order.
    const std::vector<std::string> sources = to_strings(env, static_cast<jobjectArray>(field("partSources", strings)));
    const std::vector<std::string> names = to_strings(env, static_cast<jobjectArray>(field("partNames", strings)));
    std::size_t part = 0;
    for (orcinus::orca::PlateObject& object : plate) {
        for (orcinus::orca::ObjectPart& added : object.parts) {
            if (part < sources.size()) {
                added.model_path = sources[part];
            }
            if (part < names.size()) {
                added.name = names[part];
            }
            added.from_inches = part < part_inches.size() && part_inches[part];
            added.from_meters = part < part_meters.size() && part_meters[part];
            if (part < part_inputs.size()) {
                added.input_file = part_inputs[part];
            }
            added.cut_info = cut_info_at(part_cut_info, part);
            added.origin = origin_at(part_origins, part);
            ++part;
        }
    }
    // The text or the SVG every object's own mesh and every part was embossed from.
    const std::vector<std::string> volume_emboss = to_strings(env, static_cast<jobjectArray>(field("volumeEmboss", strings)));
    for (std::size_t index = 0; index < plate.size() && index < volume_emboss.size(); ++index) {
        plate[index].volume_emboss = volume_emboss[index];
    }
    const std::vector<std::string> part_emboss = to_strings(env, static_cast<jobjectArray>(field("partEmboss", strings)));
    std::size_t embossed = 0;
    for (orcinus::orca::PlateObject& object : plate) {
        for (orcinus::orca::ObjectPart& added : object.parts) {
            if (embossed < part_emboss.size()) {
                added.emboss = part_emboss[embossed];
            }
            ++embossed;
        }
    }
    // The variable layer height of every object.
    if (const auto profiles = static_cast<jobjectArray>(field("layerHeightProfiles", "[[D"))) {
        for (std::size_t index = 0; index < plate.size() && static_cast<std::size_t>(env->GetArrayLength(profiles)) > index; ++index) {
            const auto profile = static_cast<jdoubleArray>(env->GetObjectArrayElement(profiles, static_cast<jsize>(index)));
            plate[index].layer_height_profile = to_doubles(env, profile);
            env->DeleteLocalRef(profile);
        }
    }
    // Every copy's place in the assembly view and offset to it, in the copies' order.
    {
        const auto assemble_array = static_cast<jdoubleArray>(field("assembleMatrices", "[D"));
        const auto assembled_array = static_cast<jbooleanArray>(field("assembled", "[Z"));
        const auto offsets_array = static_cast<jdoubleArray>(field("offsetsToAssembly", "[D"));
        const std::vector<double> matrices = assemble_array != nullptr ? to_doubles(env, assemble_array) : std::vector<double>{};
        const std::vector<double> offsets = offsets_array != nullptr ? to_doubles(env, offsets_array) : std::vector<double>{};
        std::vector<jboolean> flags;
        if (assembled_array != nullptr) {
            flags.resize(static_cast<std::size_t>(env->GetArrayLength(assembled_array)));
            env->GetBooleanArrayRegion(assembled_array, 0, static_cast<jsize>(flags.size()), flags.data());
        }
        std::size_t copy = 0;
        for (orcinus::orca::PlateObject& object : plate) {
            for (orcinus::orca::ObjectPlacement& placement : object.instances) {
                if (copy < flags.size() && flags[copy] == JNI_TRUE && matrices.size() >= 16 * (copy + 1))
                    placement.assemble_matrix.assign(matrices.begin() + std::ptrdiff_t(16 * copy), matrices.begin() + std::ptrdiff_t(16 * (copy + 1)));
                if (offsets.size() >= 3 * (copy + 1)) {
                    const std::vector<double> offset(offsets.begin() + std::ptrdiff_t(3 * copy), offsets.begin() + std::ptrdiff_t(3 * (copy + 1)));
                    if (offset[0] != 0.0 || offset[1] != 0.0 || offset[2] != 0.0)
                        placement.offset_to_assembly = offset;
                }
                ++copy;
            }
        }
    }
    // The brim ears of every object.
    if (const auto points = static_cast<jobjectArray>(field("brimPoints", "[[D"))) {
        for (std::size_t index = 0; index < plate.size() && static_cast<std::size_t>(env->GetArrayLength(points)) > index; ++index) {
            const auto values = static_cast<jdoubleArray>(env->GetObjectArrayElement(points, static_cast<jsize>(index)));
            plate[index].brim_points = to_doubles(env, values);
            env->DeleteLocalRef(values);
        }
    }
    env->DeleteLocalRef(plate_class);
    return plate;
}

// NativeModelInspection
jobject to_java(JNIEnv* env, const orcinus::orca::ModelInspection& inspection)
{
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeModelInspection");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;JJDDD[DJ[DD[D[D[D)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(inspection.status),
        to_java(env, inspection.message),
        static_cast<jlong>(inspection.facet_count),
        static_cast<jlong>(inspection.open_edges),
        static_cast<jdouble>(inspection.size_x),
        static_cast<jdouble>(inspection.size_y),
        static_cast<jdouble>(inspection.size_z),
        to_java(env, inspection.instance_matrix.data(), inspection.instance_matrix.size()),
        static_cast<jlong>(inspection.volume_state),
        to_java(env, inspection.sphere_center.data(), inspection.sphere_center.size()),
        static_cast<jdouble>(inspection.sphere_radius),
        to_java(env, inspection.rotation_degrees.data(), inspection.rotation_degrees.size()),
        to_java(env, inspection.unscaled_size.data(), inspection.unscaled_size.size()),
        to_java(env, inspection.box_center.data(), inspection.box_center.size())
    );
}

// NativeSettingsDialog
jobject to_java(JNIEnv* env, const orcinus::orca::SettingsDialog& dialog)
{
    const jclass dialog_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSettingsDialog");
    const jmethodID constructor = env->GetMethodID(
        dialog_class,
        "<init>",
        "(Ljava/lang/String;J"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;"
        "Z"
        "Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;"
        "Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;"
        "Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;Z)V"
    );
    return env->NewObject(
        dialog_class,
        constructor,
        to_java(env, dialog.id),
        static_cast<jlong>(dialog.icon),
        to_java(env, dialog.title),
        to_java(env, dialog.text),
        dialog.question ? JNI_TRUE : JNI_FALSE,
        to_java(env, dialog.yes),
        to_java(env, dialog.no),
        to_java(env, dialog.checkbox),
        dialog.checked ? JNI_TRUE : JNI_FALSE
    );
}

// NativeSettingsLineOption
jobject to_java(JNIEnv* env, const orcinus::orca::SettingsLineOption& option)
{
    const jclass option_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSettingsLineOption");
    const jmethodID constructor = env->GetMethodID(option_class, "<init>", "(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;ZZZIZ)V");
    return env->NewObject(
        option_class,
        constructor,
        to_java(env, option.id),
        to_java(env, option.key),
        static_cast<jint>(option.index),
        to_java(env, option.label),
        option.full_width ? JNI_TRUE : JNI_FALSE,
        option.is_code ? JNI_TRUE : JNI_FALSE,
        option.multiline ? JNI_TRUE : JNI_FALSE,
        static_cast<jint>(option.height),
        option.edit_custom_gcode ? JNI_TRUE : JNI_FALSE
    );
}

// NativeSettingsLine
jobject to_java(JNIEnv* env, const orcinus::orca::SettingsLine& line)
{
    const jclass line_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSettingsLine");
    const jmethodID constructor = env->GetMethodID(
        line_class,
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;ZJZ[Lapp/orcinus/shadow/slicing/nativebridge/NativeSettingsLineOption;)V"
    );
    return env->NewObject(
        line_class,
        constructor,
        to_java(env, line.label),
        to_java(env, line.tooltip),
        line.separator ? JNI_TRUE : JNI_FALSE,
        static_cast<jlong>(line.widget),
        line.has_override ? JNI_TRUE : JNI_FALSE,
        to_java_objects(env, "app/orcinus/shadow/slicing/nativebridge/NativeSettingsLineOption", line.options,
                        [](JNIEnv* option_env, const orcinus::orca::SettingsLineOption& option) { return to_java(option_env, option); })
    );
}

// NativeSettingsGroup
jobject to_java(JNIEnv* env, const orcinus::orca::SettingsGroup& group)
{
    const jclass group_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSettingsGroup");
    const jmethodID constructor = env->GetMethodID(
        group_class,
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;[Lapp/orcinus/shadow/slicing/nativebridge/NativeSettingsLine;)V"
    );
    return env->NewObject(
        group_class,
        constructor,
        to_java(env, group.title),
        to_java(env, group.icon),
        to_java_objects(env, "app/orcinus/shadow/slicing/nativebridge/NativeSettingsLine", group.lines,
                        [](JNIEnv* line_env, const orcinus::orca::SettingsLine& line) { return to_java(line_env, line); })
    );
}

// NativeSettingsPage
jobject to_java(JNIEnv* env, const orcinus::orca::SettingsPage& page)
{
    const jclass page_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSettingsPage");
    const jmethodID constructor = env->GetMethodID(
        page_class,
        "<init>",
        "(Ljava/lang/String;[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;Ljava/lang/String;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeSettingsGroup;)V"
    );
    return env->NewObject(
        page_class,
        constructor,
        to_java(env, page.title),
        to_java(env, page.label),
        to_java(env, page.icon),
        to_java_objects(env, "app/orcinus/shadow/slicing/nativebridge/NativeSettingsGroup", page.groups,
                        [](JNIEnv* group_env, const orcinus::orca::SettingsGroup& group) { return to_java(group_env, group); })
    );
}

// NativeSettingState
jobject to_java(JNIEnv* env, const orcinus::orca::SettingState& state)
{
    const jclass state_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSettingState");
    const jmethodID constructor = env->GetMethodID(
        state_class,
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZZZ[Ljava/lang/String;[Ljava/lang/String;ZZZZ[Ljava/lang/String;)V"
    );
    return env->NewObject(
        state_class,
        constructor,
        to_java(env, state.id),
        to_java(env, state.key),
        to_java(env, state.value),
        state.modified ? JNI_TRUE : JNI_FALSE,
        state.system ? JNI_TRUE : JNI_FALSE,
        state.enabled ? JNI_TRUE : JNI_FALSE,
        state.visible ? JNI_TRUE : JNI_FALSE,
        state.has_choices ? JNI_TRUE : JNI_FALSE,
        to_java(env, state.choice_values),
        to_java(env, state.choice_labels),
        state.nullable ? JNI_TRUE : JNI_FALSE,
        state.is_nil ? JNI_TRUE : JNI_FALSE,
        state.mixed ? JNI_TRUE : JNI_FALSE,
        state.override_enabled ? JNI_TRUE : JNI_FALSE,
        to_java(env, state.list_values)
    );
}

// NativePresetSettings
jobject to_java(JNIEnv* env, const orcinus::orca::PresetSettings& settings)
{
    const char* dialog_class_name = "app/orcinus/shadow/slicing/nativebridge/NativeSettingsDialog";
    const jclass settings_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePresetSettings");
    const jmethodID constructor = env->GetMethodID(
        settings_class,
        "<init>",
        "(JLjava/lang/String;JLjava/lang/String;Ljava/lang/String;ZZZZZJ"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeSettingsPage;"
        "Ljava/lang/String;"
        "[Ljava/lang/String;J"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeSettingState;"
        "Ljava/lang/String;Z"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeSettingsDialog;"
        "Z"
        "Lapp/orcinus/shadow/slicing/nativebridge/NativeSettingsDialog;"
        "ZZ[[Ljava/lang/String;[[Ljava/lang/String;)V"
    );
    return env->NewObject(
        settings_class,
        constructor,
        static_cast<jlong>(settings.status),
        to_java(env, settings.message),
        static_cast<jlong>(settings.kind),
        to_java(env, settings.preset),
        to_java(env, settings.label),
        settings.dirty ? JNI_TRUE : JNI_FALSE,
        settings.is_default ? JNI_TRUE : JNI_FALSE,
        settings.is_system ? JNI_TRUE : JNI_FALSE,
        settings.has_parent ? JNI_TRUE : JNI_FALSE,
        settings.can_delete ? JNI_TRUE : JNI_FALSE,
        static_cast<jlong>(settings.mode),
        to_java_objects(env, "app/orcinus/shadow/slicing/nativebridge/NativeSettingsPage", settings.pages,
                        [](JNIEnv* page_env, const orcinus::orca::SettingsPage& page) { return to_java(page_env, page); }),
        to_java(env, settings.active_page),
        to_java(env, settings.variants),
        static_cast<jlong>(settings.variant),
        to_java_objects(env, "app/orcinus/shadow/slicing/nativebridge/NativeSettingState", settings.settings, [](JNIEnv* state_env, const orcinus::orca::SettingState& state) {
            return to_java(state_env, state);
        }),
        to_java(env, settings.save_name),
        settings.save_name_copy_suffix ? JNI_TRUE : JNI_FALSE,
        to_java_objects(env, dialog_class_name, settings.notices, [](JNIEnv* dialog_env, const orcinus::orca::SettingsDialog& dialog) {
            return to_java(dialog_env, dialog);
        }),
        settings.has_question ? JNI_TRUE : JNI_FALSE,
        to_java(env, settings.question),
        settings.question_loads_selection ? JNI_TRUE : JNI_FALSE,
        settings.has_model_settings ? JNI_TRUE : JNI_FALSE,
        to_java(env, settings.model_settings, true),
        to_java(env, settings.model_settings, false)
    );
}

// NativeSettingDefinition
jobject to_java(JNIEnv* env, const orcinus::orca::SettingDefinition& setting)
{
    const jclass setting_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSettingDefinition");
    const jmethodID constructor = env->GetMethodID(
        setting_class,
        "<init>",
        "(Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;JJ[Ljava/lang/String;[Ljava/lang/String;ZZZI)V"
    );
    return env->NewObject(
        setting_class,
        constructor,
        to_java(env, setting.key),
        static_cast<jlong>(setting.type),
        to_java(env, setting.label),
        to_java(env, setting.sidetext),
        to_java(env, setting.category),
        static_cast<jlong>(setting.mode),
        static_cast<jlong>(setting.gui_type),
        to_java(env, setting.enum_values),
        to_java(env, setting.enum_labels),
        setting.multiline ? JNI_TRUE : JNI_FALSE,
        setting.full_width ? JNI_TRUE : JNI_FALSE,
        setting.is_code ? JNI_TRUE : JNI_FALSE,
        static_cast<jint>(setting.height)
    );
}

// The answers to a change's questions: parallel arrays of dialog ids and Yes flags.
orcinus::orca::DialogAnswers to_answers(JNIEnv* env, jobjectArray ids, jbooleanArray answers)
{
    const std::vector<std::string> names = to_strings(env, ids);
    const std::vector<bool> flags = to_bools(env, answers);
    orcinus::orca::DialogAnswers result;
    for (std::size_t index = 0; index < std::min(names.size(), flags.size()); ++index) {
        result.emplace_back(names[index], flags[index]);
    }
    return result;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_engineVersion(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::engine_version());
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_initialize(
    JNIEnv* env,
    jobject /* this */,
    jstring data_dir,
    jstring resources_dir,
    jstring temporary_dir
)
{
    orcinus::orca::EngineDirectories directories;
    directories.data_dir = to_utf8(env, data_dir);
    directories.resources_dir = to_utf8(env, resources_dir);
    directories.temporary_dir = to_utf8(env, temporary_dir);
    const orcinus::orca::EngineInitialization result = orcinus::orca::initialize(directories);
    return result.ready ? nullptr : to_java(env, result.message);
}

// The codes on the layers: one entry per code in every array.
static std::vector<orcinus::orca::LayerGcode> to_layer_gcodes(
    JNIEnv* env,
    jdoubleArray layer_gcode_heights,
    jlongArray layer_gcode_types,
    jintArray layer_gcode_extruders,
    jobjectArray layer_gcode_colors,
    jobjectArray layer_gcode_extras
)
{
    std::vector<orcinus::orca::LayerGcode> layer_gcodes;
    const std::vector<double> heights = to_doubles(env, layer_gcode_heights);
    const std::vector<std::int32_t> extruders = to_ints(env, layer_gcode_extruders);
    const std::vector<std::string> colors = to_strings(env, layer_gcode_colors);
    const std::vector<std::string> extras = to_strings(env, layer_gcode_extras);
    std::vector<jlong> types(heights.size(), 0);
    if (layer_gcode_types != nullptr) {
        env->GetLongArrayRegion(layer_gcode_types, 0, std::min<jsize>(env->GetArrayLength(layer_gcode_types), jsize(types.size())), types.data());
    }
    for (std::size_t index = 0; index < heights.size(); ++index) {
        orcinus::orca::LayerGcode& code = layer_gcodes.emplace_back();
        code.print_z = heights[index];
        code.type = static_cast<orcinus::orca::LayerGcodeType>(types[index]);
        code.extruder = index < extruders.size() ? extruders[index] : 1;
        code.color = index < colors.size() ? colors[index] : std::string();
        code.extra = index < extras.size() ? extras[index] : std::string();
    }
    return layer_gcodes;
}

// The plates of a project as NativeProjectPlate carries them (NativeBindings.kt).
static std::vector<orcinus::orca::ProjectPlate> to_project_plates(JNIEnv* env, jobjectArray native_plates)
{
    std::vector<orcinus::orca::ProjectPlate> plates;
    const jclass plate_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeProjectPlate");
    const jsize count = native_plates == nullptr ? 0 : env->GetArrayLength(native_plates);
    constexpr const char* string = "Ljava/lang/String;";
    constexpr const char* strings = "[Ljava/lang/String;";
    for (jsize index = 0; index < count; ++index) {
        if (env->PushLocalFrame(16) != JNI_OK) {
            break;
        }
        const jobject native_plate = env->GetObjectArrayElement(native_plates, index);
        const auto field = [env, native_plate, plate_class](const char* name, const char* signature) {
            return env->GetObjectField(native_plate, env->GetFieldID(plate_class, name, signature));
        };
        orcinus::orca::ProjectPlate& plate = plates.emplace_back();
        plate.name = to_utf8(env, static_cast<jstring>(field("name", string)));
        plate.locked = env->GetBooleanField(native_plate, env->GetFieldID(plate_class, "locked", "Z")) == JNI_TRUE;
        plate.settings = to_model_settings(env, static_cast<jobjectArray>(field("settingKeys", strings)),
                                           static_cast<jobjectArray>(field("settingValues", strings)));
        plate.layer_gcodes = to_layer_gcodes(
            env,
            static_cast<jdoubleArray>(field("layerGcodeHeights", "[D")),
            static_cast<jlongArray>(field("layerGcodeTypes", "[J")),
            static_cast<jintArray>(field("layerGcodeExtruders", "[I")),
            static_cast<jobjectArray>(field("layerGcodeColors", strings)),
            static_cast<jobjectArray>(field("layerGcodeExtras", strings))
        );
        plate.thumbnail.width = env->GetIntField(native_plate, env->GetFieldID(plate_class, "thumbnailWidth", "I"));
        plate.thumbnail.height = env->GetIntField(native_plate, env->GetFieldID(plate_class, "thumbnailHeight", "I"));
        plate.thumbnail.path = to_utf8(env, static_cast<jstring>(field("thumbnailPath", string)));
        // The other pictures are as large as the plate's picture.
        for (auto [picture, name] : {std::pair{&plate.no_light_thumbnail, "noLightThumbnailPath"}, std::pair{&plate.top_thumbnail, "topThumbnailPath"},
                                     std::pair{&plate.pick_thumbnail, "pickThumbnailPath"}}) {
            picture->width = plate.thumbnail.width;
            picture->height = plate.thumbnail.height;
            picture->path = to_utf8(env, static_cast<jstring>(field(name, string)));
        }
        plate.slice_info_path = to_utf8(env, static_cast<jstring>(field("sliceInfoPath", string)));
        plate.gcode_path = to_utf8(env, static_cast<jstring>(field("gcodePath", string)));
        env->PopLocalFrame(nullptr);
    }
    env->DeleteLocalRef(plate_class);
    return plates;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_saveProject(
    JNIEnv* env,
    jobject /* this */,
    jstring path,
    jobject plate,
    jobjectArray plates,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring project_info,
    jint current_plate,
    jlong sliced
)
{
    const orcinus::orca::ProjectSave saved = orcinus::orca::save_project(
        to_utf8(env, path),
        to_plate(env, plate),
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
        to_project_plates(env, plates),
        to_utf8(env, project_info),
        static_cast<int>(current_plate),
        static_cast<orcinus::orca::SlicedPlates>(sliced)
    );
    // status, message.
    return to_java(env, std::vector<std::string>{std::to_string(static_cast<int>(saved.status)), saved.message});
}

// The calibration as NativeCalibration carries it (NativeBindings.kt); none for null.
static orcinus::orca::CalibrationParams to_calibration(JNIEnv* env, jobject native)
{
    orcinus::orca::CalibrationParams params;
    if (native == nullptr) {
        return params;
    }
    const jclass calibration_class = env->GetObjectClass(native);
    const auto double_field = [env, native, calibration_class](const char* name) {
        return env->GetDoubleField(native, env->GetFieldID(calibration_class, name, "D"));
    };
    const auto int_field = [env, native, calibration_class](const char* name) {
        return env->GetIntField(native, env->GetFieldID(calibration_class, name, "I"));
    };
    params.mode = static_cast<orcinus::orca::CalibrationMode>(env->GetLongField(native, env->GetFieldID(calibration_class, "mode", "J")));
    params.extruder_id = int_field("extruderId");
    params.start = double_field("start");
    params.end = double_field("end");
    params.step = double_field("step");
    params.print_numbers = env->GetBooleanField(native, env->GetFieldID(calibration_class, "printNumbers", "Z")) == JNI_TRUE;
    params.freq_start_x = double_field("freqStartX");
    params.freq_end_x = double_field("freqEndX");
    params.freq_start_y = double_field("freqStartY");
    params.freq_end_y = double_field("freqEndY");
    params.test_model = int_field("testModel");
    params.shaper_type = to_utf8(env, static_cast<jstring>(env->GetObjectField(native, env->GetFieldID(calibration_class, "shaperType", "Ljava/lang/String;"))));
    params.accelerations = to_doubles(env, static_cast<jdoubleArray>(env->GetObjectField(native, env->GetFieldID(calibration_class, "accelerations", "[D"))));
    params.speeds = to_doubles(env, static_cast<jdoubleArray>(env->GetObjectField(native, env->GetFieldID(calibration_class, "speeds", "[D"))));
    env->DeleteLocalRef(calibration_class);
    return params;
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_slice(
    JNIEnv* env,
    jobject /* this */,
    jstring job_id,
    jobject plate,
    jobjectArray plate_setting_keys,
    jobjectArray plate_setting_values,
    jstring output_path,
    jstring toolpaths_path,
    jstring wipe_tower_path,
    jintArray thumbnail_sizes,
    jobjectArray thumbnail_paths,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jobject progress_listener,
    jdoubleArray layer_gcode_heights,
    jlongArray layer_gcode_types,
    jintArray layer_gcode_extruders,
    jobjectArray layer_gcode_colors,
    jobjectArray layer_gcode_extras,
    jobject calibration,
    jobject pa_pattern,
    jstring slice_info_path
)
{
    const std::vector<orcinus::orca::LayerGcode> layer_gcodes =
        to_layer_gcodes(env, layer_gcode_heights, layer_gcode_types, layer_gcode_extruders, layer_gcode_colors, layer_gcode_extras);
    // The thumbnails the app rendered: a width and a height per file.
    std::vector<orcinus::orca::ThumbnailImage> thumbnails;
    {
        const std::vector<std::int32_t> sizes = to_ints(env, thumbnail_sizes);
        const std::vector<std::string> paths = to_strings(env, thumbnail_paths);
        for (std::size_t index = 0; index < paths.size() && 2 * index + 1 < sizes.size(); ++index) {
            thumbnails.push_back({sizes[2 * index], sizes[2 * index + 1], paths[index]});
        }
    }
    const orcinus::orca::ProfileSelection profiles =
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles);
    const ProgressForwarder forward_progress(env, progress_listener);
    const orcinus::orca::SliceResult result = orcinus::orca::slice(
        to_utf8(env, job_id),
        to_plate(env, plate),
        to_utf8(env, output_path),
        toolpaths_path != nullptr ? to_utf8(env, toolpaths_path) : std::string(),
        profiles,
        to_model_settings(env, plate_setting_keys, plate_setting_values),
        [&forward_progress](const int percent, const std::string& message) { forward_progress(percent, message); },
        wipe_tower_path != nullptr ? to_utf8(env, wipe_tower_path) : std::string(),
        thumbnails,
        layer_gcodes,
        to_calibration(env, calibration),
        to_calibration(env, pa_pattern),
        slice_info_path != nullptr ? to_utf8(env, slice_info_path) : std::string()
    );

    // The filaments the plate prints with, and eight amounts for each: metres
    // and grams for the model, support, flushing and wipe tower.
    std::vector<jint> filaments;
    std::vector<double> amounts;
    for (const orcinus::orca::FilamentUsage& usage : result.filaments) {
        filaments.push_back(usage.filament);
        for (const std::array<double, 2>& used : {usage.model, usage.support, usage.flushed, usage.wipe_tower}) {
            amounts.insert(amounts.end(), used.begin(), used.end());
        }
    }
    const jintArray filament_array = env->NewIntArray(static_cast<jsize>(filaments.size()));
    env->SetIntArrayRegion(filament_array, 0, static_cast<jsize>(filaments.size()), filaments.data());
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSliceResult");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;JJJZZZZZD[I[DZ)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        static_cast<jlong>(result.layer_count),
        static_cast<jlong>(result.estimated_print_time_seconds),
        static_cast<jlong>(result.filament_micrometers),
        result.toolpaths_written ? JNI_TRUE : JNI_FALSE,
        result.wipe_tower_written ? JNI_TRUE : JNI_FALSE,
        result.sequential ? JNI_TRUE : JNI_FALSE,
        result.can_change_filament ? JNI_TRUE : JNI_FALSE,
        result.has_template ? JNI_TRUE : JNI_FALSE,
        static_cast<jdouble>(result.total_cost),
        filament_array,
        to_java(env, amounts.data(), amounts.size()),
        result.slice_info_written ? JNI_TRUE : JNI_FALSE
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeWipeTower(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jobjectArray plate_setting_keys,
    jobjectArray plate_setting_values,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile
)
{
    const orcinus::orca::WipeTowerState tower = orcinus::orca::describe_wipe_tower(
        to_plate(env, plate),
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
        to_model_settings(env, plate_setting_keys, plate_setting_values)
    );

    std::vector<double> filaments;
    filaments.reserve(tower.filaments.size());
    for (const int filament : tower.filaments) {
        filaments.push_back(static_cast<double>(filament));
    }
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeWipeTower");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;ZDDDDDDD[DZZZZ)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(tower.status),
        to_java(env, tower.message),
        tower.shown ? JNI_TRUE : JNI_FALSE,
        static_cast<jdouble>(tower.x),
        static_cast<jdouble>(tower.y),
        static_cast<jdouble>(tower.width),
        static_cast<jdouble>(tower.depth),
        static_cast<jdouble>(tower.height),
        static_cast<jdouble>(tower.rotation),
        static_cast<jdouble>(tower.brim_width),
        to_java(env, filaments.data(), filaments.size()),
        tower.prime_tower ? JNI_TRUE : JNI_FALSE,
        tower.flush_into_infill ? JNI_TRUE : JNI_FALSE,
        tower.flush_into_objects ? JNI_TRUE : JNI_FALSE,
        tower.flush_into_support ? JNI_TRUE : JNI_FALSE
    );
}

// NativeFlushVolumes
jobject to_java(JNIEnv* env, const orcinus::orca::FlushVolumes& volumes, const bool updated)
{
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeFlushVolumes");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;JJ[D[D[DZZ)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(volumes.status),
        to_java(env, volumes.message),
        static_cast<jlong>(volumes.filaments),
        static_cast<jlong>(volumes.nozzles),
        to_java(env, volumes.matrix.data(), volumes.matrix.size()),
        to_java(env, volumes.automatic.data(), volumes.automatic.size()),
        to_java(env, volumes.multipliers.data(), volumes.multipliers.size()),
        volumes.modified ? JNI_TRUE : JNI_FALSE,
        updated ? JNI_TRUE : JNI_FALSE
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeFlushVolumes(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jobjectArray plate_setting_keys,
    jobjectArray plate_setting_values,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile
)
{
    const orcinus::orca::FlushVolumes volumes = orcinus::orca::describe_flush_volumes(
        to_plate(env, plate),
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
        to_model_settings(env, plate_setting_keys, plate_setting_values)
    );
    return to_java(env, volumes, false);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_updateFlushVolumes(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jobjectArray plate_setting_keys,
    jobjectArray plate_setting_values,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jlong change,
    jlong index
)
{
    const orcinus::orca::FlushVolumesUpdate update = orcinus::orca::update_flush_volumes(
        to_plate(env, plate),
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
        to_model_settings(env, plate_setting_keys, plate_setting_values),
        static_cast<orcinus::orca::FlushVolumesChange>(change),
        static_cast<std::int64_t>(index)
    );
    return to_java(env, update.volumes, update.updated);
}

jobject to_java(JNIEnv* env, const orcinus::orca::PaintingState& state)
{
    const jclass string_class = env->FindClass("java/lang/String");
    const jobjectArray meshes = env->NewObjectArray(static_cast<jsize>(state.meshes.size()), string_class, nullptr);
    for (std::size_t index = 0; index < state.meshes.size(); ++index) {
        env->SetObjectArrayElement(meshes, static_cast<jsize>(index), to_java(env, state.meshes[index]));
    }
    std::vector<double> states;
    states.reserve(state.states.size());
    for (const int painted : state.states) {
        states.push_back(static_cast<double>(painted));
    }
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePainting");
    const jmethodID constructor =
        env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;Z[D[Ljava/lang/String;Ljava/lang/String;ZZ)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(state.status),
        to_java(env, state.message),
        state.hit ? JNI_TRUE : JNI_FALSE,
        to_java(env, states.data(), states.size()),
        meshes,
        to_java(env, state.facets),
        state.can_undo ? JNI_TRUE : JNI_FALSE,
        state.can_redo ? JNI_TRUE : JNI_FALSE
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_beginPainting(
    JNIEnv* env,
    jobject /* this */,
    jobject object,
    jint part,
    jlong kind,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring facets,
    jstring mesh_prefix,
    jint instance,
    jboolean assembly_view,
    jdouble explosion_ratio
)
{
    const std::vector<orcinus::orca::PlateObject> plate = to_plate(env, object);
    orcinus::orca::PaintPlacement placement;
    placement.instance = static_cast<int>(instance);
    placement.assembly_view = assembly_view == JNI_TRUE;
    placement.explosion_ratio = static_cast<double>(explosion_ratio);
    const orcinus::orca::PaintingState state = orcinus::orca::begin_painting(
        plate.empty() ? orcinus::orca::PlateObject{} : plate.front(),
        static_cast<int>(part),
        static_cast<orcinus::orca::PaintKind>(kind),
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
        to_utf8(env, facets),
        to_utf8(env, mesh_prefix),
        placement
    );
    return to_java(env, state);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_fuzzySkinDisabled(
    JNIEnv* env,
    jobject /* this */,
    jobject object,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile
)
{
    const std::vector<orcinus::orca::PlateObject> plate = to_plate(env, object);
    const bool disabled = !plate.empty() &&
        orcinus::orca::fuzzy_skin_disabled(
            plate.front(),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles)
        );
    return disabled ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_paintStroke(
    JNIEnv* env,
    jobject /* this */,
    jdoubleArray origin,
    jdoubleArray direction,
    jint state,
    jdouble radius,
    jlong tool,
    jdouble angle,
    jdouble overhang_angle,
    jboolean starts,
    jstring mesh_prefix
)
{
    const std::vector<double> from = to_doubles(env, origin);
    const std::vector<double> along = to_doubles(env, direction);
    orcinus::orca::PaintStroke stroke;
    for (std::size_t axis = 0; axis < 3; ++axis) {
        stroke.origin[axis] = axis < from.size() ? from[axis] : 0.0;
        stroke.direction[axis] = axis < along.size() ? along[axis] : 0.0;
    }
    stroke.state = static_cast<int>(state);
    stroke.radius = radius;
    stroke.tool = static_cast<orcinus::orca::PaintTool>(tool);
    stroke.angle = angle;
    stroke.overhang_angle = overhang_angle;
    stroke.starts = starts == JNI_TRUE;
    return to_java(env, orcinus::orca::paint(stroke, to_utf8(env, mesh_prefix)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_undoPainting(JNIEnv* env, jobject /* this */, jstring mesh_prefix)
{
    return to_java(env, orcinus::orca::undo_painting(to_utf8(env, mesh_prefix)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_redoPainting(JNIEnv* env, jobject /* this */, jstring mesh_prefix)
{
    return to_java(env, orcinus::orca::redo_painting(to_utf8(env, mesh_prefix)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_clearPainting(JNIEnv* env, jobject /* this */, jstring mesh_prefix)
{
    return to_java(env, orcinus::orca::clear_painting(to_utf8(env, mesh_prefix)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setGapFill(JNIEnv* env, jobject /* this */, jdouble gap_area, jstring mesh_prefix)
{
    return to_java(env, orcinus::orca::set_gap_fill(gap_area, to_utf8(env, mesh_prefix)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_fillGaps(JNIEnv* env, jobject /* this */, jstring mesh_prefix)
{
    return to_java(env, orcinus::orca::fill_gaps(to_utf8(env, mesh_prefix)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_endPainting(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::end_painting());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_cancel(JNIEnv* env, jobject /* this */, jstring job_id)
{
    return orcinus::orca::cancel(to_utf8(env, job_id)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describePlate(
    JNIEnv* env,
    jobject /* this */,
    jstring printer_profile,
    jstring filament_profile,
    jstring process_profile,
    jstring output_dir
)
{
    const orcinus::orca::PlateDescription plate = orcinus::orca::describe_plate(
        to_profiles(env, printer_profile, filament_profile, process_profile),
        to_utf8(env, output_dir)
    );
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePlateDescription");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;[DD[F[F[F[FLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
    );
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(plate.status),
        to_java(env, plate.message),
        to_java(env, plate.printable_area.data(), plate.printable_area.size()),
        static_cast<jdouble>(plate.printable_height),
        to_java(env, plate.plate_triangles),
        to_java(env, plate.exclude_triangles),
        to_java(env, plate.thin_grid_lines),
        to_java(env, plate.bold_grid_lines),
        to_java(env, plate.bed_model_mesh),
        to_java(env, plate.bed_texture),
        to_java(env, plate.filament_colour)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_inspectModel(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jstring printer_profile,
    jstring filament_profile,
    jstring process_profile,
    jstring mesh_path,
    jobject plate
)
{
    const orcinus::orca::ModelInspection inspection = orcinus::orca::inspect_model(
        to_utf8(env, model_path),
        to_profiles(env, printer_profile, filament_profile, process_profile),
        to_utf8(env, mesh_path),
        to_plate(env, plate)
    );
    return to_java(env, inspection);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_placeModel(
    JNIEnv* env,
    jobject /* this */,
    jobject object,
    jstring printer_profile,
    jstring filament_profile,
    jstring process_profile,
    jdoubleArray previous_placement,
    jdoubleArray placement,
    jboolean auto_drop,
    jlong manipulation,
    jdoubleArray face_normal
)
{
    std::array<double, 3> normal{};
    if (face_normal != nullptr && env->GetArrayLength(face_normal) == 3) {
        env->GetDoubleArrayRegion(face_normal, 0, 3, normal.data());
    }
    const std::vector<orcinus::orca::PlateObject> plate = to_plate(env, object);
    const orcinus::orca::ModelInspection inspection = orcinus::orca::place_model(
        plate.empty() ? orcinus::orca::PlateObject{} : plate.front(),
        to_profiles(env, printer_profile, filament_profile, process_profile),
        to_doubles(env, previous_placement),
        to_doubles(env, placement),
        auto_drop == JNI_TRUE,
        static_cast<orcinus::orca::Manipulation>(manipulation),
        normal
    );
    return to_java(env, inspection);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_placeObjects(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jbooleanArray selected,
    jstring printer_profile,
    jstring filament_profile,
    jstring process_profile,
    jlong manipulation,
    jdouble arrange_distance,
    jboolean arrange_enable_rotation,
    jboolean arrange_allow_multi_materials,
    jboolean arrange_align_to_y_axis,
    jint selected_instance,
    jbooleanArray locked_plates
)
{
    orcinus::orca::ArrangeSettings arrange;
    arrange.distance = arrange_distance;
    arrange.enable_rotation = arrange_enable_rotation == JNI_TRUE;
    arrange.allow_multi_materials_on_same_plate = arrange_allow_multi_materials == JNI_TRUE;
    arrange.align_to_y_axis = arrange_align_to_y_axis == JNI_TRUE;
    const orcinus::orca::PlateInspection inspection = orcinus::orca::place_objects(
        to_plate(env, plate),
        to_bools(env, selected),
        to_profiles(env, printer_profile, filament_profile, process_profile),
        static_cast<orcinus::orca::PlateManipulation>(manipulation),
        arrange,
        selected_instance,
        to_bools(env, locked_plates)
    );

    const jclass inspection_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeModelInspection");
    const jclass instances_class = env->FindClass("[Lapp/orcinus/shadow/slicing/nativebridge/NativeModelInspection;");
    const jobjectArray objects = env->NewObjectArray(static_cast<jsize>(inspection.objects.size()), instances_class, nullptr);
    for (std::size_t index = 0; index < inspection.objects.size(); ++index) {
        const std::vector<orcinus::orca::ModelInspection>& copies = inspection.objects[index].instances;
        const jobjectArray instances = env->NewObjectArray(static_cast<jsize>(copies.size()), inspection_class, nullptr);
        for (std::size_t copy = 0; copy < copies.size(); ++copy) {
            // Each inspection creates several local references; its frame keeps them few.
            if (env->PushLocalFrame(16) != JNI_OK) {
                return nullptr;
            }
            const jobject instance = env->PopLocalFrame(to_java(env, copies[copy]));
            env->SetObjectArrayElement(instances, static_cast<jsize>(copy), instance);
            env->DeleteLocalRef(instance);
        }
        env->SetObjectArrayElement(objects, static_cast<jsize>(index), instances);
        env->DeleteLocalRef(instances);
    }
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePlateInspection");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;[[Lapp/orcinus/shadow/slicing/nativebridge/NativeModelInspection;I)V"
    );
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(inspection.status),
        to_java(env, inspection.message),
        objects,
        static_cast<jint>(inspection.plate_count)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_addObjectPart(
    JNIEnv* env,
    jobject /* this */,
    jobject object,
    jstring shape,
    jlong type,
    jstring printer_profile,
    jstring filament_profile,
    jstring process_profile,
    jstring mesh_path
)
{
    const std::vector<orcinus::orca::PlateObject> plate = to_plate(env, object);
    const orcinus::orca::ModelInspection inspection = orcinus::orca::add_object_part(
        plate.empty() ? orcinus::orca::PlateObject{} : plate.front(),
        to_utf8(env, shape),
        static_cast<orcinus::orca::VolumeType>(type),
        to_profiles(env, printer_profile, filament_profile, process_profile),
        to_utf8(env, mesh_path)
    );
    return to_java(env, inspection);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeFlatteningPlanes(
    JNIEnv* env,
    jobject /* this */,
    jobject object,
    jstring printer_profile,
    jstring filament_profile,
    jstring process_profile,
    jdoubleArray placement
)
{
    const std::vector<orcinus::orca::PlateObject> plate = to_plate(env, object);
    const orcinus::orca::FlatteningPlanes result = orcinus::orca::describe_flattening_planes(
        plate.empty() ? orcinus::orca::PlateObject{} : plate.front(),
        to_profiles(env, printer_profile, filament_profile, process_profile),
        to_doubles(env, placement)
    );
    std::vector<double> normals;
    std::vector<int> counts;
    std::vector<float> vertices;
    for (const orcinus::orca::FlatteningPlane& plane : result.planes) {
        normals.insert(normals.end(), plane.normal.begin(), plane.normal.end());
        counts.push_back(static_cast<int>(plane.vertices.size() / 3));
        vertices.insert(vertices.end(), plane.vertices.begin(), plane.vertices.end());
    }
    const jintArray counts_array = env->NewIntArray(static_cast<jsize>(counts.size()));
    env->SetIntArrayRegion(counts_array, 0, static_cast<jsize>(counts.size()), counts.data());
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeFlatteningPlanes");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;[D[I[F)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        to_java(env, normals.data(), normals.size()),
        counts_array,
        to_java(env, vertices)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describePresets(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::describe_presets());
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_selectPreset(JNIEnv* env, jobject /* this */, jlong choice, jstring value, jlong action)
{
    return to_java(
        env,
        orcinus::orca::select_preset(
            static_cast<orcinus::orca::PresetChoice>(choice),
            to_utf8(env, value),
            static_cast<orcinus::orca::PresetChangeAction>(action)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeSetupPrinters(JNIEnv* env, jobject /* this */)
{
    const orcinus::orca::SetupPrinters result = orcinus::orca::describe_setup_printers();
    const jobjectArray models = to_java_objects(
        env,
        "app/orcinus/shadow/slicing/nativebridge/NativeSetupPrinterModel",
        result.models,
        [](JNIEnv* model_env, const orcinus::orca::SetupPrinterModel& model) {
            const jclass model_class = model_env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSetupPrinterModel");
            const jmethodID constructor = model_env->GetMethodID(
                model_class,
                "<init>",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V"
            );
            return model_env->NewObject(
                model_class,
                constructor,
                to_java(model_env, model.vendor),
                to_java(model_env, model.model),
                to_java(model_env, model.name),
                to_java(model_env, model.nozzle_diameters),
                to_java(model_env, model.default_materials),
                to_java(model_env, model.cover),
                to_java(model_env, model.installed_nozzles)
            );
        }
    );
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSetupPrinters");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;[Lapp/orcinus/shadow/slicing/nativebridge/NativeSetupPrinterModel;)V"
    );
    return env->NewObject(result_class, constructor, static_cast<jlong>(result.status), to_java(env, result.message), models);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeSetupFilaments(JNIEnv* env, jobject /* this */, jobjectArray models)
{
    const orcinus::orca::SetupFilaments result = orcinus::orca::describe_setup_filaments(to_strings(env, models));
    const jobjectArray filaments = to_java_objects(
        env,
        "app/orcinus/shadow/slicing/nativebridge/NativeSetupFilament",
        result.filaments,
        [](JNIEnv* filament_env, const orcinus::orca::SetupFilament& filament) {
            const jclass filament_class = filament_env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSetupFilament");
            const jmethodID constructor = filament_env->GetMethodID(
                filament_class,
                "<init>",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[IZ)V"
            );
            const jintArray filament_models = filament_env->NewIntArray(static_cast<jsize>(filament.models.size()));
            filament_env->SetIntArrayRegion(filament_models, 0, static_cast<jsize>(filament.models.size()), filament.models.data());
            return filament_env->NewObject(
                filament_class,
                constructor,
                to_java(filament_env, filament.name),
                to_java(filament_env, filament.vendor),
                to_java(filament_env, filament.type),
                filament_models,
                filament.selected ? JNI_TRUE : JNI_FALSE
            );
        }
    );
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSetupFilaments");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;[Lapp/orcinus/shadow/slicing/nativebridge/NativeSetupFilament;)V"
    );
    return env->NewObject(result_class, constructor, static_cast<jlong>(result.status), to_java(env, result.message), filaments);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_applySetup(JNIEnv* env, jobject /* this */, jobjectArray models, jobjectArray filaments)
{
    return to_java(env, orcinus::orca::apply_setup(to_strings(env, models), to_strings(env, filaments)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_applyDefaultSetup(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::apply_default_setup());
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeSettingDefinitions(JNIEnv* env, jobject /* this */, jlong kind)
{
    const orcinus::orca::SettingDefinitions result = orcinus::orca::describe_setting_definitions(static_cast<orcinus::orca::PresetKind>(kind));
    const jobjectArray settings = to_java_objects(
        env,
        "app/orcinus/shadow/slicing/nativebridge/NativeSettingDefinition",
        result.settings,
        [](JNIEnv* setting_env, const orcinus::orca::SettingDefinition& setting) { return to_java(setting_env, setting); }
    );
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSettingDefinitions");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;[Lapp/orcinus/shadow/slicing/nativebridge/NativeSettingDefinition;)V"
    );
    return env->NewObject(result_class, constructor, static_cast<jlong>(result.status), to_java(env, result.message), settings);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeSettings(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jstring page,
    jobjectArray answer_ids,
    jbooleanArray answers,
    jobjectArray model_keys,
    jobjectArray model_values,
    jobjectArray plate_keys,
    jobjectArray plate_values,
    jobjectArray parent_keys,
    jobjectArray parent_values
)
{
    return to_java(
        env,
        orcinus::orca::describe_settings(
            static_cast<orcinus::orca::PresetKind>(kind),
            to_utf8(env, page),
            to_answers(env, answer_ids, answers),
            to_model_request(env, model_keys, model_values, plate_keys, plate_values, parent_keys, parent_values)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_changeSetting(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jstring page,
    jstring id,
    jstring text,
    jobjectArray answer_ids,
    jbooleanArray answers,
    jobjectArray model_keys,
    jobjectArray model_values,
    jobjectArray plate_keys,
    jobjectArray plate_values,
    jobjectArray parent_keys,
    jobjectArray parent_values
)
{
    return to_java(
        env,
        orcinus::orca::change_setting(
            static_cast<orcinus::orca::PresetKind>(kind),
            to_utf8(env, page),
            to_utf8(env, id),
            to_utf8(env, text),
            to_answers(env, answer_ids, answers),
            to_model_request(env, model_keys, model_values, plate_keys, plate_values, parent_keys, parent_values)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_resetSettings(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jstring page,
    jobjectArray ids,
    jobjectArray answer_ids,
    jbooleanArray answers,
    jobjectArray model_keys,
    jobjectArray model_values,
    jobjectArray plate_keys,
    jobjectArray plate_values,
    jobjectArray parent_keys,
    jobjectArray parent_values
)
{
    return to_java(
        env,
        orcinus::orca::reset_settings(
            static_cast<orcinus::orca::PresetKind>(kind),
            to_utf8(env, page),
            to_strings(env, ids),
            to_answers(env, answer_ids, answers),
            to_model_request(env, model_keys, model_values, plate_keys, plate_values, parent_keys, parent_values)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setSettingOverride(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jstring page,
    jstring id,
    jboolean enabled,
    jobjectArray answer_ids,
    jbooleanArray answers
)
{
    return to_java(
        env,
        orcinus::orca::set_setting_override(
            static_cast<orcinus::orca::PresetKind>(kind),
            to_utf8(env, page),
            to_utf8(env, id),
            enabled == JNI_TRUE,
            to_answers(env, answer_ids, answers)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setCompatiblePresets(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jstring page,
    jstring key,
    jobjectArray presets,
    jobjectArray answer_ids,
    jbooleanArray answers
)
{
    return to_java(
        env,
        orcinus::orca::set_compatible_presets(
            static_cast<orcinus::orca::PresetKind>(kind),
            to_utf8(env, page),
            to_utf8(env, key),
            to_strings(env, presets),
            to_answers(env, answer_ids, answers)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_compatiblePresetChoices(JNIEnv* env, jobject /* this */, jlong kind, jstring key)
{
    const orcinus::orca::PresetNames result =
        orcinus::orca::compatible_preset_choices(static_cast<orcinus::orca::PresetKind>(kind), to_utf8(env, key));
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePresetNames");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;[Ljava/lang/String;)V");
    return env->NewObject(result_class, constructor, static_cast<jlong>(result.status), to_java(env, result.message), to_java(env, result.names));
}

// NativeConfigTransfer
jobject to_java(JNIEnv* env, const orcinus::orca::ConfigTransfer& transfer)
{
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeConfigTransfer");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;[Ljava/lang/String;Ljava/lang/String;)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(transfer.status),
        to_java(env, transfer.message),
        to_java(env, transfer.names),
        to_java(env, transfer.overwrite_preset)
    );
}

// NativePrinterConnection
jobject to_java(JNIEnv* env, const orcinus::orca::PrinterConnection& connection)
{
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePrinterConnection");
    const jmethodID constructor = env->GetMethodID(
        result_class, "<init>", "(JLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;Ljava/lang/String;ZLjava/lang/String;)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(connection.status),
        to_java(env, connection.message),
        to_java(env, connection.settings.keys),
        to_java(env, connection.settings.values),
        to_java(env, connection.save_name),
        connection.save_name_copy_suffix ? JNI_TRUE : JNI_FALSE,
        to_java(env, connection.webui),
        to_java(env, connection.api_key),
        connection.bbl_device_tab ? JNI_TRUE : JNI_FALSE,
        to_java(env, connection.printer_type)
    );
}

// NativeDirtyPreset
static jobject to_java(JNIEnv* env, const orcinus::orca::DirtyPreset& preset)
{
    const jclass preset_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeDirtyPreset");
    const jmethodID constructor = env->GetMethodID(
        preset_class,
        "<init>",
        "(JLjava/lang/String;ZLjava/lang/String;Z[Lapp/orcinus/shadow/slicing/nativebridge/NativePresetChange;)V"
    );
    return env->NewObject(
        preset_class,
        constructor,
        static_cast<jlong>(preset.kind),
        to_java(env, preset.name),
        preset.can_overwrite ? JNI_TRUE : JNI_FALSE,
        to_java(env, preset.save_name),
        preset.save_name_copy_suffix ? JNI_TRUE : JNI_FALSE,
        to_java_objects(
            env,
            "app/orcinus/shadow/slicing/nativebridge/NativePresetChange",
            preset.changes,
            [](JNIEnv* change_env, const orcinus::orca::PresetChange& change) { return to_java(change_env, change); }
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_dirtyPresets(JNIEnv* env, jobject /* this */)
{
    const orcinus::orca::DirtyPresets dirty = orcinus::orca::dirty_presets();
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeDirtyPresets");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;[Lapp/orcinus/shadow/slicing/nativebridge/NativeDirtyPreset;)V"
    );
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(dirty.status),
        to_java(env, dirty.message),
        to_java_objects(
            env,
            "app/orcinus/shadow/slicing/nativebridge/NativeDirtyPreset",
            dirty.presets,
            [](JNIEnv* preset_env, const orcinus::orca::DirtyPreset& preset) { return to_java(preset_env, preset); }
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_discardPresetChanges(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::discard_preset_changes());
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_resetProjectPresets(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::reset_project_presets());
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_addFilament(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::add_filament());
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_removeFilament(JNIEnv* env, jobject /* this */, jlong index)
{
    return to_java(env, orcinus::orca::remove_filament(index));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_selectFilament(
    JNIEnv* env,
    jobject /* this */,
    jlong index,
    jstring name,
    jlong action
)
{
    return to_java(
        env,
        orcinus::orca::select_filament(index, to_utf8(env, name), static_cast<orcinus::orca::PresetChangeAction>(action))
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setFilamentColor(JNIEnv* env, jobject /* this */, jlong index, jstring color)
{
    return to_java(env, orcinus::orca::set_filament_color(index, to_utf8(env, color)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_printerConnection(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::printer_connection());
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_savePrinterConnection(
    JNIEnv* env,
    jobject /* this */,
    jobjectArray keys,
    jobjectArray values,
    jstring name
)
{
    return to_java(env, orcinus::orca::save_printer_connection(to_model_settings(env, keys, values), to_utf8(env, name)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_importPresets(
    JNIEnv* env,
    jobject /* this */,
    jobjectArray paths,
    jobjectArray answer_presets,
    jlongArray answers
)
{
    // What the user answered about every preset the import would replace.
    std::map<std::string, orcinus::orca::ConfigOverwriteAnswer> given;
    const std::vector<std::string> presets = to_strings(env, answer_presets);
    const std::vector<std::int64_t> values = to_longs(env, answers);
    for (std::size_t index = 0; index < presets.size() && index < values.size(); ++index) {
        given.emplace(presets[index], static_cast<orcinus::orca::ConfigOverwriteAnswer>(values[index]));
    }
    return to_java(env, orcinus::orca::import_presets(to_strings(env, paths), given));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_exportConfigs(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jobjectArray names,
    jstring directory
)
{
    return to_java(
        env,
        orcinus::orca::export_configs(
            static_cast<orcinus::orca::ConfigExportKind>(kind),
            to_strings(env, names),
            to_utf8(env, directory)
        )
    );
}

namespace {

// The printers and presets the check boxes of a filament dialog stand for.
std::vector<orcinus::orca::FilamentPresetChoice> to_choices(JNIEnv* env, jobjectArray printers, jobjectArray presets)
{
    const std::vector<std::string> printer_names = to_strings(env, printers);
    const std::vector<std::string> preset_names = to_strings(env, presets);
    std::vector<orcinus::orca::FilamentPresetChoice> choices;
    for (std::size_t index = 0; index < printer_names.size() && index < preset_names.size(); ++index) {
        choices.push_back(orcinus::orca::FilamentPresetChoice{printer_names[index], preset_names[index]});
    }
    return choices;
}

// NativePresetCreation
jobject to_java(JNIEnv* env, const orcinus::orca::PresetCreation& creation)
{
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePresetCreation");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;ZLapp/orcinus/shadow/slicing/nativebridge/NativeSettingsDialog;Ljava/lang/String;)V"
    );
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(creation.status),
        to_java(env, creation.message),
        creation.has_question ? JNI_TRUE : JNI_FALSE,
        to_java(env, creation.question),
        to_java(env, creation.name)
    );
}

}  // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_createFilamentOptions(
    JNIEnv* env,
    jobject /* this */,
    jstring type,
    jstring base_filament
)
{
    const orcinus::orca::CreateFilamentOptions result = orcinus::orca::create_filament_options(to_utf8(env, type), to_utf8(env, base_filament));
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeCreateFilamentOptions");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;"
        "[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)V"
    );
    const auto printers_of = [](const std::vector<orcinus::orca::FilamentPresetChoice>& choices) {
        std::vector<std::string> names;
        for (const orcinus::orca::FilamentPresetChoice& choice : choices) {
            names.push_back(choice.printer);
        }
        return names;
    };
    const auto presets_of = [](const std::vector<orcinus::orca::FilamentPresetChoice>& choices) {
        std::vector<std::string> names;
        for (const orcinus::orca::FilamentPresetChoice& choice : choices) {
            names.push_back(choice.preset);
        }
        return names;
    };
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        to_java(env, result.vendors),
        to_java(env, result.types),
        to_java(env, result.base_filaments),
        to_java(env, printers_of(result.presets)),
        to_java(env, presets_of(result.presets)),
        to_java(env, printers_of(result.copy_presets)),
        to_java(env, presets_of(result.copy_presets))
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_createFilament(
    JNIEnv* env,
    jobject /* this */,
    jstring vendor,
    jboolean custom_vendor,
    jstring type,
    jstring serial,
    jobjectArray printers,
    jobjectArray presets,
    jobjectArray answer_ids,
    jbooleanArray answers
)
{
    orcinus::orca::CreateFilamentRequest request;
    request.vendor = to_utf8(env, vendor);
    request.custom_vendor = custom_vendor == JNI_TRUE;
    request.type = to_utf8(env, type);
    request.serial = to_utf8(env, serial);
    request.presets = to_choices(env, printers, presets);
    return to_java(env, orcinus::orca::create_filament(request, to_answers(env, answer_ids, answers)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_createPrinterOptions(
    JNIEnv* env,
    jobject /* this */,
    jstring vendor,
    jstring nozzle,
    jstring preset_vendor,
    jstring printer_preset
)
{
    const orcinus::orca::CreatePrinterOptions result = orcinus::orca::create_printer_options(
        to_utf8(env, vendor),
        to_utf8(env, nozzle),
        to_utf8(env, preset_vendor),
        to_utf8(env, printer_preset)
    );
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeCreatePrinterOptions");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;"
        "[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[DD)V"
    );
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        to_java(env, result.vendors),
        to_java(env, result.models),
        to_java(env, result.nozzle_diameters),
        to_java(env, result.preset_vendors),
        to_java(env, result.printer_presets),
        to_java(env, result.filament_presets),
        to_java(env, result.process_presets),
        to_java(env, result.printable_area.data(), result.printable_area.size()),
        static_cast<jdouble>(result.max_print_height)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_createPrinter(
    JNIEnv* env,
    jobject /* this */,
    jstring model,
    jstring nozzle,
    jdoubleArray printable_area,
    jdouble max_print_height,
    jstring custom_texture,
    jstring custom_model,
    jstring preset_vendor,
    jstring printer_preset,
    jobjectArray filament_presets,
    jobjectArray process_presets,
    jobjectArray answer_ids,
    jbooleanArray answers
)
{
    orcinus::orca::CreatePrinterRequest request;
    request.model = to_utf8(env, model);
    request.nozzle = to_utf8(env, nozzle);
    request.printable_area = to_doubles(env, printable_area);
    request.max_print_height = max_print_height;
    request.custom_texture = to_utf8(env, custom_texture);
    request.custom_model = to_utf8(env, custom_model);
    request.preset_vendor = to_utf8(env, preset_vendor);
    request.printer_preset = to_utf8(env, printer_preset);
    request.filament_presets = to_strings(env, filament_presets);
    request.process_presets = to_strings(env, process_presets);
    return to_java(env, orcinus::orca::create_printer(request, to_answers(env, answer_ids, answers)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_customFilaments(JNIEnv* env, jobject /* this */)
{
    const orcinus::orca::CustomFilaments result = orcinus::orca::custom_filaments();
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeCustomFilaments");
    const jmethodID constructor =
        env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)V");
    std::vector<std::string> ids;
    std::vector<std::string> names;
    for (const orcinus::orca::CustomFilament& filament : result.filaments) {
        ids.push_back(filament.id);
        names.push_back(filament.name);
    }
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        to_java(env, ids),
        to_java(env, names)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_filamentPresets(JNIEnv* env, jobject /* this */, jstring filament_id)
{
    const orcinus::orca::FilamentPresetList result = orcinus::orca::filament_presets(to_utf8(env, filament_id));
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeFilamentPresets");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
        "[Ljava/lang/String;[Ljava/lang/String;)V"
    );
    std::vector<std::string> printers;
    std::vector<std::string> presets;
    for (const orcinus::orca::FilamentPresetChoice& choice : result.presets) {
        printers.push_back(choice.printer);
        presets.push_back(choice.preset);
    }
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        to_java(env, result.name),
        to_java(env, result.vendor),
        to_java(env, result.type),
        to_java(env, result.serial),
        to_java(env, printers),
        to_java(env, presets)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_deleteFilamentPreset(
    JNIEnv* env,
    jobject /* this */,
    jstring preset,
    jobjectArray answer_ids,
    jbooleanArray answers
)
{
    return to_java(env, orcinus::orca::delete_filament_preset(to_utf8(env, preset), to_answers(env, answer_ids, answers)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_configExportOptions(JNIEnv* env, jobject /* this */, jlong kind)
{
    const orcinus::orca::ConfigExportOptions result = orcinus::orca::config_export_options(static_cast<orcinus::orca::ConfigExportKind>(kind));
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeConfigExportOptions");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;[Ljava/lang/String;[JLjava/lang/String;)V"
    );
    std::vector<std::string> names;
    std::vector<std::int64_t> counts;
    for (const orcinus::orca::ConfigExportEntry& entry : result.entries) {
        names.push_back(entry.name);
        counts.push_back(entry.count);
    }
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        to_java(env, names),
        to_java(env, counts),
        to_java(env, result.note)
    );
}

namespace {

// The presets one side of the dialog selects: the printer, the process and the
// filament, in the order NativeBindings passes them.
orcinus::orca::ComparedPresets to_compared(JNIEnv* env, jobjectArray names)
{
    const std::vector<std::string> values = to_strings(env, names);
    orcinus::orca::ComparedPresets presets;
    presets.printer = values.size() > 0 ? values[0] : std::string();
    presets.print = values.size() > 1 ? values[1] : std::string();
    presets.filament = values.size() > 2 ? values[2] : std::string();
    return presets;
}

// NativePresetKindComparison
jobject to_java(JNIEnv* env, const orcinus::orca::PresetKindComparison& compared)
{
    const char* item_class = "app/orcinus/shadow/slicing/nativebridge/NativePresetItem";
    const auto item = [](JNIEnv* item_env, const orcinus::orca::PresetItem& value) { return to_java(item_env, value); };
    const jclass compared_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePresetKindComparison");
    const jmethodID constructor = env->GetMethodID(
        compared_class,
        "<init>",
        "(J"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativePresetItem;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativePresetItem;"
        "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativePresetChange;"
        "Ljava/lang/String;Z)V"
    );
    return env->NewObject(
        compared_class,
        constructor,
        static_cast<jlong>(compared.kind),
        to_java_objects(env, item_class, compared.left_presets, item),
        to_java_objects(env, item_class, compared.right_presets, item),
        to_java(env, compared.left),
        to_java(env, compared.right),
        to_java(env, compared.problem),
        to_java_objects(
            env,
            "app/orcinus/shadow/slicing/nativebridge/NativePresetChange",
            compared.changes,
            [](JNIEnv* change_env, const orcinus::orca::PresetChange& change) { return to_java(change_env, change); }
        ),
        to_java(env, compared.edited),
        compared.edited_dirty ? JNI_TRUE : JNI_FALSE
    );
}

}  // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_comparePresets(
    JNIEnv* env,
    jobject /* this */,
    jobjectArray left,
    jobjectArray right,
    jboolean show_all
)
{
    const orcinus::orca::PresetComparison result =
        orcinus::orca::compare_presets(to_compared(env, left), to_compared(env, right), show_all == JNI_TRUE);
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePresetComparison");
    const jmethodID constructor =
        env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;[Lapp/orcinus/shadow/slicing/nativebridge/NativePresetKindComparison;)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        to_java_objects(
            env,
            "app/orcinus/shadow/slicing/nativebridge/NativePresetKindComparison",
            result.kinds,
            [](JNIEnv* kind_env, const orcinus::orca::PresetKindComparison& compared) { return to_java(kind_env, compared); }
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_transferPresetOptions(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jstring from,
    jstring to,
    jobjectArray options
)
{
    return to_java(
        env,
        orcinus::orca::transfer_preset_options(
            static_cast<orcinus::orca::PresetKind>(kind),
            to_utf8(env, from),
            to_utf8(env, to),
            to_strings(env, options)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_searchCatalog(JNIEnv* env, jobject /* this */)
{
    const orcinus::orca::SearchCatalog result = orcinus::orca::search_catalog();
    const jobjectArray options = to_java_objects(
        env,
        "app/orcinus/shadow/slicing/nativebridge/NativeSearchOption",
        result.options,
        [](JNIEnv* option_env, const orcinus::orca::SearchOption& option) {
            const jclass option_class = option_env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSearchOption");
            const jmethodID constructor = option_env->GetMethodID(
                option_class,
                "<init>",
                "(JLjava/lang/String;Ljava/lang/String;"
                "[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;"
                "[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;"
                "[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;J)V"
            );
            return option_env->NewObject(
                option_class,
                constructor,
                static_cast<jlong>(option.kind),
                to_java(option_env, option.key),
                to_java(option_env, option.id),
                to_java(option_env, option.page),
                to_java(option_env, option.group),
                to_java(option_env, option.label),
                static_cast<jlong>(option.mode)
            );
        }
    );
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSearchCatalog");
    const jmethodID constructor = result_class != nullptr
        ? env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;[Lapp/orcinus/shadow/slicing/nativebridge/NativeSearchOption;)V")
        : nullptr;
    return env->NewObject(result_class, constructor, static_cast<jlong>(result.status), to_java(env, result.message), options);
}

extern "C" JNIEXPORT void JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_selectPlate(JNIEnv* /* env */, jobject /* this */, jint index, jint count)
{
    orcinus::orca::select_plate(index, count);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_thumbnailSizes(
    JNIEnv* env,
    jobject /* this */,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile
)
{
    const orcinus::orca::ThumbnailSizes result =
        orcinus::orca::thumbnail_sizes(to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles));
    const jintArray sizes = env->NewIntArray(static_cast<jsize>(result.sizes.size()));
    if (!result.sizes.empty()) {
        env->SetIntArrayRegion(sizes, 0, static_cast<jsize>(result.sizes.size()), reinterpret_cast<const jint*>(result.sizes.data()));
    }
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeThumbnailSizes");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;[I)V");
    return env->NewObject(result_class, constructor, static_cast<jlong>(result.status), to_java(env, result.message), sizes);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_validatePlate(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jobjectArray plate_setting_keys,
    jobjectArray plate_setting_values,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile
)
{
    const orcinus::orca::PlateValidation result = orcinus::orca::validate_plate(
        to_plate(env, plate),
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
        to_model_settings(env, plate_setting_keys, plate_setting_values)
    );
    const auto ints = [env](const std::vector<std::int32_t>& values) {
        const jintArray array = env->NewIntArray(static_cast<jsize>(values.size()));
        if (array != nullptr && !values.empty()) {
            env->SetIntArrayRegion(array, 0, static_cast<jsize>(values.size()), reinterpret_cast<const jint*>(values.data()));
        }
        return array;
    };
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePlateValidation");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(ZLjava/lang/String;IILjava/lang/String;Ljava/lang/String;IILjava/lang/String;[I[D[D[D[I)V"
    );
    return env->NewObject(
        result_class,
        constructor,
        result.read ? JNI_TRUE : JNI_FALSE,
        to_java(env, result.error.text),
        static_cast<jint>(result.error.object),
        static_cast<jint>(result.error.instance),
        to_java(env, result.error.option),
        to_java(env, result.warning.text),
        static_cast<jint>(result.warning.object),
        static_cast<jint>(result.warning.instance),
        to_java(env, result.warning.option),
        ints(result.clearance_counts),
        to_java(env, result.clearance.data(), result.clearance.size()),
        to_java(env, result.clearance_fill.data(), result.clearance_fill.size()),
        to_java(env, result.height_fill.data(), result.height_fill.size()),
        ints(result.sequence)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setTranslations(JNIEnv* env, jobject /* this */, jstring po)
{
    orcinus::orca::set_translations(to_utf8(env, po));
}

extern "C" JNIEXPORT jdouble JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_overhangNormalZ(
    JNIEnv* env,
    jobject /* this */,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile
)
{
    return orcinus::orca::overhang_normal_z(to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeGcodePlaceholders(JNIEnv* env, jobject /* this */, jlong kind, jstring key)
{
    const orcinus::orca::GcodePlaceholders result =
        orcinus::orca::describe_gcode_placeholders(static_cast<orcinus::orca::PresetKind>(kind), to_utf8(env, key));
    const jobjectArray placeholders = to_java_objects(
        env,
        "app/orcinus/shadow/slicing/nativebridge/NativeGcodePlaceholder",
        result.placeholders,
        [](JNIEnv* node_env, const orcinus::orca::GcodePlaceholder& node) {
            const jclass node_class = node_env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeGcodePlaceholder");
            const jmethodID constructor = node_env->GetMethodID(
                node_class,
                "<init>",
                "(IJ[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V"
            );
            return node_env->NewObject(
                node_class,
                constructor,
                static_cast<jint>(node.parent),
                static_cast<jlong>(node.type),
                to_java(node_env, node.label),
                to_java(node_env, node.key),
                to_java(node_env, node.text),
                to_java(node_env, node.icon),
                node.expanded ? JNI_TRUE : JNI_FALSE
            );
        }
    );
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeGcodePlaceholders");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;Ljava/lang/String;[Lapp/orcinus/shadow/slicing/nativebridge/NativeGcodePlaceholder;)V"
    );
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        to_java(env, result.value),
        placeholders
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeGcodePlaceholder(JNIEnv* env, jobject /* this */, jstring key, jboolean presets)
{
    const orcinus::orca::GcodePlaceholderInfo result = orcinus::orca::describe_gcode_placeholder(to_utf8(env, key), presets == JNI_TRUE);
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeGcodePlaceholderInfo");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "([Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;Ljava/lang/String;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;Z)V"
    );
    return env->NewObject(
        result_class,
        constructor,
        to_java(env, result.label),
        to_java(env, result.type),
        to_java(env, result.description),
        result.undefined ? JNI_TRUE : JNI_FALSE
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_editCustomGcode(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jstring page,
    jstring key,
    jstring value,
    jobjectArray answer_ids,
    jbooleanArray answers
)
{
    return to_java(
        env,
        orcinus::orca::edit_custom_gcode(
            static_cast<orcinus::orca::PresetKind>(kind),
            to_utf8(env, page),
            to_utf8(env, key),
            to_utf8(env, value),
            to_answers(env, answer_ids, answers)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setRammingParameters(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jstring page,
    jstring parameters,
    jobjectArray answer_ids,
    jbooleanArray answers
)
{
    return to_java(
        env,
        orcinus::orca::set_ramming_parameters(
            static_cast<orcinus::orca::PresetKind>(kind),
            to_utf8(env, page),
            to_utf8(env, parameters),
            to_answers(env, answer_ids, answers)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeBedShape(JNIEnv* env, jobject /* this */)
{
    const orcinus::orca::BedShapeState result = orcinus::orca::describe_bed_shape();
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeBedShape");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;JDDDDDLjava/lang/String;Ljava/lang/String;[D)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        static_cast<jlong>(result.kind),
        static_cast<jdouble>(result.size_x),
        static_cast<jdouble>(result.size_y),
        static_cast<jdouble>(result.origin_x),
        static_cast<jdouble>(result.origin_y),
        static_cast<jdouble>(result.diameter),
        to_java(env, result.texture),
        to_java(env, result.model),
        to_java(env, result.points.data(), result.points.size())
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setBedShape(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jdouble size_x,
    jdouble size_y,
    jdouble origin_x,
    jdouble origin_y,
    jdouble diameter,
    jstring custom_path,
    jstring texture,
    jstring model,
    jobjectArray answer_ids,
    jbooleanArray answers
)
{
    return to_java(
        env,
        orcinus::orca::set_bed_shape(
            static_cast<orcinus::orca::BedShapeKind>(kind),
            size_x,
            size_y,
            origin_x,
            origin_y,
            diameter,
            custom_path != nullptr ? to_utf8(env, custom_path) : std::string(),
            texture != nullptr ? to_utf8(env, texture) : std::string(),
            model != nullptr ? to_utf8(env, model) : std::string(),
            to_answers(env, answer_ids, answers)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setSettingsMode(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jlong mode,
    jobjectArray model_keys,
    jobjectArray model_values,
    jobjectArray plate_keys,
    jobjectArray plate_values,
    jobjectArray parent_keys,
    jobjectArray parent_values
)
{
    return to_java(
        env,
        orcinus::orca::set_settings_mode(
            static_cast<orcinus::orca::PresetKind>(kind),
            static_cast<orcinus::orca::SettingsMode>(mode),
            to_model_request(env, model_keys, model_values, plate_keys, plate_values, parent_keys, parent_values)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setSettingsVariant(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jstring page,
    jlong variant,
    jobjectArray answer_ids,
    jbooleanArray answers,
    jobjectArray model_keys,
    jobjectArray model_values,
    jobjectArray plate_keys,
    jobjectArray plate_values,
    jobjectArray parent_keys,
    jobjectArray parent_values
)
{
    return to_java(
        env,
        orcinus::orca::set_settings_variant(
            static_cast<orcinus::orca::PresetKind>(kind),
            to_utf8(env, page),
            static_cast<int>(variant),
            to_answers(env, answer_ids, answers),
            to_model_request(env, model_keys, model_values, plate_keys, plate_values, parent_keys, parent_values)
        )
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_settingTooltip(JNIEnv* env, jobject /* this */, jlong kind, jstring id)
{
    return to_java(env, orcinus::orca::setting_tooltip(static_cast<orcinus::orca::PresetKind>(kind), to_utf8(env, id)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_checkPresetName(JNIEnv* env, jobject /* this */, jlong kind, jstring name)
{
    const orcinus::orca::PresetNameValidation result =
        orcinus::orca::check_preset_name(static_cast<orcinus::orca::PresetKind>(kind), to_utf8(env, name));
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativePresetNameValidation");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;J[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;)V"
    );
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        static_cast<jlong>(result.check),
        to_java(env, result.info)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_savePreset(JNIEnv* env, jobject /* this */, jlong kind, jstring name)
{
    return to_java(env, orcinus::orca::save_preset(static_cast<orcinus::orca::PresetKind>(kind), to_utf8(env, name)));
}

jobject to_java(JNIEnv* env, const orcinus::orca::AppConfigValues& values)
{
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeAppConfigValues");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;[Ljava/lang/String;)V");
    return env->NewObject(result_class, constructor, static_cast<jlong>(values.status), to_java(env, values.message), to_java(env, values.values));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_appConfigValues(JNIEnv* env, jobject /* this */, jobjectArray keys)
{
    return to_java(env, orcinus::orca::app_config_values(to_strings(env, keys)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setAppConfigValue(JNIEnv* env, jobject /* this */, jstring key, jstring value)
{
    return to_java(env, orcinus::orca::set_app_config_value(to_utf8(env, key), to_utf8(env, value)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_recentProjects(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::recent_projects());
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setRecentProjects(JNIEnv* env, jobject /* this */, jobjectArray projects)
{
    return to_java(env, orcinus::orca::set_recent_projects(to_strings(env, projects)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_deletePreset(
    JNIEnv* env,
    jobject /* this */,
    jlong kind,
    jobjectArray answer_ids,
    jbooleanArray answers
)
{
    return to_java(env, orcinus::orca::delete_preset(static_cast<orcinus::orca::PresetKind>(kind), to_answers(env, answer_ids, answers)));
}

jbooleanArray to_java_bools(JNIEnv* env, const std::vector<bool>& values)
{
    std::vector<jboolean> flags;
    for (const bool value : values) {
        flags.push_back(value ? JNI_TRUE : JNI_FALSE);
    }
    const jbooleanArray array = env->NewBooleanArray(static_cast<jsize>(flags.size()));
    env->SetBooleanArrayRegion(array, 0, static_cast<jsize>(flags.size()), flags.data());
    return array;
}

// NativeImportedObject
static jobject to_java_imported(JNIEnv* env, const orcinus::orca::ImportedObject& object)
{
    std::vector<std::string> part_names;
    std::vector<std::string> part_paths;
    std::vector<jlong> part_types;
    std::vector<double> part_matrices;
    std::vector<orcinus::orca::ModelSettings> part_settings;
    std::vector<std::string> part_painted;
    std::vector<bool> part_splittable;
    std::vector<bool> part_inches;
    std::vector<bool> part_meters;
    std::vector<std::string> part_inputs;
    std::vector<std::string> part_emboss;
    std::vector<std::int64_t> part_emboss_kinds;
    for (const orcinus::orca::ImportedPart& part : object.parts) {
        part_emboss.push_back(part.emboss);
        part_emboss_kinds.push_back(static_cast<std::int64_t>(part.emboss_kind));
        part_inputs.push_back(part.input_file);
        part_names.push_back(part.name);
        part_paths.push_back(part.model_path);
        part_types.push_back(static_cast<jlong>(part.type));
        part_matrices.insert(part_matrices.end(), part.matrix.begin(), part.matrix.end());
        part_settings.push_back(part.settings);
        part_painted.push_back(part.painted);
        part_splittable.push_back(part.splittable);
        part_inches.push_back(part.from_inches);
        part_meters.push_back(part.from_meters);
    }
    std::vector<double> range_heights;
    std::vector<orcinus::orca::ModelSettings> range_settings;
    for (const orcinus::orca::LayerRange& range : object.layer_ranges) {
        range_heights.push_back(range.bottom);
        range_heights.push_back(range.top);
        range_settings.push_back(range.settings);
    }
    const jobjectArray instances = to_java_objects(
        env,
        "app/orcinus/shadow/slicing/nativebridge/NativeModelInspection",
        object.instances,
        [](JNIEnv* instance_env, const orcinus::orca::ModelInspection& instance) { return to_java(instance_env, instance); }
    );
    const jlongArray types = env->NewLongArray(static_cast<jsize>(part_types.size()));
    env->SetLongArrayRegion(types, 0, static_cast<jsize>(part_types.size()), part_types.data());
    const jclass object_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeImportedObject");
    const jmethodID constructor = env->GetMethodID(
        object_class,
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;[DLjava/lang/String;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeModelInspection;"
        "Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;"
        "[Ljava/lang/String;[J[Ljava/lang/String;[D"
        "[Ljava/lang/String;[Ljava/lang/String;[[Ljava/lang/String;[[Ljava/lang/String;"
        "Ljava/lang/String;ZZZ[Ljava/lang/String;[Z[Z[Z[D[[Ljava/lang/String;[[Ljava/lang/String;[Z[Z"
        "Ljava/lang/String;[Ljava/lang/String;[J[D[DLjava/lang/String;[D"
        "Ljava/lang/String;J[Ljava/lang/String;[J[D[D[Z[D[D[D)V"
    );
    // The cut the object is a part of, and the cut info of its own mesh and of every part.
    const jlong cut_id[3]{jlong(object.cut_id.id), jlong(object.cut_id.check_sum), jlong(object.cut_id.connectors_cnt)};
    const jlongArray cut_id_array = env->NewLongArray(3);
    env->SetLongArrayRegion(cut_id_array, 0, 3, cut_id);
    const auto cut_info_values = [](const orcinus::orca::VolumeCutInfo& info, std::vector<double>& values) {
        values.push_back(info.from_upper ? 1.0 : 0.0);
        values.push_back(info.connector ? 1.0 : 0.0);
        values.push_back(info.processed ? 1.0 : 0.0);
        values.push_back(double(info.connector_type));
        values.push_back(info.radius_tolerance);
        values.push_back(info.height_tolerance);
    };
    std::vector<double> volume_cut_info;
    cut_info_values(object.volume_cut_info, volume_cut_info);
    // Every copy's place in the assembly, 16 each (zero while it has none), and its offset to it, 3 each.
    std::vector<double> assemble_matrices;
    std::vector<bool> assembled;
    std::vector<double> offsets_to_assembly;
    for (std::size_t copy = 0; copy < object.instances.size(); ++copy) {
        const std::vector<double> matrix = copy < object.assemble_matrices.size() ? object.assemble_matrices[copy] : std::vector<double>{};
        assembled.push_back(matrix.size() == 16);
        if (matrix.size() == 16)
            assemble_matrices.insert(assemble_matrices.end(), matrix.begin(), matrix.end());
        else
            assemble_matrices.insert(assemble_matrices.end(), 16, 0.0);
        const std::vector<double> offset = copy < object.offsets_to_assembly.size() ? object.offsets_to_assembly[copy] : std::vector<double>{};
        if (offset.size() == 3)
            offsets_to_assembly.insert(offsets_to_assembly.end(), offset.begin(), offset.end());
        else
            offsets_to_assembly.insert(offsets_to_assembly.end(), 3, 0.0);
    }
    std::vector<double> part_cut_info;
    for (const orcinus::orca::ImportedPart& part : object.parts) {
        cut_info_values(part.cut_info, part_cut_info);
    }
    // Where its own mesh and every part was in its file, five each (VolumeOrigin).
    const auto origin_values = [](const orcinus::orca::VolumeOrigin& origin, std::vector<double>& values) {
        values.insert(values.end(), {double(origin.object_idx), double(origin.volume_idx), origin.mesh_offset[0], origin.mesh_offset[1], origin.mesh_offset[2]});
    };
    std::vector<double> volume_origin;
    origin_values(object.volume_origin, volume_origin);
    std::vector<double> part_origins;
    for (const orcinus::orca::ImportedPart& part : object.parts) {
        origin_values(part.origin, part_origins);
    }
    return env->NewObject(
        object_class,
        constructor,
        to_java(env, object.name),
        to_java(env, object.model_path),
        to_java(env, object.matrix.data(), object.matrix.size()),
        to_java(env, object.mesh_path),
        instances,
        to_java(env, object.volume_name),
        to_java(env, object.volume_settings.keys),
        to_java(env, object.volume_settings.values),
        to_java(env, part_names),
        types,
        to_java(env, part_paths),
        to_java(env, part_matrices.data(), part_matrices.size()),
        to_java(env, object.settings.keys),
        to_java(env, object.settings.values),
        to_java(env, part_settings, true),
        to_java(env, part_settings, false),
        to_java(env, object.painted),
        object.volume_splittable ? JNI_TRUE : JNI_FALSE,
        object.volume_from_inches ? JNI_TRUE : JNI_FALSE,
        object.volume_from_meters ? JNI_TRUE : JNI_FALSE,
        to_java(env, part_painted),
        to_java_bools(env, part_splittable),
        to_java_bools(env, part_inches),
        to_java_bools(env, part_meters),
        to_java(env, range_heights.data(), range_heights.size()),
        to_java(env, range_settings, true),
        to_java(env, range_settings, false),
        to_java_bools(env, object.auto_drops),
        to_java_bools(env, object.printables),
        to_java(env, object.volume_input_file),
        to_java(env, part_inputs),
        cut_id_array,
        to_java(env, volume_cut_info.data(), volume_cut_info.size()),
        to_java(env, part_cut_info.data(), part_cut_info.size()),
        to_java(env, object.input_file),
        to_java(env, object.layer_height_profile.data(), object.layer_height_profile.size()),
        to_java(env, object.volume_emboss),
        static_cast<jlong>(object.volume_emboss_kind),
        to_java(env, part_emboss),
        to_java(env, part_emboss_kinds),
        to_java(env, object.brim_points.data(), object.brim_points.size()),
        to_java(env, assemble_matrices.data(), assemble_matrices.size()),
        to_java_bools(env, assembled),
        to_java(env, offsets_to_assembly.data(), offsets_to_assembly.size()),
        to_java(env, volume_origin.data(), volume_origin.size()),
        to_java(env, part_origins.data(), part_origins.size())
    );
}

// NativeProjectPlate, without a picture.
static jobject to_java(JNIEnv* env, const orcinus::orca::ProjectPlate& plate)
{
    const jclass plate_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeProjectPlate");
    const jmethodID constructor = env->GetMethodID(
        plate_class,
        "<init>",
        "(Ljava/lang/String;Z[Ljava/lang/String;[Ljava/lang/String;[D[J[I[Ljava/lang/String;[Ljava/lang/String;IILjava/lang/String;"
        "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
    );
    std::vector<double> heights;
    std::vector<jlong> types;
    std::vector<jint> extruders;
    std::vector<std::string> colors;
    std::vector<std::string> extras;
    for (const orcinus::orca::LayerGcode& code : plate.layer_gcodes) {
        heights.push_back(code.print_z);
        types.push_back(static_cast<jlong>(code.type));
        extruders.push_back(code.extruder);
        colors.push_back(code.color);
        extras.push_back(code.extra);
    }
    const jlongArray type_array = env->NewLongArray(static_cast<jsize>(types.size()));
    env->SetLongArrayRegion(type_array, 0, static_cast<jsize>(types.size()), types.data());
    const jintArray extruder_array = env->NewIntArray(static_cast<jsize>(extruders.size()));
    env->SetIntArrayRegion(extruder_array, 0, static_cast<jsize>(extruders.size()), extruders.data());
    return env->NewObject(
        plate_class,
        constructor,
        to_java(env, plate.name),
        plate.locked ? JNI_TRUE : JNI_FALSE,
        to_java(env, plate.settings.keys),
        to_java(env, plate.settings.values),
        to_java(env, heights.data(), heights.size()),
        type_array,
        extruder_array,
        to_java(env, colors),
        to_java(env, extras),
        static_cast<jint>(plate.thumbnail.width),
        static_cast<jint>(plate.thumbnail.height),
        to_java(env, plate.thumbnail.path),
        to_java(env, plate.no_light_thumbnail.path),
        to_java(env, plate.top_thumbnail.path),
        to_java(env, plate.pick_thumbnail.path),
        to_java(env, plate.slice_info_path),
        to_java(env, plate.gcode_path)
    );
}

// NativeCalibration; null for none.
static jobject to_java(JNIEnv* env, const orcinus::orca::CalibrationParams& params)
{
    if (params.mode == orcinus::orca::CalibrationMode::none) {
        return nullptr;
    }
    const jclass calibration_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeCalibration");
    const jmethodID constructor = env->GetMethodID(calibration_class, "<init>", "(JIDDDZDDDDILjava/lang/String;[D[D)V");
    return env->NewObject(
        calibration_class,
        constructor,
        static_cast<jlong>(params.mode),
        static_cast<jint>(params.extruder_id),
        params.start,
        params.end,
        params.step,
        params.print_numbers ? JNI_TRUE : JNI_FALSE,
        params.freq_start_x,
        params.freq_end_x,
        params.freq_start_y,
        params.freq_end_y,
        static_cast<jint>(params.test_model),
        to_java(env, params.shaper_type),
        to_java(env, params.accelerations.data(), params.accelerations.size()),
        to_java(env, params.speeds.data(), params.speeds.size())
    );
}

// NativeImportedModels
static jobject to_java(JNIEnv* env, const orcinus::orca::ImportedModels& imported)
{
    const jobjectArray objects = to_java_objects(
        env,
        "app/orcinus/shadow/slicing/nativebridge/NativeImportedObject",
        imported.objects,
        [](JNIEnv* object_env, const orcinus::orca::ImportedObject& object) { return to_java_imported(object_env, object); }
    );
    const char* dialog_class = "app/orcinus/shadow/slicing/nativebridge/NativeSettingsDialog";
    const jobjectArray notices = to_java_objects(env, dialog_class, imported.notices, [](JNIEnv* dialog_env, const orcinus::orca::SettingsDialog& dialog) {
        return to_java(dialog_env, dialog);
    });
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeImportedModels");
    const jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(JLjava/lang/String;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeSettingsDialog;"
        "Z"
        "Lapp/orcinus/shadow/slicing/nativebridge/NativeSettingsDialog;"
        "[Lapp/orcinus/shadow/slicing/nativebridge/NativeImportedObject;ZI"
        "Z[Lapp/orcinus/shadow/slicing/nativebridge/NativeProjectPlate;ZLjava/lang/String;"
        "Lapp/orcinus/shadow/slicing/nativebridge/NativeCalibration;I"
        "ZDDZIZ)V"
    );
    const jobjectArray plates = to_java_objects(
        env,
        "app/orcinus/shadow/slicing/nativebridge/NativeProjectPlate",
        imported.plates,
        [](JNIEnv* plate_env, const orcinus::orca::ProjectPlate& plate) { return to_java(plate_env, plate); }
    );
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(imported.status),
        to_java(env, imported.message),
        notices,
        imported.has_question ? JNI_TRUE : JNI_FALSE,
        to_java(env, imported.question),
        objects,
        imported.appended ? JNI_TRUE : JNI_FALSE,
        static_cast<jint>(imported.selected_volume),
        imported.project ? JNI_TRUE : JNI_FALSE,
        plates,
        imported.presets_changed ? JNI_TRUE : JNI_FALSE,
        to_java(env, imported.project_info),
        to_java(env, imported.calibration),
        static_cast<jint>(imported.plate_count),
        imported.step_mesh ? JNI_TRUE : JNI_FALSE,
        static_cast<jdouble>(imported.step_linear_deflection),
        static_cast<jdouble>(imported.step_angle_deflection),
        imported.step_split_compound ? JNI_TRUE : JNI_FALSE,
        static_cast<jint>(imported.step_file),
        imported.split_to_objects ? JNI_TRUE : JNI_FALSE
    );
}

// StepMeshDialog's answer as the bridge passes it.
static orcinus::orca::StepMeshChoice to_step_mesh(jboolean chosen, jdouble linear, jdouble angle, jboolean split)
{
    orcinus::orca::StepMeshChoice choice;
    choice.chosen = chosen == JNI_TRUE;
    choice.linear_deflection = linear;
    choice.angle_deflection = angle;
    choice.split_compound = split == JNI_TRUE;
    return choice;
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_stepTriangleCount(JNIEnv* env, jobject /* this */, jstring path, jdouble linear, jdouble angle)
{
    return static_cast<jlong>(orcinus::orca::step_triangle_count(to_utf8(env, path), linear, angle));
}

extern "C" JNIEXPORT void JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_stopStepTriangleCount(JNIEnv* /* env */, jobject /* this */)
{
    orcinus::orca::stop_step_triangle_count();
}

extern "C" JNIEXPORT void JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_releaseStepFile(JNIEnv* /* env */, jobject /* this */)
{
    orcinus::orca::release_step_file();
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_exportObjectMesh(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object,
    jlong format,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring path
)
{
    const orcinus::orca::MeshExport exported = orcinus::orca::export_object_mesh(
        to_plate(env, plate),
        static_cast<std::size_t>(object),
        static_cast<orcinus::orca::MeshFormat>(format),
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
        to_utf8(env, path)
    );
    // status, message, warning.
    return to_java(env, std::vector<std::string>{std::to_string(static_cast<int>(exported.status)), exported.message, exported.warning});
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_simplifyVolume(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object,
    jint volume,
    jboolean use_count,
    jint wanted_count,
    jfloat decimate_ratio,
    jfloat max_error,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring path
)
{
    orcinus::orca::SimplifyConfig config;
    config.use_count = use_count == JNI_TRUE;
    config.wanted_count = wanted_count;
    config.decimate_ratio = decimate_ratio;
    config.max_error = max_error;
    const orcinus::orca::SimplifiedVolume simplified = orcinus::orca::simplify_volume(
        to_plate(env, plate),
        static_cast<std::size_t>(object),
        static_cast<std::size_t>(volume),
        config,
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
        to_utf8(env, path)
    );
    // status, message, triangles, the volume's own triangles.
    return to_java(env, std::vector<std::string>{std::to_string(static_cast<int>(simplified.status)), simplified.message,
                                                 std::to_string(simplified.triangle_count), std::to_string(simplified.original_count)});
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_setVolumeType(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object,
    jint volume,
    jlong type,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::set_volume_type(
            to_plate(env, plate),
            static_cast<std::size_t>(object),
            static_cast<std::size_t>(volume),
            static_cast<orcinus::orca::VolumeType>(type),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_applySimplify(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object,
    jint volume,
    jboolean use_count,
    jint wanted_count,
    jfloat decimate_ratio,
    jfloat max_error,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    orcinus::orca::SimplifyConfig config;
    config.use_count = use_count == JNI_TRUE;
    config.wanted_count = wanted_count;
    config.decimate_ratio = decimate_ratio;
    config.max_error = max_error;
    return to_java(
        env,
        orcinus::orca::apply_simplify(
            to_plate(env, plate),
            static_cast<std::size_t>(object),
            static_cast<std::size_t>(volume),
            config,
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_pasteModelSettings(
    JNIEnv* env,
    jobject /* this */,
    jobjectArray clipboard_keys,
    jobjectArray clipboard_values,
    jobjectArray target_keys,
    jobjectArray target_values,
    jboolean part,
    jobjectArray object_keys,
    jobjectArray object_values
)
{
    const orcinus::orca::PastedSettings pasted = orcinus::orca::paste_model_settings(
        to_model_settings(env, clipboard_keys, clipboard_values),
        to_model_settings(env, target_keys, target_values),
        part == JNI_TRUE,
        to_model_settings(env, object_keys, object_values)
    );
    // status, message, then every key with its value.
    std::vector<std::string> answer{std::to_string(static_cast<int>(pasted.status)), pasted.message};
    for (std::size_t i = 0; i < pasted.settings.keys.size() && i < pasted.settings.values.size(); ++i) {
        answer.push_back(pasted.settings.keys[i]);
        answer.push_back(pasted.settings.values[i]);
    }
    return to_java(env, answer);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_replaceVolume(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object,
    jint volume,
    jstring source_path,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix,
    jboolean step_chosen,
    jdouble step_linear,
    jdouble step_angle,
    jboolean step_split
)
{
    return to_java(
        env,
        orcinus::orca::replace_volume(
            to_plate(env, plate),
            static_cast<std::size_t>(object),
            static_cast<std::size_t>(volume),
            to_utf8(env, source_path),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix),
            to_step_mesh(step_chosen, step_linear, step_angle, step_split)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_loadVolume(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object,
    jstring source_path,
    jstring name,
    jlong type,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix,
    jboolean step_chosen,
    jdouble step_linear,
    jdouble step_angle,
    jboolean step_split
)
{
    return to_java(
        env,
        orcinus::orca::load_volume(
            to_plate(env, plate),
            static_cast<std::size_t>(object),
            to_utf8(env, source_path),
            to_utf8(env, name),
            static_cast<orcinus::orca::VolumeType>(type),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix),
            to_step_mesh(step_chosen, step_linear, step_angle, step_split)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_addPrimitive(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jstring shape,
    jstring name,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::add_primitive(
            to_plate(env, plate),
            to_utf8(env, shape),
            to_utf8(env, name),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_prepareCalibration(
    JNIEnv* env,
    jobject /* this */,
    jobject calibration,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::prepare_calibration(
            to_calibration(env, calibration),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeCalibrationPrinter(
    JNIEnv* env,
    jobject /* this */,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile
)
{
    const orcinus::orca::CalibrationPrinter printer = orcinus::orca::describe_calibration_printer(
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles)
    );
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeCalibrationPrinter");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;Ljava/lang/String;Z[Ljava/lang/String;)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(printer.status),
        to_java(env, printer.message),
        to_java(env, printer.gcode_flavor),
        printer.junction_deviation ? JNI_TRUE : JNI_FALSE,
        to_java(env, printer.shaper_types)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_prepareFlowRateCalibration(
    JNIEnv* env,
    jobject /* this */,
    jboolean linear,
    jint pass,
    jstring top_surface_pattern,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::prepare_flow_rate_calibration(
            linear == JNI_TRUE,
            static_cast<int>(pass),
            to_utf8(env, top_surface_pattern),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_pasteVolumes(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object,
    jint instance,
    jobject source,
    jintArray volumes,
    jboolean same_input_file,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    const std::vector<orcinus::orca::PlateObject> sources = to_plate(env, source);
    if (sources.size() != 1) {
        orcinus::orca::ImportedModels refused;
        refused.message = "The clipboard holds one object";
        return to_java(env, refused);
    }
    const std::vector<std::int32_t> taken = to_ints(env, volumes);
    return to_java(
        env,
        orcinus::orca::paste_volumes(
            to_plate(env, plate),
            static_cast<std::size_t>(object),
            static_cast<std::size_t>(instance),
            sources.front(),
            std::vector<int>(taken.begin(), taken.end()),
            same_input_file == JNI_TRUE,
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_importModel(
    JNIEnv* env,
    jobject /* this */,
    jobjectArray source_paths,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jobject plate,
    jstring output_prefix,
    jobjectArray answer_ids,
    jbooleanArray answers,
    jlong load,
    jboolean chosen,
    jbooleanArray step_chosen,
    jdoubleArray step_linear,
    jdoubleArray step_angle,
    jbooleanArray step_split,
    jboolean ask_multi
)
{
    // StepMeshDialog's answer for every file.
    const std::vector<bool> chosen_meshes = to_bools(env, step_chosen);
    const std::vector<double> linear = to_doubles(env, step_linear);
    const std::vector<double> angle = to_doubles(env, step_angle);
    const std::vector<bool> split = to_bools(env, step_split);
    std::vector<orcinus::orca::StepMeshChoice> step_meshes;
    for (std::size_t index = 0; index < chosen_meshes.size() && index < linear.size() && index < angle.size() && index < split.size(); ++index) {
        step_meshes.push_back(to_step_mesh(chosen_meshes[index] ? JNI_TRUE : JNI_FALSE, linear[index], angle[index], split[index] ? JNI_TRUE : JNI_FALSE));
    }
    return to_java(
        env,
        orcinus::orca::import_models(
            to_strings(env, source_paths),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_plate(env, plate),
            to_utf8(env, output_prefix),
            to_answers(env, answer_ids, answers),
            static_cast<orcinus::orca::ModelLoad>(load),
            chosen == JNI_TRUE,
            step_meshes,
            ask_multi == JNI_TRUE
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_editObject(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object,
    jlong edit,
    jint volume,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix,
    jobjectArray answer_ids,
    jbooleanArray answers,
    jint cut_instance,
    jdoubleArray cut_plane,
    jbooleanArray cut_flags,
    jdoubleArray connector_values,
    jintArray connector_kinds,
    jdouble snap_space,
    jdouble snap_bulge,
    jstring connector_name,
    jboolean dovetail,
    jdoubleArray groove,
    jdouble radius,
    jdoubleArray parts_plane,
    jbooleanArray parts
)
{
    orcinus::orca::ObjectCut cut;
    cut.instance = cut_instance;
    cut.plane = to_doubles(env, cut_plane);
    cut.connectors = to_connectors(env, connector_values, connector_kinds);
    cut.snap_space = snap_space;
    cut.snap_bulge = snap_bulge;
    cut.connector_name = to_utf8(env, connector_name);
    cut.dovetail = dovetail == JNI_TRUE;
    cut.groove = to_groove(env, groove);
    cut.radius = radius;
    cut.parts_plane = to_doubles(env, parts_plane);
    for (const bool part : to_bools(env, parts)) {
        cut.parts.push_back(part ? 1 : 0);
    }
    // keep_upper, keep_lower, keep_as_parts, place_on_cut_upper,
    // place_on_cut_lower, flip_upper, flip_lower
    const std::vector<bool> flags = to_bools(env, cut_flags);
    if (flags.size() == 7) {
        cut.keep_upper = flags[0];
        cut.keep_lower = flags[1];
        cut.keep_as_parts = flags[2];
        cut.place_on_cut_upper = flags[3];
        cut.place_on_cut_lower = flags[4];
        cut.flip_upper = flags[5];
        cut.flip_lower = flags[6];
    }
    return to_java(
        env,
        orcinus::orca::edit_object(
            to_plate(env, plate),
            static_cast<std::size_t>(object),
            static_cast<orcinus::orca::ObjectEdit>(edit),
            volume,
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix),
            to_answers(env, answer_ids, answers),
            cut
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_beginCut(
    JNIEnv* env,
    jobject /* this */,
    jobject object,
    jint instance,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile
)
{
    const std::vector<orcinus::orca::PlateObject> plate = to_plate(env, object);
    const orcinus::orca::CutObject cut = orcinus::orca::begin_cut(
        plate.empty() ? orcinus::orca::PlateObject{} : plate.front(),
        instance,
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles)
    );
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeCutObject");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;[D[D)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(cut.status),
        to_java(env, cut.message),
        to_java(env, cut.min, 3),
        to_java(env, cut.max, 3)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeCutPlane(
    JNIEnv* env,
    jobject /* this */,
    jdoubleArray plane,
    jdoubleArray connector_values,
    jintArray connector_kinds,
    jdouble snap_space,
    jdouble snap_bulge,
    jboolean dovetail,
    jdoubleArray groove,
    jboolean preview,
    jstring mesh_prefix,
    jdoubleArray parts_plane,
    jbooleanArray parts
)
{
    std::vector<int> selected;
    for (const bool part : to_bools(env, parts)) {
        selected.push_back(part ? 1 : 0);
    }
    const orcinus::orca::CutPlane described = orcinus::orca::describe_cut_plane(
        to_doubles(env, plane),
        to_connectors(env, connector_values, connector_kinds),
        snap_space,
        snap_bulge,
        dovetail == JNI_TRUE,
        to_groove(env, groove),
        preview == JNI_TRUE,
        to_utf8(env, mesh_prefix),
        to_doubles(env, parts_plane),
        selected
    );
    std::vector<std::string> preview_meshes;
    std::vector<bool> preview_upper;
    std::vector<bool> preview_modifiers;
    for (const auto& part : described.preview_parts) {
        preview_meshes.push_back(part.mesh);
        preview_upper.push_back(part.upper);
        preview_modifiers.push_back(part.modifier);
    }
    const auto booleans = [env](const std::vector<bool>& values) {
        const jbooleanArray array = env->NewBooleanArray(static_cast<jsize>(values.size()));
        std::vector<jboolean> flags(values.begin(), values.end());
        if (!flags.empty()) env->SetBooleanArrayRegion(array, 0, static_cast<jsize>(flags.size()), flags.data());
        return array;
    };
    const jintArray invalid = env->NewIntArray(static_cast<jsize>(described.invalid_connectors.size()));
    if (!described.invalid_connectors.empty()) {
        env->SetIntArrayRegion(invalid, 0, static_cast<jsize>(described.invalid_connectors.size()),
                               reinterpret_cast<const jint*>(described.invalid_connectors.data()));
    }
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeCutPlane");
    const jmethodID constructor = env->GetMethodID(
        result_class, "<init>", "(JLjava/lang/String;[D[DZLjava/lang/String;Ljava/lang/String;[IIIZ[Ljava/lang/String;Ljava/lang/String;Z[Ljava/lang/String;[Z[Z)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(described.status),
        to_java(env, described.message),
        to_java(env, described.min, 3),
        to_java(env, described.max, 3),
        described.valid_contour ? JNI_TRUE : JNI_FALSE,
        to_java(env, described.contour),
        to_java(env, described.section),
        invalid,
        static_cast<jint>(described.outside_cut_contour),
        static_cast<jint>(described.outside_bounding_box),
        described.overlap ? JNI_TRUE : JNI_FALSE,
        to_java(env, described.connector_meshes),
        to_java(env, described.groove_plane),
        described.valid_groove ? JNI_TRUE : JNI_FALSE,
        to_java(env, preview_meshes),
        booleans(preview_upper),
        booleans(preview_modifiers)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_selectCutPart(
    JNIEnv* env,
    jobject /* this */,
    jdoubleArray parts_plane,
    jbooleanArray selected,
    jdoubleArray origin,
    jdoubleArray direction,
    jstring mesh_prefix
)
{
    std::vector<int> flags;
    for (const bool part : to_bools(env, selected)) {
        flags.push_back(part ? 1 : 0);
    }
    const std::vector<double> ray_origin = to_doubles(env, origin);
    const std::vector<double> ray_direction = to_doubles(env, direction);
    const double from[3]{ray_origin.size() == 3 ? ray_origin[0] : 0.0, ray_origin.size() == 3 ? ray_origin[1] : 0.0, ray_origin.size() == 3 ? ray_origin[2] : 0.0};
    const double along[3]{ray_direction.size() == 3 ? ray_direction[0] : 0.0, ray_direction.size() == 3 ? ray_direction[1] : 0.0,
                          ray_direction.size() == 3 ? ray_direction[2] : 0.0};
    const orcinus::orca::CutParts selection = orcinus::orca::select_cut_part(to_doubles(env, parts_plane), flags, from, along, to_utf8(env, mesh_prefix));
    std::vector<std::string> meshes;
    std::vector<jboolean> upper;
    std::vector<jboolean> modifiers;
    for (const auto& part : selection.parts) {
        meshes.push_back(part.mesh);
        upper.push_back(part.upper ? JNI_TRUE : JNI_FALSE);
        modifiers.push_back(part.modifier ? JNI_TRUE : JNI_FALSE);
    }
    const jbooleanArray upper_array = env->NewBooleanArray(static_cast<jsize>(upper.size()));
    if (!upper.empty()) env->SetBooleanArrayRegion(upper_array, 0, static_cast<jsize>(upper.size()), upper.data());
    const jbooleanArray modifier_array = env->NewBooleanArray(static_cast<jsize>(modifiers.size()));
    if (!modifiers.empty()) env->SetBooleanArrayRegion(modifier_array, 0, static_cast<jsize>(modifiers.size()), modifiers.data());
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeCutParts");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;[Ljava/lang/String;[Z[Z)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(selection.status),
        to_java(env, selection.message),
        to_java(env, meshes),
        upper_array,
        modifier_array
    );
}

extern "C" JNIEXPORT void JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_endCut(JNIEnv* /* env */, jobject /* this */)
{
    orcinus::orca::end_cut();
}

// NativeLayerEditing
static jobject to_java(JNIEnv* env, const orcinus::orca::LayerEditing& editing)
{
    const jclass editing_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeLayerEditing");
    const jmethodID constructor = env->GetMethodID(editing_class, "<init>", "(JLjava/lang/String;[D[DDDDDDZ)V");
    return env->NewObject(
        editing_class,
        constructor,
        static_cast<jlong>(editing.status),
        to_java(env, editing.message),
        to_java(env, editing.profile.data(), editing.profile.size()),
        to_java(env, editing.layers.data(), editing.layers.size()),
        static_cast<jdouble>(editing.object_max_z),
        static_cast<jdouble>(editing.layer_height),
        static_cast<jdouble>(editing.min_layer_height),
        static_cast<jdouble>(editing.max_layer_height),
        static_cast<jdouble>(editing.object_print_z_height),
        editing.fixed ? JNI_TRUE : JNI_FALSE
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_beginLayerEditing(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object_index,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jobjectArray plate_setting_keys,
    jobjectArray plate_setting_values
)
{
    return to_java(
        env,
        orcinus::orca::begin_layer_editing(
            to_plate(env, plate),
            static_cast<int>(object_index),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_model_settings(env, plate_setting_keys, plate_setting_values)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_editLayerHeights(
    JNIEnv* env, jobject /* this */, jlong action, jdouble z, jdouble strength, jdouble band_width)
{
    return to_java(env, orcinus::orca::edit_layer_heights(static_cast<orcinus::orca::LayerHeightEdit>(action), z, strength, band_width));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_adaptiveLayerHeights(JNIEnv* env, jobject /* this */, jdouble quality)
{
    return to_java(env, orcinus::orca::adaptive_layer_heights(quality));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_smoothLayerHeights(JNIEnv* env, jobject /* this */, jint radius, jboolean keep_min)
{
    return to_java(env, orcinus::orca::smooth_layer_heights(static_cast<int>(radius), keep_min == JNI_TRUE));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_resetLayerHeights(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::reset_layer_heights());
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_acceptLayerHeights(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::accept_layer_heights());
}

extern "C" JNIEXPORT void JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_endLayerEditing(JNIEnv* /* env */, jobject /* this */)
{
    orcinus::orca::end_layer_editing();
}

// NativeTextStyle, whose NaN numbers are unset.
static orcinus::orca::TextStyle to_text_style(JNIEnv* env, jobject style)
{
    const jclass style_class = env->GetObjectClass(style);
    const auto text = [env, style, style_class](const char* name) {
        return to_utf8(env, static_cast<jstring>(env->GetObjectField(style, env->GetFieldID(style_class, name, "Ljava/lang/String;"))));
    };
    const auto number = [env, style, style_class](const char* name) {
        return static_cast<double>(env->GetDoubleField(style, env->GetFieldID(style_class, name, "D")));
    };
    const auto flag = [env, style, style_class](const char* name) {
        return env->GetBooleanField(style, env->GetFieldID(style_class, name, "Z")) == JNI_TRUE;
    };
    const auto whole = [env, style, style_class](const char* name) {
        return static_cast<int>(env->GetIntField(style, env->GetFieldID(style_class, name, "I")));
    };
    const auto optional = [&number](const char* name) {
        const double value = number(name);
        return std::isnan(value) ? std::optional<double>() : std::optional<double>(value);
    };
    const auto optional_int = [&optional](const char* name) {
        const std::optional<double> value = optional(name);
        return value.has_value() ? std::optional<int>(int(std::lround(*value))) : std::optional<int>();
    };
    orcinus::orca::TextStyle result;
    result.name = text("name");
    result.font_path = text("fontPath");
    result.size_in_mm = number("sizeInMm");
    result.per_glyph = flag("perGlyph");
    result.horizontal_align = whole("horizontalAlign");
    result.vertical_align = whole("verticalAlign");
    result.char_gap = optional_int("charGap");
    result.line_gap = optional_int("lineGap");
    result.boldness = optional("boldness");
    result.skew = optional("skew");
    result.collection_number = optional_int("collectionNumber");
    result.family = text("family");
    result.face_name = text("faceName");
    result.style = text("style");
    result.weight = text("weight");
    result.depth = number("depth");
    result.use_surface = flag("useSurface");
    result.angle = optional("angle");
    result.distance = optional("distance");
    env->DeleteLocalRef(style_class);
    return result;
}

static jobject to_java(JNIEnv* env, const orcinus::orca::TextStyle& style)
{
    const jclass style_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeTextStyle");
    const jmethodID constructor = env->GetMethodID(
        style_class,
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;DZIIDDDDDLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;DZDD)V"
    );
    const auto unset = std::numeric_limits<double>::quiet_NaN();
    return env->NewObject(
        style_class,
        constructor,
        to_java(env, style.name),
        to_java(env, style.font_path),
        static_cast<jdouble>(style.size_in_mm),
        style.per_glyph ? JNI_TRUE : JNI_FALSE,
        static_cast<jint>(style.horizontal_align),
        static_cast<jint>(style.vertical_align),
        static_cast<jdouble>(style.char_gap.has_value() ? double(*style.char_gap) : unset),
        static_cast<jdouble>(style.line_gap.has_value() ? double(*style.line_gap) : unset),
        static_cast<jdouble>(style.boldness.value_or(unset)),
        static_cast<jdouble>(style.skew.value_or(unset)),
        static_cast<jdouble>(style.collection_number.has_value() ? double(*style.collection_number) : unset),
        to_java(env, style.family),
        to_java(env, style.face_name),
        to_java(env, style.style),
        to_java(env, style.weight),
        static_cast<jdouble>(style.depth),
        style.use_surface ? JNI_TRUE : JNI_FALSE,
        static_cast<jdouble>(style.angle.value_or(unset)),
        static_cast<jdouble>(style.distance.value_or(unset))
    );
}

// EmbossPlacement from its arrays, empty for what it leaves out.
static orcinus::orca::EmbossPlacement to_placement(
    JNIEnv* env, jint object_index, jint instance_index, jdoubleArray position, jdoubleArray normal, jdoubleArray bed_point)
{
    orcinus::orca::EmbossPlacement placement;
    placement.object_index = static_cast<int>(object_index);
    placement.instance_index = static_cast<int>(instance_index);
    placement.position = to_doubles(env, position);
    placement.normal = to_doubles(env, normal);
    placement.bed_point = to_doubles(env, bed_point);
    return placement;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeFonts(JNIEnv* env, jobject /* this */, jobjectArray paths)
{
    return to_java_objects(
        env,
        "app/orcinus/shadow/slicing/nativebridge/NativeFontFace",
        orcinus::orca::describe_fonts(to_strings(env, paths)),
        [](JNIEnv* face_env, const orcinus::orca::FontFace& face) {
            const jclass face_class = face_env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeFontFace");
            const jmethodID constructor = face_env->GetMethodID(face_class, "<init>", "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;IZI)V");
            const jobject made = face_env->NewObject(
                face_class,
                constructor,
                to_java(face_env, face.path),
                static_cast<jint>(face.index),
                to_java(face_env, face.family),
                to_java(face_env, face.subfamily),
                static_cast<jint>(face.weight),
                face.italic ? JNI_TRUE : JNI_FALSE,
                static_cast<jint>(face.ascent)
            );
            face_env->DeleteLocalRef(face_class);
            return made;
        }
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_createText(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object_index,
    jint instance_index,
    jdoubleArray position,
    jdoubleArray normal,
    jdoubleArray bed_point,
    jlong type,
    jstring text,
    jobject style,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::create_text(
            to_plate(env, plate),
            to_placement(env, object_index, instance_index, position, normal, bed_point),
            static_cast<orcinus::orca::VolumeType>(type),
            to_utf8(env, text),
            to_text_style(env, style),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_updateText(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object_index,
    jint volume_index,
    jstring text,
    jobject style,
    jdoubleArray matrix,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::update_text(
            to_plate(env, plate),
            static_cast<std::size_t>(object_index),
            static_cast<std::size_t>(volume_index),
            to_utf8(env, text),
            to_text_style(env, style),
            to_doubles(env, matrix),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_createSvg(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object_index,
    jint instance_index,
    jdoubleArray position,
    jdoubleArray normal,
    jdoubleArray bed_point,
    jlong type,
    jstring svg_path,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::create_svg(
            to_plate(env, plate),
            to_placement(env, object_index, instance_index, position, normal, bed_point),
            static_cast<orcinus::orca::VolumeType>(type),
            to_utf8(env, svg_path),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_updateSvg(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object_index,
    jint volume_index,
    jdouble depth,
    jboolean use_surface,
    jstring svg_path,
    jdoubleArray matrix,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::update_svg(
            to_plate(env, plate),
            static_cast<std::size_t>(object_index),
            static_cast<std::size_t>(volume_index),
            depth,
            use_surface == JNI_TRUE,
            to_utf8(env, svg_path),
            to_doubles(env, matrix),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

// TextStyles of orca_engine_adapter.hpp as a NativeTextStyles.
static jobject to_java(JNIEnv* env, const orcinus::orca::TextStyles& styles)
{
    const jobjectArray items = to_java_objects(
        env,
        "app/orcinus/shadow/slicing/nativebridge/NativeTextStyle",
        styles.styles,
        [](JNIEnv* style_env, const orcinus::orca::TextStyle& style) { return to_java(style_env, style); }
    );
    const jclass styles_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeTextStyles");
    const jmethodID constructor = env->GetMethodID(
        styles_class,
        "<init>",
        "(JLjava/lang/String;[Lapp/orcinus/shadow/slicing/nativebridge/NativeTextStyle;J)V"
    );
    return env->NewObject(
        styles_class,
        constructor,
        static_cast<jlong>(styles.status),
        to_java(env, styles.message),
        items,
        static_cast<jlong>(styles.active)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_transformEmboss(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object_index,
    jint instance_index,
    jint volume_index,
    jdouble rotate,
    jdouble move,
    jdoubleArray camera_position,
    jdoubleArray camera_forward,
    jboolean perspective,
    jboolean keep_up,
    jdoubleArray scale,
    jint mirror,
    jstring text,
    jobject style,
    jboolean re_emboss,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    orcinus::orca::EmbossTransform transform;
    transform.rotate = rotate;
    transform.move = move;
    transform.camera_position = to_doubles(env, camera_position);
    transform.camera_forward = to_doubles(env, camera_forward);
    transform.perspective = perspective == JNI_TRUE;
    transform.keep_up = keep_up == JNI_TRUE;
    transform.scale = to_doubles(env, scale);
    transform.mirror = static_cast<int>(mirror);
    return to_java(
        env,
        orcinus::orca::transform_emboss(
            to_plate(env, plate),
            static_cast<std::size_t>(object_index),
            static_cast<std::size_t>(instance_index),
            static_cast<std::size_t>(volume_index),
            transform,
            to_utf8(env, text),
            to_text_style(env, style),
            re_emboss == JNI_TRUE,
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_previewSvg(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object_index,
    jint volume_index,
    jstring png_path,
    jint max_size,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile
)
{
    const orcinus::orca::SvgPreview preview = orcinus::orca::preview_svg(
        to_plate(env, plate),
        static_cast<std::size_t>(object_index),
        static_cast<std::size_t>(volume_index),
        to_utf8(env, png_path),
        static_cast<int>(max_size),
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles)
    );
    const jobjectArray warnings = to_java_objects(
        env,
        "app/orcinus/shadow/slicing/nativebridge/NativeSvgWarning",
        preview.warnings,
        [](JNIEnv* warning_env, const orcinus::orca::SvgWarning& warning) {
            const jclass warning_class = warning_env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSvgWarning");
            const jmethodID constructor = warning_env->GetMethodID(
                warning_class,
                "<init>",
                "(Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;[Lapp/orcinus/shadow/slicing/nativebridge/NativeUiText;)V"
            );
            const jobject made = warning_env->NewObject(warning_class, constructor, to_java(warning_env, warning.text), to_java(warning_env, warning.unsupported));
            warning_env->DeleteLocalRef(warning_class);
            return made;
        }
    );
    const jclass preview_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSvgPreview");
    const jmethodID constructor = env->GetMethodID(
        preview_class,
        "<init>",
        "(JLjava/lang/String;Ljava/lang/String;II[Lapp/orcinus/shadow/slicing/nativebridge/NativeSvgWarning;J)V"
    );
    return env->NewObject(
        preview_class,
        constructor,
        static_cast<jlong>(preview.status),
        to_java(env, preview.message),
        to_java(env, preview.svg_path),
        static_cast<jint>(preview.width),
        static_cast<jint>(preview.height),
        warnings,
        static_cast<jlong>(preview.points)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_editSvgFile(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object_index,
    jint volume_index,
    jlong edit,
    jstring path,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::edit_svg_file(
            to_plate(env, plate),
            static_cast<std::size_t>(object_index),
            static_cast<std::size_t>(volume_index),
            static_cast<orcinus::orca::SvgFileEdit>(edit),
            to_utf8(env, path),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_renameTextStyle(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object_index,
    jstring old_name,
    jstring new_name,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::rename_text_style(
            to_plate(env, plate),
            static_cast<std::size_t>(object_index),
            to_utf8(env, old_name),
            to_utf8(env, new_name),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_textStyles(JNIEnv* env, jobject /* this */)
{
    return to_java(env, orcinus::orca::load_text_styles());
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_storeTextStyles(JNIEnv* env, jobject /* this */, jobjectArray styles, jlong active)
{
    std::vector<orcinus::orca::TextStyle> stored;
    const jsize count = env->GetArrayLength(styles);
    stored.reserve(static_cast<std::size_t>(count));
    for (jsize index = 0; index < count; ++index) {
        const jobject style = env->GetObjectArrayElement(styles, index);
        stored.push_back(to_text_style(env, style));
        env->DeleteLocalRef(style);
    }
    return to_java(env, orcinus::orca::store_text_styles(stored, static_cast<std::int64_t>(active)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeEmboss(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object_index,
    jint volume_index,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile
)
{
    const orcinus::orca::EmbossVolume volume = orcinus::orca::describe_emboss(
        to_plate(env, plate),
        static_cast<std::size_t>(object_index),
        static_cast<std::size_t>(volume_index),
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles)
    );
    const jclass volume_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeEmbossVolume");
    const jmethodID constructor = env->GetMethodID(
        volume_class,
        "<init>",
        "(JLjava/lang/String;JLjava/lang/String;Lapp/orcinus/shadow/slicing/nativebridge/NativeTextStyle;Ljava/lang/String;ZDDJZDD[DD)V"
    );
    return env->NewObject(
        volume_class,
        constructor,
        static_cast<jlong>(volume.status),
        to_java(env, volume.message),
        static_cast<jlong>(volume.kind),
        to_java(env, volume.text),
        to_java(env, volume.style),
        to_java(env, volume.svg_name),
        volume.svg_reloadable ? JNI_TRUE : JNI_FALSE,
        static_cast<jdouble>(volume.width),
        static_cast<jdouble>(volume.height),
        static_cast<jlong>(volume.type),
        volume.only_part ? JNI_TRUE : JNI_FALSE,
        static_cast<jdouble>(volume.scale_height),
        static_cast<jdouble>(volume.scale_depth),
        to_java(env, volume.fix.data(), volume.fix.size()),
        static_cast<jdouble>(volume.scale_width)
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_copyObjects(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jobject sources,
    jint count,
    jlong placement,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::copy_objects(
            to_plate(env, plate),
            to_plate(env, sources),
            count,
            static_cast<orcinus::orca::CopyPlacement>(placement),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

// NativeMeasureFeature
static jobject to_java(JNIEnv* env, const orcinus::orca::MeasureFeature& feature)
{
    const jclass feature_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeMeasureFeature");
    const jmethodID constructor = env->GetMethodID(feature_class, "<init>", "(I[D[D[DD[F)V");
    const jobject result = env->NewObject(
        feature_class,
        constructor,
        static_cast<jint>(feature.type),
        to_java(env, feature.pt1.data(), feature.pt1.size()),
        to_java(env, feature.pt2.data(), feature.pt2.size()),
        to_java(env, feature.pt3.data(), feature.pt3.size()),
        static_cast<jdouble>(feature.value),
        to_java(env, feature.plane_triangles)
    );
    env->DeleteLocalRef(feature_class);
    return result;
}

// NativeMeasureItem
static jobject to_java(JNIEnv* env, const orcinus::orca::MeasureItem& item)
{
    const jclass item_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeMeasureItem");
    const jmethodID constructor = env->GetMethodID(
        item_class,
        "<init>",
        "(ZZLapp/orcinus/shadow/slicing/nativebridge/NativeMeasureFeature;Lapp/orcinus/shadow/slicing/nativebridge/NativeMeasureFeature;II)V"
    );
    const jobject result = env->NewObject(
        item_class,
        constructor,
        item.selected ? JNI_TRUE : JNI_FALSE,
        item.is_center ? JNI_TRUE : JNI_FALSE,
        to_java(env, item.source),
        to_java(env, item.feature),
        static_cast<jint>(item.object_index),
        static_cast<jint>(item.volume_index)
    );
    env->DeleteLocalRef(item_class);
    return result;
}

// NativeMeasureState: the angle (the angle, the radius, whether it is
// coplanar, the centre, then the two edges' ends) and the distances (the
// distance and its ends) flattened, empty for none.
static jobject to_java(JNIEnv* env, const orcinus::orca::MeasureState& state)
{
    std::vector<double> angle;
    if (state.has_angle) {
        angle = {state.angle, state.angle_radius, state.angle_coplanar ? 1.0 : 0.0};
        angle.insert(angle.end(), state.angle_center.begin(), state.angle_center.end());
        angle.insert(angle.end(), state.angle_edge_1.begin(), state.angle_edge_1.end());
        angle.insert(angle.end(), state.angle_edge_2.begin(), state.angle_edge_2.end());
    }
    const auto distance = [](bool has, double value, const std::vector<double>& from, const std::vector<double>& to) {
        std::vector<double> result;
        if (has) {
            result.push_back(value);
            result.insert(result.end(), from.begin(), from.end());
            result.insert(result.end(), to.begin(), to.end());
        }
        return result;
    };
    const std::vector<double> infinite = distance(state.has_infinite, state.infinite, state.infinite_from, state.infinite_to);
    const std::vector<double> strict = distance(state.has_strict, state.strict, state.strict_from, state.strict_to);
    const jclass state_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeMeasureState");
    const jmethodID constructor = env->GetMethodID(
        state_class,
        "<init>",
        "(JLjava/lang/String;Lapp/orcinus/shadow/slicing/nativebridge/NativeMeasureFeature;[DI"
        "Lapp/orcinus/shadow/slicing/nativebridge/NativeMeasureItem;Lapp/orcinus/shadow/slicing/nativebridge/NativeMeasureItem;"
        "[D[D[D[DZZZZZZZDIZZZZZ)V"
    );
    const jobject result = env->NewObject(
        state_class,
        constructor,
        static_cast<jlong>(state.status),
        to_java(env, state.message),
        to_java(env, state.hovered),
        to_java(env, state.hovered_point.data(), state.hovered_point.size()),
        static_cast<jint>(state.hovered_sphere),
        to_java(env, state.first),
        to_java(env, state.second),
        to_java(env, angle.data(), angle.size()),
        to_java(env, infinite.data(), infinite.size()),
        to_java(env, strict.data(), strict.size()),
        to_java(env, state.distance_xyz.data(), state.distance_xyz.size()),
        state.can_set_xyz_distance ? JNI_TRUE : JNI_FALSE,
        state.can_set_to_parallel ? JNI_TRUE : JNI_FALSE,
        state.can_set_to_center_coincidence ? JNI_TRUE : JNI_FALSE,
        state.can_set_feature_1_reverse_rotation ? JNI_TRUE : JNI_FALSE,
        state.can_set_feature_2_reverse_rotation ? JNI_TRUE : JNI_FALSE,
        state.can_around_center_of_faces ? JNI_TRUE : JNI_FALSE,
        state.has_parallel_distance ? JNI_TRUE : JNI_FALSE,
        static_cast<jdouble>(state.parallel_distance),
        static_cast<jint>(state.hit_volumes),
        state.same_object ? JNI_TRUE : JNI_FALSE,
        state.show_reset_first_tip ? JNI_TRUE : JNI_FALSE,
        state.hover_only ? JNI_TRUE : JNI_FALSE,
        state.hovered_unchanged ? JNI_TRUE : JNI_FALSE,
        state.wrong_feature_tip ? JNI_TRUE : JNI_FALSE
    );
    env->DeleteLocalRef(state_class);
    return result;
}

static orcinus::orca::MeasureRay to_measure_ray(
    JNIEnv* env, jdoubleArray origin, jdoubleArray direction, jboolean point_selection, jboolean only_select_plane, jdouble sphere_radius, jint assembly_mode)
{
    orcinus::orca::MeasureRay ray;
    ray.origin = to_doubles(env, origin);
    ray.direction = to_doubles(env, direction);
    ray.origin.resize(3, 0.0);
    ray.direction.resize(3, 0.0);
    ray.point_selection = point_selection == JNI_TRUE;
    ray.only_select_plane = only_select_plane == JNI_TRUE;
    ray.sphere_radius = sphere_radius;
    ray.assembly_mode = static_cast<int>(assembly_mode);
    return ray;
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_assemblySection(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jdoubleArray plane,
    jdouble explosion_ratio,
    jstring mesh_path
)
{
    const orcinus::orca::AssemblySection section = orcinus::orca::assembly_section(
        to_plate(env, plate),
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
        to_doubles(env, plane),
        static_cast<double>(explosion_ratio),
        to_utf8(env, mesh_path)
    );
    return to_java(env, section.mesh);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_beginMeasure(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jintArray selection,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jboolean assembly_view
)
{
    const std::vector<std::int32_t> triples = to_ints(env, selection);
    return to_java(
        env,
        orcinus::orca::begin_measure(
            to_plate(env, plate),
            std::vector<int>(triples.begin(), triples.end()),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            assembly_view == JNI_TRUE
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_hoverMeasure(
    JNIEnv* env,
    jobject /* this */,
    jdoubleArray origin,
    jdoubleArray direction,
    jboolean point_selection,
    jboolean only_select_plane,
    jdouble sphere_radius,
    jint assembly_mode
)
{
    return to_java(env, orcinus::orca::hover_measure(to_measure_ray(env, origin, direction, point_selection, only_select_plane, sphere_radius, assembly_mode)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_selectMeasure(
    JNIEnv* env,
    jobject /* this */,
    jdoubleArray origin,
    jdoubleArray direction,
    jboolean point_selection,
    jboolean only_select_plane,
    jdouble sphere_radius,
    jint assembly_mode
)
{
    return to_java(env, orcinus::orca::select_measure(to_measure_ray(env, origin, direction, point_selection, only_select_plane, sphere_radius, assembly_mode)));
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_resetMeasure(JNIEnv* env, jobject /* this */, jint selection)
{
    return to_java(env, orcinus::orca::reset_measure(static_cast<int>(selection)));
}

// NativeMeasureEdit
static jobject to_java(JNIEnv* env, const orcinus::orca::MeasureEdit& edited)
{
    const jintArray indexes = env->NewIntArray(static_cast<jsize>(edited.object_indexes.size()));
    if (indexes != nullptr && !edited.object_indexes.empty()) {
        std::vector<jint> values(edited.object_indexes.begin(), edited.object_indexes.end());
        env->SetIntArrayRegion(indexes, 0, static_cast<jsize>(values.size()), values.data());
    }
    const jclass edit_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeMeasureEdit");
    const jmethodID constructor = env->GetMethodID(
        edit_class,
        "<init>",
        "(Lapp/orcinus/shadow/slicing/nativebridge/NativeMeasureState;Lapp/orcinus/shadow/slicing/nativebridge/NativeImportedModels;[I)V"
    );
    const jobject result = env->NewObject(edit_class, constructor, to_java(env, edited.measure), to_java(env, edited.edit), indexes);
    env->DeleteLocalRef(edit_class);
    return result;
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_scaleMeasure(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jdouble ratio,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::scale_measure(
            to_plate(env, plate),
            ratio,
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_assembleMeasure(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jlong action,
    jdoubleArray values,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile,
    jstring output_prefix
)
{
    return to_java(
        env,
        orcinus::orca::assemble_measure(
            to_plate(env, plate),
            static_cast<orcinus::orca::AssemblyAction>(action),
            to_doubles(env, values),
            to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles),
            to_utf8(env, output_prefix)
        )
    );
}

extern "C" JNIEXPORT void JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_endMeasure(JNIEnv* /* env */, jobject /* this */)
{
    orcinus::orca::end_measure();
}

// NativeBrimEars
extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_beginBrimEars(
    JNIEnv* env,
    jobject /* this */,
    jobject plate,
    jint object_index,
    jint instance_index,
    jstring printer_profile,
    jstring filament_profile,
    jobjectArray filament_profiles,
    jstring process_profile
)
{
    const orcinus::orca::BrimEars ears = orcinus::orca::begin_brim_ears(
        to_plate(env, plate),
        static_cast<int>(object_index),
        static_cast<int>(instance_index),
        to_profiles(env, printer_profile, filament_profile, process_profile, filament_profiles)
    );
    const jclass ears_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeBrimEars");
    const jmethodID constructor = env->GetMethodID(ears_class, "<init>", "(JLjava/lang/String;DDZ)V");
    const jobject result = env->NewObject(
        ears_class,
        constructor,
        static_cast<jlong>(ears.status),
        to_java(env, ears.message),
        static_cast<jdouble>(ears.detection_radius_max),
        static_cast<jdouble>(ears.default_head_diameter),
        ears.painted ? JNI_TRUE : JNI_FALSE
    );
    env->DeleteLocalRef(ears_class);
    return result;
}

// The hit's position and the ear's, one after the other; empty without a hit.
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_hitBrimEars(JNIEnv* env, jobject /* this */, jdoubleArray origin, jdoubleArray direction)
{
    const orcinus::orca::BrimEarHit hit = orcinus::orca::hit_brim_ears(to_doubles(env, origin), to_doubles(env, direction));
    std::vector<double> values;
    if (hit.hit) {
        values = hit.position;
        values.insert(values.end(), hit.ear.begin(), hit.ear.end());
    }
    return to_java(env, values.data(), values.size());
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_generateBrimEars(
    JNIEnv* env, jobject /* this */, jdoubleArray points, jdouble max_angle, jdouble detection_radius, jdouble head_diameter)
{
    const std::vector<double> generated = orcinus::orca::generate_brim_ears(to_doubles(env, points), max_angle, detection_radius, head_diameter);
    return to_java(env, generated.data(), generated.size());
}

extern "C" JNIEXPORT jintArray JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_checkBrimEars(JNIEnv* env, jobject /* this */, jdoubleArray points)
{
    const std::vector<int> invalid = orcinus::orca::check_brim_ears(to_doubles(env, points));
    const jintArray result = env->NewIntArray(static_cast<jsize>(invalid.size()));
    if (result != nullptr && !invalid.empty()) {
        std::vector<jint> values(invalid.begin(), invalid.end());
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_endBrimEars(JNIEnv* /* env */, jobject /* this */)
{
    orcinus::orca::end_brim_ears();
}
