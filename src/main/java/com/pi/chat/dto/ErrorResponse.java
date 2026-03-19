package com.pi.chat.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response format.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>9.5 - Return appropriate HTTP status codes</li>
 *   <li>9.6 - Return descriptive error messages</li>
 * </ul>
 * 
 * @param code      Error code (e.g., "NOT_FOUND", "VALIDATION_ERROR")
 * @param message   Human-readable error message
 * @param details   Additional error details (optional)
 * @param timestamp When the error occurred
 */
public record ErrorResponse(
    String code,
    String message,
    Map<String, Object> details,
    Instant timestamp
) {
    
    /**
     * Creates an error response with the current timestamp.
     */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null, Instant.now());
    }
    
    /**
     * Creates an error response with details.
     */
    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(code, message, details, Instant.now());
    }
}
