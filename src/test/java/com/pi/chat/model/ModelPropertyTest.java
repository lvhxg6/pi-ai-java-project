package com.pi.chat.model;

import com.pi.chat.dto.ModelDTO;
import com.pi.chat.service.ModelService;
import com.pi.coding.auth.AuthStorage;
import com.pi.coding.model.CodingModelRegistry;

import net.jqwik.api.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for Model module.
 * 
 * <p>Tests the following correctness properties:
 * <ul>
 *   <li>Property 4: Model Availability Reflects Provider Configuration</li>
 *   <li>Property 5: Model Information Completeness</li>
 * </ul>
 */
class ModelPropertyTest {
    
    private CodingModelRegistry modelRegistry;
    private ModelService modelService;
    
    @BeforeEach
    void setUp() {
        // Create an in-memory AuthStorage for testing
        AuthStorage authStorage = AuthStorage.inMemory();
        
        // Set test API keys for providers
        authStorage.setRuntimeApiKey("anthropic", "sk-ant-test-key-for-testing");
        authStorage.setRuntimeApiKey("openai", "sk-test-key-for-testing");
        
        modelRegistry = new CodingModelRegistry(authStorage);
        modelService = new ModelService(modelRegistry);
    }
    
    // ========== Property 4: Model Availability Reflects Provider Configuration ==========
    
    @Test
    @Tag("Feature: ai-chat-web, Property 4: Model Availability Reflects Provider Configuration")
    void availableModelsReflectsBuiltinProviders() {
        // Get available models
        List<ModelDTO> models = modelService.getAvailableModels();
        
        // Should have models from built-in providers
        assertThat(models).isNotEmpty();
        
        // Get providers
        List<String> providers = modelService.getProviders();
        
        // Each model should belong to a known provider
        for (ModelDTO model : models) {
            assertThat(providers).contains(model.provider());
        }
    }
    
    @Test
    @Tag("Feature: ai-chat-web, Property 4: Model Availability Reflects Provider Configuration")
    void modelsFilteredByProviderMatchProvider() {
        // Get all providers
        List<String> providers = modelService.getProviders();
        
        for (String provider : providers) {
            List<ModelDTO> models = modelService.getModelsByProvider(provider);
            
            // All models should belong to the requested provider
            for (ModelDTO model : models) {
                assertThat(model.provider()).isEqualTo(provider);
            }
        }
    }
    
    @Property(tries = 50)
    @Tag("Feature: ai-chat-web, Property 4: Model Availability Reflects Provider Configuration")
    void modelLookupReturnsCorrectModel(@ForAll("existingModels") ModelDTO existingModel) {
        // Look up the model
        ModelDTO found = modelService.getModel(existingModel.provider(), existingModel.id());
        
        // Should find the same model
        assertThat(found).isNotNull();
        assertThat(found.id()).isEqualTo(existingModel.id());
        assertThat(found.provider()).isEqualTo(existingModel.provider());
    }
    
    // ========== Property 5: Model Information Completeness ==========
    
    @Property(tries = 100)
    @Tag("Feature: ai-chat-web, Property 5: Model Information Completeness")
    void modelHasRequiredFields(@ForAll("existingModels") ModelDTO model) {
        // All required fields should be non-null
        assertThat(model.id()).isNotNull().isNotBlank();
        assertThat(model.name()).isNotNull().isNotBlank();
        assertThat(model.provider()).isNotNull().isNotBlank();
        
        // Context window should be positive
        assertThat(model.contextWindow()).isGreaterThan(0);
        
        // Max tokens should be positive
        assertThat(model.maxTokens()).isGreaterThan(0);
    }
    
    @Property(tries = 100)
    @Tag("Feature: ai-chat-web, Property 5: Model Information Completeness")
    void modelCostIsValidWhenPresent(@ForAll("existingModels") ModelDTO model) {
        if (model.cost() != null) {
            // Cost values should be non-negative
            assertThat(model.cost().input()).isGreaterThanOrEqualTo(0);
            assertThat(model.cost().output()).isGreaterThanOrEqualTo(0);
            assertThat(model.cost().cacheRead()).isGreaterThanOrEqualTo(0);
            assertThat(model.cost().cacheWrite()).isGreaterThanOrEqualTo(0);
        }
    }
    
    @Test
    @Tag("Feature: ai-chat-web, Property 5: Model Information Completeness")
    void allModelsHaveValidContextWindow() {
        List<ModelDTO> models = modelService.getAvailableModels();
        
        for (ModelDTO model : models) {
            // Context window should be reasonable (at least 1K, at most 10M)
            assertThat(model.contextWindow())
                .as("Model %s context window", model.id())
                .isBetween(1000, 10_000_000);
        }
    }
    
    @Test
    @Tag("Feature: ai-chat-web, Property 5: Model Information Completeness")
    void allModelsHaveValidMaxTokens() {
        List<ModelDTO> models = modelService.getAvailableModels();
        
        for (ModelDTO model : models) {
            // Max tokens should be reasonable (at least 100, at most context window)
            assertThat(model.maxTokens())
                .as("Model %s max tokens", model.id())
                .isBetween(100, model.contextWindow());
        }
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    Arbitrary<ModelDTO> existingModels() {
        List<ModelDTO> models = modelService.getAvailableModels();
        if (models.isEmpty()) {
            // Return a dummy model if no models available
            return Arbitraries.just(new ModelDTO(
                "test-model",
                "Test Model",
                "test-provider",
                128000,
                4096,
                false,
                List.of("text"),
                null
            ));
        }
        return Arbitraries.of(models);
    }
}
