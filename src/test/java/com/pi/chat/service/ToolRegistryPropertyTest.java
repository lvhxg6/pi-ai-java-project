package com.pi.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.agent.types.AgentTool;
import com.pi.agent.types.AgentToolResult;
import com.pi.agent.types.AgentToolUpdateCallback;
import com.pi.ai.core.types.CancellationSignal;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for ToolRegistry.
 * 
 * <p>Tests the following correctness properties from the design document:
 * <ul>
 *   <li>Property 1: 工具注册和查找一致性</li>
 * </ul>
 * 
 * <p>Validates: Requirements 2.1, 2.5
 */
class ToolRegistryPropertyTest {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    @TempDir
    Path tempDir;
    
    private String cwd;
    
    @BeforeEach
    void setUp() {
        cwd = tempDir.toString();
    }
    
    // ========== Property 1: 工具注册和查找一致性 ==========
    
    /**
     * Feature: chat-tool-calling, Property 1: 工具注册和查找一致性
     * 
     * <p>For any AgentTool 实例，如果它被注册到 ToolRegistry，
     * 那么通过 findByName(tool.name()) 应该能够返回相同的工具实例。
     * 
     * <p>Validates: Requirements 2.1, 2.5
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 1: 工具注册和查找一致性")
    void toolRegistrationRoundTrip(@ForAll("validAgentTool") AgentTool tool) {
        ToolRegistry registry = new ToolRegistry(cwd);
        registry.register(tool);
        
        Optional<AgentTool> found = registry.findByName(tool.name());
        
        assertThat(found).isPresent();
        assertThat(found.get()).isSameAs(tool);
    }
    
    /**
     * Feature: chat-tool-calling, Property 1: 工具注册和查找一致性
     * 
     * <p>多个工具注册后，每个工具都应该能够通过名称正确查找。
     * 
     * <p>Validates: Requirements 2.1, 2.5
     */
    @Property(tries = 50)
    @Tag("Feature: chat-tool-calling, Property 1: 工具注册和查找一致性")
    void multipleToolsRegistrationRoundTrip(
            @ForAll @Size(min = 1, max = 10) List<@From("validAgentTool") AgentTool> tools) {
        
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // 注册所有工具
        for (AgentTool tool : tools) {
            registry.register(tool);
        }
        
        // 验证每个工具都能正确查找
        Set<String> registeredNames = new HashSet<>();
        for (AgentTool tool : tools) {
            registeredNames.add(tool.name());
            Optional<AgentTool> found = registry.findByName(tool.name());
            assertThat(found).isPresent();
            // 如果有重名工具，最后注册的会覆盖之前的
            assertThat(found.get().name()).isEqualTo(tool.name());
        }
        
        // 验证工具名称列表包含所有唯一名称
        List<String> toolNames = registry.getToolNames();
        assertThat(toolNames).containsAll(registeredNames);
    }
    
    /**
     * Feature: chat-tool-calling, Property 1: 工具注册和查找一致性
     * 
     * <p>不存在的工具名称应该返回空 Optional。
     * 
     * <p>Validates: Requirements 2.5
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 1: 工具注册和查找一致性")
    void nonExistentToolReturnsEmpty(@ForAll("randomToolName") String randomToolName) {
        // 排除默认工具名称
        Set<String> defaultToolNames = Set.of("bash", "read", "edit");
        Assume.that(!defaultToolNames.contains(randomToolName));
        
        ToolRegistry registry = new ToolRegistry(cwd);
        
        Optional<AgentTool> found = registry.findByName(randomToolName);
        
        assertThat(found).isEmpty();
    }
    
    /**
     * Feature: chat-tool-calling, Property 1: 工具注册和查找一致性
     * 
     * <p>重复注册同名工具时，后注册的工具应该覆盖先注册的。
     * 
     * <p>Validates: Requirements 2.1
     */
    @Property(tries = 50)
    @Tag("Feature: chat-tool-calling, Property 1: 工具注册和查找一致性")
    void duplicateRegistrationOverwritesPrevious(
            @ForAll("validToolName") String toolName,
            @ForAll("validToolDescription") String description1,
            @ForAll("validToolDescription") String description2) {
        
        Assume.that(!description1.equals(description2));
        
        ToolRegistry registry = new ToolRegistry(cwd);
        
        AgentTool tool1 = createMockTool(toolName, description1);
        AgentTool tool2 = createMockTool(toolName, description2);
        
        registry.register(tool1);
        registry.register(tool2);
        
        Optional<AgentTool> found = registry.findByName(toolName);
        
        assertThat(found).isPresent();
        assertThat(found.get()).isSameAs(tool2);
        assertThat(found.get().description()).isEqualTo(description2);
    }
    
