# Repository Map

Last updated: 2026-05-17.

This file is the high-level navigation map for agents working on the Forge 1.12.2 Oculus backport. It does not replace source inspection. Use it to find the right subsystem quickly, then confirm behavior in code and against `../Oculus-1.16.5`.

## First Files To Read

- `AGENTS.md`: operating rules, reference paths, high-value entry points, and documentation expectations.
- `README.md`: project status, build commands, and links to the documentation set.
- `docs/README.md`: documentation index and task-based lookup table.
- `docs/agent-quickstart.md`: first-page checklist for cold-start agents, with active paths, search recipes, subsystem starts, verification commands, and documentation ownership.
- `docs/ai-agent-context.md`: compact context pack for cold-start agents, including local layout, source-backed invariants, and current traps.
- `docs/agent-handoff.md`: current resume context, recent evidence, and likely next investigations.
- `docs/agent-playbook.md`: step-by-step workflow for agents taking over the port.
- `docs/subsystem-index.md`: subsystem entry points, 1.16.5 reference areas, tests, and docs to update.
- `docs/source-flow-guide.md`: flow-oriented guide for tracing selection/reload, metadata, source preparation, render passes, postprocess, shadows, uniforms, terrain, and resource lifetime.
- `docs/backport-status.md`: current implemented, incomplete, and unverified areas.
- `docs/parity-roadmap.md`: strict map of what remains before full-port parity can be claimed.
- `docs/evidence-log.md`: source-backed audit notes, bytecode checks, shader-pack evidence, and explicit blockers where guessing would be unsafe.
- `docs/architecture.md`: runtime control-flow map.
- `docs/render-target-lifecycle.md`: render-target, depth-texture, framebuffer ownership, buffer-flip, and postprocess lifecycle notes.
- `docs/custom-uniform-smooth-semantics.md`: focused custom expression `smooth([id], ...)` evidence and duplicate-ID risk notes.
- `docs/legacy-shims-and-remnants.md`: compatibility shim and inactive copied-resource map.
- `docs/directive-support.md`: `shaders.properties` and const directive support table.
- `docs/uniform-gap-analysis.md`: uniform, sampler, and image audit guide.
- `docs/verification.md`: compile, build, refmap, runtime, and shader-pack validation workflow.
- `docs/relictium-integration.md`: Relictium/Sodium terrain hook notes.

## Local Reference Layout

- `../Oculus-1.16.5`: authoritative source for architecture and behavior. Always inspect this before porting a subsystem.
- `run/shaderpacks/ComplementaryReimagined_r5.6.1`: current real shader-pack target for directives, uniforms, samplers, images, options, and pass layout.
- `run/shaderpacks/ComplementaryReimagined_r5.6.1.zip`: packaged copy of the same target.
- `run/shaderpacks/MakeUp-UltraFast-9.3e`: secondary local shader-pack target used to audit OptiFine custom expressions such as `fmod`, view-size uniforms, `uniform.vec2.taa_offset`, and matrix element access.
- `run/shaderpacks/MakeUp-UltraFast-9.3e.zip`: packaged copy of the secondary target, covered by the headless real-pack loader guard when present.
- `/home/daniel/.gradle/caches/minecraft/deobfedDeps/deobf/maven/modrinth/relictium/1.2.0/relictium-1.2.0.jar`: active local Relictium bytecode reference.
- Parent-project `src/main/java/net/coderbot/iris`: a separate copied/transformed source tree exists in the broader workspace. Do not treat it as the active 1.12.2 port unless the user explicitly redirects you. The active project for this backport is this `oculus-1.12.2` directory.
- In-project `src/main/java/com/mojang/*` and `src/main/java/net/coderbot/iris/*`: compatibility shims or historical remnants unless current call-site inspection proves otherwise. See `docs/legacy-shims-and-remnants.md`.

## Build And Runtime Files

