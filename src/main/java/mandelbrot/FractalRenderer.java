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

        int renderWidth = 65536, renderHeight = 65536, iterations = 100;
        double mMinY = -2, mMaxY = 2, mMinX = -2, mMaxX = 2, mDY, mDX;

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
            GpuInterface gpuInterface = new GpuInterface(renderWidth, renderHeight, mMinX, mMinY, mMaxX, mMaxY, mDX, mDY, iterations);

            CpuProfiler.startTask("Create fractal");
            gpuInterface.renderFractalAndSave();
            GL11.glFinish();
            CpuProfiler.endTask();
        }
        CpuProfiler.endTask();
    }
}
