package com.pi.chat.model;

/**
 * Connection status for an LLM Provider.
 * 
 * <p>Represents the current connectivity state of a configured provider.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>1.6 - Display connection status for configured providers</li>
 * </ul>
 */
public enum ConnectionStatus {
    
    /**
     * Provider is connected and API key is valid.
     */
    CONNECTED,
    
    /**
     * Provider is disconnected or API key is invalid.
     */
    DISCONNECTED,
    
    /**
     * Connection status has not been verified.
     */
    UNKNOWN
}
