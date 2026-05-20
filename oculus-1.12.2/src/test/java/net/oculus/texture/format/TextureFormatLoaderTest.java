package net.oculus.texture.format;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipError;

import javax.imageio.ImageIO;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;
import net.oculus.gl.shader.StandardMacros;
import net.oculus.shaderpack.StringPair;
import net.oculus.texture.pbr.PBRType;
import org.lwjgl.opengl.GL11;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

public class TextureFormatLoaderTest {
    private static final String GL_PROBE_BYPASS_PROPERTY = "oculus.disableGlStringProbes";

    @After
    public void clearLoadedFormat() {
        TextureFormatLoader.reload(new MissingResourceManager());
        TextureFormatLoader.resetReloadListenerRegistrationForTesting();
    }

    @Test
    public void labPbrDefinesMatch1165FormatRules() {
        TextureFormat format = new LabPBRTextureFormat("lab-pbr", "1.3.0");

        assertEquals("MC_TEXTURE_FORMAT_LAB_PBR", format.getDefines().get(0));
        assertEquals("MC_TEXTURE_FORMAT_LAB_PBR_1_3_0", format.getDefines().get(1));
    }

    @Test
    public void textureFormatWithoutVersionOnlyExposesBaseDefine() {
        TextureFormat format = new LabPBRTextureFormat("lab-pbr", null);

        assertEquals(1, format.getDefines().size());
        assertEquals("MC_TEXTURE_FORMAT_LAB_PBR", format.getDefines().get(0));
    }

    @Test
    public void loaderParsesOptifineTextureProperties() {
        Properties properties = new Properties();
        properties.setProperty("format", "lab-pbr/1.3.0");

        assertEquals(new LabPBRTextureFormat("lab-pbr", "1.3.0"), TextureFormatLoader.loadFormat(properties));
    }

    @Test
    public void loaderIgnoresUnknownOrMissingFormats() {
        Properties unknown = new Properties();
        unknown.setProperty("format", "unknown/1.0");
        assertNull(TextureFormatLoader.loadFormat(unknown));

        Properties missing = new Properties();
        assertNull(TextureFormatLoader.loadFormat(missing));
    }

    @Test
    public void reloadMakesTextureFormatDefinesVisibleToStandardMacros() {
        String previousGlProbeBypass = System.getProperty(GL_PROBE_BYPASS_PROPERTY);
        System.setProperty(GL_PROBE_BYPASS_PROPERTY, "true");
        try {
            TextureFormatLoader.reload(new StringResourceManager("format=lab-pbr/1.3.0\n"));

            assertEquals(new LabPBRTextureFormat("lab-pbr", "1.3.0"), TextureFormatLoader.getFormat());
            Iterable<StringPair> defines = StandardMacros.createStandardEnvironmentDefines();
            assertTrue(contains(defines, "MC_TEXTURE_FORMAT_LAB_PBR"));
            assertTrue(contains(defines, "MC_TEXTURE_FORMAT_LAB_PBR_1_3_0"));
        } finally {
            restoreProperty(GL_PROBE_BYPASS_PROPERTY, previousGlProbeBypass);
        }
    }

    @Test
    public void localPbrValidationPackMatchesRuntimeTextureFormatPathAndCompanions() throws Exception {
        Path packRoot = Paths.get("run/resourcepacks/Oculus-PBR-Validation");
        Assume.assumeTrue("Oculus-PBR-Validation resource pack is not present", Files.isDirectory(packRoot));

        Path textureProperties = packRoot.resolve("assets/minecraft/optifine/texture.properties");
        Path normalPath = packRoot.resolve("assets/minecraft/textures/blocks/dirt_n.png");
        Path specularPath = packRoot.resolve("assets/minecraft/textures/blocks/dirt_s.png");

        assertTrue(Files.isRegularFile(textureProperties));
        assertTrue(Files.isRegularFile(normalPath));
        assertTrue(Files.isRegularFile(specularPath));
        assertEquals(new LabPBRTextureFormat("lab-pbr", "1.3.0"),
            TextureFormatLoader.loadFormat(new FolderResourceManager(packRoot)));

        ResourceLocation baseTexture = new ResourceLocation("minecraft", "textures/blocks/dirt.png");
        assertEquals(new ResourceLocation("minecraft", "textures/blocks/dirt_n.png"),
            PBRType.NORMAL.appendToFileLocation(baseTexture));
        assertEquals(new ResourceLocation("minecraft", "textures/blocks/dirt_s.png"),
            PBRType.SPECULAR.appendToFileLocation(baseTexture));
        assertPngSize(normalPath, 16, 16);
        assertPngSize(specularPath, 16, 16);
    }

