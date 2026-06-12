package backend.dtos.requests;

import backend.dtos.requests.address.CreateCustomerAddressRequest;
import backend.dtos.requests.collection.CreateCollectionRequest;
import backend.dtos.requests.qa.AskQuestionRequest;
import org.junit.jupiter.api.Test;

class DtoValidation_MiscTest extends AbstractDtoValidationTest {

    // ─── CreateCustomerAddressRequest ─────────────────────────────────────────

    @Test
    void createAddress_valid_noViolations() {
        CreateCustomerAddressRequest req = new CreateCustomerAddressRequest();
        req.setLabel("Home");
        req.setRecipientName("Alice Smith");
        req.setStreet("123 Main St");
        req.setCity("Toronto");
        req.setState("ON");
        req.setPostalCode("M5V1A1");
        req.setCountry("CA");
        assertValid(req);
    }

    @Test
    void createAddress_blankLabel_violation() {
        CreateCustomerAddressRequest req = buildValidAddress();
        req.setLabel("");
        assertViolation(req, "label");
    }

    @Test
    void createAddress_labelWithHtml_safeTextViolation() {
        CreateCustomerAddressRequest req = buildValidAddress();
        req.setLabel("<b>Home</b>");
        assertViolation(req, "label");
    }

    @Test
    void createAddress_blankStreet_violation() {
        CreateCustomerAddressRequest req = buildValidAddress();
        req.setStreet("");
        assertViolation(req, "street");
    }

    @Test
    void createAddress_blankCity_violation() {
        CreateCustomerAddressRequest req = buildValidAddress();
        req.setCity("");
        assertViolation(req, "city");
    }

    @Test
    void createAddress_blankPostalCode_violation() {
        CreateCustomerAddressRequest req = buildValidAddress();
        req.setPostalCode("");
        assertViolation(req, "postalCode");
    }

    @Test
    void createAddress_invalidCountryCode_violation() {
        CreateCustomerAddressRequest req = buildValidAddress();
        req.setCountry("USA"); // must be exactly 2 uppercase letters
        assertViolation(req, "country");
    }

    @Test
    void createAddress_lowercaseCountry_violation() {
        CreateCustomerAddressRequest req = buildValidAddress();
        req.setCountry("ca"); // must be uppercase
        assertViolation(req, "country");
    }

    @Test
    void createAddress_blankCountry_violation() {
        CreateCustomerAddressRequest req = buildValidAddress();
        req.setCountry("");
        assertViolation(req, "country");
    }

    // ─── CreateCollectionRequest ──────────────────────────────────────────────

    @Test
    void createCollection_valid_noViolations() {
        CreateCollectionRequest req = new CreateCollectionRequest();
        req.setName("Summer Collection");
        req.setSlug("summer-collection");
        assertValid(req);
    }

    @Test
    void createCollection_blankName_violation() {
        CreateCollectionRequest req = new CreateCollectionRequest();
        req.setName("");
        req.setSlug("summer");
        assertViolation(req, "name");
    }

    @Test
    void createCollection_slugWithInvalidChars_violation() {
        CreateCollectionRequest req = new CreateCollectionRequest();
        req.setName("Summer");
        req.setSlug("summer collection"); // SafeIdentifier: no spaces
        assertViolation(req, "slug");
    }

    @Test
    void createCollection_nameTooLong_violation() {
        CreateCollectionRequest req = new CreateCollectionRequest();
        req.setName("N".repeat(256));
        req.setSlug("valid-slug");
        assertViolation(req, "name");
    }

    // ─── AskQuestionRequest ───────────────────────────────────────────────────

    @Test
    void askQuestion_valid_noViolations() {
        AskQuestionRequest req = new AskQuestionRequest();
        req.setQuestionText("Does this product come with a warranty?");
        assertValid(req);
    }

    @Test
    void askQuestion_blankText_violation() {
        AskQuestionRequest req = new AskQuestionRequest();
        req.setQuestionText("");
        assertViolation(req, "questionText");
    }

    @Test
    void askQuestion_textWithHtml_safeTextViolation() {
        AskQuestionRequest req = new AskQuestionRequest();
        req.setQuestionText("<script>evil()</script>");
        assertViolation(req, "questionText");
    }

    @Test
    void askQuestion_textTooLong_violation() {
        AskQuestionRequest req = new AskQuestionRequest();
        req.setQuestionText("Q".repeat(501));
        assertViolation(req, "questionText");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private CreateCustomerAddressRequest buildValidAddress() {
        CreateCustomerAddressRequest req = new CreateCustomerAddressRequest();
        req.setLabel("Home");
        req.setRecipientName("Alice Smith");
        req.setStreet("123 Main St");
        req.setCity("Toronto");
        req.setState("ON");
        req.setPostalCode("M5V1A1");
        req.setCountry("CA");
        return req;
    }
}
