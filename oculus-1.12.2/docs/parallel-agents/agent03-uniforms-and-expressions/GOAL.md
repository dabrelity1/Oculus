/goal Agent 03 Uniforms: complete one source-backed, production-grade uniforms, samplers, images, or custom expression parity slice for the Oculus Forge 1.12.2 backport, with tests and docs, without running Minecraft.

You are Agent 03, the uniforms, samplers, images, and expressions worker.

Start in:
`/home/daniel/Downloads/MIGRACAO_ARCH/dev/games/Oculus/oculus-1.12.2`

Read first:

1. `AGENTS.md`
2. `docs/parallel-agents/README.md`
3. `docs/parallel-agents/agent03-uniforms-and-expressions/PROMPT.md`
4. `docs/agent-quickstart.md`
5. `docs/uniform-gap-analysis.md`
6. `docs/custom-uniform-smooth-semantics.md`
7. `docs/source-flow-guide.md`
8. `docs/subsystem-index.md`
9. `docs/evidence-log.md`

Primary reference:
`../Oculus-1.16.5`

Real shader-pack evidence:

- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/lib/uniforms.glsl`
- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/shaders.properties`
- `run/shaderpacks/MakeUp-UltraFast-9.3e/shaders`

Connected boundaries:

- Coordinate conflicts through Agent 01.
- Hand parser/source discovery gaps to Agent 02.
- Hand postprocess pass binding/lifecycle gaps to Agent 04.
- Hand shadow-specific uniforms or shadow samplers to Agent 05 when they require shadow renderer changes.
- Hand terrain-specific vertex/material context gaps to Agent 06.
- Hand resource/PBR texture loading gaps to Agent 07.
- Hand config/options/reload gaps to Agent 08.
- Hand doc-only cleanup or stale navigation to Agent 09.

Hard limits:

- Do not run Minecraft, `runClient`, `xvfb-run ... runClient`, or GUI automation.
- Do not edit outside uniforms, expressions, samplers, images, or GL state notifiers unless Agent 01 explicitly assigns that boundary.
- Do not guess runtime values, mappings, hooks, GL behavior, or method names.
- Do not claim full-port completion.

Your mission:

Pick or continue one bounded issue in built-in uniforms, compatibility uniforms, custom `uniform.*` or `variable.*` expressions, smoothing, sampler/image limits, binding order, fallback textures, or active-program uniform refresh. Inspect 1.16.5 reference files and real shader-pack use sites before editing.

Required output:

- Active files changed.
- 1.16.5 reference files inspected.
- Shader-pack files inspected.
- Uniforms, expressions, samplers, or images affected.
- Focused tests and doc hygiene checks run.
- Docs updated.
- Runtime status, normally `unverified` because Minecraft was not run.
- Remaining exact gap and which agent owns it.
