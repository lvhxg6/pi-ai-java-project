package com.pi.chat.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ApiKeyEncryption.
 * 
 * <p>Validates:
 * <ul>
 *   <li>Requirement 10.1 - Secure storage of API keys</li>
 *   <li>Requirement 10.2 - API keys not exposed in responses</li>
 * </ul>
 */
class ApiKeyEncryptionTest {
    
    private static final String ENCRYPTION_KEY = "test-encryption-key-12345";
    private ApiKeyEncryption encryption;
    
    @BeforeEach
    void setUp() {
        encryption = new ApiKeyEncryption(ENCRYPTION_KEY);
    }
    
    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        
        @Test
        @DisplayName("should create instance with valid encryption key")
        void shouldCreateInstanceWithValidKey() {
            assertThatCode(() -> new ApiKeyEncryption("valid-key"))
                .doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("should throw exception for null encryption key")
        void shouldThrowForNullKey() {
            assertThatThrownBy(() -> new ApiKeyEncryption(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or empty");
        }
        
        @Test
        @DisplayName("should throw exception for empty encryption key")
        void shouldThrowForEmptyKey() {
            assertThatThrownBy(() -> new ApiKeyEncryption(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or empty");
        }
    }
    
    @Nested
    @DisplayName("Encryption")
    class EncryptionTests {
        
        @Test
        @DisplayName("should encrypt plain API key")
        void shouldEncryptPlainApiKey() {
            String plainKey = "sk-test-api-key-12345";
            
            String encrypted = encryption.encrypt(plainKey);
            
            assertThat(encrypted)
                .startsWith("encrypted:")
                .isNotEqualTo(plainKey);
        }
        
        @Test
        @DisplayName("should return already encrypted key unchanged")
        void shouldReturnEncryptedKeyUnchanged() {
            String plainKey = "sk-test-api-key-12345";
            String encrypted = encryption.encrypt(plainKey);
            
            String doubleEncrypted = encryption.encrypt(encrypted);
            
            assertThat(doubleEncrypted).isEqualTo(encrypted);
        }
        
        @Test
        @DisplayName("should throw exception for null API key")
        void shouldThrowForNullApiKey() {
            assertThatThrownBy(() -> encryption.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
        }
        
        @Test
        @DisplayName("should encrypt empty string")
        void shouldEncryptEmptyString() {
            String encrypted = encryption.encrypt("");
            
            assertThat(encrypted).startsWith("encrypted:");
        }
        
        @Test
        @DisplayName("should produce different ciphertext for same plaintext (due to random IV)")
        void shouldProduceDifferentCiphertextForSamePlaintext() {
            String plainKey = "sk-test-api-key-12345";
            
            String encrypted1 = encryption.encrypt(plainKey);
            String encrypted2 = encryption.encrypt(plainKey);
            
            // Due to random IV, encrypted values should be different
            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }
    }
    
    @Nested
    @DisplayName("Decryption")
    class DecryptionTests {
        
        @Test
        @DisplayName("should decrypt encrypted API key")
        void shouldDecryptEncryptedApiKey() {
            String plainKey = "sk-test-api-key-12345";
            String encrypted = encryption.encrypt(plainKey);
            
            String decrypted = encryption.decrypt(encrypted);
            
            assertThat(decrypted).isEqualTo(plainKey);
        }
        
        @Test
        @DisplayName("should return plain key unchanged")
        void shouldReturnPlainKeyUnchanged() {
            String plainKey = "sk-test-api-key-12345";
            
            String result = encryption.decrypt(plainKey);
            
            assertThat(result).isEqualTo(plainKey);
        }
        
        @Test
        @DisplayName("should throw exception for null encrypted key")
        void shouldThrowForNullEncryptedKey() {
            assertThatThrownBy(() -> encryption.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
        }
        
        @Test
        @DisplayName("should decrypt empty string correctly")
        void shouldDecryptEmptyString() {
            String encrypted = encryption.encrypt("");
            
            String decrypted = encryption.decrypt(encrypted);
            
            assertThat(decrypted).isEmpty();
        }
        
        @Test
        @DisplayName("should throw exception for invalid encrypted data")
        void shouldThrowForInvalidEncryptedData() {
            String invalidEncrypted = "encrypted:invalid-base64-data!!!";
            
            assertThatThrownBy(() -> encryption.decrypt(invalidEncrypted))
                .isInstanceOf(ApiKeyEncryption.ApiKeyEncryptionException.class);
        }
    }
    
    @Nested
    @DisplayName("Round-trip")
    class RoundTripTests {
        
        @Test
        @DisplayName("should round-trip various API key formats")
        void shouldRoundTripVariousFormats() {
            String[] testKeys = {
                "sk-ant-api03-test-key",
                "sk-proj-test-key-12345",
                "AIzaSyTest-Google-Key",
                "very-long-api-key-" + "x".repeat(100),
                "key-with-special-chars-!@#$%^&*()",
                "unicode-key-中文-日本語-한국어"
            };
            
            for (String key : testKeys) {
                String encrypted = encryption.encrypt(key);
                String decrypted = encryption.decrypt(encrypted);
                
                assertThat(decrypted)
                    .as("Round-trip for key: %s", key)
                    .isEqualTo(key);
            }
        }
    }
    
    @Nested
    @DisplayName("isEncrypted")
    class IsEncryptedTests {
        
        @Test
        @DisplayName("should return true for encrypted key")
        void shouldReturnTrueForEncryptedKey() {
            String encrypted = encryption.encrypt("test-key");
            
            assertThat(ApiKeyEncryption.isEncrypted(encrypted)).isTrue();
        }
        
        @Test
        @DisplayName("should return false for plain key")
        void shouldReturnFalseForPlainKey() {
            assertThat(ApiKeyEncryption.isEncrypted("sk-test-key")).isFalse();
        }
        
        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(ApiKeyEncryption.isEncrypted(null)).isFalse();
        }
        
        @Test
        @DisplayName("should return false for empty string")
        void shouldReturnFalseForEmptyString() {
            assertThat(ApiKeyEncryption.isEncrypted("")).isFalse();
        }
    }
    
    @Nested
    @DisplayName("mask")
    class MaskTests {
        
        @Test
        @DisplayName("should mask API key showing prefix and suffix")
        void shouldMaskApiKey() {
            String masked = ApiKeyEncryption.mask("sk-test-api-key-12345");
            
            assertThat(masked)
                .startsWith("sk-")
                .endsWith("2345")
                .contains("****");
        }
        
        @Test
        @DisplayName("should mask short API key")
        void shouldMaskShortApiKey() {
            String masked = ApiKeyEncryption.mask("short");
            
            assertThat(masked).isEqualTo("****");
        }
        
        @Test
        @DisplayName("should return empty string for null")
        void shouldReturnEmptyForNull() {
            assertThat(ApiKeyEncryption.mask(null)).isEmpty();
        }
        
        @Test
        @DisplayName("should return empty string for empty input")
        void shouldReturnEmptyForEmptyInput() {
            assertThat(ApiKeyEncryption.mask("")).isEmpty();
        }
        
        @Test
        @DisplayName("should mask encrypted key")
        void shouldMaskEncryptedKey() {
            String encrypted = encryption.encrypt("test-key");
            
            String masked = ApiKeyEncryption.mask(encrypted);
            
            assertThat(masked).isEqualTo("****...****");
        }
    }
    
    @Nested
    @DisplayName("Different encryption keys")
    class DifferentKeysTests {
        
        @Test
        @DisplayName("should not decrypt with different key")
        void shouldNotDecryptWithDifferentKey() {
            String plainKey = "sk-test-api-key";
            String encrypted = encryption.encrypt(plainKey);
            
            ApiKeyEncryption differentEncryption = new ApiKeyEncryption("different-key");
            
            assertThatThrownBy(() -> differentEncryption.decrypt(encrypted))
                .isInstanceOf(ApiKeyEncryption.ApiKeyEncryptionException.class);
        }
    }
}
