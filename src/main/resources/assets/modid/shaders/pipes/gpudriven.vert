#version 400 compatibility

uniform usamplerBuffer blocks;
uniform int vertexEnds[64];
uniform int blockOffsets[64];
uniform vec2 faceUV[24];
uniform float faceShade[6];
uniform sampler2D lightmap;
uniform mat4 transform;
uniform vec4 fogPlane;

smooth out vec2 UV;
flat out vec3 LightColor;
smooth out float FogDistance;

const int quadIndices[6] = int[](0, 1, 2, 0, 2, 3);
const ivec3 faceCorners[24] = ivec3[](
        ivec3(0, 0, 0), ivec3(1, 0, 0), ivec3(1, 0, 1), ivec3(0, 0, 1),
        ivec3(0, 1, 1), ivec3(1, 1, 1), ivec3(1, 1, 0), ivec3(0, 1, 0),
        ivec3(1, 0, 0), ivec3(0, 0, 0), ivec3(0, 1, 0), ivec3(1, 1, 0),
        ivec3(0, 0, 1), ivec3(1, 0, 1), ivec3(1, 1, 1), ivec3(0, 1, 1),
        ivec3(0, 0, 0), ivec3(0, 0, 1), ivec3(0, 1, 1), ivec3(0, 1, 0),
        ivec3(1, 0, 1), ivec3(1, 0, 0), ivec3(1, 1, 0), ivec3(1, 1, 1));

int connectionAt(uint mask, int index)
{
    for (int i = 0; i < index; i++)
    {
        mask &= mask - 1u;
    }
    return findLSB(mask);
}

void main()
{
    int first = 0;
    int end = 64;
    while (first < end)
    {
        int middle = (first + end) / 2;
        if (gl_VertexID < vertexEnds[middle])
        {
            end = middle;
        }
        else
        {
            first = middle + 1;
        }
    }

    int verticesPerBlock = (6 + 3 * bitCount(uint(first))) * 6;
    int groupVertex = gl_VertexID - (first == 0 ? 0 : vertexEnds[first - 1]);
    uvec2 block = texelFetch(blocks, blockOffsets[first] + groupVertex / verticesPerBlock).rg;
    uint topology = block.x >> 24u;
    int localVertex = groupVertex % verticesPerBlock;
    int quad = localVertex / 6;
    int centerFaces = 6 - bitCount(topology);
    int face;
    ivec3 minimum = ivec3(5);
    ivec3 maximum = ivec3(11);
    if (quad < centerFaces)
    {
        face = connectionAt(~topology & 63u, quad);
    }
    else
    {
        int armQuad = quad - centerFaces;
        int direction = connectionAt(topology, armQuad / 4);
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
        face = armQuad % 4;
        if (face >= (direction & ~1))
        {
            face += 2;
        }
    }

    ivec3 corner = faceCorners[face * 4 + quadIndices[localVertex % 6]];
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
