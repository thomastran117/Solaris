package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import backend.models.core.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Optional<User> findByStripeCustomerId(String stripeCustomerId);

    Optional<User> findByPremiumStripeSubscriptionId(String premiumStripeSubscriptionId);

    /** Returns the segment ids a user belongs to. Empty for anonymous/unsegmented users. */
    @Query("SELECT s.id FROM User u JOIN u.segments s WHERE u.id = :userId")
    List<UUID> findSegmentIdsByUserId(@Param("userId") UUID userId);

    /** Returns IDs of users whose birth month and day match today, for birthday reward processing. */
    @Query(value = "SELECT id FROM users WHERE birth_date IS NOT NULL " +
                   "AND MONTH(birth_date) = :month AND DAY(birth_date) = :day",
           nativeQuery = true)
    List<UUID> findUserIdsWithBirthday(@Param("month") int month, @Param("day") int day);
}
