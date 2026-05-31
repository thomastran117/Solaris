package backend.models.core;

import backend.models.enums.RiskBlocklistType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Manual blocklist. A matching entry forces the engine to return BLOCK immediately —
 * evaluators are skipped. Populated by support/ops via SQL until an admin UI is built.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "risk_blocklist",
        uniqueConstraints = @UniqueConstraint(name = "uq_risk_blocklist_type_value", columnNames = {"type", "value"}),
        indexes = @Index(name = "idx_risk_blocklist_type", columnList = "type"))
@EntityListeners(AuditingEntityListener.class)
public class RiskBlocklist {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskBlocklistType type;

    @Column(nullable = false, length = 320)
    private String value;

    @Column(nullable = true, length = 255)
    private String reason;

    /** Null = permanent. Past timestamps are treated as expired (ignored). */
    @Column(nullable = true)
    private Instant expiresAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
