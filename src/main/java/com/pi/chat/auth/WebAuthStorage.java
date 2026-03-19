package com.pi.chat.auth;

import com.pi.chat.model.ProviderConfig;
import com.pi.chat.repository.ProviderConfigRepository;
import com.pi.coding.auth.ApiKeyCredential;
import com.pi.coding.auth.AuthCredential;
import com.pi.coding.auth.AuthStorage;
import com.pi.coding.auth.AuthStorageBackend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Web application adapter for AuthStorage that integrates with ProviderConfigRepository.
 * 
 * <p>This component bridges the pi-coding-agent SDK's authentication system with
 * the web application's provider configuration storage. It provides API keys from
 * configured providers to the CodingModelRegistry and other SDK components.
 * 
 * <p>Key behaviors:
 * <ul>
 *   <li>Retrieves API keys from ProviderConfigRepository</li>
 *   <li>Does not use OAuth (returns false for isUsingOAuth)</li>
 *   <li>Supports multiple providers with different API keys</li>
 *   <li>Maps provider types to their configured API keys</li>
 * </ul>
 * 
 * <p>Requirements:
 * <ul>
 *   <li>1.3 - Support provider types: Anthropic, OpenAI, Google Gemini, Mistral, Bedrock</li>
 *   <li>2.2 - Load available models from configured providers via Model_Registry</li>
 * </ul>
 */
@Component
public class WebAuthStorage {
    
    private static final Logger log = LoggerFactory.getLogger(WebAuthStorage.class);
    
    private final ProviderConfigRepository repository;
    private final AuthStorage authStorage;
    
    /**
     * Creates a new WebAuthStorage with the given repository.
     * 
     * @param repository The provider configuration repository
     */
    public WebAuthStorage(ProviderConfigRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.authStorage = AuthStorage.withBackend(new RepositoryBackend());
        log.info("WebAuthStorage initialized with ProviderConfigRepository");
    }
    
    /**
     * Gets the API key for a provider type.
     * 
     * <p>Looks up the first configured provider of the given type and returns
     * its API key. If no provider is configured, returns null.
     * 
     * @param provider The provider type (e.g., "anthropic", "openai")
     * @return The API key, or null if not configured
     */
    public String getApiKey(String provider) {
        // First check the underlying AuthStorage (for runtime overrides)
        String key = authStorage.getApiKey(provider);
        if (key != null) {
            return key;
        }
        
        // Fall back to repository lookup
        return repository.findByType(provider).stream()
            .filter(config -> config.apiKey() != null && !config.apiKey().isBlank())
            .map(ProviderConfig::apiKey)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Checks if the provider is using OAuth authentication.
     * 
     * <p>This implementation always returns false as the web application
     * uses API key authentication only.
     * 
     * @param provider The provider type
     * @return Always false (OAuth not supported)
     */
    public boolean isUsingOAuth(String provider) {
        return false;
    }
    
    /**
     * Refreshes the authentication token if needed.
     * 
     * <p>Since this implementation uses API keys (not OAuth), this method
     * simply returns the current API key wrapped in a CompletableFuture.
     * 
     * @param provider The provider type
     * @return A future containing the API key
     */
    public CompletableFuture<String> refreshIfNeeded(String provider) {
        String apiKey = getApiKey(provider);
        if (apiKey == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("No API key configured for provider: " + provider));
        }
        return CompletableFuture.completedFuture(apiKey);
    }
    
    /**
     * Gets the underlying AuthStorage instance.
     * 
     * <p>This can be used for direct integration with SDK components
     * that require an AuthStorage instance.
     * 
     * @return The underlying AuthStorage
     */
    public AuthStorage getAuthStorage() {
        return authStorage;
    }
    
    /**
     * Sets a runtime API key override for a provider.
     * 
     * <p>Runtime overrides take precedence over repository-stored keys.
     * 
     * @param provider The provider type
     * @param apiKey The API key to set
     */
    public void setRuntimeApiKey(String provider, String apiKey) {
        authStorage.setRuntimeApiKey(provider, apiKey);
    }
    
    /**
     * Clears the runtime API key override for a provider.
     * 
     * @param provider The provider type
     */
    public void clearRuntimeApiKey(String provider) {
        authStorage.clearRuntimeApiKey(provider);
    }
    
    /**
     * Checks if an API key is configured for the given provider.
     * 
     * @param provider The provider type
     * @return true if an API key is available
     */
    public boolean hasApiKey(String provider) {
        return getApiKey(provider) != null;
    }
    
    /**
     * AuthStorageBackend implementation that reads from ProviderConfigRepository.
     */
    private class RepositoryBackend implements AuthStorageBackend {
        
        @Override
        public Map<String, AuthCredential> load() {
            Map<String, AuthCredential> credentials = new HashMap<>();
            
            for (ProviderConfig config : repository.findAll()) {
                if (config.apiKey() != null && !config.apiKey().isBlank()) {
                    credentials.put(config.type(), new ApiKeyCredential(config.apiKey()));
                }
            }
            
            log.debug("Loaded {} credentials from repository", credentials.size());
            return credentials;
        }
        
        @Override
        public void save(Map<String, AuthCredential> credentials) {
            // This backend is read-only; credentials are managed through ProviderConfigRepository
            log.debug("Save operation ignored - credentials managed through ProviderConfigRepository");
        }
    }
}
