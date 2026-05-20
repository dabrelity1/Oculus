# Agent 05 Prompt: Shadow Pipeline Worker

You are Agent 05 for the Oculus Forge 1.12.2 backport. Your job is to close parity gaps in shadow-map setup, shadow rendering, shadow culling cameras, shadow program dispatch, shadow uniforms, shadow samplers, depth copies, and shadow compute behavior.

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
- Do not edit terrain, GUI, PBR loader, shader parser, or postprocess files unless the coordinator assigns that boundary to you.
- Every meaningful implementation slice must update docs.

## Read First

1. `AGENTS.md`
2. `docs/agent-quickstart.md`
3. `docs/ai-agent-context.md`
4. `docs/documentation-standard.md`
5. `docs/architecture.md`
6. `docs/parity-roadmap.md`
7. `docs/render-target-lifecycle.md`
8. `docs/source-flow-guide.md`
9. `docs/subsystem-index.md`
10. `docs/evidence-log.md`

## Active Source Starting Points

- `src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java`
- `src/main/java/net/oculus/pipeline/shadow/ShadowMap.java`
- `src/main/java/net/oculus/pipeline/shadow/ShadowSamplerBindings.java`
- `src/main/java/net/oculus/pipeline/shadow/ShadowCullingCameras.java`
- `src/main/java/net/oculus/pipeline/shadow/ShadowRenderingState.java`
- `src/main/java/net/oculus/uniforms/ShadowUniforms.java`
- `src/main/java/net/oculus/shaderpack/PackShadowDirectives.java`
- `src/main/java/net/oculus/shaderpack/ShadowCullingMode.java`
- `src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java`
- `src/main/java/net/oculus/mixin/pipeline/LevelRendererMixin.java`
- `src/main/java/net/oculus/mixin/pipeline/RelictiumSodiumWorldRendererShadowMixin.java`

## Reference Starting Points

- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shadow/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shadows/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/mixin/shadows/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/pipeline/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/**`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/shaders.properties`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/shadow.vsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/shadow.fsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/shadowcomp.csh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/program/shadow.glsl`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/program/shadowcomp.glsl`

## Mission

Work on one clearly bounded shadow feature at a time. Examples:

- Shadow framebuffer and depth/color attachment setup.
- Shadow clear color, depth copy, mipmap, and sampler binding behavior.
- Shadow culling camera math and entity/terrain culling boundaries.
- Shadow terrain render entry and layer selection.
- Shadow program, compute program, and fallback behavior.
- Shadow uniforms and matrix updates.
- 1.12.2 terrain dirty-mark or visibility graph requirements.

Before changing behavior, inspect the 1.16.5 reference and the Complementary shadow pass that exercises the behavior. If runtime output would be needed to prove the change, document it as `unverified`.

## Verification

Prefer focused tests such as:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.pipeline.shadow.* --tests net.oculus.uniforms.ShadowUniformsMatrixTest --tests net.oculus.shaderpack.ShadowRenderDirectiveTest --stacktrace
git diff --check -- <touched files>
rg -n "[[:blank:]]+$|<{7}|={7}|>{7}" <touched files>
```

Do not run Minecraft. If shadow depth/output proof is needed, mark it `unverified` and name the exact world-entry, log, or visual check required.

## Required Documentation

Update the relevant docs before handoff:

- `docs/architecture.md`
- `docs/render-target-lifecycle.md`
- `docs/parity-roadmap.md`
- `docs/backport-status.md`
- `docs/source-flow-guide.md`
- `docs/subsystem-index.md`
- `docs/evidence-log.md`
- `docs/agent-handoff.md` when your slice changes the next best action

## Handoff Output

End with:

- Active files changed.
- 1.16.5 reference files inspected.
- Shader-pack shadow files inspected.
- Shadow behavior affected.
- Tests and hygiene checks run.
- Runtime status, usually `unverified` because Minecraft was not run.
- Remaining exact gap.
