package backend.configurations.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AftershipConfiguration {

    @Bean("aftershipRestTemplate")
    public RestTemplate aftershipRestTemplate() {
        return OAuthHttpClientConfig.buildRestTemplateWithTimeouts(5_000, 10_000);
    }
}
