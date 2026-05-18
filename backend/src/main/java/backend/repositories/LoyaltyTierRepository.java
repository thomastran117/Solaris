package backend.repositories;

import backend.models.core.LoyaltyTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoyaltyTierRepository extends JpaRepository<LoyaltyTier, UUID> {

    List<LoyaltyTier> findByCompanyIdOrderByMinPointsAsc(UUID companyId);

    List<LoyaltyTier> findByCompanyIdOrderByMinPointsDesc(UUID companyId);
}
