package com.pi.chat.config;

import com.pi.chat.auth.WebAuthStorage;
import com.pi.chat.repository.ProviderConfigRepository;
import com.pi.chat.service.BrandService;
import com.pi.chat.service.ChatService;
import com.pi.chat.service.ModelService;
import com.pi.chat.service.ProviderService;
import com.pi.chat.service.SessionService;
import com.pi.chat.session.SessionManagerFactory;
import com.pi.coding.model.CodingModelRegistry;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * Main configuration class for AI Chat Web application.
 * 
 * <p>Responsibilities:
 * <ul>
 *   <li>Enable configuration properties binding</li>
 *   <li>Configure async task executor for SSE streaming</li>
 *   <li>Initialize required directories</li>
 * </ul>
 * 
 * <p>Requirements:
 * <ul>
 *   <li>7.3 - Session storage directory setup</li>
 *   <li>10.1 - Provider configuration file path setup</li>
 * </ul>
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(AiChatProperties.class)
public class AiChatConfig {
    
    private final AiChatProperties properties;
    
    public AiChatConfig(AiChatProperties properties) {
        this.properties = properties;
    }
    
    /**
     * Initialize required directories on application startup.
     */
    @PostConstruct
    public void initDirectories() throws IOException {
        // Create session storage directory
        Path sessionsDir = Path.of(properties.session().storageDir());
        if (!Files.exists(sessionsDir)) {
            Files.createDirectories(sessionsDir);
        }
        
        // Create provider config directory
        Path providerConfigFile = Path.of(properties.provider().configFile());
        Path providerConfigDir = providerConfigFile.getParent();
        if (providerConfigDir != null && !Files.exists(providerConfigDir)) {
            Files.createDirectories(providerConfigDir);
        }
    }
    
    /**
     * Async task executor for SSE streaming and background operations.
     * 
     * <p>This executor is used for:
     * <ul>
     *   <li>SSE event emission</li>
     *   <li>LLM streaming response handling</li>
     *   <li>Background session persistence</li>
     * </ul>
     */
    @Bean(name = "chatAsyncExecutor")
    public Executor chatAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("chat-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
    
    /**
     * Provides the session storage directory path.
     */
    @Bean
    public Path sessionStorageDir() {
        return Path.of(properties.session().storageDir());
    }
    
    /**
     * Provides the provider configuration file path.
     */
    @Bean
    public Path providerConfigFile() {
        return Path.of(properties.provider().configFile());
    }
    
    /**
     * Creates the ProviderConfigRepository bean.
     * 
     * <p>The repository manages provider configurations with encrypted API keys.
     */
    @Bean
    public ProviderConfigRepository providerConfigRepository() {
        String encryptionKey = properties.provider().encryptionKey();
        if (encryptionKey == null || encryptionKey.isBlank()) {
            // Use a default key for development; in production, this should be configured
            encryptionKey = "default-encryption-key-32chars!";
        }
        return new ProviderConfigRepository(providerConfigFile(), encryptionKey);
    }
    
    /**
     * Creates the WebAuthStorage bean.
     * 
     * <p>Provides API key resolution from the ProviderConfigRepository.
     */
    @Bean
    public WebAuthStorage webAuthStorage(ProviderConfigRepository repository) {
        return new WebAuthStorage(repository);
    }
    
    /**
     * Creates the CodingModelRegistry bean.
     * 
     * <p>Manages available models from configured providers.
     */
    @Bean
    public CodingModelRegistry codingModelRegistry(WebAuthStorage webAuthStorage) {
        return new CodingModelRegistry(webAuthStorage.getAuthStorage());
    }
    
    /**
     * Creates the ProviderService bean.
     * 
     * <p>Manages provider CRUD operations and syncs with the model registry.
     */
    @Bean
    public ProviderService providerService(ProviderConfigRepository repository, 
                                           CodingModelRegistry modelRegistry) {
        return new ProviderService(repository, modelRegistry);
    }
    
    /**
     * Creates the ModelService bean.
     * 
     * <p>Provides model information from the CodingModelRegistry.
     */
    @Bean
    public ModelService modelService(CodingModelRegistry modelRegistry) {
        return new ModelService(modelRegistry);
    }
    
    /**
     * Creates the BrandService bean.
     *
     * <p>Manages brand CRUD operations, model management, and SDK synchronization.
     * Calls syncAllOnStartup() to sync existing brand configs to the registry.
     *
     * <p>Requirements:
     * <ul>
     *   <li>6.5 - Sync brands to CodingModelRegistry on startup</li>
     * </ul>
     */
    @Bean
    public BrandService brandService(ProviderConfigRepository repository,
                                     CodingModelRegistry modelRegistry,
                                     WebAuthStorage webAuthStorage) {
        BrandService service = new BrandService(repository, modelRegistry, webAuthStorage);
        service.syncAllOnStartup();
        return service;
    }
    
    /**
     * Creates the SessionManagerFactory bean.
     * 
     * <p>Factory for creating and managing SessionManager instances.
     */
    @Bean
    public SessionManagerFactory sessionManagerFactory() {
        Path sessionsDir = Path.of(properties.session().storageDir());
        String cwd = System.getProperty("user.dir");
        return new SessionManagerFactory(sessionsDir, cwd);
    }
    
    /**
     * Creates the SessionService bean.
     * 
     * <p>Manages session CRUD operations.
     */
    @Bean
    public SessionService sessionService(SessionManagerFactory factory) {
        return new SessionService(factory);
    }
    
    /**
     * Creates the ChatService bean.
     * 
     * <p>Handles chat operations and message streaming.
     */
    @Bean
    public ChatService chatService(SessionService sessionService, ModelService modelService,
                                   CodingModelRegistry modelRegistry, BrandService brandService) {
        return new ChatService(sessionService, modelService, modelRegistry, brandService);
    }
}
