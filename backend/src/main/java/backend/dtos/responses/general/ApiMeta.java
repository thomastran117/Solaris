package backend.dtos.responses.general;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiMeta(
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean hasNext,
        Boolean hasPrevious,
        String nextCursor,
        Boolean hasMore
) {
    public static ApiMeta from(PagedResponse<?> p) {
        return new ApiMeta(
                p.getPage(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages(),
                p.isHasNext(),
                p.isHasPrevious(),
                null,
                null
        );
    }

    public static ApiMeta from(CursorPagedResponse<?> p) {
        return new ApiMeta(
                null,
                p.size(),
                null,
                null,
                null,
                null,
                p.nextCursor(),
                p.hasMore()
        );
    }
}
