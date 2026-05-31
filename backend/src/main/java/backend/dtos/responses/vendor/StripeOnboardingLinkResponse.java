package backend.dtos.responses.vendor;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StripeOnboardingLinkResponse {
    private String url;
    private String stripeConnectAccountId;
}
