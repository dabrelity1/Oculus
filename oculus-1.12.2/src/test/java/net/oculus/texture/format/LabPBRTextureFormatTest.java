package net.oculus.texture.format;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import net.oculus.texture.pbr.PBRType;
import org.junit.Test;

public class LabPBRTextureFormatTest {
    @Test
    public void labPbrUsesCustomDiscreteMipmapGeneratorForSpecularOnly() {
        LabPBRTextureFormat format = new LabPBRTextureFormat("lab-pbr", "1.3.0");

        assertSame(LabPBRTextureFormat.SPECULAR_MIPMAP_GENERATOR, format.getMipmapGenerator(PBRType.SPECULAR));
        assertNull(format.getMipmapGenerator(PBRType.NORMAL));
    }
}
