package dev.kasapdev.jsondiff;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single RFC 6902 JSON Patch operation: {@code add}, {@code remove}, or {@code replace}.
 *
 * <p>{@code path} is a real RFC 6901 JSON Pointer (e.g. {@code /user/tags/2}, with {@code ~}
 * and {@code /} inside keys escaped as {@code ~0}/{@code ~1}). {@code value} holds the value to
 * add or replace with; it is {@code null} (and meaningless) for {@code remove} operations, and
 * may also legitimately be {@code null} for {@code add}/{@code replace} when the JSON value
 * itself is {@code null}.
 */
public final class PatchOperation {
    private final PatchOp op;
    private final String path;
    private final Object value;

    public PatchOperation(PatchOp op, String path, Object value) {
        this.op = op;
        this.path = path;
        this.value = value;
    }

    public PatchOp op() {
        return op;
    }

    public String path() {
        return path;
    }

    public Object value() {
        return value;
    }

    /**
     * Renders this operation as the {@code {"op": ..., "path": ..., "value": ...}} object shape
     * used by the RFC 6902 wire format (a {@code remove} operation omits {@code "value"}, as
     * required by the spec).
     */
    public Map<String, Object> toJsonObject() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("op", op.rfcName());
        obj.put("path", path);
        if (op != PatchOp.REMOVE) {
            obj.put("value", value);
        }
        return obj;
    }

    @Override
    public String toString() {
        String p = path.isEmpty() ? "/" : path;
        switch (op) {
            case ADD:
                return "ADD     " + p + " = " + format(value);
            case REMOVE:
                return "REMOVE  " + p;
            case REPLACE:
                return "REPLACE " + p + " = " + format(value);
            default:
                throw new IllegalStateException("Unknown patch op: " + op);
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
        if (!(o instanceof PatchOperation)) return false;
        PatchOperation other = (PatchOperation) o;
        return op == other.op
                && Objects.equals(path, other.path)
                && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(op, path, value);
    }
}
