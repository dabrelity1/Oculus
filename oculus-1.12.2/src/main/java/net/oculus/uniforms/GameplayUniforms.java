package net.oculus.uniforms;

import java.util.Arrays;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.oculus.Oculus;
import net.oculus.colorspace.ColorSpace;
import net.oculus.config.OculusConfig;
import net.oculus.gl.state.FanOutValueUpdateNotifier;
import net.oculus.gl.state.GameDataSuppliers;
import net.oculus.gl.state.ValueUpdateNotifier;
import net.oculus.pipeline.OculusTerrainVertexType;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.texture.TextureInfoCache;
import net.oculus.uniforms.transforms.SmoothedFloat;
import net.oculus.uniforms.transforms.SmoothedVec2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

public final class GameplayUniforms {
	private static final float DEFAULT_WETNESS_HALF_LIFE = 600.0F;
	private static final float DEFAULT_DRYNESS_HALF_LIFE = 200.0F;
	private static final float DEFAULT_EYE_BRIGHTNESS_HALF_LIFE = 10.0F;

	private static final float[] SKY_COLOR = new float[3];
	private static final float[] SCREEN_SIZE = new float[2];
	private static final float[] EYE_BRIGHTNESS = new float[2];
	private static final int[] EYE_BRIGHTNESS_INT = new int[2];
	private static final float[] SMOOTHED_EYE_BRIGHTNESS = new float[2];
	private static final int[] SMOOTHED_EYE_BRIGHTNESS_INT = new int[2];
	private static final float[] EYE_POSITION = new float[3];
	private static final float[] LOOK_VECTOR = new float[3];
	private static final float[] BODY_VECTOR = new float[3];
	private static final int[] ATLAS_SIZE = new int[2];
	private static final float[] ATLAS_SIZE_FLOAT = new float[2];
	private static final int[] GTEXTURE_SIZE = new int[2];
	private static final float[] GTEXTURE_SIZE_FLOAT = new float[2];
	private static final int[] BLEND_FUNC = new int[4];
	private static final float[] BLEND_FUNC_FLOAT = new float[4];
	private static final float[] TERRAIN_MODEL_SCALE = new float[3];
	private static final float[] TERRAIN_TEXTURE_SCALE = new float[2];
	private static final float[] ENTITY_COLOR = new float[4];
	private static final FanOutValueUpdateNotifier ENTITY_COLOR_NOTIFIER = new FanOutValueUpdateNotifier();

	private static final FrameUpdateNotifier UPDATE_NOTIFIER = new FrameUpdateNotifier();
	private static final SmoothedFloat WETNESS = new SmoothedFloat(DEFAULT_WETNESS_HALF_LIFE, DEFAULT_DRYNESS_HALF_LIFE,
		GameplayUniforms::getRainStrengthRaw, UPDATE_NOTIFIER);
	private static final SmoothedVec2f EYE_BRIGHTNESS_SMOOTH = new SmoothedVec2f(DEFAULT_EYE_BRIGHTNESS_HALF_LIFE,
		DEFAULT_EYE_BRIGHTNESS_HALF_LIFE,
		GameplayUniforms::computeEyeBrightness, UPDATE_NOTIFIER);

	private GameplayUniforms() {
	}

	public static void onFrameStart() {
		UPDATE_NOTIFIER.onNewFrame();
	}

	public static void configure(PackDirectives directives) {
		float wetnessHalfLife = directives != null ? directives.getWetnessHalfLife() : DEFAULT_WETNESS_HALF_LIFE;
		float drynessHalfLife = directives != null ? directives.getDrynessHalfLife() : DEFAULT_DRYNESS_HALF_LIFE;
		float eyeBrightnessHalfLife = directives != null
			? directives.getEyeBrightnessHalfLife()
			: DEFAULT_EYE_BRIGHTNESS_HALF_LIFE;

		WETNESS.configureHalfLives(wetnessHalfLife, drynessHalfLife);
		EYE_BRIGHTNESS_SMOOTH.configureHalfLives(eyeBrightnessHalfLife, eyeBrightnessHalfLife);
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

	public static int[] getEyeBrightnessInt() {
		float[] brightness = computeEyeBrightness();
		EYE_BRIGHTNESS_INT[0] = (int) brightness[0];
		EYE_BRIGHTNESS_INT[1] = (int) brightness[1];
		return EYE_BRIGHTNESS_INT;
	}

	public static float[] getEyeBrightnessSmooth() {
		float[] smoothed = EYE_BRIGHTNESS_SMOOTH.get();
		SMOOTHED_EYE_BRIGHTNESS[0] = smoothed[0];
		SMOOTHED_EYE_BRIGHTNESS[1] = smoothed[1];
		return SMOOTHED_EYE_BRIGHTNESS;
	}

	public static int[] getEyeBrightnessSmoothInt() {
		float[] smoothed = getEyeBrightnessSmooth();
		SMOOTHED_EYE_BRIGHTNESS_INT[0] = (int) smoothed[0];
		SMOOTHED_EYE_BRIGHTNESS_INT[1] = (int) smoothed[1];
		return SMOOTHED_EYE_BRIGHTNESS_INT;
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
		return isSpectatorGameType(getCurrentGameType()) ? 1 : 0;
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
				return getNightVisionStrength(effect.getDuration(), CapturedRenderingState.INSTANCE.getTickDelta());
			}
		}

