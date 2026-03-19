package com.pi.chat.model;

import java.time.Instant;
import java.util.Objects;

/**
 * LLM Provider configuration.
 * 
 * <p>Represents the configuration for an LLM provider including API credentials
 * and connection settings. API keys are stored in encrypted form.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>1.2 - Store Provider_Config including provider type, API Key, and optional Base URL</li>
 *   <li>10.1 - Store Provider_Config data in a secure configuration file</li>
 *   <li>10.2 - API keys are encrypted for storage</li>
 * </ul>
 * 
 * @param id        Unique identifier (e.g., "anthropic-1")
 * @param type      Provider type (anthropic, openai, google, mistral, bedrock)
 * @param name      Display name for the provider
 * @param apiKey    API Key (encrypted when stored)
 * @param baseUrl   Optional custom Base URL for the provider
 * @param createdAt Timestamp when the provider was created
 * @param updatedAt Timestamp when the provider was last updated
 * @param status    Current connection status
 */
public record ProviderConfig(
    String id,
    String type,
    String name,
    String apiKey,
    String baseUrl,
    Instant createdAt,
    Instant updatedAt,
    ConnectionStatus status
) {
    
    /**
     * Supported provider types.
     */
    public static final String TYPE_ANTHROPIC = "anthropic";
    public static final String TYPE_OPENAI = "openai";
    public static final String TYPE_GOOGLE = "google";
    public static final String TYPE_MISTRAL = "mistral";
    public static final String TYPE_BEDROCK = "bedrock";
    
    /**
     * Validates the provider configuration.
     */
    public ProviderConfig {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
    
    /**
     * Creates a new ProviderConfig with updated fields.
     * 
     * @param name    New display name (null to keep existing)
     * @param apiKey  New API key (null to keep existing)
     * @param baseUrl New base URL (null to keep existing)
     * @return Updated ProviderConfig
     */
    public ProviderConfig withUpdates(String name, String apiKey, String baseUrl) {
        return new ProviderConfig(
            this.id,
            this.type,
            name != null ? name : this.name,
            apiKey != null ? apiKey : this.apiKey,
            baseUrl != null ? baseUrl : this.baseUrl,
            this.createdAt,
            Instant.now(),
            this.status
        );
    }
    
    /**
     * Creates a new ProviderConfig with updated connection status.
     * 
     * @param status New connection status
     * @return Updated ProviderConfig
     */
    public ProviderConfig withStatus(ConnectionStatus status) {
        return new ProviderConfig(
            this.id,
            this.type,
            this.name,
            this.apiKey,
            this.baseUrl,
            this.createdAt,
            Instant.now(),
            status
        );
    }
    
    /**
     * Returns a masked version of the API key for display purposes.
     * 
     * <p>The masked key shows the first 4 characters, followed by asterisks,
     * and the last 4 characters. For example: "sk-a****wxyz"
     * 
     * <p>Requirements:
     * <ul>
     *   <li>10.2 - API keys should not be exposed in logs or responses</li>
     *   <li>10.3 - Display masked API key in UI</li>
     * </ul>
     * 
     * @return Masked API key string
     */
    public String maskedApiKey() {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        
        int prefixLen = 4;
        int suffixLen = 4;
        
        if (apiKey.length() <= prefixLen + suffixLen) {
            return apiKey.substring(0, 2) + "****" + apiKey.substring(apiKey.length() - 2);
        }
        
        return apiKey.substring(0, prefixLen) + "****" + apiKey.substring(apiKey.length() - suffixLen);
    }
    
    /**
     * Checks if the provider type is valid.
     * 
     * @param type Provider type to check
     * @return true if the type is supported
     */
    public static boolean isValidType(String type) {
        return TYPE_ANTHROPIC.equals(type) ||
               TYPE_OPENAI.equals(type) ||
               TYPE_GOOGLE.equals(type) ||
               TYPE_MISTRAL.equals(type) ||
               TYPE_BEDROCK.equals(type);
    }
    
    /**
     * Creates a builder for constructing ProviderConfig instances.
     * 
     * @return New builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for ProviderConfig.
     */
    public static class Builder {
        private String id;
        private String type;
        private String name;
        private String apiKey;
        private String baseUrl;
        private Instant createdAt;
        private Instant updatedAt;
        private ConnectionStatus status = ConnectionStatus.UNKNOWN;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }
        
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public Builder status(ConnectionStatus status) {
            this.status = status;
            return this;
        }
        
        public ProviderConfig build() {
            Instant now = Instant.now();
            return new ProviderConfig(
                id,
                type,
                name,
                apiKey,
                baseUrl,
                createdAt != null ? createdAt : now,
                updatedAt != null ? updatedAt : now,
                status
            );
        }
    }
}
