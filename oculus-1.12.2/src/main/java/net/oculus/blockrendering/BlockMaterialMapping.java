package net.oculus.blockrendering;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.oculus.Oculus;
import net.oculus.shaderpack.materialmap.BlockEntry;
import net.oculus.shaderpack.materialmap.BlockRenderType;
import net.oculus.shaderpack.materialmap.NamespacedId;

/**
 * Builds block/material mappings from OptiFine-style ID maps.
 */
public final class BlockMaterialMapping {
    private static final String[] MODERN_DYE_COLORS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    private static final String[] VANILLA_WOOD_TYPES = {
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak"
    };

    private BlockMaterialMapping() {
    }

    public static Object2IntMap<IBlockState> createBlockStateIdMap(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap) {
        Object2IntOpenHashMap<IBlockState> idMap = new Object2IntOpenHashMap<>();
        idMap.defaultReturnValue(-1);
        Set<String> colorSplitFamilies = collectColorSplitFamilies(blockPropertiesMap);
        Set<String> broadLegacyFamilies = collectBroadLegacyFamilies(blockPropertiesMap);
        Set<String> defaultVariantFamilies = collectDefaultVariantFamilies(blockPropertiesMap);
        boolean hasDaylightDetectorSplitEntries = hasDaylightDetectorSplitEntries(blockPropertiesMap);
        boolean hasMushroomStemEntry = hasMushroomStemEntry(blockPropertiesMap);

        blockPropertiesMap.forEach((intId, entries) -> {
            if (entries == null) {
                return;
            }

            for (BlockEntry entry : entries) {
                addBlockStates(entry, idMap, intId, colorSplitFamilies, broadLegacyFamilies,
                    defaultVariantFamilies, hasDaylightDetectorSplitEntries, hasMushroomStemEntry);
            }
        });

        return idMap;
    }

    public static Map<Block, BlockRenderLayer> createRenderLayerMap(Map<NamespacedId, BlockRenderType> blockRenderTypeMap) {
        Map<Block, BlockRenderLayer> overrides = new HashMap<>();

        blockRenderTypeMap.forEach((id, renderType) -> {
            Block block = lookupBlock(id);
            if (block == null) {
                return;
            }

            BlockRenderLayer layer = convertRenderType(renderType);
            if (layer != null) {
                overrides.put(block, layer);
            }
        });

        return overrides;
    }

    private static void addBlockStates(BlockEntry entry, Object2IntOpenHashMap<IBlockState> idMap, int intId,
                                       Set<String> colorSplitFamilies, Set<String> broadLegacyFamilies,
                                       Set<String> defaultVariantFamilies,
                                       boolean hasDaylightDetectorSplitEntries,
                                       boolean hasMushroomStemEntry) {
        if (shouldSkipLegacyColorContainer(entry, colorSplitFamilies)) {
            return;
        }
        if (shouldSkipBroadLegacyContainer(entry, broadLegacyFamilies)) {
            return;
        }
        if (addDaylightDetectorStates(entry, idMap, intId, hasDaylightDetectorSplitEntries)) {
            return;
        }
        if (addAttachedStemStates(entry, idMap, intId)) {
            return;
        }
        if (addHugeMushroomStates(entry, idMap, intId, hasMushroomStemEntry)) {
            return;
        }
        if (addQuartzPillarStates(entry, idMap, intId)) {
            return;
        }

        BlockEntry resolvedEntry = resolveLegacyBlockEntry(
            constrainBroadDefaultVariantContainer(entry, defaultVariantFamilies));
        Block block = lookupBlock(resolvedEntry.getId());
        if (block == null) {
            return;
        }

        BlockStateContainer container = block.getBlockState();
        Map<String, String> predicates = resolvedEntry.getPropertyPredicates();

        if (predicates.isEmpty()) {
            for (IBlockState state : container.getValidStates()) {
                idMap.putIfAbsent(state, intId);
            }
            return;
        }

        Map<IProperty<?>, String> resolvedPredicates = resolvePropertyPredicates(
            container, predicates, resolvedEntry.getId());

        for (IBlockState state : container.getValidStates()) {
            if (matches(state, resolvedPredicates)) {
                idMap.putIfAbsent(state, intId);
            }
        }
    }

    static BlockEntry resolveLegacyBlockEntry(BlockEntry entry) {
        NamespacedId id = entry.getId();
        Map<String, String> predicates = entry.getPropertyPredicates();

        if (!"minecraft".equals(id.getNamespace())) {
            return entry;
        }

        BlockEntry colorSplitEntry = resolveColorSplitBlockEntry(id.getName(), predicates);
        if (colorSplitEntry != null) {
            return colorSplitEntry;
        }

        BlockEntry woodEntry = resolveWoodBlockEntry(id.getName(), predicates);
        if (woodEntry != null) {
            return woodEntry;
        }

        BlockEntry stoneEntry = resolveStoneBlockEntry(id.getName(), predicates);
        if (stoneEntry != null) {
            return stoneEntry;
        }

        BlockEntry terrainEntry = resolveTerrainVariantBlockEntry(id.getName(), predicates);
        if (terrainEntry != null) {
            return terrainEntry;
        }

        BlockEntry anvilEntry = resolveAnvilBlockEntry(id.getName(), predicates);
        if (anvilEntry != null) {
            return anvilEntry;
        }

        BlockEntry spongeEntry = resolveSpongeBlockEntry(id.getName(), predicates);
        if (spongeEntry != null) {
            return spongeEntry;
        }

        BlockEntry slabEntry = resolveStoneSlabBlockEntry(id.getName(), predicates);
        if (slabEntry != null) {
            return slabEntry;
        }

        BlockEntry wallEntry = resolveWallBlockEntry(id.getName(), predicates);
        if (wallEntry != null) {
            return wallEntry;
        }

        BlockEntry plantEntry = resolvePlantBlockEntry(id.getName(), predicates);
        if (plantEntry != null) {
            return plantEntry;
        }

        String doublePlantVariant = legacyDoublePlantVariant(id.getName());
        if (doublePlantVariant != null) {
            return new BlockEntry(new NamespacedId("minecraft", "double_plant"),
                withPredicate(predicates, "variant", doublePlantVariant));
        }

        if ("terracotta".equals(id.getName())) {
            return new BlockEntry(new NamespacedId("minecraft", "hardened_clay"), predicates);
        }

        if ("petrified_oak_slab".equals(id.getName())) {
            return new BlockEntry(new NamespacedId("minecraft", "stone_slab"),
                withPredicate(predicates, "variant", "wood"));
        }

        String lit = predicates.get("lit");
        String litLegacyName = legacyLitBlockName(id.getName(), lit);
        if (litLegacyName != null) {
            return new BlockEntry(new NamespacedId("minecraft", litLegacyName), withoutPredicate(predicates, "lit"));
        }

        BlockEntry poweredRedstoneEntry = resolvePoweredRedstoneBlockEntry(id.getName(), predicates);
        if (poweredRedstoneEntry != null) {
            return poweredRedstoneEntry;
        }

        if ("snow".equals(id.getName()) && predicates.containsKey("layers")) {
            return new BlockEntry(new NamespacedId("minecraft", "snow_layer"), predicates);
        }

        String directLegacyName = directLegacyMinecraftBlockName(id.getName());
        if (directLegacyName != null) {
            return new BlockEntry(new NamespacedId("minecraft", directLegacyName), predicates);
        }

        return entry;
    }

