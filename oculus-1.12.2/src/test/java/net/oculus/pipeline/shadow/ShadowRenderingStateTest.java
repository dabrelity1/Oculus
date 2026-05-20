package net.oculus.pipeline.shadow;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import sun.misc.Unsafe;

import net.minecraft.entity.player.EntityPlayer;

public class ShadowRenderingStateTest {
    @Before
    public void resetBefore() {
        ShadowRenderingState.endShadowPass();
    }

    @After
    public void resetAfter() {
        ShadowRenderingState.endShadowPass();
    }

    @Test
    public void shadowPassActiveMatchesIrisGlobalShadowState() {
        float[] projection = matrix();

        ShadowRenderingState.beginShadowPass(projection);

        assertTrue(ShadowRenderingState.isActive());
        assertTrue(ShadowRenderingState.areShadowsCurrentlyBeingRendered());
    }

    @Test
    public void shadowProjectionIsCopiedForExternalConsumers() {
        float[] projection = matrix();

        ShadowRenderingState.beginShadowPass(projection);

        float[] firstCopy = ShadowRenderingState.getShadowOrthoMatrix();
        assertNotNull(firstCopy);
        assertArrayEquals(projection, firstCopy, 0.0F);

        projection[0] = -1.0F;
        firstCopy[1] = -2.0F;

        float[] secondCopy = ShadowRenderingState.getShadowOrthoMatrix();
        assertNotNull(secondCopy);
        assertArrayEquals(matrix(), secondCopy, 0.0F);
    }

    @Test
    public void shadowPassDoesNotFilterBlockEntitiesByItself() {
        ShadowRenderingState.beginShadowPass(matrix());

        assertTrue(ShadowRenderingState.areShadowsCurrentlyBeingRendered());
        assertTrue(ShadowRenderingState.shouldRenderBlockEntities());
    }

    @Test
    public void entityFilteringCanEndWithoutClearingShadowPassState() {
        ShadowRenderingState.beginShadowPass(matrix());
        ShadowRenderingState.beginEntityFiltering(false, false, false, null);

        assertTrue(ShadowRenderingState.areShadowsCurrentlyBeingRendered());
        assertFalse(ShadowRenderingState.shouldRenderBlockEntities());

        ShadowRenderingState.endEntityFiltering();

        assertTrue(ShadowRenderingState.areShadowsCurrentlyBeingRendered());
        assertTrue(ShadowRenderingState.shouldRenderBlockEntities());
        assertNotNull(ShadowRenderingState.getShadowOrthoMatrix());
    }

    @Test
    public void endingShadowPassClearsEntityFiltering() {
        ShadowRenderingState.beginShadowPass(matrix());
        ShadowRenderingState.beginEntityFiltering(false, false, false, null);

        ShadowRenderingState.endShadowPass();

        assertFalse(ShadowRenderingState.areShadowsCurrentlyBeingRendered());
        assertTrue(ShadowRenderingState.shouldRenderBlockEntities());
        assertNull(ShadowRenderingState.getShadowOrthoMatrix());
    }

    @Test
    public void legacyBeginEndKeepsImplicitPassScope() {
        ShadowRenderingState.begin(false, false, false, null);

        assertTrue(ShadowRenderingState.areShadowsCurrentlyBeingRendered());
        assertFalse(ShadowRenderingState.shouldRenderBlockEntities());

        ShadowRenderingState.end();

        assertFalse(ShadowRenderingState.areShadowsCurrentlyBeingRendered());
        assertTrue(ShadowRenderingState.shouldRenderBlockEntities());
    }

    @Test
    public void shadowEntityFilteringSkipsSpectatorPlayersWhenEntitiesAreEnabled() throws Exception {
        TestPlayer spectator = player(true);

        ShadowRenderingState.beginEntityFiltering(true, false, false, null);

        assertFalse(ShadowRenderingState.shouldRenderEntity(spectator));
    }

    @Test
    public void shadowPlayerFilteringSkipsSpectatorPlayer() throws Exception {
        TestPlayer spectator = player(true);

        ShadowRenderingState.beginEntityFiltering(false, true, false, spectator);

        assertFalse(ShadowRenderingState.shouldRenderEntity(spectator));
    }

    @Test
    public void shadowPlayerFilteringStillAllowsNonSpectatorPlayer() throws Exception {
        TestPlayer player = player(false);

        ShadowRenderingState.beginEntityFiltering(false, true, false, player);

        assertTrue(ShadowRenderingState.shouldRenderEntity(player));
    }

    @Test
    public void inactiveFilteringDoesNotApplySpectatorShadowRules() throws Exception {
        TestPlayer spectator = player(true);

        assertTrue(ShadowRenderingState.shouldRenderEntity(spectator));
    }

    private static float[] matrix() {
        float[] matrix = new float[16];
        for (int i = 0; i < matrix.length; i++) {
            matrix[i] = i + 1.0F;
        }
        return matrix;
    }

    private static TestPlayer player(boolean spectator) throws Exception {
        TestPlayer player = allocate(TestPlayer.class);
        player.spectator = spectator;
        return player;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class TestPlayer extends EntityPlayer {
        private boolean spectator;

        private TestPlayer() {
            super(null, new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000001"), "shadow-test"));
        }

        @Override
        public boolean isSpectator() {
            return spectator;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }
}
