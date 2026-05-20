# Parallel Agent Prompt Index

Last updated: 2026-05-17.

This directory contains ready-to-paste prompts for splitting the Oculus Forge 1.12.2 backport across multiple AI agents. Each numbered folder has:

- `GOAL.md`: first-message prompt that starts with Codex `/goal` and links the worker into the shared plan.
- `PROMPT.md`: detailed role contract with ownership boundaries, reference paths, allowed verification, and required documentation output.

The recommended fast pool is eight active agents:

| Folder | Goal prompt | Detailed prompt | Main ownership |
| --- | --- | --- | --- |
| `agent01-coordinator/` | `GOAL.md` | `PROMPT.md` | Work slicing, conflict control, status docs, final integration review |
| `agent02-shader-source/` | `GOAL.md` | `PROMPT.md` | Pack discovery, include/preprocessor flow, program discovery, directives, source transforms |
| `agent03-uniforms-and-expressions/` | `GOAL.md` | `PROMPT.md` | Built-in uniforms, compatibility uniforms, custom expressions, smoothing, samplers/images binding audits |
| `agent04-postprocess-render-targets/` | `GOAL.md` | `PROMPT.md` | Render target lifecycle, composite/final passes, framebuffer ownership, buffer flips |
| `agent05-shadow-pipeline/` | `GOAL.md` | `PROMPT.md` | Shadow maps, shadow programs, culling cameras, depth/copy behavior, shadow uniforms |
| `agent06-terrain-relictium/` | `GOAL.md` | `PROMPT.md` | Terrain vertex formats, Relictium bridge/mixins, G-buffer terrain overrides, separate AO |
| `agent07-resources-textures-pbr/` | `GOAL.md` | `PROMPT.md` | PBR loaders, texture formats, custom textures/images, resource reload lifetime |
| `agent08-config-gui-reload/` | `GOAL.md` | `PROMPT.md` | Config persistence, shader-pack GUI, options/profiles, language lookup, reload behavior |

`agent09-documentation-triage/` is optional and useful when many implementation workers are active. It should not own feature code. Its job is to keep docs, status words, handoffs, evidence links, and navigation current while other agents implement.

## How To Use

1. Start each external agent in the parent workspace or in `oculus-1.12.2/`.
2. Give each agent exactly one folder's `GOAL.md` as the first message.
3. The `GOAL.md` instructs the worker to read the same folder's `PROMPT.md`; do not skip that detailed prompt.
4. Keep one agent as `agent01-coordinator` so worker changes do not overlap silently.
5. Tell every worker which files or subsystem it owns for that session.
6. Require each worker to report changed files, tests run, docs updated, remaining risk, and any cross-agent conflict before ending.

Do not ask these agents to run Minecraft. The worker prompts explicitly forbid `runClient`, GUI automation, and in-client runtime validation. They may run compile, unit, source, bytecode, and documentation hygiene checks only. Runtime validation remains a separate manual or coordinator-approved phase.

## Shared Rules

- The active project is `oculus-1.12.2/`.
- The primary behavior reference is `../Oculus-1.16.5/`.
- The main real shader-pack target is `run/shaderpacks/ComplementaryReimagined_r5.6.1/` plus `run/shaderpacks/ComplementaryReimagined_r5.6.1.zip`.
- The secondary expression/loader target is `run/shaderpacks/MakeUp-UltraFast-9.3e/` plus `run/shaderpacks/MakeUp-UltraFast-9.3e.zip`.
- Do not guess method names, class names, Forge hooks, MCP names, SRG descriptors, OpenGL behavior, or resource semantics. Inspect source, bytecode, mappings, shader-pack files, or documented evidence first.
- Do not claim full-port completion from a green Gradle build.
- Do not revert unrelated dirty worktree changes.
- Do not perform broad cleanup, broad formatting, or package-wide rewrites unless the coordinator explicitly gives that ownership.
- Every meaningful implementation slice must update the owning docs under `docs/` following `docs/documentation-standard.md`.

## Verification Boundary

Allowed for workers:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --tests 'net.oculus.<focused test class>' --stacktrace
git diff --check -- <touched files>
rg -n "[[:blank:]]+$|<{7}|={7}|>{7}" <touched files>
```

Forbidden for workers unless the user changes the rule:

```bash
runClient
xvfb-run ... runClient
Minecraft GUI automation
manual in-client validation
```

## Coordination Notes

- Prefer one agent per ownership area. Do not put two agents on the same package unless one is read-only.
- If a worker discovers it must change another worker's files, it should stop that change, document the dependency, and report it to the coordinator.
- If tests expose a runtime gap that cannot be proven without Minecraft, record it as `unverified` and hand it to the coordinator instead of claiming parity.
- If docs and source disagree, source wins until a source-backed doc correction is made.

## Coordinator Overlap Notes

These files and responsibilities are cross-cutting. Agents should treat them as coordinator-owned boundaries unless their current prompt or an explicit coordinator assignment includes the change:

- `ShaderWorldRenderingPipeline` is shared by postprocess/render-target work, shadow timing, resource/PBR binding scope, and config fallback behavior.
- `LevelRendererMixin` and Relictium shadow mixins sit between shadow and terrain ownership.
- `CustomTextureManager` and `CustomImageManager` sit between sampler/image binding and resource/PBR ownership.
- `ProgramBuilder`, `ProgramSamplers`, and `ProgramImages` are shared by uniforms, shader compilation, postprocess binding, shadow samplers, and resource samplers.
- Status docs such as `docs/backport-status.md`, `docs/parity-roadmap.md`, `docs/evidence-log.md`, and `docs/agent-handoff.md` may be edited by implementation workers only for their own slice; broader reconciliation belongs to Agent 01 or Agent 09.

When a worker needs one of these shared surfaces, the handoff must name the dependency, the other affected agent area, and the exact runtime or source evidence needed before the boundary is widened.
