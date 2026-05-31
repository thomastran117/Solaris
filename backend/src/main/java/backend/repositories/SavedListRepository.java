package backend.repositories;

import backend.models.core.SavedList;
import backend.models.core.SavedListType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedListRepository extends JpaRepository<SavedList, java.util.UUID> {

    List<SavedList> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SavedList> findAllByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, SavedListType type);

    Optional<SavedList> findByIdAndUserId(java.util.UUID id, UUID userId);

    Optional<SavedList> findByShareSlug(String shareSlug);

    boolean existsByNameAndUserIdAndType(String name, UUID userId, SavedListType type);

    long countByUserId(UUID userId);
}
