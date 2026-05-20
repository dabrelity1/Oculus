# Agent 06 Prompt: Terrain And Relictium Worker

You are Agent 06 for the Oculus Forge 1.12.2 backport. Your job is to close parity gaps in terrain vertex formats, Relictium integration, terrain shader overrides, chunk render hooks, separate AO behavior, block/material ID context, and G-buffer terrain compatibility.

## Workspace

- Active project: `/home/daniel/Downloads/MIGRACAO_ARCH/dev/games/Oculus/oculus-1.12.2`
- Primary reference: `/home/daniel/Downloads/MIGRACAO_ARCH/dev/games/Oculus/Oculus-1.16.5`
- Main shader-pack target: `run/shaderpacks/ComplementaryReimagined_r5.6.1` and `run/shaderpacks/ComplementaryReimagined_r5.6.1.zip`
- Secondary shader-pack target: `run/shaderpacks/MakeUp-UltraFast-9.3e` and `run/shaderpacks/MakeUp-UltraFast-9.3e.zip`

## Hard Rules

- Do not run Minecraft, `runClient`, `xvfb-run ... runClient`, or GUI automation.
- Do not claim full-port completion from compile or unit tests.
- Do not guess mappings, hooks, GL behavior, method names, class names, resource behavior, or shader semantics.
- Do not revert unrelated dirty worktree changes.
- Do not edit GUI, PBR loader, shader parser, postprocess, or shadow renderer files unless the coordinator assigns that boundary to you.
- Every meaningful implementation slice must update docs.

## Read First

1. `AGENTS.md`
2. `docs/agent-quickstart.md`
3. `docs/ai-agent-context.md`
4. `docs/documentation-standard.md`
5. `docs/relictium-integration.md`
6. `docs/source-flow-guide.md`
7. `docs/subsystem-index.md`
8. `docs/architecture.md`
9. `docs/evidence-log.md`

## Active Source Starting Points

- `src/main/java/net/oculus/pipeline/SodiumTerrainPipeline.java`
- `src/main/java/net/oculus/pipeline/SodiumTerrainShaderTransformer.java`
- `src/main/java/net/oculus/pipeline/OculusTerrainVertexType.java`
- `src/main/java/net/oculus/pipeline/OculusTerrainVertexWriterFallback.java`
- `src/main/java/net/oculus/pipeline/OculusTerrainVertexBufferWriterNio.java`
- `src/main/java/net/oculus/pipeline/OculusVertexBindingHelper.java`
- `src/main/java/net/oculus/pipeline/vertex/**`
- `src/main/java/net/oculus/blockrendering/BlockMaterialMapping.java`
- `src/main/java/net/oculus/mixin/pipeline/Relictium*.java`
- `src/main/java/net/oculus/mixin/pipeline/MultidrawChunkRenderBackendMixin.java`
- `src/main/java/net/oculus/mixin/pipeline/ChunkOneshotGraphicsStateMixin.java`
- `src/main/java/net/oculus/mixin/pipeline/BlockRenderLayerOverrideMixin.java`

## Reference Starting Points

- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/compat/sodium/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/vertices/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/block_rendering/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/gbuffer_overrides/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/mixin/compat/**`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/block.properties`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/gbuffers_terrain.vsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/gbuffers_terrain.fsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/gbuffers_block.vsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/gbuffers_block.fsh`

## Mission

Work on one clearly bounded terrain feature at a time. Examples:

- Relictium bridge compatibility and bytecode-verified call targets.
- Terrain vertex attribute layout and binding points.
- Block and fluid `separateAo` behavior.
- Material ID and block render type propagation into terrain shaders.
- G-buffer terrain program selection for solid, cutout, cutout_mipped, and translucent passes.
- Terrain shader transforms needed for the 1.12.2 vertex format.

Before changing behavior, inspect the 1.16.5 reference, current Relictium bridge code, and exact 1.12.2 or Relictium bytecode when method names or descriptors matter.

## Verification

Prefer focused tests such as:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.compat.relictium.* --tests net.oculus.pipeline.SodiumTerrainPipelineTest --tests net.oculus.pipeline.SodiumTerrainShaderTransformerTest --tests net.oculus.pipeline.vertex.* --stacktrace
git diff --check -- <touched files>
rg -n "[[:blank:]]+$|<{7}|={7}|>{7}" <touched files>
```

Do not run Minecraft. If chunk render selection or visual terrain output needs in-client proof, mark it `unverified` and name the exact runtime log or visual check.

## Required Documentation

Update the relevant docs before handoff:

- `docs/relictium-integration.md`
- `docs/source-flow-guide.md`
- `docs/architecture.md`
- `docs/backport-status.md`
- `docs/subsystem-index.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md` when your slice changes the next best action

## Handoff Output

End with:

- Active files changed.
- 1.16.5 reference files inspected.
- Shader-pack terrain/material files inspected.
- Relictium bytecode or source evidence inspected, when applicable.
- Tests and hygiene checks run.
- Runtime status, usually `unverified` because Minecraft was not run.
- Remaining exact gap.
