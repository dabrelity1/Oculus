package net.oculus.blockrendering;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private BlockMaterialMapping() {
    }

    public static Object2IntMap<IBlockState> createBlockStateIdMap(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap) {
        Object2IntOpenHashMap<IBlockState> idMap = new Object2IntOpenHashMap<>();
        idMap.defaultReturnValue(-1);

        blockPropertiesMap.forEach((intId, entries) -> {
            if (entries == null) {
                return;
            }

            for (BlockEntry entry : entries) {
                addBlockStates(entry, idMap, intId);
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

    private static void addBlockStates(BlockEntry entry, Object2IntOpenHashMap<IBlockState> idMap, int intId) {
        Block block = lookupBlock(entry.getId());
        if (block == null) {
            return;
        }

        BlockStateContainer container = block.getBlockState();
        Map<String, String> predicates = entry.getPropertyPredicates();

        if (predicates.isEmpty()) {
            for (IBlockState state : container.getValidStates()) {
                idMap.putIfAbsent(state, intId);
            }
            return;
        }

        Map<IProperty<?>, String> resolvedPredicates = new HashMap<>();
        predicates.forEach((name, value) -> {
            IProperty<?> property = container.getProperty(name);
            if (property == null) {
                Oculus.LOGGER.warn("Unknown block property '{}' on {} while building block ID map", name, entry.getId());
                return;
            }
            resolvedPredicates.put(property, value);
        });

        for (IBlockState state : container.getValidStates()) {
            if (matches(state, resolvedPredicates)) {
                idMap.putIfAbsent(state, intId);
            }
        }
    }

    private static Block lookupBlock(NamespacedId id) {
        ResourceLocation location = new ResourceLocation(id.getNamespace(), id.getName());
        Block block = Block.REGISTRY.getObject(location);
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        return block;
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
            String actual = property.getName(value);
            if (!entry.getValue().equals(actual)) {
                return false;
            }
        }
        return true;
    }
}
