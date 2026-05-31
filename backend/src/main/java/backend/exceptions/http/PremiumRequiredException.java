package backend.exceptions.http;

import org.springframework.http.HttpStatus;

public class PremiumRequiredException extends AppHttpException {
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.PAYMENT_REQUIRED;
    private static final String DEFAULT_MESSAGE = "A Premium subscription is required to use this feature";

    public PremiumRequiredException() {
        super(DEFAULT_STATUS, DEFAULT_MESSAGE);
    }

    public PremiumRequiredException(String detail) {
        super(DEFAULT_STATUS, DEFAULT_MESSAGE, null, detail);
    }
}
