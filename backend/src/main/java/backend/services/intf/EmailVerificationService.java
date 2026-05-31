package backend.services.intf;

public interface EmailVerificationService {

    /**
     * Generates a UUID token, stores it in Redis mapped to the userId with a configurable TTL,
     * then dispatches the verification email asynchronously.
     *
     * @param userId the newly created user's DB id
     * @param email  the address to send the verification email to
     */
    void initiateVerification(long userId, String email);

    /**
     * Atomically consumes the verification token from Redis and returns the associated userId.
     * Throws {@link backend.exceptions.http.BadRequestException} if the token is missing,
     * expired, or malformed.
     *
     * @param token the raw UUID token from the query parameter
     * @return the userId associated with the token
     */
    long consumeVerificationToken(String token);
}
