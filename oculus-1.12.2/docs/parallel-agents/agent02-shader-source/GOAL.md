/goal Agent 02 Shader Source: complete one source-backed, production-grade shader source or pack parser parity slice for the Oculus Forge 1.12.2 backport, with tests and docs, without running Minecraft.

You are Agent 02, the shader source and pack parser worker.

Start in:
`/home/daniel/Downloads/MIGRACAO_ARCH/dev/games/Oculus/oculus-1.12.2`

Read first:

1. `AGENTS.md`
2. `docs/parallel-agents/README.md`
3. `docs/parallel-agents/agent02-shader-source/PROMPT.md`
4. `docs/agent-quickstart.md`
5. `docs/source-flow-guide.md`
6. `docs/directive-support.md`
7. `docs/subsystem-index.md`
8. `docs/evidence-log.md`

Primary reference:
`../Oculus-1.16.5`

Real shader-pack evidence:

- `run/shaderpacks/ComplementaryReimagined_r5.6.1`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1.zip`
- `run/shaderpacks/MakeUp-UltraFast-9.3e`
- `run/shaderpacks/MakeUp-UltraFast-9.3e.zip`

Connected boundaries:

- Coordinate conflicts through Agent 01.
- Hand uniform/sampler/image binding gaps to Agent 03.
- Hand postprocess/render-target pass gaps to Agent 04.
- Hand shadow renderer gaps to Agent 05.
- Hand terrain/Relictium shader override gaps to Agent 06.
- Hand resource/PBR texture gaps to Agent 07.
- Hand GUI/options/reload gaps to Agent 08.
- Hand doc-only cleanup or stale navigation to Agent 09.

Hard limits:

- Do not run Minecraft, `runClient`, `xvfb-run ... runClient`, or GUI automation.
- Do not edit outside your shader source/parser ownership unless Agent 01 explicitly assigns that boundary.
- Do not guess shader semantics, mappings, hooks, GL behavior, or method names.
- Do not claim full-port completion.

Your mission:

Pick or continue one bounded issue in pack loading, include processing, preprocessing, `shaders.properties`, directive application, program discovery, compute source discovery, fallback behavior, or 1.12 GLSL source transforms. Inspect the 1.16.5 reference and real shader-pack files before editing.

Required output:

- Active files changed.
- 1.16.5 reference files inspected.
- Shader-pack files inspected.
- Behavior implemented or intentionally left unchanged.
- Focused tests and doc hygiene checks run.
- Docs updated.
- Runtime status, normally `unverified` because Minecraft was not run.
- Remaining exact gap and which agent owns it.
