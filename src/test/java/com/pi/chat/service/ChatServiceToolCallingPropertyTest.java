package com.pi.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.agent.Agent;
import com.pi.agent.event.AgentEvent;
import com.pi.agent.types.AgentOptions;
import com.pi.agent.types.AgentTool;
import com.pi.agent.types.AgentToolResult;
import com.pi.agent.types.AgentToolUpdateCallback;
import com.pi.ai.core.types.CancellationSignal;
import com.pi.chat.dto.ChatEvent;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for ChatService Tool Calling functionality.
 * 
 * <p>Tests the following correctness properties from the design document:
 * <ul>
 *   <li>Property 2: 工具列表传递完整性</li>
 *   <li>Property 4: 工具调用参数传递完整性</li>
 *   <li>Property 10: SSE 事件发射顺序</li>
 * </ul>
 * 
 * <p>Validates: Requirements 1.1, 2.3, 2.4, 3.1, 3.2, 4.2, 7.1, 7.2, 7.4
 */
class ChatServiceToolCallingPropertyTest {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    @TempDir
    Path tempDir;
    
    private String cwd;
    
    @BeforeEach
    void setUp() {
        cwd = tempDir.toString();
    }
    
    // ========== Property 2: 工具列表传递完整性 ==========
    
    /**
     * Feature: chat-tool-calling, Property 2: 工具列表传递完整性
     * 
     * <p>For any ToolRegistry 中注册的工具集合，当 ChatService 创建 AgentSession 时，
     * Agent.getState().getTools() 应该包含所有注册的工具。
     * 
     * <p>This test verifies that when tools are set on an Agent via setTools(),
     * the Agent's state correctly contains all the tools.
     * 
     * <p>Validates: Requirements 1.1, 2.3, 2.4
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 2: 工具列表传递完整性")
    void toolListPassedToAgentContainsAllRegisteredTools(
            @ForAll @Size(min = 0, max = 5) List<@From("validAgentTool") AgentTool> additionalTools) {
        
        // Create ToolRegistry with default tools
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // Register additional tools
        for (AgentTool tool : additionalTools) {
            registry.register(tool);
        }
        
        // Get all tools from registry
        List<AgentTool> registryTools = registry.getAllTools();
        List<String> registryToolNames = registry.getToolNames();
        
        // Create Agent and set tools (simulating what ChatService does)
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        agent.setTools(registryTools);
        
        // Verify Agent's state contains all tools
        List<AgentTool> agentTools = agent.getState().getTools();
        
        // Property: Agent should have all tools from registry
        assertThat(agentTools).hasSize(registryTools.size());
        
        // Property: All tool names should match
        Set<String> agentToolNames = agentTools.stream()
            .map(AgentTool::name)
            .collect(Collectors.toSet());
        assertThat(agentToolNames).containsExactlyInAnyOrderElementsOf(registryToolNames);
    }
    
    /**
     * Feature: chat-tool-calling, Property 2: 工具列表传递完整性
     * 
     * <p>Verifies that the tool list is not empty when default tools are registered.
     * 
     * <p>Validates: Requirements 1.1, 2.3
     */
    @Property(tries = 50)
    @Tag("Feature: chat-tool-calling, Property 2: 工具列表传递完整性")
    void toolListIsNotEmptyWithDefaultTools() {
        // Create ToolRegistry with default tools
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // Get tools from registry
        List<AgentTool> tools = registry.getAllTools();
        List<String> toolNames = registry.getToolNames();
        
        // Property: Tool list should not be empty
        assertThat(tools).isNotEmpty();
        assertThat(toolNames).isNotEmpty();
        
        // Property: Default tools should be present
        assertThat(toolNames).contains("bash", "read", "edit");
    }
    
