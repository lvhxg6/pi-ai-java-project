package com.pi.chat.service;

import com.pi.chat.dto.UpdateBrandRequest;
import com.pi.chat.model.BrandDefinitions;
import com.pi.chat.repository.ProviderConfigRepository;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for API Key validation relaxation.
 *
 * <p>Feature: settings-model-brands, Property 22: API Key 校验放宽
 *
 * <p>Validates: Requirements 8.5
 *
 * <p>For any non-empty string of length >= 10, API Key format validation
 * should pass; for any string of length < 10, validation should fail.
 * Validation is tested through BrandService.updateBrand().
 */
class ApiKeyValidationPropertyTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-1234";
    private static final int MIN_API_KEY_LENGTH = 10;

    private BrandService createBrandService() {
        try {
            Path tempDir = Files.createTempDirectory("api-key-validation-test");
            tempDir.toFile().deleteOnExit();
            Path configFile = tempDir.resolve("providers.json");
            ProviderConfigRepository repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            return new BrandService(repository, null, null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp dir for test", e);
        }
    }

    // ========== Property 22: Keys >= 10 chars should be accepted ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 22: API Key 校验放宽")
    void apiKeyWithLengthAtLeast10ShouldBeAccepted(
            @ForAll("validApiKey") String apiKey) {

        BrandService service = createBrandService();
        String brandId = BrandDefinitions.CLAUDE.id();

        // Use a built-in brand so baseUrl is not required
        UpdateBrandRequest request = new UpdateBrandRequest(apiKey, null, null);

        // Should not throw - key is long enough
        assertThatCode(() -> service.updateBrand(brandId, request))
            .as("API key of length %d should be accepted", apiKey.length())
            .doesNotThrowAnyException();
    }

    // ========== Property 22: Keys < 10 chars should be rejected ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 22: API Key 校验放宽")
    void apiKeyWithLengthLessThan10ShouldBeRejected(
            @ForAll("shortApiKey") String apiKey) {

        BrandService service = createBrandService();
        String brandId = BrandDefinitions.CLAUDE.id();

        UpdateBrandRequest request = new UpdateBrandRequest(apiKey, null, null);

        // Should throw IllegalArgumentException for short keys
        assertThatThrownBy(() -> service.updateBrand(brandId, request))
            .as("API key of length %d should be rejected", apiKey.length())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("" + MIN_API_KEY_LENGTH);
    }

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<String> validApiKey() {
        // Generate non-empty strings of length >= 10
        return Arbitraries.strings()
            .all()
            .ofMinLength(MIN_API_KEY_LENGTH)
            .ofMaxLength(200)
            .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<String> shortApiKey() {
        // Generate non-empty, non-blank strings of length 1..9
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(MIN_API_KEY_LENGTH - 1);
    }
}
