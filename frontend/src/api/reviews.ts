import api from "../api";
import type {
  HelpfulVote,
  ModerationAction,
  Review,
  ReviewMedia,
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
  list: (companyId: string, productId: string, filters: ReviewListFilters = {}) =>
    api.get<PagedResponse<Review>>(
      `/companies/${companyId}/products/${productId}/reviews`,
      { params: filters },
    ),

  summary: (companyId: string, productId: string) =>
    api.get<ReviewSummary>(
      `/companies/${companyId}/products/${productId}/reviews/summary`,
    ),

  getMine: (companyId: string, productId: string) =>
    api.get<Review>(`/companies/${companyId}/products/${productId}/reviews/me`),

  create: (companyId: string, productId: string, payload: CreateReviewPayload) =>
    api.post<Review>(`/companies/${companyId}/products/${productId}/reviews`, payload),

  update: (companyId: string, productId: string, payload: UpdateReviewPayload) =>
    api.patch<Review>(`/companies/${companyId}/products/${productId}/reviews/me`, payload),

  remove: (companyId: string, productId: string) =>
    api.delete(`/companies/${companyId}/products/${productId}/reviews/me`),

  voteHelpful: (companyId: string, productId: string, reviewId: string) =>
    api.post<HelpfulVote>(
      `/companies/${companyId}/products/${productId}/reviews/${reviewId}/helpful`,
    ),

  removeHelpful: (companyId: string, productId: string, reviewId: string) =>
    api.delete<HelpfulVote>(
      `/companies/${companyId}/products/${productId}/reviews/${reviewId}/helpful`,
    ),

  presignMediaUpload: (contentType: string) =>
    api.post<PresignResponse>("/upload/presign", { folder: "REVIEW_MEDIA", contentType }),

  attachMedia: (companyId: string, productId: string, reviewId: string, url: string) =>
    api.post<ReviewMedia>(
      `/companies/${companyId}/products/${productId}/reviews/${reviewId}/media`,
      { url },
    ),

  deleteMedia: (companyId: string, productId: string, reviewId: string, mediaId: string) =>
    api.delete(
      `/companies/${companyId}/products/${productId}/reviews/${reviewId}/media/${mediaId}`,
    ),

  search: (
    companyId: string,
    productId: string,
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
  moderate: (reviewId: string, action: ModerationAction) =>
    api.post<void>(`/admin/reviews/${reviewId}/moderate`, { action }),

  reindex: () => api.post<{ message: string }>("/admin/reviews/reindex"),
};
