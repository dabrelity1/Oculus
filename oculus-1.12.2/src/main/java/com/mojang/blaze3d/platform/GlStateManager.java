package com.mojang.blaze3d.platform;

/**
 * Minimal stub of Mojang's {@code GlStateManager}. Only the pieces referenced by the
 * deferred pipeline are provided.
 */
public final class GlStateManager {
    private GlStateManager() {
    }

    public static void bindTexture(int textureId) {
    }

    public static final class BlendState {
        public int srcRgb;
        public int dstRgb;
        public int srcAlpha;
        public int dstAlpha;
    }

    public enum SourceFactor {
        SRC_ALPHA(770),
        ONE(1),
        ONE_MINUS_SRC_ALPHA(771);

        private final int value;

        SourceFactor(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }
}
