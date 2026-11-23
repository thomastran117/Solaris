"use client";

import { useState, useEffect, useRef, useMemo } from "react";
import { useParams, useSearchParams } from "next/navigation";
import { useSetSearchParams } from "../components/navigation/useSetSearchParams";
import { useSelector } from "react-redux";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Search, Plus, GitCompareArrows, Package, Boxes } from "lucide-react";
import { selectActiveMarketplaceId } from "../stores/marketplaceSlice";
import { compareApi } from "../api/compare";
import { catalogApi } from "../api/catalog";
import { useAnims } from "../hooks/useAnims";
import { useDebounce } from "../hooks/useDebounce";
import type { CompareType } from "../types/comparison";
import {
  ProductComparisonTable,
  BundleComparisonTable,
} from "../components/product/ComparisonTable";
import { formatMoney } from "../lib/format";

const MAX_ITEMS = 4;

// Lightweight shape shared by the two picker sources (catalog products + bundles).
interface PickerResult {
  id: string;
  name: string;
  thumbnailUrl: string | null;
  subtitle: string;
}

// ======================================================================
// SearchPicker
// ======================================================================

interface SearchPickerProps {
  type: CompareType;
  companyId: string;
  marketplaceId: string | undefined;
  selectedIds: string[];
  onAdd: (id: string) => void;
}

