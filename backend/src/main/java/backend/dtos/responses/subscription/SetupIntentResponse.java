package backend.dtos.responses.subscription;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SetupIntentResponse {
    private String setupIntentId;
    private String clientSecret;
    private String stripeCustomerId;
}
