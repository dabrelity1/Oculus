package net.oculus.shaderpack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.oculus.gl.blending.AlphaTestOverride;
import net.oculus.gl.blending.BlendModeOverride;
import net.oculus.gl.blending.BufferBlendInformation;

public final class ProgramDirectives {
    private final int[] drawBuffers;
    private final float viewportScale;
    private final AlphaTestOverride alphaTestOverride;
    private final Optional<BlendModeOverride> blendModeOverride;
    private final List<BufferBlendInformation> bufferBlendInformations;
    private final Set<Integer> mipmappedBuffers;
    private final Map<Integer, Boolean> explicitFlips;

    public ProgramDirectives() {
        this(new int[] {0}, 1.0f, null, Optional.empty(), Collections.emptyList(),
            Collections.emptySet(), Collections.emptyMap());
    }

    public ProgramDirectives(ProgramSet parent, String programName, String fragmentSource,
                             ShaderProperties properties, BlendModeOverride defaultBlendOverride) {
        int[] resolvedDrawBuffers = resolveDrawBuffers(fragmentSource);

        float resolvedViewportScale = properties == null ? 1.0f
            : properties.getViewportScaleOverrides().getOrDefault(programName, 1.0f);

        AlphaTestOverride alphaOverride = properties == null ? null
            : properties.getAlphaTestOverrides().get(programName);

        BlendModeOverride propertyOverride = properties == null ? null
            : properties.getBlendModeOverrides().get(programName);

        List<BufferBlendInformation> bufferOverrides;
        if (properties == null) {
            bufferOverrides = Collections.emptyList();
        } else {
            List<BufferBlendInformation> overrides = properties.getBufferBlendOverrides().get(programName);
            bufferOverrides = overrides == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(overrides));
        }

        Optional<BlendModeOverride> resolvedBlendOverride = Optional.ofNullable(
            propertyOverride != null ? propertyOverride : defaultBlendOverride
        );

        Map<Integer, Boolean> flips;
        Set<Integer> supportedTargets = Collections.emptySet();
        if (parent != null) {
            flips = new HashMap<>(parent.getPackDirectives().getExplicitFlips(programName));
            supportedTargets = parent.getPackDirectives().getRenderTargetDirectives().getRenderTargetSettings().keySet();
        } else {
            flips = Collections.emptyMap();
        }

        Set<Integer> mipmapped = new HashSet<>();
        if (parent != null) {
            DispatchingDirectiveHolder directiveHolder = new DispatchingDirectiveHolder();
            for (Integer index : supportedTargets) {
                if (index == null) {
                    continue;
                }

                java.util.function.Consumer<Boolean> handler = value -> {
                    if (Boolean.TRUE.equals(value)) {
                        mipmapped.add(index);
                    } else {
                        mipmapped.remove(index);
                    }
                };

                directiveHolder.acceptConstBooleanDirective("colortex" + index + "MipmapEnabled", handler);

                if (index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
                    String legacyName = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index);
                    directiveHolder.acceptConstBooleanDirective(legacyName + "MipmapEnabled", handler);
                }
            }

