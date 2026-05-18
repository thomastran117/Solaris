package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.ReportReason;
import backend.models.enums.ReportStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "review_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_report_review_reporter",
                columnNames = {"review_id", "reporter_id"}
        ),
        indexes = @Index(name = "idx_report_status", columnList = "status, created_at")
)
@EntityListeners(AuditingEntityListener.class)
public class ReviewReport {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "reporter_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReportReason reason;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", columnDefinition = "BINARY(16)")
    private UUID resolvedBy;
}
