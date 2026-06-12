package backend.dtos.requests;

import backend.dtos.requests.order.CreateOrderRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

class DtoValidation_OrderTest extends AbstractDtoValidationTest {

    // ─── CreateOrderRequest ───────────────────────────────────────────────────

    @Test
    void createOrder_valid_noViolations() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        assertValid(req);
    }

    @Test
    void createOrder_nullItems_violation() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(null);
        assertViolation(req, "items");
    }

    @Test
    void createOrder_emptyItems_violation() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of());
        assertViolation(req, "items");
    }

    @Test
    void createOrder_tooManyItems_violation() {
        // max 200 items
        List<CreateOrderRequest.OrderItemRequest> items = new java.util.ArrayList<>();
        for (int i = 0; i < 201; i++) items.add(item(1));
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(items);
        assertViolation(req, "items");
    }

    @Test
    void createOrder_currencyTooShort_violation() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        req.setCurrency("US"); // must be exactly 3
        assertViolation(req, "currency");
    }

    @Test
    void createOrder_currencyTooLong_violation() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        req.setCurrency("USDD");
        assertViolation(req, "currency");
    }

    @Test
    void createOrder_latitudeTooLow_violation() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        req.setBuyerLatitude(-91.0);
        assertViolation(req, "buyerLatitude");
    }

    @Test
    void createOrder_latitudeTooHigh_violation() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        req.setBuyerLatitude(91.0);
        assertViolation(req, "buyerLatitude");
    }

    @Test
    void createOrder_longitudeTooLow_violation() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        req.setBuyerLongitude(-181.0);
        assertViolation(req, "buyerLongitude");
    }

    @Test
    void createOrder_longitudeTooHigh_violation() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        req.setBuyerLongitude(181.0);
        assertViolation(req, "buyerLongitude");
    }

    @Test
    void createOrder_negativeLoyaltyPoints_violation() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        req.setLoyaltyPointsToRedeem(-1);
        assertViolation(req, "loyaltyPointsToRedeem");
    }

    @Test
    void createOrder_zeroLoyaltyPoints_noViolations() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        req.setLoyaltyPointsToRedeem(0);
        assertValid(req);
    }

    @Test
    void createOrder_shipRecipientNameWithHtml_violation() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        req.setShipRecipientName("<script>evil()</script>");
        assertViolation(req, "shipRecipientName");
    }

    @Test
    void createOrder_shipCountryInvalidFormat_violation() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        req.setShipCountry("USA"); // must be exactly 2 uppercase letters
        assertViolation(req, "shipCountry");
    }

    @Test
    void createOrder_shipCountryValidFormat_noViolations() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item(1)));
        req.setShipCountry("CA");
        assertValid(req);
    }

    // ─── Nested OrderItemRequest ──────────────────────────────────────────────

    @Test
    void orderItem_nullQuantity_violationOnItem() {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setQuantity(null);
        // @NotNull on quantity
        assertHasAnyViolation(item);
    }

    @Test
    void orderItem_zeroQuantity_violationOnItem() {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setQuantity(0); // min is 1
        assertHasAnyViolation(item);
    }

    @Test
    void orderItem_quantityTooHigh_violationOnItem() {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setQuantity(1000); // max is 999
        assertHasAnyViolation(item);
    }

    @Test
    void orderItem_valid_noViolations() {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setQuantity(2);
        assertValid(item);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private CreateOrderRequest.OrderItemRequest item(int qty) {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setQuantity(qty);
        return item;
    }
}