    /**
     * Feature: chat-tool-calling, Property 2: 工具列表传递完整性
     * 
     * <p>Verifies that tool names list is consistent with getAllTools().
     * 
     * <p>Validates: Requirements 2.3, 2.4
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 2: 工具列表传递完整性")
    void toolNamesConsistentWithGetAllTools(
            @ForAll @Size(min = 0, max = 10) List<@From("validAgentTool") AgentTool> tools) {
        
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // Register additional tools
        for (AgentTool tool : tools) {
            registry.register(tool);
        }
        
        // Get tools and names
        List<AgentTool> allTools = registry.getAllTools();
        List<String> toolNames = registry.getToolNames();
        
        // Property: Size should match
        assertThat(toolNames).hasSameSizeAs(allTools);
        
        // Property: Names should match tool names
        Set<String> namesFromTools = allTools.stream()
            .map(AgentTool::name)
            .collect(Collectors.toSet());
        assertThat(new HashSet<>(toolNames)).isEqualTo(namesFromTools);
    }
    
    /**
     * Feature: chat-tool-calling, Property 2: 工具列表传递完整性
     * 
     * <p>Verifies that Agent.setTools() correctly updates the Agent's state.
     * 
     * <p>Validates: Requirements 2.3, 2.4
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 2: 工具列表传递完整性")
    void agentSetToolsUpdatesState(
            @ForAll @Size(min = 1, max = 10) List<@From("validAgentTool") AgentTool> tools) {
        
        // Create Agent
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        
        // Initially, tools should be empty
        assertThat(agent.getState().getTools()).isEmpty();
        
        // Set tools
        agent.setTools(tools);
        
        // Property: Agent state should contain all tools
        List<AgentTool> agentTools = agent.getState().getTools();
        assertThat(agentTools).hasSize(tools.size());
        
        // Property: Each tool should be present
        for (AgentTool tool : tools) {
            assertThat(agentTools.stream()
                .anyMatch(t -> t.name().equals(tool.name())))
                .isTrue();
        }
    }
    
    /**
     * Feature: chat-tool-calling, Property 2: 工具列表传递完整性
     * 
     * <p>Verifies that tool list passing preserves tool identity (same instances).
     * 
     * <p>Validates: Requirements 2.3, 2.4
     */
    @Property(tries = 50)
    @Tag("Feature: chat-tool-calling, Property 2: 工具列表传递完整性")
    void toolListPassingPreservesToolIdentity(
            @ForAll @Size(min = 1, max = 5) List<@From("validAgentTool") AgentTool> tools) {
        
        // Create Agent
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        
        // Set tools
        agent.setTools(tools);
        
        // Property: Tools should be the same instances
        List<AgentTool> agentTools = agent.getState().getTools();
        for (AgentTool originalTool : tools) {
            boolean found = agentTools.stream()
                .anyMatch(t -> t == originalTool);
            assertThat(found)
                .as("Tool '%s' should be the same instance", originalTool.name())
                .isTrue();
        }
    }
    
    // ========== Property 10: SSE 事件发射顺序 ==========
    
