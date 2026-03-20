package com.pi.chat.exception;

/**
 * Exception thrown when a skill name is invalid.
 *
 * <p>Requirements:
 * <ul>
 *   <li>4.4 - Validate skill name contains only alphanumeric, hyphens, underscores</li>
 *   <li>4.5 - Return validation error with descriptive message</li>
 * </ul>
 */
public class InvalidSkillNameException extends RuntimeException {

    /**
     * Creates a new InvalidSkillNameException.
     *
     * @param message The validation error message
     */
    public InvalidSkillNameException(String message) {
        super(message);
    }
}
