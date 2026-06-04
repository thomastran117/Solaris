package backend.controllers.impl.customers;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.address.CustomerAddressResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.customers.CustomerAddressService;
import backend.testutil.TestIds;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerAddressControllerTest {

    private CustomerAddressService addressService;
    private MockMvc mockMvc;
    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID ADDRESS_ID = TestIds.uuid(2);

    @BeforeEach
    void setUp() {
        addressService = mock(CustomerAddressService.class);
        CustomerAddressController controller = new CustomerAddressController(addressService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /addresses ───────────────────────────────────────────────────────

    @Test
    void listAddresses_returns200() throws Exception {
        when(addressService.listAddresses(USER_ID)).thenReturn(List.of(makeResponse()));

        mockMvc.perform(get("/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ADDRESS_ID.toString()));
    }

    // ─── GET /addresses/{id} ─────────────────────────────────────────────────

    @Test
    void getAddress_returns200() throws Exception {
        when(addressService.getAddress(USER_ID, ADDRESS_ID)).thenReturn(makeResponse());

        mockMvc.perform(get("/addresses/" + ADDRESS_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ADDRESS_ID.toString()));
    }

    @Test
    void getAddress_notFound_returns404() throws Exception {
        when(addressService.getAddress(eq(USER_ID), eq(ADDRESS_ID)))
                .thenThrow(new ResourceNotFoundException("Address not found"));

        mockMvc.perform(get("/addresses/" + ADDRESS_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /addresses ──────────────────────────────────────────────────────

    @Test
    void createAddress_returns201() throws Exception {
        when(addressService.createAddress(eq(USER_ID), any())).thenReturn(makeResponse());

        mockMvc.perform(post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAddressJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ADDRESS_ID.toString()));
    }

    @Test
    void createAddress_serviceThrowsNotFound_returns404() throws Exception {
        when(addressService.createAddress(eq(USER_ID), any()))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAddressJson()))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /addresses/{id} ─────────────────────────────────────────────────

    @Test
    void updateAddress_returns200() throws Exception {
        when(addressService.updateAddress(eq(USER_ID), eq(ADDRESS_ID), any()))
                .thenReturn(makeResponse());

        mockMvc.perform(put("/addresses/" + ADDRESS_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ADDRESS_ID.toString()));
    }

    @Test
    void updateAddress_notFound_returns404() throws Exception {
        when(addressService.updateAddress(eq(USER_ID), eq(ADDRESS_ID), any()))
                .thenThrow(new ResourceNotFoundException("Address not found"));

        mockMvc.perform(put("/addresses/" + ADDRESS_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /addresses/{id} ───────────────────────────────────────────────

    @Test
    void deleteAddress_returns204() throws Exception {
        mockMvc.perform(delete("/addresses/" + ADDRESS_ID))
                .andExpect(status().isNoContent());

        verify(addressService).deleteAddress(USER_ID, ADDRESS_ID);
    }

    @Test
    void deleteAddress_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Address not found"))
                .when(addressService).deleteAddress(USER_ID, ADDRESS_ID);

        mockMvc.perform(delete("/addresses/" + ADDRESS_ID))
                .andExpect(status().isNotFound());
    }

    // ─── PATCH /addresses/{id}/default ───────────────────────────────────────

    @Test
    void setDefault_returns200() throws Exception {
        when(addressService.setDefault(USER_ID, ADDRESS_ID)).thenReturn(makeResponse());

        mockMvc.perform(patch("/addresses/" + ADDRESS_ID + "/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ADDRESS_ID.toString()));
    }

    @Test
    void setDefault_notFound_returns404() throws Exception {
        when(addressService.setDefault(USER_ID, ADDRESS_ID))
                .thenThrow(new ResourceNotFoundException("Address not found"));

        mockMvc.perform(patch("/addresses/" + ADDRESS_ID + "/default"))
                .andExpect(status().isNotFound());
    }

    // ─── error handling ───────────────────────────────────────────────────────

    @Test
    void unexpectedException_returns500() throws Exception {
        when(addressService.listAddresses(USER_ID))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/addresses"))
                .andExpect(status().isInternalServerError());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private CustomerAddressResponse makeResponse() {
        return new CustomerAddressResponse(
                ADDRESS_ID, USER_ID, "Home", "John Doe",
                "123 Main St", null, "New York", "NY", "10001", "US",
                null, false, Instant.now(), Instant.now());
    }

    private String validAddressJson() {
        return """
                {"label":"Home","recipientName":"John Doe","street":"123 Main St",
                 "city":"New York","state":"NY","postalCode":"10001","country":"US"}
                """;
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
