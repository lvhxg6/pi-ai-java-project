package com.pi.chat.repository;

import com.pi.chat.model.ConnectionStatus;
import com.pi.chat.model.ProviderConfig;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for backward compatibility of old-format JSON.
 *
 * <p>Feature: settings-model-brands, Property 21: 旧格式 JSON 向后兼容
 *
 * <p><b>Validates: Requirements 8.4, 10.3</b>
 *
 * <p>For any old-format ProviderConfig JSON (without {@code enabled}, {@code models},
 * {@code apiType} fields), loading should successfully create a ProviderConfig object
 * with {@code enabled=true}, {@code models} as empty list, and {@code apiType}
 * correctly inferred from {@code type}.
 */
class BackwardCompatPropertyTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-12345";

    /** Provider types that map to specific apiType values. */
    private static final List<String> ALL_TYPES = List.of(
        "anthropic", "openai", "google", "mistral", "bedrock",
        "amazon-bedrock", "deepseek", "glm", "custom-xyz"
    );

    /**
     * Property 21: Old-format JSON without enabled/models/apiType fields
     * should load with correct defaults.
     *
     * <p><b>Validates: Requirements 8.4, 10.3</b>
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 21: 旧格式 JSON 向后兼容")
    void oldFormatJsonLoadsWithCorrectDefaults(
            @ForAll("oldFormatProviders") OldFormatProvider provider) throws IOException {

        Path tempDir = Files.createTempDirectory("backward-compat-test");
        Path configFile = tempDir.resolve("providers.json");

        try {
            // Build old-format JSON (no enabled, models, apiType fields)
            String json = buildOldFormatJson(provider);
            Files.writeString(configFile, json);

            // Load through repository
            ProviderConfigRepository repository =
                    new ProviderConfigRepository(configFile, ENCRYPTION_KEY);

            List<ProviderConfig> all = repository.findAll();
            assertThat(all).hasSize(1);

            ProviderConfig loaded = all.get(0);

            // Verify identity fields preserved
            assertThat(loaded.id()).isEqualTo(provider.id());
            assertThat(loaded.type()).isEqualTo(provider.type());
            assertThat(loaded.name()).isEqualTo(provider.name());

            // Verify new field defaults
            assertThat(loaded.enabled())
                    .as("enabled should default to true for old-format JSON")
                    .isTrue();

            assertThat(loaded.models())
                    .as("models should default to empty list for old-format JSON")
                    .isNotNull()
                    .isEmpty();

            assertThat(loaded.apiType())
                    .as("apiType should be inferred from type='%s'", provider.type())
                    .isEqualTo(ProviderConfig.inferApiType(provider.type()));
        } finally {
            Files.deleteIfExists(configFile);
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * Property 21 (supplementary): Old-format JSON with explicit status field
     * should preserve the status while still defaulting new fields.
     *
     * <p><b>Validates: Requirements 8.4, 10.3</b>
     */
    @Property(tries = 100)
    @Tag("Feature: settings-model-brands, Property 21: 旧格式 JSON 向后兼容")
    void oldFormatJsonPreservesStatusAndDefaultsNewFields(
            @ForAll("oldFormatProviders") OldFormatProvider provider,
            @ForAll("connectionStatuses") ConnectionStatus status) throws IOException {

        Path tempDir = Files.createTempDirectory("backward-compat-status-test");
        Path configFile = tempDir.resolve("providers.json");

        try {
            String json = buildOldFormatJsonWithStatus(provider, status);
            Files.writeString(configFile, json);

            ProviderConfigRepository repository =
                    new ProviderConfigRepository(configFile, ENCRYPTION_KEY);

            List<ProviderConfig> all = repository.findAll();
            assertThat(all).hasSize(1);

            ProviderConfig loaded = all.get(0);

            // Status preserved
            assertThat(loaded.status()).isEqualTo(status);

            // New fields still get defaults
            assertThat(loaded.enabled()).isTrue();
            assertThat(loaded.models()).isEmpty();
            assertThat(loaded.apiType())
                    .isEqualTo(ProviderConfig.inferApiType(provider.type()));
        } finally {
            Files.deleteIfExists(configFile);
            Files.deleteIfExists(tempDir);
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<OldFormatProvider> oldFormatProviders() {
        Arbitrary<String> ids = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "id-" + s);

        Arbitrary<String> types = Arbitraries.of(ALL_TYPES);

        Arbitrary<String> names = Arbitraries.strings()
                .alpha()
                .ofMinLength(1).ofMaxLength(30)
                .map(s -> "Name-" + s);

        Arbitrary<String> apiKeys = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(10).ofMaxLength(50)
                .map(s -> "sk-" + s);

        Arbitrary<String> baseUrls = Arbitraries.of(
                "https://api.example.com",
                "https://api.openai.com/v1",
                "https://custom.endpoint.io",
                null
        );

        return Combinators.combine(ids, types, names, apiKeys, baseUrls)
                .as(OldFormatProvider::new);
    }

    @Provide
    Arbitrary<ConnectionStatus> connectionStatuses() {
        return Arbitraries.of(ConnectionStatus.values());
    }

    // ========== Helpers ==========

    record OldFormatProvider(String id, String type, String name, String apiKey, String baseUrl) {}

    /**
     * Builds a providers.json string in the OLD format (no enabled, models, apiType).
     */
    private String buildOldFormatJson(OldFormatProvider p) {
        String now = Instant.now().toString();
        String baseUrlField = p.baseUrl() != null
                ? String.format("\"baseUrl\": \"%s\",", escapeJson(p.baseUrl()))
                : "\"baseUrl\": null,";

        return String.format("""
                {
                  "providers": [
                    {
                      "id": "%s",
                      "type": "%s",
                      "name": "%s",
                      "apiKey": "%s",
                      %s
                      "createdAt": "%s",
                      "updatedAt": "%s",
                      "status": "UNKNOWN"
                    }
                  ]
                }
                """,
                escapeJson(p.id()),
                escapeJson(p.type()),
                escapeJson(p.name()),
                escapeJson(p.apiKey()),
                baseUrlField,
                now, now);
    }

    /**
     * Builds old-format JSON with an explicit status value.
     */
    private String buildOldFormatJsonWithStatus(OldFormatProvider p, ConnectionStatus status) {
        String now = Instant.now().toString();
        String baseUrlField = p.baseUrl() != null
                ? String.format("\"baseUrl\": \"%s\",", escapeJson(p.baseUrl()))
                : "\"baseUrl\": null,";

        return String.format("""
                {
                  "providers": [
                    {
                      "id": "%s",
                      "type": "%s",
                      "name": "%s",
                      "apiKey": "%s",
                      %s
                      "createdAt": "%s",
                      "updatedAt": "%s",
                      "status": "%s"
                    }
                  ]
                }
                """,
                escapeJson(p.id()),
                escapeJson(p.type()),
                escapeJson(p.name()),
                escapeJson(p.apiKey()),
                baseUrlField,
                now, now,
                status.name());
    }

    private static String escapeJson(String value) {
        if (value == null) return "null";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
