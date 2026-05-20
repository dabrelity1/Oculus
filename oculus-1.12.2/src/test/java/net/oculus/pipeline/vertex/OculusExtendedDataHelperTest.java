package net.oculus.pipeline.vertex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class OculusExtendedDataHelperTest {
    @Test
    public void renderTypeConstantsMatchIrisReferenceContract() throws Exception {
        String active = read("src/main/java/net/oculus/pipeline/vertex/OculusExtendedDataHelper.java");
        String reference = read("../Oculus-1.16.5/src/main/java/net/coderbot/iris/vertices/ExtendedDataHelper.java");

        assertTrue(reference.contains("public static final short BLOCK_RENDER_TYPE = -1;"));
        assertTrue(reference.contains("public static final short FLUID_RENDER_TYPE = 1;"));
        assertEquals(-1, OculusExtendedDataHelper.BLOCK_RENDER_TYPE);
        assertEquals(1, OculusExtendedDataHelper.FLUID_RENDER_TYPE);
        assertTrue(active.contains("public static final short BLOCK_RENDER_TYPE = -1;"));
        assertTrue(active.contains("public static final short FLUID_RENDER_TYPE = 1;"));
    }

    @Test
    public void packMidBlockUsesSignedByteStylePackingLikeReference() throws Exception {
        String active = read("src/main/java/net/oculus/pipeline/vertex/OculusExtendedDataHelper.java");
        String reference = read("../Oculus-1.16.5/src/main/java/net/coderbot/iris/vertices/ExtendedDataHelper.java");

        assertTrue(reference.contains("((int) (x * 64) & 0xFF)"));
        assertTrue(reference.contains("(((int) (y * 64) & 0xFF) << 8)"));
        assertTrue(reference.contains("(((int) (z * 64) & 0xFF) << 16)"));
        assertTrue(active.contains("((int) (x * 64) & 0xFF)"));
        assertTrue(active.contains("(((int) (y * 64) & 0xFF) << 8)"));
        assertTrue(active.contains("(((int) (z * 64) & 0xFF) << 16)"));

        int expected = (32 & 0xFF) | ((-32 & 0xFF) << 8) | ((64 & 0xFF) << 16);

        assertEquals(expected, OculusExtendedDataHelper.packMidBlock(0.5F, -0.5F, 1.0F));
    }

    @Test
    public void computeMidBlockUsesChunkLocalBlockCenterMinusVertexPosition() throws Exception {
        String active = read("src/main/java/net/oculus/pipeline/vertex/OculusExtendedDataHelper.java");
        String reference = read("../Oculus-1.16.5/src/main/java/net/coderbot/iris/vertices/ExtendedDataHelper.java");

        assertTrue(reference.contains("localPosX + 0.5f - x"));
        assertTrue(reference.contains("localPosY + 0.5f - y"));
        assertTrue(reference.contains("localPosZ + 0.5f - z"));
        assertTrue(active.contains("localPosX + 0.5f - x"));
        assertTrue(active.contains("localPosY + 0.5f - y"));
        assertTrue(active.contains("localPosZ + 0.5f - z"));

        int expected = OculusExtendedDataHelper.packMidBlock(0.25F, 0.0F, -0.25F);

        assertEquals(expected, OculusExtendedDataHelper.computeMidBlock(3.25F, 4.5F, 5.75F, 3, 4, 5));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
