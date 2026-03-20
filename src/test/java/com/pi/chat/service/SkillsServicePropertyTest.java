package com.pi.chat.service;

import com.pi.chat.dto.*;
import com.pi.chat.exception.*;
import com.pi.coding.resource.*;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for SkillsService.
 * 
 * <p>Tests the following correctness properties from the design document:
 * <ul>
 *   <li>Property 1: Skills 列表完整性与正确性</li>
 *   <li>Property 2: Skill 详情完整性</li>
 *   <li>Property 3: Skill 创建往返一致性</li>
 *   <li>Property 4: Skill 名称验证</li>
 *   <li>Property 5: Skill 更新正确性</li>
 *   <li>Property 6: 用户级 Skills 只读保护</li>
 *   <li>Property 7: Skill 删除完整性</li>
 *   <li>Property 8: Skill 名称冲突检测</li>
 * </ul>
 */
class SkillsServicePropertyTest {
    
    @TempDir
    Path tempDir;
    
    private Path projectSkillsDir;
    private Path userSkillsDir;
    private TestResourceLoader resourceLoader;
    private SkillsService skillsService;
    
    @BeforeEach
    void setUp() throws IOException {
        projectSkillsDir = tempDir.resolve("project/.kiro/skills");
        userSkillsDir = tempDir.resolve("user/skills");
        Files.createDirectories(projectSkillsDir);
        Files.createDirectories(userSkillsDir);
        
        resourceLoader = new TestResourceLoader();
        skillsService = new SkillsService(
            resourceLoader,
            tempDir.resolve("project").toString(),
            tempDir.resolve("user").toString()
        );
    }
    
    // ========== Property 1: Skills 列表完整性与正确性 ==========
    
