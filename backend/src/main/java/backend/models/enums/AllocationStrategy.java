package backend.models.enums;

public enum AllocationStrategy {
    /** Pick the location with the highest stock. Safe default — works without location metadata. */
    HIGHEST_STOCK,

    /** Pick the location closest to the buyer. Requires buyerLatitude/buyerLongitude on the order request.
     *  Falls back to HIGHEST_STOCK if buyer coords are absent or no location has coordinates. */
    NEAREST,

    /** Pick the location with the lowest fulfillmentCost.
     *  Falls back to HIGHEST_STOCK if no location has a fulfillmentCost set. */
    CHEAPEST
}
