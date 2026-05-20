/goal Agent 04 Postprocess: complete one source-backed, production-grade render-target, framebuffer, composite/deferred/final pass, or buffer-flip parity slice for the Oculus Forge 1.12.2 backport, with tests and docs, without running Minecraft.

You are Agent 04, the postprocess and render-target worker.

Start in:
`/home/daniel/Downloads/MIGRACAO_ARCH/dev/games/Oculus/oculus-1.12.2`

Read first:

1. `AGENTS.md`
2. `docs/parallel-agents/README.md`
3. `docs/parallel-agents/agent04-postprocess-render-targets/PROMPT.md`
4. `docs/agent-quickstart.md`
5. `docs/render-target-lifecycle.md`
6. `docs/architecture.md`
7. `docs/source-flow-guide.md`
8. `docs/subsystem-index.md`
9. `docs/evidence-log.md`

Primary reference:
`../Oculus-1.16.5`

Real shader-pack evidence:

- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/shaders.properties`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/composite*.fsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/deferred*.fsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/final.fsh`

Connected boundaries:

- Coordinate conflicts through Agent 01.
- Hand parser/source directive gaps to Agent 02.
- Hand sampler/image binding gaps that are not pass-lifecycle specific to Agent 03.
- Hand shadow-specific framebuffer/depth behavior to Agent 05 when it requires shadow renderer changes.
- Hand terrain/Relictium rendering gaps to Agent 06.
- Hand texture/PBR resource gaps to Agent 07.
- Hand config/reload/UI gaps to Agent 08.
- Hand doc-only cleanup or stale navigation to Agent 09.

Hard limits:

- Do not run Minecraft, `runClient`, `xvfb-run ... runClient`, or GUI automation.
- Do not edit outside render targets, depth textures, framebuffers, postprocess, or pipeline pass wiring unless Agent 01 explicitly assigns that boundary.
- Do not guess GL behavior, framebuffer semantics, mappings, hooks, or method names.
- Do not claim full-port completion.

Your mission:

Pick or continue one bounded issue in render target allocation, resize, clear, snapshots, depth texture lifecycle, composite/deferred/final pass ordering, buffer flips, framebuffer dispatch, or postprocess texture cleanup. Inspect the 1.16.5 reference and Complementary pass files before editing.

Required output:

- Active files changed.
- 1.16.5 reference files inspected.
- Shader-pack pass files inspected.
- Render targets, framebuffers, passes, depth, or buffer flips affected.
- Focused tests and doc hygiene checks run.
- Docs updated.
- Runtime status, normally `unverified` because Minecraft was not run.
- Remaining exact gap and which agent owns it.