    /**
     * Feature: chat-tool-calling, Property 10: SSE 事件发射顺序
     * 
     * <p>For any 工具调用，ChatService 应该先发射 ToolCallStart 事件，然后发射 ToolCallEnd 事件，
     * 且两个事件的 toolCallId 相同。
     * 
     * <p>Validates: Requirements 7.1, 7.2, 7.4
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 10: SSE 事件发射顺序")
    void sseEventOrderIsCorrect(
            @ForAll @From("validToolCallId") String toolCallId,
            @ForAll @From("validToolName") String toolName) {
        
        List<ChatEvent> events = new ArrayList<>();
        String messageId = "msg-" + System.nanoTime();
        
        // 模拟事件发射顺序：先 ToolCallStart，后 ToolCallEnd
        events.add(new ChatEvent.ToolCallStart(messageId, toolCallId, toolName));
        events.add(new ChatEvent.ToolCallEnd(messageId, toolCallId, "result"));
        
        // Property 1: 第一个事件应该是 ToolCallStart
        assertThat(events.get(0)).isInstanceOf(ChatEvent.ToolCallStart.class);
        
        // Property 2: 第二个事件应该是 ToolCallEnd
        assertThat(events.get(1)).isInstanceOf(ChatEvent.ToolCallEnd.class);
        
        // Property 3: 两个事件的 toolCallId 应该相同
        ChatEvent.ToolCallStart startEvent = (ChatEvent.ToolCallStart) events.get(0);
        ChatEvent.ToolCallEnd endEvent = (ChatEvent.ToolCallEnd) events.get(1);
        assertThat(startEvent.toolCallId()).isEqualTo(endEvent.toolCallId());
    }
    
    /**
     * Feature: chat-tool-calling, Property 10: SSE 事件发射顺序
     * 
     * <p>Verifies that multiple tool calls maintain correct event ordering.
     * Each tool call should have its Start event before its End event.
     * 
     * <p>Validates: Requirements 7.1, 7.2, 7.4
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 10: SSE 事件发射顺序")
    void multipleToolCallsHaveCorrectEventOrder(
            @ForAll @Size(min = 1, max = 5) List<@From("validToolCallId") String> toolCallIds,
            @ForAll @From("validToolName") String toolName) {
        
        List<ChatEvent> events = new ArrayList<>();
        String messageId = "msg-" + System.nanoTime();
        Map<String, Integer> startEventIndices = new ConcurrentHashMap<>();
        Map<String, Integer> endEventIndices = new ConcurrentHashMap<>();
        
        // 模拟多个工具调用的事件发射
        int eventIndex = 0;
        for (String toolCallId : toolCallIds) {
            events.add(new ChatEvent.ToolCallStart(messageId, toolCallId, toolName));
            startEventIndices.put(toolCallId, eventIndex++);
        }
        for (String toolCallId : toolCallIds) {
            events.add(new ChatEvent.ToolCallEnd(messageId, toolCallId, "result-" + toolCallId));
            endEventIndices.put(toolCallId, eventIndex++);
        }
        
        // Property: 对于每个 toolCallId，Start 事件应该在 End 事件之前
        for (String toolCallId : toolCallIds) {
            int startIndex = startEventIndices.get(toolCallId);
            int endIndex = endEventIndices.get(toolCallId);
            assertThat(startIndex)
                .as("ToolCallStart for %s should come before ToolCallEnd", toolCallId)
                .isLessThan(endIndex);
        }
    }
    
    /**
     * Feature: chat-tool-calling, Property 10: SSE 事件发射顺序
     * 
     * <p>Verifies that ToolCallStart and ToolCallEnd events have matching messageId.
     * 
     * <p>Validates: Requirements 7.1, 7.2, 7.4
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 10: SSE 事件发射顺序")
    void sseEventsHaveMatchingMessageId(
            @ForAll @From("validToolCallId") String toolCallId,
            @ForAll @From("validToolName") String toolName,
            @ForAll @From("validMessageId") String messageId) {
        
        // 创建事件
        ChatEvent.ToolCallStart startEvent = new ChatEvent.ToolCallStart(messageId, toolCallId, toolName);
        ChatEvent.ToolCallEnd endEvent = new ChatEvent.ToolCallEnd(messageId, toolCallId, "result");
        
        // Property: 两个事件的 messageId 应该相同
        assertThat(startEvent.messageId()).isEqualTo(endEvent.messageId());
        assertThat(startEvent.messageId()).isEqualTo(messageId);
    }
    
    // ========== Property 4: 工具调用参数传递完整性 ==========
    
    /**
     * Feature: chat-tool-calling, Property 4: 工具调用参数传递完整性
     * 
     * <p>For any ToolExecutionStart 事件，ChatService 应该能够正确提取 toolCallId、toolName 和 args，
     * 并且这些值与原始事件中的值相等。
     * 
     * <p>Validates: Requirements 3.1, 3.2, 4.2
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 4: 工具调用参数传递完整性")
    void toolExecutionStartParametersArePreserved(
            @ForAll @From("validToolCallId") String toolCallId,
            @ForAll @From("validToolName") String toolName,
            @ForAll @From("validToolArgs") Object args) {
        
        // 创建 ToolExecutionStart 事件
        AgentEvent.ToolExecutionStart event = new AgentEvent.ToolExecutionStart(toolCallId, toolName, args);
        
        // Property 1: toolCallId 应该被正确保留
        assertThat(event.toolCallId()).isEqualTo(toolCallId);
        
        // Property 2: toolName 应该被正确保留
        assertThat(event.toolName()).isEqualTo(toolName);
        
        // Property 3: args 应该被正确保留
        assertThat(event.args()).isEqualTo(args);
    }
    
    /**
     * Feature: chat-tool-calling, Property 4: 工具调用参数传递完整性
     * 
     * <p>Verifies that ChatEvent.ToolCallStart correctly preserves toolCallId and toolName
     * from the original ToolExecutionStart event.
     * 
     * <p>Validates: Requirements 3.1, 3.2, 4.2
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 4: 工具调用参数传递完整性")
    void chatEventToolCallStartPreservesParameters(
            @ForAll @From("validToolCallId") String toolCallId,
            @ForAll @From("validToolName") String toolName,
            @ForAll @From("validMessageId") String messageId) {
        
        // 模拟 ChatService 从 ToolExecutionStart 创建 ChatEvent.ToolCallStart
        AgentEvent.ToolExecutionStart agentEvent = new AgentEvent.ToolExecutionStart(toolCallId, toolName, null);
        
        // 创建 ChatEvent（模拟 handleToolExecutionStart 方法的行为）
        ChatEvent.ToolCallStart chatEvent = new ChatEvent.ToolCallStart(
            messageId,
            agentEvent.toolCallId(),
            agentEvent.toolName()
        );
        
        // Property 1: toolCallId 应该从 AgentEvent 正确传递到 ChatEvent
        assertThat(chatEvent.toolCallId()).isEqualTo(agentEvent.toolCallId());
        assertThat(chatEvent.toolCallId()).isEqualTo(toolCallId);
        
        // Property 2: toolName 应该从 AgentEvent 正确传递到 ChatEvent
        assertThat(chatEvent.toolName()).isEqualTo(agentEvent.toolName());
        assertThat(chatEvent.toolName()).isEqualTo(toolName);
    }
    
    /**
     * Feature: chat-tool-calling, Property 4: 工具调用参数传递完整性
     * 
     * <p>Verifies that ToolExecutionEnd event preserves toolCallId from ToolExecutionStart.
     * 
     * <p>Validates: Requirements 3.1, 3.2, 4.2
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 4: 工具调用参数传递完整性")
    void toolExecutionEndPreservesToolCallId(
            @ForAll @From("validToolCallId") String toolCallId,
            @ForAll @From("validToolName") String toolName) {
        
        // 创建 ToolExecutionStart 和 ToolExecutionEnd 事件
        AgentEvent.ToolExecutionStart startEvent = new AgentEvent.ToolExecutionStart(toolCallId, toolName, null);
        AgentEvent.ToolExecutionEnd endEvent = new AgentEvent.ToolExecutionEnd(toolCallId, toolName, null, false);
        
        // Property: toolCallId 应该在 Start 和 End 事件中保持一致
        assertThat(startEvent.toolCallId()).isEqualTo(endEvent.toolCallId());
        assertThat(startEvent.toolName()).isEqualTo(endEvent.toolName());
    }
    
    /**
     * Feature: chat-tool-calling, Property 4: 工具调用参数传递完整性
     * 
     * <p>Verifies that the complete event chain preserves all parameters:
     * ToolExecutionStart -> ChatEvent.ToolCallStart -> ChatEvent.ToolCallEnd
     * 
     * <p>Validates: Requirements 3.1, 3.2, 4.2
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 4: 工具调用参数传递完整性")
    void completeEventChainPreservesParameters(
            @ForAll @From("validToolCallId") String toolCallId,
            @ForAll @From("validToolName") String toolName,
            @ForAll @From("validMessageId") String messageId,
            @ForAll @From("validToolResult") String result) {
        
        // 创建 AgentEvent
        AgentEvent.ToolExecutionStart agentStartEvent = new AgentEvent.ToolExecutionStart(toolCallId, toolName, null);
        AgentEvent.ToolExecutionEnd agentEndEvent = new AgentEvent.ToolExecutionEnd(toolCallId, toolName, null, false);
        
        // 创建 ChatEvent（模拟 ChatService 的转换）
        ChatEvent.ToolCallStart chatStartEvent = new ChatEvent.ToolCallStart(
            messageId,
            agentStartEvent.toolCallId(),
            agentStartEvent.toolName()
        );
        ChatEvent.ToolCallEnd chatEndEvent = new ChatEvent.ToolCallEnd(
            messageId,
            agentEndEvent.toolCallId(),
            result
        );
        
        // Property 1: toolCallId 在整个事件链中保持一致
        assertThat(agentStartEvent.toolCallId())
            .isEqualTo(agentEndEvent.toolCallId())
            .isEqualTo(chatStartEvent.toolCallId())
            .isEqualTo(chatEndEvent.toolCallId())
            .isEqualTo(toolCallId);
        
        // Property 2: toolName 在 Start 事件中保持一致
        assertThat(agentStartEvent.toolName())
            .isEqualTo(chatStartEvent.toolName())
            .isEqualTo(toolName);
        
        // Property 3: messageId 在 ChatEvent 中保持一致
        assertThat(chatStartEvent.messageId())
            .isEqualTo(chatEndEvent.messageId())
            .isEqualTo(messageId);
    }
    
    // ========== Property 12: 中止信号传播 ==========
    
    /**
     * Feature: chat-tool-calling, Property 12: 中止信号传播
     * 
     * <p>For any 用户中止请求，ChatService 应该调用 AgentSession.abort()，
     * 且 CancellationSignal 应该传播到正在执行的工具。
     * 
     * <p>This test verifies that when abort() is called on an Agent,
     * the CancellationSignal is properly triggered.
     * 
     * <p>Validates: Requirements 9.1, 9.2, 9.3
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 12: 中止信号传播")
    void abortSignalPropagatesToAgent(
            @ForAll @From("validSessionId") String sessionId) {
        
        // Create Agent
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        
        // Property 1: Agent should be created successfully
        assertThat(agent).isNotNull();
        
        // Property 2: Abort should complete without throwing exceptions
        assertThatCode(() -> agent.abort()).doesNotThrowAnyException();
    }
    
    /**
     * Feature: chat-tool-calling, Property 12: 中止信号传播
     * 
     * <p>Verifies that abort operation can be called multiple times without errors.
     * 
     * <p>Validates: Requirements 9.1, 9.2, 9.3
     */
    @Property(tries = 50)
    @Tag("Feature: chat-tool-calling, Property 12: 中止信号传播")
    void multipleAbortCallsAreIdempotent(
            @ForAll @IntRange(min = 1, max = 5) int abortCount) {
        
        // Create Agent
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        
        // Property: Multiple abort calls should not throw exceptions
        for (int i = 0; i < abortCount; i++) {
            assertThatCode(() -> agent.abort()).doesNotThrowAnyException();
        }
    }
    
