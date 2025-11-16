<p align="center">
  <img src="banner.png" alt="Oculus 1.12.2 Backport banner">
</p>

# Oculus 1.12.2 Backport

> A Forge 1.12.2-focused continuation of Oculus by **dabrelity1**. This branch rewires the modern Iris/Oculus shader pipeline so classic modpacks can finally use full-featured shaders without downgrading to OptiFine.

## What this project is
- **Target Minecraft**: 1.12.2 (Forge 14.23.5).<br>
- **Goal**: Parity with the Iris/Oculus 1.16.5 renderer—gbuffers, shadow passes, compute stages, GUI, and pack compatibility—on legacy packs.
- **Status**: Active development. Large pieces are still being ported (see open issues for the breakdown of remaining blockers).

## Current focus areas
1. Captured rendering state, camera trackers, and ID maps.
2. Full uniform suite (camera, weather, player, compatibility shims).
3. Framebuffer + shadow map rendering and sampler bindings.
4. Render phase mixins for world, hand, weather, and composite passes.
5. Shader pack UI/option persistence and compute/image wiring.

If you want to help, pick an unchecked issue from the tracker or open a discussion before starting a larger subsystem.

## Requirements & compatibility
- Forge 1.12.2-14.23.5.2768 (matching the Gradle config).
- [Relictium 1.2.0](https://modrinth.com/mod/relictium) (required dependency).
- Java 8 (HotSpot). Later JVMs are not tested.
- OptiFine is **not** supported and never will be.

## Building from source
> The project uses the legacy ForgeGradle 2.3 toolchain.

```powershell
cd .\oculus-1.12.2\
.\gradlew.bat clean build
```

- `gradlew.bat runClient` launches a dev instance with shaders enabled (once the remaining mixins land).
- Built jars reside in `oculus-1.12.2/build/libs`.

## Getting help
- Bugs & feature gaps: [GitHub Issues](https://github.com/dabrelity1/Oculus/issues)
- Development chat: open a discussion in the repo or ping `@dabrelity1` via GitHub.

## Roadmap snapshot
- [ ] Finish render-state backbones and uniform parity.
- [ ] Hook real gbuffers, shadow maps, and compute/image bindings.
- [ ] Restore shader pack GUI parity (profiles, overrides, folder tools).
- [ ] Add automated shader-pack validation + CI smoke tests.
- [ ] Publish pre-release builds for community packs.

## License & acknowledgements
- This fork remains under the [LGPL-3.0](./LICENSE).
- Huge credit to Asek3, coderbot, and the Iris team for the original engine and ongoing upstream work.

If this backport helps your modpack or showcase, consider sharing feedback or fixes—every issue closed gets us closer to fully-featured shaders on 1.12.2.
