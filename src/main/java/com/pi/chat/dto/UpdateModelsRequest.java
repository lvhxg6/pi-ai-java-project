package com.pi.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.chat.model.ModelEntry;

import java.util.List;

/**
 * Request DTO for updating a brand's model list.
 *
 * <p>Requirements:
 * <ul>
 *   <li>9.6 - Update brand model list</li>
 * </ul>
 *
 * @param models List of model entries to set for the brand
 */
public record UpdateModelsRequest(
    @JsonProperty("models") List<ModelEntry> models
) {}
