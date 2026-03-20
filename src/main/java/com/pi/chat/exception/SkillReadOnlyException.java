package com.pi.chat.exception;

/**
 * Exception thrown when attempting to modify a read-only (user-level) skill.
 *
 * <p>Requirements:
 * <ul>
 *   <li>5.3, 5.4 - Only allow editing of project-level Skills</li>
 *   <li>6.3, 6.4 - Only allow deletion of project-level Skills</li>
 * </ul>
 */
public class SkillReadOnlyException extends RuntimeException {

    private final String skillName;

    /**
     * Creates a new SkillReadOnlyException.
     *
     * @param skillName The name of the read-only skill
     */
    public SkillReadOnlyException(String skillName) {
        super("Skill is read-only (user-level): " + skillName);
        this.skillName = skillName;
    }

    /**
     * Gets the name of the read-only skill.
     *
     * @return The skill name
     */
    public String getSkillName() {
        return skillName;
    }
}
