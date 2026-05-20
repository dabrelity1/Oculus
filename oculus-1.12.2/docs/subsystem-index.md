# Subsystem Index

Last updated: 2026-05-17.

This index maps the active 1.12.2 port by subsystem so future agents can find the right files quickly. It is intentionally practical: use it to start investigation, then verify every claim in source and against `../Oculus-1.16.5`.

## How To Navigate

For any change, identify four things before editing:

- The active 1.12.2 owner class.
- The corresponding 1.16.5 reference class.
- The real shader-pack feature or render pass that exercises it.
- The docs and tests that must change if behavior changes.

Use `docs/source-flow-guide.md` when the question is not just "which package owns this?" but "how does this value move from shader-pack files into runtime rendering?"

Useful lookup commands:

```bash
rg "class ProgramBuilder|class ShadowMap|class ShaderProperties" src/main/java
rg "addShadowSamplers|DeferredWorldRenderingPipeline|BuiltinReplacementUniforms" ../Oculus-1.16.5/src/main/java
rg "watershadow|shadowcolor|supportsColorCorrection|separateAo" run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders
```

## Subsystems

| Subsystem | Active 1.12.2 entry points | 1.16.5 reference areas | Tests and docs to update |
| --- | --- | --- | --- |
| Mod lifecycle, reload, and config | `Oculus`, `OculusClientEvents`, `OculusRuntimeValidation`, `ShaderPackReloader`, `OculusConfig`, `PipelineManager` | Iris/Oculus client lifecycle, config classes, pipeline manager, Forge 1.12 GUI world-load path for validation | `docs/backport-status.md`, `docs/verification.md`, `src/test/java/net/oculus/config` including dotted pack override persistence in `OculusConfigTest`, `src/test/java/net/oculus/client` |
| Shader-pack discovery and options | `ShaderPackLoader`, `ShaderPack`, `ShaderProperties`, `ShaderPackOptions`, `OptionSet`, `ProfileSet`, `LanguageMap`, `ShaderPackLanguageLookup`, `IrisLanguageJsonLoader`, `ClientLocaleLanguageMixin`, `TextLanguageMapMixin`, `IrisFeatureDefines`, `ShaderPackSourceNames` | shader pack loading, source-start discovery, options, profiles, language handling, `MixinClientLanguage`, `FeatureFlags` | `docs/directive-support.md`, `docs/parity-roadmap.md`, `docs/source-flow-guide.md`, `src/test/java/net/oculus/shaderpack/ShaderPackLoaderComplementaryTest.java`, `src/test/java/net/oculus/shaderpack/ShaderPropertiesTest.java`, `src/test/java/net/oculus/shaderpack`, `src/test/java/net/oculus/shaderpack/option`, `src/test/java/net/oculus/client/InternalTranslationResourceTest.java`, `src/test/java/net/oculus/client/IrisLanguageJsonLoaderTest.java`, `src/test/java/net/oculus/mixins/I18nLanguageMixinSourceTest.java`, `IrisFeatureDefinesTest` |
| Include and preprocessing | `ShaderPackLoader`, `IncludeGraph`, `IncludeProcessor`, `StandardMacros`, `ShaderPreprocessor`, `PropertiesPreprocessor`, `JcppProcessor`, `ShaderSourcePreparer`, `ShaderCompatibilityPatcher`, `TextureFormatLoader` | Iris include and transform preprocessing plus `TextureFormatLoader` / `TextureFormatRegistry` define emission, including `ShaderPackSourceNames` root discovery, `JcppProcessor` directive hoisting, and `TransformPatcher`'s numeric raster `#version` precondition | `docs/architecture.md`, `docs/uniform-gap-analysis.md`, `docs/source-flow-guide.md`, `src/test/java/net/oculus/shader`, `src/test/java/net/oculus/shaderpack/ShaderPackLoaderComplementaryTest.java`, `src/test/java/net/oculus/shaderpack/preprocessor`, `StandardMacrosTest`, `JcppProcessorTest`, `ShaderSourcePreparerTest`, `PropertiesPreprocessorTest`, `TextureFormatLoaderTest` |
| Program discovery and pass gating | `ProgramSet`, `ProgramSource`, `ComputeSource`, `ProgramConditionEvaluator`, `ProgramFallbackResolver` | program set and deferred pipeline source discovery | `docs/backport-status.md`, `docs/parity-roadmap.md`, `src/test/java/net/oculus/shaderpack` |
| Shader compilation and linking | `ShaderLoader`, `ProgramCreator`, `GlShader`, `ProgramBuilder`, `Program`, `ComputeProgram`, `ShaderWorkarounds` | program builder, availability-specific world/shadow shader variants, 1.12-adapted attribute and texture-matrix shader transforms, compute program setup | `docs/architecture.md`, `docs/verification.md`, `docs/evidence-log.md`, shader tests when transforms change |
| Uniforms and expressions | `ProgramBuilder`, `ProgramUniforms`, `CapturedRenderingState`, `BuiltinReplacementUniforms`, `GameplayUniforms`, `WorldInfoUniforms`, `IdMapUniforms`, `SpecialEffectUniforms`, `ShadowUniforms`, `CustomUniformExpressionManager`, `SmoothedFloat`, `SmoothedVec2f`, `ExponentialSmoothing` | `net/coderbot/iris/uniforms` and custom uniform expression code, including hardcoded fallback names like `shadowFade` / `shdFade` | `docs/uniform-gap-analysis.md`, `docs/custom-uniform-smooth-semantics.md`, `docs/evidence-log.md`, `docs/backport-status.md`, `src/test/java/net/oculus/uniforms`, especially `GameplayUniformsTest`, `WorldInfoUniformsTest`, `IdMapUniformsTest`, `SmoothedFloatTest`, `CustomUniformExpressionManagerTest`, `InternalProgramBuilderPathTest`, and `ShaderWorldRenderingPipelineSourceTest` |
| Samplers, images, and binding units | `ProgramBuilder`, `ProgramSamplers`, `SamplerLimits`, `TextureBindingRegistry`, `ProgramImages`, `IrisImages`, `ImageBinding`, `ImageHolder`, `ImageLimits` | Iris sampler and image binding helpers, especially reserved texture-unit rules in `ProgramSamplers`, `IrisSamplers`, and fail-fast image-unit handling in `ProgramImages` | `docs/uniform-gap-analysis.md`, `docs/architecture.md`, `ProgramSamplersTest`, `ProgramImagesTest`, `ImageLimitsTest`, focused tests if logic is pure Java |
| Render targets and depth snapshots | `RenderTargets`, `DepthTexture`, `FramebufferManager`, `GlFramebuffer`, `ClearPassCreator`, `ClearPass`, `ShaderWorldRenderingPipeline.clearRenderTargets()` | render target management and framebuffer setup plus 1.12 `OpenGlHelper` FBO dispatch and 1.16.5 render-target preparation cleanup | `docs/render-target-lifecycle.md`, `docs/architecture.md`, `docs/parity-roadmap.md`, `ShaderWorldRenderingPipelineSourceTest`, `FramebufferCompatibilityTest`, runtime validation notes |
| Post-processing and final output | `CompositeRenderer`, `FinalPassRenderer`, `BufferFlipper`, `CenterDepthSampler`, `FullScreenQuadRenderer`, `MinecraftFramebufferExt`, `FramebufferVersionMixin` | `DeferredWorldRenderingPipeline`, composite/deferred/final renderers, `Blaze3dRenderTargetExt`, `MixinRenderTarget` | `docs/render-target-lifecycle.md`, `docs/backport-status.md`, `docs/architecture.md`, `PostprocessRendererSourceTest` / `FinalPassRendererSourceTest` / `FramebufferVersionMixinSourceTest` for source guards, `FramebufferCompatibilityTest` for FBO dispatch, runtime screenshot/log evidence |
| Color-space conversion | `ColorSpace`, `ColorSpaceConverter`, `ColorSpaceComputeConverter`, `ColorSpaceFragmentConverter`, `NoOpColorSpaceConverter`, GUI selector code | Oculus 1.16.5 color-space option and converter path | `docs/directive-support.md`, `docs/backport-status.md`, `src/test/java/net/oculus/colorspace`, `src/test/java/net/oculus/config` |
| Shadow pipeline | `ShadowMap`, `ShadowSamplerBindings`, `ShadowRenderer`, `ShadowCullingCameras`, `ShadowRenderingState`, `PackShadowDirectives`, `ShadowUniforms`, `LevelRendererMixin` setup-terrain redirect | `ShadowRenderTargets`, `ShadowRenderer`, `ShadowMatrices`, `IrisSamplers`, shadow uniforms, texture state, culling, 1.12 terrain dirty/setup bytecode, shadow target-prep default texture-unit state, no-translucents depth-copy state, shadow mipmap texture-unit state, legacy depth-state cleanup, and 1.12 FBO dispatch evidence | `docs/directive-support.md`, `docs/architecture.md`, `docs/parity-roadmap.md`, `FramebufferCompatibilityTest`, `ShadowRendererBytecodeTest`, `ShadowMapBytecodeTest`, `EntityShadowDistanceDirectiveTest`, `ShadowEntityCullingTest`, `ShadowRenderingStateTest`, `ShadowSamplerBindingsTest`, `ShadowMapTextureStateTest`, `ShadowUniformsMatrixTest`, shadow directive tests |
| Main world render loop | `ShaderWorldRenderingPipeline`, `FixedFunctionWorldRenderingPipeline`, `WorldRenderingPhase`, `InputAvailability`, `LevelRendererMixin`, `WorldRendererMixin`, `GameSettingsCloudsMixin`, `GuiIngameOverlayMixin`, `ItemRendererOverlayMixin` | `DeferredWorldRenderingPipeline`, world render phase hooks, cloud override, vignette and underwater overlay mixins, sun/moon draw suppression, `sunPathRotation` sky tilt | `docs/architecture.md`, `docs/backport-status.md`, runtime phase validation |
| Entities, block entities, particles, weather, hand | `RenderManagerMixin`, `TileEntityRendererDispatcherMixin`, `ParticleManagerMixin`, `TileEntityBeaconRendererMixin`, `LevelRendererMixin` | 1.16 entity/block entity/weather/hand hooks | `docs/directive-support.md`, `docs/verification.md`, runtime scene matrix |
| Terrain, vertex format, and block context | `OculusVertexFormats`, `OculusLegacyVertexFormats`, `OculusChunkMeshAttributes`, `OculusExtendedDataHelper`, `BlockContextHolder`, `ContextAwareVertexWriter`, `SodiumTerrainPipeline`, `OculusTerrainPass`, `OculusRelictiumChunkProgramOverrides`, `OculusRelictiumProgramLinker`, `OculusRelictiumSwappableChunkRenderManager`, `RelictiumSodiumWorldRendererShadowMixin` | Iris/Sodium terrain vertex formats, attribute bindings, Sodium terrain shader transforms including direct no-dedup generated declaration injection, Relictium shader override behavior, Sodium shadow visibility graph swapping, vanilla 1.12 shadow terrain dirty/setup ordering, and pre-translucent shadow depth-copy texture state | `docs/relictium-integration.md`, `docs/architecture.md`, `SodiumTerrainPipelineTest`, `SodiumTerrainPipelineGlCompileTest`, `SodiumTerrainShaderTransformerTest`, `OculusTerrainPassTest`, `RelictiumTerrainBridgeBytecodeTest`, `ShadowRendererBytecodeTest`, `ShadowMapBytecodeTest`, vertex tests |
| `separateAo`, material data, and render layers | `SeparateAoTracker`, `FluidSeparateAoTracker`, `RelictiumSeparateAoColor`, block/fluid AO mixins, `BlockMaterialMapping`, `BlockRenderingSettings`, `BlockContextHolder`, `BlockRenderLayerOverrideMixin` | Sodium terrain AO mixins, material-map code, `BlockRenderingSettings#setBlockStateIds(...)`, `BlockRenderingSettings#setBlockTypeIds(...)`, `MixinItemBlockRenderTypes`, Forge 1.12 `Block.canRenderInLayer(...)`, and 1.12 block registry bytecode for split-state and color-property aliases | `docs/directive-support.md`, `docs/relictium-integration.md`, `src/test/java/net/oculus/pipeline/vertex`, `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`, `IdMapTest`, `BlockRenderingSettingsTest`, `PipelineManagerSourceTest`, `BlockRenderLayerOverrideMixinSourceTest` |
| Relictium integration | `OculusRelictiumBridge`, Relictium mixins under `mixin/pipeline`, `net.oculus.sodium.extensions` | installed Relictium jar bytecode, not modern Sodium source alone | `docs/relictium-integration.md`, `docs/verification.md`, refmap and `javap` notes |
| Custom textures, images, texture formats, and SSBOs | `CustomTextureManager`, `CustomImageManager`, `CustomTextureData`, `CustomImageData`, `ShaderStorageBufferManager`, `OculusRenderSystem`, `TextureFormat`, `TextureFormatLoader`, `TextureInfoCache`, `TextureLifecycleTracker`, `LabPBRTextureFormat`, `PBRTextureManager`, `PBRType` | Iris custom texture/image/buffer managers, generated noise texture path, `CustomTextureSamplerInterceptor`, `TextureFormatLoader`, `TextureInfoCache`, `PBRTextureManager`, simple/atlas PBR loaders, LabPBR mipmap behavior, and GL clear capability dispatch | `docs/directive-support.md`, `docs/architecture.md`, `CustomTextureManagerTest`, `CustomImageManagerTest`, `ShaderStorageBufferManagerTest`, `OculusRenderSystemCapabilityDispatchTest`, `TextureFormatLoaderTest`, `TextureInfoCacheTest`, `TextureLifecycleTrackerSourceTest`, `Texture2DBindCacheSourceTest`, `PBRTextureManagerTest`, `ChannelMipmapGeneratorTest`, `AtlasPBRLoaderTest`, runtime reload/PBR tests |
| Blend, alpha, fog, and GL state | `AlphaTest`, `BlendMode`, `BlendModeStorage`, `BufferBlendOverride`, `StateUpdateNotifiers`, `GameDataSuppliers`, GL state mixins | Iris blend/fog overrides and RenderSystem state tracking, especially `FogUniforms` | `docs/directive-support.md`, `docs/uniform-gap-analysis.md`, `GameDataSuppliersTest`, GL state tests if possible |
| Resources and mixin metadata | `mixins/OculusMixinLoader`, `src/main/resources/oculus.mixins.json`, compatibility mixin JSON files, `META-INF/accesstransformer.cfg`, `mcmod.info` | 1.16 mixin configs only as concept reference; 1.12 descriptors and early MixinBooter/coremod loading are authoritative | `docs/verification.md`, generated refmap inspection, `OculusMixinLoaderTest`, fresh startup log |
| Legacy shims and copied remnants | `com.mojang.blaze3d.*`, `com.mojang.math.Matrix4f`, in-project `net.coderbot.iris.*`, non-primary mixin JSON resources | Use modern/reference names only as clues; active behavior must be proven through call sites, `build.gradle`, and mixin runtime config | `docs/legacy-shims-and-remnants.md`, `docs/verification.md`, focused call-site audit |

