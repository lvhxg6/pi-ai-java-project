package com.pi.chat.controller;

import com.pi.chat.dto.GroupedModels;
import com.pi.chat.dto.ModelDTO;
import com.pi.chat.service.BrandService;
import com.pi.chat.service.ModelService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing LLM models.
 * 
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Listing all available models</li>
 *   <li>Listing models by provider</li>
 * </ul>
 * 
 * <p>Requirements:
 * <ul>
 *   <li>9.4 - Model listing endpoints</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {
    
    private final ModelService modelService;
    private final BrandService brandService;
    
    public ModelController(ModelService modelService, BrandService brandService) {
        this.modelService = modelService;
        this.brandService = brandService;
    }
    
    /**
     * Lists all available models. When {@code grouped=true}, returns models
     * grouped by brand for the chat page model selector.
     * 
     * @param grouped Whether to return models grouped by brand
     * @return List of models (flat or grouped)
     */
    @GetMapping
    public ResponseEntity<?> listModels(
            @RequestParam(required = false, defaultValue = "false") boolean grouped) {
        if (grouped) {
            List<GroupedModels> groupedModels = brandService.getGroupedModels();
            return ResponseEntity.ok(groupedModels);
        }
        List<ModelDTO> models = modelService.getAvailableModels();
        return ResponseEntity.ok(models);
    }
    
    /**
     * Lists models for a specific provider.
     * 
     * @param providerId The provider identifier
     * @return List of models for the provider
     */
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<ModelDTO>> listModelsByProvider(@PathVariable String providerId) {
        List<ModelDTO> models = modelService.getModelsByProvider(providerId);
        return ResponseEntity.ok(models);
    }
    
    /**
     * Gets a specific model by provider and model ID.
     * 
     * @param providerId The provider identifier
     * @param modelId    The model identifier
     * @return The model, or 404 if not found
     */
    @GetMapping("/provider/{providerId}/{modelId}")
    public ResponseEntity<ModelDTO> getModel(
            @PathVariable String providerId,
            @PathVariable String modelId) {
        ModelDTO model = modelService.getModel(providerId, modelId);
        if (model == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(model);
    }
    
    /**
     * Lists all configured provider identifiers.
     * 
     * @return List of provider identifiers
     */
    @GetMapping("/providers")
    public ResponseEntity<List<String>> listProviders() {
        List<String> providers = modelService.getProviders();
        return ResponseEntity.ok(providers);
    }
}