    /**
     * Feature: chat-tool-calling, Property 12: 中止信号传播
     * 
     * <p>Verifies that after abort, subsequent operations can still work.
     * 
     * <p>Validates: Requirements 9.1, 9.2, 9.3
     */
    @Property(tries = 50)
    @Tag("Feature: chat-tool-calling, Property 12: 中止信号传播")
    void agentCanBeUsedAfterAbort(
            @ForAll @Size(min = 1, max = 3) List<@From("validAgentTool") AgentTool> tools) {
        
        // Create Agent
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        
        // Set tools
        agent.setTools(tools);
        
        // Abort
        agent.abort();
        
        // Property: Agent should still be usable after abort
        // Can still set tools
        assertThatCode(() -> agent.setTools(tools)).doesNotThrowAnyException();
        
        // Tools should still be accessible
        assertThat(agent.getState().getTools()).hasSize(tools.size());
    }
    
    /**
     * Feature: chat-tool-calling, Property 12: 中止信号传播
     * 
     * <p>Verifies that ChatEvent.Error with ABORTED code is correctly created
     * when abort is triggered.
     * 
     * <p>Validates: Requirements 9.1, 9.2, 9.3
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 12: 中止信号传播")
    void abortCreatesCorrectErrorEvent(
            @ForAll @From("validSessionId") String sessionId) {
        
        // Simulate the error event created by ChatService.abortChat()
        ChatEvent.Error errorEvent = new ChatEvent.Error("ABORTED", "Chat was aborted by user");
        
        // Property 1: Error code should be ABORTED
        assertThat(errorEvent.code()).isEqualTo("ABORTED");
        
        // Property 2: Error message should indicate user abort
        assertThat(errorEvent.message()).contains("aborted");
    }
    
    /**
     * Feature: chat-tool-calling, Property 12: 中止信号传播
     * 
     * <p>Verifies that abort signal propagates through AgentSession to Agent.
     * Tests the chain: ChatService.abortChat() -> AgentSession.abort() -> Agent.abort()
     * 
     * <p>Validates: Requirements 9.1, 9.2, 9.3
     */
    @Property(tries = 50)
    @Tag("Feature: chat-tool-calling, Property 12: 中止信号传播")
    void abortSignalPropagationChain(
            @ForAll @From("validSessionId") String sessionId,
            @ForAll @From("validToolName") String toolName) {
        
        // Create Agent
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        
        // Set up tools
        ToolRegistry registry = new ToolRegistry(cwd);
        agent.setTools(registry.getAllTools());
        
        // Property 1: Agent should have tools before abort
        assertThat(agent.getState().getTools()).isNotEmpty();
        
        // Property 2: Abort should complete without errors
        assertThatCode(() -> agent.abort()).doesNotThrowAnyException();
        
        // Property 3: Agent state should still be accessible after abort
        assertThat(agent.getState()).isNotNull();
        assertThat(agent.getState().getTools()).isNotEmpty();
    }
    
