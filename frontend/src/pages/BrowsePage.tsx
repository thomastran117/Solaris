import { useState } from "react";
import { useSearchParams, useNavigate, Link } from "react-router-dom";
import { useSelector } from "react-redux";
import { useQuery } from "@tanstack/react-query";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { SlidersHorizontal, X, ChevronLeft, ChevronRight, ShoppingBag } from "lucide-react";
import { catalogApi } from "../api/catalog";
import SearchBar from "../components/search/SearchBar";
import FacetPanel, { type FacetSelection } from "../components/search/FacetPanel";
import ProductCard from "../components/product/ProductCard";
import type { RootState } from "../stores";
import type { SearchFacets } from "../types/search";

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

const SORT_OPTIONS = [
  { label: "Relevance", sort: "createdAt", direction: "desc" },
  { label: "Price: Low to High", sort: "price", direction: "asc" },
  { label: "Price: High to Low", sort: "price", direction: "desc" },
  { label: "Newest", sort: "createdAt", direction: "desc" },
] as const;

const EMPTY_FACETS: SearchFacets = { categories: [], brands: [], priceRanges: [] };

export default function BrowsePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const marketplaceId = useSelector((s: RootState) => s.marketplace.currentMarketplace?.id);
  const { fadeInUp, stagger } = useAnims();

  const q = searchParams.get("q") ?? "";
  const categoryParam = searchParams.get("category");
  const brandParam = searchParams.get("brand");
  const minPriceParam = searchParams.get("minPrice");
  const maxPriceParam = searchParams.get("maxPrice");
  const sortParam = searchParams.get("sort") ?? "createdAt";
  const directionParam = (searchParams.get("direction") ?? "desc") as "asc" | "desc";
  const pageParam = Number(searchParams.get("page") ?? "0");

  const [mobileFacetsOpen, setMobileFacetsOpen] = useState(false);

  const facetSelection: FacetSelection = {
    category: categoryParam,
    brand: brandParam,
    minPrice: minPriceParam ? Number(minPriceParam) : null,
    maxPrice: maxPriceParam ? Number(maxPriceParam) : null,
  };

  function updateParams(updates: Record<string, string | null>) {
    const next = new URLSearchParams(searchParams);
    for (const [k, v] of Object.entries(updates)) {
      if (v == null) next.delete(k);
      else next.set(k, v);
    }
    next.set("page", "0");
    setSearchParams(next, { replace: true });
  }

  function handleSearch(newQ: string) {
    updateParams({ q: newQ || null, category: null, brand: null });
  }

  function handleFacetChange(sel: FacetSelection) {
    updateParams({
      category: sel.category,
      brand: sel.brand,
      minPrice: sel.minPrice != null ? String(sel.minPrice) : null,
      maxPrice: sel.maxPrice != null ? String(sel.maxPrice) : null,
    });
    setMobileFacetsOpen(false);
  }

  function handleSortChange(e: React.ChangeEvent<HTMLSelectElement>) {
    const opt = SORT_OPTIONS[Number(e.target.value)];
    updateParams({ sort: opt.sort, direction: opt.direction });
  }

  function handlePage(next: number) {
    const p = new URLSearchParams(searchParams);
    p.set("page", String(next));
    setSearchParams(p, { replace: true });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  const queryEnabled = !!marketplaceId;
  const searchResult = useQuery({
    queryKey: ["catalog", "browse", marketplaceId, q, categoryParam, brandParam, minPriceParam, maxPriceParam, sortParam, directionParam, pageParam],
    queryFn: () =>
      catalogApi.search(marketplaceId!, {
        q: q || undefined,
        category: categoryParam ?? undefined,
        brand: brandParam ?? undefined,
        minPrice: minPriceParam ? Number(minPriceParam) : undefined,
        maxPrice: maxPriceParam ? Number(maxPriceParam) : undefined,
        sort: sortParam,
        direction: directionParam,
        page: pageParam,
        size: 24,
      }).then((r) => r.data),
    enabled: queryEnabled,
    placeholderData: (prev) => prev,
  });

  const data = searchResult.data;
  const facets = data?.facets ?? EMPTY_FACETS;
  const products = data?.items ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  const selectedSortIndex = SORT_OPTIONS.findIndex(
    (o) => o.sort === sortParam && o.direction === directionParam
  );

  const hasActiveFilters = !!(categoryParam || brandParam || minPriceParam || maxPriceParam);

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
        <div className="absolute top-1/4 left-1/4 w-[600px] h-[600px] rounded-full bg-blue-600/10 blur-[120px]" />
        <div className="absolute bottom-1/3 right-1/4 w-[400px] h-[400px] rounded-full bg-sky-400/8 blur-[100px]" />
      </div>

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 py-8">
        {/* Header */}
        <motion.div variants={fadeInUp} initial="hidden" animate="visible" className="mb-6">
          <Link
            to="/"
            className="inline-flex items-center gap-1.5 text-sm text-white/60 hover:text-white/90 transition-colors mb-5"
          >
            <ChevronLeft className="w-4 h-4" />
            Home
          </Link>
          <h1 className="text-3xl md:text-4xl font-extrabold text-white mb-4">
            {q ? (
              <>
                Results for{" "}
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-sky-200 to-blue-400">
                  "{q}"
                </span>
              </>
            ) : (
              "Browse Products"
            )}
          </h1>
          <div className="max-w-xl">
            <SearchBar
              onSearch={handleSearch}
              initialValue={q}
              placeholder="Search products, brands, categories…"
            />
          </div>
        </motion.div>

        <div className="flex gap-6">
          {/* Facet sidebar — desktop */}
          <aside className="hidden lg:block w-56 flex-shrink-0">
            <div className="sticky top-24">
              <FacetPanel
                facets={facets}
                selected={facetSelection}
                onChange={handleFacetChange}
              />
            </div>
          </aside>

          {/* Main content */}
          <div className="flex-1 min-w-0">
            {/* Toolbar */}
            <div className="flex items-center justify-between gap-3 mb-5">
              <div className="flex items-center gap-2 flex-wrap">
                {searchResult.isFetching && !searchResult.isLoading && (
                  <div className="w-3.5 h-3.5 rounded-full border border-sky-400/40 border-t-sky-400 animate-spin" />
                )}
                <span className="text-sm text-white/50">
                  {searchResult.isLoading ? "Searching…" : `${totalElements.toLocaleString()} product${totalElements !== 1 ? "s" : ""}`}
                </span>
                {hasActiveFilters && (
                  <button
                    type="button"
                    onClick={() => handleFacetChange({ category: null, brand: null, minPrice: null, maxPrice: null })}
                    className="inline-flex items-center gap-1 text-xs text-sky-400 hover:text-sky-300 transition-colors"
                  >
                    <X className="w-3 h-3" />
                    Clear filters
                  </button>
                )}
              </div>
              <div className="flex items-center gap-2">
                {/* Mobile filter toggle */}
                <button
                  type="button"
                  onClick={() => setMobileFacetsOpen((v) => !v)}
                  className="lg:hidden flex items-center gap-1.5 text-sm text-white/70 border border-white/10 rounded-full px-3 py-1.5 hover:bg-white/[0.06] transition-colors"
                >
                  <SlidersHorizontal className="w-3.5 h-3.5" />
                  Filters
                  {hasActiveFilters && (
                    <span className="w-1.5 h-1.5 rounded-full bg-blue-400" />
                  )}
                </button>
                <select
                  value={selectedSortIndex >= 0 ? selectedSortIndex : 0}
                  onChange={handleSortChange}
                  className="text-sm bg-white/[0.06] border border-white/10 rounded-full px-3 py-1.5 text-white/70 focus:outline-none focus:border-white/20 appearance-none cursor-pointer"
                >
                  {SORT_OPTIONS.map((o, i) => (
                    <option key={i} value={i} className="bg-slate-900 text-white">
                      {o.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Mobile facet panel */}
            {mobileFacetsOpen && (
              <div className="lg:hidden mb-5">
                <FacetPanel
                  facets={facets}
                  selected={facetSelection}
                  onChange={handleFacetChange}
                />
              </div>
            )}

            {/* Active filter chips */}
            {hasActiveFilters && (
              <div className="flex flex-wrap gap-2 mb-4">
                {categoryParam && (
                  <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-blue-500/30 bg-blue-600/10 px-3 py-1 text-white/80">
                    {categoryParam}
                    <button type="button" onClick={() => updateParams({ category: null })}>
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                )}
                {brandParam && (
                  <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-blue-500/30 bg-blue-600/10 px-3 py-1 text-white/80">
                    {brandParam}
                    <button type="button" onClick={() => updateParams({ brand: null })}>
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                )}
                {(minPriceParam || maxPriceParam) && (
                  <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-blue-500/30 bg-blue-600/10 px-3 py-1 text-white/80">
                    {minPriceParam ? `$${minPriceParam}` : ""}
                    {minPriceParam && maxPriceParam ? " – " : ""}
                    {maxPriceParam ? `$${maxPriceParam}` : "+"}
                    <button type="button" onClick={() => updateParams({ minPrice: null, maxPrice: null })}>
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                )}
              </div>
            )}

            {/* Loading skeleton */}
            {searchResult.isLoading && (
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
            {!searchResult.isLoading && products.length === 0 && (
              <motion.div
                variants={fadeInUp}
                initial="hidden"
                animate="visible"
                className="flex flex-col items-center justify-center py-24 text-center"
              >
                <ShoppingBag className="w-12 h-12 text-white/20 mb-4" />
                <h3 className="text-lg font-semibold text-white mb-2">No products found</h3>
                <p className="text-sm text-white/50 max-w-xs">
                  Try adjusting your search term or removing filters.
                </p>
                {hasActiveFilters && (
                  <button
                    type="button"
                    onClick={() => handleFacetChange({ category: null, brand: null, minPrice: null, maxPrice: null })}
                    className="mt-4 text-sm text-sky-400 hover:text-sky-300 transition-colors"
                  >
                    Clear all filters
                  </button>
                )}
              </motion.div>
            )}

            {/* Results grid */}
            {!searchResult.isLoading && products.length > 0 && (
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
                  disabled={pageParam === 0}
                  onClick={() => handlePage(pageParam - 1)}
                  className="p-2 rounded-full border border-white/10 text-white/60 hover:text-white hover:bg-white/[0.06] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>

                {Array.from({ length: Math.min(totalPages, 7) }).map((_, i) => {
                  const start = Math.max(0, Math.min(pageParam - 3, totalPages - 7));
                  const p = start + i;
                  return (
                    <button
                      key={p}
                      type="button"
                      onClick={() => handlePage(p)}
                      className={`w-8 h-8 rounded-full text-sm transition-colors ${
                        p === pageParam
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
                  disabled={pageParam >= totalPages - 1}
                  onClick={() => handlePage(pageParam + 1)}
                  className="p-2 rounded-full border border-white/10 text-white/60 hover:text-white hover:bg-white/[0.06] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
