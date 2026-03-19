package com.pi.chat.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pi.chat.model.ConnectionStatus;
import com.pi.chat.model.ProviderConfig;
import com.pi.chat.util.ApiKeyEncryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Repository for managing ProviderConfig persistence.
 * 
 * <p>Stores provider configurations in a JSON file (providers.json) with
 * encrypted API keys. Provides thread-safe CRUD operations.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>1.2 - Store Provider_Config including provider type, API Key, and optional Base URL</li>
 *   <li>1.5 - Remove provider and its associated API Key from storage</li>
 *   <li>10.1 - Store Provider_Config data in a secure configuration file</li>
 *   <li>10.2 - API keys are encrypted for storage</li>
 * </ul>
 */
public class ProviderConfigRepository {
    
    private static final Logger log = LoggerFactory.getLogger(ProviderConfigRepository.class);
    
    private final Path configFile;
    private final ApiKeyEncryption encryption;
    private final ObjectMapper objectMapper;
    private final Map<String, ProviderConfig> providers;
    private final ReadWriteLock lock;
    
    /**
     * Creates a new ProviderConfigRepository.
     * 
     * @param configFile    Path to the providers.json configuration file
     * @param encryptionKey Encryption key for API key storage
     */
    public ProviderConfigRepository(Path configFile, String encryptionKey) {
        this.configFile = configFile;
        this.encryption = new ApiKeyEncryption(encryptionKey);
        this.objectMapper = createObjectMapper();
        this.providers = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        
        loadFromFile();
    }
    
    /**
     * Retrieves all provider configurations.
     * 
     * @return List of all provider configurations (with decrypted API keys)
     */
    public List<ProviderConfig> findAll() {
        lock.readLock().lock();
        try {
            return providers.values().stream()
                .map(this::decryptApiKey)
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Finds a provider configuration by ID.
     * 
     * @param id Provider ID
     * @return Optional containing the provider if found (with decrypted API key)
     */
    public Optional<ProviderConfig> findById(String id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(providers.get(id))
                .map(this::decryptApiKey);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Finds provider configurations by type.
     * 
     * @param type Provider type (e.g., "anthropic", "openai")
     * @return List of providers matching the type
     */
    public List<ProviderConfig> findByType(String type) {
        lock.readLock().lock();
        try {
            return providers.values().stream()
                .filter(p -> p.type().equals(type))
                .map(this::decryptApiKey)
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Saves a provider configuration.
     * 
     * <p>If a provider with the same ID exists, it will be updated.
     * API keys are encrypted before storage.
     * 
     * @param config Provider configuration to save
     * @return Saved provider configuration
     */
    public ProviderConfig save(ProviderConfig config) {
        lock.writeLock().lock();
        try {
            ProviderConfig encrypted = encryptApiKey(config);
            providers.put(config.id(), encrypted);
            saveToFile();
            return decryptApiKey(encrypted);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Deletes a provider configuration by ID.
     * 
     * @param id Provider ID to delete
     * @return true if the provider was deleted, false if not found
     */
    public boolean deleteById(String id) {
        lock.writeLock().lock();
        try {
            ProviderConfig removed = providers.remove(id);
            if (removed != null) {
                saveToFile();
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Checks if a provider with the given ID exists.
     * 
     * @param id Provider ID
     * @return true if the provider exists
     */
    public boolean existsById(String id) {
        lock.readLock().lock();
        try {
            return providers.containsKey(id);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Returns the count of stored providers.
     * 
     * @return Number of providers
     */
    public long count() {
        lock.readLock().lock();
        try {
            return providers.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Deletes all provider configurations.
     */
    public void deleteAll() {
        lock.writeLock().lock();
        try {
            providers.clear();
            saveToFile();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Reloads configurations from the file.
     */
    public void reload() {
        lock.writeLock().lock();
        try {
            providers.clear();
            loadFromFile();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void loadFromFile() {
        if (!Files.exists(configFile)) {
            log.info("Provider config file does not exist, starting with empty configuration: {}", configFile);
            return;
        }
        
        try {
            String content = Files.readString(configFile);
            if (content.isBlank()) {
                log.info("Provider config file is empty: {}", configFile);
                return;
            }
            
            ProvidersFile file = objectMapper.readValue(content, ProvidersFile.class);
            if (file.providers() != null) {
                for (ProviderConfigDto dto : file.providers()) {
                    ProviderConfig config = dto.toProviderConfig();
                    providers.put(config.id(), config);
                }
            }
            log.info("Loaded {} providers from {}", providers.size(), configFile);
        } catch (IOException e) {
            log.error("Failed to load provider config file: {}", configFile, e);
            throw new ProviderConfigException("Failed to load provider configurations", e);
        }
    }
    
    private void saveToFile() {
        try {
            // Ensure parent directory exists
            Path parent = configFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            
            List<ProviderConfigDto> dtos = providers.values().stream()
                .map(ProviderConfigDto::fromProviderConfig)
                .toList();
            
            ProvidersFile file = new ProvidersFile(dtos);
            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(file);
            Files.writeString(configFile, content);
            log.debug("Saved {} providers to {}", providers.size(), configFile);
        } catch (IOException e) {
            log.error("Failed to save provider config file: {}", configFile, e);
            throw new ProviderConfigException("Failed to save provider configurations", e);
        }
    }
    
    private ProviderConfig encryptApiKey(ProviderConfig config) {
        if (config.apiKey() == null || ApiKeyEncryption.isEncrypted(config.apiKey())) {
            return config;
        }
        
        String encryptedKey = encryption.encrypt(config.apiKey());
        return new ProviderConfig(
            config.id(),
            config.type(),
            config.name(),
            encryptedKey,
            config.baseUrl(),
            config.createdAt(),
            config.updatedAt(),
            config.status()
        );
    }
    
    private ProviderConfig decryptApiKey(ProviderConfig config) {
        if (config.apiKey() == null || !ApiKeyEncryption.isEncrypted(config.apiKey())) {
            return config;
        }
        
        String decryptedKey = encryption.decrypt(config.apiKey());
        return new ProviderConfig(
            config.id(),
            config.type(),
            config.name(),
            decryptedKey,
            config.baseUrl(),
            config.createdAt(),
            config.updatedAt(),
            config.status()
        );
    }
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    
    /**
     * Root structure of the providers.json file.
     */
    private record ProvidersFile(
        @JsonProperty("providers") List<ProviderConfigDto> providers
    ) {
        ProvidersFile {
            if (providers == null) {
                providers = new ArrayList<>();
            }
        }
    }
    
    /**
     * DTO for JSON serialization of ProviderConfig.
     */
    private record ProviderConfigDto(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("name") String name,
        @JsonProperty("apiKey") String apiKey,
        @JsonProperty("baseUrl") String baseUrl,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt,
        @JsonProperty("status") ConnectionStatus status
    ) {
        static ProviderConfigDto fromProviderConfig(ProviderConfig config) {
            return new ProviderConfigDto(
                config.id(),
                config.type(),
                config.name(),
                config.apiKey(),
                config.baseUrl(),
                config.createdAt(),
                config.updatedAt(),
                config.status()
            );
        }
        
        ProviderConfig toProviderConfig() {
            return new ProviderConfig(
                id,
                type,
                name,
                apiKey,
                baseUrl,
                createdAt != null ? createdAt : Instant.now(),
                updatedAt != null ? updatedAt : Instant.now(),
                status != null ? status : ConnectionStatus.UNKNOWN
            );
        }
    }
    
    /**
     * Exception thrown when provider configuration operations fail.
     */
    public static class ProviderConfigException extends RuntimeException {
        public ProviderConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
