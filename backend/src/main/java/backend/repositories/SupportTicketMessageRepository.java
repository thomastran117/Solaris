package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.SupportTicketMessage;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, UUID> {

    List<SupportTicketMessage> findAllByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
