package backend.models.core;

import backend.models.enums.MfaType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "mfa_credentials",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_mfa_user_type",
        columnNames = {"user_id", "type"}
    )
)
public class MfaCredential {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MfaType type;

    @Column(nullable = false)
    private boolean verified;

    // Whether this method is currently active. A user can disable a method from their MFA
    // settings without unenrolling (which would discard the TOTP secret / require re-scanning).
    @Column(nullable = false)
    private boolean enabled = true;

    // Base32 TOTP secret. Null for EMAIL and SMS.
    // TODO: encrypt at rest using an AttributeConverter once key management is in place.
    @Column(length = 512)
    private String secret;

    // E.164 phone for SMS, email address for EMAIL, null for TOTP.
    @Column(length = 320)
    private String target;

    // BCrypt-hashed backup codes for TOTP. Each code is single-use (consumed when wiring auth flow).
    @ElementCollection
    @CollectionTable(
        name = "mfa_credential_backup_codes",
        joinColumns = @JoinColumn(name = "credential_id")
    )
    @Column(name = "code_hash", nullable = false, length = 72)
    private List<String> backupCodeHashes = new ArrayList<>();

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
