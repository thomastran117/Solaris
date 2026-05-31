package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import backend.models.core.Company;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {
    List<Company> findAllByOwnerId(long ownerId);
    Optional<Company> findByIdAndOwnerId(long id, long ownerId);
    List<Company> findAllByIdInAndOwnerId(Collection<Long> ids, long ownerId);
    boolean existsByNameAndOwnerId(String name, long ownerId);
}
