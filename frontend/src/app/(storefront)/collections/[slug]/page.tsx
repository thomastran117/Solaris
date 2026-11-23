import type { Metadata } from "next";
import CollectionPage from "@views/CollectionPage";
import type { CatalogProduct } from "../../../../api/catalog";
import { collectionKeys } from "../../../../api/keys";
import { Hydrate, type Prefetched } from "../../../../api/server/hydrate";
import {
  getCatalogProduct,
  getCollectionBySlug,
  getCollectionMembers,
  loadOptional,
  loadPrimary,
} from "../../../../api/server/storefront";
import { getDefaultMarketplaceId } from "../../../../configuration/server";
import { metaDescription } from "../../../../lib/seo";

// Incremental static regeneration: rendered on first request, then cached for 60 s.
export const revalidate = 60;
export async function generateStaticParams() {
  return [];
}

export async function generateMetadata({
  params,
}: PageProps<"/collections/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const mp = getDefaultMarketplaceId();
  const collection = mp
    ? await loadPrimary(() => getCollectionBySlug(mp, slug))
    : null;
  const canonical = `/collections/${encodeURIComponent(slug)}`;
  if (!collection) return { title: "Collection", alternates: { canonical } };
  const description = metaDescription(
    collection.description,
    `Shop the ${collection.name} collection on ShopWave.`
  );
  return {
    title: collection.name,
    description,
    alternates: { canonical },
    openGraph: {
      type: "website",
      title: collection.name,
      description,
      url: canonical,
      images: collection.imageUrl
        ? [{ url: collection.imageUrl, alt: collection.name }]
        : undefined,
    },
  };
}

export default async function Page({
  params,
}: PageProps<"/collections/[slug]">) {
  const { slug } = await params;
  const mp = getDefaultMarketplaceId();
  const entries: Array<Prefetched[number]> = [];

  if (mp) {
    const collection = await loadPrimary(() => getCollectionBySlug(mp, slug));
    entries.push([collectionKeys.detail(mp, slug), collection]);
    const members = collection
      ? await loadOptional(() => getCollectionMembers(mp, slug))
      : null;
    entries.push([collectionKeys.members(mp, slug), members]);

    // Mirrors the view: full catalog cards for each member, skipping any that fail.
    const productIds = members?.items.map((m) => m.productId) ?? [];
    if (productIds.length > 0) {
      const products = await Promise.all(
        productIds.map((id) => getCatalogProduct(mp, id).catch(() => null))
      );
      entries.push([
        collectionKeys.membersCatalog(mp, slug, productIds),
        products.filter((p): p is CatalogProduct => p !== null),
      ]);
    }
  }

  return (
    <Hydrate entries={entries}>
      <CollectionPage />
    </Hydrate>
  );
}
