package dev.kasapdev.simplecache;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class LruCacheTest {

    public static void main(String[] args) throws Exception {
        testBasicPutGet();
        testSizeAndClear();
        testLruEvictionOrder();
        testAccessRefreshesRecency();
        testTtlExpiryOnGet();
        testPerPutTtlOverridesDefault();
        testNegativeOrZeroTtlMeansNoExpiry();
        testCapacityOneAlwaysEvictsPrevious();
        testConcurrentAccessDoesNotCorruptState();
        testInvalidCapacityRejected();
        testSizeCountsExpiredButNotYetEvictedEntries();
        testRePuttingExistingKeyDoesNotEvictOthers();
        testStatsAreZeroBeforeAnyGet();
        testHitAndMissCountsWithExactExpectedValues();
        TestKit.finish();
    }

    private static void testStatsAreZeroBeforeAnyGet() {
        LruCache<String, String> cache = new LruCache<>(3);
        TestKit.check("hitCount is 0 before any get", cache.hitCount() == 0);
        TestKit.check("missCount is 0 before any get", cache.missCount() == 0);
        TestKit.check("hitRate is 0.0 before any get (no divide-by-zero)", cache.hitRate() == 0.0);
        cache.put("a", "A");
        TestKit.check("put alone does not affect hitCount", cache.hitCount() == 0);
        TestKit.check("put alone does not affect missCount", cache.missCount() == 0);
    }

    private static void testHitAndMissCountsWithExactExpectedValues() {
        // Capacity 2, no TTL. Hand-tracked sequence of gets, mixing hits, misses,
        // and a miss caused by capacity eviction.
        LruCache<String, String> cache = new LruCache<>(2);
        cache.put("a", "A");
        cache.put("b", "B");

        cache.get("a");        // 1: hit  (a present)                    -> hits=1 misses=0
        cache.get("b");        // 2: hit  (b present)                    -> hits=2 misses=0
        cache.get("missing");  // 3: miss (never put)                    -> hits=2 misses=1

        // Capacity is 2 and recency order right now (oldest -> newest) is a, b
        // (both gets above refreshed recency in that order). Inserting "c" evicts "a".
        cache.put("c", "C");

        cache.get("a");        // 4: miss (evicted by capacity)          -> hits=2 misses=2
        cache.get("b");        // 5: hit  (survived eviction)            -> hits=3 misses=2
        cache.get("c");        // 6: hit  (just inserted)                -> hits=4 misses=2
        cache.get("missing");  // 7: miss (still never put)              -> hits=4 misses=3

        // Overwriting an existing key is not a get, so it must not move the counters.
        cache.put("b", "B-updated");
        cache.get("b");        // 8: hit  (updated value, still present) -> hits=5 misses=3

        TestKit.check("hitCount matches hand-calculated expected value", cache.hitCount() == 5);
        TestKit.check("missCount matches hand-calculated expected value", cache.missCount() == 3);
        // hitRate = hits / (hits + misses) = 5 / 8 = 0.625
        TestKit.check("hitRate matches hand-calculated expected value", cache.hitRate() == 0.625);

        // clear() must not reset lifetime stats (documented, Guava-style behavior).
        cache.clear();
        TestKit.check("hitCount is unchanged by clear()", cache.hitCount() == 5);
        TestKit.check("missCount is unchanged by clear()", cache.missCount() == 3);
        TestKit.check("hitRate is unchanged by clear()", cache.hitRate() == 0.625);

        // A get() after clear() on a now-absent key is a miss, and does update stats.
        cache.get("b");        // 9: miss (cleared)                      -> hits=5 misses=4
        TestKit.check("missCount increments for a get() after clear()", cache.missCount() == 4);
        TestKit.check("hitRate reflects the post-clear miss: 5 / 9", cache.hitRate() == 5.0 / 9.0);
    }

    private static void testInvalidCapacityRejected() {
        boolean threwZero = false;
        try {
            new LruCache<String, String>(0);
        } catch (IllegalArgumentException e) {
            threwZero = true;
        }
        TestKit.check("capacity 0 is rejected", threwZero);

        boolean threwNegative = false;
        try {
            new LruCache<String, String>(-1);
        } catch (IllegalArgumentException e) {
            threwNegative = true;
        }
        TestKit.check("negative capacity is rejected", threwNegative);
    }

    private static void testSizeCountsExpiredButNotYetEvictedEntries() throws InterruptedException {
        // Per the documented contract, size() counts entries that have expired by TTL but
        // have not yet been physically purged (purging only happens lazily inside get()).
        LruCache<String, String> cache = new LruCache<>(10, 20L);
        cache.put("soon-expired", "value");
        TestKit.check("size reflects the entry right after put", cache.size() == 1);
        Thread.sleep(60);
        TestKit.check("size still counts the expired-but-not-yet-evicted entry", cache.size() == 1);
        TestKit.check("get() purges the expired entry and reports it absent", cache.get("soon-expired") == null);
        TestKit.check("size reflects the purge after get() evicted the expired entry", cache.size() == 0);
    }

    private static void testRePuttingExistingKeyDoesNotEvictOthers() {
        LruCache<String, String> cache = new LruCache<>(3);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.put("c", "C");
        // Overwriting an already-present key must not grow past capacity or evict anyone.
        cache.put("a", "A-updated");
        TestKit.check("size is unchanged after re-putting an existing key", cache.size() == 3);
        TestKit.check("re-put key has the updated value", "A-updated".equals(cache.get("a")));
        TestKit.check("other keys are untouched by re-putting an existing key (b)", "B".equals(cache.get("b")));
        TestKit.check("other keys are untouched by re-putting an existing key (c)", "C".equals(cache.get("c")));
    }

    private static void testBasicPutGet() {
        LruCache<String, Integer> cache = new LruCache<>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        TestKit.check("get returns stored value", cache.get("a") == 1);
        TestKit.check("get returns stored value for second key", cache.get("b") == 2);
        TestKit.check("get returns null for missing key", cache.get("missing") == null);
    }

    private static void testSizeAndClear() {
        LruCache<String, Integer> cache = new LruCache<>(5);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        TestKit.check("size reflects number of entries", cache.size() == 3);
        cache.clear();
        TestKit.check("size is zero after clear", cache.size() == 0);
        TestKit.check("get returns null after clear", cache.get("a") == null);
    }

    private static void testLruEvictionOrder() {
        // Capacity 3: fill it, then insert a 4th distinct key. The least-recently-used
        // entry (the one never touched again after insertion) must be evicted.
        LruCache<String, String> cache = new LruCache<>(3);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.put("c", "C");
        // No further access to "a" before overflow -> "a" is least-recently-used.
        cache.put("d", "D"); // triggers eviction
        TestKit.check("cache size stays at capacity after overflow", cache.size() == 3);
        TestKit.check("least-recently-used entry (a) was evicted", cache.get("a") == null);
        TestKit.check("b survives eviction", "B".equals(cache.get("b")));
        TestKit.check("c survives eviction", "C".equals(cache.get("c")));
        TestKit.check("newly inserted d is present", "D".equals(cache.get("d")));
    }

    private static void testAccessRefreshesRecency() {
        // Capacity 3: fill it, then GET the oldest entry to refresh its recency,
        // then insert enough new entries to force one eviction. The refreshed
        // entry must survive while a truly-unused entry gets evicted.
        LruCache<String, String> cache = new LruCache<>(3);
        cache.put("old", "OLD");
        cache.put("mid", "MID");
        cache.put("unused", "UNUSED");
        // Refresh "old" by reading it - it becomes most-recently-used.
        TestKit.check("refresh read returns correct value", "OLD".equals(cache.get("old")));
        // Insert a new key; capacity is 3, so exactly one entry must be evicted.
        // Order of recency before this put (oldest -> newest): mid, unused, old.
        // So "mid" is now the least-recently-used and should be evicted.
        cache.put("new", "NEW");
        TestKit.check("refreshed entry (old) survives eviction", "OLD".equals(cache.get("old")));
        TestKit.check("truly-unused entry (mid) is evicted", cache.get("mid") == null);
        TestKit.check("unused-but-newer entry (unused) survives", "UNUSED".equals(cache.get("unused")));
        TestKit.check("newly inserted entry (new) is present", "NEW".equals(cache.get("new")));
        TestKit.check("size stays at capacity", cache.size() == 3);
    }

    private static void testTtlExpiryOnGet() throws InterruptedException {
        LruCache<String, String> cache = new LruCache<>(10, 30L); // default TTL 30ms
        cache.put("short", "value");
        TestKit.check("value present immediately after put", "value".equals(cache.get("short")));
        Thread.sleep(80);
        TestKit.check("expired entry treated as absent on get", cache.get("short") == null);
    }

    private static void testPerPutTtlOverridesDefault() throws InterruptedException {
        // Default TTL is very long, but this one entry gets a short explicit TTL.
        LruCache<String, String> cache = new LruCache<>(10, 10_000L);
        cache.put("longLived", "keepsForAWhile"); // uses default (long) TTL
        cache.put("shortLived", "goesAwaySoon", 30L); // explicit short TTL override
        Thread.sleep(80);
        TestKit.check("entry with short per-put TTL expires", cache.get("shortLived") == null);
        TestKit.check("entry using long default TTL still present", "keepsForAWhile".equals(cache.get("longLived")));
    }

    private static void testNegativeOrZeroTtlMeansNoExpiry() throws InterruptedException {
        LruCache<String, String> cache = new LruCache<>(10, 20L); // short default
        cache.put("forever", "value", 0L); // explicit "no expiry" override
        Thread.sleep(60);
        TestKit.check("ttlMillis <= 0 means the entry never expires", "value".equals(cache.get("forever")));
    }

    private static void testCapacityOneAlwaysEvictsPrevious() {
        LruCache<String, Integer> cache = new LruCache<>(1);
        cache.put("first", 1);
        cache.put("second", 2);
        TestKit.check("capacity-1 cache evicts previous entry on new insert", cache.get("first") == null);
        TestKit.check("capacity-1 cache retains the newest entry", cache.get("second") == 2);
        TestKit.check("capacity-1 cache never exceeds size 1", cache.size() == 1);
    }

    private static void testConcurrentAccessDoesNotCorruptState() throws Exception {
        final LruCache<Integer, Integer> cache = new LruCache<>(50);
        int threadCount = 8;
        int opsPerThread = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        final boolean[] sawException = {false};
        for (int t = 0; t < threadCount; t++) {
            final int base = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = (base * 1000 + i) % 200;
                        cache.put(key, key);
                        cache.get(key);
                        if (i % 50 == 0) {
                            cache.size();
                        }
                    }
                } catch (Exception e) {
                    sawException[0] = true;
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        TestKit.check("concurrent access from multiple threads raises no exceptions", !sawException[0]);
        TestKit.check("cache never exceeds configured capacity under concurrent load", cache.size() <= 50);
    }
}
