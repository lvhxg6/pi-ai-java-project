package com.pi.chat.dto;

/**
 * Request DTO for updating an existing LLM provider configuration.
 * 
 * <p>All fields are optional. Only non-null fields will be updated.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>1.4 - Validate API Key format before saving</li>
 * </ul>
 * 
 * @param name    New display name (null to keep existing)
 * @param apiKey  New API key (null to keep existing)
 * @param baseUrl New base URL (null to keep existing)
 */
public record UpdateProviderRequest(
    String name,
    String apiKey,
    String baseUrl
) {}
