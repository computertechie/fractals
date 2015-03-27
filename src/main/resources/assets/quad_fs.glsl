#version 430 core

/* This comes interpolated from the vertex shader */
in vec2 texcoord;

/* The fragment color */
out vec4 color;

/* The texture we are going to sample */
layout(binding = 0) uniform isampler2D tex;
layout(location = 1) uniform int maxIterations;

void main(void) {
    /* Well, simply sample the texture */
    ivec2 tc = ivec2(gl_FragCoord.xy);
    ivec3 iterations = texelFetch(tex, tc,0).xyz;

    int colorInt = int(1280 * (float(iterations.x)/float(maxIterations)));

    if(colorInt < 256){}
    else if(colorInt >= 256 && colorInt < 512){
        colorInt = (colorInt & 0xFF) << 8 | 0xFF;
    }
    else if(colorInt >= 512 && colorInt < 768){
        colorInt = (0xFF - (colorInt & 0xFF)) | 0xFF00;
    }
    else if(colorInt >= 768 && colorInt < 1024){
        colorInt = (colorInt & 0xFF) << 16 | 0xFF00;
    }
    else if(colorInt >= 1024 && colorInt < 1280) {
        colorInt = (0xFF - (colorInt & 0xFF)) << 8 | 0xFF0000;
    }
    else {
        colorInt = 0;
    }

    float red = float((colorInt >> 16)/255.0);
    float green = float(((colorInt >> 8) & 0xFF)/255.0);
    float blue = float((colorInt & 0xFF)/255.0);
    color = vec4(red, green, blue, 1);
}
