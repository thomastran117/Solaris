package backend.models.core;

import backend.models.enums.DisputeEvidenceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One block of evidence attached to a {@link DisputeCase}, either auto-generated at case creation
 * or added manually by support.
 *
 * <p>{@link #content} is plain text, never HTML — staff paste it straight into Stripe's evidence
 * form.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "dispute_evidence", indexes = {
        @Index(name = "idx_dispute_evidence_case", columnList = "dispute_case_id")
})
public class DisputeEvidence {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_case_id", nullable = false)
    private DisputeCase disputeCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30)
    private DisputeEvidenceType evidenceType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachment_url", nullable = true, length = 500)
    private String attachmentUrl;

    /** Loose FK to {@code users.id}. Null for entries generated from order data at case creation. */
    @Column(name = "created_by_id", nullable = true)
    private UUID createdById;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
