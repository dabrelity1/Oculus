package net.oculus.shaderpack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import net.oculus.Oculus;
import net.oculus.gl.blending.BlendModeOverride;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.loading.ProgramId;
import net.oculus.shaderpack.util.ComputeSourceCollector;

/**
 * Port of the Iris {@code ProgramSet}. Shader loading and directive parsing are
 * stubbed for now, but the class structure mirrors the upstream version so
 * dependent systems can be migrated without churn.
 */
public class ProgramSet implements ProgramSetInterface {
    private static final int PROGRAM_ARRAY_LENGTH = 99;
    private static final int COMPUTE_ARRAY_LENGTH = 27;

    private final PackDirectives packDirectives;
    private final ShaderPack pack;
    private final ShaderProperties shaderProperties;

    private final ProgramSource shadow;
    private final ComputeSource[] shadowCompute;

    private final ProgramSource[] shadowcomp;
    private final ComputeSource[][] shadowCompCompute;

    private final ProgramSource[] prepare;
    private final ComputeSource[][] prepareCompute;

    private final ProgramSource gbuffersBasic;
    private final ProgramSource gbuffersLine;
    private final ProgramSource gbuffersBeaconBeam;
    private final ProgramSource gbuffersTextured;
    private final ProgramSource gbuffersTexturedLit;
    private final ProgramSource gbuffersTerrain;
    private ProgramSource gbuffersDamagedBlock;
    private final ProgramSource gbuffersSkyBasic;
    private final ProgramSource gbuffersSkyTextured;
    private final ProgramSource gbuffersClouds;
    private final ProgramSource gbuffersWeather;
    private final ProgramSource gbuffersEntities;
    private final ProgramSource gbuffersEntitiesTrans;
    private final ProgramSource gbuffersEntitiesGlowing;
    private final ProgramSource gbuffersGlint;
    private final ProgramSource gbuffersEntityEyes;
    private final ProgramSource gbuffersBlock;
    private final ProgramSource gbuffersHand;

    private final ProgramSource[] deferred;
    private final ComputeSource[][] deferredCompute;

    private final ProgramSource gbuffersWater;
    private final ProgramSource gbuffersHandWater;

    private final ProgramSource[] composite;
    private final ComputeSource[][] compositeCompute;
    private final ProgramSource compositeFinal;
    private final ComputeSource[] finalCompute;

    public ProgramSet(ShaderPack pack, ShaderProperties properties) {
        this(AbsolutePackPath.fromAbsolutePath("/"), path -> null, properties, pack);
    }

