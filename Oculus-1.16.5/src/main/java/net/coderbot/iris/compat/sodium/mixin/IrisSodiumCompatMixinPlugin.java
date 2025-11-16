package net.coderbot.iris.compat.sodium.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.FMLLoader;

/**
 * Semi-critical mixin config plugin, disables mixins if Sodium isn't present,
 * since on 1.18+ we have mixins into Iris classes that crash the game instead of just
 * spamming the log if Sodium isn't present.
 */
public class IrisSodiumCompatMixinPlugin implements IMixinConfigPlugin {
	
	public static boolean isRubidiumLoaded;

	@Override
	public void onLoad(String mixinPackage) {
		isRubidiumLoaded = FMLLoader.getLoadingModList().getModFileById("rubidium") != null;
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return isRubidiumLoaded;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

	}
}
