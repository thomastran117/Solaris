package backend.dtos.responses.product;

import java.util.UUID;

public record ProductOptionResponse(UUID id, String name, int position) {}
