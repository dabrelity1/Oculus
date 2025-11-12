package net.oculus.shaderpack;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.loading.ProgramId;

/**
 * Skeleton port of the Iris {@code ProgramSet}. The heavy lifting performed by
 * the 1.16 implementation (parsing directives and compiling GLSL) is stripped
 * out, but the data layout and accessors are kept so downstream systems can be
 * brought over with minimal churn.
 */
public class ProgramSet implements ProgramSetInterface {
    private static final int PROGRAM_ARRAY_LENGTH = 99;
    private static final int COMPUTE_ARRAY_LENGTH = 27;

    private final PackDirectives packDirectives;
    private final ShaderPack pack;

    private final ProgramSource shadow;
    private final ComputeSource[] shadowCompute;

    private final ProgramSource[] shadowComposite;
    private final ComputeSource[][] shadowCompCompute;

    private final ProgramSource[] prepare;
    private final ComputeSource[][] prepareCompute;

    private final ProgramSource gbuffersBasic;
    private final ProgramSource gbuffersLine;
    private final ProgramSource gbuffersBeaconBeam;
    private final ProgramSource gbuffersTextured;
    private final ProgramSource gbuffersTexturedLit;
    private final ProgramSource gbuffersTerrain;
    private final ProgramSource gbuffersDamagedBlock;
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
        this.pack = Objects.requireNonNull(pack, "pack");
        this.packDirectives = new PackDirectives(shaderProperties);

        this.shadow = readProgramSource(directory, sourceProvider, "shadow");
        this.shadowCompute = readComputeArray(directory, sourceProvider, "shadow");

        this.shadowComposite = readProgramArray(directory, sourceProvider, "shadowcomp");
        this.shadowCompCompute = readComputeMatrix(directory, sourceProvider, "shadowcomp", shadowComposite.length);

        this.prepare = readProgramArray(directory, sourceProvider, "prepare");
        this.prepareCompute = readComputeMatrix(directory, sourceProvider, "prepare", prepare.length);

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
        this.deferredCompute = readComputeMatrix(directory, sourceProvider, "deferred", deferred.length);

        this.gbuffersWater = readProgramSource(directory, sourceProvider, "gbuffers_water");
        this.gbuffersHandWater = readProgramSource(directory, sourceProvider, "gbuffers_hand_water");

        this.composite = readProgramArray(directory, sourceProvider, "composite");
        this.compositeCompute = readComputeMatrix(directory, sourceProvider, "composite", composite.length);
        this.compositeFinal = readProgramSource(directory, sourceProvider, "final");
        this.finalCompute = readComputeArray(directory, sourceProvider, "final");

        locateDirectives();
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
        ComputeSource[] computes = new ComputeSource[COMPUTE_ARRAY_LENGTH];

        String base = sourceProvider.apply(directory.resolve(name + ".csh"));
        computes[0] = base == null ? null : ComputeSource.create(name, this, base);

        int index = 1;
        for (char c = 'a'; c <= 'z' && index < computes.length; c++) {
            String suffix = "_" + c;
            String source = sourceProvider.apply(directory.resolve(name + suffix + ".csh"));
            if (source == null) {
                break;
            }
            computes[index++] = ComputeSource.create(name + suffix, this, source);
        }

        return computes;
    }

    private ComputeSource[][] readComputeMatrix(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider,
                                                String name, int length) {
        ComputeSource[][] result = new ComputeSource[length][];
        for (int i = 0; i < length; i++) {
            String suffix = i == 0 ? "" : Integer.toString(i);
            result[i] = readComputeArray(directory, sourceProvider, name + suffix);
        }
        return result;
    }

    private ProgramSource readProgramSource(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, String program) {
        String vertex = sourceProvider.apply(directory.resolve(program + ".vsh"));
        String geometry = sourceProvider.apply(directory.resolve(program + ".gsh"));
        String fragment = sourceProvider.apply(directory.resolve(program + ".fsh"));

        ProgramSource source = ProgramSource.create(program, this, vertex, geometry, fragment);

        if (!source.isValid()) {
            if ("gbuffers_damagedblock".equals(program)) {
                return fallbackDamagedBlock();
            }
            return source;
        }

        return source;
    }

    private ProgramSource fallbackDamagedBlock() {
        for (ProgramSource candidate : Arrays.asList(gbuffersTerrain, gbuffersTexturedLit, gbuffersTextured, gbuffersBasic)) {
            if (candidate != null && candidate.isValid()) {
                return candidate;
            }
        }
        return ProgramSource.missing("gbuffers_damagedblock");
    }

    private void locateDirectives() {
        // Directive parsing is deferred until the full metadata pipeline is ported.
    }

    public PackDirectives getPackDirectives() {
        return packDirectives;
    }

    public ShaderPack getPack() {
        return pack;
    }

    public Optional<ProgramSource> getShadow() {
        return shadow.requireValid();
    }

    public ProgramSource[] getShadowComposite() {
        return shadowComposite;
    }

    public ProgramSource[] getPrepare() {
        return prepare;
    }

    public Optional<ProgramSource> getGbuffersTerrain() {
        return gbuffersTerrain.requireValid();
    }

    public Optional<ProgramSource> getGbuffersTextured() {
        return gbuffersTextured.requireValid();
    }

    public Optional<ProgramSource> getGbuffersTexturedLit() {
        return gbuffersTexturedLit.requireValid();
    }

    public Optional<ProgramSource> getGbuffersHand() {
        return gbuffersHand.requireValid();
    }

    public ProgramSource[] getDeferred() {
        return deferred;
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

    public ComputeSource[][] getShadowCompositeCompute() {
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
            case Shadow:
                return getShadow();
            case Basic:
                return gbuffersBasic.requireValid();
            case Line:
                return gbuffersLine.requireValid();
            case Textured:
                return gbuffersTextured.requireValid();
            case TexturedLit:
                return gbuffersTexturedLit.requireValid();
            case SkyBasic:
                return gbuffersSkyBasic.requireValid();
            case SkyTextured:
                return gbuffersSkyTextured.requireValid();
            case Clouds:
                return gbuffersClouds.requireValid();
            case Terrain:
                return gbuffersTerrain.requireValid();
            case DamagedBlock:
                return gbuffersDamagedBlock.requireValid();
            case Block:
                return gbuffersBlock.requireValid();
            case BeaconBeam:
                return gbuffersBeaconBeam.requireValid();
            case Entities:
                return gbuffersEntities.requireValid();
            case EntitiesTrans:
                return gbuffersEntitiesTrans.requireValid();
            case EntitiesGlowing:
                return gbuffersEntitiesGlowing.requireValid();
            case ArmorGlint:
                return gbuffersGlint.requireValid();
            case SpiderEyes:
                return gbuffersEntityEyes.requireValid();
            case Hand:
                return gbuffersHand.requireValid();
            case Weather:
                return gbuffersWeather.requireValid();
            case Water:
                return gbuffersWater.requireValid();
            case HandWater:
                return gbuffersHandWater.requireValid();
            case Final:
                return compositeFinal.requireValid();
            default:
                return Optional.empty();
        }
    }
}
