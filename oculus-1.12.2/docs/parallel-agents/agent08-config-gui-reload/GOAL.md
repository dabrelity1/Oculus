/goal Agent 08 Config GUI: complete one source-backed, production-grade config, GUI, shader-pack selection, option/profile, language, or reload parity slice for the Oculus Forge 1.12.2 backport, with tests and docs, without running Minecraft.

You are Agent 08, the config, GUI, reload, options, and language worker.

Start in:
`/home/daniel/Downloads/MIGRACAO_ARCH/dev/games/Oculus/oculus-1.12.2`

Read first:

1. `AGENTS.md`
2. `docs/parallel-agents/README.md`
3. `docs/parallel-agents/agent08-config-gui-reload/PROMPT.md`
4. `docs/agent-quickstart.md`
5. `docs/backport-status.md`
6. `docs/source-flow-guide.md`
7. `docs/subsystem-index.md`
8. `docs/evidence-log.md`

Primary reference:
`../Oculus-1.16.5`

Real shader-pack evidence:

- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/shaders.properties`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/lang/en_US.lang`
- `run/shaderpacks/MakeUp-UltraFast-9.3e/shaders`

Connected boundaries:

- Coordinate conflicts through Agent 01.
- Hand parser/source directive gaps to Agent 02.
- Hand uniform/sampler/image gaps to Agent 03.
- Hand postprocess/render-target gaps to Agent 04.
- Hand shadow renderer gaps to Agent 05.
- Hand terrain/Relictium gaps to Agent 06.
- Hand resource/PBR loading gaps to Agent 07.
- Hand doc-only cleanup or stale navigation to Agent 09.

Hard limits:

- Do not run Minecraft, `runClient`, `xvfb-run ... runClient`, or GUI automation.
- Do not edit outside config, GUI, shader-pack selection, options/profiles, language lookup, client reload, or pipeline reload state unless Agent 01 explicitly assigns that boundary.
- Do not guess GUI hooks, reload hooks, mappings, descriptors, or method names.
- Do not claim full-port completion.

Your mission:

Pick or continue one bounded issue in config load/save/defaults, shader-pack selection, disabled/internal pack behavior, option menu/profile parsing, persisted option values, language lookup, pipeline reload, or resource reload state transitions. Inspect 1.16.5 reference files and real shader-pack option/language files before editing.

Required output:

- Active files changed.
- 1.16.5 reference files inspected.
- Shader-pack option or language files inspected.
- Config, GUI, reload, option, or language behavior affected.
- Focused tests and doc hygiene checks run.
- Docs updated.
- Runtime status, normally `unverified` because Minecraft was not run.
- Remaining exact gap and which agent owns it.
