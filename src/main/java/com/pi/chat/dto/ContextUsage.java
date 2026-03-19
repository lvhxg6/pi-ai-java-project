package com.pi.chat.dto;

/**
 * Context usage information for a session.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>6.5 - Display current context usage</li>
 * </ul>
 * 
 * @param currentTokens Current context token count
 * @param contextWindow Model context window size
 * @param usagePercent  Usage percentage (0-100)
 * @param nearLimit     Whether usage is near limit (>80%)
 */
public record ContextUsage(
    int currentTokens,
    int contextWindow,
    double usagePercent,
    boolean nearLimit
) {
    
    /**
     * Creates a ContextUsage from token counts.
     * 
     * @param currentTokens Current token count
     * @param contextWindow Context window size
     * @return ContextUsage instance
     */
    public static ContextUsage of(int currentTokens, int contextWindow) {
        double percent = contextWindow > 0 ? (currentTokens * 100.0 / contextWindow) : 0;
        return new ContextUsage(currentTokens, contextWindow, percent, percent > 80);
    }
}
