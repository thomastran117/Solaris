package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.IssueResolution;
import backend.models.enums.OrderIssueState;
import backend.models.enums.OrderIssueType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "order_issues", indexes = {
        @Index(name = "idx_issue_order", columnList = "order_id"),
        @Index(name = "idx_issue_ticket", columnList = "ticket_id"),
        @Index(name = "idx_issue_state", columnList = "state")
})
@EntityListeners(AuditingEntityListener.class)
public class OrderIssue {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = true)
    private SupportTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by_id", nullable = false)
    private User reportedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderIssueType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderIssueState state = OrderIssueState.REPORTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 15)
    private IssueResolution resolution;

    @Column(nullable = true, length = 2000)
    private String description;

    @Column(nullable = true, length = 500)
    private String rejectionReason;

    /** Loose FK to returns.id — set when resolved via refund. */
    @Column(nullable = true)
    private Long returnId;

    /** Loose FK to orders.id — set when resolved via replacement order. */
    @Column(nullable = true)
    private Long replacementOrderId;

    /** Loose FK to customer_credits.id — set when resolved via store credit. */
    @Column(nullable = true, columnDefinition = "BINARY(16)")
    private UUID customerCreditId;

    /** Loose FK to sub_orders.id — set for issues on marketplace vendor sub-orders. */
    @Column(nullable = true)
    private Long subOrderId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = true)
    private Instant resolvedAt;
}