    public ProgramSet(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider,
                      ShaderProperties shaderProperties, ShaderPack pack) {
        this.pack = pack;
        this.shaderProperties = shaderProperties == null ? ShaderProperties.empty() : shaderProperties;
        this.packDirectives = new PackDirectives(PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS, this.shaderProperties);

        this.shadow = readProgramSource(directory, sourceProvider, "shadow", BlendModeOverride.OFF);
        this.shadowCompute = readComputeArray(directory, sourceProvider, "shadow");

        this.shadowcomp = readProgramArray(directory, sourceProvider, "shadowcomp");
        this.shadowCompCompute = new ComputeSource[shadowcomp.length][];
        for (int i = 0; i < shadowcomp.length; i++) {
            String suffix = i == 0 ? "" : Integer.toString(i);
            this.shadowCompCompute[i] = readComputeArray(directory, sourceProvider, "shadowcomp" + suffix);
        }

        this.prepare = readProgramArray(directory, sourceProvider, "prepare");
        this.prepareCompute = new ComputeSource[prepare.length][];
        for (int i = 0; i < prepare.length; i++) {
            String suffix = i == 0 ? "" : Integer.toString(i);
            this.prepareCompute[i] = readComputeArray(directory, sourceProvider, "prepare" + suffix);
        }

        this.gbuffersBasic = readProgramSource(directory, sourceProvider, "gbuffers_basic");
        this.gbuffersLine = readProgramSource(directory, sourceProvider, "gbuffers_line");
        this.gbuffersBeaconBeam = readProgramSource(directory, sourceProvider, "gbuffers_beaconbeam");
        this.gbuffersTextured = readProgramSource(directory, sourceProvider, "gbuffers_textured");
        this.gbuffersTexturedLit = readProgramSource(directory, sourceProvider, "gbuffers_textured_lit");
        this.gbuffersTerrain = readProgramSource(directory, sourceProvider, "gbuffers_terrain");
        this.gbuffersDamagedBlock = readProgramSource(directory, sourceProvider, "gbuffers_damagedblock");
        this.gbuffersSkyBasic = readProgramSource(directory, sourceProvider, "gbuffers_skybasic");
        this.gbuffersSkyTextured = readProgramSource(directory, sourceProvider, "gbuffers_skytextured");
        this.gbuffersClouds = readProgramSource(directory, sourceProvider, "gbuffers_clouds");
        this.gbuffersWeather = readProgramSource(directory, sourceProvider, "gbuffers_weather");
        this.gbuffersEntities = readProgramSource(directory, sourceProvider, "gbuffers_entities");
        this.gbuffersEntitiesTrans = readProgramSource(directory, sourceProvider, "gbuffers_entities_translucent");
        this.gbuffersEntitiesGlowing = readProgramSource(directory, sourceProvider, "gbuffers_entities_glowing");
        this.gbuffersGlint = readProgramSource(directory, sourceProvider, "gbuffers_armor_glint");
        this.gbuffersEntityEyes = readProgramSource(directory, sourceProvider, "gbuffers_spidereyes");
        this.gbuffersBlock = readProgramSource(directory, sourceProvider, "gbuffers_block");
        this.gbuffersHand = readProgramSource(directory, sourceProvider, "gbuffers_hand");

        this.deferred = readProgramArray(directory, sourceProvider, "deferred");
        this.deferredCompute = new ComputeSource[deferred.length][];
        for (int i = 0; i < deferred.length; i++) {
            String suffix = i == 0 ? "" : Integer.toString(i);
            this.deferredCompute[i] = readComputeArray(directory, sourceProvider, "deferred" + suffix);
        }

        this.gbuffersWater = readProgramSource(directory, sourceProvider, "gbuffers_water");
        this.gbuffersHandWater = readProgramSource(directory, sourceProvider, "gbuffers_hand_water");

        this.composite = readProgramArray(directory, sourceProvider, "composite");
        this.compositeCompute = new ComputeSource[composite.length][];
        for (int i = 0; i < composite.length; i++) {
            String suffix = i == 0 ? "" : Integer.toString(i);
            this.compositeCompute[i] = readComputeArray(directory, sourceProvider, "composite" + suffix);
        }
        this.compositeFinal = readProgramSource(directory, sourceProvider, "final");
        this.finalCompute = readComputeArray(directory, sourceProvider, "final");

        locateDirectives();

        if (!gbuffersDamagedBlock.isValid()) {
            first(getGbuffersTerrain(), getGbuffersTexturedLit(), getGbuffersTextured(), getGbuffersBasic())
                .ifPresent(src -> this.gbuffersDamagedBlock = src.withDirectiveOverride(
                    src.getDirectives().withOverriddenDrawBuffers(new int[] {0})
                ));
        }
    }

