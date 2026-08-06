package backend.models.core;

import backend.models.enums.RiskAction;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskMode;
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
 * Immutable audit row produced by every call to the risk engine. Persisted even in
 * SHADOW mode so we can reconstruct what the engine would have done vs. what the
 * order flow actually did.
 *
 * <p>{@code reasonsJson} is a compact serialisation of the per-signal rows returned
 * by the engine. Kept as an opaque blob — the review UI parses it client-side.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "risk_assessments", indexes = {
        @Index(name = "idx_risk_assessment_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_risk_assessment_order", columnList = "order_id")
})
@EntityListeners(AuditingEntityListener.class)
public class RiskAssessment {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    /** Null for assessments captured before the order row exists (not currently used, reserved for future). */
    @Column(name = "order_id", nullable = true)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskAction decision;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskAssessmentKind kind;

    /** Serialised {@code List<RiskSignal>}. Review APIs re-parse this to present reasons to merchants. */
    @Column(nullable = true, columnDefinition = "TEXT")
    private String reasonsJson;

    @Column(nullable = true, length = 45)
    private String ip;

    @Column(nullable = true, length = 64)
    private String deviceFingerprint;

    @Column(nullable = true, length = 512)
    private String userAgent;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
