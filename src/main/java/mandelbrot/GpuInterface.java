package mandelbrot;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Created by Pepper on 3/24/2015.
 */
public class GpuInterface {
    private int width = 1280, height = 720;
    private static final int TILE_SIZE = 16384;
    private IntBuffer tileTextures;
    private int csId, vsId, fsId, csProgramId, renderProgramId, quadVAO;
    private int currentTile = 0, workgroupSize_x, workgroupSize_y;
    private int debugTexture;

    private int complexComponentTexture, iterationsTexture;

    private int rWidth, rHeight, maxIterations;
    private double minX, minY, dX, dY;

    public GpuInterface(int renderWidth, int renderHeight, double minX, double minY, double dX, double dY, int iterations){
        rWidth = renderWidth;
        rHeight = renderHeight;
        this.minX = minX;
        this.minY = minY;
        this.dX = dX;
        this.dY = dY;
        maxIterations = iterations;

        int numWide = renderWidth/TILE_SIZE;
        int numHigh = renderHeight/TILE_SIZE;

        if(renderWidth % TILE_SIZE > 0)
            numWide++;
        if(renderHeight% TILE_SIZE > 0)
            numHigh++;

        tileTextures = BufferUtils.createIntBuffer(numHigh*numWide);

        createDisplay();
        initialiseShaders();
        quadVAO = quadFullScreenVao();

        GL11.glGenTextures(tileTextures);
        debugTexture = GL11.glGenTextures();
        createTextures();

        IntBuffer workgroupSize = BufferUtils.createIntBuffer(3);
        GL20.glGetProgram(csProgramId, GL43.GL_COMPUTE_WORK_GROUP_SIZE, workgroupSize);
        workgroupSize_x = workgroupSize.get(0);
        workgroupSize_y = workgroupSize.get(1);
    }

    public void saveRender(File file) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, iterationsTexture);
        int byteCount = rWidth * rHeight;
        IntBuffer bytes = BufferUtils.createIntBuffer(byteCount);
        BufferedImage image = new BufferedImage(rWidth, rHeight, BufferedImage.TYPE_INT_RGB);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL30.GL_RED_INTEGER, GL11.GL_INT, bytes);
        final String ext = "PNG";
        for (int x = 0; x < rWidth; x++) {
            for (int y = 0; y < rHeight; y++) {
                int i = (x + (rWidth * y));
                int color = (int) (1280 * ((float)bytes.get(i) / (float)maxIterations));
//                System.out.println(bytes.get(i) + " " + color);

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
//                int r = bytes.get(i) & 0xFF;
//                int g = (bytes.get(i) << 8) & 0xFF;
//                int b = (bytes.get(i) << 16) & 0xFF;
//                image.setRGB(x, height - (y + 1), (0xFF << 24) | (r << 16) | (g << 8) | b);
                image.setRGB(x, rHeight - (y + 1), color);
            }
        }
        try {
            ImageIO.write(image, ext, file);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void iterate(int maxIters){
        GL20.glUseProgram(csProgramId);
        GL42.glBindImageTexture(0, complexComponentTexture, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RG32F);
        GL42.glBindImageTexture(1, iterationsTexture, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R32I);
        GL20.glUniform1i(2, maxIters);
        int error = GL11.glGetError();
        if(error!=0){
            System.err.println("Error: " + error);
        }
        GL43.glDispatchCompute(rHeight/workgroupSize_x, rWidth/workgroupSize_y, 1);
        GL42.glBindImageTexture(0, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RG32F);
        GL42.glBindImageTexture(1, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R32I);
        error = GL11.glGetError();
        if(error!=0){
            System.err.println("Error2: " + error );
        }

        GL20.glUseProgram(0);
        GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    public void deleteComplexTextures(){
        GL11.glDeleteTextures(complexComponentTexture);
    }

    public void render(int iters){
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
        Display.sync(10);
    }

    private void createDisplay(){
        try {
            Display.setDisplayMode(new DisplayMode(width, height));
//            Display.create(new PixelFormat(), new ContextAttribs(3, 2).withProfileCore(true).withForwardCompatible(true));
            Display.create(new PixelFormat(), new ContextAttribs(4,3).withProfileCore(true).withForwardCompatible(true).withDebug(true));
            Display.setResizable(true);
//            GL11.glEnable(GL11.GL_DEPTH_TEST);
//            GL11.glDepthFunc(GL11.GL_LEQUAL);
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
        csId = loadShader(this.getClass().getResource("/assets/mandelbrot_compute.glsl"), GL43.GL_COMPUTE_SHADER);
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
        //vec2 dx_dy
        GL20.glUniform2f(3, (float) dX, (float)dY);
        //vec2 minX_minY
        GL20.glUniform2f(4, (float)minX, (float)minY);
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
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0,  GL30.GL_RG32F, rWidth, rHeight, 0, GL11.GL_RGBA, GL11.GL_FLOAT, black);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        int error = GL11.glGetError();
        if(error!=0){
            System.err.println("Error: " + error );
        }

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, iterationsTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0,  GL30.GL_R32I, rWidth, rHeight, 0, GL30.GL_RED_INTEGER, GL11.GL_INT, black);
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
