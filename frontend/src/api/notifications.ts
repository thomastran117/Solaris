import api from "../api";
import type { StockNotification } from "../types/notifications";

export const notificationsApi = {
  subscribeBackInStock: (productId: string | undefined, variantId?: string) =>
    api.post<StockNotification>("/stock-notifications", { productId, variantId }),

  cancelBackInStock: (notificationId: string) =>
    api.delete(`/stock-notifications/${notificationId}`),

  listBackInStock: () =>
    api.get<StockNotification[]>("/stock-notifications"),
};
