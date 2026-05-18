export type ReviewStatus = "PUBLISHED" | "HIDDEN" | "PENDING_MODERATION" | "REMOVED";

export interface ReviewMedia {
  id: number;
  url: string;
  position: number;
}

export interface Review {
  id: number;
  productId: number;
  reviewerId: string;
  reviewerFirstName: string;
  reviewerLastName: string;
  rating: number;
  title: string | null;
  body: string | null;
  status: ReviewStatus;
  verifiedPurchase: boolean;
  helpfulCount: number;
  media: ReviewMedia[];
  createdAt: string;
  updatedAt: string;
}

export interface ReviewSummary {
  productId: number;
  averageRating: number;
  total: number;
  verifiedCount: number;
  distribution: Record<string, number>;
}

export interface HelpfulVote {
  reviewId: number;
  helpfulCount: number;
  userHasVoted: boolean;
}

export interface ReviewSearchHit {
  id: number;
  productId: number;
  reviewerId: string | null;
  reviewerName: string;
  rating: number;
  title: string | null;
  body: string | null;
  verifiedPurchase: boolean;
  hasMedia: boolean;
  helpfulCount: number;
  status: ReviewStatus;
  createdAt: string;
  updatedAt: string;
  titleHighlights: string[];
  bodyHighlights: string[];
  relevanceScore: number;
}

export type ReportReason = "SPAM" | "OFFENSIVE" | "OFF_TOPIC" | "FAKE" | "OTHER";
export type ReportStatus = "OPEN" | "ACTIONED" | "DISMISSED";
export type ModerationAction = "PUBLISH" | "HIDE" | "REMOVE";

export interface ReviewReport {
  id: number;
  reviewId: number;
  reporterId: string;
  reason: ReportReason;
  detail: string | null;
  status: ReportStatus;
  createdAt: string;
  resolvedAt: string | null;
  resolvedBy: string | null;

  productId: number | null;
  reviewerId: string | null;
  reviewerName: string;
  rating: number;
  reviewTitle: string | null;
  reviewBody: string | null;
  reviewStatus: ReviewStatus | null;
  reportCount: number;
  media: ReviewMedia[];
}
