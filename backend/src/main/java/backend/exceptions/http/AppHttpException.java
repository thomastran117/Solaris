package backend.exceptions.http;

import org.springframework.http.HttpStatus;

public abstract class AppHttpException extends RuntimeException {

    private final HttpStatus status;
    private final String message;
    private final String detail;

    protected AppHttpException(HttpStatus status, String defaultMessage) {
        this(status, defaultMessage, null);
    }

    protected AppHttpException(HttpStatus status, String defaultMessage, String messageOverride) {
        this(status, defaultMessage, messageOverride, null);
    }

    protected AppHttpException(
            HttpStatus status,
            String defaultMessage,
            String messageOverride,
            String detail
    ) {
        super(detail != null ? detail : (messageOverride != null ? messageOverride : defaultMessage));
        this.status = status;
        this.message = messageOverride != null ? messageOverride : defaultMessage;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getDetail() {
        return detail;
    }
}
