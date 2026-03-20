package com.pi.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for updating an existing skill.
 *
 * <p>Requirements:
 * <ul>
 *   <li>5.1 - Provide edit interface for modifying Skill content</li>
 * </ul>
 *
 * @param content Updated SKILL.md file content
 */
public record UpdateSkillRequest(
    @JsonProperty("content") String content
) {}
