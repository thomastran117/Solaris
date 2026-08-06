package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.VendorDocumentType;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vendor_onboarding_documents", indexes = {
        @Index(name = "idx_vod_vendor", columnList = "marketplace_vendor_id"),
        @Index(name = "idx_vod_type", columnList = "marketplace_vendor_id, document_type")
})
@EntityListeners(AuditingEntityListener.class)
public class VendorOnboardingDocument {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marketplace_vendor_id", nullable = false)
    private MarketplaceVendor marketplaceVendor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VendorDocumentType documentType;

    /** S3 object key for the uploaded document. */
    @Column(nullable = false, length = 500)
    private String s3Key;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(nullable = true)
    private Instant verifiedAt;

    @Column(nullable = true, length = 500)
    private String rejectionNote;
}
