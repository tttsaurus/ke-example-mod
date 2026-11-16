#version 400 compatibility

uniform usamplerBuffer blocks;
uniform int quadEnds[7];
uniform int blockOffsets[7];
uniform uvec4 topologyFaces[128];
uniform vec2 faceUV[24];
uniform float faceShade[6];
uniform sampler2D lightmap;
uniform mat4 transform;
uniform vec4 fogPlane;

smooth out vec2 UV;
flat out vec3 LightColor;
smooth out float FogDistance;

const int quadsPerBatch = 64;
const ivec3 faceCorners[24] = ivec3[](
        ivec3(0, 0, 0), ivec3(1, 0, 0), ivec3(1, 0, 1), ivec3(0, 0, 1),
        ivec3(0, 1, 1), ivec3(1, 1, 1), ivec3(1, 1, 0), ivec3(0, 1, 0),
        ivec3(1, 0, 0), ivec3(0, 0, 0), ivec3(0, 1, 0), ivec3(1, 1, 0),
        ivec3(0, 0, 1), ivec3(1, 0, 1), ivec3(1, 1, 1), ivec3(0, 1, 1),
        ivec3(0, 0, 0), ivec3(0, 0, 1), ivec3(0, 1, 1), ivec3(0, 1, 0),
        ivec3(1, 0, 1), ivec3(1, 0, 0), ivec3(1, 1, 0), ivec3(1, 1, 1));

void main()
{
    int globalQuad = gl_InstanceID * quadsPerBatch + gl_VertexID / 4;
    if (globalQuad >= quadEnds[6])
    {
        UV = vec2(0.0);
        LightColor = vec3(0.0);
        FogDistance = 0.0;
        gl_Position = vec4(0.0, 0.0, 2.0, 1.0);
        return;
    }

    int connections = 0;
    int end = 7;
    while (connections < end)
    {
        int middle = (connections + end) / 2;
        if (globalQuad < quadEnds[middle])
        {
            end = middle;
        }
        else
        {
            connections = middle + 1;
        }
    }

    int quadsPerBlock = 6 + 3 * connections;
    int groupQuad = globalQuad - (connections == 0 ? 0 : quadEnds[connections - 1]);
    uvec2 block = texelFetch(blocks, blockOffsets[connections] + groupQuad / quadsPerBlock).rg;
    int topology = int(block.x >> 24u);
    int quad = groupQuad % quadsPerBlock;
    uint packedFaces = topologyFaces[topology * 2 + quad / 16][quad / 4 % 4];
    uint descriptor = (packedFaces >> uint(quad % 4 * 8)) & 255u;
    int face = int(descriptor & 7u);
    int direction = int(descriptor >> 3u) - 1;
    ivec3 minimum = ivec3(5);
    ivec3 maximum = ivec3(11);
    if (direction >= 0)
    {
        int axis = direction < 2 ? 1 : direction < 4 ? 2 : 0;
        if ((direction & 1) == 0)
        {
            maximum[axis] = minimum[axis];
            minimum[axis] = 0;
        }
        else
        {
            minimum[axis] = maximum[axis];
            maximum[axis] = 16;
        }
    }

    ivec3 corner = faceCorners[face * 4 + gl_VertexID % 4];
    vec3 position = vec3(minimum + corner * (maximum - minimum)) / 16.0;
    vec3 blockPosition = vec3(block.x & 255u, (block.x >> 8u) & 255u, (block.x >> 16u) & 255u);
    vec4 vertex = vec4(blockPosition + position, 1.0);
    vec2 uvPosition = face < 2 ? position.xz : face < 4 ? position.xy : position.zy;
    int uvOffset = face * 4;
    UV = mix(
            mix(faceUV[uvOffset], faceUV[uvOffset + 1], uvPosition.x),
            mix(faceUV[uvOffset + 2], faceUV[uvOffset + 3], uvPosition.x),
            uvPosition.y);
    vec2 lightmapCoordinates = vec2(block.y & 255u, (block.y >> 8u) & 255u);
    float brightness = float((block.y >> 16u) & 255u) / 255.0;
    LightColor = textureLod(lightmap, (lightmapCoordinates + 8.0) / 256.0, 0.0).rgb * faceShade[face] * brightness;
    FogDistance = abs(dot(fogPlane, vertex));
    gl_Position = transform * vertex;
}
