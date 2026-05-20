package net.oculus.gl.program;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntBiFunction;

import net.oculus.gl.state.StateUpdateNotifiers;
import net.oculus.gl.state.ValueUpdateNotifier;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import org.junit.Test;

public class ProgramSamplersTest {
    @Test
    public void nextAvailableTextureUnitSkipsUsedUnits() {
        assertEquals(18, ProgramSamplers.nextAvailableTextureUnit(16, 32, new HashSet<>(Arrays.asList(16, 17))));
    }

    @Test
    public void nextAvailableTextureUnitReturnsFirstCandidateWhenFree() {
        assertEquals(16, ProgramSamplers.nextAvailableTextureUnit(16, 32, new HashSet<>(Arrays.asList(15, 17))));
    }

    @Test
    public void nextAvailableTextureUnitRejectsExhaustedLimit() {
        assertEquals(-1, ProgramSamplers.nextAvailableTextureUnit(16, 16, new HashSet<Integer>()));
    }

    @Test
    public void nextAvailableTextureUnitClampsNegativeStartToZero() {
        assertEquals(1, ProgramSamplers.nextAvailableTextureUnit(-4, 4, new HashSet<>(Arrays.asList(0))));
    }

    @Test
    public void nextAvailableTextureUnitSkipsReservedUnits() {
        assertEquals(19, ProgramSamplers.nextAvailableTextureUnit(
            16,
            32,
            new HashSet<>(Arrays.asList(16, 18)),
            new HashSet<>(Arrays.asList(17))));
    }

    @Test
    public void pbrSamplerNamesUseReferenceTextureChangeNotifiersWithExactCase() {
        assertSame(StateUpdateNotifiers.normalTextureChangeNotifier,
            ProgramSamplers.Builder.textureChangeNotifierForSampler("normals"));
        assertSame(StateUpdateNotifiers.specularTextureChangeNotifier,
            ProgramSamplers.Builder.textureChangeNotifierForSampler("specular"));
        assertNull(ProgramSamplers.Builder.textureChangeNotifierForSampler("NORMALS"));
        assertNull(ProgramSamplers.Builder.textureChangeNotifierForSampler("Specular"));
        assertNull(ProgramSamplers.Builder.textureChangeNotifierForSampler("colortex0"));
    }

    @Test
    public void customTextureOverrideNamesMatchReferenceCaseSensitivity() {
        assertTrue(ProgramSamplers.Builder.samplerNameMatchesOverride("textureAtlas", "textureAtlas"));
        assertTrue(ProgramSamplers.Builder.samplerNameMatchesOverride("colortex4", "colortex4"));
        assertFalse(ProgramSamplers.Builder.samplerNameMatchesOverride("textureatlas", "textureAtlas"));
        assertFalse(ProgramSamplers.Builder.samplerNameMatchesOverride("COLORTEX4", "colortex4"));
    }

    @Test
    public void activeExternalSamplerReservesItsUnitBeforeManagedAliases() {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "test",
            7,
            SamplerOverrideMap.empty(),
            Collections.singleton(0),
            () -> 32,
            activeUniforms("u_BlockTex", "colortex0"));

        builder.addExternalSampler(0, "u_BlockTex");
        builder.overrideBinding("colortex0", TextureBinding.texture2D(() -> 44));

