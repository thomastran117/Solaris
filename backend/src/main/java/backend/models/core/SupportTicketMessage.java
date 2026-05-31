package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.TicketMessageAuthor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "support_ticket_messages", indexes = {
        @Index(name = "idx_ticket_msg_ticket", columnList = "ticket_id")
})
@EntityListeners(AuditingEntityListener.class)
public class SupportTicketMessage {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 4000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketMessageAuthor authorRole;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
