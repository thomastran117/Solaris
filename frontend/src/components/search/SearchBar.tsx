import { useState, useEffect, useRef, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useSelector } from "react-redux";
import { Search, Tag, Layers, ImageOff } from "lucide-react";
import { catalogApi } from "../../api/catalog";
import type {
  ProductSuggestion,
  SearchSuggestionsResponse,
} from "../../types/search";
import { selectActiveMarketplaceId } from "../../stores/marketplaceSlice";
import { formatMoney } from "../../lib/format";
import { cn } from "../../lib/cn";
import { Spinner } from "../ui/Spinner";

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

function formatPrice(price: number | null): string {
  return price == null ? "" : formatMoney(price, "USD");
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
  const router = useRouter();
  const marketplaceId = useSelector(selectActiveMarketplaceId);

  const [value, setValue] = useState(initialValue);
  const [suggestions, setSuggestions] =
    useState<SearchSuggestionsResponse | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    setValue(initialValue);
  }, [initialValue]);

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

  const fetchSuggestions = useCallback(
    (q: string) => {
      // Cancel any in-flight request so a fast typer never sees stale results
      // overwrite the current ones.
      abortRef.current?.abort();

      if (q.length < 2) {
        setSuggestions(null);
        setOpen(false);
        return;
      }

      const ctrl = new AbortController();
      abortRef.current = ctrl;

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
          if (ctrl.signal.aborted) return; // a newer request already won
          setSuggestions(data);
          const hasAny =
            data.products.length > 0 ||
            data.categories.length > 0 ||
            data.brands.length > 0;
          setOpen(hasAny);
        })
        .catch((err) => {
          if (
            err?.name === "AbortError" ||
            (err instanceof DOMException && err.name === "AbortError")
          )
            return;
          setSuggestions(null);
          setOpen(false);
        })
        .finally(() => {
          if (!ctrl.signal.aborted) setLoading(false);
        });
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

  function selectProduct(product: ProductSuggestion) {
    setOpen(false);
    router.push(`/products/${product.id}`);
  }

  function selectCategory(cat: string) {
    setOpen(false);
    if (onSelectCategory) onSelectCategory(cat);
    else router.push(`/browse?category=${encodeURIComponent(cat)}`);
  }

  function selectBrand(brand: string) {
    setOpen(false);
    if (onSelectBrand) onSelectBrand(brand);
    else router.push(`/browse?brand=${encodeURIComponent(brand)}`);
  }

  const hasSuggestions =
    suggestions &&
    (suggestions.products.length > 0 ||
      suggestions.categories.length > 0 ||
      suggestions.brands.length > 0);

  return (
    <div ref={containerRef} className={cn("relative", className)}>
      <form onSubmit={handleSubmit} role="search" className="flex items-center">
        <div className="relative flex w-full items-center">
          <Search
            aria-hidden
            className="pointer-events-none absolute left-3 size-4 text-muted-foreground"
          />
          <input
            type="search"
            value={value}
            onChange={handleChange}
            onFocus={() => hasSuggestions && setOpen(true)}
            placeholder={placeholder}
            aria-label="Search products"
            autoComplete="off"
            className={cn(
              "h-10 w-full rounded-lg border border-input bg-surface pl-9 pr-9 text-sm text-foreground shadow-xs",
              "placeholder:text-muted-foreground transition-colors",
              "focus-visible:outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/30",
              "[&::-webkit-search-cancel-button]:hidden"
            )}
          />
          {loading && (
            <Spinner className="absolute right-3 size-3.5 text-muted-foreground" />
          )}
        </div>
      </form>

      {open && hasSuggestions && (
        <div className="absolute left-0 right-0 top-full z-50 mt-2 overflow-hidden rounded-xl border bg-surface-raised shadow-lg">
          {suggestions!.products.length > 0 && (
            <div>
              <p className="px-4 pb-1 pt-3 text-xs font-medium text-muted-foreground">
                Products
              </p>
              {suggestions!.products.map((product) => (
                <button
                  key={product.id}
                  type="button"
                  onMouseDown={() => selectProduct(product)}
                  className="flex w-full items-center gap-3 px-4 py-2 text-left transition-colors hover:bg-muted"
                >
                  {product.thumbnailUrl ? (
                    <img
                      src={product.thumbnailUrl}
                      alt={product.name}
                      className="size-10 shrink-0 rounded-lg bg-muted object-cover"
                    />
                  ) : (
                    <div className="flex size-10 shrink-0 items-center justify-center rounded-lg border bg-muted">
                      <ImageOff
                        className="size-4 text-muted-foreground"
                        aria-hidden
                      />
                    </div>
                  )}
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm leading-tight text-foreground">
                      {product.name}
                    </p>
                    {(product.price != null ||
                      product.discountedPrice != null) && (
                      <p className="mt-0.5 text-xs text-muted-foreground tabular-nums">
                        {product.discountedPrice != null ? (
                          <>
                            <span className="font-semibold text-foreground">
                              {formatPrice(product.discountedPrice)}
                            </span>
                            <span className="ml-1.5 line-through">
                              {formatPrice(product.price)}
                            </span>
                          </>
                        ) : (
                          <span className="font-semibold text-foreground">
                            {formatPrice(product.price)}
                          </span>
                        )}
                      </p>
                    )}
                  </div>
                </button>
              ))}
            </div>
          )}

          {suggestions!.categories.length > 0 && (
            <div>
              <p className="px-4 pb-1 pt-3 text-xs font-medium text-muted-foreground">
                Categories
              </p>
              {suggestions!.categories.slice(0, 3).map((cat) => (
                <button
                  key={cat}
                  type="button"
                  onMouseDown={() => selectCategory(cat)}
                  className="flex w-full items-center gap-3 px-4 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted"
                >
                  <Layers
                    className="size-3.5 shrink-0 text-muted-foreground"
                    aria-hidden
                  />
                  <span className="truncate">{cat}</span>
                </button>
              ))}
            </div>
          )}

          {suggestions!.brands.length > 0 && (
            <div>
              <p className="px-4 pb-1 pt-3 text-xs font-medium text-muted-foreground">
                Brands
              </p>
              {suggestions!.brands.slice(0, 3).map((brand) => (
                <button
                  key={brand}
                  type="button"
                  onMouseDown={() => selectBrand(brand)}
                  className="flex w-full items-center gap-3 px-4 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted"
                >
                  <Tag
                    className="size-3.5 shrink-0 text-muted-foreground"
                    aria-hidden
                  />
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
