package net.oculus.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class OculusClientEventsSourceTest {
    @Test
    public void reloadKeyUsesPublicReloadPathEvenWhenActivePackIsInternal() throws Exception {
        String source = readSource("src/main/java/net/oculus/client/OculusClientEvents.java");
        String reloadBody = methodBody(source, "private void handleReload(Minecraft minecraft)");

        int reloadIndex = reloadBody.indexOf("boolean reloaded = ShaderPackReloader.reload();");
        int configIndex = reloadBody.indexOf("OculusConfig config = Oculus.getConfig();");

        assertTrue(reloadIndex >= 0);
        assertTrue(configIndex >= 0);
        assertTrue("reload key should refresh config before message decisions", reloadIndex < configIndex);
        assertFalse(reloadBody.contains("PipelineManager.INSTANCE.getActivePack()"));
        assertFalse(reloadBody.contains("ShaderPackReloader.applyConfiguredShaderPack()"));
    }

    @Test
    public void runtimeValidationRunsAfterStartupConfigAndBeforeInputReloads() throws Exception {
        String source = readSource("src/main/java/net/oculus/client/OculusClientEvents.java");
        String tickBody = methodBody(source, "public void onClientTick(TickEvent.ClientTickEvent event)");

        int minecraftLookup = tickBody.indexOf("Minecraft minecraft = Minecraft.getMinecraft();");
        int startupApply = tickBody.indexOf("applyStartupConfig(minecraft);");
        int runtimeValidation = tickBody.indexOf("runtimeValidation.onClientTick(minecraft);");
        int reload = tickBody.indexOf("handleReload(minecraft);");
        int toggle = tickBody.indexOf("handleToggle(minecraft);");
        int gui = tickBody.indexOf("handleGui(minecraft);");

        assertTrue(source.contains("private final OculusRuntimeValidation runtimeValidation = new OculusRuntimeValidation();"));
        assertTrue(minecraftLookup >= 0);
        assertTrue(startupApply > minecraftLookup);
        assertTrue(runtimeValidation > startupApply);
        assertTrue(reload > runtimeValidation);
        assertTrue(toggle > reload);
        assertTrue(gui > toggle);
    }

    @Test
    public void toggleKeySavesConfigBeforeSharedReloadLikeReference() throws Exception {
        String source = readSource("src/main/java/net/oculus/client/OculusClientEvents.java");
        String toggleBody = methodBody(source, "private void handleToggle(Minecraft minecraft)");

        int previousIndex = toggleBody.indexOf("boolean previousValue = config.areShadersEnabled();");
        int setIndex = toggleBody.indexOf("config.setShadersEnabled(newValue);");
        int saveIndex = toggleBody.indexOf("if (!saveConfig(config))");
        int restoreIndex = toggleBody.indexOf("config.setShadersEnabled(previousValue);", saveIndex);
        int reloadIndex = toggleBody.indexOf("boolean reloaded = ShaderPackReloader.reload();");
        int messageIndex = toggleBody.indexOf("String message = newValue && reloaded");

        assertTrue(previousIndex >= 0);
        assertTrue(setIndex >= 0);
        assertTrue(saveIndex >= 0);
        assertTrue(restoreIndex >= 0);
        assertTrue(reloadIndex >= 0);
        assertTrue(messageIndex >= 0);
        assertTrue(previousIndex < setIndex);
        assertTrue(setIndex < saveIndex);
        assertTrue(saveIndex < restoreIndex);
        assertTrue(restoreIndex < reloadIndex);
        assertTrue(reloadIndex < messageIndex);
        assertFalse(toggleBody.contains("ShaderPackReloader.applyConfiguredShaderPack()"));
    }

    @Test
    public void toggleKeyDoesNotReloadStaleConfigWhenSaveFails() throws Exception {
        String source = readSource("src/main/java/net/oculus/client/OculusClientEvents.java");
        String toggleBody = methodBody(source, "private void handleToggle(Minecraft minecraft)");
        String saveBody = methodBody(source, "private boolean saveConfig(OculusConfig config)");

        int saveFailureIndex = toggleBody.indexOf("if (!saveConfig(config))");
        int continueIndex = toggleBody.indexOf("continue;", saveFailureIndex);
        int reloadIndex = toggleBody.indexOf("boolean reloaded = ShaderPackReloader.reload();");

        assertTrue(saveFailureIndex >= 0);
        assertTrue(continueIndex > saveFailureIndex);
        assertTrue(reloadIndex > continueIndex);
        assertTrue(saveBody.contains("return true;"));
        assertTrue(saveBody.contains("return false;"));
    }

    private static String readSource(String path) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        return new String(bytes, StandardCharsets.UTF_8);
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
