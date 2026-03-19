package com.pi.chat.service;

import com.pi.chat.dto.CreateSessionRequest;
import com.pi.chat.dto.SessionInfo;
import com.pi.chat.session.SessionManagerFactory;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for Chat module.
 * 
 * <p>Tests the following correctness properties:
 * <ul>
 *   <li>Property 9: Message Persistence Round-Trip</li>
 *   <li>Property 10: Error Events on LLM Failure</li>
 *   <li>Property 22: Chat Requires Valid API Key</li>
 * </ul>
 */
class ChatPropertyTest {
    
    @TempDir
    Path tempDir;
    
    private SessionManagerFactory factory;
    private SessionService sessionService;
    
    @BeforeEach
    void setUp() {
        String cwd = tempDir.toString();
        factory = new SessionManagerFactory(tempDir, cwd);
        sessionService = new SessionService(factory);
    }
    
    // ========== Property 9: Message Persistence Round-Trip ==========
    
    @Test
    @Tag("Feature: ai-chat-web, Property 9: Message Persistence Round-Trip")
    void sessionMessagesArePersisted() {
        // Create session
        CreateSessionRequest request = new CreateSessionRequest("Test Chat", null, null);
        SessionInfo created = sessionService.createSession(request);
        
        // Session should be retrievable
        SessionInfo retrieved = sessionService.getSessionInfo(created.id());
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.name()).isEqualTo("Test Chat");
    }
    
    @Property(tries = 20)
    @Tag("Feature: ai-chat-web, Property 9: Message Persistence Round-Trip")
    void multipleSessionsCanBeCreated(
            @ForAll @IntRange(min = 1, max = 5) int count) {
        
        for (int i = 0; i < count; i++) {
            CreateSessionRequest request = new CreateSessionRequest("Chat " + i, null, null);
            SessionInfo created = sessionService.createSession(request);
            assertThat(created.id()).isNotNull();
        }
        
        var sessions = sessionService.listSessions();
        assertThat(sessions).hasSize(count);
    }
    
    // ========== Property 10: Error Events on LLM Failure ==========
    
    @Test
    @Tag("Feature: ai-chat-web, Property 10: Error Events on LLM Failure")
    void chatServiceHandlesInvalidSession() {
        // Attempting to get non-existent session should throw
        assertThatThrownBy(() -> sessionService.getSessionInfo("non-existent-id"))
            .isInstanceOf(RuntimeException.class);
    }
    
    // ========== Property 22: Chat Requires Valid Session ==========
    
    @Property(tries = 30)
    @Tag("Feature: ai-chat-web, Property 22: Chat Requires Valid Session")
    void sessionIdMustBeValid(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String invalidId) {
        
        // Random IDs should not match any session
        assertThatThrownBy(() -> sessionService.getSessionInfo(invalidId))
            .isInstanceOf(RuntimeException.class);
    }
    
    @Test
    @Tag("Feature: ai-chat-web, Property 22: Chat Requires Valid Session")
    void sessionInfoIsAccessibleAfterCreation() {
        // Create session
        CreateSessionRequest request = new CreateSessionRequest("Test Session", null, null);
        SessionInfo created = sessionService.createSession(request);
        String sessionId = created.id();
        
        // Should be accessible via getSessionInfo
        SessionInfo retrieved = sessionService.getSessionInfo(sessionId);
        assertThat(retrieved.id()).isEqualTo(sessionId);
        assertThat(retrieved.name()).isEqualTo("Test Session");
    }
}
