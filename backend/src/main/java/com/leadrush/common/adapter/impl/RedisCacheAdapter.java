package com.leadrush.common.adapter.impl;

import com.leadrush.common.adapter.CacheAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

// Redis CacheAdapter. All ops fail-soft — Redis is cache-only, never source of truth.
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisCacheAdapter implements CacheAdapter {

    private final StringRedisTemplate redis;

    @Override
    public Optional<String> get(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.warn("Redis unavailable for GET {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis unavailable for SET {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis unavailable for DELETE {}: {}", key, e.getMessage());
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (Exception e) {
            log.warn("Redis unavailable for EXISTS {}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public long increment(String key, Duration ttlIfNew) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                // First increment sets the TTL so the counter auto-expires.
                redis.expire(key, ttlIfNew);
            }
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Redis unavailable for INCREMENT {}: {}", key, e.getMessage());
            return 0; // fail-open: don't rate-limit if Redis is down
        }
    }
}
