package net.oculus.shaderpack.parsing;

/**
 * Minimal port of the Iris parsed string helper for shader option parsing.
 * The class offers token-style consumption helpers tailored to the shader
 * preprocessor regex quirks used by shaders.properties metadata.
 */
public final class ParsedString {
    private String text;

    public ParsedString(String text) {
        this.text = text;
    }

    public boolean takeLiteral(String token) {
        if (!text.startsWith(token)) {
            return false;
        }

        text = text.substring(token.length());
        return true;
    }

    public boolean takeSomeWhitespace() {
        if (text.isEmpty() || !Character.isWhitespace(text.charAt(0))) {
            return false;
        }

        text = text.trim();
        return true;
    }

    public boolean takeComments() {
        if (!text.startsWith("//")) {
            return false;
        }

        text = text.substring(2);
        while (text.startsWith("/")) {
            text = text.substring(1);
        }
        return true;
    }

    public boolean currentlyContains(String token) {
        return text.contains(token);
    }

    public boolean isEnd() {
        return text.isEmpty();
    }

    public String takeRest() {
        return text;
    }

    private String takeCharacters(int length) {
        String result = text.substring(0, length);
        text = text.substring(length);
        return result;
    }

    public String takeWord() {
        if (isEnd()) {
            return null;
        }

        int position = 0;
        for (char character : text.toCharArray()) {
            if (!Character.isDigit(character) && !Character.isAlphabetic(character) && character != '_') {
                break;
            }
            position++;
        }

        if (position == 0) {
            return null;
        }

        return takeCharacters(position);
    }

    public String takeNumber() {
        if (isEnd()) {
            return null;
        }

        int position = 0;
        while (position < text.length()) {
            if (position + 1 < text.length()) {
                if (!Character.isDigit(text.charAt(position)) && !Character.isDigit(text.charAt(position + 1))) {
                    break;
                }
            }
            position++;
        }

        try {
            Float.parseFloat(text.substring(0, position));
        } catch (Exception exception) {
            return null;
        }

        return takeCharacters(position);
    }

    public String takeWordOrNumber() {
        String number = takeNumber();
        if (number != null) {
            return number;
        }
        return takeWord();
    }
}