- `build.gradle`: ForgeGradle 2.3, Forge `1.12.2-14.23.5.2768`, MCP `stable_39`, Java 8, Relictium dependency, Mixin annotation processor, Oculus early coremod manifest attributes, and HTTPS asset workaround.
- `settings.gradle`, `gradle.properties`, `gradle/wrapper/*`: Gradle wrapper and project settings.
- `run/`: ForgeGradle client run directory. Contains `shaderpacks`, config output, logs, screenshots if captured, and runtime-generated files.
- `logs/debug.log`: local Gradle/test log output outside the client run directory.

Use the Java 8 wrapper-jar command from `README.md` and `docs/verification.md`; do not assume `./gradlew` is executable or clean.

## Resources And Mixin Configs

- `src/main/resources/oculus.mixins.json`: primary 1.12 client mixin list.
- `src/main/resources/mixins.oculus.vertexformat.json`: vertex-format and block/fluid emission mixins.
- `src/main/resources/mixins.oculus.compat*.json`: compatibility mixin configs.
- `src/main/resources/META-INF/accesstransformer.cfg`: 1.12 access transformer entries.
- `src/main/resources/mcmod.info`: Forge 1.12 mod metadata.
- `src/main/resources/colorSpace.csh`, `colorSpace.vsh`: color-space post-final converter resources.
- `src/main/resources/centerDepth.fsh`, `centerDepth.vsh`: center-depth sampling resources.

Only `oculus.mixins.json` is queued by the current Oculus early coremod path. Other mixin JSON resources with `net.coderbot` package names remain packaged but should not be treated as active without a fresh runtime/config audit.

When adding or changing mixins, run `compileJava` and inspect the generated refmap as described in `docs/verification.md`. When changing mixin bootstrap, also run `OculusMixinLoaderTest` and verify a fresh client log shows MixinBooter adding `oculus.mixins.json` through `net.oculus.mixins.OculusMixinLoader` before late-loader discovery.

## Entry Packages

- `net.oculus`: mod entry point and global services.
- `net.oculus.client`: client lifecycle, key bindings, shader reload integration.
- `net.oculus.config`: persistent Oculus config, including shader pack and color-space settings.
- `net.oculus.gui`: 1.12 shader-pack screen, pack list, option screens, and widgets.
- `net.oculus.bridge`: Relictium bridge and optional integration boundaries.
- `net.oculus.util`: small shared utilities.

Start here when changing lifecycle, config persistence, GUI behavior, or reload flow.

## Shader-Pack Loading

Primary files:

- `net.oculus.shaderpack.ShaderPackLoader`
- `net.oculus.shaderpack.ShaderPack`
- `net.oculus.shaderpack.ShaderProperties`
- `net.oculus.shaderpack.ProgramSet`
- `net.oculus.shaderpack.ProgramSource`
- `net.oculus.shaderpack.ComputeSource`
- `net.oculus.shaderpack.ProgramConditionEvaluator`
- `net.oculus.shaderpack.ProgramFallbackResolver`
- `net.oculus.shaderpack.PackDirectives`
- `net.oculus.shaderpack.PackRenderTargetDirectives`
- `net.oculus.shaderpack.PackShadowDirectives`

Supporting packages:

- `shaderpack.include`: include graph, absolute pack paths, and include processing.
- `shaderpack.option`: shader option discovery, merging, values, profiles, and source annotations.
- `shaderpack.option.menu`: shader option screen/menu model.
- `shaderpack.preprocessor`: GLSL/properties preprocessing using JCPP, including `JcppProcessor`'s 1.16.5-style `#version` / `#extension` marker hoisting and the two-pass `shaders.properties` flow that exposes self-declared supported `IRIS_FEATURE_*` guards before final metadata parsing.
- `gl.shader.StandardMacros`: OptiFine/Iris environment defines used by shader and properties preprocessing. The target default `MC_VERSION` is `11202`, not `11605`.
- `shaderpack.texture`: custom texture/image directive data.
- `shaderpack.materialmap`: block/entity material mapping support.
- `shaderpack.loading`: program id and group definitions.

