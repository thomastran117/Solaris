package backend.services.intf.products;

import backend.dtos.responses.products.PriceWatcherResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PriceWatchService {

    PriceWatcherResponse watchProduct(UUID userId, UUID productId);

    void unwatchProduct(UUID userId, UUID productId);

    Page<PriceWatcherResponse> getWatchedProducts(UUID userId, Pageable pageable);
}
