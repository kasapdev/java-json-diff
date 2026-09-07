package dev.kasapdev.jsondiff;

import java.util.List;
import java.util.Map;

public final class JsonPatchTest {

    public static void main(String[] args) {
        testGenerateOnIdenticalDocumentsIsEmpty();
        testGenerateAddedKey();
        testGenerateRemovedKey();
        testGenerateReplacedScalar();
        testGeneratePointerEscaping();
        testGenerateArrayGrowthAppendsAscending();
        testGenerateArrayShrinkRemovesDescending();
        testApplyDoesNotMutateInput();
        testApplyHandBuiltPatchWithMidArrayInsertAndAppendMarker();
        testApplyThrowsOnInvalidOperations();
        testRoundTripTopLevelScalar();
        testRoundTripNontrivialNestedStructure();

        TestKit.finish();
    }

    private static void testGenerateOnIdenticalDocumentsIsEmpty() {
        Object a = JsonParser.parse("{\"a\":1,\"b\":[1,2,3]}");
        Object b = JsonParser.parse("{\"a\":1,\"b\":[1,2,3]}");
        TestKit.check("identical documents produce an empty patch", JsonPatch.generate(a, b).isEmpty());
    }

    private static void testGenerateAddedKey() {
        Object a = JsonParser.parse("{\"a\":1}");
        Object b = JsonParser.parse("{\"a\":1,\"b\":2}");
        List<PatchOperation> patch = JsonPatch.generate(a, b);
        TestKit.check("added key produces exactly one operation", patch.size() == 1);
        PatchOperation op = patch.get(0);
        TestKit.check("added key operation is ADD", op.op() == PatchOp.ADD);
        TestKit.check("added key operation has correct path", op.path().equals("/b"));
        TestKit.check("added key operation has correct value", op.value().equals(2.0));
        TestKit.check("ADD op's RFC name is 'add'", op.op().rfcName().equals("add"));
    }

    private static void testGenerateRemovedKey() {
        Object a = JsonParser.parse("{\"a\":1,\"b\":2}");
        Object b = JsonParser.parse("{\"a\":1}");
        List<PatchOperation> patch = JsonPatch.generate(a, b);
        TestKit.check("removed key produces exactly one operation", patch.size() == 1);
        PatchOperation op = patch.get(0);
        TestKit.check("removed key operation is REMOVE", op.op() == PatchOp.REMOVE);
        TestKit.check("removed key operation has correct path", op.path().equals("/b"));
        TestKit.check("remove op's toJsonObject omits 'value'", !op.toJsonObject().containsKey("value"));
    }

    private static void testGenerateReplacedScalar() {
        Object a = JsonParser.parse("{\"name\":\"Ada\"}");
        Object b = JsonParser.parse("{\"name\":\"Grace\"}");
        List<PatchOperation> patch = JsonPatch.generate(a, b);
        TestKit.check("replaced scalar produces exactly one operation", patch.size() == 1);
        PatchOperation op = patch.get(0);
        TestKit.check("replaced scalar operation is REPLACE", op.op() == PatchOp.REPLACE);
        TestKit.check("replaced scalar operation has correct value", op.value().equals("Grace"));
        Map<String, Object> obj = op.toJsonObject();
        TestKit.check("toJsonObject carries op/path/value", "replace".equals(obj.get("op"))
                && "/name".equals(obj.get("path")) && "Grace".equals(obj.get("value")));
    }

    private static void testGeneratePointerEscaping() {
        Object a = JsonParser.parse("{}");
        Object b = JsonParser.parse("{\"a/b\":1}");
        List<PatchOperation> patch = JsonPatch.generate(a, b);
        TestKit.check("key containing '/' is escaped as ~1 in the generated path",
                patch.get(0).path().equals("/a~1b"));

        Object c = JsonParser.parse("{}");
        Object d = JsonParser.parse("{\"a~b\":1}");
        List<PatchOperation> patch2 = JsonPatch.generate(c, d);
        TestKit.check("key containing '~' is escaped as ~0 in the generated path",
                patch2.get(0).path().equals("/a~0b"));
    }

    private static void testGenerateArrayGrowthAppendsAscending() {
        Object a = JsonParser.parse("[1,2]");
        Object b = JsonParser.parse("[1,2,3,4]");
        List<PatchOperation> patch = JsonPatch.generate(a, b);
        TestKit.check("array growth by two produces exactly two ADD operations", patch.size() == 2);
        TestKit.check("growth ops are ADD /2 then ADD /3 (ascending)",
                patch.get(0).op() == PatchOp.ADD && patch.get(0).path().equals("/2")
                        && patch.get(1).op() == PatchOp.ADD && patch.get(1).path().equals("/3"));
    }

    private static void testGenerateArrayShrinkRemovesDescending() {
        Object a = JsonParser.parse("[1,2,3,4]");
        Object b = JsonParser.parse("[1,2]");
        List<PatchOperation> patch = JsonPatch.generate(a, b);
        TestKit.check("array shrink by two produces exactly two REMOVE operations", patch.size() == 2);
        TestKit.check("shrink ops are REMOVE /3 then REMOVE /2 (descending, so indices stay valid)",
                patch.get(0).op() == PatchOp.REMOVE && patch.get(0).path().equals("/3")
                        && patch.get(1).op() == PatchOp.REMOVE && patch.get(1).path().equals("/2"));

        // Prove it isn't just shape-correct: actually apply it and check the result.
        Object result = JsonPatch.apply(a, patch);
        TestKit.check("applying the descending-order shrink patch yields the correct array", result.equals(b));
    }

