package net.oculus.shaderpack;

/**
 * Marker interface that mirrors the 1.16.5 programme set hierarchy. The 1.12.2 port
 * keeps the hierarchy intentionally light-weight while the surrounding systems are
 * scaffolded.
 */
public interface ProgramSetInterface {
    /**
     * Convenience placeholder used when no shader programs are available yet.
     */
    final class Empty implements ProgramSetInterface {
        public static final Empty INSTANCE = new Empty();

        private Empty() {
        }
    }
}
