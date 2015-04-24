package mandelbrot;

import ar.com.hjg.pngj.*;
import ar.com.hjg.pngj.chunks.ChunkLoadBehaviour;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;
import org.lwjgl.opengl.DisplayMode;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * Created by Pepper on 3/24/2015.
 */
public class GpuInterface {
    public static final int TILE_SIZE = 4096;

    private int width = 1280, height = 720;
    private int csId, vsId, fsId, csProgramId, renderProgramId, quadVAO;
    private int workgroupSize_x, workgroupSize_y, numTilesX, numTilesY;
    private int complexComponentTexture, iterationsTexture;
    private int rWidth, rHeight, maxIterations;
    private double minX, minY, dX, dY, tileViewingSizeX, tileViewingSizeY;

    private long startTime = System.currentTimeMillis();

    private File[][] tileFiles;

    public GpuInterface(int renderWidth, int renderHeight, double minX, double minY, double maxX, double maxY, double dX, double dY, int iterations){
        rWidth = renderWidth;
        rHeight = renderHeight;
        this.minX = minX;
        this.minY = minY;
        this.dX = dX;
        this.dY = dY;
        maxIterations = iterations;


        numTilesY = renderWidth/TILE_SIZE;
        tileViewingSizeY = (maxY - minY) / numTilesY;

        numTilesX = renderHeight/TILE_SIZE;
        tileViewingSizeX = (maxX - minX) / numTilesX;

        if(renderWidth % TILE_SIZE > 0)
            numTilesY++;
        if(renderHeight % TILE_SIZE > 0)
            numTilesX++;

        tileFiles = new File[numTilesX][numTilesY];

        createDisplay();
        initialiseShaders();
        quadVAO = quadFullScreenVao();

        IntBuffer workgroupSize = BufferUtils.createIntBuffer(3);
        GL20.glGetProgram(csProgramId, GL43.GL_COMPUTE_WORK_GROUP_SIZE, workgroupSize);
        workgroupSize_x = workgroupSize.get(0);
        workgroupSize_y = workgroupSize.get(1);
    }

    public void renderFractalAndSave(){
        CpuProfiler.startTask("Render tiles and save");
        for(int row = 0; row < numTilesX; row++){
            for(int column = 0; column < numTilesY; column++){
                CpuProfiler.startTask(String.format("Render and save tile %d %d", row, column));
                renderTileAndSave(row, column);
                CpuProfiler.endTask();
            }
        }

        CpuProfiler.startTask("Create composite image");
        saveComposite();
        CpuProfiler.endTask();
        CpuProfiler.endTask();
    }

