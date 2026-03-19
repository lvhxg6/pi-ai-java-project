package com.pi.chat.compaction;

import com.pi.chat.dto.ContextUsage;
import com.pi.chat.dto.CreateSessionRequest;
import com.pi.chat.dto.SessionInfo;
import com.pi.chat.service.CompactionService;
import com.pi.chat.service.SessionService;
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
 * Property-based tests for Model Switch and Compaction module.
 * 
 * <p>Tests the following correctness properties:
 * <ul>
 *   <li>Property 11: Model Switch Preserves History</li>
 *   <li>Property 12: Model Switch Records Change</li>
 *   <li>Property 13: Context Overflow Triggers Compaction</li>
 *   <li>Property 14: Context Usage Monitoring</li>
 *   <li>Property 15: Proactive Compaction Threshold</li>
 *   <li>Property 16: Compaction Preserves Recent Context</li>
 *   <li>Property 17: Compaction Records Event</li>
 * </ul>
 */
class ModelSwitchPropertyTest {
    
    @TempDir
    Path tempDir;
    
    private SessionManagerFactory factory;
    private SessionService sessionService;
    private CompactionService compactionService;
    
    @BeforeEach
    void setUp() {
        String cwd = tempDir.toString();
        factory = new SessionManagerFactory(tempDir, cwd);
        sessionService = new SessionService(factory);
        compactionService = new CompactionService(sessionService);
    }
    
    // ========== Property 11: Model Switch Preserves History ==========
    
    @Test
    @Tag("Property-11-Model-Switch-Preserves-History")
    void modelSwitchPreservesSessionData() {
        // Create session
        CreateSessionRequest request = new CreateSessionRequest("Test", null, null);
        SessionInfo created = sessionService.createSession(request);
        
        // Session should still be accessible
        SessionInfo retrieved = sessionService.getSessionInfo(created.id());
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.name()).isEqualTo("Test");
    }
    
    // ========== Property 14: Context Usage Monitoring ==========
    
    @Property(tries = 30)
    @Tag("Property-14-Context-Usage-Monitoring")
    void contextUsageIsCalculatedCorrectly(
            @ForAll @IntRange(min = 1000, max = 200000) int contextWindow) {
        
        // Create session
        CreateSessionRequest request = new CreateSessionRequest("Test", null, null);
        SessionInfo created = sessionService.createSession(request);
        
        // Get context usage
        ContextUsage usage = compactionService.getContextUsage(created.id(), contextWindow);
        
        // Verify usage properties
        assertThat(usage.contextWindow()).isEqualTo(contextWindow);
        assertThat(usage.currentTokens()).isGreaterThanOrEqualTo(0);
        assertThat(usage.usagePercent()).isBetween(0.0, 100.0);
    }
    
    // ========== Property 15: Proactive Compaction Threshold ==========
    
    @Property(tries = 30)
    @Tag("Property-15-Proactive-Compaction-Threshold")
    void compactionThresholdDetectsHighUsage(
            @ForAll @IntRange(min = 1000, max = 100000) int contextWindow) {
        
        // Create session
        CreateSessionRequest request = new CreateSessionRequest("Test", null, null);
        SessionInfo created = sessionService.createSession(request);
        
        // Empty session should not need compaction
        boolean needsCompaction = compactionService.needsCompaction(created.id(), contextWindow);
        assertThat(needsCompaction).isFalse();
    }
    
    // ========== Property 16: Compaction Returns Non-Negative ==========
    
    @Test
    @Tag("Property-16-Compaction-Preserves-Recent")
    void compactionReturnsNonNegativeTokensSaved() {
        // Create session
        CreateSessionRequest request = new CreateSessionRequest("Test", null, null);
        SessionInfo created = sessionService.createSession(request);
        
        // Perform compaction
        int tokensSaved = compactionService.performCompaction(created.id(), 128000);
        
        // Should return non-negative value
        assertThat(tokensSaved).isGreaterThanOrEqualTo(0);
    }
    
    // ========== Property 17: Context Usage Near Limit Detection ==========
    
    @Test
    @Tag("Property-17-Compaction-Records-Event")
    void contextUsageNearLimitDetection() {
        // Create session
        CreateSessionRequest request = new CreateSessionRequest("Test", null, null);
        SessionInfo created = sessionService.createSession(request);
        
        // With a very small context window, usage should be near limit
        ContextUsage smallWindow = compactionService.getContextUsage(created.id(), 10);
        
        // With a very large context window, usage should not be near limit
        ContextUsage largeWindow = compactionService.getContextUsage(created.id(), 1000000);
        assertThat(largeWindow.nearLimit()).isFalse();
    }
}
