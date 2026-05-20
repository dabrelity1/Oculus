# Workspace Agent Guide

This workspace contains the active Forge 1.12.2 Oculus backport under `oculus-1.12.2/`.

If you are an AI agent starting from this parent directory, begin here:

1. `oculus-1.12.2/AGENTS.md`
2. `oculus-1.12.2/docs/agent-quickstart.md`
3. `oculus-1.12.2/docs/ai-agent-context.md`
4. `oculus-1.12.2/docs/README.md`
5. `oculus-1.12.2/docs/parallel-agents/README.md` when launching multiple scoped agents
6. `oculus-1.12.2/docs/agent-handoff.md`
7. `oculus-1.12.2/docs/documentation-standard.md`
8. `oculus-1.12.2/docs/repo-map.md`
9. `oculus-1.12.2/docs/subsystem-index.md`
10. `oculus-1.12.2/docs/backport-status.md`
11. `oculus-1.12.2/docs/parity-roadmap.md`
12. `oculus-1.12.2/docs/evidence-log.md`
13. `oculus-1.12.2/docs/render-target-lifecycle.md` when touching render targets, depth textures, framebuffers, buffer flips, or postprocess passes

The active project is not complete. Do not claim full-port parity from a green Gradle build. Full completion requires in-client Minecraft 1.12.2 runtime validation with real shader packs.

Important local references:

- `Oculus-1.16.5/`: primary Oculus/Iris behavior reference.
- `oculus-1.12.2/run/shaderpacks/ComplementaryReimagined_r5.6.1`: current real shader-pack target.
- `oculus-1.12.2/`: active Forge 1.12.2 port.

The worktree is intentionally dirty. Do not run broad reset, checkout, cleanup, or formatting commands unless the user explicitly asks for that exact operation.

Every meaningful implementation slice must leave durable documentation in `oculus-1.12.2/docs/`; follow `oculus-1.12.2/docs/documentation-standard.md` before handing work to another agent.
