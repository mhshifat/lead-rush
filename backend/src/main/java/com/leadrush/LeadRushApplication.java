package com.leadrush;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code exclude = RedisRepositoriesAutoConfiguration.class} — we use
 * RedisTemplate directly from CacheService, never @RedisHash-annotated
 * Repository interfaces. Excluding this autoconfig stops Spring Data Redis
 * from scanning every JPA repository and logging "Could not safely identify
 * store assignment…" on boot (one line per repo × 40 repos = log flood).
 */
@SpringBootApplication(exclude = { RedisRepositoriesAutoConfiguration.class })
@EnableScheduling
public class LeadRushApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadRushApplication.class, args);
    }
}
