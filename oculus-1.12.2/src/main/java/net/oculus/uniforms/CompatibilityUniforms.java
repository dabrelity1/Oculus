package net.oculus.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.oculus.uniforms.transforms.SmoothedFloat;

/**
 * Collection of hardcoded compatibility uniforms used by popular shader packs
 * (BSL, Complementary, AstralEX, etc.).
 */
public final class CompatibilityUniforms {
    private static final FrameUpdateNotifier UPDATE_NOTIFIER = new FrameUpdateNotifier();

    private static Biome cachedBiome;
    private static BlockPos cachedBiomePos = BlockPos.ORIGIN;

    private static final SmoothedFloat EYE_IN_CAVE = new SmoothedFloat(6f, 12f,
        CompatibilityUniforms::computeEyeInCave, UPDATE_NOTIFIER);
    private static final SmoothedFloat RAIN_STRENGTH_S = createRainStrength(15f, 15f);
    private static final SmoothedFloat RAIN_STRENGTH_SHINING = createRainStrength(10f, 11f);
    private static final SmoothedFloat RAIN_STRENGTH_S2 = createRainStrength(70f, 1f);
    private static final SmoothedFloat IS_DRY = new SmoothedFloat(20f, 10f,
        () -> getRawPrecipitation() == 0 ? 1f : 0f, UPDATE_NOTIFIER);
    private static final SmoothedFloat IS_RAINY = new SmoothedFloat(20f, 10f,
        () -> getRawPrecipitation() == 1 ? 1f : 0f, UPDATE_NOTIFIER);
    private static final SmoothedFloat IS_SNOWY = new SmoothedFloat(20f, 10f,
        () -> getRawPrecipitation() == 2 ? 1f : 0f, UPDATE_NOTIFIER);
    private static final SmoothedFloat STARTER = createStarter();
    private static final SmoothedFloat FRAME_TIME_SMOOTH = new SmoothedFloat(5f, 5f,
        SystemTimeUniforms.TIMER::getLastFrameTime, UPDATE_NOTIFIER);
    private static final SmoothedFloat EYE_BRIGHTNESS_M = new SmoothedFloat(5f, 5f,
        CompatibilityUniforms::getEyeBrightnessM, UPDATE_NOTIFIER);
    private static final SmoothedFloat EYE_BRIGHTNESS_M2 = new SmoothedFloat(2f, 2f,
        CompatibilityUniforms::getEyeBrightnessM2Raw, UPDATE_NOTIFIER);
    private static final SmoothedFloat RAIN_FACTOR = createRainStrength(15f, 15f);
    private static final SmoothedFloat IN_SWAMP = new SmoothedFloat(5f, 5f,
        CompatibilityUniforms::computeInSwamp, UPDATE_NOTIFIER);
    private static final SmoothedFloat IS_PRECIP_RAIN = new SmoothedFloat(6f, 6f,
        () -> computeIsPrecipitationRain(getRawPrecipitation(), getCameraY()), UPDATE_NOTIFIER);
    private static final SmoothedFloat TOUCH_MY_BODY = new SmoothedFloat(0f, 0.1f,
        CompatibilityUniforms::getHurtFactor, UPDATE_NOTIFIER);
    private static final SmoothedFloat SNEAK_SMOOTH = new SmoothedFloat(2f, 0.9f,
        CompatibilityUniforms::getSneakFactor, UPDATE_NOTIFIER);
    private static final SmoothedFloat BURNING_SMOOTH = new SmoothedFloat(1f, 2f,
        CompatibilityUniforms::getBurnFactor, UPDATE_NOTIFIER);
    private static final SmoothedFloat SPEED_SMOOTH = new SmoothedFloat(1f, 1.5f,
        CompatibilityUniforms::getVelocityPerFrameTime, UPDATE_NOTIFIER);

    private CompatibilityUniforms() {
    }

