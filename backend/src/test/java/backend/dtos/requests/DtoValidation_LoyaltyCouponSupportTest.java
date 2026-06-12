package backend.dtos.requests;

import backend.dtos.requests.coupon.CreateCouponRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyPolicyRequest;
import backend.dtos.requests.support.CreateTicketRequest;
import backend.models.enums.TicketCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class DtoValidation_LoyaltyCouponSupportTest extends AbstractDtoValidationTest {

    // ─── CreateLoyaltyPolicyRequest ───────────────────────────────────────────

    @Test
    void createLoyaltyPolicy_valid_noViolations() {
        CreateLoyaltyPolicyRequest req = new CreateLoyaltyPolicyRequest();
        req.setName("Gold Rewards");
        req.setEarnRatePerDollar(new BigDecimal("1.50"));
        req.setCashbackRatePercent(BigDecimal.ZERO);
        req.setEarnMode("POINTS");
        assertValid(req);
    }

    @Test
    void createLoyaltyPolicy_blankName_violation() {
        CreateLoyaltyPolicyRequest req = new CreateLoyaltyPolicyRequest();
        req.setName("");
        req.setEarnRatePerDollar(new BigDecimal("1.00"));
        req.setCashbackRatePercent(BigDecimal.ZERO);
        req.setEarnMode("POINTS");
        assertViolation(req, "name");
    }

    @Test
    void createLoyaltyPolicy_nameWithHtml_safeTextViolation() {
        CreateLoyaltyPolicyRequest req = new CreateLoyaltyPolicyRequest();
        req.setName("<script>evil()</script>");
        req.setEarnRatePerDollar(new BigDecimal("1.00"));
        req.setCashbackRatePercent(BigDecimal.ZERO);
        req.setEarnMode("POINTS");
        assertViolation(req, "name");
    }

    @Test
    void createLoyaltyPolicy_nullEarnRate_violation() {
        CreateLoyaltyPolicyRequest req = new CreateLoyaltyPolicyRequest();
        req.setName("Policy");
        req.setEarnRatePerDollar(null);
        req.setCashbackRatePercent(BigDecimal.ZERO);
        req.setEarnMode("POINTS");
        assertViolation(req, "earnRatePerDollar");
    }

    @Test
    void createLoyaltyPolicy_earnRateTooHigh_violation() {
        CreateLoyaltyPolicyRequest req = new CreateLoyaltyPolicyRequest();
        req.setName("Policy");
        req.setEarnRatePerDollar(new BigDecimal("101.00")); // max is 100.00
        req.setCashbackRatePercent(BigDecimal.ZERO);
        req.setEarnMode("POINTS");
        assertViolation(req, "earnRatePerDollar");
    }

    @Test
    void createLoyaltyPolicy_cashbackTooHigh_violation() {
        CreateLoyaltyPolicyRequest req = new CreateLoyaltyPolicyRequest();
        req.setName("Policy");
        req.setEarnRatePerDollar(new BigDecimal("1.00"));
        req.setCashbackRatePercent(new BigDecimal("51.00")); // max is 50.00
        req.setEarnMode("POINTS");
        assertViolation(req, "cashbackRatePercent");
    }

    @Test
    void createLoyaltyPolicy_birthdayBonusNegative_violation() {
        CreateLoyaltyPolicyRequest req = new CreateLoyaltyPolicyRequest();
        req.setName("Policy");
        req.setEarnRatePerDollar(new BigDecimal("1.00"));
        req.setCashbackRatePercent(BigDecimal.ZERO);
        req.setEarnMode("POINTS");
        req.setBirthdayBonusPoints(-1);
        assertViolation(req, "birthdayBonusPoints");
    }

    // ─── CreateCouponRequest ──────────────────────────────────────────────────

    @Test
    void createCoupon_valid_noViolations() {
        CreateCouponRequest req = new CreateCouponRequest();
        req.setCode("SUMMER20");
        req.setName("Summer Sale");
        req.setType("PERCENTAGE");
        req.setValue(new BigDecimal("20.00"));
        assertValid(req);
    }

    @Test
    void createCoupon_blankCode_violation() {
        CreateCouponRequest req = new CreateCouponRequest();
        req.setCode("");
        req.setName("Sale");
        req.setType("PERCENTAGE");
        req.setValue(new BigDecimal("10.00"));
        assertViolation(req, "code");
    }

    @Test
    void createCoupon_codeInvalidPattern_violation() {
        CreateCouponRequest req = new CreateCouponRequest();
        req.setCode("lower case"); // must be uppercase, no spaces
        req.setName("Sale");
        req.setType("PERCENTAGE");
        req.setValue(new BigDecimal("10.00"));
        assertViolation(req, "code");
    }

    @Test
    void createCoupon_codeLowercase_violation() {
        CreateCouponRequest req = new CreateCouponRequest();
        req.setCode("summer20"); // lowercase not allowed
        req.setName("Sale");
        req.setType("PERCENTAGE");
        req.setValue(new BigDecimal("10.00"));
        assertViolation(req, "code");
    }

    @Test
    void createCoupon_nullValue_violation() {
        CreateCouponRequest req = new CreateCouponRequest();
        req.setCode("SUMMER20");
        req.setName("Sale");
        req.setType("PERCENTAGE");
        req.setValue(null);
        assertViolation(req, "value");
    }

    @Test
    void createCoupon_valueTooLow_violation() {
        CreateCouponRequest req = new CreateCouponRequest();
        req.setCode("SUMMER20");
        req.setName("Sale");
        req.setType("PERCENTAGE");
        req.setValue(new BigDecimal("0.00")); // min is 0.01
        assertViolation(req, "value");
    }

    @Test
    void createCoupon_maxUsesZero_violation() {
        CreateCouponRequest req = new CreateCouponRequest();
        req.setCode("SUMMER20");
        req.setName("Sale");
        req.setType("PERCENTAGE");
        req.setValue(new BigDecimal("10.00"));
        req.setMaxUses(0); // min is 1
        assertViolation(req, "maxUses");
    }

    // ─── CreateTicketRequest ──────────────────────────────────────────────────

    @Test
    void createTicket_valid_noViolations() {
        assertValid(new CreateTicketRequest(
                "Order not delivered", "My order has not arrived after 14 days",
                TicketCategory.SHIPPING, null, null, null));
    }

    @Test
    void createTicket_blankSubject_violation() {
        assertViolation(new CreateTicketRequest(
                "", "Description here", TicketCategory.SHIPPING, null, null, null), "subject");
    }

    @Test
    void createTicket_subjectWithHtml_safeTextViolation() {
        assertViolation(new CreateTicketRequest(
                "<script>xss</script>", "Description", TicketCategory.SHIPPING, null, null, null),
                "subject");
    }

    @Test
    void createTicket_blankDescription_violation() {
        assertViolation(new CreateTicketRequest(
                "Subject", "", TicketCategory.SHIPPING, null, null, null), "description");
    }

    @Test
    void createTicket_nullCategory_violation() {
        assertViolation(new CreateTicketRequest(
                "Subject", "Description", null, null, null, null), "category");
    }

    @Test
    void createTicket_subjectTooLong_violation() {
        assertViolation(new CreateTicketRequest(
                "S".repeat(201), "Description", TicketCategory.OTHER, null, null, null), "subject");
    }
}
