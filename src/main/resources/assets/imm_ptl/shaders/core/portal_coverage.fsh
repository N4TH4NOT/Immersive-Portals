#version 330
#moj_import <imm_ptl:portal_depth.glsl>
out vec4 fragColor;
in float portalClipDistance;
void main() {
    if (portalClipDistance < -0.0001) discard;
    float depth = portalSurfaceDepth(gl_FragCoord.z);
    gl_FragDepth = depth;
    fragColor = vec4(1.0, depth, 0.0, 1.0);
}
