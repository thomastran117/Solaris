package backend.security.oauth;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MicrosoftIssuerValidatorTest {

    // ── Null / blank ──────────────────────────────────────────────────────────

    @Test
    void nullIssuer_invalid() {
        assertFalse(MicrosoftIssuerValidator.isValid(null));
    }

    @Test
    void blankIssuer_invalid() {
        assertFalse(MicrosoftIssuerValidator.isValid("  "));
    }

    // ── Well-known tenants (default authority) ────────────────────────────────

    @Test
    void common_tenant_valid() {
        assertTrue(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/common/v2.0"));
    }

    @Test
    void organizations_tenant_valid() {
        assertTrue(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/organizations/v2.0"));
    }

    @Test
    void consumers_tenant_valid() {
        assertTrue(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/consumers/v2.0"));
    }

    @Test
    void common_tenantWithTrailingSlash_valid() {
        assertTrue(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/common/v2.0/"));
    }

    // ── GUID tenant ───────────────────────────────────────────────────────────

    @Test
    void guid_tenant_valid() {
        assertTrue(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0"));
    }

    @Test
    void guidUpperCase_valid() {
        assertTrue(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/9188040D-6C67-4C5B-B112-36A304B66DAD/v2.0"));
    }

    // ── Invalid issuers ───────────────────────────────────────────────────────

    @Test
    void differentHost_invalid() {
        assertFalse(MicrosoftIssuerValidator.isValid(
                "https://login.evil.com/common/v2.0"));
    }

    @Test
    void missingV2Path_invalid() {
        assertFalse(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/common"));
    }

    @Test
    void wrongVersionPath_invalid() {
        assertFalse(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/common/v1.0"));
    }

    @Test
    void unknownTenant_invalid() {
        assertFalse(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/unknown_tenant/v2.0"));
    }

    @Test
    void noPath_invalid() {
        assertFalse(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com"));
    }

    @Test
    void extraPathSegment_invalid() {
        assertFalse(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/common/v2.0/extra"));
    }

    // ── Host case normalization ───────────────────────────────────────────────

    @Test
    void uppercaseHost_normalizedAndValid() {
        assertTrue(MicrosoftIssuerValidator.isValid(
                "https://LOGIN.MICROSOFTONLINE.COM/common/v2.0"));
    }

    // ── Custom authority host and tenant set ──────────────────────────────────

    @Test
    void customAuthorityHost_matchingIssuer_valid() {
        assertTrue(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.de/mygov/v2.0",
                "https://login.microsoftonline.de/",
                Set.of("mygov")));
    }

    @Test
    void customAuthorityHost_nonMatchingHost_invalid() {
        assertFalse(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/common/v2.0",
                "https://login.microsoftonline.de/",
                Set.of("common")));
    }

    @Test
    void customAuthorityHostWithoutTrailingSlash_normalized() {
        assertTrue(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.de/mygov/v2.0",
                "https://login.microsoftonline.de",
                Set.of("mygov")));
    }

    @Test
    void nullAuthorityHost_fallsBackToDefault() {
        assertTrue(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/common/v2.0",
                null,
                null));
    }

    @Test
    void emptyTenantSet_fallsBackToDefaults() {
        assertTrue(MicrosoftIssuerValidator.isValid(
                "https://login.microsoftonline.com/common/v2.0",
                "https://login.microsoftonline.com/",
                Set.of()));
    }

    // ── Malformed URI ─────────────────────────────────────────────────────────

    @Test
    void malformedUri_invalid() {
        assertFalse(MicrosoftIssuerValidator.isValid("not a uri :::"));
    }

    @Test
    void uriWithNoHost_invalid() {
        assertFalse(MicrosoftIssuerValidator.isValid("/common/v2.0"));
    }
}
