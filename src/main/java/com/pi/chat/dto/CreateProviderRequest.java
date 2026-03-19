package com.pi.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new LLM provider configuration.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>1.2 - Store Provider_Config including provider type, API Key, and optional Base URL</li>
 * </ul>
 * 
 * @param type    Provider type (anthropic, openai, google, mistral, bedrock)
 * @param name    Display name for the provider
 * @param apiKey  API Key for authentication
 * @param baseUrl Optional custom Base URL for the provider
 */
public record CreateProviderRequest(
    @NotBlank(message = "Provider type is required")
    String type,
    
    @NotBlank(message = "Provider name is required")
    String name,
    
    @NotBlank(message = "API key is required")
    String apiKey,
    
    String baseUrl
) {}
