package net.oculus.shaderpack.preprocessor;

import org.anarres.cpp.Feature;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Preprocessor;
import org.anarres.cpp.StringLexerSource;
import org.anarres.cpp.Token;

import net.oculus.shaderpack.StringPair;

public final class JcppProcessor {
    private JcppProcessor() {
    }

    public static String glslPreprocessSource(String source, Iterable<StringPair> environmentDefines) {
        if (source.contains(GlslCollectingListener.VERSION_MARKER)
            || source.contains(GlslCollectingListener.EXTENSION_MARKER)) {
            throw new RuntimeException("Shader source contains reserved Oculus preprocessing markers");
        }

        source = source.replace("#version", GlslCollectingListener.VERSION_MARKER);
        source = source.replace("#extension", GlslCollectingListener.EXTENSION_MARKER);

        GlslCollectingListener listener = new GlslCollectingListener();

        try (Preprocessor preprocessor = new Preprocessor()) {
            try {
                for (StringPair define : environmentDefines) {
                    preprocessor.addMacro(define.getKey(), define.getValue());
                }
            } catch (LexerException exception) {
                throw new RuntimeException("Unexpected LexerException processing macros", exception);
            }

            preprocessor.setListener(listener);
            preprocessor.addInput(new StringLexerSource(source, true));
            preprocessor.addFeature(Feature.KEEPCOMMENTS);

            StringBuilder builder = new StringBuilder();
            try {
                for (;;) {
                    Token token = preprocessor.token();
                    if (token == null || token.getType() == Token.EOF) {
                        break;
                    }
                    builder.append(token.getText());
                }
            } catch (Exception exception) {
                throw new RuntimeException("GLSL source pre-processing failed", exception);
            }

            return listener.collectLines() + builder.append('\n');
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("GLSL source pre-processing failed", exception);
        }
    }
}
