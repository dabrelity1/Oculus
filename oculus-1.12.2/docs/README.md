# Documentation Index

Last updated: 2026-05-17.

This directory is the navigation layer for agents and contributors working on the Forge 1.12.2 Oculus backport. Source code remains authoritative; these files explain where to look, what is implemented, what is still risky, and what evidence is needed before claiming parity.

## Read Order For New Agents

1. `../AGENTS.md`: operating rules, reference paths, build commands, and documentation duties.
2. `agent-quickstart.md`: first-page checklist for cold-start agents, with active paths, search recipes, subsystem starting points, commands, and docs to update.
3. `ai-agent-context.md`: compact context pack for future agents, including local layout, source-backed facts, commands, and current traps.
4. `parallel-agents/README.md`: ready-to-paste prompts for splitting work across scoped parallel agents without overlapping ownership.
5. `agent-handoff.md`: current resume point, recent verified commands, implemented slices, and next high-value checks.
6. `documentation-standard.md`: durable documentation contract for every implementation slice.
7. `repo-map.md`: high-level source map by package and subsystem.
8. `source-flow-guide.md`: flow-oriented guide for tracing pack reloads, shader compilation, render passes, shadows, uniforms, terrain, and resource lifetime.
9. `subsystem-index.md`: table mapping each subsystem to active 1.12.2 files, 1.16.5 reference areas, tests, and docs.
10. `backport-status.md`: current implementation status and known incomplete or unverified areas.
11. `parity-roadmap.md`: strict completion bar for a real full port.
12. `evidence-log.md`: source-backed findings, bytecode checks, shader-pack evidence, and exact blockers where guessing would be unsafe.
13. `verification.md`: build, test, refmap, and runtime validation workflow.
14. `render-target-lifecycle.md`: render-target, depth-texture, framebuffer, buffer-flip, and postprocess ownership notes.
15. `custom-uniform-smooth-semantics.md`: exact evidence and risk notes for `smooth([id], ...)` state behavior.
16. `legacy-shims-and-remnants.md`: compatibility shim and inactive-remnant warning map.
17. `runtime-diagnosis-2026-05-17.md`: manual `latest.log` diagnosis for the MakeUp compile fallback and Complementary long compile/freeze report.

## Task Lookup

| Task | Read first |
| --- | --- |
| Resume work after another agent | `agent-handoff.md`, then `repo-map.md` |
| Regain context quickly from a cold start | `ai-agent-context.md`, then `agent-handoff.md` |
| Start safely in the repo with minimal reading | `agent-quickstart.md`, then `ai-agent-context.md` |
| Launch scoped parallel agents | `parallel-agents/README.md`, then the chosen `parallel-agents/agent*/PROMPT.md` |
| Understand what must be documented for each slice | `documentation-standard.md`, then `agent-playbook.md` |
| Find subsystem ownership | `repo-map.md`, `subsystem-index.md` |
| Trace a feature through data flow and runtime flow | `source-flow-guide.md`, then `architecture.md` |
| Decide if a feature is done | `backport-status.md`, `parity-roadmap.md`, `verification.md` |
| Check the proof behind a recent claim | `evidence-log.md`, then the referenced source/bytecode/shader-pack files |
| Diagnose the latest manual client log | `runtime-diagnosis-2026-05-17.md`, then `run/logs/latest.log` |
| Change runtime control flow | `architecture.md`, `repo-map.md`, matching 1.16.5 reference classes |
| Add or audit `shaders.properties` / const directives | `directive-support.md`, `subsystem-index.md` |
| Add or audit uniforms, samplers, images, or custom expressions | `uniform-gap-analysis.md`, `custom-uniform-smooth-semantics.md`, `architecture.md` |
| Change render targets or post passes | `render-target-lifecycle.md`, `architecture.md`, `agent-handoff.md` |
| Change shadows | `architecture.md`, `parity-roadmap.md`, `agent-handoff.md` |
| Change terrain, vertex formats, or Relictium hooks | `relictium-integration.md`, `subsystem-index.md` |
| Add or change mixins | `verification.md`, `repo-map.md`, target bytecode or MCP mappings |
| Judge whether an old copied class/config is active | `legacy-shims-and-remnants.md`, then `rg` call sites and active mixin configs |
| Prepare a handoff | `agent-playbook.md`, `agent-handoff.md` |

