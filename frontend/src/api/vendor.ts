import api from "../api";
import type { MarketplaceVendor } from "../stores/vendorSlice";

export interface VendorDocument {
  id: number;
  marketplaceVendorId: number;
  documentType: string;
  s3Key: string;
  uploadedAt: string;
  verifiedAt: string | null;
  rejectionNote: string | null;
}

export interface StripeOnboardingLink {
  url: string;
  stripeConnectAccountId: string;
}

export interface ApplyVendorRequest {
  vendorCompanyId: number;
}

export interface UpdateVendorProfileRequest {
  displayName?: string;
  description?: string;
  supportEmail?: string;
  supportPhone?: string;
  website?: string;
}

export interface SubmitVendorTaxRequest {
  taxId: string;
  legalBusinessName: string;
  businessAddress: string;
  country: string;
}

export interface GenerateStripeOnboardingLinkRequest {
  returnUrl: string;
  refreshUrl: string;
}

export interface VendorActionRequest {
  reason?: string;
  tier?: "STANDARD" | "PREMIUM" | "STRATEGIC";
}

export const vendorApi = {
  apply: (marketplaceId: number, data: ApplyVendorRequest) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/apply`, data),

  updateProfile: (marketplaceId: number, vendorId: number, data: UpdateVendorProfileRequest) =>
    api.patch<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/onboarding/profile`, data),

  submitTax: (marketplaceId: number, vendorId: number, data: SubmitVendorTaxRequest) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/onboarding/tax`, data),

  getStripeOnboardingLink: (
    marketplaceId: number,
    vendorId: number,
    data: GenerateStripeOnboardingLinkRequest
  ) =>
    api.post<StripeOnboardingLink>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/onboarding/stripe-link`,
      data
    ),

  recordDocument: (
    marketplaceId: number,
    vendorId: number,
    documentType: string,
    s3Key: string
  ) =>
    api.post<VendorDocument>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/onboarding/documents`,
      null,
      { params: { documentType, s3Key } }
    ),

  listDocuments: (marketplaceId: number, vendorId: number) =>
    api.get<VendorDocument[]>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/onboarding/documents`
    ),

  submitForReview: (marketplaceId: number, vendorId: number) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/submit`),

  getMyRecord: (marketplaceId: number) =>
    api.get<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/me`),

  list: (marketplaceId: number, status?: string, page = 0, size = 20) =>
    api.get<MarketplaceVendor[]>(`/marketplaces/${marketplaceId}/vendors`, {
      params: { status, page, size },
    }),

  get: (marketplaceId: number, vendorId: number) =>
    api.get<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}`),

  approve: (marketplaceId: number, vendorId: number, data: VendorActionRequest) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/approve`, data),

  reject: (marketplaceId: number, vendorId: number, data: VendorActionRequest) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/reject`, data),

  suspend: (marketplaceId: number, vendorId: number, data: VendorActionRequest) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/suspend`, data),

  reinstate: (marketplaceId: number, vendorId: number) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/reinstate`),

  requestMoreInfo: (marketplaceId: number, vendorId: number, data: VendorActionRequest) =>
    api.post<MarketplaceVendor>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/needs-info`,
      data
    ),
};
