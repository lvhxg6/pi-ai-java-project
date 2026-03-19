package com.pi.chat.service;

import com.pi.chat.dto.BrandView;
import com.pi.chat.dto.CreateBrandRequest;
import com.pi.chat.model.BrandDefinition;
import com.pi.chat.model.BrandDefinitions;
import com.pi.chat.model.ModelEntry;
import com.pi.chat.repository.ProviderConfigRepository;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for BrandService model list add/remove and custom flag.
 *
 * <p>Feature: settings-model-brands, Property 10/11/12: 模型列表增删与标记
 *
 * <p><b>Validates: Requirements 3.3, 3.4, 3.5</b>
 *
 * <p>Property 10: For any brand and any valid custom ModelEntry, adding it to
 * the brand's model list should increase the list length by 1 and the list
 * should contain that ModelEntry.
 *
 * <p>Property 11: For any brand and any existing custom ModelEntry, removing it
 * from the brand's model list should result in the list no longer containing
 * that ModelEntry.
 *
 * <p>Property 12: For any brand's model list, preset models should have
 * {@code custom=false}, and user-added models should have {@code custom=true}.
 */
class BrandModelListPropertyTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-1234";

    private BrandService createBrandService() {
        try {
            Path tempDir = Files.createTempDirectory("brand-model-list-test");
            tempDir.toFile().deleteOnExit();
            Path configFile = tempDir.resolve("providers.json");
            ProviderConfigRepository repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            return new BrandService(repository, null, null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp dir for test", e);
        }
    }

    // ========== Property 10: Adding a custom model grows the list by 1 (extension brand) ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 10: 添加自定义模型增长列表")
    void addingCustomModelToExtensionBrandGrowsList(
            @ForAll("extensionBrand") BrandDefinition brand,
            @ForAll("customModelEntry") ModelEntry newModel) {

        BrandService service = createBrandService();

        // Get the current model list (preset defaults from definition)
        BrandView before = service.getBrand(brand.id());
        List<ModelEntry> currentModels = before.models();
        int sizeBefore = currentModels.size();

        // Build new list = existing + new custom model
        List<ModelEntry> updatedModels = new ArrayList<>(currentModels);
        updatedModels.add(newModel);

        // Persist via updateModels — need a saved config first
        service.toggleEnabled(brand.id(), true);
        service.updateModels(brand.id(), updatedModels);

        BrandView after = service.getBrand(brand.id());

        assertThat(after.models()).hasSize(sizeBefore + 1);
        assertThat(after.models()).contains(newModel);
    }

    // ========== Property 10: Adding a custom model grows the list by 1 (custom brand) ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 10: 添加自定义模型增长列表")
    void addingCustomModelToCustomBrandGrowsList(
            @ForAll("brandName") String name,
            @ForAll("baseUrl") String baseUrl,
            @ForAll("apiKey") String apiKey,
            @ForAll("customModelEntry") ModelEntry newModel) {

        BrandService service = createBrandService();

        // Create a custom brand (starts with empty model list)
        BrandView created = service.createCustomBrand(new CreateBrandRequest(name, baseUrl, apiKey));
        String brandId = created.id();

        BrandView before = service.getBrand(brandId);
        int sizeBefore = before.models().size();

        List<ModelEntry> updatedModels = new ArrayList<>(before.models());
        updatedModels.add(newModel);

        service.updateModels(brandId, updatedModels);

        BrandView after = service.getBrand(brandId);

        assertThat(after.models()).hasSize(sizeBefore + 1);
        assertThat(after.models()).contains(newModel);
    }

    // ========== Property 11: Removing a custom model shrinks the list (extension brand) ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 11: 移除自定义模型缩减列表")
    void removingCustomModelFromExtensionBrandShrinksList(
            @ForAll("extensionBrand") BrandDefinition brand,
            @ForAll("customModelEntry") ModelEntry customModel) {

        BrandService service = createBrandService();

        // Ensure the brand has a saved config
        service.toggleEnabled(brand.id(), true);

        // First add the custom model
        BrandView current = service.getBrand(brand.id());
        List<ModelEntry> withCustom = new ArrayList<>(current.models());
        withCustom.add(customModel);
        service.updateModels(brand.id(), withCustom);

        BrandView afterAdd = service.getBrand(brand.id());
        assertThat(afterAdd.models()).contains(customModel);

        // Now remove the custom model
        List<ModelEntry> withoutCustom = new ArrayList<>(afterAdd.models());
        withoutCustom.remove(customModel);
        service.updateModels(brand.id(), withoutCustom);

        BrandView afterRemove = service.getBrand(brand.id());

        assertThat(afterRemove.models()).doesNotContain(customModel);
    }

    // ========== Property 11: Removing a custom model from custom brand ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 11: 移除自定义模型缩减列表")
    void removingCustomModelFromCustomBrandShrinksList(
            @ForAll("brandName") String name,
            @ForAll("baseUrl") String baseUrl,
            @ForAll("apiKey") String apiKey,
            @ForAll("customModelEntry") ModelEntry customModel) {

        BrandService service = createBrandService();

        BrandView created = service.createCustomBrand(new CreateBrandRequest(name, baseUrl, apiKey));
        String brandId = created.id();

        // Add the custom model
        service.updateModels(brandId, List.of(customModel));

        BrandView afterAdd = service.getBrand(brandId);
        assertThat(afterAdd.models()).contains(customModel);

        // Remove it
        service.updateModels(brandId, List.of());

        BrandView afterRemove = service.getBrand(brandId);
        assertThat(afterRemove.models()).doesNotContain(customModel);
    }

    // ========== Property 12: Preset models have custom=false, user-added have custom=true ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 12: ModelEntry 自定义标记正确性")
    void presetModelsHaveCustomFalseAndUserAddedHaveCustomTrue(
            @ForAll("extensionBrand") BrandDefinition brand,
            @ForAll("customModelEntry") ModelEntry userModel) {

        BrandService service = createBrandService();

        // Ensure the brand has a saved config
        service.toggleEnabled(brand.id(), true);

        // Build a combined list: preset models (custom=false) + user model (custom=true)
        List<ModelEntry> presetModels = brand.defaultModels();
        List<ModelEntry> combined = new ArrayList<>(presetModels);
        combined.add(userModel);

        service.updateModels(brand.id(), combined);

        BrandView view = service.getBrand(brand.id());

        // Verify preset models have custom=false
        for (ModelEntry preset : presetModels) {
            assertThat(view.models())
                .filteredOn(m -> m.id().equals(preset.id()))
                .allMatch(m -> !m.custom(),
                    "Preset model '%s' should have custom=false".formatted(preset.id()));
        }

        // Verify user-added model has custom=true
        assertThat(view.models())
            .filteredOn(m -> m.id().equals(userModel.id()))
            .allMatch(ModelEntry::custom,
                "User-added model '%s' should have custom=true".formatted(userModel.id()));
    }

    // ========== Property 12: All preset models in definition have custom=false ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 12: ModelEntry 自定义标记正确性")
    void allPresetModelsInDefinitionHaveCustomFalse(
            @ForAll("extensionBrand") BrandDefinition brand) {

        // Verify directly from the brand definition
        for (ModelEntry model : brand.defaultModels()) {
            assertThat(model.custom())
                .as("Preset model '%s' in brand '%s' should have custom=false",
                    model.id(), brand.name())
                .isFalse();
        }

        // Also verify via BrandService
        BrandService service = createBrandService();
        BrandView view = service.getBrand(brand.id());

        for (ModelEntry model : view.models()) {
            if (!model.custom()) {
                // This is a preset model — verify it exists in the definition
                assertThat(brand.defaultModels())
                    .extracting(ModelEntry::id)
                    .contains(model.id());
            }
        }
    }

    // ========== Arbitrary Providers ==========

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
    Arbitrary<ModelEntry> customModelEntry() {
        Arbitrary<String> ids = Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(20)
            .map(s -> "custom-model-" + s);

        Arbitrary<String> names = Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(20)
            .map(s -> "Custom Model " + s);

        Arbitrary<Integer> contextWindows = Arbitraries.integers().between(1000, 2_000_000);
        Arbitrary<Integer> maxTokensList = Arbitraries.integers().between(1000, 128_000);

        return Combinators.combine(ids, names, contextWindows, maxTokensList)
            .as((id, name, ctx, max) -> new ModelEntry(id, name, ctx, max, true));
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
