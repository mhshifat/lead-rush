package com.leadrush;

import org.junit.jupiter.api.Test;

/**
 * Smoke test — just verifies the test framework is working.
 *
 * The original scaffold used @SpringBootTest which starts the full Spring context.
 * We skip that here because it requires a running PostgreSQL + Redis.
 * Service-level unit tests live in their own files (EncryptionServiceTest, etc.).
 *
 * Integration tests that need the full context should use @SpringBootTest
 * with Testcontainers for a real DB.
 */
class LeadRushApplicationTests {

    @Test
    void smokeTest() {
        // Simply asserts the test framework runs. Real tests are in other files.
    }
}
