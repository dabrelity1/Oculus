package net.oculus.pipeline.vertex;

import static org.junit.Assert.assertEquals;

import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import org.junit.Test;

public class RelictiumSeparateAoColorTest {
    @Test
    public void storesAoInAlphaAndPreservesRgbWhenSeparateAoIsEnabled() {
        int color = ColorABGR.pack(0x33, 0x66, 0x99, 0xFF);
        int separated = RelictiumSeparateAoColor.apply(color, 0.5F, true);

        assertEquals(0x33, ColorABGR.unpackRed(separated));
        assertEquals(0x66, ColorABGR.unpackGreen(separated));
        assertEquals(0x99, ColorABGR.unpackBlue(separated));
        assertEquals(127, ColorABGR.unpackAlpha(separated));
    }

    @Test
    public void clampsStoredAoAlphaToByteRange() {
        int color = ColorABGR.pack(0x33, 0x66, 0x99, 0xFF);

        int belowRange = RelictiumSeparateAoColor.withSeparateAo(color, -0.25F);
        int aboveRange = RelictiumSeparateAoColor.withSeparateAo(color, 1.5F);

        assertEquals(0, ColorABGR.unpackAlpha(belowRange));
        assertEquals(255, ColorABGR.unpackAlpha(aboveRange));
    }

    @Test
    public void multipliesRgbWhenSeparateAoIsDisabled() {
        int color = ColorABGR.pack(100, 150, 200, 255);
        int multiplied = RelictiumSeparateAoColor.apply(color, 0.5F, false);

        assertEquals(50, ColorABGR.unpackRed(multiplied));
        assertEquals(75, ColorABGR.unpackGreen(multiplied));
        assertEquals(100, ColorABGR.unpackBlue(multiplied));
        assertEquals(255, ColorABGR.unpackAlpha(multiplied));
    }
}
