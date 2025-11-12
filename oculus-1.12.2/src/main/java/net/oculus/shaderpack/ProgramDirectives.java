package net.oculus.shaderpack;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lightweight representation of shader directives parsed from pack metadata.
 * The 1.16 implementation exposes a large surface area; the 1.12 skeleton only
 * keeps the fields referenced by the deferred rendering pipeline.
 */
public final class ProgramDirectives {
    private final int[] drawBuffers;
    private final Map<Integer, Boolean> explicitFlips;
    private final float viewportScale;
    private final Set<Integer> mipmappedBuffers;

    public ProgramDirectives() {
        this(new int[] {0}, Collections.emptyMap(), 1.0F, Collections.emptySet());
    }

    public ProgramDirectives(int[] drawBuffers, Map<Integer, Boolean> explicitFlips, float viewportScale, Set<Integer> mipmappedBuffers) {
        this.drawBuffers = Objects.requireNonNull(drawBuffers, "drawBuffers");
        this.explicitFlips = explicitFlips == null ? Collections.emptyMap() : explicitFlips;
        this.viewportScale = viewportScale;
        this.mipmappedBuffers = mipmappedBuffers == null ? Collections.emptySet() : new HashSet<>(mipmappedBuffers);
    }

    public int[] getDrawBuffers() {
        return Arrays.copyOf(drawBuffers, drawBuffers.length);
    }

    public Map<Integer, Boolean> getExplicitFlips() {
        return Collections.unmodifiableMap(explicitFlips);
    }

    public float getViewportScale() {
        return viewportScale;
    }

    public Set<Integer> getMipmappedBuffers() {
        return Collections.unmodifiableSet(mipmappedBuffers);
    }

    public ProgramDirectives withOverriddenDrawBuffers(int[] newDrawBuffers) {
        return new ProgramDirectives(newDrawBuffers, explicitFlips, viewportScale, mipmappedBuffers);
    }
}
