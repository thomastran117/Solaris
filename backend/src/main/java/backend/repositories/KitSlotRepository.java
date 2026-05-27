package backend.repositories;

import backend.models.core.KitSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KitSlotRepository extends JpaRepository<KitSlot, UUID> {
    List<KitSlot> findAllByKitId(UUID kitId);
}
