package net.oculus.pipeline;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.block.state.IBlockState;

public class BlockContextHolder {
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
}
