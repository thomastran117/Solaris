package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.OrderIssue;
import backend.models.enums.OrderIssueState;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderIssueRepository extends JpaRepository<OrderIssue, UUID> {

    List<OrderIssue> findAllByOrderId(UUID orderId);

    @Query("SELECT i FROM OrderIssue i WHERE " +
           "(:state IS NULL OR i.state = :state)")
    Page<OrderIssue> findAllByFilters(
            @Param("state") OrderIssueState state,
            Pageable pageable);
}
