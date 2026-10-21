#include "convex_hull.hpp"

#include <libqhullcpp/Qhull.h>
#include <libqhullcpp/QhullPoint.h>
#include <libqhullcpp/QhullVertex.h>
#include <libqhullcpp/QhullVertexSet.h>

namespace orcinus::scene {

std::vector<float> convex_hull_points(const float* points, std::size_t count)
{
    std::vector<float> hull;
    if (count == 0) {
        return hull;
    }
    orgQhull::Qhull qhull;
    // we want qhull to be quiet
    qhull.disableOutputStream();
    // qhull's coordinates are doubles (REALfloat 0).
    const std::vector<realT> source(points, points + count * 3);
    try {
        qhull.runQhull("", 3, static_cast<int>(count), source.data(), "Qt");
    } catch (...) {
        return hull;
    }
    for (const orgQhull::QhullVertex& vertex : qhull.vertexList()) {
        const realT* coordinates = vertex.point().coordinates();
        hull.insert(hull.end(), {static_cast<float>(coordinates[0]), static_cast<float>(coordinates[1]), static_cast<float>(coordinates[2])});
    }
    return hull;
}

} // namespace orcinus::scene
