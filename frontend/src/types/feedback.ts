export type FeedbackCategory =
  | "BUG_REPORT"
  | "FEATURE_REQUEST"
  | "UI_UX"
  | "PERFORMANCE"
  | "CHECKOUT"
  | "SEARCH"
  | "ACCOUNT"
  | "OTHER";

export type FeedbackStatus = "OPEN" | "UNDER_REVIEW" | "RESOLVED" | "CLOSED";

export interface Feedback {
  id: string;
  submittedById: string;
  submitterEmail: string;
  submitterName: string | null;
  category: FeedbackCategory;
  status: FeedbackStatus;
  message: string;
  rating: number | null;
  pageContext: string | null;
  reviewedAt: string | null;
  reviewedById: string | null;
  createdAt: string;
  updatedAt: string;
}
