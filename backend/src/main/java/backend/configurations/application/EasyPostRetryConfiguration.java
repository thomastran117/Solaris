package backend.configurations.application;

import com.easypost.exception.API.GatewayTimeoutError;
import com.easypost.exception.API.InternalServerError;
import com.easypost.exception.API.RateLimitError;
import com.easypost.exception.API.ServiceUnavailableError;
import com.easypost.exception.API.TimeoutError;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.classify.Classifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import backend.configurations.environment.EnvironmentSetting;

/**
 * Retry configuration for EasyPost API calls (Feature 13). Only retries on transient
 * errors: connection/read timeouts, 5xx server errors, gateway timeouts, and rate-limit
 * (429). Validation errors (bad/missing address) are NOT retried — they will fail fast and
 * the rate service degrades to the flat-rate fallback.
 */
@Configuration
public class EasyPostRetryConfiguration {

    private final EnvironmentSetting env;

    public EasyPostRetryConfiguration(EnvironmentSetting env) {
        this.env = env;
    }

    @Bean("easyPostRetryTemplate")
    @Qualifier("easyPostRetryTemplate")
    public RetryTemplate easyPostRetryTemplate() {
        EnvironmentSetting.Retry retry = env.getEasyPost().getRetry();

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(retry.getInitialIntervalMs());
        backOff.setMultiplier(retry.getMultiplier());
        backOff.setMaxInterval(retry.getMaxIntervalMs());

        ExceptionClassifierRetryPolicy retryPolicy = new ExceptionClassifierRetryPolicy();
        retryPolicy.setExceptionClassifier((Classifier<Throwable, RetryPolicy>) t -> {
            if (isTransientEasyPostError(t)) {
                return new SimpleRetryPolicy(retry.getMaxAttempts());
            }
            return new NeverRetryPolicy();
        });

        RetryTemplate template = new RetryTemplate();
        template.setBackOffPolicy(backOff);
        template.setRetryPolicy(retryPolicy);
        return template;
    }

    /**
     * Determines whether the exception is a transient EasyPost error worth retrying.
     * Walks the cause chain to find EasyPost exceptions wrapped by Spring retry.
     */
    private static boolean isTransientEasyPostError(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof TimeoutError
                    || current instanceof GatewayTimeoutError
                    || current instanceof ServiceUnavailableError
                    || current instanceof InternalServerError
                    || current instanceof RateLimitError) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
