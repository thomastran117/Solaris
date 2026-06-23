package backend.services.pricing;

/**
 * Where an order ships to (or is collected from), used to resolve a sales-tax rate. For DELIVERY
 * this is the ship-to address; for PICKUP it is the store location's address. {@code postalCode}
 * is optional (empty/blank when unknown). Normalisation to uppercase happens in the tax service.
 */
public record TaxDestination(String country, String state, String postalCode) {

    public static TaxDestination of(String country, String state, String postalCode) {
        return new TaxDestination(country, state, postalCode);
    }
}
