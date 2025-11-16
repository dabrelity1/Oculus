package net.oculus.uniforms;

import java.util.Arrays;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.oculus.uniforms.transforms.SmoothedFloat;
import net.oculus.uniforms.transforms.SmoothedVec2f;

public final class GameplayUniforms {
	private static final float[] SKY_COLOR = new float[3];
	private static final float[] SCREEN_SIZE = new float[2];
	private static final float[] EYE_BRIGHTNESS = new float[2];
	private static final float[] SMOOTHED_EYE_BRIGHTNESS = new float[2];
	private static final float[] EYE_POSITION = new float[3];
	private static final float[] LOOK_VECTOR = new float[3];
	private static final float[] BODY_VECTOR = new float[3];

	private static final FrameUpdateNotifier UPDATE_NOTIFIER = new FrameUpdateNotifier();
	private static final SmoothedFloat WETNESS = new SmoothedFloat(20f, 60f,
		GameplayUniforms::getRainStrengthRaw, UPDATE_NOTIFIER);
	private static final SmoothedVec2f EYE_BRIGHTNESS_SMOOTH = new SmoothedVec2f(5f, 5f,
		GameplayUniforms::computeEyeBrightness, UPDATE_NOTIFIER);

	private GameplayUniforms() {
	}

	public static void onFrameStart() {
		UPDATE_NOTIFIER.onNewFrame();
	}

	public static float getRainStrength() {
		return getRainStrengthRaw();
	}

	public static float getThunderStrength() {
		World world = getWorld();
		if (world == null) {
			return 0.0F;
		}

		return MathHelper.clamp(world.getThunderStrength(CapturedRenderingState.INSTANCE.getTickDelta()), 0.0F, 1.0F);
	}

	public static float getWetness() {
		return WETNESS.getAsFloat();
	}

	public static float[] getSkyColor() {
		Minecraft minecraft = Minecraft.getMinecraft();
		if (minecraft == null) {
			Arrays.fill(SKY_COLOR, 0.0F);
			return SKY_COLOR;
		}

		World world = minecraft.world;
		Entity camera = minecraft.getRenderViewEntity();
		if (world == null || camera == null) {
			Arrays.fill(SKY_COLOR, 0.0F);
			return SKY_COLOR;
		}

		Vec3d skyColor = world.getSkyColor(camera, CapturedRenderingState.INSTANCE.getTickDelta());
		if (skyColor == null) {
			Arrays.fill(SKY_COLOR, 0.0F);
		} else {
			SKY_COLOR[0] = (float) skyColor.x;
			SKY_COLOR[1] = (float) skyColor.y;
			SKY_COLOR[2] = (float) skyColor.z;
		}

	return SKY_COLOR;
	}

	public static int getWorldTime() {
		World world = getWorld();
		if (world == null) {
			return 0;
		}

		return (int) (world.getWorldTime() % 24000L);
	}

	public static int getWorldDay() {
		World world = getWorld();
		if (world == null) {
			return 0;
		}

		return (int) (world.getWorldTime() / 24000L);
	}

	public static float[] getEyeBrightness() {
		return computeEyeBrightness();
	}

	public static float[] getEyeBrightnessSmooth() {
		float[] smoothed = EYE_BRIGHTNESS_SMOOTH.get();
		SMOOTHED_EYE_BRIGHTNESS[0] = smoothed[0];
		SMOOTHED_EYE_BRIGHTNESS[1] = smoothed[1];
		return SMOOTHED_EYE_BRIGHTNESS;
	}

	public static int getMoonPhase() {
		World world = getWorld();
		if (world == null) {
			return 0;
		}

		WorldProvider provider = world.provider;
		return provider != null ? provider.getMoonPhase(world.getWorldTime()) : 0;
	}

	public static int hideGui() {
		Minecraft minecraft = Minecraft.getMinecraft();
		if (minecraft == null || minecraft.gameSettings == null) {
			return 0;
		}

		return minecraft.gameSettings.hideGUI ? 1 : 0;
	}

