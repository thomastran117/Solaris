import { useState, useEffect, useRef, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { Search, Tag, Layers } from "lucide-react";
import { catalogApi } from "../../api/catalog";
import type { SearchSuggestionsResponse } from "../../types/search";
import type { RootState } from "../../stores";

interface Props {
  onSearch: (q: string) => void;
  initialValue?: string;
  placeholder?: string;
  className?: string;
  /**
   * Override the suggestion source. When omitted, falls back to the marketplace-scoped
   * fetch using the current marketplace from Redux. Pass this from a company storefront
   * to scope suggestions to a single company.
   */
  suggestionsFetcher?: (q: string) => Promise<SearchSuggestionsResponse>;
  /**
   * Override what happens when a category/brand suggestion is picked. Default behaviour
   * navigates to /browse?category=… (the marketplace browse page); page-specific callers
   * can route within their own surface instead.
   */
  onSelectCategory?: (cat: string) => void;
  onSelectBrand?: (brand: string) => void;
}

export default function SearchBar({
  onSearch,
  initialValue = "",
  placeholder = "Search products, brands…",
  className = "",
  suggestionsFetcher,
  onSelectCategory,
  onSelectBrand,
}: Props) {
  const navigate = useNavigate();
  const marketplaceId = useSelector((s: RootState) => s.marketplace.currentMarketplace?.id);

  const [value, setValue] = useState(initialValue);
  const [suggestions, setSuggestions] = useState<SearchSuggestionsResponse | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setValue(initialValue);
  }, [initialValue]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const fetchSuggestions = useCallback(
    (q: string) => {
      if (q.length < 2) {
        setSuggestions(null);
        setOpen(false);
        return;
      }
      const fetcher = suggestionsFetcher
        ? suggestionsFetcher(q)
        : marketplaceId
          ? catalogApi.getSuggestions(marketplaceId, q).then((r) => r.data)
          : null;
      if (!fetcher) {
        setSuggestions(null);
        setOpen(false);
        return;
      }
      setLoading(true);
      fetcher
        .then((data) => {
          setSuggestions(data);
          const hasAny =
            data.productNames.length > 0 ||
            data.categories.length > 0 ||
            data.brands.length > 0;
          setOpen(hasAny);
        })
        .catch(() => {
          setSuggestions(null);
          setOpen(false);
        })
        .finally(() => setLoading(false));
    },
    [marketplaceId, suggestionsFetcher]
  );

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const q = e.target.value;
    setValue(q);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => fetchSuggestions(q), 250);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!value.trim()) return;
    setOpen(false);
    onSearch(value.trim());
  }

  function selectSuggestion(type: "product" | "category" | "brand", val: string) {
    setOpen(false);
    if (type === "product") {
      setValue(val);
      onSearch(val);
    } else if (type === "category") {
      if (onSelectCategory) onSelectCategory(val);
      else navigate(`/browse?category=${encodeURIComponent(val)}`);
    } else {
      if (onSelectBrand) onSelectBrand(val);
      else navigate(`/browse?brand=${encodeURIComponent(val)}`);
    }
  }

  const hasSuggestions =
    suggestions &&
    (suggestions.productNames.length > 0 ||
      suggestions.categories.length > 0 ||
      suggestions.brands.length > 0);

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      <form onSubmit={handleSubmit} className="flex items-center">
        <div className="relative flex items-center w-full">
          <Search className="absolute left-3 w-4 h-4 text-white/40 pointer-events-none" />
          <input
            type="text"
            value={value}
            onChange={handleChange}
            onFocus={() => hasSuggestions && setOpen(true)}
            placeholder={placeholder}
            className="w-full bg-white/[0.06] border border-white/10 rounded-full pl-9 pr-4 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/20 transition-colors"
          />
          {loading && (
            <div className="absolute right-3 w-3.5 h-3.5 rounded-full border border-sky-400/40 border-t-sky-400 animate-spin" />
          )}
        </div>
      </form>

      {open && hasSuggestions && (
        <div className="absolute top-full mt-2 left-0 right-0 z-50 rounded-2xl border border-white/10 bg-slate-900/95 backdrop-blur shadow-xl overflow-hidden">
          {suggestions!.productNames.length > 0 && (
            <div>
              <p className="px-4 pt-3 pb-1 text-xs uppercase tracking-[0.2em] font-semibold text-sky-200/60">
                Products
              </p>
              {suggestions!.productNames.slice(0, 5).map((name) => (
                <button
                  key={name}
                  type="button"
                  onMouseDown={() => selectSuggestion("product", name)}
                  className="flex items-center gap-3 w-full px-4 py-2 text-sm text-white/80 hover:bg-white/[0.06] hover:text-white transition-colors text-left"
                >
                  <Search className="w-3.5 h-3.5 text-white/30 flex-shrink-0" />
                  <span className="truncate">{name}</span>
                </button>
              ))}
            </div>
          )}

          {suggestions!.categories.length > 0 && (
            <div>
              <p className="px-4 pt-3 pb-1 text-xs uppercase tracking-[0.2em] font-semibold text-sky-200/60">
                Categories
              </p>
              {suggestions!.categories.slice(0, 3).map((cat) => (
                <button
                  key={cat}
                  type="button"
                  onMouseDown={() => selectSuggestion("category", cat)}
                  className="flex items-center gap-3 w-full px-4 py-2 text-sm text-white/80 hover:bg-white/[0.06] hover:text-white transition-colors text-left"
                >
                  <Layers className="w-3.5 h-3.5 text-sky-200/50 flex-shrink-0" />
                  <span className="truncate">{cat}</span>
                </button>
              ))}
            </div>
          )}

          {suggestions!.brands.length > 0 && (
            <div>
              <p className="px-4 pt-3 pb-1 text-xs uppercase tracking-[0.2em] font-semibold text-sky-200/60">
                Brands
              </p>
              {suggestions!.brands.slice(0, 3).map((brand) => (
                <button
                  key={brand}
                  type="button"
                  onMouseDown={() => selectSuggestion("brand", brand)}
                  className="flex items-center gap-3 w-full px-4 py-2 pb-2 text-sm text-white/80 hover:bg-white/[0.06] hover:text-white transition-colors text-left"
                >
                  <Tag className="w-3.5 h-3.5 text-sky-200/50 flex-shrink-0" />
                  <span className="truncate">{brand}</span>
                </button>
              ))}
            </div>
          )}
          <div className="h-2" />
        </div>
      )}
    </div>
  );
}
