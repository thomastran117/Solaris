package backend.configurations.application;

import backend.exceptions.http.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * Guards against SSRF by validating that a webhook URL resolves to a publicly
 * routable address.
 *
 * Blocked ranges: loopback (127.x, ::1), site-local (10.x, 172.16-31.x,
 * 192.168.x), link-local (169.254.x), any-local (0.0.0.0), and the CGNAT
 * shared space (100.64.0.0/10).  Only HTTPS is accepted.
 *
 * The check can be disabled via {@code app.webhook.ssrf-check.enabled=false},
 * which is appropriate for integration tests where endpoint URLs are mocked.
 */
@Component
public class WebhookUrlValidator {

    @Value("${app.webhook.ssrf-check.enabled:true}")
    private boolean enabled;

    public void validate(String url) {
        if (!enabled) return;
        URL parsed;
        try {
            parsed = new URI(url).toURL();
        } catch (URISyntaxException | IllegalArgumentException | java.net.MalformedURLException e) {
            throw new BadRequestException("Webhook URL is malformed");
        }
        if (!"https".equalsIgnoreCase(parsed.getProtocol())) {
            throw new BadRequestException("Webhook URL must use HTTPS");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(parsed.getHost());
        } catch (UnknownHostException e) {
            throw new BadRequestException("Webhook URL host cannot be resolved: " + parsed.getHost());
        }
        for (InetAddress addr : addresses) {
            if (isBlocked(addr)) {
                throw new BadRequestException(
                        "Webhook URL must resolve to a publicly accessible address");
            }
        }
    }

    private boolean isBlocked(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress()
                || isCgnatAddress(addr);
    }

    private boolean isCgnatAddress(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length != 4) return false;
        // 100.64.0.0/10
        return (b[0] & 0xFF) == 100 && ((b[1] & 0xFF) >= 64 && (b[1] & 0xFF) <= 127);
    }
}
