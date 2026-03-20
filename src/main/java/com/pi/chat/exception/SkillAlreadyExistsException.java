package com.pi.chat.exception;

/**
 * Exception thrown when attempting to create a skill that already exists.
 *
 * <p>Requirements:
 * <ul>
 *   <li>3.5 - Return error indicating conflict when skill name already exists</li>
 * </ul>
 */
public class SkillAlreadyExistsException extends RuntimeException {

    private final String skillName;

    /**
     * Creates a new SkillAlreadyExistsException.
     *
     * @param skillName The name of the skill that already exists
     */
    public SkillAlreadyExistsException(String skillName) {
        super("Skill already exists: " + skillName);
        this.skillName = skillName;
    }

    /**
     * Gets the name of the skill that already exists.
     *
     * @return The skill name
     */
    public String getSkillName() {
        return skillName;
    }
}
