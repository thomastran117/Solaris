package backend.repositories;

import backend.models.core.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByUserIdAndFingerprint(long userId, String fingerprint);
    List<UserDevice> findByUserId(long userId);
}
