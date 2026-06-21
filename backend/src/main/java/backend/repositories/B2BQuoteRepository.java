package backend.repositories;

import backend.models.core.B2BQuote;
import backend.models.enums.QuoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2BQuoteRepository extends JpaRepository<B2BQuote, UUID> {

    @EntityGraph(attributePaths = {"items"})
    Optional<B2BQuote> findByIdAndBuyerUserId(UUID id, UUID buyerUserId);

    @EntityGraph(attributePaths = {"items"})
    Optional<B2BQuote> findByIdAndVendorCompanyId(UUID id, UUID vendorCompanyId);

    Page<B2BQuote> findByBuyerUserId(UUID buyerUserId, Pageable pageable);

    Page<B2BQuote> findByBuyerUserIdAndStatus(UUID buyerUserId, QuoteStatus status, Pageable pageable);

    Page<B2BQuote> findByVendorCompanyId(UUID vendorCompanyId, Pageable pageable);

    Page<B2BQuote> findByVendorCompanyIdAndStatus(UUID vendorCompanyId, QuoteStatus status, Pageable pageable);

    /** Quotes awaiting buyer action that have passed their expiry — used by the expiry scheduler. */
    List<B2BQuote> findByStatusAndExpiresAtBefore(QuoteStatus status, Instant cutoff);
}
