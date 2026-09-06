package dev.kasapdev.jsondiff;

import java.util.List;
import java.util.Map;

public final class JsonDiffTest {

    public static void main(String[] args) {
        testIdenticalDocumentsProduceNoDiff();
        testAddedKey();
        testRemovedKey();
        testChangedScalarValue();
        testChangedTypeIsReportedAsChanged();
        testArrayLengthGrowth();
        testArrayLengthShrink();
        testNestedPathReporting();
        testNullValueVsMissingKey();
        testPointerEscaping();
        testTopLevelScalarDiff();
        testTopLevelArrayDiff();
        testDiffingAcrossDifferentMapImplementationsRecursesStructurally();
        testDiffingAcrossDifferentListImplementationsRecursesStructurally();
        testIdenticalContentAcrossDifferentMapImplementationsIsNoDiff();

        TestKit.finish();
    }

    private static void testDiffingAcrossDifferentMapImplementationsRecursesStructurally() {
        // "b" is a hand-built java.util.Map (a different concrete class than the LinkedHashMap
        // JsonParser produces) representing the same JSON object shape as "a" but with one
        // changed value. This is the common pattern of comparing parsed JSON against an
        // "expected" value built with Map.of(...) in a test. The diff must still recurse into
        // the object key-by-key based on JSON kind, not fail closed just because the two sides
        // happen to be different concrete Map implementations.
        Object a = JsonParser.parse("{\"name\":\"Ada\",\"age\":30}");
        Map<String, Object> b = Map.of("name", "Ada", "age", 31.0);
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("diffing across Map implementations finds exactly the one real change",
                diffs.size() == 1);
        TestKit.check("the change is reported at the specific key, not the whole object",
                diffs.get(0).path().equals("/age") && diffs.get(0).type() == DiffType.CHANGED);
    }

    private static void testDiffingAcrossDifferentListImplementationsRecursesStructurally() {
        Object a = JsonParser.parse("[1,2,3]");
        List<Object> b = List.of(1.0, 9.0, 3.0);
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("diffing across List implementations finds exactly the one real change",
                diffs.size() == 1);
        TestKit.check("the change is reported at the specific index, not the whole array",
                diffs.get(0).path().equals("/1") && diffs.get(0).type() == DiffType.CHANGED);
    }

    private static void testIdenticalContentAcrossDifferentMapImplementationsIsNoDiff() {
        Object a = JsonParser.parse("{\"a\":1,\"b\":[1,2,3]}");
        Map<String, Object> b = Map.of("a", 1.0, "b", List.of(1.0, 2.0, 3.0));
        TestKit.check("structurally identical content across different Map/List implementations produces no diff",
                JsonDiff.diff(a, b).isEmpty());
    }

    private static void testIdenticalDocumentsProduceNoDiff() {
        Object a = JsonParser.parse("{\"a\":1,\"b\":[1,2,3]}");
        Object b = JsonParser.parse("{\"a\":1,\"b\":[1,2,3]}");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("identical documents produce empty diff", diffs.isEmpty());
    }

    private static void testAddedKey() {
        Object a = JsonParser.parse("{\"a\":1}");
        Object b = JsonParser.parse("{\"a\":1,\"b\":2}");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("added key produces exactly one entry", diffs.size() == 1);
        DiffEntry entry = diffs.get(0);
        TestKit.check("added key entry has type ADDED", entry.type() == DiffType.ADDED);
        TestKit.check("added key entry has correct path", entry.path().equals("/b"));
        TestKit.check("added key entry has correct new value", entry.newValue().equals(2.0));
        TestKit.check("added key entry has null old value", entry.oldValue() == null);
    }

    private static void testRemovedKey() {
        Object a = JsonParser.parse("{\"a\":1,\"b\":2}");
        Object b = JsonParser.parse("{\"a\":1}");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("removed key produces exactly one entry", diffs.size() == 1);
        DiffEntry entry = diffs.get(0);
        TestKit.check("removed key entry has type REMOVED", entry.type() == DiffType.REMOVED);
        TestKit.check("removed key entry has correct path", entry.path().equals("/b"));
        TestKit.check("removed key entry retains old value", entry.oldValue().equals(2.0));
    }

    private static void testChangedScalarValue() {
        Object a = JsonParser.parse("{\"name\":\"Ada\"}");
        Object b = JsonParser.parse("{\"name\":\"Grace\"}");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("changed value produces exactly one entry", diffs.size() == 1);
        DiffEntry entry = diffs.get(0);
        TestKit.check("changed value entry has type CHANGED", entry.type() == DiffType.CHANGED);
        TestKit.check("changed value old value correct", entry.oldValue().equals("Ada"));
        TestKit.check("changed value new value correct", entry.newValue().equals("Grace"));
    }

