package com.pi.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for switching the model in a session.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>5.1 - Switch model during conversation</li>
 * </ul>
 * 
 * @param provider Model provider ID
 * @param modelId  Model ID
 */
public record SwitchModelRequest(
    @NotBlank(message = "Provider is required")
    String provider,
    
    @NotBlank(message = "Model ID is required")
    String modelId
) {}
