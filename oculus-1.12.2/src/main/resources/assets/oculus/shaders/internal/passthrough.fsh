#version 120

uniform sampler2D u_ColorTexture;
varying vec2 v_TexCoord;

void main() {
    gl_FragColor = texture2D(u_ColorTexture, v_TexCoord);
}
