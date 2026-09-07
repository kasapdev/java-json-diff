package dev.kasapdev.jsondiff;

/**
 * Thrown when a JSON Patch (RFC 6902) document cannot be applied — e.g. a {@code replace} or
 * {@code remove} operation targets a path that does not exist, an array index is out of range,
 * or a path segment tries to navigate into a scalar value.
 */
public class JsonPatchException extends RuntimeException {
    public JsonPatchException(String message) {
        super(message);
    }
}
