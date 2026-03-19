package com.pi.chat.service;

import com.pi.ai.core.types.Model;
import com.pi.chat.dto.*;
import com.pi.chat.exception.BrandNotFoundException;
import com.pi.chat.exception.IllegalBrandOperationException;
import com.pi.chat.model.*;
import com.pi.chat.auth.WebAuthStorage;
import com.pi.chat.repository.ProviderConfigRepository;
import com.pi.coding.model.CodingModelRegistry;
import com.pi.coding.model.ProviderModelConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing model brands.
 *
 * <p>Provides CRUD operations for brands, model management,
 * and SDK synchronization via CodingModelRegistry.
 *
 * <p>Requirements:
 * <ul>
 *   <li>2.1-2.8 - Brand configuration management</li>
 *   <li>3.1-3.5 - Model list management</li>
 *   <li>4.1-4.5 - Custom brand support</li>
 *   <li>6.1-6.6 - SDK integration and model registration</li>
 *   <li>7.1-7.4 - Grouped models for chat page</li>
 *   <li>8.5 - Relaxed API key validation</li>
 * </ul>
 */
public class BrandService {

    private static final Logger log = LoggerFactory.getLogger(BrandService.class);
    private static final int MIN_API_KEY_LENGTH = 10;

    private final ProviderConfigRepository repository;
    private final CodingModelRegistry modelRegistry;
    private final WebAuthStorage webAuthStorage;

