package backend.controllers.impl.returns;

import backend.annotations.safeText.SafeTextValidator;
import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.return_.ReturnLocationResponse;
import backend.services.intf.SanitizationService;
import backend.services.intf.returns.ReturnLocationService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyReturnLocationControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID LOCATION_ID = TestIds.uuid(3);

    private ReturnLocationService returnLocationService;
    private SanitizationService sanitizationService;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        returnLocationService = mock(ReturnLocationService.class);
        sanitizationService = mock(SanitizationService.class);
        when(sanitizationService.isSafePlainText(any())).thenReturn(true);

        validator = new LocalValidatorFactoryBean();
        validator.setConstraintValidatorFactory(new InjectingConstraintValidatorFactory(sanitizationService));
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new CompanyReturnLocationController(returnLocationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.close();
    }

    @Test
    void getReturnLocations_returnsLocations() throws Exception {
        authenticateAs(USER_ID);
        when(returnLocationService.getReturnLocations(COMPANY_ID, USER_ID)).thenReturn(List.of(response()));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/return-locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(LOCATION_ID.toString()))
                .andExpect(jsonPath("$[0].primary").value(true));
    }

    @Test
    void createReturnLocation_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(returnLocationService.createReturnLocation(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(response());

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/return-locations")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "address", "123 Warehouse Rd",
                                "city", "Toronto",
                                "country", "CA",
                                "postalCode", "M5V1A1",
                                "name", "Main Warehouse",
                                "primary", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Main Warehouse"));
    }

    @Test
    void updateReturnLocation_returnsOk() throws Exception {
        authenticateAs(USER_ID);
        when(returnLocationService.updateReturnLocation(eq(LOCATION_ID), eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(response());

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/return-locations/" + LOCATION_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "city", "Montreal",
                                "primary", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("CA"));
    }

    @Test
    void createReturnLocation_invalidBodyReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/return-locations")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "address", "",
                                "city", "",
                                "country", "CA"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturnLocation_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/return-locations/" + LOCATION_ID))
                .andExpect(status().isNoContent());

        verify(returnLocationService).deleteReturnLocation(LOCATION_ID, COMPANY_ID, USER_ID);
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private ReturnLocationResponse response() {
        return new ReturnLocationResponse(
                LOCATION_ID,
                COMPANY_ID,
                "Main Warehouse",
                "123 Warehouse Rd",
                "Toronto",
                "CA",
                "M5V1A1",
                true,
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }

    private static final class InjectingConstraintValidatorFactory implements ConstraintValidatorFactory {
        private final SanitizationService sanitizationService;

        private InjectingConstraintValidatorFactory(SanitizationService sanitizationService) {
            this.sanitizationService = sanitizationService;
        }

        @Override
        public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            try {
                T instance = key.getDeclaredConstructor().newInstance();
                if (instance instanceof SafeTextValidator safeTextValidator) {
                    Field field = SafeTextValidator.class.getDeclaredField("sanitizationService");
                    field.setAccessible(true);
                    field.set(safeTextValidator, sanitizationService);
                }
                return instance;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to create validator " + key.getName(), e);
            }
        }

        @Override
        public void releaseInstance(ConstraintValidator<?, ?> instance) {
        }
    }
}
