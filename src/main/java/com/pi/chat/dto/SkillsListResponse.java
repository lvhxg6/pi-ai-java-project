package com.pi.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for skills list endpoint.
 *
 * <p>Requirements:
 * <ul>
 *   <li>1.1 - Return list of all Skills from both directories</li>
 *   <li>1.5 - Display total count of Skills for each source location</li>
 *   <li>10.1 - Display last reload timestamp</li>
 * </ul>
 *
 * @param skills              List of all skills
 * @param userSkillsCount     Count of user-level skills
 * @param projectSkillsCount  Count of project-level skills
 * @param lastReloadTimestamp Timestamp of last skills reload
 */
public record SkillsListResponse(
    @JsonProperty("skills") List<SkillView> skills,
    @JsonProperty("userSkillsCount") int userSkillsCount,
    @JsonProperty("projectSkillsCount") int projectSkillsCount,
    @JsonProperty("lastReloadTimestamp") long lastReloadTimestamp
) {}
