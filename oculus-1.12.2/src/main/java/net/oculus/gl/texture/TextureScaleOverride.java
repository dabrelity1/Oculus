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
        DimensionOverride xOverride = parseDimension(xValue, 'x');
        this.relativeX = xOverride.relative;
        this.sizeX = xOverride.absolute;
        this.scaleX = xOverride.isScale;

        DimensionOverride yOverride = parseDimension(yValue, 'y');
        this.relativeY = yOverride.relative;
        this.sizeY = yOverride.absolute;
        this.scaleY = yOverride.isScale;
    }

    private static DimensionOverride parseDimension(String token, char axis) {
        try {
            if (token.contains(".")) {
                float value = Float.parseFloat(token);
                return new DimensionOverride(true, value, 0, false);
            }

            int intValue = Integer.parseInt(token);
            return new DimensionOverride(false, 1.0F, intValue, false);
        } catch (NumberFormatException exception) {
            // Handle custom defines like REFLECTION_RES, SHADOW_MAP_SIZE, etc.
            // These are resolved at shader compile time, not here.
            // Return a placeholder that marks this as unresolved.
            return new DimensionOverride(true, 1.0F, 0, true);
        }
    }

    private static final class DimensionOverride {
        private final boolean isScale;
        private final float relative;
        private final int absolute;
        private final boolean isUnresolved;

        private DimensionOverride(boolean isScale, float relative, int absolute, boolean isUnresolved) {
            this.isScale = isScale;
            this.relative = relative;
            this.absolute = absolute;
            this.isUnresolved = isUnresolved;
        }
    }

    public int getX(int originalX) {
        return scaleX ? Math.round(originalX * relativeX) : sizeX;
    }

    public int getY(int originalY) {
        return scaleY ? Math.round(originalY * relativeY) : sizeY;
    }
}
