import { z } from "zod";

export const FEEDBACK_CATEGORIES = [
  { value: "BUG_REPORT",      label: "Bug Report" },
  { value: "FEATURE_REQUEST", label: "Feature Request" },
  { value: "UI_UX",           label: "UI / UX" },
  { value: "PERFORMANCE",     label: "Performance" },
  { value: "CHECKOUT",        label: "Checkout" },
  { value: "SEARCH",          label: "Search" },
  { value: "ACCOUNT",         label: "Account" },
  { value: "OTHER",           label: "Other" },
] as const;

export const feedbackFormSchema = z.object({
  category: z.enum(
    ["BUG_REPORT", "FEATURE_REQUEST", "UI_UX", "PERFORMANCE", "CHECKOUT", "SEARCH", "ACCOUNT", "OTHER"],
    { required_error: "Please select a category" }
  ),
  message: z
    .string()
    .min(10, "Message must be at least 10 characters")
    .max(5000, "Message must be 5000 characters or fewer"),
  rating: z.number().int().min(1).max(5).nullable().optional(),
  pageContext: z.string().max(500).optional(),
});

export type FeedbackFormValues = z.infer<typeof feedbackFormSchema>;

export const feedbackFormDefaults: FeedbackFormValues = {
  category: "OTHER",
  message: "",
  rating: null,
  pageContext: undefined,
};