Use `docs/directive-support.md` before adding directive handling. Update that file whenever directive parsing changes runtime behavior.

## GLSL Preparation And Program Construction

Primary files:

- `net.oculus.shader.ShaderLoader`
- `net.oculus.shader.ShaderPreprocessor`
- `net.oculus.shader.ShaderSourcePreparer`
- `net.oculus.shader.ShaderCompatibilityPatcher`
- `net.oculus.shader.IrisFeatureDefines`
- `net.oculus.gl.shader.ProgramCreator`
- `net.oculus.gl.shader.GlShader`
- `net.oculus.gl.shader.ShaderWorkarounds`
- `net.oculus.gl.program.ProgramBuilder`
- `net.oculus.gl.program.Program`
- `net.oculus.gl.program.ComputeProgram`
- `net.oculus.gl.program.ProgramUniforms`
- `net.oculus.gl.program.ProgramSamplers`
- `net.oculus.gl.program.ProgramImages`
- `net.oculus.gl.program.TextureBindingRegistry`
- `net.oculus.gl.sampler.SamplerLimits`

This path owns macro definition, source patching, shader compilation, uniform registration, sampler allocation, image allocation, texture-unit limits, reserved sampler unit policy, and custom uniform wiring. `ShaderSourcePreparer` owns the raster pack-source numeric `#version` precondition before define injection, while compute sources keep compiler-path version handling. `ShaderLoader` also owns availability-specific `gbuffers_*` / root shadow variants, and `ShaderCompatibilityPatcher` owns the 1.12-adapted `gl_MultiTexCoord0/1/2`, `iris_TextureMatrix[8]`, non-core `mc_midTexCoord`, compatibility-profile-gate transforms for those variants, and grouped numeric interface repair across the reference `BuiltinNumericTypeSpecifier` breadth. `SodiumTerrainShaderTransformer` owns the separate Sodium terrain transform path, including reference-style direct generated declaration injection without duplicate suppression. Compare every new built-in uniform against `../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms`, and compare source transforms against `../Oculus-1.16.5/src/main/java/net/coderbot/iris/pipeline/transform`; when indices differ, verify 1.12 bytecode before changing code.

## Render Pipeline

Primary files:

- `net.oculus.pipeline.PipelineManager`
- `net.oculus.pipeline.WorldRenderingPipeline`
- `net.oculus.pipeline.ShaderWorldRenderingPipeline`
- `net.oculus.pipeline.FixedFunctionWorldRenderingPipeline`
- `net.oculus.pipeline.WorldRenderingPhase`
- `net.oculus.pipeline.RenderCondition`
- `net.oculus.pipeline.InputAvailability`
- `net.oculus.pipeline.BlockRenderingSettings`
- `net.oculus.pipeline.ClearPass`
- `net.oculus.pipeline.ClearPassCreator`
- `net.oculus.layer.GbufferPrograms`

Supporting packages:

- `pipeline.framebuffer`: framebuffer manager.
- `pipeline.shadow`: shadow framebuffer, render pass, culling cameras, pass-wide shadow active state, and entity/block-entity shadow filters.
- `pipeline.texture`: custom texture and image managers.
- `pipeline.buffer`: SSBO manager.
- `pipeline.sampler`: sampler override configuration.
- `pipeline.particle`: phased particle rendering.
- `pipeline.state`: state tracker utilities.
- `postprocess`: composite, deferred/final pass helpers, full-screen quad, buffer flips, and center-depth sampling.
- `rendertarget`: render-target and depth texture ownership.

Use `docs/architecture.md` for frame flow and `docs/render-target-lifecycle.md` for render-target, depth-texture, framebuffer, buffer-flip, and final/composite ownership before touching this area. Active framebuffer calls should use `OpenGlHelper` dispatch; `FramebufferCompatibilityTest` guards this for render-target, shadow, postprocess, center-depth, and color-space paths.

## GL State And Low-Level Types

Primary packages:

