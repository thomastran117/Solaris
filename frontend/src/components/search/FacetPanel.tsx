import type { SearchFacets } from "../../types/search";

export interface FacetSelection {
  category: string | null;
  brand: string | null;
  minPrice: number | null;
  maxPrice: number | null;
}

interface Props {
  facets: SearchFacets;
  selected: FacetSelection;
  onChange: (next: FacetSelection) => void;
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-[0.2em] font-semibold text-sky-200/70 mb-3">
        {title}
      </p>
      {children}
    </div>
  );
}

export default function FacetPanel({ facets, selected, onChange }: Props) {
  function toggleCategory(val: string) {
    onChange({ ...selected, category: selected.category === val ? null : val });
  }

  function toggleBrand(val: string) {
    onChange({ ...selected, brand: selected.brand === val ? null : val });
  }

  function setPriceRange(min: number | null, max: number | null) {
    const same = selected.minPrice === min && selected.maxPrice === max;
    onChange({ ...selected, minPrice: same ? null : min, maxPrice: same ? null : max });
  }

  const hasFilters =
    selected.category || selected.brand || selected.minPrice != null || selected.maxPrice != null;

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 space-y-6">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-white">Filters</h3>
        {hasFilters && (
          <button
            type="button"
            onClick={() => onChange({ category: null, brand: null, minPrice: null, maxPrice: null })}
            className="text-xs text-sky-400 hover:text-sky-300 transition-colors"
          >
            Clear all
          </button>
        )}
      </div>

      {facets.categories.length > 0 && (
        <Section title="Category">
          <ul className="space-y-1.5">
            {facets.categories.map((b) => (
              <li key={b.value}>
                <button
                  type="button"
                  onClick={() => toggleCategory(b.value)}
                  className={`flex items-center justify-between w-full text-sm rounded-lg px-3 py-1.5 transition-colors ${
                    selected.category === b.value
                      ? "bg-blue-600/20 text-white border border-blue-500/30"
                      : "text-white/70 hover:bg-white/[0.06] hover:text-white"
                  }`}
                >
                  <span>{b.value}</span>
                  <span className="text-xs text-white/40">{b.count}</span>
                </button>
              </li>
            ))}
          </ul>
        </Section>
      )}

      {facets.brands.length > 0 && (
        <Section title="Brand">
          <ul className="space-y-1.5">
            {facets.brands.map((b) => (
              <li key={b.value}>
                <button
                  type="button"
                  onClick={() => toggleBrand(b.value)}
                  className={`flex items-center justify-between w-full text-sm rounded-lg px-3 py-1.5 transition-colors ${
                    selected.brand === b.value
                      ? "bg-blue-600/20 text-white border border-blue-500/30"
                      : "text-white/70 hover:bg-white/[0.06] hover:text-white"
                  }`}
                >
                  <span>{b.value}</span>
                  <span className="text-xs text-white/40">{b.count}</span>
                </button>
              </li>
            ))}
          </ul>
        </Section>
      )}

      {facets.priceRanges.length > 0 && (
        <Section title="Price">
          <ul className="space-y-1.5">
            {facets.priceRanges.map((r) => (
              <li key={r.label}>
                <button
                  type="button"
                  onClick={() => setPriceRange(r.min, r.max)}
                  className={`flex items-center justify-between w-full text-sm rounded-lg px-3 py-1.5 transition-colors ${
                    selected.minPrice === r.min && selected.maxPrice === r.max
                      ? "bg-blue-600/20 text-white border border-blue-500/30"
                      : "text-white/70 hover:bg-white/[0.06] hover:text-white"
                  }`}
                >
                  <span>{r.label}</span>
                  <span className="text-xs text-white/40">{r.count}</span>
                </button>
              </li>
            ))}
          </ul>
        </Section>
      )}
    </div>
  );
}
