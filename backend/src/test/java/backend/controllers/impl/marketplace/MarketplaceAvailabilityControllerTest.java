package backend.controllers.impl.marketplace;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.inventory.AvailabilityEstimateResponse;
import backend.dtos.responses.inventory.NearestSourceResponse;
import backend.dtos.responses.inventory.PickupOptionResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.inventory.AvailabilityEstimateService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketplaceAvailabilityControllerTest {

    private static final UUID MARKETPLACE_ID = TestIds.uuid(1);
    private static final UUID PRODUCT_ID = TestIds.uuid(2);
    private static final UUID VARIANT_ID = TestIds.uuid(3);

    private AvailabilityEstimateService availabilityEstimateService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        availabilityEstimateService = mock(AvailabilityEstimateService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new MarketplaceAvailabilityController(availabilityEstimateService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @Test
    void getAvailability_requiresLatLngTogether() throws Exception {
        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/products/" + PRODUCT_ID + "/availability")
                        .param("lat", "43.65"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAvailability_passesOutOfRangeLatWhenStandaloneMethodValidationIsInactive() throws Exception {
        when(availabilityEstimateService.estimateForMarketplace(
                MARKETPLACE_ID, PRODUCT_ID, null, 91.0, -79.38))
                .thenReturn(response());

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/products/" + PRODUCT_ID + "/availability")
                        .param("lat", "91")
                        .param("lng", "-79.38"))
                .andExpect(status().isOk());

        verify(availabilityEstimateService)
                .estimateForMarketplace(MARKETPLACE_ID, PRODUCT_ID, null, 91.0, -79.38);
    }

    @Test
    void getAvailability_returnsServiceResponse() throws Exception {
        when(availabilityEstimateService.estimateForMarketplace(
                MARKETPLACE_ID, PRODUCT_ID, VARIANT_ID, 43.65, -79.38))
                .thenReturn(response());

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/products/" + PRODUCT_ID + "/availability")
                        .param("variantId", VARIANT_ID.toString())
                        .param("lat", "43.65")
                        .param("lng", "-79.38"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inStock").value(true))
                .andExpect(jsonPath("$.pickup.readyHours").value(4));
    }

    @Test
    void getAvailability_propagatesAppHttpException() throws Exception {
        when(availabilityEstimateService.estimateForMarketplace(
                MARKETPLACE_ID, PRODUCT_ID, null, null, null))
                .thenThrow(new ResourceNotFoundException("Product not found"));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/products/" + PRODUCT_ID + "/availability"))
                .andExpect(status().isNotFound());
    }

    private AvailabilityEstimateResponse response() {
        return new AvailabilityEstimateResponse(
                true,
                new NearestSourceResponse(TestIds.uuid(10), "Downtown", "Toronto", "Canada", 2.1),
                1,
                3,
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 5, 22),
                new PickupOptionResponse(TestIds.uuid(11), "Store", "Toronto", 1.2, 4)
        );
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
