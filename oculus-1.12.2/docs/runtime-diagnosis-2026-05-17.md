# Runtime Diagnosis 2026-05-17

Last updated: 2026-05-17.

This note records the manual `runClient` attempt where MakeUp UltraFast appeared to load as vanilla and Complementary Reimagined appeared to freeze. It is based on `run/logs/latest.log`, `run/logs/debug.log`, current source inspection, local shader-pack files, and a small external version check for MakeUp.

## Inputs

- Command used by the user:

```bash
JAVA_HOME=/home/daniel/.cache/codex/jdks/temurin8 \
PATH=/home/daniel/.cache/codex/jdks/temurin8/bin:$PATH \
./gradlew runClient --no-daemon --stacktrace
```

- Active log: `run/logs/latest.log`.
- Root `logs/latest.log` is older test output and is not the client log for this run.
- No new crash report was written. The newest crash report during this audit was still `run/crash-reports/crash-2026-05-17_11.22.41-client.txt`, before the 13:57 manual run.
- `run/config/oculus.properties` ended with `shadersEnabled=true` and `selectedPackName=ComplementaryReimagined_r5.6.1`.

## Summary

The run did not fail at Forge startup, Java, LWJGL display initialization, or mod loading. It entered a world and created a fixed-function Oculus pipeline at `run/logs/latest.log:381` through `run/logs/latest.log:382`.

MakeUp did not get ignored. It started shader pipeline creation, failed compiling `prepare.fsh`, and `PipelineManager` disabled shaders. That explains the vanilla-looking result in the recorded run. A later source-preparation slice added a narrow duplicate-identical function cleanup for this exact MakeUp 9.3e legacy-branch failure; that is source-backed by tests, but has not been proven in a live Minecraft GL compile.

Complementary did not hit a clear crash in the log. It initialized the shader pipeline and began creating postprocess, final, shadow, and many gbuffer program variants. The client was still compiling and source-patching shader variants on the main thread when the user interrupted it with Ctrl-C. That explains the apparent freeze. A later source-preparation slice removed the extra un-specialized base compile for availability-backed world/shadow sources, added compile progress/timing logs, downgraded avoidable compatibility cleanup spam to debug, and added failed-pack backoff keyed by pack plus option values; Complementary still needs a fresh client run to measure the real improvement.

## MakeUp Failure

Log evidence:

- MakeUp pipeline starts at `run/logs/latest.log:403` through `run/logs/latest.log:414`.
- `prepare.fsh` fails with `function shifted_r_dither redefined` at `run/logs/latest.log:417` through `run/logs/latest.log:420`.
- The pipeline disables shaders at `run/logs/latest.log:418` and is destroyed at `run/logs/latest.log:450`.
- The same failure repeats at `run/logs/latest.log:458` through `run/logs/latest.log:505`, so retries currently re-hit the same compile failure instead of staying backed off until the pack/options change.

Local pack evidence:

- `run/shaderpacks/MakeUp-UltraFast-9.3e/shaders/common/prepare_fragment.glsl` includes `/lib/dither.glsl`.
- `run/shaderpacks/MakeUp-UltraFast-9.3e/shaders/lib/dither.glsl:251` and `run/shaderpacks/MakeUp-UltraFast-9.3e/shaders/lib/dither.glsl:255` define identical `shifted_r_dither(vec2 frag)` functions in the `MC_VERSION < 11300` branch.
- The active 1.12 environment makes that branch reachable, so Mesa correctly rejects the duplicate definition.

External version check:

- Modrinth lists MakeUp UltraFast `9.3e` as supporting `1.12.2-1.21.10`: `https://modrinth.com/shader/makeup-ultra-fast-shaders/version/9.3e`.
- Modrinth and CurseForge list `9.3h` as supporting `1.12.2` and its changelog says compatibility with older Minecraft versions from 1.12 onward was fixed since `9.3e`: `https://modrinth.com/shader/makeup-ultra-fast-shaders/version/UH6jy81G` and `https://www.curseforge.com/minecraft/shaders/makeup-ultra-fast-shader/files/7303099`.

Diagnosis:

- This is a real GLSL compile failure, not a GUI/config failure.
- It may be a known MakeUp `9.3e` pack-side issue fixed in later MakeUp versions, but the backport still needs a decision: either update the test target pack, or source-back an OptiFine/Iris-compatible duplicate-function tolerance/cleanup if `9.3e` must remain supported.
- Do not hide this by swallowing `ProgramLoadException`; the current fail-fast compile result is the right failure mode. The missing piece is a source-backed compatibility decision and better one-shot failure/backoff behavior after the pack fails.