    static {
        UPDATE_NOTIFIER.addListener(CompatibilityUniforms::updateBiomeCache);
    }

    public static void onFrameStart() {
        UPDATE_NOTIFIER.onNewFrame();
    }

    public static float getTimeAngle() {
        return getWorldDayTime() / 24000f;
    }

    public static float getTimeBrightness() {
        return (float) Math.max(Math.sin(getTimeAngle() * Math.PI * 2.0), 0.0);
    }

    public static float getMoonBrightness() {
        return (float) Math.max(Math.sin(getTimeAngle() * Math.PI * -2.0), 0.0);
    }

    public static float getShadowFade() {
        double sunTerm = Math.abs(Math.abs(getSunAngle() - 0.5) - 0.25);
        return clamp01(1.0 - (sunTerm - 0.23) * 100.0);
    }

    public static float getBlindFactor() {
        float blindFactorSqrt = clamp01(GameplayUniforms.getBlindness() * 2.0f - 1.0f);
        return blindFactorSqrt * blindFactorSqrt;
    }

    public static float getRainStrengthS() {
        return RAIN_STRENGTH_S.getAsFloat();
    }

    public static float getRainStrengthShiningStars() {
        return RAIN_STRENGTH_SHINING.getAsFloat();
    }

    public static float getRainStrengthS2() {
        return RAIN_STRENGTH_S2.getAsFloat();
    }

    public static float getIsDry() {
        return IS_DRY.getAsFloat();
    }

    public static float getIsRainy() {
        return IS_RAINY.getAsFloat();
    }

    public static float getIsSnowy() {
        return IS_SNOWY.getAsFloat();
    }

    public static float getInDry() {
        return getIsDry();
    }

    public static float getInRainy() {
        return getIsRainy();
    }

    public static float getInSnowy() {
        return getIsSnowy();
    }

    public static float getIsEyeInCave() {
        return GameplayUniforms.isEyeInWater() == 0 ? EYE_IN_CAVE.getAsFloat() : 0f;
    }

    public static float getVelocity() {
        double[] current = CapturedRenderingState.INSTANCE.getCameraPosition();
        double[] previous = CapturedRenderingState.INSTANCE.getPreviousCameraPosition();
        return computeVelocity(current[0], current[1], current[2], previous[0], previous[1], previous[2]);
    }

    public static float getStarter() {
        return STARTER.getAsFloat();
    }

    public static float getFrameTimeSmooth() {
        return FRAME_TIME_SMOOTH.getAsFloat();
    }

    public static float getEyeBrightnessMUniform() {
        return EYE_BRIGHTNESS_M.getAsFloat();
    }

    public static float getEyeBrightnessM2() {
        return EYE_BRIGHTNESS_M2.getAsFloat();
    }

    public static float getRainFactor() {
        return RAIN_FACTOR.getAsFloat();
    }

    public static float getInSwamp() {
        return IN_SWAMP.getAsFloat();
    }

    public static float getBiomeTemperature() {
        if (cachedBiome == null) {
            return 0f;
        }
        return cachedBiome.getTemperature(cachedBiomePos);
    }

    public static float getDay() {
        return clamp01(5.4f - getAdjustedTime());
    }

    public static float getNight() {
        return clamp01(getAdjustedTime() - 6.0f);
    }

    public static float getDawnDusk() {
        return (1.0f - getDay()) - getNight();
    }

    public static float getShdFade() {
        double sunTerm = Math.abs(Math.abs(getSunAngle() - 0.5) - 0.25);
        return clamp01(1.0 - (sunTerm - 0.225) * 40.0);
    }

    public static float getIsPrecipitationRain() {
        return IS_PRECIP_RAIN.getAsFloat();
    }

    public static float getTouchMyBody() {
        return TOUCH_MY_BODY.getAsFloat();
    }

    public static float getSneakSmooth() {
        return SNEAK_SMOOTH.getAsFloat();
    }

