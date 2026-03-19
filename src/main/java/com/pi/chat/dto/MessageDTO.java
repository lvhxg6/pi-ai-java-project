package com.pi.chat.dto;

import com.pi.agent.types.AgentMessage;
import com.pi.agent.types.MessageAdapter;
import com.pi.ai.core.types.AssistantContentBlock;
import com.pi.ai.core.types.AssistantMessage;
import com.pi.ai.core.types.Message;
import com.pi.ai.core.types.TextContent;
import com.pi.ai.core.types.ToolCall;
import com.pi.ai.core.types.UserMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Message DTO for API responses.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>4.2 - Display message history</li>
 *   <li>4.4 - Display assistant responses with Markdown formatting</li>
 * </ul>
 * 
 * @param id        Message unique identifier
 * @param role      Message role (user, assistant, system)
 * @param content   Text content or Markdown
 * @param toolCalls Tool calls (for assistant messages)
 * @param timestamp Message timestamp
 * @param modelId   Model that generated this message (for assistant messages)
 * @param usage     Token usage (for assistant messages)
 */
public record MessageDTO(
    String id,
    String role,
    String content,
    List<ToolCallDTO> toolCalls,
    Instant timestamp,
    String modelId,
    UsageDTO usage
) {
    
    /**
     * Creates a MessageDTO from an AgentMessage.
     * 
     * @param agentMessage The source message
     * @param id           Message ID
     * @param timestamp    Message timestamp
     * @return MessageDTO instance
     */
    public static MessageDTO from(AgentMessage agentMessage, String id, Instant timestamp) {
        if (agentMessage instanceof MessageAdapter adapter) {
            return fromMessage(adapter.message(), id, timestamp);
        }
        
        // For non-standard messages, just return basic info
        return new MessageDTO(
            id,
            agentMessage.role(),
            "",
            null,
            timestamp,
            null,
            null
        );
    }
    
    private static MessageDTO fromMessage(Message message, String id, Instant timestamp) {
        String content = "";
        List<ToolCallDTO> toolCalls = null;
        String modelId = null;
        UsageDTO usage = null;
        
        if (message instanceof UserMessage userMsg) {
            content = extractUserContent(userMsg);
        } else if (message instanceof AssistantMessage assistantMsg) {
            content = extractAssistantContent(assistantMsg);
            toolCalls = extractToolCalls(assistantMsg);
            modelId = assistantMsg.getModel();
            if (assistantMsg.getUsage() != null) {
                usage = new UsageDTO(
                    assistantMsg.getUsage().input(),
                    assistantMsg.getUsage().output(),
                    assistantMsg.getUsage().totalTokens()
                );
            }
        }
        
        return new MessageDTO(
            id,
            message.role(),
            content,
            toolCalls != null && !toolCalls.isEmpty() ? toolCalls : null,
            timestamp,
            modelId,
            usage
        );
    }
    
    @SuppressWarnings("unchecked")
    private static String extractUserContent(UserMessage message) {
        Object content = message.content();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> blocks) {
            StringBuilder sb = new StringBuilder();
            for (Object block : blocks) {
                if (block instanceof Map<?, ?> map) {
                    Object type = map.get("type");
                    if ("text".equals(type)) {
                        Object text = map.get("text");
                        if (text != null) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(text);
                        }
                    }
                }
            }
            return sb.toString();
        }
        return "";
    }
    
    private static String extractAssistantContent(AssistantMessage message) {
        List<AssistantContentBlock> content = message.getContent();
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (AssistantContentBlock block : content) {
            if (block instanceof TextContent text) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(text.text());
            }
        }
        return sb.toString();
    }
    
    private static List<ToolCallDTO> extractToolCalls(AssistantMessage message) {
        List<AssistantContentBlock> content = message.getContent();
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        
        List<ToolCallDTO> toolCalls = new ArrayList<>();
        for (AssistantContentBlock block : content) {
            if (block instanceof ToolCall toolCall) {
                toolCalls.add(new ToolCallDTO(
                    toolCall.id(),
                    toolCall.name(),
                    toolCall.arguments(),
                    null
                ));
            }
        }
        return toolCalls;
    }
    
    /**
     * Tool call DTO.
     * 
     * @param id        Tool call ID
     * @param name      Tool name
     * @param arguments Tool arguments
     * @param result    Tool result (if available)
     */
    public record ToolCallDTO(
        String id,
        String name,
        Map<String, Object> arguments,
        String result
    ) {}
    
    /**
     * Token usage DTO.
     * 
     * @param inputTokens  Input token count
     * @param outputTokens Output token count
     * @param totalTokens  Total token count
     */
    public record UsageDTO(
        int inputTokens,
        int outputTokens,
        int totalTokens
    ) {}
}
