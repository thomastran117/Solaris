package backend.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.SupportTicket;
import backend.models.enums.TicketStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SupportTicket t WHERE t.id = :id")
    Optional<SupportTicket> findByIdForUpdate(@Param("id") UUID id);

    Page<SupportTicket> findAllByCustomerId(UUID customerId, Pageable pageable);

    @Query("SELECT t FROM SupportTicket t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:assignedToId IS NULL OR t.assignedTo.id = :assignedToId)")
    Page<SupportTicket> findAllByFilters(
            @Param("status") TicketStatus status,
            @Param("assignedToId") UUID assignedToId,
            Pageable pageable);

    long countByCustomerIdAndStatusNot(UUID customerId, TicketStatus status);

    /** Every ticket raised against an order — the customer-communication evidence for a chargeback. */
    List<SupportTicket> findAllByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
