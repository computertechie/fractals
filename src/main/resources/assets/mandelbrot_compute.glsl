#version 430 core

//layout(binding = 0, rgba8ui) uniform uimage2D resultTexture;
layout(binding = 0, rg16f) uniform image2D complexComponentTexture;
layout(binding = 1, r32i) uniform iimage2D iterationsTexture;
layout(location = 2) uniform int lastMaxIterations;
layout(location = 3) uniform vec2 dx_dy;
layout(location = 4) uniform vec2 minX_minY;

const vec2 constantComplex = vec2(.285, .01);

layout(local_size_x = 16, local_size_y = 16, local_size_z = 1) in;

vec2 c_mult(vec2 a, vec2 b) {
  return vec2(a.x * b.x - a.y * b.y,
              (a.x+a.y)*(b.x+b.y) - a.x*b.x - a.y*b.y);
}

vec2 mandel(vec2 prev) {
  if(length(prev) < 4.0) {
      prev = c_mult(prev, prev) + constantComplex;

      ivec2 pix = ivec2(gl_GlobalInvocationID.xy);
      imageAtomicAdd(iterationsTexture, pix, 1);
  }

  return prev;
}

void main(void){
    ivec2 pix = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(iterationsTexture);

    if (pix.x >= size.x || pix.y >= size.y) {
        return;
    }

    if(imageLoad(iterationsTexture, pix).x < lastMaxIterations){
        return;
    }

    vec2 complex_coord;
    if(lastMaxIterations == 0){
        complex_coord = vec2(minX_minY.x + dx_dy.x * gl_GlobalInvocationID.x, minX_minY.y + dx_dy.y * gl_GlobalInvocationID.y);
    }
    else{
        complex_coord = imageLoad(complexComponentTexture, pix).xy;
    }

    vec2 complex = mandel(complex_coord);
    vec4 complexPixel = vec4(complex, 0, 1);
    imageStore(complexComponentTexture, pix, complexPixel);
}