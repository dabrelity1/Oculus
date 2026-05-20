package net.oculus.texture.pbr.loader;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipError;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.AnimationFrame;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.oculus.Oculus;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.texture.format.TextureFormat;
import net.oculus.texture.format.TextureFormatLoader;
import net.oculus.texture.mipmap.ChannelMipmapGenerator;
import net.oculus.texture.mipmap.CustomMipmapGenerator;
import net.oculus.texture.mipmap.LinearBlendFunction;
import net.oculus.texture.pbr.PBRAtlasSprite;
import net.oculus.texture.pbr.PBRAtlasTexture;
import net.oculus.texture.pbr.PBRType;
import org.lwjgl.opengl.GL11;

public class AtlasPBRLoader implements PBRTextureLoader<TextureMap> {
    public static final ChannelMipmapGenerator LINEAR_MIPMAP_GENERATOR = new ChannelMipmapGenerator(
        LinearBlendFunction.INSTANCE,
        LinearBlendFunction.INSTANCE,
        LinearBlendFunction.INSTANCE,
        LinearBlendFunction.INSTANCE);

    @Override
    public void load(TextureMap atlas, IResourceManager resourceManager, PBRTextureConsumer pbrTextureConsumer) {
        Map<String, TextureAtlasSprite> uploadedSprites = getUploadedSprites(atlas);
        if (uploadedSprites == null || uploadedSprites.isEmpty()) {
            return;
        }

        int[] atlasSize = getAtlasSize(atlas, uploadedSprites.values());
        int atlasWidth = atlasSize[0];
        int atlasHeight = atlasSize[1];
        int mipLevel = atlas.getMipmapLevels();

        PBRAtlasTexture normalAtlas = new PBRAtlasTexture(atlas, PBRType.NORMAL);
        PBRAtlasTexture specularAtlas = new PBRAtlasTexture(atlas, PBRType.SPECULAR);

        for (TextureAtlasSprite sprite : uploadedSprites.values()) {
            if (isMissingSprite(atlas, sprite)) {
                continue;
            }

            PBRAtlasSprite normalSprite = createPBRSprite(sprite, resourceManager, atlas, mipLevel, PBRType.NORMAL);
            if (normalSprite != null) {
                normalAtlas.addSprite(normalSprite);
            }

            PBRAtlasSprite specularSprite = createPBRSprite(sprite, resourceManager, atlas, mipLevel, PBRType.SPECULAR);
            if (specularSprite != null) {
                specularAtlas.addSprite(specularSprite);
            }
        }

        uploadAndAcceptAtlas(normalAtlas, atlasWidth, atlasHeight, mipLevel, PBRType.NORMAL, pbrTextureConsumer,
            atlas.getBasePath());
        uploadAndAcceptAtlas(specularAtlas, atlasWidth, atlasHeight, mipLevel, PBRType.SPECULAR, pbrTextureConsumer,
            atlas.getBasePath());
    }

    void uploadAndAcceptAtlas(PBRAtlasTexture pbrAtlas, int atlasWidth, int atlasHeight, int mipLevel,
                              PBRType pbrType, PBRTextureConsumer pbrTextureConsumer, String atlasBasePath) {
        if (!pbrAtlas.hasSprites() || !pbrAtlas.tryUpload(atlasWidth, atlasHeight, mipLevel)) {
            return;
        }

        boolean accepted = false;
        try {
            if (pbrType == PBRType.NORMAL) {
                pbrTextureConsumer.acceptNormalTexture(pbrAtlas);
            } else {
                pbrTextureConsumer.acceptSpecularTexture(pbrAtlas);
            }
            accepted = true;
            OculusRuntimeValidation.logPBRAtlasTextureUploaded(
                atlasBasePath,
                pbrType,
                pbrAtlas.getGlTextureId(),
                atlasWidth,
                atlasHeight,
                mipLevel,
                pbrAtlas.getSpriteCount(),
                pbrAtlas.getAnimatedSpriteCount());
        } catch (RuntimeException | Error failure) {
            closeUnacceptedAtlasTexture(pbrAtlas, pbrType, accepted, failure);
            throw failure;
        }
    }

    private static void closeUnacceptedAtlasTexture(PBRAtlasTexture pbrAtlas, PBRType pbrType, boolean accepted,
                                                    Throwable failure) {
        if (pbrAtlas == null || accepted) {
            return;
        }

        try {
            pbrAtlas.deleteGlTexture();
        } catch (RuntimeException | Error cleanupFailure) {
            suppressRestoreFailure(failure, cleanupFailure);
            Oculus.LOGGER.debug("Failed to close unaccepted {} PBR atlas after consumer failure", pbrType,
                cleanupFailure);
        }
    }

