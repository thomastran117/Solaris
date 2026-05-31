package backend.repositories;

import backend.models.core.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(UUID userId);

    Optional<CustomerAddress> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    Optional<CustomerAddress> findByUserIdAndIsDefaultTrue(UUID userId);

    @Modifying
    @Query("UPDATE CustomerAddress a SET a.isDefault = false WHERE a.user.id = :userId")
    void clearDefaultForUser(@Param("userId") UUID userId);
}
