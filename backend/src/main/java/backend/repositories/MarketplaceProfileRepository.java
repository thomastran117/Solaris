package backend.repositories;

import backend.models.core.MarketplaceProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceProfileRepository extends JpaRepository<MarketplaceProfile, UUID> {

    Optional<MarketplaceProfile> findByCompanyId(UUID companyId);

    Optional<MarketplaceProfile> findBySlug(String slug);

    boolean existsByCompanyId(UUID companyId);

    boolean existsBySlug(String slug);
}
