package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.InventoryLocation;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryLocationRepository extends JpaRepository<InventoryLocation, java.util.UUID> {

    List<InventoryLocation> findAllByCompanyIdOrderByDisplayOrderAscNameAsc(java.util.UUID companyId);

    Optional<InventoryLocation> findByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);

    boolean existsByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);

    boolean existsByCodeAndCompanyId(String code, java.util.UUID companyId);

    boolean existsByCodeAndCompanyIdAndIdNot(String code, java.util.UUID companyId, java.util.UUID excludeId);
}
