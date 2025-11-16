# Shader Uniform & Sampler Gap Analysis

_Last updated: 2025-11-14 (afternoon refresh)_

This document compares the rich uniform/sampler surface provided by the 1.16.5 Iris pipeline against the current 1.12.2 backport. It highlights what already exists, what is missing, and which supporting systems are required before each feature can be implemented.

## Quick summary

- **Matrix & camera state**: Only raw GL model-view/projection matrices exist. There is no captured g-buffer history, inverse matrices, or camera shift logic yet.
- **Gameplay-driven uniforms** (rain, thunder, player status, IDs, etc.) are completely absent on 1.12.2 because the helper state (`CapturedRenderingState`, `FrameUpdateNotifier`, IdMap plumbing) has not been ported.
- **Sampler bindings** now ship with a configurable override map (noise, shadow, render-target/resolved aliases) so shader packs receive deterministic texture units. Dynamic render-target bindings and flip-aware wiring are still missing, so complex packs may continue to log warnings.
- **Compatibility shims** (hardcoded custom uniforms, externally managed matrices) will need bespoke backports for popular packs like BSL, Complementary, and AstralEX.
- **Compute shaders** compile + dispatch through the new `ComputeDispatchManager`, covering shadow, prepare, deferred, composite, and final stages. Image bindings still rely on placeholder textures until real render targets are exposed.

## Detailed matrix

