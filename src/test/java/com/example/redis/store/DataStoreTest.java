package com.example.redis.store;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataStoreTest {

    private final AtomicLong now = new AtomicLong(1_000);
    // Fixed seed so the random sampling in removeExpired() picks the same keys every run
    private final DataStore store = new DataStore(now::get, new Random(42));

    @Test
    void getReturnsNullForMissingKey() {
        assertNull(store.get("missing"));
    }

    @Test
    void keyWithoutTtlExpiresAfterDefault() {
        store.set("name", "satyam");

        now.addAndGet(DataStore.DEFAULT_TTL_MILLIS - 1);
        assertEquals("satyam", store.get("name"));

        now.addAndGet(1);
        assertNull(store.get("name"));
    }

    @Test
    void keyExpiresAfterTtl() {
        store.set("name", "satyam", 100);

        now.addAndGet(99);
        assertEquals("satyam", store.get("name"));

        now.addAndGet(1);
        assertNull(store.get("name"));
    }

    @Test
    void plainSetReplacesPreviousExpiryWithDefault() {
        store.set("name", "satyam", 100);
        store.set("name", "satyam");
        now.addAndGet(200);
        assertEquals("satyam", store.get("name"));
    }

    @Test
    void removeExpiredScansEverythingWhenManySampledKeysExpired() {
        addKeys("short", 60, 100);
        addKeys("long", 40, 1_000);

        now.addAndGet(500);
        store.removeExpired();

        assertEquals(40, store.size());
        assertEquals("v", store.get("long0"));
    }

    @Test
    void removeExpiredSkipsFullScanWhenFewSampledKeysExpired() {
        addKeys("short", 5, 100);
        addKeys("long", 95, 1_000);

        now.addAndGet(500);
        store.removeExpired();

        // Only expired keys that happened to be sampled are removed, so some are left for later
        assertTrue(store.size() > 95);
    }

    @Test
    void removeExpiredClearsSmallStoreWhenAllExpired() {
        store.set("a", "1", 100);
        store.set("b", "2", 100);

        now.addAndGet(500);
        store.removeExpired();

        assertEquals(0, store.size());
    }

    @Test
    void removeExpiredNeverDeletesLiveKeys() {
        addKeys("long", 50, 1_000);

        now.addAndGet(500);
        store.removeExpired();

        assertEquals(50, store.size());
    }

    @Test
    void removeAllExpiredDeletesEveryExpiredKey() {
        addKeys("short", 5, 100);
        addKeys("long", 95, 1_000);

        now.addAndGet(500);
        store.removeAllExpired();

        assertEquals(95, store.size());
    }

    @Test
    void getStillWorksAfterKeysAreRemoved() {
        store.set("a", "1", 100);
        store.set("b", "2", 1_000);
        store.set("c", "3", 100);

        now.addAndGet(500);
        assertNull(store.get("a"));
        assertNull(store.get("c"));
        store.set("a", "4");

        assertEquals("2", store.get("b"));
        assertEquals("4", store.get("a"));
        assertEquals(2, store.size());
    }

    private void addKeys(String prefix, int count, long ttlMillis) {
        for (int i = 0; i < count; i++) {
            store.set(prefix + i, "v", ttlMillis);
        }
    }

    @Test
    void hugeTtlDoesNotOverflow() {
        store.set("name", "satyam", Long.MAX_VALUE);
        assertEquals("satyam", store.get("name"));
    }

}
