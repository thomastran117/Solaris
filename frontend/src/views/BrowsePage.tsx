"use client";

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useSetSearchParams } from "../components/navigation/useSetSearchParams";
import { useSelector } from "react-redux";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  SlidersHorizontal,
  X,
  ChevronLeft,
  ChevronRight,
  ShoppingBag,
} from "lucide-react";
import { catalogApi } from "../api/catalog";
import SearchBar from "../components/search/SearchBar";
import FacetPanel, {
  type FacetSelection,
} from "../components/search/FacetPanel";
import ProductCard from "../components/product/ProductCard";
import { selectActiveMarketplaceId } from "../stores/marketplaceSlice";
import type { SearchFacets } from "../types/search";
import { useAnims } from "../hooks/useAnims";
import { formatCount } from "../lib/format";
import {
  catalogSearchRequest,
  parseCatalogUrlParams,
} from "../lib/catalogParams";
import { catalogKeys } from "../api/keys";
import {
  CATALOG_SORT_OPTIONS,
  selectedCatalogSortIndex,
} from "../components/search/catalogSort";

const EMPTY_FACETS: SearchFacets = {
  categories: [],
  brands: [],
  priceRanges: [],
};

export default function BrowsePage() {
  const searchParams = useSearchParams();
  const setSearchParams = useSetSearchParams();
  const marketplaceId = useSelector(selectActiveMarketplaceId);
  const { fadeInUp, stagger } = useAnims();

  // Shared with the server page so prefetched data lands on the same query key.
  const urlParams = parseCatalogUrlParams(searchParams);
  const {
    q,
    category: categoryParam,
    brand: brandParam,
    minPrice: minPriceParam,
    maxPrice: maxPriceParam,
    sort: sortParam,
    direction: directionParam,
    page: pageParam,
  } = urlParams;

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
    const opt = CATALOG_SORT_OPTIONS[Number(e.target.value)];
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
    queryKey: catalogKeys.browse(marketplaceId, urlParams),
    queryFn: () =>
      catalogApi
        .search(marketplaceId!, catalogSearchRequest(urlParams))
        .then((r) => r.data),
    enabled: queryEnabled,
    placeholderData: (prev) => prev,
  });

  const data = searchResult.data;
  const facets = data?.facets ?? EMPTY_FACETS;
  const products = data?.items ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  const selectedSortIndex = selectedCatalogSortIndex(sortParam, directionParam);

  const hasActiveFilters = !!(
    categoryParam ||
    brandParam ||
    minPriceParam ||
    maxPriceParam
  );

  if (!marketplaceId) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <p className="text-muted-foreground text-sm">
          No marketplace selected.
        </p>
      </div>
    );
  }

  return (
    <div className="bg-background relative overflow-hidden">
      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 py-8">
        {/* Header */}
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="mb-6"
        >
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors mb-5"
          >
            <ChevronLeft className="w-4 h-4" />
            Home
          </Link>
          <h1 className="text-3xl md:text-4xl font-semibold text-foreground mb-4">
            {q ? (
              <>
                Results for <span className="text-foreground">"{q}"</span>
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
                  <div className="w-3.5 h-3.5 rounded-full border border-primary/40 border-t-primary animate-spin" />
                )}
                <span className="text-sm text-muted-foreground">
                  {searchResult.isLoading
                    ? "Searching…"
                    : `${formatCount(totalElements)} product${totalElements !== 1 ? "s" : ""}`}
                </span>
                {hasActiveFilters && (
                  <button
                    type="button"
                    onClick={() =>
                      handleFacetChange({
                        category: null,
                        brand: null,
                        minPrice: null,
                        maxPrice: null,
                      })
                    }
                    className="inline-flex items-center gap-1 text-xs text-link hover:text-link transition-colors"
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
                  className="lg:hidden flex items-center gap-1.5 text-sm text-muted-foreground border border-border rounded-full px-3 py-1.5 hover:bg-muted transition-colors"
                >
                  <SlidersHorizontal className="w-3.5 h-3.5" />
                  Filters
                  {hasActiveFilters && (
                    <span className="w-1.5 h-1.5 rounded-full bg-primary" />
                  )}
                </button>
                <select
                  value={selectedSortIndex}
                  onChange={handleSortChange}
                  className="text-sm bg-surface border border-border rounded-full px-3 py-1.5 text-muted-foreground focus:outline-none focus:border-ring appearance-none cursor-pointer"
                >
                  {CATALOG_SORT_OPTIONS.map((o, i) => (
                    <option
                      key={i}
                      value={i}
                      className="bg-surface text-foreground"
                    >
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
                  <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-primary/40 bg-primary/10 px-3 py-1 text-foreground">
                    {categoryParam}
                    <button
                      type="button"
                      onClick={() => updateParams({ category: null })}
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                )}
                {brandParam && (
                  <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-primary/40 bg-primary/10 px-3 py-1 text-foreground">
                    {brandParam}
                    <button
                      type="button"
                      onClick={() => updateParams({ brand: null })}
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                )}
                {(minPriceParam || maxPriceParam) && (
                  <span className="inline-flex items-center gap-1.5 text-xs rounded-full border border-primary/40 bg-primary/10 px-3 py-1 text-foreground">
                    {minPriceParam ? `$${minPriceParam}` : ""}
                    {minPriceParam && maxPriceParam ? " – " : ""}
                    {maxPriceParam ? `$${maxPriceParam}` : "+"}
                    <button
                      type="button"
                      onClick={() =>
                        updateParams({ minPrice: null, maxPrice: null })
                      }
                    >
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
                    className="rounded-xl border border-border bg-surface overflow-hidden animate-pulse"
                  >
                    <div className="aspect-square bg-surface" />
                    <div className="p-4 space-y-2">
                      <div className="h-2.5 bg-surface rounded w-2/3" />
                      <div className="h-3.5 bg-surface rounded" />
                      <div className="h-3.5 bg-surface rounded w-4/5" />
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
                <ShoppingBag className="w-12 h-12 text-muted-foreground mb-4" />
                <h3 className="text-lg font-semibold text-foreground mb-2">
                  No products found
                </h3>
                <p className="text-sm text-muted-foreground max-w-xs">
                  Try adjusting your search term or removing filters.
                </p>
                {hasActiveFilters && (
                  <button
                    type="button"
                    onClick={() =>
                      handleFacetChange({
                        category: null,
                        brand: null,
                        minPrice: null,
                        maxPrice: null,
                      })
                    }
                    className="mt-4 text-sm text-link hover:text-link transition-colors"
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
                  <ProductCard
                    key={product.id}
                    product={product}
                    variants={fadeInUp}
                  />
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
                  className="p-2 rounded-full border border-border text-muted-foreground hover:text-foreground hover:bg-muted disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>

                {Array.from({ length: Math.min(totalPages, 7) }).map((_, i) => {
                  const start = Math.max(
                    0,
                    Math.min(pageParam - 3, totalPages - 7)
                  );
                  const p = start + i;
                  return (
                    <button
                      key={p}
                      type="button"
                      onClick={() => handlePage(p)}
                      className={`w-8 h-8 rounded-full text-sm transition-colors ${
                        p === pageParam
                          ? "bg-primary text-primary-foreground"
                          : "text-muted-foreground hover:text-foreground hover:bg-muted"
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
                  className="p-2 rounded-full border border-border text-muted-foreground hover:text-foreground hover:bg-muted disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
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
