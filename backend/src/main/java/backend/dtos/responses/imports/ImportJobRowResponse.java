package backend.dtos.responses.imports;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImportJobRowResponse {
    private long id;
    private long jobId;
    private int rowNumber;
    private String sku;
    private String errorMessage;
}
