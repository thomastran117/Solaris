package backend.repositories;

import backend.models.core.LoyaltyPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoyaltyPolicyRepository extends JpaRepository<LoyaltyPolicy, UUID> {

    Optional<LoyaltyPolicy> findFirstByCompanyIdAndActiveTrue(UUID companyId);

    List<LoyaltyPolicy> findByCompanyId(UUID companyId);

    List<LoyaltyPolicy> findAllByActiveTrue();
}
