package com.pi.chat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ProviderConfig record.
 * 
 * <p>Validates:
 * <ul>
 *   <li>Requirement 1.2 - Provider configuration structure</li>
 *   <li>Requirement 1.3 - Supported provider types</li>
 * </ul>
 */
class ProviderConfigTest {
    
    @Nested
    @DisplayName("Record construction")
    class ConstructionTests {
        
        @Test
        @DisplayName("should create valid provider config")
        void shouldCreateValidConfig() {
            Instant now = Instant.now();
            
            ProviderConfig config = new ProviderConfig(
                "test-id",
                "anthropic",
                "Test Provider",
                "sk-test-key",
                "https://api.example.com",
                now,
                now,
                ConnectionStatus.UNKNOWN
            );
            
            assertThat(config.id()).isEqualTo("test-id");
            assertThat(config.type()).isEqualTo("anthropic");
            assertThat(config.name()).isEqualTo("Test Provider");
            assertThat(config.apiKey()).isEqualTo("sk-test-key");
            assertThat(config.baseUrl()).isEqualTo("https://api.example.com");
            assertThat(config.createdAt()).isEqualTo(now);
            assertThat(config.updatedAt()).isEqualTo(now);
            assertThat(config.status()).isEqualTo(ConnectionStatus.UNKNOWN);
        }
        
        @Test
        @DisplayName("should allow null apiKey")
        void shouldAllowNullApiKey() {
            Instant now = Instant.now();
            
            ProviderConfig config = new ProviderConfig(
                "test-id",
                "anthropic",
                "Test",
                null,
                null,
                now,
                now,
                ConnectionStatus.UNKNOWN
            );
            
            assertThat(config.apiKey()).isNull();
        }
        
        @Test
        @DisplayName("should allow null baseUrl")
        void shouldAllowNullBaseUrl() {
            Instant now = Instant.now();
            
            ProviderConfig config = new ProviderConfig(
                "test-id",
                "anthropic",
                "Test",
                "key",
                null,
                now,
                now,
                ConnectionStatus.UNKNOWN
            );
            
            assertThat(config.baseUrl()).isNull();
        }
        
        @Test
        @DisplayName("should throw for null id")
        void shouldThrowForNullId() {
            Instant now = Instant.now();
            
            assertThatThrownBy(() -> new ProviderConfig(
                null, "anthropic", "Test", "key", null, now, now, ConnectionStatus.UNKNOWN
            )).isInstanceOf(NullPointerException.class);
        }
        
