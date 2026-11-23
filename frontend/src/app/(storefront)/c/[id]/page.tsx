import type { Metadata } from "next";
import { Suspense } from "react";
import CompanyPage from "@views/CompanyPage";
import { companyKeys } from "../../../../api/keys";
import { Hydrate } from "../../../../api/server/hydrate";
import {
  getPublicCompany,
  getPublishedAnnouncements,
  loadOptional,
  loadPrimary,
  searchCompanyCatalog,
} from "../../../../api/server/storefront";
import { parseCatalogUrlParams } from "../../../../lib/catalogParams";
import {
  catalogCanonical,
  isCatalogIndexable,
  metaDescription,
} from "../../../../lib/seo";

// Rendered per request: the storefront catalog is driven by the query string.
// Backend reads still go through the Next.js data cache (60 s).

export async function generateMetadata({
  params,
  searchParams,
}: PageProps<"/c/[id]">): Promise<Metadata> {
  const { id } = await params;
  const p = parseCatalogUrlParams(await searchParams);
  const company = await loadPrimary(() => getPublicCompany(id));
  const canonical = catalogCanonical(`/c/${encodeURIComponent(id)}`, p);
  if (!company) return { title: "Store", alternates: { canonical } };
  const description = metaDescription(
    company.description,
    `Shop ${company.name} on ShopWave.`
  );
  return {
    title: company.name,
    description,
    alternates: { canonical },
    robots: isCatalogIndexable(p) ? undefined : { index: false, follow: true },
    openGraph: {
      type: "website",
      title: company.name,
      description,
      url: canonical,
      images: company.logoUrl
        ? [{ url: company.logoUrl, alt: company.name }]
        : undefined,
    },
  };
}

export default async function Page({
  params,
  searchParams,
}: PageProps<"/c/[id]">) {
  const { id } = await params;
  const p = parseCatalogUrlParams(await searchParams);
  const company = await loadPrimary(() => getPublicCompany(id));
  // Follow status is per-user and is fetched in the browser, never here.
  const [catalog, announcements] = company
    ? await Promise.all([
        loadOptional(() => searchCompanyCatalog(id, p)),
        loadOptional(() => getPublishedAnnouncements(id, 0)),
      ])
    : [null, null];
  return (
    <Hydrate
      entries={[
        [companyKeys.public(id), company],
        [companyKeys.catalog(id, p), catalog],
        [companyKeys.announcements(id, 0), announcements],
      ]}
    >
      {/* The view reads useSearchParams(), which needs a Suspense boundary. */}
      <Suspense>
        <CompanyPage />
      </Suspense>
    </Hydrate>
  );
}
