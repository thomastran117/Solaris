package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.ProductImage;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, java.util.UUID> {

    List<ProductImage> findAllByProductIdOrderByDisplayOrderAsc(java.util.UUID productId);

    List<ProductImage> findAllByProductIdInOrderByDisplayOrderAsc(Collection<java.util.UUID> productIds);

    Optional<ProductImage> findByIdAndProductId(java.util.UUID id, java.util.UUID productId);

    int countByProductId(java.util.UUID productId);
}
