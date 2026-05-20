# Custom Uniform Smooth Semantics

Last updated: 2026-05-16.

This note records the current evidence around OptiFine/Iris custom expression `smooth([id], ...)` semantics. It exists because changing this behavior from memory can silently break real shader packs.

## Current Implementation

`CustomUniformExpressionManager` accepts both forms:

- `smooth(value, fadeUp, fadeDown)`
- `smooth(id, value, fadeUp, fadeDown)` when the first argument is an integer constant

The optional integer ID is currently used only to identify which argument is the smoothed value. Smoothing state is stored per compiled `SmoothNode`, not in a manager-wide map keyed by the integer ID.

This means:

- Calls without an explicit ID are independent per expression node.
- Calls with unique explicit IDs are also independent per expression node.
- Two calls that reuse the same explicit ID remain independent in this port.
- Scalar and vector values use the same component-wise half-life smoothing path.

`CustomUniformExpressionManagerTest#duplicateExplicitSmoothIdsRemainExpressionLocalForTargetPackCompatibility` now pins this behavior in code. The test uses two expressions with the same explicit ID and distinct target values, proving they keep separate accumulators across frames.

## Evidence Checked

Public documentation says the ID is optional and unique:

- Iris custom uniform docs: `smooth([id], val, [fadeUpTime, [fadeDownTime]])` has an optional unique ID and supports vector inputs. Source: https://shaders.properties/current/reference/shadersproperties/custom_uniforms/
- OptiDocs custom uniform docs: the `id` must be unique and is generated automatically when omitted. That page targets the latest OptiFine/Minecraft behavior, not specifically Forge 1.12.2. Source: https://optifine.readthedocs.io/shaders_dev/uniforms.html

Local source evidence is incomplete:

- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms` contains hardcoded smoothed uniforms, but not enough source to prove the exact custom-expression ID storage model.
- `../Oculus-1.16.5/src/main/java/net/coderbot/iris/shaderpack/ShaderProperties.java` still marks custom uniform / variable parsing as missing, so it is not evidence for the exact `smooth([id], ...)` evaluator.
- The latest uniform audit fixed a separate hardcoded `shdFade` binding mismatch in `ProgramBuilder`; that does not prove anything about duplicate smooth-ID ownership.
- No standalone `../Relictium` source directory is present in this Linux layout.

Local shader-pack evidence complicates a keyed-state change:

- `run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/shaders.properties` reuses ID `4` for `eyeBrightnessM` and `eyeBrightnessM2`.
- The same file reuses ID `54` for `inSoulValley` and `inPaleGarden`.
- Those duplicate IDs violate the public uniqueness rule, but they are present in the current real-world target pack.
- `CustomUniformExpressionManagerTest#complementaryRuntimeCustomUniformBlockCompilesAndEvaluatesFromTargetPack` now asserts those duplicate-ID expressions are present in the local target pack before compiling and evaluating the custom-uniform block.

## Current Compatibility Decision

Do not change `smooth(id, ...)` to manager-wide keyed state unless source-level evidence proves that the target behavior requires it and the duplicate-ID shader-pack behavior has been tested.

Reasoning:

- For valid shader packs that follow the documented unique-ID rule, per-node state and keyed unique-ID state produce independent smoothers.
- For packs with duplicate IDs, keyed global state can make unrelated uniforms share or collide in one frame.
- Complementary Reimagined currently contains duplicate IDs in the local target pack, so a blind keyed-state change would be a high-risk regression.

## Future Audit Checklist

Before changing this behavior:

1. Obtain source or a decompiled reference for the exact OptiFine/Iris custom expression evaluator that owns `smooth([id], ...)`.
2. Identify whether explicit IDs are stored in a global smoother table, an expression-local object, or another scope.
3. Confirm how duplicate explicit IDs behave when two custom uniforms with different target expressions are evaluated in the same frame.
4. Compare the local Complementary duplicate-ID pairs against the reference implementation in a real client.
5. Add focused tests that encode the proven behavior.
6. Update this file, `docs/uniform-gap-analysis.md`, `docs/directive-support.md`, and `docs/backport-status.md`.

Until that audit is complete, document this as a known custom-expression parity risk, not as a closed parity item.
