package net.oculus.shaderpack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.oculus.Oculus;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.shaderpack.directives.DirectiveHolder;
import net.oculus.vendored.joml.Vector4f;

public final class PackRenderTargetDirectives {
	public static final List<String> LEGACY_RENDER_TARGETS = Collections.unmodifiableList(Arrays.asList(
		"gcolor",
		"gdepth",
		"gnormal",
		"composite",
		"gaux1",
		"gaux2",
		"gaux3",
		"gaux4"
	));

	public static final Set<Integer> BASELINE_SUPPORTED_RENDER_TARGETS = Collections.unmodifiableSet(
		IntStream.range(0, IrisLimits.MAX_COLOR_BUFFERS).boxed().collect(Collectors.toSet())
	);

	private final Map<Integer, RenderTargetSettings> renderTargetSettings;

	public PackRenderTargetDirectives(Set<Integer> supportedRenderTargets) {
		this.renderTargetSettings = new HashMap<>();
		supportedRenderTargets.forEach(index -> renderTargetSettings.put(index, new RenderTargetSettings()));
	}

	public PackRenderTargetDirectives() {
		this(BASELINE_SUPPORTED_RENDER_TARGETS);
	}

	public void acceptDirectives(DirectiveHolder directives) {
		Optional.ofNullable(renderTargetSettings.get(7)).ifPresent(colortex7 ->
			directives.acceptCommentStringDirective("GAUX4FORMAT", format -> {
				InternalTextureFormat legacyFormat = parseLegacyGaux4Format(format);
				if (legacyFormat != null) {
					colortex7.requestedFormat = legacyFormat;
				} else {
					Oculus.LOGGER.warn(
						"Ignoring GAUX4FORMAT directive /* GAUX4FORMAT:{}*/ because {} must be RGBA32F, RGB32F, or RGB16. Use `const int colortex7Format = {};` instead.",
						format, format, format);
				}
			})
		);

		Optional.ofNullable(renderTargetSettings.get(1)).ifPresent(gdepth ->
			directives.acceptUniformDirective("gdepth", () -> {
				if (gdepth.requestedFormat == InternalTextureFormat.RGBA) {
					gdepth.requestedFormat = InternalTextureFormat.RGBA32F;
				}
			})
		);

		renderTargetSettings.forEach((index, settings) -> {
			acceptBufferDirectives(directives, settings, "colortex" + index);
			if (index < LEGACY_RENDER_TARGETS.size()) {
				acceptBufferDirectives(directives, settings, LEGACY_RENDER_TARGETS.get(index));
			}
		});
	}

	private void acceptBufferDirectives(DirectiveHolder directives, RenderTargetSettings settings, String bufferName) {
		directives.acceptConstStringDirective(bufferName + "Format", format -> {
			Optional<InternalTextureFormat> internalFormat = InternalTextureFormat.fromString(format);
			if (internalFormat.isPresent()) {
				settings.requestedFormat = internalFormat.get();
			} else {
				Oculus.LOGGER.warn("Unrecognized internal texture format '{}' for {}", format, bufferName);
			}
		});

		directives.acceptConstBooleanDirective(bufferName + "Clear", value -> settings.clear = value);
		directives.acceptConstVec4Directive(bufferName + "ClearColor", value -> settings.clearColor = value);
	}

	private static InternalTextureFormat parseLegacyGaux4Format(String format) {
		if ("RGBA32F".equals(format)) {
			return InternalTextureFormat.RGBA32F;
		}
		if ("RGB32F".equals(format)) {
			return InternalTextureFormat.RGB32F;
		}
		if ("RGB16".equals(format)) {
			return InternalTextureFormat.RGB16;
		}
		return null;
	}

	public Map<Integer, RenderTargetSettings> getRenderTargetSettings() {
		return Collections.unmodifiableMap(renderTargetSettings);
	}

	public List<Integer> getBuffersToBeCleared() {
		List<Integer> buffersToBeCleared = new ArrayList<>();
		renderTargetSettings.forEach((index, settings) -> {
			if (settings.shouldClear()) {
				buffersToBeCleared.add(index);
			}
		});
		return buffersToBeCleared;
	}

	public static final class RenderTargetSettings {
		private InternalTextureFormat requestedFormat = InternalTextureFormat.RGBA;
		private boolean clear = true;
		private Vector4f clearColor = null;

		public InternalTextureFormat getInternalFormat() {
			return requestedFormat;
		}

		public boolean shouldClear() {
			return clear;
		}

		public Optional<Vector4f> getClearColor() {
			return Optional.ofNullable(clearColor);
		}

		@Override
		public String toString() {
			return "RenderTargetSettings{" +
				"requestedFormat=" + requestedFormat +
				", clear=" + clear +
				", clearColor=" + clearColor +
				'}';
		}
	}
}
