
package mandelbrot;

import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;

import javax.tools.JavaCompiler;

public class JCaculation implements Runnable {
	double dx;
	double minx;
	
	double imaginary;
	double max;
	int y;
	int xTileNum;
	
	BufferedImage image;
	
	public JCaculation(double dx, double minX, int xTileNum, double i, int y, double maxIter, BufferedImage image){
		this.dx = dx;
		this.minx = minX;
		this.xTileNum = xTileNum;
		
		imaginary = i;
		this.y = y;
		max = maxIter;
		
		this.image = image;
	}

	
	@Override
	public void run() {
		double zx, zy, cX, cY, tmp;
		for (int x = 0; x < image.getWidth(); x++) {
			zx = zy = 0;
			cX = minx + dx * (x + xTileNum * image.getWidth());
			cY = imaginary;

			int iter;
			for(iter = 0; iter < max && zx * zx + zy * zy < 4; iter++){
				tmp = zx * zx - zy * zy + cX;
				zy = 2.0 * zx * zy + cY;
				zx = tmp;
			}

			int color = (int) (767 * (iter / max));
			//int color = x;

			if(color > 255 & color <= 511){
				color = (color & 0xFF) << 8 | 0xFF;
			}

			else if(color > 511 && color < 767){
				color = (color & 0xFF) << 16 | 0xFFFF;
			} else {
				color = 0;
			}
						
			image.setRGB(x, y, color);
		}
	}
}
