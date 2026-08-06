package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.VendorAuditAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vendor_audit_logs", indexes = {
        @Index(name = "idx_val_vendor", columnList = "marketplace_vendor_id"),
        @Index(name = "idx_val_actor", columnList = "actor_user_id"),
        @Index(name = "idx_val_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class VendorAuditLog {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marketplace_vendor_id", nullable = false)
    private MarketplaceVendor marketplaceVendor;

    /** Null for system-generated events (e.g. scheduled stripe status sync). */
    @Column(nullable = true, name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VendorAuditAction action;

    /** JSON blob of relevant metadata for the action (e.g. old/new status, document type). */
    @Column(nullable = true, columnDefinition = "TEXT")
    private String metadataJson;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
