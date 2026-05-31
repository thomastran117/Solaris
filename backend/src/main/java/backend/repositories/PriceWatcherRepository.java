package backend.repositories;

import backend.models.core.PriceWatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceWatcherRepository extends JpaRepository<PriceWatcher, UUID> {

    Optional<PriceWatcher> findByUserIdAndProductId(UUID userId, UUID productId);

    List<PriceWatcher> findAllByProductIdAndWatchPriceCentsGreaterThan(UUID productId, int newPriceCents);

    Page<PriceWatcher> findAllByUserId(UUID userId, Pageable pageable);

    void deleteByUserIdAndProductId(UUID userId, UUID productId);
}
