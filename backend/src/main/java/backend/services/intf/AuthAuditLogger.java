package backend.services.intf;

/**
 * Emits structured authentication-event records to a dedicated SLF4J logger ("AUDIT").
 * Operators can route this logger to a separate appender (file, SIEM, etc.) in logback
 * config without coupling auth code to a particular sink. Implementations must not throw —
 * a failed audit entry must never break the auth flow it is observing.
 */
public interface AuthAuditLogger {

    /** Login/signup/refresh/role-change outcomes that operators may want to query later. */
    enum Event {
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        REFRESH,
        SIGNUP,
        OAUTH_LOGIN_SUCCESS,
        OAUTH_LOGIN_FAILURE,
        DEVICE_VERIFICATION_REQUIRED,
        DEVICE_VERIFIED,
        EMAIL_VERIFIED,
        ROLE_CHANGED
    }

    /**
     * Record an auth event. {@code subject} is the user identifier or email (for failed
     * logins where the userId is unknown). {@code detail} is free-form context (provider,
     * old role -> new role, etc.). All fields may be null.
     */
    void log(Event event, String subject, String detail);
}