Follow-up source-backed status:

- `ShaderCompatibilityPatcher` now removes later top-level function definitions only when the canonical signature and canonical body match an already-seen definition. Same-signature different bodies are left intact so real GLSL redefinition errors remain visible.
- The cleanup tracks multiple bodies per signature so the MakeUp layout of one 1.13+ body followed by two identical 1.12 branch bodies removes only the second legacy body.
- `ShaderPackLoaderComplementaryTest.preparesMakeUpPrepareFragmentWithoutDuplicateShiftedRDitherInLegacyBranch` prepares the real local MakeUp 9.3e `prepare` source and verifies the active legacy branch has exactly one `float shifted_r_dither(vec2 frag)` definition while preserving the call in `prepare_fragment.glsl`.
- This makes the recorded MakeUp `prepare.fsh` blocker source-backed as fixed in the backport. It is still not live GL/runtime proof; Minecraft was not run for the follow-up slice.

## Complementary Freeze

Log evidence:

- Complementary selection starts at `run/logs/latest.log:515` through `run/logs/latest.log:523`.
- The pipeline starts preparing at `run/logs/latest.log:527`.
- It creates `99` postprocess passes at `run/logs/latest.log:528`, `run/logs/latest.log:531`, and `run/logs/latest.log:546`.
- The final pass compiles at `run/logs/latest.log:549`.
- The shadow renderer initializes at `run/logs/latest.log:550` through `run/logs/latest.log:551`.
- From `run/logs/latest.log:552` through `run/logs/latest.log:906`, the main thread is still repeatedly patching and compiling shadow/gbuffer program variants.
- The run ends only when the client shutdown thread starts at `run/logs/latest.log:907`; there is no Complementary `Failed to create shader rendering pipeline` error and no final `Shader programs compiled for ComplementaryReimagined_r5.6.1` line before shutdown.

Source evidence:

- `ShaderWorldRenderingPipeline.beginLevelRendering()` calls `ensureShadersCompiled()` synchronously from the render path at `src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java:423` through `src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java:435`.
- `ShaderLoader.initialize()` now compiles availability variants for gbuffer/root-shadow programs without also compiling an un-specialized base program for those same names. This was changed after the recorded run because the current 1.12 pipeline resolves those sources only by exact availability variant.
- `ShaderLoader.compileAvailabilityVariants()` still walks all `InputAvailability.NUM_VALUES` variants. Do not remove those variants just to shorten startup; the local 1.16.5 reference also caches availability-specialized programs.
- The local 1.16.5 reference also constructs an availability table for all variants through `ProgramTable`, but it caches by `ProgramId` plus `InputAvailability`. The current 1.12 loader now matches that closer by avoiding the extra base program for variant-backed sources and keeping cleanup chatter out of warn-level logs.

Observed shape:

- Many Complementary programs in the recorded pre-fix log emit exactly 18 unused-function warnings in `run/logs/latest.log`: 9 compilations times 2 raster stages. That lined up with the old one base compile plus eight input-availability variants per source. Current source has removed the extra base compile and moved avoidable cleanup logs to debug.
- `gbuffers_water` shows repeated Mesa compiler warnings at `run/logs/latest.log:892` through `run/logs/latest.log:904`, then continues patching at `run/logs/latest.log:905` through `run/logs/latest.log:906`.
- This looks like a long blocking compile/patch phase, not a Java deadlock or a startup crash.

Diagnosis:

- The user-facing freeze is a production gap. Shader compilation can monopolize the main thread long enough that the game appears hung.
- The compile count is not automatically wrong, because the 1.16.5 reference also uses availability-specialized programs. The suspect gaps are extra base compiles for availability-backed programs, slow regex/text transforms on very large prepared sources, warn-level log spam, lack of progress feedback, and no resumable/one-shot compile cache across failed/aborted attempts.
- A future agent should not remove availability variants just to make startup fast. It must compare exact 1.16.5 `DeferredWorldRenderingPipeline`, `ProgramTable`, `TransformPatcher`, and sampler/attribute behavior first.

Follow-up source-backed status:

