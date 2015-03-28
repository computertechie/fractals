package mandelbrot;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.IOException;

/**
 * Created by Pepper on 3/27/2015.
 */
public class FractalRenderer {
    public static void main(String[] args) {
        boolean mandel = false;
        CpuProfiler.startTask("Main");
        System.setProperty("org.lwjgl.librarypath", "E:\\Documents\\Projects\\fractals\\build\\natives\\windows");

        int renderWidth = 8192, renderHeight = 8192, iterations = 1000;
        double mMinY = -1, mMaxY = 1, mMinX = -1, mMaxX = 1, mDY, mDX;

        mDX = (mMaxX - mMinX) / renderWidth;
        mDY = (mMaxY - mMinY) / renderHeight;

        if(mandel) {
            CpuProfiler.startTask("Mandelbrot");
            Mandelbrot mandelbrot = new Mandelbrot(mMinX, mMaxX, mMinY, mMaxY, renderWidth, renderHeight, iterations);
            try {
                mandelbrot.render();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            CpuProfiler.endTask();
        }

        else {
            GpuInterface gpuInterface = new GpuInterface(renderWidth, renderHeight, mMinX, mMinY, mDX, mDY, iterations);

            CpuProfiler.startTask("iterate");
            for(int i = 0; i<iterations; i++) {
                gpuInterface.iterate(i);
                gpuInterface.quickRender(i);
                Display.setTitle("Iteration: " + i);
            }
            GL11.glFinish();
            CpuProfiler.endTask();

            CpuProfiler.startTask("Saving.");
            File f;
            f = new File("./last/tile_"+renderHeight+"_"+renderWidth+"_"+iterations+".png");
            f.mkdirs();
            gpuInterface.saveRender(f);
            CpuProfiler.endTask();

            gpuInterface.deleteComplexTextures();
            while(!Display.isCloseRequested()){
                gpuInterface.render(iterations);
            }
        }
        CpuProfiler.endTask();
    }
}
