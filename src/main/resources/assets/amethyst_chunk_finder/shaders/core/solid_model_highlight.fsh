#version 330
#extension GL_ARB_separate_shader_objects : require

// Match the vanilla position_tex_color vertex shader's dynamic transform block.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;

void main() {
    // Retain cage holes, crystal cutouts and equipment masks; discard original texture RGB.
    float mask = texture(Sampler0, texCoord0).a;
    vec4 solid = vertexColor * ColorModulator;
    solid.a *= mask;
    if (solid.a <= 0.0) {
        discard;
    }
    fragColor = solid;
}
