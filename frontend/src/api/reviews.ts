import api from "../api";
import type {
  HelpfulVote,
  ModerationAction,
  ReportReason,
  ReportStatus,
  Review,
  ReviewMedia,
  ReviewReport,
  ReviewSearchHit,
  ReviewSummary,
} from "../types/review";

export interface ReviewListFilters {
  page?: number;
  size?: number;
  sort?: "createdAt" | "rating" | "helpfulCount";
  direction?: "asc" | "desc";
  rating?: number[];
  verifiedOnly?: boolean;
  hasMedia?: boolean;
}

export interface CreateReviewPayload {
  rating: number;
  title?: string;
  body?: string;
}

export interface UpdateReviewPayload {
  rating?: number;
  title?: string;
  body?: string;
}

interface PagedResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface PresignResponse {
  uploadUrl: string;
  fileUrl: string;
  key: string;
  expiresIn: number;
}

export const reviewsApi = {
  list: (companyId: number, productId: number, filters: ReviewListFilters = {}) =>
    api.get<PagedResponse<Review>>(
      `/companies/${companyId}/products/${productId}/reviews`,
      { params: filters },
    ),

  summary: (companyId: number, productId: number) =>
    api.get<ReviewSummary>(
      `/companies/${companyId}/products/${productId}/reviews/summary`,
    ),

  getMine: (companyId: number, productId: number) =>
    api.get<Review>(`/companies/${companyId}/products/${productId}/reviews/me`),

  create: (companyId: number, productId: number, payload: CreateReviewPayload) =>
    api.post<Review>(`/companies/${companyId}/products/${productId}/reviews`, payload),

  update: (companyId: number, productId: number, payload: UpdateReviewPayload) =>
    api.patch<Review>(`/companies/${companyId}/products/${productId}/reviews/me`, payload),

  remove: (companyId: number, productId: number) =>
    api.delete(`/companies/${companyId}/products/${productId}/reviews/me`),

  voteHelpful: (companyId: number, productId: number, reviewId: number) =>
    api.post<HelpfulVote>(
      `/companies/${companyId}/products/${productId}/reviews/${reviewId}/helpful`,
    ),

  removeHelpful: (companyId: number, productId: number, reviewId: number) =>
    api.delete<HelpfulVote>(
      `/companies/${companyId}/products/${productId}/reviews/${reviewId}/helpful`,
    ),

  presignMediaUpload: (contentType: string) =>
    api.post<PresignResponse>("/upload/presign", { folder: "REVIEW_MEDIA", contentType }),

  attachMedia: (companyId: number, productId: number, reviewId: number, url: string) =>
    api.post<ReviewMedia>(
      `/companies/${companyId}/products/${productId}/reviews/${reviewId}/media`,
      { url },
    ),

  deleteMedia: (companyId: number, productId: number, reviewId: number, mediaId: number) =>
    api.delete(
      `/companies/${companyId}/products/${productId}/reviews/${reviewId}/media/${mediaId}`,
    ),

  reportReview: (
    companyId: number,
    productId: number,
    reviewId: number,
    payload: { reason: ReportReason; detail?: string },
  ) =>
    api.post<void>(
      `/companies/${companyId}/products/${productId}/reviews/${reviewId}/report`,
      payload,
    ),

  search: (
    companyId: number,
    productId: number,
    params: {
      q?: string;
      rating?: number[];
      verifiedOnly?: boolean;
      sort?: "relevance" | "createdAt" | "helpfulCount";
      page?: number;
      size?: number;
    } = {},
  ) =>
    api.get<PagedResponse<ReviewSearchHit>>(
      `/companies/${companyId}/products/${productId}/reviews/search`,
      { params },
    ),
};

export const reviewAdminApi = {
  listReports: (params: { status?: ReportStatus; page?: number; size?: number } = {}) =>
    api.get<PagedResponse<ReviewReport>>("/admin/reviews/reports", { params }),

  moderate: (reviewId: number, action: ModerationAction) =>
    api.post<void>(`/admin/reviews/${reviewId}/moderate`, { action }),

  reindex: () => api.post<{ message: string }>("/admin/reviews/reindex"),
};
