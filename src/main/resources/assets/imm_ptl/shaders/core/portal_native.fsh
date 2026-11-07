#version 330
#moj_import <imm_ptl:portal_depth.glsl>
uniform sampler2D PortalColor;
uniform sampler2D PortalDepth;
uniform sampler2D PortalMask;
layout(std140) uniform PortalTransform {
    mat4 PortalMvp;
    vec4 PortalViewport;
    mat4 SourceProjectionInverse;
    mat4 ParentProjection;
    vec4 PortalFlags;
    vec4 PortalClipPlane;
};
in float portalClipDistance;
in float portalOriginalClipZ;
out vec4 fragColor;
void main() {
    if (portalClipDistance < -0.0001) discard;
    vec2 uv = (gl_FragCoord.xy - PortalViewport.xy) / PortalViewport.zw;
    int flags = int(PortalFlags.w + 0.5);
    gl_FragDepth = (flags & 2) != 0
        ? portalClampedDepth(portalOriginalClipZ, gl_FragCoord.w, PortalFlags.y > 0.5)
        : gl_FragCoord.z;
    if ((flags & 1) != 0) {
        vec2 mask = texelFetch(PortalMask, ivec2(gl_FragCoord.xy), 0).rg;
        if (mask.r < 0.5) discard;
        // Coverage already applied the surface offset; do not bias a second time.
        gl_FragDepth = mask.g;
    } else if ((flags & 8) != 0) gl_FragDepth = portalSurfaceDepth(gl_FragDepth);
    if (PortalFlags.x > 0.5) {
        float depth = texture(PortalDepth, uv).r;
        // Reverse Z: the cleared depth represents an empty remote world.
        if (depth <= 0.0) discard;
        float ndcDepth = PortalFlags.y > 0.5 ? depth : depth * 2.0 - 1.0;
        vec4 view = SourceProjectionInverse * vec4(uv * 2.0 - 1.0, ndcDepth, 1.0);
        if (PortalFlags.z > 0.5) view.x = -view.x;
        vec4 parent = ParentProjection * view;
        float parentDepth = parent.z / parent.w;
        gl_FragDepth = PortalFlags.y > 0.5 ? parentDepth : parentDepth * 0.5 + 0.5;
    }
    fragColor = texture(PortalColor, uv);
}
