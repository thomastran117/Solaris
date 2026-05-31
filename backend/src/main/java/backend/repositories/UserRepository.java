package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import backend.models.core.User;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
