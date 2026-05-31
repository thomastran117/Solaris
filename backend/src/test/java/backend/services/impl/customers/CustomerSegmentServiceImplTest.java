package backend.services.impl.customers;

import backend.dtos.requests.segment.CreateCustomerSegmentRequest;
import backend.dtos.requests.segment.UpdateCustomerSegmentRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.segment.CustomerSegmentResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CustomerSegment;
import backend.models.core.User;
import backend.repositories.CustomerSegmentRepository;
import backend.repositories.UserRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerSegmentServiceImplTest {

    private CustomerSegmentRepository segmentRepository;
    private UserRepository userRepository;
    private CustomerSegmentServiceImpl service;

    private static final UUID SEGMENT_ID = TestIds.uuid(1);
    private static final UUID USER_ID    = TestIds.uuid(2);

    @BeforeEach
    void setUp() {
        segmentRepository = mock(CustomerSegmentRepository.class);
        userRepository    = mock(UserRepository.class);
        service = new CustomerSegmentServiceImpl(segmentRepository, userRepository);
    }

    // ─── listSegments ─────────────────────────────────────────────────────────

    @Test
    void listSegments_returnsPaginatedResponse() {
        CustomerSegment seg = makeSegment(SEGMENT_ID, "VIP");
        when(segmentRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(seg), PageRequest.of(0, 20), 1));

        PagedResponse<CustomerSegmentResponse> result = service.listSegments(0, 20);

        assertEquals(1, result.getItems().size());
        assertEquals(1, result.getTotalElements());
        assertEquals(SEGMENT_ID, result.getItems().get(0).id());
    }

    // ─── getSegment ───────────────────────────────────────────────────────────

    @Test
    void getSegment_found_returnsResponse() {
        when(segmentRepository.findById(SEGMENT_ID))
                .thenReturn(Optional.of(makeSegment(SEGMENT_ID, "VIP")));

        CustomerSegmentResponse result = service.getSegment(SEGMENT_ID);

        assertEquals(SEGMENT_ID, result.id());
        assertEquals("VIP", result.code());
    }

    @Test
    void getSegment_notFound_throwsResourceNotFound() {
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getSegment(SEGMENT_ID));
    }

    // ─── createSegment ────────────────────────────────────────────────────────

    @Test
    void createSegment_duplicateCode_throwsConflict() {
        when(segmentRepository.existsByCodeIgnoreCase("VIP")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.createSegment(createRequest("vip", "VIP Members")));
    }

    @Test
    void createSegment_savesWithUppercaseCode() {
        when(segmentRepository.existsByCodeIgnoreCase("VIP")).thenReturn(false);
        when(segmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createSegment(createRequest("vip", "VIP Members"));

        ArgumentCaptor<CustomerSegment> captor = ArgumentCaptor.forClass(CustomerSegment.class);
        verify(segmentRepository).save(captor.capture());
        assertEquals("VIP", captor.getValue().getCode());
    }

    @Test
    void createSegment_savesNameAndDescription() {
        when(segmentRepository.existsByCodeIgnoreCase(any())).thenReturn(false);
        when(segmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateCustomerSegmentRequest req = createRequest("PREMIUM", "Premium Customers");
        req.setDescription("Top-tier buyers");
        service.createSegment(req);

        ArgumentCaptor<CustomerSegment> captor = ArgumentCaptor.forClass(CustomerSegment.class);
        verify(segmentRepository).save(captor.capture());
        assertEquals("Premium Customers", captor.getValue().getName());
        assertEquals("Top-tier buyers", captor.getValue().getDescription());
    }

    // ─── updateSegment ────────────────────────────────────────────────────────

    @Test
    void updateSegment_notFound_throwsResourceNotFound() {
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateSegment(SEGMENT_ID, new UpdateCustomerSegmentRequest()));
    }

    @Test
    void updateSegment_updatesNameAndDescription() {
        CustomerSegment seg = makeSegment(SEGMENT_ID, "VIP");
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(segmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCustomerSegmentRequest req = new UpdateCustomerSegmentRequest();
        req.setName("VIP Members");
        req.setDescription("Our best customers");
        service.updateSegment(SEGMENT_ID, req);

        assertEquals("VIP Members", seg.getName());
        assertEquals("Our best customers", seg.getDescription());
    }

    @Test
    void updateSegment_nullFields_noChange() {
        CustomerSegment seg = makeSegment(SEGMENT_ID, "VIP");
        seg.setName("Original Name");
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(segmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateSegment(SEGMENT_ID, new UpdateCustomerSegmentRequest());

        assertEquals("Original Name", seg.getName());
    }

    // ─── deleteSegment ────────────────────────────────────────────────────────

    @Test
    void deleteSegment_notFound_throwsResourceNotFound() {
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteSegment(SEGMENT_ID));
    }

    @Test
    void deleteSegment_deletesSegment() {
        CustomerSegment seg = makeSegment(SEGMENT_ID, "VIP");
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));

        service.deleteSegment(SEGMENT_ID);

        verify(segmentRepository).delete(seg);
    }

    // ─── assignSegmentToUser ──────────────────────────────────────────────────

    @Test
    void assignSegmentToUser_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.assignSegmentToUser(USER_ID, SEGMENT_ID));
    }

    @Test
    void assignSegmentToUser_segmentNotFound_throwsResourceNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(makeUser(USER_ID)));
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.assignSegmentToUser(USER_ID, SEGMENT_ID));
    }

    @Test
    void assignSegmentToUser_addsSegmentAndSavesUser() {
        User user = makeUser(USER_ID);
        CustomerSegment seg = makeSegment(SEGMENT_ID, "VIP");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));

        service.assignSegmentToUser(USER_ID, SEGMENT_ID);

        assertTrue(user.getSegments().contains(seg));
        verify(userRepository).save(user);
    }

    // ─── removeSegmentFromUser ────────────────────────────────────────────────

    @Test
    void removeSegmentFromUser_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.removeSegmentFromUser(USER_ID, SEGMENT_ID));
    }

    @Test
    void removeSegmentFromUser_removesSegmentAndSavesUser() {
        User user = makeUser(USER_ID);
        CustomerSegment seg = makeSegment(SEGMENT_ID, "VIP");
        user.getSegments().add(seg);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.removeSegmentFromUser(USER_ID, SEGMENT_ID);

        assertFalse(user.getSegments().contains(seg));
        verify(userRepository).save(user);
    }

    @Test
    void removeSegmentFromUser_segmentNotPresent_savesUserAnyway() {
        User user = makeUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.removeSegmentFromUser(USER_ID, SEGMENT_ID);

        verify(userRepository).save(user);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CustomerSegment makeSegment(UUID id, String code) {
        CustomerSegment s = new CustomerSegment();
        s.setId(id);
        s.setCode(code);
        s.setName(code + " Members");
        return s;
    }

    private User makeUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setEmail("user@test.com");
        return u;
    }

    private CreateCustomerSegmentRequest createRequest(String code, String name) {
        CreateCustomerSegmentRequest req = new CreateCustomerSegmentRequest();
        req.setCode(code);
        req.setName(name);
        return req;
    }
}
