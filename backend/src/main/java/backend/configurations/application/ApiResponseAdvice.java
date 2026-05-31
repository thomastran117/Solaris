package backend.configurations.application;

import backend.dtos.responses.general.ApiMeta;
import backend.dtos.responses.general.ApiResponse;
import backend.dtos.responses.general.CursorPagedResponse;
import backend.dtos.responses.general.PagedResponse;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps every JSON response in an {@link ApiResponse} envelope so the API has a single
 * shape across all endpoints. Bodies that are already {@code ApiResponse} (e.g. from the
 * exception handler) pass through. {@link PagedResponse} and {@link CursorPagedResponse}
 * are unwrapped so the items become {@code data} and pagination becomes {@code meta}.
 */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        String path = request.getURI().getPath();
        if (isExcludedPath(path)) {
            return body;
        }

        if (body instanceof ApiResponse<?>) {
            return body;
        }

        String message = defaultMessageFor(response);

        // Exact-type checks only: subclasses (e.g. CatalogSearchResponse adds `facets`)
        // must keep their extra fields, so they go through the default wrapping branch.
        if (body != null && body.getClass() == PagedResponse.class) {
            PagedResponse<?> paged = (PagedResponse<?>) body;
            return ApiResponse.ok(message, paged.getItems(), ApiMeta.from(paged));
        }

        if (body != null && body.getClass() == CursorPagedResponse.class) {
            CursorPagedResponse<?> cursor = (CursorPagedResponse<?>) body;
            return ApiResponse.ok(message, cursor.items(), ApiMeta.from(cursor));
        }

        return ApiResponse.ok(message, body);
    }

    private static boolean isExcludedPath(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    private static String defaultMessageFor(ServerHttpResponse response) {
        int status = statusCode(response);
        return switch (status) {
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No content";
            default -> "OK";
        };
    }

    private static int statusCode(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servlet) {
            return servlet.getServletResponse().getStatus();
        }
        return 200;
    }
}
