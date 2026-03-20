package com.pi.chat.integration;

import com.pi.chat.dto.*;
import com.pi.chat.exception.*;
import com.pi.chat.service.SkillsService;
import com.pi.coding.resource.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * Integration tests for Skills management functionality.
 * 
 * <p>Tests complete workflows for:
 * <ul>
 *   <li>Skill CRUD operations</li>
 *   <li>File upload</li>
 *   <li>User vs Project skill handling</li>
 *   <li>Error scenarios</li>
 * </ul>
 */
class SkillsIntegrationTest {
    
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
    
    @Nested
    @DisplayName("Complete Skill Lifecycle")
    class SkillLifecycle {
        
        @Test
        @DisplayName("Full CRUD lifecycle for project skill")
        void fullCrudLifecycle() throws IOException {
            String skillName = "lifecycle-skill";
            String initialContent = "---\ndescription: Initial description\n---\n# Initial Content";
            String updatedContent = "---\ndescription: Updated description\n---\n# Updated Content";
            
            // 1. Create skill
            resourceLoader.setSkills(List.of());
            SkillView created = skillsService.createSkill(new CreateSkillRequest(skillName, initialContent));
            
            assertThat(created.name()).isEqualTo(skillName);
            assertThat(created.source()).isEqualTo("project");
            assertThat(created.editable()).isTrue();
            
            // Verify file exists
            Path skillFile = projectSkillsDir.resolve(skillName).resolve("SKILL.md");
            assertThat(Files.exists(skillFile)).isTrue();
            assertThat(Files.readString(skillFile)).isEqualTo(initialContent);
            
            // 2. Read skill (simulate ResourceLoader update)
            resourceLoader.setSkills(List.of(createSkillRecord(skillName, projectSkillsDir, "project", "Initial description")));
            SkillDetailView detail = skillsService.getSkill(skillName);
            
            assertThat(detail.name()).isEqualTo(skillName);
            assertThat(detail.content()).isEqualTo(initialContent);
            assertThat(detail.editable()).isTrue();
            
            // 3. Update skill
            SkillView updated = skillsService.updateSkill(skillName, new UpdateSkillRequest(updatedContent));
            
            assertThat(updated.name()).isEqualTo(skillName);
            assertThat(updated.description()).isEqualTo("Updated description");
            assertThat(Files.readString(skillFile)).isEqualTo(updatedContent);
            
            // 4. Delete skill
            skillsService.deleteSkill(skillName);
            
            assertThat(Files.exists(projectSkillsDir.resolve(skillName))).isFalse();
        }
        
        @Test
        @DisplayName("Upload skill file creates correct structure")
        void uploadSkillFile() throws IOException {
            String skillName = "uploaded-skill";
            String content = "---\ndescription: Uploaded skill\n---\n# Uploaded";
            
            resourceLoader.setSkills(List.of());
            SkillView result = skillsService.uploadSkill(skillName, content.getBytes(), "skill.md");
            
            assertThat(result.name()).isEqualTo(skillName);
            assertThat(result.source()).isEqualTo("project");
            
            Path skillFile = projectSkillsDir.resolve(skillName).resolve("SKILL.md");
            assertThat(Files.exists(skillFile)).isTrue();
            assertThat(Files.readString(skillFile)).isEqualTo(content);
        }
    }
    
    @Nested
    @DisplayName("Skills List Operations")
    class SkillsListOperations {
        
        @Test
        @DisplayName("List returns all skills with correct counts")
        void listReturnsAllSkillsWithCounts() throws IOException {
            // Setup: Create user and project skills
            createTestSkill(userSkillsDir, "user-skill-1", "user");
            createTestSkill(userSkillsDir, "user-skill-2", "user");
            createTestSkill(projectSkillsDir, "project-skill-1", "project");
            
            resourceLoader.setSkills(List.of(
                createSkillRecord("user-skill-1", userSkillsDir, "user", "User skill 1"),
                createSkillRecord("user-skill-2", userSkillsDir, "user", "User skill 2"),
                createSkillRecord("project-skill-1", projectSkillsDir, "project", "Project skill 1")
            ));
            
            SkillsListResponse response = skillsService.listSkills();
            
            assertThat(response.skills()).hasSize(3);
            assertThat(response.userSkillsCount()).isEqualTo(2);
            assertThat(response.projectSkillsCount()).isEqualTo(1);
            assertThat(response.lastReloadTimestamp()).isPositive();
        }
        
        @Test
        @DisplayName("Empty list when no skills exist")
        void emptyListWhenNoSkills() {
            resourceLoader.setSkills(List.of());
            
            SkillsListResponse response = skillsService.listSkills();
            
            assertThat(response.skills()).isEmpty();
            assertThat(response.userSkillsCount()).isZero();
            assertThat(response.projectSkillsCount()).isZero();
        }
    }
    
    @Nested
    @DisplayName("User Level Skills Protection")
    class UserLevelSkillsProtection {
        
        @Test
        @DisplayName("User skills are marked as not editable")
        void userSkillsNotEditable() throws IOException {
            createTestSkill(userSkillsDir, "user-skill", "user");
            resourceLoader.setSkills(List.of(
                createSkillRecord("user-skill", userSkillsDir, "user", "User skill")
            ));
            
            SkillsListResponse response = skillsService.listSkills();
            SkillView userSkill = response.skills().get(0);
            
            assertThat(userSkill.editable()).isFalse();
            assertThat(userSkill.source()).isEqualTo("user");
        }
        
