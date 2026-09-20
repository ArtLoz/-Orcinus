// RGB2HSV() of slic3r/Utils/ColorSpaceConvert.cpp, which libslic3r's
// FlushVolCalculator calls: the flushing volumes are worked out in HSV. The
// upstream file belongs to the desktop GUI and includes wxWidgets, so the one
// function the engine needs is carried over here unchanged.

#include <algorithm>
#include <cmath>

void RGB2HSV(float r, float g, float b, float* h, float* s, float* v)
{
    float Cmax = std::max(std::max(r, g), b);
    float Cmin = std::min(std::min(r, g), b);
    float delta = Cmax - Cmin;

    if (std::abs(delta) < 0.001) {
        *h = 0.f;
    }
    else if (Cmax == r) {
        *h = 60.f * fmod((g - b) / delta, 6.f);
    }
    else if (Cmax == g) {
        *h = 60.f * ((b - r) / delta + 2);
    }
    else {
        *h = 60.f * ((r - g) / delta + 4);
    }

    if (std::abs(Cmax) < 0.001) {
        *s = 0.f;
    }
    else {
        *s = delta / Cmax;
    }

    *v = Cmax;
}
