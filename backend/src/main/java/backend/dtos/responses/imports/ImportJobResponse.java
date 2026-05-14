package backend.dtos.responses.imports;

import backend.models.enums.ImportJobStatus;
import backend.models.enums.ImportJobType;
import backend.models.enums.ImportMode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ImportJobResponse {
    private long id;
    private long companyId;
    private long uploadedBy;
    private ImportJobType jobType;
    private ImportMode mode;
    private ImportJobStatus status;
    private String fileName;
    private int totalRows;
    private int processedRows;
    private int successRows;
    private int errorRows;
    private int progressPercent;
    private boolean hasErrorReport;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
}
