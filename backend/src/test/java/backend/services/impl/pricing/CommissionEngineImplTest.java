package backend.services.impl.pricing;

import backend.models.core.CommissionPolicy;
import backend.models.core.CommissionRule;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.core.MarketplaceVendor;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.SubOrder;
import backend.models.enums.CommissionRuleType;
import backend.models.enums.VendorTier;
import backend.repositories.CommissionPolicyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.OrderItemRepository;
import backend.services.intf.pricing.CommissionEngine;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommissionEngineImplTest {

    private static final UUID MARKETPLACE_ID = TestIds.uuid(1);
    private static final UUID VENDOR_ID = TestIds.uuid(2);
    private static final UUID SUB_ORDER_ID = TestIds.uuid(3);
    private static final UUID POLICY_ID = TestIds.uuid(4);

    private CommissionPolicyRepository commissionPolicyRepository;
    private MarketplaceProfileRepository marketplaceProfileRepository;
    private OrderItemRepository orderItemRepository;
    private CommissionEngineImpl service;

    @BeforeEach
    void setUp() {
        commissionPolicyRepository = mock(CommissionPolicyRepository.class);
        marketplaceProfileRepository = mock(MarketplaceProfileRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        service = new CommissionEngineImpl(
                commissionPolicyRepository,
                marketplaceProfileRepository,
                orderItemRepository
        );
    }

    @Test
    void compute_usesVendorOverridePolicy() {
        MarketplaceVendor vendor = vendor();
        vendor.setCommissionPolicyId(POLICY_ID);
        SubOrder subOrder = subOrder(vendor, new BigDecimal("100.00"));
        when(commissionPolicyRepository.findById(POLICY_ID))
                .thenReturn(Optional.of(policy(new BigDecimal("0.1200"))));
        when(orderItemRepository.findAllBySubOrderId(SUB_ORDER_ID))
                .thenReturn(List.of(item("Office", "Acme", "SKU-1-V")));

        CommissionEngine.CommissionResult result = service.compute(subOrder);

        assertEquals(new BigDecimal("0.1200"), result.commissionRate());
        assertEquals(new BigDecimal("12.00"), result.commissionAmount());
        assertEquals(new BigDecimal("88.00"), result.netVendorAmount());
    }

    @Test
    void compute_usesMarketplaceDefaultPolicyAndMatchingCategoryRule() {
        MarketplaceVendor vendor = vendor();
        SubOrder subOrder = subOrder(vendor, new BigDecimal("80.00"));
        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(vendor.getMarketplace());
        profile.setDefaultCommissionPolicyId(POLICY_ID);
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.of(profile));
        when(commissionPolicyRepository.findById(POLICY_ID))
                .thenReturn(Optional.of(policyWithRule(
                        CommissionRuleType.CATEGORY,
                        "Office",
                        new BigDecimal("0.1000"),
                        new BigDecimal("0.1500")
                )));
        when(orderItemRepository.findAllBySubOrderId(SUB_ORDER_ID))
                .thenReturn(List.of(item("Office", "Acme", "SKU-1-V")));

        CommissionEngine.CommissionResult result = service.compute(subOrder);

        assertEquals(new BigDecimal("0.1000"), result.commissionRate());
        assertEquals(new BigDecimal("8.00"), result.commissionAmount());
    }

    @Test
    void compute_fallsBackToActivePolicyWhenNoExplicitPolicyConfigured() {
        MarketplaceVendor vendor = vendor();
        SubOrder subOrder = subOrder(vendor, new BigDecimal("50.00"));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.empty());
        when(commissionPolicyRepository.findActiveAt(eq(MARKETPLACE_ID), any(Instant.class)))
                .thenReturn(List.of(policy(new BigDecimal("0.0700"))));
        when(orderItemRepository.findAllBySubOrderId(SUB_ORDER_ID))
                .thenReturn(List.of(item("Office", "Acme", "SKU-1-V")));

        CommissionEngine.CommissionResult result = service.compute(subOrder);

        assertEquals(new BigDecimal("0.0700"), result.commissionRate());
        assertEquals(new BigDecimal("3.50"), result.commissionAmount());
    }

    @Test
    void compute_matchesVendorTierRule() {
        MarketplaceVendor vendor = vendor();
        vendor.setTier(VendorTier.PREMIUM);
        SubOrder subOrder = subOrder(vendor, new BigDecimal("120.00"));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.empty());
        when(commissionPolicyRepository.findActiveAt(eq(MARKETPLACE_ID), any(Instant.class)))
                .thenReturn(List.of(policyWithRule(
                        CommissionRuleType.VENDOR_TIER,
                        "premium",
                        new BigDecimal("0.0500"),
                        new BigDecimal("0.1500")
                )));
        when(orderItemRepository.findAllBySubOrderId(SUB_ORDER_ID))
                .thenReturn(List.of(item("Office", "Acme", "SKU-1-V")));

        CommissionEngine.CommissionResult result = service.compute(subOrder);

        assertEquals(new BigDecimal("0.0500"), result.commissionRate());
        assertEquals(new BigDecimal("6.00"), result.commissionAmount());
    }

    @Test
    void compute_matchesBrandRule() {
        MarketplaceVendor vendor = vendor();
        SubOrder subOrder = subOrder(vendor, new BigDecimal("90.00"));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.empty());
        when(commissionPolicyRepository.findActiveAt(eq(MARKETPLACE_ID), any(Instant.class)))
                .thenReturn(List.of(policyWithRule(
                        CommissionRuleType.BRAND,
                        "acme",
                        new BigDecimal("0.0800"),
                        new BigDecimal("0.1500")
                )));
        when(orderItemRepository.findAllBySubOrderId(SUB_ORDER_ID))
                .thenReturn(List.of(item("Office", "Acme", "SKU-1-V")));

        CommissionEngine.CommissionResult result = service.compute(subOrder);

        assertEquals(new BigDecimal("0.0800"), result.commissionRate());
        assertEquals(new BigDecimal("7.20"), result.commissionAmount());
    }

    @Test
    void compute_matchesSkuPrefixAgainstVariantSku() {
        MarketplaceVendor vendor = vendor();
        SubOrder subOrder = subOrder(vendor, new BigDecimal("65.00"));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.empty());
        when(commissionPolicyRepository.findActiveAt(eq(MARKETPLACE_ID), any(Instant.class)))
                .thenReturn(List.of(policyWithRule(
                        CommissionRuleType.SKU,
                        "SKU-1",
                        new BigDecimal("0.0600"),
                        new BigDecimal("0.1500")
                )));
        when(orderItemRepository.findAllBySubOrderId(SUB_ORDER_ID))
                .thenReturn(List.of(item("Office", "Acme", "SKU-1-V")));

        CommissionEngine.CommissionResult result = service.compute(subOrder);

        assertEquals(new BigDecimal("0.0600"), result.commissionRate());
        assertEquals(new BigDecimal("3.90"), result.commissionAmount());
    }

    @Test
    void compute_matchesVolumeTierWhenThresholdMet() {
        MarketplaceVendor vendor = vendor();
        SubOrder subOrder = subOrder(vendor, new BigDecimal("250.00"));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.empty());
        when(commissionPolicyRepository.findActiveAt(eq(MARKETPLACE_ID), any(Instant.class)))
                .thenReturn(List.of(policyWithRule(
                        CommissionRuleType.VOLUME_TIER,
                        "200.00",
                        new BigDecimal("0.0400"),
                        new BigDecimal("0.1500")
                )));
        when(orderItemRepository.findAllBySubOrderId(SUB_ORDER_ID))
                .thenReturn(List.of(item("Office", "Acme", "SKU-1-V")));

        CommissionEngine.CommissionResult result = service.compute(subOrder);

        assertEquals(new BigDecimal("0.0400"), result.commissionRate());
        assertEquals(new BigDecimal("10.00"), result.commissionAmount());
    }

    @Test
    void compute_invalidVolumeThresholdFallsBackToDefaultRate() {
        MarketplaceVendor vendor = vendor();
        SubOrder subOrder = subOrder(vendor, new BigDecimal("75.00"));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.empty());
        when(commissionPolicyRepository.findActiveAt(eq(MARKETPLACE_ID), any(Instant.class)))
                .thenReturn(List.of(policyWithRule(
                        CommissionRuleType.VOLUME_TIER,
                        "not-a-number",
                        new BigDecimal("0.0100"),
                        new BigDecimal("0.1500")
                )));
        when(orderItemRepository.findAllBySubOrderId(SUB_ORDER_ID))
                .thenReturn(List.of(item("Office", "Acme", "SKU-1-V")));

        CommissionEngine.CommissionResult result = service.compute(subOrder);

        assertEquals(new BigDecimal("0.1500"), result.commissionRate());
        assertEquals(new BigDecimal("11.25"), result.commissionAmount());
    }

    private MarketplaceVendor vendor() {
        Company marketplace = new Company();
        marketplace.setId(MARKETPLACE_ID);
        marketplace.setName("Marketplace");

        Company vendorCompany = new Company();
        vendorCompany.setId(VENDOR_ID);
        vendorCompany.setName("Vendor Co");

        MarketplaceVendor vendor = new MarketplaceVendor();
        vendor.setId(VENDOR_ID);
        vendor.setMarketplace(marketplace);
        vendor.setVendorCompany(vendorCompany);
        vendor.setTier(VendorTier.STANDARD);
        return vendor;
    }

    private SubOrder subOrder(MarketplaceVendor vendor, BigDecimal totalAmount) {
        Order order = new Order();
        order.setId(TestIds.uuid(50));

        SubOrder subOrder = new SubOrder();
        subOrder.setId(SUB_ORDER_ID);
        subOrder.setOrder(order);
        subOrder.setMarketplaceVendor(vendor);
        subOrder.setTotalAmount(totalAmount);
        subOrder.setSubtotal(totalAmount);
        subOrder.setCurrency("USD");
        subOrder.setMarketplaceId(1L);
        return subOrder;
    }

    private OrderItem item(String category, String brand, String variantSku) {
        Product product = new Product();
        product.setId(TestIds.uuid(60));
        product.setCategory(category);
        product.setBrand(brand);
        product.setSku("SKU-1");

        OrderItem item = new OrderItem();
        item.setId(TestIds.uuid(61));
        item.setProduct(product);
        item.setVariantSku(variantSku);
        return item;
    }

    private CommissionPolicy policy(BigDecimal defaultRate) {
        CommissionPolicy policy = new CommissionPolicy();
        policy.setId(POLICY_ID);
        policy.setMarketplaceId(MARKETPLACE_ID);
        policy.setDefaultRate(defaultRate);
        policy.setRules(List.of());
        return policy;
    }

    private CommissionPolicy policyWithRule(
            CommissionRuleType type, String matchValue, BigDecimal rate, BigDecimal defaultRate) {
        CommissionPolicy policy = policy(defaultRate);
        CommissionRule rule = new CommissionRule();
        rule.setId(TestIds.uuid(70));
        rule.setPolicy(policy);
        rule.setRuleType(type);
        rule.setMatchValue(matchValue);
        rule.setRate(rate);
        rule.setPriority(10);
        policy.setRules(List.of(rule));
        return policy;
    }
}
