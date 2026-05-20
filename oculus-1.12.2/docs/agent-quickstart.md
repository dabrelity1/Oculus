# Agent Quickstart

Last updated: 2026-05-17.

This is the first-page checklist for an AI agent entering the Forge 1.12.2 Oculus backport. It is intentionally short. Use it to avoid the common wrong turns, then move into the deeper docs.

## Ten-Minute Start

1. Confirm you are in `oculus-1.12.2`, not the parent copied source tree.
2. Read `AGENTS.md`, `docs/ai-agent-context.md`, and `docs/agent-handoff.md`.
3. Check `git status --short`, but do not clean or revert the dirty worktree.
4. Find the owning subsystem in `docs/repo-map.md` and `docs/subsystem-index.md`.
5. If you are part of a parallel work split, read `docs/parallel-agents/README.md` and your assigned `docs/parallel-agents/agent*/PROMPT.md`.
6. Use `docs/source-flow-guide.md` when you need to trace how data moves from pack files into runtime rendering.
7. Read `docs/documentation-standard.md` before landing a slice so the evidence trail survives chat compaction.
8. Open the matching 1.16.5 reference class under `../Oculus-1.16.5`.
9. Inspect the target shader-pack usage under `run/shaderpacks/ComplementaryReimagined_r5.6.1` when the behavior affects pack parsing, uniforms, samplers, images, passes, or options.
10. Only then edit code or docs.

The full port is not complete. Compile/test success is useful, but it is not runtime rendering parity.

## Active Versus Distracting Paths

| Path | Meaning |
| --- | --- |
| `src/main/java/net/oculus` | Active Forge 1.12.2 port code. Start here for implementation work. |
| `src/test/java/net/oculus` | Current Java regression tests. Add focused coverage here when behavior is pure Java or source parsing. |
| `../Oculus-1.16.5` | Primary behavior reference. Use it before porting or "fixing" any subsystem. |
| `run/shaderpacks/ComplementaryReimagined_r5.6.1` | Main extracted real shader-pack target. Use it to prove which directives, uniforms, samplers, images, and passes matter. |
| `run/shaderpacks/ComplementaryReimagined_r5.6.1.zip` | Packaged copy of the same target. Keep zip loading covered because normal user installs are usually archives. |
| `run/shaderpacks/MakeUp-UltraFast-9.3e` | Secondary extracted shader-pack target for OptiFine-style expression behavior and broader loader coverage. |
| `run/shaderpacks/MakeUp-UltraFast-9.3e.zip` | Packaged copy of the secondary target; covered by the real-pack loader guard when present. |
| Parent-level `src/` | Not the active 1.12.2 port unless the user explicitly redirects you. |
| In-project `com.mojang.*` and `net.coderbot.iris.*` | Compatibility shims or historical remnants unless current call sites prove active runtime behavior. Read `docs/legacy-shims-and-remnants.md`. |

## Search Recipes

Use `rg` first:

```bash
rg "class ShaderWorldRenderingPipeline|class ProgramSet|class ShadowRenderer" src/main/java
rg "HardcodedCustomUniforms|BuiltinReplacementUniforms|DeferredWorldRenderingPipeline" ../Oculus-1.16.5/src/main/java
rg "uniform\\.|variable\\.|program\\.|texture\\.|image\\.|shadow" run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders
rg "MixinName|targetMethod" build/resources/main/oculus.refmap.json build/tmp/compileJava
```

When a method name, descriptor, Forge hook, MCP name, Relictium call, or OpenGL state assumption is uncertain, inspect the actual source or bytecode. Do not guess.

## Subsystem Starting Points