    static Set<String> collectColorSplitFamilies(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap) {
        Set<String> families = new HashSet<>();

        blockPropertiesMap.forEach((intId, entries) -> {
            if (entries == null) {
                return;
            }

            for (BlockEntry entry : entries) {
                if (!"minecraft".equals(entry.getId().getNamespace())) {
                    continue;
                }

                String legacyFamily = legacyColorContainerName(entry.getId().getName());
                if (legacyFamily != null) {
                    families.add(legacyFamily);
                }
            }
        });

        return families;
    }

    static Set<String> collectBroadLegacyFamilies(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap) {
        Set<String> families = collectColorSplitFamilies(blockPropertiesMap);

        blockPropertiesMap.forEach((intId, entries) -> {
            if (entries == null) {
                return;
            }

            for (BlockEntry entry : entries) {
                NamespacedId id = entry.getId();
                if (!"minecraft".equals(id.getNamespace())) {
                    continue;
                }

                String family = legacyVariantContainerName(id.getName(), entry.getPropertyPredicates());
                if (family != null) {
                    families.add(family);
                }
            }
        });

        return families;
    }

    static Set<String> collectDefaultVariantFamilies(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap) {
        Set<String> families = new HashSet<>();

        blockPropertiesMap.forEach((intId, entries) -> {
            if (entries == null) {
                return;
            }

            for (BlockEntry entry : entries) {
                NamespacedId id = entry.getId();
                if (!"minecraft".equals(id.getNamespace())) {
                    continue;
                }

                String family = defaultVariantContainerName(id.getName(), entry.getPropertyPredicates());
                if (family != null) {
                    families.add(family);
                }
            }
        });

        return families;
    }

    static boolean shouldSkipLegacyColorContainer(BlockEntry entry, Set<String> colorSplitFamilies) {
        NamespacedId id = entry.getId();
        return "minecraft".equals(id.getNamespace())
            && entry.getPropertyPredicates().isEmpty()
            && colorSplitFamilies.contains(id.getName());
    }

    static boolean shouldSkipBroadLegacyContainer(BlockEntry entry, Set<String> broadLegacyFamilies) {
        NamespacedId id = entry.getId();
        return "minecraft".equals(id.getNamespace())
            && entry.getPropertyPredicates().isEmpty()
            && broadLegacyFamilies.contains(id.getName());
    }

    static boolean hasDaylightDetectorSplitEntries(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap) {
        final boolean[] found = {false};

        blockPropertiesMap.forEach((intId, entries) -> {
            if (entries == null || found[0]) {
                return;
            }

            for (BlockEntry entry : entries) {
                NamespacedId id = entry.getId();
                if (!"minecraft".equals(id.getNamespace())) {
                    continue;
                }
                if ("daylight_detector_inverted".equals(id.getName())
                    || ("daylight_detector".equals(id.getName())
                    && entry.getPropertyPredicates().containsKey("inverted"))) {
                    found[0] = true;
                    return;
                }
            }
        });

        return found[0];
    }

    static boolean hasMushroomStemEntry(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap) {
        final boolean[] found = {false};

        blockPropertiesMap.forEach((intId, entries) -> {
            if (entries == null || found[0]) {
                return;
            }

            for (BlockEntry entry : entries) {
                NamespacedId id = entry.getId();
                if ("minecraft".equals(id.getNamespace()) && "mushroom_stem".equals(id.getName())) {
                    found[0] = true;
                    return;
                }
            }
        });

        return found[0];
    }

    static BlockEntry constrainBroadDefaultVariantContainer(BlockEntry entry, Set<String> defaultVariantFamilies) {
        NamespacedId id = entry.getId();
        if (!"minecraft".equals(id.getNamespace()) || !entry.getPropertyPredicates().isEmpty()
            || !defaultVariantFamilies.contains(id.getName())) {
            return entry;
        }

        String defaultProperty = defaultVariantProperty(id.getName());
        String defaultValue = defaultVariantValue(id.getName());
        if (defaultProperty == null || defaultValue == null) {
            return entry;
        }

        return new BlockEntry(id, Collections.singletonMap(defaultProperty, defaultValue));
    }

    private static BlockEntry resolveColorSplitBlockEntry(String name, Map<String, String> predicates) {
        String legacyFamily = legacyColorContainerName(name);
        if (legacyFamily == null || predicates.containsKey("color")) {
            return null;
        }

        return new BlockEntry(
            new NamespacedId("minecraft", legacyFamily),
            withPredicate(predicates, "color", legacyColorName(colorPrefix(name))));
    }

    private static String legacyColorContainerName(String name) {
        for (String color : MODERN_DYE_COLORS) {
            if (name.equals(color + "_stained_glass")) {
                return "stained_glass";
            }
            if (name.equals(color + "_stained_glass_pane")) {
                return "stained_glass_pane";
            }
            if (name.equals(color + "_wool")) {
                return "wool";
            }
            if (name.equals(color + "_carpet")) {
                return "carpet";
            }
            if (name.equals(color + "_concrete")) {
                return "concrete";
            }
            if (name.equals(color + "_concrete_powder")) {
                return "concrete_powder";
            }
            if (name.equals(color + "_terracotta")) {
                return "stained_hardened_clay";
            }
        }

        return null;
    }

    private static String colorPrefix(String name) {
        for (String color : MODERN_DYE_COLORS) {
            if (name.startsWith(color + "_")) {
                return color;
            }
        }

        return name;
    }