        @Test
        @DisplayName("Cannot update user level skill")
        void cannotUpdateUserSkill() throws IOException {
            createTestSkill(userSkillsDir, "user-skill", "user");
            resourceLoader.setSkills(List.of(
                createSkillRecord("user-skill", userSkillsDir, "user", "User skill")
            ));
            
            assertThatThrownBy(() -> 
                skillsService.updateSkill("user-skill", new UpdateSkillRequest("new content")))
                .isInstanceOf(SkillReadOnlyException.class);
        }
        
        @Test
        @DisplayName("Cannot delete user level skill")
        void cannotDeleteUserSkill() throws IOException {
            createTestSkill(userSkillsDir, "user-skill", "user");
            resourceLoader.setSkills(List.of(
                createSkillRecord("user-skill", userSkillsDir, "user", "User skill")
            ));
            
            assertThatThrownBy(() -> skillsService.deleteSkill("user-skill"))
                .isInstanceOf(SkillReadOnlyException.class);
            
            // Verify file still exists
            assertThat(Files.exists(userSkillsDir.resolve("user-skill").resolve("SKILL.md"))).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {
        
        @Test
        @DisplayName("Get nonexistent skill throws SkillNotFoundException")
        void getNonexistentSkill() {
            resourceLoader.setSkills(List.of());
            
            assertThatThrownBy(() -> skillsService.getSkill("nonexistent"))
                .isInstanceOf(SkillNotFoundException.class)
                .hasMessageContaining("nonexistent");
        }
        
        @Test
        @DisplayName("Create duplicate skill throws SkillAlreadyExistsException")
        void createDuplicateSkill() throws IOException {
            String skillName = "existing-skill";
            createTestSkill(projectSkillsDir, skillName, "project");
            resourceLoader.setSkills(List.of(
                createSkillRecord(skillName, projectSkillsDir, "project", "Existing")
            ));
            
            assertThatThrownBy(() -> 
                skillsService.createSkill(new CreateSkillRequest(skillName, "content")))
                .isInstanceOf(SkillAlreadyExistsException.class)
                .hasMessageContaining(skillName);
        }
        
        @Test
        @DisplayName("Invalid skill name throws InvalidSkillNameException")
        void invalidSkillName() {
            resourceLoader.setSkills(List.of());
            
            assertThatThrownBy(() -> 
                skillsService.createSkill(new CreateSkillRequest("invalid name with spaces", "content")))
                .isInstanceOf(InvalidSkillNameException.class);
        }
        
        @Test
        @DisplayName("Upload non-md file throws InvalidSkillNameException")
        void uploadNonMdFile() {
            resourceLoader.setSkills(List.of());
            
            assertThatThrownBy(() -> 
                skillsService.uploadSkill("skill", "content".getBytes(), "skill.txt"))
                .isInstanceOf(InvalidSkillNameException.class)
                .hasMessageContaining(".md");
        }
        
        @Test
        @DisplayName("Update nonexistent skill throws SkillNotFoundException")
        void updateNonexistentSkill() {
            resourceLoader.setSkills(List.of());
            
            assertThatThrownBy(() -> 
                skillsService.updateSkill("nonexistent", new UpdateSkillRequest("content")))
                .isInstanceOf(SkillNotFoundException.class);
        }
        
        @Test
        @DisplayName("Delete nonexistent skill throws SkillNotFoundException")
        void deleteNonexistentSkill() {
            resourceLoader.setSkills(List.of());
            
            assertThatThrownBy(() -> skillsService.deleteSkill("nonexistent"))
                .isInstanceOf(SkillNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("Skill Name Validation")
    class SkillNameValidation {
        
        @Test
        @DisplayName("Valid skill names are accepted")
        void validSkillNames() {
            assertThat(skillsService.validateSkillName("my-skill").valid()).isTrue();
            assertThat(skillsService.validateSkillName("mySkill").valid()).isTrue();
            assertThat(skillsService.validateSkillName("my_skill").valid()).isTrue();
            assertThat(skillsService.validateSkillName("skill123").valid()).isTrue();
            assertThat(skillsService.validateSkillName("a").valid()).isTrue();
        }
        
        @Test
        @DisplayName("Invalid skill names are rejected")
        void invalidSkillNames() {
            assertThat(skillsService.validateSkillName("").valid()).isFalse();
            assertThat(skillsService.validateSkillName(null).valid()).isFalse();
            assertThat(skillsService.validateSkillName("-starts-with-hyphen").valid()).isFalse();
            assertThat(skillsService.validateSkillName("_starts-with-underscore").valid()).isFalse();
            assertThat(skillsService.validateSkillName("has spaces").valid()).isFalse();
            assertThat(skillsService.validateSkillName("has@special").valid()).isFalse();
            assertThat(skillsService.validateSkillName("has/slash").valid()).isFalse();
        }
    }
    
    // ========== Helper Methods ==========
    
    private void createTestSkill(Path baseDir, String name, String source) throws IOException {
        String content = "---\ndescription: " + name + " description\n---\n# " + name;
        Path skillDir = baseDir.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), content);
    }
    
    private Skill createSkillRecord(String name, Path baseDir, String source, String description) {
        Path skillDir = baseDir.resolve(name);
        Path skillFile = skillDir.resolve("SKILL.md");
        return new Skill(
            name,
            description,
            skillFile.toString(),
            skillDir.toString(),
            source,
            false
        );
    }
    
    // ========== Test ResourceLoader ==========
    
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
