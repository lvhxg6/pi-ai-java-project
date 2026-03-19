package com.pi.chat.service;

import com.pi.ai.core.types.Model;
import com.pi.chat.dto.ModelDTO;
import com.pi.coding.model.CodingModelRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for managing LLM models.
 * 
 * <p>Wraps CodingModelRegistry to provide model information for the web application.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>2.1 - Display list of available models from configured providers</li>
 *   <li>2.2 - Filter models by provider</li>
 *   <li>2.3 - Display model information including name, context window, and cost</li>
 * </ul>
 */
public class ModelService {
    
    private static final Logger log = LoggerFactory.getLogger(ModelService.class);
    
    private final CodingModelRegistry modelRegistry;
    
    /**
     * Creates a new ModelService.
     * 
     * @param modelRegistry The coding model registry
     */
    public ModelService(CodingModelRegistry modelRegistry) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
    }
    
    /**
     * Gets all available models from configured providers.
     * 
     * @return List of available models as DTOs
     */
    public List<ModelDTO> getAvailableModels() {
        List<Model> models = modelRegistry.getAvailableModels();
        log.debug("Found {} available models", models.size());
        
        return models.stream()
            .map(ModelDTO::from)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets models for a specific provider.
     * 
     * @param provider The provider identifier
     * @return List of models for the provider
     */
    public List<ModelDTO> getModelsByProvider(String provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        
        List<Model> models = modelRegistry.getModelsForProvider(provider);
        log.debug("Found {} models for provider: {}", models.size(), provider);
        
        return models.stream()
            .map(ModelDTO::from)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets a specific model by provider and model ID.
     * 
     * @param provider The provider identifier
     * @param modelId  The model identifier
     * @return The model DTO, or null if not found
     */
    public ModelDTO getModel(String provider, String modelId) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(modelId, "modelId must not be null");
        
        Model model = modelRegistry.find(provider, modelId);
        if (model == null) {
            log.debug("Model not found: provider={}, modelId={}", provider, modelId);
            return null;
        }
        
        return ModelDTO.from(model);
    }
    
    /**
     * Gets all configured provider identifiers.
     * 
     * @return List of provider identifiers
     */
    public List<String> getProviders() {
        return modelRegistry.getProviders();
    }
}
