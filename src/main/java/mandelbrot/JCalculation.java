
package mandelbrot;

import java.awt.image.BufferedImage;

import org.apache.commons.math3.complex.Complex;

public class JCalculation extends RowCalculation {
	public JCalculation(double dx, double minX, int xTileNum, double i, int y, double maxIter, BufferedImage image){
		super(dx, minX, xTileNum, i, y, maxIter, image);
	}

	@Override
	protected void fillPixel(int x, int y, Complex c) {
		int iter;
		
		for(iter = 0; iter < this.max && c.abs() < 4; iter++ ){
			c = c.multiply(c).add(new Complex(.285, .01));
		}
		
	//	System.err.println(c.abs());
		
		int color = (int) (1280 * (iter / max));
		
		//int color = x;
		
		if(color < 256){		
		} else if(color >= 256 & color < 512){
			color = (color & 0xFF) << 8 | 0xFF;
		} else if(color >= 512 && color < 768){
			color = (0xFF - (color & 0xFF)) | 0xFF00;
		} else if(color >= 768 && color < 1024){
			color = (color & 0xFF) << 16 | 0xFF00;
		} else if(color >= 1024 && color < 1280) {
			color = (0xFF - (color & 0xFF)) << 8 | 0xFF0000;
		} else {
			//System.err.println(c.abs());
			color = 0;
		}

		image.setRGB(x, y, color);
	}
}
