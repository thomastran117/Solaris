package backend.models.core;

import backend.models.enums.ImportJobStatus;
import backend.models.enums.ImportJobType;
import backend.models.enums.ImportMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "import_jobs", indexes = {
        @Index(name = "idx_import_job_company", columnList = "company_id"),
        @Index(name = "idx_import_job_company_status", columnList = "company_id, status"),
        @Index(name = "idx_import_job_company_created", columnList = "company_id, created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private ImportJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private ImportMode mode = ImportMode.UPSERT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImportJobStatus status = ImportJobStatus.PENDING;

    /** S3 key of the uploaded CSV (or generated CSV for EXPORT jobs). */
    @Column(name = "csv_s3_key", nullable = true, length = 500)
    private String csvS3Key;

    /** Optional original filename — surfaced in the recent-jobs UI. */
    @Column(name = "file_name", nullable = true, length = 255)
    private String fileName;

    @Column(name = "total_rows", nullable = false)
    private int totalRows = 0;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows = 0;

    @Column(name = "success_rows", nullable = false)
    private int successRows = 0;

    @Column(name = "error_rows", nullable = false)
    private int errorRows = 0;

    /** S3 key of the generated error-report CSV. Populated when errorRows > 0. */
    @Column(name = "error_report_s3_key", nullable = true, length = 500)
    private String errorReportS3Key;

    /** Top-level failure message when status == FAILED. */
    @Column(name = "failure_reason", nullable = true, length = 1000)
    private String failureReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