| Category | 1.16.5 coverage | 1.12.2 status | Dependencies to port |
| --- | --- | --- | --- |
| **Matrices & captured state** | `gbufferModelView/Projection` + inverse & previous variants, `shadowModelView/Projection`, stored in `CapturedRenderingState`. | Only `modelViewMatrix` and `projectionMatrix` from `MatrixState` (no copies, no history). | Implement `CapturedRenderingState` equivalent, hook into gbuffer setup to capture matrices each pass, add previous-frame buffer, add utility to invert matrices (JOML). |
| **Camera data** | `CameraUniforms` supplies precision-preserving `cameraPosition`, `previousCameraPosition`, `near/far`, `eyeAltitude`. | `cameraPosition` is raw world coords; no previous position, no near/far constants. | Port `CameraPositionTracker`, add render-distance-derived `far`, store `previousCameraPosition`, expose `near` constant. |
| **Viewport** | `viewWidth`, `viewHeight`, `aspectRatio` from render target. | Width/height only (from `GameDataSuppliers`), no aspect ratio. | Query current framebuffer each frame; compute ratio. |
| **Fog uniforms** | `fogMode`, `fogDensity`, `fogStart`, `fogEnd` with GL state listeners; `fogColor` via `CapturedRenderingState`. | Fog color & start/end pulled once via `glGetFloat`; no mode/density toggles or listener updates. | Add state listeners/mixins or active tracking; port `FogUniforms` logic; ensure `CapturedRenderingState` stores fog color on mixin callback. |
| **World & system time** | `worldTime`, `worldDay`, `moonPhase`, `frameCounter`, `framemod8` (int+float), `frameTime`, `frameTimeCounter`. | Only system timers exist; world time uniforms missing. | Add world refs (level + dimension accessors) and expose values via `ProgramBuilder`. |
| **Weather & lighting** | `rainStrength`, `wetness`, `skyColor`, `fogColor`, `thunderStrength`, `eyeBrightness`, smoothed variants. | None beyond static fog color and camera position. | Need tick delta tracking, `FrameUpdateNotifier`, smoothing helpers, and hooks into level weather values. |
| **Player state** | `is_sneaking`, `is_sprinting`, `is_hurt`, `is_invisible`, `is_burning`, `is_on_ground`, `screenBrightness`, health/hunger/air, `firstPersonCamera`, `isSpectator`, `eyePosition`, `relativeEyePosition`, `playerLookVector`, `playerBodyVector`. | No equivalents exposed to shaders. | Access player entity each frame, mimic Iris uniform registration, add relative-eye calculation once camera shift exists. |
| **Id map + entity context** | `heldItemId`, `heldItemId2`, block-light values, `entityId`, `blockEntityId` via `CapturedRenderingState`. | No ID uniforms or event hooks. | Port IdMap loader, implement block/entity tracking (mixins around render passes), add `CapturedRenderingState` notifiers. |
| **Celestial & world info** | `sunAngle`, `sunPosition`, `moonPosition`, `shadowAngle`, `upPosition`, world metadata (cloud height, bedrock level, ambient light). | None. | Requires sun-path math (Matrix ops + tick delta), access to dimension props, plus g-buffer matrices to transform vectors. |
| **Hardcoded custom uniforms (compat)** | `timeAngle`, `timeBrightness`, `rainStrengthS`, `isDry`, `isEyeInCave`, `velocity`, `starter`, `frameTimeSmooth`, `inSwamp`, `touchmybody`, `effectStrength`, etc. | None. | After base weather/player data exists, port the smoothed helpers used by BSL/Complementary/AstralEX; requires `SmoothedFloat/Vec` utilities. |
| **Externally managed uniforms** | `iris_ModelViewMatrix`, `u_ModelViewProjectionMatrix`, `iris_NormalMatrix`, `u_ModelScale`, `u_TextureScale`, fog uniforms wired to vanilla pipeline. | Only attempts to bind `modelViewMatrix`/`projectionMatrix` if shader declares them; no compatibility names. | Mirror `ExternallyManagedUniforms.addExternallyManagedUniforms116`, ensure vanilla/Sodium integrations supply these when available. |
| **Samplers (world)** | All `colortex#` buffers, `gcolor`, `gnormal`, `gdepthtex`, `depthtex[0-2]`, `noisetex`, `lightmap`, `iris_overlay`, `normals`, `specular`, etc., with reserved texture units and flip tracking. | Static alias map extended with override provider (`oculus_rt#`, `oculus_noise`, `oculus_shadow_*`, etc.) so shader packs can hijack consistent units. Still lacks dynamic render-target binding, flip tracking, and sampler listeners. | Finish `ProgramSamplers` parity: per-pass reserved unit sets, runtime render-target binding, flip snapshots, and custom texture manager integration. |
| **Samplers (shadows & composites)** | `shadowtex0/1`, `shadowtex0HW/1HW`, `watershadow`, `shadowcolorimg#`, `depthtex#`, composite-only samplers, noise textures. | Override map reserves units for `oculus_shadow_*` aliases, but no actual shadow framebuffer bindings yet. | Wire `ShadowMap` textures + composite framebuffers into the sampler builder, add flip-aware depth/noise updates. |
| **Images / compute** | Shadow color textures exposed as images; general image support via `ProgramImages`. | `ProgramImages` now binds declared image uniforms and `ComputeDispatchManager` drives all compute passes each frame. Still lacks actual framebuffer textures to attach. | Populate real render targets/shadow maps, thread them through `ProgramImages`, and add memory barriers per pass. |

## Implementation ordering suggestions

1. **State backbones**: port `CapturedRenderingState`, `CameraPositionTracker`, frame update notifier, and render-stage tracking so higher-level uniforms have data sources.
2. **Matrix + camera uniforms**: once state exists, expose the gbuffer/previous matrices, near/far, precision camera positions, and aspect ratio. This unblocks many packs.
3. **Weather/player/world info**: add the common uniforms (rain, fog, sky color, player states) before the hardcoded compatibility shims.
4. **Sampler overhaul**: implement the full `ProgramSamplers` builder (reserved units + render target samplers + depth/noise/shadow) to eliminate “Unknown sampler” warnings and let shader packs access their expected textures.
5. **Compatibility extras**: port the hardcoded custom uniforms and externally managed bindings once the fundamentals are solid.
6. **Advanced features**: add IdMap/item lights, lightning position, compute images, and optional pack-specific hooks.

Use this checklist to scope upcoming work items and to verify shader packs as each feature lands.
