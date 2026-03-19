package com.pi.chat.service;

import com.pi.chat.dto.BrandView;
import com.pi.chat.dto.CreateBrandRequest;
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
 * Property-based tests for BrandService enabled state persistence round-trip.
 *
 * <p>Feature: settings-model-brands, Property 8: 启用状态持久化 Round-Trip
 *
 * <p>Validates: Requirements 2.6
 *
 * <p>For any brand and any boolean value {@code enabled}, calling
 * {@code toggleEnabled(brandId, enabled)} then calling {@code getBrand(brandId)}
 * should return the same {@code enabled} value.
 */
class BrandEnabledPropertyTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-1234";

    private BrandService createBrandService() {
        try {
            Path tempDir = Files.createTempDirectory("brand-enabled-test");
            tempDir.toFile().deleteOnExit();
            Path configFile = tempDir.resolve("providers.json");
            ProviderConfigRepository repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            return new BrandService(repository, null, null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp dir for test", e);
        }
    }

    // ========== Property 8: Built-in brand enabled state round-trip ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 8: 启用状态持久化 Round-Trip")
    void builtinBrandEnabledStateRoundTrip(
            @ForAll("builtinBrand") BrandDefinition brand,
            @ForAll boolean enabled) {

        BrandService service = createBrandService();

        service.toggleEnabled(brand.id(), enabled);
        BrandView retrieved = service.getBrand(brand.id());

        assertThat(retrieved.enabled())
            .as("Built-in brand '%s' enabled state should be %s after toggleEnabled",
                brand.name(), enabled)
            .isEqualTo(enabled);
    }

    // ========== Property 8: Extension brand enabled state round-trip ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 8: 启用状态持久化 Round-Trip")
    void extensionBrandEnabledStateRoundTrip(
            @ForAll("extensionBrand") BrandDefinition brand,
            @ForAll boolean enabled) {

        BrandService service = createBrandService();

        service.toggleEnabled(brand.id(), enabled);
        BrandView retrieved = service.getBrand(brand.id());

        assertThat(retrieved.enabled())
            .as("Extension brand '%s' enabled state should be %s after toggleEnabled",
                brand.name(), enabled)
            .isEqualTo(enabled);
    }

    // ========== Property 8: Custom brand enabled state round-trip ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 8: 启用状态持久化 Round-Trip")
    void customBrandEnabledStateRoundTrip(
            @ForAll("brandName") String name,
            @ForAll("baseUrl") String baseUrl,
            @ForAll("apiKey") String apiKey,
            @ForAll boolean enabled) {

        BrandService service = createBrandService();

        // Create a custom brand first
        CreateBrandRequest request = new CreateBrandRequest(name, baseUrl, apiKey);
        BrandView created = service.createCustomBrand(request);

        // Toggle enabled state
        service.toggleEnabled(created.id(), enabled);
        BrandView retrieved = service.getBrand(created.id());

        assertThat(retrieved.enabled())
            .as("Custom brand '%s' enabled state should be %s after toggleEnabled",
                name, enabled)
            .isEqualTo(enabled);
    }

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<BrandDefinition> builtinBrand() {
        return Arbitraries.of(
            BrandDefinitions.CLAUDE,
            BrandDefinitions.CHATGPT,
            BrandDefinitions.GEMINI,
            BrandDefinitions.MISTRAL,
            BrandDefinitions.BEDROCK
        );
    }

    @Provide
    Arbitrary<BrandDefinition> extensionBrand() {
        return Arbitraries.of(
            BrandDefinitions.DEEPSEEK,
            BrandDefinitions.GLM,
            BrandDefinitions.MINIMAX,
            BrandDefinitions.KIMI
        );
    }

    @Provide
    Arbitrary<String> brandName() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(20)
            .map(s -> "Custom-" + s);
    }

    @Provide
    Arbitrary<String> baseUrl() {
        return Arbitraries.of(
            "https://api.example.com",
            "https://api.custom-llm.io",
            "https://openai-compat.local:8080",
            "https://my-model-server.com/v1"
        );
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
