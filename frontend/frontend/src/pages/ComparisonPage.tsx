import { useState, useEffect, useRef, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import { useSelector } from "react-redux";
import { useQuery } from "@tanstack/react-query";
import { motion, useReducedMotion } from "framer-motion";
import type { Variants } from "framer-motion";
import { Search, Plus, GitCompareArrows, Package, Boxes } from "lucide-react";
import type { RootState } from "../stores";
import { compareApi } from "../api/compare";
import type { CompareType, CompareProduct, CompareBundle } from "../types/comparison";
import { ProductComparisonTable, BundleComparisonTable } from "../components/product/ComparisonTable";

// ---- Animation helpers ----
const useAnims = () => {
  const prefersReducedMotion = useReducedMotion();
  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.35 } } }
    : { hidden: { opacity: 0, y: 18 }, visible: { opacity: 1, y: 0, transition: { duration: 0.55 } } };
  const fadeIn: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1 } }
    : { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.6 } } };
  const stagger: Variants = prefersReducedMotion
    ? { hidden: {}, visible: { transition: { staggerChildren: 0.05 } } }
    : { hidden: {}, visible: { transition: { staggerChildren: 0.12 } } };
  return { fadeInUp, fadeIn, stagger };
};

// ---- useDebounce ----
function useDebounce<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

const MAX_ITEMS = 4;

// ======================================================================
// SearchPicker
// ======================================================================

interface SearchPickerProps {
  type: CompareType;
  companyId: number;
  selectedIds: number[];
  onAdd: (id: number) => void;
}

