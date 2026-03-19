package com.pi.chat.service;

import com.pi.chat.dto.BrandView;
import com.pi.chat.dto.CreateBrandRequest;
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
 * Property-based tests for brand list ordering invariant.
 *
 * <p>Feature: settings-model-brands, Property 15: 品牌列表排序不变量
 *
 * <p><b>Validates: Requirements 5.3</b>
 *
 * <p>For any {@code listBrands()} result, all builtin brands ({@code builtin=true})
 * should have indices smaller than all non-builtin brands.
 */
class BrandListOrderPropertyTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-1234";

    private BrandService createBrandService() {
        try {
            Path tempDir = Files.createTempDirectory("brand-list-order-test");
            tempDir.toFile().deleteOnExit();
            Path configFile = tempDir.resolve("providers.json");
            ProviderConfigRepository repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            return new BrandService(repository, null, null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp dir for test", e);
        }
    }

    // ========== Property 15: Default brand list has builtin brands before non-builtin ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 15: 品牌列表排序不变量")
    void defaultBrandListHasBuiltinBrandsBeforeNonBuiltin() {
        BrandService service = createBrandService();
        List<BrandView> brands = service.listBrands();

        assertBuiltinBeforeNonBuiltin(brands);
    }

    // ========== Property 15: Ordering holds after creating custom brands ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 15: 品牌列表排序不变量")
    void orderingHoldsAfterCreatingCustomBrands(
            @ForAll("brandName") String name,
            @ForAll("baseUrl") String baseUrl,
            @ForAll("apiKey") String apiKey) {

        BrandService service = createBrandService();

        // Create a custom brand
        CreateBrandRequest request = new CreateBrandRequest(name, baseUrl, apiKey);
        service.createCustomBrand(request);

        List<BrandView> brands = service.listBrands();
        assertBuiltinBeforeNonBuiltin(brands);
    }

    // ========== Property 15: Ordering holds after toggling enabled states ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 15: 品牌列表排序不变量")
    void orderingHoldsAfterTogglingEnabledStates(
            @ForAll("predefinedBrand") BrandDefinition brand,
            @ForAll boolean enabled) {

        BrandService service = createBrandService();

        // Toggle enabled state on a predefined brand
        service.toggleEnabled(brand.id(), enabled);

        List<BrandView> brands = service.listBrands();
        assertBuiltinBeforeNonBuiltin(brands);
    }

    // ========== Property 15: Ordering holds after mixed operations ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 15: 品牌列表排序不变量")
    void orderingHoldsAfterMixedOperations(
            @ForAll("brandName") String customName,
            @ForAll("baseUrl") String baseUrl,
            @ForAll("apiKey") String apiKey,
            @ForAll("extensionBrand") BrandDefinition extBrand,
            @ForAll boolean toggleValue) {

        BrandService service = createBrandService();

        // Create a custom brand
        service.createCustomBrand(new CreateBrandRequest(customName, baseUrl, apiKey));

        // Update an extension brand with an API key
        service.updateBrand(extBrand.id(),
                new UpdateBrandRequest(apiKey, extBrand.defaultBaseUrl(), null));

        // Toggle enabled state on the extension brand
        service.toggleEnabled(extBrand.id(), toggleValue);

        List<BrandView> brands = service.listBrands();
        assertBuiltinBeforeNonBuiltin(brands);
    }

    // ========== Assertion Helper ==========

    /**
     * Asserts that all builtin brands appear before all non-builtin brands in the list.
     * Specifically: the maximum index of any builtin brand must be less than
     * the minimum index of any non-builtin brand.
     */
    private void assertBuiltinBeforeNonBuiltin(List<BrandView> brands) {
        int lastBuiltinIndex = -1;
        int firstNonBuiltinIndex = Integer.MAX_VALUE;

        for (int i = 0; i < brands.size(); i++) {
            if (brands.get(i).builtin()) {
                lastBuiltinIndex = i;
            } else {
                firstNonBuiltinIndex = Math.min(firstNonBuiltinIndex, i);
            }
        }

        if (lastBuiltinIndex >= 0 && firstNonBuiltinIndex < Integer.MAX_VALUE) {
            assertThat(lastBuiltinIndex)
                .as("Last builtin brand index (%d) should be less than first non-builtin brand index (%d). Brands: %s",
                    lastBuiltinIndex, firstNonBuiltinIndex,
                    brands.stream().map(b -> b.id() + "(builtin=" + b.builtin() + ")").toList())
                .isLessThan(firstNonBuiltinIndex);
        }
    }

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<BrandDefinition> predefinedBrand() {
        return Arbitraries.of(BrandDefinitions.ALL);
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
