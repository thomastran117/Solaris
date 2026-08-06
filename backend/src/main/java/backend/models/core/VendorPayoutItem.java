package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import backend.models.enums.PayoutEntryType;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vendor_payout_items", indexes = {
        @Index(name = "idx_vpi_payout", columnList = "payout_id"),
        @Index(name = "idx_vpi_sub_order", columnList = "sub_order_id"),
        @Index(name = "idx_vpi_commission_record", columnList = "commission_record_id")
})
public class VendorPayoutItem {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_id", nullable = false)
    private VendorPayout payout;

    /** Loose FK to sub_orders.id. Null for adjustment entries. */
    @Column(nullable = true, name = "sub_order_id")
    private Long subOrderId;

    /** Loose FK to commission_records.id. Null for adjustment and refund entries. */
    @Column(nullable = true, name = "commission_record_id")
    private Long commissionRecordId;

    /** Loose FK to vendor_adjustments.id. Non-null only for ADJUSTMENT entries. */
    @Column(nullable = true, name = "adjustment_id")
    private Long adjustmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutEntryType entryType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    /** Net amount credited to the vendor for this line. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;
}
