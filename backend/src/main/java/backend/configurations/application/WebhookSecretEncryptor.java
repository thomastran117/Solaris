package backend.configurations.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Encrypts webhook signing secrets at rest using AES-256-GCM.
 *
 * When {@code app.webhook.secret-encryption-key} is not set the service is a
 * pass-through, which is safe for local development and integration tests where
 * secrets are ephemeral.  Production deployments MUST set the property to a
 * 64-hex-character (256-bit) key, e.g. from {@code openssl rand -hex 32}.
 *
 * Storage format: Base64( IV[12] || GCM-ciphertext-and-tag )
 * Max stored length for a 64-char plaintext: ~112 Base64 chars (fits in 256).
 */
@Component
public class WebhookSecretEncryptor {

    private static final int GCM_IV_LEN  = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    @Value("${app.webhook.secret-encryption-key:}")
    private String keyHex;

    public String encrypt(String raw) {
        if (keyHex == null || keyHex.isBlank()) return raw;
        try {
            byte[] key = HexFormat.of().parseHex(keyHex);
            byte[] iv  = new byte[GCM_IV_LEN];
            RNG.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[GCM_IV_LEN + ct.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LEN);
            System.arraycopy(ct, 0, combined, GCM_IV_LEN, ct.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt webhook secret", e);
        }
    }

    public String decrypt(String stored) {
        if (keyHex == null || keyHex.isBlank()) return stored;
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            byte[] iv = Arrays.copyOf(combined, GCM_IV_LEN);
            byte[] ct = Arrays.copyOfRange(combined, GCM_IV_LEN, combined.length);
            byte[] key = HexFormat.of().parseHex(keyHex);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt webhook secret", e);
        }
    }
}
