package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import backend.models.core.Company;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, java.util.UUID>, JpaSpecificationExecutor<Company> {
    List<Company> findAllByOwnerId(UUID ownerId);
    Optional<Company> findByIdAndOwnerId(java.util.UUID id, UUID ownerId);
    List<Company> findAllByIdInAndOwnerId(Collection<java.util.UUID> ids, UUID ownerId);
    boolean existsByNameAndOwnerId(String name, UUID ownerId);
    Optional<Company> findByNameAndOwnerId(String name, UUID ownerId);
}
