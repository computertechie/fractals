
package mandelbrot;

import java.awt.image.BufferedImage;
import org.apache.commons.math3.complex.Complex;

public class MCalculation extends RowCalculation {
	
	public MCalculation(double dx, double minX, int xTileNum, double i, int y, double maxIter, BufferedImage image){
		super(dx, minX, xTileNum, i, y, maxIter, image);
	}

	@Override
	protected void fillPixel(int x, int y, Complex c) {
		// TODO Auto-generated method stub
		int iter;
		Complex z = new Complex(c.getReal(), c.getImaginary());
		
		for(iter = 0; iter < max && z.abs() < 2; iter++){
			z = z.pow(2).add(c);
		}
		
		int color = (int) (1280 * (iter / max));
		//int color = x;

//		if(color > 255 & color <= 511){
//			color = (color & 0xFF) << 8 | 0xFF;
//		}
//
//		else if(color > 511 && color < 767){
//			color = (color & 0xFF) << 16 | 0xFFFF;
//		} else if(color >= 767){
//			color = 0;
//		}
		
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
			color = 0;
		}

		image.setRGB(x, y, color);
	}
}
