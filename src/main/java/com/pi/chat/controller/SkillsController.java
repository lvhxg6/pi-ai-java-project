package com.pi.chat.controller;

import com.pi.chat.dto.*;
import com.pi.chat.service.SkillsService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST Controller for managing Skills.
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Listing all skills from user and project directories</li>
 *   <li>Getting skill details</li>
 *   <li>Creating, updating, and deleting project-level skills</li>
 *   <li>Uploading skill files</li>
 * </ul>
 *
 * <p>Requirements:
 * <ul>
 *   <li>7.1 - GET /api/skills - list all skills</li>
 *   <li>7.2 - GET /api/skills/{skillName} - get skill detail</li>
 *   <li>7.3 - POST /api/skills - create new skill</li>
 *   <li>7.4 - PUT /api/skills/{skillName} - update skill</li>
 *   <li>7.5 - DELETE /api/skills/{skillName} - delete skill</li>
 *   <li>7.6 - POST /api/skills/upload - upload skill file</li>
 *   <li>7.7 - Return appropriate HTTP status codes</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/skills")
public class SkillsController {

    private final SkillsService skillsService;

    public SkillsController(SkillsService skillsService) {
        this.skillsService = skillsService;
    }

    /**
     * Lists all skills from both user and project directories.
     *
     * @return SkillsListResponse containing all skills and counts
     */
    @GetMapping
    public SkillsListResponse listSkills() {
        return skillsService.listSkills();
    }

    /**
     * Gets a specific skill's details including full content.
     *
     * @param skillName The skill name
     * @return SkillDetailView with full content
     */
    @GetMapping("/{skillName}")
    public SkillDetailView getSkill(@PathVariable String skillName) {
        return skillsService.getSkill(skillName);
    }

    /**
     * Creates a new skill in the project directory.
     *
     * @param request The create skill request
     * @return SkillView of the created skill
     */
    @PostMapping
    public SkillView createSkill(@RequestBody CreateSkillRequest request) {
        return skillsService.createSkill(request);
    }

    /**
     * Updates an existing project-level skill.
     *
     * @param skillName The skill name
     * @param request The update request
     * @return SkillView of the updated skill
     */
    @PutMapping("/{skillName}")
    public SkillView updateSkill(@PathVariable String skillName, @RequestBody UpdateSkillRequest request) {
        return skillsService.updateSkill(skillName, request);
    }

    /**
     * Deletes a project-level skill.
     *
     * @param skillName The skill name
     * @return 204 No Content on success
     */
    @DeleteMapping("/{skillName}")
    public ResponseEntity<Void> deleteSkill(@PathVariable String skillName) {
        skillsService.deleteSkill(skillName);
        return ResponseEntity.noContent().build();
    }

    /**
     * Uploads a skill file to the project directory.
     *
     * @param file The skill file (.md)
     * @param skillName The skill name (directory name)
     * @return SkillView of the uploaded skill
     */
    @PostMapping("/upload")
    public SkillView uploadSkill(@RequestParam("file") MultipartFile file,
                                  @RequestParam("skillName") String skillName) throws IOException {
        return skillsService.uploadSkill(skillName, file.getBytes(), file.getOriginalFilename());
    }
}
