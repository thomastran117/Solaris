package backend.services.impl.customers;

import backend.dtos.requests.address.CreateCustomerAddressRequest;
import backend.dtos.requests.address.UpdateCustomerAddressRequest;
import backend.dtos.responses.address.CustomerAddressResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CustomerAddress;
import backend.models.core.User;
import backend.repositories.CustomerAddressRepository;
import backend.repositories.UserRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerAddressServiceImplTest {

    private CustomerAddressRepository addressRepository;
    private UserRepository userRepository;
    private CustomerAddressServiceImpl service;

    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID ADDRESS_ID = TestIds.uuid(2);

    @BeforeEach
    void setUp() {
        addressRepository = mock(CustomerAddressRepository.class);
        userRepository    = mock(UserRepository.class);
        service = new CustomerAddressServiceImpl(addressRepository, userRepository);
    }

    // ─── listAddresses ────────────────────────────────────────────────────────

    @Test
    void listAddresses_returnsAllMappedResponses() {
        CustomerAddress a = makeAddress(ADDRESS_ID, USER_ID);
        when(addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID))
                .thenReturn(List.of(a));

        List<CustomerAddressResponse> result = service.listAddresses(USER_ID);

        assertEquals(1, result.size());
        assertEquals(ADDRESS_ID, result.get(0).getId());
    }

    @Test
    void listAddresses_emptyList_returnsEmpty() {
        when(addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID))
                .thenReturn(List.of());

        assertTrue(service.listAddresses(USER_ID).isEmpty());
    }

    // ─── getAddress ───────────────────────────────────────────────────────────

    @Test
    void getAddress_found_returnsMappedResponse() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(makeAddress(ADDRESS_ID, USER_ID)));

        CustomerAddressResponse result = service.getAddress(USER_ID, ADDRESS_ID);

        assertEquals(ADDRESS_ID, result.getId());
        assertEquals(USER_ID, result.getUserId());
    }

    @Test
    void getAddress_notFound_throwsResourceNotFound() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getAddress(USER_ID, ADDRESS_ID));
    }

    // ─── createAddress ────────────────────────────────────────────────────────

    @Test
    void createAddress_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createAddress(USER_ID, validCreateRequest(false)));
    }

    @Test
    void createAddress_firstAddress_forcedDefault() {
        User user = makeUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID))
                .thenReturn(List.of()); // no existing
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createAddress(USER_ID, validCreateRequest(false)); // isDefault=false

        ArgumentCaptor<CustomerAddress> captor = ArgumentCaptor.forClass(CustomerAddress.class);
        verify(addressRepository).save(captor.capture());
        assertTrue(captor.getValue().isDefault());
    }

    @Test
    void createAddress_subsequentAddress_notForcedDefault() {
        User user = makeUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID))
                .thenReturn(List.of(makeAddress(ADDRESS_ID, USER_ID))); // existing address
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createAddress(USER_ID, validCreateRequest(false));

        ArgumentCaptor<CustomerAddress> captor = ArgumentCaptor.forClass(CustomerAddress.class);
        verify(addressRepository).save(captor.capture());
        assertFalse(captor.getValue().isDefault());
    }

    @Test
    void createAddress_isDefaultTrue_clearsPreviousDefault() {
        User user = makeUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID))
                .thenReturn(List.of(makeAddress(ADDRESS_ID, USER_ID)));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createAddress(USER_ID, validCreateRequest(true));

        verify(addressRepository).clearDefaultForUser(USER_ID);
    }

    @Test
    void createAddress_setsAllFieldsFromRequest() {
        User user = makeUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID))
                .thenReturn(List.of(makeAddress(ADDRESS_ID, USER_ID)));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateCustomerAddressRequest req = validCreateRequest(false);
        service.createAddress(USER_ID, req);

        ArgumentCaptor<CustomerAddress> captor = ArgumentCaptor.forClass(CustomerAddress.class);
        verify(addressRepository).save(captor.capture());
        CustomerAddress saved = captor.getValue();
        assertEquals(req.getLabel(), saved.getLabel());
        assertEquals(req.getStreet(), saved.getStreet());
        assertEquals(req.getCity(), saved.getCity());
        assertEquals(req.getCountry(), saved.getCountry());
    }

    // ─── updateAddress ────────────────────────────────────────────────────────

    @Test
    void updateAddress_notFound_throwsResourceNotFound() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateAddress(USER_ID, ADDRESS_ID, new UpdateCustomerAddressRequest()));
    }

    @Test
    void updateAddress_updatesNonNullFields() {
        CustomerAddress address = makeAddress(ADDRESS_ID, USER_ID);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCustomerAddressRequest req = new UpdateCustomerAddressRequest();
        req.setLabel("Office");

        service.updateAddress(USER_ID, ADDRESS_ID, req);

        assertEquals("Office", address.getLabel());
    }

    @Test
    void updateAddress_allNullFields_changesNothing() {
        CustomerAddress address = makeAddress(ADDRESS_ID, USER_ID);
        String originalLabel = address.getLabel();
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateAddress(USER_ID, ADDRESS_ID, new UpdateCustomerAddressRequest());

        assertEquals(originalLabel, address.getLabel());
    }

    // ─── deleteAddress ────────────────────────────────────────────────────────

    @Test
    void deleteAddress_notFound_throwsResourceNotFound() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteAddress(USER_ID, ADDRESS_ID));
    }

    @Test
    void deleteAddress_nonDefault_deletesWithoutPromotion() {
        CustomerAddress address = makeAddress(ADDRESS_ID, USER_ID);
        address.setDefault(false);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));

        service.deleteAddress(USER_ID, ADDRESS_ID);

        verify(addressRepository).delete(address);
        // No promotion: findAll not called again after delete
        verify(addressRepository, never()).findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID);
    }

    @Test
    void deleteAddress_default_promotesNextOldest() {
        CustomerAddress address = makeAddress(ADDRESS_ID, USER_ID);
        address.setDefault(true);
        CustomerAddress next = makeAddress(TestIds.uuid(3), USER_ID);
        next.setDefault(false);

        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));
        when(addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID))
                .thenReturn(List.of(next)); // remaining after delete
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deleteAddress(USER_ID, ADDRESS_ID);

        assertTrue(next.isDefault());
        verify(addressRepository).save(next);
    }

    @Test
    void deleteAddress_default_noRemainingAddresses_noPromotion() {
        CustomerAddress address = makeAddress(ADDRESS_ID, USER_ID);
        address.setDefault(true);

        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));
        when(addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID))
                .thenReturn(List.of()); // no remaining

        service.deleteAddress(USER_ID, ADDRESS_ID);

        verify(addressRepository, never()).save(any());
    }

    // ─── setDefault ───────────────────────────────────────────────────────────

    @Test
    void setDefault_notFound_throwsResourceNotFound() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.setDefault(USER_ID, ADDRESS_ID));
    }

    @Test
    void setDefault_clearsAllThenSetsNewDefault() {
        CustomerAddress address = makeAddress(ADDRESS_ID, USER_ID);
        address.setDefault(false);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setDefault(USER_ID, ADDRESS_ID);

        verify(addressRepository).clearDefaultForUser(USER_ID);
        assertTrue(address.isDefault());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CustomerAddress makeAddress(UUID id, UUID userId) {
        User user = makeUser(userId);
        CustomerAddress a = new CustomerAddress();
        a.setId(id);
        a.setUser(user);
        a.setLabel("Home");
        a.setRecipientName("John Doe");
        a.setStreet("123 Main St");
        a.setCity("New York");
        a.setState("NY");
        a.setPostalCode("10001");
        a.setCountry("US");
        return a;
    }

    private User makeUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setEmail("user@test.com");
        return u;
    }

    private CreateCustomerAddressRequest validCreateRequest(boolean isDefault) {
        CreateCustomerAddressRequest req = new CreateCustomerAddressRequest();
        req.setLabel("Home");
        req.setRecipientName("John Doe");
        req.setStreet("123 Main St");
        req.setCity("New York");
        req.setState("NY");
        req.setPostalCode("10001");
        req.setCountry("US");
        req.setDefault(isDefault);
        return req;
    }
}
