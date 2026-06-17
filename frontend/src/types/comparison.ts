export type CompareType = "product" | "bundle";

/** One column of the product comparison matrix (mirrors backend ComparedProduct). */
export interface ComparedProduct {
  productId: string;
  name: string;
  price: number;
  currency: string;
  rating: number | null;
  reviewCount: number;
  stockStatus: string;
  imageUrl: string | null;
}

/**
 * One row of the matrix for a single attribute. `valuesByProductId` maps each product id to its
 * value for that attribute, or `null` when the product lacks it (mirrors backend ComparisonRow).
 */
export interface ComparisonRow {
  attributeName: string;
  valuesByProductId: Record<string, string | null>;
}

/** Server-built comparison matrix returned by GET /marketplaces/{id}/catalog/products/compare. */
export interface ProductComparisonResponse {
  products: ComparedProduct[];
  attributes: ComparisonRow[];
}

export interface BundleItem {
  id: string;
  productId: string;
  productName: string;
  variantId: string | null;
  variantSku: string | null;
  variantTitle: string | null;
  quantity: number;
  displayOrder: number;
}

/** Full bundle object returned by GET /companies/{id}/bundles/compare (matrix built client-side). */
export interface CompareBundle {
  id: string;
  companyId: string;
  name: string;
  description: string | null;
  thumbnailUrl: string | null;
  price: number;
  compareAtPrice: number | null;
  currency: string;
  status: string;
  listed: boolean;
  items: BundleItem[];
  createdAt: string;
  updatedAt: string;
}
