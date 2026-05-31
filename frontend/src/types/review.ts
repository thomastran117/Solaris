export type { ReportStatus } from "./report";

export type ReviewStatus = "PUBLISHED" | "HIDDEN" | "PENDING_MODERATION" | "REMOVED";

export interface ReviewMedia {
  id: string;
  url: string;
  position: number;
}

export interface Review {
  id: string;
  productId: string;
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
  productId: string;
  averageRating: number;
  total: number;
  verifiedCount: number;
  distribution: Record<string, number>;
}

export interface HelpfulVote {
  reviewId: string;
  helpfulCount: number;
  userHasVoted: boolean;
}

export interface ReviewSearchHit {
  id: string;
  productId: string;
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

export type ModerationAction = "PUBLISH" | "HIDE" | "REMOVE";
