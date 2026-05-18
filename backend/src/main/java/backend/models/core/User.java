package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = true)
    private String password;

    @Column(unique = true, nullable = true)
    private String googleId;

    @Column(unique = true, nullable = true)
    private String microsoftId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = true)
    private Instant lastLoginAt;

    @Column(nullable = true, length = 100)
    private String firstName;

    @Column(nullable = true, length = 100)
    private String lastName;

    @Column(nullable = true, length = 30)
    private String phoneNumber;

    @Column(nullable = true, length = 255)
    private String address;

    /**
     * Stripe Customer ID linked to this user. Provisioned lazily the first time
     * the user saves a payment method or creates a subscription. Null until then.
     */
    @Column(nullable = true, length = 100, unique = true)
    private String stripeCustomerId;

    /** Date of birth. Used by the LoyaltyScheduler for birthday rewards. Null if not provided. */
    @Column(nullable = true, name = "birth_date")
    private LocalDate birthDate;

    /**
     * Customer segments this user belongs to (VIP, WHOLESALE, …).
     * Used by PricingEngine to gate segment-targeted promotion rules.
     * Assigned manually by platform admins.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_segments",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "segment_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_segment",
                    columnNames = {"user_id", "segment_id"})
    )
    private Set<CustomerSegment> segments = new HashSet<>();
}
