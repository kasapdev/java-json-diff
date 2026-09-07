# java-json-diff

[![CI](https://github.com/kasapdev/java-json-diff/actions/workflows/ci.yml/badge.svg)](https://github.com/kasapdev/java-json-diff/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE) ![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)

A small, zero-dependency Java library that parses JSON with a hand-rolled recursive-descent
parser and computes a structural diff between two JSON documents, reporting each difference
as an `ADDED` / `REMOVED` / `CHANGED` entry keyed by a JSON-pointer-style path (e.g.
`/user/tags/2`). It can also emit that same diff as a standard [RFC 6902](https://www.rfc-editor.org/rfc/rfc6902)
JSON Patch and apply such a patch back to a document — see [JSON Patch (RFC 6902)](#json-patch-rfc-6902)
below. Pure Java 17, no external libraries, no build tool required.

## Build & Run

```bash
JAVAC="/path/to/jdk/bin/javac"
JAVA="/path/to/jdk/bin/java"

# Compile the library
"$JAVAC" -d out $(find src/main/java -name "*.java")

# Compile the tests against the compiled library
"$JAVAC" -cp out -d out $(find src/test/java -name "*.java")

# Run the tests
"$JAVA" -cp out dev.kasapdev.jsondiff.JsonParserTest
"$JAVA" -cp out dev.kasapdev.jsondiff.JsonDiffTest
"$JAVA" -cp out dev.kasapdev.jsondiff.JsonPatchTest

# Run the CLI on two JSON files
"$JAVA" -cp out dev.kasapdev.jsondiff.Main fileA.json fileB.json
```

On Windows, replace `$(find ... -name "*.java")` with an explicit file list, or run the
`find` substitution from Git Bash / WSL.

## Usage

```java
import dev.kasapdev.jsondiff.JsonParser;
import dev.kasapdev.jsondiff.JsonDiff;
import dev.kasapdev.jsondiff.DiffEntry;

import java.util.List;

public class Example {
    public static void main(String[] args) {
        Object before = JsonParser.parse("{\"user\":{\"name\":\"Ada\",\"tags\":[\"admin\"]}}");
        Object after  = JsonParser.parse("{\"user\":{\"name\":\"Grace\",\"tags\":[\"admin\",\"dev\"]}}");

        List<DiffEntry> diffs = JsonDiff.diff(before, after);
        for (DiffEntry entry : diffs) {
            System.out.println(entry);
            // CHANGED /user/name: "Ada" -> "Grace"
            // ADDED   /user/tags/1 = "dev"
        }
    }
}
```

A second, common pattern: use the diff purely as a boolean "did anything change" check, e.g. in
a test that compares parsed JSON against an expected value built with `Map.of(...)`:

```java
Object expected = Map.of("status", "ok", "retries", 0.0);
Object actual = JsonParser.parse(responseBody);

List<DiffEntry> diffs = JsonDiff.diff(expected, actual);
if (!diffs.isEmpty()) {
    throw new AssertionError("Unexpected response:\n" + diffs.stream()
            .map(DiffEntry::toString)
            .collect(java.util.stream.Collectors.joining("\n")));
}
```

## JSON Patch (RFC 6902)

`JsonPatch` builds on `JsonDiff`'s structural walk but emits standard
[RFC 6902](https://www.rfc-editor.org/rfc/rfc6902) `add`/`remove`/`replace` operations addressed
by real [RFC 6901](https://www.rfc-editor.org/rfc/rfc6901) JSON Pointer paths, instead of the
library's own `DiffEntry` shape. It also ships an applier, so a patch generated from one pair of
documents can actually be replayed to reconstruct the "after" document from the "before" one:

```java
import dev.kasapdev.jsondiff.JsonParser;
import dev.kasapdev.jsondiff.JsonPatch;
import dev.kasapdev.jsondiff.PatchOperation;

import java.util.List;

public class PatchExample {
    public static void main(String[] args) {
        Object before = JsonParser.parse("{\"user\":{\"name\":\"Ada\",\"roles\":[\"admin\"]}}");
        Object after  = JsonParser.parse("{\"user\":{\"name\":\"Grace\",\"roles\":[\"admin\",\"dev\"]}}");

        // 1. Generate an RFC 6902 patch from the diff between the two documents.
        List<PatchOperation> patch = JsonPatch.generate(before, after);
        for (PatchOperation op : patch) {
            System.out.println(op.toJsonObject());
            // {op=replace, path=/user/name, value=Grace}
            // {op=add, path=/user/roles/1, value=dev}
        }

        // 2. Apply the patch to "before". "before" itself is never mutated.
        Object reconstructed = JsonPatch.apply(before, patch);

        // 3. Round trip: reconstructed is now structurally identical to "after".
        System.out.println(JsonDiff.diff(reconstructed, after).isEmpty()); // true
    }
}
```

`JsonPatch.apply` is a general-purpose RFC 6902 applier, not just the inverse of `generate`: it
also accepts hand-built patches, including array insertion at an arbitrary index and the `"-"`
"append at the end" pointer segment, and throws `JsonPatchException` if an operation's path
doesn't resolve (e.g. `replace`/`remove` on a member or index that doesn't exist).

## API

### `JsonParser`

- `static Object parse(String json)` — parses a JSON document into plain Java types:
  `Map<String, Object>` (objects, insertion-ordered), `List<Object>` (arrays), `String`,
  `Double` (all numbers), `Boolean`, or `null`. Throws `JsonParseException` on malformed input.

### `JsonDiff`

- `static List<DiffEntry> diff(Object a, Object b)` — walks two parsed JSON values
  recursively and returns a flat list of differences. Object keys and array indices are
  combined into a JSON-pointer-style path (`~` and `/` in keys are escaped as `~0`/`~1`).

### `DiffEntry`

- `String path()` — the JSON-pointer-style path of the change.
- `DiffType type()` — one of `ADDED`, `REMOVED`, `CHANGED`.
- `Object oldValue()` / `Object newValue()` — the values on each side (nullable).

### `JsonPatch`

- `static List<PatchOperation> generate(Object a, Object b)` — walks the same before/after
  values as `JsonDiff.diff`, returning an RFC 6902 patch (`add`/`remove`/`replace` operations
  addressed by RFC 6901 JSON Pointer paths) that transforms `a` into `b`.
- `static Object apply(Object before, List<PatchOperation> patch)` — applies a patch to
  `before` and returns the result; `before` is not mutated. Throws `JsonPatchException` if an
  operation's path doesn't resolve.

### `PatchOperation`

- `PatchOp op()` — one of `ADD`, `REMOVE`, `REPLACE`.
- `String path()` — the RFC 6901 JSON Pointer path the operation applies to.
- `Object value()` — the value to add/replace with (`null`, and unused, for `REMOVE`).
- `Map<String, Object> toJsonObject()` — the `{"op": ..., "path": ..., "value": ...}` wire shape
  (omits `"value"` for `REMOVE`, per the RFC).

### `Main`

CLI entry point: `java dev.kasapdev.jsondiff.Main <fileA.json> <fileB.json>` parses both
files and prints every difference.

## License

MIT — see [LICENSE](LICENSE).
