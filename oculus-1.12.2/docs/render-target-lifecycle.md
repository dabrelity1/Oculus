# Render Target And Postprocess Lifecycle

Last updated: 2026-05-20.

This file records the current source-backed render-target, depth-texture, and postprocess ownership model for the Forge 1.12.2 Oculus backport. It exists so future agents do not have to rediscover these invariants from chat history or by diffing the whole pipeline.

Source code remains authoritative. If this file disagrees with the active 1.12.2 source or the `../Oculus-1.16.5` reference, inspect the source first, fix the behavior if needed, then update this file.

## Active Files

- `src/main/java/net/oculus/shaderpack/PackRenderTargetDirectives.java`
- `src/main/java/net/oculus/shaderpack/PackDirectives.java`
- `src/main/java/net/oculus/shaderpack/ProgramSet.java`
- `src/main/java/net/oculus/rendertarget/RenderTargets.java`
- `src/main/java/net/oculus/rendertarget/DepthTexture.java`
- `src/main/java/net/oculus/rendertarget/MinecraftFramebufferExt.java`
- `src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer.java`
- `src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java`
- `src/main/java/net/oculus/mixin/pipeline/FramebufferVersionMixin.java`
- `src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java`
- `src/main/java/net/oculus/postprocess/CompositeRenderer.java`
- `src/main/java/net/oculus/postprocess/FinalPassRenderer.java`
- `src/main/java/net/oculus/postprocess/BufferFlipper.java`
- `src/main/java/net/oculus/postprocess/FullScreenQuadRenderer.java`
- `src/main/java/net/oculus/pipeline/ClearPassCreator.java`
- `src/main/java/net/oculus/pipeline/ClearPass.java`
- `src/test/java/net/oculus/gl/FramebufferCompatibilityTest.java`
- `src/test/java/net/oculus/mixins/FramebufferVersionMixinSourceTest.java`
- `src/test/java/net/oculus/rendertarget/RenderTargetsSourceTest.java`

Primary 1.16.5 references:

- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shaderpack/PackRenderTargetDirectives.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shaderpack/PackDirectives.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shaderpack/ProgramSet.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/gl/framebuffer/GlFramebuffer.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/rendertarget/RenderTargets.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/postprocess/CompositeRenderer.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/postprocess/FinalPassRenderer.java`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/pipeline/DeferredWorldRenderingPipeline.java`

## Baseline Render Targets

`ProgramSet` constructs `PackDirectives` with `PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS`, matching the 1.16.5 `ProgramSet` constructor. In this port, the baseline set is `0..IrisLimits.MAX_COLOR_BUFFERS - 1`.

`PackRenderTargetDirectives` creates a default `RenderTargetSettings` for every supported target. That means normal shader-pack loading gives `RenderTargets` entries for `colortex0` through the baseline limit even when the pack does not explicitly declare `colortex0Format`, `colortex0Clear`, or draw-buffer settings.

This matters because empty draw-buffer framebuffers still need a color attachment before OpenGL 3.0. Both the 1.16.5 reference and this 1.12.2 port attach `colortex0` to the empty framebuffer while disabling draw buffers. Do not change the default supported-target set to an empty or usage-discovered set unless the discovery logic is complete and always preserves `colortex0`.

If a future feature introduces custom supported-target sets, the safe invariant is:

- `colortex0` must exist whenever `RenderTargets.createEmptyFramebuffer()` or the equivalent reference path can be called.
- Any draw buffer referenced by `ProgramDirectives`, clear passes, composite/final passes, explicit flips, custom texture overrides, images, or shader-pack aliases must have a configured `RenderTarget`.
- Unsupported draw buffers should fail at pack parsing or pipeline setup with a precise error, not later through framebuffer incompleteness.

## Depth Texture Resize Lifecycle

The 1.16.5 reference keeps a long-lived `RenderTargets` object and calls `RenderTargets.resizeIfNeeded(...)`. That method tracks a depth-buffer version, updates `currentDepthTexture`, and reattaches the new Minecraft depth texture to owned framebuffers when Minecraft recreates its depth texture.

The current 1.12.2 port uses a different but deliberate lifecycle:

1. `ShaderWorldRenderingPipeline.ensureFramebufferDimensionsUpToDate()` reads `Minecraft.displayWidth` and `displayHeight`.
2. It calls `FramebufferManager.resizeIfNeeded(displayWidth, displayHeight)`.
3. `FramebufferManager.resizeIfNeeded(...)` rebuilds its private render-target cache and deletes/recreates its depth texture when the display size changes.
4. The pipeline then rebuilds `RenderTargets` in the same method if the size changed or no `RenderTargets` exists.
5. The new `RenderTargets` instance captures `framebufferManager.getDepthTexture()` after the depth texture has been recreated.

