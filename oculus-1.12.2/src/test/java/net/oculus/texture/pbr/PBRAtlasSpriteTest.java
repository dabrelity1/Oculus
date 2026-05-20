package net.oculus.texture.pbr;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.resources.data.AnimationFrame;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.junit.Test;

public class PBRAtlasSpriteTest {
    @Test
    public void animatedFrameSelectionWalksBackwardToNearestValidFrame() {
        int[][] firstFrame = new int[][] {new int[] {1}};
        int[][] previousValidFrame = new int[][] {new int[] {2}};
        List<int[][]> frames = Arrays.asList(firstFrame, null, previousValidFrame);
        AnimationMetadataSection metadata = new AnimationMetadataSection(Arrays.asList(
            new AnimationFrame(0),
            new AnimationFrame(2),
            new AnimationFrame(7)
        ), -1, -1, 1, false);

        int[][] selected = PBRAtlasSprite.selectCurrentFrameTextureData(frames, metadata, 2);

        assertSame(previousValidFrame, selected);
    }

    @Test
    public void frameSelectionFallsBackToFirstAvailableFrameWhenAnimationHasNoValidCandidate() {
        int[][] firstAvailableFrame = new int[][] {new int[] {3}};
        List<int[][]> frames = Arrays.asList(null, firstAvailableFrame);
        AnimationMetadataSection metadata = new AnimationMetadataSection(Collections.singletonList(
            new AnimationFrame(9)
        ), -1, -1, 1, false);

        int[][] selected = PBRAtlasSprite.selectCurrentFrameTextureData(frames, metadata, 0);

        assertSame(firstAvailableFrame, selected);
    }

    @Test
    public void frameSelectionReturnsNullWhenNoFrameDataExists() {
        List<int[][]> frames = Arrays.asList(null, null);

        int[][] selected = PBRAtlasSprite.selectCurrentFrameTextureData(frames, null, 0);

        assertNull(selected);
    }
}
