package net.oculus.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.oculus.shaderpack.materialmap.NamespacedId;
import org.junit.Test;

public class BlockRenderingSettingsTest {
    @Test
    public void appliesAmbientOcclusionLevelLikeIris() {
        BlockRenderingSettings settings = BlockRenderingSettings.INSTANCE;
        settings.clearReloadRequired();

        settings.setAmbientOcclusionLevel(1.0F);
        assertEquals(0.2F, settings.applyAmbientOcclusionLevel(0.2F), 0.0001F);

        settings.setAmbientOcclusionLevel(0.5F);
        assertEquals(0.6F, settings.applyAmbientOcclusionLevel(0.2F), 0.0001F);

        settings.setAmbientOcclusionLevel(0.0F);
        assertEquals(1.0F, settings.applyAmbientOcclusionLevel(0.2F), 0.0001F);

        settings.setAmbientOcclusionLevel(1.0F);
        settings.clearReloadRequired();
    }

    @Test
    public void marksReloadRequiredWhenBlockRenderingSettingsChange() {
        BlockRenderingSettings settings = BlockRenderingSettings.INSTANCE;
        settings.setAmbientOcclusionLevel(1.0F);
        settings.setDisableDirectionalShading(false);
        settings.setUseSeparateAo(false);
        settings.setUseExtendedVertexFormat(false);
        settings.clearReloadRequired();

        settings.setAmbientOcclusionLevel(0.75F);
        assertTrue(settings.isReloadRequired());

        settings.clearReloadRequired();
        settings.setUseSeparateAo(true);
        assertTrue(settings.isReloadRequired());

        settings.setAmbientOcclusionLevel(1.0F);
        settings.setUseSeparateAo(false);
        settings.clearReloadRequired();
        assertFalse(settings.isReloadRequired());
    }

    @Test
    public void storesAndClearsShaderPackRenderLayerOverrides() {
        BlockRenderingSettings settings = BlockRenderingSettings.INSTANCE;
        settings.setRenderLayerOverrides(null);
        settings.clearReloadRequired();

        Map<Block, BlockRenderLayer> overrides = new HashMap<>();
        overrides.put(null, BlockRenderLayer.TRANSLUCENT);

        settings.setRenderLayerOverrides(overrides);
        assertTrue(settings.isReloadRequired());
        assertEquals(BlockRenderLayer.TRANSLUCENT, settings.getRenderLayerOverride(null));

        settings.clearReloadRequired();
        settings.setRenderLayerOverrides(overrides);
        assertFalse(settings.isReloadRequired());

        settings.setRenderLayerOverrides(null);
        assertTrue(settings.isReloadRequired());
        assertEquals(null, settings.getRenderLayerOverride(null));

        settings.clearReloadRequired();
    }

    @Test
    public void installsShaderPackBlockStateIdsWithoutTreatingEmptyMapsAsVanilla() {
        BlockRenderingSettings settings = BlockRenderingSettings.INSTANCE;
        IBlockState state = fakeState();

        settings.setBlockStateIds(null);
        settings.clearReloadRequired();

        Object2IntOpenHashMap<IBlockState> shaderIds = new Object2IntOpenHashMap<>();
        shaderIds.defaultReturnValue(-1);
        shaderIds.put(state, 450);

        settings.setBlockStateIds(shaderIds);
        assertTrue(settings.isReloadRequired());
        assertSame(shaderIds, settings.getBlockStateIds());
        assertEquals(450, BlockContextHolder.getActiveStateId(state));

        BlockContextHolder holder = BlockContextHolder.createActiveHolder();
        holder.set(state, (short) 3);
        assertEquals(450, holder.blockId);
        assertEquals(3, holder.renderType);

        settings.clearReloadRequired();
        settings.setBlockStateIds(shaderIds);
        assertFalse(settings.isReloadRequired());

        Object2IntOpenHashMap<IBlockState> emptyShaderIds = new Object2IntOpenHashMap<>();
        emptyShaderIds.defaultReturnValue(-1);
        settings.setBlockStateIds(emptyShaderIds);
        assertTrue(settings.isReloadRequired());
        assertSame(emptyShaderIds, settings.getBlockStateIds());
        assertEquals(-1, BlockContextHolder.getActiveStateId(state));

        settings.setBlockStateIds(null);
        assertEquals(null, settings.getBlockStateIds());
        settings.clearReloadRequired();
    }

    @Test
    public void storesEntityIdsWithoutMarkingChunkReloadRequired() {
        BlockRenderingSettings settings = BlockRenderingSettings.INSTANCE;
        settings.setEntityIds(null);
        settings.clearReloadRequired();

        Object2IntOpenHashMap<NamespacedId> entityIds = new Object2IntOpenHashMap<>();
        entityIds.defaultReturnValue(-1);
        entityIds.put(new NamespacedId("minecraft", "player"), 50016);

        settings.setEntityIds(entityIds);
        assertSame(entityIds, settings.getEntityIds());
        assertFalse(settings.isReloadRequired());
        assertEquals(50016, settings.getEntityIds().getInt(new NamespacedId("minecraft", "player")));

        settings.setEntityIds(null);
        assertEquals(null, settings.getEntityIds());
        assertFalse(settings.isReloadRequired());
    }

    private static IBlockState fakeState() {
        return (IBlockState) Proxy.newProxyInstance(
            IBlockState.class.getClassLoader(),
            new Class<?>[] {IBlockState.class},
            BlockRenderingSettingsTest::handleFakeStateInvocation);
    }

    private static Object handleFakeStateInvocation(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if ("equals".equals(name)) {
            return proxy == args[0];
        }
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("toString".equals(name)) {
            return "fakeBlockState";
        }
        if (method.getReturnType() == boolean.class) {
            return false;
        }
        if (method.getReturnType() == int.class) {
            return 0;
        }
        return null;
    }
}
