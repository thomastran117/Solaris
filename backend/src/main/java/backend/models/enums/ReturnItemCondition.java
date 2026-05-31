package backend.models.enums;

public enum ReturnItemCondition {
    /** Item is in original or like-new condition and can be put back on the shelf. */
    RESELLABLE,
    /** Item has cosmetic or structural damage that reduces resale value. */
    DAMAGED,
    /** Item does not function as intended (manufacturer or usage defect). */
    DEFECTIVE,
    /** Item is missing components, accessories, or packaging. */
    MISSING_PARTS,
    /** Item has been opened but is otherwise intact. */
    OPENED,
    /** Catch-all for conditions not covered by the other values. */
    OTHER
}
