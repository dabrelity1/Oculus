package net.oculus.bridge;

import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import net.oculus.Oculus;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.embeddedt.embeddium.api.ChunkDataBuiltEvent;

import java.lang.reflect.Field;
import java.text.DecimalFormat;

public final class OculusRelictiumBridge {
    private static final DecimalFormat BOUNDS_FORMAT = new DecimalFormat("0.###");
    private static final Field BOUNDS_FIELD;

    static {
        Field field;
        try {
            field = ReflectionHelper.findField(ChunkRenderData.Builder.class, "bounds");
            field.setAccessible(true);
        } catch (ReflectionHelper.UnableToFindFieldException ex) {
            field = null;
            Oculus.LOGGER.warn("Unable to locate ChunkRenderData.Builder#bounds via reflection", ex);
        }

        BOUNDS_FIELD = field;
    }

    @SubscribeEvent
    public void onChunkDataBuilt(ChunkDataBuiltEvent event) {
        if (!Oculus.LOGGER.isDebugEnabled()) {
            return;
        }

        ChunkRenderBounds bounds = extractBounds(event.getDataBuilder());

        if (bounds == null) {
            Oculus.LOGGER.debug("Chunk build intercepted but bounds were unavailable");
            return;
        }

        Oculus.LOGGER.debug("Chunk build intercepted: {}", describeBounds(bounds));
    }

    private static ChunkRenderBounds extractBounds(ChunkRenderData.Builder builder) {
        if (BOUNDS_FIELD == null) {
            return null;
        }

        try {
            Object value = BOUNDS_FIELD.get(builder);
            if (value instanceof ChunkRenderBounds) {
                return (ChunkRenderBounds) value;
            }
        } catch (IllegalAccessException | RuntimeException ex) {
            Oculus.LOGGER.debug("Failed to inspect chunk bounds", ex);
        }

        return null;
    }

    private static String describeBounds(ChunkRenderBounds bounds) {
        return "[x1=" + BOUNDS_FORMAT.format(bounds.x1) + ", y1=" + BOUNDS_FORMAT.format(bounds.y1) + ", z1=" + BOUNDS_FORMAT.format(bounds.z1)
                + ", x2=" + BOUNDS_FORMAT.format(bounds.x2) + ", y2=" + BOUNDS_FORMAT.format(bounds.y2) + ", z2=" + BOUNDS_FORMAT.format(bounds.z2) + "]";
    }
}