Because the active path rebuilds `RenderTargets` after `FramebufferManager` recreates the depth texture, there is no known frame where the active `RenderTargets` intentionally keeps using the old depth texture after a resize. This is not the same implementation shape as 1.16.5, but it preserves the important attachment boundary for the current 1.12 lifecycle.

Do not convert `RenderTargets.resize(...)` into the main resize path without also porting the 1.16.5 depth-texture reattachment/version behavior. The current `RenderTargets.resize(...)` resizes render-target textures and depth snapshots only; it does not replace `currentDepthTexture` or reattach owned framebuffers.

## Framebuffer Ownership

`RenderTargets` owns the `GlFramebuffer` handles created through:

- `createGbufferFramebuffer(...)`
- `createClearFramebuffer(...)`
- `createColorFramebuffer(...)`
- the internal empty-framebuffer path

Framebuffers created by `RenderTargets` must be destroyed with `RenderTargets.destroyFramebuffer(...)` or by `RenderTargets.destroy()`. Direct `GlFramebuffer.destroy()` leaves stale handles in `RenderTargets.ownedFramebuffers` and can make later teardown or resize behavior misleading.

Current ownership rules:

- `ShaderWorldRenderingPipeline.destroy()` destroys cached g-buffer wrapper framebuffers before destroying `RenderTargets`.
- `CompositeRenderer` destroys pass framebuffers through `RenderTargets.destroyFramebuffer(...)` during recalculation and teardown.
- `FinalPassRenderer` destroys swap and baseline framebuffers through `RenderTargets.destroyFramebuffer(...)`.
- `FramebufferManager` has a separate legacy/private cache. The active shader pipeline uses it primarily for display size and the current depth texture; do not assume its internal render-target map is the active postprocess target owner.

`RenderTargets.createColorFramebuffer(...)` owns gbuffer and composite-pass draw-buffer routing. `createGbufferFramebuffer(...)` delegates to it after resolving the current flipped main/alternate write targets. The framebuffer builder attaches each requested logical `drawBuffers` target to sequential framebuffer color attachments, calls `framebuffer.drawBuffers(logicalDrawBuffers(drawBuffers.length))`, and verifies completeness. The gbuffer bind path and composite render loop should bind the already-created framebuffer and use the shader program; they should not reissue `glDrawBuffers(...)` or `noDrawBuffers()` per pass. This mirrors the local 1.16.5 `DeferredWorldRenderingPipeline.Pass.use()` and `CompositeRenderer` paths, where framebuffer bind is followed by viewport/program work and draw-buffer state is not rebuilt in the render loop.

Explicit postprocess target references are required, not optional. Composite pass sizing, composite resize sizing, composite/final mipmap setup, final swap-pass creation, final swap-pass resize, and final render-target mipmap reset now throw when the referenced `colortexN` is missing. This matches the local 1.16.5 paths that dereference `renderTargets.get(...)` directly and prevents stale dimensions, skipped swap copies, or missing mipmaps from hiding a bad shaderpack directive. Missing draw-buffer directives still default to `{0}` in `ProgramDirectives`; explicitly empty draw-buffer arrays are not normalized again in `CompositeRenderer` and should fail at framebuffer creation. General sampler enumeration can still skip sparse absent entries while walking configured target slots.

## 1.12 Framebuffer Dispatch

Framebuffer operations in active render-target, shadow, center-depth, color-space, composite, and pipeline-boundary code should go through `OpenGlHelper` instead of direct `GL30.gl*Framebuffer` calls.

Why this matters:

- Local MCP stable 39 exposes `OpenGlHelper.glGenFramebuffers`, `glBindFramebuffer`, `glFramebufferTexture2D`, `glCheckFramebufferStatus`, and `glDeleteFramebuffers`.
- Local 1.12.2 bytecode shows those methods dispatch through the selected framebuffer backend: core OpenGL 3.0, ARB framebuffer object, or EXT framebuffer object.
- Existing runtime logs showed `Shadow framebuffer incomplete: 36055`, which is the local missing-attachment framebuffer status, while direct GL30 shadow attachment calls were still active.

