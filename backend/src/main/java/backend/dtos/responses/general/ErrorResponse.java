package backend.dtos.responses.general;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Consistent JSON error body for all API error responses.
 * Optional details map holds provider-specific fields (e.g. errors, allowedMethods).
 */
@Getter
@Setter
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private String detail;
    /** Optional extra fields (e.g. "errors" for validation, "allowedMethods" for 405). */
    private Map<String, Object> details;

    public ErrorResponse() {}

    public ErrorResponse(int status, String message, String detail) {
        this(status, message, detail, null);
    }
}
