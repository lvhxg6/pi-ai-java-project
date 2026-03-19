package com.pi.chat.integration;

import com.pi.chat.dto.CreateProviderRequest;
import com.pi.chat.dto.CreateSessionRequest;
import com.pi.chat.dto.SessionInfo;
import com.pi.chat.dto.UpdateProviderRequest;
import com.pi.chat.model.ProviderConfig;
import com.pi.chat.repository.ProviderConfigRepository;
import com.pi.chat.service.ProviderService;
import com.pi.chat.service.SessionService;
import com.pi.chat.session.SessionManagerFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration tests for AI Chat Web application.
 * 
 * <p>Tests complete workflows across multiple services.
 */
class EndToEndIntegrationTest {
    
    @TempDir
    Path tempDir;
    
    private ProviderConfigRepository providerRepository;
    private ProviderService providerService;
    private SessionManagerFactory sessionFactory;
    private SessionService sessionService;
    
    @BeforeEach
    void setUp() {
        Path providersFile = tempDir.resolve("providers.json");
        providerRepository = new ProviderConfigRepository(providersFile, "test-secret-key");
        providerService = new ProviderService(providerRepository, null);
        
        sessionFactory = new SessionManagerFactory(tempDir, tempDir.toString());
        sessionService = new SessionService(sessionFactory);
    }
    
    @Nested
    @DisplayName("Provider Configuration Flow")
    class ProviderConfigurationFlow {
        
        @Test
        @DisplayName("Complete provider setup workflow")
        void completeProviderSetupWorkflow() {
            // 1. Create a provider
            CreateProviderRequest createRequest = new CreateProviderRequest(
                ProviderConfig.TYPE_ANTHROPIC,
                "My Anthropic",
                "sk-ant-test-key-12345",
                null
            );
            ProviderConfig created = providerService.createProvider(createRequest);
            
            assertThat(created.id()).isNotNull();
            assertThat(created.name()).isEqualTo("My Anthropic");
            assertThat(created.type()).isEqualTo(ProviderConfig.TYPE_ANTHROPIC);
            
            // 2. List providers
            List<ProviderConfig> providers = providerService.listProviders();
            assertThat(providers).hasSize(1);
            assertThat(providers.get(0).id()).isEqualTo(created.id());
            
            // 3. Update provider
            UpdateProviderRequest updateRequest = new UpdateProviderRequest("Updated Anthropic", null, null);
            ProviderConfig updated = providerService.updateProvider(created.id(), updateRequest);
            assertThat(updated.name()).isEqualTo("Updated Anthropic");
            
            // 4. Delete provider
            providerService.deleteProvider(created.id());
            assertThat(providerService.listProviders()).isEmpty();
        }
        
        @Test
        @DisplayName("Multiple providers can coexist")
        void multipleProvidersCanCoexist() {
            // Create multiple providers
            ProviderConfig anthropic = providerService.createProvider(
                new CreateProviderRequest(ProviderConfig.TYPE_ANTHROPIC, "Anthropic", "sk-ant-key", null)
            );
            ProviderConfig openai = providerService.createProvider(
                new CreateProviderRequest(ProviderConfig.TYPE_OPENAI, "OpenAI", "sk-openai-key", null)
            );
            
            List<ProviderConfig> providers = providerService.listProviders();
            assertThat(providers).hasSize(2);
            assertThat(providers).extracting(ProviderConfig::type)
                .containsExactlyInAnyOrder(ProviderConfig.TYPE_ANTHROPIC, ProviderConfig.TYPE_OPENAI);
        }
    }
    
    @Nested
    @DisplayName("Session Management Flow")
    class SessionManagementFlow {
        
        @Test
        @DisplayName("Complete session lifecycle")
        void completeSessionLifecycle() {
            // 1. Create session
            CreateSessionRequest request = new CreateSessionRequest("Test Chat", null, null);
            SessionInfo created = sessionService.createSession(request);
            
            assertThat(created.id()).isNotNull();
            assertThat(created.name()).isEqualTo("Test Chat");
            
            // 2. Get session info
            SessionInfo retrieved = sessionService.getSessionInfo(created.id());
            assertThat(retrieved.id()).isEqualTo(created.id());
            
            // 3. Rename session
            SessionInfo renamed = sessionService.renameSession(created.id(), "Renamed Chat");
            assertThat(renamed.name()).isEqualTo("Renamed Chat");
        }
        
        @Test
        @DisplayName("Multiple sessions can be managed")
        void multipleSessionsCanBeManaged() {
            // Create multiple sessions
            SessionInfo session1 = sessionService.createSession(
                new CreateSessionRequest("Chat 1", null, null)
            );
            SessionInfo session2 = sessionService.createSession(
                new CreateSessionRequest("Chat 2", null, null)
            );
            SessionInfo session3 = sessionService.createSession(
                new CreateSessionRequest("Chat 3", null, null)
            );
            
            // All sessions should be accessible
            assertThat(sessionService.getSessionInfo(session1.id()).name()).isEqualTo("Chat 1");
            assertThat(sessionService.getSessionInfo(session2.id()).name()).isEqualTo("Chat 2");
            assertThat(sessionService.getSessionInfo(session3.id()).name()).isEqualTo("Chat 3");
        }
    }
    
    @Nested
    @DisplayName("Combined Provider and Session Flow")
    class CombinedFlow {
        
        @Test
        @DisplayName("Session can be created with provider context")
        void sessionWithProviderContext() {
            // Setup provider with valid API key format
            ProviderConfig provider = providerService.createProvider(
                new CreateProviderRequest(ProviderConfig.TYPE_ANTHROPIC, "Test Provider", "sk-ant-api03-valid-key-12345", null)
            );
            
            // Create session
            CreateSessionRequest request = new CreateSessionRequest(
                "Chat with Provider", 
                provider.type(), 
                "claude-3-opus"
            );
            SessionInfo session = sessionService.createSession(request);
            
            assertThat(session.name()).isEqualTo("Chat with Provider");
            assertThat(session.id()).isNotNull();
        }
    }
}
