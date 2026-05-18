package backend.repositories;

import backend.models.core.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {
    Optional<UserDevice> findByUserIdAndFingerprint(UUID userId, String fingerprint);
    List<UserDevice> findByUserId(UUID userId);

    /** Counts how many distinct user ids have seen this fingerprint — feeds multi-account velocity. */
    @Query("SELECT COUNT(DISTINCT d.user.id) FROM UserDevice d WHERE d.fingerprint = :fingerprint")
    long countDistinctUserIdByFingerprint(@Param("fingerprint") String fingerprint);

    /** Number of device rows with this fingerprint created after {@code since} — used for 1h bursts. */
    long countByFingerprintAndCreatedAtAfter(String fingerprint, Instant since);
}
