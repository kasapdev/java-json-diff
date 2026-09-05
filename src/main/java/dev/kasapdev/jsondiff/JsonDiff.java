package dev.kasapdev.jsondiff;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Computes a structural diff between two parsed JSON values (as produced by
 * {@link JsonParser#parse(String)}), reporting differences as a flat list of
 * {@link DiffEntry} objects keyed by JSON-pointer-style path.
 */
public final class JsonDiff {

    private JsonDiff() {
    }

    /**
     * Diffs two JSON values.
     *
     * @param a the "before" value
     * @param b the "after" value
     * @return a list of differences, empty if the values are structurally equal
     */
    public static List<DiffEntry> diff(Object a, Object b) {
        List<DiffEntry> entries = new ArrayList<>();
        diffValues("", a, b, entries);
        return entries;
    }

    @SuppressWarnings("unchecked")
    private static void diffValues(String path, Object a, Object b, List<DiffEntry> out) {
        if (Objects.equals(a, b)) {
            return;
        }
        if (a == null || b == null) {
            out.add(new DiffEntry(path, DiffType.CHANGED, a, b));
            return;
        }
        if (!a.getClass().equals(b.getClass())) {
            out.add(new DiffEntry(path, DiffType.CHANGED, a, b));
            return;
        }
        if (a instanceof Map) {
            diffMaps(path, (Map<String, Object>) a, (Map<String, Object>) b, out);
        } else if (a instanceof List) {
            diffLists(path, (List<Object>) a, (List<Object>) b, out);
        } else {
            // String, Double, Boolean and unequal (Objects.equals already checked above)
            out.add(new DiffEntry(path, DiffType.CHANGED, a, b));
        }
    }

    private static void diffMaps(String path, Map<String, Object> a, Map<String, Object> b, List<DiffEntry> out) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        for (String key : keys) {
            String childPath = path + "/" + escapePointerSegment(key);
            boolean inA = a.containsKey(key);
            boolean inB = b.containsKey(key);
            if (inA && inB) {
                diffValues(childPath, a.get(key), b.get(key), out);
            } else if (inA) {
                out.add(new DiffEntry(childPath, DiffType.REMOVED, a.get(key), null));
            } else {
                out.add(new DiffEntry(childPath, DiffType.ADDED, null, b.get(key)));
            }
        }
    }

    private static void diffLists(String path, List<Object> a, List<Object> b, List<DiffEntry> out) {
        int max = Math.max(a.size(), b.size());
        for (int i = 0; i < max; i++) {
            String childPath = path + "/" + i;
            boolean inA = i < a.size();
            boolean inB = i < b.size();
            if (inA && inB) {
                diffValues(childPath, a.get(i), b.get(i), out);
            } else if (inA) {
                out.add(new DiffEntry(childPath, DiffType.REMOVED, a.get(i), null));
            } else {
                out.add(new DiffEntry(childPath, DiffType.ADDED, null, b.get(i)));
            }
        }
    }

    /**
     * Escapes a JSON object key for use as one segment of a JSON-pointer-style path,
     * per RFC 6901 ({@code ~} -&gt; {@code ~0}, {@code /} -&gt; {@code ~1}).
     */
    static String escapePointerSegment(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }
}
