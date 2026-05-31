package backend.repositories;

import backend.models.core.CompanyReturnLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyReturnLocationRepository extends JpaRepository<CompanyReturnLocation, java.util.UUID> {

    List<CompanyReturnLocation> findAllByCompanyId(java.util.UUID companyId);

    Optional<CompanyReturnLocation> findByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);

    long countByCompanyId(java.util.UUID companyId);

    /**
     * Returns the primary location if one is set, otherwise the first location by insertion order.
     * Single query using ORDER BY is_primary DESC so this replaces a two-query fallback chain.
     */
    Optional<CompanyReturnLocation> findFirstByCompanyIdOrderByPrimaryDescIdAsc(java.util.UUID companyId);

    @Modifying
    @Query("UPDATE CompanyReturnLocation l SET l.primary = false WHERE l.company.id = :companyId")
    void clearPrimaryForCompany(@Param("companyId") java.util.UUID companyId);
}
