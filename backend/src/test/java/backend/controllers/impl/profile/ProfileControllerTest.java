package backend.controllers.impl.profile;

import backend.annotations.safeText.SafeTextValidator;
import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.profile.ProfileResponse;
import backend.services.intf.SanitizationService;
import backend.services.intf.profile.ProfileService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);

    private ProfileService profileService;
    private SanitizationService sanitizationService;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        profileService = mock(ProfileService.class);
        sanitizationService = mock(SanitizationService.class);
        when(sanitizationService.isSafePlainText(any())).thenReturn(true);

        validator = new LocalValidatorFactoryBean();
        validator.setConstraintValidatorFactory(new InjectingConstraintValidatorFactory(sanitizationService));
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController(profileService))
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
    void getProfile_returnsCurrentUserProfile() throws Exception {
        authenticateAs(USER_ID);
        when(profileService.getProfile(USER_ID)).thenReturn(profileResponse());

        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.firstName").value("Alex"));
    }

    @Test
    void updateProfile_delegatesBodyFields() throws Exception {
        authenticateAs(USER_ID);
        when(profileService.updateProfile(
                USER_ID,
                "Alex",
                "Morgan",
                "+1 555 0101",
                "123 King St"
        )).thenReturn(profileResponse());

        mockMvc.perform(patch("/profile")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Alex",
                                "lastName", "Morgan",
                                "phoneNumber", "+1 555 0101",
                                "address", "123 King St"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Morgan"))
                .andExpect(jsonPath("$.phoneNumber").value("+1 555 0101"));
    }

    @Test
    void updateProfile_invalidPhoneReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(patch("/profile")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Alex",
                                "phoneNumber", "abc"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProfile_unexpectedRuntimeReturns500() throws Exception {
        authenticateAs(USER_ID);
        when(profileService.getProfile(USER_ID)).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/profile"))
                .andExpect(status().isInternalServerError());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private ProfileResponse profileResponse() {
        return new ProfileResponse(
                USER_ID,
                "alex@example.com",
                "Alex",
                "Morgan",
                "+1 555 0101",
                "123 King St"
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
