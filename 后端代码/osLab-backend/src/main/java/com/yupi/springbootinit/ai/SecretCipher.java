package com.yupi.springbootinit.ai;

import org.apache.commons.lang3.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 加密（任务书 §4）：AES-256-GCM，主密钥只来自环境变量。
 *
 * <p>密文格式 {@code v1:<base64(iv)>:<base64(cipherText+tag)>}。带版本前缀，
 * 便于将来轮换主密钥时区分格式。
 *
 * <p>本类只处理密文：明文密钥不写日志、不进响应、不落库。
 * 主密钥缺失时 {@link #available()} 为 false，调用方必须显式报错，
 * 绝不退回明文存储。
 */
public final class SecretCipher {

    private static final String VERSION = "v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(String masterKey) {
        this.key = StringUtils.isBlank(masterKey) ? null : derive(masterKey);
    }

    /** 主密钥是否已配置。未配置时不得保存任何个人密钥。 */
    public boolean available() {
        return key != null;
    }

    public String encrypt(String plain) {
        if (key == null) {
            throw new IllegalStateException("未配置 OSLAB_AI_MASTER_KEY，无法加密保存 API Key");
        }
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return VERSION + ":" + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(sealed);
        } catch (Exception e) {
            // 异常信息里不得带明文密钥
            throw new IllegalStateException("API Key 加密失败：" + e.getClass().getSimpleName(), e);
        }
    }

    public String decrypt(String cipherText) {
        if (key == null) {
            throw new IllegalStateException("未配置 OSLAB_AI_MASTER_KEY，无法解密 API Key");
        }
        if (StringUtils.isBlank(cipherText)) {
            return null;
        }
        String[] parts = cipherText.split(":");
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalStateException("API Key 密文格式无效");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] sealed = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败（主密钥可能已更换）", e);
        }
    }

    /**
     * 脱敏展示值：只保留极少量可辨识字符，供用户在设置页确认"是哪一个 key"。
     * 短密钥完全遮蔽，避免小样本被还原。
     */
    public static String mask(String plain) {
        if (StringUtils.isBlank(plain)) {
            return "";
        }
        String value = plain.trim();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /** 密文指纹：用于在不解密的前提下判断"是否同一把 key"。 */
    public static String fingerprint(String plain) {
        if (StringUtils.isBlank(plain)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plain.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static SecretKeySpec derive(String masterKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(masterKey.trim().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("主密钥派生失败", e);
        }
    }
}
