export interface StockNotification {
  id: string;
  productId: string;
  productName: string;
  variantId: string | null;
  variantTitle: string | null;
  status: "PENDING" | "NOTIFIED" | "CANCELLED";
  createdAt: string;
}
