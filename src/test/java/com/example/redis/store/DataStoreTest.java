package com.example.redis.store;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataStoreTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final DataStore store = new DataStore(now::get);

    @Test
    void getReturnsNullForMissingKey() {
        assertNull(store.get("missing"));
    }

    @Test
    void keyWithoutExpiryNeverExpires() {
        store.set("name", "tushar");
        now.addAndGet(1_000_000);
        assertEquals("tushar", store.get("name"));
    }

    @Test
    void keyExpiresAfterTtl() {
        store.set("name", "tushar", 100);

        now.addAndGet(99);
        assertEquals("tushar", store.get("name"));

        now.addAndGet(1);
        assertNull(store.get("name"));
    }

    @Test
    void plainSetClearsPreviousExpiry() {
        store.set("name", "tushar", 100);
        store.set("name", "tushar");
        now.addAndGet(200);
        assertEquals("tushar", store.get("name"));
    }

    @Test
    void removeExpiredDeletesOnlyExpiredKeys() {
        store.set("short", "a", 100);
        store.set("long", "b", 1_000);
        store.set("forever", "c");

        now.addAndGet(500);
        store.removeExpired();

        assertEquals(2, store.size());
        assertNull(store.get("short"));
        assertEquals("b", store.get("long"));
    }

    @Test
    void hugeTtlDoesNotOverflow() {
        store.set("name", "tushar", Long.MAX_VALUE);
        assertEquals("tushar", store.get("name"));
    }

}
