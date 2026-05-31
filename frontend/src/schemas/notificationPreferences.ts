import { z } from "zod";

export const notificationPreferencesSchema = z.object({
  pushEnabled: z.boolean(),
  smsEnabled: z.boolean(),
  smsPhoneNumber: z.string().max(30, "Phone number must be at most 30 characters").nullable().optional(),
});

export type NotificationPreferencesFormValues = z.infer<typeof notificationPreferencesSchema>;
