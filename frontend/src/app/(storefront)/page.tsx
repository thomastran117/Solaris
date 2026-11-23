import type { Metadata } from "next";
import HomePage from "@views/HomePage";
import { homeKeys } from "../../api/keys";
import { Hydrate } from "../../api/server/hydrate";
import {
  getFeaturedCollections,
  loadOptional,
} from "../../api/server/storefront";
import { getDefaultMarketplaceId } from "../../configuration/server";

// Regenerated at most once a minute (ISR); public data only.
export const revalidate = 60;

export const metadata: Metadata = {
  alternates: { canonical: "/" },
};

export default async function Page() {
  const mp = getDefaultMarketplaceId();
  const featured = mp
    ? await loadOptional(() => getFeaturedCollections(mp))
    : null;
  return (
    <Hydrate entries={[[homeKeys.featuredCollections(mp), featured]]}>
      <HomePage />
    </Hydrate>
  );
}
