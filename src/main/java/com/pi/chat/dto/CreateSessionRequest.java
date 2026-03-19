package com.pi.chat.dto;

/**
 * Request DTO for creating a new session.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>3.1 - Create new session with optional name</li>
 * </ul>
 * 
 * @param name          Optional session name
 * @param modelProvider Initial model provider (optional)
 * @param modelId       Initial model ID (optional)
 */
public record CreateSessionRequest(
    String name,
    String modelProvider,
    String modelId
) {}
