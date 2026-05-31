package backend.services.intf.pricing;

import backend.models.core.SubOrder;

import java.math.BigDecimal;

public interface CommissionEngine {

    /**
     * Computes the commission for a fully-persisted sub-order.
     * The result is idempotent — calling it twice with the same sub-order returns the same answer.
     */
    CommissionResult compute(SubOrder subOrder);

    /**
     * @param commissionRate  applied rate (e.g. 0.15 = 15%)
     * @param grossAmount     sub-order total before commission
     * @param commissionAmount marketplace take
     * @param netVendorAmount amount owed to the vendor
     * @param currency        ISO 4217 code
     */
    record CommissionResult(
            BigDecimal commissionRate,
            BigDecimal grossAmount,
            BigDecimal commissionAmount,
            BigDecimal netVendorAmount,
            String currency
    ) {}
}
