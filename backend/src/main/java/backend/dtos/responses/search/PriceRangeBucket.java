package backend.dtos.responses.search;

public record PriceRangeBucket(String label, Double min, Double max, long count) {}
