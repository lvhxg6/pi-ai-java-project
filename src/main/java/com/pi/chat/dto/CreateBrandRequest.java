package com.pi.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for creating a custom brand.
 *
 * <p>Requirements:
 * <ul>
 *   <li>9.4 - Create custom brand</li>
 * </ul>
 *
 * @param name    Brand display name
 * @param baseUrl Base URL for the API endpoint
 * @param apiKey  API key for authentication
 */
public record CreateBrandRequest(
    @JsonProperty("name") String name,
    @JsonProperty("baseUrl") String baseUrl,
    @JsonProperty("apiKey") String apiKey
) {}
