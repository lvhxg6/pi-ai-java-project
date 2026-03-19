package com.pi.chat.controller;

import com.pi.chat.dto.CreateSessionRequest;
import com.pi.chat.dto.MessageDTO;
import com.pi.chat.dto.SessionInfo;
import com.pi.chat.dto.UpdateSessionRequest;
import com.pi.chat.service.SessionService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing chat sessions.
 * 
 * <p>Provides endpoints for:
 * <ul>
 *   <li>CRUD operations on sessions</li>
 *   <li>Message history retrieval</li>
 * </ul>
 * 
 * <p>Requirements:
 * <ul>
 *   <li>9.2 - Session management endpoints</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    
    private final SessionService sessionService;
    
    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }
    
    /**
     * Lists all sessions.
     * 
     * @return List of all sessions
     */
    @GetMapping
    public ResponseEntity<List<SessionInfo>> listSessions() {
        List<SessionInfo> sessions = sessionService.listSessions();
        return ResponseEntity.ok(sessions);
    }
    
    /**
     * Creates a new session.
     * 
     * @param request The create request (optional)
     * @return The created session info with 201 status
     */
    @PostMapping
    public ResponseEntity<SessionInfo> createSession(
            @Valid @RequestBody(required = false) CreateSessionRequest request) {
        SessionInfo created = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    /**
     * Gets a session by ID.
     * 
     * @param sessionId The session ID
     * @return The session info
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionInfo> getSession(@PathVariable String sessionId) {
        SessionInfo session = sessionService.getSessionInfo(sessionId);
        return ResponseEntity.ok(session);
    }
    
    /**
     * Updates a session (rename).
     * 
     * @param sessionId The session ID
     * @param request   The update request
     * @return The updated session info
     */
    @PutMapping("/{sessionId}")
    public ResponseEntity<SessionInfo> updateSession(
            @PathVariable String sessionId,
            @Valid @RequestBody UpdateSessionRequest request) {
        SessionInfo updated = sessionService.renameSession(sessionId, request.name());
        return ResponseEntity.ok(updated);
    }
    
    /**
     * Deletes a session.
     * 
     * @param sessionId The session ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Gets messages for a session.
     * 
     * @param sessionId The session ID
     * @return List of messages
     */
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<MessageDTO>> getMessages(@PathVariable String sessionId) {
        List<MessageDTO> messages = sessionService.getMessages(sessionId);
        return ResponseEntity.ok(messages);
    }
}
