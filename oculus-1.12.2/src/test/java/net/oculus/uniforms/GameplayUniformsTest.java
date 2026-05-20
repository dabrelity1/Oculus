package net.oculus.uniforms;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import net.oculus.colorspace.ColorSpace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.oculus.gl.state.ValueUpdateNotifier;
import net.oculus.shaderpack.ConstDirectiveParser;
import net.oculus.shaderpack.DispatchingDirectiveHolder;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.uniforms.transforms.ExponentialSmoothing;
import net.oculus.uniforms.transforms.SmoothedFloat;
import net.oculus.uniforms.transforms.SmoothedVec2f;
import org.junit.Test;

public class GameplayUniformsTest {
    @Test
    public void terrainScaleUniformsExposeRelictiumVertexTypeScales() {
        assertEquals(1.0F, GameplayUniforms.getTerrainModelScale(), 0.0F);
        assertEquals(1.0F, GameplayUniforms.getTerrainTextureScale(), 0.0F);
        assertArrayEquals(new float[] {1.0F, 1.0F, 1.0F},
            GameplayUniforms.getTerrainModelScaleVec3(), 0.0F);
        assertArrayEquals(new float[] {1.0F, 1.0F},
            GameplayUniforms.getTerrainTextureScaleVec2(), 0.0F);
    }

    @Test
    public void currentColorSpaceMatchesReferenceDefault() {
        assertEquals(ColorSpace.SRGB.ordinal(), GameplayUniforms.getCurrentColorSpace());
    }

    @Test
    public void irisExclusivePlayerStatsExposeOnlySurvivalLikeModes() {
        assertTrue(GameplayUniforms.shouldExposeSurvivalPlayerStats(true, GameType.SURVIVAL));
        assertTrue(GameplayUniforms.shouldExposeSurvivalPlayerStats(true, GameType.ADVENTURE));
        assertFalse(GameplayUniforms.shouldExposeSurvivalPlayerStats(true, GameType.CREATIVE));
        assertFalse(GameplayUniforms.shouldExposeSurvivalPlayerStats(true, GameType.SPECTATOR));
        assertFalse(GameplayUniforms.shouldExposeSurvivalPlayerStats(true, GameType.NOT_SET));
        assertFalse(GameplayUniforms.shouldExposeSurvivalPlayerStats(true, null));
        assertFalse(GameplayUniforms.shouldExposeSurvivalPlayerStats(false, GameType.SURVIVAL));
    }

    @Test
    public void irisExclusiveMaxPlayerHungerIsReferenceConstant() {
        assertEquals(20.0F, GameplayUniforms.getMaxPlayerHunger(), 0.0F);
    }

