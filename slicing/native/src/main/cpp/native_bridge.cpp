#include <jni.h>

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

jfloatArray to_java(JNIEnv* env, const std::vector<float>& values)
{
    const auto length = static_cast<jsize>(values.size());
    const jfloatArray array = env->NewFloatArray(length);
    if (array != nullptr) {
        env->SetFloatArrayRegion(array, 0, length, values.data());
    }
    return array;
}

orcinus::orca::ProfileSelection to_profiles(JNIEnv* env, jstring printer, jstring filament, jstring process)
{
    orcinus::orca::ProfileSelection profiles;
    profiles.printer = to_utf8(env, printer);
    profiles.filament = to_utf8(env, filament);
    profiles.process = to_utf8(env, process);
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

// NativeModelInspection
jobject to_java(JNIEnv* env, const orcinus::orca::ModelInspection& inspection)
{
    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeModelInspection");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;JDDD[DJ[DD[D[D[D)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(inspection.status),
        to_java(env, inspection.message),
        static_cast<jlong>(inspection.facet_count),
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

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_slice(
    JNIEnv* env,
    jobject /* this */,
    jstring job_id,
    jstring model_path,
    jstring output_path,
    jstring printer_profile,
    jstring filament_profile,
    jstring process_profile,
    jdoubleArray placement,
    jboolean auto_drop,
    jobject progress_listener
)
{
    const orcinus::orca::ProfileSelection profiles = to_profiles(env, printer_profile, filament_profile, process_profile);
    orcinus::orca::ObjectPlacement transformation;
    transformation.auto_drop = auto_drop == JNI_TRUE;
    if (placement != nullptr) {
        transformation.matrix = to_doubles(env, placement);
    }
    const ProgressForwarder forward_progress(env, progress_listener);
    const orcinus::orca::SliceResult result = orcinus::orca::slice(
        to_utf8(env, job_id),
        to_utf8(env, model_path),
        to_utf8(env, output_path),
        profiles,
        transformation,
        [&forward_progress](const int percent, const std::string& message) { forward_progress(percent, message); }
    );

    const jclass result_class = env->FindClass("app/orcinus/shadow/slicing/nativebridge/NativeSliceResult");
    const jmethodID constructor = env->GetMethodID(result_class, "<init>", "(JLjava/lang/String;JJJ)V");
    return env->NewObject(
        result_class,
        constructor,
        static_cast<jlong>(result.status),
        to_java(env, result.message),
        static_cast<jlong>(result.layer_count),
        static_cast<jlong>(result.estimated_print_time_seconds),
        static_cast<jlong>(result.filament_micrometers)
    );
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
    jstring mesh_path
)
{
    const orcinus::orca::ModelInspection inspection = orcinus::orca::inspect_model(
        to_utf8(env, model_path),
        to_profiles(env, printer_profile, filament_profile, process_profile),
        to_utf8(env, mesh_path)
    );
    return to_java(env, inspection);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_placeModel(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jstring printer_profile,
    jstring filament_profile,
    jstring process_profile,
    jdoubleArray previous_placement,
    jdoubleArray placement,
    jboolean auto_drop,
    jlong manipulation,
    jdoubleArray face_normal,
    jdouble arrange_distance,
    jboolean arrange_enable_rotation,
    jboolean arrange_allow_multi_materials,
    jboolean arrange_align_to_y_axis
)
{
    std::array<double, 3> normal{};
    if (face_normal != nullptr && env->GetArrayLength(face_normal) == 3) {
        env->GetDoubleArrayRegion(face_normal, 0, 3, normal.data());
    }
    orcinus::orca::ArrangeSettings arrange;
    arrange.distance = arrange_distance;
    arrange.enable_rotation = arrange_enable_rotation == JNI_TRUE;
    arrange.allow_multi_materials_on_same_plate = arrange_allow_multi_materials == JNI_TRUE;
    arrange.align_to_y_axis = arrange_align_to_y_axis == JNI_TRUE;
    const orcinus::orca::ModelInspection inspection = orcinus::orca::place_model(
        to_utf8(env, model_path),
        to_profiles(env, printer_profile, filament_profile, process_profile),
        to_doubles(env, previous_placement),
        to_doubles(env, placement),
        auto_drop == JNI_TRUE,
        static_cast<orcinus::orca::Manipulation>(manipulation),
        normal,
        arrange
    );
    return to_java(env, inspection);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_describeFlatteningPlanes(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jstring printer_profile,
    jstring filament_profile,
    jstring process_profile,
    jdoubleArray placement
)
{
    const orcinus::orca::FlatteningPlanes result = orcinus::orca::describe_flattening_planes(
        to_utf8(env, model_path),
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
