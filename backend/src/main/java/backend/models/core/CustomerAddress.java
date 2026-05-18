package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "customer_addresses", indexes = {
        @Index(name = "idx_ca_user_id", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
public class CustomerAddress {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false, length = 150)
    private String recipientName;

    @Column(nullable = false, length = 255)
    private String street;

    @Column(nullable = true, length = 255)
    private String street2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(nullable = false, length = 20)
    private String postalCode;

    /** ISO 3166-1 alpha-2 country code (e.g. "US", "GB"). */
    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = true, length = 30)
    private String phoneNumber;

    @Column(nullable = false)
    private boolean isDefault = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
