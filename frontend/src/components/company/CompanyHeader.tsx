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
    <div className="rounded-3xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6 md:p-8">
      <div className="flex flex-col md:flex-row gap-6">
        <div className="flex-shrink-0">
          {company.logoUrl ? (
            <img
              src={company.logoUrl}
              alt={`${company.name} logo`}
              className="w-20 h-20 md:w-24 md:h-24 rounded-2xl object-cover border border-white/10"
            />
          ) : (
            <div className="w-20 h-20 md:w-24 md:h-24 rounded-2xl bg-blue-500/15 border border-white/10 flex items-center justify-center">
              <span className="text-2xl md:text-3xl font-extrabold text-sky-200">
                {initialsFor(company.name)}
              </span>
            </div>
          )}
        </div>

        <div className="flex-1 min-w-0">
          <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-2">
            Storefront
          </p>
          <h1 className="text-3xl md:text-5xl font-extrabold tracking-tight text-white mb-3">
            {company.name}
          </h1>

          {(company.industry || location || company.foundedYear) && (
            <div className="flex flex-wrap gap-2 mb-4">
              {company.industry && (
                <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-white/10 bg-white/[0.04] px-3 py-1 text-white/70">
                  <Building2 className="w-3 h-3 text-sky-200" />
                  {company.industry}
                </span>
              )}
              {location && (
                <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-white/10 bg-white/[0.04] px-3 py-1 text-white/70">
                  <MapPin className="w-3 h-3 text-sky-200" />
                  {location}
                </span>
              )}
              {company.foundedYear && (
                <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-white/10 bg-white/[0.04] px-3 py-1 text-white/70">
                  <Calendar className="w-3 h-3 text-sky-200" />
                  Est. {company.foundedYear}
                </span>
              )}
            </div>
          )}

          {company.description && (
            <p className="text-sm md:text-base text-white/70 leading-relaxed max-w-3xl">
              {company.description}
            </p>
          )}

          {company.website && (
            <a
              href={company.website}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 mt-4 text-sm border border-white/20 hover:bg-white/10 rounded-full px-4 py-1.5 text-white/80 transition-colors"
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
