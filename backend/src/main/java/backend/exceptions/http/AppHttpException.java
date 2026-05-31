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

    /**
     * Stable machine-readable code for this error, used in the ApiResponse envelope.
     * Default: derive from the simple class name by stripping the trailing "Exception"
     * and converting to UPPER_SNAKE_CASE (e.g. ResourceNotFoundException -> RESOURCE_NOT_FOUND).
     * Subclasses may override for a more specific code.
     */
    public String errorCode() {
        String simple = getClass().getSimpleName();
        if (simple.endsWith("Exception")) {
            simple = simple.substring(0, simple.length() - "Exception".length());
        }
        StringBuilder sb = new StringBuilder(simple.length() + 4);
        for (int i = 0; i < simple.length(); i++) {
            char c = simple.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }
}
