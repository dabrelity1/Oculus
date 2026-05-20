# Verification Workflow

Last updated: 2026-05-20.

Use this workflow before telling a user that a slice is done. Full-port completion requires substantially more than this checklist.

## Compile And Build

Run both commands with Java 8:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain build --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --stacktrace
```

`build` now runs `compileTestJava` and `test`. Treat a green build as Java/unit-test coverage only; it still does not prove in-client shader rendering parity.

## Focused Diff Hygiene

For touched files:

```bash
git diff --check -- <file> <file>
```

Check line endings if `diff --check` reports many false-looking trailing whitespace entries:

```bash
file <file>
```

The repo contains mixed historical line endings. Normalize only files you intentionally edit.

## Legacy Shim Guard

For changes near `com.mojang.*`, `net.coderbot.iris.*`, source-package routing, or agent navigation docs, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.compat.LegacyShimUsageSourceTest --stacktrace
```

This source guard proves active `net.oculus` code does not depend on historical `net.coderbot.iris` runtime packages, inactive modern compatibility shells stay out of active runtime source, and modern vertex shim imports remain scoped to `OculusVertexFormats`.

## Shader-Pack Loader Guard

For shader-pack parser or resource-loading changes, run the real-pack loader guard when the local packs exist:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --tests net.oculus.shaderpack.ProgramSetTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.CommentDirectiveParserTest --tests net.oculus.shaderpack.ProgramSetTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.PackRenderTargetDirectivesTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.DispatchingDirectiveHolderTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.ComputeDirectiveParserTest --stacktrace
```

This guard proves the real local Complementary directory and packaged zip can be parsed outside a live Minecraft singleton, and now also covers the local MakeUp UltraFast directory and packaged zip when present. It verifies 1.16.5-style nested `TopFolder/shaders` zip roots, rejects directory packs without a direct `<pack>/shaders` child, ignores root-level `<pack>/shaders.properties` when a valid direct `shaders/` child exists, checks representative dimension/profile/option/language/texture/id-map metadata, proves program sources remain available after zip loading returns, pins Complementary's `customTexture.textureAtlas` under the exact camelCase key for every texture stage, pins MakeUp's option-preprocessed `texture.gbuffers.gaux2` and `texture.deferred.gaux2` directives for both the default natural cloud texture path and the `CLOUD_VOL_STYLE=1` blocky texture path, pins base-root selection from wildcard dimension mappings, pins that wildcard-only mappings reuse the cached base `ProgramSet` instead of creating per-dimension overrides, pins fallback to the base set when a declared dimension folder has no known program starts, pins the source-backed rule that shader option macros are not visible while preprocessing `dimension.properties`, and pins raw empty-value dimension tokens. `CustomTextureManagerTest` covers the manager-side half of that case-sensitive custom-sampler path without GL by using lightmap marker data. `CommentDirectiveParserTest` pins the active `DRAWBUFFERS` / `RENDERTARGETS` parser boundary: the same-type last occurrence wins like 1.16.5, malformed latest occurrences suppress earlier matches, whitespace before the colon is rejected, `ProgramDirectives` applies the latest directive type, and malformed `RENDERTARGETS` comma tokens with spaces or empty payloads fail instead of being normalized. It also records that generic `acceptComment*Directive` handlers are no-op in the inspected 1.16.5 dispatcher, so `SHADOWRES`-style comments are not active parity evidence. `ProgramSetTest` pins disabled-program key scoping, the local 1.16.5 compute directive scan order where final compute sources are processed before shadow compute sources, and the `gbuffers_damagedblock` fallback timing where the fallback source receives draw buffers `{0}` only after directive collection. `PackRenderTargetDirectivesTest` pins the registered legacy `GAUX4FORMAT` handler allowlist (`RGBA32F`, `RGB32F`, `RGB16`) while confirming the const `colortex7Format` path still accepts general internal formats. `DispatchingDirectiveHolderTest` pins the reference vector const dispatcher boundary: extra vector constructor args are logged but the first expected components are used, while bad numeric vector values are logged and ignored without throwing. `ComputeDirectiveParserTest` pins the same malformed-constructor boundary for compute-specific `workGroups` / `workGroupsRender` parsing. These tests do not prove shader compilation or rendered output.

For shader option profile parsing or option-menu layout changes, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.option.ProfileSetTest --tests net.oculus.shaderpack.option.menu.OptionMenuContainerTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.option.OptionAnnotatedSourceTest --tests net.oculus.shaderpack.option.ProfileSetTest --tests net.oculus.shaderpack.option.menu.OptionMenuContainerTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.option.OptionSetTest --tests net.oculus.shaderpack.option.OptionAnnotatedSourceTest --tests net.oculus.shaderpack.option.ProfileSetTest --tests net.oculus.shaderpack.option.menu.OptionMenuContainerTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.option.OptionAnnotatedSourceTest --tests net.oculus.shaderpack.option.values.OptionValuesTest --tests net.oculus.shaderpack.option.OptionSetTest --tests net.oculus.shaderpack.option.ProfileSetTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.option.values.OptionValuesTest --tests net.oculus.shaderpack.ProgramConditionEvaluatorTest --tests net.oculus.shaderpack.option.OptionSetTest --tests net.oculus.shaderpack.option.OptionAnnotatedSourceTest --tests net.oculus.shaderpack.option.ProfileSetTest --tests net.oculus.shaderpack.option.menu.OptionMenuContainerTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
```

This guard pins original-source layout parsing, reference space-only splitting for `screen`, `screen.*`, `sliders`, and `profile.*` values, profile inheritance, disabled programs, missing/recursive dependency failure behavior, unknown assignment match behavior, negated token precedence, negated string-option behavior, `=` versus `:` assignment precedence, malformed assignment storage, reference-style option-menu wildcard/profile/link construction, and real Complementary profile/resource loading. It does not prove in-client profile cycling, option persistence, GUI rendering, or rendered differences after profile changes.

`OptionMenuContainerTest` also pins that the menu screen model returns raw parsed `screen.columns` / `screen.<name>.columns` integers like the 1.16.5 `OptionMenuElementScreen`. The 1.12 GUI constructor clamps later for widget layout safety; the parser/model layer should not clamp.

`OptionAnnotatedSourceTest` in this set pins the boolean shader-option discovery boundary: boolean candidates, including `const bool`, are only admitted when their name appears in the connected-component `#ifdef` / `#ifndef` reference set like the local 1.16.5 source. String options still bypass that boolean-only gate. The same test also pins reference source-editing semantics: unchanged `const` and string options preserve their original lines, changed string `#define` options rewrite to the reference `OptionAnnotatedSource: Changed option` line, `const` edits quote regex replacement values and only touch the post-`=` segment, and boolean `#define` toggles use the reference tri-state comment helper, including the odd default-false double-comment output.

`OptionSetTest` pins duplicate option merging: same-name boolean or string options with conflicting defaults are removed as ambiguous like the 1.16.5 builder, while same-default duplicates prefer the first declaration with a comment and adopt a later comment only when the existing declaration has none.

`OptionValuesTest` pins changed-option storage: unknown keys are ignored, exact defaults are removed from the changed map, invalid boolean strings collapse to the default, mutable setters remove defaults, raw changed-value accessors return `OptionalBoolean.DEFAULT` / empty `Optional` for unchanged options, raw accessors do not cross boolean/string option types, and unknown boolean lookups default to `true` like the 1.16.5 `OptionValues` helper. `ProgramConditionEvaluatorTest` uses that same boundary for expression-like and case-mismatched `program.*.enabled` values.

`ProfileSetTest` in this set also pins that `profile.*` tokens are not trimmed after the reference `value.split(" +")` list split. Tab-suffixed option names, disabled-program names, and dependency names remain raw parser input like the local 1.16.5 source.

`ShaderPropertiesTest` in this set also pins the reference literal-space parser boundary for `size.buffer.*`, including rejection of tab-separated and double-space malformed values. That test proves parser behavior only; framebuffer scale and resize behavior still need live render validation.

It also pins the `handleTwoArgDirective(...)` boundary used by `texture.*` and `flip.*`: keys missing the second dot fail fast like the 1.16.5 helper, while empty second arguments are preserved. `PackDirectives` now also pins the 1.16.5 explicit-flip alias resolution boundary: duplicate aliases such as `gcolor` and `colortex0` for the same pass fail after resolving to the same integer buffer instead of silently overwriting, and non-empty resolved flip maps are immutable. This is malformed-pack parser and directive-resolution evidence, not runtime texture or flip validation.

