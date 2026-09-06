# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

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
