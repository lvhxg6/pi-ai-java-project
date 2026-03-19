package com.pi.chat.provider;

import com.pi.chat.dto.CreateProviderRequest;
import com.pi.chat.dto.UpdateProviderRequest;
import com.pi.chat.dto.ValidationResult;
import com.pi.chat.exception.ProviderNotFoundException;
import com.pi.chat.model.ProviderConfig;
import com.pi.chat.repository.ProviderConfigRepository;
import com.pi.chat.service.ProviderService;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for Provider module.
 * 
 * <p>Tests the following correctness properties:
 * <ul>
 *   <li>Property 1: Provider Configuration Round-Trip</li>
 *   <li>Property 2: Provider Validation Rejects Invalid API Keys</li>
 *   <li>Property 3: Provider Deletion Removes All Data</li>
 *   <li>Property 21: API Key Masking</li>
 * </ul>
 */
class ProviderPropertyTest {
    
    @TempDir
    Path tempDir;
    
    private ProviderConfigRepository repository;
    private ProviderService service;
    
    @BeforeEach
    void setUp() {
        Path configFile = tempDir.resolve("providers.json");
        String encryptionKey = "test-encryption-key-32-chars!!";
        repository = new ProviderConfigRepository(configFile, encryptionKey);
        service = new ProviderService(repository);
    }
    
    // ========== Property 1: Provider Configuration Round-Trip ==========
    
