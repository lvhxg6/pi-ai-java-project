package com.pi.chat.auth;

import com.pi.chat.model.ConnectionStatus;
import com.pi.chat.model.ProviderConfig;
import com.pi.chat.repository.ProviderConfigRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for WebAuthStorage.
 * 
 * <p>Tests the integration between WebAuthStorage and ProviderConfigRepository,
 * verifying that API keys are correctly retrieved and managed.
 * 
 * <p>Validates: Requirements 1.3, 2.2
 */
@DisplayName("WebAuthStorage")
class WebAuthStorageTest {
    
    private static final String ENCRYPTION_KEY = "test-encryption-key-32chars!!";
    
    @TempDir
    Path tempDir;
    
    private ProviderConfigRepository repository;
    private WebAuthStorage webAuthStorage;
    
    @BeforeEach
    void setUp() {
        Path configFile = tempDir.resolve("providers.json");
        repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
        webAuthStorage = new WebAuthStorage(repository);
    }
    
    @Nested
    @DisplayName("getApiKey")
    class GetApiKey {
        
        @Test
        @DisplayName("should return API key for configured provider")
        void shouldReturnApiKeyForConfiguredProvider() {
            // Given
            ProviderConfig config = createProviderConfig("anthropic-1", "anthropic", "sk-test-key-123");
            repository.save(config);
            
            // When
            String apiKey = webAuthStorage.getApiKey("anthropic");
            
            // Then
            assertThat(apiKey).isEqualTo("sk-test-key-123");
        }
        
        @Test
        @DisplayName("should return null for unconfigured provider")
        void shouldReturnNullForUnconfiguredProvider() {
            // When
            String apiKey = webAuthStorage.getApiKey("openai");
            
            // Then
            assertThat(apiKey).isNull();
        }
        
        @Test
        @DisplayName("should return first API key when multiple providers of same type exist")
        void shouldReturnFirstApiKeyWhenMultipleProvidersExist() {
            // Given
            ProviderConfig config1 = createProviderConfig("anthropic-1", "anthropic", "sk-first-key");
            ProviderConfig config2 = createProviderConfig("anthropic-2", "anthropic", "sk-second-key");
            repository.save(config1);
            repository.save(config2);
            
            // When
            String apiKey = webAuthStorage.getApiKey("anthropic");
            
            // Then
            assertThat(apiKey).isIn("sk-first-key", "sk-second-key");
        }
        
        @Test
        @DisplayName("should skip providers with null API key")
        void shouldSkipProvidersWithNullApiKey() {
            // Given
            ProviderConfig configWithoutKey = createProviderConfig("anthropic-1", "anthropic", null);
            ProviderConfig configWithKey = createProviderConfig("anthropic-2", "anthropic", "sk-valid-key");
            repository.save(configWithoutKey);
            repository.save(configWithKey);
            
            // When
            String apiKey = webAuthStorage.getApiKey("anthropic");
            
            // Then
            assertThat(apiKey).isEqualTo("sk-valid-key");
        }
        
        @Test
        @DisplayName("should skip providers with blank API key")
        void shouldSkipProvidersWithBlankApiKey() {
            // Given
            ProviderConfig configWithBlankKey = createProviderConfig("openai-1", "openai", "   ");
            ProviderConfig configWithKey = createProviderConfig("openai-2", "openai", "sk-valid-key");
            repository.save(configWithBlankKey);
            repository.save(configWithKey);
            
            // When
            String apiKey = webAuthStorage.getApiKey("openai");
            
            // Then
            assertThat(apiKey).isEqualTo("sk-valid-key");
        }
        
        @Test
        @DisplayName("should return runtime override when set")
        void shouldReturnRuntimeOverrideWhenSet() {
            // Given
            ProviderConfig config = createProviderConfig("anthropic-1", "anthropic", "sk-stored-key");
            repository.save(config);
            webAuthStorage.setRuntimeApiKey("anthropic", "sk-runtime-key");
            
            // When
            String apiKey = webAuthStorage.getApiKey("anthropic");
            
            // Then
            assertThat(apiKey).isEqualTo("sk-runtime-key");
        }
        
