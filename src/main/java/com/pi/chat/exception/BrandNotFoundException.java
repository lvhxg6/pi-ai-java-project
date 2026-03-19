package com.pi.chat.exception;

/**
 * Exception thrown when a requested brand is not found.
 *
 * <p>Requirements:
 * <ul>
 *   <li>9.5 - Return appropriate HTTP status codes (404 for not found)</li>
 * </ul>
 */
public class BrandNotFoundException extends RuntimeException {

    private final String brandId;

    /**
     * Creates a new BrandNotFoundException.
     *
     * @param brandId The ID of the brand that was not found
     */
    public BrandNotFoundException(String brandId) {
        super("Brand not found: " + brandId);
        this.brandId = brandId;
    }

    /**
     * Gets the ID of the brand that was not found.
     *
     * @return The brand ID
     */
    public String getBrandId() {
        return brandId;
    }
}
