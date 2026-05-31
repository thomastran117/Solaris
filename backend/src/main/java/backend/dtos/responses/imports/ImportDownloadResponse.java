package backend.dtos.responses.imports;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImportDownloadResponse {
    /** Presigned GET URL the client can fetch directly. */
    private String url;
    /** Seconds until {@link #url} expires. */
    private int expiresIn;
}
