"use client";

import { useParams } from "next/navigation";
import { useSelector } from "react-redux";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Layers } from "lucide-react";
import { marketplaceCollectionsApi } from "../api/merchandising";
import { catalogApi } from "../api/catalog";
import ProductCard from "../components/product/ProductCard";
import type { CatalogProduct } from "../api/catalog";
import { selectActiveMarketplaceId } from "../stores/marketplaceSlice";
import { COLLECTION_MEMBERS_PAGE_SIZE, collectionKeys } from "../api/keys";
import { useAnims } from "../hooks/useAnims";

/**
 * Public collection detail page. Members come from the marketplace endpoint already ordered
 * by pinnedRank — we then look each one up via the marketplace catalog so the page reuses the
 * existing {@code ProductCard} (with sale badges, bookmarking, etc.) instead of rendering a
 * stripped-down row.
 */
export default function CollectionPage() {
  const { slug } = useParams<{ slug: string }>();
  const marketplaceId = useSelector(selectActiveMarketplaceId);
  const { fadeInUp, stagger } = useAnims();

  const enabled = !!marketplaceId && !!slug;

  const { data: collection } = useQuery({
    queryKey: collectionKeys.detail(marketplaceId, slug),
    queryFn: () =>
      marketplaceCollectionsApi
        .getBySlug(marketplaceId!, slug!)
        .then((r) => r.data),
    enabled,
  });

  const { data: members, isLoading: membersLoading } = useQuery({
    queryKey: collectionKeys.members(marketplaceId, slug),
    queryFn: () =>
      marketplaceCollectionsApi
        .listProductsBySlug(
          marketplaceId!,
          slug!,
          0,
          COLLECTION_MEMBERS_PAGE_SIZE
        )
        .then((r) => r.data),
    enabled,
  });

  // For full catalog cards (images, variants, promo), batch-fetch each product via the catalog
  // endpoint. The list is small (max 50 per page) so the parallel calls stay cheap.
  const productIds = members?.items.map((m) => m.productId) ?? [];
  const { data: catalogProducts } = useQuery<CatalogProduct[]>({
    queryKey: collectionKeys.membersCatalog(marketplaceId, slug, productIds),
    queryFn: async () => {
      if (!marketplaceId || productIds.length === 0) return [];
      const results = await Promise.all(
        productIds.map((id) =>
          catalogApi
            .getProduct(marketplaceId, id)
            .then((r) => r.data)
            .catch(() => null)
        )
      );
      return results.filter((p): p is CatalogProduct => p !== null);
    },
    enabled: enabled && productIds.length > 0,
  });

  if (!marketplaceId) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center px-6">
        <p className="text-muted-foreground text-sm">
          Pick a marketplace to browse collections.
        </p>
      </div>
    );
  }

  return (
    <div className="bg-background relative overflow-hidden">
      <div className="relative z-10 max-w-6xl mx-auto px-4 sm:px-6 py-12">
        <motion.header
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="mb-10"
        >
          <p className="text-xs font-semibold text-link mb-3">Collection</p>
          <h1 className="text-4xl md:text-5xl font-semibold tracking-tight text-foreground">
            {collection?.name ?? "…"}
          </h1>
          {collection?.description && (
            <p className="text-base text-muted-foreground leading-relaxed mt-3 max-w-2xl">
              {collection.description}
            </p>
          )}
        </motion.header>

        {membersLoading ? (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            {Array.from({ length: 8 }).map((_, i) => (
              <div
                key={i}
                className="aspect-[4/5] rounded-xl bg-surface animate-pulse"
              />
            ))}
          </div>
        ) : !catalogProducts || catalogProducts.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <Layers className="w-12 h-12 text-muted-foreground mb-4" />
            <p className="text-muted-foreground text-sm">
              No products in this collection yet.
            </p>
          </div>
        ) : (
          <motion.div
            variants={stagger}
            initial="hidden"
            animate="visible"
            className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4"
          >
            {catalogProducts.map((p) => (
              <ProductCard key={p.id} product={p} variants={fadeInUp} />
            ))}
          </motion.div>
        )}
      </div>
    </div>
  );
}
