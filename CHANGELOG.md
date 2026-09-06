# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.1.0] - 2026-09-06

### Added

- Test coverage for documented-but-untested `LruCache` edge cases:
  - The constructor rejects a capacity less than 1 (`0` and negative
    values) with `IllegalArgumentException`.
  - `size()` is confirmed to count an expired-but-not-yet-purged entry
    (purging only happens lazily inside `get()`), matching the
    documented contract.
  - Re-putting an already-present key does not grow the cache past
    capacity or evict any other entry.

No behavioral changes were needed — all new edge-case tests passed against
the existing implementation.
