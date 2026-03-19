package com.pi.chat.service;

import com.pi.chat.dto.CreateProviderRequest;
import com.pi.chat.dto.UpdateProviderRequest;
import com.pi.chat.dto.ValidationResult;
import com.pi.chat.exception.ProviderNotFoundException;
import com.pi.chat.model.ConnectionStatus;
import com.pi.chat.model.ProviderConfig;
import com.pi.chat.repository.ProviderConfigRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ProviderService.
 * 
 * <p>Tests cover:
 * <ul>
 *   <li>Provider CRUD operations</li>
 *   <li>API key format validation</li>
 *   <li>Unique ID generation</li>
 *   <li>Error handling</li>
 * </ul>
 */
class ProviderServiceTest {
    
    private static final String ENCRYPTION_KEY = "test-encryption-key-32-chars!!";
    
    @TempDir
    Path tempDir;
    
    private ProviderConfigRepository repository;
    private ProviderService service;
    
    @BeforeEach
    void setUp() {
        Path configFile = tempDir.resolve("providers.json");
        repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
        service = new ProviderService(repository);
    }
    
    @Nested
    @DisplayName("listProviders")
    class ListProvidersTests {
        
        @Test
        @DisplayName("should return empty list when no providers configured")
        void shouldReturnEmptyListWhenNoProviders() {
            List<ProviderConfig> providers = service.listProviders();
            
            assertThat(providers).isEmpty();
        }
        
        @Test
        @DisplayName("should return all configured providers")
        void shouldReturnAllProviders() {
            // Create two providers
            service.createProvider(new CreateProviderRequest(
                "anthropic", "Anthropic Main", "sk-ant-api03-validkey123456789012345678901234567890", null));
            service.createProvider(new CreateProviderRequest(
                "openai", "OpenAI Main", "sk-validopenaikey1234567890123456789012345678901234", null));
            
            List<ProviderConfig> providers = service.listProviders();
            
            assertThat(providers).hasSize(2);
            assertThat(providers).extracting(ProviderConfig::type)
                .containsExactlyInAnyOrder("anthropic", "openai");
        }
    }
    
    @Nested
    @DisplayName("createProvider")
    class CreateProviderTests {
        
        @Test
        @DisplayName("should create provider with generated ID")
        void shouldCreateProviderWithGeneratedId() {
            CreateProviderRequest request = new CreateProviderRequest(
                "anthropic", "My Anthropic", "sk-ant-api03-validkey123456789012345678901234567890", null);
            
            ProviderConfig created = service.createProvider(request);
            
            assertThat(created.id()).isEqualTo("anthropic-1");
            assertThat(created.type()).isEqualTo("anthropic");
            assertThat(created.name()).isEqualTo("My Anthropic");
            assertThat(created.apiKey()).isEqualTo("sk-ant-api03-validkey123456789012345678901234567890");
            assertThat(created.status()).isEqualTo(ConnectionStatus.UNKNOWN);
            assertThat(created.createdAt()).isNotNull();
            assertThat(created.updatedAt()).isNotNull();
        }
        
        @Test
        @DisplayName("should generate sequential IDs for same provider type")
        void shouldGenerateSequentialIds() {
            service.createProvider(new CreateProviderRequest(
                "anthropic", "First", "sk-ant-api03-validkey123456789012345678901234567890", null));
            service.createProvider(new CreateProviderRequest(
                "anthropic", "Second", "sk-ant-api03-validkey123456789012345678901234567891", null));
            
            List<ProviderConfig> providers = service.listProviders();
            
            assertThat(providers).extracting(ProviderConfig::id)
                .containsExactlyInAnyOrder("anthropic-1", "anthropic-2");
        }
        
