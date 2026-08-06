package backend.integration.analytics;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.DisputeCase;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.DisputeOutcome;
import backend.models.enums.DisputeStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.ProductStatus;
import backend.repositories.BundleRepository;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.DisputeCaseRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers OperationsController (/companies/{companyId}/operations/*).
 *
 * <p>Every endpoint now has a happy path. Fulfillment, refunds, pick-delays,
 * supplier-lateness and cancellations were previously untested because their native SQL
 * (TIMESTAMPDIFF/DATE/DATEDIFF) could not run on H2 in MySQL-compat mode; the suite runs
 * on real PostgreSQL and that SQL has been rewritten to portable equivalents.
 *
 * <p>The happy-path assertions deliberately cover the empty-data case. That is enough to
 * catch the failure these tests exist to catch — the controller converts any SQL error into
 * a 500, so a 200 with a well-formed body proves the aggregate query parsed, executed, and
 * bound its result to the projection. Value-level aggregation is covered by unit tests.
 */
class OperationsIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private DisputeCaseRepository disputeCaseRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private BundleRepository bundleRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Ops Co " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private void addMember(Company company, User user, CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    private String ops(UUID companyId, String metric) {
        return "/companies/" + companyId + "/operations/" + metric;
    }

    // ── Access control (auth check fires before any DB metric query) ──────────

    @Test
    void getSummary_employee_returns403() throws Exception {
        User owner = createActiveUser("ops-emp-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("ops-employee@example.com", "Password1!");
        addMember(company, employee, CompanyRole.EMPLOYEE);

        // EMPLOYEE lacks READ_ANALYTICS capability
        mockMvc.perform(get(ops(company.getId(), "summary"))
                        .header("Authorization", bearer(accessTokenFor(employee))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_noMembership_returns403() throws Exception {
        User owner = createActiveUser("ops-nomem-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User outsider = createActiveUser("ops-outsider@example.com", "Password1!");

        mockMvc.perform(get(ops(company.getId(), "summary"))
                        .header("Authorization", bearer(accessTokenFor(outsider))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ops(UUID.randomUUID(), "summary")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getFulfillment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ops(UUID.randomUUID(), "fulfillment")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRefunds_noMembership_returns403() throws Exception {
        User owner = createActiveUser("ops-ref-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User outsider = createActiveUser("ops-ref-out@example.com", "Password1!");

        mockMvc.perform(get(ops(company.getId(), "refunds"))
                        .header("Authorization", bearer(accessTokenFor(outsider))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPickDelays_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ops(UUID.randomUUID(), "pick-delays")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSupplierLateness_noMembership_returns403() throws Exception {
        User owner = createActiveUser("ops-sup-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User outsider = createActiveUser("ops-sup-out@example.com", "Password1!");

        mockMvc.perform(get(ops(company.getId(), "supplier-lateness"))
                        .header("Authorization", bearer(accessTokenFor(outsider))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCancellations_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ops(UUID.randomUUID(), "cancellations")))
                .andExpect(status().isUnauthorized());
    }

    // ── Stockouts — JPQL only, fully testable on H2 ───────────────────────────

    @Test
    void getStockouts_owner_returns200() throws Exception {
        User user = createActiveUser("ops-sto-owner@example.com", "Password1!");
        Company company = createCompany(user);
        addMember(company, user, CompanyRole.OWNER);

        mockMvc.perform(get(ops(company.getId(), "stockouts"))
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackedProducts").value(0))
                .andExpect(jsonPath("$.data.outOfStockRate").value(0.0))
                .andExpect(jsonPath("$.data.backorderRate").value(0.0));
    }

    @Test
    void getStockouts_manager_returns200() throws Exception {
        User owner = createActiveUser("ops-sto-co-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User manager = createActiveUser("ops-sto-mgr@example.com", "Password1!");
        addMember(company, manager, CompanyRole.MANAGER);

        // MANAGER has READ_ANALYTICS
        mockMvc.perform(get(ops(company.getId(), "stockouts"))
                        .header("Authorization", bearer(accessTokenFor(manager))))
                .andExpect(status().isOk());
    }

    @Test
    void getStockouts_employee_returns403() throws Exception {
        User owner = createActiveUser("ops-sto-emp-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("ops-sto-emp@example.com", "Password1!");
        addMember(company, employee, CompanyRole.EMPLOYEE);

        mockMvc.perform(get(ops(company.getId(), "stockouts"))
                        .header("Authorization", bearer(accessTokenFor(employee))))
                .andExpect(status().isForbidden());
    }

    // ── Native-SQL metrics ────────────────────────────────────────────────────
    // These exercise the interval/date-truncation queries in OperationsMetricsRepository.

    /** Creates a company whose owner can read analytics, and returns a bearer header value. */
    private String ownerTokenFor(Company company, User owner) {
        addMember(company, owner, CompanyRole.OWNER);
        return bearer(accessTokenFor(owner));
    }

    @Test
    void getFulfillment_owner_returns200() throws Exception {
        User owner = createActiveUser("ops-ful-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "fulfillment"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.daily").isArray());
    }

    @Test
    void getRefunds_owner_returns200() throws Exception {
        User owner = createActiveUser("ops-ref2-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "refunds"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.daily").isArray());
    }

    @Test
    void getPickDelays_owner_returns200() throws Exception {
        User owner = createActiveUser("ops-pick-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "pick-delays"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.daily").isArray());
    }

    @Test
    void getSupplierLateness_owner_returns200() throws Exception {
        User owner = createActiveUser("ops-sup2-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "supplier-lateness"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.late").value(0))
                .andExpect(jsonPath("$.data.daily").isArray());
    }

    @Test
    void getCancellations_owner_returns200() throws Exception {
        User owner = createActiveUser("ops-can-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "cancellations"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.byReason").isArray())
                .andExpect(jsonPath("$.data.daily").isArray());
    }

    /** Summary fans out to every metric above in one request. */
    @Test
    void getSummary_owner_returns200WithAllMetrics() throws Exception {
        User owner = createActiveUser("ops-sum-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "summary"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.fulfillment").exists())
                .andExpect(jsonPath("$.data.refunds").exists())
                .andExpect(jsonPath("$.data.pickDelays").exists())
                .andExpect(jsonPath("$.data.openDisputeCount").exists());
    }

    /**
     * Feature 15, AC 6. A company with no chargebacks reports zero — which also proves the
     * native dispute-count join parses and executes against real PostgreSQL.
     */
    @Test
    void getSummary_includesZeroOpenDisputeCountWhenCompanyHasNoChargebacks() throws Exception {
        User owner = createActiveUser("ops-disputes-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "summary"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openDisputeCount").value(0));
    }

    @Test
    void getSummary_countsOpenDisputeAgainstAProductOrder() throws Exception {
        User owner = createActiveUser("ops-disp-prod@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID orderId = seedOrderWithProductLine(company);
        seedOpenDispute(orderId, "dp_ops_product");

        mockMvc.perform(get(ops(company.getId(), "summary"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openDisputeCount").value(1));
    }

    /**
     * Bundle and kit lines are persisted with a null {@code product_id}, so an inner join on
     * {@code products} silently reports zero disputes for a bundles-only order.
     */
    @Test
    void getSummary_countsOpenDisputeAgainstABundleOnlyOrder() throws Exception {
        User owner = createActiveUser("ops-disp-bundle@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID orderId = seedOrderWithBundleLine(company);
        seedOpenDispute(orderId, "dp_ops_bundle");

        mockMvc.perform(get(ops(company.getId(), "summary"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openDisputeCount").value(1));
    }

    @Test
    void getSummary_excludesClosedDisputesFromOpenCount() throws Exception {
        User owner = createActiveUser("ops-disp-closed@example.com", "Password1!");
        Company company = createCompany(owner);
        seedClosedDispute(seedOrderWithProductLine(company), "dp_ops_closed");

        mockMvc.perform(get(ops(company.getId(), "summary"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openDisputeCount").value(0));
    }

    /** A dispute on another company's order must not appear in this company's count. */
    @Test
    void getSummary_excludesDisputesBelongingToAnotherCompany() throws Exception {
        User owner = createActiveUser("ops-disp-mine@example.com", "Password1!");
        Company mine = createCompany(owner);
        User otherOwner = createActiveUser("ops-disp-theirs@example.com", "Password1!");
        Company theirs = createCompany(otherOwner);
        seedOpenDispute(seedOrderWithProductLine(theirs), "dp_ops_other");

        mockMvc.perform(get(ops(mine.getId(), "summary"))
                        .header("Authorization", ownerTokenFor(mine, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openDisputeCount").value(0));
    }

    // ── Dispute-count seeding ─────────────────────────────────────────────────
    // Entities rather than raw INSERTs: `orders` and `products` carry a long tail of
    // not-null columns with entity-level defaults, so hand-written SQL breaks whenever
    // one is added. Order items are cascaded from Order — OrderItemRepository is not
    // autowirable here.

    private void seedOpenDispute(UUID orderId, String stripeDisputeId) {
        DisputeCase c = new DisputeCase();
        c.setOrder(orderRepository.findById(orderId).orElseThrow());
        c.setStripeDisputeId(stripeDisputeId);
        c.setAmountCents(2500L);
        c.setCurrency("usd");
        c.setStatus(DisputeStatus.OPEN);
        c.setOutcome(DisputeOutcome.PENDING);
        disputeCaseRepository.save(c);
    }

    private void seedClosedDispute(UUID orderId, String stripeDisputeId) {
        DisputeCase c = new DisputeCase();
        c.setOrder(orderRepository.findById(orderId).orElseThrow());
        c.setStripeDisputeId(stripeDisputeId);
        c.setAmountCents(2500L);
        c.setCurrency("usd");
        c.setStatus(DisputeStatus.CLOSED);
        c.setOutcome(DisputeOutcome.LOST);
        disputeCaseRepository.save(c);
    }

    private UUID seedOrderWithProductLine(Company company) {
        Product product = new Product();
        product.setCompany(company);
        product.setName("Disputed Widget");
        product.setPrice(BigDecimal.valueOf(24.99));
        product.setStatus(ProductStatus.ACTIVE);
        product.setPurchasable(true);
        product.setListed(true);
        product = productRepository.save(product);

        OrderItem item = newOrderItem();
        item.setProduct(product);
        return saveOrderWith(item);
    }

    private UUID seedOrderWithBundleLine(Company company) {
        ProductBundle bundle = new ProductBundle();
        bundle.setCompany(company);
        bundle.setName("Disputed Bundle");
        bundle.setPrice(BigDecimal.valueOf(49.99));
        bundle.setStatus(ProductStatus.ACTIVE);
        bundle = bundleRepository.save(bundle);

        // Mirrors OrderServiceImpl: a bundle line carries a null product_id.
        OrderItem item = newOrderItem();
        item.setBundle(bundle);
        item.setBundleName(bundle.getName());
        return saveOrderWith(item);
    }

    private OrderItem newOrderItem() {
        OrderItem item = new OrderItem();
        item.setProductName("Line");
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(24.99));
        return item;
    }

    private UUID saveOrderWith(OrderItem item) {
        User buyer = createActiveUser("ops-disp-buyer-" + UUID.randomUUID() + "@example.com", "Password1!");
        Order order = new Order();
        order.setUser(buyer);
        order.setStatus(OrderStatus.PAID);
        order.setCurrency("USD");
        order.setTotalAmount(BigDecimal.valueOf(24.99));
        item.setOrder(order);
        order.setItems(new java.util.ArrayList<>(List.of(item)));
        return orderRepository.save(order).getId();
    }
}
