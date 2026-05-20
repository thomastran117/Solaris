package backend.controllers.impl.promotions;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.coupon.CreateCouponRequest;
import backend.dtos.requests.coupon.UpdateCouponRequest;
import backend.dtos.responses.coupon.CouponResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.DiscountStatus;
import backend.models.enums.DiscountType;
import backend.services.intf.promotions.CouponService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CouponControllerTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID USER_ID    = TestIds.uuid(2);
    private static final UUID COUPON_ID  = TestIds.uuid(3);

    private CouponService couponService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        couponService = mock(CouponService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new CouponController(couponService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /companies/{companyId}/coupons ───────────────────────────────────

    @Test
    void listCoupons_returns200() throws Exception {
        when(couponService.listCoupons(eq(COMPANY_ID), eq(USER_ID), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/companies/{cid}/coupons", COMPANY_ID))
                .andExpect(status().isOk());
    }

    // ─── GET /companies/{companyId}/coupons/{couponId} ────────────────────────

    @Test
    void getCoupon_returns200() throws Exception {
        when(couponService.getCoupon(COMPANY_ID, COUPON_ID, USER_ID)).thenReturn(makeCouponResponse());

        mockMvc.perform(get("/companies/{cid}/coupons/{id}", COMPANY_ID, COUPON_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SAVE10"));
    }

    @Test
    void getCoupon_notFound_returns404() throws Exception {
        when(couponService.getCoupon(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/companies/{cid}/coupons/{id}", COMPANY_ID, COUPON_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /companies/{companyId}/coupons ──────────────────────────────────

    @Test
    void createCoupon_returns201() throws Exception {
        when(couponService.createCoupon(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(makeCouponResponse());

        mockMvc.perform(post("/companies/{cid}/coupons", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SAVE10"));
    }

    @Test
    void createCoupon_duplicateCode_returns409() throws Exception {
        when(couponService.createCoupon(any(), any(), any()))
                .thenThrow(new ConflictException("code exists"));

        mockMvc.perform(post("/companies/{cid}/coupons", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void createCoupon_invalidType_returns400() throws Exception {
        when(couponService.createCoupon(any(), any(), any()))
                .thenThrow(new BadRequestException("invalid type"));

        mockMvc.perform(post("/companies/{cid}/coupons", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isBadRequest());
    }

    // ─── PATCH /companies/{companyId}/coupons/{couponId} ─────────────────────

    @Test
    void updateCoupon_returns200() throws Exception {
        when(couponService.updateCoupon(eq(COMPANY_ID), eq(COUPON_ID), eq(USER_ID), any()))
                .thenReturn(makeCouponResponse());

        mockMvc.perform(patch("/companies/{cid}/coupons/{id}", COMPANY_ID, COUPON_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateCoupon_expiredStatus_returns400() throws Exception {
        when(couponService.updateCoupon(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("EXPIRED status not allowed"));

        mockMvc.perform(patch("/companies/{cid}/coupons/{id}", COMPANY_ID, COUPON_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EXPIRED\"}"))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE /companies/{companyId}/coupons/{couponId} ────────────────────

    @Test
    void deleteCoupon_returns204() throws Exception {
        mockMvc.perform(delete("/companies/{cid}/coupons/{id}", COMPANY_ID, COUPON_ID))
                .andExpect(status().isNoContent());

        verify(couponService).deleteCoupon(COMPANY_ID, COUPON_ID, USER_ID);
    }

    @Test
    void deleteCoupon_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("not found"))
                .when(couponService).deleteCoupon(any(), any(), any());

        mockMvc.perform(delete("/companies/{cid}/coupons/{id}", COMPANY_ID, COUPON_ID))
                .andExpect(status().isNotFound());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private CouponResponse makeCouponResponse() {
        return new CouponResponse(COUPON_ID, COMPANY_ID, "SAVE10", "Test Coupon",
                DiscountType.FIXED_AMOUNT, new BigDecimal("10"), DiscountStatus.ACTIVE,
                null, null, null, 0, null, null, null, null);
    }

    private CreateCouponRequest makeCreateRequest() {
        CreateCouponRequest req = new CreateCouponRequest();
        req.setCode("SAVE10");
        req.setName("Test Coupon");
        req.setType("FIXED_AMOUNT");
        req.setValue(new BigDecimal("10"));
        return req;
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
