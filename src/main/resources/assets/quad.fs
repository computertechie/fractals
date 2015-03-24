#version 410 core

/* This comes interpolated from the vertex shader */
in vec2 texcoord;

/* The fragment color */
out vec4 color;

/* The texture we are going to sample */
uniform sampler2D tex;

void main(void) {
  /* Well, simply sample the texture */
  ivec2 tc = ivec2(gl_FragCoord.xy);
  vec3 tempcolor = texelFetch(tex, tc,0).xyz;
  color = vec4(tempcolor, 1);
}
