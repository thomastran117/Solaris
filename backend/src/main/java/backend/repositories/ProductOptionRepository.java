package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.ProductOption;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, java.util.UUID> {
    List<ProductOption> findAllByProductIdOrderByPositionAsc(java.util.UUID productId);
    Optional<ProductOption> findByIdAndProductId(java.util.UUID id, java.util.UUID productId);
    int countByProductId(java.util.UUID productId);
}
