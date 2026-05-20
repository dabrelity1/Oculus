package net.oculus.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OculusRuntimeValidationTest {
    @Before
    public void clearProperties() {
        clearValidationProperties();
    }

    @After
    public void restoreProperties() {
        clearValidationProperties();
    }

    @Test
    public void pbrValidationIsDisabledByDefault() {
        assertFalse(OculusRuntimeValidation.isPbrValidationEnabled());
        assertFalse(OculusRuntimeValidation.isRenderTargetDumpEnabled());
    }

    @Test
    public void blankValidationPropertiesDoNotEnableRuntimeTelemetry() {
        System.setProperty(OculusRuntimeValidation.AUTO_JOIN_WORLD_PROPERTY, "   ");
        System.setProperty(OculusRuntimeValidation.PBR_TEXTURES_PROPERTY, "\t");
        System.setProperty(OculusRuntimeValidation.DUMP_RENDER_TARGETS_PROPERTY, "\n");

        assertFalse(OculusRuntimeValidation.isEnabled());
        assertFalse(OculusRuntimeValidation.isPbrValidationEnabled());
        assertFalse(OculusRuntimeValidation.isRenderTargetDumpEnabled());
    }

    @Test
    public void pbrValidationCanBeEnabledWithoutAutoJoin() {
        System.setProperty(OculusRuntimeValidation.PBR_TEXTURES_PROPERTY, "true");

        assertTrue(OculusRuntimeValidation.isPbrValidationEnabled());
    }

    @Test
    public void trimmedPbrValidationPropertyEnablesTelemetryWithoutAutoJoin() {
        System.setProperty(OculusRuntimeValidation.PBR_TEXTURES_PROPERTY, " true ");

        assertFalse(OculusRuntimeValidation.isEnabled());
        assertTrue(OculusRuntimeValidation.isPbrValidationEnabled());
    }

    @Test
    public void autoJoinValidationAlsoEnablesPbrTelemetry() {
        System.setProperty(OculusRuntimeValidation.AUTO_JOIN_WORLD_PROPERTY, "New World");

        assertTrue(OculusRuntimeValidation.isPbrValidationEnabled());
    }

    @Test
    public void renderTargetDumpIsEnabledOnlyByExplicitProperty() {
        System.setProperty(OculusRuntimeValidation.AUTO_JOIN_WORLD_PROPERTY, "New World");

        assertFalse(OculusRuntimeValidation.isRenderTargetDumpEnabled());

        System.setProperty(OculusRuntimeValidation.DUMP_RENDER_TARGETS_PROPERTY, "true");

        assertTrue(OculusRuntimeValidation.isRenderTargetDumpEnabled());
    }

    @Test
    public void runClientForwardsRuntimeValidationProperties() throws IOException {
        String buildScript = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);

        assertTrue(buildScript.contains("tasks.getByName(\"runClient\")"));
        assertTrue(buildScript.contains("\"" + OculusRuntimeValidation.AUTO_JOIN_WORLD_PROPERTY + "\""));
        assertTrue(buildScript.contains("\"" + OculusRuntimeValidation.EXIT_AFTER_WORLD_TICKS_PROPERTY + "\""));
        assertTrue(buildScript.contains("\"" + OculusRuntimeValidation.PBR_TEXTURES_PROPERTY + "\""));
        assertTrue(buildScript.contains("\"" + OculusRuntimeValidation.SCREENSHOT_WORLD_TICK_PROPERTY + "\""));
        assertTrue(buildScript.contains("\"" + OculusRuntimeValidation.INVENTORY_SCREENSHOT_WORLD_TICK_PROPERTY + "\""));
        assertTrue(buildScript.contains("\"" + OculusRuntimeValidation.DUMP_RENDER_TARGETS_PROPERTY + "\""));
        assertTrue(buildScript.contains("\"" + OculusRuntimeValidation.DUMP_RENDER_TARGETS_WORLD_TICK_PROPERTY + "\""));
        assertTrue(buildScript.contains("\"" + OculusRuntimeValidation.THIRD_PERSON_VIEW_PROPERTY + "\""));
        assertTrue(buildScript.contains("\"" + OculusRuntimeValidation.WORLD_TIME_PROPERTY + "\""));
        assertTrue(buildScript.contains("\"" + OculusRuntimeValidation.SHADER_PACK_PROPERTY + "\""));
        assertTrue(buildScript.contains("System.getProperty(propertyName)"));
        assertTrue(buildScript.contains("jvmArgs \"-D${propertyName}=${propertyValue}\""));
    }

    @Test
    public void renderDiagnosticsIncludeTextureFormatsAndShaderSunTerms() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/OculusRuntimeValidation.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("GL_TEXTURE_INTERNAL_FORMAT"));
        assertTrue(source.contains("GL_TEXTURE_RED_SIZE"));
        assertTrue(source.contains("GL_TEXTURE_RED_TYPE"));
        assertTrue(source.contains("logColorTextureFloatStats("));
        assertTrue(source.contains("negative={} overOne={}"));
        assertTrue(source.contains("computeComplementarySunVector("));
        assertTrue(source.contains("shaderUp=({}, {}, {})"));
        assertTrue(source.contains("SdotU={} sunVisibility={} shadowTime={}"));
        assertTrue(source.contains("CelestialUniforms.getSunPathRotation()"));
    }

    @Test
    public void worldScreenshotClosesLingeringScreenBeforeCapture() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/OculusRuntimeValidation.java")), StandardCharsets.UTF_8);

        int worldActive = source.indexOf("worldActive = true;");
        int close = source.indexOf("boolean closedScreen = closeScreenForWorldCaptureIfNeeded(minecraft, screenshotWorldTick);", worldActive);
        int closedGuard = source.indexOf("if (!closedScreen)", close);
        int screenshot = source.indexOf("queueScreenshotIfRequested(minecraft, screenshotWorldTick);", closedGuard);
        int helper = source.indexOf("private boolean closeScreenForWorldCaptureIfNeeded");
        int displayGui = source.indexOf("minecraft.displayGuiScreen(null);", helper);
        int focus = source.indexOf("minecraft.setIngameFocus();", displayGui);
        int returnsTrue = source.indexOf("return true;", focus);

        assertTrue(worldActive >= 0);
        assertTrue(close > worldActive);
        assertTrue(closedGuard > close);
        assertTrue(screenshot > closedGuard);
        assertTrue(helper >= 0);
        assertTrue(displayGui > helper);
        assertTrue(focus > displayGui);
        assertTrue(returnsTrue > focus);
        assertTrue(source.contains("public static void captureWorldScreenshotAfterRender(Minecraft minecraft)"));
        assertTrue(source.contains("minecraft.currentScreen != null"));
        assertTrue(source.contains("captured post-world-render screenshot"));
    }

    @Test
    public void inventoryScreenshotOpensGuiAndCapturesAfterGuiRender() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/OculusRuntimeValidation.java")), StandardCharsets.UTF_8);

        int worldActive = source.indexOf("worldActive = true;");
        int openInventory = source.indexOf("openInventoryForScreenshotIfRequested(minecraft, inventoryScreenshotWorldTick, screenshotWorldTick);",
            worldActive);
        int queueInventory = source.indexOf("queueInventoryScreenshotIfRequested(minecraft, inventoryScreenshotWorldTick, screenshotWorldTick);",
            openInventory);
        String openBody = methodBody(source, "private void openInventoryForScreenshotIfRequested");
        String queueBody = methodBody(source, "private void queueInventoryScreenshotIfRequested");

        assertTrue(source.contains("INVENTORY_SCREENSHOT_WORLD_TICK_PROPERTY"));
        assertTrue(source.contains("import net.minecraft.client.gui.inventory.GuiInventory;"));
        assertTrue(worldActive >= 0);
        assertTrue(openInventory > worldActive);
        assertTrue(queueInventory > openInventory);
        assertTrue(openBody.contains("new GuiInventory(minecraft.player)"));
        assertTrue(openBody.contains("screenshotWorldTick >= 0 && !screenshotCaptured"));
        assertTrue(queueBody.contains("isInventoryScreen(minecraft.currentScreen)"));
        assertTrue(queueBody.contains("inventoryScreenOpenTick >= 0 && worldTicks <= inventoryScreenOpenTick + 1"));
        assertTrue(source.contains("import net.minecraft.client.gui.inventory.GuiContainerCreative;"));
        assertTrue(source.contains("screen instanceof GuiInventory || screen instanceof GuiContainerCreative"));
        assertTrue(source.contains("public static void captureInventoryScreenshotAfterGuiRender(Minecraft minecraft)"));
        assertTrue(source.contains("private boolean shouldCaptureInventoryScreenshotAfterGuiRender(Minecraft minecraft)"));
        assertTrue(source.contains("captured inventory GUI screenshot"));
        assertTrue(source.contains("inventory GUI state"));
    }

    @Test
    public void thirdPersonValidationViewIsAppliedBeforeWorldScreenshot() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/OculusRuntimeValidation.java")), StandardCharsets.UTF_8);

        int worldActive = source.indexOf("worldActive = true;");
        int applyThirdPerson = source.indexOf("applyThirdPersonViewIfRequested(minecraft);", worldActive);
        int closeScreen = source.indexOf("closeScreenForWorldCaptureIfNeeded(minecraft, screenshotWorldTick);", applyThirdPerson);
        int screenshot = source.indexOf("queueScreenshotIfRequested(minecraft, screenshotWorldTick);", closeScreen);
        int helper = source.indexOf("private void applyThirdPersonViewIfRequested");
        int setter = source.indexOf("minecraft.gameSettings.thirdPersonView = thirdPersonView;", helper);
        int property = source.indexOf("THIRD_PERSON_VIEW_PROPERTY", source.indexOf("private int getThirdPersonView()"));

        assertTrue(worldActive >= 0);
        assertTrue(applyThirdPerson > worldActive);
        assertTrue(closeScreen > applyThirdPerson);
        assertTrue(screenshot > closeScreen);
        assertTrue(helper >= 0);
        assertTrue(setter > helper);
        assertTrue(property > helper);
    }

    @Test
    public void validationDisablesPauseOnLostFocusBeforeWorldScreenshot() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/OculusRuntimeValidation.java")), StandardCharsets.UTF_8);

        int worldActive = source.indexOf("worldActive = true;");
        int applyWorldTime = source.indexOf("applyWorldTimeIfRequested(minecraft);", worldActive);
        int disablePause = source.indexOf("disablePauseOnLostFocusForValidation(minecraft);", applyWorldTime);
        int applyThirdPerson = source.indexOf("applyThirdPersonViewIfRequested(minecraft);", disablePause);
        String helper = methodBody(source, "private void disablePauseOnLostFocusForValidation");

        assertTrue(worldActive >= 0);
        assertTrue(applyWorldTime > worldActive);
        assertTrue(disablePause > applyWorldTime);
        assertTrue(applyThirdPerson > disablePause);
        assertTrue(helper.contains("minecraft.gameSettings.pauseOnLostFocus = false;"));
        assertTrue(helper.contains("disabled pauseOnLostFocus"));
    }

    @Test
    public void worldTimeValidationOverrideIsAppliedBeforeWorldScreenshot() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/OculusRuntimeValidation.java")), StandardCharsets.UTF_8);

        int worldActive = source.indexOf("worldActive = true;");
        int applyWorldTime = source.indexOf("applyWorldTimeIfRequested(minecraft);", worldActive);
        int disablePause = source.indexOf("disablePauseOnLostFocusForValidation(minecraft);", applyWorldTime);
        int applyThirdPerson = source.indexOf("applyThirdPersonViewIfRequested(minecraft);", disablePause);
        int screenshot = source.indexOf("queueScreenshotIfRequested(minecraft, screenshotWorldTick);", applyThirdPerson);
        int helper = source.indexOf("private void applyWorldTimeIfRequested");
        int setter = source.indexOf("minecraft.world.setWorldTime(worldTime);", helper);
        int property = source.indexOf("WORLD_TIME_PROPERTY", source.indexOf("private int getWorldTimeOverride()"));

        assertTrue(worldActive >= 0);
        assertTrue(applyWorldTime > worldActive);
        assertTrue(disablePause > applyWorldTime);
        assertTrue(applyThirdPerson > disablePause);
        assertTrue(screenshot > applyThirdPerson);
        assertTrue(helper >= 0);
        assertTrue(setter > helper);
        assertTrue(property > helper);
    }

    @Test
    public void renderTargetDumpsWaitForActiveWorldWithoutGuiScreen() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/OculusRuntimeValidation.java")), StandardCharsets.UTF_8);
        String shouldDump = methodBody(source, "private static boolean shouldDumpRenderTargetsNow()");

        int minecraft = shouldDump.indexOf("Minecraft minecraft = Minecraft.getMinecraft();");
        int screenGuard = shouldDump.indexOf("minecraft.currentScreen != null", minecraft);
        int tickCheck = shouldDump.indexOf("return requestedWorldTick < 0 || (worldActive && currentWorldTicks >= requestedWorldTick);",
            screenGuard);

        assertTrue(minecraft >= 0);
        assertTrue(screenGuard > minecraft);
        assertTrue(tickCheck > screenGuard);
    }

    private void clearValidationProperties() {
        System.clearProperty(OculusRuntimeValidation.AUTO_JOIN_WORLD_PROPERTY);
        System.clearProperty(OculusRuntimeValidation.EXIT_AFTER_WORLD_TICKS_PROPERTY);
        System.clearProperty(OculusRuntimeValidation.PBR_TEXTURES_PROPERTY);
        System.clearProperty(OculusRuntimeValidation.SCREENSHOT_WORLD_TICK_PROPERTY);
        System.clearProperty(OculusRuntimeValidation.INVENTORY_SCREENSHOT_WORLD_TICK_PROPERTY);
        System.clearProperty(OculusRuntimeValidation.DUMP_RENDER_TARGETS_PROPERTY);
        System.clearProperty(OculusRuntimeValidation.DUMP_RENDER_TARGETS_WORLD_TICK_PROPERTY);
        System.clearProperty(OculusRuntimeValidation.THIRD_PERSON_VIEW_PROPERTY);
        System.clearProperty(OculusRuntimeValidation.WORLD_TIME_PROPERTY);
        System.clearProperty(OculusRuntimeValidation.SHADER_PACK_PROPERTY);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method " + signature);
        }

        int openBrace = source.indexOf('{', start);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }

        throw new AssertionError("Unable to read method body for " + signature);
    }
}
