package backend.dtos.requests;

import backend.dtos.requests.company.CreateCompanyRequest;
import backend.dtos.requests.company.InviteTeamMemberRequest;
import backend.dtos.requests.company.UpdateCompanyRequest;
import org.junit.jupiter.api.Test;

class DtoValidation_CompanyTest extends AbstractDtoValidationTest {

    // ─── CreateCompanyRequest ─────────────────────────────────────────────────

    @Test
    void createCompany_valid_noViolations() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("Acme Corp");
        assertValid(req);
    }

    @Test
    void createCompany_blankName_violation() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("   ");
        assertViolation(req, "name");
    }

    @Test
    void createCompany_nullName_violation() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName(null);
        assertViolation(req, "name");
    }

    @Test
    void createCompany_nameTooLong_violation() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("A".repeat(256));
        assertViolation(req, "name");
    }

    @Test
    void createCompany_nameWithHtml_safeTextViolation() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("<b>Company</b>");
        assertViolation(req, "name");
    }

    @Test
    void createCompany_invalidEmail_violation() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("Acme");
        req.setEmail("not-an-email");
        assertViolation(req, "email");
    }

    @Test
    void createCompany_validEmail_noViolations() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("Acme");
        req.setEmail("contact@acme.com");
        assertValid(req);
    }

    @Test
    void createCompany_invalidPhoneNumber_violation() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("Acme");
        req.setPhoneNumber("not-a-phone-number-at-all-abc");
        assertViolation(req, "phoneNumber");
    }

    @Test
    void createCompany_validPhoneNumber_noViolations() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("Acme");
        req.setPhoneNumber("+1 (416) 555-0100");
        assertValid(req);
    }

    @Test
    void createCompany_foundedYearTooEarly_violation() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("Acme");
        req.setFoundedYear(1799); // min is 1800
        assertViolation(req, "foundedYear");
    }

    @Test
    void createCompany_foundedYearValid_noViolations() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("Acme");
        req.setFoundedYear(2001);
        assertValid(req);
    }

    @Test
    void createCompany_descriptionWithScript_safeRichTextViolation() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("Acme");
        req.setDescription("<script>alert('xss')</script>");
        assertViolation(req, "description");
    }

    @Test
    void createCompany_descriptionWithBold_noViolations() {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName("Acme");
        req.setDescription("<b>Leading</b> provider of <i>solutions</i>.");
        assertValid(req);
    }

    // ─── UpdateCompanyRequest ─────────────────────────────────────────────────

    @Test
    void updateCompany_allNullFields_noViolations() {
        assertValid(new UpdateCompanyRequest());
    }

    @Test
    void updateCompany_invalidEmail_violation() {
        UpdateCompanyRequest req = new UpdateCompanyRequest();
        req.setEmail("bad-email");
        assertViolation(req, "email");
    }

    @Test
    void updateCompany_nameTooLong_violation() {
        UpdateCompanyRequest req = new UpdateCompanyRequest();
        req.setName("N".repeat(256));
        assertViolation(req, "name");
    }

    // ─── InviteTeamMemberRequest ──────────────────────────────────────────────

    @Test
    void inviteTeamMember_valid_noViolations() {
        InviteTeamMemberRequest req = new InviteTeamMemberRequest();
        req.setEmail("member@company.com");
        req.setRole(backend.models.enums.CompanyRole.MANAGER);
        assertValid(req);
    }

    @Test
    void inviteTeamMember_blankEmail_violation() {
        InviteTeamMemberRequest req = new InviteTeamMemberRequest();
        req.setEmail("");
        req.setRole(backend.models.enums.CompanyRole.MANAGER);
        assertViolation(req, "email");
    }

    @Test
    void inviteTeamMember_invalidEmail_violation() {
        InviteTeamMemberRequest req = new InviteTeamMemberRequest();
        req.setEmail("not-email");
        req.setRole(backend.models.enums.CompanyRole.MANAGER);
        assertViolation(req, "email");
    }

    @Test
    void inviteTeamMember_nullRole_violation() {
        InviteTeamMemberRequest req = new InviteTeamMemberRequest();
        req.setEmail("member@company.com");
        req.setRole(null);
        assertViolation(req, "role");
    }
}
