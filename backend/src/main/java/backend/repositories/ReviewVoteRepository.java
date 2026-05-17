package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.ReviewVote;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReviewVoteRepository extends JpaRepository<ReviewVote, Long> {

    boolean existsByReviewIdAndUserId(long reviewId, long userId);

    @Modifying
    @Query("DELETE FROM ReviewVote v WHERE v.reviewId = :reviewId AND v.userId = :userId")
    int deleteByReviewIdAndUserId(@Param("reviewId") long reviewId, @Param("userId") long userId);

    @Query("SELECT v.reviewId FROM ReviewVote v WHERE v.userId = :userId AND v.reviewId IN :reviewIds")
    List<Long> findVotedReviewIds(@Param("userId") long userId, @Param("reviewIds") Collection<Long> reviewIds);
}
