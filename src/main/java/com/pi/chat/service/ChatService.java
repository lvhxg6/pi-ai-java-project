package com.pi.chat.service;

import com.pi.agent.Agent;
import com.pi.agent.event.AgentEvent;
import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.AgentOptions;
import com.pi.agent.types.AgentTool;
import com.pi.agent.types.AgentToolResult;
import com.pi.agent.types.MessageAdapter;
import com.pi.ai.core.event.AssistantMessageEvent;
import com.pi.ai.core.types.AssistantMessage;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.StopReason;
import com.pi.ai.core.types.TextContent;
import com.pi.ai.core.types.UserMessage;
import com.pi.chat.dto.ChatEvent;
import com.pi.chat.dto.ContextUsage;
import com.pi.chat.model.ModelEntry;
import com.pi.coding.model.CodingModelRegistry;
import com.pi.coding.resource.ResourceLoader;
import com.pi.coding.session.AgentSession;
import com.pi.coding.session.AgentSessionConfig;
import com.pi.coding.session.AgentSessionEvent;
import com.pi.coding.session.SessionContext;
import com.pi.coding.session.SessionManager;
import com.pi.coding.settings.SettingsManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling chat operations.
 * 
 * <p>Manages AgentSession instances per session and handles message streaming.
 * Uses AgentSession for automatic message persistence, auto-retry, auto-compaction,
 * and Skills hot-reload support.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>4.1 - Send user message to LLM</li>
 *   <li>4.2 - Persist messages to session (via AgentSession)</li>
 *   <li>4.3 - Stream assistant response</li>
 *   <li>4.4 - Display response with Markdown</li>
 *   <li>4.6 - Handle LLM errors</li>
 *   <li>1.1 - AgentSession lifecycle management</li>
 *   <li>2.1 - AgentEventWrapper event handling</li>
 * </ul>
 */
public class ChatService {
    
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    
    private final SessionService sessionService;
    private final ModelService modelService;
    private final CodingModelRegistry modelRegistry;
    private final BrandService brandService;
    private final SettingsManager settingsManager;
    private final ResourceLoader resourceLoader;  // Optional, can be null
    private final ToolRegistry toolRegistry;
    
    private final Map<String, AgentSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();
    private final Map<String, String> currentMessageIds = new ConcurrentHashMap<>();
    
    /**
     * Creates a new ChatService with AgentSession support.
     * 
     * @param sessionService  Session management service
     * @param modelService    Model information service
     * @param modelRegistry   Model registry for API key resolution
     * @param brandService    Brand configuration service
     * @param settingsManager Settings manager for AgentSession configuration
     * @param resourceLoader  Resource loader for Skills (optional, can be null)
     * @param toolRegistry    Tool registry for managing available tools
     */
    public ChatService(SessionService sessionService, ModelService modelService, 
                       CodingModelRegistry modelRegistry, BrandService brandService,
                       SettingsManager settingsManager, ResourceLoader resourceLoader,
                       ToolRegistry toolRegistry) {
        this.sessionService = Objects.requireNonNull(sessionService);
        this.modelService = Objects.requireNonNull(modelService);
        this.modelRegistry = Objects.requireNonNull(modelRegistry);
        this.brandService = Objects.requireNonNull(brandService);
        this.settingsManager = Objects.requireNonNull(settingsManager);
        this.resourceLoader = resourceLoader;  // Optional
        this.toolRegistry = Objects.requireNonNull(toolRegistry);
    }
    
    /**
     * Sends a message to a session and streams the response.
     * 
     * <p>Uses AgentSession.prompt() which automatically handles:
     * <ul>
     *   <li>Message persistence</li>
     *   <li>System prompt rebuilding</li>
     *   <li>Auto-retry on transient errors</li>
     * </ul>
     * 
     * @param sessionId The session ID
     * @param content   The message content
     * @param emitter   The SSE emitter for streaming
     */
    public void sendMessage(String sessionId, String content, SseEmitter emitter) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(emitter, "emitter must not be null");
        
        // Get or create AgentSession for session
        AgentSession agentSession = getOrCreateAgentSession(sessionId);
        
        // Store emitter for this session
        activeEmitters.put(sessionId, emitter);
        