Active copy paths should bind through the unified `OpenGlHelper.GL_FRAMEBUFFER` target. They use `glCopyTexImage2D` / `glCopyTexSubImage2D`, not framebuffer blits, so they do not need split read/draw framebuffer targets. Avoid `OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ...)` and `OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ...)` in active code unless a future runtime-proven path also proves those targets are valid for every 1.12 backend the mod supports.

`GlFramebuffer.addDepthAttachment(...)` now mirrors the local 1.16.5 helper's attachment selection by reading `TextureInfoCache` metadata for the texture internal format and using `GL_DEPTH_STENCIL_ATTACHMENT` only when `DepthBufferFormat.isCombinedStencil()` is true. The actual attachment call still goes through `OpenGlHelper.glFramebufferTexture2D(...)`, preserving the 1.12 backend dispatch. `FramebufferCompatibilityTest` guards both the OpenGlHelper dispatch rule and the depth-stencil attachment selection.

Shadow target preparation has an additional 1.16.5 source-backed texture-unit boundary: `ShadowRenderer.prepareRenderTargets()` restores the default texture unit through `OculusRenderSystem.restoreDefaultActiveTexture()` before binding the shadow framebuffer, clearing depth, dispatching shadow computes, or clearing shadow color targets. This mirrors `DeferredWorldRenderingPipeline.prepareRenderTargets()` resetting `GL_TEXTURE0` before shadow framebuffer work and keeps 1.12 bind-to-edit paths from inheriting a non-default active unit while keeping the `GlStateManager` cache synchronized. The inspected shadow path does not issue renderer-level post-dispatch memory barriers during target preparation or immediately after `ShadowRenderer.renderShadows(...)`; `ComputeProgram.dispatch(...)` retains the `allowConcurrentCompute=false` pre-dispatch barrier.

Shadow framebuffer construction must fail fast when `OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER)` reports anything other than `OpenGlHelper.GL_FRAMEBUFFER_COMPLETE`. The failure path unbinds framebuffer `0` and deletes the newly-created shadow FBO before throwing so an invalid setup does not leave the bad framebuffer bound. Continuing after an incomplete shadow framebuffer leaves the pipeline in a half-active state and hides the same class of setup failure that the 1.16.5 framebuffer builders surface during render-target creation.

The same fail-fast rule applies to the temporary framebuffer used by the 1.12 `ShadowMap` color-clear helper. The local 1.16.5 `ShadowRenderTargets` path creates verified clear framebuffers up front through `GlFramebuffer.isComplete()`. The 1.12 implementation attaches one shadow color texture at a time to a temporary FBO, sets the draw buffer, then checks `OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER)` before issuing `glClear`. An incomplete temporary clear target throws `IllegalStateException("Shadow color framebuffer incomplete: ...")` instead of turning the clear into a hidden GL error.

Render-target depth snapshots have a source-backed texture binding split:

- Dirty/first allocation uses `OculusRenderSystem.copyTexImage2D(...)` after binding the destination depth texture through `GlStateManager.bindTexture(...)`, matching the local 1.16.5 `RenderTargets.copyPreTranslucentDepth()` / `copyPreHandDepth()` branch that calls `RenderSystem.bindTexture(destination)` before `IrisRenderSystem.copyTexImage2D(...)`. It intentionally does not bind texture `0` afterward.
- Later refreshes use `OculusRenderSystem.copyTexSubImage2D(...)` and must save/restore the previous `GL_TEXTURE_BINDING_2D`, matching the local 1.16.5 `DepthCopyStrategy.Gl20CopyTexture` path through `IrisRenderSystem.copyTexSubImage2D(...)` on non-DSA contexts.

`RenderTargetsSourceTest` pins that split so this path does not regress back to raw 2D binds or unconditional `glBindTexture(..., 0)` cleanup.

## Hand And Translucent Depth Boundary

The shader hand boundary is intentionally earlier than vanilla 1.12's final hand draw. The local 1.16.5 reference calls `pipeline.beginHand()`, renders the solid hand through `HandRenderer`, then calls `pipeline.beginTranslucents()`. That order captures `depthtex2` before the hand, renders the hand before deferred/pre-translucent depth capture, and lets composite sample the expected live `depthtex0`.

Forge 1.12 `EntityRenderer.renderWorldPass(IFJ)V` instead performs a late vanilla depth clear immediately before `renderHand(float, int)`. If that path is left active while shader deferred/composite passes are enabled, `depthtex0` can reach composite as an all-clear snapshot even though opaque world rendering was valid earlier in the frame.

The active 1.12 port handles this with two mixin boundaries:

