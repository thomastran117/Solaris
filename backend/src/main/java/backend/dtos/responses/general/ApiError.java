package backend.dtos.responses.general;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, Map<String, Object> details) {
    public static ApiError of(String code) {
        return new ApiError(code, null);
    }
}