function SearchPicker({ type, companyId, selectedIds, onAdd }: SearchPickerProps) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const debouncedQuery = useDebounce(query, 300);
  const containerRef = useRef<HTMLDivElement>(null);

  const isDisabled = selectedIds.length >= MAX_ITEMS;

  // Product search
  const { data: productResults } = useQuery({
    queryKey: ["compare-search-products", companyId, debouncedQuery],
    queryFn: () =>
      compareApi.searchProducts(companyId, debouncedQuery).then((r) => r.data.items),
    enabled: type === "product" && debouncedQuery.length >= 1 && !isDisabled,
    staleTime: 10_000,
  });

  // Bundle list (no search endpoint — client-side filter)
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

  const results: { id: number; name: string; thumbnailUrl: string | null; subtitle: string }[] =
    type === "product"
      ? (productResults ?? []).map((p) => ({
          id: p.id,
          name: p.name,
          thumbnailUrl: p.thumbnailUrl,
          subtitle: [p.category, p.brand].filter(Boolean).join(" · ") || (p.price ? `$${p.price.toFixed(2)}` : ""),
        }))
      : bundleResults.map((b) => ({
          id: b.id,
          name: b.name,
          thumbnailUrl: b.thumbnailUrl,
          subtitle: `${b.items.length} items · ${b.currency} ${b.price.toFixed(2)}`,
        }));

  // Close on outside click
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
        className={`flex items-center gap-2 px-3 py-2.5 rounded-xl border ${
          isDisabled
            ? "border-white/[0.05] bg-white/[0.02] opacity-50 cursor-not-allowed"
            : "border-white/20 bg-white/[0.06] backdrop-blur hover:border-white/30 focus-within:border-blue-500/60 transition-colors"
        }`}
      >
        <Search size={15} className="text-white/40 shrink-0" />
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
          className="flex-1 bg-transparent text-sm text-white placeholder:text-white/45 outline-none disabled:cursor-not-allowed"
        />
        {isDisabled && (
          <span className="text-xs text-white/40 shrink-0">{selectedIds.length}/{MAX_ITEMS}</span>
        )}
      </div>

      {open && results.length > 0 && !isDisabled && (
        <div className="absolute top-full mt-2 w-full z-50 rounded-xl border border-white/10 bg-slate-900/95 backdrop-blur shadow-2xl overflow-hidden">
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
                className={`w-full flex items-center gap-3 px-3 py-2.5 text-left transition-colors ${
                  alreadyAdded
                    ? "opacity-40 cursor-not-allowed"
                    : "hover:bg-white/[0.06] cursor-pointer"
                }`}
              >
                {r.thumbnailUrl ? (
                  <img
                    src={r.thumbnailUrl}
                    alt={r.name}
                    className="w-9 h-9 rounded-lg object-cover border border-white/10 shrink-0"
                  />
                ) : (
                  <div className="w-9 h-9 rounded-lg bg-white/[0.06] border border-white/10 shrink-0 flex items-center justify-center text-white/25">
                    <Package size={14} />
                  </div>
                )}
                <div className="flex-1 min-w-0">
                  <div className="text-sm text-white font-medium truncate">{r.name}</div>
                  {r.subtitle && (
                    <div className="text-xs text-white/50 truncate">{r.subtitle}</div>
                  )}
                </div>
                {alreadyAdded ? (
                  <span className="text-xs text-white/40 shrink-0">Added</span>
                ) : (
                  <Plus size={14} className="text-white/40 shrink-0" />
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
  const companyId = useSelector((state: RootState) => state.auth.companyId);

  const [searchParams, setSearchParams] = useSearchParams();

  const type: CompareType =
    searchParams.get("type") === "bundle" ? "bundle" : "product";

  const idsParam = searchParams.get("ids") ?? "";
  const selectedIds = useMemo(
    () =>
      idsParam
        .split(",")
        .map(Number)
        .filter((n) => !isNaN(n) && n > 0),
    [idsParam]
  );

  const sortedIds = useMemo(() => [...selectedIds].sort((a, b) => a - b), [selectedIds]);

  const setType = (t: CompareType) => {
    setSearchParams({ type: t });
  };

  const addId = (id: number) => {
    if (selectedIds.length >= MAX_ITEMS || selectedIds.includes(id)) return;
    const next = [...selectedIds, id];
    setSearchParams({ type, ids: next.join(",") });
  };

  const removeId = (id: number) => {
    const next = selectedIds.filter((i) => i !== id);
    if (next.length === 0) {
      setSearchParams({ type });
    } else {
      setSearchParams({ type, ids: next.join(",") });
    }
  };

  const clearAll = () => setSearchParams({ type });

  // Fetch comparison data
  const {
    data: compareProducts,
    isLoading: loadingProducts,
    error: errorProducts,
  } = useQuery({
    queryKey: ["compare", "product", sortedIds],
    queryFn: () =>
      compareApi.compareProducts(companyId!, sortedIds).then((r) => r.data),
    enabled: type === "product" && sortedIds.length >= 2 && companyId != null,
    staleTime: 60_000,
  });

  const {
    data: compareBundles,
    isLoading: loadingBundles,
    error: errorBundles,
  } = useQuery({
    queryKey: ["compare", "bundle", sortedIds],
    queryFn: () =>
      compareApi.compareBundles(companyId!, sortedIds).then((r) => r.data),
    enabled: type === "bundle" && sortedIds.length >= 2 && companyId != null,
    staleTime: 60_000,
  });

  const isLoading = type === "product" ? loadingProducts : loadingBundles;
  const fetchError = type === "product" ? errorProducts : errorBundles;
  const items = (type === "product" ? compareProducts : compareBundles) ?? [];

  if (!companyId) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-8 text-center max-w-sm"
        >
          <GitCompareArrows size={36} className="text-sky-200 mx-auto mb-4" />
          <h2 className="text-lg font-semibold text-white mb-2">Sign in to compare</h2>
          <p className="text-sm text-white/60">
            Product comparison is available to logged-in users. Please sign in to continue.
          </p>
        </motion.div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 px-4 py-10 md:py-14">
      <div className="max-w-6xl mx-auto">

        {/* Header */}
        <motion.div
          initial="hidden"
          animate="visible"
          variants={stagger}
          className="mb-10 text-center"
        >
          <motion.p
            variants={fadeIn}
            className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-3"
          >
            Compare
          </motion.p>
          <motion.h1
            variants={fadeInUp}
            className="text-4xl md:text-5xl font-extrabold tracking-tight text-white mb-3"
          >
            Side-by-Side Comparison
          </motion.h1>
          <motion.p variants={fadeInUp} className="text-white/60 text-lg leading-relaxed">
            Add up to {MAX_ITEMS} {type === "product" ? "products" : "bundles"} to compare.
          </motion.p>
        </motion.div>

        {/* Type toggle + search controls */}
        <motion.div
          initial="hidden"
          animate="visible"
          variants={stagger}
          className="flex flex-wrap items-center gap-3 mb-8"
        >
          {/* Type toggle */}
          <motion.div
            variants={fadeInUp}
            className="flex items-center gap-1 p-1 rounded-xl border border-white/10 bg-white/[0.04] backdrop-blur"
          >
            <button
              onClick={() => setType("product")}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                type === "product"
                  ? "bg-blue-600 text-white shadow"
                  : "text-white/60 hover:text-white"
              }`}
            >
              <Package size={14} />
              Products
            </button>
            <button
              onClick={() => setType("bundle")}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                type === "bundle"
                  ? "bg-blue-600 text-white shadow"
                  : "text-white/60 hover:text-white"
              }`}
            >
              <Boxes size={14} />
              Bundles
            </button>
          </motion.div>

          {/* Search picker */}
          <motion.div variants={fadeInUp} className="flex-1">
            <SearchPicker
              type={type}
              companyId={companyId}
              selectedIds={selectedIds}
              onAdd={addId}
            />
          </motion.div>

          {/* Selected count + clear */}
          {selectedIds.length > 0 && (
            <motion.div variants={fadeInUp} className="flex items-center gap-2">
              <span className="text-sm text-white/50">
                {selectedIds.length} / {MAX_ITEMS} selected
              </span>
              <button
                onClick={clearAll}
                className="text-xs text-white/40 hover:text-white/70 underline underline-offset-2 transition-colors"
              >
                Clear all
              </button>
            </motion.div>
          )}
        </motion.div>

        {/* Empty state */}
        {selectedIds.length === 0 && (
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex flex-col items-center justify-center py-24 gap-5 text-center"
          >
            <div className="w-16 h-16 rounded-2xl bg-blue-500/15 border border-white/10 flex items-center justify-center">
              <GitCompareArrows size={28} className="text-sky-200" />
            </div>
            <div>
              <p className="text-white font-semibold mb-1">Nothing to compare yet</p>
              <p className="text-white/50 text-sm">
                Search for {type === "product" ? "products" : "bundles"} above and add up to {MAX_ITEMS} to compare.
              </p>
            </div>
            {/* Placeholder columns */}
            <div className="flex gap-3 mt-4">
              {Array.from({ length: 3 }, (_, i) => (
                <div
                  key={i}
                  className="w-32 h-36 rounded-2xl border border-dashed border-white/15 bg-white/[0.02] flex flex-col items-center justify-center gap-2"
                >
                  <Plus size={18} className="text-white/20" />
                  <span className="text-xs text-white/25">Add item</span>
                </div>
              ))}
            </div>
          </motion.div>
        )}

        {/* Need more items prompt */}
        {selectedIds.length === 1 && (
          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="text-center text-white/50 text-sm py-8"
          >
            Add at least one more {type === "product" ? "product" : "bundle"} to start comparing.
          </motion.p>
        )}

        {/* Loading */}
        {selectedIds.length >= 2 && isLoading && (
          <div className="flex items-center justify-center py-20">
            <div className="w-8 h-8 rounded-full border-2 border-blue-500/30 border-t-blue-500 animate-spin" />
          </div>
        )}

        {/* Error */}
        {fetchError && selectedIds.length >= 2 && !isLoading && (
          <div className="rounded-2xl border border-red-500/20 bg-red-500/[0.06] p-6 text-center">
            <p className="text-red-400 text-sm">
              Failed to load comparison data. Please try again.
            </p>
          </div>
        )}

        {/* Comparison table */}
        {!isLoading && !fetchError && items.length >= 2 && (
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
          >
            {type === "product" ? (
              <ProductComparisonTable
                products={items as CompareProduct[]}
                onRemove={removeId}
              />
            ) : (
              <BundleComparisonTable
                bundles={items as CompareBundle[]}
                onRemove={removeId}
              />
            )}
          </motion.div>
        )}

        {/* Partial state: selected some but data hasn't loaded or returned fewer items */}
        {!isLoading && !fetchError && selectedIds.length >= 2 && items.length < 2 && (
          <div className="rounded-2xl border border-white/10 bg-white/[0.04] p-6 text-center">
            <p className="text-white/50 text-sm">
              Some selected items could not be found. They may have been removed or are no longer available.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
