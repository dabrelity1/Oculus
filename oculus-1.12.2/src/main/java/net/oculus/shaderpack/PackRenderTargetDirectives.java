package net.oculus.shaderpack;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Minimal stub mirroring the render-target directive container from the modern
 * Iris codebase. The full implementation exposes per-target formats and clear
 * behaviour; for the 1.12 port we keep the structure but return empty defaults.
 */
public final class PackRenderTargetDirectives {
	public static final int[] BASELINE_SUPPORTED_RENDER_TARGETS = new int[] {0};

	public Map<Integer, RenderTargetSettings> getRenderTargetSettings() {
		return Collections.emptyMap();
	}

	public List<Integer> getBuffersToBeCleared() {
		return Collections.emptyList();
	}

	public static final class RenderTargetSettings {
		public int getInternalFormat() {
			return 0;
		}
	}
}
