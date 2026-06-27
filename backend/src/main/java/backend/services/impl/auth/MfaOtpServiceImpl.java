package backend.services.impl.auth;

import backend.exceptions.http.BadRequestException;
import backend.services.intf.CacheService;
import backend.services.intf.auth.MfaOtpService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;

@Service
public class MfaOtpServiceImpl implements MfaOtpService {

    static final String EMAIL_OTP_PREFIX = "mfa:otp:email:";
    static final String SMS_OTP_PREFIX   = "mfa:otp:sms:";
    static final long   OTP_TTL_SECONDS  = 300L; // 5 minutes

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public MfaOtpServiceImpl(CacheService cacheService, ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateEmailOtp(UUID userId) {
        String code = sixDigitCode();
        cacheService.set(EMAIL_OTP_PREFIX + userId, code, OTP_TTL_SECONDS);
        return code;
    }

    @Override
    public void verifyEmailOtp(UUID userId, String submittedCode) {
        String stored = cacheService.getAndDelete(EMAIL_OTP_PREFIX + userId);
        if (stored == null) {
            throw new BadRequestException("Verification code has expired or was not requested.");
        }
        if (!stored.equals(submittedCode)) {
            throw new BadRequestException("Invalid verification code.");
        }
    }

    @Override
    public String generateSmsOtp(UUID userId, String phoneNumber) {
        String code = sixDigitCode();
        String payload = toJson(Map.of("code", code, "phone", phoneNumber));
        cacheService.set(SMS_OTP_PREFIX + userId, payload, OTP_TTL_SECONDS);
        return code;
    }

    @Override
    public String verifySmsOtp(UUID userId, String submittedCode) {
        String stored = cacheService.getAndDelete(SMS_OTP_PREFIX + userId);
        if (stored == null) {
            throw new BadRequestException("Verification code has expired or was not requested.");
        }
        Map<?, ?> payload = fromJson(stored);
        String storedCode  = (String) payload.get("code");
        String phoneNumber = (String) payload.get("phone");
        if (!storedCode.equals(submittedCode)) {
            throw new BadRequestException("Invalid verification code.");
        }
        return phoneNumber;
    }

    private String sixDigitCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OTP payload", e);
        }
    }

    private Map<?, ?> fromJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Verification code has expired or was not requested.");
        }
    }
}