- `ShaderLoader` now skips the un-specialized base compile for `gbuffers_*` and root shadow programs that are selected only through `InputAvailability` variants. `ShaderWorldRenderingPipeline` uses the variant-aware program-object count instead of treating an empty base map as "no programs".
- `ShaderLoader` now logs pack-level source-program count, per-source progress, final program-object count, and per-program timing. Normal timing is debug-level; variants over the slow threshold log one useful info line with total, source preparation, compile/link/bind, and source character count.
- `ShaderCompatibilityPatcher` now logs unused-function cleanup and duplicate-identical cleanup at debug level, avoiding the recorded warn-level flood while leaving actual compile/link failures at error/warn boundaries.
- `PipelineManager` now records a failed-pack backoff keyed by pack name plus sorted option values. The same failed pack/options do not retry every frame; pack, profile, or option changes produce a new key and allow a deliberate retry.
- This reduces known avoidable compile work and log spam. It does not make shader compilation asynchronous and does not prove Complementary startup time or visual parity; no Minecraft client run was performed for this slice.

## Non-Blocking Noise

These messages appeared in the run but do not explain the MakeUp fallback or Complementary freeze:

- `Apache Maven library folder was not in the format expected`.
- FML class-read errors for `mixin-0.8.5.jar` and `mixinbooter-8.0.jar`. They are dependency jars being scanned like mods after their tweakers/coremods are already queued.
- Log4j `LoggerNamePatternSelector` / `TerminalConsole` class-not-found console-appender errors in the terminal.
- Narrator native-library and Realms auth warnings.
- `Failed to parse block state predicate` warnings for BetterEnd block names absent from this 1.12 environment.

One logging issue is still worth fixing separately:

- Many log lines end with `inecraftFormatting`. This looks like a formatting/string-strip bug. It is not the shader blocker, but it makes runtime evidence harder to read.

## What Is Missing

These percentages are conservative engineering estimates from current code and runtime evidence, not completion claims.

| Area | Code present estimate | Production confidence | Main reason confidence is lower |
| --- | ---: | ---: | --- |
| Forge 1.12 startup, mod loading, config selection | 75-85% | 55-65% | This run reaches a world, but GUI apply/reload/dimension flows are not fully proven. |
| Shader-pack parsing and option metadata | 75-85% | 60-70% | Complementary and MakeUp parse enough to start, but target-pack edge cases still appear at compile time. |
| Shader source transform and GLSL compatibility | 60-75% | 35-50% | MakeUp fails `prepare.fsh`; Complementary source transforms are very slow and noisy under runtime load. |
| Program compile/link/runtime program selection | 65-80% | 40-55% | Availability variants exist, but compile happens synchronously, extra base compiles likely exist, and failed packs retry poorly. |
| Render targets/postprocess/final pass | 65-75% | 35-50% | Complementary reaches postprocess/final creation, but visual output and long-run stability are not proven. |
| Shadows | 60-70% | 25-40% | Shadow renderer initializes, but non-clear shadow depth and final shadow output remain unproven. |
| Relictium terrain integration | 65-75% | 30-45% | Prior logs prove override creation/selection, not rendered terrain attribute or translucent/shadow visual parity. |
| Uniforms, samplers, images, custom resources, PBR | 65-80% | 30-45% | Many paths are wired/tested, but live sampler/image/PBR/resource-reload proof is still thin. |
| Logging, diagnostics, failure UX | 35-50% | 20-35% | Current runtime behavior looks frozen and log spam obscures the real compile progress. |

Overall code-wise estimate: about 65-75% of the broad port surface has real code now.

Overall production-readiness estimate: about 40-50%.

Full-port parity estimate: about 30-40%, because full parity requires successful in-client operation, rendered-output proof, reload proof, long-session proof, and targeted validation across shader packs, dimensions, render phases, shadows, PBR, and resources.

## Next Exact Work

1. Re-run Complementary long enough to learn whether it reaches the final shader compile summary with the base-compile skip and timing logs in place; record elapsed time, slowest programs, and last active program/variant if interrupted.
2. Re-run MakeUp 9.3e in-client to confirm the prepared `shifted_r_dither` cleanup removes the recorded GL compile blocker without introducing another compile error.
3. If compile still feels frozen, decide whether to add async or staged compilation. Do not remove availability variants; the 1.16.5 reference uses availability-specialized programs.
4. Keep failed-pack backoff keyed to a deliberate retry boundary. If future UI/profile code changes the option model, ensure the backoff key includes the profile/options that actually affect preprocessed source.
5. Continue auditing remaining `TransformPatcher` differences with real source examples. Text-based transforms are now closer to 1.16.5 but not exhaustive AST parity.

Runtime status:

- Minecraft was not run by this audit. The diagnosis is from the user's manual run logs.
- No visual parity, screenshots, reload stability, long-session stability, or non-clear shadow-output proof is added by this note.
