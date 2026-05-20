package backend.services.impl.promotions;

import backend.dtos.requests.coupon.CreateCouponRequest;
import backend.dtos.requests.coupon.UpdateCouponRequest;
import backend.dtos.responses.coupon.CouponResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.Coupon;
import backend.models.enums.CompanyCapability;
import backend.models.enums.DiscountStatus;
import backend.models.enums.DiscountType;
import backend.repositories.CompanyRepository;
import backend.repositories.CouponRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CouponServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID OWNER_ID   = TestIds.uuid(2);
    private static final UUID COUPON_ID  = TestIds.uuid(3);

    private CouponRepository couponRepository;
    private CompanyRepository companyRepository;
    private CompanyAccessService companyAccessService;

    private CouponServiceImpl service;

    @BeforeEach
    void setUp() {
        couponRepository     = mock(CouponRepository.class);
        companyRepository    = mock(CompanyRepository.class);
        companyAccessService = mock(CompanyAccessService.class);

        service = new CouponServiceImpl(couponRepository, companyRepository, companyAccessService);

        when(companyAccessService.require(eq(COMPANY_ID), eq(OWNER_ID), any(CompanyCapability.class)))
                .thenReturn(makeCompany());
        when(companyRepository.getReferenceById(COMPANY_ID)).thenReturn(makeCompany());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> {
            Coupon c = inv.getArgument(0);
            if (c.getId() == null) c.setId(COUPON_ID);
            return c;
        });
    }

    // ─── createCoupon ─────────────────────────────────────────────────────────

    @Test
    void createCoupon_happyPath_savesAndReturnsResponse() {
        when(couponRepository.existsByCodeIgnoreCase("SAVE10")).thenReturn(false);

        CouponResponse result = service.createCoupon(COMPANY_ID, OWNER_ID, makeCreateRequest("save10", "PERCENTAGE", new BigDecimal("10")));

        verify(couponRepository).save(any(Coupon.class));
        assertNotNull(result);
        assertEquals("SAVE10", result.code());
    }

    @Test
    void createCoupon_codeNormalizedUppercaseAndTrimmed() {
        when(couponRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);

        service.createCoupon(COMPANY_ID, OWNER_ID, makeCreateRequest("  save10  ", "PERCENTAGE", new BigDecimal("10")));

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        assertEquals("SAVE10", captor.getValue().getCode());
    }

    @Test
    void createCoupon_duplicateCode_throwsConflict() {
        when(couponRepository.existsByCodeIgnoreCase("SAVE10")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.createCoupon(COMPANY_ID, OWNER_ID, makeCreateRequest("save10", "PERCENTAGE", new BigDecimal("10"))));
    }

    @Test
    void createCoupon_invalidType_throwsBadRequest() {
        when(couponRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.createCoupon(COMPANY_ID, OWNER_ID, makeCreateRequest("CODE1", "BOGUS", new BigDecimal("10"))));
    }

    @Test
    void createCoupon_percentageOver100_throwsBadRequest() {
        when(couponRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.createCoupon(COMPANY_ID, OWNER_ID, makeCreateRequest("CODE1", "PERCENTAGE", new BigDecimal("101"))));
    }

    @Test
    void createCoupon_percentageExactly100_succeeds() {
        when(couponRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);

        assertDoesNotThrow(() ->
                service.createCoupon(COMPANY_ID, OWNER_ID, makeCreateRequest("CODE1", "PERCENTAGE", new BigDecimal("100"))));
    }

    @Test
    void createCoupon_fixedAmountAnyValue_noUpperBound() {
        when(couponRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);

        assertDoesNotThrow(() ->
                service.createCoupon(COMPANY_ID, OWNER_ID, makeCreateRequest("CODE1", "FIXED_AMOUNT", new BigDecimal("999"))));
    }

    @Test
    void createCoupon_endDateBeforeStartDate_throwsBadRequest() {
        when(couponRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        Instant start = Instant.now().plusSeconds(3600);
        Instant end   = Instant.now().plusSeconds(60);

        CreateCouponRequest req = makeCreateRequest("CODE1", "FIXED_AMOUNT", new BigDecimal("5"));
        req.setStartDate(start);
        req.setEndDate(end);

        assertThrows(BadRequestException.class,
                () -> service.createCoupon(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createCoupon_endDateEqualStartDate_throwsBadRequest() {
        when(couponRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        Instant same = Instant.now().plusSeconds(3600);

        CreateCouponRequest req = makeCreateRequest("CODE1", "FIXED_AMOUNT", new BigDecimal("5"));
        req.setStartDate(same);
        req.setEndDate(same);

        assertThrows(BadRequestException.class,
                () -> service.createCoupon(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createCoupon_nullDates_noDateValidation() {
        when(couponRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);

        CreateCouponRequest req = makeCreateRequest("CODE1", "FIXED_AMOUNT", new BigDecimal("5"));
        req.setStartDate(null);
        req.setEndDate(null);

        assertDoesNotThrow(() -> service.createCoupon(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createCoupon_pastEndDate_statusComputedAsExpired() {
        when(couponRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);

        CreateCouponRequest req = makeCreateRequest("CODE1", "FIXED_AMOUNT", new BigDecimal("5"));
        req.setEndDate(Instant.now().minusSeconds(3600)); // past

        CouponResponse result = service.createCoupon(COMPANY_ID, OWNER_ID, req);

        assertEquals(DiscountStatus.EXPIRED, result.status());
    }

    @Test
    void createCoupon_futureEndDate_statusFromStoredValue() {
        when(couponRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);

        CreateCouponRequest req = makeCreateRequest("CODE1", "FIXED_AMOUNT", new BigDecimal("5"));
        req.setEndDate(Instant.now().plusSeconds(3600)); // future

        CouponResponse result = service.createCoupon(COMPANY_ID, OWNER_ID, req);

        // stored status defaults to ACTIVE for a new coupon
        assertEquals(DiscountStatus.ACTIVE, result.status());
    }

    @Test
    void createCoupon_nullEndDate_statusFromStoredValue() {
        when(couponRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);

        CouponResponse result = service.createCoupon(COMPANY_ID, OWNER_ID,
                makeCreateRequest("CODE1", "FIXED_AMOUNT", new BigDecimal("5")));

        assertEquals(DiscountStatus.ACTIVE, result.status());
    }

    // ─── updateCoupon ─────────────────────────────────────────────────────────

    @Test
    void updateCoupon_nameOnly_updatesName() {
        Coupon existing = makeCoupon("SAVE10", DiscountType.FIXED_AMOUNT, new BigDecimal("5"), DiscountStatus.ACTIVE);
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateCouponRequest req = new UpdateCouponRequest();
        req.setName("Updated Name");

        CouponResponse result = service.updateCoupon(COMPANY_ID, COUPON_ID, OWNER_ID, req);

        assertEquals("Updated Name", result.name());
    }

    @Test
    void updateCoupon_setExpiredStatus_throwsBadRequest() {
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeCoupon("CODE", DiscountType.FIXED_AMOUNT, new BigDecimal("5"), DiscountStatus.ACTIVE)));

        UpdateCouponRequest req = new UpdateCouponRequest();
        req.setStatus("EXPIRED");

        assertThrows(BadRequestException.class,
                () -> service.updateCoupon(COMPANY_ID, COUPON_ID, OWNER_ID, req));
    }

    @Test
    void updateCoupon_invalidStatus_throwsBadRequest() {
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeCoupon("CODE", DiscountType.FIXED_AMOUNT, new BigDecimal("5"), DiscountStatus.ACTIVE)));

        UpdateCouponRequest req = new UpdateCouponRequest();
        req.setStatus("PENDING");

        assertThrows(BadRequestException.class,
                () -> service.updateCoupon(COMPANY_ID, COUPON_ID, OWNER_ID, req));
    }

    @Test
    void updateCoupon_setActiveStatus_setsActive() {
        Coupon existing = makeCoupon("CODE", DiscountType.FIXED_AMOUNT, new BigDecimal("5"), DiscountStatus.DISABLED);
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateCouponRequest req = new UpdateCouponRequest();
        req.setStatus("ACTIVE");

        service.updateCoupon(COMPANY_ID, COUPON_ID, OWNER_ID, req);

        assertEquals(DiscountStatus.ACTIVE, existing.getStatus());
    }

    @Test
    void updateCoupon_setDisabledStatus_setsDisabled() {
        Coupon existing = makeCoupon("CODE", DiscountType.FIXED_AMOUNT, new BigDecimal("5"), DiscountStatus.ACTIVE);
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateCouponRequest req = new UpdateCouponRequest();
        req.setStatus("DISABLED");

        service.updateCoupon(COMPANY_ID, COUPON_ID, OWNER_ID, req);

        assertEquals(DiscountStatus.DISABLED, existing.getStatus());
    }

    @Test
    void updateCoupon_typeChangeWithExistingValueOver100_throwsBadRequest() {
        // existing value is 150 (valid for FIXED_AMOUNT), changing type to PERCENTAGE → invalid
        Coupon existing = makeCoupon("CODE", DiscountType.FIXED_AMOUNT, new BigDecimal("150"), DiscountStatus.ACTIVE);
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateCouponRequest req = new UpdateCouponRequest();
        req.setType("PERCENTAGE"); // triggers re-validation with existing value 150

        assertThrows(BadRequestException.class,
                () -> service.updateCoupon(COMPANY_ID, COUPON_ID, OWNER_ID, req));
    }

    @Test
    void updateCoupon_valueChangeExceedsPercentageCap_throwsBadRequest() {
        // existing type is PERCENTAGE; new value 150 > 100
        Coupon existing = makeCoupon("CODE", DiscountType.PERCENTAGE, new BigDecimal("10"), DiscountStatus.ACTIVE);
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateCouponRequest req = new UpdateCouponRequest();
        req.setValue(new BigDecimal("150"));

        assertThrows(BadRequestException.class,
                () -> service.updateCoupon(COMPANY_ID, COUPON_ID, OWNER_ID, req));
    }

    @Test
    void updateCoupon_endDateBeforeExistingStart_throwsBadRequest() {
        Coupon existing = makeCoupon("CODE", DiscountType.FIXED_AMOUNT, new BigDecimal("5"), DiscountStatus.ACTIVE);
        existing.setStartDate(Instant.now().plusSeconds(3600));
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateCouponRequest req = new UpdateCouponRequest();
        req.setEndDate(Instant.now().plusSeconds(60)); // before existing start

        assertThrows(BadRequestException.class,
                () -> service.updateCoupon(COMPANY_ID, COUPON_ID, OWNER_ID, req));
    }

    @Test
    void updateCoupon_notFound_throwsResourceNotFound() {
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateCoupon(COMPANY_ID, COUPON_ID, OWNER_ID, new UpdateCouponRequest()));
    }

    // ─── deleteCoupon ─────────────────────────────────────────────────────────

    @Test
    void deleteCoupon_happyPath_deletes() {
        Coupon coupon = makeCoupon("CODE", DiscountType.FIXED_AMOUNT, new BigDecimal("5"), DiscountStatus.ACTIVE);
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID)).thenReturn(Optional.of(coupon));

        service.deleteCoupon(COMPANY_ID, COUPON_ID, OWNER_ID);

        verify(couponRepository).delete(coupon);
    }

    @Test
    void deleteCoupon_notFound_throwsResourceNotFound() {
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteCoupon(COMPANY_ID, COUPON_ID, OWNER_ID));
    }

    // ─── getCoupon ────────────────────────────────────────────────────────────

    @Test
    void getCoupon_happyPath_returnsResponse() {
        Coupon coupon = makeCoupon("CODE", DiscountType.FIXED_AMOUNT, new BigDecimal("5"), DiscountStatus.ACTIVE);
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID)).thenReturn(Optional.of(coupon));

        CouponResponse result = service.getCoupon(COMPANY_ID, COUPON_ID, OWNER_ID);

        assertNotNull(result);
        assertEquals("CODE", result.code());
    }

    @Test
    void getCoupon_notFound_throwsResourceNotFound() {
        when(couponRepository.findByIdAndCompanyId(COUPON_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCoupon(COMPANY_ID, COUPON_ID, OWNER_ID));
    }

    // ─── listCoupons ──────────────────────────────────────────────────────────

    @Test
    void listCoupons_delegatesToRepository() {
        when(couponRepository.findAllByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listCoupons(COMPANY_ID, OWNER_ID, 0, 20);

        verify(couponRepository).findAllByCompanyId(eq(COMPANY_ID), any(Pageable.class));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Company makeCompany() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        c.setName("Test Company");
        return c;
    }

    private Coupon makeCoupon(String code, DiscountType type, BigDecimal value, DiscountStatus status) {
        Coupon c = new Coupon();
        c.setId(COUPON_ID);
        c.setCompany(makeCompany());
        c.setCode(code);
        c.setName("Test Coupon");
        c.setType(type);
        c.setValue(value);
        c.setStatus(status);
        return c;
    }

    private CreateCouponRequest makeCreateRequest(String code, String type, BigDecimal value) {
        CreateCouponRequest req = new CreateCouponRequest();
        req.setCode(code);
        req.setName("Test Coupon");
        req.setType(type);
        req.setValue(value);
        return req;
    }
}
