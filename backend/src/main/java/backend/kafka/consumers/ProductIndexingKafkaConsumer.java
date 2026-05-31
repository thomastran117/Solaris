package backend.kafka.consumers;

import backend.events.activity.BundleChangedEvent;
import backend.events.activity.ProductChangedEvent;
import backend.kafka.workers.ProductIndexingService;
import backend.repositories.BundleRepository;
import backend.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProductIndexingKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexingKafkaConsumer.class);

    private final ProductIndexingService indexingService;
    private final ProductRepository productRepository;
    private final BundleRepository bundleRepository;

    public ProductIndexingKafkaConsumer(
            ProductIndexingService indexingService,
            ProductRepository productRepository,
            BundleRepository bundleRepository) {
        this.indexingService = indexingService;
        this.productRepository = productRepository;
        this.bundleRepository = bundleRepository;
    }

    @KafkaListener(topics = "${app.kafka.topics.product-events}", groupId = "indexer",
                   containerFactory = "indexerKafkaListenerContainerFactory")
    public void onProductEvent(ProductChangedEvent event) {
        switch (event.changeType()) {
            case UPDATED, CREATED -> productRepository.findByIdWithCompanyOwner(event.productId())
                    .ifPresentOrElse(
                            p -> indexingService.indexProduct(p, p.getCompany().getId()),
                            () -> log.warn("[SEARCH INDEX] Product {} not found for indexing", event.productId())
                    );
            case DELETED -> indexingService.removeProduct(event.productId());
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.bundle-events}", groupId = "indexer",
                   containerFactory = "indexerKafkaListenerContainerFactory")
    public void onBundleEvent(BundleChangedEvent event) {
        switch (event.changeType()) {
            case UPDATED, CREATED -> bundleRepository.findById(event.bundleId())
                    .ifPresentOrElse(
                            indexingService::indexBundle,
                            () -> log.warn("[SEARCH INDEX] Bundle {} not found for indexing", event.bundleId())
                    );
            case DELETED -> indexingService.removeBundle(event.bundleId());
        }
    }
}