- `net.oculus.gl`: GL resource base classes, debug helpers, and 1.12 render-system compatibility.
- `net.oculus.gl.blending`: alpha test, blend mode, per-buffer blend override, and live blend state storage.
- `net.oculus.gl.image`: image binding, image limits, and image holder abstractions.
- `net.oculus.gl.state`: matrix state, texture/blend/fog update notifiers, and game-data suppliers.
- `net.oculus.gl.texture`: texture formats, depth formats, pixel types, and texture scale overrides.
- `net.oculus.texture.format`: OptiFine/Iris texture-format metadata from `optifine/texture.properties`, currently including `lab-pbr` define emission.
- `net.oculus.texture`: `TextureInfoCache` tracks source-shaped texture upload metadata for texture-size uniforms, and `TextureLifecycleTracker` connects both mixin and port-owned direct LWJGL texture lifecycle calls to metadata/PBR cleanup.
- `net.oculus.texture.pbr`: PBR suffix/default metadata, runtime PBR holder management, default normal/specular textures, `SimpleTexture` `_n` / `_s` companions, the first 1.12 atlas PBR loader path with animated atlas updates, and opt-in validation logging for loaded PBR texture IDs. Runtime resource-pack proof is still required before treating PBR parity as closed.
- `net.oculus.vendored.joml`: vendored math classes used by rendering and uniforms.

The 1.12 renderer has no modern `RenderSystem`; compatibility wrappers exist here but must be checked against actual 1.12 OpenGL state behavior.

## Uniforms, Samplers, Images, And Custom Expressions

Primary files:

- `net.oculus.uniforms.CapturedRenderingState`
- `net.oculus.uniforms.BuiltinReplacementUniforms`
- `net.oculus.uniforms.CameraPositionTracker`
- `net.oculus.uniforms.CelestialUniforms`
- `net.oculus.uniforms.CompatibilityUniforms`
- `net.oculus.uniforms.GameplayUniforms`
- `net.oculus.uniforms.IdMapUniforms`
- `net.oculus.uniforms.ShadowUniforms`
- `net.oculus.uniforms.SpecialEffectUniforms`
- `net.oculus.uniforms.SystemTimeUniforms`
- `net.oculus.uniforms.WorldInfoUniforms`
- `net.oculus.uniforms.custom.CustomUniformExpressionManager`
- `net.oculus.uniforms.transforms.ExponentialSmoothing`
- `kroppeb.stareval.*`: vendored expression evaluator used by custom uniforms and variables.

`CustomUniformExpressionManager` handles scalar `bool`/`int`/`float` and vector `vec2`/`vec3`/`vec4` custom expressions, including Complementary smoothing expressions, fixed scalar/vector built-in uniform symbols, bare built-in vector identifiers, hardcoded fallback symbols with shaderpack directive precedence, and MakeUp helpers such as `fmod`, view-size symbols, optional-ID `smooth`, vector constructors, vector arithmetic, component access, and matrix element access. Known dynamic pass/object/fog/texture/blend symbols are dependency-tracked and evaluated on uniform upload instead of frame-start pre-evaluation. Exact in-client timing, OptiFine/Iris duplicate smooth-ID collision behavior, and full vector-function parity still need audit; read `docs/custom-uniform-smooth-semantics.md` before changing smooth state ownership.

Update `docs/uniform-gap-analysis.md` when closing a uniform or sampler gap. A compile pass is not enough; document whether frame timing and runtime behavior are verified.

## Color-Space Support

Primary files:

- `net.oculus.colorspace.ColorSpace`
- `net.oculus.colorspace.ColorSpaceConverter`
- `net.oculus.colorspace.ColorSpaceShaderSource`
- `net.oculus.colorspace.ColorSpaceComputeConverter`
- `net.oculus.colorspace.ColorSpaceFragmentConverter`
- `net.oculus.colorspace.NoOpColorSpaceConverter`
- `net.oculus.config.OculusConfig`
- `net.oculus.gui.ShaderPackSelectionList`
- `net.oculus.gui.ShaderPackScreen`
- `net.oculus.pipeline.ShaderWorldRenderingPipeline`

