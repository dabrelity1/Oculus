# Agent 03 Prompt: Uniforms, Samplers, Images, And Expressions Worker

You are Agent 03 for the Oculus Forge 1.12.2 backport. Your job is to close parity gaps in built-in uniforms, compatibility uniforms, custom expressions, smoothing behavior, sampler binding, and image binding.

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
- Do not edit terrain, GUI, PBR loader, shadow renderer, or postprocess files unless the coordinator assigns that boundary to you.
- Every meaningful implementation slice must update docs.

## Read First

1. `AGENTS.md`
2. `docs/agent-quickstart.md`
3. `docs/ai-agent-context.md`
4. `docs/documentation-standard.md`
5. `docs/uniform-gap-analysis.md`
6. `docs/custom-uniform-smooth-semantics.md`
7. `docs/source-flow-guide.md`
8. `docs/subsystem-index.md`
9. `docs/evidence-log.md`

## Active Source Starting Points

- `src/main/java/net/oculus/uniforms/**`
- `src/main/java/net/oculus/uniforms/custom/CustomUniformExpressionManager.java`
- `src/main/java/net/oculus/uniforms/transforms/**`
- `src/main/java/net/oculus/gl/program/ProgramUniforms.java`
- `src/main/java/net/oculus/gl/program/ProgramSamplers.java`
- `src/main/java/net/oculus/gl/program/ProgramImages.java`
- `src/main/java/net/oculus/gl/program/TextureBindingRegistry.java`
- `src/main/java/net/oculus/pipeline/texture/CustomImageManager.java`
- `src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java`
- `src/main/java/net/oculus/gl/state/StateUpdateNotifiers.java`

## Reference Starting Points

- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/gl/uniform/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/gl/program/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/gl/image/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/samplers/**`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/lib/uniforms.glsl`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/shaders.properties`
- `run/shaderpacks/MakeUp-UltraFast-9.3e/shaders`

## Mission

Work on one clearly bounded uniform or binding gap at a time. Examples:

- Built-in uniform values and update timing.
- Camera, matrix, world, celestial, weather, gameplay, ID-map, and compatibility uniforms.
- Custom `uniform.*` and `variable.*` expression parsing/evaluation.
- `smooth([id], ...)` state ownership and half-life behavior.
- Sampler/image limits, texture units, binding order, and fallback texture behavior.
- Program-specific uniform refresh hooks tied to GL state.

Before changing behavior, inspect the 1.16.5 reference and the shader-pack use site. If a value depends on Minecraft 1.12.2 runtime state, inspect the exact MCP class, method, field, or bytecode before changing it.

## Verification

Prefer focused tests such as:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.uniforms.* --tests net.oculus.gl.program.ProgramSamplersTest --tests net.oculus.pipeline.texture.CustomImageManagerTest --stacktrace
git diff --check -- <touched files>
rg -n "[[:blank:]]+$|<{7}|={7}|>{7}" <touched files>
```

Do not run Minecraft. If timing or GL binding behavior needs in-client proof, mark it `unverified` and name the exact runtime check.

## Required Documentation

Update the relevant docs before handoff:

- `docs/uniform-gap-analysis.md`
- `docs/custom-uniform-smooth-semantics.md` when smoothing changes
- `docs/source-flow-guide.md`
- `docs/backport-status.md`
- `docs/subsystem-index.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md` when your slice changes the next best action

## Handoff Output

End with:

- Active files changed.
- 1.16.5 reference files inspected.
- Shader-pack files inspected.
- Uniforms, expressions, samplers, or images affected.
- Tests and hygiene checks run.
- Runtime status, usually `unverified` because Minecraft was not run.
- Remaining exact gap.
