package com.project.server.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 256-bit 대칭 암호화/복호화 서비스
 * CODEF 토큰 및 민감 정보 보안 저장용
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CryptoService {

    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;

    @Value("${crypto.aes.key:}")
    private String aesKeyString;

    private SecretKey secretKey;

    private SecretKey getSecretKey() {
        if (secretKey == null) {
            if (aesKeyString == null || aesKeyString.isBlank()) {
                throw new IllegalStateException(
                        "AES encryption key is not configured. " +
                        "Please set 'crypto.aes.key' in application.yml or environment variables. " +
                        "Generate a 256-bit key: openssl enc -aes-256-cbc -S $(openssl rand -hex 8) -P -pass pass:YourPassphrase");
            }
            
            // Base64-encoded key 디코딩
            byte[] decodedKey = Base64.getDecoder().decode(aesKeyString);
            
            // 키 길이 검증 (256-bit = 32 bytes)
            if (decodedKey.length != 32) {
                throw new IllegalStateException(
                        "AES key must be 256-bit (32 bytes). Current key size: " + (decodedKey.length * 8) + " bits");
            }
            
            secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
        }
        return secretKey;
    }

    /**
     * 평문 암호화
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("Error encrypting data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * 암호문 복호화
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey());
            byte[] decodedBytes = Base64.getDecoder().decode(ciphertext);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error decrypting data", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * 개발/테스트용: 256-bit AES 키 생성 헬퍼
     * 실제 프로덕션에서는 이를 실행해 키를 생성하고 환경 변수로 관리하세요.
     */
    public static String generateBase64EncodedKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE);
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            log.error("Error generating AES key", e);
            throw new RuntimeException("Key generation failed", e);
        }
    }
}
