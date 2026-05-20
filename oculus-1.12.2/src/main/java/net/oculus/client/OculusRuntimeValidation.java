package net.oculus.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

import net.minecraft.client.AnvilConverterException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.WorldSummary;

import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.StartupQuery;

import net.oculus.Oculus;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.state.GameDataSuppliers;
import net.oculus.pipeline.InputAvailability;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPipeline;
import net.oculus.texture.pbr.PBRType;
import net.oculus.uniforms.BuiltinReplacementUniforms;
import net.oculus.uniforms.CapturedRenderingState;
import net.oculus.uniforms.CelestialUniforms;
import net.oculus.uniforms.CompatibilityUniforms;
import net.oculus.uniforms.GameplayUniforms;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Disabled-by-default runtime validation hook for unattended development smoke
 * runs. It is intentionally isolated from normal gameplay and only activates
 * when explicit JVM system properties are present.
 */
public final class OculusRuntimeValidation {
    public static final String AUTO_JOIN_WORLD_PROPERTY = "oculus.validation.autoJoinWorld";
    public static final String EXIT_AFTER_WORLD_TICKS_PROPERTY = "oculus.validation.exitAfterWorldTicks";
    public static final String PBR_TEXTURES_PROPERTY = "oculus.validation.pbrTextures";
    public static final String SCREENSHOT_WORLD_TICK_PROPERTY = "oculus.validation.screenshotWorldTick";
    public static final String INVENTORY_SCREENSHOT_WORLD_TICK_PROPERTY =
        "oculus.validation.inventoryScreenshotWorldTick";
    public static final String DUMP_RENDER_TARGETS_PROPERTY = "oculus.validation.dumpRenderTargets";
    public static final String DUMP_RENDER_TARGETS_WORLD_TICK_PROPERTY = "oculus.validation.dumpRenderTargetsWorldTick";
    public static final String THIRD_PERSON_VIEW_PROPERTY = "oculus.validation.thirdPersonView";
    public static final String WORLD_TIME_PROPERTY = "oculus.validation.worldTime";
    public static final String SHADER_PACK_PROPERTY = "oculus.validation.shaderPack";

    private static final Set<String> RELICTIUM_TERRAIN_SELECTIONS_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> RELICTIUM_TERRAIN_LOOKUPS_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> RELICTIUM_TERRAIN_VERTEX_FORMATS_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> RELICTIUM_SHADOW_TERRAIN_DRAWS_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> SHADOW_TERRAIN_LAYERS_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> SHADOW_DEPTH_READBACKS_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> RELICTIUM_SHADOW_VISIBILITY_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> PBR_SIMPLE_TEXTURES_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> PBR_ATLAS_TEXTURES_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> PBR_HOLDERS_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> RENDER_TARGET_DUMPS =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> GL_ERROR_CHECKPOINTS_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> LOCAL_PLAYER_RENDER_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> LOCAL_PLAYER_MODEL_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> LOCAL_PLAYER_MODEL_PARTS_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> LOCAL_PLAYER_HAND_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> WORLD_PROGRAM_SELECTIONS_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> LEGACY_ENTITY_PROGRAM_SYNCS_LOGGED =
        Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> INVENTORY_GUI_CAPTURE_WAITS =
        Collections.synchronizedSet(new HashSet<String>());
    private static volatile OculusRuntimeValidation activeInstance;
    private static volatile boolean worldActive;
    private static volatile int currentWorldTicks;

    private boolean screenshotQueued;
    private boolean joinAttempted;
    private boolean enteredWorldLogged;
    private boolean invalidExitValueLogged;
    private boolean invalidScreenshotValueLogged;
    private boolean invalidInventoryScreenshotValueLogged;
    private boolean invalidThirdPersonViewValueLogged;
    private boolean invalidWorldTimeValueLogged;
    private boolean worldStateLogged;
    private boolean pauseOnLostFocusDisabled;
    private boolean visualUniformStateLogged;
    private boolean screenshotCaptured;
    private boolean inventoryScreenshotQueued;
    private boolean inventoryScreenshotCaptured;
    private boolean shutdownRequested;
    private boolean thirdPersonViewApplied;
    private int worldTicks;
    private int lastScreenCloseTick = -1;
    private int inventoryScreenOpenTick = -1;

    public OculusRuntimeValidation() {
        activeInstance = this;
    }

    public static void logRelictiumTerrainOverrideSelected(String terrainPass,
                                                           String blockRenderPass,
                                                           String phase,
                                                           String programName) {
        if (getTrimmedProperty(AUTO_JOIN_WORLD_PROPERTY) == null) {
            return;
        }

        String key = terrainPass + ":" + blockRenderPass + ":" + phase;
        if (RELICTIUM_TERRAIN_SELECTIONS_LOGGED.add(key)) {
            Oculus.LOGGER.info(
                "Oculus runtime validation selected Relictium terrain override {} for BlockRenderPass {} / phase {} using {}",
                terrainPass,
                blockRenderPass,
                phase,
                programName
            );
        }
    }

    public static void logRelictiumTerrainOverrideLookup(String terrainPass,
                                                         String blockRenderPass,
                                                         String result) {
        if (getTrimmedProperty(AUTO_JOIN_WORLD_PROPERTY) == null) {
            return;
        }

        String key = terrainPass + ":" + blockRenderPass + ":" + result;
        if (RELICTIUM_TERRAIN_LOOKUPS_LOGGED.add(key)) {
            Oculus.LOGGER.info(
                "Oculus runtime validation looked up Relictium terrain override {} for BlockRenderPass {}: {}",
                terrainPass,
                blockRenderPass,
                result
            );
        }
    }

    public static void logRelictiumTerrainVertexFormat(String originalVertexType,
                                                       String resolvedVertexType,
                                                       boolean extendedVertexFormatSetting,
                                                       boolean usingOculusTerrainVertexType) {
        if (!isEnabled()) {
            return;
        }

        String key = originalVertexType + ":" + resolvedVertexType + ":" + extendedVertexFormatSetting
            + ":" + usingOculusTerrainVertexType;
        if (RELICTIUM_TERRAIN_VERTEX_FORMATS_LOGGED.add(key)) {
            Oculus.LOGGER.info(
                "Oculus runtime validation selected Relictium terrain vertex type: original={} resolved={} extendedSetting={} oculusTerrainFormat={}",
                originalVertexType,
                resolvedVertexType,
                extendedVertexFormatSetting,
                usingOculusTerrainVertexType
            );
        }
    }

    public static void logRelictiumShadowTerrainDraw(String pass,
                                                     boolean hasNext,
                                                     String graphicsState,
                                                     int visibleFaces,
                                                     int chunkX,
                                                     int chunkY,
                                                     int chunkZ,
                                                     float modelOffsetX,
                                                     float modelOffsetY,
                                                     float modelOffsetZ,
                                                     double cameraX,
                                                     double cameraY,
                                                     double cameraZ,
                                                     int framebuffer,
                                                     int program,
                                                     boolean depthTest,
                                                     boolean depthMask,
                                                     boolean blend,
                                                     int viewportWidth,
                                                     int viewportHeight) {
        if (!isEnabled()) {
            return;
        }

        String safePass = pass == null ? "unknown" : pass;
        String safeGraphicsState = graphicsState == null ? "null" : graphicsState;
        String key = safePass + ":" + hasNext + ":" + safeGraphicsState + ":" + visibleFaces
            + ":" + chunkX + ":" + chunkY + ":" + chunkZ
            + ":" + framebuffer + ":" + program + ":" + depthTest + ":" + depthMask + ":" + blend
            + ":" + viewportWidth + "x" + viewportHeight;
        if (RELICTIUM_SHADOW_TERRAIN_DRAWS_LOGGED.add(key)) {
            Oculus.LOGGER.info(
                "Oculus runtime validation Relictium shadow terrain draw pass={} hasNext={} graphicsState={} visibleFaces={} chunk=({}, {}, {}) modelOffset=({}, {}, {}) camera=({}, {}, {}) framebuffer={} program={} depthTest={} depthMask={} blend={} viewport={}x{} worldTick={}",
                safePass,
                hasNext,
                safeGraphicsState,
                visibleFaces,
                chunkX,
                chunkY,
                chunkZ,
                modelOffsetX,
                modelOffsetY,
                modelOffsetZ,
                cameraX,
                cameraY,
                cameraZ,
                framebuffer,
                program,
                depthTest,
                depthMask,
                blend,
                viewportWidth,
                viewportHeight,
                currentWorldTicks
            );
        }
    }

