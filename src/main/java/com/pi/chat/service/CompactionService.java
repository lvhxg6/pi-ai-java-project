package com.pi.chat.service;

import com.pi.chat.dto.ContextUsage;
import com.pi.coding.compaction.CompactionUtils;
import com.pi.coding.session.SessionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Service for managing context compaction.
 * 
 * <p>Provides automatic and manual context compaction to keep conversations
 * within model context limits.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>6.1 - Monitor context usage</li>
 *   <li>6.2 - Trigger automatic compaction at threshold</li>
 *   <li>6.3 - Generate summary of compacted content</li>
 *   <li>6.4 - Preserve recent messages</li>
 *   <li>6.5 - Display context usage</li>
 * </ul>
 */
public class CompactionService {
    
    private static final Logger log = LoggerFactory.getLogger(CompactionService.class);
    
    private static final double DEFAULT_THRESHOLD = 0.8; // 80%
    private static final int DEFAULT_PRESERVE_RECENT = 10; // Keep last 10 messages
    
    private final SessionService sessionService;
    private final double compactionThreshold;
    private final int preserveRecentMessages;
    
    /**
     * Creates a new CompactionService with default settings.
     */
    public CompactionService(SessionService sessionService) {
        this(sessionService, DEFAULT_THRESHOLD, DEFAULT_PRESERVE_RECENT);
    }
    
    /**
     * Creates a new CompactionService with custom settings.
     */
    public CompactionService(SessionService sessionService, double compactionThreshold, 
                             int preserveRecentMessages) {
        this.sessionService = Objects.requireNonNull(sessionService);
        this.compactionThreshold = compactionThreshold;
        this.preserveRecentMessages = preserveRecentMessages;
    }
    
    /**
     * Checks if compaction is needed for a session.
     * 
     * @param sessionId     The session ID
     * @param contextWindow The model's context window size
     * @return true if compaction is needed
     */
    public boolean needsCompaction(String sessionId, int contextWindow) {
        SessionManager manager = sessionService.getSession(sessionId);
        int currentTokens = estimateTokens(manager);
        double usage = (double) currentTokens / contextWindow;
        return usage >= compactionThreshold;
    }
    
    /**
     * Gets context usage for a session.
     * 
     * @param sessionId     The session ID
     * @param contextWindow The model's context window size
     * @return Context usage information
     */
    public ContextUsage getContextUsage(String sessionId, int contextWindow) {
        SessionManager manager = sessionService.getSession(sessionId);
        int currentTokens = estimateTokens(manager);
        return ContextUsage.of(currentTokens, contextWindow);
    }
    
    /**
     * Performs compaction on a session.
     * 
     * <p>This is a simplified implementation. Full compaction would use
     * the Compaction class from pi-coding-agent to generate summaries.
     * 
     * @param sessionId     The session ID
     * @param contextWindow The model's context window size
     * @return Number of tokens saved
     */
    public int performCompaction(String sessionId, int contextWindow) {
        SessionManager manager = sessionService.getSession(sessionId);
        
        int tokensBefore = estimateTokens(manager);
        
        // For now, we just log that compaction would happen
        // Full implementation would use Compaction.compact()
        log.info("Compaction triggered for session {} - tokens before: {}, context window: {}",
            sessionId, tokensBefore, contextWindow);
        
        // In a full implementation, we would:
        // 1. Use Compaction.findCutPoint() to find where to cut
        // 2. Use SummaryGenerator to create a summary of cut content
        // 3. Call manager.appendCompaction() to record the compaction
        
        // For now, return 0 as no actual compaction is performed
        return 0;
    }
    
    /**
     * Estimates token count for a session.
     * 
     * <p>Uses a simple character-based estimation (4 chars per token).
     * A production implementation would use proper tokenization.
     * 
     * @param manager The session manager
     * @return Estimated token count
     */
    private int estimateTokens(SessionManager manager) {
        var context = manager.buildSessionContext();
        return CompactionUtils.estimateTokens(context.messages());
    }
}
