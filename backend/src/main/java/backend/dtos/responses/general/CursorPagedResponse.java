package backend.dtos.responses.general;

import java.util.List;

public record CursorPagedResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore,
        int size
) {}