    public static void logShadowTerrainLayerRendered(String layer) {
        if (!isEnabled()) {
            return;
        }

        if (SHADOW_TERRAIN_LAYERS_LOGGED.add(layer)) {
            Oculus.LOGGER.info("Oculus runtime validation rendering shadow terrain layer {}", layer);
        }
    }

    public static boolean isEnabled() {
        return getTrimmedProperty(AUTO_JOIN_WORLD_PROPERTY) != null;
    }

    public static boolean isPbrValidationEnabled() {
        return isEnabled() || getTrimmedProperty(PBR_TEXTURES_PROPERTY) != null;
    }

    public static boolean isRenderTargetDumpEnabled() {
        return getTrimmedProperty(DUMP_RENDER_TARGETS_PROPERTY) != null;
    }

    public static String getShaderPackOverride() {
        return getTrimmedProperty(SHADER_PACK_PROPERTY);
    }

    public static void logWorldProgramSelection(String phase,
                                                String condition,
                                                InputAvailability rawAvailability,
                                                InputAvailability availability,
                                                String programId,
                                                String sourceName,
                                                String programName) {
        if (!isEnabled()) {
            return;
        }

        String safePhase = phase == null ? "null" : phase;
        String safeCondition = condition == null ? "null" : condition;
        if (!shouldLogProgramSelection(safeCondition)) {
            return;
        }

        String key = safePhase + ":" + safeCondition + ":" + availability + ":" + programId + ":" + sourceName
            + ":" + programName;
        if (!WORLD_PROGRAM_SELECTIONS_LOGGED.add(key)) {
            return;
        }

        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        Oculus.LOGGER.info(
            "Oculus runtime validation world program phase={} condition={} rawInputs={} effectiveInputs={} id={} source={} program={} framebuffer={} glProgram={} activeTexture={} texture2D={} depthTest={} depthMask={} blend={} viewport={}x{} worldTick={}",
            safePhase,
            safeCondition,
            rawAvailability,
            availability,
            programId == null ? "null" : programId,
            sourceName == null ? "null" : sourceName,
            programName == null ? "null" : programName,
            OculusRenderSystem.getFramebufferBinding(),
            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
            GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
            GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
            GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
            GL11.glIsEnabled(GL11.GL_BLEND),
            viewport.get(2),
            viewport.get(3),
            currentWorldTicks
        );
    }

    private static boolean shouldLogProgramSelection(String condition) {
        return "ENTITIES".equals(condition)
            || "ENTITIES_TRANSLUCENT".equals(condition)
            || "BLOCK_ENTITIES".equals(condition)
            || "HAND_OPAQUE".equals(condition)
            || "HAND_TRANSLUCENT".equals(condition)
            || "SHADOW".equals(condition);
    }

    public static void logLegacyEntityProgramSync(String stage,
                                                  String phase,
                                                  String condition,
                                                  boolean prepared,
                                                  boolean renderingWorld,
                                                  boolean fullscreen,
                                                  boolean postChain,
                                                  boolean shadersCompiled,
                                                  boolean mainBound,
                                                  boolean gbufferBound,
                                                  boolean shadow,
                                                  String programName,
                                                  int expectedProgram,
                                                  int framebuffer,
                                                  int glProgram) {
        if (!isEnabled()) {
            return;
        }

        String key = stage + ":" + phase + ":" + condition + ":" + prepared + ":" + renderingWorld
            + ":" + fullscreen + ":" + postChain + ":" + shadersCompiled + ":" + mainBound + ":"
            + gbufferBound + ":" + shadow + ":" + programName + ":" + expectedProgram + ":" + framebuffer
            + ":" + glProgram;
        if (!LEGACY_ENTITY_PROGRAM_SYNCS_LOGGED.add(key)) {
            return;
        }

        Oculus.LOGGER.info(
            "Oculus runtime validation legacy entity program sync {}: phase={} condition={} prepared={} renderingWorld={} fullscreen={} postChain={} shadersCompiled={} mainBound={} gbufferBound={} shadow={} program={} expectedProgram={} framebuffer={} glProgram={} worldTick={}",
            stage,
            phase == null ? "null" : phase,
            condition == null ? "null" : condition,
            prepared,
            renderingWorld,
            fullscreen,
            postChain,
            shadersCompiled,
            mainBound,
            gbufferBound,
            shadow,
            programName == null ? "null" : programName,
            expectedProgram,
            framebuffer,
            glProgram,
            currentWorldTicks
        );
    }

    public static int drainGlErrorsAtCheckpoint(String checkpoint) {
        if (!isEnabled()) {
            return GL11.GL_NO_ERROR;
        }

        int count = 0;
        int lastError = GL11.GL_NO_ERROR;
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR) {
            count++;
            lastError = error;
        }

        if (count > 0) {
            String safeCheckpoint = checkpoint == null ? "unknown" : checkpoint;
            String key = safeCheckpoint + ":" + lastError;
            if (GL_ERROR_CHECKPOINTS_LOGGED.add(key)) {
                Oculus.LOGGER.warn(
                    "Oculus runtime validation GL error at {}: last={} count={}",
                    safeCheckpoint,
                    lastError,
                    count
                );
            }
        }

