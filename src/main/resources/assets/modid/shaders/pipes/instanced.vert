#version 330 compatibility

layout(location = 0) in vec3 position;
layout(location = 1) in float face;
layout(location = 2) in vec2 uv;
layout(location = 3) in vec3 blockPosition;
layout(location = 4) in float brightness;
layout(location = 5) in vec2 lightmapCoordinates;

uniform mat4 transform;
uniform vec4 fogPlane;
uniform sampler2D lightmap;
uniform float faceShade[6];

smooth out vec2 UV;
flat out vec3 LightColor;
smooth out float FogDistance;

void main()
{
    vec4 vertex = vec4(blockPosition + position / 16.0, 1.0);
    gl_Position = transform * vertex;
    UV = uv;
    LightColor = textureLod(lightmap, (lightmapCoordinates + 8.0) / 256.0, 0.0).rgb * faceShade[int(face)] * brightness;
    FogDistance = abs(dot(fogPlane, vertex));
}