    private static String legacyColorName(String color) {
        return "light_gray".equals(color) ? "silver" : color;
    }

    private static BlockEntry resolveWoodBlockEntry(String name, Map<String, String> predicates) {
        String planksType = legacyWoodType(name, "_planks");
        if (planksType != null) {
            return new BlockEntry(new NamespacedId("minecraft", "planks"),
                withPredicate(predicates, "variant", planksType));
        }

        String logType = legacyWoodType(name, "_log");
        if (logType != null) {
            return new BlockEntry(new NamespacedId("minecraft", legacyLogBlockName(logType)),
                withPredicate(predicates, "variant", logType));
        }

        String woodType = legacyWoodType(name, "_wood");
        if (woodType != null) {
            return new BlockEntry(new NamespacedId("minecraft", legacyLogBlockName(woodType)),
                withDefaultPredicate(withPredicate(predicates, "variant", woodType), "axis", "none"));
        }

        String leavesType = legacyWoodType(name, "_leaves");
        if (leavesType != null) {
            return new BlockEntry(new NamespacedId("minecraft", legacyLeavesBlockName(leavesType)),
                withPredicate(predicates, "variant", leavesType));
        }

        String slabType = legacyWoodType(name, "_slab");
        if (slabType != null) {
            return new BlockEntry(new NamespacedId("minecraft", "wooden_slab"),
                withPredicate(predicates, "variant", slabType));
        }

        return null;
    }

    private static String legacyWoodType(String name, String suffix) {
        for (String type : VANILLA_WOOD_TYPES) {
            if (name.equals(type + suffix)) {
                return type;
            }
        }

        return null;
    }

    private static String legacyLogBlockName(String woodType) {
        return isNewWoodType(woodType) ? "log2" : "log";
    }

    private static String legacyLeavesBlockName(String woodType) {
        return isNewWoodType(woodType) ? "leaves2" : "leaves";
    }

    private static boolean isNewWoodType(String woodType) {
        return "acacia".equals(woodType) || "dark_oak".equals(woodType);
    }

    private static BlockEntry resolveStoneBlockEntry(String name, Map<String, String> predicates) {
        String stoneVariant = legacyStoneVariant(name);
        if (stoneVariant != null) {
            return new BlockEntry(new NamespacedId("minecraft", "stone"),
                withPredicate(predicates, "variant", stoneVariant));
        }

        String stoneBrickVariant = legacyStoneBrickVariant(name);
        if (stoneBrickVariant != null) {
            return new BlockEntry(new NamespacedId("minecraft", "stonebrick"),
                withPredicate(predicates, "variant", stoneBrickVariant));
        }

        String infestedVariant = legacyInfestedVariant(name);
        if (infestedVariant != null) {
            return new BlockEntry(new NamespacedId("minecraft", "monster_egg"),
                withPredicate(predicates, "variant", infestedVariant));
        }

        String prismarineVariant = legacyPrismarineVariant(name);
        if (prismarineVariant != null) {
            return new BlockEntry(new NamespacedId("minecraft", "prismarine"),
                withPredicate(predicates, "variant", prismarineVariant));
        }

        return null;
    }

    private static String legacyVariantContainerName(String name, Map<String, String> predicates) {
        if ("stone".equals(name) && predicates.containsKey("variant")) {
            return "stone";
        }
        if ("stonebrick".equals(name) && predicates.containsKey("variant")) {
            return "stonebrick";
        }
        if ("monster_egg".equals(name) && predicates.containsKey("variant")) {
            return "monster_egg";
        }
        if (legacyStoneVariant(name) != null) {
            return "stone";
        }
        if (legacyStoneBrickVariant(name) != null) {
            return "stonebrick";
        }
        if (legacyInfestedVariant(name) != null) {
            return "monster_egg";
        }
        if ("snow".equals(name) && predicates.containsKey("layers")) {
            return "snow_layer";
        }
        String poweredRedstoneContainer = legacyPoweredRedstoneContainerName(name, predicates);
        if (poweredRedstoneContainer != null) {
            return poweredRedstoneContainer;
        }

        return null;
    }

    private static String legacyStoneVariant(String name) {
        if ("granite".equals(name)) {
            return "granite";
        }
        if ("diorite".equals(name)) {
            return "diorite";
        }
        if ("andesite".equals(name)) {
            return "andesite";
        }
        if ("polished_granite".equals(name)) {
            return "smooth_granite";
        }
        if ("polished_diorite".equals(name)) {
            return "smooth_diorite";
        }
        if ("polished_andesite".equals(name)) {
            return "smooth_andesite";
        }

        return null;
    }

    private static String legacyStoneBrickVariant(String name) {
        if ("stone_bricks".equals(name)) {
            return "stonebrick";
        }
        if ("mossy_stone_bricks".equals(name)) {
            return "mossy_stonebrick";
        }
        if ("cracked_stone_bricks".equals(name)) {
            return "cracked_stonebrick";
        }
        if ("chiseled_stone_bricks".equals(name)) {
            return "chiseled_stonebrick";
        }

        return null;
    }

    private static String legacyInfestedVariant(String name) {
        if ("infested_stone".equals(name)) {
            return "stone";
        }
        if ("infested_cobblestone".equals(name)) {
            return "cobblestone";
        }
        if ("infested_stone_bricks".equals(name)) {
            return "stone_brick";
        }
        if ("infested_mossy_stone_bricks".equals(name)) {
            return "mossy_brick";
        }
        if ("infested_cracked_stone_bricks".equals(name)) {
            return "cracked_brick";
        }
        if ("infested_chiseled_stone_bricks".equals(name)) {
            return "chiseled_brick";
        }

        return null;
    }

    private static String legacyPrismarineVariant(String name) {
        if ("prismarine".equals(name)) {
            return "rough";
        }
        if ("prismarine_bricks".equals(name)) {
            return "bricks";
        }
        if ("dark_prismarine".equals(name)) {
            return "dark";
        }

        return null;
    }

