#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    float opacity = texture(Sampler0, texCoord0).a;
    if (opacity < 0.1) discard;
    fragColor = vec4(ColorModulator.rgb, opacity * ColorModulator.a);
}
