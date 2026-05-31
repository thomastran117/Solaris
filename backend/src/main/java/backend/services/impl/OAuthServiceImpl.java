package backend.services.impl;

import backend.aspects.OAuthResilient;
import backend.configurations.environment.EnvironmentSetting;
import backend.models.other.OAuthUser;
import backend.security.oauth.InvalidOAuthTokenException;
import backend.security.oauth.OAuthClaimUtils;
import backend.security.oauth.OAuthExceptionClassifier;
import backend.security.oauth.OAuthProviderNotConfiguredException;
import backend.security.oauth.OAuthProviderTransientException;
import backend.security.oauth.OAuthVerificationError;
import backend.services.intf.OAuthService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;

/**
 * Verifies OAuth ID tokens from Google, Microsoft, and Apple. The client sends the token
 * obtained from the provider; this service verifies it (signature, audience, issuer)
 * and returns user claims. Retry and circuit breaker are applied by {@link backend.aspects.OAuthRetryAspect}.
 * <p>
 * Each provider bean is optional: if the corresponding client ID environment variable is not set,
 * the bean is absent and the injected field is null. Any call to a disabled provider logs a
 * warning and throws {@link OAuthProviderNotConfiguredException}.
 * </p>
 * Logging: debug must not include tokens, PII, or provider responses.
 */
