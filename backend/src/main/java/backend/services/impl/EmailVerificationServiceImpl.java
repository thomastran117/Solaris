package backend.services.impl;

import backend.configurations.environment.EnvironmentSetting;
import backend.exceptions.http.BadRequestException;
import backend.services.intf.CacheService;
import backend.services.intf.EmailService;
import backend.services.intf.EmailVerificationService;
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
    public void initiateVerification(long userId, String email) {
        String token = UUID.randomUUID().toString();
        long ttl = env.getEmail().getVerificationTokenTtlSeconds();
        cache.set(EMAIL_VERIFY_PREFIX + token, String.valueOf(userId), ttl);
        emailService.sendVerificationEmail(email, token);
    }

    @Override
    public long consumeVerificationToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Verification token is required.");
        }
        String raw = cache.getAndDelete(EMAIL_VERIFY_PREFIX + token);
        if (raw == null) {
            throw new BadRequestException("Invalid or expired verification token.");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Malformed verification token.");
        }
    }
}
