package net.coderbot.iris.compat.sodium.mixin.shader_overrides;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import net.coderbot.iris.Iris;
import net.coderbot.iris.compat.sodium.impl.shader_overrides.ChunkRenderBackendExt;
import net.coderbot.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides;
import net.coderbot.iris.compat.sodium.impl.vertex_format.IrisModelVertexFormats;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.pipeline.SodiumTerrainPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.coderbot.iris.shaderpack.transform.StringTransformations;
import net.coderbot.iris.shaderpack.transform.Transformations;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.IOUtils;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlShader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkRenderShaderBackend;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the Iris shader program overrides to Relictium's chunk rendering pipeline.
 */
@Mixin(ChunkRenderShaderBackend.class)
public class MixinChunkRenderShaderBackend implements ChunkRenderBackendExt {
	@Unique
	private IrisChunkProgramOverrides irisChunkProgramOverrides;

	@Unique
	private RenderDevice device;

	@Unique
	private ChunkProgram override;

	@Shadow(remap = false)
	protected ChunkProgram activeProgram;

	@Shadow(remap = false)
	@Final
	protected ChunkVertexType vertexType;

	@Shadow(remap = false)
	public void begin() {
		throw new AssertionError();
	}

	@Inject(method = "<init>", at = @At("RETURN"), remap = false)
	private void iris$onInit(ChunkVertexType vertexType, CallbackInfo ci) {
		this.irisChunkProgramOverrides = new IrisChunkProgramOverrides();
	}

	@Redirect(method = "createShader", at = @At(value = "INVOKE",
			target = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderLoader;loadShader(Lme/jellysquid/mods/sodium/client/gl/device/RenderDevice;Lme/jellysquid/mods/sodium/client/gl/shader/ShaderType;Lnet/minecraft/util/ResourceLocation;Ljava/util/List;)Lme/jellysquid/mods/sodium/client/gl/shader/GlShader;"),
		remap = false)
	private GlShader iris$redirectOriginalShader(RenderDevice renderDevice, ShaderType shaderType, ResourceLocation name, List<String> constants) {
		if (this.vertexType == IrisModelVertexFormats.MODEL_VERTEX_XHFP) {
			String shader = getShaderSource(getShaderPath(name));
			shader = shader.replace("v_LightCoord = a_LightCoord", "v_LightCoord = (iris_LightmapTextureMatrix * vec4(a_LightCoord, 0, 1)).xy");

			StringTransformations transformations = new StringTransformations(shader);
			transformations.injectLine(Transformations.InjectionPoint.BEFORE_CODE,
					"mat4 iris_LightmapTextureMatrix = mat4(vec4(0.00390625, 0.0, 0.0, 0.0), vec4(0.0, 0.00390625, 0.0, 0.0), vec4(0.0, 0.0, 0.00390625, 0.0), vec4(0.03125, 0.03125, 0.03125, 1.0));");

			return new GlShader(renderDevice, shaderType, name, transformations.toString(), ShaderConstants.fromStringList(constants));
		}

		return ShaderLoader.loadShader(renderDevice, shaderType, name, constants);
	}

	private static String getShaderPath(ResourceLocation name) {
		return String.format("/assets/%s/shaders/%s", name.getResourceDomain(), name.getResourcePath());
	}

	private static String getShaderSource(String path) {
		try {
			InputStream in = ShaderLoader.class.getResourceAsStream(path);
			Throwable thrown = null;

			String source;
			try {
				if (in == null) {
					throw new RuntimeException("Shader not found: " + path);
				}

				source = IOUtils.toString(in, StandardCharsets.UTF_8);
			} catch (Throwable error) {
				thrown = error;
				throw error;
			} finally {
				if (in != null) {
					if (thrown != null) {
						try {
							in.close();
						} catch (Throwable suppressed) {
							thrown.addSuppressed(suppressed);
						}
					} else {
						in.close();
					}
				}
			}

			return source;
		} catch (IOException e) {
			throw new RuntimeException("Could not read shader sources", e);
		}
	}

	@Inject(method = "createShaders", at = @At("HEAD"), remap = false)
	private void iris$onCreateShaders(RenderDevice renderDevice, CallbackInfo ci) {
		this.device = renderDevice;
		WorldRenderingPipeline worldRenderingPipeline = Iris.getPipelineManager().getPipelineNullable();
		SodiumTerrainPipeline sodiumTerrainPipeline = null;

		if (worldRenderingPipeline != null) {
			sodiumTerrainPipeline = worldRenderingPipeline.getSodiumTerrainPipeline();
		}

		this.irisChunkProgramOverrides.createShaders(sodiumTerrainPipeline, renderDevice);
	}

	@Override
	public void iris$begin(BlockRenderPass pass) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			GlStateManager.disableCull();
		}

		this.override = this.irisChunkProgramOverrides.getProgramOverride(this.device, pass);

		Iris.getPipelineManager().getPipeline().ifPresent(WorldRenderingPipeline::beginSodiumTerrainRendering);
		begin();
	}

	@Inject(method = "begin", at = @At(value = "FIELD",
			target = "Lme/jellysquid/mods/sodium/client/render/chunk/shader/ChunkRenderShaderBackend;activeProgram:Lme/jellysquid/mods/sodium/client/render/chunk/shader/ChunkProgram;",
			args = "opcode=PUTFIELD", shift = At.Shift.AFTER), remap = false)
	private void iris$applyOverride(CallbackInfo ci) {
		if (this.override != null) {
			this.activeProgram = this.override;
		}
	}

	@Inject(method = "end", at = @At("RETURN"), remap = false)
	private void iris$onEnd(CallbackInfo ci) {
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		Iris.getPipelineManager().getPipeline().ifPresent(WorldRenderingPipeline::endSodiumTerrainRendering);
	}

	@Inject(method = "delete", at = @At("HEAD"), remap = false)
	private void iris$onDelete(CallbackInfo ci) {
		this.irisChunkProgramOverrides.deleteShaders();
	}
}