    @Property(tries = 100)
    @Tag("Feature: ai-chat-web, Property 1: Provider Configuration Round-Trip")
    void providerConfigurationRoundTrip(
            @ForAll("validProviderTypes") String type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String name,
            @ForAll("validApiKeys") String apiKey) {
        
        // Create provider
        CreateProviderRequest request = new CreateProviderRequest(type, name, apiKey, null);
        ProviderConfig created = service.createProvider(request);
        
        // Retrieve provider
        ProviderConfig retrieved = service.getProvider(created.id());
        
        // Verify round-trip preserves data
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.type()).isEqualTo(type);
        assertThat(retrieved.name()).isEqualTo(name);
        assertThat(retrieved.apiKey()).isEqualTo(apiKey); // Internal storage has full key
        assertThat(retrieved.createdAt()).isNotNull();
        assertThat(retrieved.updatedAt()).isNotNull();
    }
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 1: Provider Configuration Round-Trip")
    void providerConfigurationRoundTripWithBaseUrl(
            @ForAll("validProviderTypes") String type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String name,
            @ForAll("validApiKeys") String apiKey,
            @ForAll("validBaseUrls") String baseUrl) {
        
        // Create provider with base URL
        CreateProviderRequest request = new CreateProviderRequest(type, name, apiKey, baseUrl);
        ProviderConfig created = service.createProvider(request);
        
        // Retrieve provider
        ProviderConfig retrieved = service.getProvider(created.id());
        
        // Verify base URL preserved
        assertThat(retrieved.baseUrl()).isEqualTo(baseUrl);
    }
    
    // ========== Property 2: Provider Validation Rejects Invalid API Keys ==========
    
    @Property(tries = 100)
    @Tag("Feature: ai-chat-web, Property 2: Provider Validation Rejects Invalid API Keys")
    void rejectsEmptyApiKey(@ForAll("validProviderTypes") String type) {
        // Empty API key should be rejected
        ValidationResult result = service.validateApiKeyFormat(type, "");
        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isNotBlank();
    }
    
    @Property(tries = 100)
    @Tag("Feature: ai-chat-web, Property 2: Provider Validation Rejects Invalid API Keys")
    void rejectsNullApiKey(@ForAll("validProviderTypes") String type) {
        // Null API key should be rejected
        ValidationResult result = service.validateApiKeyFormat(type, null);
        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isNotBlank();
    }
    
    @Property(tries = 100)
    @Tag("Feature: ai-chat-web, Property 2: Provider Validation Rejects Invalid API Keys")
    void rejectsTooShortApiKey(
            @ForAll("validProviderTypes") String type,
            @ForAll @StringLength(min = 1, max = 9) String shortKey) {
        
        // API key shorter than 10 chars should be rejected
        ValidationResult result = service.validateApiKeyFormat(type, shortKey);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 2: Provider Validation Rejects Invalid API Keys")
    void rejectsMalformedAnthropicKey(
            @ForAll @AlphaChars @StringLength(min = 20, max = 50) String randomKey) {
        
        // Anthropic keys must start with "sk-ant-"
        Assume.that(!randomKey.startsWith("sk-ant-"));
        
        ValidationResult result = service.validateApiKeyFormat(
            ProviderConfig.TYPE_ANTHROPIC, randomKey);
        assertThat(result.valid()).isFalse();
    }
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 2: Provider Validation Rejects Invalid API Keys")
    void rejectsMalformedOpenAIKey(
            @ForAll @AlphaChars @StringLength(min = 20, max = 50) String randomKey) {
        
        // OpenAI keys must start with "sk-"
        Assume.that(!randomKey.startsWith("sk-"));
        
        ValidationResult result = service.validateApiKeyFormat(
            ProviderConfig.TYPE_OPENAI, randomKey);
        assertThat(result.valid()).isFalse();
    }
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 2: Provider Validation Rejects Invalid API Keys")
    void createProviderRejectsInvalidApiKey(
            @ForAll("validProviderTypes") String type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String name) {
        
        // Creating provider with empty API key should throw
        CreateProviderRequest request = new CreateProviderRequest(type, name, "", null);
        
        assertThatThrownBy(() -> service.createProvider(request))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 2: Provider Validation Rejects Invalid API Keys")
    void updateProviderRejectsInvalidApiKey(
            @ForAll("validProviderTypes") String type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String name,
            @ForAll("validApiKeys") String validKey) {
        
        // First create a valid provider
        CreateProviderRequest createRequest = new CreateProviderRequest(type, name, validKey, null);
        ProviderConfig created = service.createProvider(createRequest);
        
        // Try to update with invalid API key (too short)
        UpdateProviderRequest updateRequest = new UpdateProviderRequest(null, "short", null);
        
        assertThatThrownBy(() -> service.updateProvider(created.id(), updateRequest))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    // ========== Property 3: Provider Deletion Removes All Data ==========
    
    @Property(tries = 100)
    @Tag("Feature: ai-chat-web, Property 3: Provider Deletion Removes All Data")
    void deletedProviderNotFound(
            @ForAll("validProviderTypes") String type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String name,
            @ForAll("validApiKeys") String apiKey) {
        
        // Create provider
        CreateProviderRequest request = new CreateProviderRequest(type, name, apiKey, null);
        ProviderConfig created = service.createProvider(request);
        String providerId = created.id();
        
        // Delete provider
        service.deleteProvider(providerId);
        
        // Verify provider not found
        assertThatThrownBy(() -> service.getProvider(providerId))
            .isInstanceOf(ProviderNotFoundException.class);
    }
    
    @Property(tries = 100)
    @Tag("Feature: ai-chat-web, Property 3: Provider Deletion Removes All Data")
    void deletedProviderNotInList(
            @ForAll("validProviderTypes") String type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String name,
            @ForAll("validApiKeys") String apiKey) {
        
        // Create provider
        CreateProviderRequest request = new CreateProviderRequest(type, name, apiKey, null);
        ProviderConfig created = service.createProvider(request);
        String providerId = created.id();
        
        // Delete provider
        service.deleteProvider(providerId);
        
        // Verify provider not in list
        List<ProviderConfig> providers = service.listProviders();
        assertThat(providers)
            .extracting(ProviderConfig::id)
            .doesNotContain(providerId);
    }
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 3: Provider Deletion Removes All Data")
    void deleteNonExistentProviderThrows(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String fakeId) {
        
        // Deleting non-existent provider should throw
        assertThatThrownBy(() -> service.deleteProvider(fakeId))
            .isInstanceOf(ProviderNotFoundException.class);
    }
    
    // ========== Property 21: API Key Masking ==========
    
    @Property(tries = 100)
    @Tag("Feature: ai-chat-web, Property 21: API Key Masking")
    void apiKeyMaskingInMaskedResponse(
            @ForAll("validProviderTypes") String type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String name,
            @ForAll("validApiKeys") String apiKey) {
        
        // Create provider
        CreateProviderRequest request = new CreateProviderRequest(type, name, apiKey, null);
        ProviderConfig created = service.createProvider(request);
        
        // Get masked version
        String maskedKey = created.maskedApiKey();
        
        // Verify masking
        assertThat(maskedKey).isNotEqualTo(apiKey);
        assertThat(maskedKey).contains("****");
        assertThat(maskedKey.length()).isLessThan(apiKey.length());
        
        // Verify original key not exposed in masked version
        assertThat(maskedKey).doesNotContain(apiKey.substring(4, apiKey.length() - 4));
    }
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 21: API Key Masking")
    void maskedKeyPreservesPrefix(
            @ForAll("validApiKeys") String apiKey) {
        
        Assume.that(apiKey.length() >= 8);
        
        // Create provider
        CreateProviderRequest request = new CreateProviderRequest(
            ProviderConfig.TYPE_ANTHROPIC, "Test", apiKey, null);
        ProviderConfig created = service.createProvider(request);
        
        // Get masked version
        String maskedKey = created.maskedApiKey();
        
        // Verify prefix preserved (first 4 chars)
        assertThat(maskedKey).startsWith(apiKey.substring(0, 4));
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    Arbitrary<String> validProviderTypes() {
        return Arbitraries.of(
            ProviderConfig.TYPE_ANTHROPIC,
            ProviderConfig.TYPE_OPENAI,
            ProviderConfig.TYPE_GOOGLE,
            ProviderConfig.TYPE_MISTRAL,
            ProviderConfig.TYPE_BEDROCK
        );
    }
    
    @Provide
    Arbitrary<String> validApiKeys() {
        return Arbitraries.oneOf(
            // Anthropic format: sk-ant-*
            Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(20).ofMaxLength(40)
                .map(s -> "sk-ant-" + s),
            // OpenAI format: sk-*
            Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(20).ofMaxLength(40)
                .map(s -> "sk-" + s),
            // Google format: AIza*
            Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(20).ofMaxLength(40)
                .map(s -> "AIza" + s),
            // Mistral format: alphanumeric
            Arbitraries.strings()
                .alpha()
                .ofMinLength(20).ofMaxLength(40)
        );
    }
    
    @Provide
    Arbitrary<String> validBaseUrls() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(5).ofMaxLength(30)
            .map(s -> "https://" + s + ".example.com");
    }
}
