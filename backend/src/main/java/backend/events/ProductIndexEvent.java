package backend.events;

import backend.models.core.Product;
import java.util.UUID;

public record ProductIndexEvent(Product product, UUID companyId) {}
