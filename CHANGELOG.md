# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.3.0] - 2026-09-20

### Added

- `V remove(K key)` - removes an entry and returns its live value (or `null`
  if absent/expired). Not counted as a hit or miss.
- `V computeIfAbsent(K key, Function<? super K, ? extends V> loader)` and an
  overload taking an explicit `long ttlMillis` - returns the cached value or
  computes, stores and returns it. Runs under the cache lock, so concurrent
  callers for the same key run the loader once. Counts as one hit or miss. A
  `null` result or an exception stores nothing.
- `List<K> keys()` - snapshot of live keys, least- to most-recently-used, with
  no effect on recency or statistics.

## [1.2.0] - 2026-09-07

### Added

- Cache hit/miss statistics on `LruCache`:
  - `long hitCount()` - lifetime count of `get()` calls that found a live,
    non-expired entry.
  - `long missCount()` - lifetime count of `get()` calls that found nothing
    (key absent, or present but TTL-expired).
  - `double hitRate()` - `hitCount / (hitCount + missCount)`, returning
    `0.0` before the first `get()` call to avoid dividing by zero.
  - Following Guava's `CacheStats` convention, these counters are
    cumulative for the life of the cache instance and are **not** reset
    by `put()` or `clear()`.
- New README "Cache Statistics" section with a runnable example, plus a
  second `## Usage` example (a cache-in-front-of-a-lookup pattern).
- New hand-calculated test (`testHitAndMissCountsWithExactExpectedValues`)
  that walks a specific sequence of puts and gets - including a
  capacity-triggered eviction and a post-`clear()` get - and asserts
  exact expected `hitCount()`, `missCount()`, and `hitRate()` values.

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
