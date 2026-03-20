package com.pi.chat.service;

import com.pi.agent.types.AgentTool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ToolRegistry.
 * 
 * <p>Tests cover:
 * <ul>
 *   <li>Default tool registration (bash, read, edit)</li>
 *   <li>Tool lookup (existing and non-existing)</li>
 *   <li>Tool list retrieval</li>
 *   <li>Tool name list retrieval</li>
 *   <li>Tool count statistics</li>
 * </ul>
 * 
 * <p>Validates: Requirements 2.1, 2.5
 */
class ToolRegistryTest {
    
    @TempDir
    Path tempDir;
    
    private String cwd;
    private ToolRegistry registry;
    
    @BeforeEach
    void setUp() {
        cwd = tempDir.toString();
        registry = new ToolRegistry(cwd);
    }
    
    @Nested
    @DisplayName("Default Tool Registration")
    class DefaultToolRegistrationTests {
        
        @Test
        @DisplayName("should register bash tool by default")
        void shouldRegisterBashTool() {
            Optional<AgentTool> bashTool = registry.findByName("bash");
            
            assertThat(bashTool).isPresent();
            assertThat(bashTool.get().name()).isEqualTo("bash");
        }
        
        @Test
        @DisplayName("should register read tool by default")
        void shouldRegisterReadTool() {
            Optional<AgentTool> readTool = registry.findByName("read");
            
            assertThat(readTool).isPresent();
            assertThat(readTool.get().name()).isEqualTo("read");
        }
        
        @Test
        @DisplayName("should register edit tool by default")
        void shouldRegisterEditTool() {
            Optional<AgentTool> editTool = registry.findByName("edit");
            
            assertThat(editTool).isPresent();
            assertThat(editTool.get().name()).isEqualTo("edit");
        }
        
        @Test
        @DisplayName("should have at least 3 default tools registered")
        void shouldHaveAtLeastThreeDefaultTools() {
            assertThat(registry.size()).isGreaterThanOrEqualTo(3);
        }
        
        @Test
        @DisplayName("default tools should have non-empty descriptions")
        void defaultToolsShouldHaveDescriptions() {
            List<AgentTool> tools = registry.getAllTools();
            
            for (AgentTool tool : tools) {
                assertThat(tool.description())
                    .as("Tool '%s' should have a description", tool.name())
                    .isNotNull()
                    .isNotEmpty();
            }
        }
        
        @Test
        @DisplayName("default tools should have parameters defined")
        void defaultToolsShouldHaveParameters() {
            List<AgentTool> tools = registry.getAllTools();
            
            for (AgentTool tool : tools) {
                assertThat(tool.parameters())
                    .as("Tool '%s' should have parameters", tool.name())
                    .isNotNull();
            }
        }
    }
    
    @Nested
    @DisplayName("Tool Lookup - Existing Tools")
    class ToolLookupExistingTests {
        
        @Test
        @DisplayName("should find existing tool by name")
        void shouldFindExistingToolByName() {
            Optional<AgentTool> tool = registry.findByName("bash");
            
            assertThat(tool).isPresent();
        }
        
        @Test
        @DisplayName("should return correct tool instance")
        void shouldReturnCorrectToolInstance() {
            Optional<AgentTool> tool = registry.findByName("bash");
            
            assertThat(tool).isPresent();
            assertThat(tool.get().name()).isEqualTo("bash");
        }
        
        @Test
        @DisplayName("contains should return true for existing tool")
        void containsShouldReturnTrueForExistingTool() {
            assertThat(registry.contains("bash")).isTrue();
            assertThat(registry.contains("read")).isTrue();
            assertThat(registry.contains("edit")).isTrue();
        }
        
        @Test
        @DisplayName("findByName and contains should be consistent")
        void findByNameAndContainsShouldBeConsistent() {
            String toolName = "bash";
            
            boolean contains = registry.contains(toolName);
            Optional<AgentTool> found = registry.findByName(toolName);
            
            assertThat(contains).isEqualTo(found.isPresent());
        }
    }
    
    @Nested
    @DisplayName("Tool Lookup - Non-Existing Tools")
    class ToolLookupNonExistingTests {
        
        @Test
        @DisplayName("should return empty Optional for non-existing tool")
        void shouldReturnEmptyForNonExistingTool() {
            Optional<AgentTool> tool = registry.findByName("non-existing-tool");
            
            assertThat(tool).isEmpty();
        }
        
        @Test
        @DisplayName("should throw NullPointerException for null name")
        void shouldThrowNullPointerExceptionForNullName() {
            // ConcurrentHashMap does not allow null keys
            assertThatThrownBy(() -> registry.findByName(null))
                .isInstanceOf(NullPointerException.class);
        }
        
        @Test
        @DisplayName("should return empty Optional for empty name")
        void shouldReturnEmptyForEmptyName() {
            Optional<AgentTool> tool = registry.findByName("");
            
            assertThat(tool).isEmpty();
        }
        
