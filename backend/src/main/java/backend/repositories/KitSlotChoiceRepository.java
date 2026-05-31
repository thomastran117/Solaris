package backend.repositories;

import backend.models.core.KitSlotChoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KitSlotChoiceRepository extends JpaRepository<KitSlotChoice, UUID> {
    List<KitSlotChoice> findAllBySlotId(UUID slotId);
    boolean existsBySlotIdAndProductIdAndVariantId(UUID slotId, UUID productId, UUID variantId);
}
