package com.yupi.springbootinit.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密钥加密回归（任务书 §4）：主密钥来自环境变量，密文可逆，
 * 未配置主密钥时必须显式失败，绝不退回明文存储。
 */
class SecretCipherTest {

    @Test
    void encryptsAndDecryptsWithMasterKey() {
        SecretCipher cipher = new SecretCipher("demo-master-key");
        assertTrue(cipher.available());
        String plain = "sk-6d9dd210fd1f4abc832e3f80efc5d6ee";
        String sealed = cipher.encrypt(plain);
        assertNotEquals(plain, sealed, "密文不得等于明文");
        assertTrue(sealed.startsWith("v1:"), "密文必须带版本前缀以便轮换");
        assertFalse(sealed.contains("6d9dd210"), "密文不得包含明文片段");
        assertEquals(plain, cipher.decrypt(sealed));
    }

    @Test
    void sameKeyProducesDifferentCipherText() {
        SecretCipher cipher = new SecretCipher("demo-master-key");
        String plain = "sk-shared-value";
        // GCM 随机 IV：相同明文两次加密结果不同，避免相同密钥被识别为同一份
        assertNotEquals(cipher.encrypt(plain), cipher.encrypt(plain));
    }

    @Test
    void missingMasterKeyRefusesToStoreAnything() {
        SecretCipher cipher = new SecretCipher("");
        assertFalse(cipher.available());
        IllegalStateException encryptFailure = assertThrows(IllegalStateException.class,
                () -> cipher.encrypt("sk-plain"));
        assertTrue(encryptFailure.getMessage().contains("OSLAB_AI_MASTER_KEY"),
                "失败原因必须指出缺失的环境变量");
        assertFalse(encryptFailure.getMessage().contains("sk-plain"), "异常信息不得带明文密钥");
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("v1:aaa:bbb"));
    }

    @Test
    void wrongMasterKeyCannotDecrypt() {
        String sealed = new SecretCipher("master-a").encrypt("sk-value");
        assertThrows(IllegalStateException.class, () -> new SecretCipher("master-b").decrypt(sealed));
    }

    @Test
    void malformedCipherTextIsRejected() {
        SecretCipher cipher = new SecretCipher("demo-master-key");
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("not-a-cipher"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("v2:aaa:bbb"));
        assertNull(cipher.decrypt(""));
        assertNull(cipher.encrypt(null));
    }

    @Test
    void maskingNeverRevealsTheSecret() {
        assertEquals("sk-****d6ee", SecretCipher.mask("sk-6d9dd210fd1f4abc832e3f80efc5d6ee"));
        assertEquals("****", SecretCipher.mask("short"), "短密钥完全遮蔽");
        assertEquals("****", SecretCipher.mask("12345678"), "8 位及以下完全遮蔽");
        assertEquals("", SecretCipher.mask(null));
        assertEquals("", SecretCipher.mask("   "));
    }

    @Test
    void fingerprintIsStableAndNotReversible() {
        String first = SecretCipher.fingerprint("sk-value");
        assertEquals(first, SecretCipher.fingerprint("sk-value"));
        assertEquals(first, SecretCipher.fingerprint(" sk-value "), "首尾空白视为同一把 key");
        assertNotEquals(first, SecretCipher.fingerprint("sk-other"));
        assertEquals(16, first.length());
        assertFalse(first.contains("value"));
    }
}
