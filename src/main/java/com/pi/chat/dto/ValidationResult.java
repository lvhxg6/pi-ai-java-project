package com.pi.chat.dto;

import java.util.List;

/**
 * Result of API key validation for a provider.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>1.4 - Validate API Key format before saving</li>
 *   <li>1.7 - Display descriptive error message on validation failure</li>
 * </ul>
 * 
 * @param valid   Whether the API key is valid
 * @param message Human-readable validation message
 * @param errors  List of specific validation errors (empty if valid)
 */
public record ValidationResult(
    boolean valid,
    String message,
    List<String> errors
) {
    
    /**
     * Creates a successful validation result.
     * 
     * @return ValidationResult indicating success
     */
    public static ValidationResult success() {
        return new ValidationResult(true, "API key is valid", List.of());
    }
    
    /**
     * Creates a successful validation result with a custom message.
     * 
     * @param message Success message
     * @return ValidationResult indicating success
     */
    public static ValidationResult success(String message) {
        return new ValidationResult(true, message, List.of());
    }
    
    /**
     * Creates a failed validation result with a single error.
     * 
     * @param error Error message
     * @return ValidationResult indicating failure
     */
    public static ValidationResult failure(String error) {
        return new ValidationResult(false, error, List.of(error));
    }
    
    /**
     * Creates a failed validation result with multiple errors.
     * 
     * @param message Summary message
     * @param errors  List of specific errors
     * @return ValidationResult indicating failure
     */
    public static ValidationResult failure(String message, List<String> errors) {
        return new ValidationResult(false, message, errors);
    }
}
