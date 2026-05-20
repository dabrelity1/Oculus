# Legacy Shims And Remnants

Last updated: 2026-05-19.

This file records source-tree areas that exist for compatibility, migration, or historical reasons but should not be mistaken for completed Oculus 1.12.2 runtime systems.

Source code is still authoritative. If any item here becomes active runtime behavior, audit it against the 1.16.5 reference and the Forge 1.12.2 target before relying on it.

## Why This Exists

The project began from a mixed foundation with copied modern Iris/Oculus concepts, 1.12.2 Forge code, Relictium compatibility, and local experiments. Some files compile because other translated classes need a type name, but a compiling type is not evidence that the corresponding renderer feature is implemented.

Future agents should use this document to avoid two common mistakes:

- Treating compatibility shims as production implementations.
- Chasing inactive copied configs or old package names instead of the active `net.oculus` port.

`LegacyShimUsageSourceTest` guards the current routing: active `net.oculus` source must not import historical `net.coderbot.iris` runtime packages, inactive modern compatibility shells must stay out of active runtime source, `com.mojang.blaze3d.vertex.*` use is limited to `OculusVertexFormats`, the early `OculusMixinLoader` must queue only `oculus.mixins.json`, the active mixin JSON must stay under `net.oculus.mixin`, and `build.gradle` must not restore the late `-Dmixin.configs` route or queue historical mixin configs.

## Active Runtime Owner Rule

Before changing one of these files, answer all of the following:

- Is it referenced by active `net.oculus` code, an active mixin config, or Gradle runtime setup?
- Is the behavior required by the 1.16.5 reference class being ported?
- Does Forge 1.12.2 have an equivalent runtime API, or does this need a real adapter?
- Is there a focused test or client runtime check that can prove the behavior?

If the answer is unknown, document the uncertainty and inspect the call sites first. Do not assume that a modern package name implies active behavior.

## Known Compile-Time Shims

| Path | Current role | Risk |
| --- | --- | --- |
| `src/main/java/com/mojang/blaze3d/systems/RenderSystem.java` | Translation facade for selected 1.16.5 `RenderSystem` calls to 1.12.2 `GlStateManager` / LWJGL calls. | Some methods are intentionally no-op because 1.12.2 has no matching bootstrap/release concept. Do not add callers without checking state ownership. |
| `src/main/java/com/mojang/blaze3d/vertex/DefaultVertexFormat.java` | Defines modern-style vertex-format element constants used by the port's terrain vertex format declarations. | It is not Minecraft's 1.16 implementation. Verify byte sizes, usage indices, and downstream attribute binding before adding fields. |
| `src/main/java/com/mojang/blaze3d/vertex/VertexFormat.java` | Small immutable container for translated vertex-format elements. | Only safe for the operations currently used by `net.oculus.pipeline.vertex`. It is not a general 1.16 vertex-format backport. |
| `src/main/java/com/mojang/blaze3d/vertex/VertexFormatElement.java` | Small value object for translated vertex-format elements. | Treat as a local data model, not as Mojang runtime behavior. |
| `src/main/java/com/mojang/blaze3d/vertex/VertexBuffer.java` | Empty compatibility type. | This is not production runtime behavior. If a real caller appears, implement or replace it with a real 1.12.2 VBO path before claiming parity. |
| `src/main/java/com/mojang/blaze3d/platform/Framebuffer.java` | Empty compatibility type with stored width/height and zero texture IDs. | This is not production runtime behavior. Active framebuffer ownership should use `net.oculus.rendertarget`, `net.oculus.pipeline.framebuffer`, and Minecraft's 1.12 `Framebuffer`. |
| `src/main/java/com/mojang/math/Matrix4f.java` | Tiny float-array holder for translated code that needs a modern matrix type name. | It is not a full matrix math implementation. Use `net.oculus.gl.state.MatrixMath`, `net.oculus.vendored.joml`, or Minecraft 1.12 matrix state for real transforms. |

