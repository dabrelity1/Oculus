package net.oculus.shaderpack.preprocessor;

import org.anarres.cpp.DefaultPreprocessorListener;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Source;

/**
 * Collects pseudo-"warning" lines emitted by the preprocessing step to rebuild properties content.
 */
public final class PropertyCollectingListener extends DefaultPreprocessorListener {
    public static final String PROPERTY_MARKER = "#warning IRIS_PASSTHROUGH ";

    private final StringBuilder builder = new StringBuilder();

    @Override
    public void handleWarning(Source source, int line, int column, String message) throws LexerException {
        if (message.startsWith(PROPERTY_MARKER)) {
            builder.append(message.substring(PROPERTY_MARKER.length()));
            builder.append('\n');
            return;
        }
        super.handleWarning(source, line, column, message);
    }

    @Override
    public void handleError(Source source, int line, int column, String message) throws LexerException {
        if (message.contains("Unknown preprocessor directive") || message.contains("Preprocessor directive not a word")) {
            return;
        }
        super.handleError(source, line, column, message);
    }

    public String collectLines() {
        return builder.toString();
    }
}
