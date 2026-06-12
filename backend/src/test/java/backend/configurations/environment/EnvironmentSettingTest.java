package backend.configurations.environment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentSettingTest {

    // ── Cors.getAllowedOrigins ─────────────────────────────────────────────────

    @Test
    void cors_nullAllowedOrigins_returnsEmptyList() {
        EnvironmentSetting.Cors cors = new EnvironmentSetting.Cors();
        cors.setAllowedOrigins(null);
        assertEquals(List.of(), cors.getAllowedOrigins());
    }

    @Test
    void cors_blankAllowedOrigins_returnsEmptyList() {
        EnvironmentSetting.Cors cors = new EnvironmentSetting.Cors();
        cors.setAllowedOrigins("   ");
        assertEquals(List.of(), cors.getAllowedOrigins());
    }

    @Test
    void cors_emptyString_returnsEmptyList() {
        EnvironmentSetting.Cors cors = new EnvironmentSetting.Cors();
        cors.setAllowedOrigins("");
        assertEquals(List.of(), cors.getAllowedOrigins());
    }

    @Test
    void cors_singleOrigin_returnsList() {
        EnvironmentSetting.Cors cors = new EnvironmentSetting.Cors();
        cors.setAllowedOrigins("https://example.com");
        assertEquals(List.of("https://example.com"), cors.getAllowedOrigins());
    }

    @Test
    void cors_multipleOrigins_splitOnComma() {
        EnvironmentSetting.Cors cors = new EnvironmentSetting.Cors();
        cors.setAllowedOrigins("https://example.com,https://app.example.com");
        assertEquals(2, cors.getAllowedOrigins().size());
        assertTrue(cors.getAllowedOrigins().contains("https://example.com"));
        assertTrue(cors.getAllowedOrigins().contains("https://app.example.com"));
    }

    @Test
    void cors_spacesAroundCommas_trimmed() {
        EnvironmentSetting.Cors cors = new EnvironmentSetting.Cors();
        cors.setAllowedOrigins(" https://a.com , https://b.com ");
        List<String> result = cors.getAllowedOrigins();
        assertTrue(result.contains("https://a.com"));
        assertTrue(result.contains("https://b.com"));
        result.forEach(s -> assertFalse(s.startsWith(" "), "Entry should not have leading spaces: " + s));
    }

    @Test
    void cors_emptyEntriesAfterSplit_filtered() {
        EnvironmentSetting.Cors cors = new EnvironmentSetting.Cors();
        cors.setAllowedOrigins("https://a.com,,  ,https://b.com");
        List<String> result = cors.getAllowedOrigins();
        assertEquals(2, result.size());
    }

    // ── Proxy.getTrustedProxies ───────────────────────────────────────────────

    @Test
    void proxy_nullTrustedProxies_returnsEmptyList() {
        EnvironmentSetting.Proxy proxy = new EnvironmentSetting.Proxy();
        proxy.setTrustedProxies(null);
        assertEquals(List.of(), proxy.getTrustedProxies());
    }

    @Test
    void proxy_blankTrustedProxies_returnsEmptyList() {
        EnvironmentSetting.Proxy proxy = new EnvironmentSetting.Proxy();
        proxy.setTrustedProxies("  ");
        assertEquals(List.of(), proxy.getTrustedProxies());
    }

    @Test
    void proxy_singleIp_returnsList() {
        EnvironmentSetting.Proxy proxy = new EnvironmentSetting.Proxy();
        proxy.setTrustedProxies("10.0.0.1");
        assertEquals(List.of("10.0.0.1"), proxy.getTrustedProxies());
    }

    @Test
    void proxy_cidrAndIp_returnsBoth() {
        EnvironmentSetting.Proxy proxy = new EnvironmentSetting.Proxy();
        proxy.setTrustedProxies("10.0.0.0/8, 192.168.1.1");
        assertEquals(2, proxy.getTrustedProxies().size());
        assertTrue(proxy.getTrustedProxies().contains("10.0.0.0/8"));
        assertTrue(proxy.getTrustedProxies().contains("192.168.1.1"));
    }

    // ── Security.setBcryptStrength clamping ───────────────────────────────────

    @Test
    void security_bcryptStrength_clampedToMin4() {
        EnvironmentSetting.Security security = new EnvironmentSetting.Security();
        security.setBcryptStrength(1);
        assertEquals(4, security.getBcryptStrength());
    }

    @Test
    void security_bcryptStrength_clampedToMax31() {
        EnvironmentSetting.Security security = new EnvironmentSetting.Security();
        security.setBcryptStrength(100);
        assertEquals(31, security.getBcryptStrength());
    }

    @Test
    void security_bcryptStrength_inRange_unchanged() {
        EnvironmentSetting.Security security = new EnvironmentSetting.Security();
        security.setBcryptStrength(12);
        assertEquals(12, security.getBcryptStrength());
    }

    // ── Security.setOauthMaxTokenLength clamping ──────────────────────────────

    @Test
    void security_oauthMaxTokenLength_clampedToMin256() {
        EnvironmentSetting.Security security = new EnvironmentSetting.Security();
        security.setOauthMaxTokenLength(10);
        assertEquals(256, security.getOauthMaxTokenLength());
    }

    // ── S3.setPresignExpirySeconds clamping ───────────────────────────────────

    @Test
    void s3_presignExpiry_clampedToMin30() {
        EnvironmentSetting.S3 s3 = new EnvironmentSetting.S3();
        s3.setPresignExpirySeconds(1);
        assertEquals(30, s3.getPresignExpirySeconds());
    }

    @Test
    void s3_presignExpiry_clampedToMax3600() {
        EnvironmentSetting.S3 s3 = new EnvironmentSetting.S3();
        s3.setPresignExpirySeconds(10000);
        assertEquals(3600, s3.getPresignExpirySeconds());
    }

    // ── RiskProperties setters (boundary validation) ──────────────────────────

    @Test
    void riskProperties_verifyThreshold_clampedToMin0() {
        RiskProperties props = new RiskProperties();
        props.setVerifyThreshold(-5);
        assertEquals(0, props.getVerifyThreshold());
    }

    @Test
    void riskProperties_verifyThreshold_clampedToMax1000() {
        RiskProperties props = new RiskProperties();
        props.setVerifyThreshold(9999);
        assertEquals(1000, props.getVerifyThreshold());
    }

    @Test
    void riskProperties_blockThreshold_defaults() {
        RiskProperties props = new RiskProperties();
        assertEquals(70, props.getBlockThreshold());
    }

    @Test
    void riskProperties_getVipSegmentId_nullWhenBlank() {
        RiskProperties props = new RiskProperties();
        props.setVipSegmentId("   ");
        assertNull(props.getVipSegmentId());
    }

    @Test
    void riskProperties_getVipSegmentId_nullWhenInvalidUuid() {
        RiskProperties props = new RiskProperties();
        props.setVipSegmentId("not-a-uuid");
        assertNull(props.getVipSegmentId());
    }

    @Test
    void riskProperties_getVipSegmentId_parsesValidUuid() {
        RiskProperties props = new RiskProperties();
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        props.setVipSegmentId(uuid);
        assertEquals(java.util.UUID.fromString(uuid), props.getVipSegmentId());
    }
}