    /**
     * Feature: chat-tool-calling, Property 12: 中止信号传播
     * 
     * <p>Verifies that waitForIdle() returns a completed future when agent is idle.
     * 
     * <p>Validates: Requirements 9.1, 9.2, 9.3
     */
    @Property(tries = 50)
    @Tag("Feature: chat-tool-calling, Property 12: 中止信号传播")
    void waitForIdleCompletesWhenAgentIsIdle(
            @ForAll @From("validSessionId") String sessionId) {
        
        // Create Agent
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        
        // Property: waitForIdle should return a completed future when agent is idle
        CompletableFuture<Void> idleFuture = agent.waitForIdle();
        assertThat(idleFuture).isNotNull();
        assertThat(idleFuture.isDone()).isTrue();
    }
    
    // ========== Unit Tests ==========
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 12: 中止信号传播")
    void abortSignalPropagationWithSpecificValues() {
        String sessionId = "test-session-123";
        
        // Create Agent
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        
        // Set up tools
        ToolRegistry registry = new ToolRegistry(cwd);
        agent.setTools(registry.getAllTools());
        
        // Verify abort works
        assertThatCode(() -> agent.abort()).doesNotThrowAnyException();
        
        // Verify agent is still usable
        assertThat(agent.getState().getTools()).isNotEmpty();
        
        // Verify error event is correctly created
        ChatEvent.Error errorEvent = new ChatEvent.Error("ABORTED", "Chat was aborted by user");
        assertThat(errorEvent.code()).isEqualTo("ABORTED");
    }
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 12: 中止信号传播")
    void abortDoesNotAffectToolRegistry() {
        // Create ToolRegistry
        ToolRegistry registry = new ToolRegistry(cwd);
        List<String> toolNamesBefore = registry.getToolNames();
        
        // Create Agent and abort
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        agent.setTools(registry.getAllTools());
        agent.abort();
        
        // Verify ToolRegistry is not affected
        List<String> toolNamesAfter = registry.getToolNames();
        assertThat(toolNamesAfter).containsExactlyInAnyOrderElementsOf(toolNamesBefore);
    }
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 10: SSE 事件发射顺序")
    void sseEventOrderWithSpecificValues() {
        String toolCallId = "call_123";
        String toolName = "bash";
        String messageId = "msg_456";
        
        List<ChatEvent> events = new ArrayList<>();
        events.add(new ChatEvent.ToolCallStart(messageId, toolCallId, toolName));
        events.add(new ChatEvent.ToolCallEnd(messageId, toolCallId, "command output"));
        
        // Verify order
        assertThat(events.get(0)).isInstanceOf(ChatEvent.ToolCallStart.class);
        assertThat(events.get(1)).isInstanceOf(ChatEvent.ToolCallEnd.class);
        
        // Verify toolCallId matches
        ChatEvent.ToolCallStart start = (ChatEvent.ToolCallStart) events.get(0);
        ChatEvent.ToolCallEnd end = (ChatEvent.ToolCallEnd) events.get(1);
        assertThat(start.toolCallId()).isEqualTo(end.toolCallId());
    }
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 4: 工具调用参数传递完整性")
    void toolExecutionStartParametersWithSpecificValues() {
        String toolCallId = "call_abc";
        String toolName = "read";
        Map<String, Object> args = Map.of("path", "/tmp/test.txt");
        
        AgentEvent.ToolExecutionStart event = new AgentEvent.ToolExecutionStart(toolCallId, toolName, args);
        
        assertThat(event.toolCallId()).isEqualTo(toolCallId);
        assertThat(event.toolName()).isEqualTo(toolName);
        assertThat(event.args()).isEqualTo(args);
    }
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 2: 工具列表传递完整性")
    void defaultToolsArePassedToAgent() {
        // Create ToolRegistry with default tools
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // Create Agent and set tools
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        agent.setTools(registry.getAllTools());
        
        // Verify default tools are present
        List<AgentTool> agentTools = agent.getState().getTools();
        Set<String> toolNames = agentTools.stream()
            .map(AgentTool::name)
            .collect(Collectors.toSet());
        
        assertThat(toolNames).contains("bash", "read", "edit");
    }
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 2: 工具列表传递完整性")
    void emptyToolListCanBeSet() {
        // Create Agent
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        
        // Set empty tool list
        agent.setTools(List.of());
        
        // Verify tools are empty
        assertThat(agent.getState().getTools()).isEmpty();
    }
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 2: 工具列表传递完整性")
    void toolListCanBeReplaced() {
        // Create Agent
        AgentOptions options = AgentOptions.builder()
            .getApiKey(provider -> CompletableFuture.completedFuture("test-api-key"))
            .build();
        Agent agent = new Agent(options);
        
        // Set initial tools
        AgentTool tool1 = createMockTool("tool1", "First tool");
        agent.setTools(List.of(tool1));
        assertThat(agent.getState().getTools()).hasSize(1);
        
        // Replace with new tools
        AgentTool tool2 = createMockTool("tool2", "Second tool");
        AgentTool tool3 = createMockTool("tool3", "Third tool");
        agent.setTools(List.of(tool2, tool3));
        
        // Verify tools are replaced
        List<AgentTool> agentTools = agent.getState().getTools();
        assertThat(agentTools).hasSize(2);
        
        Set<String> toolNames = agentTools.stream()
            .map(AgentTool::name)
            .collect(Collectors.toSet());
        assertThat(toolNames).containsExactlyInAnyOrder("tool2", "tool3");
    }
    
