package net.oculus.uniforms;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class SpecialEffectUniformsTest {
    @Test
    public void specialEffectUniformsResolveMinecraftAtUseTimeLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/uniforms/SpecialEffectUniforms.java");

        assertFalse(source.contains("private static final Minecraft"));
        assertTrue(source.contains("private static Minecraft getMinecraft()"));
        assertTrue(source.contains("Minecraft minecraft = getMinecraft();\n"
            + "        if (minecraft == null)"));
        assertTrue(source.contains("Entity camera = minecraft.getRenderViewEntity();"));
        assertTrue(source.contains("World world = minecraft.world;"));
    }

    @Test
    public void specialEffectUniformsReturnZeroVectorsWithoutLiveClientState() {
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F},
            SpecialEffectUniforms.getRelativeEyePosition(), 0.0F);
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 0.0F},
            SpecialEffectUniforms.getLightningBoltPosition(), 0.0F);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
