/goal Agent 05 Shadows: complete one source-backed, production-grade shadow pipeline parity slice for the Oculus Forge 1.12.2 backport, with tests and docs, without running Minecraft.

You are Agent 05, the shadow pipeline worker.

Start in:
`/home/daniel/Downloads/MIGRACAO_ARCH/dev/games/Oculus/oculus-1.12.2`

Read first:

1. `AGENTS.md`
2. `docs/parallel-agents/README.md`
3. `docs/parallel-agents/agent05-shadow-pipeline/PROMPT.md`
4. `docs/agent-quickstart.md`
5. `docs/architecture.md`
6. `docs/parity-roadmap.md`
7. `docs/render-target-lifecycle.md`
8. `docs/source-flow-guide.md`
9. `docs/subsystem-index.md`
10. `docs/evidence-log.md`

Primary reference:
`../Oculus-1.16.5`

Real shader-pack evidence:

- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/shaders.properties`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/shadow.vsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/shadow.fsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/shadowcomp.csh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/program/shadow.glsl`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/program/shadowcomp.glsl`

Connected boundaries:

- Coordinate conflicts through Agent 01.
- Hand parser/source directive gaps to Agent 02.
- Hand generic uniform/sampler/image gaps to Agent 03 unless they require shadow renderer changes.
- Hand generic postprocess/render-target gaps to Agent 04 unless they are shadow-map-specific.
- Hand terrain/Relictium shadow terrain gaps to Agent 06.
- Hand texture/PBR resource gaps to Agent 07.
- Hand config/reload/UI gaps to Agent 08.
- Hand doc-only cleanup or stale navigation to Agent 09.

Hard limits:

- Do not run Minecraft, `runClient`, `xvfb-run ... runClient`, or GUI automation.
- Do not edit outside shadow maps, shadow renderer, shadow uniforms, shadow samplers, shadow directives, or shadow mixin entry points unless Agent 01 explicitly assigns that boundary.
- Do not guess GL behavior, camera math, mappings, hooks, or method names.
- Do not claim full-port completion.

Your mission:

Pick or continue one bounded issue in shadow framebuffer/depth setup, shadow clear/copy/mipmap behavior, shadow culling cameras, shadow terrain entry, shadow program/compute dispatch, shadow uniforms, or shadow sampler bindings. Inspect 1.16.5 reference files and Complementary shadow files before editing.

Required output:

- Active files changed.
- 1.16.5 reference files inspected.
- Shader-pack shadow files inspected.
- Shadow behavior affected.
- Focused tests and doc hygiene checks run.
- Docs updated.
- Runtime status, normally `unverified` because Minecraft was not run.
- Remaining exact gap and which agent owns it.
