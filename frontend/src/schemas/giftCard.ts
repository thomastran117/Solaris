import { z } from "zod";

export const redeemGiftCardSchema = z.object({
  code: z
    .string()
    .min(1, "Gift card code is required")
    .max(20, "Code is too long")
    .regex(/^[A-Z0-9]+$/i, "Code must contain only letters and numbers"),

  amountCents: z
    .number({ invalid_type_error: "Amount is required" })
    .int("Amount must be a whole number of cents")
    .positive("Amount must be greater than zero"),
});

export type RedeemGiftCardFormValues = z.infer<typeof redeemGiftCardSchema>;

export const checkBalanceSchema = z.object({
  code: z
    .string()
    .min(1, "Gift card code is required")
    .max(20, "Code is too long"),
});

export type CheckBalanceFormValues = z.infer<typeof checkBalanceSchema>;
