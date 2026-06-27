package backend.services.intf.auth;

import backend.dtos.responses.mfa.MfaCredentialResponse;
import backend.dtos.responses.mfa.TotpEnrollmentResponse;
import backend.models.enums.MfaType;

import java.util.List;
import java.util.UUID;

public interface MfaService {

    /** Sends a 6-digit OTP to the user's account email to begin email MFA enrollment. */
    void initiateEmailEnrollment(UUID userId);

    /** Verifies the email OTP and persists an EMAIL MfaCredential as verified. */
    void verifyEmailEnrollment(UUID userId, String code);

    /** Sends a 6-digit OTP to {@code phoneNumber} to begin SMS MFA enrollment. */
    void initiateSmsEnrollment(UUID userId, String phoneNumber);

    /** Verifies the SMS OTP and persists an SMS MfaCredential with the verified phone number. */
    void verifySmsEnrollment(UUID userId, String code);

    /**
     * Generates a TOTP secret and caches it for 10 minutes.
     *
     * @return the secret and an {@code otpauth://} URI for QR display — returned only once, never again
     */
    TotpEnrollmentResponse initiateTotpEnrollment(UUID userId);

    /**
     * Verifies the first TOTP code against the pending secret and persists the TOTP MfaCredential.
     *
     * @return plaintext backup codes — returned only here, must be saved by the user
     */
    List<String> verifyTotpEnrollment(UUID userId, String code);

    /** Returns all enrolled MFA methods for the user. */
    List<MfaCredentialResponse> listMethods(UUID userId);

    /** Removes the specified MFA method. Throws ResourceNotFoundException if not enrolled. */
    void removeMethod(UUID userId, MfaType type);
}
