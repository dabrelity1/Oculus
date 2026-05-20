package net.oculus.texture.mipmap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChannelMipmapGeneratorTest {
    @Test
    public void linearGeneratorBuildsArgbMipsPerChannel() {
        ChannelMipmapGenerator generator = new ChannelMipmapGenerator(
            LinearBlendFunction.INSTANCE,
            LinearBlendFunction.INSTANCE,
            LinearBlendFunction.INSTANCE,
            LinearBlendFunction.INSTANCE);

        int[][] mipLevels = generator.generateMipLevels(new int[] {
            0x00000000,
            0x4004080C,
            0x800C1014,
            0xC0101418
        }, 2, 2, 1);

        assertArrayEquals(new int[] {0x60080B0E}, mipLevels[1]);
    }

    @Test
    public void discreteBlendSelectsDominantTypeWithFirstTiePriority() {
        assertEquals(2, DiscreteBlendFunction.selectTargetType(1, 2, 2, 3));
        assertEquals(1, DiscreteBlendFunction.selectTargetType(1, 2, 3, 4));
        assertEquals(3, DiscreteBlendFunction.selectTargetType(1, 2, 3, 3));
    }
}
