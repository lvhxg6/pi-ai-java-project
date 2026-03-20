package com.pi.chat.service;

import com.pi.chat.dto.*;
import com.pi.chat.exception.*;
import com.pi.coding.resource.LoadSkillsResult;
import com.pi.coding.resource.ResourceLoader;
import com.pi.coding.resource.Skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Service for Skills management operations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Read skills from ResourceLoader cache</li>
 *   <li>Write skill files to project directory</li>
 *   <li>Validate skill names and content</li>
 *   <li>Handle file I/O operations</li>
 * </ul>
 *
 * <p>Requirements:
 * <ul>
 *   <li>1.1-1.5 - Skills list display</li>
 *   <li>2.1-2.4 - Skills detail view</li>
 *   <li>3.1-3.6 - Skills file upload</li>
 *   <li>4.1-4.5 - Skills online creation</li>
 *   <li>5.1-5.5 - Skills content editing</li>
 *   <li>6.1-6.6 - Skills deletion</li>
 *   <li>10.1 - Last reload timestamp</li>
 * </ul>
 */
public class SkillsService {

    private static final Logger log = LoggerFactory.getLogger(SkillsService.class);
    
    /**
     * Pattern for valid skill names: alphanumeric, hyphens, underscores.
     * Must start with alphanumeric character.
     */
    private static final Pattern VALID_SKILL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]*$");
    
    private static final String SKILL_FILE_NAME = "SKILL.md";
    
    private final ResourceLoader resourceLoader;
    private final Path projectSkillsDir;
    private final Path userSkillsDir;
    
    private volatile long lastReloadTimestamp = System.currentTimeMillis();

    public SkillsService(ResourceLoader resourceLoader, String cwd, String agentDir) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        this.projectSkillsDir = Path.of(cwd, ".kiro", "skills");
        this.userSkillsDir = Path.of(agentDir, "skills");
        
        // Register change listener to track reload timestamp
        resourceLoader.addChangeListener(event -> {
            this.lastReloadTimestamp = event.timestamp();
            log.debug("Skills reloaded at timestamp: {}", lastReloadTimestamp);
        });
    }


    /**
     * Lists all skills with source information.
     *
     * @return SkillsListResponse containing all skills and counts
     */
    public SkillsListResponse listSkills() {
        LoadSkillsResult result = resourceLoader.getSkills();
        List<Skill> allSkills = result.skills();
        
        List<SkillView> skillViews = new ArrayList<>();
        int userCount = 0;
        int projectCount = 0;
        
        for (Skill skill : allSkills) {
            String source = normalizeSource(skill.source());
            boolean editable = "project".equals(source);
            
            skillViews.add(new SkillView(
                skill.name(),
                skill.description(),
                source,
                skill.filePath(),
                editable
            ));
            
            if ("user".equals(source)) {
                userCount++;
            } else if ("project".equals(source)) {
                projectCount++;
            }
        }
        
        return new SkillsListResponse(skillViews, userCount, projectCount, lastReloadTimestamp);
    }

    /**
     * Gets skill details including full file content.
     *
     * @param skillName the skill name to look up
     * @return SkillDetailView with full content
     * @throws SkillNotFoundException if skill doesn't exist
     */
    public SkillDetailView getSkill(String skillName) {
        Skill skill = findSkillByName(skillName);
        if (skill == null) {
            throw new SkillNotFoundException(skillName);
        }
        
        String content = readSkillContent(skill.filePath());
        String source = normalizeSource(skill.source());
        boolean editable = "project".equals(source);
        
        return new SkillDetailView(
            skill.name(),
            skill.description(),
            source,
            skill.filePath(),
            content,
            editable
        );
    }

    /**
     * Validates skill name format.
     *
     * @param skillName the skill name to validate
     * @return ValidationResult indicating if name is valid
     */
    public ValidationResult validateSkillName(String skillName) {
        if (skillName == null || skillName.isEmpty()) {
            return ValidationResult.failure("Skill name cannot be empty");
        }
        if (skillName.length() > 100) {
            return ValidationResult.failure("Skill name cannot exceed 100 characters");
        }
        if (!VALID_SKILL_NAME_PATTERN.matcher(skillName).matches()) {
            return ValidationResult.failure(
                "Skill name must start with alphanumeric and contain only alphanumeric, hyphens, and underscores");
        }
        return ValidationResult.success("Skill name is valid");
    }

    /**
     * Creates a new skill in the project directory.
     *
     * @param request the create skill request
     * @return SkillView of the created skill
     * @throws InvalidSkillNameException if skill name is invalid
     * @throws SkillAlreadyExistsException if skill already exists
     */
    public SkillView createSkill(CreateSkillRequest request) {
        // Validate skill name
        ValidationResult validation = validateSkillName(request.name());
        if (!validation.valid()) {
            throw new InvalidSkillNameException(validation.message());
        }
        
        // Check if skill already exists
        if (skillExists(request.name())) {
            throw new SkillAlreadyExistsException(request.name());
        }
        
        // Create skill directory and file
        Path skillDir = projectSkillsDir.resolve(request.name());
        Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
        
        try {
            Files.createDirectories(skillDir);
            Files.writeString(skillFile, request.content());
            log.info("Created skill: {} at {}", request.name(), skillFile);
        } catch (IOException e) {
            log.error("Failed to create skill: {}", request.name(), e);
            throw new RuntimeException("Failed to create skill: " + e.getMessage(), e);
        }
        
        // Return view (hot reload will update the cache)
        return new SkillView(
            request.name(),
            extractDescription(request.content()),
            "project",
            skillFile.toString(),
            true
        );
    }


    /**
     * Updates an existing project-level skill.
     *
     * @param skillName the skill name to update
     * @param request the update request
     * @return SkillView of the updated skill
     * @throws SkillNotFoundException if skill doesn't exist
     * @throws SkillReadOnlyException if skill is user-level
     */
    public SkillView updateSkill(String skillName, UpdateSkillRequest request) {
        Skill skill = findSkillByName(skillName);
        if (skill == null) {
            throw new SkillNotFoundException(skillName);
        }
        
        String source = normalizeSource(skill.source());
        if (!"project".equals(source)) {
            throw new SkillReadOnlyException(skillName);
        }
        
        // Update skill file
        Path skillFile = Path.of(skill.filePath());
        try {
            Files.writeString(skillFile, request.content());
            log.info("Updated skill: {} at {}", skillName, skillFile);
        } catch (IOException e) {
            log.error("Failed to update skill: {}", skillName, e);
            throw new RuntimeException("Failed to update skill: " + e.getMessage(), e);
        }
        
        return new SkillView(
            skillName,
            extractDescription(request.content()),
            "project",
            skill.filePath(),
            true
        );
    }

    /**
     * Deletes a project-level skill.
     *
     * @param skillName the skill name to delete
     * @throws SkillNotFoundException if skill doesn't exist
     * @throws SkillReadOnlyException if skill is user-level
     */
    public void deleteSkill(String skillName) {
        Skill skill = findSkillByName(skillName);
        if (skill == null) {
            throw new SkillNotFoundException(skillName);
        }
        
        String source = normalizeSource(skill.source());
        if (!"project".equals(source)) {
            throw new SkillReadOnlyException(skillName);
        }
        
        // Delete skill directory
        Path skillDir = Path.of(skill.baseDir());
        try {
            deleteDirectory(skillDir);
            log.info("Deleted skill: {} at {}", skillName, skillDir);
        } catch (IOException e) {
            log.error("Failed to delete skill: {}", skillName, e);
            throw new RuntimeException("Failed to delete skill: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads a skill file.
     *
     * @param skillName the skill name
     * @param content the file content
     * @param filename the original filename
     * @return SkillView of the uploaded skill
     * @throws InvalidSkillNameException if skill name is invalid
     * @throws SkillAlreadyExistsException if skill already exists
     */
    public SkillView uploadSkill(String skillName, byte[] content, String filename) {
        // Validate file extension
        if (filename == null || !filename.toLowerCase().endsWith(".md")) {
            throw new InvalidSkillNameException("File must have .md extension");
        }
        
        // Validate skill name
        ValidationResult validation = validateSkillName(skillName);
        if (!validation.valid()) {
            throw new InvalidSkillNameException(validation.message());
        }
        
        // Check if skill already exists
        if (skillExists(skillName)) {
            throw new SkillAlreadyExistsException(skillName);
        }
        
        // Create skill directory and file
        Path skillDir = projectSkillsDir.resolve(skillName);
        Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
        
        try {
            Files.createDirectories(skillDir);
            Files.write(skillFile, content);
            log.info("Uploaded skill: {} at {}", skillName, skillFile);
        } catch (IOException e) {
            log.error("Failed to upload skill: {}", skillName, e);
            throw new RuntimeException("Failed to upload skill: " + e.getMessage(), e);
        }
        
        String contentStr = new String(content);
        return new SkillView(
            skillName,
            extractDescription(contentStr),
            "project",
            skillFile.toString(),
            true
        );
    }

    /**
     * Gets the last reload timestamp.
     *
     * @return timestamp of last skills reload
     */
    public long getLastReloadTimestamp() {
        return lastReloadTimestamp;
    }


    // ==================== Private Helpers ====================

    /**
     * Finds a skill by name from the ResourceLoader cache.
     */
    private Skill findSkillByName(String skillName) {
        LoadSkillsResult result = resourceLoader.getSkills();
        return result.skills().stream()
            .filter(s -> s.name().equals(skillName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Checks if a skill with the given name already exists.
     */
    private boolean skillExists(String skillName) {
        return findSkillByName(skillName) != null;
    }

    /**
     * Normalizes source identifier to "user" or "project".
     */
    private String normalizeSource(String source) {
        if ("user".equals(source)) {
            return "user";
        }
        // "project" or "path" or any other source is treated as project-level
        return "project";
    }

    /**
     * Reads the content of a skill file.
     */
    private String readSkillContent(String filePath) {
        try {
            return Files.readString(Path.of(filePath));
        } catch (IOException e) {
            log.error("Failed to read skill file: {}", filePath, e);
            throw new RuntimeException("Failed to read skill file: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts description from SKILL.md content (from frontmatter).
     */
    private String extractDescription(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // Check for YAML frontmatter
        if (content.startsWith("---")) {
            int endIndex = content.indexOf("---", 3);
            if (endIndex > 0) {
                String frontmatter = content.substring(3, endIndex);
                // Simple extraction of description field
                for (String line : frontmatter.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("description:")) {
                        String desc = line.substring("description:".length()).trim();
                        // Remove quotes if present
                        if ((desc.startsWith("\"") && desc.endsWith("\"")) ||
                            (desc.startsWith("'") && desc.endsWith("'"))) {
                            desc = desc.substring(1, desc.length() - 1);
                        }
                        return desc;
                    }
                }
            }
        }
        
        // Fallback: use first line or first heading
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("---")) {
                if (line.startsWith("#")) {
                    return line.replaceFirst("^#+\\s*", "");
                }
                return line.length() > 100 ? line.substring(0, 100) + "..." : line;
            }
        }
        
        return "";
    }

    /**
     * Recursively deletes a directory and its contents.
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        
        Files.walk(dir)
            .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before directories
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete: " + path, e);
                }
            });
    }
}
