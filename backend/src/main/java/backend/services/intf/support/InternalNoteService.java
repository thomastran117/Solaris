package backend.services.intf.support;

import java.util.UUID;
import backend.dtos.requests.note.CreateNoteRequest;
import backend.dtos.responses.note.InternalNoteResponse;
import backend.models.enums.NoteEntityType;

import java.util.List;

public interface InternalNoteService {

    /** Staff-only: add an internal note to a ticket, order, user, or order issue. */
    InternalNoteResponse addNote(UUID authorUserId, CreateNoteRequest request);

    /** Staff-only: list all internal notes for a given entity. */
    List<InternalNoteResponse> listNotes(UUID actorUserId, NoteEntityType entityType, UUID entityId);

    /** Staff-only: delete a note. Author or admin may delete. */
    void deleteNote(UUID noteId, UUID actorUserId);
}
