package backend.services.intf.auth;

import java.util.UUID;

public interface MfaOtpService {

    /**
     * Generates a 6-digit OTP and caches it at {@code mfa:otp:email:{userId}} with a 5-minute TTL.
     *
     * @return the generated plaintext code (so the caller can send it)
     */
    String generateEmailOtp(UUID userId);

    /**
     * Atomically retrieves and deletes the email OTP for this user, then checks it against
     * {@code submittedCode}. Throws {@code BadRequestException} on mismatch or expiry.
     */
    void verifyEmailOtp(UUID userId, String submittedCode);

    /**
     * Generates a 6-digit OTP and caches {@code {"code":"...","phone":"..."}} as JSON
     * at {@code mfa:otp:sms:{userId}} with a 5-minute TTL.
     *
     * @return the generated plaintext code (so the caller can send it via SMS)
     */
    String generateSmsOtp(UUID userId, String phoneNumber);

    /**
     * Atomically retrieves and deletes the SMS OTP entry, checks the code, and returns the
     * verified phone number. Throws {@code BadRequestException} on mismatch or expiry.
     */
    String verifySmsOtp(UUID userId, String submittedCode);
}