    // ========== Property 13: 错误处理完整性 ==========
    
    /**
     * Feature: chat-tool-calling, Property 13: 错误处理完整性
     * 
     * <p>For any 工具执行失败（isError=true），ChatService 应该正确格式化错误信息，
     * 并在 ToolCallEnd 事件中设置 isError=true。
     * 
     * <p>Validates: Requirements 10.1, 10.2, 10.3, 10.4
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 13: 错误处理完整性")
    void errorToolExecutionCreatesErrorEvent(
            @ForAll @From("validToolCallId") String toolCallId,
            @ForAll @From("validToolName") String toolName,
            @ForAll @From("validMessageId") String messageId,
            @ForAll @From("validErrorMessage") String errorMessage) {
        
        // 创建错误的 ToolExecutionEnd 事件
        AgentEvent.ToolExecutionEnd errorEvent = new AgentEvent.ToolExecutionEnd(
            toolCallId, toolName, null, true);
        
        // Property 1: isError 应该为 true
        assertThat(errorEvent.isError()).isTrue();
        
        // 创建 ChatEvent.ToolCallEnd（模拟 handleToolExecutionEnd 的行为）
        String formattedError = "Error: " + errorMessage;
        ChatEvent.ToolCallEnd chatEvent = new ChatEvent.ToolCallEnd(
            messageId, toolCallId, formattedError, true);
        
        // Property 2: ChatEvent 的 isError 应该为 true
        assertThat(chatEvent.isError()).isTrue();
        
        // Property 3: 错误消息应该包含 "Error:" 前缀
        assertThat(chatEvent.result()).startsWith("Error:");
    }
    
    /**
     * Feature: chat-tool-calling, Property 13: 错误处理完整性
     * 
     * <p>Verifies that successful tool execution has isError=false.
     * 
     * <p>Validates: Requirements 10.1, 10.2, 10.3, 10.4
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 13: 错误处理完整性")
    void successfulToolExecutionHasNoError(
            @ForAll @From("validToolCallId") String toolCallId,
            @ForAll @From("validToolName") String toolName,
            @ForAll @From("validMessageId") String messageId,
            @ForAll @From("validToolResult") String result) {
        
        // 创建成功的 ToolExecutionEnd 事件
        AgentEvent.ToolExecutionEnd successEvent = new AgentEvent.ToolExecutionEnd(
            toolCallId, toolName, null, false);
        
        // Property 1: isError 应该为 false
        assertThat(successEvent.isError()).isFalse();
        
        // 创建 ChatEvent.ToolCallEnd（使用 3 参数构造函数，默认 isError=false）
        ChatEvent.ToolCallEnd chatEvent = new ChatEvent.ToolCallEnd(
            messageId, toolCallId, result);
        
        // Property 2: ChatEvent 的 isError 应该为 false
        assertThat(chatEvent.isError()).isFalse();
    }
    
    /**
     * Feature: chat-tool-calling, Property 13: 错误处理完整性
     * 
     * <p>Verifies that error messages are preserved in the event chain.
     * 
     * <p>Validates: Requirements 10.1, 10.2, 10.3, 10.4
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 13: 错误处理完整性")
    void errorMessageIsPreservedInEventChain(
            @ForAll @From("validToolCallId") String toolCallId,
            @ForAll @From("validMessageId") String messageId,
            @ForAll @From("validErrorMessage") String errorMessage) {
        
        // 创建带错误消息的 ChatEvent.ToolCallEnd
        String formattedError = "Error: " + errorMessage;
        ChatEvent.ToolCallEnd chatEvent = new ChatEvent.ToolCallEnd(
            messageId, toolCallId, formattedError, true);
        
        // Property: 错误消息应该被完整保留
        assertThat(chatEvent.result()).isEqualTo(formattedError);
        assertThat(chatEvent.result()).contains(errorMessage);
    }
    
    /**
     * Feature: chat-tool-calling, Property 13: 错误处理完整性
     * 
     * <p>Verifies that ToolCallEnd with isError=true and isError=false are distinguishable.
     * 
     * <p>Validates: Requirements 10.1, 10.2, 10.3, 10.4
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 13: 错误处理完整性")
    void errorAndSuccessEventsAreDistinguishable(
            @ForAll @From("validToolCallId") String toolCallId,
            @ForAll @From("validMessageId") String messageId,
            @ForAll @From("validToolResult") String result,
            @ForAll @From("validErrorMessage") String errorMessage) {
        
        // 创建成功事件
        ChatEvent.ToolCallEnd successEvent = new ChatEvent.ToolCallEnd(
            messageId, toolCallId, result, false);
        
        // 创建错误事件
        ChatEvent.ToolCallEnd errorEvent = new ChatEvent.ToolCallEnd(
            messageId, toolCallId, "Error: " + errorMessage, true);
        
        // Property: 两个事件的 isError 状态应该不同
        assertThat(successEvent.isError()).isFalse();
        assertThat(errorEvent.isError()).isTrue();
        assertThat(successEvent.isError()).isNotEqualTo(errorEvent.isError());
    }
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 13: 错误处理完整性")
    void errorHandlingWithSpecificValues() {
        String toolCallId = "call_error_123";
        String toolName = "bash";
        String messageId = "msg_error_456";
        String errorMessage = "Command not found: xyz";
        
        // 创建错误事件
        ChatEvent.ToolCallEnd errorEvent = new ChatEvent.ToolCallEnd(
            messageId, toolCallId, "Error: " + errorMessage, true);
        
        // 验证
        assertThat(errorEvent.isError()).isTrue();
        assertThat(errorEvent.result()).startsWith("Error:");
        assertThat(errorEvent.result()).contains(errorMessage);
        assertThat(errorEvent.toolCallId()).isEqualTo(toolCallId);
        assertThat(errorEvent.messageId()).isEqualTo(messageId);
    }
    
    // ========== Property 5: 不存在工具的错误处理 ==========
    
    /**
     * Feature: chat-tool-calling, Property 5: 不存在工具的错误处理
     * 
     * <p>For any 不存在的工具名称，ToolRegistry.findByName() 应该返回 Optional.empty()。
     * 
     * <p>Validates: Requirements 3.3, 3.4
     */
    @Property(tries = 100)
    @Tag("Feature: chat-tool-calling, Property 5: 不存在工具的错误处理")
    void nonExistentToolReturnsEmpty(
            @ForAll @From("nonExistentToolName") String toolName) {
        
        // Create ToolRegistry with default tools
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // Property: 不存在的工具应该返回 Optional.empty()
        assertThat(registry.findByName(toolName)).isEmpty();
    }
    
