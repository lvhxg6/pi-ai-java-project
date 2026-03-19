package com.pi.chat.model;

import net.jqwik.api.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for ProviderConfig new field defaults.
 *
 * <p>Feature: settings-model-brands, Property 3: ProviderConfig 新字段默认值
 *
 * <p>Validates: Requirements 8.1, 8.2, 8.3
 *
 * <p>For any newly created ProviderConfig (without explicitly setting new fields),
 * {@code enabled} should be {@code true}, {@code models} should be an empty list,
 * and {@code apiType} should be correctly inferred from the {@code type} field.
 */
class ProviderConfigDefaultsPropertyTest {

    /** Known provider types with their expected apiType mappings. */
    private static final List<String> KNOWN_TYPES = List.of(
        "anthropic", "openai", "google", "mistral", "bedrock", "amazon-bedrock"
    );

    // ========== Property 3: ProviderConfig 新字段默认值 — enabled ==========

    /**
     * Validates: Requirements 8.1
     *
     * When a ProviderConfig is created via the backward-compatible 8-arg constructor,
     * the {@code enabled} field must default to {@code true}.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 3: ProviderConfig 新字段默认值")
    void enabledDefaultsToTrue(@ForAll("providerTypes") String type) {
        Instant now = Instant.now();
        ProviderConfig config = new ProviderConfig(
            "id-1", type, "Name", "key", null, now, now, ConnectionStatus.UNKNOWN
        );

        assertThat(config.enabled())
            .as("enabled should default to true for type=%s", type)
            .isTrue();
    }

    // ========== Property 3: ProviderConfig 新字段默认值 — models ==========

    /**
     * Validates: Requirements 8.2
     *
     * When a ProviderConfig is created via the backward-compatible 8-arg constructor,
     * the {@code models} field must default to an empty list.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 3: ProviderConfig 新字段默认值")
    void modelsDefaultsToEmptyList(@ForAll("providerTypes") String type) {
        Instant now = Instant.now();
        ProviderConfig config = new ProviderConfig(
            "id-1", type, "Name", "key", null, now, now, ConnectionStatus.UNKNOWN
        );

        assertThat(config.models())
            .as("models should default to empty list for type=%s", type)
            .isNotNull()
            .isEmpty();
    }

    /**
     * Validates: Requirements 8.2
     *
     * When a ProviderConfig is created via the full constructor with models=null,
     * the {@code models} field must be normalized to an empty list.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 3: ProviderConfig 新字段默认值")
    void nullModelsNormalizedToEmptyList(@ForAll("providerTypes") String type) {
        Instant now = Instant.now();
        ProviderConfig config = new ProviderConfig(
            "id-1", type, "Name", "key", null, now, now,
            ConnectionStatus.UNKNOWN, true, null, null
        );

        assertThat(config.models())
            .as("null models should be normalized to empty list")
            .isNotNull()
            .isEmpty();
    }

    // ========== Property 3: ProviderConfig 新字段默认值 — apiType inference ==========

    /**
     * Validates: Requirements 8.3
     *
     * When a ProviderConfig is created via the backward-compatible 8-arg constructor,
     * the {@code apiType} field must be correctly inferred from the {@code type} field.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 3: ProviderConfig 新字段默认值")
    void apiTypeInferredFromType(@ForAll("providerTypes") String type) {
        Instant now = Instant.now();
        ProviderConfig config = new ProviderConfig(
            "id-1", type, "Name", "key", null, now, now, ConnectionStatus.UNKNOWN
        );

        String expected = ProviderConfig.inferApiType(type);
        assertThat(config.apiType())
            .as("apiType should be inferred from type=%s", type)
            .isEqualTo(expected);
    }

    /**
     * Validates: Requirements 8.3
     *
     * When a ProviderConfig is created with apiType=null in the full constructor,
     * the {@code apiType} must be inferred from the {@code type} field.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 3: ProviderConfig 新字段默认值")
    void nullApiTypeInferredFromType(@ForAll("providerTypes") String type) {
        Instant now = Instant.now();
        ProviderConfig config = new ProviderConfig(
            "id-1", type, "Name", "key", null, now, now,
            ConnectionStatus.UNKNOWN, true, List.of(), null
        );

        String expected = ProviderConfig.inferApiType(type);
        assertThat(config.apiType())
            .as("null apiType should be inferred from type=%s", type)
            .isEqualTo(expected);
    }

    // ========== inferApiType mapping correctness ==========

    /**
     * Validates: Requirements 8.3
     *
     * The inferApiType static method must produce the correct mapping for every
     * known provider type.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 3: ProviderConfig 新字段默认值")
    void inferApiTypeMappingIsCorrect(@ForAll("knownTypeMappings") TypeMapping mapping) {
        assertThat(ProviderConfig.inferApiType(mapping.type()))
            .as("inferApiType(%s) should return %s", mapping.type(), mapping.expectedApiType())
            .isEqualTo(mapping.expectedApiType());
    }

    /**
     * Validates: Requirements 8.3
     *
     * For any unknown provider type string, inferApiType must fall back to
     * "openai-completions".
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 3: ProviderConfig 新字段默认值")
    void unknownTypeFallsBackToOpenaiCompletions(
            @ForAll("unknownProviderTypes") String unknownType) {
        assertThat(ProviderConfig.inferApiType(unknownType))
            .as("Unknown type '%s' should fall back to openai-completions", unknownType)
            .isEqualTo("openai-completions");
    }

    // ========== Builder defaults ==========

    /**
     * Validates: Requirements 8.1, 8.2, 8.3
     *
     * A ProviderConfig built via the Builder without setting new fields should
     * have the same defaults: enabled=true, models=empty, apiType inferred.
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 3: ProviderConfig 新字段默认值")
    void builderDefaultsMatchExpected(@ForAll("providerTypes") String type) {
        ProviderConfig config = ProviderConfig.builder()
            .id("builder-id")
            .type(type)
            .name("Builder Name")
            .build();

        assertThat(config.enabled()).as("builder enabled default").isTrue();
        assertThat(config.models()).as("builder models default").isEmpty();
        assertThat(config.apiType())
            .as("builder apiType default")
            .isEqualTo(ProviderConfig.inferApiType(type));
    }

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<String> providerTypes() {
        return Arbitraries.of(
            "anthropic", "openai", "google", "mistral", "bedrock",
            "amazon-bedrock", "deepseek", "glm", "custom-provider", "unknown"
        );
    }

    @Provide
    Arbitrary<String> unknownProviderTypes() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(30)
            .filter(s -> !KNOWN_TYPES.contains(s.toLowerCase()));
    }

    @Provide
    Arbitrary<TypeMapping> knownTypeMappings() {
        return Arbitraries.of(
            new TypeMapping("anthropic", "anthropic-messages"),
            new TypeMapping("openai", "openai-responses"),
            new TypeMapping("google", "google-generative-ai"),
            new TypeMapping("mistral", "mistral-conversations"),
            new TypeMapping("bedrock", "bedrock-converse-stream"),
            new TypeMapping("amazon-bedrock", "bedrock-converse-stream")
        );
    }

    /** Simple pair for mapping assertions. */
    record TypeMapping(String type, String expectedApiType) {}
}
