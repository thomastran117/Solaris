export type ReportTargetType = "PRODUCT" | "COMPANY" | "REVIEW" | "USER";

export type ReportReason =
  | "SPAM"
  | "OFFENSIVE_CONTENT"
  | "MISLEADING_INFO"
  | "COUNTERFEIT"
  | "HARASSMENT"
  | "FRAUD"
  | "COPYRIGHT_VIOLATION"
  | "OFF_TOPIC"
  | "FAKE"
  | "OTHER";

export const REPORT_STATUSES = {
  OPEN: "Open",
  ACTIONED: "Actioned",
  DISMISSED: "Dismissed",
} as const;

export type ReportStatus = keyof typeof REPORT_STATUSES;

export interface ReportScreenshot {
  id: string;
  url: string;
  position: number;
}

export interface Report {
  id: string;
  targetType: ReportTargetType;
  targetId: string;
  reporterId: string;
  reason: ReportReason;
  title: string;
  description: string;
  status: ReportStatus;
  screenshots: ReportScreenshot[];
  createdAt: string;
  resolvedAt: string | null;
  resolvedBy: string | null;
  targetName: string | null;
  reporterName: string | null;
}
