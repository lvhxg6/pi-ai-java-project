package com.pi.chat.service;

import com.pi.agent.Agent;
import com.pi.agent.event.AgentEvent;
import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.AgentOptions;
import com.pi.agent.types.MessageAdapter;
import com.pi.ai.core.event.AssistantMessageEvent;
import com.pi.ai.core.types.AssistantMessage;
import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.TextContent;
import com.pi.ai.core.types.UserMessage;
import com.pi.chat.dto.ChatEvent;
import com.pi.chat.dto.ContextUsage;
import com.pi.chat.model.ModelEntry;
import com.pi.coding.model.CodingModelRegistry;
import com.pi.coding.session.SessionContext;
import com.pi.coding.session.SessionManager;

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
 * <p>Manages Agent instances per session and handles message streaming.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>4.1 - Send user message to LLM</li>
 *   <li>4.2 - Persist messages to session</li>
 *   <li>4.3 - Stream assistant response</li>
 *   <li>4.4 - Display response with Markdown</li>
 *   <li>4.6 - Handle LLM errors</li>
 * </ul>
 */
public class ChatService {
    
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    
    private final SessionService sessionService;
    private final ModelService modelService;
    private final CodingModelRegistry modelRegistry;
    private final BrandService brandService;
    
    private final Map<String, Agent> activeAgents = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();
    private final Map<String, String> currentMessageIds = new ConcurrentHashMap<>();
    
    /**
     * Creates a new ChatService.
     */
    public ChatService(SessionService sessionService, ModelService modelService, 
                       CodingModelRegistry modelRegistry, BrandService brandService) {
        this.sessionService = Objects.requireNonNull(sessionService);
        this.modelService = Objects.requireNonNull(modelService);
        this.modelRegistry = Objects.requireNonNull(modelRegistry);
        this.brandService = Objects.requireNonNull(brandService);
    }
    
    /**
     * Sends a message to a session and streams the response.
     * 
     * @param sessionId The session ID
     * @param content   The message content
     * @param emitter   The SSE emitter for streaming
     */
    public void sendMessage(String sessionId, String content, SseEmitter emitter) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(emitter, "emitter must not be null");
        
        // Get or create agent for session
        Agent agent = getOrCreateAgent(sessionId);
        
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
        
        // Create user message
        UserMessage userMessage = new UserMessage(content, System.currentTimeMillis());
        AgentMessage agentMessage = MessageAdapter.wrap(userMessage);
        
        // Append to session
        SessionManager manager = sessionService.getSession(sessionId);
        manager.appendMessage(agentMessage);
        
        // Send message to agent
        agent.prompt(agentMessage)
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
     * @param sessionId The session ID
     */
    public void abortChat(String sessionId) {
        Agent agent = activeAgents.get(sessionId);
        if (agent != null) {
            agent.abort();
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
        
        // Update agent if exists
        Agent agent = activeAgents.get(sessionId);
        if (agent != null) {
            agent.setModel(model);
        }
        
        // Record model change in session
        SessionManager manager = sessionService.getSession(sessionId);
        manager.appendModelChange(provider, modelId);
        
        log.info("Switched model for session {} to {}/{}", sessionId, provider, modelId);
    }
    
    /**
     * Gets context usage for a session.
     * 
     * @param sessionId The session ID
     * @return Context usage information
     */
    public ContextUsage getContextUsage(String sessionId) {
        SessionManager manager = sessionService.getSession(sessionId);
        
        // Get current agent's model for context window
        Agent agent = activeAgents.get(sessionId);
        int contextWindow = 128000; // Default
        if (agent != null && agent.getState().getModel() != null) {
            contextWindow = agent.getState().getModel().contextWindow();
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
        
        // Estimate current tokens (simplified - would need proper tokenization)
        int currentTokens = estimateTokens(manager);
        
        return ContextUsage.of(currentTokens, contextWindow);
    }
    
    /**
     * Closes the agent for a session.
     * 
     * @param sessionId The session ID
     */
    public void closeAgent(String sessionId) {
        Agent agent = activeAgents.remove(sessionId);
        if (agent != null) {
            agent.abort();
            log.debug("Closed agent for session: {}", sessionId);
        }
    }
    
    // ========== Private Methods ==========
    
    private Agent getOrCreateAgent(String sessionId) {
        return activeAgents.computeIfAbsent(sessionId, id -> {
            log.debug("Creating new agent for session: {}", id);
            
            // Create agent with options
            AgentOptions options = AgentOptions.builder()
                .getApiKey((provider) -> modelRegistry.getApiKeyForProvider(provider))
                .build();
            
            Agent agent = new Agent(options);
            
            // Subscribe to events
            agent.subscribe(event -> handleAgentEvent(id, event));
            
            // Load session context
            SessionManager manager = sessionService.getSession(id);
            SessionContext context = manager.buildSessionContext();
            
            // Set model if available
            if (context.model() != null) {
                Model model = modelRegistry.find(
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
            
            return agent;
        });
    }
    
    private void handleAgentEvent(String sessionId, AgentEvent event) {
        SseEmitter emitter = activeEmitters.get(sessionId);
        if (emitter == null) {
            return;
        }
        
        String messageId = currentMessageIds.getOrDefault(sessionId, UUID.randomUUID().toString());
        
        // Convert AgentEvent to ChatEvent
        ChatEvent chatEvent = convertEvent(event, messageId);
        if (chatEvent != null) {
            sendEvent(emitter, chatEvent);
        }
        
        // Persist assistant messages when they end
        if (event instanceof AgentEvent.MessageEnd messageEnd) {
            SessionManager manager = sessionService.getSession(sessionId);
            manager.appendMessage(messageEnd.message());
        }
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
            if (msg instanceof MessageAdapter adapter) {
                if (adapter.message() instanceof AssistantMessage assistantMsg) {
                    // Check for error in the message
                    if (assistantMsg.getErrorMessage() != null && !assistantMsg.getErrorMessage().isEmpty()) {
                        log.error("Agent returned error: {}", assistantMsg.getErrorMessage());
                        return new ChatEvent.Error("API_ERROR", assistantMsg.getErrorMessage());
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
        } else if (event instanceof AgentEvent.ToolExecutionStart toolStart) {
            return new ChatEvent.ToolCallStart(messageId, toolStart.toolCallId(), toolStart.toolName());
        } else if (event instanceof AgentEvent.ToolExecutionEnd toolEnd) {
            String result = toolEnd.result() != null ? toolEnd.result().toString() : null;
            return new ChatEvent.ToolCallEnd(messageId, toolEnd.toolCallId(), result);
        }
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
        int totalChars = 0;
        for (AgentMessage msg : context.messages()) {
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