function SearchPicker({
  type,
  companyId,
  marketplaceId,
  selectedIds,
  onAdd,
}: SearchPickerProps) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const debouncedQuery = useDebounce(query, 300);
  const containerRef = useRef<HTMLDivElement>(null);

  const isDisabled = selectedIds.length >= MAX_ITEMS;

  // Product search — scoped to the SAME marketplace the compare call targets, so every result is
  // a marketplace-listed product the endpoint can actually compare (no scope-mismatch 404s).
  const { data: productResults } = useQuery({
    queryKey: ["compare-search-products", marketplaceId, debouncedQuery],
    queryFn: () =>
      catalogApi
        .search(marketplaceId!, { q: debouncedQuery, size: 6 })
        .then((r) => r.data.items),
    enabled:
      type === "product" &&
      !!marketplaceId &&
      debouncedQuery.length >= 1 &&
      !isDisabled,
    staleTime: 10_000,
  });

  // Bundle list (no search endpoint — client-side filter).
  const { data: allBundles } = useQuery({
    queryKey: ["compare-bundles-list", companyId],
    queryFn: () => compareApi.listBundles(companyId).then((r) => r.data.items),
    enabled: type === "bundle" && !isDisabled,
    staleTime: 30_000,
  });

  const bundleResults = useMemo(() => {
    if (!allBundles) return [];
    const q = debouncedQuery.toLowerCase();
    return allBundles
      .filter((b) => b.name.toLowerCase().includes(q))
      .slice(0, 6);
  }, [allBundles, debouncedQuery]);

  const results: PickerResult[] =
    type === "product"
      ? (productResults ?? []).map((p) => ({
          id: p.id,
          name: p.name,
          thumbnailUrl: p.thumbnailUrl,
          subtitle:
            [p.category, p.brand].filter(Boolean).join(" · ") ||
            (p.price ? formatMoney(p.price, p.currency ?? "USD") : ""),
        }))
      : bundleResults.map((b) => ({
          id: b.id,
          name: b.name,
          thumbnailUrl: b.thumbnailUrl,
          subtitle: `${b.items.length} items · ${formatMoney(b.price, b.currency)}`,
        }));

  // Close on outside click.
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  return (
    <div ref={containerRef} className="relative w-full max-w-md">
      <div
        className={`flex items-center gap-2 rounded-xl border px-3 py-2.5 ${
          isDisabled
            ? "cursor-not-allowed border-border bg-surface opacity-50"
            : "border-input bg-surface transition-colors hover:border-input focus-within:border-ring"
        }`}
      >
        <Search size={15} className="shrink-0 text-muted-foreground" />
        <input
          type="text"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          disabled={isDisabled}
          placeholder={
            isDisabled
              ? "Maximum 4 items reached"
              : `Search ${type === "product" ? "products" : "bundles"}…`
          }
          className="flex-1 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed"
        />
        {isDisabled && (
          <span className="shrink-0 text-xs text-muted-foreground">
            {selectedIds.length}/{MAX_ITEMS}
          </span>
        )}
      </div>

      {open && results.length > 0 && !isDisabled && (
        <div className="absolute top-full z-50 mt-2 w-full overflow-hidden rounded-xl border border-border bg-surface shadow-2xl">
          {results.map((r) => {
            const alreadyAdded = selectedIds.includes(r.id);
            return (
              <button
                key={r.id}
                onClick={() => {
                  if (!alreadyAdded) {
                    onAdd(r.id);
                    setQuery("");
                    setOpen(false);
                  }
                }}
                disabled={alreadyAdded}
                className={`flex w-full items-center gap-3 px-3 py-2.5 text-left transition-colors ${
                  alreadyAdded
                    ? "cursor-not-allowed opacity-40"
                    : "cursor-pointer hover:bg-muted"
                }`}
              >
                {r.thumbnailUrl ? (
                  <img
                    src={r.thumbnailUrl}
                    alt={r.name}
                    className="h-9 w-9 shrink-0 rounded-lg border border-border object-cover"
                  />
                ) : (
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-border bg-surface text-muted-foreground">
                    <Package size={14} />
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium text-foreground">
                    {r.name}
                  </div>
                  {r.subtitle && (
                    <div className="truncate text-xs text-muted-foreground">
                      {r.subtitle}
                    </div>
                  )}
                </div>
                {alreadyAdded ? (
                  <span className="shrink-0 text-xs text-muted-foreground">
                    Added
                  </span>
                ) : (
                  <Plus size={14} className="shrink-0 text-muted-foreground" />
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ======================================================================
// ComparisonPage
// ======================================================================

export default function ComparisonPage() {
  const { fadeInUp, fadeIn, stagger } = useAnims();
  const { id } = useParams<{ id: string }>();
  const companyId = id ?? null;
  const marketplaceId = useSelector(selectActiveMarketplaceId);

  const searchParams = useSearchParams();
  const setSearchParams = useSetSearchParams();

  const type: CompareType =
    searchParams.get("type") === "bundle" ? "bundle" : "product";

  const idsParam = searchParams.get("ids") ?? "";
  const selectedIds = useMemo(
    () =>
      idsParam
        .split(",")
        .map((s) => s.trim())
        .filter((s) => s.length > 0),
    [idsParam]
  );

  const sortedIds = useMemo(() => [...selectedIds].sort(), [selectedIds]);

  // Switching product/bundle mode resets the selection: product and bundle IDs are different
  // entities, so carrying them across modes would produce invalid comparisons.
  const setType = (t: CompareType) => setSearchParams({ type: t });

  const addId = (newId: string) => {
    if (selectedIds.length >= MAX_ITEMS || selectedIds.includes(newId)) return;
    setSearchParams({ type, ids: [...selectedIds, newId].join(",") });
  };

  const removeId = (rid: string) => {
    const next = selectedIds.filter((i) => i !== rid);
    if (next.length === 0) setSearchParams({ type });
    else setSearchParams({ type, ids: next.join(",") });
  };

  const clearAll = () => setSearchParams({ type });

  const {
    data: matrix,
    isLoading: loadingProducts,
    error: errorProducts,
  } = useQuery({
    queryKey: ["compare", "product", marketplaceId, sortedIds],
    queryFn: () =>
      compareApi.compareProducts(marketplaceId!, sortedIds).then((r) => r.data),
    enabled: type === "product" && sortedIds.length >= 2 && !!marketplaceId,
    staleTime: 60_000,
  });

  const {
    data: compareBundles,
    isLoading: loadingBundles,
    error: errorBundles,
  } = useQuery({
    queryKey: ["compare", "bundle", companyId, sortedIds],
    queryFn: () =>
      compareApi.compareBundles(companyId!, sortedIds).then((r) => r.data),
    enabled: type === "bundle" && sortedIds.length >= 2 && !!companyId,
    staleTime: 60_000,
  });

  const isLoading = type === "product" ? loadingProducts : loadingBundles;
  const fetchError = type === "product" ? errorProducts : errorBundles;

  const productCount = matrix?.products.length ?? 0;
  const bundleCount = compareBundles?.length ?? 0;
  const resultCount = type === "product" ? productCount : bundleCount;

  if (!companyId) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="max-w-sm rounded-xl border border-border bg-surface p-8 text-center">
          <GitCompareArrows size={36} className="mx-auto mb-4 text-link" />
          <h2 className="mb-2 text-lg font-semibold text-foreground">
            Nothing to compare
          </h2>
          <p className="text-sm text-muted-foreground">
            Open a store to compare its products and bundles.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-background px-4 py-10 md:py-14">
      <div className="mx-auto max-w-6xl">
        {/* Header */}
        <motion.div
          initial="hidden"
          animate="visible"
          variants={stagger}
          className="mb-10 text-center"
        >
          <motion.p
            variants={fadeIn}
            className="mb-3 text-xs font-semibold text-link"
          >
            Compare
          </motion.p>
          <motion.h1
            variants={fadeInUp}
            className="mb-3 text-4xl font-semibold tracking-tight text-foreground md:text-5xl"
          >
            Side-by-Side Comparison
          </motion.h1>
          <motion.p
            variants={fadeInUp}
            className="text-lg leading-relaxed text-muted-foreground"
          >
            Add up to {MAX_ITEMS} {type === "product" ? "products" : "bundles"}{" "}
            to compare.
          </motion.p>
        </motion.div>

        {/* Controls */}
        <motion.div
          initial="hidden"
          animate="visible"
          variants={stagger}
          className="mb-8 flex flex-wrap items-center gap-3"
        >
          <motion.div
            variants={fadeInUp}
            className="flex items-center gap-1 rounded-xl border border-border bg-surface p-1"
          >
            <button
              onClick={() => setType("product")}
              className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                type === "product"
                  ? "bg-primary text-primary-foreground shadow"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <Package size={14} />
              Products
            </button>
            <button
              onClick={() => setType("bundle")}
              className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                type === "bundle"
                  ? "bg-primary text-primary-foreground shadow"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <Boxes size={14} />
              Bundles
            </button>
          </motion.div>

          <motion.div variants={fadeInUp} className="flex-1">
            <SearchPicker
              type={type}
              companyId={companyId}
              marketplaceId={marketplaceId ?? undefined}
              selectedIds={selectedIds}
              onAdd={addId}
            />
          </motion.div>

          {selectedIds.length > 0 && (
            <motion.div variants={fadeInUp} className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">
                {selectedIds.length} / {MAX_ITEMS} selected
              </span>
              <button
                onClick={clearAll}
                className="text-xs text-muted-foreground underline underline-offset-2 transition-colors hover:text-muted-foreground"
              >
                Clear all
              </button>
            </motion.div>
          )}
        </motion.div>

        {/* Empty state */}
        {selectedIds.length === 0 && (
          <div className="flex flex-col items-center justify-center gap-5 py-24 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-xl border border-border bg-primary/10">
              <GitCompareArrows size={28} className="text-link" />
            </div>
            <div>
              <p className="mb-1 font-semibold text-foreground">
                Nothing to compare yet
              </p>
              <p className="text-sm text-muted-foreground">
                Search for {type === "product" ? "products" : "bundles"} above
                and add up to {MAX_ITEMS} to compare.
              </p>
            </div>
          </div>
        )}

        {/* Needs one more */}
        {selectedIds.length === 1 && (
          <p className="py-8 text-center text-sm text-muted-foreground">
            Add at least one more {type === "product" ? "product" : "bundle"} to
            start comparing.
          </p>
        )}

        {/* Product compare with no marketplace context */}
        {type === "product" && selectedIds.length >= 2 && !marketplaceId && (
          <div className="rounded-xl border border-border bg-surface p-6 text-center">
            <p className="text-sm text-muted-foreground">
              Browse the marketplace to compare products side by side.
            </p>
          </div>
        )}

        {/* Loading */}
        {selectedIds.length >= 2 && isLoading && (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary/40 border-t-primary/40" />
          </div>
        )}

        {/* Error */}
        {fetchError && selectedIds.length >= 2 && !isLoading && (
          <div className="rounded-xl border border-danger/40 bg-danger-subtle p-6 text-center">
            <p className="text-sm text-danger">
              Failed to load comparison data. Please try again.
            </p>
          </div>
        )}

        {/* Tables */}
        {!isLoading && !fetchError && resultCount >= 2 && (
          <motion.div variants={fadeInUp} initial="hidden" animate="visible">
            {type === "product" && matrix ? (
              <ProductComparisonTable matrix={matrix} onRemove={removeId} />
            ) : type === "bundle" && compareBundles ? (
              <BundleComparisonTable
                bundles={compareBundles}
                onRemove={removeId}
              />
            ) : null}
          </motion.div>
        )}

        {/* Partial / not found */}
        {!isLoading &&
          !fetchError &&
          selectedIds.length >= 2 &&
          !(type === "product" && !marketplaceId) &&
          resultCount < 2 && (
            <div className="rounded-xl border border-border bg-surface p-6 text-center">
              <p className="text-sm text-muted-foreground">
                Some selected items could not be found. They may have been
                removed or are no longer available.
              </p>
            </div>
          )}
      </div>
    </div>
  );
}