    private static void testChangedTypeIsReportedAsChanged() {
        Object a = JsonParser.parse("{\"value\":42}");
        Object b = JsonParser.parse("{\"value\":\"42\"}");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("type change produces exactly one entry", diffs.size() == 1);
        TestKit.check("type change is reported as CHANGED", diffs.get(0).type() == DiffType.CHANGED);

        Object c = JsonParser.parse("{\"value\":{\"nested\":1}}");
        Object d = JsonParser.parse("{\"value\":[1,2]}");
        List<DiffEntry> diffs2 = JsonDiff.diff(c, d);
        TestKit.check("object-to-array type change is CHANGED", diffs2.size() == 1 && diffs2.get(0).type() == DiffType.CHANGED);
    }

    private static void testArrayLengthGrowth() {
        Object a = JsonParser.parse("{\"list\":[1,2]}");
        Object b = JsonParser.parse("{\"list\":[1,2,3]}");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("array growth produces exactly one entry", diffs.size() == 1);
        DiffEntry entry = diffs.get(0);
        TestKit.check("array growth entry is ADDED", entry.type() == DiffType.ADDED);
        TestKit.check("array growth entry path indexes new element", entry.path().equals("/list/2"));
        TestKit.check("array growth entry value correct", entry.newValue().equals(3.0));
    }

    private static void testArrayLengthShrink() {
        Object a = JsonParser.parse("{\"list\":[1,2,3]}");
        Object b = JsonParser.parse("{\"list\":[1,2]}");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("array shrink produces exactly one entry", diffs.size() == 1);
        DiffEntry entry = diffs.get(0);
        TestKit.check("array shrink entry is REMOVED", entry.type() == DiffType.REMOVED);
        TestKit.check("array shrink entry path indexes removed element", entry.path().equals("/list/2"));
    }

    private static void testNestedPathReporting() {
        Object a = JsonParser.parse("{\"user\":{\"name\":\"Ada\",\"tags\":[\"x\",\"y\",\"z\"]}}");
        Object b = JsonParser.parse("{\"user\":{\"name\":\"Ada\",\"tags\":[\"x\",\"y\",\"w\"]}}");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("nested array element change produces exactly one entry", diffs.size() == 1);
        DiffEntry entry = diffs.get(0);
        TestKit.check("deep nested path is correct", entry.path().equals("/user/tags/2"));
        TestKit.check("deep nested change type is CHANGED", entry.type() == DiffType.CHANGED);
        TestKit.check("deep nested old/new values correct", entry.oldValue().equals("z") && entry.newValue().equals("w"));
    }

    private static void testNullValueVsMissingKey() {
        // Explicit JSON null value should be treated as CHANGED (not ADDED) when it differs
        // from a present non-null value on the other side, since the key exists on both sides.
        Object a = JsonParser.parse("{\"a\":null}");
        Object b = JsonParser.parse("{\"a\":5}");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("null-to-value key produces one entry", diffs.size() == 1);
        TestKit.check("null-to-value change is CHANGED not ADDED", diffs.get(0).type() == DiffType.CHANGED);

        Object c = JsonParser.parse("{\"a\":null}");
        Object d = JsonParser.parse("{\"a\":null}");
        TestKit.check("two explicit nulls are equal (no diff)", JsonDiff.diff(c, d).isEmpty());
    }

    private static void testPointerEscaping() {
        TestKit.check("tilde escaped in pointer path", JsonDiff.escapePointerSegment("a~b").equals("a~0b"));
        TestKit.check("slash escaped in pointer path", JsonDiff.escapePointerSegment("a/b").equals("a~1b"));

        Object a = JsonParser.parse("{}");
        Object b = JsonParser.parse("{\"a/b\":1}");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("key containing slash is escaped in reported path", diffs.get(0).path().equals("/a~1b"));
    }

    private static void testTopLevelScalarDiff() {
        // Diffing two documents that are themselves bare scalars (not wrapped in an
        // object/array) should report a single CHANGED entry at the root path "".
        Object a = JsonParser.parse("5");
        Object b = JsonParser.parse("6");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("top-level scalar diff produces exactly one entry", diffs.size() == 1);
        TestKit.check("top-level scalar diff type is CHANGED", diffs.get(0).type() == DiffType.CHANGED);
        TestKit.check("top-level scalar diff path is the empty root path", diffs.get(0).path().equals(""));
        TestKit.check("top-level scalar diff preserves old/new values", diffs.get(0).oldValue().equals(5.0) && diffs.get(0).newValue().equals(6.0));

        TestKit.check("two equal top-level scalars produce no diff", JsonDiff.diff(JsonParser.parse("\"x\""), JsonParser.parse("\"x\"")).isEmpty());
    }

    private static void testTopLevelArrayDiff() {
        // Diffing two documents that are themselves bare top-level arrays should
        // report entries rooted directly at "/<index>" (no object wrapper).
        Object a = JsonParser.parse("[1,2,3]");
        Object b = JsonParser.parse("[1,9,3]");
        List<DiffEntry> diffs = JsonDiff.diff(a, b);
        TestKit.check("top-level array diff produces exactly one entry", diffs.size() == 1);
        TestKit.check("top-level array diff path is rooted at the index directly", diffs.get(0).path().equals("/1"));
        TestKit.check("top-level array diff type is CHANGED", diffs.get(0).type() == DiffType.CHANGED);
    }
}
