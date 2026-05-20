package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.oculus.blockrendering.BlockMaterialMapping;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.image.ImageLimits;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.gl.texture.PixelFormat;
import net.oculus.gl.texture.PixelType;
import net.oculus.pipeline.InputAvailability;
import net.oculus.pipeline.NamespacedId;
import net.oculus.pipeline.texture.CustomImageManager;
import net.oculus.shader.ShaderPreprocessor;
import net.oculus.shader.ShaderSourcePreparer;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.include.ShaderPackSourceNames;
import net.oculus.shaderpack.texture.CustomImageData;
import net.oculus.shaderpack.texture.CustomTextureData;
import net.oculus.shaderpack.texture.TextureStage;
import net.oculus.vendored.joml.Vector3i;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ShaderPackLoaderComplementaryTest {
    private static final String COMPLEMENTARY = "ComplementaryReimagined_r5.6.1";
    private static final String COMPLEMENTARY_ZIP = COMPLEMENTARY + ".zip";
    private static final String MAKEUP = "MakeUp-UltraFast-9.3e";
    private static final String MAKEUP_ZIP = MAKEUP + ".zip";
    private static final String DISABLE_GL_STRING_PROBES_PROPERTY = "oculus.disableGlStringProbes";
    private static final String UNIFORM_QUALIFIER =
        "\\b(?:readonly|writeonly|coherent|volatile|restrict|highp|mediump|lowp)\\b";
    private static final String UNIFORM_LAYOUT_QUALIFIER = "layout\\s*\\([^)]*\\)";
    private static final Pattern UNIFORM_DECLARATION = Pattern.compile(
        "(?:(?:" + UNIFORM_LAYOUT_QUALIFIER + "|" + UNIFORM_QUALIFIER + ")\\s+)*"
            + "uniform\\s+"
            + "(?:(?:" + UNIFORM_LAYOUT_QUALIFIER + "|" + UNIFORM_QUALIFIER + ")\\s+)*"
            + "([A-Za-z_][A-Za-z0-9_]*)\\s+([^;]+);");
    private static final Pattern IDENTIFIER = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)");
    private static String previousGlStringProbeSetting;
    private static String previousGlCapabilityProbeSetting;
    private static Field imageLimitsInstanceField;
    private static Object previousImageLimits;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void disableGlProbesForHeadlessLoaderTests() throws Exception {
        previousGlStringProbeSetting = System.getProperty(DISABLE_GL_STRING_PROBES_PROPERTY);
        previousGlCapabilityProbeSetting = System.getProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY);
        System.setProperty(DISABLE_GL_STRING_PROBES_PROPERTY, "true");
        System.setProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, "true");
        seedImageLimitsForTargetPackSourcePreprocessing();
    }

    @AfterClass
    public static void restoreGlProbeSettings() throws Exception {
        restoreImageLimits();
        restoreProperty(DISABLE_GL_STRING_PROBES_PROPERTY, previousGlStringProbeSetting);
        restoreProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, previousGlCapabilityProbeSetting);
    }

    @Test
    public void blankSelectionReturnsInternalPackWithoutMinecraftSingleton() throws Exception {
        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(Paths.get("not-used"), " ");

        assertTrue(pack.isInternal());
        assertFalse(pack.getProgramSet(NamespacedId.overworld()).getGbuffersTerrain().isPresent());
    }

    @Test
    public void loadsComplementaryDirectoryWithDimensionsAndPackMetadata() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());

        assertComplementaryMetadata(pack, COMPLEMENTARY);
    }

    @Test
    public void loadsComplementaryZipWithDimensionsPackMetadataAndDeferredSources() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryZipExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY_ZIP, complementaryOverrides());

        assertComplementaryMetadata(pack, COMPLEMENTARY_ZIP);
    }

    @Test
    public void complementaryEntityPropertiesMapPlayerToShaderEntityId() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());

        assertEquals(50016, pack.getIdMap().getEntityIdMap().getInt(
            new net.oculus.shaderpack.materialmap.NamespacedId("minecraft", "player")));
        assertEquals(50016, pack.getIdMap().getEntityIdMap().getInt(
            new net.oculus.shaderpack.materialmap.NamespacedId("minecraft", "mannequin")));
    }

    @Test
    public void complementaryEntityAndHandFragmentsSkipModernIrisItemMaterialBranch() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());
        ProgramSet overworld = pack.getProgramSet(NamespacedId.overworld());

        String entities = overworld.getGbuffersEntities().get().getFragmentSource().get();
        String hand = overworld.getGbuffersHand().get().getFragmentSource().get();
        String compactEntities = entities.replaceAll("\\s+", "");
        String compactHand = hand.replaceAll("\\s+", "");

        assertTrue("Complementary entities must keep the entityId player material branch",
            compactEntities.contains("entityId==50016"));
        assertFalse("1.12 entity rendering must not enter Complementary's modern Iris item material branch",
            compactEntities.contains("intmat=currentRenderedItemId"));
        assertFalse("1.12 entity rendering must not enter Complementary's modern Iris item material conditions",
            compactEntities.contains("currentRenderedItemId<"));
        assertFalse("1.12 hand rendering must not enter Complementary's modern Iris item material branch",
            compactHand.contains("intmat=currentRenderedItemId"));
        assertFalse("1.12 hand rendering must not enter Complementary's modern Iris item material conditions",
            compactHand.contains("currentRenderedItemId<"));
    }

    @Test
    public void complementaryColoredLightingEnablesComputeOnlyShadowComposite() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);

        Map<String, String> overrides = complementaryOverrides();
        overrides.put("SHADOW_QUALITY", "0");
        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, overrides);
        ProgramSet overworld = pack.getProgramSet(NamespacedId.overworld());
        ComputeSource shadowCompositeCompute = overworld.getShadowCompCompute()[0][0];

        assertFalse("Complementary target profile should not expose raster shadowcomp sources",
            overworld.getShadowComposite()[0].requireValid().isPresent());
        assertNotNull("Complementary colored lighting should expose shadowcomp.csh", shadowCompositeCompute);
        assertTrue(shadowCompositeCompute.isValid());
        assertEquals("shadowcomp", shadowCompositeCompute.getName());
        assertTrue(shadowCompositeCompute.getSource().isPresent());
        assertNull("Complementary shadowcomp uses absolute work groups, not relative framebuffer groups",
            shadowCompositeCompute.getWorkGroupRelative());

        Vector3i workGroups = shadowCompositeCompute.getWorkGroups();
        assertNotNull("Complementary COLORED_LIGHTING=128 must parse absolute shadowcomp work groups", workGroups);
        assertEquals(16, workGroups.x());
        assertEquals(8, workGroups.y());
        assertEquals(16, workGroups.z());

        String source = shadowCompositeCompute.getSource().get();
        assertTrue(source.contains("writeonly uniform image3D floodfill_img;"));
        assertTrue(source.contains("writeonly uniform image3D floodfill_img_copy;"));
        String compactSource = source.replaceAll("\\s+", "");
        assertTrue(compactSource.contains("floodfill_sampler"));
        assertTrue(compactSource.contains("floodfill_sampler_copy"));
        assertTrue(compactSource.contains("previousPos"));
        assertTrue(source.contains("imageStore(floodfill_img_copy"));
        assertTrue(source.contains("imageStore(floodfill_img"));

        assertComplementaryVolumeImage(pack, "voxel_img", "voxel_sampler",
            PixelFormat.RED_INTEGER, InternalTextureFormat.R8UI, PixelType.UNSIGNED_INT, true);
        assertComplementaryVolumeImage(pack, "floodfill_img", "floodfill_sampler",
            PixelFormat.RGBA, InternalTextureFormat.RGBA16F, PixelType.HALF_FLOAT, false);
        assertComplementaryVolumeImage(pack, "floodfill_img_copy", "floodfill_sampler_copy",
            PixelFormat.RGBA, InternalTextureFormat.RGBA16F, PixelType.HALF_FLOAT, false);
        assertComplementaryImage(pack, "wsr_img", "wsr_sampler",
            PixelFormat.RED_INTEGER, InternalTextureFormat.R16UI, PixelType.UNSIGNED_INT, true,
            "COLORED_LIGHTING", "64", "COLORED_LIGHTING");
        assertComplementaryImage(pack, "wsr_img_lod", "wsr_sampler_lod",
            PixelFormat.RED_INTEGER, InternalTextureFormat.R8UI, PixelType.UNSIGNED_INT, true,
            "32", "16", "32");
        assertCustomImageManagerResolvesDimensions(pack, "wsr_img", 128, 64, 128);
        assertCustomImageManagerResolvesDimensions(pack, "wsr_img_lod", 32, 16, 32);
        assertEquals(Long.valueOf(100663296L), pack.getProperties().getShaderStorageBufferSizes().get(0));
    }

    @Test
    public void complementaryRainPuddleOptionEnablesTargetPackCustomImage() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);

        Map<String, String> overrides = complementaryOverrides();
        overrides.put("RAIN_PUDDLES", "1");
        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, overrides);

        assertComplementaryImage(pack, "puddle_img", "puddle_sampler",
            PixelFormat.RED_INTEGER, InternalTextureFormat.R8UI, PixelType.UNSIGNED_INT, true,
            "128", "128", null);
        assertCustomImageManagerResolvesDimensions(pack, "puddle_img", 128, 128, null);
    }

    @Test
    public void complementaryEntitiesOverlayVariantUsesCapturedEntityColorPassthrough() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());
        ProgramSource entities = pack.getProgramSet(NamespacedId.overworld()).getGbuffersEntities().get();

        ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
            pack.getName(),
            entities.getName(),
            entities.getVertexSource().get(),
            entities.getGeometrySource().orElse(null),
            entities.getFragmentSource().get(),
            Collections.emptyList(),
            new InputAvailability(true, true, true));

        assertTrue(prepared.getVertexSource().contains("uniform vec4 iris_entityColor;"));
        assertTrue(prepared.getVertexSource().contains("varying vec4 entityColor;"));
        assertTrue(prepared.getVertexSource().contains("entityColor = iris_entityColor;"));
        assertFalse(prepared.getVertexSource().contains("texture2D(iris_overlay"));
        assertFalse(prepared.getVertexSource().contains("uniform vec4 entityColor;"));
        assertTrue(prepared.getFragmentSource().contains("varying vec4 entityColor;"));
        assertTrue(prepared.getFragmentSource().contains("mix(color.rgb, entityColor.rgb, entityColor.a)"));
        assertFalse(prepared.getFragmentSource().contains("uniform vec4 entityColor;"));
    }

    @Test
    public void complementarySnowLayerRowsResolve112LayerStatesAfterPreprocessing() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);
        Bootstrap.register();

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());
        Object2IntMap<IBlockState> blockStateIds =
            BlockMaterialMapping.createBlockStateIdMap(pack.getIdMap().getBlockPropertiesMap());

        assertEquals(10380, blockStateIds.getInt(Blocks.SNOW.getDefaultState()));
        assertEquals(10953, blockStateIds.getInt(snowLayerState(1)));
        assertEquals(10953, blockStateIds.getInt(snowLayerState(4)));
        assertEquals(10953, blockStateIds.getInt(snowLayerState(7)));
        assertEquals(10380, blockStateIds.getInt(snowLayerState(8)));
    }

    @Test
    public void complementaryDaylightDetectorRowCovers112InvertedSplitBlock() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);
        Bootstrap.register();

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());
        Object2IntMap<IBlockState> blockStateIds =
            BlockMaterialMapping.createBlockStateIdMap(pack.getIdMap().getBlockPropertiesMap());

        assertEquals(10121, blockStateIds.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR, 0)));
        assertEquals(10121, blockStateIds.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR, 12)));
        assertEquals(10121, blockStateIds.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR_INVERTED, 0)));
        assertEquals(10121, blockStateIds.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR_INVERTED, 12)));
    }

    @Test
    public void complementaryComparatorRowsCover112SplitModes() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);
        Bootstrap.register();

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());
        Object2IntMap<IBlockState> blockStateIds =
            BlockMaterialMapping.createBlockStateIdMap(pack.getIdMap().getBlockPropertiesMap());

        assertEquals(10644, blockStateIds.getInt(comparatorState(Blocks.POWERED_COMPARATOR, "compare")));
        assertEquals(10644, blockStateIds.getInt(comparatorState(Blocks.POWERED_COMPARATOR, "subtract")));
        assertEquals(10645, blockStateIds.getInt(comparatorState(Blocks.UNPOWERED_COMPARATOR, "compare")));
        assertEquals(10646, blockStateIds.getInt(comparatorState(Blocks.UNPOWERED_COMPARATOR, "subtract")));
    }

    @Test
    public void complementaryQuartzPillarRowCovers112AxisVariants() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);
        Bootstrap.register();

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());
        Object2IntMap<IBlockState> blockStateIds =
            BlockMaterialMapping.createBlockStateIdMap(pack.getIdMap().getBlockPropertiesMap());

        assertEquals(10364, blockStateIds.getInt(quartzState("default")));
        assertEquals(10364, blockStateIds.getInt(quartzState("chiseled")));
        assertEquals(10364, blockStateIds.getInt(quartzState("lines_x")));
        assertEquals(10364, blockStateIds.getInt(quartzState("lines_y")));
        assertEquals(10364, blockStateIds.getInt(quartzState("lines_z")));
    }

    @Test
    public void complementaryRedSandstoneRowCovers112SharedSlabAndStairStates() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);
        Bootstrap.register();

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());
        Object2IntMap<IBlockState> blockStateIds =
            BlockMaterialMapping.createBlockStateIdMap(pack.getIdMap().getBlockPropertiesMap());

        assertTrue(pack.getIdMap().getBlockPropertiesMap().get(10247).contains(
            net.oculus.shaderpack.materialmap.BlockEntry.parse("cut_red_sandstone_slab")));
        assertTrue(pack.getIdMap().getBlockPropertiesMap().get(10247).contains(
            net.oculus.shaderpack.materialmap.BlockEntry.parse("smooth_red_sandstone_slab")));
        assertTrue(pack.getIdMap().getBlockPropertiesMap().get(10247).contains(
            net.oculus.shaderpack.materialmap.BlockEntry.parse("smooth_red_sandstone_stairs")));
        assertEquals(10247, blockStateIds.getInt(redSandstoneSlabState()));
        assertEquals(10247, blockStateIds.getInt(Blocks.RED_SANDSTONE_STAIRS.getDefaultState()));
    }

    @Test
    public void complementaryTileEntityFamilyRowsCover112GenericBlocks() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);
        Bootstrap.register();

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());
        Object2IntMap<IBlockState> blockStateIds =
            BlockMaterialMapping.createBlockStateIdMap(pack.getIdMap().getBlockPropertiesMap());

        assertEquals(5004, blockStateIds.getInt(Blocks.STANDING_SIGN.getDefaultState()));
        assertEquals(5004, blockStateIds.getInt(Blocks.WALL_SIGN.getDefaultState()));
        assertEquals(5016, blockStateIds.getInt(Blocks.SKULL.getDefaultState()));
        assertEquals(5016, blockStateIds.getInt(Blocks.STANDING_BANNER.getDefaultState()));
        assertEquals(5016, blockStateIds.getInt(Blocks.WALL_BANNER.getDefaultState()));
        assertEquals(5016, blockStateIds.getInt(Blocks.BED.getDefaultState()));
        assertEquals(10153, blockStateIds.getInt(Blocks.PISTON_HEAD.getDefaultState()));
    }

    @Test
    public void complementaryWaterPreparesAllInputAvailabilityVariantsWithTextureMatrixShim() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());
        ProgramSet programSet = pack.getProgramSet(NamespacedId.overworld());
        ProgramSource water = programSet.getGbuffersWater().get();
        List<StringPair> environmentDefines = ShaderPreprocessor.createEnvironmentDefines(programSet);

        for (int packed = 0; packed < InputAvailability.NUM_VALUES; packed++) {
            InputAvailability availability = InputAvailability.unpack(packed);
            ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
                pack.getName(),
                water.getName(),
                water.getVertexSource().get(),
                water.getGeometrySource().orElse(null),
                water.getFragmentSource().get(),
                environmentDefines,
                availability);

            assertPreparedStage(water.getName(), "vertex", prepared.getVertexSource());
            assertPreparedStage(water.getName(), "fragment", prepared.getFragmentSource());
            assertTrue("Complementary water vertex variant missing iris texture matrix shim for " + availability,
                prepared.getVertexSource().contains("mat4 iris_TextureMatrix[8] = mat4[8]("));
            if (availability.lightmap) {
                assertTrue(prepared.getVertexSource().contains("mat4 iris_LightmapTextureMatrix = gl_TextureMatrix[1];"));
            } else {
                assertTrue(prepared.getVertexSource().contains("mat4 iris_LightmapTextureMatrix = mat4("));
            }
        }
    }

    @Test
    public void preparesComplementaryRepresentativeRuntimeProgramSourcesAcrossDimensions() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, COMPLEMENTARY, complementaryOverrides());

        int prepared = 0;
        prepared += assertRepresentativeProgramSetPrepares(pack.getProgramSet(NamespacedId.overworld()));
        prepared += assertRepresentativeProgramSetPrepares(pack.getProgramSet(NamespacedId.nether()));
        prepared += assertRepresentativeProgramSetPrepares(pack.getProgramSet(NamespacedId.end()));
        prepared += assertRepresentativeProgramSetPrepares(pack.getProgramSet(NamespacedId.of("example", "custom")));

        assertTrue("Expected Complementary to prepare representative real runtime program sources", prepared >= 10);
    }

    @Test
    public void targetPackModernDhAndEndFlashUniformsStayOutOf112RuntimeSources() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);
        assumeMakeUpExists(shaderpacks);

        assertTrue(read("run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/lib/uniforms.glsl")
            .contains("uniform float endFlashIntensity;"));
        assertTrue(read("run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/lib/uniforms.glsl")
            .contains("uniform sampler2D dhDepthTex;"));
        assertTrue(read("run/shaderpacks/MakeUp-UltraFast-9.3e/shaders/common/solid_blocks_vertex.glsl")
            .contains("uniform float endFlashIntensity;"));
        assertTrue(read("run/shaderpacks/MakeUp-UltraFast-9.3e/shaders/common/deferred_fragment.glsl")
            .contains("uniform sampler2D dhDepthTex0;"));
        assertFalse("Distant Horizons standalone starts are not part of the 1.16.5 runtime source graph",
            ShaderPackSourceNames.POTENTIAL_STARTS.contains("dh_terrain.vsh"));
        assertFalse("Distant Horizons standalone starts are not part of the 1.16.5 runtime source graph",
            ShaderPackSourceNames.POTENTIAL_STARTS.contains("dh_water.fsh"));

        ShaderPack complementary = ShaderPackLoader.loadFromShaderpacksDirectory(
            shaderpacks, COMPLEMENTARY, complementaryOverrides());
        ShaderPack makeUp = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, MAKEUP);

        assertNoRuntimeSourceContains(complementary,
            "endFlashIntensity",
            "previousEndFlashIntensity",
            "endFlashPosition",
            "dhDepthTex",
            "dhDepthTex0",
            "dhDepthTex1",
            "dhProjection",
            "dhProjectionInverse",
            "dhNearPlane",
            "dhFarPlane",
            "dhRenderDistance",
            "dhMaterialId");
        assertNoRuntimeSourceContains(makeUp,
            "endFlashIntensity",
            "endFlashPosition",
            "dhDepthTex",
            "dhDepthTex0",
            "dhDepthTex1",
            "dhProjection",
            "dhProjectionInverse",
            "dhNearPlane",
            "dhFarPlane",
            "dhRenderDistance",
            "dhMaterialId");
    }

    @Test
    public void targetPackRuntimeNonSamplerUniformDeclarationsStayBoundOrCustom() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);
        assumeMakeUpExists(shaderpacks);

        ShaderPack complementary = ShaderPackLoader.loadFromShaderpacksDirectory(
            shaderpacks, COMPLEMENTARY, complementaryOverrides());
        ShaderPack makeUp = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, MAKEUP);

        assertRuntimeNonSamplerUniformsCovered(complementary);
        assertRuntimeNonSamplerUniformsCovered(makeUp);
    }

    @Test
    public void targetPackRuntimeSamplerAndImageDeclarationsStayKnownToBindingSurface() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryExists(shaderpacks);
        assumeMakeUpExists(shaderpacks);

        ShaderPack complementary = ShaderPackLoader.loadFromShaderpacksDirectory(
            shaderpacks, COMPLEMENTARY, complementaryOverrides());
        ShaderPack makeUp = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, MAKEUP);

        assertRuntimeSamplerAndImageUniformsCovered(complementary);
        assertRuntimeSamplerAndImageUniformsCovered(makeUp);
    }

    @Test
    public void loadsMakeUpDirectoryWithDimensionsOptionsAndCustomTextures() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeMakeUpExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, MAKEUP);

        assertMakeUpMetadata(pack, MAKEUP);
    }

    @Test
    public void makeUpOptionOverrideChangesConditionalCustomTextureDirectives() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeMakeUpExists(shaderpacks);

        Map<String, String> overrides = new HashMap<>();
        overrides.put("CLOUD_VOL_STYLE", "1");
        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, MAKEUP, overrides);

        assertMakeUpConditionalCloudTexture(pack, "textures/clouds_blocky_512_R_8bit.png");
    }

    @Test
    public void loadsMakeUpZipWithDimensionsOptionsAndProgramSources() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeMakeUpZipExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, MAKEUP_ZIP);

        assertMakeUpMetadata(pack, MAKEUP_ZIP);
    }

    @Test
    public void preparesMakeUpPrepareFragmentWithoutDuplicateShiftedRDitherInLegacyBranch() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeMakeUpExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, MAKEUP);
        ProgramSet programSet = pack.getProgramSet(NamespacedId.overworld());
        ProgramSource prepare = programSet.getPrepare()[0];
        assertTrue(prepare.isValid());

        ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
            pack.getName(),
            prepare.getName(),
            prepare.getVertexSource().get(),
            prepare.getGeometrySource().orElse(null),
            prepare.getFragmentSource().get(),
            ShaderPreprocessor.createEnvironmentDefines(programSet),
            null);

        assertPreparedStage(prepare.getName(), "fragment", prepared.getFragmentSource());
        assertEquals(1, countOccurrences(prepared.getFragmentSource(), "float shifted_r_dither(vec2 frag)"));
        assertEquals(1, countOccurrences(prepared.getFragmentSource(),
            "return fract((frame_mod * 0.4) + dot(frag, vec2(0.75487766624669276, 0.569840290998)));"));
        assertTrue(prepared.getFragmentSource().contains("float dither = shifted_r_dither(gl_FragCoord.xy);"));
    }

    @Test
    public void preparesMakeUpCompositeFragmentWithLegacyShiftedDither17Fallback() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeMakeUpExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, MAKEUP);
        ProgramSet programSet = pack.getProgramSet(NamespacedId.overworld());
        ProgramSource composite = programSet.getComposite()[0];
        assertTrue(composite.isValid());

        ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
            pack.getName(),
            composite.getName(),
            composite.getVertexSource().get(),
            composite.getGeometrySource().orElse(null),
            composite.getFragmentSource().get(),
            ShaderPreprocessor.createEnvironmentDefines(programSet),
            null);

        assertPreparedStage(composite.getName(), "fragment", prepared.getFragmentSource());
        assertEquals(1, countOccurrences(prepared.getFragmentSource(), "float shifted_dither17(vec2 pos)"));
        assertTrue(prepared.getFragmentSource().contains(
            "return fract((frame_mod * 0.4) + dot(pos, vec2(0.11764705882352941, 0.4117647058823529)));"));
        assertTrue(prepared.getFragmentSource().contains("float dither = shifted_dither17(gl_FragCoord.xy);"));
    }

    @Test
    public void preparesComplementaryRuntimeCompositeWithSsboCompatibleVersion() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeComplementaryZipExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(
            shaderpacks,
            COMPLEMENTARY_ZIP,
            complementaryRuntimeOverrides());
        ProgramSet programSet = pack.getProgramSet(NamespacedId.overworld());
        ProgramSource composite = programSet.getComposite()[0];
        assertTrue(composite.isValid());

        ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
            pack.getName(),
            composite.getName(),
            composite.getVertexSource().get(),
            composite.getGeometrySource().orElse(null),
            composite.getFragmentSource().get(),
            ShaderPreprocessor.createEnvironmentDefines(programSet),
            null);

        String fragment = prepared.getFragmentSource();
        assertPreparedStage(composite.getName(), "fragment", fragment);
        assertTrue("Complementary runtime WSR composite needs GLSL 4.30 compatibility for SSBO syntax",
            fragment.startsWith("#version 430 compatibility\n"));
        assertTrue(fragment.contains("layout(std430, binding = 0) readonly buffer blockDataSSBO"));
    }

    @Test
    public void preparesMakeUpDirectoryRuntimeProgramSourcesAcrossDimensions() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeMakeUpExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, MAKEUP);

        assertMakeUpProgramSourcesPrepare(pack);
    }

    @Test
    public void preparesMakeUpZipRuntimeProgramSourcesAfterLoadReturns() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        assumeMakeUpZipExists(shaderpacks);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, MAKEUP_ZIP);

        assertMakeUpProgramSourcesPrepare(pack);
    }

    @Test
    public void loadsNestedZipShaderRootLikeReferenceLoader() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("shaderpacks").toPath();
        String packName = "NestedPack.zip";
        writeNestedShaderPackZip(shaderpacks.resolve(packName));

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, packName);

        assertFalse(pack.isInternal());
        assertEquals(packName, pack.getName());
        assertEquals("Nested Value", pack.getLanguageMap().get("en_us", "option.NESTED"));
        assertEquals(7, pack.getIdMap().getItemIdMap().getInt(
            new net.oculus.shaderpack.materialmap.NamespacedId("minecraft", "stick")));
        assertTrue(pack.getCustomNoiseTexture().isPresent());
        assertTrue(pack.getCustomNoiseTexture().get() instanceof CustomTextureData.PngData);
        assertEquals(4, ((CustomTextureData.PngData) pack.getCustomNoiseTexture().get()).getContent().length);

        ProgramSet overworld = pack.getProgramSet(NamespacedId.overworld());
        assertEquals("/world0", overworld.getProgramRoot().getPathString());
        assertTrue(overworld.getPackDirectives().shouldUseSeparateAo());
        assertTrue(overworld.getGbuffersTerrain().isPresent());
        assertTrue(overworld.getGbuffersTerrain().get().getVertexSource().isPresent());
        assertTrue(overworld.getGbuffersTerrain().get().getFragmentSource().isPresent());
    }

    @Test
    public void rejectsDirectoryPackWithoutDirectShadersFolderLikeReferenceLoader() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("invalid-shaderpacks").toPath();
        String packName = "RootMetadataPack";
        Path packRoot = shaderpacks.resolve(packName);
        Files.createDirectories(packRoot);
        Files.write(packRoot.resolve("shaders.properties"),
            java.util.Collections.singletonList("separateAo=true"),
            StandardCharsets.ISO_8859_1);

        try {
            ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, packName);
        } catch (IOException exception) {
            assertTrue(exception.getMessage().contains("does not contain a shaders directory"));
            return;
        }

        throw new AssertionError("Directory pack without a direct shaders folder should be rejected");
    }

    @Test
    public void directoryPackIgnoresRootLevelShadersPropertiesLikeReferenceLoader() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("root-properties-ignored").toPath();
        String packName = "RootPropertiesIgnored";
        Path packRoot = shaderpacks.resolve(packName);
        Path shaderRoot = packRoot.resolve("shaders");
        writeMinimalProgram(shaderRoot);
        Files.write(packRoot.resolve("shaders.properties"),
            Collections.singletonList("separateAo=true"),
            StandardCharsets.ISO_8859_1);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, packName);

        assertFalse(pack.getProgramSet(NamespacedId.overworld()).getPackDirectives().shouldUseSeparateAo());
        assertTrue(pack.getProgramSet(NamespacedId.overworld()).getGbuffersTerrain().isPresent());
    }

    @Test
    public void dimensionPropertiesWithoutWildcardKeepsRootBaseLikeReferenceLoader() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("dimension-no-wildcard").toPath();
        String packName = "NoWildcardWorld0";
        Path shaderRoot = shaderpacks.resolve(packName).resolve("shaders");
        writeMinimalProgram(shaderRoot);
        writeMinimalProgram(shaderRoot.resolve("world0"));
        Files.write(shaderRoot.resolve("dimension.properties"),
            Collections.singletonList("dimension.world-1=minecraft:the_nether"),
            StandardCharsets.ISO_8859_1);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, packName);

        ProgramSet overworld = pack.getProgramSet(NamespacedId.overworld());
        assertEquals("/", overworld.getProgramRoot().getPathString());
        assertTrue(overworld.getGbuffersTerrain().isPresent());
    }

    @Test
    public void declaredDimensionFolderWithoutProgramStartsFallsBackToBaseLikeReferenceLoader() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("dimension-empty-override").toPath();
        String packName = "EmptyNetherOverride";
        Path shaderRoot = shaderpacks.resolve(packName).resolve("shaders");
        writeMinimalProgram(shaderRoot.resolve("world0"));
        Files.createDirectories(shaderRoot.resolve("world-1"));
        Files.write(shaderRoot.resolve("dimension.properties"),
            Arrays.asList(
                "dimension.world0=*",
                "dimension.world-1=minecraft:the_nether"
            ),
            StandardCharsets.ISO_8859_1);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, packName);

        ProgramSet nether = pack.getProgramSet(NamespacedId.nether());
        assertEquals("/world0", nether.getProgramRoot().getPathString());
        assertTrue(nether.getGbuffersTerrain().isPresent());
    }

    @Test
    public void wildcardOnlyDimensionMappingUsesBaseProgramSetLikeReferenceLoader() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("dimension-wildcard-base").toPath();
        String packName = "WildcardBase";
        Path shaderRoot = shaderpacks.resolve(packName).resolve("shaders");
        writeMinimalProgram(shaderRoot.resolve("world0"));
        Files.write(shaderRoot.resolve("dimension.properties"),
            Collections.singletonList("dimension.world0=*"),
            StandardCharsets.ISO_8859_1);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, packName);

        ProgramSet overworld = pack.getProgramSet(NamespacedId.overworld());
        ProgramSet unknown = pack.getProgramSet(NamespacedId.of("example", "custom"));
        assertEquals("/world0", overworld.getProgramRoot().getPathString());
        assertSame(overworld, unknown);
    }

    @Test
    public void dimensionPropertiesDoNotSeeShaderOptionMacrosLikeReferenceLoader() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("dimension-no-options").toPath();
        String packName = "DimensionNoOptions";
        Path shaderRoot = shaderpacks.resolve(packName).resolve("shaders");
        writeMinimalProgram(shaderRoot, "#define USE_WORLD0 1 //[0 1]\n");
        writeMinimalProgram(shaderRoot.resolve("world0"));
        Files.write(shaderRoot.resolve("dimension.properties"),
            Arrays.asList(
                "dimension.world-1=minecraft:the_nether",
                "#if USE_WORLD0 == 1",
                "dimension.world0=*",
                "#endif"
            ),
            StandardCharsets.ISO_8859_1);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, packName);

        ProgramSet overworld = pack.getProgramSet(NamespacedId.overworld());
        assertEquals("/", overworld.getProgramRoot().getPathString());
        assertTrue(overworld.getGbuffersTerrain().isPresent());
    }

    @Test
    public void sourcePreprocessingUsesProgramAndStageDefinesBeforeDirectiveScan() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("program-stage-source-defines").toPath();
        String packName = "ProgramStageDefines";
        Path shaderRoot = shaderpacks.resolve(packName).resolve("shaders");
        Files.createDirectories(shaderRoot);
        Files.write(shaderRoot.resolve("shadowcomp.csh"), Arrays.asList(
            "#version 430",
            "#ifdef SHADOWCOMP",
            "#ifdef COMPUTE_SHADER",
            "const ivec3 workGroups = ivec3(3, 4, 5);",
            "const int keptByProgramAndStageDefines = 1;",
            "#endif",
            "#endif",
            "#ifdef GBuffers",
            "const ivec3 workGroups = ivec3(99, 99, 99);",
            "#endif"
        ), StandardCharsets.UTF_8);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, packName);
        ComputeSource compute = pack.getProgramSet(NamespacedId.overworld()).getShadowCompCompute()[0][0];

        assertNotNull(compute);
        assertNotNull(compute.getWorkGroups());
        assertEquals(3, compute.getWorkGroups().x());
        assertEquals(4, compute.getWorkGroups().y());
        assertEquals(5, compute.getWorkGroups().z());
        assertTrue(compute.getSource().get().contains("keptByProgramAndStageDefines"));
        assertFalse(compute.getSource().get().contains("99, 99, 99"));
    }

    @Test
    public void emptyDimensionValueTokenIsPreservedLikeReferenceParser() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("dimension-empty-token").toPath();
        String packName = "EmptyDimensionToken";
        Path shaderRoot = shaderpacks.resolve(packName).resolve("shaders");
        writeMinimalProgram(shaderRoot.resolve("empty"));
        Files.write(shaderRoot.resolve("dimension.properties"),
            Collections.singletonList("dimension.empty="),
            StandardCharsets.ISO_8859_1);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, packName);

        ProgramSet empty = pack.getProgramSet(new NamespacedId(""));
        assertEquals("/empty", empty.getProgramRoot().getPathString());
        assertTrue(empty.getGbuffersTerrain().isPresent());
    }

    @Test
    public void includeGraphStartsFromReferenceProgramSourcesNotEveryShaderLikeFile() throws Exception {
        Path shaderpacks = temporaryFolder.newFolder("source-starts").toPath();
        String packName = "SourceStarts";
        Path shaderRoot = shaderpacks.resolve(packName).resolve("shaders");
        Files.createDirectories(shaderRoot);
        Files.write(shaderRoot.resolve("gbuffers_terrain.vsh"), Arrays.asList(
            "#version 120",
            "#include \"/included.glsl\"",
            "void main() { gl_Position = gl_Vertex; }"
        ), StandardCharsets.UTF_8);
        Files.write(shaderRoot.resolve("gbuffers_terrain.fsh"),
            Collections.singletonList("#version 120\nvoid main() { gl_FragData[0] = vec4(1.0); }\n"),
            StandardCharsets.UTF_8);
        Files.write(shaderRoot.resolve("included.glsl"), Arrays.asList(
            "#define INCLUDED_OPTION // Included option",
            "#ifdef INCLUDED_OPTION",
            "#endif"
        ), StandardCharsets.UTF_8);
        Files.write(shaderRoot.resolve("unused.glsl"), Arrays.asList(
            "#define ORPHAN_OPTION // Orphan option",
            "#ifdef ORPHAN_OPTION",
            "#endif"
        ), StandardCharsets.UTF_8);

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, packName);

        assertTrue(pack.getOptionValues().getOptionSet().getBooleanOptions().containsKey("INCLUDED_OPTION"));
        assertFalse(pack.getOptionValues().getOptionSet().getBooleanOptions().containsKey("ORPHAN_OPTION"));
        assertTrue(pack.getProgramSet(NamespacedId.overworld()).getGbuffersTerrain().isPresent());
        assertFalse(pack.getProgramSet(NamespacedId.overworld()).getGbuffersTerrain().get().getVertexSource().get()
            .contains("#include"));
        assertNull(pack.getSourceProvider().apply(AbsolutePackPath.fromAbsolutePath("/unused.glsl")));
    }

    private static Map<String, String> complementaryOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("COLORED_LIGHTING", "128");
        overrides.put("WORLD_SPACE_REFLECTIONS", "1");
        overrides.put("SHADOW_QUALITY", "-1");
        return overrides;
    }

    private static void seedImageLimitsForTargetPackSourcePreprocessing() throws Exception {
        imageLimitsInstanceField = ImageLimits.class.getDeclaredField("instance");
        imageLimitsInstanceField.setAccessible(true);
        previousImageLimits = imageLimitsInstanceField.get(null);
        imageLimitsInstanceField.set(null, null);

        Method get = ImageLimits.class.getDeclaredMethod("get", IntSupplier.class);
        get.setAccessible(true);
        get.invoke(null, (IntSupplier) () -> 8);
    }

    private static void restoreImageLimits() throws Exception {
        if (imageLimitsInstanceField != null) {
            imageLimitsInstanceField.set(null, previousImageLimits);
        }
    }

    private static void assertComplementaryMetadata(ShaderPack pack, String expectedName) {
        assertFalse(pack.isInternal());
        assertEquals(expectedName, pack.getName());
        assertTrue(pack.getProperties().getProfiles().containsKey("HIGH"));
        assertTrue(pack.getProperties().getConditionallyEnabledPrograms().containsKey("world0/shadow"));
        assertTrue(pack.getProperties().getNoiseTexturePath().isPresent());
        assertTrue(pack.getCustomNoiseTexture().isPresent());
        assertTrue(pack.getCustomNoiseTexture().get() instanceof CustomTextureData.PngData);
        assertTrue(((CustomTextureData.PngData) pack.getCustomNoiseTexture().get()).getContent().length > 0);
        for (TextureStage stage : TextureStage.values()) {
            Map<String, String> customTextures = pack.getProperties().getCustomTextures().get(stage);
            assertNotNull(customTextures);
            assertEquals("minecraft:textures/atlas/blocks.png", customTextures.get("textureAtlas"));

            Map<String, CustomTextureData> loadedTextures = pack.getCustomTextureDataMap().get(stage);
            assertNotNull(loadedTextures);
            assertTrue(loadedTextures.containsKey("textureAtlas"));
            assertFalse(loadedTextures.containsKey("textureatlas"));
            assertTrue(loadedTextures.get("textureAtlas") instanceof CustomTextureData.ResourceData);
            CustomTextureData.ResourceData atlas =
                (CustomTextureData.ResourceData) loadedTextures.get("textureAtlas");
            assertEquals("minecraft", atlas.getNamespace());
            assertEquals("textures/atlas/blocks.png", atlas.getLocation());
        }
        assertEquals("lib/textures/cloud-water.png",
            pack.getProperties().getCustomTextures().get(TextureStage.DEFERRED).get("colortex3"));
        assertEquals("lib/textures/cloud-water.png",
            pack.getProperties().getCustomTextures().get(TextureStage.GBUFFERS_AND_SHADOW).get("gaux4"));
        assertPngCustomTexture(pack, TextureStage.DEFERRED, "colortex3");
        assertPngCustomTexture(pack, TextureStage.GBUFFERS_AND_SHADOW, "gaux4");
        assertTrue(pack.getProperties().getTextureScaleOverrides().containsKey("colortex1"));
        assertTrue(pack.getProperties().getTextureScaleOverrides().containsKey("colortex7"));
        assertEquals(512, pack.getProperties().getTextureScaleOverrides().get("colortex1").getX(1024));
        assertEquals(256, pack.getProperties().getTextureScaleOverrides().get("colortex1").getY(512));
        assertEquals(512, pack.getProperties().getTextureScaleOverrides().get("colortex7").getX(1024));
        assertEquals(256, pack.getProperties().getTextureScaleOverrides().get("colortex7").getY(512));
        assertTrue(pack.getProperties().getShaderStorageBufferSizes().containsKey(0));

        CustomImageData voxelImage = pack.getProperties().getCustomImages().get("voxel_img");
        assertNotNull(voxelImage);
        assertEquals("voxel_sampler", voxelImage.getSamplerName());
        assertEquals("128", voxelImage.getWidthExpression());
        assertEquals("64", voxelImage.getHeightExpression());
        assertEquals("128", voxelImage.getDepthExpression());

        assertEquals(44011, pack.getIdMap().getItemIdMap().getInt(
            new net.oculus.shaderpack.materialmap.NamespacedId("minecraft", "jack_o_lantern")));
        assertEquals(44037, pack.getIdMap().getItemIdMap().getInt(
            new net.oculus.shaderpack.materialmap.NamespacedId("minecraft", "magma_block")));
        assertTrue(pack.getIdMap().getBlockPropertiesMap().containsKey(10005));

        ProgramSet overworld = pack.getProgramSet(NamespacedId.overworld());
        assertEquals("/world0", overworld.getProgramRoot().getPathString());
        assertTrue(overworld.getGbuffersTerrain().isPresent());
        assertTrue(overworld.getGbuffersLine().isPresent());
        assertTrue(overworld.getCompositeFinal().isPresent());
        assertTrue(overworld.getGbuffersTerrain().get().getVertexSource().isPresent());
        assertTrue(overworld.getGbuffersTerrain().get().getFragmentSource().isPresent());
        assertTrue(overworld.getCompositeFinal().get().getFragmentSource().isPresent());
        assertFalse(overworld.getShadow().isPresent());
        assertTrue(overworld.getPackDirectives().shouldUseSeparateAo());

        ProgramSet nether = pack.getProgramSet(NamespacedId.nether());
        assertEquals("/world-1", nether.getProgramRoot().getPathString());
        assertTrue(nether.getGbuffersTerrain().isPresent());

        ProgramSet end = pack.getProgramSet(NamespacedId.end());
        assertEquals("/world1", end.getProgramRoot().getPathString());
        assertTrue(end.getGbuffersTerrain().isPresent());

        ProgramSet unknown = pack.getProgramSet(NamespacedId.of("example", "custom"));
        assertEquals("/world0", unknown.getProgramRoot().getPathString());
    }

    private static void assertComplementaryVolumeImage(
            ShaderPack pack,
            String imageName,
            String samplerName,
            PixelFormat pixelFormat,
            InternalTextureFormat internalFormat,
            PixelType pixelType,
            boolean clearOnNewFrame) {
        assertComplementaryImage(pack, imageName, samplerName, pixelFormat, internalFormat, pixelType, clearOnNewFrame,
            "128", "64", "128");
    }

    private static void assertComplementaryImage(
            ShaderPack pack,
            String imageName,
            String samplerName,
            PixelFormat pixelFormat,
            InternalTextureFormat internalFormat,
            PixelType pixelType,
            boolean clearOnNewFrame,
            String widthExpression,
            String heightExpression,
            String depthExpression) {
        CustomImageData image = pack.getProperties().getCustomImages().get(imageName);

        assertNotNull(image);
        assertEquals(imageName, image.getImageName());
        assertEquals(samplerName, image.getSamplerName());
        assertEquals(pixelFormat, image.getPixelFormat());
        assertEquals(internalFormat, image.getInternalFormat());
        assertEquals(pixelType, image.getPixelType());
        assertEquals(clearOnNewFrame, image.shouldClearOnNewFrame());
        assertFalse(image.isRelative());
        assertEquals(depthExpression != null, image.isThreeDimensional());
        assertEquals(widthExpression, image.getWidthExpression());
        assertEquals(heightExpression, image.getHeightExpression());
        assertEquals(depthExpression, image.getDepthExpression());
    }

    private static void assertCustomImageManagerResolvesDimensions(
            ShaderPack pack,
            String imageName,
            int expectedWidth,
            int expectedHeight,
            Integer expectedDepth) throws Exception {
        CustomImageData image = pack.getProperties().getCustomImages().get(imageName);
        assertNotNull(image);

        CustomImageManager manager = new CustomImageManager(pack.getProperties(), pack.getOptionValues());
        Method resolveDimension = CustomImageManager.class.getDeclaredMethod(
            "resolveDimensionForTesting", String.class, boolean.class, int.class);
        Method resolveAbsoluteDimension = CustomImageManager.class.getDeclaredMethod(
            "resolveAbsoluteDimensionForTesting", String.class);
        resolveDimension.setAccessible(true);
        resolveAbsoluteDimension.setAccessible(true);

        assertEquals(Integer.valueOf(expectedWidth),
            resolveDimension.invoke(manager, image.getWidthExpression(), image.isRelative(), 4096));
        assertEquals(Integer.valueOf(expectedHeight),
            resolveDimension.invoke(manager, image.getHeightExpression(), image.isRelative(), 2048));
        if (expectedDepth == null) {
            assertNull(image.getDepthExpression());
        } else {
            assertEquals(expectedDepth, resolveAbsoluteDimension.invoke(manager, image.getDepthExpression()));
        }
    }

    private static void assertMakeUpMetadata(ShaderPack pack, String expectedName) {
        assertFalse(pack.isInternal());
        assertEquals(expectedName, pack.getName());
        assertTrue(pack.getProperties().getProfiles().containsKey("high"));
        assertTrue(pack.getProperties().getProfiles().containsKey("shadowless_low"));
        assertTrue(pack.getProperties().getConditionallyEnabledPrograms().containsKey("shadow"));
        assertTrue(pack.getProperties().getConditionallyEnabledPrograms().containsKey("world0/shadow"));
        assertTrue(pack.getProperties().getSliderOptions().contains("sunPathRotation"));
        assertEquals("MakeUp", pack.getLanguageMap().get("en_us", "option.ACERCADE"));
        assertEquals("Shadows", pack.getLanguageMap().get("en_us", "option.SHADOW_CASTING"));

        assertTrue(pack.getProperties().getCustomTextures().containsKey(TextureStage.GBUFFERS_AND_SHADOW));
        assertEquals("textures/water_256_RG_8bit.png",
            pack.getProperties().getCustomTextures().get(TextureStage.GBUFFERS_AND_SHADOW).get("noisetex"));
        assertTrue(pack.getCustomTextureDataMap().containsKey(TextureStage.GBUFFERS_AND_SHADOW));
        assertTrue(pack.getCustomTextureDataMap().get(TextureStage.GBUFFERS_AND_SHADOW).containsKey("noisetex"));
        assertMakeUpConditionalCloudTexture(pack, "textures/clouds_natural_512_R_8bit.png");

        assertTrue(pack.getIdMap().getItemIdMap().containsKey(
            new net.oculus.shaderpack.materialmap.NamespacedId("minecraft", "redstone_torch")));
        assertTrue(pack.getIdMap().getBlockPropertiesMap().get(10090).contains(
            new net.oculus.shaderpack.materialmap.BlockEntry(
                new net.oculus.shaderpack.materialmap.NamespacedId("betterendforge", "lumecorn"),
                Collections.singletonMap("shape", "light_middle"))));
        assertTrue(pack.getIdMap().getBlockPropertiesMap().get(10090).contains(
            new net.oculus.shaderpack.materialmap.BlockEntry(
                new net.oculus.shaderpack.materialmap.NamespacedId("betterendforge", "purple_polypore"),
                Collections.emptyMap())));

        ProgramSet overworld = pack.getProgramSet(NamespacedId.overworld());
        assertEquals("/world0", overworld.getProgramRoot().getPathString());
        assertTrue(overworld.getGbuffersTerrain().isPresent());
        assertTrue(overworld.getGbuffersWater().isPresent());
        assertTrue(overworld.getCompositeFinal().isPresent());
        assertTrue(overworld.getShadow().isPresent());

        ProgramSet nether = pack.getProgramSet(NamespacedId.nether());
        assertEquals("/world-1", nether.getProgramRoot().getPathString());
        assertTrue(nether.getGbuffersTerrain().isPresent());

        ProgramSet end = pack.getProgramSet(NamespacedId.end());
        assertEquals("/world1", end.getProgramRoot().getPathString());
        assertTrue(end.getGbuffersTerrain().isPresent());
    }

    private static void assertMakeUpConditionalCloudTexture(ShaderPack pack, String expectedPath) {
        assertEquals(expectedPath,
            pack.getProperties().getCustomTextures().get(TextureStage.GBUFFERS_AND_SHADOW).get("gaux2"));
        assertEquals(expectedPath,
            pack.getProperties().getCustomTextures().get(TextureStage.DEFERRED).get("gaux2"));

        assertPngCustomTexture(pack, TextureStage.GBUFFERS_AND_SHADOW, "gaux2");
        assertPngCustomTexture(pack, TextureStage.DEFERRED, "gaux2");
    }

    private static void assertPngCustomTexture(ShaderPack pack, TextureStage stage, String samplerName) {
        Map<String, CustomTextureData> stageTextures = pack.getCustomTextureDataMap().get(stage);
        assertNotNull(stageTextures);
        CustomTextureData data = stageTextures.get(samplerName);
        assertTrue(data instanceof CustomTextureData.PngData);
        assertTrue(((CustomTextureData.PngData) data).getContent().length > 0);
    }

    private static IBlockState snowLayerState(int layers) {
        return stateWithProperty(Blocks.SNOW_LAYER.getDefaultState(), "layers", Integer.toString(layers));
    }

    private static IBlockState daylightDetectorState(Block block, int power) {
        return stateWithProperty(block.getDefaultState(), "power", Integer.toString(power));
    }

    private static IBlockState comparatorState(Block block, String mode) {
        return stateWithProperty(block.getDefaultState(), "mode", mode);
    }

    private static IBlockState quartzState(String variant) {
        return stateWithProperty(Blocks.QUARTZ_BLOCK.getDefaultState(), "variant", variant);
    }

    private static IBlockState redSandstoneSlabState() {
        return stateWithProperty(Blocks.STONE_SLAB2.getDefaultState(), "variant", "red_sandstone");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState stateWithProperty(IBlockState state, String propertyName, String valueName) {
        IProperty property = state.getBlock().getBlockState().getProperty(propertyName);
        for (Object allowedValue : property.getAllowedValues()) {
            Comparable comparable = (Comparable) allowedValue;
            if (property.getName(comparable).equals(valueName)) {
                return state.withProperty(property, comparable);
            }
        }
        throw new AssertionError("Missing property value " + propertyName + "=" + valueName);
    }

    private static void assertMakeUpProgramSourcesPrepare(ShaderPack pack) {
        int prepared = 0;
        prepared += assertProgramSetPrepares(pack.getProgramSet(NamespacedId.overworld()));
        prepared += assertProgramSetPrepares(pack.getProgramSet(NamespacedId.nether()));
        prepared += assertProgramSetPrepares(pack.getProgramSet(NamespacedId.end()));
        prepared += assertProgramSetPrepares(pack.getProgramSet(NamespacedId.of("example", "custom")));

        assertTrue("Expected MakeUp to prepare many real runtime program sources", prepared >= 250);
    }

    private static int assertProgramSetPrepares(ProgramSet programSet) {
        List<ProgramSource> sources = new ArrayList<>();
        addOptional(programSet.getShadow(), sources);
        addArray(programSet.getPrepare(), sources);
        addOptional(programSet.getGbuffersBasic(), sources);
        addOptional(programSet.getGbuffersLine(), sources);
        addOptional(programSet.getGbuffersBeaconBeam(), sources);
        addOptional(programSet.getGbuffersTextured(), sources);
        addOptional(programSet.getGbuffersTexturedLit(), sources);
        addOptional(programSet.getGbuffersTerrain(), sources);
        addOptional(programSet.getGbuffersDamagedBlock(), sources);
        addOptional(programSet.getGbuffersSkyBasic(), sources);
        addOptional(programSet.getGbuffersSkyTextured(), sources);
        addOptional(programSet.getGbuffersClouds(), sources);
        addOptional(programSet.getGbuffersWeather(), sources);
        addOptional(programSet.getGbuffersEntities(), sources);
        addOptional(programSet.getGbuffersEntitiesTrans(), sources);
        addOptional(programSet.getGbuffersEntitiesGlowing(), sources);
        addOptional(programSet.getGbuffersGlint(), sources);
        addOptional(programSet.getGbuffersEntityEyes(), sources);
        addOptional(programSet.getGbuffersBlock(), sources);
        addOptional(programSet.getGbuffersHand(), sources);
        addArray(programSet.getDeferred(), sources);
        addOptional(programSet.getGbuffersWater(), sources);
        addOptional(programSet.getGbuffersHandWater(), sources);
        addArray(programSet.getComposite(), sources);
        addOptional(programSet.getCompositeFinal(), sources);

        List<StringPair> environmentDefines = ShaderPreprocessor.createEnvironmentDefines(programSet);
        Set<String> compiledNames = new HashSet<>();
        int prepared = 0;
        for (ProgramSource source : sources) {
            if (source == null || !source.isValid() || !compiledNames.add(source.getName())) {
                continue;
            }

            if (usesAvailabilityVariants(source.getName())) {
                for (int packed = 0; packed < InputAvailability.NUM_VALUES; packed++) {
                    assertProgramSourcePrepares(programSet, source, environmentDefines, InputAvailability.unpack(packed));
                    prepared++;
                }
            } else {
                assertProgramSourcePrepares(programSet, source, environmentDefines, null);
                prepared++;
            }
        }

        return prepared;
    }

    private static void assertNoRuntimeSourceContains(ShaderPack pack, String... inactiveNames) {
        List<String> sources = new ArrayList<>();
        collectRuntimeSources(pack.getProgramSet(NamespacedId.overworld()), sources);
        collectRuntimeSources(pack.getProgramSet(NamespacedId.nether()), sources);
        collectRuntimeSources(pack.getProgramSet(NamespacedId.end()), sources);
        collectRuntimeSources(pack.getProgramSet(NamespacedId.of("example", "custom")), sources);
        assertTrue("Expected preprocessed runtime sources for " + pack.getName(), sources.size() > 0);

        for (String source : sources) {
            for (String inactiveName : inactiveNames) {
                assertFalse(pack.getName() + " preprocessed runtime source still contains inactive "
                    + inactiveName, source.contains(inactiveName));
            }
        }
    }

    private static void assertRuntimeNonSamplerUniformsCovered(ShaderPack pack) throws IOException {
        Set<String> knownUniforms = extractProgramBuilderUniformCases();
        knownUniforms.addAll(pack.getProperties().getCustomUniforms().keySet());

        List<String> sources = new ArrayList<>();
        collectRuntimeSources(pack.getProgramSet(NamespacedId.overworld()), sources);
        collectRuntimeSources(pack.getProgramSet(NamespacedId.nether()), sources);
        collectRuntimeSources(pack.getProgramSet(NamespacedId.end()), sources);
        collectRuntimeSources(pack.getProgramSet(NamespacedId.of("example", "custom")), sources);
        assertTrue("Expected preprocessed runtime sources for " + pack.getName(), sources.size() > 0);

        Set<String> missing = new LinkedHashSet<>();
        for (String source : sources) {
            collectMissingNonSamplerUniforms(source, knownUniforms, missing);
        }

        assertTrue(pack.getName() + " runtime non-sampler uniform declarations missing ProgramBuilder/custom coverage: "
            + missing, missing.isEmpty());
    }

    private static void assertRuntimeSamplerAndImageUniformsCovered(ShaderPack pack) {
        Set<String> knownSamplers = targetPackSamplerBindingNames(pack);
        Set<String> knownImages = targetPackImageBindingNames(pack);

        List<String> sources = new ArrayList<>();
        collectRuntimeSources(pack.getProgramSet(NamespacedId.overworld()), sources);
        collectRuntimeSources(pack.getProgramSet(NamespacedId.nether()), sources);
        collectRuntimeSources(pack.getProgramSet(NamespacedId.end()), sources);
        collectRuntimeSources(pack.getProgramSet(NamespacedId.of("example", "custom")), sources);
        assertTrue("Expected preprocessed runtime sources for " + pack.getName(), sources.size() > 0);

        Set<String> missing = new LinkedHashSet<>();
        for (String source : sources) {
            collectMissingSamplerImageUniforms(source, knownSamplers, knownImages, missing);
        }

        assertTrue(pack.getName() + " runtime sampler/image declarations missing binding coverage: "
            + missing, missing.isEmpty());
    }

    private static Set<String> targetPackSamplerBindingNames(ShaderPack pack) {
        Set<String> samplers = new LinkedHashSet<>(Arrays.asList(
            "u_BlockTex",
            "u_LightTex",
            "tex",
            "texture",
            "gtexture",
            "gcolor",
            "colortex0",
            "lightmap",
            "iris_overlay",
            "normals",
            "specular",
            "iris_centerDepthSmooth",
            "centerDepthSmooth",
            "gdepthtex",
            "depthtex0",
            "depthtex1",
            "depthtex2",
            "oculus_depth",
            "shadow",
            "s_shadow",
            "watershadow",
            "shadowtex0",
            "shadowtex0HW",
            "shadowtex0hw",
            "shadowtex1",
            "shadowtex1HW",
            "shadowtex1hw",
            "shadowcolor",
            "s_shadowcolor",
            "shadowcolor0",
            "shadowcolor1",
            "oculus_shadow_depth",
            "oculus_shadow_depth_notrans",
            "oculus_shadow_color",
            "oculus_shadow_color1",
            "oculus_noise",
            "custom_noise",
            "noise_texture",
            "noisetex",
            "gaux0"
        ));
        samplers.addAll(PackRenderTargetDirectives.LEGACY_RENDER_TARGETS);
        addIndexedNames(samplers, "colortex", IrisLimits.MAX_COLOR_BUFFERS);
        addIndexedNames(samplers, "oculus_rt", IrisLimits.MAX_COLOR_BUFFERS);
        addIndexedNames(samplers, "oculus_flipped_rt", IrisLimits.MAX_COLOR_BUFFERS);

        for (Map<String, String> stageTextures : pack.getProperties().getCustomTextures().values()) {
            samplers.addAll(stageTextures.keySet());
        }
        for (Map<String, CustomTextureData> stageTextures : pack.getCustomTextureDataMap().values()) {
            samplers.addAll(stageTextures.keySet());
        }
        for (CustomImageData image : pack.getProperties().getCustomImages().values()) {
            samplers.add(image.getSamplerName());
        }
        return samplers;
    }

    private static Set<String> targetPackImageBindingNames(ShaderPack pack) {
        Set<String> images = new LinkedHashSet<>();
        addIndexedNames(images, "colorimg", IrisLimits.MAX_COLOR_BUFFERS);
        images.add("shadowcolorimg0");
        images.add("shadowcolorimg1");
        images.addAll(pack.getProperties().getCustomImages().keySet());
        return images;
    }

    private static void addIndexedNames(Set<String> names, String prefix, int count) {
        for (int index = 0; index < count; index++) {
            names.add(prefix + index);
        }
    }

    private static Set<String> extractProgramBuilderUniformCases() throws IOException {
        String builder = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");
        Set<String> uniforms = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("case\\s+\"([^\"]+)\"\\s*:").matcher(builder);
        while (matcher.find()) {
            uniforms.add(matcher.group(1));
        }
        return uniforms;
    }

    private static void collectMissingNonSamplerUniforms(String source, Set<String> knownUniforms, Set<String> missing) {
        String stripped = stripComments(source);
        Matcher declaration = UNIFORM_DECLARATION.matcher(stripped);
        while (declaration.find()) {
            String type = declaration.group(1);
            if (isSamplerImageOrAtomicType(type)) {
                continue;
            }

            for (String declarator : declaration.group(2).split(",")) {
                Matcher nameMatcher = IDENTIFIER.matcher(declarator);
                if (!nameMatcher.find()) {
                    continue;
                }

                String name = nameMatcher.group(1);
                if (!name.startsWith("gl_") && !knownUniforms.contains(name)) {
                    missing.add(type + " " + name);
                }
            }
        }
    }

    private static void collectMissingSamplerImageUniforms(String source, Set<String> knownSamplers,
                                                           Set<String> knownImages, Set<String> missing) {
        String stripped = stripComments(source);
        Matcher declaration = UNIFORM_DECLARATION.matcher(stripped);
        while (declaration.find()) {
            String type = declaration.group(1);
            boolean sampler = isSamplerTypeName(type);
            boolean image = isImageTypeName(type);
            if (!sampler && !image) {
                continue;
            }

            for (String declarator : declaration.group(2).split(",")) {
                Matcher nameMatcher = IDENTIFIER.matcher(declarator);
                if (!nameMatcher.find()) {
                    continue;
                }

                String name = nameMatcher.group(1);
                if (name.startsWith("gl_")) {
                    continue;
                }

                if (sampler && !knownSamplers.contains(name)) {
                    missing.add(type + " " + name);
                } else if (image && !knownImages.contains(name)) {
                    missing.add(type + " " + name);
                }
            }
        }
    }

    private static String stripComments(String source) {
        return source
            .replaceAll("(?s)/\\*.*?\\*/", "")
            .replaceAll("(?m)//.*$", "");
    }

    private static boolean isSamplerImageOrAtomicType(String type) {
        return isSamplerTypeName(type) || isImageTypeName(type) || "atomic_uint".equals(type.toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean isSamplerTypeName(String type) {
        return type.toLowerCase(java.util.Locale.ROOT).contains("sampler");
    }

    private static boolean isImageTypeName(String type) {
        return type.toLowerCase(java.util.Locale.ROOT).contains("image");
    }

    private static void collectRuntimeSources(ProgramSet programSet, List<String> sink) {
        List<ProgramSource> sources = new ArrayList<>();
        addOptional(programSet.getShadow(), sources);
        addArray(programSet.getShadowComposite(), sources);
        addArray(programSet.getPrepare(), sources);
        addOptional(programSet.getGbuffersBasic(), sources);
        addOptional(programSet.getGbuffersLine(), sources);
        addOptional(programSet.getGbuffersBeaconBeam(), sources);
        addOptional(programSet.getGbuffersTextured(), sources);
        addOptional(programSet.getGbuffersTexturedLit(), sources);
        addOptional(programSet.getGbuffersTerrain(), sources);
        addOptional(programSet.getGbuffersDamagedBlock(), sources);
        addOptional(programSet.getGbuffersSkyBasic(), sources);
        addOptional(programSet.getGbuffersSkyTextured(), sources);
        addOptional(programSet.getGbuffersClouds(), sources);
        addOptional(programSet.getGbuffersWeather(), sources);
        addOptional(programSet.getGbuffersEntities(), sources);
        addOptional(programSet.getGbuffersEntitiesTrans(), sources);
        addOptional(programSet.getGbuffersEntitiesGlowing(), sources);
        addOptional(programSet.getGbuffersGlint(), sources);
        addOptional(programSet.getGbuffersEntityEyes(), sources);
        addOptional(programSet.getGbuffersBlock(), sources);
        addOptional(programSet.getGbuffersHand(), sources);
        addArray(programSet.getDeferred(), sources);
        addOptional(programSet.getGbuffersWater(), sources);
        addOptional(programSet.getGbuffersHandWater(), sources);
        addArray(programSet.getComposite(), sources);
        addOptional(programSet.getCompositeFinal(), sources);

        Set<String> programNames = new HashSet<>();
        for (ProgramSource source : sources) {
            if (source == null || !source.isValid() || !programNames.add(source.getName())) {
                continue;
            }

            source.getVertexSource().ifPresent(sink::add);
            source.getGeometrySource().ifPresent(sink::add);
            source.getFragmentSource().ifPresent(sink::add);
        }

        collectComputeSources(programSet.getShadowCompute(), sink);
        collectComputeSources(programSet.getFinalCompute(), sink);
        collectComputeSources(programSet.getShadowCompCompute(), sink);
        collectComputeSources(programSet.getPrepareCompute(), sink);
        collectComputeSources(programSet.getDeferredCompute(), sink);
        collectComputeSources(programSet.getCompositeCompute(), sink);
    }

    private static void collectComputeSources(ComputeSource[] sources, List<String> sink) {
        if (sources == null) {
            return;
        }

        for (ComputeSource source : sources) {
            if (source != null && source.isValid()) {
                source.getSource().ifPresent(sink::add);
            }
        }
    }

    private static void collectComputeSources(ComputeSource[][] sources, List<String> sink) {
        if (sources == null) {
            return;
        }

        for (ComputeSource[] group : sources) {
            collectComputeSources(group, sink);
        }
    }

    private static int assertRepresentativeProgramSetPrepares(ProgramSet programSet) {
        List<StringPair> environmentDefines = ShaderPreprocessor.createEnvironmentDefines(programSet);
        Set<String> preparedNames = new HashSet<>();
        int prepared = 0;

        prepared += assertRepresentativeProgramSourcePrepares(programSet, environmentDefines, preparedNames,
            programSet.getGbuffersTerrain().orElse(null), new InputAvailability(true, true, true));
        prepared += assertRepresentativeProgramSourcePrepares(programSet, environmentDefines, preparedNames,
            programSet.getGbuffersWater().orElse(null), new InputAvailability(true, true, true));
        prepared += assertRepresentativeProgramSourcePrepares(programSet, environmentDefines, preparedNames,
            firstValid(programSet.getDeferred()), null);
        prepared += assertRepresentativeProgramSourcePrepares(programSet, environmentDefines, preparedNames,
            firstValid(programSet.getComposite()), null);
        prepared += assertRepresentativeProgramSourcePrepares(programSet, environmentDefines, preparedNames,
            programSet.getCompositeFinal().orElse(null), null);

        return prepared;
    }

    private static int assertRepresentativeProgramSourcePrepares(ProgramSet programSet,
                                                                 List<StringPair> environmentDefines,
                                                                 Set<String> preparedNames,
                                                                 ProgramSource source,
                                                                 InputAvailability availability) {
        if (source == null || !source.isValid() || !preparedNames.add(source.getName())) {
            return 0;
        }

        assertProgramSourcePrepares(programSet, source, environmentDefines, availability);
        return 1;
    }

    private static ProgramSource firstValid(ProgramSource[] sources) {
        if (sources == null) {
            return null;
        }

        for (ProgramSource source : sources) {
            if (source != null && source.isValid()) {
                return source;
            }
        }

        return null;
    }

    private static void addOptional(java.util.Optional<ProgramSource> optional, List<ProgramSource> sink) {
        optional.ifPresent(sink::add);
    }

    private static void addArray(ProgramSource[] array, List<ProgramSource> sink) {
        if (array != null) {
            Collections.addAll(sink, array);
        }
    }

    private static boolean usesAvailabilityVariants(String programName) {
        return programName.startsWith("gbuffers_") || programName.equals("shadow") || programName.startsWith("shadow_");
    }

    private static void assertProgramSourcePrepares(ProgramSet programSet,
                                                    ProgramSource source,
                                                    List<StringPair> environmentDefines,
                                                    InputAvailability availability) {
        ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
            programSet.getPack().getName(),
            source.getName(),
            source.getVertexSource().orElse(null),
            source.getGeometrySource().orElse(null),
            source.getFragmentSource().orElse(null),
            environmentDefines,
            availability);

        assertPreparedStage(source.getName(), "vertex", prepared.getVertexSource());
        assertPreparedStage(source.getName(), "fragment", prepared.getFragmentSource());
        if (source.getGeometrySource().isPresent()) {
            assertPreparedStage(source.getName(), "geometry", prepared.getGeometrySource());
        }
    }

    private static void assertPreparedStage(String programName, String stage, String source) {
        assertNotNull("Prepared " + stage + " source missing for " + programName, source);
        assertFalse("Prepared " + stage + " source still contains an include directive for " + programName,
            source.contains("#include"));
        assertTrue("Prepared " + stage + " source missing shader stage define for " + programName,
            source.contains("#define " + stage.toUpperCase(java.util.Locale.ROOT) + "_SHADER"));
    }

    private static void assumeComplementaryExists(Path shaderpacks) {
        Assume.assumeTrue("Complementary Reimagined shader pack is not available",
            Files.isDirectory(shaderpacks.resolve(COMPLEMENTARY).resolve("shaders")));
    }

    private static void assumeComplementaryZipExists(Path shaderpacks) {
        Assume.assumeTrue("Complementary Reimagined shader pack zip is not available",
            Files.isRegularFile(shaderpacks.resolve(COMPLEMENTARY_ZIP)));
    }

    private static void assumeMakeUpExists(Path shaderpacks) {
        Assume.assumeTrue("MakeUp UltraFast shader pack is not available",
            Files.isDirectory(shaderpacks.resolve(MAKEUP).resolve("shaders")));
    }

    private static void assumeMakeUpZipExists(Path shaderpacks) {
        Assume.assumeTrue("MakeUp UltraFast shader pack zip is not available",
            Files.isRegularFile(shaderpacks.resolve(MAKEUP_ZIP)));
    }

    private static void writeNestedShaderPackZip(Path zipPath) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            addZipEntry(zip, "NestedPack/shaders/shaders.properties",
                "separateAo=true\ntexture.noise=textures/noise.png\n");
            addZipEntry(zip, "NestedPack/shaders/lang/en_US.lang",
                "option.NESTED=Nested Value\n");
            addZipEntry(zip, "NestedPack/shaders/item.properties",
                "item.7=minecraft:stick\n");
            addZipEntry(zip, "NestedPack/shaders/textures/noise.png",
                new byte[] {1, 2, 3, 4});
            addZipEntry(zip, "NestedPack/shaders/world0/gbuffers_terrain.vsh",
                "#version 120\nvoid main() { gl_Position = gl_Vertex; }\n");
            addZipEntry(zip, "NestedPack/shaders/world0/gbuffers_terrain.fsh",
                "#version 120\nvoid main() { gl_FragData[0] = vec4(1.0); }\n");
        }
    }

    private static void writeMinimalProgram(Path directory) throws IOException {
        writeMinimalProgram(directory, "");
    }

    private static void writeMinimalProgram(Path directory, String vertexPrefix) throws IOException {
        Files.createDirectories(directory);
        Files.write(directory.resolve("gbuffers_terrain.vsh"),
            Collections.singletonList("#version 120\n" + vertexPrefix + "void main() { gl_Position = gl_Vertex; }\n"),
            StandardCharsets.UTF_8);
        Files.write(directory.resolve("gbuffers_terrain.fsh"),
            Collections.singletonList("#version 120\nvoid main() { gl_FragData[0] = vec4(1.0); }\n"),
            StandardCharsets.UTF_8);
    }

    private static void addZipEntry(ZipOutputStream zip, String path, String contents) throws Exception {
        addZipEntry(zip, path, contents.getBytes(StandardCharsets.UTF_8));
    }

    private static void addZipEntry(ZipOutputStream zip, String path, byte[] contents) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(contents);
        zip.closeEntry();
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static Map<String, String> complementaryRuntimeOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("WORLD_SPACE_REFLECTIONS", "1");
        overrides.put("COLORED_LIGHTING", "512");
        overrides.put("SHADOW_QUALITY", "4");
        overrides.put("DETAIL_QUALITY", "3");
        overrides.put("ANISOTROPIC_FILTER", "8");
        overrides.put("LIGHTSHAFT_QUALI_DEFINE", "3");
        overrides.put("shadowDistance", "256.0");
        overrides.put("CLOUD_QUALITY", "3");
        return overrides;
    }

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while (index < source.length()) {
            int found = source.indexOf(needle, index);
            if (found < 0) {
                return count;
            }
            count++;
            index = found + needle.length();
        }
        return count;
    }

}
