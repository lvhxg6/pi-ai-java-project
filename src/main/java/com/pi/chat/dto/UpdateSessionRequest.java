package com.pi.chat.dto;

/**
 * Request DTO for updating a session.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>3.7 - Rename session</li>
 * </ul>
 * 
 * @param name New session name
 */
public record UpdateSessionRequest(
    String name
) {}