    @Test
    public void missingTexturePropertiesClearsLoadedFormat() {
        TextureFormatLoader.reload(new StringResourceManager("format=lab-pbr/1.3.0\n"));
        assertEquals(new LabPBRTextureFormat("lab-pbr", "1.3.0"), TextureFormatLoader.getFormat());

        TextureFormatLoader.reload(new MissingResourceManager());

        assertNull(TextureFormatLoader.getFormat());
    }

    @Test
    public void corruptZipTexturePropertiesClearsLoadedFormatLikeMissingMetadata() {
        TextureFormatLoader.reload(new StringResourceManager("format=lab-pbr/1.3.0\n"));
        assertEquals(new LabPBRTextureFormat("lab-pbr", "1.3.0"), TextureFormatLoader.getFormat());

        TextureFormatLoader.reload(new ZipErrorResourceManager());

        assertNull(TextureFormatLoader.getFormat());
    }

    @Test
    public void labPbrDoesNotInterpolateSpecularValues() {
        TextureFormat format = new LabPBRTextureFormat("lab-pbr", "1.3.0");

        assertTrue(format.canInterpolateValues(PBRType.NORMAL));
        assertFalse(format.canInterpolateValues(PBRType.SPECULAR));
    }

    @Test
    public void textureFormatParameterSetupDetectsMipmapMinFilters() {
        assertFalse(TextureFormat.hasMipmappedMinFilter(GL11.GL_NEAREST));
        assertFalse(TextureFormat.hasMipmappedMinFilter(GL11.GL_LINEAR));
        assertTrue(TextureFormat.hasMipmappedMinFilter(GL11.GL_NEAREST_MIPMAP_NEAREST));
        assertTrue(TextureFormat.hasMipmappedMinFilter(GL11.GL_LINEAR_MIPMAP_NEAREST));
        assertTrue(TextureFormat.hasMipmappedMinFilter(GL11.GL_NEAREST_MIPMAP_LINEAR));
        assertTrue(TextureFormat.hasMipmappedMinFilter(GL11.GL_LINEAR_MIPMAP_LINEAR));
    }

    @Test
    public void textureFormatParameterSetupRestoreFailureKeepsOriginalFailure() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/texture/format/TextureFormat.java")), StandardCharsets.UTF_8);
        String setupBody = methodBody(source, "default void setupTextureParameters(PBRType pbrType, int textureId)");
        String restoreBody = methodBody(source,
            "static void restorePreviousTextureBinding(int previousTextureBinding, Throwable setupFailure)");

        int callbackGuard = setupBody.indexOf("OculusRenderSystem.runWithoutTextureBindCallback(() -> {");
        int setupFailureLocal = setupBody.indexOf("Throwable setupFailure = null;", callbackGuard);
        int bindTexture = setupBody.indexOf("GlStateManager.bindTexture(textureId);", setupFailureLocal);
        int minFilter = setupBody.indexOf("GL11.glGetTexParameteri", bindTexture);
        int mipmapCheck = setupBody.indexOf("hasMipmappedMinFilter(minFilter);", minFilter);
        int catchBlock = setupBody.indexOf("catch (RuntimeException | Error exception)", mipmapCheck);
        int captureFailure = setupBody.indexOf("setupFailure = exception;", catchBlock);
        int rethrow = setupBody.indexOf("throw exception;", captureFailure);
        int restore = setupBody.indexOf("restorePreviousTextureBinding(previousTextureBinding, setupFailure);", rethrow);

        int restoreBind = restoreBody.indexOf("GlStateManager.bindTexture(previousTextureBinding);");
        int restoreCatch = restoreBody.indexOf("catch (RuntimeException | Error restoreFailure)", restoreBind);
        int setupFailureGuard = restoreBody.indexOf("if (setupFailure != null)", restoreCatch);
        int suppress = restoreBody.indexOf("suppressRestoreFailure(setupFailure, restoreFailure);", setupFailureGuard);
        int keepOriginal = restoreBody.indexOf("return;", suppress);
        int failFast = restoreBody.indexOf("throw restoreFailure;", keepOriginal);

        assertTrue(callbackGuard >= 0);
        assertTrue(setupFailureLocal > callbackGuard);
        assertTrue(bindTexture > setupFailureLocal);
        assertTrue(minFilter > bindTexture);
        assertTrue(mipmapCheck > minFilter);
        assertTrue(catchBlock > mipmapCheck);
        assertTrue(captureFailure > catchBlock);
        assertTrue(rethrow > captureFailure);
        assertTrue(restore > rethrow);

