import { z } from "zod";

/**
 * UTC YYYY-MM-DD string for `today + n` days. Computed entirely in UTC so the picker's
 * bounds match the backend, which resolves "today" via LocalDate.now(ZoneOffset.UTC).
 * (Using local setHours/setDate here would drift by a day for users ahead of UTC.)
 */
function isoDay(offsetDays: number): string {
  const d = new Date();
  d.setUTCHours(0, 0, 0, 0);
  d.setUTCDate(d.getUTCDate() + offsetDays);
  return d.toISOString().slice(0, 10);
}

export const MIN_DELIVERY_DATE = isoDay(1); // tomorrow
export const MAX_DELIVERY_DATE = isoDay(14); // today + 14 days

export const deliverySlotSchema = z.object({
  preferredDeliveryDate: z
    .string()
    .min(1, "Please choose a delivery date")
    .refine((d) => d >= MIN_DELIVERY_DATE, "Delivery date must be tomorrow or later")
    .refine((d) => d <= MAX_DELIVERY_DATE, "Delivery date must be within 14 days"),
  preferredDeliveryWindow: z.enum(["MORNING", "AFTERNOON", "EVENING"], {
    required_error: "Please choose a time window",
  }),
});

export type DeliverySlotValues = z.infer<typeof deliverySlotSchema>;

export const markSlotUnavailableSchema = z.object({
  reason: z.string().max(500, "Reason must be 500 characters or fewer").optional(),
});

export type MarkSlotUnavailableValues = z.infer<typeof markSlotUnavailableSchema>;
