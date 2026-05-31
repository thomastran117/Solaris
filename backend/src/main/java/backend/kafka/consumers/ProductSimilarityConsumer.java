package backend.kafka.consumers;

import backend.events.activity.ProductChangedEvent;
import backend.repositories.ProductRepository;
import backend.services.impl.products.ProductSimilarityService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProductSimilarityConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductSimilarityConsumer.class);

    private final ProductSimilarityService productSimilarityService;
    private final ProductRepository productRepository;
    private final MeterRegistry meterRegistry;

    public ProductSimilarityConsumer(
            ProductSimilarityService productSimilarityService,
            ProductRepository productRepository,
            MeterRegistry meterRegistry) {
        this.productSimilarityService = productSimilarityService;
        this.productRepository = productRepository;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.product-events}",
            groupId = "similarity-product-events-consumer")
    public void onProductEvent(ProductChangedEvent event) {
        switch (event.changeType()) {
            case CREATED, UPDATED -> {
                boolean exists = productRepository.existsById(event.productId());
                if (!exists) {
                    log.warn("[SimilarityConsumer] Product {} not found, skipping recompute", event.productId());
                    return;
                }
                try {
                    productSimilarityService.recompute(event.productId());
                } catch (Exception e) {
                    log.warn("[SimilarityConsumer] recompute failed for {}: {} — nightly catch-up will retry",
                            event.productId(), e.getMessage());
                    meterRegistry.counter("similarity_recompute_failed_total").increment();
                }
            }
            case DELETED -> {
                // FK cascade on product_similarity handles cleanup — nothing to do here
            }
        }
    }
}
