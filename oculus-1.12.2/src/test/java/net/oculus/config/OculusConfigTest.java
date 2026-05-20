package net.oculus.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Properties;

import net.oculus.colorspace.ColorSpace;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class OculusConfigTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void newConfigDefaultsShadersEnabledLikeReference() {
        OculusConfig config = new OculusConfig(temporaryFolder.getRoot().toPath().resolve("oculus.properties"));

        assertTrue(config.areShadersEnabled());
    }

    @Test
    public void initializeCreatesDefaultConfigFileLikeReference() throws Exception {
        Path path = temporaryFolder.getRoot().toPath().resolve("missing").resolve("oculus.properties");

        OculusConfig config = new OculusConfig(path);
        config.initialize();

        Properties properties = loadProperties(path);
        assertEquals("", properties.getProperty("shaderPack"));
        assertEquals("true", properties.getProperty("enableShaders"));
        assertEquals("false", properties.getProperty("enableDebugOptions"));
        assertEquals("false", properties.getProperty("disableUpdateMessage"));
        assertEquals("SRGB", properties.getProperty("colorSpace"));
        assertEquals("32", properties.getProperty("maxShadowRenderDistance"));
    }

    @Test
    public void modInitializationUsesReferenceCreateMissingConfigBoundary() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/Oculus.java")), StandardCharsets.UTF_8);
        String initializeBody = methodBody(source, "private static void initializeConfig()");

        assertTrue(initializeBody.contains("config.initialize();"));
        assertFalse(initializeBody.contains("config.load();"));
    }

    @Test
    public void missingShadersEnabledPropertyLoadsAsEnabledLikeReference() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, "selectedPackName=(internal)\n".getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertTrue(config.areShadersEnabled());
    }

    @Test
    public void explicitFalseShadersEnabledPropertyStillDisablesShaders() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, "shadersEnabled=false\n".getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertFalse(config.areShadersEnabled());
    }

    @Test
    public void colorSpacePersistsWithReferenceName() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        OculusConfig config = new OculusConfig(path);
        config.setColorSpace(ColorSpace.ADOBE_RGB);
        config.save();

        OculusConfig reloaded = new OculusConfig(path);
        reloaded.load();

        assertEquals(ColorSpace.ADOBE_RGB, reloaded.getColorSpace());
    }

    @Test
    public void maxShadowRenderDistanceDefaultsAndPersistsLikeReference() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();

        OculusConfig config = new OculusConfig(path);
        config.load();
        assertEquals(32, config.getMaxShadowRenderDistance());

        config.setMaxShadowRenderDistance(12);
        config.save();

        OculusConfig reloaded = new OculusConfig(path);
        reloaded.load();

        assertEquals(12, reloaded.getMaxShadowRenderDistance());
        assertEquals("12", loadProperties(path).getProperty("maxShadowRenderDistance"));
    }

    @Test
    public void disableUpdateMessageDefaultsAndPersistsLikeReference() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();

        OculusConfig config = new OculusConfig(path);
        config.load();
        assertFalse(config.shouldDisableUpdateMessage());

        config.setDisableUpdateMessage(true);
        config.save();

        OculusConfig reloaded = new OculusConfig(path);
        reloaded.load();

        assertTrue(reloaded.shouldDisableUpdateMessage());
        assertEquals("true", loadProperties(path).getProperty("disableUpdateMessage"));
    }

    @Test
    public void disableUpdateMessageLoadsReferenceKey() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, "disableUpdateMessage=true\n".getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertTrue(config.shouldDisableUpdateMessage());
    }

    @Test
    public void invalidMaxShadowRenderDistanceFallsBackToReferenceDefault() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, (
            "maxShadowRenderDistance=far\n"
                + "colorSpace=ADOBE_RGB\n"
        ).getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertEquals(32, config.getMaxShadowRenderDistance());
        assertEquals(ColorSpace.SRGB, config.getColorSpace());
        assertEquals("32", loadProperties(path).getProperty("maxShadowRenderDistance"));
        assertEquals("SRGB", loadProperties(path).getProperty("colorSpace"));
    }

    @Test
    public void invalidColorSpaceFallsBackToSrgb() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, (
            "colorSpace=not-a-space\n"
                + "maxShadowRenderDistance=12\n"
        ).getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertEquals(ColorSpace.SRGB, config.getColorSpace());
        assertEquals(32, config.getMaxShadowRenderDistance());
        assertEquals("SRGB", loadProperties(path).getProperty("colorSpace"));
        assertEquals("32", loadProperties(path).getProperty("maxShadowRenderDistance"));
    }

    @Test
    public void recognizedColorSpaceAliasDoesNotForceConfigRewrite() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, "colorSpace=display-p3\n".getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertEquals(ColorSpace.DISPLAY_P3, config.getColorSpace());
        assertEquals("display-p3", loadProperties(path).getProperty("colorSpace"));
    }

    @Test
    public void dottedShaderPackNamesPreservePerPackOptionOverrides() throws Exception {
        String packName = "ComplementaryReimagined_r5.6.1.zip";
        Path path = temporaryFolder.newFile("oculus.properties").toPath();

        OculusConfig config = new OculusConfig(path);
        config.setSelectedPackName(packName);
        config.setOptionOverrides(packName, Collections.singletonMap("SHADOW_QUALITY", "2"));
        config.save();

        OculusConfig reloaded = new OculusConfig(path);
        reloaded.load();

        assertEquals("2", reloaded.getOptionOverrides(packName).get("SHADOW_QUALITY"));
    }

    @Test
    public void existingDottedPackOptionKeysLoadFromLastSeparator() throws Exception {
        String packName = "ComplementaryReimagined_r5.6.1.zip";
        String canonicalPackName = OculusConfig.canonicalizePackName(packName);
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, (
            "selectedPackName=" + packName + "\n"
                + "option." + canonicalPackName + ".SHADOW_QUALITY=3\n"
        ).getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertEquals("3", config.getOptionOverrides(packName).get("SHADOW_QUALITY"));
    }

    @Test
    public void legacyOptionKeysMigrateToSelectedDottedPackName() throws Exception {
        String packName = "ComplementaryReimagined_r5.6.1.zip";
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, (
            "selectedPackName=" + packName + "\n"
                + "option.SHADOW_QUALITY=4\n"
        ).getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertEquals("4", config.getOptionOverrides(packName).get("SHADOW_QUALITY"));
        assertTrue(config.getOptionOverrides("internal").isEmpty());
    }

    @Test
    public void internalSentinelSelectionLoadsAsNoExternalPackLikeReference() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, "selectedPackName=(internal)\nshadersEnabled=true\n".getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertNull(config.getSelectedPackName());
        assertTrue(config.areShadersEnabled());
    }

    @Test
    public void internalSentinelSelectionSetterClearsExternalPackLikeReference() {
        OculusConfig config = new OculusConfig(temporaryFolder.getRoot().toPath().resolve("oculus.properties"));

        config.setSelectedPackName("ConfiguredPack");
        assertEquals("ConfiguredPack", config.getSelectedPackName());

        config.setSelectedPackName("(internal)");

        assertNull(config.getSelectedPackName());
    }

    @Test
    public void legacyReferenceConfigKeysLoadWhenPortKeysAreMissing() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, (
            "shaderPack=ConfiguredPack\n"
                + "enableShaders=false\n"
                + "enableDebugOptions=true\n"
        ).getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertEquals("ConfiguredPack", config.getSelectedPackName());
        assertFalse(config.areShadersEnabled());
        assertTrue(config.isDebugEnabled());
    }

    @Test
    public void debugOptionsUseReferenceExactTrueSemantics() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, (
            "debugEnabled=TRUE\n"
                + "enableDebugOptions=true\n"
        ).getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertFalse(config.isDebugEnabled());

        Files.write(path, "enableDebugOptions=TRUE\n".getBytes("UTF-8"));

        config.load();

        assertFalse(config.isDebugEnabled());
    }

    @Test
    public void saveWritesReferenceConfigKeysAlongsidePortKeys() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();

        OculusConfig config = new OculusConfig(path);
        config.setSelectedPackName("ConfiguredPack");
        config.setShadersEnabled(false);
        config.setDebugEnabled(true);
        config.save();

        Properties properties = loadProperties(path);
        assertEquals("ConfiguredPack", properties.getProperty("selectedPackName"));
        assertEquals("ConfiguredPack", properties.getProperty("shaderPack"));
        assertEquals("false", properties.getProperty("shadersEnabled"));
        assertEquals("false", properties.getProperty("enableShaders"));
        assertEquals("true", properties.getProperty("debugEnabled"));
        assertEquals("true", properties.getProperty("enableDebugOptions"));
    }

    @Test
    public void saveWritesReferenceInternalPackAsEmptyShaderPack() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();

        OculusConfig config = new OculusConfig(path);
        config.save();

        Properties properties = loadProperties(path);
        assertNull(properties.getProperty("selectedPackName"));
        assertEquals("", properties.getProperty("shaderPack"));
    }

    @Test
    public void portConfigKeysTakePrecedenceOverLegacyReferenceKeys() throws Exception {
        Path path = temporaryFolder.newFile("oculus.properties").toPath();
        Files.write(path, (
            "selectedPackName=PortPack\n"
                + "shaderPack=ReferencePack\n"
                + "shadersEnabled=true\n"
                + "enableShaders=false\n"
                + "debugEnabled=false\n"
                + "enableDebugOptions=true\n"
        ).getBytes("UTF-8"));

        OculusConfig config = new OculusConfig(path);
        config.load();

        assertEquals("PortPack", config.getSelectedPackName());
        assertTrue(config.areShadersEnabled());
        assertFalse(config.isDebugEnabled());
    }

    @Test
    public void loadFailurePreservesPreviousInMemoryConfigState() throws Exception {
        Path path = temporaryFolder.newFolder("oculus.properties").toPath();
        OculusConfig config = new OculusConfig(path);
        String packName = "ComplementaryReimagined_r5.6.1.zip";

        config.setSelectedPackName(packName);
        config.setShadersEnabled(false);
        config.setDebugEnabled(true);
        config.setDisableUpdateMessage(true);
        config.setColorSpace(ColorSpace.DISPLAY_P3);
        config.setMaxShadowRenderDistance(18);
        config.setOptionOverrides(packName, Collections.singletonMap("SHADOW_QUALITY", "2"));

        boolean failed = false;
        try {
            config.load();
        } catch (Exception expected) {
            failed = true;
        }

        assertTrue(failed);
        assertEquals(packName, config.getSelectedPackName());
        assertFalse(config.areShadersEnabled());
        assertTrue(config.isDebugEnabled());
        assertTrue(config.shouldDisableUpdateMessage());
        assertEquals(ColorSpace.DISPLAY_P3, config.getColorSpace());
        assertEquals(18, config.getMaxShadowRenderDistance());
        assertEquals("2", config.getOptionOverrides(packName).get("SHADOW_QUALITY"));
    }

    @Test
    public void missingConfigSaveFailurePreservesPreviousInMemoryConfigState() throws Exception {
        Path fileParent = temporaryFolder.newFile("not-a-directory").toPath();
        Path path = fileParent.resolve("oculus.properties");
        OculusConfig config = new OculusConfig(path);
        String packName = "ComplementaryReimagined_r5.6.1.zip";

        config.setSelectedPackName(packName);
        config.setShadersEnabled(false);
        config.setDebugEnabled(true);
        config.setDisableUpdateMessage(true);
        config.setColorSpace(ColorSpace.DISPLAY_P3);
        config.setMaxShadowRenderDistance(18);
        config.setOptionOverrides(packName, Collections.singletonMap("SHADOW_QUALITY", "2"));

        boolean failed = false;
        try {
            config.initialize();
        } catch (Exception expected) {
            failed = true;
        }

        assertTrue(failed);
        assertEquals(packName, config.getSelectedPackName());
        assertFalse(config.areShadersEnabled());
        assertTrue(config.isDebugEnabled());
        assertTrue(config.shouldDisableUpdateMessage());
        assertEquals(ColorSpace.DISPLAY_P3, config.getColorSpace());
        assertEquals(18, config.getMaxShadowRenderDistance());
        assertEquals("2", config.getOptionOverrides(packName).get("SHADOW_QUALITY"));
    }

    private static Properties loadProperties(Path path) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
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
