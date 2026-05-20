# Agent Guide

This file is for AI agents and humans joining the Oculus Forge 1.12.2 backport. It describes how to navigate the repo without losing context or accidentally undoing useful work.

## Mission

Backport Oculus from Forge 1.16.5 behavior to Forge 1.12.2 faithfully. Do not replace missing behavior with stubs, simplified approximations, or fake completion claims. If a feature is not implemented and verified, document it as incomplete.

## Reference Material

- `../Oculus-1.16.5`: primary reference. Read this before porting any subsystem.
- `run/shaderpacks/ComplementaryReimagined_r5.6.1`: extracted real shader pack target used to confirm directives, program layout, uniforms, samplers, images, and render passes.
- `run/shaderpacks/ComplementaryReimagined_r5.6.1.zip`: packaged copy of the same target; `ShaderPackLoaderComplementaryTest` covers both directory and zip loading.
- `run/shaderpacks/MakeUp-UltraFast-9.3e`: secondary real shader pack target for OptiFine-style scalar/vector custom expressions, especially TAA offset directives.
- Relictium: no standalone `../Relictium` source directory is visible in this Linux workspace, but the project contains Relictium bridge code and depends on Relictium artifacts. Search `OculusRelictiumBridge`, `MultidrawChunkRenderBackendMixin`, and Gradle dependencies when working on terrain integration.

## Do Not Assume

- Do not guess MCP method names or signatures. Inspect Forge 1.12.2 classes or existing mixins.
- Do not trust old docs in this repo unless they were updated for the current tree. Prefer current source and the docs under `docs/`.
- Do not claim "full port complete" from compile success. A full port requires in-game visual/runtime validation and directive-by-directive parity.
- Do not revert unrelated dirty worktree changes.

## Build Commands

