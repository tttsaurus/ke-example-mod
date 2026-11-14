#version 330 compatibility

uniform sampler2D atlas;
uniform int fogMode;
uniform float waterFogDensity;

smooth in vec2 UV;
flat in vec3 LightColor;
smooth in float FogDistance;

layout(location = 0) out vec4 Color;

void main()
{
    vec3 color = texture(atlas, UV).rgb * LightColor;

    if (fogMode == 2)
    {
        color *= vec3(0.85, 0.95, 1.0);
        vec3 fogColor = max(gl_Fog.color.rgb, vec3(0.06, 0.16, 0.22));
        float visibility = exp(-waterFogDensity * FogDistance);
        Color = vec4(mix(fogColor, color, visibility), 1.0);
        return;
    }

    float fog = fogMode == 0
            ? (gl_Fog.end - FogDistance) / max(gl_Fog.end - gl_Fog.start, 0.0001)
            : exp(-gl_Fog.density * FogDistance);

    Color = vec4(mix(gl_Fog.color.rgb, color, clamp(fog, 0.0, 1.0)), 1.0);
}
