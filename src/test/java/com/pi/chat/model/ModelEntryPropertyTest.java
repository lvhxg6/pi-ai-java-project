package com.pi.chat.model;

import net.jqwik.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for ModelEntry and BrandDefinition.
 *
 * <p>Feature: settings-model-brands, Property 2: 扩展品牌预设模型完整性
 *
 * <p>Validates: Requirements 1.5
 *
 * <p>For any predefined extension brand's any preset ModelEntry,
 * the ModelEntry's {@code id} and {@code name} should be non-empty strings,
 * {@code contextWindow} and {@code maxTokens} should be positive integers.
 */
class ModelEntryPropertyTest {

    /**
     * Extension brands that have preset default models.
     */
    private static final List<BrandDefinition> EXTENSION_BRANDS = List.of(
        BrandDefinitions.DEEPSEEK,
        BrandDefinitions.GLM,
        BrandDefinitions.MINIMAX,
        BrandDefinitions.KIMI
    );

    // ========== Property 2: 扩展品牌预设模型完整性 ==========

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 2: 扩展品牌预设模型完整性")
    void extensionBrandPresetModelHasNonEmptyId(
            @ForAll("extensionBrandModels") ModelEntry model) {
        assertThat(model.id())
            .as("ModelEntry id should be non-empty")
            .isNotNull()
            .isNotBlank();
    }

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 2: 扩展品牌预设模型完整性")
    void extensionBrandPresetModelHasNonEmptyName(
            @ForAll("extensionBrandModels") ModelEntry model) {
        assertThat(model.name())
            .as("ModelEntry name should be non-empty")
            .isNotNull()
            .isNotBlank();
    }

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 2: 扩展品牌预设模型完整性")
    void extensionBrandPresetModelHasPositiveContextWindow(
            @ForAll("extensionBrandModels") ModelEntry model) {
        assertThat(model.contextWindow())
            .as("ModelEntry contextWindow should be positive")
            .isGreaterThan(0);
    }

    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 2: 扩展品牌预设模型完整性")
    void extensionBrandPresetModelHasPositiveMaxTokens(
            @ForAll("extensionBrandModels") ModelEntry model) {
        assertThat(model.maxTokens())
            .as("ModelEntry maxTokens should be positive")
            .isGreaterThan(0);
    }

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<ModelEntry> extensionBrandModels() {
        List<ModelEntry> allModels = EXTENSION_BRANDS.stream()
            .flatMap(brand -> brand.defaultModels().stream())
            .toList();

        return Arbitraries.of(allModels);
    }
}