        @Test
        @DisplayName("should generate independent IDs for different provider types")
        void shouldGenerateIndependentIds() {
            service.createProvider(new CreateProviderRequest(
                "anthropic", "Anthropic", "sk-ant-api03-validkey123456789012345678901234567890", null));
            service.createProvider(new CreateProviderRequest(
                "openai", "OpenAI", "sk-validopenaikey1234567890123456789012345678901234", null));
            
            List<ProviderConfig> providers = service.listProviders();
            
            assertThat(providers).extracting(ProviderConfig::id)
                .containsExactlyInAnyOrder("anthropic-1", "openai-1");
        }
        
        @Test
        @DisplayName("should store optional base URL")
        void shouldStoreBaseUrl() {
            CreateProviderRequest request = new CreateProviderRequest(
                "openai", "Custom OpenAI", "sk-validopenaikey1234567890123456789012345678901234", 
                "https://custom.openai.com/v1");
            
            ProviderConfig created = service.createProvider(request);
            
            assertThat(created.baseUrl()).isEqualTo("https://custom.openai.com/v1");
        }
        
        @Test
        @DisplayName("should reject invalid provider type")
        void shouldRejectInvalidProviderType() {
            CreateProviderRequest request = new CreateProviderRequest(
                "invalid-type", "Invalid", "sk-somekey1234567890", null);
            
            assertThatThrownBy(() -> service.createProvider(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid provider type");
        }
        
        @Test
        @DisplayName("should reject empty API key")
        void shouldRejectEmptyApiKey() {
            CreateProviderRequest request = new CreateProviderRequest(
                "anthropic", "Anthropic", "", null);
            
            assertThatThrownBy(() -> service.createProvider(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        }
        
        @Test
        @DisplayName("should reject null request")
        void shouldRejectNullRequest() {
            assertThatThrownBy(() -> service.createProvider(null))
                .isInstanceOf(NullPointerException.class);
        }
    }
    
    @Nested
    @DisplayName("getProvider")
    class GetProviderTests {
        
        @Test
        @DisplayName("should return provider by ID")
        void shouldReturnProviderById() {
            service.createProvider(new CreateProviderRequest(
                "anthropic", "My Anthropic", "sk-ant-api03-validkey123456789012345678901234567890", null));
            
            ProviderConfig provider = service.getProvider("anthropic-1");
            
            assertThat(provider).isNotNull();
            assertThat(provider.id()).isEqualTo("anthropic-1");
            assertThat(provider.name()).isEqualTo("My Anthropic");
        }
        
        @Test
        @DisplayName("should throw ProviderNotFoundException for unknown ID")
        void shouldThrowNotFoundForUnknownId() {
            assertThatThrownBy(() -> service.getProvider("unknown-id"))
                .isInstanceOf(ProviderNotFoundException.class)
                .hasMessageContaining("unknown-id");
        }
    }
    
    @Nested
    @DisplayName("updateProvider")
    class UpdateProviderTests {
        
        @Test
        @DisplayName("should update provider name")
        void shouldUpdateName() {
            service.createProvider(new CreateProviderRequest(
                "anthropic", "Original Name", "sk-ant-api03-validkey123456789012345678901234567890", null));
            
            ProviderConfig updated = service.updateProvider("anthropic-1", 
                new UpdateProviderRequest("New Name", null, null));
            
            assertThat(updated.name()).isEqualTo("New Name");
            assertThat(updated.apiKey()).isEqualTo("sk-ant-api03-validkey123456789012345678901234567890");
        }
        
        @Test
        @DisplayName("should update API key")
        void shouldUpdateApiKey() {
            service.createProvider(new CreateProviderRequest(
                "anthropic", "Anthropic", "sk-ant-api03-validkey123456789012345678901234567890", null));
            
            ProviderConfig updated = service.updateProvider("anthropic-1",
                new UpdateProviderRequest(null, "sk-ant-api03-newvalidkey12345678901234567890123", null));
            
            assertThat(updated.apiKey()).isEqualTo("sk-ant-api03-newvalidkey12345678901234567890123");
            assertThat(updated.name()).isEqualTo("Anthropic");
        }
        
        @Test
        @DisplayName("should update base URL")
        void shouldUpdateBaseUrl() {
            service.createProvider(new CreateProviderRequest(
                "openai", "OpenAI", "sk-validopenaikey1234567890123456789012345678901234", null));
            
            ProviderConfig updated = service.updateProvider("openai-1",
                new UpdateProviderRequest(null, null, "https://new.api.com/v1"));
            
            assertThat(updated.baseUrl()).isEqualTo("https://new.api.com/v1");
        }
        
        @Test
        @DisplayName("should update multiple fields at once")
        void shouldUpdateMultipleFields() {
            service.createProvider(new CreateProviderRequest(
                "anthropic", "Original", "sk-ant-api03-validkey123456789012345678901234567890", null));
            
            ProviderConfig updated = service.updateProvider("anthropic-1",
                new UpdateProviderRequest("Updated Name", "sk-ant-api03-newvalidkey12345678901234567890123", 
                    "https://custom.api.com"));
            
            assertThat(updated.name()).isEqualTo("Updated Name");
            assertThat(updated.apiKey()).isEqualTo("sk-ant-api03-newvalidkey12345678901234567890123");
            assertThat(updated.baseUrl()).isEqualTo("https://custom.api.com");
        }
        
        @Test
        @DisplayName("should update updatedAt timestamp")
        void shouldUpdateTimestamp() throws InterruptedException {
            ProviderConfig created = service.createProvider(new CreateProviderRequest(
                "anthropic", "Anthropic", "sk-ant-api03-validkey123456789012345678901234567890", null));
            
            Thread.sleep(10); // Ensure time difference
            
            ProviderConfig updated = service.updateProvider("anthropic-1",
                new UpdateProviderRequest("New Name", null, null));
            
            assertThat(updated.updatedAt()).isAfter(created.updatedAt());
            assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        }
        
        @Test
        @DisplayName("should reject invalid API key format on update")
        void shouldRejectInvalidApiKeyOnUpdate() {
            service.createProvider(new CreateProviderRequest(
                "anthropic", "Anthropic", "sk-ant-api03-validkey123456789012345678901234567890", null));
            
            assertThatThrownBy(() -> service.updateProvider("anthropic-1",
                new UpdateProviderRequest(null, "invalid", null)))
                .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("should throw ProviderNotFoundException for unknown ID")
        void shouldThrowNotFoundOnUpdate() {
            assertThatThrownBy(() -> service.updateProvider("unknown-id",
                new UpdateProviderRequest("Name", null, null)))
                .isInstanceOf(ProviderNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("deleteProvider")
    class DeleteProviderTests {
        
        @Test
        @DisplayName("should delete existing provider")
        void shouldDeleteProvider() {
            service.createProvider(new CreateProviderRequest(
                "anthropic", "Anthropic", "sk-ant-api03-validkey123456789012345678901234567890", null));
            
            service.deleteProvider("anthropic-1");
            
            assertThat(service.listProviders()).isEmpty();
        }
        
        @Test
        @DisplayName("should throw ProviderNotFoundException for unknown ID")
        void shouldThrowNotFoundOnDelete() {
            assertThatThrownBy(() -> service.deleteProvider("unknown-id"))
                .isInstanceOf(ProviderNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("validateApiKey")
    class ValidateApiKeyTests {
        
        @Test
        @DisplayName("should validate existing provider's API key")
        void shouldValidateExistingProvider() {
            service.createProvider(new CreateProviderRequest(
                "anthropic", "Anthropic", "sk-ant-api03-validkey123456789012345678901234567890", null));
            
            ValidationResult result = service.validateApiKey("anthropic-1");
            
            assertThat(result.valid()).isTrue();
        }
        
        @Test
        @DisplayName("should throw ProviderNotFoundException for unknown ID")
        void shouldThrowNotFoundOnValidate() {
            assertThatThrownBy(() -> service.validateApiKey("unknown-id"))
                .isInstanceOf(ProviderNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("validateApiKeyFormat")
    class ValidateApiKeyFormatTests {
        
        @Test
        @DisplayName("should accept valid Anthropic API key")
        void shouldAcceptValidAnthropicKey() {
            ValidationResult result = service.validateApiKeyFormat("anthropic", 
                "sk-ant-api03-validkey123456789012345678901234567890");
            
            assertThat(result.valid()).isTrue();
        }
        
        @Test
        @DisplayName("should accept valid OpenAI API key")
        void shouldAcceptValidOpenAIKey() {
            ValidationResult result = service.validateApiKeyFormat("openai",
                "sk-validopenaikey1234567890123456789012345678901234");
            
            assertThat(result.valid()).isTrue();
        }
        
        @Test
        @DisplayName("should accept valid Google API key")
        void shouldAcceptValidGoogleKey() {
            ValidationResult result = service.validateApiKeyFormat("google",
                "AIzaSyValidGoogleApiKey1234567890");
            
            assertThat(result.valid()).isTrue();
        }
        
        @Test
        @DisplayName("should accept valid Mistral API key")
        void shouldAcceptValidMistralKey() {
            ValidationResult result = service.validateApiKeyFormat("mistral",
                "validmistralkey1234567890");
            
            assertThat(result.valid()).isTrue();
        }
        
        @Test
        @DisplayName("should accept valid Bedrock API key")
        void shouldAcceptValidBedrockKey() {
            // Bedrock doesn't have a specific pattern, just length check
            ValidationResult result = service.validateApiKeyFormat("bedrock",
                "AKIAIOSFODNN7EXAMPLE");
            
            assertThat(result.valid()).isTrue();
        }
        
        @Test
        @DisplayName("should reject empty API key")
        void shouldRejectEmptyKey() {
            ValidationResult result = service.validateApiKeyFormat("anthropic", "");
            
            assertThat(result.valid()).isFalse();
            assertThat(result.message()).containsIgnoringCase("empty");
        }
        
        @Test
        @DisplayName("should reject null API key")
        void shouldRejectNullKey() {
            ValidationResult result = service.validateApiKeyFormat("anthropic", null);
            
            assertThat(result.valid()).isFalse();
            assertThat(result.message()).containsIgnoringCase("empty");
        }
        
        @Test
        @DisplayName("should reject too short API key")
        void shouldRejectTooShortKey() {
            ValidationResult result = service.validateApiKeyFormat("anthropic", "short");
            
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("too short"));
        }
        
        @Test
        @DisplayName("should reject Anthropic key with wrong prefix")
        void shouldRejectWrongAnthropicPrefix() {
            ValidationResult result = service.validateApiKeyFormat("anthropic",
                "wrong-prefix-key1234567890123456789012345678901234");
            
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("format"));
        }
        
        @Test
        @DisplayName("should reject OpenAI key with wrong prefix")
        void shouldRejectWrongOpenAIPrefix() {
            ValidationResult result = service.validateApiKeyFormat("openai",
                "wrong-prefix-key1234567890123456789012345678901234");
            
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("format"));
        }
    }
    
    @Nested
    @DisplayName("updateConnectionStatus")
    class UpdateConnectionStatusTests {
        
        @Test
        @DisplayName("should update connection status")
        void shouldUpdateStatus() {
            service.createProvider(new CreateProviderRequest(
                "anthropic", "Anthropic", "sk-ant-api03-validkey123456789012345678901234567890", null));
            
            ProviderConfig updated = service.updateConnectionStatus("anthropic-1", 
                ConnectionStatus.CONNECTED);
            
            assertThat(updated.status()).isEqualTo(ConnectionStatus.CONNECTED);
        }
        
        @Test
        @DisplayName("should throw ProviderNotFoundException for unknown ID")
        void shouldThrowNotFoundOnStatusUpdate() {
            assertThatThrownBy(() -> service.updateConnectionStatus("unknown-id", 
                ConnectionStatus.CONNECTED))
                .isInstanceOf(ProviderNotFoundException.class);
        }
    }
}
