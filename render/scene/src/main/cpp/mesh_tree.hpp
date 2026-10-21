// Where a ray meets a large mesh of the 3D view, which a tap and the gizmos
// ask, found in native code.
//
// OrcaSlicer finds them with an AABB tree over the triangles
// (MeshRaycaster on AABBMesh). Its tree keeps one triangle a leaf in a
// balanced tree of power-of-two size, half a gigabyte for a model of four
// million triangles; this one keeps up to eight triangles a leaf, a tenth of
// that, and finds the same hits.
//
// The meshes are MeshData's corners: x, y, z and the normal of every corner,
// three corners a triangle. The tree lives in memory the caller gives it.

#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace orcinus::scene {

constexpr std::size_t floats_per_corner = 6;

// A node of the tree, the first child of an inner node right after it.
struct TreeNode {
    float min[3];
    float max[3];
    // A leaf: its first triangle in the order and, with leaf_flag, how many.
    // An inner node: the index of its second child, and 0.
    std::uint32_t first;
    std::uint32_t count;
};
static_assert(sizeof(TreeNode) == 32, "Kotlin allocates 32 bytes a node");

constexpr std::uint32_t leaf_flag = 0x80000000u;
constexpr std::uint32_t leaf_size = 8;

// How many nodes the tree of a mesh of [triangles] has.
std::size_t tree_node_count(std::size_t triangles);

// Builds the tree into [nodes] (tree_node_count() of them) and [order] (a
// triangle index each); the leaves' triangles are runs of [order].
void build_tree(const float* corners, std::size_t triangles, TreeNode* nodes, std::uint32_t* order);

struct Ray {
    double origin[3];
    double direction[3];
};

// SceneData.kt's rayTriangle(): Moller-Trumbore, the parameter along the
// direction where the ray enters the triangle, if it is ahead.
bool ray_triangle(const Ray& ray, const float* v0, const float* v1, const float* v2, double& t);

// The nearest triangle the ray enters ahead of its origin, the first of the
// triangles at the same distance; false for none.
bool first_hit(const float* corners, const TreeNode* nodes, const std::uint32_t* order, std::size_t triangles, const Ray& ray, double& t, std::uint32_t& triangle);

// The parameters of every triangle the ray enters ahead of its origin, in no order.
void all_hits(const float* corners, const TreeNode* nodes, const std::uint32_t* order, std::size_t triangles, const Ray& ray, std::vector<double>& hits);

} // namespace orcinus::scene
