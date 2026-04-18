package com.leadrush.common.adapter;

import java.time.Duration;
import java.util.Optional;

/** Cache adapter (default impl: Redis). */
public interface CacheAdapter {

    Optional<String> get(String key);

    void set(String key, String value, Duration ttl);

    void delete(String key);

    boolean exists(String key);

    /** Atomic increment used for rate limiting. Returns the new count. */
    long increment(String key, Duration ttlIfNew);
}
