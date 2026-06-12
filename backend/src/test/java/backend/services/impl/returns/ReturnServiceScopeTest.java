package backend.services.impl.returns;

import backend.configurations.environment.RiskProperties;
import backend.dtos.requests.return_.BuyerInitiateReturnRequest;
import backend.dtos.requests.return_.BuyerReturnItemRequest;
import backend.dtos.requests.return_.MerchantApproveReturnRequest;
import backend.dtos.requests.return_.MerchantInitiateReturnRequest;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyReturnLocation;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.Return;
import backend.models.core.ReturnItem;
import backend.models.core.User;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.ReturnReason;
import backend.repositories.CompanyReturnLocationRepository;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.OrderCompensationRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.ReturnItemRepository;
import backend.repositories.ReturnRepository;
import backend.repositories.RiskAssessmentRepository;
import backend.repositories.UserRepository;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.AuthAuditLogger;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.pricing.RiskEngine;
import backend.services.intf.promotions.LoyaltyService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReturnServiceScopeTest {

    private ReturnRepository returnRepository;
    private ReturnItemRepository returnItemRepository;
    private OrderRepository orderRepository;
    private CompanyAccessService companyAccessService;
    private CompanyReturnLocationRepository returnLocationRepository;
    private UserRepository userRepository;
    private ReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        returnRepository       = mock(ReturnRepository.class);
        returnItemRepository   = mock(ReturnItemRepository.class);
        orderRepository        = mock(OrderRepository.class);
        companyAccessService   = mock(CompanyAccessService.class);
        returnLocationRepository = mock(CompanyReturnLocationRepository.class);
        userRepository         = mock(UserRepository.class);

        RiskProperties riskProperties = mock(RiskProperties.class);
        RiskProperties.ReturnPolicy returnPolicy = mock(RiskProperties.ReturnPolicy.class);
        when(returnPolicy.getWindowDays()).thenReturn(30);
        when(riskProperties.getReturnPolicy()).thenReturn(returnPolicy);

        service = new ReturnServiceImpl(
                returnRepository,
                returnItemRepository,
                orderRepository,
                mock(OrderCompensationRepository.class),
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(LocationStockRepository.class),
                mock(InventoryAdjustmentRepository.class),
                companyAccessService,
                returnLocationRepository,
                userRepository,
                mock(PaymentService.class),
                mock(RiskEngine.class),
                mock(RiskAssessmentRepository.class),
                riskProperties,
                mock(ActivityEventPublisher.class),
                mock(LoyaltyService.class),
                mock(AuthAuditLogger.class));
    }

    @Test
    void requestReturn_rejectsMixedCompanyItems() {
        User buyer = makeUser(TestIds.uuid(7));
        Order order = makeOrder(TestIds.uuid(55), buyer, OrderStatus.DELIVERED);
        order.setItems(List.of(
                makeProductOrderItem(TestIds.uuid(101), TestIds.uuid(1)),
                makeProductOrderItem(TestIds.uuid(102), TestIds.uuid(2))));

        when(orderRepository.findByIdAndUserId(TestIds.uuid(55), TestIds.uuid(7))).thenReturn(Optional.of(order));
        when(userRepository.getReferenceById(TestIds.uuid(7))).thenReturn(buyer);
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(TestIds.uuid(101))).thenReturn(0);

        BuyerInitiateReturnRequest request = new BuyerInitiateReturnRequest(
                List.of(
                        new BuyerReturnItemRequest(TestIds.uuid(101), 1),
                        new BuyerReturnItemRequest(TestIds.uuid(102), 1)),
                ReturnReason.WRONG_ITEM,
                "Received the wrong products",
                List.of("https://example.test/evidence.jpg"));

        assertThrows(BadRequestException.class,
                () -> service.requestReturn(TestIds.uuid(55), TestIds.uuid(7), request));
    }

    @Test
    void merchantInitiateReturn_rejectsItemsOutsideCompanyScope() {
        Company company = makeCompany(TestIds.uuid(1));
        Order order = makeOrder(TestIds.uuid(44), makeUser(TestIds.uuid(5)), OrderStatus.DELIVERED);
        order.setItems(List.of(
                makeProductOrderItem(TestIds.uuid(201), TestIds.uuid(1)),
                makeProductOrderItem(TestIds.uuid(202), TestIds.uuid(2))));

        CompanyReturnLocation location = new CompanyReturnLocation();
        location.setId(TestIds.uuid(301));
        location.setCompany(company);
        location.setAddress("123 Warehouse Rd");
        location.setCity("Toronto");
        location.setCountry("CA");
        location.setPostalCode("M5V1A1");

        when(companyAccessService.require(eq(TestIds.uuid(1)), eq(TestIds.uuid(99)), any())).thenReturn(company);
        when(orderRepository.findByIdAndProductCompanyIdForUpdate(TestIds.uuid(44), TestIds.uuid(1))).thenReturn(Optional.of(order));
        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(TestIds.uuid(1))).thenReturn(Optional.of(location));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(TestIds.uuid(201))).thenReturn(0);

        MerchantInitiateReturnRequest request = new MerchantInitiateReturnRequest(
                List.of(
                        new BuyerReturnItemRequest(TestIds.uuid(201), 1),
                        new BuyerReturnItemRequest(TestIds.uuid(202), 1)),
                ReturnReason.WRONG_ITEM,
                "Merchant initiated split return",
                false,
                0L,
                null);

        assertThrows(BadRequestException.class,
                () -> service.merchantInitiateReturn(TestIds.uuid(44), TestIds.uuid(1), TestIds.uuid(99), request));
    }

    @Test
    void approveReturn_rejectsExistingMixedCompanyReturn() {
        Company company = makeCompany(TestIds.uuid(1));
        Return mixedReturn = new Return();
        mixedReturn.setId(TestIds.uuid(500));
        mixedReturn.setStatus(backend.models.enums.ReturnStatus.REQUESTED);
        mixedReturn.setItems(new ArrayList<>());

        ReturnItem ownedItem = new ReturnItem();
        ownedItem.setOrderItem(makeProductOrderItem(TestIds.uuid(301), TestIds.uuid(1)));
        ownedItem.setReturnRequest(mixedReturn);

        ReturnItem foreignItem = new ReturnItem();
        foreignItem.setOrderItem(makeProductOrderItem(TestIds.uuid(302), TestIds.uuid(2)));
        foreignItem.setReturnRequest(mixedReturn);

        mixedReturn.getItems().add(ownedItem);
        mixedReturn.getItems().add(foreignItem);

        when(companyAccessService.require(eq(TestIds.uuid(1)), eq(TestIds.uuid(99)), any())).thenReturn(company);
        when(returnRepository.findByIdAndCompanyIdForUpdate(TestIds.uuid(500), TestIds.uuid(1))).thenReturn(Optional.of(mixedReturn));

        MerchantApproveReturnRequest request = new MerchantApproveReturnRequest("Approve if valid", null, null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.approveReturn(TestIds.uuid(500), TestIds.uuid(1), TestIds.uuid(99), request));
    }

    private Company makeCompany(UUID id) {
        Company company = new Company();
        company.setId(id);
        return company;
    }

    private User makeUser(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Order makeOrder(UUID id, User user, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUser(user);
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCurrency("USD");
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>());
        return order;
    }

    private OrderItem makeProductOrderItem(UUID id, UUID companyId) {
        Company company = makeCompany(companyId);
        Product product = new Product();
        product.setId(id);
        product.setCompany(company);
        product.setName("Product " + id);

        OrderItem item = new OrderItem();
        item.setId(id);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.ONE);
        item.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
        return item;
    }
}
