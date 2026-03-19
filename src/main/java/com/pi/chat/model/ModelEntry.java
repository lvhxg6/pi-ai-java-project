package com.pi.chat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * A model entry representing a single AI model within a brand.
 *
 * <p>Each model entry contains the model's identifier, display name,
 * context window size, maximum output tokens, and whether it is a
 * user-defined custom model.
 *
 * <p>Requirements:
 * <ul>
 *   <li>1.5 - Model_Entry with ID, name, context window, max tokens</li>
 *   <li>3.3 - Custom model entries added by users</li>
 * </ul>
 *
 * @param id            Model identifier (e.g. "deepseek-chat")
 * @param name          Display name (e.g. "DeepSeek Chat")
 * @param contextWindow Context window size in tokens
 * @param maxTokens     Maximum output tokens
 * @param custom        Whether this is a user-defined custom model
 */
public record ModelEntry(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("contextWindow") int contextWindow,
    @JsonProperty("maxTokens") int maxTokens,
    @JsonProperty("custom") boolean custom
) {

    /**
     * Validates that id and name are non-null/non-blank,
     * and contextWindow and maxTokens are positive.
     */
    public ModelEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (contextWindow <= 0) {
            throw new IllegalArgumentException("contextWindow must be positive, got: " + contextWindow);
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive, got: " + maxTokens);
        }
    }
}
