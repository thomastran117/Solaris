export interface StockNotification {
  id: number;
  productId: number;
  productName: string;
  variantId: number | null;
  variantTitle: string | null;
  status: "PENDING" | "NOTIFIED" | "CANCELLED";
  createdAt: string;
}
