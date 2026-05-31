import { useState } from "react";
import { Link } from "react-router-dom";
import { useSelector } from "react-redux";
import { useQuery } from "@tanstack/react-query";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { ChevronLeft, ChevronRight, Sparkles } from "lucide-react";
import { catalogApi } from "../api/catalog";
import ProductCard from "../components/product/ProductCard";
import type { RootState } from "../stores";

const FEED_PAGE_SIZE = 20;

const useAnims = () => {
  const prefersReducedMotion = useReducedMotion();
  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.2 } } }
    : { hidden: { opacity: 0, y: 16 }, visible: { opacity: 1, y: 0, transition: { duration: 0.4 } } };
  const stagger: Variants = {
    hidden: {},
    visible: { transition: { staggerChildren: prefersReducedMotion ? 0 : 0.06 } },
  };
  return { fadeInUp, stagger };
};

export default function FeedPage() {
  const [page, setPage] = useState(0);
  const marketplaceId = useSelector((s: RootState) => s.marketplace.currentMarketplace?.id);
  const { fadeInUp, stagger } = useAnims();

  const feedQuery = useQuery({
    queryKey: ["catalog", "feed", marketplaceId, page],
    queryFn: () =>
      catalogApi.getFeed(marketplaceId!, { page, size: FEED_PAGE_SIZE }).then((r) => r.data),
    enabled: !!marketplaceId,
    placeholderData: (prev) => prev,
  });

  const data = feedQuery.data;
  const products = data?.items ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  function handlePage(next: number) {
    setPage(next);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  if (!marketplaceId) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <p className="text-white/60 text-sm">No marketplace selected.</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 relative overflow-hidden">
      {/* Ambient glow */}
      <div aria-hidden className="pointer-events-none fixed inset-0" style={{ zIndex: 0 }}>
        <div className="absolute top-1/4 left-1/3 w-[600px] h-[600px] rounded-full bg-blue-600/10 blur-[120px]" />
        <div className="absolute bottom-1/3 right-1/4 w-[400px] h-[400px] rounded-full bg-sky-400/8 blur-[100px]" />
        <div className="absolute top-2/3 left-1/2 w-[300px] h-[300px] rounded-full bg-indigo-500/8 blur-[90px]" />
      </div>

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 py-8">
        {/* Header */}
        <motion.div variants={fadeInUp} initial="hidden" animate="visible" className="mb-8">
          <Link
            to="/"
            className="inline-flex items-center gap-1.5 text-sm text-white/60 hover:text-white/90 transition-colors mb-5"
          >
            <ChevronLeft className="w-4 h-4" />
            Home
          </Link>
          <div className="flex items-center gap-3 mb-1">
            <span className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">
              For You
            </span>
          </div>
          <h1 className="text-3xl md:text-4xl font-extrabold text-white">Your Feed</h1>
          {!feedQuery.isLoading && totalElements > 0 && (
            <p className="mt-2 text-sm text-white/50">
              {totalElements.toLocaleString()} product{totalElements !== 1 ? "s" : ""} picked for you
            </p>
          )}
        </motion.div>

        {/* Loading skeleton */}
        {feedQuery.isLoading && (
          <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-4">
            {Array.from({ length: 12 }).map((_, i) => (
              <div
                key={i}
                className="rounded-2xl border border-white/10 bg-white/[0.04] overflow-hidden animate-pulse"
              >
                <div className="aspect-square bg-white/[0.04]" />
                <div className="p-4 space-y-2">
                  <div className="h-2.5 bg-white/[0.06] rounded w-2/3" />
                  <div className="h-3.5 bg-white/[0.06] rounded" />
                  <div className="h-3.5 bg-white/[0.06] rounded w-4/5" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Empty state */}
        {!feedQuery.isLoading && products.length === 0 && (
          <motion.div
            variants={fadeInUp}
            initial="hidden"
            animate="visible"
            className="flex flex-col items-center justify-center py-24 text-center"
          >
            <Sparkles className="w-12 h-12 text-white/20 mb-4" />
            <h3 className="text-lg font-semibold text-white mb-2">Your feed is empty</h3>
            <p className="text-sm text-white/50 max-w-xs">
              Browse and purchase products to personalise your feed.
            </p>
            <Link
              to="/browse"
              className="mt-5 inline-flex items-center gap-1.5 rounded-full bg-blue-600 hover:bg-blue-500 px-5 py-2 text-sm font-semibold text-white transition-colors"
            >
              Browse products
            </Link>
          </motion.div>
        )}

        {/* Results grid */}
        {!feedQuery.isLoading && products.length > 0 && (
          <motion.div
            variants={stagger}
            initial="hidden"
            animate="visible"
            className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-4"
          >
            {products.map((product) => (
              <ProductCard key={product.id} product={product} variants={fadeInUp} />
            ))}
          </motion.div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 mt-10">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => handlePage(page - 1)}
              className="p-2 rounded-full border border-white/10 text-white/60 hover:text-white hover:bg-white/[0.06] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>

            {Array.from({ length: Math.min(totalPages, 7) }).map((_, i) => {
              const start = Math.max(0, Math.min(page - 3, totalPages - 7));
              const p = start + i;
              return (
                <button
                  key={p}
                  type="button"
                  onClick={() => handlePage(p)}
                  className={`w-8 h-8 rounded-full text-sm transition-colors ${
                    p === page
                      ? "bg-blue-600 text-white"
                      : "text-white/60 hover:text-white hover:bg-white/[0.06]"
                  }`}
                >
                  {p + 1}
                </button>
              );
            })}

            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => handlePage(page + 1)}
              className="p-2 rounded-full border border-white/10 text-white/60 hover:text-white hover:bg-white/[0.06] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
