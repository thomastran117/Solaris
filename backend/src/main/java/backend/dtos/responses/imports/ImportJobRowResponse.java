package backend.dtos.responses.imports;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ImportJobRowResponse {
    private UUID id;
    private UUID jobId;
    private int rowNumber;
    private String sku;
    private String errorMessage;
}
