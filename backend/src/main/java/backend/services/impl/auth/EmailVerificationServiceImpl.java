package backend.services.impl.auth;

import backend.configurations.environment.EnvironmentSetting;
import backend.exceptions.http.BadRequestException;
import backend.services.intf.CacheService;
import backend.services.intf.support.EmailService;
import backend.services.intf.auth.EmailVerificationService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final String EMAIL_VERIFY_PREFIX = "email:verify:";

    private final CacheService cache;
    private final EmailService emailService;
    private final EnvironmentSetting env;

    public EmailVerificationServiceImpl(CacheService cache,
                                        EmailService emailService,
                                        EnvironmentSetting env) {
        this.cache = cache;
        this.emailService = emailService;
        this.env = env;
    }

    @Override
    public void initiateVerification(UUID userId, String email) {
        String token = UUID.randomUUID().toString();
        long ttl = env.getEmail().getVerificationTokenTtlSeconds();
        cache.set(EMAIL_VERIFY_PREFIX + token, userId.toString(), ttl);
        emailService.sendVerificationEmail(email, token);
    }

    @Override
    public UUID consumeVerificationToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Verification token is required.");
        }
        String raw = cache.getAndDelete(EMAIL_VERIFY_PREFIX + token);
        if (raw == null) {
            throw new BadRequestException("Invalid or expired verification token.");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Malformed verification token.");
        }
    }
}
