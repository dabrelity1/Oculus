# Agent Handoff

Last updated: 2026-05-20.

This file is the fast context handoff for agents resuming the Forge 1.12.2 Oculus backport. It records the current working assumptions, recent evidence, and next high-value checks. Source code and fresh runtime logs remain authoritative.

## Mission State

The repository is still an active backport, not a finished port. Many real systems are implemented, but full production parity is not proven until the mod runs cleanly in Minecraft 1.12.2 with real shader packs and the rendered output is checked pass by pass.

Short answer for future agents: yes, there is still a long way left before a full port can be claimed. The remaining work is mostly correctness proof, runtime validation, and closing exact parity gaps, not simply making the Java project compile.

## Reference Priority

Use this order when facts conflict:

1. Current source in this `oculus-1.12.2` directory.
2. `../Oculus-1.16.5`, the primary behavior reference.
3. `run/shaderpacks/ComplementaryReimagined_r5.6.1`, the current primary real shader-pack target.
4. `run/shaderpacks/MakeUp-UltraFast-9.3e`, the secondary real shader-pack target for custom expressions and broader loader coverage.
5. Forge 1.12.2, MCP `stable_39`, and installed Relictium bytecode.
6. The docs in this directory.

Docs are navigation aids. If docs and code disagree, inspect the code, fix the behavior if needed, then update the docs.

## Current Verification Evidence

These commands have passed recently with Java 8:

Latest Complementary compile-fallback fix: the saved `ComplementaryReimagined_r5.6.1.zip` config with `WORLD_SPACE_REFLECTIONS=1`, `COLORED_LIGHTING=512`, and `SHADOW_QUALITY=4` was resetting to vanilla because `composite.fsh` hit a syntax error while compiling the SSBO world-space-reflection path. `ShaderCompatibilityPatcher` now raises raster sources with active `layout(std430, ...) ... buffer` declarations to `#version 430 compatibility`, keeping legacy fixed-function symbols while enabling SSBO grammar. The same slice also adds a MakeUp UltraFast legacy `shifted_dither17` fallback when `frame_mod` exists and the helper is called but not defined. Focused `ShaderCompatibilityPatcherTest` plus `ShaderPackLoaderComplementaryTest` passed. A 2026-05-20 bounded `runClient` validation auto-joined `New World` and fresh `run/logs/latest.log` reports `Compiled 144 shader program object(s)`, `Loaded 144 shader program object(s); 0 unspecialized program(s): []`, no shader-pipeline fallback, Relictium shadow/main terrain override creation, and local-player render passes in both shadow and main. The screenshot for that run was captured with the pause menu open, so it is not visual-parity proof. Remaining user-visible issues to investigate separately: dark mobs, transparent/missing player body or hand, buggy shadows, `Unknown sampler textureAtlas`, and runtime validation GL errors around stone item rendering.

Latest Complementary visual fix: the grey/foggy atlas-smeared final output was traced to active texture-unit drift. `ProgramSamplers` intended `deferred1 colortex0` to bind render-target texture `52`, but runtime telemetry saw unit `0` sampling texture `8` from the atlas because the real GL active unit and the 1.12 `GlStateManager` cache had diverged after high-unit sampler work. `OculusRenderSystem.setActiveTextureUnit(...)` now forces `OpenGlHelper.setActiveTexture(...)` even after updating the vanilla cache for low texture units, and fullscreen `colortex0` registers the pack alias group (`gcolor`, `gtexture0`, `s_texture`, `tex`, `texture`, `gtexture`, `gaux0`) on the same unit-0 default binding. Focused sampler/render-system/program-sampler tests passed, standalone `compileJava testClasses` passed, and the 2026-05-20 Complementary validation produced `run/screenshots/oculus-validation-1779244370267-tick-220.png`, a colored shader-lit scene with no atlas overlay. Latest log evidence reports `deferred1 colortex0 unit=0 texture=52` and `composite1 colortex0 unit=0 texture=53`. Player-body visibility is still not conclusively proven by that screenshot; keep it as a separate entity/player rendering check if the user still sees missing body parts.

Recent Complementary visual split and hand/depth note: the black sky lines are no longer the active symptom. The remaining grey/foggy output is not the same exact bug as the missing third-person player body. A 2026-05-19 first-person dump with Complementary compiled/loaded 144 shader program objects, showed full-color `pre-deferred-colortex0`, recovered `pre-composite-depthtex0` after moving shader hand rendering before `beginTranslucents()`, but still produced foggy/grey final composite output. A matching `thirdPersonView=1` dump showed full-color pre-deferred terrain with no normal player body, so the body issue is upstream of deferred/composite and should be investigated in entity/player gbuffer rendering. Shadow depth still sampled all-clear. Code touched: `EntityRendererAccessor`, `WorldRendererMixin`, `LevelRendererMixin`, `oculus.mixins.json`, plus focused mixin source tests. `compileJava`, `LevelRendererMixinSourceTest`, `WorldRendererMixinSourceTest`, and both runtime validations passed.

Recent Complementary runtime shader enablement note: the current `run/logs/latest.log` was analyzed and the shader pipeline blockers were removed in sequence. The fixes covered same-alias fullscreen default sampler reuse on texture unit `0`, LWJGL 2 `glGet*` state snapshot buffers widened to the 16-element storage required by its buffer overloads, and shadow culling adapters extending `Frustum` for the Forge 1.12 `RenderGlobal.setupTerrain(...)` cast path. A final `runClient` validation auto-joined `New World` with `ComplementaryReimagined_r5.6.1.zip`, created a `ShaderWorldRenderingPipeline`, created 99 postprocess passes, compiled 144 shader program objects, initialized final and shadow rendering, selected Relictium shadow/main terrain overrides, sampled non-clear shadow depth on attempt 20, and exited after 240 ticks. The negative log check found no shader-pipeline fallback, unit-zero collision, LWJGL buffer-size crash, `ClassCastException`, or client crash. Visual parity is still not fully proven.

Recent Complementary red-sandstone material-alias note: `BlockMaterialMapping` now resolves `cut_red_sandstone_slab` and `smooth_red_sandstone_slab` to the closest Forge 1.12 red-sandstone slab state, and `smooth_red_sandstone_stairs` to `red_sandstone_stairs`. `BlockMaterialMappingTest`, `ShaderPackLoaderComplementaryTest`, and standalone `compileJava` passed. Minecraft and `runClient` were not run, so live Relictium/fallback terrain material IDs and rendered red-sandstone branches remain runtime-unverified.

Recent Relictium level-input availability note: `ProgramBuilder` now exposes a non-owning linked-program wrapper overload that accepts explicit `InputAvailability`, and `SodiumTerrainPipeline` passes `InputAvailability(true, true, false)` when attaching Oculus bindings to Relictium-owned `_sodium` programs. This pins the 1.16.5 terrain sampler assumption that atlas and lightmap are available while overlay is unavailable, without adding source input-availability variants. Focused `SodiumTerrainPipelineTest`, `ShaderLoaderSourceTest`, `InternalProgramBuilderPathTest`, `IrisSamplersTest`, Relictium override tests, and standalone `compileJava` passed. Minecraft and `runClient` were not run.

Recent custom texture external sampler interception note: `CustomTextureManagerTest#levelAlbedoStageOverrideReplacesExternalAtlasAliasGroupLikeReferenceInterceptor` now proves `texture.gbuffers.gtexture` replaces active `tex`, `texture`, and `gtexture` external atlas aliases on unit `0`, while `#nonZeroExternalStageOverrideUsesDynamicUnitLikeReferenceInterceptor` proves `lightmap`-style nonzero external overrides allocate a dynamic managed unit. No production code changed. Focused custom texture, adjacent sampler/image/PBR tests, and standalone `compileJava` passed. Minecraft and `runClient` were not run.

Recent custom expression directive type coverage note: `CustomUniformExpressionManagerTest#customExpressionDirectiveTypesEvaluateBooleanIntegerAndVectorVariables` now pins `variable.bool`, `variable.int`, `variable.vec4`, `uniform.bool`, `uniform.int`, and `uniform.vec4` chaining through the custom expression evaluator. No production code changed; this was a code-side test-evidence gap after the Complementary/MakeUp expression audit found the implementation already source-backed. The focused custom-expression test, broad Agent 3 runtime-data suite, and standalone `compileJava` passed. Minecraft and `runClient` were not run.

Recent legacy shim and mixin-route guard note: `LegacyShimUsageSourceTest` now checks active `net.oculus` source stays off historical `net.coderbot.iris` runtime packages, inactive modern shells stay out of active runtime code, `OculusMixinLoader` queues only `oculus.mixins.json`, the active mixin config stays under `net.oculus.mixin`, and `build.gradle` does not restore late `-Dmixin.configs` routing or queue historical packaged mixin configs. Focused compat/Relictium source tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent simple PBR validation resource-pack loader note: `SimplePBRLoaderTest#localPbrValidationResourcePackLoadsSimpleCompanionsThroughLoaderPath` now exercises `SimplePBRLoader.load(...)` against the optional `run/resourcepacks/Oculus-PBR-Validation` folder pack when present. The test reads `minecraft:textures/blocks/dirt_n.png` and `dirt_s.png` through a headless `IResourceManager`, confirms both are 16x16, and verifies the normal/specular companions reach the consumer without allocating GL textures. Focused PBR/custom-resource/sampler/image tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent Relictium render-manager failure cleanup note: `RelictiumChunkRenderManagerMixin` now attempts collected `backend.end()` plus `OculusRelictiumChunkRenderBackendExt.oculus$end()` cleanup when `ChunkRenderBackend.render(...)` throws, then rethrows the original runtime or hard render failure with cleanup failures suppressed. The normal backend end redirect delegates to the same collected cleanup helper, so terrain-scope cleanup cannot replace an earlier backend end failure. `RelictiumChunkRenderManagerMixinSourceTest`, focused Relictium source/bytecode tests, adjacent terrain/vertex tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent texture-format reload PBR clear failure-boundary note: `TextureFormatLoader.reload(...)` now records runtime or hard failures from `PBRTextureManager.INSTANCE.clear()`, still attempts `onFormatChange()` when `optifine/texture.properties` changes, and rethrows the collected cleanup failure afterward. This keeps texture-format define rebuild pressure, PBR/custom-resource sampler refresh, and Relictium wrapped sampler rebuild pressure from being skipped solely because stale PBR holder cleanup failed after the new format was published. Focused `TextureFormatLoaderTest`, adjacent PBR/reload/mixin tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent texture-manager reload PBR reset failure-boundary note: `TextureManagerPBRReloadMixin` now attempts `WorldRenderingPipeline.resetPbrTextureBindings()` after `TextureFormatLoader.reload(resourceManager)` even when texture-format reload throws during the vanilla texture-manager reload tail. The original reload failure is rethrown and any reset failure is suppressed onto it, so optional metadata reload failure does not skip active `normals` / `specular` and custom-resource `_n` / `_s` sampler reset. Focused `TextureManagerPBRReloadMixinSourceTest`, adjacent texture-format/PBR/pipeline tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent render-target sampler/image count fail-fast note: `IrisSamplers.addRenderTargetSamplerBindings(...)` now checks active `colortexN`, legacy render-target aliases, `oculus_rtN`, and `oculus_flipped_rtN` beyond the configured render-target count up to `IrisLimits.MAX_COLOR_BUFFERS`; `IrisImages.addRenderTargetImages(...)` now checks active `colorimgN` beyond that count. `IrisSamplersTest#renderTargetSamplerBindingsFailFastWhenProgramReferencesSamplerBeyondConfiguredTargetCount`, `#nonFullscreenRenderTargetSamplerBindingsStillSkipLowColorBuffersBeyondConfiguredTargetCount`, `IrisImagesTest#renderTargetImagesFailFastWhenProgramReferencesImageBeyondConfiguredTargetCount`, focused sampler/image tests, adjacent binding/source tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent PBR sampler usage scope note: `PipelineManager.reloadShaderPack(...)` now resets `PBRTextureManager` sampler usage once per selected-pack reload, after derived terrain state succeeds and before active-pack publication. `ShaderWorldRenderingPipeline` no longer resets that shared flag during each dimension pipeline construction, so a later dimension without `normals` / `specular` cannot disable PBR refresh for an already-loaded PBR dimension. Focused `PipelineManagerSourceTest`, `ShaderWorldRenderingPipelineSourceTest`, `PBRTextureManagerTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent special-effect uniform client lookup note: `SpecialEffectUniforms` now resolves `Minecraft.getMinecraft()` inside `relativeEyePosition` and `lightningBoltPosition` supplier calls instead of caching it in a static field. This follows the 1.16.5 use-site lookup pattern and prevents early class loading from permanently forcing those built-ins/custom-expression symbols to zero in a later live client. Focused `SpecialEffectUniformsTest`, adjacent `CustomUniformExpressionManagerTest`, `ProgramBuilderReferenceUniformCoverageTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent custom texture successful-setup restore cleanup note: `CustomTextureManager.buildPngBinding(...)` and `buildDefaultNoiseBinding(...)` now treat previous texture-binding restore failure after successful upload as a pre-publication cleanup case. If a shader-pack PNG custom texture or generated `noisetex` uploads, records lifecycle metadata, and is added to `ownedTextureIds` but restore fails before the binding is returned into stage/noise state, the manager removes that ID from ownership and deletes it through the lifecycle-aware helper before rethrowing. `CustomTextureManagerTest#pngBindingDeletesGeneratedTextureWhenUploadOrSuccessfulRestoreFails`, `#defaultNoiseBindingDeletesGeneratedTextureWhenUploadOrSuccessfulRestoreFails`, focused `CustomTextureManagerTest`, adjacent `CustomImageManagerTest`, `ProgramSamplersTest`, `PBRTextureManagerTest`, `TextureLifecycleTrackerSourceTest`, `TextureInfoCacheTest`, `TextureFormatLoaderTest`, `FallbackTexturesTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent fallback white texture restore cleanup note: `FallbackTextures.createSingleColorTexture(...)` now tracks restore failure after successful setup. If the white fallback texture uploads successfully but restoring the previous 2D texture binding fails before `getWhiteTexture()` can publish the ID, the generated texture is deleted through the safe delete helper before the restore failure is rethrown. `FallbackTexturesTest#fallbackTextureOwnerCleansStateAndRollbackThroughSafeDelete`, focused `FallbackTexturesTest`, adjacent `CustomTextureManagerTest`, `ProgramSamplersTest`, `PBRTextureManagerTest`, `TextureLifecycleTrackerSourceTest`, `TextureInfoCacheTest`, `TextureFormatLoaderTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent PBR successful-load restore cleanup note: `PBRTextureManager.loadHolder(...)` now tracks the created `loadedHolder` through previous texture-binding restoration. If restore fails after a successful load but before `getOrLoadHolder(...)` can cache the holder, `closeLoadedHolderAfterRestoreFailure(...)` closes the non-default holder and suppresses cleanup failures onto the restore failure. `PBRTextureManagerTest#successfulPbrLoadClosesUncachedHolderWhenTextureBindingRestoreFails`, focused `PBRTextureManagerTest`, adjacent `SimplePBRLoaderTest`, `PBRAtlasTextureTest`, `PBRAtlasSpriteTest`, `AtlasPBRLoaderTest`, `TextureLifecycleTrackerSourceTest`, `TextureInfoCacheTest`, `CustomTextureManagerTest`, `ProgramSamplersTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent default sampler unit-zero note: `ProgramSamplers.Builder.addDefaultSampler(...)` now follows the local 1.16.5 unit-zero contract. Active default sampler names bind on texture unit `0`, and the builder fails if unit `0` is reserved or already managed instead of moving the sampler to a dynamic unit. `ProgramSamplersTest#defaultSamplerUsesTextureUnitZeroLikeReference`, `#defaultSamplerFailsWhenTextureUnitZeroIsReservedLikeReference`, and `#defaultSamplerFailsWhenTextureUnitZeroAlreadyHasManagedBindingLikeReference` cover the behavior. Focused `ProgramSamplersTest`, adjacent `IrisSamplersTest`, `CustomTextureManagerTest`, `CustomImageManagerTest`, `PBRTextureManagerTest`, `OculusRelictiumChunkProgramSourceTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent custom-image restore-failure cleanup note: `CustomImageManager.allocateTexture(...)` now treats prior-texture/active-texture restore failure as a pre-publication cleanup case. If a custom image texture is generated, allocated, and cleared but restore fails before the texture can be returned into the replacement map, the generated texture is deleted through the lifecycle-aware helper before the restore failure is rethrown. Focused `CustomImageManagerTest`, adjacent `CustomTextureManagerTest`, `ProgramImagesTest`, `ProgramSamplersTest`, `TextureInfoCacheTest`, `TextureLifecycleTrackerSourceTest`, `PBRTextureManagerTest`, `OculusRelictiumChunkProgramSourceTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent PBR sampler notifier preservation note: `ProgramSamplers.Builder.overrideBinding(...)` already preserved existing sampler notifiers when a notifier-less custom texture/resource override replaced an active binding; this slice added direct coverage for the PBR cases. `ProgramSamplersTest#notifierlessOverridePreservesExistingPbrSamplerNotifier` and `#notifierlessAliasOverridePreservesSharedPbrSamplerNotifier` prove `normals` keeps `StateUpdateNotifiers.normalTextureChangeNotifier` after direct and shared-alias overrides. Focused `ProgramSamplersTest`, adjacent custom-texture/PBR/Relictium tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent config reload initialization failure guard note: `ShaderPackReloader.reload()` now returns `false` before applying any configured pack when `OculusConfig.initialize()` cannot load or create the config file. This keeps stale in-memory selected-pack, enabled-state, option override, color-space, and shadow-distance values from rebuilding runtime binding state after a failed disk config refresh. Focused `ShaderPackReloaderTest`, adjacent `OculusConfigTest`, `OculusClientEventsSourceTest`, `ShaderPackScreenSourceTest`, `TextureFormatLoaderTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent color-space cleanup suppression note: `ColorSpaceComputeConverter` and `ColorSpaceFragmentConverter` now guard duplicate same-instance suppression while cleaning post-final compute/image state, fragment setup rollback, swap-texture lifecycle cleanup, aggregate destroy, process cleanup, and state restoration. Focused `ColorSpaceShaderSourceTest`, adjacent `ProgramImagesTest`, `ProgramActivationCleanupSourceTest`, `ComputeProgramSourceTest`, `OculusConfigTest`, `ShaderPackScreenSourceTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent base texture bind callback suppression note: `GlStateManagerStateMixin.runBindTextureCallback(...)` now guards duplicate same-instance suppression while aggregating base texture bind callbacks. The hook still attempts `StateUpdateNotifiers.notifyTextureBindingChanged`, `WorldRenderingPipeline.onBindTexture(texture)`, and final `GlStateManager.bindTexture(texture)` cache restoration before rethrowing the first failure. `GlStateManagerStateMixinSourceTest`, adjacent `StateUpdateNotifiersTest`, `TextureInfoCacheTest`, `TextureLifecycleTrackerSourceTest`, `PBRTextureManagerTest`, `ProgramSamplersTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent runtime notifier suppression note: `FanOutValueUpdateNotifier`, `StateUpdateNotifiers`, `FrameUpdateNotifier`, and `CustomUniformExpressionManager` combined dynamic notifiers now skip same-instance suppression while still aggregating distinct listener/delegate failures. This protects dynamic built-in uniforms, custom expressions, texture-size metadata, PBR sampler rebinds, fog/blend/object state, and Relictium wrapped bindings from losing the original failure to Java self-suppression. Focused `StateUpdateNotifiersTest`, `FrameUpdateNotifierTest`, and `CustomUniformExpressionManagerTest`, the adjacent active binding/uniform/PBR suite, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent cut-sandstone material-ID note: `BlockMaterialMapping` now resolves Complementary's `cut_sandstone` and `cut_red_sandstone` terrain material aliases to Forge 1.12 `sandstone:type=smooth_sandstone` and `red_sandstone:type=smooth_red_sandstone`. `BlockMaterialMappingTest#cutSandstoneAliasesMap112SmoothStatesWithoutBroadPreemption`, the combined `BlockMaterialMappingTest` plus `ShaderPackLoaderComplementaryTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run, so live Relictium material IDs and rendered terrain remain runtime-unverified.

Recent PBR holder cleanup fan-out note: `PBRTextureManager.clear()` now records runtime failures and hard errors from individual cached-holder cleanup, keeps closing later holders and registered atlas textures, clears holder/atlas/consumer state, and rethrows after the cleanup sweep. `PBRTextureManager.close()` now still attempts default normal/specular texture cleanup after a holder cleanup failure. `PBRTextureManagerTest#clearAttemptsLaterHolderCleanupWhenOneHolderThrows`, `#closeAttemptsDefaultTextureCleanupAfterHolderCleanupFailure`, focused PBR/custom-resource tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run, so live PBR sampler output, custom resource `_n` / `_s` reload behavior, Relictium wrapped PBR sampler output, and rendered output remain runtime-unverified.

Recent custom-image 3D texture metadata note: `CustomImageManager.allocateTexture(...)` now publishes lifecycle metadata for depth-backed custom images after `GL12.glTexImage3D(...)`, and `TextureInfoCache` records explicit texture target plus depth for level-0 3D allocations. `TextureInfoCacheTest`, `CustomImageManagerTest`, `ShaderPackLoaderComplementaryTest`, standalone `compileJava`, and touched-file hygiene passed; an earlier combined focused run hit Gradle 4.9 `ClassNotFoundException` initialization/reporting noise after test compilation. Minecraft and `runClient` were not run, so live 3D custom-image writes, paired sampler reads, texture-size visibility, reload behavior, and Relictium wrapped output remain runtime-unverified.

Recent PBR atlas delete unregister cleanup note: `PBRAtlasTexture.deleteGlTexture()` now records runtime failures or hard errors from `PBRTextureManager.unregisterAtlasTexture(...)`, still attempts `super.deleteGlTexture()`, notifies `TextureLifecycleTracker` from `finally`, and rethrows the first failure after cleanup with later failures suppressed. `PBRAtlasTextureTest#atlasDeleteClearsLifecycleTrackerEvenWhenSuperDeleteThrows`, focused PBR atlas/manager/sprite/loader/custom-texture tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent PBR atlas per-sprite animation update fan-out note: `PBRAtlasTexture.updateAnimations()` now records runtime failures or hard errors from individual animated PBR sprite updates, attempts every animated sprite in the atlas, and rethrows the first failure after the loop with later failures suppressed. `PBRAtlasTextureTest#atlasAnimationAttemptsEverySpriteBeforeRethrowing`, focused PBR atlas/manager/sprite/loader/custom-texture tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent PBR atlas animation update fan-out note: `PBRAtlasHolder.updateAnimations()` now records runtime failures or hard errors from normal/specular atlas animation updates, attempts both companion atlases, and rethrows the first failure after both attempts with later failures suppressed. `PBRTextureManagerTest#atlasAnimationUpdateAttemptsSpecularAfterNormalFailure`, `#atlasAnimationUpdateSuppressesSpecularFailureAfterNormalFailure`, focused PBR manager/atlas/loader/custom-texture tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent custom texture/noise destroy unregister cleanup note: `CustomTextureManager.destroy()` now records global noise sampler unregister runtime failures or hard errors, continues deleting every owned PNG/generated custom texture ID, clears manager state, and rethrows after cleanup. `CustomTextureManagerTest#destroyClearsOwnedTextureStateEvenWhenDeleteFailsAtSourceLevel`, focused custom texture/custom image/program sampler/PBR tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent custom-image destroy unregister cleanup note: `CustomImageManager.destroyTextureMap(...)` now records paired-sampler unregister runtime failures or hard errors per owned texture, continues deleting every owned custom-image texture, clears ownership, and rethrows after cleanup. `CustomImageManagerTest#destroyTexturesUnregistersOnlyOwnedCustomImageSamplerBindings`, focused custom image/custom texture/program image/sampler tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent Relictium vertex-format bootstrap and terrain writer byte proof note: `OculusTerrainVertexType.createFormat()` now calls `OculusChunkMeshAttributes.initialize()` before constructing Relictium's `GlVertexFormat.Builder`, so the builder's `EnumMap` is created after NORMAL, TANGENT, MID_UV, MATERIAL, and MID_BLOCK are injected. The new `OculusTerrainVertexBufferWriterNioTest` caught the old static-init failure and now writes a quad to verify the actual 52-byte vertex bytes for material/render type, propagated mid UV, normal/tangent, `at_midBlock.xyz`, and block emission. `OculusExtendedDataHelperTest` and `OculusTerrainVertexTypeSourceTest` still cover helper math and declared offsets. Focused Relictium/vertex/PBR/custom-resource tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent custom texture alias-group and unavailable albedo fallback note: `ProgramBuilder` now binds unavailable `tex`, `texture`, `gtexture`, `gcolor`, and `colortex0` declarations to one white fallback binding like the inspected 1.16.5 `IrisSamplers.addLevelSamplers(...)` path. `CustomTextureManager.applyCustomSamplers(...)` now expands stage custom texture override keys across source-backed render-target, level-albedo, and composite-depth alias groups before applying the binding, with low albedo aliases gated so `gcolor` / `colortex0` only replace `tex`-style names when the fallback group is already registered instead of replacing the external atlas sampler path. This matches the inspected 1.16.5 `ProgramSamplers.CustomTextureSamplerInterceptor` behavior for `IrisSamplers.addRenderTargetSamplers(...)`, `addLevelSamplers(...)`, and `addCompositeSamplers(...)`, and covers target-pack directives such as Complementary `texture.gbuffers.gaux4` and MakeUp `texture.*.gaux2`. `CustomTextureManagerTest#legacyStageOverrideUpdatesEquivalentColortexSamplerLikeReferenceInterceptor`, `#levelAlbedoStageOverrideUpdatesEquivalentActiveAliasLikeReferenceInterceptor`, `#unavailableAlbedoLowAliasOverrideUpdatesFallbackRegisteredTexLikeReferenceInterceptor`, `#albedoLowAliasOverrideDoesNotReplaceExternalAtlasSamplerWhenFallbackGroupIsAbsent`, `#compositeDepthStageOverrideUpdatesRegisteredEquivalentAliasLikeReferenceInterceptor`, `#deactivatedLegacyStageOverrideDoesNotUpdateEquivalentColortexSampler`, `InternalProgramBuilderPathTest`, focused `CustomTextureManagerTest`/`ProgramSamplersTest`/`IrisSamplersTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent GUI Cancel discard save-failure note: `ShaderPackScreen.dropChangesAndClose()` now closes only when `discardChanges()` successfully persists the restored baseline config. `discardChanges()` captures current config, restores baseline selected pack/enabled state/option overrides, saves with reason `discard`, then publishes baseline GUI state; on save failure it restores the pre-discard config snapshot, shows the save-failure notification, refreshes the still-pending view, and returns `false`. `ShaderPackScreenSourceTest`, `ShaderPackReloaderTest`, `OculusClientEventsSourceTest`, `OculusConfigTest`, and standalone `compileJava` passed in focused runs; the first combined focused run hit the known Gradle 4.9 XML report writer EOF, then smaller reruns passed. Minecraft and `runClient` were not run.

Recent custom-image replacement registration rollback note: `CustomImageManager.replaceTextures(...)` now keeps old textures and sampler aliases live until every replacement paired sampler alias registers. If registration fails, it unregisters partially registered replacement aliases by owned binding identity, restores old aliases, destroys only the replacement textures, and rethrows the original failure with rollback failures suppressed. `CustomImageManagerTest#replaceTexturesRollsBackRegistrationFailureBeforePublishingReplacementState`, focused custom image/custom texture/program image/sampler tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent custom-image restore aggregation note: `CustomImageManager.allocateTexture(...)` and `clearTextureFallback(...)` now record runtime failures and hard errors from image setup/clear before restoring texture binding, unpack alignment, and active texture state. Restore helpers attempt every applicable restore step, suppress restore failures onto an existing primary failure, aggregate restore failures when there is no primary failure, and keep rollback deletion reachable. `CustomImageManagerTest`, focused custom image/custom texture/program image/lifecycle/sampler tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent PBR atlas size-query restore note: `AtlasPBRLoader.getAtlasSize(...)` now records live atlas GL size query failures before restoring the previous 2D texture binding. Recoverable runtime query failures still fall back to sprite extents if restore also fails, hard errors keep the original hard error primary with restore context, and successful live queries fail fast on restore failure. `AtlasPBRLoaderTest`, focused PBR atlas/manager/lifecycle tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent fallback/default single-color texture restore note: `FallbackTextures.createSingleColorTexture(...)` and `PBRTextureManager.SingleColorTexture.upload()` now record runtime failures and hard errors from fallback/default texture setup before restoring the previous 2D texture binding. Restore failures are suppressed onto existing setup failures, rollback deletion remains reachable, and successful setup still fails fast if restore fails. `FallbackTexturesTest`, `PBRTextureManagerTest`, focused fallback/PBR/lifecycle/sampler tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent TextureInfoCache live query restore note: `TextureInfoCache.TextureInfo.fetchLevelParameter(...)` now records runtime failures and hard errors from live texture level-parameter queries before restoring the previous 2D texture binding. Restore failures are suppressed onto an existing query failure, and successful queries still fail fast if restore fails. `TextureInfoCacheTest`, focused texture metadata/uniform tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run. An attempted broader focused run that also included `FramebufferCompatibilityTest` failed on unrelated source-shape assertion `legacyFramebufferManagerEmptyFramebufferMatchesReferenceAttachmentRules` at line 341.

Recent texture-format parameter restore note: `TextureFormat.setupTextureParameters(...)` now records runtime failures and hard errors from LabPBR/PBR parameter setup before restoring the previous texture binding. Restore failures are suppressed onto the original setup failure, the no-bind-callback guard remains in place, and successful setup still fails fast on restore failure. `TextureFormatLoaderTest`, focused texture-format/custom-resource/PBR tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent PBR atlas binding-restore note: `PBRAtlasTexture.upload(...)` and `updateAnimations()` now record runtime failures and hard errors before restoring the previous texture binding. Restore failures are suppressed onto the original upload/update failure and no longer replace failed-upload fallback diagnostics; restore failure after successful work still fails fast. `PBRAtlasTextureTest`, focused PBR atlas/manager/loader/lifecycle tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent custom texture setup restore note: `CustomTextureManager.buildPngBinding(...)` and `buildDefaultNoiseBinding(...)` now record setup/upload runtime failures and hard errors before restoring the previous texture binding. Restore failures during rollback are suppressed onto the original setup failure, generated texture IDs still go through `deleteOwnedTexture(...)`, and successful setup still fails fast if restore fails. `CustomTextureManagerTest`, focused custom texture/lifecycle/sampler tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent PBR holder texture-binding restore note: `PBRTextureManager.loadHolder(...)` now records companion-load failures before the `finally` block restores the previous texture binding. Restore failures after recoverable runtime or corrupt-ZIP load failures are suppressed onto the original failure and do not replace default-holder fallback; restore failures after hard-error load failures are attached to the hard error; restore failures after successful loads still fail fast. `PBRTextureManagerTest`, focused PBR/custom-texture/lifecycle tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent Relictium override rebuild cleanup note: `OculusRelictiumChunkProgramOverrides.rebuildShaders(...)` now catches runtime failures and hard errors from the override creation loop, calls `deleteShaders()` to clear partial programs/cache keys, and rethrows. `createShader(...)` now tracks the linked handle, built non-owning `Program` binding wrapper, and constructed `ChunkProgram`; failed construction destroys the wrapper before deleting the handle, while failures after construction use `program.delete()`. Focused Relictium source/bytecode tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent color-space compute destroy cleanup note: `ColorSpaceComputeConverter.destroyResources()` now snapshots the owned `ComputeProgram`, clears `program` and `targetTexture` before low-level cleanup can throw, then destroys the captured program reference. `ColorSpaceShaderSourceTest`, focused color-space/program-image tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent PBR failed-load cleanup isolation note: `PBRTextureManager.loadHolder(...)` now calls `cleanupAfterLoadFailure(...)` from runtime, Java 8 corrupt-ZIP, and hard-error load failure paths. The helper closes accepted normal/specular companion textures and suppresses cleanup failures onto the original failure, so recoverable PBR loads still return the default holder. Focused PBR/custom-texture tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent custom texture initialize cleanup note: `CustomTextureManager.initialize()` now calls `cleanupAfterInitializeFailure(...)` on runtime or hard-error setup aborts. The helper calls `destroy()` and suppresses cleanup failures onto the original initialization failure, preserving root-cause diagnostics while still tearing down partial PNG/noise/custom-resource state. Focused custom texture/texture lifecycle/PBR tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent GUI/config save-failure reload guard note: `ShaderPackScreen.updateConfigAfterApply(...)` now snapshots selected pack, enabled state, and option overrides before mutating config; if `saveConfig("apply")` fails, it restores that snapshot, leaves the pending pack unapplied, and returns before `ShaderPackReloader.reload()` can re-read stale disk config. `OculusClientEvents.handleToggle(...)` now restores the previous enabled flag and skips reload if saving the toggle state fails. Focused GUI/config/reload source tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent immediate config-control rollback note: shader-pack GUI color-space cycling and max-shadow-distance slider edits now restore the previous live config value if saving fails. This prevents failed persistence from still changing `currentColorSpace`, color-space converter selection, default shadow-distance culling/display, or success notifications through the live `OculusConfig`. Focused GUI/config/reload source tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent shader-option reset pending-state note: `ShaderPackScreen.resetCurrentPackOptions()` now clears only the screen working option values and marks pending changes. It no longer clears live `OculusConfig` option overrides before Apply, so reload-key or resource-triggered reload cannot consume reset shader options while the GUI still presents the reset as pending. Focused GUI/config/reload source tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent GUI Apply pack-publication note: `ShaderPackScreen.applyChanges()` still calls `reloadSelectedPackForApply(...)` before config save to validate pending option-controlled pack state, but it now assigns `this.currentPack = packToApply` only after `updateConfigAfterApply(...)` and `ShaderPackReloader.reload()` succeed. Failed config saves or failed shared reloads leave the screen-local applied option source on the previous state instead of making uniforms, samplers, images, PBR, and Relictium terrain wrappers look applied ahead of the active runtime. Focused GUI/config/reload source tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent GUI option import/profile effective-change note: `ShaderPackScreen.applyImportedValues(...)` and `applyProfile(...)` now delegate to a shared effective-change helper. It sanitizes raw values, applies them to a candidate `MutableOptionValues`, compares the effective before/after option maps, and only then marks pending state. Unknown keys, invalid booleans, and default-collapsed values no longer create false pending state; real override removals still count. Focused GUI/option/config tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent color-space fragment swap texture cleanup note: `ColorSpaceFragmentConverter.destroyResources()` now snapshots and clears owned program/framebuffer/swap-texture fields before cleanup can throw, attempts every cleanup step through the aggregate helper, and invalidates texture lifecycle metadata even when swap texture GL deletion fails. `createSwapTexture(...)` now fails fast on non-positive allocation and suppresses rollback failures onto setup failures. `ColorSpaceShaderSourceTest`, focused color-space/lifecycle tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent center-depth sampler destroy lifecycle note: `CenterDepthSampler.destroy()` now marks the owner destroyed from `finally` after the first cleanup attempt, and `deleteTexture(...)` attempts `TextureLifecycleTracker.onDeleteTexture(...)` even when `GL11.glDeleteTextures(...)` throws. `CenterDepthSamplerSourceTest`, focused postprocess/lifecycle source tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent custom image active-texture capture note: `CustomImageManager.allocateTexture(...)` now captures `GL_ACTIVE_TEXTURE` inside the same cleanup scope that owns a generated custom-image texture ID. The previous active unit starts as `GL_TEXTURE0`, is marked captured only after `glGetInteger(GL_ACTIVE_TEXTURE)` succeeds, and is restored only when captured. If setup fails before ownership is returned, the generated texture still goes through `deleteTexture(...)` and `TextureLifecycleTracker.onDeleteTexture(...)`. `CustomImageManagerTest#allocateTextureDeletesGeneratedTextureWhenSetupFails`, focused custom image/custom texture/lifecycle tests, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent Relictium context-holder timing note: `ChunkBuildBuffersMixin` now returns a lazily created `BlockContextHolder` from `oculus_getContextHolder()` and creates it at `ChunkBuildBuffers.init` HEAD before Relictium creates terrain writers. Installed Relictium 1.2.0 bytecode shows `performBuild(...)` calls `ChunkBuildBuffers.init(...)` before block/fluid terrain render calls, while the Oculus build-task mixin reads the holder at method entry. This closes the source-level null-holder path for Relictium material IDs, render type, chunk-local `at_midBlock`, and block emission. `BufferBuilderExtendedVertexFormatMixinSourceTest`, `RelictiumTerrainBridgeBytecodeTest`, the broader Agent 3 focused suite, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent GUI keyboard selection note: keyboard up/down pack selection in `ShaderPackSelectionList.moveSelection(...)` now mirrors mouse selection by enabling shaders in the pending top-row state before refreshing the selected pack and notifying the screen. `ShaderPackScreenSourceTest#keyboardPackSelectionEnablesShadersBeforeSelectionRefresh` passed as part of the GUI/config/reload and broader Agent 3 suites. Live GUI behavior remains runtime-unverified.

Recent shadow color image/sampler boundary note: `ProgramSamplers` no longer treats `shadowcolorimg0` or `shadowcolorimg1` as built-in sampler unit aliases. The local 1.16.5 `IrisImages.addShadowColorImages(...)` binds those names as images, while `IrisSamplers.hasShadowSamplers(...)` only queries them to request shadow targets. `ProgramSamplersTest#shadowColorImageNamesAreNotSamplerUnitAliasesLikeReference` covers both the no-unit resolution and no-binding behavior for active sampler declarations with no custom/registry binding. Focused sampler/image/shadow tests, the broader Agent 3 source suite, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent legacy render-target alias unit note: `ProgramSamplers` now derives `gcolor`, `gdepth`, `gnormal`, `composite`, and `gaux1..gaux4` from `PackRenderTargetDirectives.LEGACY_RENDER_TARGETS`, so those aliases share the same fixed units as `colortex0..7`. This closes the source-level drift where `gaux4` could bind on the old `colortex5` unit while `IrisSamplers` and custom texture flip handling treated it as `colortex7`. `ProgramSamplersTest#legacyRenderTargetAliasesUseReferenceColortexUnits` and `#registryBackedGaux4SharesColortex7UnitWhenRegisteredFirst` cover the mapping and Complementary-style registration order. Focused sampler/custom-texture tests, the broader Agent 3 source suite, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent held block-light directive note: direct `heldBlockLightValue` binding now captures `PackDirectives.isOldHandLight()` through `ProgramBuilder`, matching the inspected 1.16.5 held-item supplier construction boundary. `IdMapUniformsTest` covers the main/offhand block-item light behavior for `oldHandLight=true` and `false`, and `ProgramBuilderReferenceUniformCoverageTest` source-pins the direct uniform capture path. Focused IdMap/program-builder/custom-expression tests and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent custom-image sampler binding guard note: `CustomImageManagerTest` now pins that compiled paired custom-image sampler bindings re-query the current texture ID by image name across replacement/resize and that Complementary-style depth-backed custom images bind as `GL_TEXTURE_3D` while plane images bind as `GL_TEXTURE_2D`. The image/barrier audit found the existing 1.12 compute pre-dispatch and composite/final post-compute barriers aligned with the inspected 1.16.5 source, so no render-target, shadow, or postprocess internals were edited for that part. The broad Agent 3 source suite also caught a missing GUI translation, and the bundled English resources now include `options.iris.applyFailed`. Focused image/sampler tests, the broader Agent 3 source suite, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent texture lifecycle delete-notification isolation note: `TextureLifecycleTracker.onDeleteTexture(...)` now attempts `TextureTracker`, `TextureInfoCache`, and `PBRTextureManager` invalidation independently through a shared helper, aggregates suppressed failures, and logs instead of throwing from the delete-notification path. This protects custom texture, custom image, fallback texture, PBR companion, and atlas cleanup from one lifecycle callback short-circuiting later teardown during reload/fallback/shutdown. Focused texture/PBR/custom-resource tests, the broader Agent 3 source suite, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent program activation failure cleanup note: `Program.use()` now wraps GL program bind plus uniform, sampler, and image updates, and `ComputeProgram.dispatch(...)` wraps activation, the pre-dispatch barrier, work-group resolution, and dispatch. Runtime failures and hard errors clear active uniforms, samplers, images, and the GL program before rethrowing the original failure with cleanup failures suppressed. `ProgramActivationCleanupSourceTest`, focused program-binding tests, the broader Agent 3 source suite, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent Relictium setup failure cleanup note: `OculusRelictiumChunkProgram.setup(...)` now wraps its split binding sequence in the same active-binding cleanup policy. It still updates Oculus uniforms before Relictium restores MVP/scale/base sampler uniforms, then updates Oculus samplers/images and wrapper matrices, but any runtime failure or hard error clears active Oculus bindings and the GL program before rethrow. `OculusRelictiumChunkProgramSourceTest`, `ProgramActivationCleanupSourceTest`, `ComputeProgramSourceTest`, standalone `ProgramSamplersTest`, the broader Agent 3 source suite with `--max-workers=1`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent texture-format reload ZipError guard note: `TextureFormatLoader.onFormatChange()` now catches `RuntimeException | ZipError` around the optional `ShaderPackReloader.reload()` callback after `optifine/texture.properties` changes. The configured-pack and active-pack reload paths already catch corrupt ZIP `ZipError`; this closes the smaller callback boundary so resource reload does not escape solely from Java 8 surfacing corrupt ZIP data as an `Error` after PBR holders were cleared. `TextureFormatLoaderTest`, `ShaderPackReloaderTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent program destroy cleanup aggregation note: `Program.destroyInternal()` now uses `runCleanup(...)` for instance-scoped active uniform cleanup, sampler cleanup, image cleanup, and owned `glDeleteProgram(...)`. A cleanup failure during destroy no longer skips later active binding cleanup or owned handle deletion, and non-owning Relictium wrappers still skip the GL delete step. `OculusRelictiumChunkProgramSourceTest`, `ProgramActivationCleanupSourceTest`, `ProgramImagesTest`, `ProgramSamplersTest`, `ProgramUniformsTest`, and standalone `compileJava` passed; Minecraft and `runClient` were not run. The first focused run without `--max-workers=1` hit the known Gradle 4.9 XML report writer EOF for `ProgramSamplersTest`; the one-worker rerun passed.

Recent GlResource failed-destroy invalidation note: `GlResource.destroy()` now invalidates the wrapper from a `finally` block after the first destroy attempt, successful or failed. `GlResourceTest` covers both outcomes and repeated destroy idempotence. A focused Java 8 run of `GlResourceTest`, `ProgramActivationCleanupSourceTest`, `ProgramImagesTest`, and `OculusRelictiumChunkProgramSourceTest` passed with `--max-workers=1`, and standalone `compileJava` passed. Minecraft and `runClient` were not run.

Recent texture lifecycle source-guard note: `TextureLifecycleTrackerSourceTest` now checks direct texture upload/delete call sites individually. Each `GL11.glTexImage2D(...)` and `GL11.glDeleteTextures(...)` statement must be followed by the matching `TextureLifecycleTracker` notification before the next direct call, while extra tracked boundaries such as `OculusRenderSystem.copyTexImage2D(...)` are allowed. Focused texture lifecycle tests, the broader Agent 3 source suite, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent PBR atlas registry cleanup note: `PBRTextureManager.clear()` now closes any registered PBR atlas textures left in `atlasHolders` after holder cleanup before dropping the animated-atlas registry. `PBRTextureManagerTest#clearClosesRegisteredAtlasTexturesWithoutCachedHolder`, `#clearDoesNotDoubleDeleteSharedRegisteredAtlasTexture`, and `#clearClosesRegisteredAtlasTexturesBeforeDroppingAtlasRegistry` cover the source/behavior boundary. Focused PBR atlas/manager tests and the broader Agent 3 source suite passed; Minecraft and `runClient` were not run.

Recent PBR sampler exact-case notifier note: `ProgramSamplers.Builder.textureChangeNotifierForSampler(...)` now attaches normal/specular texture-change notifiers only for exact `normals` and `specular` sampler names. This matches the inspected local 1.16.5 `IrisSamplers.addLevelSamplers(...)` registration surface and the port's exact `PBRTextureManager.isPbrSamplerName(...)` usage detector. `ProgramSamplersTest` and `PBRTextureManagerTest` cover uppercase and mixed-case misses. Focused sampler/PBR tests, the broader Agent 3 source suite, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

Recent shared sampler alias override note: `ProgramSamplers.Builder.overrideBinding(...)` now updates sibling aliases on the same texture unit when they still point at the same previous binding. This matches the inspected 1.16.5 custom texture interceptor behavior where a custom texture matching any name in an alias list overrides the whole helper sampler supplier. `ProgramSamplersTest#overridingOneAliasUpdatesSharedSamplerBindingGroupLikeReference` covers overriding either `shadowtex0` or `shadow`. Focused sampler/custom-texture tests, the broader Agent 3 source suite, and standalone `compileJava` passed; Minecraft and `runClient` were not run.

`ShaderPackLoaderComplementaryTest` is now a real-pack loader guard for both Complementary and MakeUp directory/zip packs when those local files exist. It also pins Complementary's `customTexture.textureAtlas` as exact-case `textureAtlas` resource data for every texture stage, pins MakeUp's option-preprocessed `gaux2` cloud custom textures for default and `CLOUD_VOL_STYLE=1`, pins MakeUp block 10090 double-colon BetterEnd material entries as parsed modded block IDs, and prepares MakeUp runtime program sources across Overworld, Nether, End, and fallback base roots, including availability variants. It is still parser/resource/source-preparation evidence, not GL compilation or rendered-output proof.

Recent shader-source graph note: `ShaderPackLoader` now seeds the include/option graph from `ShaderPackSourceNames.findPresentSources(...)` for the root shader folder and usable dimension folders, then follows includes. A synthetic `ShaderPackLoaderComplementaryTest` case proves included files still contribute options while unreferenced shader-like files do not create options or source-provider entries. Complementary and MakeUp `dh_*` files remain target-pack observations, not active Distant Horizons support.

Recent config persistence note: `OculusConfig` now reads per-pack option override keys from the last separator in `option.<pack>.<id>`, so dotted pack names such as `ComplementaryReimagined_r5.6.1.zip` keep overrides like `SHADOW_QUALITY`. GUI pack selection now remains pending until Apply/Done instead of saving `selectedPackName` immediately, matching the local 1.16.5 apply boundary; clicking a pack while shaders are disabled keeps the pending auto-enable state across selection refresh, and the applied-pack marker stays on the captured baseline pack while changes are pending. Invalid stored selections are still cleared immediately during recovery. `OculusConfigTest` and `ShaderPackScreenSourceTest` cover the source boundaries; GUI Apply and live reload after option edits remain in-client validation items.

Recent GUI/toggle reload note: `ShaderPackScreen.applyChanges()` now saves the pending selected pack, enabled flag, and option overrides before calling `ShaderPackReloader.reload()`, rather than reloading `PipelineManager` directly from GUI-local state. Main-screen Escape now follows the reference close/apply path while Cancel remains the discard path. Toggle key handling now also saves the enabled flag and calls `ShaderPackReloader.reload()`, matching the inspected 1.16.5 `Iris.toggleShaders(...)` save-then-reload boundary. Public reload prepares the loaded-world pipeline after applying config or fallback, matching the inspected 1.16.5 `Iris.reload()` tail. `ShaderPackScreenSourceTest`, `OculusClientEventsSourceTest`, and `ShaderPackReloaderTest` cover the source ordering; live GUI Apply/Escape/toggle/resource-reload behavior remains runtime-unverified.

Recent pipeline reload teardown note: `PipelineManager.destroyPipeline()` now isolates teardown failures per dimension. Texture-state reset and `WorldRenderingPipeline.destroy()` are logged independently, later dimension pipelines still tear down, and `pipelinesPerDimension`, the active `pipeline`, and `versionCounterForSodiumShaderReload` are cleared/bumped from a `finally` block. Cached pipeline replacement inside `preparePipeline(...)` and first-frame runtime fallback now use the same isolated cleanup helper before publishing the replacement or fixed-function fallback pipeline, so cleanup exceptions cannot block source-level fallback state or the Relictium/Sodium override version bump. Shader pipeline creation and delayed first-frame shader setup now also catch Java 8 `ZipError`, record failed-pack backoff through the same fallback path, and install fixed-function fallback for corrupt ZIP-backed shader resources instead of letting the hard error escape. This protects shader reload, shader disable, mode replacement, fixed-function fallback, corrupt-pack recovery, and Relictium/Sodium override refresh from stale state if one pipeline cleanup step fails. `PipelineManagerSourceTest`, reload, config, and GUI source tests passed; live reload/dimension-switch/corrupt-pack fallback behavior remains runtime-unverified.

Recent shader/program object cleanup note: `ProgramBuilder` now destroys non-null raster and compute `GlShader` objects from `finally` blocks around program creation, and `GlShader` deletes its allocated shader handle if construction fails after `glCreateShader`. `ProgramCreator` also catches shader-detach and failed-program-delete cleanup failures, suppressing them on the original link/create failure and logging cleanup failures after successful link. Cleanup failures no longer mask compile/link diagnostics, stop later shader-object cleanup, or block a successfully linked program from returning. Focused `InternalProgramBuilderPathTest`, `ProgramCreatorSourceTest`, and `PipelineManagerSourceTest` passed, and standalone `compileJava` passed; Minecraft/runtime driver cleanup and failed-pack fallback remain unverified.

Recent Relictium linker cleanup note: `OculusRelictiumProgramLinker` now applies the same cleanup isolation to Relictium terrain program links. Shader detach, shader destroy, and failed-program delete cleanup failures are suppressed onto real link failures, while cleanup failures after a successful link are logged and do not block the linked handle from returning to wrapper setup. `OculusRelictiumProgramLinkerSourceTest` and focused Relictium tests passed; live driver cleanup and terrain fallback behavior remain runtime-unverified.

Recent config key compatibility note: `OculusConfig` now reads `shaderPack`, `enableShaders`, and `enableDebugOptions` as 1.16.5-style fallbacks when the port-native `selectedPackName`, `shadersEnabled`, and `debugEnabled` keys are absent, and saves those reference keys alongside the port keys. Port-native keys still take precedence when both forms exist, and internal/no-pack state persists as `shaderPack=` like the reference config. `OculusConfig.initialize()` now saves a default config file on first run, and `Oculus.initializeConfig()` uses that path during client construction/pre-init. The reference `disableUpdateMessage` key is also loaded with exact-true semantics, saved, and exposed through accessors; this is persisted config-surface parity only because no active 1.12 update-message display path has been proven. `OculusConfigTest` and `ShaderPackReloaderTest` cover the headless source path; reload-key/resource-reload, update-message, and GUI flows are still runtime-unverified.

Recent reload config recovery note: public shader reload now calls `OculusConfig.initialize()` rather than bare `load()`, so a missing/deleted config file is recreated with reference keys before the configured pack decision, matching the local 1.16.5 `Iris.reload()` boundary. `ShaderPackReloader` also catches Java 8 `ZipError` beside normal exceptions for configured-pack and active-pack reload failures, so corrupt zip shader packs enter the disable/fallback path instead of escaping recovery. `ShaderPackReloaderTest` covers this source/headless behavior; live reload-key, GUI Apply, toggle, resource-reload, and corrupt-zip messaging remain runtime-unverified.

Recent SSBO binding/cleanup note: `ShaderStorageBufferManager.initialize()` now restores the previously bound generic `GL_SHADER_STORAGE_BUFFER` after each requested `bufferObject.*` allocation and bind-base setup. `destroy()` now catches `RuntimeException | Error` from per-buffer deletion, so a hard driver-side delete failure cannot stop later SSBO cleanup or prevent the manager from clearing owned handles and initialized state. `ShaderStorageBufferManagerTest` and standalone `compileJava` passed; live SSBO allocation, shader-visible contents, and driver failure behavior remain runtime-unverified.

Recent color-space config note: invalid persisted `colorSpace` or `maxShadowRenderDistance` values now recover like the inspected local 1.16.5 `IrisConfig` path: load resets the paired settings to `colorSpace=SRGB` and `maxShadowRenderDistance=32`, logs the reset, and rewrites the config. Recognized aliases such as `display-p3` are still accepted without forced rewrite. `OculusConfigTest` covers the persisted fallback, paired reset, and alias preservation; live reload-key/GUI behavior and non-SRGB visual output remain runtime-unverified.

Recent shadow-distance config note: `OculusConfig` now persists `maxShadowRenderDistance`, defaults it to 32 chunks like local 1.16.5, rewrites invalid persisted values during load, and `ShadowRenderer` uses it for negative `shadowDistanceRenderMul` in normal/default culling instead of a fixed fallback. The renderer also skips the shadow pass when the effective reference display distance is zero: default/unforced user distance `0`, or an explicit non-negative multiplier that rounds to `0` chunks. The 1.12 shader-pack screen now exposes `maxShadowRenderDistance` through an immediate-save slider row, tracks drag updates until mouse release, skips duplicate writes when the rounded value is unchanged, displays forced shader-pack distances, and disables or cancels editing when the active pipeline reports a forced value like `IrisVideoSettings.RENDER_DISTANCE`. Explicit negative shader-pack multipliers and explicit shader-pack-disabled culling keep their reference negative-value branches. `OculusConfigTest`, `ShadowEntityCullingTest`, and `ShaderPackScreenSourceTest` cover the source path; live GUI slider rendering/dragging, reload-key behavior, Relictium shadow culling, and shadow rendered output remain runtime-unverified.

Recent postprocess source-parity note: `CompositeRenderer` now mirrors the inspected 1.16.5 constructor loop by iterating exactly the source array length and only creating compute-only passes at matching source-array indices. The invalid-source compute-only branch also follows the reference non-null compute-array check; it does not pre-scan for valid compute sources before adding the pass. `renderAll()` now returns only when destroyed, so an empty pass list still reaches the reference cleanup block. The related disabled-compute suspicion was checked and left unchanged because 1.16.5 filters disabled `.csh` paths through `ShaderPack.sourceProvider`, while the 1.12 port filters in `ProgramSet` with the same path-derived disabled keys.

Recent render-target preparation note: `ShaderWorldRenderingPipeline.clearRenderTargets()` now restores the default texture unit before disabled-shadow/main render-target clears and rebinds `Minecraft.getFramebuffer().bindFramebuffer(true)` in a `finally` cleanup after the clear block, matching the local 1.16.5 `DeferredWorldRenderingPipeline.prepareRenderTargets()` texture-unit and main-framebuffer boundary. `ShaderWorldRenderingPipelineSourceTest` has a source guard for this shape. The older handoff note about `ProgramImagesTest` calling a missing helper is stale in the current tree; the owned sampler/image focused tests compile and pass.

Recent center-depth compile note: a Java 8 compile blocker in `CenterDepthSampler` was found during sampler verification. The constructor now copies generated texture IDs into final locals before `OculusRenderSystem.withDefaultTextureBindingRestored(...)` captures them in the setup lambda. This is a compile-compatibility fix only; it does not change center-depth texture setup order or ownership. Standalone `compileJava` passed after the fix, but live center-depth contents remain runtime-unverified.

Recent render-stage boundary note: `ShaderWorldRenderingPipeline.beginLevelRendering()` now leaves `renderStage` / `WorldRenderingPhase` at `NONE` through `FrameUpdateNotifier.onNewFrame()` and render-target clears, then switches to `SKY` after target preparation, matching the inspected local 1.16.5 `beginLevelRendering()` ordering. `finalizeLevelRendering()` now also resets phase and override phase and enters fullscreen postprocess scope before center-depth sampling, composite, final, and color-space work, so fullscreen passes do not inherit the last world draw phase and gbuffer program sync cannot run during postprocess. `ShaderWorldRenderingPipelineSourceTest` guards both orderings; runtime visual validation is still missing.

Recent frame-update notifier aggregation note: `FrameUpdateNotifier.onNewFrame()` now snapshots listeners and attempts every registered callback before rethrowing the first runtime or hard-error failure with later failures suppressed. This protects smoothed built-ins and frame-start custom uniform state from one failed listener blocking later `wetness`, `eyeBrightnessSmooth`, compatibility smoothing, or directive-derived refreshes. `FrameUpdateNotifierTest`, `SmoothedFloatTest`, `GameplayUniformsTest`, `CompatibilityUniformsTest`, and `CustomUniformExpressionManagerTest` passed; live smoothed uniform timing is still runtime-unverified.

Recent custom smooth-ID compatibility note: `CustomUniformExpressionManager` keeps `smooth(id, ...)` accumulators scoped to each compiled expression node. The optional integer ID is not a manager-global key in this port because the local Complementary target pack reuses explicit IDs `4` and `54`, and the local 1.16.5 source does not prove keyed custom-expression state. `CustomUniformExpressionManagerTest` now source-pins the target-pack duplicate-ID expressions and verifies same-ID expressions keep independent accumulators across frames. Live OptiFine/Iris duplicate-ID behavior and shader-visible timing remain runtime-unverified.

Recent compatibility precipitation camera-Y note: `CompatibilityUniforms.isPrecipitationRain` now uses `CapturedRenderingState.INSTANCE.getCameraPosition()[1]` through a tested helper instead of the raw 1.12 `Entity.posY`. This matches the inspected 1.16.5 `HardcodedCustomUniforms` path that reads `CameraPositionTracker.getCurrentCameraPosition().y` for the `< 96` rain threshold. Focused compatibility/custom-uniform/frame tests and standalone `compileJava` passed; live rain/biome/camera timing remains runtime-unverified.

Recent camera vector null-state note: `GameplayUniforms.getEyePosition()` and `getPlayerLookVector()` now zero their reused arrays when no render-view entity is available. This prevents stale camera vector data from leaking into built-in uniforms or custom expressions during headless evaluation, reload, fallback, or world teardown. `GameplayUniformsTest`, focused custom-expression/program-builder tests, and standalone `compileJava` passed; live shader-visible camera timing remains runtime-unverified.

Recent gbuffer framebuffer note: `ShaderWorldRenderingPipeline.bindGbufferFramebuffer(...)` now reuses the draw-buffer state established by `RenderTargets.createGbufferFramebuffer(...)` / `createColorFramebuffer(...)`; it binds the cached framebuffer and no longer reissues `GL20.glDrawBuffers(...)` or `noDrawBuffers()` per pass. This matches the local 1.16.5 `DeferredWorldRenderingPipeline.Pass.use()` boundary. Its no-compiled-program fallback now rebinds `Minecraft.getFramebuffer().bindFramebuffer(true)` through the existing main-framebuffer helper instead of binding raw framebuffer `0`, matching the 1.16.5 fixed-function main-render-target boundary. `ShaderWorldRenderingPipelineSourceTest#gbufferFramebufferBindReusesFramebufferDrawBufferStateLikeReference` and `#gbufferNoProgramFallbackBindsMinecraftMainFramebuffer` pin the source shape; live gbuffer output still needs client validation.

Recent pipeline failure-boundary note: `ShaderWorldRenderingPipeline.bindProgram(...)` no longer catches `RuntimeException` from gbuffer framebuffer binding, `program.use()`, or state override application; those failures now propagate like the 1.16.5 pass-use path instead of logging and silently skipping one pass. `ShadowRenderer` also fails construction on incomplete shadow framebuffers, `shouldDisableVanillaEntityShadows()` now returns `shadowRenderer != null`, `shouldDisableDirectionalShading()` returns `!oldLighting`, and forced shadow render-distance display uses the 1.16.5 integer chunk rounding. Source coverage lives in `ShaderWorldRenderingPipelineSourceTest` and `ShadowRendererSourceTest`; runtime visual parity is still unproven.

Recent sampler/PBR binding note: `ProgramSamplers` now uses the local 1.16.5-style `ValueUpdateNotifier` lifecycle. Activating a new sampler set clears the previous active sampler set through `clearActiveSamplers()`, so previous listeners are detached, previous managed sampler texture units are unbound, and the caller's active texture unit is restored before replacement bindings attach notifier callbacks after their first bind. Notifier-triggered rebinds preserve the caller's active texture unit, and auto-bound `normals` / `specular` map to the normal/specular texture-change notifiers. `ShaderWorldRenderingPipeline.onBindTexture(...)` now also refreshes those PBR samplers for texture ID `0` unbinds like the local 1.16.5 path instead of leaving stale companion textures active. `CustomTextureManager` now records level-0 `TextureInfoCache` metadata for shader-pack PNG uploads after vanilla `TextureUtil.uploadTextureImageAllocate(...)`, and `PBRAtlasTexture` records the same kind of metadata immediately after vanilla `TextureUtil` atlas allocation; both keep texture-size metadata source-backed for `atlasSize` / `gtextureSize`. Pipeline teardown now unbinds 2D textures across the reported `SamplerLimits` texture-unit range when a GL context is available, with a 16-unit fallback for headless probes; this covers dynamic sampler units starting at 16 during reload/fallback cleanup. Focused `CustomTextureManagerTest`, `ProgramSamplersTest`, `InternalProgramBuilderPathTest`, `OculusRenderSystemCapabilityDispatchTest`, `PipelineManagerSourceTest`, `PBRAtlasTextureTest`, `PBRTextureManagerTest`, and `ShaderWorldRenderingPipelineSourceTest` passed; live PBR sampler output and Relictium sampler output remain runtime-unverified.

Recent material-map note: `BlockMaterialMapping` now supports unguarded modern `mushroom_stem` rows by mapping bytecode-confirmed 1.12 huge-mushroom `variant=stem` and `variant=all_stem` states from both red and brown mushroom blocks to the stem material ID, while broad mushroom rows skip those states only when the split row exists. Complementary's raw mushroom rows are guarded by `MC_VERSION >= 11300`, so they are inactive for this 1.12.2 port; this is source/test hardening, not a runtime proof. Focused `BlockMaterialMappingTest` passed; live Relictium `blockId` values remain runtime-unverified.

Recent base texture bind callback aggregation note: `GlStateManagerStateMixin` now attempts texture binding notifier publish, `pipeline.onBindTexture(texture)`, and the final cache-restoring `GlStateManager.bindTexture(texture)` call even when an earlier callback fails. It rethrows the first runtime or hard-error failure with later failures suppressed. Focused mixin/render-system/state/pipeline source tests and standalone `compileJava` passed; live texture-size timing, PBR sampler output, resource reload, and Relictium sampler output remain runtime-unverified.

Recent PBR texture-change publish aggregation note: `PBRTextureManager.notifyPBRTexturesChanged()` now attempts both normal and specular texture-change publishes before rethrowing the first listener failure, with later failures suppressed. `PBRTextureManagerTest` covers the behavior. Live PBR sampler output, custom-resource `_n` / `_s` updates, reload timing, and Relictium sampler output remain runtime-unverified.

Recent resource-reload PBR binding reset note: `TextureFormatLoader.reload(...)` remains the PBR holder cleanup owner during texture-manager resource reload, and `TextureManagerPBRReloadMixin` now calls `WorldRenderingPipeline.resetPbrTextureBindings()` after that reload instead of clearing holders again. `ShaderWorldRenderingPipeline.resetPbrTextureBindings()` resolves the default PBR holder and publishes PBR texture-change notifiers when PBR samplers are active, so active `normals` / `specular` samplers do not rebind deleted companion IDs before the next base texture bind. Focused mixin/pipeline/texture-format/PBR/sampler tests and standalone `compileJava` passed; live resource reload and PBR sampler output remain runtime-unverified.

Recent corrupt optional texture-resource note: texture-format metadata, eager custom resource texture loading, simple PBR companion loading, atlas PBR sprite loading, and PBR holder loading now treat Java 8 `ZipError` from corrupt ZIP-backed resource packs as an optional-resource failure. The source-backed result is fallback to no texture format, missing/default custom resource/PBR textures, and continued sampler construction; non-zip hard `Error`s still propagate from `PBRTextureManager` after cleanup. Focused `CustomTextureManagerTest`, `TextureFormatLoaderTest`, `SimplePBRLoaderTest`, `AtlasPBRLoaderTest`, and `PBRTextureManagerTest` passed; live corrupt resource-pack reload behavior and rendered sampler output remain runtime-unverified.

Recent simple PBR companion handoff note: `SimplePBRLoader.load(...)` now follows the local 1.16.5 ordering by creating normal and specular companions before accepting either one. If a later companion or consumer failure leaves a created normal companion unaccepted, the loader closes it before rethrowing; accepted textures remain covered by `PBRTextureManager.loadHolder(...)` cleanup. `SimplePBRLoaderTest#loadCreatesBothCompanionsBeforeHandingOffLikeReference` and `#specularRuntimeFailureClosesUnacceptedNormalTextureBeforeRethrowing`, the focused PBR/custom texture test set, standalone `compileJava`, and touched-file hygiene passed; live simple PBR output, custom resource `_n` / `_s` sampler output, Relictium wrapped-program PBR sampler output, and resource reload behavior remain runtime-unverified.

Recent PBR holder clear reentrancy note: `PBRTextureManager.clear()` now removes each holder from the cache before closing its textures. This keeps reentrant low-level delete callbacks from invalidating the active clear loop or skipping later PBR holder cleanup. `PBRTextureManagerTest#clearRemovesHolderBeforeClosingToTolerateDeleteCallbacks` and `#clearContinuesWhenTextureDeleteReentersHolderRemoval` cover the source/behavior boundary; live resource reload, shader reload, shutdown callback order, and sampler output remain runtime-unverified.

Recent PBR atlas registry cleanup note: `PBRTextureManager.clear()` now snapshots registered normal/specular PBR atlas textures with identity semantics and closes any remaining registry entries before `atlasHolders.clear()`. This guards reload, fallback, and shutdown cleanup against orphan animated atlas registry ownership that is not represented by a cached holder. `PBRTextureManagerTest` covers orphan registry cleanup and shared normal/specular atlas deletion once; live atlas PBR sampler output remains runtime-unverified.

Recent PBR consumer reference cleanup note: the reusable `PBRTextureManager` loader consumer now clears normal/specular references after a successful holder handoff and from `clear()` after holder/atlas caches are dropped. The cleanup path nulls fields instead of allocating fallback PBR textures, so reload/shutdown cleanup does not keep stale last-loaded companion texture objects in manager-local state. `PBRTextureManagerTest`, focused PBR/custom texture tests, standalone `compileJava`, and touched-file hygiene passed; live resource reload, shutdown ordering, Relictium wrapped-program PBR sampler output, and visual parity remain runtime-unverified.

Recent PBR holder load default-setup cleanup note: `PBRTextureManager.loadHolder(...)` now calls the reusable consumer's default setup from inside the previous-texture-binding restoration scope. Failed holder-load cleanup closes accepted companion textures and then clears consumer references directly, avoiding a second fallback texture allocation attempt during cleanup. `PBRTextureManagerTest` covers the source ordering; live default-texture allocation failure behavior and PBR sampler output remain runtime-unverified.

Recent direct texture-delete lifecycle note: `CustomImageManager`, `CustomTextureManager`, and `FallbackTextures` now call `TextureLifecycleTracker.onDeleteTexture(...)` from delete-helper `finally` blocks after positive-ID guards. A thrown `GL11.glDeleteTextures(...)` still logs and lets later cleanup proceed, while texture-size metadata, `TextureTracker`, and PBR holder state are invalidated for the manager-dropped texture ID. Focused texture lifecycle/custom resource tests and standalone `compileJava` passed; live driver delete failures, reload/fallback behavior, and shader-visible sampler/PBR output remain runtime-unverified.

Recent custom image frame-clear cleanup note: `CustomImageManager.clearNewFrameImages()` now attempts all owned `clear=true` image clears before rethrowing the first runtime or hard-error failure with later failures suppressed. `CustomImageManagerTest`, focused custom texture/program image tests, and standalone `compileJava` passed; live custom-image writes, sampler reads, driver clear failures, reload behavior, and Relictium wrapped-program image output remain runtime-unverified.

Recent PBR `AbstractTexture` delete lifecycle note: `PBRTextureManager.deletePbrTexture(...)` now captures the existing protected texture id through reflection before `deleteGlTexture()` and notifies `TextureLifecycleTracker` from `finally`. This mirrors the direct-delete lifecycle cleanup without calling `getGlTextureId()`, which Forge 1.12 bytecode would allocate on an unallocated texture. Focused PBR/texture lifecycle tests and standalone `compileJava` passed; live driver delete failures, resource reload, shutdown ordering, and PBR sampler output remain runtime-unverified.

Recent PBR atlas delete lifecycle note: `PBRAtlasTexture.deleteGlTexture()` now captures `glTextureId` before unregistering the atlas holder and calls `TextureLifecycleTracker.onDeleteTexture(...)` from `finally` after the vanilla delete attempt. This keeps atlas texture-size metadata, `TextureTracker`, and PBR holder cleanup aligned with manager state even if the 1.12 `AbstractTexture` delete path throws before the normal low-level hook runs. `PBRAtlasTextureTest`, focused PBR/custom texture tests, a representative Complementary real-source preparation guard, and standalone `compileJava` passed; live atlas PBR sampler output and reload/shutdown ordering remain runtime-unverified.

Recent registry-backed custom sampler note: `TextureBindingRegistry` now preserves exact sampler names instead of lowercasing them, and `ProgramSamplers.addSampler(...)` now allocates a dynamic unit for exact registry-backed custom samplers whose names are not built-in aliases. This closes the source gap where paired custom-image sampler aliases could be registered but dropped as unknown samplers, and prevents a registered `CustomImageSampler` from binding an active `customimagesampler` uniform. `ProgramSamplersTest`, `TextureBindingRegistryTest`, and the wider sampler/image/PBR/Relictium/config/GUI focused test run passed; live custom-image sampler contents remain runtime-unverified.

Recent exact sampler-unit alias note: `ProgramBuilder`, `ProgramSamplers`, and `SamplerOverrideMap` now keep active sampler names exact while deciding world helper auto-skip, built-in sampler aliases, and provider override units. Lowercase render-target/depth aliases no longer match uppercase or mixed-case active uniforms such as `COLORTEX4` or `OCULUS_RT4`, while exact `shadowtex0HW` and `shadowtex1HW` aliases remain available. Focused builder/sampler/shadow tests and standalone `compileJava` passed; live sampler contents and Relictium wrapped-program output remain runtime-unverified.

Recent sampler notifier override note: explicit notifier-backed dynamic sampler overrides now update the notifier on an existing `ProgramSamplers` binding instead of leaving the first notifier state frozen. Tests cover replacing the default `normals` notifier with an explicit notifier and adding a notifier to an existing `colortex0` binding that previously had none. Public notifier-less overrides still preserve the current notifier, so custom texture/resource replacement does not clear PBR callbacks. Focused `ProgramSamplersTest` and standalone `compileJava` passed after the slice; live notifier-triggered sampler output remains runtime-unverified.

Recent external sampler allocator note: active externally-managed sampler uniforms now reserve their texture unit in `ProgramSamplers` before dynamic or custom sampler bindings are placed. This keeps vanilla atlas/lightmap/overlay and wrapped external units from being silently reused by later managed sampler aliases; aliases move to a dynamic unit, and adding an active external sampler after a distinct managed binding already owns that unit fails fast. `ProgramSamplersTest` covers the allocator behavior; live sampler output remains runtime-unverified.

Recent external sampler reservation note: `ProgramSamplers.Builder.addExternalSampler(...)` now also enforces the local 1.16.5 reserved-unit precondition. External atlas/lightmap/overlay or wrapped caller-owned sampler units must be present in the builder reserved set before binding; `ProgramBuilder` auto-reserves `{0, 1, 2}` for world/root-shadow programs and keeps explicit postprocess/shadow-compute reservations. Focused sampler, builder, terrain-pipeline, and Relictium wrapper tests cover the source shape; live sampler output remains runtime-unverified.

Recent inner active binding cleanup note: the uniform, sampler, and image cleanup loops now aggregate their own per-binding failures. `ProgramUniforms` detaches all active listeners before rethrowing, `ProgramSamplers` detaches all sampler listeners, unbinds all sampler texture units, restores the previous active texture when captured, and clears active state from `finally`, and `ProgramImages` clears all image units before rethrowing. This protects reload/fallback and Relictium wrapped-program teardown from one failed binding cleanup skipping later bindings on the same surface. Focused binding, postprocess/color-space/Relictium/custom texture-image tests and standalone `compileJava` passed; live driver cleanup remains runtime-unverified.

Recent failed active binding update cleanup note: `ProgramUniforms.update()`, `ProgramSamplers.update()`, and `ProgramImages.update()` now clean up the binding set they just published if activation fails midway. The cleanup covers uniform listeners, sampler texture units/listeners plus active-texture restore, and image units, then rethrows the original activation failure with cleanup failures suppressed. Focused uniform/sampler/image tests passed; live failed-pack fallback, reload, and Relictium wrapped-program activation behavior remain runtime-unverified.

Recent custom texture helper-owned alias guard note: `CustomTextureManager.applyCustomSamplers(...)` now checks whether low render-target and helper-owned depth names already have an exact active managed sampler binding before applying a stage custom texture override. This keeps `texture.gbuffers.gcolor`, `texture.gbuffers.colortex0`, `texture.gbuffers.depthtex0`, and `texture.gbuffers.gdepthtex` from reactivating non-fullscreen world, Relictium terrain, shadow-depth, or composite-depth aliases that `ProgramBuilder` and the 1.16.5 helper path intentionally skip, while preserving explicit fallback/fullscreen/world-depth/composite-depth helper registrations. `CustomTextureManagerTest`, `ProgramSamplersTest`, `SodiumTerrainPipelineTest`, and `IrisSamplersTest` passed with standalone `compileJava`; live sampler output remains runtime-unverified.

Recent world sampler availability note: `ProgramBuilder` now matches the local 1.16.5 level-sampler unavailable-input fallback for world/root-shadow availability variants. When `InputAvailability.texture` is false, `tex`, `texture`, `gtexture`, `gcolor`, and `colortex0` bind `FallbackTextures.getWhiteTexture()` before the external atlas or low-render-target filtering branches can run. When `InputAvailability.lightmap` is false, `lightmap` binds the same white fallback. Null availability remains treated as present input so internal/wrapped builders keep the existing external path. `InternalProgramBuilderPathTest` guards this source shape; live sampler output remains runtime-unverified.

Recent render-target sampler binding note: `IrisSamplers` now owns render-target sampler alias suppliers for postprocess program builders and world-pipeline registry aliases. `colortexN`, legacy aliases, `oculus_rtN`, and `oculus_flipped_rtN` re-read `RenderTargets.get(index)` when updated instead of using a `RenderTarget` captured at registration. Registered targets that disappear fail fast, sparse 1.12 slots are still skipped during registration, and `IrisSamplersTest` guards flip changes, target replacement, target disappearance, and the postprocess/world call sites. Live sampler contents, resize/reload behavior, and Relictium wrapped-program sampler output remain runtime-unverified.

Recent unit-0 custom texture override note: `ProgramSamplers` now matches the local 1.16.5 `CustomTextureSamplerInterceptor` split for active external sampler overrides. Custom textures overriding active unit-0 external samplers stay on unit 0; nonzero external overrides still move to dynamic units; replacing an existing sampler binding updates allocator ownership so same-binding aliases can reuse the assigned unit. `ProgramSamplersTest` covers this source shape; live sampler output remains runtime-unverified.

Recent custom-image allocation cleanup note: `CustomImageManager.initializeOrResize(...)` now handles mid-resize allocation/setup failures by destroying images already allocated in that pass, unregistering their paired sampler aliases through owned-binding teardown, resetting cached framebuffer dimensions, and rethrowing. The zero-image-unit path also destroys any existing manager-owned custom image textures, resets cached framebuffer dimensions to `-1`, and returns before GL allocation instead of hard-failing shader-pack load; active linked image uniforms still fail fast through `ProgramImages` when image load/store is unavailable. `allocateTexture(...)` also deletes the generated GL texture and updates `TextureLifecycleTracker` when setup or clear fails before ownership is recorded. `CustomImageManagerTest` covers these source shapes; live resize/reload failures and custom-image sampler output remain runtime-unverified.

Recent render-target image binding note: `IrisImages.addRenderTargetImages(...)` now re-reads `RenderTargets.get(index)` when supplying `colorimgN` texture IDs instead of capturing the original `RenderTarget` object at registration. This matches the local 1.16.5 helper's update-time lookup shape while preserving the 1.12 sparse-slot registration skip. A registered target that disappears now fails fast, and `IrisImagesTest` covers flip changes, target replacement, and disappearance without creating GL textures. Live render-target image contents, Relictium wrapped-program image output, and shader image writes remain runtime-unverified.

Recent dynamic notifier detach note: fan-out state notifiers no longer require program teardown to clear every listener on a shared notifier. `ProgramUniforms` and `ProgramSamplers` now store stable listener callbacks and call `removeListener(...)` during detach, while `FanOutValueUpdateNotifier`, fog-toggle combined notifiers, and custom-expression combined notifiers remove only that callback. This protects other dynamic uniform/sampler listeners attached to the same fog, blend, texture-binding, phase, id-map, fog-color, or PBR notifier when a different program set is replaced. Source tests pass; live same-frame shader-visible update timing remains runtime-unverified.

Recent combined notifier cleanup note: combined dynamic custom-expression notifiers and fog value/toggle notifiers now aggregate delegate attach/detach failures. A failing delegate no longer stops later delegate listener cleanup; the first failure is rethrown with later failures suppressed. `CustomUniformExpressionManagerTest`, `StateUpdateNotifiersTest`, focused `ProgramUniformsTest`, and standalone `compileJava` passed; live custom uniform/fog timing remains runtime-unverified.

Recent fan-out notifier publish cleanup note: `FanOutValueUpdateNotifier.notifyListeners()` now attempts every registered listener callback during a state publish before rethrowing the first runtime or hard-error failure with later failures suppressed. `StateUpdateNotifiersTest#fanOutNotifierAttemptsEveryListenerBeforeRethrowingFirstFailure`, focused state/custom-uniform/program uniform/sampler tests, and standalone `compileJava` passed; live same-frame shader-visible timing, reload/fallback behavior, and Relictium wrapped-program callback behavior remain runtime-unverified.

Recent active binding replacement cleanup note: `ProgramUniforms.update()`, `ProgramSamplers.update()`, and `ProgramImages.update()` now keep activation moving when cleanup of the previous active binding set fails. They record previous cleanup failures, publish/update the new binding set, then rethrow the cleanup failure after success or suppress it onto an activation failure. The sampler path now records failures from the full active-sampler cleanup boundary, including previous sampler texture-unit unbinds, not only listener detach. Focused program binding tests and standalone `compileJava` passed; live reload/fallback, postprocess/compute transitions, and Relictium wrapped-program behavior remain runtime-unverified.

Recent custom-texture allocation cleanup note: `CustomTextureManager.initialize()` now rolls back partially built stage/noise bindings and owned texture IDs when custom texture setup fails before initialization completes. Shader-pack PNG setup and generated fallback noise setup also delete the generated GL texture and notify `TextureLifecycleTracker` if upload/setup fails before the texture ID reaches `ownedTextureIds`. `CustomTextureManagerTest` covers this source shape; live custom texture reload failures and sampler output remain runtime-unverified.

Recent TextureManager lifecycle note: `TextureManagerPBRReloadMixin` injects at the tail of `TextureManager.onResourceManagerReload(IResourceManager)`, reloads `TextureFormatLoader`, and now resets active pipeline PBR sampler source values through the default PBR holder after `TextureFormatLoader.reload(...)` clears loaded holders. The local 1.16.5 reference also closes the PBR manager from `TextureManager.close()`; since Forge 1.12 `TextureManager` has no matching close method, `MinecraftPBRShutdownMixin` closes `PBRTextureManager` from `Minecraft.shutdownMinecraftApplet()` immediately before LWJGL `Display.destroy()` instead. `TextureManagerPBRReloadMixinSourceTest`, `ShaderWorldRenderingPipelineSourceTest`, `TextureFormatLoaderTest`, and `MinecraftPBRShutdownMixinSourceTest` guard the source hooks and reset order. Live resource reload ordering, client shutdown ordering, texture-format macro updates, and PBR texture output remain runtime-unverified.

Recent PBR animated-atlas note: `PBRAtlasSprite` now follows the local 1.16.5 upload fallback for animated atlas sprites. If the current animation metadata entry points at an invalid or absent PBR frame, it walks backward to the nearest valid metadata frame before falling back to the first available frame, instead of jumping straight to frame zero. `PBRAtlasSpriteTest`, `PBRAtlasTextureTest`, and `AtlasPBRLoaderTest` passed with Java 8. Live animated atlas output remains runtime-unverified.

Recent PBR holder failure cleanup note: `PBRTextureManager.loadHolder(...)` now closes accepted normal/specular textures if the active loader throws before returning a holder, falls back to default textures for `RuntimeException`, and rethrows `Error` after cleanup. `SimplePBRLoader` also deletes a companion `SimpleTexture` when `loadTexture(...)` fails before the texture can be accepted by the holder consumer; runtime or hard-error load failures are rethrown after cleanup with runtime or hard cleanup failures suppressed, while IO misses still return null with cleanup failures suppressed on the IO failure. `PBRAtlasTexture.tryUpload(...)` now keeps the reference-style false return path even if cleanup deletion throws a runtime failure or hard error after an upload failure. Holder deletion also skips default textures and avoids double-deleting a shared normal/specular texture object. Shutdown cleanup clears default normal/specular texture references before deletion and keeps closing the second default texture if the first default delete fails. `SimplePBRLoaderTest`, `PBRAtlasTextureTest`, and `PBRTextureManagerTest` cover the source shape; live PBR sampler output and shutdown-time driver deletion behavior remain runtime-unverified.

Recent Relictium extended vertex-format note: `RelictiumSodiumWorldRendererVertexFormatMixin` is now listed in `oculus.mixins.json` and redirects `SodiumWorldRenderer.initRenderer()` backend creation to use `OculusTerrainVertexType.INSTANCE` when `BlockRenderingSettings.shouldUseExtendedVertexFormat()` is true. This preserves Relictium's original HFP/SFP vertex type while shaders are disabled, and makes the existing extended attribute bindings source-reachable for shader-enabled terrain. The `mc_Entity` material/render-type pair now uses signed `GL_SHORT` like the local 1.16.5 XHFP terrain format, so the `-1` missing-context sentinel written by the block context path stays shader-visible as `-1` instead of `65535`. `RelictiumTerrainBridgeBytecodeTest` pins the installed Relictium HFP/SFP selection and backend call order; `RelictiumSodiumWorldRendererVertexFormatMixinSourceTest` pins the mixin/config shape; `OculusTerrainVertexTypeSourceTest` pins the signed material attribute. Live chunk buffers, vertex attribute values, and rendered terrain remain runtime-unverified.

Recent ProgramBuilder binding-classification note: active-uniform discovery now recognizes the LWJGL 2 sampler and image enum families exposed by the 1.12 runtime, including 1D, 2D, 3D, cube, array, rectangle, buffer, signed integer, unsigned integer, and multisample forms, as managed sampler/image surfaces. `ProgramBuilderReferenceUniformCoverageTest` guards the classifier shape. This does not prove non-2D sampler output, image writes, memory barriers, or Relictium terrain sampler/image contents in a client.

Recent external uniform alias note: `ProgramBuilder` now binds `iris_FogStart`, `iris_FogEnd`, `iris_TextureMat`, `iris_ModelViewMat`, `iris_ProjMat`, `iris_ChunkOffset`, and `iris_ColorModulator`, closing the latest source-backed gap against the local 1.16.5 `ExternallyManagedUniforms.addExternallyManagedUniforms117(...)` list. `iris_ProjMat` is split from `iris_ProjectionMatrix` and reads the current GL projection matrix like a vanilla-current external alias, while `iris_ProjectionMatrix` remains on the captured gbuffer projection path. `CustomUniformExpressionManager` exposes the same names for shader-pack custom expressions and now mirrors the bound-uniform value source for the vanilla-current matrix aliases: `modelViewMatrix` / `u_ModelViewMatrix` / `iris_ModelViewMat`, `projectionMatrix` / `u_ProjectionMatrix` / `iris_ProjMat`, and `iris_TextureMat` read current GL matrices with headless captured/identity fallbacks and are dynamic upload-time dependencies instead of frame-start cached values. The chunk offset is deliberately zero on 1.12 because Relictium terrain uses the `iris_ModelOffset` vertex attribute for per-chunk offsets. Focused tests passed; live shader-visible timing and visual output remain runtime-unverified.

Recent custom-expression matrix timing note: `CustomUniformExpressionManager` now also treats `u_ModelViewProjectionMatrix`, `iris_ModelViewProjectionMatrix`, and `iris_NormalMatrix` matrix-element reads as dynamic dependencies. That keeps custom uniforms derived from the externally managed terrain matrices out of the frame-start pre-evaluation cache. `CustomUniformExpressionManagerTest` and `ProgramBuilderReferenceUniformCoverageTest` passed; live Relictium terrain matrix values and shader-visible custom-uniform timing remain runtime-unverified.

Recent color-modulator notifier note: `BuiltinReplacementUniforms` now publishes a dedicated fan-out notifier when the 1.12 `GlStateManager.color(...)` bridge changes `iris_ColorModulator`. `ProgramBuilder` binds the built-in `iris_ColorModulator` uniform with that notifier, and `CustomUniformExpressionManager` maps dynamic expressions depending on `iris_ColorModulator` to the same notifier. Focused tests passed; live same-frame color-modulator timing remains runtime-unverified.

Recent entity-color notifier note: `GameplayUniforms` now publishes a dedicated fan-out notifier when the 1.12 entity brightness-overlay capture changes `entityColor` / `iris_entityColor`. `ProgramBuilder` binds both aliases with that notifier, and `CustomUniformExpressionManager` treats both aliases as dynamic custom-expression dependencies with same-frame vector reads covered by tests. Focused Agent 3 tests and the broader source suite passed; live entity overlay rendering remains runtime-unverified.

Recent Relictium wrapped-program binding guard note: `SodiumTerrainPipelineTest#wrappedRelictiumProgramsKeepRuntimeBindingSurface` now source-pins the `SodiumTerrainPipeline.buildProgramBindings(...)` setup order for Relictium-owned linked handles. It requires `prepareResources.run()`, custom texture initialization and global overrides, `ProgramBuilder.wrapLinkedProgram(..., frameUpdateNotifier, packDirectives)`, shadow sampler binding, explicit non-fullscreen render-target sampler binding, non-shadow-only `depthtex0` / `depthtex1` world-depth binding, render-target image binding, shadow image binding, custom texture sampler binding, and custom image binding before `builder.build()`, and rejects the older no-notifier wrapper overload. `IrisSamplersTest` guards that the world-depth helper does not expose composite-only `gdepthtex`, `depthtex2`, or `oculus_depth`. `OculusRelictiumChunkProgramSourceTest` pins the wrapped setup order too: Oculus uniforms update first, Relictium `ChunkProgram.setup(modelScale, textureScale)` restores the Relictium-owned MVP/scale/base sampler uniforms, Oculus samplers and images update through separate `Program.bindSamplers()` / `Program.bindImages()` calls, and the wrapper uploads current GL `iris_ModelViewMatrix` plus inverse-transpose `iris_NormalMatrix`. This is source coverage only; live Relictium uniform values, sampler/image contents, depth timing, matrix timing, reload behavior, and rendered terrain output are still runtime-unverified.

Recent Relictium shadow override fail-fast note: `OculusRelictiumChunkProgramOverrides.getProgramOverride(...)` now throws the same local 1.16.5-style `IllegalStateException` when a shadow terrain override is requested but the active Sodium terrain pipeline has no shadow source, instead of returning `null` and letting Relictium's default terrain program handle the shadow pass. `OculusRelictiumChunkProgramOverridesSourceTest`, `OculusRelictiumChunkProgramSourceTest`, `OculusTerrainPassTest`, and `RelictiumTerrainBridgeBytecodeTest` passed for this source/bytecode surface. Live missing-shadow-source behavior, warning presentation, and rendered shadow terrain remain runtime-unverified.

Recent dynamic notifier note: runtime value notifiers now fan out to every listener attached by the active program instead of storing only the last `Runnable`. This covers `atlasSize` / `gtextureSize`, `blendFunc`, fog mode/start/end/density/color, `renderStage`, `entityId`, `blockEntityId`, `currentRenderedItemId`, and PBR normal/specular texture-change listeners. `ProgramUniforms` and `ProgramSamplers` also reset active listeners even when the same program refreshes again, matching the inspected 1.16.5 lifecycle shape. Focused coverage lives in `StateUpdateNotifiersTest`, `CapturedRenderingStateTest`, `IdMapUniformsTest`, `ProgramUniformsTest`, `ProgramSamplersTest`, and `CustomUniformExpressionManagerTest`; live same-frame shader-visible timing remains runtime-unverified.

Recent MakeUp custom-uniform note: `CustomUniformExpressionManagerTest#makeupRuntimeCustomUniformBlockCompilesAndEvaluatesFromTargetPack` loads `run/shaderpacks/MakeUp-UltraFast-9.3e` through `ShaderPackLoader`, so custom directives are tested after real target-pack property preprocessing, option defaults, optional feature defines, and the 1.12 `MC_VERSION` branch. The processed block has 11 uniforms and 17 variables, compiles every custom uniform, includes default-option `taa_offset`, and excludes `dither_shift`. Headless viewport-dependent values remain zero without Minecraft; live viewport/TAA/custom-uniform upload timing is still runtime-unverified.

Recent Complementary custom-uniform note: `CustomUniformExpressionManagerTest#complementaryRuntimeCustomUniformBlockCompilesAndEvaluatesFromTargetPack` loads `run/shaderpacks/ComplementaryReimagined_r5.6.1` through `ShaderPackLoader` with headless GL probes disabled/restored for the test class. The processed block has 19 uniforms and 6 variables, compiles every custom uniform, and evaluates representative frame-mod, precipitation, movement/smoothing, biome-fallback, and blindness/darkness expressions headlessly. `BIOME_*` custom-expression constants now resolve known 1.12 IDs before Minecraft bootstrap and use registered `Biomes` fields after bootstrap. Live biome/weather/camera movement, duplicate explicit `smooth` ID behavior, Relictium terrain upload timing, and shader-visible output remain runtime-unverified.

Recent material-ID note: `BlockMaterialMapping` now resolves exact modern direct names from Complementary and MakeUp active `block.properties` to their 1.12 registry equivalents: `grass_block`, `dirt_path`, `slime_block`, `wall_torch`, `redstone_wall_torch`, `note_block`, `spawner`, `cobweb`, `dead_bush`, `sugar_cane`, `bricks`, `nether_bricks`, `red_nether_bricks`, `end_stone_bricks`, `melon`, `carved_pumpkin`, and `jack_o_lantern`. Complementary's generic sign/hanging-sign, skull/head, banner, and bed rows now map to 1.12 `standing_sign`, `wall_sign`, `skull`, `standing_banner`, `wall_banner`, and `bed`, with skull/head fallback matching restricted to known aliases so `piston_head` stays an exact block instead of a skull fallback. The bare-`grass` special case maps only predicate-free modern plant entries to `tallgrass:type=tall_grass`, preserving `grass:snowy=*` and `grass_block:snowy=*` for the 1.12 grass block. Plain modern `shulker_box` intentionally stays unmapped because 1.12 has colored shulker registries but no plain registry block; active Complementary purple shulker states still resolve through `purple_shulker_box`. `short_grass`, `fern`, vanilla flowers, vanilla saplings, and supported `potted_*` names now resolve to the matching 1.12 shared-block state predicates; bare `flower_pot` resolves to `contents=empty`. Modern double-plant names now resolve to 1.12 `double_plant` variants while preserving `half` predicates: `sunflower`, `lilac` -> `syringa`, `tall_grass` -> `double_grass`, `large_fern` -> `double_fern`, `rose_bush` -> `double_rose`, and `peony` -> `paeonia`. `BlockEntry.parse(...)` now also tolerates MakeUp 9.3e `betterendforge::...` entries by dropping the empty middle segment while preserving the mod namespace, block name, and predicates. `BlockMaterialMappingTest` and `ShaderPackLoaderComplementaryTest` cover the source/real-pack loader path; live terrain `blockId` attributes and shader material branches remain runtime-unverified.

Recent entity-ID note: MakeUp's active `entity.properties` maps `minecraft:lightning_bolt`. `IdMapUniforms.resolveEntityId(...)` now handles 1.12 `EntityLightningBolt` through the dedicated `EntityList.LIGHTNING_BOLT` resource when the normal Forge entity-registry lookup is unavailable, matching the 1.16.5 entity-type registry-key surface as closely as the 1.12 weather-effect path allows. `IdMapUniformsTest` covers the fallback; live `entityId` rendering remains runtime-unverified.

Recent shader-source compile note: MakeUp 9.3e's recorded `prepare.fsh` failure was source-backed to duplicate identical `shifted_r_dither` definitions in the active 1.12 branch. `ShaderCompatibilityPatcher` now removes only later top-level functions with the same canonical signature and body, leaving same-signature different bodies for real compiler diagnostics. `ShaderPackLoaderComplementaryTest` prepares the real MakeUp `prepare` source and verifies exactly one active-branch `shifted_r_dither` remains. `ShaderLoader` now skips un-specialized base compiles for availability-only `gbuffers_*` and root shadow programs, reports variant-aware program counts, and logs non-spammy progress/timing diagnostics. `PipelineManager` records failed-pack backoff keyed by pack name plus sorted option values, so the same failed pack/options do not retry every frame. A minimal LWJGL2 constant fix in legacy `com.github...GlFramebuffer` was required to make the requested `compileJava` pass; it uses `GL30.GL_DEPTH_STENCIL_ATTACHMENT` like the local 1.16.5 reference. This slice is source/test evidence only; MakeUp live GL compile and Complementary startup-time improvement still need a client run.

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.mixins.OculusMixinLoaderTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.FixedFunctionWorldRenderingPipelineTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.IdMapTest --tests net.oculus.pipeline.BlockRenderingSettingsTest --tests net.oculus.mixins.BlockRenderLayerOverrideMixinSourceTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.pipeline.FixedFunctionWorldRenderingPipelineTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.BlockRenderingSettingsTest --tests net.oculus.pipeline.PipelineManagerSourceTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.pipeline.FixedFunctionWorldRenderingPipelineTest --tests net.oculus.shaderpack.IdMapTest --tests net.oculus.blockrendering.BlockMaterialMappingTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.BlockRenderingSettingsTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.pipeline.FixedFunctionWorldRenderingPipelineTest --tests net.oculus.uniforms.IdMapUniformsTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.client.ShaderPackReloaderTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.gl.FramebufferCompatibilityTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.preprocessor.PropertiesPreprocessorTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --tests net.oculus.shader.IrisFeatureDefinesTest --tests net.oculus.gl.OculusRenderSystemCapabilityDispatchTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.CommentDirectiveParserTest --tests net.oculus.shaderpack.ProgramSetTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.ComputeDirectiveParserTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.compat.relictium.OculusTerrainPassTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.compat.relictium.RelictiumTerrainBridgeBytecodeTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.shadow.ShadowRendererSourceTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.pipeline.shadow.ShadowRendererBytecodeTest --tests net.oculus.pipeline.shadow.ShadowMapBytecodeTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.SodiumTerrainPipelineGlCompileTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.SodiumTerrainShaderTransformerTest --tests net.oculus.pipeline.SodiumTerrainPipelineTest --tests net.oculus.mixins.OculusMixinLoaderTest --tests net.oculus.shader.ShaderSourcePreparerTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.SodiumTerrainShaderTransformerTest --tests net.oculus.pipeline.SodiumTerrainPipelineTest --tests net.oculus.compat.relictium.RelictiumTerrainBridgeBytecodeTest --tests net.oculus.compat.relictium.OculusTerrainPassTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.uniforms.transforms.SmoothedFloatTest --tests net.oculus.uniforms.GameplayUniformsTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.shaderpack.EntityShadowDistanceDirectiveTest --tests net.oculus.shaderpack.ShadowColorClearDirectiveTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.texture.TextureInfoCacheTest --tests net.oculus.texture.TextureLifecycleTrackerSourceTest --tests net.oculus.mixins.GlStateManagerTextureLifecycleMixinSourceTest --tests net.oculus.gl.program.InternalProgramBuilderPathTest --tests net.oculus.uniforms.GameplayUniformsTest --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --tests net.oculus.texture.pbr.loader.SimplePBRLoaderGlTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.client.OculusRuntimeValidationTest --tests net.oculus.mixins.GlStateManagerStateMixinSourceTest --tests net.oculus.texture.mipmap.ChannelMipmapGeneratorTest --tests net.oculus.texture.format.LabPBRTextureFormatTest --tests net.oculus.texture.pbr.PBRAtlasTextureTest --tests net.oculus.texture.pbr.loader.AtlasPBRLoaderTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.texture.format.TextureFormatLoaderTest --tests net.oculus.gl.state.StateUpdateNotifiersTest --tests net.oculus.gl.shader.StandardMacrosTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.gl.OculusRenderSystemCapabilityDispatchTest --tests net.oculus.pipeline.buffer.ShaderStorageBufferManagerTest --tests net.oculus.pipeline.texture.CustomImageManagerTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shader.ShaderCompatibilityPatcherTest --tests net.oculus.shader.ShaderSourcePreparerTest --tests net.oculus.shader.ShaderLoaderSourceTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.pipeline.PipelineManagerSourceTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shader.ShaderCompatibilityPatcherTest --tests net.oculus.shader.ShaderSourcePreparerTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --tests net.oculus.pipeline.SodiumTerrainPipelineTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.option.ProfileSetTest --tests net.oculus.shaderpack.option.menu.OptionMenuContainerTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.ShaderPropertiesTest --tests net.oculus.shaderpack.option.ProfileSetTest --tests net.oculus.shaderpack.option.menu.OptionMenuContainerTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.postprocess.PostprocessRendererSourceTest --tests net.oculus.postprocess.FinalPassRendererSourceTest --tests net.oculus.postprocess.CenterDepthSamplerSourceTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.postprocess.PostprocessRendererSourceTest --tests net.oculus.gl.FramebufferCompatibilityTest --tests net.oculus.rendertarget.RenderTargetsSourceTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.pipeline.shadow.ShadowRendererSourceTest --tests net.oculus.pipeline.shadow.ShadowRendererBytecodeTest --tests net.oculus.pipeline.shadow.ShadowMapBytecodeTest --tests net.oculus.pipeline.shadow.ShadowEntityCullingTest --tests net.oculus.pipeline.shadow.ShadowSamplerBindingsTest --tests net.oculus.pipeline.shadow.ShadowMapTextureStateTest --tests net.oculus.pipeline.shadow.ShadowRenderingStateTest --tests net.oculus.postprocess.PostprocessRendererSourceTest --tests net.oculus.postprocess.FinalPassRendererSourceTest --tests net.oculus.postprocess.CenterDepthSamplerSourceTest --tests net.oculus.gl.FramebufferCompatibilityTest --tests net.oculus.rendertarget.RenderTargetsSourceTest --tests net.oculus.texture.TextureLifecycleTrackerSourceTest --tests net.oculus.gl.OculusRenderSystemCapabilityDispatchTest --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain build --stacktrace
timeout 90s /usr/bin/zsh -lc 'JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient --stacktrace'
timeout 240s /usr/bin/zsh -lc 'JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient --stacktrace'
timeout 360s /usr/bin/zsh -lc 'JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -Doculus.validation.autoJoinWorld="New World" -Doculus.validation.exitAfterWorldTicks=900 -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient --stacktrace'
timeout 480s /usr/bin/zsh -lc 'JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -Doculus.validation.autoJoinWorld="New World" -Doculus.validation.exitAfterWorldTicks=1200 -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient --stacktrace'
```

As of the latest headless test repair, `ShaderPackReloaderTest` preserves/restores GL probe system properties and sets `oculus.disableGlStringProbes=true` plus `oculus.disableGlCapabilityProbes=true` while loading temporary packs. This removed a full-suite hang in LWJGL `GL11.glGetString(...)`; `test --tests net.oculus.client.ShaderPackReloaderTest --stacktrace` and full `test --stacktrace` both passed afterward.

The bounded 90 second client run is startup evidence. It confirms the local dev client reaches successful Forge mod load with Oculus mixins queued through the early coremod path.

For PBR runtime proof, `run/resourcepacks/Oculus-PBR-Validation` now contains a tiny LabPBR pack with dirt `_n` / `_s` companions, and `run/options.txt` currently enables it. The `2026-05-16 22:18` attempt with `-Doculus.validation.pbrTextures=true` blocked before resource loading in `sun.awt.X11GraphicsEnvironment.initDisplay`, so that was setup evidence only. A `2026-05-17 00:07` retry with `DISPLAY=:0`, `autoJoinWorld="New World"`, `exitAfterWorldTicks=900`, and `pbrTextures=true` reached early Minecraft/mixin startup but stopped before Forge mod loading; `jstack` showed the same `sun.awt.X11GraphicsEnvironment.initDisplay` path through LWJGL `Sys.<clinit>`. A later `2026-05-17` Xvfb smoke loaded the `Oculus-PBR-Validation` resource pack and completed 180 in-world ticks, but the recorded log evidence still does not prove simple, atlas, or custom resource PBR texture uploads.

Agent 07 texture cache note: the 2026-05-17 source-backed resource slice keeps 2D bind-to-edit work in custom PNG/noise, custom image fallback, fallback normal/specular, texture-info, texture-format, and PBR default/atlas paths aligned with Forge 1.12.2 `GlStateManager` by restoring previous `GL_TEXTURE_BINDING_2D` through `GlStateManager.bindTexture(...)`. `CustomImageManager` routes only `GL_TEXTURE_2D` through that helper and keeps 3D image textures on raw GL with a separate 3D binding restore. Program sampler binding now also uses the cache-aware texture-unit wrapper for 2D sampler binds: units `0..7` update `GlStateManager`, units `>=8` use raw GL, and 3D image sampler binds use raw target binding with the same active-unit restore helper. The source guards are `Texture2DBindCacheSourceTest`, `ProgramSamplersTest`, and `OculusRenderSystemCapabilityDispatchTest`; runtime resource reload and live sampler output are still unverified.

Recent AbstractTexture tracker/PBR lookup note: the 1.12 port now mirrors the local 1.16.5 `TextureTracker` boundary for generated `AbstractTexture` IDs. `AbstractTextureTextureTrackerMixin` records an `AbstractTexture` after `getGlTextureId()` allocates a new GL handle, `TextureLifecycleTracker.onDeleteTexture(...)` clears the tracker before clearing texture-size metadata and PBR holders, and `PBRTextureManager.findTextureById(...)` checks that tracker before scanning `TextureManager.mapTextureObjects`. This keeps custom resource `_n` / `_s` PBR lookup reachable for live base textures that are not discoverable through the 1.12 texture-manager map. Focused `TextureTrackerTest`, `TextureLifecycleTrackerSourceTest`, `PBRTextureManagerTest`, and `AbstractTextureTextureTrackerMixinSourceTest` passed with Java 8; live resource reload, driver texture lifetime, Relictium sampler output, and PBR visuals remain runtime-unverified.

Recent sampler registry lifecycle note: `TextureBindingRegistry` is a 1.12-specific global bridge for render-target, shadow, custom image, custom noise, and PBR sampler aliases. `ShaderWorldRenderingPipeline.destroy()` now clears it after custom image/custom texture/framebuffer teardown so shader disable, failed-pack fallback, and pack reload cannot leak stale custom sampler bindings into the next pipeline. `CustomImageManager.destroyTextures()` unregisters each paired custom-image sampler alias by owned binding identity before clearing its texture map, covering resize and failed custom-image reallocation paths that happen before a whole-pipeline registry clear without removing a newer same-name binding. Mid-resize custom-image allocation failures now also destroy any images allocated before the failure and reset cached dimensions, while generated textures that fail during setup/clear are deleted before leaving `allocateTexture(...)`. `CustomImageManager` no longer has a zero-image-unit allocation bypass; active image-uniform failure is handled later by `ProgramImages`. `CustomTextureManager.destroy()` unregisters its owned global noise aliases by binding identity before deleting owned textures, covering custom/generated noise teardown without removing a newer same-name binding; initialization failures now also destroy partial custom texture/noise state and delete generated textures that fail before ownership is recorded. Texture-format reload-listener registration is now also reentry-guarded: Forge 1.12 calls a listener immediately from `SimpleReloadableResourceManager.registerReloadListener(...)`, so `TextureFormatLoader` latches accepted reloadable registration before that callback can trigger shader reload and ask to register again. Source coverage lives in `ShaderWorldRenderingPipelineSourceTest`, `TextureBindingRegistryTest`, `CustomImageManagerTest`, `CustomTextureManagerTest`, and `TextureFormatLoaderTest`; live reload behavior remains unverified.

`SimplePBRLoaderGlTest` is a ready opt-in harness for simple PBR GL upload evidence. It is skipped unless the JVM has `-Doculus.tests.pbrGl=true`; the default focused suite compiles it and records the skip. `SodiumTerrainPipelineGlCompileTest` is now guarded the same way with `-Doculus.tests.sodiumTerrainGl=true`; the default command only proves the harness compiles and skips before LWJGL. Enable either GL harness only on a known-good real display or virtual display.

The `2026-05-16 15:13` shader-world smoke is archived at `run/logs/2026-05-16-7.log.gz`. It started with `run/config/oculus.properties` selecting `ComplementaryReimagined_r5.6.1`, logged `Using configured shader pack: ComplementaryReimagined_r5.6.1`, entered the existing `New World` save, created `ShaderWorldRenderingPipeline`, compiled 27 shader programs for Complementary, initialized the 2048 shadow renderer, created 7 postprocess passes, and compiled the final pass. During the same run, GUI/reload behavior switched the selected pack to `ComplementaryReimagined_r5.6.1.zip`, rebuilt the pipeline, and compiled 27 programs for the zip too. The command exited with code `0`. This proves shader-world compilation and initialization for both local pack layouts, not rendered-output parity. Runtime work after that exposed Relictium `_sodium.vsh` undeclared-symbol failures; the source fix is tested, `SodiumTerrainPipelineGlCompileTest` can prove generated Complementary solid/translucent/shadow `_sodium` compile/link only when explicitly run with `-Doculus.tests.sodiumTerrainGl=true` on a working display or virtual display, the `2026-05-16 19:26` validation run proves the Complementary zip can auto-enter `New World`, initialize `ShaderWorldRenderingPipeline`, compile 27 programs, and create SHADOW, GBUFFER_SOLID, and GBUFFER_TRANSLUCENT Relictium terrain overrides in-client, the `2026-05-16 19:35` run proves actual Relictium active-program override selection for solid, cutout-mipped, cutout, and translucent main terrain, and the `2026-05-16 19:49` run proves shadow terrain layer requests plus `oculus:relictium-terrain-shadow` active-program selection for solid, cutout-mipped, cutout, and translucent shadow terrain before a validation-triggered shutdown. The newer Relictium shadow visibility graph swap layer is implemented and bytecode-guarded. The vanilla `RenderGlobal` shadow setup path is also bytecode-guarded now: `ShadowRenderer.renderShadows(...)` marks `displayListEntitiesDirty` before shadow `setupTerrain(...)`, matching the local 1.16.5 pre-setup `needsUpdate()` call, keeps the post-shadow dirty mark for the main camera graph, renders shadow terrain in the 1.16.5 solid/cutout/cutout-mipped/translucent order, and restores depth state after the shadow pass. Source guards now also pin the shadow target preparation boundary: `ShaderWorldRenderingPipeline` runs depth clear, shadow compute dispatch, and shadow color clear before `prepareBeforeShadow` and before the root shadow raster program sync, `ShadowRenderer.prepareRenderTargets()` restores the default active texture through `OculusRenderSystem.restoreDefaultActiveTexture()` before binding the shadow framebuffer, and `ShadowRenderer.renderShadows(...)` no longer dispatches compute after the raster program is active. The target-prep and immediate post-shadow blocks intentionally have no renderer-level memory barrier, matching the inspected local 1.16.5 source; `ComputeProgram.dispatch(...)` keeps the `allowConcurrentCompute=false` pre-dispatch barrier. `ShadowMapBytecodeTest` now also guards that the no-translucents shadow depth copy calls the cache-aware `OculusRenderSystem.copyTexSubImage2D(...)` helper instead of raw bind/copy calls and that shadow mipmap generation uses a dedicated texture unit with restore guards. A later `2026-05-17` Xvfb smoke compiled 162 Complementary zip programs, loaded 18 active programs, completed 180 in-world ticks, logged main and shadow Relictium override selection, and saw shadow visible chunks rise to 355. That smoke fixed the previous postprocess cleanup crash on texture unit 8, but shadow depth samples at attempts 1 and 20 were still all clear (`nonClear=0`, depth 1.0), so shadow rendered-output validation remains open.

The shadow mipmap guard now expects the dedicated texture unit to be selected through `OculusRenderSystem.setActiveTextureUnit(...)`, mipmap/filter edits to use `OculusRenderSystem.generateMipmaps(...)` and `texParameteri(...)`, the saved dedicated-unit texture binding to be restored through `GlStateManager.bindTexture(...)`, and the active unit to return to default through `OculusRenderSystem.restoreDefaultActiveTexture()`.

The archived `2026-05-16 15:13` shader-world log no longer contains the previous `gbuffers_line.vsh` `vec4(vaPosition, 1.0)` compile failure, `Shadow framebuffer incomplete: 36055`, repeated `1282: Invalid operation` errors, the prior internal `centerDepthSmooth` sampler/uniform warnings, or the older Mesa/GLSL uninitialized-variable compiler warnings. Unused-function cleanup warnings are now stage/function-specific and source-backed. The lightmap texture matrix warning is also source-backed: the local 1.16.5 `BuiltinReplacementUniforms` emits the same warning when `iris_LightmapTextureMatrix` is required.

A `2026-05-16 11:10` bounded run entered `New World` with config still set to `shadersEnabled=true` and `selectedPackName=ComplementaryReimagined_r5.6.1`, but only created `FixedFunctionWorldRenderingPipeline`. That proved the persisted config was not being applied to `PipelineManager` at startup. The source now applies config once on the first client tick, and `ShaderPackReloaderTest` covers the pure Java application path. A `2026-05-16 12:11` bounded client run proved the live startup path logs `Using configured shader pack: ComplementaryReimagined_r5.6.1`; the later `2026-05-16 13:52` run proved that startup activation reaches `ShaderWorldRenderingPipeline` during world entry.

## Recent Implemented Slices

- `OculusConfig` per-pack option override persistence now handles dotted shader-pack names. The config file still uses `option.<canonical pack name>.<option id>`, but load splits from the last dot so `ComplementaryReimagined_r5.6.1.zip` maps back to the same canonical pack instead of truncating at `r5`. Focused coverage lives in `OculusConfigTest`; in-client GUI Apply/profile cycling/reload behavior after persisted option edits remains unverified.
- Shader-pack `.lang` entries now sideload through both Minecraft 1.12 translation systems. `ClientLocaleLanguageMixin` exposes active pack translations to `net.minecraft.client.resources.I18n.hasKey(...)` / `format(...)`, while `TextLanguageMapMixin` exposes the same active pack translations to `TextComponentTranslation` through the legacy `net.minecraft.util.text.translation.LanguageMap` path. Both hooks check vanilla storage first, use configured-language then `en_us` fallback through `ShaderPackLanguageLookup`, and the text-component path bumps the language update value with the shader reload counter. The same client `Locale` mixin also runs `IrisLanguageJsonLoader` after vanilla `.lang` loading so the bundled modern `assets/iris/lang/*.json` resources are usable in 1.12. `GuiUtil` still uses selected-pack fallback lookup for option-screen previews. The internal 1.12 GUI-only English messages for reset/import/export/apply/folder errors and profile controls now exist in both `assets/oculus/lang/en_us.lang` and `assets/iris/lang/en_us.json`. Focused coverage lives in `InternalTranslationResourceTest`, `IrisLanguageJsonLoaderTest`, `ShaderPackLanguageLookupTest`, and `I18nLanguageMixinSourceTest`; runtime language switching, message layout, and third-party translation-mixin behavior remain unverified.
- `FixedFunctionWorldRenderingPipeline.beginLevelRendering()` now restores the Minecraft main framebuffer and calls `Program.unbind()` before vanilla fixed-function rendering continues, matching the local 1.16.5 fallback path. This protects shader disable, failed-pack fallback, and reload recovery from stale shader/FBO state at source level. Unit/source coverage lives in `FixedFunctionWorldRenderingPipelineTest`; in-client toggle/fallback validation is still missing.
- `shadow.enabled=false` now follows the local 1.16.5 disabled-renderer target clear branch. If linked programs requested shadow targets, `ShadowMap` full-clears shadow color textures once without creating `ShadowRenderer`; depth is still not cleared in this branch, matching the inspected reference. Runtime color sampling remains unproven.
- Shadow target allocation now mirrors the local 1.16.5 lazy lifecycle. `shadow.enabled=true` forces `ShadowMap` creation, default settings create targets only after linked programs expose shadow samplers/images, and `shadow.enabled=false` can still expose requested target textures while suppressing `ShadowRenderer`. The no-GL trigger surface is pinned in `ShadowSamplerBindingsTest`; live visual output for disabled-but-requested targets is still unproven.
- Shadow color image metadata is no longer gated on renderer allocation once a `ShadowMap` exists. `IrisImages.addShadowColorImages(...)` now matches the local 1.16.5 helper shape by binding `shadowcolorimg*` from present shadow-target metadata even when `ShadowMap.isEnabled()` is false; `IrisImagesTest` covers the disabled no-GL case. Runtime image contents remain unproven.
- Shadow render inclusion flags are parsed and wired: `shadowTerrain`, `shadowTranslucent`, `shadowEntities`, `shadowPlayer`, and `shadowBlockEntities` feed `PackShadowDirectives` and `ShadowRenderer`. `ShadowRenderDirectiveTest` pins the 1.16.5 defaults and explicit override parsing; live visual validation for each flag is still missing.
- `DRAWBUFFERS` / `RENDERTARGETS` comment parsing is intentionally last-match and strict like the local 1.16.5 parser. `RENDERTARGETS` comma tokens are parsed without per-token trimming or empty-list fallback, so malformed values such as `2, 4` or an empty payload fail instead of normalizing to a usable draw-buffer list. Do not "fix" `SHADOWRES` / `GAUX4FORMAT` comment registration as a parity change without separate target-pack-support evidence; the inspected 1.16.5 generic comment directive handlers are no-op.
- `alphaTest.<pass>` parsing now matches the local 1.16.5 extra-token behavior. Values with more than two space-separated tokens warn but still use the first function/reference pair; fewer than two tokens remain invalid. Complementary's current alpha-test directives are normal two-token values, so keep `ShaderPackLoaderComplementaryTest` in the focused verification set when changing this area.
- `texture.<stage>.<sampler>` now keeps the local 1.16.5 raw-texture TODO boundary: single-path values are stored, but multi-token raw-style values are ignored after a warning. Do not broaden this parser to raw texture declarations without separate source or target-pack evidence. Complementary's `texture.deferred.colortex3`, `texture.gbuffers.gaux4`, and `texture.noise` entries are single-path values and still load; its `customTexture.textureAtlas` entry is a separate target-pack support path.
- Custom texture sampler keys must preserve shader-pack casing through parser and manager state. Complementary declares `customTexture.textureAtlas` and `uniform sampler2D textureAtlas`; lowercasing the key prevents the post-discovery override from reaching case-sensitive `glGetUniformLocation`. Coverage lives in `CustomTextureManagerTest` and `ShaderPackLoaderComplementaryTest`.
- MakeUp's conditional `texture.gbuffers.gaux2` / `texture.deferred.gaux2` directives are now guarded through the real loader. Default options load `textures/clouds_natural_512_R_8bit.png`; `CLOUD_VOL_STYLE=1` loads `textures/clouds_blocky_512_R_8bit.png`. This is parser/resource evidence for option-preprocessed custom textures, not live cloud rendering proof.
- Resource custom texture references now honor the local 1.16.5 PBR suffix branch. `minecraft:textures/block/stone_n.png` and `_s` variants strip to the base resource path, resolve that base texture through `TextureManager`, and return the normal/specular holder texture from `PBRTextureManager`, with active texture-format parameter setup applied to the returned PBR texture. Malformed custom resource locations are skipped with a warning before texture loading instead of hard-failing custom texture manager initialization. Valid but unloadable resource textures also log and keep the late supplier alive after the 1.12 eager load attempt fails, so missing-texture fallback remains possible. Unit coverage lives in `CustomTextureManagerTest`; live sampler output and malformed/load-failure presentation remain unproven.
- `program.*.enabled` condition evaluation now matches the local 1.16.5 single-token rule. Exact `true` enables a program, exact `false` disables it, and every other value is treated as one boolean shader option name. Unknown option names default to `true` through the reference `OptionValues` helper, so expression-like values such as `A && B` remain enabled when no exact option by that name exists. Do not reintroduce expression parsing for `!`, `&&`, `||`, or parentheses without source evidence from the active reference. Complementary's current declarations use only `false` and `FXAA`.
- Program condition key extraction also follows the local 1.16.5 helper. The key is taken from after `program.` up to the first following dot; malformed extra suffixes do not extend the key, empty names are preserved, and missing second dots throw. Normal world-prefixed keys such as `program.world0/shadow.enabled` still become `world0/shadow`.
- Boolean shader option discovery follows the local 1.16.5 `OptionAnnotatedSource` gate. A candidate boolean option, including a valid `const bool` configurable directive name, is exposed only when its name appears in the connected-component `#ifdef` / `#ifndef` reference set. Do not expose unreferenced `const bool` directive constants as GUI/config options without new source evidence; string options such as configurable `const int` declarations still bypass this boolean-only gate.
- Duplicate shader option declarations follow the local 1.16.5 `OptionSet.Builder` merge path. Same-name boolean or string options with conflicting defaults are removed as ambiguous instead of letting the later declaration win. Same-default duplicates prefer the existing declaration when it has a comment, otherwise they adopt the later declaration's comment.
- Changed option values follow the local 1.16.5 storage boundary. `OptionValues` only stores known values that differ from discovered defaults; unknown config/profile keys are ignored, invalid boolean text collapses to the default, and setting an option back to its default removes it from the changed map.
- Applying option values back into shader source now follows the local 1.16.5 `OptionAnnotatedSource.edit(...)` path. Source edits use raw changed-value accessors, so unchanged `const` and string options preserve their original lines. Changed string `#define` options become `#define <name> <value> // OptionAnnotatedSource: Changed option`, `const` edits only replace after the first `=` with regex-safe replacement, and boolean `#define` toggles use the reference tri-state comment helper. A default-off commented define can become double-commented, for example `//#define SHADOWS` to `////#define SHADOWS`; that is source parity with the inspected reference.
- `blend.<program>.<buffer>` directives now keep the local 1.16.5 capability boundary. `ShaderProperties` checks `OculusRenderSystem.supportsBufferBlending()` and throws the reference error when probes are active and per-buffer blending is unavailable. The existing disabled-probe headless loading mode is preserved through `OculusRenderSystem.areCapabilityProbesDisabled()` so real-pack parser tests do not treat unknown GL capability as an unsupported-GPU result. The malformed-data boundary is source-backed too: unknown buffer names throw `Failed to parse buffer blend! index = -1`, malformed `colortex` IDs wrap the parse failure, invalid blend-mode tokens throw, and extra valid blend-mode tokens are parsed before only the first four feed `BlendMode`. Complementary declares `blend.gbuffers_water.colortex4=off` and `blend.gbuffers_water.colortex8=off`, so keep the Complementary loader tests in the verification set when changing this area.
- `shaders.properties` now preserves the local 1.16.5 processed/original split. `ShaderPackLoader` passes both the two-pass preprocessed contents and the original file into `ShaderProperties`; behavioral directives still parse from the processed view, while `screen`, `screen.*`, `profile.*`, and `sliders` parse from the non-preprocessed file. This prevents property preprocessor guards from changing option-menu layout. Focused coverage lives in `ShaderPropertiesTest` plus the existing option-menu and Complementary loader tests.
- Directory shaderpacks only read metadata from the resolved shader root `<pack>/shaders`. Root-level `<pack>/shaders.properties` is ignored when the direct `shaders/` child exists, because the local 1.16.5 folder loader passes `shaderPackRoot.resolve("shaders")` into the `ShaderPack` constructor. `ShaderPackLoaderComplementaryTest.directoryPackIgnoresRootLevelShadersPropertiesLikeReferenceLoader` pins this.
- Layout/profile lists now use the local 1.16.5 `value.split(" +")` behavior. This applies to `screen`, `screen.*`, `sliders`, and `profile.*` values parsed by `ShaderProperties`; tabs inside those values are not delimiters. Do not replace this helper with generic whitespace splitting unless the authoritative reference changes.
- `OptionMenuElementScreen.getColumnCount()` returns raw parsed `screen.columns` / `screen.<name>.columns` integers like the local 1.16.5 menu model. Do not put parser/model clamping back there; the 1.12 UI bridge clamps in `OptionMenuConstructor` when creating widget rows.
- `size.buffer.*` uses the local 1.16.5 literal-space tokenizer, not a trimmed generic whitespace tokenizer. Valid two-token values still resolve shader option tokens for the 1.12 supported optional-feature path, but tab-separated or double-space malformed values are rejected like the reference.
- Pass directives such as `scale.*`, `blend.*`, and `alphaTest.*` preserve empty suffixes like the local 1.16.5 helper. Do not add an `isEmpty()` skip unless the reference changes; malformed entries are intentionally parser-visible.
- `texture.*` and `flip.*` use the local 1.16.5 two-argument helper boundary. Keys missing the second dot fail fast through substring bounds, and empty second arguments are preserved. Explicit flip resolution also rejects duplicate resolved aliases like `gcolor` plus `colortex0`, matching the 1.16.5 `ImmutableMap.Builder` failure boundary. This is intentional malformed-pack parity, not a preferred authoring pattern.
- The latest local target-pack property audit covered `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/shaders.properties` and `run/shaderpacks/MakeUp-UltraFast-9.3e/shaders/shaders.properties`. Their active property families are represented by existing parser/runtime owners for profiles, screens, sliders, program gating, alpha/blend/per-buffer blend, size buffers, custom textures, `image.*`, `bufferObject.*`, custom uniforms/variables, shadow flags, particles, sky/cloud booleans, and texture noise/stage overrides. Modern keys such as `dhShadow.enabled`, `dhClouds`, and `voxelizeLightBlocks` remain deliberately not wired as 1.16.5 parity directives; `customTexture.*`, `image.*`, `bufferObject.*`, `particles.ordering`, `shadow.culling=reversed`, and the extra optional features are documented target-pack support paths.
- The registered `GAUX4FORMAT` handler is intentionally narrower than general render-target format parsing: like the local 1.16.5 reference, it accepts only `RGBA32F`, `RGB32F`, and `RGB16`, while `const int colortex7Format = ...` remains the general format path. Generic `acceptComment*Directive` methods are still no-op in the inspected dispatcher, so do not document `GAUX4FORMAT` comments as active runtime behavior without separate evidence.
- `DispatchingDirectiveHolder` intentionally keeps the 1.16.5 vector const leniency. Extra args in `vec2`, `ivec3`, and `vec4` constructors log but still use the first expected components; bad numeric vector components are logged and ignored without throwing. Do not replace this with strict arity parsing unless the reference changes.
- `ComputeDirectiveParser` intentionally keeps the same leniency for compute-only `workGroups` and `workGroupsRender` directives. Extra `ivec3` / `vec2` constructor args log but still use the leading components; bad numeric components log and leave the `ComputeSource` unchanged. Do not restore exact-arity early returns unless the 1.16.5 reference changes.
- `ProgramSet.locateDirectives()` keeps the local 1.16.5 compute-source directive scan order: composite, deferred, prepare, shadowcomp, final, then shadow. This is source-parity for `workGroups` / `workGroupsRender` discovery order; keep `ProgramSetTest` in the guard if touching program discovery.
- `gbuffers_damagedblock` fallback must stay after `locateDirectives()`. The local 1.16.5 constructor first reads a missing/invalid damaged-block source, collects directives, then replaces it with the first valid terrain/textured/basic fallback using `withOverriddenDrawBuffers(new int[] {0})`. Do not reintroduce an early fallback in `readProgramSource(...)`; that bypasses the draw-buffer override. `ProgramSetTest.missingDamagedBlockFallbackOverridesDrawBuffersAfterDirectiveCollection` pins this.
- Dimension wildcard mappings are base-root selection only. The local 1.16.5 `getProgramSet(dimension)` returns the base `ProgramSet` when the requested dimension is not explicitly present in `dimensionMap`, even if the wildcard base root points at `world0`. Do not reintroduce a second wildcard lookup inside `getProgramSet(...)`; `ShaderPackLoaderComplementaryTest.wildcardOnlyDimensionMappingUsesBaseProgramSetLikeReferenceLoader` pins this.
- The `screen.*.columns` parser now also keeps the 1.16.5 empty-affix boundary. `screen..columns` does not create a column count for an empty sub-screen name; because the reference returns false from the affixed-int handler, it falls through to the general `screen.` list parser as `.columns`. This is malformed-pack parity, not a preferred pack authoring pattern.
- Shader profile parsing and matching now mirror the local 1.16.5 token behavior more closely. `ProfileSet` may record entries such as `UNKNOWN_OPTION=value` or `UNKNOWN_OPTION=false`, but `Profile.matches(...)` only compares known boolean and string options from the active `OptionSet`; unknown entries are ignored at match time while still contributing to `Profile.precedence`. Negated tokens are stored unconditionally as `option=false`, so negated string options require literal `false` and negated unknown options can affect scan priority like the reference. Assignment parsing checks `=` before `:` when both separators are present, malformed assignment sides are preserved, empty `!program.` disables are preserved, and missing, empty, or recursive `profile.*` dependencies throw like the 1.16.5 parser instead of being skipped. After the space-only list split, profile tokens are intentionally not trimmed; tab-suffixed options, disabled-program names, and dependency names remain raw parser input. `ProfileSetTest` covers these cases; option-menu and Complementary loader guards passed after the change.
- Persisted shader-pack config now has a startup activation path. `OculusClientEvents` calls `ShaderPackReloader.applyConfiguredShaderPack()` once after `Minecraft.gameDir` is available, and the helper loads the selected pack from `<gameDir>/shaderpacks` using the saved option overrides before applying it to `PipelineManager`. Disabled config, missing selection, unavailable shaderpacks directory, and load failure fall back to `ShaderPack.internal()`. Public reload now attempts `TextureFormatLoader.registerReloadListener()`, reloads `OculusConfig` from disk, applies the configured pack like 1.16.5 `Iris.reload()`, and prepares the loaded-world pipeline after the reload decision; the old active-pack reload remains only as the no-config fallback. The reload and toggle keys now call that same public reload path, matching the local 1.16.5 keybind boundary more closely than the previous startup-helper/active-pack split. `OculusConfig` now treats persisted or setter-supplied `selectedPackName=(internal)` like the 1.16.5 internal-pack sentinel and normalizes it to no external pack before reload application. It also matches the 1.16.5 enabled-default boundary: a new config or a loaded config with no `shadersEnabled` key is enabled unless the key is exactly `false`. GUI pack selection no longer writes `selectedPackName` until Apply/Done, so reload/config-driven pipeline creation cannot see an uncommitted GUI selection; the shader-enable state created by clicking a pack while shaders are disabled also remains pending across selection refresh, the applied-pack marker uses the captured baseline until Apply, and main-screen Escape now applies through the normal close path while Cancel discards. Unit/source coverage lives in `ShaderPackReloaderTest`, `OculusClientEventsSourceTest`, `OculusConfigTest`, and `ShaderPackScreenSourceTest`; live runtime validation now covers startup config activation followed by world-entry creation of `ShaderWorldRenderingPipeline`, but reload-key/toggle/resource-reload and GUI apply/cancel/Escape behavior are still runtime-unverified.
- Shader-pack `underwaterOverlay`, `vignette`, and `clouds` now have exact 1.12 runtime hooks. `ItemRendererOverlayMixin` cancels `ItemRenderer.renderWaterOverlayTexture` when the shader pipeline disables the water overlay, `GuiIngameOverlayMixin` cancels `GuiIngame.renderVignette` with 1.12 depth/default-blend restoration when the shader pipeline disables vignette, and `GameSettingsCloudsMixin` maps shader-pack cloud `OFF`/`FAST`/`FANCY` to 1.12 `shouldRenderClouds()` return values while preserving the vanilla render-distance-less-than-4 guard. `FixedFunctionWorldRenderingPipeline` explicitly keeps vanilla water overlay and vignette enabled when shaders are disabled. Compile and mixin-loader checks passed; in-client visual validation is still missing.
- `iris_LightmapTextureMatrix` is registered and shader source patching rewrites live `gl_TextureMatrix[1]` references outside comments and strings.
- Color-space support is wired through config, GUI, shader defines, `currentColorSpace`, compute conversion, and GLSL 120 fragment fallback.
- Shadow color clear directives are wired: the first shadow frame fully clears color targets, then `shadowcolor*Clear` and `shadowcolor*ClearColor` control later clears. The temporary 1.12 clear framebuffer now checks `OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER)` after attaching each shadow color texture and setting draw buffers, then throws before `glClear` if the clear target is incomplete. Source coverage lives in `ShadowMapTextureStateTest`; runtime shadow color output remains unproven.
- `entityShadowDistanceMul` now creates a separate entity/block-entity shadow culling camera for positive values such as Complementary's `0.125`; `1.0` and negative values reuse the terrain camera like 1.16.5. Unit coverage lives in `ShadowEntityCullingTest`; runtime validation is still missing.
- Shadow sampler aliases now apply per program through `ShadowMap.applySamplerBindings`, including the 1.16.5 `watershadow` branch and `shadowtex*HW` hardware-filtering gate. Unit coverage lives in `ShadowSamplerBindingsTest`; runtime validation is still missing.
- Shadow depth textures now use the 1.16.5 `{RED, RED, RED, ONE}` RGBA swizzle compatibility workaround for old packs, shadow texture setup routes allocation/filter/wrap/swizzle calls through `OculusRenderSystem` bind-to-edit wrappers, and shadow textures switch to mipmap min filters only after cache-aware `OculusRenderSystem.generateMipmaps(...)` runs on the dedicated mipmap unit. Unit/source coverage lives in `ShadowMapTextureStateTest`; runtime validation is still missing.
- Shadow target preparation now restores the default active texture through `OculusRenderSystem.restoreDefaultActiveTexture()` before binding the shadow framebuffer for depth clear, shadow compute dispatch, and shadow color clears. This mirrors the local 1.16.5 `DeferredWorldRenderingPipeline.prepareRenderTargets()` `GL_TEXTURE0` reset while updating the 1.12 `GlStateManager` cache. Bytecode coverage lives in `ShadowRendererBytecodeTest`; runtime validation is still missing.
- Shadow terrain now restores the default active texture through `OculusRenderSystem.restoreDefaultActiveTexture()` before binding `TextureMap.LOCATION_BLOCKS_TEXTURE`, preventing prior compute/sampler work from making the 1.12 `TextureManager` bind the block atlas on a non-default unit. Bytecode coverage lives in `ShadowRendererBytecodeTest`; runtime validation is still missing.
- Shadow matrix cleanup now returns the active matrix mode to `GL_MODELVIEW` after popping the shadow projection matrix, matching the local 1.16.5 `IrisRenderSystem.restoreProjectionMatrix()` cleanup tail. Source coverage lives in `ShadowRendererSourceTest`; runtime validation is still missing.
- `ProgramBuilder` now treats OptiFine level samplers as external only for world/shadow program names: `tex`, `texture`, and `gtexture` bind to vanilla albedo texture unit `0`, while `lightmap` binds to the current 1.12 lightmap unit. It also reserves world units `0`, `1`, and `2` for that same name scope so later dynamic samplers cannot collide with the level bindings, and it filters automatic registry fallback so low render targets, composite-only `gdepthtex` / `depthtex2`, and root-shadow-only `depthtex0` / `depthtex1` are not exposed outside the reference stage surface. This mirrors `IrisSamplers.addLevelSamplers(...)` plus `WORLD_RESERVED_TEXTURE_UNITS` and protects Complementary's shared `uniform sampler2D tex;` from being resolved as an unregistered dynamic `TextureBinding`. Unit coverage lives in `InternalProgramBuilderPathTest`; runtime validation is still missing.
- `ShaderLoader.getProgram(sourceName, availability)` must not fall back to the un-specialized base program for `gbuffers_*` or root shadow names. The 1.16.5 `ProgramTable` caches passes by exact `InputAvailability`, so a missing variant should return no program instead of silently running source prepared for different texture/lightmap/overlay inputs. Source coverage lives in `ShaderLoaderSourceTest`; runtime variant-link failure surfacing is still not visually proven.
- Shadow projection and grid snap math now match the 1.16.5 `ShadowMatrices` / `MatrixUniforms` split: raw `shadowDistance` drives the shader-visible orthographic `shadowProjection`, `shadowMapFov` only selects the GL shadow render projection, and `shadowIntervalSize` skips snapping only at exact zero while preserving negative-coordinate remainder behavior. Unit coverage lives in `ShadowUniformsMatrixTest`; runtime validation is still missing.
- Shadow culling distance now preserves the local 1.16.5 split for negative `shadowDistanceRenderMul`. Normal/default culling uses persisted `OculusConfig.maxShadowRenderDistance`, defaulting to 32 chunks, and default/unforced user distance `0` skips the shadow pass like the reference. Explicit non-negative multipliers that round to `0` chunks also skip, while explicit negative multipliers and explicit shader-pack-disabled culling keep the negative-value branches. Unit coverage lives in `OculusConfigTest` and `ShadowEntityCullingTest`; runtime shadow frustum validation is still missing.
- `shadow.culling` parsing deliberately accepts exact lowercase `true`, `false`, and target-pack `reversed` only. Do not restore `equalsIgnoreCase`; other 1.16.5 boolean directives are case-sensitive, and `ShadowCullingModeTest` pins that uppercase variants leave the directive at default.
- `ShadowRenderingState` now exposes the 1.16.5-style pass-wide active state for the whole shadow-map render while keeping entity/block-entity directive filtering scoped only to `RenderGlobal.renderEntities`. Unit coverage lives in `ShadowRenderingStateTest`; runtime validation is still missing.
- Stage custom texture overrides now match the 1.16.5 `CustomTextureSamplerInterceptor` rule for render targets: once an exact `colortexN` name or exact legacy alias has been flipped in a prepare/deferred/composite stage, later passes in that stage stop using the static custom texture override and sample the live render target. Differently cased sampler names are not treated as those deactivated aliases, stage binding maps preserve exact sampler spelling for non-render-target custom samplers such as Complementary's `textureAtlas`, and `ProgramSamplers` now matches custom override names exactly after active sampler discovery instead of treating `textureAtlas` and `textureatlas` as interchangeable. Unit coverage lives in `CustomTextureManagerTest` and `ProgramSamplersTest`; runtime validation is still missing.
- `PER_BUFFER_BLENDING` is recognized in `IrisFeatureDefines` and is gated by `OculusRenderSystem.supportsBufferBlending()`, matching the local 1.16.5 `FeatureFlags` entry. Required feature flags validate but do not emit `IRIS_FEATURE_*` defines; optional supported flags emit defines. Feature tokens intentionally split only on spaces, preserve case and duplicates, and validate case-sensitively like the 1.16.5 enum lookup. The 1.12 port still supports extra uppercase target-pack features such as `CUSTOM_IMAGES`, `SSBO`, and `BLOCK_EMISSION_ATTRIBUTE`; do not use that as evidence to normalize arbitrary case. `shaders.properties` now uses a two-pass preprocess so supported optional feature defines are available to same-file metadata guards; this preserves Complementary's `IRIS_FEATURE_BLOCK_EMISSION_ATTRIBUTE` gated `size.buffer.*` entries. Headless tests prove optional packs do not receive unavailable hardware-gated defines when `oculus.disableGlCapabilityProbes=true`; live GL validation is still missing.
- GLSL JCPP preprocessing now has a direct source-backed guard. `JcppProcessor` preserves the local 1.16.5 warning-marker flow for `#version` / `#extension`, hoists only active directives after conditional preprocessing, rejects reserved internal markers in pack source, keeps source preprocessing failures distinct from macro setup failures, and uses the reference macro setup error text. Unit coverage lives in `JcppProcessorTest`; runtime shader compilation still needs broader pack/client proof.
- Sampler binding now preserves the active texture unit across program sampler updates/clears, dynamic sampler allocation respects the runtime texture-unit limit via `SamplerLimits`, dynamic samplers attach to the current program after active uniform discovery, composite/final programs reserve texture units `1` and `2` like the 1.16.5 postprocess path, aliases can reuse an already allocated texture unit, 2D sampler binds use the 1.12 cache-aware texture-unit wrapper instead of raw GL on vanilla-cached units, compute program creation fails before `.csh` compilation when compute support is unavailable like the local 1.16.5 builder, compute dispatch honors the 1.16.5 `allowConcurrentCompute` pre-dispatch memory-barrier rule, and composite/final cleanup unbinds all reported sampler units. Unit/source coverage lives in `ProgramSamplersTest`, `OculusRenderSystemCapabilityDispatchTest`, and `InternalProgramBuilderPathTest`; runtime validation is still missing.
- Postprocess shader failure handling now matches the local 1.16.5 renderer more closely: present composite/final raster and compute creation failures propagate instead of being logged and skipped. Only an absent valid final raster source uses the baseline copy path. Source coverage lives in `PostprocessRendererSourceTest`; runtime postprocess output validation is still missing.
- Postprocess render-target mipmap setup/reset and final baseline-copy texture operations now use `OculusRenderSystem` wrappers and `GlStateManager` texture binds instead of raw bind-to-edit calls, matching the 1.16.5 `IrisRenderSystem` non-DSA state-cache boundary. Source coverage lives in `PostprocessRendererSourceTest`; runtime mipmap/copy output validation is still missing.
- Active render-target/depth texture allocation and resizing now use `OculusRenderSystem.texImage2D(...)` plus `texParameteri(...)`, so `RenderTarget`, `DepthTexture`, and the private framebuffer-manager depth texture path bind 2D textures through `GlStateManager` and record texture metadata through `TextureLifecycleTracker` like the 1.16.5 non-DSA helper boundary. `RenderTargetsSourceTest#renderTargetAndDepthAllocationUseReferenceStyleBindWrappers` guards this, and `RenderTargetsSourceTest#legacyFramebufferManagerTextureSetupUsesReferenceStyleBindWrappers` covers the older `net.oculus.pipeline.framebuffer.FramebufferManager` private color/depth/noise cache, including `noiseTextureResolution` plus `Random(0)` x-major fallback noise. Shadow depth/color allocation has the same wrapper coverage in `ShadowMapTextureStateTest`, and `CenterDepthSamplerSourceTest` now guards center-depth 1x1 texture setup plus the sampling copy through `OculusRenderSystem.copyTexSubImage2D(...)`. The render-target depth-copy allocation/update split remains intentional; shadow and center-depth per-frame subimage copies use the cache-aware helper.
- Missing postprocess targets now fail fast where the 1.16.5 reference dereferences them: composite draw-target sizing, composite resize sizing, composite/final mipmap setup, final swap-pass creation, final swap-pass resize, and final render-target mipmap reset. Do not restore the old silent `target == null` skips for explicitly referenced pass targets. Missing draw-buffer directives still default to `{0}` in `ProgramDirectives`, but explicitly empty draw-buffer arrays are no longer normalized again in `CompositeRenderer`. General sampler enumeration still skips absent sparse entries while walking target slots. Source coverage lives in `PostprocessRendererSourceTest` and `FinalPassRendererSourceTest`; runtime output validation is still missing.
- Composite/final raster sources now use `ShaderSourcePreparer.prepareProgram(...)` directly after source extraction. Do not reintroduce renderer-local `patchCompositeShader(...)` or `patchFinalShader(...)` helpers; the shared preparer already owns environment defines, `centerDepthSmooth`, GLSL 120 `texture*Lod`, and grouped cross-stage patches. The removed helpers synthesized `#version 120` after the preparer had made explicit versions mandatory and could corrupt geometry-stage `in` declarations with blanket `in vec` replacements. Source coverage lives in `PostprocessRendererSourceTest`; runtime geometry-shader postprocess validation is still missing.
- `noisetex` now matches the 1.16.5 manager behavior more closely: `texture.noise` still wins, packs without it receive a deterministic generated fallback using `noiseTextureResolution` and the reference x-major `Random(0)` pixel order, and a declared custom noise binding that cannot be built now falls back to generated noise instead of leaving global noise samplers unbound. Postprocess programs bind the noise supplier before stage custom overrides, manager teardown unregisters owned global noise aliases by binding identity before deleting owned textures, and failed generated-noise setup deletes the generated texture before manager ownership is recorded. Unit coverage lives in `CustomTextureManagerTest` and `TextureBindingRegistryTest`; runtime validation is still missing.
- Texture-format metadata and the first runtime PBR binding slices are implemented. `TextureFormatLoader` registers with the 1.12 reloadable resource manager only after the listener is actually accepted, reads `minecraft:optifine/texture.properties`, recognizes the registered `lab-pbr` format, clears when the resource disappears, reloads the active shader pack when the format changes, clears cached PBR holders, and feeds `MC_TEXTURE_FORMAT_*` / version defines into `StandardMacros`. `TextureInfoCache` tracks level-0 metadata for `GlStateManager.glTexImage2D(..., IntBuffer)` uploads, direct port-owned `GL11.glTexImage2D(...)` uploads through `TextureLifecycleTracker`, shader-pack PNG uploads after `TextureUtil.uploadTextureImageAllocate(...)`, and PBR atlas allocations immediately after `TextureUtil.allocateTextureImpl(...)`; it clears on low-level texture delete and backs `atlasSize` / `gtextureSize` through `GameplayUniforms` with a lazy GL query fallback for uncached texture IDs. `GlStateManagerStateMixin` now mirrors the 1.16.5 `TextureTracker` constraint: bind tracking and PBR side effects run only for the base texture unit, with a recursion lock and a final `GlStateManager.bindTexture(texture)` restore so PBR uploads do not leave the cached vanilla binding stale. `ShaderWorldRenderingPipeline` now also mirrors the reference `shouldBindPBR && isRenderingWorld` gate by allowing PBR bind updates only between world begin and the pre-composite finalization point, and it no longer skips texture ID `0` unbinds so active PBR samplers reset to default companions instead of stale `_n` / `_s` textures. `PBRTextureManager` detects active `normals` / `specular` samplers, publishes normal/specular texture-change notifications, and `ProgramSamplers` now maps those sampler names to notifier-backed texture rebinds while preserving the active texture unit. It also binds default normal/specular textures, loads `_n` / `_s` companions for `SimpleTexture`, resolves `TextureMap` atlas `_n` / `_s` sprites into dedicated PBR atlas textures, resolves `_n` / `_s` custom resource textures through the base resource, updates animated PBR atlas sprites after vanilla atlas animation ticks, selects sparse animated atlas frames through the same nearest-previous-valid fallback as the local 1.16.5 upload path, applies source-backed LabPBR specular nearest filtering, uses LabPBR custom specular mipmap blending, listens for low-level texture deletion through `TextureLifecycleTracker`, and emits disabled-by-default validation telemetry when `oculus.validation.pbrTextures` or auto-join validation is enabled. Unit coverage lives in `CustomTextureManagerTest`, `TextureFormatLoaderTest`, `TextureInfoCacheTest`, `TextureLifecycleTrackerSourceTest`, `PBRTextureManagerTest`, `ChannelMipmapGeneratorTest`, `LabPBRTextureFormatTest`, `PBRAtlasTextureTest`, `PBRAtlasSpriteTest`, `AtlasPBRLoaderTest`, `ProgramSamplersTest`, `GlStateManagerStateMixinSourceTest`, `GlStateManagerTextureLifecycleMixinSourceTest`, `ShaderWorldRenderingPipelineSourceTest`, `OculusRuntimeValidationTest`, and `StateUpdateNotifiersTest`. Full PBR runtime parity is still missing until simple, atlas, and custom resource PBR are proven in-client with a resource pack.
- Held item ID uniforms now fall back from the 1.12 registry names `lit_pumpkin` and `magma` to Complementary's modern `jack_o_lantern` and `magma_block` `item.properties` entries while keeping exact-name lookup first and keeping generic entity/block lookup unaffected. Unit coverage lives in `IdMapUniformsTest`; runtime validation is still missing.
- `entity.properties` IDs are now active runtime state on `BlockRenderingSettings`, not a direct read from `PipelineManager`'s active pack. `ShaderWorldRenderingPipeline` installs the pack entity map, fixed-function fallback clears it, and `IdMapUniforms.resolveEntityId(...)` consumes the active map; no chunk reload is marked because entity context is rebuilt every frame. `IdMapUniforms` also adapts 1.12 lightning entities to `EntityList.LIGHTNING_BOLT`, covering MakeUp's `minecraft:lightning_bolt` entry. `blockEntityId` consumes `BlockRenderingSettings#getBlockStateIds()` now, matching the 1.16.5 block-entity render context. Runtime `entityId` and `blockEntityId` validation is still missing.
- `block.properties` material IDs now resolve Complementary's modern lit/snow/color-split entries to 1.12 block states: `lit_furnace`, `lit_redstone_ore`, `lit_redstone_lamp`, `redstone_torch` / `unlit_redstone_torch`, `snow_layer`, full `snow`, `terracotta` to `hardened_clay`, color-split wool/carpet/concrete/concrete powder/stained glass/stained glass pane/terracotta names to the matching `color` predicate, and modern `light_gray` to the 1.12 `silver` color or registry name where applicable. Broad legacy color containers are skipped when a pack also declares modern color-split entries, so they do not preempt Complementary's later color-specific IDs through first-match `putIfAbsent(...)`. Exact modern direct aliases from Complementary and MakeUp active `block.properties` now also resolve to matching 1.12 registry names for grass blocks, dirt paths, slime blocks, wall torches, redstone wall torches without a lit predicate, note blocks, spawners, cobwebs, dead bushes, sugar cane, bricks, nether bricks, red nether bricks, end stone bricks, melons, and jack-o-lanterns. Modern single plants and potted plants now resolve through 1.12 shared-block state predicates, including `tallgrass:type=*`, `red_flower:type=*`, `yellow_flower:type=*`, `sapling:type=*`, and `flower_pot:contents=*`; bare `flower_pot` maps only to `contents=empty`. Modern double-plant names now resolve to the 1.12 `double_plant` block with the matching `variant` and preserved `half` predicates. MakeUp 9.3e malformed double-colon modded entries such as `betterendforge::lumecorn:shape=light_middle` now parse as `betterendforge:lumecorn` with predicates preserved, so those material IDs can reach terrain when the modded blocks exist. Unit and real-pack loader coverage lives in `BlockMaterialMappingTest` and `ShaderPackLoaderComplementaryTest`; runtime material-ID validation is still missing.
- Shader-pack block-state ID maps now remain shader-pack maps even when empty. `BlockRenderingSettings` owns the active map, `PipelineManager` no longer collapses `ShaderPack#getBlockStateIdMap().isEmpty()` to `null`, `ShaderWorldRenderingPipeline` installs the active pack map, fixed-function fallback clears to vanilla, and `BlockContextHolder` lazily builds vanilla IDs. This closes a parity bug where packs with no `block.properties` mappings could get vanilla `Block.BLOCK_STATE_IDS` instead of `-1`; runtime terrain attribute validation is still missing.
- `block.properties` `layer.*` render-layer overrides now reach Forge 1.12 chunk rebuild selection. The parser already captured the map; `PipelineManager.reloadShaderPack(...)` and `ShaderWorldRenderingPipeline` install `BlockMaterialMapping.createRenderLayerMap(...)` output into `BlockRenderingSettings`, `BlockRenderLayerOverrideMixin` answers Forge's `Block.canRenderInLayer(IBlockState, BlockRenderLayer)` hook, and fixed-function fallback clears the map. Reload also clears stale render-layer and entity-ID maps immediately when disabling shaders or falling back to the internal pack. This is a deliberate 1.12 lifecycle adaptation: global mixins and uniform helpers can read `BlockRenderingSettings` before the next pipeline constructor runs. Complementary's `layer.translucent=glass glass_pane beacon` is covered by `IdMapTest`, `BlockRenderingSettingsTest`, `PipelineManagerSourceTest`, `BlockRenderLayerOverrideMixinSourceTest`, `ShaderWorldRenderingPipelineSourceTest`, and `FixedFunctionWorldRenderingPipelineTest`; runtime translucent-block validation is still missing.
- World-info `bedrockLevel` now matches the local Oculus 1.16.5 `IrisExclusiveUniforms.WorldInfoUniforms` constant `0`. Do not replace it with `WorldProvider.getAverageGroundLevel()` unless the target reference changes. `hasCeiling` uses the 1.12 `WorldProvider.isNether()` flag so no-skylight non-Nether dimensions do not get misclassified as ceiling dimensions.
- Vanilla world-info `cloudHeight`, `logicalHeightLimit`, and `ambientLight` now use exact local 1.16.5 engine bytecode evidence: Nether and End cloud height map to `NaN`, Nether logical height maps to `128`, and Nether ambient light maps to `0.1`. Custom providers still use the closest 1.12 provider source unless more exact dimension-type evidence is found.
- Viewport uniforms now prefer `Minecraft.getFramebuffer().framebufferWidth` / `.framebufferHeight`, matching the local 1.16.5 `ViewportUniforms` main-render-target source. This covers `viewWidth`, `viewHeight`, `aspectRatio`, `u_ViewWidth`, `u_ViewHeight`, and `iris_ScreenSize`; direct `displayWidth` / `displayHeight` reads are only the 1.12 startup fallback.
- Fog uniforms now match the local 1.16.5 `FogUniforms` disabled-fog behavior for `fogMode`: when 1.12 `GL_FOG` is disabled, `fogMode` reports `0` instead of the stale GL mode enum. `ProgramBuilder` also wires active uniform notifiers for `fogMode`, `fogStart`, `fogEnd`, and `fogDensity` through verified 1.12 `GlStateManager` fog hooks, with the reference toggle-plus-specific notifier timing for each fog value. Unit coverage lives in `GameDataSuppliersTest` and `StateUpdateNotifiersTest`; runtime fog visual validation is still missing.
- Iris-exclusive player stat uniforms now follow the local 1.16.5 game-mode gate for `currentPlayerHealth`, `maxPlayerHealth`, `currentPlayerHunger`, `currentPlayerAir`, and `maxPlayerAir`: expose values only in survival-like modes and return `-1` otherwise. `maxPlayerHunger` is not gated; it is the reference constant `20`. 1.16.5 `GameType.isSurvival()` and 1.12 `GameType.isSurvivalOrAdventure()` were bytecode-confirmed to cover Survival and Adventure. Unit coverage lives in `GameplayUniformsTest`; runtime validation in survival, adventure, creative, and spectator is still missing.
- Iris-exclusive `isSpectator` now uses the local current game type from `PlayerControllerMP.getCurrentGameType()`, matching the 1.16.5 `gameMode.getPlayerMode() == GameType.SPECTATOR` source. Do not switch it back to `player.isSpectator()` without proof; 1.12 bytecode shows that path uses network player info. Unit coverage lives in `GameplayUniformsTest`; runtime game-mode transition validation is still missing.
- Iris-exclusive `playerLookVector` now uses 1.12 `Entity.getLookVec()`, matching the 1.16.5 `Entity.getLookAngle()` source used by `IrisExclusiveUniforms`. The prior `getLook(partialTicks)` path interpolated previous/current rotation and was not the reference source. Runtime validation with normal player and alternate camera entities is still missing.
- Iris-exclusive `playerBodyVector` now uses 1.12 `Entity.getForward()`, matching the 1.16.5 `Entity.getForward()` source used by `IrisExclusiveUniforms`. The prior `renderYawOffset` path was a horizontal body-yaw approximation and lost pitch/current-rotation behavior. Runtime validation with normal player and alternate camera entities is still missing.
- Common `nightVision` now uses the bytecode-confirmed vanilla fade curve shared by 1.16.5 `GameRenderer.getNightVisionScale` and 1.12 `EntityRenderer.getNightVisionBrightness`, instead of the old linear `duration / 200` approximation. Vanilla 1.12 has no conduit-power equivalent, and modded night-vision-effect parity still needs evidence. Unit coverage lives in `GameplayUniformsTest`; runtime validation for effect start, steady state, and fade-out is still missing.
- Common `eyeBrightness` now uses the current camera eye block like the local 1.16.5 source (`position()` plus `getEyeY()`), mapped to 1.12 `posX`, `posY + getEyeHeight()`, and `posZ`. The old `getPositionEyes(tickDelta)` lookup was intentionally removed only from brightness sampling; the separate `eyePosition` uniform still uses partial-tick eye position. Unit coverage lives in `GameplayUniformsTest`; runtime light-boundary validation is still missing.
- Frame update timing and camera position now have separate source-backed boundaries. `CapturedRenderingState.beginFrame(...)` still rolls previous camera/matrix state once at frame head, and `ShaderWorldRenderingPipeline.beginLevelRendering()` now fires the shared `FrameUpdateNotifier` after source-built programs register non-camera smoothed built-in listeners but before render-target clears, matching the local 1.16.5 `updateNotifier.onNewFrame()` before `prepareRenderTargets()` for those safe pre-camera values. `LevelRendererMixin` then calls `PipelineManager.afterCameraSetup((float) partialTicks)` from the existing `RenderGlobal.setupTerrain(...)` redirect, which bytecode places after `setupCameraTransform(...)` and `ActiveRenderInfo.updateRenderInfo(...)`. `CameraPositionTracker` reads `ActiveRenderInfo.projectViewFromEntity(entity, partialTicks)` at that point and falls back to `Entity.getPositionEyes(partialTicks)`, not a manual feet-position interpolation. `ShaderWorldRenderingPipeline.afterCameraSetup(...)` runs `GameplayUniforms.onFrameStart()`, `CompatibilityUniforms.onFrameStart()`, and `customUniforms.beginFrame()` after this refreshed capture, then syncs the current program. Keep this hook in the setup-terrain redirect; the direct `ActiveRenderInfo.updateRenderInfo(Entity, boolean)` injection target produced an MCP mapping warning under `stable_39`. Unit/source coverage lives in `CameraPositionTrackerSourceTest`, `CapturedRenderingStateTest`, `LevelRendererMixinSourceTest`, `InternalProgramBuilderPathTest`, and `ShaderWorldRenderingPipelineSourceTest`; runtime frame-update, camera split, previous-camera, matrix, and custom-uniform timing validation is still missing.
- Hardcoded compatibility uniform `inNetherWastes` now maps to the legacy 1.12 `Biomes.HELL` biome when a pack does not override it with a custom uniform. Other modern nether/pale-garden helper fallbacks remain zero because those biomes are absent in vanilla 1.12. Unit coverage lives in `CompatibilityUniformsTest`; runtime validation is still missing.
- Hardcoded compatibility formulas for `isEyeInCave`, camera `velocity`, `starter` movement detection, hardcoded world-day time, `effectStrength`, and legacy precipitation mapping are now unit-tested. Camera velocity and movement detection now cast deltas to `float` before magnitude/sum calculations, matching the local 1.16.5 `HardcodedCustomUniforms` reference. Hardcoded time helpers use vanilla Nether/End fixed times from exact local 1.16.5 bytecode, while general `worldTime` remains on the separate `WorldTimeUniforms` semantics. Runtime validation is still missing, and the existing 1.12 zero-frame-time guard around `effectStrength` velocity/frameTime remains a documented timing audit item.
- Direct hardcoded compatibility fallback registration now keeps `shadowFade` and `shdFade` separate in `ProgramBuilder`, matching local 1.16.5 `HardcodedCustomUniforms`: `shadowFade` uses the `0.23` / `100.0` fade formula, and `shdFade` uses the `0.225` / `40.0` formula. `CustomUniformExpressionManager` already resolved both names separately. Unit coverage lives in `InternalProgramBuilderPathTest`; direct in-client value validation is still missing.
- Smoothed uniform half-life math now matches the 1.16.5 formula across `SmoothedFloat`, custom expression `smooth()`, and `CenterDepthSampler`. Zero half-life now converges immediately, which fixes Complementary's `smooth(2, moving, 0, 31536000)` behavior. Legacy postprocess `uniform float centerDepthSmooth;` declarations are transformed into `uniform sampler2D iris_centerDepthSmooth;` reads, and `CenterDepthSampler` now renders the 1.16.5-style 1x1 smoothed color texture pair rather than a CPU float/raw-depth texture. Its copy into the alternate 1x1 texture goes through `OculusRenderSystem.copyTexSubImage2D(...)`, matching the local 1.16.5 `DepthCopyStrategy` non-DSA fallback, and after sampling it rebinds `Minecraft.getFramebuffer().bindFramebuffer(true)` like the 1.16.5 `mainRenderTarget.bindWrite(true)` postprocess boundary. The transform is intentionally scoped to composite-style postprocess program names (`prepare*`, `deferred*`, `composite*`, `final*`, `shadowcomp*`); `gbuffers_*` and root `shadow` stay on the world/attribute transform path. Unit coverage lives in `SmoothedFloatTest`, `CustomUniformExpressionManagerTest`, `ShaderCompatibilityPatcherTest`, and `CenterDepthSamplerSourceTest`; runtime visual timing is still unverified.
- Built-in gameplay smoothing now uses shader-pack directives instead of the old hardcoded constants. `GameplayUniforms` defaults to the 1.16.5 `PackDirectives` values for `wetness` and `eyeBrightnessSmooth`, `ShaderWorldRenderingPipeline` calls `GameplayUniforms.configure(directives)` when a pack pipeline is built, and reconfiguration resets smoother accumulators like constructing new 1.16.5 smoothers. Unit coverage lives in `GameplayUniformsTest`, `SmoothedFloatTest`, and `ShaderWorldRenderingPipelineSourceTest`; rain and eye-brightness visual timing is still unverified.
- Internal helper programs that bind their own uniforms/samplers now have `ProgramBuilder.beginExplicit(...)` so active-uniform auto-discovery does not log false unknowns before explicit caller bindings are attached. `CenterDepthSampler` uses this path for `centerDepthSmooth`, matching the local 1.16.5 explicit binding pattern for `depth`, `altDepth`, `lastFrameTime`, and `decay`. The fragment color-space fallback uses the same path for its manually-bound `readImage` sampler. Unit coverage lives in `InternalProgramBuilderPathTest`; the archived `2026-05-16 15:13` Complementary world smoke confirms the old `centerDepthSmooth` unknown sampler/uniform warnings are gone.
- `ColorSpaceComputeConverter` now binds its `readImage` image uniform through the same default `ProgramImages` path as local Oculus 1.16.5. The prior explicit non-layered flag was unnecessary for the 2D framebuffer texture because OpenGL ignores `layered` for non-layered texture targets. Its process path also follows the inspected 1.16.5 sequence: `program.use()`, direct `width / 8` and `height / 8` workgroup dispatch, post-dispatch memory barrier, and `ComputeProgram.unbind()`, instead of routing through the generic shader-pack compute dispatch helper. Source coverage lives in `ColorSpaceShaderSourceTest`; runtime compute color-space output is still unverified.
- `ColorSpaceFragmentConverter` intentionally does not reuse the local 1.16.5 `/colorSpace.vsh` verbatim. The reference shader expects `iris_Position`, `iris_UV0`, and a `projection` uniform for a `[0, 1]` quad, while the 1.12 fullscreen helper emits clip-space `gl_Vertex` positions plus fixed-function UVs. The generated GLSL 120 vertex source is a 1.12 adapter, and `ColorSpaceShaderSourceTest` guards this shape plus the swap-FBO copyback path. The fragment copyback now also preserves the previous `GL_TEXTURE_BINDING_2D` around `glCopyTexSubImage2D`, matching the local 1.16.5 non-DSA `IrisRenderSystem.copyTexSubImage2D(...)` fallback instead of leaving texture 2D unbound on unit 0.
- `ShaderWorldRenderingPipeline` now tracks the main framebuffer width/height used to build the active color-space converter and rebuilds it when those dimensions change, matching the local 1.16.5 resize-or-color-change rebuild rule. Dimension tracking happens before the `supportsColorCorrection()` no-op branch so pack-owned color correction does not cause a no-op rebuild every frame. This protects compute workgroup sizing and the fragment fallback swap texture/FBO size. Source coverage lives in `ShaderWorldRenderingPipelineSourceTest`; runtime resize validation is still missing.
- Scalar and vector custom expressions now cover MakeUp-style OptiFine helpers and symbols including `fmod`, optional-ID `smooth`, view dimensions plus `u_View*` aliases, aspect ratio, vector constructors/arithmetic, fixed scalar/vector built-in uniform symbols, bare built-in vector identifiers such as `cameraPosition`, hardcoded fallback symbols with shaderpack directive precedence, `uniform.vec2.taa_offset`, and `gbufferProjection.1.1` matrix access. This prevents custom directives from overriding hardcoded fallback uniforms with missing-function or missing-symbol zeros. Camera split vectors, external matrix aliases, shadow matrix aliases, terrain scale aliases, celestial vectors (`sunPosition`, `moonPosition`, `shadowLightPosition`, `upPosition`), and Iris-exclusive special-effect vectors (`relativeEyePosition`, `lightningBoltPosition`) are now also visible to custom expressions, matching runtime uniforms that `ProgramBuilder` already registers from `CapturedRenderingState`, `ShadowUniforms`, `CelestialUniforms`, `GameplayUniforms`, and `SpecialEffectUniforms`; the special-effect suppliers return zero vectors safely when no client exists. Known dynamic pass/object/fog/atlas/texture/blend symbols are now dependency-tracked through custom variables and evaluated on uniform upload instead of being frozen at frame start. `atlasSize` is intentionally in this dynamic set because local 1.16.5 `CommonUniforms` registers it with `StateUpdateNotifiers.bindTextureNotifier`. Unit coverage lives in `CustomUniformExpressionManagerTest`; exact duplicate smooth-ID collision behavior, external/shadow alias timing, celestial/lightning/camera-relative runtime values, and runtime visual timing remain future validation work. The current smooth-ID evidence and decision are documented in `docs/custom-uniform-smooth-semantics.md`.
- `ProgramBuilderReferenceUniformCoverageTest` now guards the active local 1.16.5 built-in uniform name surface against accidental case removal. The sweep intentionally treats `heldBlockLightColor` / `heldBlockLightColor2` as false positives because they are commented-out TODO lines in the local 1.16.5 `IdMapUniforms`. This is only name-surface evidence; type, update frequency, notifier timing, and in-client values still need deeper validation.
- `iris_NormalMatrix` now matches the local 1.16.5 Sodium chunk override's inverse-transpose upload path. `CapturedRenderingState` derives `normalMatrix` from the current model-view inverse plus transpose, `ProgramBuilder` binds `iris_NormalMatrix` from that capture, and custom matrix expressions read the same value. Generated Sodium terrain still declares `uniform mat4 iris_NormalMatrix;` and rewrites `gl_NormalMatrix` to `mat3(iris_NormalMatrix)`. Unit/source coverage lives in `MatrixMathTest`, `CapturedRenderingStateTest`, `InternalProgramBuilderPathTest`, `CustomUniformExpressionManagerTest`, `SodiumTerrainShaderTransformerTest`, and `SodiumTerrainPipelineTest`; live Relictium terrain normal/lighting validation is still missing.
- GLSL 120 postprocess programs that call `texture2DLod` or `texture3DLod` now receive `#extension GL_ARB_shader_texture_lod : require`, matching the local 1.16.5 `CompositeTransformer` path for composite-transformed sources. The reference does not deduplicate an existing extension directive, so the 1.12 patcher intentionally injects again in that case. The scope is intentionally postprocess program names only (`prepare*`, `deferred*`, `composite*`, `final*`, `shadowcomp*`); gbuffers/shadow sources need separate evidence before broadening. Unit coverage lives in `ShaderCompatibilityPatcherTest`; runtime shader-compile validation is still missing.
- Vertex shader source patching now mirrors the local 1.16.5 `CompatibilityTransformer` Sildur water workaround by rewriting `fract(worldpos.y + 0.001)` to `fract(worldpos.y + 0.01)` outside comments and strings. Unit coverage lives in `ShaderCompatibilityPatcherTest`; runtime visual validation with an affected pack is still missing.
- World and root shadow shader programs now have `InputAvailability`-specific compiled variants like the local 1.16.5 `ProgramTable` cache. `ShaderLoader` compiles variants for `gbuffers_*`, `shadow`, `shadow_*`, and `shadow.*`, and `ShaderWorldRenderingPipeline` selects them with `getProgram(sourceName, availability)`. Do not include `SodiumTerrainPipeline` in this availability branch: `_sodium` sources now prepare without `InputAvailability`, matching local 1.16.5 `patchSodiumTerrain(...)`, which runs `SodiumTerrainTransformer` plus common compatibility transforms but not `AttributeTransformer`. The implemented availability transform covers `gl_MultiTexCoord0/1/2`, the full texture-matrix array shape, and the 1.16.5 compatibility-profile gate for world/root-shadow variants: lightmap-present variants alias modern `gl_MultiTexCoord2` back to 1.12 `gl_MultiTexCoord1`, missing texture/lightmap variants use `vec4(240.0, 240.0, 0.0, 1.0)`, non-core availability variants always inject `iris_TextureMatrix[8]`, original `gl_TextureMatrix` references become `iris_TextureMatrix` when present, core-profile vertex availability sources throw `Vertex shaders must be in the compatibility profile to run properly!`, and core-profile non-vertex stages skip the attribute rewrites. The 1.12 adaptation is bytecode-backed: `OpenGlHelper.lightmapTexUnit` is `GL_TEXTURE1`, `DefaultVertexFormats.TEX_2S` uses UV index `1`, and Forge uploads UVs to `defaultTexUnit + element.getIndex()`. Unit/source coverage lives in `ShaderCompatibilityPatcherTest`, `ShaderSourcePreparerTest`, `ShaderLoaderSourceTest`, and `ShaderWorldRenderingPipelineSourceTest`; runtime validation is still missing.
- Root `shadow` raster program ownership now matches the inspected 1.16.5 pass-table boundary. Keep `programSet.getShadow()` in generic `ShaderLoader` variant compilation, keep `ShaderWorldRenderingPipeline.renderShadows(...)` syncing the active shadow variant before `ShadowRenderer.renderShadows(...)`, and do not reintroduce `ShadowRenderer`-owned raster `shadowProgram` compilation, binding, unbinding, or destruction. Shadow target preparation is now a pipeline-triggered boundary before that sync: depth clear, shadow compute dispatch, and shadow color clear run through `ShadowRenderer.prepareRenderTargets()` once per frame before `prepareBeforeShadow` and before `isRenderingShadow=true`. Source coverage lives in `ShadowRendererSourceTest`, `ShaderWorldRenderingPipelineSourceTest`, and `ShaderLoaderSourceTest`; runtime shadow output is still not proven.
- Root shadow render-target read buffers now follow the inspected `prepareBeforeShadow` rule. During root shadow raster rendering, suppliers must return `flippedAfterPrepare` only when `prepareBeforeShadow=true`; otherwise they return the empty pre-shadow read set. The current ShadowRenderer-owned shadow compute bindings always use the empty pre-shadow set, matching the 1.16.5 `createShadowComputes(...)` supplier. Source coverage lives in `ShaderWorldRenderingPipelineSourceTest`; runtime validation with packs that read render targets during shadow work is still missing.
- Shadow compute dispatch moved with the surrounding clear ordering. Do not move only compute compilation/dispatch again: the important invariant is that depth clear, compute dispatch, and color clear remain together and happen before the root shadow raster program sync. Do not add a target-prep post-dispatch memory barrier unless new 1.16.5 evidence shows one; the inspected local reference goes from shadow compute dispatch to shadow color clear, while `ComputeProgram.dispatch(...)` owns the pre-dispatch barrier. On the current 1.12 hook layout, this block also waits until `afterCameraSetup(...)` has refreshed camera-dependent custom-uniform caches.
- `InputAvailability.overlay` now has a real 1.12 source instead of being permanently false. `RenderLivingBase#setBrightness(...)` bytecode proves the legacy entity brightness overlay enables texture 2D on `OpenGlHelper.GL_TEXTURE2`, binds the white `TEXTURE_BRIGHTNESS` texture, and carries hurt/custom multiplier color through fixed-function texture-env constants. `GlStateManagerStateMixin` therefore maps `OpenGlHelper.GL_TEXTURE2 - OpenGlHelper.defaultTexUnit` to `StateTracker.overlaySampler`, and `StateTracker.getInputs()` propagates it. `RenderLivingBaseEntityColorMixin` also captures vanilla's post-`setBrightness(...)` `brightnessBuffer` into `GameplayUniforms.entityColor` and clears it after `unsetBrightness()`, so 1.12 shader code can see the exact brightness overlay color without recomputing renderer overrides. Because 1.12 `ModelRenderer` draws normal model parts through `GlStateManager.callList(int)`, `GlStateManagerStateMixin` now syncs programs before display-list draws as well as `glDrawArrays`, ensuring the captured value can upload before legacy entity/model rendering. `ProgramBuilder` binds declared `iris_overlay` samplers in world/shadow availability variants: overlay-present variants point at the 1.12 brightness overlay unit, and overlay-missing variants use an owned 1x1 white fallback texture. `ShaderCompatibilityPatcher` now adapts the 1.16.5 `entityColor` varying passthrough without sampling that white texture: it removes pack `uniform vec4 entityColor`, injects vertex `uniform vec4 iris_entityColor` bound to `GameplayUniforms.getEntityColor()`, passes `varying vec4 entityColor` through the raster stages, and renames the fragment side to `entityColorGS` when a geometry stage exists. The local Complementary `gbuffers_entities` source is covered through the loader and common source preparer. Coverage lives in `StateTrackerTest`, `GlStateManagerStateMixinSourceTest`, `GameplayUniformsTest`, `RenderLivingBaseEntityColorMixinSourceTest`, `InternalProgramBuilderPathTest`, `ShaderLoaderSourceTest`, `ShaderSourcePreparerTest`, `ShaderCompatibilityPatcherTest`, `ShaderPackLoaderComplementaryTest`, and `FallbackTexturesTest`.
- Vertex shader source patching also mirrors the local 1.16.5 `AttributeTransformer` legacy mid-texcoord alias by rewriting `gl_MultiTexCoord3` to `mc_midTexCoord` and injecting `attribute vec4 mc_midTexCoord;` when the pack has not already declared that attribute and the source is not core-profile. Remaining `AttributeTransformer` parity is not complete: the overlay passthrough is source-backed now, but still needs exact in-client evidence across entity hurt/custom-color draws and geometry/no-geometry shader variants. Unit coverage lives in `ShaderCompatibilityPatcherTest`; runtime validation with a pack that uses these paths is still missing.
- Raster program source preparation now runs a grouped compatibility patch across vertex, optional geometry, and fragment sources. It adds missing numeric previous-stage outputs for used later-stage `in`/`varying` declarations, initializes same-type previous-stage outputs that were only declared, and repairs same-dimension previous-output/current-input type mismatches by moving previous-stage references behind an `iris_template_` internal variable and appending the cast assignment used by the 1.16.5 transformer. The supported text classifier now covers the local 1.16.5 `BuiltinNumericTypeSpecifier` breadth, including `bool`/`bvec*`, explicit integer widths such as `int8_t` / `ui64vec*`, `double` / `dvec*` / `dmat*`, and explicit `f16`/`f32`/`f64` float vector/matrix spellings. Runtime world, shadow, composite, and final raster compile paths use `ShaderSourcePreparer.prepareProgram`; unit coverage lives in `ShaderCompatibilityPatcherTest` and `ShaderSourcePreparerTest`. The latest Complementary smoke compiles the active pack, but broader packs, rare numeric interface declarations on the target GL context, and visual correctness remain unverified.
- Regular raster shader source preparation now mirrors the local 1.16.5 `TransformPatcher` validation boundary more closely: non-compute pack sources must have an explicit numeric `#version` before environment defines or compatibility patches run, and pack-source identifiers starting with `iris_` or named `irisMain` fail before text transforms run while comments, strings, character literals, and preprocessor lines are ignored. Generated `_sodium` program names are allowed through later internal-interface processing because `SodiumTerrainShaderTransformer` already ran the same guard before injecting its generated `iris_*` declarations. Sodium terrain generated declarations are injected without deduplicating against pack declarations, matching the 1.16.5 `SodiumTerrainTransformer` direct injection path. Compute sources intentionally keep compiler-path version handling because the inspected 1.16.5 compute builders do not route them through `TransformPatcher`. Unit coverage lives in `ShaderCompatibilityPatcherTest`, `ShaderSourcePreparerTest`, and `SodiumTerrainShaderTransformerTest`; `ShaderPackLoaderComplementaryTest` and `SodiumTerrainPipelineTest` passed after the internal-interface change.
- Raster shader source patching now mirrors the local 1.16.5 `CompatibilityTransformer` unused-function cleanup by removing non-`main` top-level function definitions whose name appears only in the definition. Unit coverage lives in `ShaderCompatibilityPatcherTest`; this is compiler-resilience evidence until broader runtime shader compilation is proven.
- Unused-function cleanup warning text now includes the first removed function name, shader stage, program, pack, and omitted count, matching the local 1.16.5 "first function plus omitted further messages" warning policy more closely than the previous generic program-only warning.
- Raster shader source patching now mirrors the local 1.16.5 `CompatibilityTransformer` const-parameter declaration cleanup by removing `const` from function-local declarations initialized from `const` parameters and from transitive declarations derived from those locals. It also matches the reference failure path for illegal tracked-name redefinition during that cleanup. Unit coverage lives in `ShaderCompatibilityPatcherTest`; a local Complementary/MakeUp search found no direct const-parameter function signatures, so runtime value is still broader-pack shader compile resilience.
- Raster shader source patching now mirrors the local 1.16.5 `CompatibilityTransformer` empty-declaration cleanup by removing top-level standalone semicolons while preserving declaration terminators, struct terminators, and function-local semicolons. Unit coverage lives in `ShaderCompatibilityPatcherTest`; this is source-preparation evidence only.
- The legacy preprocessor alias `vaPosition` now expands to `gl_Vertex.xyz` instead of `gl_Vertex`. This addresses the logged Complementary `gbuffers_line.vsh` compile failure where `vec4(vaPosition, 1.0)` became an invalid `vec4(gl_Vertex, 1.0)`. Unit coverage lives in `ShaderPreprocessorTest`; the latest Complementary smoke loaded `gbuffers_line` without the old compile failure.
- Standard preprocessing macros now report the real Forge 1.12.2 target value for `MC_VERSION`: default/headless preprocessing yields `11202`, not a faked `11605`. This matches the 1.16.5 reference behavior of reporting the running Minecraft version while adapting it to the 1.12.2 target. `oculus.mcVersionOverride` is still available for deliberate pack-debug runs, unknown headless GL/GLSL probes return `000` rather than a Minecraft version, and GL vendor/renderer classifiers match the 1.16.5 prefix table including `MC_GL_VENDOR_ATI` and `NVS*` as `MC_GL_RENDERER_QUADRO`. Unit coverage lives in `StandardMacrosTest`; Complementary loader and Sodium terrain source guards were rerun after the MC version change.
- Active framebuffer paths now use Minecraft 1.12.2 `OpenGlHelper` framebuffer dispatch instead of direct GL30 FBO calls. Local MCP/bytecode evidence shows `OpenGlHelper` selects BASE, ARB, or EXT framebuffer backends; the old direct calls lined up with the logged `Shadow framebuffer incomplete: 36055` missing-attachment status. Active copy paths bind through unified `OpenGlHelper.GL_FRAMEBUFFER` instead of split read/draw targets. Unit coverage lives in `FramebufferCompatibilityTest`; the latest Complementary smoke no longer reports the old shadow FBO or repeated `1282` errors, but shadow visual parity is still unproven.
- Shadow compute program compilation now fails fast with `ProgramLoadException` instead of logging and continuing without the broken program. The root shadow raster fail-fast boundary now lives in `ShaderWorldRenderingPipeline.resolveProgram(...)` because the raster program is owned by generic program resolution, not by `ShadowRenderer`. Source coverage lives in `ShadowRendererSourceTest` and `ShaderWorldRenderingPipelineSourceTest`. Runtime shadow output is still not proven.
- Generic world shader program failure handling now follows the same source-backed boundary more closely. `ShaderWorldRenderingPipeline.resolveProgram(...)` throws when a selected source has no exact compiled `InputAvailability` program, and `ensureShadersCompiled()` rethrows first-frame compile failures. Because this 1.12 port compiles those programs during first world-frame setup, `PipelineManager.beginWorldRendering(...)` catches shader-pipeline failures there, destroys the failed shader pipeline, installs fixed-function fallback, and latches runtime fallback until `reloadShaderPack(...)` clears it. Source coverage lives in `ShaderWorldRenderingPipelineSourceTest` and `PipelineManagerSourceTest`; reload recovery and user-facing fallback behavior are not runtime-proven.
- Generic `ShaderLoader` is now scoped to shadow and gbuffer-style world sources. Do not re-add `prepare`, `deferred`, `composite`, or `compositeFinal` there; `CompositeRenderer` and `FinalPassRenderer` own postprocess compilation with the composite reserved sampler units, render-target bindings, custom bindings, noise, and center-depth path. Source coverage lives in `ShaderLoaderSourceTest`; live postprocess output is still unproven.
- Oculus mixins now load through the Forge 1.12 early coremod path. `OculusMixinLoader` implements `IFMLLoadingPlugin` and `IEarlyMixinLoader`, the jar manifest advertises it like Relictium's coremod, and `Oculus.onConstruction(...)` no longer queues mixins late. Fresh bounded `runClient` logs show `BlockStateAmbientOcclusionMixin` applies without the prior target-loaded-too-early crash, Forge successfully loads all 7 mods, and the later run enters a shader-enabled world. Unit coverage lives in `OculusMixinLoaderTest`; render-phase visual validation is still missing.
- Shader-pack loading now has a direct `loadFromShaderpacksDirectory(...)` entry point for tests/tools that should not depend on `Minecraft.getMinecraft().gameDir`, and the active no-pack sentinel is named `ShaderPack.internal()` instead of `placeholder()`. Directory root resolution follows the local 1.16.5 folder load path by requiring a direct `<pack>/shaders` child, while zip root resolution follows `Iris.loadExternalZipShaderpack(...)` for both root-level `shaders/` and nested `TopFolder/shaders` archives. Root-level `<pack>/shaders.properties` is ignored for directory packs because the reference constructs `ShaderPack` from `<pack>/shaders`. `ShaderPackLoaderComplementaryTest` loads the local Complementary Reimagined directory/zip, the local MakeUp UltraFast directory/zip, and a synthetic nested zip; it also rejects a directory with root-level metadata but no `shaders/` child and pins the root-level metadata ignore rule when `shaders/` exists. It proves dimension mappings, profiles, options/language, custom noise/custom texture data, image/SSBO metadata, item/block ID maps, disabled program gating, world-specific `ProgramSet` roots, nested language/texture/ID-map metadata, deferred program source availability after zip loading returns, and MakeUp runtime program source preparation across all vanilla dimension roots plus fallback base root. This is parser/resource/source-preparation evidence, not shader compile or in-world render evidence.
- Dimension routing now mirrors the local 1.16.5 loader boundary more closely. `dimension.properties` is preprocessed with environment defines only, before shader option values and optional feature defines exist, so do not make dimension mappings depend on shader options. The base `ProgramSet` root is derived from the parsed wildcard dimension mapping, not from blindly detecting a `world0` folder, and a declared folder is only considered a usable override after `ShaderPackSourceNames.findPresentSources(...)` finds a known shader program start there. Empty or metadata-only dimension folders intentionally fall back to the base set. The include/option graph starts from those same known root and usable-dimension program starts, then follows `#include` edges; it does not walk every `.vsh` / `.fsh` / `.gsh` / `.csh` / `.glsl` file as an independent root. Value tokens are intentionally raw like the reference: `*` is recorded both as wildcard and as `minecraft:*`, and an empty value token maps `minecraft:`. Synthetic `ShaderPackLoaderComplementaryTest` cases pin these behaviors.
- `ProgramSet` disabled-program matching now mirrors the local 1.16.5 source-provider rule for path-derived keys. Root program sets check keys such as `shadowcomp`, while world-folder program sets check keys such as `world0/shadowcomp`; the port no longer lets an unprefixed global key suppress world-specific raster or compute sources. Unit coverage lives in `ProgramSetTest`; runtime GUI reload and visual validation are still missing.
- Headless shader-pack parsing no longer crashes when LWJGL is unavailable: `StandardMacros` has a GL-string probe bypass, and capability probes in `OculusRenderSystem` can be disabled with `oculus.disableGlCapabilityProbes=true` for parser tests. Keep `MC_VERSION` at the target `11202` default; do not restore the older `11605` compatibility clamp. Do not set the capability-probe bypass in live render validation, because it intentionally makes `PER_BUFFER_BLENDING`, `CUSTOM_IMAGES`, `SSBO`, and compute capability checks report unavailable. `ImageLimits` can recover from a cached unavailable image-unit limit after a later positive probe; coverage lives in `ImageLimitsTest`.
- `ProgramImages` now matches the local 1.16.5 active-image fail-fast boundary. Missing image uniforms still no-op after the uniform lookup, but linked programs that declare image uniforms throw when `ImageLimits` reports zero image units or when declarations exceed the image-unit limit. `ImageBinding` also matches the local 1.16.5 image update call by always passing `layered=true` to `glBindImageTexture`; the old 1.12-only non-layered overload has been removed. Coverage lives in `ProgramImagesTest`; live image load/store output is still unverified.
- `CustomImageManager` now sizes the legacy fallback clear upload buffer with OpenGL packed-pixel semantics, resolves Complementary-style custom-image dimension tokens such as `COLORED_LIGHTING` through active option values before allocation, keeps 2D `image.*` directives depthless, allocates declared custom-image resources without pre-gating on `ImageLimits`, unregisters paired sampler aliases by owned binding identity during custom-image texture teardown before clearing its texture map, and cleans partial allocation/setup failures by deleting generated GL textures, destroying earlier images from that resize, and resetting cached dimensions. Custom image uniforms now use the default layered `ProgramImages` path for both 2D and 3D images while paired samplers still keep their 2D/3D texture target shape; unsupported or exhausted image units fail there only for active image uniforms. Unit coverage lives in `CustomImageManagerTest`; this is headless source/value/capability-boundary evidence only, not live image load/store or sampler-output proof.
- `ShaderStorageBufferManager` no longer marks requested `bufferObject.*` declarations initialized when SSBO support is temporarily unavailable. Coverage lives in `ShaderStorageBufferManagerTest`; live SSBO allocation and shader-visible contents remain unproven.
- `OculusRenderSystem` now owns the active clear/capability dispatch for center-depth OpenGL 3.2 output selection, custom-image `glClearTexImage` through OpenGL 4.4 or `GL_ARB_clear_texture`, and SSBO `glClearBufferData` through OpenGL 4.3 or `GL_ARB_clear_buffer_object`. `CustomImageManager`, `ShaderStorageBufferManager`, and `CenterDepthSampler` no longer do their own direct `GLContext.getCapabilities()` probes for those decisions; source coverage lives in `OculusRenderSystemCapabilityDispatchTest`.
- `separateAo=true` has vanilla block, vanilla fluid, and Relictium block/fluid paths, with focused Java tests.
- Main frustum culling, occlusion culling, back-face, rain depth, particle ordering, beacon beam depth, and several shader-pack directive paths are wired.
- Agent navigation docs now include `docs/legacy-shims-and-remnants.md`, which records modern-package compatibility shims, in-project `net.coderbot.iris` remnants, and non-primary mixin JSON resources so future agents do not mistake compile-only helpers for production parity.
- `LegacyShimUsageSourceTest` now source-guards that active `net.oculus` code does not import historical `net.coderbot.iris` runtime packages, inactive modern shells such as modern `Framebuffer` / `Matrix4f` / `VertexBuffer` stay out of active runtime source, and modern `com.mojang.blaze3d.vertex.*` use remains limited to `OculusVertexFormats`.
- Agent navigation docs now include `docs/agent-quickstart.md`, a first-page checklist for cold-start agents that records active paths, distracting paths, search recipes, subsystem starting points, verification commands, and documentation update ownership.
- Agent navigation docs now include `docs/source-flow-guide.md`, which traces pack selection/reload, metadata-to-runtime conversion, shader source preparation, render-loop mixins, postprocess, shadows, uniforms, terrain/Relictium, and resource lifetime. Use it when a future agent needs to understand how a feature moves through the system rather than only which package owns it.
- A current documentation audit is recorded in `docs/evidence-log.md`. The main agent entry points link the navigation docs, every `docs/` file has a `Last updated:` marker, and the latest Relictium slice replaced the null `SodiumTerrainPipeline` with a source-backed terrain bridge, explicit Relictium pass-to-phase scoping, and Relictium chunk-program overrides. Treat this as a compiled bridge that still needs runtime validation, not as full terrain parity.
- A fresh Complementary Xvfb shader-world smoke after the source, framebuffer, and texture-unit cleanup fixes is recorded in `docs/evidence-log.md`. Treat it as evidence for world entry, shader pack compilation, shadow initialization, Relictium override selection, and short-run shutdown. It does not close visual parity, screenshot coverage, reload behavior, PBR resource-pack proof, non-clear shadow depth output, or cross-pack validation.
- Sun and moon directives now have 1.12 vanilla sky draw suppression in `WorldRendererMixin`. The hooks match the local 1.16.5 empty-buffer strategy by clearing the active sky `BufferBuilder` immediately before the sliced `Tessellator.draw()` between `SUN_TEXTURES` and `MOON_PHASES_TEXTURES` for sun, and between `MOON_PHASES_TEXTURES` and `WorldClient.getStarBrightness(F)` for moon. Compile, mixin-loader, and refmap checks passed; in-client visual validation is still missing.
- `sunPathRotation` now reaches every currently identified 1.16.5 consumer path: `CelestialUniforms`, `ShadowUniforms`, and the live vanilla sky transform. `WorldRendererMixin` injects before the second `WorldClient.getCelestialAngle(F)F` call in `RenderGlobal.renderSky(FI)V`, after vanilla's `-90` Y-axis setup and before the celestial-angle X rotation, then applies `GlStateManager.rotate(rotation, 0, 0, 1)`. Compile, mixin-loader, refmap, and startup smoke checks passed; visual validation with a non-zero-rotation shader pack is still missing.
- `SodiumTerrainPipeline` is no longer null. It now selects terrain/translucent/shadow sources, applies `SodiumTerrainShaderTransformer`, prepares the sources through `ShaderSourcePreparer` without `InputAvailability`, and exposes them to a Relictium override bridge. That source-prep scope is intentional: the local 1.16.5 `Patch.SODIUM_TERRAIN` path does not run `AttributeTransformer`, and `SodiumTerrainPipelineTest` guards against accidental `iris_TextureMatrix[8]` / `iris_ONE_OVER_256` injection in `_sodium` output. `RelictiumChunkRenderManagerMixin` passes `BlockRenderPass` into the backend, maps render/end exceptions back through Oculus terrain-scope cleanup before rethrowing, `OculusTerrainPass` maps that pass to the solid/translucent terrain override and the matching `WorldRenderingPhase`, `RelictiumChunkRenderShaderBackendMixin` scopes Relictium terrain rendering with `beginSodiumTerrainRendering()` / `endSodiumTerrainRendering()` and restores the previous phase, `OculusRelictiumProgramLinker` links optional geometry shaders through Oculus' GL path, and `OculusRelictiumChunkProgram` tolerates missing superclass uniforms like the 1.16.5 Iris chunk program. The focused source tests now include real Complementary zip Sodium terrain source generation. Those guards caught the old catastrophic declaration-regex backtracking, reversed generated declaration ordering, and declarations hidden inside Complementary's leading block comment; `SodiumTerrainShaderTransformer` now uses ordered declaration batches, whole-block-comment preamble scanning, direct no-dedup generated declaration injection like the 1.16.5 reference, token scanners for validation/replacement paths, and the reference unexpected-stage guard that throws on compute inputs. `SodiumTerrainPipelineGlCompileTest` is opt-in with `-Doculus.tests.sodiumTerrainGl=true`; default runs only prove the harness compiles and skips before LWJGL, while explicit runs can compile/link the generated Complementary solid, translucent, and shadow `_sodium` programs in a guarded local Pbuffer context. `OculusTerrainPassTest` pins pass/phase mapping and `RelictiumTerrainBridgeBytecodeTest` pins the installed Relictium bytecode descriptors and injection points used by these remap=false mixins, including the backend `render(...)` and later `end()` calls required for terrain scope cleanup. `LevelRendererMixin` now invokes shadow rendering from the existing setup-terrain redirect after a diagnostic run proved the old separate setup-terrain injection initialized the shadow renderer without requesting shadow terrain layers. The 1.16.5-style Relictium shadow visibility graph swap is implemented through `OculusRelictiumSwappableChunkRenderManager`, `RelictiumChunkRenderManagerMixin`, and `RelictiumSodiumWorldRendererShadowMixin`, and the bytecode test pins the manager/renderer fields and call sites it depends on. `ShadowRendererBytecodeTest` separately pins the vanilla 1.12 terrain graph dirty mark before shadow `setupTerrain(...)`, which is the local equivalent of Iris' pre-shadow `LevelRenderer.needsUpdate()`, the shadow terrain layer order from the 1.16.5 renderer, and depth-state restoration after shadow work. `ShadowMapBytecodeTest` pins the no-translucents depth-copy cache-aware helper boundary and shadow mipmap cache-aware texture-unit isolation plus default-active-unit restoration. `OculusRuntimeValidation` provides an opt-in unattended runClient path plus validation-only layer/lookup/active-program/shadow-depth telemetry. The `2026-05-16 19:26` run proved in-client Relictium override creation for shadow, solid, and translucent terrain, the `2026-05-16 19:35` run proved active-program override selection for solid, cutout-mipped, cutout, and translucent main terrain, the `2026-05-16 19:49` run proved shadow terrain layer requests plus `oculus:relictium-terrain-shadow` active-program selection for all shadow terrain layers, and the later `2026-05-17` Xvfb smoke reconfirmed those selections while shadow visible chunks rose above zero. Visual parity, runtime attribute values, sampler/image bindings, non-clear shadow depth output, translucent behavior, reloads, and broader GL state cleanup remain open.
- Relictium/Oculus terrain attribute locations now match the local 1.16.5 Sodium bridge for chunk programs: base Relictium attributes occupy `0..4`, then Oculus binds `iris_Normal=5`, `at_tangent=6`, `mc_midTexCoord=7`, `mc_Entity=8`, and `at_midBlock=9`. Keep legacy fixed-function `ProgramCreator` locations `11..14` separate from this Relictium path. `OculusChunkShaderBindingPointsTest` pins the constants, and the default `SodiumTerrainPipelineGlCompileTest` harness was updated to bind those locations before its opt-in GL link path.
- Earlier `2026-05-17` client reruns on bare `DISPLAY=:0` blocked before Minecraft startup in `sun.awt.X11GraphicsEnvironment.initDisplay`. A later `xvfb-run` path did produce a short in-world Complementary smoke, but it is still not screenshot, long-session, reload, PBR upload, or non-clear shadow-depth proof.

Treat each item as implemented but runtime-unverified unless another doc records specific client evidence.

## Immediate High-Value Follow-Ups

The most valuable next work is visual validation and deeper runtime parity with Complementary Reimagined:

- Start with `docs/runtime-diagnosis-2026-05-17.md` before another runtime/debugging slice. The manual `runClient` log showed MakeUp `9.3e` falling back because `prepare.fsh` hit a duplicate `shifted_r_dither` definition; that exact source-prep blocker is now fixed by a duplicate-identical function cleanup, but live GL compile has not been rerun. The same log showed Complementary reaching shader pipeline construction and then appearing frozen because shader source transforms and availability-variant compilation were still running on the main thread when interrupted; the base-compile skip and timing logs need fresh runtime measurement.
- Use a known-good LWJGL 2 display path, such as the recently working Xvfb route, for the next client validation. Treat the existing 180-tick smoke as log evidence only, not screenshot or visual parity evidence.
- Re-run a 1.12.2 client from `run/`, enter a world, and confirm the old `gbuffers_line`, shadow framebuffer, and `1282` errors stay gone after any warning fixes.
- Specifically for Relictium terrain, use the unattended validation command or enter `New World` with `ComplementaryReimagined_r5.6.1.zip` selected, then go beyond the now-proven override creation, main/shadow active-program selection, graph-swap bytecode guards, no-translucents depth-copy cache-aware helper guard, cache-aware shadow mipmap texture-unit guard, and shadow depth-state guard: inspect rendered solid terrain, translucent water/terrain, non-clear shadow depth output, final shadow output, attribute values, sampler/image bindings, reload behavior, and broader GL state cleanup.
- Capture screenshots for overworld, nether, end, water, weather, translucent blocks, entities, block entities, particles, hand rendering, beacon beam, and shadows.
- Add a camera-uniform validation scene with movement, third-person/front-view changes, smooth camera if available, and shader/debug output for `cameraPosition`, `previousCameraPosition`, split camera vectors, and gbuffer matrices. The source path now uses the post-vanilla-camera setup boundary, but it is not visually proven.
- Toggle shader options that affect `program.*.enabled`, `supportsColorCorrection`, shadow settings, and `separateAo`.
- Add underwater, normal HUD/vignette, cloud mode, non-zero `sunPathRotation`, and day/night sky scenes to the next client visual pass because `underwaterOverlay`, `vignette`, `clouds`, `sun`, `moon`, and `sunPathRotation` are now wired but not visually proven.
- Add a Complementary block-layer scene with glass, glass panes, and beacons, because `layer.translucent` is now wired through Forge's `canRenderInLayer` hook but has not been proven in-client.
- Add screenshots/debug observations for solid, translucent, and shadow terrain to the next Relictium validation pass because in-client override creation, main/shadow terrain active-program selection, and shadow graph-swap hook execution are proven or guarded, but visual and state parity are not.
- Note that `run/config/oculus.properties` currently selects `ComplementaryReimagined_r5.6.1.zip` after the latest GUI/reload smoke. Change it deliberately if a future test needs the extracted directory pack.

The most promising next code audits are now deeper shadow runtime parity and post-process pass ordering:

- For shadows, compare runtime projection/snap stability, culling, no-translucents depth copy timing, mipmap generation, shadow color writes, and shadow image barriers against the 1.16.5 shadow pipeline.
- For shader source transforms, validate the new availability variants, `iris_entityColor` uploads, and entity `entityColor` passthrough in-client, especially hurt/custom overlay draws with and without geometry shaders. Keep the 1.12 rule that `iris_overlay` is not the color source by itself; the proven color source is fixed-function texture-env state captured from `brightnessBuffer`, not the white brightness texture pixels.
- For post-processing, compare `ProgramSet`, `ShaderWorldRenderingPipeline`, `CompositeRenderer`, `FinalPassRenderer`, and `BufferFlipper` against `DeferredWorldRenderingPipeline`.
- For shader source transforms, keep the explicit numeric source `#version` requirement on raster pack sources before define injection. For Sodium terrain specifically, also keep the pre-transform `iris_*` / `irisMain` guard and `_sodium` preparation out of the `InputAvailability` / `AttributeTransformer` branch. These mirror the local 1.16.5 `TransformPatcher` paths; compute sources are a deliberate exception because the inspected compute builders compile them directly.

Do not implement these from memory. Compare the exact 1.16.5 classes and the current 1.12.2 builders/renderers first.

## Current World-Info Audit Notes

These notes are source-backed context, not runtime validation. The detailed evidence trail is in `docs/evidence-log.md`.

- The local 1.16.5 `IrisExclusiveUniforms.WorldInfoUniforms` call sites show `cloudHeight` comes from `level.effects().getCloudHeight()`, `heightLimit` from `level.getMaxBuildHeight()`, `logicalHeightLimit` from `level.dimensionType().logicalHeight()`, and `ambientLight` from the dimension type ambient-light accessor.
- Local 1.16.5 bytecode shows Overworld cloud height `128.0F`, Nether and End cloud height `NaN`, default Nether logical height `128`, and default Nether ambient light `0.1F`.
- The active 1.12.2 path now maps vanilla Nether/End cloud height to `NaN`, vanilla Nether logical height to `128`, and vanilla Nether ambient light to `0.1F`; other providers still use the closest existing 1.12 provider values.
- Runtime validation is still required in vanilla dimensions and at least one custom dimension before calling world-info parity complete.

## Current Postprocess Audit Notes

These notes are source-backed context for the next agent. They are not runtime validation.

- `BufferFlipper` currently matches the 1.16.5 ping-pong model at the conceptual level: non-flipped buffers are read from main and written to alternate; flipped buffers are read from alternate and written to main.
- `CompositeRenderer` applies explicit pre-flips before pass creation and does not add those buffers to `flippedAtLeastOnce`, matching the 1.16.5 note that `deferred_pre` / `composite_pre` flips do not count as normal stage writes.
- `CompositeRenderer` snapshots `stageReadsFromAlt` before creating each pass, flips draw buffers after creating that pass, and then applies explicit `flip.*=true` entries. This matches the reference ordering broadly, but still needs in-client proof with packs that depend on ping-pong history.
- `CompositeRenderer` relies on `RenderTargets.createColorFramebuffer(stageReadsFromAlt, drawBuffers)` to attach pass targets and set draw buffers once. The render loop now mirrors the local 1.16.5 path by binding the pass framebuffer and using the program without reissuing `glDrawBuffers(...)` per pass.
- `CompositeRenderer` now requires every draw buffer referenced by a pass during creation and resize, and requires every declared mipmapped buffer during render. The 1.16.5 reference dereferences these targets directly; the 1.12 port now throws explicit `IllegalStateException`s instead of silently skipping them or keeping stale pass dimensions. It also leaves explicitly empty draw-buffer arrays empty so `RenderTargets.createColorFramebuffer(...)` can fail like the reference, rather than converting them back to `{0}`.
- `CompositeRenderer` no longer runs a second local source patch after `ShaderSourcePreparer.prepareProgram(...)`; prepared vertex, geometry, and fragment sources are passed directly to `ProgramBuilder`.
- `CompositeRenderer` and `FinalPassRenderer` now clear the framebuffer binding after constructor framebuffer creation. The local 1.16.5 constructors clear `GL_READ_FRAMEBUFFER`; the 1.12 port maps this to `OpenGlHelper.GL_FRAMEBUFFER` because the current 1.12 `GlFramebuffer` helper collapses read/draw framebuffer binding for compatibility with legacy FBO modes.
- Composite compute dispatch now uses the active Minecraft main framebuffer dimensions when available, matching the 1.16.5 renderer's `main.width` / `main.height` dispatch inputs. It falls back to `RenderTargets` dimensions only outside a live client framebuffer.
- Composite cleanup now rebinds `Minecraft.getFramebuffer().bindFramebuffer(true)` when available, matching the 1.16.5 `mainRenderTarget.bindWrite(true)` boundary instead of assuming raw framebuffer `0` is the Minecraft render target.
- `CenterDepthSampler` cleanup now follows the same boundary after the 1x1 smoothing copy: restore the previous 2D texture binding, then rebind the Minecraft main framebuffer with `bindFramebuffer(true)`. It deliberately does not restore the previously-bound framebuffer/viewport and does not call `Program.unbind()` inside the helper, matching the inspected 1.16.5 sampler.
- `FullScreenQuadRenderer` now matches the 1.16.5 helper contract more closely: it owns projection/model-view setup, resets fixed-function color to opaque white before drawing, and owns depth-test toggling, but callers own depth-mask, blend, alpha, and framebuffer state. The final pass no longer disables depth outside that helper, so the baseline copy path leaves depth-test state alone like the reference.
- `FinalPassRenderer` creates final compute programs only when a valid final raster source exists. This matches the 1.16.5 reference, where `pack.getFinalCompute()` is consumed inside the `pack.getCompositeFinal().map(...)` branch.
- Present composite/final raster and compute shader creation failures are intentionally fail-fast. Do not restore the old logged-and-skipped behavior; it hid real shader-pack errors and diverged from 1.16.5.
- `shadowcomp` sources remain discoverable through `ProgramSet`, but generic `ShaderLoader` no longer compiles them and `ShaderWorldRenderingPipeline` no longer creates or runs a `shadowCompositeRenderer`. This follows the inspected local 1.16.5 `DeferredWorldRenderingPipeline`, which discovers `shadowcomp` sources and recognizes its texture stage but does not render them. Complementary has profile-gated `shadowcomp.csh` for colored lighting; executable support for that would be a deliberate target-pack-support divergence, not source parity.
- 1.16.5 final pass ordering is: enter the fullscreen quad helper scope when a final raster pass exists, dispatch final computes, always issue the post-compute memory barrier, setup requested mipmaps, render final quad or baseline copy, reset render-target mipmap filters, run swap-copy passes using framebuffer `bind()`, then clear uniforms/samplers/program and unbind sampler texture units.
- Current 1.12.2 `FinalPassRenderer` follows that broad reset-before-swap ordering, starts fullscreen scope before final compute dispatch, ignores final draw-buffer and viewport-scale directives like the local 1.16.5 final renderer, uses `swapPass.from.bind()` rather than `bindAsReadBuffer()`, resets active texture unit 0 before mipmap/swap work, always runs the final-pass post-compute barrier on the final raster path, does not call `Program.unbind()` between the final quad and mipmap/swap cleanup, does not clear the active GL program early in the baseline-copy helper, clears constructor framebuffer binding like the reference, does not add a one-off texture-0 unbind after the swap-copy loop, and unbinds all reported sampler units at final cleanup like the 1.16.5 renderer. Its `colorHolder` attachment check now mirrors the local 1.16.5 texture-id plus color-buffer-version guard through the 1.12 `FramebufferVersionMixin` / `MinecraftFramebufferExt` bridge. Final raster and compute programs rely on `ProgramBuilder` sampler bindings instead of legacy manual texture-unit setup. Runtime validation is still required before calling final output parity complete.
- `FinalPassRenderer` now requires every final swap target during swap-pass creation and resize, rejects null flipped-buffer entries, requires every final mipmapped buffer during render, and resets each configured render target through a `resetRenderTarget(...)` helper that unbinds texture 2D after filter edits like the reference helper.
- `FinalPassRenderer` also passes prepared vertex, geometry, and fragment sources directly to `ProgramBuilder`; postprocess source transforms belong in `ShaderSourcePreparer` / `ShaderCompatibilityPatcher`, not in renderer-local text replacements.
- Postprocess mipmap generation/filter edits and the final baseline copy route through `OculusRenderSystem.generateMipmaps(...)`, `texParameteri(...)`, and `copyTexSubImage2D(...)`, with 2D texture binds going through `GlStateManager`. This mirrors the local 1.16.5 `IrisRenderSystem` non-DSA fallback; `PostprocessRendererSourceTest#renderTargetMipmapsRouteThroughReferenceStyleRenderSystemWrappers` guards against reintroducing raw postprocess `glGenerateMipmap` / `glTexParameteri` calls.
- Pipeline destroy now clears cached g-buffer framebuffer wrappers before destroying `RenderTargets`, matching the ownership model used during resize/rebuild and avoiding stale wrappers around render-target-owned GL framebuffer handles.
- Composite pass framebuffers are now destroyed through `RenderTargets.destroyFramebuffer(...)`, matching their creation ownership and the final-pass swap/baseline cleanup pattern.

## Current Render Target Lifecycle Audit Notes

These notes came from checking the active 1.12.2 source against `../Oculus-1.16.5` on 2026-05-16.

- `ProgramSet` constructs `PackDirectives` with `PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS`, matching the 1.16.5 constructor path. In this port, that baseline set covers `0..IrisLimits.MAX_COLOR_BUFFERS - 1`, so normal shader-pack loading gives `RenderTargets` a default `colortex0` even if the pack does not declare explicit render-target directives.
- `RenderTargets.createEmptyFramebuffer()` intentionally requires `colortex0`, matching the 1.16.5 OpenGL-pre-3.0 compatibility path that attaches a color texture while disabling draw buffers. Do not treat the `colortex0` requirement as dead code.
- The 1.16.5 `RenderTargets.resizeIfNeeded(...)` updates `currentDepthTexture` and reattaches owned framebuffers when Minecraft recreates its depth texture. The active 1.12.2 path instead calls `FramebufferManager.resizeIfNeeded(...)` and then rebuilds `RenderTargets` in the same `ShaderWorldRenderingPipeline.ensureFramebufferDimensionsUpToDate()` call, so the new `RenderTargets` instance captures the new depth texture immediately after resize.
- Current `RenderTargets.resize(...)` is not equivalent to the 1.16.5 in-place resize path because it does not replace `currentDepthTexture` or reattach owned framebuffers. If a future agent makes it the primary resize path, port the 1.16.5 depth-version/reattachment behavior first.
- `RenderTargets.copyDepth(...)` has a deliberate binding split. Dirty/first allocation binds the destination depth texture through `GlStateManager` and leaves it bound after `OculusRenderSystem.copyTexImage2D(...)`, matching the local 1.16.5 `RenderTargets` branch. Later refreshes use `OculusRenderSystem.copyTexSubImage2D(...)` to save and restore the previous `GL_TEXTURE_BINDING_2D`, matching the local 1.16.5 `DepthCopyStrategy` non-DSA helper. `RenderTargetsSourceTest` guards this; do not collapse it back to raw binds or unconditional `glBindTexture(..., 0)`.
- `GlFramebuffer.addDepthAttachment(...)` now mirrors the local 1.16.5 helper's combined depth-stencil attachment selection by reading `TextureInfoCache`, mapping through `DepthBufferFormat`, and choosing `GL_DEPTH_STENCIL_ATTACHMENT` only for combined formats. Keep the actual FBO call on `OpenGlHelper.glFramebufferTexture2D(...)`; the 1.12 backend-dispatch rule still applies.
- `ShadowRenderer` incomplete-framebuffer construction now unbinds framebuffer `0` and deletes the newly-created shadow FBO before throwing. Keep this cleanup with the fail-fast behavior so a setup failure does not leave an invalid shadow framebuffer bound.
- Detailed notes now live in `docs/render-target-lifecycle.md`.

## Current Texture-Size Uniform Note

`atlasSize` and `gtextureSize` read texture-unit-0 dimensions through `GameplayUniforms.readTextureUnitZeroSize(...)` and `TextureInfoCache`. Keep the active-texture switch on `OpenGlHelper.setActiveTexture(...)`, not raw `GL13.glActiveTexture(...)`. If `TextureInfoCache` misses cached upload metadata, it may bind through `GlStateManager` to query live texture-level parameters, and raw active-unit switching would leave the 1.12 `OpenGlHelper` / `GlStateManager` active-texture cache naming the wrong unit during that bind. The `GlStateManager.glTexImage2D` bridge must ignore non-2D targets before asking GL for the current 2D binding, otherwise custom 3D/image uploads could pollute 2D texture-size metadata. `GameplayUniformsTest#textureSizeUniformsUseMinecraftActiveTextureStateBoundary` and `TextureInfoCacheTest#glStateManagerUploadBridgeIgnoresNon2DTargetsWithoutQueryingGlState` guard these source boundaries. Runtime texture-size uniform timing is still unverified.

## Worktree Safety

The worktree is intentionally dirty. Some modified files are the current port, some logs are runtime output, and some docs are newly added. Do not run broad cleanup, reset, checkout, or formatting commands.

Before editing a file:

- Read the current file.
- Check whether the change belongs to the active slice.
- Avoid reverting unrelated edits.
- Update the docs that own the behavior.
- Update `docs/custom-uniform-smooth-semantics.md` before changing `smooth([id], ...)` state ownership.
- Update `docs/legacy-shims-and-remnants.md` if you activate, remove, or replace modern-package compatibility shims or old mixin configs.
- Run focused `git diff --check -- <files>` for touched files.

## Handoff Format

When ending a slice, leave enough context for the next agent to continue without archaeology:

- Files changed.
- 1.16.5 reference files inspected.
- Complementary files inspected, if applicable.
- Behavior implemented.
- Tests or commands run.
- Runtime validation status.
- Remaining gap or exact blocker.

Never write "full port complete" unless the completion bar in `docs/parity-roadmap.md` has actually been met.

## 2026-05-17 - Agent 3 Modern Wood Material ID Slice

Slice:
Modern vanilla wood-family material IDs from Complementary and MakeUp now resolve to 1.12 shared wood state predicates where those blocks exist in vanilla 1.12.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- No new Java source behavior was ported for this slice; the decision is the 1.12 material-ID compatibility layer needed for modern shaderpack `block.properties` names.

Shader-pack/runtime evidence:
- Complementary `block.properties` lists modern wood names next to 1.12 aliases in rows such as `block.10156`, `block.10159`, `block.10160`, `block.10196`, `block.10199`, and `block.10200`.
- Complementary and MakeUp both list modern leaf names such as `oak_leaves`, `spruce_leaves`, `birch_leaves`, `jungle_leaves`, `acacia_leaves`, and `dark_oak_leaves`.
- 1.12 bytecode confirms `variant` on old/new logs, old/new leaves, and wooden slabs, with `BlockPlanks$EnumType` names `oak`, `spruce`, `birch`, `jungle`, `acacia`, and `dark_oak`.

Implemented behavior:
- `<wood>_planks` -> `planks:variant=<wood>`.
- `<wood>_log` -> `log:variant=<wood>` for oak/spruce/birch/jungle and `log2:variant=<wood>` for acacia/dark_oak.
- `<wood>_wood` -> the matching log/log2 variant with default `axis=none`, preserving any explicit `axis` predicate.
- `<wood>_leaves` -> `leaves:variant=<wood>` for oak/spruce/birch/jungle and `leaves2:variant=<wood>` for acacia/dark_oak.
- `<wood>_slab` -> `wooden_slab:variant=<wood>`.
- `stripped_<wood>_log` / `stripped_<wood>_wood` intentionally remain unmapped because Complementary puts stripped aliases in plank rows before later real-log rows; mapping them to 1.12 logs would preempt real log material IDs.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest`
- `test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace`
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest`
- `compileJava`
- `git diff --check -- src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `git diff --no-index --check -- /dev/null src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `rg -n '^(<<<<<<<|=======|>>>>>>>)|[[:blank:]]$' src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run for this slice.

Remaining gap:
- Prove live Relictium terrain `blockId` output and visible material branches for wood-family aliases, especially MakeUp modern leaves and Complementary wood/log/slab rows.

Next exact check:
- Continue code-side material-ID audit for target-pack entries that still reference modern names with real 1.12 equivalents. Avoid lossy aliases where 1.12 has no state/property that can distinguish the modern variants.

## 2026-05-17 - Agent 3 Modern Stone Material ID Slice

Slice:
Modern stone, stone-brick, and infested block material IDs now resolve to 1.12 shared state predicates, and broad legacy containers no longer preempt split variants when split entries exist.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- No new Java source behavior was ported for this slice; this is the 1.12 material-ID compatibility layer needed for modern shaderpack `block.properties` names.

Shader-pack/runtime evidence:
- Complementary `block.properties` lists modern names such as `granite`, `diorite`, `andesite`, polished variants, `stone_bricks`, stone-brick variants, and `infested_*` variants.
- 1.12 bytecode confirms the `variant` property values on `BlockStone`, `BlockStoneBrick`, and `BlockSilverfish`.

Implemented behavior:
- Granite/diorite/andesite and polished variants map to `stone:variant=*`.
- Stone-brick variants map to `stonebrick:variant=*`.
- Infested variants map to `monster_egg:variant=*`.
- Bare `stone`, `stonebrick`, and `monster_egg` entries are skipped only when split entries exist for that family, avoiding broad first-match material assignment while keeping broad-only packs usable.
- Modern granite/diorite/andesite walls, slabs, and stairs remain unmapped because vanilla 1.12 has no distinct states for them.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest`
- `cleanTest` after Gradle XML generation hit `EOFException` / Kryo buffer underflow from a stale generated test output store
- `test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace`
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest`
- `compileJava`
- `git diff --check -- src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java docs/uniform-gap-analysis.md`
- `git diff --no-index --check -- /dev/null src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `rg -n '^(<<<<<<<|=======|>>>>>>>)|[[:blank:]]$' src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java docs/uniform-gap-analysis.md docs/relictium-integration.md docs/directive-support.md docs/source-flow-guide.md docs/backport-status.md docs/parity-roadmap.md docs/evidence-log.md docs/agent-handoff.md`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run for this slice.

Remaining gap:
- Prove live Relictium terrain `blockId` output and visible material branches for stone variants, stone-brick variants, and infested variants.

Next exact check:
- Continue code-side material-ID audit for target-pack modern names that have exact 1.12 state/property equivalents, especially shared-state families where broad legacy entries can preempt specific variants.

## 2026-05-17 - Agent 3 Exact Modern Material Alias Slice

Slice:
Exact modern target-pack block names for petrified oak slabs, nether quartz ore, and magma blocks now resolve to bytecode-confirmed 1.12 registry/state equivalents.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- Complementary `block.properties` rows contain `petrified_oak_slab`, `nether_quartz_ore`, and `magma_block`.
- MakeUp 9.3e's modern branch contains `magma_block`.
- 1.12 bytecode confirms `Blocks.MAGMA`, `Blocks.QUARTZ_ORE`, and `BlockStoneSlab$EnumType.WOOD` with state name `wood`.

Implemented behavior:
- `petrified_oak_slab` -> `stone_slab:variant=wood`.
- `nether_quartz_ore` -> `quartz_ore`.
- `magma_block` -> `magma`.
- No broad or lossy modern aliases were added for blocks vanilla 1.12 cannot represent distinctly.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --stacktrace`
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace`
- `compileJava --stacktrace`
- `git diff --check -- src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java docs/uniform-gap-analysis.md`
- `git diff --no-index --check -- /dev/null src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `rg -n '^(<<<<<<<|=======|>>>>>>>)|[[:blank:]]$' src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java docs/uniform-gap-analysis.md docs/relictium-integration.md docs/directive-support.md docs/source-flow-guide.md docs/backport-status.md docs/parity-roadmap.md docs/evidence-log.md docs/agent-handoff.md`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run for this slice.

Remaining gap:
- Prove live Relictium terrain `blockId` output and visible material branches for petrified oak slabs, nether quartz ore, and magma blocks.

Next exact check:
- Continue code-side material-ID audit only for target-pack modern names with exact 1.12 registry/state equivalents. Avoid aliases for modern blocks where 1.12 has no distinguishable block state.

## 2026-05-17 - Agent 3 PBR Holder Delete Failure Cleanup

Slice:
PBR holder cleanup now keeps closing later owned normal/specular companion textures and drops holder caches when one companion texture throws during deletion.

Active files:
- `src/main/java/net/oculus/texture/pbr/PBRTextureManager.java`
- `src/test/java/net/oculus/texture/pbr/PBRTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/texture/pbr/PBRTextureManager.java`

Shader-pack/runtime evidence:
- This is a lifecycle hardening slice for PBR companion resources reached by simple, atlas, and custom resource `_n` / `_s` indirection. No new shader-pack file was inspected for this specific cleanup path.

Implemented behavior:
- `PBRTextureManager.clear()` clears `holders` and `atlasHolders` from a `finally` block.
- Owned PBR companion deletion now catches `RuntimeException` from `AbstractTexture.deleteGlTexture()`, logs it, and continues with later companion textures.
- Default normal/specular textures are still skipped, and duplicate normal/specular object references still delete only once.
- `PBRTextureManagerTest#clearContinuesAfterOwnedPbrTextureDeleteFailure` injects a throwing owned texture and proves the later companion is still deleted and the holder cache is cleared.

Verification:
- `test --tests net.oculus.texture.pbr.PBRTextureManagerTest --stacktrace`
- `test --tests 'net.oculus.texture.pbr.*' --tests 'net.oculus.texture.pbr.loader.*' --tests 'net.oculus.texture.format.*' --tests net.oculus.pipeline.texture.CustomTextureManagerTest --stacktrace`
- `compileJava --stacktrace`
- `git diff --check -- src/main/java/net/oculus/texture/pbr/PBRTextureManager.java docs/uniform-gap-analysis.md`
- `git diff --no-index --check -- /dev/null <untracked touched file>`
- `rg -n '^(<<<<<<<|=======|>>>>>>>)|[[:blank:]]$' src/main/java/net/oculus/texture/pbr/PBRTextureManager.java src/test/java/net/oculus/texture/pbr/PBRTextureManagerTest.java docs/uniform-gap-analysis.md docs/relictium-integration.md docs/directive-support.md docs/backport-status.md docs/parity-roadmap.md docs/evidence-log.md docs/agent-handoff.md`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run for this slice.

Remaining gap:
- Prove live PBR sampler output, resource-pack `_n` / `_s` output, texture-format interaction after reload, and Relictium terrain sampler output in-client.

Next exact check:
- Continue code-side PBR/custom-resource audit around live holder refresh and custom resource reload behavior, then move to Relictium terrain attribute/material proof gaps that still lack client validation.

## 2026-05-17 - Agent 3 Custom Texture/Image Delete Failure Cleanup

Slice:
Custom texture and custom image manager teardown now clears manager-local reload state even if one owned GL texture delete throws.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/main/java/net/oculus/pipeline/texture/CustomImageManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java`
- `src/test/java/net/oculus/pipeline/texture/CustomImageManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/pipeline/CustomTextureManager.java`

Shader-pack/runtime evidence:
- This is a manager-owned cleanup hardening slice for shader-pack PNG custom textures, generated fallback noise, and `image.*` custom image textures. No Minecraft runtime was run.

Implemented behavior:
- `CustomTextureManager.destroy()` now always clears `ownedTextureIds`, `stageBindings`, `noiseBinding`, and `initialized` from a `finally` block.
- `CustomTextureManager` catches per-texture `GL11.glDeleteTextures(...)` runtime failures and continues deleting later owned custom/noise texture IDs.
- `CustomImageManager.destroyTextures()` now always clears its texture map from a `finally` block.
- `CustomImageManager` catches per-texture `GL11.glDeleteTextures(...)` runtime failures and continues deleting later custom image texture IDs.
- Focused source tests guard the new cleanup and catch boundaries.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.pipeline.texture.CustomImageManagerTest --stacktrace`
- `compileJava --stacktrace`
- `git diff --check -- docs/uniform-gap-analysis.md`
- `git diff --no-index --check -- /dev/null <untracked touched file>`
- `rg -n '^(<<<<<<<|=======|>>>>>>>)|[[:blank:]]$' src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java src/main/java/net/oculus/pipeline/texture/CustomImageManager.java src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java src/test/java/net/oculus/pipeline/texture/CustomImageManagerTest.java docs/uniform-gap-analysis.md docs/relictium-integration.md docs/directive-support.md docs/backport-status.md docs/parity-roadmap.md docs/evidence-log.md docs/agent-handoff.md`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run for this slice.

Remaining gap:
- Prove live custom texture sampler output, custom image writes/sampler output, resource reload behavior, driver deletion behavior, and Relictium wrapped-program sampler/image output in-client.

Next exact check:
- Continue code-side audit of custom resource/PBR reload refresh, then close any remaining source/test gaps around Relictium terrain attributes and config reload state before runtime validation.

## 2026-05-17 - Agent 3 GUI Invalid Stored Pack Recovery

Slice:
Shader-pack GUI preload recovery now clears invalid stored selected packs for broad loader/runtime failures, not only missing-pack `IOException`.

Active files:
- `src/main/java/net/oculus/gui/ShaderPackScreen.java`
- `src/test/java/net/oculus/gui/ShaderPackScreenSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/Iris.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/config/IrisConfig.java`

Shader-pack/runtime evidence:
- No Minecraft runtime was run. This is source-level recovery behavior for malformed or missing stored selected packs.

Implemented behavior:
- `ShaderPackScreen.preloadSelectedPack(...)` now catches `Exception` from `ShaderPackLoader.load(...)`.
- The GUI recovery path logs the failure, displays `Missing or invalid shader pack: <pack>`, clears that pack's overrides, clears `selectedPackName`, persists the cleanup, and falls back to the active/internal pack.
- Public startup/reload fallback in `ShaderPackReloader` intentionally still does not rewrite the saved pack after a load failure, matching the inspected 1.16.5 load boundary.
- `ShaderPackScreenSourceTest#invalidStoredSelectionClearsOverridesAndPersistsRecovery` guards the cleanup path.

Verification:
- `test --tests net.oculus.gui.ShaderPackScreenSourceTest --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.config.OculusConfigTest --tests net.oculus.client.OculusClientEventsSourceTest --stacktrace`
- `compileJava --stacktrace`
- `git diff --check -- src/main/java/net/oculus/gui/ShaderPackScreen.java docs/uniform-gap-analysis.md`
- `git diff --no-index --check -- /dev/null <untracked touched file>`
- `rg -n '^(<<<<<<<|=======|>>>>>>>)|[[:blank:]]$' src/main/java/net/oculus/gui/ShaderPackScreen.java src/test/java/net/oculus/gui/ShaderPackScreenSourceTest.java docs/uniform-gap-analysis.md docs/relictium-integration.md docs/directive-support.md docs/backport-status.md docs/parity-roadmap.md docs/evidence-log.md docs/agent-handoff.md`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run for this slice.

Remaining gap:
- Live GUI proof for malformed/missing stored-pack cleanup, notification text, persisted config cleanup, and Relictium override fallback behavior.

Next exact check:
- Continue code-side audits for sampler/image/PBR/Relictium surfaces before runtime validation.

## 2026-05-17 - Agent 3 Terrain Variant Material Alias Slice

Slice:
Target-pack modern terrain variant names now map to exact 1.12 shared block-state predicates where they exist, and broad default containers no longer preempt split variants when the same pack data proves a split family is active.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/directive-support.md`
- `docs/relictium-integration.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Shader-pack/runtime evidence:
- Complementary Reimagined r5.6.1 declares `coarse_dirt`, `podzol:snowy=*`, `red_sand`, chiseled/smooth sandstone and red-sandstone variants, `chiseled_quartz_block`, and `quartz_pillar` in active `block.properties` rows.
- MakeUp UltraFast 9.3e declares `red_sand` and `coarse_dirt` in active `block.properties` rows.
- Local 1.12 bytecode/SRG evidence confirms shared-state families for dirt, sand, sandstone, red sandstone, and quartz.

Implemented behavior:
- `coarse_dirt` / `podzol` resolve to `dirt:variant=*`.
- `red_sand` resolves to `sand:variant=red_sand`.
- `chiseled_sandstone` / `smooth_sandstone` resolve to `sandstone:type=*`.
- `chiseled_red_sandstone` / `smooth_red_sandstone` resolve to `red_sandstone:type=*`.
- `chiseled_quartz_block` / `quartz_pillar` resolve to `quartz_block:variant=chiseled` / `lines_y`.
- Broad default containers for `dirt`, `sand`, `sandstone`, `red_sandstone`, and `quartz_block` are narrowed to their default 1.12 state only when split variants for that family are present; broad-only legacy rows stay broad.
- `cut_*` sandstone, `smooth_quartz`, `quartz_bricks`, and modern terrain walls/slabs/stairs remain unmapped without exact 1.12 state proof.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace`
- `compileJava --stacktrace`
- `git diff --check -- src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java docs/uniform-gap-analysis.md`
- `git diff --no-index --check -- /dev/null <untracked touched file>`
- `rg -n '^(<<<<<<<|=======|>>>>>>>)|[[:blank:]]$' src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java docs/uniform-gap-analysis.md docs/directive-support.md docs/relictium-integration.md docs/backport-status.md docs/parity-roadmap.md docs/evidence-log.md docs/agent-handoff.md`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.

Remaining gap:
- Prove live Relictium terrain `blockId` attributes and shader material branch output for coarse dirt, podzol, red sand, sandstone/red-sandstone variants, quartz pillar, and chiseled quartz.

Next exact check:
- Continue code-side audit for remaining target-pack material aliases only when there is exact 1.12 registry/state evidence; otherwise leave modern-only blocks unmapped and document runtime-unverified behavior.

## 2026-05-18 - Agent 3 Exact Slab Wall And Oak Material Alias Slice

Slice:
Target-pack aliases with exact 1.12 registry/state equivalents now resolve before registry lookup for oak interactive blocks, old stone-slab variants, red sandstone slabs, and mossy cobblestone walls. Broad `stone_slab` and `cobblestone_wall` rows also narrow to their default variants when split aliases for those families appear.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/directive-support.md`
- `docs/relictium-integration.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Shader-pack/runtime evidence:
- Complementary active `block.properties` rows include modern aliases that motivated this slice, including `brick_slab`, `mossy_cobblestone_wall`, oak interactive aliases, and `oak_door`.
- Local 1.12 bytecode confirms the exact registry names and state values for `wooden_door`, `wooden_button`, `wooden_pressure_plate`, `trapdoor`, `fence`, `fence_gate`, `stone_slab`, `stone_slab2`, and `cobblestone_wall`.

Implemented behavior:
- `oak_fence`, `oak_fence_gate`, `oak_button`, `oak_pressure_plate`, `oak_trapdoor`, and `oak_door` resolve to their exact 1.12 registry names.
- `smooth_stone_slab`, `sandstone_slab`, `cobblestone_slab`, `brick_slab`, `stone_brick_slab`, `nether_brick_slab`, and `quartz_slab` resolve to `stone_slab:variant=*`; modern slab `type=top|bottom` converts to `half=top|bottom`, and `type=double` resolves to `double_stone_slab`.
- `red_sandstone_slab` resolves to `stone_slab2:variant=red_sandstone`; modern slab `type=top|bottom|double` resolves to `half=top|bottom` or `double_stone_slab2`.
- `mossy_cobblestone_wall` resolves to `cobblestone_wall:variant=mossy_cobblestone`.
- Broad `stone_slab` and `cobblestone_wall` entries are constrained to `variant=stone` and `variant=cobblestone` only when split aliases are present; broad-only legacy rows stay broad.
- Modern-only walls/slabs/stairs, cut/smooth sandstone slabs, smooth quartz slabs, and non-oak buttons/pressure plates/trapdoors remain unmapped without exact 1.12 state proof.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --stacktrace`
- `test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.complementaryEntitiesOverlayVariantUsesCapturedEntityColorPassthrough --stacktrace --info`
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace`
- `compileJava --stacktrace`

Combined material-map plus loader attempts exposed transient Gradle/test-output issues: one run reported `NoClassDefFoundError` failures in several source-prep tests, and a later run failed while Gradle wrote `ShaderPackLoaderComplementaryTest.xml` with `TestOutputStore` EOF/Kryo buffer underflow. The isolated representative Complementary source-prep test passed, and the combined focused selection passed after clearing only generated test-result/report directories.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.

Remaining gap:
- Prove live Relictium terrain `blockId` attributes and shader material branch output for oak interactive aliases, old stone slab variants, red sandstone slabs, and mossy cobblestone walls.

Next exact check:
- Continue code-side material-ID audit only for target-pack modern names with exact 1.12 registry/state equivalents. Avoid mapping modern-only states that 1.12 cannot distinguish, especially non-oak wood interactive variants and modern-only slab/wall/stair blocks.

## 2026-05-18 - Agent 3 Custom Resource Texture Late Lookup Guard

Slice:
Custom resource texture bindings were checked against the local 1.16.5 reload-safety rule. The 1.12 path already re-queries `TextureManager` inside the sampler supplier, and tests now pin that shape for regular resource textures and custom resource `_n` / `_s` PBR indirection.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/directive-support.md`
- `docs/relictium-integration.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/pipeline/CustomTextureManager.java`
- The local 1.16.5 resource custom texture path explicitly re-queries `TextureManager` on every supplier call so resource reloads cannot leave a sampler holding a deleted `AbstractTexture`.

Implemented behavior:
- Added a source comment to the 1.12 resource binding supplier documenting the reload-safe late lookup rule.
- Added `CustomTextureManagerTest#resourceTextureBindingRequeriesTextureManagerInsideSupplierForReloadSafety`.
- Added `CustomTextureManagerTest#pbrResourceTextureBindingResolvesHolderFromLiveBaseTextureInsideSupplier`.
- No behavioral rewrite was needed; the existing 1.12 supplier already fetched `Minecraft.getTextureManager()` and `manager.getTexture(reference.location)` at update time.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.mixins.TextureManagerPBRReloadMixinSourceTest --tests net.oculus.mixins.MinecraftPBRShutdownMixinSourceTest --stacktrace`
- `compileJava --stacktrace`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.

Remaining gap:
- Prove live custom resource texture and custom resource `_n` / `_s` PBR output through resource reload in a real client, including Relictium-wrapped terrain sampler output.

Next exact check:
- Continue code-side resource binding audits where a source-level stale capture can still exist, especially custom image/resource reload and sampler registry lifetime. Keep material aliases scoped to exact 1.12 registry/state evidence.

## 2026-05-18 - Agent 3 Redstone Diode Material Alias Slice

Slice:
Complementary redstone diode `block.properties` entries with modern `powered` predicates now resolve to 1.12 split-registry repeater/comparator blocks, and broad legacy diode rows no longer preempt mode-specific modern entries when split entries are present.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Shader-pack/runtime evidence:
- Complementary Reimagined r5.6.1 active `block.properties` rows `10644`, `10645`, and `10646` declare `powered_repeater`, `unpowered_repeater`, `powered_comparator`, `unpowered_comparator`, `repeater:powered=*`, and `comparator:mode=*:powered=*`.
- Local 1.12 bytecode confirms the split repeater/comparator registry names.
- Local 1.12 `BlockRedstoneRepeater` has no `powered` state property; local 1.12 `BlockRedstoneComparator` keeps `mode` and `powered` state properties.

Implemented behavior:
- `repeater:powered=true` maps to `powered_repeater`; `repeater:powered=false` maps to `unpowered_repeater`; the modern `powered` predicate is removed before state matching.
- `comparator:powered=true` maps to `powered_comparator`; `comparator:powered=false` maps to `unpowered_comparator`; comparator `mode` and `powered` predicates are preserved for 1.12 state matching.
- Modern powered diode entries add their corresponding split block names to the broad legacy family skip set, so broad `unpowered_comparator` cannot claim all unpowered comparator states before Complementary's later subtract-mode row.
- Broad split diode names remain usable for packs that do not declare modern powered split entries.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --stacktrace --rerun-tasks`
- `cleanTest test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.loadsComplementaryDirectoryWithDimensionsAndPackMetadata --stacktrace --rerun-tasks`

The forced mapper test and representative Complementary directory loader smoke passed with Java 8. Earlier whole-class `ShaderPackLoaderComplementaryTest` attempts failed during Gradle XML report writing with `TestOutputStore` EOF / Kryo buffer underflow, after test execution; this slice relies on the passing focused tests instead.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.

Remaining gap:
- Prove live Relictium terrain `blockId` attributes and material branches for powered/unpowered repeaters and comparators, including compare/subtract modes.

Next exact check:
- Continue code-side audits for exact 1.12-backed material aliases or stale runtime binding captures. Do not map modern-only material states that 1.12 cannot distinguish.

## 2026-05-18 - Agent 3 Sponge And Portal Material Alias Slice

Slice:
Complementary dry/wet sponge and portal `block.properties` rows now resolve to exact 1.12 block states/registry names without broad dry sponge preempting wet sponge.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/block_rendering/BlockMaterialMapping.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shaderpack/IdMap.java`
- Complementary Reimagined r5.6.1 active `block.properties` rows `10964`, `10968`, and `30020`.
- Local 1.12 bytecode confirms `BlockSponge.WET`, `Blocks.SPONGE`, and `Blocks.PORTAL`.

Implemented behavior:
- `wet_sponge` maps to `sponge:wet=true`.
- Broad `sponge` rows narrow to `sponge:wet=false` when a wet split entry exists, preserving the later wet material ID.
- `nether_portal` maps to `portal`.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --stacktrace --rerun-tasks`
- `cleanTest test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.loadsComplementaryDirectoryWithDimensionsAndPackMetadata --stacktrace --rerun-tasks`
- `compileJava --stacktrace`

All three commands passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.

Remaining gap:
- Prove live Relictium terrain `blockId` attributes and material branches for dry sponge, wet sponge, and nether portals.

Next exact check:
- Continue exact 1.12-backed material aliases from active packs, or switch to stale runtime binding captures if no exact alias remains. Keep modern-only states unmapped unless source/bytecode proves a lossless 1.12 equivalent.

## 2026-05-18 - Agent 3 Rail Anvil Lily And Stair Material Alias Slice

Slice:
Complementary utility/block material rows now resolve additional exact 1.12 rename/state aliases without broad anvil rows preempting damaged anvil states.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/block_rendering/BlockMaterialMapping.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shaderpack/IdMap.java`
- Complementary Reimagined r5.6.1 active `block.properties` rows `10037`, `10041`, `10153`, and `10489`.
- Local 1.12 bytecode confirms `Blocks.GOLDEN_RAIL`, `Blocks.STONE_STAIRS`, `Blocks.WATERLILY`, and `BlockAnvil.DAMAGE`.

Implemented behavior:
- `powered_rail` maps to `golden_rail`.
- `chipped_anvil` maps to `anvil:damage=1`.
- `damaged_anvil` maps to `anvil:damage=2`.
- Broad `anvil` rows narrow to `damage=0` when damaged split entries exist.
- `cobblestone_stairs` maps to `stone_stairs`.
- `lily_pad` maps to `waterlily`.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --stacktrace --rerun-tasks`
- `cleanTest test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.loadsComplementaryDirectoryWithDimensionsAndPackMetadata --stacktrace --rerun-tasks`
- `compileJava --stacktrace`

All three commands passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.

Remaining gap:
- Prove live Relictium terrain `blockId` attributes and material branches for powered rails, undamaged/chipped/damaged anvils, cobblestone stairs, and lily pads.

Next exact check:
- Continue exact active-pack aliases only where 1.12 has a lossless equivalent. Good candidates need fresh bytecode/source evidence; modern-only chains, scaffolding, copper, suspicious sand/gravel, and newer cauldrons should stay unmapped unless an exact state model is proven.

## 2026-05-18 - Agent 3 Pumpkin Material Alias Slice

Slice:
Complementary's modern `carved_pumpkin` block material alias now resolves to the 1.12 `pumpkin` block, while plain modern `shulker_box` remains deliberately unmapped because 1.12 has no lossless plain shulker registry equivalent.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/block.properties` rows `5016`, `10392`, and `10396`.
- Local 1.12 bytecode confirms `Blocks.PUMPKIN` and `Blocks.LIT_PUMPKIN`; `BlockPumpkin` owns the old pumpkin placement/golem behavior.
- Local 1.12 bytecode confirms colored shulker registries including `PURPLE_SHULKER_BOX`, but no plain `SHULKER_BOX`.

Implemented behavior:
- `carved_pumpkin` maps to `pumpkin`.
- `jack_o_lantern` remains mapped to `lit_pumpkin`.
- `shulker_box` remains unmapped to avoid treating a modern plain shulker box as the colored 1.12 `purple_shulker_box`.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --stacktrace --rerun-tasks`
- `cleanTest test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.loadsComplementaryDirectoryWithDimensionsAndPackMetadata --stacktrace --rerun-tasks`
- `compileJava --stacktrace`

All three commands passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.

Remaining gap:
- Prove live Relictium terrain `blockId` attributes and material branches for pumpkins, carved pumpkins, jack-o-lanterns, and shulker boxes in a real 1.12.2 client.

Next exact check:
- Continue code-side audits for exact active-pack aliases only where 1.12 bytecode proves a matching registry/state. Treat plain modern shulker boxes as unresolved unless new source evidence proves a non-lossy 1.12 mapping.

## 2026-05-18 - Agent 3 Attached Stem Material Alias Slice

Slice:
Complementary's active 1.12 `block.properties` branch now gets separate material IDs for regular and attached pumpkin/melon stem states at source level. Regular `pumpkin_stem` / `melon_stem` rows stay on the crop material, while modern `attached_pumpkin_stem` / `attached_melon_stem` aliases map to horizontal-facing 1.12 stem states.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/block.properties` active 1.12 rows `10005` and `10017`.
- Local 1.12 bytecode confirms `BlockStem.AGE`, `BlockStem.FACING`, default `FACING=UP`, and actual-state horizontal facing when a mature stem attaches to its crop.

Implemented behavior:
- `attached_pumpkin_stem` maps to horizontal-facing `pumpkin_stem` states.
- `attached_melon_stem` maps to horizontal-facing `melon_stem` states.
- Broad regular `pumpkin_stem` and `melon_stem` entries narrow to `facing=up` when attached aliases are present, preventing the crop row from claiming attached states through first-match mapping.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --stacktrace --rerun-tasks`
- `test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.loadsComplementaryDirectoryWithDimensionsAndPackMetadata --stacktrace --rerun-tasks`
- `compileJava --stacktrace`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. In particular, this does not prove the 1.12 block renderer or Relictium path passes actual horizontal stem states into terrain vertex material lookup in-client.

Remaining gap:
- Prove live Relictium terrain `blockId` attributes and material branches for mature attached pumpkin and melon stems in a real 1.12.2 client.

## 2026-05-18 - Agent 3 Snow Layer Material Split Slice

Slice:
Complementary's snow rows now preserve the intended split between full/layer-8 snow and shallow snow layers on Forge 1.12.2. The broad legacy `snow_layer` token no longer claims every 1.12 snow-layer state before the later modern `snow:layers=1..7` predicates.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `src/test/java/net/oculus/shaderpack/ShaderPackLoaderComplementaryTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- Complementary active rows `10380`, `10381`, and `10953`.
- Local 1.12 bytecode confirms `Blocks.SNOW`, `Blocks.SNOW_LAYER`, and `BlockSnow.LAYERS`.
- Local 1.16.5 block-material mapping uses first-match `putIfAbsent(...)`, and modern 1.16.5 does not have a valid `snow_layer` block name to preempt the later `snow:layers=1..7` entries.

Implemented behavior:
- Modern `snow:layers=*` entries mark broad legacy `snow_layer` as preemptable.
- Full snow and `snow:layers=8` map to material ID `10380`.
- `snow:layers=1..7` map to material ID `10953`.
- The real Complementary loader guard verifies the preprocessed pack map, not only a synthetic entry list.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.complementarySnowLayerRowsResolve112LayerStatesAfterPreprocessing --stacktrace --rerun-tasks`
- `compileJava --stacktrace`

The commands passed with Java 8. The standalone compile printed a ForgeGradle cache/hash `EOFException` warning while skipping a cached binary-patch task, but the build completed successfully.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live Relictium snow `blockId` values, shader material branches, reload behavior, and rendered output still need client validation.

Next exact check:
- Continue exact active-pack material aliases only where 1.12 bytecode proves a matching registry/state. For snow specifically, validate shallow and full snow in-client with Complementary and Relictium active.

## 2026-05-18 - Agent 3 Daylight Detector Material Split Slice

Slice:
Complementary's broad modern `daylight_detector` material row now covers both Forge 1.12 daylight-detector registry blocks. This closes the source-level gap where inverted daylight detectors could fall through unmapped even though the modern shader-pack surface uses one broad detector name.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `src/test/java/net/oculus/shaderpack/ShaderPackLoaderComplementaryTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- Complementary active row `10121`.
- Local 1.12 bytecode confirms `Blocks.DAYLIGHT_DETECTOR`, `Blocks.DAYLIGHT_DETECTOR_INVERTED`, and `BlockDaylightDetector.POWER`.

Implemented behavior:
- Broad modern `daylight_detector` maps both 1.12 normal and inverted detector states.
- `daylight_detector:inverted=true|false` maps to the matching 1.12 split registry and removes the unsupported predicate.
- Explicit legacy `daylight_detector_inverted` entries prevent broad `daylight_detector` from preempting the explicit split.
- The real Complementary loader guard verifies both 1.12 detector blocks map to material ID `10121`.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.complementarySnowLayerRowsResolve112LayerStatesAfterPreprocessing --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.complementaryDaylightDetectorRowCovers112InvertedSplitBlock --stacktrace --rerun-tasks`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live Relictium daylight-detector `blockId` values, shader material branches, reload behavior, and rendered output still need client validation.

Next exact check:
- Continue exact active-pack material aliases only where 1.12 bytecode proves a matching registry/state. For daylight detectors, validate normal and inverted blocks in-client with Complementary and Relictium active.

## 2026-05-18 - Agent 3 Tile Entity Family Material Alias Guard

Slice:
Complementary's generic tile-entity family rows now have source and real-pack loader coverage against Forge 1.12 block registries. The skull/head fallback was also narrowed so exact 1.12 blocks such as `piston_head` cannot be reported as skull aliases by the fallback helper.

Active files:
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/test/java/net/oculus/blockrendering/BlockMaterialMappingTest.java`
- `src/test/java/net/oculus/shaderpack/ShaderPackLoaderComplementaryTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- Complementary row `5004` lists modern sign and hanging-sign names.
- Complementary row `5016` lists modern skull/head, banner, and bed names.
- Local Forge 1.12 bytecode exposes generic `Blocks.STANDING_SIGN`, `Blocks.WALL_SIGN`, `Blocks.SKULL`, `Blocks.STANDING_BANNER`, `Blocks.WALL_BANNER`, `Blocks.BED`, and exact `Blocks.PISTON_HEAD`.

Implemented behavior:
- Modern sign and hanging-sign aliases map to `standing_sign` or `wall_sign`.
- Modern banner aliases map to `standing_banner` or `wall_banner`.
- Modern bed aliases map to `bed`.
- Known vanilla modern skull/head aliases map to `skull`.
- `piston_head` no longer matches the skull/head fallback helper.
- The real Complementary loader guard verifies row `5004` for signs, row `5016` for skull/banner/bed states, and row `10153` for exact `piston_head`.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.complementaryTileEntityFamilyRowsCover112GenericBlocks --stacktrace --rerun-tasks`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live Relictium `blockId` values and rendered material branches for signs, heads/skulls, banners, beds, and piston heads still need client validation.

Next exact check:
- Continue exact active-pack material aliases only where 1.12 bytecode proves a matching registry/state. For this slice, validate the generic tile-entity-backed rows with Complementary and Relictium active in-client.

## 2026-05-18 - Agent 3 Sampler Limit Fail-Fast Diagnostics

Slice:
Dynamic sampler texture-unit exhaustion now reports the sampler uniform that caused the allocation failure. This tightens the source-level fail-fast behavior for custom sampler overrides and reserved-unit relocation toward the local 1.16.5 allocator shape.

Active files:
- `src/main/java/net/oculus/gl/program/ProgramSamplers.java`
- `src/test/java/net/oculus/gl/program/ProgramSamplersTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- Local 1.16.5 `ProgramSamplers` names the sampler in dynamic exhaustion failures.
- Current 1.12 dynamic allocation paths covered custom sampler overrides and sampler relocation away from reserved/conflicting units but previously lost the sampler name when allocation was exhausted.

Implemented behavior:
- `ProgramSamplers` threads the sampler name through `resolveSharedOrDynamicUnit(...)` and `allocateDynamicUnit(...)`.
- Exhausted dynamic allocation errors now include both the sampler and program names.
- Focused tests cover exhausted custom sampler overrides and exhausted relocation away from a reserved unit.

Verification:
- `test --tests net.oculus.gl.program.ProgramSamplersTest --stacktrace --rerun-tasks`
- `compileJava --stacktrace`
- `git diff --check` on touched source, tests, and docs
- whitespace/conflict-marker scan on touched source, tests, and docs
- no-index diff-check on touched source, tests, and docs

The Gradle commands passed with Java 8, and the touched-file hygiene checks produced no whitespace-error output.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. This does not prove live driver texture-unit exhaustion behavior, shader-pack failure UI, sampler contents, or visual/runtime parity.

Next exact check:
- Continue code-side runtime-binding audits where source tests can close a real gap. The material-alias path is now mostly limited to exact 1.12 bytecode-backed cases; remaining broad confidence requires in-client runtime validation.

## 2026-05-18 - Agent 3 Custom Image Allocation / Active Image Fail-Fast Boundary

Slice:
Custom image texture allocation no longer depends on `ImageLimits` or max image-unit support. This keeps shader-pack custom-image sampler aliases alive even on platforms where image uniforms cannot be used, and leaves the active-image fail-fast decision to `ProgramImages`, where active uniform locations and image-unit limits are known.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomImageManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomImageManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- Local 1.16.5 `ProgramImages` checks image-unit exhaustion when `addTextureImage(...)` sees an active image uniform.
- Current 1.12 `ProgramImages` already has focused tests for missing inactive images, unsupported active images, and exhausted active image units.
- The removed `CustomImageManager` guard skipped texture allocation and sampler alias registration before any active-uniform evidence existed.

Implemented behavior:
- `CustomImageManager` always resolves and allocates declared custom images when dimensions change and image declarations exist.
- Paired custom-image sampler bindings remain available through `TextureBindingRegistry`.
- Active custom image uniforms still route through `builder.addTextureImage(...)`, so unsupported/exhausted image units fail at `ProgramImages` instead of being silently bypassed.
- `CustomImageManagerTest#customImageAllocationIsNotGatedByImageUnitLimit` source-pins this boundary.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.gl.program.ProgramImagesTest --stacktrace --rerun-tasks`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live custom image sampler reads, shader image writes, resize/reload behavior, driver image-unit failure presentation, and visual output still need in-client validation.

Next exact check:
- Run full touched-file hygiene and standalone `compileJava` after docs, then continue code-side audits for sampler/image/resource binding surfaces that still have source-testable gaps.

## 2026-05-18 - Agent 3 Legacy BufferBuilder Terrain Attribute Contract Guards

Slice:
The legacy Forge 1.12 `BufferBuilder` terrain fallback now has source-level tests for the extended terrain attribute contract used when the non-Relictium fallback writer path is active.

Active files:
- `src/test/java/net/oculus/pipeline/vertex/OculusLegacyVertexFormatsTest.java`
- `src/test/java/net/oculus/mixins/BufferBuilderExtendedVertexFormatMixinSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Reference/evidence:
- `OculusLegacyVertexFormats` declares signed-short `mc_Entity` at legacy generic index `11`, `mc_midTexCoord` at `12`, tangent bytes at `13`, and four `at_midBlock` bytes at `14`.
- `BufferBuilderExtendedVertexFormatMixin` writes signed block/render-type shorts, computes `at_midBlock.xyz`, stores block emission in `at_midBlock.w`, and saves/restores the block-context stack.
- `OculusTerrainVertexWriterFallback` wraps fallback vertex writes with `oculus$beginBlock(...)` / `oculus$endBlock()` in `try/finally`.
- `ForgeHooksClientGenericAttribMixin` and `VertexFormatElementGenericMixin` are pinned in `oculus.mixins.json` so Forge 1.12 accepts and binds high-index generic attributes.

Implemented behavior:
- No production code changed in this slice. The gap closed here is focused source/test coverage for a runtime-sensitive fallback path.
- `OculusLegacyVertexFormatsTest` intentionally uses source assertions because plain JUnit does not apply the mixin that accepts high-index generic `VertexFormatElement` values.
- `BufferBuilderExtendedVertexFormatMixinSourceTest` guards the mixin writes, fallback writer cleanup shape, and generic attribute binding mixin registration.

Verification:
- `test --tests net.oculus.pipeline.vertex.OculusLegacyVertexFormatsTest --tests net.oculus.mixins.BufferBuilderExtendedVertexFormatMixinSourceTest --stacktrace --rerun-tasks`
- `compileJava --stacktrace`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live BufferBuilder bytes, shader-visible legacy terrain attributes, block emission output, and rendered fallback terrain behavior still need client validation.

Next exact check:
- Run touched-file hygiene for this slice, then continue code-side audits. For terrain specifically, validate the legacy fallback path in-client or force a debug path that proves `mc_Entity`, `mc_midTexCoord`, `at_tangent`, and `at_midBlock` values reach shaders.

## 2026-05-18 - Agent 3 Custom Noise Fallback Continuity

Slice:
Declared `texture.noise` bindings that cannot be built now fall back to generated deterministic noise instead of leaving `noisetex` / `noise_texture` without a binding.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/pipeline/CustomTextureManager.java`

Implemented behavior:
- `CustomTextureManager.initialize()` first tries the declared custom noise binding when `texture.noise` exists.
- If that binding is `null`, it now calls `buildDefaultNoiseBinding(defaultNoiseTextureResolution)` before marking the manager initialized.
- The generated fallback still uses the existing deterministic `Random(0)` pixel path and owned-texture cleanup behavior.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomTextureManagerTest --stacktrace --rerun-tasks`
- `compileJava --stacktrace`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live custom-noise failure presentation, generated fallback sampler output, and reload behavior still need client validation.

Next exact check:
- Continue custom resource/PBR binding audits where source tests can still close gaps; runtime proof for `texture.noise`, resource reloads, and PBR sampler output remains required before claiming parity.

## 2026-05-18 - Agent 3 Malformed Custom Resource Texture Guard

Slice:
Malformed optional custom resource texture locations now skip only the bad binding instead of hard-failing custom texture manager initialization.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/pipeline/CustomTextureManager.java`

Implemented behavior:
- `buildResourceBinding(...)` catches runtime failures from `resolveResourceTextureReference(...)`, logs the namespace/location, and returns `null`.
- The guard runs before `ensureTextureLoaded(...)`, so malformed resource locations do not reach texture loading.
- Valid resource custom textures and `_n` / `_s` PBR indirection still use the late `TextureManager` supplier path.
- `CustomTextureManagerTest#malformedCustomResourceTextureLocationsAreSkippedBeforeBinding` pins the source ordering.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomTextureManagerTest --stacktrace --rerun-tasks`
- `compileJava --stacktrace`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live malformed-pack warning presentation, reload behavior, and shader-visible sampler output still need client validation.

Next exact check:
- Continue code-side custom resource/PBR reload audits where source tests can still close gaps; runtime proof for malformed custom resources, resource reloads, and PBR sampler output remains required before claiming parity.

## 2026-05-18 - Agent 3 Custom Resource Eager Load Failure Guard

Slice:
Valid but unloadable optional custom resource textures no longer abort custom texture manager initialization from the 1.12 eager load bridge.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/pipeline/CustomTextureManager.java`

Implemented behavior:
- `ensureTextureLoaded(...)` catches runtime failures from `TextureManager.loadTexture(...)`, logs the location, and continues.
- The existing late supplier remains the authority for sampler updates; if the texture manager still lacks the texture, the supplier returns `TextureUtil.MISSING_TEXTURE`.
- Valid resource custom textures and `_n` / `_s` PBR indirection still use the late `TextureManager` lookup path.
- `CustomTextureManagerTest#eagerResourceTextureLoadFailuresDoNotAbortBindingCreation` pins the source guard.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomTextureManagerTest --stacktrace --rerun-tasks`
- `compileJava --stacktrace --rerun-tasks`
- `git diff --check`, no-index diff-check, and whitespace/conflict-marker scan over the touched source/docs files

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live resource-load warning presentation, missing-texture sampler output, reload behavior, and custom resource PBR output still need client validation.

Next exact check:
- Continue source audits outside custom resource binding or move to runtime validation when allowed.

## 2026-05-18 - Agent 3 Relictium Instanced Tessellation Binding Guard

Slice:
Relictium tessellation augmentation now preserves instanced bindings before adding Oculus per-vertex terrain attributes.

Active files:
- `src/main/java/net/oculus/mixin/pipeline/MultidrawChunkRenderBackendMixin.java`
- `src/main/java/net/oculus/mixin/pipeline/ChunkOneshotGraphicsStateMixin.java`
- `src/test/java/net/oculus/compat/relictium/RelictiumTerrainBindingAugmentationSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Evidence:
- `javap -c -p` on the installed Relictium 1.2.0 jar shows multidraw terrain creates a non-instanced vertex-buffer binding and a separate instanced model-offset binding.
- Before this slice, the multidraw augmentation loop appended `iris_Normal`, `at_tangent`, `mc_midTexCoord`, `mc_Entity`, and `at_midBlock` to every binding, including the instanced model-offset binding.
- The mixins now skip `binding.isInstanced()` bindings and preserve them unchanged.

Verification:
- `test --tests net.oculus.compat.relictium.RelictiumTerrainBindingAugmentationSourceTest --tests net.oculus.compat.relictium.RelictiumTerrainBridgeBytecodeTest --tests net.oculus.compat.relictium.RelictiumSodiumWorldRendererVertexFormatMixinSourceTest --tests net.oculus.pipeline.OculusTerrainVertexTypeSourceTest --tests net.oculus.pipeline.OculusChunkShaderBindingPointsTest --stacktrace --rerun-tasks`
- `compileJava --stacktrace --rerun-tasks`

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live Relictium attribute pointers, vertex values, material branches, and rendered output still need client validation.

Next exact check:
- Run touched-file hygiene for this slice, then continue code-side audits. For Relictium specifically, validate in-client that multidraw `iris_ModelOffset` stays on the instanced buffer while the Oculus terrain attributes read from the extended vertex buffer.

## 2026-05-18 - Agent 3 Custom Image / Texture Binding Capture Cleanup Guards

Slice:
Custom image allocation, custom image fallback clears, shader-pack PNG custom texture upload, and generated fallback-noise upload now have tighter cleanup guards around texture binding capture and pixel-store restore.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomImageManager.java`
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomImageManagerTest.java`
- `src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- Generated custom-image texture IDs are deleted and lifecycle metadata is cleared if setup fails before manager ownership, even when previous binding capture is the failing operation.
- Custom image fallback clears restore the previous texture binding only after capture and restore `GL_UNPACK_ALIGNMENT` only after changing it.
- Shader-pack PNG custom textures and generated fallback noise now capture previous 2D binding inside the cleanup-protected block and restore only after capture.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --stacktrace --rerun-tasks`
- `compileJava --stacktrace --rerun-tasks`
- Touched-file `git diff --check`, no-index diff-check, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live driver failure behavior, reload behavior, custom image writes, fallback-noise output, sampler contents, and visual parity still need client validation.

Next exact check:
- Continue source audits or runtime validation when client runs are allowed.

## 2026-05-18 - Agent 3 Custom Texture Stage Name Case Boundary

Slice:
`texture.<stage>.<sampler>` directive stage parsing now follows the local 1.16.5 exact lowercase boundary.

Active files:
- `src/main/java/net/oculus/shaderpack/texture/TextureStage.java`
- `src/test/java/net/oculus/shaderpack/ShaderPropertiesTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `TextureStage.parse(...)` no longer lowercases stage names.
- Lowercase reference stages still bind: `shadowcomp`, `prepare`, `gbuffers`, `deferred`, and `composite`.
- Mixed- or upper-case stage names are ignored as unknown stages instead of feeding active custom texture bindings to normal or Relictium-wrapped programs.

Verification:
- `test --tests net.oculus.shaderpack.ShaderPropertiesTest --stacktrace --rerun-tasks`
- `compileJava --stacktrace --rerun-tasks`
- Touched-file `git diff --check`, no-index diff-check, and whitespace/conflict-marker scan passed after normalizing `TextureStage.java` to LF.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live warning presentation, reload behavior, custom texture sampler output, and visual parity still need client validation where relevant.

Next exact check:
- Continue source audits for runtime binding surfaces, or validate custom texture stage warning/sampler behavior in-client when client runs are allowed.

## 2026-05-18 - Agent 3 Render-Target Sampler Fullscreen Split

Slice:
Render-target sampler helper binding now matches the local 1.16.5 fullscreen/non-fullscreen split.

Active files:
- `src/main/java/net/oculus/samplers/IrisSamplers.java`
- `src/main/java/net/oculus/postprocess/CompositeRenderer.java`
- `src/main/java/net/oculus/postprocess/FinalPassRenderer.java`
- `src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java`
- `src/test/java/net/oculus/samplers/IrisSamplersTest.java`
- `src/test/java/net/oculus/pipeline/shadow/ShadowRendererSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/evidence-log.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/agent-handoff.md`
- `docs/render-target-lifecycle.md`

Implemented behavior:
- `IrisSamplers.addRenderTargetSamplerBindings(...)` now has a fullscreen-aware overload.
- Composite and final fullscreen paths pass `true` and keep binding render-target sampler aliases from index `0`.
- Shadow/non-fullscreen setup passes `false` and starts helper registration at render-target index `4`.
- Render-target image bindings were left unchanged and continue to bind all configured image targets.

Verification:
- `test --tests net.oculus.samplers.IrisSamplersTest --tests net.oculus.pipeline.shadow.ShadowRendererSourceTest --stacktrace --rerun-tasks`
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live sampler contents, shadow compute output, fullscreen output, reload/resize behavior, Relictium wrapped-program sampler output, and visual parity still need client validation.

Next exact check:
- Continue code-side source audits for the remaining runtime binding surfaces, or move to client validation when allowed.

## 2026-05-18 - Agent 3 SSBO Destroy Cleanup Guard

Slice:
`bufferObject.*` / SSBO manager teardown now clears reload/fallback state even if GL buffer deletion throws.

Active files:
- `src/main/java/net/oculus/pipeline/buffer/ShaderStorageBufferManager.java`
- `src/test/java/net/oculus/pipeline/buffer/ShaderStorageBufferManagerTest.java`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `ShaderStorageBufferManager.destroy()` wraps teardown in `try` / `finally`.
- Each owned SSBO is deleted through a helper that ignores non-positive handles, catches runtime delete failures, logs the failed handle, and continues.
- The manager always clears owned handles and sets `initialized = false`, including when `initialize()` calls `destroy()` from its rollback path.

Verification:
- `test --tests net.oculus.pipeline.buffer.ShaderStorageBufferManagerTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live SSBO allocation, binding, delete failure behavior, shader-visible contents, and Complementary reload behavior still need client validation.

Next exact check:
- Continue code-side audits or move to client validation when allowed.

## 2026-05-18 - Agent 3 Simple PBR Companion Handoff Cleanup

Slice:
Simple PBR companion loading now matches the local 1.16.5 order by creating normal and specular companions before handing either one to the consumer, while still closing a created-but-unaccepted normal texture if a later companion or consumer failure occurs.

Active files:
- `src/main/java/net/oculus/texture/pbr/loader/SimplePBRLoader.java`
- `src/test/java/net/oculus/texture/pbr/loader/SimplePBRLoaderTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/texture/pbr/loader/SimplePBRLoader.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/texture/pbr/PBRTextureManager.java`

Shader-pack/runtime evidence:
- Rechecked target-pack `texture.*` directive presence in Complementary Reimagined r5.6.1 and MakeUp UltraFast 9.3e `shaders.properties`.
- The code path affects simple `_n` / `_s` PBR companion resources and custom resource PBR indirection rather than adding a new directive.

Implemented behavior:
- `SimplePBRLoader.load(...)` now creates both normal and specular companions before accepting either one, matching the local 1.16.5 reference order.
- If a later companion or consumer failure leaves a created normal companion unaccepted, the loader closes it before rethrowing; accepted textures remain covered by `PBRTextureManager.loadHolder(...)` cleanup.
- `SimplePBRLoaderTest#loadCreatesBothCompanionsBeforeHandingOffLikeReference` and `#specularRuntimeFailureClosesUnacceptedNormalTextureBeforeRethrowing` cover the ordering and cleanup.

Verification:
- `test --tests net.oculus.texture.pbr.loader.SimplePBRLoaderTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.texture.pbr.PBRAtlasTextureTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live simple PBR output, custom resource `_n` / `_s` sampler output, Relictium wrapped-program PBR sampler output, resource reload behavior, and visual parity still need client validation.

Next exact check:
- Continue code-side PBR/custom-resource lifecycle auditing around reload and stale-holder edge cases, or move to in-client PBR validation when allowed.

## 2026-05-18 - Agent 3 GUI Apply Failed-Reload State Guard

Slice:
GUI Apply/Done now respects the shared reload result when enabling an external pack.

Active files:
- `src/main/java/net/oculus/gui/ShaderPackScreen.java`
- `src/test/java/net/oculus/gui/ShaderPackScreenSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `applyChanges()` returns success/failure.
- Done closes only after a successful apply.
- A failed shared reload while enabling an external pack keeps the change pending/unapplied, shows a failed-apply notification, and avoids marking the pack list applied or capturing a new apply baseline.

Verification:
- `test --tests net.oculus.gui.ShaderPackScreenSourceTest --tests net.oculus.config.OculusConfigTest --tests net.oculus.client.ShaderPackReloaderTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live notification display, Done-button behavior, failed-pack config state, and post-fallback Relictium/runtime binding state still need client validation.

Next exact check:
- Continue code-side audits where source/test evidence is possible, or run targeted client validation when allowed.

## 2026-05-18 - Agent 3 GUI Shader Pack Corrupt-Zip Recovery

Slice:
Shader-pack GUI load/reload boundaries now treat Java 8 corrupt zip `ZipError` as recoverable like the public reload path.

Active files:
- `src/main/java/net/oculus/gui/ShaderPackScreen.java`
- `src/test/java/net/oculus/gui/ShaderPackScreenSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/Iris.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/gui/screen/ShaderPackScreen.java`

Implemented behavior:
- `ShaderPackScreen.preloadSelectedPack(...)` catches `Exception | ZipError`, clears overrides for the invalid stored pack, clears `selectedPackName`, persists the recovery, and falls back to the active/internal pack.
- `loadShaderPack(...)` catches `Exception | ZipError` so a corrupt zip selected from the GUI creates the existing failed-pack placeholder instead of escaping the screen.
- `reloadSelectedPackForApply(...)` catches `Exception | ZipError`, displays the existing failed-options notification, and keeps the screen open.
- `resolveBaselinePack(...)` catches `Exception | ZipError` and falls back to the internal pack if discard cannot reload the captured baseline.
- `ShaderPackScreenSourceTest` source-pins the four boundaries.

Verification:
- `test --tests net.oculus.gui.ShaderPackScreenSourceTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live shader-pack screen behavior, corrupt-zip user messaging, config state, and Relictium override refresh still need client validation.

Next exact check:
- Run touched-file whitespace/hygiene for this slice, then continue code-side audits or validate GUI corrupt-pack recovery in a real client when runtime testing is allowed.

## 2026-05-18 - Agent 3 Shader Pack List Enumeration Failure Recovery

Slice:
Shader-pack list refresh now follows the 1.16.5 GUI boundary for unexpected shaderpacks-directory enumeration failures.

Active files:
- `src/main/java/net/oculus/gui/ShaderPackSelectionList.java`
- `src/test/java/net/oculus/gui/ShaderPackScreenSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/gui/element/ShaderPackSelectionList.java`

Implemented behavior:
- `ShaderPackSelectionList.refresh()` catches `Throwable` around `ShaderpackDirectoryManager.findShaderPacks()`, logs the failure, adds the existing error rows, and returns.
- Removed the unused `java.io.IOException` import.
- `ShaderPackScreenSourceTest` source-pins the broad enumeration recovery boundary.

Verification:
- `test --tests net.oculus.gui.ShaderPackScreenSourceTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live shader-pack list behavior under permission errors, bad filesystems, or modded filesystem providers still needs client validation.

Next exact check:
- Run touched-file whitespace/hygiene for this slice, then continue code-side audits for binding/config/resource gaps or validate GUI recovery in-client when allowed.

## 2026-05-18 - Agent 3 Debug Config Exact-True Semantics

Slice:
Config debug option loading now matches the local 1.16.5 exact-true parsing boundary.

Active files:
- `src/main/java/net/oculus/config/OculusConfig.java`
- `src/test/java/net/oculus/config/OculusConfigTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/config/IrisConfig.java`

Implemented behavior:
- `OculusConfig.load()` now enables debug options only when `debugEnabled` or fallback `enableDebugOptions` is exactly `true`.
- Uppercase `TRUE` and other case variants stay disabled, matching `IrisConfig.load()`.
- Port-native keys still take precedence over fallback reference keys.

Verification:
- `test --tests net.oculus.config.OculusConfigTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live debug output toggling and reload-key behavior still need client validation.

Next exact check:
- Run touched-file whitespace/hygiene for this slice, then continue code-side audits for binding/config/resource gaps or validate config behavior in-client when runtime testing is allowed.

## 2026-05-18 - Agent 3 Texture Delete Error Isolation

Slice:
Manager-owned texture delete helpers now isolate both runtime failures and hard errors so reload/fallback/shutdown cleanup can continue to later owned resources.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomImageManager.java`
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/main/java/net/oculus/texture/FallbackTextures.java`
- `src/main/java/net/oculus/texture/pbr/PBRTextureManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomImageManagerTest.java`
- `src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java`
- `src/test/java/net/oculus/texture/FallbackTexturesTest.java`
- `src/test/java/net/oculus/texture/pbr/PBRTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `CustomImageManager`, `CustomTextureManager`, `FallbackTextures`, and `PBRTextureManager` delete helpers now catch `RuntimeException | Error`.
- Existing `finally` blocks still clear local texture maps, owned IDs, cached fallback IDs, and PBR holder/default references.
- PBR cleanup is tested with an injected `AssertionError`; the later companion texture is still deleted and the holder cache is cleared.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.texture.FallbackTexturesTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live driver delete failures, reload/fallback behavior, resource reload, shutdown ordering, and rendered sampler output still need client validation.

Next exact check:
- Continue source audits around data-binding and reload lifecycle, or validate reload/fallback teardown in-client when client runs are allowed.

## 2026-05-18 - Agent 3 Relictium / Legacy at_midBlock Local Position Fix

Slice:
Relictium/Sodium and legacy terrain context hooks now feed chunk-local block coordinates into `at_midBlock` packing instead of world coordinates.

Active files:
- `src/main/java/net/oculus/mixin/ChunkRenderRebuildTaskMixin.java`
- `src/main/java/net/oculus/mixin/vertexformat/BlockModelRendererBlockContextMixin.java`
- `src/main/java/net/oculus/mixin/vertexformat/BlockFluidRendererBlockContextMixin.java`
- `src/test/java/net/oculus/mixins/BufferBuilderExtendedVertexFormatMixinSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/mixin/vertices/block_rendering/MixinChunkRebuildTask.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/compat/sodium/impl/vertex_format/terrain_xhfp/XHFPModelVertexBufferWriterNio.java`

Implemented behavior:
- `ChunkRenderRebuildTaskMixin` uses `pos.getX() & 15`, `pos.getY() & 15`, and `pos.getZ() & 15` before setting Relictium terrain block/fluid context.
- Legacy block and fluid context mixins pass the same chunk-local coordinates into `oculus$beginBlock(...)`.
- The source test pins the reference `pos & 0xF` behavior and rejects the previous direct world-coordinate calls.

Verification:
- `test --tests net.oculus.mixins.BufferBuilderExtendedVertexFormatMixinSourceTest --tests net.oculus.pipeline.OculusTerrainVertexTypeSourceTest --tests net.oculus.compat.relictium.RelictiumSodiumWorldRendererVertexFormatMixinSourceTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run, so live `at_midBlock` bytes and rendered terrain output are not proven.

Next exact check:
- Continue source-level audits around custom resource/PBR/image/reload surfaces, or move to client validation when allowed. In client validation, inspect Relictium and legacy terrain `at_midBlock` outside the origin chunk across solid, fluid, translucent, and shadow terrain.

## 2026-05-18 - Agent 3 Custom Texture Allocation Fail-Fast

Slice:
Shader-pack PNG custom textures and generated fallback noise now fail clearly if GL returns a non-positive texture ID.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `buildPngBinding(...)` throws a clear allocation failure for non-positive texture IDs before binding/upload.
- `buildDefaultNoiseBinding(...)` throws a clear allocation failure for non-positive generated-noise texture IDs.
- Existing positive-ID rollback cleanup remains intact: restore previous 2D binding, delete through `deleteOwnedTexture(...)`, and record ownership only after setup succeeds.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomTextureManagerTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live allocation-failure behavior, shader-pack PNG sampler output, generated-noise output, and reload/fallback cleanup still need client validation.

Next exact check:
- Continue source-level audits around custom resource/PBR/image/reload surfaces, then move to client validation when allowed.

## 2026-05-18 - Agent 3 PBR Default Texture Constructor Rollback

Slice:
Default PBR single-color textures now roll back a generated GL handle if constructor-time upload fails before the manager stores the texture field.

Active files:
- `src/main/java/net/oculus/texture/pbr/PBRTextureManager.java`
- `src/test/java/net/oculus/texture/pbr/PBRTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `SingleColorTexture` tracks constructor upload success and deletes itself through the shared PBR delete helper on failure.
- The upload path throws clearly if `getGlTextureId()` returns a non-positive handle.
- Owned PBR companion cleanup, default texture cleanup, and constructor rollback now share the same exception-isolating delete helper.

Verification:
- `test --tests net.oculus.texture.pbr.PBRTextureManagerTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live default PBR texture upload, sampler output, reload/shutdown behavior, and driver delete behavior still need client validation.

Next exact check:
- Continue code-side audits around resource lifecycle and runtime binding surfaces, or move to client validation when allowed.

## 2026-05-18 - Agent 3 White Fallback Sampler Texture Lifecycle Guard

Slice:
Owned white fallback sampler texture setup and teardown now use the same guarded lifecycle policy as the other manager-owned runtime resources.

Active files:
- `src/main/java/net/oculus/texture/FallbackTextures.java`
- `src/test/java/net/oculus/texture/FallbackTexturesTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `FallbackTextures.destroy()` clears the cached white texture ID before attempting GL deletion.
- Failed setup/upload after `glGenTextures()` now restores the previous 2D binding and deletes the generated ID through a safe helper.
- The helper ignores non-positive IDs, pairs successful deletion with `TextureLifecycleTracker.onDeleteTexture(...)`, and logs runtime delete failures without leaving stale cached state.

Verification:
- `test --tests net.oculus.texture.FallbackTexturesTest --tests net.oculus.texture.TextureLifecycleTrackerSourceTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live unavailable-input fallback sampler output, driver delete failure behavior, and reload/fallback texture state still need client validation.

Next exact check:
- Continue code-side audits, with priority on runtime data/resource lifecycle gaps that can still be proven at source/test level before client validation.

## 2026-05-18 - Agent 3 Custom Image / Texture Rollback Delete Isolation

Slice:
Generated custom-image, shader-pack PNG custom-texture, and generated fallback-noise rollback now use exception-isolating delete helpers.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomImageManager.java`
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomImageManagerTest.java`
- `src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `CustomImageManager.allocateTexture(...)` uses `deleteTexture(...)` for failed setup rollback before manager ownership.
- `CustomTextureManager.buildPngBinding(...)` and `buildDefaultNoiseBinding(...)` use `deleteOwnedTexture(...)` for failed setup/upload rollback before manager ownership.
- The focused source tests now require those rollback paths to avoid raw `GL11.glDeleteTextures(textureId)` calls in the allocating method bodies.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live driver delete failure behavior, custom image writes, custom texture sampler output, generated-noise output, and reload/fallback behavior still need client validation.

Next exact check:
- Continue code-side audits or move to client validation when allowed.

## 2026-05-18 - Agent 3 Relictium Override Reload Cache Reset

Slice:
Relictium terrain override teardown now resets the manager cache keys as well as deleting program objects.

Active files:
- `src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramOverrides.java`
- `src/test/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramOverridesSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `deleteShaders()` catches per-program `RuntimeException | Error` deletion failures.
- Cleanup clears `programs` in `finally`.
- Cleanup resets `versionCounterForSodiumShaderReload`, `currentPipeline`, and `currentDevice` so the next lookup rebuilds if the same backend/pipeline/version is reused.

Verification:
- `test --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramOverridesSourceTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramSourceTest --tests net.oculus.compat.relictium.RelictiumTerrainBridgeBytecodeTest --tests net.oculus.compat.relictium.RelictiumTerrainBindingAugmentationSourceTest --stacktrace --rerun-tasks` passed.
- That focused Gradle run executed `compileJava`.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live Relictium backend delete/recreate, resource reload, terrain sampler/image output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around runtime data/resource lifecycle, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Program Destroy Binding Isolation

Slice:
Program teardown now clears active uniform, sampler, and image state only when the destroyed program owns the active binding set.

Active files:
- `src/main/java/net/oculus/gl/program/Program.java`
- `src/main/java/net/oculus/gl/program/ProgramUniforms.java`
- `src/main/java/net/oculus/gl/program/ProgramSamplers.java`
- `src/main/java/net/oculus/gl/program/ProgramImages.java`
- `src/test/java/net/oculus/gl/program/ProgramImagesTest.java`
- `src/test/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `ProgramUniforms`, `ProgramSamplers`, and `ProgramImages` expose package-private instance-scoped active-clear helpers.
- `Program.destroyInternal()` uses those helpers instead of globally clearing all active binding state.
- Explicit `Program.unbind()` still clears all active uniforms, samplers, images, and GL program binding.

Verification:
- `test --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramSourceTest --tests net.oculus.gl.program.ProgramSamplersTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live reload/fallback ordering, Relictium backend deletion, dimension cleanup, dynamic uniform listener state, PBR sampler notifier state, image-unit cleanup, and shader-visible post-teardown state still need client validation.

Next exact check:
- Continue code-side audits around reload/resource lifecycle or move to runtime validation when allowed.

## 2026-05-18 - Agent 3 Program Unbind Cleanup Aggregation

Slice:
Raster and compute program unbind now attempt all active binding cleanup steps before rethrowing cleanup failures.

Active files:
- `src/main/java/net/oculus/gl/program/Program.java`
- `src/main/java/net/oculus/gl/program/ComputeProgram.java`
- `src/test/java/net/oculus/gl/program/ProgramImagesTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `Program.unbind()` routes through `Program.clearActiveBindingsAndProgram()`.
- The shared helper attempts active uniform cleanup, sampler cleanup, image cleanup, and `glUseProgram(0)` in order.
- Cleanup failures are aggregated; later failures are suppressed onto the first, and rethrow happens after all steps have been attempted.
- `ComputeProgram.unbind()` uses the same helper.

Verification:
- `test --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.gl.program.ProgramUniformsTest --tests net.oculus.colorspace.ColorSpaceShaderSourceTest --tests net.oculus.postprocess.PostprocessRendererSourceTest --tests net.oculus.postprocess.FinalPassRendererSourceTest --tests net.oculus.postprocess.CenterDepthSamplerSourceTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live cleanup failure behavior, postprocess cleanup, color-space compute cleanup, shadow target-prep cleanup, reload/fallback cleanup, and shader-visible state after cleanup failures remain unverified.

Next exact check:
- Continue code-side audits around reload/resource lifecycle or move to runtime validation when allowed.

## 2026-05-18 - Agent 3 Uniform Upload Fail-Fast Boundary

Slice:
Uniform upload failures now propagate instead of being logged and ignored.

Active files:
- `src/main/java/net/oculus/gl/program/ProgramUniforms.java`
- `src/test/java/net/oculus/gl/program/ProgramUniformsTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `ProgramUniforms.UniformBinding.upload()` directly calls the registered updater.
- Runtime failures from built-in suppliers, custom uniform expressions, matrix suppliers, or GL uniform calls now surface instead of leaving stale shader values behind.
- The source shape matches the inspected 1.16.5 `ProgramUniforms.updateStage(...)` and `Uniform.update()` path, which does not catch upload failures.

Verification:
- `test --tests net.oculus.gl.program.ProgramUniformsTest --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --tests net.oculus.uniforms.CapturedRenderingStateTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live error presentation, failed-pack fallback, reload behavior, and Relictium wrapped terrain uniform failure behavior remain unverified.

Next exact check:
- Continue code-side audits around reload/resource lifecycle or move to runtime validation when allowed.

## 2026-05-18 - Agent 3 PBR Texture-Format Callback Suppression

Slice:
PBR texture-format filtering edits no longer re-enter the base texture bind callback while custom resource or PBR sampler bindings resolve.

Active files:
- `src/main/java/net/oculus/gl/OculusRenderSystem.java`
- `src/main/java/net/oculus/mixin/pipeline/GlStateManagerStateMixin.java`
- `src/main/java/net/oculus/texture/format/TextureFormat.java`
- `src/test/java/net/oculus/gl/OculusRenderSystemCapabilityDispatchTest.java`
- `src/test/java/net/oculus/mixins/GlStateManagerStateMixinSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `OculusRenderSystem` now has a nesting-aware texture bind callback suppression guard.
- `GlStateManagerStateMixin` checks that guard before publishing texture binding notifiers or calling `pipeline.onBindTexture(...)`.
- `TextureFormat.setupTextureParameters(...)` uses the guard around its temporary PBR normal/specular texture bind, filter edits, and binding restore.
- The path still uses `GlStateManager.bindTexture(...)` so the 1.12 vanilla texture cache remains coherent.

Verification:
- `test --tests net.oculus.gl.OculusRenderSystemCapabilityDispatchTest --tests net.oculus.mixins.GlStateManagerStateMixinSourceTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.texture.format.TextureFormatLoaderTest --tests net.oculus.texture.format.LabPBRTextureFormatTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live PBR sampler output, callback timing, resource reload behavior, Relictium wrapped-program PBR output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around PBR/custom-resource live supplier cleanup, config/reload transitions, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Relictium Wrapped-Program Ownership Cleanup

Slice:
Relictium terrain wrappers now clean Oculus binding state without taking ownership of Relictium-linked GL program handles.

Active files:
- `src/main/java/net/oculus/gl/program/Program.java`
- `src/main/java/net/oculus/gl/program/ProgramBuilder.java`
- `src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgram.java`
- `src/test/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramSourceTest.java`
- `src/test/java/net/oculus/gl/program/ProgramImagesTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `Program` now carries explicit GL handle ownership.
- `ProgramBuilder.wrapLinkedProgram(...)` builds non-owning wrappers for Relictium-owned linked handles.
- `Program.destroyInternal()` still clears active uniforms, samplers, and images for every wrapper, but only deletes the GL program when the `Program` owns the handle.
- `OculusRelictiumChunkProgram.delete()` destroys the Oculus wrapper before calling Relictium `super.delete()`.
- Wrapper cleanup failures do not block Relictium handle deletion; if both paths fail, the wrapper failure is suppressed onto the Relictium deletion failure.

Verification:
- `test --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramSourceTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramOverridesSourceTest --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.pipeline.SodiumTerrainPipelineTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live Relictium backend delete/recreate behavior, driver cleanup, post-reload sampler/image state, and visual terrain parity still need client validation.

Next exact check:
- Continue code-side audits around reload/config GUI transitions or sampler/custom-resource PBR behavior, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Custom-Image Transactional Resize

Slice:
Custom-image resize/reload now preserves the previous live image state until the full replacement set has been resolved and allocated.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomImageManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomImageManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `CustomImageManager.initializeOrResize(...)` allocates replacement textures into a temporary map before touching live textures.
- Failed dimension resolution, texture setup, or clear deletes only the partial replacement set and leaves previous textures, sampler aliases, and cached dimensions intact.
- Successful replacement swaps the live map first, registers new paired sampler bindings, and then unregisters/deletes old bindings by owned identity.
- Focused tests now cover the pre-allocation failure path that used to clear previous image state.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.gl.program.ProgramImagesTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live custom-image writes, sampler reads, resize/reload behavior, driver cleanup, Relictium wrapped-program image output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around image barriers or PBR/custom-resource reload output, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Active Image Binding Cleanup

Slice:
Program image-unit state now has active binding cleanup on program switches, unbind, deletion, and compute unbind.

Active files:
- `src/main/java/net/oculus/gl/image/ImageBinding.java`
- `src/main/java/net/oculus/gl/program/ProgramImages.java`
- `src/main/java/net/oculus/gl/program/Program.java`
- `src/main/java/net/oculus/gl/program/ComputeProgram.java`
- `src/test/java/net/oculus/gl/program/ProgramImagesTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `ProgramImages` tracks the active image binding set.
- `ProgramImages.update()` unbinds the previous active image set before binding a different program's images.
- `ProgramImages.clearActiveImages()` unbinds and clears active image state.
- `Program.unbind()`, `Program.destroyInternal()`, and `ComputeProgram.unbind()` call `ProgramImages.clearActiveImages()`.
- `ImageBinding.unbind()` clears each image unit by binding texture ID `0`.

Verification:
- `test --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.colorspace.ColorSpaceShaderSourceTest --tests net.oculus.gl.OculusRenderSystemCapabilityDispatchTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live image writes, driver cleanup, reload/fallback state, Relictium wrapped-program image output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around image barriers, compute/postprocess image writes, or remaining Relictium runtime binding lifecycle gaps before client validation.

## 2026-05-18 - Agent 3 Postprocess Manual Image Cleanup

Slice:
Postprocess cleanup paths that intentionally avoid broad `Program.unbind()` now clear active image units explicitly.

Active files:
- `src/main/java/net/oculus/postprocess/CompositeRenderer.java`
- `src/main/java/net/oculus/postprocess/FinalPassRenderer.java`
- `src/main/java/net/oculus/postprocess/CenterDepthSampler.java`
- `src/test/java/net/oculus/postprocess/PostprocessRendererSourceTest.java`
- `src/test/java/net/oculus/postprocess/FinalPassRendererSourceTest.java`
- `src/test/java/net/oculus/postprocess/CenterDepthSamplerSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `CompositeRenderer.restoreAfterRenderAll(...)` clears active images after uniforms/samplers and before `glUseProgram(0)`.
- `FinalPassRenderer.restoreAfterRender(...)` clears active images after uniforms/samplers and before `glUseProgram(0)` / final sampler unbind.
- `CenterDepthSampler` clears active images after sampler cleanup on normal and exceptional cleanup paths.
- Source tests pin the cleanup order while preserving the existing no-`Program.unbind()` constraints for those postprocess paths.

Verification:
- `test --tests net.oculus.postprocess.PostprocessRendererSourceTest --tests net.oculus.postprocess.FinalPassRendererSourceTest --tests net.oculus.postprocess.CenterDepthSamplerSourceTest --tests net.oculus.gl.program.ProgramImagesTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live postprocess image writes, driver cleanup, framebuffer/copy/swap ordering, reload/fallback state, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around remaining image-barrier and custom-image reload behavior, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Color-Space Compute Image Cleanup

Slice:
Color-space compute conversion now clears active compute image state even when dispatch or memory-barrier work fails.

Active files:
- `src/main/java/net/oculus/colorspace/ColorSpaceComputeConverter.java`
- `src/test/java/net/oculus/colorspace/ColorSpaceShaderSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `ColorSpaceComputeConverter.process(...)` now tracks the primary dispatch/barrier failure.
- It resets `targetTexture` and calls `ComputeProgram.unbind()` from `finally`.
- Cleanup failures are suppressed onto primary failures or rethrown directly when cleanup is the only failure.

Verification:
- `test --tests net.oculus.colorspace.ColorSpaceShaderSourceTest --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.gl.OculusRenderSystemCapabilityDispatchTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live compute color-space output, driver cleanup, post-final state, reload/fallback state, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around custom-image resize/reload transactions or image barriers, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Relictium Override Creation Failure Cleanup

Slice:
Relictium terrain override creation now releases a linked GL program handle when later wrapper binding setup fails.

Active files:
- `src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramOverrides.java`
- `src/test/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramOverridesSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `createShader(...)` calls `deleteFailedProgram(...)` after runtime creation failures once a handle may have been linked, then keeps the existing missing-override fallback.
- Hard `Error` failures also clean up the handle, then rethrow so serious binding/setup failures are not hidden.
- Cleanup failures are suppressed on the original failure and logged.

Verification:
- `test --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramOverridesSourceTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramSourceTest --tests net.oculus.compat.relictium.RelictiumTerrainBridgeBytecodeTest --tests net.oculus.compat.relictium.RelictiumTerrainBindingAugmentationSourceTest --stacktrace --rerun-tasks` passed.
- That focused Gradle run executed `compileJava`.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live driver cleanup, Relictium fallback presentation, terrain sampler/image output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around runtime data/resource lifecycle, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Active Pack Reload Failure Fallback

Slice:
The no-config active-pack reload path now clears stale external shader state on reload failure.

Active files:
- `src/main/java/net/oculus/client/ShaderPackReloader.java`
- `src/test/java/net/oculus/client/ShaderPackReloaderTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `ShaderPackReloader.reloadActiveShaderPack()` now calls `disableShaders()` after catching `Exception | ZipError`.
- Failed active reloads keep the existing player failure message, return `false`, and switch `PipelineManager` to the internal pack instead of leaving the previous external pack active.
- The behavior matches the inspected 1.16.5 reload boundary where existing resources are destroyed before failed external loads fall back to disabled shaders.
- Focused tests pin the runtime fallback state and the corrupt-ZIP catch shape.

Verification:
- `test --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.config.OculusConfigTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.config.OculusConfigTest --tests net.oculus.gui.ShaderPackScreenSourceTest --tests net.oculus.client.OculusClientEventsSourceTest --tests net.oculus.texture.format.TextureFormatLoaderTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched source/test/docs, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live reload-key/resource-reload behavior, user-facing failure messaging, stale GL state cleanup, Relictium override refresh, custom resource/PBR sampler output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around remaining reload/resource lifecycle edges or move to client validation when allowed.

## 2026-05-18 - Agent 3 Terrain State Publication Ordering

Slice:
Shader-pack terrain state is now derived before pack/pipeline publication.

Active files:
- `src/main/java/net/oculus/pipeline/PipelineManager.java`
- `src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java`
- `src/test/java/net/oculus/pipeline/PipelineManagerSourceTest.java`
- `src/test/java/net/oculus/pipeline/ShaderWorldRenderingPipelineSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `PipelineManager.reloadShaderPack(...)` precomputes the next block-state ID map, render-layer override map, and entity ID function before assigning `activePack` or publishing `BlockRenderingSettings`.
- `ShaderWorldRenderingPipeline` computes those same derived terrain surfaces before constructing resource-owning managers.
- A malformed derived render-layer map now fails before a new external pack is published as active.
- Source tests pin the constructor ordering so derived terrain state remains ahead of shader resource manager construction.

Verification:
- `test --tests net.oculus.pipeline.PipelineManagerSourceTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.pipeline.BlockRenderingSettingsTest --stacktrace --rerun-tasks` passed after updating the source assertion for the new local precompute boundary.
- `test --tests net.oculus.pipeline.PipelineManagerSourceTest --tests net.oculus.pipeline.ShaderWorldRenderingPipelineSourceTest --tests net.oculus.pipeline.BlockRenderingSettingsTest --tests net.oculus.pipeline.FixedFunctionWorldRenderingPipelineTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramOverridesSourceTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramSourceTest --tests net.oculus.compat.relictium.RelictiumTerrainBridgeBytecodeTest --tests net.oculus.pipeline.SodiumTerrainPipelineTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked touched source/test/docs, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live malformed-pack recovery, chunk rebuild timing, Relictium material/render-layer/entity output, custom-resource/PBR terrain sampler output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around remaining reload/resource lifecycle edges, especially derived-state failure cleanup and resource reload behavior, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Entity ID Alias Lookup Coverage

Slice:
Entity ID runtime lookup now has an exact-first helper and direct tests for modern shader-pack alias fallback.

Active files:
- `src/main/java/net/oculus/uniforms/IdMapUniforms.java`
- `src/test/java/net/oculus/uniforms/IdMapUniformsTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `resolveEntityId(...)` now delegates exact registered entity lookup plus legacy fallback to `resolveEntityMappedId(...)`.
- Exact 1.12 names still win over aliases.
- Focused aliases now cover `evocation_fangs` -> `evoker_fangs` and `zombie_pigman` -> `zombified_piglin`, alongside the existing aliases for end crystal, iron golem, experience orb, command block minecart, firework rocket, snow golem, and illagers.
- Tests also guard that modern-only entities such as `glow_squid` and `piglin` are not mapped to unrelated 1.12 entities.

Verification:
- `test --tests net.oculus.uniforms.IdMapUniformsTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --tests net.oculus.shaderpack.preprocessor.PropertiesPreprocessorTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live `entityId` output, entity shadow rendering, and shader-visible behavior for target packs still need client validation.

Next exact check:
- Continue code-side audits around remaining image/resource/reload lifecycle edges, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Custom Image Replacement Dimension Publication

Slice:
Custom image resize/reload replacement now publishes cached framebuffer dimensions with the replacement texture map before old texture cleanup can throw.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomImageManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomImageManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `initializeOrResize(...)` now calls `replaceTextures(newTextures, resolvedWidth, resolvedHeight)`.
- `replaceTextures(...)` still registers every replacement paired sampler alias before publishing the new texture map, and registration failure rollback still restores old aliases and destroys only replacement textures.
- After replacement sampler registration succeeds, `replaceTextures(...)` now publishes `textures`, `framebufferWidth`, and `framebufferHeight` before `destroyTextureMap(oldTextures, true)` can report an old-cleanup failure.
- This closes the source-level partial-publication edge where replacement textures and sampler aliases could be live while cached dimensions stayed stale after old cleanup reported a failure.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.samplers.IrisImagesTest --tests net.oculus.samplers.IrisSamplersTest --tests net.oculus.texture.TextureInfoCacheTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.texture.format.TextureFormatLoaderTest --stacktrace --rerun-tasks` passed on rerun. The first attempt failed in `compileJava` on transient ForgeGradle generated-source missing-file errors; the generated files existed immediately after and no source change was made for that failure.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live custom-image writes, paired sampler reads, texture-size visibility, cleanup-failure presentation, resize/reload behavior, Relictium wrapped image/sampler output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around image barriers, custom-image/resource reload failure behavior, and Relictium wrapped sampler/image output, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Custom Image Destroy Dimension Reset

Slice:
Custom image teardown now resets cached framebuffer dimensions even when texture or paired-sampler cleanup reports a failure.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomImageManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomImageManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `CustomImageManager.destroy()` now wraps `destroyTextures()` in `try/finally`.
- `framebufferWidth` and `framebufferHeight` reset to `-1` from the `finally` block, so an aggregated cleanup failure from `destroyTextureMap(...)` cannot leave stale resize dimensions after owned texture state has been cleared.
- `CustomImageManagerTest#destroyResetsCachedDimensionsEvenWhenTextureCleanupThrows` source-pins that reset ordering.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.samplers.IrisImagesTest --tests net.oculus.samplers.IrisSamplersTest --tests net.oculus.texture.TextureInfoCacheTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.texture.format.TextureFormatLoaderTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live custom-image writes, paired sampler reads, cleanup-failure presentation, resize/reload behavior, Relictium wrapped image/sampler output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around image barriers, custom-image/resource reload failure behavior, and Relictium wrapped sampler/image output, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Generated Noise Resolution Overflow Guard

Slice:
Generated fallback `noisetex` allocation now rejects malformed pack resolutions before Java pixel-buffer length arithmetic can overflow.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `CustomTextureManager.defaultNoisePixelBufferSize(...)` computes `noiseTextureResolution * noiseTextureResolution * 4` with `long` arithmetic.
- Existing non-positive resolution behavior is preserved through `sanitizeNoiseTextureResolution(...)`, so `0` and negative values still use a 1x1 generated fallback.
- Oversized malformed resolutions now throw `IllegalArgumentException` before allocating or filling the pixel buffer.
- Normal target-pack behavior is unchanged: Complementary's generated fallback path uses `128`, the default remains `256`, and MakeUp supplies an explicit custom `noisetex` resource.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live generated-noise contents, malformed-resolution failure presentation, reload behavior, Relictium wrapped sampler output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around custom resource/PBR reload behavior, texture-size metadata, and Relictium terrain sampler/image output, or move to client validation when allowed.

## 2026-05-18 - Agent 3 PBR Texture-Format Mipmap Filter Detection

Slice:
LabPBR/non-interpolated PBR texture setup now preserves mipmapped min-filter intent.

Active files:
- `src/main/java/net/oculus/texture/format/TextureFormat.java`
- `src/test/java/net/oculus/texture/format/TextureFormatLoaderTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `TextureFormat.setupTextureParameters(...)` no longer uses the broken `(minFilter & 1 << 8) == 1` mipmap check.
- `TextureFormat.hasMipmappedMinFilter(...)` now identifies the four mipmapped GL min-filter enums by explicit range.
- Non-interpolated PBR textures keep `GL_NEAREST_MIPMAP_NEAREST` when they already use a mipmapped min filter, and use `GL_NEAREST` only for non-mipmapped min filters.
- Tests cover `GL_NEAREST`, `GL_LINEAR`, and all four mipmapped min-filter constants.

Verification:
- `test --tests net.oculus.texture.format.TextureFormatLoaderTest --tests net.oculus.gl.OculusRenderSystemCapabilityDispatchTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live PBR sampler output, custom resource `_n` / `_s` filtering, resource reload behavior, Relictium wrapped sampler output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around texture-size metadata and Relictium/custom-resource runtime sampler output, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Custom Image 3D Texture Metadata Coverage

Slice:
Complementary-style depth-backed custom images now publish source-level texture metadata like 2D custom images.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomImageManager.java`
- `src/main/java/net/oculus/texture/TextureInfoCache.java`
- `src/main/java/net/oculus/texture/TextureLifecycleTracker.java`
- `src/test/java/net/oculus/pipeline/texture/CustomImageManagerTest.java`
- `src/test/java/net/oculus/texture/TextureInfoCacheTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `TextureLifecycleTracker.onTexImage3D(...)` forwards explicit 3D allocation metadata into `TextureInfoCache`.
- `TextureInfoCache` stores explicit texture target, internal format, width, height, and depth for level-0 allocations.
- The active `glTexImage2D(...)` bridge remains limited to 2D/cubemap targets, avoiding a synthetic `GL_TEXTURE_3D` binding query from that path.
- `CustomImageManager.allocateTexture(...)` now calls the 3D lifecycle hook after successful `GL12.glTexImage3D(...)`.
- Tests pin direct 3D metadata retention, non-2D active bridge rejection, and 3D/2D custom-image metadata publication ordering.

Verification:
- `test --tests net.oculus.texture.TextureInfoCacheTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --stacktrace` passed after a transient unrelated `LanguageMap.class` compile access error cleared on rerun.
- `test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, and whitespace/conflict-marker scan passed.
- A combined focused run with all three patterns hit Gradle 4.9 `ClassNotFoundException` initialization/reporting noise for the edited tests despite successful test compilation; individual reruns passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live 3D custom-image contents, shader image writes, paired sampler reads, texture-size visibility, reload behavior, Relictium wrapped image/sampler output, and visual parity still need client validation.

Next exact check:
- Continue code-side audits around image/resource reload lifecycle and PBR/custom-resource output, or move to client validation when allowed.

## 2026-05-18 - Agent 3 Held Item ID Alias Safety Coverage

Slice:
Held/current item ID lookup now shares the exact-first alias helper shape and has stronger target-pack item-map tests.

Active files:
- `src/main/java/net/oculus/uniforms/IdMapUniforms.java`
- `src/test/java/net/oculus/uniforms/IdMapUniformsTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `resolveMappedId(...)` returns `-1` when the map or location is missing.
- `resolveItemMappedId(...)` and `resolveEntityMappedId(...)` now share `resolveAliasedMappedId(...)`, preserving exact-first lookup before alias fallback.
- The held-item alias set remains narrow: `lit_pumpkin` -> `jack_o_lantern` and `magma` -> `magma_block`.
- Tests pin exact target-pack item names that are already 1.12 registry names (`filled_map`, `sea_lantern`, `experience_bottle`, `end_crystal`) and reject modern-only names such as `lantern`, `campfire`, `shroomlight`, `crying_obsidian`, and separate `enchanted_golden_apple`.

Verification:
- `test --tests net.oculus.uniforms.IdMapUniformsTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, and whitespace/conflict-marker scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live held/current item IDs and held block light values still need client validation.

Next exact check:
- Continue code-side audits around remaining image/resource/reload lifecycle edges, or move to client validation when allowed.

## 2026-05-18 - Agent 3 PBR Holder Accessor Cleanup

Slice:
PBR holder teardown now keeps cleaning reachable companion textures when one holder accessor fails.

Active files:
- `src/main/java/net/oculus/texture/pbr/PBRTextureManager.java`
- `src/test/java/net/oculus/texture/pbr/PBRTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `PBRTextureManager.closeHolder(...)` resolves normal and specular companion textures in separate guarded calls.
- If one accessor fails, cleanup still attempts the other reachable companion texture before rethrowing.
- The shared cleanup failure collector now preserves duplicate same-instance failures instead of trying to add the throwable as suppressed to itself.
- Tests cover normal-failure/specular-cleanup, specular-failure/normal-cleanup, and the pre-existing same-throwable cleanup path.

Verification:
- `test --tests net.oculus.texture.pbr.PBRTextureManagerTest --stacktrace --rerun-tasks` passed after the collector fix.
- `test --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.texture.pbr.PBRAtlasTextureTest --tests net.oculus.texture.pbr.PBRAtlasSpriteTest --tests net.oculus.texture.pbr.loader.SimplePBRLoaderTest --tests net.oculus.texture.pbr.loader.AtlasPBRLoaderTest --tests net.oculus.texture.format.TextureFormatLoaderTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.gl.program.ProgramImagesTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live PBR sampler output, custom resource `_n` / `_s` reload behavior, Relictium wrapped sampler output, and rendered parity still need client validation.

## 2026-05-18 - Agent 3 Custom Texture Cleanup Suppression Guard

Slice:
Custom texture/noise cleanup now preserves original failures when the same throwable instance is reported twice.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomTextureManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `CustomTextureManager` now routes initialization rollback, previous texture-binding restoration, and destroy/unregister aggregation through `suppressFailure(...)`.
- Duplicate same-instance failures are preserved as the original throwable instead of producing Java self-suppression.
- Distinct cleanup failures still attach as suppressed context.
- Tests behavior-check duplicate and distinct collection cases and source-pin helper use.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomTextureManagerTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.gl.program.ProgramImagesTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live `texture.*`, `customTexture.*`, generated/custom noise, resource reload behavior, Relictium wrapped sampler output, and rendered parity still need client validation.

## 2026-05-18 - Agent 3 Custom Image Cleanup Suppression Guard

Slice:
Custom-image cleanup now preserves original failures when the same throwable instance is reported twice.

Active files:
- `src/main/java/net/oculus/pipeline/texture/CustomImageManager.java`
- `src/test/java/net/oculus/pipeline/texture/CustomImageManagerTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `CustomImageManager` now routes restore cleanup, frame-clear aggregation, replacement-registration rollback, paired sampler unregister, and owned texture destroy aggregation through `suppressFailure(...)`.
- Duplicate same-instance failures are preserved as the original throwable instead of producing Java self-suppression.
- Distinct cleanup failures still attach as suppressed context.
- Tests behavior-check duplicate and distinct collection cases and source-pin helper use.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.gl.program.ProgramImagesTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live `image.*` writes, paired sampler reads, resize/reload behavior, Relictium wrapped image/sampler output, and rendered parity still need client validation.

## 2026-05-18 - Agent 3 Active Binding Cleanup Suppression Guard

Slice:
Active uniform/sampler/image/program cleanup now preserves original failures when the same throwable instance is reported twice.

Active files:
- `src/main/java/net/oculus/gl/program/Program.java`
- `src/main/java/net/oculus/gl/program/ProgramUniforms.java`
- `src/main/java/net/oculus/gl/program/ProgramSamplers.java`
- `src/main/java/net/oculus/gl/program/ProgramImages.java`
- `src/test/java/net/oculus/gl/program/ProgramActivationCleanupSourceTest.java`
- `src/test/java/net/oculus/gl/program/ProgramUniformsTest.java`
- `src/test/java/net/oculus/gl/program/ProgramSamplersTest.java`
- `src/test/java/net/oculus/gl/program/ProgramImagesTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `ProgramUniforms`, `ProgramSamplers`, `ProgramImages`, and `Program` route cleanup suppression through guarded helpers.
- Duplicate same-instance failures preserve the original throwable instead of producing Java self-suppression.
- Distinct cleanup failures still attach as suppressed context.
- Tests behavior-check duplicate and distinct suppression for uniforms, samplers, images, and the outer program cleanup helper.

Verification:
- `test --tests net.oculus.gl.program.ProgramUniformsTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.gl.program.ProgramActivationCleanupSourceTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.gl.program.ProgramUniformsTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.gl.program.ProgramActivationCleanupSourceTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramSourceTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live activation failures, reload/fallback cleanup, compute/postprocess cleanup, Relictium wrapped-program teardown, and shader-visible state after failures still need client validation.

## 2026-05-18 - Agent 3 Relictium Cleanup Suppression Guard

Slice:
Relictium terrain override/link/delete cleanup now preserves original failures when the same throwable instance is reported twice.

Active files:
- `src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramOverrides.java`
- `src/main/java/net/oculus/compat/relictium/OculusRelictiumProgramLinker.java`
- `src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgram.java`
- `src/test/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramOverridesSourceTest.java`
- `src/test/java/net/oculus/compat/relictium/OculusRelictiumProgramLinkerSourceTest.java`
- `src/test/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `OculusRelictiumChunkProgramOverrides`, `OculusRelictiumProgramLinker`, and `OculusRelictiumChunkProgram` route cleanup suppression through guarded helpers.
- Duplicate same-instance failures preserve the original throwable instead of producing Java self-suppression.
- Distinct cleanup failures still attach as suppressed context.
- Tests behavior-check duplicate and distinct suppression in all three Relictium cleanup helpers.

Verification:
- `test --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramOverridesSourceTest --tests net.oculus.compat.relictium.OculusRelictiumProgramLinkerSourceTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramSourceTest --stacktrace --rerun-tasks` passed.
- An adjacent Relictium/source binding run first hit a Gradle 4.9 XML test-result write EOF, then `cleanTest test --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramOverridesSourceTest --tests net.oculus.compat.relictium.OculusRelictiumProgramLinkerSourceTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramSourceTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramOverridesVersionTest --tests net.oculus.compat.relictium.RelictiumTerrainBridgeBytecodeTest --tests net.oculus.pipeline.SodiumTerrainPipelineTest --tests net.oculus.gl.program.ProgramUniformsTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.gl.program.ProgramActivationCleanupSourceTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live Relictium backend deletion/recreate, shader reload/fallback recovery, terrain override failure presentation, sampler/image/PBR binding state, and rendered terrain still need client validation.

Latest continuation note:
- The texture/PBR helper cleanup suppression slice below is the latest Agent 3 code-side change in this handoff. Continue from remaining direct custom texture/custom image cleanup suppression source-shape cleanups, broader PBR/custom-resource reload lifecycle audits, Relictium sampler/image output, or client validation when allowed.

## 2026-05-18 - Agent 3 Texture Metadata Lifecycle Suppression Guard

Slice:
Texture-size metadata query restore and texture lifecycle delete invalidation now preserve original failures when the same throwable instance is reported twice.

Active files:
- `src/main/java/net/oculus/texture/TextureInfoCache.java`
- `src/main/java/net/oculus/texture/TextureLifecycleTracker.java`
- `src/test/java/net/oculus/texture/TextureInfoCacheTest.java`
- `src/test/java/net/oculus/texture/TextureLifecycleTrackerSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `TextureInfoCache.TextureInfo` routes live metadata query restore suppression through a guarded helper.
- `TextureLifecycleTracker` routes texture delete invalidation suppression through a guarded helper.
- Duplicate same-instance failures preserve the original throwable instead of producing Java self-suppression.
- Distinct restore/delete-notification failures still attach as suppressed context.
- Tests behavior-check duplicate and distinct suppression for both helpers.

Verification:
- `test --tests net.oculus.texture.TextureInfoCacheTest --tests net.oculus.texture.TextureLifecycleTrackerSourceTest --tests net.oculus.texture.TextureTrackerTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.texture.TextureInfoCacheTest --tests net.oculus.texture.TextureLifecycleTrackerSourceTest --tests net.oculus.texture.TextureTrackerTest --tests net.oculus.uniforms.GameplayUniformsTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.gl.program.ProgramUniformsTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live `atlasSize` / `gtextureSize`, resource reload cleanup, driver delete failures, PBR sampler output, Relictium wrapped sampler metadata, and rendered output still need client validation.

## 2026-05-18 - Agent 3 PBR Cleanup Suppression Guard

Slice:
PBR notifier, holder-load cleanup, default texture restore, atlas companion update, per-sprite update, and atlas binding-restore cleanup now preserve original failures when the same throwable instance is reported twice.

Active files:
- `src/main/java/net/oculus/texture/pbr/PBRTextureManager.java`
- `src/main/java/net/oculus/texture/pbr/PBRAtlasHolder.java`
- `src/main/java/net/oculus/texture/pbr/PBRAtlasTexture.java`
- `src/test/java/net/oculus/texture/pbr/PBRTextureManagerTest.java`
- `src/test/java/net/oculus/texture/pbr/PBRAtlasTextureTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `PBRTextureManager` now uses guarded cleanup collection for texture-change notification fan-out, holder-load cleanup, previous-binding restore, and default single-color texture restore.
- `PBRAtlasHolder` now uses guarded collection for normal/specular atlas animation update fan-out.
- `PBRAtlasTexture` now guards per-sprite update collection and atlas binding restore.
- Duplicate same-instance failures preserve the original throwable instead of producing Java self-suppression.
- Distinct cleanup/update failures still attach as suppressed context.

Verification:
- `test --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.texture.pbr.PBRAtlasTextureTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.texture.pbr.PBRAtlasTextureTest --tests net.oculus.texture.pbr.loader.AtlasPBRLoaderTest --tests net.oculus.texture.pbr.loader.SimplePBRLoaderTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.texture.TextureLifecycleTrackerSourceTest --tests net.oculus.texture.TextureInfoCacheTest --tests net.oculus.gl.program.ProgramSamplersTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live PBR sampler output, animated atlas contents, resource reload behavior, driver cleanup, Relictium wrapped PBR sampler state, and rendered output still need client validation.

## 2026-05-18 - Agent 3 Texture/PBR Helper Cleanup Suppression Guard

Slice:
Fallback texture setup restore, texture-format parameter restore, simple PBR companion cleanup, and atlas-size query restore now preserve original failures when the same throwable instance is reported twice.

Active files:
- `src/main/java/net/oculus/texture/FallbackTextures.java`
- `src/main/java/net/oculus/texture/format/TextureFormat.java`
- `src/main/java/net/oculus/texture/pbr/loader/SimplePBRLoader.java`
- `src/main/java/net/oculus/texture/pbr/loader/AtlasPBRLoader.java`
- `src/test/java/net/oculus/texture/FallbackTexturesTest.java`
- `src/test/java/net/oculus/texture/format/TextureFormatLoaderTest.java`
- `src/test/java/net/oculus/texture/pbr/loader/SimplePBRLoaderTest.java`
- `src/test/java/net/oculus/texture/pbr/loader/AtlasPBRLoaderTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `FallbackTextures`, `TextureFormat`, `SimplePBRLoader`, and `AtlasPBRLoader` route restore/cleanup suppression through guarded helpers.
- Duplicate same-instance failures preserve the original setup/load/query throwable instead of producing Java self-suppression.
- Distinct restore/cleanup failures still attach as suppressed context.
- Tests behavior-check duplicate and distinct suppression for fallback/format/atlas helpers and same-instance simple PBR cleanup paths.

Verification:
- `test --tests net.oculus.texture.FallbackTexturesTest --tests net.oculus.texture.format.TextureFormatLoaderTest --tests net.oculus.texture.pbr.loader.SimplePBRLoaderTest --tests net.oculus.texture.pbr.loader.AtlasPBRLoaderTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.texture.FallbackTexturesTest --tests net.oculus.texture.format.TextureFormatLoaderTest --tests net.oculus.texture.pbr.loader.SimplePBRLoaderTest --tests net.oculus.texture.pbr.loader.AtlasPBRLoaderTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.texture.pbr.PBRAtlasTextureTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.texture.TextureLifecycleTrackerSourceTest --tests net.oculus.texture.TextureInfoCacheTest --tests net.oculus.gl.program.ProgramSamplersTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. Live fallback sampler output, texture-format filtering, simple `_n` / `_s` companion output, atlas-size query behavior, resource reload cleanup, Relictium wrapped sampler state, and rendered output still need client validation.

## 2026-05-18 - Agent 3 Source-Side Runtime Data Binding Audit Pass

Slice:
Follow-up code audit after the config reload guard. No source files were changed in this slice; the result is source/test evidence and updated docs only.

Inspected surfaces:
- Built-in uniform registration and `ProgramBuilderReferenceUniformCoverageTest`.
- Custom uniform expression target-pack coverage.
- `ProgramSamplers`, `ProgramImages`, and image fail-fast behavior.
- `CustomTextureManager`, including resource `_n` / `_s` indirection and generated noise fallback.
- `CustomImageManager`, including 2D/3D allocation, paired sampler bindings, rollback, and resize behavior.
- `PBRTextureManager`, atlas cleanup, texture-format reload, and texture-size metadata paths.
- Relictium terrain wrappers, vertex writers, block context, and bytecode/source guard tests.
- Config reload and GUI apply/reload state.

Verification:
- `test --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.gl.program.ProgramImagesTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.texture.pbr.PBRAtlasTextureTest --tests net.oculus.texture.format.TextureFormatLoaderTest --tests 'net.oculus.compat.relictium.*' --tests 'net.oculus.pipeline.vertex.*' --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.config.OculusConfigTest --tests net.oculus.gui.ShaderPackScreenSourceTest --stacktrace --rerun-tasks` passed.

Notes:
- The requested `../Relictium` source checkout is not present in this workspace, so Relictium certainty is limited to local compatibility code and installed bytecode/source tests.
- No new patch-worthy source gap was found in this pass.
- Runtime-unverified. Minecraft and `runClient` were not run. Do not claim visual/runtime parity or final completion.

## 2026-05-18 - Agent 3 Custom Matrix Expression Timing

Changed:
- `CustomUniformExpressionManager.BuiltinSymbols.isDynamic(...)` now includes all matrix aliases already resolved from captured gbuffer state, shadow state, current GL state, normal-matrix state, and lightmap-matrix state.
- Added `CustomUniformExpressionManagerTest#capturedAndShadowMatrixAliasesAreDynamicCustomExpressionDependencies`.

Why:
- These symbols are per-frame upload-time sources in `ProgramBuilder` / the local 1.16.5 matrix-uniform reference. Leaving them non-dynamic let custom `uniform.*` / `variable.*` expressions cache values during `beginFrame()` before the current terrain, shadow, or postprocess program upload.

Verification:
- `test --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace --rerun-tasks` passed after this and the hand-light directive slice.
- Standalone `compileJava --stacktrace --rerun-tasks` passed after this and the hand-light directive slice.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed after this and the hand-light directive slice.

Still required:
- Runtime-unverified: live matrix values and custom uniform timing still need in-client validation.

## 2026-05-18 - Agent 3 Custom Held Hand-Light Directive Capture

Changed:
- `CustomUniformExpressionManager` now stores the `oldHandLight` value from the `ShaderProperties` used to compile custom expressions.
- `heldBlockLightValue` custom-expression built-in lookup now calls `IdMapUniforms.getHeldBlockLightValueMain(oldHandLight)`.
- Added `CustomUniformExpressionManagerTest#heldBlockLightCustomExpressionCapturesOldHandLightDirective`.

Why:
- Direct uniforms already capture `PackDirectives.isOldHandLight()` when the program is built. Custom expressions were using `IdMapUniforms.getHeldBlockLightValueMain()` with an active-pack lookup, which could drift during reload/fallback overlap.

Verification:
- `test --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed.

Still required:
- Runtime-unverified: live held-item light output and custom uniform timing still need in-client validation.

## 2026-05-18 - Agent 3 Held Item ID-Map Capture

Changed:
- `IdMapUniforms` now has held-item ID lookup overloads that accept a captured item ID map.
- `CustomUniformExpressionManager.fromShaderPack(pack)` stores `pack.getIdMap().getItemIdMap()` and returns a manager carrying that state even when the pack has no custom expression directives.
- `ProgramBuilder` now binds `heldItemId` and `heldItemId2` through the captured manager state.
- Custom expressions reading `heldItemId` / `heldItemId2` use that same captured item map.
- `ShaderWorldRenderingPipeline` now creates custom/runtime uniform state with `CustomUniformExpressionManager.fromShaderPack(pack)`.

Why:
- The inspected local 1.16.5 `IdMapUniforms.addIdMapUniforms(...)` creates `HeldItemSupplier` instances with `idMap.getItemIdMap()`. The 1.12 port was resolving held item IDs through `PipelineManager.INSTANCE.getActivePack()` at value time, which could drift during reload/fallback overlap.

Verification:
- `test --tests net.oculus.uniforms.IdMapUniformsTest --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed.

Still required:
- Runtime-unverified: live held-item ID output, item render timing, and `currentRenderedItemId` behavior still need in-client validation.

## 2026-05-18 - Agent 3 Current Rendered Item ID-Map Capture

Changed:
- `IdMapUniforms` now stores the current rendered `ItemStack` instead of a precomputed active-pack item ID.
- Added `IdMapUniforms.getCurrentRenderedItemId(itemIdMap)` so each consuming binding resolves the stored stack with its captured item map.
- `ProgramBuilder` now binds `currentRenderedItemId` through `CustomUniformExpressionManager`.
- Custom expressions reading `currentRenderedItemId` use the same captured item map.

Why:
- After held item IDs were moved to captured item maps, `currentRenderedItemId` was still computed in `RenderItemMixin` through the active pack. That could drift from the program/expression set consuming the value during reload/fallback overlap.

Verification:
- `test --tests net.oculus.uniforms.IdMapUniformsTest --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file `git diff --check`, no-index diff-check for untracked docs/source/tests, line-start conflict-marker scan, and trailing-whitespace scan passed.

Still required:
- Runtime-unverified: live item-render hook timing and shader-visible `currentRenderedItemId` still need in-client validation.

## 2026-05-19 - Agent 3 Quartz Pillar Material-ID Mapping

Changed:
- `BlockMaterialMapping` now handles `quartz_pillar` as a multi-state 1.12 alias before generic block lookup.
- Broad `quartz_pillar` entries map all 1.12 `quartz_block` pillar variants: `lines_x`, `lines_y`, and `lines_z`.
- Explicit `quartz_pillar:axis=x|y|z` entries map only the matching variant and remove the unsupported modern `axis` predicate.
- Added synthetic block-material tests and a real Complementary loader guard for row `10364`.

Why:
- Complementary groups `quartz_pillar` with quartz block material ID `10364`. The prior single-entry resolver mapped broad `quartz_pillar` only to `lines_y`; because broad `quartz_block` is constrained to the default variant when split aliases are present, `lines_x` and `lines_z` could remain unmapped.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --stacktrace --rerun-tasks` passed.
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.
- Touched-file diff checks and whitespace/conflict-marker scans passed after the later direct-shadow documentation update.

Still required:
- Runtime-unverified: live Relictium/fallback terrain `blockId` values and rendered quartz material branches still need in-client validation.

## 2026-05-19 - Agent 3 Direct Shadow Binding Fail-Fast

Changed:
- `ShadowMap.applySamplerBindings(...)` now builds a `ShadowSamplerBindings.ResourceQuery` before constructing texture bindings.
- Unsupported active shadow resources throw the same reference-limit diagnostic used by `ShadowMap.requireShadowTargets(...)`.
- Programs with no active shadow target resources still return `false`.
- Active shadow resources on disabled/unavailable targets throw before depth/color texture bindings are built.
- Added `ShadowMapTextureStateTest#directShadowSamplerBindingPathFailsUnsupportedResourcesBeforeBinding` to source-pin the ordering.

Why:
- Shader-loader, composite/final, and Relictium terrain paths already call `ShadowMap.requireShadowTargets(...)` before applying shadow bindings.
- The direct `ShadowRenderer` helper path calls `ShadowMap.applySamplerBindings(...)` directly, so it needed the same fail-fast guard to avoid silently leaving active unsupported shadow sampler/image resources unbound.

Verification:
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.
- `test --tests net.oculus.pipeline.shadow.ShadowMapTextureStateTest --tests net.oculus.pipeline.shadow.ShadowSamplerBindingsTest --stacktrace --rerun-tasks` passed with Java 8.
- The broader runtime-data selected-test coverage was rerun in split groups and passed:
- `ProgramBuilderReferenceUniformCoverageTest`, `ProgramSamplersTest`, `ProgramImagesTest`, `IrisSamplersTest`, `IrisImagesTest`, `ShadowMapTextureStateTest`, and `ShadowSamplerBindingsTest`.
- `CustomTextureManagerTest`, `CustomImageManagerTest`, `PBRTextureManagerTest`, `PBRAtlasTextureTest`, and `TextureFormatLoaderTest`.
- `net.oculus.compat.relictium.*` and `net.oculus.pipeline.vertex.*`.
- `ShaderPackReloaderTest`, `OculusConfigTest`, `ShaderPackScreenSourceTest`, `BlockMaterialMappingTest`, and `ShaderPackLoaderComplementaryTest`.
- A single monolithic selected-test command compiled and reached JUnit report generation, then failed in Gradle 4.9 XML report writing with `EOFException` / Kryo buffer underflow from `TestOutputStore`; no JUnit assertion failure was reported.
- Touched-file `git diff --check`, no-index diff-check for untracked touched files, and whitespace/conflict-marker scans passed.

Still required:
- Runtime-unverified: live shadow sampler/image contents, reload behavior, Relictium shadow terrain output, and rendered shadows still need in-client validation.

## 2026-05-19 - Agent 3 Built-In Time And Hand-Light Evidence

Changed:
- Added `SystemTimeUniformsTest` for direct `frameCounter`, `frameTime`, and `frameTimeCounter` semantics.
- Strengthened `ProgramBuilderReferenceUniformCoverageTest` so the inspected 1.16.5 offhand supplier split is pinned and the 1.12 `heldBlockLightValue2` direct/custom uniform path stays offhand-only.

Why:
- The built-in uniform surface was already implemented, but this timing and offhand-light behavior was only indirectly covered by broader custom-expression tests and source scanning.
- The new tests improve source confidence without claiming runtime parity.

Verification:
- `test --tests net.oculus.uniforms.SystemTimeUniformsTest --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace --rerun-tasks` passed.

Still required:
- Runtime-unverified: live frame pacing, first-frame timer ordering, held-item lighting, shader-visible values, and visual output still need in-client validation.

## 2026-05-19 - Agent 3 Uniform Minecraft Singleton Lookup

Changed:
- `WorldInfoUniforms` no longer stores `Minecraft.getMinecraft()` in a static field.
- `CompatibilityUniforms` no longer stores `Minecraft.getMinecraft()` in a static field.
- World, camera, and player lookups now happen through per-call helpers with null-safe fallbacks.
- Added source guards to `WorldInfoUniformsTest` and `CompatibilityUniformsTest`.

Why:
- These helpers can be loaded before the live client singleton is available through headless tests, early startup paths, or shader-pack/runtime binding setup.
- Caching a null singleton would permanently default world-info values, and compatibility player-state helpers could null-crash instead of returning fallback values.

Verification:
- `test --tests net.oculus.uniforms.WorldInfoUniformsTest --tests net.oculus.uniforms.CompatibilityUniformsTest --tests net.oculus.uniforms.SpecialEffectUniformsTest --stacktrace --rerun-tasks` passed.

Still required:
- Runtime-unverified: live dimension/world-info values, weather and biome smoothing, player-state compatibility uniforms, and shader-visible custom-expression output still need in-client validation.

## 2026-05-19 - Agent 3 Complementary Runtime Uniform Source Guard

Changed:
- `ProgramBuilderReferenceUniformCoverageTest` now reads the local Complementary Reimagined `lib/uniforms.glsl` when present.
- The test verifies the target-pack runtime uniforms `cameraPositionInt`, `previousCameraPositionInt`, `cameraPositionFract`, `previousCameraPositionFract`, `framemod2`, `framemod4`, `heavyFog`, and `maxBlindnessDarkness` still have `ProgramBuilder` registration cases.

Why:
- The previous reference-uniform guard covered the active 1.16.5 list, but the current Complementary target pack also exercises high-value 1.12 runtime names and hardcoded/custom fallback names.
- This is a source/test guard so those names are not silently dropped during later uniform registration refactors.

Verification:
- `test --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Still required:
- Runtime-unverified: live values, custom-expression upload timing, Relictium wrapped-program values, and rendered output still need in-client validation.

## 2026-05-19 - Agent 3 Custom Image Sampler And PBR Resource Guards

Changed:
- `CustomImageManagerTest` now pins that `CustomImageManager.applyToProgram(...)` keeps paired sampler binding outside the `builder.hasImage(...)` guard.
- `ProgramSamplersTest` now proves sampler-only custom image uniforms receive a dynamic unit through `overrideBinding(...)`.
- `CustomTextureManagerTest` now proves multi-dot `_n` / `_s` resource paths such as `stone.v2_n.png` resolve through base `stone.v2.png`.
- `TextureFormatLoaderTest` now proves `PBRType.appendToFileLocation(...)` appends suffixes before the final extension on multi-dot names.

Why:
- Real shader programs can read a custom image through the paired sampler without declaring writable image access in that specific program.
- Resource custom texture paths can have dotted base names; `_n` / `_s` handling must preserve the base name while resolving the live base texture for PBR holders.

Verification:
- `test --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.texture.format.TextureFormatLoaderTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Still required:
- Runtime-unverified: live custom image writes, paired sampler reads, custom resource reload behavior, PBR texture parameters/output, Relictium wrapped-program sampler/image output, and rendered visual parity still need in-client validation.

## 2026-05-19 - Agent 3 Complementary Comparator Material IDs

Changed:
- `BlockMaterialMapping.resolvePoweredRedstoneBlockEntry(...)` now consumes the modern `powered` predicate for `comparator` entries after selecting Forge 1.12's `powered_comparator` or `unpowered_comparator`.
- Comparator `mode=compare|subtract` predicates are retained for 1.12 state matching.
- Added synthetic block-material coverage and a real Complementary loader guard for rows `block.10644`, `block.10645`, and `block.10646`.

Why:
- Complementary declares both split 1.12 comparator names and modern flattened `comparator:mode=...:powered=...` rows.
- Leaving `powered` in the resolved predicate relies on the unknown-property path ignoring it; consuming it makes the material-ID state match exact and aligns comparator handling with repeater split handling.

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Still required:
- Runtime-unverified: live Relictium/fallback terrain `blockId` attributes, shadow terrain material IDs, chunk rebuild actual-state delivery, and rendered comparator material branches still need in-client validation.

## 2026-05-19 - Agent 3 SSBO Bind Refresh Restore

Changed:
- `ShaderStorageBufferManager.bindAll()` now captures `GL_SHADER_STORAGE_BUFFER_BINDING` before rebinding indexed SSBO slots.
- The previous generic SSBO binding is restored in `finally` after `glBindBufferBase(...)` refreshes.
- Added `ShaderStorageBufferManagerTest#bindAllRestoresPreviousGenericSsboBindingAfterIndexedBindings`.

Why:
- The allocation path already restored the caller's generic SSBO binding after upload/clear/bind-base setup.
- `bindAll()` is called repeatedly around world, composite, final, and post-compute work; leaving the generic binding on the last indexed buffer was an avoidable state leak for `bufferObject.*` custom resources.

Verification:
- `test --tests net.oculus.pipeline.buffer.ShaderStorageBufferManagerTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Still required:
- Runtime-unverified: live SSBO allocation, shader-visible buffer contents, driver binding behavior, reload/fallback behavior, and rendered output still need in-client validation.

## 2026-05-19 - Agent 3 GUI Invalid Stored-Pack Recovery Save Boundary

Changed:
- `ShaderPackScreen.preloadSelectedPack(...)` now stores the invalid configured pack name before clearing selection state.
- The automatic invalid-pack cleanup captures a config snapshot, clears the invalid pack's option overrides and selected pack, and persists that recovery.
- `persistSelectedPack(...)` now returns save success.
- If saving the recovery fails, the screen restores the config snapshot, restores the invalid selected-pack name, and displays the config-save failure notification.
- `ShaderPackScreenSourceTest#invalidStoredSelectionClearsOverridesAndPersistsRecovery` pins the ordering.

Why:
- Previously the screen cleared in-memory config/selection state even if `oculus.properties` still contained the invalid selected pack because the save failed.
- That could make GUI state imply reload recovery had persisted while the next public reload would still read stale disk state and rebuild runtime binding surfaces from it.

Verification:
- `test --tests net.oculus.gui.ShaderPackScreenSourceTest --tests net.oculus.config.OculusConfigTest --tests net.oculus.client.ShaderPackReloaderTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Still required:
- Runtime-unverified: live GUI notification behavior, disk save failure handling, reload-key follow-up, Relictium wrapper refresh, and rendered output still need in-client validation.

## 2026-05-19 - Agent 3 Relictium Terrain Scope Cleanup

Slice:
Relictium backend terrain scope cleanup is now best-effort across begin failure, wrapped program unbind failure, pipeline lookup failure, terrain-scope end, and previous-phase restore.

Active files:
- `src/main/java/net/oculus/mixin/pipeline/RelictiumChunkRenderShaderBackendMixin.java`
- `src/test/java/net/oculus/compat/relictium/RelictiumChunkRenderShaderBackendMixinSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/gl/program/ProgramSamplers.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/gl/program/ProgramImages.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/vertices/`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/block_rendering/`

Implemented behavior:
- `oculus$begin(...)` catches `RuntimeException` and `Error`, attempts terrain-scope cleanup, and rethrows the original failure.
- `oculus$endTerrainScope()` clears local override/pass/phase state before teardown calls can throw.
- Program unbind, pipeline lookup, `endSodiumTerrainRendering()`, previous phase restore, and previous-phase reset are attempted independently.
- Cleanup aggregation preserves the original failure and skips same-throwable suppression.

Verification:
- `test --tests net.oculus.compat.relictium.RelictiumChunkRenderShaderBackendMixinSourceTest --tests net.oculus.compat.relictium.RelictiumTerrainBridgeBytecodeTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramSourceTest --tests net.oculus.compat.relictium.OculusRelictiumChunkProgramOverridesSourceTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. This does not prove live backend cleanup timing, active-program override restoration, sampler/image/PBR state, reload behavior, or rendered terrain.

Next exact check:
- Run a Relictium-enabled client reload/failure scenario with Complementary terrain and shadow terrain, then verify terrain scope, world rendering phase, active wrapped program, sampler/image/PBR bindings, and rendered terrain recover after backend rebuild.

## 2026-05-19 - Agent 3 Pipeline Teardown Suppression Guard

Slice:
Pipeline reload/fallback cleanup now preserves original teardown failures when duplicate same-instance cleanup reports occur.

Active files:
- `src/main/java/net/oculus/pipeline/PipelineManager.java`
- `src/test/java/net/oculus/pipeline/PipelineManagerSourceTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

1.16.5 references:
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/Iris.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/pipeline/PipelineManager.java`

Implemented behavior:
- `PipelineManager.runTeardownStep(...)` now routes cleanup failure aggregation through `suppressCleanupFailure(...)`.
- The helper ignores same throwable instances and suppresses distinct cleanup failures.
- This applies to `destroyPipeline()`, cached pipeline replacement in `preparePipeline(...)`, and first-frame `activateRuntimeFallback(...)` cleanup because they all use `runTeardownStep(...)`.

Verification:
- `test --tests net.oculus.pipeline.PipelineManagerSourceTest --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.config.OculusConfigTest --tests net.oculus.gui.ShaderPackScreenSourceTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. This does not prove live reload/fallback behavior, Relictium override refresh, shader-visible binding state, or rendered output.

Next exact check:
- In-client reload-key, GUI Apply, shader toggle, failed-pack fallback, and Relictium terrain rebuild validation should confirm no stale active programs, sampler/image/PBR bindings, terrain material maps, or override versions survive cleanup trouble.

## 2026-05-19 - Agent 3 Startup Config Refresh Guard

Slice:
Startup configured-pack apply now refreshes config before reading global selected-pack state.

Active files:
- `src/main/java/net/oculus/client/ShaderPackReloader.java`
- `src/test/java/net/oculus/client/ShaderPackReloaderTest.java`
- `docs/uniform-gap-analysis.md`
- `docs/relictium-integration.md`
- `docs/directive-support.md`
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md`

Implemented behavior:
- `ShaderPackReloader.applyConfiguredShaderPack()` calls `reloadConfig(config)` when global config exists.
- If that config refresh fails, startup apply prepares any already-loaded world pipeline fallback and returns `false` before resolving shaderpacks or applying the configured pack.
- The scoped `applyConfiguredShaderPack(OculusConfig, Path)` overload remains unchanged for in-memory config tests.
- `ShaderPackReloaderTest#publicStartupApplyRefreshesConfigBeforeUsingConfiguredPack` pins the source order.

Verification:
- `test --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.config.OculusConfigTest --tests net.oculus.client.OculusClientEventsSourceTest --tests net.oculus.gui.ShaderPackScreenSourceTest --stacktrace --rerun-tasks` passed.
- Standalone `compileJava --stacktrace --rerun-tasks` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run. This does not prove live first-tick startup behavior, config failure presentation, Relictium override refresh, shader-visible binding state, or rendered output.

Next exact check:
- In-client startup with a configured pack plus forced config load/create failure should confirm stale selected-pack state does not rebuild uniforms, samplers, images, custom resources, PBR resources, or Relictium terrain wrappers.

## 2026-05-19 - Agent 3 Runtime Binding Code-Side Audit

Status:
- Code-side audit completed after the latest runtime-binding/config/Relictium cleanup slices.
- No new patch-worthy source-visible gap was found in the audited paths.
- No source code files were changed in this audit pass; this entry records verification and handoff state only.

Inspected:
- `ProgramSamplers`, `ProgramImages`, `IrisSamplers`, `IrisImages`
- `CustomTextureManager`, `CustomImageManager`, PBR managers/loaders, texture-format metadata
- `CustomUniformExpressionManager`, `ProgramBuilder` registration/test coverage
- Relictium terrain wrapped-program binding and cleanup tests
- Config persistence/reload, GUI Apply/recovery, and startup configured-pack apply
- Complementary and MakeUp custom resource declarations in `run/shaderpacks/`

Verification:
- Focused runtime-data suite passed for custom texture/image, program sampler/image, Iris sampler/image, custom uniform expressions, PBR, texture format, Relictium source/bytecode, config, reloader, and GUI source tests.
- Standalone `compileJava` passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim visual/runtime parity. The remaining 100% blocker is live validation of shader-visible values and resource contents, especially PBR output, Relictium terrain/shadow output, reload transitions, and GUI selected-pack behavior.

Recommended next work:
- Start with in-client validation rather than more broad source churn unless a new source discrepancy is found.
- Validate Complementary and MakeUp with Relictium enabled, exercise reload key and GUI Apply/Cancel/Escape, and include the Oculus PBR validation resource pack when present.

## 2026-05-19 - Agent 3 Runtime Binding Verification Refresh

Status:
- Follow-up source/test verification completed after the code-side audit.
- No new patch-worthy source-visible gap was found.
- No source code files were changed in this refresh; only documentation was updated.

Inspected:
- `ShaderLoader` availability-specific world/root-shadow binding behavior
- `ProgramBuilder` active built-in uniform coverage
- Program and Iris sampler/image binding helpers
- Custom texture/image managers
- PBR manager and texture-format reload paths
- Relictium wrapped-program source and bytecode contracts
- Config reload, reloader state, and shader-pack GUI selected-pack behavior

Verification:
- Focused runtime-data suite passed with Java 8 for `ShaderLoaderSourceTest`, `ProgramBuilderReferenceUniformCoverageTest`, sampler/image tests, custom texture/image tests, PBR and texture-format tests, Relictium source/bytecode tests, config, reloader, and GUI source tests.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim visual/runtime parity. The remaining 100% blocker is live validation of shader-visible values and resource contents, especially PBR output, Relictium terrain/shadow output, reload transitions, and GUI selected-pack behavior.

Recommended next work:
- Move to in-client validation unless a newly discovered source discrepancy appears.
- Use Complementary and MakeUp with Relictium enabled, exercise startup, reload key, GUI Apply/Cancel/Escape, F3+T/resource reload, and the PBR validation resource pack when present.

## 2026-05-19 - Agent 3 Target-Pack Uniform And Material Audit Refresh

Status:
- Follow-up code-side audit completed for target-pack custom expressions, active built-in uniform declarations, and Complementary material rows.
- No new patch-worthy source-visible gap was found.
- No source code files were changed in this refresh; only documentation was updated.

Inspected:
- `CustomUniformExpressionManager` and its Complementary/MakeUp tests
- `ProgramBuilder` and active built-in uniform coverage tests
- `BlockMaterialMapping` and Complementary loader material-ID guards
- Complementary `lib/uniforms.glsl` and `block.properties`
- MakeUp `shaders.properties` and `block.properties`

Verification:
- `test --tests net.oculus.blockrendering.BlockMaterialMappingTest --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --tests net.oculus.uniforms.custom.CustomUniformExpressionManagerTest --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim visual/runtime parity. The remaining 100% blocker is live validation of shader-visible values, material IDs, resource contents, reload behavior, GUI transitions, and Relictium terrain/shadow output.

Recommended next work:
- Stop broad source churn unless a new source discrepancy appears.
- Start in-client validation with Complementary and MakeUp, Relictium enabled, reload key, GUI Apply/Cancel/Escape, F3+T/resource reload, and the PBR validation resource pack when present.

## 2026-05-19 - Agent 3 PBR Custom Resource Verification Refresh

Status:
- Focused source/test refresh completed for texture-size metadata, custom texture/resource `_n` / `_s` indirection, custom-image paired samplers, PBR simple/atlas resources, and active sampler/image cleanup.
- No new patch-worthy source-visible gap was found.
- No source code files were changed in this refresh; documentation records verification and handoff state only.

Inspected:
- `GameplayUniforms` texture-size helpers and `TextureInfoCache`
- `CustomTextureManager` resource and PBR binding paths
- `CustomImageManager` paired sampler/image boundaries
- `PBRTextureManager`, `PBRAtlasTexture`, `PBRAtlasHolder`, and PBR loaders
- `ProgramSamplers` and `ProgramImages`
- Local 1.16.5 `CommonUniforms`, `TextureTracker`, custom texture manager, and PBR texture references

Verification:
- `test --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.texture.pbr.PBRAtlasTextureTest --tests net.oculus.texture.pbr.PBRAtlasSpriteTest --tests net.oculus.texture.pbr.loader.AtlasPBRLoaderTest --tests net.oculus.texture.pbr.loader.SimplePBRLoaderTest --tests net.oculus.texture.pbr.loader.SimplePBRLoaderGlTest --tests net.oculus.pipeline.texture.CustomTextureManagerTest --tests net.oculus.pipeline.texture.CustomImageManagerTest --tests net.oculus.texture.TextureInfoCacheTest --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.gl.program.ProgramImagesTest --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim visual/runtime parity. Live texture-size values, custom resource sampler contents, custom-image writes/reads, PBR output, resource reloads, Relictium wrapped bindings, and rendered output still need client proof.

Recommended next work:
- Move to in-client validation unless a new source discrepancy appears.
- Validate Complementary and MakeUp with Relictium enabled, F3+T/resource reload, reload key, GUI Apply/Cancel/Escape, animated terrain textures, and the PBR validation resource pack when present.

## 2026-05-19 - Agent 3 GUI Apply Reload Rollback

Status:
- Code-side fix completed for a GUI Apply post-save reload-failure state leak.
- `ShaderPackScreen.applyChanges()` now captures config before Apply mutation and restores/saves that snapshot if shared reload fails while enabling shaders.
- A failed Apply no longer leaves persisted selected-pack/options state pointing at a pack the GUI refused to mark applied.

Files changed:
- `src/main/java/net/oculus/gui/ShaderPackScreen.java`
- `src/test/java/net/oculus/gui/ShaderPackScreenSourceTest.java`

Verification:
- `test --tests net.oculus.gui.ShaderPackScreenSourceTest --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.client.OculusClientEventsSourceTest --tests net.oculus.config.OculusConfigTest --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim visual/runtime parity. Live GUI Apply failure messaging, reload-key follow-up behavior, resource reload behavior, Relictium override refresh, shader-visible binding state, and rendered output still need client proof.

Recommended next work:
- In-client validation should force or simulate a reload failure after GUI Apply save, then verify saved config, applied-pack marker, active pack, Relictium overrides, and later startup/reload stay on the previous applied pack.

## 2026-05-19 - Agent 3 Transactional Config Load

Status:
- Code-side fix completed for config load failure boundaries.
- `OculusConfig.load()` now parses selected pack, enabled state, debug/update flags, color space, shadow distance, and per-pack option overrides into local state before publishing.
- Failed config reads no longer clear existing in-memory option overrides before public reload stops.
- Failed invalid-setting sanitized rewrites restore the previous in-memory config before rethrowing.

Files changed:
- `src/main/java/net/oculus/config/OculusConfig.java`
- `src/test/java/net/oculus/config/OculusConfigTest.java`

Verification:
- `test --tests net.oculus.config.OculusConfigTest --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.client.OculusClientEventsSourceTest --tests net.oculus.gui.ShaderPackScreenSourceTest --stacktrace --rerun-tasks` passed with Java 8.
- The broad runtime-data bucket covering `ProgramBuilderReferenceUniformCoverageTest`, `ProgramSamplersTest`, `ProgramImagesTest`, `samplers`, `pipeline.texture`, `texture`, `texture.format`, `texture.pbr`, `uniforms`, `blockrendering`, `compat`, `compat.relictium`, `config`, `client`, and `gui` tests passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim visual/runtime parity. Live reload-key messaging, GUI recovery after disk failures, option-controlled shader output, Relictium override refresh, and rendered output still need client proof.

Recommended next work:
- In-client validation should force config read and sanitized-save failures around reload/startup, then verify active runtime, persisted options, GUI state, Relictium overrides, and subsequent reloads remain on the previous valid state.

## 2026-05-19 - Agent 3 Missing Config Initialization Rollback

Status:
- Code-side fix completed for missing-config creation failure boundaries.
- `OculusConfig.initialize()` now captures previous in-memory config before `load()`.
- If the config file was missing and default `save()` fails, it restores selected pack, enabled-state, debug/update flags, color-space, shadow distance, and per-pack option overrides before rethrowing.

Files changed:
- `src/main/java/net/oculus/config/OculusConfig.java`
- `src/test/java/net/oculus/config/OculusConfigTest.java`

Verification:
- `test --tests net.oculus.config.OculusConfigTest --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.client.OculusClientEventsSourceTest --tests net.oculus.gui.ShaderPackScreenSourceTest --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim visual/runtime parity. Live startup/reload config-creation failure behavior, GUI messaging, option-controlled shader output, Relictium override refresh, and rendered output still need client proof.

Recommended next work:
- In-client validation should force missing-config creation failure during startup/reload, then verify active runtime, in-memory config, GUI state, Relictium overrides, and later reloads remain on the previous valid state.

## 2026-05-19 - Agent 3 MakeUp Runtime Uniform Guard

Status:
- Code-side test guard completed for MakeUp target-pack runtime uniforms.
- `ProgramBuilderReferenceUniformCoverageTest` now reads `run/shaderpacks/MakeUp-UltraFast-9.3e/shaders/shaders.properties` when present and verifies MakeUp's `uniform.*` declarations for pixel size, inverse aspect ratio, day/night/volume/light mixing, frame mod, TAA offset, dither shift, and FOV helper values still have `ProgramBuilder` cases.
- A fresh scan of Complementary and MakeUp shader declarations across `.glsl`, `.vsh`, `.fsh`, `.gsh`, and `.csh` found no new production source gap outside documented Distant Horizons/runtime-only limits.

Files changed:
- `src/test/java/net/oculus/gl/program/ProgramBuilderReferenceUniformCoverageTest.java`

Verification:
- `test --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace --rerun-tasks` passed with Java 8.
- Focused runtime-data suite for uniforms, samplers/images, custom resources, PBR/texture, Relictium compat, config, client reload, and GUI source tests passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim visual/runtime parity. Live MakeUp uniform values, expression timing, sampler/image contents, PBR output, Relictium wrapped terrain output, reload behavior, GUI behavior, and rendered output still need client proof.

Recommended next work:
- Move toward in-client validation with MakeUp and Complementary unless a new source discrepancy appears.
- For MakeUp specifically, validate TAA, dither, day/night/volume/light mixing, FOV helper behavior, reload key, GUI Apply, F3+T/resource reload, and Relictium enabled terrain/shadow paths.

## 2026-05-19 - Agent 3 Complementary Custom Image And SSBO Guard

Status:
- Code-side test guard completed for additional Complementary target-pack custom image and SSBO declarations.
- `ShaderPackLoaderComplementaryTest` now verifies `wsr_img`, `wsr_img_lod`, option-gated `puddle_img`, and active `bufferObject.0` metadata from the real Complementary pack.
- The image assertions pin sampler names, pixel/internal/type formats, clear flags, relative flags, dimensionality, and stored dimension expressions. `wsr_img` stores `COLORED_LIGHTING` as an expression string rather than folding the current override to `128`.
- The same real-pack test now instantiates `CustomImageManager` with the loaded pack option values and verifies those parsed WSR/puddle expressions resolve to runtime dimensions before GL allocation.

Files changed:
- `src/test/java/net/oculus/shaderpack/ShaderPackLoaderComplementaryTest.java`

Verification:
- `test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --stacktrace --rerun-tasks` passed with Java 8 after correcting the expected `COLORED_LIGHTING` expression.
- `test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --tests net.oculus.pipeline.texture.CustomImageManagerTest --stacktrace --rerun-tasks` passed with Java 8 after adding the consumer-side dimension-resolution bridge.
- Adjacent runtime-data suite for ProgramBuilder/custom expressions, samplers/images, custom image manager, SSBO manager, PBR manager, Relictium compat, config, client reload, and GUI source tests passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim visual/runtime parity. Live image allocation, image writes, paired sampler reads, SSBO contents, Relictium wrapped terrain/shadow output, reload behavior, GUI behavior, and rendered output still need client proof.

Recommended next work:
- Move toward in-client validation with Complementary colored lighting, world-space reflections, and rain puddles enabled.
- Validate image allocation/writes, paired sampler reads, SSBO allocation/binding, Relictium terrain/shadow paths, reload key, F3+T/resource reload, GUI Apply, and screenshots/logs.

## 2026-05-19 - Agent 3 Broad Runtime Data Source Verification

Status:
- Code-side verification refresh completed for the Agent 3 runtime data surfaces.
- No source files were changed in this refresh; the focused suites passed and did not expose a new patch-worthy source-visible gap.
- Current confidence is high for source/test coverage of the audited code paths, but not for visual/runtime parity.

Verified buckets:
- Uniforms and custom expressions: `ProgramBuilderReferenceUniformCoverageTest`, `ProgramUniformsTest`, `net.oculus.uniforms.*`, and `net.oculus.uniforms.custom.*`.
- Samplers/images/custom resources: `ProgramSamplersTest`, `ProgramImagesTest`, `IrisSamplersTest`, `IrisImagesTest`, `CustomTextureManagerTest`, and `CustomImageManagerTest`.
- PBR/texture metadata: `TextureInfoCacheTest`, lifecycle/tracker/fallback tests, `TextureFormatLoaderTest`, PBR manager, PBR atlas/sprite, and simple/atlas loader tests.
- Relictium/material/shadow: `net.oculus.compat.relictium.*`, `net.oculus.pipeline.vertex.*`, terrain vertex tests, `BlockMaterialMappingTest`, `ShaderPackLoaderComplementaryTest`, `ShadowMapTextureStateTest`, and `ShadowSamplerBindingsTest`.
- Config/client/gui: `OculusConfigTest`, `ShaderPackReloaderTest`, `OculusClientEventsSourceTest`, and `ShaderPackScreenSourceTest`.

Verification:
- All focused Gradle test buckets above passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not mark the Agent 3 goal complete or claim parity until live client validation proves shader-visible values, sampler/image contents, PBR output, Relictium terrain/shadow output, reload behavior, GUI Apply behavior, and rendered output.

Recommended next work:
- Unless a new source discrepancy appears, stop broad code churn and run in-client validation with Complementary Reimagined, MakeUp Ultra Fast, Relictium, and the PBR validation resource pack.

## 2026-05-19 - Agent 3 Runtime Validation Property Guard

Status:
- Code-side test guard completed for the dev-only runtime validation properties.
- `OculusRuntimeValidationTest` now verifies blank `oculus.validation.autoJoinWorld` and `oculus.validation.pbrTextures` values remain inert.
- The same test verifies a trimmed `oculus.validation.pbrTextures` value enables PBR telemetry without enabling auto-join.
- The test also source-guards `build.gradle` forwarding for `oculus.validation.autoJoinWorld`, `oculus.validation.exitAfterWorldTicks`, and `oculus.validation.pbrTextures` through `runClient`.

Files changed:
- `src/test/java/net/oculus/client/OculusRuntimeValidationTest.java`

Verification:
- `test --tests net.oculus.client.OculusRuntimeValidationTest --tests net.oculus.client.OculusClientEventsSourceTest --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.config.OculusConfigTest --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace` passed with Java 8.
- `git diff --check` and touched-file whitespace/conflict scans passed for `OculusRuntimeValidationTest` and the updated docs.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Live auto-join, shutdown timing, PBR telemetry logs, Relictium validation logs, shader-visible resources, and rendered parity still need client proof.

Recommended next work:
- If runtime validation becomes allowed, launch `runClient` with a known world and the validation properties, then verify auto-join, exit timing, PBR/Relictium validation logs, reload behavior, and screenshots with Complementary, MakeUp, Relictium, and the PBR validation resource pack.

## 2026-05-19 - Agent 3 Runtime Validation Tick Ordering Guard

Status:
- Code-side source test guard completed for the client-tick order used by unattended runtime validation.
- `OculusClientEventsSourceTest` now verifies `OculusClientEvents.onClientTick(...)` applies startup config before calling `OculusRuntimeValidation.onClientTick(...)`.
- The same test verifies reload, toggle, and GUI key handling run after the runtime validation hook.
- This keeps the planned auto-join validation path source-aligned with persisted pack/options startup state before entering a world.

Files changed:
- `src/test/java/net/oculus/client/OculusClientEventsSourceTest.java`

Verification:
- `test --tests net.oculus.client.OculusClientEventsSourceTest --tests net.oculus.client.OculusRuntimeValidationTest --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.config.OculusConfigTest --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace` passed with Java 8.
- `git diff --check` and touched-file whitespace/conflict scans passed for the test file and updated docs.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Live auto-join ordering, PBR/Relictium telemetry logs, shader-visible resource state, reload transitions, and rendered parity still need client proof.

Recommended next work:
- Runtime validation remains the main blocker: run `runClient` only when allowed, with a known world and validation properties, then capture logs/screenshots for configured-pack startup state, auto-join, PBR/custom-resource telemetry, Relictium terrain/shadow behavior, reload key, F3+T/resource reload, GUI Apply, and invalid-pack recovery.

## 2026-05-19 - Agent 3 SSBO Restore Failure Suppression

Status:
- Code-side runtime-resource cleanup fix completed for `bufferObject.*` SSBO setup and binding refresh.
- `ShaderStorageBufferManager.initialize()` now records setup failures around `glBufferData`, zero-clear, and `glBindBufferBase` before restoring the caller's generic `GL_SHADER_STORAGE_BUFFER` binding.
- `ShaderStorageBufferManager.bindAll()` now records indexed binding failures before restoring the generic binding.
- If restore also fails, the restore failure is suppressed onto the original failure; if setup/bind work succeeds, restore failure remains fail-fast.

Files changed:
- `src/main/java/net/oculus/pipeline/buffer/ShaderStorageBufferManager.java`
- `src/test/java/net/oculus/pipeline/buffer/ShaderStorageBufferManagerTest.java`

Verification:
- `test --tests net.oculus.pipeline.buffer.ShaderStorageBufferManagerTest --stacktrace --rerun-tasks` passed with Java 8.
- Adjacent runtime-resource suite passed with Java 8: `ShaderStorageBufferManagerTest`, `CustomImageManagerTest`, `CustomTextureManagerTest`, `ProgramSamplersTest`, `ProgramImagesTest`, `PBRTextureManagerTest`, and `TextureFormatLoaderTest`.
- Standalone `compileJava --stacktrace` passed with Java 8.
- `git diff --check` and touched-file whitespace/conflict scans passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Live SSBO allocation, shader-visible buffer contents, driver binding failures, reload behavior, Relictium wrapped usage, and rendered parity still need client proof.

Recommended next work:
- In a real client run, enable Complementary colored lighting/world-space reflections so `bufferObject.0` is active, then verify SSBO allocation, indexed binding visibility, reload cleanup, Relictium terrain/shadow behavior, and rendered output.

## 2026-05-19 - Agent 3 ProgramBuilder Linked Program Cleanup

Status:
- Code-side GL ownership cleanup fix completed for linked-program runtime-binding discovery and wrapper construction.
- `ProgramBuilder.begin(...)` and `beginCompute(...)` now construct owning builders through `createOwningProgramBuilder(...)`.
- If builder construction or active uniform/sampler/image discovery throws after `ProgramCreator.create(...)` returns a linked handle, `deleteFailedProgramHandle(...)` deletes that handle and suppresses delete failure onto the original discovery failure.
- `ProgramBuilder.build()` and `buildCompute()` now also delete owning handles if final `Program` / `ComputeProgram` wrapper construction fails before ownership is returned to the caller.
- `wrapLinkedProgram(...)` remains non-owning and does not delete backend-owned Relictium handles.

Files changed:
- `src/main/java/net/oculus/gl/program/ProgramBuilder.java`
- `src/test/java/net/oculus/gl/program/ProgramBuilderReferenceUniformCoverageTest.java`

Verification:
- `test --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace --rerun-tasks` passed with Java 8.
- Adjacent program binding suite passed with Java 8: `ProgramBuilderReferenceUniformCoverageTest`, `ProgramActivationCleanupSourceTest`, `ComputeProgramSourceTest`, `ProgramSamplersTest`, `ProgramImagesTest`, and `ProgramUniformsTest`.
- Standalone `compileJava --stacktrace` passed with Java 8.
- `git diff --check` and touched-file whitespace/conflict scans passed.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Live shader reload failure behavior, driver cleanup, Relictium wrapped setup, sampler/image/PBR state after failures, and rendered parity still need client proof.

Recommended next work:
- In an allowed client run, force a shader-pack compile/discovery failure after link time if possible, then verify failed handles do not survive reload/fallback and Relictium wrapped terrain state is not affected.

## 2026-05-19 - Agent 3 PBR Atlas Ownership And Relictium Scope Setup Cleanup

Status:
- Code-side PBR atlas ownership cleanup completed.
- Code-side Relictium terrain-scope setup cleanup completed.
- Runtime validation remains blocked by the instruction not to run Minecraft or `runClient`.

Files changed:
- `src/main/java/net/oculus/texture/pbr/loader/AtlasPBRLoader.java`
- `src/test/java/net/oculus/texture/pbr/loader/AtlasPBRLoaderTest.java`
- `src/main/java/net/oculus/mixin/pipeline/RelictiumChunkRenderShaderBackendMixin.java`
- `src/test/java/net/oculus/compat/relictium/RelictiumChunkRenderShaderBackendMixinSourceTest.java`

Behavior:
- `AtlasPBRLoader.uploadAndAcceptAtlas(...)` now owns the upload-to-consumer handoff for both normal and specular PBR atlases.
- If an atlas upload succeeds and the consumer throws before accepting ownership, the atlas is deleted locally and cleanup failure is suppressed onto the original consumer failure.
- Failed uploads are not offered to the consumer and are not double-deleted.
- `RelictiumChunkRenderShaderBackendMixin.oculus$begin(...)` now guards phase switching and `beginSodiumTerrainRendering()` before backend `begin()`.
- A failure during phase switch or terrain entry now runs `oculus$endTerrainScope()` before rethrowing, so previous phase/scope state is not left stale at source level.

Verification:
- `test --tests net.oculus.texture.pbr.loader.AtlasPBRLoaderTest --tests net.oculus.texture.pbr.loader.SimplePBRLoaderTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.texture.pbr.PBRAtlasTextureTest` passed with Java 8.
- `test --tests 'net.oculus.compat.relictium.*'` passed with Java 8.
- Split runtime-data buckets passed for program/uniforms, samplers/custom resources/PBR, Relictium/block/vertex, config/client/gui, and target shader-pack loader guards.
- One oversized combined Relictium/config/client/gui/shaderpack selected-test bucket failed only while Gradle wrote XML reports; the smaller split buckets passed.
- Standalone `compileJava` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim live PBR atlas output, Relictium terrain/shadow parity, reload parity, GUI/reload parity, or visual parity from this slice.

Recommended next work:
- If code work continues, look for narrow source-visible ownership or stale-state failures only; broad runtime-binding churn is now low yield.
- If runtime validation becomes allowed, prioritize Complementary and MakeUp client runs with PBR/Relictium telemetry, reload/F3+T, GUI Apply, and screenshots.

## 2026-05-19 - Agent 3 Dotted Pack Legacy Option Migration Guard

Status:
- Code-side config coverage strengthened for dotted shader-pack filenames and legacy shader-option keys.
- No production code changed in this slice.
- Runtime validation remains blocked by the instruction not to run Minecraft or `runClient`.

Files changed:
- `src/test/java/net/oculus/config/OculusConfigTest.java`

Behavior:
- `OculusConfigTest#legacyOptionKeysMigrateToSelectedDottedPackName` now covers legacy one-part `option.<id>` entries when the selected pack is a multi-dot filename such as `ComplementaryReimagined_r5.6.1.zip`.
- The guard verifies those overrides resolve through `getOptionOverrides(packName)` and do not leak into `getOptionOverrides("internal")`.
- This protects source-side startup/reload/GUI Apply option state for runtime binding graphs that depend on selected-pack option values.

Verification:
- `test --tests net.oculus.config.OculusConfigTest --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.client.OculusClientEventsSourceTest --tests net.oculus.gui.ShaderPackScreenSourceTest --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace` passed with Java 8.
- `git diff --check` plus touched-file whitespace/conflict scans passed for the test and updated docs.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim live reload-key behavior, GUI Apply behavior, option-controlled shader output, Relictium override refresh, or rendered parity from this source guard.

Recommended next work:
- Runtime validation remains the real blocker: validate Complementary/MakeUp startup, reload key, F3+T/resource reload, GUI Apply/Cancel/Escape, PBR validation resources, and Relictium terrain/shadow output in-client when allowed.

## 2026-05-19 - Agent 3 Texture-Format Reload Listener Hard-Failure Latch Guard

Status:
- Code-side resource-reload latch fix completed for texture-format/PBR reload listener registration.
- Runtime validation remains blocked by the instruction not to run Minecraft or `runClient`.

Files changed:
- `src/main/java/net/oculus/texture/format/TextureFormatLoader.java`
- `src/test/java/net/oculus/texture/format/TextureFormatLoaderTest.java`

Behavior:
- `TextureFormatLoader.registerReloadListener(...)` already reset `registeredReloadListener` after a `RuntimeException` from Forge reload-listener registration.
- It now resets the latch after `RuntimeException | Error`, then rethrows the original failure.
- `TextureFormatLoaderTest#hardReloadListenerRegistrationFailureDoesNotLatchRegistration` verifies a hard registration failure does not prevent a later reloadable resource manager from registering the texture-format reload listener.
- This protects source-side F3+T/resource reload paths for texture-format metadata, PBR holder cleanup, custom resource `_n` / `_s` indirection, active PBR sampler resets, and Relictium wrapped sampler inheritance.

Verification:
- First focused test run failed at `compileTestJava` because the new test helper attempted to extend a final test class; the helper was corrected to implement `IReloadableResourceManager` directly.
- `test --tests net.oculus.texture.format.TextureFormatLoaderTest --tests net.oculus.mixins.TextureManagerPBRReloadMixinSourceTest --tests net.oculus.texture.pbr.PBRTextureManagerTest --tests net.oculus.texture.pbr.loader.SimplePBRLoaderTest --tests net.oculus.texture.pbr.loader.AtlasPBRLoaderTest --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace` passed with Java 8.
- `git diff --check` plus touched-file whitespace/conflict scans passed for the source, test, and updated docs.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim live F3+T/resource reload behavior, texture-format define refresh, PBR sampler contents, Relictium wrapped sampler refresh, or rendered parity from this source fix.

Recommended next work:
- Runtime validation remains the real blocker: validate F3+T/resource reload with Complementary/MakeUp, PBR validation resources, Relictium terrain/shadow, and screenshots/logs in-client when allowed.

## 2026-05-19 - Agent 3 Texture Metadata Mip-Level Guard

Status:
- Code-side texture-size metadata coverage strengthened.
- No production code changed in this slice.
- Runtime validation remains blocked by the instruction not to run Minecraft or `runClient`.

Files changed:
- `src/test/java/net/oculus/texture/TextureInfoCacheTest.java`

Behavior:
- Added focused tests proving non-base mip uploads for 2D and 3D textures do not overwrite the level-zero metadata used by `atlasSize` and `gtextureSize`.
- This protects source-side confidence for custom textures, PBR atlases, custom images, Relictium wrapped programs, and reload paths that depend on base texture dimensions.

Verification:
- `test --tests net.oculus.texture.TextureInfoCacheTest` passed with Java 8.
- Broad Agent 3 runtime-data selected-test suite passed with Java 8.
- Standalone `compileJava` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim live `atlasSize` / `gtextureSize` values, PBR atlas dimensions, custom-resource reload behavior, Relictium wrapped-program reads, or rendered parity from this test guard.

Recommended next work:
- Runtime validation remains the blocker: verify texture-size uniforms, PBR output, F3+T/resource reload, Relictium terrain/shadow output, and screenshots/logs in-client when allowed.

## 2026-05-19 - Agent 3 Target-Pack Modern Uniform Preprocessor Guard

Status:
- Code-side target-pack source graph coverage strengthened for raw Complementary/MakeUp Distant Horizons and Minecraft 1.21.9 End-flash declarations.
- No production code changed in this slice.
- Runtime validation remains blocked by the instruction not to run Minecraft or `runClient`.

Files changed:
- `src/test/java/net/oculus/shaderpack/ShaderPackLoaderComplementaryTest.java`

Behavior:
- Added `ShaderPackLoaderComplementaryTest#targetPackModernDhAndEndFlashUniformsStayOutOf112RuntimeSources`.
- The test proves the raw target-pack declarations still exist, then checks the loaded 1.12.2 runtime raster and compute sources across overworld, nether, end, and a custom dimension.
- It verifies the suspicious raw names are absent from runtime sources: `endFlashIntensity`, `previousEndFlashIntensity`, `endFlashPosition`, `dhDepthTex*`, `dhProjection*`, `dhNearPlane`, `dhFarPlane`, `dhRenderDistance`, and `dhMaterialId`.
- It also pins that `dh_terrain.*` and `dh_water.*` are not local 1.16.5 runtime source starts.

Verification:
- First focused run failed at `compileTestJava` because the new test referenced a missing local file-read helper.
- After adding the helper, `test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.targetPackModernDhAndEndFlashUniformsStayOutOf112RuntimeSources --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim live active uniform discovery, Distant Horizons behavior, Relictium wrapped-program output, or visual parity from this source guard.

Recommended next work:
- Runtime validation remains the real blocker: load Complementary/MakeUp in-client, confirm no active-uniform warnings for the inactive `endFlash*` / `dh*` names on the default 1.12.2 path, and keep Distant Horizons support as a separate scope decision.

## 2026-05-19 - Agent 3 Target-Pack Runtime Uniform Declaration Coverage

Status:
- Code-side target-pack runtime uniform declaration coverage strengthened.
- No production code changed in this slice.
- Runtime validation remains blocked by the instruction not to run Minecraft or `runClient`.

Files changed:
- `src/test/java/net/oculus/shaderpack/ShaderPackLoaderComplementaryTest.java`

Behavior:
- Added `ShaderPackLoaderComplementaryTest#targetPackRuntimeNonSamplerUniformDeclarationsStayBoundOrCustom`.
- The test loads Complementary and MakeUp, collects loaded runtime raster and compute sources across overworld, nether, end, and a custom dimension, and parses non-sampler/non-image/non-atomic `uniform` declarations.
- Each parsed scalar/vector/matrix uniform name must be covered by a `ProgramBuilder` switch case or by the loaded pack's parsed custom `uniform.*` directives.
- Sampler/image declarations remain under the existing sampler/image/custom-resource tests.

Verification:
- `test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.targetPackRuntimeNonSamplerUniformDeclarationsStayBoundOrCustom --stacktrace --rerun-tasks` passed with Java 8.
- `test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.targetPackModernDhAndEndFlashUniformsStayOutOf112RuntimeSources --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.targetPackRuntimeNonSamplerUniformDeclarationsStayBoundOrCustom --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim live GL active-uniform discovery, uniform upload timing, shader-visible values, Relictium wrapped values, or visual parity from this source guard.

Recommended next work:
- Runtime validation remains the blocker. If code work continues, look for similarly source-backed gaps in sampler/image/PBR/Relictium cleanup or source graph coverage; otherwise move to in-client Complementary/MakeUp logs and screenshots when allowed.

## 2026-05-19 - Agent 3 Target-Pack Runtime Sampler/Image Declaration Coverage

Status:
- Code-side target-pack runtime sampler/image declaration coverage strengthened.
- No production code changed in this slice.
- Runtime validation remains blocked by the instruction not to run Minecraft or `runClient`.

Files changed:
- `src/test/java/net/oculus/shaderpack/ShaderPackLoaderComplementaryTest.java`

Behavior:
- Added `ShaderPackLoaderComplementaryTest#targetPackRuntimeSamplerAndImageDeclarationsStayKnownToBindingSurface`.
- The test loads Complementary and MakeUp, collects loaded runtime raster and compute sources across overworld, nether, end, and a custom dimension, and parses sampler/image uniform declarations.
- Each sampler declaration must map to a known built-in binding category or parsed custom texture/custom-image sampler metadata. Each image declaration must map to render-target images, shadow color images, or parsed custom-image metadata.
- The shared declaration regex now recognizes `layout(...) uniform image*` declarations.

Verification:
- `test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest.targetPackRuntimeSamplerAndImageDeclarationsStayKnownToBindingSurface --stacktrace --rerun-tasks` passed with Java 8.
- The adjacent target-pack source guards plus `IrisSamplersTest`, `IrisImagesTest`, `ProgramSamplersTest`, and `ProgramImagesTest` passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim live sampler contents, image writes, reload behavior, Relictium wrapped output, or visual parity from this source guard.

Recommended next work:
- Runtime validation remains the real blocker: load Complementary and MakeUp in-client and compare shader logs, sampler/image contents, resource reload behavior, Relictium wrapped output, and screenshots against the source-backed binding surface.

## 2026-05-19 - Agent 3 Relictium Extended Attribute Binding Surface

Status:
- Code-side Relictium wrapped terrain attribute binding coverage strengthened.
- No production code changed in this slice.
- Runtime validation remains blocked by the instruction not to run Minecraft or `runClient`.

Files changed:
- `src/test/java/net/oculus/compat/relictium/OculusRelictiumProgramLinkerSourceTest.java`

Behavior:
- Added `OculusRelictiumProgramLinkerSourceTest#relictiumLinkerBindingsMatchExtendedTerrainVertexSurface`.
- The test source-pins `OculusRelictiumProgramLinker` bindings for `iris_Normal`, `at_tangent`, `mc_midTexCoord`, `mc_Entity`, and `at_midBlock`.
- It also pins the matching `OculusVertexBindingHelper` augmentation calls and the 52-byte `OculusTerrainVertexType` layout for normalized normal/tangent bytes, signed material/render-type shorts, float mid-UVs, and unnormalized mid-block/emission bytes.

Verification:
- `test --tests net.oculus.compat.relictium.OculusRelictiumProgramLinkerSourceTest --tests net.oculus.pipeline.OculusChunkShaderBindingPointsTest --tests net.oculus.pipeline.OculusTerrainVertexTypeSourceTest --tests net.oculus.pipeline.OculusTerrainVertexBufferWriterNioTest --tests net.oculus.compat.relictium.RelictiumTerrainBindingAugmentationSourceTest --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim live Relictium vertex attribute values, material shader branches, separate AO/emission behavior, shadow terrain output, or visual parity from this source guard.

Recommended next work:
- Runtime validation remains the blocker: load Complementary/MakeUp with Relictium enabled and verify terrain/shadow logs, material IDs, normal/tangent lighting, mid-UV behavior, mid-block/emission data, reload behavior, and screenshots.

## 2026-05-19 - Agent 3 GUI Apply Rollback Runtime Restore

Status:
- Code-side GUI Apply rollback behavior tightened.
- Production code changed in `ShaderPackScreen`.
- Runtime validation remains blocked by the instruction not to run Minecraft or `runClient`.

Files changed:
- `src/main/java/net/oculus/gui/ShaderPackScreen.java`
- `src/test/java/net/oculus/gui/ShaderPackScreenSourceTest.java`

Behavior:
- `ShaderPackScreen.restoreConfigAfterApplyReloadFailure(...)` now restores and saves the previous config snapshot, returns immediately if that rollback save fails, and calls `ShaderPackReloader.reload()` after a successful rollback save.
- This avoids leaving runtime shader resources on the internal fallback while config has already been rolled back to the prior selected pack/options after a failed GUI Apply reload.
- Added `ShaderPackScreenSourceTest#failedSharedReloadRestoresPreviousRuntimeAfterPersistedRollback`.

Verification:
- `test --tests net.oculus.config.OculusConfigTest --tests net.oculus.client.ShaderPackReloaderTest --tests net.oculus.client.OculusClientEventsSourceTest --tests net.oculus.gui.ShaderPackScreenSourceTest --stacktrace --rerun-tasks` passed with Java 8.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim live GUI failure behavior, restored shader-visible state, Relictium override refresh, or rendered parity from this source/test slice.

Recommended next work:
- Runtime validation remains the blocker: reproduce a failed GUI Apply in-client from a previously valid pack and verify rollback persistence, active runtime pack restoration, Relictium output, and screenshots/logs.

## 2026-05-19 - Agent 3 Generated Matrix Uniform Coverage Guard

Status:
- Code-side built-in uniform reference coverage strengthened.
- No production code changed in this slice.
- Runtime validation remains blocked by the instruction not to run Minecraft or `runClient`.

Files changed:
- `src/test/java/net/oculus/gl/program/ProgramBuilderReferenceUniformCoverageTest.java`

Behavior:
- Added `ProgramBuilderReferenceUniformCoverageTest#referenceCoverageTracksGeneratedMatrixAliasesAndSkipsInactiveHeldLightColorTodo`.
- The test pins the local 1.16.5 `MatrixUniforms` helper-generated matrix alias surface for `gbuffer*`, `gbuffer*Inverse`, `gbufferPrevious*`, `shadow*`, and `shadow*Inverse` names.
- The same test verifies `heldBlockLightColor` and `heldBlockLightColor2` remain commented TODOs in local 1.16.5 `IdMapUniforms`, stay out of the required built-in coverage list, and are not registered by `ProgramBuilder`.

Verification:
- First focused test attempt hit a stale Gradle 4.9 generated binary result snapshot under `build/test-results/test/binary`; only that generated directory was removed.
- `test --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace --rerun-tasks` passed with Java 8 after clearing the generated binary result directory.
- Follow-up split runtime-data buckets passed with Java 8 for uniforms/custom expressions, samplers/images/custom resources/PBR, Relictium/block/compat/terrain vertex contracts, config/client/GUI, and `ShaderPackLoaderComplementaryTest`.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim live matrix values, held-light output, custom-expression timing, Relictium wrapped-program behavior, or rendered parity from this source/test slice.

Recommended next work:
- Runtime validation remains the blocker: verify matrix-dependent shader output, hand-light behavior, Relictium wrapped programs, shader logs, and screenshots with Complementary and MakeUp.

## 2026-05-19 - Agent 3 Reference Source Uniform Literal Coverage Guard

Status:
- Code-side built-in uniform audit coverage strengthened.
- No production code changed in this slice.
- Runtime validation remains blocked by the instruction not to run Minecraft or `runClient`.

Files changed:
- `src/test/java/net/oculus/gl/program/ProgramBuilderReferenceUniformCoverageTest.java`

Behavior:
- Added `ProgramBuilderReferenceUniformCoverageTest#referenceUniformCoverageListTracksActiveReferenceSourceLiterals`.
- The test scans local 1.16.5 uniform reference files after stripping line comments, derives active literal names from direct uniform registration calls and external-managed helper calls, folds in generated matrix aliases, and skips generated string prefixes.
- Every derived active reference name must be in the maintained `REFERENCE_1_16_5_UNIFORMS` list and have a `ProgramBuilder` case.

Verification:
- `test --tests net.oculus.gl.program.ProgramBuilderReferenceUniformCoverageTest --stacktrace --rerun-tasks` passed with Java 8 after clearing Gradle's generated binary test-result directory.
- Standalone `compileJava --stacktrace --rerun-tasks` passed with Java 8.

Runtime status:
- Runtime-unverified. Minecraft and `runClient` were not run.
- Do not claim exact live GLSL type activity, upload timing, shader-visible values, Relictium wrapped-program behavior, or rendered parity from this source/test slice.

Recommended next work:
- Continue source-side exact type/timing checks only where a concrete mismatch is suspected; the remaining hard blocker is still in-client validation with Complementary and MakeUp.
