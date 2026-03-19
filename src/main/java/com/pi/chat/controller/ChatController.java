package com.pi.chat.controller;

import com.pi.chat.dto.ContextUsage;
import com.pi.chat.dto.SendMessageRequest;
import com.pi.chat.dto.SwitchModelRequest;
import com.pi.chat.service.ChatService;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST Controller for chat operations.
 * 
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Sending messages with SSE streaming response</li>
 *   <li>Aborting chat operations</li>
 *   <li>Switching models</li>
 *   <li>Getting context usage</li>
 * </ul>
 * 
 * <p>Requirements:
 * <ul>
 *   <li>9.3 - SSE streaming endpoint</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5 minutes
    
    private final ChatService chatService;
    
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }
    
    /**
     * Sends a message to a session and streams the response.
     * 
     * @param sessionId The session ID
     * @param request   The message request
     * @return SSE emitter for streaming response
     */
    @PostMapping(value = "/{sessionId}/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        chatService.sendMessage(sessionId, request.content(), emitter);
        return emitter;
    }
    
    /**
     * Aborts the current chat operation for a session.
     * 
     * @param sessionId The session ID
     * @return 204 No Content on success
     */
    @PostMapping("/{sessionId}/abort")
    public ResponseEntity<Void> abortChat(@PathVariable String sessionId) {
        chatService.abortChat(sessionId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Switches the model for a session.
     * 
     * @param sessionId The session ID
     * @param request   The switch model request
     * @return 204 No Content on success
     */
    @PostMapping("/{sessionId}/model")
    public ResponseEntity<Void> switchModel(
            @PathVariable String sessionId,
            @Valid @RequestBody SwitchModelRequest request) {
        chatService.switchModel(sessionId, request.provider(), request.modelId());
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Gets context usage for a session.
     * 
     * @param sessionId The session ID
     * @return Context usage information
     */
    @GetMapping("/{sessionId}/context-usage")
    public ResponseEntity<ContextUsage> getContextUsage(@PathVariable String sessionId) {
        ContextUsage usage = chatService.getContextUsage(sessionId);
        return ResponseEntity.ok(usage);
    }
}
