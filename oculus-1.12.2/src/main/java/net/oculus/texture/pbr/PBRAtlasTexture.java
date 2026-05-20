package net.oculus.texture.pbr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;
import net.oculus.Oculus;
import net.oculus.texture.TextureLifecycleTracker;
import org.lwjgl.opengl.GL11;

public class PBRAtlasTexture extends AbstractTexture {
    private final TextureMap atlasTexture;
    private final PBRType type;
    private final List<PBRAtlasSprite> sprites = new ArrayList<>();
    private final Set<PBRAtlasSprite> animatedSprites = new LinkedHashSet<>();

    public PBRAtlasTexture(TextureMap atlasTexture, PBRType type) {
        this.atlasTexture = atlasTexture;
        this.type = type;
    }

    public TextureMap getAtlasTexture() {
        return atlasTexture;
    }

    public PBRType getType() {
        return type;
    }

    public void addSprite(PBRAtlasSprite sprite) {
        sprites.add(sprite);
        if (sprite.isAnimated()) {
            animatedSprites.add(sprite);
        }
    }

    public boolean hasSprites() {
        return !sprites.isEmpty();
    }

    public int getSpriteCount() {
        return sprites.size();
    }

    public int getAnimatedSpriteCount() {
        return animatedSprites.size();
    }

    public boolean tryUpload(int atlasWidth, int atlasHeight, int mipLevel) {
        try {
            upload(atlasWidth, atlasHeight, mipLevel);
            return true;
        } catch (Throwable throwable) {
            Oculus.LOGGER.error("Unable to upload {} PBR texture atlas", type, throwable);
            closeFailedUploadTexture();
            return false;
        }
    }

    public void upload(int atlasWidth, int atlasHeight, int mipLevel) {
        int previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        Throwable uploadFailure = null;
        try {
            TextureUtil.allocateTextureImpl(getGlTextureId(), mipLevel, atlasWidth, atlasHeight);
            trackAtlasAllocation(getGlTextureId(), atlasWidth, atlasHeight);
            uploadDefaultColor(atlasWidth, atlasHeight, mipLevel);

            for (PBRAtlasSprite sprite : sprites) {
                try {
                    uploadSprite(sprite);
                } catch (Throwable throwable) {
                    CrashReport crashReport = CrashReport.makeCrashReport(throwable, "Stitching PBR texture atlas");
                    CrashReportCategory category = crashReport.makeCategory("PBR texture being stitched together");
                    category.addCrashSection("PBR type", type);
                    category.addCrashSection("Sprite", sprite);
                    throw new ReportedException(crashReport);
                }
            }
        } catch (RuntimeException | Error exception) {
            uploadFailure = exception;
            throw exception;
        } finally {
            restorePreviousTextureBinding(previousTextureBinding, uploadFailure);
        }

        if (!animatedSprites.isEmpty()) {
            PBRTextureManager.INSTANCE.registerAtlasTexture(atlasTexture, type, this);
        }
    }

    public void updateAnimations() {
        if (animatedSprites.isEmpty()) {
            return;
        }

        int previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        Throwable updateFailure = null;
        try {
            GlStateManager.bindTexture(getGlTextureId());
            for (PBRAtlasSprite sprite : animatedSprites) {
                updateFailure = updateSpriteAnimation(updateFailure, sprite);
            }
            rethrowFailure(updateFailure);
        } catch (RuntimeException | Error exception) {
            updateFailure = exception;
            throw exception;
        } finally {
            restorePreviousTextureBinding(previousTextureBinding, updateFailure);
        }
    }

    @Override
    public void loadTexture(IResourceManager resourceManager) throws IOException {
    }

    @Override
    public void deleteGlTexture() {
        int textureId = this.glTextureId;
        Throwable failure = null;
        try {
            try {
                PBRTextureManager.INSTANCE.unregisterAtlasTexture(atlasTexture, type, this);
            } catch (RuntimeException | Error exception) {
                failure = collectFailure(failure, exception);
            }
            try {
                super.deleteGlTexture();
            } catch (RuntimeException | Error exception) {
                failure = collectFailure(failure, exception);
            }
        } finally {
            TextureLifecycleTracker.onDeleteTexture(textureId);
        }
        rethrowFailure(failure);
    }

    private void closeFailedUploadTexture() {
        try {
            deleteGlTexture();
        } catch (RuntimeException | Error exception) {
            Oculus.LOGGER.debug("Failed to close failed {} PBR texture atlas", type, exception);
        }
    }

    private static Throwable updateSpriteAnimation(Throwable failure, PBRAtlasSprite sprite) {
        try {
            sprite.updateAnimation();
        } catch (RuntimeException | Error exception) {
            return collectFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable collectFailure(Throwable failure, Throwable exception) {
        if (failure == null) {
            return exception;
        }
        if (exception != failure) {
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private static void rethrowFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Unexpected PBR atlas animation failure", failure);
    }

    private static void restorePreviousTextureBinding(int previousTextureBinding, Throwable primaryFailure) {
        try {
            GlStateManager.bindTexture(previousTextureBinding);
        } catch (RuntimeException | Error restoreFailure) {
            if (primaryFailure != null) {
                collectFailure(primaryFailure, restoreFailure);
                return;
            }
            throw restoreFailure;
        }
    }

    private void uploadDefaultColor(int atlasWidth, int atlasHeight, int mipLevel) {
        int[][] levels = new int[mipLevel + 1][];
        int color = defaultArgb(type);
        for (int level = 0; level <= mipLevel; level++) {
            int width = Math.max(1, atlasWidth >> level);
            int height = Math.max(1, atlasHeight >> level);
            int[] data = new int[width * height];
            for (int i = 0; i < data.length; i++) {
                data[i] = color;
            }
            levels[level] = data;
        }
        TextureUtil.uploadTextureMipmap(levels, atlasWidth, atlasHeight, 0, 0, false, false);
    }

    private void uploadSprite(PBRAtlasSprite sprite) {
        int[][] frameTextureData = sprite.getCurrentFrameTextureData();
        if (frameTextureData != null) {
            TextureUtil.uploadTextureMipmap(
                frameTextureData,
                sprite.getIconWidth(),
                sprite.getIconHeight(),
                sprite.getOriginX(),
                sprite.getOriginY(),
                false,
                false);
        }
    }

    static int defaultArgb(PBRType type) {
        int rgba = type.getDefaultValue();
        return rgba >>> 8 | (rgba & 0xFF) << 24;
    }

    static void trackAtlasAllocation(int textureId, int atlasWidth, int atlasHeight) {
        TextureLifecycleTracker.onTextureUtilAllocation2D(textureId, atlasWidth, atlasHeight);
    }
}
