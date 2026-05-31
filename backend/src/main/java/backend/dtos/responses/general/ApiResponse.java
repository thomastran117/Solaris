package backend.dtos.responses.general;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        ApiError error,
        ApiMeta meta
) {
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data, ApiMeta meta) {
        return new ApiResponse<>(true, message, data, null, meta);
    }

    public static ApiResponse<Void> error(String message, ApiError error) {
        return new ApiResponse<>(false, message, null, error, null);
    }
}