    PBRAtlasSprite createPBRSprite(
        TextureAtlasSprite sprite,
        IResourceManager resourceManager,
        TextureMap atlas,
        int mipLevel,
        PBRType pbrType) {
        ResourceLocation spriteName = new ResourceLocation(sprite.getIconName());
        ResourceLocation pbrImageLocation = getPbrImageLocation(atlas.getBasePath(), spriteName, pbrType);

        try (IResource resource = resourceManager.getResource(pbrImageLocation);
             InputStream imageStream = resource.getInputStream()) {
            BufferedImage image = TextureUtil.readBufferedImage(imageStream);
            AnimationMetadataSection metadata = resource.getMetadata("animation");
            FrameSize frameSize = getFrameSize(image.getWidth(), image.getHeight(), metadata);

            int targetFrameWidth = sprite.getIconWidth();
            int targetFrameHeight = sprite.getIconHeight();
            if (frameSize.width != targetFrameWidth || frameSize.height != targetFrameHeight) {
                int targetImageWidth = image.getWidth() / frameSize.width * targetFrameWidth;
                int targetImageHeight = image.getHeight() / frameSize.height * targetFrameHeight;
                image = scaleImage(image, targetImageWidth, targetImageHeight);
                metadata = updateMetadataFrameSize(metadata, targetFrameWidth, targetFrameHeight);
            }

            ResourceLocation pbrSpriteName = new ResourceLocation(spriteName.getNamespace(), spriteName.getPath() + pbrType.getSuffix());
            PBRAtlasSprite pbrSprite = new PBRAtlasSprite(pbrSpriteName, sprite, pbrType);

            try (IResource inMemoryResource = new BufferedImageResource(pbrImageLocation, image, metadata)) {
                pbrSprite.clearFramesTextureData();
                pbrSprite.loadSpriteFrames(inMemoryResource, mipLevel + 1);
            }

            pbrSprite.generateCustomMipmaps(mipLevel, getMipmapGenerator(pbrType));
            pbrSprite.syncAnimationFrom(sprite);
            return pbrSprite;
        } catch (FileNotFoundException exception) {
            return null;
        } catch (RuntimeException exception) {
            Oculus.LOGGER.error("Unable to parse PBR texture metadata from {}", pbrImageLocation, exception);
            return null;
        } catch (IOException exception) {
            Oculus.LOGGER.error("Unable to load PBR texture {}", pbrImageLocation, exception);
            return null;
        } catch (ZipError exception) {
            Oculus.LOGGER.error("Unable to load PBR texture {}", pbrImageLocation, exception);
            return null;
        }
    }

    public static ResourceLocation getPbrImageLocation(String basePath, ResourceLocation spriteName, PBRType pbrType) {
        ResourceLocation imageLocation = new ResourceLocation(
            spriteName.getNamespace(),
            String.format("%s/%s%s", basePath, spriteName.getPath(), ".png"));
        return pbrType.appendToFileLocation(imageLocation);
    }

    static FrameSize getFrameSize(int imageWidth, int imageHeight, AnimationMetadataSection metadata) {
        int frameWidth;
        int frameHeight;
        if (metadata == null) {
            frameWidth = imageWidth;
            frameHeight = imageHeight;
        } else if (metadata.getFrameWidth() != -1) {
            frameWidth = metadata.getFrameWidth();
            frameHeight = metadata.getFrameHeight() != -1 ? metadata.getFrameHeight() : imageHeight;
        } else if (metadata.getFrameHeight() != -1) {
            frameWidth = imageWidth;
            frameHeight = metadata.getFrameHeight();
        } else {
            frameWidth = Math.min(imageWidth, imageHeight);
            frameHeight = Math.min(imageWidth, imageHeight);
        }

        if (frameWidth <= 0 || frameHeight <= 0 || imageWidth % frameWidth != 0 || imageHeight % frameHeight != 0) {
            throw new RuntimeException("invalid animation frame size " + frameWidth + "x" + frameHeight
                + " for image " + imageWidth + "x" + imageHeight);
        }
        return new FrameSize(frameWidth, frameHeight);
    }

    static BufferedImage scaleImage(BufferedImage image, int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Invalid scaled image size " + targetWidth + "x" + targetHeight);
        }

