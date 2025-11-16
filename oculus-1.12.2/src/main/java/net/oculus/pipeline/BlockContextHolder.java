package net.oculus.pipeline;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

public class BlockContextHolder {
    private static final Object2IntMap<IBlockState> VANILLA_BLOCK_STATE_IDS = buildVanillaStateIdMap();
    private static volatile Object2IntMap<IBlockState> activeBlockStateIds = VANILLA_BLOCK_STATE_IDS;

    private final Object2IntMap<IBlockState> blockStateIds;

    public int localPosX;
    public int localPosY;
    public int localPosZ;

    public short blockId;
    public short renderType;

    public BlockContextHolder() {
        this(Object2IntMaps.emptyMap());
    }

    public BlockContextHolder(Object2IntMap<IBlockState> idMap) {
        this.blockStateIds = idMap;
        this.blockId = -1;
        this.renderType = -1;
    }

    public static BlockContextHolder createVanillaHolder() {
        return new BlockContextHolder(VANILLA_BLOCK_STATE_IDS);
    }

    public static BlockContextHolder createActiveHolder() {
        return new BlockContextHolder(activeBlockStateIds);
    }

    public static void useActiveStateMap(Object2IntMap<IBlockState> idMap) {
        activeBlockStateIds = idMap == null ? VANILLA_BLOCK_STATE_IDS : idMap;
    }

    public static Object2IntMap<IBlockState> getVanillaStateIds() {
        return VANILLA_BLOCK_STATE_IDS;
    }

    public void setLocalPos(int x, int y, int z) {
        this.localPosX = x;
        this.localPosY = y;
        this.localPosZ = z;
    }

    public void set(IBlockState state, short renderType) {
        int id = this.blockStateIds.getOrDefault(state, -1);
        this.blockId = (short) id;
        this.renderType = renderType;
    }

    public void reset() {
        this.blockId = -1;
        this.renderType = -1;
        this.localPosX = 0;
        this.localPosY = 0;
        this.localPosZ = 0;
    }

    private static Object2IntMap<IBlockState> buildVanillaStateIdMap() {
        Object2IntOpenHashMap<IBlockState> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);

        for (Block block : Block.REGISTRY) {
            for (IBlockState state : block.getBlockState().getValidStates()) {
                map.put(state, Block.BLOCK_STATE_IDS.get(state));
            }
        }

        return Object2IntMaps.unmodifiable(map);
    }
}
