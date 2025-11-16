package net.oculus.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;

/**
 * Provides world metadata uniforms that shader packs expect from the Iris pipeline.
 */
public final class WorldInfoUniforms {
    private static final Minecraft MC = Minecraft.getMinecraft();

    private WorldInfoUniforms() {
    }

    public static int getBedrockLevel() {
        WorldProvider provider = getProvider();
        if (provider == null) {
            return 0;
        }
        // There is no dedicated API in 1.12.2, so fall back to the provider's average ground level.
        return (int) provider.getAverageGroundLevel();
    }

    public static float getCloudHeight() {
        WorldProvider provider = getProvider();
        return provider != null ? provider.getCloudHeight() : 192.0F;
    }

    public static int getHeightLimit() {
        World world = getWorld();
        return world != null ? world.getHeight() : 256;
    }

    public static int getLogicalHeightLimit() {
        return getHeightLimit();
    }

    public static int hasCeiling() {
        WorldProvider provider = getProvider();
        return provider != null && !provider.hasSkyLight() ? 1 : 0;
    }

    public static int hasSkylight() {
        WorldProvider provider = getProvider();
        if (provider == null) {
            return 1;
        }
        return provider.hasSkyLight() ? 1 : 0;
    }

    public static float getAmbientLight() {
        WorldProvider provider = getProvider();
        if (provider != null) {
            float[] brightness = provider.getLightBrightnessTable();
            if (brightness != null && brightness.length > 0) {
                return brightness[0];
            }
        }
        return 0.0F;
    }

    private static World getWorld() {
        return MC != null ? MC.world : null;
    }

    private static WorldProvider getProvider() {
        World world = getWorld();
        return world != null ? world.provider : null;
    }
}
