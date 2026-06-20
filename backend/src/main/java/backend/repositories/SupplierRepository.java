package backend.repositories;

import backend.models.core.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    Page<Supplier> findAllByCompanyId(UUID companyId, Pageable pageable);
    Optional<Supplier> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndEmail(UUID companyId, String email);
}
