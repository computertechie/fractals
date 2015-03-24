package mandelbrot;

import java.awt.image.BufferedImage;

import org.apache.commons.math3.complex.Complex;

public abstract class RowCalculation implements Runnable {
	double dx;
	double minx;

	double imaginary;
	double max;
	int y;
	int xTileNum;

	BufferedImage image;

	public RowCalculation(double dx, double minX, int xTileNum, double i, int y, double maxIter, BufferedImage image){
		this.dx = dx;
		this.minx = minX;
		this.xTileNum = xTileNum;

		imaginary = i;
		this.y = y;
		max = maxIter;

		this.image = image;
	}

	@Override
	public final void run() {
		double cX, cY;
		
		for (int x = 0; x < image.getWidth(); x++) {
			cX = minx + dx * (x + xTileNum * image.getWidth());
			cY = imaginary;

			Complex c = new Complex(cX, cY);
			
			this.fillPixel(x, y, c);
		}
    }

	abstract protected void fillPixel(int x, int y, Complex c);
}
