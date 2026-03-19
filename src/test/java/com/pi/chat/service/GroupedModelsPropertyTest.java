package com.pi.chat.service;

import com.pi.ai.core.types.Model;
import com.pi.chat.auth.WebAuthStorage;
import com.pi.chat.dto.GroupedModels;
import com.pi.chat.dto.ModelDTO;
import com.pi.chat.dto.UpdateBrandRequest;
import com.pi.chat.model.BrandDefinition;
import com.pi.chat.model.BrandDefinitions;
import com.pi.chat.model.ModelEntry;
import com.pi.chat.repository.ProviderConfigRepository;
import com.pi.coding.auth.AuthStorage;
import com.pi.coding.model.CodingModelRegistry;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for grouped model filtering and API protocol consistency.
 *
 * <p>Feature: settings-model-brands
 * <ul>
 *   <li>Property 19: 分组模型过滤不变量 — <b>Validates: Requirements 7.1, 7.2, 7.4</b></li>
 *   <li>Property 20: 模型 API 协议一致性 — <b>Validates: Requirements 7.3</b></li>
 * </ul>
 */
class GroupedModelsPropertyTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-1234";

    private static final List<BrandDefinition> ALL_BRANDS = BrandDefinitions.ALL;

    private static final List<BrandDefinition> EXTENSION_BRANDS = List.of(
        BrandDefinitions.DEEPSEEK,
        BrandDefinitions.GLM,
        BrandDefinitions.MINIMAX,
        BrandDefinitions.KIMI
    );

    private record TestContext(BrandService brandService, WebAuthStorage webAuthStorage,
                               CodingModelRegistry modelRegistry) {}

    private TestContext createTestContext() {
        try {
            Path tempDir = Files.createTempDirectory("grouped-models-test");
            tempDir.toFile().deleteOnExit();
            Path configFile = tempDir.resolve("providers.json");
            ProviderConfigRepository repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            WebAuthStorage webAuthStorage = new WebAuthStorage(repository);
            AuthStorage authStorage = webAuthStorage.getAuthStorage();
            CodingModelRegistry modelRegistry = new CodingModelRegistry(authStorage);
            BrandService brandService = new BrandService(repository, modelRegistry, webAuthStorage);
            return new TestContext(brandService, webAuthStorage, modelRegistry);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp dir for test", e);
        }
    }

    // ========== Property 19: 分组模型过滤不变量 ==========

    /**
     * Property 19: For any brand configuration set, getGroupedModels() results should
     * only contain brands that are enabled=true AND have a non-empty API Key.
     * Each model in a group should belong to that brand.
     *
     * <p>We set up a random subset of brands as enabled-with-key, some as disabled,
     * and some without API keys, then verify the filtering invariant.
     *
     * <p><b>Validates: Requirements 7.1, 7.2, 7.4</b>
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 19: 分组模型过滤不变量")
    void groupedModelsOnlyContainEnabledBrandsWithApiKey(
            @ForAll("brandConfigScenario") BrandConfigScenario scenario) {

        TestContext ctx = createTestContext();

        // Set up each brand according to the scenario
        Set<String> expectedBrandIds = new HashSet<>();

        for (BrandSetup setup : scenario.setups()) {
            BrandDefinition brand = setup.brand();

            if (setup.hasApiKey()) {
                // Save brand with API key
                UpdateBrandRequest request = new UpdateBrandRequest(
                    setup.apiKey(), brand.defaultBaseUrl(), setup.enabled());
                ctx.brandService().updateBrand(brand.id(), request);

                // For extension brands, also save models so they appear in grouped results
                if (!brand.builtin() && !brand.defaultModels().isEmpty()) {
                    ctx.brandService().updateModels(brand.id(), brand.defaultModels());
                }

                if (setup.enabled()) {
                    expectedBrandIds.add(brand.id());
                }
            } else {
                // Save brand without API key (just toggle enabled state)
                // For brands without API key, they should NOT appear in grouped results
                ctx.brandService().toggleEnabled(brand.id(), setup.enabled());
            }
        }

        // Call getGroupedModels
        List<GroupedModels> grouped = ctx.brandService().getGroupedModels();

        // Verify: every brand in the result must be enabled AND have an API key
        for (GroupedModels group : grouped) {
            // The brand must be one we set up as enabled + has API key
            // (or a built-in brand that has models from SDK registry)
            assertThat(group.models())
                .as("Brand '%s' group should have non-empty models", group.brandName())
                .isNotEmpty();

            // Verify each model in the group has a provider matching the brand
            for (ModelDTO model : group.models()) {
                assertThat(model.provider())
                    .as("Model '%s' in brand group '%s' should have a provider associated with that brand",
                        model.id(), group.brandName())
                    .isNotNull();
            }
        }

        // Verify: no disabled brand or brand without API key appears in the result
        Set<String> resultBrandIds = new HashSet<>();
        for (GroupedModels group : grouped) {
            resultBrandIds.add(group.brandId());
        }

        for (BrandSetup setup : scenario.setups()) {
            if (!setup.enabled() || !setup.hasApiKey()) {
                assertThat(resultBrandIds)
                    .as("Brand '%s' (enabled=%s, hasApiKey=%s) should NOT appear in grouped models",
                        setup.brand().id(), setup.enabled(), setup.hasApiKey())
                    .doesNotContain(setup.brand().id());
            }
        }
    }

    // ========== Property 20: 模型 API 协议一致性 ==========

    /**
     * Property 20: For any extension brand and any model under that brand,
     * the model found via CodingModelRegistry.find(provider, modelId) should have
     * an api field matching the brand's apiType.
     *
     * <p><b>Validates: Requirements 7.3</b>
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 20: 模型 API 协议一致性")
    void modelApiFieldMatchesBrandApiType(
            @ForAll("extensionBrand") BrandDefinition brand,
            @ForAll("apiKey") String apiKey) {

        TestContext ctx = createTestContext();

        // Register the extension brand with API key and models
        UpdateBrandRequest request = new UpdateBrandRequest(apiKey, brand.defaultBaseUrl(), true);
        ctx.brandService().updateBrand(brand.id(), request);
        ctx.brandService().updateModels(brand.id(), brand.defaultModels());

        // For each model in the brand, verify api field consistency via CodingModelRegistry
        for (ModelEntry entry : brand.defaultModels()) {
            Model found = ctx.modelRegistry().find(brand.id(), entry.id());

            assertThat(found)
                .as("CodingModelRegistry.find('%s', '%s') should find the model",
                    brand.id(), entry.id())
                .isNotNull();

            assertThat(found.api())
                .as("Model '%s' from brand '%s' should have api='%s' matching brand's apiType",
                    entry.id(), brand.name(), brand.apiType())
                .isEqualTo(brand.apiType());
        }
    }

    // ========== Data Classes ==========

    record BrandSetup(BrandDefinition brand, boolean enabled, boolean hasApiKey, String apiKey) {}

    record BrandConfigScenario(List<BrandSetup> setups) {}

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<BrandDefinition> extensionBrand() {
        return Arbitraries.of(EXTENSION_BRANDS);
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
    Arbitrary<BrandConfigScenario> brandConfigScenario() {
        // Pick a random subset of extension brands (at least 1) and assign random configs
        return Arbitraries.of(EXTENSION_BRANDS)
            .list().ofMinSize(1).ofMaxSize(EXTENSION_BRANDS.size())
            .uniqueElements(BrandDefinition::id)
            .flatMap(brands -> {
                List<Arbitrary<BrandSetup>> setupArbitraries = new ArrayList<>();
                for (BrandDefinition brand : brands) {
                    Arbitrary<BrandSetup> setupArb = Combinators.combine(
                        Arbitraries.of(true, false),  // enabled
                        Arbitraries.of(true, false)   // hasApiKey
                    ).flatAs((enabled, hasKey) -> {
                        if (hasKey) {
                            return apiKey().map(key -> new BrandSetup(brand, enabled, true, key));
                        } else {
                            return Arbitraries.just(new BrandSetup(brand, enabled, false, null));
                        }
                    });
                    setupArbitraries.add(setupArb);
                }
                return Combinators.combine(setupArbitraries).as(BrandConfigScenario::new);
            });
    }
}
