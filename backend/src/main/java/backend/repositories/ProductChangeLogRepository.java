package backend.repositories;

import backend.models.core.ProductChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductChangeLogRepository
        extends JpaRepository<ProductChangeLog, UUID>,
                JpaSpecificationExecutor<ProductChangeLog> {

    Page<ProductChangeLog> findAllByProductIdAndCompanyId(UUID productId, UUID companyId, Pageable pageable);

    List<ProductChangeLog> findAllByIdInAndCompanyId(List<UUID> ids, UUID companyId);
}
