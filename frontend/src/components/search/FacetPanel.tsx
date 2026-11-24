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

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <p className="text-xs font-semibold text-link mb-3">{title}</p>
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
    onChange({
      ...selected,
      minPrice: same ? null : min,
      maxPrice: same ? null : max,
    });
  }

  const hasFilters =
    selected.category ||
    selected.brand ||
    selected.minPrice != null ||
    selected.maxPrice != null;

  return (
    <div className="rounded-xl border border-border bg-surface p-5 space-y-6">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-foreground">Filters</h3>
        {hasFilters && (
          <button
            type="button"
            onClick={() =>
              onChange({
                category: null,
                brand: null,
                minPrice: null,
                maxPrice: null,
              })
            }
            className="text-xs text-link hover:text-link transition-colors"
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
                      ? "bg-primary/10 text-foreground border border-primary/40"
                      : "text-muted-foreground hover:bg-muted hover:text-foreground"
                  }`}
                >
                  <span>{b.value}</span>
                  <span className="text-xs text-muted-foreground">
                    {b.count}
                  </span>
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
                      ? "bg-primary/10 text-foreground border border-primary/40"
                      : "text-muted-foreground hover:bg-muted hover:text-foreground"
                  }`}
                >
                  <span>{b.value}</span>
                  <span className="text-xs text-muted-foreground">
                    {b.count}
                  </span>
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
                      ? "bg-primary/10 text-foreground border border-primary/40"
                      : "text-muted-foreground hover:bg-muted hover:text-foreground"
                  }`}
                >
                  <span>{r.label}</span>
                  <span className="text-xs text-muted-foreground">
                    {r.count}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </Section>
      )}
    </div>
  );
}
