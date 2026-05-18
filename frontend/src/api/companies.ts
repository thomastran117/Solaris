import api from "../api";
import type { PublicCompanyResponse } from "../types/company";

export const companiesApi = {
  /** Anonymous-safe single-company read for the storefront at /c/:id. */
  getPublic: (id: string) =>
    api.get<PublicCompanyResponse>(`/companies/${id}/public`),
};
