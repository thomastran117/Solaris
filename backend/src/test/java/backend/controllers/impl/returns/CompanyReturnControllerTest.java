package backend.controllers.impl.returns;

import backend.annotations.safeText.SafeTextValidator;
import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.return_.ReturnItemResponse;
import backend.dtos.responses.return_.ReturnResponse;
import backend.models.enums.RefundStatus;
import backend.models.enums.ReturnReason;
import backend.models.enums.ReturnStatus;
import backend.services.intf.SanitizationService;
import backend.services.intf.returns.ReturnService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyReturnControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID RETURN_ID = TestIds.uuid(3);

    private ReturnService returnService;
    private SanitizationService sanitizationService;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        returnService = mock(ReturnService.class);
        sanitizationService = mock(SanitizationService.class);
        when(sanitizationService.isSafePlainText(any())).thenReturn(true);

        validator = new LocalValidatorFactoryBean();
        validator.setConstraintValidatorFactory(new InjectingConstraintValidatorFactory(sanitizationService));
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new CompanyReturnController(returnService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.close();
    }

    @Test
    void getCompanyReturn_returnsScopedReturn() throws Exception {
        authenticateAs(USER_ID);
        when(returnService.getCompanyReturn(RETURN_ID, COMPANY_ID, USER_ID)).thenReturn(response());

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/returns/" + RETURN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.merchantNote").value("Approved"));
    }

    @Test
    void approveReturn_returnsOk() throws Exception {
        authenticateAs(USER_ID);
        when(returnService.approveReturn(eq(RETURN_ID), eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(response());

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/returns/" + RETURN_ID + "/approve")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "merchantNote", "Approved",
                                "refundAmountOverrideCents", 500,
                                "returnLocationId", TestIds.uuid(10)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectReturn_invalidBodyReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/returns/" + RETURN_ID + "/reject")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("merchantNote", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inspectReturn_returnsOk() throws Exception {
        authenticateAs(USER_ID);
        when(returnService.inspectReturn(eq(RETURN_ID), eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(response());

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/returns/" + RETURN_ID + "/inspect")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "returnItemId", TestIds.uuid(21),
                                        "condition", "RESELLABLE",
                                        "restock", true
                                )),
                                "merchantNote", "Inspected"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundStatus").value("PENDING"));
    }

    @Test
    void getCompanyReturn_unexpectedRuntimeReturns500() throws Exception {
        authenticateAs(USER_ID);
        when(returnService.getCompanyReturn(RETURN_ID, COMPANY_ID, USER_ID)).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/returns/" + RETURN_ID))
                .andExpect(status().isInternalServerError());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private ReturnResponse response() {
        return new ReturnResponse(
                RETURN_ID,
                TestIds.uuid(30),
                TestIds.uuid(31),
                ReturnStatus.APPROVED.name(),
                ReturnReason.WRONG_ITEM.name(),
                "Wrong item delivered",
                "Approved",
                false,
                List.of(new ReturnItemResponse(
                        TestIds.uuid(21),
                        TestIds.uuid(22),
                        "Desk Lamp",
                        null,
                        null,
                        1,
                        new BigDecimal("19.99"),
                        false,
                        null
                )),
                List.of(),
                "123 Warehouse Rd",
                "Toronto",
                "CA",
                "M5V1A1",
                500L,
                RefundStatus.PENDING.name(),
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z"),
                null
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
