package net.oculus.shaderpack.materialmap;

import java.util.Optional;

/**
 * Enumerates OptiFine's block render layer overrides.
 */
public enum BlockRenderType {
    SOLID,
    CUTOUT,
    CUTOUT_MIPPED,
    TRANSLUCENT;

    public static Optional<BlockRenderType> fromString(String name) {
        switch (name) {
            case "solid":
                return Optional.of(SOLID);
            case "cutout":
                return Optional.of(CUTOUT);
            case "cutout_mipped":
                return Optional.of(CUTOUT_MIPPED);
            case "translucent":
                return Optional.of(TRANSLUCENT);
            default:
                return Optional.empty();
        }
    }
}