    private static void testApplyDoesNotMutateInput() {
        Object before = JsonParser.parse("{\"a\":{\"b\":[1,2,3]}}");
        Object after = JsonParser.parse("{\"a\":{\"b\":[1,2,3,4]}}");
        List<PatchOperation> patch = JsonPatch.generate(before, after);
        JsonPatch.apply(before, patch);
        TestKit.check("apply() does not mutate the 'before' value it was given",
                before.equals(JsonParser.parse("{\"a\":{\"b\":[1,2,3]}}")));
    }

    @SuppressWarnings("unchecked")
    private static void testApplyHandBuiltPatchWithMidArrayInsertAndAppendMarker() {
        // A hand-built patch (not one JsonPatch.generate() produced), to prove apply() is a
        // real general-purpose RFC 6902 applier: insertion at a middle index shifts later
        // elements up, and "-" means "append at the end".
        Object doc = JsonParser.parse("{\"list\":[1,2,3]}");
        List<PatchOperation> patch = List.of(
                new PatchOperation(PatchOp.ADD, "/list/1", 99.0),
                new PatchOperation(PatchOp.REMOVE, "/list/0", null),
                new PatchOperation(PatchOp.ADD, "/list/-", 42.0)
        );
        Object result = JsonPatch.apply(doc, patch);
        List<Object> list = (List<Object>) ((Map<String, Object>) result).get("list");
        TestKit.check("mid-array insert, then remove, then append produces the expected sequence",
                list.equals(List.of(99.0, 2.0, 3.0, 42.0)));
    }

    private static void testApplyThrowsOnInvalidOperations() {
        Object doc = JsonParser.parse("{\"a\":1}");
        TestKit.check("replace on a nonexistent member throws",
                throwsPatchException(doc, new PatchOperation(PatchOp.REPLACE, "/missing", 1.0)));
        TestKit.check("remove on a nonexistent member throws",
                throwsPatchException(doc, new PatchOperation(PatchOp.REMOVE, "/missing", null)));

        Object arr = JsonParser.parse("[1,2,3]");
        TestKit.check("replace at an out-of-range array index throws",
                throwsPatchException(arr, new PatchOperation(PatchOp.REPLACE, "/5", 1.0)));
        TestKit.check("add at an out-of-range array index (beyond append position) throws",
                throwsPatchException(arr, new PatchOperation(PatchOp.ADD, "/9", 1.0)));

        Object scalar = JsonParser.parse("5");
        TestKit.check("navigating into a scalar value throws",
                throwsPatchException(scalar, new PatchOperation(PatchOp.REPLACE, "/x", 1.0)));
    }

    private static boolean throwsPatchException(Object doc, PatchOperation op) {
        try {
            JsonPatch.apply(doc, List.of(op));
            return false;
        } catch (JsonPatchException e) {
            return true;
        }
    }

    private static void testRoundTripTopLevelScalar() {
        Object a = JsonParser.parse("5");
        Object b = JsonParser.parse("\"six\"");
        List<PatchOperation> patch = JsonPatch.generate(a, b);
        TestKit.check("top-level scalar-to-scalar diff produces a single REPLACE at the root path",
                patch.size() == 1 && patch.get(0).op() == PatchOp.REPLACE && patch.get(0).path().equals(""));
        Object result = JsonPatch.apply(a, patch);
        TestKit.check("applying a root-level replace yields the new top-level value", result.equals("six"));
    }

    /**
     * The core proof: diff two nontrivial, deeply nested documents (objects and arrays,
     * additions, removals, and replacements at multiple levels, plus keys that require JSON
     * Pointer escaping), generate an RFC 6902 patch from that diff, apply the patch to the
     * "before" document, and assert the result is exactly the "after" document.
     */
    private static void testRoundTripNontrivialNestedStructure() {
        String beforeJson = "{"
                + "\"user\":{"
                + "  \"name\":\"Ada\","
                + "  \"age\":30,"
                + "  \"roles\":[\"admin\",\"dev\",\"ops\"],"
                + "  \"address\":{\"city\":\"London\",\"zip\":\"12345\"}"
                + "},"
                + "\"tags\":[\"x\",\"y\",\"z\"],"
                + "\"active\":true,"
                + "\"meta\":{\"a/b\":1,\"a~b\":2}"
                + "}";
        String afterJson = "{"
                + "\"user\":{"
                + "  \"name\":\"Grace\","
                + "  \"age\":30,"
                + "  \"roles\":[\"admin\",\"dev\",\"lead\",\"qa\"],"
                + "  \"address\":{\"city\":\"Paris\"}"
                + "},"
                + "\"tags\":[\"x\",\"w\"],"
                + "\"active\":false,"
                + "\"meta\":{\"a/b\":99,\"a~b\":2},"
                + "\"extra\":{\"note\":\"new field\",\"count\":5}"
                + "}";
        Object before = JsonParser.parse(beforeJson);
        Object after = JsonParser.parse(afterJson);

        List<PatchOperation> patch = JsonPatch.generate(before, after);
        TestKit.check("a nontrivial diff generates more than one patch operation", patch.size() > 1);

        Object result = JsonPatch.apply(before, patch);

        TestKit.check("round trip: patched 'before' equals 'after' by value equality", result.equals(after));
        TestKit.check("round trip: patched 'before' equals 'after' by JsonDiff (independent structural check)",
                JsonDiff.diff(result, after).isEmpty());
        TestKit.check("round trip: original 'before' value is untouched",
                before.equals(JsonParser.parse(beforeJson)));
    }
}
