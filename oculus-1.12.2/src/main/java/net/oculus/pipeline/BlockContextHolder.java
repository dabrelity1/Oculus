package net.oculus.pipeline;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

public class BlockContextHolder {
    private static volatile Object2IntMap<IBlockState> vanillaBlockStateIds;
    private static volatile Object2IntMap<IBlockState> activeBlockStateIds;

    private final Object2IntMap<IBlockState> blockStateIds;

    public int localPosX;
    public int localPosY;
    public int localPosZ;

    public short blockId;
    public short renderType;
    public byte blockEmission;

    public BlockContextHolder() {
        this(Object2IntMaps.emptyMap());
    }

    public BlockContextHolder(Object2IntMap<IBlockState> idMap) {
        this.blockStateIds = idMap;
        this.blockId = -1;
        this.renderType = -1;
        this.blockEmission = 0;
    }

    public static BlockContextHolder createVanillaHolder() {
        return new BlockContextHolder(getVanillaStateIds());
    }

    public static BlockContextHolder createActiveHolder() {
        return new BlockContextHolder(getActiveStateIds());
    }

    public static void useActiveStateMap(Object2IntMap<IBlockState> idMap) {
        activeBlockStateIds = idMap;
    }

    public static Object2IntMap<IBlockState> getVanillaStateIds() {
        Object2IntMap<IBlockState> map = vanillaBlockStateIds;
        if (map == null) {
            synchronized (BlockContextHolder.class) {
                map = vanillaBlockStateIds;
                if (map == null) {
                    map = buildVanillaStateIdMap();
                    vanillaBlockStateIds = map;
                }
            }
        }
        return map;
    }

    public static short getActiveStateId(IBlockState state) {
        int id = getActiveStateIds().getOrDefault(state, -1);
        return (short) id;
    }

    private static Object2IntMap<IBlockState> getActiveStateIds() {
        Object2IntMap<IBlockState> map = activeBlockStateIds;
        return map == null ? getVanillaStateIds() : map;
    }

    public static byte getBlockEmission(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return 0;
        }

        return (byte) Math.max(0, Math.min(15, state.getBlock().getLightValue(state)));
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
        this.blockEmission = getBlockEmission(state);
    }

    public void reset() {
        this.blockId = -1;
        this.renderType = -1;
        this.blockEmission = 0;
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
