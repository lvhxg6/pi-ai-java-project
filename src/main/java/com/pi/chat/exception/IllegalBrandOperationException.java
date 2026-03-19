package com.pi.chat.exception;

/**
 * Exception thrown when an illegal operation is attempted on a brand.
 * For example, attempting to delete a predefined brand.
 *
 * <p>Requirements:
 * <ul>
 *   <li>9.8 - Return HTTP 403 for illegal brand operations</li>
 * </ul>
 */
public class IllegalBrandOperationException extends RuntimeException {

    /**
     * Creates a new IllegalBrandOperationException.
     *
     * @param message Description of the illegal operation
     */
    public IllegalBrandOperationException(String message) {
        super(message);
    }
}