    @SafeVarargs
    private static <T> Optional<T> first(Optional<T>... candidates) {
        for (Optional<T> candidate : candidates) {
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private ProgramSource[] readProgramArray(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, String name) {
        ProgramSource[] programs = new ProgramSource[PROGRAM_ARRAY_LENGTH];
        for (int i = 0; i < programs.length; i++) {
            String suffix = i == 0 ? "" : Integer.toString(i);
            programs[i] = readProgramSource(directory, sourceProvider, name + suffix);
        }
        return programs;
    }

    private ComputeSource[] readComputeArray(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, String name) {
        ComputeSource[] programs = new ComputeSource[COMPUTE_ARRAY_LENGTH];

        String basePath = name + ".csh";
        String source = sourceProvider.apply(directory.resolve(basePath));
        programs[0] = source == null ? null : new ComputeSource(name, source, this);

        for (char c = 'a'; c <= 'z'; ++c) {
            int index = c - 96;
            if (index >= programs.length) {
                break;
            }

            String suffix = name + "_" + c + ".csh";
            String compute = sourceProvider.apply(directory.resolve(suffix));

            if (compute == null) {
                break;
            }

            programs[index] = new ComputeSource(name + "_" + c, compute, this);
        }

        return programs;
    }

    private ProgramSource readProgramSource(AbsolutePackPath directory,
                                            Function<AbsolutePackPath, String> sourceProvider,
                                            String program) {
        return readProgramSource(directory, sourceProvider, program, null);
    }

    private ProgramSource readProgramSource(AbsolutePackPath directory,
                                            Function<AbsolutePackPath, String> sourceProvider,
                                            String program,
                                            BlendModeOverride defaultBlend) {
        String vertexSource = sourceProvider.apply(directory.resolve(program + ".vsh"));
        String geometrySource = sourceProvider.apply(directory.resolve(program + ".gsh"));
        String fragmentSource = sourceProvider.apply(directory.resolve(program + ".fsh"));

        ProgramSource source = new ProgramSource(program, vertexSource, geometrySource, fragmentSource,
            this, shaderProperties, defaultBlend);

        if (!source.isValid() && "gbuffers_damagedblock".equals(program)) {
            return fallbackDamagedBlock();
        }

        return source;
    }

    private ProgramSource fallbackDamagedBlock() {
        List<ProgramSource> candidates = Arrays.asList(
            gbuffersTerrain, gbuffersTexturedLit, gbuffersTextured, gbuffersBasic
        );

        for (ProgramSource candidate : candidates) {
            if (candidate != null && candidate.isValid()) {
                Oculus.LOGGER.debug("Falling back to {} for gbuffers_damagedblock", candidate.getName());
                return candidate;
            }
        }

        return ProgramSource.missing("gbuffers_damagedblock");
    }

    private void locateDirectives() {
        List<ProgramSource> programs = new ArrayList<>();
        List<ComputeSource> computes = new ArrayList<>();

        programs.add(shadow);
        programs.addAll(Arrays.asList(shadowcomp));
        programs.addAll(Arrays.asList(prepare));
        programs.addAll(Arrays.asList(
            gbuffersBasic, gbuffersBeaconBeam, gbuffersTextured, gbuffersTexturedLit, gbuffersTerrain,
            gbuffersDamagedBlock, gbuffersSkyBasic, gbuffersSkyTextured, gbuffersClouds, gbuffersWeather,
            gbuffersEntities, gbuffersEntitiesTrans, gbuffersEntitiesGlowing, gbuffersGlint,
            gbuffersEntityEyes, gbuffersBlock, gbuffersHand
        ));

        ComputeSourceCollector.collect(computes, compositeCompute);
        ComputeSourceCollector.collect(computes, deferredCompute);
        ComputeSourceCollector.collect(computes, prepareCompute);
        ComputeSourceCollector.collect(computes, shadowCompCompute);
        ComputeSourceCollector.collect(computes, shadowCompute);
        ComputeSourceCollector.collect(computes, finalCompute);

        for (ComputeSource compute : computes) {
            if (compute == null) {
                continue;
            }

            compute.getSource().map(ConstDirectiveParser::findDirectives).ifPresent(directives -> {
                for (ConstDirectiveParser.ConstDirective directive : directives) {
                    ConstDirectiveParser.ConstDirective.Type type = directive.getType();
                    if (type == ConstDirectiveParser.ConstDirective.Type.IVEC3 && "workGroups".equals(directive.getKey())) {
                        ComputeDirectiveParser.setComputeWorkGroups(compute, directive);
                    } else if (type == ConstDirectiveParser.ConstDirective.Type.VEC2 && "workGroupsRender".equals(directive.getKey())) {
                        ComputeDirectiveParser.setComputeWorkGroupsRelative(compute, directive);
                    }
                }
            });
        }

        programs.addAll(Arrays.asList(deferred));
        programs.add(gbuffersWater);
        programs.add(gbuffersHandWater);
        programs.addAll(Arrays.asList(composite));
        programs.add(compositeFinal);

        DispatchingDirectiveHolder packDirectiveHolder = new DispatchingDirectiveHolder();
        packDirectives.acceptDirectivesFrom(packDirectiveHolder);

        for (ProgramSource source : programs) {
            if (source == null) {
                continue;
            }

            source.getFragmentSource().map(ConstDirectiveParser::findDirectives).ifPresent(directives -> {
                for (ConstDirectiveParser.ConstDirective directive : directives) {
                    packDirectiveHolder.processDirective(directive);
                }
            });
        }

        packDirectives.getRenderTargetDirectives().getRenderTargetSettings().forEach((index, settings) ->
            Oculus.LOGGER.debug("Render target settings for colortex{}: {}", index, settings)
        );
    }

    public PackDirectives getPackDirectives() {
        return packDirectives;
    }

    public ShaderPack getPack() {
        return pack;
    }

    public ShaderProperties getShaderProperties() {
        return shaderProperties;
    }

    public Optional<ProgramSource> getShadow() {
        return shadow.requireValid();
    }

    public ProgramSource[] getShadowComposite() {
        return shadowcomp;
    }

    public ProgramSource[] getPrepare() {
        return prepare;
    }

    public Optional<ProgramSource> getGbuffersBasic() {
        return gbuffersBasic.requireValid();
    }

    public Optional<ProgramSource> getGbuffersLine() {
        return gbuffersLine.requireValid();
    }

    public Optional<ProgramSource> getGbuffersBeaconBeam() {
        return gbuffersBeaconBeam.requireValid();
    }

    public Optional<ProgramSource> getGbuffersTextured() {
        return gbuffersTextured.requireValid();
    }

    public Optional<ProgramSource> getGbuffersTexturedLit() {
        return gbuffersTexturedLit.requireValid();
    }

    public Optional<ProgramSource> getGbuffersTerrain() {
        return gbuffersTerrain.requireValid();
    }

    public Optional<ProgramSource> getGbuffersDamagedBlock() {
        return gbuffersDamagedBlock.requireValid();
    }

    public Optional<ProgramSource> getGbuffersSkyBasic() {
        return gbuffersSkyBasic.requireValid();
    }

    public Optional<ProgramSource> getGbuffersSkyTextured() {
        return gbuffersSkyTextured.requireValid();
    }

    public Optional<ProgramSource> getGbuffersClouds() {
        return gbuffersClouds.requireValid();
    }

    public Optional<ProgramSource> getGbuffersWeather() {
        return gbuffersWeather.requireValid();
    }

    public Optional<ProgramSource> getGbuffersEntities() {
        return gbuffersEntities.requireValid();
    }

    public Optional<ProgramSource> getGbuffersEntitiesTrans() {
        return gbuffersEntitiesTrans.requireValid();
    }

    public Optional<ProgramSource> getGbuffersEntitiesGlowing() {
        return gbuffersEntitiesGlowing.requireValid();
    }

    public Optional<ProgramSource> getGbuffersGlint() {
        return gbuffersGlint.requireValid();
    }

    public Optional<ProgramSource> getGbuffersEntityEyes() {
        return gbuffersEntityEyes.requireValid();
    }

    public Optional<ProgramSource> getGbuffersBlock() {
        return gbuffersBlock.requireValid();
    }

    public Optional<ProgramSource> getGbuffersHand() {
        return gbuffersHand.requireValid();
    }

    public ProgramSource[] getDeferred() {
        return deferred;
    }

    public Optional<ProgramSource> getGbuffersWater() {
        return gbuffersWater.requireValid();
    }

    public Optional<ProgramSource> getGbuffersHandWater() {
        return gbuffersHandWater.requireValid();
    }

    public ProgramSource[] getComposite() {
        return composite;
    }

    public Optional<ProgramSource> getCompositeFinal() {
        return compositeFinal.requireValid();
    }

    public ComputeSource[] getShadowCompute() {
        return shadowCompute;
    }

    public ComputeSource[][] getShadowCompCompute() {
        return shadowCompCompute;
    }

    public ComputeSource[][] getPrepareCompute() {
        return prepareCompute;
    }

    public ComputeSource[][] getDeferredCompute() {
        return deferredCompute;
    }

    public ComputeSource[][] getCompositeCompute() {
        return compositeCompute;
    }

    public ComputeSource[] getFinalCompute() {
        return finalCompute;
    }

    public Optional<ProgramSource> get(ProgramId programId) {
        switch (programId) {
            case Shadow: return getShadow();
            case Basic: return getGbuffersBasic();
            case Line: return getGbuffersLine();
            case Textured: return getGbuffersTextured();
            case TexturedLit: return getGbuffersTexturedLit();
            case SkyBasic: return getGbuffersSkyBasic();
            case SkyTextured: return getGbuffersSkyTextured();
            case Clouds: return getGbuffersClouds();
            case Terrain: return getGbuffersTerrain();
            case DamagedBlock: return getGbuffersDamagedBlock();
            case Block: return getGbuffersBlock();
            case BeaconBeam: return getGbuffersBeaconBeam();
            case Entities: return getGbuffersEntities();
            case EntitiesTrans: return getGbuffersEntitiesTrans();
            case EntitiesGlowing: return getGbuffersEntitiesGlowing();
            case ArmorGlint: return getGbuffersGlint();
            case SpiderEyes: return getGbuffersEntityEyes();
            case Hand: return getGbuffersHand();
            case Weather: return getGbuffersWeather();
            case Water: return getGbuffersWater();
            case HandWater: return getGbuffersHandWater();
            case Final: return getCompositeFinal();
            default: return Optional.empty();
        }
    }
}
