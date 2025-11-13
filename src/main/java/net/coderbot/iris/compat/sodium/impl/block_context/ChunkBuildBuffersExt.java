package net.coderbot.iris.compat.sodium.impl.block_context;

import net.minecraft.block.state.IBlockState;

public interface ChunkBuildBuffersExt {
	void iris$setLocalPos(int localPosX, int localPosY, int localPosZ);

	void iris$setMaterialId(IBlockState state, short renderType);

	void iris$resetBlockContext();
}
