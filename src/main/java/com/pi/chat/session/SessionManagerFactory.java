package com.pi.chat.session;

import com.pi.coding.session.SessionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Factory for creating and managing SessionManager instances.
 * 
 * <p>Provides methods to:
 * <ul>
 *   <li>Create new sessions</li>
 *   <li>Open existing sessions from files</li>
 *   <li>List all session files in the storage directory</li>
 * </ul>
 * 
 * <p>Requirements:
 * <ul>
 *   <li>3.6 - Session file management</li>
 *   <li>7.3 - Session storage directory configuration</li>
 * </ul>
 */
public class SessionManagerFactory {
    
    private static final Logger log = LoggerFactory.getLogger(SessionManagerFactory.class);
    
    private static final String SESSION_FILE_EXTENSION = ".jsonl";
    
    private final Path sessionsBaseDir;
    private final String cwd;
    
    /**
     * Creates a new SessionManagerFactory.
     * 
     * @param sessionsBaseDir Base directory for session storage
     * @param cwd             Current working directory for sessions
     */
    public SessionManagerFactory(Path sessionsBaseDir, String cwd) {
        this.sessionsBaseDir = Objects.requireNonNull(sessionsBaseDir, "sessionsBaseDir must not be null");
        this.cwd = Objects.requireNonNull(cwd, "cwd must not be null");
        
        // Ensure directory exists
        try {
            if (!Files.exists(sessionsBaseDir)) {
                Files.createDirectories(sessionsBaseDir);
                log.info("Created sessions directory: {}", sessionsBaseDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create sessions directory: " + sessionsBaseDir, e);
        }
    }
    
    /**
     * Creates a new session with a generated ID.
     * 
     * @return New SessionManager instance
     */
    public SessionManager create() {
        SessionManager manager = SessionManager.create(cwd, sessionsBaseDir);
        log.debug("Created new session: {}", manager.getSessionId());
        return manager;
    }
    
    /**
     * Creates a new session with a specific name.
     * 
     * @param name Session name
     * @return New SessionManager instance
     */
    public SessionManager createWithName(String name) {
        SessionManager manager = SessionManager.create(cwd, sessionsBaseDir);
        if (name != null && !name.isBlank()) {
            manager.appendSessionInfo(name);
        }
        log.debug("Created new session with name '{}': {}", name, manager.getSessionId());
        return manager;
    }
    
    /**
     * Opens an existing session from a file.
     * 
     * @param sessionFile Path to the session file
     * @return SessionManager instance for the session
     * @throws IllegalArgumentException if the file does not exist
     */
    public SessionManager open(Path sessionFile) {
        Objects.requireNonNull(sessionFile, "sessionFile must not be null");
        
        if (!Files.exists(sessionFile)) {
            throw new IllegalArgumentException("Session file does not exist: " + sessionFile);
        }
        
        SessionManager manager = SessionManager.open(sessionFile, sessionsBaseDir);
        log.debug("Opened session: {} from {}", manager.getSessionId(), sessionFile);
        return manager;
    }
    
    /**
     * Opens an existing session by ID.
     * 
     * @param sessionId The session ID
     * @return SessionManager instance for the session
     * @throws IllegalArgumentException if the session does not exist
     */
    public SessionManager openById(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        
        Path sessionFile = sessionsBaseDir.resolve(sessionId + SESSION_FILE_EXTENSION);
        return open(sessionFile);
    }
    
    /**
     * Checks if a session exists.
     * 
     * @param sessionId The session ID
     * @return true if the session exists
     */
    public boolean exists(String sessionId) {
        Path sessionFile = sessionsBaseDir.resolve(sessionId + SESSION_FILE_EXTENSION);
        return Files.exists(sessionFile);
    }
    
    /**
     * Lists all session files in the storage directory.
     * 
     * @return List of session file paths
     */
    public List<Path> listSessionFiles() {
        try (Stream<Path> files = Files.list(sessionsBaseDir)) {
            return files
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(SESSION_FILE_EXTENSION))
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list session files in: {}", sessionsBaseDir, e);
            return List.of();
        }
    }
    
    /**
     * Deletes a session file.
     * 
     * @param sessionId The session ID
     * @return true if the session was deleted
     */
    public boolean delete(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        
        Path sessionFile = sessionsBaseDir.resolve(sessionId + SESSION_FILE_EXTENSION);
        try {
            boolean deleted = Files.deleteIfExists(sessionFile);
            if (deleted) {
                log.info("Deleted session: {}", sessionId);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete session: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Gets the base directory for session storage.
     * 
     * @return Sessions base directory path
     */
    public Path getSessionsBaseDir() {
        return sessionsBaseDir;
    }
    
    /**
     * Gets the current working directory for sessions.
     * 
     * @return Current working directory
     */
    public String getCwd() {
        return cwd;
    }
}
