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
