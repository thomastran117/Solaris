package backend.services.impl.support;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.note.CreateNoteRequest;
import backend.dtos.responses.note.InternalNoteResponse;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.InternalNote;
import backend.models.core.User;
import backend.models.enums.NoteEntityType;
import backend.models.enums.UserRole;
import backend.repositories.InternalNoteRepository;
import backend.repositories.UserRepository;
import backend.services.intf.support.InternalNoteService;
import backend.utilities.SecurityUtils;

import java.util.List;

@Service
public class InternalNoteServiceImpl implements InternalNoteService {

    private static final Logger log = LoggerFactory.getLogger(InternalNoteServiceImpl.class);

    private final InternalNoteRepository noteRepository;
    private final UserRepository userRepository;

    public InternalNoteServiceImpl(InternalNoteRepository noteRepository, UserRepository userRepository) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public InternalNoteResponse addNote(UUID authorUserId, CreateNoteRequest request) {
        User author = userRepository.findById(authorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authorUserId));
        SecurityUtils.requireStaff(author);

        InternalNote note = new InternalNote();
        note.setEntityType(request.entityType());
        note.setEntityId(request.entityId());
        note.setAuthor(author);
        note.setBody(request.body());

        noteRepository.save(note);
        log.debug("Staff {} added internal note on {} {}", authorUserId, request.entityType(), request.entityId());
        return toResponse(note);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalNoteResponse> listNotes(UUID actorUserId, NoteEntityType entityType, long entityId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        return noteRepository
                .findAllByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteNote(UUID noteId, UUID actorUserId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        InternalNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found: " + noteId));

        boolean isAuthor = note.getAuthor().getId().equals(actorUserId);
        boolean isAdmin = actor.getRole() == UserRole.ADMIN;
        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException();
        }

        noteRepository.delete(note);
    }

    private InternalNoteResponse toResponse(InternalNote note) {
        User author = note.getAuthor();
        String authorName = formatName(author);
        return new InternalNoteResponse(
                note.getId(),
                note.getEntityType().name(),
                note.getEntityId(),
                author.getId(),
                authorName,
                note.getBody(),
                note.getCreatedAt()
        );
    }

    private String formatName(User user) {
        if (user.getFirstName() != null && user.getLastName() != null) {
            return user.getFirstName() + " " + user.getLastName();
        }
        return user.getEmail();
    }
}