		return 0.0F;
	}

	public static float getMaxBlindnessDarkness() {
		return getBlindness();
	}

	public static float getPlayerMood() {
		return 0.0F;
	}

	public static int isHeavyFog() {
		Minecraft minecraft = Minecraft.getMinecraft();
		return minecraft != null
			&& minecraft.ingameGUI != null
			&& minecraft.ingameGUI.getBossOverlay() != null
			&& minecraft.ingameGUI.getBossOverlay().shouldCreateFog() ? 1 : 0;
	}

	public static float[] getEntityColor() {
		return ENTITY_COLOR;
	}

	public static ValueUpdateNotifier getEntityColorNotifier() {
		return ENTITY_COLOR_NOTIFIER;
	}

	public static void setEntityColor(float red, float green, float blue, float alpha) {
		if (Float.compare(ENTITY_COLOR[0], red) == 0
			&& Float.compare(ENTITY_COLOR[1], green) == 0
			&& Float.compare(ENTITY_COLOR[2], blue) == 0
			&& Float.compare(ENTITY_COLOR[3], alpha) == 0) {
			return;
		}

		ENTITY_COLOR[0] = red;
		ENTITY_COLOR[1] = green;
		ENTITY_COLOR[2] = blue;
		ENTITY_COLOR[3] = alpha;
		ENTITY_COLOR_NOTIFIER.notifyListeners();
	}

	public static void clearEntityColor() {
		setEntityColor(0.0F, 0.0F, 0.0F, 0.0F);
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
		if (!shouldExposeSurvivalPlayerStats(player)) {
			return -1.0F;
		}
		return player.getHealth() / player.getMaxHealth();
	}

	public static float getMaxPlayerHealth() {
		EntityPlayerSP player = getPlayer();
		return shouldExposeSurvivalPlayerStats(player) ? player.getMaxHealth() : -1.0F;
	}

	public static float getCurrentPlayerHunger() {
		EntityPlayerSP player = getPlayer();
		if (!shouldExposeSurvivalPlayerStats(player)) {
			return -1.0F;
		}
		return player.getFoodStats().getFoodLevel() / 20.0F;
	}

	public static float getMaxPlayerHunger() {
		return 20.0F;
	}

	public static float getCurrentPlayerAir() {
		EntityPlayerSP player = getPlayer();
		if (!shouldExposeSurvivalPlayerStats(player)) {
			return -1.0F;
		}
		return player.getAir() / 300.0F;
	}

	public static float getMaxPlayerAir() {
		EntityPlayerSP player = getPlayer();
		return shouldExposeSurvivalPlayerStats(player) ? 300.0F : -1.0F;
	}

	public static float[] getEyePosition() {
		Entity camera = getCameraEntity();
		if (camera == null) {
			Arrays.fill(EYE_POSITION, 0.0F);
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
			Arrays.fill(LOOK_VECTOR, 0.0F);
			return LOOK_VECTOR;
		}
		Vec3d look = camera.getLookVec();
		LOOK_VECTOR[0] = (float) look.x;
		LOOK_VECTOR[1] = (float) look.y;
		LOOK_VECTOR[2] = (float) look.z;
		return LOOK_VECTOR;
	}

	public static float[] getPlayerBodyVector() {
		Entity camera = getCameraEntity();
		if (camera == null) {
			Arrays.fill(BODY_VECTOR, 0.0F);
			return BODY_VECTOR;
		}

		Vec3d forward = camera.getForward();
		BODY_VECTOR[0] = (float) forward.x;
		BODY_VECTOR[1] = (float) forward.y;
		BODY_VECTOR[2] = (float) forward.z;
		return BODY_VECTOR;
	}

	public static float[] getScreenSize() {
		GameDataSuppliers.updateScreenSize(SCREEN_SIZE);
		return SCREEN_SIZE;
	}

	public static int[] getAtlasSize() {
		readTextureUnitZeroSize(ATLAS_SIZE, true);
		return ATLAS_SIZE;
	}

	public static float[] getAtlasSizeFloat() {
		int[] size = getAtlasSize();
		ATLAS_SIZE_FLOAT[0] = size[0];
		ATLAS_SIZE_FLOAT[1] = size[1];
		return ATLAS_SIZE_FLOAT;
	}

	public static int[] getGtextureSize() {
		readTextureUnitZeroSize(GTEXTURE_SIZE, false);
		return GTEXTURE_SIZE;
	}

	public static float[] getGtextureSizeFloat() {
		int[] size = getGtextureSize();
		GTEXTURE_SIZE_FLOAT[0] = size[0];
		GTEXTURE_SIZE_FLOAT[1] = size[1];
		return GTEXTURE_SIZE_FLOAT;
	}

	public static int[] getBlendFunc() {
		if (!GL11.glIsEnabled(GL11.GL_BLEND)) {
			Arrays.fill(BLEND_FUNC, 0);
			return BLEND_FUNC;
		}

		BLEND_FUNC[0] = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
		BLEND_FUNC[1] = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
		BLEND_FUNC[2] = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
		BLEND_FUNC[3] = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
		return BLEND_FUNC;
	}

	public static float[] getBlendFuncFloat() {
		int[] blendFunc = getBlendFunc();
		for (int i = 0; i < BLEND_FUNC_FLOAT.length; i++) {
			BLEND_FUNC_FLOAT[i] = blendFunc[i];
		}
		return BLEND_FUNC_FLOAT;
	}

	public static float getTerrainModelScale() {
		return OculusTerrainVertexType.MODEL_SCALE;
	}

	public static float[] getTerrainModelScaleVec3() {
		float scale = getTerrainModelScale();
		TERRAIN_MODEL_SCALE[0] = scale;
		TERRAIN_MODEL_SCALE[1] = scale;
		TERRAIN_MODEL_SCALE[2] = scale;
		return TERRAIN_MODEL_SCALE;
	}

	public static float getTerrainTextureScale() {
		return OculusTerrainVertexType.TEXTURE_SCALE;
	}

	public static float[] getTerrainTextureScaleVec2() {
		float scale = getTerrainTextureScale();
		TERRAIN_TEXTURE_SCALE[0] = scale;
		TERRAIN_TEXTURE_SCALE[1] = scale;
		return TERRAIN_TEXTURE_SCALE;
	}

	public static int getCurrentColorSpace() {
		OculusConfig config = Oculus.getConfig();
		return config != null ? config.getColorSpace().ordinal() : ColorSpace.SRGB.ordinal();
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

		BlockPos pos = getEyeBrightnessBlockPos(camera);
		int blockLight = world.getLightFor(EnumSkyBlock.BLOCK, pos);
		int skyLight = world.getLightFor(EnumSkyBlock.SKY, pos);
		EYE_BRIGHTNESS[0] = blockLight * 16.0F;
		EYE_BRIGHTNESS[1] = skyLight * 16.0F;
		return EYE_BRIGHTNESS;
	}

	private static BlockPos getEyeBrightnessBlockPos(Entity camera) {
		return getEyeBrightnessBlockPos(camera.posX, camera.posY, camera.posZ, camera.getEyeHeight());
	}

	static BlockPos getEyeBrightnessBlockPos(double x, double y, double z, float eyeHeight) {
		return new BlockPos(x, y + eyeHeight, z);
	}

	private static void readTextureUnitZeroSize(int[] target, boolean atlasOnly) {
		int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
		try {
			int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			if (boundTexture <= 0 || (atlasOnly && boundTexture != getBlockAtlasTextureId())) {
				target[0] = 0;
				target[1] = 0;
				return;
			}

			TextureInfoCache.TextureInfo info = TextureInfoCache.INSTANCE.getInfo(boundTexture);
			target[0] = Math.max(0, info.getWidth());
			target[1] = Math.max(0, info.getHeight());
		} finally {
			OpenGlHelper.setActiveTexture(previousActiveTexture);
		}
	}

	private static int getBlockAtlasTextureId() {
		Minecraft minecraft = Minecraft.getMinecraft();
		if (minecraft == null || minecraft.getTextureManager() == null) {
			return -1;
		}

		ITextureObject atlas = minecraft.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
		return atlas != null ? atlas.getGlTextureId() : -1;
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

	private static boolean shouldExposeSurvivalPlayerStats(EntityPlayerSP player) {
		return shouldExposeSurvivalPlayerStats(player != null, getCurrentGameType());
	}

	static boolean shouldExposeSurvivalPlayerStats(boolean hasPlayer, GameType gameType) {
		return hasPlayer && gameType != null && gameType.isSurvivalOrAdventure();
	}

	static boolean isSpectatorGameType(GameType gameType) {
		return gameType == GameType.SPECTATOR;
	}

	static float getNightVisionStrength(int duration, float tickDelta) {
		return duration > 200
			? 1.0F
			: 0.7F + MathHelper.sin(((float) duration - tickDelta) * (float) Math.PI * 0.2F) * 0.3F;
	}

	private static GameType getCurrentGameType() {
		Minecraft minecraft = Minecraft.getMinecraft();
		return minecraft != null && minecraft.playerController != null
			? minecraft.playerController.getCurrentGameType()
			: null;
	}
}
