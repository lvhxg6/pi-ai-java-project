package com.pi.chat.model;

import java.time.Instant;
import java.util.List;
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
 *   <li>8.1 - New enabled field with default true</li>
 *   <li>8.2 - New models field (List&lt;ModelEntry&gt;)</li>
 *   <li>8.3 - New apiType field inferred from type</li>
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
 * @param enabled   Whether the brand is enabled (default true)
 * @param models    List of model entries associated with this brand
 * @param apiType   API protocol type (inferred from type when null)
 */
public record ProviderConfig(
    String id,
    String type,
    String name,
    String apiKey,
    String baseUrl,
    Instant createdAt,
    Instant updatedAt,
    ConnectionStatus status,
    boolean enabled,
    List<ModelEntry> models,
    String apiType
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
     * Defaults: models → List.of() when null, apiType → inferred from type when null.
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

        if (models == null) {
            models = List.of();
        }
        if (apiType == null) {
            apiType = inferApiType(type);
        }
    }

    /**
     * Backward-compatible constructor with 8 arguments (original fields only).
     * New fields default to: enabled=true, models=List.of(), apiType=inferred.
     */
    public ProviderConfig(
            String id, String type, String name, String apiKey, String baseUrl,
            Instant createdAt, Instant updatedAt, ConnectionStatus status) {
        this(id, type, name, apiKey, baseUrl, createdAt, updatedAt, status,
             true, List.of(), null);
    }

    /**
     * Infers the API protocol type from the provider type string.
     *
     * @param type Provider type
     * @return Inferred API type
     */
    public static String inferApiType(String type) {
        if (type == null) {
            return "openai-completions";
        }
        return switch (type) {
            case "anthropic" -> "anthropic-messages";
            case "openai" -> "openai-responses";
            case "google" -> "google-generative-ai";
            case "mistral" -> "mistral-conversations";
            case "bedrock", "amazon-bedrock" -> "bedrock-converse-stream";
            default -> "openai-completions";
        };
    }

    /**
     * Creates a new ProviderConfig with updated fields (backward-compatible signature).
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
            this.status,
            this.enabled,
            this.models,
            this.apiType
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
            status,
            this.enabled,
            this.models,
            this.apiType
        );
    }

    /**
     * Creates a new ProviderConfig with the enabled flag changed.
     *
     * @param enabled New enabled state
     * @return Updated ProviderConfig
     */
    public ProviderConfig withEnabled(boolean enabled) {
        return new ProviderConfig(
            this.id,
            this.type,
            this.name,
            this.apiKey,
            this.baseUrl,
            this.createdAt,
            Instant.now(),
            this.status,
            enabled,
            this.models,
            this.apiType
        );
    }

    /**
     * Creates a new ProviderConfig with the models list replaced.
     *
     * @param models New model list
     * @return Updated ProviderConfig
     */
    public ProviderConfig withModels(List<ModelEntry> models) {
        return new ProviderConfig(
            this.id,
            this.type,
            this.name,
            this.apiKey,
            this.baseUrl,
            this.createdAt,
            Instant.now(),
            this.status,
            this.enabled,
            models,
            this.apiType
        );
    }

    /**
     * Returns a masked version of the API key for display purposes.
     * 
     * <p>The masked key shows the first 4 characters, followed by asterisks,
     * and the last 4 characters. For example: "sk-a****wxyz"
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
        private boolean enabled = true;
        private List<ModelEntry> models = List.of();
        private String apiType;

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

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder models(List<ModelEntry> models) {
            this.models = models;
            return this;
        }

        public Builder apiType(String apiType) {
            this.apiType = apiType;
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
                status,
                enabled,
                models,
                apiType
            );
        }
    }
}
