package net.coderbot.iris.gl;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Lightweight OpenGL helper that mirrors the structure of the modern Iris render system
 * while sticking to the capabilities exposed through Minecraft 1.12.2's LWJGL 2 stack.
 */
public final class IrisRenderSystem {
    private static final Logger LOGGER = LogManager.getLogger(IrisRenderSystem.class);

    private static volatile boolean initialized;
    private static GlVersion glVersion = GlVersion.GL11;
    private static boolean supportsCompute;

    private IrisRenderSystem() {
    }

    public static void initRenderer() {
        assertOnRenderThread();

        if (initialized) {
            return;
        }

        ContextCapabilities caps = GLContext.getCapabilities();
        if (caps == null) {
            throw new IllegalStateException("OpenGL context is not current on the render thread");
        }

        glVersion = GlVersion.fromCapabilities(caps);

        // LWJGL 2 is tied to OpenGL <= 3.2 on Mojang's 1.12.2 distribution, so treat compute as unavailable.
        supportsCompute = false;

        String versionString = GL11.glGetString(GL11.GL_VERSION);
        LOGGER.info("Detected OpenGL {} ({}).", glVersion.name(), versionString);

        initialized = true;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            initRenderer();
        }
    }

    public static GlVersion getGlVersion() {
        ensureInitialized();
        return glVersion;
    }

    public static boolean supportsCompute() {
        ensureInitialized();
        return supportsCompute;
    }

    public static void assertOnRenderThread() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            throw new IllegalStateException("Minecraft client is not available");
        }

        if (!minecraft.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("OpenGL call attempted off of the Minecraft render thread");
        }
    }

    public static int createShader(int shaderType) {
        assertOnRenderThread();
        return GL20.glCreateShader(shaderType);
    }

    public static void shaderSource(int shader, CharSequence source) {
        assertOnRenderThread();
        GL20.glShaderSource(shader, source);
    }

    public static void compileShader(int shader) {
        assertOnRenderThread();
        GL20.glCompileShader(shader);
    }

    public static int getShaderParameter(int shader, int pname) {
        assertOnRenderThread();
        return GL20.glGetShaderi(shader, pname);
    }

    public static String getShaderInfoLog(int shader) {
        assertOnRenderThread();
        int maxLength = Math.max(1, GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH));
        return GL20.glGetShaderInfoLog(shader, maxLength);
    }

    public static void deleteShader(int shader) {
        assertOnRenderThread();
        GL20.glDeleteShader(shader);
    }

    public static int createProgram() {
        assertOnRenderThread();
        return GL20.glCreateProgram();
    }

    public static void attachShader(int program, int shader) {
        assertOnRenderThread();
        GL20.glAttachShader(program, shader);
    }

    public static void detachShader(int program, int shader) {
        assertOnRenderThread();
        GL20.glDetachShader(program, shader);
    }

    public static void linkProgram(int program) {
        assertOnRenderThread();
        GL20.glLinkProgram(program);
    }

    public static int getProgramParameter(int program, int pname) {
        assertOnRenderThread();
        return GL20.glGetProgrami(program, pname);
    }

    public static String getProgramInfoLog(int program) {
        assertOnRenderThread();
        int maxLength = Math.max(1, GL20.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH));
        return GL20.glGetProgramInfoLog(program, maxLength);
    }

    public static void useProgram(int program) {
        assertOnRenderThread();
        GL20.glUseProgram(program);
    }

    public static void unbindPrograms() {
        assertOnRenderThread();
        GL20.glUseProgram(0);
    }

    public static void deleteProgram(int program) {
        assertOnRenderThread();
        GL20.glDeleteProgram(program);
    }

    public static int getUniformLocation(int program, CharSequence name) {
        assertOnRenderThread();
        return GL20.glGetUniformLocation(program, name);
    }

    public static void generateMipmaps(int target) {
        assertOnRenderThread();
        GL30.glGenerateMipmap(target);
    }

    public static int createFramebuffer() {
        assertOnRenderThread();
        return EXTFramebufferObject.glGenFramebuffersEXT();
    }

    public static void bindFramebuffer(int target, int framebuffer) {
        assertOnRenderThread();
        EXTFramebufferObject.glBindFramebufferEXT(target, framebuffer);
    }

    public static void deleteFramebuffer(int framebuffer) {
        assertOnRenderThread();
        EXTFramebufferObject.glDeleteFramebuffersEXT(framebuffer);
    }

    public static void framebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level) {
        assertOnRenderThread();
        EXTFramebufferObject.glFramebufferTexture2DEXT(target, attachment, textureTarget, texture, level);
    }

    public static int checkFramebufferStatus(int target) {
        assertOnRenderThread();
        return EXTFramebufferObject.glCheckFramebufferStatusEXT(target);
    }

    public static void dispatchCompute(int x, int y, int z) {
        throw new UnsupportedOperationException("Compute shaders are not available on LWJGL 2 / Minecraft 1.12.2");
    }

    public static void memoryBarrier(int barriers) {
        throw new UnsupportedOperationException("Compute shaders are not available on LWJGL 2 / Minecraft 1.12.2");
    }
}
