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
 * Property-based tests for BrandService Base URL default value and required validation.
 *
 * <p>Feature: settings-model-brands, Property 6: 内建品牌默认 Base URL
 * <p>Feature: settings-model-brands, Property 7: 扩展品牌 Base URL 必填校验
 *
 * <p>Validates: Requirements 2.4, 2.5
 *
 * <p>Property 6: For any builtin brand, when the user has not set a custom Base URL,
 * {@code getBrand()} should return the {@code defaultBaseUrl} defined in BrandDefinitions.
 *
 * <p>Property 7: For any extension brand or custom brand configuration update request,
 * if {@code baseUrl} is empty or null, the update operation should throw an exception.
 */
class BrandBaseUrlPropertyTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-1234";

    private static final List<BrandDefinition> BUILTIN_BRANDS = List.of(
        BrandDefinitions.CLAUDE,
        BrandDefinitions.CHATGPT,
        BrandDefinitions.GEMINI,
        BrandDefinitions.MISTRAL,
        BrandDefinitions.BEDROCK
    );

    private static final List<BrandDefinition> EXTENSION_BRANDS = List.of(
        BrandDefinitions.DEEPSEEK,
        BrandDefinitions.GLM,
        BrandDefinitions.MINIMAX,
        BrandDefinitions.KIMI
    );

    private BrandService createBrandService() {
        try {
            Path tempDir = Files.createTempDirectory("brand-baseurl-test");
            tempDir.toFile().deleteOnExit();
            Path configFile = tempDir.resolve("providers.json");
            ProviderConfigRepository repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            return new BrandService(repository, null, null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp dir for test", e);
        }
    }

    // ========== Property 6: Builtin brand returns defaultBaseUrl when no custom URL is set ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 6: 内建品牌默认 Base URL")
    void builtinBrandReturnsDefaultBaseUrlWhenNoCustomUrlSet(
            @ForAll("builtinBrand") BrandDefinition brand) {

        BrandService service = createBrandService();

        // No config saved — getBrand should fall back to definition's defaultBaseUrl
        BrandView view = service.getBrand(brand.id());

        assertThat(view.baseUrl())
            .as("Builtin brand '%s' should return defaultBaseUrl '%s' when no custom URL is set",
                brand.name(), brand.defaultBaseUrl())
            .isEqualTo(brand.defaultBaseUrl());
    }

    // ========== Property 7: Extension brand rejects empty/null baseUrl in updateBrand ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 7: 扩展品牌 Base URL 必填校验")
    void extensionBrandRejectsNullBaseUrlInUpdate(
            @ForAll("extensionBrand") BrandDefinition brand,
            @ForAll("apiKey") String apiKey) {

        BrandService service = createBrandService();

        // Attempt to update extension brand with null baseUrl (no existing config saved)
        UpdateBrandRequest request = new UpdateBrandRequest(apiKey, null, null);

        assertThatThrownBy(() -> service.updateBrand(brand.id(), request))
            .as("Extension brand '%s' should reject update with null baseUrl", brand.name())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Base URL is required");
    }

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 7: 扩展品牌 Base URL 必填校验")
    void extensionBrandRejectsBlankBaseUrlInUpdate(
            @ForAll("extensionBrand") BrandDefinition brand,
            @ForAll("apiKey") String apiKey,
            @ForAll("blankString") String blankBaseUrl) {

        BrandService service = createBrandService();

        // Attempt to update extension brand with blank baseUrl
        UpdateBrandRequest request = new UpdateBrandRequest(apiKey, blankBaseUrl, null);

        assertThatThrownBy(() -> service.updateBrand(brand.id(), request))
            .as("Extension brand '%s' should reject update with blank baseUrl '%s'",
                brand.name(), blankBaseUrl)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Base URL is required");
    }

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 7: 扩展品牌 Base URL 必填校验")
    void customBrandRejectsNullBaseUrlInUpdate(
            @ForAll("brandName") String name,
            @ForAll("baseUrl") String baseUrl,
            @ForAll("apiKey") String apiKey,
            @ForAll("apiKey") String newApiKey) {

        BrandService service = createBrandService();

        // Create a custom brand first (with valid baseUrl)
        var createRequest = new com.pi.chat.dto.CreateBrandRequest(name, baseUrl, apiKey);
        BrandView created = service.createCustomBrand(createRequest);

        // Now update it — set baseUrl to blank, which should be rejected
        // since existing config has a valid baseUrl, passing null keeps existing,
        // so we must pass blank explicitly to trigger validation
        UpdateBrandRequest request = new UpdateBrandRequest(newApiKey, "", null);

        assertThatThrownBy(() -> service.updateBrand(created.id(), request))
            .as("Custom brand should reject update with blank baseUrl")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Base URL is required");
    }

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<BrandDefinition> builtinBrand() {
        return Arbitraries.of(BUILTIN_BRANDS);
    }

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

    @Provide
    Arbitrary<String> blankString() {
        return Arbitraries.of("", "   ", "\t", "\n", "  \t\n  ");
    }
}
