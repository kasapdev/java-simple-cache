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
| `void clear()` | Removes all entries. |

### Behavior notes

- Eviction is genuinely LRU: both `get` and `put` on an existing key refresh that entry's recency.
- TTL and LRU are independent: an expired entry is treated as absent by `get` even if the LRU structure hasn't evicted it yet (e.g. because the cache isn't full).
- All public methods are safe to call concurrently from multiple threads (guarded by a single internal `ReentrantLock`).

## License

MIT — see [LICENSE](LICENSE).
