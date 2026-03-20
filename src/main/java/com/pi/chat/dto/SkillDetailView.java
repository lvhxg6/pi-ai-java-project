package com.pi.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Skill detail view DTO for API responses including full content.
 *
 * <p>Requirements:
 * <ul>
 *   <li>2.1 - Display full content of SKILL.md file</li>
 *   <li>2.3 - Display Skill metadata separately from content</li>
 *   <li>2.4 - Show file path of selected Skill</li>
 * </ul>
 *
 * @param name        Skill name (matches parent directory name)
 * @param description Skill description from frontmatter
 * @param source      Source location ("user" or "project")
 * @param filePath    Full path to SKILL.md file
 * @param content     Full SKILL.md file content
 * @param editable    Whether the skill can be edited (true for project-level)
 */
public record SkillDetailView(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("source") String source,
    @JsonProperty("filePath") String filePath,
    @JsonProperty("content") String content,
    @JsonProperty("editable") boolean editable
) {}