    @Property(tries = 50)
    @Tag("Feature: skills-web-management, Property 1: Skills 列表完整性与正确性")
    void skillsListContainsAllSkillsWithCorrectCounts(
            @ForAll @IntRange(min = 0, max = 3) int userSkillCount,
            @ForAll @IntRange(min = 0, max = 3) int projectSkillCount) throws IOException {
        
        // Setup: Create skills in both directories
        List<Skill> allSkills = new ArrayList<>();
        
        for (int i = 0; i < userSkillCount; i++) {
            String name = "user-skill-" + i;
            createTestSkill(userSkillsDir, name, "user");
            allSkills.add(createSkillRecord(name, userSkillsDir, "user"));
        }
        
        for (int i = 0; i < projectSkillCount; i++) {
            String name = "project-skill-" + i;
            createTestSkill(projectSkillsDir, name, "project");
            allSkills.add(createSkillRecord(name, projectSkillsDir, "project"));
        }
        
        resourceLoader.setSkills(allSkills);
        
        // Execute
        SkillsListResponse response = skillsService.listSkills();
        
        // Verify counts
        assertThat(response.userSkillsCount()).isEqualTo(userSkillCount);
        assertThat(response.projectSkillsCount()).isEqualTo(projectSkillCount);
        assertThat(response.skills()).hasSize(userSkillCount + projectSkillCount);
        
        // Verify each skill has required fields
        for (SkillView skill : response.skills()) {
            assertThat(skill.name()).isNotNull().isNotEmpty();
            assertThat(skill.description()).isNotNull();
            assertThat(skill.source()).isIn("user", "project");
            assertThat(skill.filePath()).isNotNull();
        }
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 1: Skills 列表完整性与正确性")
    void skillsListReturnsEmptyWhenNoSkills() {
        resourceLoader.setSkills(List.of());
        
        SkillsListResponse response = skillsService.listSkills();
        
        assertThat(response.skills()).isEmpty();
        assertThat(response.userSkillsCount()).isZero();
        assertThat(response.projectSkillsCount()).isZero();
    }
    
    // ========== Property 2: Skill 详情完整性 ==========
    
    @Test
    @Tag("Feature: skills-web-management, Property 2: Skill 详情完整性")
    void skillDetailContainsFullContent() throws IOException {
        String skillName = "test-skill";
        String description = "Test description";
        String content = "---\ndescription: " + description + "\n---\n# Test Skill\n\nThis is the content.";
        
        createTestSkillWithContent(projectSkillsDir, skillName, content);
        // Use the same description in the Skill record as in the file
        Skill skillRecord = new Skill(
            skillName,
            description,
            projectSkillsDir.resolve(skillName).resolve("SKILL.md").toString(),
            projectSkillsDir.resolve(skillName).toString(),
            "project",
            false
        );
        resourceLoader.setSkills(List.of(skillRecord));
        
        SkillDetailView detail = skillsService.getSkill(skillName);
        
        assertThat(detail.name()).isEqualTo(skillName);
        assertThat(detail.content()).isEqualTo(content);
        assertThat(detail.description()).isEqualTo(description);
        assertThat(detail.filePath()).contains(skillName);
        assertThat(detail.editable()).isTrue();
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 2: Skill 详情完整性")
    void skillDetailThrowsNotFoundForMissingSkill() {
        resourceLoader.setSkills(List.of());
        
        assertThatThrownBy(() -> skillsService.getSkill("nonexistent"))
            .isInstanceOf(SkillNotFoundException.class);
    }
    
    // ========== Property 3: Skill 创建往返一致性 ==========
    
    @Property(tries = 30)
    @Tag("Feature: skills-web-management, Property 3: Skill 创建往返一致性")
    void skillCreationRoundTrip(
            @ForAll("validSkillName") String skillName) throws IOException {
        
        String content = "---\ndescription: Created skill\n---\n# " + skillName + "\n\nContent here.";
        resourceLoader.setSkills(List.of()); // No existing skills
        
        // Create skill
        CreateSkillRequest request = new CreateSkillRequest(skillName, content);
        SkillView created = skillsService.createSkill(request);
        
        // Verify creation response
        assertThat(created.name()).isEqualTo(skillName);
        assertThat(created.source()).isEqualTo("project");
        assertThat(created.editable()).isTrue();
        
        // Verify file exists
        Path skillFile = projectSkillsDir.resolve(skillName).resolve("SKILL.md");
        assertThat(Files.exists(skillFile)).isTrue();
        
        // Verify content
        String savedContent = Files.readString(skillFile);
        assertThat(savedContent).isEqualTo(content);
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 3: Skill 创建往返一致性")
    void createdSkillAppearsInProjectDirectory() throws IOException {
        String skillName = "new-skill";
        String content = "---\ndescription: New skill\n---\n# New Skill";
        resourceLoader.setSkills(List.of());
        
        skillsService.createSkill(new CreateSkillRequest(skillName, content));
        
        Path skillDir = projectSkillsDir.resolve(skillName);
        Path skillFile = skillDir.resolve("SKILL.md");
        
        assertThat(Files.isDirectory(skillDir)).isTrue();
        assertThat(Files.exists(skillFile)).isTrue();
    }
    
    // ========== Property 4: Skill 名称验证 ==========
    
    @Property(tries = 50)
    @Tag("Feature: skills-web-management, Property 4: Skill 名称验证")
    void validSkillNamesAreAccepted(
            @ForAll("validSkillName") String validName) {
        
        ValidationResult result = skillsService.validateSkillName(validName);
        
        assertThat(result.valid()).isTrue();
    }
    
    @Property(tries = 50)
    @Tag("Feature: skills-web-management, Property 4: Skill 名称验证")
    void invalidSkillNamesAreRejected(
            @ForAll("invalidSkillName") String invalidName) {
        
        ValidationResult result = skillsService.validateSkillName(invalidName);
        
        assertThat(result.valid()).isFalse();
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 4: Skill 名称验证")
    void emptySkillNameIsRejected() {
        assertThat(skillsService.validateSkillName("").valid()).isFalse();
        assertThat(skillsService.validateSkillName(null).valid()).isFalse();
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 4: Skill 名称验证")
    void skillNameStartingWithHyphenIsRejected() {
        assertThat(skillsService.validateSkillName("-invalid").valid()).isFalse();
        assertThat(skillsService.validateSkillName("_invalid").valid()).isFalse();
    }
    
    // ========== Property 5: Skill 更新正确性 ==========
    
    @Test
    @Tag("Feature: skills-web-management, Property 5: Skill 更新正确性")
    void skillUpdatePersistsNewContent() throws IOException {
        String skillName = "updatable-skill";
        String originalContent = "---\ndescription: Original\n---\n# Original";
        String newContent = "---\ndescription: Updated\n---\n# Updated Content";
        
        createTestSkillWithContent(projectSkillsDir, skillName, originalContent);
        resourceLoader.setSkills(List.of(createSkillRecord(skillName, projectSkillsDir, "project")));
        
        // Update
        SkillView updated = skillsService.updateSkill(skillName, new UpdateSkillRequest(newContent));
        
        // Verify response
        assertThat(updated.name()).isEqualTo(skillName);
        assertThat(updated.description()).isEqualTo("Updated");
        
        // Verify file content
        Path skillFile = projectSkillsDir.resolve(skillName).resolve("SKILL.md");
        String savedContent = Files.readString(skillFile);
        assertThat(savedContent).isEqualTo(newContent);
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 5: Skill 更新正确性")
    void updateNonexistentSkillThrowsNotFound() {
        resourceLoader.setSkills(List.of());
        
        assertThatThrownBy(() -> 
            skillsService.updateSkill("nonexistent", new UpdateSkillRequest("content")))
            .isInstanceOf(SkillNotFoundException.class);
    }
    
    // ========== Property 6: 用户级 Skills 只读保护 ==========
    
    @Test
    @Tag("Feature: skills-web-management, Property 6: 用户级 Skills 只读保护")
    void userLevelSkillCannotBeUpdated() throws IOException {
        String skillName = "user-skill";
        createTestSkill(userSkillsDir, skillName, "user");
        resourceLoader.setSkills(List.of(createSkillRecord(skillName, userSkillsDir, "user")));
        
        assertThatThrownBy(() -> 
            skillsService.updateSkill(skillName, new UpdateSkillRequest("new content")))
            .isInstanceOf(SkillReadOnlyException.class);
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 6: 用户级 Skills 只读保护")
    void userLevelSkillCannotBeDeleted() throws IOException {
        String skillName = "user-skill";
        createTestSkill(userSkillsDir, skillName, "user");
        resourceLoader.setSkills(List.of(createSkillRecord(skillName, userSkillsDir, "user")));
        
        assertThatThrownBy(() -> skillsService.deleteSkill(skillName))
            .isInstanceOf(SkillReadOnlyException.class);
        
        // Verify file still exists
        Path skillFile = userSkillsDir.resolve(skillName).resolve("SKILL.md");
        assertThat(Files.exists(skillFile)).isTrue();
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 6: 用户级 Skills 只读保护")
    void userLevelSkillIsMarkedAsNotEditable() throws IOException {
        String skillName = "user-skill";
        createTestSkill(userSkillsDir, skillName, "user");
        resourceLoader.setSkills(List.of(createSkillRecord(skillName, userSkillsDir, "user")));
        
        SkillsListResponse response = skillsService.listSkills();
        
        SkillView userSkill = response.skills().stream()
            .filter(s -> s.name().equals(skillName))
            .findFirst()
            .orElseThrow();
        
        assertThat(userSkill.editable()).isFalse();
    }
    
    // ========== Property 7: Skill 删除完整性 ==========
    
    @Test
    @Tag("Feature: skills-web-management, Property 7: Skill 删除完整性")
    void deleteRemovesEntireSkillDirectory() throws IOException {
        String skillName = "deletable-skill";
        createTestSkill(projectSkillsDir, skillName, "project");
        resourceLoader.setSkills(List.of(createSkillRecord(skillName, projectSkillsDir, "project")));
        
        Path skillDir = projectSkillsDir.resolve(skillName);
        assertThat(Files.exists(skillDir)).isTrue();
        
        // Delete
        skillsService.deleteSkill(skillName);
        
        // Verify directory is gone
        assertThat(Files.exists(skillDir)).isFalse();
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 7: Skill 删除完整性")
    void deleteNonexistentSkillThrowsNotFound() {
        resourceLoader.setSkills(List.of());
        
        assertThatThrownBy(() -> skillsService.deleteSkill("nonexistent"))
            .isInstanceOf(SkillNotFoundException.class);
    }
    
    // ========== Property 8: Skill 名称冲突检测 ==========
    
    @Test
    @Tag("Feature: skills-web-management, Property 8: Skill 名称冲突检测")
    void createDuplicateSkillThrowsAlreadyExists() throws IOException {
        String skillName = "existing-skill";
        createTestSkill(projectSkillsDir, skillName, "project");
        resourceLoader.setSkills(List.of(createSkillRecord(skillName, projectSkillsDir, "project")));
        
        assertThatThrownBy(() -> 
            skillsService.createSkill(new CreateSkillRequest(skillName, "content")))
            .isInstanceOf(SkillAlreadyExistsException.class);
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 8: Skill 名称冲突检测")
    void uploadDuplicateSkillThrowsAlreadyExists() throws IOException {
        String skillName = "existing-skill";
        createTestSkill(projectSkillsDir, skillName, "project");
        resourceLoader.setSkills(List.of(createSkillRecord(skillName, projectSkillsDir, "project")));
        
        assertThatThrownBy(() -> 
            skillsService.uploadSkill(skillName, "content".getBytes(), "skill.md"))
            .isInstanceOf(SkillAlreadyExistsException.class);
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 8: Skill 名称冲突检测")
    void existingSkillRemainsUnchangedAfterConflict() throws IOException {
        String skillName = "existing-skill";
        String originalContent = "---\ndescription: Original\n---\n# Original";
        createTestSkillWithContent(projectSkillsDir, skillName, originalContent);
        resourceLoader.setSkills(List.of(createSkillRecord(skillName, projectSkillsDir, "project")));
        
        // Attempt to create duplicate
        try {
            skillsService.createSkill(new CreateSkillRequest(skillName, "new content"));
        } catch (SkillAlreadyExistsException e) {
            // Expected
        }
        
        // Verify original content unchanged
        Path skillFile = projectSkillsDir.resolve(skillName).resolve("SKILL.md");
        String content = Files.readString(skillFile);
        assertThat(content).isEqualTo(originalContent);
    }
    
    // ========== Upload Tests ==========
    
    @Test
    @Tag("Feature: skills-web-management, Property 4: Skill 名称验证")
    void uploadRejectsNonMdFile() {
        resourceLoader.setSkills(List.of());
        
        assertThatThrownBy(() -> 
            skillsService.uploadSkill("skill", "content".getBytes(), "skill.txt"))
            .isInstanceOf(InvalidSkillNameException.class)
            .hasMessageContaining(".md");
    }
    
    @Test
    @Tag("Feature: skills-web-management, Property 3: Skill 创建往返一致性")
    void uploadCreatesSkillCorrectly() throws IOException {
        String skillName = "uploaded-skill";
        String content = "---\ndescription: Uploaded\n---\n# Uploaded Skill";
        resourceLoader.setSkills(List.of());
        
        SkillView result = skillsService.uploadSkill(skillName, content.getBytes(), "skill.md");
        
        assertThat(result.name()).isEqualTo(skillName);
        assertThat(result.source()).isEqualTo("project");
        
        Path skillFile = projectSkillsDir.resolve(skillName).resolve("SKILL.md");
        assertThat(Files.exists(skillFile)).isTrue();
        assertThat(Files.readString(skillFile)).isEqualTo(content);
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<String> validSkillName() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .ofMinLength(1)
            .ofMaxLength(20)
            .filter(s -> Character.isLetterOrDigit(s.charAt(0)))
            .map(s -> s + Arbitraries.of("-skill", "_test", "123").sample());
    }
    
    @Provide
    Arbitrary<String> invalidSkillName() {
        return Arbitraries.oneOf(
            Arbitraries.just(""),
            Arbitraries.just("-starts-with-hyphen"),
            Arbitraries.just("_starts-with-underscore"),
            Arbitraries.just("has spaces"),
            Arbitraries.just("has@special!chars"),
            Arbitraries.just("has/slash"),
            Arbitraries.just("has\\backslash")
        );
    }
    
    // ========== Helper Methods ==========
    
    private void createTestSkill(Path baseDir, String name, String source) throws IOException {
        String content = "---\ndescription: " + name + " description\n---\n# " + name;
        createTestSkillWithContent(baseDir, name, content);
    }
    
    private void createTestSkillWithContent(Path baseDir, String name, String content) throws IOException {
        Path skillDir = baseDir.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), content);
    }
    
    private Skill createSkillRecord(String name, Path baseDir, String source) {
        Path skillDir = baseDir.resolve(name);
        Path skillFile = skillDir.resolve("SKILL.md");
        return new Skill(
            name,
            name + " description",
            skillFile.toString(),
            skillDir.toString(),
            source,
            false
        );
    }
    
    // ========== Test ResourceLoader ==========
    
    /**
     * Simple test implementation of ResourceLoader.
     */
    private static class TestResourceLoader implements ResourceLoader {
        
        private List<Skill> skills = List.of();
        private final List<ResourceChangeListener> listeners = new ArrayList<>();
        
        void setSkills(List<Skill> skills) {
            this.skills = skills;
        }
        
        @Override
        public CompletableFuture<Void> reload() {
            return CompletableFuture.completedFuture(null);
        }
        
        @Override
        public com.pi.coding.extension.LoadExtensionsResult getExtensions() {
            return new com.pi.coding.extension.LoadExtensionsResult(List.of(), List.of());
        }
        
        @Override
        public LoadSkillsResult getSkills() {
            return new LoadSkillsResult(skills, List.of());
        }
        
        @Override
        public LoadPromptsResult getPrompts() {
            return new LoadPromptsResult(List.of(), List.of());
        }
        
        @Override
        public List<ContextFile> getAgentsFiles() {
            return List.of();
        }
        
        @Override
        public String getSystemPrompt() {
            return null;
        }
        
        @Override
        public List<String> getAppendSystemPrompt() {
            return List.of();
        }
        
        @Override
        public List<ResourceDiagnostic> getDiagnostics() {
            return List.of();
        }
        
        @Override
        public void extendResources(ResourceExtensionPaths paths) {
            // No-op
        }
        
        @Override
        public void addChangeListener(ResourceChangeListener listener) {
            listeners.add(listener);
        }
        
        @Override
        public void removeChangeListener(ResourceChangeListener listener) {
            listeners.remove(listener);
        }
    }
}