        // Generate message ID for this conversation turn
        String messageId = UUID.randomUUID().toString();
        currentMessageIds.put(sessionId, messageId);
        
        // Set up emitter completion handlers
        emitter.onCompletion(() -> {
            log.debug("SSE emitter completed for session: {}", sessionId);
            activeEmitters.remove(sessionId);
            currentMessageIds.remove(sessionId);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out for session: {}", sessionId);
            activeEmitters.remove(sessionId);
            currentMessageIds.remove(sessionId);
        });
        emitter.onError(e -> {
            log.error("SSE emitter error for session: {}", sessionId, e);
            activeEmitters.remove(sessionId);
            currentMessageIds.remove(sessionId);
        });
        
        // Note: No need to manually append user message - AgentSession handles persistence
        
        // Send message to AgentSession
        agentSession.prompt(content, null)
            .thenRun(() -> {
                // Send done event
                sendEvent(emitter, new ChatEvent.MessageDone(messageId, null));
                completeEmitter(emitter);
            })
            .exceptionally(e -> {
                log.error("Error processing message for session: {}", sessionId, e);
                sendEvent(emitter, new ChatEvent.Error("PROCESSING_ERROR", e.getMessage()));
                completeEmitter(emitter);
                return null;
            });
    }
    
    /**
     * Aborts the current chat operation for a session.
     * 
     * <p>Calls AgentSession.abort() which handles:
     * <ul>
     *   <li>Aborting the underlying Agent</li>
     *   <li>Cancelling any pending compaction</li>
     *   <li>Cancelling any pending retry</li>
     * </ul>
     * 
     * @param sessionId The session ID
     */
    public void abortChat(String sessionId) {
        AgentSession agentSession = activeSessions.get(sessionId);
        if (agentSession != null) {
            agentSession.abort();
            log.info("Aborted chat for session: {}", sessionId);
        }
        
        SseEmitter emitter = activeEmitters.remove(sessionId);
        if (emitter != null) {
            sendEvent(emitter, new ChatEvent.Error("ABORTED", "Chat was aborted by user"));
            completeEmitter(emitter);
        }
    }
    
    /**
     * Switches the model for a session.
     * 
     * <p>Uses AgentSession.setModel() which automatically persists the model change.
     * 
     * @param sessionId The session ID
     * @param provider  The provider ID
     * @param modelId   The model ID
     */
    public void switchModel(String sessionId, String provider, String modelId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(modelId, "modelId must not be null");
        
        // Find the model from registry first, then fall back to brand configs
        Model model = modelRegistry.find(provider, modelId);
        if (model == null) {
            model = findModelFromBrands(provider, modelId);
        }
        if (model == null) {
            throw new IllegalArgumentException("Model not found: " + provider + "/" + modelId);
        }
        
        // Update AgentSession if exists - it will automatically persist the change
        AgentSession agentSession = activeSessions.get(sessionId);
        if (agentSession != null) {
            agentSession.setModel(model);
        } else {
            // If no active session, just record the change in SessionManager
            SessionManager manager = sessionService.getSession(sessionId);
            manager.appendModelChange(provider, modelId);
        }
        
        log.info("Switched model for session {} to {}/{}", sessionId, provider, modelId);
    }
    
    /**
     * Gets context usage for a session.
     * 
     * <p>Uses AgentSession.getState().getMessages() for token estimation
     * and AgentSession.getModel().contextWindow() for context window size.
     * 
     * @param sessionId The session ID
     * @return Context usage information
     */
    public ContextUsage getContextUsage(String sessionId) {
        SessionManager manager = sessionService.getSession(sessionId);
        
        // Get current AgentSession's model for context window
        AgentSession agentSession = activeSessions.get(sessionId);
        int contextWindow = 128000; // Default
        if (agentSession != null && agentSession.getModel() != null) {
            contextWindow = agentSession.getModel().contextWindow();
        } else {
            // Fallback: look up from session's last model change via brand configs
            SessionContext ctx = manager.buildSessionContext();
            if (ctx.model() != null) {
                Model brandModel = findModelFromBrands(ctx.model().provider(), ctx.model().modelId());
                if (brandModel != null) {
                    contextWindow = brandModel.contextWindow();
                }
            }
        }
        
        // Estimate current tokens from AgentSession state or SessionManager
        int currentTokens;
        if (agentSession != null) {
            currentTokens = estimateTokensFromMessages(agentSession.getMessages());
        } else {
            currentTokens = estimateTokens(manager);
        }
        
        return ContextUsage.of(currentTokens, contextWindow);
    }
    
    /**
     * Closes the AgentSession for a session.
     * 
     * <p>Calls AgentSession.dispose() which releases all resources:
     * <ul>
     *   <li>Unsubscribes from agent events</li>
     *   <li>Removes resource change listeners</li>
     *   <li>Aborts any pending operations</li>
     *   <li>Disposes extension runner</li>
     * </ul>
     * 
     * @param sessionId The session ID
     */
    public void closeAgent(String sessionId) {
        AgentSession agentSession = activeSessions.remove(sessionId);
        if (agentSession != null) {
            agentSession.dispose();
            log.debug("Closed AgentSession for session: {}", sessionId);
        }
    }
    
    // ========== Private Methods ==========
    
    /**
     * Gets or creates an AgentSession for the given session ID.
     * 
     * <p>AgentSession provides:
     * <ul>
     *   <li>Automatic message persistence</li>
     *   <li>Auto-retry on transient errors</li>
     *   <li>Auto-compaction when context is full</li>
     *   <li>Skills hot-reload support</li>
     * </ul>
     * 
     * @param sessionId The session ID
     * @return The AgentSession instance
     */
    private AgentSession getOrCreateAgentSession(String sessionId) {
        return activeSessions.computeIfAbsent(sessionId, id -> {
            log.debug("Creating new AgentSession for session: {}", id);
            
            // Create underlying Agent with options
            AgentOptions options = AgentOptions.builder()
                .getApiKey((provider) -> modelRegistry.getApiKeyForProvider(provider))
                .build();
            
            Agent agent = new Agent(options);
            
            // Get SessionManager for this session
            SessionManager sessionManager = sessionService.getSession(id);
            
            // Load session context
            SessionContext context = sessionManager.buildSessionContext();
            
            // Set model if available
            Model model = null;
            if (context.model() != null) {
                model = modelRegistry.find(
                    context.model().provider(),
                    context.model().modelId()
                );
                if (model == null) {
                    model = findModelFromBrands(
                        context.model().provider(),
                        context.model().modelId()
                    );
                }
                if (model != null) {
                    agent.setModel(model);
                }
            }
            
            // Load existing messages
            agent.replaceMessages(context.messages());
            
            // 获取工具列表并设置到 Agent
            List<AgentTool> tools = toolRegistry.getAllTools();
            agent.setTools(tools);
            
            // 配置 activeToolNames
            List<String> activeToolNames = toolRegistry.getToolNames();
            
            // Create AgentSessionConfig
            String cwd = System.getProperty("user.dir");
            AgentSessionConfig config = new AgentSessionConfig(
                agent,
                sessionManager,
                settingsManager,
                cwd,
                List.of(),           // scopedModels (not used in web chat)
                resourceLoader,      // can be null
                List.of(),           // customTools (not used in web chat)
                modelRegistry,
                activeToolNames      // 关键：传入工具名称列表
            );
            
            // Create AgentSession
            AgentSession agentSession = new AgentSession(config);
            
            // Subscribe to AgentSession events
            agentSession.subscribe(event -> handleAgentSessionEvent(id, event));
            
            log.info("Created AgentSession for session: {}", id);
            return agentSession;
        });
    }
    
    /**
     * Handles AgentSession events and converts them to ChatEvents.
     * 
     * <p>AgentSession emits two types of events:
     * <ul>
     *   <li>AgentEventWrapper - wraps underlying AgentEvent, should be converted to ChatEvent</li>
     *   <li>ResourceChangeSessionEvent - Skills hot-reload notification, should be ignored for SSE</li>
     * </ul>
     * 
     * <p>Tool execution events (ToolExecutionStart, ToolExecutionEnd) are handled directly
     * with dedicated methods for proper result formatting.
     * 
     * @param sessionId The session ID
     * @param event     The AgentSession event
     */
    private void handleAgentSessionEvent(String sessionId, AgentSessionEvent event) {
        // Ignore ResourceChangeSessionEvent - these are internal notifications
        if (event instanceof AgentSession.ResourceChangeSessionEvent) {
            log.debug("Ignoring ResourceChangeSessionEvent for session: {}", sessionId);
            return;
        }
        
        // Handle AgentEventWrapper - extract and process the inner AgentEvent
        if (event instanceof AgentSession.AgentEventWrapper wrapper) {
            AgentEvent agentEvent = wrapper.event();
            
            // Handle tool execution events directly with dedicated methods
            if (agentEvent instanceof AgentEvent.ToolExecutionStart toolStart) {
                handleToolExecutionStart(sessionId, toolStart);
            } else if (agentEvent instanceof AgentEvent.ToolExecutionEnd toolEnd) {
                handleToolExecutionEnd(sessionId, toolEnd);
            } else {
                // Handle other events (existing logic)
                handleAgentEvent(sessionId, agentEvent);
            }
        }
    }
    
    /**
     * Handles ToolExecutionStart event by creating and sending a ToolCallStart ChatEvent.
     * 
     * <p>Extracts toolCallId and toolName from the event and sends them to the frontend
     * via SSE to indicate that a tool execution has started.
     * 
     * @param sessionId The session ID
     * @param event     The ToolExecutionStart event
     */
    private void handleToolExecutionStart(String sessionId, AgentEvent.ToolExecutionStart event) {
        SseEmitter emitter = activeEmitters.get(sessionId);
        if (emitter == null) {
            log.warn("No emitter found for session: {} when handling ToolExecutionStart", sessionId);
            return;
        }
        
        String messageId = currentMessageIds.getOrDefault(sessionId, UUID.randomUUID().toString());
        ChatEvent chatEvent = new ChatEvent.ToolCallStart(
            messageId,
            event.toolCallId(),
            event.toolName()
        );
        
        log.debug("Sending ToolCallStart event for session {}: toolCallId={}, toolName={}", 
            sessionId, event.toolCallId(), event.toolName());
        sendEvent(emitter, chatEvent);
    }
    
    /**
     * Handles ToolExecutionEnd event by creating and sending a ToolCallEnd ChatEvent.
     * 
     * <p>Formats the tool execution result using {@link #formatToolResult(AgentToolResult)}
     * and sends it to the frontend via SSE. If the tool execution resulted in an error
     * (isError=true), the error is properly formatted and the isError flag is set in the event.
     * 
     * <p>Requirements: 10.1, 10.2, 10.3, 10.4 - Error handling for tool execution
     * 
     * @param sessionId The session ID
     * @param event     The ToolExecutionEnd event
     */
    private void handleToolExecutionEnd(String sessionId, AgentEvent.ToolExecutionEnd toolEnd) {
        SseEmitter emitter = activeEmitters.get(sessionId);
        if (emitter == null) {
            log.warn("No emitter found for session: {} when handling ToolExecutionEnd", sessionId);
            return;
        }
        
        String messageId = currentMessageIds.getOrDefault(sessionId, UUID.randomUUID().toString());
        boolean isError = toolEnd.isError();
        String result = formatToolResult(toolEnd.result());
        
        // If this is an error, ensure the result has an error prefix for clarity
        if (isError && result != null && !result.startsWith("Error:")) {
            result = "Error: " + result;
        }
        
        ChatEvent chatEvent = new ChatEvent.ToolCallEnd(
            messageId,
            toolEnd.toolCallId(),
            result,
            isError
        );
        
        if (isError) {
            log.warn("Tool execution error for session {}: toolCallId={}, toolName={}, error={}", 
                sessionId, toolEnd.toolCallId(), toolEnd.toolName(), result);
        } else {
            log.debug("Sending ToolCallEnd event for session {}: toolCallId={}, result length={}", 
                sessionId, toolEnd.toolCallId(), result != null ? result.length() : 0);
        }
        sendEvent(emitter, chatEvent);
    }
    
    /**
     * Formats an AgentToolResult into a string representation.
     * 
     * <p>Extracts TextContent from the result's content blocks and concatenates them
     * with newlines. Returns "(no output)" if the result is null or has no content.
     * 
     * @param result The AgentToolResult to format
     * @return The formatted result string
     */
    private String formatToolResult(AgentToolResult<?> result) {
        if (result == null || result.content() == null) {
            return "(no output)";
        }
        StringBuilder sb = new StringBuilder();
        for (var block : result.content()) {
            if (block instanceof TextContent text) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(text.text());
            }
        }
        return sb.length() > 0 ? sb.toString() : "(no output)";
    }
    
    private void handleAgentEvent(String sessionId, AgentEvent event) {
        log.debug("Received agent event for session {}: {}", sessionId, event.getClass().getSimpleName());
        
        SseEmitter emitter = activeEmitters.get(sessionId);
        if (emitter == null) {
            log.warn("No emitter found for session: {}", sessionId);
            return;
        }
        
        String messageId = currentMessageIds.getOrDefault(sessionId, UUID.randomUUID().toString());
        
        // Convert AgentEvent to ChatEvent
        ChatEvent chatEvent = convertEvent(event, messageId);
        if (chatEvent != null) {
            log.debug("Sending chat event: {}", chatEvent.getClass().getSimpleName());
            sendEvent(emitter, chatEvent);
        }
        
        // Note: Message persistence is now handled automatically by AgentSession
        // No need to manually call sessionManager.appendMessage()
    }
    
    private ChatEvent convertEvent(AgentEvent event, String messageId) {
        if (event instanceof AgentEvent.MessageStart) {
            return new ChatEvent.TextStart(messageId);
        } else if (event instanceof AgentEvent.MessageUpdate messageUpdate) {
            // Extract text delta from the assistant message event
            AssistantMessageEvent assistantEvent = messageUpdate.assistantMessageEvent();
            if (assistantEvent instanceof AssistantMessageEvent.TextDelta textDelta) {
                return new ChatEvent.TextDelta(messageId, textDelta.delta());
            } else if (assistantEvent instanceof AssistantMessageEvent.ThinkingDelta thinkingDelta) {
                return new ChatEvent.ThinkingDelta(messageId, thinkingDelta.delta());
            } else if (assistantEvent instanceof AssistantMessageEvent.ThinkingStart) {
                return new ChatEvent.ThinkingStart(messageId);
            } else if (assistantEvent instanceof AssistantMessageEvent.ThinkingEnd) {
                return new ChatEvent.ThinkingEnd(messageId);
            }
            return null;
        } else if (event instanceof AgentEvent.MessageEnd messageEnd) {
            // Extract full content from the message
            AgentMessage msg = messageEnd.message();
            log.debug("MessageEnd received, message type: {}", msg.getClass().getSimpleName());
            if (msg instanceof MessageAdapter adapter) {
                Object innerMsg = adapter.message();
                log.debug("Inner message type: {}", innerMsg != null ? innerMsg.getClass().getSimpleName() : "null");
                if (innerMsg instanceof AssistantMessage assistantMsg) {
                    log.debug("AssistantMessage - stopReason: {}, errorMessage: {}, content size: {}", 
                        assistantMsg.getStopReason(), 
                        assistantMsg.getErrorMessage(),
                        assistantMsg.getContent() != null ? assistantMsg.getContent().size() : 0);
                    // Check for error in the message
                    if (assistantMsg.getErrorMessage() != null && !assistantMsg.getErrorMessage().isEmpty()) {
                        log.error("Agent returned error: {}", assistantMsg.getErrorMessage());
                        return new ChatEvent.Error("API_ERROR", assistantMsg.getErrorMessage());
                    }
                    // Check for error stop reason
                    if (assistantMsg.getStopReason() == StopReason.ERROR) {
                        String errorMsg = "API call failed";
                        log.error("Agent returned error stop reason");
                        return new ChatEvent.Error("API_ERROR", errorMsg);
                    }
                    String content = extractTextContent(assistantMsg);
                    return new ChatEvent.TextEnd(messageId, content);
                }
            }
            return null;
        } else if (event instanceof AgentEvent.AgentEnd agentEnd) {
            // Handle agent end event - check for errors in the final messages
            for (AgentMessage msg : agentEnd.messages()) {
                if (msg instanceof MessageAdapter adapter) {
                    if (adapter.message() instanceof AssistantMessage assistantMsg) {
                        if (assistantMsg.getErrorMessage() != null && !assistantMsg.getErrorMessage().isEmpty()) {
                            log.error("Agent ended with error: {}", assistantMsg.getErrorMessage());
                            return new ChatEvent.Error("API_ERROR", assistantMsg.getErrorMessage());
                        }
                    }
                }
            }
            return null;
        }
        // Note: ToolExecutionStart and ToolExecutionEnd are handled by dedicated methods
        // (handleToolExecutionStart and handleToolExecutionEnd) in handleAgentSessionEvent
        return null;
    }
    
    private String extractTextContent(AssistantMessage message) {
        if (message.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var block : message.getContent()) {
            if (block instanceof TextContent text) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(text.text());
            }
        }
        return sb.toString();
    }
    
    private void sendEvent(SseEmitter emitter, ChatEvent event) {
        try {
            emitter.send(SseEmitter.event()
                .name(getEventName(event))
                .data(event));
        } catch (IOException e) {
            log.error("Failed to send SSE event", e);
        }
    }
    
    private String getEventName(ChatEvent event) {
        if (event instanceof ChatEvent.TextStart) {
            return "text_start";
        } else if (event instanceof ChatEvent.TextDelta) {
            return "text_delta";
        } else if (event instanceof ChatEvent.TextEnd) {
            return "text_end";
        } else if (event instanceof ChatEvent.ThinkingStart) {
            return "thinking_start";
        } else if (event instanceof ChatEvent.ThinkingDelta) {
            return "thinking_delta";
        } else if (event instanceof ChatEvent.ThinkingEnd) {
            return "thinking_end";
        } else if (event instanceof ChatEvent.ToolCallStart) {
            return "tool_call_start";
        } else if (event instanceof ChatEvent.ToolCallEnd) {
            return "tool_call_end";
        } else if (event instanceof ChatEvent.MessageDone) {
            return "message_done";
        } else if (event instanceof ChatEvent.Error) {
            return "error";
        } else if (event instanceof ChatEvent.CompactionNotice) {
            return "compaction_notice";
        }
        return "unknown";
    }
    
    private void completeEmitter(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("Error completing emitter", e);
        }
    }
    
    /**
     * Looks up a model from brand configurations when the SDK registry doesn't have it.
     * Constructs a temporary SDK Model from the brand's ModelEntry data.
     */
    private Model findModelFromBrands(String provider, String modelId) {
        try {
            for (var brand : brandService.listBrands()) {
                if (!brand.provider().equals(provider)) {
                    continue;
                }
                for (ModelEntry entry : brand.models()) {
                    if (entry.id().equals(modelId)) {
                        return new Model(
                            entry.id(),
                            entry.name(),
                            brand.apiType(),
                            brand.provider(),
                            brand.baseUrl(),
                            false,
                            List.of("text"),
                            null,
                            entry.contextWindow(),
                            entry.maxTokens(),
                            null,
                            null
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to find model from brands: {}/{}", provider, modelId, e);
        }
        return null;
    }

    private int estimateTokens(SessionManager manager) {
        // Simplified token estimation (roughly 4 chars per token)
        SessionContext context = manager.buildSessionContext();
        return estimateTokensFromMessages(context.messages());
    }
    
    /**
     * Estimates token count from a list of messages.
     * Uses simplified estimation (roughly 4 chars per token).
     */
    private int estimateTokensFromMessages(List<AgentMessage> messages) {
        int totalChars = 0;
        for (AgentMessage msg : messages) {
            if (msg instanceof MessageAdapter adapter) {
                var message = adapter.message();
                if (message instanceof UserMessage userMsg) {
                    Object content = userMsg.content();
                    if (content instanceof String text) {
                        totalChars += text.length();
                    }
                } else if (message instanceof AssistantMessage assistantMsg) {
                    var blocks = assistantMsg.getContent();
                    if (blocks != null) {
                        for (var block : blocks) {
                            if (block instanceof TextContent text) {
                                totalChars += text.text().length();
                            }
                        }
                    }
                }
            }
        }
        return totalChars / 4;
    }
}
