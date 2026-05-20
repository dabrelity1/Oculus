# Agent Playbook

Last updated: 2026-05-16.

This playbook is for future AI agents working in this repository. It explains how to regain context quickly, how to make changes without damaging unrelated work, and how to decide whether a slice is actually done.

## Current Truth Model

Use this order of authority:

1. Current source in this `oculus-1.12.2` directory.
2. The local 1.16.5 Oculus reference at `../Oculus-1.16.5`.
3. The local Complementary target pack at `run/shaderpacks/ComplementaryReimagined_r5.6.1`.
4. Installed Forge 1.12.2, MCP, and Relictium bytecode/classes.
5. Documentation in `docs/`.

Documentation is a navigation aid, not proof. If docs and source disagree, inspect the source and update the docs after the change.

## Required First Pass

Before editing rendering, shader, resource, or mixin behavior, read:

- `AGENTS.md`
- `README.md`
- `docs/README.md`
- `docs/agent-quickstart.md`
- `docs/ai-agent-context.md`
- `docs/agent-handoff.md`
- `docs/documentation-standard.md`
- `docs/repo-map.md`
- `docs/source-flow-guide.md`
- `docs/subsystem-index.md`
- `docs/backport-status.md`
- `docs/architecture.md`
- `docs/parity-roadmap.md`
- `docs/evidence-log.md`
- The matching 1.16.5 reference classes under `../Oculus-1.16.5`

For shader-pack or directive work, also read:

- `docs/directive-support.md`
- `docs/uniform-gap-analysis.md`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/shaders.properties`

For custom uniform `smooth([id], ...)` behavior, also read:

- `docs/custom-uniform-smooth-semantics.md`

For render-target, depth-texture, framebuffer, buffer-flip, or final/composite work, also read:

- `docs/render-target-lifecycle.md`

For terrain, vertex format, or Relictium work, also read:

- `docs/relictium-integration.md`
- The installed Relictium jar with `javap -c -p`

For modern-package compatibility shims, old `net.coderbot.iris` code, or non-primary mixin JSON resources, also read:

- `docs/legacy-shims-and-remnants.md`

## Workspace Rules

- The worktree is intentionally dirty. Do not run broad reset, checkout, cleanup, or formatting commands.
- Keep changes scoped to the subsystem you are actually porting or documenting.
- Read a file before editing it. Many files have been touched by earlier port work and may not match upstream exactly.
- Normalize line endings only for files you intentionally edit.
- Do not delete generated or dirty files unless the user asks for that exact cleanup.
- Do not claim full port completion from a green build.

## Standard Investigation Flow

When closing a gap:

1. Identify the active 1.12.2 entry point in `docs/repo-map.md`.
2. Open the corresponding 1.16.5 reference implementation.
3. Inspect the real Complementary usage if the feature affects shader-pack behavior.
4. Inspect 1.12.2 Forge/MCP or Relictium bytecode before using any method name, hook, descriptor, or field.
5. Implement the smallest production-quality slice that preserves the reference behavior.
6. Add focused tests when the behavior is source parsing, config, directives, uniforms, transforms, or pure Java state.
7. Run the verification commands that match the slice.
8. Update the relevant docs with exact status and remaining validation.

Do not shortcut the reference step. Names in this repo often mirror modern Iris/Oculus names even when the actual runtime target is old Forge 1.12.2 code.

## Build Commands

Use Java 8 and the wrapper jar:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain build --stacktrace
```

Use `compileJava` for quick Java and mixin annotation processing feedback. Use `test` for focused regression coverage. Use `build` before handing off a completed implementation slice.

## Runtime Validation Standard

For rendering behavior, compile and tests are not enough. A slice is production-confidence only after a 1.12.2 client run proves the affected behavior with a real shader pack.

Minimum runtime evidence for rendering slices:

- Client launches with Oculus and the target pack enabled.
- Logs have no shader compile failures, missing uniform/sampler/image warnings, framebuffer incompleteness, mixin failures, or GL errors connected to the slice.
- Screenshots or captured observations cover the affected pass or scene.
- Shader options and config values that drive the feature are toggled at least once.
- Any 1.16.5 behavior that cannot map exactly to 1.12.2 is documented with the reason.

Use `docs/verification.md` for the longer checklist.

## Documentation Duties

Every meaningful change should follow `docs/documentation-standard.md` and update at least one doc:

- `docs/backport-status.md`: current implemented, unverified, and high-risk areas.
- `docs/ai-agent-context.md`: high-level workspace facts, source-backed invariants, current traps, and next best work.
- `docs/agent-handoff.md`: current resume context, recent verification evidence, and recommended next audits.
- `docs/parity-roadmap.md`: remaining work and evidence needed for full-port completion.
- `docs/evidence-log.md`: source-backed findings, bytecode checks, shader-pack observations, and exact blockers where a change was intentionally deferred.
- `docs/repo-map.md`: entry points and package ownership.
- `docs/source-flow-guide.md`: selection/reload, metadata-to-runtime, shader source, render-loop, postprocess, shadow, uniform, terrain, and resource-lifetime flow.
- `docs/subsystem-index.md`: source/reference/test/doc ownership by subsystem.
- `docs/architecture.md`: runtime flow and subsystem ownership.
- `docs/render-target-lifecycle.md`: render-target allocation, depth texture lifecycle, framebuffer ownership, buffer flips, sampler policy, and final/composite ordering.
- `docs/directive-support.md`: `shaders.properties`, const directives, and feature flags.
- `docs/custom-uniform-smooth-semantics.md`: `smooth([id], ...)` state ownership and duplicate-ID evidence.
- `docs/uniform-gap-analysis.md`: uniforms, samplers, images, and expression support.
- `docs/relictium-integration.md`: Relictium/Sodium-like terrain hooks.
- `docs/legacy-shims-and-remnants.md`: compatibility shims, copied package remnants, and active/inactive mixin config assumptions.
- `docs/verification.md`: build, mixin, runtime, and shader-pack validation process.

