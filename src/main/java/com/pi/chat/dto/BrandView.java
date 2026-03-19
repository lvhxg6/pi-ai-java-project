package com.pi.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.chat.model.ModelEntry;

import java.time.Instant;
import java.util.List;

/**
 * Brand view DTO for API responses.
 *
 * <p>Requirements:
 * <ul>
 *   <li>9.1 - Return brand list with enabled status and masked API Key</li>
 *   <li>9.2 - Return brand detail configuration</li>
 * </ul>
 *
 * @param id             Brand unique identifier
 * @param name           Brand display name
 * @param provider       SDK provider ID
 * @param apiType        API protocol type
 * @param baseUrl        Current base URL
 * @param defaultBaseUrl Default base URL from brand definition
 * @param enabled        Whether the brand is enabled
 * @param builtin        Whether this is a built-in brand
 * @param deletable      Whether this brand can be deleted
 * @param hasApiKey      Whether an API key is configured
 * @param maskedApiKey   Masked API key for display
 * @param models         List of model entries
 * @param createdAt      Creation timestamp
 * @param updatedAt      Last update timestamp
 */
public record BrandView(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("provider") String provider,
    @JsonProperty("apiType") String apiType,
    @JsonProperty("baseUrl") String baseUrl,
    @JsonProperty("defaultBaseUrl") String defaultBaseUrl,
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("builtin") boolean builtin,
    @JsonProperty("deletable") boolean deletable,
    @JsonProperty("hasApiKey") boolean hasApiKey,
    @JsonProperty("maskedApiKey") String maskedApiKey,
    @JsonProperty("models") List<ModelEntry> models,
    @JsonProperty("createdAt") Instant createdAt,
    @JsonProperty("updatedAt") Instant updatedAt
) {}
