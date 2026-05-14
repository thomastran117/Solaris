package backend.repositories;

import backend.models.core.ProductChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductChangeLogRepository
        extends JpaRepository<ProductChangeLog, Long>,
                JpaSpecificationExecutor<ProductChangeLog> {

    Page<ProductChangeLog> findAllByProductIdAndCompanyId(long productId, long companyId, Pageable pageable);

    List<ProductChangeLog> findAllByIdInAndCompanyId(List<Long> ids, long companyId);
}
