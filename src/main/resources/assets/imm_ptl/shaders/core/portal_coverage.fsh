#version 330
#moj_import <imm_ptl:portal_depth.glsl>
layout(std140) uniform MaskTransform {
    mat4 MaskMvp;
    vec4 MaskFlags;
    vec4 MaskClipPlane;
};
out vec4 fragColor;
in float portalClipDistance;
in float portalOriginalClipZ;
void main() {
    if (portalClipDistance < -0.0001) discard;
    float depth = MaskFlags.x > 0.5
        ? portalClampedDepth(portalOriginalClipZ, gl_FragCoord.w, MaskFlags.y > 0.5)
        : gl_FragCoord.z;
    depth = portalSurfaceDepth(depth);
    gl_FragDepth = depth;
    fragColor = vec4(1.0, depth, 0.0, 1.0);
}
