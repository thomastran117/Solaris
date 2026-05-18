import api from "../api";
import type { CompanyRole } from "./catalog";

export type { CompanyRole };

export type CompanyCapability =
  | "MANAGE_COMPANY"
  | "MANAGE_PRODUCTS"
  | "MANAGE_INVENTORY"
  | "MANAGE_PROMOTIONS"
  | "FULFILL_ORDERS"
  | "READ_PRODUCTS"
  | "READ_ANALYTICS";

export type CompanyMembershipStatus = "PENDING" | "ACTIVE" | "REVOKED";

export interface TeamMembership {
  id: number;
  userId: string | null;
  displayName: string | null;
  email: string | null;
  role: CompanyRole;
  status: CompanyMembershipStatus;
  invitedAt: string;
  acceptedAt: string | null;
}

export interface CompanyRoleInfo {
  role: CompanyRole;
  capabilities: CompanyCapability[];
}

export interface TeamInvitePreview {
  companyName: string;
  invitedEmail: string;
  role: CompanyRole;
  inviterDisplayName: string | null;
  expiresAt: string;
}

export interface InviteTeamMemberPayload {
  email: string;
  role: Exclude<CompanyRole, "OWNER">;
}

export interface UpdateTeamMemberRolePayload {
  role: Exclude<CompanyRole, "OWNER">;
}

export const teamApi = {
  invite: (companyId: number, payload: InviteTeamMemberPayload) =>
    api.post<TeamMembership>(`/companies/${companyId}/team/invites`, payload),

  list: (companyId: number) =>
    api.get<TeamMembership[]>(`/companies/${companyId}/team`),

  updateRole: (companyId: number, membershipId: number, payload: UpdateTeamMemberRolePayload) =>
    api.patch<TeamMembership>(`/companies/${companyId}/team/${membershipId}`, payload),

  revoke: (companyId: number, membershipId: number) =>
    api.delete(`/companies/${companyId}/team/${membershipId}`),

  describeMyAccess: (companyId: number) =>
    api.get<CompanyRoleInfo>(`/companies/${companyId}/me`),

  previewInvite: (token: string) =>
    api.get<TeamInvitePreview>(`/team-invites/${token}`),

  acceptInvite: (token: string) =>
    api.post<TeamMembership>(`/team-invites/${token}/accept`),
};
