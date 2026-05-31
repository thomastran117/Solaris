package backend.services.impl.pricing;

import backend.dtos.responses.pricing.PayoutAttributionResponse;
import backend.dtos.responses.pricing.PromotionRuleAnalyticsResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.CompanyCapability;
import backend.repositories.PromotionRedemptionRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.projections.PayoutAttributionProjection;
import backend.repositories.projections.PromotionRuleAnalyticsProjection;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PricingReportServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID RULE_ID = TestIds.uuid(2);
    private static final UUID OWNER_ID = TestIds.uuid(3);

    private PromotionRedemptionRepository promotionRedemptionRepository;
    private PromotionRuleRepository promotionRuleRepository;
    private CompanyAccessService companyAccessService;
    private PricingReportServiceImpl service;

    @BeforeEach
    void setUp() {
        promotionRedemptionRepository = mock(PromotionRedemptionRepository.class);
        promotionRuleRepository = mock(PromotionRuleRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        service = new PricingReportServiceImpl(
                promotionRedemptionRepository,
                promotionRuleRepository,
                companyAccessService
        );
    }

    @Test
    void getPayoutAttribution_aggregatesGrandTotals() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-31T00:00:00Z");
        when(promotionRedemptionRepository.aggregatePayoutAttribution(from, to))
                .thenReturn(List.of(
                        payoutRow(TestIds.uuid(10), "A", new BigDecimal("10.00"), 2L, 1L),
                        payoutRow(TestIds.uuid(11), "B", new BigDecimal("15.50"), 3L, 2L)
                ));

        PayoutAttributionResponse response = service.getPayoutAttribution(from, to);

        assertEquals(new BigDecimal("25.50"), response.grandTotalSavings());
        assertEquals(5L, response.totalRedemptions());
        assertEquals(2, response.rows().size());
    }

    @Test
    void getPayoutAttribution_invalidWindowThrowsBadRequest() {
        Instant from = Instant.parse("2026-05-31T00:00:00Z");
        Instant to = Instant.parse("2026-05-01T00:00:00Z");

        assertThrows(BadRequestException.class, () -> service.getPayoutAttribution(from, to));
    }

    @Test
    void getRuleAnalytics_requiresAccessAndDefaultsNullProjection() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-31T00:00:00Z");
        when(promotionRuleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID))
                .thenReturn(Optional.of(mock(backend.models.core.PromotionRule.class)));
        when(promotionRedemptionRepository.aggregateRuleAnalytics(RULE_ID, from, to)).thenReturn(null);

        PromotionRuleAnalyticsResponse response =
                service.getRuleAnalytics(COMPANY_ID, RULE_ID, OWNER_ID, from, to);

        verify(companyAccessService).require(COMPANY_ID, OWNER_ID, CompanyCapability.READ_ANALYTICS);
        assertEquals(0L, response.redemptionCount());
        assertEquals(BigDecimal.ZERO, response.totalSavings());
        assertEquals(0L, response.uniqueOrderCount());
        assertEquals(0L, response.uniqueUserCount());
    }

    @Test
    void getRuleAnalytics_usesProjectionValues() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-31T00:00:00Z");
        when(promotionRuleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID))
                .thenReturn(Optional.of(mock(backend.models.core.PromotionRule.class)));
        when(promotionRedemptionRepository.aggregateRuleAnalytics(RULE_ID, from, to))
                .thenReturn(ruleAnalytics(4L, new BigDecimal("19.99"), 3L, 2L));

        PromotionRuleAnalyticsResponse response =
                service.getRuleAnalytics(COMPANY_ID, RULE_ID, OWNER_ID, from, to);

        assertEquals(4L, response.redemptionCount());
        assertEquals(new BigDecimal("19.99"), response.totalSavings());
        assertEquals(3L, response.uniqueOrderCount());
        assertEquals(2L, response.uniqueUserCount());
    }

    @Test
    void getRuleAnalytics_missingRuleThrowsNotFound() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-31T00:00:00Z");
        when(promotionRuleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getRuleAnalytics(COMPANY_ID, RULE_ID, OWNER_ID, from, to));
    }

    @Test
    void getRuleAnalytics_invalidWindowThrowsBadRequest() {
        Instant from = Instant.parse("2026-05-31T00:00:00Z");
        Instant to = Instant.parse("2026-05-01T00:00:00Z");

        assertThrows(BadRequestException.class,
                () -> service.getRuleAnalytics(COMPANY_ID, RULE_ID, OWNER_ID, from, to));
    }

    private PayoutAttributionProjection payoutRow(
            UUID companyId, String name, BigDecimal savings, Long count, Long orders) {
        return new PayoutAttributionProjection() {
            @Override public UUID getFundedByCompanyId() { return companyId; }
            @Override public String getCompanyName() { return name; }
            @Override public BigDecimal getTotalSavings() { return savings; }
            @Override public Long getRedemptionCount() { return count; }
            @Override public Long getUniqueOrderCount() { return orders; }
        };
    }

    private PromotionRuleAnalyticsProjection ruleAnalytics(
            Long redemptions, BigDecimal savings, Long orders, Long users) {
        return new PromotionRuleAnalyticsProjection() {
            @Override public Long getRedemptionCount() { return redemptions; }
            @Override public BigDecimal getTotalSavings() { return savings; }
            @Override public Long getUniqueOrderCount() { return orders; }
            @Override public Long getUniqueUserCount() { return users; }
        };
    }
}
