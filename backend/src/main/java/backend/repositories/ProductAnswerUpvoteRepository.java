package backend.repositories;

import backend.models.core.ProductAnswerUpvote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductAnswerUpvoteRepository extends JpaRepository<ProductAnswerUpvote, UUID> {
    boolean existsByAnswerIdAndUserId(UUID answerId, UUID userId);
}
