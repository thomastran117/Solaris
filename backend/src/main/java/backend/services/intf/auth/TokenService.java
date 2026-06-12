package backend.services.intf.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public interface TokenService {

    UUID getUserIdFromToken(HttpServletRequest request);

    <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver);

    Authentication getAuthentication(String token);

    String generateAccessToken(UUID userId, String role, String email);

    /**
     * Creates an opaque refresh token and stores it in cache (revocable). Not a JWT.
     */
    String generateRefreshToken(UUID userId, String role, String email);

    boolean validateRefreshToken(String token);

    /**
     * Payload stored with a refresh token in cache.
     */
    record RefreshTokenPayload(UUID userId, String role, String email) {}

    /**
     * Returns payload for a valid cache-backed refresh token, or null if invalid/expired/revoked.
     * Does NOT consume the token — use {@link #consumeRefreshToken} for one-time consumption.
     */
    RefreshTokenPayload getRefreshTokenPayload(String refreshToken);

    /**
     * Atomically removes the token from cache (GETDEL) and cleans up the user-set index.
     * Returns the stored payload, or null if the token was not found, already consumed, or expired.
     * Exactly one concurrent caller receives a non-null result.
     */
    RefreshTokenPayload consumeRefreshToken(String token);

    /**
     * Invalidates the old refresh token and issues a new one (rotation). Old token must be valid.
     */
    String rotateRefreshToken(String oldToken);

    Map<String, Object> generateTokenPair(UUID userId, String role, String email);

    long getAccessTokenExpiresInSeconds();

    /**
     * Revoke a single refresh token (e.g. on logout).
     */
    void revokeRefreshToken(String refreshToken);

    /**
     * Revoke all refresh tokens for a user (e.g. security reset, password change).
     */
    void revokeAllRefreshTokensForUser(UUID userId);

    /**
     * Adds the raw access token to a short-lived blacklist so it cannot be re-used after
     * logout, even within its remaining TTL. The blacklist entry expires after the
     * configured access-token TTL, at which point the token would have expired anyway.
     */
    void blacklistAccessToken(String rawToken);

    /** Returns true if this access token has been explicitly revoked via {@link #blacklistAccessToken}. */
    boolean isAccessTokenBlacklisted(String rawToken);
}
