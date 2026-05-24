package backend.repositories;

import backend.models.core.ReferralConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReferralConversionRepository extends JpaRepository<ReferralConversion, UUID> {

    long countByReferrerAccountIdAndCompanyId(UUID referrerAccountId, UUID companyId);

    List<ReferralConversion> findByReferrerAccountIdAndCompanyId(UUID referrerAccountId, UUID companyId);

    @Query("SELECT COALESCE(SUM(r.pointsAwarded), 0) FROM ReferralConversion r " +
           "WHERE r.referrerAccountId = :accountId AND r.companyId = :companyId")
    long sumPointsAwardedByReferrerAccountId(@Param("accountId") UUID accountId,
                                             @Param("companyId") UUID companyId);
}