- `WorldRendererMixin` invokes the private `EntityRenderer.renderHand(float, int)` at the translucent block-layer boundary after `pipeline.beginHand()` and before `pipeline.beginTranslucents()`, preserving the projection/modelview matrix stacks around the private call.
- `LevelRendererMixin` skips the late vanilla `GlStateManager.clear(256)` and duplicate late `renderHand(...)` only while a `ShaderWorldRenderingPipeline` is active and not rendering shadows.

The `2026-05-19` Complementary validation runs proved this fixed the all-clear `pre-composite-depthtex0` dump. It did not fix the remaining grey/foggy final composite output, and it did not make the third-person player body appear in pre-deferred color dumps. Treat those as separate follow-up investigations unless new evidence ties them back to this boundary.

Render-target texture allocation and resize also use the same bind-to-edit boundary. `OculusRenderSystem.texImage2D(...)` binds 2D textures through `GlStateManager.bindTexture(...)`, performs the upload, and records `TextureLifecycleTracker` metadata. The active `RenderTarget`, `DepthTexture`, and private framebuffer-manager depth texture allocation paths now use that wrapper plus `OculusRenderSystem.texParameteri(...)` instead of raw `GL11.glBindTexture(GL_TEXTURE_2D, ...)`, `glTexImage2D`, and 2D `glTexParameteri` calls. Constructors still defensively unbind texture `0` after initialization like the local 1.16.5 helpers; resize paths leave the last edited texture bound, matching the reference non-DSA shape.

The older `net.oculus.pipeline.framebuffer.FramebufferManager` private cache is not the primary `ShaderWorldRenderingPipeline` framebuffer owner, but it still compiles inside the Agent 2 ownership area. Its private color, depth, and noise texture setup now follows the same wrapper boundary and unbinds texture `0` through `GlStateManager`; it no longer duplicates `TextureLifecycleTracker.onTexImage2D(...)` beside raw uploads. Its fallback noise size now comes from `PackDirectives.getNoiseTextureResolution()`, and generated pixels use the same fresh `Random(0)` x-major assignment shape as the inspected 1.16.5 fallback noise texture.

Shadow target texture allocation follows that same setup boundary for the 1.12-owned `ShadowMap` texture holder. Shadow depth and color texture setup now uses `OculusRenderSystem.texImage2D(...)`, `texParameteri(...)`, and `texParameter(...)` for depth swizzles, then unbinds texture `0` through `GlStateManager`. The no-translucents shadow depth copy now uses `OculusRenderSystem.copyTexSubImage2D(...)`, whose 1.12 non-DSA path saves and restores the previous 2D binding through the `GlStateManager`-aware bind helper like the local 1.16.5 `IrisRenderSystem.copyTexSubImage2D(...)`; the validation-only shadow depth readback also restores its sampled texture through `GlStateManager.bindTexture(...)`. Shadow mipmap generation now uses `OculusRenderSystem.setActiveTextureUnit(...)`, `generateMipmaps(...)`, and `texParameteri(...)` on the dedicated `GL_TEXTURE4` unit, restores that unit's previous binding through `GlStateManager.bindTexture(...)`, and then restores the default texture unit through `OculusRenderSystem.restoreDefaultActiveTexture()`.

The center-depth postprocess sampler follows the same setup boundary for its owned 1x1 color texture pair. `CenterDepthSampler.setupColorTexture(...)` uses `OculusRenderSystem.texImage2D(...)` and `texParameteri(...)`, then constructor cleanup unbinds texture `0` through `GlStateManager` like the local 1.16.5 `RenderSystem.bindTexture(0)` boundary. The constructor copies generated texture IDs into final locals before the setup lambda so the path stays Java 8-compatible without changing the GL setup order. The sampling copy path now binds the 1x1 framebuffer for reading and copies into the alternate center-depth texture through `OculusRenderSystem.copyTexSubImage2D(...)`, matching the local 1.16.5 `DepthCopyStrategy.Gl20CopyTexture` fallback through `IrisRenderSystem.copyTexSubImage2D(...)` while keeping the 1.12 texture cache synchronized.

`FramebufferCompatibilityTest` scans the active source roots for direct GL30 framebuffer creation, bind, attachment, check, and deletion calls, and for split read/draw `OpenGlHelper` binds. If that test fails, fix the code by routing through the 1.12 dispatcher and unified framebuffer target rather than removing the test.

## Buffer Flips

`BufferFlipper` tracks whether each logical render target currently reads from the main or alternate texture.

Current model:

