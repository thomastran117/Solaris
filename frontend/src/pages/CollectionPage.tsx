import { useParams } from "react-router-dom";
import { useSelector } from "react-redux";
import { useQuery } from "@tanstack/react-query";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { Layers } from "lucide-react";
import { marketplaceCollectionsApi } from "../api/merchandising";
import { catalogApi } from "../api/catalog";
import ProductCard from "../components/product/ProductCard";
import type { CatalogProduct } from "../api/catalog";
import type { RootState } from "../stores";

const useAnims = () => {
  const prefersReducedMotion = useReducedMotion();
  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.35 } } }
    : { hidden: { opacity: 0, y: 18 }, visible: { opacity: 1, y: 0, transition: { duration: 0.55 } } };
  const stagger: Variants = prefersReducedMotion
    ? { hidden: {}, visible: { transition: { staggerChildren: 0.04 } } }
    : { hidden: {}, visible: { transition: { staggerChildren: 0.08 } } };
  return { fadeInUp, stagger };
};

/**
 * Public collection detail page. Members come from the marketplace endpoint already ordered
 * by pinnedRank — we then look each one up via the marketplace catalog so the page reuses the
 * existing {@code ProductCard} (with sale badges, bookmarking, etc.) instead of rendering a
 * stripped-down row.
 */
export default function CollectionPage() {
  const { slug } = useParams<{ slug: string }>();
  const marketplaceId = useSelector((s: RootState) => s.marketplace.currentMarketplace?.id);
  const { fadeInUp, stagger } = useAnims();

  const enabled = !!marketplaceId && !!slug;

  const { data: collection } = useQuery({
    queryKey: ["collection", "detail", marketplaceId, slug],
    queryFn: () => marketplaceCollectionsApi.getBySlug(marketplaceId!, slug!).then(r => r.data),
    enabled,
  });

  const { data: members, isLoading: membersLoading } = useQuery({
    queryKey: ["collection", "members", marketplaceId, slug],
    queryFn: () =>
      marketplaceCollectionsApi.listProductsBySlug(marketplaceId!, slug!, 0, 50).then(r => r.data),
    enabled,
  });

  // For full catalog cards (images, variants, promo), batch-fetch each product via the catalog
  // endpoint. The list is small (max 50 per page) so the parallel calls stay cheap.
  const productIds = members?.items.map(m => m.productId) ?? [];
  const { data: catalogProducts } = useQuery<CatalogProduct[]>({
    queryKey: ["collection", "members-catalog", marketplaceId, slug, productIds],
    queryFn: async () => {
      if (!marketplaceId || productIds.length === 0) return [];
      const results = await Promise.all(
        productIds.map(id =>
          catalogApi.getProduct(marketplaceId, id).then(r => r.data).catch(() => null)
        )
      );
      return results.filter((p): p is CatalogProduct => p !== null);
    },
    enabled: enabled && productIds.length > 0,
  });

  if (!marketplaceId) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6">
        <p className="text-white/60 text-sm">Pick a marketplace to browse collections.</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 relative overflow-hidden">
      <div aria-hidden className="pointer-events-none fixed inset-0" style={{ zIndex: 0 }}>
        <div className="absolute top-1/4 left-1/4 w-[600px] h-[600px] rounded-full bg-blue-600/10 blur-[120px]" />
        <div className="absolute bottom-1/3 right-1/4 w-[400px] h-[400px] rounded-full bg-sky-400/8 blur-[100px]" />
      </div>

      <div className="relative z-10 max-w-6xl mx-auto px-4 sm:px-6 py-12">
        <motion.header variants={fadeInUp} initial="hidden" animate="visible" className="mb-10">
          <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-3">
            Collection
          </p>
          <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight text-white">
            {collection?.name ?? "…"}
          </h1>
          {collection?.description && (
            <p className="text-base text-white/70 leading-relaxed mt-3 max-w-2xl">
              {collection.description}
            </p>
          )}
        </motion.header>

        {membersLoading ? (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="aspect-[4/5] rounded-2xl bg-white/[0.06] animate-pulse" />
            ))}
          </div>
        ) : !catalogProducts || catalogProducts.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <Layers className="w-12 h-12 text-white/20 mb-4" />
            <p className="text-white/55 text-sm">No products in this collection yet.</p>
          </div>
        ) : (
          <motion.div
            variants={stagger}
            initial="hidden"
            animate="visible"
            className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4"
          >
            {catalogProducts.map(p => (
              <ProductCard key={p.id} product={p} variants={fadeInUp} />
            ))}
          </motion.div>
        )}
      </div>
    </div>
  );
}
