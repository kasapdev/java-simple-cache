package dev.kasapdev.simplecache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe, generic LRU (Least Recently Used) cache with a fixed capacity
 * and optional per-entry time-to-live (TTL) support.
 *
 * <p>The cache evicts the least-recently-used entry when it would otherwise grow
 * beyond its configured capacity. Every read ({@link #get(Object)}) and write
 * ({@link #put(Object, Object)}) counts as a "use" and refreshes an entry's
 * recency, exactly like a classic LRU cache.
 *
 * <p>Independently of LRU eviction, entries may also expire based on a TTL
 * (time-to-live) in milliseconds. A TTL can be supplied as a cache-wide default
 * at construction time, or overridden per-entry via {@link #put(Object, Object, long)}.
 * An expired entry is treated as absent by {@link #get(Object)} even if it has
 * not yet been physically evicted from the underlying map.
 *
 * <p>All public methods are safe for concurrent use from multiple threads; a
 * single {@link ReentrantLock} guards all access to the underlying map.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
public final class LruCache<K, V> {

    /** Sentinel meaning "never expires" for an entry's TTL. */
    private static final long NO_EXPIRY = Long.MAX_VALUE;

    private final int capacity;
    private final long defaultTtlMillis;
    private final LinkedHashMap<K, Entry<V>> map;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates a cache with the given fixed capacity and no default TTL
     * (entries never expire unless a per-put TTL is supplied).
     *
     * @param capacity maximum number of entries the cache may hold; must be &gt;= 1
     */
    public LruCache(int capacity) {
        this(capacity, 0L);
    }

    /**
     * Creates a cache with the given fixed capacity and a default TTL applied
     * to every entry added via {@link #put(Object, Object)}.
     *
     * @param capacity         maximum number of entries the cache may hold; must be &gt;= 1
     * @param defaultTtlMillis default time-to-live in milliseconds for entries added via
     *                         {@link #put(Object, Object)}; a value &lt;= 0 means entries
     *                         never expire by default
     */
    public LruCache(int capacity, long defaultTtlMillis) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, got " + capacity);
        }
        this.capacity = capacity;
        this.defaultTtlMillis = defaultTtlMillis;
        // accessOrder = true turns this into a genuine LRU structure: get() and
        // put() on an existing key both move the entry to the end (most-recently-used).
        this.map = new LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
                return size() > LruCache.this.capacity;
            }
        };
    }

    /**
     * Returns the value associated with {@code key}, or {@code null} if absent
     * or expired. A successful lookup marks the entry as most-recently-used.
     */
    public V get(K key) {
        lock.lock();
        try {
            Entry<V> entry = map.get(key);
            if (entry == null) {
                return null;
            }
            if (isExpired(entry)) {
                map.remove(key);
                return null;
            }
            return entry.value;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Associates {@code value} with {@code key}, using this cache's default TTL.
     * If the cache is already at capacity and {@code key} is new, the
     * least-recently-used entry is evicted.
     */
    public void put(K key, V value) {
        put(key, value, defaultTtlMillis);
    }

    /**
     * Associates {@code value} with {@code key}, using an explicit TTL in
     * milliseconds for this entry only (overriding the cache's default TTL).
     * A {@code ttlMillis} value &lt;= 0 means this entry never expires.
     * If the cache is already at capacity and {@code key} is new, the
     * least-recently-used entry is evicted.
     */
    public void put(K key, V value, long ttlMillis) {
        long expiresAt = ttlMillis <= 0 ? NO_EXPIRY : System.currentTimeMillis() + ttlMillis;
        lock.lock();
        try {
            map.put(key, new Entry<>(value, expiresAt));
        } finally {
            lock.unlock();
        }
    }

    /** Returns the current number of entries held by the cache (including any not-yet-evicted expired entries). */
    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    /** Removes all entries from the cache. */
    public void clear() {
        lock.lock();
        try {
            map.clear();
        } finally {
            lock.unlock();
        }
    }

    private boolean isExpired(Entry<V> entry) {
        return entry.expiresAt != NO_EXPIRY && System.currentTimeMillis() >= entry.expiresAt;
    }

    private static final class Entry<V> {
        final V value;
        final long expiresAt;

        Entry(V value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
