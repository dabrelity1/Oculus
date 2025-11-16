package net.oculus.pipeline;

import net.minecraft.util.BlockRenderLayer;

/**
 * Mirrors the OptiFine/Iris render stage semantics so shader packs can react to the
 * portion of the pipeline that is currently drawing.
 */
public enum WorldRenderingPhase {
    NONE,
    SKY,
    SUNSET,
    CUSTOM_SKY,
    SUN,
    MOON,
    STARS,
    VOID,
    TERRAIN_SOLID,
    TERRAIN_CUTOUT_MIPPED,
    TERRAIN_CUTOUT,
    ENTITIES,
    BLOCK_ENTITIES,
    DESTROY,
    OUTLINE,
    DEBUG,
    HAND_SOLID,
    TERRAIN_TRANSLUCENT,
    TRIPWIRE,
    PARTICLES,
    CLOUDS,
    RAIN_SNOW,
    WORLD_BORDER,
    HAND_TRANSLUCENT;

    public static WorldRenderingPhase fromBlockRenderLayer(BlockRenderLayer layer) {
        if (layer == null) {
            return WorldRenderingPhase.NONE;
        }

        switch (layer) {
            case SOLID:
                return WorldRenderingPhase.TERRAIN_SOLID;
            case CUTOUT_MIPPED:
                return WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED;
            case CUTOUT:
                return WorldRenderingPhase.TERRAIN_CUTOUT;
            case TRANSLUCENT:
                return WorldRenderingPhase.TERRAIN_TRANSLUCENT;
            default:
                throw new IllegalStateException("Unsupported block render layer: " + layer);
        }
    }
}
