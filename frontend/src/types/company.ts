/**
 * Anonymous-safe projection of a company. Mirrors backend {@code PublicCompanyResponse}
 * — no owner id, contact email/phone, tax id, registration number, or employee count.
 */
export interface PublicCompanyResponse {
  id: string;
  name: string;
  city: string | null;
  country: string | null;
  logoUrl: string | null;
  website: string | null;
  description: string | null;
  industry: string | null;
  foundedYear: number | null;
}

export type AnnouncementType = "GENERAL" | "NEW_PRODUCT" | "SALE" | "NEW_COLLECTION";
export type AnnouncementStatus = "DRAFT" | "PUBLISHED";

export interface AnnouncementResponse {
  id: string;
  companyId: string;
  companyName: string;
  companyLogoUrl: string | null;
  title: string;
  body: string;
  type: AnnouncementType;
  status: AnnouncementStatus;
  productId: string | null;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface FollowStatusResponse {
  following: boolean;
  notificationsEnabled: boolean;
  followerCount: number;
}

export interface CompanyFollowResponse {
  id: string;
  companyId: string;
  followedAt: string;
  notificationsEnabled: boolean;
}

export interface FollowedCompanyResponse {
  followId: string;
  companyId: string;
  companyName: string;
  companyLogoUrl: string | null;
  industry: string | null;
  notificationsEnabled: boolean;
  followedAt: string;
}
