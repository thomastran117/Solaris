import { Building2, Globe, MapPin, Calendar } from "lucide-react";
import type { PublicCompanyResponse } from "../../types/company";

interface Props {
  company: PublicCompanyResponse;
}

function initialsFor(name: string): string {
  const parts = name.trim().split(/\s+/).slice(0, 2);
  return parts.map((p) => p.charAt(0).toUpperCase()).join("") || "?";
}

export default function CompanyHeader({ company }: Props) {
  const location = [company.city, company.country].filter(Boolean).join(", ");

  return (
    <div className="rounded-xl border border-border bg-surface shadow-sm p-6 md:p-8">
      <div className="flex flex-col md:flex-row gap-6">
        <div className="flex-shrink-0">
          {company.logoUrl ? (
            <img
              src={company.logoUrl}
              alt={`${company.name} logo`}
              className="w-20 h-20 md:w-24 md:h-24 rounded-xl object-cover border border-border"
            />
          ) : (
            <div className="w-20 h-20 md:w-24 md:h-24 rounded-xl bg-primary/10 border border-border flex items-center justify-center">
              <span className="text-2xl md:text-3xl font-semibold text-link">
                {initialsFor(company.name)}
              </span>
            </div>
          )}
        </div>

        <div className="flex-1 min-w-0">
          <p className="text-xs font-semibold text-link mb-2">Storefront</p>
          <h1 className="text-3xl md:text-5xl font-semibold tracking-tight text-foreground mb-3">
            {company.name}
          </h1>

          {(company.industry || location || company.foundedYear) && (
            <div className="flex flex-wrap gap-2 mb-4">
              {company.industry && (
                <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-border bg-surface px-3 py-1 text-muted-foreground">
                  <Building2 className="w-3 h-3 text-link" />
                  {company.industry}
                </span>
              )}
              {location && (
                <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-border bg-surface px-3 py-1 text-muted-foreground">
                  <MapPin className="w-3 h-3 text-link" />
                  {location}
                </span>
              )}
              {company.foundedYear && (
                <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-border bg-surface px-3 py-1 text-muted-foreground">
                  <Calendar className="w-3 h-3 text-link" />
                  Est. {company.foundedYear}
                </span>
              )}
            </div>
          )}

          {company.description && (
            <p className="text-sm md:text-base text-muted-foreground leading-relaxed max-w-3xl">
              {company.description}
            </p>
          )}

          {company.website && (
            <a
              href={company.website}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 mt-4 text-sm border border-input hover:bg-muted rounded-full px-4 py-1.5 text-foreground transition-colors"
            >
              <Globe className="w-3.5 h-3.5" />
              Visit website
            </a>
          )}
        </div>
      </div>
    </div>
  );
}