    private void renderTileAndSave(int row, int column) {
        CpuProfiler.startTask("Create textures");
        createTextures();
        CpuProfiler.endTask();

        CpuProfiler.startTask("Iterate");
        iterate(row, column);
//        iterateAndRender(row, column);
        CpuProfiler.endTask();


        CpuProfiler.startTask("Save tile image");
        File tileFile = new File(String.format("./last/%d/%d_%d_%d_%d.png", startTime, rHeight, maxIterations, row, column));
        tileFile.getParentFile().mkdirs();
        if(!tileFile.exists()) {
            try {
                tileFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        tileFiles[row][column] = tileFile;
        try {
            saveRender(tileFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        CpuProfiler.endTask();

        CpuProfiler.startTask("Delete textures");
        deleteTextures();
        CpuProfiler.endTask();
    }

    private void saveRender(File file) throws FileNotFoundException {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, iterationsTexture);
        int byteCount = TILE_SIZE * TILE_SIZE;
        IntBuffer bytes = BufferUtils.createIntBuffer(byteCount);
        CpuProfiler.startTask("VRAM->RAM");
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL30.GL_RED_INTEGER, GL11.GL_INT, bytes);
        CpuProfiler.endTask();

        OutputStream output = new FileOutputStream(file);
        PngWriter writer = new PngWriter(output, new ImageInfo(TILE_SIZE, TILE_SIZE, 8, false));
        ImageLineInt imageLine;

        for (int x = 0; x < TILE_SIZE; x++) {
            imageLine = new ImageLineInt(writer.imgInfo);
            for (int y = 0; y < TILE_SIZE; y++) {
                int i = (x + (TILE_SIZE * y));
                int color = (int) (1280 * ((float)bytes.get(i) / (float)maxIterations));

                if(color < 256){
                } else if(color >= 256 && color < 512){
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

                ImageLineHelper.setPixelRGB8(imageLine, y, color);
            }
            writer.writeRow(imageLine);
        }
        writer.end();
    }

    private void saveComposite(){
        PngReader[] rowTileReaders = new PngReader[numTilesX];
        PngReader tileReader = new PngReader(tileFiles[0][0]);

        ImageInfo tileInfo, compositeImageInfo;
        tileInfo = tileReader.imgInfo;
        compositeImageInfo = new ImageInfo(tileInfo.cols * numTilesX, tileInfo.rows * numTilesY, tileInfo.bitDepth, false);

        PngWriter compositeWriter = new PngWriter(new File(String.format("last/%d/%d_%d_full.png", startTime, rHeight, maxIterations)), compositeImageInfo);

        tileReader.end();

        ImageLineInt compositeLine = new ImageLineInt(compositeImageInfo);
        int compositeRow = 0;
        for(int yTile = 0; yTile < numTilesY; yTile++){
            Arrays.fill(compositeLine.getScanline(), 0);

            for(int xTile = 0; xTile < numTilesX; xTile++){
                rowTileReaders[xTile] = new PngReader(tileFiles[yTile][xTile]);
                rowTileReaders[xTile].setChunkLoadBehaviour(ChunkLoadBehaviour.LOAD_CHUNK_NEVER);
            }

            for(int tileRow = 0; tileRow < tileInfo.rows; tileRow++, compositeRow++){
                for(int xTile = 0; xTile < numTilesX; xTile++){
                    ImageLineInt tileLine = (ImageLineInt) rowTileReaders[xTile].readRow(tileRow);
                    System.arraycopy(tileLine.getScanline(), 0, compositeLine.getScanline(), tileLine.getScanline().length * xTile, tileLine.getScanline().length);
                }
                compositeWriter.writeRow(compositeLine);
            }
        }

        for(PngReader reader : rowTileReaders)
            reader.end();
    }

    private void iterateAndRender(int row, int column){
        for(int i = 0; i < maxIterations; i++) {
            GL20.glUseProgram(csProgramId);
            GL42.glBindImageTexture(0, complexComponentTexture, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RG32F);
            GL42.glBindImageTexture(1, iterationsTexture, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16I);
            GL20.glUniform1i(2, i);
            GL20.glUniform2i(5, row, column);
            int error = GL11.glGetError();
            if (error != 0) {
                System.err.println("Error: " + error);
            }
            GL43.glDispatchCompute(TILE_SIZE / workgroupSize_x, TILE_SIZE / workgroupSize_y, 1);
            GL42.glBindImageTexture(0, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RG32F);
            GL42.glBindImageTexture(1, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16I);
            error = GL11.glGetError();
            if (error != 0) {
                System.err.println("Error2: " + error);
            }

            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

            render(i);
        }
    }

    private void iterate(int row, int column){
        GL20.glUseProgram(csProgramId);
        for(int iteration = 0; iteration < maxIterations; iteration++) {
            GL42.glBindImageTexture(0, complexComponentTexture, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RG32F);
            GL42.glBindImageTexture(1, iterationsTexture, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16I);
            GL20.glUniform1i(2, iteration);
            GL20.glUniform2i(5, row, column);
            int error = GL11.glGetError();
            if (error != 0) {
                System.err.println("Error: " + error);
            }
            GL43.glDispatchCompute(TILE_SIZE / workgroupSize_x, TILE_SIZE / workgroupSize_y, 1);
            GL42.glBindImageTexture(0, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RG32F);
            GL42.glBindImageTexture(1, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16I);
            error = GL11.glGetError();
            if (error != 0) {
                System.err.println("Error2: " + error);
            }

            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        }
        GL20.glUseProgram(0);
    }

    public void deleteTextures() {
        deleteComplexTextures();
        deleteIterationTexture();
    }

    private void deleteComplexTextures(){
        GL11.glDeleteTextures(complexComponentTexture);
    }

    private void deleteIterationTexture() {
        GL11.glDeleteTextures(iterationsTexture);
    }

    public void render(int iters){
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL20.glUseProgram(renderProgramId);

        GL20.glUniform1i(1, iters);
        GL20.glUniform2i(2, width / 2, height / 2);

        GL30.glBindVertexArray(quadVAO);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, iterationsTexture);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);

        Display.update();
        Display.sync(20);
    }

    public void quickRender(int iters){
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL20.glUseProgram(renderProgramId);

        GL20.glUniform1i(1, iters);
        GL20.glUniform2i(2, width/2, height/2);

        GL30.glBindVertexArray(quadVAO);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, iterationsTexture);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);

