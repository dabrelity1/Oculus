package net.oculus.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;

/**
 * Provides world metadata uniforms that shader packs expect from the Iris pipeline.
 */
public final class WorldInfoUniforms {
    private WorldInfoUniforms() {
    }

    public static int getBedrockLevel() {
        return 0;
    }

    public static float getCloudHeight() {
        return getCloudHeight(getProvider());
    }

    public static int getHeightLimit() {
        World world = getWorld();
        return world != null ? world.getHeight() : 256;
    }

    public static int getLogicalHeightLimit() {
        return getLogicalHeightLimit(getProvider(), getHeightLimit());
    }

    public static int hasCeiling() {
        return hasCeiling(getProvider());
    }

    static int hasCeiling(WorldProvider provider) {
        return provider != null && provider.isNether() ? 1 : 0;
    }

    public static int hasSkylight() {
        WorldProvider provider = getProvider();
        if (provider == null) {
            return 1;
        }
        return provider.hasSkyLight() ? 1 : 0;
    }

    public static float getAmbientLight() {
        return getAmbientLight(getProvider());
    }

    static float getCloudHeight(WorldProvider provider) {
        if (provider == null) {
            return 192.0F;
        }

        if (isVanillaNether(provider) || isVanillaEnd(provider)) {
            return Float.NaN;
        }

        return provider.getCloudHeight();
    }

    static int getLogicalHeightLimit(WorldProvider provider, int heightLimit) {
        if (isVanillaNether(provider)) {
            return 128;
        }

        return heightLimit;
    }

    static float getAmbientLight(WorldProvider provider) {
        if (isVanillaNether(provider)) {
            return 0.1F;
        }

        if (provider != null) {
            float[] brightness = provider.getLightBrightnessTable();
            if (brightness != null && brightness.length > 0) {
                return brightness[0];
            }
        }
        return 0.0F;
    }

    private static boolean isVanillaNether(WorldProvider provider) {
        return getDimensionType(provider) == DimensionType.NETHER;
    }

    private static boolean isVanillaEnd(WorldProvider provider) {
        return getDimensionType(provider) == DimensionType.THE_END;
    }

    private static DimensionType getDimensionType(WorldProvider provider) {
        if (provider == null) {
            return null;
        }

        try {
            return provider.getDimensionType();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static World getWorld() {
        Minecraft minecraft = getMinecraft();
        return minecraft != null ? minecraft.world : null;
    }

    private static WorldProvider getProvider() {
        World world = getWorld();
        return world != null ? world.provider : null;
    }

    private static Minecraft getMinecraft() {
        return Minecraft.getMinecraft();
    }
}
