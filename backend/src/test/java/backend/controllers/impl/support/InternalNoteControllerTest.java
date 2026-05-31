package backend.controllers.impl.support;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.note.CreateNoteRequest;
import backend.dtos.responses.note.InternalNoteResponse;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.NoteEntityType;
import backend.services.intf.support.InternalNoteService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalNoteControllerTest {

    private static final UUID USER_ID   = TestIds.uuid(1);
    private static final UUID NOTE_ID   = TestIds.uuid(2);
    private static final UUID ENTITY_ID = TestIds.uuid(3);

    private InternalNoteService noteService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        noteService = mock(InternalNoteService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new InternalNoteController(noteService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── POST /support/notes ──────────────────────────────────────────────────

    @Test
    void addNote_returns201() throws Exception {
        when(noteService.addNote(eq(USER_ID), any())).thenReturn(makeNoteResponse());

        mockMvc.perform(post("/support/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateNoteRequest(NoteEntityType.TICKET, ENTITY_ID, "Check the order logs"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Internal note body"));
    }

    @Test
    void addNote_customerForbidden_returns403() throws Exception {
        when(noteService.addNote(any(), any())).thenThrow(new ForbiddenException());

        mockMvc.perform(post("/support/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addNote_serviceError_returns500() throws Exception {
        when(noteService.addNote(any(), any())).thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(post("/support/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError());
    }

    // ─── GET /support/notes ───────────────────────────────────────────────────

    @Test
    void listNotes_returns200() throws Exception {
        when(noteService.listNotes(eq(USER_ID), eq(NoteEntityType.TICKET), eq(ENTITY_ID)))
                .thenReturn(List.of(makeNoteResponse()));

        mockMvc.perform(get("/support/notes")
                        .param("entityType", "TICKET")
                        .param("entityId", ENTITY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Internal note body"));
    }

    @Test
    void listNotes_customerForbidden_returns403() throws Exception {
        when(noteService.listNotes(any(), any(), any())).thenThrow(new ForbiddenException());

        mockMvc.perform(get("/support/notes")
                        .param("entityType", "ORDER")
                        .param("entityId", ENTITY_ID.toString()))
                .andExpect(status().isForbidden());
    }

    // ─── DELETE /support/notes/{id} ───────────────────────────────────────────

    @Test
    void deleteNote_returns204() throws Exception {
        mockMvc.perform(delete("/support/notes/{id}", NOTE_ID))
                .andExpect(status().isNoContent());

        verify(noteService).deleteNote(NOTE_ID, USER_ID);
    }

    @Test
    void deleteNote_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("note not found"))
                .when(noteService).deleteNote(any(), any());

        mockMvc.perform(delete("/support/notes/{id}", NOTE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteNote_nonAuthorForbidden_returns403() throws Exception {
        doThrow(new ForbiddenException()).when(noteService).deleteNote(any(), any());

        mockMvc.perform(delete("/support/notes/{id}", NOTE_ID))
                .andExpect(status().isForbidden());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private InternalNoteResponse makeNoteResponse() {
        return new InternalNoteResponse(
                NOTE_ID, "TICKET", ENTITY_ID,
                USER_ID, "Jane Doe", "Internal note body", Instant.now());
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