        @Test
        @DisplayName("should return stored key after clearing runtime override")
        void shouldReturnStoredKeyAfterClearingRuntimeOverride() {
            // Given
            ProviderConfig config = createProviderConfig("anthropic-1", "anthropic", "sk-stored-key");
            repository.save(config);
            webAuthStorage.setRuntimeApiKey("anthropic", "sk-runtime-key");
            webAuthStorage.clearRuntimeApiKey("anthropic");
            
            // When
            String apiKey = webAuthStorage.getApiKey("anthropic");
            
            // Then
            assertThat(apiKey).isEqualTo("sk-stored-key");
        }
    }
    
    @Nested
    @DisplayName("isUsingOAuth")
    class IsUsingOAuth {
        
        @Test
        @DisplayName("should always return false")
        void shouldAlwaysReturnFalse() {
            // Given
            ProviderConfig config = createProviderConfig("anthropic-1", "anthropic", "sk-test-key");
            repository.save(config);
            
            // When/Then
            assertThat(webAuthStorage.isUsingOAuth("anthropic")).isFalse();
            assertThat(webAuthStorage.isUsingOAuth("openai")).isFalse();
            assertThat(webAuthStorage.isUsingOAuth("unknown")).isFalse();
        }
    }
    
    @Nested
    @DisplayName("refreshIfNeeded")
    class RefreshIfNeeded {
        
        @Test
        @DisplayName("should return API key for configured provider")
        void shouldReturnApiKeyForConfiguredProvider() throws Exception {
            // Given
            ProviderConfig config = createProviderConfig("anthropic-1", "anthropic", "sk-test-key");
            repository.save(config);
            
            // When
            CompletableFuture<String> future = webAuthStorage.refreshIfNeeded("anthropic");
            
            // Then
            assertThat(future.get()).isEqualTo("sk-test-key");
        }
        
        @Test
        @DisplayName("should fail for unconfigured provider")
        void shouldFailForUnconfiguredProvider() {
            // When
            CompletableFuture<String> future = webAuthStorage.refreshIfNeeded("openai");
            
            // Then
            assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No API key configured for provider: openai");
        }
    }
    
    @Nested
    @DisplayName("hasApiKey")
    class HasApiKey {
        
        @Test
        @DisplayName("should return true for configured provider")
        void shouldReturnTrueForConfiguredProvider() {
            // Given
            ProviderConfig config = createProviderConfig("anthropic-1", "anthropic", "sk-test-key");
            repository.save(config);
            
            // When/Then
            assertThat(webAuthStorage.hasApiKey("anthropic")).isTrue();
        }
        
        @Test
        @DisplayName("should return false for unconfigured provider")
        void shouldReturnFalseForUnconfiguredProvider() {
            // When/Then
            assertThat(webAuthStorage.hasApiKey("openai")).isFalse();
        }
        
        @Test
        @DisplayName("should return false for provider with null API key")
        void shouldReturnFalseForProviderWithNullApiKey() {
            // Given
            ProviderConfig config = createProviderConfig("anthropic-1", "anthropic", null);
            repository.save(config);
            
            // When/Then
            assertThat(webAuthStorage.hasApiKey("anthropic")).isFalse();
        }
    }
    
    @Nested
    @DisplayName("getAuthStorage")
    class GetAuthStorage {
        
        @Test
        @DisplayName("should return non-null AuthStorage instance")
        void shouldReturnNonNullAuthStorageInstance() {
            // When/Then
            assertThat(webAuthStorage.getAuthStorage()).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Multiple Provider Types")
    class MultipleProviderTypes {
        
        @Test
        @DisplayName("should handle multiple provider types independently")
        void shouldHandleMultipleProviderTypesIndependently() {
            // Given
            repository.save(createProviderConfig("anthropic-1", "anthropic", "sk-anthropic-key"));
            repository.save(createProviderConfig("openai-1", "openai", "sk-openai-key"));
            repository.save(createProviderConfig("google-1", "google", "google-api-key"));
            
            // When/Then
            assertThat(webAuthStorage.getApiKey("anthropic")).isEqualTo("sk-anthropic-key");
            assertThat(webAuthStorage.getApiKey("openai")).isEqualTo("sk-openai-key");
            assertThat(webAuthStorage.getApiKey("google")).isEqualTo("google-api-key");
            assertThat(webAuthStorage.getApiKey("mistral")).isNull();
        }
    }
    
    private ProviderConfig createProviderConfig(String id, String type, String apiKey) {
        Instant now = Instant.now();
        return new ProviderConfig(
            id,
            type,
            type + " Provider",
            apiKey,
            null,
            now,
            now,
            ConnectionStatus.UNKNOWN
        );
    }
}