        Display.update();
    }

    private void createDisplay(){
        try {
            Display.setDisplayMode(new DisplayMode(width, height));
            Display.create(new PixelFormat(), new ContextAttribs(4,3).withProfileCore(true).withForwardCompatible(true).withDebug(true));
            Display.setResizable(true);
            GL11.glViewport(0, 0, width, height);
            GL11.glClearColor(1, 1, 1, 0);
            ARBDebugOutput.glDebugMessageCallbackARB(new ARBDebugOutputCallback());
            Keyboard.create();
        } catch (LWJGLException e) {
            e.printStackTrace();
        }
        GL11.glViewport(0, 0, width, height);
    }

    private void initialiseShaders(){
        csId = loadShader(this.getClass().getResource("/assets/julia_compute.glsl"), GL43.GL_COMPUTE_SHADER);
        vsId = loadShader(this.getClass().getResource("/assets/quad_vs.glsl"), GL20.GL_VERTEX_SHADER);
        fsId = loadShader(this.getClass().getResource("/assets/quad_fs.glsl"), GL20.GL_FRAGMENT_SHADER);

        csProgramId = GL20.glCreateProgram();
        GL20.glAttachShader(csProgramId, csId);
        GL20.glLinkProgram(csProgramId);

        if (GL20.glGetProgrami(csProgramId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.out.println(GL20.glGetProgramInfoLog(csProgramId, 1024));
            throw new RuntimeException("Link failed");
        }
        GL20.glUseProgram(csProgramId);
        GL20.glUniform2f(3, (float) dX, (float)dY);
        GL20.glUniform2f(4, (float) minX, (float) minY);
        GL20.glUniform1i(6, TILE_SIZE);
        GL20.glUseProgram(0);

        renderProgramId = GL20.glCreateProgram();
        GL20.glAttachShader(renderProgramId, vsId);
        GL20.glAttachShader(renderProgramId, fsId);
        GL20.glBindAttribLocation(renderProgramId, 0, "vertex");
        GL30.glBindFragDataLocation(renderProgramId, 0, "color");
        GL20.glLinkProgram(renderProgramId);

        if (GL20.glGetProgrami(renderProgramId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.out.println(GL20.glGetProgramInfoLog(renderProgramId, 1024));
            throw new RuntimeException("Link failed");
        }
    }

    private static int loadShader(URL filename, int type) {
        StringBuilder shaderSource = new StringBuilder();
        int shaderID = -1;
        try
        {
            BufferedReader reader;
            reader = new BufferedReader(new InputStreamReader(filename.openStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                shaderSource.append(line).append("\n");
            }
            reader.close();
        } catch (IOException e) {
            System.err.println("Could not read file.");
            e.printStackTrace();
            System.exit(-1);
        }

        shaderID = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderID, shaderSource);
        GL20.glCompileShader(shaderID);

        int error = GL11.glGetError();

        if(error != 0){
            System.err.println("some gl error " + error);
        }

        if (GL20.glGetShaderi(shaderID, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE)
            System.err.println(GL20.glGetShaderInfoLog(shaderID, 1024));
        if (GL11.glGetError() != 0) {
            System.err.println("shader error");
        }
        return shaderID;
    }

    private void createTextures(){
        complexComponentTexture = GL11.glGenTextures();
        iterationsTexture = GL11.glGenTextures();

        ByteBuffer black = null;

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, complexComponentTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0,  GL30.GL_RG32F, TILE_SIZE, TILE_SIZE, 0, GL11.GL_RGBA, GL11.GL_FLOAT, black);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        int error = GL11.glGetError();
        if(error!=0){
            System.err.println("Error: " + error );
        }

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, iterationsTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0,  GL30.GL_R16I, TILE_SIZE, TILE_SIZE, 0, GL30.GL_RED_INTEGER, GL11.GL_INT, black);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        error = GL11.glGetError();
        if(error!=0){
            System.err.println("Erro2r: " + error );
        }
    }

    private int quadFullScreenVao() {
        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        IntBuffer bb = BufferUtils.createIntBuffer(4 * 6);
        bb.put(-1).put(-1);
        bb.put(1).put(-1);
        bb.put(1).put(1);
        bb.put(1).put(1);
        bb.put(-1).put(1);
        bb.put(-1).put(-1);
        bb.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, bb, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_INT, false, 0, 0L);
        GL30.glBindVertexArray(0);
        return vao;
    }
}
