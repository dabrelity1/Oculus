package net.oculus.pipeline.vertex;

import me.jellysquid.mods.sodium.client.util.color.ColorABGR;

public final class RelictiumSeparateAoColor {
    private RelictiumSeparateAoColor() {
    }

    public static int apply(int color, float ao, boolean useSeparateAo) {
        return useSeparateAo ? withSeparateAo(color, ao) : ColorABGR.mul(color, ao);
    }

    public static int withSeparateAo(int color, float ao) {
        return (color & 0x00FFFFFF) | (clampAlpha(ao) << 24);
    }

    private static int clampAlpha(float ao) {
        int value = (int) (ao * 255.0F);
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }
}