    private static String defaultVariantContainerName(String name, Map<String, String> predicates) {
        if ("dirt".equals(name)) {
            String variant = predicates.get("variant");
            return variant != null && !"dirt".equals(variant) ? "dirt" : null;
        }
        if ("sand".equals(name)) {
            String variant = predicates.get("variant");
            return variant != null && !"sand".equals(variant) ? "sand" : null;
        }
        if ("sandstone".equals(name)) {
            String type = predicates.get("type");
            return type != null && !"sandstone".equals(type) ? "sandstone" : null;
        }
        if ("red_sandstone".equals(name)) {
            String type = predicates.get("type");
            return type != null && !"red_sandstone".equals(type) ? "red_sandstone" : null;
        }
        if ("quartz_block".equals(name)) {
            String variant = predicates.get("variant");
            return variant != null && !"default".equals(variant) ? "quartz_block" : null;
        }
        if ("stone_slab".equals(name)) {
            String variant = predicates.get("variant");
            return variant != null && !"stone".equals(variant) ? "stone_slab" : null;
        }
        if ("cobblestone_wall".equals(name)) {
            String variant = predicates.get("variant");
            return variant != null && !"cobblestone".equals(variant) ? "cobblestone_wall" : null;
        }
        if ("sponge".equals(name)) {
            String wet = predicates.get("wet");
            return wet != null && !"false".equals(wet) ? "sponge" : null;
        }
        if ("anvil".equals(name)) {
            String damage = predicates.get("damage");
            return damage != null && !"0".equals(damage) ? "anvil" : null;
        }

        if (legacyDirtVariant(name) != null || "rooted_dirt".equals(name)) {
            return "dirt";
        }
        if (legacySandVariant(name) != null) {
            return "sand";
        }
        if (legacySandstoneVariant(name) != null || "cut_sandstone".equals(name)) {
            return "sandstone";
        }
        if (legacyRedSandstoneVariant(name) != null || "cut_red_sandstone".equals(name)) {
            return "red_sandstone";
        }
        if (legacyQuartzVariant(name) != null || "smooth_quartz".equals(name) || "quartz_bricks".equals(name)) {
            return "quartz_block";
        }
        String stoneSlabVariant = legacyStoneSlabVariant(name);
        if (stoneSlabVariant != null && !"stone".equals(stoneSlabVariant)) {
            return "stone_slab";
        }
        if (legacyCobblestoneWallVariant(name) != null) {
            return "cobblestone_wall";
        }
        if (legacySpongeWetPredicate(name) != null) {
            return "sponge";
        }
        if (legacyAnvilDamage(name) != null) {
            return "anvil";
        }
        String attachedStemBlockName = legacyAttachedStemBlockName(name);
        if (attachedStemBlockName != null) {
            return attachedStemBlockName;
        }

        return null;
    }

    private static String defaultVariantProperty(String name) {
        if ("sandstone".equals(name) || "red_sandstone".equals(name)) {
            return "type";
        }
        if ("sponge".equals(name)) {
            return "wet";
        }
        if ("anvil".equals(name)) {
            return "damage";
        }
        if ("dirt".equals(name) || "sand".equals(name) || "quartz_block".equals(name)
            || "stone_slab".equals(name) || "cobblestone_wall".equals(name)) {
            return "variant";
        }
        if ("pumpkin_stem".equals(name) || "melon_stem".equals(name)) {
            return "facing";
        }

        return null;
    }

    private static String defaultVariantValue(String name) {
        if ("dirt".equals(name)) {
            return "dirt";
        }
        if ("sand".equals(name)) {
            return "sand";
        }
        if ("sandstone".equals(name)) {
            return "sandstone";
        }
        if ("red_sandstone".equals(name)) {
            return "red_sandstone";
        }
        if ("quartz_block".equals(name)) {
            return "default";
        }
        if ("stone_slab".equals(name)) {
            return "stone";
        }
        if ("cobblestone_wall".equals(name)) {
            return "cobblestone";
        }
        if ("sponge".equals(name)) {
            return "false";
        }
        if ("anvil".equals(name)) {
            return "0";
        }
        if ("pumpkin_stem".equals(name) || "melon_stem".equals(name)) {
            return "up";
        }

        return null;
    }

    private static BlockEntry resolveTerrainVariantBlockEntry(String name, Map<String, String> predicates) {
        String dirtVariant = legacyDirtVariant(name);
        if (dirtVariant != null) {
            return new BlockEntry(new NamespacedId("minecraft", "dirt"),
                withPredicate(predicates, "variant", dirtVariant));
        }

        String sandVariant = legacySandVariant(name);
        if (sandVariant != null) {
            return new BlockEntry(new NamespacedId("minecraft", "sand"),
                withPredicate(predicates, "variant", sandVariant));
        }

        String sandstoneVariant = legacySandstoneVariant(name);
        if (sandstoneVariant != null) {
            return new BlockEntry(new NamespacedId("minecraft", "sandstone"),
                withPredicate(predicates, "type", sandstoneVariant));
        }

        String redSandstoneVariant = legacyRedSandstoneVariant(name);
        if (redSandstoneVariant != null) {
            return new BlockEntry(new NamespacedId("minecraft", "red_sandstone"),
                withPredicate(predicates, "type", redSandstoneVariant));
        }

        String quartzVariant = legacyQuartzVariant(name, predicates);
        if (quartzVariant != null) {
            return new BlockEntry(new NamespacedId("minecraft", "quartz_block"),
                withPredicate(withoutPredicate(predicates, "axis"), "variant", quartzVariant));
        }

        return null;
    }

    private static BlockEntry resolveAnvilBlockEntry(String name, Map<String, String> predicates) {
        String damage = legacyAnvilDamage(name);
        if (damage == null) {
            return null;
        }

        return new BlockEntry(new NamespacedId("minecraft", "anvil"),
            withPredicate(predicates, "damage", damage));
    }

    private static String legacyAnvilDamage(String name) {
        if ("chipped_anvil".equals(name)) {
            return "1";
        }
        if ("damaged_anvil".equals(name)) {
            return "2";
        }

        return null;
    }

    private static BlockEntry resolveSpongeBlockEntry(String name, Map<String, String> predicates) {
        String wet = legacySpongeWetPredicate(name);
        if (wet == null) {
            return null;
        }

        return new BlockEntry(new NamespacedId("minecraft", "sponge"),
            withPredicate(predicates, "wet", wet));
    }

    private static String legacySpongeWetPredicate(String name) {
        if ("wet_sponge".equals(name)) {
            return "true";
        }

        return null;
    }

