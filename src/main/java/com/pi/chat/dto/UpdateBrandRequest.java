package com.pi.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for updating a brand configuration.
 *
 * <p>Requirements:
 * <ul>
 *   <li>9.3 - Update brand configuration (API Key, Base URL, enabled status)</li>
 * </ul>
 *
 * @param apiKey  New API key (null to keep existing)
 * @param baseUrl New base URL (null to keep existing)
 * @param enabled New enabled status (null to keep existing)
 */
public record UpdateBrandRequest(
    @JsonProperty("apiKey") String apiKey,
    @JsonProperty("baseUrl") String baseUrl,
    @JsonProperty("enabled") Boolean enabled
) {}
