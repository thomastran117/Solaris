package backend.services.intf;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.function.Function;

public interface TokenService {

    int getUserIdFromToken(HttpServletRequest request);

    <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver);

    Authentication getAuthentication(String token);

    String generateAccessToken(int userId, String role, String email);

    /**
     * Creates an opaque refresh token and stores it in cache (revocable). Not a JWT.
     */
    String generateRefreshToken(int userId, String role, String email);

    boolean validateRefreshToken(String token);

    /**
     * Payload stored with a refresh token in cache.
     */
    record RefreshTokenPayload(int userId, String role, String email) {}

    /**
     * Returns payload for a valid cache-backed refresh token, or null if invalid/expired/revoked.
     */
    RefreshTokenPayload getRefreshTokenPayload(String refreshToken);

    /**
     * Invalidates the old refresh token and issues a new one (rotation). Old token must be valid.
     */
    String rotateRefreshToken(String oldToken);

    Map<String, Object> generateTokenPair(int userId, String role, String email);

    long getAccessTokenExpiresInSeconds();

    /**
     * Revoke a single refresh token (e.g. on logout).
     */
    void revokeRefreshToken(String refreshToken);

    /**
     * Revoke all refresh tokens for a user (e.g. security reset, password change).
     */
    void revokeAllRefreshTokensForUser(int userId);
}
