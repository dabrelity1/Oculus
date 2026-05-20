package net.oculus.texture.pbr;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipError;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.oculus.Oculus;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.gl.state.StateUpdateNotifiers;
import net.oculus.texture.TextureLifecycleTracker;
import net.oculus.texture.TextureTracker;
import net.oculus.texture.pbr.loader.PBRTextureLoader;
import net.oculus.texture.pbr.loader.PBRTextureLoader.PBRTextureConsumer;
import net.oculus.texture.pbr.loader.PBRTextureLoaderRegistry;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class PBRTextureManager {
    public static final PBRTextureManager INSTANCE = new PBRTextureManager();
    public static final boolean DEBUG = System.getProperty("oculus.pbr.debug") != null;

    private final Map<Integer, PBRTextureHolder> holders = new HashMap<>();
    private final Map<TextureMap, PBRAtlasHolder> atlasHolders = new IdentityHashMap<>();
    private final PBRTextureConsumerImpl consumer = new PBRTextureConsumerImpl();
    private final PBRTextureHolder defaultHolder = new PBRTextureHolder() {
        @Override
        public AbstractTexture getNormalTexture() {
            return getDefaultNormalTexture();
        }

        @Override
        public AbstractTexture getSpecularTexture() {
            return getDefaultSpecularTexture();
        }
    };

    private AbstractTexture defaultNormalTexture;
    private AbstractTexture defaultSpecularTexture;
    private boolean pbrSamplerUsed;

    private PBRTextureManager() {
    }

    public synchronized void resetSamplerUsage() {
        pbrSamplerUsed = false;
    }

    public synchronized void markPbrSamplerUsed() {
        pbrSamplerUsed = true;
    }

    public synchronized boolean hasPbrSampler() {
        return pbrSamplerUsed;
    }

    public static boolean isPbrSamplerName(String samplerName) {
        return "normals".equals(samplerName) || "specular".equals(samplerName);
    }

    public synchronized PBRTextureHolder getHolder(int id) {
        PBRTextureHolder holder = holders.get(id);
        return holder == null ? defaultHolder : holder;
    }

    public synchronized PBRTextureHolder getOrLoadHolder(int id) {
        if (id <= 0) {
            return defaultHolder;
        }

        PBRTextureHolder holder = holders.get(id);
        if (holder == null) {
            holder = loadHolder(id);
            holders.put(id, holder);
        }
        return holder;
    }

    public synchronized void onDeleteTexture(int id) {
        PBRTextureHolder holder = holders.remove(id);
        if (holder != null && holder != defaultHolder) {
            closeHolder(holder);
        }
    }

    public synchronized void registerAtlasTexture(TextureMap atlas, PBRType type, PBRAtlasTexture texture) {
        PBRAtlasHolder holder = atlasHolders.computeIfAbsent(atlas, ignored -> new PBRAtlasHolder());
        if (type == PBRType.NORMAL) {
            holder.setNormalAtlas(texture);
        } else {
            holder.setSpecularAtlas(texture);
        }
    }

    public synchronized void unregisterAtlasTexture(TextureMap atlas, PBRType type, PBRAtlasTexture texture) {
        PBRAtlasHolder holder = atlasHolders.get(atlas);
        if (holder == null) {
            return;
        }

        if (type == PBRType.NORMAL) {
            if (holder.getNormalAtlas() == texture) {
                holder.setNormalAtlas(null);
            }
        } else if (holder.getSpecularAtlas() == texture) {
            holder.setSpecularAtlas(null);
        }

        if (holder.isEmpty()) {
            atlasHolders.remove(atlas);
        }
    }

    public synchronized void updateAtlasAnimations(TextureMap atlas) {
        PBRAtlasHolder holder = atlasHolders.get(atlas);
        if (holder != null) {
            holder.updateAnimations();
        }
    }

    public synchronized void clear() {
        Throwable failure = null;
        try {
            while (!holders.isEmpty()) {
                Map.Entry<Integer, PBRTextureHolder> entry = holders.entrySet().iterator().next();
                PBRTextureHolder holder = entry.getValue();
                holders.remove(entry.getKey());
                if (holder != defaultHolder) {
                    try {
                        closeHolder(holder);
                    } catch (RuntimeException | Error exception) {
                        failure = collectCleanupFailure(failure, exception);
                    }
                }
            }
        } finally {
            try {
                closeRegisteredAtlasTextures();
            } catch (RuntimeException | Error exception) {
                failure = collectCleanupFailure(failure, exception);
            } finally {
                holders.clear();
                atlasHolders.clear();
                consumer.clearReferences();
            }
        }
        rethrowCleanupFailure(failure);
    }

    public synchronized void close() {
        Throwable failure = null;
        try {
            clear();
        } catch (RuntimeException | Error exception) {
            failure = collectCleanupFailure(failure, exception);
        }
        try {
            closeDefaultTextures();
        } catch (RuntimeException | Error exception) {
            failure = collectCleanupFailure(failure, exception);
        }
        rethrowCleanupFailure(failure);
    }

    synchronized int getLoadedHolderCount() {
        return holders.size();
    }

    public static void notifyPBRTexturesChanged() {
        Throwable failure = null;
        failure = runTextureChangeNotification(failure, StateUpdateNotifiers::notifyNormalTextureChanged);
        failure = runTextureChangeNotification(failure, StateUpdateNotifiers::notifySpecularTextureChanged);
        rethrowTextureChangeNotificationFailure(failure);
    }

    private static Throwable runTextureChangeNotification(Throwable failure, Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException | Error exception) {
            if (failure != null) {
                return collectCleanupFailure(failure, exception);
            }
            return exception;
        }
        return failure;
    }

    private static void rethrowTextureChangeNotificationFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException(failure);
    }

    private static Throwable collectCleanupFailure(Throwable failure, Throwable exception) {
        if (failure != null) {
            if (exception != failure) {
                failure.addSuppressed(exception);
            }
            return failure;
        }
        return exception;
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
        throw new IllegalStateException(failure);
    }

    static byte[] singleColorBytes(int rgba) {
        return new byte[] {
            (byte) ((rgba >>> 24) & 0xFF),
            (byte) ((rgba >>> 16) & 0xFF),
            (byte) ((rgba >>> 8) & 0xFF),
            (byte) (rgba & 0xFF)
        };
    }

    private PBRTextureHolder loadHolder(int id) {
        AbstractTexture texture = findTextureById(id);
        if (texture == null) {
            return defaultHolder;
        }

        PBRTextureLoader<AbstractTexture> loader = PBRTextureLoaderRegistry.INSTANCE.getLoader(texture.getClass());
        if (loader == null) {
            return defaultHolder;
        }

        IResourceManager resourceManager = getResourceManager();
        if (resourceManager == null) {
            return defaultHolder;
        }

        int previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        Throwable loadFailure = null;
        boolean recoverableLoadFailure = false;
        PBRTextureHolder loadedHolder = null;
        try {
            consumer.clear();
            loader.load(texture, resourceManager, consumer);
            loadedHolder = consumer.toHolder();
            OculusRuntimeValidation.logPBRHolderResolved(
                id,
                texture.getClass().getName(),
                consumer.hasLoadedNormalTexture(),
                consumer.hasLoadedSpecularTexture(),
                loadedHolder.getNormalTexture().getGlTextureId(),
                loadedHolder.getSpecularTexture().getGlTextureId());
            consumer.clearReferences();
            return loadedHolder;
        } catch (RuntimeException exception) {
            loadFailure = exception;
            recoverableLoadFailure = true;
            cleanupAfterLoadFailure(exception);
            if (DEBUG) {
                Oculus.LOGGER.warn("Failed to load PBR textures for texture {}", id, exception);
            } else {
                Oculus.LOGGER.debug("Failed to load PBR textures for texture {}", id, exception);
            }
            return defaultHolder;
        } catch (ZipError exception) {
            loadFailure = exception;
            recoverableLoadFailure = true;
            cleanupAfterLoadFailure(exception);
            if (DEBUG) {
                Oculus.LOGGER.warn("Failed to load PBR textures for texture {}", id, exception);
            } else {
                Oculus.LOGGER.debug("Failed to load PBR textures for texture {}", id, exception);
            }
            return defaultHolder;
        } catch (Error error) {
            loadFailure = error;
            cleanupAfterLoadFailure(error);
            throw error;
        } finally {
            try {
                restorePreviousTextureBinding(previousTextureBinding, loadFailure, recoverableLoadFailure);
            } catch (RuntimeException | Error restoreFailure) {
                closeLoadedHolderAfterRestoreFailure(loadedHolder, restoreFailure);
                throw restoreFailure;
            }
        }
    }

    private void closeLoadedHolderAfterRestoreFailure(PBRTextureHolder loadedHolder, Throwable restoreFailure) {
        if (loadedHolder == null || loadedHolder == defaultHolder) {
            return;
        }

        try {
            closeHolder(loadedHolder);
        } catch (RuntimeException | Error cleanupFailure) {
            collectCleanupFailure(restoreFailure, cleanupFailure);
        }
    }

    private void restorePreviousTextureBinding(int previousTextureBinding, Throwable loadFailure,
                                               boolean recoverableLoadFailure) {
        try {
            GlStateManager.bindTexture(previousTextureBinding);
        } catch (RuntimeException | Error restoreFailure) {
            if (loadFailure != null) {
                collectCleanupFailure(loadFailure, restoreFailure);
                if (recoverableLoadFailure) {
                    Oculus.LOGGER.debug("Failed to restore texture binding after recoverable PBR load failure",
                        restoreFailure);
                }
                return;
            }
            throw restoreFailure;
        }
    }

    private void cleanupAfterLoadFailure(Throwable failure) {
        try {
            consumer.closeLoadedTextures();
        } catch (RuntimeException | Error cleanupFailure) {
            collectCleanupFailure(failure, cleanupFailure);
        }
    }

    private AbstractTexture findTextureById(int id) {
        AbstractTexture trackedTexture = TextureTracker.INSTANCE.getTexture(id);
        if (trackedTexture != null) {
            return trackedTexture;
        }

        TextureManager textureManager = getTextureManager();
        if (textureManager == null) {
            return null;
        }

        Map<ResourceLocation, ITextureObject> textures = getTextureObjects(textureManager);
        if (textures == null || textures.isEmpty()) {
            return null;
        }

        for (ITextureObject texture : textures.values()) {
            if (texture instanceof AbstractTexture) {
                AbstractTexture abstractTexture = (AbstractTexture) texture;
                if (getExistingTextureId(abstractTexture) == id) {
                    return abstractTexture;
                }
            }
        }

        return null;
    }

    private static int getExistingTextureId(AbstractTexture texture) {
        try {
            Integer textureId = ObfuscationReflectionHelper.getPrivateValue(
                AbstractTexture.class,
                texture,
                "glTextureId",
                "field_110553_a");
            return textureId == null ? -1 : textureId;
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn("Failed to access AbstractTexture GL id for PBR lookup", exception);
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<ResourceLocation, ITextureObject> getTextureObjects(TextureManager textureManager) {
        try {
            return ObfuscationReflectionHelper.getPrivateValue(
                TextureManager.class,
                textureManager,
                "mapTextureObjects",
                "field_110585_a");
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn("Failed to access TextureManager texture map for PBR lookup", exception);
            return null;
        }
    }

    private IResourceManager getResourceManager() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null ? null : minecraft.getResourceManager();
    }

    private TextureManager getTextureManager() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null ? null : minecraft.getTextureManager();
    }

    private AbstractTexture getDefaultNormalTexture() {
        if (defaultNormalTexture == null) {
            defaultNormalTexture = new SingleColorTexture(PBRType.NORMAL.getDefaultValue());
        }
        return defaultNormalTexture;
    }

    private AbstractTexture getDefaultSpecularTexture() {
        if (defaultSpecularTexture == null) {
            defaultSpecularTexture = new SingleColorTexture(PBRType.SPECULAR.getDefaultValue());
        }
        return defaultSpecularTexture;
    }

    private void closeDefaultTextures() {
        AbstractTexture normalTexture = defaultNormalTexture;
        AbstractTexture specularTexture = defaultSpecularTexture;
        defaultNormalTexture = null;
        defaultSpecularTexture = null;

        closeDefaultPbrTexture(normalTexture, "default normal PBR texture");
        if (specularTexture != normalTexture) {
            closeDefaultPbrTexture(specularTexture, "default specular PBR texture");
        }
    }

    private void closeHolder(PBRTextureHolder holder) {
        Throwable failure = null;
        AbstractTexture normalTexture = null;
        AbstractTexture specularTexture = null;

        try {
            normalTexture = holder.getNormalTexture();
        } catch (RuntimeException | Error exception) {
            failure = collectCleanupFailure(failure, exception);
        }

        try {
            specularTexture = holder.getSpecularTexture();
        } catch (RuntimeException | Error exception) {
            failure = collectCleanupFailure(failure, exception);
        }

        closeOwnedPbrTexture(normalTexture);
        if (specularTexture != normalTexture) {
            closeOwnedPbrTexture(specularTexture);
        }
        rethrowCleanupFailure(failure);
    }

    private void closeRegisteredAtlasTextures() {
        if (atlasHolders.isEmpty()) {
            return;
        }

        Set<PBRAtlasTexture> atlasTextures = Collections.newSetFromMap(new IdentityHashMap<PBRAtlasTexture, Boolean>());
        for (PBRAtlasHolder atlasHolder : atlasHolders.values()) {
            PBRAtlasTexture normalAtlas = atlasHolder.getNormalAtlas();
            PBRAtlasTexture specularAtlas = atlasHolder.getSpecularAtlas();
            if (normalAtlas != null) {
                atlasTextures.add(normalAtlas);
            }
            if (specularAtlas != null) {
                atlasTextures.add(specularAtlas);
            }
        }

        for (PBRAtlasTexture atlasTexture : atlasTextures) {
            closeOwnedPbrTexture(atlasTexture);
        }
    }

    private void closeOwnedPbrTexture(AbstractTexture texture) {
        if (texture != null && texture != defaultNormalTexture && texture != defaultSpecularTexture) {
            deletePbrTexture(texture, "PBR texture");
        }
    }

    private void closeDefaultPbrTexture(AbstractTexture texture, String description) {
        if (texture == null) {
            return;
        }

        deletePbrTexture(texture, description);
    }

    private static void deletePbrTexture(AbstractTexture texture, String description) {
        int textureId = getExistingTextureId(texture);
        try {
            texture.deleteGlTexture();
        } catch (RuntimeException | Error exception) {
            if (DEBUG) {
                Oculus.LOGGER.warn("Failed to close {}", description, exception);
            } else {
                Oculus.LOGGER.debug("Failed to close {}", description, exception);
            }
        } finally {
            TextureLifecycleTracker.onDeleteTexture(textureId);
        }
    }

    private final class PBRTextureConsumerImpl implements PBRTextureConsumer {
        private AbstractTexture normalTexture;
        private AbstractTexture specularTexture;
        private boolean loadedNormalTexture;
        private boolean loadedSpecularTexture;
        private boolean changed;

        @Override
        public void acceptNormalTexture(AbstractTexture texture) {
            if (texture != null) {
                normalTexture = texture;
                loadedNormalTexture = true;
                changed = true;
            }
        }

        @Override
        public void acceptSpecularTexture(AbstractTexture texture) {
            if (texture != null) {
                specularTexture = texture;
                loadedSpecularTexture = true;
                changed = true;
            }
        }

        private void clear() {
            normalTexture = getDefaultNormalTexture();
            specularTexture = getDefaultSpecularTexture();
            loadedNormalTexture = false;
            loadedSpecularTexture = false;
            changed = false;
        }

        private void clearReferences() {
            normalTexture = null;
            specularTexture = null;
            loadedNormalTexture = false;
            loadedSpecularTexture = false;
            changed = false;
        }

        private PBRTextureHolder toHolder() {
            return changed ? new PBRTextureHolderImpl(normalTexture, specularTexture) : defaultHolder;
        }

        private boolean hasLoadedNormalTexture() {
            return loadedNormalTexture;
        }

        private boolean hasLoadedSpecularTexture() {
            return loadedSpecularTexture;
        }

        private void closeLoadedTextures() {
            closeOwnedPbrTexture(normalTexture);
            if (specularTexture != normalTexture) {
                closeOwnedPbrTexture(specularTexture);
            }
            clearReferences();
        }
    }

    private static final class PBRTextureHolderImpl implements PBRTextureHolder {
        private final AbstractTexture normalTexture;
        private final AbstractTexture specularTexture;

        private PBRTextureHolderImpl(AbstractTexture normalTexture, AbstractTexture specularTexture) {
            this.normalTexture = normalTexture;
            this.specularTexture = specularTexture;
        }

        @Override
        public AbstractTexture getNormalTexture() {
            return normalTexture;
        }

        @Override
        public AbstractTexture getSpecularTexture() {
            return specularTexture;
        }
    }

    private static final class SingleColorTexture extends AbstractTexture {
        private final int rgba;

        private SingleColorTexture(int rgba) {
            this.rgba = rgba;
            boolean success = false;
            try {
                upload();
                success = true;
            } finally {
                if (!success) {
                    deletePbrTexture(this, "default PBR texture");
                }
            }
        }

        @Override
        public void loadTexture(IResourceManager resourceManager) {
            upload();
        }

        private void upload() {
            byte[] pixel = singleColorBytes(rgba);
            ByteBuffer buffer = BufferUtils.createByteBuffer(pixel.length);
            buffer.put(pixel);
            ((Buffer) buffer).flip();

            int previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int textureId = getGlTextureId();
            if (textureId <= 0) {
                throw new IllegalStateException("Failed to allocate default PBR texture");
            }
            Throwable setupFailure = null;
            try {
                GlStateManager.bindTexture(textureId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
                GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA8,
                    1,
                    1,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    buffer);
                TextureLifecycleTracker.onTexImage2D(textureId, GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1);
            } catch (RuntimeException | Error exception) {
                setupFailure = exception;
                throw exception;
            } finally {
                restorePreviousTextureBinding(previousTextureBinding, setupFailure);
            }
        }

        private static void restorePreviousTextureBinding(int previousTextureBinding, Throwable setupFailure) {
            try {
                GlStateManager.bindTexture(previousTextureBinding);
            } catch (RuntimeException | Error restoreFailure) {
                if (setupFailure != null) {
                    collectCleanupFailure(setupFailure, restoreFailure);
                    return;
                }
                throw restoreFailure;
            }
        }
    }
}
