import api from "../api";
import type { PremiumStatus } from "../types/user";

export const getPremiumStatus = () =>
  api.get<PremiumStatus>("/premium/status");

export const createCheckoutSession = () =>
  api.post<{ url: string }>("/premium/checkout");

export const createPortalSession = () =>
  api.post<{ url: string }>("/premium/portal");
