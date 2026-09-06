# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

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