        @Test
        @DisplayName("should throw for blank id")
        void shouldThrowForBlankId() {
            Instant now = Instant.now();
            
            assertThatThrownBy(() -> new ProviderConfig(
                "  ", "anthropic", "Test", "key", null, now, now, ConnectionStatus.UNKNOWN
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("id must not be blank");
        }
        
        @Test
        @DisplayName("should throw for null type")
        void shouldThrowForNullType() {
            Instant now = Instant.now();
            
            assertThatThrownBy(() -> new ProviderConfig(
                "id", null, "Test", "key", null, now, now, ConnectionStatus.UNKNOWN
            )).isInstanceOf(NullPointerException.class);
        }
        
        @Test
        @DisplayName("should throw for null name")
        void shouldThrowForNullName() {
            Instant now = Instant.now();
            
            assertThatThrownBy(() -> new ProviderConfig(
                "id", "anthropic", null, "key", null, now, now, ConnectionStatus.UNKNOWN
            )).isInstanceOf(NullPointerException.class);
        }
    }
    
    @Nested
    @DisplayName("Builder")
    class BuilderTests {
        
        @Test
        @DisplayName("should build provider config with builder")
        void shouldBuildWithBuilder() {
            ProviderConfig config = ProviderConfig.builder()
                .id("builder-test")
                .type("openai")
                .name("Builder Test")
                .apiKey("sk-builder-key")
                .baseUrl("https://api.openai.com")
                .status(ConnectionStatus.CONNECTED)
                .build();
            
            assertThat(config.id()).isEqualTo("builder-test");
            assertThat(config.type()).isEqualTo("openai");
            assertThat(config.name()).isEqualTo("Builder Test");
            assertThat(config.apiKey()).isEqualTo("sk-builder-key");
            assertThat(config.baseUrl()).isEqualTo("https://api.openai.com");
            assertThat(config.status()).isEqualTo(ConnectionStatus.CONNECTED);
        }
        
        @Test
        @DisplayName("should set default timestamps")
        void shouldSetDefaultTimestamps() {
            Instant before = Instant.now();
            
            ProviderConfig config = ProviderConfig.builder()
                .id("time-test")
                .type("anthropic")
                .name("Time Test")
                .build();
            
            Instant after = Instant.now();
            
            assertThat(config.createdAt()).isBetween(before, after);
            assertThat(config.updatedAt()).isBetween(before, after);
        }
        
        @Test
        @DisplayName("should set default status to UNKNOWN")
        void shouldSetDefaultStatus() {
            ProviderConfig config = ProviderConfig.builder()
                .id("status-test")
                .type("anthropic")
                .name("Status Test")
                .build();
            
            assertThat(config.status()).isEqualTo(ConnectionStatus.UNKNOWN);
        }
    }
    
    @Nested
    @DisplayName("withUpdates")
    class WithUpdatesTests {
        
        @Test
        @DisplayName("should update name")
        void shouldUpdateName() {
            ProviderConfig original = ProviderConfig.builder()
                .id("update-test")
                .type("anthropic")
                .name("Original")
                .apiKey("key")
                .build();
            
            ProviderConfig updated = original.withUpdates("New Name", null, null);
            
            assertThat(updated.name()).isEqualTo("New Name");
            assertThat(updated.apiKey()).isEqualTo("key"); // Unchanged
            assertThat(updated.id()).isEqualTo("update-test"); // Unchanged
        }
        
        @Test
        @DisplayName("should update apiKey")
        void shouldUpdateApiKey() {
            ProviderConfig original = ProviderConfig.builder()
                .id("update-test")
                .type("anthropic")
                .name("Test")
                .apiKey("old-key")
                .build();
            
            ProviderConfig updated = original.withUpdates(null, "new-key", null);
            
            assertThat(updated.apiKey()).isEqualTo("new-key");
            assertThat(updated.name()).isEqualTo("Test"); // Unchanged
        }
        
        @Test
        @DisplayName("should update baseUrl")
        void shouldUpdateBaseUrl() {
            ProviderConfig original = ProviderConfig.builder()
                .id("update-test")
                .type("anthropic")
                .name("Test")
                .baseUrl("https://old.api.com")
                .build();
            
            ProviderConfig updated = original.withUpdates(null, null, "https://new.api.com");
            
            assertThat(updated.baseUrl()).isEqualTo("https://new.api.com");
        }
        
        @Test
        @DisplayName("should update updatedAt timestamp")
        void shouldUpdateTimestamp() throws InterruptedException {
            ProviderConfig original = ProviderConfig.builder()
                .id("update-test")
                .type("anthropic")
                .name("Test")
                .build();
            
            Thread.sleep(10); // Ensure time difference
            
            ProviderConfig updated = original.withUpdates("New Name", null, null);
            
            assertThat(updated.updatedAt()).isAfter(original.updatedAt());
            assertThat(updated.createdAt()).isEqualTo(original.createdAt()); // Unchanged
        }
    }
    
    @Nested
    @DisplayName("withStatus")
    class WithStatusTests {
        
        @Test
        @DisplayName("should update status")
        void shouldUpdateStatus() {
            ProviderConfig original = ProviderConfig.builder()
                .id("status-test")
                .type("anthropic")
                .name("Test")
                .status(ConnectionStatus.UNKNOWN)
                .build();
            
            ProviderConfig updated = original.withStatus(ConnectionStatus.CONNECTED);
            
            assertThat(updated.status()).isEqualTo(ConnectionStatus.CONNECTED);
        }
    }
    
    @Nested
    @DisplayName("isValidType")
    class IsValidTypeTests {
        
        @Test
        @DisplayName("should return true for valid types")
        void shouldReturnTrueForValidTypes() {
            assertThat(ProviderConfig.isValidType("anthropic")).isTrue();
            assertThat(ProviderConfig.isValidType("openai")).isTrue();
            assertThat(ProviderConfig.isValidType("google")).isTrue();
            assertThat(ProviderConfig.isValidType("mistral")).isTrue();
            assertThat(ProviderConfig.isValidType("bedrock")).isTrue();
        }
        
        @Test
        @DisplayName("should return false for invalid types")
        void shouldReturnFalseForInvalidTypes() {
            assertThat(ProviderConfig.isValidType("invalid")).isFalse();
            assertThat(ProviderConfig.isValidType("")).isFalse();
            assertThat(ProviderConfig.isValidType(null)).isFalse();
            assertThat(ProviderConfig.isValidType("ANTHROPIC")).isFalse(); // Case sensitive
        }
    }
    
    @Nested
    @DisplayName("Type constants")
    class TypeConstantsTests {
        
        @Test
        @DisplayName("should have correct type constants")
        void shouldHaveCorrectTypeConstants() {
            assertThat(ProviderConfig.TYPE_ANTHROPIC).isEqualTo("anthropic");
            assertThat(ProviderConfig.TYPE_OPENAI).isEqualTo("openai");
            assertThat(ProviderConfig.TYPE_GOOGLE).isEqualTo("google");
            assertThat(ProviderConfig.TYPE_MISTRAL).isEqualTo("mistral");
            assertThat(ProviderConfig.TYPE_BEDROCK).isEqualTo("bedrock");
        }
    }
}
