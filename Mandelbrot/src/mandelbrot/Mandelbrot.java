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

public class Mandelbrot{
	private BufferedImage I;

	private double dx, dy, maxIters;

	private int width;
	private int height;
	private double minX;
	private double minY;

	public Mandelbrot(
			double minX, double maxX,
			double minY, double maxY,
			int width, int height, int maxIters){

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

	public void saveImage(String fileType, File f) throws IOException{
		ImageIO.write(I, fileType, f);
	}

	public void render(){
		double zx, zy, cX, cY, tmp;

		ExecutorService ex = Executors.newFixedThreadPool(10);
		
		for (int y = 0; y < height; y++) {
			cY = minY + dy * y;
			ex.submit(new MCaculation(dx, minX, cY, y, maxIters, I));
		}
		ex.shutdown();
		try {
			ex.awaitTermination(10, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			try {
				this.saveImage("png", new File("mandel_incomplete.png"));
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			e.printStackTrace();
		}
	}

	public static void main(String[] args){
		int width = 1000;
		int height = 1000;
		int maxIters = 100;

		double minX = -2;
		double maxX = 1;
		double minY = -1.5;
		double maxY = 1.5;

//		double minX = -.74363 ;
//		double maxX = -.74464;
//		double minY = .09350;
//		double maxY = .09451;
		
		Mandelbrot m = new Mandelbrot(minX, maxX, minY, maxY, width, height, maxIters);
		m.render();

		try {
			m.saveImage("png", new File("mandel3.png"));
		} catch (IOException e) {
			System.out.println("Error: Couldn't save file \r\n");
		}

		System.out.println("Done");
		System.out.println(0xFFFFFF);
		System.out.println(m.dx);
		System.out.println(m.dy);
	}
}