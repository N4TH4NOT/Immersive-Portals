#ifndef IMM_PTL_PORTAL_DEPTH_GLSL
#define IMM_PTL_PORTAL_DEPTH_GLSL

float portalSurfaceDepth(float depth) {
    // Reverse Z: let the aperture win a coplanar block face despite raster rounding.
    // Float ULPs keep this relative to depth instead of exposing distant portals
    // through foreground geometry with a constant normalized-depth offset.
    if (depth <= 0.0) return depth;
    return min(uintBitsToFloat(floatBitsToUint(depth) + 16u), 1.0);
}

#endif
