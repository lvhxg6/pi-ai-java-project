package com.pi.chat.util;

import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for API Key encryption round-trip.
 *
 * <p>Feature: settings-model-brands, Property 4: API Key 加密 Round-Trip
 *
 * <p>Validates: Requirements 2.2, 10.2
 *
 * <p>For any non-empty API Key string, encrypt() then decrypt() should return
 * the original string.
 */
class ApiKeyEncryptionPropertyTest {

    private static final String ENCRYPTION_KEY = "test-property-encryption-key-2025";

    // ========== Property 4: encrypt → decrypt round-trip ==========

    /**
     * Validates: Requirements 2.2, 10.2
     *
     * For any non-empty API Key string, encrypting and then decrypting
     * must yield the original plaintext.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 4: API Key 加密 Round-Trip")
    void encryptThenDecryptReturnsOriginal(@ForAll("apiKeys") String apiKey) {
        ApiKeyEncryption encryption = new ApiKeyEncryption(ENCRYPTION_KEY);

        String encrypted = encryption.encrypt(apiKey);
        String decrypted = encryption.decrypt(encrypted);

        assertThat(decrypted)
            .as("decrypt(encrypt('%s')) should equal the original", apiKey)
            .isEqualTo(apiKey);
    }

    /**
     * Validates: Requirements 2.2, 10.2
     *
     * Encrypted output must carry the "encrypted:" prefix so that
     * isEncrypted() recognises it.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 4: API Key 加密 Round-Trip")
    void encryptedOutputHasPrefix(@ForAll("apiKeys") String apiKey) {
        ApiKeyEncryption encryption = new ApiKeyEncryption(ENCRYPTION_KEY);

        String encrypted = encryption.encrypt(apiKey);

        assertThat(encrypted)
            .as("encrypted form should start with 'encrypted:'")
            .startsWith("encrypted:");
        assertThat(ApiKeyEncryption.isEncrypted(encrypted))
            .as("isEncrypted should return true for encrypted output")
            .isTrue();
    }

    /**
     * Validates: Requirements 2.2, 10.2
     *
     * Double-encrypting an already encrypted key must be idempotent —
     * encrypt(encrypt(k)) == encrypt(k).
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 4: API Key 加密 Round-Trip")
    void doubleEncryptIsIdempotent(@ForAll("apiKeys") String apiKey) {
        ApiKeyEncryption encryption = new ApiKeyEncryption(ENCRYPTION_KEY);

        String encrypted = encryption.encrypt(apiKey);
        String doubleEncrypted = encryption.encrypt(encrypted);

        assertThat(doubleEncrypted)
            .as("encrypt(encrypt(key)) should equal encrypt(key)")
            .isEqualTo(encrypted);
    }

    /**
     * Validates: Requirements 2.2, 10.2
     *
     * Round-trip must work with API keys containing special characters,
     * unicode, and whitespace.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 4: API Key 加密 Round-Trip")
    void roundTripWithSpecialChars(@ForAll("specialApiKeys") String apiKey) {
        ApiKeyEncryption encryption = new ApiKeyEncryption(ENCRYPTION_KEY);

        String encrypted = encryption.encrypt(apiKey);
        String decrypted = encryption.decrypt(encrypted);

        assertThat(decrypted)
            .as("round-trip should preserve special chars in '%s'", apiKey)
            .isEqualTo(apiKey);
    }

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<String> apiKeys() {
        return Arbitraries.strings()
            .ofMinLength(1)
            .ofMaxLength(200)
            .withCharRange('!', '~')       // printable ASCII
            .withChars('中', '文', '日', '本', '語', '한', '국', '어')  // unicode samples
            .filter(s -> !s.startsWith("encrypted:"));
    }

    @Provide
    Arbitrary<String> specialApiKeys() {
        return Arbitraries.of(
            "sk-ant-api03-!@#$%^&*()",
            "key with spaces and\ttabs",
            "中文密钥-测试",
            "日本語キー",
            "한국어키",
            "emoji-key-🔑🔐",
            "newline\nkey",
            "mixed-ÄÖÜ-àéî-key",
            "very-long-" + "x".repeat(180),
            "special=+/base64chars=="
        );
    }
}
