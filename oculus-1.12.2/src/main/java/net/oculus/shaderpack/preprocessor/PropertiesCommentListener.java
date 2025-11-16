package net.oculus.shaderpack.preprocessor;

import org.anarres.cpp.DefaultPreprocessorListener;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Source;

/**
 * Suppresses errors raised when the properties preprocessor encounters comment-style hashes.
 */
public final class PropertiesCommentListener extends DefaultPreprocessorListener {
    @Override
    public void handleError(Source source, int line, int column, String message) throws LexerException {
        if (message.contains("Unknown preprocessor directive") || message.contains("Preprocessor directive not a word")) {
            return;
        }
        super.handleError(source, line, column, message);
    }
}
