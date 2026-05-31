package backend.services.intf;

import backend.aspects.OAuthResilient;
import backend.models.other.OAuthUser;

/**
 * Verifies OAuth ID tokens from providers (Google, Microsoft). The client (web or mobile)
 * obtains the token from the provider and sends it to the backend; this service verifies
 * it with the provider (e.g. via signature validation and audience/issuer checks).
 * Methods that call providers are annotated with {@link OAuthResilient} so retry and
 * circuit breaker apply; new provider methods should be annotated on the interface.
 */
public interface OAuthService {

    /**
     * Verify a Google ID token and return the user claims.
     *
     * @param googleToken the ID token from the Google sign-in client
     * @return verified user info (sub, email, name, provider)
     * @throws backend.security.oauth.InvalidOAuthTokenException   if the token is invalid
     * @throws backend.security.oauth.OAuthProviderTransientException if provider call failed transiently (retried by aspect)
     */
    @OAuthResilient
    OAuthUser verifyGoogleToken(String googleToken);

    /**
     * Verify a Microsoft ID token and return the user claims.
     *
     * @param microsoftToken the ID token from the Microsoft sign-in client
     * @return verified user info (sub, email, name, provider)
     * @throws backend.security.oauth.InvalidOAuthTokenException   if the token is invalid or required claims are missing
     * @throws backend.security.oauth.OAuthProviderTransientException if provider call failed transiently (retried by aspect)
     */
    @OAuthResilient
    OAuthUser verifyMicrosoftToken(String microsoftToken);

    /**
     * Verify an Apple ID token and return the user claims.
     *
     * @param appleToken the ID token from the Apple sign-in client
     * @return verified user info (sub, email, name, provider)
     * @throws backend.security.oauth.InvalidOAuthTokenException   if the token is invalid or required claims are missing
     * @throws backend.security.oauth.OAuthProviderTransientException if provider call failed transiently (retried by aspect)
     */
    @OAuthResilient
    OAuthUser verifyAppleToken(String appleToken);
}
