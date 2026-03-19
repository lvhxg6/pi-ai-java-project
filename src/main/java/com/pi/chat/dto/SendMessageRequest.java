package com.pi.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for sending a chat message.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>4.1 - Send user message to LLM</li>
 * </ul>
 * 
 * @param content Message content
 */
public record SendMessageRequest(
    @NotBlank(message = "Message content is required")
    String content
) {}
