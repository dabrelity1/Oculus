package net.oculus.shaderpack;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.oculus.gl.blending.BlendModeOverride;

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
    private final Optional<BlendModeOverride> blendModeOverride;

    public ProgramDirectives() {
        this(new int[] {0}, Collections.emptyMap(), 1.0F, Collections.emptySet(), Optional.empty());
    }

    public ProgramDirectives(ProgramSet parent, String programName, ShaderProperties properties,
                             BlendModeOverride defaultOverride) {
        this(new int[] {0}, parent.getPackDirectives().getExplicitFlips(programName), 1.0F,
            Collections.emptySet(), Optional.ofNullable(defaultOverride));
    }

    private ProgramDirectives(int[] drawBuffers, Map<Integer, Boolean> explicitFlips, float viewportScale,
                              Set<Integer> mipmappedBuffers, Optional<BlendModeOverride> blendModeOverride) {
        this.drawBuffers = Objects.requireNonNull(drawBuffers, "drawBuffers");
        this.explicitFlips = explicitFlips == null ? Collections.emptyMap() : explicitFlips;
        this.viewportScale = viewportScale;
        this.mipmappedBuffers = mipmappedBuffers == null ? Collections.emptySet() : Collections.unmodifiableSet(mipmappedBuffers);
        this.blendModeOverride = blendModeOverride == null ? Optional.empty() : blendModeOverride;
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
        return mipmappedBuffers;
    }

    public Optional<BlendModeOverride> getBlendModeOverride() {
        return blendModeOverride;
    }

    public ProgramDirectives withOverriddenDrawBuffers(int[] newDrawBuffers) {
        return new ProgramDirectives(newDrawBuffers, explicitFlips, viewportScale, mipmappedBuffers, blendModeOverride);
    }
}
