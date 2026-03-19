package com.pi.chat.service;

import com.pi.chat.dto.BrandView;
import com.pi.chat.dto.CreateBrandRequest;
import com.pi.chat.dto.UpdateBrandRequest;
import com.pi.chat.model.BrandDefinition;
import com.pi.chat.model.BrandDefinitions;
import com.pi.chat.repository.ProviderConfigRepository;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for BrandService API type invariant.
 *
 * <p>Feature: settings-model-brands, Property 1: 非内建品牌的 API 协议不变量
 *
 * <p>Validates: Requirements 1.3, 2.8, 4.2
 *
 * <p>For any non-builtin brand (including predefined extension brands and
 * user custom brands), its {@code apiType} field should always be
 * {@code "openai-completions"}.
 */
class BrandApiTypePropertyTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-1234";
    private static final String OPENAI_COMPLETIONS = "openai-completions";

    private static final List<BrandDefinition> EXTENSION_BRANDS = List.of(
        BrandDefinitions.DEEPSEEK,
        BrandDefinitions.GLM,
        BrandDefinitions.MINIMAX,
        BrandDefinitions.KIMI
    );

    private BrandService createBrandService() {
        try {
            Path tempDir = Files.createTempDirectory("brand-api-type-test");
            tempDir.toFile().deleteOnExit();
            Path configFile = tempDir.resolve("providers.json");
            ProviderConfigRepository repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            return new BrandService(repository, null, null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp dir for test", e);
        }
    }

    // ========== Property 1: Predefined extension brands always have apiType = "openai-completions" ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 1: 非内建品牌的 API 协议不变量")
    void predefinedExtensionBrandAlwaysHasOpenAiCompletionsApiType(
            @ForAll("extensionBrand") BrandDefinition brand) {

        BrandService service = createBrandService();
        BrandView view = service.getBrand(brand.id());

        assertThat(view.apiType())
            .as("Extension brand '%s' should have apiType='openai-completions'", brand.name())
            .isEqualTo(OPENAI_COMPLETIONS);
    }

    // ========== Property 1: Custom brands created via createCustomBrand always have apiType = "openai-completions" ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 1: 非内建品牌的 API 协议不变量")
    void customBrandAlwaysHasOpenAiCompletionsApiType(
            @ForAll("brandName") String name,
            @ForAll("baseUrl") String baseUrl,
            @ForAll("apiKey") String apiKey) {

        BrandService service = createBrandService();
        CreateBrandRequest request = new CreateBrandRequest(name, baseUrl, apiKey);
        BrandView created = service.createCustomBrand(request);

        assertThat(created.apiType())
            .as("Custom brand '%s' should have apiType='openai-completions'", name)
            .isEqualTo(OPENAI_COMPLETIONS);

        // Also verify via getBrand round-trip
        BrandView retrieved = service.getBrand(created.id());
        assertThat(retrieved.apiType())
            .as("Custom brand '%s' retrieved should still have apiType='openai-completions'", name)
            .isEqualTo(OPENAI_COMPLETIONS);
    }

    // ========== Property 1: apiType remains "openai-completions" after updates ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 1: 非内建品牌的 API 协议不变量")
    void extensionBrandApiTypeRemainsAfterUpdate(
            @ForAll("extensionBrand") BrandDefinition brand,
            @ForAll("apiKey") String apiKey) {

        BrandService service = createBrandService();

        // Update the extension brand with a new API key
        UpdateBrandRequest updateRequest = new UpdateBrandRequest(
            apiKey, brand.defaultBaseUrl(), null);
        BrandView updated = service.updateBrand(brand.id(), updateRequest);

        assertThat(updated.apiType())
            .as("Extension brand '%s' apiType should remain 'openai-completions' after update",
                brand.name())
            .isEqualTo(OPENAI_COMPLETIONS);
    }

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 1: 非内建品牌的 API 协议不变量")
    void customBrandApiTypeRemainsAfterUpdate(
            @ForAll("brandName") String name,
            @ForAll("baseUrl") String baseUrl,
            @ForAll("apiKey") String apiKey,
            @ForAll("apiKey") String newApiKey) {

        BrandService service = createBrandService();

        // Create a custom brand
        CreateBrandRequest createRequest = new CreateBrandRequest(name, baseUrl, apiKey);
        BrandView created = service.createCustomBrand(createRequest);

        // Update it
        UpdateBrandRequest updateRequest = new UpdateBrandRequest(newApiKey, baseUrl, null);
        BrandView updated = service.updateBrand(created.id(), updateRequest);

        assertThat(updated.apiType())
            .as("Custom brand apiType should remain 'openai-completions' after update")
            .isEqualTo(OPENAI_COMPLETIONS);
    }

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<BrandDefinition> extensionBrand() {
        return Arbitraries.of(EXTENSION_BRANDS);
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
