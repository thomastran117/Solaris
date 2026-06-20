package backend.repositories;

import backend.models.core.B2BAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2BAccountRepository extends JpaRepository<B2BAccount, UUID> {
    Optional<B2BAccount> findByUserId(UUID userId);
}
