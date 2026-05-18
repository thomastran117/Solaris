import api from "../api";
import type { MarketplaceVendor } from "../stores/vendorSlice";

export interface VendorDocument {
  id: string;
  marketplaceVendorId: string;
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
  vendorCompanyId: string;
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
  apply: (marketplaceId: string, data: ApplyVendorRequest) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/apply`, data),

  updateProfile: (marketplaceId: string, vendorId: string, data: UpdateVendorProfileRequest) =>
    api.patch<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/onboarding/profile`, data),

  submitTax: (marketplaceId: string, vendorId: string, data: SubmitVendorTaxRequest) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/onboarding/tax`, data),

  getStripeOnboardingLink: (
    marketplaceId: string,
    vendorId: string,
    data: GenerateStripeOnboardingLinkRequest
  ) =>
    api.post<StripeOnboardingLink>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/onboarding/stripe-link`,
      data
    ),

  recordDocument: (
    marketplaceId: string,
    vendorId: string,
    documentType: string,
    s3Key: string
  ) =>
    api.post<VendorDocument>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/onboarding/documents`,
      null,
      { params: { documentType, s3Key } }
    ),

  listDocuments: (marketplaceId: string, vendorId: string) =>
    api.get<VendorDocument[]>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/onboarding/documents`
    ),

  submitForReview: (marketplaceId: string, vendorId: string) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/submit`),

  getMyRecord: (marketplaceId: string) =>
    api.get<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/me`),

  list: (marketplaceId: string, status?: string, page = 0, size = 20) =>
    api.get<MarketplaceVendor[]>(`/marketplaces/${marketplaceId}/vendors`, {
      params: { status, page, size },
    }),

  get: (marketplaceId: string, vendorId: string) =>
    api.get<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}`),

  approve: (marketplaceId: string, vendorId: string, data: VendorActionRequest) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/approve`, data),

  reject: (marketplaceId: string, vendorId: string, data: VendorActionRequest) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/reject`, data),

  suspend: (marketplaceId: string, vendorId: string, data: VendorActionRequest) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/suspend`, data),

  reinstate: (marketplaceId: string, vendorId: string) =>
    api.post<MarketplaceVendor>(`/marketplaces/${marketplaceId}/vendors/${vendorId}/reinstate`),

  requestMoreInfo: (marketplaceId: string, vendorId: string, data: VendorActionRequest) =>
    api.post<MarketplaceVendor>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/needs-info`,
      data
    ),
};
