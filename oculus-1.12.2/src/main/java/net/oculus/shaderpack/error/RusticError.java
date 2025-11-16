package net.oculus.shaderpack.error;

/**
 * Simplified port of the Iris RusticError structure used for diagnostics.
 */
public final class RusticError {
    private final String severity;
    private final String message;
    private final String detailMessage;
    private final String file;
    private final int lineNumber;
    private final String badLine;

    public RusticError(String severity, String message, String detailMessage, String file, int lineNumber, String badLine) {
        this.severity = severity;
        this.message = message;
        this.detailMessage = detailMessage;
        this.file = file;
        this.lineNumber = lineNumber;
        this.badLine = badLine;
    }

    @Override
    public String toString() {
        return severity + ": " + message + "\n"
            + " --> " + file + ":" + lineNumber + "\n"
            + "  |\n"
            + "  | " + badLine + "\n"
            + "  | " + repeat('^', badLine.length()) + " " + detailMessage + "\n"
            + "  |";
    }

    private static String repeat(char value, int count) {
        if (count <= 0) {
            return "";
        }

        char[] data = new char[count];
        for (int i = 0; i < count; i++) {
            data[i] = value;
        }
        return new String(data);
    }
}
