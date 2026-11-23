import type { CatalogProduct } from "../api/catalog";
import type { CatalogUrlParams } from "./catalogParams";

/** Collapses whitespace and trims to a search-snippet length on a word boundary. */
export function metaDescription(
  text: string | null | undefined,
  fallback: string,
  max = 160
): string {
  const clean = (text ?? "").replace(/\s+/g, " ").trim();
  if (!clean) return fallback;
  if (clean.length <= max) return clean;
  const cut = clean.slice(0, max - 1);
  const lastSpace = cut.lastIndexOf(" ");
  return `${(lastSpace > max * 0.6 ? cut.slice(0, lastSpace) : cut).trimEnd()}…`;
}

/**
 * Canonical URL for catalog listings. Keeps only parameters that define a distinct,
 * indexable listing (category, brand, page); sort order and price bounds are views
 * of the same listing.
 */
export function catalogCanonical(path: string, p: CatalogUrlParams): string {
  const qs = new URLSearchParams();
  if (p.category) qs.set("category", p.category);
  if (p.brand) qs.set("brand", p.brand);
  if (p.page > 0) qs.set("page", String(p.page));
  const s = qs.toString();
  return s ? `${path}?${s}` : path;
}

/** Free-text searches and price filters produce near-duplicate pages; keep them out of the index. */
export function isCatalogIndexable(p: CatalogUrlParams): boolean {
  return !p.q && !p.minPrice && !p.maxPrice;
}

/**
 * Serializes structured data for a `<script type="application/ld+json">` tag.
 * Escaping `<` prevents a value containing `</script>` from closing the tag (XSS).
 */
export function jsonLd(data: unknown): string {
  return JSON.stringify(data).replace(/</g, "\\u003c");
}

/** Absolute URL when the public frontend origin is configured, else the path itself. */
export function absoluteUrl(path: string): string {
  const origin = (process.env.NEXT_PUBLIC_FRONTEND_URL ?? "")
    .trim()
    .replace(/\/+$/, "");
  return origin ? `${origin}${path}` : path;
}

/** schema.org Product for a catalog product, from public catalog fields only. */
export function productJsonLd(product: CatalogProduct, path: string) {
  const image =
    product.thumbnailUrl ?? product.images?.[0]?.imageUrl ?? undefined;
  const inStock =
    product.purchasable && (product.stock == null || product.stock > 0);
  return {
    "@context": "https://schema.org",
    "@type": "Product",
    name: product.name,
    description: product.description ?? undefined,
    image,
    sku: product.sku ?? undefined,
    brand: product.brand
      ? { "@type": "Brand", name: product.brand }
      : undefined,
    category: product.category ?? undefined,
    offers: {
      "@type": "Offer",
      url: absoluteUrl(path),
      price: product.price,
      priceCurrency: (product.currency || "USD").toUpperCase(),
      availability: inStock
        ? "https://schema.org/InStock"
        : product.preorderEnabled
          ? "https://schema.org/PreOrder"
          : "https://schema.org/OutOfStock",
    },
  };
}
