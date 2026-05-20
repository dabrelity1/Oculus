package net.oculus.samplers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.function.ToIntBiFunction;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.program.ProgramSamplers;
import net.oculus.gl.program.SamplerOverrideMap;
import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.rendertarget.RenderTargets;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import org.junit.Test;
import sun.misc.Unsafe;

public class IrisSamplersTest {
    @Test
    public void worldReservedTextureUnitsMirrorReferenceWorldSamplerUnits() {
        assertEquals(new HashSet<>(Arrays.asList(0, 1, 2)), IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
        assertEquals(new HashSet<>(Arrays.asList(1, 2)), IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
    }

    @Test
    public void renderTargetSamplerBindingsLookUpCurrentTargetWhenUpdated() throws Exception {
        RenderTarget[] targets = new RenderTarget[] {
            fakeRenderTarget(101, 201)
        };
        RenderTargets renderTargets = fakeRenderTargets(targets);
        AtomicReference<Set<Integer>> flipped = new AtomicReference<>(Collections.emptySet());
        TextureBinding current = IrisSamplers.renderTargetBinding(renderTargets, flipped::get, 0, false);
        TextureBinding opposite = IrisSamplers.renderTargetBinding(renderTargets, flipped::get, 0, true);

        assertEquals(101, current.getTextureId());
        assertEquals(201, opposite.getTextureId());

        flipped.set(Collections.singleton(0));
        assertEquals(201, current.getTextureId());
        assertEquals(101, opposite.getTextureId());

        targets[0] = fakeRenderTarget(102, 202);

        flipped.set(Collections.emptySet());
        assertEquals(102, current.getTextureId());
        assertEquals(202, opposite.getTextureId());
        flipped.set(Collections.singleton(0));
        assertEquals(202, current.getTextureId());
        assertEquals(102, opposite.getTextureId());
    }

    @Test
    public void renderTargetSamplerBindingsFailFastIfRegisteredTargetDisappears() throws Exception {
        RenderTarget[] targets = new RenderTarget[] {
            fakeRenderTarget(101, 201)
        };
        RenderTargets renderTargets = fakeRenderTargets(targets);
        TextureBinding binding = IrisSamplers.renderTargetBinding(renderTargets, Collections::emptySet, 0, false);

        targets[0] = null;
        try {
            binding.getTextureId();
        } catch (IllegalStateException ex) {
            assertEquals("Render target colortex0 is not configured", ex.getMessage());
            return;
        }

        throw new AssertionError("Expected disappeared render target sampler binding to fail fast");
    }

    @Test
    public void renderTargetSamplerBindingsFailFastWhenProgramReferencesUnconfiguredSparseTarget() throws Exception {
        String[] missingTargetAliases = {
            "colortex1",
            "gdepth",
            "oculus_rt1",
            "oculus_flipped_rt1"
        };

        for (String missingTargetAlias : missingTargetAliases) {
            RenderTarget[] targets = new RenderTarget[] {
                fakeRenderTarget(101, 201),
                null
            };
            RenderTargets renderTargets = fakeRenderTargets(targets);
            FakeProgramBuilder fakeBuilder = fakeProgramBuilder("colortex0", missingTargetAlias);

            try {
                IrisSamplers.addRenderTargetSamplerBindings(fakeBuilder.builder,
                    Collections::emptySet, renderTargets, true);
            } catch (IllegalStateException ex) {
                assertEquals("Render target colortex1 is not configured", ex.getMessage());
                assertTrue(fakeBuilder.samplers.hasRegisteredSamplerBinding("colortex0"));
                continue;
            }

            throw new AssertionError("Expected missing render target sampler " + missingTargetAlias
                + " to fail fast");
        }
    }

    @Test
    public void renderTargetSamplerBindingsIgnoreSparseTargetWhenProgramDoesNotReferenceIt() throws Exception {
        RenderTarget[] targets = new RenderTarget[] {
            fakeRenderTarget(101, 201),
            null
        };
        RenderTargets renderTargets = fakeRenderTargets(targets);
        FakeProgramBuilder fakeBuilder = fakeProgramBuilder("colortex0");

        IrisSamplers.addRenderTargetSamplerBindings(fakeBuilder.builder,
            Collections::emptySet, renderTargets, true);

        assertTrue(fakeBuilder.samplers.hasRegisteredSamplerBinding("colortex0"));
        assertFalse(fakeBuilder.samplers.hasRegisteredSamplerBinding("colortex1"));
    }

    @Test
    public void renderTargetSamplerBindingsFailFastWhenProgramReferencesSamplerBeyondConfiguredTargetCount()
        throws Exception {
        String[] missingTargetAliases = {
            "colortex2",
            "gnormal",
            "oculus_rt2",
            "oculus_flipped_rt2"
        };

        for (String missingTargetAlias : missingTargetAliases) {
            RenderTarget[] targets = new RenderTarget[] {
                fakeRenderTarget(101, 201)
            };
            RenderTargets renderTargets = fakeRenderTargets(targets);
            FakeProgramBuilder fakeBuilder = fakeProgramBuilder("colortex0", missingTargetAlias);

            try {
                IrisSamplers.addRenderTargetSamplerBindings(fakeBuilder.builder,
                    Collections::emptySet, renderTargets, true);
            } catch (IllegalStateException ex) {
                assertEquals("Render target colortex2 is not configured", ex.getMessage());
                assertTrue(fakeBuilder.samplers.hasRegisteredSamplerBinding("colortex0"));
                continue;
            }

            throw new AssertionError("Expected out-of-range render target sampler " + missingTargetAlias
                + " to fail fast");
        }
    }

    @Test
    public void renderTargetSamplerBindingsFailFastWhenProgramReferencesUnsupportedTargetIndex()
        throws Exception {
        String[] unsupportedTargetAliases = {
            "colortex16",
            "oculus_rt16",
            "oculus_flipped_rt16",
            "colortex99",
            "colortex100",
            "oculus_rt100",
            "oculus_flipped_rt100"
        };

        for (String unsupportedTargetAlias : unsupportedTargetAliases) {
            RenderTarget[] targets = new RenderTarget[] {
                fakeRenderTarget(101, 201)
            };
            RenderTargets renderTargets = fakeRenderTargets(targets);
            FakeProgramBuilder fakeBuilder = fakeProgramBuilder("colortex0", unsupportedTargetAlias);
            setActiveSamplerUniformNames(fakeBuilder.builder, unsupportedTargetAlias);

            try {
                IrisSamplers.addRenderTargetSamplerBindings(fakeBuilder.builder,
                    Collections::emptySet, renderTargets, true);
            } catch (IllegalStateException ex) {
                String index = unsupportedTargetAlias.endsWith("100")
                    ? "100"
                    : unsupportedTargetAlias.endsWith("99") ? "99" : "16";
                assertEquals("Render target colortex" + index
                    + " is not supported; Oculus 1.16.5 exposes up to 16 color render targets.",
                    ex.getMessage());
                assertTrue(fakeBuilder.samplers.hasRegisteredSamplerBinding("colortex0"));
                continue;
            }

            throw new AssertionError("Expected unsupported render target sampler " + unsupportedTargetAlias
                + " to fail fast");
        }
    }

    @Test
    public void renderTargetSamplerBindingsFailFastWhenRenderTargetsAreUnavailable()
        throws Exception {
        FakeProgramBuilder fullscreenBuilder = fakeProgramBuilder("colortex0");
        boolean fullscreenFailed = false;
        try {
            IrisSamplers.addRenderTargetSamplerBindings(fullscreenBuilder.builder,
                Collections::emptySet, null, true);
        } catch (IllegalStateException ex) {
            fullscreenFailed = true;
            assertEquals("Render target colortex0 is not configured", ex.getMessage());
            assertFalse(fullscreenBuilder.samplers.hasRegisteredSamplerBinding("colortex0"));
        }
        if (!fullscreenFailed) {
            throw new AssertionError("Expected unavailable render targets to fail for fullscreen colortex0");
        }

        FakeProgramBuilder lowWorldBuilder = fakeProgramBuilder("colortex2");
        IrisSamplers.addRenderTargetSamplerBindings(lowWorldBuilder.builder,
            Collections::emptySet, null, false);
        assertFalse(lowWorldBuilder.samplers.hasRegisteredSamplerBinding("colortex2"));

        FakeProgramBuilder highWorldBuilder = fakeProgramBuilder("colortex4");
        boolean highWorldFailed = false;
        try {
            IrisSamplers.addRenderTargetSamplerBindings(highWorldBuilder.builder,
                Collections::emptySet, null, false);
        } catch (IllegalStateException ex) {
            highWorldFailed = true;
            assertEquals("Render target colortex4 is not configured", ex.getMessage());
            assertFalse(highWorldBuilder.samplers.hasRegisteredSamplerBinding("colortex4"));
        }
        if (!highWorldFailed) {
            throw new AssertionError("Expected unavailable render targets to fail for active render-target samplers");
        }
    }

    private static void setActiveSamplerUniformNames(ProgramBuilder builder, String... names) throws Exception {
        setField(builder, "activeSamplerUniformNames", new HashSet<>(Arrays.asList(names)));
    }

    @Test
    public void compositeAndWorldDepthSamplersFailFastWhenRenderTargetsAreUnavailable()
        throws Exception {
        String[] compositeDepthAliases = {
            "gdepthtex",
            "depthtex0",
            "depthtex1",
            "depthtex2",
            "oculus_depth"
        };

        for (String alias : compositeDepthAliases) {
            FakeProgramBuilder builder = fakeProgramBuilder(alias);
            try {
                IrisSamplers.addCompositeSamplerBindings(builder.builder, null);
            } catch (IllegalStateException ex) {
                assertEquals("Composite depth samplers require configured render targets", ex.getMessage());
                continue;
            }
            throw new AssertionError("Expected unavailable render targets to fail for " + alias);
        }

        String[] worldDepthAliases = {
            "depthtex0",
            "depthtex1"
        };

        for (String alias : worldDepthAliases) {
            FakeProgramBuilder builder = fakeProgramBuilder(alias);
            try {
                IrisSamplers.addWorldDepthSamplerBindings(builder.builder, null);
            } catch (IllegalStateException ex) {
                assertEquals("World depth samplers require configured render targets", ex.getMessage());
                continue;
            }
            throw new AssertionError("Expected unavailable world depth render targets to fail for " + alias);
        }
    }

    @Test
    public void nonFullscreenRenderTargetSamplerBindingsStillSkipLowColorBuffersBeyondConfiguredTargetCount()
        throws Exception {
        RenderTarget[] targets = new RenderTarget[] {
            fakeRenderTarget(101, 201)
        };
        RenderTargets renderTargets = fakeRenderTargets(targets);
        FakeProgramBuilder lowBufferBuilder = fakeProgramBuilder("colortex2");

        IrisSamplers.addRenderTargetSamplerBindings(lowBufferBuilder.builder,
            Collections::emptySet, renderTargets, false);

        assertFalse(lowBufferBuilder.samplers.hasRegisteredSamplerBinding("colortex2"));

        FakeProgramBuilder highBufferBuilder = fakeProgramBuilder("colortex4");
        try {
            IrisSamplers.addRenderTargetSamplerBindings(highBufferBuilder.builder,
                Collections::emptySet, renderTargets, false);
        } catch (IllegalStateException ex) {
            assertEquals("Render target colortex4 is not configured", ex.getMessage());
            return;
        }

        throw new AssertionError("Expected out-of-range non-fullscreen render target sampler to fail fast");
    }

    @Test
    public void renderTargetSamplerStartIndexMatchesReferenceFullscreenSplit() {
        assertEquals(0, IrisSamplers.firstRenderTargetSamplerIndex(true));
        assertEquals(4, IrisSamplers.firstRenderTargetSamplerIndex(false));
    }

    @Test
    public void fullscreenColortex0UsesDefaultSamplerUnitLikeReference() throws Exception {
        RenderTarget[] targets = new RenderTarget[] {
            fakeRenderTarget(101, 201)
        };
        RenderTargets renderTargets = fakeRenderTargets(targets);
        String legacyName = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(0);
        FakeProgramBuilder fakeBuilder = fakeProgramBuilder(
            "colortex0",
            legacyName,
            "gtexture0",
            "s_texture",
            "tex",
            "texture",
            "gtexture",
            "gaux0",
            "oculus_rt0");

        IrisSamplers.addRenderTargetSamplerBindings(fakeBuilder.builder,
            Collections::emptySet, renderTargets, true);

        ProgramSamplers samplers = fakeBuilder.samplers.build();
        assertEquals(1, samplers.getActiveSamplers());

        List<Object> bindings = samplerBindings(samplers);
        assertEquals(9, bindings.size());
        assertEquals(0, fieldValue(bindingNamed(bindings, "colortex0"), "unit"));
        assertEquals(0, fieldValue(bindingNamed(bindings, legacyName), "unit"));
        assertEquals(0, fieldValue(bindingNamed(bindings, "gtexture0"), "unit"));
        assertEquals(0, fieldValue(bindingNamed(bindings, "s_texture"), "unit"));
        assertEquals(0, fieldValue(bindingNamed(bindings, "tex"), "unit"));
        assertEquals(0, fieldValue(bindingNamed(bindings, "texture"), "unit"));
        assertEquals(0, fieldValue(bindingNamed(bindings, "gtexture"), "unit"));
        assertEquals(0, fieldValue(bindingNamed(bindings, "gaux0"), "unit"));
        assertEquals(0, fieldValue(bindingNamed(bindings, "oculus_rt0"), "unit"));
    }

    @Test
    public void fullscreenColortex0FailsWhenDefaultSamplerUnitIsUnavailableLikeReference() throws Exception {
        RenderTarget[] targets = new RenderTarget[] {
            fakeRenderTarget(101, 201)
        };
        RenderTargets renderTargets = fakeRenderTargets(targets);
        FakeProgramBuilder fakeBuilder = fakeProgramBuilder(Collections.singleton(0), "colortex0");

        try {
            IrisSamplers.addRenderTargetSamplerBindings(fakeBuilder.builder,
                Collections::emptySet, renderTargets, true);
        } catch (IllegalStateException ex) {
            assertEquals("Texture unit 0 is already used.", ex.getMessage());
            return;
        }

        throw new AssertionError("Expected fullscreen colortex0 to require the default sampler unit");
    }

    @Test
    public void postprocessAndWorldSamplerRegistrationUseSharedUpdateTimeRenderTargetLookup() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String world = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");

        String compositeBody = methodBody(composite, "private void overrideRenderTargetSamplerBindings");
        String finalBody = methodBody(finalPass, "private void overrideRenderTargetSamplerBindings");
        String worldBody = methodBody(world, "private Map<String, TextureBinding> createRenderTargetBindings");

        assertTrue(compositeBody.contains(
            "IrisSamplers.addRenderTargetSamplerBindings(builder, () -> stageReadsFromAlt, renderTargets, true);"));
        assertTrue(finalBody.contains(
            "IrisSamplers.addRenderTargetSamplerBindings(builder, () -> stageReadsFromAlt, renderTargets, true);"));
        assertTrue(worldBody.contains(
            "IrisSamplers.renderTargetBinding(\n                targets, this::getCurrentRenderTargetReadBuffers, index, false);"));
        assertTrue(worldBody.contains(
            "IrisSamplers.renderTargetBinding(\n                targets, this::getCurrentRenderTargetReadBuffers, index, true);"));

        assertFalse(compositeBody.contains("target.getAltTexture()"));
        assertFalse(compositeBody.contains("target.getMainTexture()"));
        assertFalse(finalBody.contains("target.getAltTexture()"));
        assertFalse(finalBody.contains("target.getMainTexture()"));
        assertFalse(worldBody.contains("? target.getAltTexture() : target.getMainTexture()"));
        assertFalse(worldBody.contains("? target.getMainTexture() : target.getAltTexture()"));
        assertFalse(worldBody.contains("this::getActiveReadBuffers"));
    }

    @Test
    public void levelSamplerBindingsMirrorReferenceWorldInputs() throws Exception {
        String source = read("src/main/java/net/oculus/samplers/IrisSamplers.java");
        String body = methodBody(source,
            "public static void addLevelSamplerBindings(ProgramBuilder builder,");

        int requirePipeline = body.indexOf("Objects.requireNonNull(pipeline, \"pipeline\");");
        int textureInput = body.indexOf("if (inputs.texture)", requirePipeline);
        int externalTexture = body.indexOf("builder.addExternalSampler(0, \"tex\", \"texture\", \"gtexture\");",
            textureInput);
        int fallbackTexture = body.indexOf("\"tex\", \"texture\", \"gtexture\", \"gcolor\", \"colortex0\"",
            externalTexture);
        int lightmapInput = body.indexOf("if (inputs.lightmap)", fallbackTexture);
        int externalLightmap = body.indexOf("builder.addExternalSampler(getLightmapTextureUnit(), \"lightmap\");",
            lightmapInput);
        int fallbackLightmap = body.indexOf(
            "builder.addDynamicSampler(FallbackTextures::getWhiteTexture, \"lightmap\");", externalLightmap);
        int overlayInput = body.indexOf("if (inputs.overlay)", fallbackLightmap);
        int externalOverlay = body.indexOf("builder.addExternalSampler(getOverlayTextureUnit(), \"iris_overlay\");",
            overlayInput);
        int fallbackOverlay = body.indexOf(
            "builder.addDynamicSampler(FallbackTextures::getWhiteTexture, \"iris_overlay\");", externalOverlay);
        int normals = body.indexOf(
            "builder.addDynamicSampler(pipeline::getCurrentNormalTexture,\n"
                + "            StateUpdateNotifiers.normalTextureChangeNotifier, \"normals\");",
            fallbackOverlay);
        int specular = body.indexOf(
            "builder.addDynamicSampler(pipeline::getCurrentSpecularTexture,\n"
                + "            StateUpdateNotifiers.specularTextureChangeNotifier, \"specular\");",
            normals);

        assertTrue("Level sampler bindings must fail clearly if a caller forgets the pipeline",
            requirePipeline >= 0);
        assertTrue("Texture samplers should bind the active world atlas when texture input is present",
            textureInput > requirePipeline && externalTexture > textureInput);
        assertTrue("Missing texture input should use the white fallback aliases from 1.16.5",
            fallbackTexture > externalTexture);
        assertTrue("Lightmap and overlay samplers must use 1.12 texture-unit helpers with white fallbacks",
            lightmapInput > fallbackTexture && externalLightmap > lightmapInput
                && fallbackLightmap > externalLightmap && overlayInput > fallbackLightmap
                && externalOverlay > overlayInput && fallbackOverlay > externalOverlay);
        assertTrue("PBR samplers must stay dynamic and listen to normal/specular texture changes",
            normals > fallbackOverlay && specular > normals);
    }

    @Test
    public void worldDepthSamplerBindingsMirrorReferenceTerrainSurface() throws Exception {
        String source = read("src/main/java/net/oculus/samplers/IrisSamplers.java");
        String body = methodBody(source, "public static void addWorldDepthSamplerBindings");

        int depth = body.indexOf(
            "TextureBinding depth = TextureBinding.texture2D(renderTargets::getCurrentDepthTexture);");
        int depthNoTranslucents = body.indexOf(
            "TextureBinding depthNoTranslucents = TextureBinding.texture2D(() ->\n"
                + "            renderTargets.getDepthTextureNoTranslucents().getTextureId());",
            depth);
        int depthtex0 = body.indexOf("builder.overrideSamplerBinding(\"depthtex0\", depth);",
            depthNoTranslucents);
        int depthtex1 = body.indexOf("builder.overrideSamplerBinding(\"depthtex1\", depthNoTranslucents);",
            depthtex0);

        assertTrue("Terrain depthtex0 must follow the active world depth texture at update time",
            depth >= 0 && depthtex0 > depth);
        assertTrue("Terrain depthtex1 must expose the pre-translucent depth copy",
            depthNoTranslucents > depth && depthtex1 > depthNoTranslucents);
        assertFalse("World terrain depth samplers must not expose composite-only gdepthtex",
            body.contains("\"gdepthtex\""));
        assertFalse("World terrain depth samplers must not expose composite-only depthtex2",
            body.contains("\"depthtex2\""));
        assertFalse("World terrain depth samplers must not expose Oculus composite aliases",
            body.contains("\"oculus_depth\""));
    }

    private static RenderTargets fakeRenderTargets(RenderTarget[] targets) throws Exception {
        RenderTargets renderTargets = allocate(RenderTargets.class);
        setField(renderTargets, "targets", targets);
        return renderTargets;
    }

    private static FakeProgramBuilder fakeProgramBuilder(String... activeSamplers) throws Exception {
        return fakeProgramBuilder(Collections.emptySet(), activeSamplers);
    }

    private static FakeProgramBuilder fakeProgramBuilder(Set<Integer> reservedTextureUnits,
                                                         String... activeSamplers) throws Exception {
        ProgramBuilder builder = allocate(ProgramBuilder.class);
        ProgramSamplers.Builder samplers = fakeSamplerBuilder(reservedTextureUnits, activeSamplers);
        setField(builder, "samplers", samplers);
        return new FakeProgramBuilder(builder, samplers);
    }

    private static ProgramSamplers.Builder fakeSamplerBuilder(Set<Integer> reservedTextureUnits,
                                                              String... activeSamplers) throws Exception {
        final Set<String> active = new HashSet<>(Arrays.asList(activeSamplers));
        IntSupplier maxTextureUnits = () -> 32;
        ToIntBiFunction<Integer, String> locations = (program, name) ->
            active.contains(name) ? Math.abs(name.hashCode() % 1000) + 1 : -1;

        Method builder = ProgramSamplers.class.getDeclaredMethod("builder",
            String.class,
            int.class,
            SamplerOverrideMap.class,
            Set.class,
            IntSupplier.class,
            ToIntBiFunction.class);
        builder.setAccessible(true);
        return (ProgramSamplers.Builder) builder.invoke(null,
            "test",
            1,
            SamplerOverrideMap.empty(),
            reservedTextureUnits,
            maxTextureUnits,
            locations);
    }

    private static RenderTarget fakeRenderTarget(int mainTexture, int altTexture) throws Exception {
        RenderTarget target = allocate(RenderTarget.class);
        setField(target, "valid", true);
        setField(target, "mainTexture", mainTexture);
        setField(target, "altTexture", altTexture);
        setField(target, "internalFormat", InternalTextureFormat.RGBA8);
        return target;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object bindingNamed(List<Object> bindings, String uniformName) throws Exception {
        for (Object binding : bindings) {
            if (uniformName.equals(fieldValue(binding, "uniformName"))) {
                return binding;
            }
        }
        throw new AssertionError("Missing sampler binding " + uniformName);
    }

    private static List<Object> samplerBindings(ProgramSamplers samplers) throws Exception {
        Field bindingsField = ProgramSamplers.class.getDeclaredField("bindings");
        bindingsField.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Object> bindings = (List<Object>) bindingsField.get(samplers);
        return bindings;
    }

    private static Object fieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class FakeProgramBuilder {
        private final ProgramBuilder builder;
        private final ProgramSamplers.Builder samplers;

        private FakeProgramBuilder(ProgramBuilder builder, ProgramSamplers.Builder samplers) {
            this.builder = builder;
            this.samplers = samplers;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method " + signature);
        }
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(brace, i + 1);
                }
            }
        }
        throw new AssertionError("Could not parse body for " + signature);
    }
}