The source guard proves `VertexBuffer`, `com.mojang.blaze3d.platform.Framebuffer`, and `com.mojang.math.Matrix4f` are not active `net.oculus` runtime dependencies today. It also proves the active early mixin route does not queue the old packaged mixin JSON resources today. If that test fails after a future edit, do not weaken it until the new caller or queued config has a source-backed 1.12.2 implementation and corresponding docs.

## `net.coderbot.iris` Package In This Project

The active port is under `src/main/java/net/oculus`. A small `src/main/java/net/coderbot/iris` tree also exists. Current searches show it is mostly self-contained and not the primary runtime path for program construction, render targets, or pipeline behavior.

Current active equivalents usually live here:

| Historical-looking area | Active 1.12.2 owner |
| --- | --- |
| `net.coderbot.iris.gl.program.*` | `net.oculus.gl.program.*` |
| `net.coderbot.iris.gl.framebuffer.GlFramebuffer` | `net.oculus.rendertarget.RenderTargets`, `net.oculus.pipeline.framebuffer`, `net.oculus.gl` |
| `net.coderbot.iris.gl.IrisRenderSystem` | `net.oculus.gl.OculusRenderSystem` and direct 1.12 `GlStateManager`/LWJGL paths |
| `net.coderbot.iris.compat.sodium.mixin.options.MixinSodiumOptionsGUI` | Active GUI code under `net.oculus.gui` and the active mixin list in `src/main/resources/oculus.mixins.json` |

Do not port new work into `net.coderbot.iris` unless a fresh call-site audit proves it is active. Prefer moving behavior into the active `net.oculus` package that owns the subsystem.

Specific trap: `src/main/java/net/coderbot/iris/gl/program/ComputeProgram.java` and `src/main/java/net/coderbot/iris/gl/IrisRenderSystem.java` still contain historical LWJGL2 compute placeholders. The active compute implementation used by postprocess, final, shadow, and color-space paths is `src/main/java/net/oculus/gl/program/ComputeProgram.java` plus `src/main/java/net/oculus/gl/OculusRenderSystem.java`.

A 2026-05-17 placeholder-language scan still found those historical compute placeholders and many normal active-source guard returns, but did not prove a new active-runtime placeholder. Do not use that scan as completion proof; use it as a pointer to suspicious files, then compare each candidate against the local 1.16.5 source before editing.

## Mixin Resource Remnants

`build.gradle` now packages Oculus with an early Forge coremod manifest entry:

```text
FMLCorePlugin: net.oculus.mixins.OculusMixinLoader
FMLCorePluginContainsFMLMod: true
ForceLoadAsMod: true
```

`OculusMixinLoader` implements MixinBooter `IEarlyMixinLoader` and returns only `oculus.mixins.json`. The active mixin config is `src/main/resources/oculus.mixins.json`, which points at `net.oculus.mixin.*`. `LegacyShimUsageSourceTest` now parses/checks this source route so old packaged configs such as `mixins.oculus.compat*.json` or `oculus-batched-entity-rendering.mixins.json` cannot silently become the active runtime route without a failing source test.

Do not restore `-Dmixin.configs=oculus.mixins.json`, `@MixinLoader`, or `ILateMixinLoader` as the primary loading path. A local client run proved the late route queues `BlockStateAmbientOcclusionMixin` after `BlockStateContainer$StateImplementation` has already loaded.

Other mixin JSON files remain in `src/main/resources`, including configs with `net.coderbot` package names. Treat them as historical or compatibility remnants until proven otherwise. They are still packaged resources, so do not delete them casually, but do not use them as evidence that the listed mixins are active.

If a future change intentionally activates one of those configs, update:

- `build.gradle`
- `src/main/resources/oculus.mixins.json` or the new active config
- `docs/repo-map.md`
- `docs/subsystem-index.md`
- `docs/verification.md`
- This file

Then run `compileJava`, inspect the generated refmap, and perform a client launch.

## Completion Implication

These shims and remnants are one reason the full port cannot be called complete from source inspection alone. Before final completion, every compatibility shim must be either:

- Proven unused by active runtime paths.
- Replaced by a real 1.12.2 implementation.
- Documented as an intentional 1.12.2 engine limitation with runtime evidence that no shader-pack behavior depends on it.