    public static float getBurningSmooth() {
        return BURNING_SMOOTH.getAsFloat();
    }

    public static float getInBasaltDeltas() {
        return 0f;
    }

    public static float getInCrimsonForest() {
        return 0f;
    }

    public static float getInNetherWastes() {
        return isNetherWastesBiome(cachedBiome) ? 1f : 0f;
    }

    public static float getInSoulValley() {
        return 0f;
    }

    public static float getInWarpedForest() {
        return 0f;
    }

    public static float getInPaleGarden() {
        return 0f;
    }

    public static float getEffectStrength() {
        return computeEffectStrength(SPEED_SMOOTH.getAsFloat());
    }

    private static float getVelocityPerFrameTime() {
        float frameTime = SystemTimeUniforms.TIMER.getLastFrameTime();
        if (frameTime <= 0.0001f) {
            return 0f;
        }
        return getVelocity() / frameTime;
    }

    private static float computeEyeInCave() {
        World world = getWorld();
        Entity camera = getCamera();
        if (world == null || camera == null) {
            return 0f;
        }

        return computeEyeInCaveValue(camera.getPositionEyes(1.0f).y, getEyeSkyBrightness());
    }

    private static float getEyeBrightnessM() {
        return getEyeSkyBrightness() / 240f;
    }

    private static float getEyeBrightnessM2Raw() {
        return getEyeSkyBrightness() > 239f ? 1f : 0f;
    }

    private static float getEyeSkyBrightness() {
        World world = getWorld();
        Entity camera = getCamera();
        if (world == null || camera == null) {
            return 0f;
        }

        Vec3d eyes = camera.getPositionEyes(1.0f);
        BlockPos pos = new BlockPos(eyes);
        return world.getLightFor(EnumSkyBlock.SKY, pos) * 16f;
    }

    private static float getBurnFactor() {
        EntityPlayer player = getPlayer();
        return player != null && player.isBurning() ? 1f : 0f;
    }

    private static float getSneakFactor() {
        EntityPlayer player = getPlayer();
        return player != null && player.isSneaking() ? 1f : 0f;
    }

    private static float getHurtFactor() {
        EntityPlayer player = getPlayer();
        if (player == null) {
            return 0f;
        }
        return (player.hurtTime > 0 || player.deathTime > 0) ? 0.4f : 0f;
    }

    private static float getMovingFlag() {
        double[] curr = CapturedRenderingState.INSTANCE.getCameraPosition();
        double[] prev = CapturedRenderingState.INSTANCE.getPreviousCameraPosition();
        return computeMovingFlag(curr[0], curr[1], curr[2], prev[0], prev[1], prev[2]);
    }

    private static SmoothedFloat createStarter() {
        SmoothedFloat inner = new SmoothedFloat(0f, 31_536_000f,
            CompatibilityUniforms::getMovingFlag, UPDATE_NOTIFIER);
        return new SmoothedFloat(20f, 20f, inner, UPDATE_NOTIFIER);
    }

    private static SmoothedFloat createRainStrength(float up, float down) {
        return new SmoothedFloat(up, down, GameplayUniforms::getRainStrength, UPDATE_NOTIFIER);
    }

    private static float computeInSwamp() {
        if (cachedBiome == null) {
            return 0f;
        }
        return BiomeDictionary.hasType(cachedBiome, BiomeDictionary.Type.SWAMP) ? 1f : 0f;
    }

    static boolean isNetherWastesBiome(Biome biome) {
        return biome != null && isNetherWastesBiomeName(Biome.REGISTRY.getNameForObject(biome));
    }

    static boolean isNetherWastesBiomeName(ResourceLocation name) {
        return name != null && "minecraft".equals(name.getNamespace()) && "hell".equals(name.getPath());
    }

    static float computeEyeInCaveValue(double eyeY, float eyeSkyBrightness) {
        if (eyeY < 5.0) {
            return 1.0f - eyeSkyBrightness / 240f;
        }
        return 0f;
    }

