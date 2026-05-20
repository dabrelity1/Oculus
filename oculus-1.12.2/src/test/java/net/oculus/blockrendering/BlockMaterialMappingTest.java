package net.oculus.blockrendering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.oculus.shaderpack.materialmap.BlockEntry;
import net.oculus.shaderpack.materialmap.NamespacedId;
import org.junit.Test;

public class BlockMaterialMappingTest {
    @Test
    public void redstoneOreLitPredicateChoosesLegacySplitBlockNames() {
        assertResolved("redstone_ore:lit=true", "lit_redstone_ore", Collections.emptyMap());
        assertResolved("redstone_ore:lit=false", "redstone_ore", Collections.emptyMap());
    }

    @Test
    public void furnaceLitPredicateChoosesLegacySplitBlockNames() {
        assertResolved("furnace:lit=true", "lit_furnace", Collections.emptyMap());
        assertResolved("furnace:lit=false", "furnace", Collections.emptyMap());
    }

    @Test
    public void redstoneLampLitPredicateChoosesLegacySplitBlockNames() {
        assertResolved("redstone_lamp:lit=true", "lit_redstone_lamp", Collections.emptyMap());
        assertResolved("redstone_lamp:lit=false", "redstone_lamp", Collections.emptyMap());
    }

    @Test
    public void redstoneTorchLitPredicateChoosesLegacySplitBlockNames() {
        assertResolved("redstone_torch:lit=true", "redstone_torch", Collections.emptyMap());
        assertResolved("redstone_torch:lit=false", "unlit_redstone_torch", Collections.emptyMap());
        assertResolved("redstone_wall_torch:lit=true", "redstone_torch", Collections.emptyMap());
        assertResolved("redstone_wall_torch:lit=false", "unlit_redstone_torch", Collections.emptyMap());
    }

    @Test
    public void redstoneDiodePoweredPredicateChoosesLegacySplitBlockNames() {
        assertResolved("repeater:powered=true", "powered_repeater", Collections.emptyMap());
        assertResolved("repeater:powered=false", "unpowered_repeater", Collections.emptyMap());
        assertResolved("repeater:delay=4:powered=true", "powered_repeater",
            Collections.singletonMap("delay", "4"));
        assertResolved("comparator:mode=compare:powered=true", "powered_comparator",
            Collections.singletonMap("mode", "compare"));
        assertResolved("comparator:mode=subtract:powered=false", "unpowered_comparator",
            Collections.singletonMap("mode", "subtract"));
    }