## Documents

- `agent-handoff.md`: compact current-state handoff. Update it whenever a long-running slice changes the next best action or verification evidence.
- `agent-quickstart.md`: first-page checklist for cold-start agents. Update it when active paths, search recipes, subsystem entry points, build commands, or documentation ownership rules change.
- `ai-agent-context.md`: compact agent context pack. Update it when the high-level source-backed facts, workspace traps, or next best work change.
- `agent-playbook.md`: working procedure for agents. Update it when workflow, safety rules, or handoff expectations change.
- `parallel-agents/README.md` and `parallel-agents/agent*/PROMPT.md`: scoped prompts for multiple simultaneous agents. Update these when role boundaries, shared rules, or verification boundaries change.
- `architecture.md`: runtime architecture and control-flow map. Update it when ownership or frame/pass flow changes.
- `backport-status.md`: status of implemented, partial, missing, and unverified areas. Update it for meaningful feature changes.
- `documentation-standard.md`: documentation contract and handoff template. Update it when the required evidence trail or doc routing changes.
- `source-flow-guide.md`: flow-oriented code reading guide. Update it when selection/reload, metadata, source preparation, render-loop, postprocess, shadow, uniform, terrain, or resource-lifetime flow changes.
- `directive-support.md`: shader-pack property and directive support table. Update it whenever parsing or runtime directive behavior changes.
- `evidence-log.md`: source-backed findings and explicit proof gaps. Update it when an audit result should survive chat compaction or when a change is intentionally deferred because exact source data is missing.
- `custom-uniform-smooth-semantics.md`: focused note on custom expression `smooth([id], ...)` evidence, current behavior, and duplicate-ID risk. Update it before changing smooth state ownership.
- `legacy-shims-and-remnants.md`: compatibility shim and inactive-remnant map. Update it when modern package shims, copied package remnants, or active mixin config assumptions change.
- `parity-roadmap.md`: remaining work required before the port can be called production-grade. Update it when a blocker is closed or discovered.
- `relictium-integration.md`: Relictium/Sodium-like terrain integration notes. Update it when bridge code, terrain mixins, or bytecode assumptions change.
- `render-target-lifecycle.md`: render-target, depth-texture, framebuffer ownership, buffer-flip, and postprocess lifecycle notes. Update it when framebuffer ownership, resize behavior, sampler binding policy, or final/composite pass ordering changes.
- `repo-map.md`: broad source tree map. Update it when packages, entry points, resources, or tests move.
- `subsystem-index.md`: subsystem-to-source/reference/test map. Update it when a subsystem gains new owners, tests, or documentation requirements.
- `uniform-gap-analysis.md`: uniform, sampler, image, and expression audit guide. Update it when closing or discovering binding gaps.
- `verification.md`: compile, test, refmap, and runtime verification workflow. Update it when commands, runtime checks, or evidence requirements change.
- `runtime-diagnosis-2026-05-17.md`: focused note for the latest manual `runClient` log, including MakeUp fallback, Complementary long compile, current blockers, and rough completion percentages.

## Status Words

Use the same terms across all docs:

- Implemented: parsed, wired, tested where possible, and no known missing code path.
- Partial: useful behavior exists, but important reference behavior is absent or not wired.
- Parsed only: metadata is read but does not affect runtime behavior.
- Unverified: code exists, but in-client behavior has not been proven.
- Missing: no current support found.

Do not write that the full port is complete until `parity-roadmap.md`'s completion bar is actually met with Minecraft 1.12.2 runtime evidence.

## Documentation Rule

Every meaningful code slice should update at least one doc in this directory and follow `documentation-standard.md`. If the slice affects rendering behavior, also record whether it has runtime evidence or only compile/test evidence. If the slice involved a difficult audit or a "do not change yet" decision, update `evidence-log.md`. If docs and source disagree, inspect the source, fix the behavior or the doc, and keep the claim conservative.
