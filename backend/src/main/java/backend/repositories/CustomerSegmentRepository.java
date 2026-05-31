package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.CustomerSegment;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerSegmentRepository extends JpaRepository<CustomerSegment, UUID> {

    Optional<CustomerSegment> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    Page<CustomerSegment> findAll(Pageable pageable);
}