        return lastError;
    }

    public static void logLocalPlayerEntityRender(Entity entity,
                                                  Entity managerRenderViewEntity,
                                                  int managerThirdPersonView,
                                                  boolean shadowPass,
                                                  double x,
                                                  double y,
                                                  double z) {
        if (!isEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || entity == null || entity != minecraft.player) {
            return;
        }

        String pass = shadowPass ? "shadow" : "main";
        if (LOCAL_PLAYER_RENDER_LOGGED.add(pass)) {
            Entity renderViewEntity = minecraft.getRenderViewEntity();
            Oculus.LOGGER.info(
                "Oculus runtime validation local player reached RenderManager pass={}: gameThirdPersonView={} managerThirdPersonView={} minecraftRenderViewEntity={} managerRenderViewEntity={} offset=({}, {}, {}) worldTick={}",
                pass,
                minecraft.gameSettings == null ? -1 : minecraft.gameSettings.thirdPersonView,
                managerThirdPersonView,
                renderViewEntity == null ? "null" : renderViewEntity.getClass().getSimpleName(),
                managerRenderViewEntity == null ? "null" : managerRenderViewEntity.getClass().getSimpleName(),
                x,
                y,
                z,
                currentWorldTicks
            );
        }
    }

    public static void logLocalPlayerRendererCall(Entity entity, String rendererName, boolean shadowPass) {
        if (!isEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || entity == null || entity != minecraft.player) {
            return;
        }

        String pass = shadowPass ? "shadow" : "main";
        String key = pass + ":" + rendererName;
        if (LOCAL_PLAYER_RENDER_LOGGED.add(key)) {
            Oculus.LOGGER.info(
                "Oculus runtime validation local player invoking renderer pass={}: renderer={} worldTick={}",
                pass,
                rendererName == null ? "null" : rendererName,
                currentWorldTicks
            );
        }
    }

    public static void logLocalPlayerModelRender(Entity entity, String stage) {
        if (!isEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || entity == null || entity != minecraft.player) {
            return;
        }

        boolean shadowPass = net.oculus.pipeline.shadow.ShadowRenderingState.isActive();
        String pass = shadowPass ? "shadow" : "main";
        String safeStage = stage == null ? "unknown" : stage;
        String key = pass + ":" + safeStage;
        if (!LOCAL_PLAYER_MODEL_LOGGED.add(key)) {
            return;
        }

        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int textureWidth = 0;
        int textureHeight = 0;
        if (texture > 0) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            textureWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            textureHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        }

        float[] color = BuiltinReplacementUniforms.getColorModulator();
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        FloatBuffer modelView = readCurrentMatrix(GL11.GL_MODELVIEW_MATRIX);
        FloatBuffer projection = readCurrentMatrix(GL11.GL_PROJECTION_MATRIX);
        ByteBuffer colorMask = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
        Oculus.LOGGER.info(
            "Oculus runtime validation local player model pass={} {}: phase={} pipelineShadow={} entityId={} texture={} textureSize={}x{} depthTest={} depthFunc={} depthMask={} colorMask=({}, {}, {}, {}) alphaTest={} alphaFunc=({}, {}) cull={} blend={} blendFunc=({}, {}, {}, {}) color=({}, {}, {}, {}) framebuffer={} glProgram={} matrixMode={} modelViewT=({}, {}, {}) projection[0,5,10,11,14]=({}, {}, {}, {}, {}) worldTick={}",
            pass,
            safeStage,
            pipeline == null || pipeline.getPhase() == null ? "null" : pipeline.getPhase().name(),
            pipeline != null && pipeline.isRenderingShadowPass(),
            CapturedRenderingState.INSTANCE.getCurrentEntity(),
            texture,
            textureWidth,
            textureHeight,
            GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
            GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
            GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
            colorMask.get(0) != 0,
            colorMask.get(1) != 0,
            colorMask.get(2) != 0,
            colorMask.get(3) != 0,
            GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
            GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC),
            GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF),
            GL11.glIsEnabled(GL11.GL_CULL_FACE),
            GL11.glIsEnabled(GL11.GL_BLEND),
            GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
            GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
            GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
            GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
            color[0],
            color[1],
            color[2],
            color[3],
            OculusRenderSystem.getFramebufferBinding(),
            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            GL11.glGetInteger(GL11.GL_MATRIX_MODE),
            component(modelView, 12),
            component(modelView, 13),
            component(modelView, 14),
            component(projection, 0),
            component(projection, 5),
            component(projection, 10),
            component(projection, 11),
            component(projection, 14),
            currentWorldTicks
        );
    }

    public static void logLocalPlayerModelParts(AbstractClientPlayer player, ModelPlayer model, String stage) {
        if (!isEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || player == null || model == null || player != minecraft.player) {
            return;
        }

        boolean shadowPass = net.oculus.pipeline.shadow.ShadowRenderingState.isActive();
        String pass = shadowPass ? "shadow" : "main";
        String safeStage = stage == null ? "unknown" : stage;
        String key = pass + ":" + safeStage;
        if (!LOCAL_PLAYER_MODEL_PARTS_LOGGED.add(key)) {
            return;
        }

        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        Oculus.LOGGER.info(
            "Oculus runtime validation local player model parts pass={} {}: phase={} spectator={} invisible={} user={} thirdPersonView={} yaw={} headYaw={} renderYaw={} head={} headwear={} body={} rightArm={} leftArm={} rightLeg={} leftLeg={} bodyWear={} rightArmWear={} leftArmWear={} rightLegWear={} leftLegWear={} hiddenBase=({}, {}, {}, {}, {}, {}, {}) framebuffer={} glProgram={} worldTick={}",
            pass,
            safeStage,
            pipeline == null || pipeline.getPhase() == null ? "null" : pipeline.getPhase().name(),
            player.isSpectator(),
            player.isInvisible(),
            player.isUser(),
            minecraft.gameSettings == null ? -1 : minecraft.gameSettings.thirdPersonView,
            player.rotationYaw,
            player.rotationYawHead,
            player.renderYawOffset,
            model.bipedHead.showModel,
            model.bipedHeadwear.showModel,
            model.bipedBody.showModel,
            model.bipedRightArm.showModel,
            model.bipedLeftArm.showModel,
            model.bipedRightLeg.showModel,
            model.bipedLeftLeg.showModel,
            model.bipedBodyWear.showModel,
            model.bipedRightArmwear.showModel,
            model.bipedLeftArmwear.showModel,
            model.bipedRightLegwear.showModel,
            model.bipedLeftLegwear.showModel,
            model.bipedHead.isHidden,
            model.bipedHeadwear.isHidden,
            model.bipedBody.isHidden,
            model.bipedRightArm.isHidden,
            model.bipedLeftArm.isHidden,
            model.bipedRightLeg.isHidden,
            model.bipedLeftLeg.isHidden,
            OculusRenderSystem.getFramebufferBinding(),
            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            currentWorldTicks
        );
    }

    public static void logLocalPlayerHandRender(AbstractClientPlayer player, String stage) {
        if (!isEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || player == null || player != minecraft.player) {
            return;
        }

        String safeStage = stage == null ? "unknown" : stage;
        if (!LOCAL_PLAYER_HAND_LOGGED.add(safeStage)) {
            return;
        }

        float[] color = BuiltinReplacementUniforms.getColorModulator();
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        Oculus.LOGGER.info(
            "Oculus runtime validation local player hand {}: phase={} thirdPersonView={} depthTest={} depthMask={} cull={} blend={} color=({}, {}, {}, {}) framebuffer={} glProgram={} worldTick={}",
            safeStage,
            pipeline == null || pipeline.getPhase() == null ? "null" : pipeline.getPhase().name(),
            minecraft.gameSettings == null ? -1 : minecraft.gameSettings.thirdPersonView,
            GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
            GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
            GL11.glIsEnabled(GL11.GL_CULL_FACE),
            GL11.glIsEnabled(GL11.GL_BLEND),
            color[0],
            color[1],
            color[2],
            color[3],
            OculusRenderSystem.getFramebufferBinding(),
            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            currentWorldTicks
        );
    }

    public static void dumpRenderTargetTexture(String label, int texture, int width, int height) {
        if (!shouldDumpRenderTargetsNow() || texture <= 0 || width <= 0 || height <= 0) {
            return;
        }

        String safeLabel = sanitizeFileName(label == null ? "unknown" : label);
        String key = safeLabel + ":" + texture + ":" + width + "x" + height;
        if (!RENDER_TARGET_DUMPS.add(key)) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameDir == null) {
            return;
        }

        final int[] textureDimensions = new int[] {width, height};
        final int[] textureInfo = new int[7];
        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                int textureWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int textureHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                if (textureWidth > 0 && textureHeight > 0) {
                    textureDimensions[0] = textureWidth;
                    textureDimensions[1] = textureHeight;
                }
                textureInfo[0] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
                textureInfo[1] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_RED_SIZE);
                textureInfo[2] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_GREEN_SIZE);
                textureInfo[3] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_BLUE_SIZE);
                textureInfo[4] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_ALPHA_SIZE);
                textureInfo[5] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_RED_TYPE);
                textureInfo[6] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_GREEN_TYPE);
            });
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn(
                "Oculus runtime validation failed to inspect render target dump {} texture={} logicalSize={}x{}",
                safeLabel,
                texture,
                width,
                height,
                exception
            );
            return;
        }

        int readWidth = textureDimensions[0];
        int readHeight = textureDimensions[1];
        ByteBuffer pixels = BufferUtils.createByteBuffer(readWidth * readHeight * 4);
        int glError;
        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            });
            glError = GL11.glGetError();
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn(
                "Oculus runtime validation failed to read render target dump {} texture={} size={}x{}",
                safeLabel,
                texture,
                width,
                height,
                exception
            );
            return;
        }

        if (glError != GL11.GL_NO_ERROR) {
            Oculus.LOGGER.warn(
                "Oculus runtime validation skipped render target dump {} texture={} size={}x{} after GL error {}",
                safeLabel,
                texture,
                readWidth,
                readHeight,
                glError
            );
            return;
        }

        logColorTextureFloatStats(safeLabel, texture, readWidth, readHeight);

        BufferedImage image = new BufferedImage(readWidth, readHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < readHeight; y++) {
            int sourceY = readHeight - 1 - y;
            for (int x = 0; x < readWidth; x++) {
                int offset = (sourceY * readWidth + x) * 4;
                int red = pixels.get(offset) & 0xFF;
                int green = pixels.get(offset + 1) & 0xFF;
                int blue = pixels.get(offset + 2) & 0xFF;
                int alpha = pixels.get(offset + 3) & 0xFF;
                image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }

        File directory = new File(minecraft.gameDir, "screenshots");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Oculus.LOGGER.warn("Oculus runtime validation could not create screenshot directory {}", directory.getPath());
            return;
        }

        File output = new File(directory, "oculus-validation-rt-" + System.currentTimeMillis() + "-" + safeLabel + ".png");
        try {
            ImageIO.write(image, "png", output);
            Oculus.LOGGER.info(
                "Oculus runtime validation dumped render target {} texture={} logicalSize={}x{} textureSize={}x{} internalFormat={} components=({}, {}, {}, {}) componentTypes=({}, {}) to {}",
                safeLabel,
                texture,
                width,
                height,
                readWidth,
                readHeight,
                glFormatName(textureInfo[0]),
                textureInfo[1],
                textureInfo[2],
                textureInfo[3],
                textureInfo[4],
                glFormatName(textureInfo[5]),
                glFormatName(textureInfo[6]),
                output.getPath()
            );
        } catch (IOException exception) {
            Oculus.LOGGER.warn("Oculus runtime validation failed to write render target dump {}", output.getPath(), exception);
        }
    }

    private static void logColorTextureFloatStats(String label, int texture, int width, int height) {
        int count = width * height;
        if (count <= 0) {
            return;
        }

        FloatBuffer pixels = BufferUtils.createFloatBuffer(count * 4);
        int glError;
        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            });
            glError = GL11.glGetError();
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn(
                "Oculus runtime validation failed to read float color stats {} texture={} size={}x{}",
                label,
                texture,
                width,
                height,
                exception
            );
            return;
        }

        if (glError != GL11.GL_NO_ERROR) {
            Oculus.LOGGER.warn(
                "Oculus runtime validation skipped float color stats {} texture={} size={}x{} after GL error {}",
                label,
                texture,
                width,
                height,
                glError
            );
            return;
        }

        float minRed = Float.POSITIVE_INFINITY;
        float minGreen = Float.POSITIVE_INFINITY;
        float minBlue = Float.POSITIVE_INFINITY;
        float maxRed = Float.NEGATIVE_INFINITY;
        float maxGreen = Float.NEGATIVE_INFINITY;
        float maxBlue = Float.NEGATIVE_INFINITY;
        double sumRed = 0.0D;
        double sumGreen = 0.0D;
        double sumBlue = 0.0D;
        int nonFinite = 0;
        int negative = 0;
        int overOne = 0;

        for (int i = 0; i < count; i++) {
            int offset = i * 4;
            float red = pixels.get(offset);
            float green = pixels.get(offset + 1);
            float blue = pixels.get(offset + 2);
            if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue)) {
                nonFinite++;
                continue;
            }

            minRed = Math.min(minRed, red);
            minGreen = Math.min(minGreen, green);
            minBlue = Math.min(minBlue, blue);
            maxRed = Math.max(maxRed, red);
            maxGreen = Math.max(maxGreen, green);
            maxBlue = Math.max(maxBlue, blue);
            sumRed += red;
            sumGreen += green;
            sumBlue += blue;
            if (red < 0.0F || green < 0.0F || blue < 0.0F) {
                negative++;
            }
            if (red > 1.0F || green > 1.0F || blue > 1.0F) {
                overOne++;
            }
        }

        if (minRed == Float.POSITIVE_INFINITY) {
            minRed = Float.NaN;
            minGreen = Float.NaN;
            minBlue = Float.NaN;
            maxRed = Float.NaN;
            maxGreen = Float.NaN;
            maxBlue = Float.NaN;
        }

        int validCount = Math.max(1, count - nonFinite);
        int centerOffset = ((height / 2) * width + (width / 2)) * 4;
        int lowerCenterOffset = ((Math.min(height - 1, (height * 3) / 4)) * width + (width / 2)) * 4;
        Oculus.LOGGER.info(
            "Oculus runtime validation color float stats {} texture={} size={}x{} nonFinite={} negative={} overOne={} min=({}, {}, {}) max=({}, {}, {}) mean=({}, {}, {}) center=({}, {}, {}) lowerCenter=({}, {}, {})",
            label,
            texture,
            width,
            height,
            nonFinite,
            negative,
            overOne,
            minRed,
            minGreen,
            minBlue,
            maxRed,
            maxGreen,
            maxBlue,
            sumRed / validCount,
            sumGreen / validCount,
            sumBlue / validCount,
            pixels.get(centerOffset),
            pixels.get(centerOffset + 1),
            pixels.get(centerOffset + 2),
            pixels.get(lowerCenterOffset),
            pixels.get(lowerCenterOffset + 1),
            pixels.get(lowerCenterOffset + 2)
        );
    }

    private static FloatBuffer readCurrentMatrix(int matrixName) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(matrixName, buffer);
        return buffer;
    }

    public static void dumpDepthTexture(String label, int texture, int width, int height) {
        if (!shouldDumpRenderTargetsNow() || texture <= 0 || width <= 0 || height <= 0) {
            return;
        }

        String safeLabel = sanitizeFileName(label == null ? "depth" : label);
        String key = "depth:" + safeLabel + ":" + texture + ":" + width + "x" + height;
        if (!RENDER_TARGET_DUMPS.add(key)) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameDir == null) {
            return;
        }

        final int[] textureDimensions = new int[] {width, height};
        final int[] textureInfo = new int[3];
        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                int textureWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int textureHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                if (textureWidth > 0 && textureHeight > 0) {
                    textureDimensions[0] = textureWidth;
                    textureDimensions[1] = textureHeight;
                }
                textureInfo[0] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
                textureInfo[1] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL14.GL_TEXTURE_DEPTH_SIZE);
                textureInfo[2] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_DEPTH_TYPE);
            });
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn(
                "Oculus runtime validation failed to inspect depth dump {} texture={} logicalSize={}x{}",
                safeLabel,
                texture,
                width,
                height,
                exception
            );
            return;
        }

        int readWidth = textureDimensions[0];
        int readHeight = textureDimensions[1];
        FloatBuffer pixels = BufferUtils.createFloatBuffer(readWidth * readHeight);
        int glError;
        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, pixels);
            });
            glError = GL11.glGetError();
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn(
                "Oculus runtime validation failed to read depth dump {} texture={} size={}x{}",
                safeLabel,
                texture,
                readWidth,
                readHeight,
                exception
            );
            return;
        }

        if (glError != GL11.GL_NO_ERROR) {
            Oculus.LOGGER.warn(
                "Oculus runtime validation skipped depth dump {} texture={} size={}x{} after GL error {}",
                safeLabel,
                texture,
                readWidth,
                readHeight,
                glError
            );
            return;
        }

        logDepthStats(safeLabel, texture, readWidth, readHeight, pixels);

        BufferedImage image = new BufferedImage(readWidth, readHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < readHeight; y++) {
            int sourceY = readHeight - 1 - y;
            for (int x = 0; x < readWidth; x++) {
                float depth = pixels.get(sourceY * readWidth + x);
                int gray = depthToVisualization(depth);
                image.setRGB(x, y, 0xFF000000 | (gray << 16) | (gray << 8) | gray);
            }
        }

        File directory = new File(minecraft.gameDir, "screenshots");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Oculus.LOGGER.warn("Oculus runtime validation could not create screenshot directory {}", directory.getPath());
            return;
        }

        File output = new File(directory, "oculus-validation-depth-" + System.currentTimeMillis() + "-" + safeLabel + ".png");
        try {
            ImageIO.write(image, "png", output);
            Oculus.LOGGER.info(
                "Oculus runtime validation dumped depth texture {} texture={} logicalSize={}x{} textureSize={}x{} internalFormat={} depthBits={} depthType={} to {}",
                safeLabel,
                texture,
                width,
                height,
                readWidth,
                readHeight,
                glFormatName(textureInfo[0]),
                textureInfo[1],
                glFormatName(textureInfo[2]),
                output.getPath()
            );
        } catch (IOException exception) {
            Oculus.LOGGER.warn("Oculus runtime validation failed to write depth dump {}", output.getPath(), exception);
        }
    }

    private static void logDepthStats(String label, int texture, int width, int height, FloatBuffer pixels) {
        int count = width * height;
        if (count <= 0) {
            return;
        }

        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        double sum = 0.0D;
        int nonClear = 0;
        int nonFinite = 0;

        for (int i = 0; i < count; i++) {
            float depth = pixels.get(i);
            if (!Float.isFinite(depth)) {
                nonFinite++;
                continue;
            }

            min = Math.min(min, depth);
            max = Math.max(max, depth);
            sum += depth;
            if (depth < 0.9999F) {
                nonClear++;
            }
        }

        if (min == Float.POSITIVE_INFINITY) {
            min = Float.NaN;
            max = Float.NaN;
        }

        int centerX = width / 2;
        int centerY = height / 2;
        int lowerCenterY = Math.min(height - 1, (height * 3) / 4);
        float centerDepth = pixels.get(centerY * width + centerX);
        float lowerCenterDepth = pixels.get(lowerCenterY * width + centerX);

        Oculus.LOGGER.info(
            "Oculus runtime validation depth stats {} texture={} size={}x{} nonClear={} nonFinite={} min={} max={} mean={} center={} lowerCenter={}",
            label,
            texture,
            width,
            height,
            nonClear,
            nonFinite,
            min,
            max,
            sum / Math.max(1, count - nonFinite),
            centerDepth,
            lowerCenterDepth
        );
    }

    private static int depthToVisualization(float depth) {
        if (Float.isNaN(depth)) {
            return 255;
        }
        float value = 1.0F - Math.max(0.0F, Math.min(1.0F, depth));
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }

    private static String glFormatName(int value) {
        switch (value) {
            case GL11.GL_BYTE:
                return "GL_BYTE";
            case GL11.GL_UNSIGNED_BYTE:
                return "GL_UNSIGNED_BYTE";
            case GL11.GL_SHORT:
                return "GL_SHORT";
            case GL11.GL_UNSIGNED_SHORT:
                return "GL_UNSIGNED_SHORT";
            case GL11.GL_INT:
                return "GL_INT";
            case GL11.GL_UNSIGNED_INT:
                return "GL_UNSIGNED_INT";
            case GL11.GL_FLOAT:
                return "GL_FLOAT";
            case GL11.GL_RGB:
                return "GL_RGB";
            case GL11.GL_RGBA:
                return "GL_RGBA";
            case GL11.GL_RGB8:
                return "GL_RGB8";
            case GL11.GL_RGBA8:
                return "GL_RGBA8";
            case GL11.GL_RGB16:
                return "GL_RGB16";
            case GL11.GL_RGBA16:
                return "GL_RGBA16";
            case GL11.GL_DEPTH_COMPONENT:
                return "GL_DEPTH_COMPONENT";
            case GL14.GL_DEPTH_COMPONENT16:
                return "GL_DEPTH_COMPONENT16";
            case GL14.GL_DEPTH_COMPONENT24:
                return "GL_DEPTH_COMPONENT24";
            case GL14.GL_DEPTH_COMPONENT32:
                return "GL_DEPTH_COMPONENT32";
            case GL30.GL_HALF_FLOAT:
                return "GL_HALF_FLOAT";
            case GL30.GL_R11F_G11F_B10F:
                return "GL_R11F_G11F_B10F";
            case GL30.GL_RGB16F:
                return "GL_RGB16F";
            case GL30.GL_RGBA16F:
                return "GL_RGBA16F";
            case GL30.GL_RGBA32F:
                return "GL_RGBA32F";
            case GL30.GL_DEPTH_COMPONENT32F:
                return "GL_DEPTH_COMPONENT32F";
            default:
                return "0x" + Integer.toHexString(value);
        }
    }

    private static String sanitizeFileName(String label) {
        return label.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static boolean shouldDumpRenderTargetsNow() {
        if (!isRenderTargetDumpEnabled()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.world == null || minecraft.player == null || minecraft.currentScreen != null) {
            return false;
        }

        int requestedWorldTick = getRenderTargetDumpWorldTick();
        return requestedWorldTick < 0 || (worldActive && currentWorldTicks >= requestedWorldTick);
    }

    private static int getRenderTargetDumpWorldTick() {
        String rawValue = getTrimmedProperty(DUMP_RENDER_TARGETS_WORLD_TICK_PROPERTY);
        if (rawValue == null) {
            return -1;
        }

        try {
            return Math.max(0, Integer.parseInt(rawValue));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public static void logShadowDepthReadback(int attempt,
                                              int resolution,
                                              int samples,
                                              int nonClearSamples,
                                              float minDepth,
                                              float maxDepth,
                                              int glError) {
        logShadowDepthReadback("texture", -1, attempt, resolution, samples, nonClearSamples,
            minDepth, maxDepth, glError);
    }

    public static void logShadowDepthReadback(String source,
                                              int framebuffer,
                                              int attempt,
                                              int resolution,
                                              int samples,
                                              int nonClearSamples,
                                              float minDepth,
                                              float maxDepth,
                                              int glError) {
        if (!isEnabled()) {
            return;
        }

        String safeSource = source == null ? "unknown" : source;
        String key = safeSource + ":" + framebuffer + ":" + attempt + ":" + resolution + ":" + samples
            + ":" + nonClearSamples + ":" + minDepth + ":" + maxDepth + ":" + glError;
        if (SHADOW_DEPTH_READBACKS_LOGGED.add(key)) {
            Oculus.LOGGER.info(
                "Oculus runtime validation sampled shadow depth output source={} framebuffer={} attempt={} resolution={} samples={} nonClear={} minDepth={} maxDepth={} glError={}",
                safeSource,
                framebuffer,
                attempt,
                resolution,
                samples,
                nonClearSamples,
                minDepth,
                maxDepth,
                glError
            );
        }
    }

    public static void logRelictiumShadowVisibility(String event) {
        if (!isEnabled()) {
            return;
        }

        if (RELICTIUM_SHADOW_VISIBILITY_LOGGED.add(event)) {
            Oculus.LOGGER.info("Oculus runtime validation Relictium shadow visibility: {}", event);
        }
    }

    public static void logPBRSimpleTextureLoaded(String baseLocation,
                                                 PBRType pbrType,
                                                 String pbrLocation,
                                                 int textureId) {
        if (!isPbrValidationEnabled()) {
            return;
        }

        String key = baseLocation + ":" + pbrType + ":" + pbrLocation + ":" + textureId;
        if (PBR_SIMPLE_TEXTURES_LOGGED.add(key)) {
            Oculus.LOGGER.info(
                "Oculus runtime validation loaded simple {} PBR texture {} for {} as GL texture {}",
                pbrType,
                pbrLocation,
                baseLocation,
                textureId
            );
        }
    }

    public static void logPBRAtlasTextureUploaded(String atlasBasePath,
                                                  PBRType pbrType,
                                                  int textureId,
                                                  int atlasWidth,
                                                  int atlasHeight,
                                                  int mipLevel,
                                                  int spriteCount,
                                                  int animatedSpriteCount) {
        if (!isPbrValidationEnabled()) {
            return;
        }

        String key = atlasBasePath + ":" + pbrType + ":" + textureId + ":" + atlasWidth + ":" + atlasHeight + ":" + mipLevel
            + ":" + spriteCount + ":" + animatedSpriteCount;
        if (PBR_ATLAS_TEXTURES_LOGGED.add(key)) {
            Oculus.LOGGER.info(
                "Oculus runtime validation uploaded {} PBR atlas for {} as GL texture {} ({}x{}, mipLevels={}, sprites={}, animated={})",
                pbrType,
                atlasBasePath,
                textureId,
                atlasWidth,
                atlasHeight,
                mipLevel,
                spriteCount,
                animatedSpriteCount
            );
        }
    }

    public static void logPBRHolderResolved(int baseTextureId,
                                            String textureClassName,
                                            boolean loadedNormal,
                                            boolean loadedSpecular,
                                            int normalTextureId,
                                            int specularTextureId) {
        if (!isPbrValidationEnabled()) {
            return;
        }

        String key = baseTextureId + ":" + textureClassName + ":" + loadedNormal + ":" + loadedSpecular
            + ":" + normalTextureId + ":" + specularTextureId;
        if (PBR_HOLDERS_LOGGED.add(key)) {
            Oculus.LOGGER.info(
                "Oculus runtime validation resolved PBR holder for base GL texture {} ({}) normalLoaded={} specularLoaded={} normalTexture={} specularTexture={}",
                baseTextureId,
                textureClassName,
                loadedNormal,
                loadedSpecular,
                normalTextureId,
                specularTextureId
            );
        }
    }

    void onClientTick(Minecraft minecraft) {
        String requestedWorld = getTrimmedProperty(AUTO_JOIN_WORLD_PROPERTY);
        if (requestedWorld != null) {
            tryAutoJoinWorld(minecraft, requestedWorld);
        }

        updateExitAfterWorldTicks(minecraft);
    }

    private void tryAutoJoinWorld(Minecraft minecraft, String requestedWorld) {
        if (joinAttempted || minecraft.world != null || minecraft.isIntegratedServerRunning()) {
            return;
        }

        GuiScreen currentScreen = minecraft.currentScreen;
        if (currentScreen == null || !(currentScreen instanceof GuiMainMenu || currentScreen instanceof GuiWorldSelection)) {
            return;
        }

        joinAttempted = true;

        ISaveFormat saveFormat = minecraft.getSaveLoader();
        if (saveFormat == null) {
            Oculus.LOGGER.warn("Oculus runtime validation could not auto-join '{}': save loader is unavailable", requestedWorld);
            return;
        }

        WorldSummary summary;
        try {
            summary = findWorld(saveFormat.getSaveList(), requestedWorld);
        } catch (AnvilConverterException exception) {
            Oculus.LOGGER.warn("Oculus runtime validation could not enumerate saves for auto-join '{}'", requestedWorld, exception);
            return;
        }

        if (summary == null) {
            Oculus.LOGGER.warn("Oculus runtime validation could not auto-join '{}': no exact save folder or unique display-name match", requestedWorld);
            return;
        }

        if (summary.askToOpenWorld()) {
            Oculus.LOGGER.warn("Oculus runtime validation refused to auto-join '{}': Minecraft would show a version warning", summary.getFileName());
            return;
        }

        if (!saveFormat.canLoadWorld(summary.getFileName())) {
            Oculus.LOGGER.warn("Oculus runtime validation could not auto-join '{}': save loader rejected the world", summary.getFileName());
            return;
        }

        Oculus.LOGGER.info(
            "Oculus runtime validation auto-joining world '{}' (display '{}')",
            summary.getFileName(),
            summary.getDisplayName()
        );

        try {
            FMLClientHandler.instance().tryLoadExistingWorld(asWorldSelectionScreen(currentScreen), summary);
        } catch (StartupQuery.AbortedException exception) {
            Oculus.LOGGER.warn("Oculus runtime validation auto-join for '{}' was aborted by Forge startup query", summary.getFileName(), exception);
        } catch (RuntimeException exception) {
            Oculus.LOGGER.error("Oculus runtime validation failed while auto-joining '{}'", summary.getFileName(), exception);
        }
    }

    private GuiWorldSelection asWorldSelectionScreen(GuiScreen currentScreen) {
        if (currentScreen instanceof GuiWorldSelection) {
            return (GuiWorldSelection) currentScreen;
        }

        GuiScreen previousScreen = currentScreen == null ? new GuiMainMenu() : currentScreen;
        return new GuiWorldSelection(previousScreen);
    }

    private WorldSummary findWorld(List<WorldSummary> summaries, String requestedWorld) {
        WorldSummary displayNameMatch = null;
        int displayNameMatches = 0;

        for (WorldSummary summary : summaries) {
            if (requestedWorld.equals(summary.getFileName())) {
                return summary;
            }

            if (requestedWorld.equals(summary.getDisplayName())) {
                displayNameMatch = summary;
                displayNameMatches++;
            }
        }

        return displayNameMatches == 1 ? displayNameMatch : null;
    }

    private void updateExitAfterWorldTicks(Minecraft minecraft) {
        int exitAfterWorldTicks = getExitAfterWorldTicks();
        int screenshotWorldTick = getScreenshotWorldTick();
        int inventoryScreenshotWorldTick = getInventoryScreenshotWorldTick();
        if (exitAfterWorldTicks < 0 && screenshotWorldTick < 0 && inventoryScreenshotWorldTick < 0) {
            return;
        }

        if (minecraft.world == null || minecraft.player == null) {
            worldTicks = 0;
            currentWorldTicks = 0;
            worldActive = false;
            screenshotCaptured = false;
            screenshotQueued = false;
            inventoryScreenshotCaptured = false;
            inventoryScreenshotQueued = false;
            thirdPersonViewApplied = false;
            pauseOnLostFocusDisabled = false;
            visualUniformStateLogged = false;
            lastScreenCloseTick = -1;
            inventoryScreenOpenTick = -1;
            return;
        }

        if (!enteredWorldLogged) {
            enteredWorldLogged = true;
            if (exitAfterWorldTicks >= 0) {
                Oculus.LOGGER.info("Oculus runtime validation entered a world; exit countdown is {} client ticks", exitAfterWorldTicks);
            } else {
                Oculus.LOGGER.info(
                    "Oculus runtime validation entered a world; world screenshot tick is {}, inventory screenshot tick is {}",
                    screenshotWorldTick,
                    inventoryScreenshotWorldTick
                );
            }
        }

        worldTicks++;
        currentWorldTicks = worldTicks;
        worldActive = true;
        applyWorldTimeIfRequested(minecraft);
        disablePauseOnLostFocusForValidation(minecraft);
        applyThirdPersonViewIfRequested(minecraft);
        boolean closedScreen = closeScreenForWorldCaptureIfNeeded(minecraft, screenshotWorldTick);
        if (!closedScreen) {
            queueScreenshotIfRequested(minecraft, screenshotWorldTick);
            openInventoryForScreenshotIfRequested(minecraft, inventoryScreenshotWorldTick, screenshotWorldTick);
            queueInventoryScreenshotIfRequested(minecraft, inventoryScreenshotWorldTick, screenshotWorldTick);
        }

        if (exitAfterWorldTicks >= 0 && !shutdownRequested && worldTicks >= exitAfterWorldTicks) {
            shutdownRequested = true;
            Oculus.LOGGER.info("Oculus runtime validation requesting client shutdown after {} in-world client ticks", worldTicks);
            minecraft.shutdown();
        }
    }

    private void applyWorldTimeIfRequested(Minecraft minecraft) {
        int worldTime = getWorldTimeOverride();
        if (worldTime < 0 || minecraft == null || minecraft.world == null) {
            return;
        }

        minecraft.world.setWorldTime(worldTime);

        if (!worldStateLogged) {
            worldStateLogged = true;
            Oculus.LOGGER.info(
                "Oculus runtime validation fixed client worldTime={} for screenshot/run capture",
                worldTime
            );
        }
    }

    private void applyThirdPersonViewIfRequested(Minecraft minecraft) {
        int thirdPersonView = getThirdPersonView();
        if (thirdPersonView < 0 || minecraft == null || minecraft.gameSettings == null) {
            return;
        }

        if (minecraft.gameSettings.thirdPersonView != thirdPersonView) {
            minecraft.gameSettings.thirdPersonView = thirdPersonView;
        }

        if (!thirdPersonViewApplied) {
            thirdPersonViewApplied = true;
            Oculus.LOGGER.info("Oculus runtime validation set thirdPersonView={}", thirdPersonView);
        }
    }

    private void disablePauseOnLostFocusForValidation(Minecraft minecraft) {
        if (minecraft == null || minecraft.gameSettings == null) {
            return;
        }

        if (minecraft.gameSettings.pauseOnLostFocus) {
            minecraft.gameSettings.pauseOnLostFocus = false;
        }

        if (!pauseOnLostFocusDisabled) {
            pauseOnLostFocusDisabled = true;
            Oculus.LOGGER.info("Oculus runtime validation disabled pauseOnLostFocus for unattended world capture");
        }
    }

    private boolean closeScreenForWorldCaptureIfNeeded(Minecraft minecraft, int screenshotWorldTick) {
        if (screenshotWorldTick < 0 || screenshotCaptured || minecraft == null || minecraft.currentScreen == null) {
            return false;
        }

        GuiScreen lingeringScreen = minecraft.currentScreen;
        minecraft.displayGuiScreen(null);
        minecraft.setIngameFocus();
        lastScreenCloseTick = worldTicks;
        Oculus.LOGGER.info(
            "Oculus runtime validation closed lingering screen {} before world capture at tick {}",
            lingeringScreen == null ? "unknown" : lingeringScreen.getClass().getSimpleName(),
            worldTicks
        );
        return true;
    }

    private void queueScreenshotIfRequested(Minecraft minecraft, int screenshotWorldTick) {
        if (screenshotWorldTick < 0 || screenshotCaptured || screenshotQueued || worldTicks < screenshotWorldTick) {
            return;
        }

        if (lastScreenCloseTick >= 0 && worldTicks <= lastScreenCloseTick + 2) {
            return;
        }

        if (minecraft == null || minecraft.currentScreen != null || minecraft.gameDir == null || minecraft.getFramebuffer() == null) {
            return;
        }

        screenshotQueued = true;
    }

    private void openInventoryForScreenshotIfRequested(Minecraft minecraft,
                                                       int inventoryScreenshotWorldTick,
                                                       int screenshotWorldTick) {
        if (inventoryScreenshotWorldTick < 0 || inventoryScreenshotCaptured || inventoryScreenshotQueued
            || worldTicks < inventoryScreenshotWorldTick) {
            return;
        }

        if (screenshotWorldTick >= 0 && !screenshotCaptured) {
            return;
        }

        if (minecraft == null || minecraft.player == null || minecraft.currentScreen != null) {
            return;
        }

        minecraft.displayGuiScreen(new GuiInventory(minecraft.player));
        inventoryScreenOpenTick = worldTicks;
        Oculus.LOGGER.info("Oculus runtime validation opened inventory screen for GUI capture at tick {}", worldTicks);
    }

    private void queueInventoryScreenshotIfRequested(Minecraft minecraft,
                                                     int inventoryScreenshotWorldTick,
                                                     int screenshotWorldTick) {
        if (inventoryScreenshotWorldTick < 0 || inventoryScreenshotCaptured || inventoryScreenshotQueued
            || worldTicks < inventoryScreenshotWorldTick) {
            return;
        }

        if (screenshotWorldTick >= 0 && !screenshotCaptured) {
            return;
        }

        if (inventoryScreenOpenTick >= 0 && worldTicks <= inventoryScreenOpenTick + 1) {
            return;
        }

        if (minecraft == null || !isInventoryScreen(minecraft.currentScreen)
            || minecraft.gameDir == null || minecraft.getFramebuffer() == null) {
            return;
        }

        inventoryScreenshotQueued = true;
    }

    public static void captureWorldScreenshotAfterRender(Minecraft minecraft) {
        OculusRuntimeValidation instance = activeInstance;
        if (instance == null || !instance.screenshotQueued || instance.screenshotCaptured) {
            return;
        }

        if (minecraft == null || minecraft.currentScreen != null || minecraft.gameDir == null
            || minecraft.getFramebuffer() == null) {
            return;
        }

        instance.captureQueuedScreenshot(minecraft);
    }

    public static void captureInventoryScreenshotAfterGuiRender(Minecraft minecraft) {
        OculusRuntimeValidation instance = activeInstance;
        if (instance == null || !instance.shouldCaptureInventoryScreenshotAfterGuiRender(minecraft)) {
            return;
        }

        instance.captureQueuedInventoryScreenshot(minecraft);
    }

    private boolean shouldCaptureInventoryScreenshotAfterGuiRender(Minecraft minecraft) {
        int inventoryScreenshotWorldTick = getInventoryScreenshotWorldTick();
        if (inventoryScreenshotWorldTick < 0 || inventoryScreenshotCaptured || worldTicks < inventoryScreenshotWorldTick) {
            return false;
        }

        if (inventoryScreenOpenTick >= 0 && worldTicks <= inventoryScreenOpenTick + 1) {
            logInventoryGuiCaptureWait("waiting-open-settle");
            return false;
        }

        if (minecraft == null) {
            logInventoryGuiCaptureWait("minecraft-null");
            return false;
        }

        if (!isInventoryScreen(minecraft.currentScreen)) {
            logInventoryGuiCaptureWait(
                "screen=" + (minecraft.currentScreen == null ? "null" : minecraft.currentScreen.getClass().getName())
            );
            return false;
        }

        if (minecraft.gameDir == null) {
            logInventoryGuiCaptureWait("game-dir-null");
            return false;
        }

        if (minecraft.getFramebuffer() == null) {
            logInventoryGuiCaptureWait("framebuffer-null");
            return false;
        }

        return true;
    }

    private static boolean isInventoryScreen(GuiScreen screen) {
        return screen instanceof GuiInventory || screen instanceof GuiContainerCreative;
    }

    private static void logInventoryGuiCaptureWait(String reason) {
        if (INVENTORY_GUI_CAPTURE_WAITS.add(reason)) {
            Oculus.LOGGER.info(
                "Oculus runtime validation waiting for inventory GUI screenshot: reason={} worldTick={}",
                reason,
                currentWorldTicks
            );
        }
    }

    private void captureQueuedScreenshot(Minecraft minecraft) {
        logVisualUniformState(minecraft);
        screenshotCaptured = true;
        screenshotQueued = false;
        String fileName = "oculus-validation-" + System.currentTimeMillis() + "-tick-" + worldTicks + ".png";
        File screenshotFile = new File(new File(minecraft.gameDir, "screenshots"), fileName);
        try {
            ITextComponent result = ScreenShotHelper.saveScreenshot(
                minecraft.gameDir,
                fileName,
                minecraft.displayWidth,
                minecraft.displayHeight,
                minecraft.getFramebuffer()
            );
            Oculus.LOGGER.info(
                "Oculus runtime validation captured post-world-render screenshot at tick {}: {} ({})",
                worldTicks,
                screenshotFile.getPath(),
                result == null ? "no result text" : result.getUnformattedText()
            );
        } catch (RuntimeException exception) {
            screenshotCaptured = false;
            Oculus.LOGGER.warn("Oculus runtime validation failed to capture screenshot at tick {}", worldTicks, exception);
        }
    }

    private void captureQueuedInventoryScreenshot(Minecraft minecraft) {
        logInventoryGuiState(minecraft);
        inventoryScreenshotCaptured = true;
        inventoryScreenshotQueued = false;
        String fileName = "oculus-validation-inventory-" + System.currentTimeMillis() + "-tick-" + worldTicks + ".png";
        File screenshotFile = new File(new File(minecraft.gameDir, "screenshots"), fileName);
        try {
            ITextComponent result = ScreenShotHelper.saveScreenshot(
                minecraft.gameDir,
                fileName,
                minecraft.displayWidth,
                minecraft.displayHeight,
                minecraft.getFramebuffer()
            );
            Oculus.LOGGER.info(
                "Oculus runtime validation captured inventory GUI screenshot at tick {}: {} ({})",
                worldTicks,
                screenshotFile.getPath(),
                result == null ? "no result text" : result.getUnformattedText()
            );
        } catch (RuntimeException exception) {
            inventoryScreenshotCaptured = false;
            Oculus.LOGGER.warn(
                "Oculus runtime validation failed to capture inventory GUI screenshot at tick {}",
                worldTicks,
                exception
            );
        }
    }

    private void logInventoryGuiState(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }

        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        ScaledResolution scaledResolution = new ScaledResolution(minecraft);
        Oculus.LOGGER.info(
            "Oculus runtime validation inventory GUI state at screenshot tick {}: display={}x{} scaled={}x{} scaleFactor={} viewport=({}, {}, {}, {}) framebuffer={} glProgram={} matrixMode={} screen={}",
            worldTicks,
            minecraft.displayWidth,
            minecraft.displayHeight,
            scaledResolution.getScaledWidth(),
            scaledResolution.getScaledHeight(),
            scaledResolution.getScaleFactor(),
            viewport.get(0),
            viewport.get(1),
            viewport.get(2),
            viewport.get(3),
            OculusRenderSystem.getFramebufferBinding(),
            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            GL11.glGetInteger(GL11.GL_MATRIX_MODE),
            minecraft.currentScreen == null ? "null" : minecraft.currentScreen.getClass().getSimpleName()
        );
    }

    private void logVisualUniformState(Minecraft minecraft) {
        if (visualUniformStateLogged || minecraft == null || minecraft.gameSettings == null) {
            return;
        }

        visualUniformStateLogged = true;
        float[] eyeBrightness = GameplayUniforms.getEyeBrightness();
        FloatBuffer fogColor = GameDataSuppliers.fogColor().get();
        float fogRed = fogColor != null && fogColor.limit() > 0 ? fogColor.get(0) : Float.NaN;
        float fogGreen = fogColor != null && fogColor.limit() > 1 ? fogColor.get(1) : Float.NaN;
        float fogBlue = fogColor != null && fogColor.limit() > 2 ? fogColor.get(2) : Float.NaN;
        Oculus.LOGGER.info(
            "Oculus runtime validation visual state at screenshot tick {}: thirdPersonView={} worldTime={} sunAngle={} shadowAngle={} rain={} thunder={} eyeInWater={} blindness={} nightVision={} eyeBrightness=({}, {}) fogMode={} fogStart={} fogEnd={} fogDensity={} fogColor=({}, {}, {})",
            worldTicks,
            minecraft.gameSettings.thirdPersonView,
            GameplayUniforms.getWorldTime(),
            CelestialUniforms.getSunAngle(),
            CelestialUniforms.getShadowAngle(),
            GameplayUniforms.getRainStrength(),
            GameplayUniforms.getThunderStrength(),
            GameplayUniforms.isEyeInWater(),
            GameplayUniforms.getBlindness(),
            GameplayUniforms.getNightVision(),
            eyeBrightness.length > 0 ? eyeBrightness[0] : Float.NaN,
            eyeBrightness.length > 1 ? eyeBrightness[1] : Float.NaN,
            GameDataSuppliers.fogMode().getAsInt(),
            GameDataSuppliers.fogStart().get(),
            GameDataSuppliers.fogEnd().get(),
            GameDataSuppliers.fogDensity().get(),
            fogRed,
            fogGreen,
            fogBlue
        );
        logCapturedRenderingState();
    }

    private void logCapturedRenderingState() {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        double[] camera = state.getCameraPosition();
        double[] unshiftedCamera = state.getUnshiftedCameraPosition();
        float[] modelView = state.getGbufferModelView();
        float[] modelViewInverse = state.getModelViewInverse();
        float[] projection = state.getGbufferProjection();
        float[] projectionInverse = state.getProjectionInverse();
        float timeAngle = CompatibilityUniforms.getTimeAngle();
        float[] shaderUp = normalizedColumn(modelView, 1);
        float[] shaderSun = computeComplementarySunVector(modelView, timeAngle, CelestialUniforms.getSunPathRotation());
        float lightDirectionMultiplier = (timeAngle < 0.5325F || timeAngle > 0.9675F) ? 1.0F : -1.0F;
        float[] shaderLight = new float[] {
            shaderSun[0] * lightDirectionMultiplier,
            shaderSun[1] * lightDirectionMultiplier,
            shaderSun[2] * lightDirectionMultiplier
        };
        float sDotU = dot(shaderSun, shaderUp);
        float sunVisibility = clamp((sDotU + 0.0625F) / 0.125F, 0.0F, 1.0F);
        float shadowTimeVar1 = Math.abs(sunVisibility - 0.5F) * 2.0F;
        float shadowTimeVar2 = shadowTimeVar1 * shadowTimeVar1;
        float shadowTime = shadowTimeVar2 * shadowTimeVar2;

        Oculus.LOGGER.info(
            "Oculus runtime validation captured state at screenshot tick {}: near={} far={} camera=({}, {}, {}) unshiftedCamera=({}, {}, {}) modelViewT=({}, {}, {}) modelViewInvT=({}, {}, {}) projection[0,5,10,11,14]=({}, {}, {}, {}, {}) projectionInv[0,5,10,11,14]=({}, {}, {}, {}, {}) timeAngle={} sunPathRotation={} shaderUp=({}, {}, {}) shaderSun=({}, {}, {}) shaderLight=({}, {}, {}) SdotU={} sunVisibility={} shadowTime={} rainFactor={} eyeBrightnessM={} eyeBrightnessM2={} isEyeInCave={} inDry={} isDry={}",
            worldTicks,
            state.getNearPlane(),
            state.getFarPlane(),
            component(camera, 0),
            component(camera, 1),
            component(camera, 2),
            component(unshiftedCamera, 0),
            component(unshiftedCamera, 1),
            component(unshiftedCamera, 2),
            component(modelView, 12),
            component(modelView, 13),
            component(modelView, 14),
            component(modelViewInverse, 12),
            component(modelViewInverse, 13),
            component(modelViewInverse, 14),
            component(projection, 0),
            component(projection, 5),
            component(projection, 10),
            component(projection, 11),
            component(projection, 14),
            component(projectionInverse, 0),
            component(projectionInverse, 5),
            component(projectionInverse, 10),
            component(projectionInverse, 11),
            component(projectionInverse, 14),
            timeAngle,
            CelestialUniforms.getSunPathRotation(),
            shaderUp[0],
            shaderUp[1],
            shaderUp[2],
            shaderSun[0],
            shaderSun[1],
            shaderSun[2],
            shaderLight[0],
            shaderLight[1],
            shaderLight[2],
            sDotU,
            sunVisibility,
            shadowTime,
            CompatibilityUniforms.getRainFactor(),
            CompatibilityUniforms.getEyeBrightnessMUniform(),
            CompatibilityUniforms.getEyeBrightnessM2(),
            CompatibilityUniforms.getIsEyeInCave(),
            CompatibilityUniforms.getInDry(),
            CompatibilityUniforms.getIsDry()
        );
    }

    private static double component(double[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : Double.NaN;
    }

    private static float component(float[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : Float.NaN;
    }

    private static float component(FloatBuffer values, int index) {
        return values != null && index >= 0 && index < values.limit() ? values.get(index) : Float.NaN;
    }

    private static float[] normalizedColumn(float[] matrix, int column) {
        if (matrix == null || column < 0 || column > 3) {
            return new float[] {Float.NaN, Float.NaN, Float.NaN};
        }

        int offset = column * 4;
        return normalize(matrix[offset], matrix[offset + 1], matrix[offset + 2]);
    }

    private static float[] computeComplementarySunVector(float[] modelView, float timeAngle, float sunPathRotation) {
        if (modelView == null || modelView.length < 16) {
            return new float[] {Float.NaN, Float.NaN, Float.NaN};
        }

        float angle = fract(timeAngle - 0.25F);
        angle = (angle + ((((float) Math.cos(angle * Math.PI) * -0.5F) + 0.5F - angle) / 3.0F))
            * ((float) Math.PI * 2.0F);
        float rotation = (float) Math.toRadians(sunPathRotation);
        float x = -((float) Math.sin(angle)) * 2000.0F;
        float y = ((float) Math.cos(angle)) * ((float) Math.cos(rotation)) * 2000.0F;
        float z = ((float) Math.cos(angle)) * -((float) Math.sin(rotation)) * 2000.0F;

        float transformedX = modelView[0] * x + modelView[4] * y + modelView[8] * z + modelView[12];
        float transformedY = modelView[1] * x + modelView[5] * y + modelView[9] * z + modelView[13];
        float transformedZ = modelView[2] * x + modelView[6] * y + modelView[10] * z + modelView[14];
        return normalize(transformedX, transformedY, transformedZ);
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static float[] normalize(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length <= 1.0e-7F || !Float.isFinite(length)) {
            return new float[] {Float.NaN, Float.NaN, Float.NaN};
        }
        return new float[] {x / length, y / length, z / length};
    }

    private static float dot(float[] left, float[] right) {
        if (left == null || right == null || left.length < 3 || right.length < 3) {
            return Float.NaN;
        }
        return left[0] * right[0] + left[1] * right[1] + left[2] * right[2];
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int getExitAfterWorldTicks() {
        String rawValue = getTrimmedProperty(EXIT_AFTER_WORLD_TICKS_PROPERTY);
        if (rawValue == null) {
            return -1;
        }

        try {
            int value = Integer.parseInt(rawValue);
            return value >= 0 ? value : -1;
        } catch (NumberFormatException exception) {
            if (!invalidExitValueLogged) {
                invalidExitValueLogged = true;
                Oculus.LOGGER.warn("Ignoring invalid Oculus runtime validation exit tick count '{}'", rawValue);
            }
            return -1;
        }
    }

    private int getScreenshotWorldTick() {
        String rawValue = getTrimmedProperty(SCREENSHOT_WORLD_TICK_PROPERTY);
        if (rawValue == null) {
            return -1;
        }

        try {
            int value = Integer.parseInt(rawValue);
            return value >= 0 ? value : -1;
        } catch (NumberFormatException exception) {
            if (!invalidScreenshotValueLogged) {
                invalidScreenshotValueLogged = true;
                Oculus.LOGGER.warn("Ignoring invalid Oculus runtime validation screenshot tick '{}'", rawValue);
            }
            return -1;
        }
    }

    private int getInventoryScreenshotWorldTick() {
        String rawValue = getTrimmedProperty(INVENTORY_SCREENSHOT_WORLD_TICK_PROPERTY);
        if (rawValue == null) {
            return -1;
        }

        try {
            int value = Integer.parseInt(rawValue);
            return value >= 0 ? value : -1;
        } catch (NumberFormatException exception) {
            if (!invalidInventoryScreenshotValueLogged) {
                invalidInventoryScreenshotValueLogged = true;
                Oculus.LOGGER.warn("Ignoring invalid Oculus runtime validation inventory screenshot tick '{}'", rawValue);
            }
            return -1;
        }
    }

    private int getThirdPersonView() {
        String rawValue = getTrimmedProperty(THIRD_PERSON_VIEW_PROPERTY);
        if (rawValue == null) {
            return -1;
        }

        try {
            int value = Integer.parseInt(rawValue);
            return value >= 0 && value <= 2 ? value : -1;
        } catch (NumberFormatException exception) {
            if (!invalidThirdPersonViewValueLogged) {
                invalidThirdPersonViewValueLogged = true;
                Oculus.LOGGER.warn("Ignoring invalid Oculus runtime validation third-person view '{}'", rawValue);
            }
            return -1;
        }
    }

    private int getWorldTimeOverride() {
        String rawValue = getTrimmedProperty(WORLD_TIME_PROPERTY);
        if (rawValue == null) {
            return -1;
        }

        try {
            int value = Integer.parseInt(rawValue);
            return value >= 0 ? value % 24000 : -1;
        } catch (NumberFormatException exception) {
            if (!invalidWorldTimeValueLogged) {
                invalidWorldTimeValueLogged = true;
                Oculus.LOGGER.warn("Ignoring invalid Oculus runtime validation world time '{}'", rawValue);
            }
            return -1;
        }
    }

    private static String getTrimmedProperty(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            return null;
        }

        value = value.trim();
        return value.isEmpty() ? null : value;
    }
}
