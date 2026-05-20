package net.oculus.postprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

public class BufferFlipperTest {
    @Test
    public void getFlippedBuffersMatchesReferenceIteratorSurface() {
        BufferFlipper flipper = new BufferFlipper();
        flipper.flip(2);
        flipper.flip(5);
        flipper.flip(2);
        flipper.flip(7);

        Set<Integer> flipped = new HashSet<>();
        Iterator<Integer> iterator = flipper.getFlippedBuffers();
        while (iterator.hasNext()) {
            flipped.add(iterator.next());
        }

        assertEquals(ImmutableSet.of(5, 7), flipped);
        assertFalse(flipper.isFlipped(2));
        assertTrue(flipper.isFlipped(5));
        assertTrue(flipper.isFlipped(7));
    }
}
