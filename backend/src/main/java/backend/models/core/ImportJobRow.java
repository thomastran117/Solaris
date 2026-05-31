package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Sparse log of failed CSV rows. Successful rows are only counted on the parent
 * {@link ImportJob}, not persisted here — keeps the table small for large imports.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "import_job_rows", indexes = {
        @Index(name = "idx_import_job_row_job", columnList = "job_id"),
        @Index(name = "idx_import_job_row_job_row", columnList = "job_id, row_number")
})
@EntityListeners(AuditingEntityListener.class)
public class ImportJobRow {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @Column(name = "job_id", nullable = false, columnDefinition = "BINARY(16)")
    private java.util.UUID jobId;

    /** 1-based row number in the source CSV (excluding the header). */
    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "sku", nullable = true, length = 100)
    private String sku;

    @Column(name = "error_message", nullable = false, length = 1000)
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
