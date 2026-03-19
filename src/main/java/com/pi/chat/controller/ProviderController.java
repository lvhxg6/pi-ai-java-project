package com.pi.chat.controller;

import com.pi.chat.dto.CreateProviderRequest;
import com.pi.chat.dto.UpdateProviderRequest;
import com.pi.chat.dto.ValidationResult;
import com.pi.chat.model.ProviderConfig;
import com.pi.chat.service.ProviderService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing LLM provider configurations.
 * 
 * <p>Provides endpoints for:
 * <ul>
 *   <li>CRUD operations on provider configurations</li>
 *   <li>API key validation</li>
 * </ul>
 * 
 * <p>Requirements:
 * <ul>
 *   <li>9.1 - RESTful API with JSON request/response</li>
 *   <li>9.5 - Return appropriate HTTP status codes</li>
 *   <li>9.6 - Return descriptive error messages</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/providers")
public class ProviderController {
    
    private final ProviderService providerService;
    
    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }
    
    /**
     * Lists all configured providers.
     * 
     * @return List of all provider configurations
     */
    @GetMapping
    public ResponseEntity<List<ProviderConfig>> listProviders() {
        List<ProviderConfig> providers = providerService.listProviders();
        return ResponseEntity.ok(providers);
    }
    
    /**
     * Gets a provider by ID.
     * 
     * @param id The provider ID
     * @return The provider configuration
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProviderConfig> getProvider(@PathVariable String id) {
        ProviderConfig provider = providerService.getProvider(id);
        return ResponseEntity.ok(provider);
    }
    
    /**
     * Creates a new provider configuration.
     * 
     * @param request The create request
     * @return The created provider configuration with 201 status
     */
    @PostMapping
    public ResponseEntity<ProviderConfig> createProvider(
            @Valid @RequestBody CreateProviderRequest request) {
        ProviderConfig created = providerService.createProvider(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    /**
     * Updates an existing provider configuration.
     * 
     * @param id      The provider ID
     * @param request The update request
     * @return The updated provider configuration
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProviderConfig> updateProvider(
            @PathVariable String id,
            @Valid @RequestBody UpdateProviderRequest request) {
        ProviderConfig updated = providerService.updateProvider(id, request);
        return ResponseEntity.ok(updated);
    }
    
    /**
     * Deletes a provider configuration.
     * 
     * @param id The provider ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProvider(@PathVariable String id) {
        providerService.deleteProvider(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Validates the API key for a provider.
     * 
     * @param id The provider ID
     * @return Validation result
     */
    @PostMapping("/{id}/validate")
    public ResponseEntity<ValidationResult> validateApiKey(@PathVariable String id) {
        ValidationResult result = providerService.validateApiKey(id);
        return ResponseEntity.ok(result);
    }
}
