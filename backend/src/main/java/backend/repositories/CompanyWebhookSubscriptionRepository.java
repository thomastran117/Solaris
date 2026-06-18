package backend.repositories;

import backend.models.core.CompanyWebhookSubscription;
import backend.models.enums.WebhookEventType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyWebhookSubscriptionRepository extends JpaRepository<CompanyWebhookSubscription, UUID> {

    List<CompanyWebhookSubscription> findAllByCompanyId(UUID companyId);

    long countByCompanyId(UUID companyId);

    /** Acquires a pessimistic write lock on all subscription rows for this company,
     *  serialising concurrent registrations so the 5-subscription cap cannot be bypassed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CompanyWebhookSubscription s where s.company.id = :companyId")
    List<CompanyWebhookSubscription> findAllByCompanyIdWithLock(@Param("companyId") UUID companyId);

    Optional<CompanyWebhookSubscription> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("select s from CompanyWebhookSubscription s join s.events e " +
           "where s.company.id = :companyId and e = :eventType " +
           "and s.status = backend.models.enums.WebhookSubscriptionStatus.ACTIVE")
    List<CompanyWebhookSubscription> findActiveByCompanyIdAndEventType(
            @Param("companyId") UUID companyId,
            @Param("eventType") WebhookEventType eventType);
}
