package backend.exceptions.http;

import org.springframework.http.HttpStatus;

/**
 * Thrown by {@code OrderServiceImpl.createOrder} when the risk engine returns VERIFY and
 * the caller has not yet supplied a valid verification token. The client is expected to
 * read the token from the email dispatched by {@code EmailVerificationService} and retry
 * the same request with {@code riskVerificationToken} set.
 *
 * <p>HTTP 428 (Precondition Required) is used because the server is asking the client to
 * satisfy a pre-condition (email-verified step-up) before the request can succeed.
 */
public class RiskStepUpRequiredException extends AppHttpException {
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.PRECONDITION_REQUIRED;
    private static final String DEFAULT_MESSAGE = "Additional verification required";
    private static final String DEFAULT_DETAIL =
            "Please retrieve the verification code emailed to you and resubmit with riskVerificationToken.";

    private final java.util.UUID orderId;
    private final String verificationChannel;

    public RiskStepUpRequiredException(java.util.UUID orderId) {
        this(orderId, "EMAIL");
    }

    public RiskStepUpRequiredException(java.util.UUID orderId, String verificationChannel) {
        super(DEFAULT_STATUS, DEFAULT_MESSAGE, null, DEFAULT_DETAIL);
        this.orderId = orderId;
        this.verificationChannel = verificationChannel;
    }

    public java.util.UUID getOrderId() {
        return orderId;
    }

    public String getVerificationChannel() {
        return verificationChannel;
    }
}
