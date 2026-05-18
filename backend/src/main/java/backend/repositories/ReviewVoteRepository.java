package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.ReviewVote;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewVoteRepository extends JpaRepository<ReviewVote, UUID> {

    boolean existsByReviewIdAndUserId(UUID reviewId, UUID userId);

    @Modifying
    @Query("DELETE FROM ReviewVote v WHERE v.reviewId = :reviewId AND v.userId = :userId")
    int deleteByReviewIdAndUserId(@Param("reviewId") UUID reviewId, @Param("userId") UUID userId);

    @Query("SELECT v.reviewId FROM ReviewVote v WHERE v.userId = :userId AND v.reviewId IN :reviewIds")
    List<UUID> findVotedReviewIds(@Param("userId") UUID userId, @Param("reviewIds") Collection<UUID> reviewIds);
}
