#include "mesh_tree.hpp"

#include <algorithm>
#include <cfloat>
#include <limits>
#include <utility>

namespace orcinus::scene {

namespace {

const float* corner_of(const float* corners, std::size_t triangle, int corner)
{
    return corners + (triangle * 3 + corner) * floats_per_corner;
}

// Builds the subtree of order[begin, end) depth first, a node's first child
// right after it, and returns its index.
class TreeBuilder {
public:
    TreeBuilder(const float* corners, const std::vector<float>& centroids, TreeNode* nodes, std::uint32_t* order)
        : m_corners(corners), m_centroids(centroids), m_nodes(nodes), m_order(order)
    {
    }

    std::uint32_t build(std::uint32_t begin, std::uint32_t end)
    {
        const std::uint32_t index = m_next++;
        const std::uint32_t count = end - begin;
        if (count <= leaf_size) {
            TreeNode& leaf = m_nodes[index];
            std::fill(std::begin(leaf.min), std::end(leaf.min), FLT_MAX);
            std::fill(std::begin(leaf.max), std::end(leaf.max), -FLT_MAX);
            for (std::uint32_t entry = begin; entry < end; ++entry) {
                for (int corner = 0; corner < 3; ++corner) {
                    const float* point = corner_of(m_corners, m_order[entry], corner);
                    for (int axis = 0; axis < 3; ++axis) {
                        leaf.min[axis] = std::min(leaf.min[axis], point[axis]);
                        leaf.max[axis] = std::max(leaf.max[axis], point[axis]);
                    }
                }
            }
            leaf.first = begin;
            leaf.count = count | leaf_flag;
            return index;
        }
        // The median along the axis the centroids spread most along.
        float low[3] = {FLT_MAX, FLT_MAX, FLT_MAX};
        float high[3] = {-FLT_MAX, -FLT_MAX, -FLT_MAX};
        for (std::uint32_t entry = begin; entry < end; ++entry) {
            const float* centroid = &m_centroids[std::size_t(m_order[entry]) * 3];
            for (int axis = 0; axis < 3; ++axis) {
                low[axis] = std::min(low[axis], centroid[axis]);
                high[axis] = std::max(high[axis], centroid[axis]);
            }
        }
        int axis = 0;
        for (int other = 1; other < 3; ++other) {
            if (high[other] - low[other] > high[axis] - low[axis]) {
                axis = other;
            }
        }
        const std::uint32_t middle = begin + count / 2;
        std::nth_element(m_order + begin, m_order + middle, m_order + end, [this, axis](std::uint32_t a, std::uint32_t b) {
            return m_centroids[std::size_t(a) * 3 + axis] < m_centroids[std::size_t(b) * 3 + axis];
        });
        const std::uint32_t first_child = build(begin, middle);
        const std::uint32_t second_child = build(middle, end);
        TreeNode& node = m_nodes[index];
        for (int axis = 0; axis < 3; ++axis) {
            node.min[axis] = std::min(m_nodes[first_child].min[axis], m_nodes[second_child].min[axis]);
            node.max[axis] = std::max(m_nodes[first_child].max[axis], m_nodes[second_child].max[axis]);
        }
        node.first = second_child;
        node.count = 0;
        return index;
    }

private:
    const float* m_corners;
    const std::vector<float>& m_centroids;
    TreeNode* m_nodes;
    std::uint32_t* m_order;
    std::uint32_t m_next = 0;
};

// Where the ray is inside the node's box, from enter to leave along it, as
// far as [limit]; false when it misses the box. The box grows by a little,
// so that no rounding misses a triangle on its face.
bool ray_box(const TreeNode& node, const Ray& ray, double limit, double& enter)
{
    double low = 0.0;
    double high = limit;
    for (int axis = 0; axis < 3; ++axis) {
        const double pad = (double(node.max[axis]) - node.min[axis]) * 1e-6 + 1e-6;
        const double min = node.min[axis] - pad;
        const double max = node.max[axis] + pad;
        const double origin = ray.origin[axis];
        const double direction = ray.direction[axis];
        if (direction == 0.0) {
            if (origin < min || origin > max) {
                return false;
            }
            continue;
        }
        double near = (min - origin) / direction;
        double far = (max - origin) / direction;
        if (near > far) {
            std::swap(near, far);
        }
        low = std::max(low, near);
        high = std::min(high, far);
        if (low > high) {
            return false;
        }
    }
    enter = low;
    return true;
}

// Visits the leaves the ray passes through ahead of its origin, nearer
// boxes first, skipping boxes beyond what [limit] returns.
template<typename Leaf, typename Limit>
void traverse(const TreeNode* nodes, const Ray& ray, Leaf&& leaf, Limit&& limit)
{
    std::uint32_t stack[64];
    int size = 0;
    double enter = 0.0;
    if (!ray_box(nodes[0], ray, limit(), enter)) {
        return;
    }
    stack[size++] = 0;
    while (size > 0) {
        const TreeNode& node = nodes[stack[--size]];
        if ((node.count & leaf_flag) != 0) {
            leaf(node.first, node.count & ~leaf_flag);
            continue;
        }
        const std::uint32_t first = std::uint32_t(&node - nodes) + 1;
        const std::uint32_t second = node.first;
        double first_enter = 0.0;
        double second_enter = 0.0;
        const bool first_hit = ray_box(nodes[first], ray, limit(), first_enter);
        const bool second_hit = ray_box(nodes[second], ray, limit(), second_enter);
        // The nearer child is taken first, so it goes on the stack last.
        if (first_hit && second_hit) {
            const bool first_nearer = first_enter <= second_enter;
            stack[size++] = first_nearer ? second : first;
            stack[size++] = first_nearer ? first : second;
        } else if (first_hit) {
            stack[size++] = first;
        } else if (second_hit) {
            stack[size++] = second;
        }
    }
}

std::size_t node_count(std::size_t triangles)
{
    if (triangles <= leaf_size) {
        return 1;
    }
    return 1 + node_count(triangles / 2) + node_count(triangles - triangles / 2);
}

} // namespace

std::size_t tree_node_count(std::size_t triangles)
{
    return triangles == 0 ? 0 : node_count(triangles);
}

void build_tree(const float* corners, std::size_t triangles, TreeNode* nodes, std::uint32_t* order)
{
    if (triangles == 0) {
        return;
    }
    std::vector<float> centroids(triangles * 3);
    for (std::size_t triangle = 0; triangle < triangles; ++triangle) {
        order[triangle] = std::uint32_t(triangle);
        for (int axis = 0; axis < 3; ++axis) {
            centroids[triangle * 3 + axis] =
                (corner_of(corners, triangle, 0)[axis] + corner_of(corners, triangle, 1)[axis] + corner_of(corners, triangle, 2)[axis]) / 3.f;
        }
    }
    TreeBuilder(corners, centroids, nodes, order).build(0, std::uint32_t(triangles));
}

bool ray_triangle(const Ray& ray, const float* v0, const float* v1, const float* v2, double& t)
{
    const double e1[3] = {double(v1[0]) - v0[0], double(v1[1]) - v0[1], double(v1[2]) - v0[2]};
    const double e2[3] = {double(v2[0]) - v0[0], double(v2[1]) - v0[1], double(v2[2]) - v0[2]};
    const double* d = ray.direction;
    const double p[3] = {d[1] * e2[2] - d[2] * e2[1], d[2] * e2[0] - d[0] * e2[2], d[0] * e2[1] - d[1] * e2[0]};
    const double determinant = e1[0] * p[0] + e1[1] * p[1] + e1[2] * p[2];
    if (determinant > -1e-12 && determinant < 1e-12) {
        return false;
    }
    const double inverse = 1.0 / determinant;
    const double t0[3] = {ray.origin[0] - v0[0], ray.origin[1] - v0[1], ray.origin[2] - v0[2]};
    const double u = (t0[0] * p[0] + t0[1] * p[1] + t0[2] * p[2]) * inverse;
    if (u < 0.0 || u > 1.0) {
        return false;
    }
    const double q[3] = {t0[1] * e1[2] - t0[2] * e1[1], t0[2] * e1[0] - t0[0] * e1[2], t0[0] * e1[1] - t0[1] * e1[0]};
    const double v = (d[0] * q[0] + d[1] * q[1] + d[2] * q[2]) * inverse;
    if (v < 0.0 || u + v > 1.0) {
        return false;
    }
    t = (e2[0] * q[0] + e2[1] * q[1] + e2[2] * q[2]) * inverse;
    return t > 0.0;
}

bool first_hit(const float* corners, const TreeNode* nodes, const std::uint32_t* order, std::size_t triangles, const Ray& ray, double& t, std::uint32_t& triangle)
{
    if (triangles == 0) {
        return false;
    }
    double nearest = std::numeric_limits<double>::infinity();
    std::uint32_t nearest_triangle = 0;
    traverse(
        nodes, ray,
        [&](std::uint32_t first, std::uint32_t count) {
            for (std::uint32_t entry = first; entry < first + count; ++entry) {
                const std::uint32_t candidate = order[entry];
                double hit = 0.0;
                if (ray_triangle(ray, corner_of(corners, candidate, 0), corner_of(corners, candidate, 1), corner_of(corners, candidate, 2), hit) &&
                    (hit < nearest || (hit == nearest && candidate < nearest_triangle))) {
                    nearest = hit;
                    nearest_triangle = candidate;
                }
            }
        },
        [&] { return nearest; });
    if (nearest == std::numeric_limits<double>::infinity()) {
        return false;
    }
    t = nearest;
    triangle = nearest_triangle;
    return true;
}

void all_hits(const float* corners, const TreeNode* nodes, const std::uint32_t* order, std::size_t triangles, const Ray& ray, std::vector<double>& hits)
{
    if (triangles == 0) {
        return;
    }
    traverse(
        nodes, ray,
        [&](std::uint32_t first, std::uint32_t count) {
            for (std::uint32_t entry = first; entry < first + count; ++entry) {
                const std::uint32_t candidate = order[entry];
                double hit = 0.0;
                if (ray_triangle(ray, corner_of(corners, candidate, 0), corner_of(corners, candidate, 1), corner_of(corners, candidate, 2), hit)) {
                    hits.push_back(hit);
                }
            }
        },
        [] { return std::numeric_limits<double>::infinity(); });
}

} // namespace orcinus::scene
