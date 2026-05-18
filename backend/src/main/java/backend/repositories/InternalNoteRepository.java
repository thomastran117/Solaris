package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.InternalNote;
import backend.models.enums.NoteEntityType;

import java.util.List;
import java.util.UUID;

@Repository
public interface InternalNoteRepository extends JpaRepository<InternalNote, UUID> {

    List<InternalNote> findAllByEntityTypeAndEntityIdOrderByCreatedAtAsc(
            NoteEntityType entityType, UUID entityId);
}
