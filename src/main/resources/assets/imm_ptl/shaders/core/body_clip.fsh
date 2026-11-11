#version 330
uniform sampler2D BodyColor;
uniform sampler2D BodyDepth;
layout(std140) uniform BodyClipping {
    mat4 InverseViewProjection;
    vec4 BodyPlane;
    vec4 ViewPlane;
    vec4 BodyViewport;
};
out vec4 fragColor;
void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 color = texelFetch(BodyColor, pixel, 0);
    if (color.a <= 0.0) discard;
    float depth = texelFetch(BodyDepth, pixel, 0).r;
    if (depth <= 0.0) discard;
    vec2 uv = gl_FragCoord.xy / BodyViewport.xy;
    float ndcDepth = BodyViewport.z > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 position = InverseViewProjection * vec4(uv * 2.0 - 1.0, ndcDepth, 1.0);
    position /= position.w;
    // Both planes are expressed relative to the rendered camera. In particular,
    // the body's plane remains active inside another portal's clipped world view.
    if (dot(BodyPlane, position) < -0.00001 || dot(ViewPlane, position) < -0.00001) discard;
    gl_FragDepth = depth;
    fragColor = color;
}
