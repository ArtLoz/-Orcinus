// JNI bridge of :render:scene: mesh_tree.hpp over MeshData's corners, and
// convex_hull.hpp over a mesh's positions. The corners, the nodes and the
// order are direct buffers Kotlin allocates, so the garbage collector frees
// them with the mesh.

#include "convex_hull.hpp"
#include "mesh_tree.hpp"

#include <jni.h>

#include <cstdint>
#include <vector>

namespace {

using orcinus::scene::Ray;
using orcinus::scene::TreeNode;

template<typename T>
T* address_of(JNIEnv* env, jobject buffer)
{
    return static_cast<T*>(env->GetDirectBufferAddress(buffer));
}

// Origin then direction.
Ray to_ray(JNIEnv* env, jdoubleArray values)
{
    Ray ray{};
    env->GetDoubleArrayRegion(values, 0, 3, ray.origin);
    env->GetDoubleArrayRegion(values, 3, 3, ray.direction);
    return ray;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_app_orcinus_shadow_render_scene_NativeMeshes_nodeCount(JNIEnv*, jobject, jint triangles)
{
    return static_cast<jint>(orcinus::scene::tree_node_count(static_cast<std::size_t>(triangles)));
}

extern "C" JNIEXPORT void JNICALL
Java_app_orcinus_shadow_render_scene_NativeMeshes_build(JNIEnv* env, jobject, jobject corners, jint triangles, jobject nodes, jobject order)
{
    orcinus::scene::build_tree(
        address_of<const float>(env, corners), static_cast<std::size_t>(triangles), address_of<TreeNode>(env, nodes), address_of<std::uint32_t>(env, order));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_orcinus_shadow_render_scene_NativeMeshes_firstHit(
    JNIEnv* env, jobject, jobject corners, jint triangles, jobject nodes, jobject order, jdoubleArray ray, jdoubleArray hit)
{
    double t = 0.0;
    std::uint32_t triangle = 0;
    if (!orcinus::scene::first_hit(
            address_of<const float>(env, corners), address_of<const TreeNode>(env, nodes), address_of<const std::uint32_t>(env, order),
            static_cast<std::size_t>(triangles), to_ray(env, ray), t, triangle)) {
        return JNI_FALSE;
    }
    const double values[2] = {t, static_cast<double>(triangle)};
    env->SetDoubleArrayRegion(hit, 0, 2, values);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_app_orcinus_shadow_render_scene_NativeMeshes_allHits(
    JNIEnv* env, jobject, jobject corners, jint triangles, jobject nodes, jobject order, jdoubleArray ray)
{
    std::vector<double> hits;
    orcinus::scene::all_hits(
        address_of<const float>(env, corners), address_of<const TreeNode>(env, nodes), address_of<const std::uint32_t>(env, order),
        static_cast<std::size_t>(triangles), to_ray(env, ray), hits);
    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(hits.size()));
    if (result != nullptr) {
        env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(hits.size()), hits.data());
    }
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_app_orcinus_shadow_render_scene_NativeMeshes_convexHull(JNIEnv* env, jobject, jfloatArray positions)
{
    std::vector<float> points(static_cast<std::size_t>(env->GetArrayLength(positions)));
    env->GetFloatArrayRegion(positions, 0, static_cast<jsize>(points.size()), points.data());
    const std::vector<float> hull = orcinus::scene::convex_hull_points(points.data(), points.size() / 3);
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(hull.size()));
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(hull.size()), hull.data());
    }
    return result;
}