    private static boolean addDaylightDetectorStates(BlockEntry entry, Object2IntOpenHashMap<IBlockState> idMap,
                                                     int intId, boolean hasDaylightDetectorSplitEntries) {
        NamespacedId id = entry.getId();
        if (!"minecraft".equals(id.getNamespace()) || !"daylight_detector".equals(id.getName())) {
            return false;
        }

        Map<String, String> predicates = entry.getPropertyPredicates();
        String inverted = predicates.get("inverted");
        if (inverted == null) {
            if (hasDaylightDetectorSplitEntries) {
                return false;
            }

            Map<String, String> remainingPredicates = withoutPredicate(predicates, "inverted");
            addStatesForBlock(new NamespacedId("minecraft", "daylight_detector"), remainingPredicates, idMap, intId);
            addStatesForBlock(new NamespacedId("minecraft", "daylight_detector_inverted"), remainingPredicates,
                idMap, intId);
            return true;
        }

        Boolean invertedValue = parseBooleanPredicate(inverted);
        if (invertedValue == null) {
            return false;
        }

        addStatesForBlock(
            new NamespacedId("minecraft", invertedValue ? "daylight_detector_inverted" : "daylight_detector"),
            withoutPredicate(predicates, "inverted"), idMap, intId);
        return true;
    }

    private static void addStatesForBlock(NamespacedId id, Map<String, String> predicates,
                                          Object2IntOpenHashMap<IBlockState> idMap, int intId) {
        Block block = lookupBlock(id);
        if (block == null) {
            return;
        }

        BlockStateContainer container = block.getBlockState();
        Map<IProperty<?>, String> resolvedPredicates = resolvePropertyPredicates(container, predicates, id);
        for (IBlockState state : container.getValidStates()) {
            if (matches(state, resolvedPredicates)) {
                idMap.putIfAbsent(state, intId);
            }
        }
    }

    private static boolean addAttachedStemStates(BlockEntry entry, Object2IntOpenHashMap<IBlockState> idMap, int intId) {
        NamespacedId id = entry.getId();
        if (!"minecraft".equals(id.getNamespace())) {
            return false;
        }

        String legacyStemBlockName = legacyAttachedStemBlockName(id.getName());
        if (legacyStemBlockName == null) {
            return false;
        }

        Block block = lookupBlock(new NamespacedId("minecraft", legacyStemBlockName));
        if (block == null) {
            return true;
        }

        BlockStateContainer container = block.getBlockState();
        Map<String, String> predicates = entry.getPropertyPredicates();
        Map<IProperty<?>, String> resolvedPredicates = resolvePropertyPredicates(container, predicates, id);
        IProperty<?> facingProperty = container.getProperty("facing");
        String requestedFacing = predicates.get("facing");

        for (IBlockState state : container.getValidStates()) {
            if (!matches(state, resolvedPredicates)) {
                continue;
            }
            if (requestedFacing == null && !isHorizontalFacing(state, facingProperty)) {
                continue;
            }
            idMap.putIfAbsent(state, intId);
        }

        return true;
    }

    private static boolean addQuartzPillarStates(BlockEntry entry, Object2IntOpenHashMap<IBlockState> idMap, int intId) {
        NamespacedId id = entry.getId();
        if (!"minecraft".equals(id.getNamespace()) || !"quartz_pillar".equals(id.getName())) {
            return false;
        }

        Block block = lookupBlock(new NamespacedId("minecraft", "quartz_block"));
        if (block == null) {
            return true;
        }

        Set<String> variants = legacyQuartzPillarVariants(entry.getPropertyPredicates().get("axis"));
        if (variants.isEmpty()) {
            return true;
        }

        BlockStateContainer container = block.getBlockState();
        Map<String, String> predicates = withoutPredicate(entry.getPropertyPredicates(), "axis");
        Map<IProperty<?>, String> resolvedPredicates = resolvePropertyPredicates(container, predicates, id);
        IProperty<?> variantProperty = container.getProperty("variant");
        for (IBlockState state : container.getValidStates()) {
            if (!matches(state, resolvedPredicates)) {
                continue;
            }

            String variant = propertyValueName(variantProperty, state.getValue(variantProperty));
            if (variants.contains(variant)) {
                idMap.putIfAbsent(state, intId);
            }
        }

        return true;
    }

    private static Set<String> legacyQuartzPillarVariants(String axis) {
        if ("x".equals(axis)) {
            return Collections.singleton("lines_x");
        }
        if ("y".equals(axis)) {
            return Collections.singleton("lines_y");
        }
        if ("z".equals(axis)) {
            return Collections.singleton("lines_z");
        }
        if (axis == null) {
            Set<String> variants = new HashSet<>();
            variants.add("lines_x");
            variants.add("lines_y");
            variants.add("lines_z");
            return variants;
        }

        return Collections.emptySet();
    }

    private static boolean addHugeMushroomStates(BlockEntry entry, Object2IntOpenHashMap<IBlockState> idMap,
                                                 int intId, boolean hasMushroomStemEntry) {
        NamespacedId id = entry.getId();
        if (!"minecraft".equals(id.getNamespace())) {
            return false;
        }

        String name = id.getName();
        if ("mushroom_stem".equals(name)) {
            if (!entry.getPropertyPredicates().isEmpty()) {
                return false;
            }
            addHugeMushroomStemStates("brown_mushroom_block", idMap, intId);
            addHugeMushroomStemStates("red_mushroom_block", idMap, intId);
            return true;
        }

        if (!hasMushroomStemEntry || !entry.getPropertyPredicates().isEmpty()
            || (!"brown_mushroom_block".equals(name) && !"red_mushroom_block".equals(name))) {
            return false;
        }

        Block block = lookupBlock(id);
        if (block == null) {
            return true;
        }

        BlockStateContainer container = block.getBlockState();
        IProperty<?> variantProperty = container.getProperty("variant");
        for (IBlockState state : container.getValidStates()) {
            if (isHugeMushroomStemVariant(state, variantProperty)) {
                continue;
            }
            idMap.putIfAbsent(state, intId);
        }

        return true;
    }

    private static void addHugeMushroomStemStates(String blockName, Object2IntOpenHashMap<IBlockState> idMap,
                                                  int intId) {
        Block block = lookupBlock(new NamespacedId("minecraft", blockName));
        if (block == null) {
            return;
        }

        BlockStateContainer container = block.getBlockState();
        IProperty<?> variantProperty = container.getProperty("variant");
        for (IBlockState state : container.getValidStates()) {
            if (isHugeMushroomStemVariant(state, variantProperty)) {
                idMap.putIfAbsent(state, intId);
            }
        }
    }

