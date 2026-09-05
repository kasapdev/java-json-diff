package dev.kasapdev.jsondiff;

/**
 * Thrown when malformed JSON is encountered during parsing.
 */
public class JsonParseException extends RuntimeException {
    public JsonParseException(String message) {
        super(message);
    }
}
