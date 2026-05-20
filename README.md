<p align="center">
  <img src="banner.png" alt="Oculus 1.12.2 Backport banner">
</p>

# Oculus 1.12.2 Backport

> A Forge 1.12.2-focused continuation of Oculus by **dabrelity1**. This branch rewires the modern Iris/Oculus shader pipeline so classic modpacks can finally use full-featured shaders without downgrading to OptiFine.

## What this project is
- **Target Minecraft**: 1.12.2 (Forge 14.23.5).<br>
- **Goal**: Parity with the Iris/Oculus 1.16.5 renderer—gbuffers, shadow passes, compute stages, GUI, and pack compatibility—on legacy packs.
- **Status**: Active development. Large pieces are still being ported (see open issues for the breakdown of remaining blockers).

## Agent and contributor starting points

The active codebase lives in [`oculus-1.12.2`](./oculus-1.12.2). Before changing rendering, shader-pack parsing, mixins, or build behavior, read:

- [Workspace agent guide](./AGENTS.md)
- [Project agent guide](./oculus-1.12.2/AGENTS.md)
- [Documentation index](./oculus-1.12.2/docs/README.md)
- [Current agent handoff](./oculus-1.12.2/docs/agent-handoff.md)
- [Repository map](./oculus-1.12.2/docs/repo-map.md)
- [Subsystem index](./oculus-1.12.2/docs/subsystem-index.md)
- [Render target lifecycle](./oculus-1.12.2/docs/render-target-lifecycle.md)
- [Backport status](./oculus-1.12.2/docs/backport-status.md)
- [Full-port parity roadmap](./oculus-1.12.2/docs/parity-roadmap.md)

Do not treat compile success as full-port completion. The port still needs in-client Minecraft 1.12.2 validation with real shader packs.

## Current focus areas

The current focus changes as the port closes gaps. Use the live handoff and roadmap instead of this root README for active priorities:

- [Current agent handoff](./oculus-1.12.2/docs/agent-handoff.md)
- [Full-port parity roadmap](./oculus-1.12.2/docs/parity-roadmap.md)

If you want to help, start with the documentation index and verify the matching 1.16.5 reference classes before changing a subsystem.

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

If this backport helps your modpack or showcase, consider sharing feedback or fixes—every issue closed gets me closer to fully-featured shaders on 1.12.2.
