package com.pi.chat.service;

import com.pi.chat.auth.WebAuthStorage;
import com.pi.chat.dto.BrandView;
import com.pi.chat.model.ConnectionStatus;
import com.pi.chat.model.ModelEntry;
import com.pi.chat.model.ProviderConfig;
import com.pi.chat.repository.ProviderConfigRepository;
import com.pi.coding.model.CodingModelRegistry;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for brand configuration persistence round-trip.
 *
 * <p>Feature: settings-model-brands, Property 23: 品牌配置持久化 Round-Trip
 * <p>Feature: settings-model-brands, Property 24: 模型列表更新 Round-Trip
 *
 * <p><b>Validates: Requirements 9.3, 9.6, 10.1</b>
 *
 * <p>For any valid brand configuration (with API Key, Base URL, enabled state),
 * saving and then reloading from storage should yield an equivalent configuration
 * (API Key identical after decryption).
 */
class BrandPersistencePropertyTest {

    private static final String ENCRYPTION_KEY = "test-persistence-key-12345";

    /**
     * Property 23: Brand configuration persistence round-trip.
     * Save a ProviderConfig, then reload via findById — all fields should match.
     *
     * <p><b>Validates: Requirements 9.3, 10.1</b>
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 23: 品牌配置持久化 Round-Trip")
    void savedConfigShouldBeRecoverableViaFindById(
            @ForAll("providerConfigs") ProviderConfig config) throws IOException {

        Path tempDir = Files.createTempDirectory("brand-persist-test");
        Path configFile = tempDir.resolve("providers.json");

        try {
            ProviderConfigRepository repository =
                    new ProviderConfigRepository(configFile, ENCRYPTION_KEY);

            // Save
            repository.save(config);

            // Reload via findById
            Optional<ProviderConfig> loaded = repository.findById(config.id());

            assertThat(loaded).isPresent();
            ProviderConfig result = loaded.get();

            // Identity & metadata fields
            assertThat(result.id()).isEqualTo(config.id());
            assertThat(result.type()).isEqualTo(config.type());
            assertThat(result.name()).isEqualTo(config.name());
            assertThat(result.baseUrl()).isEqualTo(config.baseUrl());
            assertThat(result.enabled()).isEqualTo(config.enabled());
            assertThat(result.apiType()).isEqualTo(config.apiType());

            // API Key round-trip (decrypted transparently)
            assertThat(result.apiKey())
                    .as("API key should be identical after encrypt/decrypt round-trip")
                    .isEqualTo(config.apiKey());

            // Models round-trip
            assertThat(result.models()).isEqualTo(config.models());
        } finally {
            Files.deleteIfExists(configFile);
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * Property 23 (supplementary): Persistence round-trip across a fresh repository instance.
     * Save with one repository, create a new instance pointing to the same file, and reload.
     *
     * <p><b>Validates: Requirements 9.3, 10.1</b>
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 23: 品牌配置持久化 Round-Trip")
    void savedConfigShouldSurviveRepositoryRestart(
            @ForAll("providerConfigs") ProviderConfig config) throws IOException {

        Path tempDir = Files.createTempDirectory("brand-persist-restart-test");
        Path configFile = tempDir.resolve("providers.json");

        try {
            // Save with first repository instance
            ProviderConfigRepository repo1 =
                    new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            repo1.save(config);

            // Create a brand-new repository instance (simulates app restart)
            ProviderConfigRepository repo2 =
                    new ProviderConfigRepository(configFile, ENCRYPTION_KEY);

            Optional<ProviderConfig> loaded = repo2.findById(config.id());

            assertThat(loaded).isPresent();
            ProviderConfig result = loaded.get();

            assertThat(result.id()).isEqualTo(config.id());
            assertThat(result.type()).isEqualTo(config.type());
            assertThat(result.name()).isEqualTo(config.name());
            assertThat(result.apiKey())
                    .as("API key should survive repository restart")
                    .isEqualTo(config.apiKey());
            assertThat(result.baseUrl()).isEqualTo(config.baseUrl());
            assertThat(result.enabled()).isEqualTo(config.enabled());
            assertThat(result.models()).isEqualTo(config.models());
            assertThat(result.apiType()).isEqualTo(config.apiType());
        } finally {
            Files.deleteIfExists(configFile);
            Files.deleteIfExists(tempDir);
        }
    }

    // ========== Property 24: 模型列表更新 Round-Trip ==========

    /**
     * Property 24: Model list update round-trip via BrandService.
     * For any custom brand and any valid model list, calling updateModels then getBrand
     * should return a models list equivalent to the one that was set.
     *
     * <p>Uses a custom brand ID (not predefined) to avoid merge behavior with
     * BrandDefinition.defaultModels.
     *
     * <p><b>Validates: Requirements 9.6</b>
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 24: 模型列表更新 Round-Trip")
    void updateModelsThenGetBrandShouldReturnSameModels(
            @ForAll("modelEntryLists") List<ModelEntry> models) throws IOException {

        Path tempDir = Files.createTempDirectory("brand-models-roundtrip-test");
        Path configFile = tempDir.resolve("providers.json");

        try {
            ProviderConfigRepository repository =
                    new ProviderConfigRepository(configFile, ENCRYPTION_KEY);

            CodingModelRegistry mockRegistry = mock(CodingModelRegistry.class);
            WebAuthStorage mockAuthStorage = mock(WebAuthStorage.class);

            BrandService brandService = new BrandService(repository, mockRegistry, mockAuthStorage);

            // Save a custom brand config first (not predefined, to avoid defaultModels merge)
            String brandId = "custom-test-brand";
            Instant now = Instant.now();
            ProviderConfig initialConfig = new ProviderConfig(
                    brandId, "custom-test", "Test Brand",
                    "sk-testapikey1234567890", "https://api.test.com",
                    now, now, ConnectionStatus.UNKNOWN,
                    true, List.of(), "openai-completions"
            );
            repository.save(initialConfig);

            // Update models via BrandService
            brandService.updateModels(brandId, models);

            // Retrieve via getBrand
            BrandView view = brandService.getBrand(brandId);

            // The returned models should be equivalent to what was set
            assertThat(view.models())
                    .as("Models returned by getBrand should match the list passed to updateModels")
                    .containsExactlyElementsOf(models);
        } finally {
            Files.deleteIfExists(configFile);
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * Property 24 (supplementary): Model list update round-trip survives repository restart.
     * After updateModels, creating a fresh BrandService with a new repository instance
     * pointing to the same file should still return the same models.
     *
     * <p><b>Validates: Requirements 9.6</b>
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 24: 模型列表更新 Round-Trip")
    void updateModelsShouldSurviveRepositoryRestart(
            @ForAll("modelEntryLists") List<ModelEntry> models) throws IOException {

        Path tempDir = Files.createTempDirectory("brand-models-restart-test");
        Path configFile = tempDir.resolve("providers.json");

        try {
            // First repository + service: save brand and update models
            ProviderConfigRepository repo1 =
                    new ProviderConfigRepository(configFile, ENCRYPTION_KEY);

            CodingModelRegistry mockRegistry = mock(CodingModelRegistry.class);
            WebAuthStorage mockAuthStorage = mock(WebAuthStorage.class);

            BrandService service1 = new BrandService(repo1, mockRegistry, mockAuthStorage);

            String brandId = "restart-test-brand";
            Instant now = Instant.now();
            ProviderConfig initialConfig = new ProviderConfig(
                    brandId, "restart-type", "Restart Brand",
                    "sk-restartkey1234567890", "https://api.restart.com",
                    now, now, ConnectionStatus.UNKNOWN,
                    true, List.of(), "openai-completions"
            );
            repo1.save(initialConfig);
            service1.updateModels(brandId, models);

            // Second repository + service (simulates app restart)
            ProviderConfigRepository repo2 =
                    new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            BrandService service2 = new BrandService(repo2, mockRegistry, mockAuthStorage);

            BrandView view = service2.getBrand(brandId);

            assertThat(view.models())
                    .as("Models should survive repository restart")
                    .containsExactlyElementsOf(models);
        } finally {
            Files.deleteIfExists(configFile);
            Files.deleteIfExists(tempDir);
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<ProviderConfig> providerConfigs() {
        Arbitrary<String> ids = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "brand-" + s);

        Arbitrary<String> types = Arbitraries.of(
                "anthropic", "openai", "google", "mistral",
                "amazon-bedrock", "deepseek", "glm", "kimi", "custom-provider"
        );

        Arbitrary<String> names = Arbitraries.strings()
                .alpha()
                .ofMinLength(1).ofMaxLength(30)
                .map(s -> "Brand-" + s);

        Arbitrary<String> apiKeys = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(10).ofMaxLength(80)
                .map(s -> "sk-" + s);

        Arbitrary<String> baseUrls = Arbitraries.of(
                "https://api.example.com",
                "https://api.openai.com/v1",
                "https://api.deepseek.com",
                "https://custom.endpoint.io/v2"
        );

        Arbitrary<Boolean> enabledFlags = Arbitraries.of(true, false);

        Arbitrary<List<ModelEntry>> modelLists = modelEntries()
                .list().ofMinSize(0).ofMaxSize(5);

        Arbitrary<String> apiTypes = Arbitraries.of(
                "anthropic-messages", "openai-responses", "openai-completions",
                "google-generative-ai", "mistral-conversations", "bedrock-converse-stream"
        );

        return Combinators.combine(ids, types, names, apiKeys, baseUrls, enabledFlags, modelLists, apiTypes)
                .as((id, type, name, apiKey, baseUrl, enabled, models, apiType) ->
                        new ProviderConfig(
                                id, type, name, apiKey, baseUrl,
                                Instant.now(), Instant.now(),
                                ConnectionStatus.UNKNOWN,
                                enabled, models, apiType
                        ));
    }

    @Provide
    Arbitrary<ModelEntry> modelEntries() {
        Arbitrary<String> modelIds = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "model-" + s);

        Arbitrary<String> modelNames = Arbitraries.strings()
                .alpha()
                .ofMinLength(1).ofMaxLength(30)
                .map(s -> "Model-" + s);

        Arbitrary<Integer> contextWindows = Arbitraries.integers().between(1024, 2_000_000);
        Arbitrary<Integer> maxTokens = Arbitraries.integers().between(256, 128_000);
        Arbitrary<Boolean> customs = Arbitraries.of(true, false);

        return Combinators.combine(modelIds, modelNames, contextWindows, maxTokens, customs)
                .as(ModelEntry::new);
    }

    @Provide
    Arbitrary<List<ModelEntry>> modelEntryLists() {
        return modelEntries().list().ofMinSize(0).ofMaxSize(8);
    }
}