    private static boolean isHugeMushroomStemVariant(IBlockState state, IProperty<?> variantProperty) {
        if (variantProperty == null) {
            return false;
        }

        String variant = propertyValueName(variantProperty, state.getValue(variantProperty));
        return "stem".equals(variant) || "all_stem".equals(variant);
    }

    private static Map<IProperty<?>, String> resolvePropertyPredicates(BlockStateContainer container,
                                                                       Map<String, String> predicates,
                                                                       NamespacedId id) {
        Map<IProperty<?>, String> resolvedPredicates = new HashMap<>();
        predicates.forEach((name, value) -> {
            IProperty<?> property = container.getProperty(name);
            if (property == null) {
                Oculus.LOGGER.warn("Unknown block property '{}' on {} while building block ID map", name, id);
                return;
            }
            resolvedPredicates.put(property, value);
        });
        return resolvedPredicates;
    }

    private static boolean isHorizontalFacing(IBlockState state, IProperty<?> facingProperty) {
        if (facingProperty == null) {
            return false;
        }

        Comparable<?> value = state.getValue(facingProperty);
        String actual = propertyValueName(facingProperty, value);
        return "north".equals(actual) || "south".equals(actual)
            || "east".equals(actual) || "west".equals(actual);
    }

    private static String legacyAttachedStemBlockName(String name) {
        if ("attached_pumpkin_stem".equals(name)) {
            return "pumpkin_stem";
        }
        if ("attached_melon_stem".equals(name)) {
            return "melon_stem";
        }

        return null;
    }

    private static String legacyDirtVariant(String name) {
        if ("coarse_dirt".equals(name)) {
            return "coarse_dirt";
        }
        if ("podzol".equals(name)) {
            return "podzol";
        }

        return null;
    }

    private static String legacySandVariant(String name) {
        if ("red_sand".equals(name)) {
            return "red_sand";
        }

        return null;
    }

    private static String legacySandstoneVariant(String name) {
        if ("chiseled_sandstone".equals(name)) {
            return "chiseled_sandstone";
        }
        if ("cut_sandstone".equals(name)) {
            return "smooth_sandstone";
        }
        if ("smooth_sandstone".equals(name)) {
            return "smooth_sandstone";
        }

        return null;
    }

    private static String legacyRedSandstoneVariant(String name) {
        if ("chiseled_red_sandstone".equals(name)) {
            return "chiseled_red_sandstone";
        }
        if ("cut_red_sandstone".equals(name)) {
            return "smooth_red_sandstone";
        }
        if ("smooth_red_sandstone".equals(name)) {
            return "smooth_red_sandstone";
        }

        return null;
    }

    private static String legacyQuartzVariant(String name) {
        return legacyQuartzVariant(name, Collections.emptyMap());
    }

    private static String legacyQuartzVariant(String name, Map<String, String> predicates) {
        if ("chiseled_quartz_block".equals(name)) {
            return "chiseled";
        }
        if ("quartz_pillar".equals(name)) {
            String axis = predicates.get("axis");
            if ("x".equals(axis)) {
                return "lines_x";
            }
            if (axis == null || "y".equals(axis)) {
                return "lines_y";
            }
            if ("z".equals(axis)) {
                return "lines_z";
            }
        }

        return null;
    }

    private static BlockEntry resolveStoneSlabBlockEntry(String name, Map<String, String> predicates) {
        String stoneSlabVariant = legacyStoneSlabVariant(name);
        if (stoneSlabVariant != null) {
            return resolveSlabBlockEntry("stone_slab", "double_stone_slab", stoneSlabVariant, predicates);
        }

        String newStoneSlabVariant = legacyStoneSlabNewVariant(name);
        if (newStoneSlabVariant != null) {
            return resolveSlabBlockEntry("stone_slab2", "double_stone_slab2", newStoneSlabVariant, predicates);
        }

        return null;
    }

    private static BlockEntry resolveSlabBlockEntry(String slabName, String doubleSlabName, String variant,
                                                    Map<String, String> predicates) {
        String type = predicates.get("type");
        if ("double".equals(type)) {
            return new BlockEntry(new NamespacedId("minecraft", doubleSlabName),
                withPredicate(withoutPredicate(predicates, "type"), "variant", variant));
        }

        Map<String, String> legacyPredicates =
            ("top".equals(type) || "bottom".equals(type)) ? withoutPredicate(predicates, "type") : predicates;
        legacyPredicates = withPredicate(legacyPredicates, "variant", variant);
        if ("top".equals(type) || "bottom".equals(type)) {
            legacyPredicates = withPredicate(legacyPredicates, "half", type);
        }

        return new BlockEntry(new NamespacedId("minecraft", slabName), legacyPredicates);
    }

    private static String legacyStoneSlabVariant(String name) {
        if ("smooth_stone_slab".equals(name)) {
            return "stone";
        }
        if ("sandstone_slab".equals(name)) {
            return "sandstone";
        }
        if ("cobblestone_slab".equals(name)) {
            return "cobblestone";
        }
        if ("brick_slab".equals(name)) {
            return "brick";
        }
        if ("stone_brick_slab".equals(name)) {
            return "stone_brick";
        }
        if ("nether_brick_slab".equals(name)) {
            return "nether_brick";
        }
        if ("quartz_slab".equals(name)) {
            return "quartz";
        }

        return null;
    }

    private static String legacyStoneSlabNewVariant(String name) {
        if ("red_sandstone_slab".equals(name) || "cut_red_sandstone_slab".equals(name)
            || "smooth_red_sandstone_slab".equals(name)) {
            return "red_sandstone";
        }

        return null;
    }

    private static BlockEntry resolveWallBlockEntry(String name, Map<String, String> predicates) {
        String cobblestoneWallVariant = legacyCobblestoneWallVariant(name);
        if (cobblestoneWallVariant != null) {
            return new BlockEntry(new NamespacedId("minecraft", "cobblestone_wall"),
                withPredicate(predicates, "variant", cobblestoneWallVariant));
        }

        return null;
    }

    private static String legacyCobblestoneWallVariant(String name) {
        if ("mossy_cobblestone_wall".equals(name)) {
            return "mossy_cobblestone";
        }

        return null;
    }

