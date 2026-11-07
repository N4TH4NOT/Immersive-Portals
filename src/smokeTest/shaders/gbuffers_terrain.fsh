#version 120
uniform sampler2D texture;
uniform sampler2D lightmap;
varying vec2 uv;
varying vec2 lightUv;
varying vec4 vertexColor;
void main() {
    gl_FragData[0] = texture2D(texture, uv) * vertexColor * texture2D(lightmap, lightUv);
}
