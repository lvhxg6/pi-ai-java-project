package com.pi.chat.service;

import com.pi.chat.dto.BrandView;
import com.pi.chat.dto.CreateBrandRequest;
import com.pi.chat.exception.IllegalBrandOperationException;
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
 * Property-based tests for brand deletion and deletion protection.
 *
 * <p>Feature: settings-model-brands, Property 13: 自定义品牌可删除
 * <p>Feature: settings-model-brands, Property 14: 预定义品牌删除保护
 *
 * <p>Validates: Requirements 4.4, 4.5, 9.5, 9.8
 */
class BrandDeletionPropertyTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-1234";

    private BrandService createBrandService() {
        try {
            Path tempDir = Files.createTempDirectory("brand-deletion-test");
            tempDir.toFile().deleteOnExit();
            Path configFile = tempDir.resolve("providers.json");
            ProviderConfigRepository repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            return new BrandService(repository, null, null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp dir for test", e);
        }
    }

    // ========== Property 13: Custom brands can be deleted ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 13: 自定义品牌可删除")
    void customBrandCanBeDeleted(
            @ForAll("brandName") String name,
            @ForAll("baseUrl") String baseUrl,
            @ForAll("apiKey") String apiKey) {

        BrandService service = createBrandService();

        // Create a custom brand
        CreateBrandRequest request = new CreateBrandRequest(name, baseUrl, apiKey);
        BrandView created = service.createCustomBrand(request);
        String brandId = created.id();

        // Verify it exists in listBrands
        assertThat(service.listBrands())
            .extracting(BrandView::id)
            .contains(brandId);

        // Delete it
        service.deleteCustomBrand(brandId);

        // Verify it's gone from listBrands
        assertThat(service.listBrands())
            .extracting(BrandView::id)
            .doesNotContain(brandId);
    }

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 13: 自定义品牌可删除")
    void deletedCustomBrandProviderConfigIsRemoved(
            @ForAll("brandName") String name,
            @ForAll("baseUrl") String baseUrl,
            @ForAll("apiKey") String apiKey) {

        BrandService service = createBrandService();

        // Create a custom brand
        CreateBrandRequest request = new CreateBrandRequest(name, baseUrl, apiKey);
        BrandView created = service.createCustomBrand(request);
        String brandId = created.id();

        // Delete it
        service.deleteCustomBrand(brandId);

        // Verify getBrand throws BrandNotFoundException (config removed from storage)
        assertThatThrownBy(() -> service.getBrand(brandId))
            .isInstanceOf(com.pi.chat.exception.BrandNotFoundException.class);
    }

    // ========== Property 14: Predefined brands cannot be deleted ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 14: 预定义品牌删除保护")
    void predefinedBrandCannotBeDeleted(
            @ForAll("predefinedBrand") BrandDefinition brand) {

        BrandService service = createBrandService();

        // Capture brand list before attempted deletion
        List<BrandView> brandsBefore = service.listBrands();

        // Attempt to delete a predefined brand — should throw
        assertThatThrownBy(() -> service.deleteCustomBrand(brand.id()))
            .isInstanceOf(IllegalBrandOperationException.class);

        // Verify brand list is unchanged
        List<BrandView> brandsAfter = service.listBrands();
        assertThat(brandsAfter)
            .extracting(BrandView::id)
            .containsExactlyElementsOf(
                brandsBefore.stream().map(BrandView::id).toList()
            );
    }

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<BrandDefinition> predefinedBrand() {
        return Arbitraries.of(BrandDefinitions.ALL);
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