The current design matches the 1.16.5 split: packs declaring `supportsColorCorrection=true` get defines and `currentColorSpace` and do their own correction; other packs receive an Oculus-owned post-final conversion. Non-SRGB output still needs in-client visual validation.

## Terrain, Vertex Formats, And Relictium

Primary files:

- `net.oculus.pipeline.vertex.OculusVertexFormats`
- `net.oculus.pipeline.vertex.OculusLegacyVertexFormats`
- `net.oculus.pipeline.vertex.OculusChunkMeshAttributes`
- `net.oculus.pipeline.vertex.OculusExtendedDataHelper`
- `net.oculus.pipeline.vertex.OculusNormalHelper`
- `net.oculus.pipeline.vertex.SeparateAoTracker`
- `net.oculus.pipeline.vertex.FluidSeparateAoTracker`
- `net.oculus.pipeline.vertex.RelictiumSeparateAoColor`
- `net.oculus.pipeline.BlockContextHolder`
- `net.oculus.pipeline.ContextAwareVertexWriter`
- `net.oculus.pipeline.OculusTerrainVertexType`
- `net.oculus.pipeline.OculusTerrainVertexBufferWriterNio`
- `net.oculus.pipeline.OculusTerrainVertexWriterFallback`
- `net.oculus.blockrendering.BlockMaterialMapping`
- `net.oculus.bridge.OculusRelictiumBridge`
- `net.oculus.compat.relictium.OculusRelictiumSwappableChunkRenderManager`
- `net.oculus.sodium.extensions.IChunkBuildBuffers`

Related mixins are under `net.oculus.mixin.vertexformat` and Relictium-specific files under `net.oculus.mixin.pipeline`. `RelictiumChunkRenderManagerMixin` and `RelictiumSodiumWorldRendererShadowMixin` also own the source-backed shadow visibility graph swap for Relictium terrain. `ShadowRenderer` owns the vanilla 1.12 pre-shadow terrain dirty mark before `RenderGlobal.setupTerrain(...)`, and `ShadowRendererBytecodeTest` guards that ordering. Check `docs/relictium-integration.md` before changing these hooks.

`BlockMaterialMapping` is also the entry point for `block.properties` material IDs. It contains the focused 1.12 compatibility resolver for Complementary's modern lit/snow predicates that target split legacy block registry names.

## Mixin Hot Spots

- `mixin/pipeline/LevelRendererMixin`: `EntityRenderer` frame entry/exit, shadow setup, frustum and occlusion overrides, particles, hand, weather, and rain depth.
- `mixin/pipeline/WorldRendererMixin`: `RenderGlobal` phase tracking, terrain layers, entities, block entities, sky/cloud/world border, `backFace.*`, vanilla sun/moon draw suppression, and live `sunPathRotation` sky tilt.
- `mixin/pipeline/GameSettingsCloudsMixin`: shader-pack cloud `OFF`/`FAST`/`FANCY` override for `GameSettings.shouldRenderClouds()`.
- `mixin/pipeline/GuiIngameOverlayMixin`, `ItemRendererOverlayMixin`: shader-pack vignette and underwater overlay suppression.
- `mixin/pipeline/ParticleManagerMixin`: phased particle rendering.
- `mixin/pipeline/TileEntityBeaconRendererMixin`: beacon beam depth behavior.
- `mixin/pipeline/RenderManagerMixin`, `TileEntityRendererDispatcherMixin`: entity and block-entity phase context.
- `mixin/pipeline/GlStateManagerBlendOverrideMixin`, `GlStateManagerStateMixin`: live GL state tracking and blend overrides.
- `mixin/vertexformat/*`: extended terrain vertex format, block/fluid context, AO separation, and generic vertex attributes.
- `mixins/OculusMixinLoader`: Forge `IFMLLoadingPlugin` and MixinBooter `IEarlyMixinLoader` for `oculus.mixins.json`.
- `mixin/OculusMixinPlugin`: optional mixin config plugin class; do not assume it is active unless `oculus.mixins.json` names it.

