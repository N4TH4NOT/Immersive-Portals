#ifndef IMM_PTL_PORTAL_DEPTH_GLSL
#define IMM_PTL_PORTAL_DEPTH_GLSL

float portalClampedDepth(float clipZ, float reciprocalW, bool zeroToOne) {
    // Smooth clip Z equals the original NDC depth divided by gl_FragCoord.w.
    // Recover that depth after XY/W clipping, then emulate depth-range clamping.
    float ndcDepth = clipZ * reciprocalW;
    return clamp(zeroToOne ? ndcDepth : ndcDepth * 0.5 + 0.5, 0.0, 1.0);
}

float portalSurfaceDepth(float depth) {
    // Reverse Z: let the aperture win a coplanar block face despite raster rounding.
    // Float ULPs keep this relative to depth instead of exposing distant portals
    // through foreground geometry with a constant normalized-depth offset.
    if (depth <= 0.0) return depth;
    return min(uintBitsToFloat(floatBitsToUint(depth) + 16u), 1.0);
}

#endif
