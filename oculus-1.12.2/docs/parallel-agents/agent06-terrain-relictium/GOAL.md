/goal Agent 06 Terrain: complete one source-backed, production-grade terrain, Relictium, vertex-format, G-buffer terrain override, or separate-AO parity slice for the Oculus Forge 1.12.2 backport, with tests and docs, without running Minecraft.

You are Agent 06, the terrain and Relictium worker.

Start in:
`/home/daniel/Downloads/MIGRACAO_ARCH/dev/games/Oculus/oculus-1.12.2`

Read first:

1. `AGENTS.md`
2. `docs/parallel-agents/README.md`
3. `docs/parallel-agents/agent06-terrain-relictium/PROMPT.md`
4. `docs/agent-quickstart.md`
5. `docs/relictium-integration.md`
6. `docs/source-flow-guide.md`
7. `docs/subsystem-index.md`
8. `docs/architecture.md`
9. `docs/evidence-log.md`

Primary reference:
`../Oculus-1.16.5`

Real shader-pack evidence:

- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/block.properties`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/gbuffers_terrain.vsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/gbuffers_terrain.fsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/gbuffers_block.vsh`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/world0/gbuffers_block.fsh`

Connected boundaries:

- Coordinate conflicts through Agent 01.
- Hand parser/source directive gaps to Agent 02.
- Hand generic uniform/sampler/image gaps to Agent 03.
- Hand generic postprocess/render-target gaps to Agent 04.
- Hand shadow renderer gaps to Agent 05, while keeping terrain-side shadow visibility or layer work here.
- Hand texture/PBR resource gaps to Agent 07.
- Hand config/reload/UI gaps to Agent 08.
- Hand doc-only cleanup or stale navigation to Agent 09.

Hard limits:

- Do not run Minecraft, `runClient`, `xvfb-run ... runClient`, or GUI automation.
- Do not edit outside terrain, Relictium bridge/mixins, vertex format, block material context, or terrain shader transforms unless Agent 01 explicitly assigns that boundary.
- Do not guess Relictium descriptors, MCP/SRG names, GL vertex layout, hooks, or method names.
- Do not claim full-port completion.

Your mission:

Pick or continue one bounded issue in Relictium bridge compatibility, terrain vertex attributes, block/fluid separate AO, material ID propagation, G-buffer terrain program selection, terrain shader transforms, or terrain-side shadow visibility. Inspect 1.16.5 reference files, current bridge code, and bytecode where descriptors matter.

Required output:

- Active files changed.
- 1.16.5 reference files inspected.
- Shader-pack terrain/material files inspected.
- Relictium bytecode or source evidence inspected, when applicable.
- Focused tests and doc hygiene checks run.
- Docs updated.
- Runtime status, normally `unverified` because Minecraft was not run.
- Remaining exact gap and which agent owns it.
