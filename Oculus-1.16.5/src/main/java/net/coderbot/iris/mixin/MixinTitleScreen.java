package net.coderbot.iris.mixin;

import java.net.URI;
import java.net.URISyntaxException;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.coderbot.iris.Iris;
import net.coderbot.iris.compat.sodium.SodiumVersionCheck;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.fml.loading.FMLLoader;

@Mixin(TitleScreen.class)
public class MixinTitleScreen extends Screen {
	private static boolean iris$hasFirstInit;

	protected MixinTitleScreen(Component arg) {
		super(arg);
	}

	@Inject(method = "init", at = @At("RETURN"))
	public void iris$showSodiumIncompatScreen(CallbackInfo ci) {
		if (iris$hasFirstInit) {
			return;
		}

		iris$hasFirstInit = true;

		String reason;

		if (!Iris.isSodiumInstalled() && FMLLoader.isProduction()) {
			reason = "iris.sodium.failure.reason.notFound";
		} else {
			Iris.onLoadingComplete();

			return;
		}

		Minecraft.getInstance().setScreen(new ConfirmScreen(
				(boolean accepted) -> {
					if (accepted) {
						try {
							Util.getPlatform().openUri(new URI(SodiumVersionCheck.getDownloadLink()));
						} catch (URISyntaxException e) {
							throw new IllegalStateException(e);
						}
					} else {
						Minecraft.getInstance().stop();
					}
				},
				new TranslatableComponent("iris.sodium.failure.title").withStyle(ChatFormatting.RED),
				new TranslatableComponent(reason),
				new TranslatableComponent("iris.sodium.failure.download"),
				new TranslatableComponent("menu.quit")));
	}
}
