package net.oculus.pipeline.framebuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.program.TextureBindingRegistry;
import net.oculus.pipeline.shadow.ShadowMap;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.util.Config;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/**
 * Coordinates the lifecycle of core framebuffers used by the shader pipeline.
 * The 1.12.2 backport now allocates lightweight render targets so that sampler
 * bindings always reference a concrete OpenGL texture, even before the full
 * deferred renderer is wired up.
 */
public final class FramebufferManager {
    private static final int DEFAULT_COLOR_FORMAT = GL11.GL_RGBA8;
    private static final int NOISE_SIZE = 128;

    private final PackDirectives directives;
    private final ShaderProperties properties;
    private final Config config;
    private final Map<Integer, RenderTarget> renderTargets;
    private final Random noiseRandom;

    private ShadowMap shadowMap;
    private int gbufferCount;
    private int depthTexture;
    private int noiseTexture;
    private int width;
    private int height;
    private boolean destroyed;

    public FramebufferManager(PackDirectives directives, ShaderProperties properties, Config config) {
        this.directives = Objects.requireNonNull(directives, "directives");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.config = Objects.requireNonNull(config, "config");
        this.renderTargets = new HashMap<>();
        this.noiseRandom = new Random();
    }

    public void prepareGbuffers() {
        if (destroyed) {
            throw new IllegalStateException("FramebufferManager has been destroyed");
        }

        PackRenderTargetDirectives renderTargetDirectives = directives.getRenderTargetDirectives();
        Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> settings = renderTargetDirectives.getRenderTargetSettings();
        Set<Integer> indices = settings.keySet();
        this.gbufferCount = Math.max(1, indices.size());

        Minecraft minecraft = Minecraft.getMinecraft();
        int displayWidth = minecraft != null ? Math.max(1, minecraft.displayWidth) : 1;
        int displayHeight = minecraft != null ? Math.max(1, minecraft.displayHeight) : 1;

        rebuildTargets(indices, displayWidth, displayHeight);
        rebuildDepthTexture(displayWidth, displayHeight);
        rebuildNoiseTexture();
        registerDepthAliases();
        registerNoiseAliases();
    }

    public int getGbufferCount() {
        return gbufferCount;
    }

    public void attachShadowMap(ShadowMap shadowMap) {
        this.shadowMap = shadowMap;
    }

    public ShadowMap getShadowMap() {
        return shadowMap;
    }

    public boolean hasShadowMap() {
        return shadowMap != null && shadowMap.isEnabled();
    }

    public void applyExplicitFlips(Map<Integer, Boolean> flips) {
        if (flips == null || flips.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, Boolean> entry : flips.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                continue;
            }
            RenderTarget target = renderTargets.get(entry.getKey());
            if (target != null) {
                target.flip();
            }
        }
    }

    public void destroy() {
        destroyed = true;

        for (RenderTarget target : renderTargets.values()) {
            target.destroy();
        }
        renderTargets.clear();

        deleteTexture(depthTexture);
        depthTexture = 0;

        deleteTexture(noiseTexture);
        noiseTexture = 0;

        if (shadowMap != null) {
            shadowMap.destroy();
            shadowMap = null;
        }

        TextureBindingRegistry.clear();
    }

    private void rebuildTargets(Set<Integer> definedIndices, int targetWidth, int targetHeight) {
        width = targetWidth;
        height = targetHeight;

        Set<Integer> indices = new HashSet<Integer>(definedIndices);
        if (indices.isEmpty()) {
            indices.add(0);
        }

        // Destroy any texture that is no longer referenced
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, RenderTarget> entry : renderTargets.entrySet()) {
            if (!indices.contains(entry.getKey())) {
                entry.getValue().destroy();
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(renderTargets::remove);

        for (Integer index : indices) {
            RenderTarget target = renderTargets.computeIfAbsent(index, RenderTarget::new);
            target.allocate(targetWidth, targetHeight);
            registerRenderTargetAliases(index, target);
        }
    }

    private void rebuildDepthTexture(int targetWidth, int targetHeight) {
        deleteTexture(depthTexture);
        depthTexture = createDepthTexture(targetWidth, targetHeight);
    }

    private void rebuildNoiseTexture() {
        deleteTexture(noiseTexture);
        noiseTexture = createNoiseTexture();
    }

    private void registerRenderTargetAliases(int index, RenderTarget target) {
        TextureBinding currentBinding = TextureBinding.texture2D(target::getCurrentTextureId);
        TextureBinding flippedBinding = TextureBinding.texture2D(target::getFlippedTextureId);

        String base = "colortex" + index;
        TextureBindingRegistry.register(base, currentBinding);

        if (index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
            String legacy = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index);
            TextureBindingRegistry.register(legacy, currentBinding);
        }

        TextureBindingRegistry.register("oculus_rt" + index, currentBinding);
        TextureBindingRegistry.register("oculus_flipped_rt" + index, flippedBinding);
    }

    private void registerDepthAliases() {
        TextureBinding depthBinding = TextureBinding.texture2D(() -> depthTexture);
        TextureBindingRegistry.register("gdepthtex", depthBinding);
        TextureBindingRegistry.register("depthtex0", depthBinding);
        TextureBindingRegistry.register("oculus_depth", depthBinding);
    }

    private void registerNoiseAliases() {
        TextureBinding noiseBinding = TextureBinding.texture2D(() -> noiseTexture);
        TextureBindingRegistry.register("oculus_noise", noiseBinding);
        TextureBindingRegistry.register("custom_noise", noiseBinding);
        TextureBindingRegistry.register("noise_texture", noiseBinding);
        TextureBindingRegistry.register("noisetex", noiseBinding);
    }

    private static int createColorTexture(int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, DEFAULT_COLOR_FORMAT, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    private static int createDepthTexture(int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    private int createNoiseTexture() {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);

        ByteBuffer buffer = BufferUtils.createByteBuffer(NOISE_SIZE * NOISE_SIZE * 4);
        for (int i = 0; i < NOISE_SIZE * NOISE_SIZE; i++) {
            buffer.put((byte) noiseRandom.nextInt(256));
            buffer.put((byte) noiseRandom.nextInt(256));
            buffer.put((byte) noiseRandom.nextInt(256));
            buffer.put((byte) 255);
        }
        buffer.flip();

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, NOISE_SIZE, NOISE_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    private static void deleteTexture(int texture) {
        if (texture > 0) {
            GL11.glDeleteTextures(texture);
        }
    }

    private static final class RenderTarget {
        private final int index;
        private final int[] textures;
        private boolean flipped;

        private RenderTarget(int index) {
            this.index = index;
            this.textures = new int[2];
        }

        private void allocate(int width, int height) {
            destroy();
            textures[0] = createColorTexture(width, height);
            textures[1] = createColorTexture(width, height);
            flipped = false;
        }

        private int getCurrentTextureId() {
            return flipped ? textures[1] : textures[0];
        }

        private int getFlippedTextureId() {
            return flipped ? textures[0] : textures[1];
        }

        private void flip() {
            flipped = !flipped;
        }

        private void destroy() {
            deleteTexture(textures[0]);
            deleteTexture(textures[1]);
            textures[0] = 0;
            textures[1] = 0;
        }

        @Override
        public String toString() {
            return "RenderTarget{" +
                "index=" + index +
                ", flipped=" + flipped +
                '}';
        }
    }
}
