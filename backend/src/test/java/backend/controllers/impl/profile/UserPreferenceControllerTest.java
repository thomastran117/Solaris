package backend.controllers.impl.profile;

import backend.configurations.application.GlobalExceptionHandler;
import backend.services.intf.profile.UserPreferenceService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserPreferenceControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);

    private UserPreferenceService userPreferenceService;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        userPreferenceService = mock(UserPreferenceService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new UserPreferenceController(userPreferenceService))
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
    void getPreferences_returnsTrackingOptOutState() throws Exception {
        authenticateAs(USER_ID);
        when(userPreferenceService.isTrackingOptedOut(USER_ID)).thenReturn(true);

        mockMvc.perform(get("/users/me/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingOptOut").value(true));
    }

    @Test
    void setTracking_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(put("/users/me/preferences/tracking")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("optOut", true))))
                .andExpect(status().isNoContent());

        verify(userPreferenceService).setTrackingOptOut(USER_ID, true);
    }

    @Test
    void setTracking_missingOptOutReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(put("/users/me/preferences/tracking")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPreferences_unexpectedRuntimeReturns500() throws Exception {
        authenticateAs(USER_ID);
        when(userPreferenceService.isTrackingOptedOut(USER_ID)).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/users/me/preferences"))
                .andExpect(status().isInternalServerError());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
