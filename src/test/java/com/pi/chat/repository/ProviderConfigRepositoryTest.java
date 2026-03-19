package com.pi.chat.repository;

import com.pi.chat.model.ConnectionStatus;
import com.pi.chat.model.ProviderConfig;
import com.pi.chat.util.ApiKeyEncryption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ProviderConfigRepository.
 * 
 * <p>Validates:
 * <ul>
 *   <li>Requirement 1.2 - Store Provider_Config including provider type, API Key, and optional Base URL</li>
 *   <li>Requirement 1.5 - Remove provider and its associated API Key from storage</li>
 *   <li>Requirement 10.1 - Store Provider_Config data in a secure configuration file</li>
 *   <li>Requirement 10.2 - API keys are encrypted for storage</li>
 * </ul>
 */
class ProviderConfigRepositoryTest {
    
    private static final String ENCRYPTION_KEY = "test-encryption-key-12345";
    
    @TempDir
    Path tempDir;
    
    private Path configFile;
    private ProviderConfigRepository repository;
    
    @BeforeEach
    void setUp() {
        configFile = tempDir.resolve("providers.json");
        repository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
    }
    
    @AfterEach
    void tearDown() {
        // Clean up
    }
    
    private ProviderConfig createTestProvider(String id, String type, String name, String apiKey) {
        return ProviderConfig.builder()
            .id(id)
            .type(type)
            .name(name)
            .apiKey(apiKey)
            .status(ConnectionStatus.UNKNOWN)
            .build();
    }
    
    @Nested
    @DisplayName("Save operations")
    class SaveTests {
        
        @Test
        @DisplayName("should save provider configuration")
        void shouldSaveProviderConfig() {
            ProviderConfig config = createTestProvider("anthropic-1", "anthropic", "Anthropic Main", "sk-ant-test-key");
            
            ProviderConfig saved = repository.save(config);
            
            assertThat(saved.id()).isEqualTo("anthropic-1");
            assertThat(saved.type()).isEqualTo("anthropic");
            assertThat(saved.name()).isEqualTo("Anthropic Main");
            assertThat(saved.apiKey()).isEqualTo("sk-ant-test-key"); // Decrypted for return
        }
        
        @Test
        @DisplayName("should persist provider to file")
        void shouldPersistToFile() throws IOException {
            ProviderConfig config = createTestProvider("openai-1", "openai", "OpenAI", "sk-openai-key");
            
            repository.save(config);
            
            assertThat(configFile).exists();
            String content = Files.readString(configFile);
            assertThat(content).contains("openai-1");
            assertThat(content).contains("openai");
            assertThat(content).contains("OpenAI");
        }
        
        @Test
        @DisplayName("should encrypt API key in file")
        void shouldEncryptApiKeyInFile() throws IOException {
            String plainApiKey = "sk-secret-api-key-12345";
            ProviderConfig config = createTestProvider("test-1", "anthropic", "Test", plainApiKey);
            
            repository.save(config);
            
            String content = Files.readString(configFile);
            assertThat(content).doesNotContain(plainApiKey);
            assertThat(content).contains("encrypted:");
        }
        
