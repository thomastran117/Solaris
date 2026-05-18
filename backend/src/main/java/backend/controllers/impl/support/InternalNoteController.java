package backend.controllers.impl.support;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.note.CreateNoteRequest;
import backend.dtos.responses.note.InternalNoteResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.NoteEntityType;
import backend.services.intf.support.InternalNoteService;

import java.util.List;

@RestController
@RequestMapping("/support/notes")
public class InternalNoteController {

    private final InternalNoteService noteService;

    public InternalNoteController(InternalNoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<InternalNoteResponse> addNote(@Valid @RequestBody CreateNoteRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(noteService.addNote(resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<List<InternalNoteResponse>> listNotes(@RequestParam NoteEntityType entityType,
                                                                 @RequestParam UUID entityId) {
        try {
            return ResponseEntity.ok(noteService.listNotes(resolveUserId(), entityType, entityId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{id}")
    @RequireAuth
    public ResponseEntity<Void> deleteNote(@PathVariable UUID id) {
        try {
            noteService.deleteNote(id, resolveUserId());
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
