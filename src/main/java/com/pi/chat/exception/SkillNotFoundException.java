package com.pi.chat.exception;

/**
 * Exception thrown when a requested skill is not found.
 *
 * <p>Requirements:
 * <ul>
 *   <li>7.7 - Return appropriate HTTP status codes (404 for not found)</li>
 * </ul>
 */
public class SkillNotFoundException extends RuntimeException {

    private final String skillName;

    /**
     * Creates a new SkillNotFoundException.
     *
     * @param skillName The name of the skill that was not found
     */
    public SkillNotFoundException(String skillName) {
        super("Skill not found: " + skillName);
        this.skillName = skillName;
    }

    /**
     * Gets the name of the skill that was not found.
     *
     * @return The skill name
     */
    public String getSkillName() {
        return skillName;
    }
}
