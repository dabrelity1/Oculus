package net.oculus.shaderpack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.oculus.shaderpack.materialmap.BlockEntry;
import net.oculus.shaderpack.materialmap.NamespacedId;

/**
 * Provides legacy block mappings for packs that expect old numeric IDs.
 */
public final class LegacyIdMap {
    private static final ImmutableList<String> COLORS = ImmutableList.of(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    );

    private static final ImmutableList<String> WOOD_TYPES = ImmutableList.of(
        "oak", "birch", "jungle", "spruce", "acacia", "dark_oak"
    );

    private LegacyIdMap() {
    }

    public static void addLegacyValues(Int2ObjectMap<List<BlockEntry>> blockIdMap) {
        add(blockIdMap, 1, block("stone"), block("granite"), block("diorite"), block("andesite"));
        add(blockIdMap, 2, block("grass_block"));
        add(blockIdMap, 4, block("cobblestone"));

        add(blockIdMap, 50, block("torch"));
        add(blockIdMap, 89, block("glowstone"));
        add(blockIdMap, 124, block("redstone_lamp"));

        add(blockIdMap, 12, block("sand"));
        add(blockIdMap, 24, block("sandstone"));

        add(blockIdMap, 41, block("gold_block"));
        add(blockIdMap, 42, block("iron_block"));
        add(blockIdMap, 57, block("diamond_block"));
        add(blockIdMap, -123, block("emerald_block"));

        addMany(blockIdMap, 35, COLORS, color -> block(color + "_wool"));

        add(blockIdMap, 9, block("water"));
        add(blockIdMap, 11, block("lava"));
        add(blockIdMap, 79, block("ice"));

        addMany(blockIdMap, 18, WOOD_TYPES, wood -> block(wood + "_leaves"));
        addMany(blockIdMap, 95, COLORS, color -> block(color + "_stained_glass"));
        addMany(blockIdMap, 160, COLORS, color -> block(color + "_stained_glass_pane"));

        add(blockIdMap, 31, block("grass"), block("seagrass"), block("sweet_berry_bush"));
        add(blockIdMap, 59, block("wheat"), block("carrots"), block("potatoes"));

        add(blockIdMap, 37,
            block("dandelion"), block("poppy"), block("blue_orchid"), block("allium"), block("azure_bluet"),
            block("red_tulip"), block("pink_tulip"), block("white_tulip"), block("orange_tulip"),
            block("oxeye_daisy"), block("cornflower"), block("lily_of_the_valley"), block("wither_rose"));

        add(blockIdMap, 175,
            block("sunflower"), block("lilac"), block("tall_grass"), block("large_fern"),
            block("rose_bush"), block("peony"), block("tall_seagrass"));

        add(blockIdMap, 51, block("fire"));
        add(blockIdMap, 111, block("lily_pad"));
    }

    private static BlockEntry block(String name) {
        return new BlockEntry(new NamespacedId("minecraft", name), java.util.Collections.emptyMap());
    }

    private static void addMany(Int2ObjectMap<List<BlockEntry>> map, int id, List<String> values, Function<String, BlockEntry> factory) {
        List<BlockEntry> entries = new ArrayList<>();
        for (String value : values) {
            entries.add(factory.apply(value));
        }
        map.put(id, entries);
    }

    private static void add(Int2ObjectMap<List<BlockEntry>> map, int id, BlockEntry... entries) {
        map.put(id, Arrays.asList(entries));
    }
}