    private static BlockEntry resolvePlantBlockEntry(String name, Map<String, String> predicates) {
        String tallGrassType = legacyTallGrassType(name, predicates);
        if (tallGrassType != null) {
            return new BlockEntry(new NamespacedId("minecraft", "tallgrass"),
                withPredicate(predicates, "type", tallGrassType));
        }

        String flowerBlock = legacyFlowerBlockName(name);
        if (flowerBlock != null) {
            return new BlockEntry(new NamespacedId("minecraft", flowerBlock),
                withPredicate(predicates, "type", legacyFlowerType(name)));
        }

        String saplingType = legacySaplingType(name);
        if (saplingType != null) {
            return new BlockEntry(new NamespacedId("minecraft", "sapling"),
                withPredicate(predicates, "type", saplingType));
        }

        String pottedContent = legacyFlowerPotContent(name);
        if (pottedContent != null) {
            return new BlockEntry(new NamespacedId("minecraft", "flower_pot"),
                withPredicate(predicates, "contents", pottedContent));
        }

        if ("flower_pot".equals(name) && predicates.isEmpty()) {
            return new BlockEntry(new NamespacedId("minecraft", "flower_pot"),
                Collections.singletonMap("contents", "empty"));
        }

        return null;
    }

    private static String legacyTallGrassType(String name, Map<String, String> predicates) {
        if ("grass".equals(name) && predicates.isEmpty()) {
            return "tall_grass";
        }
        if ("short_grass".equals(name)) {
            return "tall_grass";
        }
        if ("fern".equals(name)) {
            return "fern";
        }

        return null;
    }

    private static String legacyFlowerBlockName(String name) {
        if ("dandelion".equals(name)) {
            return "yellow_flower";
        }

        return legacyFlowerType(name) == null ? null : "red_flower";
    }

    private static String legacyFlowerType(String name) {
        if ("dandelion".equals(name)) {
            return "dandelion";
        }
        if ("poppy".equals(name)) {
            return "poppy";
        }
        if ("blue_orchid".equals(name)) {
            return "blue_orchid";
        }
        if ("allium".equals(name)) {
            return "allium";
        }
        if ("azure_bluet".equals(name)) {
            return "houstonia";
        }
        if ("red_tulip".equals(name)) {
            return "red_tulip";
        }
        if ("orange_tulip".equals(name)) {
            return "orange_tulip";
        }
        if ("white_tulip".equals(name)) {
            return "white_tulip";
        }
        if ("pink_tulip".equals(name)) {
            return "pink_tulip";
        }
        if ("oxeye_daisy".equals(name)) {
            return "oxeye_daisy";
        }

        return null;
    }

    private static String legacySaplingType(String name) {
        if ("oak_sapling".equals(name)) {
            return "oak";
        }
        if ("spruce_sapling".equals(name)) {
            return "spruce";
        }
        if ("birch_sapling".equals(name)) {
            return "birch";
        }
        if ("jungle_sapling".equals(name)) {
            return "jungle";
        }
        if ("acacia_sapling".equals(name)) {
            return "acacia";
        }
        if ("dark_oak_sapling".equals(name)) {
            return "dark_oak";
        }

        return null;
    }

    private static String legacyFlowerPotContent(String name) {
        if (!name.startsWith("potted_")) {
            return null;
        }

        String content = name.substring("potted_".length());
        String flowerType = legacyFlowerType(content);
        if (flowerType != null) {
            return "poppy".equals(content) ? "rose" : flowerType;
        }
        if (legacySaplingType(content) != null) {
            return content;
        }
        if ("red_mushroom".equals(content)) {
            return "mushroom_red";
        }
        if ("brown_mushroom".equals(content)) {
            return "mushroom_brown";
        }
        if ("dead_bush".equals(content)) {
            return "dead_bush";
        }
        if ("fern".equals(content)) {
            return "fern";
        }
        if ("cactus".equals(content)) {
            return "cactus";
        }

        return null;
    }

    private static String legacyDoublePlantVariant(String name) {
        if ("sunflower".equals(name)) {
            return "sunflower";
        }
        if ("lilac".equals(name)) {
            return "syringa";
        }
        if ("tall_grass".equals(name)) {
            return "double_grass";
        }
        if ("large_fern".equals(name)) {
            return "double_fern";
        }
        if ("rose_bush".equals(name)) {
            return "double_rose";
        }
        if ("peony".equals(name)) {
            return "paeonia";
        }

        return null;
    }

    private static Map<String, String> withPredicate(Map<String, String> predicates, String name, String value) {
        Map<String, String> updated = new HashMap<>(predicates);
        updated.put(name, value);
        return updated;
    }

    private static Map<String, String> withDefaultPredicate(Map<String, String> predicates, String name, String value) {
        if (predicates.containsKey(name)) {
            return predicates;
        }

        return withPredicate(predicates, name, value);
    }

    private static Map<String, String> withoutPredicate(Map<String, String> predicates, String removedName) {
        if (predicates.size() == 1 && predicates.containsKey(removedName)) {
            return Collections.emptyMap();
        }

        Map<String, String> filtered = new HashMap<>(predicates);
        filtered.remove(removedName);
        return filtered;
    }

    private static String legacyLitBlockName(String name, String lit) {
        if (lit == null) {
            return null;
        }

        boolean litValue;
        if ("true".equals(lit)) {
            litValue = true;
        } else if ("false".equals(lit)) {
            litValue = false;
        } else {
            return null;
        }

        if ("furnace".equals(name)) {
            return litValue ? "lit_furnace" : "furnace";
        }
        if ("redstone_ore".equals(name)) {
            return litValue ? "lit_redstone_ore" : "redstone_ore";
        }
        if ("redstone_lamp".equals(name)) {
            return litValue ? "lit_redstone_lamp" : "redstone_lamp";
        }
        if ("redstone_torch".equals(name) || "redstone_wall_torch".equals(name)) {
            return litValue ? "redstone_torch" : "unlit_redstone_torch";
        }

        return null;
    }

    private static BlockEntry resolvePoweredRedstoneBlockEntry(String name, Map<String, String> predicates) {
        String powered = predicates.get("powered");
        if (powered == null) {
            return null;
        }

        Boolean poweredValue = parseBooleanPredicate(powered);
        if (poweredValue == null) {
            return null;
        }

        if ("repeater".equals(name)) {
            return new BlockEntry(new NamespacedId("minecraft",
                poweredValue ? "powered_repeater" : "unpowered_repeater"),
                withoutPredicate(predicates, "powered"));
        }
        if ("comparator".equals(name)) {
            return new BlockEntry(new NamespacedId("minecraft",
                poweredValue ? "powered_comparator" : "unpowered_comparator"),
                withoutPredicate(predicates, "powered"));
        }

        return null;
    }

