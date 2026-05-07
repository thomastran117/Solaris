import api from "../api";
import type { StockNotification } from "../types/notifications";

export const notificationsApi = {
  subscribeBackInStock: (productId: number, variantId?: number) =>
    api.post<StockNotification>("/stock-notifications", { productId, variantId }),

  cancelBackInStock: (notificationId: number) =>
    api.delete(`/stock-notifications/${notificationId}`),

  listBackInStock: () =>
    api.get<StockNotification[]>("/stock-notifications"),
};
