package com.pi.chat.service;

import com.pi.chat.dto.BrandView;
import com.pi.chat.dto.UpdateBrandRequest;
import com.pi.chat.model.BrandDefinition;
import com.pi.chat.model.BrandDefinitions;
import com.pi.chat.repository.ProviderConfigRepository;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for built-in brand API type immutability.
 *
 * <p>Feature: settings-model-brands, Property 9: 内建品牌 API 协议不可变
 *
 * <p><b>Validates: Requirements 2.7</b>
 *
 * <p>For any builtin brand, attempting to modify the {@code apiType} field
 * through {@code updateBrand()} should not change it — {@code getBrand()}
 * should still return the original protocol type defined in BrandDefinitions.
 */
class BrandApiTypeImmutablePropertyTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-1234";

    private static final List<BrandDefinition> BUILTIN_BRANDS = List.of(
        BrandDefinitions.CLAUDE,
        BrandDefinitions.CHATGPT,
        BrandDefinitions.GEMINI,
        BrandDefinitions.MISTRAL,
        BrandDefinitions.BEDROCK
    );

    private BrandService createBrandService() {
        try {
            Path tempDir = Files.createTempDirectory("brand-apitype-immutable-test");
            tempDir.toFile().deleteOnExit();
            Path configFile = tempDir.resolve("providers.json");
            ProviderConfigRepository repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            return new BrandService(repository, null, null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp dir for test", e);
        }
    }

    /**
     * Property 9: After calling updateBrand() with a valid API key on a builtin brand,
     * getBrand() should still return the original apiType from BrandDefinitions.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 9: 内建品牌 API 协议不可变")
    void builtinBrandApiTypeRemainsImmutableAfterUpdate(
            @ForAll("builtinBrand") BrandDefinition brand,
            @ForAll("apiKey") String apiKey) {

        BrandService service = createBrandService();

        String originalApiType = brand.apiType();

        // Update the builtin brand with a new API key
        UpdateBrandRequest request = new UpdateBrandRequest(apiKey, null, null);
        BrandView updated = service.updateBrand(brand.id(), request);

        // apiType in the update response must match the original
        assertThat(updated.apiType())
            .as("Built-in brand '%s' apiType should remain '%s' after update",
                brand.name(), originalApiType)
            .isEqualTo(originalApiType);

        // apiType via getBrand round-trip must also match
        BrandView retrieved = service.getBrand(brand.id());
        assertThat(retrieved.apiType())
            .as("Built-in brand '%s' apiType should remain '%s' after getBrand round-trip",
                brand.name(), originalApiType)
            .isEqualTo(originalApiType);
    }

    /**
     * Property 9 (repeated updates): Multiple successive updateBrand() calls
     * should never change the apiType of a builtin brand.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 9: 内建品牌 API 协议不可变")
    void builtinBrandApiTypeStableAcrossMultipleUpdates(
            @ForAll("builtinBrand") BrandDefinition brand,
            @ForAll("apiKey") String apiKey1,
            @ForAll("apiKey") String apiKey2) {

        BrandService service = createBrandService();

        String originalApiType = brand.apiType();

        // First update
        service.updateBrand(brand.id(), new UpdateBrandRequest(apiKey1, null, null));

        // Second update with different key
        service.updateBrand(brand.id(), new UpdateBrandRequest(apiKey2, null, null));

        BrandView retrieved = service.getBrand(brand.id());
        assertThat(retrieved.apiType())
            .as("Built-in brand '%s' apiType should remain '%s' after multiple updates",
                brand.name(), originalApiType)
            .isEqualTo(originalApiType);
    }

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<BrandDefinition> builtinBrand() {
        return Arbitraries.of(BUILTIN_BRANDS);
    }

    @Provide
    Arbitrary<String> apiKey() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(10)
            .ofMaxLength(50)
            .map(s -> "sk-" + s);
    }
}
