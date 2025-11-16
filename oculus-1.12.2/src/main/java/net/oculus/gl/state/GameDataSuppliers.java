package net.oculus.gl.state;

import java.nio.FloatBuffer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Provides access to frequently used game data in supplier form so shader uniform builders
 * can reference live values without repeatedly allocating buffers.
 */
public final class GameDataSuppliers {
    private static final FloatBuffer CAMERA_POSITION = BufferUtils.createFloatBuffer(3);
    // LWJGL expects a minimum of 16 floats for glGetFloat queries regardless of the
    // actual component count (GL_FOG_COLOR only uses 4), so allocate a larger buffer
    // and clamp the exposed range after the driver populates it.
    private static final FloatBuffer FOG_COLOR = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer SINGLE_FLOAT = BufferUtils.createFloatBuffer(1);

    private static volatile float partialTicksOverride = Float.NaN;

    private static final Supplier<Float> SYSTEM_TIME = () -> (System.currentTimeMillis() % 24000L) / 1000.0f;
    private static final Supplier<FloatBuffer> CAMERA_POSITION_SUPPLIER = GameDataSuppliers::updateCameraPosition;
    private static final Supplier<FloatBuffer> FOG_COLOR_SUPPLIER = GameDataSuppliers::updateFogColor;
    private static final Supplier<Float> FOG_START_SUPPLIER = () -> readFogValue(GL11.GL_FOG_START);
    private static final Supplier<Float> FOG_END_SUPPLIER = () -> readFogValue(GL11.GL_FOG_END);
    private static final Supplier<Float> FOG_DENSITY_SUPPLIER = () -> readFogValue(GL11.GL_FOG_DENSITY);
    private static final IntSupplier FOG_MODE_SUPPLIER = GameDataSuppliers::readFogMode;
    private static final Supplier<Float> VIEW_WIDTH_SUPPLIER = () -> {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null ? (float) mc.displayWidth : 0.0F;
    };
    private static final Supplier<Float> VIEW_HEIGHT_SUPPLIER = () -> {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null ? (float) mc.displayHeight : 0.0F;
    };
    private static final Supplier<Float> ASPECT_RATIO_SUPPLIER = () -> {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.displayHeight == 0) {
            return 1.0F;
        }
        return (float) mc.displayWidth / (float) mc.displayHeight;
    };

    private GameDataSuppliers() {
    }

    public static void setPartialTicks(float partialTicks) {
        partialTicksOverride = partialTicks;
    }

    public static Supplier<Float> systemTime() {
        return SYSTEM_TIME;
    }

    public static Supplier<FloatBuffer> cameraPosition() {
        return CAMERA_POSITION_SUPPLIER;
    }

    public static Supplier<FloatBuffer> fogColor() {
        return FOG_COLOR_SUPPLIER;
    }

    public static Supplier<Float> fogStart() {
        return FOG_START_SUPPLIER;
    }

    public static Supplier<Float> fogEnd() {
        return FOG_END_SUPPLIER;
    }

    public static Supplier<Float> fogDensity() {
        return FOG_DENSITY_SUPPLIER;
    }

    public static IntSupplier fogMode() {
        return FOG_MODE_SUPPLIER;
    }

    public static Supplier<Float> viewWidth() {
        return VIEW_WIDTH_SUPPLIER;
    }

    public static Supplier<Float> viewHeight() {
        return VIEW_HEIGHT_SUPPLIER;
    }

    public static Supplier<Float> aspectRatio() {
        return ASPECT_RATIO_SUPPLIER;
    }

    private static FloatBuffer updateCameraPosition() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity entity = mc != null ? mc.getRenderViewEntity() : null;
        float partialTicks = resolvePartialTicks(mc);

        CAMERA_POSITION.clear();

        if (entity == null) {
            CAMERA_POSITION.put(0.0F).put(0.0F).put(0.0F);
        } else {
            double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
            double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
            double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
            CAMERA_POSITION.put((float) x).put((float) y).put((float) z);
        }

        CAMERA_POSITION.flip();
        return CAMERA_POSITION;
    }

    private static FloatBuffer updateFogColor() {
        FOG_COLOR.clear();
        GL11.glGetFloat(GL11.GL_FOG_COLOR, FOG_COLOR);
        FOG_COLOR.limit(4);
        FOG_COLOR.position(0);
        return FOG_COLOR;
    }

    private static float readFogValue(int pname) {
        SINGLE_FLOAT.clear();
        GL11.glGetFloat(pname, SINGLE_FLOAT);
        SINGLE_FLOAT.position(0);
        return SINGLE_FLOAT.get(0);
    }

    private static int readFogMode() {
        return GL11.glGetInteger(GL11.GL_FOG_MODE);
    }

    private static float resolvePartialTicks(Minecraft mc) {
        float override = partialTicksOverride;
        return Float.isNaN(override) ? 0.0F : override;
    }
}
