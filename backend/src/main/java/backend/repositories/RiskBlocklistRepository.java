package backend.repositories;

import backend.models.core.RiskBlocklist;
import backend.models.enums.RiskBlocklistType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskBlocklistRepository extends JpaRepository<RiskBlocklist, UUID> {

    /**
     * Returns an unexpired entry for the given (type, value) pair, if any.
     * A null {@code expiresAt} is treated as permanent.
     */
    @Query("SELECT b FROM RiskBlocklist b WHERE b.type = :type AND b.value = :value " +
           "AND (b.expiresAt IS NULL OR b.expiresAt > :now)")
    Optional<RiskBlocklist> findActive(
            @Param("type") RiskBlocklistType type,
            @Param("value") String value,
            @Param("now") Instant now);
}