    @Test
    public void comparatorPoweredPredicateIsConsumedBefore112StateMapping() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10644, Arrays.asList(
            BlockEntry.parse("powered_comparator"),
            BlockEntry.parse("comparator:mode=compare:powered=true"),
            BlockEntry.parse("comparator:mode=subtract:powered=true")));
        entries.put(10645, Arrays.asList(
            BlockEntry.parse("unpowered_comparator"),
            BlockEntry.parse("comparator:mode=compare:powered=false")));
        entries.put(10646, Collections.singletonList(
            BlockEntry.parse("comparator:mode=subtract:powered=false")));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(10644, idMap.getInt(comparatorState(Blocks.POWERED_COMPARATOR, "compare")));
        assertEquals(10644, idMap.getInt(comparatorState(Blocks.POWERED_COMPARATOR, "subtract")));
        assertEquals(10645, idMap.getInt(comparatorState(Blocks.UNPOWERED_COMPARATOR, "compare")));
        assertEquals(10646, idMap.getInt(comparatorState(Blocks.UNPOWERED_COMPARATOR, "subtract")));
    }

    @Test
    public void snowBlockUsesLegacyFullSnowBlockName() {
        assertEquals("snow", BlockMaterialMapping.legacyMinecraftBlockName(new NamespacedId("minecraft", "snow_block")));
    }

    @Test
    public void snowLayersPredicateUsesLegacySnowLayerBlockName() {
        assertResolved("snow:layers=8", "snow_layer", Collections.singletonMap("layers", "8"));
    }

    @Test
    public void modernColorSplitBlocksResolveToLegacyColorPropertyStates() {
        assertResolved("orange_stained_glass", "stained_glass", Collections.singletonMap("color", "orange"));
        assertResolved("light_gray_stained_glass_pane", "stained_glass_pane", Collections.singletonMap("color", "silver"));
        assertResolved("lime_wool", "wool", Collections.singletonMap("color", "lime"));
        assertResolved("lime_carpet", "carpet", Collections.singletonMap("color", "lime"));
        assertResolved("lime_concrete", "concrete", Collections.singletonMap("color", "lime"));
        assertResolved("light_gray_concrete_powder", "concrete_powder", Collections.singletonMap("color", "silver"));
        assertResolved("white_terracotta", "stained_hardened_clay", Collections.singletonMap("color", "white"));
        assertResolved("terracotta", "hardened_clay", Collections.emptyMap());
    }

    @Test
    public void modernGrassPlantDoesNotClaimLegacyGrassBlockStates() {
        assertResolved("grass", "tallgrass", Collections.singletonMap("type", "tall_grass"));
        assertResolved("grass:snowy=true", "grass", Collections.singletonMap("snowy", "true"));
        assertResolved("grass_block:snowy=false", "grass", Collections.singletonMap("snowy", "false"));
    }

    @Test
    public void targetPackModernSinglePlantsResolveTo112PlantStates() {
        assertResolved("short_grass", "tallgrass", Collections.singletonMap("type", "tall_grass"));
        assertResolved("fern", "tallgrass", Collections.singletonMap("type", "fern"));
        assertResolved("dead_bush", "deadbush", Collections.emptyMap());
        assertResolved("dandelion", "yellow_flower", Collections.singletonMap("type", "dandelion"));
        assertResolved("poppy", "red_flower", Collections.singletonMap("type", "poppy"));
        assertResolved("blue_orchid", "red_flower", Collections.singletonMap("type", "blue_orchid"));
        assertResolved("allium", "red_flower", Collections.singletonMap("type", "allium"));
        assertResolved("azure_bluet", "red_flower", Collections.singletonMap("type", "houstonia"));
        assertResolved("red_tulip", "red_flower", Collections.singletonMap("type", "red_tulip"));
        assertResolved("orange_tulip", "red_flower", Collections.singletonMap("type", "orange_tulip"));
        assertResolved("white_tulip", "red_flower", Collections.singletonMap("type", "white_tulip"));
        assertResolved("pink_tulip", "red_flower", Collections.singletonMap("type", "pink_tulip"));
        assertResolved("oxeye_daisy", "red_flower", Collections.singletonMap("type", "oxeye_daisy"));
        assertResolved("oak_sapling", "sapling", Collections.singletonMap("type", "oak"));
        assertResolved("spruce_sapling", "sapling", Collections.singletonMap("type", "spruce"));
        assertResolved("birch_sapling", "sapling", Collections.singletonMap("type", "birch"));
        assertResolved("jungle_sapling", "sapling", Collections.singletonMap("type", "jungle"));
        assertResolved("acacia_sapling", "sapling", Collections.singletonMap("type", "acacia"));
        assertResolved("dark_oak_sapling", "sapling", Collections.singletonMap("type", "dark_oak"));
    }

    @Test
    public void targetPackModernWoodFamiliesResolveTo112VariantStates() {
        assertResolved("oak_planks", "planks", Collections.singletonMap("variant", "oak"));
        assertResolved("spruce_planks", "planks", Collections.singletonMap("variant", "spruce"));
        assertResolved("dark_oak_planks", "planks", Collections.singletonMap("variant", "dark_oak"));
        assertResolved("oak_log", "log", Collections.singletonMap("variant", "oak"));
        assertResolved("oak_log:axis=x", "log", predicates("axis", "x", "variant", "oak"));
        assertResolved("jungle_wood", "log", predicates("axis", "none", "variant", "jungle"));
        assertResolved("acacia_log", "log2", Collections.singletonMap("variant", "acacia"));
        assertResolved("dark_oak_wood", "log2", predicates("axis", "none", "variant", "dark_oak"));
        assertResolved("oak_leaves", "leaves", Collections.singletonMap("variant", "oak"));
        assertResolved("dark_oak_leaves", "leaves2", Collections.singletonMap("variant", "dark_oak"));
        assertResolved("oak_slab", "wooden_slab", Collections.singletonMap("variant", "oak"));
        assertResolved("dark_oak_slab", "wooden_slab", Collections.singletonMap("variant", "dark_oak"));
    }

    @Test
    public void strippedWoodAliasesStayUnmappedToAvoidStealingRealLogStates() {
        BlockEntry strippedLog = BlockMaterialMapping.resolveLegacyBlockEntry(BlockEntry.parse("stripped_oak_log"));
        assertEquals(new NamespacedId("minecraft", "stripped_oak_log"), strippedLog.getId());
        assertEquals(Collections.emptyMap(), strippedLog.getPropertyPredicates());

        BlockEntry strippedWood = BlockMaterialMapping.resolveLegacyBlockEntry(BlockEntry.parse("stripped_dark_oak_wood"));
        assertEquals(new NamespacedId("minecraft", "stripped_dark_oak_wood"), strippedWood.getId());
        assertEquals(Collections.emptyMap(), strippedWood.getPropertyPredicates());
    }

    @Test
    public void targetPackModernStoneFamiliesResolveTo112VariantStates() {
        assertResolved("granite", "stone", Collections.singletonMap("variant", "granite"));
        assertResolved("diorite", "stone", Collections.singletonMap("variant", "diorite"));
        assertResolved("andesite", "stone", Collections.singletonMap("variant", "andesite"));
        assertResolved("polished_granite", "stone", Collections.singletonMap("variant", "smooth_granite"));
        assertResolved("polished_diorite", "stone", Collections.singletonMap("variant", "smooth_diorite"));
        assertResolved("polished_andesite", "stone", Collections.singletonMap("variant", "smooth_andesite"));
        assertResolved("stone_bricks", "stonebrick", Collections.singletonMap("variant", "stonebrick"));
        assertResolved("mossy_stone_bricks", "stonebrick", Collections.singletonMap("variant", "mossy_stonebrick"));
        assertResolved("cracked_stone_bricks", "stonebrick", Collections.singletonMap("variant", "cracked_stonebrick"));
        assertResolved("chiseled_stone_bricks", "stonebrick", Collections.singletonMap("variant", "chiseled_stonebrick"));
        assertResolved("infested_stone", "monster_egg", Collections.singletonMap("variant", "stone"));
        assertResolved("infested_cobblestone", "monster_egg", Collections.singletonMap("variant", "cobblestone"));
        assertResolved("infested_stone_bricks", "monster_egg", Collections.singletonMap("variant", "stone_brick"));
        assertResolved("infested_mossy_stone_bricks", "monster_egg", Collections.singletonMap("variant", "mossy_brick"));
        assertResolved("infested_cracked_stone_bricks", "monster_egg", Collections.singletonMap("variant", "cracked_brick"));
        assertResolved("infested_chiseled_stone_bricks", "monster_egg", Collections.singletonMap("variant", "chiseled_brick"));
    }

    @Test
    public void targetPackModernPrismarineFamilyResolvesTo112VariantStates() {
        assertResolved("prismarine", "prismarine", Collections.singletonMap("variant", "rough"));
        assertResolved("prismarine_bricks", "prismarine", Collections.singletonMap("variant", "bricks"));
        assertResolved("dark_prismarine", "prismarine", Collections.singletonMap("variant", "dark"));
    }

    @Test
    public void targetPackModernTerrainVariantsResolveTo112VariantStates() {
        assertResolved("coarse_dirt", "dirt", Collections.singletonMap("variant", "coarse_dirt"));
        assertResolved("podzol:snowy=true", "dirt", predicates("snowy", "true", "variant", "podzol"));
        assertResolved("red_sand", "sand", Collections.singletonMap("variant", "red_sand"));
        assertResolved("chiseled_sandstone", "sandstone", Collections.singletonMap("type", "chiseled_sandstone"));
        assertResolved("cut_sandstone", "sandstone", Collections.singletonMap("type", "smooth_sandstone"));
        assertResolved("smooth_sandstone", "sandstone", Collections.singletonMap("type", "smooth_sandstone"));
        assertResolved("chiseled_red_sandstone", "red_sandstone",
            Collections.singletonMap("type", "chiseled_red_sandstone"));
        assertResolved("cut_red_sandstone", "red_sandstone",
            Collections.singletonMap("type", "smooth_red_sandstone"));
        assertResolved("smooth_red_sandstone", "red_sandstone",
            Collections.singletonMap("type", "smooth_red_sandstone"));
        assertResolved("chiseled_quartz_block", "quartz_block", Collections.singletonMap("variant", "chiseled"));
        assertResolved("quartz_pillar", "quartz_block", Collections.singletonMap("variant", "lines_y"));
        assertResolved("quartz_pillar:axis=x", "quartz_block", Collections.singletonMap("variant", "lines_x"));
        assertResolved("quartz_pillar:axis=z", "quartz_block", Collections.singletonMap("variant", "lines_z"));
    }

    @Test
    public void attachedStemAliasesResolveHorizontal112StemStatesWithoutClaimingUnattachedStems() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10005, Arrays.asList(
            BlockEntry.parse("pumpkin_stem"),
            BlockEntry.parse("melon_stem")));
        entries.put(10017, Arrays.asList(
            BlockEntry.parse("attached_pumpkin_stem"),
            BlockEntry.parse("attached_melon_stem")));

        Set<String> families = BlockMaterialMapping.collectDefaultVariantFamilies(entries);
        assertTrue(families.contains("pumpkin_stem"));
        assertTrue(families.contains("melon_stem"));
        assertConstrained("pumpkin_stem", families, "pumpkin_stem",
            Collections.singletonMap("facing", "up"));
        assertConstrained("melon_stem", families, "melon_stem",
            Collections.singletonMap("facing", "up"));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(10005, idMap.getInt(stemState(Blocks.PUMPKIN_STEM, "up")));
        assertEquals(10005, idMap.getInt(stemState(Blocks.MELON_STEM, "up")));
        assertEquals(10017, idMap.getInt(stemState(Blocks.PUMPKIN_STEM, "north")));
        assertEquals(10017, idMap.getInt(stemState(Blocks.PUMPKIN_STEM, "south")));
        assertEquals(10017, idMap.getInt(stemState(Blocks.MELON_STEM, "east")));
        assertEquals(10017, idMap.getInt(stemState(Blocks.MELON_STEM, "west")));
    }

    @Test
    public void modernSnowLayerPredicatesSkipBroadLegacySnowLayerRow() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10380, Arrays.asList(
            BlockEntry.parse("snow_block"),
            BlockEntry.parse("snow:layers=8")));
        entries.put(10381, Collections.singletonList(BlockEntry.parse("snow_layer")));
        entries.put(10953, Arrays.asList(
            BlockEntry.parse("snow:layers=1"),
            BlockEntry.parse("snow:layers=2"),
            BlockEntry.parse("snow:layers=3"),
            BlockEntry.parse("snow:layers=4"),
            BlockEntry.parse("snow:layers=5"),
            BlockEntry.parse("snow:layers=6"),
            BlockEntry.parse("snow:layers=7")));

        Set<String> families = BlockMaterialMapping.collectBroadLegacyFamilies(entries);
        assertTrue(families.contains("snow_layer"));
        assertTrue(BlockMaterialMapping.shouldSkipBroadLegacyContainer(
            BlockEntry.parse("snow_layer"), families));
        assertFalse(BlockMaterialMapping.shouldSkipBroadLegacyContainer(
            BlockEntry.parse("snow:layers=4"), families));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(10380, idMap.getInt(Blocks.SNOW.getDefaultState()));
        assertEquals(10953, idMap.getInt(snowLayerState(1)));
        assertEquals(10953, idMap.getInt(snowLayerState(4)));
        assertEquals(10953, idMap.getInt(snowLayerState(7)));
        assertEquals(10380, idMap.getInt(snowLayerState(8)));
    }

    @Test
    public void cutSandstoneAliasesMap112SmoothStatesWithoutBroadPreemption() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10240, Collections.singletonList(BlockEntry.parse("sandstone")));
        entries.put(10241, Collections.singletonList(BlockEntry.parse("cut_sandstone")));
        entries.put(10244, Collections.singletonList(BlockEntry.parse("red_sandstone")));
        entries.put(10245, Collections.singletonList(BlockEntry.parse("cut_red_sandstone")));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(10240, idMap.getInt(sandstoneState("sandstone")));
        assertEquals(10241, idMap.getInt(sandstoneState("smooth_sandstone")));
        assertEquals(10244, idMap.getInt(redSandstoneState("red_sandstone")));
        assertEquals(10245, idMap.getInt(redSandstoneState("smooth_red_sandstone")));
    }

    @Test
    public void broadQuartzPillarAliasMapsEvery112AxisVariant() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10364, Arrays.asList(
            BlockEntry.parse("quartz_block"),
            BlockEntry.parse("chiseled_quartz_block"),
            BlockEntry.parse("quartz_pillar")));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(10364, idMap.getInt(quartzState("default")));
        assertEquals(10364, idMap.getInt(quartzState("chiseled")));
        assertEquals(10364, idMap.getInt(quartzState("lines_x")));
        assertEquals(10364, idMap.getInt(quartzState("lines_y")));
        assertEquals(10364, idMap.getInt(quartzState("lines_z")));
    }

    @Test
    public void quartzPillarAxisPredicatesMapSpecific112AxisVariants() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(310, Collections.singletonList(BlockEntry.parse("quartz_pillar:axis=x")));
        entries.put(311, Collections.singletonList(BlockEntry.parse("quartz_pillar:axis=y")));
        entries.put(312, Collections.singletonList(BlockEntry.parse("quartz_pillar:axis=z")));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(310, idMap.getInt(quartzState("lines_x")));
        assertEquals(311, idMap.getInt(quartzState("lines_y")));
        assertEquals(312, idMap.getInt(quartzState("lines_z")));
        assertEquals(-1, idMap.getInt(quartzState("default")));
        assertEquals(-1, idMap.getInt(quartzState("chiseled")));
    }

    @Test
    public void modernDaylightDetectorBroadEntryMapsBoth112SplitRegistries() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10121, Collections.singletonList(BlockEntry.parse("daylight_detector")));

        assertFalse(BlockMaterialMapping.hasDaylightDetectorSplitEntries(entries));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(10121, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR, 0)));
        assertEquals(10121, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR, 12)));
        assertEquals(10121, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR_INVERTED, 0)));
        assertEquals(10121, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR_INVERTED, 12)));
    }

    @Test
    public void daylightDetectorInvertedPredicateChooses112SplitRegistry() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(200, Collections.singletonList(BlockEntry.parse("daylight_detector:inverted=false:power=4")));
        entries.put(201, Collections.singletonList(BlockEntry.parse("daylight_detector:inverted=true:power=4")));

        assertTrue(BlockMaterialMapping.hasDaylightDetectorSplitEntries(entries));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(200, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR, 4)));
        assertEquals(-1, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR, 5)));
        assertEquals(201, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR_INVERTED, 4)));
        assertEquals(-1, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR_INVERTED, 5)));
    }

    @Test
    public void explicitLegacyInvertedDaylightDetectorEntryPreventsBroadPreemption() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10121, Collections.singletonList(BlockEntry.parse("daylight_detector")));
        entries.put(10122, Collections.singletonList(BlockEntry.parse("daylight_detector_inverted")));

        assertTrue(BlockMaterialMapping.hasDaylightDetectorSplitEntries(entries));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(10121, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR, 0)));
        assertEquals(10121, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR, 12)));
        assertEquals(10122, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR_INVERTED, 0)));
        assertEquals(10122, idMap.getInt(daylightDetectorState(Blocks.DAYLIGHT_DETECTOR_INVERTED, 12)));
    }

    @Test
    public void targetPackModernAnvilDamageNamesResolveTo112AnvilStates() {
        assertResolved("chipped_anvil", "anvil", Collections.singletonMap("damage", "1"));
        assertResolved("damaged_anvil:facing=east", "anvil", predicates("facing", "east", "damage", "2"));
    }

    @Test
    public void targetPackModernWetSpongeResolvesTo112WetSpongeState() {
        assertResolved("wet_sponge", "sponge", Collections.singletonMap("wet", "true"));
        assertResolved("wet_sponge:powered=false", "sponge", predicates("powered", "false", "wet", "true"));
    }

    @Test
    public void targetPackModernStoneSlabAliasesResolveTo112VariantStates() {
        assertResolved("smooth_stone_slab", "stone_slab", Collections.singletonMap("variant", "stone"));
        assertResolved("sandstone_slab", "stone_slab", Collections.singletonMap("variant", "sandstone"));
        assertResolved("cobblestone_slab", "stone_slab", Collections.singletonMap("variant", "cobblestone"));
        assertResolved("brick_slab", "stone_slab", Collections.singletonMap("variant", "brick"));
        assertResolved("stone_brick_slab", "stone_slab", Collections.singletonMap("variant", "stone_brick"));
        assertResolved("nether_brick_slab", "stone_slab", Collections.singletonMap("variant", "nether_brick"));
        assertResolved("quartz_slab", "stone_slab", Collections.singletonMap("variant", "quartz"));
        assertResolved("red_sandstone_slab", "stone_slab2", Collections.singletonMap("variant", "red_sandstone"));
        assertResolved("brick_slab:type=top", "stone_slab", predicates("variant", "brick", "half", "top"));
        assertResolved("brick_slab:type=double", "double_stone_slab", Collections.singletonMap("variant", "brick"));
        assertResolved("red_sandstone_slab:type=bottom", "stone_slab2",
            predicates("variant", "red_sandstone", "half", "bottom"));
        assertResolved("red_sandstone_slab:type=double", "double_stone_slab2",
            Collections.singletonMap("variant", "red_sandstone"));
        assertResolved("cut_red_sandstone_slab", "stone_slab2",
            Collections.singletonMap("variant", "red_sandstone"));
        assertResolved("smooth_red_sandstone_slab:type=top", "stone_slab2",
            predicates("variant", "red_sandstone", "half", "top"));
        assertResolved("smooth_red_sandstone_slab:type=double", "double_stone_slab2",
            Collections.singletonMap("variant", "red_sandstone"));
    }

    @Test
    public void targetPackModernWallAliasesResolveTo112VariantStates() {
        assertResolved("mossy_cobblestone_wall", "cobblestone_wall",
            Collections.singletonMap("variant", "mossy_cobblestone"));
    }

    @Test
    public void targetPackModernOakInteractiveBlocksResolveTo112RegistryNames() {
        assertResolved("oak_fence", "fence", Collections.emptyMap());
        assertResolved("oak_fence_gate", "fence_gate", Collections.emptyMap());
        assertResolved("oak_button", "wooden_button", Collections.emptyMap());
        assertResolved("oak_pressure_plate", "wooden_pressure_plate", Collections.emptyMap());
        assertResolved("oak_trapdoor", "trapdoor", Collections.emptyMap());
        assertResolved("oak_door", "wooden_door", Collections.emptyMap());
    }

    @Test
    public void targetPackTerrainVariantSplitsConstrainBroadDefaultContainers() {
        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10124, Collections.singletonList(BlockEntry.parse("podzol:snowy=true")));
        entries.put(10128, Arrays.asList(
            BlockEntry.parse("dirt"),
            BlockEntry.parse("coarse_dirt"),
            BlockEntry.parse("rooted_dirt")));
        entries.put(10236, Arrays.asList(
            BlockEntry.parse("sand"),
            BlockEntry.parse("red_sand")));
        entries.put(10240, Arrays.asList(
            BlockEntry.parse("sandstone"),
            BlockEntry.parse("chiseled_sandstone"),
            BlockEntry.parse("cut_sandstone")));
        entries.put(10244, Arrays.asList(
            BlockEntry.parse("red_sandstone"),
            BlockEntry.parse("smooth_red_sandstone"),
            BlockEntry.parse("cut_red_sandstone")));
        entries.put(10364, Arrays.asList(
            BlockEntry.parse("quartz_block"),
            BlockEntry.parse("quartz_pillar"),
            BlockEntry.parse("smooth_quartz"),
            BlockEntry.parse("quartz_bricks")));
        entries.put(10105, Arrays.asList(
            BlockEntry.parse("stone_slab"),
            BlockEntry.parse("brick_slab")));
        entries.put(10153, Arrays.asList(
            BlockEntry.parse("cobblestone_wall"),
            BlockEntry.parse("mossy_cobblestone_wall")));
        entries.put(10964, Arrays.asList(
            BlockEntry.parse("sponge"),
            BlockEntry.parse("wet_sponge")));
        entries.put(10037, Arrays.asList(
            BlockEntry.parse("anvil"),
            BlockEntry.parse("chipped_anvil"),
            BlockEntry.parse("damaged_anvil")));

        Set<String> families = BlockMaterialMapping.collectDefaultVariantFamilies(entries);

        assertTrue(families.contains("dirt"));
        assertTrue(families.contains("sand"));
        assertTrue(families.contains("sandstone"));
        assertTrue(families.contains("red_sandstone"));
        assertTrue(families.contains("quartz_block"));
        assertTrue(families.contains("stone_slab"));
        assertTrue(families.contains("cobblestone_wall"));
        assertTrue(families.contains("sponge"));
        assertTrue(families.contains("anvil"));
        assertConstrained("dirt", families, "dirt", Collections.singletonMap("variant", "dirt"));
        assertConstrained("sand", families, "sand", Collections.singletonMap("variant", "sand"));
        assertConstrained("sandstone", families, "sandstone", Collections.singletonMap("type", "sandstone"));
        assertConstrained("red_sandstone", families, "red_sandstone",
            Collections.singletonMap("type", "red_sandstone"));
        assertConstrained("quartz_block", families, "quartz_block", Collections.singletonMap("variant", "default"));
        assertConstrained("stone_slab", families, "stone_slab", Collections.singletonMap("variant", "stone"));
        assertConstrained("cobblestone_wall", families, "cobblestone_wall",
            Collections.singletonMap("variant", "cobblestone"));
        assertConstrained("sponge", families, "sponge", Collections.singletonMap("wet", "false"));
        assertConstrained("anvil", families, "anvil", Collections.singletonMap("damage", "0"));

        BlockEntry dirtWithPredicate = BlockMaterialMapping.constrainBroadDefaultVariantContainer(
            BlockEntry.parse("dirt:snowy=false"), families);
        assertEquals(new NamespacedId("minecraft", "dirt"), dirtWithPredicate.getId());
        assertEquals(Collections.singletonMap("snowy", "false"), dirtWithPredicate.getPropertyPredicates());
    }

    @Test
    public void broadDefaultContainersRemainBroadWithoutSplitVariants() {
        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(7, Arrays.asList(
            BlockEntry.parse("dirt"),
            BlockEntry.parse("sand"),
            BlockEntry.parse("sandstone"),
            BlockEntry.parse("red_sandstone"),
            BlockEntry.parse("quartz_block"),
            BlockEntry.parse("stone_slab"),
            BlockEntry.parse("cobblestone_wall"),
            BlockEntry.parse("sponge"),
            BlockEntry.parse("anvil")));

        Set<String> families = BlockMaterialMapping.collectDefaultVariantFamilies(entries);

        assertFalse(families.contains("dirt"));
        assertFalse(families.contains("sand"));
        assertFalse(families.contains("sandstone"));
        assertFalse(families.contains("red_sandstone"));
        assertFalse(families.contains("quartz_block"));
        assertFalse(families.contains("stone_slab"));
        assertFalse(families.contains("cobblestone_wall"));
        assertFalse(families.contains("sponge"));
        assertFalse(families.contains("anvil"));
        assertUnchangedAfterDefaultConstraint("dirt", families);
        assertUnchangedAfterDefaultConstraint("sand", families);
        assertUnchangedAfterDefaultConstraint("sandstone", families);
        assertUnchangedAfterDefaultConstraint("red_sandstone", families);
        assertUnchangedAfterDefaultConstraint("quartz_block", families);
        assertUnchangedAfterDefaultConstraint("stone_slab", families);
        assertUnchangedAfterDefaultConstraint("cobblestone_wall", families);
        assertUnchangedAfterDefaultConstraint("sponge", families);
        assertUnchangedAfterDefaultConstraint("anvil", families);
    }

    @Test
    public void targetPackModernExactRenamesResolveTo112BlockStates() {
        assertResolved("petrified_oak_slab", "stone_slab", Collections.singletonMap("variant", "wood"));
        assertResolved("nether_quartz_ore", "quartz_ore", Collections.emptyMap());
        assertResolved("magma_block", "magma", Collections.emptyMap());
        assertResolved("nether_portal", "portal", Collections.emptyMap());
        assertResolved("powered_rail", "golden_rail", Collections.emptyMap());
        assertResolved("cobblestone_stairs", "stone_stairs", Collections.emptyMap());
        assertResolved("smooth_red_sandstone_stairs", "red_sandstone_stairs", Collections.emptyMap());
        assertResolved("lily_pad", "waterlily", Collections.emptyMap());
        assertResolved("carved_pumpkin", "pumpkin", Collections.emptyMap());
    }

    @Test
    public void complementaryMushroomStemRowSplits112HugeMushroomStemStates() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10532, Collections.singletonList(BlockEntry.parse("brown_mushroom_block")));
        entries.put(10536, Collections.singletonList(BlockEntry.parse("red_mushroom_block")));
        entries.put(10540, Collections.singletonList(BlockEntry.parse("mushroom_stem")));

        assertTrue(BlockMaterialMapping.hasMushroomStemEntry(entries));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(10532, idMap.getInt(hugeMushroomState(Blocks.BROWN_MUSHROOM_BLOCK, "north")));
        assertEquals(10536, idMap.getInt(hugeMushroomState(Blocks.RED_MUSHROOM_BLOCK, "north")));
        assertEquals(10540, idMap.getInt(hugeMushroomState(Blocks.BROWN_MUSHROOM_BLOCK, "stem")));
        assertEquals(10540, idMap.getInt(hugeMushroomState(Blocks.RED_MUSHROOM_BLOCK, "stem")));
        assertEquals(10540, idMap.getInt(hugeMushroomState(Blocks.BROWN_MUSHROOM_BLOCK, "all_stem")));
        assertEquals(10540, idMap.getInt(hugeMushroomState(Blocks.RED_MUSHROOM_BLOCK, "all_stem")));
    }

    @Test
    public void broadMushroomBlocksRemainBroadWithoutModernStemSplit() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10532, Collections.singletonList(BlockEntry.parse("brown_mushroom_block")));
        entries.put(10536, Collections.singletonList(BlockEntry.parse("red_mushroom_block")));

        assertFalse(BlockMaterialMapping.hasMushroomStemEntry(entries));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(10532, idMap.getInt(hugeMushroomState(Blocks.BROWN_MUSHROOM_BLOCK, "stem")));
        assertEquals(10536, idMap.getInt(hugeMushroomState(Blocks.RED_MUSHROOM_BLOCK, "stem")));
    }

    @Test
    public void mushroomStemWithModernFacePredicatesStaysUnmappedWithout112StateProof() {
        Bootstrap.register();

        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10540, Collections.singletonList(BlockEntry.parse("mushroom_stem:up=true")));

        Object2IntMap<IBlockState> idMap = BlockMaterialMapping.createBlockStateIdMap(entries);

        assertEquals(-1, idMap.getInt(hugeMushroomState(Blocks.BROWN_MUSHROOM_BLOCK, "stem")));
        assertEquals(-1, idMap.getInt(hugeMushroomState(Blocks.RED_MUSHROOM_BLOCK, "stem")));
    }

    @Test
    public void stateSplitFamiliesSkipBroadLegacyContainersBeforeStateMapping() {
        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10080, Arrays.asList(
            BlockEntry.parse("stone"),
            BlockEntry.parse("granite"),
            BlockEntry.parse("stone:variant=stone")));
        entries.put(10032, Arrays.asList(
            BlockEntry.parse("stonebrick"),
            BlockEntry.parse("mossy_stone_bricks")));
        entries.put(10052, Arrays.asList(
            BlockEntry.parse("monster_egg"),
            BlockEntry.parse("infested_stone_bricks")));

        Set<String> families = BlockMaterialMapping.collectBroadLegacyFamilies(entries);

        assertTrue(families.contains("stone"));
        assertTrue(families.contains("stonebrick"));
        assertTrue(families.contains("monster_egg"));
        assertTrue(BlockMaterialMapping.shouldSkipBroadLegacyContainer(BlockEntry.parse("stone"), families));
        assertTrue(BlockMaterialMapping.shouldSkipBroadLegacyContainer(BlockEntry.parse("stonebrick"), families));
        assertTrue(BlockMaterialMapping.shouldSkipBroadLegacyContainer(BlockEntry.parse("monster_egg"), families));
        assertFalse(BlockMaterialMapping.shouldSkipBroadLegacyContainer(BlockEntry.parse("stone:variant=stone"), families));
        assertFalse(BlockMaterialMapping.shouldSkipBroadLegacyContainer(BlockEntry.parse("cobblestone"), families));
        assertFalse(BlockMaterialMapping.shouldSkipBroadLegacyContainer(BlockEntry.parse("prismarine"), families));
    }

    @Test
    public void redstoneSplitFamiliesSkipBroadLegacyContainersBeforeStateMapping() {
        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(10644, Arrays.asList(
            BlockEntry.parse("powered_repeater"),
            BlockEntry.parse("powered_comparator"),
            BlockEntry.parse("repeater:powered=true"),
            BlockEntry.parse("comparator:mode=compare:powered=true")));
        entries.put(10645, Arrays.asList(
            BlockEntry.parse("unpowered_repeater"),
            BlockEntry.parse("unpowered_comparator"),
            BlockEntry.parse("repeater:powered=false"),
            BlockEntry.parse("comparator:mode=compare:powered=false")));
        entries.put(10646, Collections.singletonList(
            BlockEntry.parse("comparator:mode=subtract:powered=false")));

        Set<String> families = BlockMaterialMapping.collectBroadLegacyFamilies(entries);

        assertTrue(families.contains("powered_repeater"));
        assertTrue(families.contains("unpowered_repeater"));
        assertTrue(families.contains("powered_comparator"));
        assertTrue(families.contains("unpowered_comparator"));
        assertTrue(BlockMaterialMapping.shouldSkipBroadLegacyContainer(
            BlockEntry.parse("powered_repeater"), families));
        assertTrue(BlockMaterialMapping.shouldSkipBroadLegacyContainer(
            BlockEntry.parse("unpowered_repeater"), families));
        assertTrue(BlockMaterialMapping.shouldSkipBroadLegacyContainer(
            BlockEntry.parse("powered_comparator"), families));
        assertTrue(BlockMaterialMapping.shouldSkipBroadLegacyContainer(
            BlockEntry.parse("unpowered_comparator"), families));
        assertFalse(BlockMaterialMapping.shouldSkipBroadLegacyContainer(
            BlockEntry.parse("unpowered_comparator:mode=compare"), families));
    }

    @Test
    public void broadLegacyContainersRemainUsableWhenNoSplitEntriesExist() {
        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(7, Arrays.asList(
            BlockEntry.parse("stone"),
            BlockEntry.parse("stonebrick"),
            BlockEntry.parse("monster_egg"),
            BlockEntry.parse("powered_repeater"),
            BlockEntry.parse("unpowered_comparator")));

        Set<String> families = BlockMaterialMapping.collectBroadLegacyFamilies(entries);

        assertFalse(BlockMaterialMapping.shouldSkipBroadLegacyContainer(BlockEntry.parse("stone"), families));
        assertFalse(BlockMaterialMapping.shouldSkipBroadLegacyContainer(BlockEntry.parse("stonebrick"), families));
        assertFalse(BlockMaterialMapping.shouldSkipBroadLegacyContainer(BlockEntry.parse("monster_egg"), families));
        assertFalse(BlockMaterialMapping.shouldSkipBroadLegacyContainer(BlockEntry.parse("powered_repeater"), families));
        assertFalse(BlockMaterialMapping.shouldSkipBroadLegacyContainer(BlockEntry.parse("unpowered_comparator"), families));
    }

    @Test
    public void targetPackModernPottedPlantsResolveTo112FlowerPotContents() {
        assertResolved("flower_pot", "flower_pot", Collections.singletonMap("contents", "empty"));
        assertResolved("potted_dandelion", "flower_pot", Collections.singletonMap("contents", "dandelion"));
        assertResolved("potted_poppy", "flower_pot", Collections.singletonMap("contents", "rose"));
        assertResolved("potted_blue_orchid", "flower_pot", Collections.singletonMap("contents", "blue_orchid"));
        assertResolved("potted_allium", "flower_pot", Collections.singletonMap("contents", "allium"));
        assertResolved("potted_azure_bluet", "flower_pot", Collections.singletonMap("contents", "houstonia"));
        assertResolved("potted_red_tulip", "flower_pot", Collections.singletonMap("contents", "red_tulip"));
        assertResolved("potted_orange_tulip", "flower_pot", Collections.singletonMap("contents", "orange_tulip"));
        assertResolved("potted_white_tulip", "flower_pot", Collections.singletonMap("contents", "white_tulip"));
        assertResolved("potted_pink_tulip", "flower_pot", Collections.singletonMap("contents", "pink_tulip"));
        assertResolved("potted_oxeye_daisy", "flower_pot", Collections.singletonMap("contents", "oxeye_daisy"));
        assertResolved("potted_oak_sapling", "flower_pot", Collections.singletonMap("contents", "oak_sapling"));
        assertResolved("potted_dark_oak_sapling", "flower_pot", Collections.singletonMap("contents", "dark_oak_sapling"));
        assertResolved("potted_red_mushroom", "flower_pot", Collections.singletonMap("contents", "mushroom_red"));
        assertResolved("potted_brown_mushroom", "flower_pot", Collections.singletonMap("contents", "mushroom_brown"));
        assertResolved("potted_dead_bush", "flower_pot", Collections.singletonMap("contents", "dead_bush"));
        assertResolved("potted_fern", "flower_pot", Collections.singletonMap("contents", "fern"));
        assertResolved("potted_cactus", "flower_pot", Collections.singletonMap("contents", "cactus"));
    }

    @Test
    public void targetPackModernDoublePlantsResolveTo112DoublePlantVariants() {
        assertResolved("sunflower:half=lower", "double_plant",
            predicates("half", "lower", "variant", "sunflower"));
        assertResolved("lilac:half=upper", "double_plant",
            predicates("half", "upper", "variant", "syringa"));
        assertResolved("tall_grass:half=lower", "double_plant",
            predicates("half", "lower", "variant", "double_grass"));
        assertResolved("large_fern:half=upper", "double_plant",
            predicates("half", "upper", "variant", "double_fern"));
        assertResolved("rose_bush:half=lower", "double_plant",
            predicates("half", "lower", "variant", "double_rose"));
        assertResolved("peony:half=upper", "double_plant",
            predicates("half", "upper", "variant", "paeonia"));
    }

    @Test
    public void lightGrayGlazedTerracottaUsesLegacySilverRegistryName() {
        assertEquals("silver_glazed_terracotta",
            BlockMaterialMapping.legacyMinecraftBlockName(new NamespacedId("minecraft", "light_gray_glazed_terracotta")));
    }

    @Test
    public void complementaryModernDirectBlockNamesResolveToLegacy112RegistryNames() {
        assertLegacyName("grass_block", "grass");
        assertLegacyName("short_grass", "tallgrass");
        assertLegacyName("dirt_path", "grass_path");
        assertLegacyName("slime_block", "slime");
        assertLegacyName("wall_torch", "torch");
        assertLegacyName("redstone_wall_torch", "redstone_torch");
        assertLegacyName("powered_rail", "golden_rail");
        assertLegacyName("note_block", "noteblock");
        assertLegacyName("spawner", "mob_spawner");
        assertLegacyName("cobweb", "web");
        assertLegacyName("dead_bush", "deadbush");
        assertLegacyName("sugar_cane", "reeds");
        assertLegacyName("bricks", "brick_block");
        assertLegacyName("nether_bricks", "nether_brick");
        assertLegacyName("red_nether_bricks", "red_nether_brick");
        assertLegacyName("end_stone_bricks", "end_bricks");
        assertLegacyName("cobblestone_stairs", "stone_stairs");
        assertLegacyName("lily_pad", "waterlily");
        assertLegacyName("melon", "melon_block");
        assertLegacyName("carved_pumpkin", "pumpkin");
        assertLegacyName("jack_o_lantern", "lit_pumpkin");
        assertLegacyName("oak_fence", "fence");
        assertLegacyName("oak_fence_gate", "fence_gate");
        assertLegacyName("oak_button", "wooden_button");
        assertLegacyName("oak_pressure_plate", "wooden_pressure_plate");
        assertLegacyName("oak_trapdoor", "trapdoor");
        assertLegacyName("oak_door", "wooden_door");
        assertLegacyName("nether_portal", "portal");
    }

    @Test
    public void targetPackModernTileEntityFamiliesResolveTo112GenericRegistries() {
        assertLegacyName("oak_sign", "standing_sign");
        assertLegacyName("spruce_wall_sign", "wall_sign");
        assertLegacyName("dark_oak_hanging_sign", "standing_sign");
        assertLegacyName("birch_wall_hanging_sign", "wall_sign");
        assertLegacyName("white_banner", "standing_banner");
        assertLegacyName("light_gray_wall_banner", "wall_banner");
        assertLegacyName("red_bed", "bed");
        assertLegacyName("skeleton_skull", "skull");
        assertLegacyName("wither_skeleton_wall_skull", "skull");
        assertLegacyName("player_head", "skull");
        assertLegacyName("dragon_wall_head", "skull");

        assertNull(BlockMaterialMapping.legacyMinecraftBlockName(
            new NamespacedId("minecraft", "piston_head")));
    }

    @Test
    public void plainModernShulkerBoxStaysUnmappedWithoutLossless112Registry() {
        BlockEntry plain = BlockMaterialMapping.resolveLegacyBlockEntry(BlockEntry.parse("shulker_box"));
        assertEquals(new NamespacedId("minecraft", "shulker_box"), plain.getId());
        assertEquals(Collections.emptyMap(), plain.getPropertyPredicates());
    }

    @Test
    public void detectsModernColorSplitFamiliesWithoutBootstrappingMinecraftRegistries() {
        Int2ObjectOpenHashMap<List<BlockEntry>> entries = new Int2ObjectOpenHashMap<>();
        entries.put(31000, Arrays.asList(
            BlockEntry.parse("stained_glass"),
            BlockEntry.parse("white_stained_glass"),
            BlockEntry.parse("stained_glass_pane"),
            BlockEntry.parse("white_stained_glass_pane")));
        entries.put(31002, Collections.singletonList(BlockEntry.parse("orange_stained_glass")));
        entries.put(31003, Collections.singletonList(BlockEntry.parse("orange_stained_glass_pane")));

        Set<String> families = BlockMaterialMapping.collectColorSplitFamilies(entries);

        assertTrue(families.contains("stained_glass"));
        assertTrue(families.contains("stained_glass_pane"));
    }

    @Test
    public void complementaryColorSplitGlassSkipsBroadLegacyContainersBeforeStateMapping() {
        Set<String> families = new HashSet<>();
        families.add("stained_glass");
        families.add("stained_glass_pane");

        assertTrue(BlockMaterialMapping.shouldSkipLegacyColorContainer(BlockEntry.parse("stained_glass"), families));
        assertTrue(BlockMaterialMapping.shouldSkipLegacyColorContainer(BlockEntry.parse("stained_glass_pane"), families));
        assertFalse(BlockMaterialMapping.shouldSkipLegacyColorContainer(BlockEntry.parse("white_stained_glass"), families));
        assertFalse(BlockMaterialMapping.shouldSkipLegacyColorContainer(BlockEntry.parse("stained_glass:color=white"), families));
        assertFalse(BlockMaterialMapping.shouldSkipLegacyColorContainer(BlockEntry.parse("wool"), families));
    }

    @Test
    public void nonMinecraftIdsAndUnknownPredicatesAreLeftForNormalLookup() {
        BlockEntry modded = BlockMaterialMapping.resolveLegacyBlockEntry(BlockEntry.parse("examplemod:redstone_ore:lit=true"));
        assertEquals(new NamespacedId("examplemod", "redstone_ore"), modded.getId());
        assertEquals(Collections.singletonMap("lit", "true"), modded.getPropertyPredicates());

        BlockEntry unknown = BlockMaterialMapping.resolveLegacyBlockEntry(BlockEntry.parse("stone:variant=granite"));
        assertEquals(new NamespacedId("minecraft", "stone"), unknown.getId());
        assertEquals(Collections.singletonMap("variant", "granite"), unknown.getPropertyPredicates());
    }

    @Test
    public void makeUpDoubleColonModdedEntriesKeepNamespaceAndBlockName() {
        BlockEntry lightMiddle = BlockEntry.parse("betterendforge::lumecorn:shape=light_middle");
        assertEquals(new NamespacedId("betterendforge", "lumecorn"), lightMiddle.getId());
        assertEquals(Collections.singletonMap("shape", "light_middle"), lightMiddle.getPropertyPredicates());

        BlockEntry purplePolypore = BlockEntry.parse("betterendforge::purple_polypore");
        assertEquals(new NamespacedId("betterendforge", "purple_polypore"), purplePolypore.getId());
        assertEquals(Collections.emptyMap(), purplePolypore.getPropertyPredicates());
    }

    private static void assertResolved(String source, String legacyName, Map<String, String> predicates) {
        BlockEntry resolved = BlockMaterialMapping.resolveLegacyBlockEntry(BlockEntry.parse(source));

        assertEquals(new NamespacedId("minecraft", legacyName), resolved.getId());
        assertEquals(predicates, resolved.getPropertyPredicates());
        assertFalse("resolved split-block aliases should not keep the modern lit predicate",
            resolved.getPropertyPredicates().containsKey("lit"));
    }

    private static void assertLegacyName(String modernName, String legacyName) {
        assertEquals(legacyName,
            BlockMaterialMapping.legacyMinecraftBlockName(new NamespacedId("minecraft", modernName)));
    }

    private static void assertConstrained(String source, Set<String> families, String legacyName,
                                          Map<String, String> predicates) {
        BlockEntry constrained = BlockMaterialMapping.constrainBroadDefaultVariantContainer(
            BlockEntry.parse(source), families);

        assertEquals(new NamespacedId("minecraft", legacyName), constrained.getId());
        assertEquals(predicates, constrained.getPropertyPredicates());
    }

    private static void assertUnchangedAfterDefaultConstraint(String source, Set<String> families) {
        BlockEntry entry = BlockEntry.parse(source);
        BlockEntry constrained = BlockMaterialMapping.constrainBroadDefaultVariantContainer(entry, families);

        assertEquals(entry.getId(), constrained.getId());
        assertEquals(entry.getPropertyPredicates(), constrained.getPropertyPredicates());
    }

    private static IBlockState stemState(Block block, String facing) {
        return stateWithProperty(block.getDefaultState(), "facing", facing);
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

    private static IBlockState hugeMushroomState(Block block, String variant) {
        return stateWithProperty(block.getDefaultState(), "variant", variant);
    }

    private static IBlockState sandstoneState(String type) {
        return stateWithProperty(Blocks.SANDSTONE.getDefaultState(), "type", type);
    }

    private static IBlockState redSandstoneState(String type) {
        return stateWithProperty(Blocks.RED_SANDSTONE.getDefaultState(), "type", type);
    }

    private static IBlockState quartzState(String variant) {
        return stateWithProperty(Blocks.QUARTZ_BLOCK.getDefaultState(), "variant", variant);
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

    private static Map<String, String> predicates(String firstName, String firstValue,
                                                  String secondName, String secondValue) {
        java.util.LinkedHashMap<String, String> predicates = new java.util.LinkedHashMap<>();
        predicates.put(firstName, firstValue);
        predicates.put(secondName, secondValue);
        return predicates;
    }
}
