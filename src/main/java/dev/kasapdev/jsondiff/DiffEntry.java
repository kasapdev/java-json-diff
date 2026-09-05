package dev.kasapdev.jsondiff;

import java.util.Objects;

/**
 * A single reported difference between two JSON documents.
 *
 * <p>{@code path} is a JSON-pointer-style path (RFC 6901-ish, e.g. {@code /user/tags/2})
 * identifying the location of the change. {@code oldValue} and {@code newValue} hold the
 * value on each side (either may be {@code null} when a value is JSON {@code null}, or
 * absent for ADDED/REMOVED entries).
 */
public final class DiffEntry {
    private final String path;
    private final DiffType type;
    private final Object oldValue;
    private final Object newValue;

    public DiffEntry(String path, DiffType type, Object oldValue, Object newValue) {
        this.path = path;
        this.type = type;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String path() {
        return path;
    }

    public DiffType type() {
        return type;
    }

    public Object oldValue() {
        return oldValue;
    }

    public Object newValue() {
        return newValue;
    }

    @Override
    public String toString() {
        String p = path.isEmpty() ? "/" : path;
        switch (type) {
            case ADDED:
                return "ADDED   " + p + " = " + format(newValue);
            case REMOVED:
                return "REMOVED " + p + " (was " + format(oldValue) + ")";
            case CHANGED:
                return "CHANGED " + p + ": " + format(oldValue) + " -> " + format(newValue);
            default:
                throw new IllegalStateException("Unknown diff type: " + type);
        }
    }

    private static String format(Object value) {
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiffEntry)) return false;
        DiffEntry other = (DiffEntry) o;
        return Objects.equals(path, other.path)
                && type == other.type
                && Objects.equals(oldValue, other.oldValue)
                && Objects.equals(newValue, other.newValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, type, oldValue, newValue);
    }
}