The same parser guard pins `handlePassDirective(...)` for pass directives such as `scale.*`, `blend.*`, and `alphaTest.*`: empty pass suffixes are preserved like the 1.16.5 helper. Renderer behavior for those malformed empty-pass entries is still outside the unit guard.

For blend parsing specifically, `ShaderPropertiesTest` also pins the 1.16.5 fail-fast boundaries: invalid blend-mode tokens throw, extra valid blend-mode tokens are parsed before the first four are used, unknown per-buffer blend targets throw, and malformed `colortex` buffer IDs wrap the `NumberFormatException`. This remains parser evidence only; live GL blend state validation still needs a Minecraft client run.

It also pins `program.*` first-dot extraction: malformed extra suffixes do not extend the program key, empty names are preserved, and missing second dots fail fast. That is parser evidence only; use reload/client validation for actual program toggling behavior.

For shader-pack `.lang` loading or translation lookup changes, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava test --tests net.oculus.client.InternalTranslationResourceTest --tests net.oculus.client.IrisLanguageJsonLoaderTest --tests net.oculus.shaderpack.LanguageMapTest --tests net.oculus.shaderpack.ShaderPackLanguageLookupTest --tests net.oculus.mixins.I18nLanguageMixinSourceTest --stacktrace
```

This guard pins UTF-8 legacy `.lang` loading, legacy lowercase language-code normalization, configured-language plus `en_us` fallback, bundled `assets/iris/lang/*.json` loading into the 1.12 client `Locale`, bundled English coverage for 1.12 GUI-only translation keys, vanilla formatting failure text, both 1.12 translation mixin hooks, and `oculus.mixins.json` registration. It does not prove in-client language reloads, option-screen rendering under every configured language, translated-message layout, or third-party translation mixin compatibility.

For preprocessing macro changes, especially `StandardMacros` and `MC_VERSION`, also run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.gl.shader.StandardMacrosTest --tests net.oculus.shaderpack.preprocessor.PropertiesPreprocessorTest --tests net.oculus.shader.IrisFeatureDefinesTest --tests net.oculus.gl.OculusRenderSystemCapabilityDispatchTest --tests net.oculus.gl.image.ImageLimitsTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.preprocessor.JcppProcessorTest --tests net.oculus.shaderpack.preprocessor.PropertiesPreprocessorTest --tests net.oculus.gl.shader.StandardMacrosTest --tests net.oculus.shader.IrisFeatureDefinesTest --stacktrace
```

`StandardMacrosTest` pins the Forge 1.12.2 default `MC_VERSION=11202`, the explicit `oculus.mcVersionOverride` escape hatch, the headless unknown GL/GLSL fallback shape, and the 1.16.5 GL vendor/renderer prefix table. It does not prove every shaderpack conditional branch is visually correct.

`JcppProcessorTest` pins GLSL directive marker hoisting, reserved internal marker rejection, macro expansion, inactive extension suppression, and the local 1.16.5 source-preprocessing failure boundary. `PropertiesPreprocessorTest` pins option-visible properties preprocessing and the two-pass `shaders.properties` flow where supported optional Iris feature declarations become same-file `IRIS_FEATURE_*` guards. `ShaderPropertiesTest` and `IrisFeatureDefinesTest` pin the feature-token boundary: `iris.features.*` lists split only on spaces, preserve case and duplicate tokens, validate case-sensitively, and do not uppercase optional defines. `OculusRenderSystemCapabilityDispatchTest` and `ImageLimitsTest` pin headless-safe capability gating; tests that need this path set `oculus.disableGlCapabilityProbes=true`, while live client validation must leave that property unset. `OculusRenderSystemCapabilityDispatchTest` also pins the OpenGL 4.4 / `GL_ARB_clear_texture`, OpenGL 4.3 / `GL_ARB_clear_buffer_object`, and center-depth OpenGL 3.2 wrapper surface so callers do not reintroduce direct capability probes. `ImageLimitsTest` also pins that a first unavailable image-unit probe can recover after a later positive probe.

## Shader Source Transform Guard

For changes to `ShaderCompatibilityPatcher`, `ShaderSourcePreparer`, availability variants, variant failure handling, or overlay/entity-color handling, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shader.ShaderCompatibilityPatcherTest --tests net.oculus.shader.ShaderSourcePreparerTest --tests net.oculus.shader.ShaderLoaderSourceTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.pipeline.PipelineManagerSourceTest --tests net.oculus.pipeline.shadow.ShadowRendererSourceTest --tests net.oculus.pipeline.shadow.ShadowRendererBytecodeTest --tests net.oculus.gl.program.InternalProgramBuilderPathTest --tests net.oculus.uniforms.GameplayUniformsTest --tests net.oculus.mixins.RenderLivingBaseEntityColorMixinSourceTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
```

This pins availability-specific texture-coordinate/matrix rewrites, the 1.16.5 compatibility-profile gate, the 1.16.5 numeric `#version` precondition for regular raster source preparation, the 1.16.5 internal `iris_` / `irisMain` source guard for regular pack sources, generated `_sodium` source allowance after Sodium injection, `centerDepthSmooth`, texture LOD extension injection, Sildur water and mid-texcoord fixes, MakeUp UltraFast's missing legacy `shifted_dither17` fallback, Complementary's saved world-space-reflection composite source promotion to `#version 430 compatibility` when active SSBO declarations are present, grouped raster interface repair across the reference `BuiltinNumericTypeSpecifier` breadth, the 1.16.5 const-parameter cleanup behavior including illegal tracked-name redefinition failure, the 1.12-adapted `entityColor` passthrough fed by `iris_entityColor` instead of sampling the white brightness overlay texture, exact-variant lookup for `gbuffers_*` / root shadow `InputAvailability` programs, root shadow raster ownership in `ShaderWorldRenderingPipeline` instead of `ShadowRenderer`, shadow target preparation before `prepareBeforeShadow` and before root shadow raster sync, `prepareBeforeShadow`-aware root shadow render-target read buffers, empty pre-shadow render-target reads for shadow compute bindings, postprocess raster ownership outside generic `ShaderLoader`, source-level pass-resolution failure when an exact selected variant is missing, the delayed first-frame shader failure fallback latch in `PipelineManager`, and the real Complementary `gbuffers_entities` source-preparation path when the local pack is present. The same `ShaderPackLoaderComplementaryTest` guard now also prepares MakeUp UltraFast directory and zip runtime program sources across Overworld, Nether, End, and fallback base roots, including every `InputAvailability` variant for `gbuffers_*` and `shadow` programs. It does not prove live entity hurt/custom overlay rendering, rare numeric interface declarations on a target GL context, malformed-pack UI reporting, shader reload recovery after fallback, runtime shadow output, or shader visual parity. Driver GLSL acceptance for the Complementary SSBO path was runtime-proven by the 2026-05-20 Complementary validation recorded in `docs/evidence-log.md`; other driver/pack combinations still need client validation. Compute source version handling is intentionally outside this guard because the inspected 1.16.5 compute builders do not route through `TransformPatcher`.

For texture-format macro or reload changes, also run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.texture.format.TextureFormatLoaderTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.gl.state.StateUpdateNotifiersTest --tests net.oculus.gl.shader.StandardMacrosTest --stacktrace
```

For atlas PBR or LabPBR mipmap changes, extend that command with:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.texture.TextureInfoCacheTest --tests net.oculus.texture.TextureLifecycleTrackerSourceTest --tests net.oculus.mixins.GlStateManagerTextureLifecycleMixinSourceTest --tests net.oculus.gl.program.InternalProgramBuilderPathTest --tests net.oculus.uniforms.GameplayUniformsTest --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --tests net.oculus.texture.pbr.loader.SimplePBRLoaderGlTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.client.OculusRuntimeValidationTest --tests net.oculus.mixins.GlStateManagerStateMixinSourceTest --tests net.oculus.texture.mipmap.ChannelMipmapGeneratorTest --tests net.oculus.texture.format.LabPBRTextureFormatTest --tests net.oculus.texture.pbr.PBRAtlasTextureTest --tests net.oculus.texture.pbr.loader.AtlasPBRLoaderTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.texture.format.TextureFormatLoaderTest --tests net.oculus.gl.state.StateUpdateNotifiersTest --tests net.oculus.gl.shader.StandardMacrosTest --stacktrace
```

`TextureFormatLoaderTest` pins `optifine/texture.properties` parsing, `lab-pbr` registry behavior, `MC_TEXTURE_FORMAT_*` define emission through `StandardMacros`, format clearing when the resource disappears, and PBR suffix classification. `PBRTextureManagerTest` pins the default normal/specular channel layout and active PBR sampler detection, `CustomTextureManagerTest` pins custom resource `_n` / `_s` base-resource indirection, exact-case stage custom sampler key preservation, and generated fallback noise pixel order against the local 1.16.5 `NativeImageBackedNoiseTexture` loop, and `StateUpdateNotifiersTest` pins normal/specular update publishing. `ChannelMipmapGeneratorTest`, `LabPBRTextureFormatTest`, `PBRAtlasTextureTest`, and `AtlasPBRLoaderTest` pin the headless-safe atlas PBR helpers: ARGB mip blending, LabPBR specular generator selection, default atlas fill conversion, atlas resource path derivation, frame-size handling, and integer upscale behavior. `TextureInfoCacheTest` pins cached level-0 texture upload metadata, direct-GL bridge metadata, non-2D target filtering, and cache clearing on texture deletion. `TextureLifecycleTrackerSourceTest` pins that every owned direct `GL11.glTexImage2D(...)` and `GL11.glDeleteTextures(...)` caller in `src/main/java` notifies the lifecycle tracker. `GlStateManagerStateMixinSourceTest` pins the source-level bind-tracking guard that keeps PBR updates on base texture unit `0` and restores the original binding. `GlStateManagerTextureLifecycleMixinSourceTest` pins low-level `GlStateManager.glTexImage2D(...)` metadata capture plus `GlStateManager.deleteTexture(int)` cache/PBR cleanup at method tail. `ShaderWorldRenderingPipelineSourceTest` pins the 1.16.5-style world-rendering scope guard for PBR bind updates. `OculusRuntimeValidationTest` pins the disabled-by-default PBR telemetry switch. These are still unit/source tests; they do not prove actual `_n` / `_s` resource upload, in-client sampler output, or texture-size uniform timing under real render passes.

`SimplePBRLoaderGlTest` is an opt-in GL upload harness for simple `_n` / `_s` companion textures. It is included in the command above to compile and report its default skip state, but it only runs when launched with `-Doculus.tests.pbrGl=true`. On the current machine, direct Pbuffer creation blocks in `sun.awt.X11GraphicsEnvironment.initDisplay`, so do not enable it unless a working display or virtual display is available.

Avoid unbounded plain-JUnit tests that instantiate vanilla `TextureAtlasSprite` or atlas loading internals. A deeper in-memory atlas-loader test hung under Gradle during this slice; use `timeout` or a client/integration harness until that initialization behavior is isolated.

## Custom Image Guard

For changes to `image.*` allocation, custom image sampler/image binding, or fallback clear buffer sizing, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.gl.OculusRenderSystemCapabilityDispatchTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.gl.image.ImageLimitsTest --stacktrace
```

`CustomImageManagerTest` pins the headless byte-size helper used by the `glTexSubImage2D` / `glTexSubImage3D` fallback clear path. `ProgramImagesTest` pins the source-backed image-unit boundary: missing image uniforms can no-op without image support, but active image uniforms fail fast when image units are unsupported or exhausted. `OculusRenderSystemCapabilityDispatchTest` pins that accelerated `glClearTexImage` dispatch lives in `OculusRenderSystem` and the image manager does not probe `GLContext` directly. Scalar pixel types still multiply component count by component size, while packed OpenGL pixel types use the packed byte/short/int width directly. This does not prove live `glClearTexImage`, image load/store writes, sampler output, or framebuffer-size resize behavior.

## SSBO Guard

For changes to `bufferObject.*` parsing, SSBO allocation, or SSBO binding retry behavior, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.buffer.ShaderStorageBufferManagerTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --tests net.oculus.gl.OculusRenderSystemCapabilityDispatchTest --stacktrace
```

`ShaderStorageBufferManagerTest` pins that a temporary unsupported capability probe does not permanently initialize requested SSBO declarations. `ShaderPackLoaderComplementaryTest` proves the local Complementary `bufferObject.0` metadata still survives preprocessing and parsing. `OculusRenderSystemCapabilityDispatchTest` pins that accelerated `glClearBufferData` dispatch lives in `OculusRenderSystem` and the SSBO manager does not probe `GLContext` directly. These tests do not allocate live GL SSBOs or prove shader-visible buffer contents.

## Config-To-Pipeline Guard

For startup config, reload keybind, or toggle keybind changes, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.client.ShaderPackReloaderTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.FixedFunctionWorldRenderingPipelineTest --stacktrace
```

This guard uses a real temporary shaderpack directory and proves that enabled config applies an external pack to `PipelineManager`, disabled config clears that active pack back to `ShaderPack.internal()` without deleting the saved selection, enabled config without a selected pack does not leave a stale shader pipeline active, and a failed configured-pack load clears any stale external active pack. The fixed-function source guard proves the disabled/fallback pipeline rebinds the main Minecraft framebuffer before clearing the active program, matching the local 1.16.5 fallback frame boundary. `ShaderPackReloaderTest` intentionally sets `oculus.disableGlStringProbes=true` and `oculus.disableGlCapabilityProbes=true`; without those guards, headless Gradle workers can block in LWJGL `GL11.glGetString(...)` while loading the temporary pack. These tests do not by themselves prove the first client tick ran, that a live world created `ShaderWorldRenderingPipeline`, or that fixed-function fallback state is correct after an in-client toggle; verify that in fresh run logs after `runClient`. The `2026-05-16 15:13` smoke, now archived at `run/logs/2026-05-16-7.log.gz`, has this live proof for Complementary startup activation.

## Color-Space Conversion Guard

For color-space shader source, compute converter, or post-final color conversion changes, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.colorspace.ColorSpaceShaderSourceTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.colorspace.ColorSpaceShaderSourceTest --stacktrace
```

This pins GLSL 120 fragment fallback patching, the 1.12 fixed-function vertex adapter used by the fragment fallback, the fragment swap-FBO copyback path, GLSL 430 compute image source shape, config aliases, the source-level reference image binding and direct dispatch/unbind shape used by `ColorSpaceComputeConverter`, and the main-framebuffer-size rebuild guard in `ShaderWorldRenderingPipeline`. It does not prove runtime color-space output or resize behavior.

## Postprocess Final-Pass Guard

For final-pass state, compute ordering, fullscreen quad, center-depth cleanup, or swap-copy changes, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.postprocess.PostprocessRendererSourceTest --tests net.oculus.postprocess.FinalPassRendererSourceTest --tests net.oculus.postprocess.CenterDepthSamplerSourceTest --stacktrace
```

This pins the source-level 1.16.5 ordering where final raster rendering enters `FullScreenQuadRenderer` scope before final compute dispatch, leaves depth-test toggling to that helper, keeps final-pass `depthMask(false)`, ignores final draw-buffer and viewport-scale directives like the local 1.16.5 final renderer, preserves the baseline-copy path's depth-test state, returns center-depth sampling to the Minecraft main framebuffer boundary, keeps present composite/final raster or compute shader failures fail-fast instead of logged and skipped, keeps final program/uniform/sampler cleanup after mipmap reset and swap-copy work instead of using early `Program.unbind()`, and keeps composite draw-buffer routing owned by `RenderTargets.createColorFramebuffer(...)` instead of per-pass `glDrawBuffers(...)` calls. It does not prove visual output or GL state restoration in a live client.

## Postprocess Texture-State Guard

For postprocess render-target mipmap setup/reset, baseline copy, or `OculusRenderSystem` texture wrapper changes, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.postprocess.PostprocessRendererSourceTest --tests net.oculus.postprocess.FinalPassRendererSourceTest --tests net.oculus.gl.OculusRenderSystemCapabilityDispatchTest --stacktrace
```

This pins the source-level 1.16.5 non-DSA texture-state boundary: postprocess mipmap generation and filter updates route through `OculusRenderSystem.generateMipmaps(...)` / `texParameteri(...)`, final baseline copy routes through `OculusRenderSystem.copyTexSubImage2D(...)`, and the wrappers bind 2D textures through `GlStateManager` so Minecraft 1.12's texture cache is updated. It proves wrapper routing only; it does not prove live GL mipmap contents, final image output, or active texture state in a client.

## Custom Uniform Expression Guard

For changes to `CustomUniformExpressionManager`, custom uniform directive parsing, built-in custom-expression symbols, or same-frame uniform timing, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --stacktrace
```

This guard covers scalar/vector expression parsing, `smooth()` half-life behavior, shaderpack directive precedence over hardcoded fallback names, matrix/component access, and source-level same-frame dynamic lookup for known pass/object/fog symbols. It does not prove visual timing in real terrain, entity, block-entity, hand/item, fog, or postprocess render phases.

## Viewport Uniform Guard

For changes to `viewWidth`, `viewHeight`, `aspectRatio`, `u_ViewWidth`, `u_ViewHeight`, or `iris_ScreenSize`, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.gl.state.GameDataSuppliersTest --tests net.oculus.gl.program.InternalProgramBuilderPathTest --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --stacktrace
```

This pins the source-level 1.16.5 viewport boundary: shader-visible viewport dimensions read the Minecraft main framebuffer dimensions when available, with a 1.12 startup fallback to `displayWidth` / `displayHeight`. It does not prove live window resize behavior, scaled postprocess dimensions, or resolution-control mod compatibility.

## Camera Uniform Timing Guard

For changes to camera position capture, current/previous matrix capture, shared frame-update notifier timing, frame-start custom uniform pre-evaluation, or the `LevelRendererMixin` terrain setup redirect, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava test --tests net.oculus.uniforms.CameraPositionTrackerSourceTest --tests net.oculus.uniforms.CapturedRenderingStateTest --tests net.oculus.mixins.LevelRendererMixinSourceTest --tests net.oculus.gl.program.InternalProgramBuilderPathTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --stacktrace
```

This pins the current 1.12 camera and frame-update boundary: `beginFrame(...)` rolls previous camera/matrix state once at frame head, the shared `FrameUpdateNotifier` runs after source-built programs register non-camera smoothed built-in listeners and before render-target clears like 1.16.5 `prepareRenderTargets()`, `LevelRendererMixin` calls `PipelineManager.afterCameraSetup((float) partialTicks)` from the existing `RenderGlobal.setupTerrain(...)` redirect, `CameraPositionTracker` reads the post-setup camera through `ActiveRenderInfo.projectViewFromEntity(entity, partialTicks)`, and custom/gameplay/compatibility frame-start pre-evaluation runs after that capture. The mixin source guard intentionally rejects the direct `ActiveRenderInfo.updateRenderInfo(Entity, boolean)` target because MCP `stable_39` did not provide a stable annotation-processor mapping for that bytecode invoke. A pass here is still source/compile evidence only; verify live `cameraPosition`, split camera uniforms, previous camera values, matrices, non-camera smoothed built-ins, and camera-dependent custom uniforms in a client before claiming visual parity.

For `iris_NormalMatrix` or generated Sodium terrain matrix changes, extend the command with:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava test --tests net.oculus.gl.state.MatrixMathTest --tests net.oculus.uniforms.CapturedRenderingStateTest --tests net.oculus.gl.program.InternalProgramBuilderPathTest --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --tests net.oculus.pipeline.SodiumTerrainShaderTransformerTest --tests net.oculus.pipeline.SodiumTerrainPipelineTest --stacktrace
```

This pins the local 1.16.5 Sodium terrain matrix contract: `iris_NormalMatrix` is a `mat4` derived by inverting then transposing the active model-view matrix, both runtime uniform binding and custom matrix expressions read that same capture, and generated terrain sources consume it through `mat3(iris_NormalMatrix)`. It does not prove live Relictium terrain normal output.

## Program Builder Sampler Guard

For changes to `ProgramBuilder`, active-uniform discovery, external sampler naming, or reserved texture-unit handling, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.gl.program.InternalProgramBuilderPathTest --stacktrace
```

This source guard pins explicit internal-program construction, `atlasSize` notifier wiring, the separate `shadowFade` / `shdFade` reference fallbacks, gbuffers/shadow external level sampler handling for `tex`, `texture`, `gtexture`, and `lightmap`, implicit world reserved texture units `0`, `1`, and `2`, and the source-level automatic sampler fallback filter for low render targets plus composite-only and root-shadow-excluded depth samplers. It does not prove live texture-unit state or shader-visible sampler output in a client.

## Block Material And Render-Layer Guard

For `block.properties` material IDs, shader-pack block-state ID activation, `layer.*` render-layer overrides, `BlockRenderingSettings`, or related block-rendering mixins, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.IdMapTest --tests net.oculus.pipeline.BlockRenderingSettingsTest --tests net.oculus.pipeline.PipelineManagerSourceTest --tests net.oculus.mixins.BlockRenderLayerOverrideMixinSourceTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.pipeline.FixedFunctionWorldRenderingPipelineTest --stacktrace
```

After `compileJava`, inspect `build/resources/main/oculus.refmap.json` for `BlockRenderLayerOverrideMixin#getRenderLayer` remapping to `Block#func_180664_k()`. The `canRenderInLayer` injection intentionally does not appear in the refmap because Forge adds `Block.canRenderInLayer(IBlockState, BlockRenderLayer)` outside MCP `stable_39`, so the mixin uses `remap = false`.

This guard proves the parser recognizes Complementary-style `layer.translucent=glass glass_pane beacon`, shader-pack block-state ID maps stay active without treating an empty map as vanilla fallback, the runtime settings object stores and clears override maps, shader-pack reload publishes or clears block-state IDs, entity IDs, and render-layer overrides before destroying old pipelines, shader pipelines install the parsed map, fixed-function fallback clears it, and the mixin is queued. It does not prove live chunk rebuilds, shader-visible terrain `blockId` attributes, entity/block-entity ID uniforms, or rendered translucent output; validate material IDs plus glass, glass panes, beacon blocks, and entity-ID debug output in a real client pass before claiming visual parity.

## Relictium Terrain Bridge Guard

For changes to `SodiumTerrainPipeline`, `SodiumTerrainShaderTransformer`, or the Relictium terrain override classes, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.SodiumTerrainShaderTransformerTest --tests net.oculus.pipeline.SodiumTerrainPipelineTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.SodiumTerrainPipelineGlCompileTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.compat.relictium.OculusTerrainPassTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.compat.relictium.RelictiumTerrainBridgeBytecodeTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.ShadowRenderDirectiveTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.ShadowCullingModeTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.shadow.ShadowRendererSourceTest --tests net.oculus.pipeline.shadow.ShadowRendererBytecodeTest --tests net.oculus.pipeline.shadow.ShadowMapBytecodeTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava --stacktrace
```

These tests prove source selection, fallback naming, injected terrain declarations, fixed-function replacements, comment/string/preprocessor safety, Relictium pass-to-phase mapping, and real Complementary zip source generation through the common shader preparation path when `run/shaderpacks/ComplementaryReimagined_r5.6.1.zip` exists. They also pin the local 1.16.5 `TransformPatcher` internal-interface guard for Sodium terrain sources: pack code that already references `iris_*` or `irisMain` must throw the reference diagnostic before generated declarations are injected, while comments, strings, and preprocessor lines remain ignored by the local scanner. Generated declarations are intentionally injected without duplicate suppression, matching the local 1.16.5 `SodiumTerrainTransformer`; tests should keep expecting duplicate non-internal names if a pack predeclares names such as `u_ModelScale`. The same suite guards that `_sodium` source preparation does not run the world/root-shadow `AttributeTransformer` branch, so Sodium terrain sources must not receive `iris_TextureMatrix[8]` or `iris_ONE_OVER_256` declarations. The Complementary terrain guard must keep checking the shadow-enabled path too: declarations must be ordered, visible outside leading block comments, and before real uses in solid, translucent, and shadow terrain vertex sources. `ShadowCullingModeTest` pins exact lowercase `shadow.culling=true|false|reversed` parsing and the 1.16.5-style case-sensitive boolean boundary. This prevents the runtime `_sodium.vsh` undeclared-symbol failure where generated declarations were present in Java strings but hidden from GLSL inside Complementary's banner comment.

`SodiumTerrainPipelineGlCompileTest` is opt-in. The command above compiles the test and should skip before touching LWJGL, which prevents dead-display hangs in normal verification. To run the actual GL compiler proof, add `-Doculus.tests.sodiumTerrainGl=true` before `-cp` only when a working display or virtual display is available. When enabled, the test checks Linux display variables before active `Pbuffer` use, loads the Complementary zip with runtime terrain options, builds solid, translucent, and shadow `_sodium` sources, binds the Relictium/Oculus terrain attribute locations, and asks OpenGL to compile and link those generated programs. A pass proves the generated sources are accepted by the local GL compiler/linker, but it still does not prove Relictium created or used those override programs in an actual world, nor that runtime attributes, samplers, images, uniforms, and GL state are correct.

For unattended in-world terrain validation, `OculusRuntimeValidation` is available but disabled unless explicit JVM properties are present. `runClient` forwards `oculus.validation.autoJoinWorld`, `oculus.validation.exitAfterWorldTicks`, `oculus.validation.pbrTextures`, `oculus.validation.screenshotWorldTick`, `oculus.validation.dumpRenderTargets`, `oculus.validation.dumpRenderTargetsWorldTick`, and `oculus.validation.thirdPersonView` into the launched Minecraft JVM. The hook waits for the main menu, resolves the requested world by exact folder name or unique display name through `ISaveFormat.getSaveList()`, refuses version-warning saves, verifies `canLoadWorld(...)`, and then uses the exact Forge 1.12 GUI world-load path: `FMLClientHandler.instance().tryLoadExistingWorld(GuiWorldSelection, WorldSummary)`. `oculus.validation.pbrTextures=true` can be used with or without auto-join; when enabled it logs real simple PBR texture loads, atlas PBR uploads, and resolved normal/specular holder texture IDs from the runtime load paths. `oculus.validation.thirdPersonView=1` forces third-person back view before capture, which is useful for entity/player gbuffer validation. Example:

```bash
timeout 480s /usr/bin/zsh -lc 'JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -Doculus.validation.autoJoinWorld="New World" -Doculus.validation.exitAfterWorldTicks=1200 -Doculus.validation.pbrTextures=true -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient --stacktrace'
```

The local `run/resourcepacks/Oculus-PBR-Validation` directory is a tiny LabPBR resource pack for this proof path. It contains `assets/minecraft/optifine/texture.properties` with `format=lab-pbr/1.3.0` plus `textures/blocks/dirt_n.png` and `textures/blocks/dirt_s.png`, and `run/options.txt` currently enables it through `resourcePacks:["Oculus-PBR-Validation"]`. The `2026-05-16 22:18` attempt with that pack and `oculus.validation.pbrTextures=true` did not reach resource loading: `jstack` showed the main thread blocked in `sun.awt.X11GraphicsEnvironment.initDisplay` through LWJGL `Sys.<clinit>`. A fresh `2026-05-17 00:07` attempt with `DISPLAY=:0`, `autoJoinWorld="New World"`, `exitAfterWorldTicks=900`, and `pbrTextures=true` reached early Minecraft/mixin startup but again stopped before Forge mod loading; `jstack` showed the same `sun.awt.X11GraphicsEnvironment.initDisplay` path through LWJGL `Sys.<clinit>`, and no `xvfb-run` / `Xvfb` binary was available. Treat the pack setup as ready, but do not treat PBR runtime resource loading as proven.

The `2026-05-16 19:26` run with that command exited with Gradle code `0`. Fresh `run/logs/latest.log` selected `ComplementaryReimagined_r5.6.1.zip`, auto-joined `New World`, entered the world, initialized `ShaderWorldRenderingPipeline`, compiled 27 shader programs, created Relictium terrain overrides for `SHADOW`, `GBUFFER_SOLID`, and `GBUFFER_TRANSLUCENT` from `shadow_sodium`, `gbuffers_terrain_sodium`, and `gbuffers_water_sodium`, then requested client shutdown after 1200 in-world client ticks. The `_sodium` shader logs contained only local compiler warnings about uninitialized variables, not the previous undeclared Iris/Relictium symbol failures.

The `2026-05-19` hand/depth validation runs used the same world with `ComplementaryReimagined_r5.6.1.zip`, render-target dumping, screenshots at tick 220, and shutdown at tick 280. The first-person run compiled and loaded 144 shader program objects with no unspecialized programs, proved `pre-composite-depthtex0` was no longer all-clear after the early shader hand fix, and showed full-color pre-deferred terrain while final composite remained foggy/grey. The third-person run added `-Doculus.validation.thirdPersonView=1`, logged `Oculus runtime validation set thirdPersonView=1`, compiled and loaded the same 144 program objects, and showed that the player body problem was already visible before deferred/composite. Keep using this dump pattern when separating gbuffer/entity bugs from later deferred/composite color bugs:

```bash
timeout 720s /usr/bin/zsh -lc 'JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -Doculus.validation.autoJoinWorld="New World" -Doculus.validation.dumpRenderTargets=true -Doculus.validation.dumpRenderTargetsWorldTick=220 -Doculus.validation.screenshotWorldTick=220 -Doculus.validation.exitAfterWorldTicks=280 -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient --stacktrace'
timeout 720s /usr/bin/zsh -lc 'JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -Doculus.validation.autoJoinWorld="New World" -Doculus.validation.thirdPersonView=1 -Doculus.validation.dumpRenderTargets=true -Doculus.validation.dumpRenderTargetsWorldTick=220 -Doculus.validation.screenshotWorldTick=220 -Doculus.validation.exitAfterWorldTicks=280 -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient --stacktrace'
```

After adding validation-only active-program selection telemetry, the `2026-05-16 19:35` run with `exitAfterWorldTicks=900` also exited with Gradle code `0`. Fresh `run/logs/latest.log` created the same Relictium overrides and then logged actual `activeProgram` replacement for `SOLID` / `TERRAIN_SOLID`, `CUTOUT_MIPPED` / `TERRAIN_CUTOUT_MIPPED`, and `CUTOUT` / `TERRAIN_CUTOUT` through `oculus:relictium-terrain-gbuffer_solid`, plus `TRANSLUCENT` / `TERRAIN_TRANSLUCENT` through `oculus:relictium-terrain-gbuffer_translucent`. The scan did not show shader compilation failures, program link failures, undeclared generated-symbol failures, `GL_INVALID`, or `1282` entries. A diagnostic `2026-05-16 19:46` run then proved `ShadowRenderer` initialized but did not request shadow terrain layers while shadow rendering lived in a separate setup-terrain `@Inject`.

After moving shadow rendering into the existing `RenderGlobal.setupTerrain(...)` redirect, the `2026-05-16 19:49` run with `exitAfterWorldTicks=900` exited with Gradle code `0`. Fresh `run/logs/latest.log` proves shadow terrain layer requests for `SOLID`, `CUTOUT_MIPPED`, `CUTOUT`, and `TRANSLUCENT`; available `SHADOW` Relictium override lookups for each `BlockRenderPass`; and actual `activeProgram` replacement through `oculus:relictium-terrain-shadow` for each shadow terrain layer. The same run still selected the expected gbuffer solid/translucent overrides for main terrain and did not show shader compilation failures, program link failures, undeclared generated-symbol failures, `GL_INVALID`, or `1282` entries. This is still log-visible execution evidence, not visual shadow correctness.

The next Relictium shadow audit ported the 1.16.5-style separate shadow visibility graph for installed Relictium and added validation-only shadow depth sampling. A runtime attempt around `2026-05-16 20:03` proved the new hooks fired (`swapped to shadow graph`, `forced shadow graph rebuild`, `restored camera graph`) and still selected the shadow terrain program, but the first depth readback was all clear (`nonClear=0`, min/max depth `1.0`). That attempt later logged repeated `1282` errors while the older `glReadPixels(...)` validation readback was active. The readback path now uses `glGetTexImage(...)` and drains GL errors, but later runs did not reach Minecraft: `jstack` showed startup stuck in `sun.awt.X11GraphicsEnvironment.initDisplay` through LWJGL/Minecraft initialization. The `2026-05-17 00:07` retry confirmed the blocker is still current on this machine. Treat the graph-swap code as compiled and bytecode-guarded, but do not treat shadow depth output as proven.

`RelictiumTerrainBridgeBytecodeTest` proves the installed Relictium jar still has the bytecode shape targeted by the remap=false terrain bridge mixins: `ChunkRenderManager.renderLayer(...)` still calls `ChunkRenderBackend.begin()V`, then `ChunkRenderBackend.render(...)`, then a later `ChunkRenderBackend.end()V`; `ChunkRenderShaderBackend.begin()` still writes `activeProgram` before `ChunkProgram.bind()` / `setup(float,float)`; the descriptors used by the constructor/create/end/delete injectors and pass selection still match; and the `ChunkRenderManager` / `SodiumWorldRenderer` fields and call sites used by the shadow visibility graph swap layer still exist. `ShadowRendererBytecodeTest` proves the vanilla 1.12 shadow setup path marks `RenderGlobal.displayListEntitiesDirty` before calling `setupTerrain(...)`, matching the local 1.16.5 shadow renderer's pre-setup `needsUpdate()` behavior, keeps the post-shadow dirty mark for the main camera graph, verifies that the installed 1.12 `RenderGlobal` bytecode still uses `displayListEntitiesDirty` to rebuild `renderInfos` during `setupTerrain(...)`, pins the shadow terrain layer order as `SOLID`, `CUTOUT`, `CUTOUT_MIPPED`, then `TRANSLUCENT`, verifies that shadow target preparation restores `OpenGlHelper.defaultTexUnit` before framebuffer work, verifies that shadow terrain restores `OpenGlHelper.defaultTexUnit` before binding the block atlas, and verifies that the shadow pass saves/restores depth-test enablement, depth func, and depth write mask around its legacy GL state changes. `ShadowRendererSourceTest` proves root shadow raster ownership stays in `ShaderLoader` / `ShaderWorldRenderingPipeline` instead of `ShadowRenderer`, proves shadow compute compilation failures remain fail-fast `ProgramLoadException` paths instead of the old log-and-skip behavior, and proves depth clear, shadow compute dispatch/barrier, and shadow color clear live in `prepareRenderTargets()` outside `renderShadows(...)`. `ShaderWorldRenderingPipelineSourceTest` proves that shadow target preparation is guarded once per frame, runs after the 1.12 post-camera custom-uniform frame setup, and runs before `prepareBeforeShadow` and root shadow raster sync. `ShadowMapBytecodeTest` proves the 1.12 no-translucents shadow depth-copy fallback saves the active `GL_TEXTURE_2D` binding, performs `glCopyTexSubImage2D`, and restores the saved binding instead of leaving texture 2D unbound for translucent shadow rendering. It also proves shadow mipmap generation switches to a dedicated texture unit, restores that unit's previous 2D binding, and restores the previously active texture unit after mipmap generation. `ShadowSamplerBindingsTest` proves the no-GL shadow target trigger surface used for lazy allocation, including `watershadow`, `s_shadow`, hardware aliases, color samplers, and `shadowcolorimg*`. `ShaderWorldRenderingPipelineSourceTest` also proves the disabled-shadow branch calls `clearDisabledShadowTargets()` before the main render-target null guard and routes it to `ShadowMap.clearFullColorBuffersIfRequired()`. `OculusTerrainPassTest` only proves Java mapping from `BlockRenderPass` to terrain shader pass and `WorldRenderingPhase`. None of these are rendering tests.

If `SodiumTerrainPipelineTest` appears to hang in `complementaryZipProducesPreparedSodiumTerrainSources`, inspect the worker stack before killing it. A previous whole-source declaration regex in `SodiumTerrainShaderTransformer.hasDeclaration(...)` catastrophically backtracked on Complementary; generated declaration dedupe is now removed rather than scanner-backed, while token scanners remain for validation and replacement safety. A later headless run also proved this test can block in `sun.awt.X11GraphicsEnvironment.initDisplay` through `StandardMacros.getGlString(...)` if GL string/capability probes are not disabled, so the test class now sets `oculus.disableGlStringProbes=true` and `oculus.disableGlCapabilityProbes=true` around its real-pack source checks.

## Mixin Remap Checks

For new or changed mixins, inspect the generated refmap after `compileJava`:

```bash
rg "<MixinName>|<targetMethod>" build/resources/main/oculus.refmap.json build/tmp/compileJava/mcp-srg.srg build/tmp/compileJava/mcp-notch.srg
```

This is especially important for 1.12.2 MCP names. A Java compile can pass while a runtime mixin still fails if an injector descriptor, owner, or `remap` setting is wrong.

Current shader directive hook examples that should appear after relevant changes are `GameSettingsCloudsMixin` targeting `shouldRenderClouds()I`, `GuiIngameOverlayMixin` targeting `renderVignette(FLnet/minecraft/client/gui/ScaledResolution;)V`, `ItemRendererOverlayMixin` targeting `renderWaterOverlayTexture(F)V`, and `WorldRendererMixin` targeting `renderSky(FI)V` slices around `SUN_TEXTURES`, `MOON_PHASES_TEXTURES`, `Tessellator.draw()V`, `WorldClient.getStarBrightness(F)F`, and the second `WorldClient.getCelestialAngle(F)F` call used for the live `sunPathRotation` sky tilt.

For `remap=false` Relictium mixins, the refmap will not contain target mappings. Verify the owner, method name, and invoke descriptor directly against the installed Relictium jar:

```bash
javap -classpath /home/daniel/.gradle/caches/minecraft/deobfedDeps/deobf/maven/modrinth/relictium/1.2.0/relictium-1.2.0.jar -c -p me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer
javap -classpath /home/daniel/.gradle/caches/minecraft/deobfedDeps/deobf/maven/modrinth/relictium/1.2.0/relictium-1.2.0.jar -c -p me.jellysquid.mods.sodium.client.render.pipeline.FluidRenderer
javap -classpath /home/daniel/.gradle/caches/minecraft/deobfedDeps/deobf/maven/modrinth/relictium/1.2.0/relictium-1.2.0.jar -c -p me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager
javap -classpath /home/daniel/.gradle/caches/minecraft/deobfedDeps/deobf/maven/modrinth/relictium/1.2.0/relictium-1.2.0.jar -c -p me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer
javap -classpath /home/daniel/.gradle/caches/minecraft/deobfedDeps/deobf/maven/modrinth/relictium/1.2.0/relictium-1.2.0.jar -c -p me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkRenderShaderBackend
javap -classpath /home/daniel/.gradle/caches/minecraft/deobfedDeps/deobf/maven/modrinth/relictium/1.2.0/relictium-1.2.0.jar -p -s me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram
```

Before using an old `net.coderbot` mixin config or modern `com.mojang.*` shim as evidence, read `docs/legacy-shims-and-remnants.md`. The current Gradle client run queues `oculus.mixins.json` through the early Oculus coremod; extra mixin JSON resources are not proof of active runtime behavior.

## Mixin Bootstrap Guard

Oculus mixins must be queued through the early Forge coremod path. `BlockStateContainer$StateImplementation` is loaded too early for the old late `@MixinLoader` / `ILateMixinLoader` route, and that caused a real startup crash on `2026-05-16`.

Expected current shape:

- `build.gradle` jar manifest declares `FMLCorePlugin: net.oculus.mixins.OculusMixinLoader`, `FMLCorePluginContainsFMLMod: true`, and `ForceLoadAsMod: true`.
- `OculusMixinLoader` implements `IFMLLoadingPlugin` and `IEarlyMixinLoader`.
- `OculusMixinLoader` does not implement `ILateMixinLoader` and is not annotated with `@MixinLoader`.
- `Oculus.onConstruction(...)` does not call `Mixins.addConfiguration("oculus.mixins.json")`.
- `runClient` does not rely on `-Dmixin.configs=oculus.mixins.json`.

Run the focused guard after changing mixin bootstrapping:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.mixins.OculusMixinLoaderTest --stacktrace
```

Fresh runtime logs should show MixinBooter adding `oculus.mixins.json` while grabbing `net.oculus.mixins.OculusMixinLoader` for early mixins, before late `MixinLoader` / `ILateMixinLoader` discovery.

## ID-Map Uniform Guard

For changes to `item.properties`, `entity.properties`, held item IDs, entity IDs, or the active ID-map runtime owner, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.BlockRenderingSettingsTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.pipeline.FixedFunctionWorldRenderingPipelineTest --tests net.oculus.uniforms.IdMapUniformsTest --stacktrace
```

This proves held item aliases still resolve exact names first, entity IDs are read from `BlockRenderingSettings#getEntityIds()` instead of directly from `PipelineManager`'s active pack, block-entity IDs are read from `BlockRenderingSettings#getBlockStateIds()` instead of directly from the active pack, shader pipelines install the active `entity.properties` map, and fixed-function fallback clears it. It does not prove live `heldItemId`, `entityId`, or `blockEntityId` values in a rendered world.

## Reference Uniform Name Guard

After changing `ProgramBuilder` built-in uniform registration, run:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace
```

This guards the active local 1.16.5 built-in uniform name surface against accidental case removal. It does not prove type, update-frequency, notifier timing, or runtime values.

## Runtime Validation Needed For Production Confidence

Compile/build only proves Java and mixin annotation processing. For rendering work, also verify in a 1.12.2 client:

Historical local runtime blocker from earlier `2026-05-17` attempts: bounded `runClient` attempts with `ComplementaryReimagined_r5.6.1.zip` selected blocked in `sun.awt.X11GraphicsEnvironment.initDisplay` through LWJGL `Sys` before Minecraft startup. Later Xvfb and user real-display runs reached world/shaderpack execution, so do not treat that old X11 startup failure as the current blocker without a fresh log. For the latest manual MakeUp/Complementary diagnosis, read `docs/runtime-diagnosis-2026-05-17.md`.

Latest 2026-05-20 Complementary visual validation: the grey/foggy atlas-smeared output reproduced at `run/screenshots/oculus-validation-1779243952666-tick-220.png` was fixed by the active texture-unit cache correction in `OculusRenderSystem.setActiveTextureUnit(...)`. Re-run this exact shape when touching fullscreen render-target samplers or texture-unit restoration:

```bash
timeout 420s /usr/bin/zsh -lc 'JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -Doculus.validation.autoJoinWorld="New World" -Doculus.validation.thirdPersonView=2 -Doculus.validation.worldTime=6000 -Doculus.validation.screenshotWorldTick=220 -Doculus.validation.exitAfterWorldTicks=250 -Doculus.validation.dumpRenderTargets=true -Doculus.validation.dumpRenderTargetsWorldTick=220 -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient --stacktrace'
```

Expected evidence after the fix:
- `run/logs/latest.log` reports `deferred1 colortex0 unit=0 texture=52` and `composite1 colortex0 unit=0 texture=53`, not `texture=8` for the postprocess `colortex0` sampler.
- `run/screenshots/oculus-validation-1779244370267-tick-220.png` is a colored shader-lit scene with no atlas overlay and no grey wash.
- This proves the specific sampler/monochrome issue for the captured scene only. It does not prove third-person player-body visibility, reload behavior, resize behavior, water/translucent parity, PBR/resource-pack behavior, or full shader-pack parity.

Latest 2026-05-20 entity/player draw-state validation: MakeUp and Complementary both enter a world and keep the local player main model on `gbuffers_entities` before and after the base model draw. Use `JAVA_TOOL_OPTIONS` for MakeUp when launching through Gradle because the dev client is forked:

```bash
JAVA_TOOL_OPTIONS='-Doculus.validation.shaderPack=MakeUp-UltraFast-9.3e.zip' JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH timeout 360s java -Doculus.validation.autoJoinWorld="New World" -Doculus.validation.thirdPersonView=1 -Doculus.validation.worldTime=6000 -Doculus.validation.screenshotWorldTick=40 -Doculus.validation.exitAfterWorldTicks=70 -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --max-workers=1 runClient --stacktrace
```

Expected MakeUp evidence:
- `Using configured shader pack: MakeUp-UltraFast-9.3e.zip`
- `Compiled 152 shader program object(s)` and `Loaded 152 shader program object(s); 0 unspecialized program(s): []`
- `legacy entity program sync bound ... condition=ENTITIES ... program=gbuffers_entities`
- `local player model pass=main normal-draw-state ... glProgram=<same expected entity program>`
- `local player model pass=main normal-after ... glProgram=<same expected entity program>`

Complementary can use the configured pack path without `JAVA_TOOL_OPTIONS`:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH timeout 420s java -Doculus.validation.autoJoinWorld="New World" -Doculus.validation.thirdPersonView=1 -Doculus.validation.worldTime=6000 -Doculus.validation.screenshotWorldTick=80 -Doculus.validation.exitAfterWorldTicks=120 -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --max-workers=1 runClient --stacktrace
```

This proves entity program handoff for the logged local-player draw only. It does not prove shadow visual parity, inventory GUI scale, every entity layer, or all shader-pack options.

1. Launch with the target shader pack under `run/shaderpacks`. A startup-only smoke test can use:

```bash
timeout 90s /usr/bin/zsh -lc 'JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient --stacktrace'
```

A bounded world-load smoke can use the same command with a longer timeout. It still requires log inspection because timeout exit code `124` only means the run was intentionally bounded:

```bash
timeout 240s /usr/bin/zsh -lc 'JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient --stacktrace'
```

2. Confirm the fresh log no longer reports `BlockStateAmbientOcclusionMixin target ... was loaded too early`, and that Forge reaches `Forge Mod Loader has successfully loaded 7 mods`.
   The `2026-05-16 10:01` bounded smoke run reached this point with `shadersEnabled=true` and `selectedPackName=ComplementaryReimagined_r5.6.1`, then exited with timeout code `124` because the run was deliberately bounded. Treat that as startup evidence only; it did not enter a world.
3. Confirm config activation before world rendering. With `run/config/oculus.properties` set to `shadersEnabled=true` and `selectedPackName=<pack>`, fresh logs should show the configured pack being loaded by `ShaderPackReloader` before or during the first world entry. If world entry creates `FixedFunctionWorldRenderingPipeline` while config names a valid enabled pack, treat that as a config-to-pipeline regression.
4. Enter a world and enable the pack through the Oculus shader GUI if it is not already active.
5. Check logs for shader compile failures, missing uniforms/samplers/images, framebuffer incompleteness, and GL errors.
   The `2026-05-16 15:13` world-load smoke, now archived at `run/logs/2026-05-16-7.log.gz`, entered `New World` with Complementary enabled, applied the configured extracted pack at startup, created `ShaderWorldRenderingPipeline`, compiled 27 shader programs, initialized the 2048 shadow renderer, created 7 postprocess passes, and compiled the final pass. The same run later rebuilt the pipeline for `ComplementaryReimagined_r5.6.1.zip` and compiled 27 programs there too. That log no longer reports the previous `gbuffers_line.vsh` `vec4(vaPosition, 1.0)` compile failure, `Shadow framebuffer incomplete: 36055`, repeated `1282: Invalid operation` errors, or the previous internal `centerDepthSmooth` unknown sampler/uniform warnings.
	   Later `2026-05-16 17:28` and `2026-05-16 19:05` bounded runs were only startup evidence for the current Relictium terrain bridge code: they selected `ComplementaryReimagined_r5.6.1.zip`, but did not enter a world. The later `2026-05-16 19:26` unattended validation run selected the Complementary zip, auto-joined `New World`, initialized `ShaderWorldRenderingPipeline`, compiled 27 normal shader programs, and created SHADOW, GBUFFER_SOLID, and GBUFFER_TRANSLUCENT Relictium terrain overrides from the generated `_sodium` programs before a clean validation-triggered shutdown. The `2026-05-16 19:35` unattended validation run additionally proved actual Relictium active-program override selection for solid, cutout-mipped, cutout, and translucent main terrain passes. The `2026-05-16 19:49` unattended validation run proved shadow terrain layer requests and `oculus:relictium-terrain-shadow` active-program override selection for solid, cutout-mipped, cutout, and translucent shadow terrain. A later shadow visibility graph run proved the graph-swap hooks fire but sampled all-clear depth once and then hit validation-related `1282` noise; subsequent reruns blocked in X11 display initialization before Minecraft startup. The `2026-05-17` thread dump pins that blocker to `sun.awt.X11GraphicsEnvironment.initDisplay` before shaderpack loading. Keep the distinction clear: this proves in-client compile/link, override creation, and Relictium active-program selection for main and shadow terrain, plus compiled graph-swap coverage, but still does not prove visual parity, non-clear shadow output, or every runtime binding/state behavior.
   Current shader-pipeline warnings are source-backed compatibility telemetry: unused-function cleanup now names the first function/stage and omitted count, and the lightmap texture matrix warning matches the 1.16.5 `BuiltinReplacementUniforms` warning. A fresh broad warning scan of the latest log did not show the older Mesa/GLSL uninitialized-variable compiler warnings.
6. Capture screenshots in overworld, nether, end, water/translucent scenes, weather, entities, block entities, particles, hand, beacon beam, and shadows. Water/translucent and hand scenes are also the runtime checks for the source-backed pre-translucent and pre-hand depth-copy binding boundaries in `RenderTargets.copyDepth(...)`.
7. Toggle shader options that affect `shaders.properties` preprocessing and `program.*.enabled`.
8. Hold or render items and entities that exercise shader-pack ID maps on 1.12 registries, especially jack-o-lantern and magma block with Complementary's `item.properties`; check that `heldItemId`, `heldItemId2`, `heldBlockLightValue`, `heldBlockLightValue2`, and `entityId` behave without missing-ID regressions.
9. Inspect or debug material IDs for blocks covered by modern Complementary `block.properties` predicates on 1.12 split registries: lit and unlit furnace, redstone ore, redstone lamp, redstone torch, snow layers, and full snow block.
10. Exercise Complementary custom-uniform expressions that use `smooth()`, especially startup/motion expressions with zero half-life, and compare visible timing against 1.16.5 behavior. Include the duplicate explicit-ID pairs documented in `docs/custom-uniform-smooth-semantics.md`: `eyeBrightnessM` / `eyeBrightnessM2` with ID `4`, and `inSoulValley` / `inPaleGarden` with ID `54`.
11. For source transform work, inspect the preprocessed/compiled shader log or dumped shader source where available. Confirm JCPP has hoisted active `#version` / `#extension` directives before later source transforms run, confirm postprocess `uniform float centerDepthSmooth;` became the `iris_centerDepthSmooth` sampler path, confirm GLSL 120 postprocess sources using `texture2DLod` or `texture3DLod` receive `#extension GL_ARB_shader_texture_lod : require` even when a pack already declared that extension, confirm `gbuffers_*` and root shadow programs use availability-specific linked variants, confirm lightmap-present 1.12 variants alias `gl_MultiTexCoord2` to `gl_MultiTexCoord1`, confirm missing texture/lightmap variants use `vec4(240.0, 240.0, 0.0, 1.0)` for unavailable legacy coordinates, confirm non-core availability variants inject `iris_TextureMatrix[8]` with the lightmap entry sourced from `gl_TextureMatrix[1]` and rename original `gl_TextureMatrix` references when present, confirm core-profile vertex availability sources fail with `Vertex shaders must be in the compatibility profile to run properly!`, confirm core-profile non-vertex availability sources do not receive those attribute rewrites, and confirm non-core vertex sources that use `gl_MultiTexCoord3` but not `mc_midTexCoord` are patched to the bound `mc_midTexCoord` attribute. Do not broaden the center-depth or texture-LOD transforms to `gbuffers_*`/root `shadow`, or the remaining attribute transforms to all programs, unless separate reference and 1.12 vertex-data evidence requires it.
12. For Relictium terrain source-transform work, enter a world or use the explicit runtime-validation command above and check that `shadow_sodium.vsh`, `gbuffers_terrain_sodium.vsh`, and `gbuffers_water_sodium.vsh` do not report undeclared generated symbols such as `iris_LightmapTextureMatrix`, `iris_LightTexCoord`, `iris_TexCoord`, `u_TextureScale`, `iris_NormalMatrix`, `iris_Normal`, `iris_ModelViewMatrix`, `iris_Pos`, `u_ModelScale`, or `iris_ModelOffset`. Source tests catch hidden/reversed declarations, direct no-dedup generated declaration injection like the reference, the reference-style `iris_*` / `irisMain` internal-interface guard, and the reference-style missing or malformed numeric `#version` failure before Sodium terrain declarations are injected. `SodiumTerrainPipelineGlCompileTest` can prove local GL compile/link outside Minecraft only when explicitly launched with `-Doculus.tests.sodiumTerrainGl=true`, the `2026-05-16 19:26` in-world run proves Relictium creates the linked override programs, the `2026-05-16 19:35` run proves active-program override selection for solid, cutout-mipped, cutout, and translucent main terrain, and the `2026-05-16 19:49` run proves shadow terrain layer requests plus shadow active-program override selection. The newer shadow visibility graph mixins, vanilla pre-shadow dirty signal, shadow target-prep default texture-unit restoration, shadow layer order, no-translucents depth-copy 2D texture binding restoration, shadow mipmap texture-unit isolation, and shadow depth-state restoration are bytecode-guarded, but visual terrain correctness, attribute values, sampler/image bindings, translucent rendering, non-clear shadow depth output, final shadow visuals, and broader GL state cleanup still need dedicated validation.
13. Test `colorSpace=SRGB`, `DISPLAY_P3`, `REC2020`, and `ADOBE_RGB` through the shader pack screen selector and by editing `config/oculus.properties`. Use one pack with `supportsColorCorrection=true` and one without it, then check that only the correct path applies an Oculus post-final transform. For the fragment fallback path, also verify that post-final copyback does not leave texture unit 0's `GL_TEXTURE_2D` binding cleared after `glCopyTexSubImage2D`.
14. Resize the client window at least once while shaders are enabled, then re-check logs for stale depth attachments, framebuffer incompleteness, and render-target resize errors.
15. Compare behavior with the 1.16.5 reference where the engine difference does not make parity impossible.

Expected non-fatal dev-run noise currently includes Forge/ASM class-file read warnings for local dependency jars, Log4j console-appender/class-not-found messages, narrator native-library warnings, and Realms authentication warnings. Do not count those as shader pipeline blockers unless they start preventing startup or world entry. Do not lump shader compiler warnings into this dev-run-noise category; they need a source-backed triage decision. If `centerDepthSmooth` unknown sampler/uniform warnings return after the explicit-builder fix, treat that as a regression.

## Framebuffer Compatibility Guard

The active 1.12 framebuffer paths should not call `GL30.glBindFramebuffer`, `GL30.glGenFramebuffers`, `GL30.glFramebufferTexture2D`, `GL30.glCheckFramebufferStatus`, or `GL30.glDeleteFramebuffers` directly. Use `OpenGlHelper` so Minecraft's selected BASE/ARB/EXT framebuffer backend owns the call. Active copy paths should bind with `OpenGlHelper.GL_FRAMEBUFFER`, not split `GL30.GL_READ_FRAMEBUFFER` / `GL30.GL_DRAW_FRAMEBUFFER` targets.

Run the focused guard after framebuffer, render-target, postprocess, color-space, center-depth, or shadow changes:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.gl.FramebufferCompatibilityTest --stacktrace
```

## Shader Pack Targets

Current local targets:

- `run/shaderpacks/ComplementaryReimagined_r5.6.1`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1.zip`
- `run/shaderpacks/MakeUp-UltraFast-9.3e`
- `run/shaderpacks/MakeUp-UltraFast-9.3e.zip`

`run/config/oculus.properties` currently selects `ComplementaryReimagined_r5.6.1.zip` after the latest GUI/reload smoke. Change it deliberately before a run if the extracted directory pack is the specific target.

Useful areas in Complementary:

- `shaders/shaders.properties`: conditional directives, optional features, program gating, images, buffer objects, shadow culling.
- `shaders/lib/common.glsl`: feature flags, image usage, shadow/common constants.
- `shaders/lib/pipelineSettings.glsl`: shadow distance and render multiplier.
- `shaders/program/*`: pass-specific uniforms, samplers, and const directives.

Useful areas in MakeUp:

- `shaders/shaders.properties`: scalar/vector custom expressions for `pixel_size_x`, `frame_mod`, `dither_shift`, `fov_y_inv`, and `uniform.vec2.taa_offset`.
- `shaders/src/taa_offset.glsl` and `shaders/common/*`: runtime use of scalar custom uniforms and TAA offsets.

## Completion Standard

For any feature, evidence should answer:

- Does the 1.12.2 code parse the same directive/source metadata as the 1.16.5 reference?
- Does it wire that metadata to the correct runtime path?
- Does it preserve GL state across the affected pass?
- Does it work in the real shader pack that motivated the change?
- Does it fail clearly if unsupported, rather than silently pretending?

If any answer is unknown, document it as unverified.

## 2026-05-20 Relictium Block-Entity Cancellation Check

Use this after touching block entities, Relictium tile entities, entity phases, or GUI/inventory state leaks:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --max-workers=1 test --tests net.oculus.mixins.WorldRendererMixinSourceTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.mixins.RenderLivingBaseEntityColorMixinSourceTest --tests net.oculus.client.OculusRuntimeValidationTest --stacktrace
```

Then run one real shader-pack smoke and confirm:

- `run/logs/latest.log` contains `Mixing pipeline.RelictiumTileEntityRenderMixin`.
- Exported bytecode for `me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer` shows the Oculus begin/end handlers around `renderTileEntities`.
- The selected shader pack compiles and does not fall back to vanilla.
- Local-player main entity logs remain on `gbuffers_entities`.

Last verified runs:
- Complementary: `run/screenshots/oculus-validation-1779282944703-tick-60.png`
- MakeUp: `run/screenshots/oculus-validation-1779283236427-tick-45.png`

Remaining manual check: open the inventory in-client with shaders enabled and confirm GUI scale/projection is normal. No automated inventory screenshot exists yet.
