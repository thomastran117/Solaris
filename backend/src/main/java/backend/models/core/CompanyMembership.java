package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "company_memberships",
        indexes = {
                @Index(name = "ix_membership_user_status", columnList = "user_id,status"),
                @Index(name = "ix_membership_company_status", columnList = "company_id,status"),
                @Index(name = "ix_membership_invite_token", columnList = "invite_token")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class CompanyMembership {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Null while invite is PENDING and the invitee hasn't accepted yet. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    /** Populated for PENDING invites; retained on ACTIVE rows for reference. */
    @Column(name = "invite_email", length = 255, nullable = true)
    private String inviteEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyMembershipStatus status;

    @Column(name = "invite_token", length = 64, unique = true, nullable = true)
    private String inviteToken;

    @Column(name = "invite_expires_at", nullable = true)
    private Instant inviteExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "invited_by", nullable = true)
    private User invitedBy;

    @Column(name = "accepted_at", nullable = true)
    private Instant acceptedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
