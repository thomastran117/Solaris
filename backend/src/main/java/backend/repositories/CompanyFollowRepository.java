package backend.repositories;

import backend.models.core.CompanyFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyFollowRepository extends JpaRepository<CompanyFollow, UUID> {

    Optional<CompanyFollow> findByUserIdAndCompanyId(UUID userId, UUID companyId);

    boolean existsByUserIdAndCompanyId(UUID userId, UUID companyId);

    long countByCompanyId(UUID companyId);

    Page<CompanyFollow> findAllByCompanyIdAndNotificationsEnabledTrue(UUID companyId, Pageable pageable);

    @Query("SELECT f FROM CompanyFollow f JOIN FETCH f.company WHERE f.user.id = :userId")
    Page<CompanyFollow> findAllByUserIdWithCompany(@Param("userId") UUID userId, Pageable pageable);
}
