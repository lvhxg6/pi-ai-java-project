package com.pi.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO for models grouped by brand.
 *
 * <p>Requirements:
 * <ul>
 *   <li>9.7 - Return models grouped by brand</li>
 * </ul>
 *
 * @param brandId   Brand unique identifier
 * @param brandName Brand display name
 * @param models    List of models belonging to this brand
 */
public record GroupedModels(
    @JsonProperty("brandId") String brandId,
    @JsonProperty("brandName") String brandName,
    @JsonProperty("models") List<ModelDTO> models
) {}