Keep status words precise:

- Implemented: parsed, wired, tested where possible, and no known missing code path.
- Partial: some behavior exists, but important reference behavior is absent or not wired.
- Parsed only: metadata is read but does not affect runtime behavior.
- Unverified: code exists, but in-client behavior has not been proven.
- Missing: no current support found.

## Common Pitfalls

- `../Oculus-1.16.5` is the reference; parent-project copied `net/coderbot/iris` sources are not the active 1.12.2 port.
- Modern-looking package names inside this project are not proof of active behavior. Check `docs/legacy-shims-and-remnants.md` and current call sites before relying on `com.mojang.*`, in-project `net.coderbot.iris.*`, or extra mixin JSON resources.
- Old `run/logs` files can be stale. Treat them as clues until reproduced in a current client run.
- Forge 1.12.2 uses `EntityRenderer`, `RenderGlobal`, `GlStateManager`, fixed-function matrices, and MCP names. Modern `RenderSystem` assumptions are usually wrong.
- Relictium classes use `me.jellysquid.mods.sodium.*` package names. Verify bytecode before changing `remap=false` mixins.
- `gl_TextureMatrix[0]` and `gl_TextureMatrix[1]` are not interchangeable. Non-availability source patching still uses the targeted `gl_TextureMatrix[1]` to `iris_LightmapTextureMatrix` fallback, while non-core availability-specific world/shadow variants always inject the fuller `iris_TextureMatrix[8]` replacement array and rename original `gl_TextureMatrix` references when present.
- `InputAvailability` matters for world/shadow source transforms. The current port compiles availability variants for `gbuffers_*` and root shadow programs, adapts `gl_MultiTexCoord0/1/2` to proven 1.12 texture-coordinate indices, injects the availability texture-matrix array, tracks 1.12 brightness-overlay availability from `OpenGlHelper.GL_TEXTURE2`, copies vanilla `RenderLivingBase.brightnessBuffer` into the existing `entityColor` uniform, and binds declared `iris_overlay` samplers to either the 1.12 overlay unit or a white fallback. Do not flip the lightmap alias back to the 1.16 direction unless you also prove 1.12 is uploading lightmap UVs to coordinate unit `2`; current bytecode proves unit/index `1`. Do not implement the modern colored overlay transform by sampling only the 1.12 brightness texture: the inspected texture is white and the color source is fixed-function texture-env state.
- The root `shadow` raster program is not owned by `ShadowRenderer`. Keep it in generic `ShaderLoader` availability variants and let `ShaderWorldRenderingPipeline` sync it before the shadow-map draw, matching the 1.16.5 pass table. `ShadowRenderer` owns framebuffer/draw/culling/compute work only.
- Shadow render-target read buffers depend on `prepareBeforeShadow` for root shadow raster programs. If prepare runs after shadows, root shadow bindings must read the empty pre-shadow flip set, not `flippedAfterPrepare`. Shadow compute bindings always use that empty pre-shadow set.
- Shadow target preparation is one ordered block: depth clear, shadow compute dispatch/barrier, then shadow color clear. It must run before `prepareBeforeShadow` and before the root shadow raster program is synced, otherwise compute dispatch can leave a compute program bound for shadow terrain. On the current 1.12 hook layout it also waits until `afterCameraSetup(...)` has refreshed camera-dependent custom-uniform caches.
- The shared `FrameUpdateNotifier` is not the same as camera-dependent custom-uniform pre-evaluation. It runs in `beginLevelRendering()` after source-built programs register non-camera smoothed built-in listeners and before render-target clears, matching the 1.16.5 frame-update-before-prepare boundary for values that do not need the 1.12 post-camera hook. `GameplayUniforms.onFrameStart()`, `CompatibilityUniforms.onFrameStart()`, and `customUniforms.beginFrame()` still wait for `afterCameraSetup(...)`.
- The `AttributeTransformer` profile gate is intentional. Availability-backed attribute transforms throw `Vertex shaders must be in the compatibility profile to run properly!` for explicit core-profile vertex sources and unprofiled GLSL versions greater than `140`; core-profile non-vertex sources skip those attribute rewrites. The profile detector must not use Java `\s+` after `#version`, because it can cross the newline and mistake the next source token for a profile.
- 1.12 entity models usually draw display lists through `GlStateManager.callList`, not `glDrawArrays`. Keep the display-list program sync hook unless you have stronger runtime evidence; removing it can leave uniforms such as `entityColor` stale during vanilla model rendering.
- A missing uniform warning in an old log may already be fixed. Confirm against current `ProgramBuilder` and a fresh run.
- `smooth([id], ...)` ID behavior is not proven from source yet. Public docs require unique IDs, but local Complementary reuses IDs, so do not switch to keyed global state without source-level proof and a target-pack test.
- Shader source transforms must skip comments and strings. Add tests for that explicitly.
- Mixin compile success does not guarantee runtime injection success. Inspect generated refmaps and target descriptors.
- If an audit cannot be completed because exact source, bytecode, mappings, or runtime data is missing, add the blocker to `docs/evidence-log.md` instead of implementing from memory.

## Good Handoff Shape

When ending a work slice, leave the next agent with:

- Files changed.
- Feature behavior implemented.
- Tests or commands run.
- Runtime validation status.
- Any known remaining gap, with the exact file or subsystem to inspect next.

If the slice cannot be completed safely, document the blocker and the exact data needed instead of guessing.
