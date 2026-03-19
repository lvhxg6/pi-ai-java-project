package com.pi.chat.model;

import java.util.List;
import java.util.Optional;

/**
 * Constants class holding all predefined brand definitions.
 *
 * <p>Contains 5 built-in brands (with native SDK adapters) and
 * 4 extension brands (using openai-completions protocol).
 *
 * <p>Requirements:
 * <ul>
 *   <li>1.1 - Predefined built-in brands</li>
 *   <li>1.2 - Predefined extension brands</li>
 *   <li>1.3 - Extension brands use openai-completions</li>
 *   <li>1.4 - Built-in brands load models from SDK models.json</li>
 *   <li>1.5 - Extension brands have preset default model lists</li>
 * </ul>
 */
public final class BrandDefinitions {

    private BrandDefinitions() {
        // utility class
    }

    // ===== Built-in brands =====

    public static final BrandDefinition CLAUDE = new BrandDefinition(
        "claude", "Claude", "anthropic", "anthropic-messages",
        "https://api.anthropic.com", true, false, List.of()
    );

    public static final BrandDefinition CHATGPT = new BrandDefinition(
        "chatgpt", "ChatGPT", "openai", "openai-responses",
        "https://api.openai.com/v1", true, false, List.of()
    );

    public static final BrandDefinition GEMINI = new BrandDefinition(
        "gemini", "Gemini", "google", "google-generative-ai",
        "https://generativelanguage.googleapis.com/v1beta", true, false, List.of()
    );

    public static final BrandDefinition MISTRAL = new BrandDefinition(
        "mistral", "Mistral", "mistral", "mistral-conversations",
        "https://api.mistral.ai", true, false, List.of()
    );

    public static final BrandDefinition BEDROCK = new BrandDefinition(
        "bedrock", "AWS Bedrock", "amazon-bedrock", "bedrock-converse-stream",
        "https://bedrock-runtime.us-east-1.amazonaws.com", true, false, List.of()
    );

    // ===== Extension brands =====

    public static final BrandDefinition DEEPSEEK = new BrandDefinition(
        "deepseek", "DeepSeek", "deepseek", "openai-completions",
        "https://api.deepseek.com", false, false,
        List.of(
            new ModelEntry("deepseek-chat", "DeepSeek Chat", 64000, 8192, false),
            new ModelEntry("deepseek-reasoner", "DeepSeek Reasoner", 64000, 8192, false)
        )
    );

    public static final BrandDefinition GLM = new BrandDefinition(
        "glm", "GLM/智谱", "glm", "openai-completions",
        "https://open.bigmodel.cn", false, false,
        List.of(
            new ModelEntry("glm-4-plus", "GLM-4 Plus", 128000, 4096, false),
            new ModelEntry("glm-4-flash", "GLM-4 Flash", 128000, 4096, false)
        )
    );

    public static final BrandDefinition MINIMAX = new BrandDefinition(
        "minimax", "MiniMax", "minimax", "openai-completions",
        "https://api.minimax.chat", false, false,
        List.of(
            new ModelEntry("MiniMax-Text-01", "MiniMax Text 01", 1000000, 4096, false)
        )
    );

    public static final BrandDefinition KIMI = new BrandDefinition(
        "kimi", "Kimi/月之暗面", "kimi", "openai-completions",
        "https://api.moonshot.cn", false, false,
        List.of(
            new ModelEntry("moonshot-v1-8k", "Moonshot v1 8K", 8000, 4096, false),
            new ModelEntry("moonshot-v1-32k", "Moonshot v1 32K", 32000, 4096, false),
            new ModelEntry("moonshot-v1-128k", "Moonshot v1 128K", 128000, 4096, false)
        )
    );

    /**
     * All predefined brands: built-in first, then extension brands.
     */
    public static final List<BrandDefinition> ALL = List.of(
        CLAUDE, CHATGPT, GEMINI, MISTRAL, BEDROCK,
        DEEPSEEK, GLM, MINIMAX, KIMI
    );

    /**
     * Finds a predefined brand definition by ID.
     *
     * @param id Brand ID to look up
     * @return Optional containing the brand definition if found
     */
    public static Optional<BrandDefinition> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return ALL.stream()
            .filter(b -> b.id().equals(id))
            .findFirst();
    }

    /**
     * Checks whether the given ID belongs to a predefined brand.
     *
     * @param id Brand ID to check
     * @return true if the ID matches a predefined brand
     */
    public static boolean isPredefined(String id) {
        return findById(id).isPresent();
    }
}
