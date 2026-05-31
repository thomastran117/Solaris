import api from "../api";
import type {
  AnnouncementResponse,
  CompanyFollowResponse,
  FollowStatusResponse,
  FollowedCompanyResponse,
} from "../types/company";

export interface CreateAnnouncementPayload {
  title: string;
  body: string;
  type: "GENERAL" | "NEW_PRODUCT" | "SALE" | "NEW_COLLECTION";
  productId?: string;
}

export interface UpdateAnnouncementPayload {
  title?: string;
  body?: string;
  type?: "GENERAL" | "NEW_PRODUCT" | "SALE" | "NEW_COLLECTION";
  productId?: string;
  publish?: boolean;
}

export interface PagedFollowedCompanies {
  items: FollowedCompanyResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface PagedAnnouncements {
  items: AnnouncementResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export const followApi = {
  follow: (companyId: string) =>
    api.post<CompanyFollowResponse>(`/companies/${companyId}/follow`),

  unfollow: (companyId: string) =>
    api.delete<void>(`/companies/${companyId}/follow`),

  getStatus: (companyId: string) =>
    api.get<FollowStatusResponse>(`/companies/${companyId}/follow/status`),

  setNotifications: (companyId: string, notificationsEnabled: boolean) =>
    api.patch<CompanyFollowResponse>(`/companies/${companyId}/follow`, {
      notificationsEnabled,
    }),

  listFollowing: (page = 0, size = 20) =>
    api.get<PagedFollowedCompanies>("/me/following", { params: { page, size } }),

  getFeed: (page = 0, size = 20) =>
    api.get<PagedAnnouncements>("/me/announcement-feed", { params: { page, size } }),
};

export const announcementsApi = {
  listPublished: (companyId: string, page = 0, size = 20) =>
    api.get<PagedAnnouncements>(`/companies/${companyId}/announcements`, {
      params: { page, size },
    }),

  listAll: (companyId: string, page = 0, size = 20) =>
    api.get<PagedAnnouncements>(`/companies/${companyId}/announcements/all`, {
      params: { page, size },
    }),

  create: (companyId: string, data: CreateAnnouncementPayload) =>
    api.post<AnnouncementResponse>(`/companies/${companyId}/announcements`, data),

  update: (companyId: string, annId: string, data: UpdateAnnouncementPayload) =>
    api.patch<AnnouncementResponse>(
      `/companies/${companyId}/announcements/${annId}`,
      data
    ),

  delete: (companyId: string, annId: string) =>
    api.delete<void>(`/companies/${companyId}/announcements/${annId}`),
};
