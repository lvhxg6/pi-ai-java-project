package com.pi.chat.service;

import com.pi.ai.core.types.Model;
import com.pi.chat.auth.WebAuthStorage;
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
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for BrandService SDK synchronization.
 *
 * <p>Feature: settings-model-brands
 * <ul>
 *   <li>Property 16: 内建品牌 API Key 同步到 AuthStorage — <b>Validates: Requirements 6.1</b></li>
 *   <li>Property 17: 扩展品牌注册到 CodingModelRegistry — <b>Validates: Requirements 6.2</b></li>
 *   <li>Property 18: 禁用再启用恢复注册状态 — <b>Validates: Requirements 6.3, 6.4</b></li>
 * </ul>
 */
class BrandRegistrySyncPropertyTest {

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

    /**
     * Creates a BrandService with a real WebAuthStorage (backed by a real
     * ProviderConfigRepository) so that syncToRegistry actually calls
     * {@code webAuthStorage.setRuntimeApiKey()}.
     */
    private record TestContext(BrandService brandService, WebAuthStorage webAuthStorage,
                               CodingModelRegistry modelRegistry) {}

    private TestContext createTestContext(boolean withRegistry) {
        try {
            Path tempDir = Files.createTempDirectory("brand-registry-sync-test");
            tempDir.toFile().deleteOnExit();
            Path configFile = tempDir.resolve("providers.json");
            ProviderConfigRepository repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            WebAuthStorage webAuthStorage = new WebAuthStorage(repository);

            CodingModelRegistry modelRegistry = null;
            if (withRegistry) {
                AuthStorage authStorage = webAuthStorage.getAuthStorage();
                modelRegistry = new CodingModelRegistry(authStorage);
            }

            BrandService brandService = new BrandService(repository, modelRegistry, webAuthStorage);
            return new TestContext(brandService, webAuthStorage, modelRegistry);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp dir for test", e);
        }
    }

    /** Convenience: create context without registry (for Property 16). */
    private TestContext createTestContext() {
        return createTestContext(false);
    }

    // ========== Property 16: 内建品牌 API Key 同步到 AuthStorage ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 16: 内建品牌 API Key 同步到 AuthStorage")
    void builtinBrandApiKeySyncsToWebAuthStorage(
            @ForAll("builtinBrand") BrandDefinition brand,
            @ForAll("apiKey") String apiKey) {

        TestContext ctx = createTestContext();

        // Save the API Key via updateBrand — this triggers syncToRegistry,
        // which calls webAuthStorage.setRuntimeApiKey(provider, apiKey)
        UpdateBrandRequest request = new UpdateBrandRequest(apiKey, null, null);
        ctx.brandService().updateBrand(brand.id(), request);

        // Verify WebAuthStorage returns the same API Key for the brand's provider
        String storedKey = ctx.webAuthStorage().getApiKey(brand.provider());

        assertThat(storedKey)
            .as("After saving API Key for builtin brand '%s' (provider=%s), " +
                "WebAuthStorage.getApiKey('%s') should return the same key",
                brand.name(), brand.provider(), brand.provider())
            .isEqualTo(apiKey);
    }

    // ========== Property 17: 扩展品牌注册到 CodingModelRegistry ==========

    /**
     * Property 17: For any enabled extension brand configuration (with valid API Key
     * and model list), after saving via updateBrand() and setting models via updateModels(),
     * CodingModelRegistry.find(brandId, modelId) should find the brand's models,
     * and the returned Model's api field should be "openai-completions".
     *
     * <p><b>Validates: Requirements 6.2</b>
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 17: 扩展品牌注册到 CodingModelRegistry")
    void extensionBrandRegisteredToCodingModelRegistry(
            @ForAll("extensionBrand") BrandDefinition brand,
            @ForAll("apiKey") String apiKey) {

        TestContext ctx = createTestContext(true);

        // 1. Save the extension brand with a valid API Key
        UpdateBrandRequest request = new UpdateBrandRequest(apiKey, brand.defaultBaseUrl(), true);
        ctx.brandService().updateBrand(brand.id(), request);

        // 2. Set the brand's default models — syncToRegistry uses config.models(),
        //    so we must explicitly save models for them to be registered
        ctx.brandService().updateModels(brand.id(), brand.defaultModels());

        // 3. Verify every default model of this extension brand is findable via CodingModelRegistry
        for (ModelEntry entry : brand.defaultModels()) {
            Model found = ctx.modelRegistry().find(brand.id(), entry.id());

            assertThat(found)
                .as("CodingModelRegistry.find('%s', '%s') should find the model after saving extension brand '%s'",
                    brand.id(), entry.id(), brand.name())
                .isNotNull();

            assertThat(found.api())
                .as("Model '%s' from extension brand '%s' should have api='openai-completions'",
                    entry.id(), brand.name())
                .isEqualTo("openai-completions");

            assertThat(found.provider())
                .as("Model '%s' provider should match brand id '%s'",
                    entry.id(), brand.id())
                .isEqualTo(brand.id());
        }
    }

    // ========== Property 18: 禁用再启用恢复注册状态 ==========

    /**
     * Property 18: For any registered extension brand, after disabling and then
     * re-enabling, {@code CodingModelRegistry.find(provider, modelId)} should be
     * able to find the brand's models again.
     *
     * <p><b>Validates: Requirements 6.3, 6.4</b>
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 18: 禁用再启用恢复注册状态")
    void disableThenReEnableRestoresRegistration(
            @ForAll("extensionBrand") BrandDefinition brand,
            @ForAll("apiKey") String apiKey) {

        TestContext ctx = createTestContext(true);

        // 1. Register the extension brand with a valid API Key and models
        UpdateBrandRequest request = new UpdateBrandRequest(apiKey, brand.defaultBaseUrl(), true);
        ctx.brandService().updateBrand(brand.id(), request);
        ctx.brandService().updateModels(brand.id(), brand.defaultModels());

        // Sanity check: models should be findable after registration
        for (ModelEntry entry : brand.defaultModels()) {
            assertThat(ctx.modelRegistry().find(brand.id(), entry.id()))
                .as("Model '%s' should be findable after initial registration", entry.id())
                .isNotNull();
        }

        // 2. Disable the brand → models should NOT be findable
        ctx.brandService().toggleEnabled(brand.id(), false);

        for (ModelEntry entry : brand.defaultModels()) {
            Model afterDisable = ctx.modelRegistry().find(brand.id(), entry.id());
            assertThat(afterDisable)
                .as("CodingModelRegistry.find('%s', '%s') should return null after disabling brand '%s'",
                    brand.id(), entry.id(), brand.name())
                .isNull();
        }

        // 3. Re-enable the brand → models should be findable again
        ctx.brandService().toggleEnabled(brand.id(), true);

        for (ModelEntry entry : brand.defaultModels()) {
            Model afterReEnable = ctx.modelRegistry().find(brand.id(), entry.id());
            assertThat(afterReEnable)
                .as("CodingModelRegistry.find('%s', '%s') should find the model after re-enabling brand '%s'",
                    brand.id(), entry.id(), brand.name())
                .isNotNull();

            assertThat(afterReEnable.provider())
                .as("Re-enabled model '%s' provider should match brand id '%s'",
                    entry.id(), brand.id())
                .isEqualTo(brand.id());
        }
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
    Arbitrary<String> apiKey() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(10)
            .ofMaxLength(50)
            .map(s -> "sk-" + s);
    }
}
