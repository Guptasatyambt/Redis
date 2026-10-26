package com.example.redis.store;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * In-memory key-value store with optional expiry per key.
 * A plain HashMap is safe because the event loop is single-threaded.
 *
 * Expired keys are removed in two ways, like real Redis:
 * - lazily, when someone tries to read them (get)
 * - periodically, by the server calling removeExpired()
 */
public class DataStore {

    private static final long NO_EXPIRY = -1;

    private record Entry(String value, long expiresAt) {
        boolean isExpired(long now) {
            return expiresAt != NO_EXPIRY && now >= expiresAt;
        }
    }

    private final Map<String, Entry> data = new HashMap<>();
    // Current time in milliseconds; replaceable so tests don't have to sleep
    private final LongSupplier clock;

    public DataStore() {
        this(System::currentTimeMillis);
    }

    public DataStore(LongSupplier clock) {
        this.clock = clock;
    }

    public void set(String key, String value) {
        data.put(key, new Entry(value, NO_EXPIRY));
    }

    public void set(String key, String value, long ttlMillis) {
        long now = clock.getAsLong();
        // Avoid overflow for huge TTLs: treat them as "expires at the end of time"
        long expiresAt = ttlMillis > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + ttlMillis;
        data.put(key, new Entry(value, expiresAt));
    }

    // Returns the value, or null if the key doesn't exist or has expired
    public String get(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(clock.getAsLong())) {
            data.remove(key);
            return null;
        }
        return entry.value();
    }

    public void removeExpired() {
        long now = clock.getAsLong();
        data.values().removeIf(entry -> entry.isExpired(now));
    }

    public int size() {
        return data.size();
    }

}
