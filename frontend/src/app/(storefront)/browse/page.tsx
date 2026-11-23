import type { Metadata } from "next";
import { Suspense } from "react";
import BrowsePage from "@views/BrowsePage";
import { catalogKeys } from "../../../api/keys";
import { Hydrate } from "../../../api/server/hydrate";
import {
  loadOptional,
  searchMarketplaceCatalog,
} from "../../../api/server/storefront";
import { getDefaultMarketplaceId } from "../../../configuration/server";
import { parseCatalogUrlParams } from "../../../lib/catalogParams";
import { catalogCanonical, isCatalogIndexable } from "../../../lib/seo";

// Rendered per request: the result set depends on the query string. Backend reads
// still go through the Next.js data cache (60 s).

export async function generateMetadata({
  searchParams,
}: PageProps<"/browse">): Promise<Metadata> {
  const p = parseCatalogUrlParams(await searchParams);
  const title = p.q
    ? `Search results for "${p.q}"`
    : p.category
      ? `Shop ${p.category}`
      : "Browse products";
  return {
    title,
    description:
      "Browse products from merchants across the ShopWave marketplace.",
    alternates: { canonical: catalogCanonical("/browse", p) },
    robots: isCatalogIndexable(p) ? undefined : { index: false, follow: true },
  };
}

export default async function Page({ searchParams }: PageProps<"/browse">) {
  const p = parseCatalogUrlParams(await searchParams);
  const mp = getDefaultMarketplaceId();
  const results = mp
    ? await loadOptional(() => searchMarketplaceCatalog(mp, p))
    : null;
  return (
    <Hydrate entries={[[catalogKeys.browse(mp, p), results]]}>
      {/* The view reads useSearchParams(), which needs a Suspense boundary. */}
      <Suspense>
        <BrowsePage />
      </Suspense>
    </Hydrate>
  );
}
