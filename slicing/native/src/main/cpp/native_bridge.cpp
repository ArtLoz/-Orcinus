#include <jni.h>

#include <array>
#include <string>

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
    jobject progress_listener
)
{
    orcinus::orca::ProfileSelection profiles;
    profiles.printer = to_utf8(env, printer_profile);
    profiles.filament = to_utf8(env, filament_profile);
    profiles.process = to_utf8(env, process_profile);

    const ProgressForwarder forward_progress(env, progress_listener);
    const orcinus::orca::SliceResult result = orcinus::orca::slice(
        to_utf8(env, job_id),
        to_utf8(env, model_path),
        to_utf8(env, output_path),
        profiles,
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

extern "C" JNIEXPORT jlongArray JNICALL
Java_app_orcinus_shadow_slicing_nativebridge_NativeBindings_inspectStl(JNIEnv* env, jobject /* this */, jstring input_path)
{
    const orcinus::orca::StlInspection inspection = orcinus::orca::inspect_stl(to_utf8(env, input_path));
    const std::array<jlong, 9> values{
        static_cast<jlong>(inspection.status),
        static_cast<jlong>(inspection.facet_count),
        static_cast<jlong>(inspection.width_micrometers),
        static_cast<jlong>(inspection.depth_micrometers),
        static_cast<jlong>(inspection.height_micrometers),
        static_cast<jlong>(inspection.sampling_step_micrometers),
        static_cast<jlong>(inspection.sampled_plane_count),
        static_cast<jlong>(inspection.non_empty_plane_count),
        static_cast<jlong>(inspection.contour_count),
    };
    const auto size = static_cast<jsize>(values.size());
    const jlongArray result = env->NewLongArray(size);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, size, values.data());
    }
    return result;
}
