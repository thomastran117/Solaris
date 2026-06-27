package backend.repositories;

import backend.models.core.MfaCredential;
import backend.models.enums.MfaType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MfaCredentialRepository extends JpaRepository<MfaCredential, UUID> {

    List<MfaCredential> findByUserId(UUID userId);

    Optional<MfaCredential> findByUserIdAndType(UUID userId, MfaType type);
}
