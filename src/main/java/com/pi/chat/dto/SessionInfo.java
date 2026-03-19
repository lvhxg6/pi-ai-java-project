package com.pi.chat.dto;

import java.time.Instant;

/**
 * Session information for list display.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>3.2 - Display session list with name and last activity time</li>
 * </ul>
 * 
 * @param id             Session unique identifier
 * @param name           Session display name
 * @param createdAt      Timestamp when the session was created
 * @param lastActivityAt Timestamp of last activity
 * @param currentModel   Current model ID (if set)
 * @param messageCount   Number of messages in the session
 */
public record SessionInfo(
    String id,
    String name,
    Instant createdAt,
    Instant lastActivityAt,
    String currentModel,
    int messageCount
) {}
