package mandelbrot;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;
import javax.imageio.ImageIO;

public class Mandelbrot {
	private BufferedImage I;

	private double dx, dy, maxIters;

	private int width;
	private int height;
	private double minX;
	private double minY;

	public Mandelbrot(double minX, double maxX, double minY, double maxY,
			int width, int height, int maxIters) {

		this.I = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		this.dx = (maxX - minX) / width;
		this.dy = (maxY - minY) / height;

		this.minX = minX - this.dx;

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
		int[] tileSize = {4000, 4000}; //X, Y

		ExecutorService ex;

		BufferedImage image = new BufferedImage(tileSize[0], tileSize[1], BufferedImage.TYPE_INT_RGB);

		for (int xTileNum = 0; xTileNum * tileSize[0] < this.width; xTileNum++){
			
			for (int yTileNum = 0; yTileNum * tileSize[1] < this.height; yTileNum++){
				System.out.println("x = " + xTileNum + " y = " + yTileNum);
				ex = Executors.newFixedThreadPool(10);
				for (int y = 0; y < tileSize[1]; y++) {
					cY = minY + dy * (y + tileSize[1] * yTileNum);
					ex.submit(new JCalculation(dx, minX, xTileNum, cY, y, maxIters, image));
				}
				
				ex.shutdown();
				ex.awaitTermination(1, TimeUnit.HOURS);
				
				this.saveImage("png", new File("tile_" + xTileNum + "_" + yTileNum + ".png"), image);
			}
		}
	}

	public static void main(String[] args) {
		int width = 4000;
		int height = 4000;
		int maxIters = 5;

		double minX = -2;
		double maxX = 1;
		double minY = -2;
		double maxY = 2;

		// double minX = -.74363 ;
		// double maxX = -.74464;
		// double minY = .09350;
		// double maxY = .09451;

		Mandelbrot m = new Mandelbrot(minX, maxX, minY, maxY, width, height,
				maxIters);
		try {
			m.render();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.err.println("Couldn't create files");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("Done");
		//System.out.println(0xFFFFFF);
		System.out.println(m.dx);
		System.out.println(m.dy);
	}
}