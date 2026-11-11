#version 330
uniform sampler2D Coverage;
out vec4 fragColor;
void main() {
    ivec2 base = ivec2(gl_FragCoord.xy) * 8;
    ivec2 size = textureSize(Coverage, 0);
    float visible = 0.0;
    for (int y = 0; y < 8; ++y) for (int x = 0; x < 8; ++x) {
        ivec2 p = base + ivec2(x, y);
        if (all(lessThan(p, size))) visible = max(visible, texelFetch(Coverage, p, 0).r);
    }
    fragColor = vec4(visible, 0.0, 0.0, 1.0);
}
