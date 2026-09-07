package dev.kasapdev.jsondiff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generates and applies RFC 6902 JSON Patch documents against parsed JSON values (as produced
 * by {@link JsonParser#parse(String)}).
 *
 * <p>{@link #generate(Object, Object)} walks the same before/after value trees that
 * {@link JsonDiff#diff(Object, Object)} walks, but instead of the library's own
 * {@link DiffEntry} shape, it emits standard RFC 6902 operations ({@code add}/{@code remove}/
 * {@code replace}) addressed by real RFC 6901 JSON Pointer paths. {@link #apply(Object, List)}
 * is the inverse: given a "before" value and a patch, it produces the "after" value, so that
 * {@code apply(a, generate(a, b))} equals {@code b}.
 */
public final class JsonPatch {

    private JsonPatch() {
    }

    /**
     * Generates an RFC 6902 patch that transforms {@code a} into {@code b}.
     *
     * @param a the "before" value
     * @param b the "after" value
     * @return a list of patch operations, empty if the values are structurally equal
     */
    public static List<PatchOperation> generate(Object a, Object b) {
        List<PatchOperation> ops = new ArrayList<>();
        generateValue("", a, b, ops);
        return ops;
    }

    @SuppressWarnings("unchecked")
    private static void generateValue(String path, Object a, Object b, List<PatchOperation> out) {
        if (Objects.equals(a, b)) {
            return;
        }
        if (a == null || b == null) {
            out.add(new PatchOperation(PatchOp.REPLACE, path, b));
            return;
        }
        // Same "kind" dispatch as JsonDiff.diffValues: recurse based on instanceof Map/List,
        // not exact runtime class, so this also works against hand-built Map.of()/List.of()
        // "expected" values, not just JsonParser's own LinkedHashMap/ArrayList.
        if (a instanceof Map && b instanceof Map) {
            generateMap(path, (Map<String, Object>) a, (Map<String, Object>) b, out);
        } else if (a instanceof List && b instanceof List) {
            generateList(path, (List<Object>) a, (List<Object>) b, out);
        } else {
            out.add(new PatchOperation(PatchOp.REPLACE, path, b));
        }
    }

    private static void generateMap(String path, Map<String, Object> a, Map<String, Object> b, List<PatchOperation> out) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        for (String key : keys) {
            String childPath = path + "/" + JsonDiff.escapePointerSegment(key);
            boolean inA = a.containsKey(key);
            boolean inB = b.containsKey(key);
            if (inA && inB) {
                generateValue(childPath, a.get(key), b.get(key), out);
            } else if (inA) {
                out.add(new PatchOperation(PatchOp.REMOVE, childPath, null));
            } else {
                out.add(new PatchOperation(PatchOp.ADD, childPath, b.get(key)));
            }
        }
    }

    private static void generateList(String path, List<Object> a, List<Object> b, List<PatchOperation> out) {
        int common = Math.min(a.size(), b.size());
        for (int i = 0; i < common; i++) {
            generateValue(path + "/" + i, a.get(i), b.get(i), out);
        }
        if (b.size() > a.size()) {
            // Growth: append new trailing elements in ascending index order. Each "add" targets
            // an index equal to the array's current length, i.e. an append, so applying them in
            // this order never needs to shift an already-placed element.
            for (int i = a.size(); i < b.size(); i++) {
                out.add(new PatchOperation(PatchOp.ADD, path + "/" + i, b.get(i)));
            }
        } else if (a.size() > b.size()) {
            // Shrink: remove trailing elements in *descending* index order. RFC 6902 "remove"
            // on an array shifts every later element down by one, so removing lowest-index-first
            // would invalidate the indices the later "remove" ops in this same batch still refer
            // to. Removing highest-index-first sidesteps that entirely.
            for (int i = a.size() - 1; i >= b.size(); i--) {
                out.add(new PatchOperation(PatchOp.REMOVE, path + "/" + i, null));
            }
        }
    }

    /**
     * Applies a patch to {@code before}, returning the resulting value. {@code before} is not
     * mutated; the result is built from a deep copy.
     *
     * @param before the value to patch
     * @param patch  the operations to apply, in order
     * @return the patched value
     * @throws JsonPatchException if an operation's path does not resolve against the value as
     *                            it stands at the time that operation is applied
     */
    public static Object apply(Object before, List<PatchOperation> patch) {
        Object result = deepCopy(before);
        for (PatchOperation op : patch) {
            result = applyOperation(result, op);
        }
        return result;
    }

    private static Object applyOperation(Object root, PatchOperation op) {
        List<String> segments = parsePointer(op.path());
        if (segments.isEmpty()) {
            switch (op.op()) {
                case ADD:
                case REPLACE:
                    return deepCopy(op.value());
                case REMOVE:
                    return null;
                default:
                    throw new JsonPatchException("Unknown patch op: " + op.op());
            }
        }
        Object parent = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            parent = descend(parent, segments.get(i), op.path());
        }
        applyAtParent(parent, segments.get(segments.size() - 1), op);
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Object descend(Object container, String segment, String fullPath) {
        if (container instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) container;
            if (!m.containsKey(segment)) {
                throw new JsonPatchException("Path does not exist: " + fullPath + " (missing member '" + segment + "')");
            }
            return m.get(segment);
        } else if (container instanceof List) {
            List<Object> l = (List<Object>) container;
            int idx = parseIndex(segment, l.size(), fullPath, false);
            return l.get(idx);
        } else {
            throw new JsonPatchException("Cannot navigate into a scalar value at path: " + fullPath);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyAtParent(Object parent, String key, PatchOperation op) {
        if (parent instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) parent;
            switch (op.op()) {
                case ADD:
                    // RFC 6902: "add" to an object either creates a new member or replaces an
                    // existing one's value.
                    m.put(key, deepCopy(op.value()));
                    return;
                case REPLACE:
                    if (!m.containsKey(key)) {
                        throw new JsonPatchException("Cannot replace nonexistent member '" + key + "' at path: " + op.path());
                    }
                    m.put(key, deepCopy(op.value()));
                    return;
                case REMOVE:
                    if (!m.containsKey(key)) {
                        throw new JsonPatchException("Cannot remove nonexistent member '" + key + "' at path: " + op.path());
                    }
                    m.remove(key);
                    return;
                default:
                    throw new JsonPatchException("Unknown patch op: " + op.op());
            }
        } else if (parent instanceof List) {
            List<Object> l = (List<Object>) parent;
            switch (op.op()) {
                case ADD: {
                    int idx = parseIndex(key, l.size(), op.path(), true);
                    l.add(idx, deepCopy(op.value()));
                    return;
                }
                case REPLACE: {
                    int idx = parseIndex(key, l.size(), op.path(), false);
                    l.set(idx, deepCopy(op.value()));
                    return;
                }
                case REMOVE: {
                    int idx = parseIndex(key, l.size(), op.path(), false);
                    l.remove(idx);
                    return;
                }
                default:
                    throw new JsonPatchException("Unknown patch op: " + op.op());
            }
        } else {
            throw new JsonPatchException("Cannot apply patch operation: parent at path is not an object or array: " + op.path());
        }
    }

    /**
     * Resolves one array-index path segment. {@code "-"} (the RFC 6901/6902 "end of array"
     * marker) is only accepted when {@code allowAppend} is true (i.e. for {@code add}), and
     * resolves to {@code size} (one past the last valid element), matching RFC 6902's append
     * semantics.
     */
    private static int parseIndex(String segment, int size, String fullPath, boolean allowAppend) {
        if (allowAppend && "-".equals(segment)) {
            return size;
        }
        int idx;
        try {
            idx = Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            throw new JsonPatchException("Invalid array index '" + segment + "' at path: " + fullPath);
        }
        int max = allowAppend ? size : size - 1;
        if (idx < 0 || idx > max) {
            throw new JsonPatchException("Array index out of range at path: " + fullPath);
        }
        return idx;
    }

    /**
     * Splits an RFC 6901 JSON Pointer into its unescaped segments ({@code ~1} -&gt; {@code /},
     * {@code ~0} -&gt; {@code ~}). The empty string (the whole-document pointer) yields an empty
     * list.
     */
    private static List<String> parsePointer(String path) {
        if (path.isEmpty()) {
            return List.of();
        }
        if (!path.startsWith("/")) {
            throw new JsonPatchException("Invalid JSON pointer (must be empty or start with '/'): " + path);
        }
        String[] raw = path.substring(1).split("/", -1);
        List<String> segments = new ArrayList<>(raw.length);
        for (String r : raw) {
            segments.add(r.replace("~1", "/").replace("~0", "~"));
        }
        return segments;
    }

    /**
     * Recursively copies a parsed-JSON value into fresh, mutable {@link LinkedHashMap}/
     * {@link ArrayList} containers (so {@link #apply(Object, List)} never mutates its input, and
     * so it also works against immutable inputs like {@code Map.of(...)}/{@code List.of(...)}).
     * Scalars (String/Double/Boolean) and {@code null} are immutable and returned as-is.
     */
    @SuppressWarnings("unchecked")
    private static Object deepCopy(Object value) {
        if (value instanceof Map) {
            Map<Object, Object> src = (Map<Object, Object>) value;
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> e : src.entrySet()) {
                copy.put(String.valueOf(e.getKey()), deepCopy(e.getValue()));
            }
            return copy;
        } else if (value instanceof List) {
            List<Object> src = (List<Object>) value;
            List<Object> copy = new ArrayList<>(src.size());
            for (Object item : src) {
                copy.add(deepCopy(item));
            }
            return copy;
        } else {
            return value;
        }
    }
}
