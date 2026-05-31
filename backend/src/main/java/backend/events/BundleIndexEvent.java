package backend.events;

import backend.models.core.ProductBundle;

public record BundleIndexEvent(ProductBundle bundle) {}
