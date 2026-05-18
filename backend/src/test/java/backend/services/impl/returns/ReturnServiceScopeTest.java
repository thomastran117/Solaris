package backend.services.impl.returns;

import java.util.UUID;
import backend.testutil.TestIds;
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
import backend.repositories.CompanyRepository;
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
import backend.services.intf.payments.PaymentService;
import backend.services.intf.pricing.RiskEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReturnServiceScopeTest {

    private ReturnRepository returnRepository;
    private ReturnItemRepository returnItemRepository;
    private OrderRepository orderRepository;
    private CompanyRepository companyRepository;
    private CompanyReturnLocationRepository returnLocationRepository;
    private UserRepository userRepository;
    private ReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        returnRepository = mock(ReturnRepository.class);
        returnItemRepository = mock(ReturnItemRepository.class);
        orderRepository = mock(OrderRepository.class);
        companyRepository = mock(CompanyRepository.class);
        returnLocationRepository = mock(CompanyReturnLocationRepository.class);
        userRepository = mock(UserRepository.class);

        service = new ReturnServiceImpl(
                returnRepository,
                returnItemRepository,
                orderRepository,
                mock(OrderCompensationRepository.class),
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(LocationStockRepository.class),
                mock(InventoryAdjustmentRepository.class),
                companyRepository,
                returnLocationRepository,
                userRepository,
                mock(PaymentService.class),
                mock(RiskEngine.class),
                mock(RiskAssessmentRepository.class),
                mock(RiskProperties.class),
                mock(ActivityEventPublisher.class));
    }

    @Test
    void requestReturn_rejectsMixedCompanyItems() {
        User buyer = makeUser(TestIds.uuid(7));
        Order order = makeOrder(55L, buyer, OrderStatus.DELIVERED);
        order.setItems(List.of(
                makeProductOrderItem(101L, 1L),
                makeProductOrderItem(102L, 2L)));

        when(orderRepository.findByIdAndUserId(55L, 7L)).thenReturn(Optional.of(order));
        when(userRepository.getReferenceById(TestIds.uuid(7))).thenReturn(buyer);
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(101L)).thenReturn(0);

        BuyerInitiateReturnRequest request = new BuyerInitiateReturnRequest(
                List.of(
                        new BuyerReturnItemRequest(101L, 1),
                        new BuyerReturnItemRequest(102L, 1)),
                ReturnReason.WRONG_ITEM,
                "Received the wrong products",
                List.of("https://example.test/evidence.jpg"));

        assertThrows(BadRequestException.class, () -> service.requestReturn(55L, 7L, request));
    }

    @Test
    void merchantInitiateReturn_rejectsItemsOutsideCompanyScope() {
        Company company = makeCompany(1L);
        Order order = makeOrder(44L, makeUser(TestIds.uuid(5)), OrderStatus.DELIVERED);
        order.setItems(List.of(
                makeProductOrderItem(201L, 1L),
                makeProductOrderItem(202L, 2L)));

        CompanyReturnLocation location = new CompanyReturnLocation();
        location.setId(301L);
        location.setCompany(company);
        location.setAddress("123 Warehouse Rd");
        location.setCity("Toronto");
        location.setCountry("CA");
        location.setPostalCode("M5V1A1");

        when(companyRepository.findByIdAndOwnerId(1L, 99L)).thenReturn(Optional.of(company));
        when(orderRepository.findByIdAndProductCompanyId(44L, 1L)).thenReturn(Optional.of(order));
        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(1L)).thenReturn(Optional.of(location));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(201L)).thenReturn(0);

        MerchantInitiateReturnRequest request = new MerchantInitiateReturnRequest(
                List.of(
                        new BuyerReturnItemRequest(201L, 1),
                        new BuyerReturnItemRequest(202L, 1)),
                ReturnReason.WRONG_ITEM,
                "Merchant initiated split return",
                false,
                0L,
                null);

        assertThrows(BadRequestException.class, () -> service.merchantInitiateReturn(44L, 1L, 99L, request));
    }

    @Test
    void approveReturn_rejectsExistingMixedCompanyReturn() {
        Company company = makeCompany(1L);
        Return mixedReturn = new Return();
        mixedReturn.setId(500L);
        mixedReturn.setStatus(backend.models.enums.ReturnStatus.REQUESTED);
        mixedReturn.setItems(new ArrayList<>());

        ReturnItem ownedItem = new ReturnItem();
        ownedItem.setOrderItem(makeProductOrderItem(301L, 1L));
        ownedItem.setReturnRequest(mixedReturn);

        ReturnItem foreignItem = new ReturnItem();
        foreignItem.setOrderItem(makeProductOrderItem(302L, 2L));
        foreignItem.setReturnRequest(mixedReturn);

        mixedReturn.getItems().add(ownedItem);
        mixedReturn.getItems().add(foreignItem);

        when(companyRepository.findByIdAndOwnerId(1L, 99L)).thenReturn(Optional.of(company));
        when(returnRepository.findByIdAndCompanyIdForUpdate(500L, 1L)).thenReturn(Optional.of(mixedReturn));

        MerchantApproveReturnRequest request = new MerchantApproveReturnRequest(
                "Approve if valid",
                null,
                null);

        assertThrows(ResourceNotFoundException.class, () -> service.approveReturn(500L, 1L, 99L, request));
    }

    private Company makeCompany(long id) {
        Company company = new Company();
        company.setId(id);
        return company;
    }

    private User makeUser(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Order makeOrder(long id, User user, OrderStatus status) {
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

    private OrderItem makeProductOrderItem(long id, long companyId) {
        Company company = makeCompany(companyId);
        Product product = new Product();
        product.setId(10_000L + id);
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