        assertEquals(17, builder.build().getActiveSamplers());
    }

    @Test
    public void legacyRenderTargetAliasesUseReferenceColortexUnits() throws Exception {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms());

        for (int index = 0; index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size(); index++) {
            String legacyName = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index);
            assertEquals("Legacy alias " + legacyName + " must match colortex" + index,
                resolveUnit(builder, "colortex" + index),
                resolveUnit(builder, legacyName));
        }

        assertEquals(15, resolveUnit(builder, "gaux4"));
    }

    @Test
    public void registryBackedGaux4SharesColortex7UnitWhenRegisteredFirst() throws Exception {
        TextureBinding binding = TextureBinding.texture2D(() -> 77);
        TextureBindingRegistry.register("gaux4", binding);
        TextureBindingRegistry.register("colortex7", binding);
        try {
            ProgramSamplers.Builder builder = ProgramSamplers.builder(
                "gbuffers_water",
                7,
                SamplerOverrideMap.empty(),
                Collections.emptySet(),
                () -> 32,
                activeUniforms("gaux4", "colortex7"));

            builder.addSampler("gaux4");
            builder.addSampler("colortex7");

            List<Object> bindings = samplerBindings(builder.build());
            assertEquals(2, bindings.size());
            for (Object samplerBinding : bindings) {
                assertEquals(15, fieldValue(samplerBinding, "unit"));
                assertSame(binding, fieldValue(samplerBinding, "binding"));
            }
        } finally {
            TextureBindingRegistry.clear();
        }
    }

    @Test
    public void shadowColorImageNamesAreNotSamplerUnitAliasesLikeReference() throws Exception {
        ProgramSamplers.Builder unitBuilder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms());

        assertEquals(-1, resolveUnit(unitBuilder, "shadowcolorimg0"));
        assertEquals(-1, resolveUnit(unitBuilder, "shadowcolorimg1"));

        ProgramSamplers.Builder bindingBuilder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("shadowcolorimg0", "shadowcolorimg1"));

        bindingBuilder.addSampler("shadowcolorimg0");
        bindingBuilder.addSampler("shadowcolorimg1");

        ProgramSamplers samplers = bindingBuilder.build();
        assertEquals(0, samplers.getActiveSamplers());
        assertTrue(samplerBindings(samplers).isEmpty());
    }

    @Test
    public void externalSamplerMustUseReservedTextureUnitLikeReference() {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "test",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("u_BlockTex"));

        try {
            builder.addExternalSampler(0, "u_BlockTex");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("externally-managed sampler"));
            assertTrue(exception.getMessage().contains("reserved texture units"));
            return;
        }

        throw new AssertionError("Expected unreserved external sampler to fail like the reference builder");
    }

    @Test
    public void externalSamplerFailsWhenManagedBindingAlreadyUsesThatUnit() {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "test",
            7,
            SamplerOverrideMap.empty(),
            Collections.singleton(0),
            () -> 32,
            activeUniforms("u_BlockTex", "texture"));

        builder.addExternalSampler(0, "texture");
        builder.overrideBinding("texture", TextureBinding.texture2D(() -> 44));

        try {
            builder.addExternalSampler(0, "u_BlockTex");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("already assigned"));
            assertTrue(exception.getMessage().contains("test"));
            return;
        }

        throw new AssertionError("Expected external sampler to fail after unit 0 was assigned to another binding");
    }

    @Test
    public void unitZeroExternalSamplerOverrideStaysOnDefaultTextureUnitLikeReference() {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "gbuffers_textured",
            7,
            SamplerOverrideMap.empty(),
            new HashSet<>(Arrays.asList(0, 1, 2)),
            () -> 32,
            activeUniforms("texture"));

        builder.addExternalSampler(0, "texture");
        builder.overrideBinding("texture", TextureBinding.texture2D(() -> 44));

        assertEquals(1, builder.build().getActiveSamplers());
    }

    @Test
    public void nonZeroExternalSamplerOverrideUsesDynamicTextureUnitLikeReference() {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "gbuffers_textured",
            7,
            SamplerOverrideMap.empty(),
            new HashSet<>(Arrays.asList(0, 1, 2)),
            () -> 32,
            activeUniforms("lightmap"));

        builder.addExternalSampler(1, "lightmap");
        builder.overrideBinding("lightmap", TextureBinding.texture2D(() -> 44));

        assertEquals(17, builder.build().getActiveSamplers());
    }

    @Test
    public void defaultSamplerUsesTextureUnitZeroLikeReference() throws Exception {
        TextureBinding customBinding = TextureBinding.texture2D(() -> 44);
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("customSampler"));

        assertTrue(builder.addDefaultSampler(customBinding::getTextureId, "customSampler"));

        Object samplerBinding = firstSamplerBinding(builder.build());
        assertEquals("customSampler", fieldValue(samplerBinding, "uniformName"));
        assertEquals(0, fieldValue(samplerBinding, "unit"));
    }

    @Test
    public void defaultSamplerStillBindsTextureUnitZeroWhenAliasesAreInactiveLikeReference() throws Exception {
        TextureBinding customBinding = TextureBinding.texture2D(() -> 44);
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms());

        assertTrue(builder.addDefaultSampler(customBinding, "colortex0", "gcolor"));

        ProgramSamplers samplers = builder.build();
        assertEquals(1, samplers.getActiveSamplers());

        Object samplerBinding = firstSamplerBinding(samplers);
        assertNull(fieldValue(samplerBinding, "uniformName"));
        assertEquals(-1, fieldValue(samplerBinding, "location"));
        assertEquals(0, fieldValue(samplerBinding, "unit"));
        assertSame(customBinding, fieldValue(samplerBinding, "binding"));
    }

    @Test
    public void inactiveDefaultSamplerFallbackDoesNotWriteMissingUniformLocation() throws Exception {
        String source = read("src/main/java/net/oculus/gl/program/ProgramSamplers.java");
        String bind = methodBody(source, "private void bind()");
        String addDefaultSampler = methodBody(source,
            "public boolean addDefaultSampler(TextureBinding binding, String... names)");

        assertTrue("Default sampler fallback must still allocate texture unit 0 when no aliases are active",
            addDefaultSampler.contains("addBinding(null, -1, 0, binding, null);"));
        assertTrue("The inactive fallback binding must not call glUniform1i with location -1",
            bind.contains("if (location >= 0)"));
        assertTrue(bind.indexOf("binding.bindToUnit(unit);") < bind.indexOf("if (location >= 0)"));
        assertTrue(bind.indexOf("GL20.glUniform1i(location, unit);") > bind.indexOf("if (location >= 0)"));
    }

    @Test
    public void defaultSamplerFailsWhenTextureUnitZeroIsReservedLikeReference() {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "gbuffers_textured",
            7,
            SamplerOverrideMap.empty(),
            Collections.singleton(0),
            () -> 32,
            activeUniforms("customSampler"));

        try {
            builder.addDefaultSampler(() -> 44, "customSampler");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("Texture unit 0 is already used"));
            return;
        }

        throw new AssertionError("Expected default sampler to fail when texture unit 0 is reserved");
    }

    @Test
    public void defaultSamplerFailsWhenTextureUnitZeroAlreadyHasManagedBindingLikeReference() {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("colortex0", "customSampler"));

        builder.addSampler("colortex0");

        try {
            builder.addDefaultSampler(() -> 44, "customSampler");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("Texture unit 0 is already used"));
            return;
        }

        throw new AssertionError("Expected default sampler to fail when texture unit 0 is already managed");
    }

    @Test
    public void defaultSamplerReplacesPreDiscoveredSameAliasOnTextureUnitZero() throws Exception {
        TextureBinding defaultBinding = TextureBinding.texture2D(() -> 44);
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("colortex0", "gcolor"));

        builder.addSampler("colortex0");

        assertTrue(builder.addDefaultSampler(defaultBinding, "colortex0", "gcolor"));

        ProgramSamplers samplers = builder.build();
        assertEquals(1, samplers.getActiveSamplers());

        List<Object> bindings = samplerBindings(samplers);
        assertEquals(2, bindings.size());
        assertEquals(0, fieldValue(bindingNamed(bindings, "colortex0"), "unit"));
        assertEquals(0, fieldValue(bindingNamed(bindings, "gcolor"), "unit"));
        assertSame(defaultBinding, fieldValue(bindingNamed(bindings, "colortex0"), "binding"));
        assertSame(defaultBinding, fieldValue(bindingNamed(bindings, "gcolor"), "binding"));
    }

    @Test
    public void overridingExistingSamplerUpdatesAllocatorBindingForSharedCustomTextures() {
        TextureBinding sharedBinding = TextureBinding.texture2D(() -> 44);
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("colortex0", "textureAtlas"));

        builder.addSampler("colortex0");
        builder.overrideBinding("colortex0", sharedBinding);
        builder.overrideBinding("textureAtlas", sharedBinding);

        assertEquals(1, builder.build().getActiveSamplers());
    }

    @Test
    public void overridingOneAliasUpdatesSharedSamplerBindingGroupLikeReference() throws Exception {
        TextureBinding firstOverride = TextureBinding.texture2D(() -> 55);
        ProgramSamplers.Builder firstAliasBuilder = ProgramSamplers.builder(
            "shadow",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("shadowtex0", "shadow"));

        firstAliasBuilder.addDynamicSampler(() -> 44, "shadowtex0", "shadow");
        firstAliasBuilder.overrideBinding("shadowtex0", firstOverride);

        assertSharedBinding(firstAliasBuilder.build(), firstOverride);

        TextureBinding secondOverride = TextureBinding.texture2D(() -> 66);
        ProgramSamplers.Builder secondAliasBuilder = ProgramSamplers.builder(
            "shadow",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("shadowtex0", "shadow"));

        secondAliasBuilder.addDynamicSampler(() -> 44, "shadowtex0", "shadow");
        secondAliasBuilder.overrideBinding("shadow", secondOverride);

        assertSharedBinding(secondAliasBuilder.build(), secondOverride);
    }

    @Test
    public void registeredSamplerBindingTracksActualManagedSamplerBindings() {
        ProgramSamplers.Builder addedBuilder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("colortex0"));

        assertFalse(addedBuilder.hasRegisteredSamplerBinding("colortex0"));
        addedBuilder.addSampler("colortex0");
        assertTrue(addedBuilder.hasRegisteredSamplerBinding("colortex0"));
        assertFalse(addedBuilder.hasRegisteredSamplerBinding("COLORTEX0"));

        ProgramSamplers.Builder overrideBuilder = ProgramSamplers.builder(
            "gbuffers_terrain",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("gcolor"));

        overrideBuilder.overrideBinding("gcolor", TextureBinding.texture2D(() -> 55));
        assertTrue(overrideBuilder.hasRegisteredSamplerBinding("gcolor"));

        ProgramSamplers.Builder externalBuilder = ProgramSamplers.builder(
            "gbuffers_textured",
            7,
            SamplerOverrideMap.empty(),
            Collections.singleton(0),
            () -> 32,
            activeUniforms("tex"));

        externalBuilder.addExternalSampler(0, "tex");
        assertFalse(externalBuilder.hasRegisteredSamplerBinding("tex"));
    }

    @Test
    public void registryBackedCustomSamplerGetsDynamicUnitWhenNameIsNotBuiltInAlias() {
        TextureBinding binding = TextureBinding.texture2D(() -> 44);
        TextureBindingRegistry.register("CustomImageSampler", binding);
        try {
            ProgramSamplers.Builder builder = ProgramSamplers.builder(
                "composite",
                7,
                SamplerOverrideMap.empty(),
                Collections.emptySet(),
                () -> 32,
                activeUniforms("CustomImageSampler"));

            builder.addSampler("CustomImageSampler");

            assertEquals(17, builder.build().getActiveSamplers());
        } finally {
            TextureBindingRegistry.clear();
        }
    }

    @Test
    public void samplerOnlyCustomImageBindingGetsDynamicUnitWithoutImageUniform() throws Exception {
        TextureBinding binding = TextureBinding.texture2D(() -> 44);
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("wsr_sampler"));

        builder.overrideBinding("wsr_sampler", binding);

        Object samplerBinding = firstSamplerBinding(builder.build());
        assertEquals("wsr_sampler", fieldValue(samplerBinding, "uniformName"));
        assertEquals(16, fieldValue(samplerBinding, "unit"));
        assertSame(binding, fieldValue(samplerBinding, "binding"));
    }

    @Test
    public void registryBackedCustomSamplerRequiresExactUniformCase() {
        TextureBinding binding = TextureBinding.texture2D(() -> 44);
        TextureBindingRegistry.register("CustomImageSampler", binding);
        try {
            ProgramSamplers.Builder builder = ProgramSamplers.builder(
                "composite",
                7,
                SamplerOverrideMap.empty(),
                Collections.emptySet(),
                () -> 32,
                activeUniforms("customimagesampler"));

            builder.addSampler("customimagesampler");

            assertEquals(0, builder.build().getActiveSamplers());
        } finally {
            TextureBindingRegistry.clear();
        }
    }

    @Test
    public void builtInSamplerUnitAliasesRequireExactUniformCaseLikeReference() {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("COLORTEX4"));

        builder.addSampler("COLORTEX4");

        assertEquals(0, builder.build().getActiveSamplers());
    }

    @Test
    public void samplerOverrideMapRequiresExactUniformCaseLikeReference() {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.builder().put("oculus_rt4", 4).build(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("OCULUS_RT4"));

        builder.addSampler("OCULUS_RT4");

        assertEquals(0, builder.build().getActiveSamplers());
    }

    @Test
    public void exactHardwareShadowSamplerAliasesRemainReachable() throws Exception {
        TextureBinding binding = TextureBinding.texture2D(() -> 99);
        TextureBindingRegistry.register("shadowtex0HW", binding);
        try {
            ProgramSamplers.Builder builder = ProgramSamplers.builder(
                "shadow",
                7,
                SamplerOverrideMap.empty(),
                Collections.emptySet(),
                () -> 32,
                activeUniforms("shadowtex0HW"));

            builder.addSampler("shadowtex0HW");

            Object samplerBinding = firstSamplerBinding(builder.build());
            assertEquals(5, fieldValue(samplerBinding, "unit"));
            assertSame(binding, fieldValue(samplerBinding, "binding"));
        } finally {
            TextureBindingRegistry.clear();
        }
    }

    @Test
    public void registryBackedWorldRenderTargetSamplerKeepsReferenceUnitWhenNotReserved() throws Exception {
        TextureBinding binding = TextureBinding.texture2D(() -> 77);
        TextureBindingRegistry.register("colortex4", binding);
        try {
            ProgramSamplers.Builder builder = ProgramSamplers.builder(
                "gbuffers_terrain",
                7,
                SamplerOverrideMap.empty(),
                new HashSet<>(Arrays.asList(0, 1, 2)),
                () -> 32,
                activeUniforms("colortex4"));

            builder.addSampler("colortex4");

            Object samplerBinding = firstSamplerBinding(builder.build());
            assertEquals(4, fieldValue(samplerBinding, "unit"));
            assertSame(binding, fieldValue(samplerBinding, "binding"));
        } finally {
            TextureBindingRegistry.clear();
        }
    }

    @Test
    public void registryBackedWorldDepthSamplerUsesDepthUnitAfterReservedWorldInputs() throws Exception {
        TextureBinding binding = TextureBinding.texture2D(() -> 88);
        TextureBindingRegistry.register("depthtex0", binding);
        try {
            ProgramSamplers.Builder builder = ProgramSamplers.builder(
                "gbuffers_water",
                7,
                SamplerOverrideMap.empty(),
                new HashSet<>(Arrays.asList(0, 1, 2)),
                () -> 32,
                activeUniforms("depthtex0"));

            builder.addSampler("depthtex0");

            Object samplerBinding = firstSamplerBinding(builder.build());
            assertEquals(9, fieldValue(samplerBinding, "unit"));
            assertSame(binding, fieldValue(samplerBinding, "binding"));
        } finally {
            TextureBindingRegistry.clear();
        }
    }

    @Test
    public void notifierBackedOverrideReplacesExistingSamplerNotifier() throws Exception {
        TestValueUpdateNotifier notifier = new TestValueUpdateNotifier();
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "gbuffers_textured",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("normals"));

        builder.addSampler("normals");
        builder.addDynamicSampler(() -> 55, notifier, "normals");

        Object binding = firstSamplerBinding(builder.build());
        assertSame(notifier, fieldValue(binding, "notifier"));
        assertNotNull(fieldValue(binding, "listener"));
    }

    @Test
    public void notifierBackedOverrideCanAddNotifierToExistingSamplerWithoutOne() throws Exception {
        TestValueUpdateNotifier notifier = new TestValueUpdateNotifier();
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("colortex0"));

        builder.addSampler("colortex0");
        builder.addDynamicSampler(() -> 55, notifier, "colortex0");

        Object binding = firstSamplerBinding(builder.build());
        assertSame(notifier, fieldValue(binding, "notifier"));
        assertNotNull(fieldValue(binding, "listener"));
    }

    @Test
    public void notifierlessOverridePreservesExistingPbrSamplerNotifier() throws Exception {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "gbuffers_textured",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("normals"));

        builder.addSampler("normals");
        builder.overrideBinding("normals", TextureBinding.texture2D(() -> 55));

        Object binding = firstSamplerBinding(builder.build());
        assertSame(StateUpdateNotifiers.normalTextureChangeNotifier, fieldValue(binding, "notifier"));
        assertNotNull(fieldValue(binding, "listener"));
    }

    @Test
    public void notifierlessAliasOverridePreservesSharedPbrSamplerNotifier() throws Exception {
        TextureBinding customBinding = TextureBinding.texture2D(() -> 55);
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "gbuffers_textured",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 32,
            activeUniforms("normals", "colortex1"));

        builder.addSampler("normals");
        builder.addSampler("colortex1");
        builder.overrideBinding("colortex1", customBinding);

        List<Object> bindings = samplerBindings(builder.build());
        assertEquals(2, bindings.size());

        Object normals = bindingNamed(bindings, "normals");
        Object colortex1 = bindingNamed(bindings, "colortex1");
        assertSame(customBinding, fieldValue(normals, "binding"));
        assertSame(customBinding, fieldValue(colortex1, "binding"));
        assertSame(StateUpdateNotifiers.normalTextureChangeNotifier, fieldValue(normals, "notifier"));
        assertNotNull(fieldValue(normals, "listener"));
        assertNull(fieldValue(colortex1, "notifier"));
        assertNull(fieldValue(colortex1, "listener"));
    }

    @Test
    public void exhaustedDynamicSamplerFailureNamesActiveSamplerAndProgram() {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "composite",
            7,
            SamplerOverrideMap.empty(),
            Collections.emptySet(),
            () -> 16,
            activeUniforms("customSampler"));

        try {
            builder.overrideBinding("customSampler", TextureBinding.texture2D(() -> 44));
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("customSampler"));
            assertTrue(exception.getMessage().contains("composite"));
            assertTrue(exception.getMessage().contains("16 texture unit"));
            return;
        }

        throw new AssertionError("Expected custom sampler allocation to fail when dynamic units are exhausted");
    }

    @Test
    public void exhaustedReservedRelocationFailureNamesSamplerBeingMoved() {
        ProgramSamplers.Builder builder = ProgramSamplers.builder(
            "gbuffers_terrain",
            7,
            SamplerOverrideMap.empty(),
            Collections.singleton(0),
            () -> 16,
            activeUniforms("colortex0"));

        try {
            builder.addSampler("colortex0");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("colortex0"));
            assertTrue(exception.getMessage().contains("gbuffers_terrain"));
            assertTrue(exception.getMessage().contains("16 texture unit"));
            return;
        }

        throw new AssertionError("Expected reserved-unit relocation to fail when dynamic units are exhausted");
    }

    @Test
    public void samplerUpdateManagesNotifierLifecycleLikeReference() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/program/ProgramSamplers.java")), StandardCharsets.UTF_8);
        String updateBody = methodBody(source, "public void update()");
        String cleanupBody = methodBody(source, "private static Throwable cleanupActiveBeforeUpdate()");

        int cleanup = updateBody.indexOf("Throwable previousCleanupFailure = cleanupActiveBeforeUpdate();");
        int activeSet = updateBody.indexOf("active = this;", cleanup);
        int bindLoop = updateBody.indexOf("for (SamplerBinding binding : bindings)", activeSet);
        int rethrowPreviousCleanup = updateBody.indexOf("rethrowCleanupFailure(previousCleanupFailure);", bindLoop);

        assertTrue(cleanup >= 0);
        assertTrue(activeSet > cleanup);
        assertTrue(bindLoop > activeSet);
        assertTrue(rethrowPreviousCleanup > bindLoop);
        assertTrue(cleanupBody.contains("ProgramSamplers current = active;"));
        assertTrue(cleanupBody.contains("return runCleanup(null, ProgramSamplers::clearActiveSamplers);"));
        assertTrue(source.contains("binding.attachListener();"));
        assertTrue(source.contains("private Runnable listener;"));
        assertTrue(source.contains("this.listener = notifier == null ? null : this::bindTexturePreservingActiveUnit;"));
        assertTrue(source.contains("private ValueUpdateNotifier setNotifier(ValueUpdateNotifier notifier)"));
        assertTrue(source.contains("samplerBinding.setNotifier(notifier);"));
        assertTrue(source.contains("notifier.setListener(listener);"));
        assertTrue(source.contains("notifier.removeListener(listener);"));
        assertTrue(source.contains("private void bindTexturePreservingActiveUnit()"));
        assertTrue(source.contains("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);"));
        assertTrue(source.contains("customBindings.put(samplerName, binding);"));
        assertTrue(source.contains("TextureBinding registryBinding = TextureBindingRegistry.resolve(uniformName);"));
        assertTrue(source.contains("registryBinding != TextureBinding.unbound();"));
    }

    @Test
    public void samplerUpdateClearsPreviousSamplerBindingsBeforeReplacement() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/program/ProgramSamplers.java")), StandardCharsets.UTF_8);
        String cleanupBody = methodBody(source, "private static Throwable cleanupActiveBeforeUpdate()");
        String clearBody = methodBody(source, "public static void clearActiveSamplers()");

        int currentCapture = cleanupBody.indexOf("ProgramSamplers current = active;");
        int nullGuard = cleanupBody.indexOf("if (current == null)", currentCapture);
        int clearActive = cleanupBody.indexOf(
            "return runCleanup(null, ProgramSamplers::clearActiveSamplers);", nullGuard);

        assertTrue(currentCapture >= 0);
        assertTrue(nullGuard > currentCapture);
        assertTrue(clearActive > nullGuard);
        assertTrue(clearBody.contains("failure = runCleanup(failure, current::removeListeners);"));
        assertTrue(clearBody.contains("failure = runCleanup(failure, current::unbind);"));
        assertTrue(clearBody.contains("active = null;"));
    }

    @Test
    public void samplerCleanupAttemptsListenersBindingsAndTextureRestoreBeforeRethrowing() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/program/ProgramSamplers.java")), StandardCharsets.UTF_8);
        String clearBody = methodBody(source, "public static void clearActiveSamplers()");
        String removeBody = methodBody(source, "private void removeListeners()");
        String unbindBody = methodBody(source, "private void unbind()");

        int captureTry = clearBody.indexOf("previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);");
        int removeListeners = clearBody.indexOf("failure = runCleanup(failure, current::removeListeners);", captureTry);
        int unbind = clearBody.indexOf("failure = runCleanup(failure, current::unbind);", removeListeners);
        int finallyBlock = clearBody.indexOf("} finally {", unbind);
        int activeNull = clearBody.indexOf("active = null;", finallyBlock);
        int restoreLocal = clearBody.indexOf("final int textureUnitToRestore = previousActiveTexture;", activeNull);
        int restore = clearBody.indexOf(
            "failure = runCleanup(failure, () -> OculusRenderSystem.setActiveTextureUnit(textureUnitToRestore));",
            restoreLocal);
        int rethrow = clearBody.indexOf("rethrowCleanupFailure(failure);", restore);

        assertTrue(captureTry >= 0);
        assertTrue(removeListeners > captureTry);
        assertTrue(unbind > removeListeners);
        assertTrue(finallyBlock > unbind);
        assertTrue(activeNull > finallyBlock);
        assertTrue(restoreLocal > activeNull);
        assertTrue(restore > restoreLocal);
        assertTrue(rethrow > restore);

        assertTrue(removeBody.contains("failure = runCleanup(failure, binding::detachListener);"));
        assertTrue(removeBody.contains("rethrowCleanupFailure(failure);"));
        assertTrue(unbindBody.contains("failure = runCleanup(failure, binding::unbind);"));
        assertTrue(unbindBody.contains("rethrowCleanupFailure(failure);"));
    }

    @Test
    public void failedSamplerUpdateClearsPublishedActiveBindingSetBeforeRethrowing() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/program/ProgramSamplers.java")), StandardCharsets.UTF_8);
        String updateBody = methodBody(source, "public void update()");
        String cleanupBody = methodBody(source, "private static void cleanupAfterFailedUpdate(Throwable failure)");

        int activeSet = updateBody.indexOf("active = this;");
        int tryBlock = updateBody.indexOf("try {", activeSet);
        int initializer = updateBody.indexOf("if (initializer != null)", tryBlock);
        int activeTextureCapture = updateBody.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);", initializer);
        int bindLoop = updateBody.indexOf("for (SamplerBinding binding : bindings)", activeTextureCapture);
        int attach = updateBody.indexOf("binding.attachListener();", bindLoop);
        int restore = updateBody.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);", attach);
        int runtimeCatch = updateBody.indexOf("catch (RuntimeException exception)", restore);
        int runtimeSuppress = updateBody.indexOf("suppressCleanupFailure(exception, previousCleanupFailure);", runtimeCatch);
        int runtimeCleanup = updateBody.indexOf("cleanupAfterFailedUpdate(exception);", runtimeSuppress);
        int errorCatch = updateBody.indexOf("catch (Error error)", runtimeCleanup);
        int errorSuppress = updateBody.indexOf("suppressCleanupFailure(error, previousCleanupFailure);", errorCatch);
        int errorCleanup = updateBody.indexOf("cleanupAfterFailedUpdate(error);", errorSuppress);

        assertTrue(activeSet >= 0);
        assertTrue(tryBlock > activeSet);
        assertTrue(initializer > tryBlock);
        assertTrue(activeTextureCapture > initializer);
        assertTrue(bindLoop > activeTextureCapture);
        assertTrue(attach > bindLoop);
        assertTrue(restore > attach);
        assertTrue(runtimeCatch > restore);
        assertTrue(runtimeSuppress > runtimeCatch);
        assertTrue(runtimeCleanup > runtimeSuppress);
        assertTrue(errorCatch > runtimeCleanup);
        assertTrue(errorSuppress > errorCatch);
        assertTrue(errorCleanup > errorSuppress);
        assertTrue(cleanupBody.contains("clearActiveSamplers();"));
        assertTrue(cleanupBody.contains("suppressCleanupFailure(failure, cleanupFailure);"));
        assertTrue(source.contains("private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure)"));
    }

    @Test
    public void samplerCleanupSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same sampler cleanup failure");

        suppressCleanupFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void samplerCleanupSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("sampler failure");
        RuntimeException cleanupFailure = new RuntimeException("sampler cleanup failure");

        suppressCleanupFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    @Test
    public void samplerBindingsUseForcedSamplerTextureUnitWrapper() throws IOException {
        String samplers = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/program/ProgramSamplers.java")), StandardCharsets.UTF_8);
        String binding = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/program/TextureBinding.java")), StandardCharsets.UTF_8);
        String renderSystem = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/OculusRenderSystem.java")), StandardCharsets.UTF_8);

        assertTrue(samplers.contains("binding.bindToUnit(unit);"));
        assertTrue(samplers.contains("binding.unbindFromUnit(unit);"));
        assertTrue(binding.contains("OculusRenderSystem.bindSamplerTexture2DToUnit(textureUnit, texture);"));
        assertTrue(binding.contains("OculusRenderSystem.bindSamplerTexture2DToUnit(textureUnit, 0);"));
        assertTrue(binding.contains("OculusRenderSystem.setActiveTextureUnit(GL13.GL_TEXTURE0 + textureUnit);"));
        assertTrue(renderSystem.contains("public static void bindSamplerTexture2DToUnit(int textureUnit, int texture)"));
        assertTrue(renderSystem.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);"));
        assertTrue(renderSystem.contains("runWithoutTextureBindCallback(() -> GlStateManager.bindTexture(texture));"));
        assertTrue(renderSystem.contains("public static void setActiveTextureUnit(int glTextureUnit)"));
        assertTrue(renderSystem.contains("GlStateManager.setActiveTexture(glTextureUnit);"));
        assertTrue(renderSystem.contains("OpenGlHelper.setActiveTexture(glTextureUnit);"));
    }

    private static Object firstSamplerBinding(ProgramSamplers samplers) throws Exception {
        List<Object> bindings = samplerBindings(samplers);
        assertEquals(1, bindings.size());
        return bindings.get(0);
    }

    private static Object bindingNamed(List<Object> bindings, String uniformName) throws Exception {
        for (Object binding : bindings) {
            if (uniformName.equals(fieldValue(binding, "uniformName"))) {
                return binding;
            }
        }
        throw new AssertionError("Missing sampler binding " + uniformName);
    }

    private static void assertSharedBinding(ProgramSamplers samplers, TextureBinding expectedBinding) throws Exception {
        List<Object> bindings = samplerBindings(samplers);
        assertEquals(2, bindings.size());
        Object first = bindings.get(0);
        Object second = bindings.get(1);
        assertEquals(fieldValue(first, "unit"), fieldValue(second, "unit"));
        assertSame(expectedBinding, fieldValue(first, "binding"));
        assertSame(expectedBinding, fieldValue(second, "binding"));
    }

    private static List<Object> samplerBindings(ProgramSamplers samplers) throws Exception {
        Field bindingsField = ProgramSamplers.class.getDeclaredField("bindings");
        bindingsField.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Object> bindings = (List<Object>) bindingsField.get(samplers);
        return bindings;
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static Object fieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int resolveUnit(ProgramSamplers.Builder builder, String name) throws Exception {
        Method method = ProgramSamplers.Builder.class.getDeclaredMethod("resolveUnit", String.class);
        method.setAccessible(true);
        return (Integer) method.invoke(builder, name);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = ProgramSamplers.class.getDeclaredMethod(
            "suppressCleanupFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, cleanupFailure);
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

    private static ToIntBiFunction<Integer, String> activeUniforms(String... names) {
        Set<String> active = new HashSet<>(Arrays.asList(names));
        return (program, name) -> active.contains(name) ? 3 : -1;
    }

    private static final class TestValueUpdateNotifier implements ValueUpdateNotifier {
        private Runnable listener;

        @Override
        public void setListener(Runnable listener) {
            this.listener = listener;
        }

        @Override
        public void removeListener(Runnable listener) {
            if (this.listener == listener) {
                this.listener = null;
            }
        }
    }
}