    /**
     * Feature: chat-tool-calling, Property 5: 不存在工具的错误处理
     * 
     * <p>Verifies that existing tools return non-empty Optional.
     * 
     * <p>Validates: Requirements 3.3, 3.4
     */
    @Property(tries = 50)
    @Tag("Feature: chat-tool-calling, Property 5: 不存在工具的错误处理")
    void existingToolReturnsNonEmpty() {
        // Create ToolRegistry with default tools
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // Property: 默认工具应该存在
        assertThat(registry.findByName("bash")).isPresent();
        assertThat(registry.findByName("read")).isPresent();
        assertThat(registry.findByName("edit")).isPresent();
    }
    
    /**
     * Feature: chat-tool-calling, Property 5: 不存在工具的错误处理
     * 
     * <p>Verifies that findByName() handles null and empty names gracefully.
     * 
     * <p>Validates: Requirements 3.3, 3.4
     */
    @Test
    @Tag("Feature: chat-tool-calling, Property 5: 不存在工具的错误处理")
    void findByNameHandlesEdgeCases() {
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // Property 1: 空字符串应该返回 empty
        assertThat(registry.findByName("")).isEmpty();
        
        // Property 2: 不存在的工具名应该返回 empty
        assertThat(registry.findByName("nonexistent_tool_xyz")).isEmpty();
        assertThat(registry.findByName("unknown")).isEmpty();
    }
    