        if (targetWidth % image.getWidth() == 0 && targetHeight % image.getHeight() == 0) {
            return scaleNearest(image, targetWidth, targetHeight);
        }
        return scaleBilinear(image, targetWidth, targetHeight);
    }

    private static BufferedImage scaleNearest(BufferedImage image, int targetWidth, int targetHeight) {
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        float xScale = (float) targetWidth / image.getWidth();
        float yScale = (float) targetHeight / image.getHeight();
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = Math.min(image.getWidth() - 1, (int) ((x + 0.5f) / xScale));
                int sourceY = Math.min(image.getHeight() - 1, (int) ((y + 0.5f) / yScale));
                scaled.setRGB(x, y, image.getRGB(sourceX, sourceY));
            }
        }
        return scaled;
    }

    private static BufferedImage scaleBilinear(BufferedImage image, int targetWidth, int targetHeight) {
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        float xScale = (float) targetWidth / image.getWidth();
        float yScale = (float) targetHeight / image.getHeight();
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                float sourceX = (x + 0.5f) / xScale;
                float sourceY = (y + 0.5f) / yScale;

                int x1 = Math.round(sourceX);
                int y1 = Math.round(sourceY);
                int x0 = x1 - 1;
                int y0 = y1 - 1;

                boolean x0Valid = x0 >= 0;
                boolean y0Valid = y0 >= 0;
                boolean x1Valid = x1 < image.getWidth();
                boolean y1Valid = y1 < image.getHeight();

                int color;
                if (x0Valid && y0Valid && x1Valid && y1Valid) {
                    float leftWeight = (x1 + 0.5f) - sourceX;
                    float rightWeight = sourceX - (x0 + 0.5f);
                    float topWeight = (y1 + 0.5f) - sourceY;
                    float bottomWeight = sourceY - (y0 + 0.5f);

                    color = blendColor(
                        image.getRGB(x0, y0),
                        image.getRGB(x1, y0),
                        image.getRGB(x0, y1),
                        image.getRGB(x1, y1),
                        leftWeight * topWeight,
                        rightWeight * topWeight,
                        leftWeight * bottomWeight,
                        rightWeight * bottomWeight);
                } else if (x0Valid && x1Valid) {
                    float leftWeight = (x1 + 0.5f) - sourceX;
                    float rightWeight = sourceX - (x0 + 0.5f);
                    int validY = y0Valid ? y0 : y1;
                    color = blendColor(image.getRGB(x0, validY), image.getRGB(x1, validY), leftWeight, rightWeight);
                } else if (y0Valid && y1Valid) {
                    float topWeight = (y1 + 0.5f) - sourceY;
                    float bottomWeight = sourceY - (y0 + 0.5f);
                    int validX = x0Valid ? x0 : x1;
                    color = blendColor(image.getRGB(validX, y0), image.getRGB(validX, y1), topWeight, bottomWeight);
                } else {
                    color = image.getRGB(x0Valid ? x0 : x1, y0Valid ? y0 : y1);
                }
                scaled.setRGB(x, y, color);
            }
        }
        return scaled;
    }

    private static int blendColor(int c0, int c1, int c2, int c3, float w0, float w1, float w2, float w3) {
        return blendChannel(c0 >>> 24, c1 >>> 24, c2 >>> 24, c3 >>> 24, w0, w1, w2, w3) << 24
            | blendChannel(c0 >>> 16, c1 >>> 16, c2 >>> 16, c3 >>> 16, w0, w1, w2, w3) << 16
            | blendChannel(c0 >>> 8, c1 >>> 8, c2 >>> 8, c3 >>> 8, w0, w1, w2, w3) << 8
            | blendChannel(c0, c1, c2, c3, w0, w1, w2, w3);
    }

    private static int blendColor(int c0, int c1, float w0, float w1) {
        return blendChannel(c0 >>> 24, c1 >>> 24, w0, w1) << 24
            | blendChannel(c0 >>> 16, c1 >>> 16, w0, w1) << 16
            | blendChannel(c0 >>> 8, c1 >>> 8, w0, w1) << 8
            | blendChannel(c0, c1, w0, w1);
    }

    private static int blendChannel(int v0, int v1, int v2, int v3, float w0, float w1, float w2, float w3) {
        return Math.round((v0 & 0xFF) * w0 + (v1 & 0xFF) * w1 + (v2 & 0xFF) * w2 + (v3 & 0xFF) * w3);
    }

    private static int blendChannel(int v0, int v1, float w0, float w1) {
        return Math.round((v0 & 0xFF) * w0 + (v1 & 0xFF) * w1);
    }

    private static AnimationMetadataSection updateMetadataFrameSize(
        AnimationMetadataSection metadata,
        int frameWidth,
        int frameHeight) {
        if (metadata == null || (metadata.getFrameWidth() == -1 && metadata.getFrameHeight() == -1)) {
            return metadata;
        }

        List<AnimationFrame> frames = getAnimationFrames(metadata);
        if (frames == null) {
            return metadata;
        }

        return new AnimationMetadataSection(
            new ArrayList<>(frames),
            metadata.getFrameWidth() == -1 ? -1 : frameWidth,
            metadata.getFrameHeight() == -1 ? -1 : frameHeight,
            metadata.getFrameTime(),
            metadata.isInterpolate());
    }

    @SuppressWarnings("unchecked")
    private static List<AnimationFrame> getAnimationFrames(AnimationMetadataSection metadata) {
        try {
            return ObfuscationReflectionHelper.getPrivateValue(
                AnimationMetadataSection.class,
                metadata,
                "animationFrames",
                "field_110478_a");
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn("Failed to access animation metadata frames for PBR atlas", exception);
            return null;
        }
    }

    private static CustomMipmapGenerator getMipmapGenerator(PBRType pbrType) {
        TextureFormat format = TextureFormatLoader.getFormat();
        if (format != null) {
            CustomMipmapGenerator generator = format.getMipmapGenerator(pbrType);
            if (generator != null) {
                return generator;
            }
        }
        return LINEAR_MIPMAP_GENERATOR;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, TextureAtlasSprite> getUploadedSprites(TextureMap atlas) {
        try {
            return ObfuscationReflectionHelper.getPrivateValue(
                TextureMap.class,
                atlas,
                "mapUploadedSprites",
                "field_94252_e");
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn("Failed to access TextureMap uploaded sprites for PBR atlas", exception);
            return null;
        }
    }

    private static int[] getAtlasSize(TextureMap atlas, Collection<TextureAtlasSprite> sprites) {
        int previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        Throwable queryFailure = null;
        boolean recoverableQueryFailure = false;
        try {
            GlStateManager.bindTexture(atlas.getGlTextureId());
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width > 0 && height > 0) {
                return new int[] {width, height};
            }
        } catch (RuntimeException exception) {
            queryFailure = exception;
            recoverableQueryFailure = true;
            Oculus.LOGGER.warn("Failed to query TextureMap GL size for PBR atlas; falling back to sprite extents", exception);
        } catch (Error error) {
            queryFailure = error;
            throw error;
        } finally {
            restorePreviousTextureBinding(previousTextureBinding, queryFailure, recoverableQueryFailure);
        }

        int width = 0;
        int height = 0;
        for (TextureAtlasSprite sprite : sprites) {
            width = Math.max(width, sprite.getOriginX() + sprite.getIconWidth());
            height = Math.max(height, sprite.getOriginY() + sprite.getIconHeight());
        }
        return new int[] {Math.max(1, width), Math.max(1, height)};
    }

    private static void restorePreviousTextureBinding(int previousTextureBinding, Throwable queryFailure,
                                                      boolean recoverableQueryFailure) {
        try {
            GlStateManager.bindTexture(previousTextureBinding);
        } catch (RuntimeException | Error restoreFailure) {
            if (queryFailure != null) {
                suppressRestoreFailure(queryFailure, restoreFailure);
                if (recoverableQueryFailure) {
                    Oculus.LOGGER.debug("Failed to restore texture binding after recoverable PBR atlas size query failure",
                        restoreFailure);
                }
                return;
            }
            throw restoreFailure;
        }
    }

    private static void suppressRestoreFailure(Throwable queryFailure, Throwable restoreFailure) {
        if (restoreFailure != null && restoreFailure != queryFailure) {
            queryFailure.addSuppressed(restoreFailure);
        }
    }

    private static boolean isMissingSprite(TextureMap atlas, TextureAtlasSprite sprite) {
        return sprite == atlas.getMissingSprite() || TextureMap.LOCATION_MISSING_TEXTURE.toString().equals(sprite.getIconName());
    }

    static final class FrameSize {
        final int width;
        final int height;

        FrameSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class BufferedImageResource implements IResource {
        private final ResourceLocation location;
        private final byte[] imageBytes;
        private final AnimationMetadataSection animationMetadata;

        private BufferedImageResource(ResourceLocation location, BufferedImage image, AnimationMetadataSection animationMetadata)
            throws IOException {
            this.location = location;
            this.animationMetadata = animationMetadata;

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            imageBytes = output.toByteArray();
        }

        @Override
        public ResourceLocation getResourceLocation() {
            return location;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(imageBytes);
        }

        @Override
        public boolean hasMetadata() {
            return animationMetadata != null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends IMetadataSection> T getMetadata(String sectionName) {
            if ("animation".equals(sectionName)) {
                return (T) animationMetadata;
            }
            return null;
        }

        @Override
        public String getResourcePackName() {
            return "Oculus generated PBR atlas resource";
        }

        @Override
        public void close() {
        }
    }
}
