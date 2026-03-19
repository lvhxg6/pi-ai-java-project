package com.pi.chat.exception;

/**
 * Exception thrown when a requested session is not found.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>3.5 - Handle session deletion and not found scenarios</li>
 *   <li>9.5 - Return appropriate HTTP status codes (404 for not found)</li>
 * </ul>
 */
public class SessionNotFoundException extends RuntimeException {
    
    private final String sessionId;
    
    /**
     * Creates a new SessionNotFoundException.
     * 
     * @param sessionId The ID of the session that was not found
     */
    public SessionNotFoundException(String sessionId) {
        super("Session not found: " + sessionId);
        this.sessionId = sessionId;
    }
    
    /**
     * Creates a new SessionNotFoundException with a custom message.
     * 
     * @param sessionId The ID of the session that was not found
     * @param message   Custom error message
     */
    public SessionNotFoundException(String sessionId, String message) {
        super(message);
        this.sessionId = sessionId;
    }
    
    /**
     * Gets the ID of the session that was not found.
     * 
     * @return The session ID
     */
    public String getSessionId() {
        return sessionId;
    }
}