	public static int isFirstPersonCamera() {
		Minecraft minecraft = Minecraft.getMinecraft();
		if (minecraft == null || minecraft.gameSettings == null) {
			return 1;
		}
		return minecraft.gameSettings.thirdPersonView == 0 ? 1 : 0;
	}

	public static int isSpectator() {
		EntityPlayerSP player = getPlayer();
		return player != null && player.isSpectator() ? 1 : 0;
	}

	public static int isEyeInWater() {
		Minecraft minecraft = Minecraft.getMinecraft();
		if (minecraft == null) {
			return 0;
		}

		Entity camera = minecraft.getRenderViewEntity();
		if (camera == null) {
			return 0;
		}

		if (camera.isInsideOfMaterial(Material.WATER)) {
			return 1;
		}

		if (camera.isInsideOfMaterial(Material.LAVA)) {
			return 2;
		}

		return 0;
	}

	public static float getBlindness() {
		Entity camera = getCameraEntity();
		if (camera instanceof EntityLivingBase) {
			PotionEffect effect = ((EntityLivingBase) camera).getActivePotionEffect(MobEffects.BLINDNESS);
			if (effect != null) {
				float value = effect.getDuration() / 20.0F;
				return MathHelper.clamp(value, 0.0F, 1.0F);
			}
		}

		return 0.0F;
	}

	public static float getNightVision() {
		Entity camera = getCameraEntity();
		if (camera instanceof EntityLivingBase) {
			PotionEffect effect = ((EntityLivingBase) camera).getActivePotionEffect(MobEffects.NIGHT_VISION);
			if (effect != null) {
				float duration = effect.getDuration() / 200.0F;
				return MathHelper.clamp(duration, 0.0F, 1.0F);
			}
		}

		return 0.0F;
	}

	public static int isSneaking() {
		EntityPlayerSP player = getPlayer();
		return player != null && player.isSneaking() ? 1 : 0;
	}

	public static int isSprinting() {
		EntityPlayerSP player = getPlayer();
		return player != null && player.isSprinting() ? 1 : 0;
	}

	public static int isInvisible() {
		EntityPlayerSP player = getPlayer();
		return player != null && player.isInvisible() ? 1 : 0;
	}

	public static int isBurning() {
		EntityPlayerSP player = getPlayer();
		return player != null && player.isBurning() ? 1 : 0;
	}

	public static int isOnGround() {
		EntityPlayerSP player = getPlayer();
		return player != null && player.onGround ? 1 : 0;
	}

	public static int isHurt() {
		EntityPlayerSP player = getPlayer();
		return player != null && player.hurtTime > 0 ? 1 : 0;
	}

	public static float getScreenBrightness() {
		Minecraft minecraft = Minecraft.getMinecraft();
		if (minecraft == null || minecraft.gameSettings == null) {
			return 0.0F;
		}

		return minecraft.gameSettings.gammaSetting;
	}

	public static float getCurrentPlayerHealth() {
		EntityPlayerSP player = getPlayer();
		if (player == null || !player.isEntityAlive()) {
			return -1.0F;
		}
		return player.getHealth() / player.getMaxHealth();
	}

	public static float getMaxPlayerHealth() {
		EntityPlayerSP player = getPlayer();
		return player != null ? player.getMaxHealth() : -1.0F;
	}

	public static float getCurrentPlayerHunger() {
		EntityPlayerSP player = getPlayer();
		if (player == null) {
			return -1.0F;
		}
		return player.getFoodStats().getFoodLevel() / 20.0F;
	}

	public static float getMaxPlayerHunger() {
		return 20.0F;
	}

	public static float getCurrentPlayerAir() {
		EntityPlayerSP player = getPlayer();
		if (player == null) {
			return -1.0F;
		}
		return player.getAir() / 300.0F;
	}

