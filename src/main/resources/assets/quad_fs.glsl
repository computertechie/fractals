#version 410 core

/* This comes interpolated from the vertex shader */
in vec2 texcoord;

/* The fragment color */
out vec4 color;

/* The texture we are going to sample */
layout(binding = 0) uniform isampler2D tex;
layout(binding = 1) uniform sampler2D complex;

void main(void) {
  /* Well, simply sample the texture */
  ivec2 tc = ivec2(gl_FragCoord.xy);
  ivec3 tempcolor = texelFetch(tex, tc,0).xyz;
  vec4 complex = texelFetch(complex, tc, 0);

  if(tempcolor.x < 40){
    color = vec4(1, 0, 0, 1);
  }
  else if(tempcolor.x < 80 && tempcolor.x > 40){
    color = vec4(0, 1, 0, 1);
  }
  else{
    color = vec4(0, 0, 1, 1);
  }
  //color = vec4(tempcolor.x/10, 0, 0,1);
  color = complex;//vec4(complex.xyz, 1);
}
