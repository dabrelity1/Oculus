package net.coderbot.iris.mixin;

import java.util.OptionalLong;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.dimension.DimensionType;

@Mixin(DimensionType.class)
public interface DimensionTypeAccessor {

	@Accessor
	OptionalLong getFixedTime();

	@Accessor
	float getAmbientLight();

}
