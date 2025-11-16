package net.oculus.pipeline.shadow;

import java.nio.ByteBuffer;

import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.program.TextureBindingRegistry;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.util.Config;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/**
 * Tracks the state of the primary shadow map framebuffer. The backport keeps a
 * simplified representation: a depth texture and placeholder color targets are
 * provisioned so that shader samplers always resolve to a valid OpenGL object.
 */
public final class ShadowMap {
    private static final int COLOR_TARGET_COUNT = 3;

    private final boolean enabled;
    private final int resolution;
    private final int[] colorTextures;
    private int depthTexture;

    public ShadowMap(PackDirectives directives, ShaderProperties properties, Config config) {
        this.enabled = config.shadowsEnabled(directives);
        this.resolution = enabled ? config.shadowResolution(directives) : 0;
        this.colorTextures = new int[COLOR_TARGET_COUNT];

        if (enabled && resolution > 0) {
            allocateTextures();
            registerBindings();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getResolution() {
        return resolution;
    }

    public void destroy() {
        deleteTexture(depthTexture);
        depthTexture = 0;

        for (int i = 0; i < colorTextures.length; i++) {
            deleteTexture(colorTextures[i]);
            colorTextures[i] = 0;
        }
    }

    private void allocateTextures() {
        depthTexture = createDepthTexture(resolution);
        for (int i = 0; i < colorTextures.length; i++) {
            colorTextures[i] = createColorTexture(resolution);
        }
    }

    private void registerBindings() {
        TextureBindingRegistry.register("oculus_shadow_depth", TextureBinding.texture2D(() -> depthTexture));
        TextureBindingRegistry.register("oculus_shadow_color", TextureBinding.texture2D(() -> colorTextures[0]));
        TextureBindingRegistry.register("oculus_shadow_color1", TextureBinding.texture2D(() -> colorTextures[1]));
        TextureBindingRegistry.register("oculus_shadow_color2", TextureBinding.texture2D(() -> colorTextures[2]));
    }

    private static int createDepthTexture(int size) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, size, size, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    private static int createColorTexture(int size) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    private static void deleteTexture(int texture) {
        if (texture > 0) {
            GL11.glDeleteTextures(texture);
        }
    }
}