        @Test
        @DisplayName("contains should return false for non-existing tool")
        void containsShouldReturnFalseForNonExistingTool() {
            assertThat(registry.contains("non-existing-tool")).isFalse();
        }
        
        @Test
        @DisplayName("should be case-sensitive when looking up tools")
        void shouldBeCaseSensitive() {
            // "bash" exists, but "BASH" should not
            assertThat(registry.findByName("bash")).isPresent();
            assertThat(registry.findByName("BASH")).isEmpty();
            assertThat(registry.findByName("Bash")).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("Tool List Retrieval")
    class ToolListRetrievalTests {
        
        @Test
        @DisplayName("getAllTools should return all registered tools")
        void getAllToolsShouldReturnAllTools() {
            List<AgentTool> tools = registry.getAllTools();
            
            assertThat(tools).isNotEmpty();
            assertThat(tools.size()).isEqualTo(registry.size());
        }
        
        @Test
        @DisplayName("getAllTools should contain default tools")
        void getAllToolsShouldContainDefaultTools() {
            List<AgentTool> tools = registry.getAllTools();
            
            List<String> toolNames = tools.stream()
                .map(AgentTool::name)
                .toList();
            
            assertThat(toolNames).contains("bash", "read", "edit");
        }
        
        @Test
        @DisplayName("getAllTools should return a copy, not the internal collection")
        void getAllToolsShouldReturnCopy() {
            List<AgentTool> tools1 = registry.getAllTools();
            List<AgentTool> tools2 = registry.getAllTools();
            
            // Should be different list instances
            assertThat(tools1).isNotSameAs(tools2);
            // But with same content
            assertThat(tools1).containsExactlyInAnyOrderElementsOf(tools2);
        }
    }
    
    @Nested
    @DisplayName("Tool Name List Retrieval")
    class ToolNameListRetrievalTests {
        
        @Test
        @DisplayName("getToolNames should return all tool names")
        void getToolNamesShouldReturnAllNames() {
            List<String> names = registry.getToolNames();
            
            assertThat(names).isNotEmpty();
            assertThat(names.size()).isEqualTo(registry.size());
        }
        
        @Test
        @DisplayName("getToolNames should contain default tool names")
        void getToolNamesShouldContainDefaultNames() {
            List<String> names = registry.getToolNames();
            
            assertThat(names).contains("bash", "read", "edit");
        }
        
        @Test
        @DisplayName("getToolNames should return a copy, not the internal collection")
        void getToolNamesShouldReturnCopy() {
            List<String> names1 = registry.getToolNames();
            List<String> names2 = registry.getToolNames();
            
            // Should be different list instances
            assertThat(names1).isNotSameAs(names2);
            // But with same content
            assertThat(names1).containsExactlyInAnyOrderElementsOf(names2);
        }
        
        @Test
        @DisplayName("tool names should match getAllTools names")
        void toolNamesShouldMatchGetAllToolsNames() {
            List<String> names = registry.getToolNames();
            List<AgentTool> tools = registry.getAllTools();
            
            List<String> toolNames = tools.stream()
                .map(AgentTool::name)
                .toList();
            
            assertThat(names).containsExactlyInAnyOrderElementsOf(toolNames);
        }
    }
    
    @Nested
    @DisplayName("Tool Count Statistics")
    class ToolCountStatisticsTests {
        
        @Test
        @DisplayName("size should return correct count")
        void sizeShouldReturnCorrectCount() {
            int size = registry.size();
            
            assertThat(size).isGreaterThanOrEqualTo(3);
            assertThat(size).isEqualTo(registry.getAllTools().size());
            assertThat(size).isEqualTo(registry.getToolNames().size());
        }
        
        @Test
        @DisplayName("size should be consistent with getAllTools")
        void sizeShouldBeConsistentWithGetAllTools() {
            assertThat(registry.size()).isEqualTo(registry.getAllTools().size());
        }
        
        @Test
        @DisplayName("size should be consistent with getToolNames")
        void sizeShouldBeConsistentWithGetToolNames() {
            assertThat(registry.size()).isEqualTo(registry.getToolNames().size());
        }
    }
    
    @Nested
    @DisplayName("CWD Configuration")
    class CwdConfigurationTests {
        
        @Test
        @DisplayName("getCwd should return configured cwd")
        void getCwdShouldReturnConfiguredCwd() {
            assertThat(registry.getCwd()).isEqualTo(cwd);
        }
        
        @Test
        @DisplayName("different cwd should create different registry")
        void differentCwdShouldCreateDifferentRegistry() {
            String anotherCwd = tempDir.resolve("another").toString();
            ToolRegistry anotherRegistry = new ToolRegistry(anotherCwd);
            
            assertThat(anotherRegistry.getCwd()).isEqualTo(anotherCwd);
            assertThat(anotherRegistry.getCwd()).isNotEqualTo(registry.getCwd());
        }
    }
}
