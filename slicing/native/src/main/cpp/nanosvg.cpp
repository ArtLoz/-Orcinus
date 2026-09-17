// OrcaSlicer compiles nanosvg in its GUI (BitmapCache.cpp), which this library
// does not build. The adapter rasterizes bed textures with it, so the
// implementation lives here, in a unit that includes nothing else first.
#define NANOSVG_IMPLEMENTATION
#include "nanosvg/nanosvg.h"
#define NANOSVGRAST_IMPLEMENTATION
#include "nanosvg/nanosvgrast.h"
