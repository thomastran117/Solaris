package backend.dtos.responses.product;

import java.util.UUID;

public record ProductAttributeResponse(UUID id, String name, String value, int displayOrder) {}
