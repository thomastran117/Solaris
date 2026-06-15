import { z } from "zod";

/** Local YYYY-MM-DD string for `today + n` days, matching the backend's allowed window. */
function isoDay(offsetDays: number): string {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() + offsetDays);
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