    @Test
    public void entityColorUniformCanMirrorAndClear1_12BrightnessOverlayColor() {
        try {
            GameplayUniforms.setEntityColor(1.0F, 0.25F, 0.5F, 0.75F);
            assertArrayEquals(new float[] {1.0F, 0.25F, 0.5F, 0.75F},
                GameplayUniforms.getEntityColor(), 0.0F);

            GameplayUniforms.clearEntityColor();
            assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 0.0F},
                GameplayUniforms.getEntityColor(), 0.0F);
        } finally {
            GameplayUniforms.clearEntityColor();
        }
    }

    @Test
    public void entityColorNotifierPublishesOnlyWhenCapturedColorChanges() {
        ValueUpdateNotifier notifier = GameplayUniforms.getEntityColorNotifier();
        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;

        try {
            GameplayUniforms.clearEntityColor();
            notifier.setListener(listener);

            GameplayUniforms.setEntityColor(1.0F, 0.25F, 0.5F, 0.75F);
            GameplayUniforms.setEntityColor(1.0F, 0.25F, 0.5F, 0.75F);
            GameplayUniforms.clearEntityColor();

            assertEquals(2, calls.get());
        } finally {
            notifier.removeListener(listener);
            GameplayUniforms.clearEntityColor();
        }
    }

    @Test
    public void nightVisionUsesVanillaFadeCurve() {
        assertEquals(1.0F, GameplayUniforms.getNightVisionStrength(201, 0.0F), 0.0F);
        assertEquals(0.7F, GameplayUniforms.getNightVisionStrength(0, 0.0F), 1.0E-6F);
        assertEquals(0.9853152F, GameplayUniforms.getNightVisionStrength(2, 0.0F), 1.0E-6F);
        assertEquals(0.7F, GameplayUniforms.getNightVisionStrength(5, 0.0F), 1.0E-6F);
    }

    @Test
    public void irisExclusiveSpectatorUniformReadsCurrentGameType() {
        assertTrue(GameplayUniforms.isSpectatorGameType(GameType.SPECTATOR));
        assertFalse(GameplayUniforms.isSpectatorGameType(GameType.SURVIVAL));
        assertFalse(GameplayUniforms.isSpectatorGameType(GameType.ADVENTURE));
        assertFalse(GameplayUniforms.isSpectatorGameType(GameType.CREATIVE));
        assertFalse(GameplayUniforms.isSpectatorGameType(GameType.NOT_SET));
        assertFalse(GameplayUniforms.isSpectatorGameType(null));
    }

    @Test
    public void cameraVectorUniformsClearStaleValuesWhenCameraIsUnavailable() {
        float[] eyePosition = GameplayUniforms.getEyePosition();
        eyePosition[0] = 1.0F;
        eyePosition[1] = 2.0F;
        eyePosition[2] = 3.0F;

        float[] lookVector = GameplayUniforms.getPlayerLookVector();
        lookVector[0] = 4.0F;
        lookVector[1] = 5.0F;
        lookVector[2] = 6.0F;

        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F}, GameplayUniforms.getEyePosition(), 0.0F);
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F}, GameplayUniforms.getPlayerLookVector(), 0.0F);
    }

    @Test
    public void eyeBrightnessUsesCurrentEyeBlockCoordinates() {
        BlockPos pos = GameplayUniforms.getEyeBrightnessBlockPos(10.75D, 64.2D, -3.4D, 1.62F);

        assertEquals(10, pos.getX());
        assertEquals(65, pos.getY());
        assertEquals(-4, pos.getZ());
    }

    @Test
    public void textureSizeUniformsUseMinecraftActiveTextureStateBoundary() throws Exception {
        String source = readSource("src/main/java/net/oculus/uniforms/GameplayUniforms.java");
        String body = methodBody(source, "private static void readTextureUnitZeroSize");

        assertTrue(body.contains("OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);"));
        assertTrue(body.contains("OpenGlHelper.setActiveTexture(previousActiveTexture);"));
        assertFalse(body.contains("GL13.glActiveTexture("));
    }

    @Test
    public void packDirectiveHalfLivesFollowLocal1_16_5DrynessAssignment() throws Exception {
        PackDirectives directives = new PackDirectives(ShaderProperties.empty());
        DispatchingDirectiveHolder holder = new DispatchingDirectiveHolder();
        directives.acceptDirectivesFrom(holder);

        for (ConstDirectiveParser.ConstDirective directive : ConstDirectiveParser.findDirectives(
            "const float wetnessHalflife = 2.0;\n"
                + "const float drynessHalflife = 4.0;\n"
                + "const float eyeBrightnessHalflife = 8.0;\n")) {
            holder.processDirective(directive);
        }

        assertEquals(4.0F, directives.getWetnessHalfLife(), 0.0F);
        assertEquals(200.0F, directives.getDrynessHalfLife(), 0.0F);

        try {
            GameplayUniforms.configure(directives);

            SmoothedFloat wetness = staticField(GameplayUniforms.class, "WETNESS", SmoothedFloat.class);
            assertEquals(decayFromDeciseconds(4.0F), floatField(wetness, "decayConstantUp"), 0.0F);
            assertEquals(decayFromDeciseconds(200.0F), floatField(wetness, "decayConstantDown"), 0.0F);

            SmoothedVec2f eyeBrightness = staticField(GameplayUniforms.class, "EYE_BRIGHTNESS_SMOOTH",
                SmoothedVec2f.class);
            SmoothedFloat eyeX = instanceField(eyeBrightness, "x", SmoothedFloat.class);
            SmoothedFloat eyeY = instanceField(eyeBrightness, "y", SmoothedFloat.class);
            assertEquals(decayFromDeciseconds(8.0F), floatField(eyeX, "decayConstantUp"), 0.0F);
            assertEquals(decayFromDeciseconds(8.0F), floatField(eyeX, "decayConstantDown"), 0.0F);
            assertEquals(decayFromDeciseconds(8.0F), floatField(eyeY, "decayConstantUp"), 0.0F);
            assertEquals(decayFromDeciseconds(8.0F), floatField(eyeY, "decayConstantDown"), 0.0F);
        } finally {
            GameplayUniforms.configure(null);
        }
    }

    private static float decayFromDeciseconds(float halfLife) {
        return ExponentialSmoothing.decayFromHalfLifeSeconds(halfLife * 0.1F);
    }

    private static float floatField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(target);
    }

    private static <T> T staticField(Class<?> owner, String name, Class<T> type) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(null));
    }

    private static <T> T instanceField(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static String readSource(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        assertTrue("Missing method signature " + signature, signatureIndex >= 0);
        int bodyStart = source.indexOf('{', signatureIndex);
        assertTrue("Missing method body for " + signature, bodyStart >= 0);

        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, index);
                }
            }
        }

        throw new AssertionError("Unterminated method body for " + signature);
    }
}