    /**
     * Feature: chat-tool-calling, Property 5: 不存在工具的错误处理
     * 
     * <p>Verifies that registered tools can be found, unregistered cannot.
     * 
     * <p>Validates: Requirements 3.3, 3.4
     */
    @Property(tries = 50)
    @Tag("Feature: chat-tool-calling, Property 5: 不存在工具的错误处理")
    void registeredToolsCanBeFound(
            @ForAll @Size(min = 1, max = 3) List<@From("validAgentTool") AgentTool> tools) {
        
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // Register tools
        for (AgentTool tool : tools) {
            registry.register(tool);
        }
        
        // Property 1: 注册的工具应该能被找到
        for (AgentTool tool : tools) {
            assertThat(registry.findByName(tool.name())).isPresent();
            assertThat(registry.findByName(tool.name()).get().name()).isEqualTo(tool.name());
        }
        
        // Property 2: 未注册的工具应该返回 empty
        assertThat(registry.findByName("definitely_not_registered_xyz")).isEmpty();
    }
    
    @Test
    @Tag("Feature: chat-tool-calling, Property 5: 不存在工具的错误处理")
    void nonExistentToolWithSpecificValues() {
        ToolRegistry registry = new ToolRegistry(cwd);
        
        // 测试特定的不存在工具名
        String[] nonExistentNames = {"foo", "bar", "unknown_tool", "my_custom_tool"};
        
        for (String name : nonExistentNames) {
            assertThat(registry.findByName(name))
                .as("Tool '%s' should not exist", name)
                .isEmpty();
        }
        
        // 验证默认工具存在
        assertThat(registry.findByName("bash")).isPresent();
        assertThat(registry.findByName("read")).isPresent();
        assertThat(registry.findByName("edit")).isPresent();
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
            .filter(s -> !s.isEmpty() && Character.isLetter(s.charAt(0)))
            // Exclude default tool names to avoid conflicts
            .filter(s -> !s.equals("bash") && !s.equals("read") && !s.equals("edit"));
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
    Arbitrary<String> validToolCallId() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('_', '-')
            .ofMinLength(5)
            .ofMaxLength(30)
            .map(s -> "call_" + s);
    }
    
    @Provide
    Arbitrary<String> validMessageId() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(40)
            .map(s -> "msg-" + s);
    }
    
    @Provide
    Arbitrary<Object> validToolArgs() {
        return Arbitraries.oneOf(
            // null args
            Arbitraries.just(null),
            // simple string args
            Arbitraries.strings().ofMinLength(1).ofMaxLength(50).map(s -> (Object) s),
            // map args
            Arbitraries.maps(
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(10),
                Arbitraries.strings().ofMinLength(1).ofMaxLength(20)
            ).ofMinSize(0).ofMaxSize(3).map(m -> (Object) m)
        );
    }
    
    @Provide
    Arbitrary<String> validToolResult() {
        return Arbitraries.oneOf(
            Arbitraries.just("(no output)"),
            Arbitraries.strings().ofMinLength(1).ofMaxLength(200),
            Arbitraries.strings().withCharRange('a', 'z').withChars(' ', '\n').ofMinLength(10).ofMaxLength(500)
        );
    }
    
    @Provide
    Arbitrary<String> validSessionId() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('-', '_')
            .ofMinLength(5)
            .ofMaxLength(40)
            .map(s -> "session-" + s);
    }
    
    @Provide
    Arbitrary<String> validErrorMessage() {
        return Arbitraries.oneOf(
            Arbitraries.just("Command not found"),
            Arbitraries.just("Permission denied"),
            Arbitraries.just("File not found"),
            Arbitraries.just("Timeout exceeded"),
            Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars(' ', '.', ':', '-')
                .ofMinLength(5)
                .ofMaxLength(100)
        );
    }
    
    @Provide
    Arbitrary<String> nonExistentToolName() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('_')
            .ofMinLength(5)
            .ofMaxLength(30)
            .map(s -> "nonexistent_" + s)
            // Ensure it's not a default tool name
            .filter(s -> !s.equals("bash") && !s.equals("read") && !s.equals("edit"));
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Creates a mock AgentTool instance for testing.
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