        @Test
        @DisplayName("should update existing provider")
        void shouldUpdateExistingProvider() {
            ProviderConfig original = createTestProvider("update-test", "anthropic", "Original", "key-1");
            repository.save(original);
            
            ProviderConfig updated = original.withUpdates("Updated Name", "key-2", null);
            repository.save(updated);
            
            Optional<ProviderConfig> retrieved = repository.findById("update-test");
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().name()).isEqualTo("Updated Name");
            assertThat(retrieved.get().apiKey()).isEqualTo("key-2");
        }
    }
    
    @Nested
    @DisplayName("Find operations")
    class FindTests {
        
        @Test
        @DisplayName("should find provider by ID")
        void shouldFindById() {
            ProviderConfig config = createTestProvider("find-test", "google", "Google", "google-key");
            repository.save(config);
            
            Optional<ProviderConfig> found = repository.findById("find-test");
            
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo("find-test");
            assertThat(found.get().type()).isEqualTo("google");
        }
        
        @Test
        @DisplayName("should return empty for non-existent ID")
        void shouldReturnEmptyForNonExistent() {
            Optional<ProviderConfig> found = repository.findById("non-existent");
            
            assertThat(found).isEmpty();
        }
        
        @Test
        @DisplayName("should find all providers")
        void shouldFindAll() {
            repository.save(createTestProvider("p1", "anthropic", "Provider 1", "key1"));
            repository.save(createTestProvider("p2", "openai", "Provider 2", "key2"));
            repository.save(createTestProvider("p3", "google", "Provider 3", "key3"));
            
            List<ProviderConfig> all = repository.findAll();
            
            assertThat(all).hasSize(3);
            assertThat(all).extracting(ProviderConfig::id)
                .containsExactlyInAnyOrder("p1", "p2", "p3");
        }
        
        @Test
        @DisplayName("should find providers by type")
        void shouldFindByType() {
            repository.save(createTestProvider("ant-1", "anthropic", "Anthropic 1", "key1"));
            repository.save(createTestProvider("ant-2", "anthropic", "Anthropic 2", "key2"));
            repository.save(createTestProvider("oai-1", "openai", "OpenAI 1", "key3"));
            
            List<ProviderConfig> anthropicProviders = repository.findByType("anthropic");
            
            assertThat(anthropicProviders).hasSize(2);
            assertThat(anthropicProviders).extracting(ProviderConfig::id)
                .containsExactlyInAnyOrder("ant-1", "ant-2");
        }
        
        @Test
        @DisplayName("should return decrypted API keys")
        void shouldReturnDecryptedApiKeys() {
            String plainKey = "sk-plain-api-key";
            repository.save(createTestProvider("decrypt-test", "anthropic", "Test", plainKey));
            
            Optional<ProviderConfig> found = repository.findById("decrypt-test");
            
            assertThat(found).isPresent();
            assertThat(found.get().apiKey()).isEqualTo(plainKey);
            assertThat(ApiKeyEncryption.isEncrypted(found.get().apiKey())).isFalse();
        }
    }
    
    @Nested
    @DisplayName("Delete operations")
    class DeleteTests {
        
        @Test
        @DisplayName("should delete provider by ID")
        void shouldDeleteById() {
            repository.save(createTestProvider("delete-test", "anthropic", "To Delete", "key"));
            
            boolean deleted = repository.deleteById("delete-test");
            
            assertThat(deleted).isTrue();
            assertThat(repository.findById("delete-test")).isEmpty();
        }
        
        @Test
        @DisplayName("should return false when deleting non-existent provider")
        void shouldReturnFalseForNonExistent() {
            boolean deleted = repository.deleteById("non-existent");
            
            assertThat(deleted).isFalse();
        }
        
        @Test
        @DisplayName("should remove provider from file")
        void shouldRemoveFromFile() throws IOException {
            repository.save(createTestProvider("file-delete", "anthropic", "Test", "key"));
            
            repository.deleteById("file-delete");
            
            String content = Files.readString(configFile);
            assertThat(content).doesNotContain("file-delete");
        }
        
        @Test
        @DisplayName("should delete all providers")
        void shouldDeleteAll() {
            repository.save(createTestProvider("p1", "anthropic", "P1", "k1"));
            repository.save(createTestProvider("p2", "openai", "P2", "k2"));
            
            repository.deleteAll();
            
            assertThat(repository.findAll()).isEmpty();
            assertThat(repository.count()).isZero();
        }
    }
    
    @Nested
    @DisplayName("Existence and count")
    class ExistenceTests {
        
        @Test
        @DisplayName("should check existence by ID")
        void shouldCheckExistence() {
            repository.save(createTestProvider("exists-test", "anthropic", "Test", "key"));
            
            assertThat(repository.existsById("exists-test")).isTrue();
            assertThat(repository.existsById("non-existent")).isFalse();
        }
        
        @Test
        @DisplayName("should count providers")
        void shouldCountProviders() {
            assertThat(repository.count()).isZero();
            
            repository.save(createTestProvider("p1", "anthropic", "P1", "k1"));
            assertThat(repository.count()).isEqualTo(1);
            
            repository.save(createTestProvider("p2", "openai", "P2", "k2"));
            assertThat(repository.count()).isEqualTo(2);
        }
    }
    
    @Nested
    @DisplayName("Persistence and reload")
    class PersistenceTests {
        
        @Test
        @DisplayName("should load existing providers on initialization")
        void shouldLoadExistingProviders() throws IOException {
            // Create initial repository and save data
            repository.save(createTestProvider("persist-1", "anthropic", "Persist Test", "key-123"));
            
            // Create new repository instance pointing to same file
            ProviderConfigRepository newRepository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            
            Optional<ProviderConfig> loaded = newRepository.findById("persist-1");
            assertThat(loaded).isPresent();
            assertThat(loaded.get().name()).isEqualTo("Persist Test");
            assertThat(loaded.get().apiKey()).isEqualTo("key-123");
        }
        
        @Test
        @DisplayName("should reload from file")
        void shouldReloadFromFile() throws IOException {
            repository.save(createTestProvider("reload-test", "anthropic", "Original", "key"));
            
            // Manually modify file
            String content = Files.readString(configFile);
            content = content.replace("Original", "Modified");
            Files.writeString(configFile, content);
            
            repository.reload();
            
            Optional<ProviderConfig> reloaded = repository.findById("reload-test");
            assertThat(reloaded).isPresent();
            assertThat(reloaded.get().name()).isEqualTo("Modified");
        }
        
        @Test
        @DisplayName("should handle empty config file")
        void shouldHandleEmptyConfigFile() throws IOException {
            Files.writeString(configFile, "");
            
            ProviderConfigRepository newRepository = new ProviderConfigRepository(configFile, ENCRYPTION_KEY);
            
            assertThat(newRepository.findAll()).isEmpty();
        }
        
        @Test
        @DisplayName("should handle non-existent config file")
        void shouldHandleNonExistentFile() {
            Path nonExistent = tempDir.resolve("non-existent.json");
            
            ProviderConfigRepository newRepository = new ProviderConfigRepository(nonExistent, ENCRYPTION_KEY);
            
            assertThat(newRepository.findAll()).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("Provider with all fields")
    class FullProviderTests {
        
        @Test
        @DisplayName("should save and retrieve provider with all fields")
        void shouldSaveAndRetrieveFullProvider() {
            Instant now = Instant.now();
            ProviderConfig config = new ProviderConfig(
                "full-test",
                "anthropic",
                "Full Provider",
                "sk-full-api-key",
                "https://custom.api.com",
                now,
                now,
                ConnectionStatus.CONNECTED
            );
            
            repository.save(config);
            Optional<ProviderConfig> retrieved = repository.findById("full-test");
            
            assertThat(retrieved).isPresent();
            ProviderConfig p = retrieved.get();
            assertThat(p.id()).isEqualTo("full-test");
            assertThat(p.type()).isEqualTo("anthropic");
            assertThat(p.name()).isEqualTo("Full Provider");
            assertThat(p.apiKey()).isEqualTo("sk-full-api-key");
            assertThat(p.baseUrl()).isEqualTo("https://custom.api.com");
            assertThat(p.status()).isEqualTo(ConnectionStatus.CONNECTED);
        }
        
        @Test
        @DisplayName("should save provider with null baseUrl")
        void shouldSaveProviderWithNullBaseUrl() {
            ProviderConfig config = ProviderConfig.builder()
                .id("null-url-test")
                .type("openai")
                .name("No Base URL")
                .apiKey("key")
                .baseUrl(null)
                .build();
            
            repository.save(config);
            Optional<ProviderConfig> retrieved = repository.findById("null-url-test");
            
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().baseUrl()).isNull();
        }
    }
    
    @Nested
    @DisplayName("JSON format validation")
    class JsonFormatTests {
        
        @Test
        @DisplayName("should produce valid JSON structure")
        void shouldProduceValidJsonStructure() throws IOException {
            repository.save(createTestProvider("json-test", "anthropic", "JSON Test", "key"));
            
            String content = Files.readString(configFile);
            
            assertThat(content).contains("\"providers\"");
            assertThat(content).contains("\"id\"");
            assertThat(content).contains("\"type\"");
            assertThat(content).contains("\"name\"");
            assertThat(content).contains("\"apiKey\"");
            assertThat(content).contains("\"createdAt\"");
            assertThat(content).contains("\"updatedAt\"");
        }
        
        @Test
        @DisplayName("should format timestamps as ISO-8601")
        void shouldFormatTimestampsAsIso8601() throws IOException {
            repository.save(createTestProvider("time-test", "anthropic", "Time Test", "key"));
            
            String content = Files.readString(configFile);
            
            // ISO-8601 format contains 'T' separator
            assertThat(content).containsPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        }
    }
}
