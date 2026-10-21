// The tree of mesh_tree.hpp finds the hits trying every triangle finds, as
// SceneData.kt does for small meshes: run on the device by
// scripts/engine-test.ps1 -Suite orcinus_mesh_tree_tests.

#include "mesh_tree.hpp"

#include <catch2/catch_test_macros.hpp>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <random>
#include <vector>

using namespace orcinus::scene;

namespace {

// Corners as MeshData lays them out, the normal left at zero.
struct Mesh {
    std::vector<float> corners;
    std::size_t triangles() const { return corners.size() / (3 * floats_per_corner); }

    void add(const float (&a)[3], const float (&b)[3], const float (&c)[3])
    {
        for (const float* point : {a, b, c}) {
            corners.insert(corners.end(), {point[0], point[1], point[2], 0.f, 0.f, 0.f});
        }
    }
};

struct Tree {
    std::vector<TreeNode> nodes;
    std::vector<std::uint32_t> order;
};

Tree build(const Mesh& mesh)
{
    Tree tree;
    tree.nodes.resize(tree_node_count(mesh.triangles()));
    tree.order.resize(mesh.triangles());
    build_tree(mesh.corners.data(), mesh.triangles(), tree.nodes.data(), tree.order.data());
    return tree;
}

const float* corner(const Mesh& mesh, std::size_t triangle, int index)
{
    return mesh.corners.data() + (triangle * 3 + index) * floats_per_corner;
}

// SceneObject's loop over every triangle.
bool every_first_hit(const Mesh& mesh, const Ray& ray, double& t, std::uint32_t& triangle)
{
    double nearest = std::numeric_limits<double>::max();
    bool found = false;
    for (std::size_t candidate = 0; candidate < mesh.triangles(); ++candidate) {
        double hit = 0.0;
        if (ray_triangle(ray, corner(mesh, candidate, 0), corner(mesh, candidate, 1), corner(mesh, candidate, 2), hit) && hit < nearest) {
            nearest = hit;
            triangle = std::uint32_t(candidate);
            found = true;
        }
    }
    t = nearest;
    return found;
}

std::vector<double> every_hit(const Mesh& mesh, const Ray& ray)
{
    std::vector<double> hits;
    for (std::size_t candidate = 0; candidate < mesh.triangles(); ++candidate) {
        double hit = 0.0;
        if (ray_triangle(ray, corner(mesh, candidate, 0), corner(mesh, candidate, 1), corner(mesh, candidate, 2), hit)) {
            hits.push_back(hit);
        }
    }
    std::sort(hits.begin(), hits.end());
    return hits;
}

// A closed sphere of [rings] by [segments] quads, two triangles each.
Mesh sphere(int rings, int segments, float radius)
{
    Mesh mesh;
    const double pi = std::acos(-1.0);
    auto point = [&](int ring, int segment, float (&out)[3]) {
        const double polar = pi * ring / rings;
        const double azimuth = 2 * pi * segment / segments;
        out[0] = float(radius * std::sin(polar) * std::cos(azimuth));
        out[1] = float(radius * std::sin(polar) * std::sin(azimuth));
        out[2] = float(radius * std::cos(polar));
    };
    for (int ring = 0; ring < rings; ++ring) {
        for (int segment = 0; segment < segments; ++segment) {
            float a[3], b[3], c[3], d[3];
            point(ring, segment, a);
            point(ring + 1, segment, b);
            point(ring + 1, segment + 1, c);
            point(ring, segment + 1, d);
            mesh.add(a, b, c);
            mesh.add(a, c, d);
        }
    }
    return mesh;
}

void check_rays(const Mesh& mesh, std::mt19937& random, int rays, float spread)
{
    const Tree tree = build(mesh);
    std::uniform_real_distribution<double> position(-spread, spread);
    int hit_count = 0;
    for (int index = 0; index < rays; ++index) {
        Ray ray{{position(random), position(random), position(random)}, {position(random), position(random), position(random)}};
        // Some rays along the axes, which meet the boxes' faces edge on.
        if (index % 4 == 0) {
            ray.direction[0] = 0.0;
            ray.direction[1] = 0.0;
            ray.direction[2] = index % 8 == 0 ? 1.0 : -1.0;
        }
        double expected_t = 0.0;
        std::uint32_t expected_triangle = 0;
        const bool expected = every_first_hit(mesh, ray, expected_t, expected_triangle);
        double t = 0.0;
        std::uint32_t triangle = 0;
        const bool found = first_hit(mesh.corners.data(), tree.nodes.data(), tree.order.data(), mesh.triangles(), ray, t, triangle);
        REQUIRE(found == expected);
        if (found) {
            ++hit_count;
            CHECK(t == expected_t);
            CHECK(triangle == expected_triangle);
        }
        std::vector<double> hits;
        all_hits(mesh.corners.data(), tree.nodes.data(), tree.order.data(), mesh.triangles(), ray, hits);
        std::sort(hits.begin(), hits.end());
        CHECK(hits == every_hit(mesh, ray));
    }
    // The rays are worth something only if many meet the mesh.
    CHECK(hit_count > rays / 10);
}

} // namespace

TEST_CASE("The tree finds the triangles of a closed mesh every triangle finds", "[MeshTree]")
{
    std::mt19937 random(1);
    check_rays(sphere(60, 80, 20.f), random, 3000, 30.f);
}

TEST_CASE("The tree finds the triangles of a soup of triangles every triangle finds", "[MeshTree]")
{
    std::mt19937 random(2);
    std::uniform_real_distribution<float> position(-10.f, 10.f);
    std::uniform_real_distribution<float> size(-1.5f, 1.5f);
    Mesh mesh;
    for (int index = 0; index < 20000; ++index) {
        const float a[3] = {position(random), position(random), position(random)};
        const float b[3] = {a[0] + size(random), a[1] + size(random), a[2] + size(random)};
        const float c[3] = {a[0] + size(random), a[1] + size(random), a[2] + size(random)};
        mesh.add(a, b, c);
    }
    check_rays(mesh, random, 3000, 12.f);
}

TEST_CASE("Of triangles as near, the tree finds the first, as trying every triangle does", "[MeshTree]")
{
    // The same square many times over, above the origin.
    Mesh mesh;
    for (int copy = 0; copy < 100; ++copy) {
        mesh.add({-1.f, -1.f, 5.f}, {1.f, -1.f, 5.f}, {1.f, 1.f, 5.f});
        mesh.add({-1.f, -1.f, 5.f}, {1.f, 1.f, 5.f}, {-1.f, 1.f, 5.f});
    }
    const Tree tree = build(mesh);
    const Ray ray{{0.2, -0.3, 0.0}, {0.0, 0.0, 1.0}};
    double t = 0.0;
    std::uint32_t triangle = 0;
    REQUIRE(first_hit(mesh.corners.data(), tree.nodes.data(), tree.order.data(), mesh.triangles(), ray, t, triangle));
    CHECK(t == 5.0);
    CHECK(triangle == 0);
}

TEST_CASE("A tree has a node for a mesh of up to eight triangles, and none for an empty one", "[MeshTree]")
{
    CHECK(tree_node_count(0) == 0);
    CHECK(tree_node_count(1) == 1);
    CHECK(tree_node_count(leaf_size) == 1);
    CHECK(tree_node_count(leaf_size + 1) == 3);
    Mesh empty;
    const Ray ray{{0.0, 0.0, 0.0}, {0.0, 0.0, 1.0}};
    double t = 0.0;
    std::uint32_t triangle = 0;
    CHECK(!first_hit(empty.corners.data(), nullptr, nullptr, 0, ray, t, triangle));
}
