package backend.models.core;

import backend.models.enums.AnnouncementStatus;
import backend.models.enums.AnnouncementType;
import jakarta.persistence.*;
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
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "company_announcements",
        indexes = {
                @Index(name = "idx_ca_company_status", columnList = "company_id, status"),
                @Index(name = "idx_ca_published_at",   columnList = "published_at")
        }
)
public class CompanyAnnouncement {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AnnouncementStatus status = AnnouncementStatus.DRAFT;

    @Column(name = "product_id", nullable = true)
    private java.util.UUID productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    @Column(name = "published_at", nullable = true)
    private Instant publishedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
