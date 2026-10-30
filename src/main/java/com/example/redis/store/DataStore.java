package com.example.redis.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * In-memory key-value store where every key expires.
 * Keys set without an explicit TTL expire after DEFAULT_TTL_MILLIS (10 minutes).
 * A plain HashMap is safe because the event loop is single-threaded.
 *
 * Expired keys are removed in two ways, like real Redis:
 * - lazily, when someone tries to read them (get)
 * - periodically, by the server calling removeExpired(), which checks a small random sample
 *   of keys and only scans the whole store when enough of the sample has expired
 */
public class DataStore {

    public static final long DEFAULT_TTL_MILLIS = 10 * 60 * 1000;
    // How many random keys removeExpired() checks
    public static final int EXPIRY_SAMPLE_SIZE = 20;
    // If at least this fraction of the sample has expired, removeExpired() scans every key
    public static final double FULL_SCAN_THRESHOLD = 0.3;

    private static final class Entry {
        String value;
        long expiresAt;
        // Position of this key in the keys list
        int index;

        Entry(String value, long expiresAt, int index) {
            this.value = value;
            this.expiresAt = expiresAt;
            this.index = index;
        }

        boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    private final Map<String, Entry> data = new HashMap<>();
    // Every key, so removeExpired() can pick random keys in O(1); a HashMap can't do that
    private final List<String> keys = new ArrayList<>();
    // Current time in milliseconds; replaceable so tests don't have to sleep
    private final LongSupplier clock;
    private final Random random;

    public DataStore() {
        this(System::currentTimeMillis);
    }

    public DataStore(LongSupplier clock) {
        this(clock, new Random());
    }

    public DataStore(LongSupplier clock, Random random) {
        this.clock = clock;
        this.random = random;
    }

    public void set(String key, String value) {
        set(key, value, DEFAULT_TTL_MILLIS);
    }

    public void set(String key, String value, long ttlMillis) {
        long now = clock.getAsLong();
        // Avoid overflow for huge TTLs: treat them as "expires at the end of time"
        long expiresAt = ttlMillis > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + ttlMillis;
        Entry entry = data.get(key);
        if (entry == null) {
            data.put(key, new Entry(value, expiresAt, keys.size()));
            keys.add(key);
        } else {
            entry.value = value;
            entry.expiresAt = expiresAt;
        }
    }

    // Returns the value, or null if the key doesn't exist or has expired
    public String get(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(clock.getAsLong())) {
            remove(key);
            return null;
        }
        return entry.value;
    }

    /**
     * Checks EXPIRY_SAMPLE_SIZE random keys and removes the expired ones.
     * If FULL_SCAN_THRESHOLD or more of the sample had expired, many more expired keys are probably
     * left, so every key is checked. Otherwise the scan is skipped, keeping each call cheap.
     */
    public void removeExpired() {
        if (keys.isEmpty()) {
            return;
        }
        long now = clock.getAsLong();
        int sampled = Math.min(EXPIRY_SAMPLE_SIZE, keys.size());
        int expired = 0;
        for (int i = 0; i < sampled; i++) {
            String key = keys.get(random.nextInt(keys.size()));
            if (data.get(key).isExpired(now)) {
                remove(key);
                expired++;
            }
            if (keys.isEmpty()) {
                break;
            }
        }
        if (expired >= sampled * FULL_SCAN_THRESHOLD) {
            removeAllExpired(now);
        }
    }

    // Checks every key, without sampling first
    public void removeAllExpired() {
        removeAllExpired(clock.getAsLong());
    }

    private void removeAllExpired(long now) {
        // Walk backwards: remove() moves the last key into the freed slot, and that key was already checked
        for (int i = keys.size() - 1; i >= 0; i--) {
            String key = keys.get(i);
            if (data.get(key).isExpired(now)) {
                remove(key);
            }
        }
    }

    private void remove(String key) {
        Entry entry = data.remove(key);
        // Move the last key into the removed key's slot so the list has no gaps
        String lastKey = keys.remove(keys.size() - 1);
        if (!lastKey.equals(key)) {
            keys.set(entry.index, lastKey);
            data.get(lastKey).index = entry.index;
        }
    }

    public int size() {
        return data.size();
    }

}
