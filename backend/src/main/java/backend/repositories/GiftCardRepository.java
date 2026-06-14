package backend.repositories;

import backend.models.core.GiftCard;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface GiftCardRepository extends JpaRepository<GiftCard, UUID> {

    @Query("SELECT g FROM GiftCard g WHERE UPPER(g.code) = UPPER(?1)")
    Optional<GiftCard> findByCode(String code);

    Optional<GiftCard> findByPurchasedOnOrderItemId(UUID orderItemId);

    /** Count of cards already issued for an order item — drives per-unit, redelivery-safe issuance. */
    long countByPurchasedOnOrderItemId(UUID orderItemId);

    Page<GiftCard> findAllByPurchasedByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GiftCard g WHERE g.id = ?1")
    Optional<GiftCard> findByIdWithLock(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GiftCard g WHERE UPPER(g.code) = UPPER(?1)")
    Optional<GiftCard> findByCodeWithLock(String code);
}
