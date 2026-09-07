# java-simple-cache

[![CI](https://github.com/kasapdev/java-simple-cache/actions/workflows/ci.yml/badge.svg)](https://github.com/kasapdev/java-simple-cache/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE) ![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)

A generic, thread-safe in-memory `LruCache<K, V>` for Java with a fixed capacity (least-recently-used eviction) and optional per-entry time-to-live (TTL) expiry. Zero dependencies, pure Java 17, no build tool required.

## Build & Run

```bash
cd java-simple-cache

# Compile the library
javac -d out $(find src/main/java -name "*.java")

# Compile the tests against the compiled library
javac -cp out -d out $(find src/test/java -name "*.java")

# Run the test suite
java -cp out dev.kasapdev.simplecache.LruCacheTest
```

All test output lines are prefixed `[PASS]` or `[FAIL]`, ending with a summary line and a non-zero exit code if anything failed.

## Usage

```java
import dev.kasapdev.simplecache.LruCache;

public class Example {
    public static void main(String[] args) {
        // Capacity of 100 entries, no default expiry.
        LruCache<String, String> cache = new LruCache<>(100);

        cache.put("user:42", "Kayra");
        System.out.println(cache.get("user:42")); // "Kayra"

        // A cache with a 5-second default TTL for every entry.
        LruCache<String, String> sessionCache = new LruCache<>(1000, 5_000L);
        sessionCache.put("session:abc", "active");

        // Override the TTL for a single entry (here: 60 seconds instead of the 5s default).
        sessionCache.put("session:long-lived", "active", 60_000L);

        // A ttlMillis of 0 (or negative) means "never expires" for that entry.
        sessionCache.put("session:permanent", "active", 0L);

        System.out.println(sessionCache.size());
        sessionCache.clear();
    }
}
```

Another common pattern - a fixed-size cache in front of an expensive lookup, falling back on a miss:

```java
import dev.kasapdev.simplecache.LruCache;

public class LookupExample {

    private static final LruCache<Long, String> cache = new LruCache<>(200);

    public static void main(String[] args) {
        System.out.println(fetchUserName(42L)); // miss -> loads and caches
        System.out.println(fetchUserName(42L)); // hit  -> served from cache
    }

    private static String fetchUserName(long userId) {
        String cached = cache.get(userId);
        if (cached != null) {
            return cached;
        }
        String loaded = loadUserNameFromDatabase(userId); // expensive
        cache.put(userId, loaded);
        return loaded;
    }

    private static String loadUserNameFromDatabase(long userId) {
        return "user-" + userId;
    }
}
```

## Cache Statistics

Every `LruCache` tracks lifetime hit/miss counts, so you can see how effective it
actually is in production instead of guessing:

```java
import dev.kasapdev.simplecache.LruCache;

public class StatsExample {
    public static void main(String[] args) {
        LruCache<String, String> cache = new LruCache<>(100);
        cache.put("user:42", "Kayra");

        cache.get("user:42");   // hit
        cache.get("user:99");   // miss (never put)
        cache.get("user:42");   // hit

        System.out.println(cache.hitCount());  // 2
        System.out.println(cache.missCount()); // 1
        System.out.println(cache.hitRate());   // 0.6666666666666666
    }
}
```

- `hitCount()` / `missCount()` are incremented by every call to `get(key)`: a hit
  is a `get` that finds a live, non-expired entry; a miss is a `get` that finds
  nothing (absent, or present but TTL-expired).
- `hitRate()` is `hitCount / (hitCount + missCount)`, returning `0.0` before the
  first `get` call rather than dividing by zero.
- Like Guava's `CacheStats`, these counters are cumulative for the lifetime of the
  cache instance and are **not** reset by `put` or `clear()` - they measure how
  well the cache has been performing overall, independent of what its current
  contents happen to be.

## API

### `LruCache<K, V>`

| Method | Description |
| --- | --- |
| `LruCache(int capacity)` | Creates a cache with the given max capacity and no default TTL. |
| `LruCache(int capacity, long defaultTtlMillis)` | Creates a cache with a max capacity and a default TTL (ms) applied by `put(key, value)`. `defaultTtlMillis <= 0` means entries never expire by default. |
| `V get(K key)` | Returns the value for `key`, or `null` if absent or expired. A successful lookup marks the entry as most-recently-used. |
| `void put(K key, V value)` | Inserts/updates `key` using the cache's default TTL. Evicts the least-recently-used entry if the cache is full and `key` is new. |
| `void put(K key, V value, long ttlMillis)` | Inserts/updates `key` with an explicit TTL override for this entry only. `ttlMillis <= 0` means this entry never expires. |
| `int size()` | Current number of entries held (including any expired-but-not-yet-evicted entries). |
| `void clear()` | Removes all entries. Does not reset hit/miss statistics. |
| `long hitCount()` | Lifetime number of `get` calls that found a live entry. |
| `long missCount()` | Lifetime number of `get` calls that found nothing (absent or expired). |
| `double hitRate()` | `hitCount / (hitCount + missCount)`; `0.0` if `get` has never been called. |

### Behavior notes

- Eviction is genuinely LRU: both `get` and `put` on an existing key refresh that entry's recency.
- TTL and LRU are independent: an expired entry is treated as absent by `get` even if the LRU structure hasn't evicted it yet (e.g. because the cache isn't full).
- All public methods are safe to call concurrently from multiple threads (guarded by a single internal `ReentrantLock`).
- Hit/miss statistics are cumulative for the life of the cache instance and are not reset by `put` or `clear()` - see [Cache Statistics](#cache-statistics).

## License

MIT — see [LICENSE](LICENSE).