Use Java 8:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain build --stacktrace
```

Notes:

- `build` currently runs `compileTestJava` and `test`; keep it that way unless a test is replaced by a stronger verification path.
- Focused `git diff --check -- <files>` is useful before finalizing a slice.
- Runtime/client validation is still required for production confidence.

## High-Value Entry Points

- `docs/repo-map.md`: fastest subsystem map for future agents; read this before doing broad exploration.
- `docs/README.md`: documentation index and task-based lookup table.
- `docs/agent-quickstart.md`: first-page checklist for cold-start agents, including active paths, search recipes, source entry points, verification commands, and docs to update.
- `docs/ai-agent-context.md`: compact context pack for cold-start agents; includes local layout, source-backed facts, current traps, and next best work.
- `docs/parallel-agents/README.md`: ready-to-paste scoped prompts for running coordinator, implementation, and documentation agents in parallel without overlapping ownership.
- `docs/agent-handoff.md`: current resume point, recent verification evidence, and the next likely high-value investigations.
- `docs/documentation-standard.md`: durable documentation contract; use it to decide what evidence and handoff context each implementation slice must leave behind.
- `docs/agent-playbook.md`: exact working procedure for future agents, including reference order, common traps, and handoff shape.
- `docs/subsystem-index.md`: subsystem-by-subsystem source/reference/test/doc ownership map.
- `docs/parity-roadmap.md`: strict remaining-work map for reaching full-port parity.
- `docs/evidence-log.md`: source-backed findings, bytecode checks, shader-pack evidence, and exact blockers where guessing would be unsafe.
- `docs/source-flow-guide.md`: flow-oriented guide for tracing pack reloads, shader source preparation, render passes, shadows, uniforms, terrain, and resource lifetime.
- `docs/render-target-lifecycle.md`: render-target, depth-texture, framebuffer ownership, buffer-flip, and final/composite lifecycle notes.
- `docs/custom-uniform-smooth-semantics.md`: focused evidence trail for custom expression `smooth([id], ...)` state behavior and duplicate-ID risk.
- `docs/legacy-shims-and-remnants.md`: compatibility shim and inactive copied-resource warning map.
- `net.oculus.shaderpack.ShaderPackLoader`: pack discovery, preprocessing, option values, properties, id maps, dimensions.
- `net.oculus.shaderpack.ShaderProperties`: `shaders.properties` parsing.
- `net.oculus.blockrendering.BlockMaterialMapping`: `block.properties` material ID mapping and 1.12 split-block compatibility aliases.
- `net.oculus.shaderpack.PackDirectives`, `PackRenderTargetDirectives`, `PackShadowDirectives`: resolved pack directives.
- `net.oculus.shaderpack.ProgramSet`: program and compute source discovery, disabled program gating, directive collection.
- `net.oculus.shaderpack.ShaderPack.internal()`: null-object pack used when no external pack is selected; do not treat this as completed external shader-pack behavior.
- `net.oculus.pipeline.ShaderWorldRenderingPipeline`: main world pipeline, gbuffer binding, prepare/deferred/composite/final orchestration, shadow entry.
- `net.oculus.pipeline.shadow.ShadowRenderer`: shadow framebuffer, shadow program/compute, shadow culling camera selection, and the pre-shadow 1.12 terrain dirty mark before `RenderGlobal.setupTerrain(...)`.
- `net.oculus.postprocess.CompositeRenderer`, `FinalPassRenderer`, `BufferFlipper`: post-processing and buffer swapping.
- `net.oculus.colorspace.*`: 1.16.5 color-space enum, config parsing, compute converter, GLSL 120 fragment fallback, and no-op path for packs that declare `supportsColorCorrection=true`.
- `net.oculus.rendertarget.RenderTargets`, `DepthTexture`: color/depth target ownership and snapshots.
- `net.oculus.gl.program.ProgramBuilder`, `ProgramSamplers`, `ProgramImages`: shader program construction, sampler/image bindings.
- `net.oculus.gl.state.StateUpdateNotifiers`: active-program uniform refresh hooks for GL texture, blend, fog, and phase state.
- `net.oculus.uniforms.*`: built-in and compatibility uniform registration/state.
- `net.oculus.uniforms.custom.CustomUniformExpressionManager`: `uniform.*` and `variable.*` expression support.
- `net.oculus.uniforms.transforms.ExponentialSmoothing`: shared Iris-compatible half-life smoothing math; zero half-life must converge immediately.
- `src/main/java/com/mojang/*` and `src/main/java/net/coderbot/iris/*`: legacy shims/remnants. Read `docs/legacy-shims-and-remnants.md` before treating any of them as active runtime behavior.
- `src/main/resources/oculus.mixins.json`: primary client mixin list.

## Mixin Hot Spots

- `mixin/pipeline/LevelRendererMixin`: world render entry/exit, shadow render injection, particles, hand/weather phases, main frustum override, and main terrain occlusion override.
- `mixin/pipeline/WorldRendererMixin`: sky/cloud/entity/block-entity phase tracking, terrain layer phases, translucent hand/depth transition, and `backFace.*` cull-state application.
- `mixin/pipeline/ParticleManagerMixin`: particle phase filtering.
- `mixin/pipeline/TileEntityBeaconRendererMixin`: beacon beam render condition and depth-mask behavior.
- `mixin/vertexformat/*`: vanilla terrain vertex format, block emission data, and vanilla `separateAo` block/fluid vertex color handling.
- `mixin/pipeline/Relictium*` plus `ChunkBuildBuffersMixin` / `ChunkRenderRebuildTaskMixin`: Relictium terrain context, vertex attribute bindings, Relictium `separateAo` block/fluid hooks, and the Relictium shadow visibility graph swap.
- `mixin/pipeline/GlStateManager*`: GL blend/state tracking.

## Documentation Rules For Future Agents

When landing a meaningful subsystem slice, update:

- [docs/backport-status.md](docs/backport-status.md) with implemented and unverified areas.
- [docs/ai-agent-context.md](docs/ai-agent-context.md) if high-level workspace facts, source-backed invariants, traps, or next best work change.
- [docs/agent-handoff.md](docs/agent-handoff.md) with current resume context when a long-running slice changes direction or evidence.
- [docs/agent-playbook.md](docs/agent-playbook.md) if the expected workflow, traps, or handoff requirements change.
- [docs/subsystem-index.md](docs/subsystem-index.md) when package ownership, entry points, references, or tests change.
- [docs/parity-roadmap.md](docs/parity-roadmap.md) when a full-port blocker is closed or a new blocker is found.
- [docs/evidence-log.md](docs/evidence-log.md) when an audit depends on exact source, bytecode, shader-pack evidence, or when a tempting change is intentionally deferred because proof is missing.
- [docs/repo-map.md](docs/repo-map.md) when adding, removing, or moving subsystem entry points.
- [docs/source-flow-guide.md](docs/source-flow-guide.md) when changing selection/reload, metadata-to-runtime, shader source, render-loop, postprocess, shadow, uniform, terrain, or resource-lifetime flow.
- [docs/directive-support.md](docs/directive-support.md) when adding shader-pack property, directive, or ID-map support.
- [docs/architecture.md](docs/architecture.md) if control flow or ownership changes.
- [docs/render-target-lifecycle.md](docs/render-target-lifecycle.md) if render-target allocation, depth texture lifecycle, framebuffer ownership, buffer flips, sampler binding policy, or final/composite ordering changes.
- [docs/custom-uniform-smooth-semantics.md](docs/custom-uniform-smooth-semantics.md) before changing custom expression `smooth([id], ...)` state ownership.
- [docs/legacy-shims-and-remnants.md](docs/legacy-shims-and-remnants.md) if compatibility shims, old package remnants, or active mixin config assumptions change.
- [docs/verification.md](docs/verification.md) if new commands or runtime checks become required.
- [docs/relictium-integration.md](docs/relictium-integration.md) when changing Relictium/Sodium terrain hooks.

Keep docs factual. Use "implemented", "partially implemented", "parsed only", or "unverified" instead of broad claims.

## Color-Space Notes

- `OculusConfig.colorSpace` is the active config source. It defaults invalid or missing values to `SRGB`.
- `ShaderWorldRenderingPipeline` only applies the Oculus post-final converter when the pack does not declare `supportsColorCorrection=true`.
- `ColorSpaceFragmentConverter` intentionally reuses `/colorSpace.csh` and patches it to GLSL 120 instead of keeping a forked shader copy. If the shared shader changes, update `ColorSpaceShaderSourceTest`.
- `ShaderPackSelectionList` contains the 1.12 GUI selector row. It cycles through `SRGB`, `DCI_P3`, `Display P3`, `REC2020`, and `Adobe RGB`, saves immediately like the 1.16.5 Sodium option, and lets the active pipeline pick up the config change on the next full-screen pass.
