# Oculus 1.12.2 Port

A port of Oculus/Iris shader mod to Minecraft 1.12.2 with Forge.

## Current Status

**Alpha - Work in Progress**

The mod compiles and runs, with basic shader pack support infrastructure in place. However, shader rendering is currently a stub implementation.

### What Works

- ✅ **Mod Loading** - Loads properly with Forge 1.12.2
- ✅ **Shader Pack Loading** - Loads and parses shader packs from the `shaderpacks` folder
- ✅ **Shader GUI** - The shader pack selection screen is functional
- ✅ **Shader Pack Options** - Options parsing and configuration
- ✅ **Include Processing** - `#include` directive processing for shader files
- ✅ **Pipeline Infrastructure** - WorldRenderingPipeline, ShaderWorldRenderingPipeline, PipelineManager
- ✅ **Framebuffer Management** - FramebufferManager, Framebuffer, RenderTarget classes
- ✅ **GL Utilities** - OculusRenderSystem, shader compilation, program creation
- ✅ **Uniform System** - CapturedRenderingState, CelestialUniforms, SystemTimeUniforms, etc.
- ✅ **Sampler System** - ProgramSamplers, TextureBinding classes

### What Needs Work

- ⏳ **Shader Program Compilation from Packs** - The pipeline loads shader pack data but doesn't compile actual shader programs from the pack
- ⏳ **Composite Passes** - CompositeRenderer needs full implementation
- ⏳ **Final Pass** - FinalPassRenderer needs implementation
- ⏳ **Shadow Rendering** - ShadowRenderer needs implementation
- ⏳ **Deferred Rendering** - Full deferred pipeline implementation

## Building

```bash
cd oculus-1.12.2
./gradlew build
```

## Running

```bash
./gradlew runClient
```

## Project Structure

```
src/main/java/
├── net/oculus/           # Main mod code (1.12.2 native)
│   ├── pipeline/         # Rendering pipeline
│   ├── shaderpack/       # Shader pack loading
│   ├── uniforms/         # Shader uniforms
│   ├── gl/               # OpenGL utilities
│   └── gui/              # GUI screens
└── net/coderbot/iris/    # Compatibility layer (from 1.16.5)
```

## Dependencies

- Minecraft 1.12.2
- Forge 14.23.5.2860
- Relictium (Sodium-like performance mod for 1.12.2)

## Credits

- Original Iris/Oculus developers for the shader mod
- This port maintains compatibility with OptiFine-style shader packs
