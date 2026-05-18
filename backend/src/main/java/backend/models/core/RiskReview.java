package backend.models.core;

import backend.models.enums.RiskReviewStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Merchant review queue row. One per UNDER_REVIEW order. Created when the engine returns
 * BLOCK in ENFORCE mode; merchants approve/reject via {@code CompanyOrderController}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "risk_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uq_risk_review_order", columnNames = "order_id"),
        indexes = @Index(name = "idx_risk_review_status", columnList = "status"))
@EntityListeners(AuditingEntityListener.class)
public class RiskReview {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    /** Order held under review. Unique to prevent duplicate queue rows. */
    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    /** FK to the {@code risk_assessments} row that produced this review (not a JPA relationship). */
    @Column(name = "assessment_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID assessmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskReviewStatus status = RiskReviewStatus.PENDING;

    /** User id of the merchant who approved/rejected. Null while PENDING. */
    @Column(nullable = true, columnDefinition = "BINARY(16)")
    private UUID decidedByUserId;

    @Column(nullable = true)
    private Instant decidedAt;

    @Column(nullable = true, length = 500)
    private String merchantNote;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
