package com.pi.chat.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable definition of a model brand.
 *
 * <p>Describes the metadata for a brand including its provider mapping,
 * API protocol type, default base URL, and preset model list.
 *
 * <p>Requirements:
 * <ul>
 *   <li>1.1 - Predefined built-in brands</li>
 *   <li>1.2 - Predefined extension brands</li>
 *   <li>1.3 - Extension brand API protocol = openai-completions</li>
 * </ul>
 *
 * @param id             Brand unique identifier (e.g. "claude", "deepseek")
 * @param name           Display name (e.g. "Claude", "DeepSeek")
 * @param provider       SDK provider ID (e.g. "anthropic", "deepseek")
 * @param apiType        API protocol (e.g. "anthropic-messages", "openai-completions")
 * @param defaultBaseUrl Default base URL for the brand
 * @param builtin        Whether this is a built-in brand with native SDK adapter
 * @param deletable      Whether the brand can be deleted (only custom brands)
 * @param defaultModels  Preset model list for the brand
 */
public record BrandDefinition(
    String id,
    String name,
    String provider,
    String apiType,
    String defaultBaseUrl,
    boolean builtin,
    boolean deletable,
    List<ModelEntry> defaultModels
) {

    /**
     * Validates required fields.
     */
    public BrandDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(apiType, "apiType must not be null");
        Objects.requireNonNull(defaultBaseUrl, "defaultBaseUrl must not be null");
        if (defaultModels == null) {
            defaultModels = List.of();
        }
    }
}
