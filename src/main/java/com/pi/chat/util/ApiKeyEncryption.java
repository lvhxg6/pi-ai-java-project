package com.pi.chat.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Utility class for encrypting and decrypting API keys.
 * 
 * <p>Uses AES-GCM encryption with PBKDF2 key derivation for secure storage
 * of sensitive API credentials.
 * 
 * <p>Requirements:
 * <ul>
 *   <li>10.1 - Store Provider_Config data in a secure configuration file</li>
 *   <li>10.2 - API keys are not exposed in API responses or logs</li>
 * </ul>
 */
public class ApiKeyEncryption {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;
    private static final String ENCRYPTED_PREFIX = "encrypted:";
    
    private static final byte[] SALT = "ai-chat-web-salt".getBytes(StandardCharsets.UTF_8);
    
    private final SecretKey secretKey;
    private final SecureRandom secureRandom;
    
    /**
     * Creates an ApiKeyEncryption instance with the given encryption key.
     * 
     * @param encryptionKey The master encryption key (password)
     * @throws IllegalArgumentException if encryptionKey is null or empty
     */
    public ApiKeyEncryption(String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            throw new IllegalArgumentException("Encryption key must not be null or empty");
        }
        this.secretKey = deriveKey(encryptionKey);
        this.secureRandom = new SecureRandom();
    }
    
    /**
     * Encrypts an API key.
     * 
     * @param plainApiKey The plain text API key to encrypt
     * @return Encrypted API key with "encrypted:" prefix
     * @throws IllegalArgumentException if plainApiKey is null
     * @throws ApiKeyEncryptionException if encryption fails
     */
    public String encrypt(String plainApiKey) {
        if (plainApiKey == null) {
            throw new IllegalArgumentException("API key must not be null");
        }
        
        // If already encrypted, return as-is
        if (isEncrypted(plainApiKey)) {
            return plainApiKey;
        }
        
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] encryptedBytes = cipher.doFinal(plainApiKey.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new ApiKeyEncryptionException("Failed to encrypt API key", e);
        }
    }
    
    /**
     * Decrypts an encrypted API key.
     * 
     * @param encryptedApiKey The encrypted API key (with "encrypted:" prefix)
     * @return Decrypted plain text API key
     * @throws IllegalArgumentException if encryptedApiKey is null
     * @throws ApiKeyEncryptionException if decryption fails
     */
    public String decrypt(String encryptedApiKey) {
        if (encryptedApiKey == null) {
            throw new IllegalArgumentException("Encrypted API key must not be null");
        }
        
        // If not encrypted, return as-is
        if (!isEncrypted(encryptedApiKey)) {
            return encryptedApiKey;
        }
        
        try {
            String base64Data = encryptedApiKey.substring(ENCRYPTED_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(base64Data);
            
            // Extract IV and encrypted data
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedBytes.length);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ApiKeyEncryptionException("Failed to decrypt API key", e);
        }
    }
    
    /**
     * Checks if an API key is encrypted.
     * 
     * @param apiKey The API key to check
     * @return true if the API key has the encrypted prefix
     */
    public static boolean isEncrypted(String apiKey) {
        return apiKey != null && apiKey.startsWith(ENCRYPTED_PREFIX);
    }
    
    /**
     * Masks an API key for display purposes.
     * 
     * <p>Shows only the first 3 and last 4 characters, with asterisks in between.
     * For example: "sk-abc...xyz1234" becomes "sk-****...****"
     * 
     * @param apiKey The API key to mask (can be encrypted or plain)
     * @return Masked API key safe for display
     */
    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        
        // If encrypted, just show that it's encrypted
        if (isEncrypted(apiKey)) {
            return "****...****";
        }
        
        int length = apiKey.length();
        if (length <= 8) {
            return "****";
        }
        
        // Show first 3 chars and last 4 chars
        String prefix = apiKey.substring(0, Math.min(3, length));
        String suffix = apiKey.substring(Math.max(0, length - 4));
        return prefix + "****...****" + suffix;
    }
    
    /**
     * Derives an AES key from the encryption password using PBKDF2.
     */
    private SecretKey deriveKey(String password) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, ITERATION_COUNT, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), KEY_ALGORITHM);
        } catch (Exception e) {
            throw new ApiKeyEncryptionException("Failed to derive encryption key", e);
        }
    }
    
    /**
     * Exception thrown when API key encryption/decryption fails.
     */
    public static class ApiKeyEncryptionException extends RuntimeException {
        public ApiKeyEncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
