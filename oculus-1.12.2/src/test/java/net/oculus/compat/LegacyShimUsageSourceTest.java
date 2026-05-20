package net.oculus.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

public class LegacyShimUsageSourceTest {
    private static final Path ACTIVE_SOURCE_ROOT = Paths.get("src/main/java/net/oculus");
    private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");
    private static final String PRIMARY_MIXIN_CONFIG = "oculus.mixins.json";

    @Test
    public void activeOculusSourceDoesNotUseLegacyCoderbotRuntimePackages() throws IOException {
        List<String> users = findJavaSourcesContaining(ACTIVE_SOURCE_ROOT, "net.coderbot.iris.");

        assertTrue("Active net.oculus source must not depend on historical net.coderbot.iris runtime classes: "
            + users, users.isEmpty());
    }

    @Test
    public void inactiveModernCompatibilityShellsStayOutOfActiveRuntime() throws IOException {
        List<String> users = findJavaSourcesContaining(ACTIVE_SOURCE_ROOT,
            "com.mojang.blaze3d.platform.Framebuffer",
            "com.mojang.math.Matrix4f",
            "com.mojang.blaze3d.vertex.VertexBuffer");

        assertTrue("Inactive modern compatibility shells leaked into active runtime source: " + users,
            users.isEmpty());
    }

    @Test
    public void modernVertexShimsAreOnlyUsedForOculusTerrainFormatDescription() throws IOException {
        List<String> users = findJavaSourcesContaining(ACTIVE_SOURCE_ROOT,
            "com.mojang.blaze3d.vertex.");

        assertEquals(Collections.singletonList(
            "src/main/java/net/oculus/pipeline/vertex/OculusVertexFormats.java"), users);
    }

    @Test
    public void earlyMixinLoaderQueuesOnlyPrimaryOculusConfig() throws IOException {
        String loaderSource = read(Paths.get("src/main/java/net/oculus/mixins/OculusMixinLoader.java"));

        assertTrue("OculusMixinLoader must stay on the early MixinBooter path",
            loaderSource.contains("implements IFMLLoadingPlugin, IEarlyMixinLoader"));
        assertTrue("OculusMixinLoader must queue only the active Oculus mixin config",
            loaderSource.contains("Collections.singletonList(\"" + PRIMARY_MIXIN_CONFIG + "\")"));
        assertTrue("OculusMixinLoader should accept only the active Oculus mixin config",
            loaderSource.contains("\"" + PRIMARY_MIXIN_CONFIG + "\".equals(mixinConfig)"));
        assertTrue("OculusMixinLoader must not restore late mixin loading",
            !loaderSource.contains("ILateMixinLoader"));

        for (String config : nonPrimaryMixinConfigs()) {
            assertTrue("Historical mixin config must not be queued by OculusMixinLoader: " + config,
                !loaderSource.contains("\"" + config + "\""));
        }
    }

    @Test
    public void activeMixinConfigStaysOnNetOculusRuntimePackage() throws IOException {
        Path configPath = RESOURCE_ROOT.resolve(PRIMARY_MIXIN_CONFIG);
        JsonObject config = new JsonParser().parse(read(configPath)).getAsJsonObject();

        assertEquals("net.oculus.mixin", config.get("package").getAsString());
        assertTrue("Active mixin config must not reference historical net.coderbot packages",
            !read(configPath).contains("net.coderbot"));

        JsonArray client = config.getAsJsonArray("client");
        for (JsonElement element : client) {
            String mixin = element.getAsString();
            assertTrue("Active mixin entries must be relative to net.oculus.mixin: " + mixin,
                !mixin.startsWith("net.") && !mixin.startsWith("com."));
        }
    }

    @Test
    public void forgeCoremodManifestUsesEarlyOculusLoaderWithoutMixinConfigsFlag() throws IOException {
        String build = read(Paths.get("build.gradle"));

        assertTrue("Jar manifest must install the Oculus early coremod loader",
            build.contains("\"FMLCorePlugin\": \"net.oculus.mixins.OculusMixinLoader\""));
        assertTrue("Jar manifest must mark the coremod jar as containing the FML mod",
            build.contains("\"FMLCorePluginContainsFMLMod\": \"true\""));
        assertTrue("Build must not rely on the late -Dmixin.configs route",
            !build.contains("-Dmixin.configs"));

        for (String config : nonPrimaryMixinConfigs()) {
            assertTrue("Historical mixin config must not be added to the active build route: " + config,
                !build.contains(config));
        }
    }

    private static List<String> findJavaSourcesContaining(Path root, String... needles) throws IOException {
        List<String> matches = new ArrayList<>();
        List<String> needleList = Arrays.asList(needles);

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        if (containsAny(source, needleList)) {
                            matches.add(toRepoPath(path));
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to read " + path, exception);
                    }
                });
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof IOException) {
                throw (IOException) exception.getCause();
            }
            throw exception;
        }

        Collections.sort(matches);
        return matches;
    }

    private static boolean containsAny(String source, List<String> needles) {
        for (String needle : needles) {
            if (source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> nonPrimaryMixinConfigs() throws IOException {
        List<String> configs = new ArrayList<>();

        try (Stream<Path> paths = Files.list(RESOURCE_ROOT)) {
            paths.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                .map(path -> path.getFileName().toString())
                .filter(name -> name.contains("mixin") && !PRIMARY_MIXIN_CONFIG.equals(name))
                .forEach(configs::add);
        }

        Collections.sort(configs);
        return configs;
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String toRepoPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
