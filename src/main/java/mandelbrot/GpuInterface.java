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
    private int rWidth, rHeight;

    public GpuInterface(int renderWidth, int renderHeight){
        rWidth = renderWidth;
        rHeight = renderHeight;
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
        createTexture(debugTexture);

        IntBuffer workgroupSize = BufferUtils.createIntBuffer(3);
        GL20.glGetProgram(csProgramId, GL43.GL_COMPUTE_WORK_GROUP_SIZE, workgroupSize);
        workgroupSize_x = workgroupSize.get(0);
        workgroupSize_y = workgroupSize.get(1);
    }

    public void saveRender(File file) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, debugTexture);
        int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_COMPONENTS);
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        int channels = 0;
        int byteCount = 0;
        if(format == GL30.GL_RGBA32F)
            System.out.println("hrm");
        switch (format) {
            case GL11.GL_RGB:
                channels = 3;
                break;
            case GL11.GL_RGBA:
            case GL30.GL_RGBA32F:
            default:
                channels = 4;
                break;
        }
        byteCount = width * height * channels;
        ByteBuffer bytes = BufferUtils.createByteBuffer(byteCount);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
        final String ext = "PNG";
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int i = (x + (width * y)) * channels;
                int r = bytes.get(i) & 0xFF;
                int g = bytes.get(i + 1) & 0xFF;
                int b = bytes.get(i + 2) & 0xFF;
                image.setRGB(x, height - (y + 1), (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }
        try {
            ImageIO.write(image, ext, file);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void iterate(){
        GL20.glUseProgram(csProgramId);
//        for(int i = 0; i < tileTextures.limit(); i++){
//            GL42.glBindImageTexture(0, tileTextures.get(i), 0, false, 0, GL15.GL_WRITE_ONLY, GL30.GL_RGBA32F);
//        }

        GL42.glBindImageTexture(0, debugTexture, 0, false, 0, GL15.GL_WRITE_ONLY, GL30.GL_RGBA32F);
        int error = GL11.glGetError();
        if(error!=0){
            System.err.println("Error: " + error );
        }
        GL43.glDispatchCompute(rHeight/workgroupSize_x, rWidth/workgroupSize_y, 1);
        GL42.glBindImageTexture(0, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA32F);
        error = GL11.glGetError();
        if(error!=0){
            System.err.println("Error2: " + error );
        }

        GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        GL20.glUseProgram(0);
    }

    public void render(){
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL20.glUseProgram(renderProgramId);
        GL30.glBindVertexArray(quadVAO);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, debugTexture);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);

        Display.update();
        Display.sync(60);
    }

    private void createDisplay(){
        try {
            Display.setDisplayMode(new DisplayMode(width, height));
//            Display.create(new PixelFormat(), new ContextAttribs(3, 2).withProfileCore(true).withForwardCompatible(true));
            Display.create(new PixelFormat(), new ContextAttribs(4,3).withProfileCore(true).withForwardCompatible(true).withDebug(true));
            Display.setResizable(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glViewport(0, 0, width, height);
            GL11.glClearColor(0, 0, 0f, 0);
            ARBDebugOutput.glDebugMessageCallbackARB(new ARBDebugOutputCallback());
            Keyboard.create();
        } catch (LWJGLException e) {
            e.printStackTrace();
        }
        GL11.glViewport(0, 0, width, height);
    }

    private void initialiseShaders(){
        csId = loadShader(this.getClass().getResource("/assets/mandelbrot.compute"), GL43.GL_COMPUTE_SHADER);
        vsId = loadShader(this.getClass().getResource("/assets/quad.vs"), GL20.GL_VERTEX_SHADER);
        fsId = loadShader(this.getClass().getResource("/assets/quad.fs"), GL20.GL_FRAGMENT_SHADER);

        csProgramId = GL20.glCreateProgram();
        GL20.glAttachShader(csProgramId, csId);
        GL20.glLinkProgram(csProgramId);

        if (GL20.glGetProgrami(csProgramId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.out.println(GL20.glGetProgramInfoLog(csProgramId, 1024));
            throw new RuntimeException("Link failed");
        }

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

    private void createTexture(int texID){
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texID);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        ByteBuffer black = null;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, rWidth, rHeight, 0, GL11.GL_RGBA, GL11.GL_FLOAT, black);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private int quadFullScreenVao() {
        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        IntBuffer bb = BufferUtils.createIntBuffer(4 * 3);
        bb.put(-1).put(-1);
        bb.put(-1).put(3);
        bb.put(3).put(-1);
        bb.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, bb, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_INT, false, 0, 0L);
        GL30.glBindVertexArray(0);
        return vao;
    }
}
