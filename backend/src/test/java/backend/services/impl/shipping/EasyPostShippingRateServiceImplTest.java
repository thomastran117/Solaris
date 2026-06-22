package backend.services.impl.shipping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.easypost.exception.EasyPostException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.retry.support.RetryTemplate;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.shipping.ShippingRate;
import backend.dtos.shipping.ShippingRateRequest;
import backend.services.intf.CacheService;

class EasyPostShippingRateServiceImplTest {

    private CacheService cacheService;
    private EnvironmentSetting env;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<ShippingRate> EASYPOST_RATES = List.of(
            new ShippingRate("rate_1", "USPS", "Priority", "Priority", 2, 799, "USD"),
            new ShippingRate("rate_2", "UPS", "Ground", "Ground", 5, 599, "USD"));

    /** Test seam: overrides the raw EasyPost network call so the cache/degradation logic is unit-testable. */
    private static class StubService extends EasyPostShippingRateServiceImpl {
        List<ShippingRate> toReturn = EASYPOST_RATES;
        EasyPostException toThrow;
        int fetchCalls;

        StubService(EnvironmentSetting env, CacheService cache, ObjectMapper mapper) {
            super(env, cache, mapper, RetryTemplate.builder().maxAttempts(1).build());
        }

        @Override
        protected List<ShippingRate> fetchFromEasyPost(ShippingRateRequest req) throws EasyPostException {
            fetchCalls++;
            if (toThrow != null) {
                throw toThrow;
            }
            return toReturn;
        }
    }

    private ShippingRateRequest req(int length, int width, int height) {
        return new ShippingRateRequest(
                "1 Vendor St", "Seattle", "WA", "98101", "US",
                "5 Buyer Ave", null, "Boston", "MA", "02108", "US",
                1000, length, width, height, "USD");
    }

    @BeforeEach
    void setUp() {
        cacheService = org.mockito.Mockito.mock(CacheService.class);
        env = new EnvironmentSetting();
    }

    private StubService withKey() {
        env.getEasyPost().setApiKey("test_key");
        StubService svc = new StubService(env, cacheService, objectMapper);
        svc.init();
        return svc;
    }

    @Test
    void shouldCallEasyPostAndCacheOnCacheMiss() throws Exception {
        when(cacheService.get(anyString())).thenReturn(null);
        StubService svc = withKey();

        List<ShippingRate> result = svc.getRates(req(10, 10, 10));

        assertEquals(EASYPOST_RATES, result);
        assertEquals(1, svc.fetchCalls);
        verify(cacheService).set(anyString(), anyString(), eq(900L));
    }

    @Test
    void shouldReturnCachedRatesAndSkipEasyPostOnCacheHit() throws Exception {
        String json = objectMapper.writeValueAsString(EASYPOST_RATES);
        when(cacheService.get(anyString())).thenReturn(json);
        StubService svc = withKey();

        List<ShippingRate> result = svc.getRates(req(10, 10, 10));

        assertEquals(EASYPOST_RATES, result);
        assertEquals(0, svc.fetchCalls);
        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void shouldReturnFlatRateAndNotCacheWhenEasyPostThrows() {
        when(cacheService.get(anyString())).thenReturn(null);
        StubService svc = withKey();
        svc.toThrow = new EasyPostException("boom");

        List<ShippingRate> result = svc.getRates(req(10, 10, 10));

        assertEquals(1, result.size());
        assertEquals("flat-rate", result.get(0).rateId());
        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void shouldReturnFlatRateWhenNoApiKeyConfigured() {
        when(cacheService.get(anyString())).thenReturn(null);
        // No api key -> client stays null, EasyPost is never called.
        StubService svc = new StubService(env, cacheService, objectMapper);
        svc.init();

        List<ShippingRate> result = svc.getRates(req(10, 10, 10));

        assertEquals(1, result.size());
        assertEquals("flat-rate", result.get(0).rateId());
        assertEquals(0, svc.fetchCalls);
    }

    @Test
    void shouldQuoteFlatRateFallbackInRequestCurrency() {
        when(cacheService.get(anyString())).thenReturn(null);
        StubService svc = new StubService(env, cacheService, objectMapper);
        svc.init(); // no key -> flat-rate-only mode
        ShippingRateRequest cadReq = new ShippingRateRequest(
                "1 Vendor St", "Toronto", "ON", "M5V", "CA",
                "5 Buyer Ave", null, "Vancouver", "BC", "V6B", "CA",
                1000, 10, 10, 10, "CAD");

        List<ShippingRate> result = svc.getRates(cadReq);

        assertEquals(1, result.size());
        assertEquals("flat-rate", result.get(0).rateId());
        assertEquals("CAD", result.get(0).currency());
    }

    @Test
    void shouldFallThroughToEasyPostWhenCacheReadThrows() {
        when(cacheService.get(anyString())).thenThrow(new RuntimeException("redis down"));
        StubService svc = withKey();

        List<ShippingRate> result = svc.getRates(req(10, 10, 10));

        assertEquals(EASYPOST_RATES, result);
        assertEquals(1, svc.fetchCalls);
    }

    @Test
    void shouldUseDistinctCacheKeysForDistinctDimensions() {
        when(cacheService.get(anyString())).thenReturn(null);
        StubService svc = withKey();

        svc.getRates(req(10, 10, 10));
        svc.getRates(req(20, 20, 20));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cacheService, times(2)).set(keys.capture(), anyString(), eq(900L));
        List<String> captured = keys.getAllValues();
        assertNotEquals(captured.get(0), captured.get(1));
        assertTrue(captured.get(0).startsWith("shipping-rates:"));
    }
}
