package net.oculus.gl.texture;

/**
 * Parses OptiFine-style buffer size overrides ("size.buffer.") into either
 * absolute pixel dimensions or relative scale factors.
 */
public final class TextureScaleOverride {
    private final float relativeX;
    private final float relativeY;
    private final int sizeX;
    private final int sizeY;
    private final boolean scaleX;
    private final boolean scaleY;

    public TextureScaleOverride(String xValue, String yValue) {
        DimensionOverride xOverride = parseDimension(xValue);
        this.relativeX = xOverride.relative;
        this.sizeX = xOverride.absolute;
        this.scaleX = xOverride.isScale;

        DimensionOverride yOverride = parseDimension(yValue);
        this.relativeY = yOverride.relative;
        this.sizeY = yOverride.absolute;
        this.scaleY = yOverride.isScale;
    }

    private static DimensionOverride parseDimension(String token) {
        if (token.contains(".")) {
            float value = Float.parseFloat(token);
            return new DimensionOverride(true, value, 0);
        }

        int intValue = Integer.parseInt(token);
        return new DimensionOverride(false, 1.0F, intValue);
    }

    private static final class DimensionOverride {
        private final boolean isScale;
        private final float relative;
        private final int absolute;

        private DimensionOverride(boolean isScale, float relative, int absolute) {
            this.isScale = isScale;
            this.relative = relative;
            this.absolute = absolute;
        }
    }

    public int getX(int originalX) {
        return scaleX ? (int) (originalX * relativeX) : sizeX;
    }

    public int getY(int originalY) {
        return scaleY ? (int) (originalY * relativeY) : sizeY;
    }
}
