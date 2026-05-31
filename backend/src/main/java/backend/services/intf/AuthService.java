package backend.services.intf;

/**
 * Handles authentication flows (login, refresh). Delegates JWT operations to {@link TokenService}
 * and user operations to {@link UserService}.
 */
public interface AuthService {

    /**
     * Result of a successful login: tokens and user info for the response.
     */
    record LoginResult(String accessToken, String refreshToken, String email, String usertype, long userId) {}

    /**
     * Result of a login attempt. When {@code deviceVerificationRequired} is true, no tokens
     * are issued — a verification email has been sent and {@code loginResult} is null.
     */
    record LoginAttemptResult(boolean deviceVerificationRequired, LoginResult loginResult) {
        public static LoginAttemptResult success(LoginResult result) {
            return new LoginAttemptResult(false, result);
        }
        public static LoginAttemptResult pendingVerification() {
            return new LoginAttemptResult(true, null);
        }
    }

    /**
     * Result of a successful token refresh: new access token and refresh token.
     */
    record RefreshResult(String accessToken, String refreshToken, long expiresInSeconds) {}

    /**
     * Authenticate user with email/password. Returns a LoginAttemptResult — either tokens
     * (known device) or a device-verification-required flag (unknown device).
     */
    LoginAttemptResult localAuthenicate(String email, String password);

    /**
     * Validate refresh token and issue new access + refresh token pair.
     */
    RefreshResult refresh(String refreshToken);

    /**
     * Authenticate via Google (verify id token, then login or signup).
     */
    LoginAttemptResult googleAuthenicate(String token);

    /**
     * Authenticate via Microsoft (verify id token, then login or signup).
     */
    LoginAttemptResult microsoftAuthenticate(String token);

    /**
     * Authenticate via Apple (verify id token, then login or signup).
     */
    LoginAttemptResult appleAuthenticate(String token);

    /**
     * Consume a device verification token from Redis, register the device as trusted,
     * and issue tokens. Throws BadRequestException if the token is missing or expired.
     */
    LoginResult verifyDevice(String token);

    /**
     * Result of a successful signup: email and confirmation message.
     * No tokens are issued — the user must verify their email first.
     */
    record SignupResult(String email, String message) {}

    /**
     * Register a new user with status PENDING_VERIFICATION, generate a verification token,
     * and send a verification email asynchronously.
     */
    SignupResult signup(String email, String password, String usertype);

    /**
     * Consume a verification token from Redis and activate the user account.
     * Throws BadRequestException if the token is missing or expired.
     */
    void verifyEmail(String token);

    /**
     * Revoke a single refresh token (e.g. logout). No-op if token already invalid.
     */
    void revokeRefreshToken(String refreshToken);

    /**
     * Revoke all refresh tokens for a user (e.g. security reset, password change).
     */
    void revokeAllRefreshTokensForUser(int userId);
}
