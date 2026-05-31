package backend.services.impl.support;

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
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InternalNoteServiceImplTest {

    private InternalNoteRepository noteRepository;
    private UserRepository userRepository;
    private InternalNoteServiceImpl service;

    @BeforeEach
    void setUp() {
        noteRepository = mock(InternalNoteRepository.class);
        userRepository = mock(UserRepository.class);
        service = new InternalNoteServiceImpl(noteRepository, userRepository);
    }

    // ─── addNote ─────────────────────────────────────────────────────────────

    @Test
    void addNote_staffCanAddNote() {
        User staff = makeUser(TestIds.uuid(1), UserRole.SUPPORT);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(staff));
        when(noteRepository.save(any())).thenAnswer(inv -> {
            InternalNote n = inv.getArgument(0);
            n.setAuthor(staff);
            return n;
        });

        CreateNoteRequest req = new CreateNoteRequest(NoteEntityType.ORDER, TestIds.uuid(10), "Checked with warehouse");
        service.addNote(TestIds.uuid(1), req);

        verify(noteRepository).save(any(InternalNote.class));
    }

    @Test
    void addNote_customerCannotAddNote() {
        User customer = makeUser(TestIds.uuid(2), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(customer));

        CreateNoteRequest req = new CreateNoteRequest(NoteEntityType.ORDER, TestIds.uuid(10), "note");
        assertThrows(ForbiddenException.class, () -> service.addNote(TestIds.uuid(2), req));
    }

    // ─── listNotes ───────────────────────────────────────────────────────────

    @Test
    void listNotes_staffSeeNotes() {
        User staff = makeUser(TestIds.uuid(1), UserRole.SUPPORT);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(staff));
        when(noteRepository.findAllByEntityTypeAndEntityIdOrderByCreatedAtAsc(NoteEntityType.TICKET, TestIds.uuid(5)))
                .thenReturn(List.of());

        List<InternalNoteResponse> notes = service.listNotes(TestIds.uuid(1), NoteEntityType.TICKET, TestIds.uuid(5));
        assertNotNull(notes);
        assertTrue(notes.isEmpty());
    }

    @Test
    void listNotes_customerForbidden() {
        User customer = makeUser(TestIds.uuid(2), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(customer));

        assertThrows(ForbiddenException.class,
                () -> service.listNotes(TestIds.uuid(2), NoteEntityType.TICKET, TestIds.uuid(5)));
    }

    // ─── deleteNote ──────────────────────────────────────────────────────────

    @Test
    void deleteNote_authorCanDelete() {
        User staff = makeUser(TestIds.uuid(1), UserRole.SUPPORT);
        InternalNote note = makeNote(TestIds.uuid(10), staff);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(staff));
        when(noteRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(note));

        service.deleteNote(TestIds.uuid(10), TestIds.uuid(1));
        verify(noteRepository).delete(note);
    }

    @Test
    void deleteNote_adminCanDeleteOthersNotes() {
        User author = makeUser(TestIds.uuid(1), UserRole.SUPPORT);
        User admin  = makeUser(TestIds.uuid(2), UserRole.ADMIN);
        InternalNote note = makeNote(TestIds.uuid(10), author);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(admin));
        when(noteRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(note));

        service.deleteNote(TestIds.uuid(10), TestIds.uuid(2));
        verify(noteRepository).delete(note);
    }

    @Test
    void deleteNote_nonAuthorStaffForbidden() {
        User author = makeUser(TestIds.uuid(1), UserRole.SUPPORT);
        User other  = makeUser(TestIds.uuid(3), UserRole.SUPPORT);
        InternalNote note = makeNote(TestIds.uuid(10), author);
        when(userRepository.findById(TestIds.uuid(3))).thenReturn(Optional.of(other));
        when(noteRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(note));

        assertThrows(ForbiddenException.class, () -> service.deleteNote(TestIds.uuid(10), TestIds.uuid(3)));
    }

    @Test
    void deleteNote_throwsWhenNoteNotFound() {
        User staff = makeUser(TestIds.uuid(1), UserRole.SUPPORT);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(staff));
        when(noteRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteNote(TestIds.uuid(99), TestIds.uuid(1)));
    }

    // ─── addNote — additional roles & response fields ─────────────────────────

    @Test
    void addNote_adminCanAddNote() {
        User admin = makeUser(TestIds.uuid(3), UserRole.ADMIN);
        when(userRepository.findById(TestIds.uuid(3))).thenReturn(Optional.of(admin));
        when(noteRepository.save(any())).thenAnswer(inv -> {
            InternalNote n = inv.getArgument(0);
            n.setAuthor(admin);
            return n;
        });

        CreateNoteRequest req = new CreateNoteRequest(NoteEntityType.TICKET, TestIds.uuid(10), "Admin note");
        assertDoesNotThrow(() -> service.addNote(TestIds.uuid(3), req));
        verify(noteRepository).save(any(InternalNote.class));
    }

    @Test
    void addNote_returnsResponseWithCorrectFields() {
        User staff = makeUser(TestIds.uuid(1), UserRole.SUPPORT);
        staff.setFirstName("Jane");
        staff.setLastName("Doe");
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(staff));
        when(noteRepository.save(any())).thenAnswer(inv -> {
            InternalNote n = inv.getArgument(0);
            n.setId(TestIds.uuid(50));
            n.setAuthor(staff);
            return n;
        });

        CreateNoteRequest req = new CreateNoteRequest(NoteEntityType.ORDER, TestIds.uuid(20), "Checked with warehouse");
        InternalNoteResponse resp = service.addNote(TestIds.uuid(1), req);

        assertEquals(TestIds.uuid(50), resp.getId());
        assertEquals("Checked with warehouse", resp.getBody());
        assertEquals("Jane Doe", resp.getAuthorName());
        assertEquals(TestIds.uuid(1), resp.getAuthorId());
        assertEquals("ORDER", resp.getEntityType());
        assertEquals(TestIds.uuid(20), resp.getEntityId());
    }

    // ─── listNotes — returns content ──────────────────────────────────────────

    @Test
    void listNotes_returnsAllNotesForEntity() {
        User staff = makeUser(TestIds.uuid(1), UserRole.SUPPORT);
        InternalNote note1 = makeNote(TestIds.uuid(10), staff);
        InternalNote note2 = makeNote(TestIds.uuid(11), staff);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(staff));
        when(noteRepository.findAllByEntityTypeAndEntityIdOrderByCreatedAtAsc(NoteEntityType.TICKET, TestIds.uuid(5)))
                .thenReturn(List.of(note1, note2));

        List<InternalNoteResponse> notes = service.listNotes(TestIds.uuid(1), NoteEntityType.TICKET, TestIds.uuid(5));

        assertEquals(2, notes.size());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User makeUser(UUID id, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@test.com");
        u.setRole(role);
        return u;
    }

    private InternalNote makeNote(UUID id, User author) {
        InternalNote n = new InternalNote();
        n.setId(id);
        n.setAuthor(author);
        n.setEntityType(NoteEntityType.ORDER);
        n.setEntityId(TestIds.uuid(1));
        n.setBody("test note");
        return n;
    }
}