    /**
     * Feature: chat-tool-calling, Property 1: 工具注册和查找一致性
     * 
     * <p>getAllTools() 返回的工具列表应该包含所有注册的工具。
     * 
     * <p>Validates: Requirements 2.1, 2.5
     */
    @Property(tries = 50)
    @Tag("Feature: chat-tool-calling, Property 1: 工具注册和查找一致性")
    void getAllToolsContainsAllRegisteredTools(
            @ForAll @Size(min = 0, max = 5) List<@From("validAgentTool") AgentTool> additionalTools) {
        
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // 默认工具数量
        int defaultToolCount = registry.size();
        
        // 注册额外工具
        Set<String> uniqueNames = new HashSet<>();
        for (AgentTool tool : additionalTools) {
            if (!registry.contains(tool.name())) {
                uniqueNames.add(tool.name());
            }
            registry.register(tool);
        }
        
        List<AgentTool> allTools = registry.getAllTools();
        
        // 验证工具数量
        assertThat(allTools.size()).isGreaterThanOrEqualTo(defaultToolCount);
        
        // 验证每个额外工具都在列表中
        for (AgentTool tool : additionalTools) {
            assertThat(allTools.stream().anyMatch(t -> t.name().equals(tool.name()))).isTrue();
        }
    }
    
    /**
     * Feature: chat-tool-calling, Property 1: 工具注册和查找一致性
     * 
     * <p>contains() 方法应该与 findByName() 结果一致。
     * 
     * <p>Validates: Requirements 2.5
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 1: 工具注册和查找一致性")
    void containsConsistentWithFindByName(@ForAll("anyToolName") String toolName) {
        ToolRegistry registry = new ToolRegistry(cwd);
        
        boolean contains = registry.contains(toolName);
        Optional<AgentTool> found = registry.findByName(toolName);
        
        assertThat(contains).isEqualTo(found.isPresent());
    }
    
    // ========== 单元测试补充 ==========
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 1: 工具注册和查找一致性")
    void defaultToolsAreRegistered() {
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // 验证默认工具已注册
        assertThat(registry.findByName("bash")).isPresent();
        assertThat(registry.findByName("read")).isPresent();
        assertThat(registry.findByName("edit")).isPresent();
        
        // 验证工具数量至少为 3
        assertThat(registry.size()).isGreaterThanOrEqualTo(3);
    }
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 1: 工具注册和查找一致性")
    void getToolNamesReturnsAllNames() {
        ToolRegistry registry = new ToolRegistry(cwd);
        
        List<String> names = registry.getToolNames();
        
        assertThat(names).contains("bash", "read", "edit");
        assertThat(names.size()).isEqualTo(registry.size());
    }
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 1: 工具注册和查找一致性")
    void getCwdReturnsConfiguredCwd() {
        ToolRegistry registry = new ToolRegistry(cwd);
        
        assertThat(registry.getCwd()).isEqualTo(cwd);
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<AgentTool> validAgentTool() {
        return Combinators.combine(
            validToolName(),
            validToolDescription()
        ).as(this::createMockTool);
    }
    
    @Provide
    Arbitrary<String> validToolName() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(3)
            .ofMaxLength(20)
            .filter(s -> !s.isEmpty() && Character.isLetter(s.charAt(0)));
    }
    
    @Provide
    Arbitrary<String> validToolDescription() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withChars(' ', '.', ',')
            .ofMinLength(10)
            .ofMaxLength(100);
    }
    
    @Provide
    Arbitrary<String> randomToolName() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('-', '_')
            .ofMinLength(5)
            .ofMaxLength(30)
            .filter(s -> !s.isEmpty() && Character.isLetter(s.charAt(0)));
    }
    
    @Provide
    Arbitrary<String> anyToolName() {
        return Arbitraries.oneOf(
            // 默认工具名称
            Arbitraries.of("bash", "read", "edit"),
            // 随机工具名称
            randomToolName()
        );
    }
    
    // ========== Helper Methods ==========
    
    /**
     * 创建一个 Mock AgentTool 实例用于测试。
     */
    private AgentTool createMockTool(String name, String description) {
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }
            
            @Override
            public String description() {
                return description;
            }
            
            @Override
            public JsonNode parameters() {
                try {
                    return OBJECT_MAPPER.readTree("{\"type\":\"object\",\"properties\":{}}");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            @Override
            public CompletableFuture<AgentToolResult<?>> execute(
                    String toolCallId,
                    JsonNode args,
                    CancellationSignal signal,
                    AgentToolUpdateCallback onUpdate) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
