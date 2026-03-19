package com.pi.chat.session;

import com.pi.chat.dto.CreateSessionRequest;
import com.pi.chat.dto.SessionInfo;
import com.pi.chat.exception.SessionNotFoundException;
import com.pi.chat.service.SessionService;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for Session module.
 * 
 * <p>Tests the following correctness properties:
 * <ul>
 *   <li>Property 6: Session Lifecycle Round-Trip</li>
 *   <li>Property 7: Session Deletion Removes All Data</li>
 *   <li>Property 8: Session Rename Persistence</li>
 *   <li>Property 18: Session Persistence Format</li>
 *   <li>Property 19: Session Recovery</li>
 * </ul>
 */
class SessionPropertyTest {
    
    @TempDir
    Path tempDir;
    
    private SessionManagerFactory factory;
    private SessionService service;
    
    @BeforeEach
    void setUp() {
        String cwd = tempDir.toString();
        factory = new SessionManagerFactory(tempDir, cwd);
        service = new SessionService(factory);
    }
    
    // ========== Property 6: Session Lifecycle Round-Trip ==========
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 6: Session Lifecycle Round-Trip")
    void sessionLifecycleRoundTrip(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String name) {
        
        // Create session
        CreateSessionRequest request = new CreateSessionRequest(name, null, null);
        SessionInfo created = service.createSession(request);
        
        // Verify session appears in list
        List<SessionInfo> sessions = service.listSessions();
        assertThat(sessions)
            .extracting(SessionInfo::id)
            .contains(created.id());
        
        // Verify session has zero messages initially
        assertThat(created.messageCount()).isZero();
        
        // Verify session is retrievable by ID with correct name
        SessionInfo retrieved = service.getSessionInfo(created.id());
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.name()).isEqualTo(name);
    }
    
    @Test
    @Tag("Feature: ai-chat-web, Property 6: Session Lifecycle Round-Trip")
    void sessionCreatedWithDefaultName() {
        // Create session without name
        SessionInfo created = service.createSession(null);
        
        // Should have default name
        assertThat(created.name()).isEqualTo("New Chat");
        assertThat(created.id()).isNotBlank();
        assertThat(created.messageCount()).isZero();
    }
    
    // ========== Property 7: Session Deletion Removes All Data ==========
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 7: Session Deletion Removes All Data")
    void deletedSessionNotFound(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String name) {
        
        // Create session
        CreateSessionRequest request = new CreateSessionRequest(name, null, null);
        SessionInfo created = service.createSession(request);
        String sessionId = created.id();
        
        // Delete session
        service.deleteSession(sessionId);
        
        // Verify session not found
        assertThatThrownBy(() -> service.getSessionInfo(sessionId))
            .isInstanceOf(SessionNotFoundException.class);
    }
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 7: Session Deletion Removes All Data")
    void deletedSessionNotInList(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String name) {
        
        // Create session
        CreateSessionRequest request = new CreateSessionRequest(name, null, null);
        SessionInfo created = service.createSession(request);
        String sessionId = created.id();
        
        // Delete session
        service.deleteSession(sessionId);
        
        // Verify session not in list
        List<SessionInfo> sessions = service.listSessions();
        assertThat(sessions)
            .extracting(SessionInfo::id)
            .doesNotContain(sessionId);
    }
    
    @Property(tries = 30)
    @Tag("Feature: ai-chat-web, Property 7: Session Deletion Removes All Data")
    void deletedSessionFileRemoved(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String name) {
        
        // Create session
        CreateSessionRequest request = new CreateSessionRequest(name, null, null);
        SessionInfo created = service.createSession(request);
        String sessionId = created.id();
        
        // Verify file exists
        Path sessionFile = tempDir.resolve(sessionId + ".jsonl");
        assertThat(Files.exists(sessionFile)).isTrue();
        
        // Delete session
        service.deleteSession(sessionId);
        
        // Verify file removed
        assertThat(Files.exists(sessionFile)).isFalse();
    }
    
    @Test
    @Tag("Feature: ai-chat-web, Property 7: Session Deletion Removes All Data")
    void deleteNonExistentSessionThrows() {
        assertThatThrownBy(() -> service.deleteSession("non-existent-id"))
            .isInstanceOf(SessionNotFoundException.class);
    }
    
    // ========== Property 8: Session Rename Persistence ==========
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 8: Session Rename Persistence")
    void sessionRenamePersistence(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String originalName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String newName) {
        
        Assume.that(!originalName.equals(newName));
        
        // Create session
        CreateSessionRequest request = new CreateSessionRequest(originalName, null, null);
        SessionInfo created = service.createSession(request);
        
        // Rename session
        SessionInfo renamed = service.renameSession(created.id(), newName);
        
        // Verify new name
        assertThat(renamed.name()).isEqualTo(newName);
        
        // Verify persistence by retrieving again
        SessionInfo retrieved = service.getSessionInfo(created.id());
        assertThat(retrieved.name()).isEqualTo(newName);
    }
    
    // ========== Property 18: Session Persistence Format ==========
    
    @Property(tries = 30)
    @Tag("Feature: ai-chat-web, Property 18: Session Persistence Format")
    void sessionFileIsValidJsonl(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String name) throws Exception {
        
        // Create session
        CreateSessionRequest request = new CreateSessionRequest(name, null, null);
        SessionInfo created = service.createSession(request);
        
        // Read session file
        Path sessionFile = tempDir.resolve(created.id() + ".jsonl");
        List<String> lines = Files.readAllLines(sessionFile);
        
        // Verify each line is valid JSON
        assertThat(lines).isNotEmpty();
        for (String line : lines) {
            assertThat(line).isNotBlank();
            // Basic JSON validation - should start with { and end with }
            String trimmed = line.trim();
            assertThat(trimmed).startsWith("{");
            assertThat(trimmed).endsWith("}");
        }
    }
    
    // ========== Property 19: Session Recovery ==========
    
    @Property(tries = 30)
    @Tag("Feature: ai-chat-web, Property 19: Session Recovery")
    void sessionRecoveryAfterReload(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String name) {
        
        // Create session
        CreateSessionRequest request = new CreateSessionRequest(name, null, null);
        SessionInfo created = service.createSession(request);
        String sessionId = created.id();
        
        // Close session (remove from active managers)
        service.closeSession(sessionId);
        
        // Create new service instance (simulating restart)
        SessionService newService = new SessionService(factory);
        
        // Verify session is recoverable
        SessionInfo recovered = newService.getSessionInfo(sessionId);
        assertThat(recovered.id()).isEqualTo(sessionId);
        assertThat(recovered.name()).isEqualTo(name);
    }
}