	public static float getMaxPlayerAir() {
		EntityPlayerSP player = getPlayer();
		return player != null ? 300.0F : -1.0F;
	}

	public static float[] getEyePosition() {
		Entity camera = getCameraEntity();
		if (camera == null) {
			return EYE_POSITION;
		}
		Vec3d eyes = camera.getPositionEyes(CapturedRenderingState.INSTANCE.getTickDelta());
		EYE_POSITION[0] = (float) eyes.x;
		EYE_POSITION[1] = (float) eyes.y;
		EYE_POSITION[2] = (float) eyes.z;
		return EYE_POSITION;
	}

	public static float[] getPlayerLookVector() {
		Entity camera = getCameraEntity();
		if (camera == null) {
			return LOOK_VECTOR;
		}
		Vec3d look = camera.getLook(CapturedRenderingState.INSTANCE.getTickDelta());
		LOOK_VECTOR[0] = (float) look.x;
		LOOK_VECTOR[1] = (float) look.y;
		LOOK_VECTOR[2] = (float) look.z;
		return LOOK_VECTOR;
	}

	public static float[] getPlayerBodyVector() {
		Entity camera = getCameraEntity();
		if (!(camera instanceof EntityLivingBase)) {
			float[] look = getPlayerLookVector();
			BODY_VECTOR[0] = look[0];
			BODY_VECTOR[1] = look[1];
			BODY_VECTOR[2] = look[2];
			return BODY_VECTOR;
		}
		EntityLivingBase living = (EntityLivingBase) camera;
		float radians = living.renderYawOffset * (float) Math.PI / 180.0F;
		BODY_VECTOR[0] = -MathHelper.sin(radians);
		BODY_VECTOR[1] = 0.0F;
		BODY_VECTOR[2] = MathHelper.cos(radians);
		return BODY_VECTOR;
	}

	public static float[] getScreenSize() {
		Minecraft minecraft = Minecraft.getMinecraft();
		if (minecraft == null) {
			Arrays.fill(SCREEN_SIZE, 0.0F);
		} else {
			SCREEN_SIZE[0] = minecraft.displayWidth;
			SCREEN_SIZE[1] = minecraft.displayHeight;
		}

		return SCREEN_SIZE;
	}

	private static float getRainStrengthRaw() {
		World world = getWorld();
		if (world == null) {
			return 0.0F;
		}
		return MathHelper.clamp(world.getRainStrength(CapturedRenderingState.INSTANCE.getTickDelta()), 0.0F, 1.0F);
	}

	private static float[] computeEyeBrightness() {
		World world = getWorld();
		Entity camera = getCameraEntity();
		if (world == null || camera == null) {
			EYE_BRIGHTNESS[0] = 0.0F;
			EYE_BRIGHTNESS[1] = 0.0F;
			return EYE_BRIGHTNESS;
		}

		Vec3d eyes = camera.getPositionEyes(CapturedRenderingState.INSTANCE.getTickDelta());
		BlockPos pos = new BlockPos(eyes);
		int blockLight = world.getLightFor(EnumSkyBlock.BLOCK, pos);
		int skyLight = world.getLightFor(EnumSkyBlock.SKY, pos);
		EYE_BRIGHTNESS[0] = blockLight * 16.0F;
		EYE_BRIGHTNESS[1] = skyLight * 16.0F;
		return EYE_BRIGHTNESS;
	}

	private static World getWorld() {
		Minecraft minecraft = Minecraft.getMinecraft();
		return minecraft != null ? minecraft.world : null;
	}

	private static Entity getCameraEntity() {
		Minecraft minecraft = Minecraft.getMinecraft();
		return minecraft != null ? minecraft.getRenderViewEntity() : null;
	}

	private static EntityPlayerSP getPlayer() {
		Minecraft minecraft = Minecraft.getMinecraft();
		return minecraft != null ? minecraft.player : null;
	}
}
