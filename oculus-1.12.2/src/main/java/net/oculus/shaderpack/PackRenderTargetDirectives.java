package net.oculus.shaderpack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.oculus.shaderpack.directives.DirectiveHolder;

/**
 * Minimal stub mirroring the render-target directive container from the modern
 * Iris codebase. A handful of helper constants and data structures are wired
 * in so the translated {@link ProgramSet} can depend on them, but no real
 * directive parsing is performed yet.
 */
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
		IntStream.range(0, 8).boxed().collect(Collectors.toSet())
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
		// Directive parsing will be ported alongside the metadata system.
	}

	public Map<Integer, RenderTargetSettings> getRenderTargetSettings() {
		return Collections.unmodifiableMap(renderTargetSettings);
	}

	public List<Integer> getBuffersToBeCleared() {
		return new ArrayList<>();
	}

	public static final class RenderTargetSettings {
		public int getInternalFormat() {
			return 0;
		}

		public boolean shouldClear() {
			return true;
		}
	}
}
