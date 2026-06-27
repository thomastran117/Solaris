package backend.services.intf.auth;

import java.util.List;

public interface TotpService {

    /** Generates a cryptographically-random Base32 TOTP secret. */
    String generateSecret();

    /**
     * Returns an {@code otpauth://} URI suitable for QR encoding.
     * The label is the user's email; the issuer is "ShopWave".
     */
    String getOtpAuthUri(String secret, String userEmail);

    /** Returns true if the 6-digit code is valid for the secret within the current time window. */
    boolean verifyCode(String secret, String code);

    /** Generates {@code count} random plaintext backup codes (10 alphanumeric chars each). */
    List<String> generateBackupCodes(int count);

    /** BCrypt-hashes a plaintext backup code for storage. */
    String hashBackupCode(String plainCode);

    /** Returns true if {@code plainCode} matches any hash in {@code hashes}. */
    boolean verifyBackupCode(String plainCode, List<String> hashes);
}
