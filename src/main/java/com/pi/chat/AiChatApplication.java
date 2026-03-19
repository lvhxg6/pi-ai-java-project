package com.pi.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Chat Web Application main entry point.
 * 
 * <p>This application provides a multi-model, multi-session AI chat interface
 * with automatic context compaction. It leverages the pi-ai-java SDK components:
 * <ul>
 *   <li>CodingModelRegistry - Model registration and API key management</li>
 *   <li>SessionManager - Session persistence (JSONL tree structure)</li>
 *   <li>Compaction - Context compression algorithm</li>
 *   <li>Agent/AgentSession - Agent loop engine</li>
 * </ul>
 */
@SpringBootApplication
public class AiChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatApplication.class, args);
    }
}
