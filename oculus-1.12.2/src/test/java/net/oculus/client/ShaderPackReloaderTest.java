package net.oculus.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Properties;

import net.oculus.config.OculusConfig;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.pipeline.NamespacedId;
import net.oculus.pipeline.PipelineManager;
import net.oculus.shaderpack.ShaderPack;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ShaderPackReloaderTest {
    private static final String PACK_NAME = "ConfiguredPack";
    private static final String OTHER_PACK_NAME = "OtherConfiguredPack";
    private static final String DISABLE_GL_STRING_PROBES_PROPERTY = "oculus.disableGlStringProbes";
    private static String previousGlStringProbeSetting;
    private static String previousGlCapabilityProbeSetting;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void disableGlProbesForHeadlessReloaderTests() {
        previousGlStringProbeSetting = System.getProperty(DISABLE_GL_STRING_PROBES_PROPERTY);
        previousGlCapabilityProbeSetting = System.getProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY);
        System.setProperty(DISABLE_GL_STRING_PROBES_PROPERTY, "true");
        System.setProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, "true");
    }

    @AfterClass
    public static void restoreGlProbeSettings() {
        restoreProperty(DISABLE_GL_STRING_PROBES_PROPERTY, previousGlStringProbeSetting);
        restoreProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, previousGlCapabilityProbeSetting);
    }

    @Before
    public void resetBeforeTest() {
        resetPipelineToInternal();
    }

    @After
    public void resetAfterTest() {
        resetPipelineToInternal();
    }

    @Test
    public void appliesConfiguredShaderPackFromShaderpacksDirectory() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();
        writeMinimalShaderPack(shaderpacks, PACK_NAME);

        OculusConfig config = newConfig();
        config.setShadersEnabled(true);
        config.setSelectedPackName(PACK_NAME);

        assertTrue(ShaderPackReloader.applyConfiguredShaderPack(config, shaderpacks));

        ShaderPack activePack = PipelineManager.INSTANCE.getActivePack();
        assertFalse(activePack.isInternal());
        assertEquals(PACK_NAME, activePack.getName());
        assertTrue(activePack.getProgramSet(NamespacedId.overworld()).getGbuffersTerrain().isPresent());
    }

    @Test
    public void disabledConfigClearsActiveExternalPackWithoutClearingSelection() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();
        writeMinimalShaderPack(shaderpacks, PACK_NAME);

        OculusConfig config = newConfig();
        config.setShadersEnabled(true);
        config.setSelectedPackName(PACK_NAME);
        assertTrue(ShaderPackReloader.applyConfiguredShaderPack(config, shaderpacks));

        config.setShadersEnabled(false);

        assertFalse(ShaderPackReloader.applyConfiguredShaderPack(config, shaderpacks));
        assertTrue(PipelineManager.INSTANCE.getActivePack().isInternal());
        assertEquals(PACK_NAME, config.getSelectedPackName());
    }

    @Test
    public void enabledConfigWithoutSelectedPackUsesInternalPipeline() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();

        OculusConfig config = newConfig();
        config.setShadersEnabled(true);

        assertFalse(ShaderPackReloader.applyConfiguredShaderPack(config, shaderpacks));
        assertTrue(PipelineManager.INSTANCE.getActivePack().isInternal());
    }

    @Test
    public void enabledConfigWithInternalSentinelUsesInternalPipeline() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();

        OculusConfig config = newConfig();
        config.setShadersEnabled(true);
        config.setSelectedPackName("(internal)");

        assertFalse(ShaderPackReloader.applyConfiguredShaderPack(config, shaderpacks));
        assertTrue(PipelineManager.INSTANCE.getActivePack().isInternal());
    }

    @Test
    public void persistedInternalSentinelConfigUsesInternalPipelineAfterLoad() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();
        Path configPath = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(configPath,
            "selectedPackName=(internal)\nshadersEnabled=true\n".getBytes(StandardCharsets.UTF_8));

        OculusConfig config = new OculusConfig(configPath);
        config.load();

        assertFalse(ShaderPackReloader.applyConfiguredShaderPack(config, shaderpacks));
        assertTrue(PipelineManager.INSTANCE.getActivePack().isInternal());
    }

    @Test
    public void failedConfiguredPackLoadClearsStaleExternalPack() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();
        writeMinimalShaderPack(shaderpacks, PACK_NAME);

        OculusConfig config = newConfig();
        config.setShadersEnabled(true);
        config.setSelectedPackName(PACK_NAME);
        assertTrue(ShaderPackReloader.applyConfiguredShaderPack(config, shaderpacks));

        config.setSelectedPackName("MissingPack");

        assertFalse(ShaderPackReloader.applyConfiguredShaderPack(config, shaderpacks));
        assertTrue(PipelineManager.INSTANCE.getActivePack().isInternal());
    }

    @Test
    public void failedActivePackReloadClearsStaleExternalPackLikeReferenceReload() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();
        writeMinimalShaderPack(shaderpacks, PACK_NAME);

        OculusConfig config = newConfig();
        config.setShadersEnabled(true);
        config.setSelectedPackName(PACK_NAME);
        assertTrue(ShaderPackReloader.applyConfiguredShaderPack(config, shaderpacks));
        assertFalse(PipelineManager.INSTANCE.getActivePack().isInternal());

        Method reloadActive = ShaderPackReloader.class.getDeclaredMethod("reloadActiveShaderPack");
        reloadActive.setAccessible(true);

        assertFalse((Boolean) reloadActive.invoke(null));
        assertTrue(PipelineManager.INSTANCE.getActivePack().isInternal());
    }

    @Test
    public void reloadAppliesConfiguredPackWhenActivePackIsInternal() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();
        writeMinimalShaderPack(shaderpacks, PACK_NAME);

        OculusConfig config = newConfig();
        config.setShadersEnabled(true);
        config.setSelectedPackName(PACK_NAME);

        assertTrue(PipelineManager.INSTANCE.getActivePack().isInternal());
        assertTrue(ShaderPackReloader.reload(config, shaderpacks));

        ShaderPack activePack = PipelineManager.INSTANCE.getActivePack();
        assertFalse(activePack.isInternal());
        assertEquals(PACK_NAME, activePack.getName());
    }

    @Test
    public void reloadAppliesPersistedPackWhenShadersEnabledPropertyIsMissingLikeReference() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();
        writeMinimalShaderPack(shaderpacks, PACK_NAME);
        Path configPath = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(configPath,
            ("selectedPackName=" + PACK_NAME + "\n").getBytes(StandardCharsets.UTF_8));

        OculusConfig config = new OculusConfig(configPath);
        config.load();

        assertTrue(ShaderPackReloader.reload(config, shaderpacks));

        ShaderPack activePack = PipelineManager.INSTANCE.getActivePack();
        assertFalse(activePack.isInternal());
        assertEquals(PACK_NAME, activePack.getName());
    }

    @Test
    public void reloadAppliesPersistedPackFromReferenceConfigKeyNames() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();
        writeMinimalShaderPack(shaderpacks, PACK_NAME);
        Path configPath = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(configPath,
            ("shaderPack=" + PACK_NAME + "\nenableShaders=true\n").getBytes(StandardCharsets.UTF_8));

        OculusConfig config = new OculusConfig(configPath);
        config.load();

        assertTrue(ShaderPackReloader.reload(config, shaderpacks));

        ShaderPack activePack = PipelineManager.INSTANCE.getActivePack();
        assertFalse(activePack.isInternal());
        assertEquals(PACK_NAME, activePack.getName());
    }

    @Test
    public void reloadReplacesStaleActivePackWithConfiguredPack() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();
        writeMinimalShaderPack(shaderpacks, PACK_NAME);
        writeMinimalShaderPack(shaderpacks, OTHER_PACK_NAME);

        OculusConfig config = newConfig();
        config.setShadersEnabled(true);
        config.setSelectedPackName(PACK_NAME);
        assertTrue(ShaderPackReloader.applyConfiguredShaderPack(config, shaderpacks));
        assertEquals(PACK_NAME, PipelineManager.INSTANCE.getActivePack().getName());

        config.setSelectedPackName(OTHER_PACK_NAME);

        assertTrue(ShaderPackReloader.reload(config, shaderpacks));

        ShaderPack activePack = PipelineManager.INSTANCE.getActivePack();
        assertFalse(activePack.isInternal());
        assertEquals(OTHER_PACK_NAME, activePack.getName());
    }

    @Test
    public void runtimeValidationShaderPackOverrideCanSelectPackWithoutMutatingConfigSelection() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();
        writeMinimalShaderPack(shaderpacks, PACK_NAME);
        writeMinimalShaderPack(shaderpacks, OTHER_PACK_NAME);

        OculusConfig config = newConfig();
        config.setShadersEnabled(true);
        config.setSelectedPackName(PACK_NAME);

        String previousOverride = System.getProperty(OculusRuntimeValidation.SHADER_PACK_PROPERTY);
        System.setProperty(OculusRuntimeValidation.SHADER_PACK_PROPERTY, OTHER_PACK_NAME);
        try {
            assertTrue(ShaderPackReloader.applyConfiguredShaderPack(config, shaderpacks));

            ShaderPack activePack = PipelineManager.INSTANCE.getActivePack();
            assertFalse(activePack.isInternal());
            assertEquals(OTHER_PACK_NAME, activePack.getName());
            assertEquals(PACK_NAME, config.getSelectedPackName());
        } finally {
            restoreProperty(OculusRuntimeValidation.SHADER_PACK_PROPERTY, previousOverride);
        }
    }

    @Test
    public void publicReloadRegistersTextureFormatListenerBeforeConfigReload() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/ShaderPackReloader.java")), StandardCharsets.UTF_8);
        String reloadBody = methodBody(source, "public static boolean reload()");

        int textureFormatRegistration = reloadBody.indexOf("TextureFormatLoader.registerReloadListener();");
        int configRead = reloadBody.indexOf("OculusConfig config = Oculus.getConfig();");

        assertTrue(textureFormatRegistration >= 0);
        assertTrue(configRead >= 0);
        assertTrue(textureFormatRegistration < configRead);
    }

    @Test
    public void publicStartupApplyRefreshesConfigBeforeUsingConfiguredPack() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/ShaderPackReloader.java")), StandardCharsets.UTF_8);
        String applyBody = methodBody(source, "public static boolean applyConfiguredShaderPack()");

        int textureFormatRegistration = applyBody.indexOf("TextureFormatLoader.registerReloadListener();");
        int configRead = applyBody.indexOf("OculusConfig config = Oculus.getConfig();", textureFormatRegistration);
        int reloadGuard = applyBody.indexOf("if (config != null && !reloadConfig(config))", configRead);
        int failurePrepare = applyBody.indexOf("prepareLoadedWorldPipeline();", reloadGuard);
        int failureReturn = applyBody.indexOf("return false;", failurePrepare);
        int shaderpacksDirectory = applyBody.indexOf("Path shaderpacksDirectory = getShaderpacksDirectory();",
            failureReturn);
        int configuredApply = applyBody.indexOf(
            "boolean applied = applyConfiguredShaderPack(config, shaderpacksDirectory);", shaderpacksDirectory);
        int successPrepare = applyBody.indexOf("prepareLoadedWorldPipeline();", configuredApply);
        int successReturn = applyBody.indexOf("return applied;", successPrepare);

        assertTrue(textureFormatRegistration >= 0);
        assertTrue(configRead > textureFormatRegistration);
        assertTrue(reloadGuard > configRead);
        assertTrue(failurePrepare > reloadGuard);
        assertTrue(failureReturn > failurePrepare);
        assertTrue(shaderpacksDirectory > failureReturn);
        assertTrue(configuredApply > shaderpacksDirectory);
        assertTrue(successPrepare > configuredApply);
        assertTrue(successReturn > successPrepare);
    }

    @Test
    public void reloadConfigInitializesMissingConfigFileLikeReferenceReload() throws Exception {
        Path configPath = temporaryFolder.getRoot().toPath().resolve("missing").resolve("oculus.properties");
        OculusConfig config = new OculusConfig(configPath);

        Method reloadConfig = ShaderPackReloader.class.getDeclaredMethod("reloadConfig", OculusConfig.class);
        reloadConfig.setAccessible(true);
        reloadConfig.invoke(null, config);

        Properties properties = loadProperties(configPath);
        assertEquals("", properties.getProperty("shaderPack"));
        assertEquals("true", properties.getProperty("enableShaders"));
        assertEquals("false", properties.getProperty("enableDebugOptions"));
        assertEquals("false", properties.getProperty("disableUpdateMessage"));
        assertEquals("SRGB", properties.getProperty("colorSpace"));
        assertEquals("32", properties.getProperty("maxShadowRenderDistance"));
    }

    @Test
    public void reloadConfigFailureStopsBeforeUsingStaleConfig() throws Exception {
        Path fileParent = temporaryFolder.newFile("not-a-directory").toPath();
        Path configPath = fileParent.resolve("oculus.properties");
        OculusConfig config = new OculusConfig(configPath);

        Method reloadConfig = ShaderPackReloader.class.getDeclaredMethod("reloadConfig", OculusConfig.class);
        reloadConfig.setAccessible(true);

        assertFalse((Boolean) reloadConfig.invoke(null, config));
        assertFalse(Files.exists(configPath));
    }

    @Test
    public void reloadConfigUsesInitializeRatherThanBareLoadLikeReferenceReload() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/ShaderPackReloader.java")), StandardCharsets.UTF_8);
        String reloadConfigBody = methodBody(source, "private static boolean reloadConfig(OculusConfig config)");

        assertTrue(reloadConfigBody.contains("config.initialize();"));
        assertFalse(reloadConfigBody.contains("config.load();"));
    }

    @Test
    public void publicReloadStopsBeforeConfiguredPackReloadWhenConfigReloadFails() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/ShaderPackReloader.java")), StandardCharsets.UTF_8);
        String reloadBody = methodBody(source, "public static boolean reload()");

        int configReloadGuard = reloadBody.indexOf("if (!reloadConfig(config))");
        int returnFalse = reloadBody.indexOf("return false;", configReloadGuard);
        int configuredReload = reloadBody.indexOf("reloaded = reload(config, getShaderpacksDirectory());");

        assertTrue(configReloadGuard >= 0);
        assertTrue(returnFalse > configReloadGuard);
        assertTrue(configuredReload > returnFalse);
    }

    @Test
    public void reloadFailureHandlingTreatsCorruptZipErrorsAsPackLoadFailures() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/ShaderPackReloader.java")), StandardCharsets.UTF_8);
        String applyBody = methodBody(source,
            "static boolean applyConfiguredShaderPack(OculusConfig config, Path shaderpacksDirectory)");
        String activeReloadBody = methodBody(source, "private static boolean reloadActiveShaderPack()");

        assertTrue(applyBody.contains("catch (Exception | ZipError exception)"));
        assertTrue(activeReloadBody.contains("catch (Exception | ZipError exception)"));
        assertTrue(activeReloadBody.contains("disableShaders();"));
    }

    @Test
    public void publicReloadPreparesLoadedWorldPipelineAfterApplyingReload() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/client/ShaderPackReloader.java")), StandardCharsets.UTF_8);
        String reloadBody = methodBody(source, "public static boolean reload()");

        int reloadConfiguredIndex = reloadBody.indexOf("reloaded = reload(config, getShaderpacksDirectory());");
        int reloadActiveIndex = reloadBody.indexOf("reloaded = reloadActiveShaderPack();");
        int prepareIndex = reloadBody.indexOf("prepareLoadedWorldPipeline();");
        int returnIndex = reloadBody.indexOf("return reloaded;");

        assertTrue(reloadConfiguredIndex >= 0);
        assertTrue(reloadActiveIndex >= 0);
        assertTrue(prepareIndex >= 0);
        assertTrue(returnIndex >= 0);
        assertTrue(reloadConfiguredIndex < prepareIndex);
        assertTrue(reloadActiveIndex < prepareIndex);
        assertTrue(prepareIndex < returnIndex);
    }

    private OculusConfig newConfig() {
        return new OculusConfig(temporaryFolder.getRoot().toPath().resolve("oculus.properties"));
    }

    private void resetPipelineToInternal() {
        PipelineManager.INSTANCE.reloadShaderPack(ShaderPack.internal(), ShaderPack.createEmptyOptionValues());
    }

    private static void writeMinimalShaderPack(Path shaderpacks, String packName) throws Exception {
        Path shaders = shaderpacks.resolve(packName).resolve("shaders");
        Files.createDirectories(shaders);
        Files.write(shaders.resolve("shaders.properties"),
            Collections.singletonList("separateAo=true"),
            StandardCharsets.ISO_8859_1);
        Files.write(shaders.resolve("gbuffers_terrain.vsh"),
            Collections.singletonList("#version 120\nvoid main() { gl_Position = gl_Vertex; }\n"),
            StandardCharsets.UTF_8);
        Files.write(shaders.resolve("gbuffers_terrain.fsh"),
            Collections.singletonList("#version 120\nvoid main() { gl_FragData[0] = vec4(1.0); }\n"),
            StandardCharsets.UTF_8);
    }

    private static Properties loadProperties(Path path) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
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

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }
}
