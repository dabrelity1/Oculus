package net.oculus.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

public class GlResourceTest {
    @Test
    public void destroyInvalidatesResourceEvenWhenDestroyInternalThrows() {
        RuntimeException failure = new RuntimeException("destroy failed");
        TestResource resource = new TestResource(failure);

        try {
            resource.destroy();
            fail("Expected destroy failure to propagate");
        } catch (RuntimeException thrown) {
            assertSame(failure, thrown);
        }

        assertEquals(1, resource.destroyAttempts);
        try {
            resource.readId();
            fail("Expected failed destroy attempt to invalidate the resource");
        } catch (IllegalStateException expected) {
            // Expected.
        }

        resource.destroy();
        assertEquals(1, resource.destroyAttempts);
    }

    @Test
    public void destroyInvalidatesResourceAfterSuccessfulDestroyInternal() {
        TestResource resource = new TestResource(null);

        resource.destroy();

        assertEquals(1, resource.destroyAttempts);
        try {
            resource.readId();
            fail("Expected destroyed resource to be invalid");
        } catch (IllegalStateException expected) {
            // Expected.
        }

        resource.destroy();
        assertEquals(1, resource.destroyAttempts);
    }

    private static final class TestResource extends GlResource {
        private final RuntimeException failure;
        private int destroyAttempts;

        private TestResource(RuntimeException failure) {
            super(17);
            this.failure = failure;
        }

        @Override
        protected void destroyInternal() {
            destroyAttempts++;
            if (failure != null) {
                throw failure;
            }
        }

        private int readId() {
            return getGlId();
        }
    }
}
