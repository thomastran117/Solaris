package backend.dtos.requests;

import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.CreateLocationRequest;
import backend.dtos.requests.inventory.SetLocationStockRequest;
import backend.models.enums.AdjustmentReason;
import org.junit.jupiter.api.Test;

class DtoValidation_InventoryTest extends AbstractDtoValidationTest {

    // ─── CreateLocationRequest ────────────────────────────────────────────────

    @Test
    void createLocation_valid_noViolations() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("Main Warehouse");
        req.setCode("WH01");
        assertValid(req);
    }

    @Test
    void createLocation_blankName_violation() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("");
        req.setCode("WH01");
        assertViolation(req, "name");
    }

    @Test
    void createLocation_nullName_violation() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName(null);
        req.setCode("WH01");
        assertViolation(req, "name");
    }

    @Test
    void createLocation_nameTooLong_violation() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("N".repeat(256));
        req.setCode("WH01");
        assertViolation(req, "name");
    }

    @Test
    void createLocation_nameWithHtml_violation() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("<b>Warehouse</b>");
        req.setCode("WH01");
        assertViolation(req, "name");
    }

    @Test
    void createLocation_blankCode_violation() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("Warehouse");
        req.setCode("");
        assertViolation(req, "code");
    }

    @Test
    void createLocation_codeWithSpaces_patternViolation() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("Warehouse");
        req.setCode("WH 01"); // spaces not allowed
        assertViolation(req, "code");
    }

    @Test
    void createLocation_codeValidPattern_noViolations() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("Warehouse");
        req.setCode("WH-01_v2");
        assertValid(req);
    }

    @Test
    void createLocation_latitudeTooHigh_violation() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("Store");
        req.setCode("ST01");
        req.setLatitude(91.0);
        assertViolation(req, "latitude");
    }

    @Test
    void createLocation_handlingDaysTooHigh_violation() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("Store");
        req.setCode("ST01");
        req.setHandlingDays(31); // max is 30
        assertViolation(req, "handlingDays");
    }

    @Test
    void createLocation_pickupReadyHoursTooHigh_violation() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("Store");
        req.setCode("ST01");
        req.setPickupReadyHours(169); // max is 168
        assertViolation(req, "pickupReadyHours");
    }

    @Test
    void createLocation_addressWithScript_safeRichTextViolation() {
        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("Store");
        req.setCode("ST01");
        req.setAddress("<script>evil()</script> 123 Main St");
        assertViolation(req, "address");
    }

    // ─── AdjustStockRequest ───────────────────────────────────────────────────

    @Test
    void adjustStock_valid_noViolations() {
        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(10);
        req.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);
        assertValid(req);
    }

    @Test
    void adjustStock_nullDelta_violation() {
        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(null);
        req.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);
        assertViolation(req, "delta");
    }

    // ─── SetLocationStockRequest ──────────────────────────────────────────────

    @Test
    void setLocationStock_valid_noViolations() {
        SetLocationStockRequest req = new SetLocationStockRequest();
        req.setStock(50);
        assertValid(req);
    }

    @Test
    void setLocationStock_nullStock_violation() {
        SetLocationStockRequest req = new SetLocationStockRequest();
        req.setStock(null);
        assertViolation(req, "stock");
    }

    @Test
    void setLocationStock_negativeStock_violation() {
        SetLocationStockRequest req = new SetLocationStockRequest();
        req.setStock(-1);
        assertViolation(req, "stock");
    }

    @Test
    void setLocationStock_zeroStockAllowed_noViolations() {
        SetLocationStockRequest req = new SetLocationStockRequest();
        req.setStock(0);
        assertValid(req);
    }
}
