package com.pi.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Skill view DTO for API responses.
 *
 * <p>Requirements:
 * <ul>
 *   <li>1.2 - Display each Skill with name, description, and source location</li>
 *   <li>2.1 - Display Skill details</li>
 * </ul>
 *
 * @param name        Skill name (matches parent directory name)
 * @param description Skill description from frontmatter
 * @param source      Source location ("user" or "project")
 * @param filePath    Full path to SKILL.md file
 * @param editable    Whether the skill can be edited (true for project-level)
 */
public record SkillView(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("source") String source,
    @JsonProperty("filePath") String filePath,
    @JsonProperty("editable") boolean editable
) {}