    private static String legacyPoweredRedstoneContainerName(String name, Map<String, String> predicates) {
        String powered = predicates.get("powered");
        if (powered == null) {
            return null;
        }

        Boolean poweredValue = parseBooleanPredicate(powered);
        if (poweredValue == null) {
            return null;
        }

        if ("repeater".equals(name)) {
            return poweredValue ? "powered_repeater" : "unpowered_repeater";
        }
        if ("comparator".equals(name)) {
            return poweredValue ? "powered_comparator" : "unpowered_comparator";
        }

        return null;
    }

    private static Boolean parseBooleanPredicate(String value) {
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }

        return null;
    }

    private static Block lookupBlock(NamespacedId id) {
        ResourceLocation location = new ResourceLocation(id.getNamespace(), id.getName());
        Block block = Block.REGISTRY.getObject(location);
        if (isUsableBlock(block)) {
            return block;
        }

        String legacyName = legacyMinecraftBlockName(id);
        if (legacyName == null) {
            return null;
        }

        Block legacyBlock = Block.REGISTRY.getObject(new ResourceLocation("minecraft", legacyName));
        return isUsableBlock(legacyBlock) ? legacyBlock : null;
    }

    private static boolean isUsableBlock(Block block) {
        return block != null && block != Blocks.AIR;
    }

    static String legacyMinecraftBlockName(NamespacedId id) {
        if (!"minecraft".equals(id.getNamespace())) {
            return null;
        }

        String name = id.getName();
        String legacyName = directLegacyMinecraftBlockName(name);
        if (legacyName != null) {
            return legacyName;
        }

        if ("snow_block".equals(name)) {
            return "snow";
        }
        if ("light_gray_shulker_box".equals(name)) {
            return "silver_shulker_box";
        }
        if ("light_gray_glazed_terracotta".equals(name)) {
            return "silver_glazed_terracotta";
        }
        if (name.endsWith("_wall_hanging_sign") || name.endsWith("_wall_sign")) {
            return "wall_sign";
        }
        if (name.endsWith("_hanging_sign") || name.endsWith("_sign")) {
            return "standing_sign";
        }
        if (name.endsWith("_wall_banner")) {
            return "wall_banner";
        }
        if (name.endsWith("_banner")) {
            return "standing_banner";
        }
        if (name.endsWith("_bed")) {
            return "bed";
        }
        if (isModernSkullBlockName(name)) {
            return "skull";
        }

        return null;
    }

    private static boolean isModernSkullBlockName(String name) {
        return "skeleton_skull".equals(name)
            || "skeleton_wall_skull".equals(name)
            || "wither_skeleton_skull".equals(name)
            || "wither_skeleton_wall_skull".equals(name)
            || "player_head".equals(name)
            || "player_wall_head".equals(name)
            || "zombie_head".equals(name)
            || "zombie_wall_head".equals(name)
            || "creeper_head".equals(name)
            || "creeper_wall_head".equals(name)
            || "dragon_head".equals(name)
            || "dragon_wall_head".equals(name);
    }

    private static String directLegacyMinecraftBlockName(String name) {
        if ("grass_block".equals(name)) {
            return "grass";
        }
        if ("short_grass".equals(name)) {
            return "tallgrass";
        }
        if ("dirt_path".equals(name)) {
            return "grass_path";
        }
        if ("slime_block".equals(name)) {
            return "slime";
        }
        if ("wall_torch".equals(name)) {
            return "torch";
        }
        if ("powered_rail".equals(name)) {
            return "golden_rail";
        }
        if ("redstone_wall_torch".equals(name)) {
            return "redstone_torch";
        }
        if ("note_block".equals(name)) {
            return "noteblock";
        }
        if ("spawner".equals(name)) {
            return "mob_spawner";
        }
        if ("cobweb".equals(name)) {
            return "web";
        }
        if ("dead_bush".equals(name)) {
            return "deadbush";
        }
        if ("sugar_cane".equals(name)) {
            return "reeds";
        }
        if ("bricks".equals(name)) {
            return "brick_block";
        }
        if ("nether_bricks".equals(name)) {
            return "nether_brick";
        }
        if ("red_nether_bricks".equals(name)) {
            return "red_nether_brick";
        }
        if ("end_stone_bricks".equals(name)) {
            return "end_bricks";
        }
        if ("cobblestone_stairs".equals(name)) {
            return "stone_stairs";
        }
        if ("smooth_red_sandstone_stairs".equals(name)) {
            return "red_sandstone_stairs";
        }
        if ("lily_pad".equals(name)) {
            return "waterlily";
        }
        if ("melon".equals(name)) {
            return "melon_block";
        }
        if ("carved_pumpkin".equals(name)) {
            return "pumpkin";
        }
        if ("jack_o_lantern".equals(name)) {
            return "lit_pumpkin";
        }
        if ("oak_fence".equals(name)) {
            return "fence";
        }
        if ("oak_fence_gate".equals(name)) {
            return "fence_gate";
        }
        if ("oak_button".equals(name)) {
            return "wooden_button";
        }
        if ("oak_pressure_plate".equals(name)) {
            return "wooden_pressure_plate";
        }
        if ("oak_trapdoor".equals(name)) {
            return "trapdoor";
        }
        if ("oak_door".equals(name)) {
            return "wooden_door";
        }
        if ("nether_quartz_ore".equals(name)) {
            return "quartz_ore";
        }
        if ("magma_block".equals(name)) {
            return "magma";
        }
        if ("nether_portal".equals(name)) {
            return "portal";
        }

        return null;
    }

    private static BlockRenderLayer convertRenderType(BlockRenderType renderType) {
        if (renderType == null) {
            return null;
        }

        switch (renderType) {
            case SOLID:
                return BlockRenderLayer.SOLID;
            case CUTOUT:
                return BlockRenderLayer.CUTOUT;
            case CUTOUT_MIPPED:
                return BlockRenderLayer.CUTOUT_MIPPED;
            case TRANSLUCENT:
                return BlockRenderLayer.TRANSLUCENT;
            default:
                return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean matches(IBlockState state, Map<IProperty<?>, String> predicates) {
        for (Map.Entry<IProperty<?>, String> entry : predicates.entrySet()) {
            IProperty property = entry.getKey();
            Comparable value = state.getValue(property);
            String actual = propertyValueName(property, value);
            if (!entry.getValue().equals(actual)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(IProperty property, Comparable value) {
        return property.getName(value);
    }
}