            List<ConstDirectiveParser.ConstDirective> directives = ConstDirectiveParser.findDirectives(fragmentSource);
            for (ConstDirectiveParser.ConstDirective directive : directives) {
                directiveHolder.processDirective(directive);
            }
        }

        this.drawBuffers = resolvedDrawBuffers;
        this.viewportScale = resolvedViewportScale;
        this.alphaTestOverride = alphaOverride;
        this.blendModeOverride = resolvedBlendOverride;
        this.bufferBlendInformations = bufferOverrides;
        this.mipmappedBuffers = Collections.unmodifiableSet(mipmapped);
        this.explicitFlips = Collections.unmodifiableMap(flips);
    }

    private ProgramDirectives(int[] drawBuffers, float viewportScale, AlphaTestOverride alphaTestOverride,
                              Optional<BlendModeOverride> blendModeOverride, List<BufferBlendInformation> bufferBlendInformations,
                              Set<Integer> mipmappedBuffers, Map<Integer, Boolean> explicitFlips) {
        this.drawBuffers = Arrays.copyOf(drawBuffers, drawBuffers.length);
        this.viewportScale = viewportScale;
        this.alphaTestOverride = alphaTestOverride;
        this.blendModeOverride = blendModeOverride == null ? Optional.empty() : blendModeOverride;
        this.bufferBlendInformations = bufferBlendInformations == null ? Collections.emptyList() : bufferBlendInformations;
        this.mipmappedBuffers = mipmappedBuffers == null ? Collections.emptySet() : Collections.unmodifiableSet(mipmappedBuffers);
        this.explicitFlips = explicitFlips == null ? Collections.emptyMap() : Collections.unmodifiableMap(explicitFlips);
    }

    public int[] getDrawBuffers() {
        return Arrays.copyOf(drawBuffers, drawBuffers.length);
    }

    public float getViewportScale() {
        return viewportScale;
    }

    public Optional<AlphaTestOverride> getAlphaTestOverride() {
        return Optional.ofNullable(alphaTestOverride);
    }

    public Optional<BlendModeOverride> getBlendModeOverride() {
        return blendModeOverride;
    }

    public List<BufferBlendInformation> getBufferBlendOverrides() {
        return bufferBlendInformations;
    }

    public Set<Integer> getMipmappedBuffers() {
        return mipmappedBuffers;
    }

    public Map<Integer, Boolean> getExplicitFlips() {
        return explicitFlips;
    }

    public ProgramDirectives withOverriddenDrawBuffers(int[] drawBuffersOverride) {
        return new ProgramDirectives(drawBuffersOverride, viewportScale, alphaTestOverride, blendModeOverride,
            bufferBlendInformations, mipmappedBuffers, explicitFlips);
    }

    private static int[] resolveDrawBuffers(String fragmentSource) {
        Optional<CommentDirective> drawbuffersDirective = findCommentDirective(fragmentSource, CommentDirective.Type.DRAWBUFFERS);
        Optional<CommentDirective> rendertargetsDirective = findCommentDirective(fragmentSource, CommentDirective.Type.RENDERTARGETS);
        Optional<CommentDirective> applied = getAppliedDirective(drawbuffersDirective, rendertargetsDirective);

        return applied.map(commentDirective -> {
            if (commentDirective.getType() == CommentDirective.Type.DRAWBUFFERS) {
                return parseDigits(commentDirective.getDirective().toCharArray());
            } else if (commentDirective.getType() == CommentDirective.Type.RENDERTARGETS) {
                return parseDigitList(commentDirective.getDirective());
            }
            throw new IllegalStateException("Unhandled comment directive type");
        }).orElse(new int[] {0});
    }

    private static Optional<CommentDirective> findCommentDirective(String fragmentSource, CommentDirective.Type type) {
        return CommentDirectiveParser.findDirective(fragmentSource, type);
    }

    private static Optional<CommentDirective> getAppliedDirective(Optional<CommentDirective> drawbuffers,
                                                                  Optional<CommentDirective> rendertargets) {
        if (drawbuffers.isPresent() && rendertargets.isPresent()) {
            return drawbuffers.get().getLocation() > rendertargets.get().getLocation() ? drawbuffers : rendertargets;
        }
        return drawbuffers.isPresent() ? drawbuffers : rendertargets;
    }

    private static int[] parseDigits(char[] directiveChars) {
        int[] buffers = new int[directiveChars.length];
        for (int i = 0; i < directiveChars.length; i++) {
            buffers[i] = Character.digit(directiveChars[i], 10);
        }
        return buffers;
    }

    private static int[] parseDigitList(String digitListString) {
        String[] tokens = digitListString.split(",");
        int[] result = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            result[i] = Integer.parseInt(tokens[i]);
        }
        return result;
    }
}
