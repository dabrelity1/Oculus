package net.oculus.uniforms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import org.junit.Test;

public class CompatibilityUniformsTest {
    @Test
    public void compatibilityUniformsResolveMinecraftAtUseTime() throws Exception {
        String source = read("src/main/java/net/oculus/uniforms/CompatibilityUniforms.java");

        assertFalse(source.contains("private static final Minecraft"));
        assertTrue(source.contains("private static Minecraft getMinecraft()"));
        assertTrue(source.contains("private static World getWorld() {\n"
            + "        Minecraft minecraft = getMinecraft();"));
        assertTrue(source.contains("private static Entity getCamera() {\n"
            + "        Minecraft minecraft = getMinecraft();"));
        assertTrue(source.contains("private static EntityPlayer getPlayer() {\n"
            + "        Minecraft minecraft = getMinecraft();"));
    }

    @Test
    public void netherWastesFallbackMapsToLegacyHellBiome() {
        assertTrue(CompatibilityUniforms.isNetherWastesBiomeName(new ResourceLocation("minecraft", "hell")));
        assertFalse(CompatibilityUniforms.isNetherWastesBiomeName(new ResourceLocation("minecraft", "plains")));
        assertFalse(CompatibilityUniforms.isNetherWastesBiomeName(new ResourceLocation("modded", "hell")));
        assertFalse(CompatibilityUniforms.isNetherWastesBiomeName(null));
    }

    @Test
    public void eyeInCaveMatchesHardcodedReferenceFormula() {
        assertEquals(1.0f, CompatibilityUniforms.computeEyeInCaveValue(4.999, 0f), 0.0001f);
        assertEquals(0.5f, CompatibilityUniforms.computeEyeInCaveValue(4.0, 120f), 0.0001f);
        assertEquals(0.0f, CompatibilityUniforms.computeEyeInCaveValue(5.0, 120f), 0.0001f);
        assertEquals(0.0f, CompatibilityUniforms.computeEyeInCaveValue(7.0, 240f), 0.0001f);
    }

    @Test
    public void velocityUsesReferenceFloatDeltaPrecision() {
        assertEquals(13.0f, CompatibilityUniforms.computeVelocity(12.0, 4.0, 8.0, 0.0, 0.0, 5.0), 0.0001f);
        assertTrue(Float.isInfinite(CompatibilityUniforms.computeVelocity(1.0e20, 0.0, 0.0, 0.0, 0.0, 0.0)));
    }

    @Test
    public void movingFlagDeltaSumUsesReferenceFloatDeltaPrecision() {
        assertEquals(0.75f, CompatibilityUniforms.computeMovementDeltaSum(0.25, -0.25, 0.25, 0.0, 0.0, 0.0), 0.0001f);
        assertEquals(1.0f, CompatibilityUniforms.computeMovementDeltaSum(0.99999999, 0.0, 0.0, 0.0, 0.0, 0.0), 0.0001f);
    }

    @Test
    public void movingFlagUsesReferenceThresholds() {
        assertEquals(0.0f, CompatibilityUniforms.computeMovingFlag(0.0, 0.0, 0.0, 0.0, 0.0, 0.0), 0.0001f);
        assertEquals(1.0f, CompatibilityUniforms.computeMovingFlag(0.5, 0.0, 0.0, 0.0, 0.0, 0.0), 0.0001f);
        assertEquals(0.0f, CompatibilityUniforms.computeMovingFlag(1.0, 0.0, 0.0, 0.0, 0.0, 0.0), 0.0001f);
        assertEquals(0.0f, CompatibilityUniforms.computeMovingFlag(0.99999999, 0.0, 0.0, 0.0, 0.0, 0.0), 0.0001f);
    }

    @Test
    public void effectStrengthMatchesHardcodedReferenceFormula() {
        assertEquals(0.0f, CompatibilityUniforms.computeEffectStrength(0.0f), 0.0001f);
        assertEquals((float) (1.0 - Math.exp(-256.0f * 0.003906f)),
            CompatibilityUniforms.computeEffectStrength(256.0f), 0.0001f);
    }

    @Test
    public void legacyPrecipitationMapsBiomeFlagsToReferenceValues() {
        assertEquals(0.0f, CompatibilityUniforms.legacyPrecipitation(false, false), 0.0001f);
        assertEquals(1.0f, CompatibilityUniforms.legacyPrecipitation(false, true), 0.0001f);
        assertEquals(2.0f, CompatibilityUniforms.legacyPrecipitation(true, false), 0.0001f);
        assertEquals(2.0f, CompatibilityUniforms.legacyPrecipitation(true, true), 0.0001f);
    }

    @Test
    public void precipitationRainUsesReferenceCameraYThreshold() {
        assertEquals(1.0f, CompatibilityUniforms.computeIsPrecipitationRain(1.0f, 95.999f), 0.0001f);
        assertEquals(0.0f, CompatibilityUniforms.computeIsPrecipitationRain(1.0f, 96.0f), 0.0001f);
        assertEquals(0.0f, CompatibilityUniforms.computeIsPrecipitationRain(0.0f, 95.0f), 0.0001f);
        assertEquals(0.0f, CompatibilityUniforms.computeIsPrecipitationRain(2.0f, 95.0f), 0.0001f);
    }

    @Test
    public void precipitationRainCameraYUsesCapturedCameraPosition() {
        double[] cameraPosition = CapturedRenderingState.INSTANCE.getCameraPosition();
        double previousY = cameraPosition[1];
        try {
            cameraPosition[1] = 95.5;
            assertEquals(95.5f, CompatibilityUniforms.getCameraY(), 0.0001f);

            cameraPosition[1] = 120.25;
            assertEquals(120.25f, CompatibilityUniforms.getCameraY(), 0.0001f);
        } finally {
            cameraPosition[1] = previousY;
        }
    }

    @Test
    public void hardcodedWorldDayTimeUsesReferenceFixedTimesForVanillaNetherAndEnd() {
        assertEquals(18000, CompatibilityUniforms.getWorldDayTime(new TestProvider(DimensionType.NETHER), 1234L));
        assertEquals(6000, CompatibilityUniforms.getWorldDayTime(new TestProvider(DimensionType.THE_END), 1234L));
    }

    @Test
    public void hardcodedWorldDayTimeKeepsRawTimeForOverworldAndUnreadableProviders() {
        assertEquals(3456, CompatibilityUniforms.getWorldDayTime(new TestProvider(DimensionType.OVERWORLD), 27456L));
        assertEquals(3456, CompatibilityUniforms.getWorldDayTime(null, 27456L));
        assertEquals(3456, CompatibilityUniforms.getWorldDayTime(new UnreadableDimensionTypeProvider(), 27456L));
    }

    private static final class TestProvider extends WorldProvider {
        private final DimensionType dimensionType;

        private TestProvider(DimensionType dimensionType) {
            this.dimensionType = dimensionType;
        }

        @Override
        public DimensionType getDimensionType() {
            return dimensionType;
        }
    }

    private static final class UnreadableDimensionTypeProvider extends WorldProvider {
        @Override
        public DimensionType getDimensionType() {
            throw new IllegalStateException("dimension type unavailable");
        }
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
