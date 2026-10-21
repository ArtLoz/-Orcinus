// GLVolume::convex_hull() of a large mesh of the 3D view: the vertices of its
// convex hull, which OrcaSlicer measures the lowest point, the boxes, the
// bounding sphere and the outline on the screen of a volume with instead of
// every vertex.

#pragma once

#include <cstddef>
#include <vector>

namespace orcinus::scene {

// The vertices of the convex hull of [count] points (x, y, z each), x, y, z
// each, as its_convex_hull() of TriangleMesh.cpp finds them with qhull; empty
// when qhull finds no hull, as for points in a plane.
std::vector<float> convex_hull_points(const float* points, std::size_t count);

} // namespace orcinus::scene
