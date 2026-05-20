# Documentation Standard

Last updated: 2026-05-16.

This file defines how to document the Forge 1.12.2 Oculus backport so another AI agent can resume work without relying on chat history. Treat documentation as part of the implementation: if a behavioral slice is not documented, it is not ready to hand off.

## Documentation Goals

The docs should let a cold-start agent answer these questions quickly:

- Where is the active 1.12.2 implementation?
- Which 1.16.5 source files define the reference behavior?
- Which shader-pack files or runtime paths exercise the feature?
- What is implemented, partial, parsed only, missing, or unverified?
- What commands or runtime checks prove the current claim?
- What exact gap remains, and what evidence is needed before changing it?

Chat context is disposable. Durable claims belong in `docs/`, `AGENTS.md`, tests, or source comments where they directly clarify non-obvious code.

## Required Slice Record

Every meaningful code or behavior slice must leave a durable record with:

- Active 1.12.2 files changed.
- 1.16.5 reference files inspected.
- Shader-pack files inspected when the behavior affects packs, directives, uniforms, samplers, images, programs, options, or resources.
- Behavior implemented or intentionally left unchanged.
- Tests, build commands, bytecode checks, log checks, or runtime checks run.
- Runtime validation status: explicitly say whether Minecraft client/world evidence exists.
- Remaining gap or exact blocker.

Use exact names for classes, directives, uniforms, pack files, methods, and commands. Avoid broad phrases such as "matches upstream" unless the relevant reference file was actually inspected and the evidence is recorded.

## Where To Put Information

Use this routing first:

| Information type | Destination |
| --- | --- |
| Cold-start navigation, paths, commands, traps | `AGENTS.md`, `docs/agent-quickstart.md`, `docs/ai-agent-context.md` |
| Current resume point and recent evidence | `docs/agent-handoff.md` |
| High-level implementation status | `docs/backport-status.md` |
| Remaining full-port blockers | `docs/parity-roadmap.md` |
| Source-backed findings, bytecode checks, shader-pack observations, deferred decisions | `docs/evidence-log.md` |
| Package ownership and entry points | `docs/repo-map.md`, `docs/subsystem-index.md` |
| Data/control flow through the system | `docs/source-flow-guide.md`, `docs/architecture.md` |
| `shaders.properties`, const directives, feature flags | `docs/directive-support.md` |
| Uniforms, custom expressions, samplers, images | `docs/uniform-gap-analysis.md`, `docs/custom-uniform-smooth-semantics.md` |
| Render targets, depth textures, framebuffers, buffer flips, final/composite passes | `docs/render-target-lifecycle.md` |
| Relictium/Sodium-like terrain integration | `docs/relictium-integration.md` |
| Compatibility shims, copied remnants, inactive mixin configs | `docs/legacy-shims-and-remnants.md` |
| Build, test, runtime, refmap, and log workflows | `docs/verification.md` |

If a slice touches multiple areas, update every doc a future agent would reasonably read before editing that area.

## Evidence Quality

Use conservative status words consistently:

- Implemented: parsed, wired, tested where possible, and no known missing code path.
- Partial: useful behavior exists, but important reference behavior is absent or not wired.
- Parsed only: metadata is read but does not affect runtime behavior.
- Unverified: code exists, but in-client behavior has not been proven.
- Missing: no current support found.

A green Gradle build is Java evidence. It is not shader visual parity, GL state parity, framebuffer correctness, mixin runtime proof, or real shader-pack compatibility. Rendering behavior remains unverified until a Minecraft 1.12.2 client run enters a world with a real pack and the affected logs or visuals are checked.

## Handoff Template

Use this shape in `docs/agent-handoff.md` or a focused evidence-log entry:

```text
Slice:
Active files:
1.16.5 references:
Shader-pack/runtime evidence:
Implemented behavior:
Verification:
Runtime status:
Remaining gap:
Next exact check:
```

Keep entries factual and searchable. Prefer class names, directive names, test names, and pack paths over prose summaries that cannot be grepped later.

## Comment Policy

Use source comments sparingly. Add a comment only when it prevents a future agent from "simplifying" behavior that is intentionally source-backed, version-specific, or tied to 1.12.2 engine limitations. Put broader rationale in docs, not in long comments beside ordinary code.

## Before Handing Off

For documentation-only changes, run:

```bash
git diff --check -- <touched files>
rg -n "[[:blank:]]+$|<{7}|={7}|>{7}" <touched files>
```

For code changes, also run the focused test for the slice and usually the full Gradle test suite. For rendering slices, record whether runtime validation was performed; if not, mark the feature unverified instead of implying completion.
