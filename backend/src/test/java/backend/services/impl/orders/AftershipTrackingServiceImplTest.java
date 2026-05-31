package backend.services.impl.orders;

import backend.dtos.responses.order.TrackingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class AftershipTrackingServiceImplTest {

    private static final String API_KEY = "test-key";
    private static final String BASE_URL = "https://api.aftership.com/v4";

    private RestTemplate restTemplate;
    private AftershipTrackingServiceImpl service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new AftershipTrackingServiceImpl(restTemplate, new ObjectMapper().findAndRegisterModules(), API_KEY, BASE_URL);
    }

    // ─── registerTracking ─────────────────────────────────────────────────────

    @Test
    void registerTracking_postsToAftershipApi_withCorrectPayload() {
        UUID orderId = UUID.randomUUID();
        when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn("{}");

        service.registerTracking(orderId, "1Z999AA10123456784", "UPS");

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq(BASE_URL + "/trackings"), captor.capture(), eq(String.class));
        String body = captor.getValue().getBody();
        assertNotNull(body);
        assertTrue(body.contains("1Z999AA10123456784"));
        assertTrue(body.contains("ups"));
    }

    @Test
    void registerTracking_doesNotThrow_whenAftershipReturnsError() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

        assertDoesNotThrow(() -> service.registerTracking(UUID.randomUUID(), "TRACK-123", "FEDEX"));
    }

    @Test
    void registerTracking_doesNotThrow_whenAftershipIsUnreachable() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertDoesNotThrow(() -> service.registerTracking(UUID.randomUUID(), "TRACK-123", "UPS"));
    }

    // ─── getTrackingEvents ────────────────────────────────────────────────────

    @Test
    void getTrackingEvents_returnsCheckpointsAsMappedTrackingEvents() {
        String json = """
                {
                  "data": {
                    "tracking": {
                      "checkpoints": [
                        {
                          "tag": "InTransit",
                          "message": "Package in transit",
                          "city": "Seattle",
                          "checkpoint_time": "2026-05-20T10:00:00Z"
                        }
                      ]
                    }
                  }
                }
                """;
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        List<TrackingEvent> events = service.getTrackingEvents("TRACK-123", "UPS");

        assertEquals(1, events.size());
        TrackingEvent event = events.get(0);
        assertEquals("InTransit", event.status());
        assertEquals("Package in transit", event.message());
        assertEquals("Seattle", event.location());
        assertNotNull(event.occurredAt());
    }

    @Test
    void getTrackingEvents_returnsEmptyList_whenAftershipReturns404() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        List<TrackingEvent> events = service.getTrackingEvents("UNKNOWN-TRACK", "UPS");

        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void getTrackingEvents_returnsEmptyList_whenNetworkFails() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Timeout"));

        List<TrackingEvent> events = service.getTrackingEvents("TRACK-123", "DHL");

        assertNotNull(events);
        assertTrue(events.isEmpty());
    }
}
