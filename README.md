# java-json-diff

A small, zero-dependency Java library that parses JSON with a hand-rolled recursive-descent
parser and computes a structural diff between two JSON documents, reporting each difference
as an `ADDED` / `REMOVED` / `CHANGED` entry keyed by a JSON-pointer-style path (e.g.
`/user/tags/2`). Pure Java 17, no external libraries, no build tool required.

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

### `Main`

CLI entry point: `java dev.kasapdev.jsondiff.Main <fileA.json> <fileB.json>` parses both
files and prints every difference.

## License

MIT — see [LICENSE](LICENSE).