Mixin names are easy to misread because some class names mirror modern Iris while targeting 1.12 classes. Always inspect imports, descriptors, generated refmaps, and target bytecode.

## Legacy Shims And Remnants

Some source files exist for migration compatibility rather than as production runtime owners:

- `com.mojang.blaze3d.*` and `com.mojang.math.Matrix4f`: modern-name compatibility shims. `LegacyShimUsageSourceTest` guards that inactive shells such as modern `Framebuffer`, `Matrix4f`, and `VertexBuffer` do not leak into active `net.oculus` runtime source; the current allowed active modern-vertex use is `OculusVertexFormats`.
- `net.coderbot.iris.*` inside this active project: historical-looking copied classes. The active program/rendering path usually lives under `net.oculus.*`.
- Non-primary mixin JSON resources with `net.coderbot` packages: packaged resources, but not active in the current Gradle run unless `-Dmixin.configs` or another loader path is changed.

Read `docs/legacy-shims-and-remnants.md` before editing or relying on these areas.

## GUI And Options

Primary files:

- `net.oculus.gui.ShaderPackScreen`
- `net.oculus.gui.ShaderPackSelectionList`
- `net.oculus.gui.NavigationController`
- `net.oculus.gui.element.ShaderPackOptionList`
- `net.oculus.gui.element.widget.*`
- `net.oculus.client.gui.GuiShaders`
- `net.oculus.client.OculusKeyBindings`

Option values flow from shader source annotations and `shaders.properties` into `ShaderPackOptions`, `ProfileSet`, menu model classes, GUI widgets, and config persistence. GUI changes should be checked against both default pack behavior and a real pack with nested option screens.

## Tests

Current test packages:

- `colorspace`: color-space source adaptation and config parsing.
- `config`: `OculusConfig` behavior.
- `gl.state`: state update notifier behavior.
- `pipeline`: block rendering settings.
- `pipeline.vertex`: separate-AO vertex-color helpers.
- `shader`: compatibility patcher and shader preprocessor behavior.
- `shaderpack`: directive parsing, id maps, language maps, profiles, and program conditions.
- `shaderpack.preprocessor`: properties preprocessing.
- `uniforms`: captured state and gameplay uniform helpers.

Gradle `build` runs the JUnit suite. Tests are useful regression coverage, but they are not runtime shader-pack validation.

## Documentation Update Rules

When landing a meaningful slice, update the docs that match the changed behavior:

- New or changed runtime architecture: `docs/architecture.md` and this file.
- New or changed render-target, depth-texture, framebuffer, buffer-flip, or final/composite lifecycle behavior: `docs/render-target-lifecycle.md`.
- New or changed agent workflow or handoff expectation: `docs/agent-playbook.md`.
- Current resume point, recent evidence, or recommended next audit: `docs/agent-handoff.md`.
- New package ownership, subsystem entry point, test area, or reference mapping: `docs/subsystem-index.md`.
- New cold-start navigation rule, source path, search recipe, or command: `docs/agent-quickstart.md`.
- Full-port blockers closed or discovered: `docs/parity-roadmap.md`.
- New directive or property behavior: `docs/directive-support.md`.
- New uniform, sampler, image, or custom-expression behavior: `docs/uniform-gap-analysis.md`.
- New verification command or required manual check: `docs/verification.md`.
- New Relictium or terrain integration behavior: `docs/relictium-integration.md`.
- New compatibility shim, removed copied remnant, or active/inactive mixin config change: `docs/legacy-shims-and-remnants.md`.
- Changed completion status or known risk: `docs/backport-status.md`.
- New agent operating rule or critical warning: `AGENTS.md`.

Use precise status words: `implemented`, `partial`, `parsed only`, `missing`, and `unverified`. Do not write that the full port is complete until runtime parity has been proven in Minecraft 1.12.2.
