import { useState, useEffect, useRef, useMemo } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { useSelector } from "react-redux";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Search, Plus, GitCompareArrows, Package, Boxes } from "lucide-react";
import type { RootState } from "../stores";
import { compareApi } from "../api/compare";
import { catalogApi } from "../api/catalog";
import { useAnims } from "../hooks/useAnims";
import type { CompareType } from "../types/comparison";
import {
  ProductComparisonTable,
  BundleComparisonTable,
} from "../components/product/ComparisonTable";

const MAX_ITEMS = 4;

// Lightweight shape shared by the two picker sources (catalog products + bundles).
interface PickerResult {
  id: string;
  name: string;
  thumbnailUrl: string | null;
  subtitle: string;
}

// ---- useDebounce ----
function useDebounce<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
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

function SearchPicker({ type, companyId, marketplaceId, selectedIds, onAdd }: SearchPickerProps) {
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
      catalogApi.search(marketplaceId!, { q: debouncedQuery, size: 6 }).then((r) => r.data.items),
    enabled: type === "product" && !!marketplaceId && debouncedQuery.length >= 1 && !isDisabled,
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
    return allBundles.filter((b) => b.name.toLowerCase().includes(q)).slice(0, 6);
  }, [allBundles, debouncedQuery]);

  const results: PickerResult[] =
    type === "product"
      ? (productResults ?? []).map((p) => ({
          id: p.id,
          name: p.name,
          thumbnailUrl: p.thumbnailUrl,
          subtitle:
            [p.category, p.brand].filter(Boolean).join(" · ") ||
            (p.price ? `$${p.price.toFixed(2)}` : ""),
        }))
      : bundleResults.map((b) => ({
          id: b.id,
          name: b.name,
          thumbnailUrl: b.thumbnailUrl,
          subtitle: `${b.items.length} items · ${b.currency} ${b.price.toFixed(2)}`,
        }));

  // Close on outside click.
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
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
            ? "cursor-not-allowed border-white/[0.05] bg-white/[0.02] opacity-50"
            : "border-white/20 bg-white/[0.06] backdrop-blur transition-colors hover:border-white/30 focus-within:border-blue-500/60"
        }`}
      >
        <Search size={15} className="shrink-0 text-white/40" />
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
          className="flex-1 bg-transparent text-sm text-white outline-none placeholder:text-white/45 disabled:cursor-not-allowed"
        />
        {isDisabled && (
          <span className="shrink-0 text-xs text-white/40">
            {selectedIds.length}/{MAX_ITEMS}
          </span>
        )}
      </div>

      {open && results.length > 0 && !isDisabled && (
        <div className="absolute top-full z-50 mt-2 w-full overflow-hidden rounded-xl border border-white/10 bg-slate-900/95 shadow-2xl backdrop-blur">
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
                    : "cursor-pointer hover:bg-white/[0.06]"
                }`}
              >
                {r.thumbnailUrl ? (
                  <img
                    src={r.thumbnailUrl}
                    alt={r.name}
                    className="h-9 w-9 shrink-0 rounded-lg border border-white/10 object-cover"
                  />
                ) : (
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-white/10 bg-white/[0.06] text-white/25">
                    <Package size={14} />
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium text-white">{r.name}</div>
                  {r.subtitle && <div className="truncate text-xs text-white/50">{r.subtitle}</div>}
                </div>
                {alreadyAdded ? (
                  <span className="shrink-0 text-xs text-white/40">Added</span>
                ) : (
                  <Plus size={14} className="shrink-0 text-white/40" />
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
  const marketplaceId = useSelector((s: RootState) => s.marketplace.currentMarketplace?.id);

  const [searchParams, setSearchParams] = useSearchParams();

  const type: CompareType = searchParams.get("type") === "bundle" ? "bundle" : "product";

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
    queryFn: () => compareApi.compareProducts(marketplaceId!, sortedIds).then((r) => r.data),
    enabled: type === "product" && sortedIds.length >= 2 && !!marketplaceId,
    staleTime: 60_000,
  });

  const {
    data: compareBundles,
    isLoading: loadingBundles,
    error: errorBundles,
  } = useQuery({
    queryKey: ["compare", "bundle", companyId, sortedIds],
    queryFn: () => compareApi.compareBundles(companyId!, sortedIds).then((r) => r.data),
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
      <div className="flex min-h-screen items-center justify-center bg-slate-950">
        <div className="max-w-sm rounded-2xl border border-white/10 bg-white/[0.06] p-8 text-center backdrop-blur">
          <GitCompareArrows size={36} className="mx-auto mb-4 text-sky-200" />
          <h2 className="mb-2 text-lg font-semibold text-white">Nothing to compare</h2>
          <p className="text-sm text-white/60">Open a store to compare its products and bundles.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 px-4 py-10 md:py-14">
      <div className="mx-auto max-w-6xl">
        {/* Header */}
        <motion.div initial="hidden" animate="visible" variants={stagger} className="mb-10 text-center">
          <motion.p
            variants={fadeIn}
            className="mb-3 text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90"
          >
            Compare
          </motion.p>
          <motion.h1
            variants={fadeInUp}
            className="mb-3 text-4xl font-extrabold tracking-tight text-white md:text-5xl"
          >
            Side-by-Side Comparison
          </motion.h1>
          <motion.p variants={fadeInUp} className="text-lg leading-relaxed text-white/60">
            Add up to {MAX_ITEMS} {type === "product" ? "products" : "bundles"} to compare.
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
            className="flex items-center gap-1 rounded-xl border border-white/10 bg-white/[0.04] p-1 backdrop-blur"
          >
            <button
              onClick={() => setType("product")}
              className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                type === "product" ? "bg-blue-600 text-white shadow" : "text-white/60 hover:text-white"
              }`}
            >
              <Package size={14} />
              Products
            </button>
            <button
              onClick={() => setType("bundle")}
              className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                type === "bundle" ? "bg-blue-600 text-white shadow" : "text-white/60 hover:text-white"
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
              marketplaceId={marketplaceId}
              selectedIds={selectedIds}
              onAdd={addId}
            />
          </motion.div>

          {selectedIds.length > 0 && (
            <motion.div variants={fadeInUp} className="flex items-center gap-2">
              <span className="text-sm text-white/50">
                {selectedIds.length} / {MAX_ITEMS} selected
              </span>
              <button
                onClick={clearAll}
                className="text-xs text-white/40 underline underline-offset-2 transition-colors hover:text-white/70"
              >
                Clear all
              </button>
            </motion.div>
          )}
        </motion.div>

        {/* Empty state */}
        {selectedIds.length === 0 && (
          <div className="flex flex-col items-center justify-center gap-5 py-24 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl border border-white/10 bg-blue-500/15">
              <GitCompareArrows size={28} className="text-sky-200" />
            </div>
            <div>
              <p className="mb-1 font-semibold text-white">Nothing to compare yet</p>
              <p className="text-sm text-white/50">
                Search for {type === "product" ? "products" : "bundles"} above and add up to {MAX_ITEMS}{" "}
                to compare.
              </p>
            </div>
          </div>
        )}

        {/* Needs one more */}
        {selectedIds.length === 1 && (
          <p className="py-8 text-center text-sm text-white/50">
            Add at least one more {type === "product" ? "product" : "bundle"} to start comparing.
          </p>
        )}

        {/* Product compare with no marketplace context */}
        {type === "product" && selectedIds.length >= 2 && !marketplaceId && (
          <div className="rounded-2xl border border-white/10 bg-white/[0.04] p-6 text-center">
            <p className="text-sm text-white/50">
              Browse the marketplace to compare products side by side.
            </p>
          </div>
        )}

        {/* Loading */}
        {selectedIds.length >= 2 && isLoading && (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-blue-500/30 border-t-blue-500" />
          </div>
        )}

        {/* Error */}
        {fetchError && selectedIds.length >= 2 && !isLoading && (
          <div className="rounded-2xl border border-red-500/20 bg-red-500/[0.06] p-6 text-center">
            <p className="text-sm text-red-400">Failed to load comparison data. Please try again.</p>
          </div>
        )}

        {/* Tables */}
        {!isLoading && !fetchError && resultCount >= 2 && (
          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
            {type === "product" && matrix ? (
              <ProductComparisonTable matrix={matrix} onRemove={removeId} />
            ) : type === "bundle" && compareBundles ? (
              <BundleComparisonTable bundles={compareBundles} onRemove={removeId} />
            ) : null}
          </motion.div>
        )}

        {/* Partial / not found */}
        {!isLoading &&
          !fetchError &&
          selectedIds.length >= 2 &&
          !(type === "product" && !marketplaceId) &&
          resultCount < 2 && (
            <div className="rounded-2xl border border-white/10 bg-white/[0.04] p-6 text-center">
              <p className="text-sm text-white/50">
                Some selected items could not be found. They may have been removed or are no longer
                available.
              </p>
            </div>
          )}
      </div>
    </div>
  );
}
