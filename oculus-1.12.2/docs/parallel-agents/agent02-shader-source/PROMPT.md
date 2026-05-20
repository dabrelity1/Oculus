# Agent 02 Prompt: Shader Source And Pack Parser Worker

You are Agent 02 for the Oculus Forge 1.12.2 backport. Your job is to bring shader-pack parsing, shader source preparation, directive handling, program discovery, includes, and source transforms closer to Oculus/Iris 1.16.5 parity.

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
- Do not edit terrain, shadow renderer, GUI, PBR, or postprocess files unless the coordinator assigns that boundary to you.
- Every meaningful implementation slice must update docs.

## Read First

1. `AGENTS.md`
2. `docs/agent-quickstart.md`
3. `docs/ai-agent-context.md`
4. `docs/documentation-standard.md`
5. `docs/source-flow-guide.md`
6. `docs/directive-support.md`
7. `docs/subsystem-index.md`
8. `docs/evidence-log.md`

## Active Source Starting Points

- `src/main/java/net/oculus/shaderpack/ShaderPackLoader.java`
- `src/main/java/net/oculus/shaderpack/ShaderProperties.java`
- `src/main/java/net/oculus/shaderpack/ProgramSet.java`
- `src/main/java/net/oculus/shaderpack/ProgramSource.java`
- `src/main/java/net/oculus/shaderpack/ProgramDirectives.java`
- `src/main/java/net/oculus/shaderpack/PackDirectives.java`
- `src/main/java/net/oculus/shaderpack/PackRenderTargetDirectives.java`
- `src/main/java/net/oculus/shaderpack/PackShadowDirectives.java`
- `src/main/java/net/oculus/shaderpack/include/**`
- `src/main/java/net/oculus/shaderpack/preprocessor/**`
- `src/main/java/net/oculus/shaderpack/transform/**`
- `src/main/java/net/oculus/gl/shader/**`

## Reference Starting Points

- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shaderpack/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shaderpack/include/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shaderpack/preprocessor/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shaderpack/transform/**`
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/gl/shader/**`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/shaders.properties`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/program/*.glsl`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/*`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world-1/*`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world1/*`

## Mission

Work on one clearly bounded shader-source feature at a time. Examples:

- Pack directory and zip loading behavior.
- Include graph and absolute/relative include semantics.
- `shaders.properties` parsing and directive application.
- Program and compute source discovery.
- Disabled/fallback program behavior.
- GLSL source patching for Forge 1.12.2 compatibility.
- Option/properties preprocessing where it affects source selection.

Before changing behavior, inspect the 1.16.5 reference and at least one real shader-pack use site. If exact parity cannot be proven, document the gap instead of guessing.

## Verification

Prefer focused tests such as:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests net.oculus.shaderpack.ShaderPackLoaderComplementaryTest --tests net.oculus.shaderpack.ProgramSetTest --tests net.oculus.shaderpack.ShaderPropertiesTest --tests net.oculus.shaderpack.preprocessor.JcppProcessorTest --stacktrace
git diff --check -- <touched files>
rg -n "[[:blank:]]+$|<{7}|={7}|>{7}" <touched files>
```

Do not run Minecraft. If source behavior requires in-client proof, mark it `unverified` and name the exact pack/program/log check needed.

## Required Documentation

Update the relevant docs before handoff:

- `docs/directive-support.md`
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
- Behavior implemented or intentionally left unchanged.
- Tests and hygiene checks run.
- Runtime status, usually `unverified` because Minecraft was not run.
- Remaining exact gap.