        assertTrue(restoreBind >= 0);
        assertTrue(restoreCatch > restoreBind);
        assertTrue(setupFailureGuard > restoreCatch);
        assertTrue(suppress > setupFailureGuard);
        assertTrue(keepOriginal > suppress);
        assertTrue(failFast > keepOriginal);
    }

    @Test
    public void textureFormatRestoreSuppressionIgnoresSameThrowable() {
        RuntimeException failure = new RuntimeException("setup");

        TextureFormat.suppressRestoreFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void textureFormatRestoreSuppressionKeepsDistinctFailureContext() {
        RuntimeException failure = new RuntimeException("setup");
        RuntimeException restoreFailure = new RuntimeException("restore");

        TextureFormat.suppressRestoreFailure(failure, restoreFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(restoreFailure, failure.getSuppressed()[0]);
    }

    @Test
    public void reloadClearsPbrHoldersBeforeOptionalShaderReload() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/texture/format/TextureFormatLoader.java")), StandardCharsets.UTF_8);
        String reloadBody = methodBody(source, "public static void reload(IResourceManager resourceManager)");

        int formatSet = reloadBody.indexOf("format = newFormat;");
        int clear = reloadBody.indexOf("PBRTextureManager.INSTANCE.clear();", formatSet);
        int clearCatch = reloadBody.indexOf("catch (RuntimeException | Error exception)", clear);
        int clearFailure = reloadBody.indexOf("failure = exception;", clearCatch);
        int formatChange = reloadBody.indexOf("if (didFormatChange)", clearFailure);
        int onFormatChange = reloadBody.indexOf("onFormatChange();", formatChange);
        int rethrow = reloadBody.indexOf("rethrowReloadFailure(failure);", onFormatChange);

        assertTrue(formatSet >= 0);
        assertTrue("Resource reload must discard stale PBR holder textures before any shader-pack reload",
            clear > formatSet && formatChange > clear && onFormatChange > formatChange);
        assertTrue("PBR cleanup failure must be collected before optional shader reload is attempted",
            clearCatch > clear && clearFailure > clearCatch && formatChange > clearFailure);
        assertTrue("Collected PBR cleanup failure must be rethrown after optional shader reload",
            rethrow > onFormatChange);
    }

    @Test
    public void reloadAttemptsOptionalShaderReloadAfterPbrClearFailureAtSourceLevel() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/texture/format/TextureFormatLoader.java")), StandardCharsets.UTF_8);
        String reloadBody = methodBody(source, "public static void reload(IResourceManager resourceManager)");

        int failureLocal = reloadBody.indexOf("Throwable failure = null;");
        int clear = reloadBody.indexOf("PBRTextureManager.INSTANCE.clear();", failureLocal);
        int clearCatch = reloadBody.indexOf("catch (RuntimeException | Error exception)", clear);
        int clearFailure = reloadBody.indexOf("failure = exception;", clearCatch);
        int formatChange = reloadBody.indexOf("if (didFormatChange)", clearFailure);
        int reloadTry = reloadBody.indexOf("try {", formatChange);
        int onFormatChange = reloadBody.indexOf("onFormatChange();", reloadTry);
        int reloadCatch = reloadBody.indexOf("catch (RuntimeException | Error exception)", onFormatChange);
        int collectFailure = reloadBody.indexOf("failure = collectReloadFailure(failure, exception);", reloadCatch);
        int rethrow = reloadBody.indexOf("rethrowReloadFailure(failure);", collectFailure);

        assertTrue(failureLocal >= 0);
        assertTrue(clear > failureLocal);
        assertTrue(clearCatch > clear);
        assertTrue(clearFailure > clearCatch);
        assertTrue(formatChange > clearFailure);
        assertTrue(reloadTry > formatChange);
        assertTrue(onFormatChange > reloadTry);
        assertTrue(reloadCatch > onFormatChange);
        assertTrue(collectFailure > reloadCatch);
        assertTrue(rethrow > collectFailure);
    }

    @Test
    public void reloadFailureAggregationIgnoresSameThrowable() {
        RuntimeException failure = new RuntimeException("pbr clear");

        Throwable result = TextureFormatLoader.collectReloadFailure(failure, failure);

        assertSame(failure, result);
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void reloadFailureAggregationKeepsDistinctFailureContext() {
        RuntimeException failure = new RuntimeException("pbr clear");
        RuntimeException reloadFailure = new RuntimeException("shader reload");

        Throwable result = TextureFormatLoader.collectReloadFailure(failure, reloadFailure);

        assertSame(failure, result);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(reloadFailure, failure.getSuppressed()[0]);
    }

    @Test
    public void formatChangeShaderReloadTreatsCorruptZipAsRecoverable() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/texture/format/TextureFormatLoader.java")), StandardCharsets.UTF_8);
        String onFormatChangeBody = methodBody(source, "private static void onFormatChange()");

        assertTrue(onFormatChangeBody.contains("ShaderPackReloader.reload();"));
        assertTrue(onFormatChangeBody.contains("catch (RuntimeException | ZipError exception)"));
    }

    @Test
    public void earlyNonReloadableManagerDoesNotLatchReloadListenerRegistration() {
        assertFalse(TextureFormatLoader.registerReloadListener(new MissingResourceManager()));

        CountingReloadableResourceManager reloadable =
            new CountingReloadableResourceManager("format=lab-pbr/1.3.0\n");
        assertTrue(TextureFormatLoader.registerReloadListener(reloadable));
        assertEquals(1, reloadable.listenerCount);

        reloadable.fireReload();

        assertEquals(new LabPBRTextureFormat("lab-pbr", "1.3.0"), TextureFormatLoader.getFormat());
    }

    @Test
    public void reloadableRegistrationIsLatchedBeforeImmediateReloadCallback() {
        ReentrantReloadableResourceManager reloadable =
            new ReentrantReloadableResourceManager("format=lab-pbr/1.3.0\n");

        assertTrue(TextureFormatLoader.registerReloadListener(reloadable));

        assertEquals(1, reloadable.listenerCount);
        assertEquals(new LabPBRTextureFormat("lab-pbr", "1.3.0"), TextureFormatLoader.getFormat());
    }

    @Test
    public void hardReloadListenerRegistrationFailureDoesNotLatchRegistration() {
        Error failure = new AssertionError("reload listener registration failed");

        try {
            TextureFormatLoader.registerReloadListener(new FailingReloadableResourceManager(failure));
        } catch (Error thrown) {
            assertSame(failure, thrown);
        }

        CountingReloadableResourceManager reloadable =
            new CountingReloadableResourceManager("format=lab-pbr/1.3.0\n");

        assertTrue(TextureFormatLoader.registerReloadListener(reloadable));
        assertEquals(1, reloadable.listenerCount);
    }

    @Test
    public void pbrTypeClassifiesSuffixesAndAppendsBeforeExtension() {
        assertEquals(PBRType.NORMAL, PBRType.fromFileLocation("textures/block/stone_n"));
        assertEquals(PBRType.SPECULAR, PBRType.fromFileLocation("textures/block/stone_s"));
        assertNull(PBRType.fromFileLocation("textures/block/stone"));

        assertEquals(
            new ResourceLocation("minecraft", "textures/block/stone_n.png"),
            PBRType.NORMAL.appendToFileLocation(new ResourceLocation("minecraft", "textures/block/stone.png")));
        assertEquals(
            new ResourceLocation("minecraft", "textures/block/stone.v2_s.png"),
            PBRType.SPECULAR.appendToFileLocation(new ResourceLocation("minecraft", "textures/block/stone.v2.png")));
    }

    private static boolean contains(Iterable<StringPair> defines, String name) {
        for (StringPair pair : defines) {
            if (name.equals(pair.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static void assertPngSize(Path path, int width, int height) throws IOException {
        BufferedImage image;
        try (InputStream input = Files.newInputStream(path)) {
            image = ImageIO.read(input);
        }

        assertNotNull("Expected PNG image " + path, image);
        assertEquals(width, image.getWidth());
        assertEquals(height, image.getHeight());
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

        throw new AssertionError("Could not parse method body for " + signature);
    }

    private static void restoreProperty(String property, String previous) {
        if (previous == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previous);
        }
    }

    private static class StringResourceManager implements IResourceManager {
        private final String content;

        private StringResourceManager(String content) {
            this.content = content;
        }

        @Override
        public Set<String> getResourceDomains() {
            return Collections.singleton(TextureFormatLoader.LOCATION.getNamespace());
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            if (!TextureFormatLoader.LOCATION.equals(location)) {
                throw new FileNotFoundException(location.toString());
            }
            return new StringResource(content);
        }

        @Override
        public List<IResource> getAllResources(ResourceLocation location) throws IOException {
            return Collections.singletonList(getResource(location));
        }
    }

    private static final class FolderResourceManager implements IResourceManager {
        private final Path packRoot;

        private FolderResourceManager(Path packRoot) {
            this.packRoot = packRoot;
        }

        @Override
        public Set<String> getResourceDomains() {
            return Collections.singleton("minecraft");
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            Path path = packRoot.resolve("assets")
                .resolve(location.getNamespace())
                .resolve(location.getPath());
            if (!Files.isRegularFile(path)) {
                throw new FileNotFoundException(location.toString());
            }
            return new FileResource(location, Files.readAllBytes(path));
        }

        @Override
        public List<IResource> getAllResources(ResourceLocation location) throws IOException {
            return Collections.singletonList(getResource(location));
        }
    }

    private static final class MissingResourceManager implements IResourceManager {
        @Override
        public Set<String> getResourceDomains() {
            return Collections.emptySet();
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            throw new FileNotFoundException(location.toString());
        }

        @Override
        public List<IResource> getAllResources(ResourceLocation location) throws IOException {
            throw new FileNotFoundException(location.toString());
        }
    }

    private static final class ZipErrorResourceManager implements IResourceManager {
        @Override
        public Set<String> getResourceDomains() {
            return Collections.singleton(TextureFormatLoader.LOCATION.getNamespace());
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            throw new ZipError("corrupt texture format resource");
        }

        @Override
        public List<IResource> getAllResources(ResourceLocation location) throws IOException {
            throw new ZipError("corrupt texture format resource");
        }
    }

    private static final class CountingReloadableResourceManager extends StringResourceManager
        implements IReloadableResourceManager {
        private int listenerCount;
        private IResourceManagerReloadListener listener;

        private CountingReloadableResourceManager(String content) {
            super(content);
        }

        @Override
        public void reloadResources(List<IResourcePack> resourcePacks) {
        }

        @Override
        public void registerReloadListener(IResourceManagerReloadListener listener) {
            this.listener = listener;
            this.listenerCount++;
        }

        private void fireReload() {
            if (listener != null) {
                listener.onResourceManagerReload(this);
            }
        }
    }

    private static final class ReentrantReloadableResourceManager extends StringResourceManager
        implements IReloadableResourceManager {
        private int listenerCount;
        private boolean reentered;

        private ReentrantReloadableResourceManager(String content) {
            super(content);
        }

        @Override
        public void reloadResources(List<IResourcePack> resourcePacks) {
        }

        @Override
        public void registerReloadListener(IResourceManagerReloadListener listener) {
            listenerCount++;
            if (!reentered) {
                reentered = true;
                TextureFormatLoader.registerReloadListener(this);
            }
            listener.onResourceManagerReload(this);
        }
    }

    private static final class FailingReloadableResourceManager implements IReloadableResourceManager {
        private final Error failure;

        private FailingReloadableResourceManager(Error failure) {
            this.failure = failure;
        }

        @Override
        public Set<String> getResourceDomains() {
            return Collections.emptySet();
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            throw new FileNotFoundException(location.toString());
        }

        @Override
        public List<IResource> getAllResources(ResourceLocation location) throws IOException {
            throw new FileNotFoundException(location.toString());
        }

        @Override
        public void reloadResources(List<IResourcePack> resourcePacks) {
        }

        @Override
        public void registerReloadListener(IResourceManagerReloadListener listener) {
            throw failure;
        }
    }

    private static final class StringResource implements IResource {
        private final String content;

        private StringResource(String content) {
            this.content = content;
        }

        @Override
        public ResourceLocation getResourceLocation() {
            return TextureFormatLoader.LOCATION;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public boolean hasMetadata() {
            return false;
        }

        @Override
        public <T extends IMetadataSection> T getMetadata(String sectionName) {
            return null;
        }

        @Override
        public String getResourcePackName() {
            return "test";
        }

        @Override
        public void close() {
        }
    }

    private static final class FileResource implements IResource {
        private final ResourceLocation location;
        private final byte[] content;

        private FileResource(ResourceLocation location, byte[] content) {
            this.location = location;
            this.content = content;
        }

        @Override
        public ResourceLocation getResourceLocation() {
            return location;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public boolean hasMetadata() {
            return false;
        }

        @Override
        public <T extends IMetadataSection> T getMetadata(String sectionName) {
            return null;
        }

        @Override
        public String getResourcePackName() {
            return "Oculus-PBR-Validation";
        }

        @Override
        public void close() {
        }
    }
}
