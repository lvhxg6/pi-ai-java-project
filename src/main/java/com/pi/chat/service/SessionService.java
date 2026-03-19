package com.pi.chat.service;

import com.pi.agent.types.AgentMessage;
import com.pi.chat.dto.CreateSessionRequest;
import com.pi.chat.dto.MessageDTO;
import com.pi.chat.dto.SessionInfo;
import com.pi.chat.exception.SessionNotFoundException;
import com.pi.chat.session.SessionManagerFactory;
import com.pi.coding.session.SessionContext;
import com.pi.coding.session.SessionEntry;
import com.pi.coding.session.SessionHeader;
import com.pi.coding.session.SessionInfoEntry;
import com.pi.coding.session.SessionManager;
import com.pi.coding.session.SessionMessageEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing chat sessions.
 * 
 * <p>Provides CRUD operations for sessions and manages active SessionManager instances.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>3.1 - Create new session</li>
 *   <li>3.2 - Display session list</li>
 *   <li>3.3 - Load session and display message history</li>
 *   <li>3.4 - Switch between sessions</li>
 *   <li>3.5 - Delete session</li>
 *   <li>3.7 - Rename session</li>
 * </ul>
 */
public class SessionService {
    
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    
    private final SessionManagerFactory factory;
    private final Map<String, SessionManager> activeManagers = new ConcurrentHashMap<>();
    
    /**
     * Creates a new SessionService.
     * 
     * @param factory The session manager factory
     */
    public SessionService(SessionManagerFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
    }
    
    /**
     * Lists all sessions.
     * 
     * @return List of session information
     */
    public List<SessionInfo> listSessions() {
        List<Path> sessionFiles = factory.listSessionFiles();
        List<SessionInfo> sessions = new ArrayList<>();
        
        for (Path file : sessionFiles) {
            try {
                SessionManager manager = getOrOpenSession(file);
                sessions.add(buildSessionInfo(manager));
            } catch (Exception e) {
                log.warn("Failed to load session from file: {}", file, e);
            }
        }
        
        // Sort by last activity (most recent first)
        sessions.sort((a, b) -> b.lastActivityAt().compareTo(a.lastActivityAt()));
        
        return sessions;
    }
    
    /**
     * Creates a new session.
     * 
     * @param request The create request
     * @return The created session info
     */
    public SessionInfo createSession(CreateSessionRequest request) {
        String name = request != null && request.name() != null ? request.name() : "New Chat";
        
        SessionManager manager = factory.createWithName(name);
        String sessionId = manager.getSessionId();
        
        // Set initial model if provided
        if (request != null && request.modelProvider() != null && request.modelId() != null) {
            manager.appendModelChange(request.modelProvider(), request.modelId());
        }
        
        activeManagers.put(sessionId, manager);
        log.info("Created session: {} with name '{}'", sessionId, name);
        
        return buildSessionInfo(manager);
    }
    
    /**
     * Gets a session by ID.
     * 
     * @param sessionId The session ID
     * @return The session manager
     * @throws SessionNotFoundException if the session is not found
     */
    public SessionManager getSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        
        // Check active managers first
        SessionManager manager = activeManagers.get(sessionId);
        if (manager != null) {
            return manager;
        }
        
        // Try to open from file
        if (!factory.exists(sessionId)) {
            throw new SessionNotFoundException(sessionId);
        }
        
