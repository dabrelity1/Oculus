package net.oculus.texture.pbr;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.oculus.Oculus;
import net.oculus.texture.mipmap.CustomMipmapGenerator;

public final class PBRAtlasSprite extends TextureAtlasSprite {
    private final PBRType type;

    public PBRAtlasSprite(ResourceLocation name, TextureAtlasSprite source, PBRType type) {
        super(name.toString());
        this.type = type;
        copyFrom(source);
    }

    public PBRType getType() {
        return type;
    }

    public void generateCustomMipmaps(int mipLevel, CustomMipmapGenerator generator) {
        List<int[][]> generatedFrames = new ArrayList<>(framesTextureData.size());
        for (int i = 0; i < framesTextureData.size(); i++) {
            int[][] frameData = framesTextureData.get(i);
            if (frameData == null || frameData.length == 0 || frameData[0] == null) {
                generatedFrames.add(frameData);
            } else {
                generatedFrames.add(generator.generateMipLevels(frameData[0], width, height, mipLevel));
            }
        }
        setFramesTextureData(generatedFrames);
    }

    public boolean isAnimated() {
        return getAnimationMetadata(this) != null;
    }

    public int[][] getCurrentFrameTextureData() {
        return selectCurrentFrameTextureData(framesTextureData, getAnimationMetadata(this), frameCounter);
    }

    public void syncAnimationFrom(TextureAtlasSprite source) {
        AnimationMetadataSection sourceMetadata = getAnimationMetadata(source);
        AnimationMetadataSection targetMetadata = getAnimationMetadata(this);
        if (sourceMetadata == null || targetMetadata == null) {
            return;
        }

        int sourceFrame = getFrameCounter(source);
        int sourceTicks = 0;
        int sourceFrameCount = sourceMetadata.getFrameCount();
        for (int frame = 0; frame < sourceFrame && frame < sourceFrameCount; frame++) {
            sourceTicks += sourceMetadata.getFrameTimeSingle(frame);
        }
        sourceTicks += getTickCounter(source);

        int targetFrameCount = targetMetadata.getFrameCount();
        if (targetFrameCount <= 0) {
            return;
        }

        int cycleTime = 0;
        for (int frame = 0; frame < targetFrameCount; frame++) {
            cycleTime += targetMetadata.getFrameTimeSingle(frame);
        }
        if (cycleTime <= 0) {
            return;
        }

        int ticks = sourceTicks % cycleTime;
        int targetFrame = 0;
        while (targetFrame < targetFrameCount - 1) {
            int frameTime = targetMetadata.getFrameTimeSingle(targetFrame);
            if (ticks < frameTime) {
                break;
            }
            ticks -= frameTime;
            targetFrame++;
        }

        frameCounter = targetFrame;
        tickCounter = ticks;
    }

    static int[][] selectCurrentFrameTextureData(List<int[][]> frames,
                                                 AnimationMetadataSection metadata,
                                                 int frameCounter) {
        if (frames == null || frames.isEmpty()) {
            return null;
        }

        if (metadata != null && metadata.getFrameCount() > 0) {
            int frame = Math.max(0, Math.min(frameCounter, metadata.getFrameCount() - 1));
            for (int candidate = frame; candidate >= 0; candidate--) {
                int frameIndex = metadata.getFrameIndex(candidate);
                if (frameIndex >= 0 && frameIndex < frames.size()) {
                    int[][] frameData = frames.get(frameIndex);
                    if (frameData != null) {
                        return frameData;
                    }
                }
            }
        }

        for (int[][] frameData : frames) {
            if (frameData != null) {
                return frameData;
            }
        }
        return null;
    }

    private static AnimationMetadataSection getAnimationMetadata(TextureAtlasSprite sprite) {
        try {
            return ObfuscationReflectionHelper.getPrivateValue(
                TextureAtlasSprite.class,
                sprite,
                "animationMetadata",
                "field_110982_k");
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn("Failed to access TextureAtlasSprite animation metadata for PBR atlas", exception);
            return null;
        }
    }

    private static int getFrameCounter(TextureAtlasSprite sprite) {
        return getIntField(sprite, "frameCounter", "field_110973_g");
    }

    private static int getTickCounter(TextureAtlasSprite sprite) {
        return getIntField(sprite, "tickCounter", "field_110983_h");
    }

    private static int getIntField(TextureAtlasSprite sprite, String name, String srgName) {
        try {
            Integer value = ObfuscationReflectionHelper.getPrivateValue(TextureAtlasSprite.class, sprite, name, srgName);
            return value == null ? 0 : value;
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn("Failed to access TextureAtlasSprite {} for PBR atlas", name, exception);
            return 0;
        }
    }
}
