# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.3.0] - 2026-09-07

### Added

- **JSON Patch (RFC 6902) generation and application.** New `JsonPatch` class:
  - `JsonPatch.generate(Object a, Object b)` walks the same before/after values as
    `JsonDiff.diff`, but emits a standard RFC 6902 patch — a `List<PatchOperation>` of
    `add`/`remove`/`replace` operations addressed by real RFC 6901 JSON Pointer paths — instead
    of the library's own `DiffEntry` shape.
  - `JsonPatch.apply(Object before, List<PatchOperation> patch)` is a general-purpose RFC 6902
    applier: it replays a patch against a document to produce the patched result, without
    mutating the input. It supports mid-array insertion, the `"-"` append marker, and throws the
    new `JsonPatchException` when an operation's path doesn't resolve.
  - Array `add`/`remove` operations are ordered so a patch is safe to apply sequentially:
    growth is emitted as ascending appends, shrinkage as descending removes (removing
    highest-index-first so earlier removes never invalidate a later operation's index).
  - New `PatchOp` enum (`ADD`/`REMOVE`/`REPLACE`) and `PatchOperation` class (`op()`, `path()`,
    `value()`, plus `toJsonObject()` for the `{"op", "path", "value"}` wire shape).
  - Proven with a round-trip test: diffing two nontrivial nested documents (objects, arrays,
    additions, removals, replacements, and keys requiring JSON Pointer escaping), generating a
    patch from that diff, applying it to the "before" document, and asserting the result equals
    "after" exactly — both by value equality and independently via `JsonDiff.diff(...).isEmpty()`.
  - README: new "JSON Patch (RFC 6902)" section with a runnable diff -> generate -> apply ->
    round-trip example, plus an additional `## Usage` example.

## [1.2.0] - 2026-09-06

### Fixed

- `JsonDiff.diff()` no longer requires both sides of an object/array to be the exact same
  Java runtime class in order to recurse into them. Previously, two Maps (or two Lists) with
  identical *structure* but different concrete implementations — e.g. `JsonParser`'s
  `LinkedHashMap`/`ArrayList` on one side and a hand-built `Map.of(...)`/`List.of(...)` on the
  other, a common pattern when diffing parsed JSON against an "expected" value in a test —
  were reported as one coarse `CHANGED` entry for the entire value instead of being diffed
  key-by-key / index-by-index. Recursion now keys off JSON "kind" (`instanceof Map` /
  `instanceof List`) instead of exact class equality.

## [1.1.0] - 2026-09-06

### Added

- Test coverage for edge cases in `JsonParser` and `JsonDiff`:
  - A UTF-16 surrogate pair unicode escape (e.g. an emoji encoded as two
    `\uXXXX` escapes) combines into the correct single code point.
  - A leading zero followed by another digit (e.g. `01`) is correctly
    rejected as invalid JSON, both at the top level and inside an array.
  - Duplicate keys in a JSON object resolve to "last occurrence wins" (the
    underlying `LinkedHashMap` behavior), with only one entry retained.
  - `JsonDiff.diff()` on two bare top-level scalar documents (not wrapped
    in an object) reports a single `CHANGED` entry at the empty root path
    `""`.
  - `JsonDiff.diff()` on two bare top-level array documents reports
    entries rooted directly at `"/<index>"`.

No behavioral changes were needed — all new edge-case tests passed against
the existing implementation.
