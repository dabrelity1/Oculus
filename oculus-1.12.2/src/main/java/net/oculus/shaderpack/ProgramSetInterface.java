package net.oculus.shaderpack;

public interface ProgramSetInterface {
    final class Empty implements ProgramSetInterface {
        public static final Empty INSTANCE = new Empty();

        private Empty() {
        }
    }
}
