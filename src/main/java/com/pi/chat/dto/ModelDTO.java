package com.pi.chat.dto;

import com.pi.ai.core.types.Model;
import com.pi.ai.core.types.ModelCost;

import java.util.List;

/**
 * Model DTO for API responses.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>2.3 - Display model information including name, context window, and cost</li>
 * </ul>
 * 
 * @param id            Model unique identifier
 * @param name          Model display name
 * @param provider      Provider identifier
 * @param contextWindow Context window size in tokens
 * @param maxTokens     Maximum output tokens
 * @param reasoning     Whether the model supports reasoning/thinking
 * @param inputTypes    Supported input types (text, image, etc.)
 * @param cost          Model pricing information
 */
public record ModelDTO(
    String id,
    String name,
    String provider,
    int contextWindow,
    int maxTokens,
    boolean reasoning,
    List<String> inputTypes,
    ModelCostDTO cost
) {
    
    /**
     * Creates a ModelDTO from a Model.
     * 
     * @param model The source model
     * @return ModelDTO instance
     */
    public static ModelDTO from(Model model) {
        return new ModelDTO(
            model.id(),
            model.name(),
            model.provider(),
            model.contextWindow(),
            model.maxTokens(),
            model.reasoning(),
            model.input(),
            model.cost() != null ? ModelCostDTO.from(model.cost()) : null
        );
    }
    
    /**
     * Model cost DTO.
     * 
     * @param input      Input token cost ($/million tokens)
     * @param output     Output token cost ($/million tokens)
     * @param cacheRead  Cache read cost ($/million tokens)
     * @param cacheWrite Cache write cost ($/million tokens)
     */
    public record ModelCostDTO(
        double input,
        double output,
        double cacheRead,
        double cacheWrite
    ) {
        public static ModelCostDTO from(ModelCost cost) {
            return new ModelCostDTO(
                cost.input(),
                cost.output(),
                cost.cacheRead(),
                cost.cacheWrite()
            );
        }
    }
}
