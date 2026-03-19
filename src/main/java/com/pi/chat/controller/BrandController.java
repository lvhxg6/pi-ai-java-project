package com.pi.chat.controller;

import com.pi.chat.dto.*;
import com.pi.chat.model.ModelEntry;
import com.pi.chat.service.BrandService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing model brands.
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>CRUD operations on brand configurations</li>
 *   <li>Model list management per brand</li>
 * </ul>
 *
 * <p>Requirements:
 * <ul>
 *   <li>9.1 - GET /api/brands - list all brands</li>
 *   <li>9.2 - GET /api/brands/{brandId} - get brand detail</li>
 *   <li>9.3 - PUT /api/brands/{brandId} - update brand config</li>
 *   <li>9.4 - POST /api/brands - create custom brand</li>
 *   <li>9.5 - DELETE /api/brands/{brandId} - delete custom brand</li>
 *   <li>9.6 - PUT /api/brands/{brandId}/models - update model list</li>
 *   <li>9.8 - 403 on deleting predefined brands</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/brands")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    /**
     * Lists all brands (predefined + custom).
     *
     * @return List of all brand views
     */
    @GetMapping
    public List<BrandView> listBrands() {
        return brandService.listBrands();
    }

    /**
     * Gets a single brand by ID.
     *
     * @param brandId The brand identifier
     * @return The brand view
     */
    @GetMapping("/{brandId}")
    public BrandView getBrand(@PathVariable String brandId) {
        return brandService.getBrand(brandId);
    }

    /**
     * Updates a brand's configuration (API Key, Base URL, enabled).
     *
     * @param brandId The brand identifier
     * @param request The update request
     * @return The updated brand view
     */
    @PutMapping("/{brandId}")
    public BrandView updateBrand(@PathVariable String brandId, @RequestBody UpdateBrandRequest request) {
        return brandService.updateBrand(brandId, request);
    }

    /**
     * Creates a custom brand.
     *
     * @param request The create request
     * @return The created brand view
     */
    @PostMapping
    public BrandView createCustomBrand(@RequestBody CreateBrandRequest request) {
        return brandService.createCustomBrand(request);
    }

    /**
     * Deletes a custom brand. Returns 403 for predefined brands.
     *
     * @param brandId The brand identifier
     * @return 204 No Content on success
     */
    @DeleteMapping("/{brandId}")
    public ResponseEntity<Void> deleteCustomBrand(@PathVariable String brandId) {
        brandService.deleteCustomBrand(brandId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates the model list for a brand.
     *
     * @param brandId The brand identifier
     * @param request The update models request
     * @return 200 OK on success
     */
    @PutMapping("/{brandId}/models")
    public ResponseEntity<Void> updateModels(@PathVariable String brandId, @RequestBody UpdateModelsRequest request) {
        brandService.updateModels(brandId, request.models());
        return ResponseEntity.ok().build();
    }
}