        manager = factory.openById(sessionId);
        activeManagers.put(sessionId, manager);
        return manager;
    }
    
    /**
     * Gets session info by ID.
     * 
     * @param sessionId The session ID
     * @return The session info
     * @throws SessionNotFoundException if the session is not found
     */
    public SessionInfo getSessionInfo(String sessionId) {
        SessionManager manager = getSession(sessionId);
        return buildSessionInfo(manager);
    }
    
    /**
     * Deletes a session.
     * 
     * @param sessionId The session ID
     * @throws SessionNotFoundException if the session is not found
     */
    public void deleteSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        
        if (!factory.exists(sessionId)) {
            throw new SessionNotFoundException(sessionId);
        }
        
        // Remove from active managers
        activeManagers.remove(sessionId);
        
        // Delete the file
        boolean deleted = factory.delete(sessionId);
        if (!deleted) {
            throw new RuntimeException("Failed to delete session: " + sessionId);
        }
        
        log.info("Deleted session: {}", sessionId);
    }
    
    /**
     * Renames a session.
     * 
     * @param sessionId The session ID
     * @param newName   The new name
     * @return The updated session info
     * @throws SessionNotFoundException if the session is not found
     */
    public SessionInfo renameSession(String sessionId, String newName) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(newName, "newName must not be null");
        
        SessionManager manager = getSession(sessionId);
        manager.appendSessionInfo(newName);
        
        log.info("Renamed session {} to '{}'", sessionId, newName);
        return buildSessionInfo(manager);
    }
    
    /**
     * Gets messages for a session.
     * 
     * @param sessionId The session ID
     * @return List of messages
     * @throws SessionNotFoundException if the session is not found
     */
    public List<MessageDTO> getMessages(String sessionId) {
        SessionManager manager = getSession(sessionId);
        SessionContext context = manager.buildSessionContext();
        
        List<MessageDTO> messages = new ArrayList<>();
        List<AgentMessage> agentMessages = context.messages();
        
        for (int i = 0; i < agentMessages.size(); i++) {
            AgentMessage msg = agentMessages.get(i);
            // Generate a simple ID based on index
            String msgId = "msg-" + i;
            messages.add(MessageDTO.from(msg, msgId, Instant.now()));
        }
        
        return messages;
    }
    
    /**
     * Builds session context for a session.
     * 
     * @param sessionId The session ID
     * @return The session context
     * @throws SessionNotFoundException if the session is not found
     */
    public SessionContext buildContext(String sessionId) {
        SessionManager manager = getSession(sessionId);
        return manager.buildSessionContext();
    }
    
    /**
     * Closes a session (removes from active managers).
     * 
     * @param sessionId The session ID
     */
    public void closeSession(String sessionId) {
        activeManagers.remove(sessionId);
        log.debug("Closed session: {}", sessionId);
    }
    
    // ========== Private Methods ==========
    
    private SessionManager getOrOpenSession(Path file) {
        String fileName = file.getFileName().toString();
        String sessionId = fileName.replace(".jsonl", "");
        
        SessionManager manager = activeManagers.get(sessionId);
        if (manager == null) {
            manager = factory.open(file);
            activeManagers.put(sessionId, manager);
        }
        return manager;
    }
    
    private SessionInfo buildSessionInfo(SessionManager manager) {
        SessionHeader header = manager.getHeader();
        List<SessionEntry> entries = manager.getEntries();
        
        String name = extractSessionName(entries);
        Instant createdAt = parseTimestamp(header.timestamp());
        Instant lastActivityAt = extractLastActivityTime(entries, createdAt);
        String currentModel = extractCurrentModel(entries);
        int messageCount = countMessages(entries);
        
        return new SessionInfo(
            manager.getSessionId(),
            name,
            createdAt,
            lastActivityAt,
            currentModel,
            messageCount
        );
    }
    
    private String extractSessionName(List<SessionEntry> entries) {
        // Find the last SessionInfoEntry
        for (int i = entries.size() - 1; i >= 0; i--) {
            SessionEntry entry = entries.get(i);
            if (entry instanceof SessionInfoEntry info) {
                return info.name();
            }
        }
        return "Untitled";
    }
    
    private Instant extractLastActivityTime(List<SessionEntry> entries, Instant defaultTime) {
        if (entries.isEmpty()) {
            return defaultTime;
        }
        
        SessionEntry lastEntry = entries.get(entries.size() - 1);
        return parseTimestamp(lastEntry.timestamp());
    }
    
    private String extractCurrentModel(List<SessionEntry> entries) {
        // Find the last model change entry
        for (int i = entries.size() - 1; i >= 0; i--) {
            SessionEntry entry = entries.get(i);
            if (entry instanceof com.pi.coding.session.ModelChangeEntry modelChange) {
                return modelChange.provider() + "/" + modelChange.modelId();
            }
        }
        return null;
    }
    
    private int countMessages(List<SessionEntry> entries) {
        int count = 0;
        for (SessionEntry entry : entries) {
            if (entry instanceof SessionMessageEntry) {
                count++;
            }
        }
        return count;
    }
    
    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
