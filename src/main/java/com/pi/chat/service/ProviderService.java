package com.pi.chat.service;

import com.pi.chat.dto.CreateProviderRequest;
import com.pi.chat.dto.UpdateProviderRequest;
import com.pi.chat.dto.ValidationResult;
import com.pi.chat.exception.ProviderNotFoundException;
import com.pi.chat.model.ConnectionStatus;
import com.pi.chat.model.ProviderConfig;
import com.pi.chat.repository.ProviderConfigRepository;
import com.pi.coding.model.CodingModelRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Service for managing LLM provider configurations.
 * 
 * <p>Provides CRUD operations for provider configurations with:
 * <ul>
 *   <li>Unique ID generation (e.g., "anthropic-1", "openai-2")</li>
 *   <li>API key format validation</li>
 *   <li>Synchronization with CodingModelRegistry</li>
 * </ul>
 * 
 * <p>Requirements:
 * <ul>
 *   <li>1.2 - Store Provider_Config including provider type, API Key, and optional Base URL</li>
 *   <li>1.4 - Validate API Key format before saving</li>
 *   <li>1.5 - Remove provider and its associated API Key from storage</li>
 *   <li>1.6 - Display list of configured providers with connection status</li>
 * </ul>
 */
public class ProviderService {
    
    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);
    
    // API key prefix patterns for known providers
    private static final Map<String, Pattern> API_KEY_PATTERNS = Map.of(
        ProviderConfig.TYPE_ANTHROPIC, Pattern.compile("^sk-ant-[a-zA-Z0-9_-]+$"),
        ProviderConfig.TYPE_OPENAI, Pattern.compile("^sk-[a-zA-Z0-9_-]+$"),
        ProviderConfig.TYPE_GOOGLE, Pattern.compile("^AIza[a-zA-Z0-9_-]+$"),
        ProviderConfig.TYPE_MISTRAL, Pattern.compile("^[a-zA-Z0-9]+$")
    );
    
    private final ProviderConfigRepository repository;
    private final CodingModelRegistry modelRegistry;
    
    // Counter for generating unique IDs per provider type
    private final Map<String, AtomicInteger> idCounters = new ConcurrentHashMap<>();
    
    /**
     * Creates a new ProviderService.
     * 
     * @param repository    The provider configuration repository
     * @param modelRegistry The coding model registry (may be null if not yet configured)
     */
    public ProviderService(ProviderConfigRepository repository, CodingModelRegistry modelRegistry) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.modelRegistry = modelRegistry;
        initializeIdCounters();
    }
    
    /**
     * Creates a new ProviderService without model registry.
     * 
     * @param repository The provider configuration repository
     */
    public ProviderService(ProviderConfigRepository repository) {
        this(repository, null);
    }
    
    /**
     * Lists all configured providers.
     * 
     * @return List of all provider configurations
     */
    public List<ProviderConfig> listProviders() {
        return repository.findAll();
    }
    
    /**
     * Gets a provider by ID.
     * 
     * @param providerId The provider ID
     * @return The provider configuration
     * @throws ProviderNotFoundException if the provider is not found
     */
    public ProviderConfig getProvider(String providerId) {
        return repository.findById(providerId)
            .orElseThrow(() -> new ProviderNotFoundException(providerId));
    }
    
    /**
     * Creates a new provider configuration.
     * 
     * <p>Generates a unique ID in the format "{type}-{number}" (e.g., "anthropic-1").
     * Validates the API key format and syncs to the model registry.
     * 
     * @param request The create request
     * @return The created provider configuration
     * @throws IllegalArgumentException if the provider type is invalid or API key format is invalid
     */
    public ProviderConfig createProvider(CreateProviderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        
        // Validate provider type
        if (!ProviderConfig.isValidType(request.type())) {
            throw new IllegalArgumentException("Invalid provider type: " + request.type());
        }
        
        // Validate API key format
        ValidationResult validation = validateApiKeyFormat(request.type(), request.apiKey());
        if (!validation.valid()) {
            throw new IllegalArgumentException(validation.message());
        }
        
        // Generate unique ID
        String id = generateUniqueId(request.type());
        
        // Create provider config
        Instant now = Instant.now();
        ProviderConfig config = new ProviderConfig(
            id,
            request.type(),
            request.name(),
            request.apiKey(),
            request.baseUrl(),
            now,
            now,
            ConnectionStatus.UNKNOWN
        );
        
        // Save to repository
        ProviderConfig saved = repository.save(config);
        log.info("Created provider: {} (type: {})", id, request.type());
        
        // Sync to model registry
        syncToModelRegistry(saved);
        
        return saved;
    }
    
    /**
     * Updates an existing provider configuration.
     * 
     * <p>Only non-null fields in the request will be updated.
     * 
     * @param providerId The provider ID to update
     * @param request    The update request
     * @return The updated provider configuration
     * @throws ProviderNotFoundException if the provider is not found
     * @throws IllegalArgumentException  if the API key format is invalid
     */
    public ProviderConfig updateProvider(String providerId, UpdateProviderRequest request) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        
        // Get existing provider
        ProviderConfig existing = getProvider(providerId);
        
        // Validate API key format if provided
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            ValidationResult validation = validateApiKeyFormat(existing.type(), request.apiKey());
            if (!validation.valid()) {
                throw new IllegalArgumentException(validation.message());
            }
        }
        
        // Update fields
        ProviderConfig updated = existing.withUpdates(
            request.name(),
            request.apiKey(),
            request.baseUrl()
        );
        
        // Save to repository
        ProviderConfig saved = repository.save(updated);
        log.info("Updated provider: {}", providerId);
        
        // Sync to model registry
        syncToModelRegistry(saved);
        
        return saved;
    }
    
    /**
     * Deletes a provider configuration.
     * 
     * @param providerId The provider ID to delete
     * @throws ProviderNotFoundException if the provider is not found
     */
    public void deleteProvider(String providerId) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        
        // Verify provider exists
        ProviderConfig existing = getProvider(providerId);
        
        // Delete from repository
        boolean deleted = repository.deleteById(providerId);
        if (!deleted) {
            throw new ProviderNotFoundException(providerId);
        }
        
        log.info("Deleted provider: {}", providerId);
        
        // Remove from model registry
        removeFromModelRegistry(existing);
    }
    
    /**
     * Validates the API key for a provider.
     * 
     * <p>Performs format validation. Does not make actual API calls.
     * 
     * @param providerId The provider ID to validate
     * @return Validation result
     * @throws ProviderNotFoundException if the provider is not found
     */
    public ValidationResult validateApiKey(String providerId) {
        ProviderConfig config = getProvider(providerId);
        return validateApiKeyFormat(config.type(), config.apiKey());
    }
    
    /**
     * Validates API key format for a given provider type.
     * 
     * @param providerType The provider type
     * @param apiKey       The API key to validate
     * @return Validation result
     */
    public ValidationResult validateApiKeyFormat(String providerType, String apiKey) {
        List<String> errors = new ArrayList<>();
        
        // Check for null or empty
        if (apiKey == null || apiKey.isBlank()) {
            return ValidationResult.failure("API key cannot be empty");
        }
        
        // Check minimum length
        if (apiKey.length() < 10) {
            errors.add("API key is too short (minimum 10 characters)");
        }
        
        // Check provider-specific format
        Pattern pattern = API_KEY_PATTERNS.get(providerType);
        if (pattern != null && !pattern.matcher(apiKey).matches()) {
            String expectedFormat = getExpectedFormat(providerType);
            errors.add("API key format does not match expected pattern for " + providerType + 
                       ". Expected format: " + expectedFormat);
        }
        
        if (!errors.isEmpty()) {
            return ValidationResult.failure("API key validation failed", errors);
        }
        
        return ValidationResult.success("API key format is valid");
    }

    
    /**
     * Gets the expected API key format description for a provider type.
     */
    private String getExpectedFormat(String providerType) {
        return switch (providerType) {
            case ProviderConfig.TYPE_ANTHROPIC -> "sk-ant-*";
            case ProviderConfig.TYPE_OPENAI -> "sk-*";
            case ProviderConfig.TYPE_GOOGLE -> "AIza*";
            case ProviderConfig.TYPE_MISTRAL -> "alphanumeric string";
            default -> "provider-specific format";
        };
    }
    
    /**
     * Generates a unique ID for a new provider.
     * 
     * <p>Format: "{type}-{number}" (e.g., "anthropic-1", "openai-2")
     */
    private String generateUniqueId(String type) {
        AtomicInteger counter = idCounters.computeIfAbsent(type, k -> new AtomicInteger(0));
        int number = counter.incrementAndGet();
        String id = type + "-" + number;
        
        // Ensure uniqueness (in case of gaps from deletions)
        while (repository.existsById(id)) {
            number = counter.incrementAndGet();
            id = type + "-" + number;
        }
        
        return id;
    }
    
    /**
     * Initializes ID counters based on existing providers.
     */
    private void initializeIdCounters() {
        for (ProviderConfig config : repository.findAll()) {
            String id = config.id();
            String type = config.type();
            
            // Extract number from ID (e.g., "anthropic-3" -> 3)
            if (id.startsWith(type + "-")) {
                try {
                    int number = Integer.parseInt(id.substring(type.length() + 1));
                    AtomicInteger counter = idCounters.computeIfAbsent(type, k -> new AtomicInteger(0));
                    counter.updateAndGet(current -> Math.max(current, number));
                } catch (NumberFormatException e) {
                    // Ignore non-numeric suffixes
                }
            }
        }
    }
    
    /**
     * Syncs a provider configuration to the CodingModelRegistry.
     * 
     * <p>Registers the provider as a dynamic provider so its models
     * become available through the registry.
     */
    private void syncToModelRegistry(ProviderConfig config) {
        if (modelRegistry == null) {
            log.debug("Model registry not available, skipping sync for provider: {}", config.id());
            return;
        }
        
        try {
            // Create a dynamic provider config for the registry
            com.pi.coding.model.ProviderConfig registryConfig = new com.pi.coding.model.ProviderConfig(
                config.type(),  // Use type as the registry provider ID
                config.name(),
                config.baseUrl(),
                null,  // headers
                config.apiKey(),
                null   // models - will use built-in models
            );
            
            modelRegistry.registerProvider(registryConfig);
            log.debug("Synced provider to model registry: {} (type: {})", config.id(), config.type());
        } catch (Exception e) {
            log.warn("Failed to sync provider to model registry: {}", config.id(), e);
        }
    }
    
    /**
     * Removes a provider from the CodingModelRegistry.
     */
    private void removeFromModelRegistry(ProviderConfig config) {
        if (modelRegistry == null) {
            log.debug("Model registry not available, skipping removal for provider: {}", config.id());
            return;
        }
        
        try {
            // Check if there are other providers of the same type
            long sameTypeCount = repository.findByType(config.type()).size();
            
            // Only unregister if this was the last provider of this type
            if (sameTypeCount == 0) {
                modelRegistry.unregisterProvider(config.type());
                log.debug("Removed provider from model registry: {} (type: {})", config.id(), config.type());
            } else {
                log.debug("Other providers of type {} exist, not removing from registry", config.type());
            }
        } catch (Exception e) {
            log.warn("Failed to remove provider from model registry: {}", config.id(), e);
        }
    }
    
    /**
     * Updates the connection status of a provider.
     * 
     * @param providerId The provider ID
     * @param status     The new connection status
     * @return The updated provider configuration
     * @throws ProviderNotFoundException if the provider is not found
     */
    public ProviderConfig updateConnectionStatus(String providerId, ConnectionStatus status) {
        ProviderConfig existing = getProvider(providerId);
        ProviderConfig updated = existing.withStatus(status);
        return repository.save(updated);
    }
}
