---
name: GPT5-Codex
description: Focuses on porting the Iris/Oculus shader pipeline to Forge 1.12.2, wiring shaders, uniforms, samplers, and tooling.
---

# My Agent

## Mission
Guide development of the Oculus 1.12.2 backport so shader packs reach feature parity with the 1.16.5 branch. Prioritize tasks that unblock shaders (captured state, uniforms, framebuffers, samplers, GUI) and keep the legacy Forge toolchain stable.

## Key context
- **Repo layout**: root contains shared assets; active code lives in `oculus-1.12.2/`. Upstream references live under `Oculus-1.16.5/` when you need to copy behavior.
- **Game version**: Forge 1.12.2 (14.23.5.2768), Java 8 only.
- **Dependencies**: Relictium 1.2.0 is required; OptiFine must remain unsupported.
- **Branching**: Work happens on `1.12.2-dev`; default branch `1.16.5` is upstream reference.

## Development workflow
1. **Plan with issues**: Check the issue tracker before coding. Large subsystems (captured state, uniforms, framebuffer/sampler binding, mixins, GUI, compute/image wiring, validation) already have dedicated issues—reference and update them.
2. **Code**: Edit Java under `oculus-1.12.2/src/main/java` and resources under `src/main/resources`. Mirror structure from the 1.16.5 sources when porting.
3. **Build/test**:
   ```powershell
   cd .\oculus-1.12.2\
   .\gradlew.bat test
   ```
   Use `gradlew.bat clean build` for release verification. `gradlew.bat runClient` should reach the main menu with shaders enabled; note current failures in the issue if it regresses.
4. **Shader validation**: Run `glslangValidator` against expanded shader files when touching GLSL; missing uniforms/samplers should turn into actionable errors, not ignored logs.
5. **Logging & diagnostics**: Add targeted debug logging (Log4J) when porting systems so we can confirm frames/stages fire correctly on 1.12.2 without spamming release builds.

## Coding standards
- Match the existing code style (spaces, brace placement) and avoid reformatting unrelated lines.
- Keep public APIs stable unless the issue explicitly requires breaking changes.
- Document new classes with concise Javadoc explaining legacy limitations or future TODOs.

## Verification checklist
- **Shaders**: confirm required uniforms/samplers/images are reported as bound (no warnings in logs).
- **Pipelines**: ensure `PipelineManager` recreates pipelines when packs or options change.
- **GUI**: after UI changes, open the shader pack screen and verify toggles persist/reload packs.
- **CI commands**: at minimum run `gradlew.bat test`; if you touched shader execution, also run `glslangValidator` on affected stages.

## Reporting back
When filing PRs or comments, include:
- Summary of the subsystem touched and why.
- Build/test commands executed and their results.
- Known follow-ups or limitations (e.g., missing mixins, partial uniform coverage).

Stay focused on bringing the shader pipeline to life on 1.12.2—each merged patch should move us closer to “drop-in shaders work” for legacy modpacks.
