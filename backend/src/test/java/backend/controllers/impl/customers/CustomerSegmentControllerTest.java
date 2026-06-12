package backend.controllers.impl.customers;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.segment.CustomerSegmentResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.customers.CustomerSegmentService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerSegmentControllerTest {

    private CustomerSegmentService segmentService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID SEGMENT_ID = TestIds.uuid(1);
    private static final UUID USER_ID    = TestIds.uuid(2);

    @BeforeEach
    void setUp() {
        segmentService = mock(CustomerSegmentService.class);
        CustomerSegmentController controller = new CustomerSegmentController(segmentService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    // ─── GET /admin/customer-segments ────────────────────────────────────────

    @Test
    void listSegments_returns200WithPagedBody() throws Exception {
        when(segmentService.listSegments(0, 20))
                .thenReturn(new PagedResponse<>(
                        new PageImpl<>(List.of(makeResponse()), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/admin/customer-segments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(SEGMENT_ID.toString()));
    }

    // ─── GET /admin/customer-segments/{id} ───────────────────────────────────

    @Test
    void getSegment_returns200() throws Exception {
        when(segmentService.getSegment(SEGMENT_ID)).thenReturn(makeResponse());

        mockMvc.perform(get("/admin/customer-segments/" + SEGMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEGMENT_ID.toString()));
    }

    @Test
    void getSegment_notFound_returns404() throws Exception {
        when(segmentService.getSegment(SEGMENT_ID))
                .thenThrow(new ResourceNotFoundException("Segment not found"));

        mockMvc.perform(get("/admin/customer-segments/" + SEGMENT_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /admin/customer-segments ───────────────────────────────────────

    @Test
    void createSegment_returns201() throws Exception {
        when(segmentService.createSegment(any())).thenReturn(makeResponse());

        mockMvc.perform(post("/admin/customer-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "VIP", "name", "VIP Members"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SEGMENT_ID.toString()));
    }

    @Test
    void createSegment_duplicate_returns409() throws Exception {
        when(segmentService.createSegment(any()))
                .thenThrow(new ConflictException("Segment code already exists"));

        mockMvc.perform(post("/admin/customer-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "VIP", "name", "VIP Members"))))
                .andExpect(status().isConflict());
    }

    // ─── PATCH /admin/customer-segments/{id} ─────────────────────────────────

    @Test
    void updateSegment_returns200() throws Exception {
        when(segmentService.updateSegment(eq(SEGMENT_ID), any())).thenReturn(makeResponse());

        mockMvc.perform(patch("/admin/customer-segments/" + SEGMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEGMENT_ID.toString()));
    }

    @Test
    void updateSegment_notFound_returns404() throws Exception {
        when(segmentService.updateSegment(eq(SEGMENT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Segment not found"));

        mockMvc.perform(patch("/admin/customer-segments/" + SEGMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Updated"))))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /admin/customer-segments/{id} ────────────────────────────────

    @Test
    void deleteSegment_returns204() throws Exception {
        mockMvc.perform(delete("/admin/customer-segments/" + SEGMENT_ID))
                .andExpect(status().isNoContent());

        verify(segmentService).deleteSegment(SEGMENT_ID);
    }

    @Test
    void deleteSegment_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Segment not found"))
                .when(segmentService).deleteSegment(SEGMENT_ID);

        mockMvc.perform(delete("/admin/customer-segments/" + SEGMENT_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /admin/users/{userId}/segments/{segmentId} ─────────────────────

    @Test
    void assignSegment_returns204() throws Exception {
        mockMvc.perform(post("/admin/users/" + USER_ID + "/segments/" + SEGMENT_ID))
                .andExpect(status().isNoContent());

        verify(segmentService).assignSegmentToUser(USER_ID, SEGMENT_ID);
    }

    @Test
    void assignSegment_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("User not found"))
                .when(segmentService).assignSegmentToUser(USER_ID, SEGMENT_ID);

        mockMvc.perform(post("/admin/users/" + USER_ID + "/segments/" + SEGMENT_ID))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /admin/users/{userId}/segments/{segmentId} ───────────────────

    @Test
    void removeSegment_returns204() throws Exception {
        mockMvc.perform(delete("/admin/users/" + USER_ID + "/segments/" + SEGMENT_ID))
                .andExpect(status().isNoContent());

        verify(segmentService).removeSegmentFromUser(USER_ID, SEGMENT_ID);
    }

    // ─── error handling ───────────────────────────────────────────────────────

    @Test
    void unexpectedException_returns500() throws Exception {
        when(segmentService.getSegment(SEGMENT_ID))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/admin/customer-segments/" + SEGMENT_ID))
                .andExpect(status().isInternalServerError());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CustomerSegmentResponse makeResponse() {
        return new CustomerSegmentResponse(SEGMENT_ID, "VIP", "VIP Members", null,
                Instant.now(), Instant.now());
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
