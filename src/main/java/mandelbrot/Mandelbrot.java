package mandelbrot;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

public class Mandelbrot {
    private static final int TILE_SIZE = 4096;
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

        int[] tileSize = {TILE_SIZE, TILE_SIZE}; //X, Y
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

                File f =  new File("./last/cpu_tile_" + xTileNum + "_" + yTileNum + ".png");
                f.mkdirs();

                this.saveImage("png", f, image);
                CpuProfiler.endTask();;
            }
            CpuProfiler.endTask();
        }
    }
}