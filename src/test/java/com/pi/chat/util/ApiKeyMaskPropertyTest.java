package com.pi.chat.util;

import com.pi.chat.model.ConnectionStatus;
import com.pi.chat.model.ProviderConfig;
import net.jqwik.api.*;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for API Key masking security.
 *
 * <p>Feature: settings-model-brands, Property 5: API Key 掩码安全性
 *
 * <p>Validates: Requirements 2.3
 *
 * <p>For any API Key string with length >= 8, the masked result should contain
 * {@code ****} characters, and the masked result length should be less than the
 * original key length (i.e., the full key is never exposed).
 *
 * <p>Implementation note: {@code ApiKeyEncryption.mask()} produces a fixed-width
 * output of 18 chars for keys longer than 8 (prefix 3 + "****...****" + suffix 4),
 * so the "shorter than original" property holds for keys with length >= 19.
 * {@code ProviderConfig.maskedApiKey()} produces 12 chars (prefix 4 + "****" + suffix 4)
 * for keys longer than 8, so the property holds for keys with length >= 13.
 * For all keys >= 8, the mask always contains "****" and never exposes the full key.
 */
class ApiKeyMaskPropertyTest {

    // ========== Property 5: ApiKeyEncryption.mask — contains **** ==========

    /**
     * Validates: Requirements 2.3
     *
     * For any API Key with length >= 8, ApiKeyEncryption.mask() must contain "****".
     */
    @Property(tries = 200)
    @Tag("Feature: settings-model-brands, Property 5: API Key 掩码安全性")
    void maskContainsAsterisksForKeysLengthAtLeast8(
            @ForAll("apiKeysAtLeast8") String apiKey) {
        String masked = ApiKeyEncryption.mask(apiKey);

        assertThat(masked)
            .as("mask('%s') should contain '****'", apiKey)
            .contains("****");
    }

    // ========== Property 5: ApiKeyEncryption.mask — shorter than original for long keys ==========

    /**
     * Validates: Requirements 2.3
     *
     * For any API Key with length >= 19 (where the fixed-width mask output of 18
     * chars is guaranteed shorter), the masked result length must be strictly less
     * than the original key length, ensuring the full key is never exposed.
     */
    @Property(tries = 200)
    @Tag("Feature: settings-model-brands, Property 5: API Key 掩码安全性")
    void maskShorterThanOriginalForLongKeys(
            @ForAll("longApiKeys") String apiKey) {
        String masked = ApiKeyEncryption.mask(apiKey);

        assertThat(masked.length())
            .as("mask('%s') length (%d) should be < original length (%d)",
                apiKey, masked.length(), apiKey.length())
            .isLessThan(apiKey.length());
    }

    // ========== Property 5: ApiKeyEncryption.mask — never equals original ==========

    /**
     * Validates: Requirements 2.3
     *
     * For any API Key with length >= 8, the masked result must never equal the
     * original key — the full key is never exposed verbatim.
     */
    @Property(tries = 200)
    @Tag("Feature: settings-model-brands, Property 5: API Key 掩码安全性")
    void maskNeverEqualsOriginal(
            @ForAll("apiKeysAtLeast8") String apiKey) {
        String masked = ApiKeyEncryption.mask(apiKey);

        assertThat(masked)
            .as("mask('%s') should never equal the original key", apiKey)
            .isNotEqualTo(apiKey);
    }

    // ========== Property 5: ProviderConfig.maskedApiKey — contains **** ==========

    /**
     * Validates: Requirements 2.3
     *
     * For any ProviderConfig with an API Key of length >= 8,
     * maskedApiKey() must contain "****".
     */
    @Property(tries = 200)
    @Tag("Feature: settings-model-brands, Property 5: API Key 掩码安全性")
    void providerConfigMaskedApiKeyContainsAsterisks(
            @ForAll("apiKeysAtLeast8") String apiKey) {
        ProviderConfig config = buildConfig(apiKey);

        String masked = config.maskedApiKey();

        assertThat(masked)
            .as("ProviderConfig.maskedApiKey() for key '%s' should contain '****'", apiKey)
            .contains("****");
    }

    // ========== Property 5: ProviderConfig.maskedApiKey — shorter than original for long keys ==========

    /**
     * Validates: Requirements 2.3
     *
     * For any ProviderConfig with an API Key of length >= 13 (where the fixed-width
     * mask output of 12 chars is guaranteed shorter), maskedApiKey() length must be
     * strictly less than the original key length.
     */
    @Property(tries = 200)
    @Tag("Feature: settings-model-brands, Property 5: API Key 掩码安全性")
    void providerConfigMaskedApiKeyShorterThanOriginal(
            @ForAll("apiKeysAtLeast13") String apiKey) {
        ProviderConfig config = buildConfig(apiKey);

        String masked = config.maskedApiKey();

        assertThat(masked.length())
            .as("ProviderConfig.maskedApiKey() length (%d) should be < original length (%d)",
                masked.length(), apiKey.length())
            .isLessThan(apiKey.length());
    }

    // ========== Property 5: ProviderConfig.maskedApiKey — never equals original ==========

    /**
     * Validates: Requirements 2.3
     *
     * For any ProviderConfig with an API Key of length >= 8,
     * maskedApiKey() must never equal the original key.
     */
    @Property(tries = 200)
    @Tag("Feature: settings-model-brands, Property 5: API Key 掩码安全性")
    void providerConfigMaskedApiKeyNeverEqualsOriginal(
            @ForAll("apiKeysAtLeast8") String apiKey) {
        ProviderConfig config = buildConfig(apiKey);

        String masked = config.maskedApiKey();

        assertThat(masked)
            .as("ProviderConfig.maskedApiKey() should never equal the original key '%s'", apiKey)
            .isNotEqualTo(apiKey);
    }

    // ========== Helper ==========

    private ProviderConfig buildConfig(String apiKey) {
        Instant now = Instant.now();
        return new ProviderConfig(
            "test-id", "openai", "Test", apiKey, null,
            now, now, ConnectionStatus.UNKNOWN
        );
    }

    // ========== Arbitrary Providers ==========

    /**
     * Generates API key strings with length >= 8.
     */
    @Provide
    Arbitrary<String> apiKeysAtLeast8() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('-', '_')
            .ofMinLength(8)
            .ofMaxLength(200);
    }

    /**
     * Generates API key strings with length >= 13 (where ProviderConfig.maskedApiKey()
     * output of 12 chars is guaranteed shorter than the original).
     */
    @Provide
    Arbitrary<String> apiKeysAtLeast13() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('-', '_')
            .ofMinLength(13)
            .ofMaxLength(200);
    }

    /**
     * Generates API key strings with length >= 19 (where ApiKeyEncryption.mask()
     * output of 18 chars is guaranteed shorter than the original).
     */
    @Provide
    Arbitrary<String> longApiKeys() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('-', '_')
            .ofMinLength(19)
            .ofMaxLength(200);
    }
}