- Not flipped: readers sample the main texture; writers target the alternate texture.
- Flipped: readers sample the alternate texture; writers target the main texture.
- Explicit stage pre-flips are applied before pass creation and do not count as `flippedAtLeastOnce`.
- Stage custom texture overrides for exact `colortexN` names and exact legacy aliases stop applying after a target has been flipped at least once in that stage, matching the case-sensitive 1.16.5 `CustomTextureSamplerInterceptor` rule.

When auditing a pass-order bug, inspect `BufferFlipper`, `CompositeRenderer`, `FinalPassRenderer`, `CustomTextureManager`, and the pack's `flip.*` and draw-buffer directives together. A single class rarely tells the whole story.

## Postprocess Samplers And Units

Composite and final raster/compute programs rely on `ProgramBuilder` and `ProgramSamplers` to bind render targets, shadow textures, custom textures, generated or custom noise, and other samplers.

Current source-backed invariants:

- Postprocess programs reserve texture units `1` and `2`, matching the 1.16.5 postprocess builder path.
- Alias sampler names that share the same `TextureBinding` can reuse an already allocated unit.
- Sampler allocation is bounded by `SamplerLimits` and must not assume 16 available units.
- Cleanup unbinds every reported sampler unit instead of a hard-coded range.
- Render-target sampler aliases keep the local 1.16.5 fullscreen split. Composite and final fullscreen programs bind from render-target index `0`, while non-fullscreen/shadow helper setup starts at index `4`. Render-target image aliases remain separate and bind all configured targets.
- Fullscreen default render-target samplers keep texture unit `0`. If active-uniform discovery already created the same render-target alias group on unit `0`, `ProgramSamplers.Builder.addDefaultSampler(...)` may replace those same-alias bindings with the explicit default binding before adding the remaining aliases. Reserved unit `0` or an unrelated managed unit-`0` sampler is still a hard setup failure.
- Low texture-unit sampler binds must update both the 1.12 `GlStateManager` cache and the real OpenGL active unit. High sampler units are outside the vanilla cache, so a high-unit `OpenGlHelper.setActiveTexture(...)` call can leave `GlStateManager` believing unit `0` is still active. `OculusRenderSystem.setActiveTextureUnit(...)` therefore forces `OpenGlHelper.setActiveTexture(...)` after `GlStateManager.setActiveTexture(...)` for low cached units. The 2026-05-20 Complementary validation proved this matters: before the fix, `deferred1 colortex0` was expected to sample render-target texture `52` but actually sampled atlas texture `8` on unit `0`, causing the grey/atlas-smeared final output; after the fix, `deferred1 colortex0` sampled texture `52` and `composite1 colortex0` sampled texture `53`.
- Compute dispatch inserts the 1.16.5 pre-dispatch texture/image memory barrier only when `allowConcurrentCompute` is false. Composite and final renderers own their post-dispatch barriers before later texture/image consumers; the inspected shadow path intentionally has no renderer-level post-dispatch barrier in target preparation or immediately after shadow-map rendering.
- Gbuffer and composite pass framebuffers are created with their draw-buffer state up front. The active render path must not allocate or call `glDrawBuffers(...)` again after `framebuffer.bind()`.
- Final and composite passes should not reintroduce a separate manual render-target binding pass unless the exact 1.16.5 path and 1.12 runtime evidence require it.
- Present composite/final raster and compute shaders are fail-fast during postprocess construction, matching the local 1.16.5 renderer. Do not turn shader creation failures into logged-and-skipped passes; absence of a valid final raster source is the only baseline-copy case.

## Minecraft Main Framebuffer Boundary

In Forge 1.12.2, raw framebuffer `0` is not generally equivalent to the Minecraft main framebuffer when FBOs are enabled. Code that finishes a composite/final phase should rebind `Minecraft.getFramebuffer().bindFramebuffer(true)` when available. Raw framebuffer `0` is only a fallback for non-client or unavailable-framebuffer contexts.

This is especially important after composite compute/raster work and after final output or color-space conversion.

The same boundary now applies to render-target preparation clears. `ShaderWorldRenderingPipeline.clearRenderTargets()` restores the default texture unit before the clear pass block, then uses a `finally` cleanup to rebind `Minecraft.getFramebuffer().bindFramebuffer(true)` when available. This mirrors the local 1.16.5 `DeferredWorldRenderingPipeline.prepareRenderTargets()` boundary where target preparation starts from texture unit 0 and ends by binding the main render target, while keeping the 1.12 raw framebuffer `0` fallback for non-client or unavailable-framebuffer contexts.

