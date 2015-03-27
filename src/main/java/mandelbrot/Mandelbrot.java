package mandelbrot;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

public class Mandelbrot {
	private BufferedImage I;

	private double dx, dy, maxIters;

	private int width;
	private int height;
	private double minX;
	private double minY;

	public Mandelbrot(double minX, double maxX, double minY, double maxY, int width, int height, int maxIters) {


		this.dx = (maxX - minX) / width;
		this.dy = (maxY - minY) / height;
		System.out.println("dx: "+dx+" dy: "+dy);

//		this.minX = minX - this.dx;

		this.width = width;
		this.height = height;

		this.minX = minX;

		this.minY = minY;
		this.maxIters = maxIters;
	}

	public void saveImage(String fileType, File f, BufferedImage I) throws IOException {
		ImageIO.write(I, fileType, f);
	}

	public void render() throws IOException, InterruptedException {
		double cY;
		ExecutorService ex;

		int[] tileSize = {5000, 5000}; //X, Y
		if(this.height < tileSize[1]){
			tileSize[1] = this.height;
		}
		if(this.width < tileSize[0]){
			tileSize[0] = this.width;
		}
		
		BufferedImage image = new BufferedImage(tileSize[0], tileSize[1], BufferedImage.TYPE_INT_RGB);
		
		for (int xTileNum = 0; xTileNum * tileSize[0] < this.width; xTileNum++){
			CpuProfiler.startTask("xTile: "+xTileNum);
			for (int yTileNum = 0; yTileNum * tileSize[1] < this.height; yTileNum++){
			    CpuProfiler.startTask("yTile: "+yTileNum);
				ex = Executors.newFixedThreadPool(10);
				for (int y = 0; y < tileSize[1]; y++) {
					cY = minY + dy * (y + tileSize[1] * yTileNum);
					ex.submit(new JCalculation(dx, minX, xTileNum, cY, y, maxIters, image));
				}
				
				ex.shutdown();
				ex.awaitTermination(1, TimeUnit.HOURS);
				
				File f =  new File("./last/1tile_" + xTileNum + "_" + yTileNum + ".png");
				f.mkdirs();
				
				this.saveImage("png", f, image);
                CpuProfiler.endTask();;
			}
            CpuProfiler.endTask();
		}
	}

	public static void main(String[] args) {
        CpuProfiler.startTask("Main");
        System.setProperty("org.lwjgl.librarypath", "E:\\Documents\\Projects\\fractals\\build\\natives\\windows");

		int renderWidth = 1024, renderHeight = 1024, iterations = 10000;
		double mMinY = -1, mMaxY = 1, mMinX = -1, mMaxX = 1, mDY, mDX;

		mDX = (mMaxX - mMinX) / renderWidth;
		mDY = (mMaxY - mMinY) / renderHeight;


        GpuInterface gpuInterface = new GpuInterface(renderWidth, renderHeight, mMinX, mMinY, mDX, mDY, iterations);

        File f;
        CpuProfiler.startTask("iterate");
        for(int i = 0; i<iterations; i++) {
			System.out.println(i);
			gpuInterface.iterate(i);
//			if(i%10==0) {
				gpuInterface.render();
//			}
        }
		GL11.glFinish();
		CpuProfiler.endTask();

		f = new File("./last/tile_"+renderHeight+"_"+renderWidth+"_"+iterations+".png");
		f.mkdirs();
		gpuInterface.saveRender(f);

        while(!Display.isCloseRequested()){
            gpuInterface.render();
        }
        CpuProfiler.endTask();
	}
}