@Service
public class OAuthServiceImpl implements OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthServiceImpl.class);

    /**
     * Maximum token length to avoid oversized token attacks. Typical Google/Microsoft ID tokens
     * are under 4KB; 16KB allows headroom. Configurable via app.security.oauth-max-token-length.
     */
    private static final int DEFAULT_MAX_TOKEN_LENGTH = 16_384;

    private final GoogleIdTokenVerifier googleVerifier;
    private final JwtDecoder microsoftDecoder;
    private final JwtDecoder appleDecoder;
    private final int maxTokenLength;

    public OAuthServiceImpl(
            @Qualifier("googleIdTokenVerifier") @Nullable GoogleIdTokenVerifier googleVerifier,
            @Qualifier("microsoftJwtDecoder") @Nullable JwtDecoder microsoftDecoder,
            @Qualifier("appleJwtDecoder") @Nullable JwtDecoder appleDecoder,
            EnvironmentSetting env) {
        this.googleVerifier = googleVerifier;
        this.microsoftDecoder = microsoftDecoder;
        this.appleDecoder = appleDecoder;
        int configured = env != null && env.getSecurity() != null ? env.getSecurity().getOauthMaxTokenLength() : DEFAULT_MAX_TOKEN_LENGTH;
        this.maxTokenLength = configured > 0 ? configured : DEFAULT_MAX_TOKEN_LENGTH;

        if (googleVerifier == null) {
            log.warn("Google OAuth is disabled: GOOGLE_CLIENT_ID is not set. Requests to POST /auth/google will be rejected.");
        }
        if (microsoftDecoder == null) {
            log.warn("Microsoft OAuth is disabled: MICROSOFT_CLIENT_ID is not set. Requests to POST /auth/microsoft will be rejected.");
        }
        if (appleDecoder == null) {
            log.warn("Apple OAuth is disabled: APPLE_CLIENT_ID is not set. Requests to POST /auth/apple will be rejected.");
        }
    }

    /** Safely extracts a string claim from Google payload; avoids ClassCastException when provider returns non-string. */
    private static String getStringClaimFromPayload(GoogleIdToken.Payload payload, String claimName) {
        Object value = payload.get(claimName);
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s.isBlank() ? null : s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            String s = value.toString();
            return (s == null || s.isBlank()) ? null : s;
        }
        return null;
    }

    /** Shared check for blank, invalid, or oversized tokens; throws before any verification. */
    private void requireValidTokenLength(String token, String providerLabel) {
        if (token == null || token.isBlank()) {
            throw new InvalidOAuthTokenException("Invalid " + providerLabel + " token");
        }
        if (token.length() > maxTokenLength) {
            throw new InvalidOAuthTokenException("Invalid " + providerLabel + " token");
        }
    }

    @Override
    @OAuthResilient
    public OAuthUser verifyGoogleToken(String googleToken) {
        if (googleVerifier == null) {
            log.warn("Google OAuth sign-in was attempted but GOOGLE_CLIENT_ID is not configured.");
            throw new OAuthProviderNotConfiguredException("Google sign-in is not configured on this server");
        }
        requireValidTokenLength(googleToken, "Google ID");
        if (log.isDebugEnabled()) {
            log.debug("Verifying Google ID token");
        }
        GoogleIdToken idToken;
        try {
            idToken = googleVerifier.verify(googleToken);
        } catch (GeneralSecurityException e) {
            throw new InvalidOAuthTokenException("Invalid Google ID token", e);
        } catch (Exception e) {
            if (OAuthExceptionClassifier.isRetryable(e)) {
                throw new OAuthProviderTransientException("Google token verification failed", e);
            }
            if (OAuthExceptionClassifier.isValidationFailure(e)) {
                throw new InvalidOAuthTokenException("Invalid Google ID token", e);
            }
            throw new OAuthVerificationError("Google token verification failed", e);
        }
        if (idToken == null) {
            throw new InvalidOAuthTokenException("Invalid Google ID token");
        }
        GoogleIdToken.Payload payload = idToken.getPayload();
        String sub = payload.getSubject();
        String email = payload.getEmail();
        String name = getStringClaimFromPayload(payload, "name");
        if (name == null || name.isBlank()) {
            name = email != null ? email : "";
        }
        if (email == null || email.isBlank()) {
            throw new InvalidOAuthTokenException("Missing Google email claim");
        }
        return new OAuthUser(sub != null ? sub : "", email, (name != null && !name.isBlank()) ? name : email, "google");
    }

    @Override
    @OAuthResilient
    public OAuthUser verifyMicrosoftToken(String microsoftToken) {
        if (microsoftDecoder == null) {
            log.warn("Microsoft OAuth sign-in was attempted but MICROSOFT_CLIENT_ID is not configured.");
            throw new OAuthProviderNotConfiguredException("Microsoft sign-in is not configured on this server");
        }
        requireValidTokenLength(microsoftToken, "Microsoft");
        if (log.isDebugEnabled()) {
            log.debug("Verifying Microsoft ID token");
        }
        Jwt jwt;
        try {
            jwt = microsoftDecoder.decode(microsoftToken);
        } catch (JwtException e) {
            throw new InvalidOAuthTokenException("Invalid Microsoft token", e);
        } catch (Exception e) {
            if (OAuthExceptionClassifier.isRetryable(e)) {
                throw new OAuthProviderTransientException("Microsoft token verification failed", e);
            }
            throw new OAuthVerificationError("Microsoft token verification failed", e);
        }
        if (jwt == null) {
            throw new InvalidOAuthTokenException("Invalid Microsoft token (no claims)");
        }
        String email = OAuthClaimUtils.getClaim(jwt, "preferred_username", "email");
        if (email == null || email.isBlank()) {
            throw new InvalidOAuthTokenException("Missing Microsoft email claim");
        }
        String name = OAuthClaimUtils.getClaim(jwt, "name", null);
        if (name == null || name.isBlank()) {
            name = email;
        }
        String sub = OAuthClaimUtils.getClaim(jwt, "sub", null);
        if (sub == null || sub.isBlank()) {
            throw new InvalidOAuthTokenException("Missing Microsoft sub claim");
        }
        return new OAuthUser(sub, email, name, "microsoft");
    }

    @Override
    @OAuthResilient
    public OAuthUser verifyAppleToken(String appleToken) {
        if (appleDecoder == null) {
            log.warn("Apple OAuth sign-in was attempted but APPLE_CLIENT_ID is not configured.");
            throw new OAuthProviderNotConfiguredException("Apple sign-in is not configured on this server");
        }
        requireValidTokenLength(appleToken, "Apple");
        if (log.isDebugEnabled()) {
            log.debug("Verifying Apple ID token");
        }
        Jwt jwt;
        try {
            jwt = appleDecoder.decode(appleToken);
        } catch (JwtException e) {
            throw new InvalidOAuthTokenException("Invalid Apple token", e);
        } catch (Exception e) {
            if (OAuthExceptionClassifier.isRetryable(e)) {
                throw new OAuthProviderTransientException("Apple token verification failed", e);
            }
            throw new OAuthVerificationError("Apple token verification failed", e);
        }
        if (jwt == null) {
            throw new InvalidOAuthTokenException("Invalid Apple token (no claims)");
        }
        String sub = OAuthClaimUtils.getClaim(jwt, "sub", null);
        if (sub == null || sub.isBlank()) {
            throw new InvalidOAuthTokenException("Missing Apple sub claim");
        }
        String email = OAuthClaimUtils.getClaim(jwt, "email", null);
        if (email == null || email.isBlank()) {
            throw new InvalidOAuthTokenException("Missing Apple email claim");
        }
        String name = OAuthClaimUtils.getClaim(jwt, "name", null);
        if (name == null || name.isBlank()) {
            name = email;
        }
        return new OAuthUser(sub, email, name, "apple");
    }
}
