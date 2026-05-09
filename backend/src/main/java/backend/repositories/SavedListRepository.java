package backend.repositories;

import backend.models.core.SavedList;
import backend.models.core.SavedListType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedListRepository extends JpaRepository<SavedList, Long> {

    List<SavedList> findAllByUserIdOrderByCreatedAtDesc(long userId);

    List<SavedList> findAllByUserIdAndTypeOrderByCreatedAtDesc(long userId, SavedListType type);

    Optional<SavedList> findByIdAndUserId(long id, long userId);

    Optional<SavedList> findByShareSlug(String shareSlug);

    boolean existsByNameAndUserIdAndType(String name, long userId, SavedListType type);
}
