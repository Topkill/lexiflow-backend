package com.lexiflow.infra.crypto;

import com.lexiflow.infra.properties.CryptoProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * API Key 加解密服务。
 * <p>
 * 使用 AES/GCM/NoPadding 算法对用户 API Key 进行加解密。
 * 密钥通过 SHA-256 哈希归一化，每次加密使用随机 IV 保证安全性。
 * </p>
 */
@Service
public class ApiKeyCryptoService {

    /** IV 长度（字节） */
    private static final int IV_LENGTH = 12;
    /** GCM 认证标签长度（位） */
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKeySpec;

    public ApiKeyCryptoService(CryptoProperties cryptoProperties) {
        this.secretKeySpec = new SecretKeySpec(normalizeKey(cryptoProperties.apiKeySecret()), "AES");
    }

    /**
     * 加密明文。
     *
     * @param plainText 明文
     * @return Base64 编码的密文（包含 IV）
     */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("API Key 加密失败", ex);
        }
    }

    /**
     * 解密密文。
     *
     * @param encryptedText Base64 编码的密文（包含 IV）
     * @return 解密后的明文
     */
    public String decrypt(String encryptedText) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("API Key 解密失败", ex);
        }
    }

    /** 将密钥字符串通过 SHA-256 哈希归一化为 32 字节。 */
    private byte[] normalizeKey(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("API Key 密钥初始化失败", ex);
        }
    }
}
