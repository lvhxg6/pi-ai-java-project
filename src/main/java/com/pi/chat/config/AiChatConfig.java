package com.pi.chat.config;

import com.pi.ai.provider.builtin.BuiltInProviders;
import com.pi.chat.auth.WebAuthStorage;
import com.pi.chat.repository.ProviderConfigRepository;
import com.pi.chat.service.BrandService;
import com.pi.chat.service.ChatService;
import com.pi.chat.service.ModelService;
import com.pi.chat.service.ProviderService;
import com.pi.chat.service.SessionService;
import com.pi.chat.service.SkillsService;
import com.pi.chat.service.ToolRegistry;
import com.pi.chat.session.SessionManagerFactory;
import com.pi.coding.model.CodingModelRegistry;
import com.pi.coding.resource.DefaultResourceLoader;
import com.pi.coding.resource.ResourceLoader;
import com.pi.coding.resource.ResourceLoaderConfig;
import com.pi.coding.settings.SettingsManager;

import org.springframework.beans.factory.annotation.Autowired;
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
 *   <li>Register built-in API providers</li>
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
     * Initialize required directories and register API providers on application startup.
     */
    @PostConstruct
    public void initDirectories() throws IOException {
        // Register built-in API providers (Anthropic, OpenAI, etc.)
        BuiltInProviders.registerBuiltInApiProviders();
        
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
     * Creates the SettingsManager bean.
     * 
     * <p>Manages settings from global (~/.pi/settings.json) and project (.pi/settings.json).
     * 
     * <p>Requirements:
     * <ul>
     *   <li>6.1 - SettingsManager Bean for AgentSession configuration</li>
     * </ul>
     */
    @Bean
    public SettingsManager settingsManager() {
        String cwd = System.getProperty("user.dir");
        return SettingsManager.create(cwd, ".pi");
    }
    
    /**
     * Creates the ResourceLoader bean (optional).
     * 
     * <p>Loads skills, prompts, and context files for AgentSession.
     * Supports hot-reload of resources when files change.
     * 
     * <p>Requirements:
     * <ul>
     *   <li>6.2 - ResourceLoader Bean for Skills hot-reload</li>
     *   <li>6.5 - ResourceLoader is optional dependency</li>
     * </ul>
     */
    @Bean
    public ResourceLoader resourceLoader(SettingsManager settingsManager) {
        String cwd = System.getProperty("user.dir");
        String agentDir = System.getProperty("user.home") + "/.kiro";
        ResourceLoaderConfig config = new ResourceLoaderConfig(cwd, agentDir, settingsManager);
        DefaultResourceLoader loader = new DefaultResourceLoader(config);
        // Start watching for file changes
        loader.startWatching();
        return loader;
    }
    
    /**
     * Creates the ToolRegistry Bean.
     * 
     * <p>Manages available AgentTool instances for tool calling.
     * Registers default tools (bash, read, edit) on initialization.
     * 
     * <p>Requirements:
     * <ul>
     *   <li>2.1 - ToolRegistry maintains a registry of available AgentTool instances</li>
     *   <li>2.2 - ToolRegistry registers at least the BashTool on initialization</li>
     * </ul>
     */
    @Bean
    public ToolRegistry toolRegistry() {
        String cwd = System.getProperty("user.dir");
        return new ToolRegistry(cwd);
    }
    
    /**
     * Creates the ChatService bean.
     * 
     * <p>Handles chat operations and message streaming.
     * 
     * <p>Requirements:
     * <ul>
     *   <li>6.3 - ChatService Bean with SettingsManager dependency</li>
     *   <li>6.4 - ChatService Bean with ResourceLoader dependency (optional)</li>
     *   <li>2.3 - ChatService passes registered tools to the Agent via agent.setTools()</li>
     *   <li>2.4 - ChatService updates the Agent's tool list when a new tool is registered</li>
     * </ul>
     */
    @Bean
    public ChatService chatService(SessionService sessionService, ModelService modelService,
                                   CodingModelRegistry modelRegistry, BrandService brandService,
                                   SettingsManager settingsManager,
                                   @Autowired(required = false) ResourceLoader resourceLoader,
                                   ToolRegistry toolRegistry) {
        return new ChatService(sessionService, modelService, modelRegistry, brandService,
                               settingsManager, resourceLoader, toolRegistry);
    }
    
    /**
     * Creates the SkillsService bean.
     * 
     * <p>Manages Skills CRUD operations and file I/O.
     * 
     * <p>Requirements:
     * <ul>
     *   <li>7.1 - SkillsService Bean for Skills management</li>
     * </ul>
     */
    @Bean
    public SkillsService skillsService(ResourceLoader resourceLoader) {
        String cwd = System.getProperty("user.dir");
        String agentDir = System.getProperty("user.home") + "/.kiro";
        return new SkillsService(resourceLoader, cwd, agentDir);
    }
}
