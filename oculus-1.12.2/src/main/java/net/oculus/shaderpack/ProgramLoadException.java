package net.oculus.shaderpack;

/**
 * Exception thrown when a shader program cannot be loaded. The actual loading logic is stubbed in
 * this backport, but the exception exists so future implementations can mirror the upstream API.
 */
public class ProgramLoadException extends RuntimeException {
    public ProgramLoadException(String message) {
        super(message);
    }

    public ProgramLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