## Status Words

Use the same status terms across docs:

- Implemented: parsed, wired, tested where possible, and no known missing code path.
- Partial: useful behavior exists, but important reference behavior is absent or not wired.
- Parsed only: metadata is read but does not affect runtime behavior.
- Unverified: code exists, but client behavior has not been proven.
- Missing: no current support found.

Do not upgrade a subsystem to implemented solely because `compileJava`, `test`, or `build` passes.

## Documentation Ownership

When changing a subsystem, update the closest doc:

- Architecture or control flow: `docs/architecture.md`.
- Source/data/runtime flow: `docs/source-flow-guide.md`.
- Agent cold-start navigation, search recipes, active path warnings, or first-pass commands: `docs/agent-quickstart.md`.
- Render-target, depth-texture, framebuffer, buffer-flip, or final/composite lifecycle behavior: `docs/render-target-lifecycle.md`.
- Full-port status or risk: `docs/backport-status.md`.
- Remaining work: `docs/parity-roadmap.md`.
- Properties or const directives: `docs/directive-support.md`.
- Uniforms, samplers, images, or custom expressions: `docs/uniform-gap-analysis.md`.
- Terrain and Relictium: `docs/relictium-integration.md`.
- Compatibility shims, copied-remnant cleanup, or active/inactive mixin config assumptions: `docs/legacy-shims-and-remnants.md`.
- Verification commands or runtime evidence: `docs/verification.md`.
- Agent operating rules or current context: `AGENTS.md` and `docs/agent-handoff.md`.

If a change touches more than one subsystem, update all docs that a future agent would reasonably read before working there.
