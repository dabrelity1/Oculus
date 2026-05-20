# Oculus Forge 1.12.2 Backport

This repository is an in-progress Forge 1.12.2 backport of Oculus/Iris shader rendering behavior. The goal is not a rewrite or a simplified compatibility layer: the target is faithful shader-pack behavior as close to the Forge 1.12.2 engine as the legacy renderer allows.

The port is not complete. It does compile and contains real shader-pack loading, program compilation, render-target, post-process, shadow, custom texture/image, custom uniform, and mixin integration work. Treat every subsystem as needing verification against the 1.16.5 reference and real shader packs before calling it production-grade.

## Start Here

- Agent navigation guide: [AGENTS.md](AGENTS.md)
- AI agent quickstart: [docs/agent-quickstart.md](docs/agent-quickstart.md)
- Documentation index: [docs/README.md](docs/README.md)
- AI agent context pack: [docs/ai-agent-context.md](docs/ai-agent-context.md)
- Current implementation status: [docs/backport-status.md](docs/backport-status.md)
- Current agent handoff: [docs/agent-handoff.md](docs/agent-handoff.md)
- Documentation standard: [docs/documentation-standard.md](docs/documentation-standard.md)
- Agent playbook: [docs/agent-playbook.md](docs/agent-playbook.md)
- Full-port parity roadmap: [docs/parity-roadmap.md](docs/parity-roadmap.md)
- Repository map for future agents: [docs/repo-map.md](docs/repo-map.md)
- Source flow guide: [docs/source-flow-guide.md](docs/source-flow-guide.md)
- Subsystem index: [docs/subsystem-index.md](docs/subsystem-index.md)
- Runtime architecture map: [docs/architecture.md](docs/architecture.md)
- Render target and postprocess lifecycle: [docs/render-target-lifecycle.md](docs/render-target-lifecycle.md)
- Custom uniform `smooth([id], ...)` semantics: [docs/custom-uniform-smooth-semantics.md](docs/custom-uniform-smooth-semantics.md)
- Legacy shims and copied remnants: [docs/legacy-shims-and-remnants.md](docs/legacy-shims-and-remnants.md)
- Shader directive support: [docs/directive-support.md](docs/directive-support.md)
- Uniform and sampler audit notes: [docs/uniform-gap-analysis.md](docs/uniform-gap-analysis.md)
- Relictium integration notes: [docs/relictium-integration.md](docs/relictium-integration.md)
- Verification workflow: [docs/verification.md](docs/verification.md)

## Local References

- Primary 1.16.5 source reference: `../Oculus-1.16.5`
- Current 1.12.2 port: this directory
- Complementary target shader pack: `run/shaderpacks/ComplementaryReimagined_r5.6.1`
- Packaged Complementary target shader pack: `run/shaderpacks/ComplementaryReimagined_r5.6.1.zip`
- Secondary shader-pack audit target: `run/shaderpacks/MakeUp-UltraFast-9.3e`
- Standalone `../Relictium` source directory is not present in this Linux layout; Relictium APIs/dependency integration still exist in this project and Gradle cache.

## Build

Use Java 8. The wrapper jar path is used because the shell wrapper may not be executable or may have local edits.

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava --stacktrace
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain build --stacktrace
```

`build` currently compiles and runs the JUnit suite under `src/test/java`. Treat that as Java regression coverage only; it does not prove in-client shader-pack rendering parity.

## Current Warning

The worktree is intentionally very dirty because this is an active port. Do not use broad cleanup, reset, or checkout commands. Review local diffs before touching shared files, and keep changes scoped to the feature being ported or documented.
