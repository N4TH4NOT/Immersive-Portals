#version 330
in vec3 Position;
layout(std140) uniform PortalTransform {
    mat4 PortalMvp;
    vec4 PortalViewport;
    mat4 SourceProjectionInverse;
    mat4 ParentProjection;
    vec4 PortalFlags;
    vec4 PortalClipPlane;
};
out float portalClipDistance;
out float portalOriginalClipZ;
void main() {
    portalClipDistance = (int(PortalFlags.w + 0.5) & 4) != 0 ? dot(vec4(Position, 1.0), PortalClipPlane) : 1.0;
    gl_Position = PortalMvp * vec4(Position, 1.0);
    portalOriginalClipZ = gl_Position.z;
    if ((int(PortalFlags.w + 0.5) & 2) != 0) gl_Position.z = 0.0;
}