    public BrandService(ProviderConfigRepository repository,
                        CodingModelRegistry modelRegistry,
                        WebAuthStorage webAuthStorage) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.modelRegistry = modelRegistry; // may be null
        this.webAuthStorage = webAuthStorage; // may be null
    }

    // ========== Brand CRUD (Task 6.1) ==========

    /**
     * Lists all brands: predefined brands merged with saved configs,
     * followed by custom brands. Built-in brands come first.
     */
    public List<BrandView> listBrands() {
        List<ProviderConfig> allConfigs = repository.findAll();
        Map<String, ProviderConfig> configById = allConfigs.stream()
                .collect(Collectors.toMap(ProviderConfig::id, c -> c, (a, b) -> b));

        List<BrandView> result = new ArrayList<>();

        // 1. Predefined brands (built-in first, then extension) in definition order
        for (BrandDefinition def : BrandDefinitions.ALL) {
            ProviderConfig config = configById.remove(def.id());
            result.add(toBrandView(def, config));
        }

        // 2. Custom brands = remaining configs not in predefined list
        List<ProviderConfig> customConfigs = new ArrayList<>(configById.values());
        customConfigs.sort(Comparator.comparing(ProviderConfig::createdAt));
        for (ProviderConfig config : customConfigs) {
            result.add(toBrandView(null, config));
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Gets a single brand by ID.
     *
     * @throws BrandNotFoundException if neither definition nor config exists
     */
    public BrandView getBrand(String brandId) {
        BrandDefinition definition = BrandDefinitions.findById(brandId).orElse(null);
        ProviderConfig config = repository.findById(brandId).orElse(null);

        if (definition == null && config == null) {
            throw new BrandNotFoundException(brandId);
        }
        return toBrandView(definition, config);
    }

    /**
     * Updates a brand's configuration (API key, base URL, enabled).
     */
    public BrandView updateBrand(String brandId, UpdateBrandRequest request) {
        BrandDefinition definition = BrandDefinitions.findById(brandId).orElse(null);
        ProviderConfig existing = repository.findById(brandId).orElse(null);

        // For extension/custom brands, validate baseUrl is not empty
        if (definition == null || !definition.builtin()) {
            String newBaseUrl = request.baseUrl() != null ? request.baseUrl() : (existing != null ? existing.baseUrl() : null);
            if (newBaseUrl == null || newBaseUrl.isBlank()) {
                throw new IllegalArgumentException("Base URL is required for extension/custom brands");
            }
        }

        // Validate API key minimum length if provided
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            if (request.apiKey().length() < MIN_API_KEY_LENGTH) {
                throw new IllegalArgumentException("API key must be at least " + MIN_API_KEY_LENGTH + " characters");
            }
        }

        ProviderConfig config;
        if (existing != null) {
            config = applyUpdates(existing, request, definition);
        } else {
            // Create new config for a predefined brand that has no saved config yet
            config = createConfigForDefinition(definition, request);
        }

        ProviderConfig saved = repository.save(config);
        syncToRegistry(saved, definition);
        return toBrandView(definition, saved);
    }

    /**
     * Creates a custom brand.
     */
    public BrandView createCustomBrand(CreateBrandRequest request) {
        Objects.requireNonNull(request.name(), "Brand name is required");
        if (request.name().isBlank()) {
            throw new IllegalArgumentException("Brand name must not be blank");
        }
        if (request.baseUrl() == null || request.baseUrl().isBlank()) {
            throw new IllegalArgumentException("Base URL is required for custom brands");
        }

        String id = generateBrandId(request.name());

        Instant now = Instant.now();
        ProviderConfig config = new ProviderConfig(
                id,
                id, // type = id for custom brands
                request.name(),
                request.apiKey(),
                request.baseUrl(),
                now,
                now,
                ConnectionStatus.UNKNOWN,
                true,
                List.of(),
                "openai-completions"
        );

        ProviderConfig saved = repository.save(config);
        syncToRegistry(saved, null);
        return toBrandView(null, saved);
    }

    /**
     * Deletes a custom brand. Predefined brands cannot be deleted.
     *
     * @throws IllegalBrandOperationException if brand is predefined
     */
    public void deleteCustomBrand(String brandId) {
        if (BrandDefinitions.isPredefined(brandId)) {
            throw new IllegalBrandOperationException("Cannot delete predefined brand: " + brandId);
        }
        repository.deleteById(brandId);
        removeFromRegistry(brandId);
    }

    /**
     * Toggles the enabled state of a brand.
     */
    public BrandView toggleEnabled(String brandId, boolean enabled) {
        BrandDefinition definition = BrandDefinitions.findById(brandId).orElse(null);
        ProviderConfig existing = repository.findById(brandId).orElse(null);

        ProviderConfig config;
        if (existing != null) {
            config = existing.withEnabled(enabled);
        } else if (definition != null) {
            // Create a minimal config for the predefined brand
            Instant now = Instant.now();
            config = new ProviderConfig(
                    definition.id(),
                    definition.provider(),
                    definition.name(),
                    null,
                    definition.defaultBaseUrl(),
                    now, now,
                    ConnectionStatus.UNKNOWN,
                    enabled,
                    List.of(),
                    definition.apiType()
            );
        } else {
            throw new BrandNotFoundException(brandId);
        }

        ProviderConfig saved = repository.save(config);

        if (enabled) {
            syncToRegistry(saved, definition);
        } else {
            removeFromRegistry(brandId);
        }

        return toBrandView(definition, saved);
    }

    // ========== Model Management (Task 6.2) ==========

    /**
     * Updates the model list for a brand.
     */
    public void updateModels(String brandId, List<ModelEntry> models) {
        ProviderConfig existing = repository.findById(brandId)
                .orElseThrow(() -> new BrandNotFoundException(brandId));

        ProviderConfig updated = existing.withModels(models != null ? models : List.of());
        ProviderConfig saved = repository.save(updated);

        BrandDefinition definition = BrandDefinitions.findById(brandId).orElse(null);
        syncToRegistry(saved, definition);
    }

    /**
     * Returns models grouped by brand for the chat page model selector.
     * Only includes enabled brands with a configured API key.
     */
    public List<GroupedModels> getGroupedModels() {
        List<BrandView> brands = listBrands();
        List<GroupedModels> result = new ArrayList<>();

        for (BrandView brand : brands) {
            if (!brand.enabled() || !brand.hasApiKey()) {
                continue;
            }

            List<ModelDTO> modelDTOs = buildModelDTOs(brand);
            if (!modelDTOs.isEmpty()) {
                result.add(new GroupedModels(brand.id(), brand.name(), modelDTOs));
            }
        }

        return Collections.unmodifiableList(result);
    }

    // ========== SDK Sync (Task 6.3) ==========

    /**
     * Syncs a brand's configuration to the CodingModelRegistry.
     * Built-in brands pass API key via WebAuthStorage.
     * Extension/custom brands register as dynamic providers.
     */
    void syncToRegistry(ProviderConfig config, BrandDefinition definition) {
        if (modelRegistry == null) {
            log.warn("CodingModelRegistry is null, skipping sync for brand: {}", config.id());
            return;
        }

        try {
            if (definition != null && definition.builtin()) {
                // Built-in brand: pass API key via WebAuthStorage
                if (webAuthStorage != null && config.apiKey() != null && !config.apiKey().isBlank()) {
                    webAuthStorage.setRuntimeApiKey(definition.provider(), config.apiKey());
                    log.debug("Set runtime API key for built-in brand: {} (provider: {})",
                            config.id(), definition.provider());
                }
            } else {
                // Extension/custom brand: register as dynamic provider
                com.pi.coding.model.ProviderConfig sdkConfig = new com.pi.coding.model.ProviderConfig(
                        config.id(),
                        config.name(),
                        config.baseUrl(),
                        Map.of(),
                        config.apiKey(),
                        buildSdkModels(config)
                );
                modelRegistry.registerProvider(sdkConfig);
                log.debug("Registered dynamic provider for brand: {}", config.id());
            }
        } catch (Exception e) {
            log.warn("Failed to sync brand to registry: {}", config.id(), e);
        }
    }

    /**
     * Removes a brand from the CodingModelRegistry.
     */
    void removeFromRegistry(String brandId) {
        if (modelRegistry == null) {
            return;
        }
        try {
            modelRegistry.unregisterProvider(brandId);
            log.debug("Unregistered provider for brand: {}", brandId);
        } catch (Exception e) {
            log.warn("Failed to unregister brand from registry: {}", brandId, e);
        }
    }

    /**
     * Syncs all enabled brands with API keys on application startup.
     */
    public void syncAllOnStartup() {
        List<ProviderConfig> allConfigs = repository.findAll();
        int synced = 0;

        for (ProviderConfig config : allConfigs) {
            if (!config.enabled()) {
                continue;
            }
            if (config.apiKey() == null || config.apiKey().isBlank()) {
                continue;
            }

            BrandDefinition definition = BrandDefinitions.findById(config.id()).orElse(null);
            syncToRegistry(config, definition);
            synced++;
        }

        log.info("Startup sync complete: {}/{} brands synced to registry", synced, allConfigs.size());
    }

    // ========== Private Helpers ==========

    private BrandView toBrandView(BrandDefinition definition, ProviderConfig config) {
        String id = definition != null ? definition.id() : config.id();
        String name = definition != null ? definition.name() : config.name();
        String provider = definition != null ? definition.provider() : config.type();
        String apiType = definition != null ? definition.apiType() : config.apiType();
        String baseUrl = config != null && config.baseUrl() != null
                ? config.baseUrl()
                : (definition != null ? definition.defaultBaseUrl() : null);
        String defaultBaseUrl = definition != null ? definition.defaultBaseUrl() : null;
        boolean enabled = config != null ? config.enabled() : true;
        boolean builtin = definition != null && definition.builtin();
        boolean deletable = definition == null; // only custom brands are deletable
        boolean hasApiKey = config != null && config.apiKey() != null && !config.apiKey().isBlank();
        String maskedApiKey = hasApiKey ? config.maskedApiKey() : null;
        List<ModelEntry> models = config != null && !config.models().isEmpty()
                ? config.models()
                : (definition != null ? definition.defaultModels() : List.of());
        Instant createdAt = config != null ? config.createdAt() : null;
        Instant updatedAt = config != null ? config.updatedAt() : null;

        return new BrandView(id, name, provider, apiType, baseUrl, defaultBaseUrl,
                enabled, builtin, deletable, hasApiKey, maskedApiKey, models, createdAt, updatedAt);
    }

    private ProviderConfig applyUpdates(ProviderConfig existing, UpdateBrandRequest request,
                                        BrandDefinition definition) {
        String newApiKey = existing.apiKey();
        if (request.apiKey() != null && !request.apiKey().isBlank()
                && request.apiKey().length() >= MIN_API_KEY_LENGTH) {
            newApiKey = request.apiKey();
        }

        String newBaseUrl = request.baseUrl() != null ? request.baseUrl() : existing.baseUrl();
        boolean newEnabled = request.enabled() != null ? request.enabled() : existing.enabled();

        return new ProviderConfig(
                existing.id(),
                existing.type(),
                existing.name(),
                newApiKey,
                newBaseUrl,
                existing.createdAt(),
                Instant.now(),
                existing.status(),
                newEnabled,
                existing.models(),
                existing.apiType()
        );
    }

    private ProviderConfig createConfigForDefinition(BrandDefinition definition, UpdateBrandRequest request) {
        if (definition == null) {
            throw new BrandNotFoundException("unknown");
        }
        Instant now = Instant.now();
        String apiKey = (request.apiKey() != null && request.apiKey().length() >= MIN_API_KEY_LENGTH)
                ? request.apiKey() : null;
        String baseUrl = request.baseUrl() != null ? request.baseUrl() : definition.defaultBaseUrl();
        boolean enabled = request.enabled() != null ? request.enabled() : true;

        return new ProviderConfig(
                definition.id(),
                definition.provider(),
                definition.name(),
                apiKey,
                baseUrl,
                now, now,
                ConnectionStatus.UNKNOWN,
                enabled,
                List.of(),
                definition.apiType()
        );
    }

    private String generateBrandId(String name) {
        // Convert to kebab-case
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isEmpty()) {
            base = "custom";
        }

        String id = base;
        int counter = 1;
        while (repository.existsById(id) || BrandDefinitions.isPredefined(id)) {
            id = base + "-" + counter;
            counter++;
        }
        return id;
    }

    private List<ProviderModelConfig> buildSdkModels(ProviderConfig config) {
        return config.models().stream()
                .map(m -> new ProviderModelConfig(
                        m.id(), m.name(), null, null, null,
                        m.contextWindow(), m.maxTokens()))
                .toList();
    }

    private List<ModelDTO> buildModelDTOs(BrandView brand) {
        // For built-in brands: try to get models from CodingModelRegistry
        if (brand.builtin() && modelRegistry != null) {
            List<Model> registryModels = modelRegistry.getModelsForProvider(brand.provider());
            if (registryModels != null && !registryModels.isEmpty()) {
                return registryModels.stream()
                        .map(ModelDTO::from)
                        .toList();
            }
        }

        // For extension/custom brands or fallback: use saved config / definition models
        return brand.models().stream()
                .map(m -> new ModelDTO(
                        m.id(), m.name(), brand.provider(),
                        m.contextWindow(), m.maxTokens(),
                        false, List.of("text"), null))
                .toList();
    }
}
