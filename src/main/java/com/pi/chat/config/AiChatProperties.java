package com.pi.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for AI Chat Web application.
 * 
 * <p>Binds to properties prefixed with {@code ai-chat} in application.yml.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>7.3 - Session storage configuration</li>
 *   <li>10.1 - Provider configuration management</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "ai-chat")
@Validated
public record AiChatProperties(
    SessionProperties session,
    ProviderProperties provider,
    SseProperties sse,
    CompactionProperties compaction
) {
    
    /**
     * Session storage configuration.
     */
    public record SessionProperties(
        /** Directory path for storing session files (JSONL format). */
        @NotBlank
        String storageDir,
        
        /** Auto-save interval in seconds. */
        @Positive
        int autoSaveInterval
    ) {
        public SessionProperties {
            if (storageDir == null || storageDir.isBlank()) {
                storageDir = "data/sessions";
            }
            if (autoSaveInterval <= 0) {
                autoSaveInterval = 5;
            }
        }
    }
    
    /**
     * Provider configuration.
     */
    public record ProviderProperties(
        /** Path to the providers.json configuration file. */
        @NotBlank
        String configFile,
        
        /** Encryption key for API key storage. */
        String encryptionKey
    ) {
        public ProviderProperties {
            if (configFile == null || configFile.isBlank()) {
                configFile = "data/providers.json";
            }
        }
    }
    
    /**
     * SSE (Server-Sent Events) streaming configuration.
     */
    public record SseProperties(
        /** SSE connection timeout in milliseconds. */
        @Positive
        long timeout,
        
        /** Heartbeat interval in milliseconds to keep connection alive. */
        @Positive
        long heartbeatInterval
    ) {
        public SseProperties {
            if (timeout <= 0) {
                timeout = 300000L; // 5 minutes default
            }
            if (heartbeatInterval <= 0) {
                heartbeatInterval = 30000L; // 30 seconds default
            }
        }
    }
    
    /**
     * Context compaction configuration.
     */
    public record CompactionProperties(
        /** Percentage of context window that triggers compaction (0-100). */
        @Min(50) @Max(100)
        int thresholdPercent,
        
        /** Number of recent messages to preserve during compaction. */
        @Min(1)
        int preserveRecentCount
    ) {
        public CompactionProperties {
            if (thresholdPercent <= 0 || thresholdPercent > 100) {
                thresholdPercent = 80;
            }
            if (preserveRecentCount <= 0) {
                preserveRecentCount = 5;
            }
        }
    }
    
    /**
     * Creates default properties with sensible defaults.
     */
    public AiChatProperties {
        if (session == null) {
            session = new SessionProperties("data/sessions", 5);
        }
        if (provider == null) {
            provider = new ProviderProperties("data/providers.json", null);
        }
        if (sse == null) {
            sse = new SseProperties(300000L, 30000L);
        }
        if (compaction == null) {
            compaction = new CompactionProperties(80, 5);
        }
    }
}
