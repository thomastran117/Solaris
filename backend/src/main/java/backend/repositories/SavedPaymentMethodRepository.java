package backend.repositories;

import backend.models.core.SavedPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedPaymentMethodRepository extends JpaRepository<SavedPaymentMethod, UUID> {

    List<SavedPaymentMethod> findAllByUserId(UUID userId);

    Optional<SavedPaymentMethod> findByStripePaymentMethodId(String stripePaymentMethodId);

    Optional<SavedPaymentMethod> findByUserIdAndIsDefaultTrue(UUID userId);
}
