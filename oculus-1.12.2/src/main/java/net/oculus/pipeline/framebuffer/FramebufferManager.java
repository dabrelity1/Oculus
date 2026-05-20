package net.oculus.pipeline.framebuffer;

import java.nio.Buffer;
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
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.program.TextureBindingRegistry;
import net.oculus.pipeline.shadow.ShadowMap;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.texture.TextureLifecycleTracker;
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

    private final PackDirectives directives;
    private final ShaderProperties properties;
    private final Config config;
    private final Map<Integer, RenderTarget> renderTargets;
    private final Map<String, TextureBinding> registeredBindings;

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
        this.registeredBindings = new HashMap<>();
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

        try {
            rebuildTargets(indices, displayWidth, displayHeight);
            rebuildDepthTexture(displayWidth, displayHeight);
            rebuildNoiseTexture();
            registerDepthAliases();
            registerNoiseAliases();
        } catch (PostInstallCleanupException exception) {
            throw exception;
        } catch (RuntimeException | Error exception) {
            try {
                destroy();
            } catch (RuntimeException | Error cleanupException) {
                if (cleanupException != exception) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw exception;
        }
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
        try {
            Throwable failure = null;
            failure = runCleanup(failure, this::unregisterBindings);

            for (RenderTarget target : renderTargets.values()) {
                failure = runCleanup(failure, target::destroy);
            }

            failure = runCleanup(failure, () -> deleteTexture(depthTexture));

            failure = runCleanup(failure, () -> deleteTexture(noiseTexture));

            if (shadowMap != null) {
                failure = runCleanup(failure, shadowMap::destroy);
            }

            rethrowCleanupFailure(failure);
        } finally {
            renderTargets.clear();
            depthTexture = 0;
            noiseTexture = 0;
            shadowMap = null;
            destroyed = true;
        }
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
        int previousDepthTexture = depthTexture;
        int newDepthTexture = createDepthTexture(targetWidth, targetHeight);
        depthTexture = newDepthTexture;
        throwPostInstallCleanupFailure(
            runCleanup(null, () -> deleteTexture(previousDepthTexture)),
            "Failed to delete replaced legacy framebuffer depth texture");
    }

    private void rebuildNoiseTexture() {
        int previousNoiseTexture = noiseTexture;
        int newNoiseTexture = createNoiseTexture();
        noiseTexture = newNoiseTexture;
        throwPostInstallCleanupFailure(
            runCleanup(null, () -> deleteTexture(previousNoiseTexture)),
            "Failed to delete replaced legacy framebuffer noise texture");
    }

    private void registerRenderTargetAliases(int index, RenderTarget target) {
        TextureBinding currentBinding = TextureBinding.texture2D(target::getCurrentTextureId);
        TextureBinding flippedBinding = TextureBinding.texture2D(target::getFlippedTextureId);

        String base = "colortex" + index;
        registerBinding(base, currentBinding);

        if (index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
            String legacy = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index);
            registerBinding(legacy, currentBinding);
        }

        registerBinding("oculus_rt" + index, currentBinding);
        registerBinding("oculus_flipped_rt" + index, flippedBinding);
    }

    private void registerDepthAliases() {
        TextureBinding depthBinding = TextureBinding.texture2D(() -> depthTexture);
        registerBinding("gdepthtex", depthBinding);
        registerBinding("depthtex0", depthBinding);
        registerBinding("oculus_depth", depthBinding);
    }

    private void registerNoiseAliases() {
        TextureBinding noiseBinding = TextureBinding.texture2D(() -> noiseTexture);
        registerBinding("oculus_noise", noiseBinding);
        registerBinding("custom_noise", noiseBinding);
        registerBinding("noise_texture", noiseBinding);
        registerBinding("noisetex", noiseBinding);
    }

    private void registerBinding(String alias, TextureBinding binding) {
        TextureBinding previousBinding = registeredBindings.put(alias, binding);
        if (previousBinding != null) {
            TextureBindingRegistry.unregister(alias, previousBinding);
        }
        TextureBindingRegistry.register(alias, binding);
    }

    private void unregisterBindings() {
        try {
            Throwable failure = null;
            for (Map.Entry<String, TextureBinding> entry : registeredBindings.entrySet()) {
                failure = runCleanup(failure,
                    () -> TextureBindingRegistry.unregister(entry.getKey(), entry.getValue()));
            }
            rethrowCleanupFailure(failure);
        } finally {
            registeredBindings.clear();
        }
    }

    private static int createColorTexture(int width, int height) {
        int texture = createTexture("legacy framebuffer color texture");
        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D, 0, DEFAULT_COLOR_FORMAT, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            });
            return texture;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, runCleanup(null, () -> deleteTexture(texture)));
            throw exception;
        }
    }

    private static int createDepthTexture(int width, int height) {
        int texture = createTexture("legacy framebuffer depth texture");
        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0,
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            });
            return texture;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, runCleanup(null, () -> deleteTexture(texture)));
            throw exception;
        }
    }

    private int createNoiseTexture() {
        int texture = createTexture("legacy noise texture");
        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                int size = Math.max(1, directives.getNoiseTextureResolution());
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);

                byte[] pixels = new byte[size * size * 4];
                Random random = new Random(0L);
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        int color = random.nextInt() | 0xFF000000;
                        int offset = ((y * size) + x) * 4;
                        pixels[offset] = (byte) (color & 0xFF);
                        pixels[offset + 1] = (byte) ((color >>> 8) & 0xFF);
                        pixels[offset + 2] = (byte) ((color >>> 16) & 0xFF);
                        pixels[offset + 3] = (byte) 0xFF;
                    }
                }

                ByteBuffer buffer = BufferUtils.createByteBuffer(pixels.length);
                buffer.put(pixels);
                ((Buffer) buffer).flip();

                OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            });
            return texture;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, runCleanup(null, () -> deleteTexture(texture)));
            throw exception;
        }
    }

    private static int createTexture(String context) {
        int texture = GL11.glGenTextures();
        if (texture <= 0) {
            throw new IllegalStateException("Failed to create " + context);
        }
        return texture;
    }

    private static void deleteTexture(int texture) {
        if (texture <= 0) {
            return;
        }

        Throwable failure = null;
        try {
            GL11.glDeleteTextures(texture);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        } finally {
            try {
                TextureLifecycleTracker.onDeleteTexture(texture);
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }

        rethrowCleanupFailure(failure);
    }

    private static Throwable runCleanup(Throwable failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable addCleanupFailure(Throwable failure, Throwable exception) {
        if (failure == null) {
            return exception;
        }
        if (exception != failure) {
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private static void addSuppressedCleanupFailure(Throwable primary, Throwable cleanupFailure) {
        if (primary != null && cleanupFailure != null && cleanupFailure != primary) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    private static void rethrowCleanupFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new RuntimeException(failure);
    }

    private static void throwPostInstallCleanupFailure(Throwable failure, String message) {
        if (failure != null) {
            throw new PostInstallCleanupException(message, failure);
        }
    }

    private static final class PostInstallCleanupException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private PostInstallCleanupException(String message, Throwable cause) {
            super(message, cause);
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
            int previousMainTexture = textures[0];
            int previousAltTexture = textures[1];
            int newMainTexture = 0;
            int newAltTexture = 0;

            try {
                newMainTexture = createColorTexture(width, height);
                newAltTexture = createColorTexture(width, height);
            } catch (RuntimeException | Error exception) {
                final int failedMainTexture = newMainTexture;
                final int failedAltTexture = newAltTexture;
                Throwable failure = null;
                failure = runCleanup(failure, () -> deleteTexture(failedMainTexture));
                failure = runCleanup(failure, () -> deleteTexture(failedAltTexture));
                addSuppressedCleanupFailure(exception, failure);
                throw exception;
            }

            textures[0] = newMainTexture;
            textures[1] = newAltTexture;
            flipped = false;
            Throwable failure = null;
            failure = runCleanup(failure, () -> deleteTexture(previousMainTexture));
            failure = runCleanup(failure, () -> deleteTexture(previousAltTexture));
            rethrowCleanupFailure(failure);
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
            final int mainTexture = textures[0];
            final int altTexture = textures[1];
            textures[0] = 0;
            textures[1] = 0;
            flipped = false;

            Throwable failure = null;
            failure = runCleanup(failure, () -> deleteTexture(mainTexture));
            failure = runCleanup(failure, () -> deleteTexture(altTexture));
            rethrowCleanupFailure(failure);
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
