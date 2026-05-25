import { z } from "zod";
import type { ReportReason, ReportTargetType } from "../types/report";

export const REPORT_REASON_LABELS: Record<ReportReason, { label: string; hint: string }> = {
  SPAM:              { label: "Spam or advertising",        hint: "Promotional or repetitive content" },
  OFFENSIVE_CONTENT: { label: "Offensive or harmful",       hint: "Hate speech, harassment, or graphic content" },
  MISLEADING_INFO:   { label: "Misleading information",     hint: "False claims or deceptive descriptions" },
  COUNTERFEIT:       { label: "Counterfeit product",        hint: "Fake or unauthorized brand goods" },
  HARASSMENT:        { label: "Harassment or threats",      hint: "Targeting or threatening behaviour" },
  FRAUD:             { label: "Fraudulent activity",        hint: "Scam, fake listings, or payment fraud" },
  COPYRIGHT_VIOLATION: { label: "Copyright violation",      hint: "Stolen images, text, or brand assets" },
  OFF_TOPIC:         { label: "Off topic",                  hint: "Doesn't relate to the subject" },
  FAKE:              { label: "Fake or inauthentic",        hint: "Manufactured or paid content" },
  OTHER:             { label: "Other",                      hint: "Something else worth flagging" },
};

// Reasons shown per target type
export const REASONS_FOR_TARGET: Record<ReportTargetType, ReportReason[]> = {
  PRODUCT: ["SPAM", "OFFENSIVE_CONTENT", "MISLEADING_INFO", "COUNTERFEIT", "FRAUD", "COPYRIGHT_VIOLATION", "FAKE", "OTHER"],
  COMPANY: ["SPAM", "OFFENSIVE_CONTENT", "MISLEADING_INFO", "FRAUD", "COPYRIGHT_VIOLATION", "OTHER"],
  REVIEW:  ["SPAM", "OFFENSIVE_CONTENT", "OFF_TOPIC", "FAKE", "OTHER"],
  USER:    ["SPAM", "OFFENSIVE_CONTENT", "HARASSMENT", "OTHER"],
};

export const reportFormSchema = z.object({
  reason: z.enum([
    "SPAM", "OFFENSIVE_CONTENT", "MISLEADING_INFO", "COUNTERFEIT",
    "HARASSMENT", "FRAUD", "COPYRIGHT_VIOLATION", "OFF_TOPIC", "FAKE", "OTHER",
  ] as const, { required_error: "Please select a reason" }),
  title: z
    .string()
    .min(5, "Title must be at least 5 characters")
    .max(150, "Title must be 150 characters or fewer"),
  description: z
    .string()
    .min(10, "Please describe the issue in more detail")
    .max(2000, "Description must be 2000 characters or fewer"),
  screenshotUrls: z.array(z.string()).max(5).default([]),
});

export type ReportFormValues = z.infer<typeof reportFormSchema>;

export const reportFormDefaults: ReportFormValues = {
  reason: "SPAM",
  title: "",
  description: "",
  screenshotUrls: [],
};
