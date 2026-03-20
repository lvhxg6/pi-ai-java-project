package com.pi.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for creating a new skill.
 *
 * <p>Requirements:
 * <ul>
 *   <li>4.1 - Provide form for creating new Skills with name and content fields</li>
 * </ul>
 *
 * @param name    Skill name (used as directory name)
 * @param content SKILL.md file content
 */
public record CreateSkillRequest(
    @JsonProperty("name") String name,
    @JsonProperty("content") String content
) {}