    static float computeVelocity(double currentX, double currentY, double currentZ,
                                 double previousX, double previousY, double previousZ) {
        float dx = (float) (currentX - previousX);
        float dy = (float) (currentY - previousY);
        float dz = (float) (currentZ - previousZ);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static float computeMovementDeltaSum(double currentX, double currentY, double currentZ,
                                         double previousX, double previousY, double previousZ) {
        float dx = (float) (currentX - previousX);
        float dy = (float) (currentY - previousY);
        float dz = (float) (currentZ - previousZ);
        return Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
    }

    static float computeMovingFlag(double currentX, double currentY, double currentZ,
                                   double previousX, double previousY, double previousZ) {
        float sum = computeMovementDeltaSum(currentX, currentY, currentZ, previousX, previousY, previousZ);
        return (sum > 0.0f && sum < 1.0f) ? 1f : 0f;
    }

    static float computeEffectStrength(float smoothedSpeed) {
        return (float) (1.0 - Math.exp(-smoothedSpeed * 0.003906f));
    }

    static float legacyPrecipitation(boolean enableSnow, boolean canRain) {
        if (enableSnow) {
            return 2f;
        }
        return canRain ? 1f : 0f;
    }

    static float computeIsPrecipitationRain(float rawPrecipitation, float cameraY) {
        return rawPrecipitation == 1f && cameraY < 96f ? 1f : 0f;
    }

    static float getCameraY() {
        return (float) CapturedRenderingState.INSTANCE.getCameraPosition()[1];
    }

    private static float getAdjustedTime() {
        return Math.abs((((getWorldDayTime() / 1000.0f) + 6.0f) % 24.0f) - 12.0f);
    }

    private static double getSunAngle() {
        World world = getWorld();
        if (world == null) {
            return 0.0;
        }
        return world.getCelestialAngle(CapturedRenderingState.INSTANCE.getTickDelta());
    }

    private static int getWorldDayTime() {
        World world = getWorld();
        if (world == null) {
            return 0;
        }
        long time = world.getWorldTime();
        return getWorldDayTime(world.provider, time);
    }

    static int getWorldDayTime(WorldProvider provider, long timeOfDay) {
        DimensionType dimensionType = getDimensionType(provider);
        if (dimensionType == DimensionType.NETHER) {
            return 18000;
        }
        if (dimensionType == DimensionType.THE_END) {
            return 6000;
        }
        return (int) (timeOfDay % 24000L);
    }

    private static DimensionType getDimensionType(WorldProvider provider) {
        if (provider == null) {
            return null;
        }

        try {
            return provider.getDimensionType();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static float clamp01(double value) {
        return (float) MathHelper.clamp(value, 0.0, 1.0);
    }

    private static float getRawPrecipitation() {
        if (cachedBiome == null) {
            return 0f;
        }

        return legacyPrecipitation(cachedBiome.getEnableSnow(), cachedBiome.canRain());
    }

    private static void updateBiomeCache() {
        World world = getWorld();
        Entity camera = getCamera();
        if (world == null || camera == null) {
            cachedBiome = null;
            cachedBiomePos = BlockPos.ORIGIN;
            return;
        }

        BlockPos pos = new BlockPos(camera);
        cachedBiome = world.getBiome(pos);
        cachedBiomePos = pos;
    }

    private static World getWorld() {
        Minecraft minecraft = getMinecraft();
        return minecraft != null ? minecraft.world : null;
    }

    private static Entity getCamera() {
        Minecraft minecraft = getMinecraft();
        return minecraft != null ? minecraft.getRenderViewEntity() : null;
    }

    private static EntityPlayer getPlayer() {
        Minecraft minecraft = getMinecraft();
        return minecraft != null ? minecraft.player : null;
    }

    private static Minecraft getMinecraft() {
        return Minecraft.getMinecraft();
    }
}