| Work area | Start in active source | Then read |
| --- | --- | --- |
| Pack loading and directives | `ShaderPackLoader`, `ShaderProperties`, `ProgramSet`, `PackDirectives` | `docs/directive-support.md`, `docs/subsystem-index.md` |
| Program compile/link | `JcppProcessor`, `ShaderLoader`, `ShaderSourcePreparer`, `ProgramBuilder`, `ProgramCreator` | `docs/architecture.md`, `docs/source-flow-guide.md`, shader/preprocessor tests |
| Uniforms and expressions | `ProgramBuilder`, `CapturedRenderingState`, `CameraPositionTracker`, `BuiltinReplacementUniforms`, `CompatibilityUniforms`, `WorldInfoUniforms`, `CustomUniformExpressionManager` | `docs/uniform-gap-analysis.md`, `docs/custom-uniform-smooth-semantics.md`, `docs/evidence-log.md` |
| Samplers and images | `ProgramSamplers`, `ProgramImages`, `TextureBindingRegistry`, `CustomImageManager` | `docs/uniform-gap-analysis.md` |
| Render targets and postprocess | `RenderTargets`, `GlFramebuffer`, `CompositeRenderer`, `FinalPassRenderer`, `BufferFlipper` | `docs/render-target-lifecycle.md`, `docs/architecture.md`, `FramebufferCompatibilityTest` |
| Shadows | `ShadowRenderer`, `ShadowMap`, `ShadowSamplerBindings`, `ShadowUniforms` | `docs/architecture.md`, `docs/parity-roadmap.md`, `ShaderWorldRenderingPipelineSourceTest` / `ShadowRendererSourceTest` for target-prep and root-shadow ownership, `FramebufferCompatibilityTest` for FBO dispatch, `ShadowRendererBytecodeTest` for pre-shadow terrain setup/layer/depth-state guards, `ShadowMapBytecodeTest` for depth-copy and mipmap texture-state guards |
| Terrain and Relictium | `OculusRelictiumBridge`, `OculusVertexFormats`, `SeparateAoTracker`, Relictium mixins | `docs/relictium-integration.md` |
| Mixin bootstrap and hooks | `mixins/OculusMixinLoader`, `mixin/pipeline`, `mixin/vertexformat`, `src/main/resources/oculus.mixins.json` | `docs/verification.md`, target bytecode/refmap |
| Config and GUI | `OculusConfig`, `ShaderPackScreen`, `ShaderPackSelectionList` | `docs/backport-status.md` |

## Verification Commands

Use Java 8 and the wrapper jar:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain build --stacktrace
```

For docs-only slices, focused hygiene is enough:

```bash
git diff --check -- <touched files>
rg -n "[[:blank:]]+$|<{7}|={7}|>{7}" <touched files>
```

For rendering slices, also do a Minecraft 1.12.2 client run with a real shader pack. Record logs, screenshots or observations, shader options toggled, and any remaining runtime gap.

For camera uniform timing changes, also run the focused guard from `docs/verification.md`. It pins that current camera and matrices refresh from the `RenderGlobal.setupTerrain(...)` redirect after vanilla camera setup, not from an unmapped `ActiveRenderInfo.updateRenderInfo(Entity, boolean)` injection.

For shared frame-update timing changes, run `ShaderWorldRenderingPipelineSourceTest` and `InternalProgramBuilderPathTest`. Keep `FrameUpdateNotifier.onNewFrame()` after non-camera smoothed built-in listener registration but before render-target clears. This mirrors the 1.16.5 `updateNotifier.onNewFrame()` before `prepareRenderTargets()` boundary where the 1.12 value does not need post-camera setup, and is separate from camera-dependent custom-uniform pre-evaluation.

For unattended world-entry logs, use the disabled-by-default runtime validation hook instead of fragile GUI automation:

```bash
timeout 480s /usr/bin/zsh -lc 'JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -Doculus.validation.autoJoinWorld="New World" -Doculus.validation.exitAfterWorldTicks=1200 -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient --stacktrace'
```

This uses Forge's 1.12 GUI world-load path for the named save and requests shutdown after the configured in-world client tick count. When enabled, Relictium terrain override lookup/selection, shadow terrain layer requests, Relictium shadow visibility graph events, and sampled shadow depth readbacks are logged at their runtime call sites. It proves log-visible world entry and pipeline behavior only; screenshots and non-clear depth/output checks are still required for visual parity.

For mixin bootstrap changes, run `OculusMixinLoaderTest` and a bounded `runClient` smoke test. Fresh logs should show MixinBooter adding `oculus.mixins.json` through `net.oculus.mixins.OculusMixinLoader` before late-loader discovery, and should not show the old `BlockStateAmbientOcclusionMixin target ... was loaded too early` crash.

## Documentation Rule

When a slice changes behavior, follow `docs/documentation-standard.md` and update the owning doc before handing off:

- Status or risk: `docs/backport-status.md`
- Remaining full-port work: `docs/parity-roadmap.md`
- Source-backed evidence or blocker: `docs/evidence-log.md`
- Package ownership: `docs/repo-map.md` or `docs/subsystem-index.md`
- Source/data/runtime flow: `docs/source-flow-guide.md`
- Runtime flow: `docs/architecture.md`
- Render targets/postprocess: `docs/render-target-lifecycle.md`
- Directives: `docs/directive-support.md`
- Uniforms/samplers/images/expressions: `docs/uniform-gap-analysis.md`
- Relictium/terrain: `docs/relictium-integration.md`
- Shims/remnants: `docs/legacy-shims-and-remnants.md`
- Verification workflow: `docs/verification.md`
- Resume context: `docs/agent-handoff.md`

Use conservative status words: implemented, partial, parsed only, unverified, missing. Never write that the full port is complete unless `docs/parity-roadmap.md`'s completion bar has been met with Minecraft 1.12.2 runtime evidence.
