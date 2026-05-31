package backend.dtos.requests.imports;

import backend.models.enums.ImportJobType;
import backend.models.enums.ImportMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateImportJobRequest {

    @NotNull(message = "jobType is required")
    private ImportJobType jobType;

    /** Optional — defaults to UPSERT for PRODUCT_UPSERT. Ignored for INVENTORY_SYNC. */
    private ImportMode mode;

    @NotBlank(message = "csvS3Key is required")
    @Size(max = 500)
    private String csvS3Key;

    @Size(max = 255)
    private String fileName;
}
