package com.pi.chat.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * SSE event types for chat streaming.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>4.3 - Stream assistant response in real-time</li>
 *   <li>4.5 - Display streaming text as it arrives</li>
 *   <li>9.3 - SSE streaming endpoint</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ChatEvent.TextStart.class, name = "text_start"),
    @JsonSubTypes.Type(value = ChatEvent.TextDelta.class, name = "text_delta"),
    @JsonSubTypes.Type(value = ChatEvent.TextEnd.class, name = "text_end"),
    @JsonSubTypes.Type(value = ChatEvent.ThinkingStart.class, name = "thinking_start"),
    @JsonSubTypes.Type(value = ChatEvent.ThinkingDelta.class, name = "thinking_delta"),
    @JsonSubTypes.Type(value = ChatEvent.ThinkingEnd.class, name = "thinking_end"),
    @JsonSubTypes.Type(value = ChatEvent.ToolCallStart.class, name = "tool_call_start"),
    @JsonSubTypes.Type(value = ChatEvent.ToolCallEnd.class, name = "tool_call_end"),
    @JsonSubTypes.Type(value = ChatEvent.MessageDone.class, name = "message_done"),
    @JsonSubTypes.Type(value = ChatEvent.Error.class, name = "error"),
    @JsonSubTypes.Type(value = ChatEvent.CompactionNotice.class, name = "compaction_notice")
})
public sealed interface ChatEvent {
    
    /**
     * Text content streaming started.
     */
    record TextStart(String messageId) implements ChatEvent {}
    
    /**
     * Text content delta (incremental text).
     */
    record TextDelta(String messageId, String delta) implements ChatEvent {}
    
    /**
     * Text content streaming ended.
     */
    record TextEnd(String messageId, String fullContent) implements ChatEvent {}
    
    /**
     * Thinking/reasoning started.
     */
    record ThinkingStart(String messageId) implements ChatEvent {}
    
    /**
     * Thinking delta (incremental thinking text).
     */
    record ThinkingDelta(String messageId, String delta) implements ChatEvent {}
    
    /**
     * Thinking ended.
     */
    record ThinkingEnd(String messageId) implements ChatEvent {}
    
    /**
     * Tool call started.
     */
    record ToolCallStart(String messageId, String toolCallId, String toolName) implements ChatEvent {}
    
    /**
     * Tool call ended with result.
     * 
     * @param messageId  The message ID
     * @param toolCallId The tool call ID
     * @param result     The tool execution result (or error message if isError is true)
     * @param isError    Whether the tool execution resulted in an error
     */
    record ToolCallEnd(String messageId, String toolCallId, String result, boolean isError) implements ChatEvent {
        /**
         * Creates a ToolCallEnd event for a successful tool execution.
         */
        public ToolCallEnd(String messageId, String toolCallId, String result) {
            this(messageId, toolCallId, result, false);
        }
    }
    
    /**
     * Message generation completed.
     */
    record MessageDone(String messageId, MessageDTO.UsageDTO usage) implements ChatEvent {}
    
    /**
     * Error occurred during processing.
     */
    record Error(String code, String message) implements ChatEvent {}
    
    /**
     * Context compaction was triggered.
     */
    record CompactionNotice(int tokensBefore, int tokensAfter) implements ChatEvent {}
}
