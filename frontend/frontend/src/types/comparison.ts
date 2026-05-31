export type CompareType = "product" | "bundle";

export interface ProductAttribute {
  id: number;
  name: string;
  value: string;
  displayOrder: number;
}

export interface ProductOption {
  id: number;
  name: string;
  position: number;
}

export interface ProductVariant {
  id: number;
  sku: string | null;
  price: number;
  compareAtPrice: number | null;
  stock: number | null;
  purchasable: boolean;
  option1: string | null;
  option2: string | null;
  option3: string | null;
  variantTitle: string | null;
}

export interface CompareProduct {
  id: number;
  companyId: number;
  name: string;
  description: string | null;
  sku: string | null;
  price: number;
  compareAtPrice: number | null;
  currency: string;
  category: string | null;
  brand: string | null;
  tags: string | null;
  thumbnailUrl: string | null;
  images: { id: number; imageUrl: string; displayOrder: number }[];
  options: ProductOption[];
  variants: ProductVariant[];
  attributes: ProductAttribute[];
  stock: number | null;
  lowStockThreshold: number | null;
  weight: number | null;
  weightUnit: string | null;
  status: string;
  featured: boolean;
  purchasable: boolean;
  listed: boolean;
  createdAt: string;
  updatedAt: string;
  avgRating: number | null;
  reviewCount: number;
}

export interface BundleItem {
  id: number;
  productId: number;
  productName: string;
  variantId: number | null;
  variantSku: string | null;
  variantTitle: string | null;
  quantity: number;
  displayOrder: number;
}

export interface CompareBundle {
  id: number;
  companyId: number;
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
