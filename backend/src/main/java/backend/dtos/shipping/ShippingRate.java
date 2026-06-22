package backend.dtos.shipping;

/**
 * A single carrier rate option returned by a {@link backend.services.intf.shipping.ShippingRateService}.
 * This is both the cacheable unit (serialized to Redis) and the unit surfaced to the customer.
 *
 * @param rateId        provider's rate identifier (EasyPost rate IDs expire after ~15 min)
 * @param carrier       carrier name, e.g. "USPS", "UPS", "FedEx"
 * @param serviceName   human-readable service, e.g. "Priority", "Ground"
 * @param serviceCode   machine code for the service (EasyPost reuses the service string)
 * @param estimatedDays estimated transit days; {@code null} when the carrier does not provide one
 * @param totalCents    total shipping cost in the smallest currency unit
 * @param currency      ISO 4217 currency code (upper-case)
 */
public record ShippingRate(
        String rateId,
        String carrier,
        String serviceName,
        String serviceCode,
        Integer estimatedDays,
        long totalCents,
        String currency
) {}
