import type { Metadata } from "next";
import HomePage from "@views/HomePage";
import { catalogKeys, homeKeys } from "../../api/keys";
import { Hydrate } from "../../api/server/hydrate";
import {
  getFeaturedCollections,
  loadOptional,
  searchMarketplaceCatalog,
} from "../../api/server/storefront";
import { getDefaultMarketplaceId } from "../../configuration/server";
import { DEFAULT_CATALOG_PARAMS } from "../../lib/catalogParams";

// Regenerated at most once a minute (ISR); public data only.
export const revalidate = 60;

export const metadata: Metadata = {
  alternates: { canonical: "/" },
};

export default async function Page() {
  const mp = getDefaultMarketplaceId();
  const [featured, catalog] = mp
    ? await Promise.all([
        loadOptional(() => getFeaturedCollections(mp)),
        loadOptional(() =>
          searchMarketplaceCatalog(mp, DEFAULT_CATALOG_PARAMS)
        ),
      ])
    : [null, null];
  return (
    <Hydrate
      entries={[
        [homeKeys.featuredCollections(mp), featured],
        [catalogKeys.browse(mp, DEFAULT_CATALOG_PARAMS), catalog],
      ]}
    >
      <HomePage />
    </Hydrate>
  );
}
