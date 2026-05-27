package backend.repositories;

import backend.models.core.ProductRelationship;
import backend.models.enums.ProductRelationshipType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRelationshipRepository extends JpaRepository<ProductRelationship, UUID> {

    List<ProductRelationship> findAllBySourceProductIdAndSourceProductCompanyId(UUID productId, UUID companyId);

    List<ProductRelationship> findAllBySourceProductIdAndTypeAndSourceProductCompanyId(
            UUID productId, ProductRelationshipType type, UUID companyId);

    boolean existsBySourceProductIdAndTargetProductIdAndType(
            UUID sourceProductId, UUID targetProductId, ProductRelationshipType type);

    void deleteBySourceProductIdAndTargetProductIdAndType(
            UUID sourceProductId, UUID targetProductId, ProductRelationshipType type);
}
