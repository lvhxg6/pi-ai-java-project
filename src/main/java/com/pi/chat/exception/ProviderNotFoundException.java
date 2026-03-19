package com.pi.chat.exception;

/**
 * Exception thrown when a requested provider configuration is not found.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>1.5 - Handle provider deletion and not found scenarios</li>
 *   <li>9.5 - Return appropriate HTTP status codes (404 for not found)</li>
 * </ul>
 */
public class ProviderNotFoundException extends RuntimeException {
    
    private final String providerId;
    
    /**
     * Creates a new ProviderNotFoundException.
     * 
     * @param providerId The ID of the provider that was not found
     */
    public ProviderNotFoundException(String providerId) {
        super("Provider not found: " + providerId);
        this.providerId = providerId;
    }
    
    /**
     * Creates a new ProviderNotFoundException with a custom message.
     * 
     * @param providerId The ID of the provider that was not found
     * @param message    Custom error message
     */
    public ProviderNotFoundException(String providerId, String message) {
        super(message);
        this.providerId = providerId;
    }
    
    /**
     * Gets the ID of the provider that was not found.
     * 
     * @return The provider ID
     */
    public String getProviderId() {
        return providerId;
    }
}
