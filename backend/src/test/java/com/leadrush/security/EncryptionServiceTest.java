package com.leadrush.security;

import com.leadrush.config.LeadRushProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EncryptionService.
 *
 * JUNIT 5 CONCEPTS:
 *   @Test           — this method is a test
 *   @BeforeEach     — runs before every test (setup)
 *   assertEquals(expected, actual) — check equality
 *   assertNotNull / assertNull
 *   assertThrows    — check that code throws an exception
 *
 * Unit tests should be FAST (no DB, no Spring context).
 * We manually construct the service with a fake LeadRushProperties.
 */
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        LeadRushProperties properties = new LeadRushProperties();
        properties.setEncryptionKey("test-encryption-key-for-unit-tests-only");

        encryptionService = new EncryptionService(properties);
        encryptionService.init();
    }

    @Test
    void encryptDecryptRoundtrip() {
        String plaintext = "my-secret-smtp-password-123";

        String ciphertext = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(ciphertext);

        assertEquals(plaintext, decrypted, "Decrypted value must match original plaintext");
    }

    @Test
    void encryptProducesDifferentOutputEachCall() {
        // AES-GCM uses a random IV per encryption, so the same plaintext
        // should encrypt to DIFFERENT ciphertexts each time
        String plaintext = "hello";

        String first = encryptionService.encrypt(plaintext);
        String second = encryptionService.encrypt(plaintext);

        assertNotEquals(first, second,
                "AES-GCM should produce different ciphertext each call (fresh IV)");

        // But both should decrypt back to the same plaintext
        assertEquals(plaintext, encryptionService.decrypt(first));
        assertEquals(plaintext, encryptionService.decrypt(second));
    }

    @Test
    void encryptNullReturnsNull() {
        assertNull(encryptionService.encrypt(null));
    }

    @Test
    void decryptNullReturnsNull() {
        assertNull(encryptionService.decrypt(null));
    }

    @Test
    void decryptTamperedCiphertextThrows() {
        String ciphertext = encryptionService.encrypt("hello");
        // Flip one character — GCM auth tag will catch this
        String tampered = (ciphertext.charAt(0) == 'A' ? 'B' : 'A') + ciphertext.substring(1);

        assertThrows(RuntimeException.class,
                () -> encryptionService.decrypt(tampered),
                "Tampered ciphertext must fail decryption (GCM auth)");
    }
}
