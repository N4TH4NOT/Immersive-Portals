#version 330
in vec3 Position;
layout(std140) uniform MaskTransform {
    mat4 MaskMvp;
    vec4 MaskFlags;
    vec4 MaskClipPlane;
};
out float portalClipDistance;
out float portalOriginalClipZ;
void main() {
    portalClipDistance = MaskFlags.z > 0.5 ? dot(vec4(Position, 1.0), MaskClipPlane) : 1.0;
    gl_Position = MaskMvp * vec4(Position, 1.0);
    portalOriginalClipZ = gl_Position.z;
    // Neutral clip Z retains the whole aperture when an edge crosses behind the eye.
    // Its actual clamped surface depth is reconstructed in the fragment shader.
    if (MaskFlags.x > 0.5) gl_Position.z = 0.0;
}