LWJGL 2 buffer overloads for these target-preparation and postprocess state snapshots need 16 elements of storage even when OpenGL returns fewer values. The active clear, composite, final, center-depth, shadow, color-space, and single-float supplier paths use 16-element `ByteBuffer`, `FloatBuffer`, or `IntBuffer` allocations for `glGetBoolean`, `glGetFloat`, and `glGetInteger` state reads that are restored after the pass.

The gbuffer no-program fallback uses that same main-framebuffer helper. If `ShaderWorldRenderingPipeline.bindGbufferFramebuffer(...)` is reached before any shader programs are available, it rebinds `Minecraft.getFramebuffer().bindFramebuffer(true)` and marks the gbuffer unbound instead of binding raw framebuffer `0`. Raw framebuffer `0` remains only the helper fallback for contexts where no Minecraft framebuffer exists.

The world render-stage boundary around those clears also follows the reference. `beginLevelRendering()` leaves `renderStage` / `WorldRenderingPhase` at `NONE` through the frame notifier and render-target preparation, then switches to `SKY` after clears are complete. At the other end of the frame, `finalizeLevelRendering()` resets the phase and override phase and marks fullscreen postprocess scope before center-depth sampling, composite, final, and color-space processing so fullscreen passes do not inherit the last world draw phase or resync world gbuffer programs.

The final pass has an additional main-framebuffer ownership guard. The local 1.16.5 renderer uses `Blaze3dRenderTargetExt.iris$getColorBufferVersion()` so its `colorHolder` framebuffer reattaches the main color texture when Minecraft recreates the color buffer, even if the GL texture id is reused. The 1.12 port mirrors that with `FramebufferVersionMixin` on `net.minecraft.client.shader.Framebuffer.deleteFramebuffer()`, exposed through `MinecraftFramebufferExt`. `FinalPassRenderer.ensureColorHolderAttachment(...)` now checks both `framebufferTexture` and `oculus$getColorBufferVersion()` before deciding whether to skip or refresh the `colorHolder` color attachment. This is source and bytecode-backed for the 1.12 lifecycle, but live resize/reload validation is still required.

## Final Pass Ordering

The current final pass is expected to preserve this 1.16.5 ordering:

1. Dispatch final compute programs.
2. Issue the post-compute memory barrier on the final raster path, matching the 1.16.5 final renderer even when no final compute source was present.
3. Generate requested render-target mipmaps.
4. Render the final quad or perform the baseline copy path.
5. Reset render-target mipmap filters.
6. Copy flipped alternate textures back to main textures through swap framebuffers.
7. Bind the Minecraft main framebuffer.
8. Clear active uniforms, samplers, and program state.
9. Unbind all reported sampler texture units.

Do not insert `Program.unbind()` immediately after the final quad, and do not clear the active GL program inside the baseline-copy helper. In the local 1.16.5 renderer, program/uniform/sampler cleanup happens after mipmap reset and swap-copy passes for both final-raster and baseline-copy paths.

Final swap-copy passes bind each `swapPass.from` framebuffer with `bind()`, bind that pass's target texture, and issue `glCopyTexSubImage2D(...)`. Do not add a one-off `GlStateManager.bindTexture(0)` after the swap loop; the inspected 1.16.5 final renderer leaves texture cleanup to the final sampler-unit unbind pass after the main framebuffer is rebound and uniform/sampler/program state is cleared.

`FullScreenQuadRenderer` owns matrix setup and depth-test toggling only. It must not force depth-mask, blend, alpha, or framebuffer state back to legacy defaults; callers own those states.

## Verification Needed

The current code has compile and unit-test evidence, but this lifecycle is still runtime-unverified in Minecraft 1.12.2. Before claiming parity, run a real client with `run/shaderpacks/ComplementaryReimagined_r5.6.1` and inspect:

- reload teardown after forced or accidental pipeline destroy failures; one failed dimension pipeline cleanup should not leave later dimensions, render-target textures, sampler bindings, or the Relictium/Sodium reload version stale
- framebuffer completeness errors
- stale depth attachments after window resize
- missing or conflicting sampler/image bindings
- incorrect ping-pong history across deferred, composite, and final
- render-target custom texture overrides before and after flips
- final-output state leaks into vanilla UI/world rendering
- GL errors around mipmap generation, compute barriers, final swap copies, and color-space conversion

Record runtime evidence in `docs/agent-handoff.md`, `docs/backport-status.md`, and `docs/verification.md`.
